# Z8 / OriginOS 3 (Android 13, sdk 33) — PickerAlbums 与 FileManagerRename 可达性核对

取证设备：iQOO Z8 `10AE4Y0MDE000BL`（V2314A），`ro.build.version.sdk=33`、`release=13`、`ro.vivo.os.version=13.5`，当前 user=0。
方法：真机 `pm path` / `dumpsys package` → pull APK → `dexdump -f` 出成员清单 → jadx 反编译关键类 → 逐成员复核 + 只读 `content query`。
全程只读，未 reboot、未 kill systemui、未改模块源码。

产物路径（全部绝对路径）：
- APK：`G:\z8os3-ref\Z8OS3-MediaProvider.apk`（7.7MB）、`G:\z8os3-ref\Z8OS3-FileManager.apk`（17MB）
- dexdump：`G:\z8os3-ref\z8os3-mediaprovider-classes.txt`、`G:\z8os3-ref\z8os3-filemanager-classes.txt`（FileManager 含 classes.dex+classes2.dex）
- jadx：`G:\z8os3-ref\jadx\ExternalDbFacade.java`
- 局部块：`G:\z8os3-ref\ExternalDbFacade.block.txt`、`G:\z8os3-ref\DialogNameEntry.block.txt`

---

## 功能一：PickerAlbumsFeature（scope `com.android.providers.media.module`）

### OS3 侧包与 APK
- 包存在：`com.android.providers.media.module` ✓
- 路径：`/apex/com.android.mediaprovider/priv-app/MediaProvider@TP1A.220624.014/MediaProvider.apk`（单 APK，APEX 内，非 split）
- 版本：`versionName=13`，`versionCode=33`，`targetSdk=33`（A13 TP1A 原版 MediaProvider）

### 逐 hook 目标核对

| Hook 目标（源码） | OS3 是否存在 | 证据 | 备注 |
|---|---|---|---|
| `findClass ExternalDbFacade`（FACADE, L32/70） | 存在 | dexdump `Class #1191`（行169557）；jadx 类头 | |
| `SQLiteQueryBuilder`（framework, L71） | 存在（框架） | ExternalDbFacade 自身大量使用 | 运行时框架提供 |
| `findAndHookMethod(facade,"queryAlbums", String[].class)`（L74） | **不存在该签名** | dexdump 行170152 `queryAlbums(Ljava/lang/String;)Landroid/database/Cursor;`；行170335 另一重载 `queryAlbums(String,UserId)` | A13 无 `String[]` 重载 → **抛 NoSuchMethodError** |
| `hookAllMethods(facade,"appendWhere")`（L86） | 存在，但签名不同 | dexdump 行169733 `(SQLiteQueryBuilder;String;String;)Ljava/util/List;`（PRIVATE STATIC） | 第 3 参是单个 `String`（mime），非 `String[]`；源码 L103 `(String[])param.args[2]` → **ClassCastException** |
| `getObjectField(facade,"mDatabaseHelper")`（L118） | 存在 | dexdump 行169613 类型 `Lcom/android/providers/media/DatabaseHelper;`；jadx L34 | |
| `getStaticObjectField(...,"TABLE_FILES")`（L120） | 存在，值 `"files"` | dexdump 行169603-169606；jadx L32 | |
| `setStaticObjectField(...,"LOCAL_ALBUM_IDS", String[])`（L162） | 存在，类型 `[Ljava/lang/String;` | dexdump 行169568；jadx L29/46 `= {"Camera","Screenshots","Downloads"}` | **白名单前提在 A13 依然成立**：queryAlbums 仍 `for(str2: LOCAL_ALBUM_IDS)`（jadx L245） |
| `callMethod(helper,"runWithoutTransaction",Function)`（L126） | 存在 | dexdump 行137402 `runWithoutTransaction(Ljava/util/function/Function;)Ljava/lang/Object;`（DatabaseHelper） | A13 自身多用 runWithTransaction，但两方法都在 |
| `callMethod(builder,"appendWhereStandalone",String)`（L102/182） | 存在（框架） | ExternalDbFacade 内大量调用 | |
| SQL 列：`relative_path / media_type / is_trashed / is_pending`、表 `files` | 全部存在 | jadx L310-312 原生就用 `media_type = 1 OR media_type = 3`、`is_trashed = 0`、`is_pending = 0`；实测见下 | 源码 `media_type IN (1,3)` 与原生一致 |

### 只读实测（步骤 F）
```
content query --uri content://media/external/file --projection _id:relative_path:media_type:is_trashed:is_pending
Row: 0 _id=1000000002, relative_path=NULL, media_type=NULL, is_trashed=0, is_pending=0
Row: 1 _id=1000000003, relative_path=/,    media_type=NULL, is_trashed=0, is_pending=0   ...（无匹配列报错）
对照组：--projection _id:bogus_col_xyz → IllegalArgumentException: Invalid column bogus_col_xyz
```
结论：四列 + `files` 表在 OS3 真实存在（对照组的报错证明查询通道有效）。当前设备媒体尚未扫描（media_type/relative_path 多为 NULL/"/"），但 schema 支持无疑。

### 判定：需要改代码，但**可达 / 可救**（改 2 处，非重写）
OS3 的 ExternalDbFacade 与 A15 是同一套设计（LOCAL_ALBUM_IDS 白名单 + appendWhere 三分支 switch + files 表列），补丁思路完全成立。A15→A13 只有签名差异：

1. **queryAlbums 挂不上（致命，且会连带 appendWhere 也不装）**：源码 L74 `findAndHookMethod(facade,"queryAlbums",String[].class,...)` 在 A13 抛 NoSuchMethodError；因为它与 appendWhere hook 在同一个 try 块（L68-113），异常一抛，后面 appendWhere 的注册也执行不到，整个功能不加载。
   改法：把 L74 的 `XposedHelpers.findAndHookMethod(facade,"queryAlbums",String[].class,hook)` 改为 `XposedBridge.hookAllMethods(facade,"queryAlbums",hook)`（beforeHook 只调 `refreshAlbumIds(param.thisObject)`，与参数无关，能同时覆盖 `(String)` 与 `(String,UserId)` 两个重载）。
2. **appendWhere 的 mime 参数类型错**：A13 第 3 参是单个 `String`（jadx L274 `appendWhere(SQLiteQueryBuilder builder,String albumName,String mimeType)`；L276-278 用 `mime_type LIKE ?`+replaceMatchAnyChar），源码 L103 `(String[])param.args[2]` → CCE。
   改法：把 L103-104 与 `appendMimeTypes(String[])`（L169-183）改成读**单个 String**：`Object m = param.args.length>2?param.args[2]:null; if(m instanceof String){ callMethod(builder,"appendWhereStandalone","mime_type LIKE "+sqlEscapeString(((String)m).replace("*","%"))); }`。args[0]=builder、args[1]=albumName(String) 均与源码一致，无需动。

> 注：`refreshAlbumIds`（L117-166）依赖的 mDatabaseHelper / TABLE_FILES / LOCAL_ALBUM_IDS / runWithoutTransaction / SQL 列在 OS3 全部命中，无需改。

---

## 功能二：FileManagerRenameFeature（scope `com.android.filemanager`）

### OS3 侧包与 APK
- 包存在：`com.android.filemanager` ✓（scope 对得上，就是 vivo 文件管理）
- 路径：`/system/app/BBKFileManager/BBKFileManager.apk`（单 APK，2 个 dex：classes.dex + classes2.dex）
- 版本：`versionName=6.11.4.4`，`versionCode=61104004`，`minSdk=26 targetSdk=31`（与 OS5 上是**不同版本**的 App）

### 逐 hook 目标核对

| Hook 目标（源码） | OS3 是否存在 | 证据 |
|---|---|---|
| `findClass ENTRY = ...DialogNameEntry`（L25/61） | 存在 | dexdump 行416479 `Class descriptor 'Lcom/android/filemanager/view/dialog/DialogNameEntry;'` |
| `findClass RENAME = ...RenameDialogFragment`（L26/62） | **不存在** | 全 dex `Class descriptor` 无 `RenameDialogFragment`；`[Rr]ename` 类描述符 0 命中（A13 把它混淆成字母类） |
| `findClass LIMIT_CLS = ...view.dialog.t`（L27/63） | 同名存在但**不是同一个类** | dexdump 类 `t` 块 951420..951460，仅字段 `a` + 方法 `<init>`/`a`，与 A15 的 `t`（放 56 限制）无关 |
| `findClass ...smb.bean.ShareFile`（L64） | **不存在** | `Lcom/android/filemanager/smb/` 类描述符 0；无任何 `ShareFile` 类（smb 包整体不在） |
| `findClass ...originui.widget.edittext.VEditText`（L69） | **不存在** | 全 dex（大小写不敏感）无 `VEDitText`；`com/originui/` 仅有 `core/*` 与 `widget/tabs/*`（41 类），无 `widget/edittext` 包 |
| `findClass android.text.InputFilter$LengthFilter`（L71） | 存在（框架） | 但够不到（前面 findClass 已抛） |
| `hookAllMethods(entry,"L")`（L89） | **不存在** | DialogNameEntry 块内无 `name:'L'`；其方法名已重混淆为 a–r + 真名 getter |
| `hookAllMethods(rename,"x2")` / `callMethod(this,"N2",...)`（L99/130） | **不存在** | 宿主类 RenameDialogFragment 即不存在 |
| `setStaticObjectField(t,"f11819f",…)`（L92） | **不存在** | 全 dex `f11819f` 0 命中 |
| `getObjectField(rename,"f11309e"/"f11306b"/"f11314j")`（L103/111/122） | **不存在** | 三个混淆字段名全 dex 各 0 命中 |
| `callMethod(dialogEntry,"getEditText")`（L116） | 存在 | DialogNameEntry 块 `getEditText()Landroid/widget/EditText;`（真名方法，稳定） |

### 根因
OS3 的文件管理是 **6.11.4.4** 独立版本，R8 重新混淆：A15 版里作为 hook 锚点的一切混淆符号（类 `RenameDialogFragment`/`ShareFile`、方法 `L`/`x2`/`N2`、字段 `f11819f`/`f11309e`/`f11306b`/`f11314j`）在 OS3 全部对不上；`VEditText`（originui edittext 组件）在该 APK 里根本没打包。
`install()` 执行到 **L62 `findClass(RENAME)`** 即抛 `NoSuchClassError`（在 try 块 L60-140 内），整功能不加载——后面的 `t.f11819f`、`entry.L`、`rename.x2/N2` 全无从谈起。仅 `DialogNameEntry` 与 `getEditText()` 两个真名稳定，但不足以支撑补丁。

### 判定：**基本不可直接迁移，需要重做（不是改几行）**
"56→85 字符"的思路（ext4 255 字节 ÷ 3）可复用，但所有 hook 符号要针对 6.11.4.4 重新取证：
1. 用 jadx 反编译 `DialogNameEntry` 及其真名方法，定位预填充截断逻辑（A15 的 `L`），拿到 A13 里它现在的真/混淆方法名；
2. 定位承载"重命名/新建"对话框的 Fragment（A15 的 `RenameDialogFragment`）现在的混淆类名，以及"确定"按钮回调（`x2`）、重命名执行方法（`N2`）、持有的 File/dialogEntry/button 字段（`f11309e`/`f11306b`/`f11314j`）的新名字；
3. 定位 56 上限常量现在所在类/字段（A15 是 `view.dialog.t.f11819f`）；
4. 处理 `VEditText`：确认 A13 是否根本没有 com.originui.widget.edittext（多半用别的输入控件），去掉或替换掉"构造期挂 LengthFilter=85"那一段（L69-86）；
5. 处理 `ShareFile`（smb 包缺失）：去掉 L64/104 的 SMB 分支判断。
完成上述再落代码；在此之前该功能在 Z8/OS3 上应**保持关闭**。

---

## 汇总

- **PickerAlbums**：OS3 MediaProvider 的白名单机制（LOCAL_ALBUM_IDS + appendWhere + files 列）**完整存在且思路成立**，只是两处签名随版本变了（queryAlbums 无 String[] 重载、appendWhere 第 3 参变单 String）。**改 2 处即可在 OS3 生效**（见功能一判定）。
- **FileManagerRename**：OS3 文件管理是另一版本、混淆符号全换、`RenameDialogFragment`/`ShareFile`/`VEditText` 三个类在 APK 中不存在，install 立刻抛 NoSuchClassError。**无法用改动救，需对 6.11.4.4 重新逆向后再实现**；暂不适用 OS3。
