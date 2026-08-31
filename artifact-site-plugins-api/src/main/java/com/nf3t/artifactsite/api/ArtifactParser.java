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
    public boolean supports(@Nullable ArtifactInputDescriptor descriptor);

    /**
     * Parses a supported input and returns normalized metadata.
     *
     * @param descriptor input descriptor to parse
     * @param context parse helpers and utilities
     * @throws Exception when parsing fails
     */
    public void parse(ArtifactInputDescriptor descriptor, IArtifactParseContext context) throws Exception;

    /**
     * Returns the resource name of this parser's default icon (e.g. {@code "icon.svg"}).
     * The resource is expected to live in the standard location alongside the parser
     * implementation class, i.e. as a classpath resource in the same package as
     * {@code getClass()}, so it can be resolved with {@link Class#getResourceAsStream(String)}.
     *
     * @return icon resource name, resolved relative to the parser implementation class
     */
    public String iconResourceName();

    /**
     * Opens this parser's default icon, loaded by a classloader rooted at the parser
     * implementation class.
     *
     * @return icon input stream, or {@code null} when the declared resource cannot be found
     */
    default @Nullable InputStream openIconStream() {
        return getClass().getResourceAsStream(iconResourceName());
    }
}
