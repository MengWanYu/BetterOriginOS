#!/usr/bin/env bash
# 通知条美化 · 原生样式冒烟：验证「原生样式 = 完全不装通知条钩子，走 AOSP 渲染」。
# 断言全部来自日志与视图属性（read-only verifier 输出），不做截图验收。
# 用法：work/os3-smoke-native.sh [序列号]
# 退出码 0 = 全绿；非 0 = 有断言失败。
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/work/z8os3-live"
LOG="$OUT/native-smoke.log"
mkdir -p "$OUT"
FAILS=()

su_sh() { $ADB shell su -c "$1" 2>&1 | tr -d '\r'; }
fail() { FAILS+=("$1"); echo "  FAIL $1"; }
ok()   { echo "  ok   $1"; }

restart_sysui() {
  local old new i
  old=$(su_sh "pidof com.android.systemui")
  su_sh "kill -9 \$(pidof com.android.systemui)" >/dev/null 2>&1
  for i in $(seq 1 30); do
    sleep 1
    new=$(su_sh "pidof com.android.systemui")
    if [ -n "$new" ] && [ "$new" != "$old" ]; then
      return 0
    fi
  done
  return 1
}

unlock() {
  su_sh "input keyevent 224" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
}

echo "== 1) 环境前置 =="
SDK=$(su_sh "getprop ro.build.version.sdk"); OS=$(su_sh "getprop ro.vivo.os.build.display.id")
echo "  $OS / sdk $SDK"
[ "$SDK" = "33" ] || fail "不是 Android 13（sdk=$SDK）"
case "$OS" in *"OriginOS 3"*) ;; *) fail "不是 OriginOS 3（$OS）" ;; esac

echo "== 2) 切原生样式并重启 SystemUI =="
VERIFY0=$(su_sh "settings get global betteroriginos_notify_verify"); [ "$VERIFY0" = "null" ] && VERIFY0=0
su_sh "settings put global betteroriginos_notify_row_style native" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
[ "$(su_sh 'settings get global betteroriginos_notify_row_style')" = "native" ] || fail "样式没切到 native"
su_sh "logcat -c" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
unlock
P=$(su_sh "pidof com.android.systemui"); [ -n "$P" ] || fail "SystemUI 没起来"
ok "SystemUI pid=$P"

echo "== 3) 等待启动门闩日志落盘 =="
GATE_OK=0
for i in $(seq 1 20); do
  if su_sh "logcat -d -v threadtime" | grep -aq "notify_row: style=native"; then GATE_OK=1; break; fi
  sleep 1
done
[ "$GATE_OK" = "1" ] && ok "样式门闩日志已出现" || echo "  warn 启动阶段暂未抓到门闩日志（最终日志断言在 4b 复核）"

echo "== 4) 投递样本并打开面板 =="
su_sh "svc power stayon true" >/dev/null 2>&1
dbg() { su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
        su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify $1" >/dev/null 2>&1
        sleep "${2:-5}"; }
dbg postall 5
dbg postjump 5
su_sh "input keyevent 3" >/dev/null; sleep 1
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
su_sh "input swipe 540 1700 540 1100 800" >/dev/null; sleep 3
su_sh "input swipe 540 1100 540 1700 800" >/dev/null; sleep 3
su_sh "logcat -d -v threadtime" > "$LOG"

echo "== 4b) 零钩子断言（AOSP 管线未被接管）=="
grep -aq "notify_row: style=native" "$LOG" \
  && ok "样式门闩日志存在（style=native）" || fail "缺少 style=native 日志"
grep -aq "native style — AOSP notification pipeline untouched" "$LOG" \
  && ok "声明未接管 AOSP 管线" || fail "缺少 native untouched 声明"
grep -aq "native verifier installed (read-only)" "$LOG" \
  && ok "只读校验器已装（verify=1 临时诊断）" || fail "只读校验器未装"
IOSHOOK=$(grep -acE "notify row feature installed|notify_row: init enabled" "$LOG")
[ "$IOSHOOK" = "0" ] && ok "iOS 通知条钩子一个都没装" || fail "原生模式下仍装了 iOS 钩子 $IOSHOOK 处"

NV=$(grep -a "notify_row.native rowId=" "$LOG" | grep -ac .)
echo "  原生行采样 $NV 条"
[ "$NV" -ge 3 ] || fail "原生行采样不足（$NV < 3），无法证明 AOSP 渲染"

echo "== 5) AOSP 渲染结构断言（read-only verifier）=="
NONMEDIA=$(grep -a "notify_row.native rowId=" "$LOG" | grep -a " media=0 ")
NMN=$(printf '%s' "$NONMEDIA" | grep -ac .)
echo "  非媒体行 $NMN 条"
if [ "$NMN" -ge 3 ]; then
  ok "非媒体行数量足够（$NMN）"
else
  fail "非媒体行不足（$NMN < 3）"
fi
OVERLAY=$(printf '%s' "$NONMEDIA" | grep -ac " overlay=0 ")
[ "$NMN" -gt 0 ] && [ "$OVERLAY" = "$NMN" ] \
  && ok "无非媒体行带 iOS 覆盖层（overlay=0 ×$NMN）" \
  || fail "有行带 iOS 覆盖层：$((NMN-OVERLAY))/$NMN"
BGVIS=$(printf '%s' "$NONMEDIA" | grep -ac " bgAlpha=1.0 bgVis=0")
[ "$BGVIS" -ge 1 ] \
  && ok "原生通知背景参与渲染（bgAlpha=1.0/vis=0 ×$BGVIS；组内子行由 AOSP 自行隐藏属正常）" \
  || fail "没有任何一行出现原生背景（bgAlpha=1.0/vis=0）"
ROOT1=$(printf '%s' "$NONMEDIA" | grep -ac " rootAlpha=1.0 ")
[ "$NMN" -gt 0 ] && [ "$ROOT1" = "$NMN" ] \
  && ok "原生收起内容 alpha=1.0 ×$NMN" \
  || fail "原生收起内容 alpha 非 1：$((NMN-ROOT1))/$NMN"
ICON=$(printf '%s' "$NONMEDIA" | grep -acE " iconAlpha=1.0 iconVis=0")
[ "$ICON" -ge 1 ] \
  && ok "原生小图标在至少 1 行可见（iconVis=0 ×$ICON）" \
  || echo "  warn 采样瞬间没有展开到绘制小图标的行（不影响零钩子结论）"

echo "== 6) 无 iOS 专属日志（证明没走我们的绘制路径）=="
for pat in "notify_row: stack pkg" "ghost dedup" "icon morph" "notify_row.verify pkg" "shelf force radii"; do
  C=$(printf '%s' "$(su_sh "logcat -d --pid=$P -v threadtime")" | grep -ac "$pat")
  [ "$C" = "0" ] && ok "无「$pat」" || fail "原生模式出现「$pat」×$C"
done

echo "== 7) 稳定性（清除 + 重开）=="
su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1; sleep 3
P2=$(su_sh "pidof com.android.systemui")
[ "$P2" = "$P" ] && ok "清除样本后 SystemUI pid 不变（$P2）" || fail "SystemUI 重启：$P -> $P2"
CRASH=$(su_sh "logcat -d -b crash -v threadtime" | grep -aicE "systemui|betteroriginos")
[ "$CRASH" = "0" ] && ok "crash buffer 干净" || fail "crash buffer 有 $CRASH 条相关记录"

echo "== 8) 收尾 =="
su_sh "settings put global betteroriginos_notify_verify $VERIFY0" >/dev/null
su_sh "svc power stayon false" >/dev/null 2>&1

if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "原生样式冒烟：全绿（证据 $LOG）"
  exit 0
fi
echo "原生样式冒烟：失败 ${#FAILS[@]} 条"
for f in "${FAILS[@]}"; do echo " - $f"; done
exit 1
