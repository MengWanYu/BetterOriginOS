# 交接 · 2026-11-11（OS4 适配与媒体卡修复批次）

写作者：ZCode agent（本轮执行者）。目的：**如实交代做到哪、哪些结论可信、下一步从哪下手**。
阅读顺序建议：§0 → §2（未完成项）→ §4（坑表）→ §6（开源待办）。

---

## 0. 一句话现状

模块已从"只在 iQOO Z8（OS5/OS3）可用"扩到 **Android 14 / OriginOS 4（OS4）** 机型（vivo Y37），
相机快门、桌面动效、媒体卡三样式、通知条大图标、媒体选择器五项均已真机验收。
**唯一没闭环的是用户最后报的一条：Android 11 样式下《读心术》切歌后左侧仍黑。**
已发布 `0.1.3-beta`（versionCode 5，`out/BetterOriginOS-0.1.3-beta.zip`），
并在更新说明的「已知问题」里如实写了这一条。

---

## 1. 环境与设备

| 项 | 值 |
|---|---|
| 工作目录 | `C:\Users\Administrator\Desktop\Aiqooz8-os5-mod`（用户改过名，**不要硬编码**） |
| 分支 | `master`，HEAD = `da08510`（发布提交） |
| OS4 测试机 | **vivo Y37**，Android 14 / OriginOS 4（OS4），adb 序列号 `10AE742ZRF000HY`；机型代码 V2357A（raw `ro.vivo.product.model=PD2357`） |
| OS5 机 | iQOO Z8 / PD2314，`10AE4Y0MDE000BL`（本轮未使用） |
| 注入框架 | **Vector 2.2**（CLI 在 `/data/adb/lspd/cli`，**没有 lspctl**） |

> 更正（2026-11-11 用户指出）：OS4 测试机是 **vivo Y37**，系统是 **OriginOS 4**
> （Android 14），不是 Funtouch 14.1。早前把 `ro.vivo.os.name=Funtouch` 当成了系统名，
> 本文与相关日志已按正确叫法改写。
| **设备状态** | **用户已将设备拿走，当前 adb 无设备**。接手前先 `adb devices` 确认 |

Vector 常用命令（与 LSPosed 的 lspctl 不同）：

```bash
su -c "/data/adb/lspd/cli modules enable com.betteroriginos"
su -c "/data/adb/lspd/cli scope add com.betteroriginos com.android.systemui/0"
su -c "/data/adb/lspd/cli scope ls com.betteroriginos"     # 子命令是 ls，不是 list
```

当前 scope（设备上）：`com.android.camera`、`com.android.providers.media.module`、
`com.android.settings`、`com.android.systemui`、`com.bbk.launcher2`。

功能键（Settings.Global）：`betteroriginos_feature_notify_row=1`、
`betteroriginos_feature_camera_shutter=1`、`betteroriginos_feature_desktop_motion=1`、
`betteroriginos_media_card_style=a11`（用户选的样式）。

---

## 2. 未完成项（按优先级，别跳）

### 2.1【用户最后报的、未解决】a11 样式《读心术》切歌后左侧仍黑

用户原话："现在放读心术的封面的左边还是黑的，还是没盖好"。

**已修的三段根因（都真机取证过，都在 `0dd58f6`）**：

1. 建卡那一拍 RemoteViews 还没投递 largeIcon ⇒ `art=null` ⇒ `artSource()` 永远 null
   ⇒ 这张卡永久不取色（bbkmusic 元数据里没有封面位图）。
   → `rediscoverArtIfNeeded()`：取色前重探封面视图，探到即接管。
2. `bindSession` 里 syncArt 跑在 refreshColors 之前 ⇒ 浮雕拿建卡时的黑底画一张
   （`relief color=ff000000`），封面指纹不变后无人再触发。
   → 顺序改正 + `refreshColors` 末尾固定补一次 `syncArt()`（幂等门兜住）。
3. `TransitionDrawable` 只在被绘制时推进 ⇒ 闲置面板冻结在第 0 帧（旧底/黑），
   而视图树里新底色早已就位（dump 证实文字已新、像素仍黑）。
   → `startTransition` 后 `postInvalidateDelayed` 补终帧（卡片底色 + 封面淡入两处）。

**但用户复测仍黑。** 我的下一步方向（按性价比排序，均未验证）：

- **最可疑：`A11ArtRelief` 的遮罩/矩阵在浅色封面上产出偏黑**。
  读心术的卡片底色是 `f8f8f8`（浅），如果左边那半浮雕实际画的是黑色，
  肉眼就是"左半黑 + 右半真封面"，与用户描述完全吻合，而日志里 palette 全是对的
  （说明问题在**绘制产物**，不在取色数学）。建议：单独把 `A11ArtRelief.colorize()`
  的返回值存成 PNG（hook 里 `Bitmap.compress` 到 /sdcard）看一眼左半到底是什么色；
  再对照 `A11NotifSpec.reliefMatrix(color)` 与三段 `GRAD_STOPS` 的 alpha 语义
  ——`DST_IN` + `GRAD_TRANSPARENT` 起头这一套，若矩阵把白映射成黑就会得到纯黑左缘。
- 若浮雕本身没问题，则怀疑 **artView 的父容器背景**：a11 分支 `artView.setBackground(null)`，
  但 OS4 的模板 wrapper 可能给它套了自己的 background（黑）。查 `artView.getBackground()`。
- **兜底方案（如果时间紧）**：a11 样式放弃"浮雕渐变"，改成两层绘制——
  左半用卡片底色的纯色矩形、右半用封面位图，中间 32dp 线性 alpha 过渡。
  观感略逊于 A11 原生，但绝不会黑。

**验收方法（必须代码层，别只看截图）**：
- 面板**空闲**时 `screencap` 会拿到旧帧（本轮被这条骗了两轮），
  必须 `uiautomator dump` + 模块日志 + **同帧像素采样**三件套对齐；
- 像素采样脚本本轮一直在用：
  ```python
  from PIL import Image
  im = Image.open('x.png').convert('RGB')
  print([im.getpixel((x, 600)) for x in (60, 150, 250)])   # 卡片左侧
  ```
  期望 ≈ 日志里的 `bg=ffXXXXXX`（误差 1/255 内）。

### 2.2 组摘要行左上角图标空白（旧债，OS5/OS3/OS4 都有）

`pkg=android` 一类分组摘要行（"Android 系统"）图标缺失。0.1.2-beta 起就记在已知问题里，
本轮未动。相关：`NotifyRowFeature` 的 `suppressNativeIcon` / 大图标顶替判据。

### 2.3 桌面动效依赖混淆名（OS4 特有脆弱点）

`DesktopMotionFeature` 挂的是 `com.bbk.launcher2.environment.LauncherEnvironmentManager.F1()`
拿到的 config 实例字段 `L`/`M` 与方法 `w0()`/`y0()`（桌面 14.1.0.241 的混淆名）。
桌面升级后会失效——失败时日志会打 `config patch failed: no known field/method`。
重新对名的方法见 `out/LOG-1110-OS4-LAUNCHER-ANIM-REVERSE.md` §3.3/§4.4。

### 2.4 a11 收起态（compact）在 OS4 上的几何未单独验

展开态已验证（dump 里 title 在 [80,412]、动作排在文字右侧、无源图标行）。
收起态只在 Z8/A13 上验过。OS4 的收起态模板可能不同。

---

## 3. 本轮提交

| commit | 内容 |
|---|---|
| `55863f3` | OS4 适配第二批：媒体卡三样式 + 快门录像态 + 选择器探测（589 行） |
| `e8c2ac9` | DEVLOG：OS4 第二批（媒体卡四坑/快门/选择器/面板剥行） |
| `b4a2184` | notify_row 大图标在 OS4 生效 + 新增 `work/verify_row_os4.py` |
| `f99b0e3` | 晨间反馈：a11 切歌黑（art=null 重探）+ os5 右侧小封面闪现（守卫全样式） |
| `0dd58f6` | a11 黑底三段根因闭环（取色顺序 / 过渡冻结 / sweep 永久拉黑） |
| `da08510` | 发布 0.1.3-beta（APK + CHANGELOG 三件套） |

更早的 `c8ad62c` 是桌面动效首批（上一轮）。

---

## 4. OS4 特有坑表（每条都真机踩过）

1. **Vector 会重复派发 `handleLoadPackage`**（8 秒内 systemui×2 + magazine×2），
   而且**会整体重建模块 classloader** ⇒ 静态去重集合会归零。
   → `FeatureManager` 双重守卫：静态 `Set` + 宿主进程 `SharedPreferences`
   键 `installed_<Process.getStartElapsedRealtime()>`。
2. **`notify row feature installed header-time=` 不是重装日志**，是 `NotifyRowFeature.headerTimeMode()`
   的低频补发见证（logd 250 行/秒会冲掉 install 那一行）。我为此空转过一轮，别再踩。
3. **OS4 媒体行有两副面孔**：
   - 收起态 = framework AOSP 模板（`media_actions` / `right_icon`），
     根类是 `com.android.internal.widget.MediaNotificationView`
     ——**在 framework 里，不在 SystemUI APK 里**（所以 grep SystemUI dex 会扑空）；
   - 展开态 = vivo 自有 MediaStyle 族（`action0..4`、无 id 的头部/封面/SeekBar）。
   → `MediaCardFeature.discoverMedia()` 统一探测：actions = `media_actions` → `action0` 父容器；
     art = `right_icon` → 子树里最大方形 ImageView。
4. **有个外部写手会对通知视图反复写 INVISIBLE / alpha=0**（模板 wrapper 一族），
   连搬进我们自己卡片里的 `android:id/title` 也照写（见证 `titleVis=4`、`alpha=0.0`、
   `color=ffffffff`、xy 全对）。→ 媒体卡**不再搬原视图**，自建 TextView 复制内容。
5. **`createScaledBitmap` 同尺寸时返回原图本身**，而通知 largeIcon 解出来的 bitmap
   不可变 ⇒ `setPixels` 抛 `IllegalStateException`。→ 自建可变位图 + Canvas 绘制。
6. **面板关闭时 vivo 会把静默区（importance=2）的行剥成空壳**
   （`MediaContainerView→RoundRelativeLayout` 无子级），重开面板走 setChild 重绑、
   卡片自然重建——**不是 bug**。但因此 **sweep 循环不能按 `SKIP_LOGGED` 永久跳过**，
   否则剥行重建后的根永远补挂不上（真机 06:42 序列）。
7. **勿扰开着时 bbkmusic 通知是 importance=2** 进静默区；`cmd notification set_dnd off`
   不能恢复 importance（channel 决定），但行仍会 attach，卡片照常接管。
8. **OS4 相机切模式不走 `setImage*`**（icon hooks 全链 0 命中）⇒ 快门必须用
   content-desc 兜底：录像态 `开始录制…`、拍照态 `拍照…`。
9. **`isNight(ctx)` 不能用通知视图的 context**（那是 BBK 音乐带的浅色 uiMode），
   否则 os5/material 出"暖黑底 + 深棕字"看不见。→ 用 `sysuiContext()`。
10. **Git Bash 的 `MSYS_NO_PATHCONV=1` 会连 jar 路径转换一起禁掉**，
    `java -jar /c/...` 会失败。→ `JAR=$(cygpath -m /c/vivores-build-v303/apktool.jar)`。
11. 工具链：apktool 3.0.3 `/c/vivores-build-v303/apktool.jar` +
    JRE25 `/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre/bin/java.exe`；
    aapt2/dexdump 在 `work/sdk-dl/bt35/android-15/`；
    **jadx 1.5.5 对 OS4 桌面 APK 不可用**（只出 11 个类），用 apktool。

---

## 5. 构建 / 发布 / 验收

```bash
# 构建（无 Gradle，自研链）
cd module && ./build.sh          # 产物 build/BetterOriginOS.apk，失败看 build-log.txt（GBK）

# 装机 + 生效（Vector）
adb install -r build/BetterOriginOS.apk
adb shell 'su -c "kill $(pidof com.android.systemui)"'   # 会锁屏：keyevent 224 → keyevent 82

# 离线门禁（发布前必跑）
bash work/offline-all.sh          # 24 项

# 媒体卡真机日志验收
python work/verify_os5_mirror.py --media-only <logcat窗口>

# 通知栏大图标验收（需面板展开后的 dump）
python work/verify_row_os4.py <dump.xml> <logcat窗口> 2.0

# 发布三件套（apk + CHANGELOG.txt + MD5.txt → deflate zip）
# 做法见 da08510 的提交与 out/CHANGELOG-0.1.3-beta.txt（UTF-8 BOM，含 MD5/SHA-256）
```

取证的公共前提：`adb shell svc power stayon true`（结束复原 false）；
面板空闲时 `screencap` 可能拿旧帧；`uiautomator dump` 在窗口繁忙时会报
"could not get idle state"。

---

## 6. GitHub 开源待办（用户在本轮末尾提出，**未开工**）

用户要求：创建 GitHub 仓库开源、检查是否需要令牌、单开一个分支推 GitHub 不影响主线。

### 6.1 已核实的现状

- **`gh` CLI 未安装**（`gh: command not found`）→ 需要先装，或改用 HTTPS + PAT。
  **令牌不能由代理代为获取**，必须由用户提供（`gh auth login` 或
  `git remote add origin https://<user>:<PAT>@github.com/...`）。
- **仓库无任何 remote**（`git remote -v` 为空），分支只有 `master`，18 个 commit。
- `.gitignore` 已覆盖 `*.apk`、`*.dex`、`work/os4-re/`、`work/camera-re/`、
  `work/systemui/`、`work/sdk-dl/` 等 ⇒ **仓库里没有任何厂商 APK/dex/jar**
  （已用 `git ls-files | grep -E '\.(apk|dex|jar|so)$'` 核实 = 0）。
- **但**：跟踪文件里 **25 个含设备序列号**（`10AE742ZRF000HY` / `10AE4Y0MDE000BL` /
  `IBR8GEMFJNBECIFA`），**35 个含 `Users.Administrator` 本机路径**。
  主要集中在 `out/*.md`、`work/*.sh`、`.zcode/skills/`、`export/`。
  ⇒ 公开前**必须脱敏**（否则会泄露用户设备与主机信息）。

### 6.2 建议做法（不影响主线）

```bash
# 1) 建一个干净的公开分支（orphan，不承载历史里的内部笔记）
git checkout --orphan public
git rm -r --cached .
# 2) 只放要开源的部分
git add module/ vivo-OriginUI/lib/ demo/ README.md LICENSE .gitignore
# 3) 脱敏后提交
git commit -m "Initial public release"
# 4) 推送
git remote add origin git@github.com:<user>/BetterOriginOS.git
git push -u origin public
```

**开源范围建议**：`module/src`、`module/res`、`module/build.sh`、`module/stubs`、
`vivo-OriginUI/lib`、`demo`、一份新写的 README（说明这是 LSPosed 模块、需自担风险、
不含任何厂商二进制）。**排除**：`out/`（内部逆向笔记与设备读数）、`work/`（取证脚本与
反编译残片）、`.zcode/`、`.qoder/`、`export/`。

**法律注意**：本项目的取值大量来自对 vivo 闭源 APK（SystemUI / 桌面 / 相机 /
MediaProvider）的反编译。开源源码本身风险不大，但**不要把反编译产物、资源文件、
或厂商 APK 一起上传**，也不要在 README 里附资源 id 清单之外的原始反编译内容。

---

## 7. 文件与文档索引

| 文件 | 内容 |
|---|---|
| `out/DEVLOG.md` | 逐时间条目，含本轮全部踩坑与真机读数（最新三条是 2026-11-11） |
| `out/LOG-1110-OS4-LAUNCHER-ANIM-REVERSE.md` | OS4 桌面动效完整逆向（章节 §11/§12 是落地与设置入口） |
| `out/CHANGELOG.md` / `out/CHANGELOG-0.1.3-beta.txt` | 使用者向更新说明 |
| `out/HANDOVER-1103.md` | 上一轮交接（A13/OS3 线的教训，含"别用可判绿的指标定义做完"） |
| `work/offline-all.sh` | 离线门禁 24 项，发布前必跑 |
| `work/verify_os5_mirror.py` | 媒体卡日志验收器（`--media-only`） |
| `work/verify_row_os4.py` | 大图标几何验收器（本轮新增） |
| `.zcode/skills/betteroriginos-dev/SKILL.md` | 开发指南（新增坑请补 §4） |
| `module/src/com/betteroriginos/feature/media/MediaCardFeature.java` | 本轮改动最大（约 3.9k 行），§2.1 的问题在这里 |

---

## 8. 给接手的第一个小时

1. `adb devices` 确认设备在线；不在线就先别改代码，把 §2.1 的离线分析做完
   （读 `A11ArtRelief.colorize()` + `A11NotifSpec.reliefMatrix`）。
2. 跑一次 `bash work/offline-all.sh` 确认 24 项绿（环境没坏）。
3. 若设备回来了：开 a11 样式 → 放《读心术》→ 按 §2.1 的三件套取证
   （dump + 日志 + 同帧像素），先确认"左边黑的是卡片底还是浮雕"。
4. 别信截图单帧，别信"日志对了就等于画对了"——本轮这两条各骗过我一次。

---

## 9. 开源进展（2026-11-11 晚，已完成）

- **公开仓库**：<https://github.com/MengWanYu/BetterOriginOS>（public，默认分支 `main`）。
- **本地分支**：独立 worktree `C:\Users\Administrator\Desktop\Aiqooz8-public`，
  orphan 分支 `public`，根提交 `baf4a1d`，已推送为 `origin/main`；**主线 master 不受影响**。
- **公开内容**：`module/`（src/res/stubs/assets/build.sh）、`vivo-OriginUI/lib` + `docs`、
  `demo/`、`README.md`、MIT `LICENSE`、`.gitignore`；共 181 个文件、34,015 行。
- **排除**：`out/`、`work/`、`export/`、构建日志、旧 `patch_*.py`、`debug.keystore`、厂商二进制。
  已扫描确认：公开树无设备序列号、无本机路径、无 apk/dex/jar/so。
- **构建可移植**：两个 `build.sh` 改为 `ANDROID_HOME` / `JAVA` / `ANDROID_BUILD_TOOLS` /
  `ANDROID_JAR` 可覆盖，Windows/Linux 两用；module 与 demo 均已在本机构建通过。
- **凭据**：Git Credential Manager（Windows 凭据管理器），GitHub 账号 `MengWanYu`，
  classic PAT（scope `gist, repo, workflow`）已长期保存；以后 push 不再需要输入。
- **网络注意**：本机直连 `github.com` 不通、`api.github.com` 正常；git 推送依赖
  加速器代理 `127.0.0.1:7897`，且该仓库本地配置了 `http.sslBackend=schannel`
  （只写在 public worktree 的仓库配置里，未改全局）。代理关闭时推送会失败。
- 本机全局 `http.sslVerify=false` 是既有配置（非本次改动）；公开树不含任何令牌。
  如不再需要长期凭据，可在 GitHub → Settings → Applications 撤销该 PAT。
- **Release 已发布**：<https://github.com/MengWanYu/BetterOriginOS/releases/tag/v0.1.3-beta>
  - tag `v0.1.3-beta`（指向公开根提交 `baf4a1d`），标记为 **prerelease**；
  - 附件：`BetterOriginOS-0.1.3-beta.apk`（324,158 B）、`BetterOriginOS-0.1.3-beta.zip`（302,767 B）、
    `CHANGELOG-0.1.3-beta.txt`（4,019 B）；正文即更新日志（含“已知问题：a11 读心术左黑”）。
    设备名更正后已重建 zip 并替换 changelog/zip 两个附件，APK 未变（哈希仍与正文一致）。
  - 下载已通过代理回归验证（APK HTTP 200）。
  - 若要让 GitHub 显示 “Latest”，需要把该 release 取消 prerelease 标记（或另发正式版）。
- **公开提交作者**：已从 `TWRP Builder` 改为 **AuroraXL**（模块 `author_info` 里的作者名，
  邮箱 `MengWanYu@users.noreply.github.com`）。public 历史已重写并强推：
  当前 `main` = `f930091`，release tag `v0.1.3-beta` = `75d5b76`（新根提交）。
  主线本地历史未改写，只影响之后的提交身份。
