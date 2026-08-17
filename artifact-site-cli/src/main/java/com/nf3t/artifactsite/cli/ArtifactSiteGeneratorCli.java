package com.nf3t.artifactsite.cli;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.pf4j.CompoundPluginLoader;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.DefaultPluginManager;
import org.pf4j.DevelopmentPluginLoader;
import org.pf4j.JarPluginLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.cli.commands.AddPluginCommand;
import com.nf3t.artifactsite.cli.commands.ClearPluginCommand;
import com.nf3t.artifactsite.cli.commands.GenerateCommand;
import com.nf3t.artifactsite.cli.commands.InfoCommand;
import com.nf3t.artifactsite.cli.commands.ListArtifactsCommand;
import com.nf3t.artifactsite.cli.commands.ListPluginsCommand;
import com.nf3t.artifactsite.cli.commands.ParseCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * CLI entry point for artifact-site-generator.
 */
@Command(name = "artifact-site-generator", mixinStandardHelpOptions = true, subcommands = {
        ParseCommand.class,
        AddPluginCommand.class,
        GenerateCommand.class,
        ListPluginsCommand.class,
        ListArtifactsCommand.class,
        InfoCommand.class,
        ClearPluginCommand.class,
})
public class ArtifactSiteGeneratorCli implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactSiteGeneratorCli.class);

    @Option(names = "--plugin-dir", scope = CommandLine.ScopeType.INHERIT,
            description = "Additional plugin directory to load from; also used as install target for add-plugin")
    private Path pluginDir;

    @Option(names = "--artifact-json", scope = CommandLine.ScopeType.INHERIT,
            description = "Specifies the artifact json to store parsed artifact information")
    private Path artifactJsonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public Path installPluginDir() {
        return pluginDir == null ? XdgPaths.pluginDir() : pluginDir;
    }

    /***
     *
     * @return
     */
    public Path artifactJsonPath() {
        return artifactJsonPath == null ? XdgPaths.artifactJsonPath() : artifactJsonPath;
    }

    public Path remoteCacheDir() {
        return XdgPaths.remoteCacheDir();
    }

    public Path remoteRequestConfigPath() {
        return XdgPaths.remoteRequestConfigPath();
    }

    public ArtifactsByParser loadArtifacts() {
        Path artifactJsonPath = artifactJsonPath();
        List<ArtifactMetadata> artifactList = new ArrayList<>(1);

        if (Files.exists(artifactJsonPath)) {
            try (InputStream is = Files.newInputStream(artifactJsonPath)) {
            	artifactList = objectMapper.readValue(is, new TypeReference<>() {});
            } catch(Exception e) {
                LOGGER.error("Failed to read artifacts at " + artifactJsonPath, e);
            }
        }

        LOGGER.debug("Read {} artifact(s) to {}", artifactList.size(), artifactJsonPath);

        ArtifactsByParser artifacts = new ArtifactsByParser();
        artifacts.load(artifactList);

        return artifacts;
    }

    public void saveArtifacts(ArtifactsByParser artifacts) {
    	List<ArtifactMetadata> artifactList = artifacts.save();
        Path artifactJsonPath = artifactJsonPath();

        try {
            if (artifactJsonPath.getParent() != null) {
                Files.createDirectories(artifactJsonPath.getParent());
            }
        	objectMapper.writeValue(artifactJsonPath, artifactList);
            LOGGER.info("Wrote {} artifact(s) to {}", artifactList.size(), artifactJsonPath);
        } catch(Exception e) {
            LOGGER.error("Failed to write artifacts", e);
        }
    }

    public RemoteRequestConfigStore loadRemoteRequestConfigStore() {
        Path configPath = remoteRequestConfigPath();
        if (!Files.exists(configPath)) {
            return new RemoteRequestConfigStore();
        }
        try (InputStream is = Files.newInputStream(configPath)) {
            RemoteRequestConfigStore store = objectMapper.readValue(is, RemoteRequestConfigStore.class);
            return store == null ? new RemoteRequestConfigStore() : store;
        } catch (Exception e) {
            LOGGER.error("Failed to read remote request config at " + configPath, e);
            return new RemoteRequestConfigStore();
        }
    }

    public void saveRemoteRequestConfigStore(RemoteRequestConfigStore store) {
        Path configPath = remoteRequestConfigPath();
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            objectMapper.writeValue(configPath, store);
        } catch (Exception e) {
            LOGGER.error("Failed to write remote request config", e);
        }
    }

    public List<Path> pluginLoadDirs() {
        Path xdgPluginDir = XdgPaths.pluginDir();
        if (pluginDir == null || pluginDir.equals(xdgPluginDir)) {
            return List.of(xdgPluginDir);
        }

        return List.of(xdgPluginDir, pluginDir);
    }

    public PluginManager createPluginManager() {
        return new ArtifactSitePluginManager(pluginLoadDirs());
    }

    public List<ArtifactParserPlugin> loadParserPlugins(PluginManager pluginManager) {
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
            if (name.startsWith("com.nf3t.artifactsite.api.")
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
}
