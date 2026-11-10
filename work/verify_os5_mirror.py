#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OS5 复刻（Os5Mirror）与 A11 媒体卡的代码级验收器。

只读模块度量日志，不用截图。用法：
    python work/verify_os5_mirror.py <logcat.txt> [--density 3.0] [--selftest]

断言来源（每条都能在 out/LOG-1102-OS5-TO-OS3-ADAPT.md §六 与
out/LOG-1102-A11-MEDIACARD-RE.md §2/§3/§4 里回溯）：
  os5_mirror: radius level=<L> px=<P> cls=<C>      -> P 必须等于档位 dp × density
  os5_mirror: g2 path l= t= r= b= top= bottom=     -> 宽高要够（2.2r 回落判据，2.2~2.6r 换常数套）且 top/bottom 至少一个 > 0
  os5_mirror: shelf topRoundness=1.0 readback=<f> gapPorted=<0|1> -> readback 必须≈1（从视图读回，不是自己打印）
  os5_mirror: clear-all native=<true|false>       -> true 时不得再有补挂日志（OS5 原生已有圆钮）
  os5_mirror: clear-all attached size=<P> marginBottom=<M> nav=<b> navSrc=<s> shade=<s> qs=<s> host=<H> -> P == 46dp×density，M == 40|62dp×density，H 必须是 *Panel*（ConstraintLayout 会吞 gravity）；navSrc 必须是 insets|config；shade/qs 必须是读到的原生判定（isExpanded / isQsFullScreen），不能是猜的阈值
  os5_mirror: clear-all tap inlineListener=<0|1> inlineVis=<V> -> 监听必须 1；内联可见性必须不是 0（4 或 8 都算藏住）
  a11 palette base=<hex> highlight=<hex>           -> base 的 HSL 亮度必须锁在 25±0.6，且 highlight != base
--selftest 会用合成日志跑正例与四个反例，确保断言真的会红。
"""

import math
import re
import sys

RADIUS_LEVEL_DP = {0: 12.0, 1: 18.0, 2: 24.0, 3: 24.0}
SHELF_GAP_DP = 7.0
CLEAR_SIZE_DP = 46.0
CLEAR_MARGIN_DP = (40.0, 62.0)

RE_RADIUS = re.compile(r"os5_mirror: radius level=(-?\d+) px=([\d.]+)")
RE_G2 = re.compile(r"os5_mirror: g2 path l=(-?\d+) t=(-?\d+) r=(-?\d+) b=(-?\d+) "
                   r"top=([\d.]+) bottom=([\d.]+)")
RE_SHELF = re.compile(r"os5_mirror: shelf topRoundness=1\.0 readback=([\d.]+)")
RE_SHELF_GAP = re.compile(r"os5_mirror: shelf gap yFrom=(-?[\d.]+) yTo=(-?[\d.]+)"
                          r" base=(-?[\d.]+) want=(\d+)")
RE_CLEAR = re.compile(r"os5_mirror: clear-all attached size=(\d+) marginBottom=(\d+) nav=(\w+)"
                      r" navSrc=(\w+) navVis=(-?\d+) navH=(-?\d+) shade=(\w+) qs=(\w+)"
                      r"(?: host=(\w+))?")
RE_TAP = re.compile(r"os5_mirror: clear-all tap inlineListener=(\d) inlineVis=(-?\d+)"
                    r" shelfVis=(-?\d+)")
RE_NATIVE = re.compile(r"os5_mirror: clear-all native=(true|false)")
RE_BGCLIP = re.compile(r"os5_mirror: bg clip g2 r=([\d.]+) bounds=(-?\d+),(-?\d+),(-?\d+),(-?\d+)"
                       r"(?: lt=(\w+) rt=(\w+) rb=(\w+) lb=(\w+))?")
# 为什么这一帧没裁。radii-zero = 系统本来没给这枚背景圆角（原生行为）；
# 其余原因（字段名/矩形算不出/抛错）都是我们这侧的失效。
RE_BGSKIP = re.compile(r"os5_mirror: bg clip skipped reason=(\S+) n=(\d+) maxR=([\d.]+)")
# --- A11 通知栏媒体卡（LOG-1103 §2/§5/§9/§12/**§15**/**§20** 的口径）
# 旧口径已作废：52dp 中间键 / 恒深卡 / 2dp 圆角 / 16-14-14sp 都是 QS 播放器；
# 48dp 方按钮是 AOSP 那一支，设备（IS_TIER_VOS=false）走 vigour：36×40、centerInside、无背景。
# srcFrom 记录取色输入来自哪一份图（largeIcon / largeIconDrawn / metadata / argb / none）：
# 用户报"切歌后左边还是上一首"，A11 用的是通知自己的 largeIcon，metadata 常慢一拍。
# track 放在**行尾**（歌名必带空格，放中间会把后面的字段切散）。
RE_A11_PAL = re.compile(
    r"a11 palette state=(\w+) bg=([0-9a-fA-F]{1,8}) fg=([0-9a-fA-F]{1,8})"
    r" primary=([0-9a-fA-F]{1,8}) secondary=([0-9a-fA-F]{1,8})"
    r" colorized=(\d) session=(\d) contrast=([\d.]+)"
    r" artwork=(\w+) art=(\d+)x(\d+)"
    r" srcFrom=(\w+) srcHash=([0-9a-fA-F]+) metaTitle=(.*?) gridBg=(\d+) gridFg=(\d+)"
    r"(?: track=(.*))?$")
RE_A11_SPEC = re.compile(
    r"a11 card spec artSide=contentHeight btnPad=(\d+) btnEnd=(\d+)"
    r" btn=(\d+)x(\d+) src=(\d+) titlePx=(\d+) artistPx=(\d+) timePx=(\d+)"
    r" trackDp=(\d+) colTop=(\d+) colSide=(\d+) colMinH=(\d+) headH=(\d+)"
    r" actMargin=(\d+)/(\d+) density=([\d.]+) fontScale=([\d.]+) radius=(-?\d+)"
    r" hostCorner=(-?\d+)")
# compact/btnPos/mainEndPx/btnMarginPx 是 11-05 收起态（media_q 模板）那三项回读：
# compact=1 时动作带必须在文字**右侧**、给封面的右内缩必须等于 artSide+16dp、且永远没有进度条。
RE_A11_ART = re.compile(
    r"a11 art state=(\w+) size=(\d+) contentH=(-?\d+) scaleType=(\w+) clip=(\w+)"
    r" titlePx=(\d+) subtextPx=(\d+) timePx=(\d+) titleMedium=(\d) seekVis=(-?\d+)"
    r" padTbPx=(\d+) seekDur=(\d+) seekSession=(\d)"
    r" compact=(\d) btnPos=(\w+) mainEndPx=(-?\d+) btnMarginPx=([\d/-]+|-)")
RE_A11_RELIEF = re.compile(
    r"a11 art relief side=(\d+)x(\d+) src=(\d+)x(\d+) hash=([0-9a-fA-F]+)"
    r" color=([0-9a-fA-F]{1,8}) from=(\w+) rtl=(\d)")
# 封面换图的过渡（用户 11-04 要求淡入淡出，A11 原生是硬切）：cross=有旧图交叉淡，in=空窗后淡入
RE_A11_FADE = re.compile(r"a11 art fade mode=(cross|in|retint)(?: ms=(\d+))?")
# 原生 right_icon 被 RemoteViews 在换歌那一拍设回可见 ⇒ "右边闪现小封面"；守卫写回后要读回 GONE
RE_A11_NATIVE = re.compile(r"a11 art native reshown rehidden vis=(-?\d+) w=(\d+) h=(\d+)")
# 施加之后**回读真实画笔/视图状态**的见证（不是实现里再算一遍同一个表达式）。
# elapsedColor/totalColor 是两枚时间画笔上的实际色：用户报"右边那枚在黑底上没反色、看不清"，
# 只有读回来的值能证明屏幕上那一帧是什么色。
# 注意：带引号的中文注释别写进 re.compile 的续行里 —— logshape_lint 会把它们当正则字面量，
# 然后到模块源码里找这段中文，永远找不到就报"日志形状分叉"。
RE_A11_SEEK = re.compile(
    r"a11 seek prog=([0-9a-fA-F]{1,8}) track=([0-9a-fA-F]{1,8}) base=([0-9a-fA-F]{1,8})"
    r" thumbAlpha=(-?\d+) thumbColor=([0-9a-fA-F]{1,8}) thumbPx=(-?\d+)"
    r" uniformFill=(\d) barEnabled=(\d) thicknessDp=([\d.]+)"
    r" totalShadow=(\d) totalShadowColor=([0-9a-fA-F]{1,8})"
    r" elapsedColor=([0-9a-fA-F]{1,8}) totalColor=([0-9a-fA-F]{1,8})"
    r" elapsedShadow=(\d)")

# A11 通知卡的真实规格（来源见 LOG-1103 对应小节；dp 值，断言处再乘 density）
A11_BTN_DP, A11_BTN_H_DP = 36, 40         # vigour 那颗：36×40（AOSP 那一支才是 48×48）
A11_BTN_PAD_DP, A11_BTN_END_DP, A11_SRC_DP = 8, 2, 16
A11_TITLE_SP, A11_ARTIST_SP = 14, 12      # 标题 14sp / 副文 12sp（都跟 fontScale）
A11_TIME_SP, A11_TRACK_DP = 14, 3          # 播放器样式：时间 14.0**sp**（跟 fontScale）
A11_THUMB_ALPHA = 255                      # 播放器样式：滑块常驻，不像通知卡那样不可拖就透明
A11_SEEK_PAD_TB_DP = 16                    # media_view.xml 的 paddingTop/Bottom；通知卡那一版是 24
A11_COL_TOP_DP, A11_COL_SIDE_DP = 42, 20  # notification_main_column 的边距
A11_COL_MINH_DP, A11_HEAD_H_DP = 61, 44   # setContentMinHeight / 头部行高
A11_ACT_MARGIN = (14, 20)                 # media_actions 的左右外边距
A11_ART_SCALE = "center_crop"             # attr/scaleType 枚举 6 = centerCrop（§15-4）
A11_ART_RADIUS_DP = 0            # A11 没有 notification_corner_radius 这个 dimen
A11_ART_FADE_MS = 320            # 封面换图过渡；与卡片背景那次 startTransition(320) 对齐
A11_PRIMARY = {"light": 0xFF000000, "dark": 0xFFFFFFFF}
A11_SECONDARY = {"light": 0xFF4D4D4D, "dark": 0xB3FFFFFF}
A11_CARD_BG = {"light": 0xFFFFFFFF, "dark": 0xFF000000}


def _lum8(c):
    def lin(v):
        v = v / 255.0
        return v / 12.92 if v < 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF)


def _ctr(fg, bg):
    a, b = _lum8(fg) + 0.05, _lum8(bg) + 0.05
    return max(a, b) / min(a, b)


def _which_side(bg):
    """A11 shouldUseDark：底色亮度 > 0.5 就用深字，否则浅字。"""
    return "light" if _lum8(bg & 0xFFFFFF if (bg >> 24) == 0 else bg) > 0.5 else "dark"


def _composite(fg, bg):
    """A11 ContrastColorUtil 的整数 alpha 合成（与 work/a11_*_selftest 那份独立参考同源）：
       a = 255 - ((255-bgA)*(255-fgA))/255，通道 = ((fgC*255*fgA)+(bgC*bgA*(255-fgA)))/(a*255)。
       用整数除法是原文行为，改成浮点会让临界点上的判定翻面。"""
    af, ab = (fg >> 24) & 0xFF, (bg >> 24) & 0xFF
    a = 255 - (((255 - ab) * (255 - af)) // 255)
    out = 0
    for sh in (16, 8, 0):
        cf, cb = (fg >> sh) & 0xFF, (bg >> sh) & 0xFF
        c = 0 if a == 0 else (((cf * 255) * af) + (cb * ab * (255 - af))) // (a * 255)
        out |= max(0, min(255, c)) << sh
    return (a << 24) | out


def _alpha_to_meet(color, bg, ratio):
    """A11 `findAlphaToMeetContrast`：本来就够用 ⇒ 原样；否则在 **[当前alpha, 255]** 上
       二分 15 次、判定用**严格大于**、返回 **high**（够用那一侧）。
       这条把"够不够 4.5"从阈值判断升级成**与 A11 同式的独立重算**：
       alpha 停在中间任何值都会被抓，而"A11 加满仍不够"的那种底色会算出 255，不会被误判。"""
    if _ctr(_composite(color, bg), bg) >= ratio:
        return color
    rgb = color & 0xFFFFFF
    low, high = (color >> 24) & 0xFF, 255
    for _ in range(15):
        if high - low <= 0:
            break
        alpha = (low + high) // 2
        if _ctr(_composite((alpha << 24) | rgb, bg), bg) > ratio:
            high = alpha
        else:
            low = alpha
    return (high << 24) | rgb


def hsl_lightness(rgb):
    r = ((rgb >> 16) & 0xFF) / 255.0
    g = ((rgb >> 8) & 0xFF) / 255.0
    b = (rgb & 0xFF) / 255.0
    cmax, cmin = max(r, g, b), min(r, g, b)
    return (cmax + cmin) / 2.0 * 100.0


def a11_grid(sw, sh, region):
    """A11 取色网格的像素数。

    与模块/A11 同源的两步：先按整图面积 22500 缩放（`(int)` 截断）得到**工作位图**，
    区域一律用工作位图的宽高现算 —— `setRegion(0,0,bitmap.getWidth()/2,h)`（整数除）、
    `setRegion((int)(0.4f*bitmap.getWidth()),0,width,height)`。
    （旧版这里对*源图*区域做 floor/ceil 再缩放，200x200 恰好一样，201x201/512x384/1200x800
    就会和模块分叉，把正确的实现判成失败。参考 work/a11_sampling_selftest.py 的用例。）
    """
    area = 22500.0
    if sw <= 0 or sh <= 0:
        return 0
    if sw * sh <= area:
        ww, hh = sw, sh
    else:
        ratio = math.sqrt(area / (sw * sh))
        ww, hh = int(sw * ratio), int(sh * ratio)
        if ww < 1 or hh < 1:
            ww, hh = sw, sh
    # region 0 = 背景（左半幅），1 = 前景（右 60%）；一律在工作位图里现算
    regions = [(0, 0, ww // 2, hh), (int(0.4 * ww), 0, ww, hh)]
    l, t, r, b = regions[region]
    return max(0, r - l) * max(0, b - t)


def parse(text):
    out = {
        "radius": [RE_RADIUS.search(l) for l in text.splitlines() if "os5_mirror: radius" in l],
        "g2": [RE_G2.search(l) for l in text.splitlines() if "os5_mirror: g2 path" in l],
        "shelf": [RE_SHELF.search(l) for l in text.splitlines() if "shelf topRoundness" in l],
        "shelfgap": [RE_SHELF_GAP.search(l) for l in text.splitlines()
                      if "shelf gap yFrom=" in l],
        "clear": [RE_CLEAR.search(l) for l in text.splitlines() if "clear-all attached" in l],
        "native": [RE_NATIVE.search(l) for l in text.splitlines() if "clear-all native=" in l],
        "tap": [RE_TAP.search(l) for l in text.splitlines() if "clear-all tap" in l],
        "bgclip": [RE_BGCLIP.search(l) for l in text.splitlines() if "bg clip g2" in l],
        "bgskip": [RE_BGSKIP.search(l) for l in text.splitlines() if "bg clip skipped" in l],
        "a11pal": [RE_A11_PAL.search(l) for l in text.splitlines()
                    if "a11 palette state=" in l],
        "a11spec": [RE_A11_SPEC.search(l) for l in text.splitlines() if "a11 card spec" in l],
        "art": [RE_A11_ART.search(l) for l in text.splitlines() if "a11 art state=" in l],
        "relief": [RE_A11_RELIEF.search(l) for l in text.splitlines()
                   if "a11 art relief" in l],
        "seek": [RE_A11_SEEK.search(l) for l in text.splitlines() if "a11 seek prog=" in l],
        "artfade": [RE_A11_FADE.search(l) for l in text.splitlines() if "a11 art fade" in l],
        "artnative": [RE_A11_NATIVE.search(l) for l in text.splitlines()
                      if "a11 art native reshown" in l],
    }
    return {k: [m for m in v if m] for k, v in out.items()}


def _px_expect(sp, density, font_scale):
    return sp * density * font_scale


def check(text, density, notify=True, media=True, expect_inert=False):
    m = parse(text)
    lines_all = text.splitlines()
    fails = []
    passed = []
    if not notify:
        SKIP_NOTIFY = True
    if not media:
        SKIP_MEDIA = True

    def ok(name, detail):
        passed.append("%s: %s" % (name, detail))

    # 「正则匹配不到，但那条日志确实存在」是今晚最阴的一类失效：断言静默少跑，
    # fixture 照样绿（本文件顶部的 logshape 教训就是这个）。行数与解析数必须一致。
    lines = text.splitlines()
    for marker, bucket in (("a11 palette state=", "a11pal"),
                           ("a11 card spec", "a11spec"), ("a11 art state", "art"),
                           ("a11 art relief", "relief"), ("a11 seek prog=", "seek"),
                           ("a11 art fade", "artfade"),
                           ("a11 art native reshown", "artnative"),
                           ("clear-all attached", "clear"), ("clear-all tap", "tap"),
                           ("os5_mirror: radius level=", "radius"),
                           ("os5_mirror: g2 path", "g2"),
                           ("os5_mirror: shelf topRoundness", "shelf"),
                           ("os5_mirror: shelf gap yFrom=", "shelfgap"),
                           ("os5_mirror: bg clip g2", "bgclip"),
                           ("os5_mirror: bg clip skipped", "bgskip"),
                           ("clear-all native=", "native")):
        have = sum(1 for l in lines if marker in l)
        got = len(m[bucket])
        if have and have != got:
            fails.append("格式漂移: 有 %d 行「%s」但只解析出 %d 条 —— 断言在静默少跑"
                         % (have, marker, got))

    if notify and expect_inert:
        # OS5/A15 上 NotifyRowFeature 根本不装复刻钩子（OsProfile.isA13OrLower() 关门），
        # 所以"一条 os5_mirror 都没有"才是正确形态；这里反过来断言零行，而不是让它判红。
        stray = sum(1 for l in lines_all if "os5_mirror:" in l)
        if stray:
            fails.append("inert: OS5 上出现 %d 条 os5_mirror 日志 —— 复刻钩子不该在这台机装" % stray)
        else:
            ok("inert", "OS5 上没有任何 os5_mirror 日志（复刻按版本关门，零干扰）")
        return passed, fails

    if notify and not m["radius"]:
        fails.append("radius: 没有任何 os5_mirror 半径度量日志（钩子没跑或 verify 没开）")
    for mo in m["radius"]:
        level = int(mo.group(1))
        px = float(mo.group(2))
        if level not in RADIUS_LEVEL_DP:
            # 之前这里 .get(level, 18) 会把"档位根本没读到"直接算成 18dp 并通过 ——
            # 第五轮审查抓到就是这个：level() 用错 ClassLoader 时永远读到兜底值。
            fails.append("radius: 档位是 %d，不在 0/1/2/3 里 —— 主题档位根本没读到"
                         "（别用兜底值判绿）" % level)
            continue
        expect = RADIUS_LEVEL_DP[level] * density
        if abs(px - expect) > 1.5:
            fails.append("radius: level=%d 期望 %.1fpx 实得 %.1fpx" % (level, expect, px))
        else:
            ok("radius", "level=%d px=%.1f" % (level, px))

    if notify and not m["g2"]:
        fails.append("g2: 没有连续角路径日志（getRoundedRectPath 钩子没触发）")
    for mo in m["g2"]:
        l, t, r, b = (int(mo.group(i)) for i in range(1, 5))
        top, bottom = float(mo.group(5)), float(mo.group(6))
        if top <= 0 and bottom <= 0:
            fails.append("g2: 四角半径都是 0，路径不该由我们接管 l=%d t=%d r=%d b=%d" % (l, t, r, b))
            continue
        radius = max(top, bottom)
        w, h = r - l, b - t
        # 门限是 2.2r（vivo G2CornerUtil），2.2r~2.6r 之间仍画 G2，只是换一套常数
        if min(w, h) <= radius * 2.2:
            ok("g2", "回落普通圆角 w=%d h=%d r=%.1f" % (w, h, radius))
        else:
            band = "1.3r" if min(w, h) > radius * 2.6 else "1.1r"
            ok("g2", "G2 连续角 w=%d h=%d r=%.1f 常数套=%s" % (w, h, radius, band))

    # 之前这条断言永远不可能红：日志里 topRoundness 是我们自己写的字面量，
    # gap 也是把 dp 乘回来再比 —— 两边都是同一个表达式。现在比的是**从视图读回来的值**。
    if notify and not m["shelf"]:
        fails.append("shelf: 没有收纳栏圆角日志（NotificationShelf 钩子没触发）")
    for mo in m["shelf"]:
        readback = float(mo.group(1))
        if readback < 0.999:
            fails.append("shelf: 顶部圆角没生效，读回 mTopRoundness=%s（期望 1.0；"
                         "方法签名变了或被系统改回去了）" % mo.group(1))
        else:
            ok("shelf", "topRoundness 读回 %.3f" % readback)

    # 7dp 间距：A13 的通道是 updateAppearance 里那行 yTranslation（A15 才用 mPaddingBetweenElements）。
    # `yTo == base + want` 是我们代码自己算的表达式，单独比它等于自证（永远绿）。真正有判别力的是
    # 三条帧间关系：① 至少一帧是"从原生基线施加"（base == yFrom）；② 幂等帧的 incoming y 必须
    # 等于我们上次写出的位置，此时 base 要回退一个 want；③ 已经写过的位置再被当成基线加一遍
    # = prev 判定失效 = 每帧累加（真机踩过：254 行只在有可见子项时重算，其余帧保持我们写的值）。
    if notify and not m["shelfgap"]:
        fails.append("shelfgap: 没有 shelf 间距日志（updateAppearance 钩子没触发，或栏里没有可见子项）")
    gap_applied = gap_idem = 0
    written = []
    for mo in m["shelfgap"]:
        y0, y1 = float(mo.group(1)), float(mo.group(2))
        base, want = float(mo.group(3)), float(mo.group(4))
        want_px = SHELF_GAP_DP * density
        if abs(want - want_px) > 1.0:
            fails.append("shelfgap: 目标间距期望 %.1fpx 实得 %.0f" % (want_px, want))
            continue
        if abs(y1 - (base + want)) > 0.6:
            fails.append("shelfgap: yTo=%.1f != base(%.1f)+want(%.0f)，日志自相矛盾（读写字段不同源）"
                         % (y1, base, want))
            continue
        if abs(base - y0) <= 0.6:
            if any(abs(y0 - w) <= 0.6 for w in written):
                fails.append("shelfgap: yFrom=%.1f 是我们上一帧刚写出的位置，却又当成原生基线加了 %dpx"
                             "（prev 判定失效 → 每帧累加）" % (y0, want))
            else:
                gap_applied += 1
                ok("shelfgap", "施加：%.1f -> %.1f（+%0.fpx，原生基线）" % (y0, y1, want))
        elif abs(y0 - (base + want)) <= 0.6:
            gap_idem += 1
            ok("shelfgap", "幂等：%.1f 已是上次写出的位置，回退基线 %.1f 后仍落在 %.1f"
               % (y0, base, y1))
        else:
            fails.append("shelfgap: yFrom=%.1f 既不是 base=%.1f 也不是 base+want=%.1f，"
                         "位移来源不明" % (y0, base, base + want))
        written.append(y1)
    if m["shelfgap"] and gap_applied == 0:
        fails.append("shelfgap: 没有一条从原生基线施加的帧（只有幂等命中 = 间距从没真施加过）")

    if notify and not m["bgclip"]:
        # 没有裁剪行分两种：**系统这一轮没给任何背景圆角**（radii-zero，原生行为，
        # A13 真机 r9 就是这样：只有 3 条 radii-zero），或**我们这侧失效**（字段名/矩形/抛错）。
        # 前者不该永远红着挡路（G2 裁剪本体在 r8 的真机日志里画出来过：
        # `bg clip g2 r=54.0 bounds=0,0,960,279 lt/rt/rb/lb=true`），后者必须红。
        bad = sorted({mo.group(1) for mo in m["bgskip"] if mo.group(1) != "radii-zero"})
        if bad:
            fails.append("bgclip: 没有裁剪行，且 skip 原因是 %s —— 这是我们这侧的反射/几何失效，"
                         "不是系统没给圆角" % ",".join(bad))
        elif m["bgskip"]:
            ok("bgclip", "本轮没有带圆角的背景被绘制（radii-zero n=%s maxR=%s）——"
                         "裁剪代码可达，等一次真的画出圆角背景的样本"
                         % (m["bgskip"][0].group(2), m["bgskip"][0].group(3)))
        else:
            fails.append("bgclip: 没有 bg clip g2 日志，也没有 bg clip skipped 见证"
                         "（onDraw 钩子没跑，或 verify 没开）")
    for mo in m["bgclip"]:
        r = float(mo.group(1))
        w = int(mo.group(4)) - int(mo.group(2))
        h = int(mo.group(5)) - int(mo.group(3))
        if r <= 0.5:
            fails.append("bgclip: 半径为 0（r=%.1f）" % r)
        elif w <= 0 or h <= 0:
            fails.append("bgclip: 背景 bounds 非法 %dx%d" % (w, h))
        else:
            # 四角标志也是断言对象：半径 > 0.5 却四角全 false，说明 mCornerRadii 根本没被写，
            # 裁出来的还是直角 —— 之前这条完全没查，只查了 bounds。
            corners = [mo.group(i) for i in (6, 7, 8, 9)]
            if corners[0] is not None and not any(c == "true" for c in corners):
                fails.append("bgclip: r=%.1f 但四角全不是圆角 %s —— 半径没写进去" % (r, corners))
            elif min(w, h) <= r * 2.2:
                ok("bgclip", "r=%.1f %dx%d 走普通圆角回落（低于 2.2r，符合公式）" % (r, w, h))
            else:
                ok("bgclip", "r=%.1f %dx%d 底色按连续角裁剪 四角=%s" % (r, w, h, ",".join(corners)))

    if notify and not m["clear"] and not m["native"]:
        fails.append("clear: 既没有补挂圆钮的日志，也没有 native 判定日志（一键清除链路整段没跑）")
    if notify and not m["clear"] and m["native"]:
        if m["native"][0].group(1) == "true":
            ok("clear", "系统原生圆钮在位（OS5），模块不补挂——补了就是两枚重叠")
        else:
            fails.append("clear: native=false 却没有补挂日志（A13 上必须补一枚）")
    for mo in m["clear"]:
        size = float(mo.group(1))
        margin = float(mo.group(2))
        nav = mo.group(3).lower() == "true"
        nav_src = mo.group(4)
        nav_vis = int(mo.group(5))
        nav_h = int(mo.group(6))
        host = mo.group(9)
        shade = mo.group(7)
        qs = mo.group(8)
        exp_size = CLEAR_SIZE_DP * density
        exp_margin = (CLEAR_MARGIN_DP[1] if nav else CLEAR_MARGIN_DP[0]) * density
        # OS5 的浮动钮条件（A15 jadx:6113）是
        #   shouldShowClearAll() && mIsExpanded && !isQsFullScreen() && !isKeyguardShowing()
        # mIsExpanded 由 setExpandedHeight 的 `setIsExpanded(f > 0)` 写（A15:2004 / A13:4508），
        # 两版都有公开的 isExpanded() ⇒ 模块必须读那个原生值。
        # 早先这里放行的是"展开分数 > 0.5"，那是我凭空加的阈值，而且 NSSL 两版都没有
        # getExpansionFraction()（同名方法在 AmbientState 上），所以 fraction 只算兜底路径。
        if shade not in ("isExpanded", "fraction"):
            fails.append("clear: 展开状态来源是 %s（应能读到原生 isExpanded）" % shade)
            continue
        # QS 全屏时 OS5 明确不画这枚钮（A15:6113 的 !isQsFullScreen），A13 也有同名方法
        # （OS3 jadx:3491）。qs=unavailable 意味着这一条闸门根本没生效：
        # 下拉到顶打开快捷面板时浮动钮会压在面板上 —— 看得见才谈修。
        if qs != "isQsFullScreen":
            fails.append("clear: QS 全屏判定的来源是 %s（应读到原生 isQsFullScreen，"
                         "否则快捷面板上会多一枚钮）" % qs)
            continue
        # OS5 的分支信号是 CentralSurfaces.isNavigationBarVisible()（导航条此刻可见），
        # 不是"三键还是手势"，也不是静态的 config_showNavigationBar。
        # navSrc=unset 说明这条日志来自还不知道自己用哪个来源的旧构建；config 是兜底，
        # 只有 insets 才证明真的量到了窗口的 navigationBars inset。
        if nav_src not in ("insets", "config"):
            fails.append("clear: 导航条可见性的判定来源是 %s（应为 insets 或 config）" % nav_src)
            continue
        # OS5 看的是"导航条窗口此刻可见"，不是"它留没留出高度"。
        # 手势导航下常见 navVis=1 而 navH=0 —— 这一格必须走 62dp（OS5 也走 62dp），
        # 所以判据只能对 navVis，不能对 navH；拿 inset 高度当判据就是本轮审查抓到的那个错。
        if nav_src == "insets" and nav != (nav_vis == 1):
            fails.append("clear: nav=%s 与 navVis=%d 不一致（边距分支应当用可见性，"
                         "navH=%d 只是见证值）" % (mo.group(3), nav_vis, nav_h))
            continue
        if nav_src == "insets" and nav_vis == 1 and nav_h == 0:
            ok("clear", "手势 hint bar：可见但 0 高，按 OS5 取 %.0fdp" % CLEAR_MARGIN_DP[1])
        if abs(size - exp_size) > 1.0:
            fails.append("clear: 尺寸期望 %.1fpx 实得 %.1fpx" % (exp_size, size))
        elif abs(margin - exp_margin) > 1.0:
            fails.append("clear: 底边距期望 %.1fpx(nav=%s) 实得 %.1fpx"
                         % (exp_margin, nav, margin))
        elif not host:
            fails.append("clear: 挂载日志没有 host=（不知道挂在谁身上，就无法判断 gravity 生不生效）")
        elif "Panel" not in host:
            # A13 的 shelf→NSSL→NotificationsQuickSettingsContainer 是 ConstraintLayout，
            # FrameLayout 的 BOTTOM|CENTER_HORIZONTAL 会被 generateLayoutParams 丢掉，
            # 按钮不报错、只是静静落在左上角 —— 只能靠这条见证住。
            fails.append("clear: 宿主是 %s，不是 NotificationPanelView；ConstraintLayout 会吞掉 gravity"
                         % host)
        else:
            ok("clear", "size=%.1f margin=%.1f nav=%s shade=%s qs=%s host=%s"
               % (size, margin, nav, shade, qs, host))

    if notify and m["clear"] and m["native"] and m["native"][0].group(1) == "true":
        fails.append("clear: 系统原生圆钮在位（native=true）却还补挂了 —— OS5 上会出现两枚重叠按钮")
    if notify and m["clear"] and not m["tap"]:
        fails.append("tap: 补挂了圆钮却没有点击见证日志（脚本没点，或这一击没落到我们的 View 上）")
    for mo in m["tap"]:
        listener, inline_vis = int(mo.group(1)), int(mo.group(2))
        # 监听由 NSSL.setShelfController -> setShelfListener -> shelf.setClearNotificationListener
        # 一次性挂上；没挂上时 performClick() 什么都不做，现象是"按钮点了没反应"。
        # inlineVis 只要求"不是 VISIBLE(0)"：hideInline 现在把系统要显示的内联文字降级成
        # INVISIBLE(4)（不是 GONE(8)），因为 A13 的 changeShelfVisibility(z) 判据是
        # `z && 内联==8`，恒写 8 会把这条判据简化成每帧都在翻的 z —— 收纳栏因此闪烁。
        if listener != 1:
            fails.append("tap: 内联按钮没有 OnClickListener（这一击是静默空转）")
        elif inline_vis == 0:
            fails.append("tap: 内联按钮仍然可见(0)，hideInline 没生效（OS5 收纳栏不该出现这行文字）")
        elif inline_vis not in (4, 8):
            fails.append("tap: 内联按钮可见性=%d，既不是 0 也不是 4/8，见证值异常" % inline_vis)
        else:
            ok("tap", "inlineListener=1 inlineVis=%d shelfVis=%s"
               % (inline_vis, mo.group(3)))

    if media and not m["a11spec"]:
        fails.append("a11spec: 没有 A11 卡规格见证日志（style 不是 a11 或卡片没建起来）")
    dens_spec = []
    for mo in m["a11spec"]:
        dens, font = float(mo.group(16)), float(mo.group(17))
        # 带上 spec 自己的三个字号，art 行要和它同源
        dens_spec.append((dens, font, int(mo.group(6)), int(mo.group(7)), int(mo.group(8))))
        bad = []
        for idx, want_dp, name in ((1, A11_BTN_PAD_DP, "按钮内缩"),
                                   (2, A11_BTN_END_DP, "按钮外边距"),
                                   (3, A11_BTN_DP, "btnW"),
                                   (4, A11_BTN_H_DP, "btnH"),
                                   (5, A11_SRC_DP, "src"),
                                   (10, A11_COL_TOP_DP, "colTop"),
                                   (11, A11_COL_SIDE_DP, "colSide"),
                                   (12, A11_COL_MINH_DP, "colMinH"),
                                   (13, A11_HEAD_H_DP, "headH"),
                                   (14, A11_ACT_MARGIN[0], "actMarginStart"),
                                   (15, A11_ACT_MARGIN[1], "actMarginEnd")):
            want = want_dp * dens
            if abs(float(mo.group(idx)) - want) > 1.5:
                bad.append("%s 期望 %ddp=%.1fpx 实得 %s" % (name, want_dp, want, mo.group(idx)))
        if int(mo.group(9)) != A11_TRACK_DP:
            bad.append("trackDp 期望 %d 实得 %s" % (A11_TRACK_DP, mo.group(9)))
        # A11 的通知卡还是 9-patch 那一代：没有 notification_corner_radius，圆角应为 0。
        # hostCorner 只是对照值（宿主主题圆角），断言绝不能读它——那就成了"跟着宿主美化"。
        if int(mo.group(18)) != A11_ART_RADIUS_DP:
            bad.append("radius 期望 %d（A11 通知卡无圆角）实得 %s，宿主对照 %s"
                       % (A11_ART_RADIUS_DP, mo.group(18), mo.group(19)))
        if not mo.group(19):
            bad.append("spec 缺 hostCorner 对照值：没有它就无法证明我们没跟宿主主题")
        # spec 里的三个字号要和后面 art 行的字号同源（同一块卡片两处打出不一致 = 有半段没跑）
        for idx, name in ((6, "titlePx"), (7, "artistPx"), (8, "timePx")):
            if int(mo.group(idx)) <= 0:
                bad.append("%s=%s 不合理（卡片里的文本视图没拿到字号）" % (name, mo.group(idx)))
        if bad:
            fails.append("a11spec: " + "; ".join(bad))
        else:
            ok("a11spec", "btn=%sx%s pad=%s end=%s src=%s col=%s/%s/%s head=%s act=%s/%s"
               " track=%s radius=%s（宿主对照 %s）"
               % (mo.group(3), mo.group(4), mo.group(1), mo.group(2), mo.group(5),
                  mo.group(10), mo.group(11), mo.group(12), mo.group(13),
                  mo.group(14), mo.group(15), mo.group(9),
                  mo.group(18), mo.group(19)))

    # 封面几何 + 排版：A11 只有一套（MediaStyle 模板），不存在"展开态另一套 16/14/14sp"
    if media and m["a11spec"] and not m["art"]:
        fails.append("art: 有 spec 日志却没有 a11 art state 见证（几何/排版那半段没跑）")
    if media and m["art"] and not dens_spec:
        # 别把它报成"该态没在真机上验证"——那是"没展开卡片"的说法，真实成因是 density 未知
        fails.append("art: 解析到 %d 行 art 见证却没有 a11 card spec（density 取不到，"
                     "排版/尺寸断言整段没跑）" % len(m["art"]))
    prelayout = set()
    measurable = set()
    seek_covered = set()      # 真的出现过"展开且有时长"那一格（进度条显隐判据的正侧样本）
    compact_measured = set()  # 收起态那份 media_q 结构真的被建出来并且量到了高度
    for mo in m["art"]:
        state = mo.group(1).lower()
        size, content_h = int(mo.group(2)), int(mo.group(3))
        scale, clip = mo.group(4).lower(), mo.group(5).lower()
        title_px, sub_px, time_px = int(mo.group(6)), int(mo.group(7)), int(mo.group(8))
        medium = int(mo.group(9))
        if not dens_spec:
            break
        dens, font, s_title, s_artist, s_time = dens_spec[0]
        if content_h <= 0:
            # 卡片还没测量：measuredHeight=0 减掉边距就是负数。
            # 这一帧既不红也不算数 —— 但同一态必须另有一帧 contentH>0（下面的"未验证"检查负责）。
            prelayout.add(state)
            continue
        bad = []
        if size != content_h:
            bad.append("封面不是正方形：size=%d contentH=%d（A11 是**边长=内容列高**）"
                       % (size, content_h))
        if clip != "false":
            bad.append("clip=%s：A11 的 right_icon 没有裁剪/圆角" % mo.group(5))
        if scale != A11_ART_SCALE:
            bad.append("scaleType=%s，A11 两份模板写的都是 scaleType=6=**centerCrop**（§15-4）"
                       % mo.group(4))
        exp_title = A11_TITLE_SP * dens * font
        exp_sub = A11_ARTIST_SP * dens * font        # 12sp 跟 fontScale
        exp_time = A11_TIME_SP * dens * font         # 14sp 跟 fontScale（播放器那一版）
        if abs(title_px - exp_title) > 1.5:
            bad.append("titlePx 期望 %.1f（14sp 跟 fontScale=%s）实得 %d"
                       % (exp_title, font, title_px))
        if abs(sub_px - exp_sub) > 1.5:
            bad.append("subtextPx 期望 %.1f（设备副文 = 12**sp**）实得 %d" % (exp_sub, sub_px))
        if abs(time_px - exp_time) > 1.5:
            bad.append("timePx 期望 %.1f（播放器样式 14sp，跟 fontScale=%s）实得 %d"
                       % (exp_time, font, time_px))
        if medium != 1:
            bad.append("标题不是 sans-serif-medium（A11 line1 的字族是被强调那一族）")
        seek_vis = mo.group(10)
        dur = int(mo.group(12))
        session = int(mo.group(13))
        compact = int(mo.group(14))
        btn_pos = mo.group(15).lower()
        main_end = int(mo.group(16))
        btn_margin = mo.group(17)
        # A11 的显隐判据是三条与：**有会话 && 有时长 && 展开**（NotificationMediaTemplateViewWrapper
        # .resolveViews + compact_media_notification_seekbar_enabled=false，§15-7）。
        # 会话被拆掉那一刻 lastDurationMs 还留着旧值，此时藏条是对的，不能算回归。
        # compact=1 是收起态那份 media_q 结构：它的根不是 MediaNotificationView，
        # 模板里那颗进度条 ViewStub 从来没人 inflate ⇒ 即使行被展开也必须没有条。
        want_vis = "0" if (state == "expanded" and dur > 0 and session == 1
                           and compact != 1) else "8"
        if seek_vis != want_vis:
            bad.append("%s 态(compact=%d dur=%dms session=%d)进度条容器 seekVis=%s，A11 该态应为 %s"
                       % (state, compact, dur, session, seek_vis, want_vis))
        if state == "expanded" and dur > 0 and session == 1 and compact != 1:
            seek_covered.add("expanded")
        # 收起态排布（media_q：main_column 横向、动作带在文字右侧、无左右外边距）
        if compact == 1:
            if btn_pos != "beside":
                bad.append("compact=1 但 btnPos=%s：收起态动作带必须在文字**右侧**"
                           "（media_q 的 main_column 是横向的），在下方是展开那份 big_media_q" % btn_pos)
            if btn_margin != "0/0":
                bad.append("compact=1 但 btnMarginPx=%s：收起模板的 media_actions 没写任何左右外边距"
                           "（14dp/20dp 只属于展开那份）" % btn_margin)
            exp_end = size + 16 * dens
            if abs(main_end - exp_end) > 1.5:
                bad.append("给封面的右内缩期望 %.1fpx（=实测封面 %dpx + notification_content_margin_end"
                           " 16dp）实得 %dpx —— 不随封面重算就会把文字压在封面下"
                           % (exp_end, size, main_end))
        else:
            if btn_pos != "below":
                bad.append("compact=0 却 btnPos=%s：展开那份的动作带是文字列的兄弟、在其下方" % btn_pos)
            if main_end != -1:
                bad.append("compact=0 却有 mainEndPx=%d：展开态没有横向 main_column 这一层" % main_end)
        # 进度条那一版：用户 11-04 早上指定"要播放器（media_view.xml）那个样式"，
        # 它的可观测区别就是触控带 16dp（通知卡那一版是 24dp，§15-6）
        pad_tb = int(mo.group(11))
        exp_pad = A11_SEEK_PAD_TB_DP * dens
        if abs(pad_tb - exp_pad) > 1.5:
            bad.append("进度条上下内缩期望 %.1fpx（播放器那一版 %ddp）实得 %dpx —— %s"
                       % (exp_pad, A11_SEEK_PAD_TB_DP, pad_tb,
                          "24dp＝又用回通知卡那一版" if abs(pad_tb - 24 * dens) <= 1.5
                          else "和 A11 任何一种都对不上"))
        # 同一块卡片的两处见证必须同源：spec 打的是建卡那一拍，art 打的是套规格那一拍
        if title_px != s_title:
            bad.append("titlePx 与 spec 行不一致：art=%d spec=%d（有半段用了旧值）" % (title_px, s_title))
        if sub_px != s_artist:
            bad.append("subtextPx 与 spec 的 artistPx 不一致：art=%d spec=%s" % (sub_px, s_artist))
        if time_px != s_time:
            bad.append("timePx 与 spec 的 timePx 不一致：art=%d spec=%s" % (time_px, s_time))
        if bad:
            fails.append("art(%s): " % state + "; ".join(bad))
        else:
            measurable.add(state)
            if compact == 1:
                compact_measured.add(size)
            ok("art", "%s size=%d=contentH scale=%s clip=%s title=%dpx subtext=%dpx"
               " time=%dpx medium=%d seekVis=%s padTB=%dpx dur=%dms"
               " compact=%d btnPos=%s mainEndPx=%d btnMargin=%s"
               % (state, size, mo.group(4), mo.group(5), title_px, sub_px, time_px,
                  medium, seek_vis, pad_tb, dur, compact, btn_pos, main_end, btn_margin))

    # density 未知时上一条已经说明了真实成因（建卡那一拍的日志没抓到），别再报成
    # "该态没在真机上验过"——那是"没展开卡片"的说法，会把人往错的方向查。
    if media and m["art"] and dens_spec:
        for need in ("expanded", "collapsed"):
            if need not in measurable:
                extra = "（只出现过 contentH≤0 的预布局帧）" if need in prelayout else ""
                fails.append("art: 本轮没有 %s 态的**可测量**见证 —— 该态在真机上未验证%s"
                             "（门禁需要展开一次卡片）" % (need, extra))
        # 展开帧全是"没有时长"的帧时，"该不该显示进度行"只在负侧取过样 —— 判据空转，报红。
        if "expanded" in measurable and "expanded" not in seek_covered:
            fails.append("art: 展开帧没有一条同时满足\"有会话 + 有时长\"，"
                         "进度条显隐只在\"该藏\"那一侧取样 —— 本轮没验过\"该露\"")
        # 收起态结构是 11-05 新增的那条产品行为，它没有可测量样本时不能靠"collapsed 这个词出现过"过关
        if not compact_measured:
            fails.append("art: 本轮没有一条 compact=1 的**可测量**见证 —— 收起态（media_q 横向"
                         " main_column、动作带在文字右侧）在真机上没跑过（面板全程展开，"
                         "或补扫把收起子视图建成了展开结构）")

    if media and not m["a11pal"]:
        fails.append("a11: 没有 A11 取色日志（媒体卡未绑定或 style 不是 a11）")
    card_bgs = set()
    card_prims = set()
    # 取色真正采样过的那张封面指纹；浮雕必须用同一张（否则底色和屏幕上的图不同源＝滞后一首）
    sampled_hashes = set()
    # "A11 到此为止"的对比度读数：不判红，但必须看得见（别让它变成静默的"看不清"）
    limited = []
    for mo in m["a11pal"]:
        state = mo.group(1).lower()
        bg = int(mo.group(2), 16)
        primary = int(mo.group(4), 16)
        secondary = int(mo.group(5), 16)
        colorized, session = mo.group(6), mo.group(7)
        contrast = float(mo.group(8))
        artwork = mo.group(9).lower() == "true"
        aw, ah = int(mo.group(10)), int(mo.group(11))
        src_from = mo.group(12)
        src_hash = mo.group(13)
        gbg, gfg = int(mo.group(15)), int(mo.group(16))
        track = mo.group(17) or "-"
        bad = []
        card_bgs.add(mo.group(2))
        card_prims.add(mo.group(4))
        sampled_hashes.add(src_hash)
        # 取色输入与"有没有封面"必须自洽：artwork=true 却说 srcFrom=none 是见证在说谎；
        # 有会话且要着色却 srcFrom=none，就是"退回默认底 + 文字色跟着错"那种观感的源头。
        if artwork and src_from == "none":
            bad.append("artwork=true 但 srcFrom=none（自相矛盾）")
        if src_from not in ("largeIcon", "largeIconDrawn", "metadata", "argb", "none"):
            bad.append("srcFrom 取值不认识：%s" % src_from)
        # A11 isColorizedMedia：MediaStyle + 有会话 ⇒ 默认着色（app 显式写
        # android.colorized=false 才算关）。所以 session=1 而 colorized=0 是矛盾。
        if session == "1" and colorized != "1":
            bad.append("session=1 但 colorized=0，与 A11 的 isColorizedMedia 相反")
        if colorized == "0" and (bg & 0xFFFFFF) not in (0xFFFFFF, 0x000000):
            bad.append("未着色时底色应为 notification_material_background_color"
                       "（#ffffffff / 夜 black），实得 %s" % mo.group(2))
        side = _which_side(bg)
        want_p = A11_PRIMARY[side]
        want_s = A11_SECONDARY[side]
        # 前景色不是"够不够 4.5"这种阈值判断，而是 A11 那条二分的**结果值**：独立重算一遍，
        # 实得必须一模一样。"加到一半"会被抓；"加满还是不够"（饱和橙这类）算出来就是 255，
        # 不会被误判成回归 —— 没有 app 显式前景色时，A11 的着色分支只抬 alpha、不翻色族
        # （`A11-Notification-MediaStyle.java:2478-2483`）。
        for name, val, want, grp in (("primary", primary, want_p, 4),
                                     ("secondary", secondary, want_s, 5)):
            want_val = _alpha_to_meet(want, bg, 4.5) if (colorized == "1" and bg != 0) else want
            if val != want_val:
                bad.append("%s 期望 %08x（A11 findAlphaToMeetContrast 在 bg=%08x 上的结果），"
                           "实得 %08x" % (name, want_val & 0xFFFFFFFF,
                                          bg & 0xFFFFFFFF, val & 0xFFFFFFFF))
            if (val & 0xFFFFFF) != (want & 0xFFFFFF):
                bad.append("%s 的 RGB 不该动（色族由底色亮度选，实得 %s）"
                           % (name, mo.group(grp)))
        # 对比度必须就是"日志里那两色"的真实值：防止见证自己报个好数
        real_p = _ctr(primary, bg | 0xFF000000)
        if abs(real_p - contrast) > 0.06:
            bad.append("contrast 字段=%.2f 与实算 %.2f 不一致（见证自说自话）"
                       % (contrast, real_p))
        want_bg = a11_grid(aw, ah, 0) if artwork else 0
        want_fg = a11_grid(aw, ah, 1) if artwork else 0
        if gbg != want_bg or gfg != want_fg:
            bad.append("取色网格 art=%dx%d 期望 bg=%d fg=%d 实得 bg=%d fg=%d"
                       "（bg=左半幅、fg=右 60%%，且先缩整图再换算 region）"
                       % (aw, ah, want_bg, want_fg, gbg, gfg))
        if bad:
            fails.append("a11(%s): " % state + "; ".join(bad))
        else:
            ok("a11", "%s bg=%%%s primary=%%%s secondary=%%%s contrast=%.2f grid=%d/%d"
               " src=%s track=%s"
               % (state, mo.group(2), mo.group(4), mo.group(5), contrast, gbg, gfg,
                  src_from, track))

    # A11 的着色分支到 alpha 上限为止：这类读数不判红，但要在结果里点名，
    # 免得"观感看不清"被一条绿色的断言悄悄放过
    if media and limited:
        ok("a11limit", "底色饱和到 A11 也只到这些对比度（不翻色族）：" + "; ".join(limited[:4]))

    # 封面浮雕：边长 = 原图短边（§4a 居中裁方），用的底色必须是本屏卡片那一支底色
    if media and m["a11pal"] and not m["relief"]:
        fails.append("relief: 有取色日志却没有 a11 art relief 见证（封面后处理那半段没跑）")
    for mo in m["relief"]:
        sw, sh = int(mo.group(1)), int(mo.group(2))
        rw, rh = int(mo.group(3)), int(mo.group(4))
        bad = []
        if sw != sh:
            bad.append("浮雕输出不是正方形 %dx%d" % (sw, sh))
        if rw and rh and sw != min(rw, rh):
            bad.append("浮雕边长 %d 不等于原图短边 %d（A11 是居中裁方）" % (sw, min(rw, rh)))
        if card_bgs and mo.group(6) not in card_bgs:
            bad.append("浮雕用的底色 %s 不在本屏卡片底色里（%s）"
                       % (mo.group(6), ",".join(sorted(card_bgs))))
        if mo.group(8) not in ("0", "1"):
            bad.append("rtl 见证值异常：%s" % mo.group(8))
        # 浮雕画的那张图必须就是取色采样过的那一张：两张图不同源＝"底色是上一首、
        # 屏幕上是这一首"（用户 11-04 报的现象，真机见证 hash 对不上）
        if sampled_hashes and mo.group(5) not in sampled_hashes:
            bad.append("浮雕用的封面指纹 %s 不在取色采样过的集合里（%s）—— 底色与屏幕上那张图不同源"
                       % (mo.group(5), ",".join(sorted(sampled_hashes))))
        if mo.group(7) not in ("largeIcon", "largeIconDrawn", "metadata"):
            bad.append("浮雕输入来源 srcFrom=%s（不该是 none/未知：没封面时应当清空而不是继续画）"
                       % mo.group(7))
        if bad:
            fails.append("relief: " + "; ".join(bad))
        else:
            ok("relief", "side=%d src=%dx%d hash=%s color=%%%s from=%s rtl=%s"
               % (sw, rw, rh, mo.group(5), mo.group(6), mo.group(7), mo.group(8)))

    # 封面换图的过渡（用户 11-04 要求）：一窗口里出现过 ≥2 张不同封面，就必须至少有一次淡入淡出，
    # 否则就是"啪地蹦一张"那种硬切。
    if media:
        relief_hashes = set(mo.group(5) for mo in m["relief"] if mo.group(5) != "0")
        if len(relief_hashes) >= 2 and not m["artfade"]:
            fails.append("fade: 本轮换了 %d 张封面却没有一条 a11 art fade —— 换图还是硬切"
                         % len(relief_hashes))
        for mo in m["artfade"]:
            if mo.group(1) == "retint":
                # 同一张封面只是按新底色重染：必须**不**再起一次淡入（起两次就是用户看到的硬跳）
                if mo.group(2) not in (None, ""):
                    fails.append("fade: retint 却带了 ms=%s（重染不该重启过渡）" % mo.group(2))
                else:
                    ok("fade", "mode=retint（同图重染底色，不起新过渡）")
                continue
            if mo.group(2) is None or int(mo.group(2)) != A11_ART_FADE_MS:
                fails.append("fade: %s 过渡 %sms，模块常数应为 %dms（与卡片背景那次对齐）"
                             % (mo.group(1), mo.group(2), A11_ART_FADE_MS))
            else:
                ok("fade", "mode=%s ms=%s" % (mo.group(1), mo.group(2)))
        # 原生 right_icon 被重新显示过一次，就必须读回它真的被藏回去了
        for mo in m["artnative"]:
            if mo.group(1) != "8":
                fails.append("native: 守卫写回后 vis=%s（应为 8=GONE），"
                             "闪现小封面那条路径没自愈成功" % mo.group(1))
            else:
                ok("native", "reshown→rehidden vis=8 size=%sx%s" % (mo.group(2), mo.group(3)))

    # 进度条施加后的真实状态（§15-7 updateSeekBarTint / setScrubberVisible）：
    # 同一个基色的三档 alpha（255/192/128）、轨道 3dp、total 带底色阴影而 elapsed 不带。
    # 见证读的是画笔/视图当前值，所以这里比的是"施加结果"，不是实现自己的表达式。
    if media and m["a11pal"] and not m["seek"]:
        fails.append("seek: 有取色日志却没有 a11 seek 见证（进度条那半段没施加）")
    for mo in m["seek"]:
        prog = int(mo.group(1), 16)
        track = int(mo.group(2), 16)
        base = int(mo.group(3), 16)
        bad = []
        for val, alpha, name in ((prog, 192, "progress"), (track, 128, "track")):
            if (val >> 24) & 0xFF != alpha:
                bad.append("%s alpha 期望 %d（A11 withAlpha）实得 %d"
                           % (name, alpha, (val >> 24) & 0xFF))
            if val & 0xFFFFFF != base & 0xFFFFFF:
                bad.append("%s 的 RGB 与基色不一致：%08x vs %08x" % (name, val, base))
        if card_prims and "%08x" % base not in card_prims:
            bad.append("进度条基色 %08x 不是本屏任一 primaryTextColor（A11 用 originalIconColor，"
                       "着色时它就是主文字色，§15-8）" % base)
        alpha_thumb = int(mo.group(4))
        # 用户指定的是**播放器**那一版：滑块常驻（通知卡那版才会把 thumb 调透明），
        # 不可拖只 setEnabled(false)。所以断言"永远看得见"，别退回藏滑块的旧行为。
        if alpha_thumb != A11_THUMB_ALPHA:
            bad.append("thumbAlpha=%s，播放器样式应常驻 %d（滑块被藏起来＝又照通知卡那版画的）"
                       % (mo.group(4), A11_THUMB_ALPHA))
        if abs(float(mo.group(9)) - A11_TRACK_DP) > 0.01:
            bad.append("轨道厚度 %s dp，A11 是 maxHeight=3dp（§15-6）" % mo.group(9))
        # 滑块"看得见"不能只看 alpha：颜色全透明的占位 alpha 也是 255（这个洞踩过）
        thumb_col = int(mo.group(5), 16)
        if (thumb_col >> 24) & 0xFF == 0:
            bad.append("thumbColor=%s 是全透明占位 ⇒ 屏幕上没有滑块（A11 那颗是可见实心圆）"
                       % mo.group(5))
        if int(mo.group(6)) <= 0:
            bad.append("thumbPx=%s：滑块没有内在尺寸，画不出东西" % mo.group(6))
        # A11 的已读部分与轨道同厚；10dp 粗胶囊是 iOS/OS5 那一款
        if mo.group(7) != "1":
            bad.append("uniformFill=%s，A11 样式下进度应与轨道同厚（10dp 粗胶囊是 iOS 那款）"
                       % mo.group(7))
        if mo.group(10) != "1":
            bad.append("total 没有阴影（A11 setShadowLayer(1.5,1.5,1.5, 卡底色)）")
        if card_bgs and mo.group(11) not in card_bgs:
            bad.append("total 阴影色 %s 不是卡片底色（%s）"
                       % (mo.group(11), ",".join(sorted(card_bgs))))
        # 两枚时间**读回来的色**必须在"这一块卡"的底色上够对比度 —— 底色就取同一行的阴影色
        # （A11 的 setShadowLayer 用的就是卡片底色，已经断言过它 ∈ card_bgs），
        # 这样收起/展开两块卡各自判各自的那一帧，不会拿展开的底去判收起的字。
        # 用户报的就是这一条："右边那枚在黑底上没做反色，看不清"。
        elapsed_c = int(mo.group(12), 16)
        total_c = int(mo.group(13), 16)
        if mo.group(14) != "0":
            bad.append("elapsed 也有阴影 —— A11 只给 total 加（原生就这一处）")
        card_bg = int(mo.group(11), 16)
        for cname, cval in (("elapsed", elapsed_c), ("total", total_c)):
            if cval & 0xFFFFFF != base & 0xFFFFFF:
                bad.append("%s 的色 %08x 与进度条基色 %08x 不同一（A11 三档共用 originalIconColor）"
                           % (cname, cval, base))
            c = _ctr(cval, card_bg | 0xFF000000)
            if c < 4.44:
                # 同 palette 那一处：A11 只加 alpha、不翻色族，加满仍不够就到此为止
                if (cval >> 24) & 0xFF < 255:
                    bad.append("%s 时间色 %08x 对卡片底色 %08x 只有 %.2f:1 而 alpha 没加满"
                               % (cname, cval, card_bg, c))
                else:
                    limited.append("%s %.2f:1（alpha 已满）" % (cname, c))
        if bad:
            fails.append("seek: " + "; ".join(bad))
        else:
            ok("seek", "prog=%s track=%s base=%s thumb=a%s/%s/%spx uniformFill=%s"
               " thickness=%s totalShadow=%s/%s elapsedColor=%s totalColor=%s"
               " elapsedShadow=%s"
               % (mo.group(1), mo.group(2), mo.group(3), mo.group(4), mo.group(5),
                  mo.group(6), mo.group(7), mo.group(9), mo.group(10), mo.group(11),
                  mo.group(12), mo.group(13), mo.group(14)))
    return passed, fails


GOOD = """
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: radius level=1 px=54.0 cls=ExpandableNotificationRow
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: g2 path l=0 t=64 r=1032 b=296 top=54.0 bottom=54.0
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf topRoundness=1.0 readback=1.000
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf gap yFrom=0.0 yTo=21.0 base=0.0 want=21
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf gap yFrom=21.0 yTo=21.0 base=0.0 want=21
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: radius level=2 px=72.0 cls=ExpandableNotificationRow
11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: radius level=0 px=36.0 cls=ExpandableNotificationRow
11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: bg clip g2 r=54.0 bounds=0,0,1032,232 lt=true rt=true rb=true lb=true
11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: clear-all native=false
11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: clear-all attached size=138 marginBottom=120 nav=false navSrc=insets navVis=0 navH=0 shade=isExpanded qs=isQsFullScreen host=NotificationPanelView
11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: clear-all tap inlineListener=1 inlineVis=4 shelfVis=0
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 card spec artSide=contentHeight btnPad=24 btnEnd=6 btn=108x120 src=48 titlePx=42 artistPx=36 timePx=42 trackDp=3 colTop=126 colSide=60 colMinH=183 headH=132 actMargin=42/60 density=3.0 fontScale=1.0 radius=0 hostCorner=72
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art state=expanded size=255 contentH=255 scaleType=CENTER_CROP clip=false titlePx=42 subtextPx=36 timePx=42 titleMedium=1 seekVis=0 padTbPx=48 seekDur=3536000 seekSession=1 compact=0 btnPos=below mainEndPx=-1 btnMarginPx=42/60
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art state=collapsed size=126 contentH=126 scaleType=CENTER_CROP clip=false titlePx=42 subtextPx=36 timePx=42 titleMedium=1 seekVis=8 padTbPx=48 seekDur=3536000 seekSession=1 compact=1 btnPos=beside mainEndPx=174 btnMarginPx=0/0
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 palette state=expanded bg=ff33404d fg=ffe9f3f7 primary=ffffffff secondary=b3ffffff colorized=1 session=1 contrast=10.60 artwork=true art=200x200 srcFrom=largeIcon srcHash=aaa111 metaTitle=示例歌 (Live) 版 gridBg=11250 gridFg=13500 track=示例歌 (Live)
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 palette state=collapsed bg=ffffffff fg=ff000000 primary=ff000000 secondary=ff4d4d4d colorized=0 session=0 contrast=21.00 artwork=false art=0x0 srcFrom=none srcHash=0 metaTitle=- gridBg=0 gridFg=0 track=-
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art relief side=200x200 src=200x200 hash=aaa111 color=ff33404d from=largeIcon rtl=0
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 palette state=expanded bg=ff33404d fg=ffe9f3f7 primary=ffffffff secondary=b3ffffff colorized=1 session=1 contrast=10.60 artwork=true art=200x200 srcFrom=largeIcon srcHash=ccc333 metaTitle=第二首 gridBg=11250 gridFg=13500 track=第二首
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art fade mode=in ms=320
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art relief side=200x200 src=200x200 hash=ccc333 color=ff33404d from=largeIcon rtl=0
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art fade mode=cross ms=320
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art native reshown rehidden vis=8 w=96 h=96
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 seek prog=c0ffffff track=80ffffff base=ffffffff thumbAlpha=255 thumbColor=ffffffff thumbPx=30 uniformFill=1 barEnabled=1 thicknessDp=3.0 totalShadow=1 totalShadowColor=ff33404d elapsedColor=ffffffff totalColor=ffffffff elapsedShadow=0
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 seek prog=c0000000 track=80000000 base=ff000000 thumbAlpha=255 thumbColor=ff000000 thumbPx=30 uniformFill=1 barEnabled=0 thicknessDp=3.0 totalShadow=1 totalShadowColor=ffffffff elapsedColor=ff000000 totalColor=ff000000 elapsedShadow=0
"""

# OS5：圆钮是系统自己的（NotificationClearAllImageView 在 A15 全 dex 命中 6、A13 命中 0），
# 模块不补挂，所以没有 attached/tap 行——这套也必须照样全绿，否则等于在 OS5 上误报。
GOOD_OS5 = (GOOD.replace("clear-all native=false", "clear-all native=true")
            .replace("os5_mirror: clear-all attached size=138 marginBottom=120 nav=false"
                     " navSrc=insets navVis=0 navH=0 shade=isExpanded qs=isQsFullScreen"
                     " host=NotificationPanelView\n", "")
            .replace("os5_mirror: clear-all tap inlineListener=1 inlineVis=4 shelfVis=0\n", ""))

# 同一套卡片在 fontScale=1.3 上的样子：**三处文字全是 sp**（标题 14sp、副文 12sp、
# 播放器那一版的时间 14sp）⇒ 都要跟着放大。谁把单位写成 dp，这条正例就会红。
GOOD_FS = (GOOD.replace("density=3.0 fontScale=1.0", "density=3.0 fontScale=1.3")
           .replace("titlePx=42 artistPx=36 timePx=42", "titlePx=55 artistPx=47 timePx=55")
           .replace("a11 art state=expanded size=255 contentH=255"
                    " scaleType=CENTER_CROP clip=false titlePx=42 subtextPx=36 timePx=42",
                    "a11 art state=expanded size=255 contentH=255"
                    " scaleType=CENTER_CROP clip=false titlePx=55 subtextPx=47 timePx=55")
           .replace("a11 art state=collapsed size=126 contentH=126"
                    " scaleType=CENTER_CROP clip=false titlePx=42 subtextPx=36 timePx=42",
                    "a11 art state=collapsed size=126 contentH=126"
                    " scaleType=CENTER_CROP clip=false titlePx=55 subtextPx=47 timePx=55"))

# 真机 A13 的 r9 一轮：整段观察窗口里系统只画了直角背景（三条 radii-zero 见证），
# 裁剪本体在 r8 画出来过。这种"本轮无从验证"不该把门禁永久染红，但也不能变成静默放行，
# 所以它是一条**正例**（带可见的 note），而失效原因（字段名/矩形/抛错）走反例。
GOOD_NOC = GOOD.replace(
    "os5_mirror: bg clip g2 r=54.0 bounds=0,0,1032,232 lt=true rt=true rb=true lb=true",
    "os5_mirror: bg clip skipped reason=radii-zero n=3 maxR=0.0")

BAD_CASES = {
    # 7dp 间距：四种失效形态都要红。累加 = prev 判定失效，把上一帧自己写出的位置又当原生基线；
    # 没施加 = yTo 和 base+want 对不上；只剩幂等命中 = 从没真施加过；基线来源不明 = 读的不是这条链路。
    "间距每帧累加": GOOD.replace(
        "11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf gap"
        " yFrom=21.0 yTo=21.0 base=0.0 want=21",
        "11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf gap"
        " yFrom=21.0 yTo=42.0 base=21.0 want=21"),
    "间距根本没施加": GOOD.replace("yFrom=0.0 yTo=21.0 base=0.0", "yFrom=0.0 yTo=0.0 base=0.0"),
    "间距只剩幂等命中": GOOD.replace(
        "11-02 23:10:01 1234 1234 I BetterOriginOS: notify_row: os5_mirror: shelf gap"
        " yFrom=0.0 yTo=21.0 base=0.0 want=21\n", ""),
    "间距基线来源不明": GOOD.replace("yFrom=0.0 yTo=21.0 base=0.0", "yFrom=100.0 yTo=21.0 base=0.0"),
    "radius 档位算错": GOOD.replace("level=1 px=54.0", "level=1 px=36.0"),
    "清除按钮尺寸错": GOOD.replace("size=138", "size=100"),
    "展开状态读不出来": GOOD.replace("shade=isExpanded", "shade=unavailable"),
    # OS5 的 !isQsFullScreen 这一条：读不到原生判定就等于闸门没生效，快捷面板上会多一枚钮
    "QS 全屏闸门没生效": GOOD.replace("qs=isQsFullScreen", "qs=unavailable"),
    # 少一个字段 = 旧构建的行形，正则整条解析不到；行数与解析数必须相等，否则静默少跑
    "挂载日志没有 qs 字段（旧行形）": GOOD.replace(" qs=isQsFullScreen", ""),
    # A11 通知卡的反例：每条只改一处材料，且必须被 §2/§5/§9/§12/**§15**/**§20** 的某条断言抓住
    "A11 按钮内缩不是 8dp": GOOD.replace("btnPad=24 btnEnd=6", "btnPad=16 btnEnd=6"),
    # 设备（IS_TIER_VOS=false）走 vigour：36×40。串成 AOSP 那一支的 48dp 就该红（§20-1）
    "A11 按钮串成 AOSP 那一支的 48dp": GOOD.replace("btn=108x120", "btn=144x120"),
    "A11 按钮高度不是 40dp": GOOD.replace("btn=108x120", "btn=108x144"),
    "A11 列上间距串成 AOSP 的 46dp": GOOD.replace("colTop=126", "colTop=138"),
    "A11 列边距串成 AOSP 的 16dp": GOOD.replace("colSide=60", "colSide=48"),
    "A11 列最小高串成 AOSP 的 39dp": GOOD.replace("colMinH=183", "colMinH=117"),
    "A11 头部行高度不对": GOOD.replace("headH=132", "headH=150"),
    "A11 动作带边距不对": GOOD.replace("actMargin=42/60", "actMargin=30/60"),
    "A11 封面不再等于内容块高": GOOD.replace(
        "a11 art state=expanded size=255 contentH=255",
        "a11 art state=expanded size=180 contentH=255"),
    # 收起态（media_q）三条：动作带位置、右内缩是否跟着封面重算、以及"收起绝不出现进度条"
    "A11 收起态动作带排到文字下方": GOOD.replace(
        "compact=1 btnPos=beside", "compact=1 btnPos=below"),
    "A11 收起态右内缩按常量钉死（没跟封面重算）": GOOD.replace(
        "compact=1 btnPos=beside mainEndPx=174", "compact=1 btnPos=beside mainEndPx=357"),
    "A11 收起态动作带带了外边距": GOOD.replace(
        "mainEndPx=174 btnMarginPx=0/0", "mainEndPx=174 btnMarginPx=42/60"),
    # 补扫路径没判出 contracted ⇒ 收起子视图建成展开结构：逐帧断言全都自洽，
    # 只有"本轮必须有 compact=1 可测量帧"这条门能抓住它（防整段产品行为静默没跑）
    "A11 收起子视图被建成展开结构": GOOD.replace(
        "compact=1 btnPos=beside mainEndPx=174 btnMarginPx=0/0",
        "compact=0 btnPos=below mainEndPx=-1 btnMarginPx=42/60"),
    "背景没有 G2 裁剪": GOOD.replace("bg clip g2 r=54.0", "bg clip g2 r=0.0"),
    # 真机踩过：A13 的 Rect.toShortString() 是 `[0,0][960,279]`，不是 AOSP 印象里的
    # `(0, 0 - 960, 279)`。日志一旦回到平台格式化字符串，正则就整条解析不到（静默少跑）。
    "背景裁剪回到平台格式化": GOOD.replace("bounds=0,0,1032,232", "bounds=(0, 0 - 1032, 232)"),
    # 缺席必须能分辨成因：radii-zero 是系统没给圆角（可容忍），其它原因是我们坏了（红）
    "裁剪缺席且成因是反射失效": GOOD_NOC.replace("reason=radii-zero", "reason=no-radii-field"),
    "裁剪缺席且没有任何见证": GOOD_NOC.replace(
        "11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: bg clip"
        " skipped reason=radii-zero n=3 maxR=0.0\n", ""),
    "有半径但四角全直角": GOOD.replace("lt=true rt=true rb=true lb=true",
                                       "lt=false rt=false rb=false lb=false"),
    "A11 进度条轨道不是 3dp": GOOD.replace("trackDp=3 colTop", "trackDp=10 colTop"),
    # 用户报"A11 那版看着不像 A11"的三处指纹：滑块是透明占位、没有尺寸、进度是 10dp 粗胶囊
    "A11 滑块是全透明占位（看不见）": GOOD.replace(
        "thumbColor=ffffffff thumbPx=30", "thumbColor=00ffffff thumbPx=30"),
    "A11 滑块没有内在尺寸": GOOD.replace("thumbColor=ffffffff thumbPx=30",
                                      "thumbColor=ffffffff thumbPx=0"),
    "A11 进度条退回 iOS 粗胶囊": GOOD.replace("uniformFill=1", "uniformFill=0"),
    # 进度条样式走回"通知卡那一版"（16dp 触控带 → 24dp）：用户 11-04 明确否掉过这一版
    "A11 进度条用回通知卡那一版的 24dp": GOOD.replace(" padTbPx=48", " padTbPx=72"),
    "A11 进度条触控带见证整段缺失": GOOD.replace(" padTbPx=48", ""),
    # 展开帧一条时长都没抓到（会话被拆）⇒ "该露"那一侧从没取样，判据空转必须报红
    "A11 展开帧全无时长（显隐判据空转）": GOOD.replace(
        "seekVis=0 padTbPx=48 seekDur=3536000 seekSession=1",
        "seekVis=8 padTbPx=48 seekDur=0 seekSession=1"),
    "A11 标题字号不对": GOOD.replace("clip=false titlePx=42 subtextPx=36", "clip=false titlePx=57 subtextPx=36"),
    "A11 封面又加了圆角裁剪": GOOD.replace("clip=false", "clip=true"),
    "A11 scaleType 退回 CENTER_INSIDE": GOOD.replace(
        "scaleType=CENTER_CROP", "scaleType=CENTER_INSIDE"),
    "A11 标题丢了强调字族": GOOD.replace("titleMedium=1", "titleMedium=0"),
    "A11 圆角跟了宿主主题": GOOD.replace("radius=0 hostCorner", "radius=72 hostCorner"),
    "A11 圆角日志缺宿主对照": GOOD.replace(" hostCorner=72", ""),
    # 200x200 的封面按旧实现（只缩 region）会得到 20000/22504，A11 是 11250/13500
    "A11 取色网格回到旧实现": GOOD.replace("gridBg=11250 gridFg=13500",
                                            "gridBg=20000 gridFg=22504"),
    "A11 正文对比度不达标": GOOD.replace("contrast=10.60", "contrast=2.10"),
    # 字段值与实色不符 = 见证在自报好消息
    "A11 对比度字段与实色不一致": GOOD.replace("contrast=10.60", "contrast=19.90"),
    "A11 副文被调淡": GOOD.replace("secondary=b3ffffff", "secondary=6bffffff"),
    "A11 未着色却用封面色当底": GOOD.replace(
        "bg=ffffffff fg=ff000000 primary=ff000000 secondary=ff4d4d4d colorized=0",
        "bg=ff33404d fg=ff000000 primary=ff000000 secondary=ff4d4d4d colorized=0"),
    "A11 有会话却报未着色": GOOD.replace("colorized=1 session=1", "colorized=0 session=1"),
    "A11 浮雕底色与卡片不一致": GOOD.replace(
        "relief side=200x200 src=200x200 hash=aaa111 color=ff33404d",
        "relief side=200x200 src=200x200 hash=aaa111 color=ff123456"),
    "A11 浮雕输出不是正方形": GOOD.replace("relief side=200x200", "relief side=200x180"),
    "A11 浮雕边长不等于原图短边": GOOD.replace(
        "relief side=200x200 src=200x200", "relief side=250x250 src=200x200"),
    # 进度条施加结果（§15-7）：三档 alpha、thumb 随可拖开合、3dp、只 total 有阴影
    "A11 进度条没降 alpha（progress 满不透明）": GOOD.replace(
        "a11 seek prog=c0ffffff", "a11 seek prog=ffffffff"),
    "A11 轨道色 RGB 跟基色分叉": GOOD.replace(
        "track=80ffffff base=ffffffff", "track=80b3ffff base=ffffffff"),
    "A11 基色不是正文主色": GOOD.replace(
        "base=ffffffff thumbAlpha=255", "base=ffff8800 thumbAlpha=255"),
    # 播放器样式滑块常驻：被藏起来＝又退回通知卡那版（用户 11-04 早上明确否掉了那版）
    "A11 又把进度条滑块藏起来了": GOOD.replace(
        "base=ffffffff thumbAlpha=255", "base=ffffffff thumbAlpha=0"),
    "A11 轨道厚度回到 4dp": GOOD.replace("thicknessDp=3.0", "thicknessDp=4.0"),
    "A11 total 没有底色阴影": GOOD.replace(
        "totalShadow=1 totalShadowColor=ff33404d", "totalShadow=0 totalShadowColor=ff33404d"),
    "A11 阴影色不是卡片底色": GOOD.replace(
        "totalShadowColor=ff33404d", "totalShadowColor=ff123456"),
    "A11 elapsed 也多了一层阴影": GOOD.replace(
        "totalColor=ff000000 elapsedShadow=0", "totalColor=ff000000 elapsedShadow=1"),
    # 用户报的第二条：黑底上那枚总时长没做反色。把深底那一行的两枚时间换成深色字，必须报红
    "A11 时间在深底上没反色（对比度不足）": GOOD.replace(
        "elapsedColor=ffffffff totalColor=ffffffff",
        "elapsedColor=ff1a1a1a totalColor=ff1a1a1a"),
    # 用户报的第三条：切歌后取色还是上一首。有封面却 srcFrom=none 是这条链断裂的指纹
    "A11 有封面却报告没取到输入": GOOD.replace(
        "artwork=true art=200x200 srcFrom=largeIcon srcHash=aaa111",
        "artwork=true art=200x200 srcFrom=none srcHash=aaa111"),
    "A11 浮雕缺来源见证": GOOD.replace(" hash=aaa111 color=ff33404d from=largeIcon rtl=0",
                          " hash=aaa111 color=ff33404d rtl=0"),
    # 用户 11-04 报的现象：屏幕上是这一首的封面，左半底色却是上一首那张图取的颜色。
    # 判据 = "浮雕用的那张图指纹，必须是取色真正采样过的那一张"
    "A11 换了两张封面却没有淡入淡出": GOOD.replace(
        "a11 art fade mode=in ms=320\n", "").replace(
        "a11 art fade mode=cross ms=320\n", ""),
    "A11 封面过渡时长不对（硬切级别）": GOOD.replace(
        "a11 art fade mode=cross ms=320", "a11 art fade mode=cross ms=0"),
    "A11 原生小封面写回后仍可见": GOOD.replace(
        "native reshown rehidden vis=8", "native reshown rehidden vis=0"),
    "A11 浮雕与取色不是同一张封面（滞后一首）": GOOD.replace(
        "src=200x200 hash=aaa111 color=ff33404d", "src=200x200 hash=bbb222 color=ff33404d"),
    "A11 seek 见证整段缺失": GOOD.replace("a11 seek prog=", "a11 seekX prog="),
    "A11 取色日志整段缺失": GOOD.replace(
        "media_card: a11 palette state=expanded", "media_card: a11 paletteX state=expanded"),
    "A11 只有收起态几何见证": GOOD.replace(
        "11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: "
        "a11 art state=expanded size=255 contentH=255"
        " scaleType=CENTER_CROP clip=false titlePx=42 subtextPx=36 timePx=42"
        " titleMedium=1 seekVis=0 padTbPx=48 seekDur=3536000 seekSession=1"
        " compact=0 btnPos=below mainEndPx=-1 btnMarginPx=42/60\n", ""),
    # 进度条容器的显隐判据（§15-7）：收起态没有紧凑条、展开态有时长就该有
    "A11 收起态也多画了一条进度条": GOOD.replace(
        "titleMedium=1 seekVis=8", "titleMedium=1 seekVis=0"),
    "A11 展开态把进度条藏掉了": GOOD.replace(
        "titleMedium=1 seekVis=0", "titleMedium=1 seekVis=8"),
    # 点击链路：监听没挂 = 按钮是死的；内联没藏 = 收纳栏在 A13 上不会收
    "清除按钮点了没监听": GOOD.replace("clear-all tap inlineListener=1", "clear-all tap inlineListener=0"),
    "内联文字没按 OS5 藏起来": GOOD.replace("inlineVis=4", "inlineVis=0"),
    "点击见证日志缺失": GOOD.replace(
        "11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: clear-all tap"
        " inlineListener=1 inlineVis=4 shelfVis=0\n", ""),
    # 单位只有在 fontScale≠1 时才可分辨：设备副文是 12**sp**（要跟），进度条时间是 14**dp**（不跟）。
    # 这两条反例都建立在 GOOD_FS 上 —— 在 fontScale=1.0 的材料上它们本来就同值，抓不到。
    # 单位只有 fontScale≠1 才可分辨：播放器这版时间是 14**sp**（要跟），写成 14dp 就少 13px
    "A11 播放器时间错用 dp 单位（不跟着放大）": GOOD_FS.replace(
        "subtextPx=47 timePx=55", "subtextPx=47 timePx=42"),
    "A11 副文错用 dp 单位（不跟着放大）": GOOD_FS.replace(
        "titlePx=55 subtextPx=47", "titlePx=55 subtextPx=36"),
    # 展开态必须把收起态设的 sans-serif-medium 还原掉
    # 默认 Locale 在逗号小数的机器上会写成 contrast=9,40，正则会整条丢掉 -> 静默少跑
    "对比度小数点被本地化（静默丢行）": GOOD.replace("contrast=10.60", "contrast=10,60"),
    # OS5 上系统已有原生圆钮，模块再补一枚就是屏幕上两个重叠的清除按钮
    "OS5 上重复补挂圆钮": GOOD.replace("clear-all native=false", "clear-all native=true"),
    # A13 的那层容器是 ConstraintLayout：gravity 会被 generateLayoutParams 丢掉，按钮落在左上角
    "圆钮挂到 ConstraintLayout 上": GOOD.replace("host=NotificationPanelView",
                                                  "host=NotificationsQuickSettingsContainer"),
    "圆钮挂载日志没有宿主": GOOD.replace(" shade=isExpanded qs=isQsFullScreen host=NotificationPanelView",
                                          " shade=isExpanded qs=isQsFullScreen"),
    # OS5 的分支是"导航条此刻可见"，判定来源必须是 insets/config 之一；unset = 旧构建或没跑到
    "导航条可见性来源未判定": GOOD.replace(" navSrc=insets ", " navSrc=unset "),
    # 手势 hint bar：可见但 0 高 —— 若代码拿"留没留出高度"当判据，这里就会 nav=false 而 navVis=1
    "把导航条高度当成可见性": GOOD.replace(
        "nav=false navSrc=insets navVis=0 navH=0", "nav=false navSrc=insets navVis=1 navH=0"),
    "边距与可见性判定相反": GOOD.replace(
        "marginBottom=120 nav=false navSrc=insets", "marginBottom=186 nav=false navSrc=insets"),
}


def selftest(density):
    passed, fails = check(GOOD, density)
    if fails:
        print("SELFTEST FAIL: 正例被判红 ->")
        for f in fails:
            print("   ", f)
        return 1
    print("SELFTEST: 正例通过（%d 条断言）" % len(passed))
    fs_passed, fs_fails = check(GOOD_FS, density)
    if fs_fails:
        print("SELFTEST FAIL: fontScale=1.3 正例被判红 ->")
        for f in fs_fails:
            print("   ", f)
        return 1
    print("SELFTEST: 大字号正例通过（%d 条断言；sp 字号跟 fontScale、14dp 的时间不跟）"
          % len(fs_passed))
    o5_passed, o5_fails = check(GOOD_OS5, density)
    if o5_fails:
        print("SELFTEST FAIL: OS5 正例（原生圆钮、无补挂无点击见证）被判红 ->")
        for f in o5_fails:
            print("   ", f)
        return 1
    print("SELFTEST: OS5 正例通过（%d 条断言）" % len(o5_passed))
    nc_passed, nc_fails = check(GOOD_NOC, density)
    if nc_fails:
        print("SELFTEST FAIL: 「本轮只有 radii-zero 见证」正例被判红 ->")
        for f in nc_fails:
            print("   ", f)
        return 1
    print("SELFTEST: 无圆角背景正例通过（%d 条断言，radii-zero 不算我们的失效）"
          % len(nc_passed))
    bad = 0
    for name, text in BAD_CASES.items():
        if text == GOOD:
            print("SELFTEST FAIL: 反例「%s」没改到任何材料（空炮，永远抓不到东西）" % name)
            bad += 1
            continue
        _, fails = check(text, density)
        if not fails:
            print("SELFTEST FAIL: 反例「%s」没有被抓住（断言形同虚设）" % name)
            bad += 1
        else:
            print("SELFTEST: 反例「%s」正确报红 -> %s" % (name, fails[0]))
    return 1 if bad else 0


def main(argv):
    density = 3.0
    files = []
    run_selftest = False
    notify = True
    media = True
    expect_inert = False
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--density":
            i += 1
            density = float(argv[i])
        elif a == "--selftest":
            run_selftest = True
        elif a == "--notify-only":
            media = False
        elif a == "--expect-inert":
            expect_inert = True
        elif a == "--media-only":
            notify = False
        else:
            files.append(a)
        i += 1
    rc = 0
    if run_selftest:
        rc = selftest(density)
    for path in files:
        with open(path, encoding="utf-8", errors="replace") as f:
            text = f.read()
        passed, fails = check(text, density, notify, media, expect_inert)
        for p in passed:
            print("  ok   " + p)
        for fmsg in fails:
            print("  FAIL " + fmsg)
        print("%s: %s（density=%.2f，%d 条通过 / %d 条失败）"
              % (path, "全绿" if not fails else "有失败", density, len(passed), len(fails)))
        if fails:
            rc = 1
    if not files and not run_selftest:
        print(__doc__)
        rc = 2
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
