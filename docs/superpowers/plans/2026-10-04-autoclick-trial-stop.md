# Autoclick Trial and Emergency Stop Implementation Plan

**Goal:** Trial saved clicks immediately and stop all current/future automatic gestures from a visible overlay or the home screen.

**Architecture:** Use a pure execution gate plus one Autoclick session controller shared by trials and the periodic Worker. The accessibility service owns an independent execution overlay; the existing gesture executor remains cancellable. Trials use the saved protected snapshot without consuming a scheduled occurrence; emergency stop invalidates the lease synchronously and persists a disabled task.

**Tech Stack:** Kotlin, coroutines, StateFlow, Android accessibility/WindowManager, Compose, WorkManager, JUnit4 and AndroidJUnit4.

## 1. Gate and placement

- [x] Add `ClickExecutionGateTest`: only one lease starts, emergency stop invalidates it, stale finish cannot release another lease, automatic starts remain blocked until explicit enable, manual starts remain possible when halted.
- [x] Add `StopControlPlacementTest`: bounds avoid all recorded points, positions fit the display, invalid/fully obstructed geometry rejects placement.
- [x] Run `:autoclick:testDebugUnitTest`, observe missing-feature failures, then implement `ClickExecutionGate.kt` and `StopControlPlacement.kt` and require passing tests.

## 2. Session, cancellation and Worker

- [x] Add emulator regressions proving manual trial sends actual button clicks without consuming a scheduled occurrence; stop during countdown sends zero clicks; stop during a long interval returns promptly and prevents the next click.
- [x] Add independent `showExecutionControl`/`hideExecutionControl` methods and service-destruction cleanup in `AccessibilityCoreService.kt`.
- [x] Implement `ClickExecutionSession.kt`: lifecycle-bound trial coroutine, gate acquisition, countdown, safe overlay, actual-bound guards, per-point protection, immutable snapshot, progress, cancellation-safe cleanup, global emergency action.
- [x] Integrate the session lease in `ClickPeriodicWorker.kt` before the durable claim and release it in `finally`. Retain existing timing/outcome/at-most-once behavior.
- [x] Add `ClickTaskController.emergencyStop()` to disable preferences and cancel periodic work under the existing mutation lock; explicit save/enable reopens the automatic gate.

## 3. UI and validation

- [x] Update `MainActivity.kt` and `ui/AutoclickScreen.kt` with trial confirmation, countdown/progress/result, and emergency controls that remain usable while other controls are busy.
- [x] Update `autoclick/README.md` with real-gesture trial, separate scheduled ledger, stop semantics and limitations.
- [x] Run JVM tests, debug build, Android Lint and complete Autoclick tests on the dedicated read-only emulator. Independently review changes and resolve material findings; record actual validation below.

Continue inline using the current uncommitted baseline; no worktree replacement, automatic commit, publication, or real-device testing.

## Validation (2026-10-04)

- 36 JVM tests passed: 16 in accessibilityCore, 20 in autoclick.
- The complete 29-case integration suite passed with zero failures/skips on dedicated read-only Pixel_9a_3 / Android 16 (`emulator-5556`). It covers real trial gestures, disabled-task trial, scheduled-ledger preservation, real floating-stop input during an eight-second interval, countdown cancellation, dispatcher handoff cancellation cleanup, failed preference commit cancellation, and retention of trial results after a scheduled run.
- One additional Compose UI case (`ClickTrialUiTest`) passed through the actual home button, confirmation dialog, countdown, protected gesture and result UI. Following a small result-display simplification, this relevant UI case, debug build and lint passed again. The final screenshots are `.artifacts/autoclick-trial/{home,confirmation,countdown,result}.png`.
- Debug APK assembled successfully; Android Lint reports zero errors and 28 warnings. `git diff --check` passed.
- Independent review identified three material issues, all fixed: cancelled return handoff leaking a lease/overlay, failed disable commit skipping WorkManager cancellation, and scheduled status replacing trial history. The first two were reproduced with failing Android regressions before the fixes; final regressions passed.
- Initial emulator boot produced a SystemUI ANR dialog; after restoring the dedicated emulator, foreground checks and the complete suite passed. Foreground checks were not weakened to accommodate the test environment.
- Real-device/ROM power-management acceptance remains deferred. No other emulator, main-app network edits, commits, or publishing were part of this batch.
