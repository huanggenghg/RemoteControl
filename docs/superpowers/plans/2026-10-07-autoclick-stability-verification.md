# Autoclick Stability Verification Plan

> **For agentic workers:** Use superpowers:executing-plans to execute these checks inline, preserving the current uncommitted work.

**Goal:** Investigate the intermittent trial assertion and rerun the complete quick acceptance batch with useful failure evidence.

**Architecture:** Keep production behavior unchanged until a cause is identified. Capture test execution state, foreground geometry and an immediate failure screenshot before cleanup removes the evidence. Use the already running Android Studio emulator, backing up and restoring application data and accessibility settings.

**Tech Stack:** Kotlin, AndroidJUnit4, adb, Android accessibility gestures, WorkManager.

## Investigation and verification

- [x] Read the previous failure and single-test rerun; distinguish an unconfirmed original cause from a proven product fix.
- [x] Add stage/outcome/environment diagnostics and failure screenshots to `GestureIntegrationTest.kt`; log actual trial button delivery.
- [x] Build the test APK using Android Studio's JDK: `./gradlew :autoclick:assembleDebugAndroidTest --console=plain`.
- [x] Snapshot app preferences, files, databases and no_backup plus both accessibility settings before touching the existing emulator's app data.
- [x] Run the same five-case batch: `quickScheduledTaskFiresAtNextMinute`, `trialRunsDisabledScheduleWithoutConsumingItsOccurrence`, `emergencyStopDuringCountdownDispatchesNothingAndDisablesSchedule`, `floatingEmergencyStopInterruptsLongIntervalBeforeNextClick`, and `ClickTrialUiTest`, with `quickSchedule=true`.
- [x] Preserve instrumentation output and `AutoclickQuickCheck` logs. Inspect the immediate screenshot if a condition fails; do not weaken guards or change product behavior based on an unconfirmed guess.
- [x] Restore and compare original app files before relaunch, restore accessibility settings, then verify the saved task survives relaunch.
- [x] Record actual pass/fail counts, timing, and whether the original failure was reproduced; run `git diff --check`.

## Following priorities

1. Task editing: time, weekdays and recorded intervals; retain the current single saved task model. Execution records should distinguish trials, scheduled runs and interrupted runs. Define behavior before implementation.
2. Delivery cleanup: address lint findings that affect use, build a new APK and update usage instructions.
3. Real-device power-management acceptance remains skipped at the user's request. Multiple saved tasks are lower priority; no automatic commits or publishing.

## Actual validation

2026-10-07: the five-case batch passed with no failures in 187.331 seconds on emulator-5554. The normal next-minute schedule waited 28.198 seconds; the actual click arrived 1.453 seconds after the target time. Trial received one button click and the subsequent scheduled run received the second. Original data and accessibility settings were verified after restoration. Test APK build and `git diff --check` passed. The original intermittent failure was not reproduced, so its cause remains unconfirmed; production behavior was not changed. Evidence: `.artifacts/autoclick-stability/report.md`.
