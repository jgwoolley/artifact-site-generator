package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.DatumField;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.MapDatum;
import com.nf3t.artifactsite.api.SectionSpec;

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
            public boolean supports(Datum datum) {
                return false;
            }

			@Override
			public void parse(Datum datum, Path contentPath, IArtifactParseContext context) throws Exception {
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
                    public boolean supports(Datum datum) {
                        return false;
                    }

                    @Override
        			public void parse(Datum datum, Path contentPath, IArtifactParseContext context) throws Exception {
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

        Datum artifact = new MapDatum();
        ArtifactFields.ID.setString(artifact, "publisher:sample:1.0.0");
        ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
        ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/sample.vsix");
        context.writeArtifact(artifact, cachedFile);

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
        Datum first = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(first, "vsix");
        ArtifactFields.GROUP_ID.setString(first, "acme");
        ArtifactFields.ARTIFACT_ID.setString(first, "demo");
        ArtifactFields.VERSION.setString(first, "1.0.0");
        ArtifactFields.ID.setString(first, "acme:demo:1.0.0");
        ArtifactFields.DESCRIPTION.setString(first, "old");

        Datum latest = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(latest, "vsix");
        ArtifactFields.GROUP_ID.setString(latest, "acme");
        ArtifactFields.ARTIFACT_ID.setString(latest, "demo");
        ArtifactFields.VERSION.setString(latest, "1.0.0");
        ArtifactFields.ID.setString(latest, "acme:demo:1.0.0");
        ArtifactFields.DESCRIPTION.setString(latest, "new");

        artifacts.put(first);
        artifacts.put(latest);

        assertThat(artifacts.save()).singleElement().extracting(
                (Datum artifact) -> ArtifactFields.DESCRIPTION.getString(artifact)).isEqualTo("new");
    }

    @Test
    void generateProducesStaticPagesAssetsAndSearchIndex() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");
        Path localArtifact = tempDir.resolve("demo-2.0.0.vsix");
        Files.writeString(localArtifact, "artifact-bytes");

        Datum v1 = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(v1, "vsix");
        ArtifactFields.GROUP_ID.setString(v1, "acme");
        ArtifactFields.ARTIFACT_ID.setString(v1, "demo");
        ArtifactFields.NAME.setString(v1, "Acme Demo");
        ArtifactFields.VERSION.setString(v1, "1.0.0");
        ArtifactFields.DESCRIPTION.setString(v1, "first release");
        ArtifactFields.TAGS.getStringList(v1).addAll(List.of("analytics", "etl"));
        ArtifactFields.SOURCE_TYPE.setString(v1, "remote");
        ArtifactFields.SOURCE_VALUE.setString(v1, "https://example.com/acme-demo-1.0.0.vsix");
        ArtifactFields.DOWNLOAD_URL.setString(v1, "https://example.com/acme-demo-1.0.0.vsix");

        Datum v2 = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(v2, "vsix");
        ArtifactFields.GROUP_ID.setString(v2, "acme");
        ArtifactFields.ARTIFACT_ID.setString(v2, "demo");
        ArtifactFields.NAME.setString(v2, "Acme Demo");
        ArtifactFields.VERSION.setString(v2, "2.0.0");
        ArtifactFields.DESCRIPTION.setString(v2, "second release");
        ArtifactFields.TAGS.getStringList(v2).addAll(List.of("analytics", "catalog"));
        ArtifactFields.SOURCE_TYPE.setString(v2, "local");
        ArtifactFields.SOURCE_VALUE.setString(v2, localArtifact.toString());
        ArtifactFields.FILE_NAME.setString(v2, "demo-2.0.0.vsix");
        ArtifactFields.FILE_SIZE.setLong(v2, 2048L);
        ArtifactFields.README.setString(v2, "# Demo\n\nThis is the **readme** body.");

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
        assertThat(Files.exists(outputDir.resolve("types/vsix/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("artifacts/acme/demo/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("artifacts/acme/demo/2.0.0/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("tags/analytics/index.html"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("search-index.json"))).isTrue();

        Path copiedDownload = outputDir.resolve("downloads/demo/2.0.0/demo-2.0.0.vsix");
        assertThat(Files.readString(copiedDownload)).isEqualTo("artifact-bytes");

        String detailPage = Files.readString(outputDir.resolve("artifacts/acme/demo/2.0.0/index.html"));
        assertThat(detailPage).contains("<title>Acme Demo 2.0.0</title>");
        assertThat(detailPage).contains("second release");
        assertThat(detailPage).contains("Download");
        assertThat(detailPage).contains("/downloads/demo/2.0.0/demo-2.0.0.vsix");
        assertThat(detailPage).contains("2.0 KB");
        assertThat(detailPage).contains("<h1>Demo</h1>");
        assertThat(detailPage).contains("<strong>readme</strong>");

        String noReadmeDetailPage = Files.readString(outputDir.resolve("artifacts/acme/demo/1.0.0/index.html"));
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

        Datum artifact = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(artifact, "no-icon-parser-" + System.nanoTime());
        ArtifactFields.GROUP_ID.setString(artifact, "acme");
        ArtifactFields.ARTIFACT_ID.setString(artifact, "demo");
        ArtifactFields.NAME.setString(artifact, "Acme Demo");
        ArtifactFields.VERSION.setString(artifact, "1.0.0");
        ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
        ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");
        ArtifactFields.DOWNLOAD_URL.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");

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
        Datum artifact = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(artifact, parserId);
        ArtifactFields.GROUP_ID.setString(artifact, "acme");
        ArtifactFields.ARTIFACT_ID.setString(artifact, "demo");
        ArtifactFields.NAME.setString(artifact, "Acme Demo");
        ArtifactFields.VERSION.setString(artifact, "1.0.0");
        ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
        ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");
        ArtifactFields.DOWNLOAD_URL.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");

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

        String childPage = Files.readString(outputDir.resolve("artifacts").resolve("acme").resolve("demo").resolve("index.html"));
        assertThat(childPage).doesNotContain("Acme Registry");
        assertThat(childPage).contains("<link rel=\"icon\" type=\"image/png\" href=\"../../../assets/favicon.png\">");
        assertThat(childPage).contains("<img src=\"../../../assets/favicon.png\" alt=\"Artifact Site\"");
    }

    /**
     * The generated site is a basic PWA: a {@code manifest.webmanifest} naming the effective home
     * page title and pointing at the effective favicon, and a registered {@code sw.js} at the site
     * root. Every page also carries baseline SEO meta tags, and an artifact detail page's {@code
     * <meta name="keywords">} must include that artifact's own tags and every one of its artifact
     * type labels (on top of any parser-level SEO tags - see {@code ArtifactParser#seoTags()},
     * covered separately by {@code ParserSeoTagsCacheTest}).
     */
    @Test
    void generateWritesPwaManifestServiceWorkerAndArtifactKeywords() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");

        Datum artifact = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(artifact, "seo-test-parser-" + System.nanoTime());
        ArtifactFields.GROUP_ID.setString(artifact, "acme");
        ArtifactFields.ARTIFACT_ID.setString(artifact, "demo");
        ArtifactFields.NAME.setString(artifact, "Acme Demo");
        ArtifactFields.VERSION.setString(artifact, "1.0.0");
        ArtifactFields.DESCRIPTION.setString(artifact, "A demo artifact");
        ArtifactFields.TAGS.getStringList(artifact).addAll(List.of("etl", "catalog"));
        ArtifactFields.ARTIFACT_TYPES.getStringList(artifact).add("widget");
        ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
        ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");
        ArtifactFields.DOWNLOAD_URL.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");

        OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(artifact));

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute("--artifact-json", artifactJson.toString(), "generate", "--output", outputDir.toString());

        assertThat(exitCode).isZero();
        assertThat(Files.exists(outputDir.resolve("sw.js"))).isTrue();

        Map<String, Object> manifest = OBJECT_MAPPER.readValue(
                outputDir.resolve("manifest.webmanifest").toFile(),
                OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        assertThat(manifest).containsEntry("name", "Artifact Registry");
        assertThat(manifest).containsEntry("display", "standalone");

        String rootIndex = Files.readString(outputDir.resolve("index.html"));
        assertThat(rootIndex).contains("<link rel=\"manifest\" href=\"manifest.webmanifest\">");
        assertThat(rootIndex).contains("<meta name=\"description\" content=\"Browse generated artifacts");
        assertThat(rootIndex).contains("<meta property=\"og:title\" content=\"Artifact Registry\">");

        String detailPage = Files.readString(
                outputDir.resolve("artifacts")
                        .resolve("acme")
                        .resolve("demo")
                        .resolve("1.0.0")
                        .resolve("index.html"));
        assertThat(detailPage).contains("<meta name=\"keywords\" content=\"etl, catalog, widget\">");
    }

    /**
     * An artifact tagged with an {@link ArtifactFields#ARTIFACT_TYPES} type that has declared
     * sections (see {@code ArtifactTypeSectionsCache}, populated here directly to stand in for
     * what {@code parse} would have cached from a real {@code ArtifactSectionProvider}) renders
     * those sections - both a MARKDOWN and a TABLE one - in place of the plain README block. An
     * artifact with no matching type/sections must keep rendering exactly as before.
     */
    @Test
    void generateRendersDeclaredArtifactTypeSectionsAndFallsBackWhenNoneApply() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            Path artifactJson = tempDir.resolve("artifacts.json");
            Path outputDir = tempDir.resolve("public");
            String parserId = "sectioned-parser-" + System.nanoTime();
            String artifactType = "widget";
            DatumField tableField = new DatumField() {
                @Override
                public String getNamespace() {
                    return "com.example.test";
                }

                @Override
                public String getKey() {
                    return "widget_table";
                }

                @Override
                public String getFieldDescription() {
                    return "test table field";
                }
            };

            Datum withSections = new MapDatum();
            ArtifactFields.PLUGIN_ID.setString(withSections, parserId);
            ArtifactFields.GROUP_ID.setString(withSections, "acme");
            ArtifactFields.ARTIFACT_ID.setString(withSections, "widget-a");
            ArtifactFields.NAME.setString(withSections, "Widget A");
            ArtifactFields.VERSION.setString(withSections, "1.0.0");
            ArtifactFields.SOURCE_TYPE.setString(withSections, "remote");
            ArtifactFields.SOURCE_VALUE.setString(withSections, "https://example.com/widget-a-1.0.0.jar");
            ArtifactFields.DOWNLOAD_URL.setString(withSections, "https://example.com/widget-a-1.0.0.jar");
            ArtifactFields.ARTIFACT_TYPES.getStringList(withSections).add(artifactType);
            ArtifactFields.DESCRIPTION.setString(withSections, "**bold** summary");
            tableField.getRecordList(withSections).add(Map.of("Name", "alpha", "Default", "1"));

            Datum withoutSections = new MapDatum();
            ArtifactFields.PLUGIN_ID.setString(withoutSections, parserId);
            ArtifactFields.GROUP_ID.setString(withoutSections, "acme");
            ArtifactFields.ARTIFACT_ID.setString(withoutSections, "widget-b");
            ArtifactFields.NAME.setString(withoutSections, "Widget B");
            ArtifactFields.VERSION.setString(withoutSections, "1.0.0");
            ArtifactFields.SOURCE_TYPE.setString(withoutSections, "remote");
            ArtifactFields.SOURCE_VALUE.setString(withoutSections, "https://example.com/widget-b-1.0.0.jar");
            ArtifactFields.DOWNLOAD_URL.setString(withoutSections, "https://example.com/widget-b-1.0.0.jar");

            OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(withSections, withoutSections));

            Map<String, List<SectionSpec>> declaredSections = Map.of(
                    artifactType,
                    List.of(
                            new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.DESCRIPTION),
                            new SectionSpec("Widget Table", ElementType.TABLE, tableField)));
            Path sectionsCachePath = XdgPaths.artifactTypeSectionsPath();
            Files.createDirectories(sectionsCachePath.getParent());
            OBJECT_MAPPER.writeValue(sectionsCachePath.toFile(), declaredSections);

            int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                    .execute("--artifact-json", artifactJson.toString(), "generate", "--output", outputDir.toString());
            assertThat(exitCode).isZero();

            String withSectionsPage = Files.readString(
                    outputDir.resolve("artifacts").resolve("acme").resolve("widget-a").resolve("1.0.0").resolve("index.html"));
            assertThat(withSectionsPage).contains("<strong>bold</strong> summary");
            assertThat(withSectionsPage).contains("Widget Table");
            assertThat(withSectionsPage).contains("class=\"data-table\"");
            assertThat(withSectionsPage).contains("alpha");
            assertThat(withSectionsPage).doesNotContain("No README was found");

            String withoutSectionsPage = Files.readString(
                    outputDir.resolve("artifacts").resolve("acme").resolve("widget-b").resolve("1.0.0").resolve("index.html"));
            assertThat(withoutSectionsPage).contains("No README was found for this artifact.");
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    /**
     * An artifact carrying more than one {@link ArtifactFields#ARTIFACT_TYPES} (e.g. a NiFi
     * Archive is both {@code "jar"} and {@code "nar"}) must not have one type "win" for browsing
     * or keywords - it appears on every one of its type pages, and every type's label is merged
     * into its own {@code pageKeywords} - but its own card/detail-page badge shows exactly one
     * type (the last/most specific one), not all of them, to keep that compact label readable.
     */
    @Test
    void generateTreatsMultipleArtifactTypesAsInclusiveBrowsingButOneWinningBadge() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            Path artifactJson = tempDir.resolve("artifacts.json");
            Path outputDir = tempDir.resolve("public");

            Datum artifact = new MapDatum();
            ArtifactFields.PLUGIN_ID.setString(artifact, "maven");
            ArtifactFields.GROUP_ID.setString(artifact, "acme");
            ArtifactFields.ARTIFACT_ID.setString(artifact, "multitype");
            ArtifactFields.NAME.setString(artifact, "Acme Multitype");
            ArtifactFields.VERSION.setString(artifact, "1.0.0");
            ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
            ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/acme-multitype-1.0.0.nar");
            ArtifactFields.DOWNLOAD_URL.setString(artifact, "https://example.com/acme-multitype-1.0.0.nar");
            ArtifactFields.ARTIFACT_TYPES.getStringList(artifact).addAll(List.of("jar", "nar"));

            OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(artifact));

            Path displayNamesCachePath = XdgPaths.artifactTypeDisplayNamesPath();
            Files.createDirectories(displayNamesCachePath.getParent());
            OBJECT_MAPPER.writeValue(displayNamesCachePath.toFile(), Map.of("jar", "Java", "nar", "Nar"));

            int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                    .execute("--artifact-json", artifactJson.toString(), "generate", "--output", outputDir.toString());
            assertThat(exitCode).isZero();

            String detailPage = Files.readString(
                    outputDir.resolve("artifacts").resolve("acme").resolve("multitype").resolve("1.0.0").resolve("index.html"));
            assertThat(detailPage).contains("<span class=\"badge\">Nar</span>");
            assertThat(detailPage).doesNotContain("<span class=\"badge\">Java</span>");
            assertThat(detailPage).contains("<meta name=\"keywords\" content=\"Java, Nar\">");

            assertThat(Files.exists(outputDir.resolve("types/jar/index.html"))).isTrue();
            assertThat(Files.exists(outputDir.resolve("types/nar/index.html"))).isTrue();
            String jarTypePage = Files.readString(outputDir.resolve("types/jar/index.html"));
            String narTypePage = Files.readString(outputDir.resolve("types/nar/index.html"));
            assertThat(jarTypePage).contains("Acme Multitype");
            assertThat(narTypePage).contains("Acme Multitype");

            String rootIndex = Files.readString(outputDir.resolve("index.html"));
            assertThat(rootIndex).contains("<h2>Artifact Types</h2>");
            assertThat(rootIndex).contains("<strong>Java</strong>");
            assertThat(rootIndex).contains("<strong>Nar</strong>");
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    /**
     * A tag page lists artifacts from potentially many different parsers/icons, but its own
     * {@code og:image}/{@code twitter:image} (and visible page-heading icon) must still use a
     * concrete artifact icon - here, the tagged artifact's own per-artifact icon - rather than
     * falling back to the generic site favicon, matching artifact/parser index pages.
     */
    @Test
    void generateUsesArtifactIconForTagPageSocialImage() throws IOException {
        Path artifactJson = tempDir.resolve("artifacts.json");
        Path outputDir = tempDir.resolve("public");
        String tag = "demo-tag-" + System.nanoTime();

        Datum artifact = new MapDatum();
        ArtifactFields.PLUGIN_ID.setString(artifact, "tag-icon-parser-" + System.nanoTime());
        ArtifactFields.GROUP_ID.setString(artifact, "acme");
        ArtifactFields.ARTIFACT_ID.setString(artifact, "demo");
        ArtifactFields.NAME.setString(artifact, "Acme Demo");
        ArtifactFields.VERSION.setString(artifact, "1.0.0");
        ArtifactFields.TAGS.getStringList(artifact).add(tag);
        ArtifactFields.SOURCE_TYPE.setString(artifact, "remote");
        ArtifactFields.SOURCE_VALUE.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");
        ArtifactFields.DOWNLOAD_URL.setString(artifact, "https://example.com/acme-demo-1.0.0.jar");
        ArtifactFields.ICON_DATA.setString(artifact, Base64.getEncoder().encodeToString("fake-png-bytes".getBytes()));
        ArtifactFields.ICON_ORIGINAL_FILE_NAME.setString(artifact, "icon.png");

        OBJECT_MAPPER.writeValue(artifactJson.toFile(), List.of(artifact));

        int exitCode = new CommandLine(new ArtifactSiteGeneratorCli())
                .execute("--artifact-json", artifactJson.toString(), "generate", "--output", outputDir.toString());

        assertThat(exitCode).isZero();

        String tagPage = Files.readString(outputDir.resolve("tags").resolve(tag).resolve("index.html"));
        String expectedIconPath = "../../assets/icons/artifacts/acme/demo/1.0.0.png";
        assertThat(tagPage).contains("<meta property=\"og:image\" content=\"" + expectedIconPath + "\">");
        assertThat(tagPage).contains("<meta name=\"twitter:image\" content=\"" + expectedIconPath + "\">");
        assertThat(tagPage).contains("class=\"page-icon\" src=\"" + expectedIconPath + "\"");
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
