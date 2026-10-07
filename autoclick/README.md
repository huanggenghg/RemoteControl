# Autoclick

An Android app that records screen coordinates and relative timing, then replays one saved sequence on selected weekdays.

## Use

1. Open the app and enable its accessibility service and overlay permission when prompted.
2. Choose **开始录制** or **继续录制**. Move the floating button to a target position and tap it to record that position. Move/tap again to record additional positions and the elapsed interval. Recording does not click the underlying application.
3. Long-press the floating button, select a time and at least one weekday, and confirm. Cancel returns to the preserved recording.
4. Check **当前状态** for the next planned occurrence or present execution state, and **最近结果** for the independently retained previous result on the main screen. **停用任务**, **启用任务**, and **删除任务** control the saved schedule. Saving again replaces the existing task.
5. Choose **5 秒后试运行** to test the saved sequence, including a disabled schedule. Confirm the real clicks, then switch to the target page during the countdown. Trial does not consume that day's scheduled occurrence; its result appears separately.
6. Use **停止并停用** on the home screen or floating execution control to interrupt the current sequence and disable future scheduled runs. It interrupts a waiting interval immediately; an already dispatched gesture cannot be undone. Explicitly enable/save again to allow scheduled execution.

Draft recordings and saved tasks survive overlay recreation and application restarts. Starting a new recording clears only the draft; an existing scheduled task retains its saved sequence.

Accessibility is a prerequisite: opening or returning to the foreground automatically checks both the system grant and live service connection. If disabled, the app shows **去开启** and **退出应用**. A pending connection waits at most ten seconds, then offers settings guidance to switch the service off and on. Back or **退出应用** closes the Activity and preserves tasks and drafts. The home screen becomes available only after connection; losing permission in the foreground brings the prerequisite prompt back. **无障碍设置** opens Android settings; permission must still be enabled explicitly by the user. You can close the home screen and keep the target application open.

Current task status reads the matching WorkManager entry and execution session. **下次** is a planned time, not an exact-start guarantee. An overdue entry shows elapsed delay and the existing skip deadline; unknown scheduling does not invent a next time or reason. Previous execution results remain after connection recovery or disabling/resuming the same schedule. Contextual preparation/re-recording/time-zone/re-enable explanations only show guidance; they do not clear recordings, change settings or retry automatically. Legacy results retain their text without guessing a recovery cause.

Exact-start reliability and the business justification for the existing fifteen-minute cutoff remain follow-up work requiring design review; this change leaves scheduling and that cutoff unchanged.

## Execution limits

- WorkManager schedules an approximate daily trigger; Android may delay it for power management. A start delayed by more than 15 minutes is skipped, as are early triggers and yesterday's missed occurrences. It is unsuitable for actions that must occur at an exact instant. Schedules retain the time zone from saving; re-save after changing time zone.
- Keep the device awake/unlocked, the accessibility service enabled, and the target page open. Autoclick does not launch target apps or unlock devices.
- If WorkManager starts the app process before an enabled accessibility service reconnects, the scheduled worker waits up to ten seconds before the first click. A disabled service fails immediately. Stop, task replacement, clock changes and start-window expiry abort the wait without consuming the occurrence. Connection timeout does not trigger an automatic retry. The system controls accessibility binding; this is recovery from a short initialization gap, not a guarantee of process survival or exact execution. After **Force Stop**, reopen the app; if the service stays disconnected, explicitly switch it off and on in system accessibility settings. Autoclick does not bypass Android's stopped state.
- Coordinates are absolute screen pixels. Recordings retain display size/rotation and the application for each point. Before each gesture, the worker verifies those conditions and the unlocked/interactive screen, and stops on a mismatch or an unknown foreground app. Re-record after changing orientation, display dimensions, or target layout. App matching cannot detect page/layout changes inside the same app.
- Old recordings/tasks without environment metadata remain visible but require re-recording before execution. Startup disables legacy schedules; no unprotected replay is allowed.
- Before dispatching clicks, the day's occurrence is durably consumed. Process death, cancellation or a failed gesture never automatically replays that occurrence; disable/resume retains this protection. A newly saved recording defines a new schedule. This prevents duplicate actions but can leave an interrupted sequence incomplete.
- A sequence contains at most 200 points and lasts at most eight minutes. Gestures run in order and await Android completion callbacks. Rejected/cancelled gestures stop execution without replaying already completed clicks.
- Trial and scheduled execution share one execution gate and cannot run simultaneously. A scheduled trigger arriving during another run is skipped; disable the schedule while testing if it must not trigger later that day. Trial never automatically restarts after cancellation or application process death.
- The stop control remains over the target page during countdown and execution. Its position avoids the saved coordinates; execution fails closed if it cannot be safely displayed or its actual bounds cover a point.
- “全部点击手势已完成” confirms Android completed the gestures; it does not confirm the target application's business action succeeded.

## Development

```sh
./gradlew :autoclick:assembleDebug
./gradlew :accessibilityCore:testDebugUnitTest :autoclick:testDebugUnitTest
./gradlew :autoclick:connectedDebugAndroidTest
./gradlew :autoclick:lintDebug
```

The Android tests exercise saved preferences, task control, and worker outcomes. Run on a dedicated emulator or test device, since tests create and delete the app's saved task.

For a quick scheduled-click acceptance check, run `GestureIntegrationTest#quickScheduledTaskFiresAtNextMinute` with instrumentation argument `quickSchedule=true`. It saves through the normal task controller, selects the next minute (with a five-second save margin), and waits at most 90 seconds for a real button click. It checks that the click is not early, duplicate delivery does not click again, and the next daily trigger stays aligned. No test timing override is used. The default suite skips this optional wait.

`ClickProcessRestoreTest` is opt-in: run it with instrumentation argument `processRestorePhase=seed`, force-stop the application, then run it in a new instrumentation process with `processRestorePhase=verify`. Keep application data between phases. The default test run skips this two-process regression.

`ClickRecoveryProcessTest` is also opt-in (`processRecoverySeed=true`). It seeds an upcoming normal schedule against a button in the separate test-APK process. Android force-stops the app when instrumentation finishes; establish a normal live connection first, re-enabling the test service if it remains crashed. Then return to the target, kill only the Autoclick PID without force-stopping the package, and verify a new PID/system service connection and one real button click. Do not launch the app, change permissions or run instrumentation after that kill. Snapshot/restore app data and settings around these destructive fixtures.
