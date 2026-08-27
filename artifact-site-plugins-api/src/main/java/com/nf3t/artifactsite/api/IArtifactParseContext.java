package com.nf3t.artifactsite.api;

/**
 * Shared parsing utilities for parser plugins.
 */
public interface IArtifactParseContext {

	public void writeArtifact(ArtifactMetadata artifact);
}
