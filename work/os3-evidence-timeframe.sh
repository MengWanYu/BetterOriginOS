#!/usr/bin/env bash
# 逐帧取证（BACKLOG-1104 §1 / P0-1）：在"点开一条通知"的整个过渡窗口里按帧记录
# header 时间与大图标的行内落点，交给 work/verify_time_frame.py 判。
# 纪律：只用 uiautomator dump 与模块逐帧见证，不截图、不退出 DSU、不提交 git。
# 用法：work/os3-evidence-timeframe.sh [序列号]
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/work/z8os3-live"
mkdir -p "$OUT"
HASH=$(sha256sum "$ROOT/module/build/BetterOriginOS.apk" | cut -c1-12)
LOG="$OUT/timeframe-$HASH.log"
PRE="$OUT/timeframe-$HASH-pre.xml"
# MSYS_NO_PATHCONV=1（adb 的 /sdcard 远端路径需要）会连带让 Git Bash 不给 Windows 原生程序转路径，
# 所以交给 python 的**本地**路径一律先过 cygpath -m，否则它去找 C:\c\Users\... 然后 "No such file"。
PREW="$(cygpath -m "$PRE")"

su_sh() { $ADB shell su -c "$1" 2>&1 | tr -d '\r'; }
fail() { echo "  FAIL $1"; RC=1; }
ok()   { echo "  ok   $1"; }

restart_sysui() {
  local old new i
  old=$(su_sh "pidof com.android.systemui")
  su_sh "kill -9 \$(pidof com.android.systemui)" >/dev/null 2>&1
  for i in $(seq 1 30); do
    sleep 1
    new=$(su_sh "pidof com.android.systemui")
    [ -n "$new" ] && [ "$new" != "$old" ] && return 0
  done
  return 1
}
unlock() {
  su_sh "input keyevent 224" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
}
# 面板焦点门：tap 前必须确认还在 NotificationShade，否则会按旧坐标点进别人的 App（出过事）
shade_focused() {
  local f
  f=$(su_sh "dumpsys window | grep -a 'mCurrentFocus=Window' | head -1")
  case "$f" in
    *NotificationShade*|*NotificationPanel*) return 0 ;;
    *) echo "     焦点：${f:-读不到}"; return 1 ;;
  esac
}
pause_media() {
  local st
  st=$(su_sh "dumpsys media_session" | grep -a "state=PlaybackState" | head -1)
  case "$st" in
    *state=3,*|*state=6,*) su_sh "cmd media_session dispatch pause" >/dev/null; sleep 1 ;;
  esac
}

# 选材：屏上要有一条**带时间、带展开箭头**的叶子行；应用名在不在都算候选。
# 为什么不能要求"应用名已在 66dp 列"：会犯病的形状恰恰是**应用名被收掉**（vivo 组子行），
# 那时 top_line 里第一个可见子节点就是时间，按布局规则它落到行首 +126 压在 42dp 图标上。
# 用 app_name 位置筛候选 = 把唯一能红的材料排除掉，等于自证通过。
# 另两条本轮实测的坑：
# ① 同一 App 的多条通知在 OS3 会被系统自动归组，而 AOSP 组子行的 header time 宽度 0、INVISIBLE
#    —— 先投 postgroup 再投 post，样本就变成没有时间的子行，判据会拿"没材料"冒充"没闪"；
# ② 组摘要行的 bounds 把子行整个包住，不排掉容器行就会把子行的文本算到摘要行头上。
pick_target() {
  python - "$PREW" <<'PY'
import sys, re, xml.etree.ElementTree as ET


def box(n):
    v = [int(x) for x in re.findall(r"-?\d+", n.get("bounds", ""))]
    return tuple(v) if len(v) == 4 else None


root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter("node"))
boxes = [(box(r), r) for r in nodes
         if "expandableNotificationRow" in r.get("resource-id", "")]
leaf = polished = 0
cands = []
for rb, r in boxes:
    if not rb:
        continue
    rl, rt, rr, rbo = rb
    nested = [o for o, _ in boxes if o and o != rb
              and rl <= o[0] and o[2] <= rr and rt <= o[1] and o[3] <= rbo]
    if not nested:
        leaf += 1
    app = None
    has_time = False
    btn = None                      # 取**最靠上**的那枚箭头：容器行里child 的箭头在下面
    for n in nodes:
        b = box(n)
        if not b:
            continue
        l, t, rp, bp = b
        if not (rl <= l and l <= rr and rt <= t and bp <= rbo):
            continue
        rid = n.get("resource-id", "")
        if rid.endswith(":id/app_name_text") and (app is None or t < app[1]):
            app = (l - rl, t)       # 这一行自己的应用名（最靠上的那枚）
        if rid.endswith(":id/time") and (n.get("text") or "").strip():
            has_time = True
        if rid.endswith(":id/expand_button") and (btn is None or t < btn[2]):
            btn = ((l + rp) // 2, (t + bp) // 2, t)
    is_polished = app is not None and app[0] >= 180
    if is_polished:
        polished += 1
    if has_time and btn:
        # 排序：已接管的叶子行(0) > 未接管叶子行(1) > 容器/摘要行(2)——点摘要会重排子行，
        # 而 vivo 组子行才是"应用名被收掉、时间落到行首"的那一类材料。
        cands.append((0 if (is_polished and not nested) else (1 if not nested else 2),
                      rbo - rt, btn[:2]))
print("DIAG rows=%d leaf=%d polished=%d cands=%d" % (len(boxes), leaf, polished, len(cands)))
if cands:
    cands.sort()          # 同级里挑矮的：收起态展开才是判据场景
    print("TAP %d %d" % cands[0][2])
else:
    print("TAP -1 -1")
PY
}

RC=0
echo "== 0) 取证对象 =="
if [ ! -s "$ROOT/module/build/BetterOriginOS.apk" ]; then
  echo "  FAIL module/build/BetterOriginOS.apk 不存在（构建失败？），拒绝取证"; exit 2
fi
echo "  apk sha256[:12]=$HASH"
REMOTE=$(su_sh "sha256sum \$(pm path com.betteroriginos 2>/dev/null | head -1 | cut -d: -f2)" \
  | grep -aoE '^[0-9a-f]{12}')
if [ "$REMOTE" != "$HASH" ]; then
  echo "  FAIL 设备上装的是 ${REMOTE:-读不到}，本地构建是 $HASH —— 先 adb install -r 再取证"; exit 2
fi
ok "设备上的模块 APK 与本地构建一致（$REMOTE）"

echo "== 1) 起底：sep=os5 + verify=1 + trace 关 =="
su_sh "settings put global betteroriginos_feature_notify_row 1" >/dev/null
su_sh "settings put global betteroriginos_notify_row_sep os5" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
su_sh "settings put global betteroriginos_trace_flicker 0" >/dev/null   # 先清预算，装完包再重新起环
su_sh "svc power stayon true" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
restart_sysui || fail "SystemUI 二次重启失败（装包后首启可能仍是旧 dex）"
sleep 6
unlock
FOCUS=$(su_sh "dumpsys window | grep -a 'mCurrentFocus=Window' | head -1")
case "$FOCUS" in
  *com.android.systemui*|*NotificationShade*|*Launcher*) ;;
  *)
    FP=$(printf '%s' "$FOCUS" | sed -E 's/.*u0 ([a-zA-Z0-9._]+)\/.*/\1/')
    [ -n "$FP" ] && { echo "     前台是 $FP，先 force-stop"; su_sh "am force-stop $FP" >/dev/null; sleep 2; } ;;
esac
# 接管方式自报（push-only / column-hook）只在启动那一拍打，必须在清零前留档
su_sh "logcat -d -v threadtime" | grep -aE "notify row feature installed header-time=" \
  > "$OUT/timeframe-$HASH-install.txt" || true
HTM=$(grep -ao "installed header-time=[a-z-]*" "$OUT/timeframe-$HASH-install.txt" | tail -1 | cut -d= -f2)
if [ -z "$HTM" ]; then
  fail "模块没自报 header-time 接管方式（证据 timeframe-$HASH-install.txt）"
else
  ok "header 时间接管方式=$HTM"
fi

echo "== 2) 只投一条独立、带时间的样本（post）；先撤掉上一轮的旧样本 =="
su_sh "logcat -c" >/dev/null
su_sh "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1
sleep 2
su_sh "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify post" >/dev/null 2>&1
sleep 4
su_sh "input keyevent 3" >/dev/null; sleep 1
pause_media
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3

echo "== 3) 选材：被接管且带 header 时间的叶子行必须在屏上 =="
TX=-1; TY=-1; DIAG="没取到 dump"
for try in 1 2 3; do
  su_sh "rm -f /sdcard/tf.xml; uiautomator dump /sdcard/tf.xml" >/dev/null 2>&1
  $ADB pull -q /sdcard/tf.xml "$PREW" 2>/dev/null
  [ -s "$PRE" ] || { echo "     第 $try 次：dump 没拿到"; continue; }
  P=$(pick_target)
  DIAG=$(printf '%s' "$P" | grep -a DIAG | head -1)
  echo "     第 $try 次：$DIAG"
  TX=$(printf '%s' "$P" | grep -a '^TAP' | head -1 | awk '{print $2}')
  TY=$(printf '%s' "$P" | grep -a '^TAP' | head -1 | awk '{print $3}')
  case "${TX:-}" in ''|*[!0-9-]*) TX=-1 ;; esac
  if [ "${TX:-0}" -ge 0 ] 2>/dev/null; then break; fi
  # 样本可能被挤到收纳栏后面：慢滑（快滑会被当成 fling 把面板甩掉）
  su_sh "input swipe 540 2000 540 1000 900" >/dev/null; sleep 2
  su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 1
done
if [ "${TX:-0}" -lt 0 ]; then
  fail "屏上没有已接管且带 header 时间的叶子行（$DIAG）：判据没有材料，不判「没闪」"
  su_sh "svc power stayon false" >/dev/null; su_sh "settings put global betteroriginos_trace_flicker 0" >/dev/null
  exit 1
fi
ok "展开点=($TX,$TY)"
shade_focused || { su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2; }
shade_focused || fail "焦点不在面板，tap 会打空 —— 观察窗口无效"

echo "== 4) 起环 + 逐次点开（每次 tap 后立刻追加捕获日志）=="
: > "$LOG"
su_sh "settings put global betteroriginos_trace_flicker 1" >/dev/null
sleep 1.5
for i in 1 2 3; do
  if shade_focused; then
    su_sh "input tap $TX $TY" >/dev/null
    sleep 2.2
    su_sh "logcat -d -v threadtime" | grep -a "BetterOriginOS:" >> "$LOG"
  else
    fail "第 $i 次 tap 前焦点不在面板，跳过（不算测过）"
  fi
done
su_sh "settings put global betteroriginos_trace_flicker 0" >/dev/null   # 逐帧快照用完置 0

echo "== 5) 逐帧验收 =="
cp -f "$LOG" "$OUT/timeframe-$HASH-keep.log" 2>/dev/null   # 换名留档：固定名会被下一次跑覆盖
PYTHONIOENCODING=utf-8 python "$(cygpath -m "$ROOT/work/verify_time_frame.py")" \
  --log "$(cygpath -m "$LOG")"
[ "${PIPESTATUS[0]}" = "0" ] || fail "逐帧验收有失败（证据 work/z8os3-live/timeframe-$HASH-keep.log）"

echo "== 6) 收尾 =="
su_sh "svc power stayon false" >/dev/null
su_sh "cmd statusbar collapse" >/dev/null
su_sh "settings put global betteroriginos_trace_flicker 0" >/dev/null
if [ "$RC" = "0" ]; then
  echo "逐帧取证：全绿（证据 work/z8os3-live/timeframe-$HASH-keep.log）"
else
  echo "逐帧取证：有失败（证据同上）"
fi
exit $RC
