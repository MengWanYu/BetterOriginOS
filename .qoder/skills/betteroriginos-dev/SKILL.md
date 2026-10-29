---
name: betteroriginos-dev
description: BetterOriginOS（iQOO Z8 / OriginOS 5 LSPosed 模块）的开发与调试指南。凡涉及本项目的构建安装、新增功能（Feature）、Hook/预览/选择器开发、设置 App UI 修改、真机验证或问题排查，一律先读本 skill——即使用户没有明说"按 skill 来"。
---

# BetterOriginOS 开发指南

LSPosed 模块：Hook `com.android.systemui`（通知中心媒体卡 + 真实预览渲染）与
`com.android.providers.media.module`（照片选择器相册白名单修复）。设置 App 为纯 Java
View 体系（无 androidx），OS5 视觉规范。

先读设计规范 `out/DESIGN-SPEC-1025.md`（token/组件/交互），历史与踩坑细节在 `out/DEVLOG.md`。

## 1. 项目事实

- 代码：`module/src/com/betteroriginos/`；资源 `module/res/`；产物 `module/build/BetterOriginOS.apk`。
- 设备 adb `10AE4Y0MDE000BL`，无密码锁（上滑解锁）；KernelSU + LSPosed。
- 作用域两个：`com.android.systemui`、`com.android.providers.media.module`。
  推荐作用域声明在 `res/values/arrays.xml` 的 `xposedscope`，**加新 scope 时两处都要改**。
- 构建链：`module/build.sh`（aapt2→javac→d8→注入 dex→zipalign→apksigner），无 Gradle 无 androidx。
  路径由脚本位置推导，不要硬编码项目路径（用户会改目录名）。

## 2. 构建 / 安装 / 生效

```bash
cd module && ./build.sh          # 产物 129KB 左右
adb install -r build/BetterOriginOS.apk
# scope 变更才需要：lspctl scope add --user=0 com.betteroriginos <pkg>
# 生效需重启对应进程：kill $(pidof com.android.systemui)   ← 会锁屏，解锁=上滑
```

- 构建失败先看 `build-log.txt`（GBK 编码，用 python 解码再 grep"错误"）。
- 新用到的 Xposed API 必须**先补编译桩**（`module/stubs/de/robv/android/xposed/`），
  否则 javac 报找不到符号；桩只在编译期，运行时由 LSPosed 提供实现。
- Git Bash 传 Windows 工具的路径要 `cygpath -m`；操作 `/system`、`/apex` 的 adb 命令加
  `export MSYS_NO_PATHCONV=1`。

## 3. 新增 Feature（检查清单）

1. `feature/<area>/XxxFeature.java`：id/titleRes/summaryRes/categoryRes/iconRes/scopes/install；
   legacy 配置键在 static 块注册到 Prefs（Prefs 本体不感知具体功能）。
2. `FeatureManager.FEATURES` 注册 → 主页自动出现该应用卡片。
3. `strings.xml` 文案；选项带预览图时给 `iconRes`（下拉菜单自动展示）。
   若该功能在某些系统上没有可作用的落点（或行为受系统限制），重写
   `Feature#supportNoteRes(ctx)` 返回一句 ≤2 行的说明（渲染成功能卡摘要下的 `feature_note`），
   别留一个静默失效的开关；返回 0 时该控件 GONE 不占位，另一套系统渲染不变。
4. LSPosed 加 scope + 重启目标进程；`arrays.xml` 的 xposedscope 同步。
5. Hook 全程 try/catch + 日志，失败回退系统原生行为（Mainline 模块会升级，别写死）。

## 4. 高频坑（每条都真实踩过）

- **Context 错包**：通知/RemoteViews 的 View.getContext 是发起通知应用的
  createPackageContext（如 bbkmusic）。拿 SystemUI 本体上下文用
  `ActivityThread.currentApplication()` 反射（AndroidAppHelper.currentApplication 在此环境为 null）。
- **App 的 su 会话读不到其他应用文件**（KernelSU 命名空间隔离 → ENOENT），且 su 管道污染二进制。
  跨进程传大块数据用 **MediaStore**（App 加 READ_MEDIA_IMAGES，adb pm grant 一次）。
- **App 内 ProcessBuilder 读 su 命令输出必须边读边等**：先 waitFor 会因 >64KB 管道缓冲死锁。
- **PrecomputedText**：真实通知标题不能直接 setText 到别的 TextView，先 `.toString()`。
- **Xposed setResult(null)**：被 hook 方法返回值会被调用方使用时（如 addAll），必须给空集合而非 null。
- **MediaProvider picker 库**：所有 DB 操作必须走 `runWithoutTransaction/runWithTransaction`。
- **Feature.options() 已缓存**（模板方法 buildOptions）；别在子类重写 options() 导致每次 new、引用失配。
- **ListView 无 item margin**：分组卡间距用透明 spacer 行；行级圆角用 bg_item_top/mid/bot。
- **Switch 自定义 thumb/track drawable 必须带 `<size>`**，否则 Switch 测量塌缩成几像素。
- **可点击性即提示**：行点击若不作为开关用途就去掉 clickable——控件职责要单一、明确。
- **OS3 没有"通知中心媒体卡"**：MediaStyle 通知被 vivo 从通知列表收走（NMS 里有记录但折叠面板不显示），
  播放器是 QS 网格里一枚 134dp 磁贴 `com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView`，
  且**在 SystemUIPlugin 的 ClassLoader 里**（SystemUI dex 搜不到）。OS5 那套"改媒体通知行"在 OS3 无落点。
- **别借用系统视图的 id**：给自绘进度条设 `android.R.id.progress` 是为了过
  `NotifTouchInterceptor` 的手势白名单，但 `MediaNotificationView` 正是用这个 id 找它自己的条
  并控制显隐，结果把我们的一起设成 `INVISIBLE + alpha 0`（`View.draw()` 对 alpha==0 直接早退，
  现象是"进度条时有时无"）。必须借 id 时，配一个每帧纠正可见性/alpha 的自愈（`reclaimBar`），
  并在闸门里断言 `SeekDrawable.draw` 真的被调用过。
- **`hookAllMethods` 只匹配类自己声明的方法**：媒体卡靠 `NotificationContentView.setContractedChild/
  setExpandedChild` 绑定，A13 上 vivo 的行有时不走这两个方法 → 卡不建、露出原生无条卡。
  所以要有 dispatchDraw 级的补扫（`installSweepHook`），并在补扫里只认 `MediaNotificationView`
  （`MediaContainerView` 是外层容器，传进去找不到 media_actions）。
- **A15 的字段名不能用在 A13**：`NotificationChildrenContainer` 在 A13 只有
  `mChildPadding/mDividerPadding/mNotificationTopPadding`，没有
  `mSidePaddings/mMinimumPaddings/mPaddingBetweenElements/mCornerRadius`。
  反射写字段要**逐个独立 try**（一个抛错别让整段中止），并打印 `applied=N/total`。
- **改 `core/Feature`、`Prefs`、`OsProfile` 这类共享类 = 改 SystemUI 里正在跑的代码**：
  模块 dex 是被注入进目标进程的，core 层任何改动都要复跑整套 `work/os3-smoke.sh`，
  不能只看设置 App 能不能打开。
- **uiautomator 的 text 是 UTF-8，看着像乱码是控制台的问题**：Windows 控制台按 GBK 渲染 adb 输出，
  中文在 shell 里全是 `???`。要判文案/坐标就把 dump 存文件后用 Read 看，别在 shell 里 grep 中文。
- **注入成没成，只认 zygote maps**：`su -c "grep -icE 'zygisk|lsposed' /proc/$(pidof zygote64)/maps"`；
  lspd 守护进程在跑、模块 db 已写、管理器显示"已启用"都不代表注入发生。

- **OS3 收起态没有动作按钮**：vivo OS3(A13) 的动作带是 `android:id/actions_container`（**行的直接子级，
  在模板根之外**，AOSP 老名 `action_list` 这台机上不存在），收起时它是 INVISIBLE，按钮只在展开态。
  所以 iOS 化把展开箭头藏掉 = 让按钮永远够不着。判据用 `hasInlineActions`（从**行**往下 DFS），
  这类行要保留箭头、不拒绝展开、不强制收起。
- **抹 `StatusBarNotification.getGroupKey()` 为 null 会在批量移除时崩**：一键清除（NMS.dismissAll）
  时 GroupMembershipManager/HeadsUpCoordinator 拿 null key 查表 → NPE 崩溃循环。拆组只改 `isGroup()`
  + 在 ShadeListBuilder 侧摘 summary，别动 key。
- **pre-draw 监听与 post 出去的延后任务不在 Xposed 异常保护范围内**：`onPreDraw`、`post/postDelayed`
  里的异常直接带走主线程。统一过 `safe(Runnable)` / `preDrawGuard(Runnable)`。
- **视图树探测只缓存正结论**：负结论常是"节点还没 inflate"或"作用域不对"（`rowOf` 只认 contentView，
  行内任意节点要用 `rowAncestor` 爬父链）；缓存负结论 = 永久假阴性。
  另外通知视图的 context 是发通知 App 的 resources，`getIdentifier(...,"android")`+`findViewById`
  这类快路径可能量不到，同一棵子树的 DFS 反而每帧都命中。

- **OS3(A13) 上没有可达的 Photo Picker**：`android.intent.action.PICK_IMAGES` 无任何解析、
  `GET_CONTENT image/*` 被 vivo 路由到 DocumentsUI 的 `picker.PickActivity`，
  直接点 `photopicker.PhotoPicker*Activity` 报"类不存在"。所以 PickerAlbums（改 `ExternalDbFacade`）
  在 OS3 上钩子会装但永不触发。模块已加延后自检，日志 `picker albums landing no-entry|reachable`
  —— **钩子装上 ≠ 有落点，判空转看这条**。
- **跨版本改动一律先按版本关门**：只在 A13 上验证过的行为改变（如"保留展开箭头、不钳高度"）
  必须过 `OsProfile.isA13OrLower()`，否则等于在 OS5 上偷偷做未验证的行为变更。

- **vivo OS3 静默丢弃自定义 action 的广播**：`am broadcast -a <自己的 action>` 返回
  `Broadcast completed: result=0` 但 receiver 根本不跑，logcat 里是
  `_V_VivoAmsImpl: vivo protected broadcast … send by null should with permissions`。
  调试入口只能走 Activity；Activity 会盖住 heads-up，所以投递前先 `moveTaskToBack(true)` 再延迟发。
- **同一 App 的多条通知在 OS3 会被系统自动归组**：取证时看到的可能是堆叠卡而不是独立行；
  要拿独立行样本就换不同 platform/id 且不要同一时间批量投（`postact` 两条即独立行）。

- **给"所有通知行"加通用抑制时，必须排除媒体行**：`CATEGORY_TRANSPORT` 那条行归
  MediaCardFeature 独占自绘，notify_row 若对它跑 `hideSmallIcons(展开子视图)`，
  原生展开态的封面副本会露出来（实测媒体卡右上角多出一枚小封面）。判据用
  `sbn.category==CATEGORY_TRANSPORT` 或 `contracted` 类名含 `Media`。

- **OS3 展开重影的真实成因**：A13/vivo 展开时收起子视图不会被系统置为不可见，
  收起态的大图标与展开态 header 的小图标同时画出（文字正好重叠看不出来，图标错位就很显眼）。
  修法要**无状态**：对展开子视图按掉 header 小图标即可，别去猜"哪一侧该淡出"——
  按 `isExpanded`/几何判据在这台机上两种形态都有反例（展开子视图 INVISIBLE、
  或行被撑高但内容仍由收起子视图承载），猜错就是整行空白。
- **重影修复上两个"看起来更对却实测失效"的改法**（都回归过，别重犯）：
  ① 改成"检测到展开残留时才动手"→ 两枚图标照样并存：A13 展开子视图懒加载，且**展开动画会
  重新写回子视图可见性**，在动画期间/之后动手会被覆盖。
  ② 任何"把原生 alpha 还原"的逻辑必须同时排除 overlay 行**和堆叠行**，否则会把堆叠卡下面
  已压住的原生组头放回 alpha=1（门禁报 `stack cards on screen but 0 verified`）。
- **别把"改了就好了"当成因果**：我曾把一次回归归因给 `OsProfile.isA13OrLower()` 门槛，
  但该函数就是 `Build.VERSION.SDK_INT <= 33`（常量，不可能在这台机上为假）——
  真正变量是**去重发生在展开动画之前还是之后**，同一份代码会因行的历史（展开子视图
  是否已懒加载出来）而时好时坏。所以这条修复目前**仍是时序依赖的**，
  要判它可靠必须测"从未展开过→首次展开"这条路径。

- **裸 binder 事务号要核对，但"类名被混淆"不等于"映射变了"**
：AIDL 生成的 `Stub` 里
  `TRANSACTION_xxx` 只是 Java 字段名，R8 会把它混淆掉（A13 上
  `IMusicWidgetMixService` 变成 `com.vivo.musicwidgetmix.e`，dexdump 搜不到 `TRANSACTION_seekPos`），
  但**事务码由 .aidl 声明顺序决定，跨 ROM 同一份 AIDL 就不变**。核对方法：反编译 `e$a`（Stub）
  看 `onTransact` 的 switch——`case 15: a(parcel.readLong()); writeNoException()` 就是
  `void seekPos(long)`（对比 `case 13` 返回 long = `getPosition`，`case 16` 返回 String）。
  实测 OS3 的 15 号与 OS5 一致，拖动进度条 seek 真机生效（session position 精确等于下发值）。
  之前据此把 A13 关门，是只看字段名就下结论——**别拿"名字找不到"当"值不同"的证据**。

- **`MediaSessionManager` 也必须用 SystemUI 本体 context**：A13 的 MediaSessionService 会校验
  "包名属于调用 uid"，用通知视图的 context 取服务会让 `TransportControls.seekTo()` 抛
  `IllegalArgumentException: packageName does not belong to the calling uid`（OS5 上被 widget
  兜底路径掩盖）。一律走 `MediaCardFeature.sysuiContext(fallback)`。
- **通知行里的自绘控件收不到横向手势时先别怪拦截器**：本项目实测进度条是能收到 DOWN 的，
  真正的问题是上面那条 uid 校验；眼估截图坐标也不可靠——让模块把
  `getLocationOnScreen` + 宽高打进日志（`barAt=`），按日志坐标去拖。

- **`setResult(null)` 只有在被 hook 方法返回 void 时才安全**：OS5 一键清除重启的确切机理是
  `getGroupKey`（返回 String、被批量移除的 key 查表消费）被置 null。改任何 `setResult(null)` 之前，
  去 dexdump 查签名的 **V/非 V**（本项目现存 4 处全是 void：`sanitizeTopLine`、
  `updateIconsForLayout`、`updateVivoIconsForLayout`、文件管理器 `f2.x()V`）。

- **`MediaController` 的 getter 不会把 DeadObjectException 抛出来**：`getMetadata()`/`getPlaybackState()`
  内部吞掉 RemoteException 返回 null，所以"会话被清掉时主线程读状态会崩"是**假说，不是实测**。
  别把它当根因（本项目的真因是 getGroupKey + 未保护的 pre-draw/deferred）；主线程回调该兜还是兜，
  但兜底日志计数为 0 时结论应写"没撞上"，而不是"修好了"。

## 5. 真机调试技巧

- 截图 `adb exec-out screencap -p > x.png`；UI 结构 `uiautomator dump` + pull 后 grep bounds。
- 盲 tap 验证前先 dump 拿精确坐标；注意弹窗/IME 会让 dump 失败或 tap 落空。
- 长时间验证前 `adb shell svc power stayon true`（结束时记得恢复 false）；
  kill systemui/media module 必然锁屏，流程：keyevent 224 → 上滑 swipe → 再操作。
- Hook 侧日志：`adb logcat -d | grep BetterOriginOS`；LSPosed 注入确认看
  "loaded <pkg>" 与 "installed feature <id>"。
- **vivo OS3（V1913A）日志通道是残的**：shell 读 `logcat` 只有 2 行，必须 `adb shell "su -c 'logcat -d -v time'"`；
  且 `log.tag=E` 会吞掉所有 `Log.i`——`setprop log.tag I` + `persist.log.tag I` 后**必须重启目标进程**
  （该属性在进程启动时读取，已起的 zygote 子进程仍然哑）。按 pid 过滤：`logcat -d --pid=$(pidof <pkg>)`。
- Android 13 起 `POST_NOTIFICATIONS` 是运行时权限：调试通知投不出去先 `pm grant com.betteroriginos android.permission.POST_NOTIFICATIONS`
  （`appops get <pkg> POST_NOTIFICATIONS` 在这台机上会报 Unknown operation，别用）。
- 用户手机上可能同时有人在操作——tap 结果异常先截图确认当前界面再继续。

- **Z8/OS3 的 uiautomator 把 `class=` 全部抹平成通用控件**（实测：面板展开、110 个节点，
  自定义类名 0 个，通知行只剩 `android.widget.FrameLayout/LinearLayout/TextView`）——
  所以**按类名（`Button`、`ExpandableNotificationRow`、`actions_container`）grep dump 一定为 0，
  这不是"控件不存在"的证据**。resource-id 是可信的（`com.android.systemui:id/expandableNotificationRow`、
  `android:id/notification_main_column` 都正常导出），要判控件在不在，
  用 `notify_verify=1` 的 `notify_row.witness` 度量（带 `act=vis x h/kids` 与逐按钮 alpha）
  + 截图，必要时做 A/B：`settings put global betteroriginos_feature_notify_row 0` 重启 SystemUI 看原生基线。
- **dump 前必须确认面板还在**：息屏或 fling 收起后 dump 会静默给出 launcher 树（本项目因此连吃三次
  无效样本）。流程：`svc power stayon true` → `cmd statusbar expand-notifications` →
  `dumpsys window | grep mCurrentFocus` 确认是 `NotificationShade` → 再 dump；失败的样本另存别覆盖。
- **核对"设备上装的是不是这个构建"要这样取文件**：`pm path <pkg>` 拿 base.apk 后用
  `adb exec-out cat` 直读再比 sha256；**过 su 管道会把二进制污染出多余字节**
  （实测 233,890 → 234,638），会让人误判成"版本不对"。先比大小再比哈希即可分辨传输脏 vs 真不同。
- **Android 位标志别凭记忆写**：`FLAG_ONGOING_EVENT=2`、`FLAG_NO_CLEAR=32`、
  `FLAG_FOREGROUND_SERVICE=64`（我按 0x40/0x20 记，正好把 ongoing 漏掉，新断言第一次真跑就误报 6 条）。
  要用 `javap -constants -cp <android.jar> android.app.Notification` 核；
  并且**同一条策略要一致地应用到所有相关字段**（只豁免 `entryClearable`、却仍要求
  `rowCanDismiss=1`，就会把系统本来正确的"不可滑动清除"判成我们的缺陷）。
  新断言必须配正负对照：真实日志 PASS + 篡改日志精确 FAIL。
- **度量日志必须取"真正渲染这一行的那套资源"**：`ctx` 是发通知 App 的 `createPackageContext`，
  它的 `Configuration` 可能是改字号/主题前的旧值——实测设备 1.3、文字已 58px，日志还记 `fontScale=1`，
  而校验器按这个值缩放容差，等于大字号下断言被悄悄放宽。取 `root.getContext()`（已 attach 到窗口）。
- **`SystemUIPlugin` 是懒加载进 systemui 进程的**：没画过一次 QS 面板就不会有
  `qs player tile installed`，所以断言磁贴钩子前必须先 `cmd statusbar expand-settings`（再 collapse），
  否则这条检查会随"本轮有没有人开过 QS"忽绿忽红。
- **带收尾责任的长脚本不能接 `| head`**：head 退出触发 SIGPIPE 会把脚本中途杀死，
  跳过最后的恢复步骤（本项目因此把设备留在深色模式 + stayon=7 + 通知没清）。
  要整跑落盘（`> run.txt 2>&1`）再看 tail，或 `tee`。
- **`mCurrentFocus` 在息屏后是脏的**：这台机 sleep 之后它仍会停留在 `NotificationShade`
  （本轮两次误判"有人在用设备"都是这个原因）。判断能不能动设备必须两个信号一起看：
  `dumpsys power | grep mWakefulness=Asleep` + `mCurrentFocus`；Asleep 就是没人用，可以放心唤醒操作。
- **设备可能正被人使用**：发任何输入事件前先看 `dumpsys window | grep mCurrentFocus`；
  快速 swipe 会被当成 fling 把面板甩掉，列表内滚动要用 900ms 慢滑。

- **测量必须在可视区里跑**：行被挤到列表下方时 `isShown()` 不成立，重试会耗尽。
  普通行现在会打 `notify_row.verify.skipped …`，校验器对"只有 skipped 没有成功测量"直接 FAIL；
  自己造测试通知时记得先把列表滚到它们可见。

- **异常计数型断言必须按 pid 划界**：冒烟脚本第 2 步自己 kill 过 SystemUI，
  全 buffer 的 `grep -c DeadObjectException` 会数到 11 条 system_server 对旧 binder 的正常抱怨
  （`TaskOrganizerController`、`PlaybackActivityMonitor`、`DisplayManagerService`），与被测模块无关。
  用 `logcat -d --pid=$P`；判"是不是我们引起的"唯一可信的是逐条看栈里有没有 `com.betteroriginos` 帧。
- **一次跑完就要把关键样本的日志留档**：`com.bbk.appstore` 端距异常的原始 log 被后续跑覆盖后
  再无法复核，遗留项只能降级成"假说"。有价值时立刻 `cp` 到 `work/z8os3-live/` 另存。

## 6. 逆向与取证工具链

- **jadx CLI 只能用这种姿势**（`-jar` 会启动 GUI；`bin/jadx.bat` 走 PATH 上的 JDK8 会类加载失败）：

  ```bash
  J="/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre/bin/java.exe"   # JRE 25
  "$J" -Xmx4g -cp "C:\Users\Administrator\Desktop\jadx\jadx-1.5.5\lib\jadx-1.5.5-all.jar" jadx.cli.JadxCLI \
    --single-class <全限定类名> --single-class-output <输出.java> -d <临时目录> <apk>
  ```

  产物约定：OS5 → `work/systemui/out-src/`；OS3 → `G:\os3-ref\jadx/`。
- **类/成员清单**（比 jadx 快得多，核对 hook 目标可达性用它）：
  `work/sdk-dl/bt35/android-15/dexdump.exe -f classesN.dex`，取 `Class descriptor` 得类清单、
  取 `name :` 得该类字段+方法清单。OS3 全量 dump 已存 `G:\os3-ref\os3-dexdump-f.txt`（214MB，别重跑）。
- **hook 目标自动核对**：`python work/os3-probe.py <src/com/betteroriginos> <dexdump文件> <out.md>`
  ——从源码抽 `findClass`/`hookAllMethods`/`findAndHookMethod`/反射字段，对照 dexdump 出可达性报告。
  换适配目标时改 `FILES` 列表即可复用。
- 编译用的 `work/sdk-dl/plat35/android-35/android.jar` 是**系统内部 jar**：部分公开 API 名字与真机不同
  （例：`MediaSession` 里只有 `getSessionToken`，vivo 真机也没有 `getMediaSessionToken`）。
  遇到"找不到符号"先 `unzip -p` + 抽字符串确认 jar 里到底有什么，再决定改名或反射按名探测。
- Git Bash 里 `MSYS_NO_PATHCONV=1` 会让 adb 的**本地**路径也不转换：远端用 `/system/...`，本地写 `G:/dir/x.apk`。

## 7. 文档纪律

- **OS3 验收有脚本**：`work/os3-smoke.sh [序列号] [group_mode]` —— 环境前置（防把宿主当被测环境）、
  钩子装载、投递样本、慢滚到可视区、`verify_notify.py` + 覆盖闸门、一键清除式批量移除回归、
  媒体卡链路、收尾。退出码非 0 就是有断言失败；改完通知/媒体相关代码就在 OS3 上跑一遍。

功能落地后同步更新：`out/DEVLOG.md`（含踩坑表）、`out/DESIGN-SPEC-1025.md`（token/组件/架构变化）、
本 skill（新坑进 §4）。规范问题以 DESIGN-SPEC 为准，不在这里重复。
跨版本适配的过程证据写进 `out/LOG-<日期>-OS3-ADAPT.md` 这类**阶段性落盘日志**（每阶段：做了什么、证据路径、结论、下一步）。
