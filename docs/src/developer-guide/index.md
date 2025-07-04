# Developer Guide

Welcome to the Tileverse Range Reader Developer Guide! This section is designed for developers who want to contribute to the project, understand its internals, or extend its functionality.

## What You'll Learn

This guide provides comprehensive information for project contributors and maintainers:

- **Building**: How to build the project from source
- **Architecture**: Deep dive into the design patterns and structure
- **Testing**: Running tests and understanding the test strategy
- **Performance**: Benchmarking and optimization techniques
- **Contributing**: Guidelines for contributing code and documentation

## Target Audience

This guide is designed for:

- **Contributors** who want to add features or fix bugs
- **Maintainers** responsible for project maintenance and releases
- **Architects** studying the design patterns and implementation choices
- **Performance Engineers** analyzing and optimizing the library

## Prerequisites

Before diving into development, ensure you have:

- **Java 21+** for development (Java 17+ for runtime)
- **Maven 3.9+** for building and dependency management
- **Docker** for running integration tests with TestContainers
- **Git** for version control
- Familiarity with **Java NIO**, **concurrency**, and **design patterns**

## Project Overview

### Architecture Principles

The Tileverse Range Reader is built on several key architectural principles:

#### 1. **Decorator Pattern**
The core design pattern enabling composable functionality:

```java
// Stack decorators for optimal performance
RangeReader reader = CachingRangeReader.builder(
    DiskCachingRangeReader.builder(
        BlockAlignedRangeReader.builder()
            .delegate(S3RangeReader.builder()
                .uri(uri)
                .build())
            .blockSize(1024 * 1024) // 1MB blocks
            .build())
        .build())
    .build();
```

#### 2. **Builder Pattern**
Fluent APIs for configuration:

```java
// Type-safe, readable configuration
S3RangeReader reader = S3RangeReader.builder()
    .uri(uri)
    .region(Region.US_WEST_2)
    .credentialsProvider(credentialsProvider)
    .withCaching()
    .withBlockAlignment(64 * 1024)
    .build();
```

#### 3. **Thread Safety**
All implementations are thread-safe to support concurrent access:

```java
// Safe for concurrent use
RangeReader reader = // ... create reader
CompletableFuture<ByteBuffer> future1 = 
    CompletableFuture.supplyAsync(() -> reader.readRange(0, 1024));
CompletableFuture<ByteBuffer> future2 = 
    CompletableFuture.supplyAsync(() -> reader.readRange(1024, 1024));
```

### Module Structure

```
tileverse-rangereader/
├── src/core/           # Core interfaces and implementations
├── src/s3/             # Amazon S3 support
├── src/azure/          # Azure Blob Storage support  
├── src/gcs/            # Google Cloud Storage support
├── src/all/            # Aggregated module with builders/factories
└── benchmarks/         # JMH performance benchmarks
```

### Key Components

#### Core Module (`src/core`)

- **`RangeReader`**: Main interface for reading byte ranges
- **`AbstractRangeReader`**: Template method implementation
- **Decorators**: `CachingRangeReader`, `DiskCachingRangeReader`, `BlockAlignedRangeReader`
- **Base Readers**: `FileRangeReader`, `HttpRangeReader`
- **Authentication**: HTTP authentication implementations

#### Cloud Modules (`src/s3`, `src/azure`, `src/gcs`)

- Provider-specific `RangeReader` implementations
- Authentication handling for each cloud provider
- Builder patterns for type-safe configuration

#### All Module (`src/all`)

- **`RangeReaderBuilder`**: Unified builder (evolving as APIs stabilize)
- **`RangeReaderFactory`**: Factory for creating readers from URIs

#### Benchmarks Module (`benchmarks`)

- JMH-based performance benchmarks
- TestContainers integration for realistic testing
- Memory profiling and performance analysis tools

## Development Workflow

### 1. **Local Development Setup**

```bash
# Clone the repository
git clone https://github.com/tileverse-io/tileverse-rangereader.git
cd tileverse-rangereader

# Build the project
./mvnw clean compile

# Run tests
./mvnw test

# Run integration tests
./mvnw verify
```

### 2. **Code Style and Quality**

The project uses automated code formatting and quality checks:

```bash
# Apply code formatting
make format

# Check code style
make lint

# Run all quality checks
./mvnw validate
```

### 3. **Testing Strategy**

The project employs a comprehensive testing strategy:

- **Unit Tests** (`*Test.java`): Fast, isolated tests
- **Integration Tests** (`*IT.java`): End-to-end tests with real services
- **Performance Tests** (`*PerformanceTest.java`): Throughput and latency measurements
- **Benchmarks**: JMH-based comprehensive performance analysis

### 4. **Performance Analysis**

```bash
# Build benchmarks
./mvnw package -pl benchmarks

# Run all benchmarks
java -jar benchmarks/target/benchmarks.jar

# Run specific benchmarks
java -jar benchmarks/target/benchmarks.jar FileRangeReader

# Run with profiling
java -jar benchmarks/target/benchmarks.jar -prof gc
```

## Design Patterns in Detail

### Template Method Pattern

`AbstractRangeReader` implements the template method pattern:

```java
public final int readRange(long offset, int length, ByteBuffer target) {
    // Validation and preparation (template method)
    validateParameters(offset, length, target);
    
    // Delegate to implementation (hook method)
    return readRangeNoFlip(offset, length, target);
}

// Subclasses implement this hook method
protected abstract int readRangeNoFlip(long offset, int length, ByteBuffer target);
```

### Decorator Pattern Implementation

Decorators wrap other `RangeReader` instances:

```java
public class CachingRangeReader extends AbstractRangeReader {
    private final RangeReader delegate;
    private final Cache<RangeKey, ByteBuffer> cache;
    
    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) {
        // Check cache first, delegate on miss
        ByteBuffer cached = cache.get(new RangeKey(offset, actualLength), 
            key -> loadFromDelegate(key));
        target.put(cached.duplicate());
        return cached.remaining();
    }
}
```

### Builder Pattern Variations

The project uses two builder patterns:

1. **Individual Builders** (preferred):
```java
S3RangeReader.builder()
    .uri(uri)
    .region(region)
    .build()
```

2. **Unified Builder** (evolving):
```java
RangeReaderBuilder.s3(uri)
    .withCredentials(credentials)
    .build()
```

## Extension Points

### Adding New Data Sources

To add a new data source:

1. **Create the Reader Class**:
```java
public class MyRangeReader extends AbstractRangeReader {
    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) {
        // Implementation specific to your data source
    }
}
```

2. **Add Builder**:
```java
public static class Builder {
    public MyRangeReader build() {
        return new MyRangeReader(/* parameters */);
    }
}
```

3. **Add Tests**:
```java
public class MyRangeReaderTest extends AbstractRangeReaderIT {
    // Implement abstract methods from test base class
}
```

### Adding New Decorators

To add new decorator functionality:

1. **Implement the Decorator**:
```java
public class MyDecorator extends AbstractRangeReader {
    private final RangeReader delegate;
    
    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) {
        // Add your decoration logic
        return delegate.readRange(offset, actualLength, target);
    }
}
```

2. **Add Builder Support**:
```java
public static Builder builder(RangeReader delegate) {
    return new Builder(delegate);
}
```

## Common Development Tasks

<div class="grid cards" markdown>

-   :material-hammer-wrench: **Building**

    ---

    Learn how to build, test, and package the project

    [:octicons-arrow-right-24: Build Guide](building.md)

-   :material-sitemap: **Architecture**

    ---

    Deep dive into design patterns and implementation

    [:octicons-arrow-right-24: Architecture](architecture.md)

-   :material-test-tube: **Testing**

    ---

    Understand the testing strategy and write effective tests

    [:octicons-arrow-right-24: Testing](testing.md)

-   :material-speedometer: **Performance**

    ---

    Benchmark and optimize the library

    [:octicons-arrow-right-24: Performance](performance.md)

-   :material-source-pull: **Contributing**

    ---

    Guidelines for contributing code and documentation

    [:octicons-arrow-right-24: Contributing](contributing.md)

</div>

## Development Tools

### Required Tools

- **Java 21+**: Required for development
- **Maven 3.9+**: Build tool and dependency management
- **Docker**: TestContainers integration tests
- **Git**: Version control

### Recommended Tools

- **IntelliJ IDEA**: IDE with excellent Java support
- **Eclipse**: Alternative IDE with Maven integration
- **JProfiler/YourKit**: For performance profiling
- **VisualVM**: Built-in profiling tool

### Quality Tools (Automated)

- **Spotless**: Code formatting (Palantir Java Format)
- **SortPOM**: Maven POM organization
- **JaCoCo**: Code coverage analysis
- **SpotBugs**: Static analysis
- **PMD**: Code quality analysis

## Need Help?

- Review the **[Contributing Guidelines](contributing.md)** for detailed contribution instructions
- Check the **[Architecture Documentation](architecture.md)** for design details
- Browse the **[Testing Guide](testing.md)** for testing best practices
- Visit our **[GitHub repository](https://github.com/tileverse-io/tileverse-rangereader)** for the latest updates