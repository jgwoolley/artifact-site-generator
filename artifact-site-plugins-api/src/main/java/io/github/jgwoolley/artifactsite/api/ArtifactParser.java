package io.github.jgwoolley.artifactsite.api;

import org.jspecify.annotations.Nullable;
import org.pf4j.ExtensionPoint;

/**
 * Contract for artifact format parsers.
 */
public interface ArtifactParser extends ExtensionPoint {
    /**
     * Determines whether this parser can parse a given input.
     *
     * @param descriptor candidate input descriptor, may be {@code null}
     * @return {@code true} when this parser supports the descriptor
     */
    boolean supports(@Nullable ArtifactInputDescriptor descriptor);

    /**
     * Parses a supported input and returns normalized metadata.
     *
     * @param descriptor input descriptor to parse
     * @param context parse helpers and utilities
     * @return parsed artifact metadata
     * @throws Exception when parsing fails
     */
    ArtifactMetadata parse(ArtifactInputDescriptor descriptor, ArtifactParseContext context) throws Exception;
}
