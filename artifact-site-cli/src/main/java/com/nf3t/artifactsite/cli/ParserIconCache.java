package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.PathUtils;

/**
 * Persists each parser's default icon to a stable cache directory so the static site can be
 * generated without the plugins being loaded at generation time.
 */
public final class ParserIconCache {

    private ParserIconCache() {
    }

    /**
     * Copies each parser's declared default icon into {@code iconsDir}, named after the
     * parser's own id (e.g. {@code maven.svg}), per {@link ArtifactParser#id()} - not the
     * wrapping {@link ArtifactParserPlugin#pluginId()}. Icons are read via the parser's own
     * classloader, per {@link ArtifactParser#openIconStream()}.
     *
     * <p>A single plugin's icon can never abort the whole {@code parse} run: a plugin JAR built
     * against an older {@code artifact-site-plugins-api} can be binary-incompatible with the
     * currently loaded API (e.g. missing a newly added interface method, surfacing as an
     * {@link AbstractMethodError}), so failures are isolated per plugin and merely logged.
     *
     * @param plugins loaded parser plugins
     * @param iconsDir cache directory to write icons into
     * @param logger logger used to report unreadable, missing, or incompatible icons
     */
    public static void refresh(List<ArtifactParserPlugin> plugins, Path iconsDir, Logger logger) {
        for (ArtifactParserPlugin plugin : plugins) {
            try {
                refreshOne(plugin.parser(), iconsDir, logger);
            } catch (Throwable e) {
                logger.warn("Could not load icon for parser plugin '" + plugin.pluginId()
                        + "' (it may need to be rebuilt against the current plugin API)", e);
            }
        }
    }

    private static void refreshOne(ArtifactParser parser, Path iconsDir, Logger logger) throws IOException {
        String parserId = parser.id();
        String resourceName = parser.iconResourceName();
        if (resourceName == null || resourceName.isBlank()) {
            logger.warn("Parser '{}' does not declare an icon resource name.", parserId);
            return;
        }

        try (InputStream iconStream = parser.openIconStream()) {
            if (iconStream == null) {
                logger.warn(
                        "Parser '{}' declares icon resource '{}' but it could not be found.",
                        parserId,
                        resourceName);
                return;
            }

            String extension = PathUtils.getExtension(Path.of(resourceName));
            String fileName = parserId + (extension == null || extension.isBlank() ? "" : "." + extension);
            Files.createDirectories(iconsDir);
            Files.copy(iconStream, iconsDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
