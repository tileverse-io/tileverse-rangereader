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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A decorator for {@link RangeReader} that caches ranges on disk for faster
 * subsequent access.
 * <p>
 * This implementation provides a disk-based cache that persists byte ranges to
 * disk, allowing for caching of larger datasets than would fit in memory. It
 * can be combined with {@link CachingRangeReader} for a two-level caching
 * strategy (memory + disk).
 * <p>
 * Disk cache entries are stored in the specified cache directory with filenames
 * based on a hash of the source identifier and range. Caffeine's built-in LRU
 * eviction policy is applied when the cache exceeds the configured maximum
 * size.
 * <p>
 * Inspired by DuckDB's cache_httpfs extension.
 */
public class DiskCachingRangeReader extends AbstractRangeReader implements RangeReader {

    private static final Logger logger = LoggerFactory.getLogger(DiskCachingRangeReader.class);

    private final RangeReader delegate;
    private final Path cacheDirectory;
    private final long maxCacheSizeBytes;
    private final String sourceIdentifier;

    // Default cache max size (1GB)
    static final long DEFAULT_MAX_CACHE_SIZE = 1024 * 1024 * 1024;

    // Caffeine cache with built-in LRU eviction and size tracking
    private final LoadingCache<CacheKey, Path> cache;

    /**
     * Creates a new DiskCachingRangeReader with the default maximum cache size
     * (1GB).
     *
     * @param delegate         The delegate RangeReader
     * @param cacheDirectory   The directory to store cached ranges
     * @param sourceIdentifier A unique identifier for the source (URL, file path,
     *                         etc.)
     * @throws IOException If an I/O error occurs
     */
    public DiskCachingRangeReader(RangeReader delegate, Path cacheDirectory, String sourceIdentifier)
            throws IOException {
        this(delegate, cacheDirectory, sourceIdentifier, DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * Creates a new DiskCachingRangeReader that caches ranges from the delegate on
     * disk.
     *
     * @param delegate          The delegate RangeReader
     * @param cacheDirectory    The directory to store cached ranges
     * @param sourceIdentifier  A unique identifier for the source (URL, file path,
     *                          etc.)
     * @param maxCacheSizeBytes The maximum size of the disk cache in bytes
     * @throws IOException If an I/O error occurs
     */
    public DiskCachingRangeReader(
            RangeReader delegate, Path cacheDirectory, String sourceIdentifier, long maxCacheSizeBytes)
            throws IOException {
        this.delegate = Objects.requireNonNull(delegate, "Delegate RangeReader cannot be null");
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "Cache directory cannot be null");
        this.sourceIdentifier = Objects.requireNonNull(sourceIdentifier, "Source identifier cannot be null");
        this.maxCacheSizeBytes = maxCacheSizeBytes > 0 ? maxCacheSizeBytes : DEFAULT_MAX_CACHE_SIZE;

        // Create the cache directory if it doesn't exist
        Files.createDirectories(cacheDirectory);

        // Initialize the Caffeine cache with weight-based eviction and loading function
        this.cache = Caffeine.newBuilder()
                .maximumWeight(this.maxCacheSizeBytes)
                .weigher((CacheKey key, Path path) -> {
                    try {
                        return (int) Math.min(Integer.MAX_VALUE, Files.size(path));
                    } catch (IOException e) {
                        // If we can't get the file size, assume it's gone and return 0
                        return 0;
                    }
                })
                .removalListener(new CacheFileRemovalListener())
                .recordStats()
                .build(this::loadFromDelegate);

        // Initialize the cache from existing files
        initializeCacheFromDisk();
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        // Create cache key for this range
        CacheKey key = new CacheKey(sourceIdentifier, offset, actualLength);

        try {
            // Use the Caffeine loading function to get or load the cache entry
            Path cachePath = cache.get(key);

            // Read from the cache file
            try (RandomAccessFile file = new RandomAccessFile(cachePath.toFile(), "r");
                    FileChannel channel = file.getChannel()) {

                long fileSize = channel.size();

                // Read directly into the target buffer
                int bytesRead = (int) Math.min(fileSize, target.remaining());
                int totalRead = 0;
                while (totalRead < bytesRead) {
                    int read = channel.read(target);
                    if (read == -1) {
                        break; // End of file
                    }
                    totalRead += read;
                }
                return totalRead;
            } catch (FileNotFoundException deletedExternally) {
                // re-fetch
                logger.debug("Cache file deleted, re-fetching: key={}", key);
                cache.invalidate(key);
                Path path = loadFromDelegate(key);
                cache.put(key, path);
                return readRangeNoFlip(offset, actualLength, target);
            }

        } catch (Exception e) {
            logger.warn("Failed to read from cache: key={}", key, e);
            // Fallback to direct delegate read if cache access fails
            int readCount = delegate.readRange(offset, actualLength, target);
            // readRangeNoFlip shall return the non-flipped buffer, but delegate.readRange
            // returns it flipped
            target.position(target.limit());
            return readCount;
        }
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Gets the current size of the disk cache in bytes.
     *
     * @return The current cache size
     */
    public long getCurrentCacheSize() {
        return cache.asMap().keySet().stream().mapToLong(CacheKey::length).sum();
    }

    /**
     * Gets the maximum size of the disk cache in bytes.
     *
     * @return The maximum cache size
     */
    public long getMaxCacheSize() {
        return maxCacheSizeBytes;
    }

    /**
     * Gets the number of entries in the cache.
     *
     * @return The number of cache entries
     */
    public long getCacheEntryCount() {
        return cache.estimatedSize();
    }

    /**
     * Gets cache statistics.
     *
     * @return A string representation of the cache statistics
     */
    public String getCacheStats() {
        return cache.stats().toString();
    }

    /**
     * Clears the disk cache, removing all cached entries.
     */
    public void clearCache() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    /**
     * Initializes the cache by scanning the cache directory.
     *
     * @throws IOException If an I/O error occurs
     */
    private void initializeCacheFromDisk() throws IOException {
        // Use the source hash as a prefix for our cache files
        String sourceHash = getSourceHash();

        // Scan the cache directory for cache files
        try (Stream<Path> files = Files.list(cacheDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(sourceHash))
                    .forEach(p -> {
                        try {
                            // Parse the filename to extract offset and length
                            CacheKey key = parseCacheKey(p.getFileName().toString());
                            if (key != null) {
                                // Add to the cache
                                cache.put(key, p);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to process cache file: {}", p, e);
                        }
                    });
        }

        if (logger.isInfoEnabled()) {
            long count = cache.estimatedSize();
            long size = this.getCurrentCacheSize();
            logger.info("Initialized disk cache with {} entries, total size: {} bytes", count, size);
        }
    }

    /**
     * Loading function for Caffeine cache. Loads a range from the delegate reader
     * and caches it.
     *
     * @param key The cache key
     * @return Path to the cached file
     * @throws IOException If an I/O error occurs
     */
    private Path loadFromDelegate(CacheKey key) throws IOException {
        logger.debug("Cache miss for key: offset={}, length={}", key.offset, key.length);

        // Skip caching if the range is larger than the entire cache
        if (key.length > maxCacheSizeBytes) {
            logger.debug("Range too large to cache: {} bytes (max: {})", key.length, maxCacheSizeBytes);
            throw new IOException("Range too large to cache");
        }

        // Read the data from the delegate
        ByteBuffer buffer = ByteBuffer.allocate(key.length);
        int bytesRead = delegate.readRange(key.offset, key.length, buffer);

        // It's acceptable to read fewer bytes if we reached EOF
        if (bytesRead <= 0) {
            throw new IOException("Failed to read data from delegate reader");
        }

        // Update the key's length to match what was actually read, if different
        if (bytesRead != key.length) {
            logger.debug("Partial read from delegate: requested {} bytes, got {}", key.length, bytesRead);
            key = new CacheKey(sourceIdentifier, key.offset, bytesRead);
        }

        // Generate a stable file name based on the key (after potential update from
        // partial read)
        String cacheFileName = String.format("%s_%d_%d.bin", getSourceHash(), key.offset, key.length);
        Path cachePath = cacheDirectory.resolve(cacheFileName);

        // Write the data to the cache file
        try (RandomAccessFile file = new RandomAccessFile(cachePath.toFile(), "rw");
                FileChannel channel = file.getChannel()) {
            channel.write(buffer);
        } catch (IOException e) {
            // Clean up if we couldn't write
            try {
                Files.deleteIfExists(cachePath);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        logger.debug("Added to disk cache: offset={}, length={}, path={}", key.offset, key.length, cachePath);
        return cachePath;
    }

    /**
     * Computes a hash of the source identifier for use in cache file names.
     *
     * @return A hash string for the source
     */
    private String getSourceHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(sourceIdentifier.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 8); // Use first 8 chars for brevity
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash for unlikely case MD5 is not available
            return String.format("%08x", sourceIdentifier.hashCode());
        }
    }

    /**
     * Parses a cache file name to extract the cache key.
     *
     * @param fileName The cache file name
     * @return The parsed cache key, or null if the format is invalid
     */
    private CacheKey parseCacheKey(String fileName) {
        String[] parts = fileName.split("_");
        if (parts.length < 3) {
            return null;
        }

        try {
            long offset = Long.parseLong(parts[1]);
            int length = Integer.parseInt(parts[2].replace(".bin", ""));
            return new CacheKey(sourceIdentifier, offset, length);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Represents a cache key for identifying a specific byte range.
     */
    private static record CacheKey(String sourceIdentifier, long offset, int length) {
        @Override
        public String toString() {
            return "[source: %s, offset: %s, lenght: %s]".formatted(sourceIdentifier, offset, length);
        }
    }

    /**
     * Removal listener to delete cache files when they are evicted from the cache.
     */
    private class CacheFileRemovalListener implements RemovalListener<CacheKey, Path> {
        @Override
        public void onRemoval(@Nullable CacheKey key, @Nullable Path path, @NonNull RemovalCause cause) {
            if (path != null) {
                try {
                    // Update metrics before deleting
                    if (Files.exists(path)) {
                        Files.delete(path);
                    }
                    logger.debug("Removed from disk cache: path={}, cause={}", path, cause);
                } catch (IOException e) {
                    logger.warn("Failed to delete cache file: {}", path, e);
                }
            }
        }
    }

    /**
     * Creates a new builder for DiskCachingRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DiskCachingRangeReader.
     */
    public static class Builder {
        private RangeReader delegate;
        private Path cacheDirectory;
        private String sourceIdentifier;
        private long maxCacheSizeBytes = DEFAULT_MAX_CACHE_SIZE;

        private Builder() {}

        /**
         * Sets the delegate RangeReader to wrap with disk caching.
         *
         * @param delegate the delegate RangeReader
         * @return this builder
         */
        public Builder delegate(RangeReader delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
            return this;
        }

        /**
         * Sets the cache directory.
         *
         * @param cacheDirectory the directory to store cached files
         * @return this builder
         */
        public Builder cacheDirectory(Path cacheDirectory) {
            this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "Cache directory cannot be null");
            return this;
        }

        /**
         * Sets the cache directory from a string path.
         *
         * @param cacheDirectoryPath the directory path as a string
         * @return this builder
         */
        public Builder cacheDirectory(String cacheDirectoryPath) {
            Objects.requireNonNull(cacheDirectoryPath, "Cache directory path cannot be null");
            this.cacheDirectory = Paths.get(cacheDirectoryPath);
            return this;
        }

        /**
         * Sets the source identifier for cache key generation.
         *
         * @param sourceIdentifier the source identifier
         * @return this builder
         */
        public Builder sourceIdentifier(String sourceIdentifier) {
            this.sourceIdentifier = Objects.requireNonNull(sourceIdentifier, "Source identifier cannot be null");
            return this;
        }

        /**
         * Sets the maximum cache size in bytes.
         *
         * @param maxCacheSizeBytes the maximum cache size in bytes
         * @return this builder
         */
        public Builder maxCacheSizeBytes(long maxCacheSizeBytes) {
            if (maxCacheSizeBytes <= 0) {
                throw new IllegalArgumentException("Max cache size must be positive: " + maxCacheSizeBytes);
            }
            this.maxCacheSizeBytes = maxCacheSizeBytes;
            return this;
        }

        /**
         * Builds the DiskCachingRangeReader.
         *
         * @return a new DiskCachingRangeReader instance
         * @throws IOException if an error occurs during construction
         * @throws IllegalStateException if required parameters are not set
         */
        public DiskCachingRangeReader build() throws IOException {
            if (delegate == null) {
                throw new IllegalStateException("Delegate RangeReader must be set");
            }
            if (cacheDirectory == null) {
                throw new IllegalStateException("Cache directory must be set");
            }
            if (sourceIdentifier == null) {
                throw new IllegalStateException("Source identifier must be set");
            }

            return new DiskCachingRangeReader(delegate, cacheDirectory, sourceIdentifier, maxCacheSizeBytes);
        }
    }
}
