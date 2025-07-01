/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tileverse.rangereader.http;

import io.tileverse.rangereader.AbstractRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.NonNull;

/**
 * A RangeReader implementation that reads from an HTTP(S) URL using range requests.
 * <p>
 * This class enables reading data from web servers that support HTTP range requests,
 * which is essential for efficient cloud-optimized access to large files.
 * <p>
 * By default, this implementation accepts all SSL certificates, allowing connections to
 * servers with self-signed or otherwise untrusted certificates. This can be controlled
 * through the appropriate constructor.
 * <p>
 * It also supports various authentication methods through the HttpAuthentication interface.
 * <p>
 * Uses the modern Java 11+ HttpClient API for better performance and features.
 */
public class HttpRangeReader extends AbstractRangeReader implements RangeReader {

    private static final Logger LOGGER = Logger.getLogger(HttpRangeReader.class.getName());

    private final URI uri;
    private final HttpClient httpClient;
    private final HttpAuthentication authentication;
    private long contentLength = -1;

    /**
     * Creates a new HttpRangeReader for the specified URI with default settings.
     * This constructor creates an HttpClient that accepts all SSL certificates.
     *
     * @param uri The URI to read from
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(URI uri) throws IOException {
        this(uri, true);
    }

    /**
     * Creates a new HttpRangeReader for the specified URI with control over SSL certificate validation.
     *
     * @param uri The URI to read from
     * @param trustAllCertificates Whether to trust all SSL certificates
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(URI uri, boolean trustAllCertificates) throws IOException {
        this(
                uri,
                trustAllCertificates
                        ? createTrustAllHttpClient()
                        : HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(20))
                                .build(),
                null);
    }

    /**
     * Creates a new HttpRangeReader with authentication.
     *
     * @param uri The URI to read from
     * @param authentication The authentication mechanism to use
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(URI uri, HttpAuthentication authentication) throws IOException {
        this(uri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), authentication);
    }

    /**
     * Creates a new HttpRangeReader with authentication and control over SSL certificate validation.
     *
     * @param uri The URI to read from
     * @param trustAllCertificates Whether to trust all SSL certificates
     * @param authentication The authentication mechanism to use
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(URI uri, boolean trustAllCertificates, HttpAuthentication authentication)
            throws IOException {
        this(
                uri,
                trustAllCertificates
                        ? createTrustAllHttpClient()
                        : HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(20))
                                .build(),
                authentication);
    }

    /**
     * Creates an HttpClient that accepts all SSL certificates.
     *
     * @return An HttpClient configured to trust all SSL certificates
     * @throws IOException If there's an error setting up the SSL context
     */
    private static HttpClient createTrustAllHttpClient() throws IOException {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Accept all
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Accept all
                    }
                }
            };

            // Create SSL context with our trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Create the HTTP client with our SSL context
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .sslContext(sslContext)
                    .build();

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.log(Level.WARNING, "Failed to create trust-all SSL context, falling back to default", e);
            throw new IOException("Failed to create SSL context for HTTPS", e);
        }
    }

    /**
     * Creates a new HttpRangeReader with a custom HTTP client.
     *
     * @param uri The URI to read from
     * @param httpClient The HttpClient to use
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(URI uri, HttpClient httpClient) throws IOException {
        this(uri, httpClient, null);
    }

    /**
     * Creates a new HttpRangeReader with a custom HTTP client and authentication.
     *
     * @param uri The URI to read from
     * @param httpClient The HttpClient to use
     * @param authentication The authentication mechanism to use, or null for no authentication
     * @throws IOException If an I/O error occurs
     */
    public HttpRangeReader(@NonNull URI uri, @NonNull HttpClient httpClient, HttpAuthentication authentication)
            throws IOException {
        this.uri = uri;
        this.httpClient = httpClient;
        this.authentication = authentication; // Can be null for no authentication

        // Verify that the URI is accessible and check for range support
        try {
            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder().uri(uri).method("HEAD", HttpRequest.BodyPublishers.noBody());

            // Apply authentication if provided
            if (authentication != null) {
                requestBuilder = authentication.authenticate(httpClient, requestBuilder);
            }

            HttpRequest headRequest = requestBuilder.build();
            HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());

            int statusCode = headResponse.statusCode();
            if (statusCode == 401 || statusCode == 403) {
                throw new IOException("Authentication failed for URI: " + uri + ", status code: " + statusCode);
            } else if (statusCode != 200) {
                throw new IOException("Failed to connect to URI: " + uri + ", status code: " + statusCode);
            }

            // Check for range support (optional, we'll still try if not advertised)
            Optional<String> acceptRanges = headResponse.headers().firstValue("Accept-Ranges");
            if (acceptRanges.isPresent() && acceptRanges.get().equals("none")) {
                // Server explicitly doesn't support ranges
                throw new IOException("Server does not support range requests");
            }

            // Get content length
            Optional<String> contentLengthHeader = headResponse.headers().firstValue("Content-Length");
            if (contentLengthHeader.isPresent()) {
                try {
                    this.contentLength = Long.parseLong(contentLengthHeader.get());
                } catch (NumberFormatException e) {
                    // Ignore, we'll fetch it later if needed
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted", e);
        }
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Range", "bytes=" + offset + "-" + (offset + actualLength - 1))
                .GET();

        // Apply authentication if provided
        if (authentication != null) {
            requestBuilder = authentication.authenticate(httpClient, requestBuilder);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            if (statusCode == 401 || statusCode == 403) {
                throw new IOException("Authentication failed for URI: " + uri + ", status code: " + statusCode);
            } else if (statusCode != 206 && statusCode != 200) {
                throw new IOException("Failed to get range from URI: " + uri + ", status code: " + statusCode);
            }

            byte[] responseBody = response.body();
            int bytesRead;

            // Handle case where server ignores range request and sends entire file
            if (statusCode == 200 && responseBody.length > actualLength) {
                // Extract just the range we requested and put into target buffer
                target.put(responseBody, (int) offset, actualLength);
                bytesRead = actualLength;
            } else {
                // Put the response directly into the target buffer
                target.put(responseBody);
                bytesRead = responseBody.length;
            }

            return bytesRead;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted", e);
        }
    }

    @Override
    public long size() throws IOException {
        if (contentLength < 0) {
            // Fetch content length if we don't have it
            try {
                HttpRequest.Builder requestBuilder =
                        HttpRequest.newBuilder().uri(uri).method("HEAD", HttpRequest.BodyPublishers.noBody());

                // Apply authentication if provided
                if (authentication != null) {
                    requestBuilder = authentication.authenticate(httpClient, requestBuilder);
                }

                HttpRequest headRequest = requestBuilder.build();
                HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());

                int statusCode = headResponse.statusCode();
                if (statusCode == 401 || statusCode == 403) {
                    throw new IOException("Authentication failed for URI: " + uri + ", status code: " + statusCode);
                } else if (statusCode != 200) {
                    throw new IOException("Failed to get content length from URI: " + uri);
                }

                Optional<String> contentLengthHeader = headResponse.headers().firstValue("Content-Length");
                if (contentLengthHeader.isPresent()) {
                    try {
                        contentLength = Long.parseLong(contentLengthHeader.get());
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid content length header: " + contentLengthHeader.get());
                    }
                } else {
                    throw new IOException("Content length header missing");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request was interrupted", e);
            }
        }

        return contentLength;
    }

    @Override
    public void close() {
        // HttpClient is self-managed, no explicit close needed
    }

    /**
     * Creates a new builder for HttpRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for HttpRangeReader.
     */
    public static class Builder {
        private URI uri;
        private boolean trustAllCertificates = false;
        private io.tileverse.rangereader.http.HttpAuthentication authentication;

        private Builder() {}

        /**
         * Sets the HTTP URI.
         *
         * @param uri the HTTP URI
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");
            String scheme = uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("URI must have http or https scheme: " + uri);
            }
            this.uri = uri;
            return this;
        }

        /**
         * Enables trusting all SSL certificates.
         *
         * @return this builder
         */
        public Builder trustAllCertificates() {
            this.trustAllCertificates = true;
            return this;
        }

        /**
         * Sets HTTP authentication.
         *
         * @param authentication the authentication
         * @return this builder
         */
        public Builder authentication(io.tileverse.rangereader.http.HttpAuthentication authentication) {
            this.authentication = Objects.requireNonNull(authentication, "Authentication cannot be null");
            return this;
        }

        /**
         * Sets HTTP Basic Authentication.
         *
         * @param username the username
         * @param password the password
         * @return this builder
         */
        public Builder basicAuth(String username, String password) {
            this.authentication = new io.tileverse.rangereader.http.BasicAuthentication(username, password);
            return this;
        }

        /**
         * Sets HTTP Bearer Token Authentication.
         *
         * @param token the bearer token
         * @return this builder
         */
        public Builder bearerToken(String token) {
            this.authentication = new io.tileverse.rangereader.http.BearerTokenAuthentication(token);
            return this;
        }

        /**
         * Sets API Key Authentication with a custom header.
         *
         * @param headerName the name of the header (e.g., "X-API-Key")
         * @param apiKey the API key value
         * @return this builder
         */
        public Builder apiKey(String headerName, String apiKey) {
            this.authentication = new io.tileverse.rangereader.http.ApiKeyAuthentication(headerName, apiKey);
            return this;
        }

        /**
         * Sets API Key Authentication with a custom header and value prefix.
         *
         * @param headerName the name of the header (e.g., "Authorization")
         * @param apiKey the API key value
         * @param valuePrefix the prefix for the API key value (e.g., "ApiKey ")
         * @return this builder
         */
        public Builder apiKey(String headerName, String apiKey, String valuePrefix) {
            this.authentication =
                    new io.tileverse.rangereader.http.ApiKeyAuthentication(headerName, apiKey, valuePrefix);
            return this;
        }

        /**
         * Builds the HttpRangeReader.
         *
         * @return a new HttpRangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public HttpRangeReader build() throws IOException {
            if (uri == null) {
                throw new IllegalStateException("URI must be set");
            }

            if (authentication != null) {
                return new HttpRangeReader(uri, trustAllCertificates, authentication);
            } else {
                return new HttpRangeReader(uri, trustAllCertificates);
            }
        }
    }
}
