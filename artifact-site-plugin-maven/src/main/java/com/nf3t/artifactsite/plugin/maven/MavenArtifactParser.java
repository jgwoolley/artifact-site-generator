package com.nf3t.artifactsite.plugin.maven;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final MavenXpp3Reader pomReader;
    private final JAXBExtensionManifestParser nifiParser;

    public MavenArtifactParser() {
    	this.nifiParser = new JAXBExtensionManifestParser();
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

        try (ZipFile zipFile = new ZipFile(contentPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("META-INF/docs/extension-manifest.xml");
            if (entry != null) {
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
        
        final StringBuilder readme = new StringBuilder();
        
        final DynamicRelationship dynamicRelationship = extension.getDynamicRelationship();
        if(dynamicRelationship != null) {
        	writeKeyValue(readme, "Dynamic Relationship", "Value");
        	writeKeyValue(readme, "-------", "-------");
        	writeKeyValue(readme, dynamicRelationship.getName(), dynamicRelationship.getDescription());
	        readme.append("\n\n\n\n");
        }
        
        if(extension.getDynamicProperties() != null) {
        	writeKeyValue(readme, "Dynamic Property", "Value");
        	writeKeyValue(readme, "-------", "-------");
            for(DynamicProperty dynamicProperty: extension.getDynamicProperties()) {
	        	writeKeyValue(readme, dynamicProperty.getName(), dynamicProperty.getDescription());
            }
	        readme.append("\n\n\n\n");
        }
        
        if(extension.getProperties() != null) {
        	writeKeyValue(readme, "Property", "Value");
        	writeKeyValue(readme, "-------", "-------");
        	for(Property relationship: extension.getProperties()) {
	        	writeKeyValue(readme, relationship.getName(), relationship.getDescription());
            }
	        readme.append("\n\n\n\n");
        }
        
        if(extension.getRelationships() != null) {
        	writeKeyValue(readme, "Relationship", "Value");
        	writeKeyValue(readme, "-------", "-------");
        	for(Relationship relationship: extension.getRelationships()) {
	        	writeKeyValue(readme, relationship.getName(), relationship.getDescription());
            }
	        readme.append("\n\n\n\n");
        }
        
        if(extension.getReadsAttributes() != null) {
        	writeKeyValue(readme, "Reads Attribute", "Value");
        	writeKeyValue(readme, "-------", "-------");
        	for(Attribute attribute: extension.getReadsAttributes()) {
	        	writeKeyValue(readme, attribute.getName(), attribute.getDescription());
            }
	        readme.append("\n\n\n\n");
        }
             
        if(extension.getWritesAttributes() != null) {
        	writeKeyValue(readme, "Writes Attribute", "Value");
        	writeKeyValue(readme, "-------", "-------");
	        for(Attribute attribute: extension.getWritesAttributes()) {
	        	writeKeyValue(readme, attribute.getName(), attribute.getDescription());
	        }
	        readme.append("\n\n\n\n");
        }
                
        ArtifactFields.README.setString(extensionMetadata, readme.toString());
                
        context.writeArtifact(extensionMetadata, contentPath);
    }
    
    private static void writeKeyValue(StringBuilder builder, String key, String value) {
    	builder.append("| ").append(key).append(" | ").append(value).append(" |\n");
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