package com.nf3t.artifactsite.cli;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactMetadata;

public class ArtifactsByVersion implements Iterable<ArtifactMetadata> {
	private Map<String,ArtifactMetadata> artifactsBySlug;

	public ArtifactsByVersion() {
		this.artifactsBySlug = new HashMap<>();
	}

	public void put(ArtifactMetadata artifact) {
		String slug = artifact.getArtifactSlug();
		artifactsBySlug.put(slug, artifact);
	}

	@Override
	public Iterator<ArtifactMetadata> iterator() {
		return artifactsBySlug.values().iterator();
	}
}
