package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactSectionProvider;
import com.nf3t.artifactsite.api.ElementType;
import com.nf3t.artifactsite.api.SectionSpec;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class ArtifactTypeSectionsCacheTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * A provider JAR built against an older {@code artifact-site-plugins-api} (before this
     * extension point existed, or before a method was added to it) is binary-incompatible with
     * the currently loaded API once reinstalled without rebuilding, throwing {@link
     * AbstractMethodError}. That single incompatible provider must not abort caching for other,
     * compatible providers, nor propagate out of {@link ArtifactTypeSectionsCache#refresh}.
     */
    @Test
    void incompatibleProviderDoesNotAbortCachingForOtherProviders() throws Exception {
        ArtifactSectionProvider broken = () -> {
            throw new AbstractMethodError("Method com/example/BrokenProvider.sections()Ljava/util/Map; is abstract");
        };
        ArtifactSectionProvider healthy = () -> Map.of(
                "jar", List.of(new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README)));

        Path cachePath = tempDir.resolve("artifact-type-sections.json");
        ArtifactTypeSectionsCache.refresh(
                List.of(broken, healthy), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        Map<String, List<SectionSpec>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<SectionSpec>>>() {
                });
        assertThat(cached).containsKey("jar");
    }

    @Test
    void keepsPreviouslyCachedSectionsForArtifactTypeNotTouchedThisRun() throws Exception {
        Path cachePath = tempDir.resolve("artifact-type-sections.json");
        ArtifactSectionProvider jarProvider = () -> Map.of(
                "jar", List.of(new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README)));
        ArtifactTypeSectionsCache.refresh(
                List.of(jarProvider), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        ArtifactSectionProvider vsixProvider = () -> Map.of(
                "vsix", List.of(new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.DESCRIPTION)));
        ArtifactTypeSectionsCache.refresh(
                List.of(vsixProvider), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        Map<String, List<SectionSpec>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<SectionSpec>>>() {
                });
        assertThat(cached).containsKey("jar");
        assertThat(cached).containsKey("vsix");
    }

    /**
     * A provider declaring an empty section list for an artifact type it previously declared
     * sections for is a deliberate opt-out, so that type's cached entry must be removed rather
     * than kept stale.
     */
    @Test
    void providerDeclaringEmptySectionsHasCachedEntryRemoved() throws Exception {
        Path cachePath = tempDir.resolve("artifact-type-sections.json");
        ArtifactSectionProvider withSections = () -> Map.of(
                "jar", List.of(new SectionSpec(null, ElementType.MARKDOWN, ArtifactFields.README)));
        ArtifactTypeSectionsCache.refresh(
                List.of(withSections), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        ArtifactSectionProvider optedOut = () -> Map.of("jar", List.of());
        ArtifactTypeSectionsCache.refresh(
                List.of(optedOut), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        Map<String, List<SectionSpec>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<SectionSpec>>>() {
                });
        assertThat(cached).doesNotContainKey("jar");
    }

    @Test
    void lastProviderDeclaringASharedArtifactTypeWinsAndLogsAWarning() throws Exception {
        Path cachePath = tempDir.resolve("artifact-type-sections.json");
        ArtifactSectionProvider first = () -> Map.of(
                "jar", List.of(new SectionSpec("First", ElementType.MARKDOWN, ArtifactFields.README)));
        ArtifactSectionProvider second = () -> Map.of(
                "jar", List.of(new SectionSpec("Second", ElementType.MARKDOWN, ArtifactFields.DESCRIPTION)));

        ArtifactTypeSectionsCache.refresh(
                List.of(first, second), cachePath, LoggerFactory.getLogger(ArtifactTypeSectionsCacheTest.class));

        Map<String, List<SectionSpec>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<SectionSpec>>>() {
                });
        assertThat(cached.get("jar")).extracting(SectionSpec::title).containsExactly("Second");
    }
}
