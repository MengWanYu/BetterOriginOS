# OS4 桌面过渡动画逆向（罗盘日志）

- 日期：2026-11-10
- 设备：PD2357（V2357A），Android 14 / Funtouch 14.1（`ro.build.display.id=PD2357_A_14.0.20.8.W10.V000L1`）
- 桌面：`com.bbk.launcher2` 14.1.0.241（versionCode 141000241，targetSdk 34）
- 目的：查清这台机“系统桌面过渡动画”的层级限制在哪、怎么开启；供以后直接复用。

标注约定：**【已核实】**=本轮读代码/读设备确认；**【推论】**=由代码路径推出但未上机；**【未决】**=还没测。

---

## 0. 一句话结论

用户可见的“桌面过渡动画”已经是最高档（舒适=3），但**机型 OEM 预配置**把
`registeranimation=false`、`wallpaperTargetAnimation=false` 写死了，且没有 `animationGrade`（缺省=1）——
桌面因此走的是旧/低档动效链路。要开，就是改这三项（文件覆盖或 LSPosed hook）。
另外代码里还有第二层“双层动画（two-level）”判定，由 `ro.vivo.product.series`（IQOO/其他）+
`power_save_type` + `animationGrade` 共同决定。

---

## 1. 设备事实

| 项 | 值 | 来源 |
|---|---|---|
| `ro.vivo.product.model` | PD2357 | getprop |
| `ro.vivo.product.series` | **Y** | getprop |
| `ro.vivo.product.solution` | MTK | getprop |
| `ro.vivo.product.platform` | MTK6835 | getprop |
| `ro.vivo.op.entry` | FULL_SC | getprop |
| 桌面包 | `com.bbk.launcher2/.Launcher` | `cmd package resolve-activity ... HOME` |
| 桌面版本 | 14.1.0.241 | `dumpsys package com.bbk.launcher2` |
| `window/transition/animator_duration_scale` | 1.0 / 1.0 / 1.0 | `settings get global` |
| `enter_exit_app_animation` | **3（舒适）** | `settings get system` |
| `unlock_enter_launcher_animation` | **3** | 同上 |
| `support_app_animation_window` | **0** | 同上（由桌面自己写，见 §5.1） |
| `power_save_type` | **1** | 同上（决定动画层级，见 §5.4） |
| `reduced_dynamic_effects` | 0 | 同上 |
| `persist.rect.*`（壁纸缩放/模糊/launcher 缩放参数） | **全部为空** | getprop（见 §5.5 的注意事项） |

设备 adb：`10AE742ZRF000HY`（首次连接需手机上点“允许 USB 调试”）。

---

## 2. 用户可见入口

两个 Activity 都在桌面包里，资源里分别是：

| 资源 | 文案 | Activity |
|---|---|---|
| `string/launcher_transition_animation` (0x7f120307) | **桌面过渡动画** | `com.bbk.launcher2/.settings.animation.LaunchExitappAnimSettings` |
| `string/unlock_animation_settings_title` (0x7f12068c) | 解锁进入桌面动画 | `com.bbk.launcher2/.settings.animation.UnlockAnimSettings` |
| `string/enterexit_app_animation_settings_title` (0x7f1201a3) | 进入、退出应用动画 | 同上 Activity 的另一处标题文案 |

`LaunchExitappAnimSettings` 的四档（`l()` 方法按 0..3 顺序构建）：

| 值 | 资源 | zh-rCN |
|---|---|---|
| 0 | `enterexit_app_animation_none` (0x7f1201a2) | 无动效 |
| 1 | `efficient` (0x7f12019a) | 高效 |
| 2 | `enterexit_app_animation_medium` (0x7f1201a1) | 中等 |
| 3 | `enterexit_app_animation_comfort` (0x7f12019f) | 舒适 |

应用按钮写入：`LaunchExitappAnimSettings$d.run()` →
`LauncherSettingsObserver.c0(type)` + `Settings.System.putInt("enter_exit_app_animation", type)`。
(Smali：`smali_classes2/com/bbk/launcher2/settings/animation/LaunchExitappAnimSettings$d.smali`)

---

## 3. 真正的总闸：OEM 预配置

### 3.1 生效文件（按读取优先级）

代码里按顺序找（`smali/r1/a.smali`）：

1. `/oem/etc/launcher/attributes_preconfig.xml`（**本机存在，实际生效**）
2. `/oem/etc/launcher/subuser_attributes_preconfig.xml`
3. `/system/etc/launcher/attributes_preconfig.xml`（回退；本机内容与 /oem 一致）

素材：`work/os4-re/attributes_preconfig-oem.xml`、`work/os4-re/attributes_preconfig.xml`

### 3.2 三个动画开关（两份文件完全一致）

```xml
<!-- 打开退出动画开关 -->
<registeranimation launcher:registeranimation="false" />

<!-- 壁纸缩放动画开关 -->
<wallpaperTargetAnimation launcher:wallpaperTargetAnimation="false" />

<!-- 动态模糊开关 -->
<dynamic_blur launcher:dynamic_blur="true" />
```

- **没有** `animationGrade` 属性 → 走代码缺省 **1**。
- 打包内置的 `res/xml/default_preconfig.xml` 另有 `<animation launcher:useNewAnim="false" />`（与本机的三项开关不是同一条链，别混）。

### 3.3 解析落点

`smali/r1/a.smali`（源码名 **LauncherConfigManager.java**，日志 TAG `Launcher.LauncherConfig`）
`F0(XmlPullParser)`（3530..5915）：

| XML 属性 | 字段 | getter | 语义 |
|---|---|---|---|
| `registeranimation` | `L` | `w0()Z` | 打开/退出应用动画总开关 |
| `wallpaperTargetAnimation` | `M` | `y0()Z` | 壁纸缩放动画开关 |
| `animationGrade` | `N`（并 `J0()` 写 `Q`） | `h()I` / `k()I` | 动画层级（XML 层 / 运行层） |
| `dynamic_blur` | `Y` | 经 `H0()` 算 `Z`；`j0()Z` | 动态模糊能力 |

缺省值（构造函数，`r1/a.smali:255+`）：`L=false`、`M=false`、`N=1`、`Q=1`、`Y=false`。
解析位置：`L/M` 在 4752..4803，`N/J0` 在 4806..4856，`Y` 在 5179..5210。

### 3.4 一个易误解的联动

`r1/a.L0()`（7690..7850）会把 `Settings.System.support_app_animation_window` 与 `L` 对齐：
`L=false → 写 0`；`L=true → 写 1`。所以本机读到 0，不是“另一个开关”，而是 registeranimation 的镜像。

---

## 4. 动画主链（代码地图）

### 4.1 应用打开/退出 + 回桌面

- `smali/h2/k.smali`（**VivoAnimationUtils.java**）：
  - `H(Launcher, boolean)`：onResume 时按 `w0()` 分流。`w0=false` → `J(null,false,null)` / `r()`（旧路径，计算点击图标锚点后 override transition）；`w0=true` → 跳过旧路径。
  - `I(Workspace, Bitmap, IIZZ)`：`overridePendingAppTransitionThumbFromLauncherReflact`，用 Binder 调 WindowManager 做图标-窗口过渡。
  - `J(...)`：`overrideTransition`（进入三方 App / 回桌面）。
  - `N(boolean)` / `C()` / 字段 `b`（`mShouldOverrideNewTranisition`）。
- 桌面调用点：`smali/com/bbk/launcher2/Launcher.smali` 25475、25698、31516、31982、33729；
  `HomeKeyManager.smali:592`（`w0=false` 时做旧回位动画）。
- **判定**：本机 `w0=false` ⇒ 走旧“点击图标锚点 + override transition”链路；把 `w0` 置 true 才会进入新链路（含手势/远程动画注册）。

### 4.2 壁纸缩放

`smali_classes2/e3/q0.smali`（**LauncherQuickstepAppTransitionManager.java**）
`X2(VivoSpringAnimation, boolean)`（11200..11280）：

```
spring != null        -> false
h()==1 && (y0() || 第二参数) -> true
否则                   -> false
```

- `true` → `e3/b1.L(true, wallpaperTarget)`：**真实壁纸缩放**（`VivoAppTransitionController`）。
- `false` → `e3/b1.K(TransitionBlurView)`：**用模糊替代壁纸缩放**。
- 调用点：q0 的 open（约 16226）与 close（约 21285）。
- **推论**：本机 `h()=1`（见 §5.4）、`y0=false`；是否走真壁纸缩放取决于第二个参数，未上机确认。

### 4.3 模糊

- `dynamic_blur` → `Y`；`r1/a.H0()`（6400..6480）用它算 `Z`（`mDynamicBlurEnable`），getter `j0()`。
- `smali/com/android/quickstep/Constants.smali`：`SUPPORT_DYNAMIC_BLUR` 只在 `<clinit>` 被写成 **false**，全仓无其它写入。
- `TransitionBlurView.init()`（1082）据此选：`false → BlurType.STATIC`，`true → BlurType.DYNAMIC`。
- **纠正**：`SUPPORT_DYNAMIC_BLUR=false` **不是“模糊全关”**，而是“静态模糊替代动态模糊”。

### 4.4 双层动画（two-level）

`smali/com/android/quickstep/utils/AnimationLevelUtils.smali#isTwoLevelAnimation()`：

```
w0()==true
&& k()==1            // XML animationGrade
&& h()==2            // 运行层级 N
&& UnLockAnimManager.f0()==true
```

消费点：`QuickstepConfiguration.needAnimationLevel1()`、`TouchInteractionService`（选
`TwoLevelAnimationWrapperConsumer`）。

`h()`（N）的写入者：
- XML 解析：`N = animationGrade`（本机无该属性 ⇒ 1）。
- `LauncherSettingsObserver$...run()`（`smali_classes2/com/bbk/launcher2/settings/d$a.smali`）：
  - 读 `power_save_type`（默认 1）与 `ro.vivo.product.series`（默认 VIVO）；
  - `power_save_type==1 && series!=IQOO` → **不写**（保持 `N=Q=1`）；本机正是这一支；
  - `power_save_type==2 && series!=IQOO` → `I0(2)`（N=2，命中 two-level 试验）；
  - IQOO 系列另有分支；
  - `FeatureUtils.shouldSupportAnimationTrial()` 恒返回 true（`FeatureUtils.smali`）。
- 设置页同步：`d$a:562`、`d.smali:2023` 用 `I0(k())` 把 N 拉回 XML 层。

**本机推演**：`power_save_type=1`、`series=Y` ⇒ `N=1` ⇒ `h()==2` 不成立 ⇒ two-level=false。
当前 `enter_exit_app_animation=3` 选的是 Comfort provider（参数档），与 two-level 是两条判定。

### 4.5 动画参数与 OEM 特性表

- `AnimParamProvider.get()`（`smali/com/android/quickstep/gesture/animparam/AnimParamProvider.smali:141`）：
  - `strategy = BackHomeAnimationHelper.getStrategy()` ← `enter_exit_app_animation`（3 → `ComfortAnimParamProvider`）。
  - `animaVersion` ← `FtFeature.isFeatureSupport("vivo.software.launcher.animationversion")`；本机特性表无此段 ⇒ 缺省 `"1"`。
- **注意**：`persist.rect.*` 只在 `AnimParamProvider.getFloat/getInt` 里被读，而这两个方法在
  `sDebugParams==false` 时**直接返回默认值**（`sDebugParams` ← `LauncherEnvironmentManager.T2`）。
  所以普通改 prop **不会**改动画参数；这些 prop 是调试通道，不是开启通道。
- OEM 特性表：`/vendor/etc/vivo_config.ini`（已拉回 `work/os4-re/vivo_config.ini`）。
  其中**没有** `[vivo.software.recents]`、`[vivo.software.launcher.animationversion]` 段 ⇒ 相关属性全走默认。
- 由此（`FeatureUtils.smali`）：
  - `LOW_PLATFORM_ANIM2=false`（平台 MTK6835 ≠ UMS9230，且无 `low_platform_anim2` 属性）；
  - `FEATURE_ANIM_GRADE2_ENTER_RECENTS=false`（读 `enter_anim2`，默认 false）；
  - `FEATURE_DISABLE_LAUNCHER_SCALE=false`（上一条的同一个值）；
  - `FEATURE_NO_ANIMATION_HORIZONTAL_SWIPE=false`（特性缺失）。
  - **纠错**：子代理一度判“IS_MTK 会强制关 launcher scale”。复核后那段条件用的是
    `LOW_PLATFORM_ANIM2`（寄存器 v1 从 510 行一直保留到 1200 行），不是 `IS_MTK_PLATFORM`；
    本机该值为 false ⇒ **没有 MTK 级 launcher-scale 封锁**。

---

## 5. 本机实际判定（汇总表）

| 判定 | 值 | 依据 | 状态 |
|---|---|---|---|
| `registeranimation` (`w0`) | **false** | /oem preconfig | 已核实 |
| `wallpaperTargetAnimation` (`y0`) | **false** | /oem preconfig | 已核实 |
| `animationGrade` XML (`k`) | **1** | 属性缺失，缺省 1 | 已核实 |
| 运行层级 (`h`) | **1** | power_save_type=1 + series=Y 不写 N | 推论（高置信） |
| two-level 动画 | **关** | 需 w0 && k==1 && h==2 | 推论（高置信） |
| 动态模糊 | 静态模糊 | Y=true，但 SUPPORT_DYNAMIC_BLUR=false | 已核实 |
| `support_app_animation_window` | 0 | L0 镜像 L=false | 已核实 |
| 打开/退出动画路径 | 旧路径（图标锚点 + override transition） | h2/k H()/J() 的 w0 分支 | 已核实分支，观感未验 |

---

## 6. 开启方案（按侵入性排序）

### A. 文件覆盖（最原厂，适合先验证观感）

改 `/oem/etc/launcher/attributes_preconfig.xml`（或 /system 那份）：

```xml
<registeranimation launcher:registeranimation="true" />
<wallpaperTargetAnimation launcher:wallpaperTargetAnimation="true" />
<dynamic_blur launcher:dynamic_blur="true" />
<!-- 可选：<animationGrade launcher:animationGrade="2" /> -->
```

快速试验（root，临时、可回滚）：

```bash
# 1) 拉原件、改本地副本
adb pull /oem/etc/launcher/attributes_preconfig.xml
# 2) 推临时副本并以 bind mount 覆盖（不写坏系统分区）
adb push attributes_preconfig.xml /data/local/tmp/
adb shell su -c "mount --bind /data/local/tmp/attributes_preconfig.xml /oem/etc/launcher/attributes_preconfig.xml"
# 3) 重启桌面进程（桌面会自动拉起）
adb shell su -c "kill $(adb shell pidof com.bbk.launcher2)"
# 回滚：重启设备，或 umount
adb shell su -c "umount /oem/etc/launcher/attributes_preconfig.xml"
```

注意：
- 桌面只在进程启动时读一次配置，**必须重启桌面进程**。
- `mount --bind` 的 SELinux 上下文可能被拒；失败就先 `restorecon` 临时副本。
- 不要直接改 `/system`；OTA/校验会不一致。正式落地建议 KernelSU 模块 overlay 或方案 B。

### B. LSPosed hook（推荐做成 BetterOriginOS 的功能，可开关）

新增 scope：`com.bbk.launcher2`（`res/values/arrays.xml` 的 xposedscope 与
`Feature#scopes()` 两处都要加）。挂钩点：

1. `r1.a.w0()` → 强制 `true`（打开/退出动画总开关）
2. `r1.a.y0()` → 强制 `true`（壁纸缩放）
3. （可选）`r1.a.h()` → 返回 2 / 或 hook `I0(I)V` 写入 2，同时保证 `k()==1`，以命中 two-level
4. （可选）`android.os.Build` 无关；模糊若要走动态：
   `XposedHelpers.setStaticBoolean(Constants.class, "SUPPORT_DYNAMIC_BLUR", true)`
   字段非 final（`.field public static SUPPORT_DYNAMIC_BLUR:Z`），类初始化后再写；需在
   `TransitionBlurView.init()` 之前生效。
5. `UnLockAnimManager.f0()` 若为 false 会挡住 two-level，需要时一并 hook。

优点：不动系统分区、可随时关；缺点：桌面内部类名/字段可能随版本变，需要 `findClass` 失败即自报。

### C. 只改运行档位（单独无效，不建议）

`settings put system power_save_type 2` → 非 IQOO 机型会走 `I0(2)`，`h()=2`；
但 `w0` 仍为 false，动画链路照旧，且会改动省电策略。**不作为开启手段。**

### D. 用户档位

当前 `enter_exit_app_animation=3`（舒适）。若走 B 方案且想命中 two-level，需要 `h()==2`
（可 hook，也可研究 `power_save_type=2` 的副作用）。Comfort(3) 与 two-level 是两套判定，别混。

---

## 7. 未决 / 下一步（按“先便宜后昂贵”）

1. **观感与性能**：`w0=true` 后在这台 MTK6835 上是否流畅（中端机可能掉帧/发热）。
2. `X2()` 第二参数（q0 open/close 两处）到底是什么——决定 `y0=false` 时是否仍可能走真壁纸缩放。
3. two-level 与“中等(2)/舒适(3)”的对应关系：`h()=2` 是 trial 层级，不等同于用户档位，需要上机确认。
4. `UnLockAnimManager.f0()` 本机值（two-level 的第 4 个条件）。
5. `support_app_animation_window` 是否还有 SystemUI/AMS 侧消费者（目前只看到桌面自己读写）。
6. `/oem` 与 `/system` 两份配置的实际读取（代码先 /oem；可用给 launcher 加临时日志或 strace 确认）。
7. 若目标是“OS5 观感移植”，下一步应拉 OS5（Android 15）桌面 APK 做同项对照，而不仅是打开 OS4 原生动画。

---

## 8. 素材与工具索引

本地素材（本轮新增）：

```
work/os4-re/launcher.apk                     # 33,204,847 B，dev拉取
work/os4-re/dex/classes.dex, classes2.dex    # 解包
work/os4-re/launcher-dec/                    # apktool 3.0.3 全量（smali + res）
work/os4-re/launcher-res.txt                 # aapt2 dump resources
work/os4-re/attributes_preconfig-oem.xml     # /oem 生效配置
work/os4-re/attributes_preconfig.xml         # /system 回退配置
work/os4-re/vivo_config.ini                  # /vendor/etc FtFeature 特性表
work/os4-re/jadx-launcher.log                # jadx 失败记录
```

工具：

- apktool 3.0.3：`/c/vivores-build-v303/apktool.jar`，用 JRE25 跑：
  `/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre/bin/java.exe -jar /c/vivores-build-v303/apktool.jar d -f -o <out> <apk>`
- aapt2：`work/sdk-dl/bt35/android-15/aapt2.exe`（`dump resources` / `dump xmltree`）
- dexdump：`work/sdk-dl/bt35/android-15/dexdump.exe`
- 读设备配置：`adb shell su -c "cat /oem/etc/launcher/attributes_preconfig.xml"`

常用命令：

```bash
# 看配置
adb shell 'settings get system enter_exit_app_animation; settings get system power_save_type'
adb shell 'getprop ro.vivo.product.series'
# 看桌面版本
adb shell 'dumpsys package com.bbk.launcher2 | grep -E "versionName|versionCode"'
# 动画入口
adb shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

---

## 9. 本轮踩坑（复用提醒）

1. **jadx 1.5.5 对这个桌面 APK 不可用**：全量导出只产出 11 个类、末尾 `finished with errors, count: 29`。
   这个包用 **apktool** 解才是对的。
2. Git Bash 里 `grep "->w0()Z"` 会被当成选项 → 用 `grep -- "->w0()Z"` 或直接用 Grep 工具。
3. 子代理会把“相邻常量”当因果：那条 `IS_MTK 强制关 launcher scale` 已复核否定，真变量是
   `LOW_PLATFORM_ANIM2`（本机 false）。
4. `SUPPORT_DYNAMIC_BLUR=false` ≠ 模糊关闭，而是 `STATIC` 替代 `DYNAMIC`。
5. `persist.rect.*` 是调试通道（`sDebugParams` 才生效），不是开启通道。
6. 设备 `unauthorized` 时任何取证都不可信；先确认 `adb devices` 是 `device` 再开跑。

---

## 10. 与 BetterOriginOS 的衔接（如果需要做成模块功能）

建议命名：`DesktopMotionFeature` / 设置项“OS4 桌面动效增强（实验）”。

最小可用闭环：
1. scope `com.bbk.launcher2`（arrays.xml + Feature.scopes()）。
2. hook `LauncherConfigManager.w0()`、`y0()` → true；自报 `desktop_motion installed hooks=N w0=1 y0=1`。
3. 桌面重启后由用户上眼：打开/退出、回桌面、壁纸是否缩放、是否有模糊。
4. 若观感/性能不可接受，保持默认关；文档里保留 A 方案的文件覆盖作为对照。

**纪律**：先只开 `w0`/`y0` 两个变量，不一次把 two-level、blur、grade 全打开；
每开一个都留“装上了/命中了/用户看到什么”的见证，避免把多个变量混在一轮里。

---

## 11. 实施结果（方案 B 已落地，2026-11-10 夜，用户确认“动画生效了”）

### 11.1 代码

- 新增 `module/src/com/betteroriginos/feature/desktop/DesktopMotionFeature.java`
  - id `desktop_motion`，标题“桌面动效增强”，分类“桌面”，**默认关**。
  - scope：`com.bbk.launcher2`（`Feature#scopes()` + `res/values/arrays.xml` 两处）。
  - 函数开关键：`Settings.Global` 的 `betteroriginos_feature_desktop_motion`。
- `FeatureManager` 注册；`strings.xml` 新增标题/摘要/说明/分类。
- 说明文案：改的是桌面启动时读取的预配置，开/关后需重启桌面进程（或重启手机）。

### 11.2 两个 hook 点（实测顺序）

1. **主锚点（本机唯一有效）**：`com.bbk.launcher2.environment.LauncherEnvironmentManager.F1()`
   （真实类名，未混淆）返回 `LauncherConfigManager` 实例后：
   - `XposedHelpers.setBooleanField(cfg, "L", true)`（registeranimation）
   - `XposedHelpers.setBooleanField(cfg, "M", true)`（wallpaperTargetAnimation）
   - 并 hook 该实例类的 `w0()` / `y0()` → 恒 true
   - 字段名/方法名是混淆产物（本 build `r1.a` / `L`、`M` / `w0`、`y0`），逐个独立 try，失败只自报。
2. **XML 属性钩子（保留，本 build 0 命中）**：`com.android.org.kxml2.io.KXmlParser.getAttributeValue`
   覆盖 `registeranimation` / `wallpaperTargetAnimation` 为 `"true"`。
   **实测：预配置在 Vector 注入之前就解析完了**，这个钩子在本 build 永远见不到（已作为其它
   注入时序/ROM 的备用路径保留，安装日志会自报 `KXmlParser=2`）。

### 11.3 设备侧操作（这台机是 Vector 2.2，不是 lspctl）

`lspctl` 不存在；CLI 在 `/data/adb/lspd/cli`：

```bash
adb shell su -c "/data/adb/lspd/cli modules enable com.betteroriginos"
adb shell su -c "/data/adb/lspd/cli scope add com.betteroriginos com.bbk.launcher2/0"
adb shell settings put global betteroriginos_feature_desktop_motion 1
# 必须重启桌面进程（配置只在启动时读）
adb shell su -c "kill <launcher pid>"
```

### 11.4 验证读数（2026-11-10 22:58，包 320,062 B）

```
I/BetterOriginOS: loaded com.bbk.launcher2/com.bbk.launcher2
I/BetterOriginOS: desktop motion installed hooks=2 by KXmlParser=2 KXmlParser=missing
I/BetterOriginOS: desktop motion config hook F1=1
I/BetterOriginOS: desktop motion enabled=1 ctx=app
I/BetterOriginOS: desktop motion config patched class=r1.a fields=2/2 methods=2
I/BetterOriginOS: desktop motion forced w0=true class=r1.a
```

用户当场确认：**动画生效了**。

### 11.5 已知边界 / 下一轮再做

- `support_app_animation_window` 的镜像写入尝试无效（桌面进程写 Settings.System 返回 0；
  该机制已删除，不影响动画）。
- 只开了 `w0`(registeranimation) 与 `y0`(wallpaperTargetAnimation)；
  `animationGrade` / two-level / `SUPPORT_DYNAMIC_BLUR` 一律没动。
- 关掉功能后需重启桌面进程才恢复旧链路（字段只在启动期改）。
- 混淆名（`L`/`M`、`w0`/`y0`、类 `r1.a`）随桌面版本变化；失败时日志会打
  `config patch failed: no known field/method`，下一次适配按 §4.4/§5 重新对名即可。
- 跨 ROM 更稳的长期方案仍是 XML 属性钩子——若某版本注入足够早，它会直接生效。

---

## 12. 系统设置入口恢复（2026-11-10 夜，用户确认“有入口了”）

### 12.1 页面结构（实读 Settings APK）

- 用户路径：**设置 → 动态效果 → 界面动态效果**。
- 主页面：`xml/vivo_dynamic_effect_settings`（0x7f160168）→ `com.vivo.settings.DynamicEffectSettings`
  - 里面只有一条 `enhance_dynamic_effect`（order=10，标题 `interface_dynamic_effect_title`=“界面动态效果”），
    intent action = `vivo.settings.enhance_dynamic_settings`。
- 子页面：`Settings$EnhanceDynamicSettingsActivity`（`am start -a vivo.settings.enhance_dynamic_settings` 可解析到），
  资源 `xml/enhance_dynamic_effect_settings`（0x7f160086）——原生只有减弱动态效果/实时模糊/动态模糊等开关。
- **桌面条目不是静态写在 XML 里**，而是 `SettingsItemsUtils.insertExtraPrefs(...)` 从 PackageManager
  扫描满足下列条件的 Activity 动态插入：
  - action：`vivo.settings.INTERFACE_DYNAMIC_EFFECT_SETTINGS`
  - category：`vivo.intent.category.settings.ENHANCED_DYNAMIC_EFFECT`
  - 读取 meta-data：`vivo.settings.title` / `vivo.settings.order` / `vivo.settings.summaryEx`
- 桌面 manifest（`launcher-dec/AndroidManifest.xml:254-279`）：
  - `LaunchExitappAnimSettings`：title=桌面过渡动画（`@string/launcher_transition_animation`），**order=100**
  - `UnlockAnimSettings`：title=解锁进入桌面动画，**order=300**
  - 两者都带 `com.android.settings.category=device`。

### 12.2 为什么“没入口”（两层隐藏）

1. `com.vivo.settings.DynamicEffectSettings.isItemEnabled(Context,int)`：
   **order==100 或 300 直接 return false** —— 所以主「动态效果」页永远不列它们。
2. `com.vivo.settings.EnhanceDynamicSettings.isItemEnabled(Context,int)`：
   `order != 100 || Settings.System.getInt(cr, "support_app_animation_window", 0) == 1`
   —— order=300（解锁）正常显示；**order=100（桌面过渡动画）要求镜像=1**。
3. 镜像来自 `LauncherConfigManager.L0()`：把 `support_app_animation_window` 与 `L(registeranimation)` 对齐；
   本机预配置 `registeranimation=false` ⇒ 桌面每次启动把它写回 0 ⇒ 入口永远不出现。

### 12.3 我们的修法（Settings scope）

- `DesktopMotionFeature.scopes()` 增加 `com.android.settings`；`install(ClassLoader,String)` 分流：
  - launcher 进程 → `install()`（§11 的 config 改写 + w0/y0）
  - Settings 进程 → `installSettings()`：hook
    `com.vivo.settings.EnhanceDynamicSettings.isItemEnabled(Context,int)`，
    **order==100 且功能开启 → setResult(true)**（真实类名，未混淆；private static 也能被 hookAllMethods 命中）。
- Vector scope：`scope add com.betteroriginos com.android.settings/0`；设置进程需重启后注入。
- 证据（2026-11-10 23:06）：
  ```
  I/BetterOriginOS: desktop motion settings entry hooks=1
  I/BetterOriginOS: desktop motion settings entry enabled=1
  ```
  用户确认“有入口了”。
- 关闭 `betteroriginos_feature_desktop_motion` 后重启设置进程，入口恢复隐藏（hook 仅放行、不写系统）。

### 12.4 档位选择器（模块卡片，本机原生入口缺失时的替代）

- 卡片 option：`choice level`，值 0..3，标签实读系统字符串：**无动效 / 高效 / 中等 / 舒适**，默认 3。
- 写入链路：`FeatureAdapter.bindChoiceOption` 写模块键后调用新增的 `Feature#onOptionChanged(key,value)`；
  `DesktopMotionFeature` 覆写 → `SuCommander.writeSystem("enter_exit_app_animation", value)`
  （`SuCommander` 新增 `writeSystem`，与 global 版同样去抖/串行）。
- 选择后由桌面的 `LauncherSettingsObserver` 观察者即时生效，无需重启桌面。
- 语义出处：`LaunchExitappAnimSettings.l()` 的四个 `c4.a` 条目（0..3）与
  `enterexit_app_animation_none/efficient/medium/comfort` 资源。

### 12.5 素材与命令

```
work/os4-re/Settings.apk                         # 107,144,439 B
work/os4-re/settings-dec-res/res/xml/vivo_dynamic_effect_settings.xml
work/os4-re/settings-dec-res/res/xml/enhance_dynamic_effect_settings.xml
work/os4-re/settings-dec-res/res/xml/other_dynamic_effect_settings.xml
work/os4-re/settings-src5/sources/com/vivo/settings/DynamicEffectSettings.java
work/os4-re/settings-src5/sources/com/vivo/settings/EnhanceDynamicSettings.java
work/os4-re/settings-src5/sources/com/vivo/settings/SettingsItemsUtils.java
work/os4-re/settings-dex/classes5.dex
work/os4-re/Settings-res.txt                     # aapt2 dump resources
```

```bash
# 资源解码（只解资源，快）
java -jar /c/vivores-build-v303/apktool.jar d -f -s -o <out> work/os4-re/Settings.apk
# 定位页面
adb shell cmd package resolve-activity --brief -a vivo.settings.enhance_dynamic_settings
```

### 12.6 教训

- 设置入口“不存在”≠ 没有入口：先读 Settings 的 `isItemEnabled` 与动态条目扫描逻辑，再谈注入。
- 条目来自 PackageManager 扫描 + meta-data，不是 Settings 静态 XML；改 launcher 侧只改 `L` 不够，
  Settings 读的是 `Settings.System` 镜像，而且桌面启动会写回 0。
- 最稳的恢复方式是 hook Settings 的**可见性判定**（只放行 order=100），不写系统、不碰 /system。
- uiautomator dump 两次都抓到模块 App 首页（Settings 页未到前台），本轮以 hooks/hook 命中见证 +
  用户确认作为证据；dump 不作为结论。
