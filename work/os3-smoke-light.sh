#!/usr/bin/env bash
# 通知条美化 · 原生优化样式冒烟：保留系统卡片/分组，只做 38dp 大图标 + 标题排版。
# 断言全部来自代码日志（restyle 与 verify 输出），不做截图验收。
# 用法：work/os3-smoke-light.sh [序列号]
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/work/z8os3-live"
LOG="$OUT/light-smoke.log"
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

echo "== 2) 切原生优化样式并重启 SystemUI =="
VERIFY0=$(su_sh "settings get global betteroriginos_notify_verify"); [ "$VERIFY0" = "null" ] && VERIFY0=0
su_sh "settings put global betteroriginos_feature_notify_row 1" >/dev/null
su_sh "settings put global betteroriginos_notify_row_style light" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
[ "$(su_sh 'settings get global betteroriginos_notify_row_style')" = "light" ] || fail "样式没切到 light"
su_sh "logcat -c" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
# 装包后的首启可能仍加载旧 dex；与验收纪律一致：连续重启两次
restart_sysui || fail "SystemUI 二次重启失败"
sleep 8
[ "$(su_sh 'settings get global betteroriginos_notify_verify')" = "1" ] || fail "verify 未置 1"
unlock
P=$(su_sh "pidof com.android.systemui"); [ -n "$P" ] || fail "SystemUI 没起来"
ok "SystemUI pid=$P"

echo "== 3) 投递样本并打开面板 =="
su_sh "svc power stayon true" >/dev/null 2>&1
dbg() { su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
        su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify $1" >/dev/null 2>&1
        sleep "${2:-5}"; }
dbg postall 5
dbg post 5
dbg postlong 5
dbg postjump 5
su_sh "input keyevent 3" >/dev/null; sleep 1
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
su_sh "input swipe 540 1700 540 1100 800" >/dev/null; sleep 3
su_sh "input swipe 540 1100 540 1700 800" >/dev/null; sleep 3
su_sh "logcat -d -v threadtime" > "$LOG"

echo "== 4) 轻量样式结构断言（代码日志）=="
INIT=$(grep -ac "notify_row: init enabled=true" "$LOG")
[ "$INIT" -ge 1 ] && ok "功能已启用（init enabled=true ×$INIT）" || fail "功能未启用"
RESTYLED=$(grep -ac "notify_row: restyled light pkg=" "$LOG")
[ "$RESTYLED" -ge 1 ] && ok "完成大图标/标题排版的独立通知 $RESTYLED 条" \
  || fail "轻量样式没有完成任何独立通知的重排（$RESTYLED）"
BIGICON=$(grep -a "notify_row: restyled light pkg=" "$LOG" | grep -ac "bigIcon=1")
[ "$BIGICON" -ge 1 ] && ok "38dp 大图标在 $BIGICON 条独立通知上生效" \
  || fail "38dp 大图标未生效（$BIGICON）"
NOSTACK=$(grep -ac "notify_row: stack pkg" "$LOG")
[ "$NOSTACK" = "0" ] && ok "没有堆叠胶囊（轻量样式不动分组）" \
  || fail "轻量样式仍出现堆叠卡 $NOSTACK 条"
GROUPOPT=$(grep -a "notify_row: restyled light pkg=" "$LOG" | grep -ac "groupChild")
[ "$GROUPOPT" = "0" ] && ok "分组子行保持原生（未重排/未切圆角）"   || fail "轻量样式仍重排了分组子行 $GROUPOPT 条"
NOOV=$(grep -ac "notify_row.verify.overlay" "$LOG")
[ "$NOOV" = "0" ] && ok "没有自绘消息 overlay" || fail "轻量样式仍出现 overlay $NOOV 条"
METRIC=$(grep -ac "light style, native list metrics/spacing" "$LOG")
[ "$METRIC" -ge 1 ] && ok "轻量样式保留原生列表间距/边距"   || fail "缺少原生间距声明（可能仍套 iOS 8dp 间距）"
GEO=$(grep -ac "geo=native" "$LOG")
[ "$GEO" -ge 1 ] && ok "轻量样式不改原生几何（geo=native）"   || fail "缺少原生几何标记"
BLANK=$(grep -a "notify_row: restyled light pkg=" "$LOG" | grep -ac "titleLen=0 bodyLen=0")
[ "$BLANK" = "0" ] && ok "没有空内容行被轻量样式接管（无纯白空卡）"   || fail "存在空内容行被接管：$BLANK 条"

echo "== 5) 稳定性 =="
su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1; sleep 3
P2=$(su_sh "pidof com.android.systemui")
[ "$P2" = "$P" ] && ok "清除样本后 SystemUI pid 不变（$P2）" || fail "SystemUI 重启：$P -> $P2"
CRASH=$(su_sh "logcat -d -b crash -v threadtime" | grep -aicE "systemui|betteroriginos")
[ "$CRASH" = "0" ] && ok "crash buffer 干净" || fail "crash buffer 有 $CRASH 条相关记录"

echo "== 6) 收尾 =="
su_sh "settings put global betteroriginos_notify_verify $VERIFY0" >/dev/null
su_sh "svc power stayon false" >/dev/null 2>&1

if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "原生优化样式冒烟：全绿（证据 $LOG）"
  exit 0
fi
echo "原生优化样式冒烟：失败 ${#FAILS[@]} 条"
for f in "${FAILS[@]}"; do echo " - $f"; done
exit 1
