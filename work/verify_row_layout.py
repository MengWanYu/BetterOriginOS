#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""结果级验收：OS5 通知行排版 + 逐位置圆角。

输入 = uiautomator dump（XML）+ 模块见证日志（notify_row.roundness）。
断言只看用户能看见的结果，不看"钩子装没装"：

排版（density 3.0 口径，px = dp * density）
  L1 存在 42dp(126px) 大图标；
  L2 大图标相对所在行左边缘 = 16dp(48px)；
  L3 独立行的标题左边缘相对行 = 66dp(198px)；
  L4 独立行的 notification_header 高 = 34dp(102px)（标题因此比原生 42dp header 上提 8dp）。

圆角（只认 mirror=1 的见证行）
  R1 独立行 top=1 bottom=1；
  R2 组摘要 top=1；组展开且有可见子行时 bottom=0；
  R3 组子行 top=0；只有末个可见子行 bottom=1；
  R4 shelf top=1 bottom=1；
  R5 背景四角必须等于 roundness * R（容差 1px）：top=0 却画了圆角，或 top=1 却没画，都判红。

用法：
  python work/verify_row_layout.py --dump x.xml --log y.log [--density 3.0]
  python work/verify_row_layout.py --selftest
"""
import argparse
import io
import os
import re
import sys

ROW_RE = re.compile(r'<node[^>]*resource-id="[^"]*expandableNotificationRow"[^>]*>')
NODE_RE = re.compile(r"<node[^>]*>")
BOUNDS_RE = re.compile(r"bounds=\"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]\"")


def attr(node, key):
    m = re.search(key + r'="([^"]*)"', node)
    return m.group(1) if m else ""


def nodes(xml_text):
    return NODE_RE.findall(xml_text)


def bounds(node):
    m = BOUNDS_RE.search(node)
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return l, t, r, b


def parse_dump(xml_text):
    """返回 rows=[{l,t,r,b,nodes,has_children,nested}]（后代节点按嵌套关系归属最近的行走）。"""
    rows = []
    stack = []
    tok_re = re.compile(r"<node[^>]*?(/?)>|</node>")
    for tok in tok_re.finditer(xml_text):
        text = tok.group(0)
        if text == "</node>":
            if stack:
                stack.pop()
            continue
        is_self_closing = tok.group(1) == "/"
        rid = attr(text, "resource-id")
        is_row = rid.endswith("expandableNotificationRow")
        owner = next((s for s in reversed(stack) if s["row"] is not None), None)
        if is_row:
            b = bounds(text)
            if b is None:
                continue
            rec = {"row": len(rows), "bounds": b, "nodes": [text],
                   "has_children": False,
                   # 组子行的祖先里还有一行：它不适用"独立行"的 header/列宽口径
                   "nested": owner is not None}
            rows.append(rec)
            stack.append({"row": rec["row"]})
        elif is_self_closing:
            if owner is not None:
                rows[owner["row"]]["nodes"].append(text)
                if rid.endswith("notification_children_container"):
                    rows[owner["row"]]["has_children"] = True
        else:
            if owner is not None:
                rows[owner["row"]]["nodes"].append(text)
                if rid.endswith("notification_children_container"):
                    rows[owner["row"]]["has_children"] = True
            stack.append({"row": owner["row"] if owner else None})
    for rec in rows:
        l, t, r, b = rec["bounds"]
        rec.update(l=l, t=t, r=r, b=b, w=r - l, h=b - t)
    return rows


def px(dp, density):
    return dp * density


def check_dump(xml_text, density):
    fails, oks = [], []
    rows = parse_dump(xml_text)
    if not rows:
        return ["dump 里没有任何 expandableNotificationRow（样本无效）"], oks

    icons = []
    for rec in rows:
        for n in rec["nodes"]:
            b = bounds(n)
            if b and 120 <= b[2] - b[0] <= 132 and 120 <= b[3] - b[1] <= 132:
                icons.append((rec, b, n))
    if not icons:
        fails.append("L1 没有 42dp(126px) 大图标（期望 120~132px）")
    else:
        oks.append("L1 42dp 大图标 x%d" % len(icons))
        for rec, b, n in icons:
            off = b[0] - rec["l"]
            if abs(off - px(16, density)) > 3:
                fails.append("L2 图标左偏移 %dpx（行 %d），期望 %dpx"
                             % (off, rec["row"], int(px(16, density))))
        if not any("L2" in f for f in fails):
            oks.append("L2 图标左偏移 = 16dp")

    # L6/L7/L8：分组形态的观感判据（用户原话"左上角小图标别变大，跟大头儿子似的"）
    #  L6 被接管的行：图标中心与卡片中心偏差 ≤ 20px（纯按文字块居中会低 30px）
    #  L7 组摘要：**不得**出现 42dp 大图标，必须保留原生 16dp 小图标
    #  L8 展开态子行（有 header 的完整行，h ≥ 200px）：**必须**有 42dp 大图标
    seen = set()
    l6 = 0
    for rec in rows:
        for b in [bounds(n) for n in rec["nodes"]]:
            if not b or not (120 <= b[2] - b[0] <= 132 and 120 <= b[3] - b[1] <= 132):
                continue
            if id(b) in seen:
                continue
            seen.add(id(b))
            if rec["has_children"]:
                fails.append("L7 组摘要（行 %d）出现 42dp 大图标，摘要应保留原生 16dp 小图标"
                             % rec["row"])
                continue
            delta = (b[1] + b[3]) / 2.0 - (rec["t"] + rec["b"]) / 2.0
            if abs(delta) > 20:
                fails.append("L6 行 %d 图标中心比卡片中心%s %.0fpx，期望 |Δ| ≤ 20px"
                             % (rec["row"], "低" if delta > 0 else "高", abs(delta)))
            else:
                l6 += 1
    if l6 and not any(f.startswith("L6") for f in fails):
        oks.append("L6 图标纵向居中（%d 行，|Δ|≤20px）" % l6)
    if not any(f.startswith("L7") for f in fails):
        oks.append("L7 组摘要保留原生小图标")
    missing_child = []
    for rec in rows:
        if not rec.get("nested") or rec["h"] < 200:
            continue   # 收起态子行是 57px 紧凑模板，本来就不该有图标
        has_icon = any(bounds(n) and 120 <= bounds(n)[2] - bounds(n)[0] <= 132
                       and 120 <= bounds(n)[3] - bounds(n)[1] <= 132 for n in rec["nodes"])
        if not has_icon:
            missing_child.append("行%d(h%d)" % (rec["row"], rec["h"]))
    if missing_child:
        fails.append("L8 展开子行没有 42dp 大图标：" + ", ".join(missing_child)
                     + "（组展开判据或绑定漏了）")
    else:
        oks.append("L8 展开子行都有 42dp 大图标")

    polished = 0
    for rec in rows:
        if rec["has_children"] or rec.get("nested"):
            continue  # 组摘要保留原生小图标、组子行另有口径，都不参与独立行断言
        # 媒体行不归 notify_row 排版（那一行由 MediaCardFeature 自绘，A11 样式里
        # 文字列按通知模板走 20dp 内缩，不是 66dp 列）⇒ 拿它判 L3 是判据没关门，
        # 不是产品坏了。dump 里它的指纹就是行内有一枚 SeekBar。
        if any(attr(n, "class").endswith("SeekBar") for n in rec["nodes"]):
            continue
        title = None
        header_h = None
        for n in rec["nodes"]:
            rid = attr(n, "resource-id")
            if rid.endswith("notification_title") or rid == "android:id/title":
                title = bounds(n)
            if rid.endswith("notification_header"):
                hb = bounds(n)
                if hb:
                    header_h = hb[3] - hb[1]
        if title is None:
            continue
        polished += 1
        off = title[0] - rec["l"]
        if abs(off - px(66, density)) > 4:
            fails.append("L3 标题左偏移 %dpx（行 %d），期望 %dpx"
                         % (off, rec["row"], int(px(66, density))))
        if header_h is not None and abs(header_h - px(34, density)) > 3:
            fails.append("L4 header 高 %dpx（行 %d），期望 %dpx"
                         % (header_h, rec["row"], int(px(34, density))))
    if polished == 0:
        fails.append("L3/L4 没有可测的独立行标题（样本里可能只有分组）")
    else:
        if not any(f.startswith("L3") for f in fails):
            oks.append("L3 标题列 = 66dp（%d 行）" % polished)
        if not any(f.startswith("L4") for f in fails):
            oks.append("L4 header = 34dp")

    # L5：凡是有 42dp 大图标的行，header 的应用名必须与标题同列（OS5 基线 ui_final.xml：x=258，
    # 相对行 198px = 66dp）。这条就是用户看到的"图标和文字没对齐"的判据。
    checked = 0
    for rec in rows:
        if rec.get("nested") and rec["h"] < 200:
            continue   # 收起态紧凑子行没有 header，不参与列口径
        has_icon = any(bounds(n) and 120 <= bounds(n)[2] - bounds(n)[0] <= 132
                       and 120 <= bounds(n)[3] - bounds(n)[1] <= 132 for n in rec["nodes"])
        if not has_icon:
            continue
        app = None
        for n in rec["nodes"]:
            if attr(n, "resource-id").endswith("app_name_text"):
                app = bounds(n)
                break
        if app is None:
            continue
        checked += 1
        off = app[0] - rec["l"]
        if abs(off - px(66, density)) > 4:
            fails.append("L5 行 %d 应用名左偏移 %dpx，期望 %dpx（图标与文字不同列）"
                         % (rec["row"], off, int(px(66, density))))
    if checked and not any(f.startswith("L5") for f in fails):
        oks.append("L5 应用名与标题同列 66dp（%d 行）" % checked)

    # L9：header 时间必须落在文本列（OS5 基线：应用名+时间同起于 66dp 列）。
    # 判据来自 framework 的摆位规则 —— NotificationTopLineView.onLayout 按子节点顺序
    # 从左往右流排、GONE 跳过；vivo 组子行把 app_name 收掉后，时间就成第一个可见子节点，
    # 落到行首 +126，正好压在 42dp 大图标（+48..+174）上。所以直接量"时间在不在列上"。
    l9 = 0
    time_nodes = 0
    for rec in rows:
        if rec.get("nested") and rec["h"] < 200:
            continue   # 收起态紧凑子行：没有 header，不参与列口径
        has_icon = any(bounds(n) and 120 <= bounds(n)[2] - bounds(n)[0] <= 132
                       and 120 <= bounds(n)[3] - bounds(n)[1] <= 132 for n in rec["nodes"])
        for n in rec["nodes"]:
            if not attr(n, "resource-id").endswith("android:id/time"):
                continue
            tb = bounds(n)
            if tb is None or tb[2] - tb[0] <= 0:
                continue   # 被 OverflowAdjuster 压成零宽：没有可见落点可量
            time_nodes += 1
            if not has_icon:
                continue   # 未被接管的行（组摘要、媒体行）保持原生排版
            l9 += 1
            off = tb[0] - rec["l"]
            if off < px(66, density) - 4:
                fails.append("L9 行 %d 时间左偏移 %dpx，期望 ≥ %dpx（压在 42dp 图标上）"
                             % (rec["row"], off, int(px(66, density))))
    if l9 == 0:
        # 0 读数必须报红，但要说清是"没东西可量"还是"量了不对"：
        # 样本里那些 showWhen=false 的通知（LSPosed / 系统清理）根本没有 time 节点，
        # 这时该换带时间的样本（post / postbulk）再跑，而不是去怀疑对位逻辑。
        icon_rows = sum(1 for rec in rows if any(
            bounds(n) and 120 <= bounds(n)[2] - bounds(n)[0] <= 132
            and 120 <= bounds(n)[3] - bounds(n)[1] <= 132 for n in rec["nodes"]))
        if time_nodes == 0:
            fails.append("L9 整屏没有任何可读的 time 节点（%d 行有 42dp 图标）："
                         "样本都是 showWhen=false 的通知，换 post/postbulk 再跑" % icon_rows)
        else:
            fails.append("L9 有 %d 个 time 节点但都不在被接管的行里（图标行 %d）："
                         "样本或判据空转" % (time_nodes, icon_rows))
    elif not any(f.startswith("L9") for f in fails):
        oks.append("L9 header 时间在 66dp 文本列（%d 条读数）" % l9)
    return fails, oks


COL_SKIP_RE = re.compile(
    r"notify_row\.col skip why=(?P<why>\S+) pkg=(?P<pkg>\S+) lineId=(?P<line>\S+)")
COL_CLAMP_RE = re.compile(r"notify_row\.col clamp d=(?P<d>\d+) room=(?P<room>\d+)")
COL_LAYOUT_RE = re.compile(
    r"notify_row\.col layout line=(?P<line>[+-]?\d+) want=(?P<want>[+-]?\d+)"
    r" d=(?P<d>[+-]?\d+) kids=(?P<kids>\d+)")
COL_MEASURE_RE = re.compile(
    r"notify_row\.col measure row=(?P<row>\S+) lineId=(?P<lineId>\S+) line=(?P<line>[+-]?\d+)"
    r" time=(?P<time>[+-]?\d+|none) want=(?P<want>[+-]?\d+) d=(?P<d>[+-]?\d+)"
    r" moved=(?P<moved>\d+)")
# 模块在 install 那一行自报的接管方式（push-only / column-hook）：col 那套断言的唯一开关。
# 挂在"installed"同一行：单独一行的启动期日志会被 logd 每进程 250/s 限流整条冲掉。
COL_MODE_RE = re.compile(r"notify row feature installed header-time=(\S+)")
# 只有"这行不归我们接管"是正当早退；其余原因必须后来被实测补上，否则就是漏管
# 只有"这行不归我们接管"与"vivo 分组卡摘要 header（原生列、不带 42dp 图标）"算正当早退；
# 其余原因必须后来被实测补上，否则就是漏管
COL_ALLOWED_WHY = ("why=not-taken", "why=summary-header")


def check_column_log(log_text, density=3.0, install_text=None):
    """布局期对位的四路证据：实测落点、实际平移、早退原因能不能被后续实测补齐、推不动的 clamp。
    实测行（col measure）不依赖 uiautomator —— 面板活着但窗口拿不到 idle 时，它仍能证明落点。

    这套证据只属于"布局期对位"那一版实现，所以先问模块自报的接管方式：
    push-only（只在事后推 translationX）时整段**显式停用**并说明，而不是让"0 条 measure"
    变成一条永远修不好的假红；自报缺失则判红 —— 分不清"钩子没装"和"装了没抓到"就没有结论。
    自报只在进程启动那一拍打一次，取证脚本在 `logcat -c` **之前**把它单独存了文件，
    所以这里用 install_text 找它（measure/skip 仍然只从本轮窗口取，不跨窗口凑证据）。
    """
    fails, oks = [], []
    mode_src = (install_text or "") + "\n" + log_text
    modes = COL_MODE_RE.findall(mode_src)
    if not modes:
        return ["L10 模块没自报接管方式（installed 行里没有 header-time=…），"
                "无法判断布局期对位这套断言该不该跑"], []
    if modes[-1].strip() != "column-hook":
        return [], ["L10/L11 未启用：模块自报 header-time=%s（col 布局钩子没装），"
                    "时间落点只由 L9 从 dump 量" % modes[-1].strip()]
    measures = COL_MEASURE_RE.findall(log_text)
    seen_lines = set(m[1] for m in measures)
    numeric = [int(m[3]) for m in measures if m[3] != "none"]
    none_cnt = sum(1 for m in measures if m[3] == "none")
    want_px = px(66, density)
    skips = COL_SKIP_RE.findall(log_text)
    clamps = COL_CLAMP_RE.findall(log_text)
    moved = COL_LAYOUT_RE.findall(log_text)
    if not measures:
        fails.append("L10 一条 col measure 都没有：钩子没跑，或观察窗口没覆盖到被接管的行（判据空转）")
    else:
        low = [t for t in numeric if t < want_px - 4]
        if low:
            fails.append("L10 实测有 %d 条时间落在 %.0fpx 列的左边（最左 +%dpx，共 %d 条实测）"
                         % (len(low), want_px, min(low), len(numeric)))
        elif not numeric:
            fails.append("L10 实测全是 time=none（%d 条）：这一屏没有被接管的 header 时间"
                         % none_cnt)
        else:
            oks.append("L10 实测时间落点全部 ≥66dp 列（%d 条，+%d..+%dpx；none %d 条）"
                       % (len(numeric), min(numeric), max(numeric), none_cnt))
    if clamps:
        fails.append("L11 出现 %d 条 clamp（文本列推不动，时间还会压在图标上）：%s"
                     % (len(clamps), ", ".join("d=%s room=%s" % (c[0], c[1]) for c in clamps[:3])))
    # 非正当早退：只要同一 lineId 后来被实测过，就是"瞬时没挂上、下一趟补上了"，可以放行
    unresolved = [(w, l) for (w, _pkg, l) in skips
                  if "why=" + w not in COL_ALLOWED_WHY and l not in seen_lines]
    resolved = [(w, l) for (w, _pkg, l) in skips
                 if "why=" + w not in COL_ALLOWED_WHY and l in seen_lines]
    if unresolved:
        fails.append("L11 col 早退原因后来没被实测补齐：%s"
                     % ", ".join("why=%s line=%s" % (w, l) for (w, l) in unresolved[:4]))
    elif skips:
        only_taken = all("why=" + w in COL_ALLOWED_WHY for (w, _p, _l) in skips)
        if only_taken:
            oks.append("L11 col 早退只有 not-taken（媒体行等不归我们接管）×%d" % len(skips))
        else:
            oks.append("L11 结构性早退 %d 条随后都被实测补上（瞬时无 contentView）" % len(resolved))
    if moved:
        oks.append("L10 布局期实际推正 %d 次（最大 d=%dpx）" % (len(moved), max(int(m[2]) for m in moved)))
    return fails, oks


ROUND_RE = re.compile(
    r"notify_row\.roundness phase=(?P<phase>\S+) path=(?P<path>\S+) shelf=(?P<shelf>\d)"
    r" sum=(?P<sum>\d) child=(?P<child>\d) pkg=(?P<pkg>\S+)"
    r" top=(?P<top>-?[\d.]+) bot=(?P<bot>-?[\d.]+) R=(?P<R>-?[\d.]+)"
    r" radii=\[(?P<radii>[^\]]*)\] mirror=(?P<mirror>\d)")


def parse_roundness(log_text):
    out = []
    for m in ROUND_RE.finditer(log_text):
        d = m.groupdict()
        radii = [float(x) for x in d["radii"].split(",")] if d["radii"].strip() else []
        out.append({
            "phase": d["phase"], "path": d["path"], "shelf": int(d["shelf"]),
            "sum": int(d["sum"]), "child": int(d["child"]), "pkg": d["pkg"],
            "top": float(d["top"]), "bot": float(d["bot"]), "R": float(d["R"]),
            "radii": radii, "mirror": int(d["mirror"]),
        })
    return out


def check_roundness(log_text, tol=1.0):
    fails, oks = [], []
    all_rows = parse_roundness(log_text)
    rows = [r for r in all_rows if r["mirror"] == 1]
    if not rows:
        return ["R0 没有 mirror=1 的圆角见证（日志缺失或复刻没开）"], oks
    if len(all_rows) != len(re.findall(r"notify_row\.roundness phase=\S+ path=", log_text)):
        fails.append("R0 圆角见证行数 ≠ 解析条数（正则漂移）")
    # 用最后一次快照（同 path 取最后一条）
    last = {}
    for r in rows:
        last[r["path"]] = r
    rows = list(last.values())

    def radii_eq(row, top_quad, bottom_quad):
        if len(row["radii"]) != 8:
            return False
        t = row["top"] * row["R"]
        b = row["bot"] * row["R"]
        vals = [t] * 4 + [b] * 4
        return all(abs(a - e) <= tol for a, e in zip(row["radii"], vals))

    for r in rows:
        tag = "path=%s pkg=%s" % (r["path"], r["pkg"])
        if r["shelf"] == 1:
            if r["top"] < 0.99 or r["bot"] < 0.99:
                fails.append("R4 shelf %s top/bot=%s/%s，期望 1/1" % (tag, r["top"], r["bot"]))
            elif not radii_eq(r, 0, 0):
                fails.append("R5 shelf %s 背景四角与 1*R 不一致 radii=%s R=%s"
                             % (tag, r["radii"], r["R"]))
            continue
        if r["child"] == 0:
            if r["top"] < 0.99:
                fails.append("R1/R2 %s 顶角没圆（top=%s）" % (tag, r["top"]))
            if r["sum"] == 0 and r["bot"] < 0.99:
                fails.append("R1 %s 独立行底角没圆（bot=%s）" % (tag, r["bot"]))
        else:
            if r["top"] > 0.01:
                fails.append("R3 %s 子行顶角应为 0（top=%s）" % (tag, r["top"]))
        if not radii_eq(r, 0, 0):
            fails.append("R5 %s 背景四角 ≠ roundness*R（radii=%s top=%s bot=%s R=%s）"
                         % (tag, r["radii"], r["top"], r["bot"], r["R"]))
    # 常量覆盖回归：top=0 但背景顶角有值（旧的 dp20 覆盖就是这样）
    for r in rows:
        if len(r["radii"]) == 8 and r["top"] <= 0.01 and max(r["radii"][:4]) > tol:
            fails.append("R5 %s top=0 但背景顶角=%s（常量覆盖回归）"
                         % (r["path"], r["radii"][:4]))
    if not fails:
        oks.append("R1~R5 逐位置圆角与背景四角一致（%d 行）" % len(rows))
    return fails, oks


# ---------------------------------------------------------------- selftest

GOOD_XML = """<hierarchy>
<node resource-id="com.android.systemui:id/notification_stack_scroller" bounds="[0,0][1080,2298]">
 <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,699][1020,954]">
  <node resource-id="android:id/notification_header" bounds="[60,699][1020,801]"/>
  <node resource-id="android:id/app_name_text" bounds="[258,730][402,778]"/>
  <node resource-id="android:id/time" bounds="[438,730][533,778]"/>
  <node resource-id="android:id/notification_main_column" bounds="[120,801][960,912]"/>
  <node resource-id="android:id/title" bounds="[258,801][960,862]"/>
  <node class="android.widget.ImageView" bounds="[108,778][234,904]"/>
 </node>
 <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,975][1020,1620]">
  <node resource-id="com.android.systemui:id/notification_children_container" bounds="[60,975][1020,1620]">
   <node resource-id="android:id/notification_header" bounds="[60,975][1020,1107]"/>
   <node resource-id="android:id/icon" bounds="[120,1023][168,1071]"/>
   <node resource-id="android:id/app_name_text" bounds="[192,1021][446,1069]"/>
   <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,1107][1020,1362]">
    <node resource-id="android:id/notification_header" bounds="[60,1107][1020,1209]"/>
    <node resource-id="android:id/app_name_text" bounds="[258,1138][402,1186]"/>
    <node resource-id="android:id/title" bounds="[258,1209][960,1270]"/>
    <node class="android.widget.ImageView" bounds="[108,1186][234,1312]"/>
   </node>
   <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,1362][1020,1419]">
    <node resource-id="android:id/notification_title" bounds="[120,1362][623,1419]"/>
   </node>
   <!-- OS3 组子行的真实形状：header 里没有 app_name，只有时间 -->
   <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,1419][1020,1620]">
    <node resource-id="android:id/notification_header" bounds="[60,1419][1020,1521]"/>
    <node resource-id="android:id/time" bounds="[258,1450][353,1498]"/>
    <node resource-id="android:id/title" bounds="[258,1521][960,1582]"/>
    <node class="android.widget.ImageView" bounds="[108,1470][234,1596]"/>
   </node>
  </node>
 </node>
</node></hierarchy>"""

# 媒体行那一行的文字列按 A11 通知模板走 20dp 内缩（不是 66dp 列），行内有一枚 SeekBar。
# L3/L4 必须认出“这行不归 notify_row 排版”并跳过它 —— 把它判红是判据没关门
# （11-04 真机就这么红过一次，被当成产品回归查了半天）。
MEDIA_ROW = "\n".join([
    ' <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,420][1020,975]">',
    '  <node class="android.widget.TextView" resource-id="com.android.systemui:id/notification_title" bounds="[120,470][900,527]"/>',
    '  <node class="android.widget.SeekBar" resource-id="android:id/progress" bounds="[229,880][756,940]"/>',
    ' </node>',
    "",
])
GOOD_XML_MEDIA = GOOD_XML.replace(
    ' <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,975]',
    MEDIA_ROW + ' <node resource-id="com.android.systemui:id/expandableNotificationRow" bounds="[60,975]')

GOOD_LOG = "\n".join([
    # 接管方式自报：col measure 那套断言只在 column-hook 那一版才该跑
    'notify row feature installed header-time=column-hook',
    # 独立行：整卡圆
    'notify_row.roundness phase=sep path=2 shelf=0 sum=0 child=0 pkg=a top=1.00 bot=1.00 R=54.00 radii=[54,54,54,54,54,54,54,54] mirror=1',
    # 组摘要展开：顶圆底平
    'notify_row.roundness phase=sep path=3 shelf=0 sum=1 child=0 pkg=b top=1.00 bot=0.00 R=54.00 radii=[54,54,54,54,0,0,0,0] mirror=1',
    # 组子行：中间方、末行底圆
    'notify_row.roundness phase=sep path=3>4>1 shelf=0 sum=0 child=1 pkg=b top=0.00 bot=0.00 R=54.00 radii=[0,0,0,0,0,0,0,0] mirror=1',
    'notify_row.roundness phase=sep path=3>4>3 shelf=0 sum=0 child=1 pkg=b top=0.00 bot=1.00 R=54.00 radii=[0,0,0,0,54,54,54,54] mirror=1',
    # shelf：顶底都圆
    'notify_row.roundness phase=sep path=9 shelf=1 sum=0 child=0 pkg=- top=1.00 bot=1.00 R=54.00 radii=[54,54,54,54,54,54,54,54] mirror=1',
    # header 文本列：组子行被推正后的实测落点（+198 = 66dp），以及媒体行正当早退
    'notify_row.col layout line=+126 want=+198 d=72 kids=1 rowH=201 pkg=b',
    'notify_row.col measure row=aa11 lineId=L11 line=+126 time=+198 want=+198 d=72 moved=1 rowH=201 pkg=b',
    'notify_row.col measure row=bb22 lineId=L22 line=+198 time=+378 want=+198 d=0 moved=0 rowH=255 pkg=a',
    'notify_row.col skip why=not-taken pkg=com.android.bbkmusic lineId=cc33',
    'notify_row.col skip why=no-cv pkg=b lineId=L11 hop=NotificationTopLineView<RelativeLayout<FrameLayout',
    'notify_row.col skip why=summary-header pkg=b lineId=sh1 hop=TopLine<HeaderView<ChildrenContainer',
])


def run(xml_text, log_text, density=3.0, install_text=None):
    f1, o1 = check_dump(xml_text, density)
    f2, o2 = check_roundness(log_text)
    f3, o3 = check_column_log(log_text, density, install_text)
    return f1 + f2 + f3, o1 + o2 + o3


def selftest():
    cases = [
        ("baseline", GOOD_XML, GOOD_LOG, 0),
        ("媒体行不参与 L3/L4（有 SeekBar 就跳过）", GOOD_XML_MEDIA, GOOD_LOG, 0),
        ("图标变小", GOOD_XML.replace("[234,904]", "[216,886]")
         .replace("[234,1312]", "[216,1294]"), GOOD_LOG, 1),
        ("图标挪到 header 左上", GOOD_XML.replace("[108,778][234,904]", "[120,699][246,825]"), GOOD_LOG, 1),
        ("单行图标偏低10dp", GOOD_XML.replace("[108,778][234,904]", "[108,793][234,919]"), GOOD_LOG, 1),
        ("摘要长出大图标", GOOD_XML.replace(
            '<node resource-id="android:id/icon" bounds="[120,1023][168,1071]"/>',
            '<node class="android.widget.ImageView" bounds="[108,990][234,1116]"/>'), GOOD_LOG, 1),
        ("展开子行没图标", GOOD_XML.replace(
            '<node class="android.widget.ImageView" bounds="[108,1186][234,1312]"/>', ''),
         GOOD_LOG, 1),
        ("标题没右移", GOOD_XML.replace("[258,801][960,862]", "[120,801][960,862]"), GOOD_LOG, 1),
        ("应用名没与标题同列", GOOD_XML.replace("[258,730][402,778]", "[168,730][312,778]"), GOOD_LOG, 1),
        ("子行应用名没同列", GOOD_XML.replace("[258,1138][402,1186]", "[168,1138][312,1186]"), GOOD_LOG, 1),
        ("header 没压到 34dp", GOOD_XML.replace("[1020,801]", "[1020,825]"), GOOD_LOG, 1),
        ("独立行底角没圆", GOOD_LOG, GOOD_LOG.replace(
            "pkg=a top=1.00 bot=1.00 R=54.00 radii=[54,54,54,54,54,54,54,54]",
            "pkg=a top=1.00 bot=0.00 R=54.00 radii=[54,54,54,54,0,0,0,0]"), 1),
        ("组摘要顶角没圆", GOOD_LOG, GOOD_LOG.replace(
            "pkg=b top=1.00 bot=0.00 R=54.00 radii=[54,54,54,54,0,0,0,0]",
            "pkg=b top=0.00 bot=0.00 R=54.00 radii=[0,0,0,0,0,0,0,0]"), 1),
        ("背景常量覆盖", GOOD_LOG, GOOD_LOG.replace(
            "pkg=b top=0.00 bot=0.00 R=54.00 radii=[0,0,0,0,0,0,0,0]",
            "pkg=b top=0.00 bot=0.00 R=54.00 radii=[54,54,54,54,0,0,0,0]"), 1),
        ("shelf 顶角没圆", GOOD_LOG, GOOD_LOG.replace(
            "shelf=1 sum=0 child=0 pkg=- top=1.00 bot=1.00",
            "shelf=1 sum=0 child=0 pkg=- top=0.00 bot=1.00"), 1),
        ("没有 mirror=1 见证", GOOD_XML, GOOD_LOG.replace("mirror=1", "mirror=0"), 1),
        # L9/L10：header 时间对位（OS3 组子行只有时间这一个可见子节点）
        ("子行时间压在图标上", GOOD_XML.replace("[258,1450][353,1498]", "[186,1450][281,1498]"),
         GOOD_LOG, 1),
        ("独立行时间没到列上", GOOD_XML.replace("[438,730][533,778]", "[126,730][221,778]"),
         GOOD_LOG, 1),
        ("一条时间都读不到", GOOD_XML.replace(
            '<node resource-id="android:id/time" bounds="[438,730][533,778]"/>', '')
            .replace('<node resource-id="android:id/time" bounds="[258,1450][353,1498]"/>', ''),
         GOOD_LOG, 1),
        ("文本列推不动留下 clamp", GOOD_XML, GOOD_LOG +
         "\nnotify_row.col clamp d=72 room=10 lineId=L99 pkg=a", 1),
        ("布局期确实推正", GOOD_XML, GOOD_LOG +
         "\nnotify_row.col layout line=+126 want=+198 d=72 kids=1 rowH=201 pkg=a", 0),
        ("实测时间还在图标上", GOOD_XML, GOOD_LOG.replace("time=+198", "time=+126"), 1),
        ("一条实测都没有", GOOD_XML,
         "\n".join(l for l in GOOD_LOG.split("\n") if "col measure" not in l), 1),
        # 接管方式开关：自报缺失=判红；push-only=整段停用（不能拿"0 条 measure"当回归）
        ("没自报 header-time 方式", GOOD_XML,
         "\n".join(l for l in GOOD_LOG.split("\n") if "installed header-time" not in l), 1),
        ("push-only 时 col 断言停用", GOOD_XML,
         GOOD_LOG.replace("header-time=column-hook", "header-time=push-only"), 0),
        ("结构性早退原因", GOOD_XML, GOOD_LOG.replace("why=not-taken", "why=no-cv"), 1),
        ("未知早退原因", GOOD_XML, GOOD_LOG.replace(
            "why=summary-header", "why=mystery-shape"), 1),
        ("实测全是 time=none", GOOD_XML, GOOD_LOG.replace("time=+198", "time=none")
         .replace("time=+378", "time=none"), 1),
        ("媒体行正当早退", GOOD_XML, GOOD_LOG.replace(
            "skip why=not-taken pkg=com.android.bbkmusic",
            "skip why=not-taken pkg=com.other.app"), 0),
        ("早退没被实测补齐", GOOD_XML,
         GOOD_LOG.replace("pkg=b lineId=L11", "pkg=b lineId=ZZ9"), 1),
    ]
    bad = 0
    for name, xml, log, want_fail in cases:
        fails, _ = run(xml, log)
        got = 1 if fails else 0
        if got != want_fail:
            bad += 1
            print("SELFTEST FAIL: %s 期望 fail=%d 实得 %d" % (name, want_fail, got))
            for f in fails:
                print("   -", f)
        else:
            print("  ok  %s（fails=%d）" % (name, len(fails)))
    if bad:
        print("SELFTEST FAIL: %d 组" % bad)
        return 1
    print("SELFTEST: %d 组全部符合预期" % len(cases))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump")
    ap.add_argument("--log")
    ap.add_argument("--density", type=float, default=3.0)
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--log-only", action="store_true",
                    help="uiautomator 拿不到 idle 时用：只跑日志侧判据，几何判据明确标为未测")
    ap.add_argument("--install",
                    help="logcat -c 之前单独存的启动期证据（模块自报接管方式只在那里打一次）")
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    if not a.log or (not a.dump and not a.log_only):
        print("需要 --dump 与 --log（或 --log-only，或用 --selftest）")
        return 2
    log_text = io.open(a.log, encoding="utf-8", errors="replace").read()
    install_text = (io.open(a.install, encoding="utf-8", errors="replace").read()
                    if a.install and os.path.exists(a.install) else "")
    if a.log_only:
        fails, oks = check_column_log(log_text, a.density, install_text)
        f2, o2 = check_roundness(log_text)
        fails, oks = f2 + fails, o2 + oks
        oks.insert(0, "本轮只判日志侧（L1~L9 依赖 uiautomator，本轮未测，别当已通过）")
    else:
        xml_text = io.open(a.dump, encoding="utf-8", errors="replace").read()
        fails, oks = run(xml_text, log_text, a.density, install_text)
    for o in oks:
        print("  ok   " + o)
    for f in fails:
        print("  FAIL " + f)
    print("结果级验收：%d 通过 / %d 失败" % (len(oks), len(fails)))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
