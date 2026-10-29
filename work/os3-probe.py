#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抽取模块源码里的 Xposed 目标 (类 -> 方法/字段)，对照 OS3 dexdump 核对可达性。
用法: python os3-probe.py <src_root> <dexdump_file> <out_md>
"""
import re, sys, collections, pathlib

SRC = pathlib.Path(sys.argv[1])
DEXDUMP = pathlib.Path(sys.argv[2])
OUT = pathlib.Path(sys.argv[3])

FILES = ["feature/notify/NotifyRowFeature.java", "feature/media/MediaCardFeature.java",
         "feature/notify/IosNotificationRow.java", "hook/MainHook.java",
         "feature/notify/NotifyRowSpec.java", "core/FeatureManager.java",
         "feature/media/QsPlayerTile.java",
         "feature/picker/PickerAlbumsFeature.java",
         "feature/fileman/FileManagerRenameFeature.java"]

WRAP_PREFIX = "com.android.systemui.statusbar.notification.row.wrapper."

def strip_comments(t):
    t = re.sub(r'/\*.*?\*/', ' ', t, flags=re.S)
    t = re.sub(r'//[^\n]*', ' ', t)
    return t

def scan(path):
    per_class = collections.defaultdict(set)
    fields, calls = set(), set()
    varmap = {}
    text = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
    text = re.sub(r'\s+', ' ', text)
    for stmt in text.split(';'):
        for m in re.finditer(r'Class<\?>\s+(\w+)\s*=\s*XposedHelpers\.findClass(?:IfExists)?\(\s*"([\w.$]+)"', stmt):
            varmap[m.group(1)] = m.group(2)
        for m in re.finditer(r'hookAllMethods\(\s*(\w+)\s*,\s*"(\w+)"', stmt):
            cls = varmap.get(m.group(1))
            if cls:
                per_class[cls].add(m.group(2))
        for m in re.finditer(r'findAndHookMethod\(\s*"([\w.$]+)"\s*,\s*"(\w+)"', stmt):
            per_class[m.group(1)].add(m.group(2))
        for m in re.finditer(r'hookAllConstructors\(\s*(\w+)', stmt):
            cls = varmap.get(m.group(1))
            if cls:
                per_class[cls].add("<init>")
        # 裸类名常量（findClass 参数、或数组里的全限定名）
        for m in re.finditer(r'"((?:com\.android\.systemui|com\.vivo|android|androidx)\.[\w.$]+)"', stmt):
            cls = m.group(1)
            if cls.count(".") >= 3 and cls[-1].islower():
                per_class[cls]  # touch，确保进入清单
        for m in re.finditer(r'wrappers\s*=\s*\{(.*)\}', stmt):
            for nm in re.findall(r'"(\w+)"', m.group(1)):
                per_class[WRAP_PREFIX + nm].add("onContentUpdated")
        for m in re.finditer(r'get(?:Object|Int|Boolean|Long|Float)Field\(\s*[^,]+,\s*"(\w+)"', stmt):
            fields.add(m.group(1))
        for m in re.finditer(r'callMethod\(\s*\w+\s*,\s*"(\w+)"', stmt):
            calls.add(m.group(1))
        for m in re.finditer(r'callStaticMethod\(\s*[^,]+,\s*"(\w+)"', stmt):
            calls.add(m.group(1))
    return per_class, fields, calls

def load_dex(path, wanted):
    classes = {c.replace('.', '/') for c in wanted}
    out = collections.defaultdict(set)
    cur, keep = None, False
    name_re = re.compile(r"name\s*:\s*'([^']+)'")
    desc_re = re.compile(r"Class descriptor\s*:\s*'L([^;]+);'")
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            d = desc_re.search(line)
            if d:
                cur, keep = d.group(1), d.group(1) in classes
                if keep:
                    out[cur.replace('/', '.')]
                continue
            if keep:
                n = name_re.search(line)
                if n:
                    out[cur.replace('/', '.')].add(n.group(1))
    return out

def main():
    per_class = collections.defaultdict(set)
    all_fields, all_calls = set(), set()
    for rel in FILES:
        f = SRC / rel
        if not f.exists():
            continue
        pc, flds, cls_ = scan(f)
        for k, v in pc.items():
            per_class[k] |= v
        all_fields |= flds
        all_calls |= cls_

    have = load_dex(DEXDUMP, set(per_class))
    lines = ["# OS3 hook 目标可达性核对（源码 -> OS3 dexdump）", ""]
    miss_cls = miss_meth = 0
    lines.append("## 缺失类")
    for cls in sorted(per_class):
        if cls not in have:
            miss_cls += 1
            lines.append(f"- ❌ `{cls}` 需要方法: {', '.join(sorted(per_class[cls])) or '（仅引用）'}")
    lines.append("")
    lines.append("## 类存在但方法缺失")
    for cls in sorted(per_class):
        if cls in have and per_class[cls]:
            bad = sorted(m for m in per_class[cls] if m not in have[cls])
            if bad:
                miss_meth += len(bad)
                lines.append(f"- ⚠️ `{cls}`: {', '.join(bad)}")
    lines.append("")
    lines.append("## 完全可用的类")
    for cls in sorted(per_class):
        if cls in have and per_class[cls] and all(m in have[cls] for m in per_class[cls]):
            lines.append(f"- ✅ `{cls}`: {', '.join(sorted(per_class[cls]))}")
    lines.append("")
    lines.append("## 仅类名被引用（无方法级 hook 记录）")
    for cls in sorted(per_class):
        if cls in have and not per_class[cls]:
            lines.append(f"- ℹ️ `{cls}`")
    lines.append("")
    lines.append(f"## 反射字段（{len(all_fields)} 个）——需按持有类逐个确认")
    lines.append("")
    lines.append("| 字段 | 在下列 OS3 类中出现 |")
    lines.append("|---|---|")
    for fld in sorted(all_fields):
        holders = sorted(c for c in have if fld in have[c])
        lines.append(f"| `{fld}` | {', '.join('`%s`' % h.split('.')[-1] for h in holders) or '（本清单涉及的类里未命中）'} |")
    lines.append("")
    lines.append(f"## callMethod 目标（{len(all_calls)} 个）")
    lines.append("")
    for c in sorted(all_calls):
        holders = sorted(h.split('.')[-1] for k in have if c in have[k] for h in [k])
        lines.append(f"- `{c}` → {', '.join(holders) or '（本清单涉及的类里未命中）'}")
    lines.insert(2, f"统计：涉及类 {len(per_class)}，缺失类 {miss_cls}，缺失方法 {miss_meth}\n")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"classes={len(per_class)} missing_class={miss_cls} missing_method={miss_meth} -> {OUT}")

main()
