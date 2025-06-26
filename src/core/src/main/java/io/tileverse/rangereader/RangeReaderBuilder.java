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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A simplified builder facade that delegates to the individual RangeReader builders.
 * <p>
 * This class provides a unified entry point for creating RangeReaders while internally
 * using the specific builder implementations. It maintains backward compatibility
 * while encouraging the use of the more focused individual builders.
 * <p>
 * For new code, consider using the individual builders directly:
 * <ul>
 * <li>{@link FileRangeReader#builder()}</li>
 * <li>{@link HttpRangeReader#builder()}</li>
 * <li>{@link S3RangeReader#builder()}</li>
 * <li>{@link AzureBlobRangeReader#builder()}</li>
 * <li>{@link GoogleCloudStorageRangeReader#builder()}</li>
 * <li>{@link CachingRangeReader#builder()}</li>
 * <li>{@link DiskCachingRangeReader#builder()}</li>
 * <li>{@link BlockAlignedRangeReader#builder()}</li>
 * </ul>
 *
 * @deprecated Use individual builders instead for better type safety and cleaner APIs
 */
@Deprecated(since = "0.2.0", forRemoval = true)
public class RangeReaderBuilder {

    /**
     * Creates a FileRangeReader builder.
     *
     * @param path the file path
     * @return a FileRangeReader builder
     */
    public static FileRangeReader.Builder file(Path path) {
        return FileRangeReader.builder().path(path);
    }

    /**
     * Creates a FileRangeReader builder from a URI.
     *
     * @param uri the file URI
     * @return a FileRangeReader builder
     */
    public static FileRangeReader.Builder file(URI uri) {
        return FileRangeReader.builder().uri(uri);
    }

    /**
     * Creates an HttpRangeReader builder.
     *
     * @param uri the HTTP URI
     * @return an HttpRangeReader builder
     */
    public static HttpRangeReader.Builder http(URI uri) {
        return HttpRangeReader.builder().uri(uri);
    }

    /**
     * Creates an S3RangeReader builder.
     *
     * @param uri the S3 URI
     * @return an S3RangeReader builder
     */
    public static S3RangeReader.Builder s3(URI uri) {
        return S3RangeReader.builder().uri(uri);
    }

    /**
     * Creates an AzureBlobRangeReader builder.
     *
     * @param uri the Azure URI
     * @return an AzureBlobRangeReader builder
     */
    public static AzureBlobRangeReader.Builder azure(URI uri) {
        return AzureBlobRangeReader.builder().uri(uri);
    }

    /**
     * Creates an AzureBlobRangeReader builder.
     *
     * @return an AzureBlobRangeReader builder
     */
    public static AzureBlobRangeReader.Builder azure() {
        return AzureBlobRangeReader.builder();
    }

    /**
     * Creates a GoogleCloudStorageRangeReader builder.
     *
     * @param uri the GCS URI
     * @return a GoogleCloudStorageRangeReader builder
     */
    public static GoogleCloudStorageRangeReader.Builder gcs(URI uri) {
        return GoogleCloudStorageRangeReader.builder().uri(uri);
    }

    /**
     * Creates a GoogleCloudStorageRangeReader builder.
     *
     * @return a GoogleCloudStorageRangeReader builder
     */
    public static GoogleCloudStorageRangeReader.Builder gcs() {
        return GoogleCloudStorageRangeReader.builder();
    }

    /**
     * Creates a CachingRangeReader builder.
     *
     * @param delegate the delegate RangeReader
     * @return a CachingRangeReader builder
     */
    public static CachingRangeReader.Builder caching(RangeReader delegate) {
        return CachingRangeReader.builder().delegate(delegate);
    }

    /**
     * Creates a DiskCachingRangeReader builder.
     *
     * @param delegate the delegate RangeReader
     * @param cacheDirectory the cache directory
     * @param sourceIdentifier the source identifier
     * @return a DiskCachingRangeReader builder
     */
    public static DiskCachingRangeReader.Builder diskCaching(
            RangeReader delegate, Path cacheDirectory, String sourceIdentifier) {
        return DiskCachingRangeReader.builder()
                .delegate(delegate)
                .cacheDirectory(cacheDirectory)
                .sourceIdentifier(sourceIdentifier);
    }

    /**
     * Creates a BlockAlignedRangeReader builder.
     *
     * @param delegate the delegate RangeReader
     * @return a BlockAlignedRangeReader builder
     */
    public static BlockAlignedRangeReader.Builder blockAligned(RangeReader delegate) {
        return BlockAlignedRangeReader.builder().delegate(delegate);
    }

    /**
     * Creates a RangeReader from a URI using the appropriate implementation.
     *
     * @param uri the URI
     * @return a RangeReader for the URI
     * @throws IOException if an error occurs
     */
    public static RangeReader fromUri(URI uri) throws IOException {
        Objects.requireNonNull(uri, "URI cannot be null");

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URI must have a scheme: " + uri);
        }

        return switch (scheme.toLowerCase()) {
            case "file" -> FileRangeReader.builder().uri(uri).build();
            case "http", "https" -> HttpRangeReader.builder().uri(uri).build();
            case "s3" -> S3RangeReader.builder().uri(uri).build();
            case "azure", "blob" -> AzureBlobRangeReader.builder().uri(uri).build();
            case "gs" -> GoogleCloudStorageRangeReader.builder().uri(uri).build();
            default -> throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
        };
    }

    /**
     * Creates a RangeReader with common performance optimizations applied.
     * <p>
     * This applies memory caching and block alignment to the provided base reader.
     *
     * @param baseReader the base reader
     * @return an optimized RangeReader
     */
    public static RangeReader withOptimizations(RangeReader baseReader) {
        return CachingRangeReader.builder()
                .delegate(BlockAlignedRangeReader.builder().delegate(baseReader).build())
                .build();
    }

    /**
     * Creates a RangeReader with comprehensive caching (memory + disk).
     *
     * @param baseReader the base reader
     * @param diskCacheDirectory the disk cache directory
     * @param sourceIdentifier the source identifier for disk caching
     * @return a fully cached RangeReader
     * @throws IOException if an error occurs
     */
    public static RangeReader withFullCaching(RangeReader baseReader, Path diskCacheDirectory, String sourceIdentifier)
            throws IOException {
        return CachingRangeReader.builder()
                .delegate(BlockAlignedRangeReader.builder()
                        .delegate(DiskCachingRangeReader.builder()
                                .delegate(baseReader)
                                .cacheDirectory(diskCacheDirectory)
                                .sourceIdentifier(sourceIdentifier)
                                .build())
                        .build())
                .build();
    }

    // Prevent instantiation
    private RangeReaderBuilder() {}
}
