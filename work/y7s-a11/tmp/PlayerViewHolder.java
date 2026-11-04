package com.android.systemui.media;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.android.settingslib.datetime.ZoneGetter;
import com.android.systemui.R;
import com.android.systemui.util.animation.TransitionLayout;
import kotlin.Metadata;
import kotlin.TypeCastException;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: PlayerViewHolder.kt */
/* JADX INFO: loaded from: classes2.dex */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\b\n\u0002\b\u0002\u0018\u0000 >2\u00020\u0001:\u0001>B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u000e\u0010;\u001a\u00020\u00062\u0006\u0010<\u001a\u00020=R\u0019\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006¢\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0019\u0010\n\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\tR\u0019\u0010\f\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\tR\u0019\u0010\u000e\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\tR\u0019\u0010\u0010\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\tR\u0019\u0010\u0012\u001a\n \u0007*\u0004\u0018\u00010\u00130\u0013¢\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0019\u0010\u0016\u001a\n \u0007*\u0004\u0018\u00010\u00130\u0013¢\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0015R\u0019\u0010\u0018\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u001bR\u0019\u0010\u001c\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001bR\u0019\u0010\u001e\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u001bR\u0019\u0010 \u001a\n \u0007*\u0004\u0018\u00010\u00030\u0003¢\u0006\b\n\u0000\u001a\u0004\b!\u0010\"R\u0011\u0010#\u001a\u00020$¢\u0006\b\n\u0000\u001a\u0004\b%\u0010&R\u0019\u0010'\u001a\n \u0007*\u0004\u0018\u00010(0(¢\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0019\u0010+\u001a\n \u0007*\u0004\u0018\u00010(0(¢\u0006\b\n\u0000\u001a\u0004\b,\u0010*R\u0019\u0010-\u001a\n \u0007*\u0004\u0018\u00010\u00130\u0013¢\u0006\b\n\u0000\u001a\u0004\b.\u0010\u0015R\u0019\u0010/\u001a\n \u0007*\u0004\u0018\u00010\u00130\u0013¢\u0006\b\n\u0000\u001a\u0004\b0\u0010\u0015R\u0019\u00101\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b2\u0010\u001bR\u0019\u00103\u001a\n \u0007*\u0004\u0018\u00010404¢\u0006\b\n\u0000\u001a\u0004\b5\u00106R\u0019\u00107\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b8\u0010\u001bR\u0019\u00109\u001a\n \u0007*\u0004\u0018\u00010\u00190\u0019¢\u0006\b\n\u0000\u001a\u0004\b:\u0010\u001b¨\u0006?"}, d2 = {"Lcom/android/systemui/media/PlayerViewHolder;", "", "itemView", "Landroid/view/View;", "(Landroid/view/View;)V", "action0", "Landroid/widget/ImageButton;", "kotlin.jvm.PlatformType", "getAction0", "()Landroid/widget/ImageButton;", "action1", "getAction1", "action2", "getAction2", "action3", "getAction3", "action4", "getAction4", "albumView", "Landroid/widget/ImageView;", "getAlbumView", "()Landroid/widget/ImageView;", "appIcon", "getAppIcon", "appName", "Landroid/widget/TextView;", "getAppName", "()Landroid/widget/TextView;", "artistText", "getArtistText", "elapsedTimeView", "getElapsedTimeView", "options", "getOptions", "()Landroid/view/View;", "player", "Lcom/android/systemui/util/animation/TransitionLayout;", "getPlayer", "()Lcom/android/systemui/util/animation/TransitionLayout;", "progressTimes", "Landroid/view/ViewGroup;", "getProgressTimes", "()Landroid/view/ViewGroup;", "seamless", "getSeamless", "seamlessFallback", "getSeamlessFallback", "seamlessIcon", "getSeamlessIcon", "seamlessText", "getSeamlessText", "seekBar", "Landroid/widget/SeekBar;", "getSeekBar", "()Landroid/widget/SeekBar;", "titleText", "getTitleText", "totalTimeView", "getTotalTimeView", "getAction", ZoneGetter.KEY_ID, "", "Companion", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final class PlayerViewHolder {

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private final ImageButton action0;
    private final ImageButton action1;
    private final ImageButton action2;
    private final ImageButton action3;
    private final ImageButton action4;
    private final ImageView albumView;
    private final ImageView appIcon;
    private final TextView appName;
    private final TextView artistText;
    private final TextView elapsedTimeView;
    private final View options;

    @NotNull
    private final TransitionLayout player;
    private final ViewGroup progressTimes;
    private final ViewGroup seamless;
    private final ImageView seamlessFallback;
    private final ImageView seamlessIcon;
    private final TextView seamlessText;
    private final SeekBar seekBar;
    private final TextView titleText;
    private final TextView totalTimeView;

    @JvmStatic
    @NotNull
    public static final PlayerViewHolder create(@NotNull LayoutInflater layoutInflater, @NotNull ViewGroup viewGroup) {
        return INSTANCE.create(layoutInflater, viewGroup);
    }

    private PlayerViewHolder(View view) {
        if (view != null) {
            this.player = (TransitionLayout) view;
            this.appIcon = (ImageView) view.requireViewById(R.id.icon);
            this.appName = (TextView) view.requireViewById(R.id.app_name);
            this.albumView = (ImageView) view.requireViewById(R.id.album_art);
            this.titleText = (TextView) view.requireViewById(R.id.header_title);
            this.artistText = (TextView) view.requireViewById(R.id.header_artist);
            this.seamless = (ViewGroup) view.requireViewById(R.id.media_seamless);
            this.seamlessIcon = (ImageView) view.requireViewById(R.id.media_seamless_image);
            this.seamlessText = (TextView) view.requireViewById(R.id.media_seamless_text);
            this.seamlessFallback = (ImageView) view.requireViewById(R.id.media_seamless_fallback);
            this.seekBar = (SeekBar) view.requireViewById(R.id.media_progress_bar);
            this.progressTimes = (ViewGroup) view.requireViewById(R.id.notification_media_progress_time);
            this.elapsedTimeView = (TextView) view.requireViewById(R.id.media_elapsed_time);
            this.totalTimeView = (TextView) view.requireViewById(R.id.media_total_time);
            this.action0 = (ImageButton) view.requireViewById(R.id.action0);
            this.action1 = (ImageButton) view.requireViewById(R.id.action1);
            this.action2 = (ImageButton) view.requireViewById(R.id.action2);
            this.action3 = (ImageButton) view.requireViewById(R.id.action3);
            this.action4 = (ImageButton) view.requireViewById(R.id.action4);
            Drawable background = this.player.getBackground();
            if (background == null) {
                throw new TypeCastException("null cannot be cast to non-null type com.android.systemui.media.IlluminationDrawable");
            }
            IlluminationDrawable illuminationDrawable = (IlluminationDrawable) background;
            ViewGroup seamless = this.seamless;
            Intrinsics.checkExpressionValueIsNotNull(seamless, "seamless");
            illuminationDrawable.registerLightSource(seamless);
            ImageButton action0 = this.action0;
            Intrinsics.checkExpressionValueIsNotNull(action0, "action0");
            illuminationDrawable.registerLightSource(action0);
            ImageButton action1 = this.action1;
            Intrinsics.checkExpressionValueIsNotNull(action1, "action1");
            illuminationDrawable.registerLightSource(action1);
            ImageButton action2 = this.action2;
            Intrinsics.checkExpressionValueIsNotNull(action2, "action2");
            illuminationDrawable.registerLightSource(action2);
            ImageButton action3 = this.action3;
            Intrinsics.checkExpressionValueIsNotNull(action3, "action3");
            illuminationDrawable.registerLightSource(action3);
            ImageButton action4 = this.action4;
            Intrinsics.checkExpressionValueIsNotNull(action4, "action4");
            illuminationDrawable.registerLightSource(action4);
            this.options = view.requireViewById(R.id.qs_media_controls_options);
            return;
        }
        throw new TypeCastException("null cannot be cast to non-null type com.android.systemui.util.animation.TransitionLayout");
    }

    public /* synthetic */ PlayerViewHolder(View view, DefaultConstructorMarker defaultConstructorMarker) {
        this(view);
    }

    @NotNull
    public final TransitionLayout getPlayer() {
        return this.player;
    }

    public final ImageView getAppIcon() {
        return this.appIcon;
    }

    public final TextView getAppName() {
        return this.appName;
    }

    public final ImageView getAlbumView() {
        return this.albumView;
    }

    public final TextView getTitleText() {
        return this.titleText;
    }

    public final TextView getArtistText() {
        return this.artistText;
    }

    public final ViewGroup getSeamless() {
        return this.seamless;
    }

    public final ImageView getSeamlessIcon() {
        return this.seamlessIcon;
    }

    public final TextView getSeamlessText() {
        return this.seamlessText;
    }

    public final ImageView getSeamlessFallback() {
        return this.seamlessFallback;
    }

    public final SeekBar getSeekBar() {
        return this.seekBar;
    }

    public final ViewGroup getProgressTimes() {
        return this.progressTimes;
    }

    public final TextView getElapsedTimeView() {
        return this.elapsedTimeView;
    }

    public final TextView getTotalTimeView() {
        return this.totalTimeView;
    }

    public final ImageButton getAction0() {
        return this.action0;
    }

    public final ImageButton getAction1() {
        return this.action1;
    }

    public final ImageButton getAction2() {
        return this.action2;
    }

    public final ImageButton getAction3() {
        return this.action3;
    }

    public final ImageButton getAction4() {
        return this.action4;
    }

    @NotNull
    public final ImageButton getAction(int id) {
        switch (id) {
            case R.id.action0 /* 2131427413 */:
                ImageButton action0 = this.action0;
                Intrinsics.checkExpressionValueIsNotNull(action0, "action0");
                return action0;
            case R.id.action1 /* 2131427414 */:
                ImageButton action1 = this.action1;
                Intrinsics.checkExpressionValueIsNotNull(action1, "action1");
                return action1;
            case R.id.action2 /* 2131427415 */:
                ImageButton action2 = this.action2;
                Intrinsics.checkExpressionValueIsNotNull(action2, "action2");
                return action2;
            case R.id.action3 /* 2131427416 */:
                ImageButton action3 = this.action3;
                Intrinsics.checkExpressionValueIsNotNull(action3, "action3");
                return action3;
            case R.id.action4 /* 2131427417 */:
                ImageButton action4 = this.action4;
                Intrinsics.checkExpressionValueIsNotNull(action4, "action4");
                return action4;
            default:
                throw new IllegalArgumentException();
        }
    }

    public final View getOptions() {
        return this.options;
    }

    /* JADX INFO: compiled from: PlayerViewHolder.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0007¨\u0006\t"}, d2 = {"Lcom/android/systemui/media/PlayerViewHolder$Companion;", "", "()V", "create", "Lcom/android/systemui/media/PlayerViewHolder;", "inflater", "Landroid/view/LayoutInflater;", "parent", "Landroid/view/ViewGroup;", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        @JvmStatic
        @NotNull
        public final PlayerViewHolder create(@NotNull LayoutInflater inflater, @NotNull ViewGroup parent) {
            Intrinsics.checkParameterIsNotNull(inflater, "inflater");
            Intrinsics.checkParameterIsNotNull(parent, "parent");
            View mediaView = inflater.inflate(R.layout.media_view, parent, false);
            Intrinsics.checkExpressionValueIsNotNull(mediaView, "mediaView");
            mediaView.setLayoutDirection(3);
            PlayerViewHolder playerViewHolder = new PlayerViewHolder(mediaView, null);
            SeekBar seekBar = playerViewHolder.getSeekBar();
            Intrinsics.checkExpressionValueIsNotNull(seekBar, "seekBar");
            seekBar.setLayoutDirection(0);
            ViewGroup progressTimes = playerViewHolder.getProgressTimes();
            Intrinsics.checkExpressionValueIsNotNull(progressTimes, "progressTimes");
            progressTimes.setLayoutDirection(0);
            return playerViewHolder;
        }
    }
}
