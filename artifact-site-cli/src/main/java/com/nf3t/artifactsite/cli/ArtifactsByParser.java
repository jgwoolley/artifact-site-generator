package com.nf3t.artifactsite.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactMetadata;

public class ArtifactsByParser implements Iterable<ArtifactsById>{
	private Map<String,ArtifactsById> artifactsByVersion;

	public ArtifactsByParser() {
		this.artifactsByVersion = new HashMap<>();
	}

	public void put(ArtifactMetadata artifact) {
		String pluginId = artifact.getPluginId();
		ArtifactsById value = artifactsByVersion.get(pluginId);
		if(value == null) {
			value = new ArtifactsById();
			artifactsByVersion.put(pluginId, value);
		}
		value.put(artifact);
	}
	
	public void load(List<ArtifactMetadata> artifacts) {
		for(ArtifactMetadata artifact: artifacts) {
			put(artifact);
		}
	}

	public List<ArtifactMetadata> save() {
		List<ArtifactMetadata> artifacts = new ArrayList<>();
		for(ArtifactsById artifactsBySlug: this) {
			for(ArtifactsByVersion artifactsByVersion: artifactsBySlug) {
				for(ArtifactMetadata artifact: artifactsByVersion) {
					artifacts.add(artifact);
				}
			}
		}
		
		return artifacts;
	}
	
	@Override
	public Iterator<ArtifactsById> iterator() {
		return artifactsByVersion.values().iterator();
	}	
}
