package com.betteroriginos.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.SuCommander;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private View pageHome;
    private View pageInfo;
    private View tabHome;
    private View tabInfo;
    private TextView infoPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handleDebugIntent(getIntent());

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
        findViewById(R.id.restart).setOnClickListener(v ->
                ConfirmDialog.show(this,
                        R.string.dialog_restart_title,
                        R.string.dialog_restart_message,
                        R.string.restart,
                        this::restartSystemUi));

        SuCommander.get().addPendingListener(count -> updatePendingTexts());
        updatePendingTexts();
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
