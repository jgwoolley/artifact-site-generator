package com.nf3t.artifactsite.plugin.maven;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParseContext;
import com.nf3t.artifactsite.api.ArtifactParser;

/**
 * Parser for Maven-published JAR files.
 */
public class MavenArtifactParser implements ArtifactParser {

    private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
    private static final String MAVEN_METADATA_PREFIX = "META-INF/maven/";

    /** {@inheritDoc} */
    @Override
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor) {
        if (descriptor == null || descriptor.fileName() == null) {
            return false;
        }

        return descriptor.fileName().toLowerCase().endsWith(".jar")
                || Objects.equals("jar", descriptor.extension());
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactMetadata parse(ArtifactInputDescriptor descriptor, InputStream input, ArtifactParseContext context)
            throws Exception {
        byte[] content = input.readAllBytes();
        PomCandidate selected = selectPomCandidate(content, descriptor.fileName())
                .orElseThrow(() -> new IllegalArgumentException("JAR missing META-INF/maven/**/pom.properties"));

        ArtifactMetadata metadata = new ArtifactMetadata();
        metadata.setGroupId(selected.groupId());
        metadata.setArtifactId(selected.artifactId());
        metadata.setVersion(selected.version());
        metadata.setArtifactName(selected.artifactId());
        metadata.setId(selected.groupId() + ":" + selected.artifactId() + ":" + selected.version());
        PomModel pom = readPom(content, selected.path());
        metadata.setDescription(firstNonBlank(pom.description(), selected.description()));
        metadata.setAuthors(pom.authors());
        metadata.setArtifactName(firstNonBlank(pom.name(), selected.artifactName(), selected.artifactId()));
        metadata.updateFileMetadata(descriptor);
        metadata.setFileSizeBytes(content.length);
        metadata.setSha256(context.sha256(content));
        metadata.setPluginId("maven");
        metadata.setScmUrl(readScmUrl(content, selected.path()).orElse(null));
        return metadata;
    }

    private Optional<PomCandidate> selectPomCandidate(byte[] content, @Nullable String jarFileName) throws Exception {
        String jarBaseName = normalizeJarBaseName(jarFileName);
        return readPomCandidates(content).stream()
                .max(Comparator
                        .comparingInt((PomCandidate candidate) -> scoreCandidate(jarBaseName, candidate.artifactId(), candidate.version()))
                        .thenComparing(candidate -> candidate.path().length(), Comparator.reverseOrder()));
    }

    private List<PomCandidate> readPomCandidates(byte[] content) throws Exception {
        List<PomCandidate> candidates = new ArrayList<>();
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                if (!path.startsWith(MAVEN_METADATA_PREFIX) || !path.endsWith(POM_PROPERTIES_SUFFIX)) {
                    continue;
                }

                Properties properties = new Properties();
                properties.load(new ByteArrayInputStream(readEntryBytes(zipInput)));

                String groupId = trimToNull(properties.getProperty("groupId"));
                String artifactId = trimToNull(properties.getProperty("artifactId"));
                String version = trimToNull(properties.getProperty("version"));
                String description = trimToNull(properties.getProperty("description"));
                String name = trimToNull(properties.getProperty("name"));
                if (groupId == null || artifactId == null || version == null) {
                    continue;
                }
                candidates.add(new PomCandidate(path, groupId, artifactId, version, description, name));
            }
        }
        return candidates;
    }

    private Optional<String> readScmUrl(byte[] content, String pomPropertiesPath) {
        String pomPath = pomXmlPath(pomPropertiesPath);
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!pomPath.equals(entry.getName())) {
                    continue;
                }
                var document = newDocumentBuilderFactory().newDocumentBuilder().parse(zipInput);
                NodeList scmNodes = document.getElementsByTagName("scm");
                if (scmNodes.getLength() == 0) {
                    return Optional.empty();
                }

                Element scm = (Element) scmNodes.item(0);
                String url = readElementText(scm, "url");
                if (!isBlank(url)) {
                    return Optional.of(url.trim());
                }
                String connection = readElementText(scm, "connection");
                if (!isBlank(connection)) {
                    return Optional.of(connection.trim());
                }
                return Optional.empty();
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Reads the embedded POM's human-readable metadata. */
    private PomModel readPom(byte[] content, String pomPropertiesPath) {
        String pomPath = pomXmlPath(pomPropertiesPath);
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (pomPath.equals(entry.getName())) {
                    var document = newDocumentBuilderFactory().newDocumentBuilder().parse(zipInput);
                    Element project = document.getDocumentElement();
                    return new PomModel(
                            readDirectChildText(project, "name"),
                            readDirectChildText(project, "description"),
                            readDevelopers(project));
                }
            }
        } catch (Exception ignored) {
            // Properties values remain valid fallbacks for malformed or absent POM XML.
        }
        return new PomModel(null, null, List.of());
    }

    /** Creates the secure XML parser used for embedded POM files. */
    private static DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /** Resolves the POM XML entry paired with a properties entry. */
    private static String pomXmlPath(String pomPropertiesPath) {
        return pomPropertiesPath.substring(0, pomPropertiesPath.length() - "pom.properties".length()) + "pom.xml";
    }

    /** Reads text from a direct child element. */
    private static @Nullable String readDirectChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && tagName.equals(child.getTagName())) {
                return trimToNull(child.getTextContent());
            }
        }
        return null;
    }

    /** Reads developer names from the embedded Maven POM. */
    private static List<String> readDevelopers(Element project) {
        LinkedHashSet<String> authors = new LinkedHashSet<>();
        NodeList developers = project.getElementsByTagName("developer");
        for (int i = 0; i < developers.getLength(); i++) {
            if (developers.item(i) instanceof Element developer) {
                String name = readDirectChildText(developer, "name");
                if (name != null) {
                    authors.add(name);
                }
            }
        }
        return new ArrayList<>(authors);
    }

    /** Returns the first non-blank candidate. */
    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static int scoreCandidate(String jarBaseName, String artifactId, String version) {
        int score = 0;
        if (jarBaseName.equals(artifactId)) {
            score += 100;
        } else if (jarBaseName.startsWith(artifactId + "-")) {
            score += 70;
        } else if (jarBaseName.contains(artifactId)) {
            score += 25;
        }
        if (jarBaseName.contains(version)) {
            score += 25;
        }
        return score;
    }

    private static String normalizeJarBaseName(@Nullable String jarFileName) {
        if (jarFileName == null) {
            return "";
        }
        String name = jarFileName.toLowerCase();
        if (name.endsWith(".jar")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static byte[] readEntryBytes(ZipInputStream zipInput) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zipInput.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static @Nullable String readElementText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        String text = children.item(0).getTextContent();
        return text == null ? null : text;
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private record PomModel(@Nullable String name, @Nullable String description, List<String> authors) {
    }

    private record PomCandidate(
            String path,
            String groupId,
            String artifactId,
            String version,
            @Nullable String description,
            @Nullable String artifactName) {

    }
}
