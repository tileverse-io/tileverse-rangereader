# Configuration

Optimize the Tileverse Range Reader for your specific use case with proper configuration.

## Performance Configuration

### Caching Strategies

#### Memory Caching

Configure in-memory caching for frequently accessed ranges:

```java
// Entry-based sizing
var reader = CachingRangeReader.builder(delegate)
    .maximumSize(1000)  // Limit number of cached ranges
    .build();

// Memory-based sizing
var reader = CachingRangeReader.builder(delegate)
    .maxSizeBytes(64 * 1024 * 1024)  // 64MB memory limit
    .build();

// Time-based expiration
var reader = CachingRangeReader.builder(delegate)
    .maximumSize(500)
    .expireAfterAccess(30, TimeUnit.MINUTES)
    .build();

// Adaptive sizing (uses soft references)
var reader = CachingRangeReader.builder(delegate)
    .softValues()  // Let GC manage memory pressure
    .build();
```

#### Disk Caching

Configure persistent disk caching for large datasets:

```java
// Basic disk caching
var reader = DiskCachingRangeReader.builder(delegate)
    .cacheDirectory("/tmp/rangereader-cache")
    .maxCacheSizeBytes(10L * 1024 * 1024 * 1024)  // 10GB
    .build();

// Temporary caching (deleted on close)
var reader = DiskCachingRangeReader.builder(delegate)
    .cacheDirectory("/tmp/temp-cache")
    .deleteOnClose()
    .build();
```

### Block Alignment

Configure block alignment for optimal cloud storage access:

```java
// Small blocks for random access
var reader = BlockAlignedRangeReader.builder()
    .delegate(delegate)
    .blockSize(64 * 1024)  // 64KB
    .build();

// Large blocks for sequential access
var reader = BlockAlignedRangeReader.builder()
    .delegate(delegate)
    .blockSize(8 * 1024 * 1024)  // 8MB
    .build();
```

## Cloud Provider Configuration

### Amazon S3

#### Basic Configuration

```java
var reader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/key"))
    .region(Region.US_WEST_2)
    .build();
```

#### Advanced S3 Configuration

```java
// Custom S3 client configuration
var s3Client = S3Client.builder()
    .region(Region.US_WEST_2)
    .credentialsProvider(ProfileCredentialsProvider.create())
    .overrideConfiguration(ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(30))
        .apiCallAttemptTimeout(Duration.ofSeconds(10))
        .retryPolicy(RetryPolicy.builder()
            .numRetries(3)
            .build())
        .build())
    .build();

var reader = S3RangeReader.builder()
    .client(s3Client)
    .bucket("my-bucket")
    .key("path/to/object")
    .build();
```

#### S3-Compatible Storage

```java
// MinIO or other S3-compatible services
var reader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/key"))
    .endpoint(URI.create("http://localhost:9000"))
    .region(Region.US_EAST_1)
    .forcePathStyle()  // Required for MinIO
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("minioadmin", "minioadmin")))
    .build();
```

### Azure Blob Storage

#### Connection String Configuration

```java
var reader = AzureBlobRangeReader.builder()
    .uri(URI.create("https://account.blob.core.windows.net/container/blob"))
    .connectionString("DefaultEndpointsProtocol=https;AccountName=account;...")
    .build();
```

#### SAS Token Configuration

```java
var reader = AzureBlobRangeReader.builder()
    .uri(URI.create("https://account.blob.core.windows.net/container/blob"))
    .sasToken("sv=2020-08-04&ss=b&srt=sco&sp=r&se=...")
    .build();
```

### Google Cloud Storage

#### Default Credentials

```java
var reader = GoogleCloudStorageRangeReader.builder()
    .uri(URI.create("gs://bucket/object"))
    .build();  // Uses Application Default Credentials
```

#### Service Account

```java
var credentials = ServiceAccountCredentials.fromStream(
    new FileInputStream("service-account.json"));

var reader = GoogleCloudStorageRangeReader.builder()
    .uri(URI.create("gs://bucket/object"))
    .credentials(credentials)
    .build();
```

## HTTP Configuration

### Basic HTTP

```java
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/data.bin"))
    .build();
```

### HTTP with Authentication

```java
// Basic authentication
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/data.bin"))
    .withBasicAuth("username", "password")
    .build();

// Bearer token
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://api.example.com/data.bin"))
    .withBearerToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    .build();

// API key
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://api.example.com/data.bin"))
    .withApiKey("X-API-Key", "your-api-key")
    .build();

// Custom headers
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://api.example.com/data.bin"))
    .withCustomHeaders(Map.of(
        "X-Custom-Auth", "auth-value",
        "X-Client-Version", "1.0"
    ))
    .build();
```

### HTTP Client Tuning

```java
// For high-throughput scenarios
var reader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/data.bin"))
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(30))
    .maxRetries(3)
    .build();
```

## Optimal Configurations by Use Case

### High-Frequency Random Access

Optimize for applications that make many small, random reads:

```java
// ✅ CORRECT: Optimize cloud storage for high-frequency random access
var reader = BlockAlignedRangeReader.builder()
    .delegate(CachingRangeReader.builder(
        S3RangeReader.builder()
            .uri(URI.create("s3://bucket/data.bin"))
            .region(Region.US_WEST_2)
            .build())
        .maximumSize(2000)  // Large memory cache
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build())
    .blockSize(64 * 1024)  // Small blocks for memory cache
    .build();
```

> **Note**: Local files don't benefit from caching since the OS already provides efficient file caching.

### Large Sequential Reads

Optimize for applications that read large chunks sequentially:

```java
// ✅ CORRECT: Optimize HTTP for large sequential reads
var reader = BlockAlignedRangeReader.builder()
    .delegate(DiskCachingRangeReader.builder(
        HttpRangeReader.builder()
            .uri(URI.create("https://cdn.example.com/large-dataset.bin"))
            .withBearerToken(authToken)
            .build())
        .maxCacheSizeBytes(5L * 1024 * 1024 * 1024)  // 5GB disk cache
        .build())
    .blockSize(8 * 1024 * 1024)  // Large blocks (8MB)
    .build();
```

### Cloud Storage with Resilience

Optimize for cloud storage with network resilience:

```java
// ✅ CORRECT: Multi-level caching with proper decorator order
var reader = BlockAlignedRangeReader.builder()
    .delegate(CachingRangeReader.builder(
        BlockAlignedRangeReader.builder()
            .delegate(DiskCachingRangeReader.builder(
                S3RangeReader.builder()
                    .uri(s3Uri)
                    .region(region)
                    .build())
                .maxCacheSizeBytes(20L * 1024 * 1024 * 1024)  // 20GB persistent cache
                .build())
            .blockSize(4 * 1024 * 1024)  // 4MB blocks for disk cache
            .build())
        .maximumSize(1000)  // Memory cache entries
        .expireAfterAccess(2, TimeUnit.HOURS)
        .build())
    .blockSize(64 * 1024)  // 64KB blocks for memory cache
    .build();
```

### Memory-Constrained Environments

Optimize for environments with limited memory:

```java
// ✅ CORRECT: Memory-constrained cloud storage optimization
var reader = BlockAlignedRangeReader.builder()
    .delegate(CachingRangeReader.builder(
        BlockAlignedRangeReader.builder()
            .delegate(DiskCachingRangeReader.builder(
                AzureBlobRangeReader.builder()
                    .uri(URI.create("https://account.blob.core.windows.net/container/data.bin"))
                    .sasToken(sasToken)
                    .build())
                .maxCacheSizeBytes(1024 * 1024 * 1024)  // 1GB disk cache
                .deleteOnClose()  // Clean up cache
                .build())
            .blockSize(1024 * 1024)  // 1MB blocks for disk
            .build())
        .maximumSize(50)  // Small memory cache
        .softValues()     // Allow GC to reclaim memory
        .build())
    .blockSize(64 * 1024)  // 64KB blocks for memory
    .build();
```

## Configuration Guidelines

### Block Size Selection

| Data Source | Recommended Block Size | Rationale |
|-------------|------------------------|-----------|
| **Local Files** | 64KB - 1MB | Balance I/O efficiency and memory |
| **HTTP** | 256KB - 1MB | Reduce request overhead |
| **S3** | 1MB - 8MB | Minimize API calls |
| **Azure Blob** | 1MB - 4MB | Optimize for Azure's performance |
| **Google Cloud** | 1MB - 8MB | Balance throughput and latency |

### Cache Size Guidelines

| Use Case | Memory Cache | Disk Cache |
|----------|--------------|------------|
| **Interactive Applications** | 50-200 entries | 1-5GB |
| **Batch Processing** | 10-50 entries | 10-50GB |
| **Server Applications** | 500-2000 entries | 5-20GB |
| **Mobile/Embedded** | 10-50 entries | 100MB-1GB |

### ⚠️ CRITICAL: Decorator Stacking Order

**BlockAlignedRangeReader must ALWAYS wrap caching decorators to prevent overlapping ranges!**

```
Application
    ↓
BlockAlignedRangeReader (64KB blocks for memory)
    ↓
CachingRangeReader (memory - non-overlapping blocks)
    ↓
BlockAlignedRangeReader (1MB blocks for disk)
    ↓
DiskCachingRangeReader (persistent - non-overlapping blocks)
    ↓
BaseReader (source-specific)
    ↓
Data Source
```

**Why this order matters:**
- **Prevents cache pollution**: No overlapping ranges stored in cache
- **Optimal memory usage**: Each cache layer stores aligned, non-overlapping blocks
- **Maximum performance**: Clean cache hits without range conflicts

## Monitoring and Diagnostics

### Cache Statistics

```java
if (reader instanceof CachingRangeReader cachingReader) {
    var stats = cachingReader.getCacheStats();
    System.out.println("Hit rate: " + stats.hitRate());
    System.out.println("Cache size: " + stats.estimatedSize());
    System.out.println("Eviction count: " + stats.evictionCount());
}
```

### Source Identification

```java
// Useful for debugging and logging
String sourceId = reader.getSourceIdentifier();
System.out.println("Reading from: " + sourceId);
// Output examples:
// "file:///path/to/file.bin"
// "memory-cached:disk-cached:block-aligned:s3://bucket/key"
```

## Environment Variables

Set these environment variables for global configuration:

```bash
# AWS credentials (for S3)
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-west-2

# Azure credentials
export AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;..."

# Google Cloud credentials
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# HTTP proxy settings
export HTTP_PROXY=http://proxy.company.com:8080
export HTTPS_PROXY=https://proxy.company.com:8080
```

## Resource Management Patterns

### Short-term Usage

For processing individual files, batch jobs, or one-time operations, use try-with-resources:

```java
// ✅ Short-term access pattern
public ByteBuffer processFile(URI dataSource, long offset, int length) throws IOException {
    try (RangeReader reader = S3RangeReader.builder()
            .uri(dataSource)
            .withCaching()
            .build()) {
        
        // Validate parameters
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Offset and length must be non-negative");
        }
        
        // Check bounds  
        if (offset >= reader.size()) {
            return ByteBuffer.allocate(0); // Empty buffer for reads beyond EOF
        }
        
        // Perform read and return
        return reader.readRange(offset, length);
        
    } catch (IOException e) {
        logger.error("Failed to read range [{}:{}] from {}: {}", 
            offset, length, dataSource, e.getMessage());
        throw e;
    }
}
```

**Best for**:
- Batch processing jobs
- One-time file analysis
- Command-line tools
- Simple applications

### Long-term Usage

For server applications, services, or long-running processes, manage lifecycle explicitly:

```java
// ✅ Long-term access pattern
public class TileServer implements Closeable {
    private final RangeReader reader;
    private final ExecutorService executor;
    
    public TileServer(URI dataSource) throws IOException {
        // Create optimized reader for server use
        this.reader = CachingRangeReader.builder(
            DiskCachingRangeReader.builder(
                S3RangeReader.builder()
                    .uri(dataSource)
                    .region(Region.US_WEST_2)
                    .build())
                .maxCacheSizeBytes(5L * 1024 * 1024 * 1024) // 5GB cache
                .build())
            .maximumSize(2000)  // Large memory cache
            .recordStats()      // Monitor performance
            .build();
            
        this.executor = Executors.newFixedThreadPool(10);
    }
    
    public CompletableFuture<byte[]> getTileAsync(long offset, int size) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Reader stays open for multiple requests
                ByteBuffer buffer = reader.readRange(offset, size);
                byte[] result = new byte[buffer.remaining()];
                buffer.get(result);
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    @Override
    public void close() throws IOException {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            reader.close(); // ⚠️ Essential: close when service shuts down
        }
    }
}
```

**Best for**:
- Web servers
- Microservices
- Desktop applications
- Data processing services

### Key Principles

1. **Short-term**: Use try-with-resources for automatic cleanup
2. **Long-term**: Explicit lifecycle management with proper shutdown hooks
3. **Always close**: Ensure readers are closed to release resources
4. **Thread safety**: All readers support concurrent access safely

## Next Steps

- **[Authentication](authentication.md)**: Detailed authentication setup
- **[Troubleshooting](troubleshooting.md)**: Common configuration issues
- **[Performance Guide](../developer-guide/performance.md)**: Advanced optimization techniques