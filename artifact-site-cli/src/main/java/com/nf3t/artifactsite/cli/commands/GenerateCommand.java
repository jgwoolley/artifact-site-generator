package com.nf3t.artifactsite.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
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
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.PathUtils;
import com.nf3t.artifactsite.api.SectionSpec;
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
    // Plain CommonMark has no pipe-table syntax; it's a GFM extension that both the parser
    // and renderer must opt into, or "| a | b |" rows are read as one plain paragraph.
    private static final List<Extension> MARKDOWN_EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser MARKDOWN_PARSER = Parser.builder().extensions(MARKDOWN_EXTENSIONS).build();
    // README markdown comes from arbitrary third-party artifacts; escaping any raw HTML in the
    // source (rather than passing it through) keeps a malicious README from injecting a
    // <script> or other active content into the generated site.
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().escapeHtml(true).extensions(MARKDOWN_EXTENSIONS).build();
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

    void generate(List<Datum> artifactCatalog, Path outputRoot) throws IOException, TemplateException {
        String generatedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());

        Files.createDirectories(outputRoot);
        copyAsset("assets/styles.css", outputRoot.resolve("assets/styles.css"));
        copyAsset("assets/app.js", outputRoot.resolve("assets/app.js"));
        copyAsset("assets/logo.svg", outputRoot.resolve("assets/logo.svg"));
        copyFavicon(outputRoot);

        String homePageTitle = isBlank(siteTitle) ? "Artifact Registry" : siteTitle;
        writeManifest(outputRoot, homePageTitle);

        Map<String, String> parserIconUrls = copyParserIcons(parentCommand.iconsDir(), outputRoot);
        Map<String, String> typeDisplayNames = readStringMapCache(parentCommand.artifactTypeDisplayNamesPath());
        Map<String, String> parserInstallGuides = readStringMapCache(parentCommand.parserInstallGuidesPath());
        Map<String, List<String>> parserSeoTags = readListMapCache(parentCommand.parserSeoTagsPath());
        Map<String, List<SectionSpec>> artifactTypeSections = readSectionsMapCache(parentCommand.artifactTypeSectionsPath());
        GenerationModel model = buildModel(
                artifactCatalog, outputRoot, parserIconUrls, typeDisplayNames, parserInstallGuides, parserSeoTags,
                artifactTypeSections);
        writeSearchIndex(outputRoot.resolve("search-index.json"), model.searchIndexEntries());

        Configuration freemarker = createFreemarkerConfiguration();
        writeTemplate(
                freemarker,
                "index.ftl",
                outputRoot.resolve("index.html"),
                createRootIndexData(model, typeDisplayNames, homePageTitle));
        writeTemplate(
                freemarker, "build-info.ftl", outputRoot.resolve("build-info.html"), createBuildInfoData(System.getenv(), generatedAt));

        for (ArtifactGroup artifactGroup : model.artifactGroups()) {
            Path artifactIndexPath = outputRoot
                    .resolve("artifacts")
                    .resolve(artifactGroup.groupIdSegment())
                    .resolve(artifactGroup.artifactIdSegment())
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
            artifactData.put("pageKeywords", latestDetailPage.get("pageKeywords"));
            artifactData.put("versions", artifactGroup.versionRows());
            writeTemplate(freemarker, "index.ftl", artifactIndexPath, artifactData);

            for (Map<String, Object> detailPage : artifactGroup.detailPages()) {
                Path detailPath = outputRoot
                        .resolve("artifacts")
                        .resolve(artifactGroup.groupIdSegment())
                        .resolve(artifactGroup.artifactIdSegment())
                        .resolve((String) detailPage.get("versionSegment"))
                        .resolve("index.html");
                detailPage.put("rootPath", relativeRootPath(detailPath, outputRoot));
                writeTemplate(freemarker, "artifact.ftl", detailPath, detailPage);
            }
        }

        for (Map.Entry<String, List<Map<String, Object>>> tagEntry : model.tagCards().entrySet()) {
            String tag = tagEntry.getKey();
            List<Map<String, Object>> tagArtifactCards = tagEntry.getValue();
            Path tagPath = outputRoot.resolve("tags").resolve(toPathSegment(tag)).resolve("index.html");
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("title", "Tag: " + tag);
            tagData.put("pageHeading", "Tag: " + tag);
            tagData.put("pageDescription", "Artifacts tagged with “" + tag + "”");
            tagData.put("rootPath", relativeRootPath(tagPath, outputRoot));
            tagData.put("pageKeywords", tag);
            tagData.put(
                    "pageIcon",
                    tagArtifactCards.isEmpty() ? "" : tagArtifactCards.get(0).get("icon"));
            tagData.put("artifactCards", tagArtifactCards);
            writeTemplate(freemarker, "tag.ftl", tagPath, tagData);
        }

        // Artifact types are a cross-cutting classification, not a routing dimension (an artifact
        // can carry more than one, e.g. a NiFi Archive is both "jar" and "nar") - so this reuses
        // exactly the tag mechanism above (same data shape, same tag.ftl template) rather than the
        // old per-parser index pages it replaces.
        for (Map.Entry<String, List<Map<String, Object>>> typeEntry : model.typeCards().entrySet()) {
            String type = typeEntry.getKey();
            List<Map<String, Object>> typeArtifactCards = typeEntry.getValue();
            String typeLabel = typeDisplayNames.getOrDefault(type, type);
            Path typePath = outputRoot.resolve("types").resolve(toPathSegment(type)).resolve("index.html");
            Map<String, Object> typeData = new HashMap<>();
            typeData.put("title", "Type: " + typeLabel);
            typeData.put("pageHeading", "Type: " + typeLabel);
            typeData.put("pageDescription", "Artifacts of type “" + typeLabel + "”");
            typeData.put("rootPath", relativeRootPath(typePath, outputRoot));
            typeData.put("pageKeywords", typeLabel);
            typeData.put(
                    "pageIcon",
                    typeArtifactCards.isEmpty() ? "" : typeArtifactCards.get(0).get("icon"));
            typeData.put("artifactCards", typeArtifactCards);
            writeTemplate(freemarker, "tag.ftl", typePath, typeData);
        }

        // Must run last: the precache list (see collectPrecacheUrls) has to reflect every file
        // this run wrote above, and sw.js itself is naturally excluded since it doesn't exist yet.
        writeServiceWorker(freemarker, outputRoot, generatedAt);
    }

    /**
     * Writes the site's service worker (see {@code sw.ftl}), eagerly precaching every file this
     * {@code generate()} run produced so a fresh install works fully offline without waiting for
     * each page to be visited first.
     *
     * @param freemarker template engine
     * @param outputRoot generated site output root; must already contain every other file this
     *     run will write
     * @param cacheVersion per-build cache name suffix, so a redeploy's {@code activate} step evicts
     *     the previous build's cache instead of leaving stale precached files behind
     */
    private void writeServiceWorker(Configuration freemarker, Path outputRoot, String cacheVersion)
            throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("cacheVersion", cacheVersion);
        data.put("precacheUrls", collectPrecacheUrls(outputRoot));
        writeTemplate(freemarker, "sw.ftl", outputRoot.resolve("sw.js"), data);
    }

    /**
     * Lists every regular file already written under {@code outputRoot}, as site-relative URLs
     * (percent-encoded per path segment, matching every other generated link - see
     * {@link #encodePathSegment(String)}) for the service worker's install-time precache list.
     *
     * @param outputRoot generated site output root
     * @return site-relative URL for every regular file under {@code outputRoot}
     */
    private List<String> collectPrecacheUrls(Path outputRoot) throws IOException {
        List<String> urls = new ArrayList<>();
        try (var files = Files.walk(outputRoot)) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> urls.add(toSiteRelativeUrl(outputRoot, file)));
        }
        return urls;
    }

    private static String toSiteRelativeUrl(Path outputRoot, Path file) {
        Path relative = outputRoot.relativize(file);
        StringBuilder url = new StringBuilder();
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (i > 0) {
                url.append('/');
            }
            url.append(encodePathSegment(relative.getName(i).toString()));
        }
        return url.toString();
    }

    private GenerationModel buildModel(
            List<Datum> artifactCatalog,
            Path outputRoot,
            Map<String, String> parserIconUrls,
            Map<String, String> typeDisplayNames,
            Map<String, String> parserInstallGuides,
            Map<String, List<String>> parserSeoTags,
            Map<String, List<SectionSpec>> artifactTypeSections)
            throws IOException {
        Map<String, List<Datum>> grouped = new LinkedHashMap<>();

        for (Datum artifact : artifactCatalog) {
            if (isBlank(ArtifactFields.PLUGIN_ID.getString(artifact))
                    || isBlank(ArtifactFields.GROUP_ID.getString(artifact))
                    || isBlank(ArtifactFields.ARTIFACT_ID.getString(artifact))
                    || isBlank(ArtifactFields.VERSION.getString(artifact))) {
                LOGGER.warn("Ignored Artifact");
                continue;
            }

            grouped.computeIfAbsent(ArtifactFields.getArtifactSlug(artifact), ignored -> new ArrayList<>())
                    .add(artifact);
        }

        List<ArtifactGroup> artifactGroups = new ArrayList<>();
        List<Map<String, Object>> allLatestArtifactCards = new ArrayList<>();
        List<Map<String, Object>> searchEntries = new ArrayList<>();
        Map<String, List<Map<String, Object>>> tagCards = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> typeCards = new LinkedHashMap<>();

        List<String> artifactKeys = new ArrayList<>(grouped.keySet());
        artifactKeys.sort(String::compareToIgnoreCase);

        for (String artifactKey : artifactKeys) {
            List<Datum> versions = new ArrayList<>(grouped.get(artifactKey));
            versions.sort((a, b) -> compareVersions(
                    safe(ArtifactFields.VERSION.getString(b)), safe(ArtifactFields.VERSION.getString(a))));

            Datum latest = versions.get(0);
            String groupIdSegment = toPathSegment(safe(ArtifactFields.GROUP_ID.getString(latest)));
            String artifactIdSegment = toPathSegment(safe(ArtifactFields.ARTIFACT_ID.getString(latest)));
            String latestVersionSegment = toPathSegment(safe(ArtifactFields.VERSION.getString(latest)));
            String detailUrl = toArtifactDetailUrl(groupIdSegment, artifactIdSegment, latestVersionSegment);

            String latestParserIconUrl = parserIconUrls.getOrDefault(
                    safe(ArtifactFields.PLUGIN_ID.getString(latest)), defaultIconUrl());
            String latestIconUrl = resolveArtifactIconUrl(
                    latest, latestParserIconUrl, outputRoot, groupIdSegment, artifactIdSegment, latestVersionSegment);
            String latestTypeLabel = typeLabel(canonicalArtifactType(latest), typeDisplayNames);
            Map<String, Object> latestCard = createArtifactCard(latest, latestTypeLabel, detailUrl, latestIconUrl);
            allLatestArtifactCards.add(latestCard);
            searchEntries.add(createSearchEntry(latestCard));

            for (String tag : normalizeTags(ArtifactFields.TAGS.getStringList(latest))) {
                tagCards.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(latestCard);
            }
            // Browsing-by-type is inclusive (like tags): an artifact with several types (e.g. a
            // NiFi Archive is both "jar" and "nar") appears on every one of its type pages, even
            // though only one of them wins as its own card/badge label above.
            for (String type : effectiveArtifactTypes(latest)) {
                typeCards.computeIfAbsent(type, ignored -> new ArrayList<>()).add(latestCard);
            }

            List<Map<String, Object>> versionRows = new ArrayList<>();
            List<Map<String, Object>> detailPages = new ArrayList<>();
            for (Datum version : versions) {
                String versionValue = safe(ArtifactFields.VERSION.getString(version));
                String versionSegment = toPathSegment(versionValue);
                String versionUrl = toArtifactDetailUrl(groupIdSegment, artifactIdSegment, versionSegment);
                String downloadUrl = resolveDownloadUrl(version, outputRoot);
                String versionPluginId = safe(ArtifactFields.PLUGIN_ID.getString(version));
                String versionParserIconUrl = parserIconUrls.getOrDefault(versionPluginId, defaultIconUrl());
                String versionIconUrl = resolveArtifactIconUrl(
                        version, versionParserIconUrl, outputRoot, groupIdSegment, artifactIdSegment, versionSegment);
                String installGuideTemplate = parserInstallGuides.get(versionPluginId);
                List<String> versionParserKeywords = parserSeoTags.getOrDefault(versionPluginId, List.of());
                List<String> versionTypeLabels = effectiveArtifactTypes(version).stream()
                        .map(type -> typeLabel(type, typeDisplayNames))
                        .toList();

                Map<String, Object> versionRow = new HashMap<>();
                versionRow.put("version", versionValue);
                versionRow.put("description", safe(ArtifactFields.DESCRIPTION.getString(version)));
                versionRow.put("downloadUrl", downloadUrl);
                versionRow.put("detailUrl", versionUrl);
                versionRows.add(versionRow);

                List<String> versionTagsAndTypes = new ArrayList<>(ArtifactFields.TAGS.getStringList(version));
                versionTagsAndTypes.addAll(versionTypeLabels);

                Map<String, Object> detailPage = new HashMap<>();
                detailPage.put("title", displayName(version) + " " + versionValue);
                detailPage.put("pageHeading", displayName(version));
                detailPage.put("artifactKey", artifactKey);
                detailPage.put("version", versionValue);
                detailPage.put("versionSegment", versionSegment);
                detailPage.put("artifactType", typeLabel(canonicalArtifactType(version), typeDisplayNames));
                detailPage.put("groupId", safe(ArtifactFields.GROUP_ID.getString(version)));
                detailPage.put("artifactId", safe(ArtifactFields.ARTIFACT_ID.getString(version)));
                detailPage.put("description", safe(ArtifactFields.DESCRIPTION.getString(version)));
                detailPage.put("license", safe(ArtifactFields.LICENSE.getString(version)));
                detailPage.put("sourceType", safe(ArtifactFields.SOURCE_TYPE.getString(version)));
                detailPage.put("sourceValue", safe(ArtifactFields.SOURCE_VALUE.getString(version)));
                detailPage.put("scmUrl", safe(ArtifactFields.SCM_URL.getString(version)));
                detailPage.put("fileName", safe(ArtifactFields.FILE_NAME.getString(version)));
                detailPage.put("sha256", safe(ArtifactFields.FILE_SHA256.getString(version)));
                detailPage.put("fileSizeBytes", ArtifactFields.FILE_SIZE.getLong(version));
                detailPage.put("fileSizeHumanReadable", formatFileSize(ArtifactFields.FILE_SIZE.getLong(version)));
                detailPage.put("downloadUrl", downloadUrl);
                detailPage.put("pageIcon", versionIconUrl);
                detailPage.put("readmeHtml", renderMarkdown(ArtifactFields.README.getString(version)));
                detailPage.put("installGuideHtml", renderInstallGuide(installGuideTemplate, version));
                detailPage.put("tags", normalizeTags(ArtifactFields.TAGS.getStringList(version)));
                detailPage.put("authors", normalizeTags(ArtifactFields.AUTHORS.getStringList(version)));
                detailPage.put(
                        "pageKeywords",
                        String.join(", ", combineKeywords(versionTagsAndTypes, versionParserKeywords)));
                detailPage.put("sections", buildSections(version, artifactTypeSections));
                detailPages.add(detailPage);
            }

            artifactGroups.add(new ArtifactGroup(artifactKey, groupIdSegment, artifactIdSegment, versionRows, detailPages));
        }

        allLatestArtifactCards.sort(
                Comparator.comparing((Map<String, Object> card) -> (String) card.get("name"), String::compareToIgnoreCase));

        for (Map.Entry<String, List<Map<String, Object>>> entry : tagCards.entrySet()) {
            entry.getValue().sort(Comparator.comparing(card -> (String) card.get("name"), String::compareToIgnoreCase));
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : typeCards.entrySet()) {
            entry.getValue().sort(Comparator.comparing(card -> (String) card.get("name"), String::compareToIgnoreCase));
        }

        return new GenerationModel(artifactGroups, allLatestArtifactCards, searchEntries, tagCards, typeCards);
    }

    /**
     * An artifact's own {@link ArtifactFields#ARTIFACT_TYPES}, falling back to its {@link
     * ArtifactFields#PLUGIN_ID} as a pseudo-type when it declares none at all - so an artifact
     * produced by a parser that hasn't adopted {@code ARTIFACT_TYPES} yet (or a hand-built record
     * predating it) still gets a sensible type-browsing entry instead of vanishing from it.
     *
     * @param artifact artifact whose types are being resolved
     * @return one or more artifact type keys, never empty
     */
    private static List<String> effectiveArtifactTypes(Datum artifact) {
        List<String> types = ArtifactFields.ARTIFACT_TYPES.getStringList(artifact);
        return types.isEmpty() ? List.of(safe(ArtifactFields.PLUGIN_ID.getString(artifact))) : types;
    }

    /**
     * The single type shown as an artifact's own card/detail-page label - unlike type-browsing
     * (see {@link #effectiveArtifactTypes(Datum)}), which is inclusive of every type an artifact
     * carries, a compact one-line badge needs exactly one winner. The last entry in {@link
     * ArtifactFields#ARTIFACT_TYPES} wins, since parsers append from generic to specific (e.g. a
     * NiFi Archive gets {@code "jar"} unconditionally, then {@code "nar"} only once its manifest is
     * found, so the more specific, more informative type is listed last).
     *
     * @param artifact artifact whose canonical display type is being resolved
     * @return the winning artifact type key
     */
    private static String canonicalArtifactType(Datum artifact) {
        List<String> types = effectiveArtifactTypes(artifact);
        return types.get(types.size() - 1);
    }

    private static String typeLabel(String type, Map<String, String> typeDisplayNames) {
        return typeDisplayNames.getOrDefault(type, type);
    }

    private Map<String, Object> createRootIndexData(
            GenerationModel model, Map<String, String> typeDisplayNames, String homePageTitle) {
        List<Map<String, Object>> typeSummaries = new ArrayList<>();
        List<String> typeLabels = new ArrayList<>();
        List<String> typeKeys = new ArrayList<>(model.typeCards().keySet());
        typeKeys.sort(String::compareToIgnoreCase);
        for (String type : typeKeys) {
            List<Map<String, Object>> cardsForType = model.typeCards().get(type);
            String label = typeLabel(type, typeDisplayNames);
            Map<String, Object> typeSummary = new HashMap<>();
            typeSummary.put("typeName", label);
            typeSummary.put("artifactCount", cardsForType.size());
            typeSummary.put("url", "/types/" + toPathSegment(type) + "/index.html");
            typeSummary.put("icon", cardsForType.isEmpty() ? defaultIconUrl() : cardsForType.get(0).get("icon"));
            typeSummaries.add(typeSummary);
            typeLabels.add(label);
        }

        Map<String, Object> modelData = new HashMap<>();
        modelData.put("title", homePageTitle);
        modelData.put("pageHeading", homePageTitle);
        modelData.put("pageDescription", "Browse generated artifacts by type, tag, and version");
        modelData.put("rootPath", "");
        modelData.put("pageKeywords", String.join(", ", combineKeywords(typeLabels, List.of())));
        modelData.put("typeSummaries", typeSummaries);
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
     * @param generatedAt this run's generation timestamp (shared with the service worker's cache
     *     version - see {@link #writeServiceWorker(Configuration, Path, String)})
     * @return template data for {@code build-info.ftl}
     */
    private Map<String, Object> createBuildInfoData(Map<String, String> env, String generatedAt) {
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
        data.put("generatedAt", generatedAt);

        Map<String, String> cliManifestAttributes = readCliManifestAttributes();
        data.put("cliTitle", safe(cliManifestAttributes.get("Implementation-Title")));
        data.put("cliVersion", safe(cliManifestAttributes.get("Implementation-Version")));
        data.put("cliVendor", safe(cliManifestAttributes.get("Implementation-Vendor")));
        data.put("cliBuildJdk", safe(cliManifestAttributes.get("Build-Jdk-Spec")));
        data.put("cliBuiltAt", safe(cliManifestAttributes.get("Implementation-Timestamp")));
        return data;
    }

    /**
     * Reads the {@code Implementation-*}/{@code Build-Jdk-Spec} attributes that the shaded CLI
     * jar's manifest is stamped with at build time (see the {@code maven-shade-plugin}
     * {@code manifestEntries} in {@code artifact-site-cli/pom.xml}), so {@code build-info.html} can
     * show provenance for the CLI tool itself alongside the CI/CD provenance for the site it
     * generated. Falls back to an empty map when not running from a jar (e.g. tests or an IDE
     * launch), since there is no single manifest to attribute to the running classes in that case.
     *
     * @return manifest attribute values by name, or an empty map if unavailable
     */
    private Map<String, String> readCliManifestAttributes() {
        try {
            CodeSource codeSource = GenerateCommand.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return Map.of();
            }
            Path location = Path.of(codeSource.getLocation().toURI());
            if (!Files.isRegularFile(location)) {
                return Map.of();
            }
            try (JarFile jarFile = new JarFile(location.toFile())) {
                Manifest manifest = jarFile.getManifest();
                if (manifest == null) {
                    return Map.of();
                }
                Map<String, String> attributes = new HashMap<>();
                manifest.getMainAttributes().forEach((key, value) -> attributes.put(key.toString(), (String) value));
                return attributes;
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.warn("Could not read CLI build manifest", e);
            return Map.of();
        }
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
     * Reads a string-keyed JSON cache file (e.g. {@code ArtifactTypeDisplayNameCache} keyed by
     * artifact type, or {@code ParserInstallGuideCache} keyed by parser id), populated by {@code
     * parse}. Falls back to an empty map when the cache is missing or unreadable, so callers
     * should fall back sensibly per entry (e.g. the raw key, or omitting the feature entirely).
     *
     * @param cachePath string-keyed cache file populated by {@code parse}
     * @return cached value by key
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

    /**
     * Reads a parser id -> string-list JSON cache file (see {@code ParserSeoTagsCache}),
     * populated by {@code parse}. Falls back to an empty map when the cache is missing or
     * unreadable, so callers should fall back to an empty keyword list per parser.
     *
     * @param cachePath parser id -> string-list cache file populated by {@code parse}
     * @return cached value by parser id
     */
    private Map<String, List<String>> readListMapCache(Path cachePath) {
        if (!Files.isRegularFile(cachePath)) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, List<String>> values =
                    OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, List<String>>>() {
                    });
            return values == null ? Map.of() : values;
        } catch (IOException e) {
            LOGGER.warn("Failed to read parser cache at " + cachePath, e);
            return Map.of();
        }
    }

    /**
     * Reads the artifact type -> declared sections JSON cache file (see
     * {@code ArtifactTypeSectionsCache}), populated by {@code parse}. Falls back to an empty map
     * when the cache is missing or unreadable, so callers should fall back to no declared sections.
     *
     * @param cachePath artifact type -> declared sections cache file populated by {@code parse}
     * @return cached sections by artifact type
     */
    private Map<String, List<SectionSpec>> readSectionsMapCache(Path cachePath) {
        if (!Files.isRegularFile(cachePath)) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, List<SectionSpec>> values =
                    OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, List<SectionSpec>>>() {
                    });
            return values == null ? Map.of() : values;
        } catch (IOException e) {
            LOGGER.warn("Failed to read artifact type sections cache at " + cachePath, e);
            return Map.of();
        }
    }

    /**
     * Writes the site's PWA web manifest, naming it after the effective home page title and
     * pointing its icon at the effective favicon (see {@link #copyFavicon(Path)}), so both stay in
     * sync with {@code --title}/{@code --favicon}.
     *
     * @param outputRoot generated site output root
     * @param homePageTitle effective home page title (default or {@code --title})
     */
    private void writeManifest(Path outputRoot, String homePageTitle) throws IOException {
        Map<String, Object> icon = new LinkedHashMap<>();
        icon.put("src", faviconAssetPath);
        icon.put("sizes", "any");
        icon.put("type", faviconMimeType);
        icon.put("purpose", "any");

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", homePageTitle);
        manifest.put("short_name", homePageTitle);
        manifest.put("description", "Browse generated artifacts by type, tag, and version");
        manifest.put("start_url", ".");
        manifest.put("scope", ".");
        manifest.put("display", "standalone");
        manifest.put("background_color", "#ffffff");
        manifest.put("theme_color", "#2563eb");
        manifest.put("icons", List.of(icon));

        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(outputRoot.resolve("manifest.webmanifest").toFile(), manifest);
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
     * {@link ArtifactFields#ICON_DATA}), written into the generated site if the parser that
     * produced it discovered one, or the owning parser's default icon otherwise.
     *
     * @param artifact artifact version whose icon is being resolved
     * @param parserIconUrl fallback icon URL for the artifact's own parser
     * @param outputRoot generated site output root
     * @param groupIdSegment path segment for the artifact's group id
     * @param artifactIdSegment path segment for the artifact's artifact id
     * @param versionSegment path segment for this specific version
     * @return site-relative icon URL
     */
    private String resolveArtifactIconUrl(
            Datum artifact,
            String parserIconUrl,
            Path outputRoot,
            String groupIdSegment,
            String artifactIdSegment,
            String versionSegment) {
        String iconData = ArtifactFields.ICON_DATA.getString(artifact);
        if (isBlank(iconData)) {
            return parserIconUrl;
        }

        try {
            byte[] iconBytes = Base64.getDecoder().decode(iconData);
            String extension = PathUtils.getExtension(Path.of(safe(ArtifactFields.ICON_ORIGINAL_FILE_NAME.getString(artifact))));
            String fileName = versionSegment + "." + (isBlank(extension) ? "png" : extension);
            Path destination = outputRoot
                    .resolve("assets/icons/artifacts")
                    .resolve(groupIdSegment)
                    .resolve(artifactIdSegment)
                    .resolve(fileName);
            Files.createDirectories(Objects.requireNonNull(destination.getParent()));
            Files.write(destination, iconBytes);
            return "/assets/icons/artifacts/"
                    + encodePathSegment(groupIdSegment) + "/"
                    + encodePathSegment(artifactIdSegment) + "/"
                    + encodePathSegment(fileName);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not write custom icon for artifact " + ArtifactFields.ID.getString(artifact), e);
            return parserIconUrl;
        }
    }

    private String resolveDownloadUrl(Datum artifact, Path outputRoot) throws IOException {
        if (!"local".equalsIgnoreCase(safe(ArtifactFields.SOURCE_TYPE.getString(artifact)))) {
            String downloadUrl = ArtifactFields.DOWNLOAD_URL.getString(artifact);
            if (!isBlank(downloadUrl)) {
                return downloadUrl;
            }
            return safe(ArtifactFields.SOURCE_VALUE.getString(artifact));
        }

        String sourceValue = ArtifactFields.SOURCE_VALUE.getString(artifact);
        if (isBlank(sourceValue)) {
            return "#";
        }

        Path sourcePath = Path.of(sourceValue);
        if (!Files.exists(sourcePath)) {
            return "#";
        }

        String artifactIdSegment = toPathSegment(safe(ArtifactFields.ARTIFACT_ID.getString(artifact)));
        String versionSegment = toPathSegment(safe(ArtifactFields.VERSION.getString(artifact)));
        String artifactFileName = ArtifactFields.FILE_NAME.getString(artifact);
        String fileName = !isBlank(artifactFileName)
                ? artifactFileName
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
            Datum artifact, String artifactTypeLabel, String detailUrl, String iconUrl) {
        Map<String, Object> card = new HashMap<>();
        card.put("name", displayName(artifact));
        card.put("version", safe(ArtifactFields.VERSION.getString(artifact)));
        card.put("description", safe(ArtifactFields.DESCRIPTION.getString(artifact)));
        card.put("tags", normalizeTags(ArtifactFields.TAGS.getStringList(artifact)));
        card.put("url", detailUrl);
        card.put("artifactType", artifactTypeLabel);
        card.put("groupId", safe(ArtifactFields.GROUP_ID.getString(artifact)));
        card.put("artifactId", safe(ArtifactFields.ARTIFACT_ID.getString(artifact)));
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
        entry.put("artifactType", card.get("artifactType"));
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

    /**
     * Combines a first list of keywords (e.g. an artifact's own tags, optionally already merged
     * with its artifact type display names - see {@code buildModel}) with a second (typically its
     * parser's declared SEO keywords, see {@code ArtifactParser#seoTags()}) for a page's {@code
     * <meta name="keywords">}, deduplicated and trimmed, the first list's entries first.
     *
     * @param artifactTags the first keyword list, or {@code null}
     * @param parserKeywords the second keyword list, or {@code null}
     * @return combined, deduplicated keyword list
     */
    private static List<String> combineKeywords(@Nullable List<String> artifactTags, @Nullable List<String> parserKeywords) {
        List<String> combined = new ArrayList<>();
        if (artifactTags != null) {
            combined.addAll(artifactTags);
        }
        if (parserKeywords != null) {
            combined.addAll(parserKeywords);
        }
        return normalizeTags(combined);
    }

    private static String toArtifactDetailUrl(String groupIdSegment, String artifactIdSegment, String versionSegment) {
        return "/artifacts/" + groupIdSegment + "/" + artifactIdSegment + "/" + versionSegment + "/index.html";
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

    private static String displayName(Datum artifact) {
        String name = ArtifactFields.NAME.getString(artifact);
        if (!isBlank(name)) {
            return name;
        }
        return ArtifactFields.getArtifactSlug(artifact);
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
     * Builds the artifact detail page's main-content sections, declared per artifact type by
     * {@code ArtifactSectionProvider} (see {@link ArtifactFields#ARTIFACT_TYPES}) and cached by
     * {@code parse} into the map passed here. An artifact carrying more than one type (e.g. a NiFi
     * Archive is both {@code "jar"} and {@code "nar"}) gets one block of sections per type, in the
     * order the types are listed on the artifact, each type's own sections in declared order.
     *
     * <p>A section renders nothing (and is omitted) when its backing field is blank/empty, so an
     * artifact type with no matching content on a particular artifact doesn't produce an empty
     * heading. When no section produces any content, {@code artifact.ftl} falls back to rendering
     * {@code readmeHtml} directly, matching this feature's pre-existing behavior.
     *
     * @param artifact artifact version whose sections are being built
     * @param artifactTypeSections artifact type -> declared sections, from {@code parse}'s cache
     * @return rendered section view models, in render order; empty when none apply or produce content
     */
    private static List<Map<String, Object>> buildSections(
            Datum artifact, Map<String, List<SectionSpec>> artifactTypeSections) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (String artifactType : ArtifactFields.ARTIFACT_TYPES.getStringList(artifact)) {
            for (SectionSpec spec : artifactTypeSections.getOrDefault(artifactType, List.of())) {
                Map<String, Object> section = buildSection(artifact, spec);
                if (section != null) {
                    sections.add(section);
                }
            }
        }
        return sections;
    }

    /**
     * Renders one declared section, or returns {@code null} when its backing field has no content
     * to show.
     */
    private static @Nullable Map<String, Object> buildSection(Datum artifact, SectionSpec spec) {
        switch (spec.element()) {
            case MARKDOWN -> {
                String html = renderMarkdown(artifact.getString(spec.namespace(), spec.key()));
                if (isBlank(html)) {
                    return null;
                }
                Map<String, Object> section = new HashMap<>();
                section.put("title", spec.title());
                section.put("type", "markdown");
                section.put("html", html);
                return section;
            }
            case TABLE -> {
                List<Map<String, String>> rows = artifact.getRecordList(spec.namespace(), spec.key());
                if (rows.isEmpty()) {
                    return null;
                }
                LinkedHashSet<String> columns = new LinkedHashSet<>();
                for (Map<String, String> row : rows) {
                    columns.addAll(row.keySet());
                }
                Map<String, Object> section = new HashMap<>();
                section.put("title", spec.title());
                section.put("type", "table");
                section.put("columns", new ArrayList<>(columns));
                section.put("rows", rows);
                return section;
            }
            default -> {
                return null;
            }
        }
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
    private static String renderInstallGuide(@Nullable String template, Datum artifact) {
        if (isBlank(template)) {
            return "";
        }
        return template
                .replace("{{groupId}}", escapeHtml(safe(ArtifactFields.GROUP_ID.getString(artifact))))
                .replace("{{artifactId}}", escapeHtml(safe(ArtifactFields.ARTIFACT_ID.getString(artifact))))
                .replace("{{version}}", escapeHtml(safe(ArtifactFields.VERSION.getString(artifact))));
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

    private record ArtifactGroup(
            String artifactKey,
            String groupIdSegment,
            String artifactIdSegment,
            List<Map<String, Object>> versionRows,
            List<Map<String, Object>> detailPages) {
    }

    private record GenerationModel(
            List<ArtifactGroup> artifactGroups,
            List<Map<String, Object>> allLatestArtifactCards,
            List<Map<String, Object>> searchIndexEntries,
            Map<String, List<Map<String, Object>>> tagCards,
            Map<String, List<Map<String, Object>>> typeCards) {
    }
}
