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
 * NIO utilities and optimizations for range readers.
 * <p>
 * This package provides utilities to optimize NIO operations used by range readers,
 * particularly for reducing garbage collection pressure and improving performance
 * in high-throughput scenarios.
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link io.tileverse.rangereader.nio.ByteBufferPool} -
 *     Thread-safe pool for reusing ByteBuffer instances to reduce GC pressure</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic ByteBuffer Pooling</h3>
 * <pre>{@code
 * // Use the default shared pool
 * ByteBufferPool pool = ByteBufferPool.getDefault();
 *
 * // Borrow a direct buffer for I/O operations
 * ByteBuffer buffer = pool.borrowDirect(8192);
 * try {
 *     // Use buffer for I/O
 *     int bytesRead = channel.read(buffer);
 *     buffer.flip();
 *     // Process data...
 * } finally {
 *     // Always return buffers to reduce GC pressure
 *     pool.returnBuffer(buffer);
 * }
 * }</pre>
 *
 * <h3>Custom Pool Configuration</h3>
 * <pre>{@code
 * // Create a pool optimized for large buffers
 * ByteBufferPool customPool = new ByteBufferPool(
 *     20,    // maxDirectBuffers - good for I/O heavy workloads
 *     50,    // maxHeapBuffers - good for processing workloads
 *     64 * 1024  // minBufferSize - 64KB minimum
 * );
 *
 * // Use custom pool for large data processing
 * ByteBuffer largeBuffer = customPool.borrowDirect(1024 * 1024); // 1MB
 * try {
 *     // Process large chunks of data
 *     processLargeData(largeBuffer);
 * } finally {
 *     customPool.returnBuffer(largeBuffer);
 * }
 * }</pre>
 *
 * <h3>Range Reader Integration</h3>
 * <pre>{@code
 * // Typical usage in a range reader implementation
 * public class CustomRangeReader extends AbstractRangeReader {
 *     private final ByteBufferPool bufferPool = ByteBufferPool.getDefault();
 *
 *     @Override
 *     protected int doReadRange(long offset, int length, ByteBuffer destination) throws IOException {
 *         // Borrow temporary buffer for internal operations
 *         ByteBuffer tempBuffer = bufferPool.borrowDirect(length);
 *         try {
 *             // Perform I/O operation
 *             int bytesRead = performIO(offset, tempBuffer);
 *
 *             // Transfer to destination
 *             tempBuffer.flip();
 *             destination.put(tempBuffer);
 *
 *             return bytesRead;
 *         } finally {
 *             // Always return borrowed buffers
 *             bufferPool.returnBuffer(tempBuffer);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Monitoring Pool Performance</h3>
 * <pre>{@code
 * ByteBufferPool pool = ByteBufferPool.getDefault();
 *
 * // Perform operations...
 *
 * // Check pool efficiency
 * ByteBufferPool.PoolStatistics stats = pool.getStatistics();
 * System.out.printf("Pool hit rate: %.2f%%\n", stats.hitRate());
 * System.out.printf("Pool return rate: %.2f%%\n", stats.returnRate());
 * System.out.printf("Buffers in pool: %d direct, %d heap\n",
 *                   stats.currentDirectBuffers(), stats.currentHeapBuffers());
 *
 * // Example output:
 * // Pool hit rate: 87.50%
 * // Pool return rate: 92.31%
 * // Buffers in pool: 15 direct, 23 heap
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 * <li><strong>Buffer Types:</strong> Use direct buffers for I/O operations and heap buffers
 *     for CPU-intensive processing to optimize performance</li>
 * <li><strong>Pool Sizing:</strong> Tune pool limits based on your application's concurrency
 *     and memory constraints</li>
 * <li><strong>Buffer Sizes:</strong> Set appropriate minimum buffer sizes to avoid pooling
 *     tiny buffers that provide little GC benefit</li>
 * <li><strong>Lifecycle:</strong> Always return borrowed buffers in finally blocks to
 *     ensure proper cleanup and maximum pool efficiency</li>
 * <li><strong>Monitoring:</strong> Use pool statistics to tune configuration and identify
 *     performance bottlenecks</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All classes in this package are fully thread-safe and can be shared across multiple
 * threads without external synchronization. The ByteBufferPool uses lock-free algorithms
 * for optimal concurrent performance.
 *
 * <h2>Integration with Range Readers</h2>
 * The utilities in this package are designed to integrate seamlessly with range reader
 * implementations:
 * <ul>
 * <li><strong>BlockAlignedRangeReader:</strong> Can use pooled buffers for block alignment operations</li>
 * <li><strong>DiskCachingRangeReader:</strong> Can use pooled buffers for cache I/O operations</li>
 * <li><strong>Custom Implementations:</strong> Any range reader that needs temporary buffers
 *     can benefit from the pooling infrastructure</li>
 * </ul>
 *
 * @since 1.0
 */
package io.tileverse.rangereader.nio;
