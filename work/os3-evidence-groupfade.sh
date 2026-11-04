#!/usr/bin/env bash
# 分组展开/收起时 42dp 大图标的 alpha 过渡取证。
# 用户报的是观感："展开时图标闪现出来，收起时字压上去后再瞬间消失"。
# 判据只有两条：① 淡入/淡出确实在爬（中间读数既不是 1.00 也不是 0.00）；
# ② 收起后图标真的不残留、展开后真的回来了（成对 dump 证明）。
# 纪律：只用 dump + 日志，不截图、不退出 DSU、不提交 git。
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
FAILS=()

su_sh() { $ADB shell su -c "$1" 2>&1 | tr -d '\r'; }
fail() { FAILS+=("$1"); echo "  FAIL $1"; }
ok()   { echo "  ok   $1"; }
wpath() { cygpath -m "$1"; }

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

# 从 dump 里挑"子行已带 42dp 大图标的那个展开组"，返回它摘要行 header 的中心。
# 上一版直接点摘要行的几何中心：样本里同时存在展开组与收起组，点到了另一个组的空白处，
# 面板整个被关掉（焦点掉回桌面），后面两帧抓的都是桌面。
# 只有点 header（应用名那一行）才是 vivo 的"展开/收起整组"目标。
# 用法：group_stat <dump>            -> 打印"子行带大图标的那个展开组"的 header 中心 + 组序号
#      group_stat <dump> <组序号 k> -> 打印该组当前"带大图标且可见"的子行数
# 子行归属必须按"到下一个顶层行为止的连续嵌套行"算：上一版把后面所有 nested 行都记给当前摘要，
# 于是选中了错误的组（真正带图标的是第二组，点下去展开的是第一组），整轮判据都指错地方。
# 组序号 k 用来在收起/展开前后锁定同一个组（列表顺序不会因单个组的开关而变）。
group_stat() {
  PYTHONPATH="$(wpath "$ROOT/work")" python - "$(wpath "$1")" "${2:--}" <<'PY'
import sys, verify_row_layout as v

text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
want = sys.argv[2]
rows = v.parse_dump(text)


def has_icon(rec):
    return any(v.bounds(n) and 120 <= v.bounds(n)[2] - v.bounds(n)[0] <= 132
               and 120 <= v.bounds(n)[3] - v.bounds(n)[1] <= 132 for n in rec["nodes"])


def kids_of(i):
    out = []
    for r in rows[i + 1:]:
        if not r["nested"]:
            break
        out.append(r)
    return out


sums = [i for i, r in enumerate(rows) if r["has_children"] and r["w"] > 800 and not r["nested"]]
if want != "-":
    k = int(want)
    print(sum(1 for c in kids_of(sums[k]) if has_icon(c)) if k < len(sums) else -1)
    raise SystemExit
for k, i in enumerate(sums):
    if not any(has_icon(c) for c in kids_of(i)):
        continue
    hdr = [v.bounds(n) for n in rows[i]["nodes"]
           if "notification_header" in v.attr(n, "resource-id")]
    box = hdr[0] if hdr else (rows[i]["l"], rows[i]["t"], rows[i]["r"], rows[i]["t"] + 100)
    print((box[0] + box[2]) // 2, (box[1] + box[3]) // 2, k)
    break
else:
    print(-1, -1, -1)
PY
}

focus_shade() {
  case "$(su_sh "dumpsys window" | grep -a "mCurrentFocus=Window" | head -1)" in
    *NotificationShade*|*NotificationPanel*) return 0 ;;
    *) return 1 ;;
  esac
}

echo "== 0) 取证对象 =="
if [ ! -s "$ROOT/module/build/BetterOriginOS.apk" ]; then
  echo "  FAIL 没有构建产物，拒绝取证"; exit 2
fi
REMOTE=$(su_sh "sha256sum \$(pm path com.betteroriginos 2>/dev/null | head -1 | cut -d: -f2)" \
  | grep -aoE '^[0-9a-f]{12}')
[ "$REMOTE" = "$HASH" ] || { echo "  FAIL 设备上是 ${REMOTE:-读不到}，本地是 $HASH"; exit 2; }
echo "  ok   apk=$HASH"

echo "== 1) 起底并投递分组样本 =="
su_sh "settings put global betteroriginos_feature_notify_row 1" >/dev/null
su_sh "settings put global betteroriginos_notify_row_sep os5" >/dev/null
su_sh "settings put global betteroriginos_notify_verify 1" >/dev/null
su_sh "svc power stayon true" >/dev/null
restart_sysui || { echo "  重启 SystemUI 失败"; exit 2; }
sleep 3
restart_sysui || { echo "  二次重启失败"; exit 2; }
sleep 6
unlock
su_sh "logcat -c" >/dev/null
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postgroup" >/dev/null 2>&1
sleep 5
su_sh "am start -n com.betteroriginos/.ui.MainActivity --es debug_notify postbulk" >/dev/null 2>&1
sleep 4
su_sh "input keyevent 3" >/dev/null; sleep 1
su_sh "cmd statusbar expand-notifications" >/dev/null; sleep 3
su_sh "uiautomator dump /sdcard/g0.xml" >/dev/null
$ADB pull -q /sdcard/g0.xml "$(wpath "$OUT/group-$HASH-open.xml")" 2>/dev/null
su_sh "logcat -d -v threadtime" > "$OUT/group-$HASH-open.log"
read -r SX SY GK <<<"$(group_stat "$OUT/group-$HASH-open.xml")"
if [ -z "${SX//[!0-9-]/}" ] || [ "$SX" -lt 0 ] || [ "${GK:-1}" -lt 0 ]; then
  echo "  FAIL dump 里没有'子行带大图标的展开组'，整轮无从测（看 group-$HASH-open.xml）"; exit 2
fi
K0=$(group_stat "$OUT/group-$HASH-open.xml" "$GK")
ok "展开组 header 中心 ($SX,$SY)，带大图的可见子行 $K0 枚"

echo "== 2) 点 header 收起整组（期望：淡出而不是瞬间消失）=="
su_sh "logcat -c" >/dev/null
su_sh "input tap $SX $SY" >/dev/null
sleep 2
su_sh "logcat -d -v threadtime" > "$OUT/group-$HASH-collapsed.log"
su_sh "uiautomator dump /sdcard/g1.xml" >/dev/null
$ADB pull -q /sdcard/g1.xml "$(wpath "$OUT/group-$HASH-collapsed.xml")" 2>/dev/null
K1=$(group_stat "$OUT/group-$HASH-collapsed.xml" "$GK")
focus_shade || fail "收起那一下把面板关掉了（焦点不在通知面板），这一帧不是收起态"
if [ "${K1:-0}" -lt "$K0" ]; then ok "组确实收起：带图标子行 $K0 -> $K1"
else fail "组没收起（带图标子行 $K0 -> $K1），过渡判据无从谈起"; fi

echo "== 3) 再点 header 展开整组（期望：淡入而不是闪现）=="
su_sh "logcat -c" >/dev/null
su_sh "input tap $SX $SY" >/dev/null
sleep 2
su_sh "logcat -d -v threadtime" > "$OUT/group-$HASH-expanded.log"
su_sh "uiautomator dump /sdcard/g2.xml" >/dev/null
$ADB pull -q /sdcard/g2.xml "$(wpath "$OUT/group-$HASH-expanded.xml")" 2>/dev/null
K2=$(group_stat "$OUT/group-$HASH-expanded.xml" "$GK")
focus_shade || fail "展开那一下把面板关掉了，这一帧不是展开态"
if [ "${K2:-0}" -gt "${K1:-0}" ]; then ok "组确实展开：带图标子行 $K1 -> $K2"
else fail "组没展开（带图标子行 $K1 -> $K2）"; fi

echo "== 4) 判据 =="
python - "$(wpath "$OUT/group-$HASH-collapsed.log")" "$(wpath "$OUT/group-$HASH-expanded.log")" <<'PY'
import io, re, sys


def rows(path):
    return io.open(path, encoding="utf-8", errors="replace").read().splitlines()


out = rows(sys.argv[1])
inn = rows(sys.argv[2])
rc = 0


def fades(lines, want):
    got = [l for l in lines if "light.icon fade=" in l and "fade=" + want in l]
    return got


def ramps(lines):
    # 中间读数：0.05~0.95 之间才算"在爬"，只有 0.00/1.00 就是硬切
    vals = []
    for l in lines:
        m = re.search(r"light\.icon ramp t=(\d+)ms alpha=([\d.]+)", l)
        if m:
            vals.append((int(m.group(1)), float(m.group(2))))
    return vals


for label, lines, want in (("收起", out, "out"), ("展开", inn, "in")):
    f = fades(lines, want)
    if not f:
        print("  FAIL %s：没有 fade=%s 见证（过渡根本没触发）" % (label, want))
        rc = 1
        continue
    r = ramps(lines)
    mid = [a for _, a in r if 0.05 < a < 0.95]
    print("     %s：fade 行=%d，ramp 读数=%s" % (label, len(f), ["%.2f" % a for _, a in r]))
    if not mid:
        print("  FAIL %s：alpha 读数全在端点（%s）——还是硬切，不是过渡" % (label, r))
        rc = 1
    else:
        print("  ok   %s：alpha 确实逐帧在爬（中间值 %d 个，例 %.2f）"
              % (label, len(mid), mid[0]))
sys.exit(rc)
PY
[ $? = 0 ] || FAILS+=("alpha 过渡判据")

# 收起态那一帧里，这个组不该再留任何大图标（淡出必须真的把图标摘干净）：
# 用 fade/ramp 证明它是淡出去的，再用 0 枚证明没有"淡到一半停住"的残影。
LEFT=$(group_stat "$OUT/group-$HASH-collapsed.xml" "$GK")
echo "     收起态：组内带大图标的子行 $LEFT 枚"
[ "${LEFT:-1}" = "0" ] && ok "收起后图标没有残留" || fail "收起后仍有 $LEFT 枚图标残留"

echo "== 5) 回归：三态结果级验收仍要过 =="
bash "$ROOT/work/os3-evidence-rowlayout.sh" "$SERIAL" "$DENSITY" 2>&1 | grep -E "FAIL|结果级验收|取证"

echo "== 6) 收尾 =="
su_sh "settings put global betteroriginos_notify_verify 0" >/dev/null
su_sh "svc power stayon false" >/dev/null
su_sh "cmd statusbar collapse" >/dev/null
if [ "${#FAILS[@]}" -eq 0 ]; then
  echo "分组过渡取证：全绿（work/z8os3-live/group-$HASH-{collapsed,expanded}.{xml,log}）"
  exit 0
fi
echo "分组过渡取证：失败 ${#FAILS[@]} 条"
for f in "${FAILS[@]}"; do echo " - $f"; done
exit 1
