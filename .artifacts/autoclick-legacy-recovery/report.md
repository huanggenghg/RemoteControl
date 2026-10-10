# AutoClick 旧版任务恢复体验验收

验收日期：2026-10-09；交付整理：2026-10-10，Asia/Shanghai。设计和实现计划已获用户明确确认；已在当前 `main` 工作目录完成实现，验收结束时尚未提交、推送或发布。

后续整合：2026-10-10 用户明确要求“变动记得合并回main”。本次功能提交据此将已验收的代码、测试、设计文档和必要复验运行器纳入本地 `main`，保留原有其他修改及暂存内容，不推送或发布。下面的验收结果、原 HEAD 和环境保护核对是提交前的归档快照；此次提交前重新执行的 110 项本地测试也全部通过，日志为 `main-integration-tests.log`。实际 Git 整合结果另存本地 `main-integration.json`。

## 已交付行为

- 旧版任务卡片直接说明：缺少目标应用、屏幕尺寸和方向信息，需要重新录制才能启用或试运行。保存中、执行中、服务断连和恢复录制中也有对应的不可操作原因。
- 旧任务仍可修改时间和星期，保持停用；原点击、时区、稳定计划身份、执行历史、日期消费记录和录制草稿保留。取消、无变更和保存失败不替换原配置。
- “重新录制”先说明流程和清空范围。确认开始并满足权限后才创建新草稿；取消说明或拒绝权限不清空。暂停后可以继续同一草稿，也可明确选择重新开始。
- 长按悬浮按钮选择时间/星期，“保存并替换旧任务”可靠保存完成前保留原任务。成功后的新任务保持停用，无自动闹钟；先试运行，再手动启用。
- 重复提交、旋转、目标应用在前台和进程中断保留同一恢复身份。未完成的保存暂停，已确认完成的保存显示同一新任务；均不会自动重试或启用。
- 无法确认存储时，保留最后已确认的任务展示并阻止修改；不会把读取异常显示成“还没有定时任务”。原任务变化或录制身份不匹配不能覆盖其他任务。

任务切换先同步并读回原子快照，再同步既有偏好。偏好同步失败或清理失败时，已确认的快照继续作为所有任务读写入口的来源；后续编辑、删除和监听也经过同一门面。保存工作由 ViewModel 持有，使用应用级依赖，不持有 Activity 或悬浮面板。

## 最终验证

以下是最后一次顺序验收的实际结果，所有设备批次均使用同一最终 APK。此前 RED、诊断及中间 GREEN 不计入本表。

| 验证 | 通过 | 失败 / 跳过 | 证据 |
| --- | ---: | --- | --- |
| AutoClick JVM 测试 | 94 | 0 / 0 | `verification.json`、Gradle XML |
| 共享无障碍库 JVM 测试 | 16 | 0 / 0 | `verification.json`、Gradle XML |
| 存储、控制器、共享录制 | 26 | 0 / 0 | `legacy-storage-final-console.log` |
| 恢复界面与真实录制/试运行 | 11 | 0 / 0 | `legacy-ui-final-console.log` |
| 既有编辑、状态、试运行、手势和定时恢复 | 68 | 0 / 0 | `legacy-regression-final-console.log` |
| 生产精确闹钟真实点击 | 1 | 0 / 0 | `legacy-exact-final-console.log` |
| 草稿未保存后的真实进程恢复 | 1 + 主机断言 | 0 / 0 | `legacy-process-draft-final-process.json` |
| 原子切换前的真实进程恢复 | 1 + 主机断言 | 0 / 0 | `legacy-process-before-final-process.json` |
| 已落盘、界面未收到结果时的真实进程恢复 | 1 + 主机断言 | 0 / 0 | `legacy-process-after-final-process.json` |

合计 **110 项本地测试、109 项设备用例**。三个进程用例先完成 instrumentation 种子，再由主机结束普通应用进程并重新打开，主机断言也全部通过。它们与只重新构造存储对象的测试分别记录。

Debug 应用及测试 APK 构建成功。Lint：**0 错误、34 警告**；警告数量与此前整合记录一致。当前生产和测试源文件的 145 个哈希与最终构建后快照一致。构建日志为 `release-check-local.log`，该日志执行的全部任务都是 Debug。

测试环境：现有 `Pixel_9a_3`，`emulator-5554`，Android API 36；SDK 36、JBR 21.0.11。未创建新模拟器或新工作目录，未增加依赖。

## 原任务和故障保护

存储测试实际覆盖原子快照写入失败、偏好同步已经改变内存却返回失败、重建门面、记录清理失败、失败后的继续编辑/删除、类型保真和监听，以及排队写入不能恢复已删除任务。真实 AtomicFile 读写及损坏记录阻止读取/修改也已验证。

控制器用例覆盖源任务变化、错误的录制身份、无有效保护、服务断连、执行准备互斥、停止撤销保存保留权、重复完成及手动启用改变回调身份后的完成查询。旧时间编辑的历史/消费保留、无变更及写入失败均通过。

共享录制用例验证了实际写入尝试失败后的计数/快照回退、开始失败不发布新录制、同一会话重新读取和普通录制清除会话标记。界面用例覆盖说明取消、权限拒绝、暂停继续、失败后明确重试、陈旧源任务、存储未确认、旋转、1.5 倍字体和横屏。

本次补充测试发现并修复两处实际问题：保存期间旋转会重新显示录制悬浮按钮；目标应用在前台时面板不能及时显示保存状态。失败分别保存在 `saving-overlay-red-console.log` 和 `background-panel-red-console.log`；聚焦复验见 `saving-ui-green-console.log`，最终 11 项界面用例也全部通过。

早期行为失败证据另存于 `presentation-red.log`、`legacy-edit-red-console.log`、`contracts-red.log`、`storage-red-console.log`、`controller-red-console.log` 和 `recording-ui-red-console.log`。接口不存在等编译缺口仅属于搭建过程，不作为原任务保存保护的行为验证。

## 真实点击和定时结果

恢复界面用例使用真实悬浮录制和长按保存。新任务保持停用时，重复投递两次旧闹钟事件，**自动点击 0 次**；手动试运行在目标按钮收到 **1 次真实点击**。随后通过界面手动启用，才核对未来闹钟。该恢复用例没有等待启用后的未来闹钟执行。

既有编辑定时回归另用独立测试任务：真实点击 1 次，首手势晚 **296 ms**，按钮收到点击晚 **365 ms**；同日消费保护和陈旧 Worker 拒绝均通过。

生产精确闹钟独立用例：真实点击 1 次、重复点击 0 次；首手势晚 **367 ms**，按钮收到点击晚 **865 ms**。两项首手势均在原来的五秒窗口内，使用生产时钟、控制器、AlarmManager、Receiver 和无障碍服务。

本次日志使用新监听起点，不混入此前轮次：`legacy-ui-final.txt.live-log.txt`、`legacy-regression-final.txt.live-log.txt`、`legacy-exact-final.txt.live-log.txt`。

## 真实进程恢复

| 中断点 | 旧 PID → 新 PID | 重新打开后的结果 |
| --- | --- | --- |
| 草稿未保存 | 5164 → 5206 | 原任务仍停用，匹配草稿保留，会话暂停，无闹钟 |
| 切换前，仅有未完成的临时记录 | 5792 → 5823 | 原任务仍停用，草稿保留，未自动提交，无闹钟 |
| 新快照已确认、偏好同步失败、界面未收到结果 | 6395 → 6427 | 同一新任务停用，完成会话清理，旧历史不归入新任务，无闹钟 |

结束进程时 instrumentation 已结束。主机先重新绑定普通无障碍服务以获得真实应用进程；两个 SAVING 阶段在结束前均确认尚未被 MainActivity 消费。随后仅结束该应用 PID，再明确打开 MainActivity。每次验证新的 PID、正确任务、匹配草稿、会话状态、停用状态及零未来/活动闹钟。这是进程结束/重新打开验证，不是实际系统重启或 Android 强行停止后的自动恢复验证。

## 界面截图

截图均来自测试任务，不包含原用户的点击数据。

![旧版任务原因和恢复入口](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-legacy-recovery/legacy-ui-final-legacy-reason.png)

![开始前的恢复说明](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-legacy-recovery/legacy-ui-final-legacy-explanation.png)

![恢复录制设置面板](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-legacy-recovery/legacy-ui-final-legacy-panel.png)

![横屏及大字体仍可保存](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-legacy-recovery/legacy-ui-final-legacy-landscape.png)

另有 `legacy-ui-final-legacy-large-font.png` 和 `legacy-ui-final-legacy-trial-result.png`。

## 安装包、复验与原环境保护

[已验证 Debug APK](/Users/hgeng/AndroidStudioProjects/RemoteControl/.artifacts/autoclick-legacy-recovery/autoclick-debug.apk)。SHA-256：

```text
a7d8cb858cb16f5a9dae56148b2d7bbeb0367ccbad68cd5915d67bcf4e23e945
```

测试 APK SHA-256：`2580265019ac8c046020b1825afbaa1b8be04534b487466bc68230dd5819c454`。

每批安装和造测试数据前，保护运行器备份并核对 `shared_prefs`、`files`（包含会话及原子记录的 `.new`/`.bak`）、`databases`、`no_backup`、原安装包、无障碍配置、定时/悬浮 app-op、字体和旋转。每批结束后恢复并核对，只有核对成功才清除私有备份。最终七批均恢复核对成功，所有私有临时备份已清除；最终模拟器使用原安装包，交付 APK 单独保留供安装。

原安装包 SHA-256：`06f9f5962066fcc9888903f8e0e1fee5e1207226017e8259e017089d46ab070d`。恢复结果见各批 `*-restore.txt`，完整原任务数据和原子记录没有写入本报告。

基线覆盖 378 个已跟踪文件；计划范围之外的文件哈希、原暂存差异和 HEAD 均未变化，包括主 app 的既有 Logger 修改及 `.idea/misc.xml`。见 `workspace-preservation.json`。HEAD 仍为 `1fcd837310eb1af0f338c1b77a8254c0ebe488d2`。

完整复验入口：

```sh
export JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home'
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest :autoclick:assembleDebug :autoclick:assembleDebugAndroidTest :autoclick:lintDebug --console=plain --no-daemon --max-workers=2
python3 .artifacts/autoclick-legacy-recovery/run_acceptance.py
```

设备批次严格串行且显式指定已有 `emulator-5554`；另一环境需先核对设备身份再调整运行器。不要绕过备份保护直接运行生产偏好测试。`verification.json` 和 `acceptance-batches.json` 是最终验收从实际输出提取的归档结果，核对 APK、源文件及恢复证据。原始本地基线与 `collect_verification.py` 留在本地证据目录，仅用于原验收基线核对；不将原暂存差异、私有备份或 APK 纳入 Git。

## 自检与验证边界

按确认计划在当前聊天顺序实施并自行检查差异，没有派发开发子代理。自检重点为：全部任务存储入口经过同一门面、完成点前不覆盖原任务、已确认切换不因同步/清理失败回退、会话与源任务身份一致、保存协程不持有界面、后台面板及时更新、紧急停止及原五秒窗口保留。当前本地和模拟器验证没有未解决的失败。

**尚未进行真机、厂商后台策略及实际系统重启验收。** 模拟器结果不代表这些环境的执行保证。共享库仅修改必要的录制持久化接口，主远控 app 的双机通信不在本次范围；本地 `main` 整合遵循用户后续授权，未推送或发布。
