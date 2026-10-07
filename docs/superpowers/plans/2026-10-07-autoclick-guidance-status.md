# autoClick 前置引导与任务状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the current session. Steps use checkbox syntax. 用户要求：设计与本实现计划均已明确确认，2026-10-07 按确认范围实施。不得自动提交、发布或创建新聊天。

**Goal:** 无障碍未就绪时主动引导并支持退出；主页准确展示当前任务状态、下一次计划和可操作的恢复说明。

**Architecture:** AutoClick 自己管理前台前置弹框，纯状态机管理短暂连接等待；任务状态采用纯计算器，输入来自已保存任务、匹配的 WorkManager 工作与执行会话。结果原因使用明确标识独立保存；Compose 负责展示和显式用户操作。共享 accessibilityCore 不改动。

**Tech Stack:** Kotlin/JVM 11、Compose Material3、Lifecycle、WorkManager 2.11.0、SharedPreferences、JUnit4、AndroidJUnit4/Compose UI tests。SDK 36，minSdk 24。

---

## 已确认范围与实施前提

- 设计依据：`docs/superpowers/specs/2026-10-07-autoclick-guidance-status-design.md`，2026-10-07 用户明确确认。
- 2026-10-07 用户已确认本实现计划；实施遵守以上先设计评审、再计划确认的流程。
- 实施使用当前工作区及现有未提交改动作为基线；保存原有改动清单，不重置或覆盖主 app 的网络相关改动。
- 不更换 WorkManager，不添加精确闹钟，不改变 15 分钟截止值、10 秒后台重连等待、点击次数消费或手势保护。
- 任务编辑、强行停止后绕过系统限制、自动解锁/打开目标页面、自动补执行继续不在范围内。
- 每个任务先写能验证用户行为的失败测试，观察 RED，再实现、观察 GREEN；文案及静态布局不用写镜像实现的测试。
- 不自动 commit。各阶段以通过的检查和工作区差异作为检查点，最终汇总交付。

## 文件责任划分

所有路径相对于 `/Users/hgeng/AndroidStudioProjects/RemoteControl`。

| 文件 | 操作 | 责任 |
| --- | --- | --- |
| `autoclick/src/main/java/com/lumostech/autoclick/AccessibilityGateState.kt` | 新建 | 纯前置状态机、连续等待基准 |
| `autoclick/src/main/java/com/lumostech/autoclick/AccessibilityGateViewModel.kt` | 新建 | 保存状态机跨界面重建，独立于服务和 Activity 引用 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/AccessibilityGateDialog.kt` | 新建 | 前置弹框、设置/退出按钮及返回行为 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskPresentation.kt` | 新建 | 从明确输入计算当前状态和恢复说明 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStatusObserver.kt` | 新建 | 只读观察匹配的调度、任务和会话，前台时钟刷新 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickExecutionRecord.kt` | 新建 | 结果原因、关联标识及序列化 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStore.kt` | 修改 | 独立保存执行结果，兼容旧数据和现有身份保护 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickPeriodicWorker.kt` | 修改 | 现有终止分支附带原因标识，不改检查顺序和返回语义 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskController.kt` | 修改 | 保存调度失败原因作为展示信息，不修改 enqueue/对齐策略 |
| `autoclick/src/main/java/com/lumostech/autoclick/MainActivity.kt` | 修改 | 生命周期接入、操作前再次检查、前置引导和延迟处理配置入口 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/AutoclickScreen.kt` | 修改 | 当前状态/最近结果分区及按需说明 |
| `autoclick/README.md` | 修改 | 前置引导、退出含义和状态解释 |
| 对应 `autoclick/src/test/`、`src/androidTest/` 文件 | 新建/修改 | 纯逻辑及真实界面、保存、点击回归 |
| `.artifacts/autoclick-guidance-status/run_tests.py`、`report.md` | 新建 | 基于现有验证脚本保护原数据并记录本轮证据 |

不修改主 `app/`、共享权限弹框、依赖版本或生产 Manifest。

## Task 1：无障碍前置状态机

**Files:**
- Create: `autoclick/src/main/java/com/lumostech/autoclick/AccessibilityGateState.kt`
- Create: `autoclick/src/main/java/com/lumostech/autoclick/AccessibilityGateViewModel.kt`
- Test: `autoclick/src/test/java/com/lumostech/autoclick/AccessibilityGateStateTest.kt`

- [x] 写失败测试：未知初始状态阻止主页；权限未开启；连接成功；同一次等待 9 秒仍等待、10 秒进入恢复提示；反复刷新不重置时间；已连接后掉线重新等待；主动去设置后新窗口；界面重建复用同一状态机。

核心测试样例：

```kotlin
@Test fun repeatedRefreshDoesNotExtendConnectionWait() {
    val gate = AccessibilityGateState(10_000)
    assertEquals(AccessibilityGatePhase.CHECKING, gate.phase)
    assertEquals(AccessibilityGatePhase.CONNECTING,
        gate.update(ClickServiceReadiness.CONNECTING, 1_000))
    assertEquals(AccessibilityGatePhase.CONNECTING,
        gate.update(ClickServiceReadiness.CONNECTING, 10_999))
    assertEquals(AccessibilityGatePhase.RECONNECT,
        gate.update(ClickServiceReadiness.CONNECTING, 11_000))
}
```

- [x] 运行 RED：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :autoclick:testDebugUnitTest --tests 'com.lumostech.autoclick.AccessibilityGateStateTest' --console=plain
```

预期为新类型缺失导致失败，不把环境错误算作 RED。

- [x] 按如下逻辑实现纯状态机；所有时间使用单调时钟，生产以 `SystemClock.elapsedRealtime()` 注入。

```kotlin
enum class AccessibilityGatePhase { CHECKING, ENABLE, CONNECTING, RECONNECT, READY }

class AccessibilityGateState(private val maxWaitMs: Long = 10_000) {
    var phase = AccessibilityGatePhase.CHECKING
        private set
    private var startedAt: Long? = null

    fun resetForSettings() {
        startedAt = null
        phase = AccessibilityGatePhase.CHECKING
    }

    fun update(readiness: ClickServiceReadiness, elapsed: Long): AccessibilityGatePhase {
        phase = when (readiness) {
            ClickServiceReadiness.DISABLED -> {
                startedAt = null
                AccessibilityGatePhase.ENABLE
            }
            ClickServiceReadiness.CONNECTED -> {
                startedAt = null
                AccessibilityGatePhase.READY
            }
            ClickServiceReadiness.CONNECTING -> {
                val start = startedAt ?: elapsed.also { startedAt = it }
                if (elapsed - start >= maxWaitMs) AccessibilityGatePhase.RECONNECT
                else AccessibilityGatePhase.CONNECTING
            }
        }
        return phase
    }
}
```

- [x] 新建 `AccessibilityGateViewModel : ViewModel`，仅持有 `val gate = AccessibilityGateState()`。通过 `ViewModelProvider` 获取，旋转时保留基准，不持有 Activity/服务/弹框引用。没有额外后台循环。
- [x] 重跑以上命令 GREEN。ViewModel 保留及后台行为交由 Task 2 的界面测试验证。

## Task 2：接入前台检查与强制引导

**Files:**
- Create: `autoclick/src/main/java/com/lumostech/autoclick/ui/AccessibilityGateDialog.kt`
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/MainActivity.kt`
- Test: `autoclick/src/androidTest/java/com/lumostech/autoclick/AccessibilityGateUiTest.kt`
- Modify: `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTrialUiTest.kt`

- [x] 使用 `createEmptyComposeRule()`，在测试方法中准备系统权限/服务后再 `ActivityScenario.launch(MainActivity::class.java)`。现有 `ClickTrialUiTest` 也改为此顺序，避免自动启动 Activity 早于其 `@Before` 权限准备。普通退出测试不要事后调用已销毁 Activity。
- [x] 写 RED 界面用例：未开启时自动显示前置弹框、找不到可操作主页；去设置的目标正确；退出按钮及返回后 `scenario.state == Lifecycle.State.DESTROYED`；弹框外点击不进入主页；恢复连接后显示主页且引导消失；截图保留。

关键断言：

```kotlin
compose.onNodeWithText("请先开启 AutoClick 无障碍服务").assertIsDisplayed()
compose.onNodeWithText("开始录制").assertDoesNotExist()
compose.onNodeWithText("退出应用").performClick()
compose.waitUntil(3_000) { scenario.state == Lifecycle.State.DESTROYED }
```

- [x] 编译测试 APK，运行 `AccessibilityGateUiTest` 观察行为 RED。Android 测试必须通过 Task 6 的快照/恢复脚本，不能直接破坏原任务或权限。
- [x] 实现 AutoClick 专用 Compose 弹框。单一显示入口，默认主页不渲染；只有 READY 时渲染 `AutoclickScreen`。CHECKING/CONNECTING 显示检查/等待且允许退出；ENABLE/RECONNECT 的确认操作进入系统设置。返回及退出统一 `finish()`，不调用 `exitProcess`、force-stop、删除或紧急停用。

弹框关键配置：

```kotlin
AlertDialog(
    onDismissRequest = onExit,
    properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false
    ),
    title = { Text(title) },
    text = { Text(description) },
    confirmButton = {
        if (settingsLabel != null) TextButton(onClick = onSettings) { Text(settingsLabel) }
    },
    dismissButton = { TextButton(onClick = onExit) { Text("退出应用") } }
)
```

其中 `title`、`description`、`settingsLabel` 在 `AccessibilityGateDialog(phase, onSettings, onExit)` 内按五种 phase 明确选择；CHECKING 与 CONNECTING 没有设置按钮，ENABLE 为“去开启”，RECONNECT 为“去设置”。等待提示不称为权限未开启。

- [x] MainActivity 在 `onResume` 立即调用 `refreshGate()`，在 `repeatOnLifecycle(RESUMED)` 内每 500 ms 再检查。每次读取沿用 `ClickServiceConnection.readiness(this)`，同时校验系统授权及真实引用；旧的 STARTED 轮询替换，不能运行两个循环。

接入方法：

```kotlin
private fun refreshGate() {
    gatePhase = gateViewModel.gate.update(
        ClickServiceConnection.readiness(this), SystemClock.elapsedRealtime())
}

private fun requireReady(): Boolean {
    refreshGate()
    return gatePhase == AccessibilityGatePhase.READY
}

private fun openAccessibilitySettings() {
    gateViewModel.gate.resetForSettings()
    gatePhase = AccessibilityGatePhase.CHECKING
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
```

`gatePhase` 初始 CHECKING；`gateViewModel` 由 Task 1 的 ViewModel 提供。进入设置失败时保留引导并使用已有日志/错误提示，不显示已授权。观察生命周期退出时停止刷新，隐藏弹框；旋转仍复用 ViewModel。服务已连接后再次掉线恢复 gate，原独立停止悬浮窗不被主页 gate 接管。

- [x] 录制、清空重录、试运行确认、启用任务及长按配置入口都先调用 `requireReady()`。检查失败只进入引导，不执行原操作，也不自动排队这些用户操作。`CONFIGURE_CLICKS` Intent 在未 READY 时保留，READY 后才消费并打开配置；普通权限恢复不开始录制/试运行。
- [x] AutoClick 操作路径移除对共享 `showAccessibilityDialog()` 的依赖，防止第二个弹框绕过 gate；不修改共享基类或主 app 行为。
- [x] 跑 GREEN 界面测试，再跑现有试运行确认用例；验证退出前后的录制/任务完全相同、没有发生手势、停止控制仍可独立使用。

## Task 3：结构化最近结果与恢复说明

**Files:**
- Create: `autoclick/src/main/java/com/lumostech/autoclick/ClickExecutionRecord.kt`
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStore.kt`
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/ClickPeriodicWorker.kt`
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskController.kt`
- Modify: `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTaskIntegrationTest.kt`

- [x] 写 RED 保存回归：新实例恢复结果及原因；同一 schedule 停用/启用保留历史结果；替换 schedule 清除旧结果；旧数据没有结构化记录时保留原文字；旧任务/重复未持有消费权的 Worker 不能覆盖结果；紧急停止不能被旧完成结果覆盖；元数据损坏时回退原文。
- [x] 新建数据类型，并在 `ClickTaskStore` 中将它保存为独立 JSON key `execution_record`，不挤占 `task`、`consumed_at` 或旧 `outcome`。属性约定：

```kotlin
enum class ClickOutcomeReason {
    UNKNOWN, COMPLETED, ACCESSIBILITY_DISABLED, CONNECTION_TIMEOUT,
    NEEDS_RECORDING, SCREEN_LOCKED, APP_CHANGED, DISPLAY_CHANGED,
    TARGET_UNAVAILABLE, TIME_CHANGED, START_EXPIRED,
    DISPATCH_FAILED, CANCELLED, SCHEDULING_FAILED
}

data class ClickExecutionRecord(
    val taskId: String,
    val scheduleId: String,
    val occurrenceAt: Long?,
    val recordedAt: Long,
    val reason: ClickOutcomeReason,
    val message: String
)
```

序列化包含全部字段；读取使用 `runCatching`，未知 reason 名称回退 UNKNOWN，JSON 损坏返回 null。`lastExecutionRecord()` 只返回 scheduleId 与当前任务一致的记录。旧原文通过现有 `lastOutcome()/lastOutcomeTime()` 展示，不按字符串关键词反推原因。

- [x] 给 `recordOutcome`、`recordOccurrenceOutcome` 增加默认参数 `reason: ClickOutcomeReason = UNKNOWN`。把旧文本、时间及新 JSON 写入同一次 editor.commit；仍在现有锁内先执行原身份/enabled/consumed 权限判断。独立结果写入不增加第二次身份检查间隙，也不更改消费权。
- [x] `save` 在替换 scheduleId 时移除 `execution_record`，同 schedule 保留；控制类“启用/停用”仍更新旧操作状态但不伪装为一次执行成功。给 `save(task, message, reason: ClickOutcomeReason? = null)` 增加可选原因；默认 null 保留上述行为。Controller 的已有调度失败 catch 使用 `save(disabledTask, 原失败文字, SCHEDULING_FAILED)`，在同一次 commit 保存失败原因，不能在停用后调用会因 enabled 检查失败的 `recordOutcome`。展示数据不能导致重新 enqueue 或补执行。
- [x] Worker 的 `finish` 增加默认 `reason` 参数并传给原 store 方法，所有返回值及调用顺序保留。映射如下：

| 原有检查/结果 | reason |
| --- | --- |
| 无 protection | NEEDS_RECORDING |
| 权限未开启、服务等待超时 | ACCESSIBILITY_DISABLED、CONNECTION_TIMEOUT |
| 迟到窗口已过 | START_EXPIRED |
| 日期/时区/时钟保护失败 | TIME_CHANGED |
| 已完成、手势失败、已消费后的取消 | COMPLETED、DISPATCH_FAILED、CANCELLED |
| protection 的 SCREEN_LOCKED、APP_CHANGED、DISPLAY_CHANGED | 同名 reason |
| protection 的 INVALID_RECORDING | NEEDS_RECORDING |
| protection 的 ENVIRONMENT_UNAVAILABLE、UNSUPPORTED_DISPLAY、WINDOW_MISMATCH | TARGET_UNAVAILABLE |
| gate/停止按钮/未知异常 | UNKNOWN，保留原文 |

保护原因使用现有 `ClickProtectionFailure` 枚举直接映射，不分析其 message。必要时将 Worker 已有的 protection 检查结果放到局部变量，保持原检查顺序且只计算一次；不能为了展示增加第二套执行判断。当前 `nextPoint` 是已提交进度，不能用它新造“已成功点击数”。

- [x] 使用 Task 6 的保护脚本运行 `ClickTaskIntegrationTest` GREEN，同时保留现有消费、取消、替换和恢复回归。

## Task 4：纯任务状态计算与前台数据观察

**Files:**
- Create: `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskPresentation.kt`
- Create: `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStatusObserver.kt`
- Test: `autoclick/src/test/java/com/lumostech/autoclick/ClickTaskPresentationTest.kt`

- [x] 建立纯输入/输出类型，使 JVM 测试不依赖 Android WorkInfo：

```kotlin
enum class ScheduledWorkState { LOADING, MISSING, ENQUEUED, RUNNING, UNKNOWN }
data class ScheduledWorkSnapshot(
    val taskId: String,
    val state: ScheduledWorkState,
    val plannedAt: Long? = null
)
enum class RecoveryHelp { NONE, EXECUTION_PREPARATION, RECORD_AGAIN, RESAVE_TIME_ZONE, REENABLE }
data class ClickTaskPresentation(
    val title: String,
    val detail: String = "",
    val nextAt: Long? = null,
    val recoveryHelp: RecoveryHelp = RecoveryHelp.NONE
)
```

计算器入口为下面的顶层函数，task/work/lastRecord 可空；时间显式注入。`consumed` 对应 snapshot 所关联 occurrence；不拿其他任务的 watermark 判断。

```kotlin
fun present(
    task: ClickTask?,
    work: ScheduledWorkSnapshot?,
    runState: ClickRunState,
    consumed: Boolean,
    lastRecord: ClickExecutionRecord?,
    now: Long,
    currentTimeZoneId: String
): ClickTaskPresentation
```

此处为待实现函数签名；下方决策顺序定义其完整行为。读取中用 LOADING snapshot，无匹配周期工作用 MISSING。历史 lastRecord 只有 scheduleId 匹配且 occurrenceAt 指向本次时，才能说明本次已经结束；昨天或旧任务的结果不能判定今天已结束。

- [x] 写 RED 测试，使用明确日期（Asia/Shanghai）覆盖：今天/明天/下个指定星期；星期五到星期一；跨日；禁用；时区不同；当前定时准备/执行；手动试运行不冒充定时；本次已消费但未完成；延后 30 秒、1 分钟及截止边界；过期；未知调度；被替换的工作；保存时已过当天时间但实际排在明天。

关键测试样例（其他用例复用明确 epoch 与有效 task fixture）：

```kotlin
@Test fun pastClockTimeDoesNotMislabelTomorrowQueueAsDelayed() {
    val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
        set(2026, Calendar.OCTOBER, 7, 9, 1, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dueTomorrow = now + 24 * 60 * 60_000L - 60_000L
    val task = ClickTask("status-test", 9, 0, (1..7).toSet(),
        listOf(ClickCounterPoint(300f, 300f, 0)), timeZoneId = "Asia/Shanghai")
    val result = present(task,
        ScheduledWorkSnapshot(task.id, ScheduledWorkState.ENQUEUED, dueTomorrow),
        ClickRunState(), false, null, now, "Asia/Shanghai")
    assertEquals(dueTomorrow, result.nextAt)
    assertEquals("下次：明天（周四）09:00", result.title)
}
```

- [x] 运行 RED 后实现下列决策顺序：

```text
无任务 → 创建任务提示
已停用 → 任务已停用
时区变化/录制缺失 → 明确配置问题及说明入口
定时会话 active && !manual → 已有执行进度
匹配的周期工作 RUNNING → 执行准备中，不使用 Long.MAX_VALUE 推导日期
未取得工作数据 → 正在读取任务状态
无匹配工作/状态未知 → 暂无法确认下一次计划
ENQUEUED、计划在未来 → 展示该有效计划时间
ENQUEUED、已消费或当次已终止 → 当前记录 + 下个允许日期的计划
ENQUEUED、计划已到、同一日且未超过现有截止 → 尚未开始 + 延后时长 + 截止时间
已超过现有窗口/跨日的旧计划 → 本次已超时 + 下次计划
```

延后不足一分钟显示“尚未开始 · 已到计划时间”，避免显示“延后 0 分钟”。之后用已过整分钟。截止时间从 `ClickSchedulePolicy.MAX_LATENESS_MS` 计算，不在 UI 新写一个 15。日期相对当天按保存时区比较年月日，跨周/年不能只比较 DAY_OF_YEAR。

- [x] 只读 WorkManager adapter 使用当前已缓存 2.11.0 API `getWorkInfosForUniqueWorkFlow`、`WorkInfo.nextScheduleTimeMillis`。过滤：task.id 和 TAG 均匹配、`periodicityInfo != null`、非 finished；零个为 MISSING，多于一个为 UNKNOWN。

```kotlin
val matches = infos.filter {
    !it.state.isFinished && it.periodicityInfo != null &&
        task.id in it.tags && ClickPeriodicWorker.TAG in it.tags
}
val info = matches.singleOrNull()
```

ENQUEUED 的 nextScheduleTimeMillis 仅是计划/最早可启动时间，可能在过去；RUNNING/BLOCKED 等默认 Long.MAX_VALUE，不可格式化为日期。将有效 ENQUEUED hint 所在日期还原为 task.hour/minute 的计划，验证星期/时区与任务一致；不一致或无限值时展示 UNKNOWN。这兼容初始入队几毫秒偏差，同时不能把任意每日周期误当成选中的星期。不更改 WorkManager 或持久化新的执行时间。

- [x] Observer 定义 `ClickTaskStatusObserver(context: Context)`，内部只保存 applicationContext，入口为 `suspend fun observe(onUpdate: (ClickTaskPresentation, ClickExecutionRecord?) -> Unit)`。Main 在 RESUMED 范围调用并由生命周期取消。收集 WorkManager Flow、现有 prefs 变更与 `ClickExecutionSession.state`；每秒刷新墙上时间，并读取当前时区。prefs listener 用有 `awaitClose` 注销逻辑的 callbackFlow，后台同时取消工作订阅及 tick；onResume 立即生成最新快照。只读显示，禁止通过观察触发 `save`、`enqueue`、`claimExecution` 或手势。
- [x] 跑 JVM GREEN；适配器在 Task 6 添加设备测试，以真实下一分钟 WorkInfo 证明保存已过点不被误报。

## Task 5：接入卡片与恢复说明

**Files:**
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/MainActivity.kt`
- Modify: `autoclick/src/main/java/com/lumostech/autoclick/ui/AutoclickScreen.kt`
- Create: `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTaskStatusUiTest.kt`
- Modify: `autoclick/README.md`

- [x] 写 RED 界面场景：当前状态与最近结果同时存在；历史服务超时在恢复后保留；展开准备/重录说明不改变 task/draft；已停用不显示等待执行；试运行结果独立；返回前台卡片刷新。记录界面截图。
- [x] 给 `AutoclickScreen` 增加 `presentation: ClickTaskPresentation`、独立 `lastRecord`/兼容原文输入及 `onRecoveryHelp`。在原时间、星期、点击数量下方插入当前状态，最近结果在其下方；旧操作按钮和 5 秒试运行确认继续保留。

展示核心：

```kotlin
Text("当前状态", style = MaterialTheme.typography.labelMedium)
Text(presentation.title, style = MaterialTheme.typography.bodyMedium)
if (presentation.detail.isNotBlank()) {
    Text(presentation.detail, style = MaterialTheme.typography.bodySmall)
}
HorizontalDivider()
Text("最近结果", style = MaterialTheme.typography.labelMedium)
Text(lastResultText, style = MaterialTheme.typography.bodyMedium)
```

`lastResultText` 来自 Task 3 的结构化记录或原文字，附实际时间；没有记录显示“尚无执行结果”。当前读取状态不覆盖结果。

- [x] 恢复入口仅展开明确说明：EXECUTION_PREPARATION 为亮屏解锁并打开目标页面；RECORD_AGAIN 提示现有录制入口与旧配置不能继续安全使用；RESAVE_TIME_ZONE 提示重新保存时间；REENABLE 提示现有启用按钮。NONE 不显示额外入口。展开说明不能自动执行对应操作、清空录制或改变权限。
- [x] 在 MainActivity 用 Task 4 Observer 生成 presentation，只在 gate READY 时显示主页；点击操作仍按 Task 2 的实时 gate 再检查。时区说明不提供新的任务编辑功能。
- [x] 更新 README 描述前置退出是关闭界面、任务保留，下一次时间是计划时间，延后文案只是现状提示。明确准点触发及 15 分钟合理性仍待后续评审。
- [x] 运行 GREEN 界面测试与现有真实试运行确认回归。

## Task 6：完整快速验收与交付

**Files:**
- Create: `.artifacts/autoclick-guidance-status/run_tests.py`
- Create: `.artifacts/autoclick-guidance-status/report.md`
- Modify: `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTaskIntegrationTest.kt`
- Modify: `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTrialUiTest.kt`

- [x] 基于 `.artifacts/autoclick-background-recovery/run_tests.py` 复制独立本轮脚本，保留 app 文件/原任务/两项无障碍设置的备份、finally 恢复、恢复后字节及保存任务比对。新增本轮截图名称。失败也恢复；恢复未核实前不得删除备份。
- [x] 脚本固定现有 `emulator-5554`，先确认在线；不冷启动其他模拟器、不抹除当前数据。退出测试只 finish Activity，不用 PID kill/force-stop 代替点击退出；宿主备份恢复阶段的 force-stop 不作为退出行为证据。
- [x] 连接超时界面测试真实等待约 10 秒一次；其他等待基准、跨日、星期及时间边界用注入时钟的 JVM 测试，不等待几小时。正常定时复用下一分钟用例，只需一次，不伪造生产时钟或生产调度。
- [x] 构建并运行受影响 JVM 与 lint：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :accessibilityCore:testDebugUnitTest :autoclick:testDebugUnitTest :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug --console=plain
```

预期 BUILD SUCCESSFUL；测试零失败；lint 零错误，记录本轮警告数量。出现失败时先诊断，不扩大到无关主 app。

- [x] 在保护脚本下依次执行前置 UI、状态 UI、持久化、手势与试运行回归：

```sh
python3 .artifacts/autoclick-guidance-status/run_tests.py acceptance 'com.lumostech.autoclick.AccessibilityGateUiTest,com.lumostech.autoclick.ClickTaskStatusUiTest,com.lumostech.autoclick.ClickTaskIntegrationTest,com.lumostech.autoclick.GestureIntegrationTest,com.lumostech.autoclick.ClickTrialUiTest'
```

该脚本沿用 `quickSchedule=true`，因此 GestureIntegrationTest 包含一次正常下一分钟检查。预期 instrumentation `OK (...)`，原文件、任务、权限恢复全部通过。记录实际时间与次数，不事先声称固定总测试数。

- [x] 在持久化测试中增加 WorkInfo 验证：保存“当天已过点”任务实际排队在未来；RUNNING 的 nextScheduleTimeMillis 无限值不格式化；调度替换后只读取新任务。必要的强停/重启数据 fixture 使用宿主备份恢复，不能拿它代替真实权限操作或触发准时性证明。
- [x] 查看截图：前置开启、连接等待、连接超时、正常下一次计划、当前状态/最近结果、说明展开。检查没有重复弹框、裁切、被遮挡的退出按钮或恢复说明。
- [x] 对 Worker/store 改动复核原检查顺序与身份保护，确认没有新增重试/补执行；结合已有消费/停止/锁屏/应用切换测试验证。实施后使用 requesting-code-review 技能做只读复核，若需要代理只能按该技能的明确授权执行，不派发功能开发子任务。
- [x] `git diff --check`，记录本轮文件清单；保留用户原有主 app 改动。写 report 并更新计划完成项，汇总构建、真实用例、截图、恢复核对与未验证真机限制。

## 设计覆盖自审

| 设计要求 | 计划任务 |
| --- | --- |
| 前台自动检查、未开启弹框、暂时等待、超时恢复 | 1、2 |
| 退出/返回关闭界面，不能点击外部绕过，保留数据 | 2、6 |
| 前后台与重建生命周期、不重复/重置等待 | 1、2、6 |
| 当前状态、下一次计划、历史结果分开 | 3、4、5 |
| 当天/明天/星期/跨日、实际排队、时区变化、未知状态 | 4、6 |
| 新结果明确原因、旧结果兼容、旧任务不能覆盖新任务 | 3、6 |
| 说明按需展开、无自动清空/启动/补执行 | 2、5、6 |
| 快速模拟器、数据恢复、原执行保护回归 | 6 |
| 准点触发后续处理、当前时限不作为新业务承诺 | 范围条款、4、5 |

## 执行确认

本计划提出在当前对话内按任务顺序实施、验证，不创建新聊天，不自动提交或发布。用户确认本实现计划后才开始 Task 1。设计确认不等于计划确认。本文件中的代码是实施约定与测试样例，尚未写入生产源文件或测试源文件，未声称编译/验收通过。

计划自审：已对照上表逐项覆盖设计；无占位项。已明确使用缓存 2.11.0 中存在的 Flow/nextScheduleTimeMillis API，特别排除 RUNNING 的无限时间值；补充 save 在已停用调度失败时的原因保存路径，避免绕过 Worker 的 enabled 保护。所有新类型及入口在各任务中有定义，正式语法/构建验证由获批后的 RED/GREEN 步骤完成。

## 实施与验收记录（2026-10-07）

40 项步骤完成。设计与计划均经用户明确确认后实施；保留当前工作区及既有未提交改动，没有自动 commit/发布。

- 本地 78 项（autoClick 62、accessibilityCore 16）通过，安装包构建及 lint 通过（0 错误、28 警告）。
- 现有模拟器完整 52 项通过；最后一处纯展示归因修正后，补充 4 项本地回归和卡片定向复验均通过。
- 正常下一分钟触发实际等待约 58 秒，点击 1 次、重复点击 0 次。未改生产时钟、调度或截止值。
- 应用文件、原任务及无障碍设置全部恢复核对；私有备份已移除；主 app 的 76 个非构建文件与本轮基线一致。
- 只读评审发现并修正三处边界：设置导航不消耗返回连接窗口；claim 前保留 legacy 结果并区分进行状态；未知/计划前/未来时间记录不能推断当次结束。消费与执行规则保持原样。
- 恢复说明采用卡片本地展开状态，不需要 Activity 操作回调；没有执行额外操作。

证据与截图：[验收记录](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-guidance-status/report.md)。准点可靠性及 15 分钟业务依据仍待后续设计评审，真机按用户要求跳过。
