# 05 · OriginOS 6 堆叠通知取证（X200 Pro / V2405A / Android 16）→ 移植到 OS5（iQOO Z8）

取证时间：2026-10-26 17:06–17:16（设备已由用户提供并 root，KernelSU）。
样本：`su 2000 -c "cmd notification post -t StackTest{i} tag_stack{i} ..."` 三条同 app（com.android.shell）通知。
参考件（本地留存，设备已归还）：
- `G:\os6-ref\OS6-SystemUI.apk`（56.6MB，`/system/priv-app/SystemUI/SystemUI.apk`）
- `G:\os6-ref\OS6-SystemUIPlugin.apk`（130.5MB，`/system/priv-app/SystemUIPlugin/SystemUIPlugin.apk`）
- `G:\os6-ref\os6-shade.xml`（折叠态 uiautomator）、`G:\os6-ref\os6-expanded.xml`（展开态）
- `G:\os6-ref\os6-shade.png`（仅几何参考，**结论以视图 bounds 为准**）
- `G:\os6-ref\plugin-res.txt`（aapt2 dump resources，11.8MB）

density = 480 → 3.0 px/dp；屏宽 1080（与 Z8 相同，数值可直接对照）。

## 1. 折叠堆叠的实测几何（os6-shade.xml，单位 px / dp）

| 元素 | bounds(px) | 尺寸/偏移(dp) |
|---|---|---|
| 组容器行 `expandableNotificationRow` | `[48,1244][1032,1486]` | 宽 328 / 高 **80.7** |
| 正面卡（最新一条） | `[48,1244][1032,1437]` | 高 **64.3** |
| 背后第 2 层 | `[87,1286][993,1479]` | 下移 **+14**，左右各内缩 **13** |
| 背后第 3 层 | `[127,1324][953,1517]` | 下移 **+26.7**，左右各内缩 **26** |
| 组容器额外预留高度 | 1486-1437 = 49px | **16.3dp**（第 3 层越界绘制到间隙，靠 overdraw） |

结论：**露边在卡片下方**（不是上方），每层下移 14dp、左右各内缩 13dp，最多可见 2 层。

## 2. 单条/正面卡内部几何（LSPosed 行 `[48,1030][1032,1223]`）

| 元素 | bounds(px) | dp |
|---|---|---|
| 卡片 | 宽 984，高 193 | 328 × **64.3** |
| 侧边距 | 左 48 | **16** |
| 图标 `android:id/icon` | `[90,1072][198,1180]` 108×108 | **36dp**，左边距 14，垂直居中 |
| 文本列起点 `title/text` x=228 | 228-48=180 | **60dp**（14+36+10） |
| 标题 | `[228,1066][538,1123]` 高 57 | 顶部 12dp，≈15sp |
| 正文 | `[228,1129][990,1181]` 高 52 | 与标题间隙 2dp，≈14sp |
| 卡片底部留白 | 1223-1181=42 | **14dp** |
| 卡与卡间距 | 1030-1009=21 | **7dp** |

关键差异：**OS6 收起态没有 app 名/时间行**——只有 图标 + 标题 + 正文；app 名仅存在于行的
`content-desc`（uiautomator 里显示为 `text="Shell"`）。条数徽标（图中图标右上角的深色圆 +
白色数字）在视图树里**没有独立节点**，即由图标视图自绘。

## 3. 展开态（os6-expanded.xml）

- 组容器 `[48,1244][1032,2006]`（高 762px）。
- 组头带：1244 → 首个子行 1385，**141px = 47dp**（app 名 + 展开/收起箭头）。
- 子行各自 193px 高，间距 20px（≈6.7dp），按时间倒序（StackTest3 → 2 → 1），
  每条仍是完整 `expandableNotificationRow`（图标 36dp + 标题 + 正文，无 app 名行）。

## 4. 类名清单与 OS5 可用性

| OS6 类（dex 扫描） | OS5(Z8) 是否存在 |
|---|---|
| `statusbar.notification.stack.PriorityBucket` | 有（3 处引用） |
| `...collection.coordinator.StackCoordinator` | 有（10 处） |
| `...row.NotificationChildrenContainer` | 有（12 处） |
| `com.vivo.systemui.statusbar.notification.stack.StackDrawingOrder` | **无** |
| `...stack.VivoStackScrollAlgorithm` / `IStackScrollAlgorithm` | **无** |
| `...stack.MagneticNotificationRowManager(Impl)` | **无** |
| `...render.effects.BlendEffectGroup` | **无** |
| NMS 聚合组键 `g:Aggregate_AlertingSection`（Android 16 聚合） | OS5 无对应管道 |

→ OS6 的堆叠是「真实子行错位层叠 + 聚合分组管道」，OS5 既无 `StackDrawingOrder`/
`VivoStackScrollAlgorithm`，也无 AOSP16 聚合，**不能直接开关键复用**，需按上面实测几何在
OS5 上自绘实现（视觉与交互对齐，不追求类实现同源）。

## 5. OS5 侧可用挂载点（已在 `NotifyRowFeature` 使用）

- 组判定：`ExpandableNotificationRow.isSummaryWithChildren` / `isGroupExpanded`。
- 子项来源（**不依赖 GroupEntry**，OS5 上 `NotificationEntry.getRawChildren` 不存在）：
  `row.getChildrenContainer()` → `NotificationChildrenContainer.getNotificationChildCount()`
  + 容器子 View（`ExpandableNotificationRow`）→ `getEntry()` → `getSbn()`。
- 卡面材质色：`com.android.systemui:id/backgroundNormal`（`NotificationBackgroundView`）
  的 background 采样，折叠堆叠的自绘卡面/露边沿用同一色。
- 折叠/展开：沿用原生 `setUserExpanded`（组摘要行放行；普通 iOS 行仍禁用，避免掉回 OS5 展开模板）。

## 6. 移植到 Z8 的目标规格（本模块 `NotifyRowSpec`）

| 项 | OS6 实测 | Z8 采用 | 说明 |
|---|---|---|---|
| 露边方向 | 卡片下方 | 下方 | 之前实现画在上方，需翻转 |
| 每层下移 | 14dp | 14dp | |
| 每层左右内缩 | 13dp | 13dp | |
| 最大层数 | 2 | 2 | |
| 条数徽标 | 图标右上角自绘 | 图标右上角子 View | 深色圆 + 白字 |
| 图标 | 36dp | 暂留 38dp | 现有 iOS 规格已验证；如需完全对齐再改 |
| app 名/时间行 | 无 | **保留**（用户允许保留时间） | |
| 卡间距 | 7dp | 8dp（现有 iOS 规格） | 差 1dp，暂不动 |

## 7. 验证口径

沿用 `work/verify_notify.py` 的 `notify_row.verify.stack` 分支，断言：
露边层数/偏移/内缩、卡面高度 = 正面卡 + 层数×14dp、徽标可见与数字 = 组内条数、
图标 38dp 与 66dp 文本列、右侧 16dp、字号/颜色（日夜）、`groupExpanded` 状态；
所有断言只在视图静止（`scaleX/Y==1`）时出数。

## 8. Z8 落地要点（2026-10-26 23:29 实测通过）

- **折叠卡高度**：`ExpandableNotificationRow.getIntrinsicHeight()` 在摘要行会返回**子容器高度**（折叠也算），
  排版步长取的就是它。折叠期间必须把它改成卡片自身高度，否则卡下面必然留一整叠子行的空洞。
- **行高唯一真源（2026-10-27 定版）**：卡片自己用 `MeasureSpec.UNSPECIFIED` 量一次内容高
  （`IosNotificationRow#contentHeight`），`ExpandableNotificationRow.onMeasure` 的出口钳制和
  `getIntrinsicHeight()` 都读这同一个值。只读 `getMeasuredHeight()` 不行 ——
  `StackScrollAlgorithm` 会按 AOSP 缓存的行高把行 **EXACTLY** 钉死，卡片永远长不高（展开看不见）；
  只钳制 `onMeasure` 也不行 —— 排版步长仍是旧值，行下面留一整段空洞。
  两边同源后折叠/展开实测都是 行高 == 卡高 == contentHeight。
  **不要给这个高度加缓存**：影响量高结果的量太多（文本内容、maxLines、字体缩放、露边、子行数），
  少算一项就复现"行比卡高"。
- **展开态由卡片自绘，AOSP 永不展开**：堆叠行的 `setUserExpanded`/`setChildrenExpanded` 入参一律改成
  false，展开列表由 `setStackChildren` + `setStackExpanded` 自己渲染。借原生组展开会牵动高度动画、
  子容器可见性、背景尺寸三套状态，任何一处不同步就是"半展开混合态"（展开再收起掉回原生的真因）。
- **子行数据源**：`NotificationChildrenContainer.getAttachedChildren()`（源码 `mAttachedChildren`，
  由 `addChildNotification` 在绑定时填充，与展开无关），**不要**遍历 `getChildAt()` ——
  子容器被我们置 GONE 后视图树不可靠。
- **意图标记别共用**：`STACK_USER_EXPANDED` 这类标记一旦被"同步系统状态"的代码写过（例如跟随
  `GroupExpansionManager` 的钩子），用户意图就会被系统程序性的折叠冲掉。要么分开存，要么根本不同步。
- **收起交互**：OS5 的收起箭头走 vivo 自己的动作链，实测**从不调** `setUserExpanded(false)`；
  自绘方案下收起入口就是"再点一次卡片"，原生箭头直接 alpha 0 + 不可点击。
- **OS5 没有** `setGroupExpanded`（`NoSuchMethodError`）；把 `hasUserChangedGroupExpansion` 改成 false
  会**触发原生自动展开**，不要用。
- 组内子行按设计不显示 app 名行（组头已带），校验器要按这个口径断言。
- 未覆盖：RTL（需手动给 SystemUI 换 ar 语言，本机构 `am compat` / `cmd locale` 均不支持）。
