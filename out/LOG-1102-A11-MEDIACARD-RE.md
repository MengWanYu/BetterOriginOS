# LOG-1102 — OriginOS Ocean (Android 11 / sdk30) 音乐播放卡片 逆向规格

目标设备：vivo Y7s（`IBR8GEMFJNBECIFA`，OriginOS Ocean / Android 11 / sdk30，序列号 IBR8GEMFJNBECIFA）。
目标：为把该机的"音乐播放卡片"样式 **1:1 移植**到 OriginOS 3(A13)/OriginOS 5(A15) 提供纯代码层逆向规格。本文件只描述 A11 现状与移植落点，不改模块源码。

## 0. 证据来源与工具（全部静态反编译，禁止观感猜测）

拉取并解包的 APK（存于 `work/y7s-a11/`）：
- `SystemUI.apk` = `/system/priv-app/SystemUI/SystemUI.apk`（102,711,197 B，3 dex：`sysui_dex/classes{,2,3}.dex`）
- `framework-res.apk` = `/system/framework/framework-res.apk`（45,084,433 B）
- `framework.jar` = `/system/framework/framework.jar`（32,787,926 B，取 `android.app.Notification$MediaStyle`）
- `VivoMusicWidgetMix.apk` = `/system/custom/app/VivoMusicWidgetMix/VivoMusicWidgetMix.apk`（9,099,362 B）
- `bbkmusic.apk` = `/data/app/~~TFbvJg6A4.../com.android.bbkmusic-.../base.apk`（74,619,092 B）

工具：`work/sdk-dl/bt35/android-15/aapt2.exe`（`dump resources`/`dump xmltree` 读 dp 与类名/行号）、`dexdump.exe`（类清单 `work/y7s-a11/sysui_classes.txt`，20970 项）、jadx CLI 1.5.5（单类反编译到 `work/y7s-a11/tmp/*.java`）。

关键落盘证据文件：
- 布局 XML 树：`work/y7s-a11/xml_media_view.txt`、`xml_fw_template_media.txt`、`xml_fw_template_big_media.txt`
- 资源表：`work/y7s-a11/sysui_res.txt`、`fw_res.txt`；布局名清单 `systemui_vivo_layouts.txt`、`mix_layouts.txt`、`bbk_layouts.txt`
- 反编译类：`work/y7s-a11/tmp/`：`MediaNotificationProcessor.java`、`NotificationMediaManager.java`、`MediaControlPanel.java`、`PlayerViewHolder.java`、`MediaCarouselController.java`、`MediaData.java`、`MediaDataManager.java`、`IlluminationDrawable.java`、`LightSourceDrawable.java`、`MediaArtworkProcessor.java`、`MediaStyle.java`
- 设备只读取证与"未能取到实时卡片"记录：`work/y7s-a11/device-evidence.txt`

> **实时缺口**：本次未能抓到"正在放歌时的媒体通知"（`dumpsys media_session` 显示 `have 0 sessions`、`numWithMediaSession=0`），也未能对展开态卡片做 `uiautomator dump` 抓 bounds。原因：解锁阶段发现焦点应用是 `com.android.settings/.Settings$FingerpintAndFaceSettingsActivity`（指纹设置流程 + IME 已弹），系统级 `lock_pattern_type/lock_password_type` 均为 null，`input text 114514` 落到了该 Settings 输入框而非 keyguard bouncer；为避免向指纹流程继续误输入或触发锁定计时，按约束立即停止交互、熄屏、转纯静态路线。设备最终状态：`mWakefulness=Asleep`、`mStayOn=false`、`screen_brightness=20`、`screen_off_timeout=15000`、电量 21%（USB 充电中）。凡需真机放歌才能补的点在 §9 标注。

---

## 1. 实现载体判定（决定移植落点在哪个进程/ClassLoader）

A11 的"音乐播放卡片"实际是**两条渲染链并存**，都跑在 `com.android.systemui` 进程、SystemUI 的 ClassLoader 里：

**载体 A — 通知栏收起态：AOSP 着色 MediaStyle RemoteViews**
- 由播放器 `com.android.bbkmusic` 发一条 `android.app.Notification$MediaStyle` 通知。证据：`bbk_dex/classes.dex` 二进制含引用串 `android/app/Notification$MediaStyle`（`grep -ao` 命中）；`NotificationChannel{mId='com.android.bbkmusic.BBKMUSIC', mName=播放控制...}`（`device-evidence.txt`）。
- bbkmusic 的资源里只带 AOSP 模板拷贝（`notification_template_media`/`_big_media`…，见 `bbk_layouts.txt`），**没有 vivo 自研的媒体通知 RemoteViews 布局**；SystemUI 里也**没有任何 `vivo.*music/media` 通知类**（`vivo.*superx` 全是锁屏出行/物流上下文卡：`com.vivo.keyguard.presenter.superx.processor.SuperXActionFlight/Train/Film/Express/Taxi…`，无 music）。→ 结论：收起态卡片 = framework 标准模板 `notification_template_material_media.xml`（root `com.android.internal.widget.MediaNotificationView`，tag="media"，`xml_fw_template_media.txt`）。[确定度高]

**载体 B — 展开态 / QS / 锁屏：AOSP 原生 MediaControlPanel（media_view）**
- SystemUI 内含完整 AOSP 媒体播放器包 `com.android.systemui.media.*`：`MediaControlPanel`、`PlayerViewHolder`、`MediaCarouselController`、`MediaScrollView`、`IlluminationDrawable`、`LightSourceDrawable`、`MediaData(Manager)`、`SeekBarViewModel/Observer`、`MediaViewController`。
- `PlayerViewHolder.Companion.create()` 明确 `inflater.inflate(R.layout.media_view, …)`（`PlayerViewHolder.java:235`）；`MediaCarouselController` `inflate(R.layout.media_carousel, …)` 并把 `PlayerViewHolder` 的 TransitionLayout `addView(...)` 进 `mediaContent`（`MediaCarouselController.java:423/441`），基座是 AOSP `NotificationCarouselController` 的 `UniqueObjectHostView`——即媒体通知**行内展开的卡轮播**。[确定度高]

**载体 C — 桌面/锁屏音乐 widget（另一套，独立进程）**
- `com.vivo.musicwidgetmix` = `VivoMusicWidgetMix.apk`：桌面 2x2/3x2 与锁屏音乐组件（`view_music_double_widget`/`view_music_triple_widget`、`layout_lock_screen_v1/v2`、`music_card_*`，见 `mix_layouts.txt`）。这是"负一屏/桌面"音乐组件，模块通过其 `MusicWidgetMixService` 取进度（`MediaCardFeature.java` 绑定 `com.vivo.musicwidgetmix.service.MusicWidgetMixService`），**不是通知栏卡片本体**。[确定度高]

> 移植落点结论：A11 通知栏音乐卡 = **SystemUI 进程内的 AOSP MediaStyle + media_view(MiniControlPanel) 管线**；1:1 还原应钩 `com.android.systemui` ClassLoader（与现有 `MediaCardFeature` 一致，见 §6）。

---

## 2. 封面（专辑图）排版规格表

### 2a. 收起态（MediaStyle `right_icon`，framework 模板）
| 项 | 值 | 证据 |
|---|---|---|
| 承载 View | `@android:id/right_icon`（ImageView，root `MediaNotificationView` 首子） | `xml_fw_template_media.txt:9-13`；`MediaStyle.bindLargeIcon()`→`setImageViewIcon(R.id.right_icon, mN.mLargeIcon)` `MediaStyle.java:2608-2609` |
| 尺寸 | `@dimen/notification_right_icon_size` = **36dp**（low_ram 变体引用同值） | `fw_res.txt` `0x010501eb dimen/notification_right_icon_size = 36.000000dp` |
| scaleType | `CENTER`（ordinal 6），collapsed `adjustViewBounds=true` | `xml_fw_template_media.txt:11-13` |
| 位置/边距 | 右侧；`right_icon_container` 显隐；文本 `line1`/`text` 的 `marginEnd = iconSize(36) (+contentMargin)` 由 `calculateMarginEnd()` 预留 | `MediaStyle.java:2562-2599` |
| 大图画册上限 | `notification_media_image_max_width/height` = **280/140dp**（low_ram 100/100dp）；`media_notification_expanded_image_max_size`=**94dp**，`margin_bottom`=20dp | `fw_res.txt` 对应 dimen |
| 圆角 | framework 模板不裁剪 right_icon（跟随 64dp large-icon 视觉） | [假说] A11 Material right-icon 不强制圆角；真机 bounds 未采 |
| 无封面 | largeIcon==null → `right_icon_container` 置 GONE（不显示） | `MediaStyle.bindLargeIcon()/bindLargeIconAndReply()` `MediaStyle.java:2606,2566` |

### 2b. 展开态原生卡（media_view `album_art`，SystemUI）
| 项 | 值 | 证据 |
|---|---|---|
| 承载 View | `R.id.album_art`（ImageView） | `xml_media_view.txt:74-77`；`PlayerViewHolder.java:62` |
| 尺寸 | width=height=`@dimen/qs_media_album_size` = **52dp**（收起态与展开态同值） | `xml_media_view.txt:76-77`；`MediaControlPanel.loadDimens()` `mAlbumArtSize=R.dimen.qs_media_album_size` `MediaControlPanel.java:79-82` |
| 圆角 | `mAlbumArtRadius = getThemeAttr(android.R.attr.dialogCornerRadius)`（运行时解析主题值） | `MediaControlPanel.java:80` |
| 裁剪/缩放 | `ViewOutlineProvider.setRoundRect(0,0,52,52,radius)` + `albumView.setClipToOutline(true)`；`scaleDrawable()` 先按长短边把 bounds 撑到 ≥52dp 再居中 offset → 视觉 **centerCrop** | `MediaControlPanel.java:63-67,106-107,266-285` |
| 左/上边距 | 由 ConstraintLayout 约束（root 50% 竖向 `center_vertical_guideline`）；未硬编码 margin | `xml_media_view.txt:12-17` |
| 占位列宽 | 无固定列，ConstraintLayout 定位 | 同上 |
| 无封面 | `mediaData.getArtwork()==null` → `setVisibleAndAlpha(...album_art, false)`（收起+展开都隐藏）；应用图标缺失回落 `R.drawable.ic_music_note` | `MediaControlPanel.java:143-154` |

> **展开态 vs 收起态封面差异**：收起态=36dp right_icon（RemoteViews，CENTER，无圆角裁剪）；展开态=52dp album_art（原生，dialogCornerRadius 圆角 + centerCrop）。二者尺寸/圆角/裁剪行为都不同。[确定度：值高；"收起态是否也圆角"需真机 bounds 补]

---

## 3. 取色算法（逐行伪代码 + 证据）

**存在两套取色**，输入都是专辑图，但用途/结果不同。

### 3a. 收起态着色（SystemUI `MediaNotificationProcessor.processNotification`）— 通知行背景
文件 `work/y7s-a11/tmp/MediaNotificationProcessor.java`，常量（line 16-23）：
```
BLACK_MAX_LIGHTNESS=0.08f  WHITE_MIN_LIGHTNESS=0.9f
MIN_SATURATION_WHEN_DECIDING=0.19f  POPULATION_FRACTION_FOR_DOMINANT=0.01f
POPULATION_FRACTION_FOR_WHITE_OR_BLACK=2.5f  POPULATION_FRACTION_FOR_MORE_VIBRANT=1.0f
MINIMUM_IMAGE_FRACTION=0.002d  RESIZE_BITMAP_AREA=22500 (=150*150)
```
伪代码（逐行对应）：
```
icon = notification.getLargeIcon()                       // L52
if !notification.isColorizedMedia():                     // L56  ← 门控
    color = R.color.notification_material_background_color; return  // L84
w,h = icon.intrinsic; if w*h>22500: s=sqrt(22500/(w*h)); w*=s; h*=s  // L57-63  面积归一到 ~150²
bmp = drawable→ARGB_8888 bitmap                          // L64-67
pb = Palette.from(bmp).setRegion(0,0,w/2,h)  // 仅用左半幅
      .clearFilters().resizeBitmapArea(22500)            // L176-178  generateArtworkPaletteBuilder
bgSwatch = findBackgroundSwatch(pb.generate())           // L69
bgColor  = bgSwatch.rgb                                  // L70
   // findBackgroundSwatch(L157-174): 取 dominant；若 dominant 是白/黑(L>=0.9 或 <=0.8)
   //   则在所有 swatch 里选 population 最大的非黑白 swatch（除非 dominant 太占优 /2.5）
pb.setRegion(0.4w,0,w,h)                                 // L71  前景用右 60%
if !isWhiteOrBlack(bgSwatch.hsl): addFilter( |hue-bgHue|>10 && <350 )  // L72-80 排除同色
addFilter(排除白黑)                                       // L81
fgColor = selectForegroundColor(bgColor, pb.generate())  // L82,95-100
   // isColorLight(bgColor)?  则从 {darkVibrant,vibrant,darkMuted,muted,dominant} 选，兜底 #FF000000
   // 否则  从 {lightVibrant,vibrant,lightMuted,muted,dominant} 选，兜底 #FFFFFFFF
   // selectVibrantCandidate/MutedCandidate 按 population 与 hsl[1] 饱和度(>0.19)择优   // L102-151
builder.setColorPalette(bgColor, fgColor)                // L82  → SystemUI 用此色作通知背景
builder.setLargeIcon( ImageGradientColorizer.colorize(icon, bgColor, isLayoutRtl) )  // L86
   // 给专辑图本身叠一层渐变（提升文字对比），非对整卡做模糊
```
- **输入 bitmap 来源/尺寸/线程/缓存**：来源=通知 largeIcon；Palette 前把面积归一到 22500(~150²)（`resizeBitmapArea`），并在 `processNotification` 里再按 sqrt 预缩（L57-63）。该方法由 SystemUI 通知异步处理链（AsyncProcessor/Job）在**后台线程**执行。明暗判定用 `com.android.internal.graphics.ColorUtils`/`ContrastColorUtil.isColorLight`。无显式 LRU，每次处理即时算。

### 3b. 原生播放器卡背景（`MediaDataManager.computeBackgroundColor`）— media_view 背景色
文件 `MediaDataManager.java:534-554`（跑在 `@Background Executor`，结果经 `foregroundExecutor` 回主线程；`MediaDataManager.java:293,341,102-113`）：
```
swatch = MediaNotificationProcessor.findBackgroundSwatch(
             MediaNotificationProcessor.generateArtworkPaletteBuilder(artwork).generate())
rgb    = swatch ? swatch.rgb : -1                        // 复用左半幅 22500 palette
hsl    = ColorUtils.colorToHSL(rgb)
if hsl.L < 0.05 || hsl.L > 0.95: hsl.S = 0               // 极亮/极暗去饱和
hsl.S *= 0.8                                             // 饱和度降 20%
hsl.L  = 0.25                                            // 亮度强制固定为 0.25（永远偏暗）
bg     = ColorUtils.HSLToColor(hsl)
```
- 因此**原生卡背景恒为深色**（L=25%），文字/图标固定用白色系（见 §4），无"明暗翻转"。
- 无封面兜底色 = **0xFF444444**（`loadMediaDataInBgForResumption` 中 `computeBackgroundColor(null)` 分支传入的默认 `-12303292`=0xFF444444，`MediaDataManager.java:339`）。
- 背景叠层：**非图片模糊**。`R.drawable.qs_media_background` = `IlluminationDrawable`（`xml`：`cornerRadius=?dialogCornerRadius`, `highlight=15`→0.15）。`IlluminationDrawable.draw()` 只是 `drawRoundRect`；`setTintList→setBackgroundColor→animateBackground()`：把背景色 HSL 亮度 ±0.15 生成 `highlightColor`，以 370ms `FAST_OUT_LINEAR_IN` 渐变，并把 `highlightColor` 下发给各 `LightSourceDrawable`（按钮/chip 的 Material tonal-spot 按压发光）。`IlluminationDrawable.java:61-67,149-192,203-222`。**取色输入 bitmap 来源**：`MediaMetadata.METADATA_KEY_ART`→`METADATA_KEY_ALBUM_ART`→icon bitmap→icon URI(`loadBitmapFromUri`)→通知 `getLargeIcon()`；上限 `@dimen/config_mediaMetadataBitmapMaxSize` = **320dp**（`MediaDataManager.java:374-400`；`fw_res.txt`）。
- 锁屏背景大图另走 `MediaArtworkProcessor.processArtwork(Context,Bitmap)`：单槽 `mArtworkCache` Bitmap + `mTmpSize Point`，`clearCache()` 在元数据变化时清（`MediaArtworkProcessor.java:14-45`，方法体 jadx 未完全反编译；`NotificationMediaManager` 经 `ProcessArtworkTask`(AsyncTask, 后台) 驱动，用于 `BackDropView` 锁屏虚化背景，非通知卡本体）。

---

## 4. 控件与排版规格表（原生 media_view 为主）

来源 `xml_media_view.txt`（`res/layout/media_view.xml`，MediaControlPanel/PlayerViewHolder）：

| 控件 | id | 尺寸/样式 | 颜色/字体 | 证据行 |
|---|---|---|---|---|
| 上一/下一/播放暂停… | `action0..action4` ImageButton，`@style/MediaPlayer.Button`(0x7f130124) | **48/48/52/48/48 dp**（action2 中间 52dp=播放暂停） | 图标来自 MediaAction | `xml_media_view.txt:49-73`；`MediaControlPanel.ACTION_IDS` `:38` |
| 进度条轨道 | `media_progress_bar` SeekBar | maxHeight=`@dimen/qs_media_enabled_seekbar_height`=**3dp**（禁用态 `qs_media_disabled_seekbar_height`=1dp）；paddingTop/Bottom **16dp**；splitTrack=false；clickable；style=@android Widget.ProgressBar.Horizontal | progressTint=`@color/media_seekbar_progress` **#c0ffffff**；progressBackgroundTint=`@color/media_disabled` **#80ffffff**；thumbTint=`@color/media_primary_text`(白) | `xml_media_view.txt:116-128` |
| 曲名(标题) | `header_title` TextView | singleLine | textSize **16sp**，color `@color/media_primary_text`=白(0x0106000b→@android:color/white)，fontFamily=@0x0184021d(未解析) | `xml_media_view.txt:138-145` |
| 艺名 | `header_artist` TextView | singleLine | **14sp**，color `@color/media_secondary_text`=**#99ffffff** | `xml_media_view.txt:146-153` |
| 应用名 | `app_name` TextView | singleLine | **14sp**，color 白 | `xml_media_view.txt:129-137`；`MediaControlPanel.java:155-157` |
| 应用图标 | `icon` CachingIconView | `@dimen/qs_media_icon_size`=**16dp** 方形 | tint 白；无图标回落 `ic_music_note` | `xml_media_view.txt:154-158`；`MediaControlPanel.java:149-153` |
| 时间(已播/总) | `media_elapsed_time`/`media_total_time`（父 `notification_media_progress_time` FrameLayout） | 左右两端 | **14sp** 白；gravity start|center_vertical / end|center_vertical | `xml_media_view.txt:18-40` |
| 输出切换 chip | `media_seamless`(LinearLayout) + `media_seamless_image`(16dp) + `media_seamless_text`(14sp) + `media_seamless_fallback`(ic_cast_connected 16dp) | paddingStart/End **12dp**，paddingTop/Bottom **6dp**，bg `qs_media_seamless_background`，fg `qs_media_seamless`；border `media_seamless_border`=#26ffffff | 图标/文字白 | `xml_media_view.txt:78-115`；`MediaControlPanel.java:158-203` |
| 竖向参考线 | `center_vertical_guideline` | percent **0.5** | — | `xml_media_view.txt:12-17` |
| 选项(更多) | include `qs_media_panel_options`(`qs_media_controls_options`) | 默认 `visibility=gone`(2) | — | `xml_media_view.txt:159-161`；`PlayerViewHolder.java:101` |

**文字/图标颜色跟随**：原生卡恒深色底→文字/图标用固定白色系 alpha 阶（primary=100% 白、secondary=60% 白#99、seekbar=75% 白#c0、disabled=50% 白#80）；禁用/恢复态整体 alpha `DISABLED_ALPHA=0.38`（`MediaControlPanel.java:39,181`）。**收起态（MediaStyle）文字色**则由 3a 的 `selectForegroundColor` 依背景明暗在黑/白间翻转（`setColorPalette`），非固定。

**收起态（MediaStyle）控件**（`xml_fw_template_media.txt`/`xml_fw_template_big_media.txt`）：标题/正文用 `notification_template_part_line1`+`notification_template_text`（margin start 16dp、top 46dp、bottom 20dp、min content height 39dp）；内联按钮 `media_actions`(marginStart 10dp)，每按钮 `@dimen/media_notification_action_button_size`=**48dp** 方形、padding 8dp、marginEnd 2dp、style `Widget.Material.Button.Borderless.Small`；收起 3 键、展开(`bigMediaNarrow`) 5 键。

---

## 5. 展开 / 收起两态差异

| 维度 | 收起态 | 展开态 |
|---|---|---|
| 渲染体 | MediaStyle RemoteViews `notification_template_material_media`（`MediaNotificationView` tag="media"） | 原生 `media_carousel`(MediaScrollView) + 每页 `media_view`(MediaControlPanel) |
| 封面 | `right_icon` 36dp，CENTER，无裁剪圆角 | `album_art` 52dp，dialogCornerRadius 圆角 + centerCrop |
| 背景色 | `MediaNotificationProcessor` Palette dominant(左半幅) → `setColorPalette(bg,fg)`，可明可暗，文字随对比度翻转 | `MediaDataManager.computeBackgroundColor` → HSL(L=0.25) **恒深色** + IlluminationDrawable 高亮渐变 |
| 按钮 | 3 内联 48dp | 5 个（48/52 中间），MediaPlayer.Button |
| 进度条 | 细进度线由 `MediaNotificationView` 绘制（`notification_media_seekbar_container` ViewStub） | SeekBar 3dp，白系 tint，两端时间 14sp |
| 输出切换 chip | 无 | `media_seamless`（无 device 时显 fallback cast 图标） |

收起→展开：`MediaControlPanel.bind()` 用 `MediaViewController` 的 collapsed/expanded 两套 `ConstraintSet`（`setVisibleAndAlpha` 控制 album_art/media_seamless/actionN），`TransitionLayout` 做形态过渡（`MediaControlPanel.java:130-232`）。

---

## 6. 与 OS3 / OS5 落点映射

对照只读参考：`work/systemui/out-src/`（OS5 反编译：`MediaCarouselController.java`、`NotificationBigPictureTemplateViewWrapper.java`、`KeyguardMusicRemoteManager.java`、`IslandMusicRemoteManager.java`、`IMusicWidgetMixService.java`）、`G:\os3-ref\jadx/`（OS3：`ControlCenterMediaPlayerView/Controller.java`）、`G:\os3-ref\os3-dexdump-f.txt`。

| OS | 通知栏媒体卡可达? | 载体类/资源 | 可钩落点（进程/ClassLoader） |
|---|---|---|---|
| **A11（本表）** | 可达 | 收起=framework `MediaStyle`/`notification_template_material_media`；展开=SystemUI `com.android.systemui.media.MediaControlPanel`+`media_view.xml`+`MediaCarouselController`；取色=`MediaNotificationProcessor`/`MediaDataManager` | `com.android.systemui`（priv-app SystemUI.apk ClassLoader） |
| **OS5 (A15)** | 可达（已知） | 同名 AOSP `com.android.systemui.media.*`（out-src 已含 `MediaCarouselController`）；vivo 行 wrapper `NotificationBigPictureTemplateViewWrapper` | 钩 `com.android.systemui.statusbar.notification.row.NotificationContentView.setExpandedChild/setContractedChild`（现有 `MediaCardFeature.java:298-333` 即此法）+ `notification.stack/row.dispatchDraw` sweep（`:362-417`） |
| **OS3 (A13)** | 不可达（MediaStyle 被 vivo 从列表收走/降权；A11 侧已见 `post_frequency…demoted`，同源趋势） | 播放器 = QS 网格 134dp 磁贴 `com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView`（`G:\os3-ref\jadx/`） | 落点在 **`com.vivo.systemuiplugin` ClassLoader**（另一进程/插件），钩 `ControlCenterMediaPlayerView`（现有 `MediaCardFeature` 的 `PLUGIN_PKG`/`QsPlayerTile.install` 分支 `:271-289`） |

取色 API 跨版本：`androidx.palette.graphics.Palette` + `com.android.internal.graphics.ColorUtils`/`ContrastColorUtil` 在 A11–A15 均存在（`MediaNotificationProcessor.java:10-11`），但 A12+ Material You 改写过 dominant/vibrant 选择与背景合成，需按 §3 阈值**在模块内自带一份算法**，不能依赖目标系统类的新语义。

---

## 7. 移植可行性与风险

1:1 还原 A11 卡片到 A13/A15，缺什么：
- **资源常量缺**：A12+ 的 `media_view.xml`/`qs_media_album_size`/`media_seekbar_progress` 等可能改名或改值（OS5 已重构为 BigPicture wrapper）。必须在模块内**自带规格常量表**（§8），不可读目标 `R.dimen.*`。
- **取色语义缺**：A12/13/15 的 `MediaNotificationProcessor` 背景算法（Vibrant/Material You）与 A11 不同；A11 的"左半幅 dominant + 右60% 前景 + L=0.25 恒暗"要**照 §3 自实现**（现有 `MediaCardFeature` 已用 `LinearGradient`/`LayerDrawable` 手绘，见其 import，但阈值需对齐本表）。
- **落点差异**：A15 通知行可达→沿用 `NotificationContentView` 注入即可 1:1；**A13 通知栏不可达**→只能把该卡搬到 QS 磁贴/控制中心（`ControlCenterMediaPlayerView`），而 134dp 磁贴几何 ≠ A11 卡片几何，1:1 还原在 A13 **只能做视觉近似，布局尺寸必然变形**（风险最高项）。
- **封面裁剪**：centerCrop 依赖 `ViewOutlineProvider`+`clipToOutline`（A11+ 可用）；A13 plugin 进程需确认 API 与主题 `dialogCornerRadius` 取值。
- **chip/更多**：A11 有 `media_seamless`（输出切换）与 `qs_media_panel_options`；A13/A15 plugin 未必有对应服务，移植时这两块可缺省隐藏。
- **通知被降权**：A11 已现 `bbkmusic … demoted`（`device-evidence.txt`），A13 更严重；即便样式钩上，触发条件（通知存在且不被静音）需先解决。

## 8. 规格常量表建议（后续实现直接引用）

```
// —— 收起态（MediaStyle right_icon）——
A11_MEDIA_RIGHT_ICON_DP            = 36     // notification_right_icon_size
A11_MEDIA_CONTENT_MARGIN_START_DP  = 16     // notification_content_margin_start
A11_MEDIA_CONTENT_MARGIN_END_DP    = 16     // notification_content_margin_end
A11_MEDIA_CONTENT_MARGIN_TOP_DP    = 46     // notification_content_margin_top（含 header 偏移）
A11_MEDIA_CONTENT_MARGIN_DP        = 20     // notification_content_margin（bottom）
A11_MEDIA_MIN_CONTENT_HEIGHT_DP    = 39     // notification_min_content_height
A11_MEDIA_ACTION_BTN_DP            = 48     // media_notification_action_button_size（收起3键/展开5键）
A11_MEDIA_ACTION_PAD_DP            = 8      // notification_material_media_action padding
A11_MEDIA_ACTION_MARGIN_END_DP     = 2
A11_MEDIA_INLINE_ACTONS_MARGIN_START_DP = 10 // media_actions marginStart
A11_MEDIA_EXPANDED_IMG_MAX_DP      = 94     // media_notification_expanded_image_max_size
A11_MEDIA_BIG_IMG_MAX_W_DP = 280 / _H_DP = 140   // notification_media_image_max_{w,h}（low_ram 100/100）

// —— 展开态（原生 media_view / MediaControlPanel）——
A11_MEDIA_ALBUM_SIZE_DP            = 52     // qs_media_album_size
A11_MEDIA_ALBUM_RADIUS             = ?android:attr/dialogCornerRadius（运行时主题值）
A11_MEDIA_BTN_SMALL_DP             = 48     // action0/1/3/4
A11_MEDIA_BTN_CENTER_DP            = 52     // action2（播放/暂停）
A11_MEDIA_SEEKBAR_HEIGHT_DP        = 3      // qs_media_enabled_seekbar_height（禁用 1）
A11_MEDIA_SEEKBAR_VPAD_DP          = 16     // SeekBar paddingTop/Bottom
A11_MEDIA_ICON_SIZE_DP             = 16     // qs_media_icon_size / qs_seamless_icon_size
A11_MEDIA_PANEL_PADDING_DP         = 16     // qs_media_padding
A11_MEDIA_CHIP_HPAD_DP             = 12 / _VPAD_DP = 6 / _IMG_MARGIN_END_DP = 8
A11_MEDIA_TITLE_SP                 = 16     // header_title
A11_MEDIA_SUBTITLE_SP              = 14     // header_artist / app_name / 时间
A11_MEDIA_DISABLED_ALPHA           = 0.38f

// —— 颜色（固定白色系，深色底）——
A11_MEDIA_COLOR_PRIMARY            = 0xFFFFFFFF  // media_primary_text（@android:color/white）
A11_MEDIA_COLOR_SECONDARY          = 0x99FFFFFF  // media_secondary_text
A11_MEDIA_COLOR_SEEKBAR_PROGRESS   = 0xC0FFFFFF  // media_seekbar_progress
A11_MEDIA_COLOR_DISABLED           = 0x80FFFFFF  // media_disabled
A11_MEDIA_COLOR_SEAMLESS_BORDER    = 0x26FFFFFF
A11_MEDIA_COLOR_DIVIDER            = 0x1D000000
A11_MEDIA_COLOR_BG_FALLBACK        = 0xFF444444  // computeBackgroundColor(null) 兜底

// —— 取色阈值 ——
A11_PALETTE_RESIZE_AREA        = 22500        // ~150²，Palette.resizeBitmapArea
A11_PALETTE_BG_REGION          = (0,0,w/2,h)  // 背景取左半幅
A11_PALETTE_FG_REGION          = (0.4w,0,w,h) // 前景取右60%
A11_BLACK_MAX_LIGHTNESS        = 0.08f
A11_WHITE_MIN_LIGHTNESS        = 0.90f
A11_MIN_SATURATION_WHEN_DECIDE = 0.19f
A11_POP_FRAC_FOR_DOMINANT      = 0.01f
A11_POP_FRAC_FOR_WHITE_BLACK   = 2.5f
A11_MIN_IMAGE_FRACTION         = 0.002
A11_NATIVE_BG_LIGHTNESS_LOCK   = 0.25f        // 原生卡背景固定亮度
A11_NATIVE_BG_SAT_SCALE        = 0.8f         // 饱和度 ×0.8
A11_NATIVE_BG_DESAT_IF         = L<0.05||L>0.95 → S=0
A11_ILLUMINATION_HIGHLIGHT     = 0.15f        // qs_media_background highlight=15/100
A11_ILLUMINATION_ANIM_MS       = 370
A11_METADATA_ART_MAX_SIZE_DP   = 320          // config_mediaMetadataBitmapMaxSize（取色输入上限）
```

## 9. 必须等真机放歌才能补的缺口

1. **实时通知的 RemoteViews 布局名**：`adb shell dumpsys notification --noredact` 看媒体通知的 `SecondaryView=/primaryView` 的 `pkg/layout`——最终确认收起态用 framework `notification_template_material_media` 还是 vivo 覆写模板（现结论靠"无 vivo 媒体通知类 + bbkmusic 引用 MediaStyle"推得，[假说]，需真机坐实）。
2. **展开态是否走 media_view**：真机 tap 展开媒体通知，`uiautomator dump` 看 resource-id 是 `album_art`/`media_progress_bar`（原生）还是 `right_icon`/`line1`（RemoteViews），并抓各控件真实 bounds（校验 36dp/52dp/48dp 落到该机的 px）。
3. **封面圆角/位置实测**：`dialogCornerRadius` 在 Ocean 主题的具体 dp（A11 默认值随 vivo 主题可能≠2dp）、right_icon 的实际 marginEnd。
4. **isColorizedMedia 实际取值**：确认 Ocean 上 bbkmusic 媒体通知是否真的走着色分支（`setColorPalette` 生效→背景随封面变），以及明暗翻转阈值在 vivo 覆写后是否仍是 `ContrastColorUtil.isColorLight`。
5. **通知降权影响**：A11 已见 bbkmusic `demoted`，需真机确认媒体通知在默认状态下是否出现在列表/展开是否可达。

> 补测条件（受 OLED/电量约束）：先 `screen_brightness 20`+`screen_off_timeout 15000`+`svc power stayon false`；用 `am start -n com.android.bbkmusic/.WidgetToTrackActivity` 或 `media` 会话起播后 `input keyevent 126` 播放；抓完立即 `keyevent 223` 熄屏；确认结束时 `stay_on_while_plugged_in=0`、亮度/超时不残留异常值。

---

# 10. 主线补充：收起态文字色的真身链（含交叉验证抓到的移植 bug）

追加时间 2026-11-02 夜。素材来自 `work/y7s-a11/framework.jar`（jadx 单类）与已解出的 MediaStyle。

## 10.1 ensureColors 的完整分支（此前 §3a 只写了 setColorPalette，没写文字色校正）
证据 `work/y7s-a11/tmp/MediaStyle.java:2473-2516`：
```
backLum = calculateLuminance(bg); textLum = calculateLuminance(fg); cr = calculateContrast(fg, bg)
backgroundLight = (backLum > textLum && satisfiesTextContrast(bg, BLACK))
               || (backLum <= textLum && !satisfiesTextContrast(bg, WHITE))
if cr < 4.5:
    secondary = backgroundLight ? findContrastColor(fg, bg, true, 4.5)
                                : findContrastColorAgainstDark(fg, bg, true, 4.5)
    primary   = changeColorLightness(secondary, backgroundLight ? -20 : +10)
else:
    primary   = fg
    secondary = changeColorLightness(fg, backgroundLight ? +20 : -10)
    if contrast(secondary, bg) < 4.5:
        secondary = backgroundLight ? findContrastColor(secondary, bg, true, 4.5)
                                    : findContrastColorAgainstDark(secondary, bg, true, 4.5)
        primary   = changeColorLightness(secondary, backgroundLight ? -20 : +10)
```
`satisfiesTextContrast(bg, fg) = calculateContrast(fg, bg) >= 4.5`（`ContrastColorUtil.java:422-424`）。

## 10.2 三个被误以为"简单"的原语，其实都在感知色空间上做二分
| 原语 | 真实实现 | 证据 |
|---|---|---|
| `findContrastColor` | 在 **CIE-LAB 的 L\*** 上向 **0（黑）** 二分 15 次、步长阈值 1e-5，返回 `LABToColor(low,a,b)` | `ContrastColorUtil.java:198-225` |
| `findContrastColorAgainstDark` | 在 **HSL 亮度**（AndroidX 标度 [0,1]）上向 **1.0（白）** 二分 15 次，返回循环结束时写回变量的那个色 | 同 249-274 |
| `changeColorLightness` | **LAB**：`L* += amount`，夹到 [0,100] | 同 329-335 |
| `calculateLuminance` | 线性化 sRGB → XYZ 的 Y / 白点 Y（= 0.2126R+0.7152G+0.0722B） | 同 413-415、474-478 |
| `calculateContrast` | WCAG 比值；**半透明前景先 composite 到背景上** | 同 417-419、480-495 |
| `findAlphaToMeetContrast` | 只在 alpha 上二分，且是从当前 alpha **往上加到 255**（不透明输入时是恒等） | 同 227-247 |

⇒ AOSP 的二分是"先把候选色写回 fg/bg，再比较"，所以**末端允许留欠差**：
交叉验证里实测到 `contrast(secondary,bg)` 最低约 3.5（不是理想化的 ≥4.5）。
移植要么复现这个行为，要么明确偏离——我们选择复现，并把验收断言写成"校正一定不比原来差"。

## 10.3 交叉验证抓到的移植 bug（已修）
`A11Palette.colorToHsl` 的饱和度分母写成了 `2f - delta`，正确是 `2f - cMax - cMin`
（AndroidX `colorToHSL` 在 L>0.5 时的公式）。影响面：**所有** A11 背景/前景计算（`computeCardBackground`
的饱和 ×0.8、`findContrastColorAgainstDark` 的 HSL 二分都吃这个值）。
发现方式不是看代码，而是 `work/a11_crosscheck.py`：把模块真实编译产物 `A11Palette.class` 用
`work/tmp-re-a11/A11Probe.java` 跑 3005 组输入，与独立参考实现逐值比对，修前"整条颜色跑偏"、
修后最大通道累计偏差 primary=4 / secondary=3 / card=1（float32 vs float64 舍入量级）。

## 10.4 仍然只能等真机的部分（不编数）
- A11 收起态标题/正文的 **sp 与行位**：framework 模板的 `notification_template_part_line1` 尺寸没取到代码级数值；
  现实现沿用模块既有排版，标 `[待真机核]`。
- `dialogCornerRadius` 在 Ocean 上的实际 dp（A11 侧是主题属性，运行时才解析得出）。
- 真机放歌时 `dumpsys notification --noredact` 的 primary/secondary RemoteViews 布局名与 `isColorizedMedia` 实测值
  （§9 的缺口，代理当时未能解锁取到）。

---

# 11. 主线更正：§6/§7 关于 OS3 的落点判断作废

代理 C 的独立取证（`out/LOG-1102-OS3-MEDIACARD` 系 → 实为 `out/LOG-1102-OS3-MEDIA-SHADE-RE.md`）推翻了本报告 §6 的前提：

- **OS3 解锁态下拉面板本来就显示 MediaStyle 通知行**。运行时证据（本机既有日志，只读复核）：
  `work/z8os3-live/20261028-1408-stack.log` 内 `media bind n=1 cls=com.android.internal.widget.MediaNotificationView pkg=com.android.bbkmusic`、
  `restyling style=os5 MediaNotificationView title=…` ⇒ `NotificationContentView.setContracted/ExpandedChild` 在 OS3 命中。
- 列表管线里唯一的 vivo 自研过滤器是 `KeyguardNotificationManager$1.shouldFilterOut()`，只在锁屏态
  （`StateUtils.mState==1`）生效，i音乐 10001/10002 另在黑名单里；AOSP 的 `Utils.useQsMediaPlayer()` 被焊死为 `false`。
- ⇒ §6"OS3 只能搬 QS 磁贴、1:1 只能近似"改为：**OS3 与 OS5 用同一个 `a11` 风格分支落到通知行**，
  本模块已按此实现（`MediaCardFeature` 的 `expanded` 两态 + `A11Palette`）。
- 仍然成立的差异：OS3 的媒体行**默认就是展开态**（`bigMediaNarrow`），所以收起态着色链在 OS3 上
  可能较少被触发；这属行为差异，不是不可达。
- 已同步删除 `MediaCardFeature.supportNoteRes` 里基于旧前提写的说明（不留错误提示）。

## 12. 收起态排版真实数值（把 §9 的一条 `[待核]` 关掉）
`notification_template_part_line1.xml` 的 `textAppearance=@0x01030385`
= `TextAppearance.DeviceDefault.Notification.Title`，parent `TextAppearance.Material.Notification.Title`
(`@0x01030203`) 带 `textSize=@dimen/notification_title_text_size`、`fontFamily="sans-serif-medium"`；
`notification_title_text_size = 14.0sp`、`notification_subtext_size = 12.0dp`、
`notification_right_icon_size = 36dp`、`notification_action_list_height = 60dp`
（取证命令：`aapt2 dump xmltree --file res/layout/notification_template_part_line1.xml work/y7s-a11/framework-res.apk`
+ `aapt2 dump resources work/y7s-a11/framework-res.apk`）。
⇒ 本报告 §4 的 16/14sp 属**展开态** media_view 的值，不能套到收起态；模块实现已按两态分开。

## 13. Y7s 真机放歌取证（02:10，§9 的缺口按条销账）

前提说明：这台机的 PIN 我按用户授权试过 2 次都没过（每次 `deviceLocked=1` 未变），
**已停止继续试**以免触发 vivo 的锁定惩罚；所以以下证据全部走"不需要解锁"的通道
（`dumpsys notification --noredact` / `cmd media_session dispatch play` / framework-res 实读），
不用截图。放歌是靠 `monkey -p com.android.bbkmusic 1` 拉起 + `cmd media_session dispatch play`
（`state=PlaybackState{state=3}` 确认真在播），屏幕全程熄着。

取证文件：`work/y7s-a11/notif-playing.txt`（`adb exec-out dumpsys notification --noredact`）。
可重跑的核对：`python work/y7s_a11_evidence_check.py`（11 条断言，`--selftest` 7 个反例全红）。

| §9 的缺口 | 实读结论 | 出处 |
|---|---|---|
| 真实 RemoteViews 布局名 | **app 没自定布局**：`contentView=null bigContentView=null headsUpContentView=null`，模板由系统给；`android.template=android.app.Notification$MediaStyle` | notif-playing.txt:266-268、281 |
| `isColorizedMedia` 实值 | **`mColorized=true`**（但 `flags=0xa` 里没有 FLAG_COLORIZED 位 ⇒ 上色是 NMS 侧决定的，不是 app 打的 flag）。这条直接决定"走 MediaNotificationProcessor 的取色链"是否成立，现已成立 | :254、:324 |
| 封面实际尺寸 | `android.largeIcon=Icon(typ=BITMAP size=200x200)` ⇒ 取色输入是 200×200，经 `RESIZE_BITMAP_AREA=22500` 正好折成 **150×150**（不是我原先假设的 512） | :288 |
| 各控件 bounds | 不用 bounds 也能定死：`xml_media_view.txt` 里 action0..4 的 `layout_width` 依序 **48/48/52/48/48dp**（中间 52=播放键），收起态 `notification_right_icon_size=36dp` | media_view 反编译 + fw_res 0x010501eb |
| `dialogCornerRadius` 实值 | `config_dialogCornerRadius = **2.000000dp**`（0x010500a6），且 `Theme.DeviceDefault.NoActionBar` 的 `dialogCornerRadius` 就是 `@dimen/config_dialogCornerRadius`（SystemUI 用这套）。`Theme`/`Theme.Holo*` 里是 0dp，那不是要跟的主题 | fw_res.txt :9081 区段 |
| 收起态按钮数 | `android.compactActions=[2,3,0]`（暂停/下一首/桌面歌词），`actions=5` ⇒ 展开 5 键、收起 3 键，和 A11MediaSpec 一致 | :294-297 |

**由此改掉的一处实现**：`MediaCardFeature` 原先用宿主主题的 `android.R.attr.dialogCornerRadius`
当 A11 封面圆角。宿主（OS5/OS3 的 DeviceDefault）这个值比 2dp 大得多，等于"顺手把 A11 卡美化掉"，
违反 1:1。现在 `A11MediaSpec.ART_RADIUS_DP = 2f` 固定，宿主值只在
`a11 card spec ... radius=.. hostCorner=..` 里做对照打印；验证器新增断言
「radius 必须等于 2dp×density」+ 反例「radius 跟了宿主主题」。

**仍未取到（不编）**：收起/展开两态在屏幕上的真实 bounds（需要解锁后拉下通知栏）。
但布局 XML + 主题 dimen + dumpsys 三项已经把尺寸、圆角、色链、按钮数定死，
剩下的只是"逐像素复核"，留给用户在场的验证轮次。

## 14. 由"200x200 实测"逼出来的一处取色偏差（已修，已锁）

§13 拿到真实封面尺寸是 200×200 之后，回头核对 A11 的取色输入，发现我的移植**少了一步**：

A11 实际是两步（都在 Y7s 反编译里）：
1. `MediaNotificationProcessor.java:57-63`：先按**整张图**面积 22500 缩放，取整用 `(int)` 强转（向下）
   ⇒ 200×200 → 150×150，再用 `drawable.setBounds/draw` 画进 ARGB_8888。
2. `Palette.java:486-494`：如果位图被缩过，把 `region` 按同一比例换算
   —— `left/top` 用 `floor`、`right/bottom` 用 `ceil` 并夹到缩放后的边界；
   `Palette.java:551-559` 再把**只有 region 内**的像素交给 `ColorCutQuantizer`。

我原先写的是"对 region 自己做面积归一"：200×200 的左半幅是 200×100=20000 ≤ 22500 ⇒ 完全不缩，
于是喂进量化器的是 20000 个像素，而 A11 是 **11250** 个（75×150）。
后果不是"颜色差一点"这么轻：`Palette` 的 `maxPopulation`、
`MINIMUM_IMAGE_FRACTION`（`population/22500`）都按像素数算，网格不同 → 胜出 swatch 可能整个换人。

修法：`A11Palette.generate` 改成先算缩放后的整图尺寸，再把 region 按 A11 的 floor/ceil/夹紧换算，
最后按缩放网格回采样源图（`scaledRegion()` 纯算术抽出，便于离线跑字节码）。
验收：`work/a11_sampling_selftest.py`（6 组用例 + 2 条 Y7s 像素数硬锁 11250/13500 + `--negative`
拿"旧实现"当反例，证明检查真能锁住）。已挂进 `work/a11-card-gate.sh` 第 0 步。

残留近似（写清楚，不含糊）：A11 的第 1 步是 `Canvas.draw(Drawable)`（双线性缩放），
我这里是对源位图做最近邻回采样。**同一网格、同一像素数，个别像素值可能差 1-2 级**；
在 5-bit/通道（32 级）的量化直方图里，这只影响极少数边界像素的归属，
不换人则色一致，换人则差异会体现在 `a11 palette` 日志的 bg/fg 上——所以那条日志必须带
`artwork=` 与像素数，装机时能一眼看出网格是否还是 11250/13500。

## 15. 又一层：六族"选择"用的是 androidx Target 打分，不是我照 support-v7 写的老规则

§14 修的是"喂给量化器的像素"，这轮修的是"量化完之后怎么选"。
把 Y7s 里的 `androidx.palette.graphics.Target` 也反编译出来（`work/y7s-a11/tmp/Target.java`），
才拿到六个族真正的界与权重：

| 族 | 饱和度 [min, target, max] | 明度 [min, target, max] |
|---|---|---|
| lightVibrant | 0.35 / 1.0 / 1.0 | 0.55 / 0.74 / 1.0 |
| vibrant | 0.35 / 1.0 / 1.0 | 0.30 / 0.50 / 0.70 |
| darkVibrant | 0.35 / 1.0 / 1.0 | 0.00 / 0.26 / 0.45 |
| lightMuted | 0.00 / 0.3 / 0.4 | 0.55 / 0.74 / 1.0 |
| muted | 0.00 / 0.3 / 0.4 | 0.30 / 0.50 / 0.70 |
| darkMuted | 0.00 / 0.3 / 0.4 | 0.00 / 0.26 / 0.45 |

- 打分（`Palette.java:239-241`）：`0.24*(1-|sat-targetSat|) + 0.52*(1-|light-targetLight|) + 0.24*(pop/dominantPop)`，
  **sat 与 light 都是 0..1 标度**（`ColorUtils.RGBToHSL`）；本模块 `Swatch.lightness()` 是 0..100，
  混用标度就是选错族 —— 这类错在真机上不报错、只"配色有点怪"。
- 族的评估顺序与互斥（`Palette.java:391-396` + `generateScoredTarget`）：
  lightVibrant → vibrant → darkVibrant → lightMuted → muted → darkMuted，
  每个 target `isExclusive=true`，**已被选中的 rgb 不再参与后面的族**。
- 前景色（`MediaNotificationProcessor.java:95-151`）：明底用 light 族、暗底用 dark 族，
  先 vibrant 两两 PK（群体大者胜）、再 muted 两两 PK（`sat * popA/popB > satB ? A : B`），
  都没有才退 dominant，且要过 `pop/22500 > 0.002`（≈45 像素）的门槛；
  候选存在但比 dominant 小时还有一条覆写 —— **参考里是整数除法** `cand.pop / dom.pop < 0.01`，
  等价于"候选比 dominant 小"就换 dominant（前提是 dominant 够艳 sat>0.19）。照抄，不"顺手改成浮点"。
- 背景块（`MediaNotificationProcessor.java:157-174`）：空 palette 回**白色 Swatch**而不是 null；
  dominant 是白/黑时改挑"非白非黑里最大的那块"，但**只有 `dominant.pop / 候选.pop <= 2.5` 才真的换人**；
  色相排除 filter（±10°）**只在背景非黑非白时**才加，黑白/白始终要剔。

我原先的实现是 support-v7 时代的 `sat*population` 打分 + "按顺序取第一个非空族"，
上面这些门槛/互斥/比例/整数除法一条都没有 —— 由 `work/a11_selection_selftest.py`
（反射跑模块字节码 + 独立参考，6 组用例覆盖白底换人 / 黑白争位 / 暗底走 dark 族 /
候选不足退 dominant / 同色互斥 / 45-46 像素门槛）锁住，`--negative` 挪动饱和度下界与群体门槛会报红。

**一条我自己搞错的判断（03:20 更正）**：我先前"驳回"了审查代理关于无封面兜底色的意见，
理由是 `MediaDataManager.java:339` 是 `-12303292 = 0xFF444444`。
读完整条链才发现 `:339` 是 **resumption 路径**；展开卡走的 `computeBackgroundColor()`（`:534-553`）
在 `artworkBitmap == null` 时把 `rgb = -1`（白）继续送进同一条 HSL 变换 ⇒ **`0xFF404040`**。
代理是对的，我的复核对错了位置：常量要找**它那个调用点**，不能在同名公式的别处找一个能对上号的数。
现模块 `computeCardBackground(null)` 按 A11 用白色走同一条变换，`0xFF444444` 只留作 resumption 路径的记录值；
验证器加了「`artwork=false` ⇒ base 必须是 `ff404040`」的断言与反例。

同理，收起态无封面时 A11 **压根不着色**（`processNotification` 只在 `largeIcon != null` 时跑）。
我们的卡是自绘的、必须填底，于是取 Y7s 框架默认通知底色
`notification_material_background_color = #ffffffff`（fw_res 里只有这一个配置，无夜间变体），
夜间分支是本机适配而非 A11 原值 —— 已在代码注释里标明，别当成 1:1 结论。

## 16. 展开态的尺寸/颜色：从"引用了某个 dimen"升级到"那个 dimen 等于多少"

`xml_media_view.txt` 里按钮、封面、进度条、文字颜色大多是 `@0x7f07xxxx` / `@0x7f06xxxx` 引用。
**只证明"引用了某个资源"不算 1:1 证据**，必须用 `work/y7s-a11/sysui_res.txt`
（aapt2 dump 的 SystemUI 资源表）把 id 解成真实值。解出来后与 `A11MediaSpec` 全部对上：

- `@0x7f0704d8 → dimen/qs_media_album_size = 52.0dp`（封面宽高都指它，`clipToOutline` + `dialogCornerRadius=2dp`）
- `@0x7f0704c4 → dimen/qs_media_enabled_seekbar_height = 3.0dp`（禁用态 `1.0dp`），
  且它是 SeekBar 的 **`maxHeight`**（不是 layout_height）—— 我们自绘轨道时要照这个语义来
- `@0x7f0704c5 → dimen/qs_media_icon_size = 16.0dp`（`CachingIconView` 的宽高 = 应用来源图标）
- 字号：布局里 **16sp 恰好 1 个节点（标题）**、**14sp 5 个节点**（艺名/时间等）
- 颜色：`media_secondary_text=#99ffffff`、`media_seekbar_progress=#c0ffffff`、`media_disabled=#80ffffff`，
  主文字 `media_primary_text` 指向 `@android:color/white`（`#ffffffff`）

这 8 条已经进 `work/y7s_a11_evidence_check.py`（当时 19 条断言 / 10 个反例；§19 之后是 25 / 14），
反例分别把 album 改 64dp、seekbar 改 6dp、secondary 改 `#60ffffff` 验证会报红。
⇒ 从此"展开态排版"的判定不再依赖目视，也不再依赖对 XML 的粗读。

## 17. 取色的源头是量化器：A11 用的是 median-cut，不是"直方图取前 N"

`ColorCutQuantizer`（`work/y7s-a11/tmp/ColorCutQuantizer.java:58-318`）的真实语义，四条最容易做错：

1. **桶**：像素压成 5bit/通道（`red<<10 | green<<5 | blue`，8→5 是右移 3 位），
   建 32768 格直方图。**这一版不看 alpha**（早期实现没有 `ALPHA_THRESHOLD`）。
2. **过滤在桶上**：`shouldIgnoreColor(桶号)` 用**桶的代表色**（5bit 还原成 8bit）算 HSL 决定去留，
   整桶留或整桶丢。按像素过滤是另一回事：桶里混了不该要的颜色时结果会整体偏。
3. **<=maxColors 就一桶一色**：`Palette.Builder` 默认 `mMaxColors = 16`（`Palette.java:379`），
   MediaStyle 没改它；非空桶数不超过 16 时**不做任何合并**。
4. **超过才 median-cut**：优先队列按**体积**（`(maxR-minR+1)*(maxG-minG+1)*(maxB-minB+1)`）取最大盒，
   沿**最长颜色维**把桶排序后累加群体，首次 `>= 总群体/2` 处切（切点 `min(upper-1, i)`），
   切到 16 盒；每盒输出**群体加权平均色**（各通道 `round()` 后 5→8bit 左移 3），
   平均色**再过一次滤**（被剔掉的盒会连带丢群体 —— 这就是过滤开启时群体不守恒的原因）。

模块侧实现见 `A11Palette.quantizeToSwatches()`；验证在 `work/a11_quantize_selftest.py`：
- 3 组用例与独立参考**逐块精确比对**（含一组"6 桶切到 4 盒"，真的走了 median-cut）；
- 2 组大样本**只验不变量**（块数 ≤ maxColors、不过滤时群体守恒、每块群体 > 0）：
  Java 的 `PriorityQueue` 在体积并列时出堆顺序不保证，逐块比会假红 —— 这条限制写在脚本注释里，
  别以后误以为"检查变松是 bug"；
- `--negative` 拿"按群体排序取前 N"的旧实现当反例，2 组大样本确实不同解。

另外 `region` 的坐标系也定了：两处 `setRegion` 都在**缩放后的位图**上取（`MNP:177`、`MNP:71`），
所以 `collapsedRegions()` 现在返回工作系坐标；201×201 这种奇数尺寸下，
旧算法（源图算再换算）会得到 100/80，正确是 75/60 —— 已由 `work/a11_sampling_selftest.py` 锁住。

## 18. 第四轮审查（A11 这条线）：单位、Locale、状态还原，外加验证器自己的算术错
逐条与证据核对，成立 4 条、1 条前提不成立但结论仍采纳。

| # | 位置 | 问题 | 证据 | 处理 |
|---|---|---|---|---|
| 1 | `MediaCardFeature.java` 收起态 palette 日志 | `String.format("%.2f", contrast)` 不带 Locale ⇒ 逗号小数语言写出 `contrast=9,40`，验证器 `([\d.]+)` 匹配失败，**整行被丢**，颜色/对比/网格断言静默不跑（expanded 行还在 ⇒ 不报"无日志"，是假绿） | `verify_os5_mirror.py` 的 `RE_A11_COLLAPSED`；同文件 :1122 与 `fmt()` 都带了 `Locale.US`，唯独这条没有 | 补 `java.util.Locale.US`；另加**"行数≠解析数就判红"**的漂移守卫（6 个标记） |
| 2 | 收起态副文字号 | A11 副文是 `12.0`**dp**（不跟 fontScale），我们按 `COMPLEX_UNIT_SP` 套 ⇒ 大字号机器上比 A11 大一档 | `aapt dump --values resources work/fw-res/A11-framework-res.apk`：`notification_subtext_size=12.000000dp`、`notification_title_text_size=14.000000sp`（单位不同是 framework 自己的事实，不是我们选的） | 改 `COMPLEX_UNIT_DIP` + 常量改名 `COLLAPSED_SUBTEXT_DP`；新增 `GOOD_FS`（fontScale=1.3）正例与反例——**只有 fontScale≠1 才看得见这条** |
| 3 | 展开态标题 | 收起态设的 `sans-serif-medium` 在展开分支没还原，"收起再展开"之后标题永久 medium | 代码结构：收起分支 `title.setTypeface(medium)`，展开分支只改字号 | 展开分支显式 `setTypeface(null, NORMAL)`；见证 `titleMedium=0|1`（按实例身份比——内部版 jar 没有 `Typeface.getFamily()`） |
| 4 | `verify_os5_mirror.py::a11_grid()` | 对**源图**区域做 floor/ceil 再缩放；模块与 A11 都是"先按面积 22500 缩到工作位图（`(int)` 截断），再 `getWidth()/2` 整数除" | 数值对照：200x200 两者都是 11250/13500；**201x201 → 13650 vs 13500**、512x384 → 11223 vs 11094、1200x800 → 11224 vs 11102。真实封面正是这些尺寸 ⇒ 会把正确的实现判成失败 | 验证器改为按工作位图现算，签名简化成 `a11_grid(sw, sh, region)` |
| 5 | `A11Palette.quantizeToSwatches` | 切不动的盒子我们放回队列，androidx 的 `while (size<max && (poll()!=null) && canSplit())` 是**丢弃** | `work/y7s-a11/tmp/ColorCutQuantizer.java` 的 `splitBoxes` | 统一成 androidx 写法（一比一优先于"更正确"）。**但这条差异构造不出可观察用例**：只有 0 体积的盒子切不动，而它按体积排序必然最后才被 poll 到 ⇒ 没有 fixture 假装锁，只保证两边写法一致 |

验证器现状：**3 条正例**（A13 常态、fontScale=1.3、OS5 原生圆钮不补挂）**+ 28 条反例，漏网 0**。
新增 `--expect-inert`：OS5/A15 上 `NotifyRowFeature` 根本不装复刻钩子（`isA13OrLower()` 关门），
所以"零条 os5_mirror"才是正确形态，改为反向断言零行。

顺带第一次拿到**不需要解锁的真 A15 证据**：装上本轮包并重启 SystemUI 后，
`loaded com.android.systemui` / `installed feature notify_row` / `installed feature media_card` /
`notify row feature installed` 全在，`os5_mirror:` **0 条**，95 秒 pid 不变、crash buffer 干净
（`work/z8os3-live/a15-load-check-1102.log`）。A15 的**渲染度量**门禁与 A13 全部门禁仍分别等解锁与 DSU。

## 19. 把"单位"本身变成断言（21:41）
第四轮审查之后，`notification_subtext_size` 是 **12dp** 而 `notification_title_text_size` 是 **14sp**
这个事实不再只写在注释里——`work/y7s_a11_evidence_check.py` 现在读的是
`dimen/<name>` 后面的**数值 + 单位**二元组：

```
notification_title_text_size      = (14.0, "sp")   -> COLLAPSED_TITLE_SP     （跟 fontScale）
notification_subtext_size         = (12.0, "dp")   -> COLLAPSED_SUBTEXT_DP   （不跟 fontScale）
notification_action_list_height   = (60.0, "dp")   -> ACTION_LIST_HEIGHT_DP
```

并且加了三条**模块侧对表**断言：直接从 `module/src/.../A11MediaSpec.java` 里正则取出常量值，
与设备实读比。这条对照不是纸面的——把 `COLLAPSED_SUBTEXT_DP` 临时改成 14f 立刻红
（`FAIL 模块 COLLAPSED_SUBTEXT_DP: 实得 14.0 期望 12.0`），改回来 25 通过 / 0 失败。
反例也从 4 条加到 **14 条**，其中 4 条专门改单位/数值（只改单位也红：`12.0dp` → `12.0sp`）。

工具上的一点：`_re_dimen()` 只会改 `dp` 结尾的值，sp 的 dimen 改不动，所以新加了
`_re_dimen_any(text, name, value, unit)`；跨行匹配这次**不用正则**，
因为把 Python 源码经 heredoc 写进文件时反斜杠会被二次转义（今晚第三次踩这个坑），
改成按行扫描后 `dimen_and_unit()` 一眼可读也不会坏。

## 20. 展开态标题的字族：我先前那条断言是错的，真机第一份展开态见证把它顶回来了（22:1x–23:2x）

### 20.1 触发点
OS3（DSU）门禁加了"展开卡片"这一步之后，第一次拿到展开态见证：

```
a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true titlePx=48 subtextPx=42 titleMedium=1
a11 palette expanded base=ff404040 L=25.1% highlight=ff666666 grid=10368
```

字号全对（48px=16sp、42px=14sp @density 3.0），但验证器当场报红：
`art(expanded): 展开态标题还是 sans-serif-medium（收起态设的字体没还原）`。
这条断言是我写的，前提是"A11 展开卡标题是常规字族，medium 只属于收起态模板"。**现在要判的是这个前提本身。**

### 20.2 A11 侧证据（`work/y7s-a11/`，Y7s 实拉）
`media_view.xml`（A11 展开态原生卡）里三个 TextView 的排版是成对出现的：

| 行 | 字号 | `android:fontFamily` |
|---|---|---|
| line=188（标题，`@0x7f0b037f`） | **16sp** | **`@0x0184021d`** |
| line=177 / line=198（艺名、时间） | 14sp | `@0x0184021c` |

（`xml_media_view.txt:136 / 145 / 153`。注意标题不是"16sp + 无字族"，vivo 明确给它指定了**另一个**字族资源。）

同一对 id 在 Y7s 的 SystemUI 样式表里的用法（`sysui_res.txt:145332 / 145338 / 145346`）：

```
style/Control.Spinner        → 当前值那一行  fontFamily=@0x0184021d
style/Control.Spinner.Item   → 普通条目      fontFamily=@0x0184021c
```

⇒ **21d 是"被强调"的字族、21c 是普通的**；A11 展开卡标题用 21d，说明它本来就是强调字族，
和收起态模板的 `TextAppearance.Material.Notification.Title`（sans-serif-medium，见 §4）不矛盾。

### 20.3 结论与改动
1. **推翻我自己写进验证器的断言**：`expanded 必须 titleMedium=0` 改成 `必须 =1`，
   注释里写清依据与"这是被真机顶回来才复查的"；反例同步翻向（`展开态标题丢了强调字族`）。
2. **模块的真实缺陷在另一侧**：展开分支 `title.setTypeface(null, NORMAL)` 与
   产线 `build(...)` 里 a11 走 `setTypeface(null, NORMAL)` —— 两处都会把标题**变普通**。
   改成两态共用一个 `sans-serif-medium` 实例（`a11MediumTitle()`）：
   见证是身份比较，每处各自 `Typeface.create(...)` 可能拿到不同实例 ⇒ 见证会假红，
   共享静态实例之后 `titleMedium=` 才可信。
3. **诚实标注证据强度**：`0x0184021d/21c` 的**名字没有解出** ——
   `framework-res.apk` 的 type 只到 `0x17`，`vivo-res.apk` 是包 `0x03`，
   `VivoFrameworksResRRO/mediatek-res` 都不含 type `0x84`；
   所以"21d=强调族"是**按 SystemUI 自身样式的用法对照推断**的，不是解出的字符串常量。
   能一锤定音的两条路：(a) Y7s 上把通知展开后读 `mTitle.getTypeface().getFamily()`（需要解锁）；
   (b) 找到定义 type `0x84` 的那个 vivo 框架资源包再 dump。
   在这之前，这条按"证据指向 medium"实现，并在 DEVLOG 记为**待最终确认**。

### 20.4 复跑（包 `7bae013687ae`）
门禁 `work/a11-card-gate.sh 10AE4Y0MDE000BL os3`：结果见
`work/z8os3-live/a11-gate-os3-d.txt`（两态见证 + 字号 + 取色 + 非干扰断言）。
