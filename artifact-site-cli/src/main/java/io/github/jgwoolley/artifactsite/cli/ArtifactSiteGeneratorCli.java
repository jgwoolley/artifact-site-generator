package io.github.jgwoolley.artifactsite.cli;

import io.github.jgwoolley.artifactsite.api.ArtifactParserPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * CLI entry point for artifact-site-generator.
 */
@Command(name = "artifact-site-generator", mixinStandardHelpOptions = true, subcommands = {
        ArtifactSiteGeneratorCli.ParseCommand.class,
        ArtifactSiteGeneratorCli.AddPluginCommand.class,
        ArtifactSiteGeneratorCli.GenerateCommand.class
})
public class ArtifactSiteGeneratorCli implements Runnable {

    /**
     * Application main method.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli()).execute(args);
        System.exit(exitCode);
    }

    /** Prints command usage when no subcommand is provided. */
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /** Installs parser plugin artifacts in the local plugin directory. */
    @Command(name = "add-plugin", description = "Adds a parser plugin JAR to the plugin directory")
    static class AddPluginCommand implements Runnable {
        @Parameters(index = "0", description = "Plugin JAR path")
        private Path pluginJar;

        /** Copies the plugin JAR into the configured plugin directory. */
        @Override
        public void run() {
            try {
                Path pluginDir = XdgPaths.pluginDir();
                Files.createDirectories(pluginDir);
                Path target = pluginDir.resolve(pluginJar.getFileName());
                Files.copy(pluginJar, target);
                System.out.printf("Plugin installed: %s%n", target);
            } catch (IOException e) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to install plugin", e);
            }
        }
    }

    /** Loads parser plugins and prints plugin metadata for an input artifact path. */
    @Command(name = "parse", description = "Loads parser plugins and prints the first plugin that supports the input")
    static class ParseCommand implements Runnable {
        @Parameters(index = "0", description = "Artifact file path")
        private Path artifactPath;

        /** Executes parser plugin discovery and reporting. */
        @Override
        public void run() {
            PluginManager pluginManager = new DefaultPluginManager(XdgPaths.pluginDir());
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
            List<ArtifactParserPlugin> parsers = pluginManager.getExtensions(ArtifactParserPlugin.class);
            System.out.printf("Loaded %d parser plugin(s).%n", parsers.size());
            System.out.printf("Input: %s%n", artifactPath.toAbsolutePath());
            parsers.forEach(p -> System.out.printf("- %s%n", p.pluginId()));
        }
    }

    /** Placeholder command for the site generation milestone. */
    @Command(name = "generate", description = "Reserved for static site generation milestone")
    static class GenerateCommand implements Runnable {
        /** Prints placeholder output for the future generate implementation. */
        @Override
        public void run() {
            System.out.println("Generate command scaffolded; implementation comes in later milestones.");
        }
    }
}
