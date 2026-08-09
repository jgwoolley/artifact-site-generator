package io.github.jgwoolley.artifactsite.plugin.vsix;

import io.github.jgwoolley.artifactsite.api.ArtifactParser;
import io.github.jgwoolley.artifactsite.api.ArtifactParserPlugin;
import org.pf4j.Extension;

/**
 * PF4J plugin entry point for the VSIX parser.
 */
@Extension
public class VsixArtifactParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new VsixArtifactParser();

    /** {@inheritDoc} */
    @Override
    public String pluginId() {
        return "vsix";
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
