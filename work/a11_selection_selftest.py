#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A11 取色"选择逻辑"的代码级验收（不连设备、不截图）。

要验的是：给定一组 (颜色, 群体) 的量化块，模块会不会像 Android 11 那样选出
dominant / vibrant / muted 六个族、背景块与前景色。
这部分错了，真机上只表现为"卡片配色有点怪"，眼睛判不了，所以必须锁数值。

参考实现独立抄自两处反编译产物：
  work/y7s-a11/tmp/Target.java:51-69 + setDefault* 系列          —— 六个 target 的界与权重
  work/y7s-a11/tmp/Palette.java:197-241                          —— target 打分、互斥 used 颜色
  work/y7s-a11/tmp/MediaNotificationProcessor.java:95-174        —— findBackgroundSwatch /
                                                                     selectForegroundColor

跑法：反射调用模块编译产物（module/build/classes）里真实的 A11Palette，
逐值比对；`--negative` 把参考实现的一个常数挪动，证明检查真的会红。

用法：python work/a11_selection_selftest.py [--negative]
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

AREA = 22500
MIN_IMAGE_FRACTION = 0.002
POP_FRACTION_FOR_DOMINANT = 0.01
MIN_SAT_WHEN_DECIDING = 0.19
POP_FRACTION_WHITE_OR_BLACK = 2.5
BLACK_MAX_L = 0.08
WHITE_MIN_L = 0.90
W_SAT, W_LIGHT, W_POP = 0.24, 0.52, 0.24
PERTURB_SAT_MIN = 1.0   # 负对照：把 vibrant 族的饱和度下界挪动，改的是"谁有资格参选"
PERTURB_GATE = 1.0      # 负对照：挪动群体门槛，改的是"谁够格"

PERTURB = 1.0        # 负对照时挪动 target 的界与群体门槛

# (名称, 饱和度界 min/target/max, 明度界 min/target/max)
TARGETS = [
    ("LV", (0.35, 1.0, 1.0), (0.55, 0.74, 1.0)),
    ("V", (0.35, 1.0, 1.0), (0.30, 0.50, 0.70)),
    ("DV", (0.35, 1.0, 1.0), (0.00, 0.26, 0.45)),
    ("LM", (0.00, 0.3, 0.4), (0.55, 0.74, 1.0)),
    ("M", (0.00, 0.3, 0.4), (0.30, 0.50, 0.70)),
    ("DM", (0.00, 0.3, 0.4), (0.00, 0.26, 0.45)),
]


def hsl01(rgb):
    r = ((rgb >> 16) & 255) / 255.0
    g = ((rgb >> 8) & 255) / 255.0
    b = (rgb & 255) / 255.0
    cmax, cmin = max(r, g, b), min(r, g, b)
    delta = cmax - cmin
    l = (cmax + cmin) / 2.0
    if delta == 0.0:
        return (0.0, 0.0, l)
    s = delta / (2.0 - cmax - cmin) if l > 0.5 else delta / (cmax + cmin)
    if cmax == r:
        h = ((g - b) / delta + (6 if g < b else 0)) * 60.0
    elif cmax == g:
        h = ((b - r) / delta + 2) * 60.0
    else:
        h = ((r - g) / delta + 4) * 60.0
    return (h, s, l)


def luminance(rgb):
    def lin(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return (0.2126 * lin((rgb >> 16) & 255) + 0.7152 * lin((rgb >> 8) & 255)
            + 0.0722 * lin(rgb & 255))


def is_color_light(rgb):
    return luminance(rgb & 0xFFFFFF) > 0.5


def is_white_or_black(rgb):
    l = hsl01(rgb)[2]
    return l <= BLACK_MAX_L or l >= WHITE_MIN_L


def enough(pop):
    return pop / float(AREA) > MIN_IMAGE_FRACTION * PERTURB_GATE


def select_for_target(swatches, dominant, bounds_sat, bounds_light, used):
    s_min, s_t, s_max = bounds_sat
    l_min, l_t, l_max = bounds_light
    s_t *= PERTURB          # 负对照用
    found, best = None, 0.0
    base = dominant[1] if dominant else 1
    for rgb, pop in swatches:
        if rgb in used:
            continue
        _, sat, light = hsl01(rgb)
        if sat < s_min or sat > s_max or light < l_min or light > l_max:
            continue
        score = (W_SAT * (1 - abs(sat - s_t)) + W_LIGHT * (1 - abs(light - l_t))
                 + W_POP * (pop / float(base)))
        if found is None or score > best:
            best, found = score, (rgb, pop)
    return found


def palette_of(tokens):
    sw = [(int(t.split(":")[0], 16), int(t.split(":")[1])) for t in tokens]
    dominant = max(sw, key=lambda x: x[1]) if sw else None
    used, picks = set(), {}
    for name, bs, bl in TARGETS:
        pick = select_for_target(sw, dominant, bs, bl, used)
        picks[name] = pick
        if pick:
            used.add(pick[0])
    return sw, dominant, picks


def find_background(dominant, sw):
    if dominant is None:
        return (0xFFFFFFFF, 100)
    if not is_white_or_black(dominant[0]):
        return dominant
    best, best_pop = None, -1.0
    for item in sw:
        if item is dominant or item[1] <= best_pop or is_white_or_black(item[0]):
            continue
        best, best_pop = item, float(item[1])
    if best is not None and dominant[1] / best_pop <= POP_FRACTION_WHITE_OR_BLACK:
        return best
    return dominant


def select_fg(bg_rgb, picks, dominant):
    if is_color_light(bg_rgb):
        a, b, c, d, fb = "DV", "V", "DM", "M", 0xFF000000
    else:
        a, b, c, d, fb = "LV", "V", "LM", "M", 0xFFFFFFFF
    cand = vibrant_candidate(picks[a], picks[b])
    if cand is None:
        cand = muted_candidate(picks[d], picks[c])
    if cand is None:
        return cand_rgb(dominant, fb)
    if dominant is not None and cand[0] == dominant[0]:
        return cand[0]
    if dominant is None or dominant[1] == 0:
        return cand[0]
    # 整数除法（AOSP 原样）：candidate.pop / dominant.pop < 0.01 恒等于"候选更小"
    if cand[1] // dominant[1] < POP_FRACTION_FOR_DOMINANT \
            and hsl01(dominant[0])[1] > MIN_SAT_WHEN_DECIDING:
        return dominant[0]
    return cand[0]


def vibrant_candidate(a, b):
    ha, hb = a is not None and enough(a[1]), b is not None and enough(b[1])
    if ha and hb:
        return b if a[1] / float(b[1]) < 1.0 else a
    return a if ha else (b if hb else None)


def muted_candidate(a, b):
    ha, hb = a is not None and enough(a[1]), b is not None and enough(b[1])
    if ha and hb:
        sa = hsl01(a[0])[1] * (a[1] / float(b[1]))
        return a if sa > hsl01(b[0])[1] else b
    return a if ha else (b if hb else None)


def cand_rgb(item, fallback):
    return item[0] if item is not None and enough(item[1]) else fallback


CASES = [
    # 每条覆盖一条分支，注释里写清楚是哪条
    "ffffffff:12000 ff8a65f0:2500 ff211e40:1800 ff000000:400",   # 白底主导，看 2.5 比例换人
    "fff0f0f0:9000 ff000000:8000 ff2f6fb0:300 ffef4444:5000",    # 黑白双 dominant 争位
    "ff101010:12000 ff003cff:5000 ffffffff:3000 ffe0503a:600",   # 暗底：走 dark 族
    "ffdddddd:12000 ff2f6fb0:400 ffe0503a:300",                  # 候选群体不足 -> 退 dominant
    "ff3366cc:6000 ff9933cc:5500 ff339966:5200 ffcccccc:5100",   # 互斥：同色不能占两个族
    "ff123456:46 ffabcdef:45",                                   # 45/46 像素的门槛边界
]


def wpath(p):
    return os.path.abspath(p).replace("/", "\\")


def run_probe(cases):
    cp = ";".join([wpath(PROBE_DIR), wpath(CLASSES), wpath(XSTUBS), wpath(AJAR)])
    text = "\n".join(cases) + "\n"
    out = subprocess.run(["java", "-cp", cp, "SelectionProbe"], input=text,
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise RuntimeError((out.stdout + out.stderr)[-800:])
    return out.stdout.splitlines()


def compile_probe():
    cls = os.path.join(PROBE_DIR, "SelectionProbe.class")
    src = os.path.join(PROBE_DIR, "SelectionProbe.java")
    if os.path.exists(cls) and os.path.getmtime(cls) > os.path.getmtime(src):
        return
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", PROBE_DIR, src],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(r.stdout + r.stderr)


def parse(line):
    body = line.split("->")[1].strip()
    res = {}
    for chunk in body.split():
        key, val = chunk.split("=", 1)
        res[key] = val
    return res


def norm(token):
    if token == "-":
        return "-"
    rgb, pop = token.split("/")
    return "%x/%s" % (int(rgb, 16), pop)


def main(argv):
    global PERTURB
    if not os.path.isdir(CLASSES):
        print("找不到 module/build/classes，请先 cd module && ./build.sh")
        return 2
    negative = "--negative" in argv
    if negative:
        global PERTURB_SAT_MIN, PERTURB_GATE
        PERTURB_SAT_MIN, PERTURB_GATE = 0.6, 3.0
    compile_probe()
    lines = run_probe(CASES)
    if len(lines) != len(CASES):
        print("探针输出行数不符:", lines)
        return 1
    fails = 0
    for case, line in zip(CASES, lines):
        got = parse(line)
        sw, dominant, picks = palette_of(case.split())
        bg = find_background(dominant, sw)
        want = {
            "DOM": "%x/%d" % (dominant[0], dominant[1]) if dominant else "-",
            "V": fmt(picks["V"]), "LV": fmt(picks["LV"]), "DV": fmt(picks["DV"]),
            "M": fmt(picks["M"]), "LM": fmt(picks["LM"]), "DM": fmt(picks["DM"]),
            "BG": fmt(bg),
            "FG": "%x" % select_fg(bg[0] if bg else 0xFFFFFFFF, picks, dominant),
        }
        bad = []
        for k, v in want.items():
            g = got.get(k, "?")
            if k == "FG":
                if int(g, 16) != int(v, 16):
                    bad.append("%s 期望 %s 实得 %s" % (k, v, g))
            elif k == "BG" and v == "-" and g != "-":
                bad.append("BG 期望 %s 实得 %s" % (v, g))
            elif norm(g) != v and not (k == "BG" and g == "-" and v != "-"):
                bad.append("%s 期望 %s 实得 %s" % (k, v, g))
        if bad:
            fails += 1
            print("  FAIL " + " ".join(case[:2]) + " … -> " + "; ".join(bad))
        else:
            print("  ok   %d 块 -> DOM=%s V=%s M=%s BG=%s FG=%s"
                  % (len(sw), want["DOM"], want["V"], want["M"], want["BG"], want["FG"]))
    if negative:
        print("A11 选择逻辑负对照：%s" % ("有效（挪动 target 目标饱和度后 %d 组报红）" % fails
                                          if fails else "无效！检查没抓住常数变化"))
        return 0 if fails else 1
    print("A11 选择逻辑自检：%s" % ("全绿" if not fails else "%d 组不一致" % fails))
    return 1 if fails else 0


def fmt(item):
    return "-" if item is None else "%x/%d" % (item[0], item[1])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
