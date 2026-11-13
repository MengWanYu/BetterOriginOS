# 系统升级（com.bbk.updater）下载与自动重启逆向 + 防护 Hook

- 日期：2026-11-12
- 设备：iQOO Z8 / PD2314，Android 15，OriginOS 15.2.18.0.W10.V000L1
- 升级器：`com.bbk.updater` 4.9.20.5（versionCode 904092005），APK 位于
  `/system/app/Updater/Updater.apk`，本地 `work/os4-re/updater.apk`（sha256 前缀 `b6f91630243f6319`）
- 反编译：jadx → `work/os4-re/updater-src/sources/`
- 目标：加模块 hook，升级包下载完成后**不自动倒计时、不自动重启**；按钮点“重启”也不能重启（防掉 root）。

---

## 0. 一句话结论

下载完成后 `PopDialogUtils.showRebootInduceDialog(..., z5, z6)` 建重启对话框，`z5=true` 时
`countdownDialog()` 起 10 秒 `CountDownTimer`；倒计时结束发 `DialogEvent(13)`，
`BaseUpdateStrategy` 调 `n0.a.C(ctx, UPDATE_NOW_COUNT)`；正按钮“立即重启”发 `DialogEvent(10)`，
同样落到 `n0.a.C(ctx, UPDATE_NOW_DIALOG)`。`n0.a.C/D` 最终在子线程执行
`PowerManager.reboot("reboot-ab-update")`。另有 `CommonUtils.shutdown()` 与
`RecoverySystem.installPackage()` 两条旁路。

---

## 1. 当前升级包（用户让抓的“完整固件包链接”）

来源：`/data/user_de/0/com.bbk.updater/shared_prefs/update_info.xml` 的
`passivity_package_info`（JSON），快照在 `work/os4-re/update_info.xml`、`work/os4-re/current_pkg.json`。

| 字段 | 值 |
|---|---|
| version | `15.2.19.1.W10.V000L1` |
| type | `full`（UI 显示“升级包大小 8.05GB”） |
| fileName | `202610262028094ad48fade6c32367fe1dbdc1f86c02f7.zip` |
| fileLength | `8053346114` |
| sha256 | `01f02afc7e15272dcd1f1e10f150ca506a5177b10061f5d774dc5d74716f2589` |
| downloadURL | `https://sysupgrade.vivo.com.cn/pk/redir.do?name=%40a152712%3D%3D13oomKrZnN8NnM5ZiNXCnZlESYncXMyJyNXYSYlEnY5DQYgCS08hXN8JXMz1SNdJQNzjz3kwibnxTakxdK26&fileType=relative&fileLength=8053346114&packageType=patch` |
| logUrl | `https://sysdesc.vivo.com.cn/upgrade/h5/2026/11/202611091640111625756-2/index.html` |
| logZip | `https://sysdesc.vivo.com.cn/upgrade/h5/2026/11/202611091640111625756-2.zip` |
| logZipSha256 | `049cb049b492f88f4dded16f4119582aa659aaabd4ebe381e959e6cc621cbf66` |
| otaType | `AB` |

注意：`redir.do` 用裸 curl（HEAD/GET、各种 UA/referer、POST、`packageType=patch/full`）都返回
**HTTP 500**，说明它绑定升级器会话/签名，不能离线直接还原 `sysuptxdl...zip?sign=...&t=...` 直链。
上一版（15.2.18.0）的直链留在 `updater.xml` 的 `current_download_real_url`，仅作格式参考：

```
https://sysuptxdl.vivo.com.cn/upgrade/oem/files/2026061714121983b3724d964f5395020297dc766aa7b6.zip?sign=d694a408cbc08333c49cbd82d7fc3edd&t=6b01596e
```

下载记录库 `databases/downloads.db` 的 `downloads` 表当前为空（还没真正开始下载）。

---

## 2. 下载/安装组织

- 升级信息由 `com.bbk.updater` 自己保存并解析；A/B 设备（`n0.a.u()` 读
  `ro.virtual_ab.enabled`）走 `UpdateEngine.applyPayload()`（`UpdateManager.startAbInstallActual`），
  UI/策略层的安装策略是 `INSTALL_NOW_ACTIVITY`（`updater.xml:install_strategy`）。
- 下载成功回调 `BaseUpdateStrategy.onDownloadSucceed(...)`。
- 安装成功后 `onInstallPackageSucceed(...)`，随后由 `l0` 策略层决定弹重启对话框/通知。
- 本地升级/异常路径另有 `UpdateReceiver` 的 `RecoverySystem.installPackage(context, file)`
  （进 recovery，会重启）。

---

## 3. 自动重启链路（带文件行号）

1. **重启对话框 / 倒计时开关**
   `com/bbk/updater/utils/PopDialogUtils.java:1334`
   ```java
   public static void showRebootInduceDialog(Context context, String str, String str2,
                                             String str3, boolean z5, boolean z6)
   ```
   `z5` 为是否倒计时。`z5=true` 时（约 1470 行）调用
   `countdownDialog(context, mCountRebootDialog, normalContentView, true, R$string.now_reboot, 112, str3)`。

2. **10 秒倒计时**
   `PopDialogUtils.countdownDialog(...)`（约 455 行）：
   ```java
   new CountDownTimer(11000L, 1000L) { ... onFinish() {
       RxBus.a().c(new DialogEvent(13, ConstantsUtils.DialogType.COUNTDOWN_AB_REBOOT_INDUCE, str));
       dialog.dismiss();
   } }
   ```
   （另有 `countdown(...)` 用于安装倒计时，`DialogType` 为 `COUNTDOWN_AB_REBOOT_INDUCE` 时同样 13。）

3. **事件分发**
   `com/bbk/updater/install/strategy/BaseUpdateStrategy.java:228 onDialogEvent(...)`：
   - `eventId == 13` 且 `COUNTDOWN_AB_REBOOT_INDUCE` → `n0.a.C(ctx, UPDATE_NOW_COUNT)`（约 267 行）
   - `eventId == 10`（正按钮“立即重启”）→ `n0.a.C(ctx, UPDATE_NOW_DIALOG)`（约 251 行）

4. **真正重启**
   `n0/a.java:163`：
   ```java
   public static void D(Context context, BaseFrameUtils.UpdateMethod updateMethod, boolean z5) {
       ThreadPoolUtils.submit(new RunnableC0119a(...));
   }
   ```
   `RunnableC0119a.run()`（`n0/a.java:50`）里：
   ```java
   LogUtils.d("Updater/InstallUtils", "rebootNow");
   ...
   PowerManager powerManager = (PowerManager) context.getSystemService("power");
   powerManager.reboot(z5 ? "silent" : "reboot-ab-update");
   ```

5. **按钮含义**（`PopDialogUtils.DialogButtonListener`）：
   - 正按钮（`now_reboot`）：`DialogEvent(10)` → 立即重启
   - 负按钮（“稍后”）：`DialogEvent(12)` → 不重启
   - 中按钮（“夜间重启”）：`SmartInstallEvent(6)` → 走夜间策略（最终仍会到 `n0.a.D`）

6. **定时/通知旁路**
   - `UpdateManager.setRebootInduceAlarmClock()`（`remote/abinstall/UpdateManager.java:495`）注册
     `com.vivo.updater.alarm.action.AB_REBOOT_COUNTDOWN` 闹钟；
     `BaseUpdateStrategy` 收到该 action 后调 `l0.a.b().g(ctx, true, z5)` 再弹倒计时对话框。
   - `UpdateTipsService$c`（`remote/UpdateTipsService.java:179`）是“安装于夜间”的
     `CountDownTimer`，`onFinish` 发 `INSTALLATION_METHOD_INSTALL_AT_NIGHT` 广播。
   - `CommonUtils.shutdown(Context)`（`utils/CommonUtils.java:2484`）发
     `com.android.internal.intent.action.REQUEST_SHUTDOWN`。
   - `UpdateReceiver` 的 `RecoverySystem.installPackage(context, file)`（约 89 行）。
   - `InstallService` 的 4 处 `CommonUtils.shutdown` 与 `ShutdownAndUpdateStrategy` 的 2 处。

---

## 4. 模块防护实现

新增 `module/src/com/betteroriginos/feature/updater/UpdaterGuardFeature.java`
（id `updater_guard`，默认开，scope `com.bbk.updater` + `com.vivo.cota`）。

hook 点（实测本 build 全部命中）：

| 目标 | 动作 | 安装读数 |
|---|---|---|
| `n0.a.D(Context, UpdateMethod, boolean)` | 直接返回，阻止 reboot 线程 | `n0.a.D=1` |
| `android.os.PowerManager.reboot(...)` | 直接返回（最底层护栏） | 计入 total |
| `android.os.RecoverySystem.installPackage(...)` | 直接返回（不进 recovery） | 计入 total |
| `PopDialogUtils.showRebootInduceDialog(...)` | 把第 5 个参数 `z5` 改成 false | `rebootDialog=1` |
| `PopDialogUtils.countdownDialog(...)` | 直接返回（兜底） | `countdownDialog=1` |
| `PopDialogUtils.countdown(...)` | 仅 `DialogType` 含 `AB_REBOOT` 时返回 | `countdown=1` |
| `CommonUtils.shutdown(Context)` | 直接返回（不关机） | `shutdown=1` |
| `UpdateTipsService$c.onTick/onFinish` | 冻结/屏蔽夜间倒计时 | `nightTimer=2` |
| `UpdateManager.setRebootInduceAlarmClock(...)` | 直接返回（不设定时闹钟） | `rebootAlarm=1` |
| `CommonUtils.getUpdatePackageDirectory(ctx, task)` | `ota_pacakge`/`vgc_package` 返回 `Download` 目录 | `downloadDir=1` |
| `CommonUtils.startInstall(Context, String)` | 直接返回，不启动 InstallService | `startInstall=1` |
| `DownloadReceiver.onReceive(...)` | 下载状态成功后通知包路径 | `downloadNotify=1` |

装机日志（2026-11-12 22:15，包 328,254 B）：

```
I/BetterOriginOS: loaded com.bbk.updater/com.bbk.updater
I/BetterOriginOS: updater guard installed pkg=com.bbk.updater hooks=14 by
  n0.a.D=1 rebootDialog=1 countdownDialog=1 countdown=1 shutdown=1 nightTimer=2
  rebootAlarm=1 downloadDir=1 startInstall=1 downloadNotify=1
I/BetterOriginOS: updater guard enabled=1
```

LSPosed scopes 已加：`com.bbk.updater/0`、`com.vivo.cota/0`。

**效果**：倒计时被强制关掉；即使倒计时/按钮继续走到 `n0.a.C`，`n0.a.D` 与
`PowerManager.reboot` 也会拦下，不会重启；关机/进 recovery 也拦。

### 4.1 只下载不安装（2026-11-12 追加）

用户要求：点“下载并安装”只把包下下来，不自动安装；安装手动；下载路径通知出来，默认放 Download。

- **下载目录**：`UpdateDownloadManager` 初始化时用
  `CommonUtils.getUpdatePackageDirectory(ctx, "ota_pacakge")`（`UpdateDownloadManager.java:697`）。
  A/B 设备（`n0.a.u()`）的默认目录是 `/data/ota_package/OTAPackage`（`UPDATER_DIRECTORY_AB`）。
  模块 hook `getUpdatePackageDirectory`，在 `"ota_pacakge"`/`"vgc_package"` 时返回
  `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`，即 `/storage/emulated/0/Download`。
  下载器直接把包写进 Download，不需要下载后再复制 8GB。
- **不安装**：`CommonUtils.startInstall(Context,String)`（`CommonUtils.java:2509`）是启动
  `InstallService` 的唯一入口；hook 后直接 return，自动/隐私条款/手动点击都进不了安装服务。
- **通知路径**：hook `DownloadReceiver.onReceive`，收到
  `com.vivo.updater.download.status.change` 且 `task=ota_pacakge/vgc_package` 后延迟 1.5s，
  从 `UpdateDownloadManager.getDownloadInfo(task)` 取 `fileName`，拼 Download 路径；
  文件存在且非空则发通知「升级包已下载」+ 完整路径（失败回退 Toast）。
- **开关**：`UpdaterGuardFeature` 的 option `download_only`（默认开）。
  关闭后需重启 updater（`UpdateDownloadManager` 只在进程启动时读一次下载目录）。
- **日志**：`updater guard download dir=...`、`updater guard blocked startInstall`、
  `updater guard download done path=...`。
- **真实下载验证（2026-11-12 22:21，用户点击“下载并安装”）**：
  - 文件 `/storage/emulated/0/Download/202610262028094ad48fade6c32367fe1dbdc1f86c02f7.zip`
  - 大小 `8053346114` B，与 `fileLength` 完全一致
  - SHA-256 `01f02afc7e15272dcd1f1e10f150ca506a5177b10061f5d774dc5d74716f2589`，
    与 `update_info.xml` 里的 `sha256` 完全一致（完整包）
  - 同目录有 `...zip.temp`（0 B，下载器残留，可删）
  - updater 日志：`blocked startInstall` + `download done path=...`，没有进入 InstallService/重启
  - 结论：**只下载不安装 + Download 路径 + 完成通知这条链路已闭环**
- **第三方助手手动安装时的共享引擎现象（2026-11-12 22:24–22:32）**：
  - `com.moran.updater` 把安装脚本放到 `/data/ota_package/OTA/instpayload.sh`，
    由 root shell 启动 `update_engine_client2 --payload=file:///storage/emulated/0/Download/...zip --follow --update ...`；
    脚本里**没有 reboot**。
  - `com.bbk.updater` 的 `UpdateManager` 构造时就 `bind()` 系统 `UpdateEngine` 并注册
    `VUpdateEngineCallback`；它会收到**任何客户端**的 `onStatusUpdate`。
    系统升级页于是显示了第三方这次安装的进度（不是第二个安装器）。
  - `checkIfUndoUpdate()` 只在 updater 自己的 `resetAbInstall`/`startAbInstall` 路径调用，
    第三方会话不会触发；日志也没有 cancel/reset。
  - **但**：为加载“防删除”新 hook 而 `am force-stop com.bbk.updater` 时，
    第三方引擎在 62.5% 被取消（`onPayloadApplicationComplete(kUserCanceled 48)`）。
    当前系统仍是旧版 `15.2.18.0`、slot `_b`，安装包完整，root 未受影响。
    教训：**第三方安装进行中不要重启/force-stop updater**。
- **防删除保护（keep-package）**：`IOUtils.deleteFileImpl` 是递归删除；updater 的
  `checkIfNeedToDeletePackageAfterUpdate()` 会对下载目录调 `g0.a.h()/m()`，
  而下载目录已被改到整个 Download。新增 hook：`DownloadStrategy.checkIfNeedToDeletePackageAfterUpdate`、
  `clearDownloadPackage`、`IOUtils.deleteFile/deleteFileImpl`、`g0.a.n/h/m`，
  保护 Download 目录与包；实测日志 `updater guard blocked deleteDir`。
  装机后总 hook 数 `hooks=22`（新增 `keepCleanup=2 keepIo=3 keepManager=3`）。

---

## 5. 未闭环 / 下一步

- 当前升级器已显示“系统已经是最新版本”（15.2.18.0），没有真实下载完成的包，
  所以**“下载完自动弹重启对话框”这条端到端还没在真机上走完**；
  已通过 hook 安装 + `enabled=1` 验证，但没点过“立即重启”做破坏性验证（防掉 root）。
- **只下载不安装也还没做一次真实大包下载验证**（8.05GB）。真实点击“下载并安装”后应确认：
  ① 文件落在 `/storage/emulated/0/Download/<fileName>`；
  ② 出现“升级包已下载”通知并带完整路径；
  ③ 日志 `blocked startInstall` 且没有 InstallService/重启；
  ④ 升级器没有把包删掉。若 Download 目录写入被 FUSE 拒绝，回退方案是下到
  `/data/ota_package` 再用 root 复制/硬链到 Download（未实现）。
- 真机验证建议：等下一次有更新包时，下载/安装完成后观察：
  1. 对话框是否出现、但没有秒数倒计时；
  2. 点“立即重启”是否只打日志 `updater guard blocked n0.a.D` / `PowerManager.reboot` 且设备不重启；
  3. 点“稍后”是否正常关闭。
- `com.vivo.cota` 只加了 PowerManager/RecoverySystem 两层；如果实际安装由 COTA 的
  私有服务重启，需要再逆 COTA 的对应方法。
- `n0.a` 是混淆名，升级器版本变化后可能失效；失败时日志会显示
  `n0.a=missing`，但 PowerManager/RecoverySystem 护栏仍在。
- 若用户想保留“夜间自动安装”但只禁止立即重启，可把 `nightTimer` 两个 hook 去掉。

---

## 6. 素材与命令

```
work/os4-re/updater.apk                     # /system/app/Updater/Updater.apk
work/os4-re/updater-dex/classes*.dex
work/os4-re/updater-src/sources/            # jadx 输出
work/os4-re/updater-res.txt                 # aapt2 dump resources（中文文案/id）
work/os4-re/update_info.xml                 # 当前升级信息快照
work/os4-re/current_pkg.json                # 解析后的包信息
work/os4-re/downloads.db                    # 下载记录（当前为空）
work/os4-re/upd.xml / upd2.xml / upd3.xml   # 升级页 UI dump
```

```bash
# 反编译
java -jar /c/vivores-build-v303/apktool.jar d -f -o <out> <apk>
# 或 jadx（本包可用）
java -cp jadx-all.jar jadx.cli.JadxCLI --no-res -d <out> classes.dex classes2.dex
# 触发/查看
adb shell 'su -c "cat /data/user_de/0/com.bbk.updater/shared_prefs/update_info.xml"'
adb shell 'am broadcast -a com.vivo.updater.alarm.action.AB_REBOOT_COUNTDOWN -p com.bbk.updater'
```

## 7. 纪律提醒

- 这是**破坏性动作的高危区**：任何“点一下重启按钮验证 hook”都要先确认 hook 日志已出现；
  未确认前不要点正按钮。
- `PowerManager.reboot` 在 updater/cota 进程里被全局拦截是刻意为之；如果以后要允许
  手动重启，需要给 feature 加选项或先关掉 feature 再重启。

## 8. OTA 后 LSPosed 模块未加载 / 调试模式（2026-11-13，OS 15.2.19.1）

- OTA 后 root 从 KernelSU 换成 Magisk，LSPosed 2.2.1 在 Magisk 下运行；
  `modules_config.db`（含 WAL）里模块 enabled、scope 六项齐全、APK 路径与 `pm path` 一致。
- 但 updater/filemanager 进程没有 `loaded com.*` 模块日志，updater guard 心跳也不出现；
  SystemUI 的模块按用户观察正常。说明 **lspd 内存里的模块状态没有随 OTA/scope 更新重载**，
  只对已有/部分进程生效。
- 修法：`adb root` 后执行
  ```
  /data/adb/modules/zygisk_lsposed/lspctl module disable --user=0 com.betteroriginos
  /data/adb/modules/zygisk_lsposed/lspctl module enable  --user=0 com.betteroriginos
  ```
  再 force-stop/start updater。实测恢复：
  `updater guard installed hooks=22` + `updater guard alive pid=… hooks=22 enabled=1 blocks=0`。
- 注意：`lspctl` 在 Magisk 模块目录，不在 PATH；scope/module 修改要求 **ADB root shell**
  （production build 需要先 `adb root` 成功），`su -c` 会被拒。
- **调试模式**：模块信息页新增「调试」分组 →「调试模式」开关，键
  `Settings.Global betteroriginos_debug`；打开后 updater guard 每 60 秒打一条
  `updater guard alive pid=… hooks=… enabled=… blocks=…`，关闭后 2 秒内静默。
  验证完已置 0。
