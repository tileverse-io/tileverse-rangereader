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
package io.tileverse.rangereader.file;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.spi.AbstractRangeReaderProvider;
import io.tileverse.rangereader.spi.RangeReaderConfig;
import io.tileverse.rangereader.spi.RangeReaderProvider;
import java.io.IOException;

public class FileRangeReaderProvider extends AbstractRangeReaderProvider {

    public static final String ENABLED_KEY = "IO_TILEVERSE_RANGEREADER_FILE";
    public static final String ID = "file";

    public FileRangeReaderProvider() {
        super(false); // don't add caching parameters
    }

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
        return "Reads ranges from the local file system.";
    }

    @Override
    public boolean canProcess(RangeReaderConfig config) {
        return RangeReaderConfig.matches(config, getId(), "file", null);
    }

    @Override
    protected RangeReader createInternal(RangeReaderConfig opts) throws IOException {
        String path = opts.uri().getPath();
        return FileRangeReader.builder().path(path).build();
    }
}
