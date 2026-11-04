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
RE_A11_EXPANDED = re.compile(
    r"a11 palette expanded base=([0-9a-fA-F]{1,8}) highlight=([0-9a-fA-F]{1,8}) "
    r"artwork=(\w+) art=(\d+)x(\d+) gridBg=(\d+)")
RE_A11_COLLAPSED = re.compile(
    r"a11 palette collapsed bg=([0-9a-fA-F]{1,8}) fg=([0-9a-fA-F]{1,8}) "
    r"primary=([0-9a-fA-F]{1,8}) secondary=([0-9a-fA-F]{1,8}) contrast=([\d.]+) "
    r"art=(\d+)x(\d+) gridBg=(\d+) gridFg=(\d+)")
RE_A11_SPEC = re.compile(
    r"a11 card spec art=(\d+) artCollapsed=(\d+) btn=(\d+)/(\d+) src=(\d+)"
    r" titlePx=(\d+) artistPx=(\d+) timePx=(\d+) trackDp=(\d+)"
    r" density=([\d.]+) fontScale=([\d.]+) radius=(-?\d+) hostCorner=(-?\d+)")
RE_A11_ART = re.compile(
    r"a11 art state=(\w+) size=(\d+) scaleType=(\w+) clip=(\w+)"
    r" titlePx=(\d+) subtextPx=(\d+) titleMedium=(\d)")

A11_ART_DP, A11_ART_SMALL_DP = 52, 36
A11_BTN_DP, A11_BTN_CENTER_DP, A11_SRC_DP = 48, 52, 16
A11_TITLE_SP, A11_ARTIST_SP, A11_TIME_SP, A11_TRACK_DP = 16, 14, 14, 3
# 收起态（MediaStyle 模板）：标题 14sp（跟 fontScale），副文 12**dp**（不跟 fontScale）
A11_COLLAPSED_TITLE_SP, A11_COLLAPSED_SUBTEXT_DP = 14, 12
# Y7s framework-res 实读：Theme.DeviceDefault.dialogCornerRadius -> config_dialogCornerRadius=2dp
A11_ART_RADIUS_DP = 2


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
        "a11e": [RE_A11_EXPANDED.search(l) for l in text.splitlines()
                 if "a11 palette expanded" in l],
        "a11c": [RE_A11_COLLAPSED.search(l) for l in text.splitlines()
                 if "a11 palette collapsed" in l],
        "a11spec": [RE_A11_SPEC.search(l) for l in text.splitlines() if "a11 card spec" in l],
        "art": [RE_A11_ART.search(l) for l in text.splitlines() if "a11 art state=" in l],
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
    for marker, bucket in (("a11 palette expanded", "a11e"), ("a11 palette collapsed", "a11c"),
                           ("a11 card spec", "a11spec"), ("a11 art state", "art"),
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
    for mo in m["a11spec"]:
        dens, font = float(mo.group(10)), float(mo.group(11))
        checks = [("art", float(mo.group(1)), A11_ART_DP * dens),
                  ("artCollapsed", float(mo.group(2)), A11_ART_SMALL_DP * dens),
                  ("btn", float(mo.group(3)), A11_BTN_DP * dens),
                  ("btnCenter", float(mo.group(4)), A11_BTN_CENTER_DP * dens),
                  ("src", float(mo.group(5)), A11_SRC_DP * dens)]
        # 排版三项**不能按"展开态"硬期望**：spec 是在卡片构建那一拍打的，
        # 那一刻卡片多半是收起态（A13 真机第一轮就是这样：titlePx=42 而不是 48，
        # 校验器把它判成失败，其实是断言假设了日志里不存在的信息）。
        # 收起/展开两套都是 A11 的规格：展开 16/14/14sp，收起 14sp + 12**dp**（dp 不跟 fontScale）。
        expanded_set = (_px_expect(A11_TITLE_SP, dens, font),
                        _px_expect(A11_ARTIST_SP, dens, font),
                        _px_expect(A11_TIME_SP, dens, font))
        collapsed_set = (_px_expect(A11_COLLAPSED_TITLE_SP, dens, font),
                         _px_expect(A11_COLLAPSED_SUBTEXT_DP, dens, 1.0),
                         _px_expect(A11_COLLAPSED_SUBTEXT_DP, dens, 1.0))
        got_set = (float(mo.group(6)), float(mo.group(7)), float(mo.group(8)))
        which = None
        for name, expect in (("expanded", expanded_set), ("collapsed", collapsed_set)):
            if all(abs(g - e) <= 1.5 for g, e in zip(got_set, expect)):
                which = name
                break
        if which is None:
            bad = ["title/artist/time=%s 两套 A11 规格都对不上"
                   "（展开 %s / 收起 %s）" % (tuple(got_set),
                                             tuple(round(e, 1) for e in expanded_set),
                                             tuple(round(e, 1) for e in collapsed_set))]
        else:
            bad = []
        bad += ["%s 期望 %.1f 实得 %.1f" % (n, e, g)
                for n, g, e in checks if abs(g - e) > 1.5]
        if int(mo.group(9)) != A11_TRACK_DP:
            bad.append("trackDp 期望 %d 实得 %s" % (A11_TRACK_DP, mo.group(9)))
        # A11 的封面圆角是 A11 主题的常量（2dp），不是宿主 dialogCornerRadius；
        # 若实现改回去读宿主属性，这条就会红（hostCorner 只做对照打印）
        r_exp = round(A11_ART_RADIUS_DP * dens)
        if int(mo.group(12)) != r_exp:
            bad.append("radius 期望 A11 常量 %.0fdp=%.0fpx 实得 %s"
                       % (A11_ART_RADIUS_DP, r_exp, mo.group(12)))
        if bad:
            fails.append("a11spec: " + "; ".join(bad))
        else:
            ok("a11spec", "state=%s art=%s btn=%s/%s src=%s title=%spx track=%sdp radius=%s"
               % (which, mo.group(1), mo.group(3), mo.group(4), mo.group(5), mo.group(6),
                  mo.group(9), mo.group(12)))

    # 收起/展开两态的排版见证：字号单位用错（dp 当 sp）在大字号机器上才会露出来，
    # 所以正例里专门有一条 fontScale=1.3 的样本。
    if media and m["a11spec"] and not m["art"]:
        fails.append("art: 有 spec 日志却没有 a11 art state 见证（几何/排版那半段没跑）")
    dens_spec = [(float(mo.group(10)), float(mo.group(11))) for mo in m["a11spec"]]
    for mo in m["art"]:
        state = mo.group(1).lower()
        title_px, sub_px = int(mo.group(5)), int(mo.group(6))
        medium = int(mo.group(7))
        if not dens_spec:
            break
        dens, font = dens_spec[0]
        bad = []
        if state == "expanded":
            exp_title = A11_TITLE_SP * dens * font
            exp_sub = A11_ARTIST_SP * dens * font
            # 展开态标题**也**该是 sans-serif-medium：A11 的 media_view.xml 里
            # 标题(16sp)的字族资源是 @0x0184021d，艺名/时间(14sp)是 @0x0184021c
            # （work/y7s-a11/xml_media_view.txt:136/145/153），同一对 id 在 Y7s SystemUI
            # 样式表里就是"强调行 vs 普通行"（sysui_res.txt:145332/145338/145346）。
            # 我先前断言"展开态必须还原成常规字族"是想当然，被真机第一份展开态见证顶回来后才复查的。
            if medium != 1:
                bad.append("展开态标题不是 sans-serif-medium —— A11 的标题字族是被强调那一族")
        elif state == "collapsed":
            exp_title = A11_COLLAPSED_TITLE_SP * dens * font
            # 副文在 A11 是 @dimen/notification_subtext_size = 12**dp**：只乘 density，不乘 fontScale
            exp_sub = A11_COLLAPSED_SUBTEXT_DP * dens
        else:
            bad.append("未知的 state=%s" % mo.group(1))
            exp_title = exp_sub = None
        if exp_title is not None:
            if abs(title_px - exp_title) > 1.5:
                bad.append("titlePx 期望 %.1f 实得 %d" % (exp_title, title_px))
            if abs(sub_px - exp_sub) > 1.5:
                bad.append("subtextPx 期望 %.1f（%s：副文是 dp，不跟 fontScale）实得 %d"
                           % (exp_sub, state, sub_px))
        if bad:
            fails.append("art(%s): " % state + "; ".join(bad))
        else:
            ok("art", "%s title=%dpx subtext=%dpx medium=%d" % (state, title_px, sub_px, medium))

    # 两态都要有见证：只跑收起态一轮，展开态的 16/14/14sp 就**从没在真机上验过**
    # （A13 第一轮正是这样，脚本没展开卡片）。
    if media and m["art"]:
        seen_states = {mo.group(1).lower() for mo in m["art"]}
        for need in ("expanded", "collapsed"):
            if need not in seen_states:
                fails.append("art: 本轮没有 %s 态见证 —— 该态排版在真机上未验证"
                             "（门禁需要展开一次卡片）" % need)

    if media and not (m["a11e"] or m["a11c"]):
        fails.append("a11: 没有 A11 取色日志（媒体卡未绑定或 style 不是 a11）")
    for mo in m["a11e"]:
        base = int(mo.group(1), 16) & 0xFFFFFF
        hi = int(mo.group(2), 16) & 0xFFFFFF
        light = hsl_lightness(base)
        bad = []
        if abs(light - 25.0) > 0.6:
            bad.append("背景亮度应锁 25%%，实得 %.1f%%（base=%s）" % (light, mo.group(1)))
        if hi == base:
            bad.append("高亮色与底色相同，IlluminationDrawable 的 ±0.15 没生效")
        # 无封面时 A11 不是给常量，而是把白色送进同一条 HSL 变换 ⇒ 恒等于 0xFF404040
        # （证据 MediaDataManager.java:542-553；0xFF444444 是 resumption 路径 :339 的值）
        if mo.group(3).lower() == "false" and int(mo.group(1), 16) != 0xFF404040:
            bad.append("artwork=false 时 base 应为 A11 的 0xff404040，实得 %s" % mo.group(1))
        aw, ah, grid = int(mo.group(4)), int(mo.group(5)), int(mo.group(6))
        want = a11_grid(aw, ah, 0)
        if grid != want:
            bad.append("取色网格 art=%dx%d 期望 %d 像素 实得 %d"
                       "（A11 是先缩整图再换算 region，见 §14）" % (aw, ah, want, grid))
        if bad:
            fails.append("a11(expanded): " + "; ".join(bad))
        else:
            ok("a11", "expanded base=%%%s L=%.1f%% highlight=%%%s grid=%d"
               % (mo.group(1), light, mo.group(2), grid))

    for mo in m["a11c"]:
        base = int(mo.group(1), 16) & 0xFFFFFF
        primary = int(mo.group(3), 16)
        secondary = int(mo.group(4), 16)
        contrast = float(mo.group(5))
        aw, ah = int(mo.group(6)), int(mo.group(7))
        gbg, gfg = int(mo.group(8)), int(mo.group(9))
        bad = []
        # MediaStyle.ensureColors 的契约就是"正文对底色至少 4.5"，低于它就是校正没跑或跑歪
        if contrast < 4.4:
            bad.append("primary 对底色对比度只有 %.2f（应 >=4.5，ensureColors 未生效？）"
                       % contrast)
        for name, val in (("primary", primary), ("secondary", secondary)):
            if (val >> 24) & 0xFF == 0:
                bad.append("%s alpha=0，文字不会显示" % name)
        want_bg = a11_grid(aw, ah, 0)
        want_fg = a11_grid(aw, ah, 1)
        if gbg != want_bg or gfg != want_fg:
            bad.append("取色网格 art=%dx%d 期望 bg=%d fg=%d 实得 bg=%d fg=%d"
                       % (aw, ah, want_bg, want_fg, gbg, gfg))
        if bad:
            fails.append("a11(collapsed): " + "; ".join(bad))
        else:
            ok("a11", "collapsed bg=%%%s fg=%%%s contrast=%.2f grid=%d/%d"
               % (mo.group(1), mo.group(2), contrast, gbg, gfg))

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
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 card spec art=156 artCollapsed=108 btn=144/156 src=48 titlePx=48 artistPx=42 timePx=42 trackDp=3 density=3.0 fontScale=1.0 radius=6 hostCorner=72
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true titlePx=48 subtextPx=42 titleMedium=1
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 art state=collapsed size=108 scaleType=CENTER clip=false titlePx=42 subtextPx=36 titleMedium=1
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 palette expanded base=ff33404d highlight=ff526980 artwork=true art=200x200 gridBg=11250
11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: a11 palette expanded base=ff404040 highlight=ff666666 artwork=false art=0x0 gridBg=0
11-02 23:10:04 1234 1234 I BetterOriginOS: media_card: a11 palette collapsed bg=ff33404d fg=ffb3c0c7 primary=ffe9f3f7 secondary=ffe0e0e0 contrast=9.40 art=200x200 gridBg=11250 gridFg=13500
"""

# OS5：圆钮是系统自己的（NotificationClearAllImageView 在 A15 全 dex 命中 6、A13 命中 0），
# 模块不补挂，所以没有 attached/tap 行——这套也必须照样全绿，否则等于在 OS5 上误报。
GOOD_OS5 = (GOOD.replace("clear-all native=false", "clear-all native=true")
            .replace("os5_mirror: clear-all attached size=138 marginBottom=120 nav=false"
                     " navSrc=insets navVis=0 navH=0 shade=isExpanded qs=isQsFullScreen"
                     " host=NotificationPanelView\n", "")
            .replace("os5_mirror: clear-all tap inlineListener=1 inlineVis=4 shelfVis=0\n", ""))

# 同一套卡片在 fontScale=1.3 上的样子：sp 字号会跟着放大，dp 字号不会 ——
# 收起态副文（A11 是 12dp）如果被当成 12sp 套上去，这条正例就会红。
GOOD_FS = (GOOD.replace("density=3.0 fontScale=1.0", "density=3.0 fontScale=1.3")
           .replace("titlePx=48 artistPx=42 timePx=42", "titlePx=62 artistPx=55 timePx=55")
           .replace("a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true"
                    " titlePx=48 subtextPx=42",
                    "a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true"
                    " titlePx=62 subtextPx=55")
           .replace("a11 art state=collapsed size=108 scaleType=CENTER clip=false"
                    " titlePx=42 subtextPx=36",
                    "a11 art state=collapsed size=108 scaleType=CENTER clip=false"
                    " titlePx=55 subtextPx=36"))

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
    "A11 亮度没锁住": GOOD.replace("base=ff33404d", "base=ffefeff0"),
    "A11 高亮与底色相同": GOOD.replace("highlight=ff526980", "highlight=ff33404d"),
    "A11 封面尺寸不是 52dp": GOOD.replace("spec art=156", "spec art=180"),
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
    "A11 进度条轨道不是 3dp": GOOD.replace("trackDp=3 density", "trackDp=10 density"),
    "A11 标题字号不对": GOOD.replace("titlePx=48 artistPx=42", "titlePx=57 artistPx=42"),
    "A11 圆角跟了宿主主题": GOOD.replace("radius=6 hostCorner", "radius=72 hostCorner"),
    "A11 圆角日志缺宿主对照": GOOD.replace(" hostCorner=72", ""),
    # 200x200 的封面按"旧实现（只缩 region）"会得到 20000/22504，A11 是 11250/13500
    "A11 取色网格回到旧实现": GOOD.replace("gridBg=11250 gridFg=13500",
                                            "gridBg=20000 gridFg=22504"),
    "A11 正文对比度不达标": GOOD.replace("contrast=9.40", "contrast=2.10"),
    "A11 正文透明看不见": GOOD.replace("primary=ffe9f3f7", "primary=00e9f3f7"),
    "A11 无封面兜底用成 resumption 值": GOOD.replace("base=ff404040 highlight=ff666666 artwork=false",
                                                     "base=ff444444 highlight=ff666666 artwork=false"),
    # 点击链路：监听没挂 = 按钮是死的；内联没藏 = 收纳栏在 A13 上不会收
    "清除按钮点了没监听": GOOD.replace("clear-all tap inlineListener=1", "clear-all tap inlineListener=0"),
    "内联文字没按 OS5 藏起来": GOOD.replace("inlineVis=4", "inlineVis=0"),
    "点击见证日志缺失": GOOD.replace(
        "11-02 23:10:02 1234 1234 I BetterOriginOS: notify_row: os5_mirror: clear-all tap"
        " inlineListener=1 inlineVis=4 shelfVis=0\n", ""),
    # 收起态副文是 12dp（不跟 fontScale）；被当成 12sp 套上去就是 47px
    "收起态副文错用 sp 单位": GOOD_FS.replace(
        "titlePx=55 subtextPx=36", "titlePx=55 subtextPx=47"),
    # 展开态必须把收起态设的 sans-serif-medium 还原掉
    "展开态标题丢了强调字族": GOOD_FS.replace(
        "titlePx=62 subtextPx=55 titleMedium=1", "titlePx=62 subtextPx=55 titleMedium=0"),
    # 默认 Locale 在逗号小数的机器上会写成 contrast=9,40，正则会整条丢掉 -> 静默少跑
    "对比度小数点被本地化（静默丢行）": GOOD.replace("contrast=9.40", "contrast=9,40"),
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
    print("SELFTEST: 大字号正例通过（%d 条断言，dp 副文不随 fontScale）" % len(fs_passed))
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
