package com.nf3t.artifactsite.api;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Contract for artifact format parsers.
 */
public interface ArtifactParser {
    /**
     * Returns the stable identifier for this parser, used for artifact storage, URL routing,
     * and grouping (e.g. {@code "maven"}). Written into every artifact this parser produces via
     * {@link ArtifactFields#PLUGIN_ID}. This value is never shown to users; see
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
     * Determines whether this parser can handle a given artifact, based on whatever the CLI has
     * already stamped onto it (e.g. {@link ArtifactFields#FILE_NAME}) before any parser has run.
     *
     * @param datum candidate artifact datum, may be {@code null}
     * @return {@code true} when this parser supports the datum
     */
    public boolean supports(@Nullable Datum datum);

    /**
     * Parses a supported input's content and writes normalized metadata into {@code datum} (via
     * {@link ArtifactFields}). Because {@code datum} may be shared with other parsers that also
     * {@link #supports(Datum)} the same input, this should only add or refine fields relevant to
     * this parser's format rather than assume exclusive ownership of the record.
     *
     * @param datum artifact datum to enrich; may already carry fields written by an earlier
     *     parser in the same pass, or the CLI itself
     * @param contentPath local filesystem path to the artifact's content, ready to read
     * @param context parse helpers and utilities
     * @throws Exception when parsing fails
     */
    public void parse(Datum datum, Path contentPath, IArtifactParseContext context) throws Exception;

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

    /**
     * Returns additional SEO keywords this parser wants included on the {@code <meta
     * name="keywords">} tag of every page it appears on (e.g. format-specific terms like {@code
     * "maven"} or {@code "vscode"}), on top of each individual artifact's own {@link
     * ArtifactFields#TAGS}. Returns an empty list by default.
     *
     * @return parser-level SEO keywords, never {@code null}
     */
    default List<String> seoTags() {
        return List.of();
    }
}
