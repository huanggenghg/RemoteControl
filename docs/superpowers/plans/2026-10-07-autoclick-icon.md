# autoClick Icon Implementation Plan

> **For agentic workers:** Use `executing-plans` to implement this plan task by task in this session after the user explicitly confirms the plan.

**Goal:** 将用户选定的 A「指尖点击」替换为 autoClick 的正式应用图标。

**Architecture:** 保留 Manifest 的 `ic_launcher` 和 `ic_launcher_round` 入口。现代 Android 使用矢量自适应图标及独立单色前景，API 24–25 使用各密度 WebP 图标。所有形态沿用同一份手势和圆弧几何，保持视觉一致。

**Tech Stack:** Android VectorDrawable、AdaptiveIconDrawable、WebP、Gradle；本地 Pillow 用于导出和视觉检查。

---

## Task 1: 制作矢量与自适应图标

**Files:**
- Modify: `autoclick/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `autoclick/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `autoclick/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `autoclick/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `autoclick/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [x] 从确认的 A 方案复用手掌轮廓和半圆弧。预览生成脚本为 `/private/tmp/draw_autoclick_icon.py`，若脚本不再存在，以设计文档所指 PNG 和资源几何对照恢复。
- [x] 背景设为纯色 `#126B60`，去掉默认机器人及网格。
- [x] 前景使用 108 × 108 viewport，将 A 方案 1024 × 1024 画布映射到 `(18,18)` 至 `(90,90)`；检查完整前景在以 `(54,54)` 为中心、半径 33 的安全圆内。
- [x] 手掌使用白色填充，半圆弧使用 `#8FE3C5`，圆弧两端为圆角。
- [x] 独立单色前景使用同一几何，两个形状均为白色，背景透明。
- [x] 两个 adaptive icon 的 `monochrome` 引用改为 `@drawable/ic_launcher_monochrome`，保持 foreground/background 资源名。

## Task 2: 更新旧版桌面图标

**Files:**
- Modify: `autoclick/src/main/res/mipmap-mdpi/ic_launcher.webp` and `ic_launcher_round.webp`
- Modify: `autoclick/src/main/res/mipmap-hdpi/ic_launcher.webp` and `ic_launcher_round.webp`
- Modify: `autoclick/src/main/res/mipmap-xhdpi/ic_launcher.webp` and `ic_launcher_round.webp`
- Modify: `autoclick/src/main/res/mipmap-xxhdpi/ic_launcher.webp` and `ic_launcher_round.webp`
- Modify: `autoclick/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` and `ic_launcher_round.webp`

- [x] 使用同一套 A 方案几何，导出普通圆角方形和圆形版本；外部留透明背景。
- [x] 五档密度尺寸依次为 48、72、96、144、192 像素，使用抗锯齿缩放及无损 WebP。
- [x] 将导出结果与矢量前景渲染结果对照，检查指尖圆角、半圆弧端点、居中和留白。

## Task 3: 验证与交付

- [x] 生成圆形、圆角方形、48 像素和单色预览，目视检查无裁切、缺笔或失真。
- [x] 使用 Android Studio 自带 JDK 运行 `./gradlew :autoclick:assembleDebug :autoclick:lintDebug`。期望两个任务完成；记录已有 lint 问题及本次新增问题，修复本次新增问题。
- [x] 如存在可用设备或模拟器，运行 `./gradlew :autoclick:installDebug` 后检查桌面图标并截图；设备不可用则交付离线预览并说明未做设备验证。
- [x] 检查 `git diff --stat` 和资源差异，确认修改范围为上述图标资源及本次文档。
- [x] 交付图标预览、APK 路径和实际验证结果。

## Scope and authorization

该计划不需要新增业务测试，构建、lint 和视觉检查直接验证资源变更。用户已明确确认本计划，已完成资源替换和验证；未提交或发布。
