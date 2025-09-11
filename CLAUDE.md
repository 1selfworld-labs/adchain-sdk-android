# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Clean build artifacts
./gradlew clean

# Build release AAR
./gradlew :adchain-sdk:assembleRelease

# Full build with tests
./gradlew build

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test

# Run Android instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Publish to local Maven repository for testing
./gradlew :adchain-sdk:publishToMavenLocal

# Publish to remote repository
./gradlew :adchain-sdk:publish
```

## Architecture Overview

### SDK Structure
The AdChain SDK follows a modular architecture with clear separation of concerns:

- **Core Module** (`com.adchain.sdk.core`): Contains the main `AdchainSdk` singleton class that manages initialization, user sessions, and SDK lifecycle
- **Network Layer** (`com.adchain.sdk.network`): Handles all API communications using Retrofit/OkHttp with Moshi for JSON parsing
- **Feature Modules**: 
  - `offerwall`: Manages offerwall advertisements display and interactions
  - `quiz`: Handles quiz-based advertisement features
  - `mission`: Manages mission-based advertisement tasks
- **Utils** (`com.adchain.sdk.utils`): Shared utilities including logging, constants, and helpers

### Key Design Patterns
- **Singleton**: Main SDK class uses singleton pattern for global instance management
- **Builder**: Configuration objects use builder pattern for flexible setup
- **Observer**: Event callbacks and listeners for asynchronous operations
- **Factory**: Component creation for network and ad features

### Threading Model
- Uses Kotlin Coroutines for asynchronous operations
- Main thread for UI operations, background threads for network calls
- Callback interfaces for async result delivery

## Project Configuration

### Version Management
- SDK version defined in `gradle.properties` as `SDK_VERSION`
- Current version: 1.0.4
- Version should be updated before each release

### SDK Requirements
- minSdk: 24 (Android 7.0)
- targetSdk: 34
- compileSdk: 35
- Java/Kotlin target: 17

### Critical Dependencies
- Retrofit/OkHttp for networking
- Moshi for JSON serialization
- Kotlin Coroutines for async operations
- Google Play Services for advertising ID
- Glide for image loading

## Publishing & Distribution

### JitPack (Recommended)
- Configured via `jitpack.yml`
- Builds triggered by Git tags/releases
- Group ID: `com.github.fly33499`
- Artifact ID: `ad-chain-sdk`

### GitHub Packages
- Requires authentication token
- Publishing via `./gradlew :adchain-sdk:publish`
- Repository URL configured in `build.gradle.kts`

### Version Bumping Process
1. Update `SDK_VERSION` in `gradle.properties`
2. Commit changes with message: `chore: Bump version to X.Y.Z`
3. Create Git tag: `git tag vX.Y.Z`
4. Push tag: `git push origin vX.Y.Z`

## Code Modification Guidelines

### ProGuard Rules
- Located in `adchain-sdk/proguard-rules.pro`
- Must preserve SDK public APIs
- Keep networking library models
- Test thoroughly after ProGuard changes

### Required Permissions
The SDK requires these permissions in AndroidManifest.xml:
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `com.google.android.gms.permission.AD_ID`

### API Integration Points
- Base URL and endpoints defined in `NetworkManager`
- API responses handled with Moshi converters
- Error handling through sealed Result classes

## Testing Strategy

### Unit Tests
- Test directory: `adchain-sdk/src/test/`
- Run with: `./gradlew test`
- Focus on business logic and data transformations

### Instrumented Tests
- Test directory: `adchain-sdk/src/androidTest/`
- Run with: `./gradlew connectedAndroidTest`
- Test UI components and Android-specific features

### Integration Testing
- Test SDK initialization and API calls
- Verify callback mechanisms
- Test error scenarios and edge cases

## Documentation Updates

When making changes, update relevant documentation:
- `/SDK_INTEGRATION_GUIDE.md` - For API changes or new features
- `/JITPACK_GUIDE.md` - For distribution changes
- Version-specific changes in commit messages
- API documentation in code comments

## Common Development Tasks

### Adding New Ad Format
1. Create new package under `com.adchain.sdk.<format>`
2. Implement format-specific manager class
3. Add initialization in main SDK class
4. Update ProGuard rules if needed
5. Add integration examples to documentation

### Updating Network Endpoints
1. Modify `ApiService` interface in network package
2. Update data models if response format changes
3. Test with network interceptor for debugging
4. Verify error handling for new endpoints

### Debugging Network Issues
- Enable OkHttp logging interceptor in debug builds
- Check network permissions in manifest
- Verify base URL and endpoint configurations
- Use Charles/Proxyman for traffic inspection

## Build Troubleshooting

### Common Issues
- **Gradle sync fails**: Check Java version (must be 17+)
- **AAR not building**: Run `./gradlew clean` first
- **JitPack build fails**: Check `jitpack.yml` configuration
- **Duplicate class errors**: Check dependency conflicts

### Environment Setup
- Android Studio Hedgehog or newer recommended
- Gradle 8.2+ required
- Enable Gradle daemon for faster builds
- Configure `gradle.properties` for optimal performance