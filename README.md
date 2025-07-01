# Tileverse Range Reader

A Java 17 library for reading byte ranges from various data sources including local files, HTTP servers, and cloud storage services.

Website: [https://tileverse.io](https://tileverse.io)

## Overview

Tileverse Range Reader provides a flexible and extensible I/O abstraction layer for reading arbitrary ranges of bytes from multiple sources. This enables efficient access to specific portions of large files without loading the entire file into memory, making it ideal for applications that need to access specific sections of large datasets.

## Features

- **Multiple Data Sources**: Read ranges from local files, HTTP/HTTPS servers, Amazon S3, Azure Blob Storage, and Google Cloud Storage
- **High Performance**: Multi-level caching (memory + disk) and block alignment optimizations
- **Thread-Safe**: All implementations support concurrent access
- **Flexible Authentication**: Support for various authentication methods (AWS credentials, Azure SAS tokens, HTTP authentication)
- **Decorator Pattern**: Composable functionality through decorators for caching, block alignment, and other optimizations
- **Cloud Optimized**: Optimized for cloud storage access patterns with configurable block sizes and caching strategies

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 4.0.0-rc-4 or higher

### Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

For specific modules, you can use:

```xml
<!-- Core functionality only -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- S3 support -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-s3</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Azure Blob Storage support -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-azure</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Google Cloud Storage support -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-gcs</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import io.tileverse.rangereader.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.net.URI;

// Reading from a local file
try (RangeReader reader = FileRangeReader.builder()
        .path(Path.of("data.bin"))
        .build()) {
    
    // Read first 1024 bytes
    ByteBuffer header = reader.readRange(0, 1024);
    
    // Read a specific section
    ByteBuffer chunk = reader.readRange(50000, 8192);
    
    // Get total file size
    long size = reader.size();
}

// Reading from HTTP with authentication
try (RangeReader reader = HttpRangeReader.builder()
        .uri(URI.create("https://example.com/data.bin"))
        .withBasicAuth("username", "password")
        .build()) {
    
    ByteBuffer data = reader.readRange(1000, 500);
}

// Reading from S3 with optimizations
try (RangeReader reader = S3RangeReader.builder()
        .uri(URI.create("s3://my-bucket/data.bin"))
        .withRegion(Region.US_WEST_2)
        .withCaching()                    // Add memory caching
        .withBlockAlignment(64 * 1024)    // 64KB block alignment
        .build()) {
    
    ByteBuffer data = reader.readRange(0, 1000);
}

// Reading from Azure Blob Storage with SAS token
try (RangeReader reader = AzureBlobRangeReader.builder()
        .withAccountName("mystorageaccount")
        .withSasToken("sv=2022-11-02&ss=b&srt=co&sp=r&se=2023-06-30T02:00:00Z&st=2023-05-01T18:00:00Z&spr=https&sig=XXXXX")
        .withContainer("mycontainer")
        .withBlob("data.bin")
        .withCaching()
        .build()) {
    
    ByteBuffer data = reader.readRange(0, 1000);
}
```

### Advanced Usage with Multiple Caching Layers

```java
// Create an optimized reader for large cloud files
try (RangeReader reader = S3RangeReader.builder()
        .uri(URI.create("s3://my-bucket/large-file.bin"))
        .withDiskCaching(Path.of("/var/cache/tileverse"))  // Persistent disk cache
        .withDiskBlockAlignment(1024 * 1024)               // 1MB blocks for disk I/O
        .withMemoryCaching()                               // Fast memory cache
        .withMemoryBlockAlignment(64 * 1024)               // 64KB blocks for memory
        .build()) {
    
    // Efficient access with multi-level caching:
    // 1. First read: fetches from S3, stores in both disk and memory caches
    // 2. Subsequent reads: served from memory cache (fastest)
    // 3. After restart: served from disk cache (faster than S3)
    ByteBuffer data = reader.readRange(1000000, 50000);
}
```

## Project Structure

- **tileverse-rangereader-core**: Core interfaces, file/HTTP readers, and caching decorators
- **tileverse-rangereader-s3**: Amazon S3 range reader implementation  
- **tileverse-rangereader-azure**: Azure Blob Storage range reader implementation
- **tileverse-rangereader-gcs**: Google Cloud Storage range reader implementation
- **tileverse-rangereader-all**: Convenience artifact that includes all modules
- **tileverse-rangereader-benchmarks**: JMH performance benchmarks

## Authentication

### AWS S3
- Default credential chain (environment variables, profiles, IAM roles)
- Custom credential providers
- S3-compatible services (MinIO, LocalStack)

### Azure Blob Storage
- Connection strings
- Account name + key
- SAS tokens (recommended for limited access)
- Azure Active Directory authentication

### HTTP/HTTPS
- Basic Authentication
- Bearer Token Authentication
- API Key Authentication  
- Digest Authentication
- Custom header authentication

## Performance Features

- **Multi-level caching**: Memory and disk caching with configurable sizes
- **Block alignment**: Optimize read patterns for cloud storage
- **Concurrent access**: Thread-safe implementations for server environments
- **TestContainers integration**: Comprehensive testing with real cloud services

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.