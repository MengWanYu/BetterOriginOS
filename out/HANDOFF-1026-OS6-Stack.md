# HANDOFF · 2026-10-26 · OS6 堆叠通知移植（iQOO Z8 / OriginOS 5 → 对齐 X200 Pro / OS6）

> 目的：把“通知条 iOS 化”升级为“真·折叠堆叠（同 app 一叠，点开展开）”，规格以 X200 Pro（OS6 /
> Android 16）的实测视图树为准。用户要求：**代码级核验（bounds/属性），不接受只看截图**；
> 可以保留时间显示（OS6 收起态没有 app 名/时间行，Z8 侧保留）。

---

## 1. 当前状态 TL;DR

| 模块 | 状态 |
|---|---|
| 分离模式（每条独立 iOS 卡） | ✅ 已验证（日夜/1.3/RTL，14 普通+5 overlay PASS） |
| 配置变化参数回退 bug | ✅ 已修（hook `reinitView/updateCornerRadius/onSystemFilletChanged`） |
| overlay 复用残留 bug | ✅ 已修（`dropOverlay`，swap 实验 `overlay dropped` + `overlays=0`） |
| OS6 取证 + 文档 | ✅ `vivo-OriginUI/docs/05-os6-notification-stack.md` |
| **stack 模式（折叠堆叠）** | ⚠️ **半成品 + SystemUI 崩溃，未构建验证最后一轮修复** |
| 堆叠卡几何（自绘） | ✅ 已测到达标（见 §4） |
| 折叠/展开交互 | ❌ 组被系统自动展开；用户点击展开尚未接（overlay 没有 click） |
| 设备当前安装 | ⚠️ 会崩的 stack 构建（`group_mode=ios_stack`）；建议先切 `separate` 稳定 |

---

## 2. 设备与产物

- **Z8（当前连接）**：`10AE4Y0MDE000BL`，Android 15 / OS5，root+KernelSU+LSPosed。
- **X200 Pro（已归还）**：`10AF2E1R03002ZQ`，V2405A，Android 16 / OriginOS 6。
- 取证件（本地，G 盘 154G 空闲；C 盘只剩 ~5G，勿放 C）：
  - `G:\os6-ref\OS6-SystemUI.apk`（56,628,194 B）
  - `G:\os6-ref\OS6-SystemUIPlugin.apk`（130,563,758 B）
  - `G:\os6-ref\os6-shade.xml`（折叠态 uiautomator）、`os6-expanded.xml`（展开态）
  - `G:\os6-ref\os6-shade.png`（仅视觉参考）、`plugin-res.txt`（aapt2 dump resources）
  - `G:\os6-ref\{ui-dex,plugin-dex}\`（从 APK 抽出的 classes*.dex，供字符串/类名检索）
- 项目内文档：`vivo-OriginUI/docs/04-notification-row.md`（现有实现）、`05-os6-notification-stack.md`（OS6 取证）、`out/DEVLOG.md`（全量时间线）。
- 验证证据：`work/os5-ref/notify-verify-20261026-{final,night,font13,rtl,stack}.log`。
- jadx CLI：`C:\Users\Administrator\Desktop\jadx\jadx-1.5.5\bin\jadx.bat`（直接 .bat 在 Git Bash 下报类加载错误，可用 `cmd //c` 再试或改用 `java -jar`）。

---

## 3. OS6 实测规格（density 3.0，屏 1080）

折叠堆叠（3 条 Shell 通知）：

| 元素 | bounds(px) | dp |
|---|---|---|
| 组容器行 | `[48,1244][1032,1486]` | 328 × 80.7 |
| 正面卡（最新） | `[48,1244][1032,1437]` | 328 × 64.3 |
| 背后第 2 层 | `[87,1286][993,1479]` | 下移 14，左右各缩 13 |
| 背后第 3 层 | `[127,1324][953,1517]` | 下移 26.7，左右各缩 26 |

- 露边在**卡片下方**，最多 2 层；组容器只预留 16.3dp，第 2 层越界绘制。
- 单行内部：图标 108px=**36dp**（左 14dp、垂直居中）、文本列 **60dp**、标题 15sp/正文 14sp、
  底部留白 14dp、卡间距 **7dp**、**无 app 名/时间行**；条数徽标（深色圆+白字）压在图标右上角，
  视图树里无独立节点（图标视图自绘）。
- 展开态：组头 47dp + 子行各 64.3dp、间距 6.7dp、时间倒序，每条仍是完整行。

类名可用性（OS6 → OS5）：OS6 有 `StackDrawingOrder` / `VivoStackScrollAlgorithm` /
`MagneticNotificationRowManager` / A16 聚合（`g:Aggregate_AlertingSection`）；**OS5 全部没有**，
只有 `PriorityBucket`、`StackCoordinator`、`NotificationChildrenContainer`。⇒ 只能按实测几何自绘。

---

## 4. 已实现（Z8 侧，代码位置）

主文件：`module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java`

- **模式开关**：`group_mode = ios_stack | separate`（默认 ios_stack）。`isStackMode()`、
  `initFeature()`、`restyle()` 里 `sStackMode` 决定是否启用拆组 hook。
- **分组保留**：stack 模式不 hook `isGroup/getGroupKey`、不删摘要、不 flatten（走原生 GroupEntry）。
- **组摘要 → 堆叠卡**：`applyStackOverlay(contentView, root, rowView)`
  - 子项来源（OS5 无 `NotificationEntry.getRawChildren`）：`getChildrenContainer()` →
    `getNotificationChildCount()` + 容器子 View → `getEntry().getSbn()`；最近一条作为正面卡。
  - `IosNotificationRow.setStack(bars,total,cardColor)`：卡面/露边自绘，露边**在下方**、
    每层 14dp、左右各缩 13dp；徽标画在图标右上角（半径 9dp，圆心距图标右 3dp）。
  - 隐藏：摘要根 `alpha=0`、`NotificationBackgroundView alpha=0`、行内 ExpandButton/Button alpha=0；
    给原生根视图**加底部 padding = bars×14dp**，让行高容下露边带；`STACK_ROOT_PAD` 记录原值，
    `dropStackOverlay` 恢复。
  - 卡面颜色：`sampleCardColor()` 采样 `NotificationBackgroundView` 的 GradientDrawable；
    取不到时用 `0xF2F6F6F6`（日）/`0xF21C1C1E`（夜）——实测都走兜底，说明 OS5 卡面不是 GD。
- **折叠/展开**：`collapseGroup()`（setUserExpanded(false)+setChildrenExpanded(false,false)）、
  `guardStack()` pre-draw 里按 `isGroupExpanded` 切换：折叠时组头/子容器 alpha=0、显示堆叠卡；
  展开时反过来。当前仍保留 `enforceCollapsed()`（最多压 5 次）——若改用 §6 的 setter 拦截，应删除。
- **验证**：`verifyStack()` → `notify_row.verify.stack ...`（overlayW/H、cardBottom、peekPx、
  badgeCx/Cy/R/Total/RightGap、icon/列/端距/字号/颜色、groupExpanded、rtl、night、fontScale）。
  `scheduleStackVerify()` 有静止门槛（`settledTree`，scaleX/Y==1 才算）。
- **OS6 图标满幅化**：`ZoomDrawable`（`AdaptiveIconDrawable` 放大 1.28× 裁边）+ `appIcon()`。

验证脚本：`work/verify_notify.py`
- 新增 `notify_row.verify.stack` 分支（独立列表 `stack_rows`，勿与流水线 `notify_row.stack` 混淆）。
- 断言：bars=min(count-1,2)、peek=bars×14dp、cardBottom=overlayH−peek、overlayH=rowH=contractedH、
  图标 38dp@16dp 且相对**卡片区**居中、徽标总数字=条数、badgeR=9dp、badgeRightGap=3dp、
  文本列 66dp/端距 16dp/字号/颜色。
- 组内子行（`rowParent` 含 ChildrenContainer）跳过顶层卡片几何断言；`groupExpanded=1` 的 stack 记 warning 并跳过。

已测到达标的日志（示例，20:29）：
`verify.stack pkg=android count=2 bars=1 overlayW=984 overlayH=210 contractedH=210 peekPx=42
cardBottom=168 iconCenterDelta=0 badgeCx=153 badgeRightGap=9 badgeR=27 badgeTotal=2 ...`
——但同一行 `rowH=647 groupExpanded=1`（组未折叠）。

---

## 5. 阻塞问题（未解决，必须优先）

### 5.1 SystemUI 崩溃（保留 GroupEntry 引发 vivo 代码 NPE）
```
java.lang.NullPointerException: Attempt to invoke virtual method
'boolean java.lang.String.equals(java.lang.Object)' on a null object reference
  at com.vivo...VivoLegacyNotificationIconAreaControllerImpl.updateVivoIconsForLayout(:321)
  at ...LegacyNotificationIconAreaControllerImpl.updateIconsForLayout(:585)
  at ...updateShelfIcons(:469) → updateNotificationIcons(:455/433)
  at StackCoordinator.onAfterRenderList(StackCoordinator.kt:71)
```
- 已加 `installVivoIconGuardHooks()`：对 `updateVivoIconsForLayout` 用
  `XposedBridge.invokeOriginalMethod` 包 try/catch（第一层已生效，日志 `vivo icon layout swallowed`）。
- **最后一轮代码已把基类 `LegacyNotificationIconAreaControllerImpl.updateIconsForLayout` 也加进守卫
  （`icon layout guards installed=N`），但尚未构建/验证**。
- 若仍崩：把守卫扩到 `updateNotificationIcons` / `updateShelfIcons`，或直接在
  `StackCoordinator.onAfterRenderList` 外层包 try/catch（最省事，代价是那一帧状态栏图标不刷新）。

### 5.2 组被系统自动展开
- 现象：`force collapse attempt=2..5` 后放弃，`rowH=647`，堆叠卡被压在展开组里。
- 最后一轮代码已改为**setter 层拦截**：`setUserExpanded(true)` 对 `STACK_ROWS` 只有在
  `STACK_USER_EXPANDED` 有标记（用户点击）时才放行，否则改成 false —— 尚未构建/验证。
- **还缺一块**：`applyStackOverlay()` 目前 `overlay.bind(..., null, null)` 且没有设置
  `overlay.setOnClickListener` ⇒ 用户点击无法展开。需加：
  ```java
  overlay.setOnClickListener(v -> toggleStack(rowView));
  overlay.setOnLongClickListener(v -> longClickRow(rowView));
  // toggleStack: 已展开→去标记+collapseGroup；未展开→STACK_USER_EXPANDED.put+collapseGroup 反向
  ```
  （注意：`collapseGroup()` 会 `STACK_USER_EXPANDED.remove()`，展开分支要在调用 setUserExpanded 前打标记。）

---

## 6. 工作区状态（重要）

- 已安装 APK = **上一轮构建**（含第一层 icon 守卫 + enforceCollapsed，但**会崩**）。
- **源码里有未构建/未验证的改动**：
  1. `XposedBridge.invokeOriginalMethod` 桩（`module/stubs/.../XposedBridge.java`）；
  2. 基类 `updateIconsForLayout` 守卫（`installVivoIconGuardHooks` 重写为多类多方法）；
  3. setUserExpanded 对堆叠行改为“非用户展开一律拒绝”（`STACK_USER_EXPANDED`）。
- `enforceCollapsed()` / `STACK_COLLAPSE_TRIES` 仍在 `guardStack` pre-draw 里调用；setter 拦截生效后
  应删除，避免两套机制互相抖。
- 设备设置：`betteroriginos_notify_row_group_mode=ios_stack`（曾被写成 `separate`，可能是用户点过选项）；
  `betteroriginos_notify_verify=1`；`betteroriginos_feature_notify_row=1`。

---

## 7. 构建 / 部署 / 测试（Z8）

```bash
cd module && ./build.sh            # 产物 module/build/BetterOriginOS.apk（~169K）
adb install -r build/BetterOriginOS.apk
adb shell "settings put global betteroriginos_notify_row_group_mode ios_stack"
adb shell "settings put global betteroriginos_notify_verify 1"
adb shell "su -c 'killall com.android.systemui'"     # 必做：新 dex 要进程重启
```
解锁（keyguard 会按可见性过滤我们的 PRIVATE 通知，必须先解锁再验证）：
```bash
adb shell "cmd statusbar collapse; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; input keyevent KEYCODE_MENU; \
           input swipe 540 2250 540 600 250"
adb shell "dumpsys window | grep -i isKeyguardShowing"   # 必须 false
```
投递样本（**必须一次 onCreate 全投**；`am force-stop` 会顺带清掉该应用通知，切勿在两次投递之间用）：
```bash
adb shell "am force-stop com.betteroriginos; am start -n com.betteroriginos/.ui.MainActivity \
           --es debug_notify postall"      # 4242 普通/4243 聊天/4244-46 同组/4248 会话/4249 长文本
adb shell "cmd statusbar expand-notifications"
adb shell "settings put global betteroriginos_notify_reverify <递增数字>"   # 触发已绑定行重验
cd .. && python work/verify_notify.py
adb logcat -d -v time > work/os5-ref/notify-verify-20261026-stack.log
```
注意：
- vivo 冻后台应用，**广播不可靠**（`am broadcast ... DEBUG_NOTIFY` 常被延后）；用 Activity 通道。
- `MainActivity` 已加 `onNewIntent`，但后台实例的启动会被系统/冻结策略吞掉，依旧优先“一次 postall”。
- Receiver 受 `android:permission=DUMP` 保护（仅 shell/root/system）。
- 旧门禁：`verify.stack` 只在行静止时出数；滚动/滑动中途的值不可信。

---

## 8. 保真差距与待决（用户已表态的）

- **app 名/时间行**：OS6 收起态没有；用户说“可以保留时间显示” ⇒ 当前保留 34dp header（app 名+时间右侧）。
- 图标：Z8 仍 38dp（OS6 36dp）；文本列 66dp（OS6 60dp）；卡间距 8dp（OS6 7dp）。
- 卡面材质：自绘用采样/兜底色；OS5 原生卡不是 GradientDrawable ⇒ 目前恒为兜底 `0xF2F6F6F6`，
  夜间切换需重采样（`STACK_NIGHT` 目前只在夜间变化时重放主题，不重采样颜色，需补）。
- 展开态：OS5 展开仍是“组头 + 同卡内子行”，不是 OS6 的“独立卡”。若要完全对齐，需要展开时把
  子行从 children container 拆成顶层卡（复杂度高，先不做）。
- 条数徽标：OS6 日夜都是深圆白字；当前固定 `0xE6000000/白色`。
- 卡片背景材质 / iOS 动作菜单 / heads-up（vivo 自绘胶囊）/ custom view：仍为已知限制（见 docs/04 §7）。

---

## 9. 建议的下一步（按顺序）

1. **先稳定设备**：`settings put global betteroriginos_notify_row_group_mode separate`（或 feature=0），
   重启 SystemUI，确认不再崩。
2. 构建当前工作区（基类 icon 守卫 + setter 拦截），单测崩溃是否消失；观察
   `icon layout guards installed` 与 `vivo icon layout swallowed`。
3. 补 `overlay.setOnClickListener → toggleStack()`（§5.2），验证：默认折叠 → 点卡展开（子行出现、
   仍是 iOS 行）→ 再点收起；`verify.stack groupExpanded` 0/1 两态都出数。
4. 折叠态截图 + `verify.stack` PASS；再跑日/夜/1.3/RTL 矩阵（stack 模式）。
5. 打磨项：夜间重采样卡色、OS6 对齐（去掉 header / 36dp / 60dp / 7dp）做成第二选项让用户选、
   展开态独立卡方案评估。
6. 文档：把 §5 的最终修法写回 `docs/05` 与 DEVLOG；确认 `out/TASK-NOTIFY-ROW-1026.md` §9 勾选。

---

## 10. 快速索引（代码）

- `NotifyRowFeature.java`
  - `buildOptions()`（group_mode + stack_metrics）
  - `install()`（setContracted/Expanded/HeadsUp、onMeasure、calculateVisibleType）
  - `installUngroupHooks()`（separate 模式才生效）
  - `installStackMetricsHooks()`（含 `reinitView` 等）
  - `installVivoIconGuardHooks()`（★ 崩溃守卫，未验证）
  - `restyle()`（summary 分支 → `applyStackOverlay`）
  - `applyStackOverlay / dropStackOverlay / guardStack / collapseGroup / enforceCollapsed`
  - `verifyStack()`、`scheduleStackVerify()`
  - `appIcon()`（ZoomDrawable）、`sampleCardColor()`
- `IosNotificationRow.java`：`setStack(bars,total,cardColor)`、`onDraw()`（露边+徽标）、
  `badgeCenterX/Y/Radius`、`cardBottom()`、`bind()`、`applyTheme/applyTextScale`。
- `NotifyRowSpec.java`：`STACK_BARS=2`、`STACK_PEEK_LAYER_DP=14`、`STACK_BAR_INSET_DP=13`、
  `ICON_ZOOM=1.28`、卡片兜底色。
- `debug/NotifyTest.java`：post / postMessaging / postGroup / postConversation / postLong / postSwap / cancel；
  `MainActivity` 支持 `postall/postmsg/postgroup/postconv/postlong/postswap/cancel`。
