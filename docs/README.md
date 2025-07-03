# Tileverse Range Reader Documentation

This directory contains the complete documentation for the Tileverse Range Reader library, built with MkDocs Material theme and featuring C4 model architectural diagrams.

## Quick Start

### Build Documentation

```bash
# One-time setup and build
./build.sh
```

### Development Server

```bash
# Start development server with auto-reload
./serve.sh
```

### Clean Build

```bash
# Remove all generated files and rebuild
./clean.sh && ./build.sh
```

## Documentation Structure

```
docs/
├── build.sh              # Main build script (sets up venv, generates diagrams, builds docs)
├── serve.sh              # Development server script
├── clean.sh              # Clean build artifacts script
├── requirements.txt      # Python dependencies
├── mkdocs.yml            # MkDocs configuration
│
├── src/                  # Documentation source files (Markdown)
│   ├── index.md          # Homepage
│   ├── user-guide/       # User documentation
│   ├── developer-guide/  # Developer documentation
│   ├── arc42/            # Technical architecture (arc42 template)
│   ├── api/              # API reference
│   └── assets/           # Static assets (images, CSS, etc.)
│       └── images/
│           └── structurizr/  # Generated SVG diagrams (for MkDocs)
│               └── .gitkeep  # Preserves directory in git
│
├── structurizr/          # C4 model architectural diagrams
│   ├── workspace.dsl     # Main C4 model definition
│   ├── dynamic-views.dsl # Runtime scenarios (work in progress)
│   ├── exports/          # Generated PlantUML files (.puml)
│   │   └── .gitkeep      # Preserves directory in git
│   ├── structurizr-generate-diagrams.sh    # Generate PlantUML from DSL
│   └── plantuml-generate-svg.sh            # Convert PlantUML to SVG
│
└── site/                 # Generated documentation (after build)
```

## Features

### C4 Model Integration

The documentation includes automatically generated C4 architectural diagrams:

- **System Context**: Shows how the library fits in the broader ecosystem
- **Container View**: Displays the modular architecture 
- **Component Views**: Details internal structure of core and all modules
- **Runtime Views**: Dynamic scenarios (work in progress)

## Build Process

The `build.sh` script handles the complete build process:

1. **Environment Setup**: Creates Python virtual environment and installs dependencies
2. **Diagram Generation**: 
   - Runs Structurizr CLI to generate PlantUML from DSL files
   - Converts PlantUML to SVG using Docker
   - Copies SVGs to both `assets/` and `src/assets/` directories
3. **Documentation Build**: Uses MkDocs to build the complete site
4. **Validation**: Ensures all links and references are valid

## Requirements

- **Python 3.8+**: For MkDocs and dependencies
- **Docker**: For C4 diagram generation (Structurizr CLI and PlantUML)
- **Internet connection**: For pulling Docker images during diagram generation

## Contributing

When contributing to documentation:

1. Follow the existing structure and style
2. Update both content and navigation in `mkdocs.yml`
3. Test with `./serve.sh` before submitting
4. Ensure all links work with `./build.sh`
5. Include relevant architectural updates in C4 diagrams if needed