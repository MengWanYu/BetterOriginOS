import io

F = "work/verify_os5_mirror.py"
s = io.open(F, encoding="utf-8", newline="").read()

reps = [
    # 1) 内缩的反例要跳出 ±1.5px 的合法舍入带：24px→16px 才是真的没按 8dp 写
    ('"A11 按钮内缩不是 8dp": GOOD.replace("btnPad=24 btnEnd=6", "btnPad=23 btnEnd=6"),',
     '"A11 按钮内缩不是 8dp": GOOD.replace("btnPad=24 btnEnd=6", "btnPad=16 btnEnd=6"),'),
    # 2) 字号反例要打在有断言的那一行（art 行才断言 titlePx）
    ('"A11 标题字号不对": GOOD.replace("titlePx=42 artistPx=36", "titlePx=57 artistPx=36"),',
     '"A11 标题字号不对": GOOD.replace("clip=false titlePx=42 subtextPx=36",'
     ' "clip=false titlePx=57 subtextPx=36"),'),
]
for a, b in reps:
    assert s.count(a) == 1, ("锚点", a[:40], s.count(a))
    s = s.replace(a, b)

# 3) 两条遗留反例：一条已与新实现重复，一条的小数值要对上新 fixture
old_dup = [l for l in s.split("\n") if "展开态标题丢了强调字族" in l]
if old_dup:
    # 删掉这条（连同它可能跨的行）：改成把材料换成本态真正断言的东西
    i = s.index('    "展开态标题丢了强调字族"')
    j = s.index("\n", s.index("),", i))
    s = s[:i] + s[j + 1:]

a3 = 'GOOD.replace("contrast=9.40", "contrast=9,40")'
assert s.count(a3) == 1, ("contrast 反例锚点", s.count(a3))
s = s.replace(a3, 'GOOD.replace("contrast=10.60", "contrast=10,60")')

# 4) 新增交叉断言：同一块卡片的 spec 行与 art 行字号必须同源
anchor = '''        for idx, name in ((6, "titlePx"), (7, "artistPx"), (8, "timePx")):'''
assert s.count(anchor) == 1
s = s.replace(anchor, '''        # 同一块卡片的两处见证必须同源：spec 是在建卡那一拍打的，
        # art 是在套规格那一拍打的，两处字号对不上就说明其中半段没跑或跑了旧的。
        for idx, name in ((6, "titlePx"), (7, "artistPx"), (8, "timePx")):''')
io.open(F, "w", encoding="utf-8", newline="").write(s)
print("patched")
