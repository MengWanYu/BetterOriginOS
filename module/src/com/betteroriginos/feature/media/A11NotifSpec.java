package com.betteroriginos.feature.media;

/**
 * Android 11 **通知栏**媒体卡（MediaStyle 行）的规格与纯计算。
 *
 * 为什么单开一个类：模块原来的 a11 样式常量取自 A11 的 QS 播放器
 * （{@code media_view.xml / qs_media_album_size / @style/MediaPlayer.Button}），
 * 而我们改的是下拉栏里那张 MediaStyle 行 —— A11 里这是两套布局、两套尺寸、两套取色。
 * 证据与逐条来源见 out/LOG-1103-A11-MEDIACARD-REVERSE.md（§2/§5/§9/§11/§12），
 * 一手材料在 work/y7s-a11/（Y7s 真机的 SystemUI.apk / framework.jar / framework-res.apk /
 * vivo-res.apk 与 aapt dump）。QS 那套仍留在 A11MediaSpec 里，只作外观参考，不再喂给这张卡。
 *
 * 本类**不做任何 Android 静态初始化**：桌面 JVM 直接能跑，离线自检靠它比独立参考实现。
 */
public final class A11NotifSpec {

    private A11NotifSpec() {
    }

    /* ------------------------------------------------- 几何（§2 / §15 / §20）
       两套数值都读到了二进制，标注清楚谁生效：
       Y7s 的 `ro.vivo.os.name=Funtouch`、`ro.system.vivo.tierlevel` 不存在 ⇒ `IS_TIER_VOS=false`
       ⇒ MediaStyle 用 **vigour_\* 模板**（§20）。所以 DEVICE_* 才是本项目要复刻的观感，
       AOSP_* 只是同一份 A11 代码的另一支（别把两支混成一套，也别"顺手"挑看着顺眼的）。 */

    /**
     * 卡内封面是正方形，**边长 = notification_media_content 的测量高度**
     * （framework MediaNotificationView.onMeasure，LOG §2 第 4 步）。不是 52dp 也不是 36dp。
     */
    public static int artSidePx(int mediaContentHeightPx) {
        return Math.max(0, mediaContentHeightPx);
    }

    /**
     * 文字列要给封面让出来的右边距（§2 第 5 步）：
     * marginEnd = size3 + imageEndMargin + notification_content_margin_end。
     * imageEndMargin 来自 right_icon 的 marginEnd，两份模板都没声明 ⇒ 0，
     * 而 `MediaNotificationView` 构造函数读的是 framework 的 `notification_content_margin_end` = 16dp
     * （不随模板变），所以这里就是 size3 + 16dp。
     */
    public static int mainColumnMarginEndPx(int artSidePx, int contentMarginEndPx) {
        return Math.max(0, artSidePx + contentMarginEndPx);
    }

    /**
     * 封面放不下时的处理：**只有 IS_TIER_VOS** 才把封面整体平移进边距（§2 onLayout）。
     * 设备上 IS_TIER_VOS=false ⇒ 这条不生效，返回值只用来打日志，不写进布局。
     */
    public static int artPushInPx(int availablePx, int artSidePx) {
        if (availablePx >= artSidePx) {
            return 0;
        }
        return Math.max(0, artSidePx - Math.max(0, availablePx));
    }

    /** 卡内封面**没有圆角**：两份模板的 right_icon 都没有裁剪/圆角属性（圆角是 A12 才加的）。 */
    public static final float ART_RADIUS_DP = 0f;

    /**
     * 封面 scaleType：两份模板的 `right_icon` 都是 `scaleType=6`，
     * 而 framework-res 的 `attr/scaleType` 枚举里 **6=centerCrop**（§15-4；
     * 别按 ImageView.ScaleType 的序号猜，序号 6 也是 centerCrop，但 CENTER_INSIDE 是 7）。
     */
    public static final String ART_SCALE_TYPE = "centerCrop";
    public static final boolean ART_ADJUST_VIEW_BOUNDS = true;

    /** 设备（vigour）动作按钮：36×40dp，padding 上下 8dp / 左右 6dp，margin 左右各 2dp，centerInside，无背景。 */
    public static final int DEVICE_BUTTON_W_DP = 36;
    public static final int DEVICE_BUTTON_H_DP = 40;
    public static final int DEVICE_BUTTON_PADDING_TB_DP = 8;
    public static final int DEVICE_BUTTON_PADDING_LR_DP = 6;
    public static final int DEVICE_BUTTON_MARGIN_DP = 2;
    /** vigour 那颗把 background 写成 `@android:color/transparent`（0x0106000d）⇒ 原生没有水波纹层。 */
    public static final boolean DEVICE_BUTTON_HAS_RIPPLE = false;

    /** AOSP 分支的动作按钮：48dp 方形、四边 8dp、marginEnd 2dp、borderless 背景（有 ripple）。 */
    public static final int AOSP_BUTTON_DP = 48;
    public static final int AOSP_BUTTON_PADDING_DP = 8;
    public static final int AOSP_BUTTON_MARGIN_END_DP = 2;

    /** 动作带容器：设备 marginStart 14dp / marginEnd 20dp、gravity TOP、layoutDirection 强制 LTR。 */
    public static final int DEVICE_ACTIONS_MARGIN_START_DP = 14;
    public static final int DEVICE_ACTIONS_MARGIN_END_DP = 20;
    /** AOSP 走 `@style/NotificationMediaActionContainer`：paddingStart 8dp、paddingBottom 12dp、marginTop -24dp。 */
    public static final int AOSP_ACTIONS_PADDING_START_DP = 8;
    public static final int AOSP_ACTIONS_PADDING_BOTTOM_DP = 12;
    public static final int AOSP_ACTIONS_MARGIN_TOP_DP = -24;

    /** 文字列（notification_main_column）：设备 top 42dp / 左右 20dp；AOSP top 46dp / 左右 16dp / bottom 20dp。 */
    public static final int DEVICE_COLUMN_MARGIN_TOP_DP = 42;
    public static final int DEVICE_COLUMN_MARGIN_SIDE_DP = 20;
    public static final int AOSP_COLUMN_MARGIN_TOP_DP = 46;
    public static final int AOSP_COLUMN_MARGIN_SIDE_DP = 16;
    public static final int AOSP_COLUMN_MARGIN_BOTTOM_DP = 20;

    /**
     * 列最小高由 `BuilderExt.setContentMinHeight` 在绑定时写进 `notification_main_column`：
     * 设备走 `vigour_notification_min_content_height` = **61dp**，AOSP 走 39dp（§15-10/§20-1）。
     */
    public static final int MIN_CONTENT_HEIGHT_DP = 61;
    public static final int AOSP_MIN_CONTENT_HEIGHT_DP = 39;

    /** 内容块下内缩（AOSP 模板的 notification_content_margin = 20dp；设备模板没声明这一项）。 */
    public static final int CONTENT_MARGIN_DP = 20;

    /** `notification_content_margin_end`（0x010501c9）= **16dp**，是封面与文字之间的固定间隙。 */
    public static final int CONTENT_MARGIN_END_DP = 16;

    /** A11 有 media_actions 且系统允许紧凑进度条时，行最小高 = notification_min_height_media = 165dp。 */
    public static final int ROW_MIN_HEIGHT_MEDIA_DP = 165;

    /* ------------------------------------------------ 收起态（contracted）模板

       11-05 逆清：A11 的两态是**两份不同布局**，不是同一份布局改高度——
       `MediaStyle.makeMediaContentView` 在 `!IS_TIER_VOS` 时用 `0x03030061`
       = `res/layout/vigour_notification_template_material_media_q.xml`（收起），
       `makeMediaBigContentView` 用 `0x03030054` = `..._big_media_q.xml`（展开），
       证据 `work/y7s-a11/A11-Notification-MediaStyle.java:5000-5005/5053-5058`。

       两份的**动作带位置不一样**（这就是收起态要改的那一条）：
       - 展开 big_media_q：`notification_media_content`(竖向) 里 main_column **竖向**
         (orientation=1) 排标题/副文，`media_actions` 是它的**兄弟**、在其**下方**，
         marginStart 14dp / marginEnd 20dp（模块现有的展开排版就是这一份，已验收，别动）。
       - 收起 media_q：main_column **横向**(orientation=0)、minHeight=@0x030c0253、
         marginTop=@0x030c0246(42dp)、marginStart=@0x030c0245(20dp)、**无 marginEnd 声明**；
         里面 [main_column_container：竖向、layout_weight=1、layout_gravity=0x70=FILL_VERTICAL、
         paddingBottom=@0x030c0242(14dp)] + [media_actions：wrap、layout_gravity=0x00800035=top|end、
         **左右外边距一个都没写**] ⇒ 动作带在**文字右侧**，不在文字下方。 */

    /** 收起态文字列的下内缩 = `vigour_notification_content_margin_bottom`(0x030c0242) = 14dp。 */
    public static final int DEVICE_TEXT_COLUMN_PADDING_BOTTOM_DP = 14;

    /** 收起态动作带的外边距是 0（上面那份 xml 里没写；14/20 只属于展开那份）。 */
    public static final int DEVICE_COMPACT_ACTIONS_MARGIN_DP = 0;

    /**
     * 收起态给封面让出来的右边距有两套口径，都记下来，别混：
     * <ul>
     *   <li>模板层：`MediaStyle:5031` 在 hasLargeIcon 时把 main_column 的 marginEnd 写成
     *       `notification_media_image_margin_end` = **72dp**（`fw_res.txt:103221`）；</li>
     *   <li>运行时：`MediaNotificationView` 用 `artSide + notification_content_margin_end(16dp)`
     *       覆盖它（`A11-MediaNotificationView.java:61-73`，即 {@link #mainColumnMarginEndPx}）。</li>
     * </ul>
     * 收起态的根是**裸 FrameLayout**（不是 MediaNotificationView），所以原生那一支只有 72dp 生效，
     * 文字/按钮可以在水平方向压进封面里 72-artSide 那一段。我们走运行时口径（封面边长 + 16dp）：
     * 同一个 A11 公式、且不会让文字压在封面上。差异打进见证 `mainEndPx=` 供复核。
     */
    public static final int DEVICE_MEDIA_IMAGE_MARGIN_END_DP = 72;

    /* ------------------------------------------------------------ 头部行（§20-1） */

    /** 设备头部行：高 44dp，padding 上 16 / 下 12 / 左右 20，app 小图标 16dp（AOSP 是 50dp 高、图标 18dp）。 */
    public static final int HEADER_HEIGHT_DP = 44;
    public static final int HEADER_PADDING_TOP_DP = 16;
    public static final int HEADER_PADDING_BOTTOM_DP = 12;
    public static final int HEADER_PADDING_SIDE_DP = 20;
    public static final int HEADER_ICON_DP = 16;
    public static final float HEADER_TEXT_SP = 12f;
    public static final int AOSP_HEADER_HEIGHT_DP = 50;
    public static final int AOSP_HEADER_ICON_DP = 18;

    /* ---------------------------------------------------------- 排版（§15-5 / §20-1） */

    /**
     * 标题 14sp：`TextAppearance.Vigour.Material.Notification.Title` → `vigour_notification_title_text_size`，
     * 且布局在 text1 上又叠了一个 `textStyle=BOLD(1)`；父样式（AOSP 0x01030203）带
     * `fontFamily=config_headlineFontFamilyMedium` ⇒ **medium 族 + BOLD 合成**。
     */
    public static final float TITLE_SP = 14f;
    public static final boolean TITLE_BOLD = true;
    /** 副文（艺人）：设备 12**sp**（`vigour_notification_text_size`），AOSP 是 14**dp**（不跟 fontScale）。 */
    public static final float ARTIST_SP = 12f;
    public static final float AOSP_ARTIST_DP = 14f;
    /** 副文的上外边距 = `vigour_notification_text_margin_top` = 0.5dp。 */
    public static final float ARTIST_MARGIN_TOP_DP = 0.5f;

    /* -------------------------------------------------- 进度条与时间（§15-6 / §15-7 / §20-2） */

    /**
     * 进度条本体：ViewStub 的 `android:layout` 在设备上是 vigour 那份，
     * 但 SystemUI `resolveViews()` 在 inflate 前 `setLayoutResource(R.layout.notification_material_media_seekbar)`
     * **覆盖**了它（§20-2）⇒ 生效的是 AOSP 数值。
     */
    public static final int SEEK_TRACK_DP = 3;              // android:maxHeight
    public static final int SEEK_PADDING_TB_DP = 24;        // 撑出 48dp 触控带
    public static final int SEEK_MARGIN_TOP_DP = -12;
    public static final int SEEK_MARGIN_BOTTOM_DP = -24;
    public static final int SEEK_TIME_MARGIN_SIDE_DP = 16;  // elapsed 贴左 / total 贴右
    public static final int SEEK_TIME_ROW_MARGIN_BOTTOM_DP = 11;
    /** 时间字号（通知卡那一版布局）：`notification_text_size` = **14dp**（单位 dp，不跟 fontScale）。 */
    public static final float SEEK_TIME_DP = 14f;

    /* ------------------------------------------- 用户指定的"播放器"那一版进度条（11-04 早上定） */
    /**
     * **本产品按 A11 QS 播放器（SystemUI `media_view.xml:23-40 + 116-128`）画进度条**：
     * 两枚时间在条**左右两端同一行**（elapsed gravity=START + alignParentStart、
     * total gravity=END + alignParentEnd，条 `layout_width=0dp` 吃剩余宽），
     * 条 `paddingTop/Bottom=16dp`、`maxHeight=qs_media_enabled_seekbar_height=3dp`、
     * `splitTrack=false`、**thumb 常驻可见**。
     * 通知卡那一版（条满宽、时间在条下方左右 16dp、不可拖时 thumb 透明）的常数仍保留在上面，
     * 但不再喂给卡片：用户明确说"进度条要的是播放器那个样式"。
     */
    public static final float PLAYER_SEEK_TIME_SP = 14f;      // 媒体播放器这里是 14.0**sp**
    public static final int PLAYER_SEEK_PADDING_TB_DP = 16;
    /**
     * QS 播放器的 tint：progressTint=@color/media_seekbar_progress **#c0ffffff**、
     * progressBackgroundTint=@color/media_disabled **#80ffffff**、thumbTint=@color/media_primary_text。
     * 它敢用白是因为 QS 底恒定深；我们的卡片底色来自封面（可能是浅色 d8d8c8 那种），
     * 所以**保留同一对 alpha(192/128)，配在取色链算出的主文字色上** —— 观感与 A11 一致且不会看不清。
     */
    public static final int PLAYER_SEEK_PROGRESS_ALPHA = 192;
    public static final int PLAYER_SEEK_TRACK_ALPHA = 128;
    /** total 那侧的阴影：`setShadowLayer(1.5f, 1.5f, 1.5f, 卡片底色)`。 */
    public static final float SEEK_TIME_SHADOW_RADIUS = 1.5f;
    public static final float SEEK_TIME_SHADOW_DX = 1.5f;
    public static final float SEEK_TIME_SHADOW_DY = 1.5f;

    /**
     * 封面换图时的过渡时长。A11 自己就是硬切（`setImageDrawable` 一句），用户 11-04 明确要淡入淡出，
     * 所以这一档是**产品取向**而不是逆向值。取 320ms 与同一块卡背景那次
     * `TransitionDrawable.startTransition(320)` 对齐：底色与封面一起淡，才不会看着像两件事。
     */
    public static final int ART_FADE_MS = 320;

    /** `updateSeekBarTint` 的三档 alpha：thumb 255、progress 192、track 128，基色 = originalIconColor。 */
    public static final int SEEK_THUMB_ALPHA = 255;
    public static final int SEEK_PROGRESS_ALPHA = 192;
    public static final int SEEK_TRACK_ALPHA = 128;

    /** `PlaybackState.ACTION_SEEK_TO`：不可 seek 时 A11 只把 thumb 调透明并 setEnabled(false)，条与时间照常显示。 */
    public static final int ACTION_SEEK_TO = 256;

    /** 时间文本 = `DateUtils.formatElapsedTime(ms / 1000)`；进度标度用**毫秒**（setMax((int) durationMs)）。 */
    public static final long PROGRESS_UPDATE_INTERVAL_MS = 1000L;

    /** 把颜色换成另一个 alpha（`ColorStateList.withAlpha(...)` 的等价单色版）。 */
    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** 进度条三件套的颜色（§15-7：同一个 originalIconColor，只换 alpha）。 */
    public static int seekThumbColor(int iconColor) {
        return withAlpha(iconColor, SEEK_THUMB_ALPHA);
    }

    public static int seekProgressColor(int iconColor) {
        return withAlpha(iconColor, SEEK_PROGRESS_ALPHA);
    }

    public static int seekTrackColor(int iconColor) {
        return withAlpha(iconColor, SEEK_TRACK_ALPHA);
    }

    /* ---------------------------------------------------------------- 取色（§3 / §9） */

    /** palette 只取封面**左半幅**：setRegion(0, 0, w/2, h)（§3-1）。 */
    public static int[] backgroundRegion(int w, int h) {
        return new int[]{0, 0, Math.max(0, w / 2), Math.max(0, h)};
    }

    /** colorized 时前景改从**右 60%** 里选：setRegion(w*0.4, 0, w, h)（§3-4）。 */
    public static int[] foregroundRegion(int w, int h) {
        return new int[]{(int) (w * 0.4f), 0, Math.max(0, w), Math.max(0, h)};
    }

    /** 前景色相过滤：与背景色色相差要 >10° 且 <350°（§3-4，A11 用的就是这个区间）。 */
    public static boolean foregroundHueOk(float bgHue, float candidateHue) {
        float d = Math.abs(bgHue - candidateHue);
        return d > 10.0f && d < 350.0f;
    }

    /** palette 兜底：dominant==null 时 A11 用 Swatch(0xFFFFFFFF, 100)（§3-2）。 */
    public static final int FALLBACK_SWATCH_RGB = 0xFFFFFFFF;
    public static final int FALLBACK_SWATCH_POPULATION = 100;

    /** 黑白主导时改用彩色 swatch 的比例门限（§3-2）。 */
    public static final float POPULATION_FRACTION_FOR_WHITE_OR_BLACK = 2.5f;

    /**
     * 正文两色的真实来源（A11 无显式前景色时走 ensureColors 的第一条，LOG §9-6）：
     * vivo-res 实读值 —— 主文完全不透明，副文才带透明度。
     */
    public static final int PRIMARY_ON_LIGHT = 0xFF000000;   // 0x03080169 vivo_notification_title_text_color
    public static final int PRIMARY_ON_DARK = 0xFFFFFFFF;   // 0x0308016a …title_text_color_light
    public static final int SECONDARY_ON_LIGHT = 0xFF4D4D4D; // 0x03080162 vivo_notification_content_text_color
    public static final int SECONDARY_ON_DARK = 0xB3FFFFFF;  // 0x03080163 …content_text_color_light

    /** 未着色时的卡片底色 = @color/notification_material_background_color（亮 #ffffffff / 夜 @color/black）。 */
    public static final int CARD_BG_LIGHT = 0xFFFFFFFF;
    public static final int CARD_BG_DARK = 0xFF000000;

    /**
     * 卡片底色（§12-1）：着色通知用左半 palette 的 background swatch，否则用系统默认底色。
     * A11 的媒体卡**不是**恒深色卡 —— 把封面模糊铺满卡片是锁屏 backdrop（§11），不是这张卡。
     */
    public static int cardBackground(boolean colorized, int paletteBg, boolean night) {
        if (!colorized) {
            return night ? CARD_BG_DARK : CARD_BG_LIGHT;
        }
        return paletteBg;
    }

    /* luminance/contrast 一律转发 A11Palette，保持单一真相：
       A11 的实现是 CIE 分段（EPSILON=0.008856、KAPPA=903.3）走 colorToXYZ 再取 Y/100，
       不是 WCAG 文档里那个 0.03928 版本 —— 自己重写一份就会分叉。
       A11Palette 在桌面 JVM 上可加载（自检探针已验证），所以转发不影响离线验证。 */

    public static double luminance(int color) {
        return A11Palette.luminance(color);
    }

    public static double contrast(int foreground, int background) {
        return A11Palette.contrast(foreground, background);
    }

    /** shouldUseDark：无底色时看默认背景明暗，有底色时用相对亮度 &gt; 0.5（§9-5）。 */
    public static boolean shouldUseDark(int backgroundColor, boolean defaultBackgroundIsDark) {
        if (backgroundColor == 0) {
            return !defaultBackgroundIsDark;
        }
        // 不按"亮度 > 0.5"翻族（11-05 真机反例）：浅桃底 #ffd0a098 亮度略低于 0.5，
        // 于是选了白字，白字压浅桃只有 ~2.5:1 —— 用户看到"切歌按钮/时间看不清"。
        // 照 A11 的 ContrastColorUtil 精神：**比较两族的实际对比度，取更高的那族**，
        // 之后的 findAlphaToMeetContrast 再把 alpha 抬到 4.5:1（它只会加实、不会翻族）。
        return contrast(PRIMARY_ON_LIGHT, backgroundColor)
                >= contrast(PRIMARY_ON_DARK, backgroundColor);
    }

    /** 主文色（浅底用深字返回 #ff000000）。 */
    public static int primaryTextColor(int backgroundColor, boolean night) {
        return shouldUseDark(backgroundColor, night) ? PRIMARY_ON_LIGHT : PRIMARY_ON_DARK;
    }

    /** 副文色（浅底 #ff4d4d4d / 深底 #b3ffffff）。 */
    public static int secondaryTextColor(int backgroundColor, boolean night) {
        return shouldUseDark(backgroundColor, night) ? SECONDARY_ON_LIGHT : SECONDARY_ON_DARK;
    }

    /**
     * 着色且有底色时 A11 **只调 alpha**把文字拉到 4.5:1（§9-4 的 findAlphaToMeetContrast）。
     * 与 colorizedTextColors() 那条"改明度"的链是两支：先走 alpha 支，够不到 4.5 才由调用方兜底。
     */
    /**
     * 逐行照 A11 `ContrastColorUtil.findAlphaToMeetContrast`：
     * 先判"当前 RGB 是否已经够"，够就原样返回；不够就在 **[startAlpha, 255]** 上做
     * **15 次二分**（`high - low > 0` 时继续），判定用 **严格大于**，最后返回 `argb(high, r, g, b)`。
     *
     * 语义（对照 A11 原文核对过，别凭印象改）：
     * `calculateContrast` 会先把 **alpha&lt;255 的前景合成到背景上**再算亮度比
     * （A11 行 489-491 / A11Palette.contrast 同实现），所以 alpha 真的参与二分；
     * 二分的下界是**当前 alpha**、上界 255 ⇒ 这条链只会**把字加实**到刚好够 4.5:1，
     * 永远不会把文字调淡。中间那次误记成"亮度不看 alpha"已更正，见 LOG-1103 §13。
     */
    public static int findAlphaToMeetContrast(int color, int backgroundColor, double minRatio) {
        if (contrast(color, backgroundColor) >= minRatio) {
            return color;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int low = (color >>> 24) & 0xFF;
        int high = 255;
        for (int i = 0; i < 15 && high - low > 0; i++) {
            int alpha = (low + high) / 2;
            int fg = (alpha << 24) | (r << 16) | (g << 8) | b;
            if (contrast(fg, backgroundColor) > minRatio) {
                high = alpha;
            } else {
                low = alpha;
            }
        }
        return (high << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 媒体按钮的着色（§5b + §9-2）：A11 在 {@code config_tintNotificationActionButtons=true}
     * 且非 VOS 层时**一定**有着色动作按钮，于是色值 = 正文主色（着色时）或对比度链颜色（未着色时）。
     * 也就是说原生不存在"按钮压暗到看不清"这条路。
     */
    public static int actionIconTint(boolean colorized, int primaryTextColor, int contrastColor) {
        return colorized ? primaryTextColor : contrastColor;
    }

    /**
     * 水波纹色 = 主题 {@code ?attr/colorControlHighlight} 的 alpha 配上按钮色的 RGB（§9-3）。
     * 参数分开传，避免这里去读宿主主题（宿主值会随版本漂移，LOG §9 已吃过类比亏）。
     */
    public static int rippleColor(int tintColor, int highlightAlpha) {
        return (Math.max(0, Math.min(0xFF, highlightAlpha)) << 24)
                | (tintColor & 0x00FFFFFF);
    }

    /**
     * 封面的模糊+tint 后处理（§4b 的**缩放/模糊/蒙色**三步常数，模糊内核是本项目自研的替代，
     * 因为 A15 目标进程没有可用的 RenderScript）。铺的位置是 backdrop，不是卡：见 §11。
     */
    public static int backdropScalePx(int displayWidthPx, int displayHeightPx) {
        // 字节码：div-int/lit8 6 各一次，然后 Math.max —— 整数除法
        return Math.max(displayWidthPx / 6, displayHeightPx / 6);
    }

    public static final float BACKDROP_BLUR_RADIUS = 25.0f;      // 0x41C80000
    public static final int BACKDROP_TINT_ALPHA = 178;          // 0xB2：swatch 原色铺满时只用 70% 不透明

    /* ------------------------------------------------------------ §4a 封面浮雕渐变的数值表 */

    /** Rec.709 亮度权重（字节码 0x3e59b3d0 / 0x3f371759 / 0x3d93dd98）。 */
    public static final float LUMA_R = 0.2126f;
    public static final float LUMA_G = 0.7152f;
    public static final float LUMA_B = 0.0722f;

    /** 染色层 alpha：`const/16 v8, #int 127`（jadx 把它显示成 WorkQueueKt.MASK，别信符号名）。 */
    public static final int RELIEF_LAYER_ALPHA = 127;

    /** 渐变端点：全透明、50% 不透明的白、纯黑（`Color.argb(0.5f,1,1,1)` = 0x80FFFFFF）。 */
    public static final int GRAD_TRANSPARENT = 0x00000000;
    public static final int GRAD_HALF_WHITE = 0x80FFFFFF;
    public static final int GRAD_BLACK = 0xFF000000;

    /** 两段 DST_IN 的 stops（array-data 0x014c / 0x0156 解出来的真值）。 */
    public static final float[] GRAD_STOPS_FIRST = {0.0f, 0.4f, 1.0f};
    public static final float[] GRAD_STOPS_SECOND = {0.0f, 0.6f, 1.0f};

    /**
     * A11 的"浮雕"颜色矩阵：每行都是 Rec.709 权重，平移项 {@code channel - luma(color)}。
     * 语义 = 把图片变成"以取色为均值的明暗浮雕"。返回 20 个 float，行主序（第 5 列是平移项）。
     */
    public static float[] reliefMatrix(int color) {
        float r = (color >> 16) & 0xFF;
        float g = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        // 字节码：先 /255 再乘权重求和，最后再 *255
        float luma = ((r / 255.0f) * LUMA_R + (g / 255.0f) * LUMA_G + (b / 255.0f) * LUMA_B) * 255.0f;
        return new float[]{
                LUMA_R, LUMA_G, LUMA_B, 0f, r - luma,
                LUMA_R, LUMA_G, LUMA_B, 0f, g - luma,
                LUMA_R, LUMA_G, LUMA_B, 0f, b - luma,
                0f, 0f, 0f, 1f, 0f,
        };
    }

    /** 居中裁成正方形（§4a-1）：返回 {dx, dy, side}，side=min(w,h)。 */
    public static int[] squareCrop(int w, int h) {
        int side = Math.min(w, h);
        return new int[]{(w - side) / 2, (h - side) / 2, side};
    }
}
