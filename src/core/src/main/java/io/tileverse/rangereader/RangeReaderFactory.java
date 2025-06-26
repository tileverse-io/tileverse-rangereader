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

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Factory class for creating RangeReader instances for different storage types.
 * <p>
 * This factory simplifies the creation of appropriate RangeReader implementations
 * based on the URI or path provided. It handles creating the necessary clients
 * for cloud storage services.
 */
public class RangeReaderFactory {

    private RangeReaderFactory() {
        // Utility class, prevent instantiation
    }

    /**
     * Creates a RangeReader for the given URI.
     * <p>
     * This method detects the type of URI and creates the appropriate RangeReader:
     * - file:// URIs create a FileRangeReader
     * - http:// or https:// URIs create an HttpRangeReader
     * - s3:// URIs create an S3RangeReader with default client settings
     * - azure:// URIs create an AzureBlobRangeReader with default client settings
     * - gs:// URIs create a GoogleCloudStorageRangeReader with default client settings
     *
     * @param uri The URI to create a RangeReader for
     * @return A RangeReader implementation appropriate for the URI scheme
     * @throws IOException If an I/O error occurs
     * @throws IllegalArgumentException If the URI scheme is not supported
     */
    public static RangeReader create(URI uri) throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URI must have a scheme: " + uri);
        }

        switch (scheme.toLowerCase()) {
            case "file":
                return new FileRangeReader(java.nio.file.Paths.get(uri));

            case "http":
            case "https":
                return new HttpRangeReader(uri);

            case "s3":
                // Parse bucket and key from S3 URI (s3://bucket/key)
                String authority = uri.getAuthority();
                if (authority == null || authority.isEmpty()) {
                    throw new IllegalArgumentException("S3 URI must have a bucket: " + uri);
                }

                String pathStr = uri.getPath();
                if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
                    throw new IllegalArgumentException("S3 URI must have a key: " + uri);
                }

                // Remove leading slash from path to get the key
                String key = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

                // Create S3 client with default credential provider
                return createS3RangeReader(
                        uri,
                        authority,
                        key,
                        DefaultCredentialsProvider.builder().build());

            case "azure":
                // Create Azure Blob client with default auth
                return createAzureBlobRangeReader(uri, scheme);

            case "gs":
                // Parse bucket and object name from GCS URI (gs://bucket/object)
                String gcsBucket = uri.getAuthority();
                if (gcsBucket == null || gcsBucket.isEmpty()) {
                    throw new IllegalArgumentException("GCS URI must have a bucket: " + uri);
                }

                String gcsPath = uri.getPath();
                if (gcsPath == null || gcsPath.isEmpty() || gcsPath.equals("/")) {
                    throw new IllegalArgumentException("GCS URI must have an object name: " + uri);
                }

                // Remove leading slash from path to get the object name
                String objectName = gcsPath.startsWith("/") ? gcsPath.substring(1) : gcsPath;

                // Create GCS Storage client with default settings
                return createGoogleCloudStorageRangeReader(gcsBucket, objectName);

            default:
                throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
        }
    }

    /**
     * Creates a caching RangeReader that wraps another RangeReader.
     * <p>
     * This is a convenience method for creating a CachingRangeReader.
     *
     * @param delegate The RangeReader to wrap with caching
     * @return A CachingRangeReader that caches ranges read from the delegate
     */
    public static RangeReader createCaching(RangeReader delegate) {
        return new CachingRangeReader(delegate);
    }

    public static RangeReader createCaching(RangeReader delegate, long maxSize) {
        return new CachingRangeReader(delegate, maxSize);
    }

    /**
     * Creates a block-aligned RangeReader that wraps another RangeReader.
     * <p>
     * This is a convenience method for creating a BlockAlignedRangeReader with the default block size.
     *
     * @param delegate The RangeReader to wrap with block alignment
     * @return A BlockAlignedRangeReader that aligns reads to block boundaries
     */
    public static RangeReader createBlockAligned(RangeReader delegate) {
        return new BlockAlignedRangeReader(delegate);
    }

    /**
     * Creates a block-aligned RangeReader that wraps another RangeReader.
     * <p>
     * This is a convenience method for creating a BlockAlignedRangeReader with a custom block size.
     *
     * @param delegate The RangeReader to wrap with block alignment
     * @param blockSize The block size to align reads to (must be a power of 2)
     * @return A BlockAlignedRangeReader that aligns reads to block boundaries
     */
    public static RangeReader createBlockAligned(RangeReader delegate, int blockSize) {
        return new BlockAlignedRangeReader(delegate, blockSize);
    }

    /**
     * Creates a block-aligned caching RangeReader.
     * <p>
     * This is a convenience method for creating a CachingRangeReader wrapped around a BlockAlignedRangeReader.
     * This is the recommended configuration for optimal performance with cloud storage.
     *
     * @param delegate The RangeReader to wrap
     * @return A CachingRangeReader wrapped around a BlockAlignedRangeReader
     */
    public static RangeReader createBlockAlignedCaching(RangeReader delegate) {
        return new CachingRangeReader(new BlockAlignedRangeReader(delegate));
    }

    /**
     * Creates a block-aligned caching RangeReader with a custom block size.
     * <p>
     * This is a convenience method for creating a CachingRangeReader wrapped around a BlockAlignedRangeReader
     * with a custom block size. This is the recommended configuration for optimal performance with cloud storage.
     *
     * @param delegate The RangeReader to wrap
     * @param blockSize The block size to align reads to (must be a power of 2)
     * @return A CachingRangeReader wrapped around a BlockAlignedRangeReader
     */
    public static RangeReader createBlockAlignedCaching(RangeReader delegate, int blockSize) {
        return new CachingRangeReader(new BlockAlignedRangeReader(delegate, blockSize));
    }

    /**
     * Creates an S3RangeReader for the specified URI with custom credentials.
     * <p>
     * This method allows providing a custom AWS credentials provider, which can be used
     * for different authentication methods like environment variables, profile credentials,
     * STS tokens, SSO, etc.
     *
     * @param uri The S3 URI (s3://bucket/key)
     * @param credentialsProvider The AWS credentials provider to use
     * @return An S3RangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createS3RangeReader(URI uri, AwsCredentialsProvider credentialsProvider)
            throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");
        Objects.requireNonNull(credentialsProvider, "Credentials provider cannot be null");

        if (!uri.getScheme().equalsIgnoreCase("s3")) {
            throw new IllegalArgumentException("URI must have s3 scheme: " + uri);
        }

        // Parse bucket and key from S3 URI (s3://bucket/key)
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("S3 URI must have a bucket: " + uri);
        }

        String pathStr = uri.getPath();
        if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
            throw new IllegalArgumentException("S3 URI must have a key: " + uri);
        }

        // Remove leading slash from path to get the key
        String key = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

        return createS3RangeReader(uri, authority, key, credentialsProvider);
    }

    /**
     * Internal helper to create an S3RangeReader with parsed components.
     */
    private static RangeReader createS3RangeReader(
            URI uri, String bucket, String key, AwsCredentialsProvider credentialsProvider) throws IOException {
        S3ClientBuilder builder = S3Client.builder().credentialsProvider(credentialsProvider);

        // If region specified in URI fragment, use it
        if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
            builder.region(Region.of(uri.getFragment()));
        }

        S3Client s3Client = builder.build();
        return new S3RangeReader(s3Client, bucket, key);
    }

    /**
     * Creates an S3RangeReader for the specified URI with a specific region.
     * <p>
     * This method allows providing a specific AWS region, which is useful when
     * the URI doesn't specify a region in the fragment.
     *
     * @param uri The S3 URI (s3://bucket/key)
     * @param region The AWS region to use
     * @return An S3RangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createS3RangeReader(URI uri, Region region) throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");
        Objects.requireNonNull(region, "Region cannot be null");

        if (!uri.getScheme().equalsIgnoreCase("s3")) {
            throw new IllegalArgumentException("URI must have s3 scheme: " + uri);
        }

        // Parse bucket and key from S3 URI (s3://bucket/key)
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("S3 URI must have a bucket: " + uri);
        }

        String pathStr = uri.getPath();
        if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
            throw new IllegalArgumentException("S3 URI must have a key: " + uri);
        }

        // Remove leading slash from path to get the key
        String key = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(region)
                .build();

        return new S3RangeReader(s3Client, authority, key);
    }

    /**
     * Creates an S3RangeReader for the specified URI with custom credentials and region.
     * <p>
     * This method allows providing both a custom AWS credentials provider and region.
     *
     * @param uri The S3 URI (s3://bucket/key)
     * @param credentialsProvider The AWS credentials provider to use
     * @param region The AWS region to use
     * @return An S3RangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createS3RangeReader(URI uri, AwsCredentialsProvider credentialsProvider, Region region)
            throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");
        Objects.requireNonNull(credentialsProvider, "Credentials provider cannot be null");
        Objects.requireNonNull(region, "Region cannot be null");

        if (!uri.getScheme().equalsIgnoreCase("s3")) {
            throw new IllegalArgumentException("URI must have s3 scheme: " + uri);
        }

        // Parse bucket and key from S3 URI (s3://bucket/key)
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("S3 URI must have a bucket: " + uri);
        }

        String pathStr = uri.getPath();
        if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
            throw new IllegalArgumentException("S3 URI must have a key: " + uri);
        }

        // Remove leading slash from path to get the key
        String key = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();

        return new S3RangeReader(s3Client, authority, key);
    }

    /**
     * Creates an AzureBlobRangeReader for the specified URI.
     * <p>
     * This method creates an Azure Blob reader using the URI information.
     * If the URI contains a query string, it's interpreted as a SAS token.
     *
     * @param uri The Azure blob URI (azure://account.blob.core.windows.net/container/blob)
     * @return An AzureBlobRangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    private static RangeReader createAzureBlobRangeReader(URI uri, String scheme) throws IOException {
        String blobUrl = uri.toString();
        if (scheme.equals("azure")) {
            // Convert azure://account.blob.core.windows.net/container/blob
            // to https://account.blob.core.windows.net/container/blob
            blobUrl = blobUrl.replace("azure://", "https://");
        }

        // Check for SAS token in query string
        String sasToken = uri.getQuery();

        BlobClientBuilder blobClientBuilder = new BlobClientBuilder().endpoint(blobUrl);

        // If SAS token is provided, use it
        if (sasToken != null && !sasToken.isEmpty()) {
            blobClientBuilder.sasToken(sasToken);
        } else {
            // Otherwise use the DefaultAzureCredential which checks various credential sources
            blobClientBuilder.credential(new DefaultAzureCredentialBuilder().build());
        }

        BlobClient blobClient = blobClientBuilder.buildClient();
        return new AzureBlobRangeReader(blobClient);
    }

    /**
     * Creates an AzureBlobRangeReader for the specified URI with a custom token credential.
     * <p>
     * This method allows providing a custom Azure token credential, which can be used
     * for different authentication methods like managed identity, service principal, etc.
     *
     * @param uri The Azure blob URI (azure://account.blob.core.windows.net/container/blob)
     * @param credential The Azure token credential to use
     * @return An AzureBlobRangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createAzureBlobRangeReader(URI uri, TokenCredential credential) throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");
        Objects.requireNonNull(credential, "Token credential cannot be null");

        String scheme = uri.getScheme();
        if (!scheme.equalsIgnoreCase("azure") && !scheme.equalsIgnoreCase("blob")) {
            throw new IllegalArgumentException("URI must have azure or blob scheme: " + uri);
        }

        String blobUrl = uri.toString();
        if (scheme.equals("azure")) {
            // Convert azure://account.blob.core.windows.net/container/blob
            // to https://account.blob.core.windows.net/container/blob
            blobUrl = blobUrl.replace("azure://", "https://");
        }

        BlobClient blobClient =
                new BlobClientBuilder().endpoint(blobUrl).credential(credential).buildClient();

        return new AzureBlobRangeReader(blobClient);
    }

    /**
     * Creates an AzureBlobRangeReader using account name, key, container, and blob path.
     * <p>
     * This method allows providing explicit account credentials rather than using
     * DefaultAzureCredential or SAS tokens.
     *
     * @param accountName The Azure Storage account name
     * @param accountKey The Azure Storage account key
     * @param containerName The container name
     * @param blobPath The blob path within the container
     * @return An AzureBlobRangeReader for the specified blob
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createAzureBlobRangeReader(
            String accountName, String accountKey, String containerName, String blobPath) throws IOException {
        Objects.requireNonNull(accountName, "Account name cannot be null");
        Objects.requireNonNull(accountKey, "Account key cannot be null");
        Objects.requireNonNull(containerName, "Container name cannot be null");
        Objects.requireNonNull(blobPath, "Blob path cannot be null");

        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
        String endpoint = String.format("https://%s.blob.core.windows.net", accountName);

        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .containerName(containerName)
                .blobName(blobPath)
                .buildClient();

        return new AzureBlobRangeReader(blobClient);
    }

    /**
     * Creates an AzureBlobRangeReader using account name, container, blob path, and SAS token.
     * <p>
     * This method allows providing an Azure Storage account name and SAS token.
     *
     * @param accountName The Azure Storage account name
     * @param containerName The container name
     * @param blobPath The blob path within the container
     * @param sasToken The SAS token for authentication
     * @return An AzureBlobRangeReader for the specified blob
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createAzureBlobRangeReaderWithSas(
            String accountName, String containerName, String blobPath, String sasToken) throws IOException {
        Objects.requireNonNull(accountName, "Account name cannot be null");
        Objects.requireNonNull(containerName, "Container name cannot be null");
        Objects.requireNonNull(blobPath, "Blob path cannot be null");
        Objects.requireNonNull(sasToken, "SAS token cannot be null");

        // Make sure the SAS token starts with a question mark if it doesn't already
        if (!sasToken.startsWith("?")) {
            sasToken = "?" + sasToken;
        }

        String endpoint = String.format("https://%s.blob.core.windows.net", accountName);

        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(endpoint)
                .containerName(containerName)
                .blobName(blobPath)
                .sasToken(sasToken)
                .buildClient();

        return new AzureBlobRangeReader(blobClient);
    }

    /**
     * Creates an AzureBlobRangeReader using a connection string.
     * <p>
     * This method allows providing an Azure Storage connection string, which includes
     * account credentials and endpoint information.
     *
     * @param connectionString The Azure Storage connection string
     * @param containerName The container name
     * @param blobPath The blob path within the container
     * @return An AzureBlobRangeReader for the specified blob
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createAzureBlobRangeReader(String connectionString, String containerName, String blobPath)
            throws IOException {
        Objects.requireNonNull(connectionString, "Connection string cannot be null");
        Objects.requireNonNull(containerName, "Container name cannot be null");
        Objects.requireNonNull(blobPath, "Blob path cannot be null");

        BlobClient blobClient = new BlobClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .blobName(blobPath)
                .buildClient();

        return new AzureBlobRangeReader(blobClient);
    }

    /**
     * Creates a GoogleCloudStorageRangeReader for the specified bucket and object.
     * <p>
     * This method creates a Google Cloud Storage reader using default credentials.
     *
     * @param bucket The GCS bucket name
     * @param objectName The GCS object name
     * @return A GoogleCloudStorageRangeReader for the specified object
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createGoogleCloudStorageRangeReader(String bucket, String objectName) throws IOException {
        Objects.requireNonNull(bucket, "Bucket name cannot be null");
        Objects.requireNonNull(objectName, "Object name cannot be null");

        Storage storage = StorageOptions.getDefaultInstance().getService();
        return new GoogleCloudStorageRangeReader(storage, bucket, objectName);
    }

    /**
     * Creates a GoogleCloudStorageRangeReader for the specified GCS URI.
     * <p>
     * This method creates a Google Cloud Storage reader using default credentials.
     *
     * @param uri The GCS URI (gs://bucket/object)
     * @return A GoogleCloudStorageRangeReader for the specified URI
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createGoogleCloudStorageRangeReader(URI uri) throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");

        if (!uri.getScheme().equalsIgnoreCase("gs")) {
            throw new IllegalArgumentException("URI must have gs scheme: " + uri);
        }

        String bucket = uri.getAuthority();
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("GCS URI must have a bucket: " + uri);
        }

        String pathStr = uri.getPath();
        if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
            throw new IllegalArgumentException("GCS URI must have an object name: " + uri);
        }

        // Remove leading slash from path to get the object name
        String objectName = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;

        return createGoogleCloudStorageRangeReader(bucket, objectName);
    }

    /**
     * Creates a GoogleCloudStorageRangeReader with a custom Storage client.
     * <p>
     * This method allows providing a custom Google Cloud Storage client, which can be used
     * for different authentication methods or configurations.
     *
     * @param storage The Google Cloud Storage client to use
     * @param bucket The GCS bucket name
     * @param objectName The GCS object name
     * @return A GoogleCloudStorageRangeReader for the specified object
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createGoogleCloudStorageRangeReader(Storage storage, String bucket, String objectName)
            throws IOException {
        Objects.requireNonNull(storage, "Storage client cannot be null");
        Objects.requireNonNull(bucket, "Bucket name cannot be null");
        Objects.requireNonNull(objectName, "Object name cannot be null");

        return new GoogleCloudStorageRangeReader(storage, bucket, objectName);
    }

    /**
     * Creates a GoogleCloudStorageRangeReader with a specific project ID.
     * <p>
     * This method allows providing a specific Google Cloud project ID, which is useful when
     * working with buckets that require explicit project specification.
     *
     * @param projectId The Google Cloud project ID
     * @param bucket The GCS bucket name
     * @param objectName The GCS object name
     * @return A GoogleCloudStorageRangeReader for the specified object
     * @throws IOException If an I/O error occurs
     */
    public static RangeReader createGoogleCloudStorageRangeReader(String projectId, String bucket, String objectName)
            throws IOException {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(bucket, "Bucket name cannot be null");
        Objects.requireNonNull(objectName, "Object name cannot be null");

        Storage storage =
                StorageOptions.newBuilder().setProjectId(projectId).build().getService();

        return new GoogleCloudStorageRangeReader(storage, bucket, objectName);
    }
}
