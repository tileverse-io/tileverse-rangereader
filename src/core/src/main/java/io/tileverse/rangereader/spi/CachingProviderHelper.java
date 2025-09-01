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
package io.tileverse.rangereader.spi;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader.Builder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A helper class for {@link RangeReaderProvider} implementations to manage caching-related parameters
 * and to decorate {@link RangeReader} instances with {@link CachingRangeReader}.
 */
class CachingProviderHelper {

    static final String GROUP = "memory-cache";

    /**
     * A {@link RangeReaderParameter} to enable or disable memory caching for raw byte range requests.
     * When enabled, a {@link CachingRangeReader} will wrap the underlying {@link RangeReader}.
     */
    private static final RangeReaderParameter<Boolean> MEMORY_CACHE = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.caching.enabled")
            .description("Enable memory cache for raw byte data")
            .type(Boolean.class)
            .group(GROUP)
            .defaultValue(true)
            .build();

    /**
     * A {@link RangeReaderParameter} to control whether cached byte ranges should be block-aligned.
     * Block alignment can improve performance for certain storage types by optimizing read patterns.
     */
    private static final RangeReaderParameter<Boolean> MEMORY_CACHE_BLOCK_ALIGNED = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.caching.blockaligned")
            .description("Apply block alignment for cached byte ranges")
            .type(Boolean.class)
            .group(GROUP)
            .defaultValue(true)
            .build();

    /**
     * A {@link RangeReaderParameter} to specify the block size in bytes for the memory cache.
     * The block size should be a power of 2 for optimal performance.
     */
    private static final RangeReaderParameter<Integer> MEMORY_CACHE_BLOCK_SIZE = RangeReaderParameter.builder()
            .key("io.tileverse.rangereader.caching.blocksize")
            .description("Cache block size in bytes (power of 2)")
            .type(Integer.class)
            .group(GROUP)
            .defaultValue(64 * 1024)
            .options(8 * 1024, 16 * 1024, 32 * 1024, 64 * 1024, 128 * 1024, 256 * 1024, 512 * 1024)
            .build();

    private static final List<RangeReaderParameter<?>> PARAMS =
            List.of(MEMORY_CACHE, MEMORY_CACHE_BLOCK_ALIGNED, MEMORY_CACHE_BLOCK_SIZE);

    /**
     * Returns a new list of parameters that includes the provided parameters along with
     * the caching-related parameters defined in this helper.
     *
     * @param params The initial list of parameters.
     * @return A new list containing all parameters, including caching parameters.
     */
    public static List<RangeReaderParameter<?>> withCachingParameters(List<RangeReaderParameter<?>> params) {
        List<RangeReaderParameter<?>> cachingParams = CachingProviderHelper.configParameters();
        params = new ArrayList<>(params);
        params.addAll(cachingParams);
        return params;
    }

    /**
     * Returns an unmodifiable list of {@link RangeReaderParameter}s related to caching configuration.
     *
     * @return A list of caching configuration parameters.
     */
    public static List<RangeReaderParameter<?>> configParameters() {
        return PARAMS;
    }

    /**
     * Decorates the given {@link RangeReader} with a {@link CachingRangeReader} if caching is enabled
     * in the provided {@link RangeReaderConfig}.
     * The caching behavior (block alignment, block size) is configured based on the {@code opts}.
     *
     * @param reader The {@link RangeReader} to decorate.
     * @param opts The {@link RangeReaderConfig} containing caching settings.
     * @return The decorated {@link RangeReader} (a {@link CachingRangeReader}) or the original reader if caching is disabled.
     */
    public static RangeReader decorate(RangeReader reader, RangeReaderConfig opts) {
        boolean enabled = opts.getParameter(MEMORY_CACHE).orElse(false);
        if (!enabled) {
            return reader;
        }
        Optional<Boolean> blockAligned = opts.getParameter(MEMORY_CACHE_BLOCK_ALIGNED);
        Optional<Integer> blockSize = opts.getParameter(MEMORY_CACHE_BLOCK_SIZE);

        Builder builder = CachingRangeReader.builder(reader);
        if (blockAligned.isPresent()) {
            if (blockSize.isPresent()) {
                builder.blockSize(blockSize.get());
            } else {
                builder.withBlockAlignment();
            }
        } else {
            builder.withoutBlockAlignment();
        }
        return builder.build();
    }
}
