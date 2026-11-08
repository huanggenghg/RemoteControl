# 远程控制通信供应商

app 通过 `provider` 构建维度选择通信实现。两版共用业务页面、控制协议与无障碍手势，各 APK 仅依赖一家 SDK。

| 版本 | SDK | Debug APK |
| --- | --- | --- |
| zego | ZEGO Express Video 3.6.0 | `app/build/outputs/apk/zego/debug/app-zego-debug.apk` |
| agora | Agora RTC 4.6.4 | `app/build/outputs/apk/agora/debug/app-agora-debug.apk` |

双方安装同一供应商版本。本次统一了控制协议，旧版 APK 与新版不保证互通。applicationId 均为 `com.lumostech.remotecontrol`，两版覆盖安装；保留数据，不通过清数据切换供应商。

## 构建

使用支持 AGP 8.11 的 JDK 17 或更高版本。本次验证使用本机 Android Studio JBR 21；当前最新 Android Studio bundled JBR 25 与 Kotlin 2.0.21 工具存在兼容问题，不能将该问题当供应商 SDK 故障。

```sh
./gradlew :app:assembleZegoDebug :app:assembleAgoraDebug
./gradlew :app:assembleZegoRelease :app:assembleAgoraRelease
./gradlew :app:testZegoDebugUnitTest :app:testAgoraDebugUnitTest
./gradlew :app:lintZegoDebug :app:lintAgoraDebug
```

Release 默认没有签名配置，产物为 unsigned APK。没有自动上传、发布或安装步骤。

## 注入凭证

即构版默认请求 `https://opub.cn/remotecontrol/getZegoToken`，服务端通过新账号的私有配置签发基础 Token04，并返回 AppID。客户端不再固定账号。声网版默认提示“请先接入声网凭证，再发起远程会话”。两版都可以在 `MyApp.onCreate` 中安装业务自有的 `CredentialProvider`，优先于默认获取路线。

接口地址可通过 `-PzegoTokenEndpoint=https://.../getZegoToken` 或环境变量 `ZEGO_TOKEN_ENDPOINT` 覆盖；只接受不含用户信息、查询参数和 fragment 的完整 HTTPS URL。不要在地址中携带密钥。客户端保留部署路径前缀，不会把 `/remotecontrol/` 丢掉。

请求 JSON 为 `userId` 和 `loginRoomId`。响应必须含 `data`、`error.code`、`appId`、`roomId`、`userId`、`expiresAt`（Unix 秒）。客户端校验成功码 SUCCESS/0、Token04、AppID 范围、身份一致和有效期。缺配置 503/NOT_CONFIGURED 会直接显示“即构凭证服务尚未配置”，不会回退旧账号。总请求预算 12 秒，最多两次尝试，只对网络或网关临时故障重试；取消立即传播，业务错误不重试。

后端仓库 RemoteControlService 使用独立 Token 入口，不需要 MySQL。部署、私有参数和回滚方式见该仓库 `docs/TOKEN_SERVICE.md`。`/remotecontrol/health` 只表示本地配置就绪；真实账号与双机登录、操作、续期需另外验收。

```kotlin
CommunicationCredentials.provider = CredentialProvider { roomId, requestedUserId, role ->
    // 在这里调用你自己的凭证服务，并将响应转换为 SessionCredentials。
    credentialsFromYourService(roomId, requestedUserId, role)
}
```

这里的 `credentialsFromYourService` 代表业务自有的覆盖方法。返回 `com.lumostech.communication.SessionCredentials`：

- `appId`：供应商项目 ID。即构为十进制字符串；声网为 32 位十六进制字符串。
- `roomId`：本次传入的六位协助码，必须保持一致。
- `userId`：即构可使用 requestedUserId；声网必须是服务端分配、与 Token 绑定的非零 unsigned 32-bit UID 的十进制字符串，不可随机选择另一 UID 或对 UUID 哈希。
- `token`：本次身份的有效凭证，非空；只保存在当前内存会话中。

续期会再次调用同一接口，请根据 roomId/requestedUserId/role 保持相同 SDK userId 和 appId。可在业务内建立 requestedUserId 到声网 UID 的映射。接口中不要返回另一身份的 Token；协调器会拒绝身份改变。

不要将 App Certificate、签名密钥或 Token 写入源码、BuildConfig、manifest、测试 fixture 或日志。客户端不签发 Token。不需为了构建提供真实凭证。

## 模块与替换方式

- `communication-api`：会话、媒体、控制消息和事件契约。仅 Android 类型，不依赖供应商 SDK、app 或无障碍模块。
- `communication-zego`：即构房间、流、自定义采集与 custom command。
- `communication-agora`：声网频道、外部 MediaProjection 屏幕共享、视频渲染与 RTC data stream。未引入 RTM。
- `app/src/zego/` 与 `app/src/agora/`：同名 `CommunicationFactory` 装配当前实现。
- app 的 `SessionCoordinator`：凭证、连接、续期、控制就绪及退出协调。
- app 的 `protocol/`：供应商中立的远程操作与屏幕几何协议。

增加第三家时，实现公共接口、新增适配库和 flavor 工厂，并将对应依赖放在该 flavor 中；页面无需增加 SDK 分支。不要用运行时 if/反射加载多个 SDK。

## 操作与生命周期

控制端收到屏幕几何、实际视频首帧和接收方短期操作许可后才允许操作。点击以投屏内容矩形映射到原屏幕，忽略黑边。操作信封校验版本、会话、对端、许可、顺序及消息 ID；结果仅表示手势已派发，不能保证目标应用完成了业务动作。

接收方按本机单调时钟签发 3 秒许可，不依赖两台设备时钟同步。断线/退出撤销许可和未发送队列，重连重新握手；非幂等操作不会自动重发。消息分片完整组装后再执行，分片大小、待组装数量、期限和发送队列均有上限。输入文本 UTF-8 最多 4096 bytes。

录屏授权由系统取得，前台服务就绪后才交给适配器采集。退出、系统停止共享或捕获尺寸改变会结束当前共享并使授权失效；重新开始需重新授权。Android 14+ 优先请求整屏共享；若系统仍提供裁剪/单应用尺寸，拒绝把它映射成全屏点击。当前版本不承诺投屏中旋转后无缝继续，尺寸变化后重新发起。

## 验证边界

需要分别用两台 Android 设备验证两家版本的首帧、点击、四向滚动、输入、系统按键、断线重连、Token 续期和退出/再次授权。真实新账号参数和两台设备具备后完成验收；构建、JUnit 和 APK 隔离通过不等于真实网络可用。

保留的即构 3.6.0 native 库有 Android 16KB 对齐警告；不能据本次编译通过声称已支持所有 16KB 页设备。升级现有 SDK 属于后续独立验证事项。

官方参考：[声网接入](https://docs.agora.io/en/realtime-media/rtc/get-started-sdk?platform=android)、[Android RTC API](https://api-ref.agora.io/en/video-sdk/android/4.x/API/class_irtcengine.html)。
