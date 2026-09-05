package com.nf3t.artifactsite.plugin.maven;

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

class MavenArtifactParserTest {

    private final MavenArtifactParser parser = new MavenArtifactParser();

    private static Datum withFileName(String fileName) {
        Datum datum = new MapDatum();
        ArtifactFields.FILE_NAME.setString(datum, fileName);
        return datum;
    }

    @Test
    void supportsJarFiles() {
        assertThat(parser.supports(withFileName("sample.jar"))).isTrue();
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

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        Datum datum = withFileName("demo-1.2.3.jar");
        parser.parse(datum, jar, new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                metadataRef.set(artifact);
            }
        });

        Datum metadata = metadataRef.get();

        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("com.acme");
        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("demo");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("1.2.3");
        assertThat(ArtifactFields.NAME.getString(metadata)).isEqualTo("Demo Project");
        assertThat(ArtifactFields.DESCRIPTION.getString(metadata)).isEqualTo("A human-readable demo description.");
        assertThat(ArtifactFields.AUTHORS.getStringList(metadata)).containsExactly("Jane Developer");
        assertThat(ArtifactFields.ID.getString(metadata)).isEqualTo("com.acme:demo:1.2.3");
        assertThat(ArtifactFields.PLUGIN_ID.getString(metadata)).isEqualTo("maven");
        assertThat(ArtifactFields.SCM_URL.getString(metadata)).isEqualTo("https://github.com/acme/demo");
        assertThat(ArtifactFields.FILE_SHA256.getString(metadata)).isNotBlank();
        assertThat(ArtifactFields.FILE_SIZE.getLong(metadata)).isPositive();
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

        final AtomicReference<Datum> metadataRef = new AtomicReference<>();
        Datum datum = withFileName("demo-1.2.3-all.jar");
        parser.parse(datum, jar, new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                metadataRef.set(artifact);
            }
        });
        Datum metadata = metadataRef.get();

        assertThat(ArtifactFields.GROUP_ID.getString(metadata)).isEqualTo("com.acme");
        assertThat(ArtifactFields.ARTIFACT_ID.getString(metadata)).isEqualTo("demo");
        assertThat(ArtifactFields.VERSION.getString(metadata)).isEqualTo("1.2.3");
    }
}
