package com.nf3t.artifactsite.api;

import java.nio.file.Path;

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
}