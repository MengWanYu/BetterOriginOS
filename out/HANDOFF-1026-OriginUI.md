# 交接日志 · 2026-10-26 · OriginUI 复刻库完成 → 下一任务：通知条 iOS 化

> 新对话先读：**本文件** + `out/TASK-NOTIFY-ROW-1026.md`（待办任务）+ `vivo-OriginUI/LOG.md`（逐轮逆向记录）。

## 0. 开工前：先加载 Skill（必做）

| 场景 | 加载 | 路径 / 方式 |
|---|---|---|
| 改 BetterOriginOS（Feature/Hook/设置 UI/构建/真机验证/排障） | `betteroriginos-dev` | `/betteroriginos-dev` 或 Read `.zcode/skills/betteroriginos-dev/SKILL.md` |
| 用 OriginUI 复刻库（UI/模糊/控件/接入其他 App） | `originui-integration` | `/originui-integration` 或 Read `.zcode/skills/originui-integration/SKILL.md` |

- 两件事都涉及（如通知条任务）→ **先 betteroriginos-dev，再 originui-integration**。
- `originui-integration` 于 2026-10-26 新建；若当前会话技能列表未出现，新开对话会自动发现，
  临时用直接 Read 文件即可（内容自足）。
- 推荐读序：本文件 → `out/TASK-NOTIFY-ROW-1026.md` → 对应 skill → `out/DEVLOG.md` 尾部 → 代码。
> 设备/构建链/坑的长期事实已存记忆：`C:\Users\Administrator\.zcode\cli\memories\projects\aiqooz8-os5-mod-294077aec7551989\memory\betteroriginos-project-facts.md`。

## 1. 一句话现状

OriginOS 5 UI 框架（OriginUI）已完成代码级逆向与 1:1 复刻：**原生模糊、开关、滑杆、列表/卡分组、顶底栏、弹层族全部真机验证通过**；
BetterOriginOS 已接入复刻库并重排为 iOS 风排版。**下一任务：通知中心通知条改成 iOS 显示逻辑**（任务书见 `out/TASK-NOTIFY-ROW-1026.md`，当前未开工）。

## 2. 本轮（10-26）已完成并验证

### 2.1 vivo-OriginUI（新增，独立于模块）
| 内容 | 路径 |
|---|---|
| 逆向原理文档（模糊 / 顶底栏滚动 / 控件参数） | `vivo-OriginUI/docs/01-blur.md`、`02-viewport.md`、`03-controls.md` |
| 复刻库（纯 Java 9 文件，无 androidx/资源依赖，可整目录拷入任何 App） | `vivo-OriginUI/lib/src/com/originui/replica/` |
| 逆向源码归档（framework blur 三类 + OriginUI 486 类 + App 用法样本） | `vivo-OriginUI/ref/` |
| 逐轮验证日志（含所有踩坑） | `vivo-OriginUI/LOG.md` |
| 真机截图证据（12+ 张） | `vivo-OriginUI/evidence/` |
| Demo App（已装机 `com.betteroriginos.vivouidemo`，图标「vivo UI 复刻」） | `demo/` → `demo/build/VivoUILab.apk`（约 113KB） |

**核心发现（最重要）**：vivo framework 在 `android.view.View` 上加了公共方法
`setMaterial(int style, int contentType)`，普通 App 反射调用即得**原生模糊**（RenderNode Kawase + 双层遮罩 + 饱和度），
无需 hook/权限/签名。此前用 RenderEffect 自绘模拟是错误路线。
- 真机 gate 证据：`vivo-OriginUI/evidence/gate-material-1.png`；`FtFeature("vivo.software.spacesystem")=true`。
- 19 种 OriginUI 类型 → 亮/暗材质 id 映射 + contentType 语义：`docs/01-blur.md` §3-4。
- 容器行为（成功清背景 / 失败回退 / 发丝线随 alpha / 夜间自动换材质 / 滚动 16dp 渐显）：`docs/01-blur.md` §5。

### 2.2 用户两次"不像"后的重制（关键细节，勿回退）
1. **开关**：OFF 是**空心**（2.5dp 描边胶囊高 17.5dp + 3dp 灰环 thumb 内透明，thumb 高于轨道）；
   ON 是 13dp 实心浅蓝条（#330F77FF）+ 白底 thumb 80% + 3dp 蓝环。
   动画：双 ValueAnimator（C0 0→1 / B0 1→0），D0=250ms，默认 `PathInterpolator(0.2,0.4,0.2,1)`（点击无过冲）；
   拖动跨 0.6/0.4 换 `Overshoot(1.8)` + `start()`→`setCurrentPlayTime(J0×250)` 续播；
   `onCheckedChanged` 在**动画结束**触发；触感受系统 `haptic_feedback_enabled` 控制。
   逐帧证据与验证方法：`vivo-OriginUI/LOG.md` 06:45 条目。
2. **滑杆**：thumb = **18dp 蓝盘 + 9dp 白心**（无阴影，夜间白心变黑）；轨道 4dp；
   拖动有**数值气泡**（≥50×22dp、#0F77FF、白字 12sp、thumb 上方 12dp 跟随）。
3. **全局 token 像素校正**（原 DESIGN-SPEC 近似值是错的）：行高 55dp、卡间距 **18dp**、页边距 **22dp**、
   行标题 **16sp**、说明/值 **13sp**、分隔线左缩 **15dp** 右侧到边 **#D9D9D9**（夜 15% 白）、卡圆角 12dp、section 12sp。
   测量证据：`vivo-OriginUI/evidence/replica-v3.png` vs `work/os5-ref/shot-display-light.png`。
4. **方法论**：视觉参数一律「真机截图 PIL 逐像素测量 + 反编译 APK res 官方 dp/sp 交叉验证」，源码只推逻辑
   （动画/手势/状态机）。不要再凭 DESIGN-SPEC 近似值画控件。

### 2.3 BetterOriginOS 接入
- 复刻库已拷入 `module/src/com/originui/replica/`（`module/build.sh` 的 `find src` 自动编译）。
- `item_feature.xml` / `item_option_switch.xml`：框架 Switch → `com.originui.replica.OriginSwitch`（48×28dp）。
- `ConfirmDialog` 内部换成 `OriginOverlays.OriginDialog`（对外 API 不变）。
- 模块信息页 iOS grouped-inset 重排（section 标题卡外、行分隔线、蓝色文字按钮行）。
- 真机回归：开关写链路 settings 0↔1 正常；`module/build/BetterOriginOS.apk`（约 140KB，已装机）。

## 3. 设备与构建（不变）

- iQOO Z8（PD2314 / V2314A，Android 15 / OriginOS 5），adb 序列 `10AE4Y0MDE000BL`，无密码锁（上滑解锁）。
- SystemUI `15.0.5.241`；KernelSU + LSPosed 2.2.1；`lspctl` 路径 `/data/adb/modules/zygisk_lsposed/lspctl`。
- 构建（两套同款轻量链，aapt2→javac→d8→注 dex→zipalign→apksigner）：
  - 模块：`cd module && ./build.sh` → `module/build/BetterOriginOS.apk`
  - Demo：`cd demo && ./build.sh` → `demo/build/VivoUILab.apk`（已把 `../vivo-OriginUI/lib/src` 加进 javac 源）
- 安装：`adb install -r <apk>`；模块改动后：`adb shell "kill \$(pidof com.android.systemui)"`（会短暂锁屏一次）。
- 当前配置已还原：`betteroriginos_media_card_style=material`、`betteroriginos_feature_media_card=1`、
  `animator_duration_scale=1.0`（验证动画时曾临时调 5/10，已恢复）。
- 注意：App 的 su 读不到 SystemUI 目录（KernelSU 命名空间隔离）；二进制传输走 MediaStore（见记忆）。

## 4. 常用验证命令

```bash
cd "C:/Users/Administrator/Desktop/Aiqooz8-os5-mod"
adb install -r module/build/BetterOriginOS.apk
adb install -r demo/build/VivoUILab.apk

# Demo 验收页（原生模糊 + 全控件）
adb shell am start -n com.betteroriginos.vivouidemo/.ReplicaActivity

# 夜间回归
adb shell cmd uimode night yes ; adb shell cmd uimode night no

# 截图（注意：截图显示坐标 ≠ input tap 坐标，tap 用真实 1080×2400）
adb exec-out screencap -p > shot.png

# 控件定位先 dump，别猜坐标
adb shell uiautomator dump /sdcard/ui.xml && adb pull //sdcard/ui.xml ui.xml

# 动画逐帧验证
adb shell settings put global animator_duration_scale 5.0
# 设备端连拍： adb shell "(i=0; while [ \$i -lt 12 ]; do screencap -p /sdcard/f\$i.png; sleep 0.25; i=\$((i+1)); done) & input motionevent DOWN x y; ..."
adb shell settings put global animator_duration_scale 1.0
```

## 5. Skill 路径（新对话按需加载）

| Skill | 路径 | 何时用 |
|---|---|---|
| `originui-integration` | `.zcode/skills/originui-integration/SKILL.md` | 用 OriginUI 复刻库（模糊/开关/滑杆/行/弹层）接入任何 App；含坑表与验收流程 |
| `betteroriginos-dev` | `.zcode/skills/betteroriginos-dev/SKILL.md` | 改本模块（Feature/Hook/构建/安装/真机调试）必读 |

## 6. 已知限制（不影响交接）

- OriginSheet 无拖拽手势；时钟 App 的 HeaderLayout 阻尼滚动头只做了逆向文档未移植。
- Toast 未走真 TOAST 材质（窗口类型限制）；demo 的 BlurLab 页仍是旧 RenderEffect 模拟（保留作对照，验收以 ReplicaActivity 为准）。
- maskFusion 1/6/7/11 的原生混合公式未解析，兜底渲染为近似（真机走原生 API 无影响）。
- 文件管理重命名 Feature 端到端验证仍挂起（与 UI 工作无关）。
- 通知条优化是**新任务，尚未改动任何代码**。

## 7. 新对话起步清单

1. 读本文件 + `out/TASK-NOTIFY-ROW-1026.md`。
2. `adb devices` 确认设备在线；`adb shell settings get global betteroriginos_feature_media_card` 确认配置。
3. 打开 `vivo-OriginUI/evidence/` 与 `work/os5-ref/shot-notify-shade-os5-1026.png` 熟悉当前观感。
4. 按任务书 §4 的逆向清单开工；每完成一个 hook 就装机 + 截图 + 记 `out/DEVLOG.md`。
