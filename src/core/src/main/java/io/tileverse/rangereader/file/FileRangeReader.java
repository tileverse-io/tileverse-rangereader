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

import io.tileverse.rangereader.AbstractRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A thread-safe file-based implementation of RangeReader.
 * <p>
 * This implementation uses a FileChannel with position-based reads to safely handle
 * concurrent access from multiple threads without interference.
 */
public class FileRangeReader extends AbstractRangeReader implements RangeReader {

    private final FileChannel channel;
    private final Path path;

    /**
     * Creates a new FileRangeReader for the specified file.
     *
     * @param path The path to the file
     * @throws IOException If an I/O error occurs
     */
    public FileRangeReader(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        this.path = path;
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException {

        // set limit to read at most actualLength
        target.limit(target.position() + actualLength);

        // Read until buffer is full or end of file
        int totalRead = 0;
        long currentPosition = offset;

        while (totalRead < actualLength) {
            // Use the position-based read method for thread safety
            // This allows concurrent reads without interference
            int read = channel.read(target, currentPosition);
            if (read == -1) {
                // End of file reached
                break;
            }
            totalRead += read;
            currentPosition += read;
        }
        // Return the actual number of bytes read
        return totalRead;
    }

    @Override
    public long size() throws IOException {
        // size() is thread-safe in FileChannel
        return channel.size();
    }

    @Override
    public String getSourceIdentifier() {
        return path.toAbsolutePath().toString();
    }

    @Override
    public void close() throws IOException {
        // close() is thread-safe in FileChannel
        channel.close();
    }

    /**
     * Creates a new builder for FileRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for FileRangeReader.
     */
    public static class Builder {
        private Path path;

        private Builder() {}

        /**
         * Sets the file path.
         *
         * @param path the file path
         * @return this builder
         */
        public Builder path(Path path) {
            this.path = Objects.requireNonNull(path, "Path cannot be null");
            return this;
        }

        /**
         * Sets the file path from a string.
         *
         * @param pathString the file path as a string
         * @return this builder
         */
        public Builder path(String pathString) {
            Objects.requireNonNull(pathString, "Path string cannot be null");
            this.path = Paths.get(pathString);
            return this;
        }

        /**
         * Sets the file path from a URI.
         *
         * @param uri the file URI
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("URI must have file scheme: " + uri);
            }
            this.path = Paths.get(uri);
            return this;
        }

        /**
         * Builds the FileRangeReader.
         *
         * @return a new FileRangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public FileRangeReader build() throws IOException {
            if (path == null) {
                throw new IllegalStateException("Path must be set");
            }

            return new FileRangeReader(path);
        }
    }
}
