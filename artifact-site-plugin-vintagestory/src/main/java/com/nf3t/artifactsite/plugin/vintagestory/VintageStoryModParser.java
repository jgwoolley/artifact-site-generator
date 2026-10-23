package com.nf3t.artifactsite.plugin.vintagestory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * Parser for Vintage Story mod packages: {@code .zip} archives with a {@code modinfo.json} (and,
 * for code mods, one or more compiled assemblies) at their root.
 *
 * <p>Vintage Story reads {@code modinfo.json} leniently (comments, trailing commas, case-insensitive
 * property names), so this parser does too. The mod id is the artifact id, and every mod is
 * grouped under the fixed {@value #GROUP_ID} group.
 */
public class VintageStoryModParser implements ArtifactParser {

    /** Artifact type tag (see {@link ArtifactFields#ARTIFACT_TYPES}) for every mod this parser handles. */
    static final String ARTIFACT_TYPE_VINTAGE_STORY_MOD = "vintagestory-mod";

    static final String GROUP_ID = "vintagestory";

    private static final String PARSER_ID = "vintagestory";
    private static final String ICON_RESOURCE_NAME = "icon.svg";
    private static final String INSTALL_GUIDE_RESOURCE_NAME = "install.html";
    private static final List<String> SEO_TAGS = List.of("vintage story", "vintagestory", "mod", "game mod");
    private static final String MODINFO_BASENAME = "modinfo.json";
    private static final String README_BASENAME = "readme.md";
    private static final String ICON_BASENAME_PREFIX = "modicon.";
    private static final int MAX_ENTRY_BYTES = 16 << 20;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
            .build();

    /** {@inheritDoc} */
    @Override
    public String id() {
        return PARSER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "Vintage Story Mod";
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
     * Accepts {@code .zip} files. Because {@code .zip} is generic, a zip that turns out not to contain
     * a {@code modinfo.json} is silently skipped by {@link #parse} without touching the shared datum.
     */
    @Override
    public boolean supports(@Nullable Datum datum) {
        if (datum == null) {
            return false;
        }
        String fileName = ArtifactFields.FILE_NAME.getString(datum);
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /** {@inheritDoc} */
    @Override
    public void parse(Datum metadata, Path contentPath, IArtifactParseContext context) throws Exception {
        ModContents contents = scan(contentPath);
        if (contents.modinfo() == null) {
            // A generic .zip that simply isn't a mod (e.g. a zipped Chrome extension): leave the shared datum alone.
            return;
        }
        Map<String, JsonNode> info = lowerCaseProperties(readJson(contents.modinfo()));

        String name = text(info, "name");
        String modId = firstNonBlank(text(info, "modid"), normalizeModId(name));
        String version = text(info, "version");
        if (modId == null) {
            throw new IllegalArgumentException("Vintage Story modinfo.json has neither modid nor name");
        }
        if (version == null) {
            throw new IllegalArgumentException("Vintage Story modinfo.json has no version");
        }

        ArtifactFields.ARTIFACT_ID.setString(metadata, modId);
        ArtifactFields.GROUP_ID.setString(metadata, GROUP_ID);
        ArtifactFields.VERSION.setString(metadata, version);
        ArtifactFields.NAME.setString(metadata, firstNonBlank(name, modId));
        ArtifactFields.DESCRIPTION.setString(metadata, text(info, "description"));
        ArtifactFields.ID.setString(metadata, GROUP_ID + ":" + modId + ":" + version);
        ArtifactFields.PLUGIN_ID.setString(metadata, id());
        ArtifactFields.ARTIFACT_TYPES.getStringList(metadata).add(ARTIFACT_TYPE_VINTAGE_STORY_MOD);
        ArtifactFields.SCM_URL.setString(metadata, text(info, "website"));
        ArtifactFields.FILE_SIZE.setLong(metadata, Files.size(contentPath));
        ArtifactFields.FILE_SHA256.setString(metadata, PathUtils.sha256(contentPath));

        Set<String> authors = new LinkedHashSet<>(textList(info.get("authors")));
        authors.addAll(textList(info.get("contributors")));
        ArtifactFields.AUTHORS.getStringList(metadata).addAll(authors);
        String type = text(info, "type");
        if (type != null) {
            ArtifactFields.TAGS.getStringList(metadata).add(type.toLowerCase(Locale.ROOT));
        }
        if (contents.readme() != null) {
            ArtifactFields.README.setString(metadata, new String(contents.readme(), StandardCharsets.UTF_8));
        }
        if (contents.icon() != null && contents.icon().length > 0) {
            ArtifactFields.ICON_DATA.setString(metadata, Base64.getEncoder().encodeToString(contents.icon()));
            ArtifactFields.ICON_ORIGINAL_FILE_NAME.setString(metadata, contents.iconName());
        }

        fillDependencies(metadata, info.get("dependencies"));
        VintageStoryModFields.DETAILS.setString(metadata, buildDetails(info, type, contents));

        context.writeArtifact(metadata, contentPath);
    }

    /**
     * Streams the archive once, keeping the shallowest {@code modinfo.json}, README and
     * {@code modicon.*} (the icon is only kept if it sits next to the chosen modinfo, which is checked
     * afterwards), and noting assemblies and asset domains without reading them.
     */
    private ModContents scan(Path contentPath) throws IOException {
        byte[] modinfo = null;
        String modinfoPath = null;
        byte[] readme = null;
        String readmePath = null;
        Map<String, byte[]> icons = new LinkedHashMap<>();
        List<String> assemblies = new ArrayList<>();
        Set<String> assetDomains = new LinkedHashSet<>();

        try (InputStream input = Files.newInputStream(contentPath);
                ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.startsWith("assets/")) {
                    String[] parts = path.split("/");
                    if (parts.length > 2) {
                        assetDomains.add(parts[1]);
                    }
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String baseName = lower.substring(lower.lastIndexOf('/') + 1);
                if (lower.endsWith(".dll") && !lower.contains("/")) {
                    assemblies.add(path);
                } else if (baseName.equals(MODINFO_BASENAME)) {
                    if (modinfoPath == null || path.length() < modinfoPath.length()) {
                        modinfo = zip.readNBytes(MAX_ENTRY_BYTES);
                        modinfoPath = path;
                    }
                } else if (baseName.equals(README_BASENAME)) {
                    if (readmePath == null || path.length() < readmePath.length()) {
                        readme = zip.readNBytes(MAX_ENTRY_BYTES);
                        readmePath = path;
                    }
                } else if (baseName.startsWith(ICON_BASENAME_PREFIX)) {
                    icons.put(path, zip.readNBytes(MAX_ENTRY_BYTES));
                }
            }
        }

        String modinfoDir = modinfoPath == null ? "" : modinfoPath.substring(0, modinfoPath.length() - MODINFO_BASENAME.length());
        byte[] icon = null;
        String iconName = null;
        for (Map.Entry<String, byte[]> candidate : icons.entrySet()) {
            String path = candidate.getKey();
            if (path.substring(0, path.lastIndexOf('/') + 1).equalsIgnoreCase(modinfoDir)) {
                icon = candidate.getValue();
                iconName = path.substring(path.lastIndexOf('/') + 1);
                break;
            }
        }
        return new ModContents(modinfo, readme, icon, iconName, assemblies, assetDomains);
    }

    /**
     * Fills the dependency table from either the current object form ({@code {"game": "1.21.0"}}) or the
     * older array form ({@code [{"modid": "game", "version": "1.21.0"}]}).
     */
    private void fillDependencies(Datum metadata, @Nullable JsonNode dependencies) {
        List<Map<String, String>> rows = VintageStoryModFields.DEPENDENCIES.getRecordList(metadata);
        if (dependencies == null) {
            return;
        }
        if (dependencies.isObject()) {
            for (Map.Entry<String, JsonNode> dependency : dependencies.properties()) {
                addDependency(rows, dependency.getKey(), dependency.getValue().asString(""));
            }
        } else if (dependencies.isArray()) {
            for (JsonNode dependency : dependencies) {
                Map<String, JsonNode> fields = lowerCaseProperties(dependency);
                String modId = text(fields, "modid");
                if (modId != null) {
                    addDependency(rows, modId, text(fields, "version"));
                }
            }
        }
    }

    private void addDependency(List<Map<String, String>> rows, String modId, @Nullable String version) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Dependency", modId);
        row.put("Version", version == null || version.isBlank() ? "*" : version);
        rows.add(row);
    }

    /** Builds the markdown "Mod Info" summary from {@code modinfo.json} and the archive's contents. */
    private String buildDetails(Map<String, JsonNode> info, @Nullable String type, ModContents contents) {
        StringBuilder out = new StringBuilder();
        if (type != null) {
            out.append("- **Type:** ").append(type).append('\n');
        }
        String side = text(info, "side");
        if (side != null) {
            out.append("- **Side:** ").append(side).append('\n');
        }
        appendFlag(out, "Required on client", info.get("requiredonclient"));
        appendFlag(out, "Required on server", info.get("requiredonserver"));
        String networkVersion = text(info, "networkversion");
        if (networkVersion != null) {
            out.append("- **Network version:** ").append(networkVersion).append('\n');
        }
        if (!contents.assemblies().isEmpty()) {
            out.append("- **Assemblies:** `").append(String.join("`, `", contents.assemblies())).append("`\n");
        }
        if (!contents.assetDomains().isEmpty()) {
            out.append("- **Asset domains:** `").append(String.join("`, `", contents.assetDomains())).append("`\n");
        }
        return out.toString();
    }

    private void appendFlag(StringBuilder out, String label, @Nullable JsonNode value) {
        if (value != null && value.isBoolean()) {
            out.append("- **").append(label).append(":** ").append(value.asBoolean() ? "yes" : "no").append('\n');
        }
    }

    /** Vintage Story's fallback mod id: the lower-cased name with every non-alphanumeric character removed. */
    private static @Nullable String normalizeModId(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return id.isEmpty() ? null : id;
    }

    private static JsonNode readJson(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return OBJECT_MAPPER.readTree(text);
    }

    /** Indexes an object's properties by lower-cased name, since Vintage Story ignores property-name case. */
    private static Map<String, JsonNode> lowerCaseProperties(@Nullable JsonNode object) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        if (object != null && object.isObject()) {
            for (Map.Entry<String, JsonNode> property : object.properties()) {
                fields.putIfAbsent(property.getKey().toLowerCase(Locale.ROOT), property.getValue());
            }
        }
        return fields;
    }

    private static @Nullable String text(Map<String, JsonNode> fields, String lowerCaseName) {
        JsonNode value = fields.get(lowerCaseName);
        if (value == null || value.isNull() || value.isObject() || value.isArray() || value.asString("").isBlank()) {
            return null;
        }
        return value.asString().trim();
    }

    private static List<String> textList(@Nullable JsonNode value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isString() && !item.asString().isBlank()) {
                    values.add(item.asString().trim());
                }
            }
        } else if (value.isString() && !value.asString().isBlank()) {
            values.add(value.asString().trim());
        }
        return values;
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record ModContents(
            byte @Nullable [] modinfo,
            byte @Nullable [] readme,
            byte @Nullable [] icon,
            @Nullable String iconName,
            List<String> assemblies,
            Set<String> assetDomains) {
    }
}
