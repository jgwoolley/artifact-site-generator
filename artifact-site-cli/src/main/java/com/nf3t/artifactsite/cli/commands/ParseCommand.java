package com.nf3t.artifactsite.cli.commands;

import java.nio.file.Path;
import java.util.List;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nf3t.artifactsite.api.ArtifactParserPlugin;
import com.nf3t.artifactsite.cli.ArtifactParseContext;
import com.nf3t.artifactsite.cli.ArtifactSiteGeneratorCli;
import com.nf3t.artifactsite.cli.ArtifactsByParser;
import com.nf3t.artifactsite.cli.ParserDisplayNameCache;
import com.nf3t.artifactsite.cli.ParserIconCache;
import com.nf3t.artifactsite.cli.ParserInstallGuideCache;
import com.nf3t.artifactsite.cli.ParserSeoTagsCache;
import com.nf3t.artifactsite.cli.RemoteRequestConfigStore;
import com.nf3t.artifactsite.cli.RemoteTlsConfig;

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
        ParserIconCache.refresh(plugins, parentCommand.iconsDir(), LOGGER);
        ParserDisplayNameCache.refresh(plugins, parentCommand.parserDisplayNamesPath(), LOGGER);
        ParserInstallGuideCache.refresh(plugins, parentCommand.parserInstallGuidesPath(), LOGGER);
        ParserSeoTagsCache.refresh(plugins, parentCommand.parserSeoTagsPath(), LOGGER);

        ArtifactsByParser artifacts = parentCommand.loadArtifacts();
        RemoteRequestConfigStore remoteRequestConfigStore = parentCommand.loadRemoteRequestConfigStore();

        RemoteTlsConfig remoteTlsConfig = buildTlsConfig();
        ArtifactParseContext context = new ArtifactParseContext(
                LOGGER,
                remoteTlsConfig,
                parentCommand.remoteCacheDir(),
                httpHeaders,
                remoteRequestConfigStore,
                plugins,
                artifacts);
        
        try {
            context.parseInputPaths(artifactInputs);
        } finally {
            // Always persist whatever was successfully parsed, even if an
            // unexpected exception escaped parsing one of the inputs.
            parentCommand.saveArtifacts(artifacts);
            parentCommand.saveRemoteRequestConfigStore(remoteRequestConfigStore);
        }
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
}
