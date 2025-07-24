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
package io.tileverse.rangereader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.tileverse.rangereader.block.BlockAlignedRangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.file.FileRangeReader;
import io.tileverse.rangereader.http.HttpRangeReader;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Performance tests for different RangeReader implementations.
 * <p>
 * These tests demonstrate the performance benefits of different RangeReader
 * decorators such as caching and block alignment.
 */
public class RangeReaderPerformanceTest {

    @TempDir
    Path tempDir;

    /**
     * Test the performance difference between regular, block-aligned, and cached reads.
     * <p>
     * This test demonstrates the performance benefits of different RangeReader decorators.
     */
    @Test
    void testLocalFilePerformance() throws IOException {
        // Create a temporary file with test data
        Path testFile = tempDir.resolve("test.bin");
        byte[] testData = new byte[100 * 1024]; // 100 KB
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) (i % 256);
        }
        Files.write(testFile, testData);

        // Measure time for regular reader
        long start = System.currentTimeMillis();
        try (RangeReader reader = FileRangeReader.builder().path(testFile).build()) {

            // Make multiple reads
            for (int i = 0; i < 20; i++) {
                ByteBuffer data = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data.remaining(), "Should read 1000 bytes");
            }
        }
        long regularTime = System.currentTimeMillis() - start;
        System.out.println("Regular reader time: " + regularTime + "ms");

        // Measure time for block-aligned reader
        start = System.currentTimeMillis();
        try (RangeReader reader = BlockAlignedRangeReader.builder(
                        FileRangeReader.builder().path(testFile).build())
                .blockSize(4096)
                .build()) {

            // Make multiple reads
            for (int i = 0; i < 20; i++) {
                ByteBuffer data = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data.remaining(), "Should read 1000 bytes");
            }
        }
        long blockTime = System.currentTimeMillis() - start;
        System.out.println("Block-aligned reader time: " + blockTime + "ms");

        // Measure time for cached reader
        start = System.currentTimeMillis();
        try (RangeReader reader = CachingRangeReader.builder(
                        FileRangeReader.builder().path(testFile).build())
                .build()) {

            // Make multiple reads, repeating each one to test cache
            for (int i = 0; i < 10; i++) {
                // First read
                ByteBuffer data1 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data1.remaining(), "Should read 1000 bytes");

                // Second read of same range (should be cached)
                ByteBuffer data2 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data2.remaining(), "Should read 1000 bytes");

                // Verify data is the same
                byte[] bytes1 = new byte[data1.remaining()];
                byte[] bytes2 = new byte[data2.remaining()];
                data1.duplicate().get(bytes1);
                data2.duplicate().get(bytes2);
                assertArrayEquals(bytes1, bytes2, "Cached data should match original data");
            }
        }
        long cachedTime = System.currentTimeMillis() - start;
        System.out.println("Cached reader time: " + cachedTime + "ms");

        // Measure time for combined readers (block-aligned and cached)
        start = System.currentTimeMillis();
        try (RangeReader reader = CachingRangeReader.builder(BlockAlignedRangeReader.builder(
                                FileRangeReader.builder().path(testFile).build())
                        .blockSize(4096)
                        .build())
                .build()) {

            // Make multiple reads, repeating each one to test cache
            for (int i = 0; i < 10; i++) {
                // First read
                ByteBuffer data1 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data1.remaining(), "Should read 1000 bytes");

                // Second read of same range (should be cached)
                ByteBuffer data2 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data2.remaining(), "Should read 1000 bytes");
            }
        }
        long combinedTime = System.currentTimeMillis() - start;
        System.out.println("Combined reader time: " + combinedTime + "ms");
    }

    /**
     * Test the performance of HTTP RangeReader with caching and block alignment.
     * <p>
     * This test requires an actual HTTP URL, so it's disabled by default.
     * Enable it for manual testing by providing a valid URL.
     */
    @Test
    @Disabled("Requires an actual HTTP URL")
    void testHttpPerformance() throws IOException {
        String url = "https://example.com/large-file.bin"; // Replace with a real URL
        URI httpUri = URI.create(url);

        // Measure time for regular reader
        long start = System.currentTimeMillis();
        try (RangeReader reader = HttpRangeReader.builder().uri(httpUri).build()) {

            // Make multiple reads
            for (int i = 0; i < 10; i++) {
                ByteBuffer data = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data.remaining(), "Should read 1000 bytes");
            }
        }
        long regularTime = System.currentTimeMillis() - start;
        System.out.println("HTTP regular reader time: " + regularTime + "ms");

        // Measure time for cached reader
        start = System.currentTimeMillis();
        try (RangeReader reader = CachingRangeReader.builder(
                        HttpRangeReader.builder().uri(httpUri).build())
                .build()) {

            // Make multiple reads, repeating each one to test cache
            for (int i = 0; i < 5; i++) {
                // First read
                ByteBuffer data1 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data1.remaining(), "Should read 1000 bytes");

                // Second read of same range (should be cached)
                ByteBuffer data2 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data2.remaining(), "Should read 1000 bytes");
            }
        }
        long cachedTime = System.currentTimeMillis() - start;
        System.out.println("HTTP cached reader time: " + cachedTime + "ms");

        // Measure time for combined readers (block-aligned and cached)
        start = System.currentTimeMillis();
        try (RangeReader reader = CachingRangeReader.builder(BlockAlignedRangeReader.builder(
                                HttpRangeReader.builder().uri(httpUri).build())
                        .blockSize(16384)
                        .build())
                .build()) {

            // Make multiple reads, repeating each one to test cache
            for (int i = 0; i < 5; i++) {
                // First read
                ByteBuffer data1 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data1.remaining(), "Should read 1000 bytes");

                // Second read of same range (should be cached)
                ByteBuffer data2 = reader.readRange(i * 1000, 1000);
                assertEquals(1000, data2.remaining(), "Should read 1000 bytes");
            }
        }
        long combinedTime = System.currentTimeMillis() - start;
        System.out.println("HTTP combined reader time: " + combinedTime + "ms");
    }
}
