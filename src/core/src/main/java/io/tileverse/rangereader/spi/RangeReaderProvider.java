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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

public interface RangeReaderProvider {

    String getId();

    String getDescription();

    boolean isAvailable();

    List<RangeReaderParameter<?>> getParameters();

    default RangeReaderConfig getDefaultConfig() {
        return RangeReaderConfig.withDefaults(getParameters());
    }

    /**
     * Performs a fast, static check to see if this provider can likely handle the given config.
     * This check should be based on URI schemes and hostname patterns only, without I/O.
     *
     * @param config The configuration to check.
     * @return {@code true} if this provider can likely handle the config, {@code false} otherwise.
     */
    boolean canProcess(RangeReaderConfig config);

    /**
     * Performs a more definitive check by inspecting HTTP headers from a HEAD request.
     * This method is only called for ambiguous http(s) URIs as a final disambiguation step.
     *
     * @param headers The HTTP headers from a HEAD request to the resource URI.
     * @return {@code true} if the headers confirm this provider can handle the resource.
     */
    default boolean canProcessHeaders(Map<String, List<String>> headers) {
        return false; // Opt-in: only cloud providers need to implement this.
    }

    /**
     * Gets the order value of this provider. Lower values have higher priority.
     * The default priority is 0.
     *
     * @return The order value.
     */
    default int getOrder() {
        return 0;
    }

    default RangeReader create(URI uri) throws IOException {
        return create(getDefaultConfig().uri(uri));
    }

    RangeReader create(RangeReaderConfig opts) throws IOException;

    static boolean isEnabled(String key) {
        String enabled = System.getProperty(key);
        if (enabled == null) {
            enabled = System.getenv(key);
        }
        return enabled == null ? true : Boolean.parseBoolean(enabled);
    }

    static Stream<RangeReaderProvider> findProviders() {
        ServiceLoader<RangeReaderProvider> loader = ServiceLoader.load(RangeReaderProvider.class);
        return loader.stream().map(Provider::get);
    }

    /**
     * Returns all {@link RangeReaderProvider}s registered through the standard Java SPI mechanism
     */
    static List<RangeReaderProvider> getProviders() {
        return findProviders().toList();
    }

    /**
     * Returns all {@link RangeReaderProvider}s registered through the standard Java SPI mechanism
     * that are {@link RangeReaderProvider#isAvailable() available}
     */
    static List<RangeReaderProvider> getAvailableProviders() {
        return findProviders().filter(RangeReaderProvider::isAvailable).toList();
    }

    static Optional<RangeReaderProvider> findProvider(String providerId) {
        return findProviders()
                .filter(p -> p.getId().equalsIgnoreCase(providerId))
                .findFirst();
    }

    static RangeReaderProvider getProvider(String providerId, boolean available) {
        RangeReaderProvider provider = findProviders()
                .filter(p -> p.getId().equalsIgnoreCase(providerId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("The specified RangeReaderProvider is not found: " + providerId));

        if (available && !provider.isAvailable()) {
            throw new IllegalStateException("The specified RangeReaderProvider is not available: " + providerId);
        }
        return provider;
    }
}
