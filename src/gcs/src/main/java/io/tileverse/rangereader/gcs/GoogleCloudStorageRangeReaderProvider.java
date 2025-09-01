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
package io.tileverse.rangereader.gcs;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.spi.AbstractRangeReaderProvider;
import io.tileverse.rangereader.spi.RangeReaderConfig;
import io.tileverse.rangereader.spi.RangeReaderProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class GoogleCloudStorageRangeReaderProvider extends AbstractRangeReaderProvider {

    public static final String ENABLED_KEY = "IO_TILEVERSE_RANGEREADER_GCS";
    public static final String ID = "gcs";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return RangeReaderProvider.isEnabled(ENABLED_KEY);
    }

    @Override
    public String getDescription() {
        return "Google Cloud Storage Range Reader";
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }

    @Override
    public boolean canProcess(RangeReaderConfig config) {
        if (!RangeReaderConfig.matches(config, getId(), "gs", "http", "https")) {
            return false;
        }

        URI uri = config.uri();
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if ("gs".equalsIgnoreCase(scheme)) {
            return true;
        }

        return "storage.googleapis.com".equalsIgnoreCase(host) || "storage.cloud.google.com".equalsIgnoreCase(host);
    }

    @Override
    public boolean canProcessHeaders(Map<String, List<String>> headers) {
        // Check for any "x-goog-" prefixed header, which is a strong signal for GCS.
        return headers.keySet().stream().anyMatch(h -> h.toLowerCase().startsWith("x-goog-"));
    }

    @Override
    protected RangeReader createInternal(RangeReaderConfig opts) throws IOException {
        URI uri = opts.uri();
        return GoogleCloudStorageRangeReader.builder().uri(uri).build();
    }
}
