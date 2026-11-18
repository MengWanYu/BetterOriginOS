package com.betteroriginos.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.InjectionMarker;
import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.Prefs;
import com.betteroriginos.core.SuCommander;
import com.originui.replica.OriginOverlays;
import com.originui.replica.OriginSwitch;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String LSPCTL_DISABLE =
            "/data/adb/modules/zygisk_lsposed/lspctl module disable --user=0 com.betteroriginos";
    private static final String LSPCTL_ENABLE =
            "/data/adb/modules/zygisk_lsposed/lspctl module enable --user=0 com.betteroriginos";
    private static final String LSPCTL_BOTH = LSPCTL_DISABLE + "\n" + LSPCTL_ENABLE;

    private View pageHome;
    private View pageInfo;
    private View tabHome;
    private View tabInfo;
    private TextView infoPending;
    private TextView infoInject;
    private String injectDetail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handleDebugIntent(getIntent());
        if (!SetupActivity.isDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        }

        pageHome = findViewById(R.id.page_home);
        pageInfo = findViewById(R.id.page_info);
        tabHome = findViewById(R.id.tab_home);
        tabInfo = findViewById(R.id.tab_info);

        bindTab(tabHome, R.drawable.ic_home, R.string.tab_home);
        bindTab(tabInfo, R.drawable.ic_info, R.string.tab_info);
        tabHome.setOnClickListener(v -> selectTab(true));
        tabInfo.setOnClickListener(v -> selectTab(false));
        selectTab(true);

        ((ListView) findViewById(R.id.app_list)).setAdapter(new AppListAdapter(this));
        ((TextView) findViewById(R.id.home_tagline)).setText(
                getString(R.string.tagline, com.betteroriginos.core.OsProfile.originName()));

        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.info_version)).setText("v" + v);
        } catch (Throwable t) {
            ((TextView) findViewById(R.id.info_version)).setText("");
        }
        ((TextView) findViewById(R.id.info_scope)).setText(scopeSummary());
        infoPending = findViewById(R.id.info_pending);
        OriginSwitch debugSwitch = findViewById(R.id.debug_switch);
        debugSwitch.setOnCheckedChangeListener(null);
        debugSwitch.setChecked(Prefs.debugEnabled(this), false);
        debugSwitch.setOnCheckedChangeListener((button, checked) ->
                SuCommander.get().write(Prefs.DEBUG_KEY, checked ? "1" : "0", ok -> {
                    if (!ok) {
                        Toast.makeText(this, R.string.info_debug_write_failed, Toast.LENGTH_SHORT).show();
                    }
                }));
        findViewById(R.id.restart).setOnClickListener(v ->
                ConfirmDialog.show(this,
                        R.string.dialog_restart_title,
                        R.string.dialog_restart_message,
                        R.string.restart,
                        this::restartSystemUi));

        SuCommander.get().addPendingListener(count -> updatePendingTexts());
        updatePendingTexts();

        infoInject = findViewById(R.id.info_inject);
        infoInject.setOnClickListener(v -> showInjectionDialog());
        refreshInjection();
    }

    /**
     * 注入自检：LSPosed 里“模块已启用”不代表进程里真的有模块（OTA/换 root 后 lspd
     * 可能没重载）。判据只认 scope 进程自己写的标记 pid 与当前 pidof 是否一致。
     */
    private void refreshInjection() {
        infoInject.setText(R.string.info_inject_loading);
        final Set<String> scopes = new LinkedHashSet<>();
        for (Feature f : FeatureManager.all()) {
            String[] s = f.scopes();
            if (s != null) {
                Collections.addAll(scopes, s);
            }
        }
        final String fingerprint = android.os.Build.FINGERPRINT;
        new Thread(() -> {
            int running = 0;
            int missing = 0;
            boolean oldBuild = false;
            StringBuilder detail = new StringBuilder();
            Map<String, String> pidsByPkg = runningPids(scopes);
            for (String pkg : scopes) {
                String pids = pidsByPkg.get(pkg);
                String marker = readInjectionMarker(pkg);
                String state;
                if (pids == null || pids.length() == 0) {
                    state = getString(R.string.info_inject_stopped);
                } else {
                    running++;
                    String markerPid = null;
                    String markerFp = null;
                    if (marker != null) {
                        // "<pid>:<fingerprint>"；fingerprint 自己含 ':'，只按第一个分隔。
                        int sep = marker.indexOf(':');
                        if (sep > 0) {
                            markerPid = marker.substring(0, sep);
                            markerFp = marker.substring(sep + 1);
                        }
                    }
                    if (markerPid == null) {
                        state = getString(R.string.info_inject_no_marker);
                        missing++;
                    } else if (!(" " + pids + " ").contains(" " + markerPid + " ")) {
                        state = getString(R.string.info_inject_stale);
                        missing++;
                    } else if (markerFp != null && !fingerprint.equals(markerFp)) {
                        state = getString(R.string.info_inject_old_build);
                        oldBuild = true;
                    } else {
                        state = getString(R.string.info_inject_ok);
                    }
                }
                detail.append(pkg).append("：").append(state).append('\n');
            }
            final int runningCount = running;
            final int missingCount = missing;
            final boolean ota = oldBuild;
            final String detailText = detail.toString().trim();
            runOnUiThread(() -> {
                injectDetail = detailText;
                StringBuilder summary = new StringBuilder();
                if (runningCount == 0) {
                    summary.append(getString(R.string.info_inject_none_running));
                } else if (missingCount > 0) {
                    summary.append(getString(R.string.info_inject_some_missing, missingCount));
                } else {
                    summary.append(getString(R.string.info_inject_all_ok));
                }
                if (ota) {
                    summary.append(" · ").append(getString(R.string.info_inject_ota));
                }
                summary.append('\n').append(detailText);
                infoInject.setText(summary.toString());
            });
        }, "inject-check").start();
    }

    /** 一次 ps 拿到所有进程：pidof 只认主进程名，"pkg:remote" 这类会漏。 */
    private static Map<String, String> runningPids(Set<String> scopes) {
        Map<String, String> map = new HashMap<>();
        String out = SuCommander.exec("ps -A -o PID,NAME");
        if (out == null || out.length() == 0) {
            return map;
        }
        for (String line : out.split("\n")) {
            String t = line.trim();
            int sp = t.indexOf(' ');
            if (sp <= 0 || sp + 1 >= t.length()) {
                continue;
            }
            String pid = t.substring(0, sp).trim();
            String name = t.substring(sp + 1).trim();
            if (pid.length() == 0 || !Character.isDigit(pid.charAt(0))) {
                continue;
            }
            for (String scope : scopes) {
                if (name.equals(scope) || name.startsWith(scope + ":")) {
                    String cur = map.get(scope);
                    map.put(scope, cur == null ? pid : cur + " " + pid);
                }
            }
        }
        return map;
    }

    /** root 读宿主自己的标记文件；各个 App 的 prefs 目录在 user_de / user / data 三种位置都见过。 */
    private static String readInjectionMarker(String pkg) {
        String file = "/shared_prefs/" + InjectionMarker.FILE + ".xml";
        String[] paths = {
                "/data/user_de/0/" + pkg + file,
                "/data/user/0/" + pkg + file,
                "/data/data/" + pkg + file
        };
        for (String path : paths) {
            // App 自己的 mount namespace 里看不到别的 App 的 data；用 init 的 namespace 读。
            String xml = SuCommander.exec("nsenter -t 1 -m -- cat " + path + " 2>/dev/null");
            String value = extractMark(xml);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extractMark(String xml) {
        if (xml == null || xml.length() == 0) {
            return null;
        }
        int i = xml.indexOf("name=\"" + InjectionMarker.KEY + "\"");
        if (i < 0) {
            return null;
        }
        int open = xml.indexOf('>', i);
        int close = xml.indexOf("</string>", open);
        return open > 0 && close > open ? xml.substring(open + 1, close) : null;
    }

    private void showInjectionDialog() {
        OriginOverlays.OriginDialog dialog = new OriginOverlays.OriginDialog(this);
        dialog.build(getString(R.string.info_inject_dialog_title),
                (injectDetail.length() == 0 ? "" : injectDetail + "\n\n")
                        + getString(R.string.info_inject_dialog_message, LSPCTL_BOTH),
                getString(R.string.info_inject_copy), v -> copyLspctlCommands(),
                getString(R.string.info_inject_try), v -> tryReloadModule());
        dialog.show();
    }

    private void copyLspctlCommands() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("lspctl", LSPCTL_BOTH));
                Toast.makeText(this, R.string.updater_copied, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
    }

    private void tryReloadModule() {
        new Thread(() -> {
            // 先确保模块是启用的：万一 disable 成功、enable 失败，也不会把模块留在关闭状态。
            SuCommander.runSu(LSPCTL_ENABLE);
            String out = SuCommander.exec(
                    LSPCTL_DISABLE + " && " + LSPCTL_ENABLE + " && echo BETTERORIGINOS_OK");
            final boolean ok = out != null && out.contains("BETTERORIGINOS_OK");
            if (!ok) {
                SuCommander.runSu(LSPCTL_ENABLE);
            }
            final String tail = out == null ? "" : out.substring(Math.max(0, out.length() - 160));
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, R.string.info_inject_try_ok, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.info_inject_try_fail, tail), Toast.LENGTH_LONG).show();
                }
                refreshInjection();
            });
        }, "inject-reload").start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDebugIntent(intent);
    }

    /** 调试入口：同一实例可连续投递 post/postmsg/postgroup/postconv/postlong/postswap/postmedia/cancelmedia/cancel */
    private void handleDebugIntent(Intent intent) {
        String debugNotify = intent == null ? null
                : intent.getStringExtra("debug_notify");
        String debugOpen = intent == null ? null : intent.getStringExtra("debug_open");
        if (debugOpen != null) {
            android.util.Log.i("BetterOriginOS", "debug open=" + debugOpen
                    + " instance=" + Integer.toHexString(System.identityHashCode(this)));
        }
        android.util.Log.i("BetterOriginOS", "debug intent cmd=" + debugNotify
                + " instance=" + Integer.toHexString(System.identityHashCode(this)));
        if ("post".equals(debugNotify)) {
            moveTaskToBack(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> com.betteroriginos.debug.NotifyTest.post(this), 800);
        } else if ("postmsg".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postMessaging(this);
        } else if ("postgroup".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postGroup(this);
        } else if ("postall".equals(debugNotify) || "postact".equals(debugNotify)) {
            // 先把自己退到后台，否则设置页盖住 heads-up，取证取不到横幅
            moveTaskToBack(true);
            final boolean actionsOnly = "postact".equals(debugNotify);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (actionsOnly) {
                    com.betteroriginos.debug.NotifyTest.postActions(this);
                    return;
                }
                com.betteroriginos.debug.NotifyTest.post(this);
                com.betteroriginos.debug.NotifyTest.postMessaging(this);
                com.betteroriginos.debug.NotifyTest.postGroup(this);
                com.betteroriginos.debug.NotifyTest.postConversation(this);
                com.betteroriginos.debug.NotifyTest.postLong(this);
                com.betteroriginos.debug.NotifyTest.postActions(this);
            }, 900);
        } else if ("postjump".equals(debugNotify)) {
            moveTaskToBack(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> com.betteroriginos.debug.NotifyTest.postJump(this), 800);
        } else if ("postbulk".equals(debugNotify)) {
            moveTaskToBack(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> com.betteroriginos.debug.NotifyTest.postBulk(this), 800);
        } else if ("postswap".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postSwap(this);
        } else if ("postconv".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postConversation(this);
        } else if ("postlong".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postLong(this);
        } else if ("postmedia".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.postMedia(this);
        } else if ("cancelmedia".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.cancelMedia(this);
        } else if ("cancel".equals(debugNotify)) {
            com.betteroriginos.debug.NotifyTest.cancel(this);
        }
    }

    private void bindTab(View tab, int iconRes, int labelRes) {
        ((ImageView) tab.findViewById(R.id.tab_icon)).setImageResource(iconRes);
        ((TextView) tab.findViewById(R.id.tab_label)).setText(labelRes);
    }

    private void selectTab(boolean home) {
        pageHome.setVisibility(home ? View.VISIBLE : View.GONE);
        pageInfo.setVisibility(home ? View.GONE : View.VISIBLE);
        tabHome.setSelected(home);
        tabInfo.setSelected(!home);
    }

    private void updatePendingTexts() {
        int n = SuCommander.get().pendingCount();
        infoPending.setText(n == 0
                ? getString(R.string.info_pending_none)
                : getString(R.string.info_pending_format, n));
    }

    private String scopeSummary() {
        Set<String> scopes = new LinkedHashSet<>();
        List<Feature> all = FeatureManager.all();
        for (Feature f : all) {
            String[] s = f.scopes();
            if (s != null) {
                Collections.addAll(scopes, s);
            }
        }
        return android.text.TextUtils.join("，", scopes);
    }

    private void restartSystemUi() {
        Toast.makeText(this, R.string.restarting, Toast.LENGTH_SHORT).show();
        SuCommander.get().restartSystemUi(ok -> {
            if (!ok) {
                Toast.makeText(this, R.string.restart_failed, Toast.LENGTH_LONG).show();
            }
        });
    }
}
