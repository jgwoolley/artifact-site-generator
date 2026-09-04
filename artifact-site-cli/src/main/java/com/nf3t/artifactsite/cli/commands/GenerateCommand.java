package com.nf3t.artifactsite.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.PathUtils;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates a static artifact registry site from catalog metadata.
 */
@Command(name = "generate", description = "Generates a static artifact registry site")
public class GenerateCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateCommand.class);
    private static final Pattern SANITIZE_SEGMENT = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    // README markdown comes from arbitrary third-party artifacts; escaping any raw HTML in the
    // source (rather than passing it through) keeps a malicious README from injecting a
    // <script> or other active content into the generated site.
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().escapeHtml(true).build();
    private static final String DEFAULT_FAVICON_ASSET_PATH = "assets/favicon.svg";
    private static final String DEFAULT_FAVICON_MIME_TYPE = "image/svg+xml";

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Option(names = "--output", description = "Output directory for generated static site", defaultValue = "./public")
    private Path outputDir;

    @Option(
            names = "--title",
            description = "Optional custom site title shown on the home page in place of \"Artifact Registry\" "
                    + "(child pages keep their own titles)")
    private String siteTitle;

    @Option(
            names = "--favicon",
            description = "Optional path to a custom favicon image replacing the bundled default; used as the "
                    + "browser tab icon and header icon on every page, and as the fallback icon for any "
                    + "parser/artifact with no icon of its own")
    private Path faviconOverride;

    // Effective favicon for this generate() run: the site-root-relative asset path (e.g.
    // "assets/favicon.svg") and its MIME type, resolved by copyFavicon() from --favicon or the
    // bundled default. Used both for the site's own <link rel="icon">/header icon and as the
    // fallback icon for any parser/artifact that doesn't supply its own.
    private String faviconAssetPath = DEFAULT_FAVICON_ASSET_PATH;
    private String faviconMimeType = DEFAULT_FAVICON_MIME_TYPE;

    @Option(names = "--bannerText", description = "Optional banner text displayed at the top of every generated page")
    private String bannerText;

    @Option(
            names = "--bannerTextColorDark",
            description = "Optional dark mode banner text color (CSS color value)")
    private String bannerTextColorDark;

    @Option(
            names = "--bannerBackgroundColorDark",
            description = "Optional dark mode banner background color (CSS color value)")
    private String bannerBackgroundColorDark;

    @Option(
            names = "--bannerTextColorLight",
            description = "Optional light mode banner text color (CSS color value)")
    private String bannerTextColorLight;

    @Option(
            names = "--bannerBackgroundColorLight",
            description = "Optional light mode banner background color (CSS color value)")
    private String bannerBackgroundColorLight;

    /**
     * Executes static site generation and writes all pages and assets.
     */
    @Override
    public void run() {
        try {
            generate(parentCommand.loadArtifacts().save(), outputDir);
            LOGGER.info("Generated static site at {}", outputDir.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to generate static site", e);
        }
    }

    void generate(List<ArtifactMetadata> artifactCatalog, Path outputRoot) throws IOException, TemplateException {
        Files.createDirectories(outputRoot);
        copyAsset("assets/styles.css", outputRoot.resolve("assets/styles.css"));
        copyAsset("assets/app.js", outputRoot.resolve("assets/app.js"));
        copyAsset("assets/logo.svg", outputRoot.resolve("assets/logo.svg"));
        copyFavicon(outputRoot);

        Map<String, String> parserIconUrls = copyParserIcons(parentCommand.iconsDir(), outputRoot);
        Map<String, String> parserDisplayNames = readStringMapCache(parentCommand.parserDisplayNamesPath());
        Map<String, String> parserInstallGuides = readStringMapCache(parentCommand.parserInstallGuidesPath());
        GenerationModel model = buildModel(artifactCatalog, outputRoot, parserIconUrls, parserDisplayNames, parserInstallGuides);
        writeSearchIndex(outputRoot.resolve("search-index.json"), model.searchIndexEntries());

        Configuration freemarker = createFreemarkerConfiguration();
        writeTemplate(freemarker, "index.ftl", outputRoot.resolve("index.html"), createRootIndexData(model, parserIconUrls));
        writeTemplate(freemarker, "build-info.ftl", outputRoot.resolve("build-info.html"), createBuildInfoData(System.getenv()));

        for (ParserGroup parserGroup : model.parserGroups()) {
            Path parserIndexPath = outputRoot.resolve("artifacts").resolve(parserGroup.parserSegment()).resolve("index.html");
            Map<String, Object> parserData = new HashMap<>();
            parserData.put("title", parserGroup.parserDisplayName() + " Artifacts");
            parserData.put("pageHeading", parserGroup.parserDisplayName() + " Artifacts");
            parserData.put("pageDescription", "Latest artifacts parsed by " + parserGroup.parserDisplayName());
            parserData.put("rootPath", relativeRootPath(parserIndexPath, outputRoot));
            parserData.put("pageIcon", parserIconUrls.getOrDefault(parserGroup.parserName(), defaultIconUrl()));
            parserData.put("artifactCards", parserGroup.latestArtifacts());
            writeTemplate(freemarker, "index.ftl", parserIndexPath, parserData);

            for (ArtifactGroup artifactGroup : parserGroup.artifactGroups()) {
                Path artifactIndexPath = outputRoot
                        .resolve("artifacts")
                        .resolve(parserGroup.parserSegment())
                        .resolve(artifactGroup.artifactSegment())
                        .resolve("index.html");
                Map<String, Object> artifactData = new HashMap<>();
                Map<String, Object> latestDetailPage = artifactGroup.detailPages().get(0);
                String artifactName = (String) latestDetailPage.get("pageHeading");
                String latestDescription = (String) latestDetailPage.get("description");
                artifactData.put("title", artifactName + " Versions");
                artifactData.put("pageHeading", artifactName + " Versions");
                artifactData.put(
                        "pageDescription",
                        isBlank(latestDescription)
                                ? "All published versions for " + artifactName
                                : latestDescription);
                artifactData.put("rootPath", relativeRootPath(artifactIndexPath, outputRoot));
                artifactData.put("pageIcon", latestDetailPage.get("pageIcon"));
                artifactData.put("versions", artifactGroup.versionRows());
                writeTemplate(freemarker, "index.ftl", artifactIndexPath, artifactData);

                for (Map<String, Object> detailPage : artifactGroup.detailPages()) {
                    Path detailPath = outputRoot
                            .resolve("artifacts")
                            .resolve(parserGroup.parserSegment())
                            .resolve(artifactGroup.artifactSegment())
                            .resolve((String) detailPage.get("versionSegment"))
                            .resolve("index.html");
                    detailPage.put("rootPath", relativeRootPath(detailPath, outputRoot));
                    writeTemplate(freemarker, "artifact.ftl", detailPath, detailPage);
                }
            }
        }

        for (Map.Entry<String, List<Map<String, Object>>> tagEntry : model.tagCards().entrySet()) {
            String tag = tagEntry.getKey();
            Path tagPath = outputRoot.resolve("tags").resolve(toPathSegment(tag)).resolve("index.html");
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("title", "Tag: " + tag);
            tagData.put("pageHeading", "Tag: " + tag);
            tagData.put("pageDescription", "Artifacts tagged with “" + tag + "”");
            tagData.put("rootPath", relativeRootPath(tagPath, outputRoot));
            tagData.put("artifactCards", tagEntry.getValue());
            writeTemplate(freemarker, "tag.ftl", tagPath, tagData);
        }
    }

    private GenerationModel buildModel(
            List<ArtifactMetadata> artifactCatalog,
            Path outputRoot,
            Map<String, String> parserIconUrls,
            Map<String, String> parserDisplayNames,
            Map<String, String> parserInstallGuides)
            throws IOException {
        Map<String, Map<String, List<ArtifactMetadata>>> grouped = new LinkedHashMap<>();

        for (ArtifactMetadata artifact : artifactCatalog) {
            if (isBlank(artifact.getPluginId())
                    || isBlank(artifact.getGroupId())
                    || isBlank(artifact.getArtifactId())
                    || isBlank(artifact.getVersion())) {
                continue;
            }

            grouped.computeIfAbsent(artifact.getPluginId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(artifact.getArtifactSlug(), ignored -> new ArrayList<>())
                    .add(artifact);
        }

        List<ParserGroup> parserGroups = new ArrayList<>();
        List<Map<String, Object>> allLatestArtifactCards = new ArrayList<>();
        List<Map<String, Object>> searchEntries = new ArrayList<>();
        Map<String, List<Map<String, Object>>> tagCards = new LinkedHashMap<>();

        List<String> parserNames = new ArrayList<>(grouped.keySet());
        parserNames.sort(String::compareToIgnoreCase);

        for (String parserName : parserNames) {
            String parserSegment = toPathSegment(parserName);
            String parserDisplayName = parserDisplayNames.getOrDefault(parserName, parserName);
            String installGuideTemplate = parserInstallGuides.get(parserName);
            List<ArtifactGroup> artifactGroups = new ArrayList<>();
            List<Map<String, Object>> latestArtifacts = new ArrayList<>();

            List<String> artifactKeys = new ArrayList<>(grouped.get(parserName).keySet());
            artifactKeys.sort(String::compareToIgnoreCase);

            for (String artifactKey : artifactKeys) {
                String artifactSegment = toPathSegment(artifactKey);
                List<ArtifactMetadata> versions = new ArrayList<>(grouped.get(parserName).get(artifactKey));
                versions.sort((a, b) -> compareVersions(safe(b.getVersion()), safe(a.getVersion())));

                ArtifactMetadata latest = versions.get(0);
                String latestVersionSegment = toPathSegment(safe(latest.getVersion()));
                String detailUrl = toArtifactDetailUrl(parserSegment, artifactSegment, latestVersionSegment);

                String parserIconUrl = parserIconUrls.getOrDefault(parserName, defaultIconUrl());
                String latestIconUrl = resolveArtifactIconUrl(
                        latest, parserIconUrl, outputRoot, parserSegment, artifactSegment, latestVersionSegment);
                Map<String, Object> latestCard = createArtifactCard(latest, parserDisplayName, detailUrl, latestIconUrl);
                latestArtifacts.add(latestCard);
                allLatestArtifactCards.add(latestCard);
                searchEntries.add(createSearchEntry(latestCard));

                for (String tag : normalizeTags(latest.getTags())) {
                    tagCards.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(latestCard);
                }

                List<Map<String, Object>> versionRows = new ArrayList<>();
                List<Map<String, Object>> detailPages = new ArrayList<>();
                for (ArtifactMetadata version : versions) {
                    String versionValue = safe(version.getVersion());
                    String versionSegment = toPathSegment(versionValue);
                    String versionUrl = toArtifactDetailUrl(parserSegment, artifactSegment, versionSegment);
                    String downloadUrl = resolveDownloadUrl(version, outputRoot);
                    String versionIconUrl = resolveArtifactIconUrl(
                            version, parserIconUrl, outputRoot, parserSegment, artifactSegment, versionSegment);

                    Map<String, Object> versionRow = new HashMap<>();
                    versionRow.put("version", versionValue);
                    versionRow.put("description", safe(version.getDescription()));
                    versionRow.put("downloadUrl", downloadUrl);
                    versionRow.put("detailUrl", versionUrl);
                    versionRows.add(versionRow);

                    Map<String, Object> detailPage = new HashMap<>();
                    detailPage.put("title", displayName(version) + " " + versionValue);
                    detailPage.put("pageHeading", displayName(version));
                    detailPage.put("artifactKey", artifactKey);
                    detailPage.put("version", versionValue);
                    detailPage.put("versionSegment", versionSegment);
                    detailPage.put("parserType", parserDisplayName);
                    detailPage.put("groupId", safe(version.getGroupId()));
                    detailPage.put("artifactId", safe(version.getArtifactId()));
                    detailPage.put("description", safe(version.getDescription()));
                    detailPage.put("license", safe(version.getLicense()));
                    detailPage.put("sourceType", safe(version.getSourceType()));
                    detailPage.put("sourceValue", safe(version.getSourceValue()));
                    detailPage.put("scmUrl", safe(version.getScmUrl()));
                    detailPage.put("fileName", safe(version.getFileName()));
                    detailPage.put("sha256", safe(version.getSha256()));
                    detailPage.put("fileSizeBytes", version.getFileSizeBytes());
                    detailPage.put("fileSizeHumanReadable", formatFileSize(version.getFileSizeBytes()));
                    detailPage.put("downloadUrl", downloadUrl);
                    detailPage.put("pageIcon", versionIconUrl);
                    detailPage.put("readmeHtml", renderMarkdown(version.getReadme()));
                    detailPage.put("installGuideHtml", renderInstallGuide(installGuideTemplate, version));
                    detailPage.put("tags", normalizeTags(version.getTags()));
                    detailPage.put("authors", normalizeTags(version.getAuthors()));
                    detailPages.add(detailPage);
                }

                artifactGroups.add(new ArtifactGroup(artifactKey, artifactSegment, versionRows, detailPages));
            }

            parserGroups.add(new ParserGroup(parserName, parserDisplayName, parserSegment, latestArtifacts, artifactGroups));
        }

        allLatestArtifactCards.sort(
                Comparator.comparing((Map<String, Object> card) -> (String) card.get("name"), String::compareToIgnoreCase));

        for (Map.Entry<String, List<Map<String, Object>>> entry : tagCards.entrySet()) {
            entry.getValue().sort(Comparator.comparing(card -> (String) card.get("name"), String::compareToIgnoreCase));
        }

        return new GenerationModel(parserGroups, allLatestArtifactCards, searchEntries, tagCards);
    }

    private Map<String, Object> createRootIndexData(GenerationModel model, Map<String, String> parserIconUrls) {
        List<Map<String, Object>> parserSummaries = new ArrayList<>();
        for (ParserGroup parserGroup : model.parserGroups()) {
            Map<String, Object> parserSummary = new HashMap<>();
            parserSummary.put("parserName", parserGroup.parserDisplayName());
            parserSummary.put("artifactCount", parserGroup.latestArtifacts().size());
            parserSummary.put("url", "/artifacts/" + parserGroup.parserSegment() + "/index.html");
            parserSummary.put("icon", parserIconUrls.getOrDefault(parserGroup.parserName(), defaultIconUrl()));
            parserSummaries.add(parserSummary);
        }

        String homePageTitle = isBlank(siteTitle) ? "Artifact Registry" : siteTitle;
        Map<String, Object> modelData = new HashMap<>();
        modelData.put("title", homePageTitle);
        modelData.put("pageHeading", homePageTitle);
        modelData.put("pageDescription", "Browse generated artifacts by parser, tag, and version");
        modelData.put("rootPath", "");
        modelData.put("parserSummaries", parserSummaries);
        modelData.put("artifactCards", model.allLatestArtifactCards());
        return modelData;
    }

    /**
     * Builds template data for the generated site's build info page, surfacing whatever CI/CD
     * context is available so a deployed site can be traced back to the pipeline run and source
     * commit that produced it. Recognizes GitHub Actions ({@code GITHUB_ACTIONS}) and GitLab CI
     * ({@code GITLAB_CI}) environment variables; falls back to a "not run in CI/CD" state when
     * neither is present (e.g. a local {@code generate} invocation).
     *
     * @param env process environment variables (see {@link System#getenv()})
     * @return template data for {@code build-info.ftl}
     */
    private Map<String, Object> createBuildInfoData(Map<String, String> env) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Build Info");
        data.put("pageHeading", "Build Info");
        data.put("pageDescription", "Provenance for this generated site");
        data.put("rootPath", "");

        String provider = "";
        String repositoryName = "";
        String repositoryUrl = "";
        String commitSha = "";
        String ref = "";
        String commitUrl = "";
        String runLabel = "";
        String runUrl = "";
        String workflowName = "";
        String triggeredBy = "";
        String eventName = "";

        if ("true".equalsIgnoreCase(env.get("GITHUB_ACTIONS"))) {
            provider = "GitHub Actions";
            repositoryName = safe(env.get("GITHUB_REPOSITORY"));
            String serverUrl = safe(env.get("GITHUB_SERVER_URL"));
            repositoryUrl = isBlank(serverUrl) || isBlank(repositoryName) ? "" : serverUrl + "/" + repositoryName;
            commitSha = safe(env.get("GITHUB_SHA"));
            ref = safe(env.get("GITHUB_REF_NAME"));
            commitUrl = isBlank(repositoryUrl) || isBlank(commitSha) ? "" : repositoryUrl + "/commit/" + commitSha;
            String runId = safe(env.get("GITHUB_RUN_ID"));
            String runNumber = safe(env.get("GITHUB_RUN_NUMBER"));
            runUrl = isBlank(repositoryUrl) || isBlank(runId) ? "" : repositoryUrl + "/actions/runs/" + runId;
            runLabel = isBlank(runNumber) ? "" : "Run #" + runNumber;
            workflowName = safe(env.get("GITHUB_WORKFLOW"));
            triggeredBy = safe(env.get("GITHUB_ACTOR"));
            eventName = safe(env.get("GITHUB_EVENT_NAME"));
        } else if ("true".equalsIgnoreCase(env.get("GITLAB_CI"))) {
            provider = "GitLab CI/CD";
            repositoryUrl = safe(env.get("CI_PROJECT_URL"));
            repositoryName = safe(env.get("CI_PROJECT_PATH"));
            if (isBlank(repositoryName)) {
                repositoryName = safe(env.get("CI_PROJECT_NAME"));
            }
            commitSha = safe(env.get("CI_COMMIT_SHA"));
            ref = safe(env.get("CI_COMMIT_REF_NAME"));
            commitUrl = isBlank(repositoryUrl) || isBlank(commitSha) ? "" : repositoryUrl + "/-/tree/" + commitSha;
            runUrl = safe(env.get("CI_PIPELINE_URL"));
            String pipelineId = safe(env.get("CI_PIPELINE_ID"));
            runLabel = isBlank(pipelineId) ? "" : "Pipeline #" + pipelineId;
            workflowName = safe(env.get("CI_PIPELINE_NAME"));
            triggeredBy = safe(env.get("GITLAB_USER_LOGIN"));
            eventName = safe(env.get("CI_PIPELINE_SOURCE"));
        }

        data.put("hasCiInfo", !isBlank(provider));
        data.put("provider", provider);
        data.put("repositoryName", repositoryName);
        data.put("repositoryUrl", repositoryUrl);
        data.put("commitSha", commitSha);
        data.put("commitShortSha", commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha);
        data.put("commitUrl", commitUrl);
        data.put("ref", ref);
        data.put("runLabel", runLabel);
        data.put("runUrl", runUrl);
        data.put("workflowName", workflowName);
        data.put("triggeredBy", triggeredBy);
        data.put("eventName", eventName);
        data.put(
                "generatedAt",
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now()));
        return data;
    }

    private Configuration createFreemarkerConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return configuration;
    }

    private void writeTemplate(Configuration configuration, String templateName, Path outputPath, Map<String, Object> data)
            throws IOException, TemplateException {
        Files.createDirectories(outputPath.getParent());
        Template template = configuration.getTemplate(templateName);
        Map<String, Object> pageData = new HashMap<>(data);
        applyGlobalTemplateOptions(pageData);
        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            template.process(pageData, writer);
        }
    }

    /**
     * Applies global template options shared by every generated page.
     *
     * @param pageData mutable page data map
     */
    private void applyGlobalTemplateOptions(Map<String, Object> pageData) {
        pageData.put("bannerText", safe(bannerText));
        pageData.put("bannerTextColorDark", safe(bannerTextColorDark));
        pageData.put("bannerBackgroundColorDark", safe(bannerBackgroundColorDark));
        pageData.put("bannerTextColorLight", safe(bannerTextColorLight));
        pageData.put("bannerBackgroundColorLight", safe(bannerBackgroundColorLight));
        pageData.put("faviconPath", faviconAssetPath);
        pageData.put("faviconMimeType", faviconMimeType);
    }

    private void writeSearchIndex(Path outputPath, List<Map<String, Object>> searchEntries) throws IOException {
        Files.createDirectories(Objects.requireNonNull(outputPath.getParent()));
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), searchEntries);
    }

    /**
     * Copies each cached parser icon (see {@code ParserIconCache}) into the generated
     * site's assets and returns each icon's site-relative URL keyed by parser plugin id.
     *
     * @param iconsDir icon cache directory populated by {@code parse}
     * @param outputRoot generated site output root
     * @return site-relative icon URL by parser plugin id
     */
    private Map<String, String> copyParserIcons(Path iconsDir, Path outputRoot) throws IOException {
        Map<String, String> parserIconUrls = new HashMap<>();
        if (!Files.isDirectory(iconsDir)) {
            return parserIconUrls;
        }

        Path iconsOutputDir = outputRoot.resolve("assets/icons");
        try (var iconFiles = Files.list(iconsDir)) {
            for (Path iconFile : (Iterable<Path>) iconFiles::iterator) {
                if (!Files.isRegularFile(iconFile)) {
                    continue;
                }
                String fileName = iconFile.getFileName().toString();
                int extensionSeparator = fileName.lastIndexOf('.');
                String pluginId = extensionSeparator > 0 ? fileName.substring(0, extensionSeparator) : fileName;

                Files.createDirectories(iconsOutputDir);
                Files.copy(iconFile, iconsOutputDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                parserIconUrls.put(pluginId, "/assets/icons/" + encodePathSegment(fileName));
            }
        }
        return parserIconUrls;
    }

    /**
     * Reads a parser id -> string JSON cache file (see {@code ParserDisplayNameCache} and
     * {@code ParserInstallGuideCache}), populated by {@code parse}. Falls back to an empty map
     * when the cache is missing or unreadable, so callers should fall back sensibly per entry
     * (e.g. the raw parser id, or omitting the feature entirely).
     *
     * @param cachePath parser id -> string cache file populated by {@code parse}
     * @return cached value by parser id
     */
    private Map<String, String> readStringMapCache(Path cachePath) {
        if (!Files.isRegularFile(cachePath)) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, String> values = OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, String>>() {
            });
            return values == null ? Map.of() : values;
        } catch (IOException e) {
            LOGGER.warn("Failed to read parser cache at " + cachePath, e);
            return Map.of();
        }
    }

    private void copyAsset(String classpathResource, Path destination) throws IOException {
        Files.createDirectories(Objects.requireNonNull(destination.getParent()));
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + classpathResource);
            }
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Resolves the effective favicon for this {@code generate()} run - a custom image from
     * {@code --favicon} when given and readable, the bundled default otherwise - copies it into
     * the generated site, and records its site-relative asset path and MIME type in
     * {@link #faviconAssetPath} / {@link #faviconMimeType} for use in every page's
     * {@code <link rel="icon">}/header icon (see {@link #applyGlobalTemplateOptions(Map)}) and as
     * the fallback icon for any parser/artifact with none of its own (see {@link #defaultIconUrl()}).
     *
     * @param outputRoot generated site output root
     */
    private void copyFavicon(Path outputRoot) throws IOException {
        if (faviconOverride != null) {
            if (!Files.isRegularFile(faviconOverride)) {
                LOGGER.warn("Favicon override '{}' does not exist; using the default favicon.", faviconOverride);
            } else {
                String extension = PathUtils.getExtension(faviconOverride);
                String fileName = "favicon" + (isBlank(extension) ? "" : "." + extension);
                Path destination = outputRoot.resolve("assets").resolve(fileName);
                Files.createDirectories(destination.getParent());
                Files.copy(faviconOverride, destination, StandardCopyOption.REPLACE_EXISTING);
                faviconAssetPath = "assets/" + fileName;
                faviconMimeType = faviconMimeType(extension);
                return;
            }
        }

        faviconAssetPath = DEFAULT_FAVICON_ASSET_PATH;
        faviconMimeType = DEFAULT_FAVICON_MIME_TYPE;
        copyAsset(DEFAULT_FAVICON_ASSET_PATH, outputRoot.resolve(DEFAULT_FAVICON_ASSET_PATH));
    }

    /** Maps a favicon file extension to its MIME type, defaulting to a generic icon type. */
    private static String faviconMimeType(@Nullable String extension) {
        if (isBlank(extension)) {
            return "image/x-icon";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/x-icon";
        };
    }

    /**
     * Site-relative URL (with leading slash, matching {@code parserIconUrls}' convention) of the
     * effective favicon resolved by {@link #copyFavicon(Path)} - shown for any parser/artifact
     * that doesn't supply its own icon.
     */
    private String defaultIconUrl() {
        return "/" + faviconAssetPath;
    }

    /**
     * Resolves the icon URL for a specific artifact version: a per-artifact icon (see
     * {@link ArtifactMetadata#getIconData()}), written into the generated site if the parser that
     * produced it discovered one, or the owning parser's default icon otherwise.
     *
     * @param artifact artifact version whose icon is being resolved
     * @param parserIconUrl fallback icon URL for the artifact's parser
     * @param outputRoot generated site output root
     * @param parserSegment path segment for the artifact's parser
     * @param artifactSegment path segment for the artifact
     * @param versionSegment path segment for this specific version
     * @return site-relative icon URL
     */
    private String resolveArtifactIconUrl(
            ArtifactMetadata artifact,
            String parserIconUrl,
            Path outputRoot,
            String parserSegment,
            String artifactSegment,
            String versionSegment) {
        String iconData = artifact.getIconData();
        if (isBlank(iconData)) {
            return parserIconUrl;
        }

        try {
            byte[] iconBytes = Base64.getDecoder().decode(iconData);
            String extension = PathUtils.getExtension(Path.of(safe(artifact.getIconFileName())));
            String fileName = versionSegment + "." + (isBlank(extension) ? "png" : extension);
            Path destination = outputRoot
                    .resolve("assets/icons/artifacts")
                    .resolve(parserSegment)
                    .resolve(artifactSegment)
                    .resolve(fileName);
            Files.createDirectories(Objects.requireNonNull(destination.getParent()));
            Files.write(destination, iconBytes);
            return "/assets/icons/artifacts/"
                    + encodePathSegment(parserSegment) + "/"
                    + encodePathSegment(artifactSegment) + "/"
                    + encodePathSegment(fileName);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not write custom icon for artifact " + artifact.getId(), e);
            return parserIconUrl;
        }
    }

    private String resolveDownloadUrl(ArtifactMetadata artifact, Path outputRoot) throws IOException {
        if (!"local".equalsIgnoreCase(safe(artifact.getSourceType()))) {
            if (!isBlank(artifact.getDownloadUrl())) {
                return artifact.getDownloadUrl();
            }
            return safe(artifact.getSourceValue());
        }

        if (isBlank(artifact.getSourceValue())) {
            return "#";
        }

        Path sourcePath = Path.of(artifact.getSourceValue());
        if (!Files.exists(sourcePath)) {
            return "#";
        }

        String artifactIdSegment = toPathSegment(safe(artifact.getArtifactId()));
        String versionSegment = toPathSegment(safe(artifact.getVersion()));
        String fileName = !isBlank(artifact.getFileName())
                ? artifact.getFileName()
                : sourcePath.getFileName().toString();

        Path destination = outputRoot
                .resolve("downloads")
                .resolve(artifactIdSegment)
                .resolve(versionSegment)
                .resolve(fileName);
        Files.createDirectories(Objects.requireNonNull(destination.getParent()));
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);

        return "/downloads/"
                + encodePathSegment(artifactIdSegment)
                + "/"
                + encodePathSegment(versionSegment)
                + "/"
                + encodePathSegment(fileName);
    }

    private static String relativeRootPath(Path pagePath, Path outputRoot) {
        Path parent = pagePath.getParent();
        if (parent == null) {
            return "";
        }
        Path relative = outputRoot.relativize(parent);
        StringBuilder rootPath = new StringBuilder();
        for (int i = 0; i < relative.getNameCount(); i++) {
            rootPath.append("../");
        }
        return rootPath.toString();
    }

    private static Map<String, Object> createArtifactCard(
            ArtifactMetadata artifact, String parserName, String detailUrl, String iconUrl) {
        Map<String, Object> card = new HashMap<>();
        card.put("name", displayName(artifact));
        card.put("version", safe(artifact.getVersion()));
        card.put("description", safe(artifact.getDescription()));
        card.put("tags", normalizeTags(artifact.getTags()));
        card.put("url", detailUrl);
        card.put("parserType", parserName);
        card.put("groupId", safe(artifact.getGroupId()));
        card.put("artifactId", safe(artifact.getArtifactId()));
        card.put("icon", iconUrl);
        return card;
    }

    private static Map<String, Object> createSearchEntry(Map<String, Object> card) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", card.get("name"));
        entry.put("version", card.get("version"));
        entry.put("description", card.get("description"));
        entry.put("tags", card.get("tags"));
        entry.put("url", card.get("url"));
        entry.put("parserType", card.get("parserType"));
        entry.put("groupId", card.get("groupId"));
        entry.put("artifactId", card.get("artifactId"));
        entry.put("icon", card.get("icon"));
        return entry;
    }

    private static List<String> normalizeTags(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    normalized.add(value.trim());
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private static String toArtifactDetailUrl(String parserSegment, String artifactSegment, String versionSegment) {
        return "/artifacts/" + parserSegment + "/" + artifactSegment + "/" + versionSegment + "/index.html";
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[._-]");
        String[] rightParts = right.split("[._-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            String leftPart = i < leftParts.length ? leftParts[i] : "";
            String rightPart = i < rightParts.length ? rightParts[i] : "";
            int cmp = compareVersionPart(leftPart, rightPart);
            if (cmp != 0) {
                return cmp;
            }
        }
        return left.compareToIgnoreCase(right);
    }

    private static int compareVersionPart(String leftPart, String rightPart) {
        if (leftPart.chars().allMatch(Character::isDigit) && rightPart.chars().allMatch(Character::isDigit)) {
            int leftNumber = Integer.parseInt(leftPart.isEmpty() ? "0" : leftPart);
            int rightNumber = Integer.parseInt(rightPart.isEmpty() ? "0" : rightPart);
            return Integer.compare(leftNumber, rightNumber);
        }
        return leftPart.compareToIgnoreCase(rightPart);
    }

    private static String toPathSegment(String raw) {
        String value = safe(raw).toLowerCase(Locale.ROOT);
        value = SANITIZE_SEGMENT.matcher(value).replaceAll("-");
        value = value.replaceAll("-+", "-");
        while (value.startsWith("-")) {
            value = value.substring(1);
        }
        while (value.endsWith("-")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "unknown" : value;
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String displayName(ArtifactMetadata artifact) {
        if (!isBlank(artifact.getArtifactName())) {
            return artifact.getArtifactName();
        }
        return artifact.getArtifactSlug();
    }

    /**
     * Renders a parser-discovered README as HTML for the artifact detail page's main content.
     *
     * @param markdown raw README markdown, or {@code null} when the artifact has none
     * @return rendered HTML, or an empty string when there is no README to render
     */
    private static String renderMarkdown(@Nullable String markdown) {
        if (isBlank(markdown)) {
            return "";
        }
        return MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
    }

    /**
     * Substitutes an artifact's own values into its parser's install guide template (see
     * {@code ArtifactParser#installGuideResourceName()}) for the "How to Install" popup on the
     * artifact detail page. The template itself is trusted, parser-author-controlled HTML and is
     * emitted as-is; only the substituted {@code {{groupId}}}/{@code {{artifactId}}}/
     * {@code {{version}}} placeholder values are HTML-escaped, since those come from the parsed
     * artifact (e.g. a third-party VSIX manifest).
     *
     * @param template install guide HTML template, or {@code null} when the parser has none
     * @param artifact artifact whose values fill the template's placeholders
     * @return rendered install guide HTML, or an empty string when there is no template
     */
    private static String renderInstallGuide(@Nullable String template, ArtifactMetadata artifact) {
        if (isBlank(template)) {
            return "";
        }
        return template
                .replace("{{groupId}}", escapeHtml(safe(artifact.getGroupId())))
                .replace("{{artifactId}}", escapeHtml(safe(artifact.getArtifactId())))
                .replace("{{version}}", escapeHtml(safe(artifact.getVersion())));
    }

    /** Escapes text for safe inclusion in HTML markup. */
    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Formats a file size in bytes into a human-readable binary unit string.
     *
     * @param bytes file size in bytes
     * @return human-readable size, or empty string when unknown
     */
    private static String formatFileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        String[] units = { "KB", "MB", "GB", "TB", "PB" };
        double value = bytes.doubleValue();
        int unitIndex = -1;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record ParserGroup(
            String parserName,
            String parserDisplayName,
            String parserSegment,
            List<Map<String, Object>> latestArtifacts,
            List<ArtifactGroup> artifactGroups) {
    }

    private record ArtifactGroup(
            String artifactKey,
            String artifactSegment,
            List<Map<String, Object>> versionRows,
            List<Map<String, Object>> detailPages) {
    }

    private record GenerationModel(
            List<ParserGroup> parserGroups,
            List<Map<String, Object>> allLatestArtifactCards,
            List<Map<String, Object>> searchIndexEntries,
            Map<String, List<Map<String, Object>>> tagCards) {
    }
}
