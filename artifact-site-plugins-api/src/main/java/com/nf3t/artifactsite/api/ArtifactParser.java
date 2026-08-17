package com.nf3t.artifactsite.api;

import java.io.InputStream;

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
    boolean supports(@Nullable ArtifactInputDescriptor descriptor);

    /**
     * Parses a supported input and returns normalized metadata.
     *
     * @param descriptor input descriptor to parse
     * @param input artifact input stream
     * @param context parse helpers and utilities
     * @return parsed artifact metadata
     * @throws Exception when parsing fails
     */
    ArtifactMetadata parse(ArtifactInputDescriptor descriptor, InputStream input, ArtifactParseContext context) throws Exception;
}
