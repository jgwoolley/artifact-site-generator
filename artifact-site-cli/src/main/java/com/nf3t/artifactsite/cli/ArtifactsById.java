package com.nf3t.artifactsite.cli;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactMetadata;

public class ArtifactsById implements Iterable<ArtifactsByVersion> {
	private Map<String,ArtifactsByVersion> artifactsByVersion;

	public ArtifactsById() {
		this.artifactsByVersion = new HashMap<>();
	}

	public void put(ArtifactMetadata artifact) {
		String pluginId = artifact.getPluginId();
		ArtifactsByVersion value = artifactsByVersion.get(pluginId);
		if(value == null) {
			value = new ArtifactsByVersion();
			artifactsByVersion.put(pluginId, value);
		}
		value.put(artifact);
	}

	@Override
	public Iterator<ArtifactsByVersion> iterator() {
		return artifactsByVersion.values().iterator();
	}
}
