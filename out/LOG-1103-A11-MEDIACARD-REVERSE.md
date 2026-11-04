# 安卓 11 媒体播放卡逆向（2026-11-03 夜）· 一手证据记录

写作目的：**把 A11 媒体卡的真实渲染逻辑逐条抄进模块**，让接手者不需要重新反编译。
本文件只写"有二进制/字节码位置支撑"的结论；凡是推断都明写为推断，并给出下一步怎么证伪。

材料（全部为真机产物，不是文档、不是记忆）：

| 材料 | 路径 | 来源 |
|---|---|---|
| Y7s（Android 11 / OriginOS Ocean）SystemUI | `work/y7s-a11/SystemUI.apk`（102,711,197B） | 设备拉取 |
| Y7s framework | `work/y7s-a11/framework.jar`（32,787,926B，5 个 dex） | 设备拉取 |
| Y7s framework 资源表 | `work/y7s-a11/framework-res.apk` + `fw_res.txt`（aapt dump，9.0MB） | 设备拉取 |
| SystemUI 资源表 | `work/y7s-a11/sysui_res.txt`（9.9MB） | aapt dump |
| 全量反编译源 | `work/y7s-a11/jadx-proj/sources/`（jadx 1.5.5，本次跑完 exit 0） | jadx |
| 正在播放时的通知侧写 | `work/y7s-a11/notif-playing.txt`（`dumpsys notification --noredact`） | Y7s 真机 |
| 字节码 | `work/tmp-re-a11/a11-code-classes2.txt`（SystemUI classes2.dex，1,902,303 行）、`work/tmp-re-a11/A11-MediaArtworkProcessor.dump` | dexdump -d -n |

**为什么这条链以前是错的**（交接文档 HANDOVER §6.4 已自认）：此前 A11 的取色/字族结论部分来自
"我按预期写的 fixture"与 A15/A13 的类比，不是从 A11 二进制核出来的。本轮全部重做。

---

## §1 输入端：A11 真机上这张卡到底是什么

`work/y7s-a11/notif-playing.txt` 里播放中的 i 音乐通知（`pkg=com.android.bbkmusic id=10001`）：

- `android.template = android.app.Notification$MediaStyle`
- `category=transport`、`actions=5`、`flags=0xa`
- `android.showWhen = false`
- `android.largeIcon = Icon(typ=BITMAP size=200x200)`
- `color=0x00000000`，且 **没有 colorized**（extras 里无 `android.colorized`）

⇒ 结论（可核对）：**底色不来自 `notification.color`**。A11 媒体卡的配色只能由
(a) `Notification.isColorizedMedia()` 分支 或 (b) SystemUI 侧的 artwork 处理产生。
i 音乐这种"没 colorize"的样本走的是 (b)（见 §4）。

---

## §2 卡片骨架：framework 的 `MediaNotificationView`（A11 是具体类）

`com.android.internal.widget.MediaNotificationView extends FrameLayout`，`@RemoteViews.RemoteView`，
反编译件 `work/y7s-a11/A11-MediaNotificationView.java`（178 行，来自 framework classes4.dex）。

> 修正一条旧假设：A11 **没有** `NotificationMediaView`（`work/y7s-a11/sysui_classes.txt` 全量 grep
> `NotificationMediaView` = 0 命中），布局里根标签就是 `com.android.internal.widget.MediaNotificationView`
> 本身，它不是抽象类。以前按 AOSP 12+ 的 `m3a` 思路去找 `NotificationMediaView` 是找不到的。

布局源（二进制，逐属性）：`work/y7s-a11/A11-media-layout.xmltree.txt`
= `aapt2 dump xmltree --file res/layout/notification_template_material_media.xml framework-res.apk`

```
MediaNotificationView            id=@android:id/status_bar_latest_event_content  tag="media"
                                 background=#00000000  w=match_parent h=wrap_content
├─ ImageView                     id=@android:id/right_icon        ← 封面
│    layout_width=0dp layout_height=0dp scaleType=6(**centerCrop**，见 §15-4)
│    adjustViewBounds=true layout_gravity=0x00800035(=TOP|END，§15-3 拆解)
├─ include                       layout=@layout/notification_template_header
│    height=@dimen/media_notification_header_height (→ @dimen/notification_header_height)
├─ LinearLayout                  id=@android:id/notification_media_content  orientation=vertical
│  ├─ LinearLayout               id=@android:id/notification_main_column  orientation=horizontal
│  │     layout_marginTop=@dimen/notification_content_margin_top(46dp)
│  │     layout_marginStart=@dimen/notification_content_margin_start(16dp)
│  │  ├─ LinearLayout            id=@android:id/notification_content_container  weight=1
│  │  │     paddingBottom=@dimen/notification_content_margin(20dp)
│  │  │     minHeight=@dimen/notification_min_content_height(39dp)
│  │  │  ├─ include @layout/notification_template_part_line1   (title)
│  │  │  └─ include @layout/notification_template_text         (text)
│  │  └─ LinearLayout            id=@android:id/media_actions  layoutDirection=0(LTR)
│  │        layout_marginStart=10dp
│  │     ├─ include id=@android:id/action0 layout=@layout/notification_material_media_action
│  │     ├─ include id=@android:id/action1 layout=…同上
│  │     └─ include id=@android:id/action2 layout=…同上
│  └─ ViewStub                   id=@android:id/notification_media_seekbar_container
        layout_alignParentBottom=true
```

`onMeasure`（A11 真实算法，逐行照抄）：

1. `hasIcon = right_icon.getVisibility() != GONE`；不成立 → `resetHeaderIndention()` 后直接 super 测量。
2. 只有 `widthMeasureSpec` 的 mode **不是 EXACTLY** 时才做后面的事（`mode != 0`）。
3. `size2 = 给定宽 - media_actions.getMeasuredWidth()`
   `imageEndMargin = right_icon 的 marginEnd`
   `size3 = size2 - imageEndMargin`
   `fullHeight = notification_media_content.getMeasuredHeight()`
4. 封面边长 `= fullHeight`（**正方形，边长=媒体内容块的高度**）：
   - `size3 > fullHeight` → `size3 = fullHeight`
   - `size3 < fullHeight` → `size3 = max(0, size3)` 且 `mImagePushIn = fullHeight - size3`
5. `notification_main_column.marginEnd = size3 + imageEndMargin + @dimen/notification_content_margin_end`
6. `header.setHeaderTextMarginEnd(size3 + imageEndMargin)`
7. `header.marginEnd = imageEndMargin`
8. `IS_TIER_VOS`（vivo 分层判定，`FtBuild.getTierLevel()!=0 && "vos".equalsIgnoreCase(FtBuild.getOsName())`）
   时再给 header 设 `paddingEnd = @dimen/notification_content_image_margin_end`。
9. 以上任何一项改过 → 再 `super.onMeasure` 一次。

`onLayout`：`mImagePushIn > 0` 时——非 VOS 保持原位；**VOS（vivo）把封面整体向右/向左推进
`mImagePushIn`**（RTL 取负），即"放不下就把封面塞进边距里"，不是缩放。

⇒ 移植口径：A11 的封面是**右侧正方形、边长等于内容块高度、文字列用 marginEnd 给它让位**；
"封面塞进边距"是 vivo 特有分支。

---

## §3 取色：`MediaNotificationProcessor`（A11 SystemUI）

文件：`work/y7s-a11/jadx-proj/sources/com/android/systemui/statusbar/notification/MediaNotificationProcessor.java`

常量的**真实值**（不是类比）：

| 常量 | 值 | 用途 |
|---|---|---|
| `RESIZE_BITMAP_AREA` | 22500（=150×150） | palette 缩放面积、人口阈值分母 |
| `MINIMUM_IMAGE_FRACTION` | 0.002d | `hasEnoughPopulation = pop/22500f > 0.002` ⇒ **pop > 45** |
| `POPULATION_FRACTION_FOR_WHITE_OR_BLACK` | 2.5f | 黑白主导时改用彩色 swatch 的比例门限 |
| `POPULATION_FRACTION_FOR_DOMINANT` | 0.01f | 前景色改判门限 |
| `MIN_SATURATION_WHEN_DECIDING` | 0.19f | dominant 太灰就跟随 vibrant |
| `POPULATION_FRACTION_FOR_MORE_VIBRANT` | 1.0f | （本类未使用） |
| `BLACK_MAX_LIGHTNESS` | 0.08f | HSL lightness ≤0.08 记黑 |
| `WHITE_MIN_LIGHTNESS` | 0.9f | HSL lightness ≥0.9 记白 |

关键规则：

1. `generateArtworkPaletteBuilder(bmp) = Palette.from(bmp).setRegion(0, 0, w/2, h).clearFilters().resizeBitmapArea(22500)`
   ⇒ **A11 只取封面左半部分**做背景色，并且 `clearFilters()`（关掉 palette 自带过滤）。
2. `findBackgroundSwatch(Palette)`：
   - `dominant == null` → `new Palette.Swatch(0xFFFFFFFF, 100)`（**白，人口 100**）；
   - dominant 不是黑/白 → 直接用 dominant；
   - 否则在全部 swatch 里找"人口最大且非黑非白"的那个 `alt`；
     `dominant.population / alt.population <= 2.5` ⇒ 用 `alt`，否则仍用 dominant。
3. `selectForegroundColor(bg, palette)`（背景色决定前景族）：
   - `isColorLight(bg)`：候选 `darkVibrant, vibrant, darkMuted, muted, dominant`，兜底 `0xFF000000`
   - 否则：候选 `lightVibrant, vibrant, lightMuted, muted, dominant`，兜底 `0xFFFFFFFF`
   - `selectVibrantCandidate(a,b)`：两者都有人口 ⇒ `a.pop / b.pop < 1.0 ? b : a`（**人口多的胜出**）；
     只一个 → 用它；都无 → null。
   - `selectMutedCandidate(muted, darkMuted)`：两者都有人口 ⇒ `muted.hsl[1] * (muted.pop / darkMuted.pop) > darkMuted.hsl[1] ? muted : darkMuted`（**饱和×人口比**）。
   - 拿到候选后：候选==dominant → dominant.rgb；否则
     `候选.pop / dominant.pop >= 0.01 || dominant.hsl[1] <= 0.19` → 候选.rgb，否则 dominant.rgb。
   - 无候选：`hasEnoughPopulation(dominant)` → dominant.rgb，否则兜底色。
4. `processNotification(n, builder)`（决定"要不要按封面重绘"）：
   - 只有 `largeIcon != null` 才动；先 `builder.setRebuildStyledRemoteViews(true)`。
   - **仅当 `n.isColorizedMedia()`** 才做取色：
     把 drawable 画进一张位图（若 `w*h > 22500` 则按 `sqrt(22500/(w*h))` 缩），
     `bg = findBackgroundSwatch(palette(left-half)).rgb`；
     然后把 region 改成 **`(w*0.4, 0, w, h)`（右 60%）**，若 bg 不是黑/白，再加一条
     `|hue(swatch) - hue(bg)| > 10 && < 350` 的过滤器 + 黑白过滤器 → `fg`；
     `builder.setColorPalette(bg, fg)`。
   - 非 colorized：`color = @color/notification_material_background_color`
     （**A11 framework 实测：default `#ffffffff`，night `@color/black`**，`fw_res.txt` 里 `0x010601ca`）。
   - **两种情况都执行最后一步**：`builder.setLargeIcon(Icon.createWithBitmap(mColorizer.colorize(drawable, color, isRtl)))`
     ⇒ 也就是 §4 的渐变。

---

## §4 "封面铺背景 + 渐变"的两个真身

A11 里其实有**两条不同的处理**，之前把它们混为一谈，所以做出来不像：

### 4a. `ImageGradientColorizer.colorize(Drawable, color, isRtl)` —— 封面上叠加的渐变
文件：`work/y7s-a11/jadx-proj/sources/com/android/systemui/statusbar/notification/ImageGradientColorizer.java`

逐指令（`work/y7s-a11/A11-...java` 行号 18-54）：

1. 正方形裁切：`side = min(w,h)`，bounds 平移 `(-(w-side)/2, -(h-side)/2)`（居中裁方）。
2. `luma = (0.2126·R + 0.7152·G + 0.0722·B)`（Rec.709，先归一化再乘回 255）。
3. ColorMatrix = 行都是 Rec.709 系数、第四行保 alpha，平移项为 `R-luma / G-luma / B-luma`
   ⇒ 图片变成**以取色为均值的明暗浮雕**（不是简单调色）。
4. 遮罩渐变（第一次）：`LinearGradient(0,0,side,0, [0x00000000, Color.argb(0.5f,1,1,1), 0xFF000000], [0f, 0.4f, 1f], CLAMP)`
   配 `PorterDuff.Mode.DST_IN` 画在图片层上 ⇒ **左端全透明、40% 处半透、右端不透明**；RTL 时先 `translate+scale(-1,1)` 翻转。
   两段 stops 的真值来自 `fill-array-data`：`0x014c` = `[0.0, 0.4, 1.0]`、
   `0x0156` = `[0.0, 0.6, 1.0]`（第二段的 0.6 = `0x3f19999a`）。
5. 把带色矩阵的版本画进目标位图，**alpha=127**（字节码 `328300: const/16 v8, #int 127` →
   `328304: Paint.setAlpha(I)`；jadx 显示的 `WorkQueueKt.MASK` 是它把这个 127 内联成了同名 Kotlin 常量，
   别信符号名）。luma 的 255 是 `0x437f0000`，翻转用的 -1.0f 是 `0xbf800000`。
6. 遮罩渐变第二次：同一组颜色，`stops=[0f, 0.6f, 1f]`，再 `DST_IN` 一次 ⇒ 二次衰减，左半边几乎全空。
7. 原始（未染色、二次衰减后）图片再叠画上去 ⇒ 观感 = **左边是取色浮雕，右边逐渐露出真封面**。

### 4b. `MediaArtworkProcessor.processArtwork(context, artwork)` —— 铺在后面的模糊封面
jadx 因为方法体 274 个指令单元**静默跳过**（`Method dump skipped`），所以以下全部来自字节码
`work/tmp-re-a11/A11-MediaArtworkProcessor.dump`（dexdump -d，偏移 `311354`–`311586`）：

| 步 | 字节码证据 | A11 的真实做法 |
|---|---|---|
| 缓存 | `000c: iget-object mArtworkCache` / `000e: if-eqz` | 单槽缓存，命中直接返回 |
| 模糊引擎 | `0011: RenderScript.create` `0015: Element.U8_4` `0019: ScriptIntrinsicBlur.create` | RenderScript 高斯模糊（A11 时代还是 RS） |
| 目标尺寸 | `002a: Display.getSize(mTmpSize)` `003f: div-int/lit8 v6, v6, #6` `0047: div-int/lit8 v10, v10, #6` `0049: Math.max` | **`scale = max(屏宽/6, 屏高/6)`，整数除法** |
| 等比装入 | `004d: MathUtils.fitRect(Rect I)` | 把源矩形按比例装进 `scale×scale` |
| 缩放 | `0059: Bitmap.createScaledBitmap(bmp, w, h, v6=1)` | **filter=true** |
| 强制格式 | `0066: if-eq config,ARGB_8888` → `copy(ARGB_8888, false)` + `recycle` | 非 ARGB_8888 一律拷成 ARGB_8888 |
| 模糊半径 | `0098: const/high16 v0, #int 1103626240` → `setRadius(F)V` | `0x41C80000 = 25.0f` ⇒ **半径 25** |
| 执行 | `setInput / forEach / copyTo` | 输出回同一张 bitmap |
| 取色 | `00a6: MediaNotificationProcessor.findBackgroundSwatch(Bitmap)` | **对模糊后的图**跑 §3 的左半 palette |
| 蒙色 | `00b9: const/16 v1, #int 178` → `ColorUtils.setAlphaComponent(II)` → `Canvas.drawColor(I)` | swatch 原色、**alpha=178（0.7）** 整幅铺上去 |
| 收尾 | `Allocation.destroy ×2`、`ScriptIntrinsicBlur.destroy`、`scaled.recycle`、`return out` | 出错路径打 `Log.e("MediaArtworkProcessor","error while processing artwork")` 并返回 `null`（`00fd: return-object v3`，v3=0） |

消费者：`NotificationMediaManager`（`.../statusbar/NotificationMediaManager.java`）
- `processArtwork(bitmap)`（:335）只是转发给 processor；
- 后台跑在 `ProcessArtworkTask extends AsyncTask<Bitmap,Void,Bitmap>`（:127，构造带两个 boolean）；
- `mMediaArtworkProcessor.clearCache()` 在媒体会话切换/销毁时调用（:96、:222）；
- `mColorExtractor.setHasMediaArtwork(z3)`（:252）⇒ 模糊封面还会**反过来影响壁纸取色**（有媒体封面时通知底色换一套）。

---

## §5 媒体按钮：A11 到底怎么让它"看得清"

### 5a. 尺寸与样式（二进制布局）
`res/layout/notification_material_media_action.xml`（A11 framework-res，`aapt2 dump xmltree`）：

```
ImageButton  style=@style/Widget.Material.Button.Borderless.Small
             background=@drawable/notification_material_media_action_background
             layout_width=layout_height=@dimen/media_notification_action_button_size = 48.0dp
             padding(上|下)=8dp  paddingStart=8dp  paddingEnd=8dp  layout_marginEnd=2dp
             visibility=2(INVISIBLE，由 MediaStyle 逐位改可见性)
```
（`0x010501a5` → `media_notification_action_button_size = 48.0dp`，经 `work/a11_res.py` 解出。）

### 5b. 图标着色（framework `Notification$MediaStyle.bindMediaActionButton`）
来源：`work/y7s-a11/A11-Notification-MediaStyle.java`（jadx 把内部类存进了 `android.app.Notification` 全文），
`MediaStyle` 从 :4888 起，着色段在 :4974-4989：

```java
container.setImageViewIcon(buttonId, action.getIcon());
boolean inNightMode = (config.uiMode & 48) == 32;                 // UI_MODE_NIGHT_MASK / UI_MODE_NIGHT_YES
int tintColor = (!builder.shouldTintActionButtons() && !builder.isColorized(p))
        ? ContrastColorUtil.resolveColor(context, 0, inNightMode)  // 跟随主题的次要/对比色
        : getActionColor(p);                                       // colorized → getPrimaryTextColor(p)
                                                                // 否则 → resolveContrastColor(p)
container.setDrawableTint(buttonId, false, tintColor, PorterDuff.Mode.SRC_ATOP);
// 水波纹 = 主题 colorControlHighlight 的 alpha 配上 tintColor 的 RGB
int rippleAlpha = Color.alpha(obtainStyledAttributes({?attr/colorControlHighlight}).getColor(0, 0));
container.setRippleDrawableColor(buttonId, ColorStateList.valueOf(Color.argb(rippleAlpha, r, g, b)));
```

以及 `getActionColor`：
```java
private int getActionColor(StandardTemplateParams p) {
    return builder.isColorized(p) ? builder.getPrimaryTextColor(p) : builder.resolveContrastColor(p);
}
```
`isColorized(p) = p.allowColorization && n.isColorized()`；
`resolveContrastColor(p)`：`rawColor==0` 时走 `ContrastColorUtil.resolveDefaultColor(context, bg, night, true)`，
否则 `resolveContrastColor(context, rawColor, bg, night)`，最后若 alpha<255 再 `compositeColors(color, bg)`。
背景基准色 = `@color/notification_material_background_color`（default `#ffffffff` / night `@color/black`）。

⇒ **A11 的按钮可读性不是"某个固定 alpha"**：非 colorized 时按钮 = 主题 resolveColor(0)，
colorized 时按钮 = **和正文同一个 primary 色**（即 §3 从封面右 60% 选出来的 fg）。
我们现有 A11 样式若给按钮固定压暗，就是"看不清"的来源（对照见 §6）。

### 5c. 动作槽位规则（同一份源码）
`MAX_MEDIA_BUTTONS = 5`、`MAX_MEDIA_BUTTONS_IN_COMPACT = 4`、
`MEDIA_BUTTON_IDS = {action0..action4}`；收起态只显示 `setShowActionsInCompactView` 指定的（越界抛
`IllegalArgumentException("setShowActionsInCompactView: action %d out of bounds (max %d)")`）；
未用的槽位 `setViewVisibility(id, GONE)`。另外 A11 还有一个
`media_seamless`（`@drawable/ic_media_seamless`，`R.string.ext_media_seamless_action`）按钮被绑定后
**立刻置 GONE** —— 说明它只在特殊场景由别的代码再打开，普通媒体卡不可见。
`handleImage`：有 largeIcon 时把 `line1`、`text` 的 marginEnd 归零（文字可以伸到封面下沿）。
`hasProgress()` 在 MediaStyle 里返回 **false** —— A11 媒体卡的进度条不是标准 progress 模板，
而是 `notification_media_seekbar_container` 这个 ViewStub（由 SystemUI 决定何时 inflate）。

---

## §6 与模块现状的差集（下一步实现的靶子）

对照件：`module/src/com/betteroriginos/feature/media/A11MediaSpec.java`、`A11Palette.java`、
`MediaCardFeature.java` 的 a11 分支。**本轮尚未逐条比对**（先固化证据），实施阶段要打的点：

1. 取色区域：我们此前对整图取色 → A11 是 **左半 `w/2`**（§3-1），且 `clearFilters()`。
2. 背景色：A11 = 模糊后再铺 `swatch@alpha 178`（§4b），不是"纯色 + 蒙层"。
3. 封面渐变：A11 的 Rec.709 浮雕 + 两段 DST_IN（0.4 / 0.6 stops）完全没实现（§4a）。
4. 按钮：尺寸 **48dp / 内缩 8dp / marginEnd 2dp**、着色走 `primary`（colorized）或
   主题 `resolveColor(0)`（非 colorized）+ 水波纹取 colorControlHighlight 的 alpha（§5）。
5. 几何：封面是**右侧正方形、边长=内容块高**，文字列用 `marginEnd` 让位，vivo 才有"塞进边距"（§2）。
6. 兜底：`dominant == null → Swatch(0xFFFFFFFF, 100)`；非 colorized 时底色 =
   `notification_material_background_color`（亮 `#ffffffff` / 暗 `black`）。

---

## §7 待办与证伪点（按优先级）

> 1、3 已解决（见下面标注）；新增第 7 条是本轮最重要的发现。


**✅ 已解决** `ImageGradientColorizer` 染层 alpha = **127**（见 §4a-5）。
2. `ContrastColorUtil.resolveColor(Context,int,boolean)` 在 colorAttr=0 时的实际返回
   （A11 `android.internal.util.ContrastColorUtil`，framework.jar）——决定非 colorized 时按钮到底多亮。
**✅ 已解决** `mArtworkCache` 在整个类里只有 **一处写入**：`311614: 000a iput-object` —— 那是 `clearCache()`
   里的 `= null`。也就是说 **processArtwork 从不填缓存**，读到的永远是非空即返回、否则现算。
   缓存由谁填仍未证实（`NotificationMediaManager` 侧没有 setter），移植时按"单槽缓存只由调用方维护"
   处理，别指望第二次调用命中。
4. `IS_TIER_VOS` 在 Y7s 上的实际取值（`FtBuild.getTierLevel()/getOsName()`）——
   §2/§4 里有三条分支依赖它。证伪办法：在 Y7s 上反射读 FtBuild（无需 root，可用我们的调试入口起 Activity）。
5. `notification_media_seekbar_container` 被谁 inflate、进度条的真实高度/颜色（ViewStub 的布局 id 待解）。
6. A11 的 title/artist 字号与色（`notification_template_part_line1`/`_text` 的属性，待解 id）。
7. **Y7s 上 `IS_TIER_VOS` 的真实取值**（`FtBuild.getTierLevel()!=0 && "vos".equalsIgnoreCase(FtBuild.getOsName())`，
   见 `work/y7s-a11/A11-Notification-MediaStyle.java:349`）。它决定 §9 的两条分支走哪一条：
   走 `car_*`（AOSP 车机颜色被 vivo 挪用）还是走 `vivo_notification_*`。
   `getprop | grep -i tier` 在 Y7s 上**没有任何 tier 键**（只有 `ro.vivo.os.version=13.0`），
   所以必须反射 `FtBuild` 才算证。间接证据：A11 实际观感是纯白/纯黑正文，与 `vivo_notification_*`
   分支一致 ⇒ 大概率 `IS_TIER_VOS=false`。

---

## §9 A11 通知卡的颜色真值表（本轮最重要的可执行结论）

来源链：`Notification$Builder` 构造 → `config_tintNotificationActionButtons`
→ `bindMediaActionButton` → `getActionColor` → `ensureColors` → `ContrastColorUtil`。

1. `bool/config_tintNotificationActionButtons`（A11 framework `0x011100ef`）实测 = **true**。
   `mTintActionButtons = !(IS_TIER_VOS || !config)` ⇒ 非 VOS 层时 = **true**
   （`work/y7s-a11/A11-Notification-MediaStyle.java:1807-1812`）。
2. 于是 `bindMediaActionButton` 的分支（:4978）`!shouldTintActionButtons() && !isColorized(p)`
   在非 VOS 上**不成立** ⇒ 按钮色走 `getActionColor(p)`：
   - colorized → `getPrimaryTextColor(p)`（与正文同一个 palette 主色）
   - 非 colorized → `resolveContrastColor(p)`（对比度保证）
   ⇒ **“A11 按钮看不清”在原生逻辑里不可能存在**：原生要么跟随正文主色，要么被对比度链拉回到可读值。
3. 水波纹：`rippleAlpha = alpha(?attr/colorControlHighlight)`，RGB 用按钮色
   （`setRippleDrawableColor(buttonId, ColorStateList.valueOf(argb(rippleAlpha, r, g, b)))`）。
4. `ensureColors(p)`（:2473-2495）——正文两色的真实算法：
   - `resolvePrimaryColor(ctx, bg, night, fromNotification=true)` / `resolveSecondaryColor(...)`
   - 若 colorized 且 `bg != 0`：两者再过一遍 `findAlphaToMeetContrast(color, bg, **4.5**)`（**只调 alpha**）
   - 若 app 显式设了 foreground：`contrast < 4.5` 时 `findContrastColor(..., 4.5)`，
     主色再 `changeColorLightness(x, -20)`（LAB 明度 -20）
5. `shouldUseDark(bg, defaultBackgroundIsDark)`：`bg==0 → !defaultBackgroundIsDark`；
   否则 `calculateLuminance(bg) > 0.5`（**相对亮度 0.5，不是 HSL**）
   ⇒ `isColorLight(c) = luminance(c) > 0.5d`（`A11-ContrastColorUtil.java:430`）。
6. 两条分支的**资源实读值**（framework 与 vivo-res 各一份，`work/a11_res.py` 解出）：

| 用途 | 非 VOS（`vivo-res`，包 0x03） | VOS（`car_*`，android 包 0x01） |
|---|---|---|
| 主文（浅底/用深字） | `0x03080169 vivo_notification_title_text_color` = **#ff000000** | `@color/car_scrollbar_thumb` → `car_scrollbar_thumb_light` = **#99ffffff** |
| 主文（深底/用浅字） | `0x0308016a …title_text_color_light` = **#ffffffff** | `@color/car_red_a700` = **#ffd50000**（！） |
| 副文（浅底） | `0x03080162 vivo_notification_content_text_color` = **#ff4d4d4d** | `car_seekbar_track_background_light` → `car_grey_400` |
| 副文（深底） | `0x03080163 …content_text_color_light` = **#b3ffffff** | `car_seekbar_track_background_dark` → `car_grey_700` |

⇒ **移植口径**：A11 通知卡的主文是 **完全不透明的纯黑/纯白**，副文才是 `#4d4d4d` / `70% 白`。
我们现有 `A11MediaSpec.TEXT_SECONDARY = 0x99FFFFFF`（60%）与 `SEEK_*` 一组固定白阶，
是按"背景恒深"猜出来的，与这张表不一致（见 §10）。

---

## §10 以前的 A11 规格为什么"不像"：抄错了界面

`module/src/com/betteroriginos/feature/media/A11MediaSpec.java` 的每条注释都指向
`media_view.xml` / `qs_media_album_size` / `@style/MediaPlayer.Button` / `IlluminationDrawable`——
这些属于 **A11 的 QS 媒体播放器**（`com.android.systemui.media.MediaViewController` 一族），
而 BetterOriginOS 的媒体卡改的是 **通知栏里的 MediaStyle 行**
（framework `notification_template_material_media.xml` + `MediaNotificationView`，见 §2）。
两个界面在 A11 里是**两套布局、两套尺寸、两套取色**，混用就会得到"看着像但不是"的结果：

| 项 | QS 播放器（现状抄的） | 通知栏媒体卡（应该抄的） |
|---|---|---|
| 封面 | `album_art` 52dp（`qs_media_album_size`） | `right_icon` **正方形、边长=内容块高**（§2） |
| 按钮 | `MediaPlayer.Button` 48/52dp | **48dp，内缩 8dp，marginEnd 2dp，borderless-small**（§5a） |
| 着色 | 固定白阶（背景恒深假设） | 主文不透明 + 副文 70%/`#4d4d4d`，按钮走对比度链（§9） |
| 进度 | 3dp seekbar | `notification_media_seekbar_container` ViewStub（SystemUI 决定何时 inflate） |
| 取色 | 整图 palette | **左半图** palette；colorized 才重染，且 fg 从**右 60%** 选（§3-1/§3-4） |

下一步实现按右列（通知栏）为准；QS 播放器那套常量保留但改名注明"仅用于 QS 外观参考"，
避免下一个人再混。


## §11 模糊封面到底铺在哪里（本轮最关键的一条）

`NotificationMediaManager.finishUpdateMediaMetaData(metaDataChanged, allowEnterAnimation, blurred)`
（`.../statusbar/NotificationMediaManager.java:245-301`）的去向，逐行是：

1. `blurred != null` → `new BitmapDrawable(resources, blurred)`；
2. `mColorExtractor.setHasMediaArtwork(true)`（通知底色改用"有媒体封面"那一套壁纸取色）；
3. `mScrimController.setHasBackdrop(true)`；
4. **只有** `mStatusBarStateController.getState() != 0`（不是普通 Shade）**且**
   `biometricUnlockController.getMode() != 2` **且** `!statusBar.isOccluded()` 才继续；
5. `mBackdrop`（一个 ImageView）可见性设 VISIBLE；允许入场动画时 `alpha 0 → 1` 动画；
6. 双缓冲交叉淡出：`mBackdropFront` 接住 `mBackdropBack` 的旧图 →
   `mBackdropBack.setImageDrawable(新模糊封面)` → `mBackdropFront.animate().setDuration(250).alpha(0f)`；
7. 否则（普通下拉状态）整段走"收起 backdrop"分支：`setVisibility(GONE)` +
   `mBackdropBack.setImageDrawable(null)` + `setBackdropShowing(false)`。

⇒ **A11 的"封面铺满背景 + 渐变"发生在锁屏/媒体态的整屏 backdrop，不是通知栏那张卡。**
通知栏里的 MediaStyle 卡只有：右侧方形封面（§2）+ colorized 时的取色底（§3-4）+ 48dp 按钮（§5）。
我们现在的 a11 样式把模糊封面铺满卡片，是把 backdrop 的效果错搬到了卡上——这就是"不像"的第二条根因。

## §12 由 §11 直接推出来的三条修正（下一步实现按这几条改）

1. **卡片背景**：不再用模糊封面铺满。改成 A11 的两条真分支——
   colorized 用 `bg = findBackgroundSwatch(左半 palette).rgb`；非 colorized 用
   `@color/notification_material_background_color`（亮 `#ffffffff` / 夜 `@color/black`）。
2. **封面圆角**：`notification_template_material_media.xml` 的 `right_icon` **没有任何裁剪/圆角属性**
   （只有 scaleType/adjustViewBounds），所以卡内封面应是 **0 圆角**；
   现有 `ART_RADIUS_DP=2f` 来自 QS 侧的 `dialogCornerRadius`，属于 §10 的串界面。
3. **尺寸**：`right_icon` 是"边长=内容块高度"的正方形（§2 的 onMeasure），
   不是 52dp/36dp 的 QS 常量；文字列靠 `marginEnd` 让位，封面放不下时由 vivo 分支整体平移。

**仍然保留**（因为 QS 常量确实在 A11 里存在，只是不属于这张卡）：
`SEEK_TRACK_DP`、`BUTTON_CENTER_DP`、`ILLUM_*`。改名/加注是实施阶段要做的，别删证据。

---

## §13 两处我自己写错又当场改掉的判断（留着，别再犯）

1. **"A11 的亮度计算不看 alpha"** —— 错。`ColorUtilsFromCompat.calculateContrast`
   在 `alpha(foreground) < 255` 时会先 `compositeColors(foreground, background)`（
   `work/y7s-a11/A11-ContrastColorUtil.java:489-491`），我们的 `A11Palette.contrast` 也是同一实现。
   影响：`findAlphaToMeetContrast` 的二分**是有意义的**，它从当前 alpha 往上找
   **最小的够用 alpha**（下界=startAlpha、上界=255，返回 `high`）⇒ A11 只会把文字**加实**，
   绝不会调淡。这条性质正是"按钮/正文看得清"的来源。
2. **`config_tintNotificationActionButtons` 的读法** —— A11 原文是
   `mTintActionButtons = !(IS_TIER_VOS || !config)`，config 实测 = true（`0x011100ef`）。
   所以非 VOS 层 ⇒ 一定着色；别把它读成"config 为真就着色素色"，它是**与 IS_TIER_VOS 一起**决定的。

## §14 实施进度（a11 通知卡复刻）

已完成（编译通过，产物哈希见 DEVLOG 当日条目）：
- `A11NotifSpec`：§2/§5/§9/§12 的常量与纯计算（底色、主/副文色、`findAlphaToMeetContrast`
  照 A11 原文重写、按钮着色、水波纹、backdrop 缩放/模糊/tint 常数、§4a 的矩阵与 stops）。
- `A11ArtRelief`：§4a 的逐步顺序（裁方→第一段 DST_IN(0/0.4/1)→浮雕层 alpha 127
  →第二段 DST_IN(0/0.6/1)→叠原图）。
- `MediaCardFeature` a11 分支：底色不再"恒深卡"（那是 OS5/A15 的 computeBackgroundColor）；
  主/副文改用 vivo-res 真值 + alpha 二分；按钮统一 48dp/内缩 8dp/marginEnd 2dp 且与正文同色；
  封面尺寸改成"边长=内容块高"、CENTER_INSIDE、无圆角无底板，并套上 §4a 的浮雕渐变；
  排版统一为 MediaStyle 模板的 14sp medium + 12**dp** 副文。

未完成（按优先级）：
1. 离线自检 `work/a11_notif_selftest.py` 挂进 `work/offline-all.sh`（探针 NotifProbe 已就绪）。
2. 真机（Z8 上把媒体卡样式设成 a11）取 `a11 palette / a11 art / a11 art relief / a11 card spec`
   见证，核对按钮色与正文同值、封面为正方形且无圆角。
3. `isColorizedMedia` 的 `android.colorized` 显式关闭分支（本类拿不到 sbn，见代码内注释）。
4. QS 侧常量（A11MediaSpec 里 TITLE_SP/ARTIST_SP/SEEK_*/BUTTON_CENTER_DP/ART_*_DP）与
   通知卡用途混淆的历史引用清理；保留数值但改名，避免下一个模型再串。

---

---

## §15 第二轮逆向（11-03 上午）：进度条、排版单位、图标 scaleType 的真值

这一轮的问题很具体：**卡片里那条进度条和两端的"0:00"到底是什么颜色、多粗、摆在哪儿**，
以及**标题/副文到底是几 sp**。之前这些值要么借用 QS 播放器（`media_view.xml`），
要么按"通知副文一般是 12sp"的常识填，都属于推测。本轮全部改成二进制读出来的事实。

取证命令（都能重跑，产物已存档）：

| 问题 | 命令 | 产物 |
| --- | --- | --- |
| 卡片进度条布局 | `aapt2 dump xmltree --file res/layout/notification_material_media_seekbar.xml framework-res.apk` | `work/y7s-a11/xml2_fw_media_seekbar.txt` |
| 单颗动作按钮布局 | 同上 `res/layout/notification_material_media_action.xml` | `work/y7s-a11/xml2_fw_media_action.txt` |
| **展开卡**骨架 | 同上 `res/layout/notification_template_material_big_media.xml` | `work/y7s-a11/xml2_fw_big_media.txt` |
| line1 / text 的样式 | 同上 `res/layout/notification_template_part_line1.xml`、`notification_template_text.xml` | 本文 §15-5 |
| 资源 id → 名字 → 数值 | `python work/a11_res.py --dump work/y7s-a11/fw_res.txt --name …/--find …` | 本文各表 |
| `attr/scaleType` 的枚举 | `grep -A 20 "resource 0x0101011d attr/scaleType" fw_res.txt` | §15-4 |
| 谁 inflate 进度条 / 谁染色 | `jadx-proj/sources/com/android/systemui/statusbar/notification/row/wrapper/NotificationMediaTemplateViewWrapper.java` | §15-6/§15-7 |
| `originalIconColor` 到底是什么色 | `bash work/y7s-a11/jdx.sh framework.jar android.view.NotificationHeaderView A11-NotificationHeaderView.java`（再 `com.android.internal.widget.CachingIconView`、`android.app.Notification`） | §15-8 |

### §15-1 读 aapt2 dump 的两个陷阱（先学会读，才不会被数值骗）

1. **`layout_width=-1/-2` 不是 dp**：`0xffffffff=-1=MATCH_PARENT`、`0xfffffffe=-2=WRAP_CONTENT`，
   是 `Res_value` 的枚举复用，别去换算。
2. **带负号的尺寸被 aapt2 印成大正数**。TYPE_DIMENSION 的 `data = (mantissa<<8)|(radix<<4)|unit`，
   aapt2 用**无符号** `data>>8` 印，于是 `-12dp` 印成 `16777204.000000dp`。
   正确读法：取 `data` 低 32 位，`mantissa = (int32)data >> 8`（**符号扩展**），单位 = `data & 0xf`
   （`0=px 1=dip 2=sp 3=pt`）。本文所有负边距都按这条重算过：
   `0xfffff401 → -12dp`、`0xffffe801 → -24dp`、`16777195 → -24dp`。
   （`16777195*256 = 0xFFFFE8_00`，符号扩展后正是 -24。）

### §15-2 动作按钮（`notification_material_media_action.xml`，展开/收起同一份）

```
ImageButton  style=@style/Widget.Material.Button.Borderless.Small(0x0103025b)
  layout_width = layout_height = @dimen/media_notification_action_button_size  = 48.000000dp   (0x010501a5)
  padding(上下左右) = 8.000000dp
  layout_marginEnd  = 2.000000dp
  background = @drawable/notification_material_media_action_background (0x01080618)
  visibility = 2(INVISIBLE)   ← 绑定时 bindMediaActionButton 再置 0/8
```
⇒ `A11NotifSpec.BUTTON_DP=48 / BUTTON_PADDING_DP=8 / BUTTON_MARGIN_END_DP=2` 是**通知卡自己的值**，
不再需要 QS 的 52dp 中间键（那是 `media_view.xml` 的 `MediaPlayer.Button`，见 §10）。
展开卡里 5 颗按钮 `action0..4`(0x01020198..0x0102019c) **同布局同尺寸**，中间那颗不放大。

### §15-3 `layout_gravity=0x00800035` 的拆解（封面在**右上**）

`Gravity.RELATIVE_LAYOUT_DIRECTION=0x00800000`、竖直段 `TOP=0x30`、水平段 `RIGHT/END=0x05`
⇒ `0x00800035 = TOP|END`。展开卡（`bigMediaNarrow`）里 header 的 `layout_gravity=0x00800003 = TOP|START`。
**封面在右上、正文列在左** —— 与模块现在的"封面在左"相反（这是 §17 结构项的直接依据）。

### §15-4 `scaleType=6` = **centerCrop**，不是 centerInside

framework-res 的 `attr/scaleType` 枚举原文（`fw_res.txt:1010-1019`）：
`matrix=0 fitXY=1 fitStart=2 fitCenter=3 fitEnd=4 center=5 centerCrop=6 centerInside=7`。
所以收起卡与展开卡的 `right_icon` 都是 **centerCrop**（收起态另有 `adjustViewBounds=true`，展开态没有）。
`A11MediaSpec.ART_COLLAPSED_SCALE`/`MediaCardFeature` 之前按 CENTER_INSIDE 实现是错的：
centerInside 会完整容纳（留白），centerCrop 会填满并裁掉长边——观感差别一眼可见。
浮雕产物本来就是正方形（`ImageGradientColorizer` 取 `min(w,h)` 裁方），所以 crop 只在
"视图宽高不等于 1:1"时才体现；把视图摆成正方形后两者等价，但默认值仍要写对，
否则会掩盖几何算错的情况（用 CENTER_INSIDE 时封面变小不会露馅）。

### §15-5 排版：卡片只有两档字号，且**都不是 16sp**

`notification_template_material_big_media.xml` 与收起模板 include 的是**同一对部件**：
`@layout/notification_template_part_line1`(0x010900b5) + `@layout/notification_template_text`(0x010900ba)。

```
line1(0x01020344) 横向 LinearLayout
 ├─ @android:id/text1(0x01020016)  textAppearance=@style/TextAppearance.DeviceDefault.Notification.Title(0x01030385)
 │    → 父 TextAppearance.Material.Notification.Title(0x01030203) → textSize=@dimen/notification_title_text_size = 14.000000**sp**
 │    → DeviceDefault 层 fontFamily=@string/config_headlineFontFamilyMedium（= sans-serif-medium 族）
 └─ @id/text_line_1(0x010204da)    style=@style/Widget.DeviceDefault.Notification.Text(0x01030470)（媒体卡不填内容）
@android:id/text(0x010204be) = com.android.internal.widget.ImageFloatingTextView
     style=@style/Widget.DeviceDefault.Notification.Text(0x01030470)
       → textAppearance=@style/TextAppearance.DeviceDefault.Notification(0x01030380)
         → 父 TextAppearance.Material.Notification(0x010301fe)
           textSize=@dimen/notification_text_size = **14.000000dp**（单位是 dp！不跟 fontScale）
           textColor=@color/notification_secondary_text_color_light / (night)_dark
       lineHeight=@dimen/… = 20.000000**sp**（Widget.Material.Notification.Text 直接给的）
     singleLine=true, ellipsize=MARQUEE(4), fadingEdge=0x1000, gravity=TOP, layout_marginTop=@dimen/notification_line2_padding(0x010501ef)
```

⇒ **修正一条本文 earlier 结论**：`notification_subtext_size = 12.0dp` 确实存在，但它只被
`TextAppearance…Notification.Info/Time/SubText` 用（`fw_res.txt:10313/10316/12922/12926/12929`），
媒体卡的 `@android:id/text` 走的是 `notification_text_size = 14**dp**`。
所以卡片两态的排版是**同一套**：标题 14sp medium、副文 14dp（dp 不跟 fontScale 这个坑不变）。
`A11MediaSpec.TITLE_SP=16/ARTIST_SP=14/TIME_SP=14` 是 QS 播放器的，通知卡不再引用（§10 的串台）。

### §15-6 卡片进度条本体（`notification_material_media_seekbar.xml` 全文翻译）

```
LinearLayout  id=@android:id/notification_media_progress(0x010203b2)  orientation=vertical
              w=match_parent h=wrap_content layout_alignParentBottom=true（在竖直 LinearLayout 里无效，忽略）
 ├─ SeekBar   id=@android:id/notification_media_progress_bar(0x010203b3)
 │    style=@style/Widget.ProgressBar.Horizontal(0x0103001f)
 │    paddingTop=paddingBottom=24.000000dp        ← 撑出 48dp 的触控带（视觉轨道只有 3dp）
 │    layout_marginTop=-12dp  layout_marginBottom=-24dp   ← 把上下留白再收回，压进卡片
 │    maxHeight=3.000000dp                        ← **轨道厚度 3dp 的真来源**
 │    splitTrack=false  clickable=true  w=match_parent h=wrap_content
 └─ FrameLayout id=@android:id/notification_media_progress_time(0x010203b4)
      w=match_parent h=wrap_content layout_marginBottom=11.000000dp
   ├─ TextView @android:id/notification_media_elapsed_time(0x010203b1) gravity=TOP|START alignParentLeft
   │    layout_marginStart=@dimen/notification_content_margin_start = 16.000000dp   style=0x01030470
   └─ TextView @android:id/notification_media_total_time(0x010203b6)  gravity=TOP|END  alignParentRight
        layout_marginEnd=@dimen/notification_content_margin_end = 16.000000dp       style=0x01030470
```

⇒ 三条以前"看着像"的实现被推翻：
1. 时间**不在进度条左右两侧**，而是在条**下方**，一个贴左一个贴右，边距 = 通知内容边距 16dp；
2. 字号与副文同档（`Widget.DeviceDefault.Notification.Text` = 14**dp**），不是 10.5sp/12dp；
3. 轨道 3dp 的来源是这条布局的 `android:maxHeight`，与 QS 的 `qs_media_enabled_seekbar_height`
   数值巧合相同、出处完全不同 —— 以前记成"借用 QS"，现在更正。

### §15-7 进度条的显隐/数据/染色：`NotificationMediaTemplateViewWrapper`（A11 SystemUI，classes2.dex）

逐条原文（`jadx-proj/sources/.../wrapper/NotificationMediaTemplateViewWrapper.java`）：

- **inflate 时机**：`resolveViews()` 里
  `ViewStub(R.id.notification_media_seekbar_container).setLayoutResource(R.layout.notification_material_media_seekbar).inflate()`
  （:232-243），随后 `findViewById` 拿 `notification_media_progress_bar / _elapsed_time / _total_time`。
- **收起态要不要条**：`token == null || ("media".equals(view.getTag()) && !mMediaManager.getShowCompactMediaSeekbar())`
  ⇒ 条 GONE（:186-194）。而 `getShowCompactMediaSeekbar()` =
  `DeviceConfig.getProperty("systemui","compact_media_notification_seekbar_enabled")`（:217，另有内容观察器 :87），
  **默认 false** ⇒ A11 默认**只有展开卡有进度条**。模块的收起态"没有条"与原生一致（不是省略）。
- **时长未知**：`metadata.getLong(METADATA_KEY_DURATION) <= 0` ⇒ 条 GONE（:209-222）。
- **进度标度**：`mSeekBar.setMax((int) durationMs)`、`setProgress((int) positionMs)` —— 用**毫秒**当标度，不是 0..1000。
- **时间文本**：`DateUtils.formatElapsedTime(ms / 1000)` ⇒ `m:ss`，超过一小时自动变 `h:mm:ss`。
- **刷新节拍**：`PROGRESS_UPDATE_INTERVAL = 1000`；`startTimer()` 只在 `mView.isShown()` 时建
  `Timer(true)`，`TimerTask → mHandler.post(tick)`；`tick` 里 `getPlaybackState()` 为 null 就 `clearTimer()`。
  重启条件：`onPlaybackStateChanged` 里 `state == 3`(STATE_PLAYING) 且条可见且没在跑（:113-121），
  `onAggregatedVisibilityChanged(true)` 同样条件再启（:75-88）。
- **可拖动判据**：`canSeekMedia = (playbackState.getActions() & 256) != 0`（`ACTION_SEEK_TO`）。
  不可拖动时**不是隐藏条**，而是 `setScrubberVisible(false)`：
  `mSeekBar.getThumb().setAlpha(0)` + `setEnabled(false)`（:264-272）—— 轨道和时间仍然显示，只是没滑块、拖不动。
- **染色（`updateSeekBarTint`，:313-326）—— 这是"颜色从哪来"的终点**：
  ```java
  int c = getNotificationHeader().getOriginalIconColor();
  elapsed.setTextColor(c);  total.setTextColor(c);
  total.setShadowLayer(1.5f, 1.5f, 1.5f, mBackgroundColor);   // 阴影色 = 卡片底色
  seekbar.setThumbTintList(valueOf(c));                       // alpha 255
  seekbar.setProgressTintList(valueOf(c).withAlpha(192));     // 已走 75%
  seekbar.setProgressBackgroundTintList(…withAlpha(128));     // 轨道 50%
  ```
  ⇒ 三个 alpha 是 **255 / 192 / 128**，基色是 `originalIconColor`，**不是**固定白阶；
  副文色、`0xC0FFFFFF/0x80FFFFFF` 那套都属 QS，通知卡不该引用。

### §15-8 `originalIconColor` 的完整推导链（回答"基色到底是哪一个"）

```
NotificationMediaTemplateViewWrapper.updateSeekBarTint
  → NotificationHeaderViewWrapper.getOriginalIconColor()  (:156 → mIcon.getOriginalIconColor())
  → android.view.NotificationHeaderView.getOriginalIconColor()  (A11-NotificationHeaderView.java:284)
      return this.mIcon.getOriginalIconColor();            // mIcon = CachingIconView(id/icon)
  → com.android.internal.widget.CachingIconView            (A11-CachingIconView.java:193-200)
      @RemotableViewMethod setOriginalIconColor(int) → mIconColor；getOriginalIconColor() 返回它
  → 写入者是 RemoteViews 调用，来自 android.app.Notification$BuilderExt.processSmallIconColor
      (A11-Notification.java:3202-3214)
        colorable = !isLegacy() || isGrayscaleIcon(smallIcon)
        color     = isColorized(p) ? getPrimaryTextColor(p) : resolveContrastColor(p)
        if (colorable) contentView.setDrawableTint(ID_ICON, color, SRC_ATOP)
        contentView.setInt(ID_ICON, "setOriginalIconColor", colorable ? color : 1)
```
而 `getPrimaryTextColor` 来自 `ensureColors(p)`（`A11-Notification.java:2473-2517`）：
无前景色（App 没 `setForeground`，媒体通知基本都不设）且着色时，
`primary = resolvePrimaryColor(bg, night)`、`secondary = resolveSecondaryColor(bg, night)`，
再各跑一次 `findAlphaToMeetContrast(…, bg, 4.5)`。

⇒ **结论（可执行）**：着色媒体卡里，进度条基色 = 卡片主文字色 = `primaryTextColor`（同一个整数）。
所以"条 + 时间 + 动作图标 + 小图标"在 A11 着色卡上是**同一个颜色**，只是 alpha 分 255/192/128 三档；
这也解释了为什么原生"按钮看得清"——它从来不是深色底上的深色图标。
`MediaStyle.bindMediaActionButton`（`A11-Notification.java:4969-4994`）的图标色正是同一个
`getActionColor(p) = isColorized ? getPrimaryTextColor(p) : resolveContrastColor(p)`（:5035-5037），
水波纹 = `alpha(colorControlHighlight) 换到 tint 色的 RGB`。

### §15-9 展开卡的纵向堆叠（`xml2_fw_big_media.txt` 全翻译，结构项的依据）

```
MediaNotificationView(tag="bigMediaNarrow", id=status_bar_latest_event_content, bg=#00000000)
├─ ImageView right_icon      TOP|END，w/h=0dp（运行时被 onMeasure 改成 contentH 正方形），scaleType=centerCrop
├─ include notification_template_header   layout_gravity=TOP|START，height=@dimen/media_notification_header_height
│        = @dimen/notification_header_height = 50.000000dp (0x010501d5)
└─ LinearLayout notification_media_content  orientation=vertical  w=match h=wrap
   ├─ LinearLayout notification_main_column  marginTop=@dimen/notification_content_margin_top=46dp(0x010501cb)
   │      marginBottom=@dimen/notification_content_margin=20dp(0x010501c8)
   │      minHeight=@dimen/notification_min_content_height=39dp(0x010501e6)
   │      marginStart=@dimen/notification_content_margin_start=16dp  marginEnd=@dimen/notification_content_margin_end=16dp
   │  ├─ include notification_template_part_line1   （歌名）
   │  └─ include notification_template_text         （艺人名）
   ├─ LinearLayout media_actions  orientation=horizontal layoutDirection=LTR(0)
   │      style=@style/NotificationMediaActionContainer(0x01030335)：
   │        gravity=TOP(0x30)、paddingBottom=@dimen/media_notification_actions_padding_bottom=12dp(0x010501a6)
   │        layout_width/height=wrap、layout_marginTop=**-24dp**、paddingStart=8dp
   │  └─ include ×5 notification_material_media_action（action0..4，见 §15-2）
   └─ ViewStub notification_media_seekbar_container  w=match h=wrap   （内容见 §15-6）
```
垂直留白的算账方式（A11 用负 margin 抵消 padding）：
`main_column.marginBottom 20dp` + `media_actions.marginTop -24dp` ⇒ 按钮带向上咬 4dp；
`SeekBar.paddingTop/Bottom 24dp` + `marginTop -12dp/marginBottom -24dp` ⇒ 48dp 触控带只占约 12dp 视觉高度；
时间行 `marginBottom 11dp` 收尾。

⇒ 结构差集（模块 vs A11）：**封面右/左反了**、**按钮带在进度条之上**（模块在下面）、
**时间在条下方左右对齐**（模块在条左右）。详见 §17。

### §15-10 `IS_TIER_VOS` 的两个分支（以前是待办，现在只落地了"取值"，机型归属仍待定）

`android.app.Notification` 静态字段（`A11-Notification.java:349`）：
```java
IS_TIER_VOS = FtBuild.getTierLevel() != 0 && FtBuild.getOsName().equalsIgnoreCase("vos");
```
受影响且**读得出数值**的地方：
- `setContentMinHeight`（:2527-2539）：VOS 用 `@dimen/notification_min_content_height = 39dp`；
  非 VOS 用 `0x030c0257 = dimen/vigour_notification_min_content_height = **61.000000dp**`
  （vivo 静态共享库 `work/y7s-a11/vivo-res.apk`，`python work/a11_res.py --dump work/y7s-a11/vivo_res.txt --name 0x030c0257`）。
- `MediaStyle.makeMediaContentView/makeMediaBigContentView`（:4996-5070）：非 VOS 时
  模板换成 `0x03030ec1 / 0x0303…`（vivo `vigour_notification_template_material_media / _big_media`），
  VOS 时用 AOSP 的 `notification_template_material_media / _big_media`。
- `MediaNotificationView.onMeasure/onLayout`（§2）：只有 `IS_TIER_VOS` 才把头图 paddingEnd
  改成 `notification_content_image_margin_end`（= 8dp，0x010501c7），且 `mImagePushIn>0` 时
  VOS 才真的把封面**左推**（`layout(left+pushIn …)`，RTL 时取负），非 VOS 分支等于不推。

**仍未证实的**：Y7s 本机 `getTierLevel()/getOsName()` 到底是哪一支。本轮按"Y7s 是 OriginOS(VOS) 层"
假设取 **VOS 分支**（39dp 内容下限、AOSP 模板、封面会被 pushIn）。要坐实就在 Y7s 上跑
`adb shell getprop | grep -i "vivo.os\|tier"` 或用模块打一条 `FtBuild.getTierLevel()` 日志——
**这条属于"会影响 61/39dp 与模板选择"的判据，没坐实前别把它当事实**。

---

## §16 本轮落到代码的差集（数值级）

| 位置 | 以前 | A11 真值 | 证据 |
| --- | --- | --- | --- |
| 封面 scaleType | CENTER_INSIDE | **CENTER_CROP**（收起另有 adjustViewBounds） | §15-4 |
| 标题字号 | 展开 16sp / 收起 14sp | **两态都 14sp + medium 族** | §15-5 |
| 副文（艺人） | 12dp | **14dp**（`notification_text_size`，单位 dp） | §15-5 |
| 时间字号 | 10.5sp / 14sp | **14dp**，与副文同档 | §15-5/§15-6 |
| 时间位置 | 条的左右两侧 | 条**下方**，左右贴边、边距 16dp | §15-6 |
| 轨道厚度出处 | 借 QS 3dp | 本布局 `maxHeight=3dp` | §15-6 |
| 条配色 | secondary 轨道 + primary 实色 | 基色=primary，**255/192/128** 三档 alpha | §15-7 |
| 时间颜色 | primary/secondary 混用 | **=originalIconColor（=primary）**，且 total 有 `setShadowLayer(1.5,1.5,1.5,cardBg)` | §15-7/§15-8 |
| 不可拖动 | 直接禁用但滑块还在 | `thumb.setAlpha(0)` + `setEnabled(false)`，条与时间照常 | §15-7 |
| 进度标度 | 0..1000 | `max=(int)durationMs`、`progress=(int)positionMs` | §15-7 |
| 时间格式 | 自拼 `m:ss` | `DateUtils.formatElapsedTime(ms/1000)`（>1h 变 `h:mm:ss`） | §15-7 |
| 应用图标 | 16dp（QS `SOURCE_ICON_DP`） | **18dp**（`notification_header_icon_size` 0x010501d7） | §15-5/`fw_res.txt` |
| 收起态有无条 | 视作"没落点" | A11 默认就是没有（`compact_media_notification_seekbar_enabled`=false） | §15-7 |

结构级（封面在右上、按钮带在文字下、条+时间在按钮下）见 §17，属于未完成的实施项。

---

## §17 结构复刻靶子（写给下一个接手的模型，未开工部分）

按 §15-9 把 a11 分支的 `MediaCard.build` 排成 A11 的骨架（只动 a11，其它样式保持）：

1. 根 `FrameLayout`：封面 `right_icon` 语义 —— **TOP|END**，边长 = 内容块高（`applyA11ArtGeometry` 已有），
   且水平余量不足时按 `mImagePushIn` 把封面**向左推**（VOS 分支）而不是缩放。
2. 左列（垂直）自上而下：`header(50dp，模块里被藏掉/换成 18dp 应用图标)` →
   `main_column`（marginTop 46dp、marginBottom 20dp、minHeight 39dp、左右 16dp）内含标题+副文 →
   `media_actions`（paddingStart 8dp、paddingBottom 12dp、marginTop **-24dp**、gravity TOP、LTR 强置） →
   `seekbar`（3dp 轨道、上下 padding 24dp、marginTop -12dp、marginBottom -24dp） →
   时间行（elapsed START 贴左 16dp、total END 贴右 16dp、marginBottom 11dp）。
3. 主列 `marginEnd = min(可用宽 - 按钮带宽, contentH) + imageEndMargin + 16dp`，
   `imageEndMargin` 在 AOSP 模板里**没有声明 ⇒ 0**（`MediaNotificationView.onMeasure` 读的是
   `layoutParams.getMarginEnd()`，见 §2 与 §15-3）。`A11NotifSpec.mainColumnMarginEndPx` 已按这条写。
4. 负 margin 在这台机上是"观感"的关键：把 -24dp/-12dp 省掉，卡片会比 A11 高出约 36dp，
   封面正方形也跟着变大。**别为了省事把负 margin 拍平成正数**。

风险与验收：这一步改的是"行内所有元素的位置"，必须用几何见证判，不许用眼睛判。
见证建议打在布局完成后（`card.post`）：
`a11 geom art=[x+wxh] col=[x+wxh] actions=[y+h] bar=[y+h] time=[y..]`，
断言"封面右边缘贴卡片右内缩、封面高=内容块高、按钮带顶 < 条顶、时间行在条下方"。

---

## §18 本轮证伪与更正（自己打自己的记录）

1. **`scaleType=6` 我读成 CENTER_INSIDE**：查 `attr/scaleType` 枚举前用的"枚举序号"记忆，
   `ImageView.ScaleType.ordinal()` 与 attrs 的枚举值**恰好差一位**（CENTER=5/CENTER_INSIDE=7），
   所以照抄序号必错。规则：**属性整数一律回 framework-res 的 attr 枚举表核对**。
2. **"副文 12dp"是查到了资源、接错了视图**：`notification_subtext_size=12dp` 真实存在，
   但媒体卡的 `@android:id/text` 用的是 `notification_text_size=14dp`。
   以后写排版断言必须从**布局里那个 View 的 style/textAppearance 逐跳**走（§15-5 那张链），
   不能从"通知一般长这样"跳。
3. **轨道 3dp 的出处记错了**：数值对、来源错（记成 QS 的 `qs_media_enabled_seekbar_height`）。
   来源错会让下一个人以为"通知卡没有依据"，进而把它归到"借用"清单里随便改。
4. **`aapt2 dump xmltree` 对负 dimension 的印法**（§15-1-2）害我第一轮把 -12dp 读成
   "16777204dp 的垃圾值"，差点当成 vivo 的自定义属性跳过。负 margin 恰恰是这张卡的排布核心。
5. **`getOriginalIconColor()` 不是"小图标的颜色"而是"正文主色"**：如果只看方法名，
   会以为进度条跟的是 app icon 的颜色；沿到 `processSmallIconColor` 才知道它等于
   `getPrimaryTextColor(p)`（着色时）。这条链是本轮最值钱的一张图。

---

## §19 下一轮的最短路径

1. §17 的结构重排（a11 分支）+ 几何见证。
2. `android.colorized` 显式关闭分支：把 `sbn` 传进 `MediaCard`，按
   `(colorized==null || colorized) && hasMediaSession()` 原文判（§15-8 的 `isColorizedMedia`）。
3. 水波纹：`bindMediaActionButton` 的 `rippleColor = alpha(colorControlHighlight) + tint 的 RGB`
   （A11 里按钮背景就是 `notification_material_media_action_background`）；
   模块现在把按钮背景置 null，若要 1:1 需要恢复一个 ripple 层。
4. 锁屏 backdrop（§4b）仍未做，`A11NotifSpec` 已备好常数（scale=max(w/6,h/6) 取整、
   blur 25.0f、swatch tint alpha 178、relief alpha 127），
   缺的是"RenderScript blur 的软件等价 + 真机核对"。
5. Y7s 上 `IS_TIER_VOS` 归属（§15-10），决定 61/39dp 与 vigour 模板两条路。

---

## §20 决定性的一跳：Y7s 走的是 **非 VOS 分支**，所以"安卓 11 那张卡"= vigour 模板

§7-4/§7-7/§15-10 悬着的 `IS_TIER_VOS` 本轮**证掉了**（不是猜）：

```java
// work/y7s-a11/A11-FtBuild.java:17,34（framework.jar 反编译）
private static final String mOsName = SystemProperties.get("ro.vivo.os.name", "unknown");
private static final String VIVO_TIER_LEVEL = SystemProperties.get("ro.system.vivo.tierlevel", "0");
public static int getTierLevel() { if (TextUtils.isEmpty(VIVO_TIER_LEVEL)) return 0; … }
// A11-Notification.java:349
IS_TIER_VOS = FtBuild.getTierLevel() != 0 && FtBuild.getOsName().equalsIgnoreCase("vos");
```
```
$ adb -s IBR8GEMFJNBECIFA shell getprop ro.vivo.os.name          → Funtouch
$ adb -s IBR8GEMFJNBECIFA shell getprop ro.system.vivo.tierlevel → (空)  ⇒ getTierLevel()=0
```
两个条件都不成立 ⇒ **Y7s 上 `IS_TIER_VOS = false`**。
⇒ `MediaStyle.makeMediaContentView / makeMediaBigContentView`（`A11-Notification.java:4996-5070`）
取的是**非 VOS 那一支**：
`0x03030061 = layout/vigour_notification_template_material_media_q`（收起）
`0x03030054 = layout/vigour_notification_template_material_big_media_q`（展开）
（id 由 `vivo-res.apk` 的 `aapt dump resources` 反查：`python work/a11_res.py --dump work/y7s-a11/vivo_res.txt --name …`）

**所以本项目要复刻的那张卡，数值以 vigour 布局为准**；§15 读到的 AOSP 值是"另一半分支"。
两边都记下来，实现里每个常数标注 `(dev)` / `(aosp)`，下一个模型别再把它们混成一套。

### §20-1 设备真实卡片常数表（全部来自二进制）

| 项 | 设备（`IS_TIER_VOS=false`，vigour） | AOSP A11（`IS_TIER_VOS=true` 才有） | 证据 |
| --- | --- | --- | --- |
| 收起卡根 | **普通 `FrameLayout`**（不是 MediaNotificationView！） | `MediaNotificationView` | `xml2_vigour_*media_q.txt:2` |
| 展开卡根 | `MediaNotificationView` tag=`bigMediaNarrow` | 同 | `xml2_vigour_big_media_q.txt:2` |
| 封面 gravity / scaleType | TOP\|END `0x00800035` / **6=centerCrop** | 同 | §15-3/§15-4 |
| 收起封面宽高 | `wrap_content × match_parent` + adjustViewBounds | `0dp × 0dp` + adjustViewBounds | 两份 big/compact dump |
| 动作按钮 | **36dp × 40dp**，paddingTB 8dp、paddingLR **6dp**、marginStart **2dp**、marginEnd 2dp，scaleType **7=centerInside**，background `@android:color/transparent`(0x0106000d)，style `?attr/borderlessButtonStyle` | 48×48、padding 8dp 四边、marginEnd 2dp、有 ripple 背景 | `vigour_notification_material_media_action_q` / §15-2 |
| 动作带容器 | gravity TOP、w/h wrap、layoutDirection LTR、marginStart **14dp**、marginEnd **20dp**、6 颗（含 `media_seamless` 常 GONE） | `@style/NotificationMediaActionContainer`：paddingBottom 12dp、marginTop **-24dp**、paddingStart 8dp、5 颗 | big media dump |
| 文字列 | marginTop **42dp**、marginStart/End **20dp**（无 bottomMargin） | marginTop 46dp、marginBottom 20dp、marginStart/End 16dp | big media dump |
| 列最小高 | `setContentMinHeight` → `vigour_notification_min_content_height` = **61dp** | `notification_min_content_height` = 39dp | `A11-Notification.java:2527-2539` |
| 标题 | `TextAppearance.Vigour.Material.Notification.Title`(0x030f00b5) → `vigour_notification_title_text_size` = **14.0sp**，color `vivo_notification_title_text_color #ff000000`（`_light #ffffffff`），**外加 `textStyle=BOLD(1)`**，ellipsize END，singleLine | `…Notification.Title` → `notification_title_text_size=14sp` + `config_headlineFontFamilyMedium` | `vivo_res.txt:50653`、part_line1 dump |
| 副文(艺人) | `TextAppearance.Vigour.Material.Notification`(0x030f00ac) → `vigour_notification_text_size` = **12.0sp**，color `#ff4d4d4d`（`_light #b3ffffff`），marginTop `vigour_notification_text_margin_top` = **0.5dp** | `Widget.DeviceDefault.Notification.Text` → `notification_text_size` = **14dp**（单位 dp） | `vivo_res.txt:50620`、template_text dump |
| 头部行 | `NotificationHeaderView`：paddingTop **16dp** / bottom **12dp** / start/end **20dp**，height `vigour_notification_header_height_p` = **44dp**，app 小图标 **16dp×16dp**（marginEnd 8dp），`app_name` 用 `TextAppearance.Vigour…Header` = **12sp** / `#ff4d4d4d` | header 高 `notification_header_height` = 50dp，图标 `notification_header_icon_size` = **18dp** | `vigour_notification_template_header_q` dump |
| 进度条 | **与 AOSP 同一份布局**（见 §20-2） | `maxHeight 3dp`、padTB 24dp、marginTop -12dp、marginBottom -24dp、时间在条下方左右 16dp、时间底距 11dp | §15-6 |

### §20-2 为什么进度条"跟着 AOSP 走"：ViewStub 的 `android:layout` 被 SystemUI 覆盖

`vigour_notification_template_material_big_media_q.xml:87` 的 ViewStub 确实写了
`android:layout="@layout/vigour_notification_material_media_seekbar"`（0x03030045，
那份的时间是 `textSize=12sp`、marginTop **-8dp**、时间底距 **16dp**）。
但 `NotificationMediaTemplateViewWrapper.resolveViews()`（**设备自己的 SystemUI**，
`jadx-proj/sources/.../NotificationMediaTemplateViewWrapper.java:232-236`）在 inflate 之前无条件：

```java
viewStub.setLayoutInflater(LayoutInflater.from(viewStub.getContext()));
viewStub.setLayoutResource(R.layout.notification_material_media_seekbar);   // ← com.android.internal.R，AOSP 那份
this.mSeekBarView = viewStub.inflate();
```
`setLayoutResource()` 覆盖 XML 里的 `android:layout` ⇒ **运行时 inflate 的是 AOSP 布局**。
⇒ 设备上的时间字号取 **14dp**（AOSP `notification_text_size`）、marginTop **-12dp**、底距 **11dp**；
   vigour 那支（12sp/-8dp/16dp）**不会被走到**，除非有人改 SystemUI。
这条是"布局文件里写着 X，屏幕上却是 Y"的典型：判据要打在**写入者**（谁最后 set 了它），
不是打在声明处。

### §20-3 于是 a11 样式的最终靶子（设备真实观感）

```
FrameLayout 卡（底色 = 左半幅 background swatch，见 §9）
├─ 头部行  TOP|START，高 44dp，padding 16/12 上下、20 左右：16dp app 图标 + 12sp app 名（#4d4d4d/主色链覆盖）
├─ 内容列  TOP|START，竖向：
│   ├─ 文字列 marginTop 42dp、marginStart/End 20dp、minHeight 61dp
│   │    ├─ 标题 14sp + medium 族 + BOLD，ellipsize END，色 = primaryTextColor（§9 链）
│   │    └─ 副文 12sp，marginTop 0.5dp，色 = secondaryTextColor
│   ├─ 动作带 marginStart 14dp、marginEnd 20dp、LTR、gravity TOP：每颗 36×40、padding 8/6、margin 2/2、
│   │        centerInside、无背景、图标色 = primaryTextColor（§5b/§15-8）
│   └─ 进度条（AOSP 那份）：3dp 轨道、padTB 24、marginTop -12、marginBottom -24、
│          thumb=primary(255) / progress=primary(192) / track=primary(128)，
│          不可 seek ⇒ thumb alpha 0 + setEnabled(false)（条与时间仍显示）
│          时间行在条**下方**：elapsed 贴左 16dp、total 贴右 16dp，14dp，色 = primary，
│          total 加 shadowLayer(1.5,1.5,1.5, 卡底色)，底距 11dp
└─ 封面  TOP|END，正方形（边长 = 内容列高），centerCrop，无圆角、无底板，
         图像 = ImageGradientColorizer 的浮雕产物（§4a/§15-8）
```
收起态：根是普通 FrameLayout、封面 `wrap×match_parent + adjustViewBounds`、**没有进度条**
（`compact_media_notification_seekbar_enabled=false`，§15-7），标题/副文与展开同一套（14sp BOLD / 12sp）。

---

## §21 第二轮实施完成（11-03 17:5x–18:2x，含真机门禁）

`A11NotifSpec` 与 `MediaCardFeature` 的 a11 分支已按 §15/§16/§17/§20 全部改完，
**离线 + 真机门禁双绿**（证据：`work/z8os3-live/a11-card-os3.log`，
`verify_os5_mirror.py --media-only` 打出 `133 条通过 / 0 条失败`；
`bash work/offline-all.sh` 全绿，A11 通知卡规格自检 82 条比对 + 15 个负对照全部有效）。

装机产物哈希 `e0856b5e1f3b`（= 设备 `/data/app/.../base.apk` 的 sha256 前 12 位，门禁脚本第 0 步核对过）。

### §21-1 真机读数（Z8 的 DSU/A13 环境，style=a11，density=3.0，fontScale=1.0）

```
a11 card spec artSide=contentHeight btnPad=24 btnEnd=6 btn=108x120 src=48
  titlePx=42 artistPx=36 timePx=42 trackDp=3 colTop=126 colSide=60 colMinH=183
  headH=132 actMargin=42/60 radius=0 hostCorner=6
   → 36×40dp 按钮、16dp 头部图标、42/20/61/44dp 的列与头部边距全部落在整数 px 上；
     radius=0 而 hostCorner=6 ⇒ "没跟宿主主题"有对照值可查。
a11 art state=expanded  size=333 contentH=333  scaleType=CENTER_CROP clip=false
     titlePx=42 subtextPx=36 timePx=42 titleMedium=1 seekVis=0
a11 art state=collapsed size=585 contentH=585  ... seekVis=8        ← 收起态没有紧凑进度条（§15-7）
a11 seek vis=0 expanded=1 session=1 dur=313000 / vis=8 expanded=0 session=0 dur=0
a11 seek prog=c0000000 track=80000000 base=ff000000 thumb=255 enabled=1
     thicknessDp=3.0 totalShadow=1/ffd8d8c8 elapsedShadow=0
   → 同一基色三档 alpha（255/192/128）、3dp 轨道、只有 total 带卡底色阴影，都是回读真实画笔值得到的；
     thumb=0/enabled=0 的帧也在（会话未就绪那一拍），两者始终同向。
a11 palette expanded bg=ffd8d8c8 primary=ff000000 secondary=ff4d4d4d contrast=14.58 grid=10368/12528
```

### §21-2 本轮新收的三条"结构性"事实（写进实现，不只是文档）

1. **卡片根从 LinearLayout 改成 FrameLayout**（§20-3）：封面 TOP|END、内容列 TOP|START、
   头部行 TOP|START 绝对定位 —— `main_column.marginTop=42dp` 只有在"头部行是叠加而非流式"时才是那个值。
2. **进度条容器的显隐是一条判据链**（§15-7），不是"要不要画"：
   `controller!=null && duration>0 && expanded` ⇒ VISIBLE，否则 GONE。
   新增 `a11 seek vis=` 见证 + 收起/展开两态的断言，把"收起态画了条"变成可判红的失效。
3. **不可拖 ≠ 藏条**（§15-7）：thumb alpha 0 + `setEnabled(false)`，条与时间照常；
   旧实现把整条 bar 压到 0.85 是自创的，现在 a11 分支不再走那条。

### §21-3 仍未收口的（下一位从这里接）

1. `android.colorized` 被 app 显式写 false 的分支：`MediaCard` 现在拿不到 `sbn`，按"有会话即着色"处理。
   补法：`restyle()` 侧把 `entry.getSbn()` 传进 `MediaCard.build(...)`，
   照 `Notification.isColorizedMedia()` 原文判
   （`colorized = extras.get(EXTRA_COLORIZED); (colorized==null||colorized) && hasMediaSession()`）。
2. 锁屏 backdrop（§4b/§11）：常数齐了（scale=max(w/6,h/6)、blur 25.0f、tint alpha 178、relief 127），
   缺软件模糊实现 + 真机核对。
3. 水波纹：设备分支那颗按钮背景是 `@android:color/transparent` ⇒ **原生就没有波纹**，
   所以这项在"复刻设备观感"里其实已经正确；只有走 AOSP 那一支才需要 `rippleColor()`。
4. `A11MediaSpec`（QS 播放器常数）现在只被 `work/y7s_a11_evidence_check.py` 用来对表，
   通知卡路径对它的引用已清零；类头已写明"不要用进通知卡"。
5. OS5 宿主（A15，退出 DSU 才是）上的 a11 卡片观感没在本轮验证——本轮所有真机读数来自
   DSU(A13) 环境；`work/a11-card-gate.sh ... os5` 需要人工解锁，留给早上的视觉验收。

---

## §22 用户否决之后的两处样式更正（11-04 早）

用户看完成像后给了三条，其中两条属于"我按 A11 的**通知卡**画，但产品要的是 A11 的**播放器**"：

### 22-1 进度条改按 QS 播放器（`media_view.xml:23-40 + 116-128`）

| 项 | 通知卡那一版（先前实现） | **播放器那一版（现在的产品）** | 出处 |
| --- | --- | --- | --- |
| 两枚时间的位置 | 条**下方**，左右各贴一端 | 条**左右两端同一行**，条 `width=0dp` 吃剩余宽 | `media_view.xml` RelativeLayout |
| 时间字号 | 14.0**dp**（不跟 fontScale） | 14.0**sp**（跟 fontScale） | `qs_media_*` 文本尺寸 |
| 条的触控带 | `paddingTop/Bottom=24dp` | `paddingTop/Bottom=16dp` | 同上 |
| 轨道粗 | 3dp | 3dp（同一个 dimen 值来源不同：`maxHeight=3dp` vs `qs_media_enabled_seekbar_height=3dp`） | §15-6 / 本轮 |
| thumb | 不可拖时 alpha 0 | **常驻可见**（alpha 255） | `media_seekbar_*` |
| tint | progress 192 / track 128（同一个 alpha 对） | 保持 192/128，但配在**取色链算出的主文字色**上 | 见下 |

- QS 那两支 tint 原文是 `#c0ffffff`/`#80ffffff`（半透明白）。它敢用白是因为 QS 底恒定深；
  我们的卡片底色来自封面（可能是 `d8d8c8` 那种浅色），照抄白就会"控件看不清"——
  正是用户最初报的那条。所以**保留同一对 alpha，换基色**为 `a11Primary`。
- 通知卡那一版的常数没有删，留在 `A11NotifSpec.SEEK_*` 里做对照，`PLAYER_SEEK_*` 才是喂给卡片的。
- 门禁读回的是**视图真实状态**：`padTbPx=`（=16dp×density，回成 24dp 立刻红）、
  `thumbAlpha=255`、`timePx` 随 fontScale（`fontScale=1.3` 那条正例专门盯单位）。

### 22-2 封面右缘淡出的终点 = 当前卡片底色

`ImageGradientColorizer` 那两段横向 DST_IN 渐变是叠在**纯色底板**上的，底板颜色一变，
渐变就必须重做；原先缓存键只看封面哈希，于是"换歌/切深浅色"之后右缘留着旧底色的硬界。

现在：`a11ReliefBg` 进键、`a11RawArt` 留存原件（二次进入用原件重做，不在已淡出的位图上再叠一层），
取色链末尾 `syncArt()`。见证 `a11 art relief … color=` 与同一帧 `a11 palette … bg=` 必须同源
（验证器直接比这两个值，比"实现里再算一遍"有效）。

### 22-3 顺带纠正了自己一条判据

`expanded ⇒ 进度条必须可见` 是错的：A11 的判据是**三条与**（有会话 && 有时长 && 展开）。
会话被拆掉那一帧 `lastDurationMs` 还留着旧值，此时藏条是对的。所以 art 见证加了
`seekSession=`，断言改成三条件；同时保留"本轮展开帧里至少有一条 session=1 且 dur>0"的
取样守卫 —— 全是"该藏"的帧等于这条断言没跑过，判红而不是绿。

### 22-4 装机与读数（build `b47ff3304428`，DSU/A13，density=3.0）

- `work/a11-card-gate.sh … os3` 全绿：`padTB=48px`、`seekVis` 两态都对、
  `session=1/dur>0` 11 帧 + `session=0/dur=0` 6 帧、`relief color=` 与 `palette bg=` 同源。
- 建卡那一拍的行会被 logd 每进程 250/s 冲掉 ⇒ 规格见证改为低频重发 + 门禁缺证自动重投，
  细节见 `out/DEVLOG.md` 11-04 §2。

---

## §23 换歌时的真实时序（11-04 中午，用户报"底色滞后一首"逼出来的）

这条是本轮最值钱的一条事实，之前所有实现都把它当成"同时发生"：

**曲目事件先到，新一首的封面图后到。** 真机见证（同一次切歌，时间戳相邻）：

```
track changed, retinting: 鬼新娘…|Kael|147000
a11 palette … srcFrom=argb srcHash=1a13df86 bg=ffd8f0f8     ← 取色采到的还是上一首那张图
a11 art relief … hash=35f4616a color=ffd8f0f8                ← 新封面稍后到，浮雕换了，底色没换
```

三条推论，都已落到代码里：

1. **去重键必须按"输入变了"作废，不能只按曲目身份。** `refreshColors` 原来用
   `trackKey=曲名|歌手|时长` 去重，封面真的到达时那一拍被入口早退挡住 ⇒ 底色永久停在上一首。
   现在 `watchArtArrival()` 比较 Drawable 身份**与** `artHash`，变了就 `lastTrackKey=""` 再重取。
   修完同帧同源：`palette srcHash=bd385890 bg=fff8f8f8` ↔ `relief hash=bd385890 color=fff8f8f8`。
2. **"没有封面输入"必须清空，不许拿留存的旧图继续画。** 原来 `a11RawArt` 在二次进入时兜底，
   空窗期就把上一首的浮雕贴在新一首的卡上——用户看到的正是这个。
3. **封面的取色输入按 A11 用通知自己的 largeIcon，metadata 只兜底**（§22 已写），
   且非 BitmapDrawable 的那一类要按 `Icon.toBitmap()` 的思路栅格化（`largeIconDrawn`），
   否则 vivo 挂在 `android:id/art` 上的图会被判成"没有封面"，卡片退回浅色底、
   而屏幕上还是深色浮雕 ⇒ 用户报的"右边那枚时间没反色"。

顺带：卡片里那个 300ms 定时器（每拍 syncArt+refreshColors）既放大这条滞后，又让窗口
永不空闲（`uiautomator dump` 报 could not get idle state，取证取不到帧）。A11 自己
是 1 秒且只更新时间/进度，现在照它做，暂停就不挂。

验收：`verify_os5_mirror.py` 的 `relief hash ∈ palette srcHash` 就是这条的锁，
反例「A11 浮雕与取色不是同一张封面（滞后一首）」必须红；共 75 条反例全咬。
真机日志（build `c5a6001a8dd6`）`--media-only` 45/0。



- 每条"值"都必须能由本文件的命令重跑得到；命令与产物路径写在 §0 表格里。
- `--single-class` 失败/方法被跳过时，**必须**转 dexdump 字节码，不许用"应该是这样"补。
- 任何与 §6 差集不一致的实现改动，先补进本文档再动代码。
