package com.nf3t.artifactsite.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Collection wrapper for persisted remote request configurations.
 */
public class RemoteRequestConfigStore {
    private Map<String, RemoteRequestConfig> requestsByArtifactId = new LinkedHashMap<>();

    public Map<String, RemoteRequestConfig> getRequestsByArtifactId() {
        return requestsByArtifactId;
    }

    public void setRequestsByArtifactId(@Nullable Map<String, RemoteRequestConfig> requestsByArtifactId) {
        this.requestsByArtifactId = requestsByArtifactId == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(requestsByArtifactId);
    }

    public void put(String artifactId, RemoteRequestConfig config) {
        requestsByArtifactId.put(artifactId, config);
    }

    public void remove(String artifactId) {
        requestsByArtifactId.remove(artifactId);
    }
}
