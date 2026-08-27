package com.nf3t.artifactsite.api;

import org.jspecify.annotations.Nullable;

/**
 * Contract for artifact format parsers.
 */
public interface ArtifactParser {
    /**
     * Determines whether this parser can parse a given input.
     *
     * @param descriptor candidate input descriptor, may be {@code null}
     * @return {@code true} when this parser supports the descriptor
     */
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor);

    /**
     * Parses a supported input and returns normalized metadata.
     *
     * @param descriptor input descriptor to parse
     * @param context parse helpers and utilities
     * @throws Exception when parsing fails
     */
    public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception;
}
