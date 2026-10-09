# AutoClick 联合验收记录

状态：已完成本轮获批整合与模拟器联合验收。103 项本地测试、115 项设备用例通过，零失败/错误/跳过；产物和来源、数据恢复已核对。真机及实际重启验收仍未完成。

执行日期：2026-10-08 至 2026-10-09（Asia/Shanghai）。

## 范围与来源

- 用户以“okgo”确认计划，直接在 `/Users/hgeng/AndroidStudioProjects/RemoteControl` 整合，验收阶段没有新工作区、分支、提交、推送或发布。
- 实施/验收基线 HEAD `93c7c4ccaa5c51fa795b0712ca299661f72be547`；精确定时只读来源 HEAD `4b668aac7d616b7f8eadf3f5cd9a47777f8ac6f6`。两项功能原先均包含未提交改动，以来源文件清单及完整编辑备份为依据。
- 同一安装包包含 AlarmManager 精确定时、时间/星期编辑和已选指尖图标。每日消费与跳过事件分别约束执行，五秒首手势窗口不变。

## 本地验证

`final-build.log`：JBR 21.0.11，强制重新运行 AutoClick 87 + accessibilityCore 16 = 103 个单元测试，零失败、错误和跳过。Debug 应用、测试包构建成功；Lint 零错误、34 条警告，与精确定时来源基线一致。原编辑版本为28条。保留安全消费的同步持久化，不以异步写入消除告警。主应用 Android 和 Zego 两个 Debug 变体已在 `verification-build.log` 构建通过。

`local-verification.json` 保存测试计数及最终 APK 哈希；`source-verification.json` 保存原暂存区、HEAD、无关修改、来源备份、EXACT 来源和15个图标资源核对结果。最终生产和测试源码哈希见 `product-hashes.json`。

## 失败复现与修复

1. `edit-controller-red.txt`：20项中19项通过，登记期间撤销权限错误返回 ENABLED。修复最终权限检查后，同组20项在 `edit-controller-green.txt` 全部通过。
2. `cancel-red.txt`：真实调用方 Job 在同步登记期间取消，任务仍启用。补充取消检查，进入同一停用清理路径；最终21项已在 `edit-controller-final4.txt` 全部通过。
3. `edit-joint.txt`：应用启动 ANR，尚未进入功能用例，不计为通过。旧 APK 在本轮整合安装前也有启动/输入 ANR；模拟器系统诊断见 `emulator-diagnostics.json`。随后原文件、任务、权限与显示设置恢复核对完成。
4. `edit-controller-final-console.log` 首次准备中，包路径校验未包含 Android 原生 `~` 字符；修正运行器路径校验。该准备失败不计功能结果。

5. `edit-controller-final2-console.log`：冷启动完成标记早于外部存储挂载及无障碍总开关稳定，截图清理失败，且恢复核对遇到系统自动从0恢复为1。无功能用例执行。等待系统稳定后，重新恢复并逐文件比较，设置恢复到已验证的重启前值1；`post-boot-restoration.json` 确认恢复、设置与外部存储就绪。

6. `edit-ui-console.log`：系统/系统界面 ANR 持续覆盖画面（`ui-current.png`、`ui-current2.png`），旋转用例结束后，保存等待超时。停止该轮夹具并恢复，结果不算界面验收通过。临时把1080×2424 / 420dpi降为720×1616 / 280dpi以减少软件渲染负载；`edit-ui2-console.log` 仍有系统异常遮挡，主动中止并恢复。`system-lastanr.txt` 显示异常针对系统界面 ANR 弹窗的输入分发。继续切换渲染后复验；临时显示设置最后恢复。

7. 2026-10-09 在已有 Pixel_9a_3 继续：6项界面语义检查通过，但真实点击准备等待超时。独立复现与诊断 `edit-environment-diagnostic.txt.live-log.txt` 明确前台是 `android` 的 system ANR窗口，页面保护正确拒绝执行。通过实际窗口XML定位“等待”按钮并关闭；不放宽保护或超时。测试代码只补环境诊断日志，应用APK不变，测试APK重新构建成功。
8. 用户中断时UI5尚未开始，模拟器退出且未完成恢复；保留的备份在继续后重新恢复，逐文件、任务、历史/消费与设置核对一致（`interrupted-ui5-recovery.json`）。运行器补充中断恢复和未恢复备份保护，避免新一轮覆盖待恢复备份。后续恢复与验收在后台顺序运行。

## 设备与数据保护

使用已有 Pixel_9a_2 与 Pixel_9a_3，Android API 36；未创建 AVD、未清除用户设备数据。Pixel_9a_2 的临时分辨率/密度已恢复，见 `display-restored-avd2.json`。最终批次运行在 Pixel_9a_3，1080×2424、420dpi，host图形模式；该环境有API36图形兼容警告，验收仅代表这一模拟环境。每轮先备份再修改，finally 逐文件比较并恢复权限、其他无障碍服务、字体与旋转。故意失败的保护演练见 `protection-restore.txt`。

首次从旧 WorkManager 版本升级，原启用任务按已确认规则变为停用/待启用，配置、录制、消费记录和历史保留，见 `edit-controller-red-migration.json`。这是合法迁移，不能称启用状态完全未变；后续按迁移后的状态恢复。

## 最终设备结果

- 同一最终应用 APK：`edit-controller-final4.txt` 21/21，`edit-ui6.txt` 7/7，均无失败/跳过且恢复核对通过。
- 编辑后的真实 AlarmManager 事件：计划 2026-10-09 21:24:00，首手势晚217ms，按钮晚360ms收到一次点击；当天改晚消费保护和旧 Worker 拒绝都通过。日志 `edit-ui6.txt.live-log.txt`，截图 `edit-ui6-edit-result.png`。
- 普通编辑、错误、大字体和横屏截图已实际查看，最终截图无系统异常弹窗。此前被系统弹窗遮挡的截图只作诊断，不作视觉验收通过证据。
- `regression.txt`：84/84，通过手势保护、执行门、试运行、任务/状态、存储、精确闹钟、恢复及普通权限入口回归。
- `permission-seed.txt`、`permission-lost.txt`、`permission-granted.txt`：各1/1。宿主在结束 instrumentation 后撤权，真实系统闹钟取消；缺权编辑与重授后编辑仍停用，不自动启用。普通缺权/重授截图已实际查看。
- 合计115项来自上述顺序批次（21 + 7 + 84 + 1 + 1 + 1），并非单次115项 instrumentation，也不重复计入历史独立验收、RED、诊断或被中断批次。
- 未经编辑的生产精确定时回归：计划2026-10-09 21:27:00，系统接收晚24ms、首手势晚196ms、按钮晚271ms，仅收到一次点击，重复零次。与21:24的编辑场景分别记录；两条首手势都在五秒窗口内。
- `final-launcher.png`：已实际查看桌面指尖图标，同一已安装应用SHA256与交付APK一致。最终应用数据恢复到原来无保存任务的状态；`final-device-state.json` 确认无排队的测试精确闹钟、无私有待恢复备份。本轮新安装的测试包已移除，原应用保留。`final-alarms.txt`中的 EXACT_CLICK 仅在已取消/撤权的历史记录中，不能当成排队闹钟。

## 交付与复核入口

- 统一Debug安装包：`autoclick-debug.apk`（本地交付文件，不纳入Git；可按README命令从源码构建）。
- [机器可复核记录](verification.json)、[最终设备清理](final-device-state.json)、[来源保护](source-verification.json)、[本会话自查](review.md)
- [正常编辑](edit-ui6-edit-normal.png)、[真实点击后的状态](edit-ui6-edit-result.png)、[缺权保存](permission-edit-permission.png)、[重授后仍停用](permission-edit-regranted.png)、[桌面图标](final-launcher.png)

应用APK SHA256：`06f9f5962066fcc9888903f8e0e1fee5e1207226017e8259e017089d46ab070d`。
测试APK SHA256：`046cc36cbf68fc06e49a7374c385a1fec1e0eed4fa1c755c677cfae055786852`。

最后一轮设备批次均使用这两个APK；测试包的最后一次改动仅补环境诊断日志，`environment-diagnostic-build.log` 构建成功。应用源码/资源自 `final-build.log` 后未变，142个源码/资源哈希仍一致。文档收尾无需重跑不受影响的用例。原暂存区、两边HEAD、40份编辑来源备份、45份精确定时来源及无关文件、15个图标均在交付前重新核对通过。

## 边界与剩余事项

本轮使用模拟器真实手势和真实按钮事件；不代表真机、厂商后台策略或实际重启验收。旧任务恢复新交互仍在设计阶段，未纳入本轮。

## 提交与推送授权

2026-10-09，联合验收完成后用户明确要求“commit & push to main”。本记录描述提交前验收基线；随后提交的范围为 AutoClick 源码、必要共享接口、测试、设计/计划及精选非敏感验收证据。私有备份、原始来源备份、APK、缓存、无关修改及原暂存的三份文件不纳入本次提交。完整环境诊断日志保留本地；Git中的日志只保留构建、功能结果及恢复确认。

提交前复验：`pre-push-verification.log` 构建成功；强制重跑103项本地测试零失败/错误/跳过，Lint仍为零错误34警告。应用/测试APK哈希未变；此前115项设备日志及对应源码哈希重新核对，没有重复运行不受变更影响的设备夹具。
