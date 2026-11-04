#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Os5Mirror.g2Path 的代码级验收（不连设备、不截图）。

原理：用"记录型 android.graphics.Path"桩（work/tmp-re-g2/stubs/android/graphics/Path.java）
挤掉真 Path，让 JVM 直接执行模块编译产物 module/build/classes 里的 **Os5Mirror.g2Path 本身**
（反射取包私有方法），再把完整指令序列与独立参考实现逐条比对。

为什么要单独验它：G2 的三次贝塞尔控制点是从 A15 反编译结果手抄进 Java 的
（两套常数 1.1r/1.3r 与 0.4r/0.61r、负的 c0 偏移 -0.008r/-0.013r、25° 起 40° 的四段弧、
2.2r 回落阈值）。这些数字一度被抄成 1.3r / 0.55r / +0.01r / 2.6r，只有逐指令比对能抓出来。
抄错常数在真机上只表现为"圆角看着不太对"，而本项目禁止用截图判断，所以必须用数值锁住。

用法：python work/g2_path_selftest.py
前置：先跑过 module/build.sh（需要 module/build/classes 与 module/build/stubs）
"""
import math
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
STUB = os.path.join(ROOT, "work", "tmp-re-g2", "out")
CLASSES = os.path.join(ROOT, "module", "build", "classes")
XSTUBS = os.path.join(ROOT, "module", "build", "stubs")
AJAR = os.path.join(ROOT, "work", "sdk-dl", "plat35", "android-35", "android.jar")

# vivo G2CornerUtil 的常量（A15 与仓库内 com.vivo.common.utils 版本一致）
A25 = 0.4363323129985824          # 25 度
PERTURB = 1.0                       # --negative 时故意让参考实现偏移，验证检查会报红
COS25, SIN25 = math.cos(A25), math.sin(A25)


def q(v):
    return "%.4f" % v


def normal_ref(l, t, r, b, radius, lt, rt, rb, lb):
    """vivo getNormalCornerPath 的独立重抄（G2 画不出来时的回落分支）。"""
    w, h = r - l, b - t
    if w < 0 or h < 0:
        return []
    if radius * 2 > w or radius * 2 > h:
        radius = min(w, h) / 2.0
    d = radius * 2.0
    top_start = t + radius
    right_stop = r - radius
    bottom_stop = b - radius
    o = ["M %s %s" % (q(l), q(top_start))]
    if lt:
        o.append("A %s %s %s %s %s %s" % (q(l), q(t), q(l + d), q(t + d), q(180.0), q(90.0)))
    else:
        o += ["L %s %s" % (q(l), q(t)), "L %s %s" % (q(l + radius), q(t))]
    o.append("L %s %s" % (q(right_stop), q(t)))
    if rt:
        o.append("A %s %s %s %s %s %s" % (q(r - d), q(t), q(r), q(t + d), q(270.0), q(90.0)))
    else:
        o += ["L %s %s" % (q(r), q(t)), "L %s %s" % (q(r), q(top_start))]
    o.append("L %s %s" % (q(r), q(bottom_stop)))
    if rb:
        o.append("A %s %s %s %s %s %s" % (q(r - d), q(b - d), q(r), q(b), q(0.0), q(90.0)))
    else:
        o += ["L %s %s" % (q(r), q(b)), "L %s %s" % (q(right_stop), q(b))]
    o.append("L %s %s" % (q(l + radius), q(b)))
    if lb:
        o.append("A %s %s %s %s %s %s" % (q(l), q(b - d), q(l + d), q(b), q(90.0), q(90.0)))
    else:
        o += ["L %s %s" % (q(l), q(b)), "L %s %s" % (q(l), q(bottom_stop))]
    o += ["L %s %s" % (q(l), q(top_start)), "Z"]
    return o


def reference(l, t, r, b, radius, lt, rt, rb, lb):
    """独立重算期望指令序列（不 import 模块代码）。

    逐条抄自 work/tmp-re-os5toos3/com.originui.core.utils.G2CornerUtil.java:148-264，
    关键三点：退普通圆角的门限是 2.2r（不是 2.6r）；2.2~2.6r 之间用 (-0.008r, 0.4r, 1.1r)，
    超过 2.6r 才用 (-0.013r, 0.61r, 1.3r)；第一个控制点偏移是负值。
    """
    w, h = r - l, b - t
    if w < 0 or h < 0:
        return []
    if radius <= 0:
        return ["Q %s %s %s %s" % (q(l), q(t), q(r), q(b))]
    if radius * 2.2 > w or radius * 2.2 > h:
        return normal_ref(l, t, r, b, radius, lt, rt, rb, lb)
    a = radius - radius * COS25             # f15
    b25 = radius - radius * SIN25           # f16
    if min(w, h) > 2.6 * radius:
        c_neg, c_mid, n_off = -0.013 * radius, 0.61 * radius, 1.3 * radius
    else:
        c_neg, c_mid, n_off = -0.008 * radius, 0.4 * radius, 1.1 * radius
    # 负对照：常数偏移 5% 且把 c_neg 的符号翻过来 —— 抄错符号（vivo 是 -0.008/-0.013）
    # 是最容易犯、且只有逐指令比对才抓得住的错
    c_mid *= PERTURB
    n_off *= PERTURB
    if PERTURB != 1.0:
        c_neg = -c_neg
    d = 2.0 * radius
    top_start = t + n_off
    top_stop = r - n_off
    right_stop = b - n_off
    bottom_start = l + n_off
    o = ["M %s %s" % (q(l), q(top_start))]
    if lt:
        o += ["C %s %s %s %s %s %s" % (q(l), q(top_start - c_mid), q(l + a - c_neg), q(t + b25),
                                       q(l + a), q(t + b25)),
              "A %s %s %s %s %s %s" % (q(l), q(t), q(l + d), q(t + d), q(205.0), q(40.0)),
              "C %s %s %s %s %s %s" % (q(l + b25), q(t + a - c_neg), q(bottom_start - c_mid),
                                       q(t), q(bottom_start), q(t))]
    else:
        o += ["L %s %s" % (q(l), q(t)), "L %s %s" % (q(bottom_start), q(t))]
    o.append("L %s %s" % (q(top_stop), q(t)))
    if rt:
        o += ["C %s %s %s %s %s %s" % (q(top_stop + c_mid), q(t), q(r - b25), q(t + a - c_neg),
                                       q(r - b25), q(t + a)),
              "A %s %s %s %s %s %s" % (q(r - d), q(t), q(r), q(t + d), q(295.0), q(40.0)),
              "C %s %s %s %s %s %s" % (q(r - a + c_neg), q(t + b25), q(r), q(top_start - c_mid),
                                       q(r), q(top_start))]
    else:
        o += ["L %s %s" % (q(r), q(t)), "L %s %s" % (q(r), q(top_start))]
    o.append("L %s %s" % (q(r), q(right_stop)))
    if rb:
        o += ["C %s %s %s %s %s %s" % (q(r), q(right_stop + c_mid), q(r - a + c_neg),
                                       q(b - b25), q(r - a), q(b - b25)),
              "A %s %s %s %s %s %s" % (q(r - d), q(b - d), q(r), q(b), q(25.0), q(40.0)),
              "C %s %s %s %s %s %s" % (q(r - b25), q(b - a + c_neg), q(top_stop + c_mid), q(b),
                                       q(top_stop), q(b))]
    else:
        o += ["L %s %s" % (q(r), q(b)), "L %s %s" % (q(top_stop), q(b))]
    o.append("L %s %s" % (q(bottom_start), q(b)))
    if lb:
        o += ["C %s %s %s %s %s %s" % (q(bottom_start - c_mid), q(b), q(l + b25),
                                       q(b - a + c_neg), q(l + b25), q(b - a)),
              "A %s %s %s %s %s %s" % (q(l), q(b - d), q(l + d), q(b), q(115.0), q(40.0)),
              "C %s %s %s %s %s %s" % (q(l + a - c_neg), q(b - b25), q(l), q(right_stop + c_mid),
                                       q(l), q(right_stop))]
    else:
        o += ["L %s %s" % (q(l), q(b)), "L %s %s" % (q(l), q(right_stop))]
    o += ["L %s %s" % (q(l), q(top_start)), "Z"]
    return o


def module_ops(cases):
    def w(p):
        return os.path.abspath(p).replace("/", "\\")
    cp = ";".join([w(STUB), w(CLASSES), w(XSTUBS), w(AJAR)])
    text = "".join("%s %s %s %s %s %d %d %d %d\n" % c for c in cases)
    env = dict(os.environ, MSYS2_ARG_CONV_EXCL="*", MSYS_NO_PATHCONV="1")
    out = subprocess.run(["java", "-cp", cp, "G2Probe"], input=text,
                         capture_output=True, text=True, env=env)
    if out.returncode != 0:
        raise RuntimeError((out.stderr or "")[:500])
    lines = [l for l in out.stdout.splitlines() if l.strip()]
    rescale = [l for l in lines if l.startswith("RESCALE")]
    body = lines[len(rescale):]          # 探针先打 RESCALE 行，再按输入顺序逐用例打指令
    if len(body) != len(cases):
        raise RuntimeError("探针输出 %d 行 != 用例 %d 行" % (len(body), len(cases)))
    return [l.split("|") for l in body], rescale


def main():
    if not os.path.isdir(CLASSES):
        print("先跑 module/build.sh（缺 %s）" % CLASSES)
        return 2
    negative = "--negative" in sys.argv[1:]
    cases = [
        (0, 0, 1032, 232, 54.0, 1, 1, 1, 1),    # 独立通知（18dp@3.0）四角全圆
        (0, 0, 1032, 232, 54.0, 1, 1, 0, 0),    # 组头：只圆顶边
        (0, 0, 1032, 232, 54.0, 0, 0, 1, 1),    # 组内末条：只圆底边
        (0, 0, 1032, 232, 54.0, 0, 0, 0, 0),    # 全方
        (0, 0, 1032, 232, 36.0, 1, 1, 1, 1),    # 12dp sharp 档
        (0, 0, 1032, 232, 72.0, 1, 1, 1, 1),    # 24dp smooth/mellow 档
        (0, 0, 100, 60, 54.0, 1, 1, 1, 1),      # 2.2r 超界 -> 走 vivo 的普通圆角分支
        (0, 0, 1032, 133, 54.0, 1, 1, 1, 1),    # 2.2r~2.6r 带：仍画 G2，但用 (-0.008,0.4,1.1) 那套
        (0, 0, 1032, 232, 0.0, 1, 1, 1, 1),     # 半径 0 -> 矩形
        (48, 71, 1080, 300, 45.0, 1, 1, 1, 1),  # 非零原点
        (0, 0, 60, 1032, 54.0, 1, 0, 1, 0),     # 瘦高 + 对角圆角
    ]
    global PERTURB
    if negative:
        PERTURB = 1.05
    got, rescale = module_ops(cases)
    # rescaleRadii：半径=系数*基线，换基线时按比例缩放且不动系数
    expect_rescale = [
        ("51.0 54.0 51.0 0.0", "54.000 0.000 54.000"),    # 17dp 基线纠正到 18dp，底部系数 0 保持 0
        ("51.0 54.0 51.0 51.0", "54.000 54.000 54.000"),  # 上下圆角同步放大
        ("0.0 54.0 40.0 40.0", "40.000 40.000 0.000"),    # 读不到基线 -> 不动、不回写
        ("54.0 54.0 54.0 27.0", "54.000 27.000 0.000"),   # 基线已达标 -> 不动
        ("54.0 72.0 0.0 54.0", "0.000 72.000 72.000"),    # 18dp->24dp（sharp/round 档切换）
    ]
    fails = []
    for args, want in expect_rescale:
        line = "RESCALE " + args + " -> " + want
        if line not in rescale:
            fails.append("rescaleRadii(%s) 期望 %s，模块实际 %s"
                         % (args, want,
                            next((l.split("-> ")[1] for l in rescale
                                  if l.startswith("RESCALE " + args)), "无输出")))
    print("rescaleRadii 校验 %d 组（其中 %d 组与期望一致）"
          % (len(expect_rescale), sum(1 for a, w in expect_rescale
                                      if ("RESCALE " + a + " -> " + w) in rescale)))
    for c, ops in zip(cases, got):
        want = reference(c[0], c[1], c[2], c[3], c[4], *[bool(x) for x in c[5:]])
        if len(ops) != len(want):
            fails.append("case r=%.1f flags=%d%d%d%d: 指令数 模块=%d 参考=%d"
                         % (c[4], c[5], c[6], c[7], c[8], len(ops), len(want)))
            continue
        for i, (a, b) in enumerate(zip(want, ops)):
            ta, tb = a.split(" ")[0], b.split(" ")[0]
            if ta != tb:
                fails.append("case r=%.1f 第%d条 指令类型 模块=%s 参考=%s" % (c[4], i, tb, ta))
                continue
            va = [float(x) for x in a.split(" ")[1:] if x]
            vb = [float(x) for x in b.split(" ")[1:] if x]
            worst = max((abs(x - y) for x, y in zip(va, vb)), default=0.0)
            if worst > 0.02:      # 模块是 float32，参考是 float64，只容许舍入级误差
                fails.append("case r=%.1f flags=%d%d%d%d: 第%d条数值差 %.4f 模块=%s 参考=%s"
                             % (c[4], c[5], c[6], c[7], c[8], i, worst, b, a))
    print("用例 %d 组，逐条比对完整指令序列（数值容差 0.02px）" % len(cases))
    if negative:
        if fails:
            print("  ok   负对照：把参考常数偏移 5%% 并翻转 c_neg 符号后，检查报红 %d 处" % len(fails))
            print("Os5Mirror.g2Path 负对照：有效")
            return 0
        print("  FAIL 负对照没被抓到：这条检查形同虚设")
        return 1
    if fails:
        for x in fails:
            print("  FAIL " + x)
        return 1
    print("  ok   两套常数 (0.4r/1.1r, -0.008r) 与 (0.61r/1.3r, -0.013r) 逐指令一致，四段弧 205/295/25/115 各扫 40 度")
    print("  ok   逐角开关正确（组头只圆顶、组尾只圆底、全方退化）")
    print("  ok   2.2r 以内走 vivo 的普通圆角分支；2.2~2.6r 仍画 G2；半径 0 退化为矩形")
    print("  ok   非零原点平移正确")
    print("Os5Mirror.g2Path 与独立参考实现：逐指令一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
