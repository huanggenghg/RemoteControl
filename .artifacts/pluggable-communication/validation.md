# 可替换通信供应商验证记录

日期：2026-10-08。仓库：`/Users/hgeng/AndroidStudioProjects/RemoteControl`。

## 范围

用户选择打包时切换，确认设计并授权先实现；声网 Token 获取与真实双机功能验证由用户后续补充。工作区原有 autoclick、API 和日志改动保留，未提交、推送、发布或安装。

## 环境与基线

- Android SDK 36、minSdk 24、源代码 JVM 11；Gradle 使用本机 Android Studio JBR 21.0.11。
- 当前安装的 Android Studio JBR 25 与 Kotlin 2.0.21 工具存在兼容问题，验证使用已有 JBR 21，不修改系统默认 JDK。
- 修正本任务链路中的已有构建问题：BuildConfig 未生成、Token 请求 DTO 使用不一致、旧 ViewModel 返回字段错误、OkHttp 日志签名、工具类 Logger/IOException 引用。

## 验证结果

最终统一检查 `BUILD SUCCESSFUL in 54s`，629 tasks；所有以下任务通过。复验命令：

```sh
JAVA_HOME='/Users/hgeng/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home' ./gradlew \
  :communication-api:testDebugUnitTest :communication-zego:testDebugUnitTest :communication-agora:testDebugUnitTest \
  :communication-api:lintDebug :communication-zego:lintDebug :communication-agora:lintDebug \
  :app:testZegoDebugUnitTest :app:testAgoraDebugUnitTest :app:lintZegoDebug :app:lintAgoraDebug \
  :app:assembleZegoDebug :app:assembleAgoraDebug :app:assembleZegoRelease :app:assembleAgoraRelease \
  :autoclick:assembleDebug :autoclick:testDebugUnitTest --console=plain
```

| 检查 | 结果 |
| --- | --- |
| communication-api JVM | 1 test，0 failure/error/skipped |
| communication-zego JVM | 4 tests，0 failure/error/skipped |
| communication-agora JVM | 3 tests，0 failure/error/skipped |
| app zego JVM | 23 tests，0 failure/error/skipped |
| app agora JVM | 23 tests，0 failure/error/skipped |
| autoclick JVM | 71 tests，0 failure/error/skipped |
| 两家 Debug/Release 构建 | 4 APK 均生成；Release unsigned |
| app 两版 Lint | 各 0 errors、154 warnings |
| API/Zego/Agora 模块 Lint | 分别 0 errors、2/6/3 warnings |
| 依赖图隔离 | 两版 debugRuntimeClasspath 均只含所选 SDK，无 FAILED 依赖 |
| APK 隔离 | 四版均解析实际 DEX class definitions 和 native library 路径，另一 SDK 类/native 文件均为 0 |
| 公共业务源码隔离 | app/src/main、accessibilityCore、accessibilityBase 无 im.zego/io.agora SDK 导入 |
| 差异空白检查 | git diff --check 通过 |

APK 检查：Zego Debug/Release 各 461 个 SDK 类、8 个 native 文件；Agora 各 1031 个 SDK 类、82 个 native 文件。native 文件数量含应用公共库；各 APK 的另一家 SDK 类和 native 文件均为 0。

完整路径、大小、SHA-256、native 列表见 `apk-isolation.json`；可运行 `python3 .artifacts/pluggable-communication/inspect_apks.py` 复验。依赖图见 `zego-dependencies.txt` 与 `agora-dependencies.txt`；测试统计见 `test-summary.json`。

## 评审与回归

规格和代码评审发现并纳入回归：凭证首次请求/续期超时须进入失败并清理会话；第二个共享端不能替换已选目标画面；合法 4096 UTF-8 bytes 的特殊字符文本不能因 JSON 扩张而崩溃；无效命令应在状态变更前拒绝。

已有回归涵盖凭证脱敏、SDK generation 失效、声网身份校验、即构定制采集设备开关、取消入会、关闭后旧回调、接收方许可过期、重复命令只派发一次、重连后重新握手、分片完整性与文本/坐标校验。

## 尚未验证

- 无真实 Token 请求、无双机网络连接、无真实手势/首帧/Token 续期验收；JUnit 与构建不能证明真实远程控制可用。
- Release 为未签名 APK，未签名发布。
- 保留即构 3.6.0 native 库的 16KB 对齐警告，未声称所有 16KB 页设备均支持。
- 捕获尺寸改变会结束共享并要求重新授权，不支持旋转后无缝继续。

接入方法、构建命令和用户验收步骤见 `docs/COMMUNICATION_PROVIDERS.md`。
