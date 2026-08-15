package io.github.jgwoolley.artifactsite.cli;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.jgwoolley.artifactsite.api.ArtifactMetadata;

public class ArtifactsByParser implements Iterable<ArtifactsBySlug>{
	private Map<String,ArtifactsBySlug> artifactsByVersion;
	
	public ArtifactsByParser() {
		this.artifactsByVersion = new HashMap<>();
	}
	
	public void load(List<ArtifactMetadata> artifacts) { 
		for(ArtifactMetadata artifact: artifacts) {
			String pluginId = artifact.getPluginId();
			ArtifactsBySlug value = artifactsByVersion.get(pluginId);
			if(value == null) {
				value = new ArtifactsBySlug();
				artifactsByVersion.put(pluginId, value);
			}
			value.add(artifact);
		}
	}

	@Override
	public Iterator<ArtifactsBySlug> iterator() {
		return artifactsByVersion.values().iterator();
	}
}
