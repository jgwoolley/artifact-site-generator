package com.nf3t.artifactsite.plugin.maven;

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

/**
 * PF4J plugin entry point for Maven/JAR parser support.
 */
@Extension
public class MavenArtifactParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new MavenArtifactParser();

    /** {@inheritDoc} */
    @Override
    public String pluginId() {
        return "maven";
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
