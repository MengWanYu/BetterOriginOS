#!/usr/bin/env bash
# A11（OriginOS Ocean）音乐卡片风格冒烟：断言全部来自模块自身的度量日志，不做截图验收。
# 用法：work/a11-card-gate.sh [序列号] [os3|os5]
#   os3 = 走 su 的 DSU 环境（解锁靠上滑，无密码）
#   os5 = 宿主 OriginOS 5（重启 SystemUI 会进安全锁，脚本会在检测到锁屏时停下等人，不猜密码）
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
MODE="${2:-os3}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# MSYS_NO_PATHCONV=1（adb 需要）会连**送给 Windows 原生程序**的路径也不转换，
# python.exe 于是去找 C:\c\Users\... 然后 "No such file"。送 python 的路径一律过 win。
win() { cygpath -m "$1"; }
OUT="$ROOT/work/z8os3-live"
LOG="$OUT/a11-card-$MODE.log"
mkdir -p "$OUT"
FAILS=()

sh_run() { $ADB shell "$1" 2>&1 | tr -d '\r'; }
su_sh() {
  if [ "$MODE" = "os3" ]; then $ADB shell su -c "$1" 2>&1 | tr -d '\r'
  else $ADB shell "$1" 2>&1 | tr -d '\r'; fi
}
fail() { FAILS+=("$1"); echo "  FAIL $1"; }
ok()   { echo "  ok   $1"; }

restart_sysui() {
  local old new i
  old=$(su_sh "pidof com.android.systemui")
  su_sh "kill -9 \$(pidof com.android.systemui)" >/dev/null 2>&1
  for i in $(seq 1 30); do
    sleep 1
    new=$(su_sh "pidof com.android.systemui")
    if [ -n "$new" ] && [ "$new" != "$old" ]; then return 0; fi
  done
  return 1
}

unlocked() {
  local ks
  ks=$(su_sh "dumpsys window | grep -m1 -E 'mShowingLockscreen|isKeyguardShowing'")
  case "$ks" in *true*) return 1 ;; *) ;; esac
  case "$(su_sh 'dumpsys power | grep -m1 mWakefulness=')" in *Asleep*) return 1 ;; esac
  return 0
}

echo "== 0) 前提复核：Y7s 实读取证（不连设备）=="
# A11 卡的尺寸/圆角/色链/按钮数前提全部来自 Y7s 的 dumpsys + framework-res 实读。
# 这条跑红 = 前提文件缺失或被改动，此时真机"像不像"的结论没有意义，先修前提再装机。
if PYTHONIOENCODING=utf-8 python "$(win "$ROOT/work/y7s_a11_evidence_check.py")" >"$ROOT/work/a11-evidence.out" 2>&1; then
  ok "$(tail -1 "$ROOT/work/a11-evidence.out")"
else
  fail "Y7s 前提核对未通过（详见 work/a11-evidence.out）"
fi
# 取色"像素网格"（先缩整图再换算 region）跑偏只会表现为"颜色有点不对"，眼睛判不了，
# 所以把模块字节码在桌面 JVM 上直接跑一遍比对
if PYTHONIOENCODING=utf-8 python "$(win "$ROOT/work/a11_sampling_selftest.py")" >"$ROOT/work/a11-sampling.out" 2>&1; then
  ok "A11 取色网格与 A11 一致"
else
  fail "A11 取色网格自检失败（详见 work/a11-sampling.out）"
fi
# 六族选择 / 背景块 / 前景色的算法也要锁：错了只表现为"配色有点怪"
if PYTHONIOENCODING=utf-8 python "$(win "$ROOT/work/a11_selection_selftest.py")" >"$ROOT/work/a11-selection.out" 2>&1; then
  ok "A11 取色选择逻辑与 androidx Target 模型一致"
else
  fail "A11 取色选择逻辑自检失败（详见 work/a11-selection.out）"
fi
# 取色的源头是量化：median-cut 与"直方图取前 N"会给出不同的候选色
if PYTHONIOENCODING=utf-8 python "$(win "$ROOT/work/a11_quantize_selftest.py")" >"$ROOT/work/a11-quantize.out" 2>&1; then
  ok "A11 量化器与 ColorCutQuantizer 一致"
else
  fail "A11 量化器自检失败（详见 work/a11-quantize.out）"
fi

echo "== 1) 环境前置 =="
SDK=$(su_sh "getprop ro.build.version.sdk")
echo "  sdk=$SDK mode=$MODE"
case "$SDK" in 33|35) ;; *) fail "sdk=$SDK，本门禁只支持 A13(33)/A15(35)";; esac

echo "== 2) 切到 a11 风格并重启 SystemUI =="
STYLE0=$(su_sh "settings get global betteroriginos_media_card_style"); [ -z "$STYLE0" ] || [ "$STYLE0" = "null" ] && STYLE0=os5
EN0=$(su_sh "settings get global betteroriginos_feature_media_card")
su_sh "settings put global betteroriginos_feature_media_card 1" >/dev/null
su_sh "settings put global betteroriginos_media_card_style a11" >/dev/null
[ "$(su_sh 'settings get global betteroriginos_media_card_style')" = "a11" ] || fail "style 没切到 a11"
su_sh "logcat -c" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
restart_sysui || fail "SystemUI 二次重启失败（装包后首启可能仍是旧 dex）"
sleep 8
su_sh "input keyevent 224" >/dev/null; sleep 1
if [ "$MODE" = "os3" ]; then
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
fi
if ! unlocked; then
  echo "  设备仍在锁屏/息屏：OS5 宿主重启 SystemUI 后是安全锁，脚本不猜密码。"
  echo "  请人工解锁后重跑本脚本（设置已就位：style=a11）。"
  exit 3
fi
P=$(su_sh "pidof com.android.systemui"); [ -n "$P" ] || fail "SystemUI 没起来"
ok "SystemUI pid=$P"

echo "== 3) 起播并打开面板 =="
su_sh "svc power stayon true" >/dev/null 2>&1
# 开机那一拍的日志（钩子安装数、landing check）先落盘，后面一律追加不再覆盖
su_sh "logcat -d -v threadtime --pid=$P" > "$LOG" 2>/dev/null || su_sh "logcat -d -v threadtime" > "$LOG"

# restyling / a11 card spec 只在**建卡那一拍**打一次。SystemUI 刚重启时 vivo 自己每秒几百行，
# logd 的 "Single process limit 250/s drop 65 lines" 会把我们夹在中间的那两行丢掉
# （11-04 早真机实测：media bind 与 preview observer registered 都在，中间两行没了）
# ⇒ 那是丢样本，不是产品坏了。显式重投一次媒体通知，让建卡落在安静窗口里，缺见证就再来一遍。
play_once() {
  su_sh "am force-stop com.android.bbkmusic" >/dev/null 2>&1; sleep 2
  su_sh "am start -n com.android.bbkmusic/.MainActivity" >/dev/null 2>&1 || \
    su_sh "monkey -p com.android.bbkmusic 1" >/dev/null 2>&1
  sleep 5
  su_sh "input keyevent 126" >/dev/null; sleep 3       # MEDIA_PLAY
  su_sh "input keyevent 3" >/dev/null; sleep 1         # HOME，避免播放器盖住面板
  su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
  su_sh "logcat -d -v threadtime" >> "$LOG"
}
play_once
if [ "$(grep -ac 'a11 card spec' "$LOG")" -eq 0 ]; then
  echo "  note 建卡那一拍被 logd 限流丢了：重投一次媒体通知再取"
  play_once
fi
# 逐段**追加**而不是覆盖：模块每次建卡都打十几行 ancestor/rowChild 取证，环形缓冲区
# 会把"建卡那一拍"的 a11 card spec 挤出去，覆盖式捕获就变成假失败（11-03 夜真机踩过）。
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
su_sh "logcat -d -v threadtime" >> "$LOG"

# 展开一次卡片：A11 卡的收起态与展开态是**两套排版**（14sp+12dp / 16+14+14sp），
# 只看收起态等于展开态从没在真机上验过（第一轮就是这样）。用模块自己的调试键展开首行，
# 比盲 tap 可靠（tap 在这台机上会把面板甩掉或点开播放器）。
su_sh "settings put global betteroriginos_notify_expand_test 1" >/dev/null; sleep 4
su_sh "logcat -d -v threadtime" >> "$LOG"
su_sh "settings put global betteroriginos_notify_expand_test 0" >/dev/null

echo "== 4) 代码级断言（度量日志）=="
BIND=$(grep -ac "media bind" "$LOG")
[ "$BIND" -ge 1 ] && ok "媒体行绑定钩子命中 $BIND 次" \
  || fail "没有任何 media bind 日志：媒体行没进面板（或没在放歌）"
RESTYLE=$(grep -ac "restyling style=a11" "$LOG")
SPEC=$(grep -ac "a11 card spec" "$LOG")
# restyling 只在建卡那一拍打一条，会和 spec 一起被 logd 的每进程 250/s 限流冲掉；
# 同一拍里的 spec 见证模块会低频补发，所以"a11 路径跑过"用两者任一证明，两个都没有才是真红。
[ "$RESTYLE" -ge 1 ] && ok "a11 风格 restyle 命中 $RESTYLE 次" \
  || { [ "$SPEC" -ge 1 ] && ok "restyle 行被限流冲掉，用 a11 card spec 见证（$SPEC 条）证明 a11 建卡跑过" \
       || fail "a11 风格没有 restyle，也没有规格见证：style 未生效或卡片构建失败"; }
PAL=$(grep -ac "a11 palette" "$LOG")
[ "$PAL" -ge 1 ] && ok "A11 取色链跑了 $PAL 次" || fail "没有 a11 palette 日志（取色链没执行）"
SPEC=$(grep -ac "a11 card spec" "$LOG")
[ "$SPEC" -ge 1 ] && ok "a11 规格见证日志 $SPEC 条" || fail "没有 a11 card spec 见证日志"
GEOM=$(grep -ac "a11 art state=" "$LOG")
[ "$GEOM" -ge 1 ] && ok "a11 封面几何见证 $GEOM 条" || fail "没有 a11 art state 日志"
PYTHONIOENCODING=utf-8 python "$(win "$ROOT/work/verify_os5_mirror.py")" --media-only "$(win "$LOG")" && ok "尺寸/取色数值断言通过" \
  || fail "verify_os5_mirror.py 有失败项（见 $LOG）"

echo "== 4b) 另一条线的非干扰断言（OS5 复刻 × A11 卡）=="
# 复刻钩子在 A15 上**不该装**（NotifyRowFeature 用 OsProfile.isA13OrLower() 关门，:420-423）。
# 装了就是跨版本越界：系统本来就有一枚 NotificationClearAllImageView，再补一枚就重叠，
# 半径也会被第二次改写。这条断言只看日志，不靠肉眼。
MIRROR=$(grep -ac "os5 mirror hooks installed" "$LOG")
if [ "$MODE" = "os5" ]; then
  [ "$MIRROR" = "0" ] && ok "OS5 上没装 OS5 复刻钩子（原生即目标外观，零干扰）" \
    || fail "OS5 上装了复刻钩子 $MIRROR 次：会长出第二枚清除钮并重复改半径"
else
  [ "$MIRROR" -ge 1 ] && ok "A13 上复刻钩子已装（×$MIRROR）" \
    || fail "A13 上没装复刻钩子：另一半移植在这台机没有落点"
  SEP=$(su_sh 'settings get global betteroriginos_notify_row_sep')
  [ "$SEP" = "null" ] && SEP=off
  if [ "$SEP" = "off" ]; then
    ATTACH=$(grep -acE "os5_mirror: clear-all (attached|tap|at) " "$LOG")
    [ "$ATTACH" = "0" ] && ok "sep=off 时浮动清除钮没挂载（0 行）" \
      || fail "sep=off 却有 $ATTACH 行 clear-all 日志：复刻开关没关住"
  else
    echo "     sep=$SEP（本门禁不改它）：跳过挂载负对照"
  fi
fi
CRASH=$(grep -cE "Fatal EXCEPTION|BadForegroundServiceNotification" "$LOG")
[ "$CRASH" = "0" ] && ok "无致命异常" || fail "日志里有 $CRASH 条致命异常"
P2=$(su_sh "pidof com.android.systemui")
[ "$P2" = "$P" ] && ok "全程 SystemUI pid 不变（$P2）" || fail "SystemUI 中途重启：$P -> $P2"

echo "== 5) 收尾 =="
su_sh "am force-stop com.android.bbkmusic" >/dev/null 2>&1
su_sh "settings put global betteroriginos_media_card_style $STYLE0" >/dev/null
[ -n "$EN0" ] && [ "$EN0" != "null" ] && su_sh "settings put global betteroriginos_feature_media_card $EN0" >/dev/null
su_sh "svc power stayon false" >/dev/null 2>&1
su_sh "cmd statusbar collapse" >/dev/null 2>&1
su_sh "input keyevent 223" >/dev/null 2>&1

if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "A11 卡片冒烟：全绿（证据 $LOG）"
  exit 0
fi
echo "A11 卡片冒烟：失败 ${#FAILS[@]} 条"
for f in "${FAILS[@]}"; do echo " - $f"; done
exit 1
