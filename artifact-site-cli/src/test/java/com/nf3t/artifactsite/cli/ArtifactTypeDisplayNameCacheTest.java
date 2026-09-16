package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.SectionSpec;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class ArtifactTypeDisplayNameCacheTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * A provider JAR built against an older {@code artifact-site-plugins-api} (before this
     * extension point existed, or before a method was added to it) is binary-incompatible with
     * the currently loaded API once reinstalled without rebuilding, throwing {@link
     * AbstractMethodError}. That single incompatible provider must not abort caching for other,
     * compatible providers, nor propagate out of {@link ArtifactTypeDisplayNameCache#refresh}.
     */
    @Test
    void incompatibleProviderDoesNotAbortCachingForOtherProviders() throws Exception {
        ArtifactSectionProvider broken = new ArtifactSectionProvider() {
            @Override
            public Map<String, List<SectionSpec>> sections() {
                return Map.of();
            }

            @Override
            public Map<String, String> displayNames() {
                throw new AbstractMethodError("Method com/example/BrokenProvider.displayNames()Ljava/util/Map; is abstract");
            }
        };
        ArtifactSectionProvider healthy = () -> Map.of();
        ArtifactSectionProvider healthyWithNames = new ArtifactSectionProvider() {
            @Override
            public Map<String, List<SectionSpec>> sections() {
                return Map.of();
            }

            @Override
            public Map<String, String> displayNames() {
                return Map.of("jar", "Java");
            }
        };

        Path cachePath = tempDir.resolve("artifact-type-display-names.json");
        ArtifactTypeDisplayNameCache.refresh(
                List.of(broken, healthy, healthyWithNames), cachePath,
                LoggerFactory.getLogger(ArtifactTypeDisplayNameCacheTest.class));

        Map<String, String> cached = OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, String>>() {
        });
        assertThat(cached).containsEntry("jar", "Java");
    }

    @Test
    void keepsPreviouslyCachedNameForTypeNotTouchedThisRun() throws Exception {
        Path cachePath = tempDir.resolve("artifact-type-display-names.json");
        ArtifactSectionProvider jarProvider = displayNameProvider(Map.of("jar", "Java"));
        ArtifactTypeDisplayNameCache.refresh(
                List.of(jarProvider), cachePath, LoggerFactory.getLogger(ArtifactTypeDisplayNameCacheTest.class));

        ArtifactSectionProvider vsixProvider = displayNameProvider(Map.of("vsix", "VS Code Extension"));
        ArtifactTypeDisplayNameCache.refresh(
                List.of(vsixProvider), cachePath, LoggerFactory.getLogger(ArtifactTypeDisplayNameCacheTest.class));

        Map<String, String> cached = OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, String>>() {
        });
        assertThat(cached).containsEntry("jar", "Java");
        assertThat(cached).containsEntry("vsix", "VS Code Extension");
    }

    @Test
    void blankDeclaredNameIsIgnoredRatherThanCached() throws Exception {
        Path cachePath = tempDir.resolve("artifact-type-display-names.json");
        ArtifactSectionProvider blankName = displayNameProvider(Map.of("jar", " "));
        ArtifactTypeDisplayNameCache.refresh(
                List.of(blankName), cachePath, LoggerFactory.getLogger(ArtifactTypeDisplayNameCacheTest.class));

        Map<String, String> cached = OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, String>>() {
        });
        assertThat(cached).doesNotContainKey("jar");
    }

    private static ArtifactSectionProvider displayNameProvider(Map<String, String> displayNames) {
        return new ArtifactSectionProvider() {
            @Override
            public Map<String, List<SectionSpec>> sections() {
                return Map.of();
            }

            @Override
            public Map<String, String> displayNames() {
                return displayNames;
            }
        };
    }
}
