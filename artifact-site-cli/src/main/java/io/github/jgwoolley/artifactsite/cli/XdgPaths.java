package io.github.jgwoolley.artifactsite.cli;

import java.nio.file.Path;

final class XdgPaths {
    private XdgPaths() {
    }

    static Path pluginDir() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome == null || dataHome.isBlank()) {
            dataHome = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        }
        return Path.of(dataHome, "artifact-site-generator", "plugins");
    }
}
