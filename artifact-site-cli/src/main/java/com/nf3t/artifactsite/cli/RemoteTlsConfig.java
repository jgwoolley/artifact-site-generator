package com.nf3t.artifactsite.cli;

import org.jspecify.annotations.Nullable;

/**
 * TLS configuration for remote artifact requests.
 */
public class RemoteTlsConfig {
    private @Nullable String trustStorePath;
    private @Nullable String trustStorePassword;
    private @Nullable String clientCertificatePath;
    private @Nullable String clientPrivateKeyPath;
    private @Nullable String clientPrivateKeyPassword;

    public @Nullable String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(@Nullable String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public @Nullable String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(@Nullable String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public @Nullable String getClientCertificatePath() {
        return clientCertificatePath;
    }

    public void setClientCertificatePath(@Nullable String clientCertificatePath) {
        this.clientCertificatePath = clientCertificatePath;
    }

    public @Nullable String getClientPrivateKeyPath() {
        return clientPrivateKeyPath;
    }

    public void setClientPrivateKeyPath(@Nullable String clientPrivateKeyPath) {
        this.clientPrivateKeyPath = clientPrivateKeyPath;
    }

    public @Nullable String getClientPrivateKeyPassword() {
        return clientPrivateKeyPassword;
    }

    public void setClientPrivateKeyPassword(@Nullable String clientPrivateKeyPassword) {
        this.clientPrivateKeyPassword = clientPrivateKeyPassword;
    }

    public boolean isEmpty() {
        return isBlank(trustStorePath)
                && isBlank(clientCertificatePath)
                && isBlank(clientPrivateKeyPath)
                && isBlank(clientPrivateKeyPassword)
                && isBlank(trustStorePassword);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
