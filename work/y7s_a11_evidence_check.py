#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Y7s(A11) 音乐卡的实机取证核对。

用途：A11 卡的重建全靠这几条"设备实读"的前提，前提错了整张卡就错。
本脚本把 work/y7s-a11/notif-playing.txt（正在放歌时的 dumpsys notification --noredact）
和 work/y7s-a11/fw_res.txt（aapt2 dump framework-res.apk）里的关键事实固化成断言，
不需要连设备就能复核；--selftest 用篡改副本证明每条断言真的会红。

证据链对应 out/LOG-1102-A11-MEDIACARD-RE.md §9 的缺口清单。
"""
import io
import os
import re
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
NOTIF = os.path.join(BASE, "y7s-a11", "notif-playing.txt")
FWRES = os.path.join(BASE, "y7s-a11", "fw_res.txt")
MEDIA_VIEW = os.path.join(BASE, "y7s-a11", "xml_media_view.txt")
# SystemUI 自己的资源表：media_view.xml 里引用的 @0x7f07xxxx / @0x7f06xxxx 要靠它解出真实数值
SYUI_RES = os.path.join(BASE, "y7s-a11", "sysui_res.txt")

# A11MediaSpec 的展开态常量，必须与 Y7s 资源表实读一致
SPEC_ART_EXPANDED_DP = 52
SPEC_SEEK_TRACK_DP = 3
SPEC_SEEK_DISABLED_DP = 1
SPEC_SOURCE_ICON_DP = 16
SPEC_TITLE_SP = 16.0
SPEC_SUBTEXT_SP = 14.0
SPEC_TEXT_SECONDARY = "#99ffffff"
SPEC_SEEK_PROGRESS = "#c0ffffff"
SPEC_SEEK_TRACK = "#80ffffff"

# 模块里写死的规格常量，必须与设备实读一致
SPEC_ART_COLLAPSED_DP = 36      # A11MediaSpec.ART_COLLAPSED_DP
SPEC_ART_RADIUS_DP = 2          # A11MediaSpec.ART_RADIUS_DP
SPEC_BTN = [48, 48, 52, 48, 48]  # A11MediaSpec.BUTTON_DP / BUTTON_CENTER_DP

# 收起态（MediaStyle 模板）的文字与动作带：**数值和单位都要跟设备一致**。
# 单位尤其关键：A11 的副文是 12**dp**（不跟 fontScale），标题才是 14**sp**；
# 把副文当 sp 套，只在大字号机器上才会比系统大一档（第四轮审查抓到的那条）。
SPEC_COLLAPSED_TITLE_SP = 14.0
SPEC_COLLAPSED_SUBTEXT_DP = 12.0
SPEC_ACTION_LIST_HEIGHT_DP = 60.0
SPEC_JAVA = os.path.join(os.path.dirname(BASE), "module", "src", "com", "betteroriginos",
                         "feature", "media", "A11MediaSpec.java")


def media_record(text):
    """取出 bbkmusic 那条 MediaStyle 通知的整段记录。"""
    blocks = re.split(r"\n(?=    NotificationRecord\()", text)
    for b in blocks:
        head = b.split("\n", 1)[0]
        if "com.android.bbkmusic" in head and "id=10001" in head:
            return b
    return ""


def check(notif_text, fw_text, mv_text, res_text=""):
    fails, passed = [], []
    rec = media_record(notif_text)
    if not rec:
        return passed, ["取证缺失: notif-playing.txt 里找不到 bbkmusic id=10001 的记录"]

    def expect(cond, label, detail=""):
        if cond:
            passed.append("%s %s" % (label, detail))
        else:
            fails.append("%s: %s" % (label, detail))

    head = rec.split("\n", 1)[0]
    expect("category=transport" in head, "category", "transport")
    n_actions = len(re.findall(r'^\s+\[\d+\] "', rec, re.M))
    expect(n_actions == 5, "actions 数量", "期望 5 实得 %d" % n_actions)

    tpl = re.search(r"android\.template=String \(([^)]*)\)", rec)
    expect(bool(tpl) and tpl.group(1) == "android.app.Notification$MediaStyle",
           "template", tpl.group(1) if tpl else "没有 android.template")

    colorized = re.search(r"mColorized=(\w+)", rec)
    expect(bool(colorized) and colorized.group(1) == "true",
           "isColorizedMedia", colorized.group(1) if colorized else "没有 mColorized")

    art = re.search(r"android\.largeIcon=Icon \(Icon\(typ=(\w+) size=(\d+)x(\d+)\)\)", rec)
    expect(bool(art) and art.group(1) == "BITMAP", "封面类型",
           art.group(1) if art else "没有 largeIcon")
    if art:
        # 200x200 是"下发尺寸"；A11 的 MediaNotificationProcessor 会按
        # RESIZE_BITMAP_AREA=22500 折到 150x150 再做取色，这条是取色输入尺寸的前提
        expect(int(art.group(2)) == 200 and int(art.group(3)) == 200,
               "封面下发尺寸", "期望 200x200 实得 %sx%s" % (art.group(2), art.group(3)))

    compact = re.findall(r"^\s+\[\d+\] (\d+)$", rec, re.M)
    expect(compact == ["2", "3", "0"], "compactActions",
           "期望 [2,3,0] 实得 %s" % (compact or "无"))

    # framework 实读 dimen，决定收起态封面尺寸与圆角
    def dimen(name):
        m = re.search(r"resource 0x[0-9a-f]+ dimen/%s[^\n]*\n\s+\(\) ([\d.]+)dp"
                      % re.escape(name), fw_text)
        return float(m.group(1)) if m else None

    def dimen_and_unit(name):
        """从 fw_res 的 aapt2 文本里读 `dimen/<name>` 的**数值与单位**（sp 还是 dp 是关键信息）。

        不用正则：这里要跨行匹配，反斜杠经过脚本层层转义太容易写坏（今晚踩过两次）。
        """
        lines = fw_text.splitlines()
        for i, ln in enumerate(lines):
            stripped = ln.strip()
            if stripped.startswith("resource ") and ("dimen/" + name) in stripped:
                for nxt in lines[i + 1:i + 3]:
                    v = nxt.strip()
                    if v.startswith("()"):
                        val = v[2:].strip()
                        for unit in ("sp", "dp"):
                            if val.endswith(unit):
                                return (float(val[: -len(unit)]), unit)
                        return (float(val), "?")
        return (None, None)

    def spec_constant(name):
        """模块里写死的常量，必须与设备实读对上（改了模块不改证据 -> 这里红）。"""
        try:
            src = io.open(SPEC_JAVA, encoding="utf-8", errors="replace").read()
        except OSError:
            return None
        m = re.search(r"%s\s*=\s*([\d.]+)f?;" % re.escape(name), src)
        return float(m.group(1)) if m else None

    title_sz = dimen_and_unit("notification_title_text_size")
    sub_sz = dimen_and_unit("notification_subtext_size")
    action_h = dimen_and_unit("notification_action_list_height")
    expect(title_sz == (SPEC_COLLAPSED_TITLE_SP, "sp"), "notification_title_text_size",
           "期望 14.0sp 实得 %s" % (title_sz,))
    expect(sub_sz == (SPEC_COLLAPSED_SUBTEXT_DP, "dp"), "notification_subtext_size",
           "期望 12.0dp 实得 %s（单位必须是 dp：dp 不跟 fontScale）" % (sub_sz,))
    expect(action_h == (SPEC_ACTION_LIST_HEIGHT_DP, "dp"), "notification_action_list_height",
           "期望 60.0dp 实得 %s" % (action_h,))
    # 模块侧的三枚常量与设备一致（防止"改了实现忘了证据"或反过来）
    expect(spec_constant("COLLAPSED_TITLE_SP") == SPEC_COLLAPSED_TITLE_SP,
           "模块 COLLAPSED_TITLE_SP", "实得 %s 期望 %s"
           % (spec_constant("COLLAPSED_TITLE_SP"), SPEC_COLLAPSED_TITLE_SP))
    expect(spec_constant("COLLAPSED_SUBTEXT_DP") == SPEC_COLLAPSED_SUBTEXT_DP,
           "模块 COLLAPSED_SUBTEXT_DP", "实得 %s 期望 %s"
           % (spec_constant("COLLAPSED_SUBTEXT_DP"), SPEC_COLLAPSED_SUBTEXT_DP))
    expect(spec_constant("ACTION_LIST_HEIGHT_DP") == SPEC_ACTION_LIST_HEIGHT_DP,
           "模块 ACTION_LIST_HEIGHT_DP", "实得 %s 期望 %s"
           % (spec_constant("ACTION_LIST_HEIGHT_DP"), SPEC_ACTION_LIST_HEIGHT_DP))

    right = dimen("notification_right_icon_size")
    expect(right == SPEC_ART_COLLAPSED_DP, "notification_right_icon_size",
           "期望 %s dp 实得 %s" % (SPEC_ART_COLLAPSED_DP, right))
    corner = dimen("config_dialogCornerRadius")
    expect(corner == SPEC_ART_RADIUS_DP, "config_dialogCornerRadius",
           "期望 %s dp 实得 %s" % (SPEC_ART_RADIUS_DP, corner))
    # SystemUI 用 Theme.DeviceDefault*，其 dialogCornerRadius 必须指向同一 dimen，
    # 否则 A11 的封面圆角就不是 2dp（Theme/Theme.Holo 是 0dp，不是我们要跟的那套）
    dd = re.search(r"resource 0x[0-9a-f]+ style/Theme\.DeviceDefault\.NoActionBar[^\n]*\n"
                   r"(?:[^\n]*\n){0,40}?.*?dialogCornerRadius\(0x01010571\)=([^\n]+)", fw_text)
    expect(bool(dd) and "config_dialogCornerRadius" in dd.group(1),
           "DeviceDefault 的圆角来源", dd.group(1).strip() if dd else "没找到")

    # 展开态控制排：media_view.xml 反编译出来的 5 枚按钮尺寸。
    # 布局里在按钮排之前还有 0dp 的占位/分割线节点，所以按"顺序子序列"匹配，
    # 而不是取前五个宽度值
    sizes = [int(float(x)) for x in re.findall(
        r"layout_width\(0x010100f4\)=([\d.]+)dp", mv_text)]

    def contains_seq(seq, target):
        return any(seq[i:i + len(target)] == target for i in range(len(seq) - len(target) + 1))

    expect(contains_seq(sizes, SPEC_BTN), "按钮排布",
           "期望按序出现 %s，实得宽度序列 %s" % (SPEC_BTN, sizes))

    # 展开态排版：media_view.xml 引用的是 SystemUI 自己的 dimen/color，
    # 这里把 id 背后的真实值解出来核对，别停留在"布局里看着像"
    def res_dimen(name):
        m = re.search(r"resource 0x[0-9a-f]+ dimen/" + re.escape(name)
                      + r"[^\n]*\n\s+\(\) ([\d.]+)dp", res_text)
        return float(m.group(1)) if m else None

    def res_color(name):
        m = re.search(r"resource 0x[0-9a-f]+ color/" + re.escape(name)
                      + r"[^\n]*\n\s+\(\) (\S+)", res_text)
        return m.group(1).lower() if m else None

    if res_text:
        for name, want in (("qs_media_album_size", SPEC_ART_EXPANDED_DP),
                           ("qs_media_enabled_seekbar_height", SPEC_SEEK_TRACK_DP),
                           ("qs_media_disabled_seekbar_height", SPEC_SEEK_DISABLED_DP),
                           ("qs_media_icon_size", SPEC_SOURCE_ICON_DP)):
            got = res_dimen(name)
            if got != float(want):
                fails.append("A11 展开态 dimen %s 期望 %s dp 实得 %s" % (name, want, got))
            else:
                passed.append("A11dimen %s=%s dp" % (name, want))
        for name, want in (("media_secondary_text", SPEC_TEXT_SECONDARY),
                           ("media_seekbar_progress", SPEC_SEEK_PROGRESS),
                           ("media_disabled", SPEC_SEEK_TRACK)):
            got = res_color(name)
            if got != want:
                fails.append("A11 展开态颜色 %s 期望 %s 实得 %s" % (name, want, got))
            else:
                passed.append("A11color %s=%s" % (name, want))
        n16 = len(re.findall(r"textSize\(0x01010095\)=16\.000000sp", mv_text))
        n14 = len(re.findall(r"textSize\(0x01010095\)=14\.000000sp", mv_text))
        if n16 != 1 or n14 < 2:
            fails.append("A11 展开态字号节点数不符：期望 1 个 16sp + >=2 个 14sp，实得 %d/%d"
                         % (n16, n14))
        else:
            passed.append("A11font 标题 16sp ×1，副文/时间 14sp ×%d" % n14)
    return passed, fails


GOOD_NOTIF = None


def _re_dimen(fw_text, name, new_value):
    """把某个 dimen 的默认值改掉，用来验证断言真的会红（别用字面 replace：
    缩进一变就静默不匹配，反例就成了假绿）"""
    return re.sub(r"(dimen/" + re.escape(name) + r"[^\n]*\n\s+\(\) )[\d.]+dp",
                  r"\g<1>" + new_value + "dp", fw_text, count=1)


def _re_dimen_any(fw_text, name, new_value, unit):
    """同上，但适用于 **sp** 单位，也能只改单位不改数值。
    单位本身就是断言的一部分（副文 12dp vs 标题 14sp），所以必须有这条。"""
    return re.sub(r"(dimen/" + re.escape(name) + r"[^\n]*\n\s+\(\) )[\d.]+(?:sp|dp)",
                  lambda mm: mm.group(1) + new_value + unit, fw_text, count=1)


def _re_res_dimen(res_text, name, new_value):
    """改 SystemUI 资源表里某个 dimen 的默认值（反例专用）"""
    return re.sub(r"(dimen/" + re.escape(name) + r"[^\n]*\n\s+\(\) )[\d.]+dp",
                  r"\g<1>" + new_value + "dp", res_text, count=1)


def _re_res_color(res_text, name, new_value):
    return re.sub(r"(color/" + re.escape(name) + r"[^\n]*\n\s+\(\) )\S+",
                  r"\g<1>" + new_value, res_text, count=1)


def selftest():
    notif = io.open(NOTIF, encoding="utf-8", errors="replace").read()
    fw = io.open(FWRES, encoding="utf-8", errors="replace").read()
    mv = io.open(MEDIA_VIEW, encoding="utf-8", errors="replace").read()
    res = io.open(SYUI_RES, encoding="utf-8", errors="replace").read()
    _, fails = check(notif, fw, mv, res)
    if fails:
        print("SELFTEST FAIL: 真实取证被判红 ->")
        for f in fails:
            print("   ", f)
        return 1
    print("SELFTEST: 真实取证通过")
    tamper = [
        ("template 不是 MediaStyle",
         notif.replace("android.app.Notification$MediaStyle",
                       "android.app.Notification$DecoratedCustomViewStyle"), fw, mv, res),
        ("通知没被上色（mColorized=false）", notif.replace("mColorized=true",
                                                    "mColorized=false"), fw, mv, res),
        ("封面下发尺寸变了", notif.replace("size=200x200", "size=512x512"), fw, mv, res),
        ("少了一枚动作按钮", re.sub(r'^\s+\[4\] "[^\n]*\n', "", notif, flags=re.M),
         fw, mv, res),
        ("收起态封面不是 36dp", notif,
         _re_dimen(fw, "notification_right_icon_size", "40.000000"), mv, res),
        ("封面圆角不是 2dp", notif,
         _re_dimen(fw, "config_dialogCornerRadius", "24.000000"), mv, res),
        ("按钮排布变了", notif, fw, mv.replace("=52.000000dp", "=60.000000dp"), res),
        ("副文单位其实是 sp 不是 dp", notif,
         _re_dimen_any(fw, "notification_subtext_size", "12.000000", "sp"), mv, res),
        ("副文数值其实是 14dp 不是 12dp", notif,
         _re_dimen_any(fw, "notification_subtext_size", "14.000000", "dp"), mv, res),
        ("收起态标题不是 14sp", notif,
         _re_dimen_any(fw, "notification_title_text_size", "16.000000", "sp"), mv, res),
        ("动作带高度不是 60dp", notif,
         _re_dimen_any(fw, "notification_action_list_height", "48.000000", "dp"), mv, res),
        ("展开态封面不是 52dp", notif, fw, mv,
         _re_res_dimen(res, "qs_media_album_size", "64.000000")),
        ("进度条轨道不是 3dp", notif, fw, mv,
         _re_res_dimen(res, "qs_media_enabled_seekbar_height", "6.000000")),
        ("次要文字白色阶不对", notif, fw, mv,
         _re_res_color(res, "media_secondary_text", "#60ffffff")),
    ]
    bad = 0
    originals = (notif, fw, mv, res)
    for name, n, f, m, r in tamper:
        tampered = (n, f, m, r)
        changed = [i for i in range(4) if tampered[i] != originals[i]]
        if len(changed) != 1:
            print("SELFTEST FAIL: 反例「%s」篡改了 %d 份材料（应为 1 份，"
                  "否则红了也说不清是谁的作用）" % (name, len(changed)))
            bad += 1
            continue
        _, fails = check(n, f, m, r)
        if not fails:
            print("SELFTEST FAIL: 反例「%s」没被抓住" % name)
            bad += 1
        else:
            print("SELFTEST: 反例「%s」正确报红 -> %s" % (name, fails[0]))
    return 1 if bad else 0


def main(argv):
    if "--selftest" in argv:
        return selftest()
    for path in (NOTIF, FWRES, MEDIA_VIEW, SYUI_RES):
        if not os.path.exists(path):
            print("取证文件缺失:", path)
            return 2
    notif = io.open(NOTIF, encoding="utf-8", errors="replace").read()
    fw = io.open(FWRES, encoding="utf-8", errors="replace").read()
    mv = io.open(MEDIA_VIEW, encoding="utf-8", errors="replace").read()
    res = io.open(SYUI_RES, encoding="utf-8", errors="replace").read()
    passed, fails = check(notif, fw, mv, res)
    for p in passed:
        print("  ok   " + p)
    for f in fails:
        print("  FAIL " + f)
    print("A11 取证核对: %d 通过 / %d 失败" % (len(passed), len(fails)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
