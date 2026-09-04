package ${package};

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ${parserClassName}ArtifactParserTest {

    private final ${parserClassName}ArtifactParser parser = new ${parserClassName}ArtifactParser();

    @Test
    void hasStableParserId() {
        assertThat(parser.id()).isEqualTo("${parserId}");
    }

    @Test
    void hasDisplayName() {
        assertThat(parser.displayName()).isEqualTo("${parserDisplayName}");
    }

    // TODO: add supports()/parse() coverage once file matching and metadata extraction are
    // implemented, following artifact-site-plugin-maven's MavenArtifactParserTest as a model.
}
