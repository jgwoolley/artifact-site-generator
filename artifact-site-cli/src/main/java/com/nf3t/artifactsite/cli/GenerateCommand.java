package com.nf3t.artifactsite.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;

/**
 * Placeholder command for the site generation milestone.
 */
@Command(name = "generate", description = "Reserved for static site generation milestone")
class GenerateCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateCommand.class);

    /**
     * Prints placeholder output for the future generate implementation.
     */
    @Override
    public void run() {
        LOGGER.info("Generate command scaffolded; implementation comes in later milestones.");
    }
}
