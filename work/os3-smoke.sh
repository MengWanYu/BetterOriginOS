#!/usr/bin/env bash
# OriginOS 3（Z8 DSU）通知行/媒体功能冒烟：装机后可重复跑的验收闸门。
# 用法：work/os3-smoke.sh [序列号] [group_mode]
#   NIGHT=1 work/os3-smoke.sh        # 整轮在深色模式下跑（收尾自动恢复浅色）
#   FONT=1.3 work/os3-smoke.sh     # 整轮在大字号下跑（收尾恢复原字号）
#   work/os3-smoke.sh                      # 默认 Z8，沿用当前 group_mode
#   work/os3-smoke.sh 10AE4Y0MDE000BL separate
# 退出码 0 = 全绿；非 0 = 有断言失败（打印在最后）。
# 依赖：adb 已连接并授权；模块 APK 已安装且 LSPosed 作用域含 systemui/systemuiplugin。
set -u
set -o pipefail

SERIAL="${1:-10AE4Y0MDE000BL}"
MODE="${2:-}"
# NIGHT=1 时整轮在深色模式下跑（OS3 深色是 2026-10-28 才首次实测的维度，别只跑浅色）
NIGHT="${NIGHT:-0}"
# FONT=1.3 时整轮在该字号下跑（大字号是 OS3 上另一条只测过 OS5 的维度）
FONT="${FONT:-}"
ADB="adb -s $SERIAL"
export MSYS_NO_PATHCONV=1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WINROOT="$(cygpath -w "$ROOT")"   # 喂给 Windows python 的路径必须是 Windows 形式
OUT="$ROOT/work/z8os3-live"
LOG="$OUT/smoke.log"
PNG="$OUT/smoke.png"
mkdir -p "$OUT"
FAILS=()

su_sh() { $ADB shell su -c "$1" 2>&1 | tr -d '\r'; }
fail() { FAILS+=("$1"); echo "  FAIL $1"; }
ok()   { echo "  ok   $1"; }

# 解锁：灭屏唤醒 + 两次上滑（这台机没有密码，但一次上滑只到锁屏通知页）
unlock() {
  su_sh "input keyevent 224" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 1
  su_sh "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
}

if [ -n "$FONT" ]; then
  echo "== 0) 切字号 -> $FONT =="
  FONT0=$(su_sh "settings get system font_scale"); [ "$FONT0" = "null" ] && FONT0=1.0
  su_sh "settings put system font_scale $FONT" >/dev/null; sleep 2
  [ "$(su_sh "settings get system font_scale")" = "$FONT" ] || fail "字号没切过去（$FONT）"
fi

if [ "$NIGHT" = "1" ]; then
  echo "== 0) 切到深色模式 =="
  su_sh "cmd uimode night yes" >/dev/null; sleep 2
  [ "$(su_sh 'cmd uimode night')" = "Night mode: yes" ] || fail "没能切到深色模式"
fi

echo "== 1) 环境前置（必须确认在 DSU/OS3，别把宿主当被测环境）=="
SDK=$(su_sh "getprop ro.build.version.sdk"); OS=$(su_sh "getprop ro.vivo.os.build.display.id")
echo "  $OS / sdk $SDK"
[ "$SDK" = "33" ] || fail "不是 Android 13（sdk=$SDK），可能已退回宿主系统"
case "$OS" in *"OriginOS 3"*) ;; *) fail "ro.vivo.os.build.display.id 不是 OriginOS 3（$OS）" ;; esac
PID0=$(su_sh "pidof com.android.systemui")
[ -n "$PID0" ] || fail "SystemUI 没在跑"

if [ -n "$MODE" ]; then
  echo "== 1b) 切换 group_mode -> $MODE =="
  su_sh "settings put global betteroriginos_notify_row_group_mode $MODE"
fi

echo "== 2) 重启 SystemUI 让钩子重新装载 =="
VERIFY0=$(su_sh "settings get global betteroriginos_notify_verify")   # 收尾要还原成原值
[ "$VERIFY0" = "null" ] && VERIFY0=0
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
su_sh "logcat -c" >/dev/null
su_sh "kill \$(pidof com.android.systemui)" >/dev/null; sleep 15
unlock
P=$(su_sh "pidof com.android.systemui"); [ -n "$P" ] || fail "重启后 SystemUI 没起来"
ok "SystemUI pid=$P"
INST=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -acE "guards installed|media card feature installed")
[ "$INST" -ge 2 ] && ok "钩子装载记录 $INST 条" || fail "钩子装载记录只有 $INST 条（注入没发生？）"

echo "== 3) 投递样本 + 打开面板 + 慢滚到测试行 =="
# 这台机息屏只有 30s，中途熄屏会让所有行 isShown()=false、测量全部放弃
$ADB shell su -c "svc power stayon true" >/dev/null 2>&1
su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
dbg() {   # 每次投递前必须冷启：Activity 实例已存在时 intent 不会再触发 handleDebugIntent
  su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
  su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify $1" >/dev/null 2>&1
  sleep "${2:-5}"
}
dbg postall 5
# 媒体卡链路要有 MediaStyle 通知在场才会走；postmedia 会新建一个调试会话
dbg postmedia 4
su_sh "input keyevent 3" >/dev/null; sleep 1
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 4
# 快速 swipe 会被当成甩动把面板关掉，列表内滚动必须慢滑
# 真实播放卡会占掉列表顶部约 500px，两次滑不够；滚动次数按覆盖需要给到三次
su_sh "input swipe 540 1700 540 950 900" >/dev/null; sleep 2
su_sh "input swipe 540 1700 540 950 900" >/dev/null; sleep 2
# 滚动直到测试行真的被测到：夜间/大字号下行高不同，固定一次滚动会只测到 2-3 行，
# 让覆盖闸门去 FAIL 等于把门禁调成"看运气"，所以在这里边滚边数，够了才继续
RV=0
for pass in 1 2 3 4 5; do
  su_sh "input swipe 540 1700 540 950 900" >/dev/null; sleep 6
  su_sh "input swipe 540 950 540 1700 900" >/dev/null; sleep 4
  RV=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -ac "notify_row.verify pkg")
  echo "  滚动第 $pass 轮：测量行 $RV 条"
  [ "$RV" -ge 5 ] && break
done
sleep 14
su_sh "logcat -d --pid=$P -v threadtime" > "$LOG"
$ADB exec-out screencap -p > "$PNG"
SK=$(grep -ac "notify_row.verify.skipped" "$LOG")
echo "  测量行 $RV 条 / 放弃 $SK 条（放弃多为样本在可视区外）"

echo "== 4) 代码级校验器 =="
if BO_LOG_FILE="$(cygpath -w "$LOG")" python "$WINROOT\\work\\verify_notify.py" > "$OUT/verifier.out" 2>&1; then
  tail -2 "$OUT/verifier.out" | sed 's/^/  /'
else
  fail "verify_notify.py 报失败（详见 $OUT/verifier.out）"
  grep -a -E "^ - |^FAIL" "$OUT/verifier.out" | head -12 | sed 's/^/     /'
fi
# 覆盖闸门：按校验器自己报的"测到了多少行"判，而不是只看有没有 verify 行
# （ios_stack 模式下多数行是堆叠卡，走 verify.stack 通道）
CHK=$(grep -a -oE "checked [0-9]+ contracted rows, [0-9]+ overlay rows, [0-9]+ headsup rows, [0-9]+ stack rows" "$OUT/verifier.out" | tail -1)
TOTAL=$(echo "$CHK" | grep -aoE "[0-9]+" | awk '{s+=$1} END {print s+0}')
echo "  覆盖：$CHK = $TOTAL 类行"
[ "$TOTAL" -ge 4 ] || fail "实测覆盖只有 $TOTAL 行（<4），这一轮不足以判定；把列表滚到测试行再跑"

echo "== 5) 一键清除式批量移除回归（OS3 无 dismiss_all，也无圆点）=="
# 每次投递前必须 force-stop：Activity 实例已存在时 intent 不会再触发 handleDebugIntent
for i in 1 2 3; do
  su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
  su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postall" >/dev/null 2>&1; sleep 5
  su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
  su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1; sleep 3
  su_sh "am force-stop com.betteroriginos" >/dev/null; sleep 1
done
P2=$(su_sh "pidof com.android.systemui")
[ "$P2" = "$P" ] && ok "3 轮批量移除后 pid 不变（$P2）" || fail "SystemUI 重启了：$P -> $P2"
CRASH=$(su_sh "logcat -d -b crash -v threadtime" | grep -aicE "systemui|betteroriginos")
[ "$CRASH" = "0" ] && ok "crash buffer 干净" || fail "crash buffer 有 $CRASH 条 systemui 相关记录"
GUARD=$(su_sh "logcat -d -v threadtime" | grep -acE "pre-draw guard|deferred task|stack guard|icon guard")
[ "$GUARD" = "0" ] && ok "未触发兜底异常" || echo "  warn 兜底异常 $GUARD 条（说明撞上了原本会崩的路径，看日志）"

echo "== 5b) 媒体卡在场的清除压力（会话销毁与主线程回调竞态）=="
# 一键清除会连媒体通知一起清掉，MediaController 的回调跑在主线程、不受 Xposed 保护：
# 这里刻意把"会话先没、通知后消"的窗口打出来（dbg 内的 force-stop 就是让会话突然死掉）
for i in 1 2; do
  dbg postmedia 5
  su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 2
  dbg cancelmedia 3
done
P3=$(su_sh "pidof com.android.systemui")
[ "$P3" = "$P" ] && ok "媒体通知反复销毁后 pid 不变（$P3）" || fail "SystemUI 重启了：$P -> $P3"
DEAD=$(su_sh "logcat -d -v threadtime --pid=$P" | grep -ac "DeadObjectException")
GUARD2=$(su_sh "logcat -d -v threadtime --pid=$P" | grep -ac "updateSeekable failed")
echo "  死会话异常 $DEAD 条 / updateSeekable 兜底 $GUARD2 次"
[ "$DEAD" = "0" ] && ok "本轮没撞上死会话" \
  || { [ "$GUARD2" -ge 1 ] && ok "撞上死会话但被兜住（未崩溃）" \
       || echo "  warn 有 $DEAD 条死会话异常但兜底日志为 0，需确认异常是否落在别人的回调里"; }

echo "== 5c) overlay 守卫在行回收后是否还挂着 =="
# GUARDED 以前是"装过就算"的布尔标记且从不 remove：行回收后 ViewTreeObserver 换了，
# 旧监听已失效却仍被标记为已装 -> 不再压原生 alpha -> 我们的卡与原生内容同时可见
# （这就是"新旧图标重叠"的机理）。所以要看 overlay 测量是否真的带着 guard=1。
dbg postconv 5
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
for i in 1 2; do su_sh "input swipe 540 1700 540 950 900" >/dev/null; sleep 2; done
su_sh "input swipe 540 950 540 1700 900" >/dev/null; sleep 2
OVLOG=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -a "notify_row.verify.overlay")
OVN=$(printf '%s' "$OVLOG" | grep -ac . )
OVG=$(printf '%s' "$OVLOG" | grep -ac "guard=1")
# 功能判据：有卡的时候，原生收起内容必须被压成 alpha 0（否则卡与原生同时可见 = 图标重叠）。
# 不用"守卫是否挂着"的对象同一性判断——ViewTreeObserver 在 attach 时会合并换实例，必然误判。
OVZERO=$(printf '%s' "$OVLOG" | grep -acE "nativeAlpha=0(\.0*)? ")
if [ "$OVN" = "0" ]; then
  echo "  warn 本轮没有 overlay 测量行（会话通知未被展开或不在可视区），未覆盖"
elif [ "$OVZERO" = "$OVN" ]; then
  ok "overlay 测量 $OVN 条：原生收起内容全部 alpha=0（无重叠绘制）"
else
  fail "overlay 测量 $OVN 条中 $((OVN-OVZERO)) 条原生内容仍可见：卡与原生同时绘制（图标重叠）"
fi

echo "== 5d) 展开态图标去重（OS3 重影：收起大图标与展开 header 小图标同时可见）=="
# A13/vivo 展开一行时不会把收起子视图置为不可见，同一应用的图标会画出两份（大小错位=重影）。
# 修法是"无状态"地按掉展开子视图 header 里的小图标，所以每绑定一行都该留下 ghost dedup 记录；
# targets>=1 才说明真的按掉了东西，全是 0/-1 就是这条修复失效（守卫没挂上或找不到展开子视图）。
# 第 5/5b/5c 步的批量清除会把样本清光、把列表滚走；5d~5f 需要真实可见行，
# 所以这里重投一轮样本并把列表滚回顶部，否则 morph/胶囊断言会静默变成"未覆盖"。
dbg postall 5
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
su_sh "input swipe 540 950 540 1700 900" >/dev/null; sleep 2
# 先制造一次"真的展开"：调试把手会对第一条带自绘卡的行 setUserExpanded(true)；
# 再触发一次（值回 0）让把手收起同一行 —— 图标 morph 的往返两个方向都要覆盖。
su_sh "settings put global betteroriginos_notify_expand_test 1" >/dev/null; sleep 3
su_sh "settings put global betteroriginos_notify_expand_test 0" >/dev/null; sleep 3
GD=$(su_sh "logcat -d --pid=$P -v threadtime")
RESID=$(printf '%s' "$GD" | grep -acE "notify_row: fade (con|exp) to=")
REAP=$(printf '%s' "$GD" | grep -ac "ghost reapply")
ACT=$((${RESID:-0} + ${REAP:-0}))
GDLOG=$(printf '%s' "$GD" | grep -a "notify_row: ghost dedup")
GDN=$(printf '%s' "$GDLOG" | grep -ac . )
GDEFF=$(printf '%s' "$GDLOG" | grep -acE "targets=([1-9][0-9]*)" )
GDMEDIA=$(printf '%s' "$GDLOG" | grep -ac "cls=Media" )
EXPANDED=$(printf '%s' "$GD" | grep -ac "debug expand pkg=")
if [ "$GDN" = "0" ]; then
  fail "ghost dedup 一条都没有：展开子视图没被解析到，重影修复处于失效状态"
elif [ "$GDEFF" = "0" ]; then
  fail "ghost dedup $GDN 条但全部 targets=0/-1：没有真正按掉任何图标，重影仍在"
elif [ "$GDMEDIA" != "0" ]; then
  fail "ghost dedup 命中媒体行 $GDMEDIA 条：媒体卡归 MediaCardFeature，被碰会露出封面副本"
elif [ "$EXPANDED" != "0" ] && [ "$ACT" = "0" ]; then
  fail "把手展开了 $EXPANDED 行，却既没有 alpha 交还也没有逐帧补写：首次展开路径未被处理"
elif [ "$ACT" != "0" ]; then
  ok "ghost dedup $GDN 条（$GDEFF 有效）；首次展开路径已处理：交还 $RESID 次 + 逐帧补写 $REAP 次"
else
  echo "  warn 本轮把手没能展开任何行（debug expand pkg= 为 0），首次展开路径未覆盖"
fi

echo "== 5e) 展开/收起图标 morph（shared element，落点必须是 38dp 大图标）=="
# 判据来自真机取证：旧版对两侧都取 firstIconView()，拿到被压隐形的 header 小图标，
# 日志出现 from==to（原地不动）——大图标照旧淡出、小图标闪现。正确形态必须是：
#   源=落点=38dp 大图标（own/card），moved=1，中段只有飞行图标可见，小图标全程 alpha 0。
MORPH_OPEN=$(printf '%s' "$GD" | grep -ac "icon morph open=1")
# 不要求 moved=1：展开前后图标位置相同（原地不动）也是正确形态；
# 仍然要求源/落点都是 restyle 大图标（9x~19x px），旧版 48px 小图标会 FAIL。
MORPH_GOOD=$(printf '%s' "$GD" | grep -a "icon morph open=1" \
  | grep -aE "srcKind=(own|card) dstKind=own" \
  | grep -acE "from=[0-9]+,[0-9]+x(9[0-9]|1[0-9][0-9]) to=[0-9]+,[0-9]+x(9[0-9]|1[0-9][0-9])")
MORPH_SMALL=$(printf '%s' "$GD" | grep -acE "icon morph .*dstKind=small|icon morph .*srcKind=small")
MORPH_MID=$(printf '%s' "$GD" | grep -a "icon morph mid open=1" \
  | grep -acE "vis=0 .*x=[0-9]+ y=[0-9]+ .*srcAlpha=0.0 dstAlpha=0.0 smallAlpha=0.0")
MORPH_CLOSE=$(printf '%s' "$GD" | grep -a "icon morph open=0" | grep -ac "dstKind=own")
MORPH_LAND=$(printf '%s' "$GD" | grep -a "icon morph landed open=1" \
  | grep -acE "destAlpha=1.0 smallAlpha=0.0")
if [ "$EXPANDED" != "0" ] && [ "$MORPH_OPEN" = "0" ]; then
  fail "把手展开了 $EXPANDED 行，却一条 icon morph 都没有：图标仍是淡入淡出"
elif [ "$MORPH_SMALL" != "0" ]; then
  fail "morph 把 header 小图标当源/落点 $MORPH_SMALL 条：小图标本来就不要，必须始终隐藏"
elif [ "$MORPH_OPEN" != "0" ] && [ "$MORPH_GOOD" = "0" ]; then
  fail "morph $MORPH_OPEN 条但没有一条是 38dp 大图标之间的位移（旧版病：from==to 或飞的是隐形小图标）"
elif [ "$MORPH_OPEN" != "0" ] && [ "$MORPH_MID" = "0" ]; then
  fail "morph 缺少中段采样：无法证明飞行中只有一枚图标可见（src/dst/small 未全隐形）"
elif [ "$MORPH_OPEN" != "0" ] && [ "$MORPH_LAND" = "0" ]; then
  fail "morph 落点未交还大图标（destAlpha!=1 或 smallAlpha!=0）"
elif [ "$MORPH_OPEN" != "0" ]; then
  ok "morph 开 $MORPH_OPEN 条（$MORPH_GOOD 条大图标位移/原地）；中段 $MORPH_MID 条；收起 $MORPH_CLOSE 条；落点 $MORPH_LAND 条"
  [ "$MORPH_CLOSE" = "0" ] && echo "  warn 没抓到收起方向的 morph（把手第二次触发未生效？）"
else
  echo "  warn 本轮没有 morph 覆盖（没有行被展开）"
fi

echo "== 5f) 堆叠展开 = 多胶囊（正面卡就是第一枚胶囊，不再连成一张大卡）=="
# 用户要的是 iOS 分组展开：每一条子通知各自一枚胶囊、8dp 间隙；正面卡原地变成第一枚胶囊。
# 旧实现是一张大卡 + 分隔线，已废弃。这里用调试把手（1=展开最上面一叠，0=收起）。
su_sh "settings put global betteroriginos_notify_stack_test 0" >/dev/null; sleep 1
su_sh "settings put global betteroriginos_notify_stack_test 1" >/dev/null; sleep 3
SD=$(su_sh "logcat -d --pid=$P -v threadtime")
SLOG=$(printf '%s' "$SD" | grep -a "notify_row.stack expanded" | tail -1)
SFRONT=$(printf '%s' "$SLOG" | grep -aoE "frontVis=[0-9]+" | cut -d= -f2)
SFH=$(printf '%s' "$SLOG" | grep -aoE "frontH=[0-9]+" | cut -d= -f2)
SLT=$(printf '%s' "$SLOG" | grep -aoE "listTop=[0-9]+" | cut -d= -f2)
SGAP=$(printf '%s' "$SLOG" | grep -aoE " gap=[0-9-]+" | cut -d= -f2)
SITEMS=$(printf '%s' "$SLOG" | grep -aoE "items=\[[^]]*\]" | tr ' ' '\n' | grep -ac "v0")
SFINAL=$(printf '%s' "$SD" | grep -a "stack.frame open=1.000" | grep -acE "target=([0-9]+) cardH=\1 ")
STOG=$(printf '%s' "$SD" | grep -ac "debug stack toggle")
if [ "$STOG" = "0" ]; then
  echo "  warn 本轮没有可用的堆叠卡（stack toggle=0），5f 未覆盖"
elif [ -z "$SLOG" ]; then
  fail "堆叠把手触发了但没有 stack expanded 度量：展开态没进入 open=1"
else
  [ "$SFRONT" = "0" ] && ok "正面卡在展开态仍可见（第一枚胶囊）" \
    || fail "展开态 frontVis=$SFRONT（应为 0）：正面卡不是第一枚胶囊"
  [ -n "$SFH" ] && [ "$SFH" -ge 150 ] && ok "正面卡高 $SFH px" || fail "正面卡高异常：$SFH"
  WANT_TOP=$((${SFH:-0} + 24))
  [ "$SLT" = "$WANT_TOP" ] && ok "其余胶囊起点 = 正面卡 + 8dp（$SLT）" \
    || fail "胶囊起点=$SLT 期望=$WANT_TOP（正面卡与列表间距不是 8dp）"
  if [ "$SGAP" = "24" ] || [ "$SGAP" = "-1" ]; then
    ok "胶囊间隙 = 8dp（gap=$SGAP）"
  else
    fail "胶囊间隙=$SGAP（应为 24px/8dp）"
  fi
  [ "$SITEMS" -ge 1 ] && ok "逐条胶囊 $SITEMS 条可见" \
    || fail "items 里没有可见胶囊（$SITEMS）"
  [ "$SFINAL" -ge 1 ] && ok "展开稳态 cardH==rowH==target（$SFINAL 帧）" \
    || fail "没有一帧 cardH==target：行高与卡高不一致"
fi
# 收尾：恢复折叠，别把设备留在展开态
su_sh "settings put global betteroriginos_notify_stack_test 0" >/dev/null; sleep 1

echo "== 6) 媒体卡链路 =="
# OS3 的播放器是 SystemUIPlugin 里的 QS 磁贴（插件跑在 systemui 进程内，没有独立 pid），
# 所以磁贴钩子必须单独断言：作用域或类名一坏，第 2 步的"钩子装载"是查不出来的。
# SystemUIPlugin 是**懒加载**：只有真正画过一次 QS 面板才会装到本进程里，
# 所以必须先展开 QS 再查，否则这一条会随"本轮有没有人打开过 QS"忽绿忽红（2026-10-28 实测踩过）。
# SystemUIPlugin 是懒加载，且这台机上 expand-settings 不总能把它拉起来
# （实测忽绿忽红）。所以"开 QS -> 查日志"要重试，而不是一次定生死。
TILE=0
for try in 1 2 3; do
  su_sh "cmd statusbar collapse" >/dev/null; sleep 1
  su_sh "cmd statusbar expand-settings" >/dev/null; sleep 2
  # 这台机要从顶边真下拉才会拉起 SystemUIPlugin（面板内部滑动不算）
  su_sh "input swipe 540 4 540 1200 500" >/dev/null; sleep 1
  su_sh "input swipe 540 400 540 1500 600" >/dev/null; sleep 3
  TILE=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -ac "qs player tile installed")
  [ "$TILE" -ge 1 ] && break
  su_sh "cmd statusbar collapse" >/dev/null; sleep 1
done
su_sh "cmd statusbar collapse" >/dev/null; sleep 1
PLG=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -ac "loaded com.vivo.systemuiplugin")
if [ "$TILE" -ge 1 ]; then
  ok "QS 磁贴钩子已装（$TILE 条）"
elif [ "$PLG" = "0" ]; then
  # 插件根本没加载 = 本轮没触发到这条路径：这是覆盖漏洞，不是模块缺陷。
  # 混进 FAIL 会把"没测到"伪装成"测坏了"（本项目已因此误判过一次根因）
  echo "  warn 本轮未能拉起 SystemUIPlugin（QS 触发失败），磁贴钩子未覆盖"
else
  fail "插件已加载（$PLG 条）但磁贴钩子没装：作用域或 ControlCenterMediaPlayerView 类名变了"
fi
PROF=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -aoE "mediaShade=[a-z_]+" | tail -1)
echo "  $PROF"
MC=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -acE "atomic card built|bindSession enter|seekable=")
[ "$MC" -ge 2 ] && ok "媒体卡链路日志 $MC 条（渲染/绑定/可拖动三段都在）" \
  || fail "媒体卡链路日志只有 $MC 条：postmedia 没投递成功，或卡片没绑定会话"
# 进度条必须真的被绘制过：MediaNotificationView 会按 android.R.id.progress 找"它的"条
# 并把我们自绘的条设成 INVISIBLE/alpha0，只有 seek draw 计数能证明它没被吃掉
DRAW=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -ac "seek draw #")
[ "$DRAW" -ge 1 ] && ok "进度条已绘制（$DRAW 帧）"   || fail "进度条从未绘制（seek draw=0）：条被外力藏了或没建上"
RECL=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -ac "card bar reclaimed")
[ "$RECL" = "0" ] || echo "  warn 条被外力改过可见性 $RECL 次（自我修复已生效，但值得查是谁改的）"
NSEEK=$(su_sh "logcat -d --pid=$P -v threadtime" | grep -acE "native seek failed|widget seek failed")
[ "$NSEEK" = "0" ] && ok "seek 回落路径没有异常" || fail "seek 失败 $NSEEK 条（查 uid/context 或事务号）"

echo "== 6b) 非 systemui 作用域的钩子是否真的装载（picker / filemanager）=="
# 这两个功能过去只有"类存在"的静态证据；作用域对了不代表钩子装上了。
# 强制重启目标进程再触发，看模块自己的安装日志。
# 顺序很关键：先清日志再 force-stop。provider 被系统客户端秒拉起，
# 若先 kill 再清，那次 "loaded" 会被清掉，后面就永远等不到（上一版正是这样误报未覆盖）。
su_sh "logcat -c" >/dev/null
su_sh "am force-stop com.android.providers.media.module" >/dev/null
su_sh "am force-stop com.android.filemanager" >/dev/null
for i in 1 2 3 4 5 6; do
  [ -z "$(su_sh "pidof com.android.providers.media.module")" ] && break
  sleep 1
done
su_sh "content query --uri content://media/external/images/media --projection _id --limit 1" >/dev/null 2>&1
su_sh "monkey -p com.android.filemanager -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1
sleep 6
SCOPE_LOG=$(su_sh "logcat -d -v time")
PICK=$(printf '%s' "$SCOPE_LOG" | grep -ac "picker albums patch installed")
MPLOAD=$(printf '%s' "$SCOPE_LOG" | grep -ac "loaded com.android.providers.media.module")
NOLAND=$(printf '%s' "$SCOPE_LOG" | grep -ac "picker albums landing no-entry")
FMP=$(printf '%s' "$SCOPE_LOG" | grep -ac "fileman rename patch installed os=a13")
FMBAD=$(printf '%s' "$SCOPE_LOG" | grep -ac "fileman rename patch installed os=a15")
FMROLL=$(printf '%s' "$SCOPE_LOG" | grep -ac "a13 rename hooks incomplete")
if [ "$MPLOAD" = "0" ]; then
  echo "  warn media provider 本轮没重启（没有 loaded 行），picker 装载未覆盖"
elif [ "$PICK" -ge 1 ]; then
  ok "picker_albums 钩子已在 media provider 进程装载（$PICK 次）；本机落点判定 no-entry $NOLAND 次"
else
  fail "provider 已重启（loaded $MPLOAD 次）却没有 picker 安装日志：钩子真的没装上"
fi
[ "$FMP" -ge 1 ] && ok "filemanager_rename 走了 a13 分支并装载（$FMP 次）" \
                 || fail "filemanager_rename 没装上 a13 分支（os=a13 计数 0）"
[ "$FMBAD" = "0" ] || fail "filemanager_rename 在 OS3 上走了 a15 分支 $FMBAD 次：分流判据错"
[ "$FMROLL" = "0" ] || fail "a13 钩子不完整被回滚 $FMROLL 次：混淆名与本机 APK 不匹配"

echo "== 7) 收尾 =="
su_sh "settings put global betteroriginos_notify_verify $VERIFY0" >/dev/null
if [ -n "$FONT" ]; then
  su_sh "settings put system font_scale $FONT0" >/dev/null; sleep 1
  echo "  已恢复字号：$(su_sh 'settings get system font_scale')"
fi
if [ "$NIGHT" = "1" ]; then
  su_sh "cmd uimode night no" >/dev/null; sleep 1
  echo "  已恢复浅色模式：$(su_sh 'cmd uimode night')"
fi
$ADB shell su -c "svc power stayon false" >/dev/null 2>&1
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancelmedia" >/dev/null 2>&1; sleep 2
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify cancel" >/dev/null 2>&1; sleep 2
su_sh "am force-stop com.betteroriginos" >/dev/null
su_sh "cmd statusbar collapse" >/dev/null
[ -n "$MODE" ] || su_sh "settings get global betteroriginos_notify_row_group_mode" >/dev/null

echo
if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "OS3 冒烟：全绿（证据 $LOG / $PNG）"
  exit 0
fi
echo "OS3 冒烟：${#FAILS[@]} 项失败"
for f in "${FAILS[@]}"; do echo "  - $f"; done
exit 1
