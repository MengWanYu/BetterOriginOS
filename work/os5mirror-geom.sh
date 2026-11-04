#!/usr/bin/env bash
# 浮动一键清除的几何判据（从 work/os3-smoke-os5mirror.sh 抽出来，这样它能被自测）。
# 真机脚本里那 20 行 shell 判断如果从没跑过，就等于门禁上有一个没接线灯泡的灯座。
#
# 用法：
#   source work/os5mirror-geom.sh
#   parse_bounds "$LOG_LINE"        # 解析 `clear-all at x=.. y=.. w=.. h=..`
#   check_placement $CX $CY $SW $SH # 返回 0=位置对，非 0=位置错（并打印原因）
#
# 坐标只认模块自己打的日志：眼估截图坐标在这两台机上翻过车（进度条那次）。

parse_bounds() {
  local line="$1" X Y W H
  X=$(echo "$line" | grep -aoE 'x=[-0-9]+' | head -1 | cut -d= -f2)
  Y=$(echo "$line" | grep -aoE 'y=[-0-9]+' | head -1 | cut -d= -f2)
  W=$(echo "$line" | grep -aoE 'w=[0-9]+' | head -1 | cut -d= -f2)
  H=$(echo "$line" | grep -aoE 'h=[0-9]+' | head -1 | cut -d= -f2)
  # set -u 下空值会让算术终止脚本（把设备留在 stayon + 面板展开的脏状态），所以先兜 0
  CX=$(( ${X:-0} + ${W:-0} / 2 ))
  CY=$(( ${Y:-0} + ${H:-0} / 2 ))
  BW=${W:-0}
  BH=${H:-0}
}

# 位置判据来自两版布局树：OS5 的原生钮是 NotificationPanelView 的直接子级，
# layout_gravity=0x51（BOTTOM|CENTER_HORIZONTAL）。A13 的 NSSL 父层是 ConstraintLayout，
# 挂在那儿 gravity 会被 generateLayoutParams 吞掉，未约束子视图落左上角 ——
# 所以"水平居中 + 落在下半屏"这两条就是那个回归的探针。
check_placement() {
  local cx=$1 cy=$2 sw=$3 sh=$4 tol=${5:-2} dx
  if [ "$sw" -le 0 ] || [ "$sh" -le 0 ]; then
    echo "读不到屏幕尺寸（wm size 解析失败），位置判据没跑"
    return 3
  fi
  if [ "$cx" -le 0 ] || [ "$cy" -le 0 ]; then
    echo "clear-all at 解析不出正坐标，按钮没布局"
    return 4
  fi
  dx=$(( cx - sw / 2 ))
  [ "$dx" -lt 0 ] && dx=$(( -dx ))
  if [ "$dx" -gt "$tol" ]; then
    echo "没水平居中：中心 x=$cx，屏宽 $sw（差 $dx px）——宿主没吃到 gravity"
    return 1
  fi
  if [ "$cy" -le $(( sh / 2 )) ]; then
    echo "不在屏幕下半部：中心 y=$cy，屏高 $sh——像是被当未约束视图放在顶上"
    return 2
  fi
  echo "位置符合 OS5 语义：($cx,$cy) 居中且在底部（屏 ${sw}x${sh}）"
  return 0
}
