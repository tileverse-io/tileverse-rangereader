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
 * Java I/O adapters and utilities for working with NIO channels and streams.
 * <p>
 * This package provides adapters that bridge the gap between modern Java NIO
 * channels and legacy Java I/O interfaces, enabling integration with frameworks
 * and libraries that expect traditional I/O abstractions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link io.tileverse.io.ReadableByteChannelDataInput} -
 *     Adapts a {@link java.nio.channels.ReadableByteChannel} to the {@link java.io.DataInput} interface</li>
 * <li>{@link io.tileverse.io.SeekableByteChannelDataInput} -
 *     Adapts a {@link java.nio.channels.SeekableByteChannel} to the {@link java.io.DataInput} interface
 *     with optimized seeking for efficient {@code skipBytes()} operations</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Reading Primitive Types from Channels</h3>
 * <pre>{@code
 * // Open a file as ReadableByteChannel
 * try (ReadableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
 *     // Wrap it as DataInput for easy primitive type reading
 *     DataInput dataInput = ReadableByteChannelDataInput.of(channel);
 *
 *     // Read structured data
 *     int header = dataInput.readInt();
 *     String version = dataInput.readUTF();
 *     double[] coordinates = new double[3];
 *     for (int i = 0; i < coordinates.length; i++) {
 *         coordinates[i] = dataInput.readDouble();
 *     }
 * }
 * }</pre>
 *
 * <h3>Integration with Serialization Frameworks</h3>
 * <pre>{@code
 * // Use with legacy serialization code expecting DataInput
 * try (ReadableByteChannel channel = openRemoteChannel()) {
 *     DataInput dataInput = ReadableByteChannelDataInput.of(channel);
 *
 *     // Compatible with any API expecting DataInput
 *     MyObject obj = deserializeFromDataInput(dataInput);
 *
 *     // Or read binary protocols
 *     BinaryProtocolReader reader = new BinaryProtocolReader(dataInput);
 *     Message message = reader.readMessage();
 * }
 * }</pre>
 *
 * <h3>Custom Buffer Sizing for Performance</h3>
 * <pre>{@code
 * // Large files benefit from larger buffers
 * try (ReadableByteChannel channel = Files.newByteChannel(largePath, StandardOpenOption.READ)) {
 *     // Use 64KB buffer for better performance with large files
 *     DataInput dataInput = ReadableByteChannelDataInput.of(channel, 65536);
 *
 *     // Process large amounts of structured data efficiently
 *     while (hasMoreData(dataInput)) {
 *         processRecord(dataInput);
 *     }
 * }
 * }</pre>
 *
 * <h3>Working with Range Readers</h3>
 * <pre>{@code
 * // Combine with RangeReader channel adapters for cloud storage
 * RangeReader s3Reader = S3RangeReader.builder()
 *     .uri("s3://bucket/structured-data.bin")
 *     .build();
 *
 * try (SeekableByteChannel channel = RangeReaderSeekableByteChannel.of(s3Reader)) {
 *     DataInput dataInput = SeekableByteChannelDataInput.of(channel);
 *
 *     // Read header at the beginning
 *     FileHeader header = readHeader(dataInput);
 *
 *     // Efficiently skip to data section without reading
 *     dataInput.skipBytes(header.getDataOffset());
 *
 *     // Read structured data directly from cloud storage
 *     List<Record> records = readRecords(dataInput, header.getRecordCount());
 * }
 * }</pre>
 *
 * <h3>Optimized Skipping with SeekableByteChannel</h3>
 * <pre>{@code
 * try (SeekableByteChannel channel = Files.newByteChannel(largePath, StandardOpenOption.READ)) {
 *     DataInput dataInput = SeekableByteChannelDataInput.of(channel);
 *
 *     // Read file header
 *     int version = dataInput.readInt();
 *     int dataOffset = dataInput.readInt();
 *
 *     // Efficiently skip to data section - no actual reading performed
 *     dataInput.skipBytes(dataOffset - 8); // -8 for the header we already read
 *
 *     // Process data at the target location
 *     processData(dataInput);
 * }
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 * <li><strong>Buffer Sizing:</strong> Larger buffers (64KB-1MB) improve performance for large files
 *     or slow channels, while smaller buffers (4KB-8KB) are sufficient for local files</li>
 * <li><strong>Sequential Access:</strong> These adapters are optimized for sequential reading;
 *     random access patterns may be inefficient</li>
 * <li><strong>Memory Usage:</strong> Each adapter maintains an internal buffer; consider
 *     buffer size when creating many concurrent instances</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Adapters in this package are not thread-safe. External synchronization is required
 * when multiple threads access the same adapter instance. However, different threads
 * can safely use separate adapter instances.
 *
 * <h2>Error Handling</h2>
 * All adapters follow standard Java I/O error handling conventions:
 * <ul>
 * <li>{@link java.io.EOFException} when attempting to read beyond the end of data</li>
 * <li>{@link java.io.IOException} for general I/O errors from the underlying channel</li>
 * <li>{@link java.lang.IllegalArgumentException} for invalid parameters</li>
 * </ul>
 *
 * @since 1.0
 */
package io.tileverse.io;
