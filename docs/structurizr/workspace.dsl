workspace "Tileverse Range Reader" "Architecture documentation for Tileverse Range Reader library" {

    model {
        properties {
            "structurizr.groupSeparator" "/"
        }

        # People and external systems
        developer = person "Developer" "A developer using the Tileverse Range Reader library" {
            tags "Person"
        }
        
        application = person "Application" "An application that uses the library to read data" {
            tags "Application"
        }

        # External systems
        localFileSystem = softwareSystem "Local File System" "Local files on disk" {
            tags "External"
        }
        
        httpServer = softwareSystem "HTTP Server" "Remote HTTP/HTTPS servers" {
            tags "External"
        }
        
        awsS3 = softwareSystem "Amazon S3" "AWS S3 and S3-compatible storage" {
            tags "External,Cloud"
        }
        
        azureBlob = softwareSystem "Azure Blob Storage" "Microsoft Azure Blob Storage service" {
            tags "External,Cloud"
        }
        
        googleCloud = softwareSystem "Google Cloud Storage" "Google Cloud Storage service" {
            tags "External,Cloud"
        }

        # Main system
        rangeReaderLibrary = softwareSystem "Tileverse Range Reader" "Java library for reading byte ranges from various data sources" {
            tags "TileverseSystem"
            
            # Core module
            coreModule = container "Core Module" "Core interfaces and base implementations" "Java 17, Maven" {
                tags "Module,Core"
                
                # Core components
                rangeReaderInterface = component "RangeReader Interface" "Main interface for reading byte ranges" "Java Interface" {
                    tags "Interface"
                }
                
                abstractRangeReader = component "AbstractRangeReader" "Base implementation with common functionality" "Java Abstract Class" {
                    tags "Abstract"
                }
                
                fileRangeReader = component "FileRangeReader" "Reads ranges from local files" "Java Class" {
                    tags "Implementation"
                }
                
                httpRangeReader = component "HttpRangeReader" "Reads ranges from HTTP servers" "Java Class" {
                    tags "Implementation"
                }
                
                # Decorators
                cachingRangeReader = component "CachingRangeReader" "In-memory caching decorator" "Java Class" {
                    tags "Decorator"
                }
                
                diskCachingRangeReader = component "DiskCachingRangeReader" "Disk-based caching decorator" "Java Class" {
                    tags "Decorator"
                }
                
                blockAlignedRangeReader = component "BlockAlignedRangeReader" "Block alignment optimization decorator" "Java Class" {
                    tags "Decorator"
                }
                
                # Authentication
                authenticationSystem = component "Authentication System" "HTTP authentication implementations" "Java Package" {
                    tags "Authentication"
                }
            }
            
            # Cloud provider modules
            s3Module = container "S3 Module" "Amazon S3 and S3-compatible storage support" "Java 17, AWS SDK v2" {
                tags "Module,Cloud"
                
                s3RangeReader = component "S3RangeReader" "Reads ranges from S3 storage" "Java Class" {
                    tags "Implementation"
                }
            }
            
            azureModule = container "Azure Module" "Azure Blob Storage support" "Java 17, Azure SDK" {
                tags "Module,Cloud"
                
                azureBlobRangeReader = component "AzureBlobRangeReader" "Reads ranges from Azure Blob Storage" "Java Class" {
                    tags "Implementation"
                }
            }
            
            gcsModule = container "GCS Module" "Google Cloud Storage support" "Java 17, Google Cloud SDK" {
                tags "Module,Cloud"
                
                gcsRangeReader = component "GoogleCloudStorageRangeReader" "Reads ranges from Google Cloud Storage" "Java Class" {
                    tags "Implementation"
                }
            }
            
            # Aggregation module
            allModule = container "All Module" "Aggregates all functionality" "Java 17, Maven" {
                tags "Module,Aggregation"
                
                rangeReaderBuilder = component "RangeReaderBuilder" "Fluent API for creating readers" "Java Class" {
                    tags "Builder"
                }
                
                rangeReaderFactory = component "RangeReaderFactory" "Factory for creating readers from URIs" "Java Class" {
                    tags "Factory"
                }
            }
            
            # Benchmarks
            benchmarksModule = container "Benchmarks Module" "JMH performance benchmarks" "Java 17, JMH, TestContainers" {
                tags "Module,Benchmarks"
            }
        }

        # Relationships - External to system
        developer -> rangeReaderLibrary "Uses library to build applications"
        application -> rangeReaderLibrary "Uses to read data ranges"
        
        # Relationships - System to external
        rangeReaderLibrary -> localFileSystem "Reads from local files"
        rangeReaderLibrary -> httpServer "Makes HTTP range requests"
        rangeReaderLibrary -> awsS3 "Makes S3 range requests"
        rangeReaderLibrary -> azureBlob "Makes Azure Blob range requests"
        rangeReaderLibrary -> googleCloud "Makes GCS range requests"
        
        # Container relationships
        coreModule -> localFileSystem "Reads via FileRangeReader"
        coreModule -> httpServer "Reads via HttpRangeReader"
        s3Module -> awsS3 "Reads via S3RangeReader"
        azureModule -> azureBlob "Reads via AzureBlobRangeReader"
        gcsModule -> googleCloud "Reads via GoogleCloudStorageRangeReader"
        
        allModule -> coreModule "Depends on"
        allModule -> s3Module "Depends on"
        allModule -> azureModule "Depends on"
        allModule -> gcsModule "Depends on"
        
        benchmarksModule -> allModule "Benchmarks"
        
        # Component relationships - Core
        abstractRangeReader -> rangeReaderInterface "Implements"
        fileRangeReader -> abstractRangeReader "Extends"
        httpRangeReader -> abstractRangeReader "Extends"
        
        cachingRangeReader -> rangeReaderInterface "Implements (decorator)"
        diskCachingRangeReader -> rangeReaderInterface "Implements (decorator)"
        blockAlignedRangeReader -> rangeReaderInterface "Implements (decorator)"
        
        httpRangeReader -> authenticationSystem "Uses for authentication"
        
        # Component relationships - Cloud modules
        s3RangeReader -> abstractRangeReader "Extends"
        azureBlobRangeReader -> abstractRangeReader "Extends"
        gcsRangeReader -> abstractRangeReader "Extends"
        
        # Component relationships - All module
        rangeReaderBuilder -> coreModule "Creates readers from"
        rangeReaderBuilder -> s3Module "Creates readers from"
        rangeReaderBuilder -> azureModule "Creates readers from"
        rangeReaderBuilder -> gcsModule "Creates readers from"
        
        rangeReaderFactory -> rangeReaderBuilder "Uses"
    }

    views {
        systemContext rangeReaderLibrary "SystemContext" {
            include *
            autoLayout
            title "System Context - Tileverse Range Reader"
            description "Shows how the Tileverse Range Reader library fits into the overall ecosystem, connecting applications to various data sources."
        }

        container rangeReaderLibrary "Containers" {
            include *
            autoLayout
            title "Container View - Tileverse Range Reader Modules"
            description "Shows the modular structure of the library with core functionality and cloud provider extensions."
        }

        component coreModule "CoreComponents" {
            include *
            autoLayout
            title "Component View - Core Module"
            description "Shows the internal structure of the core module including the decorator pattern implementation."
        }

        component allModule "AllModuleComponents" {
            include *
            autoLayout
            title "Component View - All Module"
            description "Shows the builder and factory components that provide the fluent API."
        }

        styles {
            element "Person" {
                shape Person
                background #08427B
                color #ffffff
            }
            
            element "Application" {
                shape Robot
                background #1168BD
                color #ffffff
            }
            
            element "TileverseSystem" {
                background #2E7D32
                color #ffffff
            }
            
            element "External" {
                background #999999
                color #ffffff
            }
            
            element "External,Cloud" {
                background #FF5722
                color #ffffff
            }
            
            element "Module" {
                shape Component
            }
            
            element "Module,Core" {
                background #1976D2
                color #ffffff
            }
            
            element "Module,Cloud" {
                background #FF5722
                color #ffffff
            }
            
            element "Module,Aggregation" {
                background #9C27B0
                color #ffffff
            }
            
            element "Module,Benchmarks" {
                background #607D8B
                color #ffffff
            }
            
            element "Interface" {
                shape Component
                background #4CAF50
                color #ffffff
            }
            
            element "Abstract" {
                shape Component
                background #8BC34A
                color #ffffff
            }
            
            element "Implementation" {
                shape Component
                background #2196F3
                color #ffffff
            }
            
            element "Decorator" {
                shape Component
                background #FF9800
                color #ffffff
            }
            
            element "Authentication" {
                shape Component
                background #E91E63
                color #ffffff
            }
            
            element "Builder" {
                shape Component
                background #9C27B0
                color #ffffff
            }
            
            element "Factory" {
                shape Component
                background #673AB7
                color #ffffff
            }
        }
        
        theme default
    }
}