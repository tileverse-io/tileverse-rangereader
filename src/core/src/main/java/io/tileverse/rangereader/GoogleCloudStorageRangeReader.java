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
package io.tileverse.rangereader;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A RangeReader implementation that reads from Google Cloud Storage.
 * <p>
 * This class enables reading data stored in Google Cloud Storage buckets using the
 * Google Cloud Storage client library for Java.
 */
public class GoogleCloudStorageRangeReader extends AbstractRangeReader implements RangeReader {

    private final Storage storage;
    private final String bucket;
    private final String objectName;
    private long contentLength = -1;

    /**
     * Creates a new GoogleCloudStorageRangeReader for the specified GCS object.
     *
     * @param storage The GCS Storage client to use
     * @param bucket The GCS bucket name
     * @param objectName The GCS object name
     * @throws IOException If an I/O error occurs
     */
    public GoogleCloudStorageRangeReader(Storage storage, String bucket, String objectName) throws IOException {
        this.storage = Objects.requireNonNull(storage, "Storage client cannot be null");
        this.bucket = Objects.requireNonNull(bucket, "Bucket name cannot be null");
        this.objectName = Objects.requireNonNull(objectName, "Object name cannot be null");

        // Check if the object exists and get its content length
        try {
            BlobId blobId = BlobId.of(bucket, objectName);
            Blob blob = storage.get(blobId);

            if (blob == null || !blob.exists()) {
                throw new IOException("GCS object does not exist: gs://" + bucket + "/" + objectName);
            }

            this.contentLength = blob.getSize();
        } catch (StorageException e) {
            throw new IOException("Failed to access GCS object: " + e.getMessage(), e);
        }
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        try {
            BlobId blobId = BlobId.of(bucket, objectName);
            Blob blob = storage.get(blobId);

            if (blob == null) {
                throw new IOException("GCS object not found: gs://" + bucket + "/" + objectName);
            }

            // Read the specified range from GCS using readChannelWithResponse
            try (var reader = blob.reader()) {
                reader.seek(offset);
                byte[] data = new byte[actualLength];
                int totalBytesRead = 0;
                while (totalBytesRead < actualLength) {
                    int bytesRead = reader.read(ByteBuffer.wrap(data, totalBytesRead, actualLength - totalBytesRead));
                    if (bytesRead == -1) {
                        // End of file reached
                        break;
                    }
                    totalBytesRead += bytesRead;
                }

                // Truncate data if we read less than expected (end of file)
                if (totalBytesRead < actualLength) {
                    byte[] truncatedData = new byte[totalBytesRead];
                    System.arraycopy(data, 0, truncatedData, 0, totalBytesRead);
                    data = truncatedData;
                }

                // Put the bytes directly into the target buffer
                target.put(data);

                // Return the number of bytes read
                return data.length;
            }
        } catch (StorageException e) {
            throw new IOException("Failed to read range from GCS: " + e.getMessage(), e);
        }
    }

    @Override
    public long size() throws IOException {
        if (contentLength < 0) {
            try {
                BlobId blobId = BlobId.of(bucket, objectName);
                Blob blob = storage.get(blobId);

                if (blob == null) {
                    throw new IOException("GCS object not found: gs://" + bucket + "/" + objectName);
                }

                contentLength = blob.getSize();
            } catch (StorageException e) {
                throw new IOException("Failed to get GCS object size: " + e.getMessage(), e);
            }
        }
        return contentLength;
    }

    @Override
    public void close() {
        // Google Cloud Storage client is typically managed externally and should be closed by the caller
    }

    /**
     * Creates a new builder for GoogleCloudStorageRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for GoogleCloudStorageRangeReader.
     */
    public static class Builder {
        private Storage storage;
        private String projectId;
        private String bucket;
        private String objectName;

        private Builder() {}

        /**
         * Sets the Google Cloud Storage client to use.
         *
         * @param storage the Storage client
         * @return this builder
         */
        public Builder storage(Storage storage) {
            this.storage = Objects.requireNonNull(storage, "Storage cannot be null");
            return this;
        }

        /**
         * Sets the Google Cloud project ID.
         *
         * @param projectId the project ID
         * @return this builder
         */
        public Builder projectId(String projectId) {
            this.projectId = Objects.requireNonNull(projectId, "Project ID cannot be null");
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
         * Sets the object name.
         *
         * @param objectName the object name
         * @return this builder
         */
        public Builder objectName(String objectName) {
            this.objectName = Objects.requireNonNull(objectName, "Object name cannot be null");
            return this;
        }

        /**
         * Sets the bucket and object from a GCS URI.
         *
         * @param uri the GCS URI (gs://bucket/object)
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");

            if (!uri.getScheme().equalsIgnoreCase("gs")) {
                throw new IllegalArgumentException("URI must have gs scheme: " + uri);
            }

            String bucketName = uri.getAuthority();
            if (bucketName == null || bucketName.isEmpty()) {
                throw new IllegalArgumentException("GCS URI must have a bucket: " + uri);
            }

            String pathStr = uri.getPath();
            if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
                throw new IllegalArgumentException("GCS URI must have an object name: " + uri);
            }

            // Remove leading slash from path to get the object name
            String object = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

            this.bucket = bucketName;
            this.objectName = object;
            return this;
        }

        /**
         * Builds the GoogleCloudStorageRangeReader.
         *
         * @return a new GoogleCloudStorageRangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public GoogleCloudStorageRangeReader build() throws IOException {
            if (bucket == null || objectName == null) {
                throw new IllegalStateException("Bucket and object name must be set");
            }

            Storage storageClient = storage;
            if (storageClient == null) {
                if (projectId != null) {
                    storageClient = StorageOptions.newBuilder()
                            .setProjectId(projectId)
                            .build()
                            .getService();
                } else {
                    storageClient = StorageOptions.getDefaultInstance().getService();
                }
            }

            return new GoogleCloudStorageRangeReader(storageClient, bucket, objectName);
        }
    }
}
