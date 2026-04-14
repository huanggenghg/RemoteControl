# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RemoteControl is an Android application that enables remote control of one Android device from another. It uses the Android Accessibility Service for gesture injection and ZEGO Express SDK for real-time video/audio streaming between devices.

## Build Commands

```bash
# Build all modules
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Build specific module
./gradlew :app:assembleDebug
./gradlew :accessibilityCore:assembleDebug
./gradlew :autoclick:assembleDebug
```

## Project Structure

```
RemoteControl/
├── app/                    # Main remote control application
├── accessibilityCore/      # Core accessibility service library (published to Maven)
├── accessibilityBase/      # Base interface for accessibility events
└── autoclick/              # Separate app for scheduled auto-click functionality
```

## Module Dependencies

```
autoclick → accessibilityCore → accessibilityBase
app → accessibilityCore → accessibilityBase
```

## Architecture

### Accessibility Service Layer

- **`AccessibilityBaseEvent`** (accessibilityBase): Interface defining gesture operations (click, scroll, input, navigation)
- **`AccessibilityCoreService`** (accessibilityCore): AccessibilityService implementation that performs gesture dispatching via `GestureDescription` API
- **`AccessibilityHandler`**: Handler for processing gesture messages from remote commands

### Real-time Communication Layer

- **`ZegoBaseActivity`**: Base activity handling ZEGO room login, token management, and event callbacks
- **`RemoteControlActivity`**: Main UI for the controller device - sends commands via ZEGO custom commands
- Commands: click, scrollUp/Down/Left/Right, softInput, back, home, recents

### Logging & Error Handling

The project has a unified logging system in `app/src/main/java/com/lumostech/remotecontrol/utils/`:
- **`Logger`**: Unified logging with auto-tagging and debug/release mode control
- **`GlobalExceptionHandler`**: Catches uncaught exceptions, saves crash reports to `/cache/crashes/`
- **`CoroutineExtensions`**: `AppCoroutineExceptionHandler`, `retryIO`, `runSafely` for safe coroutine execution
- **`NetworkErrorHandler`**: User-friendly network error messages
- **`UiErrorHandler`**: Toast/Snackbar error display

Use `Logger` instead of Android `Log` class throughout the codebase.

## Key Patterns

### Gesture Dispatching Flow
1. Remote device sends JSON command via ZEGO `sendCustomCommand`
2. `ZegoBaseActivity.onIMRecvCustomCommand` parses command
3. Calls `AccessibilityCoreService.dispatchGestureClick/Scroll/etc.`
4. Service uses `GestureDescription` API to inject gestures

### Coordinate Mapping
`SoftInputUtils` handles coordinate transformation between controller and controlled device screens.

### Periodic Tasks (autoclick module)
Uses `WorkManager` with `ClickPeriodicWorker` for scheduled click execution based on day-of-week.

## Configuration

- **minSdk**: 24
- **targetSdk/compileSdk**: 36
- **Java**: 11
- **Kotlin**: 2.0.21
- **AGP**: 8.11.0

## Permissions Required

The app requires sensitive permissions:
- `SYSTEM_ALERT_WINDOW` - Floating windows
- `BIND_ACCESSIBILITY_SERVICE` - Accessibility service
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - Screen capture
- `CAMERA`, `RECORD_AUDIO` - ZEGO video/audio

## ZEGO Integration

- AppID: 678281271
- Token authentication via backend API (`GetZegoTokenService`)
- Token renewal handled in `onRoomTokenWillExpire` callback
