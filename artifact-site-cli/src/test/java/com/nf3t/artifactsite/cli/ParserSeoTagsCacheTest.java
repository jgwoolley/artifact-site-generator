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

class ParserSeoTagsCacheTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * A plugin JAR built against an older {@code artifact-site-plugins-api} (before
     * {@code seoTags()} was added to {@code ArtifactParser}) is binary-incompatible with the
     * currently loaded API once it's reinstalled without rebuilding, throwing
     * {@link AbstractMethodError} from the missing method. That single incompatible plugin must
     * not abort caching SEO tags for the other, compatible plugins, nor propagate out of
     * {@link ParserSeoTagsCache#refresh}.
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
                return "icon.svg";
            }

            @Override
            public List<String> seoTags() {
                throw new AbstractMethodError(
                        "Method com/example/BrokenParser.seoTags()Ljava/util/List; is abstract");
            }
        });

        ArtifactParserPlugin healthyPlugin = pluginWithParser("healthy-plugin", simpleParser("healthy", List.of("java", "jar")));

        Path cachePath = tempDir.resolve("parser-seo-tags.json");
        ParserSeoTagsCache.refresh(
                List.of(brokenPlugin, healthyPlugin), cachePath, LoggerFactory.getLogger(ParserSeoTagsCacheTest.class));

        Map<String, List<String>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<String>>>() {
                });
        assertThat(cached).doesNotContainKey("broken");
        assertThat(cached).containsEntry("healthy", List.of("java", "jar"));
    }

    @Test
    void keepsPreviouslyCachedTagsForParserNotInCurrentRun() throws Exception {
        Path cachePath = tempDir.resolve("parser-seo-tags.json");
        ArtifactParserPlugin maven = pluginWithParser("maven-plugin", simpleParser("maven", List.of("maven", "java")));
        ParserSeoTagsCache.refresh(List.of(maven), cachePath, LoggerFactory.getLogger(ParserSeoTagsCacheTest.class));

        ArtifactParserPlugin vsix = pluginWithParser("vsix-plugin", simpleParser("vsix", List.of("vscode", "extension")));
        ParserSeoTagsCache.refresh(List.of(vsix), cachePath, LoggerFactory.getLogger(ParserSeoTagsCacheTest.class));

        Map<String, List<String>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<String>>>() {
                });
        assertThat(cached).containsEntry("maven", List.of("maven", "java"));
        assertThat(cached).containsEntry("vsix", List.of("vscode", "extension"));
    }

    /**
     * A parser declaring no SEO tags (the {@link ArtifactParser#seoTags()} default) is a deliberate
     * opt-out, so any previously cached entry for it must be removed rather than kept stale.
     */
    @Test
    void parserDeclaringNoSeoTagsHasCachedEntryRemoved() throws Exception {
        Path cachePath = tempDir.resolve("parser-seo-tags.json");
        ArtifactParserPlugin taggedPlugin = pluginWithParser("tagged-plugin", simpleParser("format", List.of("old", "tags")));
        ParserSeoTagsCache.refresh(List.of(taggedPlugin), cachePath, LoggerFactory.getLogger(ParserSeoTagsCacheTest.class));

        ArtifactParserPlugin untaggedPlugin = pluginWithParser("tagged-plugin", simpleParser("format", List.of()));
        ParserSeoTagsCache.refresh(List.of(untaggedPlugin), cachePath, LoggerFactory.getLogger(ParserSeoTagsCacheTest.class));

        Map<String, List<String>> cached =
                OBJECT_MAPPER.readValue(cachePath.toFile(), new TypeReference<Map<String, List<String>>>() {
                });
        assertThat(cached).doesNotContainKey("format");
    }

    private static ArtifactParser simpleParser(String id, List<String> seoTags) {
        return new ArtifactParser() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
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
            public List<String> seoTags() {
                return seoTags;
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
