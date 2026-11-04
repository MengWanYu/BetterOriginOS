#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A11 取色链的交叉验证：把模块里真实的 A11Palette（Java）跑出来，
和独立参考实现（work/a11_math_selftest.py，基于 colorsys / 手写 LAB）逐值比对。

为什么需要它：移植的取色代码只能在对讲机上肉眼看到，而肉眼不算验收（用户明令禁止截图）。
这个检查在**不连设备**的情况下就能证明：
  1. 模块的 Java 实现与独立实现一致（容差只允许浮点位宽造成的舍入差）；
  2. 之前真实存在过的 bug（HSL 饱和度分母写成 2-delta）会被立刻抓出来。

用法：python work/a11_crosscheck.py [样本数]
前置：先跑过 module/build.sh（需要 module/build/classes 里有 A11Palette.class）。
"""
import io
import os
import random
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CLASSES = os.path.join(ROOT, "module", "build", "classes")
AJAR = os.path.join(ROOT, "work", "sdk-dl", "plat35", "android-35", "android.jar")
PROBE_DIR = os.path.join(ROOT, "work", "tmp-re-a11")
PROBE_OUT = os.path.join(PROBE_DIR, "out")
PAIRS = os.path.join(PROBE_DIR, "pairs.txt")
JAVA_OUT = os.path.join(PROBE_DIR, "java_out.txt")
TOLERANCE = 4          # 每通道累计允许的绝对差（float32 vs float64 的舍入量级）


def msvc(s):
    return s.replace("/", os.sep).replace("\\", os.sep)


def ensure_probe():
    os.makedirs(PROBE_OUT, exist_ok=True)
    probe = os.path.join(PROBE_OUT, "A11Probe.class")
    if os.path.exists(probe):
        return
    cp = "%s;%s" % (msvc(CLASSES), msvc(AJAR))
    subprocess.check_call(["javac", "-nowarn", "-encoding", "UTF-8", "-cp", cp,
                           "-d", msvc(PROBE_OUT), msvc(os.path.join(PROBE_DIR, "A11Probe.java"))])


def run_java(pairs):
    with io.open(PAIRS, "w", encoding="utf-8", newline="\n") as f:
        for bg, fg in pairs:
            f.write("%08x %08x\n" % (bg, fg))
    cp = "%s;%s;%s" % (msvc(PROBE_OUT), msvc(CLASSES), msvc(AJAR))
    with io.open(PAIRS, "rb") as fin:
        out = subprocess.check_output(
            ["java", "-cp", cp, "A11Probe"], stdin=fin).decode("ascii")
    return [l.split() for l in out.splitlines() if l.strip()]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    if not os.path.isdir(CLASSES):
        print("先跑 module/build.sh（缺 %s）" % CLASSES)
        return 2
    sys.path.insert(0, os.path.join(ROOT, "work"))
    import a11_math_selftest as ref
    ensure_probe()

    rnd = random.Random(1102)
    pairs = [((0xFF << 24) | rnd.randrange(1 << 24), (0xFF << 24) | rnd.randrange(1 << 24))
             for _ in range(n)]
    # 固定样本：极端值与已知会暴露分支差异的颜色
    pairs += [(0xFF000000, 0xFFFFFFFF), (0xFFFFFFFF, 0xFF000000), (0xFF404040, 0xFF33404D),
              (0xFF4B70E1, 0xFFEE0580), (0xFFDA17D9, 0xFF4034BB)]

    got = run_java(pairs)
    if len(got) != len(pairs):
        print("FAIL 探针输出行数 %d != 输入 %d" % (len(got), len(pairs)))
        return 1

    def dist(a, b):
        return sum(abs(((a >> sh) & 0xFF) - ((b >> sh) & 0xFF)) for sh in (16, 8, 0))

    worst = {"primary": 0, "secondary": 0, "card": 0}
    over = []
    for (bg, fg), row in zip(pairs, got):
        pr, sec, _ = ref.colorized_text_colors(bg, fg)
        card = ref.compute_card_background(fg)
        jpr, jsec, jcard = int(row[1], 16), int(row[2], 16), int(row[3], 16)
        for key, a, b in (("primary", pr, jpr), ("secondary", sec, jsec), ("card", card, jcard)):
            d = dist(a, b)
            worst[key] = max(worst[key], d)
            if d > TOLERANCE:
                over.append((bg, fg, key, "%08x" % a, "%08x" % b, d))

    print("样本 %d 组（含 5 组极端/边界固定样本）" % len(pairs))
    print("  最大通道累计偏差：primary=%d secondary=%d card=%d（容差 %d）"
          % (worst["primary"], worst["secondary"], worst["card"], TOLERANCE))
    if over:
        for o in over[:8]:
            print("  FAIL bg=%08x fg=%08x %s 模块=%s 参考=%s 差=%d" % o)
        print("交叉验证失败：超出舍入容差 %d 处" % len(over))
        return 1
    print("  ok   模块 Java 实现与独立参考实现一致（仅浮点位宽造成的 <=%d 差异）"
          % max(worst.values()))
    print("A11 取色交叉验证：全绿")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
