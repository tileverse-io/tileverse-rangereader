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

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import java.io.IOException;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Examples of using different authentication methods with cloud storage RangeReaders.
 * <p>
 * This class demonstrates various ways to authenticate with AWS S3 and Azure Blob Storage
 * when creating RangeReader instances.
 * <p>
 * Note: These examples are for illustration purposes only and not meant to be run directly.
 * In a real application, you would use your actual credentials and endpoints.
 */
public class CloudAuthenticationExample {

    /**
     * Examples of AWS S3 authentication methods.
     */
    public static void s3AuthenticationExamples() throws IOException {
        // Example S3 URI
        URI s3Uri = URI.create("s3://example-bucket/path/to/file.pmtiles");

        // 1. Default credentials (checks env vars, AWS profile, EC2 instance profile, etc.)
        RangeReader defaultReader = RangeReaderFactory.createS3RangeReader(
                s3Uri, DefaultCredentialsProvider.builder().build());

        // 2. Specific AWS profile
        RangeReader profileReader = RangeReaderFactory.createS3RangeReader(
                s3Uri, ProfileCredentialsProvider.builder().profileName("dev").build());

        // 3. Static credentials (not recommended for production, use for testing only)
        RangeReader staticReader = RangeReaderFactory.createS3RangeReader(
                s3Uri,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("access-key-id", "secret-access-key")));

        // 4. With specific region
        RangeReader regionReader = RangeReaderFactory.createS3RangeReader(
                s3Uri, DefaultCredentialsProvider.builder().build(), Region.US_WEST_2);

        // 5. Create with explicit bucket and key
        S3Client s3Client = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.US_EAST_1)
                .build();
        RangeReader explicitReader = new S3RangeReader(s3Client, "example-bucket", "path/to/file.pmtiles");

        // Clean up resources
        defaultReader.close();
        profileReader.close();
        staticReader.close();
        regionReader.close();
        explicitReader.close();
    }

    /**
     * Examples of Azure Blob Storage authentication methods.
     */
    public static void azureAuthenticationExamples() throws IOException {
        // Example Azure URI
        URI azureUri = URI.create("azure://mystorageaccount.blob.core.windows.net/container/file.pmtiles");

        // 1. Default credentials (checks env vars, managed identity, Visual Studio, Azure CLI, etc.)
        TokenCredential defaultCredential = new DefaultAzureCredentialBuilder().build();
        RangeReader defaultReader = RangeReaderFactory.createAzureBlobRangeReader(azureUri, defaultCredential);

        // 2. Managed Identity (useful in Azure hosted services like App Service, Functions, VMs)
        TokenCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
                .clientId("client-id") // Optional, if you have multiple identities
                .build();
        RangeReader managedIdentityReader =
                RangeReaderFactory.createAzureBlobRangeReader(azureUri, managedIdentityCredential);

        // 3. Service Principal with client secret
        TokenCredential servicePrincipalCredential = new ClientSecretCredentialBuilder()
                .tenantId("tenant-id")
                .clientId("client-id")
                .clientSecret("client-secret")
                .build();
        RangeReader servicePrincipalReader =
                RangeReaderFactory.createAzureBlobRangeReader(azureUri, servicePrincipalCredential);

        // 4. Storage account shared key (account key authentication)
        RangeReader sharedKeyReader = RangeReaderFactory.createAzureBlobRangeReader(
                "mystorageaccount",
                "accountkey==", // Replace with actual key
                "container",
                "file.pmtiles");

        // 5. Connection string
        String connectionString =
                "DefaultEndpointsProtocol=https;AccountName=mystorageaccount;AccountKey=accountkey==;EndpointSuffix=core.windows.net";
        RangeReader connectionStringReader =
                RangeReaderFactory.createAzureBlobRangeReader(connectionString, "container", "file.pmtiles");

        // 6. SAS token (Shared Access Signature)
        // SAS tokens can be included directly in the URI's query string
        URI sasUri = URI.create(
                "azure://mystorageaccount.blob.core.windows.net/container/file.pmtiles?sp=r&st=2023-01-01&se=2024-01-01&spr=https&sv=2022-11-02&sig=signature");
        RangeReader sasReader = RangeReaderFactory.create(sasUri);

        // Clean up resources
        defaultReader.close();
        managedIdentityReader.close();
        servicePrincipalReader.close();
        sharedKeyReader.close();
        connectionStringReader.close();
        sasReader.close();
    }

    /**
     * Example of using a RangeReader with caching and block alignment for better performance.
     */
    public static void performanceOptimizationExample() throws IOException {
        // Example URI
        URI uri = URI.create("s3://example-bucket/path/to/file.pmtiles");

        // Create a basic reader
        RangeReader basicReader = RangeReaderFactory.create(uri);

        // Add caching for better performance
        RangeReader cachedReader = RangeReaderFactory.createCaching(basicReader);

        // Add block alignment for optimal cloud storage access (aligns reads to block boundaries)
        RangeReader blockAlignedReader = RangeReaderFactory.createBlockAligned(basicReader);

        // Combine both caching and block alignment (recommended for cloud storage)
        RangeReader optimizedReader = RangeReaderFactory.createBlockAlignedCaching(basicReader);

        // Use a custom block size (16KB instead of the default 8KB)
        RangeReader customBlockSizeReader = RangeReaderFactory.createBlockAlignedCaching(basicReader, 16384);

        // Clean up resources
        basicReader.close();
        cachedReader.close();
        blockAlignedReader.close();
        optimizedReader.close();
        customBlockSizeReader.close();
    }

    /**
     * Main method for demonstration purposes.
     * <p>
     * Note: This is not meant to be run directly as it contains placeholder credentials.
     */
    public static void main(String[] args) {
        try {
            System.out.println("These examples are for illustration only and not meant to be run directly.");
            System.out.println("In a real application, you would use your actual credentials and endpoints.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
