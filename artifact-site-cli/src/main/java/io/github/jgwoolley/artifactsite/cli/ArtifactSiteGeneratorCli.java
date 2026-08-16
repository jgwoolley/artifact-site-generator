package io.github.jgwoolley.artifactsite.cli;

import io.github.jgwoolley.artifactsite.api.ArtifactInputDescriptor;
import io.github.jgwoolley.artifactsite.api.ArtifactMetadata;
import io.github.jgwoolley.artifactsite.api.ArtifactParseContext;
import io.github.jgwoolley.artifactsite.api.ArtifactParser;
import io.github.jgwoolley.artifactsite.api.ArtifactParserPlugin;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;

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
import org.pf4j.PluginWrapper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/**
 * CLI entry point for artifact-site-generator.
 */
@Command(name = "artifact-site-generator", mixinStandardHelpOptions = true, subcommands = {
        ArtifactSiteGeneratorCli.ParseCommand.class,
        ArtifactSiteGeneratorCli.AddPluginCommand.class,
        ArtifactSiteGeneratorCli.GenerateCommand.class,
        ArtifactSiteGeneratorCli.ListPluginsCommand.class,
        ArtifactSiteGeneratorCli.ListArtifactsCommand.class,
        ArtifactSiteGeneratorCli.InfoCommand.class,
})
public class ArtifactSiteGeneratorCli implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactSiteGeneratorCli.class);

    @Option(names = "--plugin-dir", scope = CommandLine.ScopeType.INHERIT,
            description = "Additional plugin directory to load from; also used as install target for add-plugin")
    private Path pluginDir;

    @Option(names = "--artifact-json", scope = CommandLine.ScopeType.INHERIT,
            description = "Specifies the artifact json to store parsed artifact information")
    private Path artifactJsonPath;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
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

    /***
     * 
     * @return
     */
    private Path installPluginDir() {
        return pluginDir == null ? XdgPaths.pluginDir() : pluginDir;
    }
    
    /***
     * 
     * @return
     */
    private Path artifactJsonPath() {
        return artifactJsonPath == null ? XdgPaths.artifactJsonPath() : artifactJsonPath;
    }

    private ArtifactsByParser loadArtifacts() {
        Path artifactJsonPath = artifactJsonPath();
        List<ArtifactMetadata> artifactList = new ArrayList<>(1);

        if (Files.exists(artifactJsonPath)) {
            try (InputStream is = Files.newInputStream(artifactJsonPath)) {
            	artifactList = objectMapper.readValue(is, new TypeReference<>() {});            
            } catch(Exception e) {
                LOGGER.error("Failed to read artifacts", e);
            }
        }

        LOGGER.debug("Read Artifacts Count: {}", artifactList.size());
        
        ArtifactsByParser artifacts = new ArtifactsByParser();
        artifacts.load(artifactList);
        
        return artifacts;
    }
    
    private void saveArtifacts(List<ArtifactMetadata> artifacts) {
        Path artifactJsonPath = artifactJsonPath();

        try {                
        	objectMapper.writeValue(artifactJsonPath, artifacts);
            LOGGER.info("Wrote Artifacts: {}", artifacts.size());
        } catch(Exception e) {
            LOGGER.error("Failed to write artifacts", e);
        }
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

    List<ArtifactParserPlugin> loadParserPlugins(PluginManager pluginManager) {
        List<ArtifactParserPlugin> plugins = pluginManager.getExtensions(ArtifactParserPlugin.class);
        if (!plugins.isEmpty()) {
            return plugins;
        }

        List<ArtifactParser> parsers = pluginManager.getExtensions(ArtifactParser.class);
        if (!parsers.isEmpty()) {
            LOGGER.warn(
                    "No ArtifactParserPlugin extensions found; falling back to {} ArtifactParser extension(s).",
                    parsers.size());
        }

        return parsers.stream().map(ParserOnlyPluginAdapter::new).map(ArtifactParserPlugin.class::cast).toList();
    }

    private static final class ParserOnlyPluginAdapter implements ArtifactParserPlugin {
        private final ArtifactParser parser;

        private ParserOnlyPluginAdapter(ArtifactParser parser) {
            this.parser = parser;
        }

        @Override
        public String pluginId() {
            return parser.getClass().getName();
        }

        @Override
        public ArtifactParser parser() {
            return parser;
        }
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
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("io.github.jgwoolley.artifactsite.api.")
                    || name.startsWith("org.pf4j.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            c = getParent().loadClass(name);
                        } catch (ClassNotFoundException ignored) {
                            // Fall through to super if parent doesn't contain the class
                        }
                    }
                    if (c != null) {
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                }
            }

            return super.loadClass(name, resolve);
        }
    }

    @Command(name = "info", description = "Prints out info")
    static class InfoCommand implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(InfoCommand.class);

        @ParentCommand
        private ArtifactSiteGeneratorCli parentCommand;
        
    	@Override
    	public void run() {
    		Path pluginDir = parentCommand.installPluginDir();
        	LOGGER.info("pluginDir - {}", pluginDir);
        	Path artifactJsonPath = parentCommand.artifactJsonPath();
        	LOGGER.info("artifactJsonPath - {}", artifactJsonPath);
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

            // Debug: Check raw extension class names discovered by PF4J
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

    @Command(name = "list-artifacts", description = "Lists artifacts")
    static class ListArtifactsCommand implements Runnable {
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

            for(ArtifactsBySlug artifactsBySlug: artifacts) {
            	for(ArtifactMetadata artifact: artifactsBySlug) {
                    LOGGER.info("Artifact: {}", artifact);
                }
            }
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
            List<ArtifactParserPlugin> plugins = parentCommand.loadParserPlugins(pluginManager);
            LOGGER.info("Loaded {} parser plugin(s).", plugins.size());
            LOGGER.debug("Input Artifact: {}", artifactPath.toAbsolutePath());
            ArtifactInputDescriptor descriptor = ArtifactInputDescriptor.parseLocal(artifactPath);
            ArtifactParseContext context = new ArtifactParseContext();

            Path artifactJsonPath = parentCommand.artifactJsonPath();
            List<ArtifactMetadata> artifacts = new ArrayList<>(1);

            ObjectMapper mapper = new ObjectMapper();

            if (Files.exists(artifactJsonPath)) {
                try (InputStream is = Files.newInputStream(artifactJsonPath)) {
                    artifacts = mapper.readValue(is, new TypeReference<>() {});            
                } catch(Exception e) {
                    LOGGER.error("Failed to read artifacts", e);
                }
            }

            for(ArtifactParserPlugin plugin: plugins) {
                final var parser = plugin.parser();
                if(!parser.supports(descriptor)) {
                    continue;
                }

                try {
                    ArtifactMetadata artifact = parser.parse(descriptor, context);
                    if(artifact == null) continue;
                    LOGGER.debug("Parsed: {}", artifact);
                    // DEDUPLICATE
                    artifacts.add(artifact);
                } catch (Exception e) {
                    LOGGER.warn("Could not parse "+ artifactPath.toString()+ " with " + parser.getClass(), e);
                }
            }
          
            parentCommand.saveArtifacts(artifacts);
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
