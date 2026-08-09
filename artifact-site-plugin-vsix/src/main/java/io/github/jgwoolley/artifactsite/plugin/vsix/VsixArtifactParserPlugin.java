package io.github.jgwoolley.artifactsite.plugin.vsix;

import io.github.jgwoolley.artifactsite.api.ArtifactParser;
import io.github.jgwoolley.artifactsite.api.ArtifactParserPlugin;
import org.pf4j.Extension;

@Extension
public class VsixArtifactParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new VsixArtifactParser();

    @Override
    public String pluginId() {
        return "vsix";
    }

    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
