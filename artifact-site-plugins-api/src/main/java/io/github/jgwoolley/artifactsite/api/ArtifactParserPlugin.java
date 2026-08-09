package io.github.jgwoolley.artifactsite.api;

import org.pf4j.ExtensionPoint;

public interface ArtifactParserPlugin extends ExtensionPoint {
    String pluginId();

    ArtifactParser parser();
}
