# OS3 hook 目标可达性核对（源码 -> OS3 dexdump）

统计：涉及类 36，缺失类 12，缺失方法 0

## 缺失类
- ❌ `android.app.Application` 需要方法: onCreate
- ❌ `android.database.sqlite.SQLiteQueryBuilder` 需要方法: （仅引用）
- ❌ `android.service.notification.StatusBarNotification` 需要方法: isGroup
- ❌ `com.android.systemui.dependencies.Dependency` 需要方法: （仅引用）
- ❌ `com.android.systemui.statusbar.notification.row` 需要方法: （仅引用）
- ❌ `com.android.systemui.statusbar.notification.row.wrapper.` 需要方法: onContentUpdated
- ❌ `com.android.systemui.statusbar.notification.stack` 需要方法: onLayout
- ❌ `com.android.systemui.statusbar.phone.LegacyNotificationIconAreaControllerImpl` 需要方法: （仅引用）
- ❌ `com.vivo.musicwidgetmix.IMusicWidgetMixService` 需要方法: （仅引用）
- ❌ `com.vivo.musicwidgetmix.service.MusicWidgetMixService` 需要方法: （仅引用）
- ❌ `com.vivo.systemui.statusbar.phone.VivoLegacyNotificationIconAreaControllerImpl` 需要方法: （仅引用）
- ℹ️ `com.vivo.systemui.statusbar.ui.NotificationClearAllImageView` 按设计缺失：OS5 原生浮动清除按钮（A15 三个 dex 命中 6 / A13 全 0）；命中时模块整段不补挂，避免两枚重叠
- ❌ `com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView` 需要方法: （仅引用）

## 类存在但方法缺失

## 完全可用的类
- ✅ `com.android.systemui.SystemUIApplication`: onCreate
- ✅ `com.android.systemui.statusbar.NotificationGroupingUtil`: sanitizeTopLine
- ✅ `com.android.systemui.statusbar.NotificationShelf`: bind, changeShelfVisibility, onConfigurationChanged, onFinishInflate, onLayout, setDismissVisible, updateAppearance
- ✅ `com.android.systemui.statusbar.notification.collection.ShadeListBuilder`: applyFilters, filterNotifs
- ✅ `com.android.systemui.statusbar.notification.row.ActivatableNotificationView`: onFinishInflate
- ✅ `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow`: getIntrinsicHeight, onExpansionChanged, onMeasure, setBottomRoundness, setTopRoundness
- ✅ `com.android.systemui.statusbar.notification.row.ExpandableOutlineView`: getRoundedRectPath, initDimens, onSystemFilletChanged
- ✅ `com.android.systemui.statusbar.notification.row.NotificationBackgroundView`: onDraw, setRadius
- ✅ `com.android.systemui.statusbar.notification.row.NotificationContentView`: calculateVisibleType, onMeasure, setContractedChild, setExpandedChild, setHeadsUpChild, setHeights
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationBigPictureTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationBigTextTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationCustomViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationDecoratedCustomViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationMessagingTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.row.wrapper.NotificationTemplateViewWrapper`: onContentUpdated
- ✅ `com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout`: <init>, dispatchDraw, onSystemFilletChanged, reinitView, setIsExpanded, setQsFullScreen, updateCornerRadius, updateSidePadding
- ✅ `com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm`: initView

## 仅类名被引用（无方法级 hook 记录）
- ℹ️ `com.android.systemui.Dependency`
- ℹ️ `com.android.systemui.statusbar.notification.collection.GroupEntry`
- ℹ️ `com.android.systemui.statusbar.phone.NotificationIconAreaController`
- ℹ️ `com.vivo.systemui.statusbar.phone.VivoNotificationIconAreaController`
- ℹ️ `com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker`

## 反射字段（33 个）——需按持有类逐个确认

| 字段 | 在下列 OS3 类中出现 |
|---|---|
| `mBackground` | `NotificationBackgroundView` |
| `mBackgroundNormal` | `ActivatableNotificationView` |
| `mBackgroundTop` | `ExpandableOutlineView`, `NotificationBackgroundView` |
| `mBottomAmountClips` | `NotificationBackgroundView` |
| `mBottomIsRounded` | `NotificationBackgroundView` |
| `mChildrenExpanded` | `ExpandableNotificationRow` |
| `mClipBottomAmount` | `NotificationBackgroundView`, `NotificationContentView` |
| `mClipTopAmount` | `NotificationBackgroundView`, `NotificationContentView` |
| `mContainingNotification` | `NotificationContentView` |
| `mContractedChild` | `NotificationContentView` |
| `mContractedWrapper` | `NotificationContentView` |
| `mCornerRadii` | `NotificationBackgroundView` |
| `mCornerRadius` | `NotificationStackScrollLayout` |
| `mDatabaseHelper` | （本清单涉及的类里未命中） |
| `mDismissAllNotification` | `NotificationShelf` |
| `mDistanceToTopRoundness` | `ExpandableOutlineView`, `NotificationBackgroundView` |
| `mDontModifyCorners` | `NotificationBackgroundView` |
| `mExpandAnimationRunning` | `ExpandableNotificationRow`, `NotificationBackgroundView` |
| `mExpandedChild` | `NotificationContentView` |
| `mGroupExpansionManager` | `ExpandableNotificationRow`, `NotificationStackScrollLayout` |
| `mHeadsUpChild` | `NotificationContentView` |
| `mIsHeadsUp` | `ExpandableNotificationRow`, `NotificationContentView` |
| `mOutlineRadius` | `ExpandableOutlineView` |
| `mPaddingBetweenElements` | `NotificationShelf`, `NotificationStackScrollLayout`, `StackScrollAlgorithm` |
| `mRow` | `NotificationGroupingUtil` |
| `mShelf` | `NotificationStackScrollLayout` |
| `mSidePaddings` | `NotificationStackScrollLayout` |
| `mStackScrollAlgorithm` | `NotificationStackScrollLayout` |
| `mTopAmountRounded` | `ExpandableOutlineView`, `NotificationBackgroundView` |
| `mTopRoundness` | `ExpandableOutlineView` |
| `mView` | （本清单涉及的类里未命中） |
| `mVisibleType` | `NotificationContentView` |
| `yTranslation` | （本清单涉及的类里未命中） |

## callMethod 目标（49 个）

- `adjustHeightForChildren` → （本清单涉及的类里未命中）
- `appendWhereStandalone` → （本清单涉及的类里未命中）
- `applyRoundness` → ActivatableNotificationView, ExpandableNotificationRow, ExpandableOutlineView
- `canViewBeDismissed` → ExpandableNotificationRow
- `cancelNotificationWithTag` → （本清单涉及的类里未命中）
- `doLongClickCallback` → ExpandableNotificationRow
- `expandNotification` → ExpandableNotificationRow
- `getActualHeight` → NotificationBackgroundView
- `getActualWidth` → NotificationBackgroundView, NotificationShelf
- `getAttachedChildren` → ExpandableNotificationRow
- `getButton` → （本清单涉及的类里未命中）
- `getChildren` → GroupEntry
- `getChildrenContainer` → ExpandableNotificationRow
- `getContainingNotification` → （本清单涉及的类里未命中）
- `getEditText` → （本清单涉及的类里未命中）
- `getEntry` → ExpandableNotificationRow
- `getExpansionFraction` → （本清单涉及的类里未命中）
- `getExtraHeight` → ExpandableNotificationRow
- `getFilletLevel` → VivoColorAndCornerPicker
- `getIntrinsicHeight` → ExpandableNotificationRow, NotificationStackScrollLayout
- `getKey` → （本清单涉及的类里未命中）
- `getName` → （本清单涉及的类里未命中）
- `getNotificationChildCount` → （本清单涉及的类里未命中）
- `getParent` → （本清单涉及的类里未命中）
- `getRawChildren` → GroupEntry
- `getSbn` → （本清单涉及的类里未命中）
- `getService` → （本清单涉及的类里未命中）
- `getSummary` → GroupEntry
- `getViewState` → （本清单涉及的类里未命中）
- `hasUserChangedGroupExpansion` → ExpandableNotificationRow
- `invalidateSelf` → （本清单涉及的类里未命中）
- `isClearable` → （本清单涉及的类里未命中）
- `isExpanded` → ExpandableNotificationRow, NotificationStackScrollLayout
- `isGroupExpanded` → ExpandableNotificationRow, NotificationContentView
- `isQsFullScreen` → NotificationStackScrollLayout
- `notifyHeightChanged` → ExpandableNotificationRow
- `runWithoutTransaction` → （本清单涉及的类里未命中）
- `setChildrenExpanded` → ExpandableNotificationRow
- `setClickable` → （本清单涉及的类里未命中）
- `setCorner` → （本清单涉及的类里未命中）
- `setGroupExpanded` → （本清单涉及的类里未命中）
- `setMeasuredDimension` → （本清单涉及的类里未命中）
- `setParent` → （本清单涉及的类里未命中）
- `setRadius` → NotificationBackgroundView
- `setTopRoundness` → ExpandableNotificationRow, ExpandableOutlineView
- `setUserExpanded` → ExpandableNotificationRow
- `shouldFilterOut` → （本清单涉及的类里未命中）
- `updateBackgroundRadii` → NotificationBackgroundView
- `updateLimits` → ExpandableNotificationRow