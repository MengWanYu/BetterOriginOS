# 交接：OS6（OriginOS 6 / Android 16）媒体卡适配 · 2026-11-18

写给新对话：先读本文件，再从 `module/src/com/betteroriginos/feature/media/MediaCardFeature.java` 入手。
**不要从记忆或旧 DEVLOG 推断当前状态。** 当前分支 `master`，工作区有未跟踪的 `work/` 取证文件。

---

## 0. 一句话现状

OS6 设备已连、模块已装、作用域齐全；通知条美化在 OS6 已停用（按用户要求），
**媒体卡在 OS6 被接通了（OS5 原子样式 + A11 样式都会接管）**，但当前版本出现多处渲染问题：
**播放器按钮/图标全部不显示（最新回归）**、封面曾出现“一大一小两张”、点收藏几次后布局异常、
展开/收起没有过渡且布局异常。用户明确选择“继续修我们的卡”，不退回原生。
本轮到此为止：**只记录、不继续改代码**。

---

## 1. 设备与环境

| 项 | 值 |
|---|---|
| 设备 | vivo V2405A / PD2405C，adb 序列号 `10AF2E1R03002ZQ` |
| 系统 | `PD2405C_A_16.1.24.15.W10`，OriginOS 6，Android 16（SDK 36） |
| root | KernelSU（ksud 3.2.5，su 上下文 `u:r:ksu:s0`） |
| 注入 | LSPosed 2.2.1 (7912)，API 102，模块目录 `/data/adb/modules/zygisk_lsposed` |
| lspctl | `/data/adb/modules/zygisk_lsposed/lspctl`（不在 PATH）。**必须在管理器里开 Developer mode**，否则只有 status/stop |
| 模块 | `com.betteroriginos`，本机最新构建 `module/build/BetterOriginOS.apk`，**sha256 前缀 `3c7a4b482ff9`**（385,902 B） |
| 当前作用域 | systemui、systemuiplugin、media.module、filemanager、camera、launcher2、settings、updater、cota、packageinstaller |

关键包版本：

- 升级器 `com.bbk.updater` **4.9.29.6**
- 桌面 `com.bbk.launcher2` **16.0.0.773**（`/system/app/BBKLauncher2/BBKLauncher2.apk`）
- 安装器 `com.android.packageinstaller` **12.4.1.10.0**（与 OS5 同版本）
- 媒体通知来自 `com.netease.cloudmusic`（用户环境实际在放的歌）

构建/安装/重启：

```bash
cd "C:/Users/Administrator/Desktop/Aiqooz8-os5-mod/module" && bash build.sh
adb -s 10AF2E1R03002ZQ push module/build/BetterOriginOS.apk /data/local/tmp/bo.apk
adb -s 10AF2E1R03002ZQ shell "su -c 'pm install -r /data/local/tmp/bo.apk'"
adb -s 10AF2E1R03002ZQ shell "su -c 'killall com.android.systemui'"
```

---

## 2. 本轮对 OS6 的已完成适配（勿重复逆）

### 2.1 通知条美化：OS6 停用（用户决定）

- `OsProfile.isOs6OrHigher()`（SDK>=36）。
- `NotifyRowFeature.install()` 开头直接 return；日志 `notify_row skipped: OriginOS 6+`。
- `NotifyRowFeature.supportNoteRes()` 返回 `note_notify_os6` 说明文案。

### 2.2 升级器 4.9.29.6（已在 OS6 验证）

- 重启入口：旧 `n0.a.D` **不存在**；新入口 `l0.a.F(Context, UpdateMethod, boolean)`。
- 定制仅下载：旧 `v0.a.M()` 不存在；新 `u0.a.F(int)` 返回 customtype，单例 `u0.a.B()`，
  `F(-1)==3` 才是定制模式。已 hook。
- 装机读数：`updater guard installed hooks=32 ... l0.a.F=1 ... customUi6=1`；
  运行期探针（settings 通道发 `probe`）：`updater guard probe customUi(os6)=3 wanted=true`。
- `UpdateTipsService$c` 方法改成 `a/b/onKey`，夜间计时器冻结**未适配**（安装仍被 startInstall 拦）。

### 2.3 安装器（OS6 正常）

`installer guard installed hooks=11 by noVerify=1 noRecommend=1 adbContinue=2 adbReject=1 lateAbort=1 route=5`；
adb 新装测试包 `Success`。

### 2.4 桌面动效（OS6 已挂上，待视觉验收）

- 配置类 `h2.a`、解析助手 `q1.a`；
  - `registeranimation` → 字段 `A` / getter `C()`
  - `wallpaperTargetAnimation` → 字段 `C` / getter `E()`
  - `useNewAnim` → 字段 `f2067h`（只有解析入口，无 getter）
  - `animationGrade` → 字段 `E`
- 缩放门仍在 `com.android.quickstep.VivoLauncherHelper$4.supportLauncherScale/supportWallpaperScale`。
- 已加 `installLauncherOs6()`；日志 `desktop motion os6 attrs=1 getters=4 parsed=2`，
  探针 prefs：`os6_fields A=true,C=true`、`getter_C=true`。
- 注意：`enable` 用 `settings put global betteroriginos_feature_desktop_motion 1`；
  桌面设置里的“动画档位”只写 `enter_exit_app_animation`，**不要强改 h2.a 的 animationGrade**
  （OS5 上强写 grade 会把壁纸缩放链关掉，这条教训同样适用）。

---

## 3. 媒体卡 OS6 全量事实（最重要）

### 3.1 视图结构（OS6）

- 通知列表里的媒体 UI 根是 `com.android.internal.widget.MediaNotificationView`（仍在），
  但阴影区另有 vivo 新组件 `MediaContainerView → RoundRelativeLayout → MusicSwipeView`
  （含 `MusicAnimTextView`、`VProgressSeekbar`、`MenuItemView` 等），补扫对它无效（logs `media sweep found nothing`）。
- 媒体通知的 remote views re-bind 走 `NotificationContentView.setContractedChild/setExpandedChild`，
  我们的卡就是从这两个 setter 建的。

### 3.2 动作按钮（NetEase Cloud Music 实测）

- `media_actions` 的 5 个 ImageButton，contentDescription 依次：
  `like`、`toggle`、`next`、`trash`、`lyric`（收起态 4 个，desc 可能为空）。
- **没有“上一首(previous)”动作**。用户要求保留 OS5 原子布局 → 已用 MediaSession
  `skipToPrevious()` 合成一个“上一首”按钮（克隆 next 图标并 `setScaleX(-1)`）。
- `isPlay()` 已扩展识别 `toggle`；播放键兜底：取 `next` 前一个动作。

### 3.3 封面来源（关键坑）

- 通知的 `android.R.id.right_icon` 在 OS6 是**应用图标**（`CachingIconView`，144×144），**不是封面**。
- 会话 metadata 的 `METADATA_KEY_ALBUM_ART` 是 363×363（真封面）；
  通知 `largeIcon` 是 256×256（也不一定是封面）。
- 现在的实现：
  - `MediaCard.artIsAppIcon`：art 是 CachingIconView 时不当封面；
  - `artSource()` 顺序：足够大的 pending → metadata → 大视图图 → **跳过 app icon 的 raster**；
    小 pending（<200px）不覆盖 metadata 大图；
  - 建卡前用 `Notification.getLargeIcon()` 兜底。

### 3.4 当前回归：**播放器不显示任何图标**

- 最后一次改动是 `hideResidualImages(root, mine)`（在 `MediaCard.build` 里、hideOld 之后调用）：
  它把媒体根里**所有可见 ImageView**置 GONE（ImageButton 仅当 desc 非空才隐藏）。
- 用户最新反馈：切歌按钮完整了，但**整个播放器不再显示任何图标** → 高度怀疑这条“残留隐藏”
  把按钮图标容器/头部图标也藏了。
- **新对话第一件事：加一个临时开关（Settings.Global 或静态常量）跳过 `hideResidualImages`，
  二分确认“无图标”是否由它引起**，再缩窄隐藏范围。
- 之前（未隐藏时）`visible iv` 探针看到残留：`ImageView 180×180 int=363`（原生大封面）、
  `ImageView 90×90 int=320`（小副本）、`ImageButton 321×120 int=54`（残留按钮）。
  双封面的目标只是隐藏前两者，**绝不要动 ImageButton / 头部图标**。

### 3.5 其它未解现象（用户描述的）

1. **点爱心（收藏）几次 → 布局异常**：怀疑 vivo 在点击后重新 apply RemoteViews，
   把动作按钮重建/重新塞回 `media_actions`（我们已 reparent 原件，重绑后原件状态与卡片错位）。
   排查方向：hook `NotificationContentView.setContractedChild/setExpandedChild`
   里观察 child 是否换了实例；考虑“点击转发到克隆按钮”而不是 reparent 原按钮。
2. **右上角展开/收起无过渡动画且布局异常**：OS6 的展开动画由 vivo 对
   MediaNotificationView 内部子视图驱动；我们的卡是 `addView(card, 0)`，
   在过渡里可能被从父容器“重新布局/裁剪”。排查方向：对卡片在展开切换时自己做 alpha/scale，
   或在展开态不接管（只接管收起态）做对照。
3. **A11 样式“别样问题”**：用户未给具体症状，新对话需先让用户描述或复现记录。

---

## 4. 关键代码位置（module/src/com/betteroriginos/feature/media/MediaCardFeature.java）

- `install()`：hook `NotificationContentView.setContractedChild/setExpandedChild`、展开钩子、补扫。
- `discoverMedia()`（约 700–760 行）：actions/title/artist/art/sourceIcon 发现；含 OS6 大图探针。
- 动作分类（约 2140–2200）：`isPrev/isNext/isPlay/isHeart/isLyric`、index 兜底、合成上一首。
- 建卡与隐藏（约 2200–2600）：`MediaCard.build`、`hideOld`、`hideResidualImages`、`logVisibleImages`。
- `MediaCard`（约 1384+）：`artIsAppIcon`、`artSource()`、`syncArt()`、`applyArtSwap`、`setState`。
- 其它相关：
  - `core/OsProfile#isOs6OrHigher`
  - `feature/notify/NotifyRowFeature#install`（OS6 直接 return）
  - `feature/updater/UpdaterGuardFeature`（`l0.a.F`、`u0.a.F`、`probeCustomUi`）
  - `feature/desktop/DesktopMotionFeature#installLauncherOs6`

---

## 5. 调试姿势（这台机已验证可用）

- SystemUI 日志可读：`adb logcat -d --pid=$(adb shell pidof com.android.systemui)`。
  多行日志的续行**不带 tag**，要么 `grep -A`，要么自己并成一行。
- 媒体播放时 `uiautomator dump` 会失败：先
  `adb shell cmd media_session dispatch pause`，再 dump；或看我们自己的日志/探针。
- 内置探针：
  - `betteroriginos_media_probe`（Settings.Global）：媒体判定/大图。
  - 桌面：`/data/user_de/0/com.bbk.launcher2/shared_prefs/betteroriginos_desktop_probe.xml`
  - 注入标记：`/data/user_de/0/<pkg>/shared_prefs/betteroriginos_inject.xml`
- 触发完整媒体卡：`am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postmedia`
  （会建一个 MediaSession + MediaStyle 通知；也可 `cmd media_session dispatch play` 用真实播放器）。
- 常用日志锚点：`restyling style=os5`、`atomic card built`、`atomic art:`、`art pick=`、
  `visible iv`、`action[i] desc=`、`media restyle skip`、`media sweep found nothing`。

### 本轮踩过的坑

- Python heredoc 写 Java 字符串时 `\n` 会被展开成真换行 → **一律用 Edit 工具**改 Java/XML。
- `XposedBridge.hookAllMethods` 只命中类自己声明的方法；`VivoLauncherHelper$4` 这类匿名类名要带 `$`。
- targetSdk 35 包可见性：普通 `getPackageInfo`/`getLaunchIntentForPackage` 看不到别的包，用 root `pm list packages` 兜底。
- LSPosed 2.2.1(7912) 的 `lspctl` 需要管理器里开 **Developer mode**，否则子命令不可用。
- adb 偶发掉线（重插/USB 重新枚举）；`adb kill-server && adb devices` 或等用户重插。
- 不要动 `persist.*` prop 来修观感；先钩运行时路径。

---

## 6. 新对话建议的第一步

1. `git status` + 读本文件 + 读 `MediaCardFeature.java` 的建卡/隐藏段。
2. 装当前构建（hash `3c7a4b48…`）复现“无图标”，**先跳过 `hideResidualImages`（加静态开关）二分**。
3. 图标恢复后，把隐藏范围缩到“非 ImageButton、intrinsic>=300 且位于 MediaNotificationView 直属子树”的那两张封面。
4. 再按 §3.5 顺序处理：收藏点击重绑 → 展开/收起过渡 → A11 样式（让用户描述症状）。
5. 所有改动同步 `out/DEVLOG.md` 与本 skill 的 §4.12。

---

## 7. 2026-11-18 晚补丁（用户带走设备前完成，待装机）

新构建：`module/build/BetterOriginOS.apk`，sha256 前缀 **`1ce592461afd`**。
设备拿走前用上一版（`6024a44ff437`）验证过圆角/上一首/封面源；本版再多修了切歌 240ms 图标帧。

已改（`MediaCardFeature.java`）：

1. **App 图标不再当封面**：`artSource()` 判断顺序改为
   pending(≥200) → 视图图(≥200) → metadata → 小视图（有上一张真封面则 hold；OS6 直接 iconSkip）
   → raster（**跳过 TransitionDrawable**，其它非位图按 Drawable 身份缓存）→ hold 上一张 ≥200 真封面。
   真机证据：切歌时 right_icon 是 108px 图标（hash 3a926fa3 跨歌不变），随后被换成
   每帧都变的 TransitionDrawable；旧实现拿它取色 ⇒ 整卡底色=图标色（网易云 `ff f83830`）。
2. **A11 圆角恢复**：宿主 `notification_corner_radius` 实测 12dp（OS5/OS6 同值）；
   A11 背景 `setCornerRadius` + 卡 `ViewOutlineProvider` 圆角裁剪。日志 `a11 card corner=36`。
3. **A11“上一首”**：`synthPrev` 放开到全样式，A11 插在播放键前；日志 `a11 synth prev at=1 total=6`。
4. **非 A11 无图标回归**：`hideResidualImages` 只藏 int≥300×300 的非 ImageButton，
   ImageButton/头部图标不再碰（双封面实测 int=363/320）。
5. `hideResidualImages/logVisibleImages` 仍只作用于非 a11（A11 不跑）——如 A11 也出现残留图再说。

装机后看这些日志锚点：

- `a11 card corner=36`（density 3.0 时 = 12dp）
- `a11 synth prev at=`（收起/展开各一条，count 比 actions+1）
- 切歌期间 `art pick=` 只应出现 `metadataSmallView / hold / holdSmallView`，
  不应再出现 `largeIconDrawn` 连续多帧、也不应出现 `iconSkip` 之外的图标帧
- `art drawable changed … TransitionDrawable` 允许出现（vivo 自己换图），但后面不应再跟连续 fade

仍未处理（下一轮但用户已登记）：收藏点击后重绑布局异常、展开/收起无过渡、
收起态 4 个空 desc 按钮的 prev/play/next 归属靠 index 兜底（OS6 顺序 like/toggle/next/trash，
兜底可能错位，需要真机确认收起态按钮行为）。
