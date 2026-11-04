#!/usr/bin/env bash
# 组"展开→收起→再展开"三帧取证：只产出 dump+日志，判定在 python 侧。
# 目的：定位 L8（展开子行没有 42dp 大图标）是"首次展开就没有"还是"收起再展开后没挂回来"。
set -u
S="adb -s 10AE4Y0MDE000BL"
export MSYS_NO_PATHCONV=1
su(){ $S shell su -c "$1" 2>&1 | tr -d '\r'; }
dump(){ # $1=tag
  su "rm -f /sdcard/c.xml; uiautomator dump /sdcard/c.xml" >/dev/null 2>&1
  $S pull -q /sdcard/c.xml "work/z8os3-live/cycle-$1.xml" 2>/dev/null
  echo "  $1 -> $(grep -c expandableNotificationRow work/z8os3-live/cycle-$1.xml) 个行节点"
}
su "svc power stayon true"; su "input keyevent 224"; sleep 1
su "input swipe 540 1900 540 250 300"; sleep 2
# 投递前先 force-stop：同一个 Activity 实例收到 onNewIntent 时**不会**重发通知（11-03 夜
# rowlayout 那条样本饥饿就是这个），于是"面板里根本没有可展开的组"被误读成产品回归。
su "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postgroup" >/dev/null 2>&1; sleep 4
su "cmd statusbar expand-notifications"; sleep 3
# 接管方式（notify row feature installed header-time=…）只在进程启动那一拍打一次，
# 机器用久了环形缓冲区早就把它挤出去 ⇒ 先重启 SystemUI，让这一轮自己带上前提证据。
OLD=$(su "pidof com.android.systemui")
su "kill -9 $OLD" >/dev/null 2>&1; sleep 8
su "input keyevent 224" >/dev/null; sleep 1
su "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
su "input swipe 540 1900 540 250 300" >/dev/null; sleep 2
su "logcat -d -v threadtime" | grep -a "installed header-time=" \
  > work/z8os3-live/cycle-install.txt
[ -s work/z8os3-live/cycle-install.txt ] \
  || echo "  note 启动期自报没抓到（进程可能被限流/缓冲区已滚掉），逐帧验收里 L10 会判红"
su "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postgroup" >/dev/null 2>&1; sleep 4
# L9 要量"header 时间落点"，而 postgroup 那两条子行没有可见时间戳 ⇒ 样本里必须再来一条
# 带 showWhen 的行，否则 L9 永远落在"没有被接管的行含时间"这条前提上（判据空转，不是产品坏）。
su "am force-stop com.betteroriginos" >/dev/null 2>&1; sleep 1
su "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify post" >/dev/null 2>&1; sleep 4
# postgroup/post 这两条不会自己退到后台（只有 post/postall 走 moveTaskToBack），
# 设置页压在前面时 dump 拍到的是设置界面 ⇒ "没有任何 expandableNotificationRow"，
# 整轮帧全废（11-04 下午就这么连红两轮）。先 HOME 让面板成为唯一可见表面。
su "input keyevent 3" >/dev/null; sleep 1
su "cmd statusbar expand-notifications"; sleep 3
# 设置页有时不认 HOME（vivo 的回收策略），再退一次并复核焦点；焦点不在面板上就明确报环境未就绪
case "$(su "dumpsys window" | grep -a "mCurrentFocus=Window" | head -1)" in
  *NotificationShade*) ;;
  *) su "input keyevent 4" >/dev/null; sleep 1
     su "cmd statusbar expand-notifications" >/dev/null; sleep 3 ;;
esac
rm -f work/z8os3-live/.cycle-tap.txt
su "logcat -c"
dump before
# 找到 BetterOriginOS 组摘要行的 expand_button 中心并点它（坐标来自 dump，不估）
python - <<'PY'
import re, io
s = io.open("work/z8os3-live/cycle-before.xml", encoding="utf-8", errors="replace").read()
nodes = re.findall(r'<node ([^>]*)/?>', s)
rows = []
for a in nodes:
    rid = re.search(r'resource-id="([^"]*)"', a)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', a)
    txt = re.search(r'text="([^"]*)"', a)
    if b and rid and rid.group(1).endswith("expandableNotificationRow"):
        rows.append((tuple(int(x) for x in b.groups()), txt.group(1) if txt else ""))
chev = []
for a in nodes:
    rid = re.search(r'resource-id="([^"]*)"', a)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', a)
    if b and rid and rid.group(1).endswith("expand_button"):
        chev.append(tuple(int(x) for x in b.groups()))
# 组摘要行的**结构判据**（不靠中文文案：python 从 bash heredoc 读 stdin 时按 GBK 解码，
# UTF-8 字面量会变 mojibake，匹配不上）：带 1 枚 expand_button 且行内嵌着 ≥1 条 h≈57 的子行。
rows = [r for (r, _t) in rows]
target = None
for r in rows:
    kids = [x for x in rows if x[1] > r[1] and x[3] <= r[3] and 50 <= (x[3] - x[1]) <= 64]
    c = [x for x in chev if x[1] >= r[1] and x[3] <= r[3]]
    if len(kids) >= 1 and len(c) >= 1:
        target = (r, c)
        break
if target is None:
    print("NOGROUP rows=%d chev=%d" % (len(rows), len(chev)))
    raise SystemExit(0)
r, cs = target
c = [x for x in cs if x[1] >= r[1] and x[3] <= r[3]][0]
io.open("work/z8os3-live/.cycle-tap.txt", "w").write("%d %d" % ((c[0]+c[2])//2, (c[1]+c[3])//2))
print("TAP group=%s row=%s chevron=%s" % (r, "", c))
PY
T=$(cat work/z8os3-live/.cycle-tap.txt 2>/dev/null)
if [ -n "${T:-}" ]; then
  su "input tap $T" >/dev/null; sleep 4; dump opened
  su "input tap $T" >/dev/null; sleep 4; dump closed
  su "input tap $T" >/dev/null; sleep 4; dump reopened
  # 再等 8s 出一帧"稳定态"：分得开"补扫永远不回来"与"补扫回得慢"，这两者修法完全不同
  sleep 8; dump settled
else
  echo "  没找到组摘要行的箭头，本轮不出帧"
fi
su "logcat -d -v threadtime" > work/z8os3-live/cycle.log 2>&1
# 接管方式只在进程启动那一拍打一次 ⇒ 从整段缓冲区里取，作为验证器的 --install 证据
grep -a "installed header-time=" work/z8os3-live/cycle.log > work/z8os3-live/cycle-install.txt || true
su "svc power stayon false"

# 判定交给验证器（逐帧），不再靠我手工跑：缺哪一帧、哪条判据空转都由它说
PY_VRL=$(cygpath -m "$(cd "$(dirname "$0")/.." && pwd)/work/verify_row_layout.py")
RC=0
FOC=$(su "dumpsys window" | grep -a "mCurrentFocus=Window" | head -1)
case "$FOC" in *NotificationShade*) ;; *) echo "  note 焦点不在通知面板上（$FOC）——帧大概率无效，先确认设备解锁且面板展开" ;; esac
for f in before opened closed reopened settled; do
  X=work/z8os3-live/cycle-$f.xml
  [ -s "$X" ] || { echo "  FAIL $f：没有 dump，这一帧没出成"; RC=1; continue; }
  OUT=$(PYTHONIOENCODING=utf-8 python "$PY_VRL" --dump "$X" \
        --log work/z8os3-live/cycle.log \
        --install work/z8os3-live/cycle-install.txt --density "${DENSITY:-3.0}" 2>&1)
  echo "  -- $f: $(echo "$OUT" | grep -E '^结果级验收' )"
  echo "$OUT" | grep -E "^  FAIL" | sed 's/^/     /'
  echo "$OUT" | grep -qE '^  FAIL' && RC=1
done
echo "组开合逐帧验收：$([ "$RC" = 0 ] && echo 全绿 || echo 有失败)"
exit $RC
