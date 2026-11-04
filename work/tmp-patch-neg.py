import io

F = "work/verify_os5_mirror.py"
s = io.open(F, encoding="utf-8", newline="").read()

o1 = '''    "A11 亮度没锁住": GOOD.replace("base=ff33404d", "base=ffefeff0"),
    "A11 高亮与底色相同": GOOD.replace("highlight=ff526980", "highlight=ff33404d"),
    "A11 封面尺寸不是 52dp": GOOD.replace("spec art=156", "spec art=180"),
'''
n1 = '''    # A11 通知卡的反例：每条只改一处材料，且必须被 §2/§5/§9/§12 的某条断言抓住
    "A11 按钮内缩不是 8dp": GOOD.replace("btnPad=24 btnEnd=6", "btnPad=23 btnEnd=6"),
    "A11 中间按钮又放大成 52dp": GOOD.replace("btn=144/144", "btn=144/156"),
    "A11 封面不再等于内容块高": GOOD.replace(
        "a11 art state=expanded size=255 contentH=255",
        "a11 art state=expanded size=180 contentH=255"),
'''
assert s.count(o1) == 1, "o1"
s = s.replace(o1, n1)

i = s.index('    "A11 进度条轨道不是 3dp"')
j = s.index("    # 点击链路：监听没挂")
o2 = s[i:j]
n2 = '''    "A11 进度条轨道不是 3dp": GOOD.replace("trackDp=3 density", "trackDp=10 density"),
    "A11 标题字号不对": GOOD.replace("titlePx=42 artistPx=36", "titlePx=57 artistPx=36"),
    "A11 封面又加了圆角裁剪": GOOD.replace("clip=false", "clip=true"),
    "A11 scaleType 回到 CENTER_CROP": GOOD.replace(
        "scaleType=CENTER_INSIDE", "scaleType=CENTER_CROP"),
    "A11 标题丢了强调字族": GOOD.replace("titleMedium=1", "titleMedium=0"),
    "A11 圆角跟了宿主主题": GOOD.replace("radius=0 hostCorner", "radius=72 hostCorner"),
    "A11 圆角日志缺宿主对照": GOOD.replace(" hostCorner=72", ""),
    # 200x200 的封面按旧实现（只缩 region）会得到 20000/22504，A11 是 11250/13500
    "A11 取色网格回到旧实现": GOOD.replace("gridBg=11250 gridFg=13500",
                                            "gridBg=20000 gridFg=22504"),
    "A11 正文对比度不达标": GOOD.replace("contrast=10.60", "contrast=2.10"),
    # 字段值与实色不符 = 见证在自报好消息
    "A11 对比度字段与实色不一致": GOOD.replace("contrast=10.60", "contrast=19.90"),
    "A11 副文被调淡": GOOD.replace("secondary=b3ffffff", "secondary=6bffffff"),
    "A11 未着色却用封面色当底": GOOD.replace(
        "bg=ffffffff fg=ff000000 primary=ff000000 secondary=ff4d4d4d colorized=0",
        "bg=ff33404d fg=ff000000 primary=ff000000 secondary=ff4d4d4d colorized=0"),
    "A11 有会话却报未着色": GOOD.replace("colorized=1 session=1", "colorized=0 session=1"),
    "A11 浮雕底色与卡片不一致": GOOD.replace(
        "relief side=200x200 src=200x200 color=ff33404d",
        "relief side=200x200 src=200x200 color=ff123456"),
    "A11 浮雕输出不是正方形": GOOD.replace("relief side=200x200", "relief side=200x180"),
    "A11 浮雕边长不等于原图短边": GOOD.replace(
        "relief side=200x200 src=200x200", "relief side=250x250 src=200x200"),
    "A11 取色日志整段缺失": GOOD.replace(
        "media_card: a11 palette state=expanded", "media_card: a11 paletteX state=expanded"),
    "A11 只有收起态几何见证": GOOD.replace(
        "11-02 23:10:03 1234 1234 I BetterOriginOS: media_card: "
        "a11 art state=expanded size=255 contentH=255"
        " scaleType=CENTER_INSIDE clip=false titlePx=42 subtextPx=36 titleMedium=1\\n", ""),
'''
s = s[:i] + n2 + s[j:]
io.open(F, "w", encoding="utf-8", newline="").write(s)
print("negatives rewritten")
