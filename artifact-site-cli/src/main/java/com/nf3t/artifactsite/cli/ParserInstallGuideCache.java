package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * Persists each parser's install guide HTML template to a stable cache file, keyed by the
 * parser's own id (see {@link ArtifactParser#id()}, not the wrapping
 * {@link ArtifactParserPlugin#pluginId()}), so the static site can be generated without the
 * plugins being loaded at generation time.
 */
public final class ParserInstallGuideCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParserInstallGuideCache() {
    }

    /**
     * Writes each parser's declared install guide (see {@link ArtifactParser#installGuideResourceName()}
     * and {@link ArtifactParser#openInstallGuideStream()}) into {@code cachePath} as a JSON
     * object keyed by {@link ArtifactParser#id()}. A parser that declares no install guide has
     * any previously cached entry removed, since that's a deliberate choice by the currently
     * loaded parser; a parser that isn't loaded this run keeps whatever was cached before.
     *
     * <p>A single plugin can never abort the whole {@code parse} run: a plugin JAR built
     * against an older {@code artifact-site-plugins-api} can be binary-incompatible with the
     * currently loaded API (e.g. missing a newly added interface method, surfacing as an
     * {@link AbstractMethodError}), so failures are isolated per plugin and merely logged.
     *
     * @param plugins loaded parser plugins
     * @param cachePath JSON file mapping parser id to install guide HTML template
     * @param logger logger used to report unreadable or incompatible install guides
     */
    public static void refresh(List<ArtifactParserPlugin> plugins, Path cachePath, Logger logger) {
        Map<String, String> installGuides = readExisting(cachePath, logger);

        for (ArtifactParserPlugin plugin : plugins) {
            try {
                ArtifactParser parser = plugin.parser();
                String parserId = parser.id();
                String resourceName = parser.installGuideResourceName();
                if (resourceName == null || resourceName.isBlank()) {
                    installGuides.remove(parserId);
                    continue;
                }

                try (InputStream installGuideStream = parser.openInstallGuideStream()) {
                    if (installGuideStream == null) {
                        logger.warn(
                                "Parser '{}' declares install guide resource '{}' but it could not be found.",
                                parserId,
                                resourceName);
                        continue;
                    }
                    installGuides.put(parserId, new String(installGuideStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Throwable e) {
                logger.warn("Could not read install guide for parser plugin '" + plugin.pluginId()
                        + "' (it may need to be rebuilt against the current plugin API)", e);
            }
        }

        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            OBJECT_MAPPER.writeValue(cachePath, installGuides);
        } catch (IOException e) {
            logger.warn("Failed to write parser install guide cache to " + cachePath, e);
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
            logger.warn("Failed to read existing parser install guide cache at " + cachePath, e);
            return new LinkedHashMap<>();
        }
    }
}
