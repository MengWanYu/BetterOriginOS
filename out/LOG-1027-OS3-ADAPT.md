# 阶段性落盘日志 · OriginOS 3 适配（系统界面播放器 + 通知堆叠）

> **当前有效环境 = 阶段 1e 的 Z8/OS3（DSU）**。阶段 1~1d 的记录属于先用的 V1913A 真机，
> 它的结论（尤其 hook 目标可达性）在 Z8/OS3 上**必须重新核对**，不得直接沿用。

## 目标
- 交付形态：**单 APK 双支持**（OS5/Z8 行为不回退，OS3 走版本分流）。
- 播放器方向（用户已选）：**改原生 QS 磁贴**（不新建通知媒体卡）。
- 冒烟标准：注入无崩溃 → 功能开关生效 → 交互与视觉达标，每阶段结果落盘。

---

## 阶段 0 · 环境勘察（完成）

| 项 | 结果 |
|---|---|
| 连接设备 | `IBR8GEMFJNBECIFA`（Z8 `10AE4Y0MDE000BL` 未连接） |
| Root | Magisk Delta `ccb264f3-alpha(30600)`，`u:r:magisk:s0`，`/product/bin/resetprop` |
| Hook 框架 | 初始**无**：`/data/adb/modules` 空、Magisk Zygisk 未开、无 LSPosed |
| 模块 APK | 未安装（`com.betteroriginos` 不存在） |
| 本地可用安装包 | `G:\Misc\sd\A模块\LSPosed-v1.9.2-7272-Irena-release.zip`、`Zygisk-Next-1.1.0-330-a1b0e69-release.zip` |
| SystemUI | `/system/priv-app/SystemUI/SystemUI.apk` 51,452,491 B，versionName=ROM_13.5 |
| vivo 插件 | `/system/priv-app/SystemUIPlugin/SystemUIPlugin.apk` 94,051,764 B（`com.vivo.systemuiplugin`） |

## 阶段 1 · 框架安装 → **失败并已回滚**（完成）

做了：安装 `zygisksu`(Zygisk-Next) + `zygisk_lsposed`(LSPosed) 两个 Magisk 模块 → 重启。

**后果**：重启后 SystemUI 进程不存在（`pidof com.android.systemui` 空、logcat 无一行 systemui，
bootanim 已退出、system_server 存活），即"系统界面炸了"。

回滚与验证：
1. 趁开机 adb 窗口写 `disable` 标记（**未删除模块目录，可逆**）：
   `/data/adb/modules/zygisk_lsposed/disable`、`/data/adb/modules/zygisksu/disable`
2. 重启后 SystemUI 恢复：PID 5201，12s 内 3 次采样 PID 不变；`NotificationShade` 窗口可聚焦。
3. `ps -A` 无 zygiskd / lspd 残留。

Magisk 启动日志要点（事故那次）：
- `zygisk_lsposed: ignore` —— Magisk 自身 Zygisk 未开，官方通道忽略了 LSPosed 模块；
- 但 `zygiskd64`(PID 545) 与 `lspd`(PID 1094) 仍在运行 —— 即 **Zygisk-Next 自行加载了 LSPosed**；
- 崩溃时**我们的模块 APK 根本还没安装**，LSPosed 无任何作用域 → 嫌疑集中在
  「Zygisk-Next 与 Magisk Delta 双 Zygisk 实现冲突」或「Zygisk-Next 注入 vivo 定制 zygote 失败」，
  而不是模块代码本身。

**下一步策略（单变量递进，每步一次重启）**：
1. 只开 Magisk 自带 Zygisk（`zygisk=1`），保持 Zygisk-Next 禁用，装 LSPosed → 观察 SystemUI 是否存活；
2. 若第 1 步就炸 → 这台机不能上 Zygisk 系框架，改走静态适配 + 由用户在可注入环境验证；
3. 每次重启前确认 `disable` 标记位置可用（回滚成本 = 一个 touch + 重启）。

## 阶段 1b · 框架二次尝试：LSPosed 已启用但**注入为零**（当前卡点）

用户侧操作：Magisk 设置里把自带 Zygisk 打开（`key=zygisk|value=1`），保持 Zygisk-Next 禁用，
LSPosed 模块启用、模块列表里 BetterOriginOS 已勾选、作用域含 SystemUI/MediaProvider/文件管理（截图
`G:\os3-ref\os3-lsposed-ui.png`、`os3-lsposed-detail.png`，dump `os3-lsposed-detail.xml`）。

实测结论：**注入没有发生**。证据（`G:\os3-ref\probe*.sh` 输出）：

| 检查项 | 结果 |
|---|---|
| `lspd` 守护进程 | 在跑（PID 1065，uid system） |
| `/data/adb/lspd/config/modules_config.db(-wal)` | 已写入，含 `com.betteroriginos` |
| `magisk.log` 本 boot | `zygisk_lsposed: exec [post-fs-data.sh]`（说明 zygisk=1 被读到） |
| `zygote64` maps 中 `zygisk/lsposed/liblspd` | **0 命中** |
| `zygote64` maps 中 `memfd/(deleted)` 可执行段 | **0 段**（排除"加载后改名"的情况） |
| `com.bbk.launcher2` maps | 0 命中 |
| `com.android.systemui`（kill 后重启，PID 9533） | 0 命中，logcat 无 `loaded com.android.systemui` |
| SELinux | **Permissive**（排除策略拦截） |
| Magisk app/daemon 版本 | 都是 `ccb264f3-alpha(30600)`，匹配 |

即：这台机的 **Magisk 自带 Zygisk 只跑了脚本、没有把模块 .so 注入 zygote**。
（对照：上一次用 Zygisk-Next 时 `zygiskd64` + `lspd` 都起来了，说明 ZN 的注入通道是通的。）

## 阶段 1c · 上次 SystemUI 炸掉的新线索：LSPosed 在反复重启 logd

`/data/adb/lspd/log/verbose_*.log` 与 `modules_*.log` 全是：

```
Logd maybe crashed (err=Success), retrying in 1s...
Logd crashed too many times, trying manually start...
```

两次 boot（含崩溃那次，日志在 `log.old/`）都一样。这台机器的日志通道本身是残的：

- `log.tag=E`（vivo 把全局最低级别压到 Error，所有 `Log.i` 被吞）→ 已 `setprop log.tag I` + `persist.log.tag I`；
  注意该属性在进程启动时读取，**zygote 早于设置启动，所以已起进程仍被压制**，要重启才生效；
- `adb logcat -d`（shell）只有 2 行，**必须 `su -c logcat -d`**（root 下 1121 行）；
- `logd` 的 pid 在 boot 期间变过（`init.svc_debug_pid.logd=8706`，远大于 system_server）。

所以"ZN+LSPosed 那次 SystemUI 起不来"的候选原因收敛为：
LSPosed 的 logd 自愈循环（反复 `start logd`）把启动期搅坏，而不是我们的代码。

## 阶段 1d · 待做的单变量隔离阶梯（每次一步，回滚 = 写 disable + 重启）

1. **只开 Zygisk-Next，LSPosed 保持 disable** → SystemUI 是否存活？（判定 ZN 本身是否安全）
2. ZN + LSPosed（模块不启用）→ 存活？再判定 LSPosed 注入本身；若仍炸，先在 LSPosed 设置里关掉日志再试。
3. 通过后启用 BetterOriginOS + SystemUI 作用域 → 从 `su -c logcat` 里看 `loaded com.android.systemui`。

## 阶段 2 · OS3 hook 目标可达性核对（完成，见 out/OS3-TARGET-CHECK.md）

通知堆叠相关（`os3-systemui-classes.txt` 精确匹配）：

| OS5 侧使用的类 | OS3 |
|---|---|
| `statusbar.notification.row.ExpandableNotificationRow` | ✅ 存在 |
| `statusbar.notification.row.NotificationContentView` | ✅ 存在 |
| `statusbar.notification.stack.NotificationStackScrollLayout` | ✅ 存在 |
| `statusbar.notification.stack.StackScrollAlgorithm` | ✅ 存在 |
| `statusbar.notification.collection.ShadeListBuilder` | ✅ 存在 |
| `statusbar.notification.collection.GroupEntry` | ✅ 存在 |
| `statusbar.NotificationGroupingUtil` | ✅ 存在 |
| `statusbar.NotificationShelf` | ✅ 存在 |
| `SystemUIApplication` | ✅ 存在 |
| `statusbar.phone.LegacyNotificationIconAreaControllerImpl` | ❌ **不存在**，OS3 为 `statusbar.phone.NotificationIconAreaController` + `com.vivo.systemui.statusbar.phone.VivoNotificationIconAreaController` |

方法级核对（`work/os3-probe.py` → `out/OS3-TARGET-CHECK.md`，24 类 / 缺 7 类 / 缺 1 方法，
其中 4 个"缺失类"是脚本对包名字符串的误抓与 framework 类，真正要改的是下面 3 处）：

1. **`NotificationShelf.setShouldShowShelfOnly` 在 OS3 不存在** → OS3 该方法在
   `NotificationStackScrollLayout`（`changeShelfVisibility` 两边都有）。屏蔽"装不下压成收纳栏"的 hook 必须换宿主类。
2. **图标守卫类名不同**：OS3 是 `statusbar.phone.NotificationIconAreaController`
   （有 `updateIconsForLayout`）+ `com.vivo.systemui.statusbar.phone.VivoNotificationIconAreaController`
   （有 `updateVivoIconsForLayout`）；OS5 的 `Legacy…Impl` 两个类在 OS3 全部不存在。
3. **子项取法可以更干净**：OS3 `GroupEntry` 有 `getRawChildren`（OS5 反而没有），
   行上没有 `getNotificationChildCount` → stack overlay 取子通知要走 GroupEntry。

已确认可用（OS3 全在）：`NotificationContentView` 的 `setContractedChild/setExpandedChild/setHeadsUpChild/
calculateVisibleType/setHeights/onMeasure` 与 `mContractedChild/mExpandedChild/mHeadsUpChild/mVisibleType/
mIsHeadsUp/mContainingNotification/mContractedWrapper`；7 个 `*ViewWrapper.onContentUpdated`；
`ShadeListBuilder.applyFilters/filterNotifs`；`NotificationGroupingUtil.sanitizeTopLine`；
`StackScrollAlgorithm.initView`；NSSL 的 `reinitView/updateCornerRadius/updateSidePadding/onSystemFilletChanged`
与 `mSidePaddings/mCornerRadius/mStackScrollAlgorithm`；行的 `setUserExpanded/setChildrenExpanded/
isGroupExpanded/getChildrenContainer/updateLimits/notifyHeightChanged/getIntrinsicHeight`。

真机无框架取证（把模块当普通 App 装，`am start --es debug_notify postall` 投 8 条通知）：
OS3 折叠视图树里出现 `com.android.systemui:id/expandableNotificationRow`、
`notification_children_container`、`notification_stack_scroller`、`notificationShelf`、
`com.android.systemui:id/notification_title|notification_text`、`android:id/app_name_text`、
`android:id/expand_button(_pill|_number)`、`vivo:id/header_button_container`、
`com.vivo.systemuiplugin:id/qs_panel_header` 等 → 行容器/子容器/收纳栏三件套都在，堆叠方案在 OS3 成立。
证据：`G:\os3-ref\os3-shade-group.xml`、`os3-shade-group.png`。

**未决**：MediaCardFeature 的媒体卡取证需要 MediaStyle 通知，已给调试器加 `postmedia`
（自建 MediaSession + 封面 + 播放态），但**本机默认 javac 是 Liberica JDK 8**，
`Bitmap.Config.ARGB_888` 与 `MediaSession.getMediaSessionToken()` 报"找不到符号"
（`work/tmp-javac/T.java` 最小复现；符号在 android.jar 里确实存在）→ 待换 JDK 或绕开这两个符号。

取证产物：
- `G:\os3-ref\OS3-SystemUI.apk`、`G:\os3-ref\OS3-SystemUIPlugin.apk`
- `G:\os3-ref\os3-systemui-classes.txt`、`G:\os3-ref\os3-plugin-classes.txt`、`os3-dexdump-f.txt`(214MB)
- dex 解包目录 `G:\os3-ref\dex\`；探针脚本 `G:\os3-ref\probe*.sh`

## 阶段 1e · 环境切换到 Z8 / OriginOS 3（DSU 会话）—— 这才是有效环境

| 项 | 值 |
|---|---|
| 设备 | iQOO Z8（`10AE4Y0MDE000BL`，V2314A / PD2314），**DSU 内**跑 OriginOS 3 |
| 版本 | `ro.vivo.os.build.display.id=OriginOS 3`，`ro.vivo.os.version=13.5`，Android 13 / API 33，patch 2023-11-01，kernel 5.10.233-android12 |
| 宿主 | OS5 / Android 15（`PD2314_A_15.2.18.0.W10`）；**/data 不与 DSU 共享** |
| Root | KernelSU（`/data/adb/ksu`）+ Zygisk-Next + LSPosed |
| LSPosed | **2.2.1 (7930)，API 102**，`Dex 优化器包装器: 支持(已注入)`，`Xposed API 调用保护: 已启用` |
| 系统 APK | `/system/priv-app/SystemUI/SystemUI.apk` 51,460,786 B（md5 d48c3f4c…）、`SystemUIPlugin.apk` 94,060,064 B |

注入链路已实测打通（`su -c "kill $(pidof com.android.systemui)"` 后）：

```
loaded com.android.systemui/com.android.systemui
notify_row: icon layout guards installed=0      ← 装机版还是旧类名表，OS3 名单在源码里未构建
notify row feature installed / installed feature notify_row
media card feature installed / installed feature media_card
preview observer registered
notify_row: schedule mode=0 tag=base cls=FrameLayout   ← 重排流水线在跑，enabled=false 时只观察
```

这台机的操作要点（与 V1913A 不同，别混用）：
- `log.tag=I` 已经是开的，但 **shell 读 logcat 为空**，一律 `adb shell su -c "logcat -d -v time"`；
  个别时刻 `-d` 也拿不到，用 `su -c "timeout 3 logcat"` 流式取。
- kill SystemUI 必然锁屏；解锁要 `keyevent 224` + **两次**上滑，否则 uiautomator dump 只拿到状态栏。
- 这台机的 `lspctl` 只有 `status` / `stop`（提示 "Enable developer mode for full function"），
  **不能** `module enable` / `scope set` —— skill 里那条命令对本版 LSPosed 2.2.1 无效，模块开关只能在管理器 UI 里点。
- 管理器 activity：`org.lsposed.manager/.ui.activity.MainActivity`；底部导航 4 项在 uiautomator 里 bounds 全为
  `[0,0][0,0]`，只能按几何点（模块页 ≈ (405,2230)，模块卡片 ≈ (540,700)），点完用截图确认。

**我犯的事故**：没确认 DSU 就 `adb reboot`（本意是把崩掉的 lspd 拉起来），机器直接掉回 OS5 宿主，
用户手动回切 DSU。教训已写进记忆（pre-flight DSU 检查 + /data 不共享）。以后：**要重启先问，不自己重启**。

取证目录换到 `G:\z8os3-ref\`（Z8/OS3 专属）；`G:\os3-ref\` 里的 V1913A 结论保留但标记为"待重核"。

## 阶段 2' · 用 Z8/OS3 的 dex 重做目标核对（完成）

`work/os3-probe.py module/src/com/betteroriginos G:\z8os3-ref\z8os3-dexdump-f.txt out/Z8OS3-TARGET-CHECK.md`
→ 涉及类 26 / 缺失类 7（其中 4 个是脚本误抓的包名字符串与 framework 类）/ 缺失方法 1。
逐成员复核（直接查 `z8os3-dexdump-f.txt`）与 V1913A 的三条结论**完全一致**：

| 目标 | Z8/OS3 实测 |
|---|---|
| `NotificationShelf` (90 成员) | `changeShelfVisibility` ✅ / `setShouldShowShelfOnly` ❌ |
| `NotificationStackScrollLayout` (689) | `setShouldShowShelfOnly` ✅、`onLayout` ✅、`dispatchDraw` ✅ |
| `NotificationIconAreaController` (94) | `updateIconsForLayout` ✅ |
| `VivoNotificationIconAreaController` (16) | `updateVivoIconsForLayout` ✅ |
| `GroupEntry` (15) | `getRawChildren` ✅ |
| `ExpandableNotificationRow` (513) | `setUserExpanded/setChildrenExpanded/isGroupExpanded/getChildrenContainer` ✅ |

## 阶段 3 · 代码分流（已落地并装机验证）

1. `core/OsProfile.java`：主判据 `Build.VERSION.SDK_INT`（≥34 → 通知行媒体卡；≤33 → QS 磁贴），
   OriginOS 名字只用于日志；`report()` 打一行画像供冒烟核对。
2. **插件包直接可见，不需要钩 ClassLoader**：LSPosed 2.2.1 会对跑在 SystemUI 进程里的
   `com.vivo.systemuiplugin` 单独回调 `handleLoadPackage`，所以
   `Feature.install(cl, packageName)` 新重载 + `MediaCardFeature.scopes()` 加插件包即可；
   一度写的 `LateClass`（钩 `ClassLoader.loadClass` 等类出现）已删除——热路径钩子没必要。
   `arrays.xml` 的 `xposedscope` 同步加了 `com.vivo.systemuiplugin`。
3. `NotifyRowFeature`：图标守卫类名表补 OS3 两个类；`setShouldShowShelfOnly` 同时挂到 NSSL
   （谁有方法谁生效，不做版本分支）。装机后 `icon layout guards installed=2`（旧包是 0）。
4. `feature/media/QsPlayerTile.java`（新）：OS3 播放器磁贴进度条。挂在
   `ControlCenterMediaPlayerView.onFinishInflate`，attach 时按 `media_card_qs_seek` 开关决定加条；
   子 View 的 LayoutParams 由磁贴自己的 `generateLayoutParams`（protected，反射调）生成再摆底部约束；
   三个按钮父容器 `bottomMargin` 抬 10dp 让位；进度/时长走 `MediaSessionManager.getActiveSessions(null)`
   （与 OS5 卡片同一判据：先按磁贴 `musicName` 文本匹配，再挑 PLAYING），seek 先走
   `MediaController.getTransportControls().seekTo`，不支持再退回 `com.vivo.musicwidgetmix` 的
   `seekPos`（AIDL 事务号 15，与 OS5 相同；getPosition=13 / getDuration=14）。
5. 新增选项 `media_card_qs_seek`（默认关，待真机验证后再默认开）。
6. 构建期踩到的两处：`XC_MethodHook.Unhook.unhook()` 桩缺（已补）；
   `ViewGroup.MarginLayoutParams.setBottomMargin` 在这份内部 android.jar 里没有 → 改直接赋值字段。

装机确认（Z8/OS3）：

```
os profile: OriginOS 3 / Android 13 (sdk 33) mediaShade=qs_tile
notify_row: icon layout guards installed=2
qs player tile installed on com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView
installed feature media_card for com.vivo.systemuiplugin
```

## 阶段 4 · 冒烟第一轮（Z8/OS3，分离 + 堆叠）

环境注意：`vivocameraserver` 在这台 DSU/OS3 上每 ~5s 崩一次（HIDL `BAD_TYPE`，与我们无关，
但会污染 crash buffer）；SystemUI 全程未重启（PID 稳定）。

| 用例 | 结果 |
|---|---|
| 分离模式（group_mode=separate） | ✅ 视觉与几何达标：38dp 图标、16/8/20 参数、标题 2 行/正文 4 行截断，`restyled style=base/big single` |
| 收纳栏压制 | ✅ `shelf changeShelfVisibility(true) denied`，`NotificationShelf h=0` |
| 堆叠模式（ios_stack） | ✅ 结构达标：`stack=1 groupExpanded=0`、bars=min(n-1,2)、peekPx=bars×42、badgeR=27(9dp)、badgeTotal=条数、单行标题/正文；SystemUI 不崩 |
| 堆叠视觉 | ⚠️ 露边在截图上像一整块圆角底板而不是"逐层露边"，且部分 2 条组（Xposed）看不到露边 —— 需真机眼睛判断 |
| 非分组的单条通知 | ❌ **Bug**：`com.android.dynsystem` 行标题被隐藏（`titleVis=8 titleW=0 titleLines=0`，`nAppTimeGap=-649` 负值），只剩 app 名 + 正文，看起来像没被改造 |

证据：`G:\z8os3-ref\smoke1-separate.png/.xml`、`smoke2-stack.png`。

**下一步**：查标题被隐藏的判定逻辑（`NotifyRowFeature` 里 restyle 决定 title 可见性的分支），
在 OS3 上为什么对"无 group 子容器"的行走了隐藏路径；修完再回到堆叠露边的视觉核对。

## 阶段 4b · 圆角根因与修复（已装机，待用户目测）

用户反馈：**单条通知在 OS3 上没有 OS5 那样的圆角，变成长方形很难看**。

根因（dex 证据，`G:\z8os3-ref\z8os3-dexdump-f.txt`）：
- OS3(A13) 的行圆角由 `ExpandableNotificationRow.setTopRoundness(float)/setBottomRoundness(float)/applyRoundness()`
  驱动（像素值），背景侧是 `NotificationBackgroundView.setRadius/mCornerRadii`；
- A15 上 NSSL 的 `mCornerRadius` 会传导到行，A13 **不传导** → 我们只设了 `mCornerRadius=60px(20dp)`，
  行背景仍是直角，所以"卡片是方的"；堆叠卡看起来圆是因为那是我们自绘的 overlay。

修法（`NotifyRowFeature`）：
- 新增 `applyRowRoundness(row, px)` / `applyRoundnessToRows(stack, px)`；
- `applyStackMetrics()` 里设完 `mCornerRadius` 后，对 NSSL 的每个 `ExpandableNotificationRow` 子行补上下 roundness；
- restyle 路径里对当前行也补一次（行是回收复用的，只靠 NSSL 遍历会漏新绑定的行）；
- 用 `OsProfile.isQsTilePlayer()` 限定只在 OS3 侧生效，OS5 行为零改动。

装机确认：dex 里含 `setTopRoundness`，SystemUI 未崩（PID 20009）。

## 阶段 4c · 圆角第二轮（截图证据 G:\z8os3-ref\smoke5-radius.png）

补 `setBackgroundRadius()`（往子树里所有 `*.NotificationBackgroundView` 调 `setRadius(20dp)`）后：
- ✅ **组/堆叠卡**（带条数徽标那几张）圆角 + 露边都正常了；
- ❌ **非分组的单条通知**（"互传"、"Dynamic System Updates"）**仍是直角满宽**。
  推断：vivo OS3 的行背景不是 `NotificationBackgroundView` 这个类名（可能被改名或用了 vivo 子类），
  `endsWith(".NotificationBackgroundView")` 没命中 → 下一步改成"只要有 `setRadius(float)` 方法就打"
  （鸭子类型），并把命中类名打进日志，别再猜类名。

**另一处需要推翻的结论**：这台 Z8/OS3 的通知列表里**确实有媒体卡**
（截图顶部"美人鱼 / 林俊杰 / 2:23 —— 4:14 / 上一首·暂停·下一首·收藏"，原生就带进度条）。
所以"OS3 把媒体通知收走、播放器只在 QS 磁贴"是 **V1913A 的现象，不适用于 Z8/OS3**。
`OsProfile` 现在按 SDK≤33 一律判成 `qs_tile`，在 Z8/OS3 上会走错路线——
媒体 UI 的落点必须改成**运行时探测**（看通知列表里是否出现媒体模板行 / wrapper 是否 Media），
而不是按 SDK 猜。SystemUI 稳定（PID 7477，三次采样不变；crash buffer 里只有固件自带的
`vivocameraserver` 每 5s 崩一次，与我们无关）。
**待办**：① 用户目测圆角是否恢复；② `com.android.dynsystem` 那条"标题不显示"经核对是
该通知本身只有 EXTRA_TEXT 没有 title（`flags=0x60 importance=2`），我们的"无标题→只显示正文"
判定是对的，不是 bug，但需要确认视觉上是否该保留 app 名行；③ 堆叠露边的观感；④ QS 磁贴进度条
（`media_card_qs_seek` 默认关，需开启后单独冒烟）。

## 阶段 4d · 圆角已修好；新发现缺陷：通知动作按钮丢失

- ✅ 圆角：钩住 `ExpandableNotificationRow.setTopRoundness/setBottomRoundness`，在 before 里把参数
  钉成 20dp（feature 开 + `stack_metrics=ios` 才生效，`OsProfile.isA13OrLower()` 限定，OS5 零改动）。
  装机日志 `notify_row: roundness pinned`，截图 `G:\z8os3-ref\smoke6-radius2.png`：单条通知四角 20dp 圆角。
  根因不是"背景视图半径"，而是**手动设一次会被原生每轮布局重算冲掉**，必须拦 setter。
- ⚠️ 死代码：`setBackgroundRadius` 的鸭子类型探测一次都没命中（无 `card background view = ...` 日志），
  说明 A13 的 `NotificationBackgroundView.setRadius` 不是 public，圆角完全走 roundness 管线 → 这段该删。
- ❌ **待修（用户报）**：`Dynamic System Updates`（"目前正在运行动态系统…"）这条通知原生左下角有两个
  动作按钮，被我们重排后**不显示**。代码里没有任何主动隐藏 action 的分支
  （`grep action` 只有 `media_actions` 判定），所以是高度/可见性管线的副作用：
  我们把行高压成 contracted 子 View 的高度并强制 `calculateVisibleType=0`，
  收起态带按钮的布局（AOSP 的 `notification_action_list` 在收起态内联显示）因此被裁掉/不测。
  取证未完成：uiautomator dump 里连一个 action 节点都没有（需先在真机上确认这条通知还在、
  并抓 `android:id/action_list` 与按钮节点的 bounds/visibility）。

## 阶段 4e · 动作按钮丢失：已排除项与下一步线索（未修完）

试过的两条都没解决：
1. `calculateVisibleType` 不再对"有内联动作行"的通知强钉 0 —— 无效；
2. 把 `hasInlineActions` 从"只在 contracted 子树找 `android:id/action_list`"改成"整棵 contentView 找" ——
   **探测日志一次都没出现**，说明这条通知的 contentView 里根本没有 `android:id/action_list` 这个 id
   （vivo OS3 的收起态按钮不走 AOSP 那个容器名）。

当前最可疑的点（下一轮先查这个）：`hideSmallIcons()` 被用在了**整个模板根**上
（`NotifyRowFeature.java:1320`、`:1563`、`:3149` 传的是 `root`；`:2894` 传的才是 header），
而它的命中规则包含 `name.endsWith(":id/icon")`（见 `collectHideTargets`，约 `:2939`）。
只要 vivo 的收起态按钮内部带 `android:id/icon`，就会被我们整片 GONE 掉 ——
表现正好是"左下角两个按钮没了"。

修法候选（按代价从低到高）：
- 把 `hideSmallIcons` 的调用范围收敛到 header（1320/1563/3149 三处改传 header，或对 root 调用时
  跳过父链里出现 `Button`/`action` 的子树）；
- 或在 `collectHideTargets` 里加一条：`v instanceof Button || 父链含 action` 就不隐藏。

取证补齐（一次构建就能定案）：在 `notify_verify=1` 时对 dynsystem 行打印整棵子树的
`类名 + resource-id + visibility + height`，确认按钮容器到底是什么类/id。

本轮其它状态：圆角修复有效（smoke6），SystemUI 稳定（最后 PID 14846），无崩溃回归。
最后一次截图（smoke9）拍到的是音乐 App 广告页——面板当时没在前台，说明**真机正在被人使用**，
下轮抓证前要先确认界面状态再动手势。

## 阶段 5 · 全功能 OS3 化 + 一键清除崩溃面（代码已落地并装机，真机回归待窗口）

目标扩为：**四个功能全部适配 OS3** + 修 **OS5 一键清除导致 SystemUI 重启**。
先说清一件事：OS3 通知面板**没有 OS5 那枚圆点"一键清除"**，但清空动作本身走的是同一条
`NotificationManagerService.dismissAll` → 批量移除 → SystemUI 侧同一串回调。
所以验证入口用 `cmd notification dismiss_all`（等价、可脚本化），不依赖圆点是否存在。

### 5a 崩溃根因（两份代码审计 + 逐条复核，按可疑度排序）

1. **`StatusBarNotification.getGroupKey()` 被抹成 null**（旧 `NotifyRowFeature.java:512-519`，
   分离模式每次绑定都生效）。批量移除时 `GroupMembershipManager` / `HeadsUpCoordinator`
   拿 null key 去查表 → NPE 崩溃循环。`out/DEVLOG.md` 2026-10-26 22:27 已经记录过这个现象并写"已回退"，
   **但代码里根本没删** → 已删除该钩子，只保留 `isGroup()→false` + pipeline 侧摘 summary/拆组。
2. **pre-draw 监听与 post 出去的延后任务不在 Xposed 异常保护范围内**：
   `guardStack`/`guardOverlay`/`guardSmallIcons` 的 `onPreDraw`，以及 8 处 `post/postDelayed`
   （restyle 重试、autoExpandGroup、hideSmallIcons、两个 verify 重试、dump）原先全部裸奔。
   清空瞬间视图树正在拆/换实例，一次异常 = SystemUI 重启 → 统一收进 `safe(Runnable)` /
   `preDrawGuard(Runnable)`（`NotifyRowFeature` 新增，紧邻 guardStack）。
3. **`guardOverlay` 在卡片被摘掉后仍每帧 `root.setAlpha(0)`**：行被回收给别的通知时会把整行压透明
   （"清空后通知看不见"的来源）→ 加 `OVERLAYS.containsKey(contentView) && overlay.getParent()!=null` 早退。
4. `verifyStack` 里 `title.getLayout().getLineCount()` 未测量时 NPE → 换 `lineCount()`（取不到报 -1）。

### 5b OS3 动作按钮丢失（阶段 4e 定案）

装机日志直接给出答案：

```
notify_row: inline action FrameLayout id=android:id/actions_container vis=8 h=0 kids=1
```

- **vivo OS3 收起态动作容器是 `android:id/actions_container`**；AOSP 老名字 `android:id/action_list`
  在这台机上不存在 → 之前"整棵 contentView 找 action_list"必然 0 命中，
  `calculateVisibleType` 的逃生口从未生效（阶段 4e 的"排除项 1 无效"由此解释）。
- 修法：`hasInlineActions` 改成**按行作用域**判定，先 `findViewById(actions_container)` 直取，
  取不到再走结构信号（`Button` 本体 / id 含 `:id/action` / `:id/button1..3` / 类名含
  `ActionList|ActionButton|ButtonsRow|ActionContainer`，展开箭头排除）。
  每轮 measure 都会问，所以结果按行实例缓存（`ACTION_ROWS`）并在 rebind 时失效。
- `collectHideTargets` 的动作子树剪枝换成同一个 `isActionNode`：命中即整棵子树不收集、
  不清 compound drawable —— 堵住"`:id/icon` 规则把按钮内图标藏掉"。
  注意 `applyHidden` 是 alpha0 + `importantForAccessibility=NO`，所以 uiautomator 里连节点都没有，
  与现象完全吻合（不是 GONE）。
- 附带修正：补行圆角的判据从 `OsProfile.isQsTilePlayer()`（按 SDK 猜媒体落点）换成 `isA13OrLower()`。

### 5c 另两个功能的 OS3 可达性（后台取证，产物 `out/Z8OS3-PICKER-FILEMAN-CHECK.md`）

- **PickerAlbums：可达，但此前在 OS3 上整个功能没装上**。`findAndHookMethod(queryAlbums, String[].class)`
  在 A13 抛 `NoSuchMethodError`（A13 只有 `(String)` / `(String,UserId)` 重载），而它与 `appendWhere`
  钩子在同一个 try 里 → 一起没装。另外 `appendWhere` 第三参 A13 是单个 String mime，
  源码按 `(String[]) args[2]` 取会 CCE。
  已修：两个钩子各自独立 try + 改用 `hookAllMethods`，`mimeArg()` 同时吃 String[]/String，
  并打 `picker albums patch installed hooks=N` 作为生效证据。
  其余依赖（`ExternalDbFacade`、`LOCAL_ALBUM_IDS`、`TABLE_FILES`、`mDatabaseHelper`、
  `runWithoutTransaction`、`media_type/is_trashed/is_pending/relative_path` 列）A13 全部命中。
- **FileManagerRename：OS3 上目标类不存在**，A13 版文件管理（6.11.4.4）被 R8 重混淆，
  `RenameDialogFragment` / `smb.bean.ShareFile` / `com.originui.widget.edittext.VEditText`
  与混淆锚点 `f11819f/f11309e/f11306b/f11314j` 全 0 命中 → 需重新逆向取符号（另开一轮，
  产物 `out/Z8OS3-FILEMAN-RENAME-RE.md`），在此之前它在 OS3 上不可能生效。

### 5d 装机确认（Z8/OS3 DSU，SystemUI PID 21314，全程无崩溃）

```
loaded com.android.systemui/com.android.systemui
notify_row: roundness pinned
notify_row: icon layout guards installed=2
os profile: OriginOS 3 / Android 13 (sdk 33) mediaShade=qs_tile
media card feature installed
notify_row: inline action FrameLayout id=android:id/actions_container vis=8 h=0 kids=1
```

截图 `C:\Users\Administrator\AppData\Local\Temp\z8os3\shade1.png`：列表内原生媒体卡（封面+进度+四键）、
组卡带条数徽标与露边、单条卡 20dp 圆角 —— 再次印证 **Z8/OS3 通知列表里确实有媒体卡**，
`OsProfile` 按 SDK 判 `qs_tile` 只是"磁贴存在"的判据，不能当成"列表无媒体卡"的判据。

### 5e 真机回归结果（用户已授权占用设备 + 允许清空通知）

**一键清除崩溃回归：通过。** OS3 上没有 `cmd notification dismiss_all`（AOSP 新命令，A13 没有），
也没有任何原生"一键清除"控件（面板 dump 142 个节点里无清空入口）→ 改用等价的批量移除：
`debug_notify cancel`（一次 cancel 掉我们全部测试通知）与 `postall` 交替，面板保持打开，
**separate 与 ios_stack 两种模式各 3 轮**：

| 指标 | 结果 |
|---|---|
| SystemUI PID | separate 24265 → 24265；ios_stack 25821 → 25821（全程不变） |
| crash buffer 里 systemui | 0 条 |
| 我们兜住的异常（pre-draw guard / deferred task / stack guard / icon guard） | 0 条（连一次都没触发） |

根因 1（`getGroupKey→null`）与根因 2（未捕获回调）已删除/封住；OS5 宿主的圆点回归按用户决定**暂不做**
（切系统会丢 DSU 会话），共享代码已修，留待回宿主时一次性验证。

**动作按钮（阶段 4e）定案 —— 之前两轮的判定前提都是错的：**
1. uiautomator 在这台机上**根本不导出通知行**（面板明明有 8 张卡，dump 只有 142 个节点且
   `Button`/`actions_container` 计数为 0）→ "dump 里连一个 action 节点都没有"不是证据，是工具盲区。
2. A/B（同一条 `com.android.dynsystem` 行，`feature_notify_row` 开/关各重启一次）：
   **原生 OS3 收起态也没有动作按钮**，按钮在展开态，入口是行右侧那枚 ⌄ 箭头。
   所以真正的缺陷不是"我们把按钮藏了"，而是**我们把展开箭头藏了 → 按钮永远够不着**。
3. 修法（`hideSmallIcons`/`collectHideTargets`/`rejectExpand`/restyle 四处联动）：
   带动作区的行**保留展开箭头**（不收集、且把早先被压的 alpha/IFa/clickable 反向还原
   `restoreExpandButton`），并且不拒绝它的展开请求、不强制收起。
4. 我这次改动自己踩的三个坑（都靠真机现场见证抓出来，记录以免重蹈）：
   - `hasInlineActions` 传进来的是**模板根**不是 contentView，`rowOf()` 解析不到行 → 改成先 `rowAncestor` 爬父链；
   - 首轮扫描赶在动作区 inflate 前，**负结果被缓存**成永久假阴性 → 只缓存正结果；
   - `getIdentifier("actions_container","id","android")` + `findViewById` 这条快路径在这台机上量不到
     （通知视图的 context 是发通知 App 的 resources），而同一棵子树的 DFS 每帧都能命中 → 删快路径只留 DFS。
5. 生效证据（`notify_row.witness`，同一行，点箭头前后）：
   `act=4x138/1 b0:LinearLayout(v0,h138,a100)` → `act=0x138/1`（INVISIBLE→VISIBLE），
   截图 `work/z8os3-live/fix2.png`（箭头回来了）→ `work/z8os3-live/tap1.png`（展开后"重启"等动作按钮出现）。
   ✅ **溢出已修**（同一轮内）：`NotificationContentView.onMeasure` 的"按收起子 View 高度钳"对
   带动作区的行整条跳过（`NotifyRowFeature.java:259-264`），行高交回原生决定。
   实测 dynsystem 行 `h=256 → 352`，展开后"重启"按钮落在卡片圆角内
   （`work/z8os3-live/fix3.png`），收起态其它行高度无回归；crash buffer 仍 0。
   （见证里 `act=4x138` 与截图不一致的那一帧是过渡中间态，真正画出来的是子
   `LinearLayout(v0,h138,a100)`。）

### 5f 操作纪律（又踩一次）

我在 `mCurrentFocus=com.android.bbkmusic` 的情况下发了两次 swipe，直接落进了用户的音乐 App
（截图 `shade3.png` 是 QQ 音乐广告弹窗 + 状态栏"手机异常"提示）。**动手势前必须先看 mCurrentFocus，
设备在被使用时只做只读取证（logcat / 截图），需要输入操作的验证一律先问用户。**

### 5g 遗留清单（10-28 10:43 刷新，以本轮实测为准）

**OS3 侧已完成（有真机证据）**：通知行 iOS 化（浅色/深色 × 堆叠/分离 × 字号 1.0/1.3 全绿）、
媒体卡在列表内的渲染与 seek、QS 磁贴钩子装载（已进闸门断言）、相册选择器（本机无落点，
已在设置 App 说实话）、文件管理钩子装载 `os=a13 hooks=2`、
一键清除崩溃类（根因删除 + 同类主线程回调全兜底 + `setResult(null)` 全为 void +
逐条清除能力进断言）、OS3 无一键清除圆点已实测确认。

**仍缺证据的（全部需要用户动作，不是代码问题）**：
1. **OS5 宿主的圆点一键清除实测** —— 需要重启切系统（用户已明确暂不切以保 DSU 会话）。
   共享代码侧：根因已删、同类面已收口、OS3 等价路径多轮矩阵全绿。
2. **文件管理 >56 字符重命名的功能验证** —— 钩子已装；卡在首次启动的隐私同意页（不代点）。
3. **QS 磁贴进度条与拖动** —— 钩子已装且闸门断言；vivo 磁贴不认我们自建的调试会话，需真实播放。
4. **heads-up 实测** —— 已排除"全局开关被关"（见 5y），缺的是"设备空闲且前台为 launcher"的窗口。
5. **堆叠露边观感**（浅色与深色截图都已有）—— 需要用户目测。

**已定案、不再列为待办**：相册选择器在本机没有落点；`com.bbk.appstore` 端距 132px
（density=3.0 下与"有箭头却被当普通行判"的分类竞态自洽，见 5g-5a/6c）；
`updateSeekable` 一类兜底不是 OS5 事故根因（`MediaController` getter 自吞 RemoteException）。

### 5g-旧 遗留清单（历史版本，保留备查）

1. **OS5 宿主的圆点一键清除实测** —— 需要重启切系统，用户决定暂不切；
   共享代码的根因已删（`getGroupKey→null`）+ 回调异常已兜 + OS3 等价路径 3 轮批量移除 0 崩溃。
2. **文件管理 >56 字符重命名的功能验证** —— 钩子已装（`os=a13 hooks=2`，三个进程），
   但文件管理首次启动有隐私弹窗，需用户点"同意"后才能测。
3. **QS 磁贴进度条与拖动** —— 钩子装载已确认；vivo OS3 磁贴不认我们自建的调试 MediaSession，
   需要真实播放（用户放歌）才能看效果。
4. **heads-up 在 OS3 的实测** —— 钩子目标（`setHeadsUpChild`）在 OS3 存在；
   已排除"全局开关被关"（`heads_up_notifications_enabled=1`、我们渠道 HIGH，见 5y），
   剩下的阻塞是**取证的界面状态**：需要设备空闲且前台是 launcher。
   目前文件管理的首启同意弹窗正占着前台（等用户点"同意"，同时也能解掉第 2 项）。
5. **堆叠露边观感**（"一整块圆角底板"而非逐层露边）—— 需要用户目测。
5a. `com.bbk.appstore` base 行右端距 132px（当时期望 48 上下）—— 原始日志已被后续跑覆盖、
   **无留存证据**；按现有代码复核，更可能是**校验器的分类竞态**而非模块缺陷：
   `expand=`/`actH=` 与 `timeEndGap=` 同在一条 verify 行里，但 verify 发生在绑定瞬间，
   箭头尚未测量，故"实际有箭头、按 16dp+30dp 预留了 46dp(≈126px) 却被判成普通行（期望 16dp≈44px）"
   正好能解释 132 这个数。**不去改判定阈值凑通过**；等下一次真实 appstore 行落盘时，
   `grep timeEndGap=13[0-9]` 同时看同一行的 `expand=`/`actVis=` 即可一步定案。
6. 相册选择器在 OS3 **没有落点**（本机无可达的 Photo Picker，已用 `picker albums landing no-entry`
   把这件事变成日志事实），不再列为待办。

### 5h 两条补充结论（本轮内定案）

1. **动作区豁免已按版本关门**：新增 `needsExpandForActions()` = `OsProfile.isA13OrLower() && hasInlineActions()`，
   5 处调用点（`calculateVisibleType` 逃生口、`contentView.onMeasure` 高度钳、`rejectExpand`、
   restyle 强制收起、`hideSmallIcons` 保留箭头）全部改走它。
   原因：OS5(A15) 收起态本来就内联显示动作按钮，"保留箭头/不钳高度"在 OS5 上是**未验证的行为改变**；
   关门后 OS5 走的代码与已发布验证版逐行一致（顺带省掉 OS5 每轮 measure 的子树扫描）。
   OS3 侧复验：`act=4x138 → act=0x138`、`h=256 → 352`，展开后按钮仍在卡片内（`gate1.png`）。
2. **PickerAlbums 在 Z8/OS3 上没有落点**：`android.intent.action.PICK_IMAGES` 无任何解析
   （`cmd package resolve-activity` → No activity found）、`GET_CONTENT image/*` 被 vivo 路由到
   DocumentsUI 自己的 `picker.PickActivity`，直接点 `photopicker.PhotoPickerGetContentActivity`
   报"Activity class does not exist"；真机跑一次选择器后 `refresh: table` 0 条 →
   `ExternalDbFacade.queryAlbums` 从未被调用。钩子仍装（`hooks=2`，Mainline 一旦升级就有用），
   但空转不再静默：新增延后 4s 的落点自检，实测打
   `picker albums landing no-entry`（OS5 上同一行会是 `reachable`）。
   → 这台机的"相册 tab 只显示三个相册"问题在 OS3 上不存在（没有那个选择器），
   若用户在 OS3 别处看到类似现象，那是 DocumentsUI roots 的另一条代码路径，需另立目标。

### 5i 校验器在 OS3 上跑通并给出动作行的严格规则

1. `work/verify_notify.py` 原来只会 `adb logcat -d`：多设备 + OS3 上 shell 读不到日志 → 跑不动。
   改成认环境变量 `BO_LOG_FILE` / `BO_ADB_SERIAL` / `BO_ADB_ROOT`（不改默认行为）。
2. 校验器按 OS5 时代的规则在 OS3 上报 13 条 FAIL，逐条查下来只有 1 类是真缺陷：
   - **动作行的三条设计例外**（保留展开箭头、行高含动作带、时间右移要给箭头让位）
     → 换成等价的新断言而不是跳过：`expand=1` 作为动作行标记，断言
     `rowH > contractedH` 且多出的量在 100~260px、`contentH == rowH`、
     `timeParent=NotificationHeaderView` 且 `timeEndGap == 16dp+30dp`。
   - **文字右端距 60px（非动作行 48px）**：A13 动作行模板自带 4dp 箭头槽，
     写成 `dp(16)+dp(4)` 的期望值并保留 ±3px 容差（变了要重新解释，不是放宽成"任意"）。
   - 修掉一条自造假告警：`nAppTimeGap` 断言原本在"模板没有时间位"的行也会跑，
     量出 -341 这种噪声 → 时间位存在时才断言。
3. 我这次"保留箭头"引入的真缺陷已修并复验：时间先是不再右移（app 名与时间挤在一起、
   端距 535），改成**右移但让出 30dp 箭头**（`moveTimeRight` 加 endDp 参数，
   且行回收复用时也要更新已有 margin）。截图 `work/z8os3-live/verify7.png`：
   互传行"互传 … 昨天 22:51 ⌄"，时间与箭头都不挤。
4. **OS3 代码级校验：PASS**（`checked 1 contracted rows, 4 stack rows, 5 verifier lines`，
   日志 `work/z8os3-live/verify7.log`）。同一份校验器跑旧 OS5 日志，
   失败项与改动前逐条一致（2 条 bodyColor 历史项）→ 政策改动没有影响 OS5 口径。

### 5j 删钩子后的分离模式回归 + 本轮内修掉的两处缺陷

1. **删掉 `getGroupKey→null` 后 separate 模式仍然拆得干净**（那是崩溃修复唯一的回归风险）：
   `group_mode=separate` + 重启 SystemUI + `postall` →
   `notify_row.stack count=16 summaries=0 groupEntries=0`，所有行以 `style=… single` 重排
   （没有 groupChild），截图 `work/z8os3-live/sep-shade.png` 每条独立卡、无组头无条数徽标。
   测完已把 `group_mode` 还原成用户原值 `ios_stack`。
2. **本轮改动引入的视觉缺陷已修**：保留展开箭头后，箭头与我们右移的时间文字叠在一起
   （互传行的 ⌄ 压在"昨天 22:51"上）。修法：`needsExpandForActions` 的行不再做 `moveTimeRight`
   （时间留在 app 名后，右上角让给箭头）。复验 `work/z8os3-live/arrow.png`：互传行
   "互传 昨天 22:51" + 右侧独立箭头，无重叠；非动作行仍是时间靠右、无箭头。
   豁免同样被 `isA13OrLower()` 圈住，OS5 零改动。
3. **设置 App 在 OS3 上不再自称 OriginOS 5**：`tagline` 从写死改成 `%1$s · LSPosed 模块`，
   `MainActivity` 用新增的 `OsProfile.originName()` 绑定（`page_home.xml` 补 `@+id/home_tagline`）。
   实测 `work/z8os3-live/tagline2.png` → "OriginOS 3 · LSPosed 模块"，
   三个应用卡：系统界面 2 项功能·2 已启用、媒体存储 1·1、文件管理 1·1。



**另两个功能在 OS3 装机确认：**
- PickerAlbums：`picker albums patch installed hooks=2`（修复前整个功能 0 钩子）。
  相册 tab 的实际效果还没看（需要进系统相册的选择器）。
- FileManagerRename：`fileman rename patch installed os=a13 hooks=2`，
  主进程 / `:widgetProvider` / `.share` 三个进程都装上（A13 符号表按 `view.dialog.f2`
  /`view.dialog.l1.d`/`DialogNameEntry.a(String)Z`/`f2.x()` 落地，见 `out/Z8OS3-FILEMAN-RENAME-RE.md`）。
  **>56 字符重命名的真实效果未验证**：文件管理首次启动有隐私同意弹窗，
  我没替用户点"同意"（那是用户该做的决定），需要用户手动同意后再测。
- MediaCard/QS 磁贴：`media_card feature installed` + `qs player tile installed`（OS3 列表内原生媒体卡
  在 `ab-off/keep2` 截图里都在，`media_card_qs_seek` 也已开）；OS5 行为零改动（所有新分支都用
  `OsProfile.isA13OrLower()` 圈住）。

### 5k 动作区链路的可控样本与覆盖面

1. 调试器新增 `postact`（也并入 `postall`）：两条带动作的样本（base 2 条动作、bigText 3 条动作），
   代码在 `debug/NotifyTest#postActions`，`cancel` 一并清。
2. **真实第三方样本上的完整度量**（`work/z8os3-live/verify8.log`，校验器 PASS）：
   `pkg=com.vivo.easyshare style=base expand=1 rowH=443 contractedH=317 contentH=394+`
   → 动作带 126px、`timeEndGap=138`（正好 16dp+30dp 箭头预留）、`nAppTimeGap=403`（不挤）。
   另一条 `com.android.systemui`（充电已完成）也是动作行，但模板本身没有时间位
   → 校验器按既有规则 WARN 跳过时间断言，没有误报。
3. **一个设计边界要记牢**：我们自己的通知在 OS3 会被系统自动归组，于是 `postact` 两条
   直接变成堆叠卡（截图 `verify8.png`：徽标 3 + 露边），走的是 stack 自绘分支
   ——**stack 模式下子通知的动作按钮不可达**（自绘展开列表只画 title/body/icon/time）。
   这不是 OS3 新增缺陷，OS5 上同样如此，属于堆叠功能的已知边界，记在这里避免以后误判。
4. 覆盖面现状：动作区链路已在 3 条真实/半真实样本上验证（dynsystem、easyshare、systemui 充电完成）；
   非动作行的箭头仍按原设计隐藏、时间仍右移到 16dp。

### 5l 可控动作样本 + heads-up 取证的部分结论

1. `postact`（`debug_notify postact`）实测把两条动作样本投成了**独立行**
   （`restyled pkg=com.betteroriginos style=base single` / `style=bigText single`），
   不再像上一轮那样被自动并进堆叠卡 → 动作区链路从此有可重复的可控样本。
2. **vivo OS3 会静默丢弃自定义 action 的广播**：
   `am broadcast -a com.betteroriginos.DEBUG_NOTIFY …` 返回 `Broadcast completed: result=0`
   但 receiver 一行日志都没有，logcat 里是
   `_V_VivoAmsImpl: vivo protected broadcast:Intent { act=com.betteroriginos.DEBUG_NOTIFY … }
   send by null should with permissions for security!!!`。
   → 调试入口不能指望 `am broadcast`，改走 Activity；
   而 Activity 会盖住 heads-up，所以 `postall`/`postact` 分支现在先 `moveTaskToBack(true)`
   再延迟 900ms 投递。
3. heads-up 在 OS3 上的观感（`work/z8os3-live/hud3.png`）：横幅是带我们重排样式的圆角卡
   （app 名行 + 标题 + 正文 + 右上展开箭头），没有破相。
   **但这张抓到的是 LSPosed 的模块更新通知**，我们自己 `postact` 的横幅没在 3s 采样窗口里拍到
   → heads-up 只能算"未见异常"，仍需在用户配合下补一次定向取证。
4. 设备已还原：测试通知清空、`group_mode=ios_stack`、crash buffer 0。
   注意这台机随时可能有人在用（本轮截图时前台是系统"关于手机"页）。

### 5m 复跑探针：查出一个危险的跨 ROM 假设（vivo 音乐小组件 AIDL）

1. 改完源码后再跑 `work/os3-probe.py`（产物 `out/Z8OS3-TARGET-CHECK-2.md`）：
   26 类 / 7 "缺失类" / 1 缺失方法。其中 5 条是脚本误抓（`android.app.Application`、
   `StatusBarNotification` 是 framework 类；两条是包名前缀串）；
   `Legacy…IconAreaControllerImpl` 两个只是"OS5 名字表项"，OS3 走的是新加的两个类名
   （装机 `icon layout guards installed=2` 已证）。
2. **真问题**：`com.vivo.musicwidgetmix` 在 OS3 确实存在（`/system/app/VivoMusicWidgetMix`，10.8MB），
   但把它 dexdump 出来看，`IMusicWidgetMixService` 已被混淆成 **`com.vivo.musicwidgetmix.e`**，
   41+ 个单字母方法（`v`/`w`/…）→ **事务号映射与 A15 完全不同**。
   我们按 A15 推出来的 `TX_SEEK_POS = 15` 在 A13 上打过去，会调到该接口的**另一个方法**
   （可能是播放/下一首这类），属于"看着没反应其实在做错事"。
3. 修法：A13 上不再 `bindService` 那个服务（`ensureWidgetService` 直接 return），
   `seekViaWidget` 也直接返回 false，seek 只走标准 `MediaController.getTransportControls().seekTo()`。
   装机复验：`bind widget service` 日志 **0 条**，`media card feature installed` 正常，
   通知行校验器仍 **PASS**，crash buffer 0。
4. 连带结论：QS 磁贴进度条在 OS3 只剩标准 seek 路径可用；
   它是否真能拖动进度，仍要等用户放歌时验（`media_card_qs_seek` 已开）。

### 5n 媒体卡在 OS3：链路已跑通，拖动 seek 仍待定位

1. **A/B 先纠正一个前提**：关掉 `media_card` 后，A13 对 MediaStyle 通知只渲染成
   **一行普通文本（无封面、无进度条）**（`work/z8os3-live/native-card.png`）。
   那张带封面 + `1:17/3:34` 进度条的卡是**我们做的**，所以"OS3 原生也有媒体卡"只对
   vivo 白名单音乐 App 成立，对普通 MediaStyle 通知不成立。
2. **完整链路在 OS3 上实测通过**（`07:52` 那一轮，pid 过滤日志）：
   `restyling style=os5 com.android.internal.widget.MediaNotificationView actions=5`
   → `atomic card built: prev=true play=true next=true heart=true source=true`
   → `bindSession enter root=MediaNotificationView attached=true`
   → `session=com.betteroriginos state=3`
   → `seekable=true native=true widget=false actions=822`。
   `widget=false` 正是 5m 关门后的预期结果（A13 只走标准 MediaController）。
3. **拖动 seek 在 A13 上原本完全失效，已修**（这是本轮最实的收获）：
   进度条其实收得到触摸（`card seek bar DOWN enabled=true w=708 h=72`，我先前 y=838 是眼估错了，
   真位在 y≈900），但 `MediaController.getTransportControls().seekTo()` 抛
   **`IllegalArgumentException: packageName does not belong to the calling uid;
   pkg=com.betteroriginos, uid=10056`**（栈在 `ISessionController$Stub$Proxy.seekTo`），
   回落到被我关门的 widget 路径 → 什么都不做。
   根因正是 skill §4 那条老坑的**新变种**：`bindSession` 用 `root.getContext()` 取
   `MEDIA_SESSION_SERVICE`，而通知视图的 context 是发通知 App 的 `createPackageContext`，
   A13 的 MediaSessionService 会校验包名与调用 uid 是否匹配（SystemUI 是 uid 10056）。
   修法：改用已有的 `sysuiContext(...)`（`ActivityThread.currentApplication()`）。
   端到端复验：`card seek bar DOWN` → `debug media onSeekTo pos=145214` →
   `dumpsys media_session` 里 `position=63000 → 145214`，`native seek failed` 消失。
   OS5 上这个 bug 被 widget binder 兜底路径掩盖着，同一处修复对两边都成立。
4. 为可诊断性留下的日志：`seekable=… barAt=x,yWxH`、`bindSession enter …`、
   `card seek bar DOWN enabled=… w=… h=…`（都是低频，只在绑定/用户拖条时打）。

### 5o 同类 context/uid 坑的全量审计（结论：只有 bindSession 一处）

刚修的那条属于"用通知视图 context 取系统服务"这一类，所以把同类调用点全扫了一遍：

| 调用点 | 用的 context | 判定 |
|---|---|---|
| `MediaCardFeature.bindSession` → `MEDIA_SESSION_SERVICE` | 修复前 `root.getContext()` | ❌ 已改成 `sysuiContext()` |
| `registerPreviewObserver`（ContentObserver） | `sysuiContext(ctx)` | ✅ 本来就对 |
| 预览读写 Global 设置 | `sysuiContext(ctx)` | ✅ |
| `publishToMediaStore` | 传入 `sysui` | ✅ |
| `QsPlayerTile.pickSession` | 磁贴自己的 context（SystemUIPlugin 进程内，包名与 uid 自洽） | ✅ 无需改 |
| `NotifyRowFeature.appIcon/isDebug/Prefs` | PackageManager 与 Settings.Global，不做包名-uid 校验 | ✅ |

→ A13 上这一类隐患只剩已修的那一处；skill §4 原来只把坑写在"存储/系统服务"，
现已补上 `MediaSessionService` 这个新变种。

### 5p heads-up 定向取证仍未完成（设备当时正被人使用）

用 `debug_notify post`（该分支先 `moveTaskToBack` 再延迟 800ms 投递）尝试抓横幅：
模块侧确实重排了（`restyled pkg=com.betteroriginos style=base single` / `bigText single`），
但截图 `work/z8os3-live/hud_a.png` 显示前台是系统"关于手机"页 + vivo「手机异常」提示，
横幅没采到。→ 停手：后续需要输入事件的取证（heads-up、堆叠露边观感、QS 磁贴拖动）
必须先确认 `mCurrentFocus` 空闲并由用户让出设备，不再往别人正在用的屏幕上发事件。

4. 顺带修的两处调试器缺陷：调试会话原来 `Callback` 是空的（`onSeekTo` 不会被响应，
   拖动根本无法观测）→ 已实现 `onSeekTo` 重设 PlaybackState；
   `logcat -c` 的时机要放在**卡片绑定之后**，否则会把 `bindSession`/`seekable` 这些
   一次性日志清掉，误判成"功能没跑"（我这轮就自己踩了两次）。

### 5q 最终构建的两项复跑 + 补掉一个"静默放弃"观测洞

1. **一键清除崩溃回归在最终构建上重跑**（之前那次绿灯是 07:41 的构建）：
   面板保持打开，`postall → cancel` 三轮，起始/结束 PID 都是 **9116 不变**，
   crash buffer 里 systemui/betteroriginos **0 条**，我们兜住的异常（pre-draw guard /
   deferred task / stack guard / icon guard）**0 条**。
2. **补掉一个观测洞**：普通行的 `scheduleVerify` 重试 8 次耗尽后是**静默 return**
   （堆叠那条早就有终态日志，普通行漏了）。这次就踩到了：
   `restyled=12` 但 `verify=0`，看着像"校验器坏了"，实际是我们那 8 条测试行被用户
   自己的 13 条通知挤到可视区下方，`isShown()` 一直不成立。
   现在放弃会打 `notify_row.verify.skipped pkg=… style=… w/h/shown/settledRoot/settledRow`，
   校验器新增规则：**某个 (pkg,style) 只有 skipped 没有成功测量 → FAIL**。
3. **最终构建 OS3 代码级校验 PASS**：`checked 1 contracted rows, 2 overlay rows, 1 stack rows`。
   这一跑还抓出我自己写死的一个阈值：动作带下限拍了 100px，而我们那条 2 动作样本实测是
   **98px**（vivo 模板是 126px）→ 断言改成按 dp 区间 `dp(20)..dp(72)` 并把两个实测值写进消息里。
4. **回归没有变松**：同一份校验器跑上一轮 OS3 日志仍 PASS，跑旧 OS5 日志失败项与改动前逐条一致
   （2 条 bodyColor 历史项）。
5. 设备已收尾：我们的测试通知 0 条、crash 0、PID 9116 稳定。
   heads-up 与 QS 磁贴两项仍卡在"需要设备空闲 + 真放歌"，没有靠猜测下结论。

### 5r separate 模式在最终构建上的校验（删钩子影响的另一半）+ 规则校准

1. **separate 模式 PASS，覆盖是迄今最实的一次**：`checked 9 contracted rows, 4 overlay rows`，
   流水线 `count=18 summaries=0 groupEntries=0` → 删掉 `getGroupKey→null` 之后，
   分离模式在 OS3 最终构建上确认没有漏组、没有残留摘要行。
2. **新加的 skipped 规则第一次跑就报 13 条 FAIL**，逐条看原因字段全是
   `shown=0 settledRoot=1 settledRow=1` —— 样本在可视区外，不是缺陷。
   规则因此校准成：`shown=1` 却放弃 = FAIL（测量管线在可见行上失灵）；
   `shown=0` = 覆盖率警告（列出 pkg/style 清单）。既保住"不许假绿"，也不误报。
3. 我自己又犯过一次同类错：警告是 `append` 到 `warnings` 里，但那段代码写在
   `for w in warnings: print(...)` **之后** → 警告永远不会被看见。挪到打印之前才有输出。
4. 阈值修正：动作带断言从写死的 `100..260px` 改成 `dp(20)..dp(72)`，
   并把两个实测值写进消息（我们 2 动作样本 98px、vivo 模板 126px）。
5. 四份日志回归对照：`sep-final` PASS、`final3`(ios_stack) PASS、`verify7` PASS、
   旧 OS5 日志失败项与改动前逐条一致（2 条 bodyColor 历史项）→ 口径没有变松。
6. 设备最终状态：`group_mode` 还原成用户的 `ios_stack` 并重启 SystemUI 使其生效
   （PID 15312，`guards installed=2` / `roundness pinned` / `media card feature installed`），
   crash 0，用户自己的 13 条通知完好，我们的测试通知 0 条。

### 5s 预览管线 PASS + heads-up 在 OS3 上"取不到"的确切原因

1. **设置 App 的真实预览渲染在 OS3 上工作**（不用点 UI，直接驱动设置项）：
   `settings put global betteroriginos_preview_req <ts>:os5` →
   模块侧 `preview saved style=os5 size=960x522`，并回写
   `betteroriginos_preview_done=1793147725107:os5` → 渲染 + MediaStore 发布 + 完成信号三段都通。
   这条也顺带证明 5o 的 context 审计结论（预览那几处本来就正确用了 `sysuiContext`）。
2. **heads-up 在 OS3 上取不到，原因不是我们的钩子**：
   - OS3 的 `NotificationContentView` 里 `setHeadsUpChild`/`mHeadsUpChild`/`calculateVisibleType`
     都在（直接查 `G:\z8os3-ref\z8os3-dexdump-f.txt`），钩子目标可达；
   - 之前所有投递都发生在**面板展开时**（AOSP 展开面板不出横幅）；
   - 收面板后设备其实停在**锁屏**（`hud1.png` 是锁屏页），锁屏同样不出横幅；
   - 解锁回桌面再投（`hud3.png`）仍无横幅，且我们所有测量行都是 `headsup=0`
     → 判断是 **vivo OS3 对第三方 App 的"桌面横幅"默认关闭**（用户侧通知设置），
     不是模块失灵。要验只能让用户在 设置→通知与状态栏 里给 com.betteroriginos 开横幅，
     或看真实音乐/IM App 自己触发的横幅。
   - 附带收获：锁屏上的通知卡同样是我们的 iOS 样式（圆角、38dp 图标、app 名行、动作行带箭头），
     说明重排管线在锁屏路径上也成立。
3. 设备已还原：面板收起、我们的测试通知清空，前台停在用户自己打开的"关于手机"页。

### 5t OS3 冒烟闸门脚本化：`work/os3-smoke.sh`

把这轮零散的验证手法固化成可重跑的脚本（退出码非 0 = 有断言失败）：`work/os3-smoke.sh [序列号] [group_mode]`。
七步：环境前置（确认在 DSU/OS3，防"把宿主当被测环境"）→ 重启 SystemUI 并确认钩子装载
→ 冷启投递 `postall`+`postmedia` → 开面板慢滚 → 按 pid 取日志 → 跑 `verify_notify.py`
并按"实测到多少类行"设覆盖闸门（<4 直接失败）→ 一键清除式批量移除 3 轮（PID 必须不变、
crash buffer 必须空、兜底异常计数）→ 媒体卡链路三段（渲染/绑定/可拖动）+ seek 回落异常计数 → 收尾。

首版脚本自己被抓出两个 bug（这正是脚本化的价值）：
1. 把 MSYS 路径喂给 Windows python（`can't open file C:\c\Users\...`）→ 必须 `cygpath -w`。
2. 这台机息屏只有 30s，脚本累计等待 30s 后所有行 `isShown()=0` → 28 条"放弃"；
   加 `svc power stayon true`（收尾恢复 false）。

跑出来的三个真问题与修法：
- 校验器新加的"可见行被放弃 = FAIL"立刻命中 `dynsystem base shown=1 settledRoot=0`
  —— 刚慢滑过的列表还在补间，普通行 8 次重试（≈4.6s）赶不上 → 预算给到 14 次（≈10s）后
  **放弃 0 条、4 普通行 + 3 堆叠行实测、PASS**。
- `postmedia` 在脚本里没生效：Activity 实例已存在时 intent 不再触发 `handleDebugIntent`
  → 每次投递前 `am force-stop`（`dbg()` 助手）。
- 校验器纠出一处前提缺失：`dynsystem` 报 `title hidden` FAIL，但该通知本身没有标题
  （只有 EXTRA_TEXT）→ 模块补 `hasTitle=` 字段，校验器改成"有标题才断言标题可见/端距"，
  判不出来时按"应该有标题"处理（宁可误报不漏报）。

最终一轮全绿：`OriginOS 3 / sdk 33`、钩子装载 2 条、覆盖 7 类行、批量移除 3 轮 PID 27346 不变、
crash 0、兜底异常 0、媒体卡链路 6 条日志、seek 回落无异常。证据 `work/z8os3-live/smoke.log`、`smoke.png`。

## 阶段 5u · 崩溃面收口：主线程回调兜底 + setResult(null) 全量复核（已装机并复跑）

把与"一键清除导致 SystemUI 重启"同一类的**剩余未审计面**全部扫完，两处补兜底：

1. `MediaCardFeature.updateSeekable()`（`module/src/com/betteroriginos/feature/media/MediaCardFeature.java:1453`）
   拆成外壳 try/catch + `updateSeekableInner()`：它由 `MediaController.Callback` 与
   ServiceConnection 直接调用，跑在主线程、不在 Xposed 保护范围内。
2. `NotifyRowFeature` 的 reverify `ContentObserver.onChange`（`module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java:1385`）
   原来裸调 `triggerReverify()`——那是要遍历整棵通知树的路径，观察者回调同样是主线程无保护。

同类面逐一核对，确认已经安全、不重复加壳：`ticker`/`updateProgress`/`syncArt`/`refreshColors`/
`release` 各自整方法体在 try 内；`QsPlayerTile.run()` 全试、其 Callback 只赋字段不碰 binder；
`captureWithRetry` 全试。

**`setResult(null)` 全量复核（这是 OS5 事故的确切机理，值得一次清账）**：模块现存 4 处，
逐条用 `G:\os3-ref\os3-dexdump-f.txt` 查返回类型：
- `NotificationGroupUtil.sanitizeTopLine` → `(ViewGroup,ExpandableNotificationRow)**V**`
- `updateIconsForLayout` → `(…,Function,…)**V**`；`updateVivoIconsForLayout` → `…**V**`
- 文件管理器 `f2.x()V`（A13 确定按钮，代码里已用 `findExact(..., void.class, ...)` 锁签名）
- A15 `x2` 为同一个确定按钮的对应符号（非 SystemUI 进程，且 OS5 上该功能已验证可用）

即：**除已删除的 `getGroupKey`（返回 String、被批量移除的 key 查表消费）之外，没有任何 hook 在用 null 覆盖一个被调用方消费的非 void 返回值**。崩溃类到此清账。

冒烟闸门补一步 **5b) 媒体卡在场的清除压力**：面板展开时 `postmedia`↔`cancelmedia` 两轮
（`dbg()` 内的 force-stop 让会话突然死掉，正是"会话先没、通知后消"的竞态窗口），断言 PID 不变。
首跑抓到自己的计数器在骗人：`DeadObjectException` 计到 11 条，逐条看栈全是 **system_server（pid 29952）
在第 2 步 kill 掉旧 SystemUI（27346）之后对旧 binder 的正常抱怨**（`TaskOrganizerController`×7、
`PlaybackActivityMonitor`、`VolumeController`、`DisplayManagerService`×2），与被测模块无关；
计数器改成 `--pid=$P` 只看存活进程。**教训：任何"异常计数"型断言都必须先按 pid 划界，否则自己重启一次就永远脏。**

这一轮 `updateSeekable` 兜底 0 次、`updateProgress failed` 0 条——说明 `MediaController` 的
`getMetadata()/getPlaybackState()` 在会话死亡时**内部已吞掉 RemoteException 返回 null**，
所以"一键清除带走 SystemUI"的真因不是这里（就是 getGroupKey + 未保护的 pre-draw/deferred 路径，
两者已在阶段 5a/5j 修掉）。这个补壳按 defense-in-depth 保留，但结论记清楚，别再把它当根因。

最终一轮全绿：`OriginOS 3 / sdk 33`、钩子装载 2 条、覆盖 6 类行（3 普通 + 3 堆叠）、放弃 0 条、
批量移除 3 轮 PID 1962 不变、crash buffer 空、5b 媒体销毁 2 轮 PID 不变、媒体链路 22 条日志、
seek 回落无异常。证据 `work/z8os3-live/smoke.log`、`work/z8os3-live/dead.log`、`smoke.png`。

## 阶段 5v · "OS3 没有一键清除圆点"从推断升级为实测，并纠正一条旧结论

目标里"OS3 没有 OS5 那种一键清除圆点"此前只是**代码层推断**（OS3 dexdump 里 `dismiss_all`、
`NotificationEditorView` 命中数都是 0）。这一轮补上**现场实测**：

- 面板展开、`mCurrentFocus=NotificationShade`、导出 110 个节点，
  全树 grep `清除|清空|clear|Clear|footer|Footer|删除` **0 命中**；
  列表最底部（y 到 2298，屏高 2388）仍是普通通知行（"vivo服务/10小时前"），没有 OS5 的圆点、
  也没有 A13 遗留的 `FooterView`/"清除"文本按钮。
- 也就是说 OS3 上**唯一**的批量移除入口是 `NotificationManager.cancelAll()`（应用侧）与逐条滑动/单条清除；
  冒烟闸门的第 5 步（`cancelAll` 三轮）因此就是这台机上能测到的最强等价路径——不是退而求其次。

顺带**推翻并修正**一条记错的坑：之前写的是"这台机 uiautomator 不导出通知行"。实测导出得很完整，
包括 `com.android.systemui:id/expandableNotificationRow`、`android:id/notification_main_column` 这些 id；
真正不导出的是**类名**——所有节点的 `class=` 都是 `android.widget.FrameLayout/LinearLayout/TextView`，
自定义类名一律被抹平。所以取证要用 **resource-id**，别按类名找。证据
`work/z8os3-live/footer4.xml`（同批还有 `footer.xml`/`footer2.xml`/`footer3.xml` 三次失败样本：
息屏与甩动收起面板各污染了一次，已按 5u 的教训留档而不是覆盖）。

## 阶段 5w · 文件管理功能在最终构建上的装载复确认（功能验证仍差一次用户点击）

- 冷启文件管理，当前构建（233,890 B）现场日志：
  `loaded com.android.filemanager` → `fileman rename patch installed os=a13 hooks=2`
  → `installed feature filemanager_rename`。**A13 符号表（`view.dialog.f2`/`l1`/`DialogNameEntry`）
  对现在装的这个版本仍然命中**，不需要重做逆向。
- 卡在 `com.android.filemanager/.FirstPermissionActivity`（首次启动的权限/隐私页）——
  按纪律这类页面不代点，>56 字符重命名的**功能**验证要等用户点过一次"同意"后再跑。
- 顺手记一个自己踩的错：这台机上**没有** `com.vivo.filemanager`（`resolve-activity` 直接 No activity found），
  文件管理就是 `com.android.filemanager`；以后找落点别再去 grep 前者。

## 阶段 5x · 设置 App 不再在 OS3 上摆"静默失效"的开关（新增落点说明组件）

"完美适配"也包括**设置界面本身**：之前所有功能卡与开关在两个系统上长得一模一样，
于是 OS3 用户会看到"媒体选择器相册修复"这种**开了也没任何反应**的开关（本机实测
`picker albums landing no-entry`），以及"通知条 iOS 化"收起态为什么没有动作按钮的困惑。

- 抽象：`Feature#supportNoteRes(Context)`（默认 0 = 无说明），`FeatureAdapter` 在摘要下面渲染
  `item_feature.xml` 新增的 `feature_note`（tertiary 12sp，GONE 时不占位）。
  **OS5 上两个 override 都返回 0，渲染结果与改动前完全一致**（同一 APK 双系统的前提）。
- 两条说明文案（各 ≤2 行，第一版写成 5 行带取证术语，已收敛）：
  `note_picker_no_landing`（本机没有可达的照片选择器，开启后无可见变化；切回 OS5 直接生效）、
  `note_notify_actions_a13`（本机系统只在展开后渲染动作按钮，故收起态保留展开箭头）。
- 装机实测（OS3，`uiautomator dump` 逐节点核对，不靠眼估）：
  选择器卡 `feature_note [228,791][786,923]`（2 行）、通知卡 note 在 summary 与选项区之间且不重叠。
  因为改的是**会被 SystemUI 加载的共享类**（`Feature`），复跑整套闸门：
  `OriginOS 3 / sdk 33`、钩子装载 2 条、覆盖 5 类行、批量移除 3 轮 PID 10968 不变、
  5b 媒体销毁 2 轮 PID 不变、死会话异常 0 条（pid 划界后计数诚实）、crash buffer 空、媒体链路 22 条。

## 阶段 5y · heads-up 的阻塞条件被缩小，但这次实测仍然作废（设备被占用）

想把遗留项"heads-up 在 OS3 未实测"往前推一步，先查配置面：
`global heads_up_notifications_enabled = 1`、我们的 `betteroriginos_debug` 渠道
`mImportance=4 (HIGH)`、`AppSettings importance=DEFAULT`——**说明不是全局开关被关**，
之前几次拿不到横幅只可能是"当时的界面状态"或 vivo 的按应用横幅策略。

于是按"idle + 主屏 + 面板收起"重做一次：`collapse` + `keyevent 3` → 冷启 `debug_notify post`
→ 2s 后截图 + 按 pid 取日志。结果两条 verify 全是 `headsup=0`，截图显示**前台是文件管理的
首次启动隐私弹窗**（"同意/不同意"，是我上一阶段启动 `com.android.filemanager` 留下的），
不是主屏——所以这一轮的"没横幅"不能算结论：投递时前台既不是 launcher 也可能被系统判为不打扰场景。

处置：**立刻停止对设备发任何输入事件**（弹窗按纪律不代点，且此刻面板正被人拉开），
只回写了 `notify_verify=0`、`stayon=false` 与清掉我们自己的通知。文件管理那个同意弹窗**故意留在屏上**，
用户点一次"同意"就能同时解掉 5w 的功能验证阻塞。
下一轮 heads-up 的正确前置：设备空闲、`mCurrentFocus` 确认是 launcher、且文件管理弹窗已消失。

## 阶段 5z · OS5 崩溃归因拿到"同一个消费方在 OS3 也存在"的硬证据，并拆掉一个复发性陷阱

- **OS5 那次的崩溃点是实录，不是推断**：DEVLOG 2026-10-26 22:27 的踩坑条目写着
  "在 pipeline 抹 `getGroupKey` 去'组中组'会让 `HeadsUpCoordinator.onBeforeFinalizeFilter`
  NPE 崩溃循环" —— 当时真机撞到过。一键清除会触发列表重新过滤，正好再走一遍这个方法，
  所以"圆点一键清除 → SystemUI 重启"的因果链是完整闭合的。
- **补上跨系统的那一半**：用 `G:\os3-ref\os3-dexdump-f.txt` 查 OS3/A13，
  `onBeforeFinalizeFilter` 存在且签名 `(Ljava/util/List;)V`（全量 dump 里 `HeadsUpCoordinator` 命中 563 处）。
  即**同一个空 key 消费方在两个系统都在**，这条禁忌不是"OS5 的历史包袱"，而是长期有效的硬约束。
- **拆掉一个会害人的注释**：`filterGroupSummaries` 里还留着
  "子项已通过 getGroupKey=null 独立成卡"——机制早已删掉、注释还在教后人怎么把它加回来。
  改成实际机制（`isGroup()=false` + `flattenGroups`），并在 `isGroup` 钩子处把
  "OS5 实测崩在 onBeforeFinalizeFilter / OS3 同一方法也在"写进注释。
- **摘要移除路径本身复核为安全**：`Iterator.remove()`（不会 CME）且整段在 try 内，
  发生在被 hook 的方法里（Xposed 保护范围内），不属于本轮补兜底的那一类主线程裸回调。

## 阶段 6a · QS 磁贴的装载路径查实并写进闸门（OS3 唯一媒体落点不能只靠"看起来装了"）

- 先排除一个假缺陷：`FeatureManager:46` 是按 `Feature.scopes()` 决定给哪个进程装功能的，
  而 `arrays.xml` 的 `xposedscope` 有 4 项、feature 侧 grep 只看到 3 个包名——
  实际 `MediaCardFeature.scopes()` 返回 `{"com.android.systemui", PLUGIN_PKG}`（PLUGIN_PKG 是常量，
  按字符串 grep 抓不到），与 `arrays.xml` 一致；`install(cl, pkg)` 再按包名分流到 `installQsTile`。
- 再查运行时：**OS3 上没有独立的 `com.vivo.systemuiplugin` 进程**（`pidof` 为空），
  插件是加载进 SystemUI 进程的，日志为
  `loaded com.vivo.systemuiplugin/com.android.systemui` →
  `qs player tile installed on com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView`
  → `installed feature media_card for com.vivo.systemuiplugin`，
  并且 `os profile: … mediaShade=qs_tile` 说明画像判定也走对了。
- **闸门补两条断言**（第 6 步）：`qs player tile installed` 计数 ≥1 否则 FAIL，外加打印 `mediaShade=`。
  原因：第 2 步的"钩子装载"只看 systemui 那两个功能，作用域掉了或插件类名改了都查不出来——
  而 QS 磁贴在 OS3 上是**唯一**的媒体落点，不能只靠肉眼。
  复跑全绿：pid 17336、覆盖 6 类行、批量移除 3 轮 + 媒体销毁 2 轮 PID 不变、磁贴钩子 1 条。

## 阶段 6b · OS3 深色模式首次实测（PASS，且是"真测到"而不是空跑）+ 磁贴断言补成确定性

- **深色维度此前只在 OS5 上测过**（`work/os5-ref/notify-verify-20261026-night.log`），
  OS3 侧整个 5a~6a 从没跑过 `night=1`。本轮 `cmd uimode night yes` 后整套闸门全绿，
  并按"覆盖优先"复核了不是空跑：9 条测量行**全部 `night=1`**、
  `titleColor/bodyColor=0xffffffff`、堆叠行 `cardColor=0xf21c1c1e`（6 条），
  截图 `work/z8os3-live/night-shade.png`。深色下的堆叠露边观感与浅色是同一个待用户目测项。
- **`NIGHT=1 work/os3-smoke.sh` 已固化成开关**：第 0 步切深色并断言切换成功，第 7 步自动恢复浅色
  （打印 `已恢复浅色模式：Night mode: no`），避免"只测浅色"变成默认习惯。
- **新加的磁贴断言第一次跑就 FAIL，查出来是断言自己不确定**：
  `SystemUIPlugin` 是**懒加载**进 systemui 进程的，只有真正画过一次 QS 面板才会出现
  `qs player tile installed`。上一轮"绿"是因为我手工开过 QS，本轮没开就红。
  修法不是放宽断言，而是**补确定性触发**：查之前先 `cmd statusbar expand-settings` + 4s + `collapse`。
  复跑 exit=0、磁贴 1 条、PID 26063 全程不变。
- **自己造的坑**：用 `NIGHT=1 bash work/os3-smoke.sh | head -12` 看前半段，
  `head` 退出触发 SIGPIPE 把脚本**中途杀死**，直接跳过第 7 步收尾——设备被留在
  深色模式 + `stayon=7` + 3 条我们的通知没清。长脚本要么整跑落盘再看，要么 `tee`，
  绝不能为了少看几行去截断一个带收尾责任的脚本。

## 阶段 6c · OS3 大字号（1.3）实测 PASS，并修掉一个"日志量错对象"的观测缺陷

- **字号维度此前也只在 OS5 测过**。OS3 上 `settings put system font_scale 1.3` 后整套闸门 exit=0，
  且确认不是空跑：`titleTS/bodyTS/appTS` 从 `45/42/36` 变成 `58/55/47`（≈1.3 倍），
  行高 `rowH` 310/352/383/427/491/559 与 `contractedH` 一致、`contentH==rowH`，说明卡片高度跟着字号长、没裁字。
- **顺手抓到一个观测缺陷（比"测过了"更有价值）**：verify 行的 `fontScale=`/`density=` 取自 `ctx`，
  而 `ctx` 是**发通知 App 的 `createPackageContext`**——它的 `Configuration` 可能还是改字号之前的旧值。
  实测就是：设备 1.3、文字确实 58px，日志却记 `fontScale=1`。
  而 `verify_notify.py` 有三处按 `font = num(r,"fontScale",1.0)` 缩放期望值，
  等于**校验器一直在用错的字号算容差**（这次侥幸没判错，但大字号下的严格断言会被悄悄放宽）。
  改成取 `root.getContext()`（root 已 attach 到 SystemUI 窗口，它的配置才是实际渲染用的配置），
  与另外两条 overlay/stack 日志（本来就用 `overlay.getResources()`）对齐。
- **两个方向都复验**：1.3 → `fontScale=1.3 titleTS=58`；恢复 1.0 → `fontScale=1.0 titleTS=45 night=0`，
  两次闸门都 exit=0。设备最终还原：font 1.0 / 浅色 / stayon 0 / verify 0 / 我们的通知 0。
- **回头解释了一条旧遗留**：这台 DSU 的 `density=3.0`，所以 `dp(16)=48px`——
  5g 里"`com.bbk.appstore` 端距 132px（应 48）"的"应 48"就是 16dp，
  而 132px ≈ 46dp 箭头预留（138px）量级，与 5g 里"分类竞态：实际有箭头却被当普通行判"的假说吻合。

## 阶段 6d · 补上"逐条清除能力"这条断言（OS3 唯一移除入口终于有闸门守着）

OS3 没有一键清除圆点，所以**逐条滑动/单条清除是这台机上唯一的移除入口**——可验收闸门里
居然没有任何一条断言在管"我们的改造会不会把行的清除能力改掉"。
模块早就在 verify 行里打了 `rowCanDismiss` / `entryClearable` / `notifFlags` 三个字段，
但 `verify_notify.py` **一次都没读过它们**（纯观测、零约束）。

- 新增两条规则（普通行段）：
  1. `rowCanDismiss` 必须恒为 1（视图侧手势能力，改造后不许丢）；
  2. `entryClearable` 只允许在通知自带 `FLAG_ONGOING_EVENT(0x40)` 或
     `FLAG_FOREGROUND_SERVICE(0x20)` 时为 0 —— 否则就是"被我们弄没了"，而不是系统策略。
     现场数据正好有这个合法例子：LSPosed 的前台服务通知 `notifFlags=96(0x60)`、`entryClearable=0`，
     规则按策略豁免它，不误报。
- **新断言做过正负对照**（不是"跑绿就算"）：
  真实日志 → PASS；把日志里 3 行非 ongoing 通知篡改成 `rowCanDismiss=0`/`entryClearable=0`
  → 精确 FAIL 4 条并带出 `flags=0x10` 的解释（负样本留在 `work/z8os3-live/negctl.log` 当 fixture）。

## 阶段 6e · 新断言第一次真跑就抓出"自己写错的规则"：位值必须以平台 jar 为准

把 6d 的断言拿到**分离模式 + 深色**这个从没组合过的矩阵上跑，闸门 exit=1、6 条 FAIL，
全部落在 `pkg=android style=base`、`notifFlags=0x2`。查下来是**断言自己错了两处**：

1. **位值记错**：我按记忆写成 `ONGOING=0x40 / FGS=0x20`，
   用 `javap -constants android.app.Notification` 核对平台 jar 后是
   **`FLAG_ONGOING_EVENT=2`、`FLAG_NO_CLEAR=32`、`FLAG_FOREGROUND_SERVICE=64`**——
   恰好漏掉了这次真正命中的 0x2（ongoing）。规则改成用这三个已核实常量。
2. **两个字段策略不一致**：`entryClearable` 按策略豁免了，却仍要求 `rowCanDismiss` 恒为 1。
   实测 OS3 分离模式下 ongoing 行就是 `rowCanDismiss=0`，这是系统正确行为，不是我们改坏的。
   改成"只有通知本身可清除时，才要求手势能力还在"。

**改完三向复验**（不是改到绿就完事）：
分离+深色真实日志 PASS、堆叠+浅色真实日志 PASS、篡改负样本仍精确 FAIL 5 条。
证据 `work/z8os3-live/20261028-1050-sep-night.log`、`20261028-1044-smoke.log`、`negctl.log`。

顺带把**分离 × 深色**这个新矩阵跑成绿色（9 行测量、`night=1` 全程有效、磁贴钩子 1 条），
设备已还原 `group_mode=ios_stack` / 浅色 / stayon 0 / verify 0。

## 阶段 6f · 四个待用户动作项的"解锁即跑"清单（命令与判据都已核对存在）

以下每条只等一个用户动作，之后照抄命令即可判定，不需要再重新推理。

1. **OS5 宿主的圆点一键清除**（等：同意重启切回宿主）
   ```bash
   adb -s 10AE4Y0MDE000BL shell "su -c 'getprop ro.vivo.os.build.display.id'"   # 必须是 OS5/OS6，别把 DSU 当宿主
   A=$(adb -s 10AE4Y0MDE000BL shell "su -c 'pidof com.android.systemui'")
   # 手工：面板里放 20+ 条通知 → 点右上圆点"一键清除" ×3，每次等 5s
   B=$(adb -s 10AE4Y0MDE000BL shell "su -c 'pidof com.android.systemui'")
   [ "$A" = "$B" ] && echo "pid 不变：没重启" || echo "SystemUI 重启了 $A -> $B"
   adb -s 10AE4Y0MDE000BL shell "su -c 'logcat -d -b crash'" | grep -icE "systemui|betteroriginos"   # 必须是 0
   ```
   判据：pid 不变 + crash 计数 0。根因（`getGroupKey→null` 喂给 `HeadsUpCoordinator.onBeforeFinalizeFilter`）
   已删，且该消费方在两个系统都存在（5z），所以这条是收尾确认、不是未知风险。

2. **文件管理 >56 字符重命名**（等：点掉 `FirstPermissionActivity` 的"同意"）
   ```bash
   adb -s 10AE4Y0MDE000BL shell "su -c 'touch /sdcard/Download/$(python -c "print('长文件名测试'*12)")'"
   # 手工：文件管理 → 下载 → 长按该文件 → 重命名 → 确定
   adb -s 10AE4Y0MDE000BL shell "su -c 'logcat -d | grep -aE \"fileman rename patch installed|rename unlock failed\"'"
   adb -s 10AE4Y0MDE000BL shell "su -c 'ls /sdcard/Download | head -3'"   # 新名字在 = 功能成立
   ```
   判据：`os=a13 hooks=2` 在场、无 `rename unlock failed`、目录里出现 72 字的新名。

3. **QS 磁贴进度条与拖动**（等：用 i音乐 真实播放；vivo 磁贴不认我们自建的调试会话）
   判据：展开 QS 看磁贴进度条是否随播放走动；拖动后 `logcat -d | grep -a "card seek bar DOWN"` 有 DOWN 事件、
   且曲目位置改变。闸门已断言钩子装载（`qs player tile installed`，6a），所以这一步只看观感与手势。

4. **heads-up 横幅**（等：设备空闲，前台是 launcher）
   ```bash
   adb -s 10AE4Y0MDE000BL shell "su -c 'dumpsys power | grep -m1 mWakefulness=; settings put global betteroriginos_notify_verify 1'"
   adb -s 10AE4Y0MDE000BL shell "su -c 'am force-stop com.betteroriginos; am start -n com.betteroriginos/.ui.MainActivity --es debug_notify post'"
   sleep 2; P=$(adb -s 10AE4Y0MDE000BL shell "su -c 'pidof com.android.systemui'" | tr -d '\r')
   adb -s 10AE4Y0MDE000BL shell "su -c \"logcat -d --pid=$P -v threadtime\"" | grep -ac "headsup=1"   # >0 才算测到
   ```
   注意 `mCurrentFocus` 在息屏后是脏的（5y/6b），必须和 `mWakefulness` 一起看。

## 阶段 6g · 部署一致性硬核对：装机 APK 与最终构建逐字节相同（并排除了一个自己造的假警报）

- 核对方法：`pm path com.betteroriginos` 取设备上 base.apk，与 `module/build/BetterOriginOS.apk`
  比 sha256。**结果：大小 233,890 相同、sha256 前缀 `7aab5735026f6bf66318bdf5` 相同** →
  设备上跑的就是本轮最终构建（不是"我以为装了"）。
- 第一次核出的"MISMATCH"是**探针自己的错**：用 `su -c 'cat …'` 拉二进制，管道把文件污染成
  234,638 字节（+748，CRLF 类注入）。改用 `adb exec-out cat`（base.apk 全局可读，不需要 su）
  后哈希完全一致。**教训：跨进程取二进制一律 `exec-out` 直读，别过 su 管道；
  比对先比大小再比哈希，才能一眼看出是传输脏了还是版本真的不同。**

## 阶段 7a · 进度条"时有时无"真因：借用的 android.R.id.progress 与 MediaNotificationView 抢同一个条

用户报"播放器进度条好像改没了 / 又出来了"。排查过程先否掉了三个错判：
不是布局塌宽（实测 `bar=708x72 row=888`）、不是 LayerDrawable 要求（反编译 framework：
`AbsSeekBar.verifyDrawable` 只管 thumb/tickMark，`ProgressBar.verifyDrawable` 只管"是不是当前 drawable"）、
也不是绘制颜色问题。

**真因**：为了过 `NotifTouchInterceptor` 的手势白名单，我给自绘条设了
`bar.setId(android.R.id.progress)` —— 而 `MediaNotificationView` 自己就是按这个 id
找它的原生进度条并控制显隐的。它找到的是**我们的**条，于是把它设成
`INVISIBLE(v4) + alpha 0`；`View.draw()` 对 alpha==0 直接早退，所以
`onDraw`/`SeekDrawable.draw` 一次都不跑。祖先链实测：
`DragSeekBar(v4,a0.00,w708,h72) → … → MediaNotificationView(v4) → …`。
这解释了"时有时无"（取决于 MediaNotificationView 那一轮是否跑显隐管理）
与"vivo 真机卡没条、我们的调试卡有条"的差别。

**修法**：id 不能撤（OS5 的拖动靠它），改为**自我修复** ——
`MediaCardFeature.reclaimBar()` 在每次 `updateProgress()`（300ms tick）里把
可见性与 alpha 纠正回来（可寻态 1.0、不可寻态 0.85）。

**证据**：`card bar reclaimed visibility was 4`（确实被改成 INVISIBLE）之后
`seek draw #1..#3 bounds=[0,0][588,36] level=3585→3600`（开始绘制且进度在走）；
截图 `work/z8os3-live/bar-reclaimed.png` 里 vivo 真实播放卡显示
`1:26 [███ 38%] 3:32`，两端时间各归其位。

**闸门补断言**：`seek draw #` 计数 ≥1 否则 FAIL（这是"条被外力吃掉"唯一可靠的观测点）；
`card bar reclaimed` 计数 >0 时 warn。本轮 warn 32 次 —— 说明对方一直在改、我们一直在纠，
碰撞是持续性的，值得后续换成不共享 id 的方案。

## 阶段 7b · 顺手修掉一个静默已久的 OS3 缺陷：堆叠度量字段名

`applyStackMetrics` 一直抛 `NoSuchFieldError: NotificationChildrenContainer#mSidePaddings`
（A15 字段名，A13 不存在），且因为是"第一个 setIntField 就抛出"，
**后面的间距/圆角/算法字段全都没生效**，只留一条 warn。
用 dexdump 核对 A13 的 `NotificationChildrenContainer` 字段：
只有 `mChildPadding`/`mDividerPadding`/`mNotificationTopPadding`，没有那四个 A15 名。
改成逐字段独立探测（`setIntIfPresent`），A13 走 `mChildPadding`，
侧边距/圆角由我们自绘卡片负责；`applied=N/5` 进日志。复跑 `NoSuchFieldError` 计数 0。

## 阶段 7c · 本轮新发现、尚未修

`verify_notify.py` 稳定报：
`module skipped 2 stack measurements with no later success:
pkg=com.vivo.daemonService count=3 w=984 shown=0 settledOverlay=1 settledRow=1`
—— 真实 vivo 应用的 3 条组堆叠，overlay 与 row 都已 settle 但 `shown=0`，
即**组堆叠卡没显示出来**。这与用户提的"单通知展开时新旧图标重叠"同族，
下一轮从 `STACK_CARDS` 的可见性/alpha 路径查。

## 阶段 7d · 展开图标重叠：复现路径已排除两条，锁定一条（未动代码）

自动化复现三次都拿不到"展开态"，原因查清后反而是好消息——**不是手法问题，是这个模式下不该有展开**：

- `ios_stack` 模式下 `NotifyRowFeature:1717-1722` 会对已展开的行调
  `setUserExpanded(false)` 强制收回，且 `guardStack` 每帧把原生 `ExpandButton`/`Button`
  压成 `alpha 0` + `setClickable(false)`（2109-2123）。所以点箭头/点行都不会进入展开态
  （实测 `exp=0` 恒定）。**重叠不可能发生在这里**。
- 因此重叠的两个真实候选场景：① `separate` 模式（不强制收回，走原生展开动画）；
  ② `ios_stack` 下点卡片展开**组**（`toggleStack` → `setStackExpanded`）。
  下一轮从 ① 开始：`settings put global betteroriginos_notify_row_group_mode separate`
  后点开单条，抓 `exp-mid/exp-settled` 两帧比对图标位置。
- 落点已定位：交叉淡入淡出应加在 `guardStack` 每帧钉 alpha 的地方
  （`root.setAlpha(0f)` @2125、`bg.setAlpha(0f)` @2104）。
  A13 没有 `mExpandFraction`/`getExpandFraction`（dexdump 已确认），
  所以进度用几何量：`(rowH - contractedH) / (expandedH - contractedH)`，
  逐帧映射 `overlay.alpha = 1 - f`、`root.alpha = f`，天然跟住系统动画、且不依赖私有字段。
- 顺带抓到一条独立异常：`pkg=android sum=1 vis=4 card=1`
  （**行 INVISIBLE 但堆叠卡仍存在**），与闸门稳定报的
  `com.vivo.daemonService ... shown=0 settledOverlay=1 settledRow=1` 同源，
  属于"组卡可见性"问题，单独查。

## 阶段 7e · 闸门两处自我缺陷修掉：QS 触发不确定 + 堆叠跳过规则与普通行不一致

- **`QS 磁贴钩子没装` 是闸门自己的误报**：`SystemUIPlugin` 懒加载，而 `cmd statusbar expand-settings`
  在这台 vivo A13 上**不总能拉起插件**（本进程连 `loaded com.vivo.systemuiplugin` 都没有），
  所以"开一次 QS 就断言"必然忽绿忽红。改成最多 3 次重试（命令 + 真手势 + 复查日志），
  复跑 ios_stack 全绿：覆盖 6 类行、磁贴 1 条、进度条绘制 14 帧。
- **堆叠跳过规则补齐"屏上/屏外"区分**：之前只给普通行做了
  （可见却放弃=FAIL、屏幕外=WARN），堆叠路径漏了，于是长列表下
  `com.vivo.daemonService count=3 shown=0` 必然误报。AOSP 会把超出屏幕的行停在列表底部
  并置 INVISIBLE（实测 15 个子行只有前 5 行有独立 ty），这不是缺陷。
  改后：`shown=1` 且从未补测 → FAIL；`shown=0` → 警告。
- **负样本差点自欺**：第一次篡改用了 `count=3`，而真实行是 `count=2`，
  替换没生效导致"断言没响"被我误当成规则失效；换成 `count=9 + shown=1` 后
  精确报 `FAIL: module skipped 2 stack measurements on VISIBLE rows`。
  **教训：负样本必须先确认篡改真的落到了文本上，否则"没触发"是测试没做对而不是规则对。**
- 另：一度以为 SystemUI 自发重启（pid 6107→7559），核对后是自己那次 separate 闸门跑 kill 的
  （本地时间 UTC+8，我把 UTC 时间当成 14:01 之前的独立事件）；crash buffer 里 systemui 零记录。
  **判"是否自发重启"要拿 kill 时刻与 pid 起始时刻对表，别只看 pid 变了。**

## 阶段 7f · 展开重叠：拿到可复现把手、量出真实状态，过渡动画已实现但**尚未目测确认**

- 加了调试把手 `settings put global betteroriginos_notify_expand_test=<n>`：
  模块自己找一条可见行调 `setUserExpanded(true)`，并把收起/展开两侧的
  高度、alpha、visibility 一起打出来。**这是本阶段唯一能让展开变成可重复事件的办法**
  （adb 点行会命中 contentIntent，点箭头触点太小，四次全失败）。
- 把手本身踩了三个坑，都是真知识：
  1. `VERIFY_INFO` 同时收着收起态与展开态两套内容视图，用 `root.isShown()` 判可见
     会误拒 20/24 个候选 → 必须按**行**判可见。
  2. `rowOf(root)` 在这里返回的是 `NotificationContentView`，而 `setUserExpanded`
     只在 `ExpandableNotificationRow` 上 → 按类名向上找真行。
  3. A13 上有 8 个可见行 `sbnOf()` 取不到 sbn（**独立待查项**）。
- **量到的展开态**：`cH=268 eH=394 cAlpha=1.0 eAlpha=1.0 cVis=0 eVis=4 rowH=394`
  → 行撑到展开高度，但**展开子视图仍是 INVISIBLE**，收起内容满 alpha。
  两个含义：① 有我们 overlay 的行会出现"卡图标 + 原生展开图标"重叠；
  ② 没有 overlay 的行表现为"行变高但内容还是收起态"（空一大块）。
- **过渡动画已实现**（`guardOverlay` 每帧）：几何进度
  `f=(cvH-cH)/(eH-cH)`，卡 `alpha=1-f`。
  **关键安全条件**：只有当展开内容确实 `VISIBLE && alpha>0` 时才允许把卡淡掉
  （`expandedReady`），否则强制保持卡满 alpha —— 因为实测 A13 会把展开视图留在
  INVISIBLE，无条件淡出会让整行**变空白**，比原来的重叠更糟。
- 复跑闸门全绿：覆盖 6 类行、进度条绘制 17 帧、磁贴钩子 1 条、批量移除 3 轮 + 媒体销毁 2 轮
  pid 不变、crash 0。**但"重叠是否真的消失"还没有目测证据**：
  我能展开的那两行（`com.android.systemui`、`com.betteroriginos`）当时没有 overlay。
  下一步：展开一条**有卡**的通知（分离模式下带图标的长文通知），看 `overlay fade engaged`
  日志与两帧对比。

## 阶段 7g · 过渡动画已落地并带安全门；重叠的"展开态目测证据"仍未拿到（原因已定位）

- 把手改成遍历 `OVERLAYS`（只有带自绘卡的行才可能重叠），结果：
  `roots=1 noContracted=1 pairs=NotificationContentView/ExpandableNotificationRow`
  —— 该 messaging 的 contentView 此刻 `mContractedChild` 为空。
  用 dexdump 核对过 **A13 确实有 `mContractedChild`/`mExpandedChild`**，
  所以不是字段名问题，而是**这个 overlay 的 key 指向了一个收起子视图已被清掉的 contentView**：
  说明 `OVERLAYS` 的键生命周期有问题（行回收后键还在）。
  ~~与"sbnOf() 取不到"同源~~ —— **该推测已被推翻**，见 7i。
- 交叉淡入淡出代码已在 `guardOverlay` 每帧执行，且带 `expandedReady` 安全门
  （展开内容必须 `VISIBLE && alpha>0` 才允许淡掉卡，否则保持卡满 alpha）——
  这是刻意的：**宁可暂时留着重叠，也不能把行淡成空白**。
- 已目测的界面（收起态）没有重叠：messaging 卡（和小明的聊天/周末见）单图标、
  媒体卡进度条 `41:01 [██ 70%] 58:56` 正常。闸门 exit=0 全绿。
- **诚实结论**：展开态的重叠修复**还没有拿到目测证据**。
  下一步不该继续修把手，而应先修 `OVERLAYS` 键的生命周期（行回收时清键），
  它同时是"淡出为什么没机会执行"和"sbn 为什么取不到"的共同上游。

## 阶段 7h · 修掉 overlay 守卫的生命周期缺陷，并把"不重叠"变成可断言的功能性质

- **真缺陷**：`GUARDED` 是"装过就算"的布尔标记且**全文件没有任何 remove**。
  行回收后 `ViewTreeObserver` 换实例，旧 pre-draw 监听随之失效，但标记还在 →
  `guardOverlay` 永久早退 → 不再压原生 alpha → **我们的卡与原生内容同时绘制**，
  这就是"新旧图标重叠"的机理。修法：单独记 `OVERLAY_GUARDS`（监听器本体）+
  `OVERLAY_GUARD_VTO`（它挂在哪个 observer），observer 变了就摘旧挂新；
  `dropOverlay` 里一并清 `GUARDED`/两个映射并摘监听。
  效果可量化：修复前 `OVERLAYS` 只有 1 个键，修复后同一场景 9 个（守卫真的在重建）。
- **一次测量方法错误（差点误判成模块缺陷）**：先加的 `guard=` 用
  `v == cv.getViewTreeObserver()` 判"守卫是否还挂着"，结果 7/7 全报 0 ——
  因为 **ViewTreeObserver 在视图 attach 到窗口时会合并/换实例**，对象同一性必然为假。
  判"没重叠"要测功能性质而不是内部状态。
- **闸门新增 5c 步**：投递会话通知 → 上下滚动制造回收 → 断言
  `notify_row.verify.overlay` 每条都带 `nativeAlpha=0`（原生收起内容被压住）；
  模块侧为此上报 `nativeAlpha=`/`nativeVis=`。本轮 6 条全部 alpha=0 → 通过。
- **QS 磁贴的判定也改对了**：区分"插件根本没加载（本轮没触发到 → warn 未覆盖）"
  与"插件加载了但磁贴钩子没装（真缺陷 → FAIL）"。之前把两者混成 FAIL，
  会把"没测到"伪装成"测坏了"——本项目已经因此误判过一次根因。
  手势也换成从顶边真下拉（面板内部滑动拉不起 SystemUIPlugin）。
- 本轮闸门 exit=0：覆盖 4 类行、overlay 6 条全 alpha=0、进度条绘制 15 帧、
  批量移除 3 轮 + 媒体销毁 2 轮 pid 不变、crash 0；QS 那轮如实记为未覆盖。

## 阶段 7i · 自我纠正：`sbnOf()` 返回 null 不是缺陷，我上一轮记错了

7f/7g 里我把"A13 有 8 个可见行 `sbnOf()` 取不到 sbn"记成待查缺陷，还推测它与 overlay 键同源。
用 dexdump 把两个类的完整成员块抽出来核对后：
`ExpandableNotificationRow` 有 `getEntry()`，`NotificationEntry` 有 `getSbn()`
（`mSbn` 字段也在），**A13 上这条反射链完全成立**。
所以那些 null 是"行被回收、entry 尚未重新绑定"的正常瞬态，不是缺陷。
**教训：把"某处返回 null"直接写成待查缺陷，是拿观测当结论；要么给出失败路径的证据，要么写"瞬态、已排除"。**
真正需要修的只有 overlay 键生命周期（7h 已修）。
