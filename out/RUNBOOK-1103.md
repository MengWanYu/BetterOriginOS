# 运行手册 · 2026-11-02/03 夜（OS5→OS3 移植 + A11 音乐卡：装机与门禁）

> **22:45 更正（用户当场纠正了我的前提）**：Z8 这台 OS5 宿主**没有锁屏密码**，
> "解锁"就是 `keyevent 224` + 上滑（skill §5 里本来就这么写）。我此前把它说成
> "安全锁、需要你在场解锁"，是我把另一台机（Y7s 的 PIN）的约束套错了 —— 所以
> **A15 侧的渲染度量门禁不需要你做任何事**，我从 OS5 环境里自己解锁自己跑。
> 仍然需要你说一句话的只有一件：**什么时候退出 DSU 回 OS5**（那是一次重启）。
> DSU 状态也已被我误判过一次，正确查法是 `getprop | grep -i gsid`（见 ADAPT §13.0）。

过夜完成的是"逆向 + 实现 + 离线验证"；下面这些**必须真机跑**才算数。

当前基线包：`module/build/BetterOriginOS.apk`（sha256 前缀见下，以最新构建为准）
```
cd module && ./build.sh && sha256sum build/BetterOriginOS.apk   # 22:10 这轮为 f24325c2fb15（第五轮审查：档位 ClassLoader / 圆角签名 / 间距通道 / 背景裁剪本帧矩形）
```
**装机前先跑一条**：`bash work/offline-all.sh`（写这段时 16 项，22:15 这轮 **18 项全绿**，样本
`work/z8os3-live/offline-all-1102-2215.txt`。内容：G2 逐指令与"翻符号"负对照、
A11 数学/交叉/取色网格/六族选择/**量化器 median-cut**、验证器正反例自测、Y7s 实读取证核对、日志形状自检）。
`--expect-inert` 是给 A15 用的：复刻钩子在那台机**不该装**，所以正确形态是一条 `os5_mirror` 都没有。
**这条已在 `f24325c2fb15` 上跑过并通过**（22:15，pid 23195→24171；`os5_mirror` 0 条 / `installed feature` 2 条 / 崩溃 0 条，
样本 `work/z8os3-live/a15-load-check-1102-f24325c2.log`）——也就是说 RUNBOOK 里"真机门禁全部欠跑"只剩
**需要解锁的 A15 渲染度量** 和 **需要 DSU 的 A13 全套**。以后重跑请把样本按包哈希另存，固定文件名会被下一次跑原地覆盖。
它红了就别上设备——前提或实现已经分叉，真机跑出来的"像不像"没有解释价值。
最后一轮改动（不影响门禁命令）：`Os5Mirror.level()` 改成**只在真读到 fillet level 时才写缓存**，
并订阅 `onSystemFilletChanged` 失效缓存——之前"读失败也缓存"会让圆角档位永久停在猜值上。

---

## 0. 现在的实况（22:50 更新，取代我夜里写的旧版本）
- **OS3 已经在跑**：DSU 一直是装好的（`gsid.image_installed=1` / `ro.gsid.image_running=1`），
  用户一句话就进了 **Android 13 + vivo OS 13.5**，KernelSU(`u:r:ksu:s0`) + LSPosed 2.2.1 +
  模块 scope（systemui/systemuiplugin/media provider/filemanager/dialer）齐活，`/proc/zygote64/maps` 命中 6。
  ⇒ 我之前"要装 DSU、要镜像路径"那一节整个作废，别照着做。
- **A13 复刻门禁已真机全绿**（包 `b08a88f7bf24`，23 条断言 / 0 失败，见 ADAPT §13.4）。
  离线侧 `bash work/offline-all.sh` 22 项全绿。
- **还欠两件事**：
  1. **退出 DSU 回 OS5**（一次重启，需要你点头）→ 之后 `a11-card-gate.sh <sn> os5` 与
     notify_row 的 A15 原生回归都由我自己跑，解锁是 `keyevent 224` + 上滑（这台没有密码）。
  2. **Y7s 的 PIN 两次没过**（PIN 不写进文档）：只挡"控件真实 bounds"这一项取证。

**只有十分钟的话**：先 `bash work/offline-all.sh`（22 项，无需设备，红了就别上机），
再看 §3 的 Y7s 待办与 §1 的 A15 门禁清单。

---

## 0′. DSU / OS3 环境的正确查法（我今夜在这儿错过一次，别再错）
- 判 DSU 装没装、跑没跑：`adb shell getprop | grep -i gsid`
  （`gsid.image_installed`、`ro.gsid.image_running`）。**不是** `ls /data/dsu`。
- 这台的 `dmctl` 只是 device-mapper 工具，**没有 `start-dsu` 子命令** —— 别拿它当 DSU 入口。
- LSPosed 侧核对：`su -c '/data/adb/ksu/bin/lspctl status'`、
  `lspctl scope list com.betteroriginos`、`lspctl module show com.betteroriginos`。
- 装新包后**必须连续重启两次 SystemUI**（首启可能仍是旧 dex），并核 `sha256sum` 与
  `pm path` 取回的文件一致；取文件用 `adb exec-out cat`（过 su 管道会多出脏字节）。
- 这台 DSU 的时钟比本机快 8 小时（日志 `11-03 07:0x` = 本机 `11-02 23:0x`）。

---

## 1. OS5 宿主（A15）侧：A11 音乐卡
```
adb -s 10AE4Y0MDE000BL install -r module/build/BetterOriginOS.apk
# 然后请用户解锁；连续重启两次 SystemUI（LSPosed 首启可能仍跑旧 dex）
adb -s 10AE4Y0MDE000BL shell su -c 'kill $(pidof com.android.systemui)'; sleep 15
# 再次重启后确认新包生效：日志里要出现下面这条，否则说明还是旧 dex
#   media_card: ... a11 card spec ...
settings put global betteroriginos_feature_media_card 1
settings put global betteroriginos_media_card_style a11
bash work/a11-card-gate.sh 10AE4Y0MDE000BL os5      # 需要用户在旁边待解锁
```
门禁第 4b 步顺带证明**两条线互不干扰**：
- `os5 mirror hooks installed` 在 A15 上必须 **0 次**（`NotifyRowFeature` 用 `OsProfile.isA13OrLower()` 关门）
  —— 否则 OS5 会长出第二枚清除钮、半径被改两次；
- A13 跑 os3 模式时它必须 **≥1 次**，且 `sep=off` 时不许出现 `clear-all attached/tap/at` 行。
```
```
看什么（字段名与日志逐字一致，验证器就是这么解析的）：
- `restyling style=a11`
- `a11 palette expanded base=… highlight=… artwork=true art=WxH gridBg=N`
- `a11 palette collapsed bg=… fg=… primary=… secondary=… contrast=… art=WxH gridBg=N gridFg=M`
  —— `gridBg/gridFg` 必须等于"A11 两步缩放"算出的像素数（Y7s 那张 200×200 封面是 11250/13500）；
  这两条日志的格式变了，别拿旧 fixture 的字段名去比对
- `a11 card spec art=… btn=…/… src=… titlePx=… trackDp=3 density=… fontScale=… radius=… hostCorner=…`
  —— `radius` 是 A11 常量 2dp×density，`hostCorner` 只是宿主对照、不参与判断
- `a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true`
- 没有封面时 `a11 palette expanded … artwork=false` 的 `base` **必须是 `ff404040`**
  （A11 是把白色继续送进 HSL 变换算出来的，`ff444444` 是另一条 resumption 路径的值）
- 数值断言：`PYTHONIOENCODING=utf-8 python work/verify_os5_mirror.py --media-only <日志>`

## 2. OS3（A13）侧：OS5 复刻（圆角 / 收纳栏 / 一键清除）
```
bash work/os3-smoke-os5mirror.sh <DSU序列号> <density>     # 默认 3.0
```
该脚本自带 A/B 两相与三类硬断言：
- **钩子命中数**：`os5_mirror: install groups=… initDimens=1 fillet=1 getRoundedRectPath=1
  shelf.onFinishInflate=1 shelf.bind=1 shelf.onConfigurationChanged=1 shelf.changeShelfVisibility=1 shelf.updateAppearance=1 shelf.setDismissVisible=1
  nssl.onExpandedChanged=1|nssl.setIsExpanded=1 bg.onDraw=1`，任何一项为 0 直接判失败（"钩子装了但没落点"）。
  A13 的 NSSL 没有 `onExpandedChanged`，模块会自动退到 `setIsExpanded` —— 日志里出现哪个名字就用哪个，
  两个都缺才算红。
- **落点自检**：`os5_mirror landing reachable …`（第一跳还会打一条 `os5_mirror landing tick#1 …`，
  用来区分"这条链根本没跑"和"跑了但判据没命中"；中途被 `enabled=false` 掐断则打
  `landing interrupted …` 并允许下次重排）。**`no-entry` 一定算失败**；整段没有 verdict 时，
  脚本退到同源几何见证（`g2 path` 与 `shelf topRoundness` 都 ≥1）判定落点成立 —— 那两条正是
  `reachable` 的判据本身，不是另找的证据。
- **几何度量**：`radius level/px`、`g2 path`、`bg clip g2 r=… bounds=…`、`shelf topRoundness=1.0 readback=1.000`、`shelf gap yFrom=0.0 yTo=21.0 base=0.0 want=21`（间距走 A13 自己的 yTranslation 通道；断言的是**帧间关系**：施加帧 `base==yFrom`、幂等帧 `base==yFrom-want`，而"上一帧刚写出的位置又被当成原生基线加一遍"直接判每帧累加。旧断言比 `yTo==base+want` 是实现自算的表达式，永远绿）、
  `clear-all native=false`、`clear-all attached size=138 marginBottom=120 nav=false navSrc=insets navVis=0 navH=0 shade=fraction host=NotificationPanelView`、
  `clear-all at x=… y=… w=… h=…`。
  `shade` 是"面板展开状态到底从哪读到的"：出现 `unavailable/nssl-missing` 说明按钮会在收起态也露出来，
  验证器直接判红。`host` 必须是 `*Panel*`：A13 的 `NotificationsQuickSettingsContainer` 是
  **ConstraintLayout**，挂在那儿 gravity 会被吞、按钮落左上角（静态已修，仍需设备用 `at` 坐标复核）。`native=true`（OS5/A15）时**不该**再有 attached/at/tap 行 —— 系统自带圆钮。
- **点击链路（脚本第 4 步，本轮新增）**：坐标只取 `clear-all at`，tap 中心后要求
  `clear-all tap inlineListener=1 inlineVis=4 shelfVis=…`，并用 `dumpsys notification` 里
  `NotificationRecord(` 的**可清除条数**（排除 `ONGOING_EVENT|NO_CLEAR|NO_DISMISS`）前后对比证明真的移除了。
  `inlineListener=0` = 原生监听根本没挂（它只在 `NSSL.setShelfController` 挂一次），这一击是空转。
  **位置硬判据**（就是 ConstraintLayout 吞 gravity 那次的回归闸）：用 `wm size`（有 Override 取最后一条）
  要求 `|中心x - 屏宽/2| ≤ 2px` 且中心 y 落在屏幕下半部——未约束视图会被摆到左上角，这条会直接红。
  **注意**：这台机常驻 LSPosed 的 ongoing 通知 ⇒ `z=!hasUnClearableNotifications=false`，
  点完"收纳栏还留着"才是对的；若看到栏消失，反而是我们哪里错了。
- **负对照**：B 相 `sep=off` 时**不得**出现任何 `os5_mirror:` 日志（含 `clear-all`）。
### 2.1 结果级取证（观感三缺陷：抖动 / 划回丢圆角 / 收纳栏闪烁）
```
bash work/os3-evidence-rowlayout.sh <DSU序列号> <density>   # 默认 10AE4Y0MDE000BL / 3.0
```
产出 `work/z8os3-live/row-<apk哈希>-{idle,expand,scroll}.{xml,log}` 三组**成对**证据，逐态跑
`verify_row_layout.py`（L1~L8 + R0~R5），再量化三条症状：
- 抖动 = **展开手势窗口内**同一行被 polish 的次数（`restyled light … row=<hex>` 带行身份）。
  投递阶段与手势阶段按时间切开统计；整窗口 0 行**报红**（那是 logcat 清零时机不对，判据空转，
  不是"没抖"）。
- 展开是否真发生 = idle 与 expand 两帧"最宽通知行"的高度对比（本轮 642 → 1323px）。
  手势取 `android:id/expand_button` 的中心：在行身上滑这台机当成滚列表（实测高度不变），
  tap 行中心会命中 contentIntent 把面板带回桌面（焦点校验抓到过）。
- 丢圆角 = scroll 帧的 R1~R5（独立行 1/1、组子行 0/末行 1、shelf 1/1、`radii == roundness*R`）。
- 闪烁 = `os5_mirror: shelf/button` 行数（该日志只在可见性变化时打，行数即翻转数；修复前实测
  11 次，现在 0~1 次），同时要求同窗口确有 `roundness … shelf=1` 的度量 —— **没有收纳栏就是没测到**，报红。
脚本第 0 步核对**设备上 APK 的 sha256[:12] 与本地构建一致**，不一致 `exit 2`：
`build.sh ... | tail` 会把编译失败的退出码洗成 0，我们因此装着**旧包**跑完过一整套"全绿"取证。
样本投递里的 `postbulk`（`--es debug_notify postbulk`，14 条普通通知）专门把列表撑到溢出 ——
栏都不出现，症状②③就不在观察范围内，"全绿"只是没测。

再复跑既有门禁查回归：
```
bash work/os3-smoke-light.sh <序列号>     # 注意：跑之前把 sep 置回 off
bash work/os3-smoke.sh <序列号>
```

## 3. Y7s（A11）取证：已完成大部分，只剩一项
02:10 那轮用"不需要解锁"的通道把 §9 的缺口逐条销了（详见 `out/LOG-1102-A11-MEDIACARD-RE.md §13`）：
`contentView=null` + `android.template=…$MediaStyle`、`mColorized=true`、`largeIcon 200x200`、
`compactActions=[2,3,0]`、`actions=5`、`config_dialogCornerRadius=2dp`、按钮排 48/48/52/48/48dp。
可重跑核对：`PYTHONIOENCODING=utf-8 python work/y7s_a11_evidence_check.py`
（**25 条断言**，`--selftest` 跑 **14 个反例**；现在必需 `work/y7s-a11/sysui_res.txt`，
用它把 `media_view.xml` 里的 `@0x7f07xxxx` 资源引用解成真实 dp/颜色值）。
这条已挂进 `work/a11-card-gate.sh` 的第 0 步，前提跑红就别去真机找"像不像"。

**关于 PIN**：你给的 114514 我试了 2 次没通过（`dumpsys trust` 仍是 `deviceLocked=1`），
已停手不再试（vivo 多次失败会锁机）。若那台机还有别的解锁方式（指纹/账号），告诉我一声，
剩下的"控件真实 bounds"就需要解锁后拉下通知栏用 uiautomator dump 复核；没有也不影响结论，
尺寸/圆角/色链已由布局 XML + framework-res + dumpsys 三方定死。
OLED 保护照约定执行了：亮度 20、抓完立刻 `keyevent 223`，全程熄屏取证（没截图）。
```
# 若要补 bounds（需要解锁）：
adb -s IBR8GEMFJNBECIFA shell cmd statusbar expand-notifications
adb -s IBR8GEMFJNBECIFA shell uiautomator dump && adb -s IBR8GEMFJNBECIFA exec-out cat /sdcard/window_dump.xml
```

## 4. 已知"未验证/可能翻车"清单（别当成已完成）
| 项 | 风险 | 一眼判据 |
|---|---|---|
| G2 在 A13 的实际观感 | A13 背景是 `GradientDrawable`，我们靠 `canvas.clipPath` 裁形；硬件层对 path clip 的表现需实测 | `bg clip g2` 日志 + 用户口述一句"圆角像不像 OS5" |
| 半径基线自纠正 | 依赖 `mOutlineRadius` 字段名与 `getExpansionFraction` 在 A13 的行为 | `radius level=.. px=..` 且 px 命中 36/54/72 |
| 浮动清除按钮 | 挂在 shelf 的父容器上，可能被列表遮挡或在动画期错位 | `clear-all attached` + 展开/收起时 visible 日志 |
| A11 收起态在 OS5 是否触发 | OS3 媒体行默认展开，OS5 可能一直走 expanded 链 | `a11 art state=` 两态是否都出现 |
| 批量清除稳定性 | 既有 NPE 坑（`getGroupKey` 为 null）已规避：我们只转接原生 `performClick()` | `os3-smoke-os5mirror.sh` 的 pid/crash 断言 |
| G2 的 2.2r~2.6r 带 | 收起行高经常落在这里；03:12 才把常数套与门限改对，真机未看过一条 G2 曲线 | `g2 path` 日志的 `常数套=1.1r|1.3r` 要与行高/半径自洽 |
| 收纳栏配置变更后状态 | 系统 `onConfigurationChanged` 末尾会 `setDismissVisible(内联可见性==0)`，我们按成 GONE 就是**自造 false**；已用 `sInConfigChange` 标记不采信该参数并事后重算。仍需实机转屏一次 | 转屏/改字号后 `shelf … readback=1.000` 仍在，且浮动按钮还能出现（`clear-all attached … shade=` 之后没有"永不出现"） |
| a11 两态重着色 | 去重键刚加上 `style/expanded/night`，展开↔收起是否真跑了第二次取色 | `a11 palette collapsed …` 与 `… expanded …` 要成对出现（同一首歌） |
| 收纳栏消失动画 | **不再由模块补播**（12:25 删）：A13 原生 `animateShelfDisappear` 与 OS5 逐参数相同，且带 `mClearAllInProgress` 与 `z=!hasUnClearableNotifications` 两道门；我们自己播会把还剩 ongoing 通知的栏抹掉 | 点击后 `NotificationRecord(` 可清除条数下降、不可清除的仍在；栏收不收由系统决定，模块日志里不该再出现补播动画 |
| 两枚清除按钮重叠 | 系统已有 `NotificationClearAllImageView`（A15 有 / A13 无）时不该再补挂；用类存在性探测而不是版本号 | `clear-all native=true` 时**必须**没有 `attached/at/tap` 三行；A13 上反之 |
| 点击是否真落到原生链 | 监听只在 `NSSL.setShelfController` 挂一次，没挂上时 `performClick()` 静默空转 | `clear-all tap inlineListener=1 inlineVis=8`（inlineVis≠8 说明 `hideInline` 没生效，收纳栏也会因此不收） |

## 5. 全绿之后
- 更新 `out/DESIGN-SPEC-1025.md`（token/组件）与本 skill 的 §4 新坑；
- 重新打包发布 zip（`out/` 里那份 0.1.1-beta 早于这一切）；
- 提交：`git add` 前先看 `git status`，确认不把 `work/` 里的大文件（framework-res、Y7s APK 已 gitignore）带进去。
