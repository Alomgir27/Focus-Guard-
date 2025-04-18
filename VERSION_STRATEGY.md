# Versioning Strategy for FocusGuard

This document outlines the versioning strategy for FocusGuard to ensure consistent and logical version increments for Google Play Store releases.

## Version Format

FocusGuard follows semantic versioning in the format:

```
vX.Y.Z (versionCode: NNNNNN)
```

Where:
- **X** = Major version (significant redesigns or major feature additions)
- **Y** = Minor version (new features, significant improvements)
- **Z** = Patch version (bug fixes, minor improvements)
- **versionCode** = Monotonically increasing integer for Google Play

### Examples:
- v1.0.0 (versionCode: 100000) - Initial release
- v1.0.1 (versionCode: 100001) - Bug fixes
- v1.1.0 (versionCode: 101000) - New feature
- v2.0.0 (versionCode: 200000) - Major update

## Version Code Strategy

The `versionCode` is an integer value used by Google Play Store to determine if one version is more recent than another. Our strategy for generating versionCode is:

```
versionCode = (X * 100000) + (Y * 1000) + Z
```

This allows for:
- Up to 99 major versions
- Up to 99 minor versions per major version
- Up to 999 patch versions per minor version

## How to Update Versions

1. Update the `build.gradle` (app level) file:

```kotlin
android {
    defaultConfig {
        applicationId "com.focusguard.app"
        versionCode 100000  // Update this according to formula
        versionName "1.0.0" // Update this to match semantic version
        // ... other config
    }
}
```

2. Add a git tag with the version number:
```
git tag -a v1.0.0 -m "Version 1.0.0"
git push origin v1.0.0
```

## When to Increment Versions

- **Major Version (X)**: When making incompatible API changes or complete redesigns
  - Example: Redesigning the entire UI, changing core functionality

- **Minor Version (Y)**: When adding functionality in a backward-compatible manner
  - Example: Adding a new feature like additional social media app controls
  
- **Patch Version (Z)**: When making backward-compatible bug fixes
  - Example: Fixing crashes, improving stability, minor UI fixes

## Release Channels

Consider using different release channels in Google Play Console:

1. **Internal Testing**: For team members only (early builds)
2. **Closed Testing**: For beta testers (alpha/beta tracks)
3. **Open Testing**: For public beta testing
4. **Production**: For all users

## Release Notes Template

For each release, create clear release notes following this template:

```
# Version X.Y.Z

## New Features
- Feature 1: Brief description
- Feature 2: Brief description

## Improvements
- Improvement 1: Brief description
- Improvement 2: Brief description

## Bug Fixes
- Fixed: Description of fixed issue
- Fixed: Description of fixed issue
```

## Pre-Release Checklist

Before incrementing version for release:

1. Run full test suite
2. Test on multiple device types and Android versions
3. Check for memory leaks or performance issues
4. Verify all features work with the latest Android version
5. Ensure backward compatibility with older Android versions (based on minSdkVersion)
6. Update any changed API endpoints or dependencies 