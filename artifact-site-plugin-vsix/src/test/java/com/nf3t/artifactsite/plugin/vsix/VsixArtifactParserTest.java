package com.nf3t.artifactsite.plugin.vsix;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.MapDatum;

class VsixArtifactParserTest {

    private final VsixArtifactParser parser = new VsixArtifactParser();

    private static Datum withFileName(String fileName) {
        Datum datum = new MapDatum();
        ArtifactFields.FILE_NAME.setString(datum, fileName);
        return datum;
    }

    private static IArtifactParseContext capturing(AtomicReference<Datum> metadataRef) {
        return new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                metadataRef.set(artifact);
            }
        };
    }

    @Test
    void supportsVsixFiles() {
        assertThat(parser.supports(withFileName("sample.vsix"))).isTrue();
    }

    @Test
    void parsesManifestIdentity() throws Exception {
        Path vsix = Files.createTempFile("sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <PackageManifest>
                      <Metadata>
                        <Identity Id="artifact-id" Version="1.2.3" Publisher="example.publisher" />
                        <DisplayName>Example Extension</DisplayName>
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("extension/package.json"));
            out.write("""
                    {
                      "displayName": "Package JSON Extension",
                      "description": "A human-readable VSIX description.",
                      "author": {"name": "Jane Developer"},
                      "contributors": [{"name": "John Contributor"}],
                      "repository": {"url": "https://github.com/acme/extension"}
                    }
                    """.getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        Datum metadata = metadataRef.get();
        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("artifact-id");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("1.2.3");
        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("example.publisher");
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Package JSON Extension");
        assertThat(ArtifactFields.DESCRIPTION.getString(metadata)).isEqualTo("A human-readable VSIX description.");
        assertThat(ArtifactFields.AUTHORS.getStringList(metadata)).containsExactly("Jane Developer", "John Contributor");
        assertThat(ArtifactFields.SCM_URL.getString(metadata)).isEqualTo("https://github.com/acme/extension");
        assertThat(ArtifactFields.PLUGIN_ID.getString(metadata)).isEqualTo("vsix");
        assertThat(ArtifactFields.FILE_SHA256.getString(metadata)).isNotBlank();
        assertThat(ArtifactFields.FILE_SIZE.getLong(metadata)).isPositive();
    }

    @Test
    void parsesVscodeStyleManifestWithNamespace() throws Exception {
        Path vsix = Files.createTempFile("vscode-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <PackageManifest xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
                      <Metadata>
                        <Identity Id="vscode-artifact" Version="0.0.1" Publisher="publisher.name" />
                        <DisplayName>VS Code Extension</DisplayName>
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));
        Datum metadata = metadataRef.get();

        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("vscode-artifact");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("0.0.1");
        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("publisher.name");
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("VS Code Extension");
    }

    @Test
    void parsesDisplayNameFromNamespacedMetadata() throws Exception {
        Path vsix = Files.createTempFile("namespaced-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <vsix:PackageManifest xmlns:vsix="http://schemas.microsoft.com/developer/vsx-schema/2011">
                      <vsix:Metadata>
                        <vsix:Identity Id="namespaced-artifact" Version="1.0.0" Publisher="publisher.name" />
                        <vsix:DisplayName>Namespaced Extension</vsix:DisplayName>
                      </vsix:Metadata>
                    </vsix:PackageManifest>
                    """).getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        Datum metadata = metadataRef.get();
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Namespaced Extension");
    }

    @Test
    void parsesReadmeFromExtensionRoot() throws Exception {
        Path vsix = Files.createTempFile("readme-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <PackageManifest>
                      <Metadata>
                        <Identity Id="artifact-id" Version="1.0.0" Publisher="example.publisher" />
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("extension/README.md"));
            out.write("# Extension\n\nSome docs.".getBytes());
            out.closeEntry();
            // A nested README should lose to the one closer to the extension root.
            out.putNextEntry(new ZipEntry("extension/docs/README.md"));
            out.write("# Nested\n\nShould not win.".getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        Datum metadata = metadataRef.get();
        assertThat(ArtifactFields.README.getString(metadata)).isEqualTo("# Extension\n\nSome docs.");
    }

    @Test
    void parsesIconReferencedByPackageJson() throws Exception {
        byte[] iconBytes = "not-really-a-png".getBytes();
        Path vsix = Files.createTempFile("icon-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <PackageManifest>
                      <Metadata>
                        <Identity Id="artifact-id" Version="1.0.0" Publisher="example.publisher" />
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("extension/package.json"));
            out.write("""
                    {
                      "icon": "images/icon.png"
                    }
                    """.getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("extension/images/icon.png"));
            out.write(iconBytes);
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        Datum metadata = metadataRef.get();
        assertThat(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(metadata)).isEqualTo("icon.png");
        assertThat(ArtifactFields.ICON_DATA.getString(metadata))
                .isEqualTo(java.util.Base64.getEncoder().encodeToString(iconBytes));
    }

    @Test
    void leavesIconNullWhenNotDeclared() throws Exception {
        Path vsix = Files.createTempFile("no-icon-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <PackageManifest>
                      <Metadata>
                        <Identity Id="artifact-id" Version="1.0.0" Publisher="example.publisher" />
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        Datum metadata = metadataRef.get();
        assertThat(ArtifactFields.ICON_DATA.getString(metadata)).isNull();
        assertThat(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(metadata)).isNull();
    }

    @Test
    void leavesReadmeNullWhenNotPackaged() throws Exception {
        Path vsix = Files.createTempFile("no-readme-sample", ".vsix");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(vsix))) {
            out.putNextEntry(new ZipEntry("extension.vsixmanifest"));
            out.write(("""
                    <PackageManifest>
                      <Metadata>
                        <Identity Id="artifact-id" Version="1.0.0" Publisher="example.publisher" />
                      </Metadata>
                    </PackageManifest>
                    """).getBytes());
            out.closeEntry();
        }

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        parser.parse(withFileName(vsix.getFileName().toString()), vsix, capturing(metadataRef));

        assertThat(ArtifactFields.README.getString(metadataRef.get())).isNull();
    }
}
