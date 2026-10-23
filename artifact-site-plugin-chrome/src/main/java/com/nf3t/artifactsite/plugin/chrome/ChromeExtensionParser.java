package com.nf3t.artifactsite.plugin.chrome;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jspecify.annotations.Nullable;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.PathUtils;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parser for Chrome browser extension packages: signed {@code .crx} files (CRX2 and CRX3) and
 * plain {@code .zip} archives of an unpacked extension.
 *
 * <p>Both formats wrap a ZIP archive containing a {@code manifest.json}; a CRX just prefixes it with
 * a header carrying the signing key, from which Chrome derives the extension's stable 32-character
 * id. That id (or one derived from the manifest's {@code key} field) becomes the artifact id.
 */
public class ChromeExtensionParser implements ArtifactParser {

    /** Artifact type tag (see {@link ArtifactFields#ARTIFACT_TYPES}) for every extension this parser handles. */
    static final String ARTIFACT_TYPE_CHROME_EXTENSION = "chrome-extension";

    private static final String PARSER_ID = "chrome";
    private static final String GROUP_ID = "chrome";
    private static final String ICON_RESOURCE_NAME = "icon.svg";
    private static final String INSTALL_GUIDE_RESOURCE_NAME = "install.html";
    private static final List<String> SEO_TAGS = List.of("chrome", "browser extension", "crx", "chromium");
    private static final byte[] CRX_MAGIC = "Cr24".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_CRX_HEADER_BYTES = 1 << 20;
    private static final long MAX_ENTRY_BYTES = 16L << 20;
    private static final String MANIFEST_NAME = "manifest.json";
    private static final String README_BASENAME = "readme.md";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    /** {@inheritDoc} */
    @Override
    public String id() {
        return PARSER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "Chrome Extension";
    }

    /** {@inheritDoc} */
    @Override
    public String iconResourceName() {
        return ICON_RESOURCE_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public String installGuideResourceName() {
        return INSTALL_GUIDE_RESOURCE_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> seoTags() {
        return SEO_TAGS;
    }

    /**
     * Accepts {@code .crx} and {@code .zip} files. Because {@code .zip} is generic, a zip that turns
     * out not to contain a {@code manifest.json} is silently skipped by {@link #parse} without touching
     * the shared datum (a {@code .crx} without one is an error).
     */
    @Override
    public boolean supports(@Nullable Datum datum) {
        if (datum == null) {
            return false;
        }
        String fileName = ArtifactFields.FILE_NAME.getString(datum);
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".crx") || lower.endsWith(".zip");
    }

    /** {@inheritDoc} */
    @Override
    public void parse(Datum metadata, Path contentPath, IArtifactParseContext context) throws Exception {
        // First pass: everything small we might need, at any depth - a source archive (e.g. a GitHub
        // release zip) nests the extension under a single top-level folder.
        CrxContents raw = readEntries(contentPath, name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith("/" + MANIFEST_NAME) || lower.equals(MANIFEST_NAME)
                    || lower.endsWith("/" + README_BASENAME) || lower.equals(README_BASENAME)
                    || (lower.contains("_locales/") && lower.endsWith("/messages.json"));
        });
        String root = findExtensionRoot(raw.entries().keySet());
        if (root == null) {
            if (isCrx(contentPath)) {
                throw new IllegalArgumentException("Chrome extension missing manifest.json");
            }
            // A generic .zip that simply isn't an extension (e.g. a mod for another game): leave the shared datum alone.
            return;
        }
        CrxContents contents = new CrxContents(relativeTo(raw.entries(), root), raw.extensionId());
        byte[] manifestBytes = contents.entries().get(MANIFEST_NAME);
        JsonNode manifest = readJson(manifestBytes);
        if (manifest == null || !manifest.isObject()) {
            throw new IllegalArgumentException("Chrome extension manifest.json is not a JSON object");
        }

        String version = text(manifest, "version");
        if (version == null) {
            throw new IllegalArgumentException("Chrome extension manifest.json has no version");
        }
        Localizer localizer = new Localizer(contents.entries(), text(manifest, "default_locale"));
        String name = localizer.resolve(text(manifest, "name"));
        String extensionId = firstNonBlank(contents.extensionId(), idFromKey(text(manifest, "key")));
        String artifactId = firstNonBlank(extensionId, slugify(name), "extension");
        int manifestVersion = manifest.path("manifest_version").asInt(0);

        ArtifactFields.ARTIFACT_ID.setString(metadata, artifactId);
        ArtifactFields.GROUP_ID.setString(metadata, GROUP_ID);
        ArtifactFields.VERSION.setString(metadata, version);
        ArtifactFields.NAME.setString(metadata, firstNonBlank(name, artifactId));
        ArtifactFields.DESCRIPTION.setString(metadata, localizer.resolve(text(manifest, "description")));
        ArtifactFields.ID.setString(metadata, GROUP_ID + ":" + artifactId + ":" + version);
        ArtifactFields.PLUGIN_ID.setString(metadata, id());
        ArtifactFields.ARTIFACT_TYPES.getStringList(metadata).add(ARTIFACT_TYPE_CHROME_EXTENSION);
        ArtifactFields.SCM_URL.setString(metadata, text(manifest, "homepage_url"));
        ArtifactFields.FILE_SIZE.setLong(metadata, Files.size(contentPath));
        ArtifactFields.FILE_SHA256.setString(metadata, PathUtils.sha256(contentPath));

        String author = readAuthor(manifest, localizer);
        if (author != null) {
            ArtifactFields.AUTHORS.getStringList(metadata).add(author);
        }
        if (manifestVersion > 0) {
            ArtifactFields.TAGS.getStringList(metadata).add("manifest-v" + manifestVersion);
        }
        byte[] readme = contents.entries().get(README_BASENAME);
        if (readme != null) {
            ArtifactFields.README.setString(metadata, new String(readme, StandardCharsets.UTF_8));
        }

        ChromeExtensionFields.DETAILS.setString(metadata, buildDetails(manifest, manifestVersion, extensionId));
        fillPermissions(metadata, manifest);
        readIcon(metadata, contentPath, manifest, root);

        context.writeArtifact(metadata, contentPath);
    }

    /**
     * Picks the extension root: the directory (with trailing {@code /}, or empty for the archive root)
     * holding the shallowest {@code manifest.json}, looking at most two folders deep.
     *
     * @return the root prefix, or {@code null} when no {@code manifest.json} is present
     */
    private static @Nullable String findExtensionRoot(Iterable<String> names) {
        String best = null;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals(MANIFEST_NAME) && !lower.endsWith("/" + MANIFEST_NAME)) {
                continue;
            }
            String dir = name.substring(0, name.length() - MANIFEST_NAME.length());
            if (dir.chars().filter(c -> c == '/').count() > 2) {
                continue;
            }
            if (best == null || dir.length() < best.length()) {
                best = dir;
            }
        }
        return best;
    }

    /** Re-keys the entries under {@code root} by their root-relative, lower-cased path; others are dropped. */
    private static Map<String, byte[]> relativeTo(Map<String, byte[]> entries, String root) {
        Map<String, byte[]> relative = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().regionMatches(true, 0, root, 0, root.length())) {
                relative.putIfAbsent(entry.getKey().substring(root.length()).toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        return relative;
    }

    private static boolean isCrx(Path contentPath) {
        return contentPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".crx");
    }

    /**
     * Builds the markdown "Manifest" summary: manifest version, extension id, minimum Chrome version,
     * and the kinds of entry points the extension declares.
     */
    private String buildDetails(JsonNode manifest, int manifestVersion, @Nullable String extensionId) {
        StringBuilder out = new StringBuilder();
        if (manifestVersion > 0) {
            out.append("- **Manifest version:** ").append(manifestVersion).append('\n');
        }
        if (extensionId != null) {
            out.append("- **Extension ID:** `").append(extensionId).append("`\n");
        }
        String minimumChrome = text(manifest, "minimum_chrome_version");
        if (minimumChrome != null) {
            out.append("- **Minimum Chrome version:** ").append(minimumChrome).append('\n');
        }
        List<String> entryPoints = new ArrayList<>();
        for (String key : List.of("background", "action", "browser_action", "page_action", "content_scripts",
                "options_page", "options_ui", "side_panel", "devtools_page", "chrome_url_overrides")) {
            if (manifest.has(key)) {
                entryPoints.add("`" + key + "`");
            }
        }
        if (!entryPoints.isEmpty()) {
            out.append("- **Declares:** ").append(String.join(", ", entryPoints)).append('\n');
        }
        return out.toString();
    }

    /**
     * Fills the permissions table from {@code permissions}, {@code optional_permissions},
     * {@code host_permissions} and the host patterns of {@code content_scripts}.
     */
    private void fillPermissions(Datum metadata, JsonNode manifest) {
        List<Map<String, String>> rows = ChromeExtensionFields.PERMISSIONS.getRecordList(metadata);
        addPermissions(rows, manifest.get("permissions"), "Required");
        addPermissions(rows, manifest.get("host_permissions"), "Host");
        addPermissions(rows, manifest.get("optional_permissions"), "Optional");
        addPermissions(rows, manifest.get("optional_host_permissions"), "Optional host");
        JsonNode contentScripts = manifest.get("content_scripts");
        if (contentScripts != null && contentScripts.isArray()) {
            for (JsonNode script : contentScripts) {
                addPermissions(rows, script.get("matches"), "Content script");
            }
        }
    }

    private void addPermissions(List<Map<String, String>> rows, @Nullable JsonNode values, String kind) {
        if (values == null || !values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            if (!value.isString() || value.asString().isBlank()) {
                continue;
            }
            boolean duplicate = rows.stream().anyMatch(row ->
                    value.asString().equals(row.get("Permission")) && kind.equals(row.get("Kind")));
            if (!duplicate) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("Permission", value.asString());
                row.put("Kind", kind);
                rows.add(row);
            }
        }
    }

    /** Reads {@code author}, which is a string or (in older manifests) an object with an {@code email}. */
    private @Nullable String readAuthor(JsonNode manifest, Localizer localizer) {
        JsonNode author = manifest.get("author");
        if (author == null) {
            return null;
        }
        if (author.isString()) {
            return firstNonBlank(localizer.resolve(author.asString()));
        }
        return text(author, "email");
    }

    /**
     * Picks the largest icon the manifest declares ({@code icons}, then the action's
     * {@code default_icon} map) and stores it as this artifact's icon.
     */
    private void readIcon(Datum metadata, Path contentPath, JsonNode manifest, String root) throws IOException {
        String iconPath = largestIcon(manifest.get("icons"));
        for (String actionKey : List.of("action", "browser_action", "page_action")) {
            if (iconPath != null) {
                break;
            }
            JsonNode defaultIcon = manifest.path(actionKey).get("default_icon");
            iconPath = defaultIcon != null && defaultIcon.isString() ? defaultIcon.asString() : largestIcon(defaultIcon);
        }
        if (iconPath == null) {
            return;
        }
        String target = iconPath.replace('\\', '/');
        while (target.startsWith("./") || target.startsWith("/")) {
            target = target.startsWith("./") ? target.substring(2) : target.substring(1);
        }
        String wanted = root + target;
        byte[] bytes = readEntries(contentPath, name -> name.equals(wanted)).entries().get(wanted);
        if (bytes != null && bytes.length > 0) {
            ArtifactFields.ICON_DATA.setString(metadata, Base64.getEncoder().encodeToString(bytes));
            ArtifactFields.ICON_ORIGINAL_FILE_NAME.setString(metadata, wanted.substring(wanted.lastIndexOf('/') + 1));
        }
    }

    /** Returns the path of the icon with the greatest numeric size key, or {@code null}. */
    private static @Nullable String largestIcon(@Nullable JsonNode icons) {
        if (icons == null || !icons.isObject()) {
            return null;
        }
        String best = null;
        int bestSize = -1;
        for (Map.Entry<String, JsonNode> icon : icons.properties()) {
            int size;
            try {
                size = Integer.parseInt(icon.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (icon.getValue().isString() && size > bestSize) {
                bestSize = size;
                best = icon.getValue().asString();
            }
        }
        return best;
    }

    /**
     * Streams the archive once, keeping the entries accepted by {@code wanted}, keyed by their path
     * inside the archive.
     */
    private CrxContents readEntries(Path contentPath, Predicate<String> wanted) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(contentPath))) {
            String extensionId = skipCrxHeader(raw);
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !wanted.test(name)) {
                        continue;
                    }
                    entries.putIfAbsent(name, zip.readNBytes((int) MAX_ENTRY_BYTES));
                }
            }
            return new CrxContents(entries, extensionId);
        }
    }

    /**
     * Consumes a CRX header from the front of {@code input} if there is one, leaving the stream at the
     * start of the embedded ZIP. A plain ZIP is left untouched.
     *
     * @param input buffered stream positioned at the start of the file
     * @return the extension id encoded in the CRX header, or {@code null} for a plain zip / unknown id
     */
    static @Nullable String skipCrxHeader(InputStream input) throws IOException {
        input.mark(4);
        byte[] magic = input.readNBytes(4);
        if (!java.util.Arrays.equals(magic, CRX_MAGIC)) {
            input.reset();
            return null;
        }
        int version = readIntLe(input);
        if (version == 2) {
            int publicKeyLength = readIntLe(input);
            int signatureLength = readIntLe(input);
            byte[] publicKey = readExactly(input, publicKeyLength);
            input.skipNBytes(signatureLength);
            return idFromPublicKey(publicKey);
        }
        if (version == 3) {
            int headerLength = readIntLe(input);
            if (headerLength < 0 || headerLength > MAX_CRX_HEADER_BYTES) {
                throw new IOException("CRX3 header too large: " + headerLength);
            }
            return idFromCrx3Header(readExactly(input, headerLength));
        }
        throw new IOException("Unsupported CRX version: " + version);
    }

    /**
     * Reads the extension id out of a CRX3 {@code CrxFileHeader} protobuf: preferably the
     * {@code signed_header_data.crx_id} (field 10000), else the hash of the first public key proof.
     */
    private static @Nullable String idFromCrx3Header(byte[] header) {
        byte[] firstPublicKey = null;
        ByteBuffer buffer = ByteBuffer.wrap(header);
        while (buffer.hasRemaining()) {
            long tag = readVarint(buffer);
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            if (wire == 0) {
                readVarint(buffer);
            } else if (wire == 2) {
                byte[] value = new byte[(int) readVarint(buffer)];
                buffer.get(value);
                if (field == 10000) {
                    byte[] crxId = firstLengthDelimited(value, 1);
                    if (crxId != null && crxId.length >= 16) {
                        return encodeId(crxId);
                    }
                } else if ((field == 2 || field == 3) && firstPublicKey == null) {
                    firstPublicKey = firstLengthDelimited(value, 1);
                }
            } else {
                return firstPublicKey == null ? null : idFromPublicKey(firstPublicKey);
            }
        }
        return firstPublicKey == null ? null : idFromPublicKey(firstPublicKey);
    }

    /** Returns the first length-delimited field numbered {@code wanted} in a protobuf message, or {@code null}. */
    private static byte @Nullable [] firstLengthDelimited(byte[] message, int wanted) {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        while (buffer.hasRemaining()) {
            long tag = readVarint(buffer);
            int wire = (int) (tag & 7);
            if (wire == 0) {
                readVarint(buffer);
            } else if (wire == 2) {
                byte[] value = new byte[(int) readVarint(buffer)];
                buffer.get(value);
                if ((int) (tag >>> 3) == wanted) {
                    return value;
                }
            } else {
                return null;
            }
        }
        return null;
    }

    private static long readVarint(ByteBuffer buffer) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = buffer.get();
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("Malformed varint");
    }

    private static int readIntLe(InputStream input) throws IOException {
        return ByteBuffer.wrap(readExactly(input, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        if (length < 0 || length > MAX_CRX_HEADER_BYTES) {
            throw new IOException("Invalid CRX length: " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated CRX header");
        }
        return bytes;
    }

    /** Derives an extension id from a base64 DER public key (the manifest's {@code key} field). */
    private static @Nullable String idFromKey(@Nullable String base64Key) {
        if (base64Key == null) {
            return null;
        }
        try {
            return idFromPublicKey(Base64.getMimeDecoder().decode(base64Key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Chrome's extension id: the first 16 bytes of SHA-256(DER public key), each nibble mapped to {@code a}-{@code p}. */
    static String idFromPublicKey(byte[] publicKey) {
        try {
            return encodeId(MessageDigest.getInstance("SHA-256").digest(publicKey));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encodeId(byte[] bytes) {
        StringBuilder id = new StringBuilder(32);
        for (int i = 0; i < 16; i++) {
            id.append((char) ('a' + ((bytes[i] >> 4) & 0xf))).append((char) ('a' + (bytes[i] & 0xf)));
        }
        return id.toString();
    }

    private static @Nullable JsonNode readJson(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return OBJECT_MAPPER.readTree(text);
    }

    private static @Nullable String text(@Nullable JsonNode object, String property) {
        JsonNode value = object == null ? null : object.get(property);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString().trim();
    }

    private static @Nullable String slugify(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? null : slug;
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** Small entries read from the archive, plus the extension id carried by a CRX header (if any). */
    private record CrxContents(Map<String, byte[]> entries, @Nullable String extensionId) {
    }

    /**
     * Resolves {@code __MSG_key__} placeholders against {@code _locales/<locale>/messages.json},
     * preferring the manifest's {@code default_locale}, then {@code en}, then any packaged locale.
     */
    private static final class Localizer {
        private final Map<String, byte[]> entries;
        private final @Nullable String defaultLocale;

        Localizer(Map<String, byte[]> entries, @Nullable String defaultLocale) {
            this.entries = entries;
            this.defaultLocale = defaultLocale;
        }

        @Nullable String resolve(@Nullable String value) {
            if (value == null || !value.startsWith("__MSG_") || !value.endsWith("__")) {
                return value;
            }
            String key = value.substring("__MSG_".length(), value.length() - 2);
            List<String> candidates = new ArrayList<>();
            if (defaultLocale != null) {
                candidates.add("_locales/" + defaultLocale.toLowerCase(Locale.ROOT) + "/messages.json");
            }
            candidates.add("_locales/en/messages.json");
            entries.keySet().stream().filter(name -> name.startsWith("_locales/")).forEach(candidates::add);
            for (String candidate : candidates) {
                byte[] bytes = entries.get(candidate);
                if (bytes == null) {
                    continue;
                }
                try {
                    JsonNode messages = readJson(bytes);
                    // Message names are case-insensitive in Chrome's i18n.
                    for (Map.Entry<String, JsonNode> message : messages.properties()) {
                        if (message.getKey().equalsIgnoreCase(key)) {
                            String resolved = text(message.getValue(), "message");
                            if (resolved != null) {
                                return resolved;
                            }
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Try the next locale; an unreadable catalog just leaves the placeholder unresolved.
                }
            }
            return value;
        }
    }
}
