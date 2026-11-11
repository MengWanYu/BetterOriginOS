package com.betteroriginos.feature.picker;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.OsProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 修复 OriginOS 5 照片选择器（Photo Picker）相册 tab 只显示
 * Camera/Screenshots/Downloads 三个默认相册的问题。
 *
 * 根因：MediaProvider 模块 photopicker 的 ExternalDbFacade 把相册枚举与
 * 相册内容查询都硬编码在 LOCAL_ALBUM_IDS 白名单上（appendWhere 三类别匹配）。
 *
 * 补丁：queryAlbums 前把 picker 库中全部 relative_path 的末段名写入
 * LOCAL_ALBUM_IDS（复用原循环/计数/封面逻辑）；appendWhere 对非白名单
 * 相册生成 relative_path 精确匹配。
 */
public final class PickerAlbumsFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String FACADE = "com.android.providers.media.photopicker.data.ExternalDbFacade";
    private static final List<String> WHITELIST = Arrays.asList("Camera", "Screenshots", "Downloads");
    /** 末段相册名 → 完整 relative_path（枚举时维护，供内容查询精确匹配） */
    private static volatile Map<String, String> PATH_BY_NAME = new HashMap<>();

    @Override
    public String id() {
        return "picker_albums";
    }

    @Override
    public int titleRes() {
        return R.string.feature_picker_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_picker_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_media;
    }

    @Override
    public int supportNoteRes(Context ctx) {
        // A13 的 MediaProvider 里没有 photopicker 那套库（本机实测 landing=no-entry），
        // 钩子装不上也不会有任何可见效果，所以要在卡片上说清楚，别留静默开关
        return OsProfile.isA13OrLower() ? R.string.note_picker_no_landing : 0;
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_music;
    }

    @Override
    public String[] scopes() {
        return new String[]{"com.android.providers.media.module"};
    }

    @Override
    public void install(ClassLoader cl) {
        Class<?> facade;
        try {
            facade = XposedHelpers.findClass(FACADE, cl);
            XposedHelpers.findClass(
                    "android.database.sqlite.SQLiteQueryBuilder", cl);
        } catch (Throwable t) {
            LogBridge.e("picker facade missing: " + t, null);
            return;
        }
        XC_MethodHook refreshHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    refreshAlbumIds(param.thisObject);
                } catch (Throwable t) {
                    LogBridge.w("refresh album ids failed", t);
                }
            }
        };
        XC_MethodHook whereHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String albumId = param.args.length > 1 && param.args[1] instanceof String
                        ? (String) param.args[1] : null;
                if (albumId == null || WHITELIST.contains(albumId)) {
                    return;
                }
                Object builder = param.args[0];
                String path = PATH_BY_NAME.get(albumId);
                String sql;
                if (path != null) {
                    sql = "relative_path = " + DatabaseUtils.sqlEscapeString(path);
                } else {
                    sql = "relative_path LIKE " + DatabaseUtils.sqlEscapeString("%" + albumId + "/%");
                }
                XposedHelpers.callMethod(builder, "appendWhereStandalone", sql);
                appendMimeTypes(builder, mimeArg(param.args));
                // appendWhere 的返回值会被调用方 addAll，必须给空列表而非 null
                param.setResult(new java.util.ArrayList<Object>());
            }
        };
        // 两个钩子各自独立 try：A13 的 queryAlbums 只有 (String)/(String,UserId) 重载，
        // 按 String[] 精确签名 hook 会抛 NoSuchMethodError 把整个功能一起带走
        int hooks = 0;
        try {
            hooks += XposedBridge.hookAllMethods(facade, "queryAlbums", refreshHook).size();
        } catch (Throwable t) {
            LogBridge.w("queryAlbums hook failed", t);
        }
        try {
            hooks += XposedBridge.hookAllMethods(facade, "appendWhere", whereHook).size();
        } catch (Throwable t) {
            LogBridge.w("appendWhere hook failed", t);
        }
        LogBridge.i("picker albums patch installed hooks=" + hooks);
        // handleLoadPackage 阶段 currentApplication() 还是 null，落点判定要等进程装好之后再问
        final ClassLoader delayed = cl;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                LogBridge.i("picker albums landing " + pickerState(delayed));
            } catch (Throwable t) {
                LogBridge.w("picker landing check failed: " + t, null);
            }
        }, 4000);
    }

    /**
     * 钩子装上不等于有落点：A13 的 Photo Picker 在部分 vivo ROM 上没有被任何入口注册
     * （PICK_IMAGES 无解析、GET_CONTENT 被路由到 DocumentsUI 自己的选择器），
     * 这时 queryAlbums 永远不会被调用，本功能处于空转状态 —— 打成日志而不是让它静默。
     */
    private static String pickerState(ClassLoader cl) {
        try {
            // 本进程自己的 Context：ActivityThread.currentApplication（AndroidAppHelper 在此环境为 null）
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", cl), "currentApplication");
            if (!(app instanceof android.content.Context)) {
                return "unknown(no-app)";
            }
            android.content.Intent pick = new android.content.Intent(
                    "android.provider.action.PICK_IMAGES");
            boolean ok = ((android.content.Context) app).getPackageManager()
                    .resolveActivity(pick, 0) != null;
            return ok ? "reachable" : "no-entry";
        } catch (Throwable t) {
            return "unknown(" + t.getClass().getSimpleName() + ")";
        }
    }

    /** A15 的第三类别是 String[] mimeTypes，A13 是单个 String mime，两种都要能吃下 */
    private static String[] mimeArg(Object[] args) {
        if (args == null || args.length < 3) {
            return null;
        }
        Object a = args[2];
        if (a instanceof String[]) {
            return (String[]) a;
        }
        if (a instanceof String) {
            return new String[]{(String) a};
        }
        return null;
    }

    /** 从 picker 库枚举全部相册目录，更新 LOCAL_ALBUM_IDS 与路径映射 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void refreshAlbumIds(Object facade) throws Throwable {
        Object helper = XposedHelpers.getObjectField(facade, "mDatabaseHelper");
        Class<?> facadeCls = facade.getClass();
        String table = (String) XposedHelpers.getStaticObjectField(facadeCls, "TABLE_FILES");
        if (table == null) {
            LogBridge.i("refresh: TABLE_FILES null");
            return;
        }
        // picker 库强制事务路由（否则抛 UnsupportedOperationException）
        Object result = XposedHelpers.callMethod(helper, "runWithoutTransaction",
                (java.util.function.Function) db -> {
                    android.database.sqlite.SQLiteDatabase sqdb =
                            (android.database.sqlite.SQLiteDatabase) db;
                    List<String> ids = new ArrayList<>(WHITELIST);
                    Map<String, String> paths = new HashMap<>();
                    int rows = 0;
                    Cursor c = sqdb.rawQuery("SELECT relative_path FROM " + table
                                    + " WHERE media_type IN (1,3) AND is_trashed = 0 AND is_pending = 0"
                                    + " AND relative_path IS NOT NULL AND relative_path != ''"
                                    + " GROUP BY relative_path",
                            null);
                    if (c != null) {
                        try {
                            while (c.moveToNext()) {
                                String full = c.getString(0);
                                rows++;
                                String name = albumName(full);
                                if (name == null || name.isEmpty() || paths.containsKey(name)) {
                                    continue;
                                }
                                paths.put(name, full);
                                if (!WHITELIST.contains(name)) {
                                    ids.add(name);
                                }
                            }
                        } finally {
                            c.close();
                        }
                    }
                    return new Object[]{ids, paths, rows};
                });
        Object[] arr = (Object[]) result;
        List<String> ids = (List<String>) arr[0];
        Map<String, String> paths = (Map<String, String>) arr[1];
        PATH_BY_NAME = paths;
        XposedHelpers.setStaticObjectField(facadeCls, "LOCAL_ALBUM_IDS",
                ids.toArray(new String[0]));
        LogBridge.i("refresh: table=" + table + " rows=" + arr[2]
                + " albums=" + ids + " map=" + paths.keySet());
    }

    /** mime 过滤原版走共享 args 列表（hook 拿不到），这里把值直接拼进 SQL（系统内部值） */
    private static void appendMimeTypes(Object builder, String[] mimeTypes) {
        if (mimeTypes == null || mimeTypes.length == 0) {
            return;
        }
        StringBuilder in = new StringBuilder("(");
        for (int i = 0; i < mimeTypes.length; i++) {
            if (i > 0) {
                in.append(" OR ");
            }
            in.append("mime_type LIKE ")
                    .append(DatabaseUtils.sqlEscapeString(mimeTypes[i].replace("*", "%")));
        }
        in.append(")");
        XposedHelpers.callMethod(builder, "appendWhereStandalone", in.toString());
    }

    private static String albumName(String relativePath) {
        String p = relativePath;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** 模块进程内独立日志 tag，避免依赖 XposedBridge.log 之外的通道 */
    private static final class LogBridge {
        static void i(String msg) {
            XposedBridge.log("BetterOriginOS: " + msg);
        }

        static void w(String msg, Throwable t) {
            XposedBridge.log("BetterOriginOS: " + msg + ": " + t);
        }

        static void e(String msg, Throwable t) {
            XposedBridge.log("BetterOriginOS: " + msg + ": " + t);
        }
    }
}
