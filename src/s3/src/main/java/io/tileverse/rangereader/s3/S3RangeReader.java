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
package io.tileverse.rangereader.s3;

import io.tileverse.rangereader.AbstractRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.OptionalLong;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * A RangeReader implementation that reads from AWS S3.
 * <p>
 * This class enables reading data stored in S3 buckets using the
 * AWS SDK for Java v2.
 */
public class S3RangeReader extends AbstractRangeReader implements RangeReader {

    private final S3Client s3Client;
    private final S3Reference s3Location;

    private final OptionalLong contentLength;

    /**
     * Creates a new S3RangeReader for the specified S3 object.
     *
     * @param s3Client The S3 client to use
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @throws IOException If an I/O error occurs
     */
    S3RangeReader(S3Client s3Client, S3Reference s3Location) throws IOException {
        this.s3Client = Objects.requireNonNull(s3Client, "S3Client cannot be null");
        this.s3Location = Objects.requireNonNull(s3Location, "S3Location cannot be null");

        // Check if the object exists and get its content length
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(s3Location.bucket())
                    .key(s3Location.key())
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            Long size = headResponse.contentLength();
            this.contentLength = size == null ? OptionalLong.empty() : OptionalLong.of(size);
        } catch (NoSuchKeyException e) {
            throw new IOException("S3 object does not exist: s3://" + s3Location, e);
        } catch (SdkException e) {
            throw new IOException("Failed to access S3 object " + s3Location + ": " + e.getMessage(), e);
        }
    }

    S3RangeReader(S3Client client, String bucketName, String keyName) throws IOException {
        this(client, new S3Reference(null, bucketName, keyName, null));
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        long rangeEnd = offset + actualLength - 1;

        try {
            // Request the specified range from S3
            GetObjectRequest rangeRequest = GetObjectRequest.builder()
                    .bucket(s3Location.bucket())
                    .key(s3Location.key())
                    .range("bytes=" + offset + "-" + rangeEnd)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(rangeRequest);

            // Validate that we got the expected content length
            if (objectBytes.response().contentLength() != actualLength) {
                throw new IOException("Unexpected content length: got "
                        + objectBytes.response().contentLength()
                        + ", expected "
                        + actualLength);
            }

            // Put the bytes directly into the target buffer
            byte[] data = objectBytes.asByteArray();
            target.put(data);

            // Return the number of bytes read
            return data.length;
        } catch (SdkException e) {
            throw new IOException("Failed to read range from S3: " + e.getMessage(), e);
        }
    }

    @Override
    public OptionalLong size() throws IOException {
        return contentLength;
    }

    @Override
    public String getSourceIdentifier() {
        return s3Location.toString();
    }

    @Override
    public void close() {
        // S3Client is typically managed externally and should be closed by the caller
    }

    /**
     * Creates a new builder for S3RangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for S3RangeReader.
     */
    public static class Builder {
        private S3Reference s3Location;
        private S3Client s3Client;

        private AwsCredentialsProvider credentialsProvider;
        private boolean forcePathStyle;
        private Region region;
        private String awsAccessKeyId;
        private String awsSecretAccessKey;

        private Builder() {
            // Initialize with empty S3Location
            this.s3Location = new S3Reference();
        }

        /**
         * Sets the AWS access key ID.
         *
         * @param awsAccessKeyId the AWS access key ID
         * @return this builder
         */
        public Builder awsAccessKeyId(String awsAccessKeyId) {
            this.awsAccessKeyId = awsAccessKeyId;
            return this;
        }

        /**
         * Sets the AWS secret access key.
         *
         * @param awsSecretAccessKey the AWS secret access key
         * @return this builder
         */
        public Builder awsSecretAccessKey(String awsSecretAccessKey) {
            this.awsSecretAccessKey = awsSecretAccessKey;
            return this;
        }

        /**
         * Sets the S3 client to use.
         *
         * @param s3Client the S3 client
         * @return this builder
         */
        public Builder s3Client(S3Client s3Client) {
            this.s3Client = Objects.requireNonNull(s3Client, "S3Client cannot be null");
            return this;
        }

        /**
         * Sets the AWS credentials provider.
         *
         * @param credentialsProvider the credentials provider
         * @return this builder
         */
        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider =
                    Objects.requireNonNull(credentialsProvider, "Credentials provider cannot be null");
            return this;
        }

        /**
         * Sets the AWS region.
         *
         * @param region the AWS region
         * @return this builder
         */
        public Builder region(Region region) {
            this.region = Objects.requireNonNull(region, "Region cannot be null");
            return this;
        }

        /**
         * Sets a custom endpoint URI.
         *
         * @param endpoint the endpoint URI
         * @return this builder
         */
        public Builder endpoint(URI endpoint) {
            Objects.requireNonNull(endpoint, "Endpoint cannot be null");
            this.s3Location = this.s3Location.withEndpoint(endpoint);
            return this;
        }

        /**
         * Enables force path style for S3 client.
         * <p>
         * Note: When using the {@link #uri(URI)} method, path style will be automatically
         * enabled for non-AWS S3-compatible services (such as MinIO, Google Cloud Storage, etc.)
         * that require path-style addressing. This method allows explicit override of that behavior.
         *
         * @return this builder
         */
        public Builder forcePathStyle() {
            return forcePathStyle(true);
        }

        /**
         * Enable or disable S3 path style access. When enabled, requests will use
         * path-style addressing (e.g., {@code https://s3.amazonaws.com/bucket/key}). When disabled, virtual-hosted-style
         * addressing will be used instead (e.g., {@code https://bucket.s3.amazonaws.com/key}). This can be useful for
         * compatibility with S3-compatible storage systems that do not support virtual-hosted-style requests.
         * <p>
         * <strong>Automatic Detection:</strong> When using the {@link #uri(URI)} method, path style is automatically
         * enabled for non-AWS endpoints (detected via {@link S3Reference#requiresPathStyle()}). This method allows
         * explicit override of the automatic detection.
         * <p>
         * <strong>Examples:</strong>
         * <pre>{@code
         * // Automatic path style detection
         * builder.uri("http://localhost:9000/bucket/key")  // Automatically enables path style
         *
         * // Manual override
         * builder.uri("http://localhost:9000/bucket/key")
         *        .forcePathStyle(false)                    // Override automatic detection
         *
         * // AWS endpoints default to virtual hosted-style
         * builder.uri("https://bucket.s3.amazonaws.com/key")  // Path style remains false
         * }</pre>
         *
         * @param forcePathStyle whether to enable (true) or disable path style, defaults to {@code false}
         * @return this builder
         */
        public Builder forcePathStyle(boolean forcePathStyle) {
            this.forcePathStyle = forcePathStyle;
            return this;
        }

        /**
         * Sets the bucket name.
         *
         * @param bucket the bucket name
         * @return this builder
         */
        public Builder bucket(String bucket) {
            Objects.requireNonNull(bucket, "Bucket cannot be null");
            this.s3Location = this.s3Location.withBucket(bucket);
            return this;
        }

        /**
         * Sets the object key.
         *
         * @param key the object key
         * @return this builder
         */
        public Builder key(String key) {
            Objects.requireNonNull(key, "Key cannot be null");
            this.s3Location = this.s3Location.withKey(key);
            return this;
        }

        /**
         * Sets the bucket, key, and endpoint from an S3 URI.
         *
         * <p>Examples:
         * <pre>{@code
         * // AWS S3
         * builder.uri(URI.create("s3://my-bucket/path/file.txt"));
         * builder.uri(URI.create("https://my-bucket.s3.us-west-2.amazonaws.com/path/file.txt"));
         *
         * // MinIO - extracts custom endpoint and enables path-style
         * builder.uri(URI.create("http://localhost:9000/my-bucket/path/file.txt"));
         *
         * // Other S3-compatible services
         * builder.uri(URI.create("https://storage.googleapis.com/my-bucket/path/file.txt"));
         * }</pre>
         *
         * @param uri the S3 URI (s3|http|https://bucket/key)
         * @throws IllegalArgumentException if the uri scheme is not supported, or the bucket name and key can't be extracted from the URI
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");
            this.s3Location = S3CompatibleUrlParser.parseS3Url(uri);
            return this;
        }

        /**
         * Sets the bucket, key, and endpoint from an S3 URL string.
         *
         * @param uri the S3 URL string
         * @return this builder
         * @see #uri(URI)
         */
        public Builder uri(String uri) {
            return uri(URI.create(uri));
        }

        /**
         * Builds the S3RangeReader.
         *
         * @return a new S3RangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public S3RangeReader build() throws IOException {
            if (s3Location.bucket() == null || s3Location.key() == null) {
                throw new IllegalStateException("Bucket and key must be set");
            }

            S3Client client = s3Client;
            if (client == null) {
                // Build S3 client
                S3ClientBuilder clientBuilder = S3Client.builder();

                if (credentialsProvider != null) {
                    clientBuilder.credentialsProvider(credentialsProvider);
                } else if (awsAccessKeyId != null && awsSecretAccessKey != null) {
                    clientBuilder.credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)));
                } else {
                    clientBuilder.credentialsProvider(
                            DefaultCredentialsProvider.builder().build());
                }

                if (region != null) {
                    clientBuilder.region(region);
                } else if (s3Location.region() != null) {
                    // Use region parsed from URL if no explicit region was set
                    clientBuilder.region(Region.of(s3Location.region()));
                }

                if (s3Location.endpoint() != null) {
                    clientBuilder.endpointOverride(s3Location.endpoint());
                }

                if (s3Location.requiresPathStyle() || forcePathStyle) {
                    clientBuilder.forcePathStyle(true);
                }

                try {
                    client = clientBuilder.build();
                } catch (SdkException e) {
                    throw new IOException("Failed to create S3 client: " + e.getMessage(), e);
                }
            }

            return new S3RangeReader(client, s3Location);
        }
    }
}
