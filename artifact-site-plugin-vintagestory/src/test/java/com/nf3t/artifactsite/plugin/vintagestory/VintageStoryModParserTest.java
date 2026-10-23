package com.nf3t.artifactsite.plugin.vintagestory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

class VintageStoryModParserTest {

    private final VintageStoryModParser parser = new VintageStoryModParser();
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

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path file = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes());
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    void supportsZipFilesOnly() {
        assertThat(parser.supports(withFileName("mod_1.0.0.zip"))).isTrue();
        assertThat(parser.supports(withFileName("mod.crx"))).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void parsesLenientModinfoWithCaseInsensitiveKeys() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("modinfo.json", """
                {
                  // Vintage Story tolerates comments and odd casing
                  "Type": "code",
                  "ModID": "examplemod",
                  "Name": "Example Mod",
                  "Authors": ["Alice", "Bob"],
                  "Description": "Adds things.",
                  "Version": "1.0.4",
                  "Website": "https://example.com",
                  "Side": "Universal",
                  "RequiredOnServer": false,
                  "Dependencies": {"game": "1.22.7", "survival": "1.22.7",},
                }
                """);
        entries.put("ExampleMod.dll", "dll");
        entries.put("modicon.png", "png-bytes");
        entries.put("assets/examplemod/lang/en.json", "{}");
        Path file = zip("examplemod_1.0.4.zip", entries);

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName(file.getFileName().toString()), file, capturing(ref));

        Datum metadata = ref.get();
        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("examplemod");
        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("vintagestory");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("1.0.4");
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Example Mod");
        assertThat(ArtifactFields.DESCRIPTION.getString(metadata)).isEqualTo("Adds things.");
        assertThat(ArtifactFields.AUTHORS.getStringList(metadata)).containsExactly("Alice", "Bob");
        assertThat(ArtifactFields.SCM_URL.getString(metadata)).isEqualTo("https://example.com");
        assertThat(ArtifactFields.ARTIFACT_TYPES.getStringList(metadata)).containsExactly("vintagestory-mod");
        assertThat(ArtifactFields.TAGS.getStringList(metadata)).containsExactly("code");
        assertThat(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(metadata)).isEqualTo("modicon.png");
        assertThat(ArtifactFields.ICON_DATA.getString(metadata)).isNotBlank();
        assertThat(VintageStoryModFields.DETAILS.getString(metadata))
                .contains("Side:** Universal", "Required on server:** no", "`ExampleMod.dll`", "`examplemod`");
        assertThat(VintageStoryModFields.DEPENDENCIES.getRecordList(metadata)).containsExactly(
                Map.of("Dependency", "game", "Version", "1.22.7"),
                Map.of("Dependency", "survival", "Version", "1.22.7"));
    }

    @Test
    void derivesModIdFromNameAndReadsLegacyDependencyArray() throws Exception {
        Path file = zip("legacy.zip", Map.of("modinfo.json", """
                {"name": "Fancy Mod!", "version": "2.0.0",
                 "dependencies": [{"modid": "game", "version": "1.19.0"}, {"modid": "other"}]}
                """));

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(withFileName("legacy.zip"), file, capturing(ref));

        assertThat(ArtifactFields.ARTIFACT_ID.getString(ref.get())).isEqualTo("fancymod");
        assertThat(VintageStoryModFields.DEPENDENCIES.getRecordList(ref.get())).containsExactly(
                Map.of("Dependency", "game", "Version", "1.19.0"),
                Map.of("Dependency", "other", "Version", "*"));
    }

    @Test
    void skipsZipWithoutModinfoWithoutTouchingDatum() throws Exception {
        Path file = zip("other.zip", Map.of("manifest.json", "{}"));
        Datum datum = withFileName("other.zip");

        AtomicReference<Datum> ref = new AtomicReference<>();
        parser.parse(datum, file, capturing(ref));

        assertThat(ref.get()).isNull();
        assertThat(ArtifactFields.PLUGIN_ID.getString(datum)).isNull();
    }
}
