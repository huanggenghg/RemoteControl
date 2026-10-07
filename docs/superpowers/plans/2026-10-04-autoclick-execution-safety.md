# Autoclick Execution Safety Implementation Plan

**Goal:** Prevent unsafe and duplicate scheduled gestures without changing the main remote-control app.

**Architecture:** Persist a per-recording protection snapshot in accessibilityCore; Autoclick explicitly opts in. Evaluate daily occurrences in a pure scheduling policy and atomically consume them in ClickTaskStore before gesture dispatch. Keep existing daily WorkManager scheduling and task controls.

**Tech Stack:** Kotlin, Android accessibility/WindowManager, SharedPreferences, WorkManager, JUnit 4, AndroidJUnit4.

- [x] Add a regression proving a legacy task currently executes without protection; run it and observe failure.
- [x] Add and test pure protection metadata/serialization, coordinate bounds, package/geometry checks; persist and record it through the shared service with opt-in behavior.
- [x] Add and test the saved-time-zone and 15-minute daily occurrence policy (early, late, weekdays, midnight, clock/time-zone changes).
- [x] Persist stable schedule identity and atomic occurrence claims; verify process restoration, duplicate/stale workers and disable/resume using Android tests.
- [x] Integrate Worker checks before every click; fail closed on missing protection or persistence errors and retain distinct stop reasons.
- [x] Update existing home/help copy and migrate old tasks to a visible re-recording state.
- [x] Run unit tests, debug build, lint and dedicated emulator integration tests; review final changes and record validation.

No real-device testing, automatic commits or publication. Preserve the prior local work and unrelated app/network edits.

## Validation (2026-10-04)

- 31 JVM tests passed: 16 in accessibilityCore and 15 in autoclick.
- The final complete Android run passed all 24 tests on dedicated read-only Pixel_9a_3 / Android 16 (`emulator-5556`), with zero skipped or failed tests. Coverage includes actual button gestures, duplicate workers, app switching, screen off, display mismatch, tapping the recording overlay above Settings, and the persisted next periodic trigger after completion.
- Process restoration was separately verified with two instrumentation processes: `ClickProcessRestoreTest` with `processRestorePhase=seed`, explicit force-stop, then `processRestorePhase=verify`. Both returned `OK (1 test)`; the restored occurrence could not be claimed again and retained its outcome.
- Autoclick debug build and Android Lint passed; lint reports zero errors and 28 warnings. `git diff --check` passed.
- Reproduced and fixed regressions include legacy unprotected execution, lost protection metadata on restore, DST schedule drift, and a failed preference commit incorrectly leaving a running status. The failure-injection regression now passes.
- Independent review findings were addressed: align the exact existing periodic worker after execution, detect wall-clock changes, recheck the first-dispatch cutoff, preserve occurrence-owned outcomes, reject other displays/window bounds, and handle failed claims inside the store lock.
- Only the dedicated test emulator was modified. Real-device/ROM power-management acceptance remains deferred; app-level business outcomes and page changes inside the same application are not inferred from gesture completion.
