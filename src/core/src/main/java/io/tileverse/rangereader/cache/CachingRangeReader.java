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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.tileverse.rangereader.AbstractRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A decorator for RangeReader that adds in-memory caching capabilities using Caffeine.
 * <p>
 * This implementation caches recently accessed byte ranges to improve
 * performance for repeated reads, which is common when accessing the same data
 * ranges multiple times.
 * <p>
 * <strong>Cache Configuration:</strong>
 * The cache can be configured with explicit size limits for predictable memory usage:
 * <ul>
 * <li><strong>Entry-based sizing:</strong> Use {@code maximumSize(long)} to limit the number of cached ranges</li>
 * <li><strong>Memory-based sizing:</strong> Use {@code maximumWeight(long)} or {@code maxSizeBytes(long)}
 *     to limit total memory usage (entries are automatically weighted by ByteBuffer capacity)</li>
 * <li><strong>Adaptive sizing (default):</strong> If no size limits are specified, soft references are used
 *     automatically, allowing the garbage collector to manage cache size based on memory pressure</li>
 * </ul>
 * <p>
 * <strong>Optional Configuration:</strong>
 * <ul>
 * <li><strong>Time-based expiration:</strong> Use {@code expireAfterAccess(duration, unit)} to automatically
 *     remove entries after a period of inactivity</li>
 * <li><strong>Memory pressure handling:</strong> Use {@code softValues()} to allow the garbage collector
 *     to reclaim cache entries when memory is needed</li>
 * </ul>
 * <p>
 * <strong>Usage Examples:</strong>
 * <pre>{@code
 * // Adaptive cache (default) - GC manages size automatically
 * CachingRangeReader reader = CachingRangeReader.builder(delegate)
 *     .build();
 *
 * // Fixed entry-count based cache
 * CachingRangeReader reader = CachingRangeReader.builder(delegate)
 *     .maximumSize(1000)
 *     .build();
 *
 * // Fixed memory-size based cache with expiration
 * CachingRangeReader reader = CachingRangeReader.builder(delegate)
 *     .maxSizeBytes(64 * 1024 * 1024) // 64MB
 *     .expireAfterAccess(30, TimeUnit.MINUTES)
 *     .build();
 * }</pre>
 * <p>
 * <strong>Performance Considerations:</strong>
 * For optimal performance, consider wrapping this reader with
 * {@link io.tileverse.rangereader.block.BlockAlignedRangeReader} to ensure that
 * all reads are aligned to fixed-sized blocks, which can significantly improve
 * cache efficiency and reduce the number of cache entries by encouraging cache
 * reuse across overlapping ranges.
 */
public class CachingRangeReader extends AbstractRangeReader implements RangeReader {

    private final RangeReader delegate;
    private final Cache<RangeKey, ByteBuffer> cache;

    /**
     * Creates a new CachingRangeReader with the provided cache.
     * Package-private constructor - use the builder pattern instead.
     *
     * @param delegate The underlying RangeReader to delegate to
     * @param cache    The cache to use for storing byte ranges
     */
    CachingRangeReader(RangeReader delegate, Cache<RangeKey, ByteBuffer> cache) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate RangeReader cannot be null");
        this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
    }

    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException {
        // Create a cache key for this range
        RangeKey key = new RangeKey(offset, actualLength);

        // Use Caffeine's get-or-create pattern
        // This handles concurrency correctly and prevents cache stampede
        ByteBuffer cachedBuffer = cache.get(key, this::loadRange);

        // Duplicate the cached buffer to avoid position/limit changes affecting the
        // cached version
        ByteBuffer duplicate = cachedBuffer.duplicate();

        // Copy the data from the cached buffer into the target
        int bytesRead = duplicate.remaining();
        target.put(duplicate);

        return bytesRead;
    }

    private ByteBuffer loadRange(RangeKey key) {
        try {
            // Allocate a buffer for the cache entry
            ByteBuffer cacheCopy = ByteBuffer.allocate(key.length());

            // Read data directly into the buffer
            this.delegate.readRange(key.offset(), key.length(), cacheCopy);

            return cacheCopy.asReadOnlyBuffer();
        } catch (IOException e) {
            // Propagate the exception through
            throw new UncheckedIOException("Failed to load range from delegate", e);
        }
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public String getSourceIdentifier() {
        return "memory-cached:" + delegate.getSourceIdentifier();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        cache.invalidateAll();
    }

    /**
     * Clears the cache, forcing subsequent reads to go to the underlying source.
     */
    public void clearCache() {
        cache.invalidateAll();
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
        // Calculate estimated size by summing the capacity of all cached ByteBuffers
        return cache.asMap().values().stream().mapToLong(ByteBuffer::capacity).sum();
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
     * Key for caching ranges based on offset and length.
     *
     * @param offset the starting position of the range
     * @param length the number of bytes in the range
     */
    public record RangeKey(long offset, int length) {}

    /**
     * Creates a new builder for CachingRangeReader with the mandatory delegate
     * parameter.
     *
     * @param delegate the delegate RangeReader to wrap with caching
     * @return a new builder instance with the delegate set
     */
    public static Builder builder(RangeReader delegate) {
        return new Builder(delegate);
    }

    /**
     * Builder for CachingRangeReader with configurable cache settings.
     */
    public static class Builder {
        private final RangeReader delegate;
        private Long maximumSize;
        private Long maximumWeight;
        private Long expireAfterAccessDuration;
        private TimeUnit expireAfterAccessUnit;
        private boolean softValues = false;

        private Builder(RangeReader delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
        }

        /**
         * Sets the maximum number of entries the cache can contain.
         * Cannot be used together with {@link #maximumWeight(long)}.
         *
         * @param maximumSize the maximum number of entries
         * @return this builder
         */
        public Builder maximumSize(long maximumSize) {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("Maximum size must be positive: " + maximumSize);
            }
            if (this.maximumWeight != null) {
                throw new IllegalStateException("Cannot set both maximumSize and maximumWeight");
            }
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Sets the maximum weight of entries the cache can contain.
         * When using this method, entries are weighted by their ByteBuffer capacity.
         * Cannot be used together with {@link #maximumSize(long)}.
         *
         * @param maximumWeight the maximum weight in bytes
         * @return this builder
         */
        public Builder maximumWeight(long maximumWeight) {
            if (maximumWeight <= 0) {
                throw new IllegalArgumentException("Maximum weight must be positive: " + maximumWeight);
            }
            if (this.maximumSize != null) {
                throw new IllegalStateException("Cannot set both maximumSize and maximumWeight");
            }
            this.maximumWeight = maximumWeight;
            return this;
        }

        /**
         * Sets the duration after which entries are automatically removed from the cache
         * following their last access.
         *
         * @param duration the duration
         * @param unit     the time unit
         * @return this builder
         */
        public Builder expireAfterAccess(long duration, TimeUnit unit) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Duration must be positive: " + duration);
            }
            this.expireAfterAccessDuration = duration;
            this.expireAfterAccessUnit = Objects.requireNonNull(unit, "Time unit cannot be null");
            return this;
        }

        /**
         * Enables soft values, allowing the garbage collector to evict entries when memory is needed.
         * This can help prevent OutOfMemoryError in memory-constrained environments.
         *
         * @return this builder
         */
        public Builder softValues() {
            return softValues(true);
        }

        /**
         * Enables soft values with the specified setting.
         *
         * @param softValues true to enable soft values, false to use strong references
         * @return this builder
         */
        public Builder softValues(boolean softValues) {
            this.softValues = softValues;
            return this;
        }

        /**
         * Builds the CachingRangeReader with the configured cache settings.
         *
         * @return a new CachingRangeReader instance
         */
        public CachingRangeReader build() {
            // Start with a base Caffeine builder
            Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder().recordStats();

            // Configure size limits
            if (maximumSize != null) {
                cacheBuilder.maximumSize(maximumSize);
            } else if (maximumWeight != null) {
                cacheBuilder.maximumWeight(maximumWeight).weigher((RangeKey key, ByteBuffer value) -> value.capacity());
            } else {
                // Default to soft values when no size limit is specified
                // This prevents OutOfMemoryError while still providing caching benefits
                cacheBuilder.softValues();
            }

            // Configure expiration (only if explicitly set)
            if (expireAfterAccessDuration != null && expireAfterAccessUnit != null) {
                cacheBuilder.expireAfterAccess(expireAfterAccessDuration, expireAfterAccessUnit);
            }

            // Configure value references
            if (softValues) {
                cacheBuilder.softValues();
            }

            return new CachingRangeReader(delegate, cacheBuilder.build());
        }
    }
}
