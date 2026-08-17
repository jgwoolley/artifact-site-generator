package com.nf3t.artifactsite.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Persisted request settings for a remotely-sourced artifact.
 */
public class RemoteRequestConfig {
    private @Nullable String artifactId;
    private @Nullable String sourceType;
    private @Nullable String sourceValue;
    private @Nullable String cachedPath;
    private Map<String, String> headers = new LinkedHashMap<>();
    private @Nullable RemoteTlsConfig tls;

    public @Nullable String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(@Nullable String artifactId) {
        this.artifactId = artifactId;
    }

    public @Nullable String getSourceType() {
        return sourceType;
    }

    public void setSourceType(@Nullable String sourceType) {
        this.sourceType = sourceType;
    }

    public @Nullable String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(@Nullable String sourceValue) {
        this.sourceValue = sourceValue;
    }

    public @Nullable String getCachedPath() {
        return cachedPath;
    }

    public void setCachedPath(@Nullable String cachedPath) {
        this.cachedPath = cachedPath;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(@Nullable Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    public @Nullable RemoteTlsConfig getTls() {
        return tls;
    }

    public void setTls(@Nullable RemoteTlsConfig tls) {
        this.tls = tls;
    }
}
