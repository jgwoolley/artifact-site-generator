package com.nf3t.artifactsite.api;

import java.util.List;
import java.util.Map;

import org.pf4j.ExtensionPoint;

/**
 * PF4J extension point declaring which detail page sections render for which artifact types (see
 * {@link ArtifactFields#ARTIFACT_TYPES}). Independent of {@link ArtifactParserPlugin}/{@link
 * ArtifactParser} - a plugin that produces fields for one or more artifact types registers a
 * separate {@code ArtifactSectionProvider} extension declaring how those types render, rather than
 * shipping executable page templates.
 *
 * <p>An artifact whose {@link ArtifactFields#ARTIFACT_TYPES} carries more than one type (e.g. a
 * NiFi Archive is both {@code "jar"} and {@code "nar"}) renders one section block per type, in the
 * order the types are listed on the artifact, each type's sections in the order declared here.
 */
public interface ArtifactSectionProvider extends ExtensionPoint {
    /**
     * Declares the detail page sections for each artifact type this provider is responsible for.
     * Called at {@code parse} time and cached to disk keyed by artifact type, so {@code generate}
     * can render sections without this plugin (or any plugin) being loaded - returning an empty
     * list for a previously-declared type is a deliberate opt-out, clearing that type's cached
     * entry, while a type this provider doesn't mention at all is left untouched in the cache.
     *
     * @return artifact type -> ordered list of sections to render for it
     */
    Map<String, List<SectionSpec>> sections();

    /**
     * Declares the UI-facing display name for each artifact type this provider is responsible for
     * (e.g. {@code "jar" -> "Java"}), shown on that type's browsing page (see
     * {@code ArtifactFields#ARTIFACT_TYPES}), the home page's "Artifact Types" section, and as the
     * single badge/label on an artifact's own card and detail page. Called at {@code parse} time
     * and cached to disk keyed by artifact type, so {@code generate} can render labels without any
     * plugin being loaded. A type this provider doesn't mention is left untouched in the cache;
     * declaring a blank name for a type is not a valid opt-out (unlike {@link #sections()}) and is
     * simply ignored with a warning.
     *
     * @return artifact type -> display name
     */
    default Map<String, String> displayNames() {
        return Map.of();
    }
}
