---
name: Performance Issue
about: Report performance problems or regressions
title: '[PERFORMANCE] '
labels: ['performance', 'needs-investigation']
assignees: []
---

## Performance Issue Type
<!-- What type of performance issue are you experiencing? -->
- [ ] Slow response times
- [ ] High memory usage
- [ ] High CPU usage
- [ ] Low throughput
- [ ] Performance regression (was faster before)
- [ ] Unexpected resource consumption
- [ ] Cache inefficiency
- [ ] Network inefficiency

## Issue Description
<!-- Describe the performance issue clearly -->

### Expected Performance
<!-- What performance did you expect? -->
- **Response Time**: 
- **Throughput**: 
- **Memory Usage**: 
- **CPU Usage**: 

### Actual Performance
<!-- What performance are you experiencing? -->
- **Response Time**: 
- **Throughput**: 
- **Memory Usage**: 
- **CPU Usage**: 

### Performance Comparison
<!-- How does this compare to expectations? -->
- **Slower by**: [e.g., 10x, 50%, etc.]
- **Using more memory by**: 
- **Baseline Version**: [if this is a regression]

## Benchmark Results
<!-- If you have specific measurements -->

### Test Scenario
- **Operation**: [e.g., reading 1MB ranges, sequential access]
- **Data Size**: 
- **Number of Operations**: 
- **Concurrency Level**: 

### Measurements
```
# Paste benchmark results, profiling output, or measurements here
```

### Profiling Data
<!-- JVM profiling, memory dumps, etc. -->

## Environment Details

### System Information
- **Operating System**: 
- **Java Version**: 
- **Library Version**: 
- **Hardware**: [CPU, RAM, Storage type]

### JVM Configuration
- **Heap Size**: 
- **GC Settings**: 
- **JVM Arguments**: 

### Network Environment
- **Bandwidth**: 
- **Latency to Data Source**: 
- **Network Type**: [local, cloud, VPN, etc.]

## Configuration Details

### RangeReader Configuration
```java
// Your RangeReader setup
```

### Cache Configuration
- **Memory Cache Size**: 
- **Disk Cache Size**: 
- **Block Sizes**: 
- **Cache Hit Ratio** (if known): 

### Data Source Information
- **Source Type**: [File, S3, Azure, GCS, HTTP]
- **File Size**: 
- **Geographic Location**: 
- **Access Pattern**: [sequential, random, mixed]

## Reproduction Information

### Minimal Code Example
```java
// Minimal code that demonstrates the performance issue
```

### Test Data
<!-- Information about test data used -->
- **Data Characteristics**: 
- **Source Location**: 
- **Access Pattern**: 

### Steps to Reproduce
1. 
2. 
3. 

## Analysis Done

### Profiling Performed
- [ ] JVM memory profiling
- [ ] CPU profiling
- [ ] I/O profiling
- [ ] Network monitoring
- [ ] Cache statistics analysis

### Tools Used
- [ ] JProfiler
- [ ] VisualVM
- [ ] Flight Recorder
- [ ] JMH benchmarks
- [ ] Custom timing code
- [ ] Other: ___________

### Findings
<!-- What did your analysis reveal? -->

## Potential Causes
<!-- What do you think might be causing the performance issue? -->
- [ ] Inefficient caching
- [ ] Poor block alignment
- [ ] Network bottleneck
- [ ] Memory allocation overhead
- [ ] CPU-intensive operations
- [ ] I/O inefficiency
- [ ] Configuration suboptimal
- [ ] Bug in implementation

## Impact Assessment

### Severity
- [ ] Critical (blocks production use)
- [ ] High (significantly impacts users)
- [ ] Medium (noticeable impact)
- [ ] Low (minor performance concern)

### Affected Use Cases
<!-- Which scenarios are affected? -->

### User Impact
<!-- How does this affect end users? -->

## Comparison Data

### Other Libraries/Approaches
<!-- How does performance compare to alternatives? -->
- **Direct SDK Performance**: 
- **Other Libraries**: 
- **Raw Implementation**: 

### Version Comparison
<!-- If this is a regression -->
- **Previous Version Performance**: 
- **When Did Regression Start**: 
- **Suspected Changes**: 

## Optimization Attempts

### Configuration Changes Tried
<!-- What optimizations have you attempted? -->
- [ ] Adjusted cache sizes
- [ ] Modified block sizes
- [ ] Changed concurrency settings
- [ ] Tuned JVM parameters
- [ ] Modified network settings

### Results of Attempts
<!-- Did any changes help? -->

## Expected Improvements
<!-- What performance improvements are you looking for? -->
- **Target Response Time**: 
- **Target Throughput**: 
- **Acceptable Resource Usage**: 

## Additional Context
<!-- Logs, stack traces, monitoring data, etc. -->

### Relevant Logs
```
# Performance-related log output
```

### Monitoring Data
<!-- Application monitoring, infrastructure metrics -->

### Screenshots/Graphs
<!-- Performance monitoring dashboards, profiling results -->

## Checklist
- [ ] I have provided specific performance measurements
- [ ] I have included environment and configuration details
- [ ] I have attempted basic optimization
- [ ] I have searched for similar performance issues
- [ ] I have provided reproduction steps