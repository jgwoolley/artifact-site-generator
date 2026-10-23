package com.nf3t.artifactsite.plugin.chrome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.MapDatum;

class ChromeExtensionParserTest {

    private static final String MANIFEST = """
            {
              // Chrome allows comments in manifest.json
              "manifest_version": 3,
              "name": "__MSG_extName__",
              "version": "1.2.3",
              "description": "Does things.",
              "default_locale": "en",
              "author": "Jane Developer",
              "homepage_url": "https://example.com/ext",
              "minimum_chrome_version": "110",
              "permissions": ["storage", "tabs"],
              "host_permissions": ["https://example.com/*"],
              "optional_permissions": ["history"],
              "icons": {"16": "icons/16.png", "128": "icons/128.png"},
              "background": {"service_worker": "sw.js"},
            }
            """;

    private final ChromeExtensionParser parser = new ChromeExtensionParser();
    @TempDir
    Path dir;

    private static Datum withFileName(String fileName) {
        Datum datum = new MapDatum();
        ArtifactFields.FILE_NAME.setString(datum, fileName);
        return datum;
    }

    private static IArtifactParseContext capturing(AtomicReference<Datum> ref) {
        return new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                ref.set(artifact);
            }
        };
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> sampleEntries() {
        return Map.of(
                "manifest.json", MANIFEST,
                "_locales/en/messages.json", "{\"extName\": {\"message\": \"Example Extension\"}}",
                "icons/128.png", "png-bytes",
                "README.md", "# Hello");
    }

    @Test
    void supportsCrxAndZipFiles() {
        assertThat(parser.supports(withFileName("a.crx"))).isTrue();
        assertThat(parser.supports(withFileName("a.ZIP"))).isTrue();
        assertThat(parser.supports(withFileName("a.vsix"))).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void parsesPlainZip() throws Exception {
        Path file = dir.resolve("ext.zip");
        Files.write(file, zip(sampleEntries()));

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName("ext.zip"), file, capturing(ref));

        Datum metadata = ref.get();
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Example Extension");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("1.2.3");
        assertThat(ArtifactFields.DESCRIPTION.getString(metadata)).isEqualTo("Does things.");
        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("example-extension");
        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("chrome");
        assertThat(ArtifactFields.AUTHORS.getStringList(metadata)).containsExactly("Jane Developer");
        assertThat(ArtifactFields.SCM_URL.getString(metadata)).isEqualTo("https://example.com/ext");
        assertThat(ArtifactFields.README.getString(metadata)).isEqualTo("# Hello");
        assertThat(ArtifactFields.ARTIFACT_TYPES.getStringList(metadata)).containsExactly("chrome-extension");
        assertThat(ArtifactFields.TAGS.getStringList(metadata)).containsExactly("manifest-v3");
        assertThat(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(metadata)).isEqualTo("128.png");
        assertThat(ArtifactFields.ICON_DATA.getString(metadata)).isNotBlank();
        assertThat(ChromeExtensionFields.DETAILS.getString(metadata))
                .contains("Manifest version:** 3", "Minimum Chrome version:** 110", "`background`");
        assertThat(ChromeExtensionFields.PERMISSIONS.getRecordList(metadata)).containsExactly(
                Map.of("Permission", "storage", "Kind", "Required"),
                Map.of("Permission", "tabs", "Kind", "Required"),
                Map.of("Permission", "https://example.com/*", "Kind", "Host"),
                Map.of("Permission", "history", "Kind", "Optional"));
    }

    @Test
    void parsesExtensionNestedUnderTopLevelFolderLikeAGithubArchive() throws Exception {
        Path file = dir.resolve("v1.3.zip");
        Map<String, String> nested = new java.util.LinkedHashMap<>();
        sampleEntries().forEach((name, content) -> nested.put("Repo-1.3/" + name, content));
        nested.put("Repo-1.3/node_modules/dep/deep/er/manifest.json", "{\"name\": \"not me\"}");
        Files.write(file, zip(nested));

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName("v1.3.zip"), file, capturing(ref));

        Datum metadata = ref.get();
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Example Extension");
        assertThat(ArtifactFields.README.getString(metadata)).isEqualTo("# Hello");
        assertThat(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(metadata)).isEqualTo("128.png");
    }

    @Test
    void parsesCrx2AndDerivesExtensionIdFromPublicKey() throws Exception {
        byte[] publicKey = "fake-public-key".getBytes();
        byte[] signature = "fake-signature".getBytes();
        ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        header.put("Cr24".getBytes()).putInt(2).putInt(publicKey.length).putInt(signature.length);
        ByteArrayOutputStream crx = new ByteArrayOutputStream();
        crx.write(header.array());
        crx.write(publicKey);
        crx.write(signature);
        crx.write(zip(sampleEntries()));
        Path file = dir.resolve("ext.crx");
        Files.write(file, crx.toByteArray());

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName("ext.crx"), file, capturing(ref));

        String expectedId = ChromeExtensionParser.idFromPublicKey(publicKey);
        assertThat(expectedId).hasSize(32).matches("[a-p]{32}");
        assertThat(ArtifactFields.ARTIFACT_ID.getString(ref.get())).isEqualTo(expectedId);
        assertThat(ArtifactFields.ID.getString(ref.get())).isEqualTo("chrome:" + expectedId + ":1.2.3");
    }

    @Test
    void parsesCrx3AndReadsCrxIdFromSignedHeaderData() throws Exception {
        byte[] crxId = new byte[16];
        for (int i = 0; i < crxId.length; i++) {
            crxId[i] = (byte) (i * 17);
        }
        // SignedData { bytes crx_id = 1; }
        ByteArrayOutputStream signedData = new ByteArrayOutputStream();
        signedData.write(0x0a);
        signedData.write(crxId.length);
        signedData.write(crxId);
        // CrxFileHeader { bytes signed_header_data = 10000; }  tag = 10000<<3|2 = 80002 -> varint 0x82 0xF1 0x04
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(new byte[] {(byte) 0x82, (byte) 0xF1, 0x04});
        header.write(signedData.size());
        header.write(signedData.toByteArray());

        ByteBuffer prefix = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        prefix.put("Cr24".getBytes()).putInt(3).putInt(header.size());
        ByteArrayOutputStream crx = new ByteArrayOutputStream();
        crx.write(prefix.array());
        crx.write(header.toByteArray());
        crx.write(zip(sampleEntries()));
        Path file = dir.resolve("ext3.crx");
        Files.write(file, crx.toByteArray());

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName("ext3.crx"), file, capturing(ref));

        assertThat(ArtifactFields.ARTIFACT_ID.getString(ref.get())).isEqualTo(expectedFor(crxId));
    }

    private static String expectedFor(byte[] crxId) {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            id.append((char) ('a' + ((crxId[i] >> 4) & 0xf))).append((char) ('a' + (crxId[i] & 0xf)));
        }
        return id.toString();
    }

    @Test
    void skipsZipWithoutManifestWithoutTouchingDatum() throws Exception {
        Path file = dir.resolve("other.zip");
        Files.write(file, zip(Map.of("modinfo.json", "{}")));
        Datum datum = withFileName("other.zip");

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(datum, file, capturing(ref));

        assertThat(ref.get()).isNull();
        assertThat(ArtifactFields.PLUGIN_ID.getString(datum)).isNull();
    }

    @Test
    void rejectsCrxWithoutManifest() throws Exception {
        ByteBuffer prefix = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        prefix.put("Cr24".getBytes()).putInt(2).putInt(0).putInt(0);
        ByteArrayOutputStream crx = new ByteArrayOutputStream();
        crx.write(prefix.array());
        crx.write(zip(Map.of("modinfo.json", "{}")));
        Path file = dir.resolve("bad.crx");
        Files.write(file, crx.toByteArray());

        assertThatThrownBy(() -> parser.parse(withFileName("bad.crx"), file, capturing(new AtomicReference<>())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
