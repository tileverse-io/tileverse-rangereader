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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.tileverse.rangereader.GoogleCloudStorageRangeReader;
import io.tileverse.rangereader.RangeReader;
import io.tileverse.rangereader.RangeReaderFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for GoogleCloudStorageRangeReader using Google Cloud Storage emulator.
 * <p>
 * These tests verify that the GoogleCloudStorageRangeReader can correctly read ranges of bytes
 * from a GCS bucket using the Google Cloud Storage API against a local emulator container.
 */
@Testcontainers
public class GoogleCloudStorageRangeReaderIT extends AbstractRangeReaderIT {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_NAME = "test.bin";
    private static final String PROJECT_ID = "test-project";

    private static Path testFile;
    private static Storage storage;

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> gcsEmulator = new GenericContainer<>(
                    DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"))
            .withCommand("gcloud", "beta", "emulators", "storage", "start", "--host=0.0.0.0", "--port=9199")
            .withExposedPorts(9199);

    @BeforeAll
    static void setupGCS() throws IOException {
        // Create a test file
        testFile = TestUtil.createTempTestFile(TEST_FILE_SIZE);

        // Wait for the emulator to be ready
        gcsEmulator.start();

        // Configure the Storage client to use the emulator
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getMappedPort(9199);
        String emulatorEndpoint = "http://" + emulatorHost + ":" + emulatorPort;

        // Set the environment variable for the emulator
        System.setProperty("STORAGE_EMULATOR_HOST", emulatorHost + ":" + emulatorPort);

        // Create Storage client
        storage = StorageOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorEndpoint)
                .build()
                .getService();

        // Create a bucket
        BucketInfo bucketInfo = BucketInfo.newBuilder(BUCKET_NAME).build();
        storage.create(bucketInfo);

        // Upload the test file
        byte[] fileContent = Files.readAllBytes(testFile);
        BlobId blobId = BlobId.of(BUCKET_NAME, OBJECT_NAME);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, fileContent);
    }

    @AfterAll
    static void cleanupGCS() {
        // Clean up system property
        System.clearProperty("STORAGE_EMULATOR_HOST");
    }

    @Override
    protected void setUp() throws IOException {
        // Nothing needed here since all setup is done in @BeforeAll
    }

    @Override
    protected RangeReader createBaseReader() throws IOException {
        // Create a new Storage client for each test to avoid connection issues
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getMappedPort(9199);
        String emulatorEndpoint = "http://" + emulatorHost + ":" + emulatorPort;

        Storage testStorage = StorageOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorEndpoint)
                .build()
                .getService();

        return new GoogleCloudStorageRangeReader(testStorage, BUCKET_NAME, OBJECT_NAME);
    }

    /**
     * Additional GCS-specific tests can go here
     */
    @Test
    void testGCSSpecificFactory() throws IOException {
        // Create GCS URI
        URI gcsUri = URI.create("gs://" + BUCKET_NAME + "/" + OBJECT_NAME);

        // Test factory method with custom Storage client
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getMappedPort(9199);
        String emulatorEndpoint = "http://" + emulatorHost + ":" + emulatorPort;

        Storage customStorage = StorageOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorEndpoint)
                .build()
                .getService();

        try (RangeReader reader =
                RangeReaderFactory.createGoogleCloudStorageRangeReader(customStorage, BUCKET_NAME, OBJECT_NAME)) {
            // Verify it's the right implementation
            assertTrue(
                    reader instanceof GoogleCloudStorageRangeReader,
                    "Should be a GoogleCloudStorageRangeReader instance");

            // Verify it can read the file
            assertTrue(reader.size() > 0, "Should be able to read file size");
        }
    }

    @Test
    void testGCSFactoryWithProjectId() throws IOException {
        // Test factory method with project ID
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getMappedPort(9199);

        // Temporarily set the emulator environment for this test
        String originalHost = System.getProperty("STORAGE_EMULATOR_HOST");
        System.setProperty("STORAGE_EMULATOR_HOST", emulatorHost + ":" + emulatorPort);

        try (RangeReader reader =
                RangeReaderFactory.createGoogleCloudStorageRangeReader(PROJECT_ID, BUCKET_NAME, OBJECT_NAME)) {
            // Verify it's the right implementation
            assertTrue(
                    reader instanceof GoogleCloudStorageRangeReader,
                    "Should be a GoogleCloudStorageRangeReader instance");

            // Verify it can read the file
            assertTrue(reader.size() > 0, "Should be able to read file size");
        } finally {
            // Restore original system property
            if (originalHost != null) {
                System.setProperty("STORAGE_EMULATOR_HOST", originalHost);
            }
        }
    }

    @Test
    void testGCSFactoryBasic() throws IOException {
        // Test basic factory method
        String emulatorHost = gcsEmulator.getHost();
        Integer emulatorPort = gcsEmulator.getMappedPort(9199);

        // Temporarily set the emulator environment for this test
        String originalHost = System.getProperty("STORAGE_EMULATOR_HOST");
        System.setProperty("STORAGE_EMULATOR_HOST", emulatorHost + ":" + emulatorPort);

        try (RangeReader reader = RangeReaderFactory.createGoogleCloudStorageRangeReader(BUCKET_NAME, OBJECT_NAME)) {
            // Verify it's the right implementation
            assertTrue(
                    reader instanceof GoogleCloudStorageRangeReader,
                    "Should be a GoogleCloudStorageRangeReader instance");

            // Verify it can read the file
            assertTrue(reader.size() > 0, "Should be able to read file size");
        } finally {
            // Restore original system property
            if (originalHost != null) {
                System.setProperty("STORAGE_EMULATOR_HOST", originalHost);
            }
        }
    }
}
