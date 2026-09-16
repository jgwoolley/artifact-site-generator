package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactSectionProvider;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists every loaded {@link ArtifactSectionProvider}'s declared artifact type display names to
 * a stable cache file, keyed by artifact type, so the static site can be generated without any
 * plugins being loaded at generation time.
 */
public final class ArtifactTypeDisplayNameCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ArtifactTypeDisplayNameCache() {
    }

    /**
     * Writes every currently loaded provider's declared {@link ArtifactSectionProvider#displayNames()}
     * into {@code cachePath} as a JSON object keyed by artifact type, merged with whatever names
     * are already cached there (so a type whose provider isn't currently loaded keeps its
     * last-known display name). A blank declared name is skipped with a warning rather than
     * cached.
     *
     * <p>A single plugin can never abort the whole {@code parse} run: a plugin JAR built against
     * an older {@code artifact-site-plugins-api} can be binary-incompatible with the currently
     * loaded API (e.g. missing a newly added interface method, surfacing as an {@link
     * AbstractMethodError}), so failures are isolated per provider and merely logged.
     *
     * @param providers loaded section providers
     * @param cachePath JSON file mapping artifact type to display name
     * @param logger logger used to report incompatible providers and blank display names
     */
    public static void refresh(List<ArtifactSectionProvider> providers, Path cachePath, Logger logger) {
        Map<String, String> displayNames = readExisting(cachePath, logger);

        for (ArtifactSectionProvider provider : providers) {
            try {
                Map<String, String> declared = provider.displayNames();
                if (declared == null) {
                    continue;
                }
                for (Map.Entry<String, String> entry : declared.entrySet()) {
                    String artifactType = entry.getKey();
                    String displayName = entry.getValue();
                    if (displayName == null || displayName.isBlank()) {
                        logger.warn("Artifact type '{}' has a blank declared display name; ignoring it.", artifactType);
                        continue;
                    }
                    displayNames.put(artifactType, displayName);
                }
            } catch (Throwable e) {
                logger.warn("Could not read declared display names from an ArtifactSectionProvider "
                        + "(it may need to be rebuilt against the current plugin API)", e);
            }
        }

        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            OBJECT_MAPPER.writeValue(cachePath, displayNames);
        } catch (IOException e) {
            logger.warn("Failed to write artifact type display name cache to " + cachePath, e);
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
            logger.warn("Failed to read existing artifact type display name cache at " + cachePath, e);
            return new LinkedHashMap<>();
        }
    }
}
