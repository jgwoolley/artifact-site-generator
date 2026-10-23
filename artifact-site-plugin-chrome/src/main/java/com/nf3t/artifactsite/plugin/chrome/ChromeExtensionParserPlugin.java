package com.nf3t.artifactsite.plugin.chrome;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

/**
 * PF4J plugin entry point for the Chrome extension parser.
 */
@Extension
public class ChromeExtensionParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new ChromeExtensionParser();

    /** {@inheritDoc} */
    @Override
    public String pluginId() {
        return "chrome";
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
