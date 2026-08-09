package io.github.jgwoolley.artifactsite.plugin.vsix;

import io.github.jgwoolley.artifactsite.api.ArtifactInputDescriptor;
import io.github.jgwoolley.artifactsite.api.ArtifactMetadata;
import io.github.jgwoolley.artifactsite.api.ArtifactParseContext;
import io.github.jgwoolley.artifactsite.api.ArtifactParser;
import io.github.jgwoolley.artifactsite.api.ArtifactSourceType;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class VsixArtifactParser implements ArtifactParser {
    @Override
    public boolean supports(ArtifactInputDescriptor descriptor) {
        if (descriptor == null || descriptor.fileName() == null) {
            return false;
        }
        return descriptor.fileName().toLowerCase().endsWith(".vsix")
                || Objects.equals("vsix", descriptor.extension());
    }

    @Override
    public ArtifactMetadata parse(ArtifactInputDescriptor descriptor, ArtifactParseContext context) throws Exception {
        if (descriptor.sourceType() != ArtifactSourceType.LOCAL) {
            throw new IllegalArgumentException("VSIX parser currently supports local files only.");
        }

        Path path = Path.of(descriptor.sourceValue());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("VSIX file does not exist: " + path);
        }

        Element identity = readIdentity(path);

        String artifactId = identity.getAttribute("Id");
        String version = identity.getAttribute("Version");
        String groupId = identity.getAttribute("Publisher");
        String artifactName = readDisplayName(path, artifactId);

        ArtifactMetadata metadata = new ArtifactMetadata();
        metadata.setArtifactId(artifactId);
        metadata.setVersion(version);
        metadata.setGroupId(groupId);
        metadata.setArtifactName(artifactName);
        metadata.setId(groupId + ":" + artifactId + ":" + version);
        metadata.setFileName(path.getFileName().toString());
        metadata.setFileSizeBytes(Files.size(path));
        metadata.setSourceType(descriptor.sourceType().name().toLowerCase());
        metadata.setSourceValue(descriptor.sourceValue());
        metadata.setSha256(context.sha256(path));
        metadata.setPluginId("vsix");
        return metadata;
    }

    private Element readIdentity(Path path) throws Exception {
        Document doc = parseManifest(path);
        NodeList nodes = doc.getElementsByTagName("Identity");
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("VSIX manifest missing Identity node.");
        }
        return (Element) nodes.item(0);
    }

    private String readDisplayName(Path path, String fallback) throws Exception {
        Document doc = parseManifest(path);
        NodeList nodes = doc.getElementsByTagName("DisplayName");
        if (nodes.getLength() == 0) {
            return fallback;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Document parseManifest(Path path) throws Exception {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry manifest = zipFile.getEntry("extension.vsixmanifest");
            if (manifest == null) {
                throw new IllegalArgumentException("VSIX missing extension.vsixmanifest");
            }
            try (InputStream in = zipFile.getInputStream(manifest)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                return factory.newDocumentBuilder().parse(in);
            }
        }
    }
}
