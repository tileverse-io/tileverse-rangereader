# Building

Instructions for building the Tileverse Range Reader library from source.

## Prerequisites

### Required Software

- **Java 21+**: For development (Java 17+ for runtime)
- **Maven 3.9+**: Build tool and dependency management
- **Git**: Version control

### Optional Tools

- **Docker**: For running integration tests with TestContainers
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions

## Quick Start

### Clone and Build

```bash
# Clone the repository
git clone https://github.com/tileverse-io/tileverse-rangereader.git
cd tileverse-rangereader

# Build all modules
./mvnw clean compile

# Run tests
./mvnw test

# Package JARs
./mvnw package
```

### Using the Makefile

The project includes a Makefile for common tasks:

```bash
# Clean and compile
make compile

# Run unit tests only
make test-unit

# Run integration tests
make test-it

# Run all tests
make test

# Check code formatting and licenses
make lint

# Apply code formatting
make format

# Install to local repository
make install
```

## Build Targets

### Compilation

```bash
# Compile all modules
./mvnw clean compile

# Compile specific module
./mvnw clean compile -pl src/core

# Compile with dependencies
./mvnw clean compile -pl src/s3 -am
```

### Testing

#### Unit Tests

```bash
# All unit tests (excludes *IT.java)
./mvnw test

# Specific module unit tests
./mvnw test -pl src/core

# Specific test class
./mvnw test -Dtest="CachingRangeReaderTest"

# Specific test method
./mvnw test -Dtest="CachingRangeReaderTest#testBasicCaching"
```

#### Integration Tests

```bash
# All integration tests (*IT.java)
./mvnw test -Dtest="*IT"

# Core module integration tests
./mvnw test -pl src/core -Dtest="*IT"

# S3 integration tests (requires Docker)
./mvnw test -pl src/s3 -Dtest="S3RangeReaderIT"

# MinIO integration tests
./mvnw test -pl src/s3 -Dtest="MinioRangeReaderIT"

# Azure integration tests
./mvnw test -pl src/azure -Dtest="AzureBlobRangeReaderIT"
```

#### Performance Tests

```bash
# Run performance tests
./mvnw test -Dtest="*PerformanceTest"

# Core module performance tests
./mvnw test -pl src/core -Dtest="RangeReaderPerformanceTest"
```

### Packaging

```bash
# Create JAR files
./mvnw package

# Skip tests during packaging
./mvnw package -DskipTests

# Create JAR with dependencies (fat JAR)
./mvnw package -Pshade
```

### Installation

```bash
# Install to local Maven repository
./mvnw install

# Install without tests
./mvnw install -DskipTests

# Install specific module
./mvnw install -pl src/core
```

## Code Quality

### Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with Palantir Java Format:

```bash
# Check formatting
./mvnw spotless:check

# Apply formatting
./mvnw spotless:apply

# Using Makefile
make format
```

### License Headers

All Java files must include the Apache 2.0 license header:

```bash
# Check license headers
./mvnw license:check

# Add missing license headers
./mvnw license:format
```

### POM Organization

The project uses [SortPOM](https://github.com/Ekryd/sortpom) to maintain consistent POM structure:

```bash
# Check POM formatting
./mvnw sortpom:verify

# Sort POM files
./mvnw sortpom:sort
```

### Combined Quality Checks

```bash
# Run all quality checks
./mvnw validate

# Using qa profile (check only, no changes)
./mvnw -Pqa validate

# Using Makefile
make lint
```

## Benchmarks

### Building Benchmarks

```bash
# Build benchmark JAR
./mvnw package -pl benchmarks

# Build with all benchmark dependencies
./mvnw package -pl benchmarks -Pall-benchmarks
```

### Running Benchmarks

```bash
# Run all benchmarks
java -jar benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar benchmarks/target/benchmarks.jar FileRangeReader

# Run with profiling
java -jar benchmarks/target/benchmarks.jar -prof gc

# Run with custom parameters
java -jar benchmarks/target/benchmarks.jar -f 3 -wi 5 -i 10
```

## IDE Configuration

### IntelliJ IDEA

1. **Import Project**:
   - File → Open → Select `pom.xml`
   - Choose "Open as Project"

2. **Configure Code Style**:
   - File → Settings → Editor → Code Style → Java
   - Scheme → Import Scheme → Eclipse XML Profile
   - Import `palantir-java-format.xml` (available in Spotless plugin)

3. **Enable Annotation Processing**:
   - File → Settings → Build → Compiler → Annotation Processors
   - Enable annotation processing

4. **Run Configurations**:
   ```
   # Unit Tests
   Working directory: $MODULE_WORKING_DIR$
   VM options: -ea
   
   # Integration Tests
   VM options: -ea -Dtestcontainers.reuse.enable=true
   ```

### Eclipse

1. **Import Project**:
   - File → Import → Existing Maven Projects
   - Select the root directory

2. **Install Spotless Plugin**:
   - Help → Eclipse Marketplace
   - Search for "Spotless"
   - Install "Spotless (code formatter)"

3. **Configure Formatter**:
   - Window → Preferences → Java → Code Style → Formatter
   - Import Palantir Java Format settings

### VS Code

1. **Install Extensions**:
   - Extension Pack for Java
   - Spotless Gradle plugin (if using Gradle)

2. **Configure Settings** (`.vscode/settings.json`):
   ```json
   {
     "java.configuration.updateBuildConfiguration": "automatic",
     "java.format.settings.url": "palantir-java-format.xml",
     "java.test.config.vmargs": ["-ea"]
   }
   ```

## Module Structure

### Core Module (`src/core`)

```bash
# Build core module
./mvnw clean compile -pl src/core

# Test core module
./mvnw test -pl src/core

# Core module structure
src/core/
├── src/main/java/io/tileverse/rangereader/
│   ├── RangeReader.java
│   ├── AbstractRangeReader.java
│   ├── file/FileRangeReader.java
│   ├── http/HttpRangeReader.java
│   ├── cache/
│   └── block/
└── src/test/java/
```

### Cloud Provider Modules

```bash
# Build S3 module
./mvnw clean compile -pl src/s3 -am

# Build Azure module
./mvnw clean compile -pl src/azure -am

# Build GCS module
./mvnw clean compile -pl src/gcs -am
```

### Aggregation Module (`src/all`)

```bash
# Build all module (includes all dependencies)
./mvnw clean compile -pl src/all -am

# This module provides:
# - RangeReaderBuilder (legacy)
# - RangeReaderFactory
```

### Benchmarks Module

```bash
# Build benchmarks
./mvnw clean compile -pl benchmarks -am

# Requires all other modules
```

## CI-Friendly Versioning

The project uses Maven's CI-friendly versioning:

```bash
# Build with custom version
./mvnw clean package -Drevision=1.2.3

# Build snapshot
./mvnw clean package -Drevision=1.2.3-SNAPSHOT

# The version is controlled by the revision property
```

## Docker Integration

### TestContainers for Integration Tests

Integration tests use TestContainers for realistic testing:

```bash
# Ensure Docker is running
docker --version

# Run integration tests (automatically pulls containers)
./mvnw test -Dtest="*IT"

# Available containers:
# - LocalStack (S3)
# - MinIO (S3-compatible)
# - Azurite (Azure Blob Storage)
# - Nginx (HTTP authentication testing)
```

### Container Requirements

| Test Type | Container | Purpose |
|-----------|-----------|---------|
| S3 Tests | `localstack/localstack:3.2.0` | S3 API emulation |
| MinIO Tests | `minio/minio:latest` | S3-compatible storage |
| Azure Tests | `mcr.microsoft.com/azure-storage/azurite:latest` | Azure Blob emulation |
| HTTP Tests | `nginx:alpine` | HTTP server with auth |

## Troubleshooting Build Issues

### Common Problems

**Maven not found**:
```bash
# Use Maven wrapper
./mvnw --version
```

**Java version issues**:
```bash
# Check Java version
java -version
javac -version

# Set JAVA_HOME
export JAVA_HOME=/path/to/java21
```

**Docker issues**:
```bash
# Check Docker is running
docker ps

# Pull required images manually
docker pull localstack/localstack:3.2.0
docker pull minio/minio:latest
```

**Permission issues on scripts**:
```bash
# Make scripts executable
chmod +x mvnw
chmod +x docs/structurizr/*.sh
```

**Out of memory during build**:
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"

# Or set in .mvn/jvm.config
echo "-Xmx2g" > .mvn/jvm.config
```

### Clean Build

If you encounter issues, try a clean build:

```bash
# Clean everything
./mvnw clean

# Remove local repository cache (if needed)
rm -rf ~/.m2/repository/io/tileverse/rangereader

# Fresh build
./mvnw clean compile test package
```

## Release Process

### Snapshot Builds

```bash
# Deploy snapshot to repository
./mvnw clean deploy -Drevision=1.0-SNAPSHOT
```

### Release Builds

```bash
# Release version
./mvnw clean deploy -Drevision=1.0.0

# Tag release
git tag v1.0.0
git push origin v1.0.0
```

## Performance Considerations

### Build Performance

```bash
# Parallel builds
./mvnw -T 4 clean compile

# Skip non-essential plugins during development
./mvnw compile -Dspotless.skip -Dsortpom.skip

# Use offline mode (when dependencies are cached)
./mvnw -o compile
```

### Test Performance

```bash
# Run tests in parallel
./mvnw test -Dparallel=classes -DthreadCount=4

# Reuse TestContainers
export TESTCONTAINERS_REUSE_ENABLE=true
./mvnw test -Dtest="*IT"
```

## Next Steps

- **[Architecture](architecture.md)**: Understand the codebase structure
- **[Testing](testing.md)**: Learn about the testing strategy
- **[Contributing](contributing.md)**: Guidelines for contributing code