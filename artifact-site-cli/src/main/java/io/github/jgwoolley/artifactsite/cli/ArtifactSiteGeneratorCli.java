package io.github.jgwoolley.artifactsite.cli;

import io.github.jgwoolley.artifactsite.api.ArtifactParserPlugin;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.DevelopmentPluginLoader;
import org.pf4j.DefaultPluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.pf4j.DefaultPluginManager;
import org.pf4j.JarPluginLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

/**
 * CLI entry point for artifact-site-generator.
 */
@Command(name = "artifact-site-generator", mixinStandardHelpOptions = true, subcommands = {
        ArtifactSiteGeneratorCli.ParseCommand.class,
        ArtifactSiteGeneratorCli.AddPluginCommand.class,
        ArtifactSiteGeneratorCli.GenerateCommand.class,
        ArtifactSiteGeneratorCli.ListPluginsCommand.class,
})
public class ArtifactSiteGeneratorCli implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactSiteGeneratorCli.class);

    @Option(names = "--plugin-dir", scope = CommandLine.ScopeType.INHERIT,
            description = "Additional plugin directory to load from; also used as install target for add-plugin")
    private Path pluginDir;

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
        StringWriter usageWriter = new StringWriter();
        new CommandLine(this).usage(new PrintWriter(usageWriter, true));
        LOGGER.info(System.lineSeparator() + usageWriter);
    }

    Path installPluginDir() {
        return pluginDir == null ? XdgPaths.pluginDir() : pluginDir;
    }

    List<Path> pluginLoadDirs() {
        Path xdgPluginDir = XdgPaths.pluginDir();
        if (pluginDir == null || pluginDir.equals(xdgPluginDir)) {
            return List.of(xdgPluginDir);
        }

        return List.of(xdgPluginDir, pluginDir);
    }

    PluginManager createPluginManager() {
        return new ArtifactSitePluginManager(pluginLoadDirs());
    }

    private static final class ArtifactSitePluginManager extends DefaultPluginManager {
        private ArtifactSitePluginManager(List<Path> pluginsRoots) {
            super(pluginsRoots);
        }

        @Override
        protected PluginLoader createPluginLoader() {
            return new CompoundPluginLoader()
                    .add(new DevelopmentPluginLoader(this) {
                        @Override
                        protected PluginClassLoader createPluginClassLoader(
                                Path pluginPath,
                                PluginDescriptor pluginDescriptor) {
                            return createApplicationFirstPluginClassLoader(pluginDescriptor);
                        }
                    }, this::isDevelopment)
                    .add(new JarPluginLoader(this) {
                        @Override
                        public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
                            PluginClassLoader pluginClassLoader = createApplicationFirstPluginClassLoader(
                                    pluginDescriptor);
                            pluginClassLoader.addFile(pluginPath.toFile());
                            return pluginClassLoader;
                        }
                    }, this::isNotDevelopment)
                    .add(new DefaultPluginLoader(this) {
                        @Override
                        protected PluginClassLoader createPluginClassLoader(
                                Path pluginPath,
                                PluginDescriptor pluginDescriptor) {
                            return createApplicationFirstPluginClassLoader(pluginDescriptor);
                        }
                    }, this::isNotDevelopment);
        }

        private PluginClassLoader createApplicationFirstPluginClassLoader(PluginDescriptor pluginDescriptor) {
            return new ApiParentFirstPluginClassLoader(this, pluginDescriptor, getClass().getClassLoader());
        }
    }

    private static final class ApiParentFirstPluginClassLoader extends PluginClassLoader {
        private ApiParentFirstPluginClassLoader(
                PluginManager pluginManager,
                PluginDescriptor pluginDescriptor,
                ClassLoader parent) {
            super(pluginManager, pluginDescriptor, parent);
        }

        @Override
        public Class<?> loadClass(String className) throws ClassNotFoundException {
            if (className.startsWith("io.github.jgwoolley.artifactsite.api.")
                    || className.startsWith("org.pf4j.")) {
                return getParent().loadClass(className);
            }

            return super.loadClass(className);
        }
    }

    /** Installs parser plugin artifacts in the local plugin directory. */
    @Command(name = "add-plugin", description = "Adds a parser plugin JAR to the plugin directory")
    static class AddPluginCommand implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(AddPluginCommand.class);

        @ParentCommand
        private ArtifactSiteGeneratorCli parentCommand;

        @Parameters(index = "0", description = "Plugin JAR path")
        private Path pluginJar;

        /** Copies the plugin JAR into the configured plugin directory. */
        @Override
        public void run() {
            try {
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

    @Command(name = "list-plugins", description = "Lists parser plugins")
    static class ListPluginsCommand implements Runnable {
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
            List<ArtifactParserPlugin> parsers = pluginManager.getExtensions(ArtifactParserPlugin.class);
            LOGGER.info("Loaded {} parser plugin(s).", parsers.size());
            parsers.forEach(p -> LOGGER.info("- {}", p.pluginId()));
        }
    }

    /** Loads parser plugins and prints plugin metadata for an input artifact path. */
    @Command(name = "parse", description = "Loads parser plugins and prints the first plugin that supports the input")
    static class ParseCommand implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(ParseCommand.class);

        @ParentCommand
        private ArtifactSiteGeneratorCli parentCommand;

        @Parameters(index = "0", description = "Artifact file path")
        private Path artifactPath;

        /** Executes parser plugin discovery and reporting. */
        @Override
        public void run() {
            PluginManager pluginManager = parentCommand.createPluginManager();
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
            List<ArtifactParserPlugin> parsers = pluginManager.getExtensions(ArtifactParserPlugin.class);
            LOGGER.info("Loaded {} parser plugin(s).", parsers.size());
            LOGGER.info("Input: {}", artifactPath.toAbsolutePath());
            parsers.forEach(p -> LOGGER.info("- {}", p.pluginId()));
        }
    }

    /** Placeholder command for the site generation milestone. */
    @Command(name = "generate", description = "Reserved for static site generation milestone")
    static class GenerateCommand implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(GenerateCommand.class);

        /** Prints placeholder output for the future generate implementation. */
        @Override
        public void run() {
            LOGGER.info("Generate command scaffolded; implementation comes in later milestones.");
        }
    }
}
