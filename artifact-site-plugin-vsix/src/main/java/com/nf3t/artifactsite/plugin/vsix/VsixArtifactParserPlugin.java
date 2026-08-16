package com.nf3t.artifactsite.plugin.vsix;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

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
