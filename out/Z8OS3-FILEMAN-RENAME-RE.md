# Z8 / OriginOS 3（Android 13，vivo 文件管理 6.11.4.4）重命名对话框逆向取证

**性质**：纯静态逆向（APK + dexdump + jadx + aapt2）。**未修改任何模块源码，未使用 adb/真机，未 reboot。**

产物路径（全部绝对路径）：
- APK：`G:\z8os3-ref\Z8OS3-FileManager.apk`（17MB，`classes.dex` + `classes2.dex`；任务里写的 `BBKFileManager.apk` 在本目录不存在，实际文件名是这个）
- dex 目录：`G:\z8os3-ref\fm_dex\classes.dex`、`G:\z8os3-ref\fm_dex\classes2.dex`
- 类/成员清单（dexdump -f）：`G:\z8os3-ref\z8os3-filemanager-classes.txt`（9319 个 Class descriptor）
- 整包反编译（jadx 1.5.5，`--no-imports`）：`G:\z8os3-ref\jadx\full\sources\...`，资源解码在 `G:\z8os3-ref\jadx\full\resources\...`
- 单类反编译（`--show-bad-code`，用于还原 jadx 拒绝解码的 TextWatcher）：`G:\z8os3-ref\jadx\DialogNameEntry.java`
- 既有 dexdump 局部块：`G:\z8os3-ref\DialogNameEntry.block.txt`；本次新增：`G:\z8os3-ref\f2.block.txt`、`G:\z8os3-ref\a1.block.txt`、`G:\z8os3-ref\f2.members.txt`
- aapt2 资源全量 dump：`G:\z8os3-ref\resources.dump.txt`（47474 行，工具 `C:\Users\Administrator\Desktop\Aiqooz8-os5-mod\work\sdk-dl\bt35\android-15\aapt2.exe`）

> 关键突破点：jadx 保留了 source_file 属性（`compiled from:` 注释），**混淆类 `f2` 的源文件名就是 `RenameDialogFragment.java`**，直接锁定目标，无需靠字符串链猜。

---

## 一、结论：**判定 (A) —— OS3 存在完全等价的实现，符号已全部找到**

A15 侧那套补丁的思路（把 56 提到 85 + 绕过"确定"里硬编码的 56 拒绝 + 直接调私有重命名方法）在 OS3 **逐点都有对应符号**，只是名字变了、两处锚点（`VEditText`、`ShareFile`）需要删除。

### 1.1 核心符号映射表

| 作用 | A15（现源码里的锚点） | OS3 6.11.4.4 等价符号（**dex 运行时名**） | 证据 |
|---|---|---|---|
| 重命名对话框 Fragment | `...view.dialog.RenameDialogFragment` | **`com.android.filemanager.view.dialog.f2`**（source `RenameDialogFragment.java`，`classes.dex`） | `jadx\full\sources\com\android\filemanager\view\dialog\f2.java:3-5`：`compiled from: RenameDialogFragment.java`；dexdump 块 `G:\z8os3-ref\f2.block.txt`（行 1268014 起） |
| 其基类（承载字段/按钮流程） | 同一个类里 | **`com.android.filemanager.view.dialog.a1`**（source `BaseNameEntryDialogFragment.java`） | `a1.java:3-5`；dexdump 块 `G:\z8os3-ref\a1.block.txt` |
| 名称输入控件容器 | `...view.dialog.DialogNameEntry` | 同名，未混淆 ✓ | `DialogNameEntry.block.txt:2`；`jadx\full\sources\...\DialogNameEntry.java` |
| 逐字截断方法（A15 的 `L(String)`） | `DialogNameEntry.L` | **`DialogNameEntry.a(Ljava/lang/String;)Z`（private 实例）** | `a1`/`DialogNameEntry.block.txt`：`Direct 'a' (Ljava/lang/String;)Z`；实现体 `DialogNameEntry.java:388-444`（`while (length > l1.f5968d)` 逐字删除）；调用者是主输入框 TextWatcher：`jadx\DialogNameEntry.java:319/329/349`（内部类 `e.afterTextChanged`） |
| 长度上限存放处（A15 的 `t.f11819f`） | `view.dialog.t.f11819f`（static） | **`com.android.filemanager.view.dialog.l1.d`**（`PUBLIC STATIC int`，source `DialogHelper.java`，初值 -1，按场景置 56/52） | dexdump：`name 'd' / type 'I' / access 0x0009 (PUBLIC STATIC)`；赋值点 `l1.java:60,75,86,96,109` 与 `f2.java:48`；读取点 `DialogNameEntry.java:391,398` |
| 确定按钮回调（A15 的 `x2`） | `RenameDialogFragment.x2` | **`f2.x()V`（protected，覆盖 `a1.x()V`）** | `f2.java:55-89`；dexdump `f2` 块只有 `w()Z`、`x()V` 两个受保护方法 → 名字唯一，`hookAllMethods` 安全 |
| 真正执行重命名的私有方法（A15 的 `N2`） | `N2(File,String,String)` | **`f2.a(Ljava/io/File;Ljava/lang/String;Ljava/lang/String;)V`（private）** | `f2.java:99-117`（日志字面量 `"==renameFile==srcFile:"`、tag `"RenameDialogFragment"`）；底层是 `e1.n0.c(File,String)` + 媒体库更新 `f2.java:119-137` |
| 待重命名的 File（A15 的 `f11309e`） | `f11309e` | **`a1.e`（`protected java.io.File`）**，jadx 里显示为 `f5880e` | `a1.block.txt` 字段 `e`→`Ljava/io/File;`；`f2.java:38/58/77` 使用 |
| DialogNameEntry 引用（A15 的 `f11306b`） | `f11306b` | **`a1.a`（`protected DialogNameEntry`）**，jadx `f5877a` | `a1.block.txt` 字段 `a`→`Lcom/android/filemanager/view/dialog/DialogNameEntry;`；`f2.java:63/71/74` |
| 确定按钮字段（A15 的 `f11314j`） | `f11314j` | **`a1.k`（`protected android.widget.Button`）** —— 但 **OS3 里从不赋值，恒为 null**（无效字段） | `grep -rn "\.k = \|this\.k"` 只有 3 处读取：`f2.java:67,85,140`，全 APK 无写入；真正的按钮是 `((AlertDialog)getDialog()).getButton(-1)`（`a1.java:341-344` `z()` 里 `setOnClickListener(a1$e)`，`a1$e.onClick`→`A()` 后 `x()`） |
| 当前名称来源 | — | **`a1.i`（`protected String`）** 与 `a1.d`，在 `f2.w()` 里由 `this.f5880e.getName()` 赋值；预填充在 `a1.onCreateDialog` → `getEditText().setText(this.i)`（`a1.java:262-271`） | `f2.java:49-51`；扩展名相关：`x0.i(name)` 取扩展名、`a1.java:275-276` 只选中主名不选扩展名 |

### 1.2 调用链（证明 f2 就是"重命名"弹窗，不是别的）

```
FileUtils 菜单/长按 → BaseOperatePresent.java:1307 / :1388
    r0Var.showRenameDialogFragment(file, new BaseOperatePresent.n()/o(...))
接口： view/baseoperate/r0.java:49   void showRenameDialogFragment(File, f2$a)
实现： view/baseoperate/m0.java:1588  → k1.a(getFragmentManager(), file, aVar)
工厂： view/dialog/k1.java:455-475   （source DialogFactory.java，日志 "======showRenameDialogFragment====="）
        f2.a(file)  // newInstance，Bundle key "select_file"（Serializable File）
        fragmentManager.c("RenameDialogFragment") / a(fm, f2Var, "RenameDialogFragment")
```
→ **运行时 Fragment tag 字符串 `"RenameDialogFragment"` 在 OS3 里保留**，可作为额外探测/定位手段。

### 1.3 OS3 `f2.x()`（确定）原始逻辑（等价于 A15 的 `x2`）

`jadx\full\sources\com\android\filemanager\view\dialog\f2.java:56-89` 关键行为：
1. `File` 为空/不存在 → dismiss；
2. `this.k`（Button，实际恒 null）`setClickable(false)`；
3. 取 `this.f5877a.getEditText().getText().toString().trim()`；
4. **硬编码 `if (strTrim.length() > 56)` → `errorFileNameTooLong` 红字提示后 return**（正是 A15 里同一处的硬拒绝，字面量 56 必须绕过）；
5. `parent + "/" + name` 组装 dest，非法字符走 `e1.w.a/w.c` 判定；
6. 否则调私有 `a(f5880e, str, strTrim)`（=A15 的 `N2`）异步执行重命名。

`DialogNameEntry` 的截断（等价 A15 的 `L`）：`DialogNameEntry.java:388-444`，循环条件直接读静态 `l1.d`，并在超限时弹 `errorFileNameTooLong`、**按字符逐个删除**（含按光标位置、按扩展名边界 `x0.i/x0.m` 的智能删除）。预填充长名在 `e.afterTextChanged` 里就会被它截掉——与 A15 现象完全一致。

---

## 二、字符串资源反查（问题 2）

`aapt2 dump resources` → `G:\z8os3-ref\resources.dump.txt`，命中的相关 id（`grep -nE "resource 0x[0-9a-f]+ string/(...)$"`）：

```
14439: resource 0x7f0f0002 string/Error_Search_Lenth_Limited
19848: resource 0x7f0f00bb string/dialogNameEntry_entryNewName
20003: resource 0x7f0f00c0 string/dialogNameEntry_errorNameHasUse
21679: resource 0x7f0f00f7 string/errorFileNameTooLong
22791: resource 0x7f0f011b string/fileManager_contextMenu_rename
```

解码后的文本（`jadx\full\resources\res\values\strings.xml`、`values-zh-rCN\strings.xml`）：
```
strings.xml:190   dialogNameEntry_entryNewName = "Enter new name"
strings.xml:250   errorFileNameTooLong          = "Max character limit reached"
strings.xml:286   fileManager_contextMenu_rename= "Rename"
strings.xml:5     Error_Search_Lenth_Limited    = "Max character limit reached"
values-zh-rCN/strings.xml:274  fileManager_contextMenu_rename = 重命名
```

用资源 id 名回查引用者（jadx 源码里以 `com.vivo.upgradelibrary.R.string.*` 形式出现）：
- `errorFileNameTooLong` → `f2.java:74`（确定时超限拒绝）、`a1.java:201`（密码框 >56）、`DialogNameEntry.java:396`（截断时 Toast）
- `fileManager_contextMenu_rename` → `f2.java:47`（`this.g` = 对话框标题）**← 这条就是"字符串资源 → 类"的反查闭环，独立确认 f2 是重命名弹窗**
- `dialogNameEntry_entryNewName` → `f2.java:46`（`this.h` 提示文案）、`a1.java:252`
- `Error_Search_Lenth_Limited` → `DialogNameEntry.java:156,161`（内部类 `d.a` = 压缩/密码框 watcher）

**"扩展名/显示文件扩展名"类字符串在 OS3 里 0 命中**：`grep -c 扩展名 resources.dump.txt` = 0、`grep -c 后缀` = 0；`string/*[Ee]xtension*|*suffix*` 只有 `fileSizeSuffix*`、`fileTypeSuffix_*`（单位/类型标签，与重命名无关）。→ OS3 的重命名框是**单输入框含扩展名**（仅默认只选中主名，`a1.java:272-285`），没有"显示/修改扩展名"的独立开关 UI。

布局与控件（`jadx\full\resources\res\layout\dialog_name_entry.xml`）：
```
27-39:  <EditText ... android:id="@+id/fileName_editText" android:singleLine="true"
                            style="@style/Widget.Vigour.EditText.Bold"/>   ← 无 android:maxLength
96-114: <EditText android:id="@+id/filepassword_editText" ... />           ← 无 maxLength
```
`styles.xml:3846-3855`（`Widget.Vigour.EditText` / `.Bold`）也无 `android:maxLength`；全 APK `res/values/*.xml` 里 `maxLength` 只有 1 处、值 2（无关样式）。
`grep -rn "LengthFilter\|setFilters" sources/com/android/filemanager/` → **0 命中**。
→ A15 里"布局给 VEditText 挂了 maxLength=56，所以要在构造期换 filters"的前提（源码 L67-86）**在 OS3 不成立**：OS3 的长度限制 100% 在代码里（`l1.d` + `f2.x()` 字面量 56）。

---

## 三、源码改造建议（只给建议，未落代码）

针对 `module/src/com/betteroriginos/feature/fileman/FileManagerRenameFeature.java`（行号按当前 156 行版本）：

| 行 | 现在（A15） | OS3 应换成 | 说明 |
|---|---|---|---|
| L25 `ENTRY` | `...view.dialog.DialogNameEntry` | **不变** | 两侧同名未混淆 |
| L26 `RENAME` | `...view.dialog.RenameDialogFragment` | OS3 profile：`com.android.filemanager.view.dialog.f2` | 需要按 profile 分支（见 L159 探测建议） |
| L27-28 `LIMIT_CLS`/`LIMIT_FIELD` | `view.dialog.t` / `f11819f` | OS3：`com.android.filemanager.view.dialog.l1` / `d`（PUBLIC STATIC int） | 类型是 `int`（A15 侧按对象字段 set）；OS3 建议用 `XposedHelpers.setStaticIntField` |
| L64-65 `findClass(...smb.bean.ShareFile)` | 抛 NoSuchClass | **OS3 profile 里删除这一步** | smb 包整体不在（见第四节） |
| L69-86 `VEditText` 构造 hook | 抛 NoSuchClass | **OS3 profile 里整段删除** | 布局无 maxLength、全包无 LengthFilter/setFilters；且 APK 里没有 `com.originui.widget.edittext` 包 |
| L89 `hookAllMethods(entry,"L")` | `L` 名字唯一 | OS3：`findMethodExact(DialogNameEntry, "a", String.class)` + `XposedBridge.hookMethod`，before 里 `setStaticIntField(l1,"d",85)` | **不能**用 `hookAllMethods(entry,"a")`：`DialogNameEntry` 里名为 `a` 的方法有 11 个（含 6 个 static synthetic 访问器），会全部命中 |
| L99 `hookAllMethods(rename,"x2")` | — | OS3：`hookAllMethods(f2, "x", hook)`（`f2` 只有 `w`/`x` 两个受保护方法，无重载） | before 里做 (56,85] 绕过 |
| L103 `"f11309e"` | File | OS3：`"e"` | 类型 `java.io.File` |
| L104 SMB 分支 `shareFile.isInstance(file)` | — | OS3：删掉该判断，只保留 `instanceof File` + `exists()` | 无 ShareFile |
| L111-112 `"f11306b"` | DialogNameEntry | OS3：`"a"` | 类型 `DialogNameEntry` |
| L116-117 `callMethod(dialogEntry,"getEditText")` | — | **不变** | 两侧真名，最稳 |
| L122-125 `"f11314j"` + `setClickable(false)` | — | OS3：`"k"`，但**该字段恒 null**（无写入点）；按钮置灰应改为 `((AlertDialog)callMethod(f2,"getDialog")).callMethod("getButton", -1).setClickable(false)` | 也可整段省略——紧接着就 `setResult(null)` 并走原流程，原流程自身会恢复可点 |
| L130 `callMethod(this,"N2", f, dest, name)` | — | OS3：同签名 3 参方法名是 `a`。**建议显式**`findMethodExact(f2, "a", File.class, String.class, String.class).invoke(...)`，别用 `callMethod(...,"a",...)`（`f2`/`a1` 里 `a` 重载极多，虽有 3 参 (File,String,String) 唯一，但按名匹配易踩 static synthetic 的 4 参版） | 参数顺序与 A15 一致：`(源 File, 目标全路径, 新名)` |
| L158-165（install 的 try 外层） | 单一硬编码 profile | 改为 profile 数组依次探测（见第五节） | 目前 OS3 在 L62 抛错后整功能静默关闭，无任何"为什么关闭"的日志 |

改造后 OS3 上的行为预期：预填充与输入不再被逐字删（`l1.d`=85），确定按钮对 57–85 字符的名字跳过原生硬拒绝、直接调用 `f2.a(File,String,String)` 走正常重命名（重名/非法字符错误仍由原回调 `f2.a(String,int[])` 用 `dialogNameEntry_errorNameHasUse` 等提示，`f2.java:139-208`）。85 这个值与 A15 同理（ext4 255 字节 ÷ 3 字节/CJK）。

⚠️ 顺带提示：`l1.d` 是**所有**名称输入弹窗共用的静态上限（`f1/g1/g2/i1/q2/r2/t1/f1/pdf.view.m` 等都写 56，读只在 `DialogNameEntry` 与 `CreateLabelDialogView`）。因为每个弹窗创建时都会重新赋值，且我们在 `DialogNameEntry.a(String)` 的 before 里临时抬到 85，作用域与 A15 的 `t.f11819f` 方案等价，不会串场景。

**另一处可一并覆盖的目标（如果要做全）**：`com.android.filemanager.pdf.view.m`（source `PdfRenameDialogFragment.java`，同样 `extends a1`、同样 `l1.d=56` + `> 56` 硬拒绝，见 `pdf\view\m.java:46,67`）与 `view.dialog.f1`/`b1`/`y0`（同为 `a1` 子类）。OS3 上"PDF 重命名"是独立 Fragment，A15 的源码同样没覆盖它。

---

## 四、问题 4 的确认

1. **`com.originui.widget.edittext.VEditText`：OS3 APK 中不存在。**
   - `grep -rl "VEDitText" sources/` → 0 命中；`find sources/com/originui -type d` → 只有 `com/originui/core`、`com/originui/widget/tabs`（**没有 `widget/edittext`**）。
   - 等价物：重命名框用的是**框架原生 `android.widget.EditText`**（`dialog_name_entry.xml:27`，`DialogNameEntry.java:348-349` 里 `findViewById(R.id.fileName_editText)` 强转 `android.widget.EditText`）。APK 内其它 EditText 只有 `androidx.appcompat.widget.AppCompatEditText`、`com.google.android.material.textfield.TextInputEditText`（与文件管理对话框无关）；vivo 自有控件在 `sources/vivo/app/*`、`sources/vivo/util/*`，无 EditText 子类。
   - 结论：A15 的"构造期替换 filters"在 OS3 **既无必要（无 maxLength）也无锚点（无 VEditText）**，直接删。

2. **`com.android.filemanager.smb.bean.ShareFile`：不存在，smb 包整体缺失。**
   - dexdump：`grep -oiE "Class descriptor : 'L[^']*smb[^']*;'"` → 0 命中；`ls jadx/full/sources/com/android/filemanager/ | grep -i smb` → 无。
   - 名字里带 ShareFile 的只有 XSpace 那套：`com.android.filemanager.w0.c.v`（source `XSpacePrepareShareFileTask.java`）与 `XSpaceShareFileWrapper`（见 `safe/data/n.java`、`safe/data/p.java`、`w0/c/v.java`）。这些是"原子私密空间分享"，**不是 SMB 分享文件**，不能当等价类用。
   - 结论：OS3 profile 去掉 ShareFile 相关判断（源码 L64-65、L104），只需 `instanceof java.io.File && exists()` 守卫即可。

---

## 五、探测点建议（无论走 (A) 还是兜底都适用）

最稳的探测组合（推荐放 `install(ClassLoader)` 最前面，全部命中才装 hook）：
1. `findClass("com.android.filemanager.view.dialog.DialogNameEntry")` —— 两侧都存在，先确认是 vivo 文件管理；
2. 按 profile 候选找重命名 Fragment：OS5 用 `RenameDialogFragment`，OS3 用 `f2`，并**用结构校验代替名字校验**：该类（或其父类）必须同时具备
   - `protected`/`public` 无参方法，返回 `void`（A15 是 `x2`、OS3 是 `x`）——按"本类中唯一非 `on*` 的无参 void 受保护方法"定位；
   - 一个 `java.io.File` 字段 + 一个 `DialogNameEntry` 类型字段（遍历 `getDeclaredFields()` 沿父类向上，按**类型**取，不写死 `e`/`a`/`f11309e`/`f11306b`）；
   - 一个 3 参 `(File,String,String)` 返回 void 的方法（重命名执行体）。
   → 这样连 OS3 子版本再次重混淆也只需换 profile 名，不必改字段名；
   - 反混淆级校验（离线用，不进模块）：dexdump 的 `source_file_idx` 已直接给出源文件名——`f2.block.txt:273` = `source_file_idx : 22094 (RenameDialogFragment.java)`、`a1.block.txt:437` = `(BaseNameEntryDialogFragment.java)`、`DialogNameEntry.block.txt:888` = `(DialogNameEntry.java)`；
3. 上限字段：在 `view.dialog` 包里找 `public static int` 且创建重命名弹窗时被赋值为 56 的类/字段（OS3 = `l1.d`，OS5 = `t.f11819f`）。

若任一步失败，就按 (B) 的方式**干净关闭 + 明确日志**（现有代码的失败点只有一条 `install failed: NoSuchClassError`，看不出是符号版本不匹配还是环境问题）。日志建议带上：`scopes()` 命中包的 `versionName`（OS3 = `6.11.4.4` / versionCode 61104004 / targetSdk 31）、探测到的 profile 名、缺失的具体符号（类/方法/字段）。

---

## 六、为什么先前 `out/Z8OS3-PICKER-FILEMAN-CHECK.md` 判"基本不可迁移"

那份结论的**事实全对**（`RenameDialogFragment`/`ShareFile`/`VEditText` 三类 0 命中、混淆锚点 0 命中），但**推断停在"按类名找不到 = 没有实现"**。本次换用两条不依赖混淆名的线索直接定位：
- `dexdump` 里搜**真名方法** `showRenameDialogFragment`（`r0.java:49` / `m0.java:1588`），其返回值/参数类型暴露了混淆类 `f2` 与回调接口 `f2$a`；
- jadx 保留的 `source_file_idx`（`compiled from: RenameDialogFragment.java` / `BaseNameEntryDialogFragment.java` / `DialogHelper.java`），把 `f2`/`a1`/`l1` 与 A15 的类一一对上。
（对应 dexdump 证据：`G:\z8os3-ref\z8os3-filemanager-classes.txt` 行 409964、1235759 的 `showRenameDialogFragment`，行 409850、1233097 的宿主类；`f2.block.txt` 末尾 `source_file_idx : ... (RenameDialogFragment.java)` 同理由 jadx 输出确认。）

**判定：(A) 可行**——需要新增一个 OS3 profile（删 2 段、换 6 个符号名、把 2 处"按名 hookAllMethods"改成"按签名精确 hook"），不是重写功能。
