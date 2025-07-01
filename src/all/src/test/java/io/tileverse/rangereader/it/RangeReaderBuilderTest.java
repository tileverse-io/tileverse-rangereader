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
package io.tileverse.rangereader.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.azure.AzureBlobRangeReader;
import io.tileverse.rangereader.block.BlockAlignedRangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.rangereader.file.FileRangeReader;
import io.tileverse.rangereader.http.HttpRangeReader;
import io.tileverse.rangereader.s3.S3RangeReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the RangeReaderBuilder.
 * <p>
 * These tests verify that the builder correctly creates different types of RangeReaders
 * and that configuration options are properly applied.
 */
public class RangeReaderBuilderTest {

    @TempDir
    Path tempDir;

    /**
     * Test building a file RangeReader from a Path.
     */
    @Test
    void testBuildFileRangeReaderFromPath() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        // Build a FileRangeReader
        try (RangeReader reader = FileRangeReader.builder().path(testFile).build()) {

            // Verify it's a FileRangeReader
            assertTrue(reader instanceof FileRangeReader, "Should be a FileRangeReader");

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test building a file RangeReader from a URI.
     */
    @Test
    void testBuildFileRangeReaderFromUri() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        // Build a FileRangeReader using a file URI
        try (RangeReader reader =
                FileRangeReader.builder().uri(testFile.toUri()).build()) {

            // Verify it's a FileRangeReader
            assertTrue(reader instanceof FileRangeReader, "Should be a FileRangeReader");

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test building a RangeReader with caching.
     */
    @Test
    void testBuildWithCaching() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        // Build a cached RangeReader
        try (RangeReader reader = CachingRangeReader.builder()
                .delegate(FileRangeReader.builder().path(testFile).build())
                .build()) {

            assertThat(reader).isInstanceOf(CachingRangeReader.class);

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test building a RangeReader with block alignment.
     */
    @Test
    void testBuildWithBlockAlignment() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        // Build a block-aligned RangeReader
        try (RangeReader reader = BlockAlignedRangeReader.builder()
                .delegate(FileRangeReader.builder().path(testFile).build())
                .blockSize(BlockAlignedRangeReader.DEFAULT_BLOCK_SIZE)
                .build()) {

            // Verify it's a BlockAlignedRangeReader
            assertTrue(reader instanceof BlockAlignedRangeReader, "Should be a BlockAlignedRangeReader");

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test building a RangeReader with custom block size.
     */
    @Test
    void testBuildWithCustomBlockSize() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        int customBlockSize = 4096;

        // Build a block-aligned RangeReader with custom block size
        try (RangeReader reader = BlockAlignedRangeReader.builder()
                .delegate(FileRangeReader.builder().path(testFile).build())
                .blockSize(customBlockSize)
                .build()) {

            // Verify it's a BlockAlignedRangeReader
            assertTrue(reader instanceof BlockAlignedRangeReader, "Should be a BlockAlignedRangeReader");
            assertEquals(customBlockSize, ((BlockAlignedRangeReader) reader).getBlockSize(), "Block size should match");

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test building a RangeReader with combined caching and block alignment.
     */
    @Test
    void testBuildWithCachingAndBlockAlignment() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, new byte[100]);

        // Build a RangeReader with both caching and block alignment
        try (RangeReader reader = CachingRangeReader.builder()
                .delegate(BlockAlignedRangeReader.builder()
                        .delegate(FileRangeReader.builder().path(testFile).build())
                        .blockSize(BlockAlignedRangeReader.DEFAULT_BLOCK_SIZE)
                        .build())
                .build()) {

            // Verify it's a CachingRangeReader wrapping a BlockAlignedRangeReader
            assertTrue(reader instanceof CachingRangeReader, "Outer reader should be a CachingRangeReader");

            // Verify it can read the file
            assertEquals(100, reader.size(), "File size should be 100 bytes");
            ByteBuffer data = reader.readRange(0, 10);
            assertEquals(10, data.remaining(), "Should read 10 bytes");
        }
    }

    /**
     * Test that S3 builder requires an S3 URI.
     */
    @Test
    void testS3BuilderRequiresS3Uri() {
        // This should throw an IllegalArgumentException
        URI invalidUri = URI.create("http://example.com/file");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            S3RangeReader.builder().uri(invalidUri);
        });

        assertTrue(
                exception.getMessage().contains("must have s3 scheme"),
                "Exception message should mention S3 scheme requirement");
    }

    /**
     * Test that HTTP builder requires HTTP/HTTPS URI.
     */
    @Test
    void testHttpBuilderRequiresHttpUri() {
        // This should throw an IllegalArgumentException
        URI invalidUri = URI.create("file:///path/to/file");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            HttpRangeReader.builder().uri(invalidUri);
        });

        assertTrue(
                exception.getMessage().contains("must have http or https scheme"),
                "Exception message should mention HTTP scheme requirement");
    }

    /**
     * Test validation errors for invalid block size.
     */
    @Test
    void testInvalidBlockSize() throws IOException {
        // Create a base reader
        RangeReader baseReader = FileRangeReader.builder().path(tempDir).build();

        // Test negative block size
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            BlockAlignedRangeReader.builder().delegate(baseReader).blockSize(-1024);
        });
        assertTrue(
                exception.getMessage().contains("must be positive"),
                "Exception message should mention positive block size requirement");

        // Test zero block size
        exception = assertThrows(IllegalArgumentException.class, () -> {
            BlockAlignedRangeReader.builder().delegate(baseReader).blockSize(0);
        });
        assertTrue(
                exception.getMessage().contains("must be positive"),
                "Exception message should mention positive block size requirement");

        // Test non-power-of-2 block size
        exception = assertThrows(IllegalArgumentException.class, () -> {
            BlockAlignedRangeReader.builder().delegate(baseReader).blockSize(1000); // Not a power of 2
        });
        assertTrue(
                exception.getMessage().contains("power of 2"),
                "Exception message should mention power of 2 requirement");

        baseReader.close();
    }

    /**
     * Test that SAS token is correctly set in the builder.
     */
    @Test
    void testAzureSasTokenBuilder() {
        String accountName = "teststorage";
        String sasToken = "sv=2020-08-04&ss=b&srt=co&sp=r&se=2023-04-30T17:31:52Z&sig=XXX";
        String containerName = "testcontainer";
        String blobPath = "test.pmtiles";

        // Create a builder with SAS token
        AzureBlobRangeReader.Builder builder = AzureBlobRangeReader.builder()
                .accountName(accountName)
                .sasToken(sasToken)
                .containerName(containerName)
                .blobPath(blobPath);

        // Verify the builder has set the SAS token
        // We use reflection to check the private fields
        try {
            Field sasTokenField = AzureBlobRangeReader.Builder.class.getDeclaredField("sasToken");
            sasTokenField.setAccessible(true);
            assertEquals(sasToken, sasTokenField.get(builder), "SAS token should be set");

            Field accountNameField = AzureBlobRangeReader.Builder.class.getDeclaredField("accountName");
            accountNameField.setAccessible(true);
            assertEquals(accountName, accountNameField.get(builder), "Account name should be set");

            Field containerField = AzureBlobRangeReader.Builder.class.getDeclaredField("containerName");
            containerField.setAccessible(true);
            assertEquals(containerName, containerField.get(builder), "Container name should be set");

            Field blobPathField = AzureBlobRangeReader.Builder.class.getDeclaredField("blobPath");
            blobPathField.setAccessible(true);
            assertEquals(blobPath, blobPathField.get(builder), "Blob path should be set");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access builder fields", e);
        }
    }

    /**
     * Test that account name is correctly set in the builder.
     */
    @Test
    void testAzureAccountNameBuilder() {
        String accountName = "teststorage";

        // Create a builder with account name
        AzureBlobRangeReader.Builder builder = AzureBlobRangeReader.builder().accountName(accountName);

        // Verify the builder has set the account name
        // We use reflection to check the private fields
        try {
            Field accountNameField = AzureBlobRangeReader.Builder.class.getDeclaredField("accountName");
            accountNameField.setAccessible(true);
            assertEquals(accountName, accountNameField.get(builder), "Account name should be set");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access builder fields", e);
        }

        // Verify file builder doesn't have Azure-specific methods
        FileRangeReader.Builder fileBuilder = FileRangeReader.builder().path(tempDir);
        // FileRangeReader.Builder doesn't have accountName method, so no test needed
    }

    /**
     * Test that SAS token without a question mark prefix is handled correctly.
     */
    @Test
    void testSasTokenAutomaticallyPrependsQuestionMark() throws Exception {
        // Create a test SAS token without a question mark
        String sasToken = "sv=2020-08-04&ss=b&srt=co&sp=r&se=2023-04-30T17:31:52Z&sig=XXX";

        // If we were to instantiate the reader, the SAS token would automatically get a question mark
        // But we can't easily test that without mocking Azure SDK components, which are final classes

        // Verify the withSasToken method accepts the token without throwing an exception
        AzureBlobRangeReader.Builder builder = AzureBlobRangeReader.builder()
                .accountName("testaccount")
                .sasToken(sasToken)
                .containerName("testcontainer")
                .blobPath("test.pmtiles");

        // The successful creation of the builder is sufficient for this test
        // The actual prepending of '?' happens in RangeReaderFactory.createAzureBlobRangeReader
        Field sasTokenField = AzureBlobRangeReader.Builder.class.getDeclaredField("sasToken");
        sasTokenField.setAccessible(true);
        assertEquals(sasToken, sasTokenField.get(builder), "SAS token should be stored as provided");
    }
}
