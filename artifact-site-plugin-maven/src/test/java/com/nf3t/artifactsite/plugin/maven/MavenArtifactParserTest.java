package com.nf3t.artifactsite.plugin.maven;

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

class MavenArtifactParserTest {

    private final MavenArtifactParser parser = new MavenArtifactParser();

    @Test
    void supportsJarFiles() {
        ArtifactInputDescriptor descriptor = new ArtifactInputDescriptor(
                ArtifactSourceType.LOCAL,
                "/tmp/sample.jar",
                "sample.jar",
                "jar",
                "application/java-archive");

        assertThat(parser.supports(descriptor)).isTrue();
    }

    @Test
    void parsesPomPropertiesAndScmFromJar() throws Exception {
        Path jar = Files.createTempFile("demo-1.2.3", ".jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/maven/com.acme/demo/pom.properties"));
            out.write("""
                    groupId=com.acme
                    artifactId=demo
                    version=1.2.3
                    """.getBytes());
            out.closeEntry();

            out.putNextEntry(new ZipEntry("META-INF/maven/com.acme/demo/pom.xml"));
            out.write("""
                    <project>
                      <name>Demo Project</name>
                      <description>A human-readable demo description.</description>
                      <developers>
                        <developer><name>Jane Developer</name></developer>
                      </developers>
                      <scm>
                        <url>https://github.com/acme/demo</url>
                      </scm>
                    </project>
                    """.getBytes());
            out.closeEntry();
        }

        ArtifactMetadata metadata;
        try (InputStream input = Files.newInputStream(jar)) {
            metadata = parser.parse(new ArtifactInputDescriptor(
                    ArtifactSourceType.LOCAL,
                    jar.toString(),
                    "demo-1.2.3.jar",
                    "jar",
                    "application/java-archive"), input, new ArtifactParseContext());
        }

        assertThat(metadata.getGroupId()).isEqualTo("com.acme");
        assertThat(metadata.getArtifactId()).isEqualTo("demo");
        assertThat(metadata.getVersion()).isEqualTo("1.2.3");
        assertThat(metadata.getArtifactName()).isEqualTo("Demo Project");
        assertThat(metadata.getDescription()).isEqualTo("A human-readable demo description.");
        assertThat(metadata.getAuthors()).containsExactly("Jane Developer");
        assertThat(metadata.getId()).isEqualTo("com.acme:demo:1.2.3");
        assertThat(metadata.getPluginId()).isEqualTo("maven");
        assertThat(metadata.getScmUrl()).isEqualTo("https://github.com/acme/demo");
        assertThat(metadata.getSha256()).isNotBlank();
        assertThat(metadata.getFileSizeBytes()).isPositive();
    }

    @Test
    void prefersPomPropertiesThatMatchJarNameForUberJars() throws Exception {
        Path jar = Files.createTempFile("demo-1.2.3-all", ".jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/maven/org.other/other/pom.properties"));
            out.write("""
                    groupId=org.other
                    artifactId=other
                    version=9.9.9
                    """.getBytes());
            out.closeEntry();

            out.putNextEntry(new ZipEntry("META-INF/maven/com.acme/demo/pom.properties"));
            out.write("""
                    groupId=com.acme
                    artifactId=demo
                    version=1.2.3
                    """.getBytes());
            out.closeEntry();
        }

        ArtifactMetadata metadata;
        try (InputStream input = Files.newInputStream(jar)) {
            metadata = parser.parse(new ArtifactInputDescriptor(
                    ArtifactSourceType.LOCAL,
                    jar.toString(),
                    "demo-1.2.3-all.jar",
                    "jar",
                    "application/java-archive"), input, new ArtifactParseContext());
        }

        assertThat(metadata.getGroupId()).isEqualTo("com.acme");
        assertThat(metadata.getArtifactId()).isEqualTo("demo");
        assertThat(metadata.getVersion()).isEqualTo("1.2.3");
    }
}
