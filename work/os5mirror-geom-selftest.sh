#!/usr/bin/env bash
# work/os5mirror-geom.sh 的自测：正例 1 条 + 四种必须报红的形状。
# 用法：bash work/os5mirror-geom-selftest.sh
set -u
cd "$(dirname "$0")/.."
# shellcheck source=work/os5mirror-geom.sh
source work/os5mirror-geom.sh

SW=1080
SH=2388          # Z8 实测 wm size；密度 3.0 下 46dp 按钮 = 138px
fail=0

expect() { # expect <期望rc> <名字> <命令行>
  local want=$1 name="$2" line="$3" rc out
  parse_bounds "$line"
  out=$(check_placement "$CX" "$CY" "$SW" "$SH" 2>&1); rc=$?
  if [ "$rc" != "$want" ]; then
    echo "  FAIL $name：期望 rc=$want 实得 rc=$rc（$out）"
    fail=1
  else
    echo "  ok   $name -> rc=$rc $out"
  fi
}

# 正例：FrameLayout 居中 -> left=(1080-138)/2=471，中心 540；底部 62dp 边距 + 导航条
expect 0 "居中且在底部" "os5_mirror: clear-all at x=471 y=2180 w=138 h=138"
# 反例 1：ConstraintLayout 吞 gravity 后的形状（未约束视图落左上角）
expect 1 "落在左上角" "os5_mirror: clear-all at x=0 y=0 w=138 h=138"
# 反例 2：水平居中但贴在屏幕上方（margin/gravity 的 BOTTOM 丢了）
expect 2 "居中但在上半屏" "os5_mirror: clear-all at x=471 y=100 w=138 h=138"
# 反例 3：没有坐标日志（按钮没布局 / verify 没开）
expect 4 "缺坐标日志" ""
# 反例 4：屏幕尺寸读不到 —— 不许静默通过
parse_bounds "os5_mirror: clear-all at x=471 y=2180 w=138 h=138"
out=$(check_placement "$CX" "$CY" 0 0 2>&1); rc=$?
if [ "$rc" = 3 ]; then
  echo "  ok   屏幕尺寸缺失 -> rc=3 $out"
else
  echo "  FAIL 屏幕尺寸缺失：期望 rc=3 实得 rc=$rc"; fail=1
fi
# 反例 5：日志字段漂移（模块改成 height= 而脚本还在找 w=）
# 中心会被算成左边界 471，位置判据必须因此报红，而不是"恰好通过"
parse_bounds "os5_mirror: clear-all at x=471 y=2180 height=138"
out=$(check_placement "$CX" "$CY" "$SW" "$SH" 2>&1); rc=$?
if [ "$rc" = 0 ]; then
  echo "  FAIL 字段漂移没被抓到：w= 缺失时居然通过了位置判据"
  fail=1
else
  echo "  ok   字段漂移被抓到 -> rc=$rc（CX=$CX 当成中心）"
fi

if [ "$fail" = 0 ]; then
  echo "OS5 复刻几何自测：全绿"
  exit 0
fi
echo "OS5 复刻几何自测：有失败"
exit 1
