package com.nf3t.artifactsite.cli;

import java.nio.file.Path;

/**
 * Resolves XDG-based application filesystem locations.
 */
public final class XdgPaths {
    private XdgPaths() {
    }

    /**
     * Returns the plugin installation directory.
     *
     * @return plugin directory path
     */
    public static Path pluginDir() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "plugins");
    }

        /**
     * Returns the plugin installation directory.
     *
     * @return plugin directory path
     */
    public static Path artifactJsonPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "artifacts.json");
    }

    public static Path remoteCacheDir() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "remote-cache");
    }

    public static Path remoteRequestConfigPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "remote-requests.json");
    }
}
