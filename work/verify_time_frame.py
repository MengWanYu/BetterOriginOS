#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐帧验收：展开过渡的每一帧，header 时间都不许落进图标列（BACKLOG-1104 §1 / P0-1）。

为什么必须逐帧：稳定帧的时间是对的，用户看到的是动画中途那一闪；1Hz 度量采样量不到
16ms 级的闪（DEVLOG 2026-11-04 傍晚已按量测证伪过一次）。

输入 = 模块逐帧见证（调试键 betteroriginos_trace_flicker=1，挂在
NotificationStackScrollLayout.dispatchDraw 之后）。每行每帧一条：
    /big=L<行内左偏移>:W<宽>:s<在屏上01>:a<alpha>
    /time=<实例>:L<行内左偏移|->:W<宽>:a<alpha>:v<可见性>:s<在屏上01>[,...]
两个 L 都是 `getLeft + translationX` 逐级累加到所在行 —— 动画期祖先自带 translationY，
用 getLocationOnScreen 会把祖先位移算成时间位移；而只用 getLeft 又会漏掉
push-only 那套 translationX 推过去的真实落点。

断言（每条都配反例，见 --selftest）
  T1 形状：每条 trace 条目必须同时带 big= 与 time=；缺一个就是日志形状分叉，判红而不是跳过。
  T2 窗口：要有 arm 行、条目/帧数达下限，且窗口里确有行高或行位移变化（过渡真被抓到）。
  T3 材料：至少一帧"大图标在屏上"且"时间在屏上"，否则整套判据空转。
  T4 不变量：这类帧里时间与大图标的横向区间不得重叠。
  T5 可测性：在屏上的时间却量不到左偏移或宽度为 0 ⇒ 观察失效，按材料不足判红，不当中通过。

用法：
  python work/verify_time_frame.py --log <逐帧日志>
  python work/verify_time_frame.py --selftest
"""
import argparse
import io
import re
import sys

TS_RE = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d)")
ARM_RE = re.compile(r"notify_row\.trace\s+arm frames=(\d+)")
DONE_RE = re.compile(r"notify_row\.trace done frames=(\d+) rows=(\d+)")
ENTRY_RE = re.compile(r"\bf=(\d+)\s+id=([0-9a-f]+)\s+big/nat=(.*)$")
BIG_RE = re.compile(r"/big=([^/]*)")
TIME_RE = re.compile(r"/time=([^/]*)")
GEOM_RE = re.compile(r"^L(-|\d+):W(\d+):s([01]):a([\d.]+)$")
TOKEN_RE = re.compile(r"^([0-9a-f]+):L(-|\d+):W(\d+):a([\d.]+):v(\d+):s([01])$")
TAIL_RE = re.compile(r"/h(\d+)/v(-?\d+)/ty(-?\d+)\s*$")

MIN_ENTRIES = 10          # 少于这个数说明窗口里根本没动画
MIN_FRAMES = 4            # 不同帧号的数量
OVERLAP_TOL = 2           # px


def parse(text):
    """把逐帧日志拆成条目；形状分叉（缺 big=/time=）单独列出来，不静默跳过。"""
    entries, arms, dones, drift = [], 0, 0, 0
    for raw in text.splitlines():
        if ARM_RE.search(raw):
            arms += 1
            continue
        if DONE_RE.search(raw):
            dones += 1
            continue
        m = ENTRY_RE.search(raw)
        if not m:
            continue
        ts = TS_RE.match(raw)
        frame, rid, state = int(m.group(1)), m.group(2), m.group(3)
        b, t = BIG_RE.search(state), TIME_RE.search(state)
        if not b or not t:
            drift += 1
            continue
        tail = TAIL_RE.search(state)
        entries.append({
            "f": frame, "id": rid, "ts": ts.group(1) if ts else "?",
            "big": b.group(1), "time": t.group(1),
            "h": int(tail.group(1)) if tail else -1,
            "ty": int(tail.group(3)) if tail else -1,
        })
    return entries, arms, dones, drift


def _geom(value):
    """解析几何串；解析不出来返回 None（调用方按可测性处理，不当 0 用）。"""
    m = GEOM_RE.match(value.strip())
    if not m:
        return None
    left = None if m.group(1) == "-" else int(m.group(1))
    return left, int(m.group(2)), m.group(3) == "1"


def _times(value):
    out = []
    for tok in value.split(","):
        if not tok.strip():
            continue
        m = TOKEN_RE.match(tok.strip())
        if not m:
            out.append(None)
            continue
        left = None if m.group(2) == "-" else int(m.group(2))
        out.append({"inst": m.group(1), "L": left, "W": int(m.group(3)),
                    "vis": int(m.group(5)), "shown": m.group(6) == "1"})
    return out


def run(text):
    fails, oks = [], []
    entries, arms, dones, drift = parse(text)

    # --- T1 日志形状 ---
    if drift:
        fails.append("[T1] trace 条目里没有 big=/time=（形状分叉）%d 条 —— 见证文案改过而验收器没跟上，"
                     "跳过就等于判据空转" % drift)
    else:
        oks.append("T1 形状：%d 条逐帧条目全部带 big= 与 time=" % len(entries))
    if not entries:
        if drift:
            return fails, oks       # 已经因形状分叉整轮作废，不再叠一条"没条目"
        fails.append("[T2] 一条逐帧条目都没有：调试键没开、或观察窗口里面板根本没重绘")
        return fails, oks

    frames = sorted({e["f"] for e in entries})
    # --- T2 窗口有效性 ---
    if arms == 0:
        fails.append("[T2] 没有 notify_row.trace arm 行：这一轮逐帧快照从未建立，"
                     "读不到东西不代表没闪")
    else:
        oks.append("T2 窗口：arm %d 次，抓到 %d 条条目 / %d 个不同帧号%s"
                   % (arms, len(entries), len(frames),
                      "" if dones else "（done 未到，预算还没跑完）"))
    if len(entries) < MIN_ENTRIES or len(frames) < MIN_FRAMES:
        fails.append("[T2] 逐帧样本太少（%d 条 / %d 帧，下限 %d 条 %d 帧）——"
                     "窗口里根本没有动画，不能据此说没闪"
                     % (len(entries), len(frames), MIN_ENTRIES, MIN_FRAMES))
    moved = 0
    per_row = {}
    for e in entries:
        sig = (e["h"], e["ty"])
        if e["id"] in per_row and per_row[e["id"]] != sig:
            moved += 1
        per_row[e["id"]] = sig
    if moved == 0:
        fails.append("[T2] 整个窗口里没有一行的高/位移变过：%d 条条目都在同一几何上 —— "
                     "展开没发生（tap 落空或焦点不在面板），不算测过" % len(entries))
    else:
        oks.append("T2 过渡确实发生：%d 个行实例在窗口内变过高/位移" % moved)

    # --- T3/T5 材料与可测性 ---
    usable, unmeasurable, overlaps = 0, [], []
    for e in entries:
        big = _geom(e["big"])
        if big is None or not big[2] or big[1] <= 0 or big[0] is None:
            continue          # 这一帧这行没有"在屏上的大图标"（未接管的行/摘要行）
        bL, bW = big[0], big[1]
        for t in _times(e["time"]):
            if t is None:
                unmeasurable.append((e, "形状"))
                continue
            if not t["shown"] or t["W"] <= 0:
                continue      # 隐藏的那一份 header 不参与判据
            usable += 1
            if t["L"] is None:
                unmeasurable.append((e, "父链"))
                continue
            # 时间与图标横向重叠 = 用户看到的"时间压在图标上"
            if t["L"] < bL + bW - OVERLAP_TOL and t["L"] + t["W"] > bL + OVERLAP_TOL:
                overlaps.append((e, t, bL, bW))
    if usable == 0:
        fails.append("[T3] 没有任何一帧同时观测到在屏上的大图标与在屏上的时间："
                     "被接管的行没进窗口（样本/滚动问题），判据没落地")
        return fails, oks
    oks.append("T3 材料：%d 个帧×行的组合同时有在屏上的大图标与时间" % usable)
    if unmeasurable:
        e0, why = unmeasurable[0]
        fails.append("[T5] %d 个组合里时间在屏上却量不到行内左偏移/形状不对（why=%s "
                     "f=%s id=%s）—— 接管换了 id 或父链断了，几何判据是瞎的"
                     % (len(unmeasurable), why, e0["f"], e0["id"]))
    # --- T4 不变量 ---
    if overlaps:
        e0, t0, bL, bW = overlaps[0]
        sample = "；".join("f=%s id=%s time=%d..%d icon=%d..%d"
                          % (e["f"], t["inst"], t["L"], t["L"] + t["W"], bL, bL + bW)
                          for (e, t, bL, bW) in overlaps[:4])
        fails.append("[T4] %d 个帧里 header 时间与大图标横向重叠（过渡帧错位）：%s"
                     % (len(overlaps), sample))
    else:
        oks.append("T4 不变量：%d 个可用组合里没有时间压进图标区间" % usable)
    return fails, oks


# ------------------------------- 反例材料 -------------------------------
_HDR = "11-04 17:40:54.368 31942 31942 I BetterOriginOS: "


def _entry(frame, rid, big, times, h=255, ty=1200):
    return (_HDR + "  f=%d id=%s big/nat=shown:A1.00/4:0.00/icons=-/big=%s/time=%s"
            "/h%d/v0/ty%d" % (frame, rid, big, times, h, ty))


def _good():
    lines = [_HDR + "notify_row.trace arm frames=240"]
    for i in range(6):
        ty = 1200 + i * 15
        lines.append(_entry(i + 1, "aaa", "L48:W114:s1:a1.00", "1a:L198:W57:a1.00:v0:s1",
                            h=200 + i * 11, ty=ty))
    for i in range(6, 12):
        lines.append(_entry(i + 1, "bbb", "L48:W114:s1:a1.00", "2b:L198:W57:a1.00:v0:s1",
                            h=255, ty=1400 + i * 7))
    lines.append(_HDR + "notify_row.trace done frames=240 rows=2")
    return "\n".join(lines)


def _cases():
    """(名字, 材料, 期望命中的判据标号)：GOOD 只许全绿，其余每条只许红在它对应那一判。"""
    good = _good()
    overlap = good + "\n" + _entry(13, "ccc", "L48:W114:s1:a1.00", "3c:L126:W57:a1.00:v0:s1",
                                   h=150, ty=1500)
    drift = "\n".join(l.replace("/big=", "/icon=").replace("/time=", "/tm=")
                      for l in good.splitlines())
    no_arm = "\n".join(l for l in good.splitlines() if "arm frames" not in l)
    thin = "\n".join(good.splitlines()[:4])
    no_mat = "\n".join(l.replace(":s1", ":s0") if "f=" in l and "icons=-" in l else l
                       for l in good.splitlines())
    chain = good + "\n" + _entry(14, "ddd", "L48:W114:s1:a1.00", "4d:L-:W57:a1.00:v0:s1",
                                 h=255, ty=1600)
    static = "\n".join(_entry(i + 1, "eee", "L48:W114:s1:a1.00", "5e:L198:W57:a1.00:v0:s1")
                       for i in range(12))
    return [("GOOD", good, None),
            ("NO-ARM", no_arm, "T2"),
            ("THIN", thin, "T2"),
            ("STATIC", static, "T2"),
            ("NO-MATERIAL", no_mat, "T3"),
            ("OVERLAP", overlap, "T4"),
            ("SHAPE-DRIFT", drift, "T1"),
            ("UNMEASURABLE", chain, "T5")]


def selftest():
    rc = 0
    for name, text, want in _cases():
        fails, oks = run(text)
        tags = {f.split("]")[0][1:] for f in fails}
        if want is None:
            if fails:
                print("  FAIL %-14s 期望全绿，实际红：%s" % (name, fails))
                rc = 1
            else:
                print("  ok   %-14s 正例全绿（%d 条）" % (name, len(oks)))
            continue
        if want not in tags:
            print("  FAIL %-14s 期望 %s 报红，实际命中 %s" % (name, want, sorted(tags) or "无"))
            rc = 1
        elif len(tags) != 1:
            print("  FAIL %-14s 只该红在 %s 一条上，实际还红了 %s"
                  % (name, want, sorted(tags - {want})))
            rc = 1
        else:
            print("  ok   %-14s 反例精确咬住 %s" % (name, want))
    print("逐帧验收自检：%s" % ("全部通过" if rc == 0 else "有失败"))
    return rc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    if not a.log:
        print("需要 --log（或 --selftest）")
        return 2
    fails, oks = run(io.open(a.log, encoding="utf-8", errors="replace").read())
    for o in oks:
        print("  ok   " + o)
    for f in fails:
        print("  FAIL " + f)
    print("逐帧验收：%d 通过 / %d 失败" % (len(oks), len(fails)))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
