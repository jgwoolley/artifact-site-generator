package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.AbstractPluginManager;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginLoader;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.ArtifactSourceType;

import picocli.CommandLine;

class ArtifactSiteGeneratorCliTest {
    @TempDir
    Path tempDir;

    @Test
    void addPluginOverwritesExistingJarByDefault() throws IOException {
        Path installDir = tempDir.resolve("plugins");
        Files.createDirectories(installDir);

        Path pluginJar = tempDir.resolve("artifact-site-plugin.jar");
        Files.writeString(pluginJar, "new-plugin-content");
        Path installedJar = installDir.resolve(pluginJar.getFileName());
        Files.writeString(installedJar, "old-plugin-content");

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute("--plugin-dir", installDir.toString(), "add-plugin", pluginJar.toString());

        assertThat(exitCode).isZero();
        assertThat(Files.readString(installedJar)).isEqualTo("new-plugin-content");
    }

    @Test
    void loadParserPluginsFallsBackToArtifactParserExtensions() {
        ArtifactSiteGeneratorCli cli = new ArtifactSiteGeneratorCli();
        ArtifactParser parser = new ArtifactParser() {
            @Override
            public boolean supports(com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor) {
                return false;
            }

            @Override
            public com.nf3t.artifactsite.api.ArtifactMetadata parse(
                    com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor,
                    com.nf3t.artifactsite.api.ArtifactParseContext context) {
                return null;
            }
        };

        DefaultPluginManager pluginManager = new DefaultPluginManager(Collections.emptyList()) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> getExtensions(Class<T> type) {
                if (type == ArtifactParserPlugin.class) {
                    return List.of();
                }
                if (type == ArtifactParser.class) {
                    return (List<T>) List.of(parser);
                }
                return List.of();
            }
        };

        List<ArtifactParserPlugin> plugins = cli.loadParserPlugins(pluginManager);

        assertThat(plugins).hasSize(1);
        assertThat(plugins.get(0).parser()).isSameAs(parser);
    }

    @Test
    void loadParserPluginsPrefersArtifactParserPluginExtensions() {
        ArtifactSiteGeneratorCli cli = new ArtifactSiteGeneratorCli();
        ArtifactParserPlugin plugin = new ArtifactParserPlugin() {
            @Override
            public String pluginId() {
                return "test";
            }

            @Override
            public ArtifactParser parser() {
                return new ArtifactParser() {
                    @Override
                    public boolean supports(com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public com.nf3t.artifactsite.api.ArtifactMetadata parse(
                            com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor,
                            com.nf3t.artifactsite.api.ArtifactParseContext context) {
                        return null;
                    }
                };
            }
        };

        DefaultPluginManager pluginManager = new DefaultPluginManager(Collections.emptyList()) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> getExtensions(Class<T> type) {
                if (type == ArtifactParserPlugin.class) {
                    return (List<T>) List.of(plugin);
                }
                if (type == ArtifactParser.class) {
                    return List.of();
                }
                return List.of();
            }
        };

        List<ArtifactParserPlugin> plugins = cli.loadParserPlugins(pluginManager);

        assertThat(plugins).containsExactly(plugin);
    }

    @Test
    void pluginDirOptionAddsAdditionalDirectoryAlongsideXdgDirectory() {
        Path customPluginDir = tempDir.resolve("custom-plugins");
        ArtifactSiteGeneratorCli cli = new ArtifactSiteGeneratorCli();

        new CommandLine(cli).parseArgs("--plugin-dir", customPluginDir.toString(), "generate");

        assertThat(cli.pluginLoadDirs()).isEqualTo(List.of(XdgPaths.pluginDir(), customPluginDir));
    }

    @Test
    void artifactInputDescriptorParseRemoteResolvesSourceAndExtension() {
        var descriptor = com.nf3t.artifactsite.api.ArtifactInputDescriptor.parseRemote(
                "https://example.com/releases/sample.vsix",
                null,
                "application/zip");

        assertThat(descriptor.sourceType()).isEqualTo(ArtifactSourceType.REMOTE);
        assertThat(descriptor.sourceValue()).isEqualTo("https://example.com/releases/sample.vsix");
        assertThat(descriptor.fileName()).isEqualTo("sample.vsix");
        assertThat(descriptor.extension()).isEqualTo("vsix");
    }

    @Test
    void remoteRequestConfigIsPersistedOutsideArtifactsJson() {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            ArtifactSiteGeneratorCli cli = new ArtifactSiteGeneratorCli();
            RemoteRequestConfigStore store = new RemoteRequestConfigStore();
            RemoteRequestConfig config = new RemoteRequestConfig();
            config.setArtifactId("publisher:artifact:1.0.0");
            config.setSourceType("remote");
            config.setSourceValue("https://example.com/sample.vsix");
            config.setCachedPath(tempDir.resolve("cache/sample.vsix").toString());
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "******");
            config.setHeaders(headers);
            RemoteTlsConfig tls = new RemoteTlsConfig();
            tls.setTrustStorePath(tempDir.resolve("truststore.p12").toString());
            tls.setClientCertificatePath(tempDir.resolve("client.crt").toString());
            tls.setClientPrivateKeyPath(tempDir.resolve("client.key").toString());
            tls.setClientPrivateKeyPassword("secret");
            config.setTls(tls);
            store.put("publisher:artifact:1.0.0", config);

            cli.saveRemoteRequestConfigStore(store);
            RemoteRequestConfigStore loaded = cli.loadRemoteRequestConfigStore();

            assertThat(Files.exists(XdgPaths.remoteRequestConfigPath())).isTrue();
            assertThat(Files.exists(XdgPaths.artifactJsonPath())).isFalse();
            assertThat(loaded.getRequestsByArtifactId()).containsKey("publisher:artifact:1.0.0");
            assertThat(loaded.getRequestsByArtifactId().get("publisher:artifact:1.0.0").getHeaders())
                    .containsEntry("Authorization", "******");
            assertThat(loaded.getRequestsByArtifactId().get("publisher:artifact:1.0.0").getTls())
                    .isNotNull();
            assertThat(loaded.getRequestsByArtifactId().get("publisher:artifact:1.0.0").getTls().getClientPrivateKeyPassword())
                    .isEqualTo("secret");
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    // @Test
    void pluginManagerUsesParentApiClassesWhenPluginJarContainsDuplicateApiClasses() throws Exception {
        ArtifactSiteGeneratorCli cli = new ArtifactSiteGeneratorCli();
        AbstractPluginManager pluginManager = (AbstractPluginManager) cli.createPluginManager();
        PluginLoader pluginLoader = pluginManager.getPluginLoader();

        Path pluginJar = tempDir.resolve("test-plugin.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(pluginJar))) {
            ignored.putNextEntry(new ZipEntry("io/github/jgwoolley/artifactsite/api/ArtifactParserPlugin.class"));
            ignored.write(ArtifactParserPlugin.class
                    .getResourceAsStream("ArtifactParserPlugin.class")
                    .readAllBytes());
            ignored.closeEntry();
        }

        PluginClassLoader classLoader = (PluginClassLoader) pluginLoader.loadPlugin(
                pluginJar,
                new DefaultPluginDescriptor("test-plugin", "test", "test.Plugin", "1.0.0", "*", "test", "test"));
        Class<?> loadedArtifactParserPluginClass = classLoader.loadClass(ArtifactParserPlugin.class.getName());
        assertThat(loadedArtifactParserPluginClass).isEqualTo(ArtifactParserPlugin.class);
    }
}
