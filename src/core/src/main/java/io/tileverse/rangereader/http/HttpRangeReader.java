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
import java.net.URISyntaxException;
import java.net.URL;
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
import java.util.function.Consumer;
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
    private volatile long contentLength = -1;
    private volatile boolean sizeInitialized = false;
    private volatile boolean rangeInitialized = false;
    private volatile HttpResponse<Void> cachedHeadResponse = null;

    /**
     * Creates a new HttpRangeReader with authentication and control over SSL certificate validation.
     *
     * @param uri The URI to read from
     * @param trustAllCertificates Whether to trust all SSL certificates
     * @param authentication The authentication mechanism to use
     */
    HttpRangeReader(@NonNull URI uri, boolean trustAllCertificates, HttpAuthentication authentication) {
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
     * Creates a new HttpRangeReader with a custom HTTP client.
     *
     * @param uri The URI to read from
     * @param httpClient The HttpClient to use
     */
    HttpRangeReader(URI uri, HttpClient httpClient) {
        this(uri, httpClient, null);
    }

    /**
     * Creates a new HttpRangeReader with a custom HTTP client and authentication.
     *
     * @param uri The URI to read from
     * @param httpClient The HttpClient to use
     * @param authentication The authentication mechanism to use, or null for no authentication
     */
    HttpRangeReader(@NonNull URI uri, @NonNull HttpClient httpClient, HttpAuthentication authentication) {
        this.uri = uri;
        this.httpClient = httpClient;
        this.authentication = authentication; // Can be null for no authentication
        // Content length and range support will be checked when size() is first called
    }

    /**
     * Creates an HttpClient that accepts all SSL certificates.
     *
     * @return An HttpClient configured to trust all SSL certificates
     * @throws IOException If there's an error setting up the SSL context
     */
    private static HttpClient createTrustAllHttpClient() {
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
            throw new IllegalArgumentException("Failed to create SSL context for HTTPS", e);
        }
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        checkServerSupportsByteRanges();

        // Track initial position to calculate bytes read
        int initialPosition = target.position();

        try {
            HttpResponse<Void> response = sendRangeRequest(offset, actualLength, target);

            checkStatusCode(response);

            // Calculate actual bytes read by comparing positions
            return target.position() - initialPosition;

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Response body larger than expected")) {
                throw new IOException("Server returned more data than requested range length", e);
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted", e);
        }
    }

    private HttpResponse<Void> sendRangeRequest(final long offset, final int actualLength, ByteBuffer targetBuffer)
            throws IOException, InterruptedException {

        final long start = System.nanoTime();
        final HttpRequest request = buildRangeRequest(offset, actualLength);

        Consumer<Optional<byte[]>> bodyConsumer = createStreamingConsumer(targetBuffer, actualLength);
        HttpResponse<Void> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArrayConsumer(bodyConsumer));

        if (LOGGER.isLoggable(Level.FINE)) {
            final long end = System.nanoTime();
            final long millis = Duration.ofNanos(end - start).toMillis();
            LOGGER.fine("range:[%,d +%,d], time: %,dms]".formatted(offset, actualLength, millis));
        }
        return response;
    }

    /**
     * Creates a consumer for streaming HTTP response data into a ByteBuffer.
     * The consumer handles multiple calls as data chunks arrive from the network.
     * <p>
     * Since the Javadocs for {@code HttpResponse.BodyHandlers.ofByteArrayConsumer()} are not very clear:
     * <ul>
     * <li>The consumer can be called multiple times as data chunks arrive from the network
     * <li>Each call receives an Optional<byte[]> containing a chunk of the response data
     * <li>An empty Optional indicates the end of the stream
     * </ul>
     * @param targetBuffer the buffer to write response data into
     * @param expectedLength the expected total length of the response
     * @return a consumer that processes streaming response chunks
     */
    private Consumer<Optional<byte[]>> createStreamingConsumer(ByteBuffer targetBuffer, int expectedLength) {
        return optionalBytes -> {
            if (optionalBytes.isPresent()) {
                byte[] chunk = optionalBytes.get();
                if (targetBuffer.remaining() >= chunk.length) {
                    targetBuffer.put(chunk);
                } else {
                    // Buffer overflow - response is larger than expected
                    throw new RuntimeException("Response body larger than expected range length. Expected: "
                            + expectedLength + ", received chunk size: "
                            + chunk.length + ", remaining buffer space: "
                            + targetBuffer.remaining());
                }
            }
        };
    }

    private void checkStatusCode(HttpResponse<Void> response) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new IOException("Authentication failed for URI: " + uri + ", status code: " + statusCode);
        } else if (statusCode != 206) {
            throw new IOException("Failed to get range from URI: " + uri + ", status code: " + statusCode);
        }
    }

    private HttpRequest buildRangeRequest(final long offset, final int actualLength) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Range", "bytes=" + offset + "-" + (offset + actualLength - 1))
                .GET();

        // Apply authentication if provided
        if (authentication != null) {
            requestBuilder = authentication.authenticate(httpClient, requestBuilder);
        }

        return requestBuilder.build();
    }

    private void checkServerSupportsByteRanges() throws IOException {
        // Initialize range support on first read
        if (!rangeInitialized) {
            synchronized (this) {
                if (!rangeInitialized) {
                    initializeRangeSupport();
                    rangeInitialized = true;
                }
            }
        }
    }

    @Override
    public long size() throws IOException {
        if (!sizeInitialized) {
            synchronized (this) {
                if (!sizeInitialized) {
                    initializeSize();
                    sizeInitialized = true;
                }
            }
        }
        return contentLength;
    }

    /**
     * Makes a HEAD request to the server and caches the response for reuse.
     * This method is thread-safe and ensures the HEAD request is made only once.
     *
     * @return the cached HEAD response
     * @throws IOException If an I/O error occurs during the HEAD request
     */
    private HttpResponse<Void> getHeadResponse() throws IOException {
        if (cachedHeadResponse == null) {
            synchronized (this) {
                if (cachedHeadResponse == null) {
                    try {
                        HttpRequest.Builder requestBuilder =
                                HttpRequest.newBuilder().uri(uri).method("HEAD", HttpRequest.BodyPublishers.noBody());

                        // Apply authentication if provided
                        if (authentication != null) {
                            requestBuilder = authentication.authenticate(httpClient, requestBuilder);
                        }

                        HttpRequest headRequest = requestBuilder.build();
                        HttpResponse<Void> headResponse =
                                httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());

                        int statusCode = headResponse.statusCode();
                        if (statusCode == 401 || statusCode == 403) {
                            throw new IOException(
                                    "Authentication failed for URI: " + uri + ", status code: " + statusCode);
                        } else if (statusCode != 200) {
                            throw new IOException("Failed to connect to URI: " + uri + ", status code: " + statusCode);
                        }

                        cachedHeadResponse = headResponse;

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request was interrupted", e);
                    }
                }
            }
        }
        return cachedHeadResponse;
    }

    /**
     * Initializes the content length from the cached HEAD response.
     *
     * @throws IOException If an I/O error occurs or the content length is invalid
     */
    private void initializeSize() throws IOException {
        HttpResponse<Void> headResponse = getHeadResponse();

        // Get content length
        Optional<String> contentLengthHeader = headResponse.headers().firstValue("Content-Length");
        if (contentLengthHeader.isPresent()) {
            try {
                this.contentLength = Long.parseLong(contentLengthHeader.get());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid content length header: " + contentLengthHeader.get());
            }
        } else {
            throw new IOException("Content length header missing");
        }
    }

    /**
     * Initializes range support by checking the Accept-Ranges header from the cached HEAD response.
     *
     * @throws IOException If the server doesn't support range requests
     */
    private void initializeRangeSupport() throws IOException {
        HttpResponse<Void> headResponse = getHeadResponse();

        // Check for explicit range support denial
        Optional<String> acceptRanges = headResponse.headers().firstValue("Accept-Ranges");
        if (acceptRanges.isPresent() && acceptRanges.get().equals("none")) {
            throw new IOException("Server explicitly does not support range requests (Accept-Ranges: none)");
        }

        // If Accept-Ranges: bytes is present, range requests are supported
        // If Accept-Ranges header is absent, we'll assume range support and let readRangeNoFlip() handle any errors
    }

    @Override
    public String getSourceIdentifier() {
        return uri.toString();
    }

    @Override
    public void close() {
        // HttpClient is self-managed, no explicit close needed
    }

    /**
     * Creates a new HttpRangeReader for the specified URL string with default settings.
     *
     * <p>This is the simplest way to create an HttpRangeReader for basic HTTP/HTTPS access
     * without authentication or custom SSL configuration. Uses default SSL certificate
     * validation (does not trust all certificates).
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * HttpRangeReader.builder(url).build();
     * }</pre>
     *
     * @param url the HTTP or HTTPS URL string to read from
     * @return a new HttpRangeReader instance with default configuration
     * @throws IllegalArgumentException if the URL is malformed or has an unsupported scheme
     */
    public static HttpRangeReader of(String url) {
        return builder(url).build();
    }

    /**
     * Creates a new HttpRangeReader for the specified URI with default settings.
     *
     * <p>This is the simplest way to create an HttpRangeReader for basic HTTP/HTTPS access
     * without authentication or custom SSL configuration. Uses default SSL certificate
     * validation (does not trust all certificates).
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * HttpRangeReader.builder(uri).build();
     * }</pre>
     *
     * @param url the HTTP or HTTPS URI to read from
     * @return a new HttpRangeReader instance with default configuration
     * @throws IllegalArgumentException if the URI has an unsupported scheme (must be http or https)
     */
    public static HttpRangeReader of(URI url) {
        return builder(url).build();
    }

    /**
     * Creates a new HttpRangeReader for the specified URL with default settings.
     *
     * <p>This is a convenience method for creating an HttpRangeReader from a java.net.URL
     * instance. The URL is converted to a URI internally. Uses default SSL certificate
     * validation (does not trust all certificates).
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * HttpRangeReader.builder(url.toURI()).build();
     * }</pre>
     *
     * @param url the HTTP or HTTPS URL to read from
     * @return a new HttpRangeReader instance with default configuration
     * @throws IllegalArgumentException if the URL cannot be converted to a valid URI
     *                                  or has an unsupported scheme (must be http or https)
     */
    public static HttpRangeReader of(URL url) {
        try {
            return builder(url.toURI()).build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
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
     * Create a builder for the given URL
     * @param url the URL to pre configure the builder for
     * @return builder ready for url
     */
    public static Builder builder(String url) {
        return new Builder(URI.create(url));
    }

    /**
     * Create a builder for the given URL
     * @param url the URL to pre configure the builder for
     * @return builder ready for url
     */
    public static Builder builder(URI url) {
        return new Builder(url);
    }

    /**
     * Builder for HttpRangeReader.
     */
    public static class Builder {
        private URI uri;
        private boolean trustAllCertificates = false;
        private io.tileverse.rangereader.http.HttpAuthentication authentication;

        private Builder() {}

        public Builder(URI uri) {
            this.uri = uri;
        }

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
         */
        public HttpRangeReader build() {
            if (uri == null) {
                throw new IllegalStateException("URI must be set");
            }

            return new HttpRangeReader(uri, trustAllCertificates, authentication);
        }
    }
}
