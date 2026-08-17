package com.nf3t.artifactsite.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Installs parser plugin artifacts in the local plugin directory.
 */
@Command(name = "add-plugin", description = "Adds a parser plugin JAR to the plugin directory")
public class AddPluginCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddPluginCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Parameters(index = "0", description = "Plugin JAR path")
    private Path pluginJar;

    /**
     * Copies the plugin JAR into the configured plugin directory.
     */
    @Override
    public void run() {
        try {
            // TODO: Check if one with higher version is loaded.
            Path pluginDir = parentCommand.installPluginDir();
            Files.createDirectories(pluginDir);
            Path target = pluginDir.resolve(pluginJar.getFileName());
            Files.copy(pluginJar, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Plugin installed: {}", target);
        } catch (IOException e) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to install plugin", e);
        }
    }
}
