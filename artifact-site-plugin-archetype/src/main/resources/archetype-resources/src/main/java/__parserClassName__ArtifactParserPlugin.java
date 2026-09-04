package ${package};

import org.pf4j.Extension;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

/**
 * PF4J plugin entry point for ${parserDisplayName} parser support.
 */
@Extension
public class ${parserClassName}ArtifactParserPlugin implements ArtifactParserPlugin {
    private final ArtifactParser parser = new ${parserClassName}ArtifactParser();

    /** {@inheritDoc} */
    @Override
    public String pluginId() {
        return "${parserId}";
    }

    /** {@inheritDoc} */
    @Override
    public ArtifactParser parser() {
        return parser;
    }
}
