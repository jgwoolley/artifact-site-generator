package io.github.jgwoolley.artifactsite.api;

public interface ArtifactParser {
    boolean supports(ArtifactInputDescriptor descriptor);

    ArtifactMetadata parse(ArtifactInputDescriptor descriptor, ArtifactParseContext context) throws Exception;
}
