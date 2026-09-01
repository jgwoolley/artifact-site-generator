package com.nf3t.artifactsite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactParser;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.IArtifactParseContext;

class ParserIconCacheTest {

    @TempDir
    Path tempDir;

    /**
     * A plugin JAR built against an older {@code artifact-site-plugins-api} (before
     * {@code iconResourceName()} was added) is binary-incompatible with the currently loaded
     * API once it's reinstalled without rebuilding: the JVM throws {@link AbstractMethodError}
     * from the missing method. That single incompatible plugin must not abort caching icons for
     * the other, compatible plugins, nor propagate out of {@link ParserIconCache#refresh}.
     */
    @Test
    void incompatiblePluginDoesNotAbortIconCachingForOtherPlugins() throws Exception {
        ArtifactParserPlugin incompatiblePlugin = pluginWithParser("broken", new ArtifactParser() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public String displayName() {
                return "Broken";
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
                throw new AbstractMethodError(
                        "Method com/example/BrokenParser.iconResourceName()Ljava/lang/String; is abstract");
            }
        });

        ArtifactParserPlugin healthyPlugin = pluginWithParser("healthy", new ArtifactParser() {
            @Override
            public String id() {
                return "healthy";
            }

            @Override
            public String displayName() {
                return "Healthy";
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

            @Override
            public InputStream openIconStream() {
                return new ByteArrayInputStream("<svg></svg>".getBytes());
            }
        });

        ParserIconCache.refresh(
                List.of(incompatiblePlugin, healthyPlugin), tempDir, LoggerFactory.getLogger(ParserIconCacheTest.class));

        assertThat(Files.exists(tempDir.resolve("broken.svg"))).isFalse();
        assertThat(Files.readString(tempDir.resolve("healthy.svg"))).isEqualTo("<svg></svg>");
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
