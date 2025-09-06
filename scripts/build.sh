#!/bin/bash
# AdchainSDK Android Build Script
set -e

echo "🔨 Building AdchainSDK for Android..."

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build library
echo "Building SDK library..."
./gradlew :adchain-sdk:assembleRelease

# Build sample app
echo "Building sample app..."
./gradlew :sample-app:assembleDebug || true

# Run lint checks
echo "Running lint checks..."
./gradlew lint

echo "✅ Build completed successfully!"