package com.nf3t.artifactsite.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Removes plugins in the local plugin directory.
 */
@Command(name = "clear-plugins", description = "Adds a parser plugin JAR to the plugin directory")
public class ClearPluginCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearPluginCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    /**
     * Removes plugins in the local plugin directory.
     */
    @Override
    public void run() {
        try {
            Path pluginDir = parentCommand.installPluginDir();
            if (Files.isDirectory(pluginDir)) {
                Files.list(pluginDir).forEach(pluginPath -> {
                    try {
                        Files.deleteIfExists(pluginPath);
                    } catch (IOException e) {
                        LOGGER.error("Failed to delete: " + pluginPath, e);
                    }
                });
            }

            LOGGER.info("Removed plugins at {}", pluginDir);
        } catch (IOException e) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to install plugin", e);
        }
    }
}
