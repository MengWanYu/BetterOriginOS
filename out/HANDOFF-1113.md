# 交接 · 2026-11-13（系统升级防护 / LSPosed OTA 后重载 / 调试模式）

写给新对话：先读本文件；不要从记忆或旧 DEVLOG 推断当前状态。
读完以后**先问用户这次要做什么**，不要自动开工。

---

## 0. 一句话现状

设备已 OTA 到 **OriginOS 15.2.19.1（Android 15，slot `_a`）**，模块 `0.1.3-beta`
在 LSPosed 2.2.1（Magisk 环境）下工作正常；本轮把系统升级防护收敛为
**只下载不安装 + 拦自动重启 + 保安装包 + 低频调试心跳**，updater 实测 `hooks=22`。
下一步是继续做系统升级的优化（外部安装模式 / 显示隐藏 / 取消安装），具体方向等用户定。

---

## 1. 环境与设备

| 项 | 值 |
|---|---|
| 工作目录 | `C:\Users\Administrator\Desktop\Aiqooz8-os5-mod`（名字可改，不要硬编码） |
| 设备 | iQOO Z8 / PD2314，adb 序列号 `10AE4Y0MDE000BL` |
| 系统 | `PD2314_A_15.2.19.1.W10`，Android 15，slot `_a` |
| root | KernelSU 与 Magisk 并存；当前 `adb root` 可用；`su` 可用（上下文可能是 magisk/ksu） |
| 注入框架 | LSPosed 2.2.1 (7930)，Magisk 模块 `zygisk_lsposed` |
| lspctl | `/data/adb/modules/zygisk_lsposed/lspctl`（**不在 PATH**；scope/module 修改要求 **ADB root shell**，`su -c` 会被拒绝） |
| 模块 | `com.betteroriginos` 0.1.3-beta（versionCode 5），APK 与本机 `module/build/BetterOriginOS.apk` sha256 一致 |
| 调试开关 | `betteroriginos_debug=0`（测试完已关） |

LSPosed 当前 scope（DB/WAL 均已核对）：`com.android.camera`、`com.android.filemanager`、
`com.android.providers.media.module`、`com.android.systemui`、`com.bbk.updater`、`com.vivo.cota`。

---

## 2. 本轮已完成/已提交

### 2.1 系统升级防护（commit `8ae0747`）

新功能 `feature/updater/UpdaterGuardFeature`（id `updater_guard`，默认开）：

- **只下载不安装**：hook `CommonUtils.getUpdatePackageDirectory` 把 `ota_pacakge`/`vgc_package`
  返回 `Download`；`CommonUtils.startInstall` 直接拦；`DownloadReceiver` 下载完成后发
  「升级包已下载」通知 + 完整路径。
- **不自动重启**：`n0.a.D`、`PopDialogUtils.showRebootInduceDialog`（强制 `z5=false`）、
  `countdownDialog`、`countdown(AB_REBOOT)`、`CommonUtils.shutdown`、`RecoverySystem.installPackage`、
  `UpdateTipsService$c`、`UpdateManager.setRebootInduceAlarmClock` 全部挂了。
- **保安装包**：`IOUtils.deleteFileImpl` 是递归删除，updater 成功后清理会对已改到 Download 的
  目录整棵删；hook 了 `checkIfNeedToDeletePackageAfterUpdate`/`clearDownloadPackage`/
  `IOUtils.deleteFile(Impl)`/`g0.a.n/h/m`，实测 `blocked deleteDir`。
- 实测：updater `hooks=22`；8.05GB 包完整落 Download，sha256 匹配；不安装、不重启。

### 2.2 调试模式 + 心跳（commit `3a31043`）

- 模块「信息」页新增「调试」→「调试模式」开关，键 `Settings.Global betteroriginos_debug`。
- 打开后 updater guard 每 60 秒输出：
  `updater guard alive pid=… hooks=… enabled=… blocks=…`
  关闭后 2 秒内静默（`Prefs.debugEnabled` TTL 2s）。
- 入口绑定在 `MainActivity` + `page_info.xml`；开关写入走 `SuCommander.write(Prefs.DEBUG_KEY,…)`。
- 心跳已验证：`hooks=22 enabled=1 blocks=0`。

### 2.3 OTA 后 LSPosed 模块没加载（已修）

- 现象：DB/WAL 里模块 enabled、6 个 scope 全在、APK 路径匹配，但 updater/filemanager
  进程没有 `loaded com.*` 日志，心跳不出现；SystemUI 正常。
- 根因：OTA/root 切换后 **lspd 内存里的模块状态未重载**，只对部分进程生效。
- 修法（需要 `adb root`）：
  ```bash
  adb root
  adb shell /data/adb/modules/zygisk_lsposed/lspctl module disable --user=0 com.betteroriginos
  adb shell /data/adb/modules/zygisk_lsposed/lspctl module enable  --user=0 com.betteroriginos
  # 再重启目标进程（updater 可 force-stop/start）
  ```
- 之后 updater 立即恢复 `updater guard installed hooks=22` + alive 心跳。

---

## 3. 未完成 / 下一步（按用户选择）

1. **外部安装模式**（讨论过、未实现）：hook `UpdateManager` 的 `UpdateEngine.bind`，
   让 updater 不注册会话回调；这样重启/force-stop updater 不会再取消第三方安装。
   需要验证“没有 Java 回调客户端时 update_engine 仍接受 native client apply”。
2. **显示/隐藏升级状态 + 取消安装**：入口放模块卡片（用户已选），最好走运行期命令通道，
   避免重启 updater；取消安装才调 `UpdateEngine.cancel()`。
3. **scope/模块状态跨 OTA 持久化**：每次 OTA 后可能重演 §2.3，考虑在模块里做
   “scope 缺失/未加载”提示或一键引导；`lspctl` 必须 adb root 才能用。
4. 旧功能欠债（非本轮）：
   - A11 样式《读心术》切歌后左侧仍黑（0.1.3-beta 已知问题）
   - `Android 系统` 分组摘要行图标空白
   - OS4 上 A11 收起态几何未单验
   - 桌面动效依赖桌面 14.1 混淆名（升级后可能失效）
5. 公开仓库/发布（GitHub `MengWanYu/BetterOriginOS`，release `v0.1.3-beta`）：
   作者关联/README 已处理；GitHub 账号头像仍需用户在网页上传（若还没传）。

---

## 4. 关键纪律（这轮踩过的）

- **OTA 正在进行时不要 restart/force-stop updater**：它的 `UpdateEngine` binder 回调是会话所有者，
  进程死亡会让引擎 `kUserCanceled(48)`。本轮 62.5% 就是这么被取消的。
- 重启 updater 前先 `pidof update_engine_client2`，有活动客户端就别动。
- 模块改动后要重启目标进程才生效；安装 APK 本身不会影响正在运行的引擎。
- `adb root` 在生产版默认不可用；本轮用户手动开了，所以 `lspctl` 才能用。下次 OTA/重启后先确认。
- 调试模式测试完记得关回 `betteroriginos_debug=0`。

---

## 5. 文件/文档索引

| 文件 | 内容 |
|---|---|
| `out/LOG-1112-UPDATER-REVERSE.md` | 系统升级完整逆向（下载/安装/重启链、hook 落点、验证读数、OTA 后 LSPosed 问题） |
| `out/DEVLOG.md` | 最新条目：updater guard、debug 心跳、OTA 后模块未加载 |
| `module/src/com/betteroriginos/feature/updater/UpdaterGuardFeature.java` | 防护实现（22 个 hook + 心跳） |
| `module/src/com/betteroriginos/core/Prefs.java` | `DEBUG_KEY` / `debugEnabled()` |
| `module/src/com/betteroriginos/ui/MainActivity.java`、`module/res/layout/page_info.xml` | 信息页调试开关 |
| `module/build/BetterOriginOS.apk` | 当前构建（332,350 B，已装机） |
| `out/HANDOFF-1111.md` | 上一轮交接（OS4/媒体卡/开源线） |
| `out/BACKLOG-1104.md` | 更早的欠债清单 |

当前 git：HEAD = `3a31043`；工作区只有 `work/` 下几个个人取证文件未跟踪，无源码改动。
