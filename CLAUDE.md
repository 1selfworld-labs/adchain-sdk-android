# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Primary Build Commands
```bash
# Clean and build the SDK
./gradlew clean
./gradlew :adchain-sdk:build

# Assemble release AAR
./gradlew :adchain-sdk:assembleRelease

# Run tests
./gradlew :adchain-sdk:test

# Run lint checks
./gradlew lint

# Build and publish to local Maven (for testing)
./gradlew :adchain-sdk:publishToMavenLocal
```

### Deployment Commands
```bash
# Update version in gradle.properties (SDK_VERSION=1.0.X)
# Then commit and create tag
git tag -a "v1.0.X" -m "Release v1.0.X"
git push origin "v1.0.X"

# JitPack will automatically build after tag push
# Check build status at: https://jitpack.io/#1selfworld-labs/adchain-sdk-android
```

## Architecture Overview

### SDK Structure
The SDK is a single-module Android library that provides three main features:
1. **Offerwall** - WebView-based offerwall display with JavaScript bridge
2. **Mission System** - Task completion tracking with reward URLs
3. **Quiz System** - Interactive quiz participation

### Core Components

**Initialization Flow:**
- `AdchainSdk.initialize()` validates app credentials with server
- Stores `AppData` including the offerwall URL (`adchainHubUrl`)
- User login via `AdchainSdk.login()` authenticates and stores user session

**Key Classes:**
- `AdchainSdk` - Main entry point, handles initialization and user management
- `AdchainOfferwallActivity` - WebView container with JavaScript interface for offerwall
- `AdchainMission` - Mission list fetching and completion tracking
- `AdchainQuiz` - Quiz participation management
- `NetworkManager` - Centralized API communication using Retrofit

### Version Management
- Version is defined in `gradle.properties` as `SDK_VERSION`
- This version is injected into `BuildConfig.VERSION_NAME` during build
- Git tags must include 'v' prefix (e.g., v1.0.11) for JitPack

### API Integration
The SDK communicates with the Adchain backend:
- Base URL: `https://reward.api.adchain.plus/` (production)
- Authentication: App key/secret for initialization, user token for operations
- All API responses follow a standard format with `code`, `msg`, and `data` fields

### WebView Bridge Protocol
The offerwall uses a JavaScript bridge (`AdchainAndroidBridge`) for native-web communication:
- `postMessage()` for web-to-native messages
- Handles navigation, mission completion, quiz participation
- Supports deep linking and external URL opening

## Important Deployment Notes

### JitPack Configuration
- Uses `jitpack.yml` for build configuration with OpenJDK 17
- Publishes to Maven coordinates: `com.github.1selfworld-labs:adchain-sdk-android:vX.X.X`
- Build logs available at: `https://jitpack.io/com/github/1selfworld-labs/adchain-sdk-android/[version]/build.log`

### Critical Files
- `gradle.properties` - Contains `SDK_VERSION` (must be updated for each release)
- `adchain-sdk/build.gradle.kts` - SDK configuration and dependencies
- `jitpack.yml` - JitPack build configuration (requires OpenJDK 17)

### Testing Integration
To test SDK integration locally:
1. Build and publish to local Maven: `./gradlew :adchain-sdk:publishToMavenLocal`
2. In consumer app, add: `mavenLocal()` to repositories
3. Use dependency: `implementation 'com.adchain.sdk:adchain-sdk:1.0.X'`

### Common Issues
- **JitPack build failures**: Check JDK version in jitpack.yml (must be openjdk17)
- **Version mismatches**: Ensure gradle.properties SDK_VERSION matches git tag
- **WebView issues**: Check ProGuard rules for WebView JavaScript interface