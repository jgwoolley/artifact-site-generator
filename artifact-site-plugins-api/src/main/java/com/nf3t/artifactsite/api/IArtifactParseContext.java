package com.nf3t.artifactsite.api;

import java.nio.file.Path;

/**
 * Shared parsing utilities for parser plugins.
 */
public interface IArtifactParseContext {

	/**
	 * Creates a new, empty {@link Datum} for a parser to populate (typically via
	 * {@link ArtifactFields}) and pass to {@link #writeArtifact(Datum, Path)}.
	 * Defaults to {@link MapDatum}; implementations may override this to supply a differently
	 * backed {@link Datum} (e.g. one tied to a specific storage layer).
	 *
	 * @return a new, empty datum
	 */
	default Datum createDatum() {
		return new MapDatum();
	}

	/**
	 * Persists (or updates) an artifact record. Since more than one {@link ArtifactParser} may
	 * {@link ArtifactParser#supports(Datum) support} and enrich the same {@code datum}, a parser
	 * should call this once it has finished contributing its own fields - calling it more than
	 * once for the same {@code datum} (e.g. because multiple parsers matched the same input) is
	 * harmless.
	 *
	 * @param artifact artifact datum to persist
	 * @param contentPath local filesystem path this artifact's content was read from, passed
	 *     through from {@link ArtifactParser#parse(Datum, Path, IArtifactParseContext)}
	 */
	public void writeArtifact(Datum artifact, Path contentPath);
}
