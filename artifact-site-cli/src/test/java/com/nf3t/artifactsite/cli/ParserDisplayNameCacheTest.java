package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.IArtifactParseContext;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class ParserDisplayNameCacheTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * A plugin JAR built against an older {@code artifact-site-plugins-api} (before
     * {@code displayName()} was added to {@code ArtifactParser}) is binary-incompatible with the
     * currently loaded API once it's reinstalled without rebuilding, throwing
     * {@link AbstractMethodError} from the missing method. That single incompatible plugin must
     * not abort caching display names for the other, compatible plugins, nor propagate out of
     * {@link ParserDisplayNameCache#refresh}.
     */
    @Test
    void incompatiblePluginDoesNotAbortCachingForOtherPlugins() throws Exception {
        ArtifactParserPlugin brokenPlugin = pluginWithParser("broken-plugin", new ArtifactParser() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public String displayName() {
                throw new AbstractMethodError(
                        "Method com/example/BrokenParser.displayName()Ljava/lang/String; is abstract");
            }

            @Override
            public boolean supports(ArtifactInputDescriptor descriptor) {
                return false;
            }

            @Override
            public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) {
            }

            @Override
            public String iconResourceName() {
                return "icon.svg";
            }
        });

        ArtifactParserPlugin healthyPlugin = pluginWithParser("healthy-plugin", simpleParser("healthy", "Healthy Parser"));

        Path cachePath = tempDir.resolve("parser-display-names.json");
        ParserDisplayNameCache.refresh(
                List.of(brokenPlugin, healthyPlugin), cachePath, LoggerFactory.getLogger(ParserDisplayNameCacheTest.class));

        Map<String, String> cached = OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, String>>() {
        });
        assertThat(cached).doesNotContainKey("broken");
        assertThat(cached).containsEntry("healthy", "Healthy Parser");
    }

    @Test
    void keepsPreviouslyCachedNameForParserNotInCurrentRun() throws Exception {
        Path cachePath = tempDir.resolve("parser-display-names.json");
        ArtifactParserPlugin maven = pluginWithParser("maven-plugin", simpleParser("maven", "Maven"));
        ParserDisplayNameCache.refresh(
                List.of(maven), cachePath, LoggerFactory.getLogger(ParserDisplayNameCacheTest.class));

        ArtifactParserPlugin vsix = pluginWithParser("vsix-plugin", simpleParser("vsix", "VS Code Extension"));
        ParserDisplayNameCache.refresh(
                List.of(vsix), cachePath, LoggerFactory.getLogger(ParserDisplayNameCacheTest.class));

        Map<String, String> cached = OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, String>>() {
        });
        assertThat(cached).containsEntry("maven", "Maven");
        assertThat(cached).containsEntry("vsix", "VS Code Extension");
    }

    private static ArtifactParser simpleParser(String id, String displayName) {
        return new ArtifactParser() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public boolean supports(ArtifactInputDescriptor descriptor) {
                return false;
            }

            @Override
            public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) {
            }

            @Override
            public String iconResourceName() {
                return "icon.svg";
            }
        };
    }

    private static ArtifactParserPlugin pluginWithParser(String pluginId, ArtifactParser parser) {
        return new ArtifactParserPlugin() {
            @Override
            public String pluginId() {
                return pluginId;
            }

            @Override
            public ArtifactParser parser() {
                return parser;
            }
        };
    }
}
