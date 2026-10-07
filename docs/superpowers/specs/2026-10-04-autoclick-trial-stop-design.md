# Autoclick trial and emergency stop

The user accepted the next priority, immediate trial and emergency stop. Continue on the existing local branch, preserving the preceding uncommitted work and unrelated app changes. No automatic commits, publishing, or real-device acceptance.

## Behavior

The saved task gains a **5 秒后试运行** action, including when the schedule is disabled. Confirmation explains that this sends real gestures and gives five seconds to return to the target page. Use an immutable saved task snapshot and the existing per-point application/display/unlocked checks. Trial ignores weekday/time restrictions and does not claim or change the scheduled occurrence. It never restarts automatically after process death.

A separate execution overlay shows countdown/progress and **停止并停用**. This remains available over the target application throughout trial and scheduled execution. The home screen has the same emergency action. Stopping cancels the current coroutine immediately, prevents subsequent gestures, and persistently disables/cancels the saved periodic task. Already dispatched gestures cannot be undone. Explicitly saving or re-enabling a schedule allows scheduled execution again; explicit manual trial remains possible for a disabled task.

## Coordination and overlay safety

One process-wide execution gate serializes trial and scheduled sequences. Acquire it before claiming the scheduled occurrence. Stop invalidates the active lease and blocks new scheduled starts until an explicit user save/enable. Keep the existing accessibility sequence mutex as the final shared-library guard.

The execution overlay is independent of recording/confirmation overlays. Place its fixed bounds away from every saved click, and verify its actual attached bounds before dispatch; fail closed if there is no safe placement, it disappears, or it covers a point. Do not allow replayed gestures to activate their own stop button. Release the overlay and gate in cancellation-safe cleanup. The shared service owns overlay attachment/removal; Autoclick owns control text and callbacks, leaving the remote-control application unchanged.

## Status and verification

Show active countdown/progress and the last trial result separately from the saved schedule's durable outcome. A task replacement, service loss, clock change during a run, or protection mismatch stops the trial. Stop during countdown must send no gestures; stop during an interval must interrupt the wait instead of waiting for the next point.

Test gate exclusivity, stale lease cleanup, halted scheduling, safe placement, and actual Android trials/stops. Verify that manual trial leaves the scheduled consumption ledger unchanged, disabled-task trials work, the overlay does not receive replayed clicks, and emergency stop leaves the periodic task disabled. Run relevant JVM tests, dedicated emulator integration tests, debug build and lint; preserve deferred real-device acceptance.
