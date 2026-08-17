package com.nf3t.artifactsite.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates a static artifact registry site from catalog metadata.
 */
@Command(name = "generate", description = "Generates a static artifact registry site")
public class GenerateCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateCommand.class);
    private static final Pattern SANITIZE_SEGMENT = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Option(names = "--output", description = "Output directory for generated static site", defaultValue = "./public")
    private Path outputDir;

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

        GenerationModel model = buildModel(artifactCatalog, outputRoot);
        writeSearchIndex(outputRoot.resolve("search-index.json"), model.searchIndexEntries());

        Configuration freemarker = createFreemarkerConfiguration();
        writeTemplate(freemarker, "index.ftl", outputRoot.resolve("index.html"), createRootIndexData(model));

        for (ParserGroup parserGroup : model.parserGroups()) {
            Path parserIndexPath = outputRoot.resolve("artifacts").resolve(parserGroup.parserSegment()).resolve("index.html");
            Map<String, Object> parserData = new HashMap<>();
            parserData.put("title", parserGroup.parserName() + " Artifacts");
            parserData.put("pageHeading", parserGroup.parserName() + " Artifacts");
            parserData.put("pageDescription", "Latest artifacts parsed by " + parserGroup.parserName());
            parserData.put("rootPath", relativeRootPath(parserIndexPath, outputRoot));
            parserData.put("artifactCards", parserGroup.latestArtifacts());
            writeTemplate(freemarker, "index.ftl", parserIndexPath, parserData);

            for (ArtifactGroup artifactGroup : parserGroup.artifactGroups()) {
                Path artifactIndexPath = outputRoot
                        .resolve("artifacts")
                        .resolve(parserGroup.parserSegment())
                        .resolve(artifactGroup.artifactSegment())
                        .resolve("index.html");
                Map<String, Object> artifactData = new HashMap<>();
                artifactData.put("title", artifactGroup.artifactKey() + " Versions");
                artifactData.put("pageHeading", artifactGroup.artifactKey() + " Versions");
                artifactData.put("pageDescription", "All published versions for " + artifactGroup.artifactKey());
                artifactData.put("rootPath", relativeRootPath(artifactIndexPath, outputRoot));
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

    private GenerationModel buildModel(List<ArtifactMetadata> artifactCatalog, Path outputRoot) throws IOException {
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

                Map<String, Object> latestCard = createArtifactCard(latest, parserName, detailUrl);
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

                    Map<String, Object> versionRow = new HashMap<>();
                    versionRow.put("version", versionValue);
                    versionRow.put("description", safe(version.getDescription()));
                    versionRow.put("downloadUrl", downloadUrl);
                    versionRow.put("detailUrl", versionUrl);
                    versionRows.add(versionRow);

                    Map<String, Object> detailPage = new HashMap<>();
                    detailPage.put("title", artifactKey + " " + versionValue);
                    detailPage.put("pageHeading", displayName(version));
                    detailPage.put("artifactKey", artifactKey);
                    detailPage.put("version", versionValue);
                    detailPage.put("versionSegment", versionSegment);
                    detailPage.put("parserType", parserName);
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
                    detailPage.put("downloadUrl", downloadUrl);
                    detailPage.put("tags", normalizeTags(version.getTags()));
                    detailPage.put("authors", normalizeTags(version.getAuthors()));
                    detailPages.add(detailPage);
                }

                artifactGroups.add(new ArtifactGroup(artifactKey, artifactSegment, versionRows, detailPages));
            }

            parserGroups.add(new ParserGroup(parserName, parserSegment, latestArtifacts, artifactGroups));
        }

        allLatestArtifactCards.sort(
                Comparator.comparing((Map<String, Object> card) -> (String) card.get("name"), String::compareToIgnoreCase));

        for (Map.Entry<String, List<Map<String, Object>>> entry : tagCards.entrySet()) {
            entry.getValue().sort(Comparator.comparing(card -> (String) card.get("name"), String::compareToIgnoreCase));
        }

        return new GenerationModel(parserGroups, allLatestArtifactCards, searchEntries, tagCards);
    }

    private Map<String, Object> createRootIndexData(GenerationModel model) {
        List<Map<String, Object>> parserSummaries = new ArrayList<>();
        for (ParserGroup parserGroup : model.parserGroups()) {
            Map<String, Object> parserSummary = new HashMap<>();
            parserSummary.put("parserName", parserGroup.parserName());
            parserSummary.put("artifactCount", parserGroup.latestArtifacts().size());
            parserSummary.put("url", "/artifacts/" + parserGroup.parserSegment() + "/index.html");
            parserSummaries.add(parserSummary);
        }

        Map<String, Object> modelData = new HashMap<>();
        modelData.put("title", "Artifact Registry");
        modelData.put("pageHeading", "Artifact Registry");
        modelData.put("pageDescription", "Browse generated artifacts by parser, tag, and version");
        modelData.put("rootPath", "");
        modelData.put("parserSummaries", parserSummaries);
        modelData.put("artifactCards", model.allLatestArtifactCards());
        return modelData;
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
        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            template.process(data, writer);
        }
    }

    private void writeSearchIndex(Path outputPath, List<Map<String, Object>> searchEntries) throws IOException {
        Files.createDirectories(Objects.requireNonNull(outputPath.getParent()));
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), searchEntries);
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

    private static Map<String, Object> createArtifactCard(ArtifactMetadata artifact, String parserName, String detailUrl) {
        Map<String, Object> card = new HashMap<>();
        card.put("name", displayName(artifact));
        card.put("version", safe(artifact.getVersion()));
        card.put("description", safe(artifact.getDescription()));
        card.put("tags", normalizeTags(artifact.getTags()));
        card.put("url", detailUrl);
        card.put("parserType", parserName);
        card.put("groupId", safe(artifact.getGroupId()));
        card.put("artifactId", safe(artifact.getArtifactId()));
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record ParserGroup(
            String parserName,
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
