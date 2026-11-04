#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A11 **通知栏**媒体卡的规格/数学验收（不连设备、不截图）。

被测的是模块编译产物里的真实字节码：`com.betteroriginos.feature.media.A11NotifSpec`
（反射调用，探针 work/tmp-re-a11/NotifProbe.java）。
参考实现独立抄自 A11 二进制反编译件，不是从模块代码推的：

  work/y7s-a11/A11-ContrastColorUtil.java:411-520   shouldUseDark / luminance /
                                                      calculateContrast / alpha 二分
  work/y7s-a11/A11-ContrastColorUtil.java:489-491   半透明前景先合成再算比值
  work/y7s-a11/A11-Notification-MediaStyle.java     bindMediaActionButton / getActionColor
  work/tmp-re-a11/A11-MediaArtworkProcessor.dump    backdrop 缩放(/6 整除)、模糊 25、蒙色 178
  work/tmp-re-a11/a11-code-classes2.txt(3280d0-)    浮雕矩阵、alpha 127、两段 stops 0.4/0.6
  work/y7s-a11/A11-media-layout.xmltree.txt         48dp 按钮 / 8dp 内缩 / 2dp marginEnd、
                                                    CENTER_INSIDE、无圆角
  work/y7s-a11/vivo_res.txt                         主/副文四个色资源实读值

跑法：
  python work/a11_notif_selftest.py            # 全绿
  python work/a11_notif_selftest.py --negative # 逐条挪常数，证明每条断言真的会红
前置：先跑过 module/build.sh
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CLASSES = os.path.join(ROOT, "module", "build", "classes")
XSTUBS = os.path.join(ROOT, "module", "build", "stubs")
PROBE_DIR = os.path.join(ROOT, "work", "tmp-re-a11")
AJAR = os.path.join(ROOT, "work", "sdk-dl", "plat35", "android-35", "android.jar")

# ---------------------------------------------------------------- 独立参考（A11 原文）

THRESH = 0.04045


def ch(c, sh):
    return (c >> sh) & 0xFF


def lin(v):
    v = v / 255.0
    return v / 12.92 if v < THRESH else ((v + 0.055) / 1.055) ** 2.4


def ref_luminance(c):
    return 0.2126 * lin(ch(c, 16)) + 0.7152 * lin(ch(c, 8)) + 0.0722 * lin(ch(c, 0))


def ref_composite(fg, bg):
    """A11 原文（A11-ContrastColorUtil.java:447-466），整数除法、不做四舍五入：
         a  = 255 - ((255-bgA)*(255-fgA))/255
         ch = ((fgC*255*fgA) + (bgC*bgA*(255-fgA))) / (a*255)
       这条必须逐字照抄：分组方式不同就差 1 个通道值，而 alpha 二分正好在临界点上判生死。"""
    af, ab = ch(fg, 24), ch(bg, 24)
    a = 255 - (((255 - ab) * (255 - af)) // 255)
    out = 0
    for sh in (16, 8, 0):
        cf, cb = ch(fg, sh), ch(bg, sh)
        c = 0 if a == 0 else (((cf * 255) * af) + (cb * ab * (255 - af))) // (a * 255)
        out |= max(0, min(255, c)) << sh
    return (a << 24) | out


def ref_contrast(fg, bg, perturb_dim=False):
    f = fg
    if ch(fg, 24) < 255:
        f = ref_composite(fg, bg)
    if perturb_dim:      # 负对照：把"合成"这步去掉 —— 半透明前景就永远算不出真实比值
        pass
    l1 = ref_luminance(f) + 0.05
    l2 = ref_luminance(bg) + 0.05
    return max(l1, l2) / min(l1, l2)


def ref_alpha_to_meet(color, bg, min_ratio, mode=None):
    """A11 原文（A11-ContrastColorUtil.java:227-249）：
       初始就够 ⇒ 原样返回；否则在 **[当前alpha, 255]** 上二分 **15 次**、
       判定用 **严格大于**、返回 **argb(high, ...)** —— high 是"够用"的那一侧。

       mode 是给负对照用的：
         "low"   返回 argb(low)（把不够用的那一侧当结果）⇒ 应当被抓
         "full"  下界写成 0（允许把文字调淡）⇒ 数学上与原版同界，**不可区分**，
                 所以不做成负对照（留在这里是为了说明：这条不是断言，别指望它守门）
    """
    if ref_contrast(color, bg) >= min_ratio:
        return color
    r, g, b = ch(color, 16), ch(color, 8), ch(color, 0)
    low = 0 if mode == "full" else ch(color, 24)
    high = 255
    for _ in range(15):
        if high - low <= 0:
            break
        alpha = (low + high) // 2
        c = (alpha << 24) | (r << 16) | (g << 8) | b
        if ref_contrast(c, bg) > min_ratio:
            high = alpha
        else:
            low = alpha
    pick = low if mode == "low" else high
    return (pick << 24) | (r << 16) | (g << 8) | b





def ref_should_use_dark(bg, night):
    if bg == 0:
        return not night
    return ref_luminance(bg) > 0.5


PRIM_L, PRIM_D = 0xFF000000, 0xFFFFFFFF
SEC_L, SEC_D = 0xFF4D4D4D, 0xB3FFFFFF


def ref_primary(bg, night, swap=False):
    dark = ref_should_use_dark(bg, night)
    if swap:
        return PRIM_D if dark else PRIM_L
    return PRIM_L if dark else PRIM_D


def ref_secondary(bg, night):
    return SEC_L if ref_should_use_dark(bg, night) else SEC_D


def ref_card_bg(colorized, palette_bg, night):
    if not colorized:
        return 0xFF000000 if night else 0xFFFFFFFF
    return palette_bg


def ref_with_alpha(color, a):
    """A11 的 ColorStateList.withAlpha(a)：换掉 alpha 通道，RGB 原样保留（§15-7）。"""
    return ((a & 0xFF) << 24) | (color & 0x00FFFFFF)


def ref_seek_colors(base, prog=192, track=128, thumb=255):
    """updateSeekBarTint 的三档：thumb 255 / progress 192 / track 128，基色同一个。"""
    return "%x,%x,%x" % (ref_with_alpha(base, thumb),
                         ref_with_alpha(base, prog),
                         ref_with_alpha(base, track))


def ref_relief(color):
    r, g, b = float(ch(color, 16)), float(ch(color, 8)), float(ch(color, 0))
    luma = ((r / 255.0) * 0.2126 + (g / 255.0) * 0.7152 + (b / 255.0) * 0.0722) * 255.0
    row = [0.2126, 0.7152, 0.0722, 0.0]
    return [row[0], row[1], row[2], 0.0, r - luma,
            row[0], row[1], row[2], 0.0, g - luma,
            row[0], row[1], row[2], 0.0, b - luma,
            0.0, 0.0, 0.0, 1.0, 0.0]


# ---------------------------------------------------------------- 探针调用

def wpath(p):
    out = subprocess.run(["cygpath", "-m", p], capture_output=True, text=True)
    return out.stdout.strip() or p


def run_probe(lines):
    cp = ";".join([wpath(PROBE_DIR), wpath(CLASSES), wpath(XSTUBS), wpath(AJAR)])
    out = subprocess.run(["java", "-cp", cp, "NotifProbe"],
                         input="\n".join(lines) + "\n",
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("探针没跑起来：" + (out.stderr or "")[:400])
    got = [l for l in out.stdout.splitlines()]
    if len(got) != len(lines):
        raise RuntimeError("探针回的行数(%d)和发出去的问题数(%d)不一致，比对不可信"
                           % (len(got), len(lines)))
    return got


def ensure_probe_compiled():
    cls = os.path.join(PROBE_DIR, "NotifProbe.class")
    src = os.path.join(PROBE_DIR, "NotifProbe.java")
    if os.path.exists(cls) and os.path.getmtime(cls) >= os.path.getmtime(src):
        return
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", PROBE_DIR, src],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError("探针编译失败：" + (r.stderr or "")[:500])


# ---------------------------------------------------------------- 用例

COLORS = [0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFD50000, 0xFF2A211C,
          0xFFF7DED4, 0xFF3B5B72, 0xFFB3FFFFFF, 0xFF1B3A2A]

CASES = []


def add(name, cmd, expect):
    CASES.append((name, cmd, expect))


def build_cases(neg):
    """neg 里每个键都是一次**单独**的参考侧篡改；正例传 {} 即可。"""
    del CASES[:]
    swap = neg.get("swap_primary", False)
    lower = neg.get("alpha_return_low", False)
    no_comp = neg.get("skip_composite", False)
    div = 5 if neg.get("backdrop_div5", False) else 6
    for c in COLORS:
        add("luminance %08X" % c, "luminance 0x%08X" % c,
            "%.6f" % ref_luminance(c))
        add("contrast black-on %08X" % c, "contrast 0xFF000000 0x%08X" % c,
            "%.6f" % ref_contrast(0xFF000000, c))
    # 半透明前景必须**先合成**：这是 A11 的关键一条（§13-1）
    # 半透明前景必须**先合成**（A11 行 489-491）；不合成就会得到另一个比值
    add("contrast 半透明前景 0x80FFFFFF on 0xFF3B5B72",
        "contrast 0x80FFFFFF 0xFF3B5B72",
        "%.6f" % (ref_contrast(0xFFFFFFFF, 0xFF3B5B72) if no_comp
                  else ref_contrast(0x80FFFFFF, 0xFF3B5B72)))
    for bg, night in ((0xFFFFFFFF, False), (0xFF000000, True), (0xFF3B5B72, False),
                      (0xFFD50000, True), (0x00000000, True), (0x00000000, False)):
        add("useDark %08X night=%d" % (bg, 1 if night else 0),
            "useDark 0x%08X %d" % (bg, 1 if night else 0),
            "1" if ref_should_use_dark(bg, night) else "0")
        add("primary %08X night=%d" % (bg, 1 if night else 0),
            "primary 0x%08X %d" % (bg, 1 if night else 0),
            "0x%08X" % ref_primary(bg, night, swap=swap))
        add("secondary %08X night=%d" % (bg, 1 if night else 0),
            "secondary 0x%08X %d" % (bg, 1 if night else 0),
            "0x%08X" % ref_secondary(bg, night))
        add("cardBg colorized=0 %08X night=%d" % (bg, 1 if night else 0),
            "cardBg 0 0x%08X %d" % (bg, 1 if night else 0),
            "0x%08X" % (0xFFFFFFFF if (night and neg.get("flip_cardbg", False))
                        else ref_card_bg(False, 0, night)))
        add("cardBg colorized=1 %08X night=%d" % (bg, 1 if night else 0),
            "cardBg 1 0x%08X %d" % (bg, 1 if night else 0),
            "0x%08X" % ref_card_bg(True, bg, night))
        add("alpha45 0x80FFFFFF on %08X" % bg,
            "alpha45 0x80FFFFFF 0x%08X" % bg,
            "0x%08X" % (0x80FFFFFF if no_comp
                        else ref_alpha_to_meet(0x80FFFFFF, bg, 4.5,
                                               mode=("low" if lower else None))))
    for fg, bg in ((0x80FFFFFF, 0xFF3B5B72), (0x80FFFFFF, 0xFFD50000),
                   (0x40FF0000, 0xFF2A211C), (0x60FFFFFF, 0xFF8A8A8A),
                   (0x30000000, 0xFFF7DED4)):
        add("alpha45 %08X on %08X" % (fg, bg),
            "alpha45 0x%08X 0x%08X" % (fg, bg),
            "0x%08X" % (0x80FFFFFF if no_comp else
                        ref_alpha_to_meet(fg, bg, 4.5,
                                          mode=("low" if lower else None))))

    # 按钮：A11 通知卡的动作按钮与正文主色同色（§5b/§15-8）
    add("tint colorized=1", "tint 1 0xFFFFFFFF 0xFF000000", "0xFFFFFFFF")
    add("tint colorized=0", "tint 0 0xFFFFFFFF 0xFF000000", "0xFF000000")
    add("ripple 白 on 60% alpha", "ripple 0xFFFFFFFF 153", "0x99FFFFFF")
    add("ripple 黑 on 0 alpha", "ripple 0xFF000000 0", "0x00000000")
    # 进度条三档（§15-7）：同一个基色，只换 alpha。轨道压到 128、已走压到 192 是原生数值，
    # 不是"深色底上的白阶"（那是 QS 播放器，§10）
    prog = 255 if neg.get("seek_prog_alpha") else 192
    track = 192 if neg.get("seek_track_alpha") else 128
    for base in (0xFFFFFFFF, 0xFF3B5B72, 0x80FFFFFF, 0xFF000000):
        add("seekColors %08X" % base, "seekColors %08X" % base,
            ref_seek_colors(base, prog=prog, track=track))
    # 浮雕矩阵（§4a）：以一个中调底色为例，逐系数比
    for c in (0xFF3B5B72, 0xFFFFFFFF, 0xFFD50000):
        add("relief %08X" % c, "relief 0x%08X" % c,
            " ".join("%.4f" % v for v in ref_relief(c)))
    add("crop 200x120", "crop 200 120", "40 0 120")
    add("crop 120x200", "crop 120 200", "0 40 120")
    add("bgRegion 200x120", "bgRegion 200 120", "0 0 100 120")
    add("fgRegion 200x120", "fgRegion 200 120", "80 0 200 120")
    # backdrop 缩放是 **整数除法 /6** 再取 max（字节码 div-int/lit8）
    add("bdScale 1080x2184", "bdScale 1080 2184", str(max(1080 // div, 2184 // div)))
    add("bdScale 720x1480", "bdScale 720 1480", str(max(720 // div, 1480 // div)))
    add("push 不够放", "push 90 120", "30")
    add("push 放得下", "push 130 120", "0")
    add("mge", "mge 120 48", "168")
    add("artSide", "artSide 150", "150")
    add("artSide 负数兜底", "artSide -20", "0")


CONST_EXPECT = {
    "ART_RADIUS_DP": "0.0",            # 通知卡封面没有圆角（§12-2）
    # 设备分支（IS_TIER_VOS=false ⇒ vigour 模板，§20）：按钮 36×40、padding 8/6、margin 2/2
    "DEVICE_BUTTON_W_DP": "36",
    "DEVICE_BUTTON_H_DP": "40",
    "DEVICE_BUTTON_PADDING_TB_DP": "8",
    "DEVICE_BUTTON_PADDING_LR_DP": "6",
    "DEVICE_BUTTON_MARGIN_DP": "2",
    "DEVICE_BUTTON_HAS_RIPPLE": "false",   # 背景 = @android:color/transparent
    # AOSP 那一支（IS_TIER_VOS=true）留作对照，别混用
    "AOSP_BUTTON_DP": "48",
    "AOSP_BUTTON_PADDING_DP": "8",
    "AOSP_BUTTON_MARGIN_END_DP": "2",
    "DEVICE_ACTIONS_MARGIN_START_DP": "14",
    "DEVICE_ACTIONS_MARGIN_END_DP": "20",
    "DEVICE_COLUMN_MARGIN_TOP_DP": "42",
    "DEVICE_COLUMN_MARGIN_SIDE_DP": "20",
    "AOSP_COLUMN_MARGIN_TOP_DP": "46",
    "AOSP_COLUMN_MARGIN_SIDE_DP": "16",
    "MIN_CONTENT_HEIGHT_DP": "61",      # vigour_notification_min_content_height
    "AOSP_MIN_CONTENT_HEIGHT_DP": "39",
    "HEADER_HEIGHT_DP": "44",
    "HEADER_ICON_DP": "16",             # 设备头部的小图标；AOSP 才是 18dp
    "AOSP_HEADER_ICON_DP": "18",
    # 进度条（§15-6/§15-7；SystemUI 强制 inflate AOSP 那份 seekbar 布局，§20-2）
    "SEEK_TRACK_DP": "3",
    "SEEK_PADDING_TB_DP": "24",
    "SEEK_MARGIN_TOP_DP": "-12",
    "SEEK_MARGIN_BOTTOM_DP": "-24",
    "SEEK_TIME_MARGIN_SIDE_DP": "16",
    "SEEK_TIME_ROW_MARGIN_BOTTOM_DP": "11",
    "SEEK_THUMB_ALPHA": "255",
    "SEEK_PROGRESS_ALPHA": "192",
    "SEEK_TRACK_ALPHA": "128",
    # 播放器那一版（11-04 早定：用户要的是 A11 QS 播放器 media_view.xml 那条进度行）
    "PLAYER_SEEK_TIME_SP": "14.0",       # 单位 sp：大字号机器上必须跟 fontScale
    "PLAYER_SEEK_PADDING_TB_DP": "16",   # 不是通知卡那版的 24
    "PLAYER_SEEK_PROGRESS_ALPHA": "192",
    "PLAYER_SEEK_TRACK_ALPHA": "128",
    "SEEK_TIME_SHADOW_RADIUS": "1.5",
    "SEEK_TIME_SHADOW_DX": "1.5",
    "SEEK_TIME_SHADOW_DY": "1.5",
    # 封面换图过渡（产品取向：A11 原生硬切，用户 11-04 要淡入淡出）
    "ART_FADE_MS": "320",
    "ACTION_SEEK_TO": "256",
    "PROGRESS_UPDATE_INTERVAL_MS": "1000",
    # 排版（§15-5/§20-1）
    "TITLE_SP": "14.0",
    "ARTIST_SP": "12.0",                # 设备：vigour_notification_text_size = 12sp
    "AOSP_ARTIST_DP": "14.0",           # AOSP：notification_text_size = 14dp
    "SEEK_TIME_DP": "14.0",             # 时间随 AOSP seekbar 布局 = 14dp（单位 dp）
    "HEADER_TEXT_SP": "12.0",
    "ART_SCALE_TYPE": "centerCrop",     # attr/scaleType 枚举 6 = centerCrop（§15-4）
    "RELIEF_LAYER_ALPHA": "127",       # 字节码 const/16 v8, #int 127
    "GRAD_HALF_WHITE": str(0x80FFFFFF - (1 << 32)),
    "GRAD_BLACK": str(0xFF000000 - (1 << 32)),
    "BACKDROP_BLUR_RADIUS": "25.0",    # 0x41C80000
    "BACKDROP_TINT_ALPHA": "178",      # 0xB2
    "PRIMARY_ON_LIGHT": str(PRIM_L - (1 << 32)),
    "PRIMARY_ON_DARK": str(PRIM_D - (1 << 32)),
    "SECONDARY_ON_LIGHT": str(SEC_L - (1 << 32)),
    "SECONDARY_ON_DARK": str(SEC_D - (1 << 32)),
    "FALLBACK_SWATCH_RGB": str(0xFFFFFFFF - (1 << 32)),
    "FALLBACK_SWATCH_POPULATION": "100",
    "POPULATION_FRACTION_FOR_WHITE_OR_BLACK": "2.5",
    "ROW_MIN_HEIGHT_MEDIA_DP": "165",
    "CONTENT_MARGIN_DP": "20",
    "LUMA_R": "0.2126", "LUMA_G": "0.7152", "LUMA_B": "0.0722",
    "STOPS1": "0.0,0.4,1.0,",          # array-data 0x014c
    "STOPS2": "0.0,0.6,1.0,",          # array-data 0x0156
}


def check_once(perturb):
    """跑一轮：返回 (报红的条数, 前几条红)。perturb 为空字典时是正例。"""
    build_cases(perturb)
    cmds = [c[1] for c in CASES] + ["consts"]
    got = run_probe(cmds)
    fails = []
    for (name, cmd, expect), actual in zip(CASES, got):
        if actual.strip() != expect.strip():
            fails.append("期望 %s 实得 %s（%s）"
                         % (expect.strip(), actual.strip(), name))
    consts = {}
    for tok in got[-1].split():
        k, _, v = tok.partition("=")
        consts[k] = v
    expect_consts = dict(CONST_EXPECT)
    if perturb.get("relief_alpha"):
        expect_consts["RELIEF_LAYER_ALPHA"] = "128"
    if perturb.get("grad_stops"):
        expect_consts["STOPS2"] = "0.0,0.4,1.0,"
    if perturb.get("button_pad"):
        expect_consts["DEVICE_BUTTON_PADDING_TB_DP"] = "7"
    if perturb.get("button_size"):
        expect_consts["DEVICE_BUTTON_W_DP"] = "48"    # 串成 AOSP 那一支就该被抓
    if perturb.get("min_content"):
        expect_consts["MIN_CONTENT_HEIGHT_DP"] = "39"
    if perturb.get("seek_time_dp"):
        expect_consts["SEEK_TIME_DP"] = "12.0"
    if perturb.get("player_seek_pad"):
        expect_consts["PLAYER_SEEK_PADDING_TB_DP"] = "24"
    if perturb.get("player_time_unit"):
        expect_consts["PLAYER_SEEK_TIME_SP"] = "12.0"
    if perturb.get("art_fade_ms"):
        expect_consts["ART_FADE_MS"] = "0"
    if perturb.get("col_top"):
        expect_consts["DEVICE_COLUMN_MARGIN_TOP_DP"] = "46"
    if perturb.get("art_scale"):
        expect_consts["ART_SCALE_TYPE"] = "centerInside"
    for k, v in expect_consts.items():
        if consts.get(k) != v:
            fails.append("常数 %s 期望 %s 实得 %s" % (k, v, consts.get(k)))
    both = run_probe(["contrast 0xFF000000 0xFF3B5B72",
                      "paletteContrast 0xFF000000 0xFF3B5B72",
                      "contrast 0x80FFFFFF 0xFF3B5B72",
                      "paletteContrast 0x80FFFFFF 0xFF3B5B72",
                      "luminance 0xFFD50000",
                      "paletteLuminance 0xFFD50000"])
    for i in range(0, len(both), 2):
        if both[i].strip() != both[i + 1].strip():
            fails.append("转发一致性：A11NotifSpec=%s vs A11Palette=%s（第 %d 组）"
                         % (both[i].strip(), both[i + 1].strip(), i // 2 + 1))
    return fails


def main():
    negative = "--negative" in sys.argv
    if not os.path.isdir(CLASSES):
        print("需要先跑 module/build.sh（找不到 %s）" % CLASSES)
        return 2
    ensure_probe_compiled()

    if not negative:
        fails = check_once({})
        for f in fails:
            print("  FAIL", f)
        print("A11 通知卡规格自检：%d 项比对 / %d 失败" % (len(CASES), len(fails)))
        return 0 if not fails else 1

    # 每个扰动只改**一处**参考侧定义；哪个改动抓不到红，就说明那条断言是死的
    mutations = ["swap_primary", "alpha_return_low", "skip_composite",
                 "backdrop_div5", "flip_cardbg", "relief_alpha",
                 "grad_stops", "button_pad", "button_size", "min_content",
                 "seek_prog_alpha", "seek_track_alpha", "seek_time_dp",
                 "player_seek_pad", "player_time_unit", "art_fade_ms",
                 "col_top", "art_scale"]
    bad = 0
    for m in mutations:
        fails = check_once({m: True})
        if fails:
            print("  ok  负对照 %-16s 正确报红 %d 条，首条：%s"
                  % (m, len(fails), fails[0].splitlines()[0]))
        else:
            bad += 1
            print("  FAIL 负对照 %s 没报红 —— 对应的断言是空的" % m)
    if bad:
        print("SELFTEST FAIL: %d 个负对照失效" % bad)
        return 1
    print("SELFTEST: %d 个负对照全部有效" % len(mutations))
    return 0


if __name__ == "__main__":
    sys.exit(main())
