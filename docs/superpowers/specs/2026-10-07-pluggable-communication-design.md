# RemoteControl 可替换通信供应商设计

日期：2026-10-07；2026-10-08 继续收尾。状态：用户已确认设计，并授权先实现、暂缓 Token 获取与双机功能验证。

## 已确认的范围

app 的屏幕传输、控制消息、会话成员、连接状态和 Token 更新从即构 SDK 中解耦，提供即构与声网两种实现。打包时选择供应商，每个 APK 仅包含一家 SDK。共用界面、远程操作协议和 accessibilityCore 手势执行链路。

新增 `communication-api`、`communication-zego`、`communication-agora` 三个 Android library。app 使用 `provider` flavor 维度的 `zego`、`agora` 版本，依赖通信契约和对应适配器。通过 flavor source set 中同名工厂装配，公共代码不导入供应商 SDK，也不通过运行时反射加载另一实现。

保留 `com.lumostech.remotecontrol` applicationId，两版覆盖安装。同一会话两端必须采用同一供应商；本次不做跨供应商互通、会话中切换、双供应商回退、多人控制或离线指令。

## 边界

- `communication-api`：供应商中立的会话、媒体、消息接口与事件、屏幕几何、凭证、错误和能力上限。允许 Android View/MediaProjection，不依赖 app、无障碍模块或供应商 SDK。
- 两个适配模块：实现会话进退、屏幕发布/播放、消息发送/接收、成员/连接/续期事件、SDK 与采集资源释放。供应商的流 ID、UID、枚举和 Canvas 均留在模块内部。
- app 会话协调器：凭证请求、单次会话身份、连接状态、媒体/控制就绪条件、续期去重、旧回调失效和退出清理。
- app 控制协议：点击、滚动、输入、系统按键、屏幕尺寸与控制握手。SDK 接收回调不再直接解析 JSON 并执行无障碍操作。
- 录屏授权与前台服务仍由 app 协调，适配器负责自身 Surface/VirtualDisplay 或 SDK 屏幕采集。授权停止、页面退出、会话失败统一使旧会话失效；不复用已经消费或失效的录屏授权。
- accessibilityCore、accessibilityBase 的职责和 autoclick 依赖链保持现状。

## 供应商实现

即构版本迁移现有 Express Video 3.6.0 路线：房间、流、定制采集和 custom command。声网采用 RTC 4.x 的频道、屏幕共享、远端视频和 RTC data stream；本次不引入独立 RTM SDK。媒体与消息分别定义接口，今后可以替换消息路线。

屏幕尺寸由统一协议同步，不再依赖 ZEGO stream extraInfo。渲染视图由业务提供，供应商适配器完成绑定；以实际投屏内容区域进行坐标映射，屏幕几何未就绪时不执行坐标操作。

控制协议明确发送者、会话、接收方、消息 ID 和顺序。接收方签发短期操作许可，使用本机单调时钟判断有效期；断线/退出撤销许可并清理未完成消息，重连重新握手。操作不因超时而自动重发。传输提交成功、接收确认和手势派发结果分别记录，不宣称 SDK 返回成功即操作已执行。

## 凭证与配置

兼容现有 `/getZegoToken` 请求和返回结构。两版通过 CredentialProvider 注入 App ID、房间、用户身份与 Token；声网凭证获取由用户后续补充，不新增 Token URL 或后端接口。业务服务端签发凭证并确保 App ID、频道、用户身份一致。客户端不得保存 App Certificate、不得本地签名或使用硬编码临时 Token，不输出 Token 日志。

用户明确要求暂时忽略 Token 获取，先完成实现，后续自行补充并进行功能验证。客户端可独立完成构建、协议与失败路径验证，但真实接通必须有有效配置和服务。此仓库不包含 Token 服务端，不自行假定已有 `/getAgoraToken` 或部署外部服务。

## 验收

分别构建 Zego/Agora Debug 与 Release，运行公共协议、会话及适配边界测试，检查每个变体的依赖与 APK，确保不混入另一 SDK。对 autoclick 做构建和现有单元测试回归。

两家分别进行双机实测：连接、首帧、点击、四向滚动、输入、系统按键、屏幕尺寸变化、断线重连、续期、用户退出与录屏授权停止。覆盖 Android 14+ 的录屏授权、旋转和前台服务生命周期。无双机/有效凭证时明确标记实测未验证，不以 fake、编译或模拟器单端启动代替真实接通。

## 参考

- [声网 Android 接入与 Maven 配置](https://docs.agora.io/en/realtime-media/rtc/get-started-sdk?platform=android)
- [声网 RTC Android 4.x API](https://api-ref.agora.io/en/video-sdk/android/4.x/API/class_irtcengine.html)
- [声网服务端 Token 签发](https://docs.agora.io/en/realtime-media/rtc/build/authenticate-users/deploy-token-server)

## 授权

用户选择“打包时选择”，随后确认模块边界与设计，并明确授权“先忽略token的获取，先走实现，后续我补充自己进行功能验证”。据此执行客户端重构与验证，Token 获取和真实双机验收延期交由用户补充。不自动提交、推送或部署。
