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

    /**
     * Returns the directory where parser plugin default icons are cached.
     *
     * @return icons cache directory path
     */
    public static Path iconsDir() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "icons");
    }

    /**
     * Returns the path of the cached JSON map of artifact type to UI display name.
     *
     * @return artifact type display name cache file path
     */
    public static Path artifactTypeDisplayNamesPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "artifact-type-display-names.json");
    }

    /**
     * Returns the path of the cached JSON map of parser id to install guide HTML template.
     *
     * @return parser install guide cache file path
     */
    public static Path parserInstallGuidesPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "parser-install-guides.json");
    }

    /**
     * Returns the path of the cached JSON map of parser id to SEO keywords.
     *
     * @return parser SEO tags cache file path
     */
    public static Path parserSeoTagsPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "parser-seo-tags.json");
    }

    /**
     * Returns the path of the cached JSON map of artifact type to declared detail page sections.
     *
     * @return artifact type sections cache file path
     */
    public static Path artifactTypeSectionsPath() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "artifact-type-sections.json");
    }
}
