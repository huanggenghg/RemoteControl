# Autoclick execution safety

User requested continuing after skipping real-device acceptance on 2026-10-04.

## Scope

Implement the next two priorities: recording/execution environment protection and a bounded, at-most-once daily schedule. Continue in the existing `codex/autoclick-reliability` checkout because the preceding local implementation is uncommitted and is the baseline. Preserve unrelated main-app edits. Do not commit or publish automatically.

## Protection

Autoclick opts into protected recording. Save physical display width/height, display rotation and one foreground package per recorded point alongside the draft; copy them into the saved task. Reject additional points if geometry changes. Missing/inaccessible foreground information fails closed; never infer it from stale accessibility events. Retain existing unprotected shared-library entry points for the other application.

Before every scheduled gesture, check task identity/enabled state, live accessibility service, interactive/unlocked device, display geometry/rotation, and the package expected for that point. Stop at the first mismatch and preserve a specific reason. Package checks do not establish that a page inside that application is unchanged. Existing tasks/drafts without protection remain readable, but cannot execute or be enabled until re-recorded.

## Timing and repetition

Keep approximate daily WorkManager scheduling. Save the schedule's time zone. Each day's occurrence is the selected local date/time, with a 15-minute inclusive late-start window. Never start early, outside selected weekdays, in a changed time zone, or outside today's window; never catch up yesterday's occurrence. The same checks also stop subsequent gestures after a clock/date/time-zone change invalidates the occurrence (normal passage of time during a started sequence does not reapply the start window).

Before the first gesture, atomically commit the consumed occurrence with the task's stable schedule identity. Keep a monotonic latest consumed timestamp per schedule; duplicate starts and clock rollback cannot replay it. Disable/resume changes the worker identity but preserves the schedule identity and consumed timestamp. A newly saved recording defines a new schedule. Failed persistence must prevent all gestures. Cancellation/process death after consumption never retries that occurrence; this intentionally prefers a missed action over duplicate external side effects. Duplicate workers do not overwrite the original execution's status.

## User-visible behavior and validation

Explain the 15-minute cutoff, automatic protection and legacy re-recording requirement in existing screens. Keep recent outcome timestamps. Add pure JVM tests for protection and timing boundaries, and Android tests for durable claims, stale workers, resume, and interrupted execution. Re-run build, unit tests, lint and the dedicated Android 16 emulator tests. Real-device/ROM battery acceptance remains skipped as requested.
