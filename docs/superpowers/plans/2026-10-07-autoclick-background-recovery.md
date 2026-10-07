# Autoclick Background Recovery Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to execute inline. No automatic commit or publishing.

**Goal:** Allow a briefly disconnected, enabled accessibility service to recover before scheduled dispatch and show its current readiness.

**Architecture:** A pure cancellable `ServiceConnectionWaiter` handles bounded probing. `ClickServiceConnection` reads Android settings and the live service on Main. The worker validates task/time ownership while waiting and retains existing preflight/claim ordering. MainActivity refreshes status only while visible; Compose displays the three statuses and a settings entry.

**Tech Stack:** Kotlin, coroutines, Android settings/accessibility, WorkManager, Compose, JUnit4, AndroidJUnit4.

- [x] Add a failing real-worker regression in `GestureIntegrationTest.kt`: temporarily clear the service reference for 1.5 seconds, assert no early claim/click and no premature failure, reconnect, require a real button click. Always restore the reference in `finally`.
- [x] Add `ServiceConnectionWaiterTest.kt` covering late connection, timeout, immediate abort and caller cancellation. Run `:autoclick:testDebugUnitTest`; observe missing API failure before implementing `ServiceConnectionWaiter.kt`.
- [x] Implement `ClickServiceConnection.kt` with exact component/settings detection, disabled/connecting/connected status, and bounded Main-safe polling.
- [x] Update `ClickPeriodicWorker.kt` to log waiting status without consuming the occurrence, abort on task/time changes, and distinguish disabled service from reconnect timeout.
- [x] Add Android regressions for stop during reconnection and disabled-service status; add live Compose readiness assertions.
- [x] Update MainActivity/AutoclickScreen with live readiness status and an explicit settings action. Update README with recovery and force-stop limits.
- [x] Build debug/test APKs, run affected JVM tests and lint, run real-gesture/reconnect/UI tests on the existing emulator with data snapshot/restore, then validate actual process kill/rebind and the ordinary next-minute trigger.
- [x] Review changed code, resolve material findings and record evidence plus remaining device limitations.

## Validation (2026-10-07)

- Observed missing-API JVM RED failures and old-worker real reconnect RED failure before implementing the recovery path.
- Debug/test APK builds, 45 affected JVM tests and Autoclick lint passed (zero lint errors, 28 warnings).
- Existing emulator `emulator-5554`: all 20 gesture/UI regressions passed in 222.012 seconds; next-minute trigger waited 20.287 seconds, clicked once 320 ms late, and duplicate delivery added no click.
- Actual PID kill after instrumentation ended: system restarted 7523 as 7589 in 2.395 seconds, reconnected the service and delivered the saved normal schedule to a separate-process button once. No manual app launch, setting changes or instrumentation after kill. Check including duplicate observation took 79.317 seconds.
- Initial baseline setup was blocked by Android's force-stop at instrumentation completion. Reconnected the service only before the separate PID-kill experiment; this setup is not counted as automatic recovery.
- Review's clock-anchor finding fixed and independently confirmed. Original app files, saved task and accessibility settings restored and verified after each fixture run. No real-device/ROM background-management acceptance, per user instruction.
- Evidence: `.artifacts/autoclick-background-recovery/report.md` and its linked logs, snapshots and screenshots. No commit or publishing performed.
