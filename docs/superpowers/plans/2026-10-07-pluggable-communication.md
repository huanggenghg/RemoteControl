# RemoteControl 可替换通信供应商实现计划与执行记录

日期：2026-10-07；2026-10-08 继续收尾。

**Goal:** 打包时选择即构或声网，各 APK 仅包含一家 SDK，页面与远程操作业务依赖公共通信契约。

**Architecture:** `communication-api` 定义会话、媒体和控制传输；两家适配模块实现契约；app 通过 `provider` flavor 的同名工厂装配。公共会话协调器管理凭证、连接、渲染及退出，公共协议负责握手、屏幕几何、短期许可、顺序与去重。

**Tech Stack:** Kotlin、AGP 8.11.0、Android SDK 36/minSdk 24、JVM 11、coroutines、ZEGO Express Video 3.6.0、Agora RTC 4.6.4、JUnit 4。

## 授权与范围

用户确认“打包时选择”与设计后，明确授权：“先忽略token的获取，先走实现，后续我补充自己进行功能验证”。据此实现客户端，不新增声网 Token 获取接口或部署服务。保留即构现有接口，并提供两版共用的 CredentialProvider 注入入口。真实凭证和双机功能验收由用户后续完成。

沿用当前工作区，保留已有 autoclick、Token API 和日志相关修改。不 reset、不批量暂存、不自动提交、推送、安装或发布。

设计见 [设计文档](../specs/2026-10-07-pluggable-communication-design.md)；配置和使用方式见 [通信供应商说明](../../COMMUNICATION_PROVIDERS.md)。

## 执行步骤

- [x] 调查基线与现有差异，只修正本任务构建链路中的既有编译阻塞：BuildConfig、Token DTO、OkHttp 日志签名与工具类引用。
- [x] 新建三个通信模块，SDK 类型限制在适配模块内部；公共契约不依赖 app、无障碍模块或供应商 SDK。
- [x] 配置 zego/agora flavor、固定 SDK 版本与声网官方 Maven 源；各变体只依赖对应适配库。
- [x] 迁移即构房间、流、定制采集、渲染、自定义消息和资源释放；关闭硬件摄像头/麦克风并保留定制视频编码。
- [x] 增加声网频道、屏幕共享、视频渲染、RTC data stream 和续期实现；校验服务端 UID 与 Token 身份，不引入 RTM。
- [x] 公共协议支持点击、四向滚动、输入、系统按键；校验版本、会话、目标、许可、顺序和消息 ID；限制分片、队列与等待时间。
- [x] 迁移页面和录屏授权/前台服务；首帧、几何和控制握手就绪后才允许操作；授权停止、捕获尺寸改变、退出和失败统一清理。
- [x] 提供凭证注入和缺失提示，保留即构已有获取路线；不新增声网获取服务，不存储或记录真实 Token。
- [x] 更新两份 CI 工作流，明确两版构建、单元测试、Lint 和 APK 上传路径。
- [x] 添加公共协议、会话生命周期与适配边界回归测试，执行规格和代码评审。
- [x] 修复收尾评审发现并完成最终两版 Debug/Release、测试、Lint 与 autoclick 回归。
- [x] 检查依赖图和 APK 的 DEX/native 内容，记录 SDK 隔离结果及验证边界。
- [x] 保存最终验证记录 `.artifacts/pluggable-communication/validation.md`。

## 验证方式

使用本机 Android Studio JBR 21；安装中的 JBR 25 与 Kotlin 2.0.21 工具有兼容问题，已单独记录。验证包括三个通信模块 JVM 测试、app 两个 flavor JVM 测试和 Lint、两个 flavor 的 Debug/Release APK、autoclick Debug 构建和已有 JVM 回归。检查依赖图、DEX 类描述符及 native 文件，确认不存在另一供应商。

评审回归覆盖：请求/续期超时的终止清理、第二个共享端不能替换已选画面、合法 4096-byte 文本的编码边界、旧回调失效、取消入会、重连重新握手、接收方许可过期与重复消息只派发一次。

## 留给用户的验收

- [ ] 接入有效的声网项目、服务端 UID 和 Token；同一 requestedUserId 续期保持相同身份。
- [ ] 两家分别双机验证首帧、点击、滚动、输入、系统按键、断线重连、Token 续期、退出与再次录屏授权。
- [ ] 在 Android 14+ 验证整屏授权、系统停止共享、尺寸变化后结束并重新授权。
- [ ] 在目标设备验证现有即构 3.6.0 的 native 16KB 页兼容性；本次保留现有 SDK，不因编译通过宣称全面支持。

以上真实设备事项不以 fake、JUnit 或构建结果替代，也不阻碍本次用户已授权的客户端实现交付。
