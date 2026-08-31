package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.ArtifactSourceType;
import com.nf3t.artifactsite.api.IArtifactParseContext;
import com.nf3t.artifactsite.api.PathUtils;

public class ArtifactParseContext implements IArtifactParseContext {
	private Logger logger;
	private RemoteArtifactDownloader remoteArtifactDownloader;
	private RemoteTlsConfig remoteTlsConfig;
	private Path remoteCacheDir;
	private List<String> httpHeaders;
	private RemoteRequestConfigStore remoteRequestConfigStore;
	private List<ArtifactParserPlugin> plugins;
	private ArtifactsByParser artifacts;
	
	
    public ArtifactParseContext(Logger logger, RemoteTlsConfig remoteTlsConfig, Path remoteCacheDir, List<String> httpHeaders, RemoteRequestConfigStore remoteRequestConfigStore, List<ArtifactParserPlugin> plugins, ArtifactsByParser artifacts) {
    	this.logger = logger;
    	this.remoteArtifactDownloader = new RemoteArtifactDownloader();
    	this.remoteTlsConfig = remoteTlsConfig;
    	this.remoteCacheDir = remoteCacheDir;
    	this.httpHeaders = httpHeaders == null ? new ArrayList<>(0) : new ArrayList<>(httpHeaders);
        this.remoteRequestConfigStore = remoteRequestConfigStore;
    	this.plugins = plugins;
    	this.artifacts = artifacts;
    }
	
    private void updateRemoteRequestConfig(
            RemoteRequestConfigStore store,
            ArtifactMetadata artifact,
            ArtifactInputDescriptor descriptor) {
        String artifactId = artifact.getId();
        if (artifactId == null || artifactId.isBlank()) {
            return;
        }

        if (descriptor.sourceType() != ArtifactSourceType.REMOTE) {
            if (store.getRequestsByArtifactId().containsKey(artifactId)) {
                logger.info("Removing stored remote fetch configuration for artifact {} because it was (re)parsed from a local input ({})",
                        artifactId, descriptor.sourceValue());
            }
            store.remove(artifactId);
            return;
        }

        RemoteRequestConfig requestConfig = new RemoteRequestConfig();
        requestConfig.setArtifactId(artifactId);
        requestConfig.setSourceType(ArtifactSourceType.REMOTE.name().toLowerCase());
        requestConfig.setSourceValue(descriptor.sourceValue());
        requestConfig.setCachedPath(descriptor.contentPath().toString());
        requestConfig.setHeaders(parseHeaders(httpHeaders));
        requestConfig.setTls(remoteTlsConfig.isEmpty() ? null : remoteTlsConfig);
        store.put(artifactId, requestConfig);
    }

    private static boolean isRemoteInput(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, String> parseHeaders(List<String> headers) {
        Map<String, String> map = new LinkedHashMap<>();
        if (headers == null) {
            return map;
        }
        for (String header : headers) {
            int separator = header.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid --http-header value (expected NAME=VALUE): " + header);
            }
            String name = header.substring(0, separator).trim();
            String value = header.substring(separator + 1).trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Invalid --http-header value (empty name): " + header);
            }
            map.put(name, value);
        }
        return map;
    }
	
    @Override
    public void writeArtifact(ArtifactInputDescriptor descriptor, ArtifactMetadata artifact) {
    	if (artifact == null) {
            return;
        }

        // The descriptor is the trusted source of truth for "what input produced this
        // artifact" — stamped here rather than left to the parser plugin, since
        // ArtifactParserPlugin is a PF4J extension point and a third-party plugin isn't
        // guaranteed to have called ArtifactMetadata.updateFileMetadata(descriptor) itself.
        artifact.updateFileMetadata(descriptor);
    	artifacts.put(artifact);
        if (remoteRequestConfigStore != null) {
            updateRemoteRequestConfig(remoteRequestConfigStore, artifact, descriptor);
        }
    }
    
    
	public void parseInputPaths(Iterable<String> inputPaths) {
        for(String inputPath: inputPaths) {
        	parseInputPath(inputPath);
        }
	}
	
	private ArtifactInputDescriptor createArtifactLocalInputDescriptor(String artifactInput) {
        Path path = Path.of(artifactInput);

		String extension = PathUtils.getExtension(path);
        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(path);
        } catch (IOException ignored) {

        }
        return new ArtifactInputDescriptor(path, ArtifactSourceType.LOCAL, path.toString(), path.getFileName().toString(), extension, contentType);
	}
	
	private ArtifactInputDescriptor createArtifactRemoteInputDescriptor(String artifactInput) {
		try {
	        Map<String, String> headers = parseHeaders(httpHeaders);
			RemoteDownloadResult remoteDownloadResult = remoteArtifactDownloader.download(artifactInput, headers, remoteTlsConfig, remoteCacheDir);
            Path parsePath = remoteDownloadResult.localPath();
            String fileName = remoteDownloadResult.fileName();
            String contentType = remoteDownloadResult.contentType();
            
            String resolvedFileName = fileName;
            if (resolvedFileName == null || resolvedFileName.isBlank()) {
                String path = URI.create(artifactInput).getPath();
                int lastSeparator = path.lastIndexOf('/');
                if (lastSeparator >= 0 && lastSeparator < path.length() - 1) {
                    resolvedFileName = path.substring(lastSeparator + 1);
                }
            }
            String extension = resolvedFileName == null ? null : PathUtils.getExtension(Path.of(resolvedFileName));
            return new ArtifactInputDescriptor(parsePath, ArtifactSourceType.REMOTE, artifactInput, resolvedFileName, extension, contentType);
            
        } catch (Exception e) {
        	logger.error("Unable to download remote artifact {}", artifactInput, e);
            return null;
        }
	}
	
	public ArtifactInputDescriptor createArtifactInputDescriptor(String artifactInput) {
		boolean remoteInput = isRemoteInput(artifactInput);

        if (remoteInput) {
            return createArtifactRemoteInputDescriptor(artifactInput);
        } else {
        	return createArtifactLocalInputDescriptor(artifactInput);
        }
	}
	
	public void parseInputPath(String artifactInput) {
		logger.debug("Input Artifact: {}", artifactInput);

		ArtifactInputDescriptor descriptor;
		try {
			descriptor = createArtifactInputDescriptor(artifactInput);
		} catch (Exception e) {
			// Never let a single bad input (e.g. a malformed --http-header value,
			// or any other unexpected failure) abort the whole batch: doing so
			// would skip saveArtifacts()/saveRemoteRequestConfigStore() in
			// ParseCommand and silently drop every artifact already parsed from
			// earlier inputs in this run.
			logger.error("Could not create input descriptor for {}", artifactInput, e);
			return;
		}

        for (ArtifactParserPlugin plugin : plugins) {
            final var parser = plugin.parser();
            if (!parser.supports(descriptor)) {
                continue;
            }

            try {
            	// TODO: Should have multiple phases that gather metadata
                try (InputStream input = Files.newInputStream(descriptor.contentPath())) {
                    parser.parse(descriptor, this);
                }          
            } catch (Exception e) {
            	logger.warn("Could not parse " + artifactInput + " with " + parser.getClass(), e);
            }
        }
	}
}
