#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OS4 通知栏大图标（notify_row light）结果级验收。

输入：
  1) uiautomator dump（面板展开、notify_row 开启后的真实行）
  2) 模块 logcat 窗口（restyled light / col measure 见证）

断言（全部来自 HANDOFF-1103 §6.1 的"结果级"要求 + OS5 侧常量）：
  A1 大图标存在且边长 ≥42dp±1（LIGHT_ICON_DP=42，density 由 dump 推不出，参数传）；
  A2 大图标 x 落在内容列（left 24..40dp），不是 header 左上的原生 18dp 小图标槽；
  A3 标题首行与图标顶对齐（title.top ∈ icon.top ±14dp，OS5 的 8dp 抬升后的观感）；
  L1 log 至少 1 条 "restyled light ... bigIcon=1 geo=os5-column"。
用法：python verify_row_os4.py <dump.xml> <log.txt> <density>
"""
import re
import sys


def fail(msg):
    print("FAIL", msg)
    sys.exit(1)


def main():
    if len(sys.argv) != 4:
        fail("usage: verify_row_os4.py <dump.xml> <log> <density>")
    dump_path, log_path, density = sys.argv[1], sys.argv[2], float(sys.argv[3])
    x = open(dump_path, encoding="utf-8").read()
    log = open(log_path, encoding="utf-8", errors="replace").read()

    # 行区间（expandableNotificationRow bounds）
    rows = [tuple(map(int, m.groups()))
            for m in re.finditer(
                r'resource-id="com\.android\.systemui:id/expandableNotificationRow"'
                r'[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]
    if not rows:
        fail("dump 里没有任何 expandableNotificationRow")

    # 大图标候选：ImageView，边长 ≥ 40dp，方形（宽高差 ≤ 6dp）
    icons = []
    for m in re.finditer(
            r'class="android\.widget\.ImageView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        l, t, r, b = map(int, m.groups())
        w, h = (r - l) / density, (b - t) / density
        if w >= 40 and abs(w - h) <= 6:
            icons.append((l / density, t / density, w, h))

    big = [i for i in icons if 41 <= i[2] <= 43]
    if not big:
        fail("A1 没有找到 42dp 大图标（候选：%s）" % icons)
    for l, t, w, h in big:
        if not (24 <= l <= 40):
            fail("A2 大图标 left=%.0fdp 不在内容列 (24..40)" % l)
    print("ok   A1/A2 大图标 %d 枚，42dp、内容列（left=%sdps）"
          % (len(big), "/".join("%.0f" % i[0] for i in big)))

    # 标题（android:id/title）与大图标：同排（top 对齐 ±14dp）且在图标右侧
    # （OS5 布局 = 图标内容列左、文字列右；title.left ≈ icon.right + 8dp）
    titles = [tuple(map(int, m.groups())) for m in re.finditer(
        r'resource-id="android:id/title"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]
    aligned = 0
    for tl, tt, tr, tb in titles:
        ttop, tleft = tt / density, tl / density
        for il, it, iw, ih in big:
            if abs(it - ttop) <= 14 and tleft > il + iw - 2:
                aligned += 1
                break
    if aligned == 0:
        fail("A3 没有标题与大图标同排（titles=%s big=%s）" % (titles, big))
    print("ok   A3 标题与大图标同排 %d/%d" % (aligned, len(titles)))

    n = len(re.findall(r"restyled light .*bigIcon=1 geo=os5-column", log))
    if n < 1:
        fail("L1 log 里没有 restyled light bigIcon=1 geo=os5-column")
    print("ok   L1 restyled light(bigIcon=1 geo=os5-column) x%d" % n)
    print("verify_row_os4: 全绿")


if __name__ == "__main__":
    main()
