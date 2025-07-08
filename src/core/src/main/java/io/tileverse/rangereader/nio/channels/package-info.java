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

/**
 * Java NIO channel adapters for RangeReader implementations.
 * <p>
 * This package provides adapters that allow {@link io.tileverse.rangereader.RangeReader}
 * instances to be used with standard Java NIO channel APIs, enabling integration with
 * frameworks and libraries that expect channel-based interfaces.
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link io.tileverse.rangereader.nio.channels.RangeReaderReadableByteChannel} -
 *     Adapts a RangeReader to the {@link java.nio.channels.ReadableByteChannel} interface for sequential reading</li>
 * <li>{@link io.tileverse.rangereader.nio.channels.RangeReaderSeekableByteChannel} -
 *     Extends RangeReaderReadableByteChannel and adapts a RangeReader to the {@link java.nio.channels.SeekableByteChannel} interface</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Sequential Reading with ReadableByteChannel</h3>
 * <pre>{@code
 * // Create a RangeReader
 * RangeReader reader = FileRangeReader.of(dataPath);
 *
 * // Wrap it as a ReadableByteChannel for sequential access
 * try (ReadableByteChannel channel = RangeReaderReadableByteChannel.of(reader)) {
 *     ByteBuffer buffer = ByteBuffer.allocate(1024);
 *     while (channel.read(buffer) != -1) {
 *         // Process buffer contents
 *         buffer.flip();
 *         processData(buffer);
 *         buffer.clear();
 *     }
 * } finally {
 *     reader.close(); // Channel doesn't close the RangeReader
 * }
 * }</pre>
 *
 * <h3>Random Access with SeekableByteChannel</h3>
 * <pre>{@code
 * // Create a RangeReader
 * RangeReader reader = FileRangeReader.of(dataPath);
 *
 * // Wrap it as a SeekableByteChannel for random access
 * try (SeekableByteChannel channel = RangeReaderSeekableByteChannel.of(reader)) {
 *     // Use standard NIO operations
 *     ByteBuffer buffer = ByteBuffer.allocate(1024);
 *     int bytesRead = channel.read(buffer);
 *
 *     // Seek to specific position
 *     channel.position(1000);
 *     buffer.clear();
 *     bytesRead = channel.read(buffer);
 * } finally {
 *     reader.close(); // Channel doesn't close the RangeReader
 * }
 * }</pre>
 *
 * <h3>Integration with NIO.2 File Systems</h3>
 * <pre>{@code
 * // Use with Files.newByteChannel() replacement pattern
 * RangeReader reader = HttpRangeReader.builder()
 *     .uri("https://example.com/data.bin")
 *     .build();
 *
 * try (SeekableByteChannel channel = RangeReaderSeekableByteChannel.of(reader)) {
 *     // Compatible with any API expecting SeekableByteChannel
 *     processChannel(channel);
 * }
 * }</pre>
 *
 * <h3>Memory Mapping Alternative</h3>
 * <pre>{@code
 * // Use RangeReader with caching as memory mapping alternative
 * RangeReader reader = S3RangeReader.builder()
 *     .uri("s3://bucket/large-file.dat")
 *     .withCaching()  // Enable memory caching
 *     .build();
 *
 * try (SeekableByteChannel channel = RangeReaderSeekableByteChannel.of(reader)) {
 *     // Provides random access to cloud storage
 *     // with memory caching for performance
 *     analyzeData(channel);
 * }
 * }</pre>
 *
 * <h2>Lifecycle Management</h2>
 * Channel adapters in this package do not take ownership of the underlying
 * {@link io.tileverse.rangereader.RangeReader}. This design allows:
 * <ul>
 * <li>Multiple channels to wrap the same RangeReader</li>
 * <li>Channels to be created and disposed independently</li>
 * <li>The caller to control RangeReader lifecycle explicitly</li>
 * </ul>
 * <p>
 * <strong>Important:</strong> Always close the underlying RangeReader separately
 * when you're done with all channels that use it.
 *
 * <h2>Thread Safety</h2>
 * All adapters in this package are thread-safe when used with thread-safe
 * {@link io.tileverse.rangereader.RangeReader} implementations. Position state
 * is managed using atomic operations for optimal performance.
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 * <li><strong>Buffering:</strong> Consider using cached RangeReader implementations
 *     for better performance with small, frequent reads</li>
 * <li><strong>Block Alignment:</strong> Use block-aligned RangeReader decorators
 *     for optimal performance with cloud storage</li>
 * <li><strong>Position Management:</strong> Position tracking adds minimal overhead
 *     but allows standard NIO semantics</li>
 * </ul>
 *
 * @since 1.0
 */
package io.tileverse.rangereader.nio.channels;
