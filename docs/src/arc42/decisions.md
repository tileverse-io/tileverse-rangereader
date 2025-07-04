# Architecture Decisions

## Overview

This section documents the key architectural decisions for the Tileverse Range Reader library, providing context, rationale, and consequences for each choice.

## ADR-001: Create a Unified Range-Reading Abstraction

### Status
**Accepted** - Core architectural decision

### Context

The Java geospatial ecosystem has suffered from significant fragmentation in range-based I/O operations. Each major library has developed its own isolated solution:

- **imageio-ext**: Internal `RangeReader` SPI (not reusable)
- **netCDF-Java**: Custom `cdms3://` protocol (Zarr-specific)
- **simonpoole/pmtiles-reader**: `FileChannel` wrapper (user implementation required)
- **Apache Parquet**: `SeekableInputStream` (no standard cloud implementation)

This fragmentation creates code duplication, inconsistent APIs, and high barriers to entry for new format libraries.

### Decision

**Create a standalone, lightweight library that provides a unified abstraction for range-based I/O operations across multiple storage backends**, comparable to Python's **fsspec** library.

### Consequences

**Positive:**
- Eliminates code duplication across the ecosystem
- Provides consistent API for all cloud-native formats
- Dramatically reduces integration complexity for format libraries
- Enables format libraries to focus on parsing logic instead of I/O plumbing
- Creates opportunity for ecosystem-wide performance optimizations

**Negative:**
- Requires initial development investment
- Must maintain compatibility across multiple cloud providers
- Needs to balance simplicity with power user requirements

**Risks Mitigated:**
- Continued ecosystem fragmentation
- High barriers to entry for cloud-native format development
- Vendor lock-in to specific cloud providers

---

## ADR-002: Use Decorator Pattern for Composable Functionality

### Status
**Accepted** - Proven pattern from existing implementations

### Context

The strategic analysis revealed that successful Java implementations (imageio-ext, netCDF-Java) have independently converged on decorator-like patterns for composing I/O functionality. Performance optimizations like caching, block alignment, and disk persistence need to be composable and optional.

### Decision

**Implement the core library using the Decorator pattern**, where performance optimizations are implemented as decorators that wrap base implementations.

### Rationale

- **Proven Success**: imageio-ext's `RangeReader` pattern demonstrates effectiveness
- **Composability**: Users can combine optimizations as needed
- **Separation of Concerns**: Each decorator handles a single responsibility
- **Extensibility**: New optimizations can be added without modifying existing code

### Implementation

```java
// Base abstraction
public interface RangeReader extends Closeable {
    int readRange(long offset, int length, ByteBuffer buffer);
    // ...
}

// Base implementations
public class S3RangeReader extends AbstractRangeReader { /* ... */ }

// Composable decorators
public class CachingRangeReader extends AbstractRangeReader { /* ... */ }
public class BlockAlignedRangeReader extends AbstractRangeReader { /* ... */ }
public class DiskCachingRangeReader extends AbstractRangeReader { /* ... */ }

// Composition
RangeReader reader = 
    BlockAlignedRangeReader.builder()
        .delegate(CachingRangeReader.builder(baseReader).build())
        .blockSize(64 * 1024)
        .build();
```

### Consequences

**Positive:**
- Flexible composition of optimizations
- Clear separation of concerns
- Easy to test individual components
- Extensible architecture for future optimizations

**Negative:**
- Slightly more complex API for advanced users
- Requires understanding of decorator ordering principles

---

## ADR-003: Use Official Cloud SDKs vs. Generic HTTP

### Status
**Accepted** - Required for production reliability

### Context

Cloud storage providers offer both native SDKs and S3-compatible APIs. We could use either:
1. **Generic HTTP approach**: Single HTTP client with range headers
2. **Native SDKs**: AWS SDK v2, Azure SDK, Google Cloud SDK
3. **Hybrid approach**: Native SDKs with S3-compatible fallbacks

### Decision

**Use official cloud provider SDKs as the primary implementation**, with HTTP-based implementations for generic servers and fallback scenarios.

### Rationale

**From Strategic Analysis:**
- **Authentication**: SDKs handle complex credential chains (IAM roles, STS, etc.)
- **Retry Logic**: Built-in exponential backoff and error handling
- **Performance**: Connection pooling and request optimization
- **Features**: Advanced features like server-side encryption, lifecycle policies
- **Support**: Official support and updates for API changes

**Native SDK Benefits:**
- AWS SDK v2: Full credential chain, IAM integration, S3 transfer acceleration
- Azure SDK: SAS tokens, Azure AD, managed identity support  
- Google Cloud SDK: Service accounts, application default credentials

### Implementation Strategy

```java
// S3 Module - Native AWS SDK
public class S3RangeReader extends AbstractRangeReader {
    private final S3Client s3Client;
    // Uses GetObjectRequest.builder().range("bytes=offset-end")
}

// HTTP Module - Generic fallback
public class HttpRangeReader extends AbstractRangeReader {
    private final HttpClient httpClient;
    // Uses Range: bytes=offset-end header
}

// Factory selects appropriate implementation
public static RangeReader fromUri(String uri) {
    if (uri.startsWith("s3://")) return S3RangeReader.create(uri);
    return HttpRangeReader.create(uri);
}
```

### Consequences

**Positive:**
- Production-ready authentication and error handling
- Optimal performance for each cloud provider
- Access to advanced cloud features
- Official support and updates

**Negative:**
- Larger dependency footprint for cloud modules
- Provider-specific configuration options
- More complex testing (requires cloud credentials)

---

## ADR-004: Modular Architecture with Incremental Adoption

### Status
**Accepted** - Enables minimal dependency footprint

### Context

Different users have different needs:
- **Format libraries**: May only need one cloud provider
- **Enterprise applications**: May need all providers
- **Embedded systems**: Need minimal dependencies
- **Research tools**: May only need local file access

### Decision

**Use a modular architecture** that enables incremental adoption and minimal dependency footprint.

### Module Structure

```
tileverse-rangereader-core     # Required: base abstractions, local files, HTTP
tileverse-rangereader-s3       # Optional: Amazon S3 support
tileverse-rangereader-azure    # Optional: Azure Blob Storage support  
tileverse-rangereader-gcs      # Optional: Google Cloud Storage support
tileverse-rangereader-all      # Convenience: aggregates all modules
```

### Adoption Path

```xml
<!-- Minimal: Local files and HTTP only -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-core</artifactId>
</dependency>

<!-- Add S3 support -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-s3</artifactId>
</dependency>

<!-- Full functionality -->
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-all</artifactId>
</dependency>
```

### Consequences

**Positive:**
- Minimal dependency footprint for simple use cases
- Clear separation of concerns
- Independent versioning of cloud providers
- Enterprise-friendly (can exclude unnecessary providers)

**Negative:**
- Slightly more complex dependency management
- Need to maintain module boundaries
- More artifacts to release and maintain

---

## ADR-005: Thread Safety by Design

### Status
**Accepted** - Server environment requirement

### Context

The strategic analysis emphasizes server environments like GeoServer as key use cases. These applications require thread-safe I/O operations to handle concurrent requests efficiently.

### Decision

**All `RangeReader` implementations must be thread-safe** and support concurrent access patterns.

### Implementation Strategy

1. **Immutable State**: Builder pattern creates immutable readers
2. **Thread-Safe Caching**: Use Caffeine's thread-safe cache implementations
3. **Connection Pooling**: Leverage SDK connection pools
4. **Stateless Operations**: Avoid mutable instance state

```java
public class S3RangeReader extends AbstractRangeReader {
    private final S3Client s3Client;          // Thread-safe
    private final String bucket;              // Immutable
    private final String key;                 // Immutable
    private final Cache<Range, ByteBuffer> cache; // Thread-safe
    
    // All operations are stateless and thread-safe
    public int readRange(long offset, int length, ByteBuffer buffer) {
        // Thread-safe implementation
    }
}
```

### Testing Strategy

- Concurrent stress tests with multiple threads
- Race condition detection with tools like `ThreadSanitizer`
- Performance testing under concurrent load

### Consequences

**Positive:**
- Safe for server environments (GeoServer, microservices)
- Better performance through concurrent access
- Suitable for reactive and async programming models

**Negative:**
- More complex implementation (must consider thread safety)
- Additional testing overhead
- Cannot use simple mutable state patterns

---

## ADR-006: Builder Pattern for Configuration

### Status
**Accepted** - Balances simplicity with flexibility

### Context

Different configuration approaches were considered:
1. **Constructor parameters**: Simple but inflexible
2. **Factory methods**: Good for simple cases
3. **Builder pattern**: More verbose but flexible
4. **Configuration objects**: Complex but powerful

### Decision

**Use fluent builder pattern as the primary configuration mechanism**, with factory methods for simple cases.

### Implementation

```java
// Primary: Builder pattern for flexibility
S3RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/key"))
    .region(Region.US_WEST_2)
    .credentialsProvider(customProvider)
    .withCaching()
    .maxCacheSize(1000)
    .build();

// Convenience: Factory for simple cases  
RangeReader reader = RangeReader.fromUri("s3://bucket/key");
```

### Rationale

- **Discoverability**: IDEs can autocomplete available options
- **Validation**: Builders can validate configuration before construction
- **Flexibility**: Easy to add new options without breaking existing code
- **Type Safety**: Compile-time validation of parameter types

### Consequences

**Positive:**
- Self-documenting API through method names
- Easy to extend with new configuration options
- Type-safe configuration
- Follows established Java patterns

**Negative:**
- More verbose than simple constructors
- Requires more code to implement builders

---

## ADR-007: Block Alignment Strategy

### Status
**Accepted** - Critical for cloud storage performance

### Context

Cloud storage providers optimize for larger, aligned requests. Small, random requests perform poorly. Different caching layers benefit from different block sizes:
- **Memory cache**: Smaller blocks (64KB) for granular caching
- **Disk cache**: Larger blocks (1MB+) for I/O efficiency  
- **Network requests**: Aligned to cloud provider optimizations

### Decision

**Implement block alignment as a decorator with configurable block sizes**, enabling different alignment strategies for different caching layers.

### Critical Ordering

```java
// ✅ CORRECT: Block aligners wrap caches (prevents overlapping ranges)
BlockAlignedRangeReader(64KB)          // Memory cache blocks
  └── CachingRangeReader               // Memory cache
      └── BlockAlignedRangeReader(1MB) // Disk cache blocks  
          └── DiskCachingRangeReader   // Disk cache
              └── BaseReader           // Cloud storage

// ❌ WRONG: Caches wrap block aligners (creates overlapping ranges)
CachingRangeReader
  └── BlockAlignedRangeReader          // Would cache overlapping aligned blocks
```

### Consequences

**Positive:**
- Optimal performance for cloud storage access patterns
- Flexible block sizes for different caching strategies
- Prevents cache pollution from overlapping ranges
- Dramatic reduction in network requests

**Negative:**
- Complex optimal configuration requires understanding
- Incorrect decorator order can harm performance
- Memory overhead from larger aligned blocks

---

## ADR-008: Ecosystem Integration Strategy

### Status
**Accepted** - Long-term ecosystem impact

### Context

The strategic analysis identifies ecosystem fragmentation as the core problem. Success requires broader adoption beyond just creating another library.

### Decision

**Actively pursue integration with existing Java geospatial libraries** to consolidate around a unified I/O foundation.

### Integration Targets

1. **imageio-ext**: Consolidate multiple `RangeReader` implementations
2. **GeoTools**: Adopt unified abstraction in community modules
3. **Apache SIS**: Collaboration on cloud-native format support
4. **LocationTech projects**: Propose as shared infrastructure

### Strategy

1. **Demonstrate Value**: Build reference implementations for key formats
2. **Neutral Governance**: Contribute to neutral foundation (LocationTech, OSGeo)
3. **Community Engagement**: Present at conferences, collaborate with maintainers
4. **Migration Support**: Provide compatibility layers and migration tools

### Success Metrics

- Adoption by at least 2 major Java geospatial libraries within 2 years
- Replacement of existing fragmented solutions
- Contribution to neutral open source foundation
- Community recognition as standard I/O layer

### Consequences

**Positive:**
- Ecosystem-wide impact beyond single library
- Reduced maintenance burden across projects
- Standardization of cloud-native access patterns
- Accelerated innovation in format support

**Negative:**
- Requires significant community engagement effort
- Success depends on external adoption
- Must maintain compatibility with multiple libraries

---

## Decision Summary

| Decision | Status | Impact | Risk Level |
|----------|--------|--------|------------|
| **Unified Abstraction** | Accepted | High | Medium |
| **Decorator Pattern** | Accepted | High | Low |
| **Official SDKs** | Accepted | Medium | Low |
| **Modular Architecture** | Accepted | Medium | Low |
| **Thread Safety** | Accepted | High | Medium |
| **Builder Pattern** | Accepted | Low | Low |
| **Block Alignment** | Accepted | High | Medium |
| **Ecosystem Integration** | Accepted | High | High |

These architectural decisions collectively enable the library to serve as the unified I/O foundation that the Java geospatial ecosystem has been missing, while maintaining the performance, reliability, and usability requirements identified in the strategic analysis.
