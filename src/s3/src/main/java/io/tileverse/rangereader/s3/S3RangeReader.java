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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
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
    private final String bucket;
    private final String key;
    private long contentLength = -1;

    /**
     * Creates a new S3RangeReader for the specified S3 object.
     *
     * @param s3Client The S3 client to use
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @throws IOException If an I/O error occurs
     */
    public S3RangeReader(S3Client s3Client, String bucket, String key) throws IOException {
        this.s3Client = Objects.requireNonNull(s3Client, "S3Client cannot be null");
        this.bucket = Objects.requireNonNull(bucket, "Bucket name cannot be null");
        this.key = Objects.requireNonNull(key, "Object key cannot be null");

        // Check if the object exists and get its content length
        try {
            HeadObjectRequest headRequest =
                    HeadObjectRequest.builder().bucket(bucket).key(key).build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            this.contentLength = headResponse.contentLength();
        } catch (NoSuchKeyException e) {
            throw new IOException("S3 object does not exist: s3://" + bucket + "/" + key, e);
        } catch (SdkException e) {
            throw new IOException("Failed to access S3 object: " + e.getMessage(), e);
        }
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        long rangeEnd = offset + actualLength - 1;

        try {
            // Request the specified range from S3
            GetObjectRequest rangeRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
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
    public long size() throws IOException {
        if (contentLength < 0) {
            try {
                HeadObjectRequest headRequest =
                        HeadObjectRequest.builder().bucket(bucket).key(key).build();

                HeadObjectResponse headResponse = s3Client.headObject(headRequest);
                contentLength = headResponse.contentLength();
            } catch (SdkException e) {
                throw new IOException("Failed to get S3 object size: " + e.getMessage(), e);
            }
        }
        return contentLength;
    }

    @Override
    public String getSourceIdentifier() {
        return "s3://" + bucket + "/" + key;
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
        private S3Client s3Client;
        private AwsCredentialsProvider credentialsProvider;
        private Region region;
        private URI endpoint;
        private boolean forcePathStyle = false;
        private String bucket;
        private String key;

        private Builder() {}

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
            this.endpoint = Objects.requireNonNull(endpoint, "Endpoint cannot be null");
            return this;
        }

        /**
         * Enables force path style for S3 client.
         *
         * @return this builder
         */
        public Builder forcePathStyle() {
            this.forcePathStyle = true;
            return this;
        }

        /**
         * Sets the bucket name.
         *
         * @param bucket the bucket name
         * @return this builder
         */
        public Builder bucket(String bucket) {
            this.bucket = Objects.requireNonNull(bucket, "Bucket cannot be null");
            return this;
        }

        /**
         * Sets the object key.
         *
         * @param key the object key
         * @return this builder
         */
        public Builder key(String key) {
            this.key = Objects.requireNonNull(key, "Key cannot be null");
            return this;
        }

        /**
         * Sets the bucket and key from an S3 URI.
         *
         * @param uri the S3 URI (s3://bucket/key)
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");

            if (!uri.getScheme().equalsIgnoreCase("s3")) {
                throw new IllegalArgumentException("URI must have s3 scheme: " + uri);
            }

            String bucketName = uri.getAuthority();
            if (bucketName == null || bucketName.isEmpty()) {
                throw new IllegalArgumentException("S3 URI must have a bucket: " + uri);
            }

            String pathStr = uri.getPath();
            if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
                throw new IllegalArgumentException("S3 URI must have a key: " + uri);
            }

            // Remove leading slash from path to get the key
            String objectKey = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

            this.bucket = bucketName;
            this.key = objectKey;

            // Extract region from fragment if present
            if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
                this.region = Region.of(uri.getFragment());
            }

            return this;
        }

        /**
         * Builds the S3RangeReader.
         *
         * @return a new S3RangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public S3RangeReader build() throws IOException {
            if (bucket == null || key == null) {
                throw new IllegalStateException("Bucket and key must be set");
            }

            S3Client client = s3Client;
            if (client == null) {
                // Build S3 client
                var builder = S3Client.builder();

                if (credentialsProvider != null) {
                    builder.credentialsProvider(credentialsProvider);
                } else {
                    builder.credentialsProvider(
                            DefaultCredentialsProvider.builder().build());
                }

                if (region != null) {
                    builder.region(region);
                }

                if (endpoint != null) {
                    builder.endpointOverride(endpoint);
                }

                if (forcePathStyle) {
                    builder.forcePathStyle(true);
                }

                client = builder.build();
            }

            return new S3RangeReader(client, bucket, key);
        }
    }
}
