#!/usr/bin/env python3
"""Code-level verifier for the iOS-style notification rows.

Reads `notify_row.verify` / `notify_row.stack` lines from logcat and asserts
measured view properties (px from View bounds, not screenshots).
Run: python work/verify_notify.py
"""
import os
import re
import subprocess
import sys

TAG = "notify_row.verify"
STACK_TAG = "notify_row.stack"


def logcat():
    # 多设备、或 shell 读不到 logcat 的机器（vivo OS3 必须 root）用环境变量指路：
    #   BO_LOG_FILE=<已导出的日志> / BO_ADB_SERIAL=<serial> / BO_ADB_ROOT=1
    path = os.environ.get("BO_LOG_FILE", "").strip()
    if path:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    serial = os.environ.get("BO_ADB_SERIAL", "").strip()
    cmd = ["adb"] + (["-s", serial] if serial else [])
    if os.environ.get("BO_ADB_ROOT", "").strip() in ("1", "true"):
        cmd += ["shell", "su", "-c", "logcat -d"]
    else:
        cmd += ["logcat", "-d"]
    try:
        return subprocess.check_output(cmd, text=True, errors="replace")
    except Exception as exc:
        print("adb logcat failed:", exc)
        sys.exit(2)


def parse_kv(text):
    out = {}
    for token in text.split():
        if "=" in token:
            k, v = token.split("=", 1)
            out[k] = v
    return out


def num(d, key, default=-9999):
    try:
        return float(d.get(key, default))
    except (TypeError, ValueError):
        return default


def color(d, key):
    v = d.get(key, "")
    if v.startswith("0x"):
        try:
            return int(v, 16)
        except ValueError:
            pass
    return None


def main():
    rows = []
    overlays = []
    headsups = []
    stacks = []          # ShadeListBuilder 流水线统计（notify_row.stack）
    stack_rows = []      # 折叠堆叠行的测量（notify_row.verify.stack）
    by_key = {}          # 组标识 -> 最后一次测量
    witness_cards = set()  # 屏幕上真实挂着堆叠卡的行（notify_row.witness card=1）
    skipped_stack = []     # 模块主动放弃的堆叠测量（notify_row.verify.stack.skipped）
    skipped_base = []      # 模块主动放弃的普通行测量（notify_row.verify.skipped）
    live_pid = None      # 最近一次 verify 行的进程：换过 SystemUI 进程后旧日志一律不算证据
    lines = logcat().splitlines()
    for line in reversed(lines):
        m = re.search(r"\s(\d+)\s+\d+\s+[IWE] BetterOriginOS", line)
        if m and "notify_row.verify" in line:
            live_pid = m.group(1)
            break
    for line in lines:
        if "notify_row.verify.stack.skipped" in line:
            # 必须在 verify.stack 之前判：它是前者的超串，否则会当成一次测量
            m = re.search(r"\s(\d+)\s+\d+\s+[IWE] BetterOriginOS", line)
            if live_pid and (not m or m.group(1) != live_pid):
                continue          # 旧进程（换过 SystemUI / 面板没开）的放弃记录不算证据
            skipped_stack.append(line.split("notify_row.verify.stack.skipped", 1)[1].strip())
            continue
        if "notify_row.verify.skipped" in line:
            # 同上：必须在 TAG 分支之前判，它是 TAG 的超串
            m = re.search(r"\s(\d+)\s+\d+\s+[IWE] BetterOriginOS", line)
            if live_pid and (not m or m.group(1) != live_pid):
                continue
            skipped_base.append(parse_kv(line.split("notify_row.verify.skipped", 1)[1]))
            continue
        if "notify_row.witness" in line and "card=1" in line:
            # 观察机制兜底：卡片确实在屏上，却没有对应的 verify.stack 测量，
            # 说明覆盖有洞（曾经就这么"通过"过一次）
            m = re.search(r"\s(\d+)\s+\d+\s+[IWE] BetterOriginOS", line)
            if live_pid and (not m or m.group(1) != live_pid):
                continue          # 旧进程的证据
            m = re.search(r"id=([0-9a-f]+)", line)
            if m:
                witness_cards.add(m.group(1))
        if "notify_row.verify.stack" in line:
            r = parse_kv(line.split("notify_row.verify.stack", 1)[1])
            # 卡片会随组内条数变化被重测，只保留每组最后一次（中间态不是缺陷）
            by_key[r.get("gkey") or r.get("pkg")] = r
        elif "notify_row.verify.overlay" in line:
            overlays.append(parse_kv(
                line.split("notify_row.verify.overlay", 1)[1]))
        elif TAG in line:
            r = parse_kv(line.split(TAG, 1)[1])
            if r.get("headsup") == "1":
                headsups.append(r)
            else:
                rows.append(r)
        elif STACK_TAG in line:
            stacks.append(parse_kv(line.split(STACK_TAG, 1)[1]))

    if not rows and not stack_rows:
        print("FAIL: no notify_row.verify lines; is betteroriginos_notify_verify=1 "
              "and the shade/lock screen laid out?")
        sys.exit(1)

    failures = []
    warnings = []
    checked = 0
    for r in rows:
        # Only the visible/contracted variant is the iOS collapsed card.
        if r.get("expanded") == "1":
            continue
        if r.get("summaryOnly") == "1":
            warnings.append("summaryOnly row skipped: %s" % r.get("pkg"))
            continue
        if r.get("groupChild") == "1" and r.get("stackMode") != "1":
            failures.append("groupChild=1 (grouping leaked): pkg=%s" % r.get("pkg"))
            continue
        checked += 1
        density = num(r, "density", 3.0)
        font = num(r, "fontScale", 1.0)
        dp = lambda n: round(n * density)
        sp = lambda n: round(n * density * font)
        row = "pkg=%s style=%s" % (r.get("pkg"), r.get("style"))

        def expect(cond, msg):
            if not cond:
                failures.append("%s: %s" % (row, msg))

        expect(abs(num(r, "iconW") - dp(38)) <= 2, "iconW=%s expected=%s" %
               (r.get("iconW"), dp(38)))
        expect(abs(num(r, "iconH") - dp(38)) <= 2, "iconH=%s expected=%s" %
               (r.get("iconH"), dp(38)))
        expect(abs(num(r, "iconStart") - dp(16)) <= 2, "iconStart=%s expected=%s" %
               (r.get("iconStart"), dp(16)))
        expect(abs(num(r, "iconCenterDelta")) <= dp(2),
               "icon not vertically centered: delta=%s" % r.get("iconCenterDelta"))
        expect(r.get("iconVis") == "0", "iconVis=%s" % r.get("iconVis"))
        expect(r.get("iconDrawable") == "1", "icon has no drawable")
        expect(abs(num(r, "nIconTitleGap") - dp(12)) <= 3,
               "icon-title gap=%s expected=%s" % (r.get("nIconTitleGap"), dp(12)))
        expect(abs(num(r, "headerH") - dp(34)) <= 2,
               "headerH=%s expected=%s" % (r.get("headerH"), dp(34)))
        # 组内子行不显示 app 名行（组头已带），只有顶层行按 66dp 列校验
        if r.get("groupChild") == "1":
            expect(r.get("appVis") != "0",
                   "group child must not repeat app name: appVis=%s" % r.get("appVis"))
        else:
            expect(abs(num(r, "appStart") - dp(66)) <= 3,
                   "appStart=%s expected=%s" % (r.get("appStart"), dp(66)))
        expect(abs(num(r, "titleStart") - dp(66)) <= 3,
               "titleStart=%s expected=%s" % (r.get("titleStart"), dp(66)))
        expect(abs(num(r, "bodyStart") - dp(66)) <= 3,
               "bodyStart=%s expected=%s" % (r.get("bodyStart"), dp(66)))
        expect(abs(num(r, "appTS") - sp(12)) <= 2,
               "appTS=%s expected=%s" % (r.get("appTS"), sp(12)))
        expect(abs(num(r, "titleTS") - sp(15)) <= 2,
               "titleTS=%s expected=%s" % (r.get("titleTS"), sp(15)))
        expect(abs(num(r, "bodyTS") - sp(14)) <= 2,
               "bodyTS=%s expected=%s" % (r.get("bodyTS"), sp(14)))
        expect(r.get("titleMax") == "2", "titleMax=%s" % r.get("titleMax"))
        expect(r.get("bodyMax") == "4", "bodyMax=%s" % r.get("bodyMax"))
        expect(num(r, "titleLines") <= 2, "titleLines=%s" % r.get("titleLines"))
        expect(num(r, "bodyLines") <= 4, "bodyLines=%s" % r.get("bodyLines"))
        expect(r.get("shown") == "1", "row not shown")
        expect(r.get("guard") == "1", "pre-draw guard not installed")
        # 清除能力必须还在：OS3 没有一键清除圆点，逐条滑动 / 单条清除是唯一的移除入口，
        # iOS 化改造绝不能改掉行的手势能力（这一条正是本项目的核心验收点）。
        # rowCanDismiss = 视图侧能力，必须恒为 1；
        # entryClearable = 策略侧结果，只有通知自带 ONGOING(2)/NO_CLEAR(32)/FGS(64) 时才允许为 0，
        # 否则就是"被我们改没了"。
        # 位值取自平台 jar 的常量（javap android.app.Notification，别凭记忆）：
        # FLAG_ONGOING_EVENT=2、FLAG_NO_CLEAR=32、FLAG_FOREGROUND_SERVICE=64
        flags = int(num(r, "notifFlags", 0))
        policy_locked = bool(flags & 0x2) or bool(flags & 0x20) or bool(flags & 0x40)
        # 只有"通知本身可清除"时才要求手势能力还在；
        # 系统本来就禁止滑动清除的通知（ongoing/no-clear/FGS）记 0 是正确行为，
        # 实测 OS3 分离模式下 ongoing 行就是 rowCanDismiss=0。
        if not policy_locked:
            if r.get("rowCanDismiss") is not None:
                expect(r.get("rowCanDismiss") == "1",
                       "row lost its dismiss gesture: rowCanDismiss=%s" % r.get("rowCanDismiss"))
        if r.get("entryClearable") is not None and r.get("notifFlags") is not None:
            if not policy_locked:
                expect(r.get("entryClearable") == "1",
                       "entry not clearable although notification is not ongoing/FGS: "
                       "flags=0x%x entryClearable=%s" % (flags, r.get("entryClearable")))
        # 动作行（A13/vivo 的动作带在行内、模板之外）按设计保留展开箭头，
        # 且行高必须比收起高多出一条动作带；时间留在 app 名后面（原生就是这么排的），
        # 所以不要求右对齐，但必须不与 app 名挤在一起。
        # verify 发生在绑定瞬间，动作带还没测量，故用 expand=1 作为这一类的标记。
        act_h = num(r, "actH", 0)
        action_row = r.get("expand") == "1" or act_h > 0
        if action_row:
            band = act_h if act_h > 0 else num(r, "rowH") - num(r, "contractedH")
            expect(num(r, "rowH") > num(r, "contractedH"),
                   "action row did not grow for its action band: rowH=%s contracted=%s" %
                   (r.get("rowH"), r.get("contractedH")))
            expect(dp(20) <= band <= dp(72),
                   "action band %s outside %d..%dpx (实测：我们 2 动作样本 98px、"
                   "vivo 模板 126px)" % (band, dp(20), dp(72)))
            expect(abs(num(r, "contentH") - num(r, "rowH")) <= 4,
                   "action row content height %s != rowH %s" %
                   (r.get("contentH"), r.get("rowH")))
        else:
            expect(abs(num(r, "contentH") - num(r, "contractedH")) <= 1,
                   "content height %s != contracted %s" %
                   (r.get("contentH"), r.get("contractedH")))
            expect(abs(num(r, "rowH") - num(r, "contractedH")) <= 2,
                   "row height %s != contracted %s" %
                   (r.get("rowH"), r.get("contractedH")))
        expect(num(r, "rowExtra") == 0, "row extra height=%s" % r.get("rowExtra"))
        # 折叠堆叠下，组内子行位于 NotificationChildrenContainer 内，
        # 顶层卡片几何断言只适用于栈中顶层行
        top_level = "ChildrenContainer" not in r.get("rowParent", "")
        if top_level:
            expect(int(num(r, "stackChildContainers", 0)) == 0,
                   "group child containers visible=%s" % r.get("stackChildContainers"))
            expect(int(num(r, "stackRows", 0)) >= 1, "no rows in stack")
            expect(abs(num(r, "cardLeft") - dp(16)) <= 2,
                   "card left=%s expected=%s" % (r.get("cardLeft"), dp(16)))
            expect(abs(num(r, "cardRight") - dp(16)) <= 2,
                   "card right=%s expected=%s" % (r.get("cardRight"), dp(16)))
            for gap_key in ("gapAbove", "gapBelow"):
                gap = num(r, gap_key, -1)
                if gap >= 0:
                    expect(abs(gap - dp(8)) <= 2,
                           "%s=%s expected=%s" % (gap_key, gap, dp(8)))
            expect(abs(num(r, "stackSide") - dp(16)) <= 2,
                   "stack side=%s expected=%s" % (r.get("stackSide"), dp(16)))
            expect(abs(num(r, "stackGap") - dp(8)) <= 2,
                   "stack gap=%s expected=%s" % (r.get("stackGap"), dp(8)))
        # 文字右端距：A13 的动作行模板自带 4dp 箭头槽（真机量得 60px，非动作行 48px），
        # 这是 vivo 模板属性而不是我们的缺陷，但容差照旧收紧，变了就要重新解释。
        end_gap_expect = dp(16) + (dp(4) if action_row else 0)
        # 标题：只有"通知本身带标题"时，隐藏才算缺陷（dynsystem 这类只有 EXTRA_TEXT）
        has_title = r.get("hasTitle", "1") != "0"
        if has_title:
            expect(r.get("titleVis") == "0", "title hidden")
        else:
            warnings.append("pkg=%s notification has no title, title checks skipped"
                            % r.get("pkg"))
        if has_title or r.get("titleVis") == "0":
            expect(abs(num(r, "titleEndGap") - end_gap_expect) <= 3,
                   "title end gap=%s expected=%s" %
                   (r.get("titleEndGap"), end_gap_expect))
        expect(abs(num(r, "bodyEndGap") - end_gap_expect) <= 3,
               "body end gap=%s expected=%s" %
               (r.get("bodyEndGap"), end_gap_expect))
        expect(float(r.get("dividerAlpha", -1)) <= 0.01,
               "time divider visible: alpha=%s" % r.get("dividerAlpha"))
        expect(r.get("small") == "0", "visible small icons=%s" % r.get("small"))
        if not action_row:
            expect(r.get("expand") == "0",
                   "visible expand buttons=%s" % r.get("expand"))
        if r.get("groupChild") != "1":
            expect(r.get("appVis") == "0", "app name hidden")
        # 有些模板本身就没有时间位（vivo 自家模板），"没时间"是样本属性不是缺陷；
        # 但据此算出的端距/重叠全是噪声（时间 View 不存在时坐标为 0，会算出 -270 这种数）
        time_present = r.get("timeVis") == "0" and num(r, "timeW", -1) > 0
        if not time_present:
            warnings.append("pkg=%s template has no time view, time checks skipped"
                            % r.get("pkg"))
        else:
            if action_row:
                # 动作行右上角有箭头：时间仍右对齐，但要让出 16dp 边距 + 30dp 箭头预留
                expect(r.get("timeParent") == "NotificationHeaderView",
                       "time parent=%s on action row" % r.get("timeParent"))
                expect(abs(num(r, "timeEndGap") - (dp(16) + dp(30))) <= 3,
                       "action row time end gap=%s expected=%s (16dp 边距 + 30dp 箭头)" %
                       (r.get("timeEndGap"), dp(16) + dp(30)))
            else:
                expect(r.get("timeParent") == "NotificationHeaderView",
                       "time parent=%s (expected header right-align)" % r.get("timeParent"))
                expect(abs(num(r, "timeEndGap") - dp(16)) <= 3,
                       "time end gap=%s expected=%s" % (r.get("timeEndGap"), dp(16)))
            expect(num(r, "nAppTimeGap") >= 8, "app/time overlap gap=%s" %
                   r.get("nAppTimeGap"))
        expect(r.get("bodyVis") == "0", "body hidden")
        expect(r.get("headerVis") == "0", "header hidden")
        light = r.get("night") == "0"
        expect(color(r, "titleColor") == (0xde000000 if light else 0xffffffff),
               "titleColor=%s" % r.get("titleColor"))
        expect(color(r, "bodyColor") == (0xde000000 if light else 0xffffffff),
               "bodyColor=%s" % r.get("bodyColor"))
        expect(color(r, "appColor") == (0x61000000 if light else 0xb3ffffff),
               "appColor=%s" % r.get("appColor"))
        expect(color(r, "timeColor") == (0x61000000 if light else 0xb3ffffff),
               "timeColor=%s" % r.get("timeColor"))

    if rows:
        sample = rows[-1]
        print("card metrics: left=%s right=%s gapAbove=%s gapBelow=%s stackW=%s" %
              (sample.get("cardLeft"), sample.get("cardRight"),
               sample.get("gapAbove"), sample.get("gapBelow"), sample.get("stackW")))

    if stacks:
        latest = stacks[-1]
        print("stack:", latest)
        if int(latest.get("groupEntries", "0")) != 0:
            failures.append("group entries remain in render list")
        if int(latest.get("summaries", "0")) != 0:
            warnings.append("group summaries remain in render list (may be summary-only)")

    overlay_checked = 0
    for r in overlays:
        overlay_checked += 1
        density = num(r, "density", 3.0)
        font = num(r, "fontScale", 1.0)
        dp = lambda n: round(n * density)
        sp = lambda n: round(n * density * font)
        row = "overlay pkg=%s" % r.get("pkg")

        def oexpect(cond, msg):
            if not cond:
                failures.append("%s: %s" % (row, msg))

        oexpect(r.get("shown") == "1", "overlay not shown")
        oexpect(r.get("overlayVis") == "0", "overlay visibility=%s"
                % r.get("overlayVis"))
        oexpect(num(r, "origAlpha", 1) <= 0.01,
                "original template not hidden: alpha=%s" % r.get("origAlpha"))
        oexpect(r.get("groupChild") == "0" or r.get("stackMode") == "1",
                "groupChild=1")
        oexpect(r.get("small") == "0", "small icons=%s" % r.get("small"))
        oexpect(r.get("expand") == "0", "expand=%s" % r.get("expand"))
        if r.get("stackMode") != "1":
            oexpect(int(num(r, "stackChildContainers", 0)) == 0,
                    "group containers=%s" % r.get("stackChildContainers"))
        oexpect(abs(num(r, "overlayH") - num(r, "rowH")) <= 2,
                "overlayH=%s rowH=%s" % (r.get("overlayH"), r.get("rowH")))
        oexpect(abs(num(r, "overlayH") - num(r, "contentH")) <= 2,
                "overlayH=%s contentH=%s" % (r.get("overlayH"), r.get("contentH")))
        oexpect(abs(num(r, "iconW") - dp(38)) <= 2,
                "iconW=%s expected=%s" % (r.get("iconW"), dp(38)))
        oexpect(abs(num(r, "iconH") - dp(38)) <= 2,
                "iconH=%s expected=%s" % (r.get("iconH"), dp(38)))
        oexpect(abs(num(r, "iconStart") - dp(16)) <= 2,
                "iconStart=%s expected=%s" % (r.get("iconStart"), dp(16)))
        oexpect(abs(num(r, "iconCenterDelta")) <= dp(2),
                "icon off-center delta=%s" % r.get("iconCenterDelta"))
        oexpect(r.get("iconDrawable") == "1", "icon no drawable")
        oexpect(abs(num(r, "appStart") - dp(66)) <= 3,
                "appStart=%s expected=%s" % (r.get("appStart"), dp(66)))
        oexpect(abs(num(r, "titleStart") - dp(66)) <= 3,
                "titleStart=%s expected=%s" % (r.get("titleStart"), dp(66)))
        oexpect(abs(num(r, "bodyStart") - dp(66)) <= 3,
                "bodyStart=%s expected=%s" % (r.get("bodyStart"), dp(66)))
        oexpect(abs(num(r, "timeEndGap") - dp(16)) <= 3,
                "timeEndGap=%s expected=%s" % (r.get("timeEndGap"), dp(16)))
        oexpect(abs(num(r, "titleEndGap") - dp(16)) <= 3,
                "titleEndGap=%s expected=%s" % (r.get("titleEndGap"), dp(16)))
        oexpect(abs(num(r, "bodyEndGap") - dp(16)) <= 3,
                "bodyEndGap=%s expected=%s" % (r.get("bodyEndGap"), dp(16)))
        oexpect(abs(num(r, "appTS") - sp(12)) <= 2,
                "appTS=%s expected=%s" % (r.get("appTS"), sp(12)))
        oexpect(abs(num(r, "titleTS") - sp(15)) <= 2,
                "titleTS=%s expected=%s" % (r.get("titleTS"), sp(15)))
        oexpect(abs(num(r, "bodyTS") - sp(14)) <= 2,
                "bodyTS=%s expected=%s" % (r.get("bodyTS"), sp(14)))
        oexpect(r.get("titleMax") == "2", "titleMax=%s" % r.get("titleMax"))
        oexpect(r.get("bodyMax") == "4", "bodyMax=%s" % r.get("bodyMax"))
        oexpect(num(r, "titleLines") <= 2, "titleLines=%s" % r.get("titleLines"))
        oexpect(num(r, "bodyLines") <= 4, "bodyLines=%s" % r.get("bodyLines"))
        oexpect(num(r, "nAppTimeGap") >= 8, "app/time overlap=%s"
                % r.get("nAppTimeGap"))
        oexpect(r.get("appVis") == "0", "app hidden")
        oexpect(r.get("timeVis") == "0", "time hidden")
        oexpect(r.get("titleVis") == "0", "title hidden")
        oexpect(r.get("bodyVis") == "0", "body hidden")
        if num(r, "cardLeft") > 0:  # 顶层行才有卡片几何（组内子行在容器里）
            oexpect(abs(num(r, "cardLeft") - dp(16)) <= 2,
                    "card left=%s" % r.get("cardLeft"))
            oexpect(abs(num(r, "cardRight") - dp(16)) <= 2,
                    "card right=%s" % r.get("cardRight"))
            gap = num(r, "gapAbove", -1)
            if gap >= 0:
                oexpect(abs(gap - dp(8)) <= 2, "gapAbove=%s" % gap)
            oexpect(abs(num(r, "stackSide") - dp(16)) <= 2,
                    "stack side=%s" % r.get("stackSide"))
            oexpect(abs(num(r, "stackGap") - dp(8)) <= 2,
                    "stack gap=%s" % r.get("stackGap"))
        light = r.get("night") == "0"
        oexpect(color(r, "titleColor") == (0xde000000 if light else 0xffffffff),
                "titleColor=%s" % r.get("titleColor"))
        oexpect(color(r, "bodyColor") == (0xde000000 if light else 0xffffffff),
                "bodyColor=%s" % r.get("bodyColor"))
        oexpect(color(r, "appColor") == (0x61000000 if light else 0xb3ffffff),
                "appColor=%s" % r.get("appColor"))
        oexpect(color(r, "timeColor") == (0x61000000 if light else 0xb3ffffff),
                "timeColor=%s" % r.get("timeColor"))

    headsup_checked = 0
    for r in headsups:
        headsup_checked += 1
        density = num(r, "density", 3.0)
        font = num(r, "fontScale", 1.0)
        dp = lambda n: round(n * density)
        sp = lambda n: round(n * density * font)
        row = "headsup pkg=%s" % r.get("pkg")

        def hexpect(cond, msg):
            if not cond:
                failures.append("%s: %s" % (row, msg))

        hexpect(r.get("shown") == "1", "headsup not shown")
        hexpect(r.get("guard") == "1", "guard not installed")
        hexpect(r.get("small") == "0", "small=%s" % r.get("small"))
        hexpect(r.get("expand") == "0", "expand=%s" % r.get("expand"))
        hexpect(r.get("groupChild") == "0", "groupChild=1")
        hexpect(abs(num(r, "iconW") - dp(38)) <= 2,
                "iconW=%s expected=%s" % (r.get("iconW"), dp(38)))
        hexpect(abs(num(r, "iconH") - dp(38)) <= 2,
                "iconH=%s expected=%s" % (r.get("iconH"), dp(38)))
        hexpect(abs(num(r, "iconStart") - dp(16)) <= 2,
                "iconStart=%s expected=%s" % (r.get("iconStart"), dp(16)))
        hexpect(abs(num(r, "iconCenterDelta")) <= dp(2),
                "icon off-center=%s" % r.get("iconCenterDelta"))
        hexpect(r.get("iconDrawable") == "1", "icon no drawable")
        hexpect(abs(num(r, "headerH") - dp(34)) <= 2,
                "headerH=%s expected=%s" % (r.get("headerH"), dp(34)))
        hexpect(abs(num(r, "appStart") - dp(66)) <= 3,
                "appStart=%s expected=%s" % (r.get("appStart"), dp(66)))
        hexpect(abs(num(r, "titleStart") - dp(66)) <= 3,
                "titleStart=%s expected=%s" % (r.get("titleStart"), dp(66)))
        hexpect(abs(num(r, "bodyStart") - dp(66)) <= 3,
                "bodyStart=%s expected=%s" % (r.get("bodyStart"), dp(66)))
        hexpect(abs(num(r, "timeEndGap") - dp(16)) <= 3,
                "timeEndGap=%s expected=%s" % (r.get("timeEndGap"), dp(16)))
        hexpect(abs(num(r, "titleEndGap") - dp(16)) <= 3,
                "titleEndGap=%s expected=%s" % (r.get("titleEndGap"), dp(16)))
        hexpect(abs(num(r, "bodyEndGap") - dp(16)) <= 3,
                "bodyEndGap=%s expected=%s" % (r.get("bodyEndGap"), dp(16)))
        hexpect(abs(num(r, "appTS") - sp(12)) <= 2,
                "appTS=%s expected=%s" % (r.get("appTS"), sp(12)))
        hexpect(abs(num(r, "titleTS") - sp(15)) <= 2,
                "titleTS=%s expected=%s" % (r.get("titleTS"), sp(15)))
        hexpect(abs(num(r, "bodyTS") - sp(14)) <= 2,
                "bodyTS=%s expected=%s" % (r.get("bodyTS"), sp(14)))
        hexpect(r.get("titleMax") == "2", "titleMax=%s" % r.get("titleMax"))
        hexpect(r.get("bodyMax") == "4", "bodyMax=%s" % r.get("bodyMax"))
        hexpect(num(r, "titleLines") <= 2, "titleLines=%s" % r.get("titleLines"))
        hexpect(num(r, "bodyLines") <= 4, "bodyLines=%s" % r.get("bodyLines"))
        hexpect(r.get("timeParent") == "NotificationHeaderView",
                "time parent=%s" % r.get("timeParent"))
        hexpect(float(r.get("dividerAlpha", -1)) <= 0.01,
                "divider alpha=%s" % r.get("dividerAlpha"))
        hexpect(num(r, "nAppTimeGap") >= 8,
                "app/time overlap=%s" % r.get("nAppTimeGap"))
        hexpect(r.get("appVis") == "0", "app hidden")
        hexpect(r.get("timeVis") == "0", "time hidden")
        hexpect(r.get("titleVis") == "0", "title hidden")
        hexpect(r.get("bodyVis") == "0", "body hidden")
        light = r.get("night") == "0"
        hexpect(color(r, "titleColor") == (0xde000000 if light else 0xffffffff),
                "titleColor=%s" % r.get("titleColor"))
        hexpect(color(r, "bodyColor") == (0xde000000 if light else 0xffffffff),
                "bodyColor=%s" % r.get("bodyColor"))
        hexpect(color(r, "appColor") == (0x61000000 if light else 0xb3ffffff),
                "appColor=%s" % r.get("appColor"))
        hexpect(color(r, "timeColor") == (0x61000000 if light else 0xb3ffffff),
                "timeColor=%s" % r.get("timeColor"))

    stack_checked = 0
    stack_rows = list(by_key.values())   # 每组只断言最后一次测量

    for r in stack_rows:
        if r.get("groupExpanded") == "1":
            # 新架构下堆叠行的组展开状态必须永远是 0：展开只由卡片自绘，
            # AOSP 一旦展开就会牵动子容器/背景高度，重新产生半展开混合态
            failures.append("stack pkg=%s count=%s: AOSP expanded a stack row"
                            % (r.get("pkg"), r.get("count")))
            continue
        stack_checked += 1
        tag = "stack pkg=%s count=%s" % (r.get("pkg"), r.get("count"))

        def sexpect(ok, msg):
            if not ok:
                failures.append("%s: %s" % (tag, msg))

        density = num(r, "density", 3.0)
        count = num(r, "count")
        bars = num(r, "bars")

        if num(r, "cardExpanded") == 1:
            # 展开态：卡片自绘子行列表，行高必须等于卡高且装得下整张列表；
            # 折叠态专属的露边/徽标/单行断言此时不适用
            sexpect(num(r, "overlayH") == num(r, "rowH"),
                    "expanded overlayH=%s rowH=%s" % (r.get("overlayH"), r.get("rowH")))
            sexpect(num(r, "clH") > 0, "expanded clH=%s" % r.get("clH"))
            sexpect(num(r, "padT") + num(r, "clH") + 24 <= num(r, "overlayH"),
                    "expanded list does not fit: padT=%s clH=%s overlayH=%s"
                    % (r.get("padT"), r.get("clH"), r.get("overlayH")))
            sexpect(num(r, "kids") == count,
                    "expanded kids=%s count=%s" % (r.get("kids"), count))
            sexpect(num(r, "overlayH") > num(r, "contractedH"),
                    "expanded card not taller than collapsed: %s/%s"
                    % (r.get("overlayH"), r.get("contractedH")))
            continue
        layer = round(14 * density)          # OS6: 每层露边下移 42px
        inset = round(13 * density)          # OS6: 每层左右内缩 39px
        sexpect(count >= 1, "count=%s" % r.get("count"))
        sexpect(bars == min(max(count - 1, 0), 2), "bars=%s count=%s" % (bars, count))
        sexpect(num(r, "peekPx") == bars * layer,
                "peek=%s expected=%s" % (r.get("peekPx"), bars * layer))
        sexpect(num(r, "cardBottom") == num(r, "overlayH") - bars * layer,
                "cardBottom=%s overlayH=%s" % (r.get("cardBottom"), r.get("overlayH")))
        sexpect(num(r, "overlayH") == num(r, "rowH"),
                "overlayH=%s rowH=%s" % (r.get("overlayH"), r.get("rowH")))
        # 卡片是行高的唯一真源，原生收起根视图只是被隐藏的旧内容，不再要求等高。
        # 真正必须成立的是"卡内内容装得进卡"：之前卡内容被压到 120px，
        # 三行文字画到露边带上，就是用户看到的"变形"
        sexpect(num(r, "rcH") > 0, "rcH=%s" % r.get("rcH"))
        sexpect(num(r, "padT") + num(r, "rcH") + num(r, "padB")
                <= num(r, "overlayH") + 1,
                "content does not fit: padT=%s rcH=%s padB=%s overlayH=%s"
                % (r.get("padT"), r.get("rcH"), r.get("padB"), r.get("overlayH")))
        sexpect(num(r, "padB") >= num(r, "peekPx"),
                "padB=%s peek=%s" % (r.get("padB"), r.get("peekPx")))
        sexpect(num(r, "cardExpanded") == 0, "cardExpanded=%s" % r.get("cardExpanded"))
        sexpect(num(r, "kids") == count, "kids=%s count=%s" % (r.get("kids"), count))
        sexpect(num(r, "iconW") == round(38 * density), "iconW=%s" % r.get("iconW"))
        sexpect(num(r, "iconH") == round(38 * density), "iconH=%s" % r.get("iconH"))
        sexpect(num(r, "iconStart") == round(16 * density),
                "iconStart=%s" % r.get("iconStart"))
        sexpect(abs(num(r, "iconCenterDelta")) <= 2,
                "icon not centered in card: %s" % r.get("iconCenterDelta"))
        # 条数徽标压在图标右上角（OS6 自绘）
        sexpect(num(r, "badgeTotal") == count, "badgeTotal=%s count=%s"
                % (r.get("badgeTotal"), count))
        sexpect(num(r, "badgeR") == round(9 * density), "badgeR=%s" % r.get("badgeR"))
        sexpect(num(r, "badgeRightGap") == round(3 * density),
                "badge not on icon corner: gap=%s" % r.get("badgeRightGap"))
        sexpect(num(r, "titleStart") == round(66 * density),
                "titleStart=%s" % r.get("titleStart"))
        sexpect(num(r, "bodyStart") == round(66 * density),
                "bodyStart=%s" % r.get("bodyStart"))
        sexpect(num(r, "appStart") == round(66 * density),
                "appStart=%s" % r.get("appStart"))
        sexpect(num(r, "titleEndGap") == round(16 * density),
                "titleEndGap=%s" % r.get("titleEndGap"))
        sexpect(num(r, "bodyEndGap") == round(16 * density),
                "bodyEndGap=%s" % r.get("bodyEndGap"))
        # 堆叠卡右上角有展开箭头，时间必须让出 CHEVRON_RESERVE_DP(30dp)
        sexpect(num(r, "timeEndGap") == round(16 * density) + round(30 * density),
                "timeEndGap=%s expected=%s (16dp 边距 + 30dp 箭头预留)"
                % (r.get("timeEndGap"), round(16 * density) + round(30 * density)))
        sexpect(r.get("iconDrawable") == "1", "icon drawable missing")
        # OS6 收起态是定高单行卡：堆叠行只允许 1 行标题 + 1 行正文（展开态才回到 2/4）
        sexpect(r.get("titleMax") == "1", "titleMax=%s" % r.get("titleMax"))
        sexpect(r.get("bodyMax") == "1", "bodyMax=%s" % r.get("bodyMax"))
        sexpect(num(r, "nAppTimeGap") >= 8, "app/time overlap=%s" % r.get("nAppTimeGap"))
        light = r.get("night") == "0"
        sexpect(color(r, "titleColor") == (0xde000000 if light else 0xffffffff),
                "titleColor=%s" % r.get("titleColor"))
        sexpect(color(r, "bodyColor") == (0xde000000 if light else 0xffffffff),
                "bodyColor=%s" % r.get("bodyColor"))
        sexpect(color(r, "appColor") == (0x61000000 if light else 0xb3ffffff),
                "appColor=%s" % r.get("appColor"))
        sexpect(color(r, "timeColor") == (0x61000000 if light else 0xb3ffffff),
                "timeColor=%s" % r.get("timeColor"))

    print("checked %d contracted rows, %d overlay rows, %d headsup rows, "
          "%d stack rows, %d verifier lines"
          % (checked, overlay_checked, headsup_checked, stack_checked,
             len(rows) + len(overlays) + len(headsups) + len(stack_rows)))
    if witness_cards and not stack_checked:
        # 屏上有堆叠卡但一条堆叠测量都没断言到，这不是"通过"，是没看到
        print("FAIL: %d stack cards on screen (witness) but 0 verified "
              "- bump betteroriginos_notify_reverify and retry"
              % len(witness_cards))
        sys.exit(1)
    # 面板收起时"放弃测量"是正当的：同一组后来真被测到过就不算覆盖漏洞
    measured_keys = set((r.get("pkg"), r.get("count")) for r in by_key.values())
    unmeasured_skips = [e for e in skipped_stack
                        if (parse_kv(e).get("pkg"), parse_kv(e).get("count"))
                        not in measured_keys]
    # 与普通行同一条规则（之前只给普通行做了区分，堆叠路径漏了，于是长列表必然误报）：
    #   shown=1 却测不到 = 我们的管线在可见行上失灵 -> FAIL
    #   shown=0        = 那一组根本不在可视区（AOSP 会把超出屏幕的行停在列表底部并置
    #                    INVISIBLE，实测 15 个子行里只有前 5 行有独立 ty）-> 覆盖率警告
    stack_giveups = [e for e in unmeasured_skips if parse_kv(e).get("shown") == "1"]
    stack_offscreen = [e for e in unmeasured_skips if parse_kv(e).get("shown") != "1"]
    if stack_giveups:
        # 放弃了屏上堆叠行的测量又没有补测：别让它伪装成"这里没有堆叠行"
        print("FAIL: module skipped %d stack measurements on VISIBLE rows with no later "
              "success: %s" % (len(stack_giveups), stack_giveups[-1]))
        sys.exit(1)
    if stack_offscreen:
        warnings.append("stack measurements skipped while off screen (%d, e.g. %s)"
                        % (len(stack_offscreen), stack_offscreen[-1]))
    # 普通行的"放弃"分两种，只有一种是缺陷：
    #   shown=1 却测不到 = 我们的测量管线在可见行上失灵 → FAIL
    #   shown=0        = 样本根本不在可视区（列表很长），是覆盖率事实 → 警告
    # 必须在下面打印 warnings 的循环之前算，否则警告永远看不见（就犯过这个错）
    measured_keys = {(r.get("pkg"), r.get("style")) for r in rows}
    unmeasured = [s for s in skipped_base
                  if (s.get("pkg"), s.get("style")) not in measured_keys]
    visible_giveups = [s for s in unmeasured if s.get("shown") == "1"]
    offscreen = [s for s in unmeasured if s.get("shown") != "1"]
    if visible_giveups:
        print("FAIL: module gave up on %d VISIBLE rows: %s"
              % (len(visible_giveups),
                 sorted({(s.get("pkg"), s.get("style")) for s in visible_giveups})))
        sys.exit(1)
    if offscreen:
        warnings.append("%d row measurements skipped because the row was off screen "
                        "(shown=0): %s" % (len(offscreen),
                                           sorted({(s.get("pkg"), s.get("style"))
                                                   for s in offscreen})))
    for w in warnings:
        print("WARN:", w)
    if failures:
        print("\nFAIL (%d):" % len(failures))
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print("PASS: all code-level checks passed")
    sys.exit(0)


if __name__ == "__main__":
    main()
