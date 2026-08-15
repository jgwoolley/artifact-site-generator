package io.github.jgwoolley.artifactsite.cli;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.github.jgwoolley.artifactsite.api.ArtifactMetadata;

public class ArtifactsBySlug implements Iterable<ArtifactMetadata> {
	private Map<String,ArtifactMetadata> artifactsBySlug;
	
	public ArtifactsBySlug() {
		this.artifactsBySlug = new HashMap<>();
	}
		
	public void add(ArtifactMetadata artifact) {
		String slug = artifact.getSlug();
		artifactsBySlug.put(slug, artifact);
	}

	@Override
	public Iterator<ArtifactMetadata> iterator() {
		return artifactsBySlug.values().iterator();
	}
}
