package com.nf3t.artifactsite.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.jspecify.annotations.Nullable;

public class PathUtils {

    public static String getExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }

        String fileName = path.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');

        // Handles files without extension, hidden files like ".gitignore", or trailing dots ("file.")
        if (lastDotIndex <= 0 || lastDotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(lastDotIndex + 1);
    }
    
    /**
     * Computes the SHA-256 digest for a file.
     *
     * @param file file to hash
     * @return lowercase hex SHA-256 digest
     * @throws Exception when hashing fails
     */
    public static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }

        return toHex(digest.digest());
    }

    /**
     * Computes the SHA-256 digest for bytes.
     *
     * @param content content to hash
     * @return lowercase hex SHA-256 digest
     * @throws Exception when hashing fails
     */
    public static String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(content);
        return toHex(digest.digest());
    }

    /**
     * Probes the file content type.
     *
     * @param file file to inspect
     * @return content type, or {@code null} when unknown
     * @throws IOException when probing fails
     */
    public static @Nullable String probeContentType(Path file) throws IOException {
        return Files.probeContentType(file);
    }

    /**
     * Creates a temporary working directory.
     *
     * @param prefix directory name prefix
     * @return created directory path
     * @throws IOException when creation fails
     */
    public static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}