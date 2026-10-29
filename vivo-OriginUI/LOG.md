# vivo-OriginUI 逆向复刻日志

目标：代码级逆向 OriginOS 5 系统应用的 UI 框架（OriginUI），一比一复刻为可接入的库 + demo。
设备：iQOO Z8 (PD2314, Android 15, OriginOS 5)，adb 序列 10AE4Y0MDE000BL。

## 2026-10-25 23:00 · 启动 + 模糊 gate 通过 ✅

### 素材盘点（全部在 ref/）
- `ref/originui-fm-src/`：OriginUI 控件库完整源码（jadx 反编译 BBKFileManager.apk，com.originui.** 486 类）
  - core/blur（VBlurLinearLayout/RelativeLayout）、core/utils（VBlurUtils 等）
  - widget/：toolbar、navigation（底栏）、components/switches（OS5 开关 VMoveBoolButton）、edittext、
    dialog、sheet、popup、tabs、search、spinner、toast、snackbar、bannertip、button、listitem、
    smartrefresh、scrollbar、selection、responsive、vlinearmenu、vbadgedrawable…
- `ref/framework-src/`：vivo-framework.jar 反编译（android/view/blur、VivoViewImpl、com/vivo/blur）
- `ref/app-usage-samples/`：时钟 App 顶栏阻尼滚动（HeaderLayout）、VToolbar、VSearchView 等 App 侧用法
- `ref/BlurMaterial.java` / `VivoBlurRender.java` / `VivoViewImpl.java`：框架模糊核心

### 决定性发现
1. **`View.setMaterial(style, contentType)` 是 vivo framework 里 android.view.View 的公共方法**。
   普通应用直接调用即可获得与系统应用完全一致的原生模糊，无需 hook/权限/特殊签名。
   （此前 demo 复刻失败是用了 RenderEffect 自绘模拟——根因。）
2. 材质 = style id（亮暗各一套，见 docs/01-blur.md §4.1 映射表）+ 光学参数
   （双层遮罩色/透明度 + 饱和度 + 模糊系数 + 融合模式，docs/01-blur.md §4.2）。
3. contentType=1（视图内模糊）是顶栏/底栏/卡片场景；0 是糊壁纸的窗口模糊。
4. OriginUI 模糊容器 VBlurLinearLayout 行为：成功后清空自绘背景+清除子 View 背景 tint、
   失败恢复原背景、发丝线 divider 随 blurAlpha、夜间切换自动换 dark 材质 id、
   滚动渐显 = `getScrollBlurAlpha(dist, 16dp)` 线性。
5. OS5 开关 VMoveBoolButton：拖动跟手，0.4/0.6 阈值翻转；250ms PathInterpolator(0.2,0.4,0.2,1)
   位移 + 150ms (0.33,0,0.67,1) 色彩 + OvershootInterpolator(1.8) 回弹。

### 真机 gate（evidence/gate-material-1.png）
MaterialGateActivity：彩色内容 + 顶栏(210)/圆角卡(207+corner24)/底栏(204)。
logcat：`View.setMaterial=true setMaterialAlpha=true setMaterialCornerRadius=true`，
`FtFeature(spacesystem)=true`，apply 全部 true；截图三处原生模糊全部生效。

### 产出
- `docs/01-blur.md` 模糊完整原理（三层调用链/API 清单/contentType/材质映射表/光学参数表/容器行为/复刻方案）
- demo/MaterialGateActivity.java（gate 测试页，保留作为库的验收基准）

## 下一步
- 复刻库 lib/：OriginBlur/OriginTheme/OriginBlurLayout/OriginSwitch/顶底栏…
- demo 每页一控件，真机截图 vs 原版对照
- originui-integration skill + BetterOriginOS 接入

## 2026-10-25 23:15 · 复刻库 v1 + 验收页真机全过 ✅

### 库（lib/src/com/originui/replica/，纯源码无资源依赖，可直接拷入任何 App）
- OriginTheme：OS5 全套 token（色/圆角/间距/字号，day/night 自动）
- OriginBlur：View.setMaterial 反射封装（19 种 OriginUI 类型→亮暗材质 id 映射、alpha/圆角/FPS/清除、滚动渐显公式）
- OriginBlurLayout：VBlurLinearLayout 1:1（成功清背景/失败回退、发丝线随 alpha、夜间自动切材质）
- OriginSwitch：拖动跟手 + 0.4/0.6 阈值 + 250ms/150ms/Overshoot(1.8)（VMoveBoolButton 逆向）
- OriginSlider / OriginRows / OriginTopBar（滚动渐显联动）/ OriginBottomBar / OriginOverlays（Dialog/Sheet/ListPopup）

### 真机验证（evidence/）
- replica-4.png：分组卡+行级圆角+发丝线+开关+滑杆+底栏 204 原生模糊+图标
- replica-scrolled.png：顶栏 TOOLBAR 模糊 + 滚动 16dp 渐显（渐变内容穿过雾化）
- replica-popup3.png：ListPopup 勾选态下拉
- replica-dialog.png：确认对话框（居中 16dp 卡+文字按钮+scrim）
- replica-sheet3.png：底部弹层（把手+胶囊按钮）
- replica-night.png：夜间模式（黑底/#202020 卡/蓝强调）全控件正确

### 过程修复
- OriginRows.row trailing 的 LayoutParams 三参构造把 gravity 当 weight（开关拉伸成条）→ 显式 lp.gravity
- View.SOUND_EFFECT_CLICK 是 hidden → 字面 0
- demo ListPopup 适配器复用 detached child NPE → 标准构造式 adapter
- adb 截图坐标系 ≠ tap 坐标系（1080×2400 vs 缩放显示），自动化 tap 按 2400 高换算

### 文档
- docs/01-blur.md 模糊全原理（三层链/API/材质映射/光学参数/容器行为/复刻方案）
- docs/02-viewport.md 顶底栏+滚动视口+HeaderLayout 阻尼逆向
- docs/03-controls.md 全控件参数速查（开关/滑杆/行/输入/搜索/对话框/弹层/下拉/Toast/徽标/Tab）

## 2026-10-25 23:27 · BetterOriginOS 接入复刻库 ✅ + iOS 风信息页

### 接入改动（module/，构建 137KB，真机全过）
1. 复刻库整体拷入 `module/src/com/originui/replica/`（build.sh find src 自动编译）。
2. `item_feature.xml` / `item_option_switch.xml`：框架 Switch → `com.originui.replica.OriginSwitch`
   （48×28dp）；`FeatureAdapter` findViewById 类型 + setChecked(bool,false) 适配。
3. `ConfirmDialog` 内部重写为 `OriginOverlays.OriginDialog`（API 不变，调用方零改动）。
4. `page_info.xml` iOS 风排版：section 标题移到卡外（12sp section_label）、卡内行分隔线
   （左缩 72dp/20dp）、重启改为卡内蓝色文字按钮行（bg_item_single + 48dp 高）。
   新增 `bg_item_single` drawable + `section_label` 色（亮暗两套）。

### 真机验证（evidence/betteroriginos-*.png）
- 详情页：OriginSwitch 外观/交互正常、真实媒体卡预览不受影响。
- 重启确认对话框：OriginDialog 圆角卡 + 取消/确认文字按钮正常。
- 开关写链路：UI 切换 → settings global betteroriginos_feature_media_card 0→1（SuCommander 正常）。
- 信息页：iOS grouped-inset 排版生效。

### Skill
- `.zcode/skills/originui-integration/SKILL.md`：接入步骤/模糊用法/token/控件/坑表/验收流程。

### 遗留（下一轮可做）
- demo 的 BlurLab 页仍用 RenderEffect 模拟（保留作对照）；ReplicaActivity 已是原生。
- OriginSheet 拖拽手势、HeaderLayout 阻尼头、Toast 走真 TOAST 材质（窗口类型限制）未做。
- maskFusion 混合公式（原生层）未解析，兜底渲染为近似。

## 2026-10-26 06:05 · 开关 1:1 重制 ✅（用户反馈"开关仿的不像"）

### 根因
首版 OriginSwitch 凭 DESIGN-SPEC 近似值画成了 Material 风格（实心灰轨+白 thumb）。
重读 VMoveBoolButton.d0() 绘制逻辑 + 从 settings-dec/res 提取 rom15_0 实际资源值后发现
原版结构完全不同：

- OFF = **空心**：轨道 2.5dp 描边胶囊（高 17.5dp、36% 灰），thumb = 3dp 灰环内部透明
  （外径 20dp 高于轨道）——即用户看到的"空心圆环"
- ON = 13dp 实心浅蓝条（#330F77FF，20% 蓝）+ thumb 白底 80% + 3dp 蓝环（#0F77FF）
- 切换动画：蓝条左起生长（宽 max(P,0.5)×38dp、alpha=P×2）、描边条右滑淡出（alpha=1-P）、
  thumb 半径 8.5→10dp 生长、中心 12.5→32dp
- 夜间：描边 #75464646 / 蓝 #4D2785FF / 环 #464646→#2785FF / thumb 黑

### 验证（evidence/switch-v2*.png）
- ON 态 vs 原版 crop-switch.png：浅蓝条+白 thumb+蓝环 ✓
- OFF 态 vs 原版 crop-switch2.png：空心灰环+描边轨道 ✓
- BetterOriginOS 详情页同步生效（bo-switch-v2.png）
- 教训：视觉控件必须先用真机裁剪图逐像素对照，不能凭 token 近似；尺寸/颜色优先从
  反编译 APK 的 res/values 提取（settings-dec 有带 res 的完整反编译），源码只用来推逻辑。

## 2026-10-26 06:15 · 滑杆 1:1 重制 ✅（用户反馈"滑块仿的不像"）

### 逆向路径
OriginUI 库无独立 Slider 类 → framework `com.vivo.common.widget.VivoSeekBar` 只是
AbsSeekBar 换皮（视觉全在 drawable）→ 最终从 settings-dec/res 拿到 originui seekbar
drawable 全套 + 用 Python 对真机裁剪图（crop-slider.png）逐像素测量交叉验证：

- **thumb = 18dp 蓝盘（#0F77FF）+ 9dp 白心**（vector：外圆 r=4/8、内心 r=2/8；
  夜间内心黑 #000000）——不是白圆+阴影！与开关同设计语言（实心圆盘挖白心）
- 轨道 4dp（vigour_seekbar_minHeight），已播放 #0F77FF、未播放 #0F000000（白底呈 #F0F0F0）
- 拖动数值气泡（VPopupTextView 语义）：≥50×22dp、padding 10/4、背景 #0F77FF、
  白字 12sp medium，浮在 thumb 上方 12dp
- 像素验证：截图 4.6x 系数下轨道 19px→4.1dp ✓、thumb 83px→18dp ✓、白心 44px→9.6dp ✓

### 验证（evidence/slider-v2*.png）
- 静态：蓝盘白心 thumb + 4dp 轨 vs crop-slider.png 一致
- 拖动：值 67 实时更新 + 气泡跟随 thumb（水平居中、上方 12dp）
- module 同步重装（137KB）

## 2026-10-26 06:20 · 全局细节像素级校正（用户反馈"很多细节不是像素级复刻"）

### 方法升级：原版截图逐像素测量（Python/PIL）替代 token 近似
对原版系统设置「显示与亮度」真机截图（shot-display-light.png，density 2.748）测量：

| token | 原版实测 | 旧值 | 修正 |
|---|---|---|---|
| 行高 | 55dp（分隔线均分，150px） | 56 | 55 minHeight |
| 卡间距 | **18dp**（49px） | 8dp | 18 |
| 页边距 | 22dp（60px） | 20 | 22 |
| 行标题 | **16sp**（汉字块高 43px） | 15sp | 16 |
| 行值/说明 | **13sp**（35px） | 12sp | 13 |
| 分隔线 | 左缩 **15dp**（42px）、右侧到边、#D9D9D9 | 左缩 70dp、8% 黑 | 15dp/#D9D9D9（夜 15% 白） |
| 卡圆角 | 12dp（35px 曲线收敛）✓ | 12 | 保持 |
| section | 12sp ✓、左缩 28dp（页边距+6） | 13sp | 12sp+6dp |

### 同步落地
- OriginTheme/OriginRows 改 token；module dimens.xml、divider 色与 item_feature 分隔线同步
- 真机：ReplicaActivity v3 + BetterOriginOS 首页/详情 全部生效（evidence/replica-v3.png、bo-home-v3.png）

### 方法论（重要）
1. 视觉控件先裁真机图逐像素测量（PIL），再从反编译 APK res 拿官方 dp/sp 值交叉验证；
2. 源码只用来推逻辑（动画/手势/状态机）；
3. 每 token 修正后必须真机截图回贴对比。

## 2026-10-26 06:45 · 开关动画 1:1 复刻 + 逐帧验证 ✅（用户反馈"切换动画没有代码级复刻"）

### 重读源码发现的完整动画架构（此前只复刻了"一个动画器换插值器"的近似）
- **双 ValueAnimator**（o0()）：C0=ofFloat(0,1) 翻开、B0=ofFloat(1,0) 关闭；D0=250ms；
  默认插值器 u0=PathInterpolator(0.2,0.4,0.2,1)（line 2790-2792）。
- **点击**（performClick L17-L24）：C0/B0 直接 start()，无过冲。
- **拖动跨阈值**（ACTION_MOVE L86/L88）：换 OvershootInterpolator(1.8) + setCurrentPlayTime 从 J0 续播。
- **回调时机**：onCheckedChanged 在动画结束时（h0()），不在点击瞬间。
- **触感**：H0() = vibratorPro(113,-1,-1)，受系统 haptic_feedback_enabled 开关控制。
- E1(250ms)/m0/l0(150ms) 动画器属于 loading/旧 ROM 路径，OS5 无关（已考证排除）。

### 验证方法（无 ffmpeg，用系统 animator_duration_scale 慢放 + 设备端连拍 + logcat 时间戳）
- 点击路径（5x 慢放）：帧序列 907→916→917→917，平滑减速、无过冲 = PathInterpolator(0.2,0.4,0.2,1) ✓
- 拖动路径（10x 慢放 + input motionevent 精细注入）：dragFlipTo 起点 J0=-0.107（已处于 Overshoot
  过冲区间）→ animEnd 终点精确 0.0，动画全程 = D0×scale ✓
- 写链路（1x 正常速）：DOWN→toggle→animEnd(275ms=250+帧延迟)→onCheckedChanged→SuCommander 写入
  settings=0 ✓（BetterOriginOS 真机日志时间戳逐条对应）

### 踩坑记录
- `input swipe` 的 move 事件稀疏，跨阈值瞬间 k 已=1.0（无动画可做）——是测试工具采样问题，
  验证拖动必须用 `input motionevent DOWN/MOVE/UP`（UP 也要带坐标）。
- AOSP 上 setCurrentPlayTime→start() 会被 start() 重置，续播必须 start()→setCurrentPlayTime()
  （源码顺序在 vivo framework 上等价，复刻按 AOSP 语义调整，已注记）。
- BetterOriginOS 详情页 tap 坐标在新 token（55dp 行高/18dp 卡距）下偏移，自动化前先 uiautomator dump。
- 系统级连拍验证动画：`settings put global animator_duration_scale 5/10` + 设备端 screencap 循环。

### 产出
- OriginSwitch 动画核心重写（dual animator + 阈值续播 + 结束回调 + 系统触感开关）
- docs/03-controls.md 动画架构表更新 + 实现偏差注记

## 2026-10-26 12:05 · Skill 加载指引（本库的接入 skill 怎么用）

- **Skill 文件**：`.zcode/skills/originui-integration/SKILL.md`（接入步骤/模糊用法/token/控件/坑表/验收流程）。
- **何时加载**：凡要用本库（`com.originui.replica.*`）给 App 加 UI、做原生模糊、复刻控件，
  或把本库迁进 BetterOriginOS / 其他模块时，**先加载该 skill 再动手**。
- **加载方式**：用户输入 `/originui-integration`；或说"按 originui-integration skill 做"；
  模型侧用 Skill 工具加载。若会话技能列表里没有（skill 于 2026-10-26 新建），
  新开对话会自动发现，临时用可直接 `Read .zcode/skills/originui-integration/SKILL.md`。
- **与本项目另一 skill 的关系**：改 BetterOriginOS 模块本身（Feature/Hook/构建/真机验证）先加载
  `.zcode/skills/betteroriginos-dev/SKILL.md`；涉及 UI 库接入再叠加本 skill。
- **读序**：`out/HANDOFF-1026-OriginUI.md` → 本 LOG 尾部 → 对应 skill → 代码。
