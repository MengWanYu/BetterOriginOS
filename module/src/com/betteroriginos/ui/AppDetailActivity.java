package com.betteroriginos.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.IconMapper;
import com.betteroriginos.core.SuCommander;

public class AppDetailActivity extends Activity implements SuCommander.PendingListener {
    public static final String EXTRA_PACKAGE = "package";

    private String targetPackage;
    private TextView detailPending;
    private FeatureAdapter featureAdapter;

    public static void start(Context ctx, String pkg) {
        Intent intent = new Intent(ctx, AppDetailActivity.class);
        intent.putExtra(EXTRA_PACKAGE, pkg);
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);
        targetPackage = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (targetPackage == null || targetPackage.isEmpty()) {
            finish();
            return;
        }

        ((ImageView) findViewById(R.id.app_icon))
                .setImageDrawable(IconMapper.forPackage(this, targetPackage));
        ((TextView) findViewById(R.id.app_name)).setText(IconMapper.labelOf(this, targetPackage));
        ((TextView) findViewById(R.id.app_package)).setText(targetPackage);

        ListView list = findViewById(R.id.feature_list);
        featureAdapter = new FeatureAdapter(this, FeatureManager.forPackage(targetPackage));
        list.setAdapter(featureAdapter);

        EditText searchInput = findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                featureAdapter.setFilter(s.toString());
            }
        });

        detailPending = findViewById(R.id.detail_pending);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.restart).setOnClickListener(v -> confirmRestart());
        updatePendingText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SuCommander.get().addPendingListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SuCommander.get().removePendingListener(this);
    }

    @Override
    public void onPendingChanged(int count) {
        updatePendingText();
    }

    private void updatePendingText() {
        int n = SuCommander.get().pendingCount();
        detailPending.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        detailPending.setText(getString(R.string.info_pending_format, n));
    }

    /** FeatureAdapter 写入结果回调 */
    void onWriteResult(boolean ok) {
        if (!ok) {
            Toast.makeText(this, R.string.write_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /** 只重启本页涉及的应用（功能自身 scopes + 页面包名），不再一律重启 SystemUI。 */
    private void confirmRestart() {
        final java.util.LinkedHashSet<String> pkgs = new java.util.LinkedHashSet<>();
        pkgs.add(targetPackage);
        for (com.betteroriginos.core.Feature f : FeatureManager.forPackage(targetPackage)) {
            String[] scopes = f.scopes();
            if (scopes != null) {
                java.util.Collections.addAll(pkgs, scopes);
            }
        }
        pkgs.remove(null);
        String list = android.text.TextUtils.join("、", pkgs);
        ConfirmDialog.show(this,
                getString(R.string.dialog_restart_packages_title),
                getString(R.string.dialog_restart_packages_message, list),
                getString(R.string.restart),
                () -> {
                    Toast.makeText(this, R.string.restarting, Toast.LENGTH_SHORT).show();
                    SuCommander.get().restartPackages(new java.util.ArrayList<>(pkgs), ok -> {
                        if (!ok) {
                            Toast.makeText(this, R.string.restart_failed, Toast.LENGTH_LONG).show();
                        }
                    });
                });
    }
}
