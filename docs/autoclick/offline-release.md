# AutoClick 1.0.1 离线首版发布

目标：准备可交给首批用户的正式签名离线 APK、随包说明和可核对的发布证据。版本为 `1.0.1`、`versionCode=2`，独立包名沿用 `com.lumostech.autoclick`。首批用户试用不等于应用市场已审核通过。

正式签名离线 APK 与完整分发 ZIP 已生成，开发者资料已确认。当前产物、校验值和验证边界见 [1.0.1 交付记录](release-1.0.1.md)；前期 121 项验证见 [准备记录](verification-2026-11-11.md)，正式密钥见 [签名记录](signing.md)。

## 本版范围

- AutoClick 主包不申请联网和网络状态权限；合并清单会移除依赖声明的这两项权限。
- 无障碍服务保留真实点击所需手势能力及核对目标窗口所需信息访问；仅 AutoClick 覆盖共享配置关闭截图能力，不改变远控主应用的配置。
- 本地数据关闭系统备份和 Android 12+ 的设备迁移；发布审计核对编译后的规则。
- 隐私与权限、使用指南均在本地提供，未授权无障碍时可读。没有新增账号、广告、支付、分析统计或自动更新。
- 正式包不回退到 Debug 签名。未配置正式密钥时，普通 `assembleRelease` 只生成未签名构建，不能交付用户；分发打包脚本会拒绝未签名、Debug 签名、未确认开发者资料或证书与固定指纹不符的包。

## 发布前的确定信息

开发者名称、反馈邮箱放在 `autoclick/src/main/res/values/strings.xml`。发布者已确认使用“黄耿 / huanggeng0607@163.com”，应用内说明与分发资料使用同一信息。打包工具会拒绝“待确认”占位内容。

签名优先沿用已经分发给用户的正式密钥。若从未建立过正式签名，且已确认创建首个正式密钥，运行：

```sh
python3 scripts/init_autoclick_signing.py --private-dir /absolute/private/autoclick-signing
```

脚本拒绝覆盖已有目录、签名配置、公钥证书或固定指纹。私钥和密码只写入权限受限的本地文件，不输出密码；公开证书写入 `autoclick/release-certificate.pem`，SHA-256 写入 `autoclick/release-certificate.sha256`。

**私下备份完整签名目录中的 `.p12` 和 `signing.properties`；云工作区不应作为唯一备份。** 公开分发包不包含私钥、密码或私有配置。丢失正式密钥会影响原包的覆盖升级。

已有密钥时，参照 `autoclick/signing.properties.example` 配置忽略文件 `autoclick/signing.properties`，限制为 `0600`；也可设置四项环境变量 `AUTOCLICK_KEYSTORE_PATH`、`AUTOCLICK_KEYSTORE_PASSWORD`、`AUTOCLICK_KEY_ALIAS`、`AUTOCLICK_KEY_PASSWORD`。公钥证书和固定指纹必须从该正式密钥导出并核对，不使用 Debug 证书，也不通过新建密钥解决覆盖安装失败。

## 构建与打包

使用 JDK 17+、Android SDK 36、Build Tools 和项目 Gradle Wrapper；保持环境现有代理、证书与依赖设置。

```sh
./gradlew :autoclick:testDebugUnitTest :accessibilityCore:testDebugUnitTest \
  :autoclick:assembleRelease :autoclick:assembleDebugAndroidTest :autoclick:lintRelease
python3 -m unittest discover -s scripts/tests -v
python3 scripts/package_autoclick.py \
  --apk autoclick/build/outputs/apk/release/autoclick-release.apk \
  --output autoclick/build/distributions/AutoClick-1.0.1-offline
```

打包脚本从实际 APK 解码清单、权限、无障碍能力、备份规则和开发者信息，核对签名、固定证书指纹；原生库仅允许现有 AndroidX 本地绘图组件，其他原生 SDK 会被拒绝。输出 APK、用户指南、隐私说明、反馈模板、公钥证书、签名验证结果、JSON 审计和 SHA256SUMS，并生成 ZIP。输出目录必须是新目录，避免覆盖旧产物或混入签名文件。

审计记录编译包来源 HEAD 和是否存在未提交改动；未提交构建不能只凭 HEAD 标识来源。`physical_device_acceptance` 只描述本脚本不执行真机验收，当前轮实测结论另见交付记录。

## 首批真机验收

至少在实际目标用户常用的手机品牌上分别核对以下项目，保留具体机型、系统版本、操作步骤和实际结果；未执行的项目标为待验证。

| 项目 | 检查内容 | 当前状态 |
| --- | --- | --- |
| 首次启动 | 断网可读指南和隐私；未授权不执行点击 | 待真机 |
| 权限引导 | 无障碍、悬浮窗、闹钟拒绝后状态明确；返回设置不擅自启用 | 待真机 |
| 录制与试运行 | 录制不透传点击；试运行次数正确；停止控制可达 | 待真机 |
| 定时 | 亮屏解锁的正确页面执行；错过窗口跳过；无重复点击 | 待真机 |
| 应用与显示变化 | 切换目标、旋转、显示尺寸变化后拒绝不匹配执行 | 待真机 |
| 后台及重启 | 省电、进程重建、重启后实际状态；不自动解锁或打开目标 | 待真机 |
| 升级 | 同正式签名更高版本覆盖安装后保留任务、草稿与历史 | 待后续版本 |
| 数据清除 | 删除任务、清空录制、系统清除数据的范围与说明一致 | 待真机 |

## 渠道准备

保持本版实际纯离线特征，优先通过用户可核实来源的签名 APK 开展首批试用。官网提供下载时，网站本身的备案要求单独判断。应用若以后增加互联网服务，需重新核对和完成适用备案。

小米官方提供“无需备案（单机应用）”入口并要求免责承诺函；该入口不免除开发者主体、版权资料、隐私和功能审核。当前小米暂不收录个人开发者身份发布的 APP；其他市场应逐家核对，不能视为同一规则。

- [小米单机应用备案信息指引](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1775)
- [小米暂不收录范围](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1354)
- [小米上架资质要求](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1322)
- [工信部 APP 备案通知](https://www.miit.gov.cn/jgsj/xgj/wjfb/art/2023/art_dd783a581c9644a4aee10afa582811db.html)

商店材料需由真实发布主体准备：名称与版权材料、图标与实际页面素材、功能和权限说明、隐私政策、测试方法及适用的单机承诺函。该文档不代表已提交市场、已有软著或已取得备案豁免审批。用户联系和公开下载发布以已确认名单及实际发布指令为依据。
