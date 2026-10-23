package com.nf3t.artifactsite.plugin.vintagestory;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

/**
 * PF4J plugin entry point for the Vintage Story mod parser.
 */
@Extension
public class VintageStoryModParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new VintageStoryModParser();

    /** {@inheritDoc} */
    @Override
    public String pluginId() {
        return "vintagestory";
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
