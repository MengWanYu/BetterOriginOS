import io

F = "work/verify_os5_mirror.py"
s = io.open(F, encoding="utf-8", newline="").read()

# 1) contentH 允许负数：卡片还没测量时会得到 0 - padding = 负值，
#    这种帧必须**解析出来再判**，不能让正则整条漏掉（漏掉就是"断言静默少跑"）。
a = '''RE_A11_ART = re.compile(
    r"a11 art state=(\\w+) size=(\\d+) contentH=(\\d+) scaleType=(\\w+) clip=(\\w+)"
    r" titlePx=(\\d+) subtextPx=(\\d+) titleMedium=(\\d)")'''
b = '''RE_A11_ART = re.compile(
    r"a11 art state=(\\w+) size=(\\d+) contentH=(-?\\d+) scaleType=(\\w+) clip=(\\w+)"
    r" titlePx=(\\d+) subtextPx=(\\d+) titleMedium=(\\d)")'''
assert s.count(a) == 1, "art 正则锚点"
s = s.replace(a, b)

# 2) 预布局帧单独放行：它只能证明"还没量到"，不能当验证过，也不能算失败
c = '''        dens, font, s_title, s_artist, s_time = dens_spec[0]
        bad = []
        if content_h <= 0:
            bad.append("contentH=%d：内容块还没量到，封面尺寸是猜的" % content_h)
        elif size != content_h:'''
d = '''        dens, font, s_title, s_artist, s_time = dens_spec[0]
        if content_h <= 0:
            # 卡片还没测量：card.measuredHeight=0 减掉上下 padding 就是负数。
            # 这一帧既不红也不算数 —— 但同一态必须另有一帧 contentH>0（下面的"未验证"检查负责）。
            prelayout.add(state)
            continue
        bad = []
        if size != content_h:'''
assert s.count(c) == 1, "contentH 分支锚点"
s = s.replace(c, d)

# 3) 声明 prelayout + measurable
e = '''    if media and m["a11spec"] and not m["art"]:
        fails.append("art: 有 spec 日志却没有 a11 art state 见证（几何/排版那半段没跑）")'''
f = '''    if media and m["a11spec"] and not m["art"]:
        fails.append("art: 有 spec 日志却没有 a11 art state 见证（几何/排版那半段没跑）")
    prelayout = set()
    measurable = set()'''
assert s.count(e) == 1, "prelayout 锚点"
s = s.replace(e, f)

g = '''        else:
            ok("art", "%s size=%d=contentH clip=%s title=%dpx subtext=%dpx medium=%d"
               % (state, size, mo.group(5), title_px, sub_px, medium))'''
h = '''        else:
            measurable.add(state)
            ok("art", "%s size=%d=contentH clip=%s title=%dpx subtext=%dpx medium=%d"
               % (state, size, mo.group(5), title_px, sub_px, medium))'''
assert s.count(g) == 1, "art ok 锚点"
s = s.replace(g, h)

# 4) "两态都要有见证"改成"两态都要有**可测量**的见证"
i = '''    if media and m["art"]:
        seen_states = set(mo.group(1).lower() for mo in m["art"])
        for need in ("expanded", "collapsed"):
            if need not in seen_states:
                fails.append("art: 本轮没有 %s 态见证 —— 该态排版在真机上未验证"
                             "（门禁需要展开一次卡片）" % need)'''
j = '''    if media and m["art"]:
        for need in ("expanded", "collapsed"):
            if need not in measurable:
                extra = "（只出现过 contentH≤0 的预布局帧）" if need in prelayout else ""
                fails.append("art: 本轮没有 %s 态的**可测量**见证 —— 该态在真机上未验证%s"
                             "（门禁需要展开一次卡片）" % (need, extra))'''
assert s.count(i) == 1, "两态锚点"
s = s.replace(i, j)
io.open(F, "w", encoding="utf-8", newline="").write(s)
print("patched")
