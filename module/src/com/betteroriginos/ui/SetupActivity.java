package com.betteroriginos.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.InjectionMarker;
import com.betteroriginos.core.SuCommander;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首次设置引导。硬性要求只有 root；注入框架/作用域/通知权限都是可选快速入口。
 * 单屏引导：大标题 + 唯一主任务 + 可选清单 + 底部主按钮。
 */
public class SetupActivity extends Activity {
    public static final String PREFS = "betteroriginos_setup";
    public static final String KEY_DONE = "done";

    private static final String VECTOR_CLI = "/data/adb/modules/zygisk_vector/cli";
    private static final String LSPCTL = "/data/adb/modules/zygisk_lsposed/lspctl";
    private static final String[] MANAGERS = {
            "org.matrix.vector.manager", "org.lsposed.manager", "io.github.lsposed.manager"};

    private TextView rootStatus;
    private TextView start;
    private TextView startHint;
    private TextView fwStatus;
    private TextView fwAction;
    private TextView scopeStatus;
    private TextView notiStatus;

    private volatile boolean rootOk;
    private String managerPkg;

    public static boolean isDone(Activity act) {
        return act.getSharedPreferences(PREFS, 0).getBoolean(KEY_DONE, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        rootStatus = findViewById(R.id.setup_root_status);
        start = findViewById(R.id.setup_start);
        startHint = findViewById(R.id.setup_start_hint);
        fwStatus = findViewById(R.id.setup_fw_status);
        fwAction = findViewById(R.id.setup_fw_action);
        scopeStatus = findViewById(R.id.setup_scope_status);
        notiStatus = findViewById(R.id.setup_noti_status);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.setup_root_action).setOnClickListener(v -> checkRoot(true));
        fwAction.setOnClickListener(v -> openFrameworkManager());
        findViewById(R.id.setup_scope_action).setOnClickListener(v -> fixScopes());
        findViewById(R.id.setup_noti_action).setOnClickListener(v -> requestNotifications());
        start.setOnClickListener(v -> {
            if (!rootOk) {
                Toast.makeText(this, R.string.setup_start_hint, Toast.LENGTH_SHORT).show();
                checkRoot(false);
                return;
            }
            getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_DONE, true).apply();
            finish();
        });
        checkRoot(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFramework();
        refreshScopes();
        refreshNotifications();
    }

    private void checkRoot(boolean toast) {
        rootStatus.setText("…");
        new Thread(() -> {
            String out = SuCommander.exec("id");
            final boolean ok = out != null && out.contains("uid=0");
            runOnUiThread(() -> {
                rootOk = ok;
                rootStatus.setText(ok ? R.string.setup_root_ok : R.string.setup_root_fail);
                rootStatus.setTextColor(getColor(ok ? R.color.accent : R.color.text_tertiary));
                start.setAlpha(ok ? 1f : 0.45f);
                startHint.setVisibility(ok ? View.INVISIBLE : View.VISIBLE);
                if (toast) {
                    Toast.makeText(this, ok ? R.string.setup_root_ok : R.string.setup_root_fail,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "setup-root").start();
    }

    private void checkFramework() {
        managerPkg = null;
        for (String pkg : MANAGERS) {
            boolean found = false;
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                found = true;
            } catch (Throwable ignored) {
            }
            if (!found) {
                // targetSdk 35 的包可见性限制：普通查询看不到别的包，用 root 兜底
                String out = SuCommander.exec("pm list packages " + pkg);
                found = out != null && out.contains("package:" + pkg);
            }
            if (found) {
                managerPkg = pkg;
                break;
            }
        }
        fwStatus.setText(managerPkg != null ? R.string.setup_fw_ok
                : R.string.setup_fw_no_manager);
        fwAction.setVisibility(managerPkg != null ? View.VISIBLE : View.GONE);
    }

    private void openFrameworkManager() {
        if (managerPkg == null) {
            return;
        }
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(managerPkg);
            if (launch != null) {
                startActivity(launch);
            } else {
                // 包可见性拿不到 launch intent 时，用 root monkey 起前台
                SuCommander.exec("monkey -p " + managerPkg + " 1");
            }
        } catch (Throwable t) {
            Toast.makeText(this, R.string.setup_fw_no_manager, Toast.LENGTH_LONG).show();
        }
    }

    private Set<String> expectedScopes() {
        Set<String> scopes = new LinkedHashSet<>();
        for (Feature f : FeatureManager.all()) {
            String[] s = f.scopes();
            if (s != null) {
                Collections.addAll(scopes, s);
            }
        }
        return scopes;
    }

    private void refreshScopes() {
        scopeStatus.setText(R.string.setup_scope_checking);
        final Set<String> scopes = expectedScopes();
        new Thread(() -> {
            Map<String, String> pids = runningPids(scopes);
            int running = 0;
            List<String> missing = new ArrayList<>();
            for (String pkg : scopes) {
                String pid = pids.get(pkg);
                if (pid == null || pid.length() == 0) {
                    continue;
                }
                running++;
                String marker = readMarker(pkg);
                String markerPid = null;
                if (marker != null) {
                    int i = marker.indexOf(':');
                    if (i > 0) {
                        markerPid = marker.substring(0, i);
                    }
                }
                if (markerPid == null || !(" " + pid + " ").contains(" " + markerPid + " ")) {
                    missing.add(pkg);
                }
            }
            final int runningCount = running;
            final List<String> missingList = missing;
            runOnUiThread(() -> {
                if (runningCount == 0) {
                    scopeStatus.setText(R.string.setup_scope_none);
                } else if (missingList.isEmpty()) {
                    scopeStatus.setText(R.string.setup_scope_ok);
                } else {
                    scopeStatus.setText(getString(R.string.setup_scope_missing,
                            missingList.size(), android.text.TextUtils.join("、", missingList)));
                }
            });
        }, "setup-scope").start();
    }

    private void fixScopes() {
        final Set<String> scopes = expectedScopes();
        scopeStatus.setText(R.string.setup_scope_checking);
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            boolean vector = SuCommander.exec("ls " + VECTOR_CLI).contains(VECTOR_CLI);
            boolean lspctl = !vector && SuCommander.exec("ls " + LSPCTL).contains(LSPCTL);
            if (vector) {
                for (String pkg : scopes) {
                    out.append(SuCommander.exec(VECTOR_CLI + " scope add com.betteroriginos " + pkg));
                }
            } else if (lspctl) {
                for (String pkg : scopes) {
                    out.append(SuCommander.exec(LSPCTL
                            + " scope add --user=0 com.betteroriginos " + pkg));
                }
            } else {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.setup_scope_fix_fail, "未找到框架 CLI"),
                        Toast.LENGTH_LONG).show());
                return;
            }
            final String text = out.toString();
            boolean ok = !text.toLowerCase().contains("error")
                    && !text.toLowerCase().contains("failed: [");
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, R.string.setup_scope_fix_ok, Toast.LENGTH_LONG).show();
                } else {
                    String tail = text.length() > 160
                            ? text.substring(text.length() - 160) : text;
                    Toast.makeText(this, getString(R.string.setup_scope_fix_fail, tail),
                            Toast.LENGTH_LONG).show();
                }
                refreshScopes();
            });
        }, "setup-fix").start();
    }

    private void refreshNotifications() {
        try {
            NotificationManager nm = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            boolean on = nm != null && nm.areNotificationsEnabled();
            notiStatus.setText(on ? R.string.setup_noti_ok : R.string.setup_noti_off);
        } catch (Throwable t) {
            notiStatus.setText(R.string.setup_noti_off);
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        } else {
            refreshNotifications();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshNotifications();
    }

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

    private static String readMarker(String pkg) {
        String file = "/shared_prefs/" + InjectionMarker.FILE + ".xml";
        String[] paths = {
                "/data/user_de/0/" + pkg + file,
                "/data/user/0/" + pkg + file,
                "/data/data/" + pkg + file
        };
        for (String path : paths) {
            String xml = SuCommander.exec("nsenter -t 1 -m -- cat " + path + " 2>/dev/null");
            if (xml == null || xml.length() == 0) {
                continue;
            }
            int i = xml.indexOf("name=\"" + InjectionMarker.KEY + "\"");
            if (i < 0) {
                continue;
            }
            int open = xml.indexOf('>', i);
            int close = xml.indexOf("</string>", open);
            if (open > 0 && close > open) {
                return xml.substring(open + 1, close);
            }
        }
        return null;
    }
}
