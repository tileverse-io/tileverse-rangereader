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
package io.tileverse.rangereader.azure;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.azure.AzureBlobRangeReader.Builder;
import io.tileverse.rangereader.spi.AbstractRangeReaderProvider;
import io.tileverse.rangereader.spi.RangeReaderConfig;
import io.tileverse.rangereader.spi.RangeReaderProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class AzureBlobRangeReaderProvider extends AbstractRangeReaderProvider {

    public static final String ENABLED_KEY = "IO_TILEVERSE_RANGEREADER_AZURE";
    public static final String ID = "azure";

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
        return "Azure Blob Storage Range Reader";
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }

    @Override
    public boolean canProcess(RangeReaderConfig config) {
        if (!RangeReaderConfig.matches(config, getId(), "http", "https")) {
            return false;
        }
        URI uri = config.uri();
        String host = uri.getHost();
        return host != null && host.endsWith(".blob.core.windows.net");
    }

    @Override
    public boolean canProcessHeaders(Map<String, List<String>> headers) {
        boolean hasRequestId = headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase("x-ms-request-id"));
        boolean hasVersion = headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase("x-ms-version"));
        return hasRequestId && hasVersion;
    }

    @Override
    protected RangeReader createInternal(RangeReaderConfig opts) throws IOException {
        URI uri = opts.uri();
        Builder builder = AzureBlobRangeReader.builder().uri(uri);
        return builder.build();
    }
}
