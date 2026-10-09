# AutoClick 修改任务时间与星期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the current session. Steps use checkbox syntax. 2026-10-07 用户已明确确认本计划，按授权实施。不得自动提交、推送、发布或创建新聊天。

**Goal:** 主页直接修改已保存任务的时间和星期，保留录制、启用状态、历史结果及每天防重复执行保护。

**Architecture:** 编辑草稿与已保存任务分离，Compose 弹框只负责输入；控制器串行处理身份校验、持久化和唯一工作替换。执行门增加短暂编辑保留权，与定时、试运行及紧急停止协调；统一按保存时区的消费日期计算下一次计划和领取执行权。

**Tech Stack:** Kotlin/JVM 11、Compose Material3、Lifecycle、WorkManager 2.11.0、SharedPreferences、JUnit4、AndroidJUnit4/Compose UI tests；SDK 36，minSdk 24。不添加依赖。

---

## 确认与工作区

- 设计依据：`docs/superpowers/specs/2026-10-07-autoclick-edit-schedule-design.md`。2026-10-07 用户确认时间和星期都可编辑，并确认设计。
- 本实现计划已由用户以“确认”明确批准；设计与计划分别确认。
- 使用当前工作区已实现的 WorkManager 路径。精确闹钟文档存在但功能未实施；若执行前发现调度代码已被其他工作改成精确闹钟，暂停复核受影响部分，不能将过时方案套上新架构。
- 实施前记录差异清单；保留主 app、图标、依赖目录及其他已有修改。不自动 commit。每个任务以通过的检查和差异作为检查点。
- 先写能复现行为问题的测试，运行观察失败，再做最小实现，重跑确认通过。纯文案和静态布局不写镜像测试。

## 文件职责

所有路径以仓库根目录 `/Users/hgeng/AndroidStudioProjects/RemoteControl` 为基准；以下 `kotlin/` 仅在表格中代指 `autoclick/src/main/java/com/lumostech/autoclick/`。

| 文件 | 操作与责任 |
| --- | --- |
| `kotlin/ClickSchedulePolicy.kt` | 统一消费日期判断和严格未来的下一次计算 |
| `kotlin/ClickTaskStore.kt` | 读取消费水位，领取时按日期拒绝重复；控制错误独立存储 |
| `kotlin/ClickExecutionGate.kt` | 编辑保留权与执行互斥，紧急停止撤销保留权 |
| `kotlin/ClickExecutionSession.kt` | 对控制器暴露安全编辑包装，不持有 Activity |
| `kotlin/ClickTaskController.kt` | 独立 `updateSchedule` 操作、旧身份失效和工作替换 |
| `kotlin/ClickTaskPresentation.kt` | 后续计划使用消费水位；展示控制错误而不替换执行结果 |
| `kotlin/ClickTaskStatusObserver.kt` | 将消费水位和控制错误传给纯展示逻辑 |
| `kotlin/EditScheduleViewModel.kt` | 新建；跨旋转持有单次保存及错误状态，不保留 Activity |
| `kotlin/ui/EditScheduleDialog.kt` | 新建；时间、星期、校验与保存/取消 |
| `kotlin/MainActivity.kt` | 编辑草稿状态、回调、身份/权限变化和保存生命周期 |
| `kotlin/ui/AutoclickScreen.kt` | 修改入口、禁用条件、时区恢复说明 |
| `autoclick/src/test/java/com/lumostech/autoclick/ClickSchedulePolicyTest.kt` | 日期与下一次逻辑回归 |
| `autoclick/src/test/java/com/lumostech/autoclick/ClickExecutionGateTest.kt` | 执行门竞争回归 |
| `autoclick/src/test/java/com/lumostech/autoclick/ClickTaskPresentationTest.kt` | 下一次及结果/错误区分 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/ClickTaskIntegrationTest.kt` | 存储、工作替换和失败分支 |
| `autoclick/src/androidTest/java/com/lumostech/autoclick/EditScheduleUiTest.kt` | 新建；真实主页编辑交互与截图 |
| `autoclick/README.md` | 使用说明、当天不重放及停用保持 |
| `.artifacts/autoclick-edit-schedule/` | 测试证据、截图和验证报告 |

## Task 1：消费日期与下一次计划

- [x] 在 `ClickSchedulePolicyTest.kt` 增加回归：周一 09:00 已消费，改到同日 16:00 时下次为周二 16:00（全周选中）；只选周一则下周一；当天未消费可安排今天 16:00；时间等于现在必须跳过。覆盖改早、跨午夜、DST 和时钟回拨。
- [x] 先运行失败测试：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :autoclick:testDebugUnitTest --tests 'com.lumostech.autoclick.ClickSchedulePolicyTest' --console=plain
```

预期为新增断言失败或新签名缺失；依赖下载、JDK 或设备错误不算行为失败。

- [x] 增加 `ClickSchedulePolicy.isConsumedDate(candidate: Long, consumedAt: Long?, timeZoneId: String): Boolean`。空水位返回 false；否则 candidate 不晚于 consumedAt，或两者在指定时区同一天，返回 true。保留对回拨的阻断。

核心实现：

```kotlin
fun isConsumedDate(candidate: Long, consumedAt: Long?, timeZoneId: String): Boolean {
    if (consumedAt == null) return false
    if (candidate <= consumedAt) return true
    val zone = TimeZone.getTimeZone(timeZoneId)
    val a = Calendar.getInstance(zone).apply { timeInMillis = candidate }
    val b = Calendar.getInstance(zone).apply { timeInMillis = consumedAt }
    return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
```

- [x] 将 `nextOccurrence` 签名扩展为 `nextOccurrence(task: ClickTask, now: Long, consumedAt: Long? = null): Long`。现有循环的返回条件增加 `!isConsumedDate(due.timeInMillis, consumedAt, task.timeZoneId)`，保留逐日重新构造时间及严格 `> now`。
- [x] `ClickTaskStore` 增加 `consumedAt(taskId: String): Long?`，仅当前身份匹配且键存在时返回值；`wasConsumed` 改为调用上述日期判断，`claimExecution` 沿用现有存储锁及落盘后才允许点击的规则。
- [x] 在存储设备测试中验证同日改晚仍 ALREADY_CONSUMED、未来日期可领取、旧身份 STALE_TASK、重建 Store 不丢水位。重跑纯逻辑测试并检查原消费回归仍成立。

## Task 2：编辑与执行互斥

- [x] 为 `ClickExecutionGateTest.kt` 增加失败用例：执行中不能编辑；编辑保留期间定时和试运行不能开始；结束编辑后能开始；紧急停止撤销编辑令牌且阻止自动开始；旧令牌释放不能影响新持有者。
- [x] 运行对应单元测试观察失败：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :autoclick:testDebugUnitTest --tests 'com.lumostech.autoclick.ClickExecutionGateTest' --console=plain
```

- [x] 扩展执行门，使用独立编辑令牌及同步方法 `tryBeginEdit(): Long?`、`canEdit(lease: Long): Boolean`、`finishEdit(lease: Long)`。`tryStart` 在编辑令牌存在时返回 null；`stop` 使编辑令牌失效；编辑完成不清除紧急停止标志。所有入口共用同一把同步锁，禁止先读状态再无保护地写入。
- [x] 在 `ClickExecutionSession` 增加 `withScheduleEdit` 包装：取得令牌，无法取得时报“任务正在执行，请结束后再修改”；在 finally 释放。控制器在持久化前、重建工作前、允许自动执行前确认令牌有效。有效性检查及执行门恢复须为同一同步操作，紧急停止不能在检查后被保存流程撤销。
- [x] 保存期间执行入口不得拿到新执行权；若系统事件在保留期间到达，已有跳过语义不转成排队或补执行。紧急停止仍优先取消现有序列，并通过原控制器锁完成持久化停用和取消工作。
- [x] 重跑执行门用例和原试运行/紧急停止单元回归。生产 Worker 仍走同一个 `ClickExecutionSession.acquire`，不新增另一条可绕过执行门的路径。

## Task 3：原任务更新时间与星期

- [x] 新增设备回归：已启用任务修改后仅有一个新身份的有效周期工作；已停用任务修改后保持停用且无工作；录制、保护、时区、稳定 scheduleId、消费水位和最近结果全部保留；草稿不变；旧回调不能写结果；原身份不匹配和空星期被拒绝；原值保存无变化；调度失败不覆盖历史结果。
- [x] 通过 Task 6 的备份保护运行设备用例观察失败，再新增控制器接口：

```kotlin
suspend fun updateSchedule(
    expectedTaskId: String,
    hour: Int,
    minute: Int,
    days: Set<Int>
)
```

- [x] `updateSchedule` 在既有 `mutationLock.withLock` 中加载当前任务并验证 expectedTaskId、时间范围、至少一天且所有星期有效、录制保护存在。输入天集合复制为不可变快照。原值一致直接返回，不允许通过无变化保存恢复异常调度。
- [x] 通过 Task 2 的编辑包装取得互斥保留权后重读身份及启用状态，再创建：

```kotlin
val updated = current.copy(
    id = UUID.randomUUID().toString(),
    hour = hour,
    minute = minute,
    days = days.toSet()
)
```

`current` 是锁内重读并通过校验的原任务；不构造新录制、不生成新 scheduleId、不把 enabled 改成 true。

- [x] 保持同 scheduleId 的存储语义，保存配置和控制文案。enabled 为 true 时调用现有唯一工作替换；false 时取消可能残留的唯一工作且不开放自动执行门。成功后再关闭界面并提示“时间已修改”或“时间已修改，任务仍停用”。
- [x] `enqueue`、`alignNextOccurrence` 调用 Task 1 的 nextOccurrence 时传入 `store.consumedAt(task.id)`；恢复和停用再启用经过这些入口，保持一致。旧 id 无法通过 Worker 的加载、等待、领取、继续及结果写入检查。
- [x] 修改失败分支使用独立控制错误，不调用会覆盖 execution_record 的现有 `store.save(..., reason = ...)`。增加 `control_error` 存储键及读取方法 `controlError(taskId: String): String?`，存储内容关联当前 id；正常明确保存/启用成功后清除，任务 clear 时一并清除。
- [x] 安排失败时阻断自动开始、尽力写入新配置的停用状态和 control_error，并在 finally 尝试取消唯一工作。原异常和停用/取消异常分别记录；若任一步无法确认，返回失败并明确“调度状态未确认，请重新停用或启用”，不能宣称保存成功或已停用。存储失败可能更新内存但没有落盘，必须注入该故障验证，不能以当前内存值作为落盘证明。
- [x] 回归覆盖停止与编辑竞争，确保紧急停止令牌失效后不会调用无条件 `allowScheduled()`；任何失败也释放编辑保留权。

## Task 4：主页编辑弹框及状态刷新

- [x] 新建 `EditScheduleUiTest.kt`，使用 `createEmptyComposeRule()`，准备服务后才启动 Activity；使用真实保存任务且另存不同草稿，证明编辑读取的是任务。先写入口缺失、预填、取消、保存、空星期、保持停用及旋转测试，运行观察失败。
- [x] `AutoclickScreen` 增加 `onEditSchedule: () -> Unit` 回调和“修改时间”按钮；条件为 `!busy && !runState.active && task.protection != null`。无任务不显示；其余入口保持原职责。
- [x] `EditScheduleDialog.kt` 使用应用内 Dialog/AlertDialog，滚动容器和宽度限制；24 小时制可输入时/分，星期七个独立多选项；预填保存值，按钮“保存修改”“取消”。输入变化只更新草稿，星期为空显示内联错误。为时、分和星期提供可访问语义及测试定位标识。
- [x] Activity 通过 `rememberSaveable` 保存 candidateId、hour、minute、选中星期列表；编辑回调先 `requireReady()`、检查 busy 与执行状态再读取 task。保存回调转换星期集合并调用 controller.updateSchedule(candidateId, hour, minute, days)。失败保留可修正的输入并显示具体原因；任务已替换等过期错误关闭弹框，引导重新打开。
- [x] 任务身份变化、无障碍不就绪或执行开始时关闭未提交草稿。保存过程中使确认/取消禁用，使用 finally 恢复 busy；恢复后刷新实际任务。旋转不能重复提交，保存完成后不能出现保留旧 candidateId 的编辑框。
- [x] `present` 尾部增加可选输入 `consumedAt: Long? = null`、`controlError: String? = null`，保持现有调用兼容；内部计算后续计划传消费水位。观察器传入对应当前任务的水位和控制错误；失败提示与恢复按钮使用 controlError，最近结果继续读取 execution_record。
- [x] 为 `ClickTaskPresentationTest` 增加回归：已消费日期改晚后的后续计划与实际工作一致；调度失败显示错误且 lastRecord 保留。时区恢复说明改为明确“修改时间沿用保存时区”，不能声称编辑可以完成时区迁移；仍保留原重新保存路径。
- [x] 运行上述设备与纯逻辑测试确认通过；检查弹框和主页在正常、小屏、大字体、横屏下可滚动且按钮可达。

## Task 5：说明及本地检查

- [x] 更新 `autoclick/README.md` 的步骤 4：主页“修改时间”支持时间与星期，取消不保存、停用保持、每天开始一次、过去时间不补执行、时区沿用；删除“编辑只能重新保存”的过时描述。不改变另有待评审的精确调度文档。
- [x] 使用 Android Studio JDK 运行：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :autoclick:testDebugUnitTest :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug --console=plain
git diff --check
```

预期命令退出码 0；记录测试通过数量及 Lint 原有/新增警告，不把编译通过作为点击执行通过。若共享模块代码确有必要改变，先说明原因并增加主 app 构建回归；本计划默认不涉及共享模块。

## Task 6：保护用户数据的设备验收与交付

- [x] 只读确认现有设备、目标应用及测试 APK，不为普通回归启动第二台模拟器。参考 `.artifacts/autoclick-guidance-status/run_tests.py` 创建本轮运行器，将输出写入 `.artifacts/autoclick-edit-schedule/`，不得覆盖上一轮证据。
- [x] 运行器先快照应用数据（含任务、草稿、执行记录及 WorkManager 数据）和实际涉及的系统授权，再执行新测试及相关旧回归，finally 恢复并核对内容。截图/验证用时调整和字体方向也须记录原值、结束恢复；测试期间使用自有测试目标，不点击用户业务页面。
- [x] 设备回归包含 ClickTaskIntegrationTest、新 EditScheduleUiTest、ClickTaskStatusUiTest，以及既有试运行/紧急停止相关测试；模拟编辑与 Worker 竞争及故障注入单独标注，不称为真实定时触发。
- [x] 至少一次通过主页修改未来时间后，验证旧工作失效、新工作真实等待及对应测试目标的真实点击；另一次验证已消费日期改晚没有第二次自动点击。后者同时以持久化领取拒绝及匹配的下一次工作断言证明，不能仅以短暂等待没点击为证据。
- [x] 留存正常、已停用、空星期错误、大字体和横屏截图，并实际查看检查布局。
- [x] 若没有可用设备，只交付本地通过证据并明确设备验收未完成，不能写“全部验证通过”；不得把日志或模拟结果当作真实点击。
- [x] 自查差异只覆盖本功能；报告实现行为、测试数量、真实点击证据、数据恢复结果、剩余限制及 APK 路径。不自动提交、推送或发布。

## 计划自查

- [x] 每项已确认设计均对应上述任务；当天防重复覆盖存储、调度和展示三个环节。
- [x] 已定义新增接口职责与参数，区分稳定任务归属和可失效执行身份。
- [x] 取消、无修改、停用保持、旧回调、保存失败、紧急停止及编辑竞争均有明确分支。
- [x] 工作区隔离约束、设备数据恢复及真实验收证据明确。
- [x] 无自动提交/发布步骤；用户已明确确认本实现计划后执行。

## 验收记录

已按授权实施并完成本地与模拟器验证。新增 `EditScheduleViewModel` 保持旋转中的单次保存；独立审查发现的停止竞争和保存端服务就绪校验已修复并复核。71 项单测通过，设备用例按最新结果去重为 47 项通过。真实点击、消费阻断、旧回调拒绝及数据恢复证据保存在 `.artifacts/autoclick-edit-schedule/`；初次失败和后续复跑均保留。未自动提交或发布。
