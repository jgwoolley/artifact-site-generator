package com.nf3t.artifactsite.api;

import org.jspecify.annotations.Nullable;

/**
 * Declares one renderable section of an artifact detail page: an optional heading, an
 * {@link ElementType} saying how to render it, and the {@code Datum} field it reads from.
 *
 * <p>{@code namespace}/{@code key} (rather than a {@link DatumField} reference) are what's actually
 * cached and interpreted at {@code generate} time, so a declared section survives without the
 * declaring plugin's classes being loaded - the same reason {@link
 * ArtifactParser#installGuideResourceName()} resolves to a plain cached string. The {@link
 * DatumField}-accepting constructor keeps authoring type-safe for plugin code.
 *
 * @param title section heading shown above the rendered content, or {@code null}/blank for none
 * @param element how this section's field value is rendered
 * @param namespace the field's {@link DatumField#getNamespace()}
 * @param key the field's {@link DatumField#getKey()}
 */
public record SectionSpec(@Nullable String title, ElementType element, String namespace, String key) {

    /**
     * Convenience constructor resolving a {@link DatumField} to its plain namespace/key at
     * declaration time.
     *
     * @param title section heading shown above the rendered content, or {@code null}/blank for none
     * @param element how this section's field value is rendered
     * @param field the field this section reads from
     */
    public SectionSpec(@Nullable String title, ElementType element, DatumField field) {
        this(title, element, field.getNamespace(), field.getKey());
    }
}
