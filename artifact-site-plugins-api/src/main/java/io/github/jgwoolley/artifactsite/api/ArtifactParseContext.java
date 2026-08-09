package io.github.jgwoolley.artifactsite.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.jspecify.annotations.Nullable;

/**
 * Shared parsing utilities for parser plugins.
 */
public class ArtifactParseContext {
    /**
     * Computes the SHA-256 digest for a file.
     *
     * @param file file to hash
     * @return lowercase hex SHA-256 digest
     * @throws Exception when hashing fails
     */
    public String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Probes the file content type.
     *
     * @param file file to inspect
     * @return content type, or {@code null} when unknown
     * @throws IOException when probing fails
     */
    public @Nullable String probeContentType(Path file) throws IOException {
        return Files.probeContentType(file);
    }

    /**
     * Creates a temporary working directory.
     *
     * @param prefix directory name prefix
     * @return created directory path
     * @throws IOException when creation fails
     */
    public Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }
}
