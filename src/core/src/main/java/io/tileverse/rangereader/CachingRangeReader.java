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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A decorator for RangeReader that adds caching capabilities using Caffeine.
 * <p>
 * This implementation caches recently accessed byte ranges to improve performance
 * for repeated reads, which is common when accessing the same data ranges multiple times.
 * <p>
 * For optimal performance, consider wrapping the underlying reader with
 * {@link BlockAlignedRangeReader} to ensure that all reads are aligned to
 * fixed-sized blocks, which can significantly improve cache efficiency.
 */
public class CachingRangeReader extends AbstractRangeReader implements RangeReader {

    static final long DEFAULT_MAX_SIZE_BYTES = 1024 * 1024 * 16;

    private final RangeReader delegate;
    private final Cache<RangeKey, ByteBuffer> cache;

    /**
     * Creates a new CachingRangeReader with default cache settings.
     * <p>
     * The default cache has a maximum size of 50 entries and expires entries after 10 minutes.
     *
     * @param delegate The underlying RangeReader to delegate to
     */
    public CachingRangeReader(RangeReader delegate) {
        this(delegate, DEFAULT_MAX_SIZE_BYTES);
    }

    public CachingRangeReader(RangeReader delegate, long cacheMaxSizeBytes) {
        this(delegate, createCache(cacheMaxSizeBytes));
    }

    /**
     * Creates a new CachingRangeReader with a custom Caffeine builder.
     * <p>
     * This allows for full customization of the cache behavior, including size limits,
     * expiration policies, and statistics collection.
     *
     * @param delegate The underlying RangeReader to delegate to
     * @param builder The Caffeine builder to use for creating the cache
     */
    public CachingRangeReader(RangeReader delegate, Caffeine<RangeKey, ByteBuffer> builder) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate RangeReader cannot be null");

        // Add a removal listener to ensure ByteBuffers can be garbage collected
        RemovalListener<RangeKey, ByteBuffer> removalListener = (key, value, cause) -> {
            // Nothing special needed for ByteBuffer cleanup
        };

        this.cache = builder.removalListener(removalListener).build();
    }

    static Caffeine<RangeKey, ByteBuffer> createCache(long maxSizeBytes) {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be > 0");
        }
        Caffeine<Object, Object> newBuilder =
                Caffeine.newBuilder().recordStats().expireAfterAccess(10, TimeUnit.MINUTES);

        // Use custom cache size with weighting based on actual ByteBuffer sizes
        return newBuilder.maximumWeight(maxSizeBytes).weigher((key, value) -> ((ByteBuffer) value).capacity());
    }

    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException {
        // Create a cache key for this range
        RangeKey key = new RangeKey(offset, actualLength);

        // Use Caffeine's get-or-create pattern
        // This handles concurrency correctly and prevents cache stampede
        ByteBuffer cachedBuffer = cache.get(key, this::loadRange);

        // Duplicate the cached buffer to avoid position/limit changes affecting the cached version
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
    public long getCacheSize() {
        return cache.estimatedSize();
    }

    /**
     * Gets the cache statistics.
     *
     * @return The cache statistics
     */
    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * Key for caching ranges based on offset and length.
     */
    public record RangeKey(long offset, int length) {}

    /**
     * Creates a new builder for CachingRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CachingRangeReader.
     */
    public static class Builder {
        private RangeReader delegate;
        private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

        private Builder() {}

        /**
         * Sets the delegate RangeReader to wrap with caching.
         *
         * @param delegate the delegate RangeReader
         * @return this builder
         */
        public Builder delegate(RangeReader delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
            return this;
        }

        /**
         * Sets the maximum cache size in bytes.
         *
         * @param maxSizeBytes the maximum cache size in bytes
         * @return this builder
         */
        public Builder maxSizeBytes(long maxSizeBytes) {
            if (maxSizeBytes <= 0) {
                throw new IllegalArgumentException("Max size must be positive: " + maxSizeBytes);
            }
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        /**
         * Builds the CachingRangeReader.
         *
         * @return a new CachingRangeReader instance
         * @throws IllegalStateException if delegate is not set
         */
        public CachingRangeReader build() {
            if (delegate == null) {
                throw new IllegalStateException("Delegate RangeReader must be set");
            }

            return new CachingRangeReader(delegate, maxSizeBytes);
        }
    }
}
