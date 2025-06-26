# Tileverse

A Java 21 library for reading and writing PMTiles, a cloud-optimized format for map tiles.

Website: [https://tileverse.io](https://tileverse.io)

## Overview

Tileverse is a full-featured Java implementation of the PMTiles format with additional features inspired by Tippecanoe. It allows you to read, write, and process PMTiles efficiently with a clean, modern API.

## Features

- Read and write PMTiles version 3 files
- Process geospatial data from GeoJSON and other formats
- Generate vector tiles with configurable simplification and filtering
- Cluster and deduplicate tiles for efficient storage
- High-performance tile access optimized for cloud storage (S3, Azure, HTTP)
- Clean, fluent API for integration into your applications
- Command-line interface for common operations

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8 or higher

### Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>io.tileverse</groupId>
    <artifactId>tileverse-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
// Reading tiles from a local PMTiles file
try (PMTilesReader reader = new PMTilesReader(Path.of("mymap.pmtiles"))) {
    // Get metadata
    PMTilesHeader header = reader.getHeader();
    System.out.println("Map bounds: " + 
        header.minLonE7() / 10000000.0 + "," + 
        header.minLatE7() / 10000000.0 + "," + 
        header.maxLonE7() / 10000000.0 + "," + 
        header.maxLatE7() / 10000000.0);
    
    // Read a specific tile
    Optional<byte[]> tileData = reader.getTile(10, 885, 412);
    
    // Stream all tiles at a zoom level
    reader.streamTiles(12, tile -> {
        System.out.printf("Tile %d/%d/%d: %d bytes%n", 
            tile.z(), tile.x(), tile.y(), tile.data().length);
    });
}

// Reading tiles from cloud storage
RangeReader s3Reader = RangeReaderBuilder.create()
    .s3(URI.create("s3://my-bucket/tiles.pmtiles"))
    .withCaching()
    .withBlockAlignment()
    .build();

try (PMTilesReader reader = new PMTilesReader(s3Reader)) {
    // Use the reader as above to access tiles, metadata, etc.
    Optional<byte[]> tile = reader.getTile(10, 885, 412);
}

// Creating a PMTiles file
PMTiles pmtiles = PMTiles.builder()
    .source(new GeoJSONSource(Path.of("input.geojson")))
    .destination(Path.of("output.pmtiles"))
    .minZoom(0)
    .maxZoom(14)
    .layerName("buildings")
    .simplification(SimplificationMethod.VISVALINGAM, 0.5)
    .build();

pmtiles.generate(progress -> {
    System.out.printf("Processing: %.1f%%\n", progress * 100);
});
```

## Documentation

For more detailed information, see the documentation:

- [Cloud Storage Support](docs/cloud_storage_support.md) - Using PMTiles with S3, Azure, and HTTP

## Project Structure

- **tileverse-core**: Core PMTiles format implementation
- **tileverse-mvt**: Vector tile encoding/decoding
- **tileverse-io**: Input/output formats (including cloud storage support)
- **tileverse-processing**: Tile generation and manipulation
- **tileverse-api**: Primary API for library users
- **tileverse-cli**: Command-line interface

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Protomaps](https://github.com/protomaps/PMTiles) for the PMTiles specification
- [Mapbox](https://github.com/mapbox/tippecanoe) for Tippecanoe, which inspired many features

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.