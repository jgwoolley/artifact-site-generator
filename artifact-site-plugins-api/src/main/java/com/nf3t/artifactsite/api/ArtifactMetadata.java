package com.nf3t.artifactsite.api;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Normalized artifact metadata produced by parser plugins.
 */
public class ArtifactMetadata {
    private @Nullable String id;
    private @Nullable String artifactName;
    private @Nullable String artifactId;
    private @Nullable String groupId;
    private @Nullable String version;
    private @Nullable String description;
    private List<String> authors = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private @Nullable String license;
    private @Nullable String sourceType;
    private @Nullable String sourceValue;
    private @Nullable String downloadUrl;
    private @Nullable String fileName;
    private long fileSizeBytes;
    private @Nullable String sha256;
    private @Nullable String pluginId;
    private @Nullable String scmUrl;
    private @Nullable String readme;

    /** @return stable artifact identifier */
    public @Nullable String getId() { return id; }

    /** @param id stable artifact identifier */
    public void setId(@Nullable String id) { this.id = id; }

    /** @return human-readable artifact name */
    public @Nullable String getArtifactName() { return artifactName; }

    /** @param artifactName human-readable artifact name */
    public void setArtifactName(@Nullable String artifactName) { this.artifactName = artifactName; }

    /** @return artifact id */
    public @Nullable String getArtifactId() { return artifactId; }

    /** @param artifactId artifact id */
    public void setArtifactId(@Nullable String artifactId) { this.artifactId = artifactId; }

    /** @return artifact group or publisher id */
    public @Nullable String getGroupId() { return groupId; }

    /** @param groupId artifact group or publisher id */
    public void setGroupId(@Nullable String groupId) { this.groupId = groupId; }

    /** @return artifact version */
    public @Nullable String getVersion() { return version; }

    /** @param version artifact version */
    public void setVersion(@Nullable String version) { this.version = version; }

    /** @return artifact description */
    public @Nullable String getDescription() { return description; }

    /** @param description artifact description */
    public void setDescription(@Nullable String description) { this.description = description; }

    /** @return list of artifact authors */
    public List<String> getAuthors() { return authors; }

    /** @param authors artifact authors; {@code null} resets to empty */
    public void setAuthors(@Nullable List<String> authors) { this.authors = authors == null ? new ArrayList<>() : authors; }

    /** @return list of artifact tags */
    public List<String> getTags() { return tags; }

    /** @param tags artifact tags; {@code null} resets to empty */
    public void setTags(@Nullable List<String> tags) { this.tags = tags == null ? new ArrayList<>() : tags; }

    /** @return artifact license */
    public @Nullable String getLicense() { return license; }

    /** @param license artifact license */
    public void setLicense(@Nullable String license) { this.license = license; }

    /** @return source type name */
    public @Nullable String getSourceType() { return sourceType; }

    /** @param sourceType source type name */
    public void setSourceType(@Nullable String sourceType) { this.sourceType = sourceType; }

    /** @return source value path or URL */
    public @Nullable String getSourceValue() { return sourceValue; }

    /** @param sourceValue source value path or URL */
    public void setSourceValue(@Nullable String sourceValue) { this.sourceValue = sourceValue; }

    /** @return download URL for generated site */
    public @Nullable String getDownloadUrl() { return downloadUrl; }

    /** @param downloadUrl download URL for generated site */
    public void setDownloadUrl(@Nullable String downloadUrl) { this.downloadUrl = downloadUrl; }

    /** @return original artifact file name */
    public @Nullable String getFileName() { return fileName; }

    /** @param fileName original artifact file name */
    public void setFileName(@Nullable String fileName) { this.fileName = fileName; }

    /** @return artifact file size in bytes */
    public long getFileSizeBytes() { return fileSizeBytes; }

    /** @param fileSizeBytes artifact file size in bytes */
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    /** @return SHA-256 digest */
    public @Nullable String getSha256() { return sha256; }

    /** @param sha256 SHA-256 digest */
    public void setSha256(@Nullable String sha256) { this.sha256 = sha256; }

    /** @return parser plugin id */
    public @Nullable String getPluginId() { return pluginId; }

    /** @param pluginId parser plugin id */
    public void setPluginId(@Nullable String pluginId) { this.pluginId = pluginId; }

    public @Nullable String getScmUrl() { return scmUrl; }

    public void setScmUrl(@Nullable String scmUrl) { this.scmUrl = scmUrl; }

    /** @return raw README markdown discovered inside the artifact, if any */
    public @Nullable String getReadme() { return readme; }

    /** @param readme raw README markdown discovered inside the artifact, if any */
    public void setReadme(@Nullable String readme) { this.readme = readme; }

    public String getArtifactSlug() {
    	return getGroupId() + "." + getArtifactId();
    }

    @Override
	public String toString() {
        return getGroupId() + "." + getArtifactId() + " (" + getVersion() + ")";
    }
    
    public void updateFileMetadata(ArtifactInputDescriptor descriptor) {
        setFileName(descriptor.fileName());
        setSourceType(descriptor.sourceType().name().toLowerCase());
        setSourceValue(descriptor.sourceValue());
    }
}
