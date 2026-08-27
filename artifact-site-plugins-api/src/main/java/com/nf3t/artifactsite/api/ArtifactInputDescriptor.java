package com.nf3t.artifactsite.api;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Describes a parser input and resolved file characteristics.
 *
 * @param sourceType source type of the artifact input
 * @param sourceValue source value (local path or remote URL)
 * @param fileName resolved file name, if known
 * @param extension normalized file extension, if known
 * @param contentType resolved content type, if known
 */
public record ArtifactInputDescriptor(
		Path contentPath,
        ArtifactSourceType sourceType,
        String sourceValue,
        @Nullable String fileName,
        @Nullable String extension,
        @Nullable String contentType) {

   public ArtifactMetadata createArtifact() {
	   ArtifactMetadata artifact = new ArtifactMetadata();
	   artifact.setSourceType(sourceType.name().toLowerCase());
	   artifact.setSourceValue(sourceValue);
	   
	   if (ArtifactSourceType.REMOTE == sourceType) {
		   artifact.setSourceType(ArtifactSourceType.REMOTE.name().toLowerCase());
           artifact.setDownloadUrl(sourceValue);
           if (fileName != null) {
               artifact.setFileName(fileName);
           }
       }
	   
	   return artifact;
   }
}
