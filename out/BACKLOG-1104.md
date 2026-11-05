# 待办（2026-11-04 夜）— 从根上捋的交接清单

> 这份是"下一个对话直接开工"的目标日志。每条都写清：**现象 / 已量到的事实 / 根因假设 /
> 下一步要证的证据 / 验收口径**。验收口径一律要能"红"，否则不算收口。

## 0. 现场（先对齐，别凭记忆）

- 仓库 commit `a11786a`；设备在跑的包 **`afab6b808a3b`**（APK 按 .gitignore 不入库，
  哈希以 `out/DEVLOG.md` 末尾那条为准，装机前用 `pm path` + sha256 比对）。
- 设备：Z8 `10AE4Y0MDE000BL` 在 **DSU(A13)**；Y7s `IBR8GEMFJNBECIFA` 是 Android 11 参考机（无 root）。
- 调试开关（都在 `settings put global`）：
  `betteroriginos_notify_verify`=1（见证） / `betteroriginos_notify_sep`=os5（复刻开） /
  `betteroriginos_media_card_style`=a11 / `betteroriginos_trace_flicker`=1（逐帧快照，**用完置 0**）。
- 门禁：离线 `bash work/offline-all.sh`（红了别上设备）；
  真机 `work/os3-evidence-rowlayout.sh`（三态）、`work/a11-card-gate.sh`（媒体卡）、
  `work/os3-evidence-groupcycle.sh`（组开合五帧）。

## 1. 【P0】header 时间在"展开过渡帧"错位（用户已提 4 次，跨 6 天）

**现象**：vivo服务这类多通知组里点开一条，动画过程中时间闪到图标列，稳定后回到 66dp 列。

**已量到**：稳定帧正确（title +198、time ≥66dp 列）；**过渡帧 time 落 +126，正好压在 42dp 图标上**。

**已做过两套机制（别重复造）**：
- (a) push-only：事后推 translationX（现在线上这版）——过渡帧必然过时，因为摆位是 vivo 代码按状态直写。
- (b) 布局期接管 `NotificationTopLineView.onLayout`（备份在 `work/NotifyRowFeature.col-hook-1103.java`）：
  当时三态 + L10/L11 全绿，但带着"大图标消失"的回归被整体回退。

**关键变化**：那个回归的根因已在 11-04 修掉（`LIGHT_POLISHED` 键在 contentView 而 A13 一行有两棵根 ⇒
标记过期 → `rearmStalePolish`；原生小图标改压 drawable alpha）。**所以现在具备重上 (b) 的前提。**

**下一步（顺序别乱）**：
1. 先写"逐帧验收"：用 `betteroriginos_trace_flicker` 或新增等价采样，断言**从点击到稳定的每一帧**
   里 header 时间不落进图标列（+126 是红的判据）；先在当前构建上跑到红。
2. 再恢复 (b)，复跑：该逐帧门禁 + 三态 + 组开合五帧。
3. 只有逐帧门禁绿了，才算这条收口；"稳定帧是对的"不算。

## 2. 【P0】收纳栏：可见性判据 + 透明缝（11-04 探测新增）

**现象**：LSPosed 在最后且展开；A13 收纳栏一直存在并盖住它一部分；栏与通知之间没有 OS5 那条透明缝。

**已量到**（一屏 9 行、列表已溢出）：栏 `(60,2232)-(1020,2298)` 高 66px；最后一行
`(60,2193)-(1020,2298)` 高 105px ⇒ **被栏盖掉 66px（约 63%）**。另有一条 `h=300` 的展开行是
vivo 组默认摊开（它同时报 `mChildrenExpanded=false`），不是我们造成的。

**机制差（反编译直读，别再猜）**：
- A13 `changeShelfVisibility(z)` 只有三个置 INVISIBLE 的条件（`z && 内联清除按钮==GONE` / 面板全收起 /
  QS 全展开）；**没有"栏里没有真正藏起来的通知"这一条**；`hasItemsInStableShelf` 只喂背景/描边，不喂可见性。
- A15 额外有 `mShowNotificationShelf` / `mIndexOfFirstViewInShelf` / `mHasItemsInStableShelf` 参与判断。
- 我们模块只做 `yTranslation += 7dp`（纯平移）+ 栏顶圆角 1.0；**可见性/裁切/遮罩一概没碰**，
  所以既不能"该藏时藏"，也做不出 OS5 那条缝（OS5 的缝来自栏背景 + 上方内容被裁，不是平移）。

**下一步**：
1. 先在现有 `shelf.changeShelfVisibility` 钩子里**逐次**打出 `z`、内联按钮可见性、
   `hasItemsInStableShelf`、栏当前可见性（现在只记命中数）——先证"z 是什么、谁决定的"。
2. 再定改哪一侧：让 NSSL 别传 z，还是在栏侧补一条"没有隐藏项就不显示"。
3. 缝的做法：让最后一行完整露出（栏上方留透明条）并保住它的底圆角，而不是把栏再往下推。

**承重墙**：这条判据里含"内联清除按钮是否 GONE"，而内联按钮是**我们按下去的**
（`changeShelfVisibility` 会把它读回去做判断）⇒ 改可见性前先分清"系统意图"与"我们造成的表象"。

## 3. 【P1】切歌时进度条短暂消失、两枚时间挤到左边

**机制**：`applyA11SeekVisibility` 的判据是"有会话 && duration>0 && 展开"；切歌瞬间新 duration 未到 ⇒
整行 GONE ⇒ 列按零宽重测 ⇒ 恢复时 bar（`weight=1`）拿不到剩余宽度 ⇒ 两枚时间贴合到左。

**治本三件（别只多刷布局）**：
1. 隐藏粒度降一级：只把**条**置 INVISIBLE，行与两枚时间保持占位。
2. duration 用 sticky：新曲目未知时沿用上一首，超过阈值才真的隐藏。
3. 显隐后只对 seek 行做一次定点 `requestLayout`，不要 post 一串重排。

**验收**：切歌全过程按 ≥10Hz（或逐帧）记录 `elapsed`/`total`/`bar` 的 bounds，
断言"两枚时间始终分居两端"且条宽 >0。

## 4. 【P2】其余欠跑 / 欠债

- 组开合五帧门禁本轮因设备息屏欠跑（harness 已加焦点/BACK 兜底与 `.cycle-tap.txt` 清理）。
- 切歌后约 0.9s 仍是"新文字 + 旧封面/旧底色"（vivo 先推标题、后推 largeIcon）；要压掉只能建卡时
  直接取 `sbn` 的 largeIcon（把 `sbn` 传进 `MediaCard.build()`）。
- 进度条滑块直径 10dp **没有逆向依据**（A11 两个媒体模板都没写 `android:thumb`），等用户观感定。
- B 方案（`seek=ios|a11` 做成用户可选）暂缓。

## 5. 已证伪（别再走）

- 1Hz 见证查不到 16ms 级的闪 → 必须逐帧（`betteroriginos_trace_flicker`）。
- "有实例露着但我们没登记"（0 条）、"我们代码改了别的行的图标"（0 条）、"整列表在抖"（1Hz Δ0）——三条都量到 0。
- 把原生小图标的 drawable 摘成 null：会让它 measure 成 0，header 列数学塌（L3 当场红）。
- 压视图 alpha / 可见性 / scale：vivo 每次重排都会写回，等于逐帧对写；**只有 drawable 自己的 alpha 不争抢**。
- logd 每进程 250 行/秒会整条吞掉一次性见证（建卡行、install 行）——要么低频重发，要么门禁自己重投样本。

## 6. 方法（这次反复踩出来的，写死）

1. 先确认现象的**时间尺度**，再选采样率。
2. "抓到 0 条"要分清"没发生"与"窗口没覆盖"，后者判红时要说明成因。
3. 先排环境（息屏 / 焦点 / verify 开关 / 设置页压前台），再怀疑产品。
4. **门禁绿 ≠ 用户满意**：验收对象必须和用户描述的场景**同帧、同对象**；否则就是"自证通过"。
