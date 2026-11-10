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

- 构建失败看 `module/build-log.txt`（GBK，用 python 解码再 grep"错误"）。
  它现在由 `build.sh` 每次 `tee` 覆盖写；**曾经**它是手工留下的旧文件，害我把一条早就消失的错误
  当新错误读，也因此差点相信了"exit=0 就是编译过了"。判构建是否成功要看 **build.sh 的退出码 + APK 是否新生成**，
  javac 失败时脚本现在会中断（`set -euo pipefail` + `| tee`），不再产出"看起来成功"的包。
- 新用到的 Xposed API 必须**先补编译桩**（`module/stubs/de/robv/android/xposed/`），
  否则 javac 报找不到符号；桩只在编译期，运行时由 LSPosed 提供实现。
- Git Bash 传 Windows 工具的路径要 `cygpath -m`；操作 `/system`、`/apex` 的 adb 命令加
  `export MSYS_NO_PATHCONV=1`。
- **跑取证/门禁前先核对"取证对象"**：`bash module/build.sh | tail` 会把编译失败的退出码洗成 0，
  本轮就这样"装着上一个 APK 跑完整套三态取证、还全绿"。判据要看 APK 是否新生成 + 设备上那一份
  的哈希：比对 `su -c "sha256sum $(pm path com.betteroriginos)"` 与本地 `sha256sum` 的前 12 位，
  不一致就拒绝开跑（`work/os3-evidence-rowlayout.sh` 第 0 步已内置这道门）。

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

### 4.0 排查方法论（观感类 bug 一律：**先逆向定因，再改写入点**，门禁不许变成工作对象）

0. **顺序就这一条，别的都是它的推论**：症状 → 派子代理逆向"这个属性被谁、按什么频率、写成什么"
   → 直接改那个写入点（或换成没人写的通道）→ 装机 → 用户上眼收口。
   **同一轮里，如果开始修"自己的判据/取证脚本"第二次，就是走偏了**：停下测量，回到代码。
   判据只在"能一眼分红绿"时有价值；需要反复喂材料、反复校准选材的判据，产物是拖延而不是证据。
   逆向不嫌麻烦：派 Explore 子代理并行跑，交付要求写死"结论 + 文件行号/字节码偏移，找不到就列排除项"。

0b. **同一症状改到第三遍还没变化，就别改第四遍，先做原生对照**。11-05 的 P0-1 就是这条：
   header 时间"往左跑再闪回右"改了十版无变化，用户一句"**原生在左上角一直不动**"才终止循环 ——
   真相是原生把时间放左槽、我们的 42dp 图标占了同一段 ⇒ `pushTimeRight` 每帧往右搬、
   vivo 每帧摆回去，**同一属性上逐帧对拉**。十版都在"把时间钉得更牢"，而
   **时间是原生每帧重排的下游、钉不住**；该动的是我们拥有的视图（图标/容器是我们 addView 的）。
   规则：反复不变 ⇒ 怀疑"我们在改不受我们支配的对象"，而不是"钉得不够狠"。
0c. **对照实验要证明"关掉是真关掉了"**。`settings put global betteroriginos_feature_notify_row 0`
   只关行样式接管，**不关**时间/布局钩子（实测仍报 `header-time=column-hook`、`col calls=…`）。
   真对照：`lspctl module disable --user=0 com.betteroriginos` → kill SystemUI →
   `logcat -d --pid=$(pidof com.android.systemui) | grep -c BetterOriginOS` 必须为 0。
   （`lspctl module disable` 不带 `--user=ID` 会报缺参数、静默不生效——别把它的失败当"已关闭"。）

1. **把症状翻译成"哪个属性被谁写成了什么"**。不说"图标抖"，要说"图标的 translationY/所在 View
   在哪一帧被重建"；不说"圆角丢了"，要说"top/bottom roundness 的分数值被谁覆盖成 0/1"。
   翻译对了，剩下的就是找写入者。
2. **枚举该属性的全部写入者**：反编译源里 `grep -n "字段名\|setXxx("` + dexdump 的 invoke 行。
   原生有写入者而我们只钩了一个入口，就一定是我们覆盖它（A13 圆角的原生写入者是
   `NotificationShelf.updateCornerRoundnessOnScroll`，它走 `setTopRoundness(分数)`，
   而我们钩的是 `getRoundnessFraction`）。
3. **高频写 = 观感 bug 的头号嫌疑**。任何带 `post/postDelayed/OnPreDraw/dispatchDraw` 的循环，
   先算频率：本项目的抖动就是"每秒补扫 → 重跑 polish → 删图标再新建 → 下一帧才对齐"。
   一次 polish 里既有 `clearXxx()` 又 `addView()`，就是"每轮闪一帧"的结构。
4. **幂等性优先于时机调参**：能"有就只调整、没有才新建"，就不要靠"少跑几次/延时久一点"。
5. **改系统视图状态前，先问谁在把它读回去做决策**（承重墙）。我们把内联"一键清除"文字按成
   `GONE`，而 A13 的 `changeShelfVisibility(z)` 判据是 `z && 内联.getVisibility()==8` ——
   恒 GONE 等于把"有可清除项"翻译成"藏起收纳栏"，于是滑动时反复闪。**"隐藏某个视图"永远要查
   可见性是不是别人的判断输入**。
6. **"概率性"= 竞态**，不是玄学。找那个"有时插一脚"的写入者（本项目是
   `updateNotificationRoundness` 只在 `ShadeViewManager.onRenderList` 被调，
   撞上过渡帧就丢圆角 → 用 dexdump 找唯一调用点，而不是猜）。
7. **门槛判据要挑真正表达意图的那个**：vivo 把组默认摊开显示，却仍报
   `mChildrenExpanded=false/isGroupExpanded=false/isExpanded=false`（真机见证逐条实测），
   所以"组是否展开"不能当子行该不该排版的判据，**能观测到的布局事实（行高 255px vs 紧凑 57px）
   比名义标志可靠**。
8. **自己先证伪**：任何"我以为 X 在生效"都要有一条日志/读数证明它真的生效过。
   本项目栽过的都是同一类：`RESTYLED` 只在死分支里写、计数器只声明不自增、
   门禁把刚写进去的键读回来当断言。
9. **计算参照物必须和验收量的是同一个对象**。图标对位连错三轮：见证里 `card=[453+297]`
   而 dump 里可见行是 255px —— `getXxx 往上找 ExpandableNotificationRow` 会撞到**同一行的
   孪生拷贝/过渡态**（vivo 有 contracted/expanded 两份），(297-255)/2=21px 正好是"修了不动"
   的那个固定残差。凡是"改了参数没变化"，先怀疑两边量的不是同一个对象，而不是怀疑没跑。
10. **断言不能是实现自己算出来的那个表达式**。`shelfgap` 曾断言 `yTo == base + want`，
    而实现就是 `next = base + gapPx` —— 这条永远绿。有效的形态是比**帧间关系**
    （施加帧 `base==yFrom` / 幂等帧 `base==yFrom-want` / 已写过的位置又被当基线加一遍=累加）。
    同理"把 settings 写回去再读回来"不算断言。
11. **"抓到 0 条"要报红，不能报绿**。`logcat -c` 放在投递样本之后，展开窗口里就一条 polish
    都没有，看起来"零重复=不抖"，其实是判据空转。每条计数型断言都要能区分
    "确实没发生"与"观察窗口没覆盖到"，必要时用另一态的读数证明窗口有效。
12. **修复没生效时先三分**：①根本没被调用 ②调用了但判成"不需要动" ③调用了也算了但算错。
    见证必须打在**入口**（把早退原因一起打出来），只打在动作分支就永远分不清 ① 和 ②
    —— 本项目那条"常态维护"实测 37 次全是 `skip=no-icon`，据此才发现整条路径不成立并删掉。
13. **确认你改的是"屏幕上那一份视图"**。A13 每行有收起/展开**两份 header 副本**，过渡里先后可见；
    对隐藏那份写位置/透明度等于没写，而"改了一半、过渡结束才闪回来"正是这个指纹。
    凡是动画类修正，要么在切换**之前**（先手钩子）对两份都做，要么挂在 `OnPreDraw` 上补最后一手。
14. **位置由代码按状态直接摆时，事后修正必然过时**。`notification_top_line` 是裸 `ViewGroup`
    （没有布局规则），时间位置是 vivo 代码摆的；此时加更多"延时重推"只是在跟它赛跑。
    正确顺序是：先用 dexdump 读摆位代码/布局 XML，找到它按什么状态摆，再决定是"抢在它前面"还是"改它的输入"。
    **11-04 补充：钩在布局期改 `layout` 框也一样过时** —— 只要祖先带 `translationX`，
    "可见位置 = 各级 `getLeft()+translationX` 累加"，按不含位移的布局坐标算偏差就永远慢一帧
    （用户描述成"时间从右往左**跑**、到左边再闪回右"——看得见移动 = 有每帧写入者，不是一次性摆错）。
    判据也必须是**视觉左位**，不能是布局框里的理想值。
15. **dump 里节点消失 ≠ 控件不存在**。被父容器 clip 掉、被 GONE、被裁成零面积都会从 uiautomator
    里消失；本轮两次把"时间被自己推到父容器外裁掉"误判成"这一行没有时间节点"。
    看到"应该有却没有"时，先查 `clipChildren`/`getGlobalVisibleRect`，再怀疑视图本身。
16. **先问"动得多慢"再选机制**：一次性摆错 ⇒ 稳态判据（dump/col measure）就够；看得见**移动过程**
    ⇒ 一定有按帧写入者（`ValueAnimator`/`ObjectAnimator`/`setActualHeight` 每帧重排），
    此时任何"事后纠正/延时补推/改 layout 框"都必然慢一帧 —— 只有两类解法：
    **加入那一帧**（同一趟里改），或**换一条没人写的通道**（见 17）。
17. **改视图状态前，先证明这条通道没人争抢**。可复用的查法（11-04 就是靠这三步定位到通道的）：
    ① 在 OS3/OS5 源码里 `grep "addTransformedView\|updateTransformedTypes"` 看谁被注册进
    `TransformState`（本项目实测：只有 `mIcon`(ALPHA)/`mExpandButton`/低优先级时的 `mHeaderText`，
    **OS3 的 header wrapper 连 `mTime` 字段都没有** ⇒ `time` 自己的 `translationX` 无人写，可以拿来用）；
    ② `grep "LayoutTransition\|animateLayoutChanges"` 扫三个 APK 的全部 `res/layout/*.xml`
    （实测 **0 命中**，所以"流式布局被 LayoutTransition 动画拖走"这条猜测是错的）；
    ③ 读 framework 的摆位函数本体（`work/tmp-re-os5toos3/A13-NotificationTopLineView.java:120-198`：
    `onMeasure` 算 `mViewsToDisappear`，`onLayout` 流式排、`GONE` 跳过、`start` 累加）。

### 4.1 已付出代价的坑

- **别按方法名假设调用者**：`ExpandableNotificationRow.setChildrenExpanded(false)` 系统对**每个没有子项的行**
  也会调一次。钩子里不加 `isSummaryWithChildren && childCountOf(row) > 0` 就是误伤——子行自己的大图标被淡出、
  摘除，补扫又 polish 回来，形成打环（观感"闪一下再没了"）。
- **Java 文件手术只用精确字符串 Edit**：按行号/索引批量改写会把锚点算错（本轮因此把 `NotifyRowFeature.java`
  的 2800 行替换掉，靠 `C:\Users\Administrator\.qoder-cn\file-history` 的快照才恢复）。
  改前 `cp` 备份，改后做括号平衡检查并 `diff` 确认只有预期 hunk。
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
- **更正（2026-11-02，此前这条坑写反了）**：OS3 **解锁态**的下拉面板本来就显示 MediaStyle 通知行，
  `NotificationContentView.setContracted/ExpandedChild` 在 OS3 同样命中
  （运行时证据 `work/z8os3-live/20261028-1408-stack.log`：`media bind ... pkg=com.android.bbkmusic`
  与 `restyling style=os5 MediaNotificationView`；完整取证 `out/LOG-1102-OS3-MEDIA-SHADE-RE.md`）。
  真正会挡它的是 vivo 自研 finalize filter
  `com.vivo.systemui.statusbar.notification.KeyguardNotificationManager$1.shouldFilterOut()`，
  **只在锁屏态 `StateUtils.mState==1` 生效**，且 i音乐 id 10001/10002 被写死进黑名单。
  ⇒ "OS3 无落点"是错的；只有**锁屏态面板**才需要额外钩点（H1 该 filter setResult(false)）。
- **反向陷阱**：AOSP A13 的"媒体通知收进 QS 播放器、列表不显示"机制 `Utils.useQsMediaPlayer()`
  在 OS3/OS5 两个 build 都被 vivo 焊死成常量 `false`（`classes5.dump:354724` 反汇编直证），
  `MediaCoordinator.mMediaFilter` 因此恒不生效 —— **别去"打开它"**，那条路只会把媒体行藏掉。
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

- **给"要被人点的自绘控件"设计门禁时，三件事必须分开证明**（一键清除这条链就是这么补的）：
  ① **坐标**从日志来（`clear-all at x/y/w/h`，`onSizeChanged` 与显隐变化两处打，取最后一条），
  别在 shell 里估；② **送达**由点击自己的见证行证明（`clear-all tap inlineListener=1`），
  没点到就没有这行 ⇒ 脚本判红，而不是"看起来没反应"；
  ③ **副作用真的发生**用系统侧计数证明：`dumpsys notification | grep -c "NotificationRecord("`，
  再按 flags 排除 `ONGOING_EVENT|NO_CLEAR|NO_DISMISS` 得到"可清除条数"，点击前后对比。
  `set -u` 的脚本里解析这类坐标要先 `${X:-0}` 兜底再判，否则算术报错会把脚本中断在收尾步骤之前
  （设备就被留在 stayon + 面板展开的脏状态）。

- **`setResult(null)` 只有在被 hook 方法返回 void 时才安全**：OS5 一键清除重启的确切机理是
  `getGroupKey`（返回 String、被批量移除的 key 查表消费）被置 null。改任何 `setResult(null)` 之前，
  去 dexdump 查签名的 **V/非 V**（本项目现存 4 处全是 void：`sanitizeTopLine`、
  `updateIconsForLayout`、`updateVivoIconsForLayout`、文件管理器 `f2.x()V`）。

- **`MediaController` 的 getter 不会把 DeadObjectException 抛出来**：`getMetadata()`/`getPlaybackState()`
  内部吞掉 RemoteException 返回 null，所以"会话被清掉时主线程读状态会崩"是**假说，不是实测**。
  别把它当根因（本项目的真因是 getGroupKey + 未保护的 pre-draw/deferred）；主线程回调该兜还是兜，
  但兜底日志计数为 0 时结论应写"没撞上"，而不是"修好了"。

- **"跨版本常量"别用宿主主题属性去顶**：A11 音乐卡的封面圆角在 A11 侧确实是
  `Theme.DeviceDefault.dialogCornerRadius → config_dialogCornerRadius = 2dp`，但把实现写成
  "运行时读宿主的 `android.R.attr.dialogCornerRadius`"，在 OS5/OS3 上就变成 20+dp——等于顺手美化，
  1:1 当场失效。**凡是"目标版本的观感"，把它的值按目标版本钉成常量**，宿主值只打进日志做对照
  （`radius=.. hostCorner=..`），并给验证器配"跟了宿主主题"的反例。

- **反例脚本的字面 `replace` 会静默失效 = 假绿**：给取证脚本加负对照时，若替换串里的缩进/空白
  与文件实际不一致，`str.replace` 不报错也不改任何东西，于是"反例没被抓住"看起来像"断言 weak"、
  更坏的是下一轮它悄悄变绿。规则：篡改一律用**结构化正则**（如 `dimen/<name>` 定位后改值），
  并在 selftest 里断言"每条反例恰好只改了一份材料"，改不动就直接判失败。

- **"两版都有这个方法"是假设，不是事实；fail-open 的判断必须自报来源**：装机前用
  `work/os3-probe.py` 把源码里的钩子方法名/字段名逐条对 OS3 dexdump，抓到两处只会在真机上"表现为怪现象"的错：
  A13 的 `NotificationStackScrollLayout` **没有** `onExpandedChanged`（只有 `setIsExpanded/getIsExpanded/isExpanded`），
  也**没有** `getExpansionFraction`（同名的在 `PanelViewController`/`AmbientState` 上）；
  而展开判断读不到时是 fail-open（当成已展开）⇒ 浮动一键清除会在收起态/锁屏上照样露出来。
  改法：先钩 A15 的名字、命中数为 0 再退到 A13 的名字，**实际钩到哪个/走了哪条退路都打进日志**
  （`install groups=…nssl.xxx=1`、`clear-all attached … shade=fraction|isExpanded|unavailable`），
  验证器断言来源必须是真值路径。靠"看起来两版都有"写的钩子，装机当天只能靠肉眼找现象。
- **需要系统状态时优先用公开 API，别反射 OEM 内部类**：锁屏判定用
  `KeyguardManager.isKeyguardLocked()`，不要反射 vivo 的 `KeyguardStateController` ——
  OEM 类名/方法在两版差异极大，反射一失败就静默等于闸门不存在，而公开 API 失败会老实抛异常。

- **验证器的正则也会腐烂，且它腐烂时是"绿的"**：代码把日志文案改了、正则没跟上，正例靠旧 fixture
  自洽地通过，真机日志一条都解析不到（本项目真踩过：`a11 palette base=` vs 实际的
  `a11 palette expanded base=`）。防法：`work/logshape_lint.py` 把每条正则的字面锚点回源码里查存在性，
  已挂进离线总入口。改日志文案时顺手跑它。

- **装机前先跑一条命令**：`bash work/offline-all.sh`（项数以脚本为准，别在这里抄数字：G2 逐指令与负对照、A11 数学/交叉/取色网格、
  验证器正反例自测、Y7s 实读取证核对、日志形状自检）。它红了就别上设备 —— 说明前提或被测实现已经分叉，
  真机跑出来的"像不像"没有解释价值。

- **抄常数要连"有几套、符号、门限"一起抄，反对照必须能抓住符号错**：vivo 的 G2 连续角有两套常数
  （`min(w,h)>2.6r` 用 `-0.013r/0.61r/1.3r`，`2.2~2.6r` 带用 `-0.008r/0.4r/1.1r`），
  退普通圆角的门限是 **2.2r**，第一个控制点偏移是**负值**。我只抄了一套、把门限记成 2.6r、还把偏移抄成正数，
  三条都过了当时的自检 —— 因为反对照只挪过量级（1.3r×1.05），**挪符号抓不到符号错**。
  收起行高正好落在 2.2~2.6r 带里 ⇒ 连续角静默失效，而这在本项目是"眼睛不许判"的那类问题。
  规则：数值移植自检的参考实现要**逐行照反编译源重抄**（含分支与取整方式），
  反对照要分别扰动"量级"和"符号/分支"两类，否则等于没测。

- **AOSP 的"历史包袱"要照抄，不要顺手修正**：`selectForegroundColor` 里
  `candidate.pop / dominant.pop < 0.01` 是**整数除法**（等价于"候选更小就换 dominant"），
  `Palette.generateScore` 用 0..1 标度的 HSL 而本模块 `lightness()` 是 0..100。
  把前者"改成浮点"或混用标度都不会报错，只会让选出的族静默换人。
  验收：`work/a11_selection_selftest.py`（反射跑模块字节码 + 独立参考，6 组用例 + `--negative`）。

- **推翻别人的结论，必须读到"那个值的那个调用点"**：审查代理说 A11 无封面兜底是 `0xFF404040`，
  我在 `MediaDataManager.java:339` 找到 `-12303292 = 0xFF444444` 就"驳回"了它——
  但 `:339` 是 resumption 路径，展开卡走的 `computeBackgroundColor()`（`:534-553`）
  是**把 `rgb=-1`（白）继续送进同一条 HSL 变换**，算出来正是 `0xFF404040`。代理对、我错。
  同一个公式在别处出现、别处恰好有个能对上号的常量，不构成证据。

- **系统会把我们写过的值改回去的地方，要钩在"系统改完之后"重补**：
  `NotificationShelf.onConfigurationChanged` 调 `initDimens()` 复位 `mPaddingBetweenElements`，
  只在 `bind/onFinishInflate` 补 7dp 会在转屏/改字号后静默掉回 0。
  这类"补写点"也要 `record()` 记命中数，为 0 就让门禁直接判失败，别等肉眼发现。

- **自检的比对基准要分"确定性"与"并列不确定"**：逐块/逐指令比对只在结果唯一时才有效。
  Java `PriorityQueue` 在体积并列时出堆顺序不保证（median-cut 的分组会不同但同样合法），
  硬比会造出一条永远修不好的假红。做法：确定性用例逐值比，并列用例改验不变量
  （块数上限、群体守恒、每块群体>0），并把"为什么这里不比细节"写进脚本注释。
  同理适用于负对照：只挪量级抓不到**符号/分支**错，两种扰动都要做。

- **改完状态要问一句"谁会把它读回去当输入"**：我们为了 OS5 观感把 `NotificationShelf.mDismissAllNotification`
  按成 `GONE`，而 A13 的 `onConfigurationChanged` 末尾会
  `setDismissVisible(那个视图.getVisibility()==0)` 把它**读回布尔**⇒ 转屏/改字号后系统传来自己造的 false，
  浮动一键清除从此永不出现。做法：用 `sInConfigChange` 标记覆盖那次调用、期间不采信参数，事后用自己记住的
  真实状态重算。同类坑：把 `getGroupKey()` 抹成 null 会让批量移除 NPE。**别只测"改完立刻对不对"，
  要测"过了一次配置变更/重绑之后还对不对"**。

- **移植观感前先把该状态的"写入者"枚举干净**：A13 的 `mOutlineRadius` 只有三处写入
  （`initDimens`、`onSystemFilletChanged`、`onDensityOrFontScaleChanged` 内部再调 `initDimens`），
  所以钩两处方法就够（第三处是虚调用，同样被方法钩子命中）。
  反编译文件里 `grep -n "字段名"` 列全部赋值点，比"跑了没坏"更可靠 —— 漏一个写入者就是偶发回退。

- **`dexdump -f` 里没有调用点，因为它根本不反汇编 code 段**：`G:/os3-ref/os3-dexdump-f.txt` 只能证明
  "某方法声明存在"，不能证明"没人调用"。判调用点要 `-d -n`（A13 单个 dex 5 秒 / 134MB，产物
  `work/tmp-re-os5toos3/os3-code-classes2.txt`），再 grep `L包/类;.方法名:` 那种 invoke 行。
  本轮因此才发现 `setClearNotificationListener` 的唯一调用者是 `NSSL.setShelfController`。

- **jadx 会静默跳过巨型方法**（`Method dump skipped, instruction units count: 701` +
  `throw new UnsupportedOperationException("Method not decompiled…")`）。
  **在跳过的方法里搜不到东西 ≠ 不存在**：我据此以为 A13 没有 `updateShelfWhenDismissAll`，
  多写了一遍原生已经有的 250ms 整栏消失动画；真机上一次"还剩 ongoing 通知"的点清除
  就会把收纳栏凭空抹掉。读不到方法体就去看字节码，别下"没有"的结论。

- **"系统已经有了就别补挂"用类存在性判断，不用版本号**：
  `findClass("com.vivo.systemui.statusbar.ui.NotificationClearAllImageView", shelf.getClass().getClassLoader())`
  命中就整段跳过（A15 三个 dex 命中 6、A13 全 0）。比 `isA13OrLower()` 更耐 ROM 升级，
  也不会把"只在 A13 验证过的行为"偷偷带到别的版本上。探测结果要缓存 + 打日志（`clear-all native=`），
  并且 `work/os3-probe.py` 里登记进 `INTENTIONAL_ABSENT`，免得报告把它当 hook 目标缺失去"修"。

- **某个视图被我们改了状态、而系统会把它读回去当输入**，除了上一轮的自噬回路，还有"承重墙"型：
  A13 `changeShelfVisibility(z)` 的条件含 `内联按钮.getVisibility()==8`，
  所以那枚内联文字按钮**可见时会让收纳栏拒绝收起**。移植前必须问：
  这个视图的可见性/字段还有谁在读？读回去的是"系统意图"还是"我们造成的表象"？

- **门禁脚本里"写了 settings 键 + 把同一个键读回来比较"是自我满足的假断言**。
  它只能证明 settings 可写，不能证明模块真的读这枚键。`work/settings-keys-check.py`
  会把 `work/*.sh` 里每个 `betteroriginos_*` 键对回模块源码
  （完整字面量 / `Prefs.option(fid,opt)` / `Feature#id()` 推得的 `feature_<id>`），
  没人读的键直接判红——今晚就是它抓出 `notify_row_style`：`isLightStyle()` 早已恒真，
  两条门禁还在写它、其中一条还在断言它。**同类**：断言要指向"模块的行为"，
  不要指向"我刚写进去的值"。

- **字号单位先去资源表查是 dp 还是 sp，别默认"文字就该用 sp"**：A11 framework-res 实测
  `notification_title_text_size = 14.0`**sp**、`notification_subtext_size = 12.0`**dp**
  （副文**不跟 fontScale**）。套成 `COMPLEX_UNIT_SP` 在大字号机器上会比系统大一档，
  而这种差别**只有 fontScale≠1 时才看得见** ⇒ 验收器要专门备一条 `fontScale=1.3` 的正例
  （`GOOD_FS`）加对应反例，否则那条断言是空的。

- **日志里的小数一律 `String.format(Locale.US, …)`**：默认 Locale 在逗号小数语言写出 `contrast=9,40`，
  验证器 `([\d.]+)` 匹配不到就**整行丢掉**，那行本来要验的十几条断言静默不跑，
  而"有没有日志"的兜底又被别的行满足 ⇒ **假绿**。防复发的通用做法是让验证器带
  **"某标记的行数 ≠ 解析出的条数就判红"**的漂移守卫（`verify_os5_mirror.py` 里 6 个标记都在查）。

- **改过视图样式，另一条分支必须还原，并且还原要有见证**：收起态给标题设过
  `sans-serif-medium`，展开态只改字号不清字体 ⇒ 一次"收起再展开"就永久 medium。
  现在展开态显式 `setTypeface(null, NORMAL)` 并打 `titleMedium=0|1`
  （按 Typeface 实例身份比——内部版 `android.jar` **没有 `Typeface.getFamily()`**）。

- **复刻 OEM 的"有没有导航条"分支时，先看调用方把什么信号传进去，别挑一个看起来合理的配置**：
  OS5 一键清除 40/62/90dp 的分支实际是 `CentralSurfaces.isNavigationBarVisible()`
  （A15 dexdump：`NSSL.setClearAllButtonLocation` 传的是非 null 的 `mCentralSurfaces`），
  语义是"此刻可见"，**手势导航下 hint bar 也算可见**；而 `config_navBarInteractionMode`
  实测在 A15(Z8) 与 A11(Y7s) 的 framework-res 里都是 `0`，vivo 根本不走它 —— 拿它当判据会猜错。
  模块用的等价信号是 **`WindowInsets.isVisible(Type.navigationBars())`**
  （注意不是 `getInsets(...).bottom > 0`：那个量的是"留没留出高度"，
  手势 hint bar 常常**可见但 inset=0**，用它当判据就选错档）。
  `getInsets(int)` 返回 `android.graphics.Insets`（不是 `Rect`；字段 `bottom/left/right`），
  留作见证值一起打日志（`navSrc=insets|config` + `navVis=` + `navH=`），门禁能分辨两种情形。
  取 dimen 的实际数字用 `aapt dump --values resources <apk>`（实测：`margin_bottom=40dp /
  with_navigationbar=62dp / with_flowview=90dp / size=46dp / padding=2dp`，
  A13 里这些名字命中 **0** ⇒ 只能随模块打包）；framework 侧同理，直接拉
  `/system/framework/framework-res.apk` 离线读，不需要解锁。

- **自绘浮层（钮/条）的显隐不要只挂在一个系统回调上**：只钩 `setDismissVisible` 时，
  一键清除把收纳栏清空后若系统不再回调，浮钮会留在空栏上。OS5 自己有
  `NSSL.setClearAllButtonVisibility()` 重算（A15 dexdump 有、A13 没这方法），
  所以 A13 要再找一个系统一定走得到的触发点——这里加钩 `NotificationShelf.changeShelfVisibility(Z)V`
  （PRIVATE 也能被 `hookAllMethods` 命中，`os3-probe.py` 能复核 ✅）。
  **前提是那个状态由系统写**：我们已经不再自己写 shelf 可见性，才不会变成 §8 的自噬回路。

- **"系统已有、我们什么都不做"的分支也要留正面证据**：能力探测原先挂在 shelf 事件里，
  于是 OS5 上合法地一条日志都不打，反被验收器判红。改到 `hookShelf` 里
  `findClassIfExists` 探测一次并缓存（`clear-all native=true` 一定会出现）。
  新用 `XposedHelpers.findClassIfExists` 要先补 `module/stubs/` 里的桩；
  内部版 `android.jar` 还**没有 `View.isVisible()`**，用 `getVisibility()==VISIBLE` 代替。

- **往别人的 ViewGroup 里 addSubview 之前，先查那个容器是什么 Layout 家族**：
  A13 的 `NotificationsQuickSettingsContainer` **extends ConstraintLayout**，
  给它 `FrameLayout.LayoutParams(gravity=BOTTOM|CENTER_HORIZONTAL)` 会被 `generateLayoutParams`
  换型、**gravity 静默丢失**，未约束子视图落左上角 —— 不抛异常，只会"按钮跑错地方"。
  判据来自布局与类声明，不用上设备：
  `aapt2 dump xmltree --file res/layout/status_bar_expanded.xml <apk>`（明文，1 秒）看 OS5 把
  `NotificationClearAllImageView` 放在 `NotificationPanelView` 直接子级 + `layout_gravity=0x51`；
  jadx 单类确认 `NotificationPanelView extends PanelView extends FrameLayout`。
  ⇒ 模块改成"往上找第一个 `FrameLayout`"，并让日志带 `host=<类名>`，验证器要求含 `Panel`。

- **别批量替换文档里的数字**：DEVLOG/RE 日志是按时刻记的账，
  一条 `sed 式`的"19 条断言→25 条断言"会把**历史真值**也改掉，
  等于伪造当时的状态。正确做法是保留原值 + 在新条目里写当前值（或加"当时 X，现在 Y"）。

- **日志时间戳一律 `date` 取值**：DEVLOG 里出现过 `2026-11-03 03:57` 而墙钟才 `11-02 20:25`
  ——把"熬夜排班的时间"当墙钟写，以后复核时序会全盘错乱。

- **在 `initDimens` 这类"构造期"钩子里，不要调用需要已 inflate 的子 View 的方法**：
  A13 的 `ExpandableOutlineView.<init>:103` 就调 `initDimens()`，此时
  `ActivatableNotificationView.mBackgroundNormal` 还没 `findViewById`（要到 `onFinishInflate` :472-474），
  我们的 after-hook 去调 `applyRoundness()` ⇒ `mBackgroundNormal.setRadius` NPE，**通知圆角整条静默失效**
  （真机 22 条 `radius write failed`，离线一百项都验不出来）。
  正确形态：构造期只写字段，另挂 `onFinishInflate` 补一次（`hookAllMethods` 只匹配该类自己声明的方法，
  所以要钩 `ActivatableNotificationView` 而不是基类）。

- **多项判定不要用 `&&` 短路**：`clearAllShouldShow` 里把 `sDismissAllowed` 放最前、
  `isQsFullScreen` 放后面，收起态下后者永远不被调用 ⇒ 见证日志永远 `qs=untried`，
  "闸门生效了"与"闸门从没跑过"在日志里长得一样。各自求值再合并。

- **打进日志的统计量必须有真实写入点**：`sClearAttaches` 声明了、`landing … clear=` 也打了，
  但全文件没有一处 `++` ⇒ 这条见证等于没有。已上离线检查 `work/witness-counters-check.py`
  （含正负自测，在 `offline-all` 里）。同理，"没有日志"这种断言在三种成因（反射字段名漂 /
  原生本来为 0 / 矩形算不出来）下不可区分，必须补 `skipped reason=…` 见证再判。

- **A13 的分组摘要不走 `NotificationContentView.setContractedChild/setExpandedChild`**：
  `restyle` 对摘要行永远不会被调用 ⇒ 摘要没大图标、header 没对齐，而且**没有任何 skip 日志**
  （没进函数当然不会打）。要接管摘要就挂在每轮必调的
  `NotificationRoundnessManager.getRoundnessFraction` 上（A13 有这个类，`Roundable/SourceType` 才没有）。
  同一颗钩子也用来常态维护 header 文本列：vivo 布局会把 header padding 顶回去，一次性改会失效。
- **一行里同时有收起/展开两份 header**：跨子树取 `app_name_text` 会量到隐藏那份，
  出现"`x=99` 配 `hdrPad=159`"这种自相矛盾的读数，padding 被加到错的物体上。
  必须**先选定可见 header，再只在该子树里找文本**；padding 收敛还要区分
  "布局没跟上（别重复加，会过冲）"与"被系统顶回去（>250ms 仍偏位就要再补）"。
- **摘要 header 的高度不由自己决定**：写 `LayoutParams.height` 会被 `NotificationChildrenContainer`
  覆盖（真机两次复现 `hdrH=132`），别再尝试；观感差异要靠图标/文本列解决。
- **验收快照别只挂在设置变化上**：`dumpRoundness` 原来只在切 `sep` 时打，
  稳态面板里验收脚本取不到读数就报"日志缺失"（假红）。加 `phase=live`（5s 一次）后才有证据。

- **诊断日志会把别的见证挤掉**：收敛类诊断每轮都打，A 相刷屏后启动期的
  `g2 path / shelf gap / clear-all attached` 被冲出 logcat 窗口，门禁报"没有日志"（假红，
  且真机上看不出来）。规则：**新增每帧/每轮都可能打的日志，必须按对象去重**
  （本项目用 `sColLogOnce` + 身份哈希），周期性快照放宽到 10s 以上。
- **vivo 组子行的文本位置不由 header padding 驱动**：祖先链
  `header(RelativeLayout) → notification_top_line` 里，真正带左边距的是 `top_line`；
  只改 header padding 时文本不动（真机两次复现）。子行的圆角也不走
  `getRoundnessFraction`（父行 `applyChildrenRoundness` 直接下发），
  所以挂在圆角钩子上的常态维护对子行永远不触发，必须自己递归到子行。

### 4.1 本轮（11-03 夜）新增的坑

- **文本/时间错位先问"是谁按什么规则摆的"**：通知 header 的 top line 是 framework
  `android.view.NotificationTopLineView.onLayout`——**按子节点顺序从左往右流排、GONE 跳过**
  （标签没有点号，LayoutInflater 会补 `android.view.`；不在 com.android.internal.widget，
  A13/A15 都有）。vivo 组子行把 `app_name_text` 收掉 ⇒ `time` 成第一个可见子节点 ⇒ 落到行首
  +126 压在 42dp 图标上。对这种**布局规则**，translationX 与延时补推都必然输；
  正确落点是钩 `onLayout`（after）在同一趟里把文本子节点整体平移到目标列。
- **"结构性早退"要分流成不同原因，不要共用一个**：vivo 分组卡的摘要 header 直接挂在
  `NotificationChildrenContainer` 下（没有 NotificationContentView）——它本来就走原生列，
  给它 `summary-header` 这个正当原因；`no-cv` 留给"真找不到归属"。共用一个原因码会让
  真故障要么永久染红、要么被白名单静默放行。
- **见证要有"实测"那一层**：`col measure … time=+198` 量的是布局后真实落点，验收器拿它和
  66dp 常量比；只打"我推了 d=72"就永远是自我实现式断言。早退原因必须允许被后续实测补齐
  （transient），补齐不了才红——这一条区分了"瞬时没挂上"与"这行永远没人管"。
- **DSU/OS3 上 uiautomator 拿不到 idle 的两条真因**（都不是"动画瞬时"）：
  ① 正在播放的媒体卡每帧自愈重绘 ⇒ 取帧前 `cmd media_session dispatch pause`（实测立刻能 dump）；
  ② 前台盖着别人的 App（本例 LSPosed 管理器）⇒ 先 force-stop 非系统前台。
  判"是不是我们在打转"用 `dumpsys gfxinfo com.android.systemui reset` + 6 秒后读
  `Total frames rendered`（实测 0 帧）。工具侧还要留 `--log-only`：dump 不可用时只判日志侧，
  几何判据写明"本轮未测"，别让整轮作废、也别把未测当已通过。
- **焦点不在面板时不要 tap**：脚本按旧 dump 的坐标点，等于在别人的 App 上盲点。
  tap 前必须确认 `mCurrentFocus` 是 NotificationShade；出过事就去核对被盖着的那个 App 的状态
  （本轮查了 LSPosed：模块 enabled、5 个 scope 原样）。
- **A11 有两个不同的"媒体界面"**：QS 播放器（`media_view.xml`、`qs_media_album_size`、
  `@style/MediaPlayer.Button`、`IlluminationDrawable`）与通知栏 MediaStyle 行
  （`notification_template_material_media.xml` + framework `MediaNotificationView`）。
  模块改的是**通知行**，抄 QS 常量就永远"看着不像"；而"封面铺满背景+渐变"其实是
  **锁屏/媒体态的 backdrop**（`NotificationMediaManager.finishUpdateMediaMetaData` →
  `mBackdrop` 双缓冲 250ms 交叉淡入 + `ScrimController.setHasBackdrop`），不属于那张卡。
- **A11 的可读性是硬约束不是审美**：正文主文 `#ff000000`/`#ffffffff`（**完全不透明**）、
  副文 `#ff4d4d4d`/`#b3ffffff`（vivo-res 0x0308016x 实读）；`config_tintNotificationActionButtons`=true
  ⇒ 动作按钮与正文同色；着色时 `findAlphaToMeetContrast` 只在 [当前alpha,255] 二分 ⇒
  **只会把字加实，永远不会调淡**。
- **jadx 的三个陷阱**：① 内部类要按外层类名取（`Notification` 落在
  `android.app.Notification` 全文里）；② 方法体过大被静默跳过（`Method dump skipped`）必须转
  dexdump 字节码；③ jadx 把 Kotlin 常量内联成符号名（显示 `WorkQueueKt.MASK`，真值是
  `const/16 v8, #int 127`）——**别信符号名，读立即数**。
- **Java 手术即使写在 python 里，也必须用唯一字面锚点**：本轮用 `s.index(...)` + 切片替换
  `a11ArtRadiusPx`，多留下一个右花括号，javac 报 13 条"需要 class/interface"。
  改完立刻编译；括号平衡 0 不能代替编译。
- **改了日志文案就跑 `work/offline-all.sh`**：`logshape_lint` 本轮三次抓到"验证器还在匹配旧形状"
  （`a11 palette expanded`、`a11 card spec art=`、`a11 card spec … btnPad=` 因两段字面量拼接不连续）。
  见证文案要写成**一条连续字面量**。

### 4.2 本轮（11-03 第二轮，A11 媒体卡逆向）新增的坑

- **整数值属性必须回 framework-res 的 attr 枚举表核对**：`scaleType=6` 我按
  `ImageView.ScaleType.ordinal()` 读成 CENTER_INSIDE，实际枚举表里 6=**centerCrop**（7 才是 centerInside）。
  读法：`grep -A 20 "resource 0x0101011d attr/scaleType" fw_res.txt`。同类坑：`layoutDirection=0=ltr`、
  `visibility=2=INVISIBLE`。
- **aapt2 dump xmltree 把负 dimension 印成巨大正数**：TYPE_DIMENSION 的 `data=(mantissa<<8)|(radix<<4)|unit`，
  aapt2 用**无符号** `data>>8`，`16777204.000000dp` 其实是 **-12dp**、`16777192` 是 -24dp。
  负 margin 恰恰是 A11 卡片纵向排布的核心（24dp padding 配 -12/-24dp），别把它当垃圾值跳过。
- **"布局里写着 X"不等于"屏幕上跑的是 X"**：vigour 模板的 ViewStub 写了
  `android:layout=@layout/vigour_notification_material_media_seekbar`，但设备 SystemUI 的
  `resolveViews()` 在 inflate 前无条件 `setLayoutResource(R.layout.notification_material_media_seekbar)`
  覆盖了它 ⇒ 生效的是 AOSP 那份。判"谁写这个值"要一路看到**最后一次 set**。
- **同一份 A11 代码有两个分支，数值是两套**：`IS_TIER_VOS = FtBuild.getTierLevel()!=0 &&
  "vos".equalsIgnoreCase(FtBuild.getOsName())`；Y7s `ro.vivo.os.name=Funtouch` 且
  `ro.system.vivo.tierlevel` 不存在 ⇒ **false** ⇒ MediaStyle 用 `vigour_*_q` 模板（按钮 36×40、
  minHeight 61dp、副文 12sp…），AOSP 那一支才是 48dp/39dp/14dp。
  移植前**先用 prop + 反编译把分支定下来**，常数表按分支命名（`DEVICE_*` / `AOSP_*`），
  并给反例断言"串成另一支就该红"。
- **见证要"回读施加结果"，不要重算同一个表达式**：`titleMedium=` 拿 `Typeface` 实例身份比，
  而我给标题换成了 `Typeface.create(family, BOLD)`（另一个实例）⇒ 真机 6 条假红，
  连带把"两态无可测量见证"也带出来。规则：**换实现手段时，比较基准要跟着换成同一个缓存实例**；
  进度条那组颜色则回读 `seekDrawable.fillPaint.getColor()` / `bar.isEnabled()`，比的是真实视图状态。
- **成对状态要一起纠正**：A11 的"不可拖"= thumb alpha 0 **且** setEnabled(false)。
  分开写（palette 换 thumb、seekable 回调改 enabled）会让见证抓到中间态自相矛盾。
- **门禁的 logcat 捕获必须分段追加**：`logcat -d > "$LOG"` 覆盖式捕获，被我们自己每次建卡的
  十几行 ancestor/rowChild 取证把早先的 `a11 card spec` 挤出环形缓冲 ⇒ "spec 日志缺失"的**假失败**。
  改成 `>>` 追加。**取证脚本读不到日志要当失效修，不能当"这轮没发生"。**

- **调试入口连投多次要每次先 `am force-stop`**：`am start -n com.betteroriginos/.ui.MainActivity
  --es debug_notify postbulk` 连着发第二次时，同一实例走 onNewIntent 路径，实测**样本没进去**
  ⇒ 面板里只剩最早那一组。取证脚本因此长期在"样本饥饿"的屏上跑：L8/L9 一起报红，
  看着像产品退化，其实是列表里根本没有带时间的通知。规则：**投递类前置动作，每次之前 force-stop，
  并在第一帧 dump 里数一下预期样本是否真在**（不在就先把样本问题报出来，不要去怀疑判据）。
- **"0 读数"的红要分诊，别只写"空转"**：`L9` 原来在"图标行恰好都是 showWhen=false"时报
  "样本或判据空转"，下一句还是没人知道该修哪边。现在分两支：整屏没有 time 节点 ⇒ 直说"换 post/postbulk 样本"；
  有 time 但都不在被接管的行 ⇒ 说清数量。**同一道理**：`展开有没有发生` 别拿"全局最高行"当证据
  （媒体卡/本来就开着的组摘要都可能就是最高行），要数**展开态子行**的条数。

### 4.3 本轮（11-04 下午，P0-1 逆向定因）新增的坑

- **"接管方式"这类自报字符串不能写死**。`HEADER_TIME_MODE` 原来是一条常量 `header-time=push-only`，
  钩子实际装上了也照报 push-only —— 门禁据此决定"col 那套断言该不该跑"，于是整轮结论是被自己的字符串骗的。
  改成按**实际装配结果**自报（`sColumnHook` 由 `hookAllMethods(...).size()>0` 置位）。
- **一次性见证会被 logd 每进程 250/s 整批冲掉，所以入口计数器要进低频心跳**。col 钩子装好后
  `col measure` 在窗口里一条都没有，差点判成"钩子没跑"；加了 `notify_row.col calls=N`（随补扫每 5s 重发）
  才分清"没跑"与"跑了但日志被冲"。同理把最后一次 `col measure` 整串留一份重发。
  **写入口计数器要在所有 guard 之前 ++**，否则"进了函数但判成不用动"仍然是瞎的。
- **判据的指纹不能依赖会被取证动作抹掉的东西**。L3/L4 排除媒体行原来靠"行内有 SeekBar"，
  而取证为了拿到 idle dump 会先 `cmd media_session dispatch pause` ⇒ 暂停态媒体行里没有 SeekBar ⇒
  被当独立行判红。也不能换成 `status_bar_latest_event_content`：真机 dump 里**每一行**都带它
  （5/5 行实测），拿它当指纹会把所有行排除、L3 退化成"没有可测行"的空转。
  最终用 transport 按钮 `action0..4`（≥3 枚）作指纹，并配"把按钮 id 改名就该红"的反例。
- **多行日志的续行不带 tag 前缀**：`Log.i(TAG, "notify_row.trace" + 多行)` 在 logcat 里
  只有第一行含 `notify_row.trace`，续行是 `... I BetterOriginOS:   f=1 ...`。
  取证按 `grep notify_row.trace` 会只剩表头，**要按进程 tag（`BetterOriginOS`）整批抓再拆条目**。
- **同一 App 的多条调试样本在 OS3 会被自动归组**，而 AOSP 组子行的 header `time` 宽度是 0（无文本）：
  投了 `postgroup` 之后再投 `post`，那条带时间的独立行就变成没有时间的子行 ⇒
  逐帧判据拿不到材料。**要一条"已接管且 header 带时间"的材料，就只投 `post` 一条**。
  另外 vivo 那种"应用名被收掉、时间成第一个可见子节点"的组子行只有真机自带通知里才有，
  造不出来就别硬判：验收改由用户上眼（见 §4.0 第 0 条）。
- **AOSP 自己就会收掉组子行的应用名**（不是 vivo 定制）：
  `NotificationGroupingUtil.updateChildrenAppearance` → `AppNameApplicator`/`VisibilityApplicator`
  用 `setVisibility(GONE)`，发生在**绑定期**、不在展开动画路径上；A13/A15 规则一致。
  所以"子行时间落到行首"是 `NotificationTopLineView` 流式布局的必然结果，
  不是谁写歪了 —— 别去钩 grouping，会牵动摘要行的文案。

### 4.4 本轮（11-04 深夜，P0-1 第二轮）新增的坑 —— 连着三次判错的复盘

- **"每帧入口没跑"这种结论，必须先证明观察条件活着**：本轮我据 `col calls` 冻结断言
  `onLayout` 不重跑，后来发现是**自己把模块作用域点掉了**（盲 tap 落在 LSPosed 列表行）导致新进程没被注入。
  复测同一动作 `calls` 从 23 跳到 99 ⇒ 结论作废。开口的顺序应该是：
  `lspctl scope list` 有目标包 → 进程里有 `header-time=` 自报 → `top-line column hook hits=1` → 才谈读数。
- **口径要等于用户原话，不是等于我以为的病**。用户说"从右往左**跑**、到左边又闪回右"= 要**别动**；
  我实现的是"视觉左位不早于 66dp 列"= 只防**压到图标**。实测线在 126/200/410/565/568 之间移动，
  从没跨过 198 ⇒ 我的钳位一路判"不需要动"，症状原地不动。写修正式之前先把用户那句话翻译成
  一条**会被违反的判据**，并检查它是否真的覆盖那个动作。
- **跨 OEM 变体找视图：只扫直接子节点会静默漏掉**。`findTimeChild` 原来只看 `top_line` 的直接子节点，
  而 vivo 行里的时间**不是直接子节点** ⇒ 两版钳位都在空转（见证就是 `time=none` 与
  `col predraw wrote=0`）。改成整棵子树按 id 后缀 DFS（`collectIdSuffix`）。
  凡是"我们改了但用户说没变"，先怀疑**根本没碰到屏幕上那个实例**（同 §4.0 第 9/13 条）。
- **动的是容器还是子节点，要用 kids 快照点名**。`notification_top_line` 是流式排子节点的容器，
  兄弟的可见性/宽度一翻，线的左端就跟着换，子节点是被拖着走的。
  所以钉子节点可能只是遮盖；要判得先打出线内每个子节点的 `可见性:宽度` 随趟变化
  （本轮已留 `notify_row.col snap … kids=app_name_text:0:214+time:0:136+` 这类读数，上限 40 条 + 120ms 节流）。
  已知会翻的写入者：`NotificationGroupingUtil`（对 `app_name_text` 写 GONE/VISIBLE）、
  `sanitizeTopLine`（对 `time` 写 0/8）、`ActivatableNotificationView.makeActive/makeInactive`
  （**换 time 的文本**从而改宽度）。
- **钉位优于钉常量的场景**：当"正确位置"本身随行内文本宽度变化（右侧时间戳）时，
  钉到常量会把人看出错位移；改成"学它静止时的落点、过渡帧钉回去、偏离 ≥400ms 才跟进"，
  再加一条硬护栏：低于图标列的落点**不认作稳态**（否则会把病灶学成正常值）。

### 4.5 一次性诊断行的正确姿势（别把诊断做成门禁，也别让它刷屏）

`col reg`（登记了谁、找到几枚时间）/ `col predraw`（每帧写没写）/ `col snap`（线内子节点构成）
这一类**临时**读数，规则是：① 按内容去重 + 时间节流 + **硬条数上限**；
② 不要挂在 `sVerifyEnabled` 这类绑定期快照的开关上（钩子里读到的常是旧值，
本轮 `col snap` 第一次就是这么整批丢掉的）；③ 收口后删掉或降频，别让它长住。

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
- **重启 SystemUI 后"模块一条日志都没有"，先查 LSPosed 作用域，再怀疑注入**：`lspctl scope list com.betteroriginos`
  里少 `com.android.systemui` 就是被关掉了（11-04 就这么丢过一次：脚本按旧 dump 的坐标盲 tap，
  焦点其实在 **LSPosed 管理器**，那一下点在应用列表行上=切换开关）。恢复：
  `lspctl scope add --user=0 com.betteroriginos com.android.systemui`，再 kill SystemUI。
  期望作用域以 `module/res/values/arrays.xml` 的 `xposedscope` 为准（本机还多一个 `com.android.dialer`，
  不是我加的，别顺手删）。**判"注入成没成"的唯一硬标准是目标进程里有模块日志**
  （zygote maps 那条 grep 在 su 里嵌套命令替换会被引号吃掉，别拿它的空结果当证据）。
- **盲 tap 之前必须当场核对 `mCurrentFocus` + `mWakefulness`**，坐标一律来自**本次** dump；
  焦点在别人的 App 时那一巴掌打在列表上会改配置（作用域/开关），不是"点了没反应"。
  真需要设备动作就先跟用户确认，别自己连点三次。
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
- **锁着也能取到通知的内部状态**（Y7s/A11 实测）：`monkey -p <pkg> 1` 能把 Activity 起在 keyguard
  之后，`cmd media_session dispatch play` 直接让会话进入 `state=3`（PLAYING），随后
  `adb exec-out dumpsys notification --noredact` 就有 `android.template` / `mColorized` /
  `largeIcon` 尺寸 / `compactActions` / 每个 action 的标题。要判"这张卡到底走不走系统取色链"，
  这些字段比截图硬得多，而且**不用解锁、屏幕可以全程熄着**（OLED 友好）。
  注意 `exec-out cat/dumpsys` 之前要 `export MSYS_NO_PATHCONV=1`，否则 Git Bash 会把
  `/sdcard/...` 换成本地路径，adb 报"No such file"还以为设备没文件。
- **vivo 锁屏的 PIN 面板是一次性的**：`vivo_pin_view` 出现后若屏幕超时（或被判错），面板直接退回
  锁屏，注入的 `input tap` 落点看着对其实打在了空处；`uiautomator dump` 里
  `id/vivo_cancel` 只在"已输入数字"时存在，可用它当"按键有没有落地"的判据。
  两次没解开就停手——vivo 的失败惩罚是锁机，不是重试。
- **`screen_off_timeout` 是取证的大敌**：多命令串行跑到第 20 秒就熄屏，表现为"操作没生效"。
  短时调大（如 120s）跑完再按 OLED 约定调回去，别让它整夜亮着。
- **一次跑完就要把关键样本的日志留档**：`com.bbk.appstore` 端距异常的原始 log 被后续跑覆盖后
  再无法复核，遗留项只能降级成"假说"。有价值时立刻 `cp` 到 `work/z8os3-live/` 另存。
  **重跑同一个门禁就换个名字**（后缀带包 sha256 前缀，如 `a15-load-check-1102-f24325c2.log`）：
  DEVLOG 里"证据在 x.log"的句子是按当时内容写的，固定文件名被下一次跑原地覆盖后，
  那条历史结论就再也复核不了——本轮 `a15-load-check-1102.log` 连吃两次，只能补一句"样本已被覆盖"。

- **"引用了某个 dimen"不是终点，要把它解成真实值**：`media_view.xml` 里封面/进度条/颜色全是
  `@0x7f07xxxx`、`@0x7f06xxxx` 这类 SystemUI 私有资源引用，只看布局会得出"大概 52dp"这种弱结论。
  用 `aapt2 dump resources <该 APK>` 的输出把 id 反查成 `qs_media_album_size = 52.0dp`、
  `media_secondary_text = #99ffffff` 才算证据；还要注意属性的**语义**
  （进度条那 3dp 是 SeekBar 的 `maxHeight`，不是 `layout_height`）。
  本项目把这类反查固化在 `work/y7s_a11_evidence_check.py`（19 条断言 + 10 个反例，
  需要 `work/y7s-a11/sysui_res.txt`）。

- **门禁 shell 里送 python 的路径必须过 `cygpath -m`**：脚本为了 adb 会
  `export MSYS_NO_PATHCONV=1`，它连带让 Git Bash **也不**给 Windows 原生程序转换路径，
  于是 `python "$ROOT/work/x.py"` 变成去找 `C:\c\Users\...` → `No such file`，
  整步断言从没跑过（`os3-smoke-os5mirror.sh` 第 5 步就这么空转了一轮）。
  现成写法在 `work/os3-smoke.sh`（`WINROOT="$(cygpath -w "$ROOT")"`）；新脚本别再裸传。

- **判 DSU/OS3 在不在，用 `getprop | grep -i gsid`，别拿 `ls /data/dsu` + `dmctl status` 当证据**：
  `gsid.image_installed=1` / `ro.gsid.image_running=1` 才是状态源；这台的 `dmctl` 只是
  device-mapper 工具，**没有 `start-dsu` 子命令**（我第一次据此判成"镜像不在机器上"，让用户白等了一轮）。
  DSU 起来后 KernelSU 仍可用（`su` → `u:r:ksu:s0`）、LSPosed 与 scope 都在，A13 门禁可直接跑。
  `lspctl` 的正确姿势：`lspctl status` / `lspctl scope list <模块包>` / `lspctl module show <模块包>`。

- **崩溃类断言要贴在触发动作后面，pid 基线必须当场重取**：把"批量移除有没有把 SystemUI 打崩"
  放在稳定性步骤、却拿 A 相的 pid 比，会在中间任何一次自己重启后假红（本轮就是这样误报了一次）。
  同时 `sep=off` 的负对照别把 `clear-all native=` 这类**探针日志**算进"复刻没关住"——
  它属于装钩子时的诊断，与几何无关。判"是不是我们崩的"只看栈里有没有 `com.betteroriginos` 帧；
  这镜像里 `vivocameraserve` 的 SIGABRT 循环是厂商服务，跟模块无关。

- **DSU 里这台 OS3 的时钟比本机快 8 小时**（日志 `11-03 06:4x` = 本机 `11-02 22:4x`）：
  跨端对时序时先换算，别把它读成"未来的日志"或"设备时间错了"。

## 6. 逆向与取证工具链

- **开放式跨文件逆向派 Explore 子代理并行跑**（本仓库最快的一条路，11-04 两轮 P0-1 卡死后就是它给的解）。
  brief 里必须写死五件事，否则拿回来的是二手猜测：
  ① 症状原话 + 已经量化到的事实；② **要回答的具体问题**（不要写"看看怎么回事"）；
  ③ 可用材料的绝对路径（APK / dexdump / 已反编译零散类 / 已有的 `out/LOG-*.md` 逆向记录，
     并提醒"先读已有记录别重复逆"）；④ 工具姿势（jadx CLI 命令、`aapt2 dump xmltree/resources`、
     "类清单不含代码段所以 0 命中≠不存在"这类本机坑）；
  ⑤ 交付限制：≤500 字、每条挂文件行号或字节码偏移、**找不到就列排除项**、不许提"加日志/逐帧测量"。
  拿回结论后，**load-bearing 那几条自己复读一遍原文再动手**（本轮就是自己复读
  `A13-NotificationTopLineView.java` 才发现"祖先带 translationX ⇒ 改 layout 框必然慢一帧"）。

- **读 dimen 真值只能走 `aapt2 dump resources`**：SystemUI.apk（OS5 与 OS3 都是）**不含 `res/values/*.xml`**，
  values 只编进 `resources.arsc`；所以 `grep res/values/dimens.xml`、`unzip -l | grep values` 一律为空，
  别把"搜不到"当成"这个值不存在"。姿势：
  `aapt2.exe dump resources <apk> | grep -A1 -E "resource 0x[0-9a-f]+ dimen/<name>$"`。
  实例结论（2026-11-02，`out/LOG-1102-A13-LANDING.md` §1b）：A13 与 A15 的 `notification_corner_radius`
  **同为 12dp**，差异在 A15 独有的一族 `notification_shadow_radius_{default=15,sharp=12,mellow=24,smooth=24}dp`
  （A13 只有 `notification_shadow_radius=17dp`）⇒ 跨版本观感差异要按"分档策略"去逆，别只盯基线 dimen。
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
- **字节码离线验证要求"纯计算不碰 Android 静态"**：被验证的类里只要有
  `static final Handler = new Handler(Looper.getMainLooper())` 这类静态初始化，桌面 JVM 上类加载就会失败
  （`android.jar` 的桩方法直接抛异常），于是一片红 —— 这不是检查坏，而是设计与运行时耦合。
  把纯数学（路径、颜色/半径换算）拆成独立类（本项目即 `Os5Geometry`），Handler 之类改惰性创建。
- **内部 jar 会缺公开字段**：实测 `android.util.DisplayMetrics.fontScale` 在编译用的内部 android.jar 里不存在
  （javac 报「找不到符号 / 位置: 类 DisplayMetrics」）。要字号缩放比用 `getResources().getConfiguration().fontScale`，
  `density` 仍在 DisplayMetrics 上。
- 编译用的 `work/sdk-dl/plat35/android-35/android.jar` 是**系统内部 jar**：部分公开 API 名字与真机不同
  （例：`MediaSession` 里只有 `getSessionToken`，vivo 真机也没有 `getMediaSessionToken`）。
  遇到"找不到符号"先 `unzip -p` + 抽字符串确认 jar 里到底有什么，再决定改名或反射按名探测。
- Git Bash 里 `MSYS_NO_PATHCONV=1` 会让 adb 的**本地**路径也不转换：远端用 `/system/...`，本地写 `G:/dir/x.apk`。

- **压掉原生视图的内容，必须先证明"有顶替物"**（11-05 上午的空白边框）。防小图标闪的手段是压
  `drawable.setAlpha(0)`（摘 drawable 会让 wrap_content 图标量成 0、把 header 列数学搞塌；
  压视图 alpha/可见性/scale 守不住，vivo 每帧写回）。但抑制入口注册了**每帧 pre-draw 兜底**之后，
  "压了却没有顶替"= **永久消失**：组摘要行按 L7 保留原生小图标、不放大图标，于是左上角变成空白边框；
  真机 `light.native RELEASE` 0 条也证明"放开路径从不跑"，没有自愈。
  规则：每个抑制入口先查 `findTag(root) != null`（这一行真有 42dp 大图标顶替），
  否则反过来把 alpha 恢复（也让已经压坏的行自愈）。
- **"改了十遍没变化"是元信息，不是运气差**（11-05 定案）：说明改的对象不受我们支配。
  时间是原生每帧重排的下游 ⇒ 钉不住；正确做法是**不搬时间**（`RELOCATE_HEADER_TIME=false`），
  要让位就让我们自己 addView 的视图让位。做对照时别用 `feature_notify_row=0`——
  它不关时间/布局钩子；要 `lspctl module disable --user=0` 并按 pid 确认零模块日志。

### 4.6 本轮（11-04 深夜收尾）定案：滑动是我们自己算出来的

- **`View.getRight()` 已经等于 `getLeft() + getWidth()`**。`pushTimeRight` 写的是
  `d = target - (offX + time.getRight())`，而 `offX` 本来就是逐级累加的 `getLeft()` ⇒
  左偏移被算了两遍，`d` 里混进了一个**每帧都在变的量**（`time.getLeft()` 由 `top_line`
  的流式 `start` 累加决定，兄弟可见性一翻就变）。**目标自己抖，动作就跟着抖**，
  看起来完全像"系统在跟我们抢写"——其实是我们自己造的抖动。凡是"改了没反应/越改越抖"，
  先把算式里的每一项标注它**随什么变**。
- **一个属性只允许一个主人**。当时同一枚 `time` 上有三个写者、两套口径：
  ① `c.layout()`（`HEADER_TEXT_IDS` 里含 `"time"`，把它当"要搬到 66dp 列"的普通文本）；
  ② `pushTimeRight` 写**绝对** `translationX`（目标=行右缘/箭头左）；
  ③ 我加的 `freezeTimeLeft` 写**增量** `translationX`（目标=它自己学的稳态）。
  ①②③每帧互相覆盖 ⇒ 症状就是三方互推。现在收敛成一个入口 `positionTime` → `pushTimeRight`
  （写绝对值天然幂等），并且 `HEADER_TEXT_IDS` 里**去掉 `time`/`time_divider`**。
- **"pre-draw 慢一帧"是错的假设**：`ViewRootImpl.performTraversals` 的次序是
  measure → layout → `dispatchOnPreDraw` → draw，pre-draw 是本帧最后一个写者，
  读到的是本帧新布局值。所以"钉不住"只能解释为**这一帧没作用到那枚视图**，而不是时序落后。
- **自审找出的"登记一次定终身"**：`registerColClamp` 里 `containsKey(row)` 直接 return，
  注释承诺的"失联重取"全文没有实现 ⇒ 5 个名额被隐藏副本占满，屏幕上那枚永远进不来。
  凡是"缓存/去重 + 注释说以后会刷新"，必须当场确认那段刷新代码存在。
- **`col predraw` 只在 `wrote>0` 才打** ⇒ 没写的时候整行消失，与"没登记/没装监听/被 `findTag`
  门掉/`!isShown`/容差内跟随/400ms 跟进"**不可区分**。诊断行的正确形态是无条件打计数器分解
  （`rows / skipNoTag / skipHidden / wrote`），别把"没发生"和"没观察到"省成同一行。
- **A13 的 `time` 是 `NotificationDateTimeView`**（三处写入点全是 `check-cast … NotificationDateTimeView; → setTime(J)`，
  `sanitizeTopLine` / `NotificationContentView.updateHeaderTime` / `ChildrenContainer.updateHeaderTime`，
  **没有任何 `setText`/`LayoutParams` 写它**）⇒ 它的**相对时间文本会自刷新改宽度**，
  从而每帧改变 `start` 累加。找它要按 resource-id 后缀（`:id/time`）找，按类名 `TextView` 找会错。
- **`recreateNotificationHeader` 会在动画后重灌 header 子树**
  （`Notification$Builder.makeNotificationGroupHeader()` → `RemoteViews.apply(ctx, mNotificationHeader)`）：
  重灌之后我们写过的位移/摆位全部作废。另外 header 有**两个实例**
  （`mNotificationHeader` 与 `mNotificationHeaderLowPriority`），修正必须两份都覆盖。
- **vivo 没换 OS3 的 header 类**（`NotificationHeaderView`/`NotificationTopLineView` 就是被画的那个，
  证据：wrapper 字段类型 + framework 布局根标签 + OS5 的 `vigour_*` 布局仍用同名类）；
  换的是 **OS5** 的 time 控件类（`com.android.internal.widget.NotificationDateTimeView`）。

### 4.7 本轮（11-05 下午，A11 收起态小播放器）新增的坑

- **复刻"某一态"之前，先确认那一态用的是哪份布局**。上一轮的交接把 A11 收起态写成"标准紧凑模板
  （小图标 + 标题/文本 + compactActions）+ 圆角小封面"，**是错的**：`MediaStyle.makeMediaContentView`
  传 `0x03030061`=`vigour_notification_template_material_media_q`、`makeMediaBigContentView` 传
  `0x03030054`=`..._big_media_q`（`work/y7s-a11/A11-Notification-MediaStyle.java:5000-5005/5053-5058`），
  两态**都**是媒体模板、都放 `right_icon` 封面、都无圆角；真差别只有
  ① 动作带位置（收起在文字**右侧**、main_column 是横向；展开在文字**下方**、main_column 竖向）
  ② 进度条有无 ③ 槽数与来源（收起=`compactActions` 前 4，展开=按顺序前 5）。
  可复用的查法：`aapt2 dump xmltree --file res/layout/<名>.xml work/y7s-a11/vivo-res.apk` 把**两份树并排比**，
  比读代码注释可靠；`python work/a11_res.py --dump work/y7s-a11/vivo_res.txt --name 0x030c0242` 解 dimen。
- **`aapt2` 就在 `work/sdk-dl/bt35/android-15/aapt2.exe`**（本轮复核；DEVLOG 里"全盘未命中"那句是找错地方，
  别据此放弃 xmltree 这条路）。
- **"两态共用一套结构"的代码要新增形态时，先查所有能进入某一态的路径**。`installSweepHook` 走
  `restyle(v, null)`，而 `null` 被 `!Boolean.FALSE.equals(null)` 当成"展开"——两态结构相同时这个"角色判错"
  **完全不可见**，改紧凑结构当天就会变成观感缺陷（SystemUI 重启后收起子视图建成展开排布）。
  分辨用系统自己的公开判据 `NotificationContentView.getContractedChild()/getExpandedChild()`
  （`work/systemui/out-src/NotificationContentView.java:379-384`，A13/A15 都有），别猜子节点顺序，
  并把来源打进见证（`restyling … role=contracted src=setter|getter`）。
- **"行被展开"不等于"这张卡该出展开的东西"**：`restyleOnExpansion` 对同一行的**两块卡**都 `setState(true)`，
  所以收起那张卡会收到 `expanded=true`。收起态永不出进度条这类判据必须挂在**结构**（有没有横向
  main_column）上，不能挂在 `expanded` 标志上——真机见证 `state=expanded … compact=1 … seekVis=8` 就是这条。
- **改长一条见证日志，要顺手检查所有拿它当字面锚的反例**。本轮给 `a11 art state=` 追加 4 个字段后，
  反例「A11 只有收起态几何见证」的字面串不再命中 ⇒ `--selftest` 的"空炮"检查当场报红
  （这就是 skill 里"字面 replace 静默失效 = 假绿"那条的自动化版本，**别把它的红当自检坏了**）。
- **验收窗口必须覆盖"只打一次那一拍"**：`a11 card spec` 只在建卡那一打、`a11 art relief` 只在封面
  后处理那一打。取样只做了"展开一次"就会报两条"没跑"——那是**窗口**没覆盖，不是产品坏；
  正确窗口 = 重启建卡 → 换歌 → 收起 → 展开，四段各自 `logcat -d >> 日志` 追加。

### 4.8 本轮（11-05 傍晚，"展开/收起时小图标闪一下"）新增的坑

- **"有没有顶替物"的判据不许读动画驱动量**。`bigIconShowing` 里那句
  `big.getAlpha() <= 0.5f || !big.isShown()` 把"这一行归不归我们接管"问成了
  "顶替物此刻在不在屏上"：展开/收起动画里 vivo 连子树 alpha/可见性一起写回，过渡帧就是
  `isShown()==false` ⇒ 判成"没有顶替" ⇒ 走恢复分支把原生小图标放回可见 ⇒ 用户看到
  "展开或收起时小图标闪一下"。逐帧读数（`betteroriginos_trace_flicker`）：
  `big-d1 | v0:a1.00:da255` ⇄ `v4:a0.00:da255`。判据只读**归属 + 有没有内容**
  （`findTag(root)!=null && getDrawable()!=null`），alpha/isShown/visibility 一律不参与。
- **0 条见证不能当"没发生"**（同一类错误今天第二次）。`RELEASE` 那条原来的条件是
  `ni.getScaleX() == 0f`，而 scale 压制方案早就废了、没人再写 scale ⇒ 这条永远不打印，
  昨天的记录因此写下"我们自己的放开路径一条都没跑过"。把条件换成按语义判断
  （放开前 drawable alpha 确实为 0）后，同一台机同一套操作打出 **436 条**。
  **凡是"某路径没跑过"的结论，先证明那条路径的打印条件本身能成立。**
- **别把"闪"修成"空白"**：把归属判据从"子树"升到"整行"（行里任一份拷贝有大图标就全压）
  确实止抖（`RELEASE` 526→4），但真机读数里 `big=-` 的摘要行图标跟着被压 ⇒
  观感从"闪"变成"没有图标"。判据换作用域时，必须把**另一侧的观感读数**一起看，
  不能只看"抖动指标归零"。
- **加了机制就要留下"它真的跑了"的正面读数**：为"两份 header 拷贝共用同一个 Drawable"
  这条假说写了 `privateDrawable()`（复制 constant state 再 mutate）并配
  PRIVATE / PRIVATE-SKIP / PRIVATE-FAIL 三条见证，装机后**三条都是 0**
  ⇒ 假说错、机制从不被调用，整体删除。同理 `mutate()`+`setAlpha` 这类每帧写要加
  "已经是目标值就不写"的幂等门，否则自己就是一台永不停的重绘机（窗口永不 idle，dump 取不到样本）。
- **媒体行与普通行的图标接管是两套人，别在死分支上找现象**：`guardExpandedGhost` 对媒体行
  走 `isMediaRow` 直接 return，而 `isLightStyle()` 早已恒真 ⇒ 那里的 `morphIcon` 调用点是
  **死代码**（真机读数 `ghost dedup`=0、`light.morph`=0 就是证据）；light 路径的飞行在
  `guardLightMorph`。判"为什么没有某条日志"先确认哪个 caller 才活着。

## 7. 文档纪律

- **OS3 验收有脚本**：`work/os3-smoke.sh [序列号] [group_mode]` —— 环境前置（防把宿主当被测环境）、
  钩子装载、投递样本、慢滚到可视区、`verify_notify.py` + 覆盖闸门、一键清除式批量移除回归、
  媒体卡链路、收尾。退出码非 0 就是有断言失败；改完通知/媒体相关代码就在 OS3 上跑一遍。

功能落地后同步更新：`out/DEVLOG.md`（含踩坑表）、`out/DESIGN-SPEC-1025.md`（token/组件/架构变化）、
本 skill（新坑进 §4）。规范问题以 DESIGN-SPEC 为准，不在这里重复。
跨版本适配的过程证据写进 `out/LOG-<日期>-OS3-ADAPT.md` 这类**阶段性落盘日志**（每阶段：做了什么、证据路径、结论、下一步）。
