package com.nf3t.artifactsite.cli.commands;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Prints current CLI path configuration.
 */
@Command(name = "info", description = "Prints out info")
public class InfoCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfoCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Override
    public void run() {
        Path pluginDir = parentCommand.installPluginDir();
        LOGGER.info("pluginDir - {}", pluginDir);
        Path artifactJsonPath = parentCommand.artifactJsonPath();
        LOGGER.info("artifactJsonPath - {}", artifactJsonPath);
        LOGGER.info("remoteCacheDir - {}", parentCommand.remoteCacheDir());
        LOGGER.info("remoteRequestConfigPath - {}", parentCommand.remoteRequestConfigPath());
    }
}
