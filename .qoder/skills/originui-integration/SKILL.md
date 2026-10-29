---
name: originui-integration
description: 把 vivo-OriginUI 复刻库（OriginOS 5 UI 框架逆向复刻）接入任意 Android App 的指南。凡涉及 OriginUI 复刻库、OriginBlur/OriginTheme/OriginRows 等类的使用、为 App 增加 OriginOS 5 风格 UI/原生模糊、或将 BetterOriginOS 等应用迁移到该库时使用。
---

> **便携包版本 2026-10-26**。本包自包含：`SKILL.md` + `lib/`（9 个 Java 文件）+ `docs/`（3 篇原理）
> + `evidence/`（10 张真机基准图）。文中路径均相对本 skill 目录。
> 适用：Android minSdk 29+；OriginOS 5（vivo/iQOO）可获得原生模糊，其他设备自动回退为普通视图。


# OriginUI 复刻库接入指南

库位置：`lib/src/com/originui/replica/`（本包内，纯 Java，无 androidx/资源依赖，9 个文件整体拷贝）。
原理文档：`docs/01-blur.md`（模糊）、`docs/02-viewport.md`（顶底栏/滚动）、`docs/03-controls.md`（控件参数）。
验收基准：`evidence/*.png`（iQOO Z8 真机，原生模糊/开关/滑杆/下拉/对话框/弹层/夜间）。

## 1. 接入步骤

1. **拷贝** 本包的 `lib/src/com/originui/` 整个目录到目标 App 源码树（包名可保留 `com.originui.replica`）。
2. 无需资源/manifest 改动；minSdk 29+（blur 反射在非 vivo 机静默失败回退）。
3. 首页骨架直接用：`OriginTheme.applyWindow(activity)` + `OriginRows` 分组列表 + `OriginTopBar`/`OriginBottomBar`。

## 2. 模糊（核心能力）

```java
// 能力检测（FtFeature vivo.software.spacesystem + View.setMaterial 存在）
boolean ok = OriginBlur.isSupported(context);

// 方式A：任意 View 一行打模糊（OriginUI 语义类型见 OriginBlur 常量）
OriginBlur.apply(view, OriginBlur.TOOLBAR, OriginTheme.night(ctx)); // 或 SHEET_DIALOG/FLOAT_MENU/POPUP_WINDOW/TOAST…
OriginBlur.setCornerRadius(view, dp(24));   // 模糊区圆角
OriginBlur.setAlpha(view, 0.6f);            // 滚动渐显等

// 方式B：容器（推荐，自动处理背景清除/失败回退/夜间切换）
OriginBlurLayout bar = new OriginBlurLayout(ctx);
bar.setBlurType(OriginBlur.SHEET_DIALOG);
```

**关键规则**（VBlurLinearLayout 逆向结论，违反会黑块/不生效）：
- 模糊成功后**必须清空自身背景**（OriginBlurLayout 已自动处理）；给 blur 容器再 set 背景色会盖住模糊。
- 子 View 不透明背景会挡模糊 → 放进 OriginBlurLayout 的子项背景自动清（需保留时自行不加）。
- `apply()` 返回 false（非 vivo 机）→ 容器自动恢复原背景，**业务代码不要假设模糊一定生效**。
- 夜间切换由 OriginBlurLayout.onConfigurationChanged 自动重打（dark 材质 id 自动映射）。
- 滚动渐显：`OriginTopBar.attach(scrollOrList)`；手动场景 `OriginBlur.scrollAlpha(dist, dp(16))`。

## 3. 设计 token（OriginTheme）

全部 day/night 自动：`bgPage/bgCard/textPrimary/textSecondary/textTertiary/sectionLabel/divider/accent/accentSoft/trackOff/scrim`；
形状：`RADIUS_CARD=12 / RADIUS_DIALOG=16 / RADIUS_PILL=22 / PAGE_MARGIN=22 / CARD_GAP=18`；行 55dp、行标题 16sp、说明/值 13sp；分隔线左缩 15dp 右到边（#D9D9D9/15% 白）；行级圆角 `OriginTheme.rowBg(ctx,color,POSITION_TOP/MID/BOTTOM/SINGLE)`。
新颜色**不要自造**——先查 `docs/03-controls.md`；原始逆向素材（framework jar / OriginUI 486 类）未随包，反编译来源与步骤见 `docs/01-blur.md` 开头。

## 4. 控件

- 分组列表：`OriginRows.row(ctx, title, summary, trailingView, position, onClick)` + `OriginRows.divider(ctx)`（左缩 15dp 右到边）+ 组间 `spacer(18)`。
- 开关：`OriginSwitch`（拖动跟手 0.4/0.6 阈值 + Overshoot 1.8，setOnCheckedChangeListener）。
- 滑杆：`OriginSlider`（setRange/setProgress/setListener）。
- 对话框：`OriginOverlays.OriginDialog.build(title, msg, neg, negL, pos, posL)`。
- 底部弹层：`OriginOverlays.OriginSheet(activity).body()` 塞内容后 show()。
- 行内下拉：`OriginOverlays.OriginListPopup`（标准 ListAdapter，勿复用 detached view）。
- 顶栏/底栏：`OriginTopBar`（setTitle/addAction/attach）/ `OriginBottomBar`（setItems(labels, iconFactory, listener)）。

## 5. 常见坑

| 坑 | 症状 | 处理 |
|---|---|---|
| LinearLayout.LayoutParams(w,h,gravity) 三参构造 | 第三参其实是 **weight**（gravity 当 weight 传 → 子项拉伸） | 用两参构造 + `lp.gravity=` |
| blur 容器内有不透明子背景 | 模糊被盖住 | 子项 setBackground(null) |
| View.SOUND_EFFECT_CLICK 等 hidden 常量 | javac 找不到符号 | 用字面值（0=CLICK） |
| ListPopupWindow 适配器返回 detached view | obtainView NPE | getView 正常构造/convertView 复用 |
| ListView 联动 | onScroll 里 firstTop 可能 <0 | 取 `-first.getTop()`，clamp ≥0 |
| 真机 tap 自动化 | 截图缩放 ≠ 输入坐标 | input tap 用真实分辨率（1080×2400）；页面位置变动先 uiautomator dump |
| 动画器续播 | setCurrentPlayTime→start() 在 AOSP 被重置 | 先 start() 再 setCurrentPlayTime() |
| 验证开关拖动 | input swipe 的 move 事件太稀疏（跨阈值瞬间 k=1.0） | 用 input motionevent DOWN/MOVE/UP（UP 也带坐标）逐点注入 |
| 验证动画曲线 | 无 ffmpeg 抽帧 | settings put global animator_duration_scale 5/10 慢放 + 设备端 screencap 循环连拍 |

## 6. 验收流程（每改动一轮）

在接入的 App 工程内编译安装后：

```bash
adb install -r <你的 apk>
adb exec-out screencap -p > shot.png   # 对照本包 evidence/ 基准
adb shell cmd uimode night yes/no      # 夜间回归
adb shell uiautomator dump /sdcard/ui.xml   # 布局定位，别猜坐标
```

模糊是否真生效看截图内容穿过处是否雾化（纯色背景看不出差别）。

## 7. OriginSwitch 动画语义（接入方须知）

- onCheckedChanged 在**动画结束**（约 250ms 后）触发，不是点击瞬间——依赖即时副作用的逻辑
  请自行在 onClick 层补充或接受延迟（与 vivo 原生行为一致）。
- 点击 = 无过冲 250ms PathInterpolator(0.2,0.4,0.2,1)；拖动跨 0.6/0.4 = Overshoot(1.8) 从当前位置续播。
- 触感走系统 haptic_feedback_enabled 开关（原版 vibratorPro(113) 的系统回退）。

