package com.betteroriginos.feature.media;

/**
 * Android 11（OriginOS Ocean）**QS 播放器**（SystemUI 的 media_view.xml / @style/MediaPlayer.Button）
 * 的规格常量。
 *
 * ⚠️ 这批值**不要**用在通知栏媒体卡上：那张卡是 framework 的 MediaStyle 模板
 * （notification_template_material_big_media / vigour_*_q），尺寸、字号、进度条颜色全都不一样，
 * 一律走 {@link A11NotifSpec}。把这里的常数搬进通知卡是本项目 11-02 那轮"看着像但不是复刻"的
 * 根因，逐条证据见 out/LOG-1103-A11-MEDIACARD-REVERSE.md §10/§15/§20。
 * 现在通知卡路径上对本类的引用已经清零；保留本类只因为 QS 那一侧的取证核对脚本
 * （work/y7s_a11_evidence_check.py）还要拿这些值对表。
 *
 * 每条都注明 A11 侧的来源，避免"看着像"式实现（证据：out/LOG-1102-A11-MEDIACARD-RE.md §2/§4，
 * 反编译产物在 work/y7s-a11/tmp/；Y7s framework-res 实读值在 work/y7s-a11/fw_res.txt）。
 */
public final class A11MediaSpec {

    private A11MediaSpec() {
    }

    /** 展开态 album_art = R.dimen.qs_media_album_size（media_view.xml:76-77） */
    public static final int ART_EXPANDED_DP = 52;
    /** 收起态 @android:id/right_icon = R.dimen.notification_right_icon_size（fw_res 0x010501eb） */
    public static final int ART_COLLAPSED_DP = 36;
    /** 收起态 right_icon 的 scaleType 是 CENTER（xml_fw_template_media.txt:11-13），不裁剪圆角 */
    public static final boolean ART_COLLAPSED_CLIP = false;
    /**
     * 封面圆角 2dp：A11 侧是 Theme.DeviceDefault 的 dialogCornerRadius
     * → config_dialogCornerRadius(0x010500a6)=2.000000dp（Y7s framework-res 实读）。
     * 固定值，不跟宿主主题走，理由见 MediaCardFeature.a11ArtRadiusPx。
     */
    public static final float ART_RADIUS_DP = 2f;

    /** action0..4 = @style/MediaPlayer.Button：48/48/52/48/48dp，中间是播放键（xml_media_view.txt:49-73） */
    public static final int BUTTON_DP = 48;
    public static final int BUTTON_CENTER_DP = 52;

    /** 进度条：qs_media_enabled_seekbar_height = 3dp（禁用态 1dp）（xml_media_view.txt:116-128） */
    public static final int SEEK_TRACK_DP = 3;
    public static final int SEEK_DISABLED_TRACK_DP = 1;

    /** A11 media_view 的固定白色阶（背景恒深，见 A11Palette.computeCardBackground） */
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0x99FFFFFF;
    public static final int SEEK_PROGRESS = 0xC0FFFFFF;
    public static final int SEEK_TRACK = 0x80FFFFFF;
    public static final int SEEK_THUMB = 0xFFFFFFFF;

    /** 字号：标题 16sp、艺名 14sp、时间 14sp、应用图标 16dp（xml_media_view.txt:129-158） */
    public static final float TITLE_SP = 16f;
    public static final float ARTIST_SP = 14f;
    public static final float TIME_SP = 14f;
    public static final int SOURCE_ICON_DP = 16;

    /**
     * 收起态（MediaStyle 模板）的真实排版来自 framework 文本样式，不是展开卡的 16sp：
     * line1 的 textAppearance=@style/TextAppearance.Material.Notification.Title
     * → textSize=@dimen/notification_title_text_size = 14.0sp、fontFamily="sans-serif-medium"
     * （framework-res.apk 用 aapt2 dump resources 逐跳解析出 0x01030203 的属性）；
     * 副文=@dimen/notification_subtext_size = **12.0dp**（单位是 dp 不是 sp：dp 不跟 fontScale，
     * 所以套 SP 会在大字号机器上比 A11 大一档 —— 这里必须用 COMPLEX_UNIT_DIP）；
     * 动作带=notification_action_list_height = 60dp。
     */
    public static final float COLLAPSED_TITLE_SP = 14f;
    public static final float COLLAPSED_SUBTEXT_DP = 12f;
    public static final int ACTION_LIST_HEIGHT_DP = 60;

    /** IlluminationDrawable：高亮 = 背景亮度 ±0.15，过渡 370ms FAST_OUT_LINEAR_IN */
    public static final float ILLUM_DELTA = 0.15f;
    public static final long ILLUM_MS = 370L;
}
