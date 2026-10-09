# AutoClick 精确定时、时间编辑与图标整合 Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to execute this plan inline, in task order, only after explicit user approval. Steps use checkbox syntax. Do not dispatch development subagents, automatically commit, push, publish, or create another checkout.

**Goal:** 交付同一个同时具有精确定时、时间/星期编辑和已选图标的 AutoClick 安装包，并验证交叉行为与原数据恢复。

**Architecture:** 直接在当前项目目录整合，先备份现有编辑和图标实现，再逐项纳入已验收的精确定时改动。控制器串行协调编辑与精确闹钟，执行门保护编辑/点击互斥，存储原子保存配置及对应调度状态；消费日期与跳过水位分别约束自动执行。Compose 读取精确安排，并使用明确保存结果提示。

**Tech Stack:** Kotlin、Android API 24–36、AlarmManager、SharedPreferences、协程、Compose、JUnit4、AndroidJUnit4；JBR 21.0.11。WorkManager 仅保留旧任务取消与零点击兼容入口。

---

## 状态与执行约束

2026-10-09：获批范围已完成。103项本地测试、115项设备用例及数据/权限恢复核对通过，详见[联合验收报告](../../../.artifacts/autoclick-integration/report.md)。验收阶段成果保留当前目录，未提交或推送。下文保留原实施约定，勾选表示对应要求已完成。

2026-10-08 用户已确认[整合设计](../specs/2026-10-08-autoclick-integration-design.md)。用户已同意改为当前目录整合、不新增工作区；2026-10-08 用户以“okgo”明确确认本实施计划，开始实施。代码块为实施约定，不是已写入源文件或已验证的代码。

- 原始主目录 `MAIN=/Users/hgeng/AndroidStudioProjects/RemoteControl`，当前提交 `93c7c4c`。
- 精确定时来源 `EXACT=/Users/hgeng/.codex/worktrees/autoclick-exact-timing/RemoteControl`，当前提交 `4b668aa`，功能在未提交改动中。
- `ROOT=MAIN=/Users/hgeng/AndroidStudioProjects/RemoteControl`。直接在当前目录实施，不创建 worktree、额外检出目录或新分支。下文目标文件相对 ROOT；命令均在 ROOT 执行。
- `EDIT_SOURCE` 表示 Task 1 保存在现有 `.artifacts/autoclick-integration/` 内的编辑来源备份；后续提到 MAIN 的编辑来源均读取该备份，不能从已被整合修改的目标文件反向复制。
- 本计划选择在本聊天顺序实施，不派开发代理、不另开聊天；用户已明确选择当前目录，无需再询问目录偏好。
- 只修改 MAIN 中本计划涉及的产品及文档文件；EXACT 作为只读来源。原暂存区和无关修改保持原状，不运行 reset、stash、checkout 或批量还原。用户数据和设备操作在保护运行器准备完成后进行。
- 不纳入旧任务新恢复流程、多任务、点击间隔编辑、提前待命、真机或实际重启验收。
- 安全消费记录保留同步 `commit()`；不升级 Kotlin、AGP 或无关依赖来消除环境问题。
- 每个行为先观察有意义的失败再实施/迁移，修复后复验；资源复制使用来源哈希、构建和显示检查，不写镜像测试。

统一构建前缀：

```sh
export JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest --console=plain --no-daemon --max-workers=2
```

## 文件职责

| 文件（相对 ROOT） | 操作及职责 |
| --- | --- |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickScheduleEditPolicy.kt` | 新增：编辑结果、权限/时区/停用决策与提示文字 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickSchedulePolicy.kt` | 合并每日消费日期与严格未来时间，保留精确定时的 DST 处理 |
| `autoclick/src/main/java/com/lumostech/autoclick/ExactClickPolicy.kt` | 下一次安排同时考虑事件水位与消费日期；五秒窗口不变 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStore.kt` | 同锁保存编辑配置及状态；领取、接纳均检查每日消费 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickExecutionGate.kt` | 复用当前目录的编辑保留权和停止优先 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickExecutionSession.kt` | 在精确定时版本上加入编辑保留权，保留控件就绪时限与任务身份 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskController.kt` | 编辑接入 AlarmManager；取消旧事件、保存、登记和失败清理 |
| `autoclick/src/main/java/com/lumostech/autoclick/EditScheduleViewModel.kt` | 复用并改为依据实际保存结果提示 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/EditScheduleDialog.kt` | 复用已验收的时分/星期、旋转及保存中交互 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskPresentation.kt` | 增加编辑阻止标记，保留精确调度状态来源 |
| `autoclick/src/main/java/com/lumostech/autoclick/ClickTaskStatusObserver.kt` | 根据准备/执行状态刷新编辑可用性 |
| `autoclick/src/main/java/com/lumostech/autoclick/MainActivity.kt` | 合并编辑入口与精确定时权限引导、实际结果提示 |
| `autoclick/src/main/java/com/lumostech/autoclick/ui/AutoclickScreen.kt` | 合并编辑按钮，保留定时权限入口和五秒说明 |
| `autoclick/src/main/res/drawable/ic_launcher_*.xml`、`mipmap-*` | 复制已经确认的新图标资源 |
| `autoclick/src/test/java/com/lumostech/autoclick/ExactEditPolicyTest.kt` | 新增编辑决策测试 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/ScheduleEditIntegrationTest.kt` | 新增真实存储与可控平台的交叉回归 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/EditScheduleUiTest.kt` | 迁移既有 UI 用例，真实下一分钟用例改为精确闹钟 |
| `.artifacts/autoclick-integration/` | 来源清单、保护运行器、日志、截图、哈希、报告；不自动加入提交 |

精确定时原有生产文件、Manifest、执行器及测试从 EXACT 来源纳入，不重写已验收的五秒执行链路。共享 `AccessibilityCoreService.kt` 只纳入该来源已有的最终手势检查接口。

## Task 1：备份当前改动、核对来源与保护准备

- [x] 核对当前 HEAD、分支、状态和来源；不创建新工作区。在现有 `.artifacts/autoclick-integration/` 内保存 MAIN 的相关产品文件完整字节、原状态、暂存差异和哈希，并保存 EXACT 来源清单。备份完成并逐项核对前不得改产品代码。备份不自动加入提交，不包含凭证、构建输出或设备私有备份。
- [x] 先运行当前编辑版本的本地测试并构建应用及测试包，记录真实基线。基线失败先核实原因，不当成整合回归。
- [x] 下面脚本只备份 MAIN 相关文件和记录 EXACT 来源，不覆盖目标产品文件；实施时在 ROOT 执行：

```python
from pathlib import Path
import hashlib, json, shutil, subprocess

root = Path('/Users/hgeng/AndroidStudioProjects/RemoteControl')
exact = Path('/Users/hgeng/.codex/worktrees/autoclick-exact-timing/RemoteControl')
out = root / '.artifacts/autoclick-integration'
backup = out / 'edit-source'
assert not backup.exists(), 'Existing backup must be verified and reused, never overwritten'
backup.mkdir(parents=True)
shared = 'accessibilityCore/src/main/java/com/lumostech/accessibilitycore/AccessibilityCoreService.kt'
manifest = {}
for label, checkout in [('main', root), ('exact', exact)]:
    changed = set(subprocess.check_output(['git', 'diff', '--name-only', 'HEAD'], cwd=checkout, text=True).splitlines())
    changed |= set(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=checkout, text=True).splitlines())
    selected = sorted(n for n in changed if n.startswith('autoclick/src/') or n == 'autoclick/README.md' or n == shared or n.startswith('docs/superpowers/'))
    entries = []
    for name in selected:
        src = checkout / name
        assert src.resolve().is_relative_to(checkout) and not src.is_symlink() and src.is_file(), name
        digest = hashlib.sha256(src.read_bytes()).hexdigest()
        if label == 'main':
            dst = backup / name
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
            assert hashlib.sha256(dst.read_bytes()).hexdigest() == digest
        entries.append({'path': name, 'sha256': digest})
    manifest[label] = entries
    for suffix, args in [('head', ['rev-parse', 'HEAD']), ('status', ['status', '--porcelain=v1']),
                         ('index', ['diff', '--cached', '--binary'])]:
        (out / f'{label}-{suffix}.txt').write_bytes(subprocess.check_output(['git', *args], cwd=checkout))
(out / 'source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
```

- [x] 两边都有改动的策略、存储、执行 Session、控制器、主页、状态展示和测试，按 Task 2–5 合并；不得用来源文件整批覆盖现有编辑功能。EXACT 新增的精确闹钟生产类、Manifest 和对应测试随依赖一并纳入。保留现有 ViewModel、Dialog、编辑执行门及全部 15 个图标资源，编辑原始内容始终从 EDIT_SOURCE 核对。
- [x] 当前目录已经有本次设计/计划，无需复制到另一个 ROOT。记录所有计划内写入路径；核对主 app、通信模块、依赖、无关文件和暂存差异与基线相同。
- [x] Task 2–5 构成一个有依赖的整合批次：先用交叉用例观察现有实现的缺失，再逐文件移入并接好 API。编译错误只能记录为中间状态，不能称通过；策略/存储 GREEN 在其依赖可编译后执行，整个批次到 Task 5 必须重新构建并全量通过本地测试。设备 RED/GREEN 在 Task 5 构建完成后、Task 6 联合验收前执行，避免安装中间版本。
- [x] 将 EXACT 的 `.artifacts/autoclick-exact-timing/run_tests.py` 和 `shell-ui/snapshot.jar` 复制到 ROOT 的 `.artifacts/autoclick-integration/` 对应路径。以下保护准备在所有设备测试之前完成；APK 路径相对 ROOT，输出路径取运行器所在目录。
- [x] 修正运行器对空任务和缺少目录的处理。新增以下两个完整 helper；不存在才使用空偏好，其他读取错误继续失败，不吞掉授权/设备错误：

```python
def optional_task_xml(package):
    path = 'shared_prefs/autoclick_task.xml'
    result = subprocess.run(BASE + ['shell', 'run-as', package, 'test', '-f', path], capture_output=True)
    if result.returncode == 1:
        return b'<map />'
    result.check_returncode()
    return call('exec-out', 'run-as', package, 'cat', path, capture_output=True).stdout

def snapshot_app():
    paths = []
    for path in ['shared_prefs', 'databases', 'files', 'no_backup']:
        result = subprocess.run(BASE + ['shell', 'run-as', 'com.lumostech.autoclick',
                                       'test', '-d', path], capture_output=True)
        if result.returncode == 0:
            paths.append(path)
        elif result.returncode != 1:
            result.check_returncode()
    if paths:
        return call('exec-out', 'run-as', 'com.lumostech.autoclick',
                    'tar', '-cf', '-', *paths, capture_output=True).stdout
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode='w'):
        pass
    return output.getvalue()
```

备份及恢复比较时的 tar 读取改用 `snapshot_app()`；两处备份任务 XML 改为 `next((value for key, value in original_files.items() if key.endswith('/autoclick_task.xml')), b'<map />')`，恢复启动后的读取改用 `optional_task_xml('com.lumostech.autoclick')`。没有任务时 `saved_task` 返回 None，迁移及 enabled 字段读取继续受已有 None 判断保护。
- [x] 在首次 force-stop 前先保存可恢复的应用文件和设置快照，再进入既有 try/finally 保护范围，force-stop 后重取稳定文件快照作为正式备份；写出成功后才操作授权、安装、清数据及夹具。恢复应用文件后逐文件比较，再启动核对合法迁移；保留 app-op 的 default/显式覆盖、其他无障碍服务、字体及旋转。恢复某一步失败时仍尝试其余独立设置恢复，收集全部错误；任一错误均保留私有备份并使运行失败。
- [x] 先执行 `python3 .artifacts/autoclick-integration/run_tests.py protection com.lumostech.autoclick.ClickAlarmStoreTest preflight-fail`，要求故意失败并完成 finally 恢复核对；预期非零退出、restore 文件确认恢复。该结果只算保护演练，不能计入通过用例；未通过恢复核对不得继续设备夹具。

Task 1 后具有可恢复的现有编辑/图标来源、当前版本基线和经过恢复演练的保护运行器；产品整合在 Task 2–5 进行。保护演练先使用当前已构建版本。

## Task 2：每日消费、未来安排与编辑决策

- [x] 先写 RED：从 EDIT_SOURCE 的 `ClickSchedulePolicyTest` 复用同日改晚、过去/相等时间不补执行、回拨与时区日期用例，但保留 EXACT 的五秒和 DST 用例，不带回十五分钟测试。为 `ExactClickPolicyTest` 增加以下完整边界：

```kotlin
@Test fun editKeepsConsumedDateSeparateFromSkippedEvent() {
    val zone = "Asia/Shanghai"
    fun at(day: Int, hour: Int) = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
        set(2026, java.util.Calendar.OCTOBER, day, hour, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val task = ClickTask("edit", 16, 0, (1..7).toSet(),
        listOf(com.lumostech.accessibilitycore.ClickCounterPoint(10f, 10f, 0)), timeZoneId = zone)
    assertEquals(at(9, 16), ExactClickPolicy.next(task, at(8, 11), at(8, 9), at(8, 9)))
    assertEquals(at(8, 16), ExactClickPolicy.next(task, at(8, 11), at(8, 9), null))
    assertEquals(at(9, 16), ExactClickPolicy.next(task, at(8, 16), 0, null))
    assertEquals(at(9, 16), ExactClickPolicy.next(task, at(7, 11), 0, at(8, 9)))
}
```

- [x] 运行 `:autoclick:testDebugUnitTest --tests '*ExactClickPolicyTest'`，观察新签名/行为的 RED。保留 EDIT_SOURCE 的 `isConsumedDate`，将 EXACT 的 `nextOccurrence` 增加 `consumedAt: Long? = null` 参数，候选条件保留 DST 校验，并增加 `!isConsumedDate(due.timeInMillis, consumedAt, task.timeZoneId)`。`ExactClickPolicy.next` 完整替换为：

```kotlin
fun next(task: ClickTask, now: Long, closedThrough: Long, consumedAt: Long? = null): Long =
    ClickSchedulePolicy.nextOccurrence(task, maxOf(now, closedThrough), consumedAt)
```

- [x] 新增 `ClickScheduleEditPolicy.kt`，固定结果与决策接口如下。先以缺少 API 的测试 RED 验证，再实现：

```kotlin
package com.lumostech.autoclick

enum class ClickScheduleEditResult(val message: String) {
    UNCHANGED("时间和星期未改变"),
    ENABLED("时间已修改"),
    DISABLED("时间已修改，任务仍停用"),
    PERMISSION_REQUIRED("时间已修改，定时权限未开启，任务未启用"),
    TIME_ZONE_CHANGED("时间已修改，时区已改变，任务未启用")
}

internal data class ClickScheduleEditDecision(
    val result: ClickScheduleEditResult,
    val enabled: Boolean,
    val status: ClickAlarmStatus
)

internal object ClickScheduleEditPolicy {
    fun decide(task: ClickTask, alarm: ClickAlarmState?, permission: Boolean, currentZone: String): ClickScheduleEditDecision {
        if (task.timeZoneId != currentZone || alarm?.status == ClickAlarmStatus.TIME_ZONE_CHANGED)
            return ClickScheduleEditDecision(ClickScheduleEditResult.TIME_ZONE_CHANGED, false, ClickAlarmStatus.TIME_ZONE_CHANGED)
        if (!task.enabled) {
            val retained = alarm?.status?.takeIf { it in setOf(ClickAlarmStatus.PERMISSION_REQUIRED,
                ClickAlarmStatus.NEEDS_ENABLE, ClickAlarmStatus.SCHEDULE_FAILED) } ?: ClickAlarmStatus.NEEDS_ENABLE
            return ClickScheduleEditDecision(ClickScheduleEditResult.DISABLED, false, retained)
        }
        if (!permission) return ClickScheduleEditDecision(ClickScheduleEditResult.PERMISSION_REQUIRED,
            false, ClickAlarmStatus.PERMISSION_REQUIRED)
        return ClickScheduleEditDecision(ClickScheduleEditResult.ENABLED, true, ClickAlarmStatus.ARMING)
    }
}
```

- [x] `ExactEditPolicyTest` 构造固定任务和 alarm，实际断言四种决定及标记的保留：启用+有权→ENABLED/ARMING；停用+无权→DISABLED/false 且不要求权限；启用+无权→PERMISSION_REQUIRED；保存时区不同或已有 TIME_ZONE_CHANGED→TIME_ZONE_CHANGED；停用且 SCHEDULE_FAILED/PERMISSION_REQUIRED/NEEDS_ENABLE 不清除该状态。复跑策略测试 GREEN。无修改由控制器提前返回，不能通过 decide 清除状态。

## Task 3：原子保存、领取检查与编辑互斥

- [x] 在 `ClickTaskStore` 保留 EDIT_SOURCE 的 `consumedAt(taskId)` 与每日 `wasConsumed`；保留 EXACT 的 exact_alarm、closed_at、start_trace 编解码和新计划清理。`reserveAlarm` 与 `claimAlarm` 除 `closedThrough` 判断外，都拒绝 `wasConsumed(task.id, occurrence.scheduledAt)`；legacy `claimExecution` 同样使用合并后的每日判断。Task 4 的 `armNext` 也传入 consumedAt，不能只在 UI 阻止同日重放。
- [x] `save` 新增最后一个可选参数 `alarmState: ClickAlarmState? = null`。完整新增逻辑如下，原有保存配置、历史迁移和同步 commit 保留：

```kotlin
alarmState?.let {
    require(it.isValid() && it.taskId == task.id && it.scheduleId == task.scheduleId)
}
// Add this after the existing editor/new-schedule cleanup, before editor.commit().
alarmState?.let { editor.putString("exact_alarm", it.encode()) }
```

新编辑必须使用该参数同时保存更新后的配置和身份匹配的空 active 状态，不通过两次独立 commit 留下新配置/旧状态的组合。

- [x] 新增设备 `ScheduleEditIntegrationTest`，使用独立偏好名 `autoclick_edit_integration_fixture`、真实 SharedPreferences 和 `FailingCommitPreferences`；不得使用用户偏好清空作为本地测试捷径。编写并观察 RED：领取上午事件后终结，换 id 保留 scheduleId 并改到下午；新实例读到消费记录，reserve/claim 均拒绝当天；单纯超时 finish 而未 claim 的水位允许明确编辑到当日未来时间；失败 commit 不允许手势或重复领取。
- [x] 保留当前 `ClickExecutionGateTest` 的编辑/停止用例，不要求已通过的旧编辑 API 再次失败；新增精确定时交叉用例观察 RED。保留当前完整 `ClickExecutionGate.kt` 并与 EDIT_SOURCE 核对。把 EDIT_SOURCE Session 的 `ScheduleEdit` 和 `withScheduleEdit` 移入 EXACT Session，但保留精确版本 acquire 的 `readyTimeoutMs`、cancelScheduled、taskId/scheduleId 及生命周期清理。完整接入代码：

```kotlin
internal class ScheduleEdit internal constructor(private val lease: Long) {
    fun isActive() = gate.canEdit(lease)
    fun checkActive() = check(isActive()) { "修改期间已停止，请重新打开任务设置" }
    fun allowScheduled() = check(gate.allowScheduledAfterEdit(lease)) { "修改期间已停止，任务未启用" }
    fun halt() = gate.stop()
}

internal suspend fun <T> withScheduleEdit(block: suspend (ScheduleEdit) -> T): T {
    val lease = checkNotNull(gate.tryBeginEdit()) { "任务正在执行，请结束后再修改" }
    return try { block(ScheduleEdit(lease)) } finally { gate.finishEdit(lease) }
}
```

- [x] 复跑执行门、策略测试及保护运行器下的存储回归 GREEN。设备 RED 使用 Task 1 已演练的保护运行器，并按 Task 1 的依赖说明安排在 Task 5 构建完成后；不得测试旧产物。

## Task 4：时间编辑接入精确闹钟控制器

- [x] 在 EXACT 控制器构造器最后加入 `private val canEdit: suspend () -> Boolean = { true }`，生产 Context 构造传入：

```kotlin
canEdit = {
    withContext(Dispatchers.Main.immediate) {
        ClickServiceConnection.readiness(context.applicationContext) == ClickServiceReadiness.CONNECTED
    }
}
```

`armNext` 的 due 统一改为 `ExactClickPolicy.next(task, clock.wallMillis(), store.closedThrough(), store.consumedAt(task.id))`，其余登记和 ARMED 确认逻辑保留。

- [x] 新增 `updateSchedule`。以下是完整控制流程；需要导入 `NonCancellable`、`currentCoroutineContext`、`ensureActive`，其余类型来自已定义接口或现有 EXACT 源码：

```kotlin
suspend fun updateSchedule(expectedTaskId: String, hour: Int, minute: Int,
                           days: Set<Int>): ClickScheduleEditResult = withContext(Dispatchers.IO) {
    val selected = days.toSet()
    mutationLock.withLock {
        val current = checkNotNull(store.load()) { "任务已删除，请重新打开任务设置" }
        check(current.id == expectedTaskId) { "任务已改变，请重新打开任务设置" }
        require(hour in 0..23 && minute in 0..59) { "请输入有效的时间" }
        require(selected.isNotEmpty() && selected.all { it in 1..7 }) { "请至少选择一个有效的执行星期" }
        require(current.protection != null) { ClickTaskOutcome.NEEDS_RECORDING.message }
        check(store.alarmState()?.active == null && !ClickExecutionSession.state.value.active) {
            "定时准备或点击正在执行，请结束后再修改"
        }
        check(canEdit()) { "无障碍服务未连接，请恢复后再修改" }
        if (current.hour == hour && current.minute == minute && current.days == selected)
            return@withLock ClickScheduleEditResult.UNCHANGED
        ClickExecutionSession.withScheduleEdit { edit ->
            val latest = checkNotNull(store.load()) { "任务已删除，请重新打开任务设置" }
            check(latest.id == expectedTaskId) { "任务已改变，请重新打开任务设置" }
            val old = store.alarmState()
            check(old?.active == null) { "定时任务已开始准备，请结束后再修改" }
            val decision = ClickScheduleEditPolicy.decide(latest, old, platform.canSchedule(), TimeZone.getDefault().id)
            val updated = latest.copy(id = UUID.randomUUID().toString(), hour = hour,
                minute = minute, days = selected, enabled = decision.enabled)
            val pending = ClickAlarmState(updated.id, updated.scheduleId, decision.status)
            edit.checkActive()
            check(canEdit()) { "无障碍服务已断开，请恢复后再修改" }
            try {
                cancelLegacy()
                old?.next?.let { platform.cancel(it) }
                edit.checkActive()
                check(store.save(updated, decision.result.message, alarmState = pending)) { "无法保存任务" }
                currentCoroutineContext().ensureActive()
                edit.checkActive()
                if (updated.enabled) {
                    armNext(updated, null)
                    check(platform.canSchedule()) { "定时权限已关闭，任务未启用" }
                    check(canEdit()) { "无障碍服务已断开，请恢复后重新启用任务" }
                    edit.allowScheduled()
                } else {
                    edit.checkActive()
                }
                currentCoroutineContext().ensureActive()
                decision.result
            } catch (failure: Exception) {
                val revoked = !edit.isActive()
                edit.halt()
                withContext(NonCancellable) cleanup@ {
                    val lostPermission = runCatching { !platform.canSchedule() }.getOrDefault(false)
                    val status = if (lostPermission) ClickAlarmStatus.PERMISSION_REQUIRED else ClickAlarmStatus.SCHEDULE_FAILED
                    val toCancel = listOfNotNull(old?.next, store.alarmState()?.next).distinct()
                    var cancelled = true
                    for (event in toCancel) {
                        try { platform.cancel(event) } catch (cleanup: Exception) {
                            cancelled = false; failure.addSuppressed(cleanup)
                        }
                    }
                    try { cancelLegacy() } catch (cleanup: Exception) {
                        cancelled = false; failure.addSuppressed(cleanup)
                    }
                    val disabled = updated.copy(enabled = false)
                    val message = if (lostPermission) ClickScheduleEditResult.PERMISSION_REQUIRED.message
                        else "时间配置已保留，定时安排失败，请重新启用"
                    val saved = try {
                        store.save(disabled, message,
                            alarmState = ClickAlarmState(disabled.id, disabled.scheduleId, status))
                    } catch (cleanup: Exception) { failure.addSuppressed(cleanup); false }
                    if (failure is CancellationException) throw failure
                    if (saved && cancelled && lostPermission && !revoked)
                        return@cleanup ClickScheduleEditResult.PERMISSION_REQUIRED
                    throw IllegalStateException(if (saved && cancelled) message
                        else "调度状态未确认，请重新停用或启用", failure)
                }
            }
        }
    }
}
```

上面 `return@cleanup` 只返回清理块，其返回值是 catch 表达式的结果，不跨越外层 IO 上下文。对紧急停止和协程取消的验证必须保留，不能为了通过测试移除失败关闭。

- [x] 新增/迁移控制器设备回归，使用真实偏好和完整的可控平台：

```kotlin
private class RecordingAlarmPlatform : ClickAlarmPlatform {
    var permission = true
    var failure: Exception? = null
    var beforeSchedule: (() -> Unit)? = null
    val scheduled = mutableListOf<ClickAlarmOccurrence>()
    val cancelled = mutableListOf<ClickAlarmOccurrence>()
    override fun canSchedule() = permission
    override fun schedule(occurrence: ClickAlarmOccurrence) {
        beforeSchedule?.invoke()
        failure?.let { throw it }
        scheduled += occurrence
    }
    override fun cancel(occurrence: ClickAlarmOccurrence) { cancelled += occurrence }
}
```

至少将以下断言写入测试，不用 WorkInfo 成功替代行为：

```kotlin
val beforeHistory = store.lastExecutionResult()
val result = controller.updateSchedule(task.id, 16, 0, (1..7).toSet())
val edited = checkNotNull(store.load())
assertEquals(ClickScheduleEditResult.ENABLED, result)
assertNotEquals(task.id, edited.id)
assertEquals(task.scheduleId, edited.scheduleId)
assertEquals(task.points, edited.points)
assertEquals(task.protection, edited.protection)
assertEquals(beforeHistory, store.lastExecutionResult())
assertEquals(platform.scheduled.last(), store.alarmState()?.next)
assertEquals(ClickAlarmStatus.ARMED, store.alarmState()?.status)
assertTrue(platform.cancelled.contains(oldEvent))
assertFalse(store.reserveAlarm(oldEvent, now))
```

该测试的 task 采用设备默认时区，固定 now 为 2026-10-08 11:00，旧事件为当天 09:00；points/protection 用既有 `ClickAlarmStoreTest` 的合法固定夹具。Store 使用独立 fixture 偏好，平台用上述 RecordingAlarmPlatform，控制器 clock 使用固定 wall/elapsed 的 ClickClock，canEdit 默认 true。断言当天已消费时 platform.scheduled.last().scheduledAt 为次日 16:00，未消费时为当天 16:00。

- [x] 写并观察停止竞争 RED：`beforeSchedule` 内调用 `ClickExecutionSession.emergencyStop(context)`，保存不得返回 ENABLED，当前 task 停用，相关事件进入取消记录，重新打开执行门前定时 lease 不可取得；同时保留 MAIN 中“停止早于编辑保留权”测试。
- [x] 分别验证：disabled+无权不调用 schedule；启用+无权保留新配置并返回 PERMISSION_REQUIRED；失去服务拒绝/停用；时区异常保留并返回 TIME_ZONE_CHANGED；无修改不改 id 或调度；过期 expected id 拒绝；PREPARING/CLAIMED 期间拒绝；安排抛异常和失败 commit 显示可确认失败/未确认；权限在安排期间撤销；取消后保留已消费日期。修正生产代码后同一组复跑 GREEN。

## Task 5：合并主页、编辑结果与已选图标

- [x] 保留当前 `EditScheduleViewModel.kt` 并与 EDIT_SOURCE 核对；保存成功分支完整替换为实际结果，保留取消、异常和 finally 中 saving=false：

```kotlin
val result = controller.updateSchedule(snapshot.id, hour, minute, days)
candidate = null
resultMessage = result.message
```

不再依据 snapshot.enabled 推断保存后状态，不把缺权保存视为配置保存失败。发生确认的停用失败时，主页读取失败状态、编辑框保留错误；未知状态不显示保存成功。

- [x] `ClickTaskPresentation` 末尾新增 `editingBlocked: Boolean = false`。observer 在 presentExact 返回后统一设置：

```kotlin
view = view.copy(editingBlocked = run.active || alarm?.active != null)
```

保留 presentExact 的权限、五秒、最近结果、未来安排来源，不导回 WorkInfo snapshot。`editingBlocked` 包含 PREPARING，不只看已有 run.active。

- [x] 在 EXACT MainActivity 中按 EDIT_SOURCE 的既有实现接入 EditScheduleViewModel、编辑候选、保存结果 Toast 与 EditScheduleDialog；保留 EXACT 的权限提示和处理函数。打开条件和未提交弹框关闭条件使用 presentation.editingBlocked，保存中的操作由 ViewModel 持有，不因 id 刷新重复关闭或保存。
- [x] AutoclickScreen 新增 `onEditSchedule` 参数与 当前已验收的按钮，唯一新增条件为 `!presentation.editingBlocked`。完整按钮接入形式：

```kotlin
OutlinedButton(onClick = onEditSchedule,
    enabled = !busy && !presentation.editingBlocked && task.protection != null,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.heightIn(min = 48.dp)) { Text("修改时间") }
```

MainActivity 调用处传入现有 requireReady 检查后的 `editViewModel.open(task)`；状态效应加入 `presentation.editingBlocked`。提示及按钮延续原颜色、间距和可滚动布局。

- [x] 为此前旧编辑用例的 WorkManager 专用构造、旧 WorkInfo 断言和旧 Worker 投递，分别替换为可注入平台构造、持久化精确状态/实际系统闹钟断言、过期旧事件的 reserve/dispatcher 断言；保留原 UI 测试目的，不删除旋转、取消、录制/草稿/历史保留或重复保存断言。
- [x] `rotatingDuringSaveRetainsOneOperationAndDisablesDuplicateSave` 使用 RecordingAlarmPlatform.beforeSchedule 中的 CountDownLatch 阻塞，旋转期间保存/取消禁用、同一 ViewModel、最终仅一次 schedule；finally 始终释放 latch。
- [x] 保留图标并以来源清单逐文件核对全部 15 个资源哈希及 adaptive/monochrome 引用，构建检查资源可解析；不重新生成新图标。

## Task 6：联合模拟器验收

使用 Task 1 已演练的保护运行器；功能验收在 Task 5 后执行。已有旧版本迁移到停用/待启用不称为字节完全未变。
- [x] 构建 Debug 和测试包后，在 ROOT 执行以下受保护的定向 RED/GREEN 和完整回归入口，类列表必须来自实际已编译测试类：

```sh
python3 .artifacts/autoclick-integration/run_tests.py edit-store com.lumostech.autoclick.ScheduleEditIntegrationTest
python3 .artifacts/autoclick-integration/run_tests.py edit-ui com.lumostech.autoclick.EditScheduleUiTest
python3 .artifacts/autoclick-integration/run_tests.py regression com.lumostech.autoclick.AccessibilityGateUiTest,com.lumostech.autoclick.ClickTaskStatusUiTest,com.lumostech.autoclick.ClickTrialUiTest,com.lumostech.autoclick.GestureIntegrationTest,com.lumostech.autoclick.ClickTaskIntegrationTest,com.lumostech.autoclick.ClickAlarmStoreTest,com.lumostech.autoclick.ExactAlarmPlatformTest,com.lumostech.autoclick.ExactTimingRecoveryTest,com.lumostech.autoclick.ExactTimingIntegrationTest,com.lumostech.autoclick.ExactTimingPermissionUiTest#grantStillNeedsExplicitEnableAndKeepsTrialAvailable
python3 .artifacts/autoclick-integration/run_tests.py permission com.lumostech.autoclick.ExactTimingPermissionUiTest#hostDrivenPermissionRoundtrip permissions
```

如果引用的类在导入清单中缺失，先核对真实文件并修正运行列表；不得以空类/跳过替代回归。只能使用现有模拟器，不并发设备测试，不新建模拟器。

- [x] 将 `EditScheduleUiTest#editedEnabledScheduleClicksOnceAndConsumedDateStaysBlocked` 迁移为唯一的“经主页编辑后下一分钟”真实定时验收：保存至少提前 5 秒余量，通过实际编辑 UI 保存；把场景切成原用例的真实 Button 页面；生产系统时钟和正式 AlarmManager 触发，不用内部时钟替代。要求：

```kotlin
val trace = checkNotNull(store.startTrace())
val nextDue = trace.occurrence.scheduledAt
val firstDispatch = checkNotNull(trace.firstDispatchAt)
assertTrue(firstDispatch >= nextDue)
assertTrue(firstDispatch - nextDue < 5_000)
assertEquals(1, receivedCount.get())
assertTrue(buttonReceivedAt.get() >= nextDue)
assertFalse(store.reserveAlarm(oldEvent, System.currentTimeMillis()))
```

本用例定义 `receivedCount: AtomicInteger`、`buttonReceivedAt: AtomicLong`，Button 点击监听器同时写两个值；等待事件使用最多 90 秒的单调时间截止。原始非 UI 精确定时下一分钟用例仍保留一次作为链路回归，不把它们混计为同一成功次数。

- [x] 真实点击后再次经编辑 UI 修改到当日更晚时间：核对消费水位保留、下一次为以后日期、直接接纳该日新事件被拒绝；用持久化与领取拒绝证明防重复，不只靠等待零点击。
- [x] 权限实验增加“未启用/缺权任务仍可修改时间”断言：缺权编辑保留停用；原启用标记未协调时由可控平台用例验证保存时丢失权限。宿主撤权会杀进程，沿用 seed/lost/granted 三阶段，不在 instrumentation 内撤销自己的权限。重授后编辑也不能自动启用。
- [x] 捕获编辑普通、错误、大字体、横屏、缺权保存和精确状态截图；实际查看。桌面确认新图标与同一个已安装 APK 对应；UI 快照工具保留无障碍服务，不在进程恢复实验结束 PID 后手动启动应用或修改权限。
- [x] 每轮最后核对用户应用文件、录制/草稿/历史/消费记录、授权及显示恢复。失败保留可恢复私有备份；成功核对后删除私有备份，保存非敏感诊断。报告区分测试夹具、模拟器实际手势与实际按钮收到事件。

## Task 7：最终检查、文档与统一交付

- [x] 完成本地检查，测试任务加 `--rerun` 强制重新执行；不反复全量跑已经通过且未受后续修改影响的设备批次：

```sh
export JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home'
./gradlew :autoclick:testDebugUnitTest --rerun :accessibilityCore:testDebugUnitTest --rerun :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug --console=plain --no-daemon --max-workers=2
git diff --check
```

验收要求：单元测试零失败/错误，应用和测试包构建成功，Lint 零错误并说明警告变化。新增影响使用的告警修复；有意同步持久化等告警说明原因，不通过改成异步消费写入来消警告。

- [x] 更新 `autoclick/README.md`：精确闹钟、编辑时间/星期、每日消费、缺权/时区保存结果、旧版本迁移、原数据与恢复、停止、统一短时验收入口。以 JBR 21 为本轮已验证构建环境，不宣称当前 Android Studio 安装目录的 JDK 25 可构建。不改变历史阶段设计的业务说明。
- [x] 复制并同步两边相关完成记录，新增本次联合验收报告 `.artifacts/autoclick-integration/report.md` 与 `verification.json`；将 `2026-10-08-autoclick-status-audit` 标为“整合前盘点”，避免被当成实施后的实时状态。只在真实完成时更新本计划勾选项与设计状态。
- [x] 按 requesting-code-review 和 verification-before-completion 做本会话只读自查：控制器锁、PREPARING 互斥、每日消费接纳/领取/恢复、身份更新与旧事件、停止优先、持久化失败、权限/时区与 UI 归因。新增实质问题先复现并补回归，再修复复验；改变已确认业务规则则重新评审。
- [x] 核对 EXACT 来源、MAIN 无关文件及原暂存区仍未变；MAIN 计划内产品改动逐项对照来源和备份审查；记录整合 ROOT、HEAD、最终产品文件哈希、Debug APK/测试 APK 哈希、实际用例计数、首手势与按钮时间、数据恢复、未验收真机/重启边界。检查未残留测试闹钟及私有备份。
- [x] 向用户交付统一 APK、真实截图、报告和下一项旧任务恢复的明确状态。成果留在当前目录，原编辑来源备份及 EXACT 保留；不自动提交、推送或发布。

## 覆盖与评审检查表

| 已确认设计要求 | 对应任务 |
| --- | --- |
| 当前目录整合、来源备份与无关改动保护 | 1、7 |
| 精确闹钟唯一入口、五秒规则保留 | 1、4、5、6 |
| 时间/星期编辑、旋转与真实结果提示 | 2、4、5、6 |
| 每日消费与跳过事件区分、同日改晚不重放 | 2、3、4、6 |
| PREPARING/执行/试运行互斥、停止优先 | 3、4、5、6 |
| 缺权、停用、时区、无修改的行为 | 2、4、5、6 |
| 配置/状态原子保存、平台确认与失败清理 | 3、4、6 |
| 录制/草稿/保护/历史保留 | 3、4、6 |
| 已选图标及统一安装包 | 1、5、6、7 |
| 数据/授权保护、历史与本轮证据分开 | 1、6、7 |
| 文档/JDK/交付与剩余验收边界 | 7 |

设计已确认，用户随后“可以”明确同意当前目录整合；随后用户以“okgo”明确确认整份计划，已获实施授权。

2026-10-09 联合验收完成后，用户明确要求“commit & push to main”，授权将本轮整合提交并推送到 main。原计划的不自动提交限制不阻止这一明确授权。
