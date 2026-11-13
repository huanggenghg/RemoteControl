# AutoClick 离线首版准备验证记录

日期：2026-11-11（UTC）。本轮准备 `1.0.1` / `versionCode=2`。

**当前状态：开发者资料已确认，正式签名离线 APK 与完整分发包已交付。** 发布者为“黄耿 / huanggeng0607@163.com”，签名继续沿用首次建立的密钥。最终产物与核验见 [1.0.1 交付记录](release-1.0.1.md)。以下保留首次准备和候选阶段的证据，没有提交应用市场或联系试用用户。

## 首次准备阶段的代码与产物

构建时基线 HEAD：`800829167f3df3ff63ee5248bae070af963345cd`。当时修改尚未提交，该 HEAD 本身不代表完整构建来源；后续代码归档以 `autoclick-v1.0.1` 标记，原产物和历史审计保持不变，见 [归档记录](archive-1.0.1.md)。

- Release 未签名 APK：`autoclick/build/outputs/apk/release/autoclick-release-unsigned.apk`，10,890,502 字节。
- SHA-256：`446f9daa047d5ab0f1be641b18472e0afb404f94ee48f9234a727459e4d2777a`。
- Android 测试包构建成功，未连接设备运行。
- 上述首次候选构建未签名。之后的签名候选 SHA-256 为 `043587cbb4c4b9e67912f2ebec234c1cb719208b64d8e70a3fcf7153eb7b4bef`；当时开发者资料尚未填写，分发脚本正确阻止打包。补齐资料后的最终产物采用新哈希，见交付记录。

## 构建与验证

使用本环境既有 JDK 17、Android SDK 36、Build Tools 35.0.0 和 Gradle 8.13；采用本环境的官方仓库初始化配置，未修改项目共享仓库设置。

```sh
source /workspace/.cloud-onboarding/env.sh
./gradlew --init-script /workspace/.cloud-onboarding/official-repositories.init.gradle \
  :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest \
  :autoclick:assembleRelease :autoclick:assembleDebugAndroidTest :autoclick:lintRelease \
  --console=plain --no-daemon --max-workers=2
python3 -m unittest discover -s scripts/tests -v
```

- Gradle：`BUILD SUCCESSFUL`，232 个任务执行。
- AutoClick：94 项单元测试通过，零失败、错误、跳过。
- accessibilityCore：16 项单元测试通过，零失败、错误、跳过。
- 分发审计：11 项 Python 测试通过，包含异常权限、截图能力、备份、Debug 包、组件保护、AAPT 重命名资源路径和本地绘图库边界。
- Release Lint：零错误、37 条警告。警告包括既有 API/资源/依赖/文本规范项及低版本忽略 `canTakeScreenshot` 属性的提示；没有把本轮 Release 计数冒充历史 Debug 的相同基线。
- `git diff --check` 通过；Python 发布脚本语法检查通过。

## 从实际 APK 核对

审计读取 APK 的实际资源表，再核验 AAPT 优化后的资源，未用源码配置代替包内配置。

- 包名 `com.lumostech.autoclick`，版本 `1.0.1` / `2`，minSdk 24、targetSdk 36。
- 没有 `INTERNET` 或 `ACCESS_NETWORK_STATE` 权限；剩余声明为定时、开机恢复、悬浮窗、WorkManager 的唤醒/前台服务及 AndroidX 的签名级内部接收权限。
- AutoClick 的无障碍截图能力为 false；手势和窗口核对能力保留。共享远控配置未改。
- 系统备份关闭，云备份和设备迁移的九类存储域均被排除。
- 隐私与权限、使用指南确实嵌入 APK。优化后路径分别为 `res/Oo.txt`、`res/gM.txt`，配置为 `res/az.xml`、`res/4j.xml`；分发工具按实际资源表解析，不依赖固定文件路径。
- 原生库只有四个 ABI 的 `libandroidx.graphics.path.so`，均为本地绘图组件；各 ELF 的 LOAD 对齐为 16,384 字节，APK 的 16 KiB ZIP 对齐检查通过。这不代表已在真实 16 KiB 设备验收。

## 签名工具验证

首次准备阶段在隔离临时目录中生成一次测试密钥，验证私钥与配置文件 `0600`、目录 `0700`、公钥证书与固定指纹一致、日志不输出密码、重复运行拒绝覆盖。临时测试目录已删除。后续按用户要求建立正式密钥，私密备份核对通过，详见签名记录。

另对实际 Release APK 临时使用环境 Debug 密钥签名，分发工具正确拒绝并且没有创建公开输出；测试文件随后删除。随后正式签名候选通过了唯一证书、固定指纹和包级核对；资料仍为占位内容时，分发工具正确阻止输出。最终按发布者确认的资料重建并通过完整打包。

## 尚需完成

1. 发布者将已生成的密钥和密码配置私密备份另存到自己的设备；后续继续沿用该密钥。
2. 按 [首批用户说明](first-users.md) 在真实手机上验收权限、录制、停止、定时、后台与重启表现。本环境 `adb devices -l` 无设备，未安装模拟器运行时且无 `/dev/kvm`，本轮没有执行设备 UI/手势用例。
3. 如要提交应用市场，按所选渠道准备发布主体及相关资料；当前交付为离线试用包。

机器可读记录与本轮日志位于 [.artifacts/autoclick-offline-release](../../.artifacts/autoclick-offline-release/preparation-audit.json)。首批真机结果应单独补充，不能用历史模拟器结果替代。
