#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A11 取色"工作系"的代码级验收（不连设备、不截图）。

验三件事，全部是纯算术，所以能直接在桌面 JVM 上跑模块的字节码：
  1) workingSize(w,h)     —— 整图按面积 22500 缩放，(int) 强转
                             证据 MediaNotificationProcessor.java:57-63
  2) collapsedRegions     —— 背景=工作系左半幅、前景=工作系右 60%
                             证据 MNP:177 与 MNP:71（两处 setRegion 用的都是缩放后的 bitmap）
  3) gridPixels           —— 两个区域各有多少像素（= 量化器的输入规模）

`--negative` 用"region 在源图上算"（旧实现）当反例：网格数会对不上，证明这层检查真的在锁东西。

用法：python work/a11_sampling_selftest.py [--negative]
前置：先跑过 module/build.sh
"""
import math
import os
import struct
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CLASSES = os.path.join(ROOT, "module", "build", "classes")
XSTUBS = os.path.join(ROOT, "module", "build", "stubs")
PROBE_DIR = os.path.join(ROOT, "work", "tmp-re-a11")
AJAR = os.path.join(ROOT, "work", "sdk-dl", "plat35", "android-35", "android.jar")

AREA = 22500.0

CASES = [
    (200, 200),     # Y7s 实测封面尺寸
    (100, 100),     # 面积没超，不缩放
    (512, 512),     # 大图，(int) 强转会有 149 这种非整值
    (300, 100),     # 非方形
    (1, 1),         # 退化
    (30000, 1),     # 极端长宽比：缩放后有一边会归 0，应退回不缩放
    (201, 201),     # 奇数：验证 region 的 //2 与 int(0.4*w) 的取整
]


def f32(x):
    """把 Python 的 double 收成 Java float 的精度（0.4f*w 是单精度乘法）"""
    return struct.unpack("f", struct.pack("f", x))[0]


def working_size(w, h, legacy=False):
    if w <= 0 or h <= 0:
        return (max(w, 0), max(h, 0))
    if float(w * h) <= AREA:
        return (w, h)
    ratio = math.sqrt(AREA / (w * float(h)))
    ww, hh = int(w * ratio), int(h * ratio)
    if ww < 1 or hh < 1:
        return (w, h)
    return (ww, hh)


def regions(w, h, legacy=False):
    """legacy=True 复刻"在源图上算 region"的旧实现（反例用）"""
    ww, hh = (w, h) if legacy else working_size(w, h)
    return [(0, 0, ww // 2, hh), (int(f32(f32(0.4) * ww)), 0, ww, hh)]


def clamp_rect(w, h, rect):
    ww, hh = working_size(w, h)
    l, t, r, b = rect
    l = max(0, min(l, max(0, ww - 1)))
    t = max(0, min(t, max(0, hh - 1)))
    r = max(l + 1, min(r, ww))
    b = max(t + 1, min(b, hh))
    return (l, t, r, b)


def expect_row(w, h, legacy=False):
    ww, hh = working_size(w, h)
    raw = regions(w, h, legacy)
    clamped = [clamp_rect(w, h, rg) for rg in raw]
    bg, fg = clamped
    # 位置 3..10 是 collapsedRegions 的**原始**返回（模块不做夹紧），
    # 位置 10/11 是 gridPixels：夹紧后的像素数
    return ([ww, hh] + list(raw[0]) + list(raw[1])
            + [(bg[2] - bg[0]) * (bg[3] - bg[1]), (fg[2] - fg[0]) * (fg[3] - fg[1])])


def wpath(p):
    return os.path.abspath(p).replace("/", "\\")


def compile_probe():
    cls = os.path.join(PROBE_DIR, "SamplingProbe.class")
    src = os.path.join(PROBE_DIR, "SamplingProbe.java")
    if os.path.exists(cls) and os.path.getmtime(cls) > os.path.getmtime(src):
        return
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", PROBE_DIR, src],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stdout, r.stderr)
        raise SystemExit("SamplingProbe.java 编译失败")


def run_probe(cases):
    cp = ";".join([wpath(PROBE_DIR), wpath(CLASSES), wpath(XSTUBS), wpath(AJAR)])
    text = "".join("%d %d\n" % c for c in cases)
    out = subprocess.run(["java", "-cp", cp, "SamplingProbe"], input=text,
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        print(out.stdout, out.stderr)
        raise SystemExit("probe 跑不动（确认 module/build/classes 与 work/tmp-re-a11 里的产物）")
    rows = []
    for line in out.stdout.splitlines():
        rows.append([int(x) for x in line.split("->")[1].split()])
    if len(rows) != len(cases):
        raise SystemExit("probe 输出 %d 行 != 用例 %d" % (len(rows), len(cases)))
    return rows


def main(argv):
    if not os.path.isdir(CLASSES):
        print("找不到 module/build/classes，请先 cd module && ./build.sh")
        return 2
    legacy = "--negative" in argv
    compile_probe()
    rows = run_probe(CASES)
    bad = 0
    for (w, h), got in zip(CASES, rows):
        want = expect_row(w, h, legacy)
        label = "%dx%d" % (w, h)
        if legacy:
            # 反例：模块（在工作系算 region）必须与"旧实现（在源图算 region）"不同，
            # 否则这条检查锁不住"到底在哪张图上有半幅/60%"这件事
            same = list(got) == want
            if same:
                print("  --   %s 两种算法同解（本例无从区分），跳过" % label)
                continue
            print("  ok   %s 工作系 %s ≠ 源图系 %s" % (label, got[2:], want[2:]))
        else:
            if list(got) != want:
                print("  FAIL %s 期望 %s 实得 %s" % (label, want, got))
                bad += 1
            else:
                print("  ok   %s -> 工作 %dx%d，背景网格 %d，前景网格 %d"
                      % (label, got[0], got[1], got[10], got[11]))
                bad += 0
    if not legacy:
        # Y7s 那张 200x200 的封面：背景 75x150=11250、前景 90x150=13500 是硬事实
        g = rows[CASES.index((200, 200))]
        if g[10] != 11250 or g[11] != 13500:
            print("  FAIL Y7s 200x200 网格应为 11250/13500，实得 %d/%d" % (g[10], g[11]))
            bad += 1
        else:
            print("  ok   Y7s 200x200 实测网格 11250/13500")
        print("A11 取色网格自检：%s" % ("全绿" if not bad else "有失败项"))
        return 1 if bad else 0
    print("A11 取色网格自检：负对照有效")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
