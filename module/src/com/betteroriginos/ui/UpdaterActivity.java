package com.betteroriginos.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.Prefs;
import com.betteroriginos.core.SuCommander;

import java.util.HashMap;
import java.util.Map;

/**
 * 升级中心：升级器只负责把包下到 Download，状态/取消安装都在这里。
 * 数据由 updater 进程通过 Settings.Global 的运行期通道发布（feature/updater/UpdaterGuardFeature），
 * 这里只读；命令写回同一条通道，不重启升级器。
 */
public class UpdaterActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSeq;
    private TextView status;
    private TextView statusDetail;
    private ProgressBar progressBar;
    private TextView pkgName;
    private TextView pkgMeta;
    private TextView pkgPath;
    private TextView copy;
    private View cancel;
    private TextView fetch;
    private boolean polling;
    private boolean awaitingFetch;
    private long fetchBaseline;
    private long lastFetchTs;

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, UpdaterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updater);
        status = findViewById(R.id.updater_status);
        statusDetail = findViewById(R.id.updater_status_detail);
        progressBar = findViewById(R.id.updater_progress);
        pkgName = findViewById(R.id.updater_pkg_name);
        pkgMeta = findViewById(R.id.updater_pkg_meta);
        pkgPath = findViewById(R.id.updater_pkg_path);
        copy = findViewById(R.id.updater_copy);
        cancel = findViewById(R.id.updater_cancel);
        fetch = findViewById(R.id.updater_fetch);

        ((TextView) findViewById(R.id.updater_build))
                .setText(android.os.Build.DISPLAY);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        copy.setOnClickListener(v -> copyPath());
        cancel.setOnClickListener(v -> {
            send("cancel");
            Toast.makeText(this, R.string.updater_cancel_sent, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.updater_refresh).setOnClickListener(v -> {
            send("refresh");
            handler.postDelayed(this::render, 400L);
        });
        findViewById(R.id.updater_open).setOnClickListener(v -> openSystemUpdater());
        fetch.setOnClickListener(v -> {
            awaitingFetch = true;
            fetchBaseline = lastFetchTs;
            fetch.setAlpha(0.5f);
            send("fetch");
            Toast.makeText(this, R.string.updater_fetch_started, Toast.LENGTH_SHORT).show();
        });

    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }
            render();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        send("refresh");
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }

    /** 同一毫秒内连点两次 cancel 也要产生新 seq，否则 updater 会当成旧命令忽略。 */
    private void send(String verb) {
        long seq = Math.max(System.currentTimeMillis(), lastSeq + 1);
        lastSeq = seq;
        SuCommander.get().write(Prefs.UPDATER_CMD_KEY, seq + ":" + verb, null);
    }

    private void render() {
        String raw = null;
        try {
            raw = Settings.Global.getString(getContentResolver(), Prefs.UPDATER_STATE_KEY);
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.length() == 0) {
            status.setText(R.string.updater_state_unknown);
            statusDetail.setText(R.string.updater_not_loaded);
            progressBar.setVisibility(View.GONE);
            pkgName.setText(R.string.updater_package_none);
            pkgMeta.setText("");
            pkgPath.setText("");
            copy.setAlpha(0.4f);
            copy.setEnabled(false);
            setCancelEnabled(false);
            return;
        }
        Map<String, String> m = parse(raw);
        int engine = num(m, "engine", -1);
        int progress = num(m, "progress", -1);
        int dlp = num(m, "dlp", -1);
        int dls = num(m, "dls", -1);
        long ts = num(m, "ts", 0L);
        long size = num(m, "size", 0L);
        String name = m.get("name") == null ? "" : m.get("name");
        String path = m.get("path") == null ? "" : m.get("path");
        String ver = m.get("ver") == null ? "" : m.get("ver");
        String fetchValue = m.get("fetch") == null ? "" : m.get("fetch");
        long fetchTs = num(m, "fetchts", 0L);
        if (awaitingFetch && fetchTs != fetchBaseline && fetchTs > 0) {
            awaitingFetch = false;
            fetch.setAlpha(1f);
            showFetchResult(fetchValue);
        }
        lastFetchTs = fetchTs;
        boolean stale = ts > 0 && System.currentTimeMillis() - ts > 20000;

        boolean applying = engine >= 3 && engine <= 5;
        int shownProgress = -1;
        if (applying) {
            shownProgress = Math.max(0, progress);
            status.setText(engine == 4
                    ? getString(R.string.updater_state_verifying)
                    : getString(R.string.updater_state_applying, shownProgress));
        } else if (engine == 6) {
            status.setText(R.string.updater_state_ready);
        } else if (dlp > 0 && dlp < 100 && dls > 0) {
            shownProgress = dlp;
            status.setText(getString(R.string.updater_state_downloading, dlp));
        } else if (engine == 1) {
            status.setText(R.string.updater_state_checking);
        } else if (engine == 2) {
            status.setText(R.string.updater_state_available);
        } else if (engine == 7) {
            status.setText(R.string.updater_state_error);
        } else if (engine == 8) {
            status.setText(R.string.updater_state_rollback);
        } else if (engine == 9) {
            status.setText(R.string.updater_state_disabled);
        } else if (name.length() > 0) {
            status.setText(R.string.updater_state_pkg_ready);
        } else {
            status.setText(R.string.updater_state_idle);
        }
        String detail = getString(R.string.updater_label_engine) + " " + engine;
        if (ver.length() > 0) {
            detail += " · " + ver;
        }
        if (stale) {
            detail += " · " + getString(R.string.updater_stale);
        }
        statusDetail.setText(detail);

        if (shownProgress >= 0) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(shownProgress);
        } else {
            progressBar.setVisibility(View.GONE);
        }

        boolean hasPkg = name.length() > 0;
        pkgName.setText(hasPkg ? name : getString(R.string.updater_package_none));
        pkgMeta.setText(hasPkg && size > 0 ? formatSize(size) : "");
        pkgPath.setText(hasPkg ? path : "");
        copy.setAlpha(hasPkg ? 1f : 0.4f);
        copy.setEnabled(hasPkg);
        setCancelEnabled(applying);
    }

    private void showFetchResult(String value) {
        int msg;
        if (value.startsWith("ok:") || value.startsWith("direct-ok:")) {
            String[] parts = value.split(":");
            String ver = parts.length > 1 ? parts[1] : "";
            int res = value.startsWith("direct-ok:")
                    ? R.string.updater_fetch_direct_ok : R.string.updater_fetch_ok;
            Toast.makeText(this, getString(res, ver), Toast.LENGTH_LONG).show();
            send("refresh");
            return;
        } else if (value.startsWith("already:")) {
            Toast.makeText(this, getString(R.string.updater_fetch_already, value.substring(8)),
                    Toast.LENGTH_LONG).show();
            return;
        } else if ("no-space".equals(value)) {
            msg = R.string.updater_fetch_no_space;
        } else if (value.startsWith("no-url-length") || value.startsWith("no-url")
                || value.startsWith("no-file-name")) {
            msg = R.string.updater_fetch_url_dead;
        } else if ("no-prev-version".equals(value)) {
            msg = R.string.updater_fetch_no_prev;
        } else if ("net-error".equals(value)) {
            msg = R.string.updater_fetch_net;
        } else if ("no-package".equals(value) || value.startsWith("no-package:")
                || "no-url".equals(value) || value.startsWith("no-url:")
                || "parse-fail".equals(value)) {
            msg = R.string.updater_fetch_none;
        } else if (value.startsWith("error:")) {
            Toast.makeText(this, getString(R.string.updater_fetch_fail, value), Toast.LENGTH_LONG).show();
            return;
        } else {
            msg = R.string.updater_fetch_none;
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setCancelEnabled(boolean enabled) {
        cancel.setAlpha(enabled ? 1f : 0.4f);
        cancel.setEnabled(enabled);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double v = bytes;
        int u = 0;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        return String.format(java.util.Locale.US, u <= 1 ? "%.0f %s" : "%.2f %s", v, units[u]);
    }

    private void copyPath() {
        String path = pkgPath.getText().toString();
        if (path.length() == 0) {
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("updater_path", path));
                Toast.makeText(this, R.string.updater_copied, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
    }

    private void openSystemUpdater() {
        try {
            // vivo 的入口 intent-filter 带 mimeType=application/zip，不带类型就匹配不到。
            Intent intent = new Intent("com.vivo.action.ACTION_START_UPDATEACTIVITY");
            intent.setType("application/zip");
            intent.setPackage("com.bbk.updater");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new android.content.ComponentName(
                        "com.bbk.updater", "com.bbk.updater.ui.UpdateActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Throwable t2) {
                Toast.makeText(this, R.string.updater_open_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        for (String part : raw.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) {
                map.put(part.substring(0, i), part.substring(i + 1));
            }
        }
        return map;
    }

    private static int num(Map<String, String> m, String key, int def) {
        try {
            String v = m.get(key);
            return v == null ? def : Integer.parseInt(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private static long num(Map<String, String> m, String key, long def) {
        try {
            String v = m.get(key);
            return v == null ? def : Long.parseLong(v);
        } catch (Throwable t) {
            return def;
        }
    }
}
