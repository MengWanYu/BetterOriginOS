# BetterOriginOS

面向 vivo OriginOS / Funtouch OS 的 LSPosed 模块：在系统界面里做“接近原生”的观感与行为增强。
模块通过 Xposed 钩子修改 SystemUI、桌面、相机等系统应用，不替换系统文件，可随时关闭。

> 非官方项目，与 vivo 无关联。刷模块有风险，请自行备份并承担后果。

## 功能

- **通知条美化**：OS5 通知几何复刻（连续角、收纳栏间距、浮动一键清除、大图标与文字列）。
- **通知中心媒体卡**：Android 11 原生卡（Ocean）、OS5 原子随身听、Material You 三种样式；
  支持展开/收起态、封面浮雕取色、可拖动进度条。
- **相机快门样式**：把拍照按钮改成 OS5 原子设计环（只改外观，点击热区沿用原生）。
- **桌面动效增强**（实验）：恢复部分机型被 OEM 预配置关闭的打开/退出动画与壁纸缩放，
  并把被系统设置隐藏的“桌面过渡动画”入口放回；模块卡片内可直接选择档位。
- **媒体选择器相册修复**：照片选择器显示全部自建相册。
- **文件管理重命名修复**：解除文件名长度限制。

以上功能按系统版本与机型可用性不同，模块内会显示对应的说明。

## 兼容性

已在以下设备上做过真机验证：

| 设备 | 系统 | 说明 |
|---|---|---|
| iQOO Z8 (PD2314) | OriginOS 5 / Android 15、OriginOS 3 / Android 13 | 通知条、媒体卡、相册修复 |
| PD2357 (V2357A) | Funtouch 14.1 / Android 14 (OS4) | 桌面动效、相机快门、媒体卡 |

要求：

- 已 root（KernelSU / Magisk 等）并安装 **LSPosed** 或兼容框架（如 Vector）。
- 按用途勾选作用域（模块内“作用域”列表可作为参考）：
  `com.android.systemui`、`com.vivo.systemuiplugin`、
  `com.android.providers.media.module`、`com.android.filemanager`、
  `com.android.camera`、`com.bbk.launcher2`、`com.android.settings`。

## 构建

不依赖 Gradle，使用 `aapt2 + javac + d8 + apksigner`。

需要：

- JDK 11+（编译目标仍为 Java 8；d8/apksigner 需要 11+）
- Android SDK：Platform 35 + Build-Tools 35（含 `aapt2`、`zipalign`、`lib/d8.jar`、`lib/apksigner.jar`）
- Python 3（把 `classes.dex` 注入 APK）

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA=/path/to/jdk11+/bin/java       # 可选；默认使用 PATH 中的 java
export JAVAC=/path/to/jdk11+/bin/javac     # 可选
./module/build.sh
# 产物：module/build/BetterOriginOS.apk
```

也可以直接指定 `ANDROID_BUILD_TOOLS` 与 `ANDROID_JAR`，跳过 `ANDROID_HOME` 自动探测。

Demo（OriginUI 复刻库的演示 App）：

```bash
./demo/build.sh
# 产物：demo/build/VivoUILab.apk
```

## 安装

1. 安装构建出的 APK；
2. 在 LSPosed / Vector 中启用模块，并勾选所需作用域；
3. 重启对应系统进程（SystemUI / 桌面）或重启手机；
4. 打开模块 App，按需开启功能与选项。

桌面动效增强修改的是桌面启动时读取的 OEM 预配置，**开启/关闭后需要重启桌面进程**才生效；
“动画档位”写入系统设置，可即时生效。

## 仓库内容

- `module/`：模块源码、资源、编译期 Xposed 桩与构建脚本。
- `vivo-OriginUI/lib`：OriginOS 风格 UI 复刻库（纯 Java View 实现）。
- `vivo-OriginUI/docs`：复刻库的设计/实现笔记。
- `demo/`：复刻库的演示 App。

仓库不包含任何厂商 APK、反编译产物或系统资源文件；所有数值与行为均通过公开接口与
互操作性逆向得到，相关商标归 vivo 所有。

## 许可

[MIT](LICENSE)
