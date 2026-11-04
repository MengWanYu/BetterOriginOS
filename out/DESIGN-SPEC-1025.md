# BetterOriginOS 设计规范（2026-10-25 · OS5 视觉重构版）

> 适用：BetterOriginOS LSPosed 模块的设置 App 与功能卡 UI。新增功能/新增作用域应用必须先过本规范。
> 本版设计 token 全部逆向自本机 OriginOS 5 系统应用（`com.android.settings` 的 `vivo_card_style_*` 资源 + 真机取色），不再参考 HyperOS/miuix 观感。

## 1. 定位与原则

- **定位**：OriginOS 5 的系统界面增强模块，先做「通知中心媒体卡」，后续按应用扩展（系统桌面、设置等）。
- **原则**：
  1. **视觉以本机 OS5 系统应用为基准**（设置/i 管家），复刻其颜色、圆角、行高、底栏结构。
  2. 一个功能必须有明确作用域应用、开关、可回滚路径。
  3. 标准接口优先（如 MediaSession），平台私有通道（如 vivo `seekPos`）必须有证据与回退。
  4. 写入必须可感知：结果反馈、待生效计数、重启前确认。

## 2. 信息架构

```
BetterOriginOS（MainActivity，底栏双页）
├── 首页 tab
│   ├── 大标题 BetterOriginOS + tagline
│   └── 应用列表（每个作用域应用一张分组卡）
│       └── 应用详情 AppDetailActivity
│           ├── 头部：返回 + 应用名/包名 + APK 本身图标 + 右上角重启小图标（带确认对话框）
│           ├── 搜索栏（过滤分类/功能/选项文案；搜索时强制展开并隐藏预览）
│           ├── 媒体（category 分组小节，可点击折叠/展开，箭头指示）
│           │   ├── 真实媒体卡预览（常驻置顶，随分类折叠；见 §5.1 实时渲染链路）
│           │   └── 功能卡片：通知中心媒体卡 [开关]
│           │       └── 卡片样式：当前值 >  → 行下方小下拉菜单（预览图 + 勾选）
│           └── 底部：待生效提示（N 项修改，0 项时隐藏）
└── 模块信息 tab
    ├── 应用信息卡（模块图标/版本/引擎说明）
    ├── 生效状态卡（待生效计数 + 重启 SystemUI 胶囊按钮 + 说明）
    └── 作用域卡（scope 列表 + LSPosed 勾选提示）
```

- **主页只放应用入口**，不放具体功能；应用变多时自动成列表。
- **重启入口两处**：详情页右上角小图标（图标按钮 + 确认对话框）、信息页生效状态卡（胶囊按钮 + 确认对话框）。均带确认。

## 3. 设计 token（逆向自 OS5）

### 3.1 颜色（`values/colors.xml` / `values-night/colors.xml`）

| Token | Light | Dark | 来源 |
|---|---|---|---|
| bg_page | #F2F2F5 | #000000 | `vivo_card_style_bg_root_view_color` |
| bg_card | #FFFFFF | #202020 | `vivo_card_style_bg_group_view_color` |
| text_primary | #DE000000 | #F2F2F2 | 87% 黑 |
| text_secondary | #61000000 | #99FFFFFF | 38% 黑 |
| text_tertiary | #42000000 | #61FFFFFF | 26% 黑 |
| section_label | #78000000 | #80FFFFFF | `vivo_card_style_category_title_color` |
| divider | #14000000 | #1FFFFFFF | 发丝线 |
| accent | #0F77FF | #2785FF | `vivo_btn_blue_color_rom15_0`（vivo 蓝） |
| accent_soft | #1A0F77FF | #332785FF | 图标底/定制图标 |
| track_off | #E3E5EA | #3A3C40 | 开关关闭轨道 |
| switch_thumb | #FFFFFF | #FFFFFF | 开关钮 |
| sheet_scrim | 32% 黑 | 40% 黑 | 对话框遮罩 |

- 强调色只用于：选中态、开关开启轨道、当前选项值、主按钮、可点文本链接。
- 卡片**无描边无阴影**，靠底色差分层。

### 3.2 形状与间距（`values/dimens.xml`）

| Token | 值 | 来源 |
|---|---|---|
| card_radius | 12dp | `vivo_card_radius` |
| page_margin | 20dp | `vivo_card_margin` |
| card_gap | 8dp | 分组卡间距 |
| row_pad_h | 16dp / row_pad_v 14dp | 列表行内边距 |
| row_icon_size | 24dp | `vivo_item_widget_small_item_icon_size` |
| row_title_size | 15sp | `vivo_item_widget_small_item_title_size` |
| row_summary_size | 12sp | 说明文字 |
| page_title_size | 28sp medium | OS5 设置大标题 |
| tab_bar_height | 64dp | 底栏（i 管家基准） |
| pill_height | 44dp / pill_radius 22dp | 胶囊按钮 |

### 3.3 分组列表（OS5 核心视觉）

- 应用列表采用**行级圆角**：`bg_item_top`（上 12dp）/`bg_item_mid`（直角）/`bg_item_bot`（下 12dp），组内行间 1dp 发丝分隔线（左内缩 70dp），组间 8dp 透明间隔行（ListView 无 item margin，用 spacer 行实现）。
- 功能卡是**整卡**（`bg_card`），卡内选项用分隔线。

### 3.4 通知观感（OS5 基准，移植到 OS3 用同一套）

数值全部逆向自本机 OS5 SystemUI，实现落在 `feature/notify/Os5Mirror`，常量表在 `Os5Geometry`/`Os5Mirror`：

| Token | 值 | 来源 / 作用 |
|---|---|---|
| 半径档位 | 0→12dp、1→18dp、2/3→24dp | `VivoColorAndCornerPicker.getNotificationDefFillet`；写进 A13 的 `ExpandableOutlineView.mOutlineRadius` |
| 圆角形状 | vivo G2 连续角 | `G2CornerUtil`：门限 **2.2r**；`>2.6r` 用 `-0.013r/0.61r/1.3r`，`2.2~2.6r` 用 `-0.008r/0.4r/1.1r`；弧 205/295/25/115 各扫 40° |
| 通知间距 | 7dp | OS5 收纳栏/列表间隔（A13 原生是 0） |
| 收纳栏圆角 | 顶 + 底都圆（`requestRoundness(1,1)` 语义） | A13 原生只圆底，`setTopRoundness(1.0f,false)` 补 |
| 一键清除按钮 | 46dp 圆形、内边距 2dp、底边距 40dp（导航条**此刻可见**时 62dp） | 类：`com.vivo.systemui.statusbar.ui.NotificationClearAllImageView`（A15 命中 6、**A13 三个 dex 全 0**）⇒ A13 补挂一枚，图标随模块打包（`bo_ic_clear_all_os5.xml` 与 OS5 逐字节一致）；**先探测该类存在再决定补挂**，系统已有就整段跳过，避免 OS5 上长出第二枚。数值实值用 `aapt dump --values resources` 读（size/padding/margin_bottom/margin_bottom_with_navigationbar = 46/2/40/62dp；A13 里这些名字命中 0 ⇒ 必须打包）。40↔62 的分支 OS5 看 `CentralSurfaces.isNavigationBarVisible()`（手势 hint bar 也算可见），并在配置变更时重算 ⇒ 模块用 `getInsets(Type.navigationBars())` 非零复刻（兜底 `config_showNavigationBar`），日志 `navSrc=insets\|config`。**宿主必须是 `NotificationPanelView`**：A13 的 NSSL 父层 `NotificationsQuickSettingsContainer` 是 ConstraintLayout，会吞掉 `FrameLayout` 的 gravity |
| 按钮阴影 | `#1AEEEEEE` + `BlurMaskFilter(45f, OUTER)` | 同上 |
| 按压/出现 | 200ms / 150ms，`PathInterpolator(0.25,0.45,0.3,1)` | OS5 四处动画同一曲线；**清除时的整栏消失由系统自己播**（`updateShelfWhenDismissAll→animateShelfDisappear`，两版逐参数相同、且带 `mClearAllInProgress` 与 `z=!hasUnClearableNotifications` 两道门），模块不补播 |
| 内联文字按钮 | A13 按 OS5 写成 `GONE(8)` | 不只是去装饰：`changeShelfVisibility(z)` 用 `(z && 内联==8)` 决定收纳栏收不收，A13 原生 `setDismissVisible` 是 `z?0:8`，会把栏顶住不收 |
| 显隐闸门 | `shouldShowClearAll && 面板已展开 && 非 QS 全屏 && 未锁屏` | A13 用 `isExpanded()` 退路 + 公开 API `KeyguardManager.isKeyguardLocked()` |

选项：`通知条美化 → 分割方式 sep = 关闭（本机原生）/ OS5 复刻`，**只在 A13 出现**（A15 原生就是这套，不必复刻）。

### 3.5 Android 11（OriginOS Ocean）音乐卡规格

`feature/media/A11MediaSpec`，OS3 与 OS5 共用同一 `a11` 样式；数值取自 Y7s 的 `framework-res.apk`/`SystemUI.apk` 资源表（不是目测）：

| Token | 值 | 实读出处 |
|---|---|---|
| 展开态封面 | 52dp，圆角 **2dp**（A11 主题常量，不跟宿主 `dialogCornerRadius`） | `dimen/qs_media_album_size`、`Theme.DeviceDefault → config_dialogCornerRadius=2dp` |
| 收起态封面 | 36dp，`CENTER` 不裁剪 | `dimen/notification_right_icon_size` |
| 五枚控制键 | 48/48/**52**/48/48dp（中间播放键） | `media_view.xml` 的 `MediaPlayer.Button` 排 |
| 进度轨道 | 3dp（禁用态 1dp） | `qs_media_enabled/disabled_seekbar_height`（是 SeekBar 的 `maxHeight`） |
| 白色阶 | 正文 `#FFFFFFFF`、副文 `#99FFFFFF`、已播放 `#C0FFFFFF`、轨道 `#80FFFFFF` | `color/media_secondary_text` 等 |
| 展开态字号 | 标题 16sp medium、艺名/时间 14sp | 布局里 16sp 恰好 1 个节点、14sp 5 个 |
| 收起态字号 | 标题 14sp `sans-serif-medium`、副文 12sp，动作带高 60dp | `TextAppearance.Material.Notification.Title` 链 |
| 取色 | 收起=着色行（左半幅 dominant + 右 60% 反色相 + WCAG 4.5 校正）；展开=恒深卡（L 锁 25%、饱和 ×0.8）+ ±0.15 高光 | `MediaNotificationProcessor`、`MediaStyle.ensureColors`、`MediaDataManager` |
| 无封面兜底 | 展开卡 `0xFF404040`（把白色继续送进同一条 HSL 变换） | `MediaDataManager.java:534-553` |

## 4. 组件规范

| 组件 | 规范 |
|---|---|
| 底栏 tab `bottom_bar_item` | 图标 24dp + 标签 10sp；选中 accent / 未选 text_secondary（`color/tab_icon`、`color/tab_label` 选择器）；背景随 bg_page，无边框 |
| 应用行 `item_app` | APK 本身图标 40dp + 名称 15sp + 包名 12sp + 「N 项功能 · M 已启用」12sp tertiary + chevron；长文本 ellipsize；整行 ripple |
| 功能卡 `item_feature` | `bg_card`；图标 24dp accent + 标题 15sp + 开关；选项区分隔线；功能关闭时选项区 alpha 0.45 且不可点 |
| 落点说明 `feature_note` | 功能卡摘要下方一行 tertiary 12sp（`Feature#supportNoteRes(ctx)` 非 0 才出现，否则 GONE 不占位）；只写"本机为什么看不到效果"，≤2 行、不夹带取证术语 |
| 选项-选择行 `item_option_choice` | 标题/说明 + 当前值（accent 14sp）+ chevron；点击弹出**行下方小下拉菜单** |
| 下拉菜单 `ChoiceDropdown` | `ListPopupWindow` 锚定选项行，`bg_menu`（12dp 圆角卡），行 = 预览图 36dp + 标签 15sp + 勾选；宽 = 锚点行宽（≥260dp）；下方偏移 6dp |
| 选项-开关行 `item_option_switch` | 标题/说明 + OS5 开关（thumb inset 3dp 白钮 / track inset 圆角 14dp，开启 accent 关闭 track_off） |
| 确认对话框 `ConfirmDialog` | 居中 `bg_dialog`（16dp 圆角卡）+ 标题 16sp bold + 说明 13sp secondary + 右下文字按钮（取消 secondary / 确认 accent bold） |
| 主按钮 | 胶囊 44dp（`bg_button` accent + ripple），白字 15sp medium |
| 重启小图标 | 40dp 触控区（24dp 图标内边距 8dp），`ic_restart` 环形箭头，位于详情头部右侧 |

## 5. 交互与状态（写入链路）

### 5.1 真实媒体卡预览（实时渲染链路）

预览不手绘模拟，而是**渲染真实 SystemUI 卡片**，调整样式即时反馈、无需重启：

```
设置 App                            SystemUI（Hook 侧）
   │ su: settings put global            │ ContentObserver 监听
   │ betteroriginos_preview_req          ▼
   │ = "<时间戳>:<样式>" ──────────▶ 离屏合成模板（真实标题/封面/取色）
   │                                    + 复用产线 MediaCard.build()
   │                                    ▼ Bitmap → PNG
   │ ◀─── done 回显完整请求串 ────── ① 文件（filesDir/preview，su 兜底）
   │ 轮询 done 匹配（≤5s）              ② MediaStore Pictures/BetterOriginOS（主通道）
   │ ▼                                  （App 持 READ_MEDIA_IMAGES，adb 授权一次）
   │ MediaStore 读取 → 显示真实卡片
```

- **主通道 = MediaStore**：App 的 su 会话受 KernelSU 挂载命名空间隔离，读不到 SystemUI 文件目录（ENOENT），且 su 管道会污染二进制；MediaStore 双方均可靠访问。
- **兜底 = 手绘预览**（`MediaCardPreview`）：参数严格取自产线 `MediaCard.build()`（渐变/圆角 24|28dp/封面 60|64dp/标题 17|16sp/轨道 4dp+已播放 10dp/accent），真实渲染尚未生成（开机后未播放过音乐）时展示。
- **触发时机**：详情页进入、样式下拉切换；另在 Hook 每次构建真实在屏卡片后自动抓屏更新（真实封面/标题）。
- 预览归属首个分类，**随分类折叠**；搜索过滤时隐藏。

### 5.2 写入与状态

- **SuCommander（`core/SuCommander`）**：单线程 su 执行队列。
  - 同 key **250ms 防抖合并**，快速拖开关只发一条 `settings put global`。
  - 每次写入带回执：成功 → 待生效计数 +1 并广播；失败 → toast「写入失败，请检查 KernelSU 授权」。
  - 重启 SystemUI 成功 → 待生效计数清零。
  - 8s 超时保护；**读文件必须边读边等**（先 waitFor 会因 >64KB 管道缓冲写满死锁）。
- **待生效提示**：详情页底部（>0 时显示）与信息页生效状态卡实时同步「本次运行已修改 N 项」。计数为会话级（App 进程内存）。
- **状态读取**（`core/Prefs`）：`Settings.Global` 优先 → 本地 `XSharedPreferences` 回退 → 默认。legacy 键迁移由各 Feature 通过 `registerLegacyFeatureKey/registerLegacyOptionKey` 注册，**Prefs 不再硬编码具体功能**。
- **生效**：修改后需重启对应作用域应用；重启入口均有确认对话框。
- **关闭功能**：选项区 alpha 0.45 且 `setEnabled(false)` 拦截点击；预览同步置灰。
- **搜索**（详情页）：命中功能标题/说明/分类名/选项标题/说明/选项值；搜索时强制展开并隐藏预览；清空恢复折叠状态。

## 6. 工程架构

```
com.betteroriginos
├── core
│   ├── Feature.java / FeatureOption.java   功能与选项模型（options() 缓存）
│   ├── FeatureManager.java                 注册表、按包分发
│   ├── Prefs.java                          双通道配置（legacy 注册表化）
│   ├── SuCommander.java                    su 队列/防抖/回执/待生效计数/读文件
│   └── IconMapper.java                     图标与名称（直接取 APK 本身图标）
├── feature/media/MediaCardFeature.java     媒体卡引擎 + 真实预览渲染（离屏 build + MediaStore 发布）
│     └ A11Palette / A11MediaSpec           A11 取色链（量化-选择-对比度校正）与规格常量
├── feature/notify/NotifyRowFeature.java    通知条美化（含 sep=off/os5 开关，仅 A13 出现）
│     └ Os5Mirror / Os5Geometry             OS5 通知几何移植：半径档位、G2 连续角、收纳栏、浮动一键清除
├── feature/picker/PickerAlbumsFeature.java 修复照片选择器相册白名单（scope: media module）
├── hook/MainHook.java                      薄入口
└── ui
    ├── MainActivity.java                   底栏双页（首页/模块信息）
    ├── AppDetailActivity.java              应用详情（搜索栏 + PendingListener）
    ├── AppListAdapter.java                 首页应用列表（行级圆角分组）
    ├── FeatureAdapter.java                 分类折叠 + 预览项 + 功能卡 + 过滤
    ├── ChoiceDropdown.java                 行内下拉（预览图）
    ├── ConfirmDialog.java                  确认对话框
    ├── RealPreview.java                    真实预览加载器（请求协议 + MediaStore 读取 + 缓存）
    └── MediaCardPreview.java               手绘兜底预览（产线参数）
```

- 无 androidx/第三方依赖：列表用框架 `ListView + BaseAdapter`，弹层用 `ListPopupWindow`/自绘 Dialog。
- 构建：`module/build.sh`（aapt2 → javac → d8 → 注入 dex → zipalign → apksigner）；路径由脚本位置推导（`cygpath -m` 转 Windows 形式）。
- 权限：`READ_MEDIA_IMAGES`（读取 Hook 发布的预览图；安装后 `adb shell pm grant` 一次）。
- **作用域**：`com.android.systemui`（媒体卡）+ `com.android.providers.media.module`（选择器相册修复，需 LSPosed 勾选并重启该模块进程）。

### 新增功能清单

1. 在 `feature/<area>/` 新建 `XxxFeature extends Feature`，实现元数据与 `install()`；legacy 键在 `static {}` 注册到 Prefs。
2. 在 `FeatureManager.FEATURES` 注册。
3. `strings.xml` 增加文案；需要预览图时加 drawable（下拉菜单行自动展示 `iconRes`）。
4. LSPosed scope 勾到 `scopes()` 并集；重启对应应用。
5. UI 自动出现：新应用进首页列表，新功能按 category 分组进详情页。

## 7. Do / Don't

- ✅ 一个应用一张分组卡；一个功能一张整卡；选择类选项用行内下拉。
- ✅ 视觉 token 只从本机 OS5 系统应用逆向，不凭感觉配色。
- ✅ 所有写入走 SuCommander（防抖 + 回执），禁止裸 `su`。
- ❌ 不在功能卡里堆叠超过 2 个选项；复杂配置放二级页。
- ❌ 不引入第三方 UI 库（miuix 路线已评估：fan.miuix 依赖锁在 GitHub Packages，需要 token，暂缓）。
- ❌ 不用底部大弹层做单选项选择（遮挡全局、观感突兀）。

## 8. 验收：不看截图

样式选项当前有两处会改变观感：媒体卡 `style = os5 / material / a11`（下拉带预览），
通知条 `sep = off / os5`（仅 A13）。判定一律走可复跑的取证，不承认目测：

- **一条命令跑完所有离线检查**：`bash work/offline-all.sh`（16 项）—— G2 逐指令比对与"翻符号/挪量级"负对照、
  **浮钮几何判据自测**（`work/os5mirror-geom.sh` 被真机脚本 source，所以它自己也进离线门禁：
  正例 + 左上角 / 上半屏 / 缺日志 / 读不到屏尺寸 / 日志字段漂移 五条必须红）、
  A11 数学/交叉验证/**量化器 median-cut**/取色网格/**六族选择逻辑**（都反射跑模块自己的字节码 + 独立参考）、
  验证器正反例自测、Y7s 实读取证核对、日志形状自检（正则锚点必须能在源码里找到对应日志串）。
- **真机门禁**：`work/os3-smoke-os5mirror.sh`（A13：钩子命中数 + 落点自检 + 几何度量 +
  按日志坐标 tap + 位置硬判据 + sep=off 负对照）、
  `work/a11-card-gate.sh <序列号> <os3|os5>`（媒体卡度量 + 第 4b 步"两条线互不干扰"：
  A15 上复刻钩子必须 0 次、A13 上必须 ≥1 次）、`work/os3-smoke-light.sh`、`work/os3-smoke.sh`（回归）。
- **度量日志即断言**：`os5_mirror: radius level/px`、`g2 path`、`bg clip g2 r= bounds=`、
  `shelf gap topRoundness`、`clear-all native=`、`clear-all attached size marginBottom nav navSrc navVis navH shade host=`、
  `clear-all at x y w h`、`clear-all tap inlineListener inlineVis shelfVis`、
  `a11 card spec … radius hostCorner`、`a11 palette expanded/collapsed … art gridBg gridFg contrast`，
  由 `work/verify_os5_mirror.py` 按 dp×density 逐值判定（A13 与"系统自带圆钮"两套正例都要绿）。
- 结论与踩坑同步写进 `out/DEVLOG.md` 与 `out/LOG-<日期>-*-ADAPT/RE.md`；跨版本改动先按版本关门（`OsProfile`）。
