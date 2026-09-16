package com.nf3t.artifactsite.plugin.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.nifi.extension.manifest.Extension;
import org.apache.nifi.extension.manifest.ExtensionManifest;
import org.apache.nifi.extension.manifest.Property;
import org.apache.nifi.extension.manifest.Relationship;
import org.junit.jupiter.api.Test;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.MapDatum;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

/**
 * Covers the NiFi Archive (.nar) path of {@link MavenArtifactParser}: a NAR is also a JAR (see
 * {@link MavenArtifactParser#ARTIFACT_TYPE_JAR}/{@link MavenArtifactParser#ARTIFACT_TYPE_NAR}),
 * and each bundled extension's manifest data (Properties, Relationships, etc.) is now written as
 * structured {@link NarExtensionFields} record lists rather than a hand-built markdown table.
 */
class MavenArtifactParserNarTest {

    private final MavenArtifactParser parser = new MavenArtifactParser();

    @Test
    void parsesNarExtensionIntoStructuredSections() throws Exception {
        Property property = new Property();
        property.setName("Batch Size");
        property.setDescription("Number of items to process per batch.");

        Relationship relationship = new Relationship();
        relationship.setName("success");
        relationship.setDescription("Successfully processed items.");

        Extension extension = new Extension();
        extension.setName("com.acme.nifi.BatchProcessor");
        extension.setDescription("Processes items in batches.");
        extension.setTags(List.of());
        extension.setProperties(List.of(property));
        extension.setRelationships(List.of(relationship));

        ExtensionManifest manifest = new ExtensionManifest();
        manifest.setVersion("1.2.3");
        manifest.setExtensions(List.of(extension));

        JAXBContext jaxbContext = JAXBContext.newInstance(ExtensionManifest.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        ByteArrayOutputStream manifestXml = new ByteArrayOutputStream();
        marshaller.marshal(manifest, manifestXml);

        Path nar = Files.createTempFile("demo-1.2.3", ".nar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(nar))) {
            out.putNextEntry(new ZipEntry("META-INF/maven/com.acme/demo/pom.properties"));
            out.write("""
                    groupId=com.acme
                    artifactId=demo
                    version=1.2.3
                    """.getBytes());
            out.closeEntry();

            out.putNextEntry(new ZipEntry("META-INF/docs/extension-manifest.xml"));
            out.write(manifestXml.toByteArray());
            out.closeEntry();
        }

        List<Datum> written = new ArrayList<>();
        Datum datum = new MapDatum();
        ArtifactFields.FILE_NAME.setString(datum, "demo-1.2.3.nar");
        parser.parse(datum, nar, new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                written.add(artifact);
            }
        });

        // parseExtension() writes each bundled extension before parse()'s own trailing
        // writeArtifact() call for the parent NAR/JAR record.
        assertThat(written).hasSize(2);
        Datum extensionDatum = written.get(0);
        Datum parent = written.get(1);

        assertThat(ArtifactFields.ARTIFACT_TYPES.getStringList(parent)).containsExactly("jar", "nar");
        assertThat(ArtifactFields.ARTIFACT_TYPES.getStringList(extensionDatum)).containsExactly("nifi-extension");
        assertThat(ArtifactFields.DESCRIPTION.getString(extensionDatum)).isEqualTo("Processes items in batches.");

        assertThat(NarExtensionFields.PROPERTIES.getRecordList(extensionDatum))
                .containsExactly(Map.of("Property", "Batch Size", "Value", "Number of items to process per batch."));
        assertThat(NarExtensionFields.RELATIONSHIPS.getRecordList(extensionDatum))
                .containsExactly(Map.of("Relationship", "success", "Value", "Successfully processed items."));
        assertThat(NarExtensionFields.DYNAMIC_PROPERTIES.getRecordList(extensionDatum)).isEmpty();
    }

    @Test
    void plainJarOnlyCarriesJarArtifactType() throws Exception {
        Path jar = Files.createTempFile("demo-1.2.3", ".jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/maven/com.acme/demo/pom.properties"));
            out.write("""
                    groupId=com.acme
                    artifactId=demo
                    version=1.2.3
                    """.getBytes());
            out.closeEntry();
        }

        List<Datum> written = new ArrayList<>();
        Datum datum = new MapDatum();
        ArtifactFields.FILE_NAME.setString(datum, "demo-1.2.3.jar");
        parser.parse(datum, jar, new IArtifactParseContext() {
            @Override
            public void writeArtifact(Datum artifact, Path contentPath) {
                written.add(artifact);
            }
        });

        assertThat(written).hasSize(1);
        assertThat(ArtifactFields.ARTIFACT_TYPES.getStringList(written.get(0))).containsExactly("jar");
    }
}
