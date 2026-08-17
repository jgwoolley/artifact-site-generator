package com.nf3t.artifactsite.cli;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParseContext;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Loads parser plugins and parses an input artifact path.
 */
@Command(name = "parse", description = "Loads parser plugins and prints the first plugin that supports the input")
class ParseCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Parameters(index = "0", description = "Artifact file path")
    private Path artifactPath;

    /**
     * Executes parser plugin discovery and parsing.
     */
    @Override
    public void run() {
        PluginManager pluginManager = parentCommand.createPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        List<ArtifactParserPlugin> plugins = parentCommand.loadParserPlugins(pluginManager);
        LOGGER.info("Loaded {} parser plugin(s).", plugins.size());
        LOGGER.debug("Input Artifact: {}", artifactPath.toAbsolutePath());
        ArtifactInputDescriptor descriptor = ArtifactInputDescriptor.parseLocal(artifactPath);
        ArtifactParseContext context = new ArtifactParseContext();

        ArtifactsByParser artifacts = parentCommand.loadArtifacts();

        for (ArtifactParserPlugin plugin : plugins) {
            final var parser = plugin.parser();
            if (!parser.supports(descriptor)) {
                continue;
            }

            try {
                ArtifactMetadata artifact = parser.parse(descriptor, context);
                if (artifact == null) {
                    continue;
                }
                LOGGER.debug("Parsed: {}", artifact);
                artifacts.put(artifact);
            } catch (Exception e) {
                LOGGER.warn("Could not parse " + artifactPath + " with " + parser.getClass(), e);
            }
        }

        parentCommand.saveArtifacts(artifacts);
    }
}
