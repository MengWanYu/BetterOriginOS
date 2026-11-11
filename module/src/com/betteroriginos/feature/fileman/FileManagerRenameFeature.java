package com.betteroriginos.feature.fileman;

import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.OsProfile;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 修复 vivo 文件管理（com.android.filemanager）重命名文件名 56 字符限制：
 * 长文件名显示正常，但重命名对话框会把超出部分逐字删除、确定时直接拒绝。
 *
 * 根因：DialogNameEntry 的截断法按静态上限（重命名=56）逐字删；
 * 重命名 Fragment 的"确定"里又硬编码 length() <= 56 拒绝。
 *
 * 补丁：截断法前把限制提到 85（ext4 文件名 255 字节 ÷ 3 字节/CJK = 85，任意中英混合都安全）；
 * "确定"对 (56,85] 区间跳过原检查、直接调用原私有重命名方法（重名等错误由原流程处理）。
 *
 * 单 APK 双支持（符号集两套独立，按 OsProfile.isA13OrLower() 分流，不做跨版本模糊兜底）：
 * - OriginOS 5 / A15：RenameDialogFragment.x2 + DialogNameEntry.L + t.f11819f + VEditText 56 上限；
 * - OriginOS 3 / A13（文件管理 6.11.4.4，取证 out/Z8OS3-FILEMAN-RENAME-RE.md）：对话框被混淆成
 *   view.dialog.f2（source RenameDialogFragment.java），确定=f2.x()V、执行体=f2.a(File,String,String)V、
 *   截断法=DialogNameEntry.a(String)Z、上限=l1.d；该版本既无 VEditText（布局是无 maxLength 的原生
 *   EditText，全包 LengthFilter 0 命中）也无 smb.bean.ShareFile，对应两段直接不装。
 */
public final class FileManagerRenameFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String ENTRY = "com.android.filemanager.view.dialog.DialogNameEntry";
    // ---- OriginOS 5 / Android 15（真机已验证，逻辑保持不变）----
    private static final String RENAME = "com.android.filemanager.view.dialog.RenameDialogFragment";
    private static final String LIMIT_CLS = "com.android.filemanager.view.dialog.t";
    private static final String LIMIT_FIELD = "f11819f";
    private static final String SHARE_FILE = "com.android.filemanager.smb.bean.ShareFile";
    private static final String V_EDIT_TEXT = "com.originui.widget.edittext.VEditText";
    private static final String TRUNCATE = "L";
    private static final String CONFIRM = "x2";
    private static final String RENAME_IMPL = "N2";
    private static final String FILE_FIELD = "f11309e";
    private static final String ENTRY_FIELD = "f11306b";
    private static final String BUTTON_FIELD = "f11314j";
    // ---- OriginOS 3 / Android 13：混淆名，字段在基类 a1（BaseNameEntryDialogFragment）里，
    //      XposedHelpers.getObjectField 沿父类查找；a1.k（Button）恒为 null，确定按钮取 dialog.getButton(-1)
    private static final String RENAME_A13 = "com.android.filemanager.view.dialog.f2";
    private static final String LIMIT_CLS_A13 = "com.android.filemanager.view.dialog.l1";
    private static final String LIMIT_FIELD_A13 = "d";
    private static final String TRUNCATE_A13 = "a";      // DialogNameEntry.a(String)Z，private 实例
    private static final String CONFIRM_A13 = "x";       // f2.x()V，覆盖 a1.x()V
    private static final String RENAME_IMPL_A13 = "a";   // f2.a(File,String,String)V，private
    private static final String FILE_FIELD_A13 = "e";
    private static final String ENTRY_FIELD_A13 = "a";
    private static final int OLD_LIMIT = 56;
    /** ext4 文件名 255 字节；CJK 3 字节/字 → 85 字符任意中英混合都不会超 255 字节 */
    private static final int NEW_LIMIT = 85;

    @Override
    public String id() {
        return "filemanager_rename";
    }

    @Override
    public int titleRes() {
        return R.string.feature_fileman_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_fileman_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_media;
    }

    @Override
    public String[] scopes() {
        return new String[]{"com.android.filemanager"};
    }

    @Override
    public void install(ClassLoader cl) {
        if (OsProfile.isA13OrLower()) {
            installA13(cl);
        } else {
            installA15(cl);
        }
    }

    /** A15 / OriginOS 5：已在真机验证的符号集 */
    private void installA15(ClassLoader cl) {
        Class<?> entry = findClass(cl, ENTRY);
        Class<?> rename = findClass(cl, RENAME);
        Class<?> limitCls = findClass(cl, LIMIT_CLS);
        Class<?> shareFile = findClass(cl, SHARE_FILE);
        Class<?> vEditText = findClass(cl, V_EDIT_TEXT);
        Class<?> lengthFilter = findClass(cl, "android.text.InputFilter$LengthFilter");
        if (entry == null || rename == null || limitCls == null || shareFile == null
                || vEditText == null || lengthFilter == null) {
            LogBridge.w("a15 rename symbols missing, feature disabled");
            return;
        }

        // 0) 布局里 VEditText 带 android:maxLength=56，TextView 构造时即挂 LengthFilter——
        //    构造完立即替换为 85 上限（先于任何 setText 预填充，避免长名被即刻截断）
        int hooks = 0;
        try {
            final android.text.InputFilter filter85 = (android.text.InputFilter)
                    XposedHelpers.newInstance(lengthFilter, (Object) Integer.valueOf(NEW_LIMIT));
            hooks += XposedBridge.hookAllConstructors(vEditText, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        ((android.widget.EditText) param.thisObject)
                                .setFilters(new android.text.InputFilter[]{filter85});
                        LogBridge.i("VEditText constructed, filters=85");
                    } catch (Throwable t) {
                        LogBridge.w("vedittext filter swap failed", t);
                    }
                }
            }).size();
        } catch (Throwable t) {
            LogBridge.w("vedittext hook failed", t);
        }

        // 1) 截断阈值 56 → 85：预填充长名不再被逐字删除
        try {
            hooks += XposedBridge.hookAllMethods(entry, TRUNCATE, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedHelpers.setStaticObjectField(limitCls, LIMIT_FIELD, NEW_LIMIT);
                    LogBridge.i("L called, limit raised to " + NEW_LIMIT
                            + " arg=" + param.args[0]);
                }
            }).size();
        } catch (Throwable t) {
            LogBridge.w("truncate hook failed", t);
        }

        // 2) 确定按钮：>56 且 ≤85 的名字跳过原硬编码拒绝，走原 N2 重命名流程
        try {
            hooks += XposedBridge.hookAllMethods(rename, CONFIRM, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object file = XposedHelpers.getObjectField(param.thisObject, FILE_FIELD);
                        if (!(file instanceof java.io.File) || shareFile.isInstance(file)) {
                            return;
                        }
                        java.io.File f = (java.io.File) file;
                        if (!f.exists()) {
                            return;
                        }
                        Object dialogEntry = XposedHelpers.getObjectField(
                                param.thisObject, ENTRY_FIELD);
                        if (dialogEntry == null) {
                            return;
                        }
                        EditText et = (EditText) XposedHelpers.callMethod(
                                dialogEntry, "getEditText");
                        String name = et.getText().toString().trim();
                        if (name.length() <= OLD_LIMIT || name.length() > NEW_LIMIT) {
                            return;
                        }
                        Object btn = XposedHelpers.getObjectField(param.thisObject, BUTTON_FIELD);
                        if (btn != null) {
                            XposedHelpers.callMethod(btn, "setClickable", false);
                        }
                        String dest = f.getParent() + "/" + name;
                        if (TextUtils.isEmpty(dest)) {
                            return;
                        }
                        XposedHelpers.callMethod(param.thisObject, RENAME_IMPL, f, dest, name);
                        param.setResult(null);
                    } catch (Throwable t) {
                        LogBridge.w("rename unlock failed", t);
                    }
                }
            }).size();
        } catch (Throwable t) {
            LogBridge.w("confirm hook failed", t);
        }
        LogBridge.i("fileman rename patch installed os=a15 hooks=" + hooks);
    }

    /**
     * A13 / OriginOS 3（文件管理 6.11.4.4）。截断法与重命名执行体都叫 `a`：
     * DialogNameEntry 里名为 a 的方法有 11 个（含 static synthetic 访问器），
     * 必须按签名精确锁定；装钩子前先把三处目标全部解析，任一缺失就干净退出，
     * 不留"抬了上限但确定仍被拒"的半截状态。
     */
    private void installA13(ClassLoader cl) {
        Class<?> entry = findClass(cl, ENTRY);
        Class<?> rename = findClass(cl, RENAME_A13);
        Class<?> limitCls = findClass(cl, LIMIT_CLS_A13);
        if (entry == null || rename == null || limitCls == null) {
            LogBridge.w("a13 rename symbols missing, feature disabled");
            return;
        }

        Method truncate = findExact(entry, TRUNCATE_A13, boolean.class, false, String.class);
        Method confirm = findExact(rename, CONFIRM_A13, void.class, false);
        Method impl = findExact(rename, RENAME_IMPL_A13, void.class, false,
                java.io.File.class, String.class, String.class);
        if (truncate == null || confirm == null || impl == null) {
            LogBridge.w("a13 rename methods missing, feature disabled: truncate=" + truncate
                    + " confirm=" + confirm + " impl=" + impl);
            return;
        }
        XC_MethodHook limitHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    // l1.d 是 public static int，反射按对象字段写入（Integer 自动拆箱），
                    // 与 A15 的 t.f11819f 写法一致，避免为此新增编译期桩
                    XposedHelpers.setStaticObjectField(limitCls, LIMIT_FIELD_A13, NEW_LIMIT);
                    LogBridge.i("a(String) called, limit raised to " + NEW_LIMIT
                            + " arg=" + param.args[0]);
                } catch (Throwable t) {
                    LogBridge.w("limit raise failed", t);
                }
            }
        };
        XC_MethodHook confirmHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object file = XposedHelpers.getObjectField(param.thisObject, FILE_FIELD_A13);
                    if (!(file instanceof java.io.File)) {
                        return;
                    }
                    java.io.File f = (java.io.File) file;
                    if (!f.exists()) {
                        return;
                    }
                    Object dialogEntry = XposedHelpers.getObjectField(
                            param.thisObject, ENTRY_FIELD_A13);
                    if (dialogEntry == null) {
                        return;
                    }
                    EditText et = (EditText) XposedHelpers.callMethod(
                            dialogEntry, "getEditText");
                    String name = et.getText().toString().trim();
                    if (name.length() <= OLD_LIMIT || name.length() > NEW_LIMIT) {
                        return;
                    }
                    // a1.k 恒为 null（全 APK 无写入点），真按钮是 dialog.getButton(BUTTON_POSITIVE)
                    Object dialog = XposedHelpers.callMethod(param.thisObject, "getDialog");
                    Object btn = dialog == null ? null
                            : XposedHelpers.callMethod(dialog, "getButton", -1);
                    if (btn != null) {
                        XposedHelpers.callMethod(btn, "setClickable", false);
                    }
                    String dest = f.getParent() + "/" + name;
                    if (TextUtils.isEmpty(dest)) {
                        return;
                    }
                    // 执行体是 private f2.a(File,String,String)：用装钩子前按签名解析好的方法，
                    // 不走 callMethod(..., "a", ...)——f2/a1 里名为 a 的重载极多，按名匹配易踩 synthetic 版
                    impl.invoke(param.thisObject, f, dest, name);
                    param.setResult(null);
                } catch (Throwable t) {
                    LogBridge.w("rename unlock failed", t);
                }
            }
        };

        java.util.Set<XC_MethodHook.Unhook> installed = new java.util.HashSet<>();
        try {
            XC_MethodHook.Unhook u = XposedHelpers.findAndHookMethod(
                    entry, TRUNCATE_A13, String.class, limitHook);
            if (u != null) {
                installed.add(u);
            }
        } catch (Throwable t) {
            LogBridge.w("truncate hook failed", t);
        }
        try {
            java.util.Set<XC_MethodHook.Unhook> u =
                    XposedBridge.hookAllMethods(rename, CONFIRM_A13, confirmHook);
            if (u != null) {
                installed.addAll(u);
            }
        } catch (Throwable t) {
            LogBridge.w("confirm hook failed", t);
        }
        int hooks = installed.size();
        if (hooks < 2) {
            // 只装了截断法或只装了确定按钮都是半截钩子（抬了上限仍会被"确定"拒绝）：整体回滚
            for (XC_MethodHook.Unhook u : installed) {
                try {
                    u.unhook();
                } catch (Throwable t) {
                    LogBridge.w("rollback failed", t);
                }
            }
            LogBridge.w("a13 rename hooks incomplete, rolled back: hooks=" + hooks);
            return;
        }
        LogBridge.i("fileman rename patch installed os=a13 hooks=" + hooks);
    }

    /** 每个符号各自独立 try：一次 NoSuchClassError 不该把整个功能带崩，缺失要能点名 */
    private static Class<?> findClass(ClassLoader cl, String name) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            LogBridge.w("class missing: " + name, t);
            return null;
        }
    }

    /** 按"名字 + 返回类型 + 参数类型"精确取方法（混淆名 a 有大量同名重载，不能按名 hook） */
    private static Method findExact(Class<?> cls, String name, Class<?> returnType,
                                    boolean wantStatic, Class<?>... params) {
        Method found = null;
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getReturnType() != returnType
                    || Modifier.isStatic(m.getModifiers()) != wantStatic
                    || !java.util.Arrays.equals(m.getParameterTypes(), params)) {
                continue;
            }
            if (found != null) {
                LogBridge.w("ambiguous method " + cls.getName() + "." + name + ", skip");
                return null;
            }
            found = m;
        }
        if (found == null) {
            LogBridge.w("method missing: " + cls.getName() + "." + name);
            return null;
        }
        try {
            found.setAccessible(true);
        } catch (Throwable t) {
            LogBridge.w("setAccessible failed for " + name, t);
            return null;
        }
        return found;
    }

    private static final class LogBridge {
        static void i(String msg) {
            XposedBridge.log("BetterOriginOS: " + msg);
        }

        static void w(String msg) {
            Log.w(TAG, msg);
            XposedBridge.log("BetterOriginOS: " + msg);
        }

        static void w(String msg, Throwable t) {
            Log.w(TAG, msg, t);
            XposedBridge.log("BetterOriginOS: " + msg + ": " + t);
        }
    }
}
