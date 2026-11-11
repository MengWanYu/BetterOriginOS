# 04 · 通知条 iOS 化（OriginOS 5 / SystemUI 15.0.5.241）

> 状态：**已实现并代码级验证**（日间/夜间/1.3 字号/RTL；普通模板 + messaging overlay）。
> 相关：`out/TASK-NOTIFY-ROW-1026.md`、`out/DEVLOG.md` 2026-10-26 各条、`module/src/com/betteroriginos/feature/notify/`。
> 本文记录逆向链路、实现方案、校验方法；截图只作参考，验收以 `work/verify_notify.py` 为准。

## 1. 视图层级与绑定链（已逆向）

```
ExpandableNotificationRow (id/expandableNotificationRow)
├── NotificationBackgroundView (id/backgroundNormal)     ← OS5 卡片底
├── NotificationContentView (id/expanded)                ← 收起/展开/悬浮内容的宿主
│   └── 模板 root（vigour framework-res 布局，tag = base/big/bigText/inbox/bigPicture/messaging）
├── NotificationContentView (id/expandedPublic)
├── Button (id/veto)
└── ViewStub ... children/guts/menu/headsup
```

- 模板：`framework-res` 的 `vigour_notification_template_*_vos.xml`；header
  `android.view.NotificationHeaderView`（46dp），内容列 `marquee_forever/matches/map`，
  左右边距 16dp；header 内小图标 `CachingIconView @android:id/icon` 18dp。
- 绑定：`NotificationInflater` 生成模板 → `NotificationContentView.setContractedChild(view)` /
  `setExpandedChild(view)`；`NotificationViewWrapper.wrap()` 建立 wrapper（header/title/text/time 引用）。
  `setNotificationWhen()` 通过 `mView.findViewById(R.id.time)` 更新时间。
- 高度：`NotificationContentView.onMeasure` 把 expanded/contracted/headsup 全部测量并取 max；
  行高度来自 `ExpandableNotificationRow.getIntrinsicHeight/getMinHeight`。若不收敛，收起卡片会预留
  展开高度（实测多 42dp）。
- 分组：`ShadeListBuilder.groupNotifs` 按 `StatusBarNotification.isGroup()/getGroupKey()` 建立
  `GroupEntry`；`filterNotifs` 的 finalize filters 再过滤；`GroupEntry.getChildren()` 只返回已附加子项，
  `getRawChildren()` 才是全量。
- 手势：`NotificationStackScrollLayout` 的 `NotificationSwipeHelper` 负责滑动/长按；
  `NotificationHeaderView` 自带 `HeaderTouchListener`（展开按钮/header 点击）。
- 媒体：`NotificationMediaTemplateViewWrapper` / `MediaNotificationView` 由 MediaCardFeature 接管。

## 2. 收起态原地改造（base/big/bigText/inbox/bigPicture）

在 `NotifyRowFeature` hook `setContractedChild`（以及 `setExpandedChild`、`setHeights`、`onMeasure`、
`calculateVisibleType`）后原地改布局，**不替换 child、不破坏 wrapper 绑定**：

| 元素 | 规格 |
|---|---|
| 大图标 | 38dp，圆角 10dp，左侧 16dp，垂直居中（`CENTER_VERTICAL`） |
| header | 高 34dp；原 18dp 小图标/`left_icon`/展开箭头 **alpha=0**、不可点击/无障碍 |
| 列 | app名/时间/标题/正文统一起点 66dp（16+38+12） |
| app名/时间 | 12sp secondary；时间移到 header 右侧，右端 16dp |
| 标题 | 15sp `sans-serif-medium` primary，≤2 行 |
| 正文 | 14sp regular secondary，≤4 行，标题下 2dp |
| 右/底 | 文本右端 16dp；`/` 分隔 alpha=0 |
| 高度 | `mVisibleType=0` 时 content 高度收敛到 contracted；`setHeights` 收起上限 200dp |

- `setExpandedChild` 也改造（`big` 模板已纳入支持）；但实测展开模板排版差异大，且用户不需要
  OS5 展开，最终 **隐藏箭头 + 去掉 header touch + `setUserExpanded(true)` 拦截**，整行点击走 row。
- 小图标/箭头/compound drawable 由 pre-draw 守卫持续压住；**只改 alpha/clickable，不改 visibility**
  （改 visibility 会与 OS5 每帧抢状态 → requestLayout → 吞滑动/长按手势）。

## 3. messaging / conversation overlay

`tag=messaging`（root=`MessagingLayout`）与 `NotificationConversationTemplateViewWrapper` 无法原地
按列重排，改为 **覆盖行**：

1. 保留原模板与 wrapper（继续绑定/更新）。
2. 在 `NotificationContentView` 上加 `IosNotificationRow`（View 版，同 §2 规格）。
3. 原 root `alpha=0`（不改 visibility，避免布局抖动），overlay 在上层；pre-draw 守卫保持。
4. 文案：`android.conversationTitle` / `android.messages`（Person.sender + text + selfDisplayName），
   取最近 incoming 消息；时间用 `DateUtils` 相对格式并每分钟刷新；夜间/字号由守卫
   `applyTheme/applyTextScale`。
5. 高度：`onMeasure` 测 overlay，content 与 contracted root 同步为 overlay 高度；行/卡片高度一致。
6. 交互：整行 `OnClickListener → row.performClick()`（点文本区即打开应用）；长按走
   `row.doLongClickCallback(x,y)`（原生通知菜单入口；`performLongClick()` 在本 ROM 上无绑定）。
   横向滑动（OS5 为**右滑**删除）由 NSS 手势层拦截，overlay 不阻断。
7. 生命周期：`dropOverlay()` 在「行复用为非聊天通知 / 功能关闭 / sbn 缺失」时移除 overlay，
   避免同一 key 从 MessagingStyle 换成普通样式后旧 iOS 行残留叠加。

## 4. 拆组（同应用多条独立成卡）+ 媒体豁免

- `Application.onCreate` 即置 `sUngroupEnabled`（必须在第一批 pipeline 前，否则先建 GroupEntry）。
- hook `StatusBarNotification.isGroup()` → false、`getGroupKey()` → null（**媒体白名单保持原样**）：
  让应用自定义分组与系统 `ranker_group` 自动分组都不再成组。
- `ShadeListBuilder.filterNotifs` 后：删除全部非媒体 group summary；`GroupEntry` 用
  `getRawChildren()` 扁平化并 `setParent(ROOT_ENTRY)`；媒体 summary/group 不删/不扁平化。
  （保留 ranker 组摘要会渲染出一张仅剩“Shell + 时间”的空白卡，必须一并移除；之前系统通知
  “消失”实为锁屏态 `VivoKeyguardCoordinator.vivoNotifFilter` 的隐私过滤，解锁通知中心可见，属原生行为。）
- 媒体白名单 `isMediaSbn`：`CATEGORY_TRANSPORT` / `EXTRA_MEDIA_SESSION` /
  `com.android.bbkmusic` / `com.vivo.musicwidgetmix`；媒体继续走 MediaCardFeature。
- 结果：普通同应用多条 = 每条独立 ExpandableNotificationRow；无组头/组容器；系统通知照常显示；媒体卡不丢。

## 5. 堆叠参数（选项 `stack_metrics`，默认 iOS）

`NotificationStackScrollLayout` 字段：

| 字段 | iOS | OS5 |
|---|---|---|
| `mSidePaddings` / `mMinimumPaddings` | 16dp | 20dp |
| `mPaddingBetweenElements`（stack 与 `StackScrollAlgorithm` 各自的副本） | 8dp | 7dp |
| `mCornerRadius` | 20dp | 18dp |

选项在功能详情页可切 `iOS 堆叠 / OS5 原生`（切换后需重启 SystemUI）。实测卡片左右 48px、
行距 24px、圆角字段 60px（density=3）。

注意：vivo 在字号/夜间/语言等配置变化时走 `reinitView()` 重读 OS5 资源并覆盖上述字段。
hook 覆盖 `构造器 / updateSidePadding / reinitView / updateCornerRadius / onSystemFilletChanged`
（另 hook `StackScrollAlgorithm.initView` 写 float 间距副本），配置切换后参数不丢。

## 6. 代码级验证（不靠截图）

```bash
adb install -r module/build/BetterOriginOS.apk
adb shell settings put global betteroriginos_feature_notify_row 1
adb shell settings put global betteroriginos_notify_verify 1
adb shell 'kill $(pidof com.android.systemui)'
# 唤醒/解锁/下拉通知栏
adb shell settings put global betteroriginos_notify_reverify 1   # 触发所有已改造行重验
python work/verify_notify.py
```

`work/verify_notify.py` 解析 SystemUI 内 `notify_row.verify` / `notify_row.verify.overlay` /
`notify_row.stack` 日志，按 px/dp 断言：

- icon 38dp × 38dp、x=16dp、中心偏差 ≤2dp；app/title/body 起点 66dp；time/title/body 末端 16dp；
- header 34dp；标题 15sp medium ≤2 行；正文 14sp ≤4 行；应用名 12sp medium 灰字；正文与标题同为
  primary（iOS label 色）；时间灰字（夜/日两套色值）；
- 卡片 16dp 边距、8dp 行距、20dp 圆角；`rowH=contractedH`；`small=0`、`expand=0`、`groupChild=0`、
  `stackChildContainers=0`；overlay 行 `overlayH=rowH=contentH`、`origAlpha=0`。
- 归一化 start/end 字段支持 RTL（`rtl=1`）。

验证只在行“静止”（`scaleX/Y == 1`）时出数，`translation` 是 NSS 正常堆叠定位不作判定；
overlay 待验证挂在 pre-draw：行首次真正布局可见即出数（`OVERLAY_VERIFY_PENDING`），
滚动/滑动/惰性装载不会再产生假失败。

当前证据（最终构建 16:07）：

- `work/os5-ref/notify-verify-20261026-final.log`：14 普通 + 4 overlay 行 `PASS`（日间/LTR/1.0）。
- `notify-verify-20261026-night.log`：18 行 `night=1`、正文 #ffffffff `PASS`。
- `notify-verify-20261026-font13.log`：17 行 `fontScale=1.3` `PASS`（验证 reinitView 参数回退修复）。
- `notify-verify-20261026-rtl.log`：19 行 `rtl=1` `PASS`（SystemUI app locale=ar）。
- `notify-verify-20261026.log/.txt`、`notify-verify-overlay.log/.txt`：早期基线（保留）。
- 行为级：同 groupKey 两条独立成卡且无摘要卡；点聊天行文本打开应用；右滑删除聊天行成功；
  同 key 聊天→普通样式切换时 overlay 被清理（`overlay dropped` + `overlays=0`）。

## 7. 已知限制 / 后续

- heads-up：该 ROM 的悬浮通知是 vivo 自绘胶囊（如“交管12123”），不是标准
  `NotificationContentView` heads-up；`setHeadsUpChild` 已挂同一套 restyle（mode=2）但
  真机不可复现（调试高 importance 通道在前台/冻结策略下也不弹），维持“待复验”，不作宣称。
- custom view（非标准 RemoteViews）不改造；媒体由 MediaCardFeature 接管。
- 展开态/动作不可用（按 iOS 交互选择禁用箭头）；如需 iOS 展开需另做。
- 卡片背景仍是 OS5 材质（逆向的 iOS 材质/模糊未替换）。
- OS5 finalize filters / 锁屏隐私过滤（`VivoKeyguardCoordinator`）保持原生，不强行绕过 DND/keyguard。
- 长按菜单：adb 长按注入在该 ROM 不触发（功能开关均无菜单），代码已走原生
  `doLongClickCallback`，需真机手指复验。
- `stack_metrics` 切换需重启 SystemUI。
- pre-draw 守卫已缓存目标视图（每帧只回放 alpha/clickable/enabled），overlay 主题/字号已按需应用。
