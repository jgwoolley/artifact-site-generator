package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.AbstractPluginManager;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginLoader;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.IArtifactParseContext;

import picocli.CommandLine;
import tools.jackson.databind.ObjectMapper;

class ArtifactSiteGeneratorCliTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
			public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception {
				// TODO Auto-generated method stub
				
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
        			public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception {
        				// TODO Auto-generated method stub
        				
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

    /**
     * Verifies that a later read replaces an equal catalog record.
     */
    @Test
    void laterEqualArtifactReplacesEarlierRecord() {
        ArtifactsByParser artifacts = new ArtifactsByParser();
        com.nf3t.artifactsite.api.ArtifactMetadata first = new com.nf3t.artifactsite.api.ArtifactMetadata();
        first.setPluginId("vsix");
        first.setGroupId("acme");
        first.setArtifactId("demo");
        first.setVersion("1.0.0");
        first.setId("acme:demo:1.0.0");
        first.setDescription("old");

        com.nf3t.artifactsite.api.ArtifactMetadata latest = new com.nf3t.artifactsite.api.ArtifactMetadata();
        latest.setPluginId("vsix");
        latest.setGroupId("acme");
        latest.setArtifactId("demo");
        latest.setVersion("1.0.0");
        latest.setId("acme:demo:1.0.0");
        latest.setDescription("new");

        artifacts.put(first);
        artifacts.put(latest);

        assertThat(artifacts.save()).singleElement().extracting(
                com.nf3t.artifactsite.api.ArtifactMetadata::getDescription).isEqualTo("new");
    }

    @Test
    void generateProducesStaticPagesAssetsAndSearchIndex() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");
        Path localArtifact = tempDir.resolve("demo-2.0.0.vsix");
        Files.writeString(localArtifact, "artifact-bytes");

        com.nf3t.artifactsite.api.ArtifactMetadata v1 = new com.nf3t.artifactsite.api.ArtifactMetadata();
        v1.setPluginId("vsix");
        v1.setGroupId("acme");
        v1.setArtifactId("demo");
        v1.setArtifactName("Acme Demo");
        v1.setVersion("1.0.0");
        v1.setDescription("first release");
        v1.setTags(List.of("analytics", "etl"));
        v1.setSourceType("remote");
        v1.setSourceValue("https://example.com/acme-demo-1.0.0.vsix");
        v1.setDownloadUrl("https://example.com/acme-demo-1.0.0.vsix");

        com.nf3t.artifactsite.api.ArtifactMetadata v2 = new com.nf3t.artifactsite.api.ArtifactMetadata();
        v2.setPluginId("vsix");
        v2.setGroupId("acme");
        v2.setArtifactId("demo");
        v2.setArtifactName("Acme Demo");
        v2.setVersion("2.0.0");
        v2.setDescription("second release");
        v2.setTags(List.of("analytics", "catalog"));
        v2.setSourceType("local");
        v2.setSourceValue(localArtifact.toString());
        v2.setFileName("demo-2.0.0.vsix");
        v2.setFileSizeBytes(2048L);

        OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(v1, v2));

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute(
                        "--artifact-json",
                        artifactJson.toString(),
                        "generate",
                        "--output",
                        outputDir.toString(),
                        "--bannerText",
                        "This application is in beta",
                        "--bannerTextColorDark",
                        "black",
                        "--bannerBackgroundColorDark",
                        "white",
                        "--bannerTextColorLight",
                        "black",
                        "--bannerBackgroundColorLight",
                        "white");

        assertThat(exitCode).isZero();
        assertThat(Files.exists(outputDir.resolve("index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("assets/styles.css"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("assets/app.js"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("assets/logo.svg"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("artifacts/vsix/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("artifacts/vsix/acme.demo/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("artifacts/vsix/acme.demo/2.0.0/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("tags/analytics/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("search-index.json"))).isTrue();

        Path copiedDownload = outputDir.resolve("downloads/demo/2.0.0/demo-2.0.0.vsix");
        assertThat(Files.readString(copiedDownload)).isEqualTo("artifact-bytes");

        String detailPage = Files.readString(outputDir.resolve("artifacts/vsix/acme.demo/2.0.0/index.html"));
        assertThat(detailPage).contains("<title>Acme Demo 2.0.0</title>");
        assertThat(detailPage).contains("second release");
        assertThat(detailPage).contains("Download");
        assertThat(detailPage).contains("/downloads/demo/2.0.0/demo-2.0.0.vsix");
        assertThat(detailPage).contains("2.0 KB (2048 bytes)");

        String rootIndex = Files.readString(outputDir.resolve("index.html"));
        assertThat(rootIndex).contains("Acme Demo");
        assertThat(rootIndex).contains("second release");
        assertThat(rootIndex).contains("This application is in beta");
        assertThat(rootIndex).contains("--banner-text-color-dark:black");

        List<Map<String, Object>> searchIndex = OBJECT_MAPPER.readValue(
                outputDir.resolve("search-index.json").toFile(),
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        assertThat(searchIndex).hasSize(1);
        assertThat(searchIndex.get(0)).containsEntry("name", "Acme Demo");
        assertThat(searchIndex.get(0)).containsEntry("version", "2.0.0");
    }

    // @Test
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
            ignored.putNextEntry(new ZipEntry("com/nf3t/artifactsite/api/ArtifactParserPlugin.class"));
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
