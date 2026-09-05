package com.nf3t.artifactsite.cli;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;

/**
 * Stores one metadata record per artifact identity and preserves insertion order.
 */
public class ArtifactsByVersion implements Iterable<Datum> {
	private Map<String,Datum> artifactsById;

	public ArtifactsByVersion() {
		this.artifactsById = new LinkedHashMap<>();
	}

	/**
	 * Adds an artifact, replacing an earlier record with the same identity.
	 *
	 * @param artifact artifact metadata to store
	 */
	public void put(Datum artifact) {
		String id = ArtifactFields.ID.getString(artifact);
		if (id == null || id.isBlank()) {
			id = ArtifactFields.GROUP_ID.getString(artifact) + ":" + ArtifactFields.ARTIFACT_ID.getString(artifact)
					+ ":" + ArtifactFields.VERSION.getString(artifact);
		}
		artifactsById.put(id, artifact);
	}

	@Override
	public Iterator<Datum> iterator() {
		return artifactsById.values().iterator();
	}
}
