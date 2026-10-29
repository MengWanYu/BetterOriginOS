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
