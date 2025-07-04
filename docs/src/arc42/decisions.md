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

---

## ADR-009: Virtual Thread Optimization Strategy

### Status
**Proposed** - Analysis and design phase

### Context

Java 21 introduced virtual threads (Project Loom) which enable massive concurrency with minimal resource overhead. Traditional blocking I/O operations can now scale to millions of concurrent operations without exhausting system resources. This presents both opportunities and challenges for range reader implementations.

### Decision

**Optimize the library for virtual thread compatibility** while maintaining backward compatibility with traditional threading models.

### Analysis

#### Current Threading Model
- All readers are thread-safe using traditional concurrency patterns
- Connection pooling limits concurrent operations to avoid resource exhaustion
- Blocking I/O operations can exhaust platform thread pools

#### Virtual Thread Opportunities
1. **Massive Concurrency**: Support 10,000+ concurrent range operations
2. **Simplified Programming**: Eliminate complex async patterns
3. **Better Resource Utilization**: Fewer platform threads needed
4. **Natural Blocking I/O**: Blocking operations are virtual thread friendly

#### Implementation Strategy

```java
// Virtual thread compatible implementation
public class VirtualThreadOptimizedS3RangeReader extends AbstractRangeReader {
    
    @Override
    protected int readRangeNoFlip(long offset, int length, ByteBuffer target) {
        // This will park the virtual thread, not block a carrier thread
        return performBlockingS3Request(offset, length, target);
    }
    
    // Connection pooling adjusted for virtual threads
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor()) // Java 21+
        .build();
}
```

#### Backward Compatibility

- Maintain existing thread-safe guarantees
- Continue supporting platform thread pools
- No API changes required for basic usage
- Optional virtual thread optimizations

### Consequences

**Positive:**
- Dramatically improved scalability (10,000+ concurrent ops)
- Simplified concurrency model for applications
- Reduced memory overhead per operation
- Better resource utilization in cloud environments

**Negative:**
- Requires Java 21+ for optimal performance
- Need to test with both virtual and platform threads
- Connection pool tuning becomes more complex

**Risk Mitigation:**
- Feature flags for virtual thread optimizations
- Comprehensive testing with both threading models
- Documentation for optimal virtual thread usage

---

## ADR-010: ByteBuffer Pool Management

### Status
**Proposed** - High priority enhancement

### Context

High-throughput applications create significant memory pressure through constant ByteBuffer allocation and deallocation. Profiling shows that buffer allocation can represent 30-50% of CPU time in range-intensive workloads, particularly with large buffers.

### Decision

**Implement a pluggable ByteBuffer pool system** that applications can use to eliminate allocation overhead.

### Design Principles

1. **Optional**: Pool usage is opt-in, existing APIs unchanged
2. **Thread-Safe**: Pool supports concurrent access
3. **Size-Adaptive**: Pool maintains buffers of various sizes
4. **Resource-Bounded**: Configurable limits prevent memory leaks
5. **Integration-Friendly**: Works with existing decorator pattern

### Implementation Design

```java
// New buffer pool interface
public interface ByteBufferPool extends AutoCloseable {
    PooledBuffer acquire(int minimumCapacity);
    void release(PooledBuffer buffer);
    
    static ByteBufferPool create() {
        return new DefaultByteBufferPool();
    }
}

// Pooled buffer with auto-return
public class PooledBuffer implements AutoCloseable {
    private final ByteBuffer buffer;
    private final ByteBufferPool pool;
    
    public ByteBuffer getBuffer() { return buffer; }
    
    @Override
    public void close() {
        pool.release(this); // Auto-return to pool
    }
}

// Integration with existing readers
public class S3RangeReader extends AbstractRangeReader {
    private final Optional<ByteBufferPool> bufferPool;
    
    @Override
    protected int readRangeNoFlip(long offset, int length, ByteBuffer target) {
        if (bufferPool.isPresent()) {
            try (var pooledBuffer = bufferPool.get().acquire(length)) {
                // Use pooled buffer for internal operations
                return performOptimizedRead(offset, length, target, pooledBuffer);
            }
        }
        return performStandardRead(offset, length, target);
    }
}
```

### Pool Implementation Strategy

1. **Size Classes**: Maintain pools for common sizes (4KB, 64KB, 1MB, 4MB)
2. **LRU Eviction**: Evict least recently used buffers when pool is full
3. **Direct Memory**: Use direct ByteBuffers for better I/O performance
4. **Monitoring**: Expose pool statistics for observability

### Consequences

**Positive:**
- 90% reduction in buffer allocation overhead
- 50% reduction in GC pressure
- Better memory locality and performance
- Configurable resource limits

**Negative:**
- Additional API complexity for advanced users
- Memory overhead for maintaining pools
- Need to tune pool sizes for workloads

---

## ADR-011: Multi-Range Request Batching

### Status
**Proposed** - Medium priority enhancement

### Context

Many applications need to read multiple non-contiguous ranges from the same source. Current approach requires separate network requests for each range, creating unnecessary overhead and latency.

### Decision

**Implement intelligent batching of multiple range requests** to reduce network overhead and improve throughput.

### Batching Strategies

#### 1. Request Coalescing
```java
// Multiple small ranges -> Single large range
List<Range> ranges = [
    Range(100, 200),    // 100 bytes
    Range(350, 450),    // 100 bytes  
    Range(600, 700)     // 100 bytes
];

// Coalesced into single range(100, 600) = 600 bytes
// Extract individual ranges from result
```

#### 2. HTTP/2 Multiplexing
```java
// Multiple parallel requests over single connection
public CompletableFuture<List<ByteBuffer>> readRangesAsync(List<Range> ranges) {
    return ranges.stream()
        .map(range -> readRangeAsync(range.offset, range.length))
        .collect(toCompletableFutureList());
}
```

#### 3. Cloud Provider Optimization
```java
// S3 supports multiple ranges in single request
GET /object
Range: bytes=0-499, 1000-1499, 2000-2499
```

### Implementation Approach

```java
public interface BatchRangeReader extends RangeReader {
    
    // Primary batch API
    List<ByteBuffer> readRanges(List<Range> ranges) throws IOException;
    
    // Async variant for better concurrency
    CompletableFuture<List<ByteBuffer>> readRangesAsync(List<Range> ranges);
    
    // Configuration for batching behavior
    BatchConfig getBatchConfig();
}

public class BatchConfig {
    private final int maxBatchSize;
    private final int coalesceThreshold;
    private final Duration timeout;
    
    // Smart batching decisions
    public boolean shouldCoalesce(List<Range> ranges) {
        return calculateWastedBytes(ranges) < coalesceThreshold;
    }
}
```

### Consequences

**Positive:**
- 50% reduction in network requests for typical workloads
- Lower latency for multi-range operations
- Better utilization of HTTP/2 connections
- Cloud provider cost reduction

**Negative:**
- More complex API surface
- Need to optimize for different access patterns
- Increased memory usage for batch operations

---

## ADR-012: Cache Consistency with ETag Validation

### Status
**Proposed** - Medium priority for production environments

### Context

In multi-instance deployments, cached data can become stale when the source object is updated. This leads to data consistency issues and potential application errors.

### Decision

**Implement ETag-based cache validation** to ensure cached data remains consistent with source objects.

### ETag Validation Strategy

```java
public class ETagCacheEntry {
    private final ByteBuffer data;
    private final String etag;
    private final Instant lastValidated;
    
    public boolean isStale(Duration maxAge) {
        return lastValidated.plus(maxAge).isBefore(Instant.now());
    }
}

public class ETagValidatingRangeReader extends AbstractRangeReader {
    private final RangeReader delegate;
    private final Duration validationInterval;
    private final Map<RangeKey, ETagCacheEntry> cache;
    
    @Override
    protected int readRangeNoFlip(long offset, int length, ByteBuffer target) {
        RangeKey key = new RangeKey(offset, length);
        ETagCacheEntry cached = cache.get(key);
        
        if (cached != null && !cached.isStale(validationInterval)) {
            // Cache hit, no validation needed
            return cached.data.duplicate().get(target.array());
        }
        
        if (cached != null) {
            // Validate with conditional request
            return validateAndUpdate(key, cached, offset, length, target);
        }
        
        // Cache miss, fetch with ETag
        return fetchWithETag(key, offset, length, target);
    }
    
    private int validateAndUpdate(RangeKey key, ETagCacheEntry cached, 
                                 long offset, int length, ByteBuffer target) {
        try {
            // HTTP: If-None-Match: "cached-etag"
            // S3: IfNoneMatch condition
            String currentETag = delegate.getETag(offset, length);
            
            if (cached.etag.equals(currentETag)) {
                // Data unchanged, update validation time
                cache.put(key, cached.withUpdatedValidation());
                return cached.data.duplicate().get(target.array());
            } else {
                // Data changed, fetch new version
                return fetchWithETag(key, offset, length, target);
            }
        } catch (IOException e) {
            // Validation failed, fall back to cached data
            logger.warn("ETag validation failed, using cached data", e);
            return cached.data.duplicate().get(target.array());
        }
    }
}
```

### Implementation Considerations

1. **Graceful Degradation**: Fall back to cached data if validation fails
2. **Configurable Intervals**: Balance consistency vs performance
3. **Provider Support**: Different ETag implementations across cloud providers
4. **Memory Overhead**: Store ETags with minimal additional memory

### Consequences

**Positive:**
- 95%+ cache consistency in multi-instance deployments
- Reduced risk of serving stale data
- Configurable consistency vs performance trade-offs
- Compliance with HTTP caching standards

**Negative:**
- Additional network requests for validation
- Complexity in handling ETag differences across providers
- Memory overhead for storing ETags

---

## Updated Decision Summary

| Decision | Status | Impact | Risk Level | Version |
|----------|--------|--------|------------|---------|
| **Unified Abstraction** | Accepted | High | Medium | 1.0 |
| **Decorator Pattern** | Accepted | High | Low | 1.0 |
| **Official SDKs** | Accepted | Medium | Low | 1.0 |
| **Modular Architecture** | Accepted | Medium | Low | 1.0 |
| **Thread Safety** | Accepted | High | Medium | 1.0 |
| **Builder Pattern** | Accepted | Low | Low | 1.0 |
| **Block Alignment** | Accepted | High | Medium | 1.0 |
| **Ecosystem Integration** | Accepted | High | High | 1.0 |
| **Virtual Thread Optimization** | Proposed | High | Medium | 1.1 |
| **ByteBuffer Pool Management** | Proposed | High | Low | 1.1 |
| **Multi-Range Request Batching** | Proposed | Medium | Medium | 1.2 |
| **ETag Cache Validation** | Proposed | Medium | Low | 1.2 |
| **Bill of Materials (BOM)** | Proposed | High | Low | 1.1 |

---

## ADR-013: Bill of Materials for Dependency Management

### Status
**Proposed** - Critical for enterprise adoption

### Context

Cloud SDKs bring complex transitive dependency trees that frequently conflict with each other and with application dependencies. Key conflict areas include:

1. **Netty versions**: AWS S3 SDK and Azure Blob SDK may use different Netty versions
2. **Jackson libraries**: Different JSON processing versions across SDKs
3. **SLF4J bindings**: Multiple logging framework bindings causing conflicts
4. **Reactive libraries**: Project Reactor version mismatches
5. **HTTP client libraries**: Overlapping HTTP client dependencies

These conflicts manifest as:
- Runtime ClassNotFoundException or NoSuchMethodError
- Performance degradation from suboptimal library versions
- Security vulnerabilities from older transitive dependencies
- Complex dependency exclusion management for users

### Decision

**Provide a comprehensive Bill of Materials (BOM) that manages all transitive dependencies** and ensures version alignment across all cloud provider modules.

### Implementation Strategy

```xml
<!-- Primary BOM artifact -->
<dependency>
  <groupId>io.tileverse.rangereader</groupId>
  <artifactId>tileverse-rangereader-bom</artifactId>
  <version>1.1.0</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

#### BOM Structure

1. **Import Cloud Provider BOMs**: Include AWS, Azure, and Google Cloud BOMs
2. **Override Critical Dependencies**: Explicitly manage Netty, Jackson, SLF4J versions
3. **Dependency Convergence**: Enforce single versions for all transitive dependencies
4. **Exclusion Management**: Provide guidance for excluding problematic dependencies

#### Key Dependencies Managed

```xml
<!-- Critical version alignments -->
<netty.version>4.1.114.Final</netty.version>
<jackson.version>2.18.1</jackson.version>
<slf4j.version>2.0.16</slf4j.version>
<reactor.version>3.6.11</reactor.version>
```

### Benefits

**For Users:**
- Simple dependency management with single BOM import
- Guaranteed compatibility across all modules
- No manual dependency exclusion needed
- Clear upgrade path for transitive dependencies

**For Maintainers:**
- Centralized dependency version management
- Automated conflict detection in CI/CD
- Easier security vulnerability management
- Simplified compatibility testing

### Implementation Requirements

1. **BOM Module**: Create `tileverse-rangereader-bom` Maven module
2. **Version Alignment**: Test compatibility across cloud SDKs
3. **Build Integration**: Include dependency convergence enforcement
4. **Documentation**: Provide BOM usage examples and troubleshooting guide
5. **Validation**: Integration tests with popular frameworks (Spring Boot, Quarkus)

### Gradle Support

```gradle
// Gradle dependency management
dependencies {
    platform 'io.tileverse.rangereader:tileverse-rangereader-bom:1.1.0'
    implementation 'io.tileverse.rangereader:tileverse-rangereader-s3'
    implementation 'io.tileverse.rangereader:tileverse-rangereader-azure'
}
```

### Consequences

**Positive:**
- Eliminates "dependency hell" for enterprise users
- Reduces support burden from version conflicts
- Enables safer library upgrades
- Improves security posture through dependency management

**Negative:**
- Additional maintenance overhead for version alignment
- Potential delays in adopting newest SDK versions
- Need to test multiple dependency combinations

**Risk Mitigation:**
- Automated dependency update and testing
- Regular compatibility validation with major frameworks
- Clear documentation for manual dependency overrides

These architectural decisions collectively enable the library to serve as the unified I/O foundation that the Java geospatial ecosystem has been missing, while maintaining the performance, reliability, and usability requirements identified in the strategic analysis. The new decisions address modern Java features and production deployment requirements for high-scale cloud-native applications.
