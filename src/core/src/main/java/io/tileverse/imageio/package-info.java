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
 * ImageIO adapters and utilities for working with NIO channels and image streams.
 * <p>
 * This package provides adapters that bridge the gap between modern Java NIO
 * channels and Java's ImageIO framework, enabling efficient image processing
 * from various data sources including cloud storage, HTTP endpoints, and local files.
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link io.tileverse.imageio.SeekableByteChannelImageInputStream} -
 *     Adapts a {@link java.nio.channels.SeekableByteChannel} to the {@link javax.imageio.stream.ImageInputStream} interface
 *     with configurable byte order support</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Reading Images from Files</h3>
 * <pre>{@code
 * // Read image from local file
 * try (SeekableByteChannel channel = Files.newByteChannel(imagePath, StandardOpenOption.READ);
 *      ImageInputStream imageStream = SeekableByteChannelImageInputStream.of(channel)) {
 *
 *     Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
 *     if (readers.hasNext()) {
 *         ImageReader reader = readers.next();
 *         reader.setInput(imageStream);
 *         BufferedImage image = reader.read(0);
 *         // Process image...
 *     }
 * }
 * }</pre>
 *
 * <h3>Reading Images with Different Byte Orders</h3>
 * <pre>{@code
 * // Read TIFF image with Intel byte order (little-endian)
 * try (SeekableByteChannel channel = Files.newByteChannel(tiffPath, StandardOpenOption.READ);
 *      ImageInputStream imageStream = SeekableByteChannelImageInputStream.of(channel)) {
 *
 *     // Configure for little-endian data
 *     imageStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
 *
 *     // Read TIFF header
 *     int magic = imageStream.readShort(); // Should be 0x4949 for little-endian TIFF
 *     int version = imageStream.readShort(); // Should be 42
 *
 *     // Continue with ImageIO processing
 *     Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("TIFF");
 *     // ...
 * }
 * }</pre>
 *
 * <h3>Working with Range Readers for Cloud Storage</h3>
 * <pre>{@code
 * // Read image directly from S3 with efficient seeking
 * RangeReader s3Reader = S3RangeReader.builder()
 *     .uri("s3://my-bucket/large-image.tif")
 *     .build();
 *
 * try (SeekableByteChannel channel = RangeReaderSeekableByteChannel.of(s3Reader);
 *      ImageInputStream imageStream = SeekableByteChannelImageInputStream.of(channel)) {
 *
 *     // Read image metadata without downloading entire file
 *     Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
 *     if (readers.hasNext()) {
 *         ImageReader reader = readers.next();
 *         reader.setInput(imageStream);
 *
 *         // Get image dimensions
 *         int width = reader.getWidth(0);
 *         int height = reader.getHeight(0);
 *
 *         // Read specific image region efficiently
 *         ImageReadParam param = reader.getDefaultReadParam();
 *         param.setSourceRegion(new Rectangle(100, 100, 500, 500));
 *         BufferedImage region = reader.read(0, param);
 *     }
 * }
 * }</pre>
 *
 * <h3>Efficient Random Access to Image Data</h3>
 * <pre>{@code
 * // Read image tiles from different locations efficiently
 * try (SeekableByteChannel channel = Files.newByteChannel(largeImagePath, StandardOpenOption.READ);
 *      ImageInputStream imageStream = SeekableByteChannelImageInputStream.of(channel)) {
 *
 *     // Read image header
 *     imageStream.seek(0);
 *     int imageWidth = imageStream.readInt();
 *     int imageHeight = imageStream.readInt();
 *
 *     // Jump to tile directory (efficient seeking, no data reading)
 *     long tileDirectoryOffset = 1000000;
 *     imageStream.seek(tileDirectoryOffset);
 *
 *     // Read tile metadata
 *     int tileCount = imageStream.readInt();
 *     for (int i = 0; i < tileCount; i++) {
 *         long tileOffset = imageStream.readLong();
 *         int tileSize = imageStream.readInt();
 *         // Process tile information...
 *     }
 * }
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 * <li><strong>Seeking Efficiency:</strong> The adapter uses channel seeking for random access,
 *     making it efficient for formats that require jumping between image sections</li>
 * <li><strong>Direct Channel Access:</strong> Reads data directly from the channel without
 *     internal buffering, ensuring exact position synchronization</li>
 * <li><strong>Network Optimization:</strong> For cloud storage or HTTP sources, minimize
 *     seeking operations for better performance</li>
 * </ul>
 *
 * <h2>Byte Order Support</h2>
 * The ImageInputStream interface provides full byte order control through
 * {@link javax.imageio.stream.ImageInputStream#setByteOrder(java.nio.ByteOrder)}:
 * <ul>
 * <li><strong>Big-Endian:</strong> Default byte order, compatible with network protocols
 *     and many image formats</li>
 * <li><strong>Little-Endian:</strong> Required for some TIFF variants, Windows bitmap formats,
 *     and binary data from Intel-based systems</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Adapters in this package are not thread-safe. External synchronization is required
 * when multiple threads access the same adapter instance. However, different threads
 * can safely use separate adapter instances with different channels.
 *
 * <h2>Error Handling</h2>
 * All adapters follow standard Java ImageIO error handling conventions:
 * <ul>
 * <li>{@link java.io.EOFException} when attempting to read beyond the end of data</li>
 * <li>{@link java.io.IOException} for general I/O errors from the underlying channel</li>
 * <li>{@link java.lang.IllegalArgumentException} for invalid parameters</li>
 * </ul>
 *
 * @since 1.0
 */
package io.tileverse.imageio;
