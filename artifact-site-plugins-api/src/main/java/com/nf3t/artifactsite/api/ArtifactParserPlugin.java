package com.nf3t.artifactsite.api;

import org.pf4j.ExtensionPoint;

/**
 * PF4J extension point for parser plugins.
 */
public interface ArtifactParserPlugin extends ExtensionPoint {
    /**
     * Returns the stable identifier PF4J uses for this plugin/module. This is independent of
     * {@link ArtifactParser#id()}, which identifies the parser itself and is what's actually
     * used for artifact storage, routing, and grouping (a single plugin could in principle
     * expose a parser with a different id, though today each plugin wraps exactly one parser).
     *
     * @return plugin identifier
     */
    String pluginId();

    /**
     * Returns the parser implementation exposed by this plugin.
     *
     * @return artifact parser
     */
    ArtifactParser parser();
}
