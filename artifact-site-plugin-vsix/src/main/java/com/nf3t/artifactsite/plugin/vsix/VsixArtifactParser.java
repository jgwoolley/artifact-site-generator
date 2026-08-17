package com.nf3t.artifactsite.plugin.vsix;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import com.nf3t.artifactsite.api.ArtifactParseContext;
import com.nf3t.artifactsite.api.ArtifactParser;

/**
 * Parser for Visual Studio Code VSIX extension packages.
 */
public class VsixArtifactParser implements ArtifactParser {

    private static final String SOURCE_LINK_PROPERTY = "Microsoft.VisualStudio.Services.Links.Source";

    @Override
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor) {
        if (descriptor == null || descriptor.fileName() == null) {
            return false;
        }
        return descriptor.fileName().toLowerCase().endsWith(".vsix")
                || Objects.equals("vsix", descriptor.extension());
    }

    @Override
    public ArtifactMetadata parse(ArtifactInputDescriptor descriptor, InputStream input, ArtifactParseContext context)
            throws Exception {
        byte[] content = input.readAllBytes();

        // 1. Parse the document ONCE
        Document doc = parseManifest(content);

        // 2. Extract values from document
        Element identity = readIdentity(doc);

        String artifactId = identity.getAttribute("Id");
        String version = identity.getAttribute("Version");
        String groupId = identity.getAttribute("Publisher");
        String artifactName = readDisplayName(doc, artifactId);
        
        // Extract the Source URL property
        String sourceUrl = readProperty(doc, SOURCE_LINK_PROPERTY);

        // 3. Populate metadata
        ArtifactMetadata metadata = new ArtifactMetadata();
        metadata.setArtifactId(artifactId);
        metadata.setVersion(version);
        metadata.setGroupId(groupId);
        metadata.setArtifactName(artifactName);
        metadata.setId(groupId + ":" + artifactId + ":" + version);
        metadata.setFileName(descriptor.fileName());
        metadata.setFileSizeBytes(content.length);
        metadata.setSourceType(descriptor.sourceType().name().toLowerCase());
        metadata.setSourceValue(descriptor.sourceValue());
        metadata.setSha256(context.sha256(content));
        metadata.setPluginId("vsix");
        metadata.setScmUrl(sourceUrl);
        
        return metadata;
    }

    /**
     * Finds a <Property Id="..." Value="..." /> entry in the manifest by its Id attribute.
     *
     * @param doc parsed manifest document
     * @param propertyId the property Id to search for
     * @return property value string, or null if not found/blank
     */
    private @Nullable String readProperty(Document doc, String propertyId) {
        NodeList nodes = doc.getElementsByTagName("Property");
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
        NodeList nodes = doc.getElementsByTagName("Identity");
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("VSIX manifest missing Identity node.");
        }
        return (Element) nodes.item(0);
    }

    private String readDisplayName(Document doc, String fallback) {
        NodeList nodes = doc.getElementsByTagName("DisplayName");
        if (nodes.getLength() == 0) {
            return fallback;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Document parseManifest(byte[] content) throws Exception {
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!"extension.vsixmanifest".equals(entry.getName())) {
                    continue;
                }
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                return factory.newDocumentBuilder().parse(zipInput);
            }
            throw new IllegalArgumentException("VSIX missing extension.vsixmanifest");
        }
    }
}