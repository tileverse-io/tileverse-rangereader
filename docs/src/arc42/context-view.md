# System Context

## Business Context

### The Ecosystem Problem

The Tileverse Range Reader library exists to solve a critical architectural gap in the Java geospatial ecosystem. The domain has shifted from traditional "download-and-process" workflows to **cloud-native data access patterns**, but Java lacks a unified abstraction layer comparable to Python's **fsspec**.

## System Context Diagram

![System Context Diagram](../assets/images/structurizr/structurizr-SystemContext.svg)

The system context diagram shows how the Tileverse Range Reader library fits into the broader ecosystem, connecting applications and developers to various data sources through a unified interface.

### Current Ecosystem Fragmentation

Each major Java geospatial library has developed its own isolated I/O solution:

```mermaid
graph TB
    A[Java Geospatial Ecosystem] --> B[imageio-ext]
    A --> C[netCDF-Java]
    A --> D[PMTiles Readers]
    A --> E[Apache Parquet]
    A --> F[Other Libraries]
    
    B --> B1[Custom RangeReader SPI]
    C --> C1[cdms3:// Protocol]
    D --> D1[FileChannel Wrapper]
    E --> E1[SeekableInputStream]
    F --> F1[Various Approaches]
    
    style B1 fill:#ffcccc
    style C1 fill:#ffcccc
    style D1 fill:#ffcccc
    style E1 fill:#ffcccc
    style F1 fill:#ffcccc
```

This fragmentation creates:
- **Code duplication** across projects
- **Inconsistent APIs** for similar operations
- **High barriers** for new format development
- **Vendor lock-in** to specific cloud providers

### Our Solution Context

```mermaid
graph TB
    subgraph "Unified Foundation"
        TRR[Tileverse Range Reader]
    end
    
    subgraph "Format Libraries"
        COG[COG Readers]
        PMT[PMTiles Readers]
        GP[GeoParquet Readers]
        ZARR[Zarr Readers]
        FGB[FlatGeobuf Readers]
    end
    
    subgraph "Applications"
        MAP[Web Mapping]
        GIS[GIS Applications]
        PIPE[Data Pipelines]
        SERV[Tile Servers]
    end
    
    TRR --> COG
    TRR --> PMT
    TRR --> GP
    TRR --> ZARR
    TRR --> FGB
    
    COG --> MAP
    PMT --> MAP
    GP --> GIS
    ZARR --> PIPE
    FGB --> SERV
    
    style TRR fill:#ccffcc
```

## Technical Context

### External Systems and Interfaces

The library interfaces with multiple external systems and data sources:

#### Cloud Storage Providers

| Provider | Interface | Authentication | Capabilities |
|----------|-----------|----------------|--------------|
| **Amazon S3** | AWS SDK v2 | IAM, Credentials Chain | Range requests via GetObject |
| **Google Cloud Storage** | Google Cloud SDK / S3 API | Service Accounts, ADC | Native or S3-compatible API |
| **Azure Blob Storage** | Azure SDK / S3 API | SAS, Connection Strings, AD | Native or S3-compatible API |
| **Generic HTTP/HTTPS** | Java HttpClient | Basic, Bearer, API Key, Digest | Range header support |
| **Local File System** | Java NIO | File system permissions | RandomAccessFile operations |

#### Data Format Context

The library serves as an I/O foundation for cloud-native geospatial formats:

```mermaid
graph LR
    subgraph "Cloud-Native Formats"
        COG[Cloud Optimized GeoTIFF]
        PMT[PMTiles]
        GP[GeoParquet]
        ZARR[Zarr]
        FGB[FlatGeobuf]
    end
    
    subgraph "Traditional Approach"
        DOWN[Download Entire File]
        PROC[Process Locally]
    end
    
    subgraph "Range-Based Approach"
        META[Read Metadata]
        INDEX[Read Index/Directory]
        CHUNK[Read Specific Chunks]
    end
    
    COG --> META
    PMT --> META
    GP --> META
    ZARR --> INDEX
    FGB --> INDEX
    
    META --> CHUNK
    INDEX --> CHUNK
    
    style DOWN fill:#ffcccc
    style PROC fill:#ffcccc
    style META fill:#ccffcc
    style INDEX fill:#ccffcc
    style CHUNK fill:#ccffcc
```

### Integration Interfaces

#### For Library Authors

```java
// Simplified integration for format libraries
public class MyFormatReader {
    private final RangeReader source;
    
    public MyFormatReader(RangeReader source) {
        this.source = source;
    }
    
    public MyData readData(Query query) {
        // Focus on format logic, not I/O plumbing
        ByteBuffer header = source.readRange(0, headerSize);
        ByteBuffer data = source.readRange(dataOffset, dataSize);
        return parseData(header, data);
    }
}
```

#### For Application Developers

```java
// Unified API across all storage backends
RangeReader s3Reader = S3RangeReader.builder()
    .uri(URI.create("s3://bucket/data.cog"))
    .build();
    
RangeReader httpReader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/data.pmtiles"))
    .build();
    
// Same interface, different backends
processData(s3Reader);
processData(httpReader);
```

### External Dependencies and Constraints

#### Required Dependencies

| Component | Purpose | Constraint |
|-----------|---------|------------|
| **Java 17+** | Runtime platform | Minimum language level |
| **AWS SDK v2** | S3 connectivity | S3 module only |
| **Azure SDK** | Azure Blob connectivity | Azure module only |
| **Google Cloud SDK** | GCS connectivity | GCS module only |
| **Caffeine** | Memory caching | Core caching implementation |

#### Optional Dependencies

| Component | Purpose | Usage |
|-----------|---------|-------|
| **TestContainers** | Integration testing | Development/CI only |
| **Docker** | Service emulation | Testing environments |
| **SLF4J** | Logging abstraction | Runtime logging |

### Communication Protocols

#### HTTP Range Requests (RFC 7233)

```http
GET /data.cog HTTP/1.1
Host: example.com
Range: bytes=1000-1999

HTTP/1.1 206 Partial Content
Content-Range: bytes 1000-1999/50000
Content-Length: 1000
Accept-Ranges: bytes

[1000 bytes of data]
```

#### Cloud Provider APIs

- **S3**: `GetObject` with `Range` parameter
- **GCS**: S3-compatible API or native JSON API
- **Azure**: Blob service with range headers

### Stakeholder Communication

| Stakeholder | Communication Channel | Information Need |
|-------------|----------------------|------------------|
| **Java Developers** | Documentation, Examples | Integration patterns, performance tuning |
| **Library Authors** | API Reference, Extensions | Stable interfaces, extension points |
| **DevOps Teams** | Configuration Guides | Deployment, monitoring, troubleshooting |
| **Performance Engineers** | Benchmarks, Profiling | Optimization opportunities, bottlenecks |
| **Security Teams** | Security Documentation | Authentication, authorization, compliance |
