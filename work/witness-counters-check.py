#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""见证计数器核对：出现在日志里的统计量，必须在某处真的自增过。

起因（真机第一次跑）：落点自检打的是 `landing reachable radius=0 g2=3 shelf=2 clear=0`，
`clear` 那条是 `sClearAttaches` —— 字段声明了、日志里也打了，**但全文件没有任何一处 ++**。
于是"补挂从未发生"和"补挂发生了 5 次"看起来一模一样，见证等于没有。
这条检查就是为了让这类字段活不过离线阶段。

用法：
    python work/witness-counters-check.py                # 核对模块源码
    python work/witness-counters-check.py --selftest     # 喂一份"故意漏自增"的源码，必须报红
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 只看模块里真正打进日志的统计字段
SRC_FILES = [
    os.path.join(ROOT, "module", "src", "com", "betteroriginos", "feature", "notify",
                 "Os5Mirror.java"),
    os.path.join(ROOT, "module", "src", "com", "betteroriginos", "feature", "notify",
                 "NotifyRowFeature.java"),
    os.path.join(ROOT, "module", "src", "com", "betteroriginos", "feature", "media",
                 "MediaCardFeature.java"),
]

DECL = re.compile(r"\b(?:static|private static|public static)\s+(?:volatile\s+)?"
                  r"(?:int|long|Integer|Long)\s+(s[A-Za-z0-9_]+)\s*(?:=[^;]*)?;")


def check_files(paths):
    """返回 [(文件, 字段, 说明)]：出现在日志拼接里但从未被写的计数器。"""
    bad = []
    for p in paths:
        if not os.path.exists(p):
            bad.append((os.path.basename(p), "-", "源码文件不存在（前提漂移）"))
            continue
        text = io.open(p, encoding="utf-8", errors="replace").read()
        lines = text.splitlines()
        # 日志行：只看 `Log.i(...)` 那几行的拼接（跨行合并后判断）
        log_blob = "\n".join(l for l in lines if "Log." in l or "+" in l)
        for m in DECL.finditer(text):
            name = m.group(1)
            if not re.search(r'"[^"]*"\s*\+\s*' + name + r'\b', log_blob) \
                    and re.search(r'\+\s*' + name + r'\b', log_blob) is None:
                continue      # 没进过日志，不算见证
            # 去掉声明行后，看是否还有任何写操作（前缀 ++/-- 也是合法自增）
            writes = [l for l in lines if l.strip() != m.group(0).strip()
                      and re.search(r"(\+\+|--)\s*\b" + name + r"\b"
                                    r"|\b" + name + r"\b\s*(\+\+|--|\+=|-=|=[^=])", l)]
            if not writes:
                bad.append((os.path.basename(p), name, "打进日志却从未自增/赋值"))
    return bad


def selftest():
    """构造一份"计数器只读不写"的源码，检查器必须抓到；正常那份不能误报。"""
    tmp = os.path.join(ROOT, "work", "tmp-witness-selftest.java")
    io.open(tmp, "w", encoding="utf-8").write(
        "class A {\n"
        "    private static int sGhost;\n"
        "    private static int sReal;\n"
        "    void f() {\n"
        "        sReal++;\n"
        "        Log.i(TAG, \"landing g2=\" + sG2 + \" ghost=\" + sGhost"
        " + \" real=\" + sReal);\n"
        "    }\n"
        "}\n")
    bad = check_files([tmp])
    os.remove(tmp)
    names = set(n for _, n, _ in bad)
    if "sGhost" not in names:
        print("SELFTEST FAIL: 漏自增的见证计数器没被抓出来（应报 sGhost）")
        return 1
    if "sReal" in names:
        print("SELFTEST FAIL: 正常自增的计数器被误报（sReal）")
        return 1
    print("SELFTEST: 漏自增会被抓、正常不误报（命中 %s）" % ",".join(sorted(names)))
    return 0


def main(argv):
    if "--selftest" in argv:
        return selftest()
    bad = check_files(SRC_FILES)
    if bad:
        for f, n, why in bad:
            print("FAIL %s: %s %s" % (f, n, why))
        print("见证计数器核对：有 %d 个字段是假见证" % len(bad))
        return 1
    print("见证计数器核对：打进日志的统计量都有真实写入点")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
