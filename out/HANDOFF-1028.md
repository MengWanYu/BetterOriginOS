# 交接日志 · 2026-10-28（积分耗尽前）

> 用途：新会话接手时只读这一份即可恢复上下文。细枝末节在 `out/DEVLOG.md` 与
> `.qoder/skills/betteroriginos-dev/SKILL.md`（已同步到本轮结论）。

## 1. 目标与当前判定

用户目标（三条，原样保留）：
1. 把模块**全部四个功能**完美适配 OS3；
2. 冒烟测试直到**无 bug**；
3. 修掉**单通知展开时新旧图标重叠**，并且要"丝滑过渡"——用户明确过理念：
   **图标变化必须是同一枚图标连续地从旧位置过渡到新位置（shared element），
   不是旧图标淡出+新图标淡入**（他评价后者"有点偷懒"）。

**判定：未完成。** 三条阻塞全部需要用户动作：
- **OS5 不回归**：用户不同意切回宿主（"暂不切，保住 DSU 会话"）。本轮所有改动都在双 ROM 共用代码上。
- **heads-up（横幅）维度**：OS3 上我们的测试通知**不弹横幅**（已排除通道重要度/更新/面板状态三种原因，
  详见 §4.4）；需要用户在「设置 → 通知与状态栏」给 BetterOriginOS 开横幅。
- **文件管理器 >56 字重命名功能验证**：用户已明确**这个功能不用弄了**（2026-10-28 指示），可以从目标里划掉。

## 2. 当前构建与设备状态

- 装机包：`module/build/BetterOriginOS.apk`，sha256 前 12 位 `a73e1281812a`，**242,082 B**。
- 这版包含**尚未验证**的图标位移动画（见 §3 表最后一行）。**上一版已验证绿的包是
  `a73e1281812a`（门禁 exit=0）**，如果新包在真机上有异常，先把 morph 那段回退再谈其它。
- 设备：`10AE4Y0MDE000BL`（DSU / OriginOS 3 / A13 / sdk33 / 1080×2388 / density 3.0）。
- 已还原：`group_mode=ios_stack`、`betteroriginos_notify_verify`/`..._expand_test` 已删、
  `stayon=false`、无 `id=4242` 测试通知残留。
- 文件管理器授权页**已被用户点过**（现在是它的主界面，可用 30.98 GB）；按指示不再碰该功能。

## 3. 本轮改动清单（文件 → 状态）

| 文件 | 改了什么 | 验证状态 |
|---|---|---|
| `feature/media/MediaCardFeature.java` | 删掉 `ensureWidgetService()` / `seekViaWidget()` 两处 `isA13OrLower()` 关门 → OS3 走 vivo widget binder 真 seek；`DragSeekBar.setVisibility` 覆写：被外力藏掉时下一帧自己 `post` 要回 VISIBLE；`reclaimBar` 保留自愈 | ✅ 真机验证：`widget seek pos=2417469 ok=true` → `media_session position=2417469`，卡片显示 40:19/58:56 |
| `feature/notify/NotifyRowFeature.java` | 新增 `guardExpandedGhost`（注册在 `restyle()` 所有过滤之前，覆盖每一行）；`GHOST_TARGETS` + 逐帧补写；对称 `setChildFade`；`morphIcon` 飞行图标；`isMediaRow()` 排除媒体行；`debugExpandFirstRow` 扩展到普通行 | ⚠️ 淡变+去重已绿（门禁 gate-12/14）；**morph 未验证**（编译通过、已装机，但没抓到飞行帧） |
| `work/os3-smoke.sh` | 覆盖改成边滚边数；新增 5d「展开态图标去重 + 首次展开路径」；新增 6b「非 systemui 作用域钩子装载」 | ✅ 6b 单步确定性验证 `MPLOAD=1 PICK=1 NOLAND=1 FM=2` |
| `work/os3-probe.py` | FILES 增加 QsPlayerTile / Picker / Fileman 三个文件 | ⚠️ 探针只认字符串字面量，这三个功能用常量，仍抽不到（需手工核常量） |
| `.qoder/skills/betteroriginos-dev/SKILL.md` | 新增/纠正 4 条坑（binder 事务码、重影成因、媒体行排除、别把"改了就好"当因果） | ✅ |
| `out/DEVLOG.md` | 本轮全部证据与自我纠错 | ✅ |

## 4. 关键技术结论（最有价值，别重新踩）

### 4.1 OS3 拖动进度条必须走 vivo widget binder，事务码就是 15
- i音乐 `actions=3126` 不含 `ACTION_SEEK_TO(512)`，标准 `TransportControls.seekTo()` 永远无效。
- `com.vivo.musicwidgetmix` 在 A13 上把 `IMusicWidgetMixService` R8 混淆成
  `com.vivo.musicwidgetmix.e`，**但事务码由 .aidl 声明顺序决定、不随混淆变化**。
  核对方法：反编译 `e$a`（Stub）读 `onTransact` 的 switch ——
  `case 15: a(parcel.readLong()); writeNoException()` 即 `void seekPos(long)`；
  `case 13` 返回 long = `getPosition`、`case 16` 返回 String。
- 服务 `exported=true` 无权限门槛；descriptor 与我们写死的完全一致。
- **教训**：我曾因"搜不到 `TRANSACTION_seekPos` 字段名"断定 A13 映射不同并把功能关门，那是错的。

### 4.2 展开重影的真实机理（A13/vivo 特有）
- A13 展开时**收起子视图不会被置为不可见**；收回时**两个子视图也都还在画**。
  收起态大图标与展开态 header 小图标同时可见 → 文字正好重叠看不出，图标错位很显眼。
- `applyHidden` 走的是 **alpha**（刻意不切 visibility，避免每帧 requestLayout 吃手势），
  而**展开/收回动画会把 alpha 写回 1**；我们挂在**展开子视图自己 VTO** 上的逐帧守卫
  在 attach 后观察器已被换掉（VTO 合并坑），所以没人补写。
- 有效修法（已验证）：
  1. **绑定时刻**对展开子视图跑一次 `hideSmallIcons` + `guardSmallIcons`（等展开才做无效，ghost-9 复现）；
  2. 把要按掉的图标缓存进 `GHOST_TARGETS`（**排除 ExpandButton**，否则吃掉唯一展开入口），
     由 **contentView 的活守卫**逐帧补写 alpha；门禁实测 `ghost reapply` 2~4 次/轮。
  3. alpha 还原必须同时排除 overlay 行**和堆叠行**（`cardOwned`），否则会把堆叠卡下面
     已压住的原生组头放回 alpha=1，门禁报 `stack cards on screen but 0 verified`。
- **不要**给这段加 `OsProfile.isA13OrLower()` 之类门槛（加过 → ghost-10 复现）；
  但注意 `isA13OrLower()` 本身是 `SDK_INT<=33` 的常量，**不可能是那次回归的原因**——
  真变量是"去重发生在动画之前还是之后"。别再拿"改了就好"当因果。

### 4.3 媒体卡进度条被 AOSP 误藏的元凶
- 调用栈：`ExpandableNotificationRow.notifyHeightChanged → NotificationContentView.selectLayout
  → updateViewVisibilities → NotificationHeaderViewWrapper.setVisible → TransformState.setVisible(vis=4)`。
- 原因：我们为拿横向手势借用了 `android.R.id.progress`，AOSP 把它当成"自己那份进度条"。
- 实测**只有条被藏、卡没被藏**；改成条自己下一帧 `post` 要回 VISIBLE 后，每轮计数 14 → 1~3。
- 试过换 `com.android.systemui:id/progress_bar` 根治，但**未验证该 id 是否在滑动忽略名单里**，
  换错会吃掉拖动，已回退。要根治先证明它在 `NotifTouchInterceptor` 的忽略名单内。

### 4.4 OS3 横幅测不到（已排除三种原因）
- 通道 `mImportance=4`（HIGH）、`mOriginalImp=4`；先 cancel 再新投；`statusbar collapse` + 桌面前台
  都试过 —— 通知进列表但不弹。
- 线索：`dumpsys notification_settings` 里**没有 com.betteroriginos 的 AppSettings 条目**，
  该应用从未走过用户通知授权决策，vivo 横幅开关很可能挂在这条默认抑制上。
- 结论：门禁里 `0 headsup rows` 是**已知未覆盖**，不是回归。需要用户开横幅或授权通知。

### 4.5 四个功能的 OS3 运行时可达性（已取证）
- LSPosed 作用域四个包齐全：`lspctl scope list --user=0 com.betteroriginos` →
  filemanager / providers.media.module / systemui / systemuiplugin。
- `fileman rename patch installed os=a13 hooks=2`（主进程 + `:widgetProvider` + `.share` 都装）。
- `picker albums patch installed hooks=2`，且模块自报 `picker albums landing no-entry`
  —— OS3 没有可达的照片选择器落点，与设置页说明一致。
- 静态签名级核对（设备真实 APK，`exec-out cat` 字节一致）：
  MediaProvider TP1A.220624.014 有 `ExternalDbFacade` + `queryAlbums`/`appendWhere`；
  BBKFileManager 6.11.4.4 有 `f2.x()V`、`f2.a(File,String,String)V`、
  `l1.a(Context,File,int,String,File)V`。**混淆名按 build 变，换版本必须重核。**

## 5. 门禁怎么跑（`work/os3-smoke.sh`）

```bash
cd <repo> && bash work/os3-smoke.sh > work/z8os3-live/g.log 2>&1; echo $?
NIGHT=1 bash work/os3-smoke.sh      # 深色
FONT=1.3 bash work/os3-smoke.sh     # 大字号
bash work/os3-smoke.sh "" separate  # 分离卡模式
```
- 步骤：环境前置 → 重启 SystemUI → 投样+**边滚边数**（最多 5 轮）→ `verify_notify.py` →
  批量移除回归 → 媒体卡在场压力 → overlay 守卫回收 → **5d 展开态图标去重（含首次展开路径）**
  → 媒体卡链路 → **6b 非 systemui 作用域钩子装载** → 收尾。
- 已知 warn（非失败）：`0 headsup rows`（§4.4）、偶发「未能拉起 SystemUIPlugin」、
  「条被外力改过可见性 1~3 次」（自愈生效，§4.3）。
- **纪律**：长脚本必须重定向到文件再 tail（管道 `| head` 会用 SIGPIPE 杀掉它，跳过收尾）。

## 6. 环境 / 工具速查

- adb 必须带 `-s 10AE4Y0MDE000BL`（另有一台 `IBR8GEMFJNBECIFA`，别打错）。
- `export MSYS_NO_PATHCONV=1` 时**本地 Windows 路径也不转换**：装包用
  `P=$(cygpath -w ...)`，否则 `adb install` 报 stat 失败。
- 日志通道：`adb shell "su -c 'logcat -d -v time'"`；按 pid 过滤再加 `--pid=$(pidof pkg)`。
- 提权读设备文件用 `adb exec-out cat`（`su -c 'cat'` 会污染二进制，曾伪造出"设备版本不符"）。
- jadx CLI（`-jar` 会开 GUI；`bin/jadx.bat` 走 JDK8 会类加载失败）：
  `"<jre>/bin/java.exe" -Xmx4g -cp "<jadx-all.jar>" jadx.cli.JadxCLI --single-class <FQCN> --single-class-output out.java -d tmp <apk|dex>`
- dexdump：`work/sdk-dl/bt35/android-15/dexdump.exe -f classes.dex`，流式 grep，**别落盘**（C: 只剩 ~7G）。
- 构建：`cd module && ./build.sh`；失败时 `build-log.txt` 是 **GBK**，
  用 `iconv -f gbk -t utf-8` 或 python 解码，否则 grep 不到"错误"。
- 屏幕会自己睡（`stayon true` 在本机不可靠）：**唤醒+解锁+操作必须在同一条 adb 命令里做完**，
  每条命令都 `input keyevent 224` + `wm dismiss-keyguard` 起手。
- 解锁：`wm dismiss-keyguard`；截图：`adb exec-out screencap -p > x.png`。

## 7. 下一步（按优先级）

1. **验证/收尾 morph**（当前包未验证）：
   - 真机展开一条通知，抓 `icon morph open=1 from=... to=...` 日志与中途帧；
   - 确认落点后目标图标交还可见（`withEndAction` 里两侧都 restore alpha=1）；
   - 若飞行图标被行裁切，把 flight 视图挂到 `NotificationStackScrollLayout` 或用
     `contentView.setClipChildren(false)`（行内已有部分行为会设）。
2. **回归**：跑四组门禁（§5）。morph 改动只碰 `guardExpandedGhost`，5d 应仍绿。
3. **等用户**：OS5 切宿主跑一轮（最重要）；给 BetterOriginOS 开横幅后把 heads-up 做成门禁步骤。
4. 文件管理器功能：**按用户指示不再推进**（代码保留，门禁 6b 仍会断言它装载正常）。

## 8. 反面教训（新会话请先看这段）

- 别对"已验证的修复"做顺手美化：本轮两次把重影修回去（ghost-9/ghost-10），
  都是因为改了"看起来更对"的实现。**先把失败场景自动化，再改。**
- 别把"改了就好了"当因果：一次回归被我归因给 `isA13OrLower()`，而那是常量。
- 门禁的绿灯只覆盖它断言过的维度：`0 headsup rows` 一直是绿的，但那一维从未被覆盖。
- 屏幕睡眠、前台应用、面板展开态会静默污染测试结论；`mCurrentFocus` 在睡眠时是**陈旧值**，
  必须和 `mWakefulness=Awake` 配对判断。


## 9. 2026-10-28 23:20 追加（图标 morph + 堆叠胶囊已落地）

- 装机包：sha256 前缀 **`a73e1281812a`**（比 §2 的 d8627e5ec132 新）。
- 单行图标切换：只存在 38dp 大图标；原生 18dp 小图标全程隐藏。翻转时预建的 flight（38dp）
  从 `48,71x114` 平移到展开态 `48,119x114`，弹簧曲线 240ms；`mid` 日志三枚图标全 alpha 0。
  两个"闪现"根因：① preDraw 里 addView 丢一帧 → 改为绑定时预建；② 首次展开的 expanded 子视图
  后加、盖住 flight → morph 时 `bringToFront()`。
- 堆叠展开：正面卡=第一枚胶囊，其余子条各自独立胶囊（20dp 圆角、8dp 间隙）；箭头改用原生
  `NotificationExpandButton` drawable 并 180° 弹性翻转；`applyExpansion` 在 `open>0.004` 时
  必须保持 rowContainer gravity=TOP（open==1 回落 CENTER_VERTICAL 会把正卡压到胶囊上）。
- 调试把手：`betteroriginos_notify_stack_test` 现在是**目标态**（1=展开最上面一叠，0=收起）。
- 门禁：5e（morph）与 5f（堆叠胶囊）已加入 `work/os3-smoke.sh`，三组全绿
  （`work/z8os3-live/g-2305.log`、`g-sep-2305.log`、`g-night-2305.log`）。
- 仍待用户：OS5 宿主回归；横幅开关；真机主观确认"丝滑度"。

## 10. 2026-10-29 追加（堆叠原生行为 + 底部留白 + 收纳栏回归修复）

- 当前装机包：sha256 前缀 **`441b0fde40ad`**（`module/build/BetterOriginOS.apk`）。
- 堆叠通知：展开态=多枚胶囊（正面卡=第一枚），每枚胶囊走各自原生行点击（`performClick`）/
  长按（`doLongClickCallback`）、失败兜底 `cancelNotificationWithTag`；`postjump` 演示通知可验证
  逐条跳转与 autoCancel。折叠态堆叠露边、展开后列表可滚动均已绿。
- 底部留白：`NotificationShelf` 透明占位（VISIBLE + alpha 0 + 28dp），NSS `onLayout` 自身触发，
  不再依赖堆叠行；`debug scroll to=244 content=2584 h=2340` + 多帧截图确认末条完整可见。
- **收纳栏若隐若现（本轮修复）**：AOSP shelf 淡入动画会在任意帧把 alpha 写回 1。已给 shelf 挂
  逐帧 `OnPreDraw` 守卫（换 VTO 重挂），`alphaFix` 计数证明写回确实频繁发生且全部被压回 0。
  门禁 `work/z8os3-live/gate_shelf_fix.out` **exit=0**。
- 设备已还原：verify/scroll_test/stack_test/overlap 全 0、`stayon=false`、调试通知已清。

## 11. 2026-10-29 追加（收纳栏圆角根因 + 音乐 App 闪退）

- 装机包 sha256 前缀 **`6ccb7a2441c2`**。
- 收纳栏顶角直角根因（dexdump 代码级）：`ActivatableNotificationView.applyBackgroundRoundness(top,bottom)`
  对 shelf 传 top=0 → `NotificationBackgroundView.setRadius(0,45)` → vivo `VRoundedCornerDrawable`
  顶部两角布尔 false。修复：setRadius 钩子把 shelf 的 top 对齐 bottom；shelf/NSS 生命周期补校正；
  `NotificationBackgroundView.onDraw` 按签名兜底直接写 `mCornerRadii` 与 drawable 四角（无裁剪、无盖层）。
- 音乐 App 闪退根因：媒体进度条 `DragSeekBar` 覆写 `setVisibility(int)`，RemoteViews 按
  `android.R.id.progress` 反射调用时缺 `@RemotableViewMethod`，抛
  `can't use method with RemoteViews` → 系统杀发通知的 App（真机 crash 6 次）。已删除覆写，
  可见性由 `reclaimBar()` 300ms 自愈；点卡片进 App、连续切歌均不再崩溃。
- 门禁 `work/z8os3-live/gate_final.out` exit=0（5d/5e/5f、媒体进度条与自愈全绿）。
- 设备已还原：verify/scroll_test/stack_test/overlap=0、stayon=false、播放已暂停。

## 12. 2026-10-29 追加（回退实验 + “通知条美化/样式”结构）

- 装机包 sha256 前缀 **`dabc6ecc2106`**。
- 回退：删除收纳栏交界的两层裁剪/隐藏钩子与 shelf 末尾跟随实验；恢复 `6ccb7a2441c2` 行为
  （原生重叠 + 顶角圆角修复 + 音乐闪退修复）。
- 结构：功能改名“通知条美化”，新增“样式”选项，当前仅“iOS 风格（测试版）”；原生样式预留后续。
- 已知问题（测试版维护）：收纳栏顶边与最后一条通知的半截遮挡；反复滑动后最底部通知难完全
  划出、继续上滑会关面板（原生 overscroll 行为）。
- 设备设置已还原（verify/scroll/stack/overlap=0、stayon=false）。

## 13. 2026-10-29 追加（原生通知样式落地）

- 装机包 sha256 前缀 **`b6c0ed9d8a5a`**；当前设备 style=native、verify=0。
- 「通知条美化 → 样式」新增原生样式：Application.onCreate 门闩读样式，native 时**不装任何
  通知条钩子**，AOSP 排版/绘制原样保留（不是仿制）。
- 验证：`work/os3-smoke-native.sh` exit=0（零钩子 + 只读 verifier 结构断言）；iOS 回归
  `work/os3-smoke.sh` exit=0。只读 verifier 仅在 verify=1 且 style=native 时临时安装。
- 样式弹窗经 uiautomator 层级确认双选项；点选原生后设置键为 `native`。
- 媒体卡属独立功能，不随通知条样式切换（原生样式下媒体行仍由 media_card 功能决定）。

## 14. 2026-10-29 追加（样式语义修正 + 原生优化样式）

- 装机包 sha256 前缀 **`3c1739527992`**。
- 删除“原生=零钩子”路径（与总开关关闭等价）；现在：关闭总开关=系统默认，
  开启后样式可选 iOS（测试版）或「原生优化（大图标+标题）」。
- 原生优化：保留系统卡片/背景/圆角与原生分组；独立通知做 38dp 大图标+标题排版；
  分组摘要/子行完全不重排（修复与标签栏割裂）；标题正文皆空的行不接管（防纯白空卡）。
- A13 圆角钩子按样式分流，light 不再强推 20dp。
- 门禁：`work/os3-smoke-light.sh` 连续两次 exit=0；`work/os3-smoke.sh` exit=0。
- 设备：style=light、feature=1、verify=0。当前改动尚未提交 git（初始提交为 a25313f）。

## 15. 2026-10-29 追加（原生优化样式返工）

- 轻量样式不再复用 iOS 排版手术：仅放大原生 header 图标 + 调标题字号，原生几何/行高全部保留；
  改动后调用 refreshRowLayout 通知列表更新行高。
- 分组摘要/子行、HeadsUp、媒体行保持原生；空内容行不接管。修复此前空白与大间隔。
- 门禁：work/os3-smoke-light.sh 两次 exit=0；work/os3-smoke.sh exit=0（5f 因 separate 未覆盖）。
- 构建 sha256 前缀 `3c1739527992`。

## 16. 2026-10-29 追加（轻量样式间距修复）

- 轻量样式 25px 缝隙根因：iOS 的 stack metrics（8dp 间距/16dp 缩进）在 NSS/算法层全局生效，
  未按样式分流。已在 applyStackMetrics 与 StackScrollAlgorithm.initView 钩子加 light 早退。
- 复核：轻量像素白带与原生一致（665..2231 连续）；light 门禁两次 exit=0；iOS 门禁 exit=0。
- 构建 sha256 前缀 `468624aa939d`；设备保持 style=light、feature=1、verify=0。

## 17. 2026-10-29 追加（原生优化大图标）

- 图标从“扁 38x26dp”改为“等比缩放 38x38dp、header 内居中”：scale=1.4615、ty=-24px，
  不改 header/行高/间距；只读探针记录几何。
- 门禁：light exit=0（12 条独立通知）、像素白带连续 665..2231、iOS exit=0。

## 18. 2026-10-29 追加（0.1.1-beta 发布）

- versionCode=2 / versionName=0.1.1-beta；成品 `out/BetterOriginOS-0.1.1-beta.apk`。
- 门禁：light exit=0；iOS(separate) exit=0；iOS(ios_stack) exit=0（5f 多胶囊全绿）。
- 设备：style=light、group_mode=separate、feature=1、verify=0。

## 19. 2026-10-30 追加（离线修复，待真机验证）

- iOS 空白卡根因：dropOverlay 未还原 overlay 期间的 root.alpha=0；已还原 alpha/visible/a11y，
  并对空会话内容跳过 overlay。
- 原生优化大图标：不再缩放 header 小图标，改为独立 38dp 应用图标覆盖在原生图标槽位；
  小图标隐藏、槽位宽度 38dp、header 布局与行高不变；点击/长按转发原生行。
- 离线构建通过；设备被占用，尚未跑 light/iOS 门禁与空白复现。

## 20. 2026-10-30 追加（离线审计第二批）

- 修复 guardStack 孤儿守卫：记录真实 VTO，换实例时先摘旧监听；守卫每帧检查 overlay 是否还在，
  避免继续把复用行压成 alpha 0（iOS 空白）。
- 轻量大图标：槽位 LP 可还原、屏幕坐标定位、原生小图标 alpha 逐帧兜底。
- 构建通过；设备仍被占用，门禁未跑。0.1.1-beta zip 未包含这些修复。

- 追加：轻量 polish 跳过展开子视图，展开态保持原生。离线构建 `2680ac1b723a`，未装机验证。

## 21. 2026-11-02 OS5 主系统适配（light）

- Z8 主系统为 OriginOS 5 / Android 15；此前验证都在 OS3 DSU。
- light 在 OS5 改为：42dp 彩色应用图标 + 66dp 文字列，覆盖展开行与自动归组行。
- 标题上提用布局实现（header 34dp + 文字容器 topMargin -8dp），避免 translation 被 header 遮挡。
- 展开/收起复用 iOS morph（240ms spring），仅 OS5 启用；OS3 light 行为不变。
- 装机 sha256 `650ff4cd4539…`，style=light、verify=0；UI 树与 morph 日志已核验。
