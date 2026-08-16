package com.nf3t.artifactsite.api;

import org.pf4j.ExtensionPoint;

/**
 * PF4J extension point for parser plugins.
 */
public interface ArtifactParserPlugin extends ExtensionPoint {
    /**
     * Returns the stable plugin identifier.
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
