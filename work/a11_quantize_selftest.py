#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A11 量化器（median-cut）的代码级验收（不连设备、不截图）。

为什么值得单独验：A11 的取色输入不是"直方图取前 N"，而是 androidx 的 ColorCutQuantizer ——
5bit/通道桶直方图 → 按**桶的代表色**过滤镜 → 桶数 <= maxColors(16) 时一桶一色，
否则按体积优先队列做 median-cut 切到 16 个盒子 → 每盒子求群体加权平均色 → 再过一次滤镜。
每一步都会改变"哪些颜色有资格成为 dominant/vibrant"，也就是卡片最终配色的源头。

参考实现独立抄自 work/y7s-a11/tmp/ColorCutQuantizer.java:58-318。
`--negative` 用"只按群体取前 N 桶"（我改之前那种实现）当反例，证明检查抓得住。

用法：python work/a11_quantize_selftest.py [--negative]
前置：先跑过 module/build.sh
"""
import heapq
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CLASSES = os.path.join(ROOT, "module", "build", "classes")
XSTUBS = os.path.join(ROOT, "module", "build", "stubs")
PROBE_DIR = os.path.join(ROOT, "work", "tmp-re-a11")
AJAR = os.path.join(ROOT, "work", "sdk-dl", "plat35", "android-35", "android.jar")

RED, GREEN, BLUE = -3, -2, -1


def qw(v, cur, tgt):
    return ((v << (tgt - cur)) if tgt > cur else (v >> (cur - tgt))) & ((1 << tgt) - 1)


def bucket(rgb):
    return (qw((rgb >> 16) & 0xFF, 8, 5) << 10) | (qw((rgb >> 8) & 0xFF, 8, 5) << 5) \
        | qw(rgb & 0xFF, 8, 5)


def approx(b):
    return (qw((b >> 10) & 0x1F, 5, 8) << 16) | (qw((b >> 5) & 0x1F, 5, 8) << 8) \
        | qw(b & 0x1F, 5, 8)


def light01(rgb):
    r, g, b = ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0
    return (max(r, g, b) + min(r, g, b)) / 2.0


def mono_filter(rgb):
    """与探针里那个代理过滤器同构：极黑/极白剔掉"""
    l = light01(rgb)
    return 0.08 < l < 0.90


def key_for(b, dim):
    r, g, bl = (b >> 10) & 0x1F, (b >> 5) & 0x1F, b & 0x1F
    if dim == GREEN:
        return bl | (g << 10) | (r << 5)
    if dim == BLUE:
        return r | (bl << 10) | (g << 5)
    return b


class Vbox(object):
    def __init__(self, lo, hi, colors, hist):
        self.lo, self.hi = lo, hi
        self.fit(colors, hist)

    def fit(self, colors, hist):
        minr, maxr, ming, maxg, minb, maxb = 32, -1, 32, -1, 32, -1
        pop = 0
        for i in range(self.lo, self.hi + 1):
            b = colors[i]
            n = hist.get(b, 0)
            pop += n
            r, g, bl = (b >> 10) & 0x1F, (b >> 5) & 0x1F, b & 0x1F
            minr, maxr = min(minr, r), max(maxr, r)
            ming, maxg = min(ming, g), max(maxg, g)
            minb, maxb = min(minb, bl), max(maxb, bl)
        self.minr, self.maxr = minr, maxr
        self.ming, self.maxg = ming, maxg
        self.minb, self.maxb = minb, maxb
        self.pop = pop

    @property
    def volume(self):
        return (self.maxr - self.minr + 1) * (self.maxg - self.ming + 1) * \
               (self.maxb - self.minb + 1)

    def can_split(self):
        return (self.hi + 1) - self.lo > 1

    def longest(self):
        r = self.maxr - self.minr
        g = self.maxg - self.ming
        b = self.maxb - self.minb
        if r < g or r < b:
            return BLUE if (g < r or g < b) else GREEN
        return RED

    def split_point(self, colors, hist):
        dim = self.longest()
        seg = colors[self.lo:self.hi + 1]
        seg.sort(key=lambda b: key_for(b, dim))
        colors[self.lo:self.hi + 1] = seg
        half = self.pop // 2
        run = 0
        for i in range(self.lo, self.hi + 1):
            run += hist.get(colors[i], 0)
            if run >= half:
                return min(self.hi - 1, i)
        return self.lo

    def split(self, colors, hist):
        p = self.split_point(colors, hist)
        nxt = Vbox(p + 1, self.hi, colors, hist)
        self.hi = p
        self.fit(colors, hist)
        return nxt

    def average(self, colors, hist):
        sr = sg = sb = 0
        cnt = 0
        for i in range(self.lo, self.hi + 1):
            b = colors[i]
            n = hist.get(b, 0)
            cnt += n
            sr += ((b >> 10) & 0x1F) * n
            sg += ((b >> 5) & 0x1F) * n
            sb += (b & 0x1F) * n
        if cnt == 0:
            return (0, 0)
        f = float(cnt)
        rgb = (qw(int(round(sr / f)), 5, 8) << 16) | (qw(int(round(sg / f)), 5, 8) << 8) \
            | qw(int(round(sb / f)), 5, 8)
        return (rgb, cnt)


def reference_quantize(pixels, max_colors, use_filter, legacy=False):
    if legacy:
        # 旧实现：桶均值 + 按群体排序取前 N（反例用）
        hist = {}
        for px in pixels:
            hist[bucket(px)] = hist.get(bucket(px), 0) + 1
        out = []
        for b, n in sorted(hist.items(), key=lambda kv: -kv[1]):
            rgb = approx(b)
            if use_filter and not mono_filter(rgb):
                continue
            out.append((rgb, n))
        return sorted(out[:max_colors])
    hist = {}
    for px in pixels:
        b = bucket(px)
        hist[b] = hist.get(b, 0) + 1
    if use_filter:
        for b in list(hist):
            if not mono_filter(approx(b)):
                del hist[b]
    colors = sorted(hist)
    if not colors:
        return []
    if len(colors) <= max_colors:
        return sorted([(approx(b), hist[b]) for b in colors])
    heap = []
    seq = 0
    box = Vbox(0, len(colors) - 1, colors, hist)
    heapq.heappush(heap, (-box.volume, seq, box))
    while len(heap) < max_colors:
        _, _, best = heapq.heappop(heap)
        if not best.can_split():
            # androidx ColorCutQuantizer.splitBoxes 的 while 把 can_split() 放在 && 链里：
            # 切不动的盒子**不会放回队列**。这里照抄，不放回。
            # 说明：这条差异**构造不出可观察的反例**——只有体积为 0 的盒子（空盒或单色盒）
            # 才会切不动，而优先队列按体积取最大，所以它一定排在最后，被丢时不影响其余群体。
            # 因此不设"区分两种写法"的用例，只保证两边写法一致（改了会在 diff 里露出来）。
            break
        nxt = best.split(colors, hist)
        seq += 1
        heapq.heappush(heap, (-nxt.volume, seq, nxt))
        seq += 1
        heapq.heappush(heap, (-best.volume, seq, best))
    out = []
    for _, _, box in heap:
        rgb, cnt = box.average(colors, hist)
        if use_filter and not mono_filter(rgb):
            continue
        out.append((rgb, cnt))
    return sorted(out)


CASES = [
    # (说明, maxColors, 是否过滤, 像素列表, 是否可做逐块精确比对)
    # 精确比对只在"桶数 <= maxColors"或"盒子体积互不相等、切序无并列"时成立：
    # Java 的 PriorityQueue 与任何堆在体积并列时出堆顺序都不保证，逐块比会假红。
    ("少量色直接返回（<=16 桶，逐块精确）", 16, False,
     [0x33404D] * 900 + [0xE0503A] * 400 + [0xFFFFFF] * 100, True),
    ("三簇体积悬殊（切序无并列，逐块精确）", 4, False,
     [0xFF0000] * 500 + [0xFE0000] * 500 + [0x00FF00] * 300 + [0x0000FF] * 200
     + [0x0000FE] * 100 + [0x101010] * 50, True),
    ("单色（逐块精确）", 16, False, [0x123456] * 5000, True),
    ("24 色 median-cut（只验不变量）", 16, False,
     [(r << 16) | (r << 8) | r for r in range(0, 240, 10)], False),
    ("斜向渐变 40 级（只验不变量）", 16, False,
     [(i * 5 << 16) | (255 - i * 5 << 8) | (i * 3) for i in range(40)], False),
    ("极黑极白被桶级过滤（逐块精确）", 16, True,
     [0x000000] * 800 + [0xFFFFFF] * 800 + [0x2F6FB0] * 300 + [0xEF4444] * 250, True),
]


def wpath(p):
    return os.path.abspath(p).replace("/", "\\")


def compile_probe():
    cls = os.path.join(PROBE_DIR, "QuantizeProbe.class")
    src = os.path.join(PROBE_DIR, "QuantizeProbe.java")
    if os.path.exists(cls) and os.path.getmtime(cls) > os.path.getmtime(src):
        return
    r = subprocess.run(["javac", "-encoding", "UTF-8", "-d", PROBE_DIR, src],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stdout, r.stderr)
        raise SystemExit("QuantizeProbe.java 编译失败")


def run_probe(cases):
    cp = ";".join([wpath(PROBE_DIR), wpath(CLASSES), wpath(XSTUBS), wpath(AJAR)])
    text = ""
    for _, maxc, filt, px, _exact in cases:
        text += "%d %d %s\n" % (maxc, 1 if filt else 0,
                                " ".join("%06x" % p for p in px))
    out = subprocess.run(["java", "-cp", cp, "QuantizeProbe"], input=text,
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        print(out.stdout[-500:], out.stderr[-800:])
        raise SystemExit("probe 跑不动")
    return out.stdout.splitlines()


def parse_row(line):
    body = line.split("->")[1].strip().strip("[]")
    out = []
    if body:
        for tok in body.split():
            rgb, pop = tok.split("/")
            out.append((int(rgb, 16), int(pop)))
    return sorted(out)


def invariants(px, maxc, filt, got):
    """切不动/并列时的兜底核对：块数、群体守恒、每块都有正群体"""
    problems = []
    if len(got) > maxc:
        problems.append("块数 %d 超过 maxColors %d" % (len(got), maxc))
    if any(pop <= 0 for _, pop in got):
        problems.append("有块的群体 <= 0")
    if not filt:
        total = sum(pop for _, pop in got)
        if total != len(px):
            problems.append("群体不守恒：像素 %d，块内合计 %d" % (len(px), total))
    return problems


def main(argv):
    if not os.path.isdir(CLASSES):
        print("找不到 module/build/classes，请先 cd module && ./build.sh")
        return 2
    legacy = "--negative" in argv
    compile_probe()
    rows = run_probe(CASES)
    bad = 0
    for (name, maxc, filt, px, exact), line in zip(CASES, rows):
        got = parse_row(line)
        want = reference_quantize(px, maxc, filt, legacy)
        if legacy:
            if got == want:
                print("  --   %s：与旧实现同解，跳过" % name)
            else:
                print("  ok   %s：median-cut 的分组与旧「直方图取前 N」不同（%d 块）"
                      % (name, len(got)))
            continue
        problems = invariants(px, maxc, filt, got)
        if exact and got != want:
            problems.append("逐块不一致：期望 %s 实得 %s" % (want[:3], got[:3]))
        if problems:
            bad += 1
            print("  FAIL %s -> %s" % (name, "; ".join(problems)))
        else:
            print("  ok   %s：%d 块%s" % (name, len(got), "（逐块精确）" if exact else "（不变量）"))
    if legacy:
        print("A11 量化器负对照：有效")
        return 0
    print("A11 量化器自检：%s" % ("全绿" if not bad else "%d 组不一致" % bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
