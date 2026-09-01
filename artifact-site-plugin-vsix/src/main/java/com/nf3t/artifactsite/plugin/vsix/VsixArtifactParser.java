package com.nf3t.artifactsite.plugin.vsix;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.PathUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parser for Visual Studio Code VSIX extension packages.
 */
public class VsixArtifactParser implements ArtifactParser {

    private static final String SOURCE_LINK_PROPERTY = "Microsoft.VisualStudio.Services.Links.Source";
    private static final String PACKAGE_JSON_SUFFIX = "/package.json";
    private static final String ICON_RESOURCE_NAME = "icon.svg";
    private static final String PARSER_ID = "vsix";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** {@inheritDoc} */
    @Override
    public String id() {
        return PARSER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "VS Code Extension";
    }

    /** {@inheritDoc} */
    @Override
    public String iconResourceName() {
        return ICON_RESOURCE_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor) {
        if (descriptor == null || descriptor.fileName() == null) {
            return false;
        }
        return descriptor.fileName().toLowerCase().endsWith(".vsix")
                || Objects.equals("vsix", descriptor.extension());
    }

    /** {@inheritDoc} */
    @Override
    public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context)
            throws Exception {
    	
        ArtifactMetadata metadata = new ArtifactMetadata();

    	try(InputStream input = Files.newInputStream(descriptor.contentPath())) {
    		byte[] content = input.readAllBytes();

            // 1. Parse the document ONCE
            Document doc = parseManifest(content);

            // 2. Extract values from document
            Element identity = readIdentity(doc);

            String artifactId = identity.getAttribute("Id");
            String version = identity.getAttribute("Version");
            String groupId = identity.getAttribute("Publisher");
            PackageMetadata packageMetadata = readPackageMetadata(content);
            String artifactName = firstNonBlank(packageMetadata.displayName(), readDisplayName(doc, artifactId), artifactId);
            
            // Extract the Source URL property
            String sourceUrl = firstNonBlank(
                    packageMetadata.repositoryUrl(),
                    readProperty(doc, SOURCE_LINK_PROPERTY));

            // 3. Populate metadata
            metadata.setArtifactId(artifactId);
            metadata.setVersion(version);
            metadata.setGroupId(groupId);
            metadata.setArtifactName(artifactName);
            metadata.setDescription(firstNonBlank(packageMetadata.description(), readDescription(doc)));
            metadata.setAuthors(packageMetadata.authors());
            metadata.setId(groupId + ":" + artifactId + ":" + version);
            metadata.setFileSizeBytes(content.length);
            metadata.setPluginId(id());
            metadata.setScmUrl(sourceUrl);
    	}
    	
    	String sha256 = PathUtils.sha256(descriptor.contentPath());
    	metadata.setSha256(sha256);
        context.writeArtifact(descriptor, metadata);
    }

    /**
     * Reads human-readable metadata from the extension's packaged {@code package.json}.
     *
     * @param content complete VSIX content
     * @return package metadata, or empty values when unavailable
     */
    private PackageMetadata readPackageMetadata(byte[] content) {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!"package.json".equals(entry.getName()) && !entry.getName().endsWith(PACKAGE_JSON_SUFFIX)) {
                    continue;
                }
                JsonNode packageJson = OBJECT_MAPPER.readTree(zipInput);
                return new PackageMetadata(
                        readJsonText(packageJson, "displayName"),
                        readJsonText(packageJson, "description"),
                        readAuthors(packageJson),
                        readRepositoryUrl(packageJson));
            }
        } catch (Exception ignored) {
            // Manifest metadata remains available when package.json cannot be read.
        }
        return new PackageMetadata(null, null, List.of(), null);
    }

    /** Reads author and contributor names from package metadata. */
    private List<String> readAuthors(JsonNode packageJson) {
        LinkedHashSet<String> authors = new LinkedHashSet<>();
        addAuthor(authors, packageJson == null ? null : packageJson.get("author"));
        JsonNode contributors = packageJson == null ? null : packageJson.get("contributors");
        if (contributors != null && contributors.isArray()) {
            for (JsonNode contributor : contributors) {
                addAuthor(authors, contributor);
            }
        }
        return new ArrayList<>(authors);
    }

    /** Adds a string or object-form package author to the result set. */
    private void addAuthor(LinkedHashSet<String> authors, @Nullable JsonNode author) {
        if (author == null) {
            return;
        }
        if (author.isTextual()) {
            String name = firstNonBlank(author.asText());
            if (name != null) {
                authors.add(name);
            }
            return;
        }
        String name = readJsonText(author, "name");
        if (name != null) {
            authors.add(name);
        }
    }

    /** Reads the repository URL from the package metadata. */
    private @Nullable String readRepositoryUrl(JsonNode packageJson) {
        JsonNode repository = packageJson == null ? null : packageJson.get("repository");
        if (repository == null) {
            return null;
        }
        if (repository.isTextual()) {
            return firstNonBlank(repository.asText());
        }
        return readJsonText(repository, "url");
    }

    /**
     * Reads a non-blank textual JSON property.
     *
     * @param object JSON object
     * @param property property name
     * @return trimmed property value, or {@code null}
     */
    private @Nullable String readJsonText(JsonNode object, String property) {
        JsonNode value = object == null ? null : object.get(property);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    /**
     * Returns the first non-blank value.
     *
     * @param values candidate values in preference order
     * @return first non-blank value, or {@code null}
     */
    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Finds a <Property Id="..." Value="..." /> entry in the manifest by its Id attribute.
     *
     * @param doc parsed manifest document
     * @param propertyId the property Id to search for
     * @return property value string, or null if not found/blank
     */
    private @Nullable String readProperty(Document doc, String propertyId) {
        NodeList nodes = doc.getElementsByTagNameNS("*", "Property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (propertyId.equals(element.getAttribute("Id"))) {
                String value = element.getAttribute("Value");
                return (value != null && !value.isBlank()) ? value.trim() : null;
            }
        }
        return null;
    }

    private Element readIdentity(Document doc) {
        NodeList nodes = doc.getElementsByTagNameNS("*", "Identity");
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("VSIX manifest missing Identity node.");
        }
        return (Element) nodes.item(0);
    }

    /**
     * Reads the manifest description as a fallback for packages without a description property.
     *
     * @param doc parsed manifest document
     * @return trimmed description, or {@code null}
     */
    private @Nullable String readDescription(Document doc) {
        NodeList nodes = doc.getElementsByTagNameNS("*", "Description");
        if (nodes.getLength() == 0) {
            return null;
        }
        return firstNonBlank(nodes.item(0).getTextContent());
    }

    private String readDisplayName(Document doc, String fallback) {
        NodeList metadataNodes = doc.getElementsByTagNameNS("*", "Metadata");
        if (metadataNodes.getLength() == 0) {
            return fallback;
        }

        Element metadata = (Element) metadataNodes.item(0);
        NodeList displayNameNodes = metadata.getElementsByTagNameNS("*", "DisplayName");
        if (displayNameNodes.getLength() == 0) {
            return fallback;
        }
        String value = displayNameNodes.item(0).getTextContent();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Parses the VSIX manifest document.
     *
     * @param content complete VSIX content
     * @return parsed manifest
     * @throws Exception if the manifest is missing or invalid
     */
    private Document parseManifest(byte[] content) throws Exception {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!"extension.vsixmanifest".equals(entry.getName())) {
                    continue;
                }
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                return factory.newDocumentBuilder().parse(zipInput);
            }
            throw new IllegalArgumentException("VSIX missing extension.vsixmanifest");
        }

    }

    private record PackageMetadata(
            @Nullable String displayName,
            @Nullable String description,
            List<String> authors,
            @Nullable String repositoryUrl) {
    }
}