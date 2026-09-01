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
 * Persists each parser's UI display name to a stable cache file, keyed by the parser's own id
 * (see {@link ArtifactParser#id()}, not the wrapping {@link ArtifactParserPlugin#pluginId()}),
 * so the static site can be generated without the plugins being loaded at generation time.
 */
public final class ParserDisplayNameCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParserDisplayNameCache() {
    }

    /**
     * Writes each parser's declared {@link ArtifactParser#displayName()} into {@code cachePath}
     * as a JSON object keyed by {@link ArtifactParser#id()}, merged with whatever names are
     * already cached there (so a plugin that isn't currently installed keeps its last-known
     * display name).
     *
     * <p>A single plugin can never abort the whole {@code parse} run: a plugin JAR built
     * against an older {@code artifact-site-plugins-api} can be binary-incompatible with the
     * currently loaded API (e.g. missing a newly added interface method, surfacing as an
     * {@link AbstractMethodError}), so failures are isolated per plugin and merely logged.
     *
     * @param plugins loaded parser plugins
     * @param cachePath JSON file mapping parser id to display name
     * @param logger logger used to report missing or incompatible display names
     */
    public static void refresh(List<ArtifactParserPlugin> plugins, Path cachePath, Logger logger) {
        Map<String, String> displayNames = readExisting(cachePath, logger);

        for (ArtifactParserPlugin plugin : plugins) {
            try {
                ArtifactParser parser = plugin.parser();
                String parserId = parser.id();
                String displayName = parser.displayName();
                if (displayName == null || displayName.isBlank()) {
                    logger.warn("Parser '{}' does not declare a display name.", parserId);
                    continue;
                }
                displayNames.put(parserId, displayName);
            } catch (Throwable e) {
                logger.warn("Could not read display name for parser plugin '" + plugin.pluginId()
                        + "' (it may need to be rebuilt against the current plugin API)", e);
            }
        }

        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            OBJECT_MAPPER.writeValue(cachePath, displayNames);
        } catch (IOException e) {
            logger.warn("Failed to write parser display name cache to " + cachePath, e);
        }
    }

    private static Map<String, String> readExisting(Path cachePath, Logger logger) {
        if (!Files.isRegularFile(cachePath)) {
            return new LinkedHashMap<>();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, String> existing = OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, String>>() {
            });
            return existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        } catch (IOException e) {
            logger.warn("Failed to read existing parser display name cache at " + cachePath, e);
            return new LinkedHashMap<>();
        }
    }
}
