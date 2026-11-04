#!/usr/bin/env bash
# 一键跑完所有"不需要连设备"的验收。装机前跑这个，红了就别上设备。
# 用法：bash work/offline-all.sh        （退出码非 0 = 有检查失败）
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export PYTHONIOENCODING=utf-8   # Windows 控制台默认 GBK，中文断言会花屏

FAILS=0
run() {
  local name="$1"; shift
  local out
  out=$("$@" 2>&1)
  if [ $? -eq 0 ]; then
    printf '  ok   %-34s %s\n' "$name" "$(printf '%s' "$out" | tail -1)"
  else
    printf '  FAIL %-34s %s\n' "$name" "$(printf '%s' "$out" | tail -1)"
    printf '%s\n' "$out" | tail -12 | sed 's/^/         /'
    FAILS=$((FAILS + 1))
  fi
}

if [ ! -d module/build/classes ]; then
  echo "还没构建：cd module && ./build.sh 再来"
  exit 2
fi

echo "== 离线验收（模块字节码 + 独立参考 + 反例）=="
run "G2 连续角逐指令"      python work/g2_path_selftest.py
run "G2 负对照"            python work/g2_path_selftest.py --negative
run "浮钮几何判据自测"      bash work/os5mirror-geom-selftest.sh
run "A11 取色数学"          python work/a11_math_selftest.py
run "A11 取色交叉验证"       python work/a11_crosscheck.py
run "A11 取色网格"          python work/a11_sampling_selftest.py
run "A11 取色网格负对照"     python work/a11_sampling_selftest.py --negative
run "A11 取色选择逻辑"        python work/a11_selection_selftest.py
run "A11 选择逻辑负对照"      python work/a11_selection_selftest.py --negative
run "A11 通知卡规格"        python work/a11_notif_selftest.py
run "A11 通知卡负对照"      python work/a11_notif_selftest.py --negative
run "A11 量化器(median-cut)"  python work/a11_quantize_selftest.py
run "A11 量化器负对照"        python work/a11_quantize_selftest.py --negative
run "验证器自测(正反例)"     python work/verify_os5_mirror.py --selftest
run "行排版/圆角验证器自测"  python work/verify_row_layout.py --selftest
run "Y7s 实读取证核对"       python work/y7s_a11_evidence_check.py
run "Y7s 取证反例"          python work/y7s_a11_evidence_check.py --selftest
# 验证器的每条正则必须还能对上源码里真实的日志串（今晚就踩过"正则对不上、fixture 自洽地绿"）
run "日志形状自检"          python work/logshape_lint.py
run "标记检查自身有效"      python work/logshape_lint.py --selftest
run "门禁 settings 键核对"    python work/settings-keys-check.py
run "键核对自身有效"          python work/settings-keys-check.py --selftest
# 打进日志的统计量必须有真实写入点：真机第一次跑就是被 `clear=0` 这种"假见证"骗了
run "见证计数器核对"          python work/witness-counters-check.py
run "计数器核对自身有效"      python work/witness-counters-check.py --selftest

if [ "$FAILS" -ne 0 ]; then
  echo "离线验收有 $FAILS 项失败 —— 先修再装机"
  exit 1
fi
echo "全部通过。真机门禁（需要设备/解锁）见 out/RUNBOOK-1103.md"
