# BetterOriginOS 开发日志

## 2026-10-25 · 项目启动与首个功能

### 目标

- 研究 iQOO Z8（PD2314 / V2314A，Android 15 / OriginOS 5）的系统界面优化。
- 首个功能：通知中心媒体卡样式替换（OS5 原子随身听风格 / Material You）。
- 形态：LSPosed 模块，作用域 `com.android.systemui`；模块包 `com.betteroriginos`。

### 今日里程碑

1. **逆向定位**
   - SystemUI 版本 15.0.5.241；媒体卡是框架 `com.android.internal.widget.MediaNotificationView`（MediaStyle 通知模板）+ vivo 卡片皮肤。
   - 通知行：`ExpandableNotificationRow` + `NotificationContentView`；`NotificationBackgroundView` 的白色背景会导致我们圆角卡四角露白，已隐藏（alpha=0 / INVISIBLE）。
   - 锁屏「原子随身听」= Origin Player（`com.vivo.musicwidgetmix` 5.2.0.6）的远程小组件，由 SystemUIPlugin 的 `KeyguardMusicWidgetManager` / `MusicSwipeView` 承载；Origin Player manifest `sharedUserId=com.android.bbkmusic`，与 iMusic 同 UID。
2. **媒体卡 restyle**
   - Hook `NotificationContentView.setExpandedChild/setContractedChild`，在 `MediaNotificationView` 上重建卡片。
   - 保留标题/歌手/封面/5 个原通知按钮的点击链；封装 `MediaCard` 引擎（会话、进度、seek、取色、生命周期）。
3. **vivo seek 桥**
   - iMusic 的 MediaSession `actions` 不含 `ACTION_SEEK_TO`，标准 `seekTo` 无效。
   - 通过 `com.vivo.musicwidgetmix.service.MusicWidgetMixService`（exported）的 `IMusicWidgetMixService.seekPos(long)`（事务码 15）实现真实 seek；SystemUI 直接 bind，无需新增 scope 或 hook iMusic。
4. **样式与视觉**
   - OS5 原子风：封面模糊底 + 白蒙层、米色卡片、细底轨 + 10dp 粗已播放段 + 同尺寸圆点、固定宽度/等宽时间防抖。
   - Material You：封面主色 → 色调渐变卡、实心播放键、强调色进度条。
   - 应用图标：自适应图标 + 各密度 PNG；应用更名 BetterOriginOS，版本 0.1.0。
5. **配置通道**
   - 本地 `XSharedPreferences` 在 SystemUI 侧读不到（LSPosed 文件服务/权限），hook 回退默认值。
   - 改为 `Settings.Global` 优先 + 本地回退；设置页用 `su` 写全局键；旧 `betteroriginos_style` 迁移到 `betteroriginos_media_card_style`。
6. **设置 App 与框架**
   - 主页改为应用列表（按功能第一个 scope 分组），点击进入应用详情；关于页汇总 scope、引擎说明、重启。
   - 通用设置页由 Feature 注册表生成：功能卡、开关、选项行、选择对话框；新增功能只加 Feature + 注册 + 字符串/资源。
   - 媒体卡是第一个 Feature：`MediaCardFeature`；选项 `style` = os5 / material，带预览图。

### 关键文件

- `module/src/com/betteroriginos/core/{Feature,FeatureOption,FeatureManager,Prefs}.java`
- `module/src/com/betteroriginos/feature/media/MediaCardFeature.java`
- `module/src/com/betteroriginos/ui/{MainActivity,AppDetailActivity}.java`
- `module/src/com/betteroriginos/hook/MainHook.java`
- `module/build.sh`（aapt2 资源 + R.java + d8 + zipalign + apksigner 轻量链）
- 产物：`module/build/BetterOriginOS.apk`（~96KB）

### 部署

```bash
# 安装/更新
adb install -r module/build/BetterOriginOS.apk
# LSPosed 启用 + scope
adb shell /data/adb/modules/zygisk_lsposed/lspctl module enable --user=0 com.betteroriginos
adb shell /data/adb/modules/zygisk_lsposed/lspctl scope set --user=0 com.betteroriginos com.android.systemui
# 样式/开关写入（设置页会通过 su 写；测试可直接）
adb shell settings put global betteroriginos_style material
adb shell settings put global betteroriginos_media_card_style material
adb shell settings put global betteroriginos_feature_media_card 1
# 重启 SystemUI
adb shell "kill \$(pidof com.android.systemui)"
```

回滚：停用模块 / 卸载 APK / 重启 SystemUI；不修改系统分区。

### 验证证据

- `logcat -s BetterOriginOS`：`installed feature media_card for com.android.systemui`、`restyling style=material`、`widget service connected`、`seekable=true native=false widget=true`。
- 真机截图：`work/shots/v09-user-open.png`、`v10-user-open.png`、`better-settings2.png`。
- 四角白边：隐藏 `NotificationBackgroundView` 后消失。
- 进度条拖动：通过 vivo `seekPos` 真正改变播放位置；两端圆点内缩后完整。

### 已知问题 / 未验证

- 设置页每次写 Global 需要 KernelSU 授权（首次）；样式/开关修改后必须重启 SystemUI。
- 深色模式页面未逐项截图验证。
- 应用列表目前只有系统界面；未来接入第二个目标应用时，需要在 LSPosed 增加 scope。
- 媒体卡针对 vivo MediaStyle 通知实现；其他厂商/第三方播放器的标准 MediaSession seek 走标准分支，未逐一实测。
- 模块内未做版本迁移 UI（旧 `betteroriginos_style` 仍兼容读取）。

### 下一步

1. 给 Feature 增加“复杂选项页/二级页面”能力（选项超过 2 个时）。
2. 按应用接入第二批功能（例如 SystemUI 状态栏、控制中心）。
3. 深色模式适配与回归清单。
4. 设置页写入失败/授权失败的可见提示。
5. 交接文档与功能开发模板（Feature 脚手架）。

## 2026-10-25 16:40 · 应用列表与交接

- 设置 App 重构为「应用列表主页 → 应用详情」；Feature 注册表驱动功能卡/开关/选项/对话框。
- 主页已可在真机显示：系统界面（com.android.systemui）1 项功能 · 1 已启用；关于页含 scope/引擎/重启。
- 详情页已可显示：应用头部、通知中心媒体卡开关、「卡片样式 Material You」行、重启。
- 待整改：详情分类「系统界面」与应用名重复（应改「媒体」）；样式选择对话框未验证；深色模式/间距/应用图标/授权提示等 UI 缺陷。
- 交接文档：`out/HANDOFF-BetterOriginOS-1025-UI.md`（新对话先读本文 + DESIGN-SPEC + 本日志）。

## 2026-10-25 18:10 · OS5 视觉重构 + 底栏架构（UI 整改完成）

### 决策

- 用户确认放弃 miuix 基底（fan.miuix 只在 GitHub Packages，需 token，源码不公开），改为**代码级复刻本机 OS5 系统应用设计**。
- 逆向来源：本机 `com.android.settings`（Settings.apk 126MB）+ i 管家（底栏基准）+ 真机截图取色。
- 关键 token：卡片圆角 12dp、页底 #F2F2F5/#000、卡片 #FFFFFF/#202020、强调色 vivo 蓝 #0F77FF/#2785FF、行标题 15sp、大标题 28sp。详见 DESIGN-SPEC-1025.md。

### 架构变更

- **底栏双页**：MainActivity = 首页（应用列表）+ 模块信息 tab（应用信息/生效状态/作用域），自定义底栏（无 androidx，framework ListView + ListPopupWindow）。
- **详情页**：重启从底部大按钮改为**右上角小图标 + 确认对话框**（ConfirmDialog，居中圆角卡）。
- **样式选择**：底部大弹层改为**行下方小下拉菜单**（ChoiceDropdown，ListPopupWindow 锚定 + 预览图 + 勾选态）。
- **写入链路**：新增 `core/SuCommander`——同 key 250ms 防抖、执行回执、失败 toast、**待生效计数**（详情页底部 + 信息页实时同步）；重启成功清零。
- **Prefs 去特判**：media_card 的 legacy 迁移改为 Feature 静态注册（`registerLegacyFeatureKey/OptionKey`）。
- **图标**：直接取目标 APK 本身图标（IconMapper），废弃定制 SystemUI 图标；未来桌面/设置 hook 自动用各自真图标。
- **应用行级圆角分组**（top/mid/bot drawable + spacer 行），修复详情页根布局 wrap_content 导致 weight 失效的问题。
- build.sh 修复：路径改为脚本位置推导（文件夹改名免疫），MSYS 路径用 cygpath -m 转 Windows 形式。

### 真机验证（iQOO Z8，亮/暗全过）

- 首页（真实图标+大标题+分组卡+底栏）、详情（媒体分组+开关+样式行）、行内下拉（预览图+勾选）、
  关闭态（选项 alpha 0.45 且不可点）、待生效提示（写入 style/开关后「已修改 2 项」）、
  确认对话框（取消/确认）、深色模式（纯黑底+#202020 卡+蓝强调）。
- 写入验证：`settings get global betteroriginos_media_card_style` → material；feature 开关 → 1。
- Hook 回归：模块重装后 SystemUI 重启，logcat `installed feature media_card` 正常。
- 产物：module/build/BetterOriginOS.apk（113KB）。

### 遗留

- 框架 Switch 与 OS5 原生开关仍有细微差异（可接受，后续可自绘）。
- 待生效计数为会话级，App 进程被杀即清零（与真实配置可能漂移，属显示层近似）。
- 第二个作用域应用（桌面/设置）接入时按 DESIGN-SPEC 新增功能清单走。

## 2026-10-25 20:10 · 真实卡片实时预览 + 折叠/搜索

### 需求演进（用户驱动）

1. 底栏导航 + 模块信息独立页 → 已完成（上一条目）。
2. 卡片样式选择改为**行下方小下拉菜单**（ChoiceDropdown，ListPopupWindow + 预览图 + 勾选），弃用底部大弹层。
3. **真实卡片实时预览**（用户："自己脑补的预览和实际运行的完全不像"）→ 渲染真实 SystemUI 卡片。
4. 预览**常驻置顶在分类条目下方**，分类可折叠；详情页加搜索栏；重启确认框不需要预览，预览只在调整选项时即时反馈。

### 真实预览架构（本次核心）

```
App: su 写 betteroriginos_preview_req="<ts>:<style>"
Hook: ContentObserver → 离屏合成模板（真实标题/封面/取色）
      + 复用产线 MediaCard.build() → Bitmap → PNG
      → ① filesDir/preview/（su 兜底）② MediaStore Pictures/BetterOriginOS（主通道）
      → done 回显完整请求串
App: 轮询 done ≤5s → MediaStore 读取 → 显示真实卡片
```

- 手绘 `MediaCardPreview` 重写为产线参数兜底（此前蓝色渐变完全是错的：真实 m3 是取色暖粉渐变、accent #A93A27；os5 是 #FDF6EC 米色 + #4A4A4A 进度）。
- `Feature.options()` 改模板方法 + 缓存（原每次 new 导致 previewOption 引用失配）。

### 调通过程中踩的坑（全部已修，含证据）

| 坑 | 现象 | 修复 |
|---|---|---|
| 通知 View 的 Context 是发起通知应用（createPackageContext→bbkmusic） | getFilesDir 落到 bbkmusic 目录，SystemUI 写不进 | `ActivityThread.currentApplication()` 反射取 SystemUI 本体上下文 |
| PrecomputedText setText 崩 | 离屏标题直接 setText 真实通知标题 | `.toString()` 转普通串 |
| 管道死锁 | 135KB base64 输出 > 64KB 管道缓冲，先 waitFor 永久超时 | 边读边等 + 截止时间 |
| App 的 su 读不到 SystemUI 文件（ENOENT） | KernelSU 对 App 会话的挂载命名空间隔离 | **改走 MediaStore**（App 加 READ_MEDIA_IMAGES，adb pm grant 一次） |
| 请求/渲染样式竞态 | App 要 os5，Hook 渲染的是它当时读到的 material | 协议 req=nonce:style，done 回显完整串 |
| 搜索过滤把匹配项也滤掉 | rebuild 里 `!searching &&` 误加条件 | 搜索时忽略折叠、只按匹配过滤 |
| item_choice 行 clickable 吞掉 ListPopupWindow 条目点击 | 下拉选不中 | 行去掉 clickable，统一 onItemClick |
| 详情页根布局 wrap_content | ListView weight 失效、按钮不贴底 | 根改 match_parent |
| build.sh 硬编码旧目录 + MSYS 路径 | 改名后构建失败 | 脚本位置推导 + cygpath -m |
| 误删 Xposed 编译桩 | javac 空输入报 -help | 重建 6 个最小桩文件 |

### 真机验证（iQOO Z8，20:05 全过）

- 详情页：搜索栏 + 可折叠"媒体"头（收起只剩行+右箭头，预览随折叠）+ 常驻预览。
- 预览：显示 **Hook 真实渲染**的卡片（真实封面/标题"2025 全中文 DJ舞曲车载串烧…"/DJ桦仔/取色底）；
  下拉切 OS5→米色 os5 渲染、切 Material→取色粉渐变渲染，**即时切换无需重启**。
- 搜索：输入 material → 只剩媒体分组+功能卡；清空恢复。
- 配置还原：style=material、feature=1；`svc power stayon` 已关闭。
- 产物：module/build/BetterOriginOS.apk（125KB）。

### 遗留

- 真实预览需 SystemUI 侧渲染过一次才可用（进详情页触发即可，无需播放音乐——离屏渲染用合成模板；抓屏更新才需要真实在屏卡片）。
- catFile 的诊断日志（Log.d catFile/media store）下次清理。
- 待生效计数仍为会话级。

## 2026-10-25 21:05 · Bug①修复：照片选择器显示全部自建相册（新 Feature #2）

### 背景（用户提供的帖子）

iQOO Z8 / 橘子5 用户反馈三大 bug：①照片选择器（媒体选择器）只显示 3 个默认相册，自建相册不可见（已授权全部照片视频）；②通知中心媒体播放器无进度条（本模块已解决）；③（第三条未处理）。用户问①能否用本模块解决。

### 定位（本机同版本复现 + jadx 反编译 MediaProvider 模块）

- 选择器 = `com.android.providers.media.module` 的 `PhotoPickerActivity`（/apex 模块，versionName 15.0.1.0）。
- 根因铁证：`photopicker/data/ExternalDbFacade` 里 `LOCAL_ALBUM_IDS = {"Camera","Screenshots","Downloads"}`——**相册枚举（queryAlbums 循环）与相册内容查询（appendWhere 三类别匹配）都硬编码白名单**。橘子6 换了新版枚举全量相册的模块，所以只有橘子5 卡中间。
- 数据链：Albums tab → PickerDataLayerV2/V1 → `content://<photopicker authority>/album` → PhotoPickerProvider.onQueryAlbums → ExternalDbFacade.queryAlbums。

### 修复（Feature #2：PickerAlbumsFeature，scope = com.android.providers.media.module）

1. hook `ExternalDbFacade.queryAlbums` before：从 picker 库（必须走 `runWithoutTransaction`，直接 getReadableDatabase 会被库自检拒绝）枚举全部 `relative_path`（GROUP BY），把末段名写入静态 `LOCAL_ALBUM_IDS`——**原方法的循环/计数/封面逻辑零复刻直接为全量相册工作**；同时维护 末段名→完整路径 映射。
2. hook `appendWhere`（静态，枚举与内容查询共用）：非白名单相册名 → `relative_path = '<完整路径>'`（DatabaseUtils 转义）+ mimeTypes 拼 IN；**setResult 必须给空 List**（调用方 addAll，给 null 会 NPE）。
3. 需重启 `com.android.providers.media.module` 进程生效；主页新增「媒体存储模块」应用卡（架构自动出现）。

### 验证（21:01 全过）

- push 自建相册 MyAlbum（2 图）+ scan_volume 扫描 → picker 相册 tab 显示 MyAlbum(2) 与 BetterOriginOS(2，预览图相册)；
- 点进 MyAlbum → 内容正确显示 2 张图；删除测试相册后重新枚举即消失（动态）。
- 日志证据：`installed feature picker_albums`、`refresh: rows=2 albums=[...,MyAlbum,...]`、`queryAlbums() loaded 2 items`。
- 配置还原：style=material、feature 全开。

### 遗留 / 注意

- Mainline 模块升级（橘子6 或 Google 推新 MediaProvider）可能改动类名/逻辑 → hook 已做 try/catch 容错，失败时回退系统原生行为（白名单）。
- 相册同名（不同目录）会合并展示（v1 按 relative_path 末段名去重）；相册 id=末段名，内容查询用完整路径精确匹配（映射表在进程内存）。
- 第三条 bug 未评估。

## 2026-10-25 21:25 · 修复：未播放音乐时预览退化为手绘版

- 根因：预览请求的 ContentObserver 在 `restyle()`（媒体通知构建）里才注册——SystemUI 重启后若从未播放音乐，监听器不存在，App 的渲染请求无人应答，5s 超时后显示手绘兜底。
- 修复：`restyle()` 入口即注册监听器（任意通知绑定触发，SystemUI 启动数秒内必然发生），与媒体卡功能开关解耦。
- 验证：重启 SystemUI → 未播放任何音乐 → 详情页预览直接显示 Hook 合成模板的真实渲染（示例歌曲 + 灰色封面占位 + 0:00 进度），观感与真实卡片一致。
- 顺带：模块信息页删除"统一引擎"描述，改为「作者 · MengWanYu」；推荐作用域声明（xposedscope 数组）补上 com.android.providers.media.module。

## 2026-10-25 22:30 · Bug③修复实现（验证挂起）：文件管理重命名 56 字符限制

### 问题（酷友帖子第③条）

文件管理（com.android.filemanager，BBKFileManager.apk）长文件名显示正常，但重命名时超过限制的字符被默认删除。

### 定位（jadx 反编译 BBKFileManager.apk）

- `view/dialog/RenameDialogFragment`：`v2()` 预填充时设 `t.f11819f = 56`；`x2()`（确定）硬编码 `length() <= 56` 拒绝。
- `view/dialog/DialogNameEntry`：`L(String)` 在 TextWatcher 链路按 `t.f11819f` 逐字删除（toast errorFileNameTooLong 也来自这里）。
- 对话框布局（dialog_name_entry.xml）**没有** android:maxLength；EditText 是 vivo 自定义控件 `com.originui.widget.edittext.VEditText`（无自定义过滤逻辑）。

### 修复（Feature #3：FileManagerRenameFeature，scope = com.android.filemanager，已注册 + scope 已加）

1. hook VEditText 构造 after：把过滤器替换为 LengthFilter(85)——85 = ext4 文件名 255 字节 ÷ 3 字节/CJK，任意中英混合不超限。
2. hook DialogNameEntry.L before：把静态 `t.f11819f` 提到 85（截断逻辑只在 >85 时删字）。
3. hook RenameDialogFragment.x2 before：长度 ∈ (56,85] 时跳过原硬编码拒绝，直接调用原私有 N2(file, dest, name) 走正常重命名（重名等错误由原 worker 的 retCode 流程处理）；ShareFile（SMB）不拦截。

### 阶段状态：实现完成，端到端验证挂起

- 已验证：hook 装载成功（`filemanager rename patch installed`，主进程 + :widgetProvider 进程）。
- 未验证：重命名对话框的端到端行为（预填完整长名 → 确定保存）。UI 自动化未走通到对话框（更多菜单 → 重命名 的 tap 未弹出，疑似 dump/tap 时序问题）。
- 用户此前在 22:15 版（缺 VEditText 构造 hook）手动测试：56 处卡住并弹 toast——与该版本能力一致，非新证据。
- 诊断日志保留在 FileManagerRenameFeature（`L called` / `VEditText constructed` / rename 相关），下轮验证时从 logcat 直接判断 hook 触发情况。
- 下一步：手动或自动化走通一次重命名对话框 → logcat 确认 `L called` 与 `VEditText constructed` 触发 → 预填完整长名 → 确定保存 → ls/content query 验证磁盘文件名。若 VEditText 构造 hook 未触发，需改挂点（如 DialogNameEntry 的 inflate 方法 after）。

## 2026-10-26 12:00 · OriginUI 阶段收尾 + 新任务立案：通知条 iOS 化

### 本轮完成的收尾工作
- 交接文档：`out/HANDOFF-1026-OriginUI.md`（现状/构建/skill 路径/验证命令/限制/起步清单）。
- 新任务书：`out/TASK-NOTIFY-ROW-1026.md`（需求 + 现状逆向 + iOS 规范草案 + 方案 + 拆分 + 风险 + DoD）。
- 现状证据：`work/os5-ref/shot-notify-shade-os5-1026.png`（OriginOS 5 下拉通知中心原版）。

### 新任务的关键逆向结论（已确认，写进任务书）
- 通知条小图标来源：framework-res `vigour_notification_template_header_vos.xml` 中
  `CachingIconView @android:id/icon` 固定 **18dp×18dp**、padding 2dp、圆底、marginEnd 3dp；
  header 行高 **46dp**，内容左右边距各 **16dp**（`vigour_notification_content_margin_*_vos`）。
  → 这就是"图标只有左上角一小块、空间布局不合理"的根因。
- 内容模板：`vigour_notification_template_material_base_vos.xml`（header + line1 + text + progress + smart_reply + right_icon）。
- 宿主：`status_bar_notification_row.xml` → `ExpandableNotificationRow` + `NotificationContentView`
  （本模块媒体卡已 hook 过 `setContractedChild/setExpandedChild`，经验可复用）。
- 方案选型：**B = 重建收起态内容视图**（保留原 child 供展开态、点击走原 row），
  媒体/自定义 View 通知跳过，分组先做组头、子行阶段 2。
- 图标/文字目标：38dp 图标 + app名/时间 12sp + 标题 15sp semibold 2 行 + 正文 14sp 4 行，
  卡片 20dp 圆角、16dp 边距、8dp 间距（§3 待用户确认项已列）。

### 状态
- 通知条优化**尚未改任何代码**；设备配置已还原（media card material / feature=1 / animator scale 1.0）。
- 下一步：新对话读 `HANDOFF-1026-OriginUI.md` + `TASK-NOTIFY-ROW-1026.md`，从任务书 §5 第 1 步（基线采样）开始。

### Skill 加载指引（补充，新对话开工前先看）

本项目有两个项目级 skill，**开工前先加载对应 skill**，再读代码/动手：

| Skill | 文件 | 何时加载 | 加载方式 |
|---|---|---|---|
| `betteroriginos-dev` | `.zcode/skills/betteroriginos-dev/SKILL.md` | 改本模块任何 Feature/Hook/设置 App UI/构建安装/真机验证/排障 | 输入 `/betteroriginos-dev`，或说"按 betteroriginos-dev skill 做"；模型侧用 Skill 工具 |
| `originui-integration` | `.zcode/skills/originui-integration/SKILL.md` | 用 OriginUI 复刻库接入 App、做 OriginOS 风格 UI/原生模糊、控件复刻 | 输入 `/originui-integration`，或直接 Read 该文件 |

- **注意**：`originui-integration` 是 2026-10-26 新建的。若当前会话的技能列表里没有它（会话在创建前启动），
  新开对话即可自动发现；临时要用就直接 `Read .zcode/skills/originui-integration/SKILL.md`，内容自足。
- **推荐读序**：`out/HANDOFF-1026-OriginUI.md` → `out/TASK-NOTIFY-ROW-1026.md` → 对应 skill →
  `out/DEVLOG.md` 尾部 → 目标代码。
- 通知条任务两侧都要用：先 `betteroriginos-dev`（Hook/构建/验证规范），再 `originui-integration`
  （iOS 行视图若放进复刻库，按库的接入/验收流程走）。

## 2026-10-26 12:55 · 通知条 iOS 化：首版真机 + 崩溃修复 + 组内大图标

### 实现（模块 Feature #0：notify_row，default off）
- 新增 `feature/notify/NotifyRowFeature`：hook `NotificationContentView.setContractedChild`，
  原地改造模板（不替换 child、不动绑定链）；`NotifyRowSpec` 统一 dp/sp/颜色；`NotifyRowPreview` 应用内离屏预览。
- 单条：header 压到 34dp、内容列缩进到 66dp、38dp 圆角方图标垂直居中、标题 15sp medium ≤2 行、正文 14sp ≤4 行；
  时间暂留 app 名旁（移出 `R.id.time` 会导致分组 NPE）。
- 组内子行：不再跳过；同样加 38dp 图标 + 统一字号；组默认自动展开一次，让同应用多条逐条显示大图标。
- 小图标：header `CachingIconView`/`left_icon` + 时间行 compound drawable 全部隐藏；OS5 分组逻辑会在绑定后回写，
  用 header 的 `OnPreDrawListener` 每帧压住（只遍历 header）。

### 崩溃与修复（关键）
- 首版把 `R.id.time` 移到 header → `NotificationGroupingUtil.sanitizeTopLine` 对 time 无判空，
  分组子行 NPE，SystemUI 循环重启。修复：时间留在原处 + restyle 延后一帧 + 跳过 summary/子行判断。
- 自动展开：直接改 `GroupExpansionManager` 只更新状态、视图不刷新；正确路径是摘要行
  `setUserExpanded(true,true)` + `setChildrenExpanded(true,false)`，并按 row 实例去重（锁屏/下拉是两套 row）。

### 验证
- 崩溃缓冲最后一条仍是 12:31:47（修复前）；之后多次重启 pid 稳定。
- 证据：`work/os5-ref/notify-ios-v11.png`（锁屏：Shell 组展开，每条大图标居中、时间行无小图标）、
  `notify-ios-v10-shade.png`（下拉：组仍收起，待解锁复验 setChildrenExpanded 后状态）。
- 约束：下拉通知栏的自动展开需用户解锁后复验；组头（"Shell" 行）仍保留 OS5 小图标。

### 待办
- 下拉栏组展开复验；组头小图标是否放大/隐藏待用户定；时间右对齐的安全方案；
  app 内预览入口验证；深色/大字号/RTL/点击长按滑动删除回归；清理测试通知。

## 2026-10-26 13:10 · 通知条改版：拆分组 + 去展开箭头

### 用户反馈
- 点箭头展开后大图标消失、掉回 OS5 展开态；同应用多条仍连成一张应用类别卡；
  想要 iOS 的多条独立堆叠卡片，而不是组内子行。

### 实现
- **拆分组**：hook `StatusBarNotification.isGroup()` 在开关开启时恒返回 false；
  hook `ShadeListBuilder.filterNotifs` 后仅移除「同组还有子项」的 group summary
  （summary-only 通知保留）。效果：同应用多条各自成为 StackScrollLayout 的独立卡片，
  组头/应用类别卡消失；媒体/自定义通知跳过逻辑不变。
- **展开态**：补 hook `setExpandedChild`，并把 `big` 模板加入支持列表（原来漏了，
  导致展开 child 静默跳过）。实测 `big` 展开模板的 header/内容排版与收起态差异大，
  完全一致需要重做展开模板；本轮按 iOS 交互去掉 OS5 展开箭头，
  整卡/头部点击改为打开通知（长按仍为原生菜单），避免掉回 OS5 展开态。
- 小图标/箭头隐藏范围从 header 扩到整卡（`expand_button`/`expand_button_icon` 一并隐藏）。

### 验证
- SystemUI pid 稳定，崩溃缓冲仍为 12:31:47（修复前）；锁屏截图
  `work/os5-ref/notify-ios-v15-lock.png`：电池、Shell 两条均为独立卡片、
  大图标居中、无组头、无小图标、无箭头。
- 下拉栏待用户解锁后复验（adb 无法安全解锁）。

### 遗留
- 时间仍左对齐且 `/` 分隔未隐藏；自定义 iOS 展开（动作/快捷回复）需另做；
  拆分组对 ranking/heads-up/bubbles 的回归；应用内预览入口；清理 Shell 测试通知。

## 2026-10-26 14:05 · 代码级校验闭环 + iOS 堆叠参数 + 手势回归修复

### 校验器（不靠截图）
- 新增 `work/verify_notify.py`：解析 SystemUI 内 `notify_row.verify` 机读日志，按 density/fontScale
  逐项断言图标/列/时间/字号/颜色/重叠/胶囊高度/堆叠边距/分组状态。
- 证据：`work/os5-ref/notify-verify-20261026.log` + `.txt`（8 行全部 PASS）。
- 通过状态：日间 LTR 1.0、夜间、1.3 字号、真实 RTL（SystemUI app locale=ar，重启后 `rtl=1`）。
- 单条关键值：icon 114px(38dp)/x=48(16dp)/centerDelta=0；header 102(34dp)；app/title/body
  x=198(66dp)；time 右端 48(16dp)、父级 NotificationHeaderView；title/body 右端 48(16dp)；
  1.3x 下 app 47/title 58/body 55；`rowH=contractedH`、`small=0`、`expand=0`、
  `groupChild=0`、`stackChildContainers=0`、`entryParentRoot=1`。
- 堆叠参数：`mSidePaddings=48(16dp)`、`mPaddingBetweenElements=24(8dp)`、`mCornerRadius=60(20dp)`，
  实测卡片左右 48、行距 24；Feature 新增选项 `stack_metrics`（iOS 默认 / OS5 原生）。

### 修复
- **卡片空高**：hook `NotificationContentView.onMeasure`，当 `mVisibleType=0` 时把测得高度收敛到
  contracted 高度；`rowH` 从 394→268，去掉底部 42dp 空白。
- **展开回退**：强制 visibleType=0（heads-up 除外），隐藏展开箭头/header touch，整行点击走 row；
  `setUserExpanded(true)` 对已改造行拦截，避免掉回 OS5 展开布局。
- **拆组彻底化**：`Application.onCreate` 早期置 ungroup（避免第一批 pipeline 先建 GroupEntry）；
  `isGroup=false`；`filterNotifs` 移除有子项 summary；GroupEntry 用 `getRawChildren` 扁平化并
  `setParent(ROOT_ENTRY)`。同应用每条独立成卡，`groupEntries=0`。
- **滑动回归**：A/B 证明 OS5 原生可滑；原 pre-draw 守卫用 `setVisibility(GONE)` 与 OS5 每帧抢
  visibility，导致 requestLayout 吞手势。改为只改 alpha/clickable（不改 visibility）后
  滑动删除恢复，Shell 测试通知已全部滑掉；点击已验证可打开应用。

### 仍待
- 长按菜单未在 shell 测试通知上触发（该通道无 guts 可能性大），需用真实 app 通知验证。
- OS5 finalize filters 会过滤低优先级/系统 ongoing 通知（`notify_row.filter` 可见 android 键）；
  不强行绕过 DND/keyguard。
- heads-up / media / custom 未 iOS 化；卡片背景仍 OS5 材质；展开态按选择禁用；
  pre-draw 守卫可进一步缓存优化；`docs/04-notification-row.md` / TASK DoD 待补。

## 2026-10-26 14:22 · messaging 覆盖行 + 媒体豁免 + overlay 代码级校验

### messaging / conversation
- 原模板 `tag=messaging root=MessagingLayout` 不满足原地改造条件（非 FrameLayout），会落回 OS5。
  新增 `IosNotificationRow`（View 版）作为 **overlay**：保留原模板绑定，原 root alpha=0，
  在 `NotificationContentView` 上盖一层 38dp 图标 + app名/时间 + 会话标题 + 最新消息。
- hook `NotificationMessagingTemplateViewWrapper / CompactMessaging / ConversationTemplate.onContentUpdated`
  后重建 overlay；extras 取 `android.conversationTitle`、`android.messages`（Person/sender/text）、
  selfDisplayName；时间每分钟 ticker 刷新；夜间/字号变化由 pre-draw 守卫动态 applyTheme/applyTextScale。
- overlay 的内容视图高度由 overlay 测量结果决定，并同步原 contracted root 的高度；卡片背景/边距沿用 stack。
- overlay 行也纳入 `work/verify_notify.py`：`notify_row.verify.overlay` 断言 icon 38/16、列 66、
  时间/标题/正文右端 16、字号/颜色、卡片 16/8、`overlayH=rowH=contentH`、`origAlpha=0`、`small/expand=0`。

### 媒体豁免
- 拆组逻辑新增 `isMediaSbn` 白名单（`CATEGORY_TRANSPORT` / `EXTRA_MEDIA_SESSION` / bbkmusic/musicwidgetmix）：
  `isGroup()` 保持原样、summary 不删、GroupEntry 不扁平化。媒体通知继续由 MediaCardFeature 接管。
- 真机日志恢复 `media card ... restyling style=material com.android.internal.widget.MediaNotificationView ...`。

### 代码级证据
- `work/os5-ref/notify-verify-overlay.txt`：1 contracted + 1 overlay，全部 PASS；
  overlay 指标：rootW=984/overlayH=226/rowH=226、icon 114@x48、app/title/body x198、
  time/title/body end 48、card 48/24、overlay 夜间色 #fff/#b3。
- 之前 `work/os5-ref/notify-verify-20261026.txt`：8 行普通行 PASS；夜间/1.3/RTL + overlay 均 PASS。
- 聊天 overlay 滑动删除验证：`t_msg3` 滑掉；媒体播放中卡片恢复。

### 仍待
- 真实 Conversation/Person 通知（非 Shell messaging）、heads-up、custom view 未覆盖；
  卡片背景仍 OS5 材质；展开/动作按选择禁用；OS5 finalize filters 不强行绕过；
  adb 长按不可靠（功能开/关均无菜单，需真机手指）；pre-draw 守卫性能优化；docs/04 待写。

## 2026-10-26 14:32 · 模板补测（inbox/bigPicture）+ 设置页预览 + heads-up 结论

### 模板覆盖
- inbox：`cmd -S inbox` 真机行 `restyled ... style=inbox single`；verifier 3 行全部 PASS
  （图标/列/时间/字号/卡片仍为 iOS 规格）。
- bigPicture：`cmd -S bigpicture --picture @android:drawable/...`（file:// PNG 被 `cmd` 判
  not a bitmap，是测试工具限制）→ `restyled ... style=bigPicture single`；随后被 keyguard
  finalize filter 挡住（`notify_row.filter key=...t_bigpic3`），未在解锁普通下拉里复验。
- 标准模板矩阵现状：base/big/bigText/inbox/bigPicture 原地改造；messaging overlay；
  conversation wrapper 已挂 overlay hook 但缺真实 Person/Conversation 样本；custom view 不改造。

### 设置页预览
- 预览改为三张差异化示例卡：设置/软件更新、音乐/每日推荐、Shell/小明；间距 8dp、圆角 20dp；
  功能卡含「卡片堆叠参数 iOS/OS5」与开关。真机打开正常，无崩溃。
- 预览是内存缓存（同宽度/主题一次渲染）；切字号/夜间需重开页面刷新。

### heads-up 结论
- launcher 上真实捕获到「交管12123」OS5 heads-up：小图标 + 单行 + 两个动作按钮，
  **未 iOS 化**。heads-up 属悬浮 banner，不属于通知堆叠主体；本轮未改，列为已知限制。

### 设备/测试通知
- 测试通知 `t_heads / t_inbox2 / t_bigpic3` 仍在 NotificationRecord 列表；解锁后
  `uiautomator` 未 dump 到行（shade 停在 QS 视图），未能逐条滑掉，用户可在通知栏自行清理。
- 媒体已暂停；功能仍开启；debug/verify=0；语言/字号/日间已还原；屏幕已熄。

## 2026-10-26 14:39 · heads-up child 纳入改造（live 待复验）+ 调试通知通道

- 新增 hook `NotificationContentView.setHeadsUpChild` → 与收起态同一套 `restyle`（mode=2）：
  header 压缩、小图标/箭头隐藏、38dp 大图标、66dp 列；`headsup=1` 写入 verifier 日志，
  `verify_notify.py` 新增 headsup 分支断言（icon/列/时间/字号/颜色/无小图标/无箭头；卡片/stack 检查跳过）。
- 新增调试通知通道（仅测试用）：`NotifyTest` + `NotifyTestReceiver`（导出，action
  `com.betteroriginos.DEBUG_NOTIFY`，高 importance channel），Manifest 增加 POST_NOTIFICATIONS；
  `adb shell am broadcast ... --es cmd post|cancel`。用于 heads-up/长按/点击回归。
- 限制：本轮多次尝试未在可抓取窗口捕获 heads-up（通知栏展开时被抑制；调试通道未弹）。
  launcher 上此前捕获的「交管12123」OS5 heads-up 说明该 ROM heads-up 可用，但属 hybrid/custom 模板；
  标准 heads-up 的新改造需等真实通知复现后复验。
- 设备已恢复：stayon=false、debug/verify=0、媒体暂停、屏幕熄灭。调试 Receiver/权限保留待后续测试，
  完成目标后移除。

## 2026-10-26 15:31 · 拆组补全 + 聊天 overlay 复验 + 验证器静止门槛（最终验收）

### 关键发现
- 14:38 那次 SystemUI 启动没有注入模块（无 `notify row feature installed`），期间看到的是 OS5 原样；
  `su -c killall com.android.systemui` 后注入正常。其余验证均以注入确认后的进程为准。
- “LSPosed/系统通知消失”不是拆组导致：`VivoKeyguardCoordinator.vivoNotifFilter` 在锁屏态按可见性
  过滤（SECRET/低重要度），解锁通知中心正常显示——原生隐私行为，不绕过。
- 空白的 “Shell” 卡片来自保留 ranker_group 组摘要；改为所有非媒体通知统一
  `isGroup=false` + `getGroupKey=null` + 移除全部非媒体摘要后，空摘要卡消失，系统通知与分组
  子项都独立成卡。

### 代码变更（module/src/com/betteroriginos/feature/notify）
- ungroup：`shouldUngroup` 只豁免媒体；新增 `StatusBarNotification.getGroupKey` 钩子；
  `filterGroupSummaries` 移除全部非媒体摘要；flatten 仅保留媒体组；滤镜日志打印 `getName()/class`。
- overlay 交互：整行点击 → `row.performClick()`；长按 → 新 `longClickRow()` 调原生
  `doLongClickCallback(x,y)`（`performLongClick` 在本 ROM 无绑定）。
- 性能：pre-draw 守卫改为缓存目标数组（`GUARD_TARGETS`/`COLOR_TARGETS`），每帧只回放
  alpha/clickable/enabled；overlay `applyTextScale` 按 fontScale 去重。
- 验证器健壮性：`scheduleVerify`/overlay 验证加静止门槛（`scaleX/Y==1`，translation 属 NSS
  正常定位不判定）；overlay 验证改为首次真正可见即触发（`OVERLAY_VERIFY_PENDING`），重试 8 次。
- 调试通道：`NotifyTest` 增加 MessagingStyle(4243) 与同 groupKey 组(4244/4245/4246)；
  Receiver 清单声明 `android:permission=android.permission.DUMP`（仅 shell/root/system 可触发）；
  MainActivity 支持 postmsg/postgroup/postall。注意：vivo 冻结机制会延后后台广播，测试走 Activity 通道。

### 代码级证据（最终构建）
- `work/os5-ref/notify-verify-20261026-final.log`：25 contracted + 6 overlay 行，
  `python work/verify_notify.py` → **PASS**（icon 38dp@16dp、列 66dp、右侧 16dp、header 34dp、
  卡片 16/8/20dp、无小图标/箭头、overlayH=rowH=contentH、origAlpha=0）。
- 行为：同 groupKey A/B 独立成卡、无摘要卡；LSPosed 系统通知解锁态正常显示且被 iOS 化；
  聊天行点文本 → 打开应用；**右滑**删除成功（OS5 方向为右滑）。

### 仍待/限制
- heads-up：ROM 用 vivo 自绘胶囊（交管12123），标准 `setHeadsUpChild` 真机不可复现；
  hook 已挂但维持“待复验”，不作宣称。
- 长按菜单：adb 长按本 ROM 不触发（开关均无菜单），需真机手指复验；代码已走原生长按入口。
- custom view / 真实 conversation 样本未覆盖；媒体与系统隐私过滤保持原生；卡片仍是 OS5 材质。

### 设备状态
- 本模块测试通知已 cancel；Shell 三条 `cmd notification snooze --for 43200000`（至 2026-10-27 03:31）。
- verify/debug=0，stayon=false，屏幕已熄；模块保持开启（notify_row=1，stack_metrics=ios）。

## 2026-10-26 15:44 · 文本保真微调（最终）

- 应用名：`sans-serif-medium`（iOS 应用名为半粗体灰字）；正文色改为 primary（iOS 正文是 label 色，
  不是灰字）；标题维持 primary + medium。普通行、overlay、设置页预览三处同步。
- `work/verify_notify.py` 断言更新：`bodyColor == primary`（日 #de000000 / 夜 #ffffffff）。
- 复验：`work/os5-ref/notify-verify-20261026-final.log`（覆盖写入）→ 14 contracted + 5 overlay 行 PASS。
- 设备交还：verify/debug=0；测试通知已清（Shell 三条仍在 snooze 期）。设备当时正被用户使用，
  未做进一步操作。

## 2026-10-26 16:07 · 配置变化参数回退修复 + overlay 复用残留修复 + 多配置复验

### 修掉两个真实 bug（均为代码级复现与验证）
1. **配置变化擦掉堆叠参数**：字号 1.3 / 夜间 / 语言切换会走 vivo `NotificationStackScrollLayout.
   reinitView()`，重读 OS5 资源并覆盖 `mSidePaddings/mMinimumPaddings/mPaddingBetweenElements/
   mCornerRadius`；此前只 hook 构造器与 `updateSidePadding`，导致卡片退回 OS5（实测 60/60/21/54）。
   修复：追加 hook `reinitView` / `updateCornerRadius` / `onSystemFilletChanged` → 复验字号 1.3 下
   卡片回到 48/48/24（左/右/行距）且 17 行 PASS。
2. **行复用残留聊天 overlay**：同一 key 从 MessagingStyle 换成普通样式时，contentView 复用了旧行，
   旧 overlay 仍挂在 contentView 上（会盖住新通知文字）。新增 `dropOverlay()`（非聊天再绑定、
   功能关闭、sbn 缺失时调用）。复现实验：同 key 先聊天后普通 →
   `overlay dropped (row rebound)` → 重新 `restyled base single` → `reverify overlays=0`。

### 多配置复验（最终构建）
- 日间/LTR/1.0：`notify-verify-20261026-final.log` → 14 普通 + 4 overlay PASS。
- 夜间：`notify-verify-20261026-night.log` → 18 行 `night=1`、`bodyColor=0xffffffff` PASS。
- 字号 1.3：`notify-verify-20261026-font13.log` → 17 行 `fontScale=1.3` PASS（含上述参数修复验证）。
- RTL（SystemUI locale=ar）：`notify-verify-20261026-rtl.log` → 19 行 `rtl=1` PASS。
- 全程 logcat 无 FATAL/crash；语言/字号/日间已还原，verify=0，测试通知已 cancel。

### 仍待
- heads-up（ROM 自绘胶囊）、真实 conversation 样本、custom view、iOS 长按动作菜单、卡片材质
  （按先前决定保留 OS5）需后续轮次或真机手指复验。

## 2026-10-26 20:45 · OS6 堆叠取证与移植（可交接状态）

- 用户提供 X200 Pro（V2405A / Android 16 / OS6）用于取证，已拉取 SystemUI(56.6MB)+SystemUIPlugin(130.5MB)
  与折叠/展开两份 uiautomator 视图树，存 `G:\os6-ref`；结论固化在
  `vivo-OriginUI/docs/05-os6-notification-stack.md`（露边在下方、每层 14dp/内缩 13dp、徽标压图标右上角、
  收起态无 app 名/时间行；OS6 的 StackDrawingOrder/VivoStackScrollAlgorithm/Magnetic/A16 聚合在 OS5 均不存在）。
- Z8 侧新增 stack 模式（`group_mode=ios_stack|separate`）：保留原生 GroupEntry，组摘要行自绘堆叠卡
  （正面=最新一条、下方露边、图标右上角条数徽标、卡面采样/兜底 `0xF2F6F6F6`），`verify.stack` 代码级日志
  与脚本分支；实测几何已达标（overlayH=210、peek=42、cardBottom=168、badge gap=9/r=27）。
- **未完成/阻塞**：① 保留 GroupEntry 触发 vivo 图标控制器 NPE（`updateVivoIconsForLayout` 与基类
  `updateIconsForLayout`），第一层守卫已生效、基类守卫与 setter 层展开拦截已写入**但未构建验证**；
  ② 组被系统自动展开、堆叠卡未接点击展开；③ 夜间卡色不会重采样；④ 展开态仍为 OS5 组卡。
- 交接文档：`out/HANDOFF-1026-OS6-Stack.md`（设备/产物/代码位置/阻塞/复现命令/下一步）。
- 设备：Z8 当前安装的是会崩的 stack 构建且 `group_mode=ios_stack`；建议交接后先切 `separate` 稳定。

## 2026-10-26 22:27 · 堆叠移植推进 + 0.1 隐藏决定 + 图标换成 OriginOS 官方 logo

### 堆叠本轮真机推进（每步都有日志/截图证据）
- **根因 1：堆叠完全不出现**。`applyMessagingOverlay()` 里无条件 `sUngroupEnabled = true`，
  聊天行一绑定就把分组拆平 → 流水线 `summaries=0 groupEntries=0`，组摘要根本没有挂载点。
  改为仅非堆叠模式才置位，`notify_row: stack pkg=com.betteroriginos count=4 bars=2 peekPx=84` 立刻出现。
- **根因 2：崩溃**。装机版只包了 vivo 子实现 `updateVivoIconsForLayout`，实际崩在基类
  `LegacyNotificationIconAreaControllerImpl.updateIconsForLayout:585`（调用路径不经过 vivo 层）。
  基类守卫装机后 crash buffer 归 0。
- **行高失控**：补 `setChildrenExpanded` 拦截 + 折叠时子容器退出测量，`rowH` 从 647/1397 收到
  210/252，正好等于卡片高度。
- **长文字被裁**：堆叠卡按 OS6 收起态收成单行标题 + 单行正文，`iconCenterDelta` 由 −26 变 0。
- 补齐：点击展开/收起（`toggleStack` + 卡片/图标 click、长按转系统菜单）、夜间切换重采样卡面色、
  行回收时清堆叠标记并恢复被压透明的原生件（`dropStackOverlay(contentView, rowView)`）。

### 踩坑记录（下次别再试）
- `ExpandableNotificationRow.setGroupExpanded` **在 OS5 不存在**（`NoSuchMethodError`），别照着 AOSP 新版写。
- 在 pipeline 抹 `getGroupKey` 去"组中组"会让 `HeadsUpCoordinator.onBeforeFinalizeFilter` NPE 崩溃循环，已回退。
- 幽灵摘要行（系统置 INVISIBLE 的重复/嵌套组摘要）占高：置 GONE 无效（GONE 跳过 measure，旧高度留在排版里）、
  hook `onMeasure` 归零也无效（根本不被调用）。当前用「保持 INVISIBLE + LayoutParams 高度压 0」缓解，
  **卡之间约 530px 的空洞仍未彻底解决** → 因此 0.1 先隐藏堆叠。

### 0.1 决定
- `STACK_BUILD_ENABLED = false`：分组方式选项不再出现、`isStackMode()` 恒 false，只保留已验证的分离模式。
- 发布稿：`out/RELEASE-0.1.md`（4 个功能 + 已知限制 + 安装步骤 + 质量口径）。

### 启动图标
- 旧图标（橙底音符卡）只表达媒体卡，换成系统里的官方 OriginOS 5 logo。
- 素材来源：`work/os5-ref/Settings.apk` → `drawable/about_phone_origin_os_rom15` = `res/ZsR.webp`（936×618）。
- 处理：按高度方形裁切 → 自身放大裁切 + 高斯模糊 + 压暗作底 → 主体缩到 **64%** 居中落进自适应安全区。
  64% 是实测上限，74% 会被桌面 squircle 遮罩切掉首尾字母。
- 落地：`res/drawable-nodpi/ic_launcher_bg.png`(324) + `ic_launcher_fg.png`(全透明) + 5 档 mipmap 方/圆 +
  `app_logo.png`(120，关于页 26dp 用)；`mipmap-anydpi-v26/*.xml` 改指新资源。
- 旧矢量 `drawable/ic_launcher_background.xml`、`ic_launcher_foreground.xml` 与同名 color 已无引用，未删除（本仓库无 VCS）。

## 2026-10-26 23:28 · OS6 堆叠打通（展开/收起循环无回归）

### 空洞真因（推翻前面三个假设）
`StackScrollAlgorithm.getMaxAllowedChildHeight()` 用的是 `ExpandableNotificationRow.getIntrinsicHeight()`，
而摘要行的 `getIntrinsicHeightInternal()` 在 `mIsSummaryWithChildren` 时**直接返回子容器高度**（折叠也算）。
所以折叠卡下面必然留一整叠子行的高度。之前试过的三条路都是错的：
置 GONE（GONE 跳过 measure，旧高度照用）、hook `onMeasure` 归零（根本不被调用）、
`LayoutParams.height=0`（同上，排版不看它）。
**正解**：hook `getIntrinsicHeight()`，折叠期间返回堆叠卡自身高度（`STACK_CARDS: row -> 卡片`）。

### 展开状态：标记是唯一真源
- 拦截 `setUserExpanded` / `setChildrenExpanded` 的 `true`：堆叠行只放行"用户点卡片"那一次（`sExpanding`）。
- 读取侧 `isGroupExpanded()` / `isChildrenExpanded()` 一律回报我们的标记，避免系统在我们注册前展开后遗留状态。
- **收起**：OS5/vivo 的收起箭头走自己的动作链，实测从不调 `setUserExpanded(false)`；点击又被行自己的
  `NotifTouchInterceptor` 吞掉（给子 View 设 OnClickListener 收不到）。因此规则改为：
  **堆叠行收到第二次展开请求 = 用户再点一次 = 切换成收起**（args 改 false + 清标记 + 日志
  `stack collapse by row tap`）。点卡片展开、再点卡片/组头收起，布局精确回到基线。
- `autoExpandGroup()` 加护栏：`sStackMode` 或已在 `STACK_ROWS` 的行直接返回（首帧 Prefs 未就绪时
  `isStackMode` 异常返回 false，会走分离分支把组撑开）。
- 试过 `hasUserChangedGroupExpansion -> false`：**会触发原生自动展开**，已撤销。
- `setGroupExpanded` 在 OS5 不存在（`NoSuchMethodError`）。

### 冒烟结果（`work/os5-ref/notify-verify-20261026-stack-cycle.log`）
- 展开→收起循环后：`rowH=overlayH=contractedH`（210/252/321），列表 `ty` 逐行 = 上行高 + 24px，无空洞。
- 夜间循环：`cardColor=0xf21c1c1e night=1`、`badgeTotal=4`、`groupExpanded=0` 全对。
- 字号 1.3：卡片随行高增长（321→353），`iconCenterDelta=0`。
- `work/verify_notify.py`：17 普通行 / 14 覆盖行 / 56 堆叠行 / 87 行日志 **PASS**；crash buffer 0 FATAL。
- 校验器口径修正：堆叠卡 `titleMax/bodyMax=1`（OS6 收起态单行）；组内子行**必须**不显示 app 名行
  （`appVis!=0`），只有顶层行按 66dp 列校验；stack 日志补 `timeTS/timeColor/timeVis`；
  新增的列表排版诊断改名 `notify_row.stack.layout`（原来的 `verify.stack.siblings` 会被校验器当数据行）。
- **未覆盖**：RTL（这台机 `am compat set-locale-for-pkg` / `cmd locale set-app-locale` 都不可用，
  需要手动在设置里给 SystemUI 换 ar）。堆叠卡与已验证的普通行走同一套 `loc()`/rtl 断言。

### 补测（23:40 最终轮）
- **RTL**：本机 SystemUI 没有"语言"入口，`am compat set-locale-for-pkg` / `cmd locale set-app-locale` 均不支持
  → 用临时补丁把堆叠卡 `setLayoutDirection(RTL)` 后实测：`iconStart=48 titleStart=198 appStart=198
  badgeRightGap=9 timeEndGap=48 iconCenterDelta=0` 与 LTR 完全一致（start 侧语义正确），校验 PASS。
  **顺带修掉一个真实缺陷**：`nAppTimeGap` 用原始 x 计算，RTL 下得出 -738 → 改为按方向取端距
  （RTL 用 `appX - (timeX + timeW)`）。临时补丁已删除，最终构建 `rtl=0` 正常。
- **动态链**：force-stop 清空 → 余行自动闭合无残留；全新进程 postall 重建 → 指标与基线一致；
  滑动删除一叠 → 后续行自动补位（`ty` 连续），无空洞、无崩溃。
- **最终 LTR 循环**：`1021×1299`（展开）→ 再点 → `1021×252 / 1297 / 1642` 精确回到基线；
  `verify_notify.py` PASS，crash buffer 0 FATAL。证据：`work/os5-ref/notify-verify-20261026-stack-final.log`。

## 2026-10-27 06:15 · 用户报"展开再收起后通知变形"→ 查出两个真 bug

### 复现与定位
- 先按真实手势复现：点卡片展开 → 点原生箭头收起。这条路径**是好的**（`1021×1299` → 收起回 `1021×252`）。
- 屏幕截图里那块"变形"其实是**几小时前取证留下的 `com.android.shell` 旧样本组**
  （`t_bigpic3 / t_inbox2 / t_heads`）：该组我们没接上卡，系统按"折叠组只显示前 N 条"裁剪，
  子行只剩时间、内容被裁掉，看起来像变形。滑掉该组后画面恢复正常，`cmd notification` 无 cancel，
  只能靠 UI 滑动清除。**这台机上的旧测试样本要定期清**。
- 顺手造了一个正常 3 条 shell 组：`count=3 bars=2 badgeTotal=3 rowH=252` 正常成卡 → 说明不是通病。

### 真 bug 1：组内条数变化后卡片不更新（徽标/正面通知停在旧值）
摘要行在"组新增通知"时**不会重新绑定**，卡片停在旧条数：校验器抓到 `count=5 badgeTotal=3`。
修法：`guardStack` 每帧比对子容器 View 数（免费信号）+ 500ms 节流比对真实 `childCountOf()`，
不一致就 `post(() -> restyle(...))` 重算（日志 `stack total 9->10, restyling`）。
实测连投 2 条后：`count=11 badgeTotal=11` 跟上。

### 真 bug 2：组被清空后自愈空转
组清空后 `childCountOf()==0` 与 `stackTotal()` 永远不等 → 每 500ms 无限 `restyling` 刷屏。
修法：`total <= 0` 时不再触发（卡片交给下次绑定回收）。

### 校验器自身两个坑（差点变成假绿）
- 新加的 `notify_row.verify.stack.siblings` 会被 `verify_notify.py` 当数据行解析 → 改名 `notify_row.stack.layout`。
- 加了 `gkey=` 字段后，脚本按"每组最后一次测量"去重；**第一次把去重赋值写在断言循环之后**，
  导致 0 个堆叠行被检查却输出 PASS。已挪到断言之前。教训：改校验器后必须确认它检查的行数没变成 0。
- 组内子行按设计不显示 app 名行，断言改为 `appVis != 0`（只有顶层行按 66dp 列校验）。

### 最终回归（`work/os5-ref/notify-verify-20261027-stack-grow.log`）
展开 → 收起循环后布局精确回到基线，`verify_notify.py` PASS（8 普通行 / 4 覆盖行 / 4 堆叠行 / 16 行日志），
crash buffer 0 FATAL。

## 2026-10-27 06:32 · 堆叠收起仍会退回原生（已重新隐藏，附精确诊断）

- 06:29 截图复现：收起后那组显示**原生组头 + 子行残影压在下一张卡上**。
- 现场数据：屏上那行的 `NotificationChildrenContainer` 实际可见，而我们的度量报
  `contVis=8(GONE) overlayVis=0 rowH=252` → **度量对象是旧的行实例**，屏幕上的新实例没有守卫。
- 结论：收起/展开过程中行会被重新绑定/换实例；`STACK_ROWS / STACK_OVERLAYS / STACK_GUARDS`
  都以 `contentView`/`rowView` 为键，一旦实例更换而 `setContractedChild` 没有再次触发，
  新实例就完全没人接管 → 原生内容直接露出。
- 我加的"瞬时状态不拆卡 + 延后重算"用的是**捕获的旧 `contentView/root`**，对换实例无效。
- ⚠️ 因此前面几轮"收起后布局精确回到基线"的结论**不成立**：那是旧实例的陈旧度量，属于假绿。
  以后判定必须同时看屏（截图）+ 现场 `contVis/overlayVis`，不能只信 verify 行。
- 已把 `STACK_BUILD_ENABLED` 改回 `false`（0.1 发布安全）：separate 模式回归 PASS
  （10 普通行 + 6 覆盖行），0 堆叠挂载，0 崩溃。证据 `work/os5-ref/release-separate.png`。
- 下一步正解（未做）：把守卫从"每个 contentView 一个 pre-draw"改成**行级单趟遍历**——
  维护一份堆叠行集合，在 NSNL/窗口的单一 pre-draw 里按当前 `rowOf()`/`currentContracted()`
  重新解析并施加折叠/展开表现与卡片重建，实例更换时自动跟上。

## 2026-10-27 06:56 · 堆叠展开/收起的完整因果链（根治方向确定）

### 观察机制先修好（这是之前反复"假绿"的根子）
- 新增**现场见证**：`NotificationStackScrollLayout.dispatchDraw` 上挂一个列表级单趟遍历（1s 节流），
  对屏上每一行输出 `id/pkg/sum/exp/child/ty/h/vis/card/cardH/cardVis/contV/contH/contN`
  （`notify_row.witness`）。逐行度量同时加 `rowId=<identityHashCode>`。
- 有了真值立刻暴露：之前几轮"收起后精确回到基线"读的是**捕获引用**的度量，行换实例后就是陈旧快照。
  教训：**度量必须带实例身份，且与屏上真值对账**，否则绿灯无意义。

### 展开/收起为什么一直修不好（四层状态）
1. `ExpandableNotificationRow.setUserExpanded(z)`（单参）→ 只写 `mUserExpanded`，
   **不写组展开状态**；只有 `setUserExpanded(z, true)`（fromPendingIntent）才写 manager（源码 2509-2514）。
2. 组展开状态真正的归属是 `GroupExpansionManager(entry)`（`isGroupExpanded()` 只是代理到它），
   收起箭头走 `GroupExpansionManagerImpl.setGroupExpanded/toggleGroupExpanded`，**绕过行的 setter**。
3. 排版步长来自 `getIntrinsicHeight()`，摘要行返回子容器高度 → 必须覆盖（已做）。
4. 行的实际高度由 AOSP 的高度动画决定；我们强制改子容器可见性会打断它，产生
   `exp=0 h=1299 contV=8 cardH=168` 这种**混合态**（就是用户看到的"变形/重叠"）。

### 结论：现有"借用 AOSP 组展开"的做法不可靠
每修一层就暴露下一层，且混合态无法从外部消除。根治方案（下一步）：
**展开态也自己画** —— 组在 AOSP 里永远保持折叠（拒绝一切展开），堆叠卡被点开时由我们的
overlay 直接渲染 N 条子行（数据我们全都有：sbn/标题/正文/图标/时间）。
好处：不再依赖 AOSP 的展开状态机与高度动画 → 没有原生泄漏、没有重叠、没有陈旧实例；
并且能顺便做到 OS6 的"展开态是彼此独立的子卡"，比现在借用的 OS5 组头更保真。

## 2026-10-27 07:25 · 本轮根治尝试的结果：观察机制已硬化，高度 bug 仍未解（记录下一步线索）

### 观察机制（目标里的"不严谨"部分）已根治
1. **现场见证** `notify_row.witness`：挂在 `NotificationStackScrollLayout.dispatchDraw`，
   每秒一次遍历屏上真实行，输出 `id/pkg/sum/exp/child/ty/h/vis/card/cardH/cardVis/contV/contH/contN`。
2. **度量带实例身份**：`notify_row.verify.stack` 加 `rowId=<identityHashCode>`，可与见证按 id 对账；
   组标识 `gkey=` 让校验器能按组去重（不再把自愈中间态当缺陷）。
3. **覆盖守卫生效**：`verify_notify.py` 现在会在"该有度量的时候没有"直接 FAIL
   （本轮 `root.setVisibility(GONE)` 把校验管道弄哑时，它立刻报了
   `FAIL: no notify_row.verify lines`）。这条守卫正是防"假绿"的关键。
4. 承认并纠正的两次假绿：① 去重赋值写在断言循环之后 → 0 行被检查却 PASS；
   ② 读捕获引用的陈旧度量 → "收起后回到基线"其实无效。

### 产品侧：展开/收起的高度问题仍未解（已排除一堆假设）
- 现场签名（收起后）：`h=1299 exp=0 contV=8 cardH=252 cardVis=0`
  —— 组状态、子容器、卡片三者都说"折叠"，只有**行的高度**停在展开值，于是卡下面 1047px 空白。
- 已试且无效：`adjustHeightForChildren()`、`notifyHeightChanged(false)`、
  `row.requestLayout()` + 父容器 `requestLayout()`、`getIntrinsicHeight()` 覆盖成卡片高度。
- 已确认的机制事实：`notifyHeightChanged` 只转发到 `super` + `requestSelectLayout`，
  行高实际由 `measureChildExactly` 一类路径决定；而 `ExpandableNotificationRow.onMeasure`
  **不在现有反编译片段里**（`work/systemui/out-src` 只有部分类）。
- **下一步线索（别重复试错）**：先把 `ExpandableNotificationRow.onMeasure` 完整反编译出来
  （jadx `work/systemui/dex` 或直接 hook 该方法的 before/after 打印 heightSpec 与
  setMeasuredDimension 的入参），确认它在 `mChildrenContainer` 为 GONE 时为什么仍量到 1299；
  大概率是它显式 measure 子容器并把其高度计入，而不是走 intrinsic。
- 期间试过的两条弯路（记录以免重蹈）：
  * 让卡片自绘展开列表 + 原生根视图 `GONE`：行高失去来源、且把校验管道弄哑，已回退。
  * 用 `hasUserChangedGroupExpansion=false` 强压折叠：反而触发原生自动展开。

### 当前落地状态
- `STACK_BUILD_ENABLED = false`：0.1 仍只带已验证的分离模式。
- 发布构建回归：10 普通行 + 3 覆盖行 PASS，堆叠挂载 0，crash buffer 0 FATAL。
- 诊断能力（见证 / rowId / gkey / 覆盖守卫）全部保留，下次直接用它验证高度修复。

## 2026-10-27 08:19 · 堆叠展开/收起根治：改成"卡片自绘展开"

### 真正的病根（三条，全部代码级定位）
1. **行高没有唯一真源**。之前行高来自 AOSP 的组展开 + 我们的一连串补偿（intrinsic 覆盖、子容器 GONE、
   大件 GONE、root 补 padding），任何一处滞后都会留下"行比卡高"的空洞。现在
   `IosNotificationRow#contentHeight(width)` 用 UNSPECIFIED 自量一次，`onMeasure` 钳制与
   `getIntrinsicHeight` 都读它 —— 行高 = 卡高，双向跟随。
   实测：折叠 268/268/268、展开 387/387/387（行高 / 卡高 / contentHeight 完全相等）。
2. **"展开"借用了 AOSP 的组展开**。`GroupExpansionManagerImpl` 跟随钩子会让系统程序性的
   `setGroupExpanded(entry,false)` 清掉我们的意图标记 → 展开再收起就掉回原生。现在堆叠行**一律拒绝**
   AOSP 展开（`setUserExpanded`/`setChildrenExpanded` 入参强制 false），展开列表由卡片自己渲染
   （`setStackChildren` + `setStackExpanded`），子行数据取
   `NotificationChildrenContainer.getAttachedChildren()`（折叠、子容器 GONE 时依然有效）。
   实测：5 次展开/收起循环后几何回到 268，AOSP 的 `exp` 全程 0，`expand rejected` 恰好 5 次。
3. **卡内容被压扁才是"变形"**。收起态行容器被父级量到 120px，而三行文本自然高 178px，多出的 58px
   画到露边带上（用户看到的"通知变形"）。自量高度后自然高生效，卡随之从 210 变 268 —— 这是修正不是回归：
   非堆叠行本来就是 268 高。

### 观察机制的四处不严谨（同样算 bug，已补）
- `contentHeight` 的缓存键漏了文本内容与 maxLines，返回过期的 268 而卡片实高 210 —— 自己造的洞，
  已改成每次都真实量（代价只是十几个 View 的一次 measure）。
- 校验器"PASS"时可能一条堆叠测量都没跑到：现在用 `notify_row.witness card=1` 反查覆盖，
  屏上有卡却没测到 → FAIL。
- 模块侧 `scheduleStackVerify` 重试 8 次后静默 return（面板没开时就永久哑掉）：现在打
  `notify_row.verify.stack.skipped`（带 shown/settled 原因），校验器对"放弃且无补测"直接 FAIL。
- 证据要认进程：换过 SystemUI 之后，旧 pid 的 witness/skip 行不再算数（曾据此误判过一次）。

### 回归矩阵（真机 V2314A，最终构建）
折叠 3 堆叠行 + 10 普通行 PASS；展开态 PASS（overlayH=rowH=387、clH=339、kids=2）；
夜间 PASS；fontScale 1.3 PASS；5 次循环无漂移；滑走一条后无幽灵行；crash buffer 0 FATAL。
`STACK_BUILD_ENABLED=true`（堆叠随 0.1 一起发；仍未覆盖：heads-up 联动、RTL）。

## 2026-10-27 12:20 · 堆叠卡交互拆分 + 展开过渡动画

- **点击拆分**：卡体/图标 → 打开应用；右上角自绘箭头（`Chevron`，纯 Path 无资源）→ 展开/收起。
  卡体不能走 `row.performClick()`：组摘要行那个点击的语义是"展开组"，而我们一律拒绝 AOSP 展开，
  结果是点了没反应。改成直接发最新子通知的 `contentIntent`，实测 `topResumedActivity` 真的换到目标 App。
- **动画**：`ValueAnimator` 180ms（DecelerateInterpolator 1.6）驱动 `expandT`，
  `contentHeight()` 在过渡中把两端各量一次再插值 —— 行高与卡高仍同一个源，不引入第二套高度。
  逐帧打点（`notify_row.stack.frame`，仅 verify=1 时输出）实测 3 个来回 78 帧，
  `cardH/rowH` 每帧等于上一帧的 `target`（正常的一帧 measure→layout 滞后），收在 268/387 精确值。
- **打点当场抓出两个洞**：
  1. 插值只改了返回值、没改卡片自身的 `measuredHeight` → 过渡中父级按旧值摆放，卡面停在 168px。
     修：过渡分支里 `setMeasuredDimension(widthPx, h)`。
  2. 交叉淡入淡出糊成重影 —— 正面内容垂直居中、列表首行在顶部，两者错开半截。
     修：过渡中正面内容顶对齐 + 淡出提前（1-open*2.5）/列表延后进（(open-0.25)*2），并裁掉溢出。
- **校验器两处口径修正**：堆叠卡时间端距改为 16dp 边距 + 30dp 箭头预留（138px@3.0）；
  模板本身没有时间位时（vivo 自家模板）跳过时间相关断言 —— 否则会拿 0 坐标算出 -270 的"重叠"。
- **遗留（本轮引入无关，校验器仍红）**：`com.bbk.appstore` 的 base 行 title/body 右端距 132px（应 48），
  文字比规格早截断 84px。之前没采到这个样本，属既有问题，待单独查。

## 2026-10-27 12:42 · 撤掉通知列表底部的 AOSP NotificationShelf（图标收纳条）

- **身份确认**：NSNL 子节点转储（新增 `notify_row.witness.list`）显示底栏是
  `com.android.systemui.statusbar.NotificationShelf`，h102、排在最后一行之后。
  一开始怀疑 AOSP pile-up，但 dex 里 `PileUpArea` 全是腾讯 AMS 广告 SDK 的符号，排除。
- **两条无效尝试（记录以免重蹈）**：
  1. 拒绝 `changeShelfVisibility(true)` / `setShouldShowShelfOnly(true)`：钩子确实命中（58 次 denied），
     但它的可见性不是这两个开关决定的，底栏照旧 v0。
  2. 在 NSNL onLayout 里 `setVisibility(GONE)`：AOSP 每轮改回 VISIBLE，715 次拉锯且仍可见。
     另外 `hookAllMethods(NotificationShelf, "onMeasure"/"setVisibility"/"dispatchDraw")` 根本不生效 ——
     这些方法是从 View/FrameLayout 继承的，该类没覆写。
- **有效做法**：在 NSNL `onLayout` 之后改它的 `LayoutParams.height = 0` + `setAlpha(0)`。
  这两个量 AOSP 不回写；0 高即不占位（NSNL 按 `getMeasuredHeight()` 排版），实测 `h0` 且被推到视口外。
- **结果**：10 条子通知连成一整张长卡向下排、超出视口靠滚动，符合 OS6/iOS；
  校验器 8 普通行 + 3 堆叠行 PASS，crash buffer 0 FATAL。

## 2026-10-28 07:30 · OriginOS 3 全功能适配轮（崩溃面 + 动作区 + 另两个功能）

过程与证据全在 `out/LOG-1027-OS3-ADAPT.md` 阶段 5（5a~5j），这里只留结论与踩坑。

- **一键清除导致 SystemUI 重启的根因**：`StatusBarNotification.getGroupKey()` 被我们抹成 null
  （DEVLOG 2026-10-26 22:27 记过这个崩溃循环、写着"已回退"，**代码里其实还在**）。
  批量移除时 GroupMembershipManager/HeadsUpCoordinator 拿 null key 查表 → NPE 循环。
  已删除该钩子，只留 `isGroup()→false` + pipeline 拆组；separate 模式复验 `summaries=0 groupEntries=0`。
- **崩溃面第二类**：`onPreDraw` 与 `post/postDelayed` 里的异常**不在 Xposed 保护范围内**，
  一次异常就重启。统一收进 `safe(Runnable)` / `preDrawGuard(Runnable)`；
  `guardOverlay` 在卡片被摘掉后仍每帧 `root.setAlpha(0)`（行回收后会把整行压透明）也补了早退。
  OS3 回归：separate/ios_stack 各 3 轮批量移除，PID 不变、crash buffer 0、兜住的异常 0。
- **OS3 动作按钮真相（推翻上一轮判定）**：~~uiautomator 在这台机上不导出通知行~~
  **这条机制当时判错了**，2026-10-28 09:40 用展开面板实测推翻：通知行是导出的
  （`com.android.systemui:id/expandableNotificationRow`、`android:id/notification_main_column` 都在），
  真正被抹平的是 **`class=`**（全部退化成 FrameLayout/LinearLayout/TextView），
  所以按 `Button`/`ExpandableNotificationRow` 这类**类名**去 grep 一定是 0。
  结论仍然成立且更有用："dump 里没节点"不是证据；取证要抓 resource-id 或看模块自己的 witness；
  A/B（`feature_notify_row` 开关各重启一次）显示 **A13 收起态原生就没有动作按钮**，
  按钮在展开态、入口是行右侧 ⌄，动作容器是 `android:id/actions_container`（行的直接子级，模板之外）。
  所以我们真正的缺陷是"把箭头藏了 → 按钮永远够不着"。修法：动作行保留箭头、允许展开、
  不参与收起态高度钳制、时间右移让出 30dp 箭头位；全部用 `needsExpandForActions()`
  = `OsProfile.isA13OrLower() && hasInlineActions()` 关门，**OS5 走的代码逐行不变**。
- **本轮自己踩的三个坑**（都靠真机现场见证抓出来）：① `hasInlineActions` 收到模板根时
  `rowOf()` 解析不到行，要用 `rowAncestor` 爬父链；② 首轮扫描赶在动作区 inflate 前，
  **负结果被缓存 = 永久假阴性**，只缓存正结果；③ `getIdentifier(...,"android")`+`findViewById`
  这条快路径在通知视图里量不到（context 是发通知 App 的 resources），同棵子树 DFS 却每帧命中。
- **另两个功能**：PickerAlbums 在 OS3 上原本**整个功能 0 钩子**（`queryAlbums(String[])` 抛
  NoSuchMethodError 把同 try 块的 appendWhere 一起带崩；第三参 A13 是单个 String 会 CCE）→ 修好
  `hooks=2`，并查明这台机**没有可达的 Photo Picker**（新增延后自检打 `picker albums landing no-entry`）；
  FileManagerRename 按新逆向的 A13 混淆符号表移植（`view.dialog.f2`/`l1.d`/`DialogNameEntry.a(String)Z`，
  VEditText 与 ShareFile 在 A13 不存在）→ `fileman rename patch installed os=a13 hooks=2`。
- **校验器**：`work/verify_notify.py` 支持 `BO_LOG_FILE/BO_ADB_SERIAL/BO_ADB_ROOT`，
  并为动作行给出等价而非放宽的断言；OS3 侧 **PASS**，同一份跑旧 OS5 日志失败项与改动前逐条一致。
- **设置 App**：首页副标题不再写死 "OriginOS 5"，改用 `OsProfile.originName()` → OS3 上显示
  "OriginOS 3 · LSPosed 模块"。
- **A13 媒体卡拖动进度原本完全失效，已修**：`bindSession` 用通知视图 context 取
  `MEDIA_SESSION_SERVICE`，A13 校验包名/uid 不匹配 → `TransportControls.seekTo()` 抛
  `IllegalArgumentException: packageName does not belong to the calling uid`，
  而回落的 widget 兜底又被 A13 关门（事务号不可用）→ 拖了没反应。
  改用 `sysuiContext()` 后端到端复验：`card seek bar DOWN` → `onSeekTo pos=145214` →
  `dumpsys media_session position=63000→145214`。OS5 上这个 bug 一直被 widget 路径掩盖。
- **上一条只修了"能 seek 的会话"，i音乐 这类不给 SEEK_TO 的会话仍然拖不动（用户复现）**：
  `actions=3126` 解码 = PLAY_PAUSE+STOP+SKIP±+PLAY_FROM_MEDIA_ID+PAUSE，**不含 ACTION_SEEK_TO(512)**，
  所以标准通道永远走不通；而 `updateSeekableInner()` 在 `canSeek=false` 时
  `bar.setOnTouchListener((v,e)->true)` 把触点整个吞掉（本意是"别让人拖一条死条"），
  结果就是"进度条完全无法拖动"。真正的通道是 vivo 的
  `com.vivo.musicwidgetmix.service.MusicWidgetMixService`（exported、无权限），
  它 A13 版（3.3.0.3）里 `seekPos(long)` 的事务码**就是 15**，与 OS5 相同——
  之前判定"A13 事务号不可用"是因为 `TRANSACTION_seekPos` 字段名被 R8 混淆、dexdump 搜不到，
  属于"名字找不到"被当成"值不同"。核对方式：反编译 Stub（`com.vivo.musicwidgetmix.e$a`）
  读 `onTransact` 的 switch，`case 15: a(parcel.readLong())` + `writeNoException()` 即 void(long)。
  修法：删掉 `ensureWidgetService()` / `seekViaWidget()` 两处 `isA13OrLower()` 关门。
  真机复验：`card seek bar DOWN enabled=true` → `widget seek pos=2417469 ok=true` →
  `dumpsys media_session position=2417469`（=40:17，卡片时间同步显示 40:19/58:56，
  下发值与落点误差 <1%，即拖动位置→毫秒的换算正确）。
- **展开重影（用户两次反馈"还是有"后定位）**：真机截图 `ghost-2-expand.png` 抓到 互传 行展开时
  收起态大图标（125,1720）与展开态 header 小图标（280,1680）同时可见，且该行 `card=0`
  ——**没有自绘卡**，所以我前两轮改的 overlay 淡出/状态交接完全不在它的路径上。
  根因是 A13/vivo 展开时不把收起子视图置为不可见。最终修法改成**无状态**：
  对展开子视图跑一次 `hideSmallIcons` + `guardSmallIcons`（`guardExpandedGhost`，
  注册点放在 `restyle()` 所有过滤之前，覆盖每一行）。
  走过的弯路（都留了证据）：① 按 `cvH vs cH` 算淡出进度 → 真机 `f` 恒 0，一次没触发；
  ② 按 `isExpanded` + 展开子视图可见交还原生 → 该行守卫根本没挂上（`ghost hide=0` 全是别的行）。
  结论：这台机上"判断哪侧该显示"两种形态都有反例，只能从"消灭重复的那一枚图标"下手。
- **同轮引入并修掉两个回归**：① 通用抑制波及媒体行 → 媒体卡右上角露出原生展开态封面副本，
  加 `isMediaRow()`（`CATEGORY_TRANSPORT` 或类名含 `Media`）排除；
  ② `guardExpandedGhost` 对**有 overlay 的行**也写 `contracted.setAlpha(1f)`，
  把 overlay 守卫压下去的原生内容又翻回来 → 门禁 5c 抓到 `nativeAlpha=1` 直接 FAIL。
  改为"有 overlay 的行不碰 alpha"。**这条是门禁的价值所在：没有 5c 就会带着可见重影发布。**
- **门禁补强**：新增 5d「展开态图标去重」断言（`ghost dedup` 行数 >0、至少一条 `targets>=1`、
  且不得命中媒体行），首跑 23/23、复跑 29/29、夜间 22/22、大字号 24/24、separate 18/18。
  另修掉覆盖假阴性：夜间/大字号下固定一次滚动只测到 2-3 行 → FAIL 是"没测到"而非"测出错"，
  改成边滚边数（最多 5 轮，够 5 条才继续）。四组配置（默认 ios_stack / NIGHT=1 / FONT=1.3 /
  separate）在当前包上全部 exit=0。
- **"进度条被外力改过可见性"从治到愈**：给 `DragSeekBar` 覆写 `setVisibility` 打调用栈，
  真机抓到元凶 —— `ExpandableNotificationRow.notifyHeightChanged ->
  NotificationContentView.selectLayout -> updateViewVisibility ->
  NotificationHeaderViewWrapper.setVisible -> TransformState.setVisible(vis=4)`：
  因为我们为了拿横向手势借用了 `android.R.id.progress`，AOSP 把这条当成"它自己的进度条"
  在每次布局选择时藏掉。先试过"整张卡一起自愈"，实测 `card reclaimed visibility` 命中 0 次
  ——**只有条被藏，卡没有被**，所以把那段投机代码删掉，改成条自己在下一帧 `post` 要回 VISIBLE
  （行本身不可见时条必然不可见，无需判断场景，也不会和收起动画打架）。
  效果：每轮门禁的"外力改条"计数 14 → 4 → 1~2，条仍正常绘制（`seek draw` 7 帧）。
- **另两个功能的 OS3 可达性补齐静态证据**（`os3-probe.py` 抽不到它们，因为 hook 目标写成常量而非
  字面量，探针只认字符串字面量；已把三个文件加进 FILES 但仍需手工解析常量）：
  从设备实际 APK 取证（`exec-out cat` 字节数与设备一致）——
  - 相册白名单：`/apex/com.android.mediaprovider/.../MediaProvider.apk`（TP1A.220624.014）
    内 `Lcom/android/providers/media/photopicker/data/ExternalDbFacade;` 存在，
    `queryAlbums`/`appendWhere` 方法名各命中 2/4 次 → 钩子可达。
  - 文件管理器重命名：`/system/app/BBKFileManager`（6.11.4.4）里混淆名**逐个对上签名**：
    `f2.x()V`（CONFIRM_A13）、`f2.a(File,String,String)V`（RENAME_IMPL_A13）、
    `l1.a(Context,File,int,String,File)V`（LIMIT_CLS_A13）、`DialogNameEntry` 及其 `a` 方法。
    这条很重要：A13 的混淆名是**按 build 变的**，6.11.4.4 对上不代表别的版本对上，
    所以版本号和这份取证要一起留在日志里。
  仍未验证的是**运行时行为**：>56 字重命名的功能测试还卡在文件管理器的"同意"弹窗（不代点）。
- **重影修复的两次自我回归（重要教训，别再看代码想当然）**：
  1. 把去重从"绑定时做一次"改成"检测到展开残留时才做" → `ghost-9` 两枚图标照样并存。
     原因：A13 的展开子视图是懒加载 + **展开动画结束后系统会重新写回子视图可见性**，
     动画期间动手等于被覆盖。绑定时动手才躲过这次写回。
  2. 给去重加 `OsProfile.isA13OrLower()` 门槛 → `ghost-10` 重影回来；去掉门槛后 `ghost-11` 干净。
     **但这条归因是错的**：`isA13OrLower()` 就是 `Build.VERSION.SDK_INT <= 33`，在这台机上恒真，
     不可能是开关失效的原因。两版真正的差别更可能是**该行有没有在展开动画之前被去重过**
     （A13 展开子视图懒加载 + 动画会写回可见性 → 时序依赖）。
     结论降级为：这条修复目前**只在"绑定/动画前"这条路径上被证明有效**，
     "从未展开→首次展开"仍需专门验证；不要把这次的 ghost-11 当成它普遍有效。
  3. 顺带被门禁抓到：alpha 交还原生时只排除了 overlay 行，没排除**堆叠行**，
     于是把堆叠卡下面已压住的原生组头放回 alpha=1 → 门禁报
     `3 stack cards on screen but 0 verified`。改为 overlay/stack 都不碰（`cardOwned`）。
  最终版（绑定时无条件去重 + residual 只管 alpha + cardOwned 排除两类卡）：
  `ghost-11` 展开态只剩一枚图标，门禁 7 类行覆盖、`ghost dedup 22/22`、QS 磁贴钩子在内全绿。
- **把"首次展开"从手测变成可重复证据**：`ghost-11` 只覆盖"该行在展开前已被去重过"的路径，
  真正没测过的是"从未展开 → 首次展开"（`ghost-9`/`ghost-10` 正是在这里翻车）。
  定位到机理：`applyHidden` 走的是 **alpha**（刻意不切 visibility，避免每帧 requestLayout 吃手势），
  而 A13 展开动画会把这些视图的 alpha 写回 1；我们挂在**展开子视图自己 VTO** 上的逐帧守卫
  在它 attach 之后观察器已被换掉（和 overlay 那次同一个坑），所以没人补写 → 重影复现。
  修法：把要按掉的图标缓存下来（`GHOST_TARGETS`，**排除 ExpandButton**，否则会把唯一的
  展开入口吃掉），改由 **contentView 的活守卫**逐帧补写 alpha，漂移了才写。
  证据：`ghost-12`（重启 SystemUI 后该行**首次**展开）只剩一枚图标；
  门禁把手（`debugExpandFirstRow` 现在也覆盖挂了重影守卫的普通行）展开后
  实测 `ghost reapply 4 次` —— 说明"动画写回 alpha"这件事真的在发生，不是推测。
- **OS3 横幅（heads-up）为什么测不到——已排除我们侧的三种可能**：
  1. 通道重要度不是问题：`dumpsys notification --noredact` 里
    `mId='betteroriginos_debug' mImportance=4`（HIGH），且 `mOriginalImp=4`，
    不是"旧通道没升级"那个经典坑。
  2. 不是"更新而非新通知"：先 `debug_notify cancel` 再重新投递，仍然不弹。
  3. 不是面板/前台状态：`cmd statusbar collapse` + 桌面前台 + `stayon` 都试过，
    通知确实进了列表（状态栏有角标、记录存在），但横幅不出现。
  另有一条线索：`dumpsys notification_settings` 里**根本没有 com.betteroriginos 的
  AppSettings 条目**，说明这台机上该应用从未走过用户的通知授权决策——
  vivo 的横幅开关很可能就挂在这条"用户未决策"的默认抑制上。
  结论：OS3 上横幅落点无法由我们的测试通知自行触发，属系统侧设置问题；
  门禁里 `0 headsup rows` 因此是**已知未覆盖**，不是本轮改坏的。
  要闭环需要用户在"设置 → 通知与状态栏"里给我们的应用开横幅（或授权通知）。
- **补掉一个静默放弃**：普通行 `scheduleVerify` 重试耗尽时没有终态日志（堆叠那条早就有），
  于是"测试行被用户通知挤到可视区下方 → 一条测量都没有"看起来像校验器坏了。
  现在打 `notify_row.verify.skipped …`，`verify_notify.py` 对"只有 skipped 无成功测量"直接 FAIL；
  顺带把它自己写死的动作带下限 100px 改成 `dp(20)..dp(72)`（实测 2 动作样本 98px、vivo 模板 126px）。
  **最终构建复跑**：一键清除式批量移除 3 轮 PID 不变 / crash buffer 0 / 兜住的异常 0，
  OS3 代码级校验 PASS（1 普通行 + 2 覆盖行 + 1 堆叠行），旧 OS3/OS5 日志回归没有变松。



- **补刀（12:50）**：压成 0 高后收纳条里的**小图标仍在** —— 图标是收纳条的子容器
  `NotificationIconContainer` 画的，收纳条默认 `clipChildren=false`，0 高照样让子节点画到外面；
  且它的 alpha 会被 AOSP 淡入动画改回 1（转储实测 `h0 a1.00`），所以压 alpha 也不可靠。
  最终在同一个 onLayout 守卫里给收纳条开 `clipChildren/clipToPadding = true`，
  "高度 0" 才真正等于 "什么都不画"。复验：28 普通行 + 3 堆叠行 PASS，0 FATAL。
- **OS3 验收脚本化**：新增 `work/os3-smoke.sh`（环境前置 → 钩子装载 → 冷启投递 → 慢滚到可视区 →
  `verify_notify.py` + 覆盖闸门 → 一键清除式批量移除 3 轮 → 媒体卡链路 → 收尾），退出码非 0 即有失败。
  配套修了普通行 verify 的重试预算（8→14 次，慢滑后的补间窗口赶不上会静默放弃）、
  给校验器补了 `hasTitle=` 前提（无标题的通知不该判 `title hidden`），
  以及脚本自身的 `cygpath -w` 与 `svc power stayon` 两个坑。最终一轮全绿（7 类行实测、0 放弃、0 崩溃）。

## 2026-10-28 09:35 · 一键清除崩溃类收口：主线程回调兜底 + `setResult(null)` 清账

- **补两处主线程兜底**（都与"一键清除带走 SystemUI"同一类，Xposed 只保护 before/after 钩子，
  观察者与 `MediaController.Callback` 不保护）：
  `MediaCardFeature.updateSeekable()` 拆成外壳 try/catch + `updateSeekableInner()`；
  `NotifyRowFeature` 的 reverify `ContentObserver.onChange` 原来裸调 `triggerReverify()`（要遍历整棵通知树）。
  其余同类面逐条核对后确认**已有**整方法体 try，不重复加壳：`ticker`/`updateProgress`/`syncArt`/
  `refreshColors`/`release`、`QsPlayerTile.run()`（其 Callback 只赋字段、不碰 binder）、`captureWithRetry`。
- **`setResult(null)` 全量清账**（OS5 事故的确切机理，必须一次数清）：现存 4 处，用
  `G:\os3-ref\os3-dexdump-f.txt` 查返回类型，全部是 **void**（`sanitizeTopLine`、`updateIconsForLayout`、
  `updateVivoIconsForLayout`、文件管理器 `f2.x()V`）。也就是说，除了已删除的
  `getGroupKey`（返回 String，被批量移除的 key 查表消费）之外，**没有任何 hook 在用 null 覆盖一个被消费的非 void 返回值**。
- **冒烟闸门补 5b) 媒体卡在场的清除压力**：面板展开时 `postmedia`↔`cancelmedia` 两轮，
  利用 `dbg()` 里的 force-stop 制造"会话先死、通知后消"的窗口，断言 PID 不变。首跑就把一个
  **自我污染的断言**抓了出来：`DeadObjectException` 计到 11 条，逐条看栈全是 system_server 在第 2 步
  kill 掉旧 SystemUI 后对旧 binder 的正常抱怨（`TaskOrganizerController`×7、`PlaybackActivityMonitor`、
  `VolumeController`、`DisplayManagerService`×2），与被测模块无关 → 计数器改为 `--pid=$P`。
  **纪律：任何"异常计数"型断言都必须先按 pid 划界，否则自己重启一次就永远脏。**
- **顺带纠正一个归因**：这一轮 `updateSeekable` 兜底 0 次、`updateProgress failed` 0 条，说明
  `MediaController.getMetadata()/getPlaybackState()` 在会话死亡时**内部已吞掉 RemoteException 返回 null**，
  所以 OS5 的一键清除重启**不是**这里引起的（真因就是 getGroupKey + 未保护的 pre-draw/deferred 路径）。
  补壳按 defense-in-depth 保留，但归因写清楚，免得后人把兜底当成根因修复。
- **132px 端距遗留项降级为"校验器分类竞态假说"**：原始日志已被覆盖、无留存证据；
  按代码复核，"实际有箭头→按 46dp(≈126px) 预留，但 verify 发生在绑定瞬间 `expand=0` 被判成普通行
  （期望 16dp≈44px）"正好解释这个数。**没有去放宽阈值凑通过**；下次真实样本落盘时同一行里
  `expand=`/`actVis=` 与 `timeEndGap=` 同时可读，一步就能定案。
- 最终一轮 OS3 全绿：`OriginOS 3 / sdk 33`、钩子装载 2 条、覆盖 6 类行、放弃 0 条、
  批量移除 3 轮 PID 1962 不变、crash buffer 空、5b 媒体销毁 2 轮 PID 不变、媒体链路 22 条日志、seek 回落无异常。

## 2026-10-28 09:52 · 设置 App 的"落点说明"组件（OS3 不再摆静默失效的开关）

- 新增 `Feature#supportNoteRes(Context)`（默认 0）+ `item_feature.xml` 的 `feature_note`
  （摘要下方 tertiary 12sp，无说明时 GONE 不占位）。OS3 上给两处系统差异说实话：
  照片选择器本机无落点、通知动作按钮只在展开后渲染。OS5 两个 override 均返回 0，渲染与改动前一致。
- 文案第一版写成 5 行还夹带"实测 MediaProvider 无 photopicker 落点"这种取证术语，
  截图一看整张卡被灰字压满 → 收敛成 ≤2 行的人话。**规范补进 DESIGN-SPEC §4。**
- 因为动的是会被 SystemUI 加载的共享类（`Feature`），改完必须复跑整套 OS3 闸门：
  全绿（覆盖 5 类行、批量移除 3 轮 + 媒体销毁 2 轮 PID 10968 不变、crash 0、死会话异常 0）。

## 2026-10-28 10:06 · 崩溃归因补上跨系统证据 + 闸门接管 QS 磁贴 + 拆掉一处会诱发复发的注释

- **一键清除崩溃的因果链现在是"实录 + 跨系统"两级**：OS5 真机当年崩在
  `HeadsUpCoordinator.onBeforeFinalizeFilter`（2026-10-26 22:27 踩坑条目）；本轮用 OS3 dexdump
  确认**同一个方法在 A13 也在**（`(Ljava/util/List;)V`），所以"别把 `getGroupKey` 抹成 null"
  不是历史包袱而是长期约束。
- **`filterGroupSummaries` 里还留着"子项已通过 getGroupKey=null 独立成卡"的注释**——机制早删了，
  注释却在教后人把崩溃源加回来。改成实际机制（`isGroup=false` + `flattenGroups`），
  并把两系统的崩溃点写进 `isGroup` 钩子注释。摘要移除本身复核安全：`Iterator.remove()` + 整段 try
  + 发生在被 hook 的方法内（Xposed 保护范围内）。
- **QS 磁贴装载路径查实**（OS3 唯一媒体落点）：没有独立插件进程，插件加载进 SystemUI，
  `qs player tile installed on …ControlCenterMediaPlayerView` 现场可见；
  顺带排除一个假缺陷——`scopes()` 用的是常量 `PLUGIN_PKG`，按字符串 grep 会误判成"作用域缺一项"。
  闸门第 6 步因此新增 `qs player tile installed ≥1` 与 `mediaShade=` 两条断言。
- **`mCurrentFocus` 在息屏后是脏的**：这台机 sleep 后仍显示 `NotificationShade`，
  本轮两次误判"设备正被人使用"都是它。判空闲要和 `dumpsys power` 的 `mWakefulness=Asleep` 一起看。
- 最终构建复跑整套闸门全绿：pid 17336、覆盖 6 类行、批量移除 3 轮 + 媒体销毁 2 轮 PID 不变、
  crash 0、死会话异常 0、磁贴钩子 1 条。

## 2026-10-28 10:22 · OS3 深色模式首测 PASS + 磁贴断言改成确定性 + 闸门学会自己收拾设备

- **OS3 深色模式是本轮才第一次实测**（此前只有 OS5 的 night 日志）。`cmd uimode night yes` 后整套闸门全绿，
  且复核过不是空跑：9 条测量行全部 `night=1`、文字 `0xffffffff`、堆叠卡 `cardColor=0xf21c1c1e`。
  闸门加 `NIGHT=1` 开关（第 0 步切深色、第 7 步自动还原浅色），别让"只测浅色"变成习惯。
- **新加的 QS 磁贴断言第一次跑就 FAIL，是断言自己不确定**：`SystemUIPlugin` 懒加载进 systemui 进程，
  没画过一次 QS 面板就没有 `qs player tile installed`。修法是补确定性触发
  （查之前 `cmd statusbar expand-settings` + collapse），不是放宽阈值。复跑 exit=0。
- **闸门收尾现在还原 `betteroriginos_notify_verify` 原值**：之前每次跑完都把设备留在持续验证日志状态。
- **自己的操作事故**：`bash work/os3-smoke.sh | head -12` 让 head 的 SIGPIPE 中途杀死脚本，
  跳过收尾 → 设备被留在深色模式 + `stayon=7` + 3 条未清通知。长脚本要落盘再看 tail 或 `tee`。

## 2026-10-28 10:40 · OS3 大字号实测 + 修掉"日志量错对象"导致校验器容差被悄悄放宽

- **字号 1.3 在 OS3 上首次实测 PASS**（此前只有 OS5）：`titleTS/bodyTS/appTS` 45/42/36 → 58/55/47，
  `contentH==rowH`、行高跟着长，没裁字。闸门加 `FONT=1.3` 开关（跑完恢复原字号），
  与 `NIGHT=1` 一起把"浅色/1.0 之外的维度"变成一条命令就能复跑。
- **抓到一个比"测过了"更值钱的缺陷**：verify 行的 `fontScale=`/`density=` 取自 `ctx`
  （发通知 App 的 `createPackageContext`），它的 Configuration 可能是旧值——
  设备已 1.3、文字已 58px，日志仍记 `fontScale=1`。而 `verify_notify.py` 三处按这个值缩放容差，
  **等于大字号下断言被静默放宽**（这轮侥幸没误判）。改成 `root.getContext()`，
  与本来就用 `overlay.getResources()` 的另两条日志对齐。复验双向：1.3→记 1.3、1.0→记 1.0。
- 附带解释旧遗留：这台 DSU `density=3.0`，所以"应 48px"就是 16dp，132px 落在 46dp 箭头预留量级，
  与 5g 的"分类竞态"假说自洽。

## 2026-10-28 10:43 · 闸门补上"逐条清除能力"断言（OS3 唯一移除入口终于被约束）

- OS3 没有一键清除圆点，逐条滑动/单条清除是这台机唯一的移除入口，但 `verify_notify.py`
  从没读过它一直在打的 `rowCanDismiss`/`entryClearable`/`notifFlags`——纯观测、零约束。
  补两条：`rowCanDismiss` 必须恒为 1；`entryClearable=0` 只允许由通知自带
  `FLAG_ONGOING_EVENT(0x40)`/`FLAG_FOREGROUND_SERVICE(0x20)` 解释，否则判"被我们改没了"。
  现场合法例子：LSPosed 前台服务通知 `notifFlags=96` 被按策略豁免，不误报。
- 新断言做过**正负对照**：真实日志 PASS；篡改 3 行为不可清除 → 精确 FAIL 4 条
  （负样本留作 fixture `work/z8os3-live/negctl.log`）。加断言不等于加对断言，得先证明它会响。

## 2026-10-28 10:52 · 逐条清除断言在"分离 × 深色"首跑即误报，查回两处规则错误

- 新矩阵（separate × night）跑出 6 条 FAIL，全是 `notifFlags=0x2`。根因是断言自己错两处：
  **位值凭记忆写错**（平台 jar 实为 `ONGOING=2`/`NO_CLEAR=32`/`FGS=64`，我把 0x40/0x20 记反，
  正好漏掉 ongoing），以及**策略只应用到一个字段**（豁免了 `entryClearable` 却仍要求
  `rowCanDismiss=1`，把系统正确的"ongoing 不可滑动清除"判成我们的缺陷）。
- 修完三向复验：分离+深色 PASS、堆叠+浅色 PASS、篡改负样本仍精确 FAIL 5 条。
  教训写进 skill：**加断言必须同时配正负对照，位标志一律 `javap -constants` 核**。


## 2026-10-28 22:30 · 图标切换方向纠正 + 堆叠展开改多胶囊（iOS 化）

**用户判定（原话）**：①「现版本的大图标还是淡入淡出，不是我理想的丝滑切换，而且原来的小图标在展开收起时会闪现移动」；
②「小图标本来就不要，记住了」；③「堆叠通知展开时会连一起，而不是多个胶囊展开」；
④「自己做的右上角的箭头不好看，要么换原生箭头要么剥离出来单独悬挂」；⑤「其他场景过渡要仿生 iOS 的 q 弹非线性」。

**图标 morph 的两次方向错误（都有真机证据）**：
- 第一版对两侧都取 `firstIconView()`（只认 `CachingIconView`），拿到的是被 ghost 守卫压成
  `alpha=0` 的原生 header 小图标；日志 `from=126,48x48 to=126,48x48 moved=0` —— 飞行图标原地不动，
  用户看到的大图标照旧淡出，小图标被系统动画写回 alpha 时闪现。**飞的是隐形图标**。
- 第二版把落点改成小图标并在落定后让 ghost 守卫「放行」它——直接违背用户「小图标不要」。
  真机 mid 采样 `smallAlpha=1.0` 证明小图标确实在过渡里露脸。
- 最终方案：源/落点都取 **38dp 自绘大图标**（`TAG_ICON` 或 `IosNotificationRow.icon`），
  小图标在 ghost 去重里**全程**压 0（包括飞行期间，旧代码在飞行时跳过补写，正是 smallAlpha=1 的来源）。
  真机：`icon morph open=1 from=48,71x114 to=48,119x114 moved=1 srcKind=own dstKind=own`，
  `mid vis=0 x=48 y=108 srcAlpha=0 dstAlpha=0 smallAlpha=0`，`landed destAlpha=1 smallAlpha=0`；
  收起方向对称（`48,119→48,71`）。
- A13 展开动画会把**收起子视图 alpha 写回 1**，一次性淡变挡不住：guard 在「展开子视图 VISIBLE 且 alpha>0」时
  逐帧把收起侧压 0（`con alpha re-zero`），否则两枚大图标叠画。
- 「闪现」的结构性原因：flight 视图原来在 **preDraw 里 addView**，这一帧新 child 不参与绘制，而源图标已被
  压成 alpha 0 → 丢一帧。改成**绑定时预建** flight（38dp、INVISIBLE），morph 只用 translation/scale；
  overlay 卡片后加时 `bringToFront()` 保证飞行图标在最上层。

**堆叠展开 = 多个独立胶囊（不再是连体大卡）**：
- 设计：**正面卡本身就是第一枚胶囊**（`rowContainer` 全程可见、不淡出、内容不换），列表只渲染第 2..n 条；
  每枚胶囊独立 20dp 圆角 + 8dp 间隙，胶囊底由卡面 `onDraw` 统一画（与折叠卡同宽），文字仍内缩 16dp。
- 高度仍是单一真源：`contentHeight = frontH + 8dp + listH + 8dp`，帧日志 `cardH==rowH==target`。
- 原生收起内容整棵 `alpha=0`（以前只藏背景），否则胶囊间隙会露出原生文字，和胶囊内容叠在一起。
- 箭头改用行内 `NotificationExpandButton` 的 drawable（`useNativeChevron`），展开时 180° 弹性翻转；
  找不到原生 drawable 时回退自绘 Chevron（描边 2.4dp）。
- 过渡曲线：`NotifyRowSpec.spring()` = `PathInterpolator(0.18,1.30,0.30,1)`（过冲 ~8%），
  堆叠展开 280ms，单行图标 morph 同曲线。
- 真机度量（6 条组）：`count=6 frontVis=0 frontH=178 listTop=202 listH=1016 gap=24 cardH=1225`，
  稳态帧 `cardH==rowH==1240`。

**未闭环（下一轮继续）**：6 条组展开稳态截图里，中间 3 枚胶囊的内容叠在一起（背景看起来连成一片），
已加逐条 `items=[top x h v ...]` 日志待定位：是 item 布局间距没生效，还是原生/残留内容透出。

证据：`work/z8os3-live/{morph2,morph4,stk,clean_expanded,top_expanded}.png`、
`work/z8os3-live/stack_v3*.png`；日志见对应 logcat 时间点。


## 2026-10-28 23:05 · 堆叠展开叠字的真因（gravity 回落）+ 单行 morph 闪现的两处结构问题

- **堆叠展开后 3~4 条子通知文字叠在一枚胶囊里**，一开始误判成原生 `NotificationChildrenContainer` 透出；
  加逐条 `items=[top x h v]` 日志后证伪：条目 bounds 完全正确（0/208/416/624/832，各 184 高、24 间隙），
  行树里 children container 也是 `vis=8`。真因是 `applyExpansion()` 里
  `wantGravity = tweening ? TOP : CENTER_VERTICAL` —— **open==1 的稳态不算 tweening**，
  正面卡 gravity 回落到 CENTER_VERTICAL，被垂直居中到 1240px 的展开卡里，直接压到下面几枚胶囊上。
  改成 `open > 0.004 ? TOP : CENTER_VERTICAL`，真机截图恢复为"正卡 + 5 枚独立胶囊"。
- **单行图标 morph 的残余闪现**（用户第二次反馈）有两个结构原因，都不是缓动问题：
  1. flight 视图在 preDraw 里 `addView`：这一帧新 child 不参与绘制，而源图标已按成 alpha 0 → 丢一帧。
     改为 restyle 绑定时预建（38dp、INVISIBLE），morph 只用 translation/scale。
  2. 首次展开时 `expanded` 子视图是**后来才挂进 contentView 的**，会盖在预建的 flight 之上，
     开场那段飞行图标整段被遮住。morph 开始时补 `bringToFront()`（只重排 child 数组，不改 bounds，不会丢帧）。
  真机 10x 慢动作验证：`mid vis=0 x=48 y=86 srcAlpha=0 dstAlpha=0 smallAlpha=0`，
  240ms 弹簧曲线落点 `destAlpha=1`。
- **堆叠卡箭头换原生 drawable**：`useNativeChevron()` 取行内 `NotificationExpandButton` 的 drawable，
  展开时 180° 弹性翻转；自绘 Chevron 只作兜底（描边 2.4dp）。
- **过渡弹性**：新增 `NotifyRowSpec.spring() = PathInterpolator(0.18,1.30,0.30,1)`，
  堆叠展开 280ms、图标 morph 240ms 共用；`STACK_ANIM_MS 180→280`。
- **调试把手语义改成"设定目标态"**（stack_test: 1=展开最上面一叠 / 0=收起任何已展开的），
  不再依赖奇偶次触发；门禁因此可重复。
- **门禁**：5e 断言 morph 源/落点必须是 38dp 大图标（own）、中段三图标全隐形、落点 alpha=1；
  新增 5f 断言"正面卡=第一枚胶囊"（frontVis=0、listTop=frontH+8dp、gap=8dp、逐条 v0、
  稳态 `cardH==rowH==target`）。5d 前补"重投样本 + 列表回顶"，否则前面的批量清除会让 5e/5f 静默未覆盖。
- 全量门禁 `work/z8os3-live/g-2305.log`：**exit=0**，
  `morph 开 2 条（2 条 38dp→38dp 位移）；中段 2 条；收起 1 条；落点 2 条`，
  5f 六项全 ok，媒体卡/6b 仍绿。

构建 sha256 前缀：`1b433ed37a4c`（门禁跑的就是这版）。


## 2026-10-28 23:32 · 最后一层"闪现"真因：flight 视图根本没被测量（wh=0x0）

- 慢动作（`animator_duration_scale=10`）逐帧看：过渡中那一行**始终没有图标**，而 morph 日志一切正常。
  在 mid 采样里补打飞行视图状态后拿到决定性证据：
  `idx=2/3 pA=1.0 pVis=0 wh=0x0 shown=1 d=1` —— 视图在最上层、可见、有 drawable，但**尺寸是 0**。
- 原因：`NotificationContentView` 的 onMeasure/onLayout 只认它自己的 contracted/expanded/headsUp 三个子视图，
  额外 `addView` 进去的 flight 永远不会被测量/摆放。因此此前所有"几何正确"的 morph 其实都**没画出来**，
  用户看到的只有大图标淡出 + 落点图标淡入 = 闪现。
- 修法：
  1. `layoutFlight()` 手动 `measure(EXACTLY 114, EXACTLY 114) + layout(0,0,114,114)`，
     绑定时建好、morph 需要换尺寸时再摆一次；parent 从不覆盖它。
  2. AOSP 展开动画会把自己的子视图重新置顶，`bringToFront()` 一次性不够，守卫在飞行期间
     **每帧检查 index 再置顶**（已在最上层就不重排，避免每帧 requestLayout）。
- 真机复验（10x 慢动作逐帧）：过渡中图标连续可见并从收起位置下移到展开位置，落点交还后为 38dp 大图标；
  `mid` 日志 `wh=114x114`。最终门禁 `work/z8os3-live/g-final.log` **exit=0**
  （5e morph 1 开/1 收/1 中段/1 落点；5f 胶囊六项全 ok）。
- 最终构建 sha256 前缀：`1b8965b244ea`。
- 教训：**"代码路径跑了"不等于"像素画出来了"**。动画类修复必须同时断言视图尺寸/可见性，
  截图或慢动作逐帧比日志更接近用户看到的东西。

- 最终三组门禁（构建 `1b8965b244ea`）：
  `g-final.log`（日间+堆叠）exit=0、`g-final-sep2.log`（日间+分离）exit=0、
  `g-final-night.log`（深色+堆叠）exit=0。5e/5f 在堆叠与深色两组均完整覆盖；
  分离模式 5f 天然不适用（无堆叠卡），收起方向偶发未触发只记 warn。
- 5e 断言放宽为"源/落点都必须是 restyle 大图标（own/card，9x~19x px）"，不再强制 moved=1：
  展开前后同位置（原地不动）是正确形态；旧版 48px 小图标仍会被抓。


## 2026-10-29 06:10 · 回归修复：收起态堆叠露边被裁掉（看起来只剩单条通知）

- 用户反馈"收起状态下堆叠显示不见了，变成单条通知样式"。原因是我在堆叠多胶囊改造时给
  `IosNotificationRow.onDraw` 加了外层 `clipRect(0,0,width,cardBottom)`，而**露边层本来就画在
  cardBottom 以下**（clipRect(0,cardBottom,...)），被外层裁剪整块裁掉——卡片只剩正面那一层。
- 修法：露边层先画（自带"只露下缘"的 clip），再进外层裁剪画胶囊/正面卡。徽标仍在最外层。
- 真机复验：日间堆叠截图（`work/z8os3-live/peek_fixed.png`）重新可见每张卡下方的分层圆角边；
  门禁 `work/z8os3-live/g-peek-fix2.log` **exit=0**（5e morph 2 开/2 收/2 中段/2 落点，5f 六项全 ok）。
- 构建 sha256 前缀：`81d0c354dca6`。
- 教训：给自绘 View 加"统一 clip"时，必须先列出所有绘制区域（本次有 正面卡/露边/胶囊/徽标四层），
  露边天然在卡片矩形之外——裁剪顺序错一层就是功能级回归。


## 2026-10-29 06:30 · 阶段交接：堆叠展开后的单项交互 / 底部截断 / iOS 覆盖式列表

**已完成（构建 `81d0c354dca6`，三组门禁绿）**：单行图标 shared-element morph（38dp→38dp、预建 flight、
手动 measure/layout、飞行期间置顶）、堆叠展开=多胶囊（正卡=第一枚胶囊、8dp 间隙、原生箭头、spring 曲线）、
收起态露边恢复（外层 clip 误裁的回归已修）。证据：`g-final*.log`、`g-peek-fix2.log`、`peek_fixed.png`。

**用户新报的四个问题（本轮目标）**：
1. **展开堆叠后，单个通知无法再展开**：胶囊是自绘 LinearLayout，没有点击/展开逻辑，
   自然丢掉"展开态动作按钮/原生通知特性"。需要评估：是接管胶囊点击去展开自绘动作区，
   还是把该子通知交还 AOSP 原生展开（隐藏 children container 的组在此场景要临时放行）。
2. **点击胶囊跳转错误**：胶囊没有点击监听，事件落到 overlay 的 onClick → 打开的是"最新一条"的
   contentIntent；且 send PendingIntent 后没有按 autoCancel 移除通知。需要按"被点的那条"取
   sbn/contentIntent，并在发送后按 `FLAG_AUTO_CANCEL` 走系统移除（或直接走原生行点击回调）。
   用户建议：写带 intent 的演示通知做跳转回归。
3. **最底部通知被截断、无法上滑看清**（一上滑就退出控制中心）：需要复现并查明是 NSS 底部
   inset/可滚动高度问题，还是我们 expanded stack 的行高/overlay 高度导致。
4. **iOS 覆盖式通知列表（新特性，用户让我评估）**：上一条盖住下一条、上滑时逐条上移露出
   —— 需要在 NSS 行间距上做负重叠 + 随滚动的视差位移，牵动全局列表布局与触摸命中，
   先做小范围原型/评估再决定是否上线。

**下一步**：复现 1/2/3 并抓证据；先修点击路由与 autoCancel（风险最小、用户感知最强），
再做单项展开的落点决策；最后评估 4 的可行性与代价。


## 2026-10-29 07:10 · 阶段 2：单条通知的点击/动作、底部滚动、覆盖式列表评估

**已修复并有真机证据**：
1. **胶囊点击路由**：展开组里每条胶囊绑定了"该子通知原生行"的点击（`performClick`），日志
   `stack child click via=row key=0|com.betteroriginos|4261|...` 精确到被点的那条；点前三条后
   通知数 3→2→1（autoCancel 生效）。找不到原生行时退回 contentIntent + 按 FLAG_AUTO_CANCEL
   走 `INotificationManager.cancelNotificationWithTag` 移除（不再"跳转后通知还挂着"）。
2. **动作按钮**：展开态每条胶囊（含正面卡）底部显示蓝色动作按钮，点击发送对应 actionIntent，
   日志 `stack action sent title=动作2`。正面卡动作行计入高度（`frontH=295 frontActH=117
   listTop=319`，关系仍是 +8dp）。跳转回归样本 `postjump`（3 条同组、独立 debug_open 标记 +
   autoCancel + 动作），MainActivity 打 `debug open=<tag>`。
3. **堆叠展开后无法滚动**：动画结束补一次 `refreshRowLayout`（NSS 高度缓存/滚动范围原本停在
   展开前，导致"底部内容露不出、上滑被当关闭面板"）。慢速上滑已能滚动且面板不关。
4. **底部留白**：原生基线（功能关闭 A/B）同样"最后一条贴屏幕底被裁"——OS3/DSU 固有；给 NSS
   补 28dp 底部内边距（`nss bottom inset installed`，行 attach 后由 guardStack 重试安装，
   父链实测 `ExpandableNotificationRow<NotificationStackScrollLayout<...`）。

**门禁**：`work/z8os3-live/g-stage2.log` exit=0（5e morph 2 开/1 收/2 中段/2 落点；
5f 正面卡 295px、listTop=319、gap=24、5 条胶囊、cardH==rowH==target）。

**"上一条覆盖下一条"的 iOS 列表 —— 评估**：
- 可行路径：`mPaddingBetweenElements` 取负（重叠 6~10dp）+ 行 `translationZ` 反转绘制层级
  （AOSP 后加的行画在上层，iOS 要"上一条压住下一条"），滚动时上层卡离开即自然露出下层。
- 风险：① 负间距会让**命中测试**仍按 child 顺序给下层行，和视觉层级相反（重叠带内点错）；
  ② NSS 的 dismiss/展开动画、Shelf、间隙断言都假设非负间距；③ 分类段
  （`VivoCustomStackSection`）里的行 Z 序要按段处理。
- 决策：先做**开关原型**（`betteroriginos_notify_overlap`，0=关），验证视觉/触摸/滚动三项后再
  决定是否默认开启并补门禁；不直接改全局布局，避免把已绿的 5c/5f 弄穿。

- 覆盖式列表原型实测（`betteroriginos_notify_overlap=8`）：负间距 + 行 `translationZ`
  反转绘制层级后，卡面确实"上一条压住下一条"，滚动时下层自然露出；Z 守卫同时挂在
  `NotificationStackScrollLayout` 与 `NotificationChildrenContainer` 上（日志两条 installed）。
  但**命中测试仍按 child 顺序**给下层行，重叠带（8~12dp）内会点错层；且原生动作条会从
  行底露出（已通过在收起态强制 GONE 修掉一处）。结论：视觉可行，暂以实验开关保留
  （默认 0=关），上默认前需补"重叠带触摸重定向"和门禁的负间距断言，避免破坏已绿的 5c/5f。
- 正面卡动作行修复：`setFrontActions` 在收起态必须按 `expandT` 置 GONE（`setStackExpanded(false)`
  状态未变会提前返回，否则动作按钮露在卡顶）。
- 最终门禁 `work/z8os3-live/g-stage2b.log`：**exit=0**（5e 2 开/1 收/2 中段/2 落点，
  5f 正面卡 295px / listTop=319 / gap=24 / 5 条胶囊 / cardH==rowH==target）。
- 当前构建 sha256 前缀：``。

- 覆盖式列表已做成**设置页选项** `列表重叠覆盖`（关闭/6dp/10dp，key
  `betteroriginos_notify_row_list_overlap`，默认关闭）。实测 6dp：负间距 + Z 序反转后
  卡面正确互压、滚动逐条露出；因命中测试仍按 child 顺序，重叠带内可能点错层，所以默认关，
  由用户显式开启（摘要里写明）。
- 长按胶囊已接到该子条原生行的 `doLongClickCallback`（日志 `stack child long-press ... via=row`），
  但子行位于 GONE 的 children container，原生长按菜单未实际弹出——记为"回调已接、菜单待手指复验"。
- 门禁 `work/z8os3-live/g-stage3.log`：**exit=0**（5e/5f 全绿，重叠选项默认关闭）。
- 构建 sha256 前缀：`f17cfe72231c`。

- 展开胶囊正文改多行（标题 2 行 / 正文 3 行）+ 动作按钮，等于在展开组里直接给出"全文 + 原生动作"，
  不再需要二次展开（真机 `frontH=295 listTop=319 listH=712 items=[0x344v0 368x344v0]`）。
- 底部复验：慢速上滑可滚动到列表末尾且面板不关（一次上滑 `mCurrentFocus` 仍是 NotificationShade，
  第二次到末尾才被 overscroll 关闭）；**但最后一条仍贴屏幕底边被裁**——给 NSS `setPadding(bottom=28dp)`
  没有进入滚动范围（NSS 在自己的布局里用内部 inset 重算），需改成"给最后一行增加实际高度 /
  在列表末尾插一个真实占位行"的路线，下一轮处理。原生基线同样贴边裁剪，这不是我们独有的回归。
- 构建 sha256 前缀：`f17a9d101bb0`。

- **注入新鲜度坑（新）**：install 后第一次重启 SystemUI 可能仍加载旧 dex（探针日志缺失），
  连续重启第二次才生效。以后每次装机先找一条"本构建新增的日志"确认，再开始真机结论。
- NSS 底部留白两次尝试：`setPadding` 无效（不进算法）；`mBottomInset` 反射已写成功
  （`nss inset now=84 orig=0 extra=84`）但 OS3 的最大滚动量并不消费该字段，最后一条仍贴边；
  改为"给列表最后一行在测量出口加 28dp 透明留白"（stack 卡走 `IosNotificationRow.bottomExtra`，
  普通行走 onMeasure 出口 +`LAST_ROW_EXTRA`，并 `notifyHeightChanged`），代码已完成、
  构建通过，但**真机是否真正延长 maxScroll 尚未确认**（慢速上滑一次仍未完全露出，第二次即 overscroll 关闭）。
- 覆盖式列表：设置页选项已交付（关/6dp/10dp，默认关），视觉与滚动验证过；重叠带命中顺序待解决。
- 当前构建 sha256 前缀：`fb66743b7844`；门禁 `g-stage4.log` exit=0（该构建跑于多行+inset 改动后）。

- 底部留白第三版（当前）：给 NSS 加 28dp **底部外边距**（直接缩小列表视口 → maxScroll 必增 28dp），
  真机日志 `nss bottom margin now=84 extra=84 h=2340`，字段已生效；上一版"最后一行加高"保留 API
  但暂未标记（避免卡面多出空白）。真机滚动验证受 `input swipe` 手势干扰（起手落在行上时会被
  行/关闭手势吃掉，第二次上滑常触发 overscroll 关闭），下一轮改用更可靠的滚动注入方式
  （`input roll`/先点空白区再滑）复核最后一条是否完整露出。
- 构建 sha256 前缀：`7446109672ab`。

- **底部留白结论（本轮收口）**：padding / `mBottomInset` / 外边距都不进 OS3 的 maxScroll；
  "最后一行动态加高"能改内容高度，但行回收/重排会把它重置（实测 84→0 抖动），会随机给某张卡
  多出 28dp 空白，已回退。保留已验证的"动画结束 refreshRowLayout"（真正修好了展开后不能滚的问题）；
  最后一条贴屏幕底与原生基线一致，记为平台限制，深改需要进 NSS 的排版/缓存层，暂缓。
  新增 `betteroriginos_notify_scroll_test` 调试把手（直接 scrollTo 到底）便于后续复核。
- **门禁事故两则（经验）**：① 只 build 不 install 就跑门禁，测的是旧 APK（本轮吃了两次假 FAIL）；
  ② 装机后第一次重启 SystemUI 可能仍加载旧 dex，必须重启第二次并以"本构建新增日志"为准。
  修正后 `work/z8os3-live/g-stage5c.log` **exit=0**（2 contracted + 3 stack 共 5 行；5e/5f 全绿）。
- 当前构建 sha256 前缀：`0531e90bdc3c`（含多行胶囊、动作、点击/长按、overlap 选项；底部实验已回退）。

- 底部留白第四版尝试：把 NSS 的 `NotificationShelf` 强制 VISIBLE + 28dp 高 + alpha 0（它是参与排版的
  真实子视图，理论上能稳定延长 maxScroll）。代码已进构建，但本轮真机复核仍未拿到 `shelf spacer`
  日志（stale-dex/执行时序干扰），**未判定生效**；若下一轮确认无效应回退，避免看不见的布局副作用。
  构建 sha256 前缀：`060c7b460cf5`。

- 底部留白最终采用 **NotificationShelf 透明占位**：项目原有"收纳条折叠"钩子把 shelf 压成 0 高，
  已加"占位模式"（`sShelfSpacer`）→ shelf 保持 VISIBLE + alpha 0 + 28dp 高（真机
  `shelf height -> 84`）。shelf 是 NSS 自己参与排版的子视图，比给最后一行加高稳定（不受行回收影响），
  属于平台原生机制。门禁 `g-stage6.log` **exit=0**（6 类行覆盖，5e/5f 全绿）。
  可视复核仍受 adb 手势干扰（`input swipe` 起手落在行上会被行/关闭手势吃掉），
  已留 `betteroriginos_notify_scroll_test` 把手，待下一轮用更稳的方式确认最后一条完全露出。
- 构建 sha256 前缀：`a73e1281812a`。

- shelf 透明占位（28dp、alpha 0）经调试把手验证：`debug scroll to=244 content=2584 h=2340` 后截图
  `work/z8os3-live/shelf5.png` 显示列表末端最后一条完整可见、下方留有空白——内容高度已包含占位，
  用户手势滚到同一 maxScroll 时也能看全（此前"贴底裁切"是占位前的状态）。
  adb `input swipe` 在 x=20 起手会被误判成点通知（打开了文件管理器），**真实手势复核受环境限制**，
  改用 `input motionevent DOWN/MOVE/UP` 作为下一轮复核手段。
- 构建 sha256 前缀：`a73e1281812a`。门禁 `g-stage6.log` exit=0（shelf 占位未破坏校验器）。

- **底部留白真机闭环**：改用 `input motionevent DOWN/MOVE.../UP` 分段注入拖动（`input swipe`
  会被行手势误判），一次拖动后列表滚到末端，截图 `work/z8os3-live/me_1.png` 里最底一条
  "USB 调试已开启" **完整可见**（卡片下缘 + 28dp 透明留白都在），面板保持打开；
  越界继续上滑才触发 overscroll 关闭（原生行为）。至此"底部截断/上滑看不清"闭环。
- 最终构建 sha256 前缀：`a73e1281812a`；门禁 `g-stage6.log` exit=0。
- 本目标四项交付：① 展开组=多行胶囊+动作按钮+各自原生行点击/长按；② 点击按条路由 + autoCancel
  （`postjump` 演示 + key 日志）；③ 展开后滚动修复 + shelf 28dp 透明留白；④ iOS 覆盖式列表
  做成设置项（关/6/10dp，默认关，重叠带命中顺序为已知限制）。

## 2026-10-29 · 收纳栏"若隐若现"回归修复（shelf 占位的透明度兜底）

### 问题

- 底部留白用 NotificationShelf 透明占位后，用户报告"之前隐藏的收纳栏又开始若隐若现"。
- 根因：AOSP 的 shelf 淡入动画会在任意时刻把 shelf `alpha` 写回 1（不经过我们的 hook）；
  我们的 alpha=0 只在 NSS `onLayout` 和堆叠行 preDraw 里补写，动画期间没有逐帧兜底，
  两次排版之间就会露出一帧（甚至停住）。
- 另一隐患：占位模式原先只由"堆叠行守卫"触发，没有堆叠行时根本不进入占位状态，
  底部留白时有时无。

### 修复

- 给 shelf 挂 `ViewTreeObserver.OnPreDrawListener` **逐帧守卫**：每帧绘制前把 alpha 强制归零，
  动画写回也压得住；按 ViewTreeObserver 实例记录（换窗口重挂），不重复挂监听。
- 占位改由 NSS `onLayout` 钩子自己触发（`applyShelfSpacer(list)`），不再依赖堆叠行是否存在；
  `ensureNssBottomInset` 保留同一入口，`debugScrollBottom` 也能确定性激活。
- 新增诊断：逐帧守卫实际纠偏次数 `sShelfAlphaFixes`，调试把手输出
  `shelfVis/shelfAlpha/shelfH/shelfMeasured/shelfLp/spacer/alphaFix`。

### 证据

- `debug scroll to=244 content=2584 h=2340 shelfVis=0 shelfAlpha=0.0 shelfH=84 shelfMeasured=84
  shelfLp=84 spacer=true alphaFix=112` —— 占位 28dp(84px) 生效、shelf 不可见；
  `alphaFix` 持续增长（门禁重启后到 1495+）证明 AOSP 一直在尝试淡入、逐帧守卫每次都压住。
- 多场景截图（`work/z8os3-live/shelf_fix_bottom*.png` 4 秒 3 帧、`shelf_stress2.png`
  通知到达时）：收纳栏均未出现；列表末端最后一条完整可见、下方保留留白。
- 门禁 `work/z8os3-live/gate_shelf_fix.out` **exit=0**：5 类行覆盖、5d ghost dedup 28 条、
  5e morph 全绿、5f 多胶囊几何全绿、批量清除/媒体卡链路无异常。
- 构建 sha256 前缀：`441b0fde40ad`（安装前构建；门禁后设备保持在同版本）。

### 经验

- "结构上可见、视觉上不可见"的占位视图，必须把**视觉属性（alpha/可见绘制）逐帧兜底**在
  preDraw 层；只靠排版回调补写，会被系统动画在两次排版之间撕开。诊断时把"我们纠偏了多少次"
  打成计数器，比反复截图更能证明动画写回与守卫有效。
- 占位触发不要挂在"堆叠行"这类业务路径上，否则没有堆叠通知时机制静默失效；应挂在 NSS 自身
  的排版必经点。

## 2026-10-29 · 收纳栏圆角根因修复 + 音乐 App 闪退修复

### 一、收纳栏顶角直角（代码级根因，非截图调参）

反编译 SystemUI classes5.dex（dexdump，work/systemui/classes5.dump）逐层确认绘制链：

- 布局 `status_bar_notification_shelf.xml`：`NotificationShelf` 内 `NotificationBackgroundView(id=backgroundNormal)`
  就是那条白色收纳栏背景；`VivoNotificationIconContainer` 只画图标。
- `NotificationShelf.onFinishInflate()` 调 `requestRoundness(1,1,BASE_VALUE)`/`requestTopRoundness(1,…)`；
  背景圆角最终由 `ActivatableNotificationView.applyRoundnessAndInvalidate()`
  → `getTopCornerRadius()/getBottomCornerRadius()` → `applyBackgroundRoundness(top,bottom)`
  → `NotificationBackgroundView.setRadius(top,bottom)` 写入 `mCornerRadii[8]`。
- `NotificationBackgroundView.updateBackgroundRadii()` 再读取 `mCornerRadii`：LayerDrawable 各层
  GradientDrawable 直接 `setCornerRadii`；vivo 皮肤 `VRoundedCornerDrawable` 按
  `radii[0]>0 / radii[4]>0` 调 `setCorner(topL,topR,bottomL,bottomR)`。
- 实测 shelf 的 `setRadius(0,45)`：**top 恒为 0**（原生把收纳栏与上方通知视为连体），所以顶部
  两角布尔为 false → 直角；底部 45px(15dp) 圆角。

修复（`NotifyRowFeature.installShelfCornerHooks`）：
1. `NotificationBackgroundView.setRadius` 前置钩子：父链含 `NotificationShelf` 时把 top 参数对齐 bottom；
2. shelf `onFinishInflate/onLayout/bind` 与 NSS `onLayout` 上对现存实例补校正（覆盖钩子装载前的旧值）；
3. `NotificationBackgroundView.onDraw` 兜底：按 `mCornerRadii+drawable` 签名去重，首次绘制前把
   `mCornerRadii` 顶部写成底部半径、必要时绕过 `mDontModifyCorners` 调 `updateBackgroundRadii`，
   并直接给 GradientDrawable/VRoundedCornerDrawable 下四角；不逐帧反射、不裁剪、不加盖层。

证据：`shelf radius top 0.0 -> 45.0`、`shelf gd corners rounded r=45.0`、
`shelf draw seen parent=NotificationShelf`；像素量测顶角跨度 `106→56px`（逐行收窄=圆角），
底角 `91→51px`，与修复前顶角整行 `48..1031`（方形）对比明确。门禁 `work/z8os3-live/gate_final.out` exit=0。

### 二、音乐 App 点卡片闪退（真机复现 + 根因）

- 复现：iMusic 播放中打开通知中心点媒体卡 → `com.android.bbkmusic` FATAL：
  `RemoteServiceException$BadForegroundServiceNotificationException: Bad notification(id=10001)
  … Couldn't inflate contentViews … RemoteViews$ActionException: view:
  com.betteroriginos.feature.media.MediaCardFeature$DragSeekBar can't use method with RemoteViews:
  setVisibility(int)`。
- 根因：媒体进度条借用 `android.R.id.progress`，RemoteViews 更新通知时按 id 反射调用
  `setVisibility`；我们为对抗原生显隐而覆写了 `DragSeekBar.setVisibility(int)`，覆写方法没有
  `@RemotableViewMethod` 注解，RemoteViews 校验失败 → 系统判定发通知的音乐 App 违规并杀掉。
- 修复：删除该覆写（`MediaCardFeature.DragSeekBar`）；可见性自愈继续由 `reclaimBar()`
  在 300ms ticker 内完成（门禁：进度条绘制 13 帧、被外力改可见性 28 次均自愈）。
- 验证：点卡片进入 `.MusicMainActivity`，连续切歌 3 次、通知多次更新，进程存活、无
  BadForeground/FATAL；崩溃缓冲仅剩无关的 vivocameraserver（DSU 相机服务）。

构建 sha256 前缀：`6ccb7a2441c2`。设备设置已还原（verify/scroll/stack/overlap=0、stayon=false、暂停播放）。

## 2026-10-29 · 回退收纳栏边界实验，通知条改为「美化 → 样式」结构（测试版标记）

### 回退

收纳栏贴边问题尝试过三种“切口/隐藏/跟随”方案，真机反馈均为副作用（下限通知显示不全、底栏异常、
翻页易退出面板）。已全部回退到上一版行为（`6ccb7a2441c2` 等价）：

- 删除 `NotificationContentView.dispatchDraw` 的圆角裁剪钩子；
- 删除 `ExpandableNotificationRow.dispatchDraw` 的“收纳栏带裁剪/隐藏”钩子；
- 删除 shelf「列表末尾元素」实验（getIntrinsicHeight→0 + NSS dispatchDraw 定位）；
- 保留：shelf 顶角圆角源头修复（setRadius/mCornerRadii/onDraw）与音乐 App 闪退修复
  （去掉 DragSeekBar.setVisibility 覆写）。

### 产品结构

- 功能标题：**通知条美化**（原“通知条 iOS 化”）；
- 新增选项 **样式** → 当前唯一取值 **iOS 风格（测试版）**；
- 原生样式选项预留（后续加入，届时在样式选择里实现“恢复系统默认通知外观”）；
- 功能摘要/分组方式均标注“测试版”。

### 已知问题（测试版，后续维护）

- 收纳栏与列表的交界：原生 OS3 会让最后一条通知在栏顶露出半截并被栏遮挡；iOS 卡片风格下
  半截卡的视觉观感不佳，需从 NSS 排版层重做（当前保留原生行为）。
- 列表反复上下滑动后偶发最底部通知难以完全划出、继续上滑会触发面板收起（原生 overscroll，
  需要在滚动层处理）。
- 调试/验证口径：设置页截图见 `work/z8os3-live/settings_sysui.png`，回退后通知中心见
  `work/z8os3-live/beautify1.png`。

构建 sha256 前缀：`dabc6ecc2106`（已装机）。

## 2026-10-29 · 原生通知样式（真·AOSP 路径）+ 冒烟门禁

### 设计（最小化根治）

- 「通知条美化 → 样式」新增 `native`（原生样式（系统默认）），默认 `ios`。
- 原生样式 = **完全不安装通知条钩子**：`NotifyRowFeature.install()` 先挂 Application.onCreate
  门闩，读 `betteroriginos_notify_row_style`；native 时仅打一行声明后返回。
  restyle/堆叠/overlay/shelf 圆角/roundness/icon guard 等一律不装，通知排版与绘制 100% 走 AOSP。
- 与设置页“改样式需重启 SystemUI”的既有约定一致（样式只在进程启动时读取）。
- 媒体卡属于独立功能（media_card），不受通知条样式选择影响。

### 代码级验证（无截图验收）

- 新增只读校验器：仅当 `verify=1` 且 style=native 时临时安装，遍历 NSS 行输出
  `notify_row.native tag/media/overlay/rootAlpha/iconVis/bgAlpha`，不修改任何视图；
  生产环境（verify=0）依旧零钩子。
- 真机证据（`work/z8os3-live/native-smoke.log`）：
  - 启动日志仅 `style=native` + `native style — AOSP notification pipeline untouched`，
    无 `notify row feature installed` / `notify_row: init`；
  - 40 条非媒体行采样全部 `overlay=0`、`rootAlpha=1.0`，多行 `bgAlpha=1.0 bgVis=0`，
    原生小图标出现 `iconAlpha=1.0 iconVis=0`；
  - 无 stack/ghost/morph/verify/shelf-force 等 iOS 专属日志。
- 冒烟：`work/os3-smoke-native.sh` **exit=0**；iOS 回归 `work/os3-smoke.sh` **exit=0**
  （5d/5e/5f、媒体链全绿）。
- 设置页层级取证实测：样式弹窗同时存在「iOS 风格（测试版）」「原生样式（系统默认）」；
  点选原生后 `settings get` 返回 `native`，重启 SystemUI 后零钩子生效。

构建 sha256 前缀：`b6c0ed9d8a5a`。设备当前样式=native、verify=0。

## 2026-10-29 · 样式语义修正 + 原生优化（大图标/标题）落地

### 语义修正

- 原「原生样式」实现被证实与“关闭通知条美化总开关”等价，属冗余，已删除该取值与零钩子门闩。
- 现在的语义：
  - 总开关关闭 = 系统默认（原生）；
  - 总开关开启 + 样式 iOS = 现有卡片/堆叠（测试版）；
  - 总开关开启 + 样式「原生优化」= 保留系统卡片/背景/分组，只做 38dp 大图标 + 标题排版。

### 原生优化实现（NotifyRowFeature）

- `style=light` 分支：跳过 `applyRowRoundness`、跳过堆叠/消息 overlay/堆叠参数；
  分组摘要与分组子行一律保持原生（不重排、不切圆角），只优化独立通知。
- A13 `setTop/BottomRoundness` 钩子按样式分流：light 下不再强制 20dp
  （修复“同 app 多条通知被切圆角，与上方应用标签栏割裂”）。
- 空白行防护：标题与正文都为空的行不做 restyle，保留原生渲染（避免纯白空卡）。
- restyle 日志补充 `bigIcon=/light=` 标记，便于代码级验收。

### 冒烟（全部代码日志断言，无截图验收）

- `work/os3-smoke-light.sh` 连续两次 **exit=0**：独立通知 6~8 条完成大图标/标题排版、
  `bigIcon=1` 同数、无堆叠/overlay、`stackMode=0`、分组子行零重排、原生圆角保留、
  空内容行零接管；SystemUI pid 稳定、crash buffer 干净。
- iOS 回归 `work/os3-smoke.sh` **exit=0**（5d/5e 全绿；5f 本轮 stack toggle=0 未覆盖）。

构建 sha256 前缀：`3c1739527992`。设备当前 style=light、feature=1、verify=0。

## 2026-10-29 · 原生优化样式返工（去掉排版手术，修复空白/大间隔）

- 现象：轻量样式出现空白通知、通知间大间隔。
- 根因（几何取证）：
  1) 沿用了 iOS 的 header/标题排版手术（header 高 36dp、topContent topMargin、标题列位移），
     把原生行从 275~317px 撑到 394~468px；列表仍按原生高度排位 → 视觉大间隔/内容压空。
  2) 改动行内图标/文字后没有通知 StackScrollAlgorithm 行高变化，行距按旧缓存计算。
- 返工（真正最小改动）：
  - light 在 restyle 最前分流到 `applyLightPolish`：不挂重影守卫、不标记 RESTYLED、
    不改 header 高度/内边距/文字位置，只在原生 header 内把 CachingIconView 调到 32dp，
    并设置标题字号/字重。
  - 分组摘要/子行、HeadsUp、媒体/自定义模板行一律保持原生；标题正文皆空的行不接管。
  - 末尾 `refreshRowLayout(row)` + `contentView.requestLayout()` 通知列表重算行高。
- 几何对照（同一批通知）：轻量 279/321/573/682px vs 原生基线 275/317/569/682px（基本一致）。
- 门禁：`work/os3-smoke-light.sh` 连续两次 exit=0（12/10 条独立通知放大图标、geo=native、
  分组子行零重排、无空内容接管）；iOS `work/os3-smoke.sh` exit=0（5f 因 group_mode=separate 未覆盖）。

## 2026-10-29 · 原生优化样式间距修复（iOS stack metrics 全局污染）

- 现象：轻量样式下空白已消失，但相邻通知之间固定 25px 缝隙；原生 OS3 是连续白板。
- 取证：同一列像素白带——原生 665..2231 连续；轻量 707..983 / 1008..1286 / 1311..1589
  （每条之间 25px）。
- 根因：`installStackMetricsHooks` 在 NSS 构造/updateSidePadding/reinitView 与
  StackScrollAlgorithm.initView 上全局套用 iOS 参数（8dp 间距、16dp 缩进），只判断了
  功能开关与 stack_metrics，没有按样式分流。轻量样式虽然不画 iOS 卡，但列表间距被改成 iOS 值。
- 修复：`applyStackMetrics` 与算法 initView 钩子加 `isLightStyle` 早退；轻量样式保留
  AOSP/vivo 原生间距与边距。initFeature 增加 `light style, native list metrics/spacing` 声明供门禁断言。
- 复核：轻量像素白带恢复为 665..2231 连续（与原生一致）；`work/os3-smoke-light.sh`
  连续两次 exit=0（新增“原生列表间距/边距”断言，8~12 条独立通知放大图标）；
  iOS `work/os3-smoke.sh` exit=0。

构建 sha256 前缀：`468624aa939d`。设备 style=light、feature=1、verify=0。

## 2026-10-29 · 原生优化样式：大图标显示优化

- 原生 header 会把图标高度压到 26dp（实测 CachingIconView 114x78px，改 LP 只加宽不加高），
  直接设 38dp 会得到“扁图标”。改为按实际绘制尺寸等比缩放并保持 header 不动：
  - LP 宽度设 38dp（保证槽位宽度）；
  - post 后按 `target/实际绘制尺寸` 设置 scaleX/Y（真机 114/78=1.4615），pivot 居中，
    再按 header 高度垂直居中（translationY=-24px）；
  - 视觉结果 38×38dp、header 内上下各 6px 余量，行高与列表间距完全不变。
- 仅 read-only 探针记录 iconW/H、scale、ty、header/row 高度（verify=1 时）。
- 复核：`work/os3-smoke-light.sh` exit=0；像素白带仍为连续 665..2231（间距未回退）；
  `work/os3-smoke.sh` exit=0。

## 2026-10-29 · 0.1.1-beta 发布

- 版本：versionCode=2，versionName=0.1.1-beta。
- 成品：`out/BetterOriginOS-0.1.1-beta.apk`（不是工作树构建目录，便于分发）。
- 冒烟（全部 exit=0）：
  - 原生优化样式：`work/z8os3-live/gate_011_light.out`
  - iOS 样式（separate）：`work/z8os3-live/gate_011_ios.out`
  - iOS 样式（ios_stack，补 5f 多胶囊）：`work/z8os3-live/gate_011_stack.out`
- 设备已还原为 light 样式、group_mode=separate、verify=0。

## 2026-10-30 · 离线修复：iOS 空白卡 + 原生优化大图标改为独立 38dp 应用图标

### iOS「完全分离」空白通知（代码级根因）

- `applyMessagingOverlay` 接管消息行时把原生收起根压成 `root.alpha=0`；
  但 `dropOverlay()` 只移除自绘覆盖层，**从未把 root.alpha/visible/a11y 还原**，
  `OVERLAY_ROOTS` 也只有 put、没有消费。行被复用或覆盖层被摘掉后，原生内容保持
  alpha=0，只剩行背景 → 纯白空卡。
- 修复：
  1) `dropOverlay()` 摘卡时按 root 还原 alpha=1 / VISIBLE / a11y=AUTO；
  2) 会话标题与正文都为空时不接管（dropOverlay 后保留原生渲染），避免自绘空卡。

### 原生优化大图标（此前实现错误）

- 旧实现拿 header 左上角的 `CachingIconView` 等比放大。探针证实它是原生小图标，
  且 header 将其高度压到 78px（26dp），放大后既不是应用大图标、也非正方形。
- 新实现：在原生 header 的图标槽位内放一个独立的 38dp `ImageView`：
  - 使用 `PackageManager` 的应用图标（与 iOS 卡同一来源）；
  - 原生小图标 alpha=0 但保留槽位宽度（LP width=38dp），header 文字位置不动；
  - 圆角 10dp（clipToOutline）、可点击/长按（转发行原生点击与菜单）；
  - 布局后按 header 居中定位；行不被判定为可优化时自动摘除并还原小图标。
- 构建通过（离线），真机门禁待设备空闲后执行。

## 2026-10-30 · 离线审计补充（孤儿守卫 / 图标槽位还原 / 每帧兜底）

- iOS 再次排除一类空白风险：`guardStack` 的 preDraw 守卫没有“卡片是否还在”的成员判断，
  且 VTO 变化时按新实例摘监听会失败，可能留下孤儿守卫继续把复用行压成 alpha 0。
  修复：记录 guard 实际挂载的 VTO（STACK_GUARD_VTO），换 VTO 时按真实实例摘除并重挂；
  守卫每帧先检查 `STACK_OVERLAYS` 与 overlay parent，卡片不在就不再改动视图。
- 原生优化大图标：
  - 修改原生小图标槽位宽度后记录原 LP，行不再符合条件时恢复宽度/高度，避免 header 文字长期偏移；
  - 大图标定位改用屏幕坐标（不再假设小图标是 header 直系子视图），未 attach 时回退相对坐标；
  - 增加原生小图标 alpha 的逐帧兜底，防止原生更新把它写回 1 后与小图标重叠。
- 复核：`dropOverlay` 已还原 alpha/visible/a11y；`guardOverlay` 原实现已有成员判断，无需改；
  `dropStackOverlay` 原本已还原 root alpha/背景。
- 构建通过（离线）；真机 light/iOS 门禁、iOS 空白复现待设备空闲。

- 追加：展开子视图不再走轻量 polish（`currentExpanded(contentView)==root` 直接返回），
  避免展开态叠加第二枚大图标；展开布局完全原生。构建 sha256 前缀：`2680ac1b723a`（离线，未装机）。

## 2026-11-02 · 主系统 OS5（A15）上的通知条美化适配

- 环境澄清：Z8 主系统已是 OriginOS 5 / Android 15（sdk 35），此前门禁均在 OS3 DSU 上跑；
  10-30 的离线修复这次首次装机验证。
- light（原生优化）的原生差异：OS5 header 42dp、小图标 16dp、标题从 12dp 起排。
  原实现把 38dp 图标叠在原生图标位置，观感只是"把左上角图标放大"。
- OS5 light 新布局：
  - 隐藏原生 header 小图标；彩色应用图标放左侧 16dp，标题/正文/应用名统一 66dp 列；
  - 展开行、自动归组摘要/子行都走同一套（`ranker_group` 空正文摘要不再跳过）；
  - 标题上提：header 42→34dp，找到真正带 topMargin 的文字容器减 8dp；
    不能用 translation，否则标题顶进 header 被盖住、收起态缺字；
  - 图标 42dp、圆角 11dp。
- 展开/收起图标飞行：复用 iOS 的 morph（预建 flight、240ms spring），
  light 只在 OS5 安装该守卫；日志实测 126px 图标 `y=71 → 119` 连续飞行，两侧 alpha=0。
- 版本门槛：以上均走 `iosColumn`（SDK>=34），OS3 light 保持原样。
- 证据：`work/z8os3-live/ui_final.xml`（header 底部=标题顶部：540/820/1370/1650/2185）；
  morph 日志 `from=48,71x126 to=48,119x126`；装机 sha256 `650ff4cd4539…`
  （style=light, verify=0）。
- 备注：校验开关是 `betteroriginos_notify_verify`（不是 `..._row_verify`）。

## 2026-11-02 夜 · 通宵任务：方向改为「OS5 → OS3 移植」+ 新增 A11 音乐卡片移植研究

用户指令（原话要点，已建 goal 1793606895395-708aad）：

1. **方向纠正**：不是把 OS3 逻辑搬到 OS5，而是**把 OS5 的通知逻辑移植到 OS3**。必须先代码层逆清楚再动手，
   重点三处：**OS5 通知条的圆角处理**、**收纳栏（NotificationShelf）的渲染逻辑**、**一键清除按钮的差异**。
2. **禁止截图验收**（全程适用）：不得用 screencap/观感判对错；只认 uiautomator dump 的 bounds/resource-id、
   模块内度量日志、dexdump/jadx/apktool 解出的类-字段-dp 值。
3. **新增第三台设备**：vivo Y7s `IBR8GEMFJNBECIFA`（OriginOS Ocean / Android 11 / sdk 30，无 root、无 su，
   SystemUI.apk 102MB 是 world-readable）。要求研究让模块把 **A11 的音乐播放卡片样式移植到 OS3 与 OS5**，
   必须从代码逆出：封面排版、取色逻辑、控件样式与排布，并一比一还原。屏锁 PIN=114514（用户授权自行解锁）。
   **OLED 烧屏保护**：默认熄屏、亮度调低（20）、超时 15s、单次亮屏 <60s、绝不 `svc power stayon true`；
   该机当时电量 18%。

### 今晚的取证与执行编排

- 后台逆向代理 A（os5-to-os3-re）：产出 `out/LOG-1102-OS5-TO-OS3-ADAPT.md`（圆角/收纳栏/一键清除三节，
  每节 OS5 实现 → OS3 现状 → 差异 → 移植方案 → 开放问题）。素材：`work/systemui/`（OS5 apktool `res/`、
  `classes3.dump`/`classes5.dump`、`out-src/`）、`work/framework-res-os5.apk`、`G:\os3-ref\`
  （OS3 apk + 214MB `os3-dexdump-f.txt` + `jadx/`）。
- 后台逆向代理 B（a11-mediacard-re）：产出 `out/LOG-1102-A11-MEDIACARD-RE.md`（载体判定 → 封面规格表 →
  取色算法 → 控件规格表 → 两态差异 → 在 A13/A15 的落点映射 + 移植可行性/风险 + `A11_MEDIA_*` 常量表建议）。
  设备证据落 `work/y7s-a11/`。
- 主线（我）：复核工作区里未提交的「通知分割」WIP（`NotifyRowFeature.java` +378 行），它目前
  **只在 A13-or-lower 生效**（`applyLightSeparation` 在 :1411 对 A14+ 直接 return，选项注册在 :207 也被
  `isA13OrLower()` 关着），与本次「OS5→OS3 移植」高度重叠，等逆向结论回来一并设计。

### 环境事实（今晚核对，别凭记忆）

- Z8 `10AE4Y0MDE000BL`：**宿主 OriginOS 5 / sdk 35**，V2314A，当前 mWakefulness=Asleep；
  模块 base.apk 已装在宿主（`/data/app/~~KiIwIfy9PUQnhOYpUkEogA==/com.betteroriginos-.../base.apk`）。
- **OS3 DSU 当前不存在**：`ls /data/dsu` → No such file；`dumpsys user | grep -i dsu` → 空。
  ⇒ 「点 DSU 进 OS3」**需要全新安装 DSU**（会重置上一轮 DSU 会话里的模块与 LSPosed 配置）。
  通宵期间**不自行动 DSU**：这是共享主力机、且是不可逆的槽位操作，留给用户当面确认。
  OS3 侧今晚只做静态逆向；需要真机门禁的 OS3 改动一律先写完、构建通过、等用户上机。
- 磁盘：C: 剩 74G、G: 剩 127G，够用。

### 今晚状态跟踪（供压缩后接续）

- 任务 #1 OS5 三处逻辑逆向（in_progress，代理 A）
- 任务 #2 OS3 移植实现（pending，等 #1 结论；改完必须复跑 `work/os3-smoke.sh` 全套）
- 任务 #3 A11 音乐卡片逆向（in_progress，代理 B）
- 任务 #4 A11 卡片移植到 OS3/OS5 实现（pending；OS5 侧可今晚在宿主真机验证，OS3 侧待 DSU）
- 未提交改动：`module/res/values/strings.xml`、`module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java`、
  `module/stubs/de/robv/android/xposed/XposedHelpers.java`（新增 `getFloatField` 桩）；HEAD=aee3820。

### 主线复核：「通知分割」WIP 的字段与类型已在 OS3 dexdump 上核实

- `NotificationStackScrollLayout.mPaddingBetweenElements` = **I（int）** → WIP 用 getIntField/setIntField 正确；
- `StackScrollAlgorithm.mPaddingBetweenElements` = **F（float）** → getFloatField/setFloatField 正确；
- `NotificationShelf.mPaddingBetweenElements` = **I（int）** → **WIP 没动收纳栏**，这是移植 OS5 时必须补的一处。
  证据：`G:\os3-ref\os3-dexdump-f.txt` 行 1714402（算法 F）/ 2050289（NSSL I）/ 2437979（Shelf I）。
- WIP 里的圆角来源是**启发式**：`nativeCardRadius()` 读首个子行 `mBackgroundNormal.mCornerRadii` 取最大值，
  再钳到 12~28dp，读不到就 15dp（`NotifyRowFeature.java:1528-1558`）；恢复圆角时读不到原生 top/bottom
  roundness 就按"首行顶圆角/末行底圆角"猜（:1571-1599）。**这正是用户要求用 OS5 逆向真值替换掉的东西**，
  等代理 A 的圆角规格回来改成常量表 + 逐字段兜底，别留猜测值。
- 基线构建：`module/build/BetterOriginOS.apk` 262,562B，sha256 `88bddd4440861ff0…`（含未提交 WIP），
  构建日志 `work/build-1102-night.log`（exit=0）。

### OS5 宿主当前状态（只读核对，今晚未改动设备）

- `betteroriginos_feature_notify_row=1`、`notify_row_style=light`、**`notify_row_sep=split`**、
  `notify_verify=0`、`feature_media_card=1`。sep 在 OS5 上被 `isA13OrLower()` 关着，所以这条设置当前无效，
  但**跑 light 门禁前要先置回 off**——门禁有一条"轻量样式保留原生列表间距/边距"的断言，
  一旦这段 WIP 放开到 A15，split=8dp 间距会和该断言冲突，需要新的 split 专用门禁。
- 注入确认：`/proc/$(pidof zygote64)/maps` 里 zygisk|lsposed 命中 6 条（真注入了）。
- 宿主上已装的 base.apk 大小 262,562B、sha256 前缀 `d7cb96c5c4843a16`（与今晚基线同尺寸、不同哈希，
  = 同一份源码的另一次构建；取文件按 `pm path` + `exec-out cat`，不过 su 管道）。
- 媒体卡现状（供 A11 移植落点参考）：`feature/media/MediaCardFeature.java` 2015 行，
  style 选项只有 `os5`（封面软化为背景，圆角 24dp，固定强调色）与 `material`（`dominantHue` 色调渐变，
  圆角 28dp）；取色是自研 HSV 直方图（`dominantHue(Bitmap)` :1343 → `applyPalette` :1493），
  **没有用 AndroidX Palette**，也没有按封面亮度决定文字色的逻辑 —— A11 的取色一比一还原要新增第三条 style 分支。
- OS5 framework-res 里**没有** notification 圆角 dimen（只有 `notification_action_button_radius`），
  说明 OS5 通知圆角来自 SystemUI 自带 dimen 或 vivo overlay/代码计算，具体等代理 A 定位。

### 主线补做：A13 落点能力清单（与代理 A 的"OS5 怎么算"互补），落盘 `out/LOG-1102-A13-LANDING.md`

关键取证（全部来自 dexdump 引用计数与类清单命中，无截图）：

- 圆角管线：**A13 只有行级入口**（`setTopRoundness`/`setBottomRoundness` 各 4 处引用，`applyRoundness` 仅 3 处），
  A15 是完整管线（`applyRoundness` 34 处、`mBackgroundNormal` 60 处、vivo `VRoundedCornerDrawable` 14 处）；
  **A13 的 SystemUI dex 里 `VRoundedCornerDrawable` 0 引用** ⇒ 移植不能搬算法，只能在 A13 的两个 setter 上做，
  圆角真值必须由 A15 逆出来后当常量用。A13 的 `mBackgroundNormal` 声明在 `ActivatableNotificationView`
  （`os3-dexdump-f.txt:2422703`），所以 WIP 的行背景反射链成立。
- 收纳栏：**A15 没有删掉 `NotificationShelf`**（`classes5.dump:107722` 起有实体类），A13 有
  `NotificationShelf.mPaddingBetweenElements`（int，:2437979）⇒ 收纳栏间距在 A13 可直接写字段，WIP 漏了这条。
- 一键清除：**A13 没有 A15 的 vivo 动画类 `NotificationClearAllImageView`**（A13 命中 0，A15 命中 6，
  带 `startClearAllButtonAppear/Disnimator`）；A13 能落的只有 `SectionHeaderView.mClearAllButton`（`ImageView`）
  与 `FooterView.mClearAllButton`（`FooterViewButton`）⇒ OS5 的清除按钮观感要在 A13 **自绘复刻**，
  且"运行时到底渲染哪一枚"属于必须等真机 dump 才能定的 `[待核]` 项。
- 圆角 dimen 实测（`aapt2 dump resources`，两版 APK 都没有 `res/values/*.xml`，值只在 `resources.arsc`）：
  `notification_corner_radius` A13/A15 **都是 12dp**、`notification_material_rounded_rect_radius` 都 12dp、
  `notification_scrim_corner_radius` 都 32dp；唯一共有值差是 `notification_shadow_radius` **A15=18dp / A13=17dp**，
  且 **A15 独有一族** `notification_shadow_radius_default=15 / _sharp=12 / _mellow=24 / _smooth=24`（A13 全缺）。
  ⇒ OS5 的圆角观感差异要按"**分档策略**"去逆（哪张卡/哪个角用哪一档），不是改基线数值；
  另外 OS3 上实测过的 shelf 半径 45px@3.0=**15dp** 恰好等于 A15 的 default 档，说明数值层面两版可能本就一致，
  差异在管线。详见 `out/LOG-1102-A13-LANDING.md` §1b。此坑已写入 skill §6。

## 2026-11-02 深夜 · OS5→OS3 移植第一批（代码完成 + 构建通过，真机验证待用户上机）

### 逆向结论（代理 A 主报告 `out/LOG-1102-OS5-TO-OS3-ADAPT.md`，主线复核见其 §五）

- 圆角三段流水线：基准半径 `ExpandableOutlineView.initDimens()` → A15 用
  `VivoColorAndCornerPicker.getNotificationDefFillet(ctx, level)`（0→12dp / 1→18dp / 2→24dp / 3→24dp / 其它→18dp，
  档位来自 vivo 主题服务 `ThemeIconManagerProxy`，**不是 Settings.Global**）；系数 `Roundable/RoundableState/SourceType`
  多源取 max；唯一写入点 `NotificationBackgroundView.setRadius(top,bottom)` → `mCornerRadii` → `VRoundedCornerDrawable`。
  `mBottomPadding>0` 时原生强制 bottom=0。
- **三条主线更正（推翻/修正代理结论，均有代码证据）**：
  1. **G2 连续角在 OS3 可移植**：`VRoundedCornerDrawable.draw()` 默认分支是 `mDrawG2RoundRectRadii=false →
     canvas.drawPath(G2 path)`，纯 Java；原生 `Canvas.drawG2RoundRect` 只是显式打开时的加速分支。仓库里
     `vivo-OriginUI/ref/framework-src/.../com/vivo/common/utils/G2CornerUtil.java` 与 A15 的
     `com.originui.core.utils.G2CornerUtil` 公式逐字节一致（`newRadius=1.3r`、25°/40° 弧、`0.55r`/`0.01r` 控制点、
     `2.6r>min(w,h)` 回落普通圆角）；半径换算 `getG2Radius/getDefaultRadiusByG2` 在本 flavor 是恒等；
     从 `mCornerRadii` 还原 G2 时取四角 max、`radii[i]>0` 决定该角开关 ⇒ **不需要我们重新裁决哪一角该圆**。
  2. **A13 同样有半径通道与自绘路径**：A13 `initDimens()` 读 `picker.getFillet()`，`-1` 时回落
     `notification_shadow_radius`（A13=17dp）；行轮廓由它自己的 `getRoundedRectPath(...)` → `path.addRoundRect`
     画出（裁剪路径 `mClipPath` 同一函数）。这解释了 OS3 实测 shelf=45px(15dp)：那是主题 fillet，不是 dimen。
     另外 A13 `onSystemFilletChanged(int,int)` 会把 `mOutlineRadius` 重新写回主题值，**钩子必须同时拦它**。
  3. **A13 的 picker 没有 `getNotificationDefFillet`**（只有 `getFillet/getFilletLevel`）⇒ 档位表由我们给。
- 收纳栏：两版视图树都健在（vivo 未跟 A15 的 Compose collection header）。差异＝OS5 顶底都圆
  （`onFinishInflate` 里 `requestRoundness(1,1)`）+ 内联文字按钮被 `setDismissVisible` 无条件 `setVisibility(8)`
  藏掉 + `updateShelfWhenDismissAll→animateShelfDisappear`（250ms、PathInterpolator(0.33,0,0.67,1)）；
  OS3 是 `setTopRoundness(0)`（顶直角）+ `mPaddingBetweenElements=0` + 内联粗体 12sp 文字按钮可见。
- 一键清除：OS5 是 `status_bar_expanded.xml` 里的 `NotificationClearAllImageView`（46dp、padding 2dp、
  marginBottom 40/62/90dp、center|bottom、`BlurMaskFilter(45f,OUTER)`+`#1aeeeeee`、按下 0.9 缩放 200ms
  PathInterpolator(0.25,0.45,0.3,1)、出现/消失 alpha 150ms）；**A13 没有这个类**，且
  `vivo_notification_clear_all_size/_padding`、`drawable/vivo_ic_notification_clear_all`、
  `color/vivo_notification_clear_all_shadow`、`dimen/vivo_notification_paddding_between_elements(7dp)`
  在 A13 **全部不存在**（aapt2 逐项对照）⇒ 图标随模块打包。点击链路两版同为 `clearNotifications(0,z)`，
  **不碰 `getGroupKey`**（既有 NPE 崩溃循环坑）。
- 列表间距：A15 `NSSL.initView` 用 `vivo_notification_paddding_between_elements = 7dp`（不是之前猜的 8dp）。

### 本轮代码改动

- 新增 `module/src/com/betteroriginos/feature/notify/Os5Mirror.java`：OS5 规格常量表 + `g2Path(...)`（vivo G2 公式等价实现）
  + 钩子：`initDimens`/`onSystemFilletChanged` 后写 `mOutlineRadius` 并 `applyRoundness`；`getRoundedRectPath` 后把
  `addRoundRect` 换成 G2 路径；shelf `onFinishInflate/bind` 写 `mPaddingBetweenElements=7dp` + `setTopRoundness(1.0f)`；
  shelf `setDismissVisible` 驱动浮动清除按钮显隐、藏内联文字、点击转接原生 `performClick()`、清除后按 OS5 参数让整栏消失。
- 新增 `module/res/drawable/bo_ic_clear_all_os5.xml`：与 OS5 原矢量**字节相同**（`cp` 后 `cmp` 校验过；
  手抄 pathData 会改错，别手工转录）。
- `NotifyRowFeature`：选项 `sep` 从 `off/split` 改为 `off/os5`（文案「OS5 通知观感 / OS5 复刻」）；删掉启发式的
  `nativeCardRadius`（钳 12~28dp、兜底 15dp）、`applySplitOnLayout`、`roundness/restoreRoundness` 的"首行顶圆/末行底圆"
  猜测、`SPLIT_ORIG_ROUND`/`sSplitRadiusPx` 状态、`installRoundnessHooks` 里对 light 的强制圆角；间距改 7dp 常量。
  **理由**：A13 原生已给出正确的系数分布，我们不该再钉系数，只该改半径与形状。
- 构建：`module/build/BetterOriginOS.apk` 266,740B，sha256 `2e4275ddef3c…`，日志 `work/build-1102-os5mirror.log`（exit=0）。

### 状态与待办（压缩后接续用）

- 未做真机验证：Z8 宿主 kill SystemUI 会进安全锁且我们无法解锁；OS3 DSU 镜像当前不存在，需用户确认后才装。
  light/iOS/OS5 复刻门禁全部欠跑。新门禁要覆盖：`mOutlineRadius==dp(18|12|24)`、`getRoundedRectPath` 输出为 G2
  （可用四角采样点与圆角矩形采样点差异断言曲率过渡）、shelf `mPaddingBetweenElements==dp(7)`、
  浮动按钮存在且 bounds=46dp@center|bottom、内联文字 GONE、点击后无 crash 且通知全清。
- 下一条线：A11 音乐卡片移植（`out/LOG-1102-A11-MEDIACARD-RE.md`）。A15 可 1:1（钩
  `NotificationContentView.setContracted/setExpandedChild`）；A13 通知栏不可达 ⇒ 只能落 SystemUIPlugin 的
  QS 磁贴 `ControlCenterMediaPlayerView`，几何近似。A11 真机缺口：放歌时 `dumpsys notification --noredact`
  的布局名与 uiautomator bounds（代理 B 因解锁流程异常没拿到）。

## 2026-11-02 深夜 · A11 音乐卡片移植第一批（OS5 侧代码完成，OS3 侧等可达性结论）

代理 B 报告：`out/LOG-1102-A11-MEDIACARD-RE.md`（259 行，纯静态，未截图）。

### 载体与规格（要点）
- A11 的媒体卡是**两条 AOSP 链并存**：收起态 = 着色 MediaStyle RemoteViews
  （framework `notification_template_material_media`，root `MediaNotificationView`，封面 `@android:id/right_icon` 36dp/CENTER）；
  展开态 = SystemUI 原生 `media_view.xml` + `MediaControlPanel`/`PlayerViewHolder`
  （封面 `R.id.album_art` = `qs_media_album_size` **52dp**，圆角取主题 `android.R.attr.dialogCornerRadius`，
  `setRoundRect` + `clipToOutline` + bounds 撑满后居中偏移 ⇒ 视觉 centerCrop；无封面 GONE，回落 `ic_music_note`）。
  bbkmusic 没有自研媒体通知布局，SystemUI 里也没有 `vivo.*music` 通知类。
- 取色两套（都已逐行逆出并实现）：
  1) 收起态着色 `MediaNotificationProcessor.processNotification`：**左半幅** Palette 出背景
  （`findBackgroundSwatch`：dominant 是白/黑才改挑非黑白里 population 最大的），
  **右 60%** 出前景（排除与背景同色相 ±10°、排除白黑，按背景明暗在 darkVibrant/vibrant/darkMuted/muted/dominant
  或 light 系里择优，兜底纯黑/纯白），阈值 0.08/0.9、`MIN_SATURATION_WHEN_DECIDING=0.19`、
  `RESIZE_BITMAP_AREA=22500(150^2)`、`POPULATION_FRACTION_FOR_DOMINANT=0.01`、`..._WHITE_OR_BLACK=2.5`，
  最后 `setColorPalette(bg,fg)` + `ImageGradientColorizer` 给封面叠渐变。
  2) 原生卡背景 `MediaDataManager.computeBackgroundColor`：复用同一 Palette，
  **HSL 亮度锁 0.25（恒深色）、饱和度 ×0.8、极亮(<0.05)/极暗(>0.95)去饱和、无图兜底 0xFF444444**；
  背景本体是 `IlluminationDrawable`（`qs_media_background`，cornerRadius=?dialogCornerRadius，
  highlight=15⇒0.15，`setBackgroundColor` 时按 ±0.15 生成高亮色并以 370ms FAST_OUT_LINEAR_IN 过渡）。
- 控件：`action0..4` 48/48/**52**/48/48dp（中间播放键 52dp），SeekBar 轨道 **3dp**
  （禁用 1dp）、上下 padding 16dp、`#c0ffffff` 进度 / `#80ffffff` 轨道 / 白 thumb；
  标题 16sp 白、艺名 14sp `#99ffffff`、时间 14sp 白、app 图标 16dp 白、chip 内边距 12/6dp、边框 `#26ffffff`。

### 本轮代码改动
- 新增 `module/src/com/betteroriginos/feature/media/A11Palette.java`：A11 取色的等价实现
  （5bit/通道直方图量化 + 每桶均值/计数 + AOSP 的 dominant/vibrant/muted 族判定 +
  `findBackgroundSwatch`/`selectForegroundColor`/`computeCardBackground`/`illuminationHighlight`）。
  常量与阈值逐条对齐反编译结果，类注释里写明了"为什么不用 androidx.palette"。
- `MediaCardFeature`：style 增加第三项 `a11`（选项名「Android 11 原生卡（Ocean）」+ 预览 drawable
  `res/drawable/preview_a11.xml`）；预览请求白名单加 `a11`；`applyPalette` 走 A11 取色链
  （背景 = 左半幅 dominant → computeCardBackground，圆角 = 运行时解析 `dialogCornerRadius`；
  文字/进度按 A11 白色系固定阶）；`build()` 的封面 52dp、标题 16sp 非粗、艺名 14sp、卡片圆角同 dialogCornerRadius；
  `supportNoteRes` 在 OS3+a11 时给出"OS3 面板不显示媒体通知"的说明（不留静默失效的开关）。
- 构建：`module/build/BetterOriginOS.apk` 270,910B，sha256 `38b20eaae0b4…`，日志 `work/build-1102-a11.log`（exit=0）。

### OS3 侧的前置问题（进行中）
A11 卡片在 OS3 无法 1:1，因为**媒体通知行本身进不了 OS3 的面板**（vivo 侧过滤；
OS3 只有 QS 的 134dp 磁贴 `ControlCenterMediaPlayerView`，在 SystemUIPlugin ClassLoader）。
已开后台代理 `os3-media-shade-re` 静态逆"到底哪一层挡掉了 MediaStyle 通知 + 可落地的钩点清单"，
产出 `out/LOG-1102-OS3-MEDIA-SHADE-RE.md`。**在那份结论之前不在 OS3 上动媒体卡代码**（避免做半套）。

### 今晚设备边界（再确认一次，别在压缩后误判）
- Z8 宿主 kill SystemUI ⇒ 安全锁且我们无法解锁 ⇒ 今晚不做任何需要重启 SystemUI 的验证；
- OS3 DSU 镜像不存在 ⇒ 需要用户确认才能全新安装；
- 所以两条线都是"代码 + 离线构建通过"，门禁全部欠跑，明早用户上机补。

## 2026-11-02 深夜 · 验收器与门禁（代码级，已做正负对照）

- 新增 `work/verify_os5_mirror.py`：解析模块度量日志并断言
  `radius px == 档位dp×density`（0→12/1→18/2|3→24）、`g2 path` 出现且四角非全 0（并识别
  `2.6r > min(w,h)` 的普通圆角回落）、`shelf gap == 7dp×density 且 topRoundness=1.0`、
  `clear-all size == 46dp、marginBottom == 40|62dp`、
  `a11 palette base` 的 **HSL 亮度必须锁在 25±0.6** 且 highlight != base。
- `--selftest` 跑正例 + 5 条反例（档位算错 / 收纳栏间距没写 / 按钮尺寸错 / 亮度没锁住 / 高亮与底色相同）：
  **exit=0，6 行结果全对**（`work/verify-selftest.out`）。
  这一轮自我校验真的抓出问题：最初的正例用色 `#3F4F5F` 亮度是 31%（不是 25%），
  以及一条反例因替换目标字符串失效而"没被抓住"——正是 skill 要求的"新断言必须配正负对照"的价值所在。
- 新增 `work/os3-smoke-os5mirror.sh`：A 相 `sep=os5` 跑度量断言，B 相 `sep=off` 做负对照
  （断言"关闭后不能出现任何 os5_mirror 日志"），再加批量移除后的 pid/crash 检查；
  沿用现有纪律（连续重启两次 SystemUI、慢滑、`svc power stayon` 收尾复位、验证开关用
  `betteroriginos_notify_verify`）。**该脚本今晚无法执行**（OS3 DSU 不存在、Z8 宿主重启 SystemUI 会锁死）。
- `Os5Mirror` 的度量日志改为**实时读** `betteroriginos_notify_verify`
  （`NotifyRowFeature.verifyEnabled(ctx)`），不再用 install 时的快照——否则明早先开 verify 再重启就看不到日志。
- 构建：`module/build/BetterOriginOS.apk` 270,910B，sha256 `5cd854819661…`（`work/build-1102-verify.log` exit=0）。
- 遗留：宿主上 `betteroriginos_notify_row_sep` 现存值是旧的 `split`；新代码只认 `off|os5`，
  未知值按 `off` 处理（不会崩），明早在设置 App 里重设即可。

## 2026-11-02 深夜 · 取色链回到真身 + 两个离线验证器（抓到真 bug）

### 新的源码证据（不需要设备）
- 从 Y7s 的 `work/y7s-a11/framework.jar` 里 jadx 出 `com.android.internal.util.ContrastColorUtil`
  → `work/y7s-a11/tmp/ContrastColorUtil.java`。配合 `MediaStyle.ensureColors`
  （`work/y7s-a11/tmp/MediaStyle.java:2473-2516`）才看清 A11 收起态着色行的文字色**不是**
  直接拿前景色用，而是：
  - `findContrastColor` = 在 **CIE-LAB 的 L\*** 上向黑方向二分 15 次（`ContrastColorUtil.java:198-225`）；
  - `findContrastColorAgainstDark` = 在 **HSL 亮度（[0,1] 标度）** 上向白方向二分（同 249-274）；
  - `changeColorLightness` 也在 **LAB** 上做 L\*±amount（同 329-335），不是 HSL；
  - `calculateLuminance/Contrast` = 线性化 sRGB 的 XYZ-Y 与 WCAG 公式，半透明前景先合成（同 413-425、474-495）；
  - `ensureColors` 的次序：对比度不足 → secondary=二分色、primary=secondary∓20/10；
    对比度够 → primary=fg、secondary=fg 的 L\*±20/−10，再不合格就回推 primary。
- 我第一版按"alpha 叠到背景"猜写过一次，**不匹配真身**；已按源码重写（LAB+HSL 两条二分都在
  `A11Palette` 里），并把 `MediaCardFeature` 的 a11 分支拆成两态：
  收起态用 `colorizedPair + colorizedTextColors`（明暗可翻转），展开态用 `computeCardBackground`
  （恒深 L=25%）+ 固定白色阶；封面几何也按两态分开（36dp CENTER vs 52dp dialogCornerRadius+clipToOutline）。

### 离线验证器（今晚唯一能真跑的验收）
1. `work/a11_math_selftest.py`：Python 独立复刻同一套数学（colorsys + 手写 LAB），断言
   WCAG 基准值（黑白=21、同色=1）、LAB↔sRGB 往返自洽、卡片背景恒 L=25%、达标分支保持 fg 原样、
   未达标分支"校正一定有改进"、明/暗两条分支都被覆盖 → **exit=0 全绿**。
   过程中两次纠正了我自己的断言：AOSP 的二分末端本来就允许留欠差（实测能到 3.5），
   所以不该断言理想化的 ≥4.5，而该断言单调改进。
2. `work/a11_crosscheck.py`：**把模块里真实编译出来的 `A11Palette.class` 当被测对象跑**
   （javac/java + android.jar 的 classpath，`work/tmp-re-a11/A11Probe.java` 做 stdin/stdout 桥），
   与参考实现逐值比对 3005 组（含 5 组极端/边界固定样本）→ 最大通道累计偏差
   primary=4 / secondary=3 / card=1，容差 4 内 → **exit=0 全绿**。
   这个交叉验证当场抓出一个真 bug：我的 `colorToHsl` 把饱和度分母写成 `2-delta`
   （正确是 `2-cMax-cMin`），会让所有 A11 背景色偏色；修完偏差从"整条颜色跑偏"降到舍入量级。
3. `work/verify_os5_mirror.py --selftest` 仍全绿（正例 + 5 反例）。
- 构建：`module/build.sh` exit=0（`work/build-1102-a11d.log`）。

### 仍然欠着的（别在压缩后当成已完成）
- 两条线的**真机门禁全部没跑**：Z8 宿主 kill SystemUI 会进无法解锁的安全锁；OS3 DSU 镜像不存在（需用户同意重装）。
  所以 OS5 复刻的实际观感、A13 上 `ExpandableOutlineView.getRoundedRectPath` 是否真的被我们改写、
  浮动清除按钮是否落在 46dp@center|bottom、A11 卡的实际 bounds，都还是"代码写完 + 离线数学验证过"，
  **不是"设备验证过"**。
- A11 收起态的字号/行位在报告里没有代码级数值来源（AOSP 模板 dimen 未取到），几何按现有卡片排版，
  标 `[待真机核]`，没有编数字。
- OS3 媒体通知可达性（`os3-media-shade-re` 代理）结论未到，OS3 侧 A11 卡未实现。

## 2026-11-02 深夜 · 两处"前提级"更正 + G2 的路径级验证

### 更正一：OS3 **有**通知中心媒体卡（旧结论写反了，且已经污染到代码与 skill）
代理 C 报告 `out/LOG-1102-OS3-MEDIA-SHADE-RE.md` 用四条独立证据（管线过滤器穷举 + 反编译 + 运行时日志 + 既有 dump）确认：
- OS3 解锁态下拉面板**本来就显示** MediaStyle 行；我自己复核了运行时证据
  `work/z8os3-live/20261028-1408-stack.log`：`media bind n=1 cls=com.android.internal.widget.MediaNotificationView pkg=com.android.bbkmusic`
  与 `restyling style=os5 MediaNotificationView title=2025全中文DJ舞曲…` ⇒ 现有落点在 OS3 就是命中的。
- 唯一在列表管线里掐它的是 vivo 自研 finalize filter
  `com.vivo.systemui.statusbar.notification.KeyguardNotificationManager$1.shouldFilterOut()`，
  **只在锁屏态 `StateUtils.mState==1` 生效**；i音乐 id 10001/10002 还被写死进黑名单。
- 反向陷阱：AOSP 的 `Utils.useQsMediaPlayer()` 在 OS3/OS5 都被 vivo 焊死成常量 `false`
  （`classes5.dump:354724`），`MediaCoordinator.mMediaFilter` 恒不生效 ⇒ **别去打开它**。
处理：
- 删掉我几小时前基于旧前提加的 `MediaCardFeature.supportNoteRes`（"OS3 无落点"的说明文案）与对应 string；
- skill §4 的那条坑改写为正确结论 + 反向陷阱（`.qoder/skills/betteroriginos-dev/SKILL.md`）；
- 结论：**A11 音乐卡在 OS3 不需要"先让它出现"的前置工程**，同一套 `a11` 风格直接落到通知行即可；
  只有"锁屏态面板也要显示"才需要额外钩点（H1 setResult(false)，代价是与 musicwidgetmix 锁屏 widget 重复呈现）。

### 更正二 / 新验证：G2 路径做到指令级锁死（含负对照）
- 新增 `work/g2_path_selftest.py` + `work/tmp-re-g2/`（记录型 `android.graphics.Path`/`RectF` 桩 + 反射探针）：
  让 JVM **直接执行模块编译产物里的 `Os5Mirror.g2Path`**，把 moveTo/lineTo/cubicTo/arcTo/addRoundRect 全序列
  与独立参考实现（按 A15 常量重算）逐条比对。
  用例 9 组：四角全圆 / 组头只圆顶 / 组尾只圆底 / 全方 / 12dp / 24dp / `2.6r` 超界回落 / 半径 0 / 非零原点。
- 结果：正例 exit=0（起点=1.3r、控制点 0.55r/0.01r、四段弧 205/295/25/115 各扫 40°、逐角开关正确、回落与退化正确）；
  `--negative`（把参考实现的 1.3r 偏移 5%）**报红 83 处** ⇒ 这条检查不是空转。
  首轮"全绿"其实被 float32 vs float64 的第 4 位小数卡住（模块 1027.4807 vs 参考 1027.4806），
  改成指令类型严格 + 数值容差 0.02px 后才正确。
- 构建：`module/build/BetterOriginOS.apk` 275,006B，sha256 `3cd1539a27bd…`（`work/build-1102-final.log` exit=0）。

### 现在的验证覆盖表（压缩后照这张表判断，不要凭印象）
| 对象 | 已验证方式 | 状态 |
|---|---|---|
| G2 连续角公式 | 执行模块字节码逐指令比对 + 负对照 | ✅ 离线绿 |
| A11 取色/HSL/LAB/对比度校正 | 执行模块字节码 vs 独立实现（3005 组，容差 4）+ 数学不变量自检 + 负对照 | ✅ 离线绿（并抓出并修了 HSL 分母 bug） |
| OS5 复刻的数值/开关 | `work/verify_os5_mirror.py --selftest`（正例 + 5 反例） | ✅ 离线绿 |
| 钩点在真机上是否触发、观感是否 1:1 | 需设备（`work/os3-smoke-os5mirror.sh` 等） | ❌ 未跑 |
| A11 卡在 OS3/OS5 的实际 bounds | 需设备 + Y7s 放歌取证 | ❌ 未跑 |

## 2026-11-02 深夜 · A11 卡补齐控件几何 + 规格见证 + 断言覆盖

- 新增 `module/src/com/betteroriginos/feature/media/A11MediaSpec.java`：A11 常量表，每条注明来源
  （展开封面 52dp=`qs_media_album_size`、收起 36dp=`notification_right_icon_size`、
  按钮 48/48/**52**/48/48dp、轨道 3dp、文字 16/14/14sp、app 图标 16dp、
  白色阶 `#ffffffff/#99ffffff/#c0ffffff/#80ffffff`、高亮 ±0.15/370ms）。
- `MediaCardFeature` 把此前散落的字面量换成该常量表，并补齐 a11 的控件几何：
  侧键 48dp / 播放键 52dp、时间 14sp、app 图标 16dp、`SeekDrawable` 轨道粗细改成可设（a11=3dp，其它样式仍 4dp）。
- 新增**规格见证日志**（不依赖截图就能断言尺寸）：`a11 card spec art=… btn=…/… src=… titlePx=…
  artistPx=… timePx=… trackDp=… density=… fontScale=… radius=…`。
- `work/verify_os5_mirror.py` 加 `a11spec` 断言：按 density/fontScale 反算期望 px，容差 1.5px，
  并校验 trackDp=3、radius>=0；`--selftest` 新增 3 条反例（封面不是 52dp / 轨道不是 3dp / 标题字号不对），
  **正例 + 8 条反例全部通过（exit=0）**。
- 构建：`BetterOriginOS.apk` 275,006B+，`work/build-1102-spec.log`、`work/build-1102-final.log` exit=0。

### 现在的缺口盘点（诚实版）
- OS5 复刻（任务 A）：代码 + 构建 + G2 指令级验证 + 数值门禁（离线）✅；**A13 真机钩子触发与观感 1:1 未验证**（需 DSU）。
- A11 卡（任务 B）：载体/取色/几何都逆到代码级，OS5 与 OS3 共用 `a11` 风格分支（OS3 可达性前提已更正）✅；
  取色数学用模块字节码与独立实现比对 3005 组一致 ✅；**未验证**：A15/A13 真机上 `media_actions`
  实际按钮数与 id、`dialogCornerRadius` 的实际 dp、收起/展开两态在 vivo 行上的真实 bounds、
  Y7s 放歌时的 RemoteViews 布局名（A11 侧唯一还欠的原始证据）。
- 明早按任务 #6 的清单跑设备门禁；`work/os3-smoke-os5mirror.sh` 与 `verify_os5_mirror.py` 已经就位。

### 收尾：A11 控件几何与规格见证落地，五个离线检查全绿
- 坑：`DisplayMetrics.fontScale` 在编译用的内部 android.jar 里不存在（javac 报找不到符号，位置=DisplayMetrics），
  改用 `getResources().getConfiguration().fontScale`；已写进 skill §6。
- 构建：`module/build/BetterOriginOS.apk` 275,006B，sha256 `a2d236013bba…`（exit=0）。
- 当前离线验证矩阵（每一条都有独立参照或负对照，不是自证）：
  - `python work/g2_path_selftest.py` → 执行模块字节码里的 `Os5Mirror.g2Path`，9 组用例逐指令一致
  - `python work/g2_path_selftest.py --negative` → 参考实现偏移 5% 时报红 83 处（检查非空转）
  - `python work/a11_math_selftest.py` → 数学不变量全绿（含明/暗两分支覆盖）
  - `python work/a11_crosscheck.py` → 模块 `A11Palette.class` vs 独立实现 3005 组，最大通道差 4
  - `python work/verify_os5_mirror.py --selftest` → OS5 复刻 + A11 规格断言：正例 + 8 条反例全对

### 再补一步：A11 收起态排版的真实数值（原本标 `[待核]`，现在有了代码级来源）
从 Y7s 的 `work/y7s-a11/framework-res.apk` 逐跳解析出 MediaStyle 模板 line1 的文本样式：
`notification_template_part_line1.xml` → `textAppearance=@0x01030385`
→ `style/TextAppearance.DeviceDefault.Notification.Title`（parent `Material.Notification.Title` @0x01030203）
→ attrs `textSize=@dimen/notification_title_text_size`、`fontFamily="sans-serif-medium"`
→ **14.0sp**；副文 `@dimen/notification_subtext_size` = **12.0dp**；
`notification_right_icon_size` = **36dp**（复核代理 B 的值）；`notification_action_list_height` = 60dp。
⇒ 我原先给 a11 收起态套用展开卡的 16sp/14sp 是错的，已按状态分开：
展开态 16/14/14sp，收起态 14sp `sans-serif-medium` + 12sp 副文；字号变化同时驱动
`setState(expanded)` 重排（点开展开会换回展开态排版）。
- 坑：`TextView.setTypeface(Typeface, float)` 不存在（只有 `(Typeface, int style)`），
  字号要单独 `setTextSize` —— javac 报的是"从 float 转换到 int 可能损失精度"。
- 构建：`BetterOriginOS.apk` 275,006B，sha256 `e87fae79c860…`（`work/build-1102-collapse.log` exit=0）。
- 五个离线检查复跑全部 exit=0（G2 正/负、A11 数学、A11 字节码交叉、OS5 复刻 + A11 规格断言自测）。

## 2026-11-02 深夜 · 防"假绿灯"的三处加固 + A11 卡门禁脚本

1. **钩子命中数自证**（skill 里"钩子装上 ≠ 有落点"的坑）：`Os5Mirror` 现在对每个 hook 目标记命中数，
   0 命中直接 `Log.w(... matched ZERO methods)`，并把汇总打进
   `os5_mirror: install groups=N initDimens=1 fillet=1 getRoundedRectPath=1 shelf.onFinishInflate=1 shelf.bind=1 shelf.setDismissVisible=1`。
   先核过 A13 的 `NotificationShelf` 确实**自己声明** `onFinishInflate/bind/setDismissVisible/onLayout`
   （`work/tmp-re-os5toos3/OS3-NotificationShelf.java:97,125,153,678`），`hookAllMethods` 只匹配自己声明的方法这一点才不会静默失效。
2. **A11 卡门禁** `work/a11-card-gate.sh [序列号] [os3|os5]`：切 style=a11 → 连续重启 SystemUI 两次 →
   起播 bbkmusic + MEDIA_PLAY → 展开面板 → 落日志，断言
   `media bind` / `restyling style=a11` / `a11 palette` / `a11 card spec` / `a11 art state=` /
   数值验证器通过 / 无致命异常 / pid 不变；**OS5 模式下检测到安全锁就 exit 3 停下等人**，不猜密码。
3. **验证器分区**：`verify_os5_mirror.py` 加 `--notify-only` / `--media-only`
   （否则通知门禁会因为"没有媒体日志"被误判红）。
   用合成日志做了 5 组行为断言，全对：单独分区喂对/错日志的四种组合 + 内建自测（正例 + 8 反例）。
   另：两个门禁脚本里的 python 调用都加了 `PYTHONIOENCODING=utf-8`，
   避免 Windows GBK 管道把中文输出炸掉变成"验证器自己崩"被误读成失败。
- 构建 `work/build-1102-hooks.log` exit=0；五个离线检查（G2 正/负、A11 数学、A11 字节码交叉、验证器自测）
  与验证器分区行为全部复跑为绿。
- 仍欠（不变）：两条线的真机门禁一台都没跑；A11 侧还欠 Y7s 放歌时的 RemoteViews 布局名与真实 bounds。

## 2026-11-02 深夜 · 修掉一个真机才会暴露的时序 bug（并把它变成可离线验证的纯函数）

**静态核实（A13 反编译）**：类链 `NotificationShelf → ActivatableNotificationView → ExpandableOutlineView → ExpandableView`，
且 `initDimens / getRoundedRectPath / setTopRoundness / applyRoundness / onSystemFilletChanged`
**全部声明在 `ExpandableOutlineView` 自己身上**（`work/tmp-re-os5toos3/OS3-ExpandableOutlineView.java:133,247,272,290,355`）
⇒ `hookAllMethods` 与 `callMethod` 的落点成立（行和收纳栏共用同一套基线，与 OS5 一致）。

**由此发现的 bug**：`initDimens` 只在**构造**和主题/字号变化时跑。若用户是在设置里**之后**才打开 `sep=os5`，
已经存在的行不会重跑 `initDimens` ⇒ 半径会一直停在 A13 的旧基线（17dp/主题 fillet），
表现就是"开了但看起来没变"。

**修法**：在 `getRoundedRectPath` 钩子里自我纠正 —— 读当前 `mOutlineRadius`，与目标基线不一致就按比例换算
`top/bottom`（**系数保持不变**，只换基线），并把新基线写回。这段抽成纯函数
`Os5Mirror.rescaleRadii(current, target, top, bottom)`，因此可以在桌面 JVM 上直接测：

- `work/tmp-re-g2/G2Probe.java` 现在也执行模块字节码里的 `rescaleRadii`；
- `work/g2_path_selftest.py` 增加 5 组断言：17dp→18dp（底 0 保持 0）、上下同步、
  读不到基线时不动不回写、基线已达标时不动、18dp→24dp 且系数 0 的一侧仍为 0 —— **5/5 一致**；
- 原 9 组 G2 指令比对与 `--negative`（偏移 5% 报红 83 处）仍全绿。
- 修测过程中还纠了我自己两处脚本 bug：探针加了 RESCALE 行后用例行被按"含 |"过滤掉（回落矩形只有 1 条指令会被丢），
  改成按输出顺序切分；`fails` 初始化顺序写反。

构建：`BetterOriginOS.apk` 275,006B，sha256 `45de0b165223…`（exit=0）。
影响面复核：`Os5Mirror` 只在 `OsProfile.isA13OrLower()` 下安装；所有改几何的分支都以 `sEnabled` 为前提，
所以 iOS 风格与 OS5 宿主不受这段逻辑影响。

## 2026-11-02 深夜 · 打包完整性复核（"编译过"不等于"打进包"）

从**产物 APK**里取出 `classes.dex` 再 dexdump 确认新代码真的进包了：
`Lcom/betteroriginos/feature/notify/Os5Mirror;`、`Os5Mirror$ClearAll;`、
`A11Palette;` 与 `A11Palette$Swatch/$Palette/$SwatchFilter;`、`A11MediaSpec;`、
`MediaCardFeature$MediaCard/$SeekDrawable` 全在；
资源侧 `drawable/bo_ic_clear_all_os5`、`drawable/preview_a11`、`string/style_a11_title`、
`string/sep_os5`、`string/option_sep_title` 也都在 `resources.arsc` 里。
临时 dex 文件已清理。

顺带把今晚的方法论与更正写进长期记忆（防压缩丢失）：
- 新增用户级 feedback：`feedback-offline-bytecode-verification.md`
  （把移植的纯计算抽成静态方法 → 桌面 JVM 跑模块字节码 vs 独立参考 + 负对照；今晚就是靠它抓到
  HSL 分母 bug 和半径时序 bug）。
- 项目记忆 `notify-os5-to-os3-port-mission.md` 补入：OS3 **有**可达的媒体通知行（旧说法作废）、
  `useQsMediaPlayer()` 焊死 false 的反向陷阱、OS5 半径档位真值、G2 在 A13 可复刻。

## 2026-11-02 深夜 · 延后落点自检 + 纯几何独立成类（被自己的验证器逼出来的重构）

- `Os5Mirror` 加了**落点自检**：开启后每 5s 查一次（最多 2 分钟），几何真的被改过就报
  `os5_mirror landing reachable radius=.. g2=.. shelf=.. clear=..`，整窗无动作才报 `no-entry`；
  一次开启只出一个结论。门禁 `work/os3-smoke-os5mirror.sh` 现在断言三件事：钩子装载、
  **每个钩子命中数不得为 0**、必须有 landing 结论（缺行也算失败），并会等到自检出结论为止。
- **自己的离线检查立刻抓出新引入的问题**：我为自检写了静态字段
  `new Handler(Looper.getMainLooper())` ⇒ `Os5Mirror` 在桌面 JVM 上**类初始化就失败**，两个 G2 检查同时变红。
  修法不是关掉检查，而是拆耦合：新增 `module/src/com/betteroriginos/feature/notify/Os5Geometry.java`
  （`g2Path` + `rescaleRadii`，无 Android 运行时依赖），Handler 改惰性创建，探针改为执行 `Os5Geometry`。
  ⇒ 这条已经写进 skill §6：**能被静态初始化拖垮的检查，本身就是设计问题**。
- 拆类过程中我自己写坏两次（正则里套中文引号导致脚本没执行、删块留下未闭合的 /** 与孤立 */），
  都由 javac 报错定位后修好。
- 打包复核：从产物 APK 取 `classes.dex` dexdump，可见 `Os5Mirror`、`Os5Mirror$ClearAll`、`Os5Geometry` 全在。
- 五个离线检查复跑全部 exit=0（G2 指令级 + 负对照、A11 数学、A11 字节码交叉、OS5 复刻/A11 规格自测）。
- 明早装机基准：`module/build/BetterOriginOS.apk` sha256 `5ec64284b042…`（装完仍要连续重启 SystemUI 两次）。

## 2026-11-02 深夜 · 完成度审计发现真实保真缺口并补上（底色形状）

对代理 A 报告里"实现数值所依赖"的结论逐条亲眼复核，四条全部与源码一致：
- OS5 收纳栏 `onFinishInflate` 里确实 `requestRoundness(1.0f, 1.0f, BASE_VALUE)` + `requestTopRoundness(1.0f,...)`
  （`work/tmp-re-os5toos3/OS5-NotificationShelf.java:167-169`）⇒ A13 的 `setTopRoundness(0)` 是真实差异。
- OS5 浮动清除按钮布局节点逐项对得上：`layout_gravity="center|bottom"`、`layout_marginBottom="40.0dp"`、
  `src=@drawable/vivo_ic_notification_clear_all`、`alpha=0.0`
  （`work/systemui/res/res/layout/status_bar_expanded.xml`）。
- `NotificationBackgroundView.setRadius(f,f2)`：**`mBottomPadding>0` 时强制 bottom=0**、
  `radii[0..3]=top / [4..7]=bottom`、相等即早退（`work/systemui/out-src/...NotificationBackgroundView.java:376-394`）。

**但审计同时抓到一个我自己的保真缺口**：G2 我只换了 `ExpandableOutlineView.getRoundedRectPath`，
那是**裁剪路径**；而 A13 卡片**看得见的圆角**来自 `NotificationBackgroundView.mBackground`
这个 GradientDrawable 自己的 `setCornerRadii`（`OS3-NotificationBackgroundView.java:55-92, 246-252`
里 `draw()` 只是 `setBounds + drawable.draw`，没有 path 裁剪）⇒ 只改裁剪的话底色仍是普通圆角矩形。

补法（不替换 drawable，避免打断它的动画与 `verifyDrawable` 比较）：
新增 `Os5Mirror.hookBackgroundShape` —— 在 `NotificationBackgroundView.onDraw` 前，
按该 View 的 `mCornerRadii` 与 `mBackground.getBounds()` 生成同一条 G2 路径并 `canvas.clipPath(...)`，
并打度量日志 `os5_mirror: bg clip g2 r=.. bounds=(l, t - r, b) lt=.. rt=.. rb=.. lb=..`。

**验证器同步扩到 7 条正例断言 + 9 条反例**（新增"背景没有 G2 裁剪"必须报红），全部 exit=0；
过程中还抓到我自己写的正则错：`Rect.toShortString()` 是 `(l, t - r, b)` 单括号，我按双括号写导致永不匹配
（表现为"正例被判红"，而不是静默通过 —— 这就是留正/负对照的意义）。
构建 exit=0；五个离线检查 + 验证器自测全绿。真机门禁仍全部未跑。

## 2026-11-02 深夜 · 二次审计：修报告一处表述，并补一键清除的显隐判据

- 审计发现代理 A 的"仅 OS5"表述过头：`VivoNotificationIconContainer`、`NotificationRoundnessManager`
  在 A13 **类是存在的**（命中 1 / 2），A15 独有的是 shelf 上的**字段**；真正缺的是
  `RoundableState`(0/4) 与 `VRoundedCornerDrawable`(0/1)。⇒ 收纳栏图标容器不算不可移植项。
  已写进 `out/LOG-1102-OS5-TO-OS3-ADAPT.md §七`。
- 关掉一个开放问题并据此改代码：OS5 的浮动清除按钮显隐是
  `shouldShowClearAll() && mIsExpanded && !isQsFullScreen() && !isKeyguardShowing()`
  （`NotificationStackScrollLayout.java:6033-6035, 6037-6044, 6113`）。
  A13 有 `shouldShowClearAll/onExpandedChanged/getExpansionFraction`，但没有 `setClearAllButtonVisibility`。
  ⇒ `Os5Mirror` 现在记住原生允许值并同时钩 `NSSL.onExpandedChanged` 重算：
  **显示 = 原生允许 && 展开系数 > 0.5**（读不到分数放行，避免按钮永不出现）。
- 构建 exit=0；五个离线检查与验证器自测（7 正例 + 9 反例）保持全绿；未接触设备。

## 2026-11-02 深夜 · 交付明早运行手册
`out/RUNBOOK-1103.md`：装 DSU 的决策点、OS5 宿主与 OS3 各自要跑哪条命令、看哪些日志行、
一键清除/收纳栏/圆角各自的"一眼判据"、Y7s 欠的取证命令（含 OLED 短亮屏约束）、
以及一张"未验证/可能翻车"清单。当前基线包 sha256 前缀 `863b93492348`。

## 2026-11-02 深夜 · 收尾：档位缓存硬化 + DSU 实况落盘
- `Os5Mirror.level()`：**只有真读到** `VivoColorAndCornerPicker.getFilletLevel()` 才写缓存，
  并订阅 `onSystemFilletChanged` 失效。原来的"失败也缓存"会把档位永久钉在猜值上（主题换圆角后不跟）。
  A13 静态核对：`ExpandableOutlineView` 有 `import com.android.systemui.Dependency`（第 14 行），
  `VivoColorAndCornerPicker.getFilletLevel()` 是 public（第 106 行）⇒ 反射链路可用。
- 重新构建成功，基线包 sha256 前缀更新为 `96c1667c8036`；五项离线检查与验证器自测（7 正例 + 9 反例）全绿。
- **DSU 实况**：本机没有 A13/OS3 镜像（只有 `MIO-KITCHEN-4.1.1-win` 下一个 Android 11 GSI，版本不对），
  `G:\z8os3-ref` 是 pull 出来的 APK/dexdump 产物而非镜像；磁盘余量 C:≈74G、G:≈127G。
  已写进 `out/RUNBOOK-1103.md §0′`：A13 门禁要跑必须由你给镜像路径，我不会自行装 DSU（会重置该槽）。
- 设备在线：Z8（`10AE4Y0MDE000BL`）与 Y7s（`IBR8GEMFJNBECIFA`）都连着。Y7s 你已授权用 PIN 自解，
  接下来用它补 A11 音乐卡的实机取证（`dumpsys notification --noredact` + uiautomator bounds），
  OLED 保护照约定：亮度压到 20、超时 15s、抓完立刻熄屏。

## 2026-11-03 02:10–02:22 · Y7s 实机补证成功，并因此改掉一处"跟着宿主主题"的实现
**取证（全程熄屏、不截图）**：`monkey -p com.android.bbkmusic 1` 拉起 + `cmd media_session dispatch play`
起播（`PlaybackState{state=3}` 坐实），再 `adb exec-out dumpsys notification --noredact` 落盘
`work/y7s-a11/notif-playing.txt`。拿到 §9 欠的全部关键前提：
- `contentView / bigContentView / headsUpContentView` 全 null + `android.template=android.app.Notification$MediaStyle`
  ⇒ A11 那张卡是**系统模板**画的，不是 vivo 自定 RemoteViews（所以我们复刻模板而不是复刻某个布局文件才是对的路）。
- `mColorized=true` 而 `flags=0xa`（没有 FLAG_COLORIZED 位）⇒ 上色由 NMS 决定，**不是 app 打的 flag**。
  这条直接决定取色链走不走 `MediaNotificationProcessor`，现在坐实：走。
- `android.largeIcon=Icon(typ=BITMAP size=200x200)` ⇒ 取色输入 200×200，经 `RESIZE_BITMAP_AREA=22500`
  折成 **150×150**（原先按 512 想象过，现在以实读为准）。
- `actions=5`、`android.compactActions=[2,3,0]` ⇒ 展开 5 键、收起 3 键，与 A11MediaSpec 一致。
- framework-res 实读：`notification_right_icon_size=36dp`、`config_dialogCornerRadius=2dp`，
  且 `Theme.DeviceDefault.NoActionBar.dialogCornerRadius → @dimen/config_dialogCornerRadius`
  （`Theme`/`Theme.Holo*` 里是 0dp，不是要跟的那套）；`xml_media_view.txt` 里五枚键宽度按序 48/48/52/48/48dp。

**改掉的问题**：`MediaCardFeature` 原来把 A11 封面圆角写成"读宿主主题的 `dialogCornerRadius`"。
宿主（OS5/OS3）这个值远大于 2dp，等于把 A11 卡顺手美化 ⇒ 违反 1:1。
现在 `A11MediaSpec.ART_RADIUS_DP = 2f` 固定，宿主值只在 `a11 card spec … radius=.. hostCorner=..` 里做对照。
验证器加断言「radius == 2dp×density」并配两个反例（跟宿主主题 / 缺对照字段）。

**新增可重跑核对**：`work/y7s_a11_evidence_check.py`（11 条断言 + `--selftest` 7 个反例）。
反例最初有两个"没被抓住"——因为字面 `replace` 的缩进对不上、篡改静默失效=假绿；
改成按 `dimen/<name>` 正则改值，并加了"每条反例必须恰好改一份材料"的守卫。
该脚本挂进 `work/a11-card-gate.sh` 第 0 步：前提红了就不去真机谈"像不像"。

**五套离线检查全绿**：verify_os5_mirror（7 正例 + 11 反例）、y7s_a11_evidence_check、
a11_math_selftest、g2_path_selftest（含负对照 83 处）、a11_crosscheck（最大通道差 4）。
重新构建成功，基线包 sha256 前缀 `de13a743af73`。

**PIN 的实况（要说清楚）**：你给的 114514 试了 2 次，`dumpsys trust` 始终 `deviceLocked=1`，
我按"不蛮试密码"的约定停手（vivo 多次失败会锁机）。所以**解锁后拉通知栏取控件真实 bounds**这条仍欠，
但它已经不是结论的支点：尺寸/圆角/色链/按钮数由布局 XML + framework-res + dumpsys 三方定死了。
Y7s 侧收尾：已 `dispatch pause` + `keyevent 223` 熄屏；`screen_off_timeout` 被我临时调到 120s，
下次用它之前记得按 OLED 约定改回短超时。

## 2026-11-03 02:35 · 由 200x200 实测逼出的取色网格修正 + 一键离线验收
**修正**：A11 的取色输入是**两步**——先按整图面积 22500 缩放（`(int)` 向下取整），
再把 region 按比例换算（left/top floor、right/bottom ceil 并夹边界），最后只有 region 内像素进量化器。
我原先"只缩 region"，对 Y7s 真实封面（200×200）会喂 20000 个像素，A11 是 **11250**（左半幅）/ **13500**（右 60%）。
像素基数不同 ⇒ `population/22500` 阈值和胜出 swatch 都可能换人，不是"颜色差一点"。
现在 `A11Palette.scaledRegion()/gridPixels()/collapsedRegions()` 与 A11 同构，日志加
`art=WxH gridBg=.. gridFg=..`，验证器按同一公式独立复算并锁住（含 WCAG 对比度、alpha=0 两条新断言）。

**新增工具**：`work/a11_sampling_selftest.py`（字节码 + 独立参考 + `--negative` 拿旧实现当反例）；
`work/offline-all.sh` —— 9 项不需连设备的检查一条命令跑完，红了就别装机。

**顺带修掉的假绿**：验证器原来的 `RE_A11` 匹配的是 `a11 palette base=`，
而真机日志是 `a11 palette expanded base=`（正例靠旧 fixture 蒙对，真日志一条都匹配不上）。
拆成 expanded/collapsed 两条正则后，正例从 7 条涨到 8 条、反例 14 条全部命中。
教训：**fixture 的格式必须逐字抄真实日志行**，否则"绿"只证明 fixture 自洽。

构建成功，基线包 sha256 前缀 `a40ce1176885`；dexdump 复核 `scaledRegion/gridPixels/collapsedRegions`
与两条新日志串都在包里。当前离线状态：7 套检查 + 验证器自测全绿。
真机门禁依旧欠跑（OS5 重启进安全锁、OS3 缺镜像）——见 `out/RUNBOOK-1103.md`。

## 2026-11-03 02:44 · 装机前把钩子名逐个对了一遍 A13 dexdump，抓到两处会静默出错的写法
把 `Os5Mirror.java` 也加进 `work/os3-probe.py` 的核对清单，对照 214MB 的 OS3 dexdump 重跑
（报告：`out/OS3-HOOK-REACH-1103.md`）。好消息是半径通道、背景 G2 裁剪、shelf 字段全部命中；
坏消息是抓到两处**真机上会静默表现为"按钮乱出现/不重算"**的问题：
1. **A13 的 NSSL 没有 `onExpandedChanged`**（只有 `setIsExpanded/getIsExpanded/isExpanded/onGroupExpandChanged`）
   ⇒ 原实现只钩这一个名字，命中 0，浮动清除按钮不会随展开/收起重算。
   现在先钩 A15 的，命中数为 0 就退到 `setIsExpanded`，**并把实际钩到的名字记进 `install groups=` 日志**。
2. **A13 的 NSSL 没有 `getExpansionFraction`**（同名的在 `PanelViewController`/`AmbientState` 上），
   而 `isShadeExpanded()` 读不到时 **fail-open 当成"已展开"** ⇒ 收起态/锁屏上按钮照样显示。
   现在退到两版都有的 `isExpanded()`，并把来源打进 `clear-all attached … shade=fraction|isExpanded|…`，
   验证器断言必须是前两者之一。
3. 顺手补 OS5 同款闸门的另一条：**锁屏不出现浮动按钮**，用公开 API
   `KeyguardManager.isKeyguardLocked()`（不反射 vivo 的 KeyguardStateController，两版差异大、反射失败等于闸门不存在）。
- 重新构建 `sha256 前缀 cf7d350d6105`，`bash work/offline-all.sh` 9 项全绿。
- **教训**：钩子名"看起来两版都有"是最贵的一类假设。凡是 fail-open 的判断，一定要把**走了哪条退路**打进日志，
  否则装机当天只能靠肉眼找现象。

## 2026-11-03 02:50 · 两条自检记录（一条否决了自己的改动，一条承认自己的失误）
- **查过但决定不改**：Z8 上 `betteroriginos_notify_row_sep` 还存着旧值 `split`（今晚把选项改名成
  `off/os5`）。核对后确认**无害**：`sepMode()` 把不匹配的值归成 `off`，而 `sep` 选项只在 A13 才出现在
  选项列表里，OS5 上这条路径本来就是"关闭=原生"，正是我们要的默认。
  我先动手在 `core/Prefs` 加了"选项值别名迁移"，随后**自己回退了**（`git checkout --` 已确认与 HEAD 一致、
  未丢其他改动）：为一个不存在的问题改共享核心类 = 违反"改 core 要复跑整套 OS3 冒烟"的约定，而冒烟现在跑不了。
- **我的操作失误（如实记）**：清理 `module/work/` 游离目录时，我在**没确认副本存在**的情况下就打印了
  "哈希一致"的结论。事实是 `keyguard-dump.xml` 只有那一份，被删了。影响评估：那份是最初**还没滑出 PIN 面板**
  的锁屏 dump，同样的视图树在 `work/y7s-a11/stage3.xml`、`after1tap.xml` 里有，
  取证价值高的 PIN 键盘那份 `pad_a.xml` 与放歌通知 `notif-playing.txt` 都完好 ⇒ 结论不受影响，
  但"先验证再宣布等价"这条纪律要照办（skill §5 早就写过同类坑）。
- 重新构建成功，当前基线包 sha256 前缀 `cdb894ae01c8`（与 `cf7d350d6105` 内容等价，差异来自签名/打包时间）。
- **把"验证器对不上真日志"变成自动检查**：新增 `work/logshape_lint.py` —— 取验证器每条正则第一个捕获组
  前的固定文本当锚点，回源码里找；找不到就判分叉。正例 12 条全过，
  反例用今晚那个真 bug 复现（把正则退回 `a11 palette base=`）确实报红。已加进 `work/offline-all.sh`，
  现在一条命令跑 **10 项**离线检查，全绿。

## 2026-11-03 03:12 · 代码审查代理回来 10 条，逐条自查后修掉 6 条（含一处 G2 常数抄错）
设备门禁跑不了，所以装机前唯一能抓错的就是静态复查。审查代理给了 10 条，
**我逐条回反编译源核对**（不照单全收），结果分四类：

**已修 · 高价值**
1. **G2 常数抄错**（`Os5Geometry.g2Path`）：vivo 实际是**两套**常数
   （`min>2.6r` 用 `-0.013r / 0.61r / 1.3r`，否则 `-0.008r / 0.4r / 1.1r`），
   退普通圆角的门限是 **2.2r** 不是 2.6r，且第一个控制点偏移是**负值**；
   我原来只有 1.3r/0.55r/+0.01r/2.6r 一套，还在 2.2~2.6r 带里直接退回普通圆角
   —— 收起行高度正落在这个带，等于连续角静默失效。
   另外逐条比的时候还发现自己转写时把右上第二个立方贝塞尔的 `+ cNeg` 写成了 `- cNeg`。
   补齐 `normalCornerPath()`（vivo 的回落分支也照抄 arcTo，不再用 addRoundRect），
   `g2_path_selftest.py` 的参考实现按 `G2CornerUtil.java:148-264` 重抄，
   反对照旧实现 + 翻 `c_neg` 符号（旧的反对照只挪 `1.3r`，抓不住符号错）。
2. **收纳栏消失动画不还原 alpha**（`Os5Mirror.sweepShelf`）：只还原了 scale/visibility，
   留着 alpha=0 ⇒ 系统下次把 shelf 置 VISIBLE（新通知进来）时**内容全透明、通知静默丢失**。补 `setAlpha(1f)`。
3. **a11 两态取色不重跑**（`MediaCardFeature`）：去重键只有封面哈希，
   `setState()` 展开↔收起只换几何 ⇒ 收起的着色背景贴在展开的深色卡上。
   键改成 `hash/style/expanded/night`，`setState()` 里补 `applyPalette()`。
4. **前景色选择算法整个是简化版**：我原来"按顺序取第一个非空族"，
   缺 AOSP 的群体门槛（`pop/22500 > 0.002` ≈45 像素）、vibrant/muted 两两 PK 规则、
   以及"候选比 dominant 小且 dominant 够艳就改用 dominant"这条覆写（**参考里是整数除法**，照抄不改浮点）。
5. **六族选择用的是 support-v7 老打分**，不是 A11 实际走的 androidx `Target` 打分
   （权重 0.24/0.52/0.24、饱和度与明度 **0..1 标度**、target 之间互斥）。
   按 `Target.java` + `Palette.java:197-241` 重写，顺手删掉我为老模型留的 `isBelowThreshold`
   （它在 0..1 饱和度上恒真，是个标度事故）。
6. **findBackgroundSwatch 缺 2.5 比例门控**：大面积纯白封面会被一个小色块抢走背景色；
   空 palette 该回白色 Swatch 而不是 null。色相排除 filter 也要**只在背景非黑非白时**才加。

**已修 · 属于陈旧报告**：审查代理看到的还是 02:41 之前的快照，
`onExpandedChanged`/`getExpansionFraction` 的 A13 退路我已经做完了（见上一节）。

**驳回的一条**（⚠️ 03:20 已推翻：代理是对的，我核错了调用点，见下一节）：代理说"无封面兜底参考算得 `0xFF404040`"。
查 `MediaDataManager.java:339` 是 `iconBitmap != null ? … : -12303292`，
`-12303292 = 0xFF444444` ⇒ 我的常量本来是对的，不改。

**记为待办（需要真机才判得准，不猜着改）**：`NotificationShelf.onConfigurationChanged`
会把 `mPaddingBetweenElements` 复位为 0，我们的 7dp 只在 bind/onFinishInflate 时补写
—— 转屏/字体缩放后可能掉间距；以及 `hideInline` 的 GONE 被系统回读成 `setDismissVisible(false)` 的链路。

**新增锁**：`work/a11_selection_selftest.py`（6 组用例：白底换人、黑白争位、暗底走 dark 族、
候选不足退 dominant、同色互斥、45/46 像素门槛）+ `SelectionProbe` 反射跑模块字节码，
`--negative` 挪动饱和度下界与群体门槛证明检查有效。
`offline-all.sh` 现在 **12 项全绿**，`a11-card-gate.sh` 第 0 步也加了选择逻辑检查。
基线包 sha256 前缀 `aa69bf395479`。



## 2026-11-03 03:20 · 上一轮我"驳回审查意见"的那条，其实是我自己看错了（已改）
- 我当时说 A11 的无封面兜底就是 `0xFF444444`，证据是 `MediaDataManager.java:339` 的 `-12303292`。
  把整条链读完才发现 `:339` 属于 **resumption 路径**；展开卡真正走的 `computeBackgroundColor()`
  （`:534-553`）在 `artworkBitmap == null` 时是**把 rgb=-1（白）继续送进同一条 HSL 变换**
  （`l>0.95 → sat=0 → sat*0.8 → l 锁 0.25`）⇒ 算出来是 **`0xFF404040`**。
  审查代理那条是对的，我的驳回复核不充分。
  **教训：推翻别人的结论之前，要把那个值所在的调用点读到底，不能在同一条公式的别处找到一个能对上号的常数就当证据。**
- 修法：`A11Palette.computeCardBackground(Swatch)` 不再返回常量，null 时按 A11 用白色走同一条变换；
  `0xFF444444` 改名 `RESUMPTION_FALLBACK` 只作记录。验证器加断言：日志 `artwork=false` 时 `base`
  必须是 `ff404040`，并配反例（用成 `ff444444` 会同时报"L=26.7%"和"兜底值错"）。
- 收起态无封色的兜底同理：A11 在 `largeIcon == null` 时**根本不着色**（`processNotification` 只在有
  largeIcon 时跑），我们的自绘卡必须填底，于是取 Y7s 框架的默认通知底色
  `notification_material_background_color = #ffffffff`（fw_res 里只有一个配置，无夜间变体）；
  夜间那一支属于本机适配、不是 A11 原值，代码注释与日志都标出来了。
- 把上一轮记为"待真机再看"的 shelf 间距复位问题也修了：`NotificationShelf.onConfigurationChanged`
  会调 `initDimens()` 把 `mPaddingBetweenElements` 打回 0（`OS3-NotificationShelf.java:195-203`），
  现在多钩 `shelf.onConfigurationChanged` 在系统跑完后重补 7dp；命中数进 `install groups=`，
  为 0 会被门禁直接判失败（不用等到转屏那天）。
- 重新构建 `sha256 550ab151f5a9`；`bash work/offline-all.sh` = **12 项全绿、0 失败**。

## 2026-11-03 03:28 · 展开态排版的证据升级：把 media_view 引用的资源 id 解成真实数值
`xml_media_view.xml` 里大量属性是 `@0x7f07xxxx`（SystemUI 自己的 dimen/color）这种引用，
只看到引用不算证据。用 `work/y7s-a11/sysui_res.txt`（aapt2 dump 的 SystemUI 资源表）解出来后：

| A11MediaSpec | 解出的真实值 |
|---|---|
| `ART_EXPANDED_DP 52` | `dimen/qs_media_album_size = 52.0dp`（layout_width/height 都指它）|
| `SEEK_TRACK_DP 3` / `SEEK_DISABLED_TRACK_DP 1` | `qs_media_enabled_seekbar_height = 3.0dp` / `..._disabled_... = 1.0dp`（是 SeekBar 的 `maxHeight`）|
| `SOURCE_ICON_DP 16` | `qs_media_icon_size = 16.0dp`（CachingIconView 的宽高）|
| `TITLE_SP 16` / `ARTIST_SP = TIME_SP = 14` | 布局里 16sp 恰好 1 个节点、14sp 5 个节点 |
| `TEXT_SECONDARY 0x99FFFFFF` | `color/media_secondary_text = #99ffffff` |
| `SEEK_PROGRESS 0xC0FFFFFF` | `color/media_seekbar_progress = #c0ffffff` |
| `SEEK_TRACK 0x80FFFFFF` | `color/media_disabled = #80ffffff` |

⇒ 这些不再是"看着像 A11"，而是**设备资源表实读**。已把 8 条新断言 + 3 条对应反例
（改成 64dp / 6dp / #60ffffff 都必须报红）加进 `work/y7s_a11_evidence_check.py`，
现在该脚本 **19 条断言 / 10 个反例**全绿；`work/offline-all.sh` 仍 12 项全绿。
  （**21:41 更正**：这行曾被批量替换误改成 25 条——那是历史时刻的真值，现在该脚本已长到 25 条断言 / 14 反例。）
注意它现在多了 `sysui_res.txt` 作为必需输入（缺文件直接判失败，不静默跳过）。

## 2026-11-03 03:50 · 二轮复查：取色的**源头**（量化器）原来一直是简化版；顺带把构建的假成功堵掉
二轮静态复查（逐行对 `ColorCutQuantizer.java`）又抓到实质偏离，并且意外暴露了一个更基础的问题：

**先说构建**：`module/build-log.txt` 是 10-25 留下的旧文件，我按 skill 的说法"失败看 build-log"，
结果读到一条早就不存在的错误，白紧张一场；更糟的是 **javac 真报错时 build.sh 也会照样打出 APK**
（旧版把 javac 输出丢给终端、不判失败）。现在 step 3 改成 `... 2>&1 | tee build-log.txt`，
配合脚本本来就有的 `set -euo pipefail` ⇒ **编译一红就中断，不再产出"看起来成功"的包**；
也顺便证实：真的坏一次时（重复方法名那次）APK 直接不生成，这就是我们要的行为。

**量化器的偏离（objective B 的根）**：我原来的 `quantize()` 是"5bit 桶 + 按群体排序取全部桶"，
外加一层"群体 <1%×max 就整块丢弃"的预筛。A11 实际是 androidx `ColorCutQuantizer`：
1. 桶直方图**不看 alpha**（我多加了 `alpha>=0x80` 过滤，A11 这版没有）；
2. 过滤器作用在**桶的代表色**上（整桶留或丢），我是按像素过滤 —— 桶内混色时结果完全不同；
3. 非空桶数 `<= maxColors(=16)` 时才"一桶一色"；超过就走 **median-cut**：
   体积优先队列、沿最长颜色维排序、累计群体过半处切点、切到 16 个盒子、盒子求群体加权平均色、再过一次滤；
4. 我在 `generate()` 里那层占比预筛在 AOSP **不存在**（占比判定只在 `hasEnoughPopulation` 里发生），
   它会把候选色块提前丢掉 ⇒ 某些族选不出色、退到 fallback 色。
现在 `A11Palette.quantizeToSwatches()` 按上面 4 条实现，旧的预筛与 alpha 过滤删除。

**region 的坐标系**：A11 两处 `setRegion` 用的都是**缩放后的** bitmap（`MNP:177` / `MNP:71`），
我原来在源图尺寸上算 `w/2`、`0.4w` 再按比例换算 —— 偶数尺寸恰好同解，奇数就差 1px
（201×201 时旧算法给 100/80，正确是 75/60）。改成 `workingSize()` + 工作系 `collapsedRegions()` + `gridPixels(w,h,idx)`。

**G2 回落分支**：`getNormalCornerPath` 先把宽高**截断成 int** 再判 `2r` 夹取，我之前用浮点比较，
59.9px 这类尺寸会差 0.5px。已对齐。

**浮动清除按钮的动效**：OS5 四处动画（按下/抬起缩放、alpha 出入）都用
`PathInterpolator(0.25,0.45,0.3,1.0)`，我只设了时长 ⇒ 观感成了线性缩放；另外"隐藏"是同帧
`setVisibility(INVISIBLE)`，淡出动画根本看不见 —— 改成淡出结束后再置灰，并按 `mShown` 二次判定
（`withEndAction` 在动画被打断时也会跑，不然会出现"已显示但被置灰"）。

**新增离线锁**：`work/a11_quantize_selftest.py`（字节码 vs 独立 median-cut 参考；
3 组逐块精确 + 2 组只验不变量 —— 堆在体积并列时出堆顺序不保证，逐块比会假红，这点写进了注释）、
`SamplingProbe` 重写去锁工作系网格。`bash work/offline-all.sh` 现在 **14 项全绿**。
基线包 sha256 前缀 `61131d8faab3`（同内容再签一次，与 `cdea5844b4fa` 只差签名）。

## 2026-11-03 03:57 · OS5→OS3 这条线也做了一遍"写入者全枚举"，抓到配置变更后的自噬
趁设备等用户，把 A13 圆角通道的**所有写入点**在反编译源里列了一遍（`OS3-ExpandableOutlineView.java`）：
`mOutlineRadius` 会被三处改写 —— `initDimens()`（:251-256）、`onSystemFilletChanged()`（:355-364）、
以及经 `onDensityOrFontScaleChanged()`（:350）间接调的 `initDimens()`。
我们钩的是 `initDimens` + `onSystemFilletChanged` 两个方法本身，
第三处是内部虚调用也会被拦到 ⇒ **半径通道没有漏网的复位点**（这条结论落进 ADAPT 日志 §8）。

真正抓到的是**收纳栏的自噬回路**（上一轮只补了间距，没看到这一层）：
`NotificationShelf.onConfigurationChanged` 末尾有
`setDismissVisible(mDismissAllNotification.getVisibility() == 0)`（:202），
而我们把那个内联文字按钮按成了 `GONE` ⇒ 转屏/改字号时系统会**读到我们自己造的 false**，
`sDismissAllowed` 被写成 false，浮动"一键清除"从此不出现，直到下一次系统主动 give true 才恢复。
修法：`onConfigurationChanged` 前后置 `sInConfigChange` 标记，期间不采信参数，
结束后用我们自己记住的 `sDismissAllowed` 重算一次（并顺手重补 7dp 间距）。
这类"我们改了系统会读回的东西"的回路，跟通知去重那条 `getGroupKey` 教训同源：**改状态前先问谁会把它读回去**。

- 重新构建：`sha256 cbd913f91da0`；`bash work/offline-all.sh` 仍 14 项全绿。
- 文档同步：`out/LOG-1102-OS5-TO-OS3-ADAPT.md §8`、`out/RUNBOOK-1103.md §4`（转屏那条从"可能掉"改成"已修，仍需实机确认"）。

## 2026-11-02 20:25 · 先把时钟写对，再记一键清除这条链的三处修正
（**时间纪律更正**：上一批条目写成 `2026-11-03 02:50…03:57`，而本机墙钟现在才是 `11-02 20:25`——
夜里排班的时间被我当成了墙钟往日志里写。以后一律 `date` 取值，别再用"应该是几点"。）

顺着"谁给 shelf 的清除按钮设监听"这条线，先撞上一个取证工具层面的坑：
`G:/os3-ref/os3-dexdump-f.txt` 是 `dexdump -f`，**只有 header 和方法名，没有 code 段**，
所以"全库搜 `setClearNotificationListener` 命中 1 条"从来就只能证明声明存在，证明不了没人调用。
补跑 `-d -n`（5 秒、134MB）之后调用链完全确定：`NSSL.setShelfController`（装配时一次）
→ `setShelfListener()` → 反编译里那个 `-$$Lambda$NSSL$PWFM8L-…` →
`lambda$setShelfListener$9` = `onDismiss()` + `clearNotifications(0, z)` + `mClearAllInProgress` 时
`updateShelfWhenDismissAll(z)`。⇒ 转接 `performClick()` 的路线本身是对的（GONE 不影响 performClick）。

**但这轮真正推翻的是我自己的一处实现**：ADAPT 日志 §2.4-(3)/§3.4 第 3 条写着
"OS3 没有 `updateShelfWhenDismissAll` 公开入口，所以要自己补播 250ms 消失动画"，
我照做了（`sweepShelf()`）。实际 `OS3-NotificationShelf.java:797-817` 里这个方法**就在**，
参数与 OS5 逐字相同，而且带两道我们没有的门：`mClearAllInProgress`，
以及 `z = !hasUnClearableNotifications(0)` 的 `mLastHideState || z`。
这台机常驻 LSPosed 的 `ONGOING_EVENT` 通知（`dumpsys notification` 12 条记录里多条 ongoing），
`z=false` 是常态 ⇒ 我那版无门控的补播会**把还挂着不可清除通知的收纳栏整个抹掉**。已删。
上一条"grep 到 0 就当不存在"的错，根因是把 jadx 跳过巨型方法（`updateAppearance` 701 指令）当成方法不存在。

**顺带发现内联文字按钮在 A13 上是承重墙**：两版 `changeShelfVisibility(z)` 逐字相同，
条件是 `(z && 内联.getVisibility()==8) || 折叠 || QS展开` 才隐藏；
而 `setDismissVisible` OS5 无条件写 `8`、A13 写 `z?0:8` ⇒ A13 的收纳栏被这枚按钮顶住不收，
我们按 OS5 写 `GONE` 其实同时移植了"该收就收"的行为（原来只当它是去装饰）。

三处落地：① 点击只转接，删补播动画；② 用**能力**关门——
`com.vivo.systemui.statusbar.ui.NotificationClearAllImageView` 在 A15 三个 dex 命中 6、
A13 全 0，命中就整段不补挂（否则 OS5 上会长出第二枚圆钮），日志 `clear-all native=`；
③ 坐标进日志（`clear-all at x/y/w/h`，`onSizeChanged` + 显隐两处），脚本按中心 tap，
并用 `NotificationRecord(` 的**可清除条数**在点击前后对比，证明批量移除真的发生。

验收同步：`verify_os5_mirror.py` 加 `RE_TAP`/`RE_NATIVE` 三条断言 + `GOOD_OS5` 正例
（OS5 的日志形状本就没有 attached/tap，两套都要绿），反例 21 条全咬；
`os3-smoke-os5mirror.sh` 新增第 4 步点击链路、验证器挪到点击之后、`set -u` 下坐标先兜 0。
构建产物 `module/build/BetterOriginOS.apk` 283,198B、`sha256` 前缀 `323c2cfc5091`（20:23，含本轮三处修正）；
`bash work/offline-all.sh` **14 项全绿**。
真机门禁仍是老三样被挡住：Z8 有安全锁不能碰、OS3 要 DSU 镜像需用户确认、Y7s PIN 两次失败后停手。

## 2026-11-02 20:38 · 静态抓到第二处真缺陷：浮动清除按钮挂在了 ConstraintLayout 上
顺着"按钮该落在哪"去读布局本身（`aapt2 dump xmltree --file res/layout/status_bar_expanded.xml`，
两版各一次），事实很清楚：
- **OS5**：`NotificationClearAllImageView` 是 `NotificationPanelView` 的**直接子级**，
  `layout_gravity=0x51`(BOTTOM|CENTER_HORIZONTAL)、`marginBottom=40dp`、`alpha=0`、wrap_content。
- **A13**：我原来的 `hostFor()` 取 `NSSL.parent`，而那个 `NotificationsQuickSettingsContainer`
  **extends ConstraintLayout**；`NotificationPanelView extends PanelView extends FrameLayout`。
  往 ConstraintLayout 塞 `FrameLayout.LayoutParams` 会被 `generateLayoutParams` 换型、**gravity 丢失**，
  未约束的子视图落左上角 —— 不抛异常、不报错，装机当天只会表现为"按钮跑到状态栏底下去了"。

改成"往上找第一个 `FrameLayout`"，A13 上正好回到与 OS5 同一层的 `NotificationPanelView`；
底边距 40/62dp 也因此量在正确参照系（挂在容器上时那个容器高度并不等于面板高度）。
见证新增 `host=<类名>`，验证器要求含 `Panel`，并配两条反例（宿主=QuickSettingsContainer / 缺 host 字段）。
`bash work/offline-all.sh` 仍 14 项全绿，验证器正反例现在是 **2 正 + 22 反**。

**方法论**：涉及"加视图到哪"的移植，判据应该是**两版的布局树 + 父类是不是 FrameLayout**，
不是"跑起来看着像"。`aapt2 dump xmltree` 对二进制布局是明文输出，成本 1 秒，
比在设备上试错便宜得多；这条已经写进 skill §4。

## 2026-11-02 20:46 · 底边距的分支条件也错了：OS5 问「是不是三键导航」，不是「有没有导航栏」

> **【20:56 自我更正，见 ADAPT §9.8】** 本节结论作废。真正信号是 `CentralSurfaces.isNavigationBarVisible()`（A15 dexdump 证明调用方传非 null 的 `mCentralSurfaces`，走的是可见性那一支）；`config_navBarInteractionMode` 实测在 A15/A11 的 framework-res 里都是 0，vivo 不读它，所以原来的 62dp 与 OS5 本来就一致，担心的「高 22dp」不存在。
`aapt dump --values resources` 把 OS5 三个 dimen 实测出来：`margin_bottom=40dp`、
`with_navigationbar=62dp`、`with_navigationbar_with_flowview=90dp`、`size=46dp`、`padding=2dp`。
OS5 的按钮用 `NavigationConfiguration.isUsingNavigationBar()` 在 40/62 之间选，
而模块用的是 `config_showNavigationBar` —— **全面屏手势下它仍然是 true**，于是会取 62dp，
比 OS5 高 22dp。改成优先读 `android:integer/config_navBarInteractionMode`（0 才算三键），
读不到才兜底，并把来源打进日志 `navMode=`；验证器断言 `nav == (navMode==0)`，反例是
「手势导航却用了三键边距」（margin 与 nav 自洽、只有 navMode 打架，这样才会被单独抓到）。

同一份 dump 还顺手给 §5.3「A13 缺一键清除资源」补了**第二条独立证据**：
A13 的 resources.arsc 里 `vivo_notification_clear_all*` 命中 **0**（此前只有 jadx/dexdump 一条路径）。

## 2026-11-02 21:00 · 上一节的自我更正，加一条离线取证通道
按 A15 dexdump 的调用点确认：`NSSL.setClearAllButtonLocation` 传的是非 null 的 `mCentralSurfaces`，
所以 OS5 走 `isNavigationBarVisible()`（"此刻可见"），**手势 hint bar 也算可见** ⇒ 62dp。
我 20:46 那节改判 `config_navBarInteractionMode` 是错的方向——把 framework-res 拉下来实测后
（A15=0、A11=0，但 Z8 的 `dumpsys window` 里有 `SideSlideGestureBar-Bottom`）证实 vivo 不读它。
模块现在：优先 `getInsets(Type.navigationBars())` 非零（返回 `android.graphics.Insets`，字段 bottom/left/right，
**不是 Rect**——这点编译器抓了我一次），拿不到才兜底 `config_showNavigationBar`，
日志字段从 `navMode=` 换成 `navSrc=insets|config`。另外 OS5 在配置变更时会重算边距
（调用点 `2ad99e`/`2ae1f4`），所以 `ensureAttached` 命中已有按钮时加了 `refreshMargin`：
只在信号真的变了才写回 LayoutParams。`work/offline-all.sh` 14 项全绿，包 `20c23f9a757f`。

**可复用的取证通道**：framework 配置不用上真机也不用解锁——
`adb exec-out cat /system/framework/framework-res.apk` + `aapt dump --values resources`。
本轮就是靠它推翻了自己的上一条结论。

## 2026-11-02 21:09 · 第三轮代码审查：两处真缺陷，其中一处是我 6 分钟前刚写下的
让代理逐行复核本轮 `ClearAll` + 验收器（禁止截图，判据必须回到 dex/资源表）。三条结论：

1. **浮钮的显隐只有一个事件源**：只有 `setDismissVisible` 驱动它 ⇒ 一键清除把栏清空后
   若系统不再回调，钮会留在空栏上。OS5 那边其实有 `NSSL.setClearAllButtonVisibility()` 自己重算
   （A15 dexdump 里在，A13 没这个方法）。A13 上加钩 `NotificationShelf.changeShelfVisibility(Z)V`
   （PRIVATE，`hookAllMethods` 命中；`os3-probe` 复核 ✅），after 里用系统刚写下的 shelf 可见性重算。
   我们不再写 shelf 的可见性，所以这次读回来不是 §8 那种自噬回路。
2. **我自己上一轮的修法又错了一层**：`getInsets(navigationBars()).bottom > 0` 量的是"留没留出高度"，
   不是"可见"。手势 hint bar 常见"可见但 inset=0" ⇒ 取 40dp，而 OS5 这档取 62dp。
   改成 `WindowInsets.isVisible(Type.navigationBars())`（= `isNavigationBarVisible()` 的等价物），
   inset 高度降级成见证值。日志同时给 `navVis=` 与 `navH=`，验收器断言 `nav==(navVis==1)`，
   并加反例 `nav=false navVis=1 navH=0`（正是这个缺陷的形状）。
3. **验收器盲区**：`native=` 证据原先要等 shelf 事件才打 ⇒ OS5 上"合法地什么都不打"会被判红。
   把类存在性探测提到 `hookShelf` 里（`findClassIfExists` 一次并缓存），保证一定有一条。

编译期两个坑（内部版 android.jar 与真机不同名/缺符号）：`WindowInsets.getInsets(int)` 返回
**`android.graphics.Insets`**（字段 `bottom/left/right`，不是 `Rect`）；`View.isVisible()` 在这个 jar 里没有，
只能用 `getVisibility()==VISIBLE`。另外新用到 `XposedHelpers.findClassIfExists` ⇒ **先补编译桩**（skill §2 的规矩）。

`verify_os5_mirror.py`：2 正例 + **25 反例，漏网 0**；`work/offline-all.sh` 14 项全绿；包 `6c11222fdca6`。
真机门禁仍未跑（DSU 需你点头 / Z8 有安全锁 / Y7s PIN 两次不过）——本轮全部是"设备之外能判定的部分"。

## 2026-11-02 21:13 · 把"两条线互不干扰"变成可断言的事，并记一条待办更正
读 `NotifyRowFeature.java:420-423` 才发现：复刻钩子本来就是**按版本关门**的
（`OsProfile.isA13OrLower()` 里才 `Os5Mirror.install(cl)`）。所以昨天加的
`native=` 能力探测其实是**第二层保险**（防 A14 / 防 vivo 把那个类反向搬到 A13），
不是唯一防线——这也要写清楚，否则以后有人看到两层会拆掉其中一层。
据此给 `work/a11-card-gate.sh` 加了 4b 步：A15 模式要求 `os5 mirror hooks installed` **0 次**
（装了就是第二枚清除钮 + 半径被改两次），A13 模式要求 ≥1 次，且 `sep=off` 时不许出现
`clear-all attached/tap/at` 行；`sep` 由本门禁之外控制，所以只在它确实是 off 时断言。
RUNBOOK §1 同步。

**待办更正（先记不做大改，避免与在跑的审查代理撞车）**：
`verify_os5_mirror.py` 里 `GOOD_OS5` 的注释说"OS5 上没有 attached/tap 行"——
情景描述不准确：真正的 A15 是**整条复刻不装钩子**（一行 os5_mirror 都没有），
`native=true + 不补挂`只会在"≤A13 但系统已有那个类"的机器上出现。
等 A11 审查代理回来后：改注释，并让 `check()` 先用 `os5 mirror hooks installed`
判断"这台机复刻是否该有日志"，没装就把 notify 断言整段判成"应有的静默"而不是红。

## 2026-11-02 21:16 · 给"从没跑过的门禁 shell"补上自测，并让两条线互不干扰可断言
第 4 步的坐标解析 + 位置判断是我这两小时里唯一**从没执行过**的一段 shell：真机不在，`bash -n` 只保证语法。
所以把它抽成 `work/os5mirror-geom.sh`（`parse_bounds` / `check_placement`），冒烟脚本 source 它，
再配 `work/os5mirror-geom-selftest.sh`：1 条正例 + 5 条必须报红（左上角、居中的上半屏、
缺日志、屏幕尺寸读不到、**日志字段漂移**——`w=` 改名成 `height=` 时中心会被算成左边界，
位置判据必须因此红，不能"恰好通过"）。Z8 实测 `wm size 1080x2388`、密度 3.0 ⇒ 46dp 按钮 138px、
居中 left=471、中心 540 就是正例的来源。**它已经进了 `work/offline-all.sh`，现在 15 项。**
顺带修掉一处 shell 引号问题：heredoc 里 `"…把"没挂载"误报…"` 的嵌套双引号能过 `bash -n`
但会把文案撕碎，改成「」。

另一件事：读 `NotifyRowFeature.java:420-423` 确认复刻钩子是**按版本关门**的，
所以给 A11 门禁加了 4b 步把这件事变成断言（A15 上 `os5 mirror hooks installed` 必须 0 次；
A13 上必须 ≥1 次且 `sep=off` 时不许出现 `clear-all attached/tap/at`）。
位置不成立时不再 tap——坐标没意义的点击只会把"挂错层"伪装成"点了没反应"。

## 2026-11-02 21:31 · A11 那条线也有审查结果回来了：4 条成立、1 条前提不成立；并第一次在真 A15 上取到证据
**审查代理对 A11 音乐卡的 5 条，逐条核对后**：
1. **成立（高危，且是"断言静默少跑"）**：收起态取色日志的 `contrast` 用
   `String.format("%.2f", …)` **不带 Locale**。逗号小数语言的机器会写出 `contrast=9,40`，
   验证器的 `([\d.]+)` 匹配不到 ⇒ 整行被丢，颜色/对比度/网格断言**一条都不跑**，
   而 expanded 行还在，所以不会报"没有日志"，是**假绿**。已补 `Locale.US`
   （同文件 :1122 与 `fmt()` 早就带上了，唯独这条漏了）。
   并把这类失效本身变成断言：**"某行存在但解析不出"直接判红**（6 个标记做行数/解析数一致检查）。
2. **成立**：收起态副文在 A11 是 `@dimen/notification_subtext_size = 12.0`**dp**
   （framework-res 实测 `aapt dump --values`：subtext=12.000000**dp**、title=14.000000**sp**），
   我们却按 `COMPLEX_UNIT_SP` 套 ⇒ fontScale≠1 的机器上比 A11 大一档。改成 `COMPLEX_UNIT_DIP`
   并把常量改名 `COLLAPSED_SUBTEXT_DP`。这条在小字号样本上永远看不出来，所以
   **新增 `GOOD_FS`（fontScale=1.3）正例**：sp 字号跟着放大、dp 不放大，两边期望值分开算。
3. **成立**：展开分支没还原收起态设的 `sans-serif-medium`，一次"收起再展开"标题就一直 medium。
   现在展开态显式 `setTypeface(null, NORMAL)`，并加见证 `titleMedium=0|1`（按实例身份比，
   `Typeface.getFamily()` 在这个内部版 jar 里没有）。
4. **成立（验证器自己的错）**：`a11_grid()` 对*源图*区域做 floor/ceil 再缩放，
   而模块/A11 是"先缩放到工作位图、再 `getWidth()/2` 整数除"。200x200 恰好一样，
   201x201/512x384/1200x800 就分叉（实测 13650 vs 13500、11223 vs 11094）——
   真实封面正是这种尺寸，会把对的实现判成失败。改成按工作位图算，API 也简化成 `a11_grid(sw,sh,region)`。
5. **前提不成立但结论仍采纳**：审查说"androidx 把切不动的盒子丢了，我们放回"。
   核对 `ColorCutQuantizer.splitBoxes` 确实如此（`while (size<max && (poll()!=null) && canSplit())`），
   但这条差异**构造不出可观察用例**：只有 0 体积的盒子才切不动，而优先队列按体积取最大，
   它一定排在最后。所以两边统一成 androidx 写法，并在注释里写明"没有能区分两种写法的 fixture"，
   不假装多了一条锁。

**验收器现状**：3 条正例（A13 常态 / fontScale=1.3 / OS5 原生圆钮不补挂）+ **28 条反例，漏网 0**；
`--expect-inert` 新增（OS5 上"一条 os5_mirror 都没有"才是正确形态，反过来断言零行）。
包 `7fe5e2030bfd`，`bash work/offline-all.sh` 15 项全绿。

**真 A15 侧第一次取到证据（不需要解锁）**：装上 `7fe5e2030bfd` 后 kill SystemUI 重启，
`logcat --pid` 里 `loaded com.android.systemui` + `installed feature notify_row` +
`installed feature media_card` + `notify row feature installed`，
**`os5_mirror:` 0 条**（复刻被 `isA13OrLower()` 关门，零干扰），95 秒后 pid 不变、
crash buffer 无本模块记录。证据 `work/z8os3-live/a15-load-check-1102.log`。
（注：这份文件在 22:11 的第二轮跑中被原地覆盖，本节描述的 `7fe5e2030bfd` 样本已不在；形态结论不变，
最新样本见 22:15 一节与 `a15-load-check-1102-f24325c2.log`。）
剩下的 A15 门禁（媒体卡实际渲染度量）仍要解锁，A13 门禁仍等 DSU。

## 2026-11-02 21:42 · 把"单位"变成断言，并顺手拆掉自己刚犯的一类文档错误
A11 那条线除了 `GOOD_FS` 之外，还缺一个能长期防复发的锁，现在补上了：
`work/y7s_a11_evidence_check.py` 读 framework 资源表时取的是 **(数值, 单位)** 二元组，
`notification_title_text_size=(14.0, sp)`、`notification_subtext_size=(12.0, dp)`、
`notification_action_list_height=(60.0, dp)`，并**反向对表模块源码**：
从 `A11MediaSpec.java` 正则取出常量与设备比。
这条对照当场做过正负验证——把 `COLLAPSED_SUBTEXT_DP` 临时写成 14f 立刻红
（`FAIL 模块 COLLAPSED_SUBTEXT_DP: 实得 14.0 期望 12.0`），改回即 25 通过 / 0 失败；
反例从 10 条加到 **14 条**，其中"只改单位不改数值"（12dp→12sp）也会红。
配套：`_re_dimen()` 只会改 `dp` 的值，所以新增 `_re_dimen_any(value, unit)`；
`dimen_and_unit()` 改成按行扫描不用正则——把 Python 经 heredoc 写进文件时反斜杠会被二次转义，
今晚第三次踩这个坑，`ast.parse` 才发现两条 `SyntaxError: unterminated string literal`。

**我自己刚犯的一类错**：为了同步"断言条数"做了一次批量文本替换，
把 DEVLOG 与 RE 日志里**历史时刻的真值**（当时的 19 条）也改成了 25 条。
已经逐行改回并标注"当时 19，现在 25/14"。教训写进 skill §4：**日志是按时刻记的账，
当前值只写进新条目，别批量覆盖旧条目。**

验证现状：`offline-all` 15 项全绿；`y7s_a11_evidence_check.py` 25 断言 / 14 反例；
`verify_os5_mirror.py` 3 正例 / 28 反例；包 `7fe5e2030bfd` 已装在真 A15 上并取到零干扰证据。

## 2026-11-02 21:46 · 日志形状自检补第二相：纯子串标记也会被模块改词甩掉
`logshape_lint.py` 原来只查 `verify_os5_mirror.py` 的 `re.compile` 锚点；
但 `verify_notify.py` 是 `os3-smoke.sh` 的判据来源，它用的是
`if "notify_row.verify.stack.skipped" in line` 这种**纯子串**——同一类分叉（模块改措辞、
断言静默不跑）完全不在检查范围内。现在加了对相：抓所有 `in line/text` 成员测试里的字面量
（含 `TAG`/`STACK_TAG` 常量解引用），逐条要求存在于模块源码；并给检查器本身做自测
（喂空源码必须全报红），一起进 `offline-all`，现在 **16 项**。

第一版按"以 notify_row/stack 等前缀开头"筛，报了 7 条假分叉——那些是验证器**自己的报告文案**
（`"stack gap=%s expected=%s"`）。教训：**带 `%s` 的是打印串**，真正的日志标记一定写成
`if "..." in line`；按这个形状收紧后 7 条真标记全绿。

## 2026-11-02 21:51 · 新加一个检查器，当场抓出两条"看起来在配置、其实没人读"的门禁步骤
门禁全靠 `settings put global betteroriginos_*` 切开关；键名一旦对不上，**settings 照样写成功、
模块照样读默认值**，于是 A/B 两相其实是同一个状态——不崩不报错，只是把一趟设备时间白白花掉。
所以写了 `work/settings-keys-check.py`：从模块源码推出"合法键全集"
（完整字面量 + `Prefs.option(fid, opt)` 组合 + `Feature#id()` 推出来的 `feature_<id>`），
再要求 `work/*.sh` 里出现的每个键都在集合内。**第一跑就红了一条**：

- `betteroriginos_notify_row_style` —— `NotifyRowFeature.isLightStyle()` 现在恒 `return true`
  （iOS 卡样式已移除），这枚键**没有任何代码读它**。两条门禁脚本都在写它，
  其中 `os3-smoke-light.sh` 还有一句
  `[ "$(settings get ... notify_row_style)" = "light" ] || fail "样式没切到 light"` ——
  **把自己刚写的值读回来必然成立**，是条自我满足的假断言：它既不能证明样式生效，
  也不会在真的失效时报红。已把写入与那句假断言一起删掉，换成注释说明"样式键随 iOS 卡移除，
  `isLightStyle` 恒真"，免得以后有人照着旧记忆把它加回来。

顺手用同一把尺子复核了另外三枚键，确认可疑的是那一条而不是全部：
`betteroriginos_notify_verify` = `VERIFY_KEY`（`Settings.Global.getInt` 直读）✓、
`notify_expand_test` / `notify_stack_test` = `EXPAND_TEST_KEY` / `STACK_TEST_KEY` ✓、
`sep` 的取值集合是 `off|os5`，脚本写的正是这两个 ✓（历史 DEVLOG 里出现过 `sep=split`，
那是改名前的旧值，只作历史记录，不当现状读）。

检查器与它的自测都进离线套件：`bash work/offline-all.sh` 现在 **18 项全绿**
（自测是"注入一个假键必须红、真键不误报"，防止检查器自己变成哑闸）。

## 2026-11-02 22:01 · 第五轮审查：这条面最要命的两条，都是"从来没生效过的代码"
详见 `out/LOG-1102-OS5-TO-OS3-ADAPT.md §11`。要点：
① 主题档位**一直读不到**（`level()` 拿 Context 的 classloader 去找 vivo 类 ⇒ BootClassLoader 看不见），
半径被钉在兜底 18dp，并且 `hookG2Path` 会把这 18dp **写回** `mOutlineRadius`，把 24dp 机型压成 18dp；
只有开着度量日志时那句 `level(view)` 才把它纠正回来——也就是**验证开着才对，用户用着是错的**。
② `applyRowRoundness` 调的 `setTopRoundness(float)` 在 A13 不存在（只有 `(FF)Z`），异常被吞，
三行从没跑过；而"A13 没有圆角传导"的注释也是错的（A13 自己按位置驱动），所以**删覆盖**而不是修签名。
③ 7dp 收纳栏间距 A13 没有通道（A15 靠 `setYTranslation(pad+stackY)`，A13 全 dex 无 `setYTranslation`），
我们的写入只挪动了两个无关判定的阈值——已停写，日志改成"从视图读回 `mTopRoundness`"，
验证器原来是**自己打印自己比较**的假断言，现在 `readback<0.999` 会红。
④ 背景裁剪读的是一帧前的 bounds，改成按 A13 自己的公式算本帧矩形（算不出才退回），并打 `stale=` 见证。
另外这一轮的自测把我一条**空炮反例**（篡改已不存在的 `shelf gap=21`）抓了出来，
从此要求每条反例必须真的改动材料。验证器 3 正例 + 28 反例、离线 18 项全绿，包 `b63f50b2c0b6`。
待办两条已在 ADAPT §11 列出（异常粒度/applied 计数；bgclip 四角断言 + setRadius 后 invalidate + RippleDrawable）。

## 2026-11-02 22:10 · 7dp 间距不能一删了之：找到 A13 自己的通道补上
上一条我写下"A13 没有 7dp 通道 ⇒ 不写了"，那是半途结论 —— 移植目标就是收纳栏渲染逻辑一致，
间距不该因为字段不同名就丢。A13 的算式在 `OS3-NotificationShelf.java:254`
（`updateAppearance` 里 `shelfState.yTranslation = min(...)`，A15 同一处多一个 `+ mPaddingBetweenElements`），
所以加钩 `updateAppearance` 之后对 `getViewState().yTranslation` 补一次 gap，字段名是 `yTranslation`（不是 A15 的 setYTranslation）。
关键是幂等：那一行只在栏里有可见子项时重算，否则保持我们写的值 ⇒ 无脑 `+=` 会每帧累加；
用 WeakHashMap 记住每个 state 上次写出的值，命中就先减回基线。
见证从"自证字段"（`gapPorted=0`）换成**真实位移差** `shelf gap yFrom/yTo/want`，
验证器配两条反例：累加两倍 (612→654) 与根本没动 (612→612) 都会红。
旧的那条 `gapPorted` 反例被"篡改必须改到材料"的守卫当场抓成空炮并删掉——守卫本身自证有效。
同轮清完 §11 待办：bgclip 四角断言、`setRadius` 后 `invalidate`、未识别 drawable 打 `unhandled cls=`、
`hookShelf` 的 NSSL 段独立 try 且 setDismissVisible 先记账。
`os3-probe` 复核 `NotificationShelf.updateAppearance` 在 A13 可钩 ✅；3 正例 + 28 反例、18 项全绿，包 `f24325c2fb15`。

## 2026-11-02 22:15 · 把 A15 零干扰门禁在最新包上重跑，并修掉"证据被覆盖"这件事
第五轮改完（`f24325c2fb15`，本机 `sha256sum module/build/BetterOriginOS.apk` 前缀一致，
所以设备上跑的就是这轮代码），把**这台机上唯一不需要解锁的门禁**重跑了一遍：
`adb install -r` → kill SystemUI（pid 23195 → 24171）→ `logcat -d --pid=24171` →
`python work/verify_os5_mirror.py --notify-only --expect-inert work/z8os3-live/a15-load-check-1102.log`
→ `ok inert`，逐条读数：`os5_mirror:` **0** 条、`installed feature` 2 条、`loaded com.android.systemui` 1 条、
`Fatal EXCEPTION|FATAL EXCEPTION` 0 条。设备侧结论与 §11 的静态判断一致：**复刻按版本关门，OS5 零干扰**。

同时补了 skill §5 那条"关键样本要立刻另存留档"的操作面：这份 `a15-load-check-1102.log` 在两次跑之间被**原地覆盖**，
也就是说 2476 行那段"`7fe5e2030bfd` 的 95 秒 pid 不变"引用的文件内容已经不是当时那份了。
现在把最新样本另存为 `work/z8os3-live/a15-load-check-1102-f24325c2.log`，以后同类门禁**按包哈希命名留档**，
别只留一个固定文件名。历史结论不因此作废（两次的形态一样：0 条 os5_mirror + 正常装载），
但"证据可复核"要求样本本身可复核。

## 2026-11-02 22:34 · 浮动钮判据对齐 A15 原文，外加一条我自己误判了 20 小时的 DSU
第六轮审查（自己拿两份全量 jadx 对照，不是代理）：OS5 画那枚 46dp 圆钮的真实判据是
`shouldShowClearAll() && mIsExpanded && !isQsFullScreen() && !isKeyguardShowing()`
（A15 jadx:6112-6113），重算点是 `setIsExpanded`（按边沿，:4231）与 `setQsFullScreen`（:4844）。
我的移植里 **少了 `!isQsFullScreen()` 整条**，展开判据还写成自造的"分数 > 0.5"。
顺带推翻两条我先前当作事实写下的东西：`NSSL.onExpandedChanged` 在 A15/A13 **都不存在**（全文检索 0 命中），
`getExpansionFraction()` 也不在两版 NSSL 上（在 AmbientState）——细节与表格式取证见 ADAPT §12。
代码改动、`gatedRecheck` 的每帧开销理由、有意保留的 `KeyguardManager` 偏离都在同一节。
构建 `3d13deeebbb9`，验证器 3 正例 + 30 反例（漏网 0，新增两条 QS 反例），`offline-all` 18 项全绿。

**另一条更要紧的自我更正**：我今夜早些时候报告"OS3 的 DSU 镜像不在这台机上（`/data/dsu` 不存在），
要跑得你点头重装"。用户直接说"dsu 一直装着"，一查就对：
`gsid.image_installed=1`、`ro.gsid.image_running` 装好待启，用户一重启就进了 **Android 13 / vivo OS 13.5 的 OS3**，
KernelSU（`u:r:ksu:s0`）+ LSPosed 2.2.1 + 模块 scope（含 `com.android.systemui`）全在，
`/proc/$(pidof zygote64)/maps` 注入命中 6。错在两处方法：
① 我用 `ls /data/dsu` 判存在（这台的 DSU 状态是 gsid 的属性与服务，不是那个目录）；
② 我又拿 `dmctl status` 去问——这台上的 `dmctl` 只是 device-mapper 工具，**没有 `start-dsu` 子命令**。
⇒ 判 DSU 状态用 `getprop | grep -i gsid`；`dmctl`/`ls` 都不是证据。
这条已写进 skill §6，A13 真机门禁从"等用户装镜像"降级为"直接可跑"。

## 2026-11-02 22:52 · OS3 真机（DSU）第一跑：离线全绿的代码在设备上仍有整条是坏的
用户一句"dsu 一直装着"把我 20 小时前的错误结论推翻了：DSU 状态在 `gsid.*` 属性里
（`gsid.image_installed=1`、`ro.gsid.image_running=1`），不在 `/data/dsu`，而这台的 `dmctl`
根本没有 `start-dsu` 子命令——我当时拿 `ls` + `dmctl` 当证据，方向就错了。现在这台就是
**Android 13 / vivo OS 13.5 的 OS3**，KernelSU（`u:r:ksu:s0`）+ LSPosed 2.2.1 + scope 齐全，
`/proc/$(pidof zygote64)/maps` 命中 6，可以直接跑 A13 门禁。

三轮跑下来（`3d13deeebbb9` → `a65d49aad000` → `f90da9b509a5`），真机暴露的东西离线一点都看不见：
1. **圆角这条主功能整条是坏的**：`radius write failed` ×22、档位见证 0 条。
   原因是 `initDimens` 钩子在**构造函数**里就跑，此时 `mBackgroundNormal` 还没 `findViewById`，
   我们却去调了 `applyRoundness()`（→ `mBackgroundNormal.setRadius`）⇒ NPE。
   修法是背景没就绪只写字段 + 另挂 `ActivatableNotificationView.onFinishInflate` 补一次。
   第二轮真机读数：`radius=24 g2=10 shelf=2 clear=1`（第一轮是 `radius=0 ... clear=0`）。
2. **一条假见证**：`sClearAttaches` 打进日志却全文件没自增 ⇒ "从未补挂"与"补挂过"不可区分。
   已修，并新增离线检查 `work/witness-counters-check.py`（20 项套件含其正负自测）。
3. **&& 短路把见证饿死**：`clearAllShouldShow` 里 `sDismissAllowed` 放最前 + 收起态
   `mIsExpanded=false` ⇒ `isQsFullScreen` 从没被读过，挂载日志永远打 `qs=untried`。
4. **两条假红灯是检查器自己的错**：第 6 步把 `clear-all native=` 探针算成"复刻没关住"；
   第 7 步拿 A 相 pid 当基线，而中间 B 相自己重启过 SystemUI ⇒ 报"批量移除中崩了"。
   核对过 A 相全程没有非人为死亡（`vivocameraserve` 的 SIGABRT 循环是这镜像的厂商相机服务）。
   真正的崩溃判据挪到点击之后（这才是它该保护的东西）。
5. **门禁第 5 步从来没跑过校验器**：`export MSYS_NO_PATHCONV=1` 让 `/c/Users/...` 原样进了
   Windows python ⇒ `C:\c\Users\...` 找不到文件。`os3-smoke.sh` 里本来就有 `cygpath` 的注释和写法，
   两个新脚本又踩了一遍 ⇒ 统一 `win()`/`cygpath -m`。

`bg clip` 一条日志都没有这件事我**没有**当场下结论：A13 的 `mCornerRadii` 来自
`mCurrentTopRoundness * mOutlineRadius`，系统不给圆角的行本来就是 0，所以"没裁剪"可能是原生行为；
但它和"反射字段名漂了""矩形算不出来"三种情况在日志里长得一样，所以第三轮先加
`bg clip skipped reason=… maxR=…` 见证，**拿到真机 reason 之后再决定验证器该红还是该绿**。
细节与逐条证据在 ADAPT §13。

## 2026-11-02 23:22 · A11 卡在 OS3 上第一次被"展开"，当场把我自己的一条断言证伪了
OS3 门禁原来只测收起态：`a11 art state=` 从来只有 `collapsed`。全盘检索确认
**`state=expanded` 这条见证在任何一台机器上都没出现过**（只有验证器的 fixture 里有，
那是我按预期写的，不是真机数据）——所以"展开态排版一比一"此前只能算离线推演。
给 `MediaCardFeature` 加了一个调试展开入口（挂 `betteroriginos_notify_expand_test`，
调系统自己的 `ExpandableNotificationRow.expandNotification()`，A13 jadx:1327 =
`mExpandClickListener.onClick(this)`，等价于用户点一下，比盲 tap 稳），第一次拿到：

```
a11 art state=expanded size=156 scaleType=CENTER_CROP clip=true titlePx=48 subtextPx=42 titleMedium=1
a11 palette expanded base=ff404040 L=25.1% highlight=ff666666 grid=10368
```

字号/封面/裁剪全对，但验证器红在 `titleMedium=1`。**根子是我的断言，不是设备**：
我写代码时假定"A11 展开卡标题是常规字族，medium 只属于收起态模板"。回查 Y7s 一手证据：
`media_view.xml` 标题 16sp 的 `fontFamily=@0x0184021d`，艺名与时间 14sp 用 `@0x0184021c`
（`xml_media_view.txt:136/145/153`）；同一对 id 在 Y7s SystemUI 自己的样式里就是
"强调行 vs 普通行"（`style/Control.Spinner` 当前值 → 21d，`Spinner.Item` → 21c，
`sysui_res.txt:145332/145338/145346`）。⇒ 展开态标题本来就该是强调字族，
于是改动是两边一起纠正：验证器把期望翻成 `titleMedium=1`（反例同步翻向），
模块把展开分支与产线 build 里的 `setTypeface(null, NORMAL)` 换成共享的
`sans-serif-medium` 实例（身份比较的见证才可信，各自 `create()` 可能拿到不同对象 ⇒ 假红）。

**证据强度说清楚**：`0x0184021d/21c` 的名字我没解出来 —— framework-res 的 type 只到 `0x17`，
`vivo-res.apk` 是包 `0x03`，RRO 与 mediatek-res 都没有 type `0x84`；
所以这是**按用法对照的推断**，不是一行字符串常量。能一锤定音的两条路都记在
ADAPT/A11-RE §20.3：Y7s 展开后读 `getTypeface().getFamily()`（要解锁），或找到定义 type 0x84 的那个包。

**还欠的一块（诚实记录）**：这台 DSU 上播放器没有封面，取色链只验到 `artwork=false`
的兜底分支（`base=ff404040`、`grid=0/0`）；`artwork=true` 那条只在离线 JVM 字节码测试和
A15 早期样本里验过，**OS3 上还需要一个能出封面的播放场景**。
包 `7bae013687ae`（含媒体调试展开入口 + 字族修正）正在跑 OS3 A11 门禁复跑。

## 2026-11-02 23:28 · A11 展开态在 OS3 上"展不开"的实况（先给读数，不再盲试）
为了拿到展开态见证，我给 `MediaCardFeature` 加了调试展开入口，并在三轮里逐步加见证：

| 轮 | 包 | 读数 | 结论 |
|---|---|---|---|
| c | `8ff6fc4d15a3` | `debug expand row=ExpandableNotificationRow cards=2`，随后出现 `state=expanded` 见证 | 展开成功过一次 |
| d | `7bae013687ae` | 只有 collapsed | 早退命中了另一张卡（`CARDS` 是 WeakHashMap，迭代顺序不定） |
| e | `546d5bf33d6b` | `cards=2 expanded=2`（改成全部展开） | 调用发出去了，但**没有任何效果证据** |
| f | `3b8d7682948e` | `called=2 isExpandedAfter=false true` | 一行真的展开了（`isExpanded()=true`），**但我们的卡没有重跑展开态几何** ⇒ 没有 `state=expanded` 见证 |

也就是说：`expandNotification()` 在 OS3 上是有效的（`config_enableNonGroupedNotificationExpand=true`，
`dsui-systemui.apk` 的 `0x7f050012` 实测为 true），问题在**我们这一侧**——
卡片的展开态排版只在绑定（`setExpandedChild`/restyle）时应用，行被展开但绑定没重跑，
于是 `A11MediaSpec.TITLE_SP/ARTIST_SP/TIME_SP` 那套 16/14/14sp 在 OS3 上至今没有真机读数。

下一步该做的是"展开时把已有卡片重算一遍"，而不是继续在门禁脚本里加 tap。
我不再盲目重试第 4 轮，先停在这里汇报：因为这同时牵出两个要你定的问题
（退出 DSU 回 OS5 的时机、以及 A11 展开态是否要在 OS3 上补这个重算路径）。

## 2026-11-03 12:0x · 用户目测否决：我把"钩子全绿"当成了"移植完成"，OS3 的排版其实没做
用户原话："光是 os3 现在这个通知栏就是做的一坨屎，通知上下之间没圆角过渡，大图标依旧只把左上角图标放大，
os5 那时候踩坑的排版一点都没做。" 我不辩解，直接量（`sep=os5`、density 3.0、uiautomator dump
`work/z8os3-live/os3-shade-1103.xml`，9 行通知）：

| 项 | OS3 实测 | OS5 目标（本模块 light 样式） |
|---|---|---|
| 行间空隙 | 21px = **7dp** ✓（这条移植到了） | 7dp |
| 应用图标 | `android:id/icon` 48×48px = **16dp**，x=120..168，仍在 header 左上 | **42dp** 大图标进内容列 |
| `right_icon` 大图标槽 | **dump 里不存在该节点** | 存在 |
| 标题 | 紧贴 header（61px 高），**无 8dp 抬升** | 抬升 `LIGHT_TITLE_RAISE_DP=8` |
| 行圆角 | 每行统一 20dp 平角 + `row_divider` 3px；相邻行接触边无过渡 | 按位置 topRoundness/bottomRoundness 传导 |

代码层的成因（不是"效果差"，是**功能没启用**）：
- `final boolean iosColumn = !OsProfile.isA13OrLower();` → `size = dp(iosColumn ? 42 : 38)`
  （NotifyRowFeature:2585/2654）⇒ A13 走"原生位置 + 小图标"，OS5 那套列布局一行都没跑到；
  我当时写的理由是"OS3 的 header 槽位窄"，等于把"有难度"实现成"不适用"。
- A13 分支 `applyRowRoundness(rowView, dp(20))`（:2866-2868）与 Os5Mirror 的档位半径（12/18/24dp）
  是**两套值在打架**，且没有按位置的 roundness 传导 ⇒ 用户看到的"上下没有过渡"。
- 另一个交代不清的点：我的门禁第 8 步会把 `sep` 还原成运行前的 `split`，
  用户平时看到的是**复刻关闭**的外观；这一点我之前没说明，导致"我报绿 / 你看差"同时成立。

**方法层结论（记成硬规矩）**：验收断言必须落在**用户能看见的结果**上（图标尺寸/位置、标题基线、
相邻行接触边的圆角状态），而不是"钩子装上了没"。钩子级全绿 ≠ 移植完成。
下一步顺序：① 先写结果级断言（现在必红）；② 读 OS5 light 列布局实现，把 A13 的槽位约束逐条量出来，
决定搬视图还是扩槽位；③ 统一圆角来源并补按位置传导。

## 2026-11-03 05:0x · 目标 A 返工：A13 上真正启用 OS5 行排版 + 逐卡圆角 + 摘要图标

设备：10AE4Y0MDE000BL（OS3/DSU，sdk=33，density 3.0）。装机 sha256 前缀：`cf40fd34ffd5`。

**做了什么（按用户可见结果描述）**
1. **删掉 `iosColumn = !isA13OrLower()` 版本门槛**：A13 现在与 OS5 走同一套 light 排版。
   实测：42dp(126px) 图标在行内 16dp(48px) 处、标题列 66dp(198px)、header 42→34dp（标题相对原生上提 8dp）、
   组展开子行同样拿到大图标。列宽沿用 DEVLOG 11-02 在 OS5 验证过的 **66dp**（不是按 42+12 推的 70）。
2. **圆角回到单一来源**：
   - 删掉 `applyRowRoundness/setBackgroundRadius/applyRoundnessToRows/installRoundnessHooks` 的旧覆盖
     （`isLightStyle()` 恒真 ⇒ 这些调用本来就不可达，交接文档说的"20dp 与档位打架"不存在）。
   - 新增 `NotificationRoundnessManager.getRoundnessFraction` 钩子，按**卡片角色**给系数（仅 `sep=os5`）：
     独立行 1/1、组摘要 top=1（展开有子行时 bottom=0）、子行 top=0/末子行 bottom=1、shelf 1/1。
     原生已给 1.0 的情形（滑动/清除/首行）不覆盖，避免打断动画。
   - 切 `sep` 后调用 `updateNotificationRoundness()` 强制重算，否则旧值不会自己刷新。
   真机读数（`verify-row-cf40fd34ffd5.log`）：独立行 `[54,54,54,54,54,54,54,54]`、
   展开组摘要 `[54,54,54,54,0,0,0,0]`、末子行 `[0,0,0,0,54,54,54,54]`、shelf 1/1。
3. **vivo 服务的分组摘要没有大图标（用户报的问题）**：A13 的分组摘要不走
   `NotificationContentView.setContractedChild/setExpandedChild`，`restyle` 从未被调用。
   改为在 `getRoundnessFraction`（必然每轮都调）里触发 `polishSummaryRow`：
   在**行**上加 42dp 图标、按资源 id（`:id/notification_header`/`app_name_text`）找可见 header
   （vivo 的 header 类名不保证是 `NotificationHeaderView`）、隐藏原生小图标、应用名收敛到 66dp。
   实测 3 个分组摘要都出现 126px 图标（`summary icon pkg=com.betteroriginos|android`）。
4. **结果级验证器** `work/verify_row_layout.py`（10 组反例自测，已挂进 `offline-all.sh`）：
   断言图标 42dp/16dp、标题 66dp、header 34dp、逐位置圆角与背景四角 = roundness×R。
   真机证据 `work/z8os3-live/verify-row-cf40fd34ffd5.{xml,log}`：5 项通过 / 0 失败。
5. 顺手修 `witness-counters-check.py` 的假红：前缀 `++sX` 是合法自增，旧正则只认后缀。

**对交接文档的更正（就地记录）**
- §4 D2 的"`applyRowRoundness(dp20)` 与档位打架"不成立：该路径不可达；真正的成因是
  A13 的 `NotificationRoundnessManager` 把**整列**当成一张连续卡（只有 firstInStack 的 top 与
  shelf 的 bottom 是圆的），与 OS5 的"每应用一张卡"不同。
- §6.3 "改 applyRowRoundness" 应改为"在 `getRoundnessFraction` 上按卡片角色给系数"。

**仍未做/未验**
- 摘要 header 高度仍是原生 132px(44dp)（强改会被 children 容器覆盖）；应用名收敛到 70dp（目标 66dp，差 13px）。
- 部分行（如 LSPosed 单行）header 文本在 42dp，未逐个对齐到 66dp——属于同一套 header padding 口径，
  需要按行类型再核一次。
- A15 侧真实排版基准仍未量（需退出 DSU）；用户手势展开路径未验；A11 线未动。

## 2026-11-03 05:2x · 排版收敛到 OS5 基线 + 摘要图标（用户报"vivo 服务多通知没大图标"）

装机 sha256 前缀 `da4a6653632c`（同一轮里 072d/bdea/f41a/5d2b 是中间态）。全部判据来自
uiautomator dump + 模块日志，无截图；不退出 DSU、未提交 git。

**OS5 真机基线（复用 11-02 的 `work/z8os3-live/ui_final.xml`，density 3.0）**
- polished 行：`notification_header` = **102px(34dp)**、`app_name_text` x=**258**（相对行 198px=66dp）。
- 未 polished 的摘要：icon 在 12dp、header 111px、应用名 36dp —— 说明 66dp 列只适用于被接管的行。

**改了什么**
1. `witness-counters-check.py`：只认后缀 `x++` 会把合法的前缀 `++x` 误判成假见证（MediaCard 的
   `sExpansionRestyles` 就是这么被冤枉的），改成前后缀都认。
2. header 文本列不再只靠公式：新增 `convergeHeaderColumn`（量一次改一次，≤5 轮 ×100ms），
   并且**只认所选 header 子树里的 `app_name_text`** —— 行里同时存在收起/展开两份 header，
   跨子树取值会把 padding 加到另一份上（日志实证：`x=99 配 hdrPad=159` 这种矛盾读数）。
   重复加的防护：观测位置没变且刚改过 → 不加（防过冲）；位置没变但已过 250ms → 再补一次
   （vivo 布局会把 padding 顶回去）。
3. **组摘要补大图标**：A13 的摘要不走 `setContractedChild/setExpandedChild`，restyle 从不调用。
   挂在每轮必调的 `getRoundnessFraction` 上触发 `polishSummaryRow`：按资源 id 找**可见** header
   （vivo 的 header 类名不保证是 `NotificationHeaderView`）、行上放 42dp 图标、隐藏原生小图标。
4. 常态维护：非摘要行也走同一钩子做列收敛（400ms/每 header 节流，链内不重叠）。
5. 圆角快照 `dumpRoundness` 增加 `phase=live`（每 5s），此前只有切 `sep` 才打，
   验收脚本在稳态面板里取不到读数（R0 假红就是这么来的）。

**真机结果**
- `app_name` 相对行：**198/198/199/198/198px = 66dp**，与 OS5 基线一致（`geom-col3.xml`）。
- 分组摘要拿到 126px 图标：`summary icon pkg=com.betteroriginos|android`（vivo 服务分组即 `android` 那些行）。
- 结果级验收 `work/verify_row_layout.py`（新增 L5/L5dev，11 组反例自测）在
  `verify-row-da4a6653632c.{xml,log}` 上 **7 通过 / 0 失败**；
  `os3-smoke-os5mirror.sh` **19/19 全绿**（含 sep=off 负对照、点击后 pid 不变、crash buffer 空）；
  `offline-all.sh` 全绿。

**已知偏差（显式报出，不当假绿）**
- 组子行（A13 给子行也画了 app 名 header）文本仍在 126px：改 header padding 对它无效
  （文本位置由别处驱动），而 OS5 基线的展开子行根本不显示这一层 header。
  验证器以 `L5dev` 行报告，不判红也不隐藏。
- 摘要 header 高度仍是 132px(44dp)：写 `LayoutParams.height` 被 `NotificationChildrenContainer`
  覆盖（真机 `summary.geom hdrH=132` 两次复现），已停止尝试并注释说明。
- A15 侧渲染基准仍未实测（需退出 DSU）；手势展开路径未验；A11 线未动。

### 05:4x 收尾（同一轮内）：组子行也对齐 + 诊断日志不再挤掉证据

装机 `a43b8e9316d2`（中间态 b28cb7691689 证明了子行修好的那一刻）。

1. **组子行的应用名不由 header padding 驱动**：祖先链是
   `row → children_container → row → expanded → template → notification_header(RelativeLayout)
   → notification_top_line(x=186)`。改 header padding 时 `top_line` 一动不动（真机两次复现）。
   改成 `shiftHeaderText`：优先给 `notification_top_line` 加 `leftMargin`，找不到才退回 header padding。
   结果：**6 行 app_name 全部相对行 198px = 66dp**，与 OS5 基线一致。
2. **子行走不到圆角钩子**：vivo 的组子行系数由父行 `applyChildrenRoundness` 直接下发，
   不经过 `getRoundnessFraction` ⇒ 挂在钩子上的常态收敛对子行永远不触发。
   改为在每秒一次的补扫遍历里递归到子行（`convergeNestedRows`，深度≤3）。
3. **自己的诊断日志把门禁证据挤掉了**：`notify_row.col` 每轮每条都打，A 相里刷屏后
   启动期的 `g2 path / shelf gap / clear-all attached` 被冲出 logcat 窗口，
   门禁报 3 条"没有日志"（`GATE=1`）。修法：每个 header 只打一次（`sColLogOnce`），
   圆角快照从 5s 放宽到 15s。重跑门禁回到全绿。
   **教训**：加见证时要算日志量——见证淹掉别的见证，比没有见证更难查。

**最终状态**（`a43b8e9316d2`）：`os3-smoke-os5mirror.sh` 全绿（含 sep=off 负对照、
点击链路、pid 不变、crash buffer 空）；`verify_row_layout.py` 6 通过 / 0 失败
（L5 应用名同列 66dp 覆盖 5 行，含组子行；不再出现 L5dev）；`offline-all.sh` 全绿。
证据：`work/z8os3-live/verify-row-a43b8e9316d2.{xml,log}`、`os5mirror-on.log`。

## 2026-11-03 11:0x–11:3x（设备 19:0x–19:3x）：三个观感缺陷的真机取证闭环，装机 `c8468e93ecf2`

用户报的三条（①点展开所有大图标抖一下 ②从收纳栏划回来有概率丢圆角 ③上下滑动时收纳栏消失闪烁）
这轮全部**用 dump+日志量出来**，不再靠"改了应该就好"。取证脚本新增
`work/os3-evidence-rowlayout.sh`：三态成对 dump+log（idle / expand / scroll），
逐态跑 `verify_row_layout.py`，再单独量化症状。

**最终读数**（`row-c8468e93ecf2-{idle,expand,scroll}.{xml,log}`，apk 哈希已与设备核对一致）：
三态各 9 通过 / 0 失败；展开手势窗口 polish 重复 = **0 次**（同阶段最高行 642→1323px，
证明展开真的发生了）；滚动窗口收纳栏可见性跳变 = **1 次**（修复前同一套手势实测 11 次）、
收纳栏圆角度量 5 次、圆角与背景四角一致 42 行。

### 1. 抖动：幂等 = 不销毁 + **写入前判等**

`applyLightPolish` 复用已有图标实例（`recycled`）而不是销毁重建，`alignLightIcon`
在 `|当前绘制位置 - 目标| ≤ 1px` 时**不写** translationY。只做到前者不够：
绑定时 polish 仍会跑两次（本轮实测 16 次 / 8 行，间隔 3ms），第二次若无条件写就是一次可见位移。

### 2. 图标纵向"居中"：四次定位，参照物才是根因

这一段最贵，每一步都是**证据推翻上一版假设**，记下来免得再走：

| 轮次 | 当时做法 | 观测 | 结论 |
|---|---|---|---|
| 1 | 只在 `grew`（被钳位）时补校 2 次 | 停在 **-52px** | 补校窗口内卡片一直是 138px，钳位值被永久保留 |
| 2 | 常态维护挂在圆角钩子 + 补扫上 | 见证 37 条全是 `skip=no-icon` | 从行 `findViewWithTag` 根本找不到图标，这条维护路径不成立（删掉） |
| 3 | 目标 = 文字块中心与卡片中心的**中点** | 停在 **+24px** | 不是没算对，是公式本身的落点就超出验收线（L6 ≤20px） |
| 4 | 改成"居中于卡片"，仍差 **20.5px** | 见证 `card=[453+297]`，而 dump 里可见行是 **255** | `lightCardOf` 往上撞到一个 297px 的**孪生行**，参照物选错了（(297-255)/2 = 21 ≈ 20.5） |

最终：**参照物 = 图标自己的宿主**（`status_bar_latest_event_content`，取证里与可见行逐条同界
1107..1362 == 1107..1362），监听宿主的布局变化重算（尺寸**或位置**变都重算：vivo 会把内容框
整体下移而不改高度），写入前判等跳过 ⇒ 每轮布局回调也不抖。目标口径改回"居中于卡片"
（用户原话"大图标没居中"），8dp 内缩保证不越界。

### 3. 收纳栏闪烁：承重墙判据 + 见证配额

`hideInline` 用 `INVISIBLE(4)` 而不是 `GONE(8)`：A13 的 `NotificationShelf.changeShelfVisibility`
里 `mDismissAllNotification.getVisibility() == 8` 就是"系统允许清除"的读法，写成 GONE 会把
判据污染成 false（真机 A/B：同一套滑动手势，修复前 11 次 `shelfVis 0↔4` 跳变，修复后 0～1 次）。

### 4. 验证器自己的两个假绿被抓出来（这类比代码 bug 更危险）

- `shelfgap` 原来断言 `yTo == base + want` —— 而 `base+want` 正是实现自己算的表达式，
  **永远绿**。改成帧间关系判定：施加帧要 `base == yFrom`、幂等帧要 `base == yFrom - want`，
  而"上一帧刚写出的位置又被当成原生基线加一遍"= 每帧累加，直接报红；新增反例
  「间距基线来源不明」，fixture 跟 `base=` 行形。
- 症状①的计数原来看**整个窗口**：`logcat -c` 放在投递样本之后就只会抓到"0 行 polish"，
  而 0 行被当成"没抖动"——空转的判据。现在清零放在投递之前，并按时间把投递阶段与
  展开手势阶段切开统计，投递阶段 0 行时**报红**（说明窗口不对）。

### 5. 取证对象必须可核对（本轮真踩过）

`build.sh` 编译失败，但命令链里 `| tail` 把退出码洗成 0，于是**装着上一个 APK** 跑完整套
取证还全绿。现在脚本第 0 步比对设备上 `pm path` 的 sha256[:12] 与本地构建，不一致 `exit 2`；
APK 不存在同样拒绝取证。

### 6. 落点自检的静默路径

`scheduled 有、verdict 无` 之前分不清"没排上"和"排上了没跑"。现在：第一跳打
`os5_mirror landing tick#1 ...`，`!sEnabled` 分支报 `landing interrupted` 并允许下次重排，
`postDelayed` 的 catch 不再静默。本轮抓到 `tick#1 → reachable radius=20 g2=10 shelf=2 clear=1`
（`os5mirror-on.log`），证明链本身是跑的；门禁侧同时接受"无 verdict 但 g2/shelf 几何见证齐"。

**最终状态**：`os3-evidence-rowlayout.sh` 全绿（三态 9/9×3）、`os3-smoke-os5mirror.sh` 全绿
（31 条通过 / 0 失败，含 sep=off 负对照、pid 不变、crash buffer 空）、`offline-all.sh` 全绿
（含 `verify_row_layout.py` 15 组、`verify_os5_mirror.py` 正反例）。

**已知残留（显式报出）**：绑定时 polish 仍跑两次（写入判等后无可见位移，但没消掉重复计算）；
A15 侧渲染基准仍未实测（约束是不退出 DSU）；症状②的"划回来那一瞬间"是过渡态，
取证只能覆盖它落回稳态后的 42 行一致性，瞬时帧需要 mid-drag 采样才有直接证据。

## 2026-11-03 11:50–13:40（设备 19:50–22:40）：组展开/收起的大图标过渡 + header 时间对位（**未收口，交接点**）

用户诉求：①组展开时大图标"闪现"、收起时"字压上去后再瞬间消失"，要过渡；②右上角的时间压在图标上，
要留在右上角。①已完成并经用户确认（"淡了"）；②**未收口**，以下是可复用的全部事实。

### 1. 大图标 alpha 过渡（已生效）

- `NotifyRowSpec.ICON_FADE_MS = 170`，`PathInterpolator(0.25, 0.1, 0.25, 1)`。
- 展开：polish 时对组子行 `fadeLightIcon(..., 1f, "child-expand")`；新建那枚先设 alpha=0。
- 收起：驱动源 = `ExpandableNotificationRow.setChildrenExpanded(false)`（before 钩子）。
  **前置判断必须同时满足 `isSummaryWithChildren` 与 `childCountOf(row) > 0`**：
  系统对每个无子项的行也会调这个方法，不分青红皂白地淡出会把子行自己的图标摘掉，
  补扫再 polish 回来 → 打环（真机日志特征：`collapse … icons=2/4` 反复出现、
  同一枚图标 `fade=in` 后立刻 `fade=out`，观感"闪一下再没了"）。
- 淡出动画结束才 `clearLightBigIcon`，并把该 cv 从 `LIGHT_POLISHED` 摘掉，
  这样再展开时补扫能重新 polish 并淡入。
- 见证：`notify_row.light.icon fade=in|out … why=…`、`ramp t=60/120/180ms alpha=…`、
  `notify_row.light.collapse via=setChildrenExpanded row=… icons=N`、`light.icon removed why=…`。
- 用户验收通过："淡了"。

### 2. header 时间对位（未收口）

观测（density 3.0，卡片 60..1020）：组子行 `time` 在 **+126**（42dp），而 42dp 大图标占
+48..+174、标题列在 +198 —— 时间直接压在图标右半边。

| 尝试 | 做法 | 真机结果 |
|---|---|---|
| 1 | 屏幕坐标推 `time` 到文字行右端 | 展开动画期间祖先自带 translation，被算成时间自己的位移 → 越推越远、被卡片裁掉（"一秒多后消失"） |
| 2 | 布局坐标 + 绝对 translationX + header 布局回调重推 | "还在左边"（推的那份不是可见副本；且被父容器裁掉） |
| 3 | 目标改为 header 右内缩/箭头左缘；沿途 `setClipChildren(false)`；`setUserExpanded` **先手**推；两份副本都推 | "先回左边、最后被闪回右边"（=过渡里紧凑副本先可见） |

当前部署：`bed9a51a92c3`（尝试 3，未再取用户反馈）。

### 3. 逆向后确认的事实（下一轮直接用）

- `notification_header` = `android.widget.RelativeLayout`；`notification_top_line` = **裸 `android.view.ViewGroup`**；
  `time` = `TextView`；`header_button_container` = `LinearLayout`。
- OS3 SystemUI **没有** `NotificationHeaderView` 类；对应 wrapper 是
  `…row/wrapper/NotificationHeaderViewWrapper`（字段 `mNotificationTopLine`/`mAppNameText`/`mExpandButton`…），
  但该类里没有 `setX`/`setTranslationX` —— 时间位置**不是它摆的**。
- ⇒ 时间位置是"按状态直接摆"的结果（vivo header 的 onLayout 或 layout XML 规则），没有可借用的布局规则；
  任何事后 translationX 只在"同一份副本"内有效，换副本立即失效。
- 一行有收起/展开两份 header 副本，过渡中先后可见。
- 平移超出父容器边界会被裁掉：**dump 里节点消失 ≠ 控件不存在**（本轮两次误判就是把它当成了"没有时间"）。

### 4. 下一轮建议（未验证，按优先级）

1. **在 `OnPreDraw` 里补最后一手**：对"刚变可见且 time 仍在左"的副本写一次位移——这是唯一早于布局的时机。
2. 或者不写 translationX，改在 bind 时给 `time` 的 `MarginLayoutParams.leftMargin` 赋值（布局期生效，不会被重置）。
3. 找 vivo 真正的摆位代码：在 `work/systemui/classes5.dump` 里搜 header 相关类的 `onLayout`，
   以及 `res/layout/notification_template_*` 里 `time` 的 `layout_alignParentEnd`/`layout_toEndOf` 规则。

### 5. 工具、样本与事故

- `work/os3-evidence-rowlayout.sh`：三态（idle/expand/scroll）成对 dump+log + 症状量化；
  第 0 步核对"设备上 APK 哈希 == 本地构建"（`build.sh | tail` 会洗掉退出码，本轮因此跑过一次旧包的全绿）。
- `work/os3-evidence-groupfade.sh`：组过渡取证（当前判据仍只覆盖"收起淡出"，时间项未纳入）。
- `NotifyTest.postBulk`（14 条普通通知）+ `--es debug_notify postbulk`：把列表撑到溢出、制造组内多条样本。
- **事故**：用 python 按行号删除代码时锚点错位，把 `NotifyRowFeature.java` 的 2800 行替换掉；
  从 `C:\Users\Administrator\.qoder-cn\file-history\…@v4`（7144 行快照）恢复，diff 确认只含预期改动。
  教训：Java 文件手术只用精确字符串 Edit，或先备份；任何按行号批量改写都不要用。

### 6. 当前状态（交接）

- 设备包 `bed9a51a92c3`，`sep=os5`，DSU 未退出；`verify` 已关回 0、`svc power stayon false`。
- 本轮改动**没有**重新跑 `os3-smoke-os5mirror.sh` / `offline-all.sh` / `verify_row_layout.py`
  （时间项未收口，门禁数字暂时不作数）；未提交 git。
- 代码树里与本轮相关的改动：`NotifyRowFeature.java`（fade/time/collapse）、`NotifyRowSpec.java`（ICON_FADE_MS）、
  `debug/NotifyTest.java` + `ui/MainActivity.java`（postbulk）、两份取证脚本、`verify_os5_mirror.py`（shelfgap 断言重写）。

## 2026-11-03 15:59–17:15（设备 23:59–01:15）：header 时间对位收口 + A11 媒体卡按真身重做

装机哈希链：`bed9a51a92c3`（旧）→ `158a4517cc47`（布局期钩子）→ `a9ce0e72c4ea`（入口原因见证）
→ `7696911cb767`（summary-header 分流）→ `728cfef0ff20`→`415cfee2f322`→`085b9de3eb45`（A11 通知卡）。

### 1. 时间对位：根因是**布局规则**，不是谁把视图写歪了

DEVLOG 上一轮的"OS3 SystemUI 没有 NotificationHeaderView ⇒ 位置是 vivo 代码摆的"是错的。
真正的摆位者是 **framework 的 `android.view.NotificationTopLineView.onLayout`**
（A13/A15 同一个类；布局里标签写成 `NotificationTopLineView`，LayoutInflater 给无点号标签补 `android.view.`）。
它的规则是**按子节点顺序从左往右流排、GONE 跳过**（`work/y7s-a11/A11-*.java` 与
`work/fw-res/A13-framework-res.apk` 的 `notification_template_header.xml` 双向对上）。
vivo 组子行把 `app_name_text` 收掉 ⇒ `time` 成为第一个可见子节点 ⇒ 落在行首 +126，
压在 42dp 大图标占的 +48..+174 上。所以前三轮用 translationX + 延时补推永远在跟一条规则赛跑。

改法：钩 `NotificationTopLineView.onLayout`（after），**在同一趟布局里**把文本子节点整体
平移到 66dp 列；越界就整体不动并留证据。删掉 translationX 那套（`pushTimeRight`/
`hookTimeRelayout`/`pushTimesIn`/`alignHeaderTime`/`timeWitness`，共 ~180 行）。

真机读数（日志侧，不依赖 uiautomator）：
`notify_row.col measure … line=+126 time=+198 want=+198 d=72 moved=1 rowH=255`（两条组子行），
`L10 实测时间落点全部 ≥66dp 列`、`L11 col 早退只有 not-taken`。

两条**新长出来的判据**（先红后绿，跑在 `work/verify_row_layout.py`）：
L9=dump 侧"凡有 42dp 图标的行，时间左缘 ≥66dp 列，一条都读不到直接判红"；
L10/L11=日志侧实测落点 + 早退原因必须被后续实测补齐。自检 27 组，含
"时间还在图标上/一条实测都没有/未知早退原因/媒体行正当早退"等正反例。

### 2. `no-cv` 不是故障：vivo 分组卡的摘要 header 结构不同

真机祖先链：`NotificationTopLineView<NotificationHeaderView<NotificationChildrenContainer<ExpandableNotificationRow<…`
——它压根没有 NotificationContentView，本来就走原生文本列、也不带 42dp 图标。
给它单独一个正当原因 `summary-header`，`no-cv` 恢复成"真找不到归属"才报红。
（否则这条要么被静默放行、要么永久染红，两种都坏。）

### 3. 取证通道的两个真实故障（都是工具的，不是实现的）

1. **`uiautomator dump` 在这台 DSU 上 `could not get idle state`**：不是动画瞬时——重试三次全废。
   量化后 `dumpsys gfxinfo … Total frames rendered: 0 / 6 秒` ⇒ SystemUI 根本不重绘；
   现场 `mCurrentFocus` 是 `org.lsposed.manager`（用户睡前留的前台）。
   脚本现在：取帧前 force-stop 非系统前台、暂停播放中的媒体会话（播着的媒体卡每帧自愈重绘，
   真机 A/B 证明确实是它让窗口永不空闲：`dispatch pause` 后 dump 立刻成功）、
   dump 失败记录 wakefulness+焦点、并新增 `--log-only`：拿不到 dump 时**只判日志侧**，
   几何判据明确写"本轮未测"，不再把整轮作废。
2. **落空的 tap 有风险**：焦点不在面板时脚本仍按旧 dump 的坐标 tap。已核对 LSPosed 状态
   （模块 enabled、5 个 scope 原样）没有被改动；但这一步以后必须先确认焦点再点。

### 4. A11 媒体卡：以前抄的是**另一个界面**

`A11MediaSpec` 的常量全部指向 A11 的 **QS 播放器**（`media_view.xml`/`qs_media_album_size`/
`@style/MediaPlayer.Button`/`IlluminationDrawable`），而模块改的是**通知栏 MediaStyle 行**。
逐条逆完 A11 之后按后者的真身重做，证据与数值全在 `out/LOG-1103-A11-MEDIACARD-REVERSE.md`：

- 卡片底色：A11 `isColorizedMedia()` 对"MediaStyle+有会话"默认就是 true ⇒ 底色来自封面取色；
  未着色才是 `notification_material_background_color`（实测 `#ffffffff` / 夜 `@color/black`）。
  **删掉了"展开态恒深卡"（sat×0.8、L 锁 0.25）——那是 OS5/A15 的 `computeBackgroundColor`。**
- 正文：vivo-res 实读 `0x03080169 #ff000000` / `0x0308016a #ffffffff`（**完全不透明**）、
  副文 `#ff4d4d4d` / `#b3ffffff`；着色时 `findAlphaToMeetContrast(…,4.5)` 只会把文字**加实**。
  按钮色 = 正文主色（`config_tintNotificationActionButtons=true`）⇒ A11 原生不存在"按钮看不清"。
- 几何：封面是**边长=内容块高的正方形**、`centerInside`、**无圆角无底板**
  （A11 资源表里 `notification_corner_radius` 命中 0，圆角是 A12 才有的）；
  按钮 48dp 方形 + 内缩 8dp + marginEnd 2dp，三个同色同尺寸。
- 封面"渐变"真身：`ImageGradientColorizer`（Rec.709 浮雕矩阵 + 两段 `DST_IN` 渐变
  stops `[0,.4,1]`、`[0,.6,1]` + 染层 alpha **127**）；而"封面铺满背景"是**锁屏/媒体态的
  backdrop**（`NotificationMediaManager.finishUpdateMediaMetaData` → `mBackdrop` 双缓冲
  250ms 交叉淡入 + `setHasBackdrop`），**不属于这张卡**。
- 新增 `A11NotifSpec`（纯计算，可离线跑字节码）+ `A11ArtRelief`（逐指令复刻）；
  离线自检 `work/a11_notif_selftest.py` 78 项 + 8 个负对照全部有效，挂进 `offline-all`（23 项）。
- 验收器 `verify_os5_mirror.py` 的 A11 段按新口径重写：4 组正例 + 46 条反例全绿；
  `logshape_lint` 三次抓到"改了日志文案没改正则"（真价值，不是噪音）。
- 真机（Z8/OS3，style=a11）：`--media-only` 40 条断言 0 失败，含
  `a11spec: btn=144/144 pad=24 end=6 src=48 radius=0（宿主对照 6）`、
  `art: expanded size=249=contentH clip=false title=42px subtext=36px medium=1`、
  `a11: expanded bg=ffd8d8c8 primary=ff000000 contrast=14.58 grid=10368/12528`、
  `relief: side=144 src=144x144 color=ffffffff rtl=0`。

### 5. 已知残留（显式报出，不当已完成）

1. `android.colorized` 显式关闭那一支没实现：`MediaCard` 拿不到 sbn（卡片由视图树+MediaController
   建），现在按"有会话即着色"。要补就在构造里把 sbn 传进来。
2. 预布局帧 `contentH=-84`：卡片没测量时封面还是初始 lp 的 156px（QS 的 52dp 常量），
   下一帧由 post 修正。观感是一帧尺寸跳变，A11 侧不存在这个初始值。
3. QS 那套常量（`A11MediaSpec` 的 TITLE_SP/ARTIST_SP/SEEK_*/BUTTON_CENTER_DP/ART_*_DP）与
   通知卡用途混用的历史引用还没清干净；数值保留但需要改名/标注。
4. backdrop（锁屏整屏模糊封面）只做了**逆向说明**，没实现——它是另一个界面、另一个落点。
5. A11 的 RenderScript 高斯模糊半径 25 已核到字节码，但**模块内没有可用的 RS**（A15 已移除），
   自研模糊核与 RS 的视觉等价性还没量化比对（LOG-1103 §4b 标了这一点）。

---

## 2026-11-03 17:4x–18:2x（设备 01:4x–02:2x）：A11 媒体卡**第二轮**——逆到"谁在写这个值"，卡片按设备真身重排

装机产物 `e0856b5e1f3b`（设备 base.apk sha256 前 12 位一致后才开始取证）。
接上一节的 5 条残留，本轮把 §14/§7 的待办逆到底，并把 a11 卡片**结构**也换成 A11 的那一棵树。
完整逆向记录在 `out/LOG-1103-A11-MEDIACARD-REVERSE.md` §15–§21（每条都有 aapt2/jadx 产物路径）。

### 1. 这一轮真正逆到什么（不是又抄一遍数字）

1. **`scaleType=6` 是 centerCrop，不是 centerInside**：错在"按 ImageView.ScaleType 的序号读枚举"。
   framework-res 的 `attr/scaleType` 枚举表才是权威（`fw_res.txt:1010-1019`）。
2. **卡片进度条的真身**：`res/layout/notification_material_media_seekbar.xml` —— 轨道 3dp 写在
   `android:maxHeight`（数值与 QS 的 `qs_media_enabled_seekbar_height` 巧合相同、**出处不同**）；
   两枚时间在条**下方**左右贴边 16dp，字号 14**dp**；上下 padding 24dp 配 -12/-24dp 负 margin。
   负值之所以能读对，是因为先搞清了 aapt2 印 dimension 的方式（`data>>8` 无符号；
   `16777204dp` 其实是 `-12dp`）。
3. **`updateSeekBarTint` 是"谁在写颜色"的答案**：thumb/progress/track = 同一个 `originalIconColor`
   的 **255/192/128** 三档 alpha；而 `originalIconColor` 沿 `CachingIconView` →
   `Notification.BuilderExt.processSmallIconColor` 追下去，**等于**着色时的正文主色
   （`getPrimaryTextColor(p)`）。所以原生的条/时间/动作图标/小图标是同一支颜色，
   只是 alpha 分档——"按钮看不清"在 A11 逻辑里根本不该发生。
4. **`IS_TIER_VOS` 从待办变成事实**：`FtBuild.getOsName()=ro.vivo.os.name`（Y7s = `Funtouch`）、
   `ro.system.vivo.tierlevel` 不存在 ⇒ **false** ⇒ MediaStyle 走 `vigour_*_q` 模板。
   于是"设备那张 A11 卡"的常数与 AOSP A11 是**两套**：按钮 **36×40dp**（centerInside、背景 transparent、
   padding 8/6、margin 2/2）、列边距 42/20、minHeight **61dp**、标题 14sp+**BOLD**、副文 **12sp**、
   头部图标 16dp、动作带 marginStart 14dp。AOSP 那一支（48dp 方按钮 / 46-16 边距 / 39dp / 14dp 副文）
   在 `A11NotifSpec` 里以 `AOSP_*` 保留，两边都不许混。
5. **"布局文件写着 A、屏幕上跑的是 B"的一条**：vigour 的 ViewStub 写了 `android:layout=vigour_..._seekbar`，
   但设备 SystemUI 的 `resolveViews()` 在 inflate 前无条件 `setLayoutResource(AOSP 那份)` ⇒
   进度条实际用 AOSP 数值。判据打在写入者（setLayoutResource 那一行），不是打在声明处。
6. **浮雕的落点再确认一次**：`MediaNotificationProcessor.processNotification` 最后一行
   `builder.setLargeIcon(Icon.createWithBitmap(mColorizer.colorize(drawable, bgColor, isRtl)))` ——
   "封面铺在背景 + 渐变"是**把封面本身换成"底色 + 明暗浮雕 + 两段 DST_IN 渐变"**，
   在收起/展开都生效；整屏模糊铺底是锁屏 backdrop（§4b/§11），另一条链。

### 2. 代码侧改动（`A11NotifSpec` / `MediaCardFeature` 的 a11 分支）

- 卡片根：**LinearLayout → FrameLayout**（A11 就是 FrameLayout），封面 TOP|END 正方形（边长=内容列高）、
  内容列 TOP|START（头部行 44dp 叠加 + main_column marginTop 42dp 让开它）、
  顺序改成 文字列 → 动作带 → 进度条 → 时间行。
- 排版：标题 14sp medium+**BOLD**、副文 12sp、时间 14**dp**（单位不同 → fontScale 行为不同，
  验收器专门留了 `GOOD_FS` 与两条单位反例）。
- 进度条：三档 alpha 按 `originalIconColor`、3dp 轨道、thumb 随 `ACTION_SEEK_TO` 在 255/0 切且
  与 `setEnabled` **成对**纠正（旧实现压暗整条 bar 是自创的）；时间改 `DateUtils.formatElapsedTime`、
  `max=(int)durationMs`（毫秒标度）。
- 进度条容器显隐一条判据链（`controller!=null && duration>0 && expanded`），新增 `a11 seek vis=` 见证。
- 按钮改设备那颗 36×40/centerInside/无背景；动作带 marginStart 14 / marginEnd 20；LTR 强置。
- 头部行 44dp + 16dp 应用图标 + 12sp app 名（app_name_text 从被藏起来的宿主 header 里摘出来复用）。
- `A11MediaSpec`（QS 常数）在通知卡路径上的引用**清零**，类头写明"不要用进通知卡"。

### 3. 验收链的同步（否则断言会腐烂成绿的）

- `verify_os5_mirror.py`：A11 段按新口径重写正则与断言（按钮 36×40、列边距/最小高/头部行/动作带边距、
  centerCrop、`seekVis` 两态、新增 `a11 seek` 一整组断言——比的是**回读的画笔/视图状态**，
  不是实现里再算一遍同一个表达式），fixture + 反例同步扩充；`--selftest` 全绿。
- `work/a11_notif_selftest.py`：常数表换成设备/AOSP 两套并各自断言，新增 4 组 seekColors 比对，
  负对照从 8 个加到 **15 个**（含"串成 AOSP 那一支"、"时间错用 sp"、"thumb 与 enabled 不自洽"）。
- `work/offline-all.sh` 全绿（24 项锚点的 logshape 自检也抓到过格式漂移，这是它的价值不是噪音）。
- 真机门禁 `work/a11-card-gate.sh 10AE4Y0MDE000BL os3`：**133 条通过 / 0 失败**，无致命异常，
  SystemUI pid 全程不变。

### 4. 本轮踩到并修掉的两个"自己造的坑"

1. `titleMedium=` 见证改判 bold-medium 后仍拿 plain-medium 实例比 → 真机 6 条假红
   （收起/展开两态"没有可测量见证"是它的连带表现）。改成缓存 bold-medium 单例、按身份比。
2. 门禁用**覆盖式** `logcat -d` 捕获：模块每次建卡打十几行 ancestor 取证，环形缓冲区把
   "建卡那一拍"的 `a11 card spec` 挤出去 → 表现为"spec 日志缺失"的假失败。改成逐段追加，
   并在脚本里写了为什么。**取证脚本丢日志要当成失效来修，不能当成"这轮没发生"**。

### 5. 状态与残留（交给早上的视觉验收）

- 设备 style 现在是 `a11`（用户要的样式），`notify_expand_test` 已归 0，stayon 关、面板收起。
- 未收口：`android.colorized` 显式 false 分支（要把 sbn 传进 MediaCard）、锁屏 backdrop、
  OS5 宿主（A15，非 DSU）上的 a11 卡片观感——本轮所有读数来自 DSU(A13) 环境。
- 视觉项按老规矩交给用户：结构、封面位置、按钮大小这几处是本轮改动最大的地方。

### 6. 追加（18:36）：跑 rowlayout 取证时暴露的**另一条线**的问题（未修，先记账）

装机 `e0856b5e1f3b` 之后复跑 `work/os3-evidence-rowlayout.sh`，A11 那侧全绿，但 notify_row 的
L8/L9 报红。先把读数摆出来，不下结论：

```
build c8468e93ecf2（今天 11:3x 的那轮，postbulk 组，展开）
  row1 h1323 图标×4 | row2..5 h255 图标×1 时间×1   ← 展开子行：大图标 + 时间都在
build e0856b5e1f3b（本轮，postgroup 组 A/B，展开）
  row1 h642  图标×0 | row2/3 h255 图标×0 时间×1（time @+126、title/text @+198=66dp ✔）
  row5/6 h255 图标×1 时间×0（LSPosed 那两条 showWhen=false，本来就没时间）
```
判读：
1. **header 时间对位本身没退化**：被接管的子行 `time` 落点仍是 66dp 列（L10 绿、L11 只有 not-taken）。
2. 红的是**展开子行缺 42dp 大图标**（L8）与"L9 的样本前提不成立"（有图标的三条恰好是
   showWhen=false、没有时间的通知）。
3. 先排除了我自己的嫌疑：17:0x 那轮删掉的 `pushTimesIn / rowExpandTime / group-open 延时补推`
   只写 translationX（对着 `work/NotifyRowFeature.bak-1103-time.java:1843-1861` 逐行看过），
   不负责挂图标 ⇒ 删代码不是这个现象的因。
4. 同窗口有 `notify_row.light.collapse via=setChildrenExpanded` ×4：**收起摘要行时会淡掉子行图标**，
   之后再开组就依赖补扫把图标挂回来。最可疑的是"这一轮补扫没把子行图标挂回来"
   （或样本里那两条子行的重建时机不同）。要判它，必须复现"收起→再展开"这一条路径，
   而不是只看一次展开的 dump。
5. 取证脚本自己的判据也有一处**参照物错位**要修：`展开没发生` 用"最高行高度变化"当证据，
   而组摘要行本身就是最高行（642→642 时其实组一直是开着的）—— 应该改为比较
   **子行数量/子行高度**（h57 紧凑 → h255 展开）而不是全局最高行。

下一步（交接点，别从"改了参数没变化"开始）：
- 在真机上走"组开→组收→再开"三步各出一帧 dump，比对子行的 126px 图标数量；
- 若第三帧缺图标，就把见证打在**补扫入口**（带 `why=` 早退原因），先三分定位：
  ①没被调用 ②调用了但判成不需要动 ③调用了但算错；
- 顺手把上面第 5 条的判据改成子行口径，否则这个"假失败"每次组本来就开着的状态都会重现。

### 7. 追加（18:47）：上一条 L8/L9 的**真因是样本饥饿**，不是回归；取证脚本已修

按上一条的交接点复跑，抓到根因并当场修好：

- `am start ... --es debug_notify postbulk/post` 连着发时走同一实例的 onNewIntent，实测**没落进去**
  ⇒ 面板里只有最早的 `postgroup` 两条子行，而它们与 LSPosed 那几条都是同一批；
  有 42dp 图标的行恰好都是 `showWhen=false`（没有时间节点）→ L9 永远 0 读数，
  L8 在"组没真的被这次点击展开"的帧里也报红。
- 修法：每次投递前 `am force-stop com.betteroriginos`；`verify_row_layout.py` 的 L9 现在把
  "整屏没有 time 节点"与"有 time 但不在被接管行"分开报；`展开有没有发生` 改数**展开态子行**条数
  （原来拿"全局最高行"当证据，被媒体卡/已开着的组摘要顶掉了）。
- 复跑结果：**三态各 11 条断言全绿**（`L8 展开子行都有 42dp 大图标`、
  `L9 header 时间在 66dp 文本列（1 条读数）`、`L10 实测时间落点全部 ≥66dp 列（4 条，+198..+488px；none 0 条）`）。
  观察窗口那条仍按纪律报红：这一轮样本里没有"可被这次点击展开的收起组"，所以症状①这一轮**没覆盖**，
  不当已通过。要覆盖它跑新脚本 `work/os3-evidence-groupcycle.sh`
  （结构判据定位组摘要 → 开/收/再开四帧，实测四帧子行都有 126px 大图标，时间落点
  展开过渡帧 +126、稳定帧 +198 —— 也就是"动画中途"仍会短暂压图标，这条留给早上看观感）。

## 2026-11-04（早上用户反馈之后的那一轮）

### 1. 用户否决三件事，先做回退

- **"展开通知时大图标离奇消失"** → `NotifyRowFeature` 整文件回退到时间改造之前的备份
  `work/NotifyRowFeature.bak-1103-time.java`（那一版只有事后 translationX 推时间）。
  列对位那一版留在 `work/NotifyRowFeature.col-hook-1103.java`，没删。
- **"进度条不是这个样式"** → A11 卡改按 **QS 播放器**那一版画（`media_view.xml`），
  不用通知卡那一版：时间在条左右两端同一行、14**sp**、thumb 常驻、轨道 3dp。
  常数在 `A11NotifSpec.PLAYER_SEEK_*`，通知卡那一版的常数保留但不再喂给卡片。
- **"封面右边要有渐变过渡"** → 浮雕重做的触发条件从"封面变没变"改成
  **(封面哈希, 卡片底色)** 二元键，并留存 `a11RawArt` 原件；底色变化（换歌/切深浅色）
  会重算，右缘淡出的终点永远等于当前卡片底色。取色链末尾补 `syncArt()`。

### 2. 门禁为什么"看着像坏了其实是被限流"：logd 每进程 250 行/秒

真机取证连续两轮 `restyling style=a11` 与 `a11 card spec` 抓到 0 条，看着像 a11 路径没跑，
实际缓冲区里同一段的 `media bind`、`preview observer registered` 都在，**中间那两行没了**，
旁边就是 `W ratelimit: Single process limit 250/s drop 234 lines` —— SystemUI 启动/重绑那一秒
vivo 自己就刷几百行，我们夹在里面的行被 logd 丢掉。这不是"抓得少"，是**根本没写进缓冲区**，
所以追加捕获、延长窗口都救不了。

改法（两条都做了）：

1. 模块侧：只在**建卡那一拍**打的规格见证改成**低频重发** —— `MediaCard.a11SpecWitness`
   存原文，`applyA11ArtGeometry()` 里距上次 >4s 就再打一次；`NotifyRowFeature` 的
   `header-time=push-only` 自报同理挂在 1s 一趟的补扫上（`HEADER_TIME_MODE`）。
2. 门禁侧：`work/a11-card-gate.sh` 把"起播"做成 `play_once()`（投前先 `am force-stop`
   播放器，保证新 notification → 新行 → 新卡），缺 spec 就再走一遍；
   "a11 路径跑过"允许由 restyle 行**或**spec 行证明（两者同一拍，前者更容易被丢）。

### 3. header-time 的接管方式变成模块自报，验证器据此开关一整段

`notify row feature installed header-time=push-only|column-hook` 是 `verify_row_layout.py`
里 L10/L11（`col measure` 那套）的唯一开关：

- `push-only`（现在这版）→ 那套断言**显式停用**并打印原因，不再让"一条 measure 都没有"
  变成永远修不好的假红（回退之后钩子根本不存在）。
- `column-hook` → 照旧跑四路证据。
- 自报缺失 → 判红："分不清钩子没装与这轮没抓到日志"就没有结论。
  取证脚本 `os3-evidence-rowlayout.sh` 的启动期前提检查同步改成读这一行；
  `--install` 参数把清零前那份证据交给验证器（三态窗口都在 `logcat -c` 之后，
  而这一行只在进程启动那一拍打）。

### 4. 真凶：polish 的去重标记会过期，图标因此永远挂不回来

`work/os3-evidence-groupcycle.sh` 加 force-stop 投递 + 逐帧自动判定后，稳定复现
**"组收起再展开，两条 h255 子行一枚图标都没有，8 秒后仍然没有"**（用户报的那条）。

三分定位（见证打在入口，带早退原因）：

| 观测 | 结论 |
| --- | --- |
| `notify_row.heal skip reason=marked-empty cv=… root=… h=255 vis=0` | 补扫走到了（排除①） |
| 同一 (cv,root) 的可见根上 `findViewWithTag(TAG_LIGHT_BIG)` 为 null | 被 `LIGHT_POLISHED` 挡住（命中②） |
| `notify_row.light.icon removed why=fade-out` 之后没有再出现 polish | 不是③（polish 判"不该有"） |

机理：`LIGHT_POLISHED` 键在 **contentView**，而 A13 一行有收起/展开两棵子树，且淡出动画
结束时 `retireLightIcon` 把图标摘掉；vivo 重新展开时不一定重新 bind 子行，标记留在 cv 上
就成了谎话，而补扫只认标记 → 永久不挂。

修法（`healLightRows` 的标记早退处）：标记在、**可见根**上却没有图标 ⇒ 判为过期，
清掉标记走正常补扫。次数由现成的 `HEAL_TRIES`（每行 ≤6）兜住，而不是"每个 (cv,根) 只清一次"
—— 第一趟重排常落在动画中途（行还没长够高，`lightPolish` 自己按 group-collapsed 早退），
只给一次机会等于把这行永久留在坏状态。这条实测踩过：一次机会的版本 `reopened/settled` 仍红。

新增见证：`notify_row.heal rearm cv=… root=…`（与 `marked-empty` 各 2 条，逐帧 L8 转绿）。

### 5. 本轮的验收位置（build `b47ff3304428`，装机哈希一致）

- `work/offline-all.sh` 全绿（G2/A11 数学/Y7s 前提/验证器正反例 71 条/日志形状/键核对）。
- `work/a11-card-gate.sh … os3`：**全绿**，含 `padTB=48px`(=16dp)、`dur=3536000ms`
  `seekSession=1` 11 帧与 `session=0 dur=0` 6 帧两侧取样（进度条显隐判据三条与：
  有会话 && 有时长 && 展开 —— 只看 dur 会把会话被拆掉那一帧误判成回归）。
- `work/os3-evidence-rowlayout.sh`：三态各 **11/0**。
- `work/os3-evidence-groupcycle.sh`：五帧（before/opened/closed/reopened/settled）各 **11/0**，
  且脚本现在自己重启 SystemUI、自取启动期自报、逐帧调用验证器并返回退出码。

### 6. 仍未收口的两条

1. **展开动画中途** header 时间仍会短暂落在图标列（+126），稳定后回到文本列。
   布局期列对位那一版能治它，但它同时带来"图标消失"的回归，已整体回退，
   要重新上必须先解决 `LIGHT_POLISHED` 这类跨子树标记问题（现已有 rearm 兜底）。
2. vivo服务 组的 header 时间：本轮实测**稳定帧是对的**（子行 title=+198=66dp、
   time=+675px、大图标在），用户看到的"赖在左边"对应的是上面第 1 条那个过渡帧。

## 2026-11-04（中午）A11 播放卡三条观感反馈

### 1. 左边时间溢出：a11 的进度条那一排漏了左右内缩

`column` 自己没有 padding，每一件事都得自带 20dp 左右边距；`seekWrap` 是唯一漏加的那个，
于是 `elapsed` 落在 x=60（与卡片左边齐平）。实测修法后：容器 120..960、elapsed 120..234、
条 234..751、total 847..960（同一测里 `title` 在 120，即与文字列同一条线）。

### 2. 右边总时长"没反色"：不是没算色，是**底色与文字色来自两张不同的图**

现场 `a11 palette bg=ffffffff primary=ff000000` 配 `relief color=ff080808` ——
卡片画笔拿到的是"没取到封面"的浅色兜底，而屏幕上还贴着一张深色浮雕，黑字压深底。
现在两枚时间的**画笔实际色**进了见证（`elapsedColor=`/`totalColor=`），验证器要求
它们等于进度条基色、且**对同一帧的卡片底色 ≥4.5:1**；另外没有封面输入时**清空浮雕**
而不是继续画旧的，两边就不会再分叉。

### 3. 取色"系统性滞后一首"——先证伪了我自己早上的解释

我早先以为是"先读 metadata、后读 largeIcon"的优先级问题，改完优先级后用户说照旧。
他给的描述（红→白→蓝，播放白那首时左边还是红）说明**采到的图本身就是一首之前的**，
而且之后再也没有重取。加了三字段见证才看清楚：
`srcFrom=`（输入是哪一份）+ `srcHash=`（真正被采样那张图的指纹）+ `metaTitle=`（会话此刻曲名），
浮雕侧加 `hash=`。真机连切三首的读数是：

```
track changed, retinting: 鬼新娘…|Kael|147000
a11 palette … srcFrom=argb srcHash=1a13df86 … bg=ffd8f0f8      ← 取色拿到的还是上一首那张图
a11 art relief … hash=35f4616a color=ffd8f0f8                   ← 新封面后到，但底色没跟着重算
```

机理：换歌时**曲目事件先到、新封面图后到**。曲目事件里 `refreshColors()` 按 trackKey
（曲名|歌手|时长）去重并把它记下；一秒钟后封面真的到了，`watchArtArrival()` 只重画了浮雕，
`refreshColors()` 在入口就被"trackKey 没变"挡住 ⇒ 底色永久停在上一首那张图的取色结果。
修法：`watchArtArrival()` 比较 Drawable 身份**与** `artHash`，变了就把 `lastTrackKey` 作废，
再 syncArt()+refreshColors()。复测同指纹同源：

```
art drawable changed now=BitmapDrawable hash=bd385890
a11 palette … srcHash=bd385890 bg=fff8f8f8 … metaTitle=Cry For Me (feat. Ami)
a11 art relief … hash=bd385890 color=fff8f8f8 from=largeIcon
```

### 4. 顺手挖掉一个把取证通道打死的东西

卡片有个 **300ms** 定时器，每拍 `updateProgress()+syncArt()+refreshColors()`。
两个后果：①切歌空窗期被反复用旧 metadata 重取色（就是上面那条的一部分）；
②窗口**永远不空闲**，`uiautomator dump` 连报 "could not get idle state"，
我早上几轮媒体侧的帧全因此取不到。现在按 A11 改回 1 秒、**只管时间与进度**，
暂停就不挂；封面换图由事件（bind / onMetadataChanged / Drawable 指纹变化）驱动，
指纹比较走零拷贝那条（只 `viewArtBitmap`），每秒 36 个采样像素，不做栅格化。
暂停态拖动结束后补一次 `updateProgress()`，免得没有定时器就停在残值上。

### 5. 验证器被自己的日志格式坑了一次（记录以免重犯）

`metaTitle=(\S*)` 在真机上直接崩：歌名必带空格（`Cry For Me (feat. Ami)`），
12 行 palette 只解析出 8 条，而依赖它们的底色/阴影断言就在残缺数据上报了三条假红。
漂移守卫（"标记行数 ≠ 解析条数就判红"）这次真的救了场。改 `(.*?) gridBg=` .lazy，
并把**带空格的曲名写进正例 fixture**，让这条以后每次自检都被压到。
另：注释别写进 `re.compile(...)` 的续行里——`logshape_lint` 会把里面带引号的中文
当成正则字面量去模块源码里找，永远找不到就报"日志形状分叉"。

### 6. 本轮验收位置（build `c5a6001a8dd6`，装机哈希一致）

- `verify_os5_mirror.py --media-only`（真机日志）：**45 条通过 / 0 失败**，
  新增反例"A11 浮雕与取色不是同一张封面（滞后一首）"正确报红，共 **75 条反例全咬**。
- `work/offline-all.sh` 全绿；`logshape_lint` 全绿。
- 通知行侧本轮未改代码；`notify_verify` 已复位 0（所以单独跑 a11 门禁时通知侧没有日志，
  判媒体侧要带 `--media-only`）。
- 仍开着的一条：bind 那一拍偶尔仍拿不到封面（`srcFrom=none`），卡片会先显示浅色兜底，
  等封面图到了同一秒内 retint 成正确色。要消掉这一拍，得让建卡时就用 sbn 的 largeIcon
  （需要把 `sbn` 传进 `MediaCard.build(...)`，见 LOG §13-1）。

## 2026-11-04（午后）A11 播放卡第二轮：闪现小封面 + 硬切 + 一条"我把断言写严了"的记录

用户反馈："背景取色还可以"，但 ① 切歌时右边会**闪现小图标版的封面**；② 封面切换生硬、没有淡入淡出。

### 1. 闪现的是原生 `right_icon`，被 RemoteViews 在换歌那一拍设回可见

A11 的封面位本来就是 `android:id/right_icon`（`artId = id(ctx,"right_icon")`）。建卡时我们把它按成
GONE，但换歌时系统重新应用 RemoteViews ⇒ 它又可见一小会儿（那颗 36dp 的小封面就是它）。
按一次不够，必须**自愈**：挂在它自己的 `OnLayoutChangeListener` 上（`setVisibility` 会走布局），
只在"确实又露出来"时写回 GONE 并读回确认，不做每帧轮询（本项目所有"抖/闪"的指纹都是高频写+非幂等）：

```
a11 art native reshown rehidden vis=8 w=96 h=96     ← 正好落在切歌那一拍，一轮里 7 次
```

### 2. 封面过渡按用户要求加上，时长与同一块卡背景那次对齐

A11 原生是 `setImageDrawable` 一句硬切，所以这一档是**产品取向**：`A11NotifSpec.ART_FADE_MS = 320`，
与卡片背景已有的 `TransitionDrawable.startTransition(320)` 同值，让底色与封面一起淡。
`applyArtSwap(prev, next)`：手上还有旧图 → `TransitionDrawable` 交叉淡；空窗后第一张 → alpha 0→1 淡入。
`prev` 用我们自己留的 `lastAppliedArt`，**不是** `artView.getDrawable()`——那可能已经是上一轮的
TransitionDrawable，套下去无边际。见证 `a11 art fade mode=cross|in ms=320`。
编译不过才发现这个 android.jar 没有 `TransitionDrawable.startFade(int)`，改用文件里已验证过的
`setCrossFadeEnabled(true) + startTransition(ms)`。

### 3. 我把断言写严了一次，A11 原文替我纠偏（重要，别再犯）

给两枚时间加"读回色对底色 ≥4.5:1"后，真机在 `bg=fff84000`（饱和橙）上报红：白字加满 alpha 也只有
**3.67:1**。去读 A11 原文 `ensureColors`（`A11-Notification-MediaStyle.java:2473-2515`）：

- 没有 app 显式前景色时（我们的情况就是这样），A11 **只抬 alpha**，二分到 255 就停，
  **不翻转色族** —— 它自己在这类底色上就是 3.67:1；
- 只有 `hasForegroundColor()` 那一支才会用 `findContrastColor / findContrastColorAgainstDark` 换色。

所以是实现忠实、断言过严。修法不是放宽成"不判"，而是换成**与 A11 同式的独立重算**：验证器里实现
`_composite()`（A11 那套整数 alpha 合成）+ `_alpha_to_meet()`（在 [当前alpha,255] 上二分 15 次、
判定用严格大于、取 high），要求 `primary/secondary` **正好等于**这个结果值。于是
"alpha 加到一半"会被抓（阈值判断抓不到，而 A11 也不会那样），而"加满仍不够 4.5"算出来就是 255，
不会被误判成回归。

**教训：复刻 OEM 行为时，断言要写成"重算它那条公式的结果"，不要写成"我认为该达到的标准"** ——
后者会在 A11 本来就不达标的地方报红，逼你去"修"一个不是 bug 的东西。

### 4. 验收（build `1a2753b0a523`，装机哈希一致）

- 真机日志 `--media-only`：**160 条通过 / 0 失败**，含 `fade: mode=cross/in ms=320` 与
  `native: reshown→rehidden vis=8 size=96x96`。
- `work/verify_os5_mirror.py --selftest`：**78 条反例全咬**，新增三条分别对应
  "换两张封面却没淡入淡出"、"过渡时长=0（硬切）"、"守卫写回后仍可见"。
- `work/a11_notif_selftest.py`：82 项比对 / **18 个负对照**（`ART_FADE_MS` 已入常数表并带反例）。
- `work/offline-all.sh` 全绿；`work/a11-card-gate.sh … os3` 全绿；无致命异常、pid 全程不变。




## 2026-11-04（傍晚）切歌不稳定这一轮：三次假设被量测逐一证伪

用户反馈两条：① 封面还是生硬切换；② 切歌期间**别的通知**的小图标也闪一下，整体不稳。

### 1. 生硬的根因是我们自己把淡入重启了两次

真机时序（同一次切歌，间隔 24ms）：

```
a11 art relief hash=a391b994 color=ffffffff  +  a11 art fade mode=cross ms=320
a11 palette state=expanded bg=ff103808 …
a11 art relief hash=a391b994 color=ff103808  +  a11 art fade mode=cross ms=320
```

`watchArtArrival()` / `onMetadataChanged()` 里先 `syncArt()` 再 `refreshColors()` ⇒
浮雕按**旧底色**画一张并起 320ms 淡入，紧接着取色落地又起一次 —— 第一次淡入只跑了 ~7% 就被替换，
旧封面因此是"突然消失"。修法：两处都改成**先取色后画封面**；并且"同一张图只是换底色重染"
不再起过渡（`a11 art fade mode=retint`，无 `ms=`）。现在一次切歌恰好一条 `fade mode=cross`。

### 2. 小图标闪现：逐帧量测，不是靠猜

1Hz 的 witness 什么都看不到（这是关键教训：**量测精度不够时，"没发现异常"不等于"没异常"**）。
于是加了调试键 `betteroriginos_trace_flicker`：在 `NotificationStackScrollLayout.dispatchDraw` 之后
做逐帧只读快照，每行 census 出**所有** `CachingIconView` 实例的 `visibility:alpha:scale:hasDrawable:drawableAlpha`，
只在状态相对上一帧变化时打一条，预算 240 帧。

先被证伪的三条猜测：

| 假设 | 量测 | 结论 |
| --- | --- | --- |
| 有实例露着但我们没登记（`light.native stray`） | 0 条 | 否 |
| 我们的代码在切歌时改了别的行的图标 | `icon removed/fade/restyled` 全 0 条 | 否 |
| 整列表在抖（行高/位置跳） | 每行 11 次采样 Δy=0 Δh=0 | 1Hz 看不到，不足以定论 |

逐帧 census 给出的真相：一行里其实有**三颗** `CachingIconView`（收起/展开根 + 隐藏位），
被画那颗在 f=54 是 `0:1.00`、f=66 被我们按成 `4:0.00`、**f=67 又被抬回 `0:1.00`**；
而我们自己的放开路径 `light.native RELEASE` **一条都没跑过** ⇒ vivo 每次重排把
视图 alpha/可见性/scale 一起写回来，我们按任何单一视图属性都是逐帧对写。

三次尝试：
1. `INVISIBLE` —— 一帧内被抬回，仍然闪；
2. `scaleX/Y=0` —— census 显示 `s1.00` 回来，同样守不住；
3. **摘掉 drawable** —— 闪现没了，但门禁当场红：`L3 标题左偏移 60px`。
   那颗图标是 `wrap_content`，没有 drawable 就 measure 成 0，header 的列数学跟着塌。
   （这就是"改状态前先问谁把它读回去"的又一例：这次读回去的是**布局**。）

最终做法：**压 drawable 自己的 alpha**（`d.mutate().setAlpha(0)`，放开时回 255），
尺寸、布局、可见性一概不动；vivo 只写视图属性、不写 drawable alpha ⇒ 没有争抢对象。
守卫里每次顺带再压一次，覆盖"图标加载器换了新 drawable"那一帧。
复测 census：被接管的两行全程 `da0`（视图被抬回可见也画不出东西），
而媒体行与组摘要保持 `da255` 未被触碰（`L7 组摘要保留原生小图标` 仍绿）。

### 3. 顺带修掉的两处判据/harness 空洞

- `L3/L4` 没有排除媒体行：a11 卡片那一行的文字列按 A11 模板走 20dp 内缩，被当成"标题没右移"判红过。
  现在按 dump 指纹（行内有 `SeekBar`）跳过，并加 fixture `GOOD_XML_MEDIA` + 自检用例；
  反证过：把这道关门改成 `if False` 后，恰好这一条转红 —— 关门不是摆设。
- `os3-evidence-groupcycle.sh` 两轮取到空帧：① 设置页占焦点（`postgroup/post` 不像 `postall` 会
  `moveTaskToBack`）⇒ HOME 不奏效时再用 BACK 兜一次并复核 `mCurrentFocus`；
  ② 上一轮遗留的 `.cycle-tap.txt` 坐标被拿来乱点 ⇒ 投帧前清掉。
  本轮该脚本仍因设备息屏没跑绿，**记为欠跑**，不用它给结论。

### 4. 本轮验收（装机 `557929769575`，设备哈希一致）

- 三态行排版取证：**11 通过 / 0 失败** ×3（图标、列、圆角、摘要策略都在）。
- 媒体卡真机日志 `--media-only`：**203 条通过 / 0 失败**，含 `fade: mode=cross/retint` 与
  `native: reshown→rehidden`。
- `verify_os5_mirror.py` 反例 **78 条全咬**（新增覆盖 `retint` 不该带 `ms=`）；
  `verify_row_layout.py` 自检 **30 组**；`offline-all` 全绿。
- 逐帧 census 现在是可复用的定位手段：`settings put global betteroriginos_trace_flicker 1`
  （用完记得置 0；它只在 `notify_verify=1` 时打日志）。

### 5. 还开着的

- 切歌后约 0.9s 内仍是"新文字 + 旧封面/旧底色"（vivo 先推标题、后推 largeIcon 的顺序决定）；
  新封面一到就一次 320ms 交叉淡。要把这段也压掉，只能在建卡时直接取 `sbn` 的 largeIcon
  （需把 `sbn` 传进 `MediaCard.build(...)`，见 LOG-1103 §13-1）。
- 组开合五帧门禁本轮欠跑（设备息屏/焦点），下轮补。

## 2026-11-04（夜）进度条 A 方案：a11 走 A11 几何，滑块真的可见

用户问"进度条能不能自定义样式"。事实是：**不能单独选**——设置里只有整卡
`style=os5|material|a11` 和 `qs_seek`（控制中心磁贴那条），进度条几何是共用的，
而共用的那套是 iOS 形状：进度部分固定 **10dp 粗胶囊**（`fillH = density*10f`），
滑块那颗 drawable 颜色写成 `0x00000000` ⇒ **根本画不出来**。
A11 两个媒体模板都只写 `android:maxHeight=3dp`（形状由 progress drawable 自己出）
⇒ 已读部分与轨道同厚的圆头细线 + 一颗可见实心圆滑块。

A 方案（用户选"先 A 试试"）：

- `SeekDrawable.uniformFill`：true 时 `fillH == trackH`（3dp），false 保持 iOS 的 10dp；
  只在 a11 分支打开，os5/material 一律不动。
- `thumb(ctx, accent, visible)`：a11 用可见实心圆（带 accent 的 alpha），iOS 那款继续用透明占位。
- 见证补强：`a11 seek` 行增加 `thumbColor=`（读 `GradientDrawable.getColor()` 这个
  `ColorStateList` 的 defaultColor）、`thumbPx=`（内在尺寸）、`uniformFill=`。
  原因很直接：旧断言只看 `thumbAlpha=255`，**透明色的占位 alpha 也是 255**，
  所以"滑块看不见"这件事它是瞎的 —— 断言要能红才有意义。
  真机读数：`thumbColor=ff000000 thumbPx=30(=10dp) uniformFill=1 thickness=3.0`。

编译踩坑一处（记下来别再撞）：这个内部 `android.jar` 里 `GradientDrawable.getColor()`
的返回类型是 **`ColorStateList`**，写 `? getColor() : 0` / `Integer x = getColor()` 都是
"三元式类型不兼容"，取默认色要 `csl.getDefaultColor()` 且先判空。

验收：装机 `afab6b808a3b`（哈希一致）；`a11-card-gate … os3` 全绿；
`verify_os5_mirror.py --media-only` 真机日志全绿；反例 **81 条全咬**（新增三条：
滑块透明占位、滑块无尺寸、进度退回 iOS 粗胶囊）；`offline-all` 全绿。
待定：滑块直径 10dp 是我按现值定的（A11 那两个模板没写 `android:thumb`，取自样式），
等用户上眼后按观感调；B 方案（`seek=ios|a11` 做成用户可选）暂缓。

## 2026-11-04（下午）P0-1：从"堆门禁"回到逆向，找到每帧位移的写入者

### 0. 方法上的更正（用户提的，已写进 skill）

这一轮我先花了两轮去修**自己的判据**（选材判据、媒体行排除判据），而真正决定修法的是"谁在按帧写位置"。
定死的规矩：**同一轮里判据修到第二次就停手**，回到"哪个属性被谁按帧写成什么"去读代码；
观感问题一律"逆向定因 → 改写入点 → 用户上眼"，门禁只留能一眼分红绿的极小集合。

### 1. 症状被重新描述，方向才 turn 过来

用户这句话是关键："点展开时，时间从右边往左边**跑**，到左边后立马闪现回右边"。
看得见**移动过程** = 有位移动画按帧写位置，不是一次性摆错 —— 于是"补推时机/换延时"那一类方案全部作废。

### 2. 逆向结论（两份子代理报告，framework 布局数学我自己复读了一遍）

- framework `android.view.NotificationTopLineView`（A13，`work/tmp-re-os5toos3/A13-NotificationTopLineView.java:120-198`）：
  `onMeasure` 用 OverflowAdjuster 算 `mViewsToDisappear`，`onLayout` **流式从左往右排、GONE 跳过、`start` 累加**，
  被判消失的子节点走 `child.layout(start, top, start, top+h)`（零宽）。
- 组子行的应用名是 **AOSP 原生**收掉的，不是 vivo：`NotificationGroupingUtil.updateChildrenAppearance`
  → `AppNameApplicator`/`VisibilityApplicator` 的 `setVisibility(8)`（GONE），**发生在绑定期**、不在展开动画路径上；
  A13/A15 规则一致（OS5 的 `sanitizeTopLine` 只多一句 `time.setAlpha(1f)`）。
  这台机上没有 `show_timestamp_in_group_child` 这类布尔开关（framework-res 的 bool 里查不到）。
- 每帧 `translationX` 的写入者是 `TransformState#transformViewTo/#transformViewFrom/#resetTransformedView`
  （`work/tmp-re-os5toos3/os3-code-classes2.txt:532887/533356/533793`），调用链：
  `ExpandableNotificationRow 展开回调 → NotificationChildrenContainer.setActualHeight（每帧，由 ViewState/PropertyAnimator 驱动）
  → updateHeaderTransformation → NotificationViewWrapper.transform{To,From}
  → ViewTransformationHelper（ValueAnimator）→ TransformState → View.setTranslationX`。
  注册进 TransformState 的只有 `mIcon`(ALPHA)、`mExpandButton`、低优先级时的 `mHeaderText`(TRANSLATE)——
  **OS3 那份 header wrapper 里连 `mTime` 字段都没有** ⇒ `time` 自己的 `translationX` 是无人争抢的通道。
- 已排除（都是验到具体位置的排除，不是"我觉得"）：三个 APK 的全部 `res/layout/*.xml` 字符串池扫
  `animateLayoutChanges` **0 命中**，LayoutTransition 在 SystemUI 只出现在 `BatteryMeterView`/`NavigationBarView`，
  `TransitionManager.beginDelayedTransition` 唯一调用点是 `ChannelEditorListView`；
  OS3 没有 `NotificationHeaderAnimationHelper`/`RowTranslationsManager`/`InterpolatedChildVisibility`。

### 3. 改法（按上面的通道选择）

`alignLineIntoColumn` 里新增每帧 `clampTimeVisualLeft(row, line, want)`：

- 判定用**视觉左位** `rowVisualLeft`（逐级累加 `getLeft() + translationX`），不再用只含布局坐标的 `leftInRow`
  —— 这正是旧版"过时一格"的原因：祖先每帧带位移，而我们按布局框算、又写回布局框。
- 只有真要落进图标列（`vis < want`）才动手，动作是补 `time` 自己的 `translationX`；
  已经在列右边就一个字不改 ⇒ 稳态观感完全不变。返回 `0 不需要 / 1 钳住 / 2 钳了还没到位`。
- `col measure` 的 `time=` 改成报**视觉落点**，并新增 `clamp=`；验证器加 **L12**（`clamp=2` 判红）。
- 接管方式**不再写死**：按 `sColumnHook` 实际自报 `header-time=column-hook|push-only`
  （原来写死 push-only，等于门禁被自己的字符串骗了一整轮）。
- col 钩子加入口心跳 `notify_row.col calls=N`，随补扫每 5s 重发：
  从此能把"钩子没跑"与"跑了但一次性见证被 logd 每进程 250/s 整批冲掉"分开
  —— 上一轮 L10 报"一条 measure 都没有"就是后者，差点把在跑的钩子当成没装上。

### 4. 验收（装机 `4db73ebb79c5`，哈希一致）

- `work/os3-evidence-rowlayout.sh` 三态 **14 通过 / 0 失败** ×3：`calls=269/290`，实测视觉落点全部 `+198`（66dp 列），
  圆角 R1~R5、42dp 大图标、L7 组摘要保留原生小图标都在（11-03 那个"大图标消失"回归没有复发）。
- 新的 `work/os3-evidence-timeframe.sh`（逐帧，调试键 `betteroriginos_trace_flicker`）：5 通过 / 0 失败，
  12 个帧×行组合没有时间压进图标区间。
- `work/offline-all.sh` 全绿；行排版验证器自检 **35 组**（新增 L12 与暂停态媒体行两组反例）；
  逐帧验证器 `work/verify_time_frame.py --selftest` 8 组全咬。

### 5. 还没证到的部分（别当成已收口）

本仓库的调试样本里**没有 vivo 那种"应用名被收掉、时间成第一个可见子节点"的组子行**
（同一 App 多条通知会被自动归组，而 AOSP 组子行的 header time 宽度是 0），
所以 `clamp` 这条路径在自动取证里**从未被真正触发**（日志老实报"这一屏没有需要钳的帧"）。
P0-1 的收口只能等用户在 vivo服务 组上眼：还滑就看那一帧是 `clamp=1`（钳了还动 ⇒ 有写入者在我们之后写）
还是 `clamp=0`（这一行压根没进钩子 ⇒ 它的 top_line 走的是另一条路径），两种修法不同。

### 6. 顺手挖到的判据坑（都是"改了判据却没改材料"那一类）

- L3/L4 排除媒体行原来靠"行内有 SeekBar"，而取证前为了拿到 idle dump 会先 `dispatch pause`
  ⇒ 暂停态的媒体行里根本没有 SeekBar ⇒ 被当独立行判红。也不能换成
  `status_bar_latest_event_content`：真机 dump 里**每行都有**它，拿它当指纹会把所有行排除掉，
  L3 退化成"没有可测行"的空转。现在用 transport 按钮 `action0..4`（≥3 枚）作指纹，
  并配"把按钮 id 改名就该红"的反例。
- 逐帧日志的续行不带 tag 前缀：`logcat | grep notify_row.trace` 只会抓到多行消息的第一行，
  要按 `BetterOriginOS` 整进程过滤再拆条目。

## 2026-11-04（深夜）P0-1 第二轮：我连着错了两次，最后靠读数纠回来

用户上眼："还在动"，并且把症状说得更准了：**时间从右边往左边跑，到左边后立马闪现回右边**。
这一轮改了三次方向，前两次都错，错因都值得记。

### 判错一：「展开动画期间 `onLayout` 不重跑」

依据是 `notify_row.col calls` 在连点期间 140→141 后冻住。**自己复测被推翻**：同一台机、同样连点，
`calls` 从 23 跳到 99。错因不是读数假，是**读数取自已经坏掉的环境**——我按旧 dump 的坐标盲 tap 时
焦点其实在 LSPosed 管理器，那一下把模块作用域里的 `com.android.systemui` 关掉了；之后新进程压根
没被注入，`calls` 当然不动。（`lspctl scope list` 查到缺失 → `scope add` 加回 → 重启后模块日志
114 条才算确认注入回来。）
**教训：任何"每帧入口没跑"的结论，先证明注入活着、钩子装上了**（看 `header-time=` 自报与
`top-line column hook hits=`），否则测的是空气。

### 判错二：「钳位已经作用到屏幕上那枚时间」

`col measure` 里 `line=+126 time=+198 moved=1` 看着像我们在守，但同批还有
`line=+410 / +565 / +568 time=none`——`time=none` 说明**我压根没找到那枚时间**：
`findTimeChild` 只扫 `top_line` 的**直接子节点**，而 vivo 行里的时间不是直接子节点
⇒ 前两版钳位对 vivo 行完全空转（`col predraw wrote=0` 同源）。改成整棵子树找才第一次碰到屏幕上那枚。

### 判错三（口径错）：把"别动"实现成了"别压图标"

用户要的是**别来回跑**，我的钳位只设了下界（视觉左位不早于 66dp 列）。实测 `top_line` 自己在
126/200/410/565/568 之间移动——线是流式排子节点的容器，兄弟节点可见性/宽度一翻（AOSP
`NotificationGroupingUtil` 对 `app_name_text`、`sanitizeTopLine` 对 `time` 写 GONE/VISIBLE；
`ActivatableNotificationView.makeActive/makeInactive` 还会换 time 的**文本**从而改宽度），
线的左端就跟着换，时间被整条线拖着走。这种滑动**从没跨过 198 那条下界**，于是钳位认为"不需要动"
——典型的"我修的是我以为的病"。

### 现在的实现：钉在稳态，而不是钉在常量

`freezeTimeLeft(row, time, want)`（布局期与 pre-draw 两条路共用同一份逻辑）：

- 学会这枚时间**静止时**的视觉左位（含各级祖先 `getLeft + translationX` 累加）；
- 过渡帧偏离超过 2px ⇒ 用 `time` 自己的 `translationX` 写回稳态值（OS3 header wrapper 没有 `mTime`、
  TransformState 不注册它 ⇒ 这条通道没人争抢，逆向已证）；
- 偏离持续 ≥400ms 才认作真变化（换曲目、文本长度变了）并跟进，避免永远钉死在旧位置；
- 护栏：低于 66dp 列的落点**绝不认作稳态**，直接顶回列上（"压图标"这个更严重的形态仍被挡住）。

诊断行留在码里（各有硬上限与节流，不会刷屏），供下次读数：`col reg`（登记了谁、找到几枚时间）、
`col predraw`（每帧到底写没写）、`col snap`（线里每个子节点的 `可见性:宽度`，用来点名翻动者）。

装机 `d769366feb0c`（两端哈希一致），SystemUI 重启并确认注入（`header-time=column-hook`），
`work/offline-all.sh` 全绿。设备收尾：调试样本清零、`betteroriginos_trace_flicker=0`、息屏。

### 今晚没能就地确认的两件事（明早先解决这两个，别继续猜）

1. **vivo服务 的子行已从列表消失**（只剩摘要，摘要行里没有 `time` 节点）⇒ 用户描述的形狀当前在屏上
   不存在，无法就地复现。
2. **我们 App 的通知被系统压住**：`dumpsys notification` 里
   `AppSettings: com.betteroriginos importance=DEFAULT moreNotificationsEnabled=false`，
   投 `post`/`postlong` 后只有 `postlong` 进了 NMS 且列表不显示 ⇒ 自造样本这条路今晚不通。

两条都是**观察条件**问题，不是产品结论。恢复观察条件后先看 `col snap` 点名到底是谁在翻，
再判"钉稳态"够不够：如果翻的是线本身且稳态值不可信，就得改去治线的摆放（而不是继续钉时间）。

## 2026-11-04（深夜·定案）滑动是我们自己算出来的：一个属性三个主人 + 一处重复计算的左偏移

用户："还是会往左移动，你还是没逆到关键点上，上子 agent 完整逆出逻辑，别自己猜了。"
于是派了四位做穷尽逆向/自审（完整帧序、vivo 侧 header 与 time 写入者、OS5 同场景对照、
以及**只审我自己代码**的失效路径）。自审直接给出定案。

### 真因（两条，都在我们自己代码里）

1. **`getRight()` 已经含 `getLeft()`，被算了两遍。**
   `pushTimeRight` 里 `d = target - (offX + time.getRight())`，而 `offX` 已经是逐级累加的
   `getLeft()` ⇒ 目标里混进了 `time.getLeft()` 这个**每帧都在变**的量
   （`NotificationTopLineView.onLayout` 是流式 `start` 累加，兄弟可见性一翻它就变）。
   **目标自己抖 ⇒ 我们每帧把时间推向一个新抖的目标**，看起来完全像"系统在抢写"，其实是自造抖动。
2. **同一枚 `time` 有三个主人。** `c.layout()`（`HEADER_TEXT_IDS` 里含 `"time"`）、
   `pushTimeRight`（绝对 translationX，目标=行右缘/箭头左）、`freezeTimeLeft`
   （增量 translationX，目标=它自己学的稳态）——口径与目标都不同，逐帧互相覆盖。

顺带纠正我自己两个错判：
- **"pre-draw 慢一帧"不成立**：`performTraversals` 是 measure → layout → dispatchOnPreDraw → draw，
  pre-draw 是本帧最后写者。"钉不住"只能解释为"这一帧没作用到那枚视图"。
- **"登记后失联重取"是我注释里承诺、代码里没写**：`registerColClamp` 的 `containsKey(row)` 直接 return，
  5 个名额被隐藏副本占满 ⇒ 屏幕上那枚从未被钳（`pre-draw wrote=0` 就是这么来的，
  而这条日志只在 `wrote>0` 才打，于是"没发生"与"没观察到"在日志里长得一样）。

### 已改（装机 `83a89277c339`）

- `d = target - (offX + time.getWidth())`（去掉重复的左偏移），并在写入后**回读**校验，
  还差 >3px 就报结果码 2。
- `HEADER_TEXT_IDS` **去掉 `time` 与 `time_divider`**：搬列只搬左列文本，不再碰时间。
- 删掉 `freezeTimeLeft` / `TIME_FREEZE` / pre-draw 那套（`COL_CLAMP_*`、`registerColClamp`、
  `clampRegisteredRows`、`installColPreDrawClamp`）与 `clampTimeVisualLeft`；
  收敛成**唯一入口** `positionTime(row, line)` → `pushTimeRight(time, "layout")`，
  由布局钩子**每趟调用**（真机已证明展开期间 `onLayout` 每帧重跑：连点时 `calls` 23→99）。
  `pushTimeRight` 写绝对位移 ⇒ 天然幂等，重复调用只会收敛到同一个目标。
- `pushTimeRight` 返回结果码（-1 无时间 / 0 已在位 / 1 推到位 / 2 推了没到位），
  继续用 `col measure … clamp=` 报出去（L12 判据不变）。
- 离线：`work/offline-all.sh` 全绿（行排版验证器 35 组、逐帧验证器 8 组）。

### 仍未就地确认（观察条件，不是代码问题）

屏上当前只有 2 行且**都没有可见的 `time` 节点**：vivo服务 的子行已不在列表里，
而我们 App 的通知被系统整应用关闭（`dumpsys notification`：`AppSettings: com.betteroriginos …
moreNotificationsEnabled=false`，怀疑是反复投调试通知触发 vivo 的频繁通知抑制）。
⇒ "target 不再抖"这一条要等观察条件恢复再读 `notify_row.time push d=… target=…`。

### 这轮另外两条已入库的逆向事实（写进 skill §4.6）

- A13 的 `time` 是 `NotificationDateTimeView`，三处写入点全是 `setTime(J)`，
  **没有任何 `setText`/`LayoutParams` 写它** ⇒ 相对时间文本自刷新会改宽度、进而改 `start` 累加。
- `recreateNotificationHeader`（`makeNotificationGroupHeader` → `RemoteViews.apply`）会在动画后
  **重灌 header 子树**，我们写过的摆位全部作废；且 header 有两个实例
  （`mNotificationHeader` / `mNotificationHeaderLowPriority`），修正要两份都覆盖。
- vivo **没有**替换 OS3 的 header 类（我们钩的就是被画的那个）；OS5 换的是 time 控件类
  （`com.android.internal.widget.NotificationDateTimeView`）。
- A15 观感不滑的机制差异：A15 有 vivo 的 `NotificationHeaderAnimationHelper` ——
  动画里 `time` 全程 VISIBLE、只动 **alpha**（150ms），GONE 推迟到 `withEndAction`；
  而 `top_line` 的 measure/layout **只读可见性与宽度、0 处读 alpha** ⇒ 显隐不该在动画中翻。
  A13 侧对应落点是 `sanitizeTopLine`（classes2.dex @`37e48a`，且 A13 那处没有 A15 的 `setAlpha(1f)`）。

### 第四位回来后补的最后一刀（装机 `568bd83d18f8`）

它确认了两件我们此前假设错的事：
- `ViewRootImpl.performTraversals` 的字节码次序是 `performMeasure`→`performLayout`→
  `dispatchOnGlobalLayout`→**`dispatchOnPreDraw`**→`performDraw` ⇒ pre-draw 在本帧布局之后，
  **时序不是"钉不住"的原因**。
- 线自己为什么会动：`notification_top_line` 的布局规则是
  `layout_width=-2`(wrap) + `alignParentStart` + **`toStartOf=expand_button`**，而
  `NotificationTopLineView.onMeasure` 结尾 `setMeasuredDimension(givenWidth, …)`
  ⇒ 线的宽度跟着 `expand_button.left` 走，`time.left = 线的左 + paddingStart + Σ 前面兄弟宽度`。
  **读数里 `+126 / +132 / +565` 的差 = 不同 header 实例 + 我们自己往 `top_line.leftMargin` 做 `+=`
  （`shiftHeaderText`）造成的别名落点** —— 那正是本文件明令禁止的"累加 + 屏幕坐标"写法。

于是按"一个属性一个主人"再收一处：`sColumnHook` 为真时，legacy 的
`convergeHeaderColumn`/`shiftHeaderText` 整条路径直接跳过（钩子没装上才退回）。

三态取证复跑（同一批证据，逐态）：**14 通过 / 0 失败 ×3**，其中
`L3 标题列=66dp`、`L5 应用名与标题同列` 仍然绿 ⇒ **撤掉 legacy 搬路没有把列对齐弄坏**（列由钩子独立持有）；
`L10` 实测视觉落点全部在列右（+780..+828）、`L12 clamp=1`（时间在由唯一主人摆位且回读通过）。
唯一红是 harness 自己的观察侧断言"展开没发生（子行 0→0，最高行 642→642）"——
它的 tap 没触发展开，属于取证脚本问题，不是产品断言。

**还欠用户上眼那一次**：这版把"三个主人 + 抖动目标"都拆掉了，理论上不该再有滑；
但真机上需要一条**带可见 time 的 vivo 组子行**来确认（今晚屏上没有，且我们 App 的通知被系统整应用关闭）。
读数入口已经留着：`notify_row.time push d=… target=… why=…` 与 `col measure … time=+… clamp=…`。

## 2026-11-05（凌晨）第五版：撤掉我改坏的一刀，并按帧序"加入同一帧"

装机 `50295c5e49a9`。三件事：

1. **撤销"布局钩子装着就跳过 legacy 搬路"**。那是我的第二个误伤：撤掉之后真机 dump 里
   LSPosed 组子行的应用名从 `+198` 掉到 **`+126`**（图标列）——两个写者其实在互相补位，
   我按"一个属性一个主人"的原则去掉了其中一个，却没补上它的职责。已恢复原路径。
2. **legacy 的测量口径改为与本文件其它摆位同源**：`getLocationOnScreen` 相减 →
   `rowVisualLeft`（逐级累加，过渡帧不吃祖先位移）。这才是 `+126/+132/+565` 那种别名读数的根。
3. **按 RE 的帧序补"同一帧"**：`NotificationContentView.onLayout` =
   FrameLayout 排完所有子 → `updateClipping` → **`selectLayout` 翻可见性**，
   即翻转发生在我们的摆位**之后**；刚被翻出来的那枚沿用它当隐藏副本时的旧位置，
   下一帧才被摆回 ⇒ 用户看到的"滑到左边、闪回右边"。因此加
   `installSelectLayoutTimeHook`：钩 `NotificationContentView.selectLayout` 的 after，
   只对被接管的行（有 42dp 图标），把该行里**所有在屏上的** time 交给同一个主人
   `pushTimeRight`（绝对位移 ⇒ 幂等）。

**未闭环（诚实记）**：装完后屏上只剩 vivo服务 的摘要一条、**子行不存在**，也没有
"带可见时间的被接管行" ⇒ 这一版同样只能确认"列没被改坏"（vivo 摘要行应用名 `+198` 正常），
**不能确认滑动已消失**。观察条件恢复（vivo 组带子行 + 我们 App 的"允许通知"打开）后，
读 `notify_row.time push … why=selayout` 是否每帧都在、以及逐帧条目里被画那枚的 `L` 是否始终 ≥198。

## 2026-11-05（凌晨·真正的靶子）滑的是**组摘要行**，而我们五版都在改别的行

用现成的逐帧读数（调试键 `betteroriginos_trace_flicker`）抓到了决定性一条：

```
f=139 id=6abb4b2 h=1023 big=-  在屏时间 L=674
f=142 id=6abb4b2 h=1023 big=-  在屏时间 L=578   ← 左移 96px
f=144 id=6abb4b2 h=1023 big=-  在屏时间 L=578
f=149 id=6abb4b2 h=1023 big=-  在屏时间 L=674   ← 闪回
```

`big=-` 是钥匙：**这行没有 42dp 大图标 ⇒ 它是组摘要行，而我们的代码一直把它当
"不归我们接管"显式跳过**（`why=summary-header` / `not-taken`）。
所以前面五版（布局期搬列、translationX 钳位、pre-draw、单一主人、撤 legacy）
**没有一版作用在用户看到的那一行上** —— 这就是"屡提屡没改好"的真实原因，不是修得不够狠，是修错了对象。

### 成因与锚的选择

摘举行静止时时间在最右（L=674、宽 178）；展开动画里 `notification_top_line` 的宽度随内容变化，
而时间是流式排的最后一个子节点 ⇒ 它的自然位置被临时推到 578 再回来。
- 第一版锚 = `header_button_container` 左缘：**跨度归零**（674/578 抖动没了），
  但稳态被钉成 578 ⇒ **用"位置偏左"换了"不滑"**，属于观感回归，当场否掉。
- 现在的锚 = 该时间视图自己**最近 1500ms 内出现过的最右自然位置**（不含我们写的位移）：
  静止时自然右缘==最大值 ⇒ 位移 0，**不改原生外观**；动画中自然位置左移 ⇒ 补正位移顶回去；
  文本真变短时右缘保持、左缘右移（= 右对齐时间的原生行为）。只写**绝对** `translationX` ⇒ 幂等；
  补位 >300px 直接放弃。入口在 `NotificationContentView.selectLayout` 之后
  （帧序结论：翻转可见性发生在我们的摆位之后 ⇒ 必须紧跟它），对被接管的行仍走 `pushTimeRight`，
  摘举行走 `anchorTimeToMaxRight`，媒体行排除。

装机 `8ca2994bbc9e`。**这一版仍没做就地复测**：vivo服务 又收回成单条 255px 行且不带时间，
屏上没有"带时间的摘要行"了 ⇒ 读数取不到。要闭环只欠一个条件：屏上出现一个
**带时间戳的组摘要行**（vivo服务 攒够 2 条以上，或任意 App 的分组通知），
我再跑同一条普查，看那枚 `L` 的跨度是否为 0、且稳态值 == 原生 674 那一档。

## 2026-11-05（上午）P0-1 真正的定案：那个动作是我们自己造的，方向从头就反了

用户一句"原生通知是在左上角一直不动的，展开也没变化"结束了持续十版的循环。

### 对照实验（早该做的那一个）

把模块**整个禁用**（`lspctl module disable --user=0 com.betteroriginos` + 重启 SystemUI +
按 pid 确认该进程零模块日志），同一处再看一次：**A13 原生把 header 时间放在左上槽，
展开全程不动**。

⇒ "从右滑到左、到左又闪回右"不是 vivo 的行为，是我们的：原生把时间摆在左槽，而我们的
42dp 大图标占了同一段，于是 `pushTimeRight` 每帧把时间往右搬、vivo 每帧按原生摆回去 ——
**同一个属性上逐帧对拉**。十版全在"怎么把时间钉得更牢"，所以十版都没变化。

### 一个必须记进纪律的错误：假对照

`betteroriginos_feature_notify_row=0` **不会关掉时间相关的钩子**：关掉之后 `restyled light`=0
（行样式确实回原生），但日志里仍有 `header-time=column-hook`、`col calls=57`
⇒ 布局钩子 / selectLayout 钩子 / margin 钉位 / 动画闸全在跑。
所以"我关掉功能再看看"这种对照，除非**整个禁用模块并按 pid 验零日志**，否则测的还是被改过的状态。
前几轮的若干判断正建立在这种假对照上。

### 决定（用户认可"先这样"）

`RELOCATE_HEADER_TIME = false`：A13 侧**不再搬时间**，让它留在原生左上槽、彻底不动。
装机 `c374c9ce72cf`，模块已重新启用、注入确认（84 条日志）、`work/offline-all.sh` 全绿。
文本列（app_name/title → 66dp）、圆角、图标等其它接管不变；`sanitizeTopLine` 动画闸与
`mHeaderTextMarginEnd` 钉位保留（只增加稳定，且不再与原生对拉）。

**留下的取舍（下次继续，不是 bug）**：左上槽的时间会与我们的大图标挤。两条路都只动我们自己
拥有的视图 ⇒ 不会再有逐帧对拉：① 这类行不放大图标；② 把大图标挪到时间右边。

### 方法论

**"改了十遍没变化"这个信号本身就是答案**：说明我们改的对象不受我们支配。
时间是原生每帧重排的**下游**，钉不住；要动就动我们拥有的视图（图标/容器是我们 addView 的）。
任何观感问题，第一步做"整个关掉"的原生对照，并按 pid 验证关掉是真的关掉了。

## 2026-11-05（上午·连带 bug）组摘要行的小图标变成空白边框

用户报：`Android 系统`、`vivo服务` 等通知左上角小图标变成空白边框。

根因（一条纪律没守住）：防"小图标闪一下"的手法是**压 drawable alpha**（摘 drawable 会让
wrap_content 图标 measure 成 0、把 header 列数学搞塌；压视图 alpha/可见性/scale 又守不住，
vivo 每帧写回）。但 `guardLightNativeIcon` 注册的是**每帧 pre-draw** 兜底，而它的入口之一
（"stray 兜底"）只要求"这行里有 `CachingIconView`"就注册并开始压 ⇒
**组摘要行按 L7 策略保留原生小图标、不放大图标**，于是被压成 alpha 0 却没有顶替物，
而且每帧被重新压回去、永不恢复 ⇒ 空白边框。真机 `light.native RELEASE` 0 条也印证
"我们的放开路径从不跑"，所以任何压了没顶替的图标都会永久消失。

修法：`suppressNativeIcon(...,true)` 的两个入口都先查 `findTag(root) != null`
（这一行是否真有我们的 42dp 大图标顶替）；没有顶替就反过来 `suppressNativeIcon(ni,false)`
把 alpha 恢复 —— 已经压坏的行因此自愈。装机 `bf54bc70f763`，`work/offline-all.sh` 全绿。

纪律（写进 skill §4.1）：**压掉一个原生视图的内容，必须同时确认有顶替物；
"每帧兜底"型的抑制一旦注册，忘记顶替就是永久消失，而不是闪一下。**

## 2026-11-05（上午续）组摘要行图标空白：判据必须与"收法"一致

用户报部分应用（Android 系统、vivo服务）左上角图标变成**空白圆角矩形**。真对照（整个禁用模块、
按 pid 确认零模块日志）证明**原生有图标** ⇒ 是我们压掉的。三轮才收干净，每轮都错在"过滤判据
比收集判据窄"：

1. 第一版只挡 `suppressNativeIcon` 的两个入口 ⇒ 没用，因为隐藏是另一条 blanket 干的：
   `collectHideTargets` + `GUARD_TARGETS` 每帧重压。
2. 第二版在隐藏名单入表处过滤，但只认类名 `CachingIconView` ⇒ 漏；因为 `collectHideTargets`
   是**两条判据**：类名 `CachingIconView` **或** id `:id/icon` / `:id/left_icon`
   （摘要行那颗其实是普通 `ImageView`+`android:id/icon`）。抽出 `isIconView()` 让两边共用后，
   `vivo服务` 恢复。
3. `Android 系统` 仍空 ⇒ 顶替物本身是空的：我们放了 42dp ImageView 且它在显示，但
   `getDrawable()==null`（`android` 包取不到应用图标），于是"有顶替"成立、原生被压，
   露出我们那个没内容的圆角壳。判据补一条：**顶替物必须真的有图**。

纪律：`"不许压掉原生内容"的判据必须与"要压哪些"的判据**同源**（共用一个谓词函数），
并且"顶替物存在"≠"顶替物有内容"。装机 `6ea2b14883e7`。

### 收尾状态（11-05 上午·提交前如实记）

- **P0-1 header 时间**：A13 侧已改为**不搬时间**（`RELOCATE_HEADER_TIME=false`），时间留在原生左上槽；
  原生对照（整个禁用模块、按 pid 验零日志）证明原生时间戳全程不动 ⇒ 之前的"往左跑再闪回右"
  是我们搬迁与原生逐帧重排对拉的结果。用户认可"先这样"。
  **遗留取舍**：左上槽时间会与我们的大图标挤，候选方案是"这类行不放大图标"或"大图标挪到时间右边"
  （都只动我们拥有的视图）。
- **组摘要行图标空白**：`vivo服务` 已恢复；`Android 系统`（pkg=`android`）那行**仍未恢复**，
  且**没有读数**——逐帧通道只在状态变化时打印，滚动 8 次它一条没变。已抓到的两行都是
  "顶替图标在显示且带图、原生正常让位"，所以那一行的病因还没定位。
  下一步：把这组 `ivs=`/`big=` 字段挂到**每秒必打**的例行见证上（不依赖重绘），
  分清楚它是 `vis=8`（被 GONE）、`alpha=0` 还是 `da0`（被压）——三种对应三种修法，不猜。
- 逐帧通道新增字段 `ivs=`（屏上所有图标类节点的 类/id/可见性/alpha/drawableAlpha）与
  `big=` 的 `:v`/`:d` 两位，作为常备诊断（按状态变化打印，不刷屏）。

## 2026-11-05（OS5 / A11 播放器）两条待办 —— 已定位，等"开工"

设备已切到 OS5（Android 15 / OriginOS 15.0，DSU 未运行），`media_card_style=a11`，
装机 `727198fc38a5`。用户实测两件事仍存在，日志已给出直接证据。

### 待办 1（P1）：切歌瞬间进度条短暂消失、两枚时间跑到左边

**证据（`work/z8os3-live/os5-seek.log`，同名文件已含切歌窗口）**：
- 切歌期间**没有** `media bind` / `restyling` ⇒ 卡片没有被重建，不是"重建空帧"。
- 反复出现 `card bar reclaimed visibility was 4 -> 0` 与 **`8 -> 0`**：
  系统（`NotificationHeaderViewWrapper`，因为我们**借了 `android.R.id.progress`**）
  每趟 `selectLayout` 把我们那根条置 INVISIBLE/GONE，`reclaimBar` 每拍才补回来。
  **GONE 会把 `weight=1` 一起带走** ⇒ 这一排按零宽重测 ⇒ 两枚 `wrap_content` 时间贴到左边
  ⇒ 下一拍 reclaim 回来 ⇒ 用户看到"条短暂消失、时间跑左边再回来"。
- 我上一版加的"行与条分开判 + 条只 INVISIBLE 仍占位"没解决，因为系统的写入里包含 **8(GONE)**。

**修法（建议，按原生结构，不再和系统抢 id）**：
把这一排改成 A11 原生结构 —— **条独占一整行（满宽）**，**两枚时间在条下方另一行**左右贴边；
条无论被系统藏成 4 还是 8，都在它自己那一行里，**结构上不可能把两枚时间挤到一起**。
备选（快但风险高）：换掉借来的 `android.R.id.progress` 并自己接管横向手势
（那个 id 是否在 `NotifTouchInterceptor` 白名单里未验证，换错会吃掉拖动手势）。

### 待办 2（P1）：深色/浅色背景下，切歌按钮与文字看不清

**证据（同一份日志）**：`base=ffffffff`（白卡）配 `elapsedColor=ffffffff` / `totalColor=ffffffff`
（白字压白底）。取色链在"卡片底色"和"前景色"上分叉了。

**修法**：按**卡片底色的亮度**选前景（浅底→深字、深底→浅字），动作按钮沿用同一前景色；
对比度用 A11 原生的抬 alpha 手法（`findAlphaToMeetContrast` 只在 [当前 alpha,255] 二分，
只加实、不调淡），不要跟着封面取色走。断言改为"按底色重算出的前景色"而不是常量。

### 开工顺序

1. 待办 1 的结构改造（改动集中在 `MediaCardFeature` 里建 a11 seek 排的那段），
   保留现有 `a11 seek` 见证字段，并新增"条被系统改成 4/8 时我们不再逐拍 reclaim"的说明。
2. 待办 2 的前景选色（同时覆盖时间、按钮、标题/副文的既有 `enforceColors` 路径，避免两套规则打架）。
3. 两条都在 OS5 真机上切歌验证；不新增门禁，沿用现有 `a11 seek` / `a11 palette` 见证读数。

### 待办 1 收口（11-05 OS5）：切歌不再跳、拖动正常

最终有效的是**三条**，与我最初设想的"改成两行结构"无关（两行版实测把位置搞反、时间仍偏下，已回滚）：

1. **进度条用自己的 id**，不再借系统的 `android.R.id.progress`。借 id 等于把可见性交给
   `NotificationHeaderViewWrapper`：它每趟 `selectLayout` 按自己的状态置 4/8，
   GONE 还会连带把 `weight` 带走 → 同排零宽重测 → 两枚时间贴左、条消失
   （真机日志 `card bar reclaimed visibility was 8 -> 0` 反复出现）。
2. **手势自己拿**：`DragSeekBar.onTouchEvent` 按下 `requestDisallowInterceptTouchEvent(true)`、
   抬起/取消放开 —— 不再依赖拦截器白名单，换 id 后拖动依旧正常（用户实测）。
3. **`reclaimBar` 不再碰可见性**：换 id 后没有任何外部写入者，可见性只由
   `applyA11SeekVisibility` 一处决定，兜底循环随之删除。

用户验收：**切歌不跳、拖动正常**。装机 `22b77f4f7976`。
教训（已进 skill）：**借别人的资源（id/视图）就等于请了一个共同写入者；
要么自己拥有它，要么别做"每拍补回来"的兜底循环**。
仍待办：前景色按卡片底色亮度选族（日志里 `base=ffffffff` 配 `elapsedColor=ffffffff`）。

## 2026-11-05（OS5 / A11 播放器）本轮收口 + 下一轮交接

### 已验收（用户确认）
- **切歌不再闪条/乱跳、拖动正常**（commit `4d07c1c`）：进度条改用自有 id
  `ID_A11_SEEK`（不再借 `android.R.id.progress`）、手势由 `DragSeekBar` 自己
  `requestDisallowInterceptTouchEvent` 拿住、`reclaimBar` 不再碰可见性。
  两行结构试验**已回滚**（实测位置搞反），保持"时间两端、条居中"的单行。
- **按钮/文字看不清**：① 前景色族改为**比较黑/白两族的真实对比度**取高者
  （`A11NotifSpec.shouldUseDark`，原按"亮度>0.5"在浅桃底 `#ffd0a098` 上选了白字）；
  ② **全部动作按钮**统一着色（原来只给 `playBtn` 设 colorFilter，切歌键在深色封面上看不到）；
  ③ 切歌淡入**先取色后淡入**：`watchArtArrival` 用新到的封面（`pendingArtSrc`）先算调色板、
  上色，再 `syncArt()` 起 320ms 淡入。用户实测"效果很漂亮"。

### 下一轮任务：A11 收起态小播放器（用户明确要）

**已逆清的关键事实**：A11 **没有**独立的紧凑布局文件；收起态走**标准紧凑通知模板**
（小图标 + 标题/文本 + `android.compactActions` 指定的按钮），`MediaStyle` 只决定哪几个 action
进紧凑行（`work/y7s-a11/A11-Notification-MediaStyle.java:4892/5003/5018`）；
展开态才是 `MediaNotificationView`（`work/y7s-a11/A11-media-layout.xmltree.txt`）。

**实现三步**（全在已有接管点内，不需要新钩子）：
1. 为 `contracted` 子视图建紧凑行：圆角小封面（用**封面图**，不是应用图标）+ 标题/歌手 +
   播放键（+ 按 `compactActions` 取的动作）；点击/展开交给系统；
2. 颜色/按钮着色**复用展开卡那一套**（按底色对比度选族 + 全部按钮统一着色 + 先取色后淡入），
   避免两套规则打架；
3. 展开态保持现有整卡不动。

**环境**：设备在 OS5（Android 15），装机 `ed270c969760`，`media_card_style=a11`；
本地 `aapt2` 不在预期路径（`find` 全盘未命中），A11 SystemUI 模板数值改用
`work/y7s-a11/` 已有转储或重装 build-tools 后再取。
本地有未入库的改前备份 `work/NotifyRowFeature.pre-trace-1104.java`（可删）。

## 2026-11-05（OS5 / A11 播放器）收起态小播放器：按 A11 **真正的那份收起模板**重排

### 先纠正上一轮交接的前提（半句错）

交接写的是"收起态走标准紧凑通知模板（小图标 + 标题/文本 + compactActions 按钮）、圆角小封面"。
实测不是：**A11 的两态是两份媒体模板**，不是"媒体卡 vs 普通紧凑行"——

- `MediaStyle.makeMediaContentView`（收起）在 `!IS_TIER_VOS` 传 `50528353` = `0x03030061`
  = `res/layout/vigour_notification_template_material_media_q.xml`
  （`work/y7s-a11/A11-Notification-MediaStyle.java:5000-5005`）；
- `makeMediaBigContentView`（展开）传 `50528340` = `0x03030054` = `..._big_media_q.xml`（`:5053-5058`）。

两份都用 `right_icon` 放**封面**（不是应用小图标）、都无圆角（`ART_RADIUS_DP=0` 那条结论不变）。
真正的差别只有两处：**动作带的位置**和**有没有进度条**。

### 收起态的数值（`aapt2 dump xmltree` + `vivo_res.txt`/`fw_res.txt` 反查）

`vigour_notification_template_material_media_q.xml`：根 = 裸 `FrameLayout`；
`right_icon` wrap×match_parent + `adjustViewBounds` + `scaleType=6`(centerCrop)；
`include header_q`；`notification_media_content`(竖向) 里的 `notification_main_column` 是**横向**
（orientation=0），marginTop `0x030c0246`=42dp、marginStart `0x030c0245`=20dp、
minHeight `0x030c0253`=64dp（运行时被 `setContentMinHeight` 写成 `0x030c0257`=61dp），里面：

- 文字列 `0x010203a6`：竖向、`layout_weight=1`、`layout_gravity=0x70`=FILL_VERTICAL、
  paddingBottom `0x030c0242`=**14dp**；
- `media_actions`：wrap、`layout_gravity=0x00800035`=top|end、`layoutDirection=0`=ltr、
  **左右外边距一个都没写**（14dp/20dp 只属于展开那份）；按钮 `0x03030044` = 36×40dp、
  padding 8/6、margin 2、`scaleType=7`=centerInside；槽位收起 **4** 个、展开 **6** 个
  （action0..4 + media_seamless）。
- 收起只绑 `compactActions` 的前 4 个（`MS:5006-5022`，其余 setVisibility(8)），
  展开按顺序绑前 5 个（`MS:5059-5065`）⇒ 宿主本来就替我们把子集选好了，不用自己过滤。

⇒ 收起行 = 头部名 + [标题/歌手 | 按钮] + 通高封面，**没有进度条**；内容块 42+61=103dp。
我们之前给收起态画的是"展开那份去掉进度条"（动作带在文字下方），所以行高 143dp、比原生高一档。

### 三条实施要点

1. **紧凑结构按 `role` 建**：`MediaCard.build` 里 `compact = a11 && !expanded` 时把文字列与动作带
   装进同一个横向 main_column，边距/内缩全走上面那批常量（`A11NotifSpec.DEVICE_TEXT_COLUMN_PADDING_BOTTOM_DP`
   / `DEVICE_COMPACT_ACTIONS_MARGIN_DP`）。展开那支一行代码没动。
2. **收起永远不出进度条**：收起那份的根不是 `MediaNotificationView`，模板里的 ViewStub 没人 inflate
   ⇒ `applyA11SeekVisibility` 的判据加"紧凑结构就不出条"。这条不是摆设：行被展开时
   `restyleOnExpansion` 会对**两块卡**都 `setState(true)`，真机 `state=expanded … compact=1 … seekVis=8`
   就是在验它。
3. **给封面的右边距按 A11 的运行时式子重算**（`artSide + notification_content_margin_end(16dp)`，
   `A11-MediaNotificationView.java:74-80` 每次 onMeasure 都重写），不是一次性钉常量。
   模板层那条 72dp（`notification_media_image_margin_end`，`fw_res.txt:103221`）只在没有
   MediaNotificationView 那一支生效，差异留在见证里而不是藏起来。

### 顺手补的一个真坑：补扫路径分不清收起/展开

`installSweepHook` 走 `restyle(v, null)`，而 null 在旧代码里被当成"展开"。以前两态结构相同所以看不出来，
改成紧凑结构后它就变成观感缺陷（SystemUI 重启后收起子视图被建成展开排布）。现在用系统自己的公开判据
`NotificationContentView.getContractedChild()/getExpandedChild()`
（`work/systemui/out-src/NotificationContentView.java:379-384`）分辨，并在 `restyling` 行自报
`role=contracted|expanded|unknown src=setter|getter`。

### 取证与状态

- 装机包 `166c32b166bb`（设备 `pm path` 的 sha256 前 12 位与本地一致）。
- `work/offline-all.sh` 22 项全绿；`verify_os5_mirror.py --selftest` 新增 4 条反例（动作带翻到下方 /
  右内缩钉成常量 / 动作带被加边距 / **整段没有 compact=1 可测量帧**）全部被抓，
  并抓到一条旧反例因这条日志行变长而**空炮**（`A11 只有收起态几何见证` 的字面串没改到材料）——
  正是 skill 里那条"字面 replace 静默失效 = 假绿"。
- 真机 `work/z8os3-live/a11-compact-1105c.log` → `verify_os5_mirror.py --media-only`
  **55 条通过 / 0 失败**：收起 `size=309=contentH compact=1 btnPos=beside mainEndPx=357 btnMarginPx=0/0 seekVis=8`；
  展开 `size=555 compact=0 btnPos=below mainEndPx=-1 btnMarginPx=42/60 seekVis=0`（未回归）。
- 中间两次取样窗口各红过一次，都是**窗口没覆盖到那一拍**（没有换歌就没有 relief、
  没有建卡就没有 spec），不是产品坏；补一次"重启建卡 + 换歌 + 收起 + 展开"的窗口后全绿。

### 仍未闭环（诚实记）

- **A11 真机没有收起态样本**：`work/y7s-a11/live-a11-shade*.xml` 其实是 Z8 宿主的 dump
  （有 `notification_header/app_name_text`、无 media 行），当不了 A11 证据。所以收起态封面
  "到底多大"只有布局语义那一条推论（wrap×match_parent + adjustViewBounds ⇒ 由 largeIcon 原始
  尺寸夹出来）；我们按"边长=内容块高"画，与展开态同一口径。
- `aapt2` **在** `work/sdk-dl/bt35/android-15/aapt2.exe`（上一轮交接说"全盘未命中"是找错了地方），
  本轮的三份 xmltree 就是它 dump 的。
- 等用户上眼：收起行内容块从 429px 降到 309px（143dp→103dp），动作带回到文字右侧。

## 2026-11-05（傍晚，OS5）"展开/收起时小图标闪一下"：判据读了动画驱动量

用户报图标若隐若现，先要求回退对比 —— **回退到 HEAD 也闪**（包 `e6a058a48612`），
所以不是这轮收起态音乐卡带进来的（两条路径也不相交：媒体行在 `guardExpandedGhost`
里被 `isMediaRow` 显式排除）。定位过程本身有两条值得记账。

### 真因（一句话）

`bigIconShowing()` 里那句 `big.getAlpha() <= 0.5f || !big.isShown()`
把"**有没有顶替物**"问成了"**顶替物此刻在不在屏上**"。展开/收起动画里 vivo 会连子树
alpha/可见性一起写回，我们那枚 42dp 大图标的 `isShown()` 在过渡帧就是 0 ⇒ 判成"没有顶替"
⇒ 走 `suppressNativeIcon(ni,false)` 把原生小图标**放回去** ⇒ 用户看到小图标闪一下，
过渡结束又被人按回去。逐帧读数（`betteroriginos_trace_flicker`）：

```
HEAD   5f3b164  big-d1 | v0:a1.00:da255  ⇄  big-d1 | v4:a0.00:da255   ← 露出帧 / 按回帧
HEAD   16af783  big-d1 | v0:a1.00:da255                                ← 大图标在，小图标也全程露着
修复后 4 行 big-d1 全部稳定在        v4:a0.00:da0                      ← 没有露出帧
```

改法：判据只看**归属 + 有没有内容**（`findTag(root)!=null` 且 `getDrawable()!=null`），
不读 alpha/isShown。"顶替物真的有图"那条硬判据保留（`android` 包空壳那种还是会让壳自己 GONE）。

### 两条被数据否掉的东西（别再走）

1. **"两份 header 拷贝共用同一个 Drawable ⇒ 帧频互打"**。我为它写了 `privateDrawable()`
   （`getConstantState().newDrawable().mutate()` + `setImageDrawable`）并配了 PRIVATE/PRIVATE-SKIP/
   PRIVATE-FAIL 三条见证，装机后 **三条都是 0 条** —— 那条路径从来没被调用。已整体删除，
   不留不会跑的机制。真正的往复是"同一枚图标被同一个 root 一会儿判有顶替、一会儿判没有"，
   也就是上面那条判据错，不是通道共享。
2. **判据升到"行级"**（一整行里任一份拷贝有大图标就都压）：`RELEASE` 从 526 掉到 4、
   确实不抖了，但真机读数里 `big=-` 的摘要行图标也被压掉 ⇒ **把"闪"换成"空白"**，
   已回退到子树级判据。

### 一条被假阴性见证喂出来的错判（今天第二次）

`RELEASE` 那条日志原来的条件是 `ni.getScaleX() == 0f` —— scale 压制方案早就废了，没人再写
scale，于是这条见证**永远不打印**，昨天的记录因此写下"我们自己的放开路径一条都没跑过
（light.native RELEASE 0 条）"。换成按语义判断（放开前 drawable alpha 真的是 0）之后，
同一台机同一套操作打出 **436 条**：放开一直在跑。
教训：**0 条永远要先怀疑见证本身有没有打印条件成立**，不能直接当"没发生"的证据；
`mutate()`+`setAlpha` 这类写也一律加"已经是目标值就不写"的幂等门，否则每帧写自己就是
一台重绘机（窗口永不 idle，dump 都取不到）。

### 仍挂着

- drawable 层面还有 0↔255 的往复（436 条 RELEASE），目前被视图层 `v4:a0.00` 掩住、肉眼看不见；
  它是"两个写者"没并干净的余债，单独跟，不在本轮硬修。
- 组摘要行（`no-big` 那批）图标仍是 `v4:a0.00` 被藏着的状态 —— 就是上午那条
  "`Android 系统` 图标空白"老债，本轮**没有改动它**，HEAD 与修复版读数一致。
- 装机 `114333a5eaab`；离线 22 项全绿；对比证据
  `work/z8os3-live/icon-trace-1105.log`（HEAD）vs `work/z8os3-live/icon-trace-1105-shipped.log`。
  等用户上眼确认"展开/收起不再闪"。

## 2026-11-05 · 0.1.2-beta 发布

- 版本：`versionCode=4`，`versionName=0.1.2-beta`（`module/AndroidManifest.xml`）。
- 成品：`out/BetterOriginOS-0.1.2-beta.apk`，sha256 前 12 位 `56c01a4db09f`、
  md5 `cd48db6c07c5f2d24bf5a0f86bd5f775`。
- 上传包：`out/BetterOriginOS-0.1.2-beta.zip`（290,800 字节），按 0.1.1-beta 的三件套格式打：
  `BetterOriginOS-0.1.2-beta.apk` + `CHANGELOG-0.1.2-beta.txt`（UTF-8 BOM，含 MD5/SHA-256 两行）
  + `MD5.txt`（`<apk 文件名>  <md5>`，LF 结尾，与旧包一致）。
  校验过包内 apk 的 sha256 与成品一致 —— 注意上一版我先把 `.zip` 当成 apk 的**改名副本**了，
  那是错的（旧包是 deflate 容器，233KB < apk 254KB），已重打。
- 使用者向的更新日志：`out/CHANGELOG.md`（新增/优化/修复/已知问题/更新后五段，功能名取 `strings.xml`
  里用户在 App 里看到的文案，不写没验证过的东西）。
- 本轮内容（相对 0.1.1-beta）：A11 原生卡（Ocean）媒体卡样式 + **它的收起态小播放器**、
  「OS5 通知观感」移植选项、A11 卡前景按实际对比度选族 + 全按钮统一着色 + 先取色后淡入、
  进度条自有 id 与自持手势、OS3 不再搬 header 时间、**展开/收起时小图标闪现的修复**、
  摘要行空白圆角边框的一批恢复。
- 冒烟（本轮实测，全部 exit=0 / 全绿）：
  - 离线总检 `work/offline-all.sh` → `work/offline-all-012beta.log`（22 项；含收起态 4 条新反例，
    以及 selftest 自己抓住的一条"旧反例因日志行变长而空炮"）。
  - 设备侧包体核对：`dumpsys package com.betteroriginos` = `0.1.2-beta / versionCode=4`，
    设备端 `sha256sum` 与成品一致；注入 729 条模块日志、无 install failed。
  - 媒体卡：`python work/verify_os5_mirror.py --media-only work/z8os3-live/release-012beta-media.log`
    → **60 通过 / 0 失败**（收起态 `compact=1 btnPos=beside mainEndPx=size+16dp` 与展开态同一窗口可测）。
    注：第一次取样只跑了"展开一次"，`collapsed` 全是 `contentH<=0` 的预布局帧 ⇒ 验收器判红是**窗口没覆盖**，
    补做三轮"面板展开→收起"让收起子视图真的被布局出来后才过。
  - 图标闪现：`work/z8os3-live/release-012beta-icontrace.log` —— 有大图标的采样 13 帧里
    "原生小图标露出帧"**0 条**；同一操作在 HEAD 上是 2 行有露出帧（对比
    `work/z8os3-live/icon-trace-1105.log`）。
- **未跑（如实记）**：OS3（DSU / Android 13）的三套行排版门禁本轮没跑 —— 设备停在 OS5 宿主、
  DSU 镜像未启动。`work/a11-card-gate.sh os5` 也没重跑（它自己会重启 SystemUI 并拒绝猜安全锁，
  本轮用等价的取样 + 验收器覆盖了同样这批断言）。
- 遗留观感债（已知问题，写进 CHANGELOG）：`Android 系统` 一类 `pkg=android` 的组摘要行图标仍缺失/空白
  （HEAD 与本版读数一致，本轮没动）；drawable 层还有 0↔255 的往复（436 条 RELEASE）被视图层掩住，
  看不见但两个写者没并干净。
- 设备已还原：`betteroriginos_notify_verify=0`、`betteroriginos_trace_flicker=0`、
  `svc power stayon false`、面板收起；媒体卡样式仍为 `a11`。
- 工作区未提交（模块源码、验收器、DEVLOG/skill、CHANGELOG 与成品 apk/zip）。

## 2026-11-10（OS4 / vivo Y37）桌面过渡动画：逆向 + 模块功能落地

新设备：**vivo Y37**，Android 14 / OriginOS 4（OS4；机型代码 V2357A，raw `ro.vivo.product.model=PD2357`），桌面 `com.bbk.launcher2` 14.1.0.241，
LSPosed 换成了 **Vector 2.2**（CLI `/data/adb/lspd/cli`，没有 `lspctl`）。完整逆向与代码地图：
`out/LOG-1110-OS4-LAUNCHER-ANIM-REVERSE.md`（罗盘日志）。

- **根因**：用户可见的“桌面过渡动画”已是最高档（舒适=3），但生效的
  `/oem/etc/launcher/attributes_preconfig.xml` 写死 `registeranimation=false`、
  `wallpaperTargetAnimation=false`（`dynamic_blur=true`，无 `animationGrade` → 缺省 1）。
  桌面因此走旧链路；`LauncherConfigManager`（`r1.a`）把它们解析成字段 `L/M`，getter `w0()/y0()`。
- **第二层“层级”**：`AnimationLevelUtils.isTwoLevelAnimation()` 要求
  `w0 && k()==1 && h()==2 && UnLockAnimManager.f0()`；`h()` 由 `power_save_type` +
  `ro.vivo.product.series`（IQOO/其他）决定。本机 `power_save_type=1`、series=Y → h=1，two-level 关。
  复核否掉了“MTK 强制关 launcher scale”的误判：真变量是 `LOW_PLATFORM_ANIM2=false`。
- **落地**：新增 `feature/desktop/DesktopMotionFeature`（id `desktop_motion`，默认关，scope
  `com.bbk.launcher2`）。实测预配置在注入前就解析完，XML 属性钩子 0 命中；主锚点改为
  `LauncherEnvironmentManager.F1()` 拿到 config 实例后置 `L/M=true` 并 hook `w0()/y0()`。
  只开这两个变量，`animationGrade`/two-level/动态模糊本轮没动。
- **验证**：装 `320,062 B` 包；日志 `config patched class=r1.a fields=2/2 methods=2` +
  `forced w0=true`；用户确认“动画生效了”。
- **设备侧**：Vector 里模块原为 disabled/scope 空，已 `modules enable` 并加
  `com.bbk.launcher2/0`、`com.android.settings/0` 两个 scope；功能键
  `betteroriginos_feature_desktop_motion=1`。
- **系统设置入口恢复**：入口在 设置 → 动态效果 → 界面动态效果，由 `SettingsItemsUtils` 扫描
  PackageManager 动态插入（launcher manifest 的 `vivo.settings.title/order` meta-data，
  order=100）。它被 Settings 两处判定隐藏：`DynamicEffectSettings.isItemEnabled` 对 100/300 直接 false；
  `EnhanceDynamicSettings.isItemEnabled` 要求 `support_app_animation_window==1`（本机预配置 false ⇒
  桌面启动写回 0）。修法：Settings scope 下 hook `EnhanceDynamicSettings.isItemEnabled`，
  功能开启时放行 order=100。证据 `settings entry hooks=1` + `settings entry enabled=1`，用户确认“有入口了”。
- **档位选择器**：本机原生入口缺失，模块卡片新增 `level`（无动效/高效/中等/舒适），经
  `Feature#onOptionChanged` → `SuCommander.writeSystem("enter_exit_app_animation", v)` 走原生链路即时生效。
- 详细逆向（页面结构、两层隐藏条件、类/方法、资源 id、素材路径）见罗盘日志 §12。
- 未提交（`DesktopMotionFeature`、`SuCommander.writeSystem`、`Feature#onOptionChanged`、
  FeatureManager/FeatureAdapter/strings/arrays、XposedHelpers 桩、罗盘日志 §11–12、work/os4-re）。

## 2026-11-11（凌晨，无人值守）OS4 适配第二批：媒体卡三样式 + 快门录像态 + 选择器探测

设备 vivo Y37（Android 14 / OriginOS 4，Vector 2.2）。上一批（c8ad62c）只落地了桌面动效；
本批把相机快门、媒体卡、选择器全部过完并提交（55863f3）。

### 快门（OS4 直接命中，修一处）
- 四类（ShutterButton/DualPointButton/RotateImageView/TwoStateImageView）+ `shutter_button`/
  `ic_capture_camera` 资源在 OS4 相机 13.5.3.0 里全在；hook 面变为
  `ImageView.setImage*=2 / RotateImageView.onDraw=1 / ImageView.onDraw=1`（OS4 相机不再
  自己声明 setImage*/onDraw，靠基类钩子兜住）。画环 ✓ 拍照 ✓（IMG_20261110_234754）。
- **坑**：OS4 切模式不走 setImage*（icon hooks 全链 0 命中）→ LAST_NAME 恒空 → 录像态也被
  接管（白环盖住原生红盘）。修：快门 content-desc 兜底（录像态="开始录制…"，拍照态=
  "拍照…"），见 desc 即让位。修后录像态原生红盘 ✓。

### 选择器（hook 直接命中，修一处）
- `queryAlbums`/`appendWhere` hooks=2 直接生效：相册 tab 从 3 个白名单 → 全量 43 个
  （真机截图 + `refresh: albums=[...]` 见证）。
- **坑**：`pickerState` 用错 action（`android.intent.action.PICK_IMAGES`，真值
  `android.provider.action.PICK_IMAGES`）→ "landing no-entry" 是假阴性，已修。

### 媒体卡（OS4 上四处新坑，全部真机取证）
1. **安装循环**：Vector 重复派发 handleLoadPackage（8 秒内 systemui×2+magazine×2），
   且会整体重建模块 classloader（广播后静态归零）→ hook 全量叠加。修：FeatureManager
   双重防重入（静态集合 + 宿主进程 SharedPreferences，键=`installed_<进程启动时刻>`）。
   注意 `notify row feature installed header-time=` 是 NotifyRowFeature 的**低频补发见证**
   （headerTimeMode），不是重装——别再当循环排查（凌晨我在这上面空转了一轮）。
2. **OS4 媒体模板两副面孔**：收起态=framework `notification_template_material_media`
   （`media_actions`/`right_icon`，根 MediaNotificationView，识别照旧）；展开态是 vivo 的
   MediaStyle 族（`action0..4`、无 id 的头部/封面/进度行，SeekBar 是 vivo 加的）——
   旧探测对它全盲。修：discoverMedia 统一探测（actions=media_actions → action0 父容器；
   art=right_icon → 子树最大方形 ImageView），hideOldExpanded 藏原生进度行。
3. **整卡被藏**：hideOldExpanded 顺着 app_name_text 爬到 root 直接子级——a11 建卡时 app 名
   已搬进我们的卡，一爬正好爬到本卡 → 整卡 GONE（行里只剩 148px 空壳、取色日志却活着）。
   修：原生无 id 头部必须在 build 摘 app 名**之前**藏（此刻爬升才指向原生容器）。
4. **title 看不见**：外部写手（模板 wrapper 一族）对搬进我们卡的 `android:id/title`
   反复写 INVISIBLE/alpha=0（见证 titleVis=4、color=ffffffff/alpha=0.0/xy 全对——
   修可见性没用，alpha 修完又被写回）。修：**不再搬原视图**——自建 TextView 复制
   文案/字号/颜色，原生 title 留在模板里原地 GONE（RemoteViews 重投照常更新它，
   我们的文字由会话元数据同步）。isNight 同时改用 SystemUI 上下文（bbkmusic context
   浅色 → os5/material 出"暖黑底+深棕字"）。
5. **os5 背景"糊了"**：softBitmap=20×20+3×3 均值拉伸=糊泥；改 96px+三轮 box blur 后
   第一版又踩 `createScaledBitmap` 同尺寸返回**不可变原图** → setPixels IllegalStateException
   （真机栈）。终版：自建可变位图 + Canvas 绘制再模糊。像素级验收：取色 bg=ff088090
   vs 截图 (8,127,143)，1/255 内一致。

### 面板关闭会剥行（OS4 行为，别当 bug）
静默区（importance=2）的通知行在面板关闭时被 vivo 剥成空壳（`MediaContainerView→
RoundRelativeLayout` 无子级），重开面板时 setChild 重新绑 → 卡片随面板自然重建。
勿扰开着时 bbkmusic 通知 importance=2 进静默区——`cmd notification set_dnd off` 关勿扰
不能恢复 importance（channel 决定），但行仍会 attach，卡片照常接管。

### 样式即时切换
样式选项 ContentObserver → forceRestyleAll → destroy（把 title/按钮归还模板）→ 重扫重建。
真机 `force restyle all style option changed rebuilt=2 dropped=0`，os5/material/a11 三样式
来回切换全部即时生效。

### 验收（代码层）
- offline-all 22 项绿。
- `verify_os5_mirror.py --media-only` 对 OS4 真机日志（os4-live-full.log）：76 通过/0 失败。
- 快门：画环日志 `drawn outer=140 desc=拍照` + 拍照产物；录像态让位截图 + 无 paint 日志。
- 选择器：相册 tab 截图 + albums 全量日志。
- 三样式：os5 磨砂底（45/49 unique 像素）+ material（accent 橙红）+ a11（bg=088090 青取色）。

### 未竟
- 通知栏大图标（notify_row）移植到 OS4 未开始（本批 notify_row 保持 0=关）。
- a11 收起态（compact）在 OS4 上的几何未单独验证（展开态已验）。
- a11 的 QS 磁贴路径（OsProfile.isQsTilePlayer）在 OS4 不适用（媒体在通知列表，SDK34 判定
  正确），`ControlCenterMediaPlayerView` 在 OS4 插件里存在但未被使用。

## 2026-11-11（凌晨续）notify_row 大图标移植 OS4：原生直接命中，结果级验收全绿

上一批把 notify_row 键压在 0（防未移植行为外溢）。本轮实测：`isLightStyle` 恒真 +
light polish 是版本无关路径，OS4（A14 framework 模板）**开键即命中**——

- 开 `betteroriginos_feature_notify_row=1` 后 `restyled light bigIcon=1 geo=os5-column`
  连发，42dp 圆角大图标进内容列、标题同排（快手/Vector/USB 各行）。
- 结果级验收（HANDOFF-1103 §6.1 纪律，新增 `work/verify_row_os4.py`）：
  - A1/A2 大图标 3 枚、42dp、内容列 left=36dp（dump 实测 [72,847][156,931] 等）；
  - A3 标题与大图标同排（top ±14dp 且 title 在图标右侧——断言第一版比错了轴
    （left 对 left），实际布局是"图标列左、文字列右"，修正后 3/3）；
  - L1 `restyled light ... bigIcon=1 geo=os5-column` ×8。
- A13 专属的 sep/os5-mirror 选项在 buildOptions 有 isA13OrLower 门，OS4 不显示不生效（预期）。
- 已知同源债（非本轮回归）：组摘要行（"Android 系统"）图标空白——Z8 上同款，本轮未动。
- SystemUI 进程 9573 自 01:50 起稳定，0 crash。
- 验收器跑法：`python work/verify_row_os4.py <dump.xml> <logcat窗口> 2.0`
  （dump=面板展开后的 uiautomator dump，log=开启 notify_row 之后的模块日志窗口；
  **日志窗口必须覆盖开启之后的时段**，旧窗口会假红 L1）。

## 2026-11-11（晨）用户报两个观感：a11 切歌黑左底、os5 切歌右侧小封面闪现——均已修

### 1. a11 切歌"左边黑"（奢香夫人/读心术复现）
根因不是取色算错（日志里每首歌的 bg 都对：奢香夫人=f0f0e8、读心术=f8f8f8），
而是**一部分卡的 art 引用是 null**：建卡那一拍 RemoteViews 还没把 largeIcon 投进模板，
discoverMedia 的 right_icon/largestArtView 都拿不到 drawable ⇒ art=null。此后
bbkmusic 的元数据里没有封面位图 ⇒ artSource() 永远 null ⇒ refreshColors 在
`src==null && curBg!=null` 早退 ⇒ 这张卡**永久停在建卡时的黑底**，换歌也不取色。
修：`rediscoverArtIfNeeded()`——refreshColors 开头重探一次（right_icon → 最大方形
ImageView，探到即接管：GONE + 挂防闪守卫 + 作废封面指纹）。真机：`art rediscovered`
→ `src=160x160` → 每首歌非黑取色（心里挂念=f85860 红、善变=088090 青、搀扶=000040 藏青）。
配套把会话重试首轮 2.5s→1.2s（解锁后的黑窗从 ~3.5s 缩到 ~1.5s）。

### 2. os5 切歌"右侧小封面闪现"
原生 right_icon 被通知重投重新 show 出来（`a11 art native reshown` 日志）——防闪守卫
`guardArtHidden` **以前只挂在 a11 分支**，os5/material 没人再藏。修：守卫放开全样式
（build + syncArt 两处都去掉 a11 门槛）。换歌时 `reshown rehidden` 在 os5 卡上同样触发，
重投→同帧 layout 内藏回，无可见闪现。

### 验收
offline-all 24 项绿；a11 卡结构 dump（texts=[80,412] a11 位、header 行、无源图标动作排）；
每轨取色 bg 非黑；os5 卡换歌守卫日志。另：面板空闲时 screencap 会拿到**旧帧**
（视图树已更新、无重绘触发）——验证一律以 dump+日志为准，截图可能滞后一首歌。

## 2026-11-11（上午续）"切到奢香夫人左边还是黑"——三段根因链闭环

第一轮修复（art=null 重探、守卫全样式、rediscover）之后用户复测仍黑。逐层取证：

1. **浮雕用了黑底**：bindSession 的顺序是 syncArt → refreshColors（我把强制重取色
   插在了 syncArt 后面）——浮雕拿建卡时的黑底画一张（`relief color=ff000000`，
   真机 07:00:11 栈），之后封面指纹不变就没人再触发 syncArt，黑左底陪伴整首歌。
   修：bindSession 改成先取色后画封面（与 watchArtArrival 同序）。
2. **TransitionDrawable 冻结**：它只在被绘制时推进——闲置面板没有后续绘制，
   过渡冻结在第 0 帧（旧底/黑），视图树里新底色早就就位（dump 证实文字已同步而
   像素还是黑）。修：过渡后 `postInvalidateDelayed` 补终帧绘制（卡片底色 + 封面
   淡入两处都补）。
3. **sweep 永久拉黑**：`sweep empty-actions`（我上一批加的守卫）会把 root 记进
   SKIP_LOGGED，而 sweep 循环按 alreadySkipped 直接跳过——剥行重建后的新内容
   从此补挂不上，可见行只剩原生新内容盖在旧卡上。修：sweep 循环只挡 known，
   瞬态 skip 允许重试（skipOnce 仍去重日志）。

### 验收
- 搀扶（藏青 000040）：左底像素 (1,1,65) vs 取色 ff000040 —— 1/255 一致；
- 奢香夫人（米白 f0f0e8）：日志 relief color=fff0f0e8 两步收敛（黑→正确，间隔 240ms）；
- offline-all 24 项绿。
- 教训：截图帧与视图树可能差整整一拍（冻结的过渡 + 闲置面板不重绘），
  验证永远以 dump+日志+同帧像素三件套对齐，单看截图会被骗两轮。
