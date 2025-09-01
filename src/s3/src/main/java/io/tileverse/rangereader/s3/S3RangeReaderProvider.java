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
package io.tileverse.rangereader.s3;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.spi.AbstractRangeReaderProvider;
import io.tileverse.rangereader.spi.RangeReaderConfig;
import io.tileverse.rangereader.spi.RangeReaderProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class S3RangeReaderProvider extends AbstractRangeReaderProvider {

    public static final String ENABLED_KEY = "IO_TILEVERSE_RANGEREADER_S3";
    public static final String ID = "s3";

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
        return "AWS S3 Range Reader";
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }

    @Override
    public boolean canProcess(RangeReaderConfig config) {
        if (!RangeReaderConfig.matches(config, getId(), "s3", "http", "https")) {
            return false;
        }

        URI uri = config.uri();
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String query = uri.getQuery();

        if ("s3".equalsIgnoreCase(scheme)) {
            return true;
        }

        if (host != null) {
            if (host.endsWith(".s3.amazonaws.com")
                    || host.endsWith(".s3-accesspoint.amazonaws.com")
                    || host.endsWith(".s3-accelerate.amazonaws.com")) {
                return true;
            }
        }

        // Check for pre-signed URL parameters
        if (query != null) {
            return query.toUpperCase().contains("X-AMZ-ALGORITHM=")
                    && query.contains("X-AMZ-CREDENTIAL=")
                    && query.contains("X-AMZ-SIGNATURE=");
        }

        return false;
    }

    @Override
    public boolean canProcessHeaders(Map<String, List<String>> headers) {
        return headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase("x-amz-request-id"));
    }

    @Override
    protected RangeReader createInternal(RangeReaderConfig opts) throws IOException {
        return S3RangeReader.builder().uri(opts.uri()).build();
    }
}
