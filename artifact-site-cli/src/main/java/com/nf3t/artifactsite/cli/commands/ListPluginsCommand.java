package com.nf3t.artifactsite.cli.commands;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Lists parser plugins available to the CLI.
 */
@Command(name = "list-plugins", description = "Lists parser plugins")
public class ListPluginsCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListPluginsCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Override
    public void run() {
        List<Path> pluginDirs = parentCommand.pluginLoadDirs();
        LOGGER.info("Plugin Directories: {}", pluginDirs);

        PluginManager pluginManager = parentCommand.createPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        for (PluginWrapper plugin : pluginManager.getPlugins()) {
            LOGGER.info("Plugin '{}' extensions: {}",
                    plugin.getPluginId(),
                    pluginManager.getExtensionClassNames(plugin.getPluginId()));
        }

        List<ArtifactParserPlugin> parsers = parentCommand.loadParserPlugins(pluginManager);
        LOGGER.info("Loaded {} parser plugin(s).", parsers.size());
        parsers.forEach(p -> LOGGER.info("- {}", p.pluginId()));
    }
}
