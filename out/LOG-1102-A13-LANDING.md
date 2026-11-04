# 2026-11-02 · A13 落点能力清单（OS5→OS3 移植的可行性底座）

目的：代理 A 在逆 **OS5 怎么算**（目标真值），这份文件逆 **OS3 能做到什么**（落点能力）。
两边对上才能定移植方案。所有结论只用 dexdump / 类清单取证，未使用截图。

取证素材（本机已存在，路径可直接复核）：
- A13：`G:\os3-ref\os3-dexdump-f.txt`（214MB 全量 dump，行号可回查）、
  `G:\os3-ref\os3-systemui-classes.txt`、`G:\os3-ref\os3-plugin-classes.txt`
- A15：`work/systemui/classes3.dump`（vivo/daemon 侧）、`work/systemui/classes5.dump`（SystemUI 主 dex）、
  `work/systemui/classlist.fqcn.txt`

## 1. 圆角：A13 的圆角管线比 A15 薄得多

| 关键字 | A13 引用数 | A15 引用数 | 判读 |
|---|---|---|---|
| `setTopRoundness` | 4 | 6 | 两边都有行级"顶部圆角像素"入口，可钩 |
| `setBottomRoundness` | 4 | 6 | 同上 |
| `applyRoundness` | **3** | **34** | A15 是一整套 roundness 管线（多处计算/多处应用），A13 只有零星 3 处 |
| `VRoundedCornerDrawable` | **0** | 14 | vivo 的圆角 Drawable 在 A13 的 **SystemUI dex 里 0 引用**（A15 有 14 处）|
| `mBackgroundNormal` | 1 | 60 | A15 到处读行背景；A13 只有 1 处 |

⇒ 结论：**不能靠"把 A15 的圆角算法搬过来"实现移植**——A13 根本没有那套管线的落点。
可行路径是 A13 仅有的行级入口：`ExpandableNotificationRow.setTopRoundness/setBottomRoundness`（+ 背景 `mBackgroundNormal`）。
补充取证：A13 的 `mBackgroundNormal` 声明在 `com.android.systemui.statusbar.notification.row.ActivatableNotificationView`
（`os3-dexdump-f.txt:2422703`），行类是它的子类 ⇒ WIP 里 `getObjectField(row, "mBackgroundNormal")` 的反射链在 A13 是成立的
（XposedHelpers 会沿父类找），字段名不是问题；问题只在**取到的 `mCornerRadii` 值是不是 A15 的目标值**。
注意 `[待核]` `VRoundedCornerDrawable` 在 A13 是否存在于 vivo framework/services 侧（本次只扫了 SystemUI dex），
模块现有 shelf 补丁同时处理 `GradientDrawable` 与 `VRoundedCornerDrawable` 两种背景，属防御式写法，与这里的 0 引用不矛盾。

## 1b. 圆角相关 dimen 的两版实测值（aapt2 dump resources，非截图）

命令：`work/sdk-dl/bt35/android-15/aapt2.exe dump resources <SystemUI.apk> | grep -A1 dimen/<name>`
（APK：OS5 = `work/systemui/SystemUI.apk`，OS3 = `G:\os3-ref\OS3-SystemUI.apk`；两版 APK 内都**没有** `res/values/*.xml`，
values 只存在于 `resources.arsc`，所以只能这样读值，不能用 grep 找 xml）

| dimen | OS5 / A15 | OS3 / A13 | 判读 |
|---|---|---|---|
| `notification_corner_radius` | **12.0dp** | **12.0dp** | 两版 AOSP 基线值相同 ⇒ OS5 的观感差异**不来自这个 dimen** |
| `notification_corner_radius_small` | 0.0dp | 0.0dp | 同上 |
| `notification_corner_animation_distance` | 48.0dp | 48.0dp | 一致 |
| `notification_material_rounded_rect_radius` | 12.0dp | 12.0dp | 一致 |
| `notification_scrim_corner_radius` | 32.0dp | 32.0dp | 一致 |
| `notification_shadow_radius` | **18.0dp** | **17.0dp** | 唯一不同的共有值 |
| `notification_shadow_radius_default` | **15.0dp** | **不存在** | OS5 新增的"半径档位族" |
| `notification_shadow_radius_sharp` | **12.0dp** | 不存在 | 同上 |
| `notification_shadow_radius_mellow` | **24.0dp** | 不存在 | 同上 |
| `notification_shadow_radius_smooth` | **24.0dp** | 不存在 | 同上 |

⇒ 可执行结论：OS5 相对 OS3 的圆角差异，**结构性差别是 OS5 多了一套按档位选择的半径族（12/15/18/24dp）**，
而共有的 AOSP 基线 dimen 完全一致。这与 §1 的引用计数差（`applyRoundness` 3 vs 34）方向一致：
差异在"谁在什么位置取哪一档"，不在"基础常量不同"。
⇒ 交叉验证线索：本项目在 OS3 DSU 上实测过的 shelf 半径是 **45px @ density 3.0 = 15dp**
（见 DEVLOG 2026-10-29 收纳栏圆角条目），**恰好等于 OS5 的 `notification_shadow_radius_default`**；
⇒ 也就是说 A13 运行时有效圆角（15dp，来自 vivo 侧 token/overlay，而非 AOSP 的 12dp）与 A15 的 default 档位同值，
真正要移植的是**分档策略（哪张卡用 sharp/default/mellow）与顶部/底部圆的分配**，而不是把数值改大改小。
`[待核]` A15 运行时实际生效值（需真机 witness，或代理 A 找到读这些 dimen 的具体方法）。
这条判据也说明：未提交 WIP 里 15dp 的兜底值不算离谱，但**必须换成"从 A15 分档表推导"**，
并解释 clamp 到 12~28dp 的那段（12/15/18/24 正好落在区间内，可改为直接取档位）。

## 2. 收纳栏：A13 与 A15 都还有 NotificationShelf

- A15：`com.android.systemui.statusbar.NotificationShelf` 在 `classes5.dump` 有实体类与其 lambda
  （`NotificationShelf$$ExternalSyntheticLambda0.<init>:(Lcom/android/systemui/statusbar/NotificationShelf;)V`，
  `classes5.dump:107722` 起）⇒ **A15 没有删掉 shelf**，OS5 顶部那块仍是 shelf 视图，移植有对应物。
- A13：`NotificationShelf.mPaddingBetweenElements` = **int**（`os3-dexdump-f.txt:2437979`）
  ⇒ 收纳栏自己的"条目间距"在 A13 就有字段可写；当前模块的「通知分割」WIP **只写了 NSSL，没写 shelf**，
  这是要补的第一处。

## 3. 一键清除：A15 有 vivo 专用动画类，A13 没有 ⇒ 必须自绘复刻

- A15 存在 `com.vivo.systemui.statusbar.ui.NotificationClearAllImageView`，带
  `startClearAllButtonAppear` / `startClearAllButtonDisnimator`（原文拼写如此）两个动画入口
  （`work/systemui/classes3.dump:150548` 起，`classlist.fqcn.txt` 命中 6 条）。
  同版本 AOSP 侧还有 `NotificationStackScrollLayout.mClearAllButton` + `setClearAllButtonLocation()` +
  `setClearAllButtonListener`（`classes5.dump:886782/887331/888432`），以及 Compose 页脚
  `FooterViewBinder.bindClearAllButton` / `FooterViewModel.clearAllButton`。
- A13 **完全没有** `NotificationClearAllImageView`（`os3-systemui-classes.txt` 命中 0，plugin 侧 0）。
  A13 的 `mClearAllButton` 只有两处声明方：
  - `com.android.systemui.statusbar.notification.row.FooterView.mClearAllButton` → 类型
    `FooterViewButton`（`os3-dexdump-f.txt:2424596` 附近）
  - `com.android.systemui.statusbar.notification.stack.SectionHeaderView.mClearAllButton` → 类型
    `android.widget.ImageView`（`os3-dexdump-f.txt:2427102` 附近）
  另有 `NotificationStackScrollLayout$FooterClearAllListener` / `$ClearAllListener` /
  `$ClearAllAnimationListener` / `$ClearAllNotisDismissListener`（类清单命中）。

⇒ 移植含义：OS5 的"一键清除"观感（vivo 动画 ImageView + 位置计算）在 A13 **没有同名落点可钩**，
只能把观感参数（图标资源、尺寸、圆角/背景、出现动画曲线与时长）从 A15 逆出来后，
在 A13 的 `SectionHeaderView.mClearAllButton`（普通 ImageView，改造成本低）或 `FooterView` 上自绘复刻。
`[待核]` A13 运行时真正渲染的是 SectionHeaderView 还是 FooterView 的那枚按钮（两者都存在，取决于 vivo 的页脚策略），
这条必须等真机 dump 或代理 A 的 A15 渲染链结论对齐后再定，别猜。

## 4. 与「通知分割」WIP 的关系

WIP（未提交，`NotifyRowFeature.java:1409-1558`）已经在 A13 上做"8dp 间距 + 每张独立圆角卡"，
但它的圆角值是启发式的：读首个子行 `mBackgroundNormal.mCornerRadii` 最大值再钳到 12~28dp，
读不到就 15dp；恢复时读不到原生 top/bottom roundness 就按"首行顶圆/末行底圆"猜。
按本文件 §1，A13 可用的只有行级两个 setter ⇒ **这些猜测值必须换成 A15 逆出的真值常量表**，
并补齐 §2 的 shelf 间距与 §3 的清除按钮。

## 5. 今晚的设备边界（防止后续会话误判）

- Z8 宿主 = OS5/A15，**kill SystemUI 会进安全锁且我们无法解锁**（既有记忆明确"never brute-force，等用户"）
  ⇒ 今晚不做任何需要重启 SystemUI 的真机验证；OS5 侧只做静态取证。
- OS3 DSU 镜像当前不存在于该设备 ⇒ 需要全新装 DSU（重置该槽数据），属用户当面确认项，不自动执行。
- Y7s（A11）无 root，只做只读取证 + 短暂亮屏的 UI dump（OLED 保护）。
