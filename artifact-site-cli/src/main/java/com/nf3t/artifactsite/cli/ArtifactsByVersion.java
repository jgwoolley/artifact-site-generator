package com.nf3t.artifactsite.cli;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactMetadata;

/**
 * Stores one metadata record per artifact identity and preserves insertion order.
 */
public class ArtifactsByVersion implements Iterable<ArtifactMetadata> {
	private Map<String,ArtifactMetadata> artifactsById;

	public ArtifactsByVersion() {
		this.artifactsById = new LinkedHashMap<>();
	}

	/**
	 * Adds an artifact, replacing an earlier record with the same identity.
	 *
	 * @param artifact artifact metadata to store
	 */
	public void put(ArtifactMetadata artifact) {
		String id = artifact.getId();
		if (id == null || id.isBlank()) {
			id = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
		}
		artifactsById.put(id, artifact);
	}

	@Override
	public Iterator<ArtifactMetadata> iterator() {
		return artifactsById.values().iterator();
	}
}
