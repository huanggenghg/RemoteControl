# AutoClick

Records screen coordinates and relative timing, then replays one saved sequence on selected weekdays.

## Use

1. Open AutoClick and enable accessibility when prompted. The app checks both the grant and live connection whenever it is in the foreground. Choose **去开启** to open settings or **退出应用** to close the Activity without deleting data. A connecting service waits up to ten seconds in this foreground guide.
2. Choose **开始录制** / **继续录制**, move the floating button and tap to record positions and intervals. Recording does not click the underlying app. Long-press to choose a time and weekdays; saving replaces the existing schedule. Drafts and saved tasks survive process restarts.
3. Android 12+ may require **闹钟和提醒** permission. Without it, saving preserves the configuration as disabled. **去开启 / 暂不** only affects timed execution: recording and trial remain available. Returning from settings never enables a task automatically; explicitly choose **启用任务**.
4. Check **当前状态** for the actual alarm registration or current execution, and **最近结果** for independent history. Disable, resume and delete use the existing buttons. Resume rotates the event identity while preserving this schedule's consumed/skipped occurrences and history.
5. **修改时间** edits the saved hour/minute and weekdays, retaining points, intervals, protection, saved time zone, schedule identity, draft and execution history. Cancel leaves the task unchanged; rotation keeps the edit and never duplicates a saving operation. A disabled task stays disabled and needs no exact-alarm permission to edit. If an enabled task loses permission during saving, the new configuration is retained disabled with an explicit result. A changed time zone remains blocked until the existing re-save flow updates it. Editing is unavailable during alarm preparation, trial or execution. Unchanged values do not replace identity or reschedule.
6. **5 秒后试运行** confirms real clicks, then gives five seconds to return to the target page. Trial works with a disabled schedule and does not consume a timed occurrence. It shares the execution gate with timed runs.
7. **停止并停用** immediately cancels the active sequence and disables future timing. Already dispatched gestures cannot be undone. Enable/save explicitly to allow future timed execution again.

Existing WorkManager schedules are cancelled during upgrade. Their recording, configuration, history and consumption ledger remain, but the task shows **定时方式已更新，请启用任务**. It requires explicit enabling; old Worker instances cannot dispatch gestures.

### Legacy task recovery

Tasks without saved application/display protection show **旧版任务需要重新录制，才能启用。** The card explains the missing application, display size and rotation metadata. **修改时间** still edits the time and weekdays, retaining the old clicks, saved time zone, schedule identity, history and consumption ledger; the task stays disabled.

Choose **重新录制** and review the explanation before **开始重新录制**. Only the recording draft is cleared; the saved task and its history remain until the new task is confirmed saved. Cancel or denied permissions preserve both. Tap the floating button on the target page and long-press to choose time/weekdays, then **保存并替换旧任务**. **暂停恢复** retains the new draft, and **继续恢复** lets you continue or explicitly clear it and restart. Rotation retains the time draft and one saving operation.

The replacement is a new schedule and **remains disabled**, with no automatic alarm. First use **5 秒后试运行**, then explicitly **启用任务**. The old schedule's history and consumed dates are not attributed to the new recording. An interrupted save resumes from the confirmed task snapshot; an uncompleted save pauses without retrying automatically. If storage cannot be confirmed, the UI retains its last known task, explains the state and blocks changes. A source-task or recording-session mismatch cannot overwrite another task.

## Timing and safety

- A cancellable, explicit, immutable AlarmManager event schedules one future occurrence with `setExactAndAllowWhileIdle`. WorkManager is retained only to cancel old schedules and host the inert compatibility Worker. There is no fallback clicking path when exact timing permission is unavailable.
- The first gesture may be dispatched only in **T ≤ firstDispatchAt < T + 5,000 ms**. Delivery, process startup, service connection, stop-control preparation, checks, consumption commit and the first point's delay all consume the original budget. A final guard runs immediately before `dispatchGesture`. At five seconds, the occurrence is skipped; future plans continue. This is a business cutoff, not an Android delivery guarantee.
- Once the first gesture starts in time, remaining points retain recorded intervals and may finish after that window. Execution belongs to the connected accessibility service lifecycle, and service destruction cancels it. The broadcast receiver only verifies, connects and hands off; there is no foreground standby service or automatic Activity launch.
- Keep the device awake/unlocked and the correct target page open. Each point checks task/service identity, display size/rotation, target package, stop-control bounds, screen state and clock changes. AutoClick does not open target apps or unlock devices. Package matching cannot detect page changes inside the same app.
- Before any gesture, claiming an automatic occurrence durably consumes its entire date in the saved time zone. Editing to a later time that day, process death, cancellation, clock rollback and disable/resume cannot run it again that date. A skipped event that was never claimed retains an event watermark without consuming the entire date; explicitly editing to a future time that day is allowed. Editing always selects a strictly future selected time and never catches up a passed occurrence. Saving a new recording creates a new schedule. Interrupted sequences may remain incomplete.
- Only one execution runs at a time. A timed occurrence arriving during trial is skipped without queuing or interrupting trial. An unavailable stop control prevents clicking. A sequence has at most 200 points and eight minutes of recorded time; a nine-minute watchdog bounds execution.
- Boot/application update can rebuild a strictly future alarm only for an already enabled new-version task with valid permissions. Time changes stop timed execution and recalculate future timing. Time-zone changes pause the task and require re-saving. Permission grants never arm automatically.
- After Android **Force Stop**, reopen AutoClick and check the task; an unconfirmed arrangement requires explicit enabling. Ordinary startup validates stored state and does not blindly replace a potentially cancelled alarm. If accessibility stays disconnected, switch it off/on in system settings. AutoClick cannot bypass Android's stopped state or manufacturer background restrictions.
- **下次** means the system API accepted a future arrangement, not guaranteed on-time execution. Missing/failed arrangements do not invent a next time. **全部点击手势已完成** means Android completed the gestures, not that the target business action succeeded.

## Development and short verification

Use SDK 36 and the project-configured JBR 21.0.11. The currently installed Android Studio JDK 25 does not configure this Gradle/Kotlin version successfully; no toolchain upgrade is required for this integration:

```sh
export JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest \
  :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug \
  --no-daemon --max-workers=2
```

Device tests create/delete fixtures. Snapshot application files, accessibility settings and the exact-alarm app-op before running; cancel fixture alarms and restore/verify those values afterwards. An old task may legitimately migrate to disabled after restoration; preserve its content/history and report the migration. Never run destructive fixtures against unprotected user data.

`EditScheduleUiTest#editedEnabledScheduleClicksOnceAndConsumedDateStaysBlocked` edits through the real home screen, waits for a real exact-alarm click, records first dispatch and button receipt, then edits later that date and checks persistent rejection and the next future date. `ExactTimingIntegrationTest#quickExactTaskFiresAtNextMinute` uses the production clock/controller/AlarmManager/receiver/service and an actual button. It chooses the next minute with at least five seconds of save margin, waits at most 90 seconds, checks both first-dispatch and button-receipt times within the five-second window, checks one click and no duplicate, and verifies an independent future alarm. Other gesture-protection regressions inject an internal clock to avoid repeating minute-long waits; those do not prove production timing. Production time/window cannot be overridden by Intent, preferences or UI.

`ExactTimingPermissionUiTest` supports host-driven `permissionPhase=seed/lost/granted`. End instrumentation before revoking permission: revocation can kill the app. Establish an ordinary app/service baseline and explicitly register a real fixture alarm before revocation; verify its cancellation and each phase's test result. Granting alone must leave the task disabled.

`ClickProcessRestoreTest` is opt-in (`processRestorePhase=seed/verify`) for persistence across separate processes. `ClickRecoveryProcessTest` is opt-in (`processRecoverySeed=true`) for a separate PID-kill experiment: after instrumentation ends, establish normal binding, explicitly enable the saved fixture alarm, open the test-APK target, and kill only the AutoClick PID. Observe OS restart and the real button without launching the app, changing permissions or restarting instrumentation after the kill. Simulated BOOT/recovery events and an actual device reboot are different validations; a real reboot is optional. Emulator acceptance does not cover physical-device manufacturer policies.

The current integration checkout is this project directory. Non-sensitive diagnostics and the protected test runner are in `.artifacts/autoclick-integration/`; source backups are kept there without creating another checkout. Run each device batch serially on the existing emulator:

```sh
python3 .artifacts/autoclick-integration/run_tests.py edit-store com.lumostech.autoclick.ScheduleEditIntegrationTest
python3 .artifacts/autoclick-integration/run_tests.py edit-ui com.lumostech.autoclick.EditScheduleUiTest
python3 .artifacts/autoclick-integration/run_tests.py permission com.lumostech.autoclick.ExactTimingPermissionUiTest#hostDrivenPermissionRoundtrip permissions
```

The runner snapshots application files and settings, verifies restoration even after failures, and keeps private recovery backups on unresolved failures. See `.artifacts/autoclick-integration/report.md` for the earlier integration results and `.artifacts/autoclick-status-audit/2026-10-08.md` for the pre-integration inventory.

2026-10-09 integration acceptance: 103 local tests and 115 device cases passed across protected sequential batches, with no failures or skips. Edited and unedited production alarms each produced one real button click within the first-gesture window. APK hashes, screenshots, restoration and unverified physical-device/reboot limits are recorded in the integration report.

Legacy recovery acceptance uses `.artifacts/autoclick-legacy-recovery/run_acceptance.py`. It runs protected storage/UI regressions and three actual process-kill/reopen phases serially on the existing `emulator-5554`, preserving app files, accessibility, exact-alarm/overlay permissions, display settings and the original installed APK. See `.artifacts/autoclick-legacy-recovery/report.md` for actual results, screenshots, APK hashes and physical-device limits. No fixture should be run against unprotected user data.

2026-10-09 legacy recovery acceptance: 110 local tests and 109 device cases passed with no failures or skips; Lint reported 0 errors and 34 warnings, matching the previous warning count. The final APK was used for every device batch. Three host-driven PID-kill/reopen phases retained the correct disabled task and draft without scheduling an alarm. The recovered task received zero automatic clicks from two old deliveries and one real manual-trial click; manual enabling created a future alarm. Original app data, settings and installed APK were restored and verified after each batch. Physical devices, manufacturer background policies and actual system reboot remain unverified.
