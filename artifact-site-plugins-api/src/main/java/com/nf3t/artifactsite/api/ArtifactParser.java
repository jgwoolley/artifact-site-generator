package com.nf3t.artifactsite.api;

import java.io.InputStream;

import org.jspecify.annotations.Nullable;

/**
 * Contract for artifact format parsers.
 */
public interface ArtifactParser {
    /**
     * Returns the stable identifier for this parser, used for artifact storage, URL routing,
     * and grouping (e.g. {@code "maven"}). Written into every artifact this parser produces via
     * {@link ArtifactMetadata#setPluginId(String)}. This value is never shown to users; see
     * {@link #displayName()} for the UI-facing label.
     *
     * @return parser identifier
     */
    public String id();

    /**
     * Returns the human-readable name shown in the UI for this parser (e.g. {@code "Maven"}),
     * distinct from the stable {@link #id()}.
     *
     * @return display name
     */
    public String displayName();

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

    /**
     * Returns the resource name of this parser's install guide template (e.g.
     * {@code "install.html"}), an HTML fragment shown to users in a "How to Install" popup on
     * the artifact detail page. Like {@link #iconResourceName()}, the resource is expected to
     * live in the standard location alongside the parser implementation class.
     *
     * <p>The fragment may reference {@code {{groupId}}}, {@code {{artifactId}}}, and
     * {@code {{version}}} placeholders, substituted with the specific artifact's own (HTML-escaped)
     * values when rendered. The fragment itself is treated as trusted, parser-author-controlled
     * markup - unlike a README, it is emitted as-is, so it may freely use {@code <pre>}/{@code
     * <code>} and other markup styled by the site's own CSS.
     *
     * @return install guide resource name, or {@code null} when this parser has no install guide
     */
    default @Nullable String installGuideResourceName() {
        return null;
    }

    /**
     * Opens this parser's install guide template, loaded by a classloader rooted at the parser
     * implementation class.
     *
     * @return install guide input stream, or {@code null} when this parser declares no install
     *         guide resource, or the declared resource cannot be found
     */
    default @Nullable InputStream openInstallGuideStream() {
        String resourceName = installGuideResourceName();
        return resourceName == null ? null : getClass().getResourceAsStream(resourceName);
    }
}
