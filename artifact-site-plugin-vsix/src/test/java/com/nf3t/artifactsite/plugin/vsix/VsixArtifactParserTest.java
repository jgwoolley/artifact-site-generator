package com.nf3t.artifactsite.plugin.vsix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParseContext;
import com.nf3t.artifactsite.api.ArtifactSourceType;

class VsixArtifactParserTest {

    private final VsixArtifactParser parser = new VsixArtifactParser();

    @Test
    void supportsVsixFiles() {
        ArtifactInputDescriptor descriptor = new ArtifactInputDescriptor(
                ArtifactSourceType.LOCAL,
                "/tmp/sample.vsix",
                "sample.vsix",
                "vsix",
                "application/zip");

        assertThat(parser.supports(descriptor)).isTrue();
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
        }

        ArtifactMetadata metadata;
        try (InputStream input = Files.newInputStream(vsix)) {
            metadata = parser.parse(new ArtifactInputDescriptor(
                    ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), input, new ArtifactParseContext());
        }

        assertThat(metadata.getArtifactId()).isEqualTo("artifact-id");
        assertThat(metadata.getVersion()).isEqualTo("1.2.3");
        assertThat(metadata.getGroupId()).isEqualTo("example.publisher");
        assertThat(metadata.getArtifactName()).isEqualTo("Example Extension");
        assertThat(metadata.getPluginId()).isEqualTo("vsix");
        assertThat(metadata.getSha256()).isNotBlank();
        assertThat(metadata.getFileSizeBytes()).isPositive();
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

        ArtifactMetadata metadata;
        try (InputStream input = Files.newInputStream(vsix)) {
            metadata = parser.parse(new ArtifactInputDescriptor(
                    ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), input, new ArtifactParseContext());
        }

        assertThat(metadata.getArtifactId()).isEqualTo("vscode-artifact");
        assertThat(metadata.getVersion()).isEqualTo("0.0.1");
        assertThat(metadata.getGroupId()).isEqualTo("publisher.name");
        assertThat(metadata.getArtifactName()).isEqualTo("VS Code Extension");
    }
}
