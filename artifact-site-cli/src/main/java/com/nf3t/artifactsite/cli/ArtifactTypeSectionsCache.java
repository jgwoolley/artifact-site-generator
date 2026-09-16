package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.SectionSpec;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists every loaded {@link ArtifactSectionProvider}'s declared detail page sections to a
 * stable cache file, keyed by artifact type (see {@code ArtifactFields#ARTIFACT_TYPES}) rather
 * than by parser or plugin id - a single provider can declare sections for several artifact types
 * at once - so the static site can be generated without any plugins being loaded at generation
 * time.
 */
public final class ArtifactTypeSectionsCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ArtifactTypeSectionsCache() {
    }

    /**
     * Writes every currently loaded provider's declared sections into {@code cachePath} as a JSON
     * object keyed by artifact type. For each artifact type a loaded provider mentions, that
     * type's cached entry is replaced outright, or removed when the provider now declares an
     * empty list for it (a deliberate opt-out) - an artifact type no currently loaded provider
     * mentions at all keeps whatever was cached before. Two providers loaded in the same run that
     * both declare the same artifact type is logged as a warning (the last one applied wins),
     * since that's otherwise silently ambiguous.
     *
     * <p>A single plugin can never abort the whole {@code parse} run: a plugin JAR built against
     * an older {@code artifact-site-plugins-api} can be binary-incompatible with the currently
     * loaded API (e.g. missing a newly added interface method, surfacing as an {@link
     * AbstractMethodError}), so failures are isolated per provider and merely logged.
     *
     * @param providers loaded section providers
     * @param cachePath JSON file mapping artifact type to declared sections
     * @param logger logger used to report incompatible providers and type collisions
     */
    public static void refresh(List<ArtifactSectionProvider> providers, Path cachePath, Logger logger) {
        Map<String, List<SectionSpec>> sections = readExisting(cachePath, logger);
        Set<String> touchedThisRun = new HashSet<>();

        for (ArtifactSectionProvider provider : providers) {
            try {
                Map<String, List<SectionSpec>> declared = provider.sections();
                if (declared == null) {
                    continue;
                }
                for (Map.Entry<String, List<SectionSpec>> entry : declared.entrySet()) {
                    String artifactType = entry.getKey();
                    List<SectionSpec> declaredSections = entry.getValue();
                    if (!touchedThisRun.add(artifactType)) {
                        logger.warn(
                                "More than one loaded ArtifactSectionProvider declares sections for artifact "
                                        + "type '{}' in this run; the last one applied wins.",
                                artifactType);
                    }
                    if (declaredSections == null || declaredSections.isEmpty()) {
                        sections.remove(artifactType);
                    } else {
                        sections.put(artifactType, declaredSections);
                    }
                }
            } catch (Throwable e) {
                logger.warn("Could not read declared sections from an ArtifactSectionProvider "
                        + "(it may need to be rebuilt against the current plugin API)", e);
            }
        }

        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            OBJECT_MAPPER.writeValue(cachePath, sections);
        } catch (IOException e) {
            logger.warn("Failed to write artifact type sections cache to " + cachePath, e);
        }
    }

    private static Map<String, List<SectionSpec>> readExisting(Path cachePath, Logger logger) {
        if (!Files.isRegularFile(cachePath)) {
            return new LinkedHashMap<>();
        }
        try (InputStream input = Files.newInputStream(cachePath)) {
            Map<String, List<SectionSpec>> existing =
                    OBJECT_MAPPER.readValue(input, new TypeReference<Map<String, List<SectionSpec>>>() {
                    });
            return existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        } catch (IOException e) {
            logger.warn("Failed to read existing artifact type sections cache at " + cachePath, e);
            return new LinkedHashMap<>();
        }
    }
}
