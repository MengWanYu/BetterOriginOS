# BetterOriginOS 交接日志（2026-10-25 · UI 整改前）

> 新对话先读：本文件 + `out/DESIGN-SPEC-1025.md` + `out/DEVLOG.md`。
> 目标：接手 **App UI 设计整改**。Hook/媒体卡/seek 主链路已稳定，不要重写。

## 1. 一句话现状

BetterOriginOS（`com.betteroriginos`）LSPosed 模块 v0.1.0 已安装启用，scope=`com.android.systemui`。
首个功能「通知中心媒体卡」已完成 OS5 原子 / Material You 两套样式、真实 seek（vivo `seekPos` 桥）、封面取色、进度条与四角修复。
设置 App 已重构为 **应用列表主页 → 应用详情** 两级 + Feature 注册表驱动；当前 UI 仍有明显设计缺陷，待整改。

## 2. 设备与环境

- iQOO Z8：PD2314 / V2314A，Android 15（OriginOS 5）。
- SystemUI：`15.0.5.241`；SystemUIPlugin：`15.0.5.240`。
- Root：KernelSU；LSPosed 2.2.1（API 102），`lspctl` 可用。
- 模块包：`com.betteroriginos`；版本 `0.1.0`；scope 仅 SystemUI。
- 旧的 `com.iqoo.os5.mediaprobe` 已卸载。
- 当前配置：legacy `betteroriginos_style=material`、`betteroriginos_enabled=1` 仍在；
  新键 `betteroriginos_media_card_style` / `betteroriginos_feature_media_card` 尚未写入，
  Prefs 做了 legacy 迁移，所以当前卡片仍是 Material You、功能开关为开。
- 当前界面：设置 App 停在应用详情页；手机未锁屏。
- 构建工具：
  - JRE25：`/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre`
  - build-tools 35：`C:/Users/Administrator/Desktop/iqooz8-os5-mod/work/sdk-dl/bt35/android-15`
  - android.jar：`C:/Users/Administrator/Desktop/iqooz8-os5-mod/work/sdk-dl/plat35/android-35/android.jar`
  - 构建脚本：`C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/build.sh`
  - 产物：`module/build/BetterOriginOS.apk`

## 3. 构建 / 安装 / 回滚

```bash
cd "C:/Users/Administrator/Desktop/iqooz8-os5-mod/module"
./build.sh

adb install -r build/BetterOriginOS.apk
LSP="/data/adb/modules/zygisk_lsposed/lspctl"
adb shell "$LSP module enable --user=0 com.betteroriginos"
adb shell "$LSP scope set --user=0 com.betteroriginos com.android.systemui"
adb shell "kill \$(pidof com.android.systemui)"
```

- 构建链：aapt2 编译/链接（生成 R.java）→ javac → d8 → 注入 dex → zipalign → apksigner。
- 回滚：停用模块 / 卸载 APK / 重启 SystemUI；不写系统分区。
- 注意：`adb shell` 是 root；`settings put global` 可直接写。设置 App 写入也走 `su`。

## 4. 当前代码结构

```
module/src/com/betteroriginos
├── core
│   ├── Feature.java          元数据（id/titleRes/summaryRes/categoryRes/iconRes/scopes/options/defaultEnabled/install/isEnabled/option）
│   ├── FeatureOption.java    SWITCH / CHOICE（values、labelRes、iconRes、default）
│   ├── FeatureManager.java   注册表、按包分发、appPackages()/forPackage()
│   └── Prefs.java            双通道配置 + legacy 迁移
├── feature/media/MediaCardFeature.java   媒体卡引擎（含 MediaCard 内部类、样式、seek 桥）
├── hook/MainHook.java        薄入口，按 scope 安装 Feature
└── ui
    ├── MainActivity.java     应用列表主页
    └── AppDetailActivity.java 单应用功能页（Feature 卡、选项、对话框、重启）
```

- 布局：`activity_main.xml`（主页）、`activity_app_detail.xml`（详情）、`item_app.xml`、`item_feature.xml`、`item_option_choice.xml`、`item_option_switch.xml`、`item_choice.xml`、`item_section.xml`。
- 图标/资源：`ic_launcher_foreground` 自适应图标 + `mipmap-*` PNG；`preview_os5` / `preview_m3`；`bg_card` / `bg_icon` / `bg_button` / `bg_logo` / `ic_back` / `ic_chevron` / `ic_music` / `ic_info` / `ic_check`。
- 设计规范：`out/DESIGN-SPEC-1025.md`；开发日志：`out/DEVLOG.md`。

## 5. 配置与运行链（重要）

- 键：
  - 功能开关：`betteroriginos_feature_<featureId>`（"1"/"0"）
  - 选项：`betteroriginos_<featureId>_<optionKey>`
  - 媒体卡：`betteroriginos_feature_media_card`、`betteroriginos_media_card_style`（os5/material）
  - legacy：`betteroriginos_enabled`、`betteroriginos_style`
- 设置页写：本地 SharedPreferences（`settings`）+ `su -c settings put global <key> <value>`。
- Hook 读：`Settings.Global` 优先 → 本地 `XSharedPreferences` 回退 → legacy → 默认。
  - **XSharedPreferences 在 SystemUI 侧曾读不到本地偏好**，不要再只依赖它；保留双通道。
- 修改后需重启 SystemUI；设置页/详情页都有按钮与提示。
- 首次写 Global 会弹 KernelSU 授权。

## 6. 已完成且稳定的部分（不要重写）

- 媒体卡 Hook：`NotificationContentView.setExpandedChild/setContractedChild` → 在 `MediaNotificationView` 上重建。
- 保留原通知的标题/歌手/封面/5 个按钮点击链；来源图标取 MediaSession 包名的 `PackageManager` 图标。
- 隐藏 `NotificationBackgroundView`（alpha=0 / INVISIBLE）解决四角白边。
- 进度条：自定义 SeekDrawable，4dp 底轨 + 10dp 已播放粗段 + 同尺寸圆点；时间 `tnum`/固定最小宽防抖；两端内边距。
- seek：标准 `MediaSession.seekTo` 优先；iMusic 不声明 `ACTION_SEEK_TO` 时回退到
  `com.vivo.musicwidgetmix.service.MusicWidgetMixService` 的 `IMusicWidgetMixService.seekPos(long)`（事务码 15）。
  SystemUI 直接 bind；日志 `widget service connected`、`seekable=true native=false widget=true`。
- 样式：
  - OS5 原子：封面模糊底 + 白蒙层 + 米色卡片 + 深色进度。
  - Material You：封面主色 → 色调渐变卡 + 实心播放键 + 强调色进度。
- 取色/切歌：`dominantHue`/`softBitmap`；320ms 过渡；曲目 key 去重（避免 300ms 重复取色）。

## 7. 当前 App UI 与已知设计缺陷（下一对话重点）

现有界面：
- 主页：品牌头 + “应用”卡片（当前只有“系统界面 com.android.systemui · 1 项功能 · 1 已启用”）+ “关于”（scope/引擎/重启）+ footer。
- 详情：返回箭头 + 应用图标/名称/包名 + 功能卡（通知中心媒体卡开关）+ “卡片样式 Material You >”（打开选择对话框）+ 重启按钮。

### 已知缺陷 / 待整改

1. **详情页分类重复**：应用已是“系统界面”，功能分区却还是“系统界面”。应改为「媒体」：
   - `strings.xml` 增加 `category_media`；`MediaCardFeature.categoryRes()` 返回它。
   - 同步更新 `DESIGN-SPEC-1025.md` 的信息架构示例。
2. **样式选择对话框未验证**：`item_choice` 的视觉、选中态、预览图、AlertDialog 主题、取消/写入流程都需要真机检查。
3. **主页品牌头被 LSPosed heads-up 遮挡**（非 bug，但首屏观感差）；可考虑品牌头下方留白/加载态。
4. **应用图标**：SystemUI 取到的是 Android 机器人默认图标；需要更合适的应用图标/映射（或为系统界面使用定制图标）。
5. **详情页下半屏空洞**：重启按钮之后是大片空白，内容密度/留白需要重新设计（可加页面 footer/说明/功能计数）。
6. **深色模式**未逐页验证：颜色、卡片、对话框、状态栏/导航栏。
7. **设置页写入体验**：每次写 Global 弹 KernelSU 授权；需要合并写入/提示/失败反馈。
8. **应用列表扩展性**：多应用、多分类、多选项时的排版与分组（目前只有 1 个应用）。
9. **应用详情顶栏**：返回箭头/图标/标题/包名的层级与间距；长应用名/长包名截断。
10. **功能卡选项区**：样式行目前是“预览 + 当前值 + chevron”；功能多/选项多时的层级、分隔、滚动需要规范。
11. **关于页**：scope 文案、引擎说明、版本；与详情页重启按钮的职责重复。
12. **视觉一致性**：主页/详情的标题字号、卡片间距、图标容器、section label、按钮宽度；对齐设计规范逐项校准。
13. **无障碍/可点区域**：应用行、返回、开关、选项行 ≥44dp；内容描述。
14. **应用列表空态/加载态**：没有应用/图标加载失败时的占位。
15. **framework 的“选项 >2 个”能力**：当前只有单选对话框；复杂功能需要二级选项页。
16. **开发日志/设计规范**需要补 App 列表整改记录与最终组件规范。

## 8. 下一步建议（按优先级）

1. 先做 **UI 设计评审**：对照 `DESIGN-SPEC-1025.md` 逐页检查主页/详情/对话框；用户明确说“应用 UI 还有很多设计缺陷”。
2. 修“分类重复”（媒体）+ 测样式对话框 + 测新键写入（su/Global）+ 重启后 M3/OS5 切换。
3. 详情的密度/留白、应用图标、深色模式、授权提示。
4. 多应用/多分类排版演练（可在 FeatureManager 临时加一个假 Feature 验证布局，验证完删除）。
5. 更新 `DESIGN-SPEC-1025.md` 与 `DEVLOG.md`（记录本次 UI 整改）。
6. 之后再接第二个功能/第二个作用域应用（Feature 注册表 + strings/图标 + LSPosed scope）。

## 9. 证据与截图

- 主页应用列表：`work/shots/home-applist.png`
- 应用详情：`work/shots/app-detail.png`
- 旧版设置页（品牌/开关/样式预览）：`work/shots/better-settings2.png`
- 通知中心媒体卡（OS5）：`work/shots/v09-user-open.png`、`v10-user-open.png`
- 文档：`out/DESIGN-SPEC-1025.md`、`out/DEVLOG.md`
- Hook 日志：`adb logcat -s BetterOriginOS`
  - `installed feature media_card for com.android.systemui`
  - `restyling style=material ...`
  - `widget service connected` / `seekable=true native=false widget=true`

## 10. 参考

- 旧项目工具/逆向总览：`D:/!vivo去avb开包…OS7/out/工具与逆向参考-总览-1025.md`
- HyperCeiler 设计参考：`C:/Users/Administrator/Downloads/HyperCeiler-main.zip`
- LSPosed framework API 反编译：`work/systemui/lsposed-api/`
- SystemUI/插件 APK 与资源：`work/systemui/`
