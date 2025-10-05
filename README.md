# Tileverse Range Reader

> **⚠️ ARCHIVED**: This repository has been archived and is no longer maintained. The Range Reader library has been merged into the main Tileverse monorepo at [tileverse-io/tileverse](https://github.com/tileverse-io/tileverse). Please use the consolidated repository for the latest version. Documentation is now available at [tileverse.io](https://tileverse.io).

A modern Java library (Java 17+) for efficient random access to byte ranges from local files, HTTP servers, and cloud storage services (S3, Azure Blob, Google Cloud Storage).

## Why Tileverse Range Reader?

- **🌐 Universal Access**: Read ranges from files, HTTP, S3, Azure, GCS with a unified API
- **⚡ High Performance**: Multi-level caching, block alignment, and cloud optimizations
- **🔒 Thread-Safe**: Designed for concurrent server environments
- **🧩 Composable**: Decorator pattern for flexible feature combinations
- **📦 Modular**: Include only the providers you need

## Quick Example

```java
// Read ranges from any source with the same API
try (RangeReader reader = S3RangeReader.builder()
        .uri(URI.create("s3://my-bucket/data.bin"))
        .withCaching()
        .build()) {
    
    ByteBuffer chunk = reader.readRange(1024, 512); // Read 512 bytes at offset 1024
}
```

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'io.tileverse.rangereader:tileverse-rangereader-all:1.0-SNAPSHOT'
```

For modular installations and cloud-specific dependencies, see the **[Installation Guide](https://tileverse-io.github.io/tileverse-rangereader/user-guide/installation/)**.

## Key Features

| Feature | Description | Documentation |
|---------|-------------|---------------|
| **Multiple Sources** | Local files, HTTP, S3, Azure Blob, Google Cloud Storage | [Data Sources](https://tileverse-io.github.io/tileverse-rangereader/user-guide/data-sources/) |
| **Authentication** | AWS credentials, Azure SAS tokens, HTTP auth, and more | [Authentication](https://tileverse-io.github.io/tileverse-rangereader/user-guide/authentication/) |
| **Performance Optimization** | Memory/disk caching, block alignment, concurrent access | [Performance](https://tileverse-io.github.io/tileverse-rangereader/user-guide/performance/) |
| **Configuration** | Flexible builder pattern with sensible defaults | [Configuration](https://tileverse-io.github.io/tileverse-rangereader/user-guide/configuration/) |

## Use Cases

- **Geospatial Applications**: Efficient access to PMTiles, GeoTIFF, and other tiled formats
- **Media Processing**: Extract specific segments from large audio/video files
- **Data Analysis**: Random access to large datasets without full loading
- **Cloud-Native Applications**: Optimize data access across cloud storage services

## Documentation

- **[User Guide](https://tileverse-io.github.io/tileverse-rangereader/user-guide/)**: Installation, usage, and configuration
- **[Developer Guide](https://tileverse-io.github.io/tileverse-rangereader/developer-guide/)**: Building, testing, and contributing to the project
- **[Architecture](https://tileverse-io.github.io/tileverse-rangereader/arc42/)**: Design decisions and technical details
- **[Examples](https://tileverse-io.github.io/tileverse-rangereader/user-guide/examples/)**: Common usage patterns and best practices

## Requirements

- **Java 17+** (runtime)
- **Maven 3.9+** or **Gradle 7.0+** (build)

## Development

Quick development commands using the included Makefile:

```bash
make help      # Show all available targets
make           # Build and test everything
make test      # Run all tests
make format    # Format code (Spotless + SortPOM)
make lint      # Check code formatting
```

For complete build documentation and Maven commands, see **[Developer Guide](https://tileverse-io.github.io/tileverse-rangereader/developer-guide/)**.

## Contributing

Contributions welcome! See our **[Contributing Guide](https://tileverse-io.github.io/tileverse-rangereader/contributing/)** and **[Code of Conduct](https://tileverse-io.github.io/tileverse-rangereader/code-of-conduct/)**.

## License

[Apache License 2.0](LICENSE)
