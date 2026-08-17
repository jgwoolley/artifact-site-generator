package com.nf3t.artifactsite.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.jspecify.annotations.Nullable;

/**
 * Downloads remote artifacts into the local cache.
 */
public final class RemoteArtifactDownloader {

    public RemoteDownloadResult download(String url, Map<String, String> headers, @Nullable RemoteTlsConfig tls, Path cacheDir)
            throws IOException, GeneralSecurityException {
        Files.createDirectories(cacheDir);

        try (CloseableHttpClient httpClient = createHttpClient(tls)) {
            HttpGet request = new HttpGet(url);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.addHeader(header.getKey(), header.getValue());
            }

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode < HttpStatus.SC_SUCCESS || statusCode >= HttpStatus.SC_REDIRECTION) {
                    throw new IOException("Remote download failed with status code " + statusCode + " for " + url);
                }
                String contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE) == null
                        ? null
                        : response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
                String fileName = resolveFileName(url);
                Path destination = cacheDir.resolve(UUID.randomUUID() + "-" + fileName);
                try (InputStream in = response.getEntity().getContent()) {
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return new RemoteDownloadResult(destination, fileName, contentType);
            });
        }
    }

    private CloseableHttpClient createHttpClient(@Nullable RemoteTlsConfig tls) throws GeneralSecurityException, IOException {
        if (tls == null || tls.isEmpty()) {
            return HttpClients.createDefault();
        }

        SSLContextBuilder builder = SSLContexts.custom();

        if (!isBlank(tls.getTrustStorePath())) {
            builder.loadTrustMaterial(readTrustStore(Path.of(tls.getTrustStorePath()), tls.getTrustStorePassword()), null);
        }

        if (!isBlank(tls.getClientCertificatePath()) && !isBlank(tls.getClientPrivateKeyPath())) {
            KeyStore keyStore = readClientKeyStore(
                    Path.of(tls.getClientCertificatePath()),
                    Path.of(tls.getClientPrivateKeyPath()),
                    tls.getClientPrivateKeyPassword());
            char[] keyPassword = asPassword(tls.getClientPrivateKeyPassword());
            builder.loadKeyMaterial(keyStore, keyPassword);
        }

        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(builder.build())
                .build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
    }

    private static KeyStore readTrustStore(Path trustStorePath, @Nullable String password)
            throws GeneralSecurityException, IOException {
        if (isPemFile(trustStorePath)) {
            return readPemTrustStore(trustStorePath);
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(trustStorePath)) {
            trustStore.load(in, asPassword(password));
        }
        return trustStore;
    }

    private static KeyStore readPemTrustStore(Path trustStorePath) throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (InputStream in = Files.newInputStream(trustStorePath)) {
            Collection<? extends Certificate> certs = certificateFactory.generateCertificates(in);
            int index = 0;
            for (Certificate cert : certs) {
                trustStore.setCertificateEntry("ca-" + index, cert);
                index++;
            }
        }
        return trustStore;
    }

    private static KeyStore readClientKeyStore(Path certPath, Path keyPath, @Nullable String keyPassword)
            throws GeneralSecurityException, IOException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        try (InputStream in = Files.newInputStream(certPath)) {
            Collection<? extends Certificate> generated = certificateFactory.generateCertificates(in);
            for (Certificate certificate : generated) {
                certificates.add((X509Certificate) certificate);
            }
        }

        PrivateKey privateKey = readPrivateKey(keyPath, keyPassword);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = asPassword(keyPassword);
        keyStore.load(null, password);
        keyStore.setKeyEntry("client", privateKey, password, certificates.toArray(new Certificate[0]));
        return keyStore;
    }

    private static PrivateKey readPrivateKey(Path keyPath, @Nullable String keyPassword)
            throws IOException, GeneralSecurityException {
        String pem = Files.readString(keyPath, StandardCharsets.UTF_8);

        if (pem.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            String base64 = pem.replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                    .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encrypted = Base64.getDecoder().decode(base64);
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(encrypted);
            char[] password = asPassword(keyPassword);
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
            Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, secretKeyFactory.generateSecret(pbeKeySpec), encryptedPrivateKeyInfo.getAlgParameters());
            PKCS8EncodedKeySpec keySpec = encryptedPrivateKeyInfo.getKeySpec(cipher);
            return parsePrivateKey(keySpec);
        }

        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
        return parsePrivateKey(keySpec);
    }

    private static PrivateKey parsePrivateKey(PKCS8EncodedKeySpec keySpec) throws GeneralSecurityException {
        GeneralSecurityException lastException = null;
        for (String algorithm : List.of("RSA", "EC", "DSA")) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            } catch (GeneralSecurityException e) {
                lastException = e;
            }
        }
        throw lastException == null ? new GeneralSecurityException("Unsupported private key algorithm") : lastException;
    }

    private static boolean isPemFile(Path path) {
        String value = path.getFileName().toString().toLowerCase();
        return value.endsWith(".pem") || value.endsWith(".crt") || value.endsWith(".cer");
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static char[] asPassword(@Nullable String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static String resolveFileName(String url) {
        String path = URI.create(url).getPath();
        int separator = path.lastIndexOf('/');
        if (separator >= 0 && separator < path.length() - 1) {
            return path.substring(separator + 1);
        }
        return "artifact.bin";
    }
}
