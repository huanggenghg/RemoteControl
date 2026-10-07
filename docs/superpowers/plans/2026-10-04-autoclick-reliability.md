# Autoclick Reliability Implementation Plan

**Goal:** Make the existing single scheduled click task buildable, persistent, and truthful about execution results.

**Architecture:** Share logging through accessibilityBase. Store recording state independently of overlay views, persist draft coordinates and offsets, and copy an immutable sequence into each scheduled task. Await gesture callbacks in a cancellable CoroutineWorker; retain the existing approximate daily WorkManager schedule. Expose the saved schedule and last outcome, with disable/resume/delete controls.

**Tech stack:** Kotlin, Android accessibility gestures, SharedPreferences, coroutines, WorkManager, Compose, JUnit 4, AndroidJUnit4.

## 1. Restore compilation

- [x] Use the previously reproduced `:accessibilityCore:compileDebugKotlin` failure as the baseline.
- [x] Move the existing Logger implementation into accessibilityBase, preserving the application's import compatibility; update library/autoclick imports.
- [x] Repair malformed strings in SmallWindowView and the missing Log reference in FloatWindowUtils.
- [x] Run `./gradlew :autoclick:assembleDebug --console=plain` and require success.

## 2. Preserve recordings

- [x] Add failing tests for coordinate/offset serialization, independent snapshots, reset, and recording restoration.
- [x] Introduce ClickRecording and ClickSequenceCodec in accessibilityCore and a SharedPreferences draft store.
- [x] Make the service own recording state; overlay views render it and record the current screen center.
- [x] Keep draft data when switching overlays; return to recording on cancel.
- [x] Run `./gradlew :accessibilityCore:testDebugUnitTest --console=plain`.

## 3. Await actual execution

- [x] Add failing tests for ordered offsets, first/middle gesture failure, empty input, and cancellation.
- [x] Add a coroutine sequence executor and main-thread gesture adapter, propagating rejected/cancelled gestures.
- [x] Persist task snapshots with unique IDs; validate empty days/points and a maximum duration under WorkManager's execution limit.
- [x] Replace the Worker with CoroutineWorker; skip non-target days distinctly, report disabled/stale tasks distinctly, and fail unavailable-service/gesture outcomes.
- [x] Add scheduling tests for before/after target time and malformed configuration.

## 4. Task controls and validation

- [x] Show current recording count, saved time/days/point count, enabled state, and last outcome.
- [x] Implement disable/resume/delete and prevent stale executions from overwriting replacement-task status.
- [x] Add Android tests for preference restoration and task lifecycle.
- [x] Run autoclick/core unit tests, debug build, and Android Lint; test on an existing emulator if available.
- [x] Independently review the final diff and resolve material findings.

## Scope and verification limits

One saved task is supported; new confirmation replaces it. Scheduling remains approximate and says so in the UI. Device unlock, launching target applications, and exact alarms are outside this repair. Do not change the existing ZEGO/network edits. No automatic publishing or commits are required. Real-device gesture acceptance remains distinct from host unit tests.

## Validation (2026-10-04)

- 17 JVM tests passed across accessibilityCore and autoclick.
- 10 Android tests passed on the read-only Android 16 emulator, including real gesture dispatch and WorkManager execution.
- Autoclick debug APK and Android Lint completed successfully.
- The main remote-control app still has pre-existing compilation failures (BuildConfig, network imports/model fields, and error-handler types); its network edits were preserved.
