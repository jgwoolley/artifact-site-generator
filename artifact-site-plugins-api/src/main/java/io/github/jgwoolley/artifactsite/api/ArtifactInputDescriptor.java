package io.github.jgwoolley.artifactsite.api;

public record ArtifactInputDescriptor(
        ArtifactSourceType sourceType,
        String sourceValue,
        String fileName,
        String extension,
        String contentType) {
}
