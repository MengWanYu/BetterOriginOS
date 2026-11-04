#!/usr/bin/env python3
"""A11 资源解析器：把 aapt dump resources 的产物变成"引用 → 名字 → 真实值"的两向查询。

为什么需要它：A11 的 layout/代码里全是 0x0105xxxx/0x0102xxxx 这类裸 id，
只看布局会得到"大概 16dp"这种弱结论（skill §6 的教训）。这里一次性建索引，
之后每条断言都能指回具体资源名与数值。

用法：
  python work/a11_res.py --dump work/y7s-a11/fw_res.txt --name 0x01020436 0x010501a9
  python work/a11_res.py --dump work/y7s-a11/fw_res.txt --find dimen/notification_art_size
  python work/a11_res.py --dump work/y7s-a11/fw_res.txt --grep notification
"""
import argparse
import io
import re
import sys

RES_RE = re.compile(r"^\s*resource (0x[0-9a-f]{8}) (\S+)(.*)$")


def load(path):
    """返回 {id: (name, [body 行])}，body 是 resource 之后的缩进行（含各配置下的值）。"""
    out = {}
    cur = None
    for line in io.open(path, encoding="utf-8", errors="replace"):
        m = RES_RE.match(line.rstrip("\n"))
        if m:
            cur = m.group(1)
            out[cur] = (m.group(2), [line.strip()])
        elif cur is not None:
            if RES_RE.match(line.rstrip("\n")):
                cur = None
            elif line.startswith("      ") or line.startswith("        "):
                out[cur][1].append(line.strip())
            else:
                cur = None
    return out


def values(entry, limit=6):
    """挑出真正带值的行（(default)/密度/夜间等配置），跳过 PURE/MIXED 之类的声明。"""
    keep = []
    for t in entry[1][1:]:
        if t.startswith("(") and ")" in t:
            keep.append(t)
        if len(keep) >= limit:
            break
    return keep


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", required=True)
    ap.add_argument("--name", nargs="*", default=[])
    ap.add_argument("--find", nargs="*", default=[], help="按 类型/名字 精确查（含值）")
    ap.add_argument("--grep", help="按名字子串列出所有资源 id")
    a = ap.parse_args()
    idx = load(a.dump)
    by_name = {}
    for rid, (nm, _) in idx.items():
        by_name.setdefault(nm, []).append(rid)

    rc = 0
    for h in a.name:
        e = idx.get(h if len(h) == 10 else h)
        if not e:
            print("%s -> 查不到（这份 dump 里没有）" % h)
            rc = 1
            continue
        vs = values(e)
        print("%s = %s%s" % (h, e[0], ("   " + " | ".join(vs)) if vs else ""))
    for nm in a.find:
        ids = by_name.get(nm)
        if not ids:
            print("%s -> 查不到" % nm)
            rc = 1
            continue
        for rid in ids:
            vs = values(idx[rid])
            print("%s %s = %s%s" % (rid, nm, idx[rid][0],
                                    ("   " + " | ".join(vs)) if vs else ""))
    if a.grep:
        for rid, (nm, _) in sorted(idx.items(), key=lambda kv: kv[1][0]):
            if a.grep.lower() in nm.lower():
                print("%s %s" % (rid, nm))
    return rc


if __name__ == "__main__":
    sys.exit(main())
