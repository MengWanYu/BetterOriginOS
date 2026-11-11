# 03 · 控件参数速查（逆向自 OriginUI 源码 + 真机校准）

> 位置基准：`ref/originui-fm-src/com/originui/widget/`（jadx 反编译）。
> 视觉参数：真机截图校准（ref 工程里 R.dimen 为符号名，精确 dp 以本表为准）。

## OS5 开关（components/switches/VMoveBoolButton，2840 行）

**真实视觉（rom15_0，与 Android Material 开关完全不同）**：
- OFF 态：**空心**——轨道是 2.5dp 描边胶囊（高 17.5dp，色 36% 灰），thumb 是 3dp 灰环
  内部透明（外径 20dp，**高于轨道**，突出轨道上下）。
- ON 态：轨道变 13dp 高实心浅蓝条（#330F77FF，20% 蓝），thumb 变白底（80% 白填充）+
  3dp 蓝环（#0F77FF）。
- 切换时：蓝条从左生长（宽 = max(P,0.5)×38dp，alpha = P×2）、描边条向右滑出淡出（alpha=1-P）、
  thumb 半径 8.5→10dp 生长、中心 12.5→32dp。
- 夜间：描边 #75464646 / 蓝条 #4D2785FF / 环 #464646→#2785FF / thumb 黑→80% 黑。

| 参数 | 值 | 来源（settings-dec res + d0() 绘制逻辑） |
|---|---|---|
| 控件 | 48×28dp，左 padding 4dp | bg_width 40 + padding_left 4 |
| 填充条 | 高 13dp（moveboolbutton_bg_on_height），宽 max(P,0.5)×38dp | |
| 描边条 | 高 17.5dp、线宽 2.5dp（bg_off_height/bg_off_line_width rom15_0） | |
| thumb | 半径 8.5→10dp（ring_off_r/ring_on_r），环线宽 3dp（ring_line_width） | |
| 动画架构 | 双 ValueAnimator：C0=0→1 / B0=1→0，D0=250ms，默认插值器 u0=PathInterpolator(0.2,0.4,0.2,1) | o0() 1583-1592 / 2790-2792 |
| 点击切换 | 目标开→C0.start()、关→B0.start()（不换插值器，无过冲） | performClick L17-L24 |
| 拖动翻转 | 跨 0.6/0.4 → setInterpolator(Overshoot 1.8) + setCurrentPlayTime(J0×250) 从当前位置续播（之后不恢复默认插值器） | ACTION_MOVE L86/L88 |
| 回调时机 | onCheckedChanged 在**动画结束时**（h0()），非点击瞬间 | h0() |
| 触感 | vibratorPro(113,-1,-1)（H0()），受系统 haptic_feedback_enabled 控制 | H0() |
| 拖动翻转 | 进度 ≥0.6 翻开 / ≤0.4 回弹 | ACTION_MOVE 分支 |
| 颜色 | originui_vcomponents_moveboolbutton_*_rom15_0（亮暗两套，见上） | colors.xml |
| 声音/震动 | playSoundEffect(0) + VIRTUAL_KEY | ACTION_UP 分支 |

复刻：`lib/src/com/originui/replica/OriginSwitch.java`（三层绘制 1:1，真机对照 crop-switch2.png 通过；
动画架构 1:1——真机验证：点击路径帧序列 907→916→917→917（5x 慢放，平滑无过冲=PathInterpolator 曲线 ✓），
拖动路径动画起点 J0=-0.107（Overshoot 过冲区间内）→ 终点精确 0.0 ✓，全程 275ms=D0+帧延迟 ✓，
写链路在动画结束回调触发 ✓）。

**实现偏差注记**：源码顺序 setCurrentPlayTime→start() 在 AOSP 上会被 start() 重置位置，
复刻改为 start()→setCurrentPlayTime()（vivo framework 可能容忍原顺序，行为等价：从 J0 续播）。

## 滑杆（originui seekbar，VivoSeekBar=AbsSeekBar 换皮，视觉在 drawable）

**真实视觉（rom15_0，像素级验证 vs crop-slider.png）**：
- thumb = **18dp 蓝盘（#0F77FF）+ 9dp 白心**（vector 两个圆，无阴影）；夜间白心变黑心。
  与开关同设计语言（圆盘挖心），直径远大于轨道。
- 轨道 4dp 高圆角：已播放 #0F77FF、未播放 #0F000000（白底呈 #F0F0F0）/ 夜 #33FFFFFF。
- **拖动数值气泡**（VPopupTextView）：≥50×22dp 胶囊、padding 10/4、背景 #0F77FF、白字 12sp
  medium，浮在 thumb 上方 12dp，水平跟随。
- 行高 48dp 触控；按下即跳转（AbsSeekBar trackTouchEvent）。

复刻：`OriginSlider.java`（含 setToastSuffix 后缀如 %/°）。

## 列表行（listitem/VListContent + OS5 设置）

| 参数 | 值 |
|---|---|
| 行内边距 | 16dp 横 / 14dp 纵 |
| 标题 | 15sp medium text_primary |
| 副标题 | 12sp regular text_secondary，上距 2dp |
| 分组 | 行级圆角 12dp（top/mid/bot drawable），组间 8dp |
| 分隔线 | 1dp divider 色，左缩 70dp |
| ripple | 33% 黑，圆角 mask |
| 图标 | 24dp（行级小图标）；应用行 40dp |

复刻：`OriginRows.java`。

## 输入框（edittext/VEditText，517 行）

- 背景：圆角 12dp 卡（bgCard），聚焦时描边 accent（ROM15 行为：描边 1dp）。
- 文字 15sp；hint text_tertiary。
- 清除按钮：右侧 24dp 灰叉，输入非空显示。
- 错误态：描边红 + 底部 12sp 红字（errorFileNameTooLong 场景）。
- **无 maxLength 内建过滤**（文件管理 bug 由此而来，VEditText 无 InputFilter）。

## 搜索框（search/VSearchView，3848 行）

- 顶栏搜索态：替换标题，背景圆角胶囊（12dp），左侧放大镜 20dp + 右侧清除。
- 聚焦展开：从 icon 位展开到全宽（150ms）。
- VSearchView2 为第二代（独立圆角卡样式）。

## 对话框（dialog/VCustomRoundRectLayout）

| 参数 | 值 |
|---|---|
| 卡 | 居中，宽 300dp（wrap 上限），圆角 16dp，bgCard |
| 标题 | 16sp bold text_primary |
| 说明 | 13sp regular text_secondary，行距 +2dp |
| 按钮 | 文字按钮 14sp：取消 text_secondary regular / 确认 accent bold，右下排布 |
| scrim | 32% 黑 / 40% 黑(暗) |
| 列表项对话框 | VCustomCheckedTextView/RadioButtonTextView，勾选 accent |

复刻：`OriginOverlays.OriginDialog`（已验证）。

## 底部弹层（sheet/VBottomSheet + VSheetHandleBar）

| 参数 | 值 |
|---|---|
| 容器 | 顶部圆角 24dp，SHEET_DIALOG 材质（204/254），兜底 #F7F7FA/#252525 |
| 把手 | 居中 32×4dp 圆角 2dp，divider 色 |
| 行为 | VBottomSheetBehavior（嵌套滚动拖拽、半展开态、隐藏态） |
| 按钮 | 胶囊主按钮 44dp accent |

复刻：`OriginOverlays.OriginSheet`（已验证；拖拽手势未实现）。

## 行内下拉（popup/VListPopupWindow）

| 参数 | 值 |
|---|---|
| 背景 | 12dp 圆角卡 bgCard |
| 行 | 预览图 36dp + 标签 15sp + 勾选（accent）；padding 14dp |
| 宽 | 锚行宽，≥260dp；锚下方 6dp |
| 选中 | 勾选符 + accent 色 |

复刻：`OriginOverlays.OriginListPopup`（已验证）。

## Toast（toast/VToastView）

- 底部居中悬浮胶囊，TOAST 材质（209/259）模糊底；文字 13sp。
- demo 的 VivoToast 为自绘近似（未走 setMaterial，窗口类型限制）。

## 徽标/角标（vbadgedrawable）

- 圆点 6dp / 数字徽标高 16dp 圆角 8dp，位置 = 图标右上角偏移（with_text/without_text 两档）。

## Tab（tabs/VTabLayout / VTabLayoutWithIcon）

- 文字 tab：15sp medium，选中 accent + 底部 3dp 圆角指示条（32dp 宽居中）。
- 图标 tab：24dp 图标 + 12sp 标签。
- 指示条切换动画：宽度/位置 200ms PathInterpolator(0.2,0,0,1)。

## 未逐项复刻（源码已备，按需启用）

- bannertip / snackbar / tipspopupwindow / spinner / VLineaMenu / smartrefresh（下拉刷新头尾）
- responsive（VGrid* 布局）/ scrollbar（VFastScroll 拖动索引）/ indexbar
- VBadgeDrawable / VClickDrawable（点击涟漪扩展）/ privacycompliance
