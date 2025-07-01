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
package io.tileverse.rangereader.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.tileverse.rangereader.AbstractRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.NonNull;
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
 * For optimal performance, consider wrapping this reader with
 * {@link io.tileverse.rangereader.block.BlockAlignedRangeReader} to ensure that all reads are aligned to
 * fixed-sized blocks, which can significantly improve cache efficiency and reduce
 * the number of cache entries by encouraging cache reuse across overlapping ranges.
 * <p>
 * Disk cache entries are stored in a subdirectory within the specified cache
 * directory, where the subdirectory name is based on a hash of the source
 * identifier. Cache files within the subdirectory use filenames based on the
 * range offset and length. Caffeine's built-in LRU eviction policy is applied
 * when the cache exceeds the configured maximum size.
 * <p>
 * <strong>Multi-Instance Sharing:</strong> Multiple DiskCachingRangeReader instances
 * for the same source can share cache files on disk, enabling efficient data sharing.
 * However, each instance maintains its own internal cache view for concurrency control
 * and size management. This means:
 * <ul>
 * <li>Cache files created by one instance are immediately accessible to other instances</li>
 * <li>Each instance may report different cache statistics (entry counts, hit rates)</li>
 * <li>Cache eviction decisions are made independently by each instance</li>
 * <li>An instance may find "surprise cache hits" from files cached by other instances</li>
 * </ul>
 * <p>
 * Inspired by DuckDB's cache_httpfs extension.
 */
public class DiskCachingRangeReader extends AbstractRangeReader implements RangeReader {

    private static final Logger logger = LoggerFactory.getLogger(DiskCachingRangeReader.class);

    private final RangeReader delegate;
    private final Path sourceCacheDirectory;
    private final long maxCacheSizeBytes;
    private final String sourceIdentifier;
    private final boolean deleteOnClose;
    private final String sourceHash;

    // Default cache max size (1GB)
    static final long DEFAULT_MAX_CACHE_SIZE = 1024 * 1024 * 1024;

    // Caffeine cache with built-in LRU eviction and size tracking
    private final LoadingCache<CacheKey, Path> cache;

    /**
     * Creates a new DiskCachingRangeReader that caches ranges from the delegate on
     * disk. Package-private constructor - use the builder pattern instead.
     *
     * @param delegate           The delegate RangeReader
     * @param cacheDirectoryRoot The root directory for caches (a subdirectory will
     *                           be created)
     * @param maxCacheSizeBytes  The maximum size of the disk cache in bytes
     * @param deleteOnClose      Whether to delete cached files when this reader is
     *                           closed
     * @throws IOException If an I/O error occurs
     */
    DiskCachingRangeReader(RangeReader delegate, Path cacheDirectoryRoot, long maxCacheSizeBytes, boolean deleteOnClose)
            throws IOException {

        this.delegate = Objects.requireNonNull(delegate, "Delegate RangeReader cannot be null");
        Objects.requireNonNull(cacheDirectoryRoot, "Cache directory cannot be null");
        this.sourceIdentifier = delegate.getSourceIdentifier();
        if (maxCacheSizeBytes <= 0) {
            throw new IllegalArgumentException("Max cache size must be positive: " + maxCacheSizeBytes);
        }
        this.maxCacheSizeBytes = maxCacheSizeBytes;
        this.deleteOnClose = deleteOnClose;
        this.sourceHash = computeSourceHash();
        this.sourceCacheDirectory = cacheDirectoryRoot.resolve(sourceHash);

        // Create the source-specific cache directory if it doesn't exist
        Files.createDirectories(sourceCacheDirectory);

        // Initialize the Caffeine cache with weight-based eviction and loading function
        this.cache = Caffeine.newBuilder()
                .maximumWeight(this.maxCacheSizeBytes)
                .weigher(this::weighCacheEntry)
                .removalListener(this::onCacheFileRemoval)
                .recordStats()
                .build(this::loadFromDelegate);

        // Initialize the cache from existing files
        initializeCacheFromDisk();
    }

    @Override
    protected int readRangeNoFlip(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        // Skip caching for ranges larger than the entire cache
        if (actualLength > maxCacheSizeBytes) {
            logger.debug(
                    "Range too large to cache: {} bytes (max: {}), reading directly from delegate",
                    actualLength,
                    maxCacheSizeBytes);
            return fallbackToDelegate(offset, actualLength, target);
        }

        // Create cache key for this range
        CacheKey key = new CacheKey(sourceIdentifier, offset, actualLength);

        try {
            // Use Caffeine for concurrency control and cache management,
            // but always read from disk files to enable sharing between instances
            Path cachePath = cache.get(key);

            // Read from the cache file directly into the target buffer
            return readFromCacheFile(cachePath, target);

        } catch (NoSuchFileException fileDeleted) {
            // Cache file was deleted externally - invalidate and re-cache
            logger.debug("Cache file deleted externally, re-caching: key={}", key);
            cache.invalidate(key);

            // Re-load from delegate which will create a new cache file
            Path newCachePath = cache.get(key);
            return readFromCacheFile(newCachePath, target);

        } catch (Exception e) {
            logger.warn("Failed to read from cache: key={}, reading directly from delegate", key, e);
            // Fallback to direct delegate read if cache access fails
            return fallbackToDelegate(offset, actualLength, target);
        }
    }

    /**
     * Reads data from a cache file into the target buffer.
     * This method enables file-level sharing between multiple DiskCachingRangeReader instances.
     *
     * @param cachePath the path to the cache file
     * @param target the target buffer to read into
     * @return the number of bytes read
     * @throws IOException if an I/O error occurs
     */
    private int readFromCacheFile(Path cachePath, ByteBuffer target) throws IOException {
        try (FileChannel channel = FileChannel.open(cachePath, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            final int bytesToRead = (int) Math.min(fileSize, target.remaining());
            int totalRead = 0;
            while (totalRead < bytesToRead) {
                int read = channel.read(target);
                if (read == -1) {
                    break; // End of file
                }
                totalRead += read;
            }
            return totalRead;
        }
    }

    private int fallbackToDelegate(final long offset, final int actualLength, ByteBuffer target) throws IOException {
        int readCount = delegate.readRange(offset, actualLength, target);
        // readRangeNoFlip shall return the non-flipped buffer, but delegate.readRange
        // returns it flipped
        target.position(target.limit());
        return readCount;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public String getSourceIdentifier() {
        return "disk-cached:" + delegate.getSourceIdentifier();
    }

    @Override
    public void close() throws IOException {
        try {
            if (deleteOnClose) {
                deleteCacheFiles();
            }
        } finally {
            delegate.close();
        }
    }

    /**
     * Gets the current number of entries in the cache.
     *
     * @return The number of cached entries
     */
    long getCacheEntryCount() {
        return cache.estimatedSize();
    }

    /**
     * Gets the estimated cache size in bytes.
     *
     * @return The estimated cache size in bytes
     */
    long getEstimatedCacheSizeBytes() {
        return cache.asMap().keySet().stream().mapToLong(CacheKey::length).sum();
    }

    /**
     * Gets the maximum size of the disk cache in bytes.
     *
     * @return The maximum cache size
     */
    long getMaxCacheSize() {
        return maxCacheSizeBytes;
    }

    /**
     * Gets the cache statistics.
     *
     * @return The cache statistics
     */
    public CacheStats getCacheStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = cache.stats();
        long entryCount = cache.estimatedSize();
        long estimatedSizeBytes = getEstimatedCacheSizeBytes();

        return CacheStats.fromCaffeine(caffeineStats, entryCount, estimatedSizeBytes);
    }

    /**
     * Clears the disk cache, removing all cached entries and their corresponding cache files.
     * <p>
     * This method invalidates all entries in the cache and triggers the removal listener
     * to delete the associated cache files from disk. After this method completes,
     * all cache files known to this reader instance will have been removed from the
     * file system.
     * <p>
     * Note: If multiple DiskCachingRangeReader instances share the same cache directory
     * and source, this will only remove cache files known to this specific instance.
     * Cache files created by other instances may remain on disk.
     */
    public void clearCache() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    /**
     * Deletes all cache files for this source by removing the entire
     * source-specific cache subdirectory. This removes both cached entries and
     * their corresponding files on disk.
     */
    private void deleteCacheFiles() {

        // First, invalidate all cache entries to trigger cleanup
        cache.invalidateAll();
        cache.cleanUp();

        // Delete the entire source-specific cache directory
        try {
            if (Files.isDirectory(sourceCacheDirectory)) {
                try (Stream<Path> files = Files.walk(sourceCacheDirectory)) {
                    files.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                    logger.debug("Deleted cache path: {}", path);
                                } catch (IOException e) {
                                    logger.warn("Failed to delete cache path: {}", path, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to delete source cache directory: {}", sourceCacheDirectory, e);
        }
    }

    /**
     * Initializes the cache by scanning the cache directory.
     *
     * @throws IOException If an I/O error occurs
     */
    private void initializeCacheFromDisk() throws IOException {
        // Scan the source-specific cache directory for cache files
        if (Files.exists(sourceCacheDirectory)) {
            try (Stream<Path> files = Files.list(sourceCacheDirectory)) {
                files.filter(Files::isRegularFile).forEach(p -> {
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
        }

        if (logger.isInfoEnabled()) {
            long count = cache.estimatedSize();
            long size = this.getEstimatedCacheSizeBytes();
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
        String cacheFileName = String.format("%d_%d.range", key.offset, key.length);
        Path cachePath = sourceCacheDirectory.resolve(cacheFileName);

        // Ensure the source cache directory exists before writing
        Files.createDirectories(sourceCacheDirectory);

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
     * Weighs a cache entry for Caffeine's eviction policy based on file size.
     * Since we validate that cache entries don't exceed Integer.MAX_VALUE,
     * we can safely cast to int.
     *
     * @param key  The cache key
     * @param path The path to the cached file
     * @return The weight of the cache entry (file size in bytes)
     */
    private int weighCacheEntry(CacheKey key, Path path) {
        try {
            return (int) Files.size(path);
        } catch (IOException e) {
            // If we can't get the file size, assume it's gone and return 0
            return 0;
        }
    }

    /**
     * Computes a hash of the source identifier for use in cache file names.
     *
     * @return A hash string for the source
     */
    private String computeSourceHash() {
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
        if (parts.length < 2) {
            return null;
        }

        try {
            long offset = Long.parseLong(parts[0]);
            int length = Integer.parseInt(parts[1].replace(".range", ""));
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
     * Handles cache file removal when entries are evicted from the cache.
     *
     * @param key The cache key
     * @param path The path to the cache file
     * @param cause The reason for removal
     */
    private void onCacheFileRemoval(CacheKey key, Path path, @NonNull RemovalCause cause) {
        if (path != null && Files.isRegularFile(path)) {
            try {
                Files.delete(path);
                logger.debug("Removed from disk cache: path={}, cause={}", path, cause);
            } catch (NoSuchFileException alreadyRemoved) {
                logger.debug("File already removed from disk cache: path={}, cause={}", path, cause);
            } catch (IOException e) {
                logger.warn("Failed to delete cache file: {}", path, e);
            }
        }
    }

    /**
     * Creates a new builder for DiskCachingRangeReader with the mandatory delegate parameter.
     *
     * @param delegate the delegate RangeReader to wrap with disk caching
     * @return a new builder instance with the delegate set
     */
    public static Builder builder(RangeReader delegate) {
        return new Builder(delegate);
    }

    /**
     * Builder for DiskCachingRangeReader.
     */
    public static class Builder {
        private final RangeReader delegate;
        private Path cacheDirectory;
        private Long maxCacheSizeBytes;
        private boolean deleteOnClose = false;

        private Builder(RangeReader delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
        }

        /**
         * Sets the cache directory. If not set, defaults to a subdirectory
         * {@literal tileverse-rangereader-cache} in the system temporary directory.
         *
         * @param cacheDirectoryRoot the root directory for caches (a subdirectory will
         *                           be created)
         * @return this builder
         */
        public Builder cacheDirectory(Path cacheDirectoryRoot) {
            this.cacheDirectory = Objects.requireNonNull(cacheDirectoryRoot, "Cache directory cannot be null");
            return this;
        }

        /**
         * Sets the cache directory from a string path. If not set, defaults to a
         * subdirectory {@literal tileverse-rangereader-cache} in the system temporary
         * directory.
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
         * Sets whether to delete cached files when the reader is closed. This is useful
         * for temporary caching scenarios where you want to clean up after processing
         * is complete.
         *
         * @param deleteOnClose true to delete cached files on close, false to keep them
         * @return this builder
         */
        public Builder deleteOnClose(boolean deleteOnClose) {
            this.deleteOnClose = deleteOnClose;
            return this;
        }

        /**
         * Configures the reader to delete cached files when closed. This is equivalent
         * to calling {@code deleteOnClose(true)}.
         *
         * @return this builder
         */
        public Builder deleteOnClose() {
            return deleteOnClose(true);
        }

        /**
         * Builds the DiskCachingRangeReader.
         *
         * @return a new DiskCachingRangeReader instance
         * @throws IOException           if an error occurs during construction
         * @throws IllegalStateException if required parameters are not set
         */
        public DiskCachingRangeReader build() throws IOException {
            // Use default temporary directory if none specified
            Path effectiveCacheDirectory = cacheDirectory != null
                    ? cacheDirectory
                    : Paths.get(System.getProperty("java.io.tmpdir"), "tileverse-rangereader-cache");

            long effectiveMaxCacheSize = maxCacheSizeBytes != null ? maxCacheSizeBytes : DEFAULT_MAX_CACHE_SIZE;
            return new DiskCachingRangeReader(delegate, effectiveCacheDirectory, effectiveMaxCacheSize, deleteOnClose);
        }
    }
}
