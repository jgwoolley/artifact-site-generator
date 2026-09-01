package com.nf3t.artifactsite.plugin.vsix;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactSourceType;
import com.nf3t.artifactsite.api.IArtifactParseContext;

class VsixArtifactParserTest {

    private final VsixArtifactParser parser = new VsixArtifactParser();

    @Test
    void supportsVsixFiles() {
        ArtifactInputDescriptor descriptor = new ArtifactInputDescriptor(
                null, ArtifactSourceType.LOCAL,
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

        final AtomicReference<ArtifactMetadata> metadataRef = new AtomicReference<>();
        try (InputStream input = Files.newInputStream(vsix)) {
            parser.parse(new ArtifactInputDescriptor(
            		vsix, ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), new IArtifactParseContext() {
						@Override
						public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
							metadataRef.set(artifact);
						}
            	
            });
        }
        
        ArtifactMetadata metadata = metadataRef.get();
        assertThat(metadata.getArtifactId()).isEqualTo("artifact-id");
        assertThat(metadata.getVersion()).isEqualTo("1.2.3");
        assertThat(metadata.getGroupId()).isEqualTo("example.publisher");
        assertThat(metadata.getArtifactName()).isEqualTo("Package JSON Extension");
        assertThat(metadata.getDescription()).isEqualTo("A human-readable VSIX description.");
        assertThat(metadata.getAuthors()).containsExactly("Jane Developer", "John Contributor");
        assertThat(metadata.getScmUrl()).isEqualTo("https://github.com/acme/extension");
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

        final AtomicReference<ArtifactMetadata> metadataRef = new AtomicReference<>();
        try (InputStream input = Files.newInputStream(vsix)) {
             parser.parse(new ArtifactInputDescriptor(
            		vsix, ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), new IArtifactParseContext() {

						@Override
						public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
							metadataRef.set(artifact);
						}
            	 
             });
        }
        ArtifactMetadata metadata = metadataRef.get();

        assertThat(metadata.getArtifactId()).isEqualTo("vscode-artifact");
        assertThat(metadata.getVersion()).isEqualTo("0.0.1");
        assertThat(metadata.getGroupId()).isEqualTo("publisher.name");
        assertThat(metadata.getArtifactName()).isEqualTo("VS Code Extension");
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

        final AtomicReference<ArtifactMetadata> metadataRef = new AtomicReference<>();
        try (InputStream input = Files.newInputStream(vsix)) {
            parser.parse(new ArtifactInputDescriptor(
                    vsix, ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), new IArtifactParseContext() {

				@Override
				public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
					metadataRef.set(artifact);
				}
    	 
     });
        }
        
        ArtifactMetadata metadata = metadataRef.get();
        assertThat(metadata.getArtifactName()).isEqualTo("Namespaced Extension");
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

        final AtomicReference<ArtifactMetadata> metadataRef = new AtomicReference<>();
        try (InputStream input = Files.newInputStream(vsix)) {
            parser.parse(new ArtifactInputDescriptor(
                    vsix, ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), new IArtifactParseContext() {
                @Override
                public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
                    metadataRef.set(artifact);
                }
            });
        }

        ArtifactMetadata metadata = metadataRef.get();
        assertThat(metadata.getReadme()).isEqualTo("# Extension\n\nSome docs.");
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

        final AtomicReference<ArtifactMetadata> metadataRef = new AtomicReference<>();
        try (InputStream input = Files.newInputStream(vsix)) {
            parser.parse(new ArtifactInputDescriptor(
                    vsix, ArtifactSourceType.LOCAL,
                    vsix.toString(),
                    vsix.getFileName().toString(),
                    "vsix",
                    "application/zip"), new IArtifactParseContext() {
                @Override
                public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
                    metadataRef.set(artifact);
                }
            });
        }

        assertThat(metadataRef.get().getReadme()).isNull();
    }
}
