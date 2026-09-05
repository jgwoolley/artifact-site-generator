package com.nf3t.artifactsite.cli;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.nf3t.artifactsite.api.ArtifactFields;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.ArtifactSourceType;
import com.nf3t.artifactsite.api.Datum;
import com.nf3t.artifactsite.api.IArtifactParseContext;

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

    private void updateRemoteRequestConfig(RemoteRequestConfigStore store, Datum artifact, Path contentPath) {
        String artifactId = ArtifactFields.ID.getString(artifact);
        if (artifactId == null || artifactId.isBlank()) {
            return;
        }

        String sourceType = ArtifactFields.SOURCE_TYPE.getString(artifact);
        String sourceValue = ArtifactFields.SOURCE_VALUE.getString(artifact);
        if (!ArtifactSourceType.REMOTE.name().equalsIgnoreCase(sourceType)) {
            if (store.getRequestsByArtifactId().containsKey(artifactId)) {
                logger.info("Removing stored remote fetch configuration for artifact {} because it was (re)parsed from a local input ({})",
                        artifactId, sourceValue);
            }
            store.remove(artifactId);
            return;
        }

        RemoteRequestConfig requestConfig = new RemoteRequestConfig();
        requestConfig.setArtifactId(artifactId);
        requestConfig.setSourceType(sourceType);
        requestConfig.setSourceValue(sourceValue);
        requestConfig.setCachedPath(contentPath.toString());
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
    public void writeArtifact(Datum artifact, Path contentPath) {
    	if (artifact == null) {
            return;
        }

    	artifacts.put(artifact);
        if (remoteRequestConfigStore != null) {
            updateRemoteRequestConfig(remoteRequestConfigStore, artifact, contentPath);
        }
    }


	public void parseInputPaths(Iterable<String> inputPaths) {
        for(String inputPath: inputPaths) {
        	parseInputPath(inputPath);
        }
	}

	/**
	 * Resolves a local input path: the {@link Datum} the CLI stamps with {@link
	 * ArtifactFields#SOURCE_TYPE}/{@link ArtifactFields#SOURCE_VALUE}/{@link
	 * ArtifactFields#FILE_NAME} before any parser sees it, paired with the local file to read.
	 */
	private ResolvedInput resolveLocalInput(String artifactInput) {
        Path path = Path.of(artifactInput);

        Datum datum = createDatum();
        ArtifactFields.SOURCE_TYPE.setString(datum, ArtifactSourceType.LOCAL.name().toLowerCase());
        ArtifactFields.SOURCE_VALUE.setString(datum, path.toString());
        ArtifactFields.FILE_NAME.setString(datum, path.getFileName().toString());
        return new ResolvedInput(path, datum);
	}

	/**
	 * Downloads and resolves a remote input URL, mirroring {@link #resolveLocalInput(String)} for
	 * an artifact fetched over HTTP(S).
	 *
	 * @return the resolved datum and downloaded content path, or {@code null} when the download
	 *     failed (already logged)
	 */
	private ResolvedInput resolveRemoteInput(String artifactInput) {
		try {
	        Map<String, String> headers = parseHeaders(httpHeaders);
			RemoteDownloadResult remoteDownloadResult = remoteArtifactDownloader.download(artifactInput, headers, remoteTlsConfig, remoteCacheDir);
            Path parsePath = remoteDownloadResult.localPath();
            String fileName = remoteDownloadResult.fileName();

            String resolvedFileName = fileName;
            if (resolvedFileName == null || resolvedFileName.isBlank()) {
                String path = URI.create(artifactInput).getPath();
                int lastSeparator = path.lastIndexOf('/');
                if (lastSeparator >= 0 && lastSeparator < path.length() - 1) {
                    resolvedFileName = path.substring(lastSeparator + 1);
                }
            }

            Datum datum = createDatum();
            ArtifactFields.SOURCE_TYPE.setString(datum, ArtifactSourceType.REMOTE.name().toLowerCase());
            ArtifactFields.SOURCE_VALUE.setString(datum, artifactInput);
            ArtifactFields.FILE_NAME.setString(datum, resolvedFileName);
            return new ResolvedInput(parsePath, datum);

        } catch (Exception e) {
        	logger.error("Unable to download remote artifact {}", artifactInput, e);
            return null;
        }
	}

	private ResolvedInput resolveInput(String artifactInput) {
		boolean remoteInput = isRemoteInput(artifactInput);

        if (remoteInput) {
            return resolveRemoteInput(artifactInput);
        } else {
        	return resolveLocalInput(artifactInput);
        }
	}

	public void parseInputPath(String artifactInput) {
		logger.debug("Input Artifact: {}", artifactInput);

		ResolvedInput resolved;
		try {
			resolved = resolveInput(artifactInput);
		} catch (Exception e) {
			// Never let a single bad input (e.g. a malformed --http-header value,
			// or any other unexpected failure) abort the whole batch: doing so
			// would skip saveArtifacts()/saveRemoteRequestConfigStore() in
			// ParseCommand and silently drop every artifact already parsed from
			// earlier inputs in this run.
			logger.error("Could not resolve input {}", artifactInput, e);
			return;
		}

		if (resolved == null) {
			// Remote download failure; already logged in resolveRemoteInput().
			return;
		}

        for (ArtifactParserPlugin plugin : plugins) {
            final var parser = plugin.parser();
            if (!parser.supports(resolved.datum())) {
                continue;
            }

            try {
            	// TODO: Should have multiple phases that gather metadata
                parser.parse(resolved.datum(), resolved.contentPath(), this);
            } catch (Exception e) {
            	logger.warn("Could not parse " + artifactInput + " with " + parser.getClass(), e);
            }
        }
	}

	/** A resolved artifact input: its local content path, paired with a freshly-stamped datum. */
	private record ResolvedInput(Path contentPath, Datum datum) {
	}
}
