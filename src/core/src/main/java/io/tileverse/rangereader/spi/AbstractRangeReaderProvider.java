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
import java.io.IOException;
import java.util.List;

/**
 * An abstract base class for {@link RangeReaderProvider} implementations.
 * This class provides common functionality for managing parameters and applying
 * caching decorations to the created {@link RangeReader} instances.
 */
public abstract class AbstractRangeReaderProvider implements RangeReaderProvider {

    private final boolean supportsCaching;
    private final List<RangeReaderParameter<?>> params;

    /**
     * Constructs an {@code AbstractRangeReaderProvider} with caching enabled by default.
     * The caching parameters are added to the provider's parameter list.
     */
    protected AbstractRangeReaderProvider() {
        this(true);
    }

    /**
     * Constructs an {@code AbstractRangeReaderProvider} with a specified caching support.
     * If caching is supported, caching-related parameters are added to the provider's parameter list.
     *
     * @param supportsCaching {@code true} if this provider supports caching, {@code false} otherwise.
     */
    protected AbstractRangeReaderProvider(boolean supportsCaching) {
        this.supportsCaching = supportsCaching;

        List<RangeReaderParameter<?>> params = buildParameters();
        if (supportsCaching) {
            params = CachingProviderHelper.withCachingParameters(params);
        }
        this.params = List.copyOf(params);
    }

    /**
     * Returns an unmodifiable list of all {@link RangeReaderParameter}s supported by this provider.
     * This includes parameters defined by the concrete provider and, if supported, caching parameters.
     *
     * @return A list of {@link RangeReaderParameter}s.
     */
    @Override
    public final List<RangeReaderParameter<?>> getParameters() {
        return params;
    }

    /**
     * Creates a {@link RangeReader} instance based on the provided configuration.
     * If caching is supported and enabled in the configuration, the created reader
     * will be decorated with a {@link io.tileverse.rangereader.cache.CachingRangeReader}.
     *
     * @param opts The {@link RangeReaderConfig} containing the URI and other parameters.
     * @return A new {@link RangeReader} instance, potentially wrapped with caching.
     * @throws IOException If an I/O error occurs during reader creation.
     */
    @Override
    public final RangeReader create(RangeReaderConfig opts) throws IOException {
        RangeReader reader = createInternal(opts);
        if (supportsCaching) {
            reader = CachingProviderHelper.decorate(reader, opts);
        }
        return reader;
    }

    /**
     * Builds the list of {@link RangeReaderParameter}s specific to this concrete provider.
     * Subclasses should override this method to define their own parameters.
     *
     * @return A list of provider-specific {@link RangeReaderParameter}s.
     */
    protected List<RangeReaderParameter<?>> buildParameters() {
        return List.of();
    }

    /**
     * Creates the core {@link RangeReader} instance without any caching decoration.
     * Subclasses must implement this method to provide their specific reader implementation.
     *
     * @param opts The {@link RangeReaderConfig} containing the URI and other parameters.
     * @return The raw {@link RangeReader} instance.
     * @throws IOException If an I/O error occurs during reader creation.
     */
    protected abstract RangeReader createInternal(RangeReaderConfig opts) throws IOException;
}
