package com.nf3t.artifactsite.cli.commands;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;
import com.nf3t.artifactsite.cli.ArtifactsByParser;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Lists parsed artifacts from the local catalog.
 */
@Command(name = "list-artifacts", description = "Lists artifacts")
public class ListArtifactsCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListArtifactsCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Override
    public void run() {
        List<Path> pluginDirs = parentCommand.pluginLoadDirs();
        LOGGER.info("Plugin Directories: {}", pluginDirs);

        PluginManager pluginManager = parentCommand.createPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        ArtifactsByParser artifacts = parentCommand.loadArtifacts();
        for (ArtifactMetadata artifact : artifacts.save()) {
            LOGGER.info("Artifact: {}", artifact);
        }
    }
}
