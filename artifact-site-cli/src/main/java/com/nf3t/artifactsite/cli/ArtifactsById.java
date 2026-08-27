package com.nf3t.artifactsite.cli;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactMetadata;

/**
 * Groups artifact versions by artifact slug.
 */
public class ArtifactsById implements Iterable<ArtifactsByVersion> {
	private Map<String,ArtifactsByVersion> artifactsBySlug;

	public ArtifactsById() {
		this.artifactsBySlug = new LinkedHashMap<>();
	}

	/**
	 * Adds an artifact, replacing only an existing record with the same identity.
	 *
	 * @param artifact artifact metadata to store
	 */
	public void put(ArtifactMetadata artifact) {
		String slug = artifact.getArtifactSlug();
		ArtifactsByVersion value = artifactsBySlug.get(slug);
		if(value == null) {
			value = new ArtifactsByVersion();
			artifactsBySlug.put(slug, value);
		}
		value.put(artifact);
	}

	@Override
	public Iterator<ArtifactsByVersion> iterator() {
		return artifactsBySlug.values().iterator();
	}
}
