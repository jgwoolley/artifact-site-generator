package com.nf3t.artifactsite.plugin.maven;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Developer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.nifi.extension.manifest.Attribute;
import org.apache.nifi.extension.manifest.DynamicProperty;
import org.apache.nifi.extension.manifest.DynamicRelationship;
import org.apache.nifi.extension.manifest.Extension;
import org.apache.nifi.extension.manifest.ExtensionManifest;
import org.apache.nifi.extension.manifest.Property;
import org.apache.nifi.extension.manifest.Relationship;
import org.apache.nifi.extension.manifest.parser.jaxb.JAXBExtensionManifestParser;
import org.jspecify.annotations.Nullable;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.Datum;
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
    /** Artifact type tag (see {@link ArtifactFields#ARTIFACT_TYPES}) for every JAR/WAR/NAR this parser handles. */
    static final String ARTIFACT_TYPE_JAR = "jar";
    /** Artifact type tag added on top of {@link #ARTIFACT_TYPE_JAR} when a NiFi Archive manifest is found. */
    static final String ARTIFACT_TYPE_NAR = "nar";
    /** Artifact type tag for each NiFi extension's own child {@link Datum}, see {@link #parseExtension}. */
    static final String ARTIFACT_TYPE_NIFI_EXTENSION = "nifi-extension";
    private final MavenXpp3Reader pomReader;
    private final JAXBExtensionManifestParser nifiParser;

    public MavenArtifactParser() {
        this.nifiParser = createNifiParser();
        this.pomReader = new MavenXpp3Reader();
    }

    /**
     * Builds the NiFi extension manifest parser.
     *
     * <p>{@link JAXBExtensionManifestParser} resolves its JAXB provider via
     * {@code ServiceLoader}, which looks up {@code Thread.currentThread().getContextClassLoader()}
     * rather than the classloader that loaded this class. Under pf4j, the thread's context
     * classloader is the host application's classloader, which does not see the JAXB runtime
     * shaded into this plugin's jar, so the lookup must be pointed at this class's own
     * classloader for the duration of construction.
     */
    private static JAXBExtensionManifestParser createNifiParser() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(MavenArtifactParser.class.getClassLoader());
        try {
            return new JAXBExtensionManifestParser();
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
        }
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
    public boolean supports(@Nullable Datum datum) {
        if (datum == null) {
            return false;
        }
        String fileName = ArtifactFields.FILE_NAME.getString(datum);
        if (fileName == null) {
            return false;
        }
        String lowerCaseFileName = fileName.toLowerCase();
        // A NiFi Archive (.nar) is a Maven-built JAR with the same META-INF/maven/**/pom.properties
        // layout, plus an optional META-INF/docs/extension-manifest.xml this parser also reads.
        return lowerCaseFileName.endsWith(".jar") || lowerCaseFileName.endsWith(".nar") || lowerCaseFileName.endsWith(".war");
    }

    /** {@inheritDoc} */
    @Override
    public void parse(Datum metadata, Path contentPath, IArtifactParseContext context)
            throws Exception {

        String fileName = ArtifactFields.FILE_NAME.getString(metadata);
        // ZipFile gives random-access reads straight off the file on disk - entries are
        // located by index and streamed individually, so the JAR's bytes are never buffered
        // into memory as a whole.
        try (ZipFile zipFile = new ZipFile(contentPath.toFile())) {
            PomCandidate selected = selectPomCandidate(zipFile, fileName)
                    .orElseThrow(() -> new IllegalArgumentException("JAR missing META-INF/maven/**/pom.properties"));

            ArtifactFields.GROUP_ID.setString(metadata, selected.groupId());
            ArtifactFields.ARTIFACT_ID.setString(metadata, selected.artifactId());
            ArtifactFields.VERSION.setString(metadata, selected.version());
            ArtifactFields.ID.setString(metadata, selected.groupId() + ":" + selected.artifactId() + ":" + selected.version());

            Optional<Model> pomModel = readPomModel(zipFile, selected.path());
            ArtifactFields.DESCRIPTION.setString(metadata, firstNonBlank(
                    pomModel.map(Model::getDescription).orElse(null), selected.description()));
            ArtifactFields.AUTHORS.getStringList(metadata)
                    .addAll(pomModel.map(MavenArtifactParser::readDevelopers).orElse(List.of()));
            ArtifactFields.NAME.setString(metadata, firstNonBlank(
                    pomModel.map(Model::getName).orElse(null), selected.artifactName(), selected.artifactId()));
            ArtifactFields.PLUGIN_ID.setString(metadata, id());
            ArtifactFields.SCM_URL.setString(metadata, pomModel.flatMap(MavenArtifactParser::readScmUrl).orElse(null));
        }

        ArtifactFields.ARTIFACT_TYPES.getStringList(metadata).add(ARTIFACT_TYPE_JAR);

        try (ZipFile zipFile = new ZipFile(contentPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/docs/extension-manifest.xml");
            if (entry != null) {
                ArtifactFields.ARTIFACT_TYPES.getStringList(metadata).add(ARTIFACT_TYPE_NAR);
                try (InputStream is = zipFile.getInputStream(entry)) {
                    ExtensionManifest manifest = this.nifiParser.parse(is);

                    for(Extension extension: manifest.getExtensions()) {
                    	parseExtension(context, contentPath, metadata, manifest, extension);
                    }
                }
            }
        }
        
        ArtifactFields.FILE_SIZE.setLong(metadata, Files.size(contentPath));
        ArtifactFields.FILE_SHA256.setString(metadata, PathUtils.sha256(contentPath));
        context.writeArtifact(metadata, contentPath);
    }

    private void parseExtension(IArtifactParseContext context, Path contentPath, Datum parent, ExtensionManifest manifest, Extension extension) {
    	final Datum extensionMetadata = context.createDatum();
        NarExtensionFields.HAS_EXTENSION.setReference(NarExtensionFields.HAS_NAR, parent, extensionMetadata);
        final String id = extension.getName();
        final String name = simpleClassName(id);
        ArtifactFields.NAME.setString(extensionMetadata, name);
        final String description = extension.getDescription();
        ArtifactFields.DESCRIPTION.setString(extensionMetadata, description);
        final String version = manifest.getVersion();
        ArtifactFields.VERSION.setString(extensionMetadata, version);
        final String groupId = ArtifactFields.GROUP_ID.getString(parent);
        final String sourceType = ArtifactFields.SOURCE_TYPE.getString(parent);
        ArtifactFields.PLUGIN_ID.setString(extensionMetadata, id());
        ArtifactFields.GROUP_ID.setString(extensionMetadata, groupId);
        ArtifactFields.ARTIFACT_ID.setString(extensionMetadata, name);
        ArtifactFields.ID.setString(extensionMetadata, id);
        ArtifactFields.SOURCE_TYPE.setString(extensionMetadata, sourceType);
        final List<String> tags = extension.getTags();
        ArtifactFields.TAGS.getStringList(extensionMetadata).addAll(tags);
        ArtifactFields.ARTIFACT_TYPES.getStringList(extensionMetadata).add(ARTIFACT_TYPE_NIFI_EXTENSION);

        final DynamicRelationship dynamicRelationship = extension.getDynamicRelationship();
        if (dynamicRelationship != null) {
            addRecord(NarExtensionFields.DYNAMIC_RELATIONSHIP, extensionMetadata,
                    "Dynamic Relationship", dynamicRelationship.getName(),
                    dynamicRelationship.getDescription());
        }

        if (extension.getDynamicProperties() != null) {
            for (DynamicProperty dynamicProperty : extension.getDynamicProperties()) {
                addRecord(NarExtensionFields.DYNAMIC_PROPERTIES, extensionMetadata,
                        "Dynamic Property", dynamicProperty.getName(), dynamicProperty.getDescription());
            }
        }

        if (extension.getProperties() != null) {
            for (Property property : extension.getProperties()) {
                addRecord(NarExtensionFields.PROPERTIES, extensionMetadata,
                        "Property", property.getName(), property.getDescription());
            }
        }

        if (extension.getRelationships() != null) {
            for (Relationship relationship : extension.getRelationships()) {
                addRecord(NarExtensionFields.RELATIONSHIPS, extensionMetadata,
                        "Relationship", relationship.getName(), relationship.getDescription());
            }
        }

        if (extension.getReadsAttributes() != null) {
            for (Attribute attribute : extension.getReadsAttributes()) {
                addRecord(NarExtensionFields.READS_ATTRIBUTES, extensionMetadata,
                        "Reads Attribute", attribute.getName(), attribute.getDescription());
            }
        }

        if (extension.getWritesAttributes() != null) {
            for (Attribute attribute : extension.getWritesAttributes()) {
                addRecord(NarExtensionFields.WRITES_ATTRIBUTES, extensionMetadata,
                        "Writes Attribute", attribute.getName(), attribute.getDescription());
            }
        }

        context.writeArtifact(extensionMetadata, contentPath);
    }

    /**
     * Appends one {@code nameColumnLabel -> name, "Value" -> description} row to a table field.
     * The NiFi extension manifest doesn't guarantee either value is present, so both are
     * null-safe (an empty cell renders instead of a missing row).
     */
    private static void addRecord(NarExtensionFields field, Datum extensionMetadata, String nameColumnLabel,
            @Nullable String name, @Nullable String description) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(nameColumnLabel, safe(name));
        row.put("Value", safe(description));
        field.getRecordList(extensionMetadata).add(row);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
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

    /** Reduces a fully-qualified extension class name to its simple class name for display. */
    private static @Nullable String simpleClassName(@Nullable String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return null;
        }
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedName : fullyQualifiedName.substring(lastDot + 1);
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