# AutoClick 正式签名

2026-11-11，按发布者要求创建首个正式签名密钥，并通过 Gradle Release 构建及实际 APK 签名验证。后续版本继续沿用该密钥。

| 项目 | 值 |
| --- | --- |
| 格式 | PKCS#12 |
| 算法 | RSA 3072 / SHA256withRSA |
| 别名 | `autoclick` |
| 自签证书名称 | `CN=AutoClick` |
| 有效期截止（UTC） | 2054-03-29 06:52:01 |
| 证书 SHA-256 | `b7bd7c3aec77266ce511bbc126481c3e3e18568255ff1e0ba84dc45ed5108e06` |

公开证书为 `autoclick/release-certificate.pem`，固定指纹为 `autoclick/release-certificate.sha256`。指纹根据 DER 证书计算，与 Android APK 签名校验输出一致。

私钥和密码配置保存在仓库外的私密目录，目录权限 `0700`，文件权限 `0600`。本地构建配置 `autoclick/signing.properties` 已由 Git 忽略。私密备份同时包含密钥库与密码配置，仅由发布者保管；请在当前云环境之外另存备份。公开 APK 分发包只携带公钥证书，不携带私钥或密码配置。

2026-11-13 归档时已复核本环境私密备份的内容、权限及证书对应关系；发布者确认尚未下载到独立设备，外部备份仍待完成。此状态不记为已完成云环境之外的备份。

实际 `autoclick-release.apk` 已通过 SDK 的 APK Signature Scheme v2 校验，唯一证书与上述指纹一致；离线权限、截图能力、备份规则和 16 KiB ZIP 对齐再次通过检查。完整结果见 [.artifacts/autoclick-offline-release/signing-update.json](../../.artifacts/autoclick-offline-release/signing-update.json)。

发布者已确认使用“黄耿 / huanggeng0607@163.com”，并沿用此密钥重建。最终 APK 和 ZIP 已通过完整分发审计，见 [1.0.1 交付记录](release-1.0.1.md)。上方签名更新记录保留的是当时尚缺发布者资料的候选阶段。
