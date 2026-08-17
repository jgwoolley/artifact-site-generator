package com.nf3t.artifactsite.cli;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Result of downloading a remote artifact to local cache.
 */
public record RemoteDownloadResult(
        Path localPath,
        @Nullable String fileName,
        @Nullable String contentType) {
}
