# LOG-1102：OS5(A15) 通知实现逆向 → OS3(A13) 反向移植研究

## 任务范围
在 **纯静态、离线**（不连设备、不截图）前提下，逆清三处 OriginOS 5 / Android 15 通知实现，并与 OriginOS 3 / Android 13 对照，给出可落地的移植方案：

1. 通知条/卡片 **圆角** 的算法归属、时机、字段与数值来源。
2. **收纳栏 NotificationShelf** 的视图树是否仍在、两块渲染差异、把 OS3 做成 OS5 观感需要改哪些方法/字段。
3. **一键清除** 按钮在类/布局/图标/点击链路上的差异，以及观感元素差异。

## 取证方法
- jadx 1.5.5 CLI（`--single-class`）反编译单类，产物统一落在 `work/tmp-re-os5toos3/`（OS5 主体复用 `work/systemui/out-src/`，OS3 复用 `G:/os3-ref`）。
- dexdump 现成产物核对类与成员存在性：`work/systemui/classes5.dump`、`work/systemui/shelf_block.txt`（NotificationShelf 块）、`work/systemui/bg_block.txt`（NotificationBackgroundView 块）、`G:/os3-ref/os3-dexdump-f.txt`（**直接 grep，未重跑**）。
- 类清单：`work/systemui/classlist.fqcn.txt` / `G:/os3-ref/os3-systemui-classes.txt` / `os3-plugin-classes.txt`。
- 数值：`work/systemui/res/res/values/dimens.xml`（apktool 反解）、`work/systemui/res/res/layout/status_bar_expanded.xml`。

## 证据可复核承诺
本报告每条结论后括号标注 **文件路径 + 行号**，或 **类名 + 方法名 / 字段名（dump 出处）**。无二进制直接支撑的推断一律标 `[假说]`。所有引用文件均为本机现存素材，可用 Read/Grep 逐条复核。

---

# 一、通知条圆角

## 1.1 OS5(A15) 实现：谁在什么时机算出圆角

圆角是一条 **三段流水线**，落在三个不同的类里：

### (A) 基准半径 maxRadius 的来源（dp 数值 + 覆盖入口）
- 计算点：`ExpandableOutlineView.initDimens()` 把基准半径写进 `RoundableState.maxRadius`。
  - 默认取 `R.dimen.notification_shadow_radius`；
  - 若 `VivoColorAndCornerPicker.supportColorAndCornerPicker()` 为真（恒真），改用 `VivoColorAndCornerPicker.getNotificationDefFillet(ctx, getFilletLevel())`。
  `(work/tmp-re-os5toos3/com.android.systemui.statusbar.notification.row.ExpandableOutlineView.java:265-283)`
- 数值（vivo overlay 的 SystemUI dimen）：
  - `notification_shadow_radius = 18.0dp`、`_default = 15.0dp`、`_mellow = 24.0dp`、`_sharp = 12.0dp`、`_smooth = 24.0dp`
  `(work/systemui/res/res/values/dimens.xml:1897-1901)`
- 档位→dimen 映射（vivo "圆角调节/主题圆角拾取器"）：level 0→sharp 12dp、1→default 18dp、2→smooth 24dp、3→mellow 24dp，其它回落 18dp。
  `(work/tmp-re-os5toos3/com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker.java:194-206)`
- **来源不是 Settings.Global**：档位与原始 fillet 由 vivo 主题服务 `ThemeIconManagerProxy.getSystemFillet()/getSystemFilletLevel()` 提供，注册监听 `SystemFilletListener.onSystemFilletChanged` 实时刷新。
  `(同上 VivoColorAndCornerPicker.java:83-84, 176-192)`
- 实时改半径：`ExpandableOutlineView.onSystemFilletChanged(i, i2)` → `RoundableState.setMaxRadius(i)` → `applyRoundnessAndInvalidate()`（i==-1 时回退 `notification_shadow_radius`）。
  `(同 ExpandableOutlineView.java:295-303)`

### (B) 圆角"系数"（roundness 0..1）：多源取最大
- 接口 `Roundable`（A15 独有的一套 Kotlin 架构）：`getTopCornerRadius() = getMaxRadius() * getTopRoundness()`；命中 flag `notificationsImprovedHunAnimation` 时改为直取 `RoundableState.getTopCornerRadius()`。
  `(work/tmp-re-os5toos3/com.android.systemui.statusbar.notification.Roundable.java:32-41)`
- 每条边由多个 `SourceType` 分别请求，`requestTopRoundness/BottomRoundness` 用 `Map<SourceType,Float>` 取 **max** 作为最终系数；置 0 即撤销该源。
  `(同 Roundable.java:49-95)`
- 触发时机：`ExpandableOutlineView` 在 `setActualHeight / setClipTopAmount / setClipBottomAmount / setOutlineAlpha` 每次都 `applyRoundnessAndInvalidate()`；`ExpandableNotificationRow.onAttachedToWindow → updateBaseRoundness()`。
  `(ExpandableOutlineView.java:306-336；ExpandableNotificationRow.java:3933-3937)`

### (C) 半径落进 drawable 的唯一写入点
- `ActivatableNotificationView.applyRoundnessAndInvalidate()` → `applyBackgroundRoundness(getTopCornerRadius(), getBottomCornerRadius())` → `mBackgroundNormal.setRadius(top, bottom)`。
  `(work/systemui/out-src/com.android.systemui.statusbar.notification.row.ActivatableNotificationView.java:672-698)`
  其中 HUN 出现动画期间由 `CapsuleAnimationUtils.getCornerRadiusByFraction(...)` 插值覆写顶/底半径（`mIsPressCapsule + mFakeStatusBarView + mIsHeadsUpAnimation + mDrawingAppearAnimation`）。`(同 679-693)`
- `NotificationBackgroundView.setRadius(f, f2)` 是唯一写 `mCornerRadii` 的地方：`fArr[0..3]=top`、`fArr[4..7]=bottom`；**若 `mBottomPadding>0` 强制 bottom=0**；相等则早退。
  `(work/systemui/out-src/com.android.systemui.statusbar.notification.row.NotificationBackgroundView.java:376-394)`
- `updateBackgroundRadii()`（vivo 关键定制）：
  - LayerDrawable → 逐层 `GradientDrawable.setCornerRadii(mCornerRadii)`；
  - **`mBackground instanceof VRoundedCornerDrawable`** → `setRadius(G2CornerUtil.getG2Radius(max(top,bottom)))` + `setCorner(top>0,top>0,bottom>0,bottom>0)`；
  - `VivoNotificationBlurController.setMaterialCornerRadius(CornerRadius(top,top,bottom,bottom))`（毛玻璃按角裁切）。
  `(同 NotificationBackgroundView.java:403-431；字段声明 41-116，`mDontModifyCorners = R.bool.config_clipNotificationsToOutline`)`
- G2 连续角（squircle）本身：`G2CornerUtil.getG2Radius(f)` 在本 flavor 是 **恒等**（`return (int) f`），连续角形状在 `VRoundedCornerDrawable` 内部靠 `canvas.drawG2RoundRect` 反射原生方法实现。
  `(work/tmp-re-os5toos3/com.originui.core.utils.G2CornerUtil.java:30-32, 278-280；canvasDrawG2RoundRectRadii 282-304)`

### 分组摘要 vs 子行的圆角差异
- 子行基准：`ExpandableNotificationRow.updateBaseRoundness()` —— 若 `isChildInGroup()` 则 `requestRoundnessReset(BASE_VALUE)`（系数 0），否则用 `mSmallRoundness`。而 `mSmallRoundness = notification_corner_radius_small / getMaxRadius()`，且 **`notification_corner_radius_small = 0.0dp`** → 组内非首尾子行拿到的是 **直角**。
  `(work/systemui/out-src/ExpandableNotificationRow.java:1745, 3924-3931；dimens.xml:1841)`
- 容器裁决：`NotificationChildrenContainer.applyRoundnessAndInvalidate()` —— 组头 `requestTopRoundness(getTopRoundness())`（顶圆角），子行倒序遍历，**只有第一条可见子行** `requestRoundness(0, getBottomRoundness())`（底圆角），其余 `(0,0)`。即整组只有 **顶（组头）+ 底（末子行）** 圆，中间全方。
  `(work/systemui/out-src/NotificationChildrenContainer.java:1450-1467)`
- 行→容器传递：`ExpandableNotificationRow.applyChildrenRoundness()` 把父系数下发给容器，且当 vivo 快捷屏蔽菜单 `mQuickBlockMenu` 可见时强制 bottom=0。
  `(ExpandableNotificationRow.java:3683-3690)`
- 容器半径同步：`NotificationChildrenContainer.updateMaxRadius(f)` 把 maxRadius 同步到组头/最小化组头的 RoundableState。`(NotificationChildrenContainer.java:1731-1741)`

### 「A15 独有字段」前提核对（重要纠正）
- 题设里说的 `NotificationChildrenContainer.mCornerRadius / mSidePaddings / mMinimumPaddings / mPaddingBetweenElements` 是 **AOSP-15 上游** 的名字；**vivo OS5 的 `NotificationChildrenContainer` 里根本没有这些字段**。
  `(grep work/systemui/out-src/NotificationChildrenContainer.java 对 mCornerRadius/mSidePaddings/mMinimumPaddings/mPaddingBetweenElements = 0 命中；字段清单 85-139)`
- vivo OS5 用的是 **与 A13 同源的老字段词表**：`mChildPadding`、`mDividerPadding`、`mNotificationTopPadding`、`mCornerRadii(float[8])`、`mRoundableState`；数值来自 `R.dimen.vivo_notification_children_padding=0.0dp`、`vivo_notification_divider_padding=12.0dp`、`notification_children_container_top_padding`、`notification_children_container_margin_top`。
  `(NotificationChildrenContainer.java:90,102,131,136；initDimens 203-223；dimens.xml:3374,3382)`
- `mPaddingBetweenElements` 只在 **NotificationShelf** 里存在（见第二节），不在 children 容器里。
  `(work/systemui/shelf_block.txt:108)`
- 结论：这条差异不是"A15 有 A13 没有"，而是"vivo 两版都改名/未采用上游字段"。移植时**不要**去反射 AOSP 的 `mSidePaddings/mCornerRadius`，两版都没有。

## 1.2 OS3(A13) 现状
- `NotificationBackgroundView` 是 **纯 AOSP A13** 版：`setRadius(f,f2)` 写 `mCornerRadii` 后 `updateBackgroundRadii()` **只处理 `LayerDrawable → GradientDrawable.setCornerRadii`**，**没有 `VRoundedCornerDrawable`、没有 `G2CornerUtil`、没有 `VivoNotificationBlurController`**。
  `(work/tmp-re-os5toos3/OS3-NotificationBackgroundView.java:221-252)`
- `ActivatableNotificationView` 走 **旧 API**：`applyRoundness()` → `getCurrentBackgroundRadiusTop()/getCurrentBackgroundRadiusBottom()` → `applyBackgroundRoundness(...)` → `mBackgroundNormal.setRadius(...)`。没有 `getTopCornerRadius()/applyRoundnessAndInvalidate()` 这套。
  `(work/tmp-re-os5toos3/OS3-ActivatableNotificationView.java:527-533)`
- 类清单里 **不存在** `Roundable / RoundableState / SourceType`（A15 系数架构在 OS3 缺失）。
  `(grep /g/os3-ref/os3-systemui-classes.txt：.Roundable/RoundableState/SourceType = 0)`
- `com.originui.core.utils` 的 `G2CornerUtil/VRoundedCornerDrawable` 在 **OS3 SystemUI 与 SystemUIPlugin 全都不存在**。
  `(grep os3-dexdump-f.txt "Lcom/originui/core/utils/*;" = 0；grep os3-plugin-classes.txt G2Corner/VRounded = 0)`

## 1.3 差异结论
| 维度 | OS5(A15) | OS3(A13) |
|---|---|---|
| 基准半径来源 | dimen 18/12/24dp × 主题 fillet 档位，可实时改 | 同一套 dimen 存在（vivo 未删），但半径写入路径不同 |
| 系数机制 | Roundable + RoundableState + SourceType 多源取 max | 直存 top/bottom 系数（setTopRoundness/setBottomRoundness） |
| 背景 drawable | VRoundedCornerDrawable（**连续角/squircle**）+ 毛玻璃按角 | LayerDrawable/GradientDrawable（**普通圆角矩形**） |
| 组内子行 | 非首尾子行直角（small=0dp），仅组顶/组底圆 | 同机制，vivo 保留 |
| A15 新字段 | **无**（题设前提不成立） | 无 |

**关键不可移植项**：OS3 的观感上限是"标准圆角矩形"，拿不到 OS5 的 **G2 连续角 + 逐角毛玻璃**，因为承载这两者的 `com.originui.core.utils` 库在 OS3 两个 APK 里都没有。半径数值(18dp)、圆/方分布可以复刻；squircle 曲率过渡不能。`[假说]` 除非把 originui 库自身（其 dex/资源）也注入 OS3 SystemUI 进程，但这是另一个量级的工作。

## 1.4 移植方案
1. **只复刻半径数值与方圆分布**（可落地）：沿用现有模块的 `installShelfCornerHooks`/`installRoundnessHooks` 思路——在 `NotificationBackgroundView.setRadius` 前后按目标分布改 `top/bottom`。OS3 的 `setRadius(float,float)` 签名与 OS5 一致，`mCornerRadii` 字段名一致，hook 点安全。`(module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java:989-1071, 993-1014)`
2. **A13 缺字段兜底**：需要"半径"时不要反射 AOSP `mSidePaddings/mCornerRadius`；读 `RoundableState` 在 OS3 不存在，应改读 `ExpandableOutlineView.mRoundableState`？——**OS3 也没有**。正确兜底是从 `getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius)` + 主题档位自己算 px（档位 OS3 也可从 `VivoColorAndCornerPicker.getNotificationDefFillet` 取，OS3 有该类）。`(grep os3-systemui-classes.txt: com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker 存在)`
3. **逐字段反射各自 try**：容器 `mChildPadding/mDividerPadding/mNotificationTopPadding` 两版都有，可直接反射；但 `mCornerRadius/mSidePaddings` 两版都无，反射需逐个独立 try + `applied=N/total` 日志（skill §4 既有约束）。
4. **风险与回归面**：改 setRadius 会影响 HUN、组内所有行、shelf 三处背景；改系数要与 StackScroll 的 layout/scroll roundness 源协调，否则滚动贴边会顶掉设置。回归必跑 `work/os3-smoke.sh`。

---

# 二、收纳栏 NotificationShelf

## 2.1 OS5(A15)：视图树仍在，且被 vivo 深度定制
- **A15 上游把 shelf 换成了 collection header / Compose `NotificationShelfSpace`，但 vivo OS5 仍保留 `com.android.systemui.statusbar.NotificationShelf`（非 Compose）并在用。**
  `(work/systemui/classlist.fqcn.txt: com.android.systemui.statusbar.NotificationShelf 存在；classes5.dump 含其 descriptor；另有 notifications.ui.composable.NotificationsKt$NotificationShelfSpace 属未启用的 Compose 支路)`
- 类结构：`NotificationShelf extends ActivatableNotificationView`，所以它 **本身就是一个通知行外壳**，自带背景（`mBackgroundNormal`）、圆角、blur。
  `(work/systemui/shelf_block.txt: Superclass 'ActivatableNotificationView')`
- 顶部那块渲染由以下成员/方法负责：
  - 背景与圆角：`bind(...)` 注入 `mRoundnessManager (NotificationRoundnessManager)`、`mHostLayout`；`onFinishInflate` 里 `requestRoundness(1.0f,1.0f,BASE_VALUE)` + `requestTopRoundness(1.0f,...)` → shelf **顶底都圆**；`mBackgroundNormal.setParentTag("[StackScroller:Shelf]")`（vivo blur 分组标签）。
    `(work/tmp-re-os5toos3/OS5-NotificationShelf.java:91-92,167-169,189,203-208)`
  - 高度：继承行测量 + `updateIconPositioning/setIconTransformationAmount`；容器间距 `mPaddingBetweenElements = R.dimen.vivo_notification_paddding_between_elements = 7.0dp`。`(OS5-NotificationShelf.java:213；dimens.xml:3393)`
  - 图标：`mShelfIcons (VivoNotificationIconContainer, id R.id.content)`，`setInNotificationIconShelf(true)`、`setOverrideIconColor(true)`。`(OS5-NotificationShelf.java:86,159-166,229-231)`
  - 可清除文案：`mDismissAllNotification` 是 **TextView**（id `R.id.clear_all_notification`），但 OS5 的 `setDismissVisible(...)` **无条件 `setVisibility(8)`**——即 shelf 内联文字按钮被强制隐藏（改由第三节浮动物理按钮承担）。`(OS5-NotificationShelf.java:70,119-128,170-173)`
  - 一键清除动效：`updateShelfWhenDismissAll(z)` → `animateShelfDisappear(z)`：`alpha 1→0`、`scale→0`，**250ms + PathInterpolator(0.33,0,0.67,1)**，结束置 INVISIBLE(4)，`mShelfDisappearAnimatorRun` 守卫。`(OS5-NotificationShelf.java:940-962)`
  - 折叠态隐藏背景：`hideBackground()` 覆写为 `super.hideBackground() || VivoDeviceUtils.isFoldKeyguardOS2(ctx)`。`(OS5-NotificationShelf.java:970-971)`
  - 滚动改角：`updateCornerRoundnessOnScroll` + `requestRoundnessResetFor` + `isViewAffectedBySwipe`（依赖 mRoundnessManager）。`(OS5-NotificationShelf.java:496,511-512,885)`

## 2.2 OS3(A13)：视图树也在，但 API 与形态不同
- OS3 保留 `NotificationShelf + NotificationShelfController`。`(grep os3-systemui-classes.txt: com.android.systemui.statusbar.NotificationShelf / NotificationShelfController)`
- 圆角用 **旧系数 API**：`onFinishInflate` → `setBottomRoundness(1.0f,false)` + `setTopRoundness(0.0f,false)` + `setFirstInSection(true)` → shelf **顶角是直角、底角圆**（与 OS5 顶底都圆相反）。
  `(work/tmp-re-os5toos3/OS3-NotificationShelf.java:135-136,141)`
- 有 **两个图标容器**：`mShelfIcons`(R.id.content) + `mCollapsedIcons`（收起态副本）；`updateRelativeOffset` 操作 mCollapsedIcons。
  `(OS3-NotificationShelf.java:53,68,715,690-691)`
- `updateCornerRoundnessOnScroll` 用 `setTopRoundness/setBottomRoundness + isFirstInSection()/isLastInSection()`（无 mRoundnessManager）。`(OS3-NotificationShelf.java:337-358)`
- `mFirstElementRoundness` / `setFirstElementRoundness(f)` 是 OS3 的"首元素圆角"字段。`(OS3-NotificationShelf.java:57,372-375)`
- shelf 内联"一键清除"文字按钮 **可见**：`setDismissVisible(z)` → `setVisibility(z?0:8)`，文案 `vivo_clear_all_notifications_text`、粗体、`12sp`。`(OS3-NotificationShelf.java:97-98,138-140,198-199)`
- 高度 `notification_shelf_height = 34.0dp`（两版同源）。`(OS3-NotificationShelf.java:163；dimens.xml:1902)`
- OS3 有 `animateShelfDisappear` 但 **没有 `updateShelfWhenDismissAll` 公开入口**（清除时整栏消失的联动 OS5 才有）。`(grep OS3-NotificationShelf.java: updateShelfWhenDismissAll = 0)`
  - **【12:24 已推翻】** 同一条 grep 现在命中 `OS3-NotificationShelf.java:797`：A13 **有** `updateShelfWhenDismissAll(boolean)`，
    参数与 OS5 逐字相同，且由 `NSSL` 的清除监听 lambda 亲自在 `mClearAllInProgress` 时调用（dexdump 调用点 `41b0fa`）。
    当时判 0 的原因是把"jadx 跳过巨型方法"当成了"方法不存在"——见 §9。由此 §2.4-(3)/§3.4 第 3 条的"自己补播 250ms 消失动画"**取消**，见 §9.2。

字段差异（两版共有 vs 独有）：
- 仅 OS5：`mRoundnessManager`、`mHostLayout`、`mEnableNotificationClipping`、`mMaxIconsOnLockscreen`、`mCanInteract`、`mCanModifyColorOfNotifications`、`mShelfDisappearAnimatorRun`、`mYTranslation`。
- 仅 OS3：`mCollapsedIcons`、`mController`、`mFirstElementRoundness`、`mStatusBarState`、`mTmp`。
  `(diff work/tmp-re-os5toos3/{OS5,OS3}-NotificationShelf.java 字段区)`

## 2.3 差异结论
- 两版 shelf **视图树都健在**，不是 collection header 顶替（vivo 未跟进 A15 那条 Compose 路线）。
- 真正差异集中在：① OS5 顶底都圆、OS3 顶直角；② OS5 隐藏内联文字按钮改浮动物理按钮、OS3 显示内联文字按钮；③ OS5 用 NotificationRoundnessManager/SourceType 协调滚动圆角、OS3 用直接 setRoundness + Section 判定；④ OS5 有整栏消失联动 `updateShelfWhenDismissAll`、OS3 无；⑤ OS3 多一套 `mCollapsedIcons` 收起态副本。

## 2.4 移植方案（把 OS3 收纳栏做成 OS5 观感需要动的点）
1. **顶角补圆**（已在做，保留）：现有 `installShelfCornerHooks` 在 `NotificationBackgroundView.setRadius` 把 shelf 背景 top 对齐 bottom，并 `updateBackgroundRadii`/逐层 `GradientDrawable.setCornerRadii` 重刷。OS3 无 VRounded 分支，`forceShelfDrawableCorners` 的 GradientDrawable 分支即生效。
   `(module/.../NotifyRowFeature.java:989-1071, 1080-1129, 1134-1176)`
2. **让 shelf 顶底都圆**：OS3 `onFinishInflate` 只给了 `setTopRoundness(0)`。要么 hook `setTopRoundness/setBottomRoundness`（OS3 行方法）在 shelf 实例上强制 top=1.0，要么沿用第 1 点的 setRadius 源头改写（更稳，绕过动画覆盖）。
3. **整栏消失动效**：hook 一键清除完成回调（见 2.5），对 `NotificationShelf` 施加等价动画：`animate().alpha(0).scaleX(0).scaleY(0).setDuration(250).setInterpolator(new PathInterpolator(0.33,0,0.67,1))`，完成置 INVISIBLE。数值直接照抄 OS5。`(OS5-NotificationShelf.java:946-962)`
4. **隐藏 OS3 内联文字按钮 + 换浮动按钮**：属于第三节联动项，若要完全对齐 OS5，需把 OS3 `mDismissAllNotification` 置 GONE（等价 `setDismissVisible` 恒 8）并挂第三节浮动物理按钮。
5. 缺字段兜底：`mRoundnessManager`/`mHostLayout` OS3 没有——**不要去反射**；OS3 用 `mHostLayoutController`（两版都有）拿宿主列表即可。
6. 风险面：改 setRadius 与"贴边滚动/折叠态"冲突，滚动时会重算 top 系数；因此校正要放在 **每帧 onDraw 兜底**（现有 `enforceShelfCornersOnDraw` 已按签名去重执行）。
   `(module/.../NotifyRowFeature.java:1018-1029, 1181-1231)`

---

# 三、一键清除按钮

## 3.1 OS5(A15)：底部居中的圆形浮动物理按钮
- 实体：`com.vivo.systemui.statusbar.ui.NotificationClearAllImageView extends ImageView`，直接写在展开面板布局里：
  ```
  <com.vivo.systemui.statusbar.ui.NotificationClearAllImageView
      android:id="@id/vivo_notification_clear_all"
      android:layout_gravity="center|bottom"
      android:layout_marginBottom="40.0dp"
      android:src="@drawable/vivo_ic_notification_clear_all"
      android:alpha="0.0" />
  ```
  `(work/systemui/res/res/layout/status_bar_expanded.xml:25)`
- 观感要素（全部 vivo 独有，OS3 无）：
  - **尺寸/内边距**：`vivo_notification_clear_all_size=46.0dp`、`_padding=2.0dp`。`(dimens.xml:3380-3381)`
  - **位置随导航栏/折叠/流体窗动态**：竖屏 gravity=81(center|bottom)，marginBottom 40dp / 62dp(带导航栏) / 90dp(带导航栏+FlowView)；横屏或折叠态 gravity=21(right)，margin_right 变体。`(OS5-NotificationClearAllImageView.java:207-250；dimens.xml:3375-3377)`
  - **圆形 + 外发光阴影**：`onDraw` 用 `BlurMaskFilter(45.0f, OUTER)`、色 `vivo_notification_clear_all_shadow`，画 `drawCircle(size/2, size/2, size/2-padding)`。`(OS5-NotificationClearAllImageView.java:252-265)`
  - **按下缩放动效**：`onTouchEvent` DOWN 缩放 1.0→0.9（200ms，PathInterpolator(0.25,0.45,0.3,1)），UP/CANCEL 回弹。`(同 134-189)`
  - **出现/消失 alpha 动效**：`startClearAllButtonAppear/Disnimator` 150ms。`(同 60-123)`
  - 图标资源：`@drawable/vivo_ic_notification_clear_all`（OS5 SystemUI 资源）。`(public.xml:12215)`
- 显隐：仅当 `shouldShowClearAll() && mIsExpanded && !isQsFullScreen() && !keyguardShowing` 才显示。`(NotificationStackScrollLayout.java:6112-6113)`
- 点击链：`NSSL.setClearAllButtonListener` → `lambda$setClearAllButtonListener$15`：`mClearAllNotisDismissListener.onDismiss()` → `mIsClickClearAllBtn=true` → `clearNotifications(0, z)` → `mClearAllButton.startClearAllButtonDisnimator()`。
  `(NotificationStackScrollLayout.java:6072-6106)`
- 同一路径的 shelf 文字按钮（`setClearNotificationListener` → `lambda$setShelfListener$14`）也调 `clearNotifications(0,z)`，但 OS5 shelf 文字按钮已被强制隐藏（见 2.1）。`(NSSL.java:6046-6070)`
- 批量移除落点：`clearNotifications → getRowsToDismissInBackend(set) → 后端 (NMS) 批量 dismiss`。`clearNotifications(int,boolean,...)` 内 `getVisibleViewsToAnimateAway` + 逐行 `dismissViewAnimated(…, 200ms)`。
  `(NSSL.java:5254-5302)`
- **A15 上游另有 Compose footer（`FooterViewBinder.bindClearAllButton`），但 vivo 走 `FooterViewRefactor.assertInLegacyMode()` 的 legacy 路径**（NSSL 里 clearNotifications 首行就 assertLegacy）。即 vivo OS5 的一键清除仍在旧命令式链路 + 自绘浮动按钮。
  `(classlist: footer.ui.viewbinder.FooterViewBinder 存在；NSSL.java:5255)`

## 3.2 OS3(A13)：shelf 内联的粗体文字按钮
- **不存在** `NotificationClearAllImageView`（类清单 0 命中，且 `status_bar_expanded` 无该节点）。
  `(grep os3-systemui-classes.txt: NotificationClearAllImageView/vivo_notification_clear_all = 0)`
- 一键清除 = shelf 的 `mDismissAllNotification (TextView)`：文案 `R.string.vivo_clear_all_notifications_text`、`Typeface.DEFAULT_BOLD`、`R.dimen.vivo_shelf_dismiss_notification_text_size=12.0sp`、色 `vivo_dismiss_notification_text_color`；有可清除项时 `setDismissVisible(true)` 显示。
  `(OS3-NotificationShelf.java:56,97-98,138-140,198-199；dimens.xml:3597)`
- 点击链同为 `NSSL.setClearNotificationListener → clearNotifications(0,z)`（legacy A13 链路，无浮动按钮动画、无 updateShelfWhenDismissAll 联动）。

## 3.3 差异结论
| 元素 | OS5(A15) | OS3(A13) |
|---|---|---|
| 承载类 | NotificationClearAllImageView（自绘 ImageView） | shelf 内 TextView |
| 形状 | 46dp **圆形** + OUTER BlurMask 阴影 | 文字，无背景/无阴影 |
| 图标 | `vivo_ic_notification_clear_all`（位图矢量图标） | 无图标，纯"一键清除"文字 |
| 位置 | 面板 **底部居中**（marginBottom 40/62/90dp，横屏/折叠改右侧） | shelf 内联右侧，随图标行 |
| 出现条件 | 展开且非全屏QS且非锁屏 | 有可清除项即在 shelf 内显示 |
| 动效 | 按下缩放0.9(200ms)、出现/消失alpha(150ms)、清除整栏消失(250ms) | 无独立动效 |
| 点击后端 | clearNotifications(0,z)→NMS 批量 | clearNotifications(0,z)→NMS 批量（同） |

"OS5 有而 OS3 没有的观感元素" = 圆形图标按钮本体、外发光阴影、按下缩放、出现/消失 alpha、底部居中随导航栏动态定位、清除时整栏消失联动。

## 3.4 移植方案
1. **点击链路可完全复用，无需新后端**：OS3 已有 `NSSL.setClearNotificationListener → clearNotifications(0,z)`，把新浮动按钮的 click 直接转接到这条既有链路即可（OS5 浮动按钮走的也是同一个 `clearNotifications`）。
2. **观感复刻（自绘）**：在 OS3 上由模块 inflate 一枚 `ImageView`（圆底 + 阴影 + 图标），挂到 `NotificationStackScrollLayout`/面板根，按 OS5 数值定位（46dp、marginBottom 40/62/90dp、gravity center|bottom），并照抄 `startClearAllButton*` 的 alpha/scale 动画参数。触发点用 `setClearAllButton` 时机或 NSSL expand/collapse 回调控制显隐。
   - **图标资源缺口**：`vivo_ic_notification_clear_all`、色 `vivo_notification_clear_all_shadow`、dimen `vivo_notification_clear_all_*` 在 OS3 SystemUI 可能不存在——需把图标 **随模块打包**（`module/res/`），不要依赖目标包资源 id。`[需真机确认项]`
3. **隐藏 OS3 内联文字按钮**：hook `NotificationShelf.setDismissVisible` 令 `mDismissAllNotification` 恒 GONE，向 OS5 的"shelf 内联隐藏 + 浮动按钮"形态对齐。`(OS3-NotificationShelf.java:97-98)`
4. **整栏消失联动**：hook 清除完成（clearNotifications 结束 / shelf 变空）后对 `NotificationShelf` 施加 2.4-(3) 的 250ms 消失动画，弥补 OS3 无 `updateShelfWhenDismissAll`。
5. **关键风险：groupKey NPE 崩溃循环（既有坑，勿回退）**：
   - 一键清除 = `NMS.dismissAll`/批量移除；`GroupMembershipManager`、`HeadsUpCoordinator` 会用 `SBN.getGroupKey()` 查表。**把 getGroupKey 抹成 null 会在批量移除时 NPE 崩溃循环。**
   - 移植只做"显隐/外观/动画 + 复用既有 clearNotifications 链路"，**不得** 为拆组而 `setResult(null)` getGroupKey（返回 String 且被消费）。拆组只改 `isGroup()` 并在 ShadeListBuilder 侧摘 summary。
     `(skill §4；module/.../NotifyRowFeature.java:601 注释)`
   - 回归必过 `work/os3-smoke.sh` 的"一键清除式批量移除回归"步骤。
6. 风险与回归面：浮动物理按钮会盖住列表底部行，需与 `notification_shelf_height=34dp`、列表 padding 协调；导航栏/折叠分支在 Z8（非折叠、全面屏手势）可简化，只保留非导航栏分支(40dp) + 有导航栏(62dp)。

---

# 四、开放问题 / 需真机确认

以下均无法从本机静态素材定论，**不猜测**：

1. **OS3 上 `vivo_ic_notification_clear_all` 图标、`vivo_notification_clear_all_*` dimen、`vivo_notification_clear_all_shadow` 色是否存在**：仅静态比对了类名/资源 id，未在 OS3 framework-res/SystemUI 资源表内逐一核验；决定移植要自带多少资源。（`G:/os3-ref` 只抽了类清单，未做资源 dump 对照）
2. **OS3 主题 fillet 档位能否被 `VivoColorAndCornerPicker.getNotificationDefFillet` 读到真实值**：OS3 有该类，但其档位数据源 `ThemeIconManagerProxy.getSystemFillet()` 在 OS3 是否被填充、默认值多少，未知。
3. **OS3 shelf 顶角"直角"是否为纯系统态**：静态只看到 `setTopRoundness(0)`；真机上是否有别的源（StackScroll 滚动）把 top 又抬起来，需 `notify_verify` 度量确认（本轮不连设备）。
4. **originui `G2CornerUtil/VRoundedCornerDrawable` 能否在 OS3 运行时注入**：两 APK 均无此类，静态判"不可用"；但若存在独立的 originui 共享库 APK 未纳入本次素材，则结论可能翻。素材内无该库，故按"OS3 拿不到连续角"处理。
5. **OS5 一键清除的 `getRowsToDismissInBackend` 是否对 group summary 做批量 key 展开**：静态确认走 legacy 链路，但分组批删时是否触达 groupKey 查表的确切调用点，未逐帧追（属既有 NPE 坑的边界，需真机批量移除时抓栈确认无 com.betteroriginos 帧）。
6. **`shouldShowClearAll()` 在 OS5/OS3 的判据差异**：两版都用它控显隐，但内部条件（ongoing/不可清除计数）是否一致，未展开该方法体比对。

---

## 附：本轮新增取证产物索引（均在 work/tmp-re-os5toos3/）
- OS5：ExpandableOutlineView.java、Roundable.java、G2CornerUtil.java、VRoundedCornerDrawable.java、VivoColorAndCornerPicker.java、OS5-NotificationShelf.java、OS5-NotificationClearAllImageView.java
- OS3：OS3-NotificationShelf.java、OS3-NotificationBackgroundView.java、OS3-ActivatableNotificationView.java
- 复用既有：work/systemui/out-src/{NotificationBackgroundView, ActivatableNotificationView, ExpandableNotificationRow, NotificationChildrenContainer, NotificationStackScrollLayout}.java、work/systemui/{shelf_block,bg_block}.txt

---

# 五、主线复核（对代理结论的更正，逐条附证据）

## 5.1 更正一：**G2 连续角在 OS3 是可移植的**，本报告 §1.3 的"不可移植"结论不成立
- `VRoundedCornerDrawable` 构造里 `this.mDrawG2RoundRectRadii = false`（默认走 Path 分支），
  `draw()` 中 `if (!mDrawG2RoundRectRadii) canvas.drawPath(mSolidPathCache.getG2RoundConerPath(...), mSolidPaint);
  else G2CornerUtil.canvasDrawG2RoundRectRadii(...)`
  `(work/tmp-re-os5toos3/com.originui.core.utils.VRoundedCornerDrawable.java:64, 138-146)`
  ⇒ 原生 `Canvas.drawG2RoundRect` 只是被显式打开时的**加速分支**，默认渲染路径是纯 Java `Path`，
  A13 完全能用同样的 `drawPath` 复刻同一几何。
- 两版 G2 公式**逐字节一致**：A15 `ANGLE_COS_25 = Math.cos(0.4363323129985824d)`、`ANGLE_SIN_25 = Math.sin(0.4363…)`
  `(work/tmp-re-os5toos3/com.originui.core.utils.G2CornerUtil.java:14-15)`
  与仓库里已有的 `com.vivo.common.utils.G2CornerUtil.getG2RoundConerPath(...)`
  `(vivo-OriginUI/ref/framework-src/sources/com/vivo/common/utils/G2CornerUtil.java:150-195)` 相同：
  `newRadius=1.3r`、`arcTo(...,205°,40°/295°,25°,115°)`、`cubicTo` 控制点 `0.55r` 与 `0.01r`、
  `2.6r > min(w,h)` 时回落普通圆角。
- 半径→G2 的换算在本 flavor 是**恒等**：`getG2Radius(f,·) = (int) f`、`getDefaultRadiusByG2(f,·) = (int) f`
  `(同 G2CornerUtil.java:26-32)`；且从角半径数组还原 G2 时取 **四角最大值** 作为单一半径、
  逐角开关 = `radii[i] > 0`：`getG2RoundConerPath(null,l,t,r,b, getDefaultRadiusByG2(max(fArr[0],fArr[2],fArr[4],fArr[6]),1f), fArr[0]>0, fArr[2]>0, fArr[4]>0, fArr[6]>0)`
  `(同 299)` ⇒ **移植配方可以沿用原生已算好的方圆分布，只替换"画成什么形状"**，不需要我们重新裁决哪一角该圆。

## 5.2 更正二：OS3 的圆角**没有主题档位通道**
- A13 的 `VivoColorAndCornerPicker` 里**没有** `getNotificationDefFillet(...)`（只有 `supportColorAndCornerPicker()`
  与 `mFillet/mFilletLevel` 字段读取）`(work/tmp-re-os5toos3/OS3-VivoColorAndCornerPicker.java:61,85-86)`；
  A15 版则有 `(work/tmp-re-os5toos3/com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker.java:194-206)`。
  ⇒ OS3 侧"目标半径"必须由我们给常量，**不能**指望读系统档位得到 OS5 值（§四-2 的开放问题就此关闭：走我们自己的档位表）。

## 5.3 开放问题 #1 已静态回答：一键清除的资源 A13 全缺，必须随模块打包
`aapt2 dump resources` 对照（`work/systemui/SystemUI.apk` vs `G:/os3-ref/OS3-SystemUI.apk`）：

| 资源 | A15 | A13 |
|---|---|---|
| `dimen/vivo_notification_clear_all_size` | 46.0dp | **不存在** |
| `dimen/vivo_notification_clear_all_padding` | 2.0dp | **不存在** |
| `drawable/vivo_ic_notification_clear_all` | XML 矢量 | **不存在** |
| `color/vivo_notification_clear_all_shadow` | `#1aeeeeee` | **不存在** |
| `dimen/vivo_notification_paddding_between_elements` | 7.0dp | **不存在** |

⇒ 图标本体已从 OS5 apktool 树取到，是自包含矢量（42dp：`#eeeeee` 圆 fillAlpha 0.9 + `#333333` 叉）
`(work/systemui/res/res/drawable/vivo_ic_notification_clear_all.xml)` ⇒ 原样打进 `module/res/drawable/`，不依赖目标包 id。

## 5.4 归档日志里的一次误读已排除
`work/z8os3-live/light-smoke.log` 里 900 条 `radius=88` 是 vivo `_V_InsetsStateController` 的挖孔半径日志，
与本模块无关（不是我们的度量行）；OS3 运行时圆角真值仍需明早 `notify_verify` 度量，别拿这条当证据。

---

## 5.5 更正三（最关键）：**A13 也有 `ExpandableOutlineView` 的半径通道与自有路径**，§1.2/§1.3 的"OS3 走旧 API、拿不到连续角"低估了可移植面
`(work/tmp-re-os5toos3/OS3-ExpandableOutlineView.java)`
- `initDimens()` 里 A13 **同样**先问 `VivoColorAndCornerPicker.supportColorAndCornerPicker()`，
  用 `getFillet()` 赋给 `mOutlineRadius`，`fillet == -1` 时回落 `R.dimen.notification_shadow_radius`
  `(同 247-257)`。⇒ A13 与 A15 的差别不是"有没有档位通道"，而是 **A13 直接用主题原始 fillet（px），
  A15 用 `getNotificationDefFillet(ctx, level)` 的档位表**（12/18/24dp）。
  这正好解释了既有实测：OS3 DSU 上 shelf 半径 45px@3.0=15dp 来自主题 fillet，而非 A13 的 17dp dimen。
- 半径消费：`getCurrentBackgroundRadiusTop() = getCurrentTopRoundness() * mOutlineRadius`、
  bottom 同理 `(同 296, 308)` ⇒ **系数×基准半径的模型两版一致**，分组"只有组顶/组底圆"的策略不用重写。
- **行形状在 A13 是自己画的 Path**：`getOutlinePath()` 用 `getRoundedRectPath(l,t,r,b, topR, bottomR, path)`，
  其实现是 `mTmpCornerRadii[0..3]=topR、[4..7]=bottomR; path.addRoundRect(...)` `(同 129, 133-144)`；
  裁剪路径 `mClipPath` 走同一个方法 `(193)`。
  ⇒ 移植点收敛成两处，且都在同一个类里：
  1) **基准半径**：把 `mOutlineRadius` 从"主题原始 fillet"改成 **A15 的档位表值**（A13 也拿得到 level）；
  2) **形状**：把 `getRoundedRectPath` 的 `addRoundRect` 换成 vivo 的 **G2 `Path`**（入参完全相同：四角半径），
     即可在 A13 得到与 OS5 同样的连续角轮廓，不依赖原生 `Canvas.drawG2RoundRect`。
- 因此 §1.3 表里"OS3=普通圆角矩形 / 关键不可移植项=squircle"两行**作废**，正确表述是：
  **squircle 可移植（Path 分支）；不可移植的是逐角毛玻璃 `VivoNotificationBlurController.setMaterialCornerRadius`
  与 HWUI 原生 `drawG2RoundRect` 加速分支**——前者 OS3 无该类，后者只是性能路径、不影响形状。

# 六、移植规格常量表（实现直接引用；每条带来源）

| 常量 | 值 | 来源 |
|---|---|---|
| `OS5_RADIUS_BASE` | 18dp（= `notification_shadow_radius`@A15） | `work/systemui/res/res/values/dimens.xml:1897` |
| 档位→半径 | 0→12dp，1→18dp，2→24dp，3→24dp，其它→18dp | `VivoColorAndCornerPicker.java:194-206` |
| `OS5_RADIUS_SMALL`（组内非首尾） | 0dp ⇒ 直角 | `dimens.xml:1841` + `ExpandableNotificationRow.java:3924-3931` |
| `OS5_SHELF_GAP` | 7dp（`vivo_notification_paddding_between_elements`） | `dimens.xml:3393` |
| `SHELF_HEIGHT` | 34dp（两版同源） | `dimens.xml:1902` |
| `CLEAR_SIZE / CLEAR_PAD` | 46dp / 2dp | `dimens.xml:3380-3381` |
| `CLEAR_MARGIN_BOTTOM` | 40dp（无导航栏）/ 62dp（有导航栏）/ 90dp（导航栏+流体窗） | `OS5-NotificationClearAllImageView.java:207-250`、`dimens.xml:3375-3377` |
| `CLEAR_GRAVITY` | 竖屏 center\|bottom；横屏/折叠 right | 同上 |
| `CLEAR_SHADOW` | `#1aeeeeee` + `BlurMaskFilter(45f, OUTER)`，画 `drawCircle(size/2,size/2,size/2-padding)` | color 资源 + `OS5-NotificationClearAllImageView.java:252-265` |
| `CLEAR_PRESS` | scale 1.0→0.9，200ms，`PathInterpolator(0.25,0.45,0.3,1)` | 同 134-189 |
| `CLEAR_APPEAR` | alpha 淡入/淡出 150ms | 同 60-123 |
| `SHELF_DISAPPEAR` | alpha 1→0 + scale→0，250ms，`PathInterpolator(0.33,0,0.67,1)`，结束 INVISIBLE | `OS5-NotificationShelf.java:940-962` |
| G2 公式 | `r'=1.3r`；25° 起 40° 扫过；控制点 `0.55r`、`0.01r`；`2.6r>min(w,h)` 回落普通圆角 | `com.originui.core.utils.G2CornerUtil.java:14-15,144-195`（与仓库 `com.vivo.common.utils` 版一致） |
| 逐角策略 | 不改原生分布：取 `mCornerRadii` 四角 max 作半径，`radii[i]>0` 决定该角是否圆 | `G2CornerUtil.java:299` |
| A13 可用写入点 | `NotificationBackgroundView.setRadius(float,float)`（签名两版一致）、`mCornerRadii`、行级 `setTopRoundness/setBottomRoundness` | `OS3-NotificationBackgroundView.java:221-252`、`OS3-ActivatableNotificationView.java:527-533` |
| A13 禁反射 | `Roundable/RoundableState/SourceType`、`mRoundnessManager`、`mHostLayout`、`mSidePaddings/mCornerRadius` 均不存在 | `os3-systemui-classes.txt` 0 命中、`NotificationChildrenContainer.java` 0 命中 |


# 七、主线二次审计：报告里两处需要修正/补强（逐字对源码）

1. **§2.2 的"仅 OS5"表述要收窄**：`VivoNotificationIconContainer` 与 `NotificationRoundnessManager`
   这两个**类在 A13 也存在**（`os3-systemui-classes.txt` 命中 1 / 2 次）；
   A15 独有的是 **shelf 上的字段 `mRoundnessManager`/`mHostLayout`**（类存在但 A13 的 shelf 不用它）。
   真正 A13 缺失的是 `RoundableState`(0 vs 4) 与 `VRoundedCornerDrawable`(0 vs 1) —— 与 §1 的结论一致。
   ⇒ 收纳栏的图标容器**不是"不可移植项"**，只是 A13 走 `mShelfIcons + mCollapsedIcons` 的旧结构。

2. **补上 §四-6 的开放问题（shouldShowClearAll 判据）并据此改实现**：
   - A15 `shouldShowClearAll() = mController.hasClearableNotifications()
     && !StateUtils.isKeyguardShowingWithoutShade()`（`out-src/NotificationStackScrollLayout.java:6033-6035`）；
   - `updateShelf()` 先 `setDismissVisible(shouldShowClearAll())` 再 `setClearAllButtonVisibility()`（同 6037-6044）；
   - 浮动按钮最终显隐还要 **`&& mIsExpanded && !isQsFullScreen() && !isKeyguardShowing()`**（同 6113）。
   - A13 侧对照：`shouldShowClearAll`/`onExpandedChanged`/`getExpansionFraction`/`hasClearableNotifications` 都存在，
     但 **`setClearAllButtonVisibility` 为 0**（没有浮动按钮，自然没有这段）
     （计数对照 `grep -c`：`os3-dexdump-f.txt` vs `classes5.dump` = 2/8、10/10、5/17、1/6、0/7）。
   ⇒ 移植实现原来只挂在 `setDismissVisible` 上，缺了"面板必须展开"这一条 ⇒ 已补：
   记 `sDismissAllowed` + `sShelfRef`，并**同时钩 `NSSL.onExpandedChanged`** 重算，
   判据 = `原生允许 && getExpansionFraction() > 0.5`（读不到分数时放行，避免按钮永不出现）。
   这条也让 A13 的浮动按钮行为与 OS5 一致：收起面板时不会浮在锁屏/ QS 上。

## 6. 装机前静态复核（02:43，`work/os3-probe.py` 对照 214MB A13 dexdump）

真机门禁跑不了，就把"钩子名/方法名对不对"这件事在离线做一次到底。把 `Os5Mirror.java` 也加进
`os3-probe.py` 的 FILES 后重新核对（产物：`out/OS3-HOOK-REACH-1103.md`），结论分三块：

**6.1 全部命中的**（原来的担心可以撤）：`ExpandableOutlineView` 的 `initDimens / onSystemFilletChanged /
getRoundedRectPath` 与字段 `mOutlineRadius`、`NotificationBackgroundView` 的 `onDraw / setRadius /
mCornerRadii / mBackground / updateBackgroundRadii`、`NotificationShelf` 的 `bind / onFinishInflate /
setDismissVisible / mDismissAllNotification / mPaddingBetweenElements`、
`VivoColorAndCornerPicker.getFilletLevel` —— A13 上都在。

**6.2 抓到两处会在 A13 上静默出错的问题（已修）**：
1. **`NSSL.onExpandedChanged` 在 A13 不存在**（A13 只有 `setIsExpanded / getIsExpanded / isExpanded /
   onGroupExpandChanged`）。原实现只钩这一个名字 ⇒ A13 上命中 0，浮动"一键清除"不会随展开/收起重算。
   现在：先钩 `onExpandedChanged`，命中数为 0 就退到 `setIsExpanded`，并把实际钩到的名字记进 `install groups=` 日志。
2. **`NSSL.getExpansionFraction` 在 A13 不存在**（同名的在 `PanelViewController` / `AmbientState` 上），
   而 `isShadeExpanded()` 失败时**fail-open 返回"已展开"** ⇒ 收起态/锁屏上按钮照样出现。
   现在：`getExpansionFraction` → 退到 `isExpanded()` → 都失败才放行，并把来源打进
   `clear-all attached ... shade=fraction|isExpanded|unavailable|nssl-missing`，
   验证器断言 shade 必须是前两者之一。
3. 顺手补上 OS5 同款闸门里缺的另一条：**锁屏时不出现浮动按钮**，用公开 API
   `KeyguardManager.isKeyguardLocked()`（不反射 vivo 的 KeyguardStateController —— 两版类名差异大，
   反射失败等于闸门不存在）。

**6.3 一条可以撤掉的担心**：源码里 `Dependency` 的 FQN 是**两个候选依次试**
（`com.android.systemui.Dependency` 与 A15 的 `com.android.systemui.dependencies.Dependency`），
报告里"`dependencies.Dependency` 缺失"只是因为 A13 用的是前一个名字，不是 bug。

⇒ 复查后重新构建（`sha256 cf7d350d6105`），9 项离线检查全绿。

## 7. 更正（03:12）：G2 的常数我抄漏了"两套"，回落门限也不是 2.6r

代码审查代理回来看见 `work/tmp-re-os5toos3/com.originui.core.utils.G2CornerUtil.java:148-264`，
逐条比对我的 `Os5Geometry.g2Path`，我复核后确认它说的是对的：

| 项 | 我原来的实现 | vivo 实际（已改） |
|---|---|---|
| 退普通圆角的门限 | `min(w,h) <= 2.6r` | `2.2r > w 或 2.2r > h` |
| `min>2.6r` 时的常数 | `-0.013r? 没有` 0.01r / 0.55r / 1.3r | `-0.013r / 0.61r / 1.3r` |
| `2.2r~2.6r` 带 | 直接退普通圆角 | **仍画 G2**，用 `-0.008r / 0.4r / 1.1r` |
| 第一个控制点偏移符号 | `+0.01r`（正值） | 负值（`-0.008r / -0.013r`） |
| 回落分支的形状 | `Path.addRoundRect(圆角数组)` | vivo 自己的 `getNormalCornerPath`：先把 r 夹到 `min/2`，再逐角 `arcTo(90°)` |

影响：**收起态通知行的高度经常落在 2.2r~2.6r 之间**（例如 r=54px、行高 133px），
按旧实现那一条会整条退回普通圆角 ⇒ "OS5 连续角"在这类行上静默失效，
而它在真机上只表现为"圆角看着不太对"，正是本项目禁止用眼睛判断的那类问题。

顺带发现自己转写时把右上第二个立方贝塞尔的 `(r-a)+cNeg` 写成了 `(r-a)-cNeg`（符号错）。

`work/g2_path_selftest.py` 的独立参考按上面这张表重抄，
用例补了 `2.2r~2.6r` 带、瘦高+对角圆角，`--negative` 除了挪 `1.1r/1.3r` 还**翻转 `c_neg` 符号**
（旧的反对照只挪量级，抓不住符号错——这就是它当初没能挡住我的原因）。

## 8. 圆角通道的写入者枚举 + 收纳栏"自噬回路"（03:57）

**写入者枚举**（`work/tmp-re-os5toos3/OS3-ExpandableOutlineView.java`）：`mOutlineRadius` 只被三处改写：

| 位置 | 触发 | 我们是否拦住 |
|---|---|---|
| `initDimens()` :251-256 | 构造/主题/密度字体变更 | ✅ 直接钩该方法 |
| `onSystemFilletChanged()` :355-364 | 主题圆角档位回调 | ✅ 钩后重算并失效档位缓存 |
| `onDensityOrFontScaleChanged()` :350 | 内部再调 `initDimens()` + `applyRoundness()` | ✅ 虚调用同样命中方法钩子 |

⇒ 结论：A13 上**没有**绕过我们的第四个复位点，半径通道可以放心只在两处写。

**收纳栏自噬回路（本轮新修）**：`NotificationShelf.onConfigurationChanged`（OS3:195-203）
最后一行 `setDismissVisible(mDismissAllNotification.getVisibility() == 0)` 会把可见性读回成布尔值。
我们把内联文字按钮按 `GONE` ⇒ 配置变更后系统传来**自己造的 false**，
`sDismissAllowed=false`，浮动一键清除从此消失。
修法：`sInConfigChange` 标记覆盖那次调用，期间不采信参数；结束后用 `sDismissAllowed` 重算 + 重补 7dp。
**通用教训**：凡"我们改了某个视图/字段的状态，而系统会把它读回去当输入"，
必须区分"系统真实意图"与"我们造成的表象"，否则会出现只在转屏/改字号后才暴露的静默失效。

---

## 9. 一键清除的点击链与"内联按钮是承重墙"（12:24，代码层取证 + 三处代码修正）

### 9.1 取证方法上的坑：`dexdump -f` 根本不包含调用点

`G:/os3-ref/os3-dexdump-f.txt` 是 `-f`（只出 header/字段/方法名，**不反汇编 code**）。
所以前几轮"全库搜 `setClearNotificationListener` 只有 1 条"只能证明**声明存在**，
不能证明"没人调用"。本轮改用完整反汇编（5 秒、134MB，产物 `work/tmp-re-os5toos3/os3-code-classes2.txt`）：

```
./work/sdk-dl/bt35/android-15/dexdump.exe -d -n G:/os3-ref/dex/classes2.dex > os3-code-classes2.txt
```

`-d` 反汇编、`-n` 丢调试信息。**调用点只有一条**，链路因此完全确定：

```
NSSL.setShelfController(NotificationShelfController)          // 41d6dc，启动时装配一次
  ├─ removeView(旧 shelf) → controller.getView() → addView
  ├─ AmbientState.setShelf / StackStateAnimator.setShelf / controller.bind(...)
  └─ NSSL.setShelfListener()                                  // 41d750
        new -$$Lambda$NSSL$PWFM8L-...  → shelf.setClearNotificationListener(that)   // 41d772
              └─ mDismissAllNotification.setOnClickListener(...)   // OS3-NotificationShelf.java:108-113
NSSL.lambda$setShelfListener$9(View)                          // 41b0b8，真正的"一键清除"
  ├─ mClearAllNotisDismissListener.onDismiss()
  ├─ z = !controller.hasUnClearableNotifications(0)
  ├─ clearNotifications(0, z)
  └─ if (mClearAllInProgress) shelf.updateShelfWhenDismissAll(z)   // 41b0fa
```

⇒ 我们把浮动按钮转接成 `mDismissAllNotification.performClick()` 是**对的**：
`View.performClick()` 不检查可见性，GONE 也照样跑原生链，批量移除完全走系统自己的路。
但监听**只在 `setShelfController` 里挂一次**，挂没挂我们平时看不见 ⇒ 新增见证 `inlineListener=`（§9.4）。

### 9.2 被推翻的实现：我们自己又播了一遍"整栏消失"

A13 的 `updateShelfWhenDismissAll` → `animateShelfDisappear` 与 OS5 **逐参数相同**
（`animate().cancel()`；`getVisibility()==0` 才播；`alpha 1→0`、`scale z?0:1`、250ms、
`PathInterpolator(0.33,0,0.67,1)`；结束 `setVisibility(4)`）。
`(OS3-NotificationShelf.java:797-817 vs OS5-NotificationShelf.java:940-962)`

而它带两道门，我们的 `sweepShelf()` 一道都没有：

| 门 | 语义 | 我们补播的后果 |
|---|---|---|
| `if (mClearAllInProgress)` | 只有"确实是全部清除"这一动作才联动 | 单条滑动清除等场景被我们误当成整栏清除 |
| `if (mLastHideState \|\| z)`，`z = !hasUnClearableNotifications(0)` | **还剩不可清除通知时，系统故意让栏留着** | 把还挂着 ongoing 通知的收纳栏凭空抹掉 |

这台机上常驻 LSPosed 的 `ONGOING_EVENT` 通知（`dumpsys notification` 实测 12 条记录里
多条带 `ONGOING_EVENT`），所以 `z=false` 才是常态 —— 补播的后果不是"动画快了一点"，
是**通知还在、收纳栏没了**。已经删掉 `sweepShelf()` 与 `SHELF_SWEEP_MS`，点击只转接。

顺带：`animateShelfDisappear` 之后系统靠 `updateAppearance()` 收尾时
`animate().cancel(); setAlpha(1); setScaleX(1); setScaleY(1)`（dexdump `387136-38714b`）自己还原形状，
我们那个"endAction 里把 alpha 复位"也是多余的。

### 9.3 真正的版本差异：内联文字按钮在 A13 上是**承重墙**

`updateAppearance()` 的收尾（jadx 因为 701 条指令跳过该方法，本轮从字节码读）：

```
3870f6: iget-boolean v1, viewState.hidden        // z 的来源
38712a: iput-boolean v4, mLastHideState
38714e: invoke-direct changeShelfVisibility(v4)
```

```java
private void changeShelfVisibility(boolean z) {              // 两版逐字相同
    if ((z && mDismissAllNotification.getVisibility() == 8)  // ← 关键
        || CentralSurfaces.isFullyCollapsed()
        || getPanelController().isQSFullyExpanded()) setVisibility(4);
    else setVisibility(0);
}
```

两版唯一差异在 `setDismissVisible`：

| | OS5 (A15) | OS3 (A13) |
|---|---|---|
| 内联按钮可见性 | `setVisibility(8)` **无条件 GONE** `(:119-128)` | `setVisibility(z ? 0 : 8)` `(:97-106)` |

⇒ 结论：**A13 的收纳栏被这枚内联文字按钮顶住不肯收**（`z=true` 也照样 `VISIBLE`）；
OS5 因为按钮恒为 GONE，`z` 单独决定显隐。所以我们把内联按 OS5 写成 `GONE`
**不只是去装饰，而是把"该收就收"的收纳行为一起移植过来**。
副作用与 OS5 一致：`setDismissVisible(true)` 里 `mShelfIcons` 的右内边距 = `gap + 按钮宽度`，
GONE 之后宽度 0 ⇒ 图标行拿到几乎整幅宽度，正是 OS5 的排布。

### 9.4 本轮落地的三处修正

1. **点击只转接**：删 `sweepShelf()`/`SHELF_SWEEP_MS`；新增见证
   `os5_mirror: clear-all tap inlineListener=<0|1> inlineVis=<V> shelfVis=<V>`。
   `inlineListener=0` = 原生链根本没挂上，这一击是静默空转，必须看得见。
2. **原生已有就不补挂**：OS5 的圆钮是 `com.vivo.systemui.statusbar.ui.NotificationClearAllImageView`
   （A15 三个 dex 命中 6、由 `status_bar_expanded.xml` inflate；**A13 三个 dex 全 0**）。
   `onShelfDismissVisible` 先用 `findClass` 探测（结果缓存在 `sNativeClearAll`），
   命中就整段跳过。用**能力**而不是版本号关门：ROM 哪天补上这个类，模块自动让位，
   不会在 OS5 上叠出第二枚按钮。日志 `os5_mirror: clear-all native=<true|false>`。
3. **坐标进日志**：`os5_mirror: clear-all at x=.. y=.. w=.. h=..`（`onSizeChanged` +
   显隐变化两处打，取最后一条）。脚本按中心 `input tap`，没点到就没有 tap 见证行 ⇒ 验收器直接红，
   不会再犯"眼估坐标"那类错。

### 9.5 验收器与冒烟脚本同步

- `work/verify_os5_mirror.py`：新增 `RE_TAP`/`RE_NATIVE` 与三条断言
  （`inlineListener` 必须 1、`inlineVis` 必须 8、`native=true` 时不得有补挂日志），
  "点击见证"只在**我们自己挂了按钮**时才要求；正例新增 `GOOD_OS5`（原生圆钮、无 attached/tap）——
  **OS5 上的日志形状与 A13 不同，这是有意的**，两套正例都要绿。反例 +4（没监听/没藏/缺见证/重复补挂），
  现在 21 条反例全部会红。
- `work/os3-smoke-os5mirror.sh`：新增第 4 步"一键清除点击链路"，
  坐标取自日志，点击后用 `dumpsys notification` 里 `NotificationRecord(` 的**可清除条数**
  （排除 `ONGOING_EVENT|NO_CLEAR|NO_DISMISS`）证明批量移除真的发生了；
  验证器挪到点击之后（否则"还没点"会被当成失败）。`set -u` 下坐标解析先兜 0 再判，
  避免算术报错把脚本中断在收尾之前。

### 9.6 又抓到一处只能静态发现的缺陷：浮动按钮的**宿主选错层**

用 `aapt2 dump xmltree` 直接读两版的布局（不是猜的）：

```
OS5 work/systemui/SystemUI.apk  res/layout/status_bar_expanded.xml
  NotificationPanelView (…)
    ├─ NotificationsQuickSettingsContainer (line=62)   ← NSSL 在这层里面
    └─ com.vivo.systemui.statusbar.ui.NotificationClearAllImageView (line=199)
          layout_gravity=0x51(BOTTOM|CENTER_HORIZONTAL) · layout_width/height=-2(wrap)
          layout_marginBottom=40.0dp · alpha=0 · contentDescription=@0x7f140a92
A13 G:/os3-ref/OS3-SystemUI.apk res/layout/status_bar_expanded.xml
  com.android.systemui.statusbar.phone.NotificationPanelView (line=21)
    └─ NotificationsQuickSettingsContainer (line=80)
         ├─ Guideline(122) · NotificationStackScrollLayout(130) · Button(151) · …
```

⇒ OS5 的原生圆钮是 **`NotificationPanelView` 的直接子级**，而旧的 `hostFor()` 取的是
`NSSL.parent`，也就是 `NotificationsQuickSettingsContainer`。A13 反编译确认它是
**`extends ConstraintLayout`**（`A13-NotificationsQuickSettingsContainer.java:23`），
`NotificationPanelView extends PanelView extends FrameLayout`（`A13-PanelView.java:14`）。
往 ConstraintLayout 上 `addView(..., FrameLayout.LayoutParams(gravity=BOTTOM|CENTER_HORIZONTAL))`
会被 `generateLayoutParams` 换成 Constraint 参数：**gravity 被丢**，未约束子视图落在左上角，
且不会有任何报错 —— 装机当天只会看到"按钮跑到状态栏下面去了"。

修法：`hostFor` 改为**往上找第一个 `FrameLayout`**，A13 上正好是 `NotificationPanelView`，
与 OS5 同一层、同一套 gravity/margin 语义；`40dp/62dp` 的底边距也因此终于量在正确的参照系上
（挂在容器上时，那个容器本身的高度并不等于面板高度）。
挂载日志新增 `host=<类名>`，验证器要求它含 `Panel`，两条反例：
`host=NotificationsQuickSettingsContainer` 与"没有 host 字段"都会红。

顺带用同一份 dump 复核了规格：`marginBottom=40dp`、`layout_gravity=0x51(BOTTOM|CENTER_HORIZONTAL)`、
初始 `alpha=0` 与模块常量一致（`CLEAR_MARGIN_DP=40`、`CLEAR_MARGIN_NAV_DP=62`、`CLEAR_SIZE_DP=46`、
`CLEAR_PAD_DP=2`，取自 `vivo_notification_clear_all_*`；代码里 gravity 是内联的
`CENTER_HORIZONTAL|BOTTOM`，没有单独常量）。
OS5 布局用 `wrap_content`，我们写死 46dp 方框 —— 是否等价最终要看设备上的
`clear-all at w/h`，已列进 RUNBOOK 门禁。

### 9.7 底边距的**分支条件**也不对：OS5 问的是「是不是三键导航条」

> **【12:56 作废，见 §9.8】** 本节两处判断都错了：`isUsingNavigationBar()` 那条分支实际没被走到（调用方传的是非 null 的 CentralSurfaces），而 `config_navBarInteractionMode` 在这两台机上实测都是 0，vivo 的导航方式不读它。

`OS5-NotificationClearAllImageView.java:207-250` 用
`NavigationConfiguration.getInstance(ctx).isUsingNavigationBar()`（A15）/
`centralSurfaces.isNavigationBarVisible()` 来在三个 dimen 之间选：

```
aapt dump --values resources  work/systemui/SystemUI.apk     （实测值）
  dimen/vivo_notification_clear_all_margin_bottom                                = 40.0dp
  dimen/vivo_notification_clear_all_margin_bottom_with_navigationbar             = 62.0dp
  dimen/vivo_notification_clear_all_margin_bottom_with_navigationbar_with_flowview = 90.0dp
  dimen/vivo_notification_clear_all_size = 46.0dp · dimen/vivo_notification_clear_all_padding = 2.0dp
```

模块原先用 `config_showNavigationBar` 判，这在**全面屏手势**下也是 `true`，
于是会取 62dp，而 OS5 在这种情况下取 40dp ⇒ 按钮比 OS5 高 22dp。
改成优先读 `android:integer/config_navBarInteractionMode`（0=三键才用 62dp；1/2=滑动/全面势用 40dp），
读不到才退回 `config_showNavigationBar`，并把判定来源打进日志（`navMode=`），
验证器断言 `nav == (navMode==0)`（`navMode=-1` 表示没读到，跳过该断言但不静默通过尺寸之外的检查）。

顺带把 §5.3「A13 缺一键清除资源」用**第二条独立路径**复核了一遍：
`aapt dump --values resources G:/os3-ref/OS3-SystemUI.apk | grep -c vivo_notification_clear_all` = **0**
（此前是靠 jadx/dexdump 判的）。⇒ 这些数值只能随模块打包，不能指望在 A13 上按名读。

### 9.8 【§9.7 的结论作废】底边距真正的信号是「导航条此刻可见」

§9.7 说"改读 `config_navBarInteractionMode==0`"，**这条是错的，已推翻**。两条新证据：

1. 从设备 `framework-res.apk`（`adb exec-out cat /system/framework/framework-res.apk`，
   离线只读取证，不需要解锁）实测资源表：
   `android:integer/config_navBarInteractionMode` 在 **A15（Z8）与 A11（Y7s）都是 0**，
   而 Z8 的 `dumpsys window` 里明明有 `SideSlideGestureBar-Bottom`（手势在跑）——
   说明 vivo 的导航方式根本不走这个 AOSP 配置，拿它当判据等于猜。
2. **真正决定分支的是调用方传了什么**。A15 dexdump（`work/fw-res/a15-classes5-code.txt`）：
   ```
   |[2ae26c] NSSL.setClearAllButtonLocation:(Landroid/content/res/Configuration;)V
     iget-object v1, …NSSL;.mCentralSurfaces      // 非 null
     invoke-virtual {v0,v1,v2,v3}, NotificationClearAllImageView
         .setClearAllButtonLocation:(CentralSurfaces;VivoFlowDisplayView;Configuration;)V
   ```
   于是走 `OS5-NotificationClearAllImageView.java:214-215` 的
   `zIsUsingNavigationBar = centralSurfaces.isNavigationBarVisible()` 分支 —— 语义是
   "**导航条此刻可不可见**"，不是"三键还是手势"。全面屏手势下 hint bar 仍可见 ⇒ OS5 也取 **62dp**。
   也就是说：我原来用 `config_showNavigationBar`（本机 true）拿到的 62dp **本来就和 OS5 一致**，
   §9.7 担心的"高 22dp"并不存在。

模块据此改为：优先用标准窗口信号 `getInsets(Type.navigationBars())` 是否非零
（`android.graphics.Insets`，注意 `WindowInsets.getInsets(int)` 返回的是它而不是 `Rect`），
拿不到（还没 attach）才退回 `config_showNavigationBar`；日志字段从 `navMode=` 换成 `navSrc=insets|config`，
验证器断言来源必须是这两个值之一，并留一条"边距与可见性判定相反"的反例。

同一处还纠正了 §9.6 结尾的一个说法：OS5 的 XML 虽然写 `wrap_content`，但
`setClearAllButtonLocation` 会用 `vivo_notification_clear_all_size`（实测 46dp）**覆写**
`layoutParams.width/height`（:242-247），所以我们写死 46dp 不是"近似等价"，而是与系统最终值一致。

### 9.9 补两处：边距要跟着**重算**，以及一条新的离线取证通道

1. **OS5 的边距不是一次性决定的**：`NSSL.setClearAllButtonLocation` 也在
   `onConfigurationChanged` 路径上被调（A15 dexdump 调用点 `2ad99e` 与 `2ae1f4`），
   所以导航条由隐到显（视频全屏、临时隐藏等）时 40dp/62dp 会换档。
   模块据此在 `ensureAttached` 命中已存在的按钮时调用 `refreshMargin(...)`：
   只有 `navBarVisible` 与上次记录（`ClearAll#mNav`）**真的不同**才写回 `LayoutParams`，
   避免每次 shelf 事件都 requestLayout；变更时打 `clear-all margin re-armed nav=… navSrc=… marginBottom=…`。
2. **新的离线取证通道**：framework 侧的配置不必解锁设备、也不必上真机——
   `adb exec-out cat /system/framework/framework-res.apk` 直接拉下来用
   `aapt dump --values resources` 读。本轮就是这样发现
   `android:integer/config_navBarInteractionMode` 在 **A15(Z8)=0 与 A11(Y7s)=0**（`dumpsys window` 里
   却有 `SideSlideGestureBar-Bottom`），从而否掉了 §9.7 的判据。
   产物留在 `work/fw-res/{A15,A11}-framework-res.apk` 与 `work/fw-res/a15-classes5-code.txt`。
   **注意 `exec-out cat` 而不是 `adb pull` 走 su**：过 su 管道会给二进制添脏字节（DEVLOG 里记过）。

本轮构建 `sha256` 前缀 `20c23f9a757f`（`work/offline-all.sh` 15 项全绿）。

## 10. 第三轮代码审查（代理复核）抓到两处，其中一处是我上一轮刚写下的

审查对象：本轮 `ClearAll` 全部改动 + `verify_os5_mirror.py` 的 clear/tap/native 断言。
结论逐条与证据核对后：

**#1 浮钮的显隐只有一个事件源（真缺陷）**。原先只有 `setDismissVisible` 会驱动它。
一键清除把栏清空后，如果系统不再回调那个方法，钮就会留在空栏上——而 OS5 的钮可见性
本来就是 `NSSL.setClearAllButtonVisibility()` 自己重算的（A15 dexdump 里确实有这个方法，A13 没有）。
修法：加钩 `NotificationShelf.changeShelfVisibility(Z)V`（A13 里是 PRIVATE，
`hookAllMethods` 照样命中；`out/OS3-HOOK-REACH-1103.md` 复核为 ✅），
after 钩子里用**系统刚写下的 shelf 可见性**重算一次
（`sDismissAllowed && 面板展开 && 非锁屏 && shelf.getVisibility()==VISIBLE`）。
读的是系统的决定、我们也不再写 shelf 的可见性（补播动画已删），所以不构成 §8 那类自噬回路。

**#2 `getInsets(navigationBars()).bottom > 0` 量的是"留没留出高度"，不是"可见"**（我 21:03 写下的）。
手势 hint bar 常常可见但 inset 为 0 ⇒ 会取 40dp，而 OS5 在这种情形取 62dp，
正好在我自己标榜的那一档上分叉。改用 `WindowInsets.isVisible(Type.navigationBars())`，
这才是 `CentralSurfaces.isNavigationBarVisible()` 的等价物；
inset 高度降级为见证值 `navH=`，日志同时给 `navVis=`，
验证器新增断言 `nav == (navVis==1)`（只对可见性，不对高度），
并把"可见但 0 高"识别成**应当 62dp** 的正例见证。
反例补一条：`nav=false navVis=1 navH=0`（= 把高度当可见性用）必须红。

**#3 审查器提的验收器盲区（`native=` 行依赖 shelf 事件）**：把类存在性探测从
`onShelfDismissVisible` 提到**装钩子时**（`hookShelf` 里 `findClassIfExists` 一次，缓存），
所以 OS5 上"整段不补挂"也一定留有一条 `clear-all native=true` 证据，
不会出现"合法地什么都没打 ⇒ 被当成失败"。

顺带两个编译期事实（内部版 android.jar 与真机不同）：
`WindowInsets.getInsets(int)` 返回 **`android.graphics.Insets`**（不是 `Rect`，字段 `bottom/left/right`），
而 `View.isVisible()` 在这个 jar 里**根本没有** ⇒ 用 `getVisibility() == VISIBLE` 等价写法。

本轮构建 `sha256` 前缀 `6c11222fdca6`；`work/offline-all.sh` 15 项全绿，
`verify_os5_mirror.py` 2 个正例 + 25 个反例（漏网 0）。

## 11. 第五轮审查（半径/圆角/收纳栏几何这条面）：4 条成立并已修，2 条待办
证据都用 dexdump/反编译复核过，没有采信注释。

**已修**
1. **主题档位从来没读到过**（`Os5Mirror.level()`）。它用 `view/ctx.getClass().getClassLoader()`
   解析 `VivoColorAndCornerPicker`/`Dependency`，但传进来的是 **Context** ——
   `ContextImpl`/`ContextThemeWrapper` 由 BootClassLoader 加载，看不见 vivo 的类 ⇒ 两次 findClass 都抛，
   `sLevel` 停在 -1，半径恒等于兜底 18dp；而 `hookG2Path` 里的 `rescaleRadii(current, radiusPx…)`
   还会把这个 18dp **写回** `mOutlineRadius`，把用户真实档位（24dp 机型）压掉。
   唯一"读对"的路径是 verify 日志里那句 `level(view)` —— 也就是**只有开着度量日志时才会被纠正**。
   现在：`install(cl)` 记下 SystemUI 的 ClassLoader 存进 `sLoader`，`level()` 只用它。
   验证器同步：档位不在 `0/1/2/3` 里直接判红（原来是 `.get(level, 18)` 自吞），
   并补 `level=0 px=36.0` / `level=2 px=72.0` 两条正例 + 档位假绿反例。
2. **`applyRowRoundness` 调的方法在 A13 根本不存在**。A13 只有 `setTopRoundness(FF)Z`
   （dexdump 全库 `type : '(FZ)Z'`），我们传单个 float ⇒ `callMethod` 抛错被
   `catch (Throwable ignored)` 吞掉 ⇒ **这三行从来没执行过**。
   而注释说"A13 没有系统侧圆角传导"也不成立：A13 自己在按位置驱动 roundness
   （`ExpandableView.setTopRoundness` 5 处调用 + shelf/row 各 1 处），分组中间行的直角正是它算的。
   ⇒ 不是"把签名修对"，而是**删掉这个覆盖**（修对反而会做出视觉回归），只留 `setBackgroundRadius`。
3. **7dp 收纳栏间距在 A13 没有通道**。A15 用它算 shelf 位移（`OS5-NotificationShelf.java:341/351/357`
   `setYTranslation(pad + stackY)`）；A13 只在图标数（`OS3-NotificationShelf.java:484`）和
   "行是否已在栏内"的判定（:611）里读同一个字段，**A13 全 dex 里 `setYTranslation` 出现 0 次**。
   ⇒ 原来那次写入没补出间距，只是把两个判定的阈值挪了 21px。已停止写它，
   日志从 `shelf gap=21`（自己算的数，验证器永远绿）换成 **`readback=` 读回 `mTopRoundness`** +
   `gapPorted=0`；验证器现在断言 `readback≈1`，并有一条 `readback=0.000` 的反例。
4. **背景裁剪用的是一帧前的矩形**。`mBackground.getBounds()` 在 `onDraw` 内部才被 set，
   我们的 before-hook 读到的是上一帧 ⇒ 展开/收起、RTL、宽度过渡那几帧裁错。
   现在按 A13 自己的公式（`OS3-NotificationBackgroundView.java:65-92`）算本帧矩形，
   算不出来才退回旧 bounds，并把差异作为见证打出来（`stale=0|1`）。
   两个 API 现实：这台的内部版 jar 里 **没有 `View.isLayoutRtl()`**（用 `getLayoutDirection()`）；
   `WindowInsets.getInsets(int)` 返回 `android.graphics.Insets` 而不是 `Rect`。

**待办（已确认成立，尚未动代码）**
- `mirrorShelf`/`hookShelf` 的异常粒度与 `applied=N/total` 计数（NSSL 查找抛错会连带丢掉后面的注册与计数）。
- 验证器 `bg clip` 没断言 `lt/rt/rb/lb` 四角；`NotifyRowFeature:1119` 的 `setRadius` 后没 `invalidate`；
  `forceShelfDrawableCorners` 只拆 `LayerDrawable`，注释里说支持的 `RippleDrawable` 其实没处理。

**顺带被这一轮逼出来的自测升级**：反例集合里有条"篡改空炮"（改了个日志里已经不存在的 `shelf gap=21`），
`--selftest` 直接把它抓出来了；现在自测要求**每条反例必须真的改动材料**，否则算失败。
验证器现状 3 正例 + 28 反例、`offline-all` 18 项全绿，包 `b63f50b2c0b6`。

### 11.1 间距没有放弃：找到 A13 自己的通道后照样移植（22:10）
§11 第 3 条当时停在"A13 没有 7dp 通道 ⇒ 不写"。那是**半途结论**：
移植目标要求收纳栏渲染逻辑一致，间距不能因为"字段不同名"就丢。
真正的通道在 `OS3-NotificationShelf.java:254`（`updateAppearance` 内）：

```java
shelfState.yTranslation = MathUtils.min(viewState.yTranslation + viewState.height,
        (stackY + stackHeight) - shelfState.height);          // A13：少加 mPaddingBetweenElements
// A15 同一处：… + this.mPaddingBetweenElements（OS5-NotificationShelf.java:341/351/357）
```

⇒ 加钩 `NotificationShelf.updateAppearance()`（A13 是包级可见，`hookAllMethods` 命中；
`os3-probe` 复核已在 ✅ 列表里），after 里对 `getViewState().yTranslation` **补一次 gap**。
**幂等是必须的**：254 只在"栏里有可见背景子项"时重算，不重算的帧会保持我们上次写的值，
直接 `+=` 就是每帧累加。实现上记住每个 state 上次写出的值，命中就先减回基线再加
（`SHELF_Y_APPLIED` 用 WeakHashMap，state 对象被换掉就自动失效）。

见证与断言换成量"真实位移差"：`shelf gap yFrom=612.0 yTo=633.0 want=21`，
验证器要求 `yTo-yFrom == want == 7dp×density`，并配两条反例：
**累加成两倍**（612→654）和**根本没动**（612→612）都会红。
之前的 `gapPorted=0`（自证字段）连同它的反例一起删了——那条反例被"空炮守卫"当场抓出来
（它篡改的文本已经不存在），说明这个守卫本身也在起作用。

§11 的待办也已清掉：`bg clip` 断言四角标志（四角全 false 会红）、
`setRadius` 后补 `invalidate`、未识别的 drawable 类型打 `shelf drawable unhandled cls=` 见证、
`hookShelf` 里 NSSL 段独立 try 且 `shelf.setDismissVisible` 先记账（抛错不再连带丢注册数）。

验证器现状 3 正例 + 28 反例（漏网 0），`offline-all` 18 项全绿，包 `f24325c2fb15`。

## 12. 第六轮：浮动清除钮的**判据**终于对到 OS5 那一行代码上（22:20–22:34）

### 12.1 取证：OS5 到底按什么条件画这枚钮
`work/systemui/out-src/NotificationStackScrollLayout.java`（A15/vivo OS5 全量 jadx）：

| 位置 | 代码 | 含义 |
|---|---|---|
| :6112-6116 | `setClearAllButtonVisibility()`：`z = shouldShowClearAll() && mIsExpanded && !isQsFullScreen() && !mCentralSurfaces.isKeyguardShowing()`，再 `setVisibility(z?0:4)` | **浮动圆钮**的完整判据（四项与） |
| :6033 | `shouldShowClearAll()` = `mController.hasClearableNotifications() && !StateUtils.isKeyguardShowingWithoutShade()` | 可清除性 + 锁屏语义（注意：**不含**"面板已展开"） |
| :6037-6043 | `updateShelf()` → `mShelf.setDismissVisible(shouldShowClearAll())` + `setClearAllButtonVisibility()` | 收纳栏内联文字与浮动钮是**两套判据**：内联不要 `mIsExpanded`，浮动钮要 |
| :4228-4233 | `setIsExpanded(z)`：`z2 = z != mIsExpanded`，只有 `z2` 才调 `setClearAllButtonVisibility()` | 重算点①，且**按边沿触发** |
| :4837-4844 | `setQsFullScreen(z)` 无条件调 `setClearAllButtonVisibility()` | 重算点② |
| :2004 | `setExpandedHeight(f)` 里 `setIsExpanded(f > 0.0f)` | `mIsExpanded` 的真义 = "有展开高度"，不是"展开过半" |

A13（`work/tmp-re-os3-media/sysui-full/sources/.../NotificationStackScrollLayout.java`）逐条对上：
`shouldShowClearAll()` :4943（多一个 `mClearAllEnabled` 与门）、`isExpanded()` :3435、
`isQsFullScreen()` :3491 / 写点 :4828、`setExpandedHeight` 里 `setIsExpanded(f > 0.0f)` :4508。
⇒ **两版语义一致**，A13 有全部四个判据的可读入口，移植不需要自造任何东西。

### 12.2 推翻我先前写下的两条（就地标注，不删原文）
1. **假命题：「A15 有 `NSSL.onExpandedChanged`，A13 没有」。**
   A15 与 A13 两份全量 jadx 里 `onExpandedChanged` **各 0 命中**（`grep -rln` 均空）。
   旧代码先钩它、命中 0 再退到 `setIsExpanded`，结果侥幸对，但把这条写进了注释、
   并且让 `work/os3-probe.py` 报告长期挂一条误导性的 ⚠️「NSSL 缺 onExpandedChanged」。
   现在钩子目标就是 A15 真实的重算点：`setIsExpanded` + `setQsFullScreen`。
2. **假命题：「A15 的 NSSL 有 `getExpansionFraction()`」。**
   两版都没有——同名方法在 `AmbientState` 上（A15 :1918/:1979/:2176 全是 `mAmbientState.getExpansionFraction()`）。
   我据此写的"展开分数 > 0.5"是**凭空加的阈值**；原生 `mIsExpanded` 的判据是 `f > 0`。
   现在先读 `isExpanded()`（原生那个值），`fraction` 只留作兜底路径。

### 12.3 少移植的一条闸门
`!isQsFullScreen()` 之前整条没实现：OS5 在下拉到底、快捷面板铺满时**不画**这枚钮，
我们会让它留在快捷面板上。已补（A13 有同名方法，直接读原生值，读不到才放行并记 `qs=unavailable`）。

### 12.4 落地的代码改动（`module/src/.../Os5Mirror.java`）
- 单一谓词 `clearAllShouldShow(shelf)` = `sDismissAllowed && isShadeExpanded && !isQsFullScreen && !isKeyguardLocked`，
  四处调用点（`setDismissVisible` 钩子、`onConfigurationChanged`、`changeShelfVisibility`→`reEvaluate`、边沿钩子）全部改走它，
  防止以后只改一处造成分叉。`reEvaluate` 只额外多一项 `shelf.getVisibility()==VISIBLE`（一键清除后栏收起）。
- `gatedRecheck(boolean[] last)`：布尔参数没翻转就不重算——照抄 A15 的 `z2`。
  不门控的话 `setExpandedHeight` **每一帧**都会走一遍父链 + 2–3 次反射，全在主线程拖拽路径上。
- 见证日志加 `qs=`，验证器加 `qs != "isQsFullScreen"` 断言。
- 锁屏仍用公开 API `KeyguardManager.isKeyguardLocked()` 而不是反射 `StateUtils`/`CentralSurfaces`：
  这是**有意偏离**（反射失败等于闸门形同虚设），已在注释里写明不是原生同一条调用。

### 12.5 离线验证现状
构建 `3d13deeebbb9`；`verify_os5_mirror.py --selftest` = 3 正例（15/15/14 条断言）+ **30 反例，漏网 0**
（新增两条：`qs=unavailable` 报红、去掉 `qs` 字段被"行数≠解析数"的格式漂移守卫报红）；`offline-all` 18 项全绿。

## 13. A13 真机第一次跑（DSU/OS3）：离线永远看不到的四个缺陷，一次全暴露

### 13.0 环境（可复核，别当成假想设备）
```
ro.build.version.sdk=33   ro.vivo.os.version=13.5   ro.product.model=V2314A
ro.gsid.image_running=1   gsid.image_installed=1    ← DSU 就是这台在跑的 OS3
su → uid=0 context=u:r:ksu:s0                       ← KernelSU 在 DSU 里可用
lspctl status → LSPosed 2.2.1 (7930) / Developer mode: yes / System server: connected
lspctl scope list com.betteroriginos → com.android.systemui, com.vivo.systemuiplugin,
    com.android.providers.media.module, com.android.filemanager, com.android.dialer
grep -icE 'zygisk|lsposed' /proc/$(pidof zygote64)/maps → 6      ← 注入确实发生
wm density=480（3.0）  wm size=1080x2388
```
设备时钟比本机快 8 小时：日志里的 `11-03 06:4x` 就是本机的 `11-02 22:4x`，比对时间时别再判成"未来日志"。
**更正**：我此前报告"OS3 的 DSU 镜像不在机器上（`/data/dsu` 不存在），要跑得重装"——错的。
DSU 的状态在 gsid 属性里，不在那个目录；而这台的 `dmctl` 只是 device-mapper 工具，**没有 `start-dsu` 子命令**。
判 DSU 用 `getprop | grep -i gsid`，别拿 `ls` + `dmctl` 当证据。

### 13.1 第一轮（包 `3d13deeebbb9`）暴露的缺陷
1. **半径整条是坏的**：`os5_mirror: radius write failed` ×22，`radius level=` 见证 0 条，落点自检 `radius=0`。
   根因（代码级，不是猜）：我们在 `initDimens` 的 after-hook 里调 `applyRoundness()`，
   而 `initDimens` 是 `ExpandableOutlineView.<init>:103` 里跑的；
   `applyRoundness()` 在 `ActivatableNotificationView:308-310` 会走
   `applyBackgroundRoundness(getCurrentBackgroundRadiusTop(), ...)` →
   `mBackgroundNormal.setRadius(f,f)`（:86-88），而 `mBackgroundNormal` 要到
   `onFinishInflate`（:472-474）才 `findViewById` —— 构造函数那一刻它是 null ⇒ NPE。
   修法：背景没就绪就**只写 `mOutlineRadius` 字段**（G2 路径与 bind 都读它），
   另挂 `ActivatableNotificationView.onFinishInflate` 补一次（`hookAllMethods` 只匹配该类自己声明的方法，
   所以必须钩这个类而不是基类）。
2. **`clear=0` 是假见证**：`sClearAttaches` 声明了、日志里也打了，全文件**没有任何一处自增**，
   所以"从未补挂"和"补挂过"长得一模一样。修：`host.addView()` 后面 `sClearAttaches++`，
   并新增离线检查 `work/witness-counters-check.py`（打进日志的统计量必须有写入点；自带正负自测）。
3. **`shade=untried qs=untried`**：`clearAllShouldShow` 用 `&&` 短路，收起态下 `mIsExpanded=false`
   就再也没读 `isQsFullScreen`，而挂载日志恰在此时打印 ⇒ 见证永远不更新。
   修：四项各自先求值再合并（见 §12.4 的同一函数）。
4. **门禁第 5 步直接崩**：脚本 `export MSYS_NO_PATHCONV=1` 后，`$ROOT/work/x.py` 原样交给
   Windows python ⇒ 它去找 `C:\c\Users\...`。`os3-smoke.sh` 早就用 `cygpath` 处理并写了注释，
   两个新脚本（`os3-smoke-os5mirror.sh`、`a11-card-gate.sh`）又踩回去 ⇒ 全部改过 `cygpath -m`。
5. **两条假红灯**（检查器自己错，不是被测代码错）：
   - 第 6 步 `sep=off 仍有复刻度量日志 1 条`：抓到的是 `clear-all native=` 探针（装钩子时必打，与几何无关）
     ⇒ OFFHITS 收窄到几何标记；
   - 第 7 步 `SystemUI 在批量移除中重启：16059 -> 16820`：拿的是 **A 相**的 pid 基线，
     而中间第 6 步自己重启过 SystemUI ⇒ 基线改为在 B 相重取；
     同时把真正的崩溃判据挪到**点击之后**（这才是它该保护的东西）。
     本轮核对过：A 相全程没有非人为的 SystemUI 死亡（`Process com.android.systemui (pid 16059) has died`
     只出现在第 6 步自己重启那一刻），日志里的 `vivocameraserve` SIGABRT 循环是这台 DSU 镜像的厂商相机服务，与本模块无关。

### 13.2 第二轮（包 `a65d49aad000`，修完 1–4 后）
`radius=24`（原 0）、`g2=10`、`shelf=2`、`clear=1`（原 0），钩子命中明细里新增 `onFinishInflate=1`；
验证器从 12 通过涨到 19 通过，剩下的 3 条：
- `qs=untried`（上面第 3 条，短路还没改）——已改；
- `bg clip` 一条都没有。查 A13 源码后确认这是**可能正常的**：`mCornerRadii` 由
  `getCurrentBackgroundRadiusTop() = mCurrentTopRoundness * mOutlineRadius`（OS3 jadx:237-238）得来，
  系统没给圆角的行半径本就是 0，裁剪提前返回。但"没日志"同时也能长成"反射字段名漂了"，
  第三种可能是矩形算不出来 —— 三者不可区分就是问题本身。
  ⇒ 第三轮加 `bg clip skipped reason=<no-radii-field|radii-zero|no-rect|throw:*> n= maxR=` 见证，
  **先拿到真机上的 reason，再决定验证器该红还是该绿**，不提前编规则。

### 13.3 第三轮（包 `f90da9b509a5`）在跑
门禁：`work/os3-smoke-os5mirror.sh`，证据按包哈希另存 `work/z8os3-live/os3-gate-r8.txt`。

### 13.4 第三轮结果（包 `b08a88f7bf24`）+ 两条规则的定案
真机读数（`work/z8os3-live/os5mirror-on.log`，同批另存 `os5mirror-on-<包哈希>.log`）：
`os3-smoke-os5mirror.sh` 步骤 3-7 全绿，验证器 **23 条断言通过 / 0 失败**：
- 落点自检 `reachable radius=24 g2=10 shelf=2 clear=1`；
- 收纳栏：`topRoundness` 读回 1.000 ×4、`shelf gap 0.0 -> 21.0`（+7dp×3.0）×2 与幂等命中 ×2；
- 一键清除：按钮 46dp=138px、底边距 120px(nav=false，来源 `insets`)、宿主 `NotificationPanelView`、
  判定来源 `shade=isExpanded qs=isQsFullScreen`（这一条就是 §12 那次对齐的落地证明）；
- 点击链路：`inlineListener=1 inlineVis=8 shelfVis=0`，可清除通知 21 → 9；
  **点击后 SystemUI 仍是同一进程**（崩溃判据现在贴在点击之后）；
- `sep=off` 负对照：几何标记 0 条；crash buffer 无本模块记录。

两条规则的定案（都是真机逼出来的，不是我先验设计）：
1. **`bg clip` 缺席要能分辨成因**。A13 的 `mCornerRadii` = `mCurrentTopRoundness * mOutlineRadius`
   （OS3 jadx:237-238），系统不给圆角的那一轮里"没裁剪"是原生行为；
   但它与"反射字段名漂了/矩形算不出来/抛错"在日志里同形。模块因此打
   `bg clip skipped reason=<radii-zero|no-radii-field|no-rect|throw:*> n= maxR=`，
   验证器：`radii-zero` → 绿（带可见 note），其它原因或**完全没有见证** → 红。
   正反例都跑过（`裁剪缺席且成因是反射失效`、`裁剪缺席且没有任何见证` 均正确报红）。
   G2 裁剪本体并非没验过：r8 那轮真机打出过
   `bg clip g2 r=54.0 bounds=[0,0][960,279] lt=true rt=true rb=true lb=true stale=1`
   —— 注意这条行形就是下一点的起因：那个样本文件随后被 r9 覆盖，只剩这里的摘录，
   所以脚本现在会先按包哈希留档再跑校验。
2. **断言不要建在平台格式化字符串上**。`Rect.toShortString()` 在这台 A13 上是 `[0,0][960,279]`，
   我按 AOSP 印象写的 `(\d+, \d+ - \d+, \d+)` 整条解析不到，被"行数≠解析数"的漂移守卫抓成红。
   模块改成自己打四个数字，验证器加一条反例 `背景裁剪回到平台格式化`。

### 13.5 这台 DSU 镜像自己的钩子可达性（别拿别的机型的结论顶替）
从 DSU 里直接拉 `/system/priv-app/SystemUI/SystemUI.apk`（51,460,786 字节，`adb exec-out cat` +
`MSYS_NO_PATHCONV=1`；过 su 管道会污染字节数）→ `dexdump -f` 三个 dex（141MB / 21774 类）→
`work/os3-probe.py` → `out/OS3-DSU-HOOK-REACH-1102.md`：**涉及类 36、缺失方法 0**。
本轮新增的钩子/反射目标全部命中：`NotificationShelf.updateAppearance|changeShelfVisibility|setDismissVisible`、
`NotificationStackScrollLayout.setIsExpanded|setQsFullScreen`、
`ActivatableNotificationView.onFinishInflate`、字段 `mBackgroundNormal`/`mCornerRadii`/`mOutlineRadius`。
（顺带：这台镜像的 NSSL **有** `mSidePaddings`，与早前 V1913A 那台的结论不同——跨镜像也会差。）

**检查器自己的一条锁**：`os3-probe.py` 第一次被我按 `module/src` 调用时输出了"涉及类 0 / 缺失 0"的
**全绿空报告**；现在 SRC 给错、FILES 里文件缺失、或 dexdump 不含 `Class descriptor` 都会
`PROBE ABORT` 退出码 2（正误两种调用都验过：错=2，对=0/36 类）。
