#!/usr/bin/env bash
# 结果级取证：一次跑完「静止 → 点开展开 → 滚动+从收纳栏划回」三态，
# 每态产出**成对**的 dump + 日志（文件名带 apk hash），判定全部交给 verify_row_layout.py。
# 纪律：只用 uiautomator dump 与模块见证日志，不截图、不退出 DSU、不提交 git。
# 用法：work/os3-evidence-rowlayout.sh [序列号] [density]
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
DENSITY="${2:-3.0}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/work/z8os3-live"
mkdir -p "$OUT"
HASH=$(sha256sum "$ROOT/module/build/BetterOriginOS.apk" | cut -c1-12)

su_sh() { $ADB shell su -c "$1" 2>&1 | tr -d '\r'; }
fail() { echo "  FAIL $1"; RC=1; }
ok()   { echo "  ok   $1"; }
# 脚本开头 export 了 MSYS_NO_PATHCONV=1（adb 的 /sdcard 远端路径需要），它同时让 Git Bash
# 不再给 Windows 原生程序转路径 —— 直接把 /c/... 交给 python.exe / adb.exe 就去找
# C:\c\... 然后 "No such file"（这一版第一次跑就是这样把三态 dump 全丢掉的）。
# ⇒ 凡是给 Windows 侧程序的**本地**路径，一律过 cygpath -m。
wpath() { cygpath -m "$1"; }
# 取 dump 里"最宽通知行"的高度：展开是否真的发生，用行高前后对比证明，
# 而不是信手势命令返回 0（上一轮 tap 其实把面板关回了桌面，命令一样成功）。
maxh() {
  grep -o 'resource-id="com.android.systemui:id/expandableNotificationRow"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' "$1" \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' \
    | awk '{ h = $4 - $2; w = $3 - $1; if (w > 800 && h > m) m = h } END { print m + 0 }'
}

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

# 成对取证：dump 与 log 必须来自同一个观察窗口，否则"布局对、圆角错"这类交叉证据没法用
# 取帧前置：播着的媒体卡每帧都在自愈重绘，窗口**永远不会空闲**，
# uiautomator 直接 "ERROR: could not get idle state."（本轮三态 dump 全空就是这么废的；
# 会话一 dispatch pause，dump 立刻成功）。观察对象是通知行，暂停会话不影响被判据。
pause_media() {
  local st
  st=$(su_sh "dumpsys media_session" | grep -a "state=PlaybackState" | head -1)
  case "$st" in
    *state=3,*|*state=6,*)
      su_sh "cmd media_session dispatch pause" >/dev/null
      sleep 1
      echo "     取帧前暂停播放会话（原 ${st##*state=}）"
      ;;
  esac
}

capture() {
  local tag="$1" attempt dmsg
  su_sh "logcat -d -v threadtime" > "$OUT/row-$HASH-$tag.log"
  pause_media
  # uiautomator 在窗口"永远不空闲"时是硬失败（本轮就撞上过一次：投完 20 多条样本、
  # 面板里有活着的媒体行/未完成的动画，三态 dump 全空，整轮取证作废）。
  # 这是观察窗口的瞬时状态，不是被测实现的问题 ⇒ 等它静下来重试，三次都空才判这一帧作废。
  dmsg="$OUT/.dump-$tag.txt"
  for attempt in 1 2 3; do
    su_sh "rm -f /sdcard/row.xml; uiautomator dump /sdcard/row.xml" > "$dmsg" 2>&1
    grep -qa "dumped to" "$dmsg" && { [ "$attempt" -gt 1 ] && echo "     $tag: 第 $attempt 次 dump 成功"; break; }
    # 失败时先记现场再自救：息屏、面板被收掉、焦点跑掉，在 uiautomator 那边都表现为
    # "could not get idle state"，不记就分不出是哪一种（本轮就是这么连丢三帧）。
    { echo "--- attempt $attempt 失败现场"
      su_sh "dumpsys power | grep mWakefulness="
      su_sh "dumpsys window | grep -a 'mCurrentFocus=Window' | head -1"; } >> "$dmsg"
    su_sh "input keyevent 224" >/dev/null; sleep 1
    su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 1
    su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
  done
  $ADB pull -q /sdcard/row.xml "$(wpath "$OUT/row-$HASH-$tag.xml")" 2>/dev/null
  [ -s "$OUT/row-$HASH-$tag.xml" ] || fail "$tag: 没拿到 uiautomator dump（$(head -1 "$dmsg")）"
  # 焦点必须还在通知面板上，否则这一帧根本不是我们要的那一屏。
  # 注意：dumpsys window 里 mCurrentFocus 会出现两次（一次是 null 的残留段），取带 Window{ 的那条。
  FOCUS=$(su_sh "dumpsys window" | grep -a "mCurrentFocus=Window" | head -1)
  case "$FOCUS" in
    *NotificationShade*|*NotificationPanel*) ;;
    *) fail "$tag: 焦点不在通知面板（${FOCUS:-读不到}），这一帧作废" ;;
  esac
  echo "     $tag -> row-$HASH-$tag.{xml,log}"
}

RC=0
echo "== 0) 取证对象 =="
# 上一轮就吃过这个亏：build.sh 编译失败但 `| tail` 把退出码洗成 0，
# 脚本装着**上一个包**跑完整套取证，还全绿 —— 取证对象必须是可核对的那一个 APK。
if [ ! -s "$ROOT/module/build/BetterOriginOS.apk" ]; then
  echo "  FAIL module/build/BetterOriginOS.apk 不存在（构建失败？），拒绝取证"
  exit 2
fi
echo "  apk sha256[:12]=$HASH density=$DENSITY"
REMOTE=$(su_sh "sha256sum \$(pm path com.betteroriginos 2>/dev/null | head -1 | cut -d: -f2)" \
  | grep -aoE '^[0-9a-f]{12}')
if [ "$REMOTE" != "$HASH" ]; then
  echo "  FAIL 设备上装的是 ${REMOTE:-读不到}，本地构建是 $HASH —— 先 adb install 再取证"
  exit 2
fi
echo "  ok   设备上的模块 APK 与本地构建一致（$REMOTE）"

echo "== 1) 起底：sep=os5 + verify=1 =="
su_sh "settings put global betteroriginos_feature_notify_row 1" >/dev/null
su_sh "settings put global betteroriginos_notify_row_sep os5" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
su_sh "svc power stayon true" >/dev/null
restart_sysui || fail "SystemUI 重启失败"
sleep 3
restart_sysui || fail "SystemUI 二次重启失败（装包后首启可能仍是旧 dex）"
sleep 6
unlock
# 上一轮连吃三次 "could not get idle state"：现场 mWakefulness=Awake 但 mCurrentFocus 是
# org.lsposed.manager —— 屏幕上盖着别人的 App 时，面板不在最上层，uiautomator 取的是那一层的
# 窗口树（它有自己在动的列表），而我们的行根本不在观察对象里。先把非系统前台清掉再取证。
FOCUS=$(su_sh "dumpsys window | grep -a 'mCurrentFocus=Window' | head -1")
case "$FOCUS" in
  *com.android.systemui*|*NotificationShade*|*Launcher*) ;;
  *)
    FP=$(printf '%s' "$FOCUS" | sed -E 's/.*u0 ([a-zA-Z0-9._]+)\/.*/\1/')
    if [ -n "$FP" ]; then
      echo "     前台是 $FP（不是面板），先 force-stop 再取证"
      su_sh "am force-stop $FP" >/dev/null
      sleep 2
    fi
    ;;
esac
# 钩子装载证据必须在清零之前取：接管方式（header-time=push-only / column-hook）只在进程启动时打一次，
# 清掉之后三态日志里永远分不清"哪种接管在跑"与"这轮没抓到日志"。
su_sh "logcat -d -v threadtime" | grep -aE "notify row feature installed header-time=" \
  > "$OUT/row-$HASH-install.txt" || true
HTM=$(grep -ao "installed header-time=[a-z-]*" "$OUT/row-$HASH-install.txt" | tail -1 | cut -d= -f2)
if [ -z "$HTM" ]; then
  fail "模块没自报 header-time 接管方式（证据 row-$HASH-install.txt：$(head -2 "$OUT/row-$HASH-install.txt" | tr '\n' ' ')）"
else
  ok "header 时间接管方式=$HTM"
fi
su_sh "logcat -c" >/dev/null     # 清零必须在投递样本**之前**：polish 与 align 都发生在绑定时，
su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1   # 清在投递之后就只剩"一条都没抓到"的空绿
# 先把列表撑到溢出：没有收纳栏，症状②③这一轮就只是"没测到"。
# 走 Activity 而不是 broadcast —— 这台机上发给 .debug.NotifyTestReceiver 的 broadcast
# result=0 但根本没进 onReceive（实测），而 Activity 路径是历史验证过的投递口。
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postgroup" >/dev/null 2>&1
sleep 4
# 媒体行是 L9/L10 的**负对照**：它不归 notify_row 接管，钩子对它只能记 not-taken。
# 没有这条例外，"只搬被接管的行"和"把所有行都搬了"在日志里长得一样。
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postmedia" >/dev/null 2>&1
sleep 4
su_sh "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postbulk" >/dev/null 2>&1
sleep 5
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postall" >/dev/null 2>&1
sleep 5
su_sh "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify post" >/dev/null 2>&1
sleep 5
su_sh "input keyevent 3" >/dev/null; sleep 1
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
sleep 16   # 圆角度量是 15s 一跳的心跳，等短了会拿到"这帧没测到"的假失败
capture idle

echo "== 2) 点开展开（症状①：所有大图标抖一下）=="
pause_media
su_sh "uiautomator dump /sdcard/tap.xml" >/dev/null
$ADB pull -q /sdcard/tap.xml "$(wpath "$OUT/tmp-tap.xml")" 2>/dev/null
read -r TX TY <<<"$(python - "$(wpath "$OUT/tmp-tap.xml")" <<'PY'
import io, re, sys
# 展开走 **expand_button（右下角箭头）**：在行身上上滑这台机不认（实测最高行 642->642，
# 手势被当成滚列表），tap 行中心又会命中 contentIntent 把面板带走（上一轮焦点掉回桌面）。
# 箭头是系统自己的展开控件，点它必然展开，也不会清除通知。
text = io.open(sys.argv[1], encoding="utf-8", errors="replace").read()

def centers(pat):
    out = []
    for m in re.finditer(pat + r'[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text):
        l, t, r, b = (int(x) for x in m.groups())
        if r - l > 20 and b - t > 20:
            out.append(((l + r) // 2, (t + b) // 2, b - t))
    return out

chevrons = centers(r'resource-id="android:id/expand_button"')
rows = centers(r'resource-id="com.android.systemui:id/expandableNotificationRow"')
if chevrons:
    x, y, _ = chevrons[0]
elif rows:
    x, y, _ = rows[0]
else:
    x = y = -1
print(x, y)
PY
)"
if [ -z "${TX//[!0-9-]/}" ]; then
  fail "展开点：取坐标失败（TX='${TX:-}'），症状①没测到"
elif [ "$TX" -lt 0 ]; then
  fail "展开点：dump 里没找到 expandableNotificationRow 节点，症状①没测到"
else
  su_sh "input tap $TX $TY" >/dev/null; sleep 4
  capture expand
fi

echo "== 3) 滚动 + 从收纳栏划回（症状②③：丢圆角、收纳栏闪烁）=="
# 收起那一下可能把整个面板关掉；滚动必须在面板打开时发生
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
for i in 1 2 3 4 5; do
  su_sh "input swipe 540 2000 540 900 700" >/dev/null; sleep 1    # 快滑：把行挤进收纳栏
  su_sh "input swipe 540 900 540 2000 700" >/dev/null; sleep 1    # 反向滑回来
done
# 慢速把收纳栏后面的行拉回可见区（症状②的发生条件），然后静止 3s 让系统落回稳态
su_sh "input swipe 540 2050 540 1250 1500" >/dev/null; sleep 2   # 慢速把收纳栏里的行拉回可见区
# 停在列表底部时屏幕上只剩组行，独立行的 66dp 列（L3/L4）就没材料了 —— 回到顶部再取证。
su_sh "input swipe 540 900 540 2100 700" >/dev/null; sleep 1
su_sh "input swipe 540 900 540 2100 700" >/dev/null; sleep 3
capture scroll

echo "== 4) 结果级验收（逐态）=="
PY_VRL=$(cygpath -m "$ROOT/work/verify_row_layout.py")
# 接管方式只在进程启动那一拍打一次，而三态窗口都在 `logcat -c` 之后 ⇒ 必须把清零前那份
# 证据一起交给验证器，否则"钩子没装"与"这轮没抓到日志"again 分不开。
INSTALL_EV="$(cygpath -m "$OUT/row-$HASH-install.txt")"
for tag in idle expand scroll; do
  echo "  -- $tag"
  if [ -s "$OUT/row-$HASH-$tag.xml" ]; then
    PYTHONIOENCODING=utf-8 python "$PY_VRL" \
      --dump "$(cygpath -m "$OUT/row-$HASH-$tag.xml")" \
      --log "$(cygpath -m "$OUT/row-$HASH-$tag.log")" \
      --install "$INSTALL_EV" \
      --density "$DENSITY" | tail -25
  else
    # uiautomator 拿不到 idle 时不能整套作废：几何判据本轮标为未测，日志侧判据照跑。
    # 落点证据由模块自己实测（col measure），它量的就是屏幕上那一帧。
    echo "     （$tag：无 dump，L1~L9 本轮未测，只判日志侧）"
    PYTHONIOENCODING=utf-8 python "$PY_VRL" --log-only \
      --log "$(cygpath -m "$OUT/row-$HASH-$tag.log")" \
      --install "$INSTALL_EV" \
      --density "$DENSITY" | tail -25
  fi
  [ "${PIPESTATUS[0]}" = "0" ] || fail "$tag: verify_row_layout.py 有失败"
done

echo "== 5) 症状量化（同一批日志里的计数，不靠眼观）=="
# ① 抖动 = **展开手势期间**同一行被反复 polish。polish 在绑定时本来就跑两次（实测同一行
# 相隔 3ms 各一次，图标已复用、无销毁重建，观感上不抖），所以"整窗口计数 ≤1"会把正常的
# 双绑定判成回归；必须按时间切开：投递阶段（idle 抓帧之前）与手势阶段（之后）分别统计。
J=$(python - "$(cygpath -m "$OUT/row-$HASH-idle.log")" \
        "$(cygpath -m "$OUT/row-$HASH-expand.log")" <<'PY'
import io, re, sys, collections


def events(path):
    out = []
    pat = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d).*restyled light pkg=(\S+)"
                     r".*?row=([0-9a-f]+)")
    for line in io.open(path, encoding="utf-8", errors="replace"):
        m = pat.search(line.strip())
        if m:
            out.append(m.groups())
    return out


bind, all_ev = events(sys.argv[1]), events(sys.argv[2])
cut = max([t for t, _, _ in bind], default=None)
if cut is None:
    print("0 0 0 0 0 0")     # 连绑定阶段的 polish 都没抓到 = 日志窗口不对，不是"没抖动"
    raise SystemExit
after = [e for e in all_ev if e[0] > cut]
g = collections.Counter(r for _, _, r in after)
b = collections.Counter(r for _, _, r in bind)
print("%d %d %d %d %d %d" % (len(b), sum(b.values()),
                             max(b.values()) if b else 0,
                             len(g), sum(g.values()),
                             max(g.values()) if g else 0))
PY
)
if [[ "$J" =~ ^[0-9]+\ [0-9]+\ [0-9]+\ [0-9]+\ [0-9]+\ [0-9]+$ ]]; then
  read -r BROWS BTOTAL BMAX GROWS GTOTAL GMAX <<<"$J"
  echo "     投递阶段：polish 行数=$BROWS 总次数=$BTOTAL 单行最多=$BMAX"
  echo "     展开手势阶段：polish 行数=$GROWS 总次数=$GTOTAL 单行最多=$GMAX"
  if [ "$BROWS" -eq 0 ]; then
    fail "整轮没抓到任何 polish：日志窗口不对（判据空转，不算测过）"
  elif [ "$GMAX" -le 1 ]; then
    ok "展开手势窗口内每行最多 polish 一次（$GROWS 行/$GTOTAL 次）——症状①无重复指纹"
  else
    fail "展开手势窗口内有行被 polish $GMAX 次 —— 抖动回归"
  fi
else
  fail "展开窗口 polish 计数失败（日志缺失或行形变了：'$J'）"
fi
# 子行口径：展开态子行 h≈255、收起态露出的 h≈57（真机实测值），数量变化才代表"组真的开了"
nkids() {
  grep -o 'resource-id="com.android.systemui:id/expandableNotificationRow"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' "$1"     | sed -E 's/.*bounds="\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]"/ /'     | awk '{h=$2-$1; if (h>=200 && h<=300) n++} END{print n+0}'
}
H0=$(maxh "$OUT/row-$HASH-idle.xml"); H1=$(maxh "$OUT/row-$HASH-expand.xml" 2>/dev/null)
K0=$(nkids "$OUT/row-$HASH-idle.xml"); K1=$(nkids "$OUT/row-$HASH-expand.xml" 2>/dev/null)
if [ "${K1:-0}" -gt "${K0:-0}" ] || [ "${H1:-0}" -gt "${H0:-0}" ]; then
  ok "展开确实发生：展开态子行 $K0 -> $K1 条，最高行 $H0 -> $H1 px（症状①的观察窗口有效）"
else
  fail "展开没发生（子行 ${K0:-?} -> ${K1:-?} 条，最高行 ${H0:-?} -> ${H1:-?} px）：症状①这一轮没测到"
fi
# ③ 收纳栏闪烁 = shelf/button 可见性跳变次数（该日志只在变化时打，所以行数就是翻转数）
FLIP=$(grep -ac "os5_mirror: shelf/button" "$OUT/row-$HASH-scroll.log")
# 收纳栏有没有被观测到，用**每轮都打**的圆角度量（shelf=1 那一行）判定；
# os5_mirror 的 mirrorShelf 只在 updateAppearance/onConfigurationChanged 里打，
# 静止的一屏抓不到它，拿它当"收纳栏存在"的判据会把正常状态误报成没测到。
SHELF=$(grep -acE "notify_row\.roundness phase=.*shelf=1" "$OUT/row-$HASH-scroll.log")
if [[ ! "$FLIP" =~ ^[0-9]+$ ]] || [[ ! "$SHELF" =~ ^[0-9]+$ ]]; then
  fail "跳变/度量计数读不出来（FLIP='$FLIP' SHELF='$SHELF'）"
  FLIP=99; SHELF=0
fi
echo "     滚动窗口：shelf 可见性跳变=$FLIP 次，shelf 圆角度量=$SHELF 次"
[ "$FLIP" -le 2 ] && ok "滚动期间收纳栏可见性基本没跳（$FLIP 次）" \
                  || fail "滚动期间收纳栏可见性跳了 $FLIP 次 —— 闪烁回归（修复前实测 11 次）"
[ "$SHELF" -ge 1 ] || fail "滚动窗口里没有 shelf 圆角度量（收纳栏整段没被观测到，这条不算验证）"

echo "== 6) 收尾 =="
su_sh "svc power stayon false" >/dev/null
su_sh "cmd statusbar collapse" >/dev/null
if [ "$RC" = "0" ]; then
  echo "结果级取证：全绿（证据 work/z8os3-live/row-$HASH-{idle,expand,scroll}.{xml,log}）"
else
  echo "结果级取证：有失败（证据同上）"
fi
exit $RC
