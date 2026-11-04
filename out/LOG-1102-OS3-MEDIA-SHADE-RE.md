# LOG-1102-OS3-MEDIA-SHADE-RE — OS3(A13) 通知面板里 MediaStyle 音乐通知的可达性逆向

取证方式：纯静态（jadx 1.5.5 CLI 单类/整包反编译 + 现成 dexdump grep + 本机既有真机日志/截图复核），
本轮**未连设备、未新截图**。所有新产物在 `work/tmp-re-os3-media/`。

素材对照：
- OS3 SystemUI（ROM_13.5/A13，`G:\os3-ref\OS3-SystemUI.apk`）整包反编译 → `work/tmp-re-os3-media/sysui-full/sources/`（8544 个 java）
- OS3 dexdump：`G:\os3-ref\os3-dexdump-f.txt`（classes.dex@1 / classes2.dex@1244215 / classes3.dex@2451880 / plugin@3376826）
- Z8 实机 OS3 dexdump：`G:\z8os3-ref\z8os3-dexdump-f.txt`（与 os3-ref 是**不同构建**的两个 APK，但本报告的判定点两边一致）
- OS3 插件整包（既有）：`G:\os3-ref\jadx\plugin-src\sources\`
- OS5(A15) 对照：`work/systemui/SystemUI.apk`、`work/systemui/classes5.dump`、`work/systemui/classlist.fqcn.txt`
- 本机既有真机取证（只读复核）：`work/z8os3-live/*.log`、`work/z8os3-live/notif-dump.txt`、`work/z8os3-live/media_shade1.png`

---

## 1. 结论（一句话）

**不是 NMS 下发的问题，也不是"vivo 把媒体通知从列表收走"——OS3 的解锁态下拉面板本来就显示 MediaStyle 通知行；
唯一在列表构建管线里掐掉它的是 vivo 自研的 finalize filter
`com.vivo.systemui.statusbar.notification.KeyguardNotificationManager$1.shouldFilterOut()`，
且它只在锁屏态（`StateUtils.mState == 1`，即 StatusBarState.KEYGUARD）生效，i音乐(id=10001/10002) 还被单独写进黑名单。**

推论：
1. 历史记录里"OS3 面板不显示媒体通知"（SKILL-EXPORT-1028 §、LOG-1102-A11-MEDIACARD-RE §6）**前提不成立**（对 Z8/OS3 而言）；
   `out/LOG-1027-OS3-ADAPT.md:281` 的推翻是对的，本轮从"管线过滤器清单 + 反编译 + 运行时日志 + 既有截图"四条独立证据再次确认。
2. 因此 **OS3 这一半不需要"先让它出现"这个前置工程**：现有 `MediaCardFeature` 的
   `NotificationContentView.setContractedChild/setExpandedChild` 落点在 OS3 同样命中（有运行时证据）。
3. 唯一需要"让它出现"的场景是**锁屏态面板**（以及 V1913A 那类构建），钩点见 §4。
4. 有个**反向陷阱**：AOSP A13 的"媒体通知收进 QS 播放器、列表里不显示"机制（`MediaCoordinator.mMediaFilter`）
   在 OS3/OS5 两个 build 都被 vivo 焊死为常量 false，**千万别去把它打开**。

---

## 2. 证据链

### 2.1 OS3 列表构建管线里到底有哪些过滤器（穷举）

`G:\os3-ref\os3-dexdump-f.txt` 里 `Superclass : 'Lcom/android/systemui/statusbar/notification/collection/listbuilder/pluggable/NotifFilter;'`
共 15 处命中，逐个回溯持有类：

| dump 行 | 持有类 | 归属 |
|---|---|---|
| 2275429 | `...collection.coordinator.AppOpsCoordinator$1` | AOSP |
| 2275667 | `BubbleCoordinator$1` | AOSP |
| 2276215 | `DebugModeCoordinator$preGroupFilter$1` | AOSP |
| 2276349 | `DeviceProvisionedCoordinator$1` | AOSP |
| 2277175 | `HideLocallyDismissedNotifsCoordinator$1` | AOSP |
| 2277302 | `HideNotifsForOtherUsersCoordinator$1` | AOSP |
| 2277446 | `KeyguardCoordinator$1` | AOSP |
| **2277626** | **`MediaCoordinator$1`** | **AOSP（媒体收走开关，见 2.2）** |
| 2278236 / 2278283 | `PreparationCoordinator$2/$3` | AOSP（inflation） |
| 2278804 / 2278851 / 2278899 | `RankingCoordinator$4/$5/$6` | AOSP（blocked/alerting/…） |
| 2280017 | `SmartspaceDedupingCoordinator$filter$1` | AOSP |
| **3215901** | **`com.vivo.systemui.statusbar.notification.KeyguardNotificationManager$1`** | **唯一 vivo 自研**（classes3.dex，Class #4668@3215898；`shouldFilterOut(NotificationEntry;J)Z` @3215928，insns 93 units） |

⇒ vivo 在 OS3 只加了**一个**通知列表过滤器，且它自带锁屏门控（2.3）。没有任何"media 专用"的 shade filter。

### 2.2 AOSP 的"媒体通知从列表收走"机制在 OS3 被 vivo 焊死（不是它挡的）

- 过滤器本体：`work/tmp-re-os3-media/OS3-MediaCoordinator.java:29-53`
  `shouldFilterOut()` 第一行 `if (!mIsMediaFeatureEnabled || !MediaDataManagerKt.isMediaNotification(entry.getSbn())) return false;`
  → **只有 flag 为真时，才把所有媒体通知从列表里摘掉**（A13 把它们搬去 QS 播放器）；
  注册点：同文件 `attach()` :91-94 `notifPipeline.addPreGroupFilter(mMediaFilter)`（pre-group，摘得最狠）。
- flag 来源：`work/tmp-re-os3-media/MediaFeatureFlag.java:21-23` → `Utils.useQsMediaPlayer(context)`。
- **vivo 把它改成了常量**：`work/tmp-re-os3-media/OS3-Utils.java:19-21` = `return false;`
  二进制核对：`G:\os3-ref\os3-dexdump-f.txt:1791069`（`Lcom/android/systemui/util/Utils;->useQsMediaPlayer`）
  → `insns size : 2 16-bit code units`（:1791076），即 `const/4 + return`；
  Z8 构建同点位同结论：`G:\z8os3-ref\z8os3-dexdump-f.txt:1791068`（insns size 2）。
  （AOSP 原语义是读 `Settings.Secure "notification_player_apps"`，vivo 直接不读。）
- 该协调器确实被装配：`NotifCoordinatorsImpl.<init>` 形参列表含 `...coordinator/MediaCoordinator`
  （dump 行 2277827 的构造签名）。

⇒ `MediaCoordinator` 在 OS3 恒不生效 → 媒体通知**留在列表里**。

### 2.3 真正的拦截者：vivo `KeyguardNotificationManager` 的 finalize filter（锁屏态）

反编译：`work/tmp-re-os3-media/sysui-full/sources/com/vivo/systemui/statusbar/notification/KeyguardNotificationManager.java`
（同一份也在 `work/tmp-re-os3-media/KeyguardNotificationManager.java`，行号一致）

- 过滤器与注册：`:85-98`（`new NotifFilter(TAG)`，TAG=`"KeyguardNotificationManager"`）、`:136` `notifPipeline.addFinalizeFilter(mNotifFilter)`（构造函数里注册）
- **门控**：`:88` `if (!StateUtils.getInstance().isKeyguardShowingWithoutShade() || isGroupSummary) return false;`
  → 解锁态直接放行，**不挡**；
- 锁屏态判定：`com/vivo/systemui/statusbar/utils/StateUtils.java:38-40, 59-61`（`mState == 1`），
  状态由 AOSP 状态机喂入：`com/android/systemui/statusbar/phone/CentralSurfacesImpl.java:1068-1071`
  `onStateChanged(i) → StateUtils.setStatsBarState(i)`（`StatusBarState`：OFF=0 / KEYGUARD=1 / SHADE=2，
  见 `com/android/systemui/statusbar/StatusBarStateControllerImpl.java:467-498`，另有 SHADE_LOCKED 分支 :478-482）
  ⇒ **锁屏（含锁屏上拉出的面板）期间 mState 一直是 1**，解锁瞬间 `setStayingKeyguard(true)` 会
  `reBuildList("faceUnlockSuccess")` 重建列表（`KeyguardNotificationManager.java:870-875`）→ 解锁后自动回来。
- 锁屏态下的判定链 `allowedOnKeyguard()`（`:466-547`）：
  - `:521` `z3 = pkg.equals(VivoConstants.BBK_MUSIC_PKG) && (id==10001 || id==10002 || isGroupSummary)`
    （`VivoConstants.java:14` `BBK_MUSIC_PKG = "com.android.bbkmusic"`）
  - `:522-524` `if (... || z3 || "android".equals(pkg) || internalPriority<0) return false;`
    ⇒ **i音乐自己的媒体通知被写死为"锁屏不显示"**；
  - `:526-528` `if (notificationEntry.isIsMedia()) return true;`
    ⇒ **其它 App 的媒体通知在锁屏态反而是放行的**；
  - `:422-441` `shouldMutexAndHide()`：包名在插件 `KeyguardPlugin.getPlayingPackage()` 里且 `row.isMediaNotification()`
    → 与锁屏音乐 widget 互斥、直接隐藏（`:497-500` 在 allowedOnKeyguard 里 return false）。
    插件侧数据源：`G:\os3-ref\jadx\plugin-src\sources\com\vivo\systemuiplugin\keyguard\legacy\keyguard\presenter\remotewidget\KeyguardMusicWidgetManager.java:303-309`
    （`isMusicSwitchEnable()` 关 → 返回空表 → 互斥不触发；表内容来自 `com.vivo.musicwidgetmix` 的 `support_packages`）。
  - `:529-538` vivo 智能分类：`sbn.getClassifyImportance()`（vivo 框架对 `StatusBarNotification` 的扩展）
    == 1/2（"低优先"）→ 锁屏不显示。
- `isIsMedia` 这个 vivo 新增标志的写入方（只有一处）：
  `com/android/systemui/statusbar/NotificationMediaManager.java:552-590`（当前正在播放/活跃会话那条才置 true，
  dump 侧方法 `findAndUpdateMediaNotifications` @ `os3-dexdump-f.txt:1980573`），
  读取方只有 `KeyguardNotificationManager.java:526`。⇒ **没有任何 shade 侧代码消费它**。
- 全 OS3 SystemUI 里 `isMediaNotification()/isMediaRow()` 的调用点穷举（grep `sysui-full/sources`）：
  只有 `RowAppearanceCoordinator.java:65`、`NotificationViewHierarchyManager.java:265`、
  `ExpandableNotificationRow.java:1822/1927-1949/3412`、`NotificationRankingManager.java:155`、
  `VivoNotificationComparator.java:35`、`HighPriorityProvider.java:25`、`IconManager.java:266`、
  `MediaOutputController.java:502`、`NotificationEntry.java:397/441-446`、`KeyguardNotificationManager.java:205/433/594`
  —— 除 keyguard 那三处外，全是**排序/自动展开/图标**，**无一处隐藏**。
  `isBBKMusicMediaRow()`（`ExpandableNotificationRow.java:635-637`）只用于 `canViewBeDismissed()`（可清除性）。
- vivo 自研分节 `com.vivo.systemui.statusbar.notification.stack.VivoCustomSectionController/VivoCustomStackSection`
  与媒体无关（`work/tmp-re-os3-media/OS3-com.vivo.systemui.statusbar.notification.stack.VivoCustomSectionController.java`
  通读，仅一个 `nodeLabel="VivoCustomSection"` 的节头 NodeController）。
  NSSL 里 `mKeyguardMediaControllorVisible`（`NotificationStackScrollLayout.java:247`）是**死字段**（全类仅此一处，无读写）。

### 2.4 NMS 侧下发正常（排除"没下发"）

`work/z8os3-live/notif-dump.txt`（Z8/OS3 `dumpsys notification`）：
- `:592` `NotificationRecord(... pkg=com.android.bbkmusic ... id=10001 importance=2 key=0|com.android.bbkmusic|10001|null|10139 ... flags=0x62 category=transport actions=5 vis=PUBLIC internalFlag=0)`
- `:626` `android.template=String (android.app.Notification$MediaStyle)`、`android.mediaSession=Token`、`android.compactActions=[2,3,0]`、`largeIcon=Icon(BITMAP 144x144)`
- `:653` `mImportance=LOW`（LOW 不影响列表显示，只影响打扰）、`mHidden==false`、`mSuppressedVisualEffects=0`、`seen=true`、
  stats 里 `airtimeCount=3, airtimeMs=87384, airtimeExpandedMs=57350`（**有 airtime ⇒ 它真的在屏上显示过**）

`work/z8os3-live/native-smoke.log`：
- `:6334` `_V_ThirdPartyIncoming: onNotification1 add pkn=com.android.bbkmusic ... key-0|com.android.bbkmusic|10001|null|10139 ... isSeparation->true`（vivo 框架收到）
- `:6335` `ActivityManager: Notification posted: ...`（NMS 入表）
- `:6337` `_V_Jovi-LuckyMoneyService: NotificationListenerService.onNotificationPosted: key= 0|com.android.bbkmusic|10001...`（**监听器广播正常下发**）
- `:6339` `_V_KeyguardNotificationManager: onNotificationUpdate, shown = false, pkg = com.android.bbkmusic, id = 10001, getState() = 1, ... belongToKeyguard = false`
  （`shown` 就是 `allowedOnKeyguard()` 的返回值，见 `KeyguardNotificationManager.java:769-770`）

### 2.5 SystemUI 收到并真的把媒体行渲染出来了（可达性正证）

模块自带的观测（只读，不改）：
- `work/z8os3-live/20261028-1408-stack.log:2019`、`20261028-1433-fade.log:1160`、`sep-witness.log:1166`
  `BetterOriginOS: media bind n=1 cls=com.android.internal.widget.MediaNotificationView pkg=com.android.bbkmusic`
  ⇒ OS3 上 i音乐媒体通知的 RemoteViews 根（framework `MediaNotificationView`）**被塞进了
  `NotificationContentView.setContractedChild/setExpandedChild`**（钩子注册：`MediaCardFeature.java:341-342`），
  即 OS5 用的同一套落点在 OS3 直接命中。
- `work/z8os3-live/media1.log:1821-1866`（我们自己 post 的 MediaStyle 调试通知）
  `restyling ... MediaNotificationView actions=5` + `ancestor[0] NotificationContentView / ancestor[1] ExpandableNotificationRow`
  + `rowChild[1][0]=MediaNotificationView vis=0` + `notify_row: schedule mode=0 tag=media` / `mode=1 tag=bigMediaNarrow`
  ⇒ 收起/展开两套模板都走通了。
- **既有截图（本轮未新拍）**：`work/z8os3-live/media_shade1.png`（2026-10-29 12:49，Z8/OS3）
  显示"通知中心"里 i音乐媒体卡（叹云兮 / 海翼 / 0:59 — 4:41 / 5 键）就在列表**第一条**，
  与 `Android 系统`、`vivo服务`、`Dynamic System Updates` 同列。
- 反向：`grep -h "notify_row.filter" work/z8os3-live/*.log | grep bbkmusic` 共 13 条，**全部**是
  `name=KeyguardNotificationManager`（模块只在 `shouldFilterOut()==true` 时打日志，
  `module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java:625-641`），
  且同一时刻 `_V_KeyguardNotificationManager ... getState() = 1`（全部 278 条状态采样都是 1，无一条 0/2）
  ⇒ **被过滤只发生在锁屏态，且过滤者只有 vivo 这一个**。

### 2.6 顺带发现（会影响观感但不是"不显示"的根因）

- OS3 媒体行**默认就是展开态**：`RowAppearanceCoordinator.java:65` / `NotificationViewHierarchyManager.java:265`
  `setSystemExpanded(... || row.isMediaNotification())`，`ExpandableNotificationRow.java:1822`
  `notifState.defaultExpand = shouldCustomAlwaysExpand() || isMediaNotification()`
  ⇒ 移植 A11 卡时，收起/展开两套 child 都要按 A11 规格重画（`media1.log` 已见 `bigMediaNarrow`）。
- 媒体行被排到列表最前：`VivoNotificationComparator.java:35`、`NotificationRankingManager.java:155`
  （`isMediaNotification() && importance>1` 提级）、`HighPriorityProvider.java:25`。
- vivo 有节流：`com/vivo/systemui/statusbar/utils/NotificationOpControl.java:31,68-180,295-356`
  （`OP_UpdateMediaNotifications="findAndUpdateMediaNotifications"`、`NotificationAddHelper.delegateNotification`
  在面板卡顿/`SystemUIPerfOpt.isTriggering()` 时**延迟**投递），只会"晚出现"，不会"不出现"。

---

## 3. OS5(A15) 对照：为什么 OS5 可达

| 逻辑点 | OS3 (A13) | OS5 (A15) | 结论 |
|---|---|---|---|
| AOSP 媒体收走开关 `MediaCoordinator.mMediaFilter`（pre-group） | 存在，`OS3-MediaCoordinator.java:29-53`（dump 2277626/2277653） | 存在且**逻辑更强**（flag 真则直接 `return true`，`work/tmp-re-os3-media/OS5-MediaCoordinator.java:29-41`，classes5.dex） | 两版都有 |
| 其开关 `Utils.useQsMediaPlayer()` | `OS3-Utils.java:19-21` `return false`；dump `os3-dexdump-f.txt:1791069` insns=2 | `OS5-Utils.java:16-21` `return false`（`sUseQsMediaPlayer` 字段残留未用）；**反汇编直证** `work/systemui/classes5.dump:354724-354738`：`const/4 v0,0 / return v0` | **两版都被 vivo 焊死为 false ⇒ 媒体行都留在列表里** |
| `MediaFeatureFlag.getEnabled()` | `MediaFeatureFlag.java:21-23` → `Utils.useQsMediaPlayer` | `OS5-MediaFeatureFlag.java:16-18` 同 | 同 |
| vivo 锁屏媒体过滤器 `com.vivo.systemui.statusbar.notification.KeyguardNotificationManager` | 存在（唯一 vivo NotifFilter） | **整个类不存在**：`grep -c KeyguardNotificationManager work/systemui/classlist.fqcn.txt = 0`；jadx `--single-class` 报 `Input class not found`；锁屏可见性改由 AOSP `KeyguardNotificationVisibilityProviderImpl` 承担（classlist 有） | **OS5 没有任何一处会按"媒体"过滤列表 → OS5 全态可达；OS3 只有锁屏态不可达** |
| 渲染体 | framework `MediaNotificationView`（AOSP 模板），`NotificationMediaTemplateViewWrapper`、`MediaNotificationProcessor` 都在 | 同（vivo 另有 BigPicture wrapper） | OS3 的媒体行渲染链完整，可套样式 |

---

## 4. 可落地的钩点清单

作用域都是 `com.android.systemui`（**不是插件**；插件里只有 QS 磁贴，见 §4.3）。

### 4.1 让媒体行在**锁屏态面板**出现（真正的"让它出现"）

| # | 钩点 | 改法 | 触发条件（建议） | 置信度 |
|---|---|---|---|---|
| H1 | `com.vivo.systemui.statusbar.notification.KeyguardNotificationManager$1#shouldFilterOut(NotificationEntry, long)` | `before` 里对媒体条目 `setResult(false)`（整体旁路该 filter） | `feature 开 && entry.getRow().isMediaNotification()`（行未 attach 时用 `entry.getSbn().getNotification().isMediaNotification()` 兜底） | **[确定] 高**：它就是唯一会返回 true 的过滤器（§2.5 运行时 13/13），且签名/类名在 dump 3215901/3215928 可核 |
| H2 | `KeyguardNotificationManager#allowedOnKeyguard(NotificationEntry)`（public，`:466`） | 媒体条目 `setResult(true)` | 同上；比 H1 温和——保留 vivo 对**非媒体**通知的锁屏治理（儿童模式/车载/智能分类/计数） | **[确定] 高**，但注意它同时被 `:887 shouldShowOnKeyguard`、`:729/:769` 的状态统计复用，会连带影响 `mKeyguardNotificationCount` 与锁屏通知计数 |
| H3 | `KeyguardNotificationManager#allowedOnKeyguard` 内的 i音乐黑名单分支（`:521-524` `z3`） | 无法单独钩（私有分支）→ 只能靠 H2 覆盖；或 `afterHookedMethod` 里当 `pkg==com.android.bbkmusic && isMediaRow` 且返回 false 时强改 true | 仅当要"锁屏也显示 i音乐媒体卡" | **[假说] 中**：等价于 H2 的窄化版，实现要靠 H2 的 before/after 组合 |
| H4 | `KeyguardNotificationManager#shouldMutexAndHide(NotificationEntry)`（private，`:422`） | `setResult(false)` 解除与锁屏音乐 widget 的互斥 | 与 H2 配套（否则 `com.vivo.musicwidgetmix` 在 `support_packages` 里时仍被隐藏） | **[假说] 中高**：路径确定（`:497-500`→return false），但 `getPlayingPackage()` 是否非空取决于插件开关 `isMusicSwitchEnable()`，未实测 |
| H5 | `StateUtils#isKeyguardShowingWithoutShade()` | **禁止**（全局状态，影响 vivo 多处：topPadding、NSSL padding、keyguard 计数） | — | 否决 |
| H6 | `Utils.useQsMediaPlayer(Context)` | **禁止改成 true** —— 改了会激活 `MediaCoordinator` 的 pre-group filter，媒体行**彻底从列表消失**（当前是 2 条指令的常量 false，LSPosed 改它很容易，但方向相反） | — | 否决（陷阱项） |
| H7 | `NotificationEntry#setIsMedia(boolean)` / `isIsMedia()` | 只读参考，不钩（写方只有 `NotificationMediaManager:552-590`，且同一时刻只有一条为 true） | — | 说明项 |

**解锁态**：什么都不用钩——§2.2/§2.3/§2.5 已证明无过滤器命中。
若将来在别的 OS3 构建（如 V1913A）上发现解锁态也不显示，第一顺位仍是 H1（该类是 vivo 通用品，构建差异最可能体现在它的判定分支里）。

### 4.2 样式落点（OS3 与 OS5 可共用）

| 钩点 | 说明 | 证据 |
|---|---|---|
| `NotificationContentView#setContractedChild/setExpandedChild`（现有实现） | OS3 上对 i音乐媒体通知确实回调（`media bind ... pkg=com.android.bbkmusic`），A11 卡可直接沿用 | `MediaCardFeature.java:341-342`、§2.5 |
| 展开态也要画 | OS3 媒体行 `defaultExpand=true`，面板里默认看到的是 `bigMediaNarrow` 那套 child | `ExpandableNotificationRow.java:1822`、`media1.log:1866` |
| 行高/最大高 | `notification_min_height_media` 等 dimen 在 OS3 存在（`initDimens` 里读，`ExpandableNotificationRow.java:617-631`），改高度时按 `getFontScaledHeight` 语义给值 | 同上 |
| 图标重绘冲突 | `setTitleIcon()` 里 `if (!isMediaNotification()) redrawRightLargeIcon(...)`（`ExpandableNotificationRow.java:3412`）⇒ 媒体行的 large icon 不会被 vivo 的图标重绘覆盖，A11 封面可以直接放 | 同上 |

### 4.3 重复呈现风险评估（媒体行进面板 vs vivo QS 磁贴 / 锁屏音乐 widget）

1. **QS 134dp 磁贴（现状就已重复）**：`com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView`
   在插件 ClassLoader（`G:\os3-ref\os3-plugin-classes.txt`），由用户开关
   `Settings.System "control_center_display_music"`（默认 1）控制
   （`G:\os3-ref\jadx\plugin-src\sources\com\vivo\systemuiplugin\systemui\qs\Constants.java:43`、
   `.../qs/settings/QsMusicPlayerEnableGenerator.java:50,90`）。
   它出现在 QS 网格，媒体行出现在通知列表——**解锁态今天就已经是"两份控制入口"**，
   我们套 A11 样式只会让它更显眼。缓解：模块文案提示该开关；或（不建议）由模块写 0。
   风险级别：**中（观感）**，无功能冲突（磁贴走 AIDL/`IMusicWidgetMixService`，通知行走 RemoteViews PendingIntent，互不干扰）。
2. **锁屏音乐 widget（若做 H1/H2/H4 才会撞上）**：vivo 用 `shouldMutexAndHide` 明确表达过"锁屏上播放器 widget 与媒体行互斥"的设计意图
   （`KeyguardNotificationManager.java:422-441` + 插件 `KeyguardMusicWidgetManager.getPlayingPackage()`）。
   强放开 H1/H2 后，锁屏会同时出现 widget 与媒体行 ⇒ **高重复风险**；
   建议：只放开 H1（列表不被摘掉）+ **不**放开 H4，并在 `com.vivo.musicwidgetmix` 的 widget 可见时
   （`KeyguardNotificationManager#onWidgetVisibleChange`/`mIsWidgetVisible`，`:836-843`）把媒体行让位。
3. **锁屏通知计数**：`MAX_ITEM = 4`（`:55`）与 `mKeyguardNotificationCount`（`:449-459`）——多放一条媒体行会挤占锁屏通知位，
   可能触发 vivo 自己的"更多"折叠。风险：**低-中**，需真机看锁屏通知区是否被顶掉。
4. **HUN**：媒体通知的 heads-up 另受 vivo 智能分类影响
   （`NotificationInterruptStateProviderImpl.java:365-379` `getClassifyImportance()==1|2 → 不 HUN`），
   与本次改动无关，但排查"看不到"时容易混淆。

---

## 5. 需要真机才能定论（不猜）

1. **解锁态 vs 锁屏态的现场对照**：本轮所有 OS3 运行时采样里 `getState()` 恒为 1（278/278），
   从未采到"解锁 + 放歌 + 面板打开"的过滤链日志。需要一次真机采样确认
   解锁态 `KeyguardNotificationManager$1.shouldFilterOut` 确实返回 false（预期成立，但无直接采样）。
2. **锁屏上拉面板时 `StateUtils.mState` 是否仍为 1**：A13 另有 `StatusBarState.SHADE_LOCKED`
   （`StatusBarStateControllerImpl.java:478-482` 有 "Invalid state transition: SHADE -> SHADE_LOCKED" 分支），
   vivo 的 `isKeyguardShowingWithoutShade()` 只认 1；若真机上锁屏拉下时落到 3，则 H1 的前提（"锁屏被挡"）需要重述。
3. **V1913A 是否同一套代码**：`G:\os3-ref` 与 `G:\z8os3-ref` 是两个不同构建的 APK（本报告的判定点两边一致），
   但历史上"OS3 收走媒体通知"的观察来自 V1913A。需要在该机上跑一次 `notify_row.filter` 采样 +
   `dumpsys notification` 才能确认是构建差异还是别的开关（如 `control_center_display_music`、儿童模式、车载模式）。
4. **`sbn.getClassifyImportance()`（vivo 框架扩展）对 i音乐媒体通知的实际取值**：
   决定 H2 之外还要不要绕智能分类；本机无 services.jar/framework.jar 素材，静态无法定论。
5. **`shouldMutexAndHide` 是否真的触发**：取决于 `com.vivo.musicwidgetmix` 上报的 `support_packages`
   与 `isMusicSwitchEnable()` 实机状态（`KeyguardMusicWidgetManager.java:303-309`）。
6. **放开锁屏后与锁屏音乐 widget 的实测观感/计数**（§4.3-2/3），以及 QS 磁贴与媒体行同屏的观感接受度（§4.3-1）。
7. **A11 卡在 OS3 上的 dp 落值**：`work/sdk-dl/bt35/android-15/aapt2.exe dump xmltree` 只能读 A15 布局；
   OS3 的 `MediaNotificationView` 模板来自 framework（`G:\os3-ref` 无 framework-res.apk），
   收起/展开模板的真实 bounds 需真机 `uiautomator dump`（本轮禁连）。

---

## 附：本轮新增取证产物（`work/tmp-re-os3-media/`）

- `sysui-full/sources/`：OS3 SystemUI 整包反编译（8544 java，jadx CLI `--no-res -j 4`）
- `OS3-MediaCoordinator.java`、`MediaFeatureFlag.java`、`MediaDataManagerKt.java`、`OS3-Utils.java`、
  `OS3-NotificationEntry.java`、`KeyguardNotificationManager.java`、
  `OS3-com.vivo.systemui.statusbar.notification.stack.{VivoCustomStackSection,VivoCustomSectionController}.java`、
  `OS3-com.android.systemui.plugins.vivo.VivoPanelStackSection.java`
- `OS5-MediaCoordinator.java`、`OS5-Utils.java`、`OS5-MediaFeatureFlag.java`
- `enclosing.sh`：按 dump 行号回溯持有类的小工具
