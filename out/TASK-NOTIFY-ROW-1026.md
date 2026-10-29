# 任务书 · 通知中心通知条 iOS 化（2026-10-26 立案，未开工）

> 关联：`out/HANDOFF-1026-OriginUI.md`（交接）、`out/DEVLOG.md`（开发日志）。
> **开工前先加载 skill**：`/betteroriginos-dev`（Hook/构建/验证规范）→ `/originui-integration`（若 iOS 行视图放进复刻库）。
> 技能文件：`.zcode/skills/betteroriginos-dev/SKILL.md`、`.zcode/skills/originui-integration/SKILL.md`。
> 状态：**待开发**。本文件是需求 + 逆向清单 + 方案 + 验收标准，开工后每步回填证据。

## 1. 用户原话与问题定义

> "通知中心的通知条也想改善一下，改成类似 ios 的显示逻辑，现在的通知条图标只有左上角一小块，空间布局也不合理，需要单独开个通知条优化条目了"

现状证据：`work/os5-ref/shot-notify-shade-os5-1026.png`（OriginOS 5 原版下拉通知中心）。

- 每条通知的 app 图标只是**左上角 18dp 小圆块**（见 §2 源码铁证），缩在一个 46dp 的 header 行里；
- 图标与正文脱节：标题/正文整行从卡片左边距开始，图标只占 header 行最左一小格；
- header 行内还塞了 app 名、时间、展开箭头、删除按钮，信息挤在一条线上；
- 用户要的是 **iOS 显示逻辑**：大 app 图标居左贯穿两行、右侧竖向排「app 名+时间 / 标题 / 正文」，
  图标区域成为视觉锚点而不是装饰。

## 2. 现状逆向（已确认的事实，直接可用）

### 2.1 视图层级（SystemUI 15.0.5.241，本机）
`work/systemui/res/res/layout/status_bar_notification_row.xml`：
```
ExpandableNotificationRow (id/expandableNotificationRow)
├── NotificationBackgroundView (id/backgroundNormal)      ← 卡片底（媒体卡模块曾隐藏它）
├── NotificationContentView (id/expanded)                 ← 收起/展开内容宿主（关键 hook 点）
├── NotificationContentView (id/expandedPublic)
├── Button (id/veto)
├── ViewStub child_container_stub → notification_children_container  ← 分组子通知容器
├── ViewStub notification_guts_stub
├── ViewStub vivo_notification_block_menu_stub
└── ViewStub vivo_headsup_launch_handle_stub
```
内容由 `NotificationTemplateViewWrapper` 按模板 inflate **framework-res 的 vigour 模板**：

`work/os5-ref/framework/vivo-res-dec/res/layout/`
- `vigour_notification_template_material_base_vos.xml`：`<include header>` + 内容区
  （`@android:id/marquee_forever`，marginTop/Bottom/Start/End 用 `vigour_notification_content_margin_*_vos`）
  → `vigour_notification_template_part_line1_vos`（行1：app 名/时间/重要标记）
  → `vigour_notification_template_text_vos`（标题+正文）
  → progress / smart_reply
  → `vigour_notification_template_right_icon_vos`（右侧大图）
- `vigour_notification_template_header_vos.xml`（**问题根源**）：
  ```
  NotificationHeaderView  高 @dimen/vigour_notification_header_height_vos = 46dp
  ├── CachingIconView @android:id/icon
  │     layout 18dp × 18dp, padding 2dp, background=vigour_notification_icon_circle,
  │     marginEnd = vigour_notification_header_icon_margin_end_vos = 3dp
  ├── NotificationTopLineView（app 名 + 时间 + 免打扰等）
  └── header_button_container（删除/展开箭头）
  ```
  即：图标 **18dp**、header 高 46dp、内容左右边距 `vigour_notification_content_margin_start_vos=16dp` / `_end_vos=16dp`。

### 2.2 相关类（已在 `work/systemui/classlist.txt` 确认存在）
- `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow`
- `...row.NotificationContentView`（已在本模块媒体卡里 hook 过 `setExpandedChild/setContractedChild`，见 `MediaCardFeature`）
- `...row.wrapper.NotificationTemplateViewWrapper` 及子类：
  `NotificationBigTextTemplateViewWrapper`、`NotificationBigPictureTemplateViewWrapper`、
  `NotificationMessagingTemplateViewWrapper`、`NotificationConversationTemplateViewWrapper`、
  `NotificationCompactMessagingTemplateViewWrapper`、`NotificationCompactHeadsUpTemplateViewWrapper`、
  `NotificationCallTemplateViewWrapper`、`NotificationMediaTemplateViewWrapper`、
  `NotificationCustomViewWrapper`、`NotificationDecoratedCustomViewWrapper`、`NotificationHeaderViewWrapper`
- 其它：`HybridNotificationView`、`NotificationRowLayout`（列表）、`NotificationChildrenContainer`（分组）

### 2.3 已踩过的坑（媒体卡经验，直接复用）
- 通知 View 的 Context 是**发起通知应用的 createPackageContext**（如 bbkmusic），拿 SystemUI 资源/存储
  必须 `ActivityThread.currentApplication()` 反射取 SystemUI 本体 Context。
- `NotificationBackgroundView`（白底）会盖住自绘卡片四角 → 媒体卡方案是 alpha=0/INVISIBLE。
- 通知标题可能是 `PrecomputedText`，直接 `setText` 到自定义控件要 `.toString()`。
- 分组子通知走 `child_container_stub`，`ExpandableNotificationRow.getChildrenContainer()`；
  子行不是独立 ViewGroup 顶层，重构时必须区分 `row.isChildInGroup()`。

## 3. iOS 目标规范（草案，可先按此实现，用户可后续调）

参考 iOS 通知（单条、收起态）：

| 元素 | 规格 | 说明 |
|---|---|---|
| 卡片 | 圆角 20dp，左右边距 16dp，内边距 12dp，卡片间距 8dp | 比 OS5 的 22dp/18dp 更紧凑 |
| app 图标 | **38dp × 38dp，圆角 10dp**（连续曲率可用 G2），左上对齐 | 取代 18dp 小图标，成为视觉锚点 |
| app 名 | 12sp regular，text_secondary，位于图标右侧第一行左端 | 替代原 header 行 |
| 时间 | 12sp regular，text_secondary，第一行右端 | 与 app 名同一基线 |
| 标题 | 15sp semibold，text_primary，最多 2 行，ellipsize | 位于 app 名下一行 |
| 正文 | 14sp regular，text_secondary，最多 4 行 | 标题下方 2dp |
| 图标与文字间距 | 12dp | 图标右缘到文字左缘 |
| 分隔线（组内） | 1px #D9D9D9 / 15% 白，**左缩到文字左端**（图标宽+间距） | iOS 组内通知之间 |
| 点击 | 整行 → 原通知 PendingIntent | 保持原行为 |
| 长按 | 原通知操作/设置入口 | 保持原行为 |
| 展开 | 先保持 OS5 原展开态（阶段 2 再 iOS 化） | 降低风险 |
| 分组（≥2 条） | 卡片顶部一次 app 图标+app名+时间；组内每条只显示标题/正文，发丝线分隔；折叠态显示前 3 条 + “还有 N 条” | 阶段 2 |

**与现状差异对照**：
| 项 | 现状 OS5 | 目标 iOS |
|---|---|---|
| 图标 | 18dp 圆，header 行内 | 38dp 圆角方，贯穿两行 |
| app 名/时间 | header 行内紧贴图标 | 与标题同列、图标右侧第一行 |
| 内容起点 | app 名在图标右侧，标题整行从卡片边距开始（与图标不对齐） | 标题/正文与 app 名同列对齐（图标右侧） |
| header 行 | 46dp 独立行 | 取消独立行，信息合并进内容列 |
| 卡片边距/间距 | 22dp / 18dp | 16dp / 8dp |

## 4. 技术方案（推荐 B，A 为兜底）

### 方案 A：改现有视图（低风险、视觉上限低）
hook `NotificationTemplateViewWrapper.updateView` / `NotificationHeaderViewWrapper`：
- 把 `@android:id/icon` 放大（18→38dp）、去掉圆底、调整 margin；
- 把 header 行高度改 0/隐藏，把 app 名/时间挪到内容列；
- 问题：标题/正文是 framework 布局里的独立 LinearLayout，无法简单与图标同列对齐；模板多（bigText/inbox/messaging）要逐个适配。

### 方案 B：重建收起态内容（推荐，参照 MediaCard 成功经验）
1. hook `NotificationContentView.setContractedChild(View)` after：
   - 若 `row.getEntry().getSbn()` 是媒体通知 → 跳过（交给 MediaCardFeature）；
   - 否则用自定义 `IosNotificationRow` 替换 `child`（保留原 child 引用供展开态用）。
2. `IosNotificationRow` 自绘/自排：大图标（`PackageManager.getApplicationIcon(pkg)` 或
   `NotificationEntry` 的小图标位图放大）+ app 名 + 时间 + 标题 + 正文。
   - 文案来源优先级：`Notification.extras` EXTRA_TITLE/EXTRA_TEXT/EXTRA_BIG_TEXT/EXTRA_SUB_TEXT；
     时间：`sbn.getPostTime()` 格式化为「刚刚/N 分钟前/HH:mm」；
   - 点击/长按转投原 row（`row.performClick()` / `row.callOnClick()` 或直接 launch PendingIntent），
     保证展开、快捷操作、滑动删除仍由 SystemUI 原逻辑处理。
3. 分组：`ExpandableNotificationRow.isChildInGroup()` 时用「无图标紧凑行」；
   `getNotificationChildrenCount()>0` 时用「组头（图标+app名+时间+N 条）+ 子行」布局。
4. 阶段 2：hook `setExpandedChild` 同样替换（展开态 iOS 化）。

### 方案 C：混合（保留原布局，仅注入大图标）
只把 18dp 图标换成 38dp 并把 header 图标位预留 50dp —— 最小改动，但标题/正文仍从卡片边距起，**达不到 iOS 的列对齐**，不推荐。

**推荐：B（收起态先做），A 作为异常兜底（替换失败时恢复原 child）。**

## 5. 逆向 → 实现 任务拆分（每步都要真机截图 + 日志）

1. **基线采样**（0.5h）
   - 构造 6 类通知：单条、同 app 2 条分组、BigText、Inbox、Messaging、Media。
   - 每类：截图 + `uiautomator dump` + `dumpsys notification --noredact` 关键行，存 `work/os5-ref/notify/`。
2. **逆向绑定链**（1h）
   - 读 `NotificationContentView`（`work/systemui/out-src/NotificationContentView.java` 已有提取）与
     `NotificationTemplateViewWrapper`、`ExpandableNotificationRow`（jadx 反编译到 `work/systemui/out-src`）。
   - 记录：contracted child 何时创建/复用、`NotificationEntry` 如何取、icon 从哪来、点击链。
   - 产出 `vivo-OriginUI/docs/04-notification-row.md`。
3. **iOS 行视图**（2h）
   - `vivo-OriginUI/lib/src/com/originui/replica/notify/IosNotificationRow.java`（纯 Java，可复用）。
   - 参数全部来自 §3；文字 ellipsize、maxLines、时间格式。
   - 在 demo 加一个静态演示页（不依赖 SystemUI），先做视觉验收。
4. **Feature 骨架**（0.5h）
   - `module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java`，id `notify_row`，
     category 新 `category_notify`（"通知"），`defaultEnabled=false`（稳定后再开）。
   - `FeatureManager` 注册；strings/drawable 补齐；scope 沿用 systemui。
5. **Hook 收起态**（2h）
   - hook `NotificationContentView.setContractedChild` after；黑名单：media / custom view / 悬浮通知。
   - 失败回退原 child；logcat `notify_row: restyled pkg=...`。
6. **分组**（2h）
   - 组头 + 子行；`isChildInGroup` 紧凑行；展开/折叠计数文案。
7. **交互保真**（1h）
   - 点击 → 原 row 行为；长按 → 原操作；滑动删除不受影响；展开箭头/整行点击展开。
8. **回归**（1h）
   - 媒体卡、headsup、锁屏、深色、RTL、大字号、无通知、分组展开。
9. **记录**（持续）
   - `out/DEVLOG.md` 每次一条；截图进 `work/os5-ref/notify/`；最终并入交接文档。

## 6. 风险与规避

| 风险 | 影响 | 规避 |
|---|---|---|
| 替换 contracted child 破坏展开动画 | 通知无法展开/动画错位 | 保留原 child；展开态暂用原布局；点击走原 row |
| 与 MediaCardFeature 冲突 | 媒体卡被覆盖 | 媒体通知跳过；判定 `sbn.getNotification().isMediaNotification()` 或 template 类型 |
| 自定义 View 通知（音乐/下载）被误改 | 第三方布局破相 | `NotificationCustomViewWrapper` 类型跳过 |
| 分组子行重构导致错位/计数错 | 分组显示异常 | 先只做「组头样式」，子行保持原样（阶段 2 再动） |
| 多模板（BigText/Inbox/Messaging）文案取值不同 | 标题/正文缺失 | 统一从 `Notification.extras` 取，缺省回退原 child |
| 大字号/无障碍 | 布局溢出 | 文字用 maxLines+ellipsize；测试 font_scale 1.3 |
| 折叠屏/RTL | 图标位置反 | 用 start/end 而非 left/right |

## 7. 验收标准（DoD）

- [ ] 单条通知：38dp 图标 + app 名/时间行 + 标题（≤2 行）+ 正文（≤4 行），文字列与图标右侧对齐，
      无重复小图标、无 46dp 空 header 行；截图与 §3 规范逐项对照。
- [ ] 同 app 2 条分组：组头一次图标+app 名+时间，子行无图标、发丝线左缩到文字列；
      折叠/展开、计数正确。
- [ ] 媒体通知仍显示 MediaCard 自定义卡（未被通知条样式覆盖）。
- [ ] 点击/长按/滑动删除/展开动画 全部与原版一致（录屏或逐帧截图对比）。
- [ ] 深色模式、大字号（1.3）、RTL 无破版。
- [ ] logcat 无异常；`installed feature notify_row`；关闭 Feature 后完全恢复原版。
- [ ] `out/DEVLOG.md` + `vivo-OriginUI/docs/04-notification-row.md` 记录完整逆向与验证。

## 8. 需用户确认（可先按推荐默认，见面再定）

1. 展开态是否也要 iOS 化？**推荐：阶段 2 再做，先只改收起态。**
2. 分组折叠态显示几条？**推荐：3 条 + "还有 N 条"。**
3. 时间格式：相对（刚刚/N 分钟前）还是绝对（HH:mm）？**推荐：<1 分钟"刚刚"、<1 小时"N 分钟前"、当天 HH:mm、跨天 M月d日。**
4. 图标形状：圆角方（iOS 观感）还是圆形（OS5 观感）？**推荐：圆角方 10dp。**
5. 是否保留 OS5 的右上角展开箭头？**推荐：保留（可点击展开），但缩小并降低对比度。**

---

## 9. 实现进展（2026-10-26 14:23，代码级）

> 用户后续调整：**同应用多条不再做成应用类别卡，而是每条独立堆叠成卡**；展开箭头导致掉回
> OS5 展开布局，因此**去掉箭头/禁用展开**；卡片外壳保留 OS5 材质，但提供 `stack_metrics`
> （iOS 16/8/20 默认 / OS5 原生）。详细逆向/实现/校验见 `vivo-OriginUI/docs/04-notification-row.md`。

- [x] 单条收起态：38dp 图标（x=16、垂直居中）、app/title/body 同列 66dp、时间右端 16dp、
      标题 15sp≤2 行、正文 14sp≤4 行、无 18dp 小图标/46dp header、卡片底部空高收敛。
- [x] 同应用多条（15:31 终稿）：`isGroup=false` + `getGroupKey=null`（媒体豁免）+ 全量移除非媒体
      组摘要 + GroupEntry rawChildren 扁平化；独立成卡、无组头/组容器、无空白摘要卡。
- [x] 媒体：媒体白名单不拆组；MediaCardFeature 继续接管（restyling 日志恢复）。
- [x] messaging/conversation：新增 `IosNotificationRow` overlay，保留原模板绑定，iOS 行覆盖。
- [x] 交互：整行点击（含文本区）→ 打开应用；长按走原生 `doLongClickCallback`；右滑删除
      overlay 行成功。长按菜单 adb 注入在该 ROM 不触发，待真机手指验证。
- [x] 深色、1.3 字号、真实 RTL：最终构建复验 PASS（night 18 行 / font13 17 行 / rtl 19 行）。
- [x] 配置变化参数回退 bug：vivo `reinitView()` 会重写堆叠参数 → 追加 hook
      `reinitView/updateCornerRadius/onSystemFilletChanged`；字号 1.3 下卡片保持 48/48/24。
- [x] overlay 复用残留 bug：同 key 聊天→普通样式时 `dropOverlay` 清理（`overlay dropped`+`overlays=0`）。
- [x] 代码级校验器：`work/verify_notify.py`（新增静止门槛 + overlay 可见即验证）；证据
      `work/os5-ref/notify-verify-20261026-{final,night,font13,rtl}.log` 全 PASS。
- [x] pre-draw 守卫性能：缓存目标视图数组，每帧仅回放 alpha/clickable/enabled；overlay 字号按需。
- [ ] heads-up：ROM 用 vivo 自绘胶囊，标准 `setHeadsUpChild` 真机不可复现（hook 已挂，待复验）。
- [ ] custom view、卡片 iOS 材质、自定义展开（动作/快捷回复）仍待（卡片外壳为用户先前决定保留）。
- [ ] OS5 finalize filters / 锁屏隐私过滤保持原生策略（不绕过 DND/keyguard）。

### 2026-10-26 20:45 追加：OS6 真·折叠堆叠（进行中，交接）
- 用户选定“真·折叠堆叠”并以 X200 Pro(OS6) 实测为准；取证与几何见
  `vivo-OriginUI/docs/05-os6-notification-stack.md`，取证件在 `G:\os6-ref`。
- 已实现 `group_mode=ios_stack`（保留原生分组 + 组摘要自绘堆叠卡：下方露边 14dp/层、内缩 13dp/层、
  图标右上角条数徽标）与 `verify.stack` 断言；几何已达标。
- **未完成（详见交接文档 `out/HANDOFF-1026-OS6-Stack.md`）**：
  - [ ] 保留 GroupEntry 触发 vivo 图标控制器 NPE（SystemUI 重启）；基类守卫已写未验证。
  - [ ] 组被系统自动展开；setter 层拦截已写未验证，堆叠卡点击展开尚未接。
  - [ ] 夜间卡色重采样；展开态仍为 OS5 组卡；OS6 对齐项（36dp 图标/60dp 列/7dp 间距/无 header）待定。
