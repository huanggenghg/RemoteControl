# Autoclick Visual Style Implementation Plan

> **For agentic workers:** Use executing-plans to implement the following tasks in this session.

**Goal:** Apply the user-approved deep teal style consistently to existing autoclick surfaces.

**Architecture:** Store shared colors in autoclick XML resources and read them from Compose. Keep screen presentation in a separate composable; retain Activity callbacks. Scope native dialog/drawable overrides to autoclick. Use an autoclick-only icon subclass while preserving the shared touch handling and remote app appearance.

**Tech Stack:** Kotlin, Compose Material 3, Android XML and Canvas.

- [x] Define colors, light native/Compose themes, typography and button/panel shapes.
- [x] Extract and style the home screen with recording/task sections and truthful empty/saved states.
- [x] Style the scrollable schedule panel, weekday selector and local permission dialog; retain binding IDs and handlers.
- [x] Unify the floating control through theme colors without changing gestures or coordinates.
- [x] Build/lint and run existing Android integration tests; inspect screenshots at normal and large fonts and in landscape.
- [x] Record validation and deliver the updated APK and actual screenshots.

No new business-logic tests are needed for these reversible presentation changes. Existing integration tests verify retained recording and task behavior.

## Validation

- Final `:autoclick:assembleDebug`, `:autoclick:lintDebug`, and `:autoclick:connectedDebugAndroidTest` succeeded. Android 16 dedicated emulator: 10 tests, zero failures/errors/skips. Lint: zero errors, 25 warnings.
- Independently reviewed resource overrides, icon subclass/casts, permission scrolling, and optional overlay width; no unresolved material findings.
- Actual UI checks: empty home, floating record control, selected weekdays, saving a task, saved task status/actions, permission prompt, and landscape at font scale 2. Permission actions are reachable by scrolling at font scale 2 in landscape; the portrait schedule panel shows all text and actions at its bounded width.
- Final screenshots are in `.artifacts/autoclick-visual/`. Scheduling/recording business behavior was retained; the custom overlay now supports an optional bounded width.
- The initial device test run on an already configured emulator failed at accessibility-service setup. Starting with services disabled on the dedicated read-only emulator resolved setup and all tests passed.
- APK: `autoclick/build/outputs/apk/debug/autoclick-debug.apk`. Changes remain local on `codex/autoclick-reliability`.
