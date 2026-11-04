#!/usr/bin/env bash
# 通知条美化 · OS5 复刻（Os5Mirror）冒烟：A13 上复刻 A15 的连续角半径、收纳栏与浮动一键清除。
# 断言全部来自模块自身的度量日志 + work/verify_os5_mirror.py，不做截图验收。
# 用法：work/os3-smoke-os5mirror.sh [序列号] [density]
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
DENSITY="${2:-3.0}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/work/os5mirror-geom.sh"   # 坐标解析与位置判据（有自测）
OUT="$ROOT/work/z8os3-live"
LOG_ON="$OUT/os5mirror-on.log"
LOG_OFF="$OUT/os5mirror-off.log"
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

# 投递样本、展开面板、把日志落到指定文件
collect() {
  local logfile="$1"
  su_sh "svc power stayon true" >/dev/null 2>&1
  su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
  su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postall" >/dev/null 2>&1
  sleep 5
  su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify post" >/dev/null 2>&1
  sleep 5
  su_sh "input keyevent 3" >/dev/null; sleep 1
  su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
  su_sh "input swipe 540 1700 540 1100 800" >/dev/null; sleep 2
  su_sh "input swipe 540 1100 540 1700 800" >/dev/null; sleep 2
  su_sh "logcat -d -v threadtime" > "$logfile"
}

echo "== 1) 环境前置 =="
SDK=$(su_sh "getprop ro.build.version.sdk"); OS=$(su_sh "getprop ro.vivo.os.build.display.id")
echo "  $OS / sdk $SDK"
[ "$SDK" = "33" ] || { echo "  这台不是 Android 13（sdk=$SDK），OS5 复刻只应在 OS3 上跑"; exit 2; }
case "$OS" in *"OriginOS 3"*) ;; *) fail "不是 OriginOS 3（$OS）" ;; esac

VERIFY0=$(su_sh "settings get global betteroriginos_notify_verify")
[ "$VERIFY0" = "null" ] && VERIFY0=0
SEP0=$(su_sh "settings get global betteroriginos_notify_row_sep")
[ "$SEP0" = "null" ] && SEP0=off

echo "== 2) A 相：sep=os5（复刻开）=="
su_sh "settings put global betteroriginos_feature_notify_row 1" >/dev/null
su_sh "settings put global betteroriginos_notify_row_sep os5" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
[ "$(su_sh 'settings get global betteroriginos_notify_row_sep')" = "os5" ] || fail "sep 没切到 os5"
su_sh "logcat -c" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
restart_sysui || fail "SystemUI 二次重启失败（装包后首启可能仍是旧 dex）"
sleep 8
unlock
P=$(su_sh "pidof com.android.systemui"); [ -n "$P" ] || fail "SystemUI 没起来"
P_ON="$P"          # A 相基线：一键清除点击后必须是同一个 pid（第 4 步用它判崩溃）
ok "SystemUI pid=$P"
collect "$LOG_ON"

echo "== 3) 复刻钩子装载与落点 =="
INST=$(grep -ac "os5 mirror hooks installed" "$LOG_ON")
[ "$INST" -ge 1 ] && ok "复刻钩子已装载（×$INST）" || fail "复刻钩子没装载"
HITS=$(grep -m1 -a "os5_mirror: install groups" "$LOG_ON")
echo "     钩子命中明细: ${HITS:-无}"
echo "$HITS" | grep -q "=0" && fail "有钩子命中 0 个方法（静默失效）：$HITS" || ok "每个钩子都至少命中 1 个方法"
# 落点自检：优先信模块自己给的结论。reachable 的判据本来就是"g2 路径与 shelf 圆角真的被改过"，
# 所以异步自检没出结论时（A13 真机 17:58 那轮：scheduled 有、verdict 无 —— 已被证实是
# 中间一次 enabled=false 把这条链静默掐断），不能反过来把已经抓到的同源几何证据判红。
for i in $(seq 1 10); do
  sleep 5
  su_sh "logcat -d -v threadtime" > "$LOG_ON"
  grep -qa "os5_mirror landing " "$LOG_ON" && break
done
LAND=$(grep -m1 -a "os5_mirror landing " "$LOG_ON")
G2HIT=$(grep -ac "os5_mirror: g2 path" "$LOG_ON")
SHELFHIT=$(grep -ac "os5_mirror: shelf topRoundness" "$LOG_ON")
case "$LAND" in
  *reachable*) ok "落点自检 reachable：${LAND#*landing }" ;;
  *no-entry*)  fail "落点自检 no-entry：钩子装了但几何从未触发" ;;
  *interrupted*) if [ "$G2HIT" -ge 1 ] && [ "$SHELFHIT" -ge 1 ]; then
        ok "落点自检被 enabled=false 掐断（${LAND#*landing }），改按几何见证判定：g2=$G2HIT shelf=$SHELFHIT"
      else
        fail "落点自检中断且没有几何见证：g2=$G2HIT shelf=$SHELFHIT ${LAND#*landing }"
      fi ;;
  *)           if [ "$G2HIT" -ge 1 ] && [ "$SHELFHIT" -ge 1 ]; then
        ok "无 verdict 行，落点按同源几何证据成立：g2=$G2HIT shelf=$SHELFHIT"
      else
        fail "落点未证实：既没有 landing verdict，也没有 g2/shelf 几何见证（g2=$G2HIT shelf=$SHELFHIT）"
      fi ;;
esac

echo "== 4) 一键清除点击链路（tap -> 内联监听 -> 原生批量移除）=="
# 判据全在日志与 dumpsys 里：坐标只认模块自己打的 clear-all at（眼估坐标在这两台机上翻过车），
# 点击是否真落到我们的 View 由 clear-all tap 见证行证明，通知是否真被清由 NotificationRecord 计数证明。
# 注意：A13 原生 animateShelfDisappear 被 z = !hasUnClearableNotifications 门控，
# 这台机上常驻 LSPosed 的 ONGOING 通知，所以点完之后"栏留着"才是对的（OS5 也留着）。
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
DISM_BEFORE=$(su_sh "dumpsys notification" | grep -a "NotificationRecord(" \
  | grep -avcE "flags=[A-Z_|]*(ONGOING_EVENT|NO_CLEAR|NO_DISMISS)")
AT=$(grep -a "os5_mirror: clear-all at" "$LOG_ON" | tail -1)
parse_bounds "$AT"                       # work/os5mirror-geom.sh：有自测，跑的就是这段
# 有 Override 时以最后一条为准（wm size 先物理后覆盖）
read -r SW SH <<<"$(su_sh "wm size" | grep -aoE '[0-9]+x[0-9]+' | tail -1 | tr 'x' ' ')"
GMSG=$(check_placement "$CX" "$CY" "${SW:-0}" "${SH:-0}"); GRC=$?
case "$GRC" in
  0) ok "位置：$GMSG" ;;
  4) fail "没有可用的 clear-all at 坐标日志（$GMSG）：点击链路整段没测" ;;
  *) fail "浮动按钮位置不对（rc=$GRC）：$GMSG" ;;
esac
if [ "$GRC" -ne 0 ]; then
  echo "     位置不成立就不 tap：坐标没有意义，免得把「没挂载/挂错层」误报成「点了没反应」"
else
  echo "     按钮 $AT -> tap ($CX,$CY)，可清除通知 $DISM_BEFORE 条"
  [ "$DISM_BEFORE" -gt 0 ] 2>/dev/null || fail "本轮没有可清除通知（样本没投上？），点击链路验不出东西"
  su_sh "input tap $CX $CY" >/dev/null
  sleep 4
  su_sh "logcat -d -v threadtime" > "$LOG_ON"
  TAP=$(grep -a "os5_mirror: clear-all tap" "$LOG_ON" | tail -1)
  if [ -z "$TAP" ]; then
    fail "点了 ($CX,$CY) 但没有 clear-all tap 见证：这一击没落到浮动按钮上"
  else
    echo "     $TAP"
    case "$TAP" in
      *inlineListener=1*) ok "内联监听在位（原生批量移除链可达）" ;;
      *) fail "内联按钮没有 OnClickListener：点击是静默空转（监听只在 NSSL.setShelfController 挂一次）" ;;
    esac
  fi
  # 崩溃判据必须贴在这一击后面：A15 上真崩过（抹掉 getGroupKey 后批量移除拿 null key 查表 NPE），
  # 拖到第 7 步比 pid 已经没有意义 —— 那时 B 相自己重启过一次 SystemUI。
  P_AFTER=$(su_sh "pidof com.android.systemui")
  [ "$P_AFTER" = "$P_ON" ] \
    && ok "点击后 SystemUI 仍是同一进程（$P_AFTER）：批量移除链路没崩" \
    || fail "点击后 SystemUI 重启（$P_ON -> $P_AFTER）：批量移除链路里有崩溃"
  DISM_AFTER=$(su_sh "dumpsys notification" | grep -a "NotificationRecord(" | grep -avcE "flags=[A-Z_|]*(ONGOING_EVENT|NO_CLEAR|NO_DISMISS)")
  if [ "$DISM_AFTER" = "0" ]; then
    ok "可清除通知全部移除（$DISM_BEFORE -> 0），SystemUI 没重启由第 4/7 步复核"
  elif [ "$DISM_AFTER" -lt "$DISM_BEFORE" ] 2>/dev/null; then
    ok "可清除通知减少（$DISM_BEFORE -> $DISM_AFTER）"
  else
    fail "点击后通知数没降（$DISM_BEFORE -> $DISM_AFTER）：转接没生效"
  fi
fi

echo "== 5) 复刻断言（verify_os5_mirror.py，含点击见证）=="
# 先按包哈希留档再验：LOG_ON/LOG_OFF 是固定文件名，下一轮 collect 会原地覆盖，
# 而文档里"证据在 os5mirror-on.log"那句话是按当时内容写的（r8 的 bg clip 样本就这么丢了）。
BHASH=$(sha256sum "$ROOT/module/build/BetterOriginOS.apk" 2>/dev/null | cut -c1-12)
cp "$LOG_ON" "$OUT/os5mirror-on-${BHASH:-unknown}.log" 2>/dev/null
cp "$LOG_OFF" "$OUT/os5mirror-off-${BHASH:-unknown}.log" 2>/dev/null
# 放在点击链路之后：验证器现在要求 clear-all tap 见证行，先跑会把"还没点"误判成失败。
# 脚本开头 export 了 MSYS_NO_PATHCONV=1（adb 的 /system 路径需要），它会让 Git Bash
# **也不**给 Windows 原生程序转换路径 —— 直接把 $ROOT/work/... 交给 python.exe，
# 它就去找 C:\c\Users\... 然后 "No such file"（真机第一次跑就是这样断在第 5 步的）。
# ⇒ 送给 python 的路径一律过 cygpath -m。
PY_SCRIPT=$(cygpath -m "$ROOT/work/verify_os5_mirror.py")
PY_LOG=$(cygpath -m "$LOG_ON")
PYTHONIOENCODING=utf-8 python "$PY_SCRIPT" --density "$DENSITY" --notify-only "$PY_LOG" \
  && ok "OS5 复刻度量全绿" || fail "OS5 复刻度量有失败（见 $LOG_ON）"

echo "== 6) B 相：sep=off（复刻关，负对照）=="
su_sh "settings put global betteroriginos_notify_row_sep off" >/dev/null
sleep 3
su_sh "logcat -c" >/dev/null
restart_sysui || fail "SystemUI 三启失败"
sleep 8
unlock
collect "$LOG_OFF"
OFFHITS=$(grep -acE "os5_mirror: (radius level=|g2 path|shelf topRoundness|shelf gap|bg clip|clear-all (attached|at|tap))" "$LOG_OFF")
# 注意不含 `clear-all native=`：那是"系统有没有原生圆钮"的探针，装钩子时就要打一次，
# 与复刻几何无关；把它算进负对照会把正常的探针误报成"开关没关住"。
[ "$OFFHITS" = "0" ] && ok "关闭复刻后没有任何复刻度量日志（几何回到系统自身）" \
  || fail "sep=off 仍有复刻度量日志 $OFFHITS 条（开关没关住）"

echo "== 7) 稳定性 =="
# 基线必须在这里重取：第 6 步自己重启过 SystemUI（B 相），
# 拿 A 相的 pid 来比会得到一条假的"批量移除中崩了"（上一轮就是这么红的）。
P7=$(su_sh "pidof com.android.systemui")
su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1
sleep 3
P2=$(su_sh "pidof com.android.systemui")
[ "$P2" = "$P7" ] && ok "清除样本后 SystemUI pid 不变（$P2）" || fail "SystemUI 在批量移除中重启：$P7 -> $P2"
CRASH=$(su_sh "logcat -d -b crash -v threadtime --pid=$P2" | grep -aic "com.betteroriginos")
[ "$CRASH" = "0" ] && ok "crash buffer 无本模块记录" || fail "crash buffer 有 $CRASH 条本模块相关记录"

echo "== 8) 收尾 =="
su_sh "settings put global betteroriginos_notify_row_sep $SEP0" >/dev/null
su_sh "settings put global betteroriginos_notify_verify $VERIFY0" >/dev/null
su_sh "svc power stayon false" >/dev/null 2>&1
su_sh "cmd statusbar collapse" >/dev/null 2>&1

if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "OS5 复刻冒烟：全绿（证据 $LOG_ON / $LOG_OFF）"
  exit 0
fi
echo "OS5 复刻冒烟：失败 ${#FAILS[@]} 条"
for f in "${FAILS[@]}"; do echo " - $f"; done
exit 1
