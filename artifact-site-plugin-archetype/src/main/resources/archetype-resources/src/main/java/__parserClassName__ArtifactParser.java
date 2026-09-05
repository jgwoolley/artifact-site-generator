package ${package};

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.Datum;
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
    public boolean supports(@Nullable Datum datum) {
        if (datum == null) {
            return false;
        }
        String fileName = ArtifactFields.FILE_NAME.getString(datum);
        if (fileName == null) {
            return false;
        }

        // TODO: match this parser's file extension(s), e.g.:
        // return fileName.toLowerCase().endsWith(".ext");
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void parse(Datum metadata, Path contentPath, IArtifactParseContext context) throws Exception {
        // TODO: populate the required fields from the artifact's contents. Note metadata may
        // already carry fields written by another parser that also supports() this input, or the
        // CLI itself (e.g. ArtifactFields.FILE_NAME) - only set fields this parser owns.
        // ArtifactFields.GROUP_ID.setString(metadata, ...);
        // ArtifactFields.ARTIFACT_ID.setString(metadata, ...);
        // ArtifactFields.VERSION.setString(metadata, ...);
        // ArtifactFields.NAME.setString(metadata, ...);
        // ArtifactFields.ID.setString(metadata, ArtifactFields.GROUP_ID.getString(metadata) + ":"
        //         + ArtifactFields.ARTIFACT_ID.getString(metadata) + ":" + ArtifactFields.VERSION.getString(metadata));
        ArtifactFields.PLUGIN_ID.setString(metadata, id());

        try (InputStream input = Files.newInputStream(contentPath)) {
            ArtifactFields.FILE_SIZE.setLong(metadata, (long) input.readAllBytes().length);
        }
        ArtifactFields.FILE_SHA256.setString(metadata, PathUtils.sha256(contentPath));

        context.writeArtifact(metadata, contentPath);
    }
}
