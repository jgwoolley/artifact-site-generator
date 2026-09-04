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
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.ArtifactSourceType;
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
            public String id() {
                return "test";
            }

            @Override
            public String displayName() {
                return "Test";
            }

            @Override
            public boolean supports(com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor) {
                return false;
            }

			@Override
			public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception {
				// TODO Auto-generated method stub
				
			}

			@Override
			public String iconResourceName() {
				return "icon.svg";
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
                    public String id() {
                        return "test";
                    }

                    @Override
                    public String displayName() {
                        return "Test";
                    }

                    @Override
                    public boolean supports(com.nf3t.artifactsite.api.ArtifactInputDescriptor descriptor) {
                        return false;
                    }

                    @Override
        			public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception {
        				// TODO Auto-generated method stub
        				
        			}

                    @Override
                    public String iconResourceName() {
                        return "icon.svg";
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
    void parseRemoteInputWithoutHeadersDoesNotTreatInputAsHttpHeader() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        Path pluginDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginDir);
        System.setProperty("user.home", tempDir.toString());
        try {
            int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                    .execute(
                            "--plugin-dir",
                            pluginDir.toString(),
                            "parse",
                            "http://127.0.0.1:1/test.vsix");

            assertThat(exitCode).isZero();
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void writeArtifactPersistsRemoteRequestConfigForCurrentRemoteInput() throws Exception {
        Path cachedFile = tempDir.resolve("cached.vsix");
        Files.writeString(cachedFile, "artifact-content");

        RemoteTlsConfig tls = new RemoteTlsConfig();
        tls.setTrustStorePath(tempDir.resolve("truststore.p12").toString());
        RemoteRequestConfigStore store = new RemoteRequestConfigStore();
        ArtifactParseContext context = new ArtifactParseContext(
                LoggerFactory.getLogger(ArtifactParseContext.class),
                tls,
                tempDir.resolve("cache"),
                List.of("Authorization=******"),
                store,
                List.of(),
                new ArtifactsByParser());

        ArtifactInputDescriptor descriptor = new ArtifactInputDescriptor(
                cachedFile,
                ArtifactSourceType.REMOTE,
                "https://example.com/sample.vsix",
                "sample.vsix",
                "vsix",
                "application/octet-stream");

        ArtifactMetadata artifact = new ArtifactMetadata();
        artifact.setId("publisher:sample:1.0.0");
        context.writeArtifact(descriptor, artifact);

        RemoteRequestConfig saved = store.getRequestsByArtifactId().get("publisher:sample:1.0.0");
        assertThat(saved).isNotNull();
        assertThat(saved.getSourceType()).isEqualTo("remote");
        assertThat(saved.getSourceValue()).isEqualTo("https://example.com/sample.vsix");
        assertThat(saved.getCachedPath()).isEqualTo(cachedFile.toString());
        assertThat(saved.getHeaders()).containsEntry("Authorization", "******");
        assertThat(saved.getTls()).isSameAs(tls);
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
        v2.setReadme("# Demo\n\nThis is the **readme** body.");

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
        assertThat(detailPage).contains("2.0 KB");
        assertThat(detailPage).contains("<h1>Demo</h1>");
        assertThat(detailPage).contains("<strong>readme</strong>");

        String noReadmeDetailPage = Files.readString(outputDir.resolve("artifacts/vsix/acme.demo/1.0.0/index.html"));
        assertThat(noReadmeDetailPage).contains("No README was found for this artifact.");

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

    /**
     * A parser plugin with no cached icon (e.g. it declared no {@code iconResourceName()}, or its
     * icon failed to load) must fall back to the site's default icon rather than rendering with no
     * icon at all.
     */
    @Test
    void generateFallsBackToDefaultIconForParserWithNoCachedIcon() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");

        com.nf3t.artifactsite.api.ArtifactMetadata artifact = new com.nf3t.artifactsite.api.ArtifactMetadata();
        artifact.setPluginId("no-icon-parser-" + System.nanoTime());
        artifact.setGroupId("acme");
        artifact.setArtifactId("demo");
        artifact.setArtifactName("Acme Demo");
        artifact.setVersion("1.0.0");
        artifact.setSourceType("remote");
        artifact.setSourceValue("https://example.com/acme-demo-1.0.0.jar");
        artifact.setDownloadUrl("https://example.com/acme-demo-1.0.0.jar");

        OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(artifact));

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute("--artifact-json", artifactJson.toString(), "generate", "--output", outputDir.toString());

        assertThat(exitCode).isZero();
        assertThat(Files.exists(outputDir.resolve("assets/favicon.svg"))).isTrue();

        String rootIndex = Files.readString(outputDir.resolve("index.html"));
        assertThat(rootIndex).contains("class=\"card-icon\" src=\"assets/favicon.svg\"");
    }

    /**
     * {@code --title} replaces "Artifact Registry" in the home page's {@code <title>}/heading only
     * - child pages (parser index, in this case) keep their own titles unaffected. {@code --favicon}
     * replaces the default favicon everywhere: the home page's {@code <link rel="icon">}, the header
     * icon next to the brand text (shared by every page, including the child page), and its MIME
     * type is derived from the custom file's extension.
     */
    @Test
    void generateAppliesCustomTitleToHomePageOnlyAndCustomFaviconEverywhere() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");
        Path customFavicon = tempDir.resolve("custom-favicon.png");
        Files.writeString(customFavicon, "fake-png-bytes");

        String parserId = "custom-title-parser-" + System.nanoTime();
        com.nf3t.artifactsite.api.ArtifactMetadata artifact = new com.nf3t.artifactsite.api.ArtifactMetadata();
        artifact.setPluginId(parserId);
        artifact.setGroupId("acme");
        artifact.setArtifactId("demo");
        artifact.setArtifactName("Acme Demo");
        artifact.setVersion("1.0.0");
        artifact.setSourceType("remote");
        artifact.setSourceValue("https://example.com/acme-demo-1.0.0.jar");
        artifact.setDownloadUrl("https://example.com/acme-demo-1.0.0.jar");

        OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(artifact));

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute(
                        "--artifact-json",
                        artifactJson.toString(),
                        "generate",
                        "--output",
                        outputDir.toString(),
                        "--title",
                        "Acme Registry",
                        "--favicon",
                        customFavicon.toString());

        assertThat(exitCode).isZero();

        Path faviconAsset = outputDir.resolve("assets/favicon.png");
        assertThat(Files.readString(faviconAsset)).isEqualTo("fake-png-bytes");

        String rootIndex = Files.readString(outputDir.resolve("index.html"));
        assertThat(rootIndex).contains("<title>Acme Registry</title>");
        assertThat(rootIndex).contains("<h1>Acme Registry</h1>");
        assertThat(rootIndex).contains("<link rel=\"icon\" type=\"image/png\" href=\"assets/favicon.png\">");
        assertThat(rootIndex).contains("<img src=\"assets/favicon.png\" alt=\"Artifact Site\"");

        String parserIndex = Files.readString(outputDir.resolve("artifacts").resolve(parserId).resolve("index.html"));
        assertThat(parserIndex).doesNotContain("Acme Registry");
        assertThat(parserIndex).contains("<link rel=\"icon\" type=\"image/png\" href=\"../../assets/favicon.png\">");
        assertThat(parserIndex).contains("<img src=\"../../assets/favicon.png\" alt=\"Artifact Site\"");
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
