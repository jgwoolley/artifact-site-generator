package com.nf3t.artifactsite.plugin.maven;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Developer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jspecify.annotations.Nullable;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.PathUtils;

/**
 * Parser for Maven-published JAR files.
 */
public class MavenArtifactParser implements ArtifactParser {

    private static final String POM_PROPERTIES_SUFFIX = "/pom.properties";
    private static final String MAVEN_METADATA_PREFIX = "META-INF/maven/";
    private static final String ICON_RESOURCE_NAME = "icon.svg";
    private static final String INSTALL_GUIDE_RESOURCE_NAME = "install.html";
    private static final String PARSER_ID = "maven";
    private static final List<String> SEO_TAGS = List.of("maven", "java", "jar", "dependency", "artifact");
    private final MavenXpp3Reader pomReader;

    public MavenArtifactParser() {
        this.pomReader = new MavenXpp3Reader();
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return PARSER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "Maven";
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
    public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context)
            throws Exception {

        ArtifactMetadata metadata = new ArtifactMetadata();
        // ZipFile gives random-access reads straight off the file on disk - entries are
        // located by index and streamed individually, so the JAR's bytes are never buffered
        // into memory as a whole.
        try (ZipFile zipFile = new ZipFile(descriptor.contentPath().toFile())) {
            PomCandidate selected = selectPomCandidate(zipFile, descriptor.fileName())
                    .orElseThrow(() -> new IllegalArgumentException("JAR missing META-INF/maven/**/pom.properties"));

            metadata.setGroupId(selected.groupId());
            metadata.setArtifactId(selected.artifactId());
            metadata.setVersion(selected.version());
            metadata.setArtifactName(selected.artifactId());
            metadata.setId(selected.groupId() + ":" + selected.artifactId() + ":" + selected.version());

            Optional<Model> pomModel = readPomModel(zipFile, selected.path());
            metadata.setDescription(firstNonBlank(
                    pomModel.map(Model::getDescription).orElse(null), selected.description()));
            metadata.setAuthors(pomModel.map(MavenArtifactParser::readDevelopers).orElse(List.of()));
            metadata.setArtifactName(firstNonBlank(
                    pomModel.map(Model::getName).orElse(null), selected.artifactName(), selected.artifactId()));
            metadata.setPluginId(id());
            metadata.setScmUrl(pomModel.flatMap(MavenArtifactParser::readScmUrl).orElse(null));
        }

        metadata.setFileSizeBytes(Files.size(descriptor.contentPath()));
        metadata.setSha256(PathUtils.sha256(descriptor.contentPath()));
        context.writeArtifact(descriptor, metadata);
    }

    private Optional<PomCandidate> selectPomCandidate(ZipFile zipFile, @Nullable String jarFileName) throws Exception {
        String jarBaseName = normalizeJarBaseName(jarFileName);
        return readPomCandidates(zipFile).stream()
                .max(Comparator
                        .comparingInt((PomCandidate candidate) -> scoreCandidate(jarBaseName, candidate.artifactId(), candidate.version()))
                        .thenComparing(candidate -> candidate.path().length(), Comparator.reverseOrder()));
    }

    private List<PomCandidate> readPomCandidates(ZipFile zipFile) throws Exception {
        List<PomCandidate> candidates = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String path = entry.getName();
            if (!path.startsWith(MAVEN_METADATA_PREFIX) || !path.endsWith(POM_PROPERTIES_SUFFIX)) {
                continue;
            }

            Properties properties = new Properties();
            try (InputStream entryStream = zipFile.getInputStream(entry)) {
                // Properties.load streams the entry directly; nothing is buffered up front.
                properties.load(entryStream);
            }

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
        return candidates;
    }

    /**
     * Reads the embedded {@code pom.xml} paired with the given {@code pom.properties} entry
     * using Maven's own {@link MavenXpp3Reader}, rather than hand-rolled DOM parsing.
     */
    private Optional<Model> readPomModel(ZipFile zipFile, String pomPropertiesPath) {
        String pomPath = pomXmlPath(pomPropertiesPath);
        ZipEntry pomEntry = zipFile.getEntry(pomPath);
        if (pomEntry == null) {
            return Optional.empty();
        }
        try (InputStream entryStream = zipFile.getInputStream(pomEntry)) {
            // MavenXpp3Reader.read(InputStream) streams the entry and auto-detects encoding
            // from the XML declaration, returning the fully typed Maven Model.
            return Optional.of(pomReader.read(entryStream, false));
        } catch (Exception ignored) {
            // pom.properties values remain valid fallbacks for malformed or absent POM XML.
            return Optional.empty();
        }
    }

    /** Resolves the SCM URL from a parsed Maven {@link Model}, falling back to the connection string. */
    private static Optional<String> readScmUrl(Model model) {
        Scm scm = model.getScm();
        if (scm == null) {
            return Optional.empty();
        }
        if (!isBlank(scm.getUrl())) {
            return Optional.of(scm.getUrl().trim());
        }
        if (!isBlank(scm.getConnection())) {
            return Optional.of(scm.getConnection().trim());
        }
        return Optional.empty();
    }

    /** Reads developer names from a parsed Maven {@link Model}. */
    private static List<String> readDevelopers(Model model) {
        LinkedHashSet<String> authors = new LinkedHashSet<>();
        for (Developer developer : model.getDevelopers()) {
            String name = trimToNull(developer.getName());
            if (name != null) {
                authors.add(name);
            }
        }
        return new ArrayList<>(authors);
    }

    /** Resolves the POM XML entry paired with a properties entry. */
    private static String pomXmlPath(String pomPropertiesPath) {
        return pomPropertiesPath.substring(0, pomPropertiesPath.length() - "pom.properties".length()) + "pom.xml";
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

    private record PomCandidate(
            String path,
            String groupId,
            String artifactId,
            String version,
            @Nullable String description,
            @Nullable String artifactName) {

    }
}