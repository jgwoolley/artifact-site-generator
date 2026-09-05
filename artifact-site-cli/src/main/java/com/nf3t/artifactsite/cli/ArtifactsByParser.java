package com.nf3t.artifactsite.cli;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.Datum;

/**
 * Groups parsed artifacts by parser plugin.
 */
public class ArtifactsByParser implements Iterable<ArtifactsById>{
	private Map<String,ArtifactsById> artifactsByVersion;

	public ArtifactsByParser() {
		this.artifactsByVersion = new LinkedHashMap<>();
	}

	public void put(Datum artifact) {
		String pluginId = ArtifactFields.PLUGIN_ID.getString(artifact);
		ArtifactsById value = artifactsByVersion.get(pluginId);
		if(value == null) {
			value = new ArtifactsById();
			artifactsByVersion.put(pluginId, value);
		}
		value.put(artifact);
	}

	public void load(List<Datum> artifacts) {
		for(Datum artifact: artifacts) {
			put(artifact);
		}
	}

	public List<Datum> save() {
		List<Datum> artifacts = new ArrayList<>();
		for(ArtifactsById artifactsBySlug: this) {
			for(ArtifactsByVersion artifactsByVersion: artifactsBySlug) {
				for(Datum artifact: artifactsByVersion) {
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
