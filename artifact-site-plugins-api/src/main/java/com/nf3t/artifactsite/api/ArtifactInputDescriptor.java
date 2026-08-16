package com.nf3t.artifactsite.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static ArtifactInputDescriptor parseLocal(Path path) {
        String extension = PathUtils.getExtension(path);
        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(path);
        } catch (IOException ignored) {

        }
        return new ArtifactInputDescriptor(ArtifactSourceType.LOCAL, path.toString(), path.getFileName().toString(), extension, contentType);
    }
}
