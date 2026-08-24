package com.nf3t.artifactsite.cli.commands;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactInputDescriptor;
import com.nf3t.artifactsite.api.ArtifactMetadata;
import com.nf3t.artifactsite.api.ArtifactParseContext;
import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.api.ArtifactSourceType;
import com.nf3t.artifactsite.cli.RemoteDownloadResult;
import com.nf3t.artifactsite.cli.RemoteRequestConfig;
import com.nf3t.artifactsite.cli.RemoteRequestConfigStore;
import com.nf3t.artifactsite.cli.RemoteTlsConfig;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;
import com.nf3t.artifactsite.cli.ArtifactsByParser;
import com.nf3t.artifactsite.cli.RemoteArtifactDownloader;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Loads parser plugins and parses an input artifact path.
 */
@Command(name = "parse", description = "Loads parser plugins and prints the first plugin that supports the input")
public class ParseCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseCommand.class);

    @ParentCommand
    private ArtifactSiteGeneratorCli parentCommand;

    @Parameters(index = "0..*", description = "Artifact file path(s) or URL(s)")
    private List<String> artifactInputs;

    @Option(names = "--http-header", description = "Remote HTTP header in NAME=VALUE format")
    private List<String> httpHeaders;

    @Option(names = "--remote-tls-trust-store", description = "Path to trust store or CA certificate")
    private Path remoteTlsTrustStore;

    @Option(names = "--remote-tls-trust-store-password", description = "Password for remote trust store")
    private String remoteTlsTrustStorePassword;

    @Option(names = "--remote-tls-client-cert", description = "Path to remote client certificate")
    private Path remoteTlsClientCert;

    @Option(names = "--remote-tls-client-key", description = "Path to remote client private key")
    private Path remoteTlsClientKey;

    @Option(names = "--remote-tls-client-key-password", description = "Password for remote client private key")
    private String remoteTlsClientKeyPassword;

    /**
     * Executes parser plugin discovery and parsing.
     */
    @Override
    public void run() {
        PluginManager pluginManager = parentCommand.createPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        List<ArtifactParserPlugin> plugins = parentCommand.loadParserPlugins(pluginManager);
        LOGGER.info("Loaded {} parser plugin(s).", plugins.size());
        
        ArtifactsByParser artifacts = parentCommand.loadArtifacts();
        RemoteRequestConfigStore remoteRequestConfigStore = parentCommand.loadRemoteRequestConfigStore();

        for(String artifactInput: artifactInputs) {
            LOGGER.debug("Input Artifact: {}", artifactInput);
            ArtifactParseContext context = new ArtifactParseContext();

            boolean remoteInput = isRemoteInput(artifactInput);
            ArtifactInputDescriptor descriptor;
            Path parsePath;
            // TODO: RemoteDownloadResult could potentially share class with artifact descriptor
            RemoteDownloadResult remoteDownloadResult = null;
            if (remoteInput) {
                Map<String, String> headers = parseHeaders(httpHeaders);
                RemoteTlsConfig tlsConfig = buildTlsConfig();
                RemoteArtifactDownloader downloader = new RemoteArtifactDownloader();
                try {
                    remoteDownloadResult = downloader.download(artifactInput, headers, tlsConfig, parentCommand.remoteCacheDir());
                } catch (Exception e) {
                    LOGGER.error("Unable to download remote artifact {}", artifactInput, e);
                    return;
                }
                parsePath = remoteDownloadResult.localPath();
                descriptor = ArtifactInputDescriptor.parseRemote(
                        artifactInput,
                        remoteDownloadResult.fileName(),
                        remoteDownloadResult.contentType());
            } else {
                parsePath = Path.of(artifactInput);
                descriptor = ArtifactInputDescriptor.parseLocal(parsePath);
            }

            for (ArtifactParserPlugin plugin : plugins) {
                final var parser = plugin.parser();
                if (!parser.supports(descriptor)) {
                    continue;
                }

                try {
                	// TODO: Should have multiple phases that gather metadata
                    ArtifactMetadata artifact;
                    try (InputStream input = Files.newInputStream(parsePath)) {
                        artifact = parser.parse(descriptor, input, context);
                    }
                    if (artifact == null) {
                        continue;
                    }
                    if (remoteInput) {
                        artifact.setSourceType(ArtifactSourceType.REMOTE.name().toLowerCase());
                        artifact.setSourceValue(artifactInput);
                        artifact.setDownloadUrl(artifactInput);
                        if (remoteDownloadResult != null && remoteDownloadResult.fileName() != null) {
                            artifact.setFileName(remoteDownloadResult.fileName());
                        }
                    } else {
                        artifact.setSourceType(ArtifactSourceType.LOCAL.name().toLowerCase());
                        artifact.setSourceValue(Path.of(artifactInput).toString());
                    }
                    LOGGER.debug("Parsed: {}", artifact);
                    artifacts.put(artifact);
                    updateRemoteRequestConfig(remoteRequestConfigStore, artifact, remoteInput, remoteDownloadResult, artifactInput);
                } catch (Exception e) {
                    LOGGER.warn("Could not parse " + artifactInput + " with " + parser.getClass(), e);
                }
            }        	
        }
        
        parentCommand.saveArtifacts(artifacts);
        parentCommand.saveRemoteRequestConfigStore(remoteRequestConfigStore);
    }

    private void updateRemoteRequestConfig(
            RemoteRequestConfigStore store,
            ArtifactMetadata artifact,
            boolean remoteInput,
            RemoteDownloadResult remoteDownloadResult, String artifactInput) {
        String artifactId = artifact.getId();
        if (artifactId == null || artifactId.isBlank()) {
            return;
        }

        if (!remoteInput) {
            store.remove(artifactId);
            return;
        }

        RemoteRequestConfig requestConfig = new RemoteRequestConfig();
        requestConfig.setArtifactId(artifactId);
        requestConfig.setSourceType(ArtifactSourceType.REMOTE.name().toLowerCase());
        requestConfig.setSourceValue(artifactInput);
        if (remoteDownloadResult != null) {
            requestConfig.setCachedPath(remoteDownloadResult.localPath().toString());
        }
        requestConfig.setHeaders(parseHeaders(httpHeaders));
        RemoteTlsConfig tlsConfig = buildTlsConfig();
        requestConfig.setTls(tlsConfig.isEmpty() ? null : tlsConfig);
        store.put(artifactId, requestConfig);
    }

    private RemoteTlsConfig buildTlsConfig() {
        RemoteTlsConfig tls = new RemoteTlsConfig();
        tls.setTrustStorePath(remoteTlsTrustStore == null ? null : remoteTlsTrustStore.toString());
        tls.setTrustStorePassword(remoteTlsTrustStorePassword);
        tls.setClientCertificatePath(remoteTlsClientCert == null ? null : remoteTlsClientCert.toString());
        tls.setClientPrivateKeyPath(remoteTlsClientKey == null ? null : remoteTlsClientKey.toString());
        tls.setClientPrivateKeyPassword(remoteTlsClientKeyPassword);
        return tls;
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
}
