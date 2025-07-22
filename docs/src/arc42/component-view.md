# Component View

## Overview

The component view provides detailed insight into the internal structure of the main modules, showing how the decorator pattern and builder pattern are implemented to provide flexible, composable functionality.

## Project Structure

The project follows a multi-module Maven structure with comprehensive dependency management through Bills of Materials (BOMs):

```
tileverse-rangereader-parent/
├── dependencies/          # BOM managing third-party dependency versions
├── bom/                  # BOM managing Range Reader module versions  
├── src/core/             # Core interfaces and implementations
├── src/s3/               # AWS S3 implementation
├── src/azure/            # Azure Blob Storage implementation
├── src/gcs/              # Google Cloud Storage implementation
├── src/all/              # Unified builder and factory (legacy)
└── benchmarks/           # JMH performance benchmarks
```

### BOM Architecture

The project provides a two-tier BOM structure for optimal dependency management:

#### Dependencies BOM (`tileverse-rangereader-dependencies`)
- Manages versions of third-party libraries (AWS SDK, Azure SDK, etc.)
- Ensures dependency convergence across all modules
- Provides a single source of truth for external dependency versions

#### Range Reader BOM (`tileverse-rangereader-bom`)  
- Manages versions of all Range Reader modules
- Simplifies consumption by downstream projects
- Enables version-consistent module combinations

## Core Module Components

![Core Module Components](../assets/images/structurizr/structurizr-CoreComponents.svg)

The core module contains the fundamental abstractions and implementations:

- **RangeReader Interface**: The main abstraction for reading byte ranges
- **AbstractRangeReader**: Base implementation providing common functionality and template method pattern
- **FileRangeReader & HttpRangeReader**: Concrete implementations for local files and HTTP sources
- **Decorators**: CachingRangeReader, DiskCachingRangeReader, and BlockAlignedRangeReader for performance optimization
- **Authentication System**: Pluggable authentication mechanisms for HTTP sources

## All Module Components

![All Module Components](../assets/images/structurizr/structurizr-AllModuleComponents.svg)

The "All" module provides unified access to all functionality through builder and factory patterns:

- **RangeReaderBuilder**: Unified fluent API for creating configured readers
- **RangeReaderFactory**: Simple factory for creating readers from URIs

## Key Design Patterns

### Decorator Pattern Implementation

The decorator pattern enables composable functionality:

```java
// Base implementation
RangeReader base = S3RangeReader.builder()
    .uri(uri)
    .build();

// Add caching decorator
RangeReader cached = CachingRangeReader.builder(base)
    .maximumSize(1000)
    .build();

// Add block alignment decorator
RangeReader optimized = BlockAlignedRangeReader.builder()
    .delegate(cached)
    .blockSize(1024 * 1024)
    .build();
```

### Builder Pattern Usage

Each implementation provides a fluent builder:

```java
S3RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/key"))
    .region(Region.US_WEST_2)
    .credentialsProvider(credentialsProvider)
    .build();
```

### Template Method Pattern

`AbstractRangeReader` uses the template method pattern:

```java
public final int readRange(long offset, int length, ByteBuffer target) {
    // Common validation and setup
    validateParameters(offset, length, target);
    
    // Delegate to subclass implementation
    return readRangeNoFlip(offset, length, target);
}

// Subclasses implement this hook method
protected abstract int readRangeNoFlip(long offset, int length, ByteBuffer target);
```

## Component Responsibilities

### Core Interfaces

| Component | Responsibility | 
|-----------|---------------|
| **RangeReader** | Define the contract for reading byte ranges |
| **AbstractRangeReader** | Provide common functionality and validation |

### Implementations

| Component | Responsibility |
|-----------|---------------|
| **FileRangeReader** | Read ranges from local files using NIO |
| **HttpRangeReader** | Read ranges from HTTP servers with authentication |
| **S3RangeReader** | Read ranges from Amazon S3 and S3-compatible storage |
| **AzureBlobRangeReader** | Read ranges from Azure Blob Storage |
| **GoogleCloudStorageRangeReader** | Read ranges from Google Cloud Storage |

### Decorators

| Component | Responsibility |
|-----------|---------------|
| **CachingRangeReader** | Provide in-memory caching with configurable policies |
| **DiskCachingRangeReader** | Provide persistent disk-based caching |
| **BlockAlignedRangeReader** | Optimize reads through block alignment |

### Support Components

| Component | Responsibility |
|-----------|---------------|
| **Authentication System** | Handle various HTTP authentication mechanisms |
| **RangeReaderBuilder** | Provide unified builder API |
| **RangeReaderFactory** | Create readers from URI strings |

## Extension Points

The architecture provides several extension points:

1. **New Storage Backends**: Extend `AbstractRangeReader`
2. **Custom Authentication**: Implement `HttpAuthentication` interface
3. **Additional Decorators**: Implement `RangeReader` interface with delegation
4. **Custom Builders**: Follow the established builder pattern

This component architecture enables the library to serve as a flexible, extensible foundation for cloud-native data access in Java applications.
