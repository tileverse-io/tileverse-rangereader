# Installation

This guide explains how to add the Tileverse Range Reader library to your Java project.

## Requirements

- **Java 17+**: Minimum runtime version
- **Maven 3.9+** or **Gradle 7.0+**: For dependency management

## Maven Installation

### All Modules (Recommended)

Include all functionality with a single dependency:

```xml
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Individual Modules

For smaller dependencies, include only the modules you need:

#### Core Module (Required)

```xml
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### Cloud Provider Modules

=== "Amazon S3"

    ```xml
    <dependency>
        <groupId>io.tileverse.rangereader</groupId>
        <artifactId>tileverse-rangereader-s3</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    ```

=== "Azure Blob Storage"

    ```xml
    <dependency>
        <groupId>io.tileverse.rangereader</groupId>
        <artifactId>tileverse-rangereader-azure</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    ```

=== "Google Cloud Storage"

    ```xml
    <dependency>
        <groupId>io.tileverse.rangereader</groupId>
        <artifactId>tileverse-rangereader-gcs</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    ```

## Gradle Installation

### All Modules

```gradle
implementation 'io.tileverse.rangereader:tileverse-rangereader-all:1.0-SNAPSHOT'
```

### Individual Modules

```gradle
// Core module (required)
implementation 'io.tileverse.rangereader:tileverse-rangereader-core:1.0-SNAPSHOT'

// Cloud provider modules (optional)
implementation 'io.tileverse.rangereader:tileverse-rangereader-s3:1.0-SNAPSHOT'
implementation 'io.tileverse.rangereader:tileverse-rangereader-azure:1.0-SNAPSHOT'
implementation 'io.tileverse.rangereader:tileverse-rangereader-gcs:1.0-SNAPSHOT'
```

## Version Compatibility

| Library Version | Java Version | Maven Version |
|----------------|--------------|---------------|
| 1.0.x | 17+ | 3.9+ |
| 0.9.x | 17+ | 3.6+ |

## Verify Installation

Create a simple test to verify the installation:

```java
import io.tileverse.rangereader.FileRangeReader;
import java.nio.file.Path;
import java.nio.file.Files;

public class InstallationTest {
    public static void main(String[] args) throws Exception {
        // Create a temporary test file
        Path testFile = Files.createTempFile("test", ".bin");
        Files.write(testFile, "Hello, World!".getBytes());
        
        // Test the library
        try (var reader = FileRangeReader.builder()
                .path(testFile)
                .build()) {
            
            var data = reader.readRange(0, 5);
            String result = new String(data.array(), 0, data.remaining());
            System.out.println("Read: " + result); // Should print "Hello"
            
            System.out.println("Installation successful!");
        }
        
        // Clean up
        Files.deleteIfExists(testFile);
    }
}
```

## Migration Guide

### From Version 0.x to 1.x

Breaking changes and migration steps:

- **Replace `RangeReaderBuilder`**: Use type-specific builders instead
  ```java
  // Old (deprecated)
  RangeReader reader = RangeReaderBuilder.s3(uri).build();
  
  // New (recommended)
  RangeReader reader = S3RangeReader.builder()
      .uri(uri)
      .build();
  ```

- **Update import statements**: Package reorganization
  ```java
  // Old imports
  import io.tileverse.rangereader.RangeReaderBuilder;
  
  // New imports  
  import io.tileverse.rangereader.s3.S3RangeReader;
  import io.tileverse.rangereader.cache.CachingRangeReader;
  ```

- **Review caching configuration**: New options available
  ```java
  // Enhanced caching options in 1.x
  var reader = CachingRangeReader.builder(baseReader)
      .maximumSize(1000)
      .maxSizeBytes(64 * 1024 * 1024)  // New: memory-based limits
      .recordStats()                   // New: performance monitoring
      .build();
  ```

### From Other Range Reading Libraries

Common migration patterns:

- **Map offset/length operations** to `readRange()` calls
- **Replace custom caching** with built-in decorators  
- **Adopt builder patterns** for configuration instead of constructors

## Next Steps

- **[Quick Start](quick-start.md)**: Basic usage examples
- **[Configuration](configuration.md)**: Performance optimization
- **[Authentication](authentication.md)**: Cloud provider setup