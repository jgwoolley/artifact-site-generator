package ${package};

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.PathUtils;

/**
 * Parser for ${parserDisplayName} artifacts.
 */
public class ${parserClassName}ArtifactParser implements ArtifactParser {

    private static final String ICON_RESOURCE_NAME = "icon.svg";
    private static final String INSTALL_GUIDE_RESOURCE_NAME = "install.html";
    private static final String PARSER_ID = "${parserId}";

    /** {@inheritDoc} */
    @Override
    public String id() {
        return PARSER_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "${parserDisplayName}";
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
        // TODO: return format-specific SEO keywords shown on every page for this parser, on top
        // of each artifact's own tags - e.g. List.of("${parserId}", "..."). Defaults to none.
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor) {
        if (descriptor == null || descriptor.fileName() == null) {
            return false;
        }

        // TODO: match this parser's file extension(s), e.g.:
        // return descriptor.fileName().toLowerCase().endsWith(".ext");
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception {
        ArtifactMetadata metadata = new ArtifactMetadata();

        // TODO: populate the required fields from the artifact's contents.
        // metadata.setGroupId(...);
        // metadata.setArtifactId(...);
        // metadata.setVersion(...);
        // metadata.setArtifactName(...);
        // metadata.setId(metadata.getGroupId() + ":" + metadata.getArtifactId() + ":" + metadata.getVersion());
        metadata.setPluginId(id());

        try (InputStream input = Files.newInputStream(descriptor.contentPath())) {
            metadata.setFileSizeBytes(input.readAllBytes().length);
        }
        metadata.setSha256(PathUtils.sha256(descriptor.contentPath()));

        context.writeArtifact(descriptor, metadata);
    }
}
