# Runtime View

## Overview

The runtime view describes the dynamic behavior of the Tileverse Range Reader library, showing how components interact to fulfill range-based I/O operations across different storage backends and usage patterns.

This section uses C4 model dynamic views that illustrate the actual runtime scenarios implemented in the library.

## Core Runtime Scenarios

### Basic File Range Reading

This fundamental scenario shows how a simple file range read operation flows through the system:

![Basic File Read](../assets/images/structurizr/structurizr-BasicFileRead.svg)

**Key aspects:**
- Parameter validation happens at the abstract level
- Direct file system access through NIO
- Buffer management is handled consistently
- Minimal overhead for local file access

### HTTP Range Reading with Authentication

This scenario details the HTTP range request implementation that underlies all cloud storage access:

![HTTP Range Read](../assets/images/structurizr/structurizr-HttpRangeRead.svg)

**Key aspects:**
- Standard RFC 7233 range request protocol
- Pluggable authentication system integration
- HTTP client abstraction
- Proper error mapping from HTTP status codes

### S3 Authentication Flow

This scenario shows AWS credential resolution and S3 API authentication:

![S3 Authentication](../assets/images/structurizr/structurizr-S3Authentication.svg)

**Key aspects:**
- AWS SDK credential chain integration
- Automatic credential resolution
- S3-compatible storage support
- Secure authentication handling

## Performance-Oriented Scenarios

### Multi-Level Caching Scenario

This scenario demonstrates the sophisticated caching behavior with proper decorator ordering:

![Multi-Level Caching](../assets/images/structurizr/structurizr-MultiLevelCaching.svg)

**Key aspects:**
- Decorator pattern enabling composable caching layers
- Memory cache → disk cache → file reader delegation chain
- Efficient cache miss handling through the chain
- Optimal performance for repeated access patterns

### Cache Hit Scenario

This scenario shows the efficient cache hit path for optimal performance:

![Cache Hit](../assets/images/structurizr/structurizr-CacheHitScenario.svg)

**Key aspects:**
- Immediate response from memory cache
- No network or disk I/O required
- Sub-millisecond response times
- Optimal path for frequently accessed data

## Resilience Scenarios

### Disk Cache Recovery

This scenario shows resilience when cache files are deleted externally:

![Disk Cache Recovery](../assets/images/structurizr/structurizr-DiskCacheRecovery.svg)

**Key aspects:**
- Graceful handling of missing cache files
- Automatic fallback to delegate reader
- Cache rebuilding on demand
- Resilient operation under various failure conditions

## Performance Characteristics

The C4 dynamic views above illustrate the actual runtime behavior that delivers the following performance characteristics:

### Latency Patterns

| Scenario | First Request | Subsequent Requests | Optimization |
|----------|---------------|-------------------|--------------|
| **Local File** | ~1ms | ~1ms | OS page cache (no app caching needed) |
| **HTTP (uncached)** | ~100-500ms | ~100-500ms | Network latency |
| **HTTP (cached)** | ~100-500ms | ~0.1ms | Memory cache hit |
| **S3 (uncached)** | ~50-200ms | ~50-200ms | Network + processing |
| **S3 (cached)** | ~50-200ms | ~0.1ms | Memory/disk cache hit |

### Key Performance Features

- **Multi-level caching** as shown in the caching scenarios minimizes redundant operations
- **Decorator pattern** enables optimal performance composition
- **Block alignment** reduces request count for sequential access patterns
- **Authentication caching** in S3 scenarios reduces credential resolution overhead

## Runtime Design Principles

The dynamic views demonstrate several key design principles:

### Fail-Fast Validation
All scenarios show parameter validation at the abstract level before expensive operations.

### Graceful Degradation
Cache recovery scenarios show how the system gracefully handles component failures.

### Consistent Abstractions
All scenarios use the same `RangeReader` interface regardless of underlying storage.

### Composable Behavior
Decorator scenarios show how performance optimizations can be layered without coupling.

This runtime behavior enables the library to serve as a robust, high-performance foundation for cloud-native geospatial applications while maintaining simplicity and consistency across different storage backends.
