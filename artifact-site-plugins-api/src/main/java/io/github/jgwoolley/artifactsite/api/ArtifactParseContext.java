package io.github.jgwoolley.artifactsite.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class ArtifactParseContext {
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

    public String probeContentType(Path file) throws IOException {
        return Files.probeContentType(file);
    }

    public Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }
}
