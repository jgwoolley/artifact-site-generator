package io.github.jgwoolley.artifactsite.api;

import org.jspecify.annotations.Nullable;

/**
 * Describes a parser input and resolved file characteristics.
 *
 * @param sourceType source type of the artifact input
 * @param sourceValue source value (local path or remote URL)
 * @param fileName resolved file name, if known
 * @param extension normalized file extension, if known
 * @param contentType resolved content type, if known
 */
public record ArtifactInputDescriptor(
        ArtifactSourceType sourceType,
        String sourceValue,
        @Nullable String fileName,
        @Nullable String extension,
        @Nullable String contentType) {
}
