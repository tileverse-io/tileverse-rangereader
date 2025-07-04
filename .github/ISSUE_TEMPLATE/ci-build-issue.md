---
name: CI/CD & Build Issue
about: Report problems with builds, tests, or continuous integration
title: '[BUILD] '
labels: ['build', 'ci/cd', 'infrastructure']
assignees: []
---

## Issue Type
<!-- What type of build/CI issue is this? -->
- [ ] Build failure
- [ ] Test failure
- [ ] CI/CD pipeline failure
- [ ] Dependency issue
- [ ] Release process issue
- [ ] Documentation build issue
- [ ] Performance test issue
- [ ] Security scan failure
- [ ] Code quality check failure

## Build Environment

### CI/CD Platform
- [ ] GitHub Actions
- [ ] Local development environment
- [ ] Other: ___________

### Build Tool
- [ ] Maven
- [ ] Gradle
- [ ] Other: ___________

### Environment Details
- **OS**: [e.g., ubuntu-latest, windows-2022, macos-12]
- **Java Version**: [e.g., 17, 21, 22]
- **Build Tool Version**: 
- **Node Version** (for docs): 

## Failure Details

### Workflow/Job Name
<!-- Which GitHub Action workflow or build job failed? -->

### Failure Location
- **Module**: [e.g., core, s3, azure, benchmarks]
- **Phase**: [e.g., compile, test, package, deploy]
- **Specific Task**: [e.g., specific test class, Maven goal]

### Error Message
```
# Full error message or relevant excerpt
```

### Build Log
```
# Relevant build log output
# Please include context before and after the error
```

### Stack Trace
```
# If applicable, full stack trace
```

## Reproduction Information

### How to Reproduce
<!-- Steps to reproduce the issue -->
1. 
2. 
3. 

### Consistency
- [ ] Fails consistently
- [ ] Intermittent failure
- [ ] First time failure
- [ ] Recent regression

### Affected Branches
<!-- Which branches/PRs are affected? -->
- [ ] main branch
- [ ] develop branch
- [ ] Feature branches
- [ ] Release branches
- [ ] Specific PR: #___

## Build Configuration

### Maven Configuration
<!-- Relevant POM configuration -->
```xml
<!-- Relevant POM sections -->
```

### GitHub Actions Configuration
<!-- Relevant workflow configuration -->
```yaml
# Relevant workflow sections
```

### Environment Variables
<!-- Any relevant environment variables -->
```bash
# Environment variables used
```

## Dependency Information

### Dependencies Involved
<!-- If this is a dependency-related issue -->
- **Library Name**: 
- **Version**: 
- **Type**: [compile, test, runtime]

### Recent Changes
<!-- Any recent dependency updates? -->
- [ ] Updated dependencies
- [ ] Added new dependencies
- [ ] Changed dependency versions
- [ ] Modified Maven configuration

## Test Failure Details
<!-- If this is a test failure -->

### Test Class/Method
<!-- Which specific tests are failing? -->

### Test Type
- [ ] Unit tests
- [ ] Integration tests
- [ ] Performance tests
- [ ] Documentation tests
- [ ] Security tests

### Test Environment
<!-- TestContainers, external services, etc. -->

### Failure Pattern
<!-- When do tests fail? -->
- [ ] Always fails
- [ ] Fails under specific conditions
- [ ] Timeout issues
- [ ] Resource contention
- [ ] Race conditions

## Infrastructure Issues

### Resource Constraints
<!-- If related to CI resources -->
- [ ] Memory issues
- [ ] CPU timeout
- [ ] Disk space
- [ ] Network connectivity
- [ ] External service dependency

### Service Dependencies
<!-- External services the build depends on -->
- [ ] Maven Central
- [ ] Docker Hub
- [ ] TestContainers services
- [ ] Cloud provider APIs
- [ ] Documentation hosting

## Impact Assessment

### Severity
- [ ] Blocks all development (main branch broken)
- [ ] Blocks feature development
- [ ] Blocks releases
- [ ] Intermittent issues
- [ ] Minor infrastructure concern

### Affected Workflows
<!-- Which workflows/processes are impacted? -->
- [ ] Pull request validation
- [ ] Main branch builds
- [ ] Release builds
- [ ] Documentation builds
- [ ] Performance testing
- [ ] Security scanning

## Investigation Done

### Debugging Steps Taken
<!-- What investigation have you done? -->
- [ ] Checked recent changes
- [ ] Reviewed build logs
- [ ] Compared with successful builds
- [ ] Tested locally
- [ ] Checked dependency changes
- [ ] Reviewed infrastructure status

### Local Reproduction
- [ ] Reproduces locally
- [ ] Only fails in CI
- [ ] Different behavior locally

## Proposed Solution
<!-- If you have ideas for fixing this -->

### Short-term Fix
<!-- Immediate workaround -->

### Long-term Solution
<!-- Proper fix for the root cause -->

## Recent Changes
<!-- What changed recently that might be related? -->
- **Code Changes**: 
- **Configuration Changes**: 
- **Dependency Updates**: 
- **Infrastructure Changes**: 

## Related Issues
<!-- Links to similar or related build issues -->

## Additional Context
<!-- Screenshots, monitoring data, related documentation -->

### Build History
<!-- Links to recent build results -->

### External References
<!-- Links to relevant documentation, issues in dependencies, etc. -->

## Checklist
- [ ] I have provided the complete error message
- [ ] I have included relevant build logs
- [ ] I have checked for recent changes that might be related
- [ ] I have attempted to reproduce the issue locally
- [ ] I have searched for similar build issues