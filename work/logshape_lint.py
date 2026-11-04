#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""日志形状自检：验证器里每条正则的"字面锚点"必须真的存在于模块源码的日志串里。

为什么要这个：今晚发现 `verify_os5_mirror.py` 里匹配 `a11 palette base=` 的正则，
而代码早就改成了 `a11 palette expanded base=` —— 正例靠旧 fixture 自洽地绿着，
真日志却一条都解析不到。**验证器绿 ≠ 它在检查真机上的东西**。

做法：把每条正则第一个捕获组之前的固定文本当锚点，去源码里找；找不到就是分叉。
锚点至少 8 个字符（太短的固定串没区分度，跳过）。

用法：python work/logshape_lint.py
"""
import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
VERIFIER = os.path.join(ROOT, "work", "verify_os5_mirror.py")
SRC_FILES = [
    os.path.join(ROOT, "module/src/com/betteroriginos/feature/notify/Os5Mirror.java"),
    os.path.join(ROOT, "module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java"),
    os.path.join(ROOT, "module/src/com/betteroriginos/feature/notify/NotifyRowSpec.java"),
    os.path.join(ROOT, "module/src/com/betteroriginos/feature/media/MediaCardFeature.java"),
]
MIN_ANCHOR = 8

# 第二相：有些验证器不按 re.compile 匹配日志，而是直接 `if "notify_row.witness" in line`。
# 这类纯子串标记同样会分叉（模块改了措辞 -> 断言静默不跑），但 re.compile 那套扫不到。
# 只认以这些前缀开头的字面量，别的（文件路径、正则片段）不参与。
MARKER_VERIFIERS = ["work/verify_notify.py", "work/y7s_a11_evidence_check.py"]


def literal_markers(text):
    """验证器里**用来匹配日志**的字面量：只认出现在 `in line` / `in text`  membership 测试里的。

    一开始我按"以 notify_row 之类前缀开头"来筛，结果把验证器**自己的报告文案**
    （`"stack gap=%s expected=%s"`）也当成标记，报了 7 条假分叉 ——
    带 %s/%d 的是打印串，不是日志匹配；而真正的标记一定写成 `if "..." in line`。
    """
    out = set()
    for m in re.finditer(r'"([^"\n]{8,})"\s+in\s+(?:line|text|l)\b', text):
        out.add(m.group(1))
    for m in re.finditer(r'(?:elif|if)\s+([A-Z_]+)\s+in\s+(?:line|text)\b', text):
        # 常量型标记（TAG / STACK_TAG）：值就在源码里定义，单独取出来查
        const = m.group(1)
        dm = re.search(r'^%s\s*=\s*"([^"\n]+)"' % re.escape(const), text, re.M)
        if dm:
            out.add(dm.group(1))
    return out



def regex_sources(text):
    """从验证器源码里抠出所有 re.compile(...) 的字符串字面量（含拼接）。"""
    out = []
    for m in re.finditer(r"re\.compile\(\s*(.*?)\)\n", text, re.S):
        chunk = m.group(1)
        for lit in re.findall(r'r?"((?:[^"\\]|\\.)*)"', chunk):
            out.append(lit)
    return out


def anchor(pattern):
    """第一个捕获组之前的固定文本，去掉转义与字符类，截到最后一个自然断点。"""
    head = re.split(r"\(", pattern, maxsplit=1)[0]
    head = head.replace("\\.", ".").replace("\\d", "").replace("\\w", "")
    head = re.sub(r"\[[^\]]*\]", "", head)
    # 只保留连续的可 grep 字面部分（后面跟的是量词/通配就到此为止）
    head = re.split(r"[\s]*[+*?{$^]", head, maxsplit=1)[0]
    return head.strip()


def marker_phase(srcs):
    """第二相：纯子串型验证器的日志标记也必须真的存在于模块源码里。"""
    bad, checked = [], 0
    for rel in MARKER_VERIFIERS:
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            continue
        for lit in sorted(literal_markers(io.open(path, encoding="utf-8",
                                                 errors="replace").read())):
            checked += 1
            if lit not in srcs:
                bad.append("%s: %s" % (rel, lit))
    return checked, bad


def main(argv=()):
    verifier = VERIFIER
    for i, a in enumerate(argv):
        if a == "--verifier" and i + 1 < len(argv):
            verifier = argv[i + 1]
    ver = io.open(verifier, encoding="utf-8", errors="replace").read()
    srcs = "".join(io.open(p, encoding="utf-8", errors="replace").read()
                   for p in SRC_FILES)
    # 自检：故意用一个"源码里不存在的标记"跑一遍，标记检查必须报红，
    # 否则第二相本身就是一条不会亮的闸（今晚正反例咬不住的老毛病）。
    if "--selftest" in argv:
        checked, bad = marker_phase('notify_row: witness card=1\nos5_mirror: shelf gap=')
        print("标记检查扫到 %d 条" % checked)
        if not bad:
            print("SELFTEST FAIL: 空源码里一条标记都没报红（检查器形同虚设）")
            return 1
        print("SELFTEST: 标记检查有效（%d 条报红）" % len(bad))
        return 0
    # 源码里日志是拼接的，所以按引号内片段逐条比对更靠得住
    frags = set()
    for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', srcs):
        frags.add(m.group(1))
    fails = []
    checked = 0
    for pat in regex_sources(ver):
        a = anchor(pat)
        if len(a) < MIN_ANCHOR:
            continue
        checked += 1
        # 锚点允许出现在任一引号片段里（拼接后相邻即可）
        hit = any(a in f for f in frags)
        if not hit:
            # 再试"锚点跨片段"的情况：把源码所有片段顺序拼一遍找子串
            joined = "".join(sorted(frags))
            hit = a in joined or a in srcs
        if not hit:
            fails.append(a)
    print("检查 %d 条正则锚点" % checked)
    for a in fails:
        print("  FAIL 验证器在匹配 %r，但模块源码里没有这段日志文本（分叉或已改名）" % a)
    m_checked, m_bad = marker_phase(srcs)
    print("检查 %d 条纯子串日志标记（verify_notify / y7s 取证）" % m_checked)
    for b in m_bad:
        print("  FAIL 验证器在读 %s，但模块源码里没有这段日志文本" % b)
    if fails or m_bad:
        print("日志形状自检：有 %d 条分叉（正则 %d / 标记 %d）"
              % (len(fails) + len(m_bad), len(fails), len(m_bad)))
        return 1
    print("日志形状自检：验证器匹配的每条日志都在源码里存在")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
