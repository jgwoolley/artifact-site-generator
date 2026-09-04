package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists each parser's declared SEO keywords (see {@link ArtifactParser#seoTags()}) to a
 * stable cache file, keyed by the parser's own id (see {@link ArtifactParser#id()}, not the
 * wrapping {@link ArtifactParserPlugin#pluginId()}), so the static site can be generated without
 * the plugins being loaded at generation time.
 */
public final class ParserSeoTagsCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParserSeoTagsCache() {
    }

    /**
     * Writes each parser's declared {@link ArtifactParser#seoTags()} into {@code cachePath} as a
     * JSON object keyed by {@link ArtifactParser#id()}. A parser that declares no SEO tags has any
     * previously cached entry removed, since that's a deliberate choice by the currently loaded
     * parser; a parser that isn't loaded this run keeps whatever was cached before.
     *
     * <p>A single plugin can never abort the whole {@code parse} run: a plugin JAR built against
     * an older {@code artifact-site-plugins-api} can be binary-incompatible with the currently
     * loaded API (e.g. missing a newly added interface method, surfacing as an
     * {@link AbstractMethodError}), so failures are isolated per plugin and merely logged.
     *
     * @param plugins loaded parser plugins
     * @param cachePath JSON file mapping parser id to SEO keywords
     * @param logger logger used to report incompatible parsers
     */
    public static void refresh(List<ArtifactParserPlugin> plugins, Path cachePath, Logger logger) {
        Map<String, List<String>> seoTags = readExisting(cachePath, logger);

        for (ArtifactParserPlugin plugin : plugins) {
            try {
                ArtifactParser parser = plugin.parser();
                String parserId = parser.id();
                List<String> tags = parser.seoTags();
                if (tags == null || tags.isEmpty()) {
                    seoTags.remove(parserId);
                    continue;
                }
                seoTags.put(parserId, tags);
            } catch (Throwable e) {
                logger.warn("Could not read SEO tags for parser plugin '" + plugin.pluginId()
                        + "' (it may need to be rebuilt against the current plugin API)", e);
            }
        }

        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            OBJECT_MAPPER.writeValue(cachePath, seoTags);
        } catch (IOException e) {
            logger.warn("Failed to write parser SEO tags cache to " + cachePath, e);
        }
    }

    private static Map<String, List<String>> readExisting(Path cachePath, Logger logger) {
        if (!Files.isRegularFile(cachePath)) {
            return new LinkedHashMap<>();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, List<String>> existing =
                    OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, List<String>>>() {
                    });
            return existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        } catch (IOException e) {
            logger.warn("Failed to read existing parser SEO tags cache at " + cachePath, e);
            return new LinkedHashMap<>();
        }
    }
}
