# AutoClick 秒级启动与超时跳过 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to execute this plan inline after the user explicitly approves it. Steps use checkbox (`- [x]`) syntax. Do not dispatch development subagents, automatically commit, push, or alter unrelated work.

**Goal:** 使用精确闹钟启动已保存的定时点击，首手势仅允许在计划时间后的五秒内发起；错过就跳过本次，继续未来计划。

**Architecture:** AlarmManager 一次性事件携带配置、启用代次和发生时间，接收器完成短时核验并把执行交给已连接无障碍服务的生命周期。控制器串行协调持久化、未来安排及恢复；执行前消费与跳过终结分别防止重放，旧 WorkManager 入口关闭。任务卡片读取精确调度记录，精确定时权限缺失只阻止定时任务。

**Tech Stack:** Kotlin、Android API 24–36、AlarmManager、SharedPreferences、协程、Compose、JUnit4、AndroidJUnit4；现有 WorkManager 仅用于取消和阻断旧调度。

---

## 状态、范围与执行纪律

设计已确认：[完整设计](../specs/2026-10-07-autoclick-exact-timing-design.md)。本计划已获用户确认，现已完成实现和模拟器验收。下列代码块保留为原计划参考，最终实现以源码为准。勾选表示对应交付结果已覆盖；RED 次序及整批复验的执行差异在文末明确记录，不表示每个原始步骤都逐字执行。

- 只修改 AutoClick、必要的共享手势回调及本轮文档。不得修改主 `app/` 网络功能、已有版本目录改动或其他未提交内容。
- 保留单任务模型、录制间隔、手动试运行五秒倒计时、既有安全检查和停止机制。任务编辑、多任务、提前待命、自动解锁/打开页面、迟到补执行、真机验收不在范围。
- 功能代码必须等用户确认本计划后再修改。沿用当前会话按任务顺序实施；每个行为先观察对应 RED，再实现并验证 GREEN。日志、截图和恢复记录放 `.artifacts/autoclick-exact-timing/`，不默认加入提交。
- 开始实施时核对工作区与已有附属 worktree；按 using-git-worktrees 技能选择安全的隔离方式。当前普通 checkout 存在无关未提交内容，不重置、自动暂存或移动这些内容。若使用新 checkout，只带入本轮已确认文档，不假设原 checkout 的未提交代码属于本轮。
- 每个任务完成做差异检查和验收记录；不用自动 commit 作为任务完成条件。代码评审按 requesting-code-review 的只读流程进行，不让评审修改代码或并发操作模拟器。
- 所有 Gradle 命令在项目根目录使用 Android Studio JDK：

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest --no-daemon --max-workers=2
```

## 文件结构与固定接口

新增代码都使用 `com.lumostech.autoclick`，纯策略不依赖 Android 设置、WorkManager 或系统时钟。默认文件根为 `autoclick/src/main/java/com/lumostech/autoclick/`。

| 文件 | 职责 |
| --- | --- |
| `ClickAlarmState.kt`，新增 | 事件、持久化调度快照、准备阶段和启动诊断；JSON 编解码 |
| `ClickClock.kt`，新增 | 生产时钟与内部测试时钟接口，生产固定使用系统时间 |
| `ExactClickPolicy.kt`，新增 | 对明确发生时间判断五秒窗口与未来时间 |
| `ClickAlarmPlatform.kt`，新增 | 可替换的平台接口与真实 AlarmManager 实现，封装权限和 PendingIntent |
| `ScheduledClickExecutor.kt`，新增 | 从旧 Worker 提取入口无关的受保护执行，归无障碍服务生命周期 |
| `ClickAlarmDispatcher.kt`，新增 | 剩余窗口连接、服务交接和准备阶段失败落盘 |
| `ClickAlarmReceiver.kt`，新增 | 解析显式定时事件、短任务生命周期 |
| `ClickScheduleRecoveryReceiver.kt`，新增 | 系统恢复事件到控制器的映射，不自动点击 |
| `ui/ExactTimingPermissionDialog.kt`，新增 | 解释定时权限、去开启和暂不 |
| `ClickTaskStore.kt`，修改 | 同锁下保存新快照，领取/消费/终结与历史保护 |
| `ClickTaskController.kt`，修改 | 保存、启用、取消、迁移、恢复、事件接纳及未来推进 |
| `ClickPeriodicWorker.kt`，修改 | 不发手势的旧入口兼容壳，保留原 TAG 与 TASK_ID |
| `ClickExecutionSession.kt`，修改 | 增加取消当前定时序列的入口，不取消试运行或偷偷停用后续计划 |
| `ClickTaskStatusObserver.kt`、`ClickTaskPresentation.kt`，修改 | 去掉 WorkInfo 作为时间来源，显示新调度与权限状态 |
| `MainActivity.kt`、`ConfirmEventHandler.kt`、`ui/AutoclickScreen.kt`，修改 | 定时授权入口、显式启用与五秒文案 |
| `AutoclickApp.kt`、生产 Manifest、README，修改 | 启动只做安全协调，声明权限/系统接收器及更新限制说明 |
| `accessibilityCore/.../AccessibilityCoreService.kt`，修改 | 增加紧贴真实 dispatchGesture 调用的通用可选检查钩子 |

新增本地测试：`ExactClickPolicyTest.kt`、`ClickAlarmStateTest.kt`、`ExactTimingPresentationTest.kt`。新增设备测试：`ClickAlarmStoreTest.kt`、`ExactAlarmPlatformTest.kt`、`ExactTimingIntegrationTest.kt`、`ExactTimingPermissionUiTest.kt`、`ExactTimingRecoveryTest.kt`。既有策略/卡片测试同步换成五秒和精确安排断言；既有真实手势测试迁移到新入口，保留旧 Worker 零点击回归。

以下名字在全计划中保持一致：

```kotlin
enum class ClickAlarmStatus {
    NEEDS_ENABLE, PERMISSION_REQUIRED, TIME_ZONE_CHANGED,
    ARMING, ARMED, SCHEDULE_FAILED
}
enum class ClickAlarmPhase { PREPARING, CLAIMED }
enum class ClickTaskSaveResult { ENABLED, SAVED_NEEDS_PERMISSION }
enum class ClickRecoveryEvent { STARTUP, BOOT, PACKAGE_REPLACED, TIME_CHANGED, ZONE_CHANGED, PERMISSION_CHANGED }

data class ClickAlarmOccurrence(
    val taskId: String,
    val scheduleId: String,
    val scheduledAt: Long,
    val version: Int = 2
) {
    fun matches(task: ClickTask): Boolean = version == 2 && task.id == taskId &&
        task.scheduleId == scheduleId && scheduledAt > 0
}

data class ClickAlarmState(
    val taskId: String,
    val scheduleId: String,
    val status: ClickAlarmStatus,
    val next: ClickAlarmOccurrence? = null,
    val active: ClickAlarmOccurrence? = null,
    val phase: ClickAlarmPhase? = null,
    val version: Int = 2
)

data class ClickStartTrace(
    val occurrence: ClickAlarmOccurrence,
    val receivedAt: Long,
    val firstDispatchAt: Long? = null
)

interface ClickAlarmPlatform {
    fun canSchedule(): Boolean
    fun schedule(occurrence: ClickAlarmOccurrence)
    fun cancel(occurrence: ClickAlarmOccurrence)
}

interface ClickClock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
}
object SystemClickClock : ClickClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
```

`task.id` 是现有的启用代次，显式重新启用会更换它；`scheduleId` 是同一录制计划的稳定身份。后续使用这些字段，不再另造 generation 或混用 WorkRequest UUID。

控制器、Dispatcher 和 Executor 的内部测试构造允许注入 `ClickClock`，生产 Context 构造只传 `SystemClickClock`。时间依赖不从 Intent、偏好、BuildConfig 或 UI 开关读取。正常下一分钟验收必须使用生产默认时钟；固定时间的真实手势保护回归不能当作真实闹钟准时证明。

## Task 0：保护现有模拟器与准备验收运行器

**Files:** 新增本地 `.artifacts/autoclick-exact-timing/run_tests.py`；读取既有 `.artifacts/autoclick-guidance-status/run_tests.py`；生成本轮工作区基线与恢复证据。这些操作也必须等本计划获确认后执行。

- [x] 在首次设备 RED 前，从既有 `.artifacts/autoclick-guidance-status/run_tests.py` 复用快照/恢复框架，扩充新 SCHEDULE_EXACT_ALARM app-op 的精确原状态、应用文件、两项无障碍设置和原已登记计划诊断。新增快照包括“默认/没有显式覆盖”，恢复不能一律写 allow。
- [x] 修正保护运行器与迁移的关系：新 APK 的启动可能迁移旧数据。finally 先恢复并逐文件比对，再启动应用验证预期迁移，不能要求旧任务启用字段与迁移前完全相等；验证任务的录制、时间、星期、保护信息、历史和消费边界均保留，旧任务停用及待启用提示符合设计。最终留下的数据迁移变化在报告中逐项列出，不称“全部字节完全未变”。
- [x] 若用户原始数据已是本版本，恢复后启动必须保持配置/启用意愿不变。测试过程中创建的未来闹钟须全部取消；恢复用户的已确认未来计划只允许按正式恢复规则办理，不能由宿主脚本偷偷启用旧任务。失败保留可恢复的私有备份并报告；成功核对后删除备份。
- [x] 运行器执行保护流程：快照 → install -r debug/test APK → 设置本次 fixture 权限 → 执行指定类 → 保存结果/失败截图/时间诊断 → finally 停止测试并取消 fixture 闹钟 → 恢复文件与授权 → 核对 → 重开验证迁移或正常状态。没有快照成功不得开始破坏性 fixture。
- [x] 只使用当前可用的 Android Studio 模拟器；用 adb devices 核对设备，不硬编码另一台新模拟器，不并发测试。类名通过参数传入，快速定时通过 `quickSchedule=true`，常规回归不反复等下一分钟。
- [x] 给运行器增加可选第三参数 `permissions`。该模式按 `permissionPhase=seed/lost/granted` 分三次运行指定 UI 类，中间由宿主修改 SCHEDULE_EXACT_ALARM app-op；每阶段必须有 OK 和独立日志。普通模式不改成轮询无限重试。
- [x] 在权限 seed 结束后建立正常应用/服务基线；通过读取界面实际“停用任务 / 启用任务”按钮位置操作，明确重新登记未来 fixture 计划，并核对 dumpsys alarm 中对应的自有事件，再撤权。不得在应用已经被 instrumentation 强停且没有真实闹钟时，宣称验证了撤权取消闹钟。重授阶段只查询状态，不替用户点击启用。
- [x] 用一次不涉及真实手势的测试验证运行器即使故意失败也进入 finally，恢复文件和 app-op 能力、核对并保留失败证据；确认保护机制可用后才能进行后续设备 RED。第一次运行记录现有应用是否尚未迁移，确保最终恢复按相应版本验证。

## Task 1：明确发生时间的五秒策略

**Files:** 新增 `ExactClickPolicy.kt`、`ClickClock.kt`；新增 `autoclick/src/test/java/com/lumostech/autoclick/ExactClickPolicyTest.kt`；修改 `ClickSchedulePolicy.kt` 和对应本地测试。

- [x] 写 RED：固定 Asia/Shanghai 的 2026-10-07 09:00，验证早到、0、4999、5000 毫秒，跨日与事件时间不属于配置。核心测试完整样例：

```kotlin
class ExactClickPolicyTest {
    private val zone = "Asia/Shanghai"
    private val due = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
        set(2026, java.util.Calendar.OCTOBER, 7, 9, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val task = ClickTask("epoch", 9, 0, (1..7).toSet(),
        listOf(com.lumostech.accessibilitycore.ClickCounterPoint(100f, 100f, 0)),
        timeZoneId = zone)

    @org.junit.Test fun firstDispatchWindowHasExclusiveEnd() {
        org.junit.Assert.assertEquals(ClickScheduleStatus.EARLY,
            ExactClickPolicy.evaluate(task, due, due - 1, zone).status)
        org.junit.Assert.assertEquals(ClickScheduleStatus.READY,
            ExactClickPolicy.evaluate(task, due, due, zone).status)
        org.junit.Assert.assertEquals(ClickScheduleStatus.READY,
            ExactClickPolicy.evaluate(task, due, due + 4_999, zone).status)
        org.junit.Assert.assertEquals(ClickScheduleStatus.LATE,
            ExactClickPolicy.evaluate(task, due, due + 5_000, zone).status)
    }

    @org.junit.Test fun oldEventNeverBecomesTodaysOccurrence() {
        org.junit.Assert.assertEquals(ClickScheduleStatus.LATE,
            ExactClickPolicy.evaluate(task, due, due + 86_400_000, zone).status)
        org.junit.Assert.assertEquals(ClickScheduleStatus.INVALID,
            ExactClickPolicy.evaluate(task, due + 1, due + 1, zone).status)
        org.junit.Assert.assertEquals(ClickScheduleStatus.TIME_ZONE_CHANGED,
            ExactClickPolicy.evaluate(task, due, due, "UTC").status)
    }
}
```

- [x] 运行 `./gradlew :autoclick:testDebugUnitTest --tests '*ExactClickPolicyTest' --no-daemon --max-workers=2`，确认缺少新策略 API 的失败。
- [x] 实现完整纯策略，并将既有 `ClickSchedulePolicy.evaluate` 的迟到分支改为 `lateness >= ExactClickPolicy.START_WINDOW_MS`，LATE 文案改为“已错过启动时间，本次跳过”。将原 MAX_LATENESS_MS 临时保留为指向 START_WINDOW_MS 的 deprecated 兼容别名，值为五秒，避免尚未迁移的 presenter 编译失败；Task 8 移除最后引用和别名。更新现有策略测试的边界。

```kotlin
object ExactClickPolicy {
    const val START_WINDOW_MS = 5_000L

    fun evaluate(task: ClickTask, due: Long, now: Long,
                 currentZone: String): ClickScheduleDecision {
        if (!task.isValid() || due <= 0) return ClickScheduleDecision(ClickScheduleStatus.INVALID, due)
        if (task.timeZoneId != currentZone)
            return ClickScheduleDecision(ClickScheduleStatus.TIME_ZONE_CHANGED, due)
        val date = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(task.timeZoneId)).apply {
            timeInMillis = due
        }
        val valid = date.get(java.util.Calendar.DAY_OF_WEEK) in task.days &&
            date.get(java.util.Calendar.HOUR_OF_DAY) == task.hour &&
            date.get(java.util.Calendar.MINUTE) == task.minute &&
            date.get(java.util.Calendar.SECOND) == 0 && date.get(java.util.Calendar.MILLISECOND) == 0
        if (!valid) return ClickScheduleDecision(ClickScheduleStatus.INVALID, due)
        val status = when {
            now < due -> ClickScheduleStatus.EARLY
            now - due >= START_WINDOW_MS -> ClickScheduleStatus.LATE
            else -> ClickScheduleStatus.READY
        }
        return ClickScheduleDecision(status, due)
    }

    fun remaining(due: Long, now: Long): Long =
        if (now < due) 0 else (START_WINDOW_MS - (now - due)).coerceAtLeast(0)

    fun startupOpen(due: Long, receivedAt: Long, receivedElapsed: Long,
                    nowWall: Long, nowElapsed: Long): Boolean {
        val originalBudget = remaining(due, receivedAt)
        val spent = nowElapsed - receivedElapsed
        return originalBudget > 0 && remaining(due, nowWall) > 0 &&
            spent >= 0 && spent < originalBudget &&
            ClickSchedulePolicy.clockUnchanged(receivedAt, receivedElapsed, nowWall, nowElapsed)
    }

    fun next(task: ClickTask, now: Long, closedThrough: Long): Long =
        ClickSchedulePolicy.nextOccurrence(task, maxOf(now, closedThrough))
}
```

- [x] 补充未来时间跨周、夏令时、消费/跳过边界大于当前时间、时间回拨测试；窗口后的已开始序列继续使用现有 `isSameOccurrence` 和 `clockUnchanged`，不再重复套首点击窗口。还须断言 startupOpen 在 receivedAt=T+4000、receivedElapsed=10000、nowWall=T+3500、nowElapsed=11001 时为 false：即使小幅回拨仍在原时钟容差内，单调时间的剩余预算也不得延长。
- [x] 运行策略测试并确认 GREEN。此时不接入用户入口，旧 Worker 的行为切换安排在 Task 6，与迁移同时闭环。

## Task 2：调度快照与发生次数账本

**Files:** 新增 `ClickAlarmState.kt`、`ClickAlarmStateTest.kt`、设备 `ClickAlarmStoreTest.kt`；修改 `ClickTaskStore.kt`、`ClickExecutionRecord.kt`。

- [x] 写 JSON RED：新快照往返相等，未知 status/损坏时间/身份不符不能恢复为 ARMED，历史 execution_record 无需新增字段也能读取。设备 RED 使用真实 SharedPreferences 验证保存独立、同计划停用/启用保留账本、新计划清理账本。
- [x] 运行 `./gradlew :autoclick:testDebugUnitTest --tests '*ClickAlarmStateTest' --no-daemon --max-workers=2`；设备 RED 在 Task 0 的保护运行器验证完成后执行，不能提前清空用户数据。
- [x] 实现 Task 头部数据类型的 JSON 编解码。字段使用上述属性原名；未知 version/status、非正 plannedAt 或 taskId/scheduleId 不一致，decode 返回 null。`active` 和 `next` 允许同属任务但发生时间不同；`phase` 仅在 active 非空时存在。`ClickStartTrace` 为独立键，不覆盖真实历史。
- [x] 在现有 Store 同一 `lock` 下增加以下方法。方法声明是实施时固定的接口；调用方不得绕过 Store 直接编辑偏好。

```kotlin
fun alarmState(): ClickAlarmState?
fun startTrace(): ClickStartTrace?
fun closedThrough(): Long
fun saveAlarmState(taskId: String, state: ClickAlarmState, enabled: Boolean? = null): Boolean
fun reserveAlarm(occurrence: ClickAlarmOccurrence, receivedAt: Long): Boolean
fun claimAlarm(occurrence: ClickAlarmOccurrence): ClickExecutionClaim
fun finishAlarm(occurrence: ClickAlarmOccurrence, reason: ClickOutcomeReason, message: String): Boolean
fun recordFirstDispatch(occurrence: ClickAlarmOccurrence, firstDispatchAt: Long): Boolean
```

Store 实施规则逐项固定；本任务编解码需提供 `ClickAlarmState.encode/decode`、`ClickAlarmOccurrence.encode/decode`、`ClickStartTrace.encode/decode`，供后续方法直接使用：

| 方法 | 原锁内的核验与单次 commit |
| --- | --- |
| `alarmState/startTrace` | 解码并核验当前 scheduleId；状态还须核验 taskId。损坏返回 null |
| `closedThrough` | 返回同一 scheduleId 下 `consumed_at` 与新 `closed_at` 的最大值，缺省为 0 |
| `saveAlarmState` | 核验 current.id；保存 exact_alarm JSON，必要时同次更新 task JSON 的 enabled；不调用会覆盖历史的通用 outcome 写入 |
| `reserveAlarm` | 核验启用、保护、版本、ARMED、next 等于事件、没有 active、scheduledAt 大于 closedThrough；同次保存 active=事件、phase=PREPARING、next=null 及 receivedAt |
| `claimAlarm` | 核验仍是该 active 且 PREPARING、current.id/启用有效、没有终结；在原同锁事务迁移旧历史，保存 consumed_at、execution_pending=true、phase=CLAIMED；存储失败沿用现有“不点击”处理 |
| `finishAlarm` | 核验 current.id 和 active 身份；claimed 只能结束自己的消费，未 claimed 不能覆盖已消费的较新发生次数；同次更新 closed_at 最大值、独立结果、清理 active/phase/execution_pending，保留 next 和其安排状态 |
| `recordFirstDispatch` | 核验 active/CLAIMED 和 receivedAt 归属；更新独立 trace。不得因后续写 trace 失败重发已发出的手势 |

`saveAlarmState` 的关键原子更新直接嵌入现有 Store，不让状态写入与启用写入分开。对于同时保存 consumed/closed/active/结果的方法，沿用同一原锁和同一次 commit，不能用先调用 saveAlarmState 再另写账本的两笔事务替代：

```kotlin
fun saveAlarmState(taskId: String, state: ClickAlarmState, enabled: Boolean? = null): Boolean = synchronized(lock) {
    val current = load() ?: return false
    if (current.id != taskId || state.taskId != taskId || state.scheduleId != current.scheduleId) return false
    val editor = preferences.edit().putString("exact_alarm", state.encode())
    if (enabled != null) {
        val json = org.json.JSONObject(preferences.getString("task", null) ?: return false)
        json.put("enabled", enabled)
        editor.putString("task", json.toString())
    }
    editor.commit()
}
```

- [x] 修改现有 `save`：仅 `!sameSchedule` 时清除 `exact_alarm`、`closed_at`、`start_trace`，同计划启用代次变化保留消费/跳过边界和历史。状态的旧 taskId 不能作为新启用的安排事实。
- [x] 为 `ClickOutcomeReason` 加入 `EXECUTION_BUSY`、`STOP_CONTROL_UNAVAILABLE`、`EXACT_PERMISSION_REQUIRED`，保留 UNKNOWN 向后兼容；控制状态和授权操作仍不伪装为点击结果。
- [x] 加入两类崩溃恢复断言：已 reserve 但未 claim 的进程重建不重复接纳；已 claim 但未写完成记录不得再次执行。恢复入口将遗留 active 终结为中断，保留原历史并报告该次无自动重放。
- [x] 验证终结失败、旧回调、重复终结都不能改变新 next；全量 JVM GREEN，设备存储 GREEN 在保护运行器中完成。

## Task 3：平台闹钟、授权与可取消身份

**Files:** 新增 `ClickAlarmPlatform.kt`；新增设备 `ExactAlarmPlatformTest.kt`；修改生产 Manifest 的权限声明。

- [x] 写平台 RED：没有权限时不得登记；不同 taskId 或时间不能匹配同一 PendingIntent；cancel 不创建不存在的 PendingIntent；生产不使用 `FLAG_ONE_SHOT`。注入 fake 平台检验注册异常向上传播，真实设备检查 Intent/action/data 和授权状态。
- [x] 实现真实适配器。下面是完整核心实现；接收器解析也使用同一 URI 结构：

```kotlin
class AndroidClickAlarmPlatform(context: android.content.Context) : ClickAlarmPlatform {
    private val app = context.applicationContext
    private val manager = app.getSystemService(android.app.AlarmManager::class.java)

    override fun canSchedule(): Boolean = android.os.Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun eventIntent(o: ClickAlarmOccurrence): android.content.Intent =
        android.content.Intent(app, ClickAlarmReceiver::class.java).apply {
            action = app.packageName + ".EXACT_CLICK"
            data = android.net.Uri.Builder().scheme("autoclick").authority(app.packageName)
                .appendPath(o.version.toString()).appendPath(o.scheduleId)
                .appendPath(o.taskId).appendPath(o.scheduledAt.toString()).build()
        }

    override fun schedule(occurrence: ClickAlarmOccurrence) {
        check(canSchedule()) { "定时权限未开启" }
        val pending = android.app.PendingIntent.getBroadcast(app, 0, eventIntent(occurrence),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, occurrence.scheduledAt, pending)
    }

    override fun cancel(occurrence: ClickAlarmOccurrence) {
        val pending = android.app.PendingIntent.getBroadcast(app, 0, eventIntent(occurrence),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    fun parse(intent: android.content.Intent): ClickAlarmOccurrence? = runCatching {
        if (intent.action != app.packageName + ".EXACT_CLICK") return null
        val uri = intent.data ?: return null
        if (uri.scheme != "autoclick" || uri.authority != app.packageName) return null
        val p = uri.pathSegments
        if (p.size != 4 || p[0] != "2" || p[1].isBlank() || p[2].isBlank()) return null
        ClickAlarmOccurrence(p[2], p[1], p[3].toLong()).takeIf { it.scheduledAt > 0 }
    }.getOrNull()
}
```

- [x] 在 Manifest 声明 `android.permission.SCHEDULE_EXACT_ALARM` 和 `android.permission.RECEIVE_BOOT_COMPLETED`。不声明 USE_EXACT_ALARM，不新建前台服务，不引入新的网络依赖。
- [x] 授权设置 Intent 固定为：

```kotlin
fun exactTimingSettingsIntent(context: android.content.Context): android.content.Intent =
    android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        android.net.Uri.parse("package:" + context.packageName))
```

只在 API 31+ 且实际需要授权时调用；捕获 ActivityNotFoundException/SecurityException 后提示用户手动进入设置。查询能力和登记异常均须处理；grant=true 不代表实际登记一定成功。
- [x] 构建 APK 与测试 APK；用保护运行器验证平台身份和授权测试 GREEN。恢复权限时不能用“强制授予后一直留下”替代原状态恢复。

## Task 4：任务控制器与下一次推进

**Files:** 修改 `ClickTaskController.kt`；设备 `ClickTaskIntegrationTest.kt` 改为精确安排断言；新增设备 `ExactTimingRecoveryTest.kt` 的控制器场景。

- [x] RED：平台 spy 断言保存时只登记一次未来事件、无授权只保存停用配置、重新启用更换 taskId、正常和跳过都推进未来、重复事件不推进第二次、失败不显示 ARMED。使用设备真实 Store 和注入的 `ClickAlarmPlatform`；不能拿 WorkInfo 代替新安排事实。
- [x] 将控制器的依赖改为 Store、`ClickAlarmPlatform`、旧 WorkManager 取消函数、`ClickClock`；Context 构造函数提供真实实现。保留现有 static `mutationLock`。公共用户接口与内部入口固定为：

```kotlin
suspend fun save(task: ClickTask): ClickTaskSaveResult
suspend fun setEnabled(enabled: Boolean)
suspend fun delete()
suspend fun emergencyStop()
suspend fun reconcile(event: ClickRecoveryEvent = ClickRecoveryEvent.STARTUP)
internal suspend fun acceptAlarm(o: ClickAlarmOccurrence, receivedAt: Long): ClickTask?
internal suspend fun finishAlarm(o: ClickAlarmOccurrence, reason: ClickOutcomeReason, message: String)
```

删除新调度对 `enqueue`/`alignNextOccurrence` 的使用；既有测试对 internal `(store, WorkManager)` 构造的依赖随本任务迁移。保留 WorkManager 依赖仅供迁移取消与兼容回归。

- [x] 实现私有未来安排函数。所有调用者已持有 mutationLock；不能在持锁时再次调用另一公开持锁方法。将幂等取消与保存失败都反馈给外层，不吞掉取消异常：

```kotlin
private fun armNext(task: ClickTask, active: ClickAlarmState? = store.alarmState()) {
    val due = ExactClickPolicy.next(task, clock.wallMillis(), store.closedThrough())
    val occurrence = ClickAlarmOccurrence(task.id, task.scheduleId, due)
    val pending = ClickAlarmState(task.id, task.scheduleId, ClickAlarmStatus.ARMING,
        next = occurrence, active = active?.active, phase = active?.phase)
    check(store.saveAlarmState(task.id, pending)) { "无法保存定时安排" }
    try {
        platform.schedule(occurrence)
        check(store.saveAlarmState(task.id, pending.copy(status = ClickAlarmStatus.ARMED))) {
            "无法确认定时安排"
        }
    } catch (error: Exception) {
        val cleanupError = runCatching { platform.cancel(occurrence) }.exceptionOrNull()
        cleanupError?.let { error.addSuppressed(it) }
        if (!store.saveAlarmState(task.id, pending.copy(status = ClickAlarmStatus.SCHEDULE_FAILED, next = null))) {
            error.addSuppressed(IllegalStateException("定时失败状态未能保存"))
        }
        throw error
    }
}
```

`platform`、`clock` 为注入依赖。失败清理只含同步平台调用和 commit，不在已取消的 Job 上追加普通挂起；CancellationException 同样清理后原样抛出，不能被 acceptAlarm 当成普通登记失败继续点击。下一次非取消异常失败时保留已接纳 active；已经取得本次执行资格的序列可以在窗口内继续，卡片明确显示后续安排失败，不用新失败记录覆盖本次点击结果。

- [x] 按下面的事务顺序实现控制方法，不在未持久化时先发出手势或调用 `allowScheduled`：

| 操作 | 顺序 |
| --- | --- |
| save | 取消旧 active 的定时执行与旧 next；保存新配置，先 enabled=false；缺权写 PERMISSION_REQUIRED 并返回 SAVED_NEEDS_PERMISSION；有权写新代次、enabled=true 后 armNext；只有登记确认成功才 allowScheduled 并返回 ENABLED |
| enable=true | 核验保护和权限；缺权保持停用；有权生成新 task.id、保留 scheduleId/账本/历史；取消旧 next、保存新任务后 armNext；成功才 allowScheduled |
| enable=false/delete/emergencyStop | 先让当前定时序列不能继续；在 finally 路径取消旧 next；持久化停用或清理；任一失败必须反馈界面，不丢失独立停止动作 |
| acceptAlarm | 锁内核验 task、版本、权限、ARMED next 身份；早到只保证原未来登记、不终结；有效窗口或迟到时 reserveAlarm；再 armNext；重复事件不会匹配 next 或 active 接纳条件 |
| finishAlarm | 锁内只终结相同 active；不因结果写入改变已确认的 next；结果失败也不自动重试手势 |

迟到事件也先取得该发生次数的处理归属，再终结为 START_EXPIRED；不能直接覆盖当前新任务的结果。登记下一次使用短本地事务和平台调用，不等待后台工作队列；准备与首手势仍以原 due 截止检查。
- [x] 当下一次登记失败但 active 接纳已成功时，`acceptAlarm` 返回当前任务并保留失败状态；不得因平台异常遗留 PREPARING 永远无法结束。若接纳的持久化失败，返回 null 且不执行。
- [x] 全量本地测试 GREEN；保护运行器中验证控制器/存储场景。应用入口在接收器、执行器及迁移都完成后再统一接通。

## Task 5：服务生命周期执行与最终手势检查

**Files:** 新增 `ScheduledClickExecutor.kt`；修改 `ClickExecutionSession.kt`、`ClickServiceConnection.kt`、`accessibilityCore/src/main/java/com/lumostech/accessibilitycore/AccessibilityCoreService.kt`；迁移 `GestureIntegrationTest.kt`；新增 `ExactTimingIntegrationTest.kt`。

- [x] RED：实际按钮页面上，使准备阶段用尽窗口、第一点等待跨过窗口，要求零按钮点击；第二点晚于五秒但首点及时的序列应完成。保留锁屏、应用切换、显示改变、停止控件和服务身份回归。
- [x] 给共享服务增加 `beforeDispatch` 可选参数，放在 `canContinue` 之前，保留已有 trailing lambda 的含义：

```kotlin
suspend fun executeClickSequence(
    points: List<ClickCounterPoint>,
    beforeDispatch: (() -> Boolean)? = null,
    canContinue: () -> Boolean = { true }
): Boolean
```

在现有 `suspendCancellableCoroutine` 内，构造 gesture 后、`dispatchGesture` 前加入完整分支。外层现有 canContinue 与服务身份核验仍保留：

```kotlin
if (beforeDispatch != null && !beforeDispatch()) {
    if (continuation.isActive) continuation.resume(false)
    return@suspendCancellableCoroutine
}
```

共享库不认识定时窗口，也不负责消费任务。这个通用钩子用于把最末检查放到现有内部 50ms 和第一点间隔之后；旧调用默认没有新行为。

- [x] 从旧 Worker 提取 `ScheduledClickExecutor`，构造依赖为 context、store、controller、clock；`suspend fun execute(service: AccessibilityCoreService, task: ClickTask, occurrence: ClickAlarmOccurrence, receivedAt: Long, receivedElapsed: Long)`。执行器必须由接收器连接到服务后的 `service.lifecycleScope.launch` 调用，不使用 Worker 或 Activity scope。receivedAt/receivedElapsed 是 Dispatcher 进入时的一对锚点，交接不能重取后重新计时。各阶段顺序：

| 阶段 | 实施内容 |
| --- | --- |
| 进入 | 用明确 occurrence.scheduledAt 判断窗口，核验 task.id 和 active；startWall=receivedAt，startElapsed=receivedElapsed |
| 取执行权 | 原 `ClickExecutionSession.acquire(..., manual=false)`；受剩余窗口 timeout 限制，失败分别记录 busy/stop control，不排队 |
| 首次环境核验 | 原锁屏、互动屏幕、显示/包名、停止控件遮挡、service 身份及时间检查 |
| 消费 | `store.claimAlarm(occurrence)` 成功后才允许手势；其余结果零点击返回 |
| 执行 | 原九分钟保护超时与逐点安全检查；窗口只在首手势钩子检查 |
| 完成/失败/取消 | 用明确 active 身份 finishAlarm，release 放 NonCancellable；序列中断不重放 |

- [x] 最终首手势钩子实施如下。`context`、`store`、`task`、`occurrence`、`service`、`handle`、`receivedAt`、`clock` 来自执行器参数/进入阶段；`stopReason`/`stopCode` 为局部结果，`nextPoint` 从 0 开始。`canContinue` 不提前递增 point；进度界面更新在末次时间检查之前：

```kotlin
var firstDispatchAt: Long? = null
var nextPoint = 0
val completed = service.executeClickSequence(task.points, beforeDispatch = {
    val now = clock.wallMillis()
    val nowElapsed = clock.elapsedMillis()
    if (firstDispatchAt == null &&
        (!ExactClickPolicy.startupOpen(occurrence.scheduledAt, receivedAt, receivedElapsed, now, nowElapsed) ||
            ExactClickPolicy.evaluate(task, occurrence.scheduledAt, now,
                java.util.TimeZone.getDefault().id).status != ClickScheduleStatus.READY)) {
        stopCode = ClickOutcomeReason.START_EXPIRED
        stopReason = "已错过启动时间，本次跳过"
        false
    } else {
        if (firstDispatchAt == null) firstDispatchAt = now
        nextPoint++
        true
    }
}) {
    val current = store.load()
    val now = clock.wallMillis()
    val point = task.points.getOrNull(nextPoint)
    val failure = when {
        current?.id != task.id || !current.enabled -> "任务已停用、删除或替换"
        AccessibilityCoreService.accessibilityCoreService !== service -> "无障碍服务不可用"
        !ClickSchedulePolicy.clockUnchanged(startWall, startElapsed, now, clock.elapsedMillis()) -> "系统时间已改变"
        !ClickSchedulePolicy.isSameOccurrence(task, occurrence.scheduledAt, now) -> "日期或时区已改变"
        point == null -> "点击序列无效"
        else -> handle.failureAt(point) ?: task.protection!!.failureAt(nextPoint,
            service.currentClickEnvironment(), power.isInteractive, keyguard.isKeyguardLocked, point)?.message
    }
    if (failure != null) stopReason = failure
    else handle.update("定时执行准备 ${nextPoint + 1} / ${task.points.size}")
    failure == null
}
```

实际实现还须为每种检查失败赋明确 `ClickOutcomeReason`，按旧 Worker 的 protection 映射保留原因。`power/keyguard` 沿用旧 Worker 创建方式，`startWall/startElapsed` 保持 Dispatcher 的原始锚点。外层 finally 在 NonCancellable 下先保存 `firstDispatchAt` trace，再 finishAlarm，最后 release；包括后续点失败和取消，不能只有全部成功才保存 trace。不能在最后时间检查与 dispatch 之间插入磁盘写入或界面更新。首手势时刻用于诊断，发起被系统拒绝仍记录 DISPATCH_FAILED，不把它当作成功按钮点击。

- [x] 新增 `ClickExecutionSession.cancelScheduled(reason: String)`，仅取消 active.manual=false 的 job；同步停止能力与公开紧急停止复用。系统时间变化取消当前序列但不设置全局 emergency-stop latch；显式紧急停止仍先 gate.stop 再持久化停用。

```kotlin
fun cancelScheduled(reason: String) {
    val handle = active ?: return
    if (handle.manual) return
    handle.message = reason
    handle.job.cancel(kotlinx.coroutines.CancellationException(reason))
}
```

该方法仅 Main 调用；控制器从 IO 进入时使用 `withContext(Dispatchers.Main.immediate)`。取消后更换任务可利用 lease 与身份校验保证旧 finally 不终结新任务。
- [x] 给现有 ClickRunState 增加带 null 默认值的 taskId/scheduleId，acquireOnMain 建立会话时一起写入，方便卡片判断当前会话归属。完整新增字段及赋值：

```kotlin
data class ClickRunState(
    val active: Boolean = false,
    val manual: Boolean = false,
    val message: String = "尚未执行",
    val lastTrialResult: String = "",
    val stopError: String? = null,
    val taskId: String? = null,
    val scheduleId: String? = null
)
```

在 acquireOnMain 的 `active = handle` 之后、第一次 `handle.update` 之前插入：

```kotlin
mutableState.value = mutableState.value.copy(taskId = task.id, scheduleId = task.scheduleId)
```

release 仍保留独立 lastTrialResult，active=false 后旧身份不代表活跃执行；呈现必须同时判断 active、manual、taskId、scheduleId。
- [x] `ClickServiceConnection.await` 加 `timeoutMs: Long = MAX_WAIT_MS` 参数并转交给 ServiceConnectionWaiter；定时入口传 ExactClickPolicy.remaining，其余旧调用保留默认。remaining<=0 时直接返回，不能调用要求 timeout>0 的等待器。
- [x] 运行共享库及 AutoClick JVM 测试；设备 GREEN 验证实际手势、跨窗口序列继续、首点前超时零点击及停止行为。不能只测试纯 predicate 后宣布最终 dispatch 位置已覆盖。

## Task 6：短接收器、服务交接与旧入口阻断

**Files:** 新增 `ClickAlarmDispatcher.kt`、`ClickAlarmReceiver.kt`；修改 `ClickPeriodicWorker.kt`；扩充设备 `ExactTimingIntegrationTest.kt`。

- [x] RED：两个相同事件并发只开始一个会话；连接超时/窗口耗尽不点击；切换目标应用时首手势受保护；旧 Worker 即使手工排入也零点击。
- [x] 在 Dispatcher 实现 `suspend fun dispatch(context: Context, occurrence: ClickAlarmOccurrence, clock: ClickClock = SystemClickClock)`：进入时捕获 receivedAt=clock.wallMillis()、receivedElapsed=clock.elapsedMillis()，用控制器 acceptAlarm；未接纳立即结束；过期 finish START_EXPIRED；权限关闭 finish EXACT_PERMISSION_REQUIRED；其余在剩余窗口内 await。等待上限取墙上时间剩余值与原始单调预算剩余值的最小值，canContinue 也调用 startupOpen，不能在小幅回拨后增加等待。取消、超时、服务身份改变均 finish 对应 active，next 保留。
- [x] 连接成功后在 Main 创建 `service.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED)`，调用 ScheduledClickExecutor；launch 后 receiver 只负责结束短任务。交接前再次核验窗口与任务身份。不能把 receiver coroutine 的 Job 作为服务序列的父 Job，否则 finish 后会取消长序列。
- [x] 接收器的完整外壳：

```kotlin
class ClickAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val app = context.applicationContext
        val occurrence = AndroidClickAlarmPlatform(app).parse(intent) ?: return
        val pending = goAsync()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
        scope.launch {
            try {
                ClickAlarmDispatcher.dispatch(app, occurrence)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                com.lumostech.accessibilitybase.utils.Logger.e("ClickAlarmReceiver", "Alarm handoff failed", error)
                ClickTaskController(app).finishAlarm(occurrence, ClickOutcomeReason.UNKNOWN, "执行检查异常，未执行")
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
```

文件加 `import kotlinx.coroutines.launch`/`cancel`；短任务对连接等待使用 due 的剩余时间，异常落盘放 NonCancellable 并严格限时，pending.finish 保证执行。不能在 receiver 中等待八分钟序列完成。接收入口通过生产 Manifest 的 `exported=false` 与显式自有 PendingIntent 限制来源。

- [x] 将旧 Worker 替换成以下不可执行兼容壳，保留类名和常量供旧 WorkManager 实例化/取消：

```kotlin
class ClickPeriodicWorker(context: android.content.Context, params: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success(
        androidx.work.Data.Builder().putString("outcome", "旧定时入口已停用，请启用新定时任务").build()
    )
    companion object {
        const val TAG = "ClickPeriodicWorker"
        const val TASK_ID = "task_id"
    }
}
```

壳不写最近结果，不调控制器重新启用，不发手势；原 Worker 的执行保护已移至 Task 5。保留一项 TestListenableWorkerBuilder 回归验证此行为，其他原 worker 场景迁移到新执行链路。
- [x] 构建 debug/test APK，运行保护的接收器/旧入口设备 GREEN；确认服务序列能在 receiver 完成后继续，服务销毁后能取消并清理停止控件。

## Task 7：迁移、恢复及权限变化

**Files:** 新增 `ClickScheduleRecoveryReceiver.kt`；修改 `ClickTaskController.kt`、`AutoclickApp.kt`、生产 Manifest；设备 `ExactTimingRecoveryTest.kt`。

- [x] RED：旧任务首次迁移停用但保留草稿、配置、消费和历史；失败后不生成迁移成功状态；grant 事件不登记；BOOT 只恢复已明确启用的新版本任务；STARTUP 不无条件登记。
- [x] 实现控制器 reconcile 事件表。所有入口使用同一 mutationLock；迁移判断以新 state.version/身份为准，不能仅靠已有 enabled=true：

| 入口 | 固定行为 |
| --- | --- |
| 任意入口首次看见未迁移任务 | 先阻断旧 Worker，再等待 cancelUniqueWork 完成；保存 enabled=false、NEEDS_ENABLE 的 version2 状态；任何失败仍禁止新点击，不覆盖历史 |
| STARTUP | 执行幂等迁移、校验权限和未完成 active；缺权暂停，损坏/ARMING 状态显示未确认；已 ARMED 未来安排不重复登记 |
| BOOT / PACKAGE_REPLACED | 仅 current.enabled=true、版本2、无待授权/时区问题者恢复；终结已过窗口的旧 next/active 后安排严格未来时间 |
| TIME_CHANGED | 先取消当前定时序列和旧 next，保留终结边界；已启用且其他条件符合时安排未来，不回放过去 |
| ZONE_CHANGED | 取消定时序列与 next，写 TIME_ZONE_CHANGED 且 enabled=false；需现有保存入口重新保存 |
| PERMISSION_CHANGED | 实时查询能力，取消旧 next，写 PERMISSION_REQUIRED 或 NEEDS_ENABLE 且 enabled=false；授予也不调用 armNext |

时间变化取消的是当前定时序列；现有试运行通过自身时钟检查停止。无需为了恢复而启动 Activity 或目标应用。恢复中的过期结果用明确原 occurrence 时间，不推断未送达原因。
- [x] 恢复接收器 action 映射使用 Android 常量与以下 Manifest 配置。应用更新只接收本包 replacement；exact permission grant 自行再次查能力：

```xml
<receiver android:name=".ClickAlarmReceiver" android:exported="false" />
<receiver android:name=".ClickScheduleRecoveryReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
    </intent-filter>
</receiver>
```

接收器使用与 Task 6 相同的短任务 finish 保证，但只调用 reconcile(event)，没有调度点击的 dispatcher。未知 action 立即返回。取消异常不吞；数据保存失败反馈日志和待修复状态。
- [x] AutoclickApp.onCreate 与 MainActivity 启动协调改为 reconcile(STARTUP)，不创建新周期 WorkRequest。权限事件与启动初始化串行，Application 启动不得先恢复一个待授权任务。
- [x] 测试同时覆盖“只模拟恢复事件”和实际进程重建，两者分开记录；不要把发送 BOOT 逻辑事件称为真实设备重启。

## Task 8：状态读取、权限提示与显式启用

**Files:** 修改 `ClickTaskStatusObserver.kt`、`ClickTaskPresentation.kt`、`MainActivity.kt`、`ConfirmEventHandler.kt`、`ui/AutoclickScreen.kt`；新增 `ui/ExactTimingPermissionDialog.kt`；本地 `ExactTimingPresentationTest.kt`、设备 `ExactTimingPermissionUiTest.kt`，更新既有卡片测试。

- [x] RED：权限缺失时没有 ARMED/已启用假象；授权返回不自动登记；录制/试运行仍可用；迁移需启用提示不被通用“任务已停用”遮住；当前结果和未来 next 分开显示。
- [x] 新建 `presentExact(task, alarm, run, record, permissionGranted, now, currentZone)`；从 Store snapshot 读取实际登记的 next，不再读取 WorkInfo 或由 config 单独推断 next。精确优先级为：无任务 → 缺权限/时区暂停 → 当前身份匹配且仍允许执行的定时会话 → 当前窗口内 PREPARING → 迁移/需启用/安排失败 → 手动停用 → ARMED 未来安排 → 超时/未知。运行会话必须同时匹配 taskId/scheduleId 且 !manual；旧 scheduleId 结果仍过滤，旧 taskId 状态仍拒绝。

```kotlin
if (!permissionGranted || alarm?.status == ClickAlarmStatus.PERMISSION_REQUIRED) {
    return ClickTaskPresentation(if (permissionGranted) "权限已开启，请启用任务" else "定时权限未开启")
}
if (alarm?.status == ClickAlarmStatus.NEEDS_ENABLE) {
    return ClickTaskPresentation("请启用定时任务", recoveryHelp = RecoveryHelp.REENABLE)
}
if (alarm?.status == ClickAlarmStatus.SCHEDULE_FAILED) {
    return ClickTaskPresentation("定时安排失败，请重新启用", recoveryHelp = RecoveryHelp.REENABLE)
}
```

以上分支置于活跃会话/准备分支之后，以免后续登记失败掩盖本次正在执行。迁移与普通停用的具体文案通过 state/迁移写入的控制状态区别。未来 next 只接受 version2、status=ARMED、身份匹配、scheduledAt>now；ARMING 或缺失不给 nextAt。active 和 next 同时存在时，当前运行优先显示；next 的失败单独以 detail 提示，不能把已开始的当前会话显示成未开始。
- [x] Observer 改为 preferences changes + ClickExecutionSession.state + 前台一秒时钟；回调中实时读取 platform.canSchedule，取消时 unregister listener。观察器不写任务、不登记、不自动补执行。
- [x] 新增权限对话框，回调由 MainActivity 控制是否在前台显示。完整 Compose 内容：

```kotlin
@androidx.compose.runtime.Composable
fun ExactTimingPermissionDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("开启定时权限") },
        text = { androidx.compose.material3.Text("定时点击需要开启闹钟和提醒权限。开启后请返回并启用任务；录制和手动试运行仍可使用。") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onSettings) {
            androidx.compose.material3.Text("去开启")
        } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) {
            androidx.compose.material3.Text("暂不")
        } }
    )
}
```

- [x] MainActivity 增加独立 permission prompt 状态。只有保存/启用操作触发提示，返回前台只刷新能力和卡片；保留当前无障碍 gate。设置返回不自动调用 setEnabled(true)，后台不弹对话框，不退出应用。
- [x] `onToggleTask` 在开启分支先读真实定时权限；缺权时保持任务停用、触发独立权限提示，不把 SecurityException 只显示成通用 Toast。停用分支不要求权限，精确授权可用性不参与录制/试运行按钮的可用判断。
- [x] ConfirmEventHandler 根据 save 返回的 ENABLED/SAVED_NEEDS_PERMISSION 分支提示：“任务已保存，须在计划时间后 5 秒内开始”或“任务已保存，开启定时权限后请启用”。任务已保存但缺权限不称“保存失败”。关闭配置浮窗后，提示回调仅在 Activity 前台显示；否则下次前台卡片提供授权按钮。
- [x] AutoclickScreen 保留卡片结构，增加缺权时的“去开启”入口；任务 badge 使用实际 enabled/有效状态，不让旧 enabled=true 假装已登记。三处旧十五分钟/大致触发说明统一改为“计划时间后 5 秒内未开始则跳过；请保持亮屏解锁并打开目标页面”。更新强行停止后检查任务的说明。
- [x] 本地卡片测试 GREEN；设备验证权限对话框、返回不自动启用、暂不后的录制与试运行、前后台 gate、任务卡片截图及大字体/横屏滚动。保留原历史归因四项边界回归，不能借换调度来源删除历史保护断言。

## Task 9：真实定时点击与旧回归迁移

**Files:** 新增 `ExactTimingIntegrationTest.kt`；修改 `GestureIntegrationTest.kt`、`ClickTaskIntegrationTest.kt`、`ClickTaskStatusUiTest.kt`、`ClickRecoveryProcessTest.kt`、`RecoveryTargetActivity.kt` 及现有过程恢复夹具。

- [x] 调整验收方式：未单独运行旧 Worker 的整分钟正常启动 RED；已由旧 Worker 零点击回归和生产入口下一分钟真实闹钟/按钮测试覆盖替换。新测试通过 ClickTaskController.save/enable、真实 AlarmManager 接收事件和实际按钮检查，保留至少提前五秒的保存余量。
- [x] 正常测试需要完整记录以下断言；真实按钮监听器记录 receivedWall 并累加原子计数，测试使用主线程安全读写：

```kotlin
assertEquals(1, clicks.get())
val trace = requireNotNull(ClickTaskStore(context).startTrace())
val due = trace.occurrence.scheduledAt
val first = requireNotNull(trace.firstDispatchAt)
assertTrue(first >= due && first - due < ExactClickPolicy.START_WINDOW_MS)
assertTrue(buttonReceivedAt.get() >= due && buttonReceivedAt.get() - due < ExactClickPolicy.START_WINDOW_MS)
val next = requireNotNull(ClickTaskStore(context).alarmState()?.next)
assertTrue(next.scheduledAt > due)
assertEquals(ClickTaskOutcome.COMPLETED.message, ClickTaskStore(context).lastExecutionResult()?.message)
```

测试再投递相同显式事件，等待两秒观测，要求计数仍为 1；trace 不得因重复事件重新开始。终结后 late/duplicate 不覆盖最近真实结果。
- [x] 过期测试通过保存合法事件后，使 dispatcher 的事件送达/服务交接延迟超过原 T+5000，再经同一接纳链路处理。断言零 gesture dispatch、零按钮点击、START_EXPIRED、closedThrough>=due、next 为未来。夹具可调用内部入口或测试注入的连接函数；生产不提供对外测试延迟开关。
- [x] 增加第一点非零间隔导致过期、停止控件 await 耗尽窗口、连接在剩余时间内恢复/未恢复、消费保存失败、首点及时后第二点晚于窗口、试运行互斥和服务销毁取消场景。真实时间等待只用于必要的五秒边界，其他边界用固定时间策略或存储测试。
- [x] 既有真实手势保护回归通过内部构造注入固定墙上时间和可推进的单调时间，直接使用新执行器，避免每条都等下一分钟；这些回归只证明保护与真实手势，不证明生产闹钟时机。真实下一分钟测试集中在 `ExactTimingIntegrationTest#quickExactTaskFiresAtNextMinute` 一条，常规 GestureIntegrationTest 不再重复该等待，生产参数/偏好不得改变时钟或窗口。
- [x] 真实权限撤销/重授分为宿主驱动的独立 instrumentation 阶段：seed 保存启用任务 → instrumentation 完成 → 宿主撤销 app-op → verify 检查暂停 → instrumentation 完成 → 宿主重新授予 → verify 检查仍需明确启用。仪器进程被系统终止不是成功断言；每阶段均须有完整结果。普通权限 UI 测试不在自身 instrumentation 中撤销会杀死自己的权限。
- [x] 迁移旧 GestureIntegrationTest 的 runWorker 辅助为有效事件/执行器夹具，保留原保护 metadata、目标应用及真实按钮验证。删除只断言 WorkInfo.SUCCEEDED 的成功标准，不删行为回归。单独保留旧 Worker 壳测试要求零点击。
- [x] 过程恢复种子改为真实精确调度，并让测试 APK 的 RecoveryTargetActivity 保存 `clicks` 和 `first_click_at`。独立 PID-kill 实验在 instrumentation 完成且正常绑定基线建立后进行；杀进程后不能手动启动应用或改权限来替代系统恢复。若冷启动超过窗口，应报告跳过而非延长五秒标准。
- [x] 实际重启不是常规快速批次的必选步骤。重启策略、持久化重建、恢复接收器调用分别有断言；如果额外运行真实重启，单独列证据和用户环境恢复结果。

## Task 10：保护运行器、最终验收与交付

**Files:** 新增本地产物 `.artifacts/autoclick-exact-timing/run_tests.py`、`report.md`、`verification.json`；修改 `autoclick/README.md`；只更新本轮设计/计划完成记录。

- [x] 工程完整验证：

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest \
  :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug \
  --no-daemon --max-workers=2
python3 .artifacts/autoclick-exact-timing/run_tests.py final \
  'com.lumostech.autoclick.ExactTimingIntegrationTest,com.lumostech.autoclick.ExactAlarmPlatformTest,com.lumostech.autoclick.ClickAlarmStoreTest,com.lumostech.autoclick.ExactTimingRecoveryTest,com.lumostech.autoclick.ExactTimingPermissionUiTest,com.lumostech.autoclick.GestureIntegrationTest,com.lumostech.autoclick.ClickTaskIntegrationTest,com.lumostech.autoclick.ClickTaskStatusUiTest,com.lumostech.autoclick.ClickTrialUiTest,com.lumostech.autoclick.AccessibilityGateUiTest'
python3 .artifacts/autoclick-exact-timing/run_tests.py permission-roundtrip \
  'com.lumostech.autoclick.ExactTimingPermissionUiTest' permissions
git diff --check
```

验收标准：JVM 零失败、构建成功、Lint 零错误且记录警告数量；设备失败必须调查并复验通过，宿主确认 fixture 已取消、数据与权限恢复/合法迁移核对成功。实际采用完整批次加受影响范围定向复验，完整计数见文末，不能把失败批次写成全绿。
- [x] 更新 README：新授权与显式启用、五秒启动/超时跳过、后续计划、旧任务升级确认、重启恢复、强行停止限制、真实按钮与业务结果区分、模拟器短测入口。移除“WorkManager 约每天触发”和“15 分钟截止”的现行说明，历史设计文档不伪改。
- [x] 按 requesting-code-review 只读检查调度身份、旧入口、持久化/取消顺序、最终 dispatch 钩子、服务生命周期、权限变化以及新状态来源。实质问题按 systematic-debugging 定位并增加有意义回归；本方案内修复可纳入当前计划，改变已确认业务规则须重新设计评审。
- [x] 按 verification-before-completion 检查最终代码与构建/设备证据对应，列明完整批次之后若有代码修改的实际复验范围。交付真实计数、启动耗时、跳过证据、失败截图、恢复核对和仍缺真机的限制，不虚构通过数。
- [x] 最终向用户汇报已实现行为、安装包位置、短时验收结果及残留限制；保留工作区，不自动提交、推送或清理无关文件。

## 自查：设计覆盖与评审检查点

| 已确认设计要求 | 计划位置 |
| --- | --- |
| 精确闹钟、一次性未来事件、没有点击降级 | 1、3、4、6 |
| T 至 T+5000 的排他上界，准备/连接共用预算 | 1、5、6、9 |
| 实际最终 dispatch 检查，首点及时后继续序列 | 5、9 |
| 缺权只限制定时，授权返回仍需明确启用 | 3、4、7、8 |
| 持久化消费/跳过、回拨和停用重启不重放 | 2、4、7、9 |
| 活跃发生次数与下一次安排独立 | 2、4、8 |
| 服务生命周期拥有长执行，短接收器交接 | 5、6 |
| 旧 Worker 不点击，旧任务需启用确认 | 6、7、8 |
| 重启/更新/时间/时区/权限恢复 | 7、9 |
| 当前状态和最近结果、没有虚构 next | 2、8 |
| 正常下一分钟与超时零点击真实验证 | 9、10 |
| 用户原数据/权限恢复及迁移验证 | 0、10 |
| 文档、构建、Lint、代码评审及最终交付 | 10 |

实施评审检查点：Task 2 后检查账本与崩溃边界；Task 6 后检查首手势最终位置与所有旧入口；Task 8 后检查授权/迁移交互；Task 10 后核对真实证据。不得跳过终结/授权回归只凭正常下一分钟成功交付。

用户确认本计划后，才进入实现阶段。执行方式沿用本会话顺序实施，不另要求用户选择开发代理或新的聊天。


## 实施与验收记录（2026-10-07）

实现与本轮模拟器验收完成，保留 `codex/autoclick-exact-timing` 工作区；未提交、推送或合并。证据目录为 `.artifacts/autoclick-exact-timing/`，完整报告为该目录下 `report.md`，机器可读汇总为 `verification.json`。

- 最终构建及检查成功：92 项 JVM 测试零失败，Debug 应用/测试 APK 构建成功，Lint 0 错误、34 警告。
- 完整设备批次 83 项：80 通过、2 个测试夹具断言失败、1 个宿主阶段用例按设计跳过。两个失败只涉及真实按钮异步回调等待及已有草稿时“继续录制”的文案。修正夹具后，受影响及追加用例共 8 项全部复验通过；之后生产代码和应用 APK 哈希与完整批次保持一致。
- 合并结果：84 个常规独立用例都有通过证据，宿主权限用例的 seed/lost/granted 三阶段各通过一次（每阶段另一个普通用例按设计跳过），共 85 个功能用例有通过记录。这不是单次全量 85 项全绿。
- 生产时钟真实下一分钟：系统送达 +18 ms，首手势 +235 ms，按钮收到 +387 ms；实际点击 1 次，重复 0 次。
- 独立 PID-kill 实验：系统约 1.779 秒恢复进程/服务；首手势 +666 ms，按钮收到 +845 ms；实际点击 1 次、重复 0 次。结束 PID 后未手动重开应用、改权限或启动 instrumentation。进程种子另有 1 项通过，不混入 85 项。
- 权限撤销前，在普通应用基线明确登记并核对真实闹钟；撤销后原闹钟消失，缺权限暂停、暂不后试运行可用，重授仍需显式启用。
- 实际按钮过期零点击、第一点等待/停止控件准备消耗原窗口、第二点超过窗口、并发接纳、消费失败、服务销毁、停止、恢复账本与历史归因均已覆盖。大字体及横屏截图已检查。
- 只读代码评审的实质问题已处理；包括恢复顺序/关闭账本、reserve 后取消清理、小幅回拨 trace、服务执行归属与独立接收器结束保护，保留对应 RED/GREEN 日志。

流程差异：并非每条最终追加用例都先独立运行 RED；编译 RED 与实际策略/DST/恢复/取消/trace 问题的失败记录已保留，并发、停止控件准备等为后续补充回归。旧 Worker 的整分钟 RED 已由零执行旧入口和真实新入口验收替代。没有把这些差异写成逐条完成原 RED 顺序。

保护验证：每次批次先恢复并逐文件核对，再启动核对正常状态或合法升级。最初旧版本按设计变为停用/待启用，不能称为升级前后逐字节未变；配置、录制、历史和消费边界保留。最新恢复后的原任务仍停用，权限/无障碍/显示设置恢复，成功后的私有备份已删除。原 checkout 无关代码与暂存文件按基线核对保留。

限制：没有真机和厂商后台策略验收，没有实际重启模拟器；BOOT/更新恢复为逻辑/持久化测试。五秒是启动截止规则，不是系统必达保证。编辑、多任务、提前待命仍不在本轮范围。
