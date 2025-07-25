# Installation

This guide explains how to add the Tileverse Range Reader library to your Java project.

## Requirements

- **Java 17+**: Minimum runtime version
- **Maven 3.9+** or **Gradle 7.0+**: For dependency management

## Maven Installation

### Using the BOM (Recommended)

The project provides a Bill of Materials (BOM) to manage dependency versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.tileverse.rangereader</groupId>
            <artifactId>tileverse-rangereader-bom</artifactId>
            <version>1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Now you can omit versions - they're managed by the BOM -->
    <dependency>
        <groupId>io.tileverse.rangereader</groupId>
        <artifactId>tileverse-rangereader-core</artifactId>
    </dependency>
    <!-- Add cloud provider modules as needed -->
    <dependency>
        <groupId>io.tileverse.rangereader</groupId>
        <artifactId>tileverse-rangereader-s3</artifactId>
    </dependency>
</dependencies>
```

### All Modules (Simple Approach)

Include all functionality with a single dependency:

```xml
<dependency>
    <groupId>io.tileverse.rangereader</groupId>
    <artifactId>tileverse-rangereader-all</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Individual Modules (Without BOM)

If you prefer not to use the BOM, specify versions explicitly:

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

### Dependency Management BOMs

The project provides two BOMs for different use cases:

#### Tileverse Range Reader BOM

Manages versions of all Tileverse Range Reader modules:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.tileverse.rangereader</groupId>
            <artifactId>tileverse-rangereader-bom</artifactId>
            <version>1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Dependencies BOM

Manages versions of third-party dependencies (for library developers):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.tileverse.rangereader</groupId>
            <artifactId>tileverse-rangereader-dependencies</artifactId>
            <version>1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

This BOM includes managed versions for:

- AWS SDK components
- Azure Storage SDK components  
- Google Cloud Storage SDK components
- Jackson (JSON processing)
- Caffeine (caching)
- Netty (networking)

## Gradle Installation

### Using the BOM (Recommended)

```gradle
dependencyManagement {
    imports {
        mavenBom 'io.tileverse.rangereader:tileverse-rangereader-bom:1.0-SNAPSHOT'
    }
}

dependencies {
    // Versions managed by the BOM
    implementation 'io.tileverse.rangereader:tileverse-rangereader-core'
    implementation 'io.tileverse.rangereader:tileverse-rangereader-s3'
}
```

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

### From Other Range Reading Libraries

Common migration patterns:

- **Map offset/length operations** to `readRange()` calls
- **Replace custom caching** with built-in decorators  
- **Adopt builder patterns** for configuration instead of constructors

## Next Steps

- **[Quick Start](quick-start.md)**: Basic usage examples
- **[Configuration](configuration.md)**: Performance optimization
- **[Authentication](authentication.md)**: Cloud provider setup