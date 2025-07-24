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
package io.tileverse.rangereader.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.it.AbstractRangeReaderIT;
import io.tileverse.rangereader.it.TestUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for HttpRangeReader using Nginx container.
 * <p>
 * These tests verify that the HttpRangeReader can correctly read ranges of
 * bytes from an HTTP server using range requests. The tests use an Nginx
 * container to serve a static test file that is accessed by the
 * HttpRangeReader.
 * <p>
 * Note: For tests using authentication, see HttpRangeReaderAuthenticationIT.
 */
@Testcontainers(disabledWithoutDocker = true)
public class HttpRangeReaderIT extends AbstractRangeReaderIT {

    private static final DockerImageName NGINX_IMAGE = DockerImageName.parse("nginx:alpine");
    private static final String TEST_FILE_NAME = "test.bin";

    private static URI testFileUri;
    private static Path testFilePath;

    @TempDir
    static Path tempDir;

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> nginx = new GenericContainer<>(NGINX_IMAGE)
            .withCommand(
                    "sh",
                    "-c",
                    """
                    # Create test file
                    mkdir -p /usr/share/nginx/html && \
                    dd if=/dev/urandom of=/usr/share/nginx/html/test.bin bs=1024 count=100 && \
                    echo -n -e 'TstFile\\x03' > /tmp/header && \
                    dd if=/tmp/header of=/usr/share/nginx/html/test.bin bs=7 count=1 conv=notrunc && \

                    # Set up Nginx config
                    cat > /etc/nginx/conf.d/default.conf << 'EOF'
                    server {
                        listen 80;
                        server_name localhost;
                        location / {
                            root /usr/share/nginx/html;
                            autoindex on;
                            add_header Accept-Ranges bytes;
                        }
                    }
                    EOF

                    # Start Nginx
                    nginx -g 'daemon off;'
                    """)
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/"))
            .withLogConsumer(outputFrame -> System.out.println("Nginx: " + outputFrame.getUtf8String()));

    @BeforeAll
    static void setupNginx() throws IOException, InterruptedException {
        System.out.println("Setting up Nginx container for HTTP Range Reader tests...");

        // Create local placeholder file for the abstract test class
        testFilePath = tempDir.resolve(TEST_FILE_NAME);
        TestUtil.createMockTestFile(testFilePath, TEST_FILE_SIZE);

        // Set up the URI for accessing the test file via HTTP
        String baseUrl = String.format("http://%s:%d", nginx.getHost(), nginx.getFirstMappedPort());
        testFileUri = URI.create(baseUrl + "/" + TEST_FILE_NAME);

        System.out.println("Test file available at: " + testFileUri);
    }

    @AfterAll
    static void cleanupTestPaths() {
        // No specific cleanup needed as testcontainers handles container cleanup
    }

    @Override
    protected void setUp() throws IOException {
        // Make the test file accessible to the abstract test class
        testFile = testFilePath;
    }

    @Override
    protected RangeReader createBaseReader() throws IOException {
        return HttpRangeReader.of(testFileUri);
    }

    /**
     * Additional HTTP-specific tests can go here
     */
    @Test
    void testHttpRangeReaderBuilder() throws IOException {
        // Create RangeReader using builder
        try (RangeReader reader = HttpRangeReader.builder().uri(testFileUri).build()) {
            // Verify it's the right implementation
            assertTrue(reader instanceof HttpRangeReader, "Should be an HttpRangeReader instance");

            // Verify size matches
            assertEquals(TEST_FILE_SIZE, reader.size(), "File size should match");

            // Read some data to verify it works correctly
            ByteBuffer buffer = reader.readRange(0, 10);
            assertEquals(10, buffer.remaining(), "Should read 10 bytes");
        }
    }

    @Test
    void testHttpRangeReaderWithTrustAllCertificates() throws IOException {
        // Create RangeReader with trust all certificates option
        try (RangeReader reader = HttpRangeReader.builder()
                .uri(testFileUri)
                .trustAllCertificates()
                .build()) {

            // Verify it's the right implementation
            assertTrue(reader instanceof HttpRangeReader, "Should be an HttpRangeReader instance");

            // Verify size matches
            assertEquals(TEST_FILE_SIZE, reader.size(), "File size should match");
        }
    }

    @Test
    void testHttpRangeReaderWithInvalidUrl() throws IOException {
        // We'll use the testFileUri but append a nonexistent path to ensure it 404s
        URI invalidUri = URI.create(testFileUri.toString() + ".does-not-exist");

        // Constructor should succeed (lazy initialization)
        HttpRangeReader reader = HttpRangeReader.of(invalidUri);

        // But calling size() should throw an IOException due to the invalid URL
        assertThrows(IOException.class, reader::size, "Calling size() with a nonexistent URL should throw IOException");
    }

    @Test
    void testMultipleConsecutiveRangeRequests() throws IOException {
        // Test making multiple consecutive range requests to verify connection handling
        try (RangeReader reader = createBaseReader()) {
            // Verify size as sanity check
            assertEquals(TEST_FILE_SIZE, reader.size(), "File size should match");

            // Make multiple consecutive range requests
            for (int i = 0; i < 5; i++) {
                int offset = i * 1000;
                ByteBuffer buffer = reader.readRange(offset, 100);
                assertEquals(100, buffer.remaining(), "Range request " + i + " should return 100 bytes");
            }

            // Make a request at the end of the file
            ByteBuffer endBuffer = reader.readRange(TEST_FILE_SIZE - 50, 100);
            assertEquals(50, endBuffer.remaining(), "Range request at end of file should be truncated");
        }
    }

    @Test
    void testSmallAndLargeRangeRequests() throws IOException {
        // Test a mix of small and large range requests
        try (RangeReader reader = createBaseReader()) {
            // Small range request (10 bytes)
            ByteBuffer smallBuffer = reader.readRange(100, 10);
            assertEquals(10, smallBuffer.remaining(), "Small range request should return 10 bytes");

            // Medium range request (1KB)
            ByteBuffer mediumBuffer = reader.readRange(5000, 1024);
            assertEquals(1024, mediumBuffer.remaining(), "Medium range request should return 1024 bytes");

            // Large range request (10KB)
            ByteBuffer largeBuffer = reader.readRange(10000, 10240);
            assertEquals(10240, largeBuffer.remaining(), "Large range request should return 10240 bytes");

            // Very large range request (50KB)
            ByteBuffer veryLargeBuffer = reader.readRange(20000, 51200);
            assertEquals(51200, veryLargeBuffer.remaining(), "Very large range request should return 51200 bytes");
        }
    }

    @Test
    void testReadingAcrossChunkBoundaries() throws IOException {
        // Test reading across potential chunk boundaries in HTTP responses
        try (RangeReader reader = createBaseReader()) {
            // Read a range that spans typical chunk boundaries (4KB, 8KB, 16KB)
            for (int chunkSize : new int[] {4096, 8192, 16384}) {
                // Read range that starts before chunk boundary and ends after it
                int startOffset = chunkSize - 100;
                int length = 200;

                ByteBuffer buffer = reader.readRange(startOffset, length);
                assertEquals(
                        length,
                        buffer.remaining(),
                        "Range crossing " + chunkSize + " chunk boundary should return " + length + " bytes");
            }
        }
    }
}
