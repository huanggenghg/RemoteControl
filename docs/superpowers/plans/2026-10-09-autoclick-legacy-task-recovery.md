# AutoClick 旧版任务恢复体验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan inline, in task order, only after explicit user approval of this plan. Steps use checkbox syntax. Do not dispatch development subagents, automatically commit, push, publish, or create another checkout.

**Goal:** 解释旧任务按钮置灰原因，提供可继续的重新录制流程，并在新停用任务可靠保存前完整保留原任务。

**Architecture:** 复用任务卡片、时间编辑弹框和悬浮录制界面，以一个持久化恢复会话关联原任务和新的录制草稿。恢复保存通过现有控制器串行边界与执行保留权校验，再使用恢复专用原子快照完成切换；既有任务偏好同步失败时从该快照读取，后续读写也经同一门面。旧任务时间编辑仅保存停用配置，恢复成功同样保持停用。

**Tech Stack:** Kotlin、Android API 24–36、Compose、SharedPreferences、AtomicFile、协程/ViewModel、JUnit4、AndroidJUnit4；本机已核对的 JetBrains JBR 21.0.11，无新增依赖。

---

## 状态、基线与范围

2026-10-09：用户确认[设计](../specs/2026-10-09-autoclick-legacy-task-recovery-design.md)。用户已再次明确确认本实现计划，现已在当前目录顺序实施完成。最终构建、110 项本地测试、109 项现有模拟器用例及三阶段真实进程恢复均通过；原数据、设置和安装包恢复核对通过。证据见 `.artifacts/autoclick-legacy-recovery/report.md`，下文复选项按实际证据更新。

2026-10-10 后续整合授权：用户明确要求“变动记得合并回main”。本次功能提交包含已验收的代码、测试、文档与必要复验运行器，纳入本地 `main`；原有其他修改及暂存内容不纳入本次提交，不推送或发布。下文不自动提交的限制对应原实现/验收阶段，此次整合按用户追加请求进行。

- `ROOT=/Users/hgeng/AndroidStudioProjects/RemoteControl`，当前 `main`，HEAD 为 `1fcd837310eb1af0f338c1b77a8254c0ebe488d2`。在当前聊天和当前目录顺序实施；计划确认前只写本设计和计划。
- 当前另有暂存的 `.artifacts/ec1eaca1-9d82-4ff8-8b91-1cc1ffc8329e/` 文档、`.idea/misc.xml` 和主 app 的 `Logger.kt` 修改。不得 reset、stash、覆盖或自动纳入提交。
- 修改只限本计划列出的 AutoClick、共享录制接口、对应测试和文档。主 app、通信 SDK、图标、依赖、精确首次启动五秒窗口不纳入改造。
- 每个行为先观察实际失败再实现，失败与复验分别记录。不写只复述实现的测试；初期 API 编译缺口不称作行为验证。
- 不自动提交或推送。各 Task 结束只运行聚焦检查并记录差异；最终用户另行授权时才处理 Git 提交。
- 以下所有路径相对上述完整 ROOT，命令均在 ROOT 执行。证据目录为 `.artifacts/autoclick-legacy-recovery/`，设备私有备份不提交。

构建环境与本地验收命令：

```sh
export JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug --console=plain --no-daemon --max-workers=2
```

预期：构建成功，本次及既有相关本地测试无失败，Lint 无错误；既有警告与新增警告分别记录。此命令只在实施和最终验收阶段执行。

## 文件与职责

| 路径 | 操作和职责 |
| --- | --- |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskActions.kt` | 新增：统一按钮可用性与具体阻止原因，紧急停止不经过普通修改限制 |
| `autoclick/src/main/java/com/lumostech/autoclick/LegacyTaskRecoverySession.kt` | 新增：持久化恢复身份、阶段、时间/星期草稿和新任务 ID |
| `autoclick/src/main/java/com/lumostech/autoclick/LegacyTaskRecoveryStore.kt` | 新增：原子保存/读取恢复会话，不保存录制坐标副本 |
| `autoclick/src/main/java/com/lumostech/autoclick/LegacyTaskRecoveryViewModel.kt` | 新增：开始、暂停、继续、保存与单次结果，工作不持有 Activity 或 View |
| `autoclick/src/main/java/com/lumostech/autoclick/TaskPreferenceSnapshot.kt` | 新增：偏好完整快照的版本化、类型保真编码及校验 |
| `autoclick/src/main/java/com/lumostech/autoclick/RecoveryJournalStorage.kt` | 新增：可注入的原子快照读写，检查同步和读回结果 |
| `autoclick/src/main/java/com/lumostech/autoclick/RecoverableTaskPreferences.kt` | 新增：统一 SharedPreferences 门面，完成恢复切换及偏好同步失败后的连续读写 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStore.kt` | 接入门面；复用完整任务快照构造，新增旧任务原子替换和完成会话查询 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskController.kt` | 新增恢复保存；旧任务时间编辑停用分支，保留串行锁和执行互斥 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickScheduleEditPolicy.kt` | 增加旧任务编辑结果及文案 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskPresentation.kt` | 旧任务原因独立可见，不被权限或时区状态遮蔽 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStatusObserver.kt` | 输出一致操作状态，观察任务和恢复状态变化 |
| `autoclick/src/main/java/com/lumostech/autoclick/MainActivity.kt` | 连接恢复入口、权限返回、继续草稿、设置面板和 ViewModel 状态 |
| `autoclick/src/main/java/com/lumostech/autoclick/ConfirmEventHandler.kt` | 恢复分支提交不可变快照，阻止误入普通自动启用保存 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/AutoclickScreen.kt` | 显示原因、恢复入口、恢复进度与结果 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/LegacyTaskRecoveryDialog.kt` | 新增说明、继续/重新开始选择；沿用现有主题和权限引导 |
| `autoclick/src/main/res/layout/layout_confirm.xml` | 复用现有布局，由 MainActivity/ConfirmEventHandler 设置恢复文案及保存状态；无需改 XML |
| `accessibilityCore/src/main/java/com/lumostech/accessibilitycore/ClickSequenceStore.kt` | 通用录制会话标记与同步草稿保存，不依赖 AutoClick 类型 |
| `accessibilityCore/src/main/java/com/lumostech/accessibilitycore/AccessibilityCoreService.kt` | 开始带身份的录制会话；草稿写入成功才更新可见计数，提供不可变快照 |
| `autoclick/src/test/java/com/lumostech/autoclick/ClickTaskActionsTest.kt` | 新增操作条件与原因的组合测试 |
| `autoclick/src/test/java/com/lumostech/autoclick/LegacyTaskRecoverySessionTest.kt` | 新增恢复身份、阶段和输入校验测试 |
| `autoclick/src/test/java/com/lumostech/autoclick/ClickTaskPresentationTest.kt` | 扩展旧任务状态回归，保留有效任务历史与真实下次计划断言 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/RecoverableTaskPreferencesTest.kt` | 新增真实偏好与可控原子存储的故障测试 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/LegacyTaskRecoveryIntegrationTest.kt` | 新增控制器、旧任务编辑、保存失败及陈旧事件测试 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/RecordingSessionIntegrationTest.kt` | 新增真实共享录制持久化、失败回退及重连读取测试 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/LegacyTaskRecoveryUiTest.kt` | 新增真实旧数据 UI、取消、继续、重复提交、旋转和真实试运行 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/LegacyRecoveryProcessTest.kt` | 新增受保护的进程恢复种子及读取断言 |
| `autoclick/README.md` | 更新已交付行为、原数据保护和新验收入口 |
| `.artifacts/autoclick-legacy-recovery/` | 基线、保护运行器、故障阶段日志、截图、安装包哈希和验收报告 |

## Task 1：保护基线，建立可复验的旧任务失败

- [x] 保存 HEAD、分支、原暂存差异和计划内文件字节/哈希；源文件备份保存在本次证据目录，不覆盖旧整合证据。检查记录完整后才修改产品代码。
- [x] 运行既有相关本地测试，记录基线。环境错误与功能失败分开；使用上文已核对的 JBR 21，不升级构建依赖。
- [x] 在新的设备测试文件中，用独立测试偏好创建下列旧任务。UI 测试在受保护运行器内才使用生产偏好；用 JSON 明确缺少 `protection`，不能用无效任务或空点击冒充旧任务。

```kotlin
private fun legacyTask(): ClickTask = ClickTask(
    id = "legacy-source", hour = 9, minute = 30, days = setOf(2, 4, 6),
    points = listOf(com.lumostech.accessibilitycore.ClickCounterPoint(300f, 400f, 0)),
    enabled = false, protection = null, timeZoneId = java.util.TimeZone.getDefault().id,
    scheduleId = "legacy-plan"
)
```

- [x] 先通过现有 `updateSchedule()` 入口观察“缺少保护时不能编辑”的真实失败；通过现有 UI 观察缺少新解释与恢复按钮的失败。随后保留这些用例作回归。设备准备必须先完成 Task 7 的备份保护，不能为了取得 RED 直接删除用户任务。
- [x] 为新接口建立可编译的测试支架后，再运行对应存储/会话用例观察行为失败；不要把找不到类的编译错误当作保存保护验证。

## Task 2：一致的置灰原因和恢复身份

- [x] 编写 `ClickTaskActionsTest`：旧任务可以编辑/恢复，不能启用/试运行；有效停用任务可以启用、编辑和试运行；保存、准备、执行、断连及录制中各自返回具体原因。停止按钮独立验证仍可用。
- [x] 实现以下操作契约，并让 UI 的 `enabled` 和说明文字使用同一次计算结果。`executing` 输入必须包含 `runState.active || presentation.editingBlocked`，覆盖尚未进入手势会话的定时准备。

```kotlin
enum class ClickTaskAction { EDIT_TIME, ENABLE, DISABLE, TRIAL, RECOVER, DELETE }
data class ClickTaskActionState(val enabled: Boolean, val reason: String? = null)

fun taskActionState(
    task: ClickTask?, action: ClickTaskAction, saving: Boolean,
    executing: Boolean, connected: Boolean, recordingRecovery: Boolean
): ClickTaskActionState {
    fun blocked(reason: String) = ClickTaskActionState(false, reason)
    if (task == null) return blocked("还没有定时任务")
    if (saving) return blocked("正在保存，请稍候")
    if (executing) return blocked("任务正在准备或执行，请结束后再操作")
    if (!connected) return blocked("无障碍服务未连接，请恢复后再操作")
    if (recordingRecovery && action != ClickTaskAction.RECOVER)
        return blocked("正在重新录制，请先保存或取消恢复")
    if (task.protection == null && action in setOf(ClickTaskAction.ENABLE, ClickTaskAction.TRIAL))
        return blocked("旧版任务需要重新录制，才能启用或试运行")
    if (action == ClickTaskAction.RECOVER && task.protection != null)
        return blocked("当前任务已有完整录制")
    return ClickTaskActionState(true)
}
```

- [x] 恢复会话和新任务 ID 在开始时生成一次，旋转和重试复用。定义以下数据，JSON 编码逐字段保存，解码检查 ID 非空、时间范围、星期范围和阶段；不能吞掉损坏记录并自动清空。

```kotlin
enum class LegacyRecoveryPhase { RECORDING, PAUSED, SAVING }
data class LegacyTaskRecoverySession(
    val recoveryId: String,
    val sourceTaskId: String,
    val sourceScheduleId: String,
    val replacementTaskId: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val phase: LegacyRecoveryPhase
) {
    fun isValid(): Boolean = listOf(recoveryId, sourceTaskId, sourceScheduleId, replacementTaskId)
        .all { it.isNotBlank() } && hour in 0..23 && minute in 0..59 &&
        days.isNotEmpty() && days.all { it in 1..7 }
}
enum class LegacyTaskRecoveryResult {
    SAVED_DISABLED, ALREADY_SAVED
}
```

- [x] `LegacyTaskRecoveryStore` 使用应用私有 `files/legacy-task-recovery-session.json` 和受校验的 AtomicFile 读写，所有 I/O 在后台执行。接口固定为 `load(): LegacyTaskRecoverySession?`、`save(session): Boolean`、`clear(): Boolean`；开始或阶段保存失败时原任务不变，不能继续到破坏草稿的步骤。
- [x] 运行上述聚焦本地用例，确认原因、身份和解码测试通过；记录变更清单，不提交。

## Task 3：可靠的原任务到新任务切换

- [x] 为 `RecoverableTaskPreferencesTest` 定义真实独立 SharedPreferences，以及下面的可控存储。测试覆盖新快照写入失败、偏好同步返回失败但已经改变内存、重新构造门面、清理失败、同步失败后的编辑/删除和监听通知。

```kotlin
data class TaskPreferenceSnapshot(
    val transactionId: String,
    val previous: Map<String, Any>,
    val current: Map<String, Any>
)
interface RecoveryJournalStorage {
    fun read(): TaskPreferenceSnapshot?
    fun write(snapshot: TaskPreferenceSnapshot): Boolean
    fun clear(): Boolean
}
class MemoryRecoveryJournal : RecoveryJournalStorage {
    var value: TaskPreferenceSnapshot? = null
    var failWrite = false
    var failClear = false
    override fun read() = value
    override fun write(snapshot: TaskPreferenceSnapshot): Boolean {
        if (failWrite) return false
        value = snapshot
        return true
    }
    override fun clear(): Boolean {
        if (failClear) return false
        value = null
        return true
    }
}
```

- [x] 版本化快照编码保留 String、Int、Long、Float、Boolean、StringSet 类型；集合必须复制，不保留 Editor 或可变 Map。完整保留旧偏好字段，不依靠白名单猜测旧历史。新录制快照至少包含 `task`、对应停用 `exact_alarm`、控制状态和 `recovered_session_id`；旧时间编辑不写录制完成标记。旧结果及消费字段按“同计划编辑保留、新录制新计划清除”的原有规则构造。
- [x] 实现 AtomicFile 后端，路径为应用私有 `files/legacy-task-recovery-commit.json`。加自己的串行锁；依次 `startWrite()`、写入、`stream.fd.sync()`、`finishWrite()`、重新打开读回完整编码。同步或读回不成功不能直接宣称保存成功；异常时仅对仍开放的输出流调用 `failWrite()`。Android 36 本地源码已确认 `finishWrite()` 的同步/重命名失败可能只写日志，必须有上述校验。
- [x] 实现 `RecoverableTaskPreferences(raw, journal)`，实现完整 SharedPreferences 接口。正常无记录时委托现有偏好；记录有效时所有 get、contains、all 和 Editor 都以该记录的 `current` 为来源。生产通过 `forContext(applicationContext)` 返回按文件路径共享的同一门面，测试可注入独立实例。不得仅让 `load()` 读新快照而让历史、闹钟或执行领取读旧偏好。
- [x] 恢复切换接口固定为 `commitRecovery(expectedTaskId: String, replacement: Map<String, Any>, transactionId: String): Boolean`。先检查当前任务 ID，再构造完整记录并确认落盘；此前绝不覆盖 raw 的原任务。确认后新记录成为权威来源，再同步整个 current 到 raw。同步失败仍返回新任务已完整保存，保留记录；同步确认成功才清理记录。清理失败继续读写有效记录。
- [x] 有有效记录时，普通 Editor 的每次 `commit()` 也先确认更新后的完整记录，再同步 raw；`apply()` 在门面内排队，不能先发布尚未保存的状态。监听只对有效快照变化通知，不能因 raw 的同步失败把陈旧值传播给 UI。完成后的空快照也是有效状态，删除后不得重新出现旧任务。
- [x] `ClickTaskStore(Context)` 改为使用门面；保留注入 SharedPreferences 的测试构造器。把现有 `save()` 的 JSON、结果/消费保留与删除规则提取为同一个完整 Map 构造方法，普通保存和恢复保存共同使用。新增 `replaceLegacyTask(expectedTaskId: String, task: ClickTask, recoveryId: String): Boolean`、`saveLegacySchedule(expectedTaskId: String, task: ClickTask, message: String): Boolean`、`wasRecovered(recoveryId: String, replacementTaskId: String): Boolean`。前者在 store 锁内要求有效新保护和停用；时间编辑方法要求新旧都无保护、同一稳定计划、点击相同且停用。两者通过同一门面完成切换。完成查询同时核对 `recovered_session_id` 和当前 `scheduleId == replacementTaskId`，不以可因启用而变化的 `id` 单独判断；其他新录制与删除清除完成标记，同计划修改保留标记。
- [x] 已确认写入的恢复记录具有最高读取优先级，应用启动、控制器、Receiver、执行结果与页面观察均通过 `ClickTaskStore`。如果记录不可确认，保留 raw 与记录并阻止修改/执行；再次读取确认后再恢复。不能以清理失败删除新记录，不能盲目回滚已确认的新任务。
- [x] 执行以下实际偏好测试，特别覆盖 commit 已改变内存再返回 false 的情况：

```kotlin
@Test fun failedJournalWriteDoesNotReplaceRawTask() {
    val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    val raw = context.getSharedPreferences("legacy-journal-failure-fixture", android.content.Context.MODE_PRIVATE)
    raw.edit().clear().putString("task", "{\"id\":\"old\"}").putLong("outcome_time", 123L).commit()
    val journal = MemoryRecoveryJournal().apply { failWrite = true }
    val protected = RecoverableTaskPreferences(raw, journal)
    assertFalse(protected.commitRecovery("old", mapOf("task" to "{\"id\":\"new\"}"), "recovery-1"))
    assertEquals("{\"id\":\"old\"}", protected.getString("task", null))
    assertEquals(123L, protected.getLong("outcome_time", 0))
    assertNull(journal.value)
    raw.edit().clear().commit()
}
```

- [x] 上面是底层迁移的最小字段测试；业务集成测试必须使用完整有效任务和停用闹钟，不能拿最小 JSON 替代真实保存验证。全部存储故障用例通过后才连接 UI。

## Task 4：旧任务时间编辑与停用恢复保存

- [x] 在 `ClickScheduleEditResult` 增加 `LEGACY_DISABLED("时间已修改，旧版任务仍需重新录制")`。有效任务继续走现有政策；缺保护任务走停用分支，保留原时区与 `scheduleId`、点击、消费和最近结果。无变化仍返回 `UNCHANGED`。
- [x] `updateSchedule()` 保留身份、时分、星期、服务和执行互斥校验。缺保护分支取消旧兼容调度/旧事件但不创建新事件，通过 Task 3 的完整快照切换保存新时间，更新回调 ID；失败时旧快照仍可读。不得仅删除 `require(protection != null)` 后流入启用调度分支。
- [x] 在参数、无变更和互斥检查之后，旧任务分支调用以下 helper；`saveLegacySchedule()` 同时构造对应停用闹钟及保留历史快照，原子保存失败不返回成功：

```kotlin
private suspend fun updateLegacySchedule(
    original: ClickTask, hour: Int, minute: Int, days: Set<Int>
): ClickScheduleEditResult = ClickExecutionSession.withScheduleEdit { edit ->
    val current = checkNotNull(store.load()) { "任务已删除，请重新打开时间设置" }
    check(current.id == original.id && current.protection == null) { "任务已变化，请重新打开时间设置" }
    check(store.alarmState()?.active == null) { "定时任务已开始准备，请结束后再修改" }
    edit.checkActive()
    check(canEdit()) { "无障碍服务未连接，请恢复后再修改" }
    cancelLegacy()
    store.alarmState()?.next?.let { platform.cancel(it) }
    edit.checkActive()
    val updated = current.copy(
        id = UUID.randomUUID().toString(), hour = hour, minute = minute,
        days = days.toSet(), enabled = false
    )
    check(store.saveLegacySchedule(current.id, updated, ClickScheduleEditResult.LEGACY_DISABLED.message)) {
        "时间修改未完成，原任务已保留"
    }
    ClickScheduleEditResult.LEGACY_DISABLED
}
```
- [x] 在控制器增加以下完整业务入口；对应 store 方法来自 Task 3。`recordedSessionId` 从共享录制快照取得，不能由 UI 直接填成当前会话值。

```kotlin
suspend fun saveRecoveredRecording(
    session: LegacyTaskRecoverySession,
    candidate: ClickTask,
    recordedSessionId: String?
): LegacyTaskRecoveryResult = withContext(Dispatchers.IO) {
    mutationLock.withLock {
        if (store.wasRecovered(session.recoveryId, session.replacementTaskId))
            return@withLock LegacyTaskRecoveryResult.ALREADY_SAVED
        val current = checkNotNull(store.load()) { "原任务已删除，请重新进入恢复" }
        check(current.id == session.sourceTaskId && current.scheduleId == session.sourceScheduleId) {
            "原任务已变化，请重新进入恢复"
        }
        check(current.protection == null && !current.enabled) { "任务状态已变化，请重新进入恢复" }
        require(recordedSessionId == session.recoveryId) { "当前录制不属于本次恢复，请重新确认" }
        require(candidate.id == session.replacementTaskId && candidate.scheduleId == session.replacementTaskId) {
            "恢复任务身份不匹配"
        }
        require(candidate.isValid() && candidate.protection != null) { "请完成有效的新录制" }
        require(candidate.timeZoneId == TimeZone.getDefault().id) { "时区已改变，请重新确认保存时间" }
        check(store.alarmState()?.active == null && !ClickExecutionSession.state.value.active) {
            "任务正在准备或执行，请结束后再恢复"
        }
        check(canEdit()) { "无障碍服务未连接，请恢复后再保存" }
        ClickExecutionSession.withScheduleEdit { edit ->
            val latest = checkNotNull(store.load()) { "原任务已删除，请重新进入恢复" }
            check(latest.id == session.sourceTaskId) { "原任务已变化，请重新进入恢复" }
            edit.checkActive()
            cancelLegacy()
            store.alarmState()?.next?.let { platform.cancel(it) }
            edit.checkActive()
            check(canEdit()) { "无障碍服务已断开，请恢复后再保存" }
            val disabled = candidate.copy(enabled = false)
            check(store.replaceLegacyTask(session.sourceTaskId, disabled, session.recoveryId)) {
                "新任务保存未完成，原任务已保留"
            }
            LegacyTaskRecoveryResult.SAVED_DISABLED
        }
    }
}
```

- [x] 原子完成点之后不执行会把结果当失败的取消检查，也不自动回滚完整新任务；调用方因取消丢失返回值时，从 `wasRecovered()` 和持久化任务恢复结果。完成点之前取消仍终止提交。该入口不调用普通 `save()`、`armNext()` 或 `allowScheduled()`，不写任何执行结果。
- [x] 集成测试覆盖：旧编辑停用/保留字段、无修改、原子写入失败、原身份变化、会话不匹配、无有效保护、服务掉线、正在准备、保存中停止、重复提交、旧事件拒绝及保存后无闹钟。有效任务原有编辑、消费日期与五秒调度用例继续通过。

## Task 5：独立录制草稿与可继续的恢复会话

- [x] 共享层使用通用录制身份，不能 import AutoClick 类型。新增不可变快照 `RecordedClickSnapshot(sessionId: String?, points: List<ClickCounterPoint>, protection: ClickRecordingProtection?)`，放入 `ClickSequenceStore.kt`；`points` 和包名列表复制。
- [x] `ClickSequenceStore` 增加 `loadSnapshot()`、`saveSession(snapshot): Boolean`；同一同步 commit 保存点、保护及 `session_id`。普通 `save()` 保留原兼容入口并移除会话 ID；恢复草稿写失败返回 false，不把旧草稿混入新会话。
- [x] 服务增加 `beginRecordingSession(sessionId: String): Boolean` 和 `getRecordedSnapshot()`。先可靠保存空的新会话，再更新服务录制对象和计数。`recordClick()` 在有会话 ID 时，冻结新增点和对应保护，确认同步保存后才更新计数；失败恢复此前内存快照并提示“录制未保存，请重试”。普通未标记录制沿用原行为。
- [x] 用户明确开始后，先完成服务/权限及原身份检查，再可靠保存恢复会话，最后调用 `beginRecordingSession()`。如果开始新草稿失败，暂停会话并说明原因；原任务不变。只有明确开始/重新开始时才清空，继续录制只允许同一持久化会话 ID。
- [x] `LegacyTaskRecoveryViewModel` 使用 `viewModelScope`，接口固定为 `begin(source: ClickTask)`、`pause()`、`resume(source: ClickTask)`、`save(candidate: ClickTask, recordedSessionId: String?)`，公开 `session`、`saving`、`error`、`resultMessage`。接收应用级 controller/store，不持有 Activity、权限页或 Binding。保存时先持久化时间/星期和 SAVING 阶段，失败保留草稿及会话。
- [x] 重启遇到 SAVING：先检查 Task 4 的完成记录；已完成则展示新停用任务并清理会话，未完成则恢复为 PAUSED，不自动重新提交。会话文件清理失败时不能阻止读取已保存的新任务或再次创建一个新任务。
- [x] 观察任务变化时先查录制完成标记，再判定源任务失效；保存工作存在时不能触发第二次保存。源任务确已变化时保留草稿、暂停并显示原因，不能自动把会话改绑到另一个任务。`RecordingSessionIntegrationTest` 分别验证 `startFailureDoesNotPublishSession`、`failedPointWriteRestoresSnapshot`、`reconnectLoadsSameSession` 和 `ordinaryRecordingClearsSessionMarker`；共享旧接口调用仍编译并保持普通录制行为。

## Task 6：接入页面与悬浮设置面板

- [x] `AutoclickScreen` 使用 Task 2 的操作状态，显示设计中的旧任务解释、“重新录制”或“继续恢复”入口和暂停恢复操作。`RecoveryExplanation` 对缺少 protection 的旧任务改为同一恢复入口/文案；有完整保护、因历史 DISPLAY_CHANGED 展开说明的有效任务继续走现有说明和普通录制，不称作旧版，也不改绑到旧任务恢复会话。权限、时区和缺少保护分别显示，不相互替代。
- [x] `MainActivity` 等待现有启动恢复完成后才开始新恢复；打开说明弹框，原任务身份快照用于重新检查。权限页返回只更新就绪状态，满足条件后继续用户已明确开始的会话，不自动开始任何手势。
- [x] 录制卡片的继续/清空入口也检查恢复会话：继续恢复必须读取同一个 session ID；重新开始先走明确清空说明并创建新会话身份。不能直接调用旧 `showRecording(reset)` 清掉会话标记后继续显示为同一恢复。
- [x] 移除 MainActivity 编辑入口对 `protection != null` 的 UI 限制，改用一致的操作状态；控制器仍按 Task 4 分流。编辑保存中和恢复保存中的 busy 均阻止普通操作，紧急停止独立可用。
- [x] 长按设置面板时读取不可变录制快照。恢复模式预填会话中的时间和星期，显示当前保存时区、“保存并替换旧任务”“返回录制”及停用说明。时间/星期草稿保存在会话中，不因旋转或重建面板丢失。
- [x] `ConfirmEventHandler` 恢复分支只提交候选快照到 ViewModel：

```kotlin
val snapshot = service.getRecordedSnapshot()
val session = recoveryViewModel.session
if (session != null) {
    if (snapshot.sessionId != session.recoveryId) {
        showRecordingError("当前录制不属于本次恢复，请重新确认")
        return
    }
    val candidate = ClickTask(
        id = session.replacementTaskId,
        hour = binding.timePicker.hour,
        minute = binding.timePicker.minute,
        days = binding.weekdaysPicker.selectedDays.toSet(),
        points = snapshot.points,
        enabled = false,
        protection = snapshot.protection,
        timeZoneId = TimeZone.getDefault().id,
        scheduleId = session.replacementTaskId
    )
    recoveryViewModel.save(candidate, snapshot.sessionId)
    return
}
if (snapshot.sessionId != null) {
    showRecordingError("这是恢复录制草稿，请先进入任务恢复")
    return
}
```

`showRecordingError(message: String)` 是 handler 内同步 Toast helper；这里不启动协程、不把 Binding 传给 ViewModel。普通未标记录制保留现有保存分支。面板按钮状态由当前 Activity 观察 ViewModel 更新，Activity 销毁时释放旧面板；新 Activity 读取同一保存工作和会话，不能再次提交。

- [x] 成功提示反映已确认的新任务与停用状态；普通保存的自动启用提示不能出现在恢复模式。缺定时权限不要求授权才能保存，试运行继续独立可用；用户开启定时权限后仍需明确启用。
- [x] MainActivity 和状态观察器处理存储未确认结果时保留最后已确认的展示快照，显示“保存状态未确认”并阻止任务操作；不能因读取异常崩溃或显示“还没有定时任务”。再次确认记录后刷新真实状态，不自动提交或启用。
- [x] UI 用例核对真实旧数据文案、旧时间编辑、说明取消不清空、权限拒绝、录制后暂停/继续、保存失败保留任务及草稿、重复提交、旋转、1.5 倍字体和横屏可达性。成功后实际试运行测试按钮一次并记录接收次数；随后手动启用才检查未来精确安排。

## Task 7：受保护的联合验收与交付

- [x] 在任何设备 RED/GREEN 前，复制已存在的 `.artifacts/autoclick-integration/run_tests.py` 到本次证据目录，保留原运行器。新增可选 `--serial` 参数显式选用一个现有模拟器；无参数且设备不唯一就拒绝运行，不任意选设备，也不新建模拟器。
- [x] 保留原运行器对 `shared_prefs`、`files`、`databases`、`no_backup`、无障碍配置、定时 app-op、显示/字体和原安装包的备份、恢复与核对。新会话/原子记录位于 files，必须连同 `.new`/`.bak` 一起保护。先核对私有备份可读且完整，再安装或造测试数据。
- [x] 运行聚焦新设备用例。Task 1 的红灯日志、Task 3–6 的绿色日志和最终回归分开保存，不把不同轮次合并成单次全绿。运行器中的每次 instrumentation 都检查返回码和实际测试结果。

```sh
python3 .artifacts/autoclick-legacy-recovery/run_tests.py legacy-storage 'com.lumostech.autoclick.RecoverableTaskPreferencesTest,com.lumostech.autoclick.LegacyTaskRecoveryIntegrationTest,com.lumostech.autoclick.RecordingSessionIntegrationTest'
python3 .artifacts/autoclick-legacy-recovery/run_tests.py legacy-ui 'com.lumostech.autoclick.LegacyTaskRecoveryUiTest'
python3 .artifacts/autoclick-legacy-recovery/run_tests.py legacy-regression 'com.lumostech.autoclick.ScheduleEditIntegrationTest,com.lumostech.autoclick.EditScheduleUiTest,com.lumostech.autoclick.ClickTaskStatusUiTest,com.lumostech.autoclick.ClickTrialUiTest,com.lumostech.autoclick.GestureIntegrationTest,com.lumostech.autoclick.ExactTimingRecoveryTest'
```

预期：各批次有实际 `OK` 测试结果、零失败以及恢复核对成功；测试数从输出提取，不预填数量。默认要求一个现有模拟器；设备不唯一时，先读取现有设备身份再显式传 `--serial`，不能填猜测的设备 ID。
- [x] 扩展保护运行器的进程阶段：未保存的新草稿、原子切换之前、切换完成但 UI 未收到结果分别准备种子；结束 instrumentation 后再终止真实应用进程并重新启动。每次核对任务、会话、草稿、停用状态和闹钟；重建一个 store 对象不能代替该进程验收。
- [x] 精确闹钟迟到旧事件注入只能使用测试任务：恢复成功后重复送达，断言零真实点击、没有覆盖新任务/结果。恢复后的手动试运行真实接收一次；未手动启用前零闹钟、零自动点击。手动启用后的首手势仍在原五秒窗口。
- [x] 运行最终本地构建/Lint及受影响既有设备回归；只有新增修改、失败或未解决的关注点才扩大复跑。验收各批次结束后恢复并核对原数据/权限/显示和安装包，核对成功才清除私有临时备份；失败保留备份及恢复入口。
- [x] 最终证据包含 UI 截图、恢复前后任务身份及停用状态、各故障阶段、真实点击接收、进程恢复、APK SHA-256、原数据恢复结果及测试/Lint计数。原子记录中包含用户点击数据，原始设备备份和完整记录不公开、不提交；报告只写脱敏断言。
- [x] 更新 README 和本设计/计划状态，写 `.artifacts/autoclick-legacy-recovery/report.md`。区分本地测试、模拟器真实录制/手势、进程重启和未进行的真机/厂商后台/系统重启验证。核对原暂存差异和无关文件哈希未变。
- [x] 交付已验证 APK、报告和实际结果；不自动提交、推送或发布。

## 自检映射与实施确认

| 已确认设计要求 | 对应实施和验证 |
| --- | --- |
| 原因可读，临时状态说明准确 | Task 2、6 的操作政策及 UI 用例 |
| 旧时间/星期编辑保持停用和历史 | Task 3、4 的快照、政策及存储失败用例 |
| 明确开始前不清空，草稿不混用 | Task 2、5、6 的会话身份及权限/取消用例 |
| 新任务完成保存前保留原任务 | Task 3 的完成切换及各 I/O 故障注入 |
| 成功后保持停用，不自动安排 | Task 4、6、7 的控制器和真实零点击断言 |
| 重复/旋转/进程中断一致 | Task 5、6、7 的单次工作及真实进程恢复 |
| 保护原设备数据，保留既有功能 | Task 1、7 的备份恢复及回归 |

本实现计划已获用户明确确认，并在当前聊天、当前目录顺序完成。真机、厂商后台策略和实际系统重启仍未验收；未自动提交、推送或发布。
