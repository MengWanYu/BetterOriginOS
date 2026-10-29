# 02 · 顶栏 / 底栏 / 滚动视口

> 逆向来源：OriginUI 库（fm-src）+ 时钟 App HeaderLayout（ref/app-usage-samples/）+ 真机对照。

## 1. 顶栏（Toolbar 场景）

### 1.1 结构
OriginUI 的 `VToolbar`（widget/toolbar/VToolbar.java，2646 行）本身**不含模糊**；
模糊由布局层的 `VBlurLinearLayout`（TYPE_TOOLBAR）承载，Toolbar 作为其子 View。
应用侧两种用法：
1. XML：`<VBlurLinearLayout app:blur_type="toolbar"> <VToolbar …/> </VBlurLinearLayout>`
2. 类继承：自定义 Bar 直接 extends VBlurLinearLayout（文件管理 LiteRecentFilesBottomTabBar 即如此）。

### 1.2 VToolbar 要点（ref/originui-fm-src/com/originui/widget/toolbar/VToolbar.java）
- 结构：返回键区 + 标题区 + 动作区（VEditLayoutButton 长按编辑/多选态）。
- 标题双态：页面顶部大标题（28sp medium，跟随内容滚动渐隐）→ 顶栏小标题（17sp medium，滚动后渐显）。
  时钟/设置的分栏滚动由 HeaderLayout（NestedScrollingParent，阻尼弹簧）驱动：
  - 弹簧参数（HeaderLayout 构造）：zoom=4.0、pow=2.0、fix=2.3、velocityMultiplier=1.0
  - 状态机：0=reset → 1=dragging → 2=holding → 3=over-scroll → 4=animating
  - 底部过滚动：`c.c(getContext())`（框架 overScroll 距离）
- 搜索态：VSearchView 替换标题（详见 03）。

### 1.3 滚动联动模糊（复刻已验证）
```
ScrollView.OnScrollChanged / ListView.onScroll:
    dist = scrollY
    alpha = dist < 16dp ? dist/16dp : 1.0        // VBlurUtils.getScrollBlurAlpha
    blurLayout.setMaterialAlpha(alpha)
    发丝线 alpha 同步（VBlurLinearLayout.setDividerAlpha）
```
真机证据：`evidence/replica-scrolled.png`（渐变内容穿过顶栏被雾化）。

## 2. 底栏

### 2.1 OriginUI VBottomNavigationView（widget/navigation/）
- 主体继承 m3 风格（design_bottom_navigation_shadow + item padding top/bottom dimen）。
- item：图标 + 文字 10sp；选中 accent、未选 text_secondary；
  徽标（origin_navigation_item_badge_radius_with_text_rom13_5 / without_text）。
- 阴影：VBottomNavigationShadow 高度 = design_bottom_navigation_shadow_height。
- **模糊底**：App 侧把底栏类 extends VBlurLinearLayout（TYPE_SHEET_DIALOG → light 204 / dark 254），
  顶部发丝线（divider bottom=false → 顶部画线）。

### 2.2 复刻（OriginBottomBar）
- OriginBlurLayout 衍生 + 64dp 高 + tab（图标 24dp + label 10sp）+ 选中 accent。
- 真机证据：`evidence/replica-4.png`（底栏 204 材质，页面内容穿过时雾化）。

## 3. 列表页面骨架（OS5 设置结构）

```
FrameLayout
├── ScrollView / ListView                ← 内容（页底 bgPage）
│   ├── 大标题 pageTitle 28sp（仅首屏）
│   ├── sectionLabel（12sp section_label 色，上下 18/8dp）
│   └── 分组卡（行级圆角：bg_item_top/mid/bot + 组内 1dp 发丝线左缩 70dp + 组间 8dp）
├── OriginTopBar（TOOLBAR 材质，滚动 <16dp 渐显）
└── OriginBottomBar（SHEET_DIALOG 材质，64dp）
```

token 见 OriginTheme.java（= out/DESIGN-SPEC-1025.md §3，已验证与真机一致）。

## 4. HeaderLayout 阻尼滚动头（时钟 App 用法，可选复刻）

`HeaderLayout extends LinearLayout implements NestedScrollingParent`（BBKClock）：
- 两个 child：headerView（索引 f2531w，默认 0）+ content（索引 f2532x，默认 1）。
- 嵌套滚动 axes=SCROLL_AXIS_VERTICAL；onNestedPreScroll 先耗 header 位移（o()），
  onNestedScroll 后耗（p()），位移上限 max(topOver,bottomOver)=overScroll 距离。
- 触摸直拖（onTouchEvent）+ Overshoot 回弹由 f6.c（SpringHelper，w(200.0d, 26.0d) 刚度/阻尼）。
- damp 公式：`dist = input / (zoom * pow((limit-cur)/limit, t) + fix)`（c()）。
- 回调：setStatusChangeListener（状态迁移）+ setNestedListener（位移）。
- **复刻态**：OriginTopBar 未实现 header 吸附/阻尼（时钟场景特有）；如需：
  `OriginHeaderLayout` 移植上述 NestedScrollingParent 逻辑即可（源码在 ref/app-usage-samples/HeaderLayout.java）。

## 5. 已验证清单

| 项 | 证据 |
|---|---|
| TOOLBAR 顶栏模糊 + 滚动 16dp 渐显 | evidence/replica-scrolled.png |
| SHEET_DIALOG 底栏模糊（204/254） | evidence/replica-4.png |
| 行级圆角分组 + 发丝线左缩 | evidence/replica-4.png |
| 夜间模式（黑底/#202020 卡/蓝强调） | evidence/replica-night.png |
| 下拉（ListPopup 勾选态） | evidence/replica-popup3.png |
| 确认对话框（16dp 卡 + 文字按钮） | evidence/replica-dialog.png |
| 底部弹层（把手 + 胶囊按钮） | evidence/replica-sheet3.png |
