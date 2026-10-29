---
name: betteroriginos-dev
description: BetterOriginOS（iQOO Z8 / OriginOS 5 LSPosed 模块）的开发与调试指南。凡涉及本项目的构建安装、新增功能（Feature）、Hook/预览/选择器开发、设置 App UI 修改、真机验证或问题排查，一律先读本 skill——即使用户没有明说"按 skill 来"。
---

# BetterOriginOS 开发指南

LSPosed 模块：Hook `com.android.systemui`（通知中心媒体卡 + 真实预览渲染）与
`com.android.providers.media.module`（照片选择器相册白名单修复）。设置 App 为纯 Java
View 体系（无 androidx），OS5 视觉规范。

先读设计规范 `out/DESIGN-SPEC-1025.md`（token/组件/交互），历史与踩坑细节在 `out/DEVLOG.md`。

## 1. 项目事实

- 代码：`module/src/com/betteroriginos/`；资源 `module/res/`；产物 `module/build/BetterOriginOS.apk`。
- 设备 adb `10AE4Y0MDE000BL`，无密码锁（上滑解锁）；KernelSU + LSPosed。
- 作用域两个：`com.android.systemui`、`com.android.providers.media.module`。
  推荐作用域声明在 `res/values/arrays.xml` 的 `xposedscope`，**加新 scope 时两处都要改**。
- 构建链：`module/build.sh`（aapt2→javac→d8→注入 dex→zipalign→apksigner），无 Gradle 无 androidx。
  路径由脚本位置推导，不要硬编码项目路径（用户会改目录名）。

## 2. 构建 / 安装 / 生效

```bash
cd module && ./build.sh          # 产物 129KB 左右
adb install -r build/BetterOriginOS.apk
# scope 变更才需要：lspctl scope add --user=0 com.betteroriginos <pkg>
# 生效需重启对应进程：kill $(pidof com.android.systemui)   ← 会锁屏，解锁=上滑
```

- 构建失败先看 `build-log.txt`（GBK 编码，用 python 解码再 grep"错误"）。
- 新用到的 Xposed API 必须**先补编译桩**（`module/stubs/de/robv/android/xposed/`），
  否则 javac 报找不到符号；桩只在编译期，运行时由 LSPosed 提供实现。
- Git Bash 传 Windows 工具的路径要 `cygpath -m`；操作 `/system`、`/apex` 的 adb 命令加
  `export MSYS_NO_PATHCONV=1`。

## 3. 新增 Feature（检查清单）

1. `feature/<area>/XxxFeature.java`：id/titleRes/summaryRes/categoryRes/iconRes/scopes/install；
   legacy 配置键在 static 块注册到 Prefs（Prefs 本体不感知具体功能）。
2. `FeatureManager.FEATURES` 注册 → 主页自动出现该应用卡片。
3. `strings.xml` 文案；选项带预览图时给 `iconRes`（下拉菜单自动展示）。
4. LSPosed 加 scope + 重启目标进程；`arrays.xml` 的 xposedscope 同步。
5. Hook 全程 try/catch + 日志，失败回退系统原生行为（Mainline 模块会升级，别写死）。

## 4. 高频坑（每条都真实踩过）

- **Context 错包**：通知/RemoteViews 的 View.getContext 是发起通知应用的
  createPackageContext（如 bbkmusic）。拿 SystemUI 本体上下文用
  `ActivityThread.currentApplication()` 反射（AndroidAppHelper.currentApplication 在此环境为 null）。
- **App 的 su 会话读不到其他应用文件**（KernelSU 命名空间隔离 → ENOENT），且 su 管道污染二进制。
  跨进程传大块数据用 **MediaStore**（App 加 READ_MEDIA_IMAGES，adb pm grant 一次）。
- **App 内 ProcessBuilder 读 su 命令输出必须边读边等**：先 waitFor 会因 >64KB 管道缓冲死锁。
- **PrecomputedText**：真实通知标题不能直接 setText 到别的 TextView，先 `.toString()`。
- **Xposed setResult(null)**：被 hook 方法返回值会被调用方使用时（如 addAll），必须给空集合而非 null。
- **MediaProvider picker 库**：所有 DB 操作必须走 `runWithoutTransaction/runWithTransaction`。
- **Feature.options() 已缓存**（模板方法 buildOptions）；别在子类重写 options() 导致每次 new、引用失配。
- **ListView 无 item margin**：分组卡间距用透明 spacer 行；行级圆角用 bg_item_top/mid/bot。
- **Switch 自定义 thumb/track drawable 必须带 `<size>`**，否则 Switch 测量塌缩成几像素。
- **可点击性即提示**：行点击若不作为开关用途就去掉 clickable——控件职责要单一、明确。

## 5. 真机调试技巧

- 截图 `adb exec-out screencap -p > x.png`；UI 结构 `uiautomator dump` + pull 后 grep bounds。
- 盲 tap 验证前先 dump 拿精确坐标；注意弹窗/IME 会让 dump 失败或 tap 落空。
- 长时间验证前 `adb shell svc power stayon true`（结束时记得恢复 false）；
  kill systemui/media module 必然锁屏，流程：keyevent 224 → 上滑 swipe → 再操作。
- Hook 侧日志：`adb logcat -d | grep BetterOriginOS`；LSPosed 注入确认看
  "loaded <pkg>" 与 "installed feature <id>"。
- 用户手机上可能同时有人在操作——tap 结果异常先截图确认当前界面再继续。

## 6. 文档纪律

功能落地后同步更新：`out/DEVLOG.md`（含踩坑表）、`out/DESIGN-SPEC-1025.md`（token/组件/架构变化）、
本 skill（新坑进 §4）。规范问题以 DESIGN-SPEC 为准，不在这里重复。
