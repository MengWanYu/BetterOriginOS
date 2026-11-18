package com.betteroriginos.core;

import android.content.Context;

import java.util.Collections;
import java.util.List;

public abstract class Feature {
    public abstract String id();

    public abstract int titleRes();

    public abstract int summaryRes();

    public abstract int categoryRes();

    public int iconRes() {
        return 0;
    }

    /** 非空表示该功能在详情页有预览：media / notify */
    public String previewKind() {
        return null;
    }

    /** 功能卡底部入口行文案；0 表示没有入口。点击后交给 onAction。 */
    public int actionLabelRes() {
        return 0;
    }

    public void onAction(Context ctx) {
    }

    public boolean defaultEnabled() {
        return true;
    }

    /**
     * 当前系统上该功能的"落点说明"资源；0 表示无需说明。
     * 用于诚实呈现：功能在本机没有可作用的系统界面时，卡片仍然可开（钩子无害），
     * 但要在摘要下面写清楚看不到效果的原因，而不是留一个静默失效的开关。
     */
    public int supportNoteRes(Context ctx) {
        return 0;
    }

    /** 动态说明（优先于 supportNoteRes）：用于展示本机实际预配置/当前状态。 */
    public CharSequence noteText(Context ctx) {
        return null;
    }

    /** 选项行的动态说明（优先于静态 summary）：用于显示该项在本机的预置状态。 */
    public CharSequence optionNote(Context ctx, String optionKey) {
        return null;
    }

    private List<FeatureOption> optionsCache;

    public final List<FeatureOption> options() {
        if (optionsCache == null) {
            optionsCache = buildOptions();
        }
        return optionsCache;
    }

    protected List<FeatureOption> buildOptions() {
        return Collections.emptyList();
    }

    /**
     * 选项写入模块存储之后额外要做的事（设置 App 进程）。默认无。
     * 例如把档位同步到系统 settings 的某个键，让被 hook 的进程走原生链路。
     */
    public void onOptionChanged(String key, String value) {
    }

    public String[] scopes() {
        return new String[]{"com.android.systemui"};
    }

    public abstract void install(ClassLoader classLoader);

    /** 同一进程可能加载多个包（vivo 的 SystemUIPlugin 就跑在 SystemUI 进程里） */
    public void install(ClassLoader classLoader, String packageName) {
        install(classLoader);
    }

    public boolean isEnabled(Context ctx) {
        return Prefs.featureEnabled(ctx, id(), defaultEnabled());
    }

    public String option(Context ctx, String key, String defaultValue) {
        return Prefs.option(ctx, id(), key, defaultValue);
    }
}
