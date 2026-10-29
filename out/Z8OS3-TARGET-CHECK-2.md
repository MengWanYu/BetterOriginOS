# OS3 hook 目标可达性核对（源码 -> OS3 dexdump）

统计：涉及类 26，缺失类 7，缺失方法 1

## 缺失类
- ❌ `android.app.Application` 需要方法: onCreate
- ❌ `android.service.notification.StatusBarNotification` 需要方法: isGroup
- ❌ `com.android.systemui.statusbar.notification.row.wrapper.` 需要方法: onContentUpdated
- ❌ `com.android.systemui.statusbar.notification.stack` 需要方法: onLayout, setShouldShowShelfOnly
- ❌ `com.android.systemui.statusbar.phone.LegacyNotificationIconAreaControllerImpl` 需要方法: （仅引用）
- ❌ `com.vivo.musicwidgetmix.service.MusicWidgetMixService` 需要方法: （仅引用）
- ❌ `com.vivo.systemui.statusbar.phone.VivoLegacyNotificationIconAreaControllerImpl` 需要方法: （仅引用）

## 类存在但方法缺失
- ⚠️ `com.android.systemui.statusbar.NotificationShelf`: setShouldShowShelfOnly

## 完全可用的类
- ✅ `com.android.systemui.SystemUIApplication`: onCreate
- ✅ `com.android.systemui.statusbar.NotificationGroupingUtil`: sanitizeTopLine
- ✅ `com.android.systemui.statusbar.notification.collection.ShadeListBuilder`: applyFilters, filterNotifs
- ✅ `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow`: getIntrinsicHeight, onMeasure, setBottomRoundness, setTopRoundness
- ✅ `com.android.systemui.statusbar.notification.row.NotificationContentView`: calculateVisibleType, onMeasure, setContractedChild, setExpandedChild, setHeadsUpChild, setHeights
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationBigPictureTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationBigTextTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationCustomViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationDecoratedCustomViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationMessagingTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout`: <init>, dispatchDraw, onSystemFilletChanged, reinitView, updateCornerRadius, updateSidePadding
- ✅ `com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm`: initView

## 仅类名被引用（无方法级 hook 记录）
- ℹ️ `com.android.systemui.statusbar.notification.collection.GroupEntry`
- ℹ️ `com.android.systemui.statusbar.phone.NotificationIconAreaController`
- ℹ️ `com.vivo.musicwidgetmix.IMusicWidgetMixService`
- ℹ️ `com.vivo.systemui.statusbar.phone.VivoNotificationIconAreaController`

## 反射字段（14 个）——需按持有类逐个确认

| 字段 | 在下列 OS3 类中出现 |
|---|---|
| `mContainingNotification` | `NotificationContentView` |
| `mContractedChild` | `NotificationContentView` |
| `mContractedWrapper` | `NotificationContentView` |
| `mCornerRadius` | `NotificationStackScrollLayout` |
| `mExpandedChild` | `NotificationContentView` |
| `mGroupExpansionManager` | `ExpandableNotificationRow`, `NotificationStackScrollLayout` |
| `mHeadsUpChild` | `NotificationContentView` |
| `mIsHeadsUp` | `ExpandableNotificationRow`, `NotificationContentView` |
| `mPaddingBetweenElements` | `NotificationShelf`, `NotificationStackScrollLayout`, `StackScrollAlgorithm` |
| `mRow` | `NotificationGroupingUtil` |
| `mSidePaddings` | `NotificationStackScrollLayout` |
| `mStackScrollAlgorithm` | `NotificationStackScrollLayout` |
| `mView` | （本清单涉及的类里未命中） |
| `mVisibleType` | `NotificationContentView` |

## callMethod 目标（31 个）

- `adjustHeightForChildren` → （本清单涉及的类里未命中）
- `applyRoundness` → ExpandableNotificationRow
- `canViewBeDismissed` → ExpandableNotificationRow
- `doLongClickCallback` → ExpandableNotificationRow
- `getAttachedChildren` → ExpandableNotificationRow
- `getChildren` → GroupEntry
- `getChildrenContainer` → ExpandableNotificationRow
- `getEntry` → ExpandableNotificationRow
- `getExtraHeight` → ExpandableNotificationRow
- `getIntrinsicHeight` → ExpandableNotificationRow, NotificationStackScrollLayout
- `getKey` → （本清单涉及的类里未命中）
- `getName` → （本清单涉及的类里未命中）
- `getNotificationChildCount` → （本清单涉及的类里未命中）
- `getParent` → （本清单涉及的类里未命中）
- `getRawChildren` → GroupEntry
- `getSbn` → （本清单涉及的类里未命中）
- `getSummary` → GroupEntry
- `hasUserChangedGroupExpansion` → ExpandableNotificationRow
- `isClearable` → （本清单涉及的类里未命中）
- `isGroupExpanded` → ExpandableNotificationRow, NotificationContentView
- `notifyHeightChanged` → ExpandableNotificationRow
- `setBottomRoundness` → ExpandableNotificationRow
- `setChildrenExpanded` → ExpandableNotificationRow
- `setGroupExpanded` → （本清单涉及的类里未命中）
- `setMeasuredDimension` → （本清单涉及的类里未命中）
- `setParent` → （本清单涉及的类里未命中）
- `setRadius` → （本清单涉及的类里未命中）
- `setTopRoundness` → ExpandableNotificationRow
- `setUserExpanded` → ExpandableNotificationRow
- `shouldFilterOut` → （本清单涉及的类里未命中）
- `updateLimits` → ExpandableNotificationRow