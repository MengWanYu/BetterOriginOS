#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁脚本写的 settings 键，必须真的是模块会读的键。

为什么要这个：OS3/OS5 的门禁全靠 `settings put global betteroriginos_xxx` 切开关。
键名一旦对不上（模块改了 featureId / optionId，或脚本手打错），
`settings` 照样写成功、模块照样读默认值 —— **A/B 两相跑完，其实两相都是同一个状态**，
所有"复刻开 vs 关"的对照变成自证。这类失效不会崩、不会报错，只会让门禁白跑一趟设备。

规则：从模块源码推出"合法键全集"，再要求 work/*.sh 里出现的每个 betteroriginos_ 键都在其中。
推导来源（缺一会假报未知键）：
  1) 源码里写死的完整键字面量（legacy 注册表、debug 用键）；
  2) `Prefs.option(ctx, "<featureId>", "<optionId>", ...)` 的组合；
  3) `Feature#id()` 的返回值 —— feature 开关走 `featureEnabled(ctx, id())`，
     源码里根本没有 "betteroriginos_feature_media_card" 这种整串字面量（只有 "media_card"），
     所以必须按 id() 来推，否则第一条版本就把它误报成未知键。
用法：python work/settings-keys-check.py [--selftest]
"""
import glob
import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODULE_SRC = os.path.join(ROOT, "module", "src")
GATE_GLOBS = ["work/*.sh"]


def module_text():
    out = []
    for p in glob.glob(os.path.join(MODULE_SRC, "**", "*.java"), recursive=True):
        out.append(io.open(p, encoding="utf-8", errors="replace").read())
    return "\n".join(out)


def known_keys(src):
    keys = set(re.findall(r"betteroriginos_[a-z0-9_]+", src))
    # Feature#id() 的返回值：形如 `return "media_card";`，紧跟在 id() 方法里
    feature_ids = set(re.findall(r"public\s+String\s+id\(\)\s*\{[^}]*?return\s+\"([a-z_0-9]+)\"",
                                 src, re.S))
    keys |= {"betteroriginos_feature_" + f for f in feature_ids}
    # Prefs.option(ctx, "<fid>", "<opt>", ...) —— 允许参数跨行
    for fid, opt in re.findall(
            r"Prefs\.\w*[Oo]ption\(\s*[^,]+,\s*\"([a-z_0-9]+)\"\s*,\s*\"([a-z_0-9]+)\"",
            src, re.S):
        keys.add("betteroriginos_%s_%s" % (fid, opt))
        keys.add("betteroriginos_" + opt)          # 有些选项按裸 optionId 注册成 legacy
    return keys


def script_keys():
    keys = {}
    for pat in GATE_GLOBS:
        for p in glob.glob(os.path.join(ROOT, pat)):
            text = io.open(p, encoding="utf-8", errors="replace").read()
            for k in re.findall(r"betteroriginos_[a-z0-9_]+", text):
                keys.setdefault(k, set()).add(os.path.relpath(p, ROOT))
    return keys


def find_unknown(known, skeys):
    return sorted(k for k in skeys if k not in known)


def main(argv):
    src = module_text()
    known = known_keys(src)
    skeys = script_keys()
    if "--selftest" in argv:
        # 自测：往已知集合里塞一个真键、再造一个假键，必须分别"通过/报红"。
        fake = dict(skeys)
        fake["betteroriginos_notify_row_definitely_not_a_real_option"] = {"<fixture>"}
        unknown = find_unknown(known, fake)
        if "betteroriginos_notify_row_definitely_not_a_real_option" not in unknown:
            print("SELFTEST FAIL: 假键没被抓出来（检查器形同虚设）")
            return 1
        if any(k not in fake for k in unknown):
            print("SELFTEST FAIL: 报了不该报的键")
            return 1
        print("SELFTEST: 假键会被抓、真键不误报（未知键数 %d，含注入的那条）" % len(unknown))
        return 0
    unknown = find_unknown(known, skeys)
    print("门禁脚本用到 %d 个键；模块可推导的键 %d 个" % (len(skeys), len(known)))
    for k in unknown:
        print("  FAIL %s（出现在 %s）模块推导不出这个键 —— 写了也没人读，"
              "A/B 两相会变成同一个状态" % (k, ", ".join(sorted(skeys[k]))))
    if unknown:
        print("settings 键核对：有 %d 个对不上" % len(unknown))
        return 1
    print("settings 键核对：门禁脚本写的每个键都真的会被模块读到")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
