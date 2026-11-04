package com.android.systemui.media;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.MathUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.media.MediaDataManager;
import com.android.systemui.media.MediaHostStatesManager;
import com.android.systemui.media.PlayerViewHolder;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.PageIndicator;
import com.android.systemui.statusbar.notification.VisualStabilityManager;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.Utils;
import com.android.systemui.util.animation.MeasurementInput;
import com.android.systemui.util.animation.TransitionLayout;
import com.android.systemui.util.animation.UniqueObjectHostView;
import com.android.systemui.util.animation.UniqueObjectHostViewKt;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.vivo.keyguard.model.provider.DataEntry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import kotlin.Metadata;
import kotlin.TypeCastException;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.FunctionReference;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.MutablePropertyReference0;
import kotlin.jvm.internal.Reflection;
import kotlin.jvm.internal.TypeIntrinsics;
import kotlin.reflect.KDeclarationContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vivo.app.epm.Switch;

/* JADX INFO: compiled from: MediaCarouselController.kt */
/* JADX INFO: loaded from: classes2.dex */
@Singleton
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000±\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\b\n\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\t\n\u0002\u0010\t\n\u0002\b\u0012*\u0001\u001a\b\u0007\u0018\u00002\u00020\u0001BW\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\b\b\u0001\u0010\r\u001a\u00020\u000e\u0012\u0006\u0010\u000f\u001a\u00020\u0010\u0012\u0006\u0010\u0011\u001a\u00020\u0012\u0012\u0006\u0010\u0013\u001a\u00020\u0014¢\u0006\u0002\u0010\u0015J\"\u0010I\u001a\u00020J2\u0006\u0010K\u001a\u0002092\b\u0010L\u001a\u0004\u0018\u0001092\u0006\u0010M\u001a\u00020:H\u0002J\b\u0010N\u001a\u000206H\u0002J\b\u0010O\u001a\u00020JH\u0002J\b\u0010P\u001a\u00020JH\u0002J4\u0010Q\u001a\u00020J2\u0006\u0010-\u001a\u00020\u00172\b\u0010+\u001a\u0004\u0018\u00010,2\u0006\u0010R\u001a\u00020&2\b\b\u0002\u0010S\u001a\u00020T2\b\b\u0002\u0010U\u001a\u00020TJ\b\u0010V\u001a\u00020JH\u0002J\u0010\u0010W\u001a\u00020J2\u0006\u0010K\u001a\u000209H\u0002J\b\u0010X\u001a\u00020JH\u0002J&\u0010Y\u001a\u00020J2\u0006\u0010Z\u001a\u00020\u00172\u0006\u0010[\u001a\u00020\u00172\u0006\u0010\\\u001a\u00020$2\u0006\u0010]\u001a\u00020&J\b\u0010^\u001a\u00020JH\u0002J\b\u0010_\u001a\u00020JH\u0002J\b\u0010`\u001a\u00020JH\u0002J\b\u0010a\u001a\u00020JH\u0002J\b\u0010b\u001a\u00020JH\u0002J\u0018\u0010c\u001a\u00020J2\u0006\u0010d\u001a\u00020\u00062\u0006\u0010e\u001a\u00020&H\u0002R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0017X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0019\u001a\u00020\u001aX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u001bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u0017X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u0017X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u001e\u001a\u00020\u0017X\u0082\u000e¢\u0006\b\n\u0000\u0012\u0004\b\u001f\u0010 R\u0014\u0010!\u001a\u00020\u0017X\u0082\u000e¢\u0006\b\n\u0000\u0012\u0004\b\"\u0010 R\u000e\u0010#\u001a\u00020$X\u0082\u000e¢\u0006\u0002\n\u0000R\u001e\u0010'\u001a\u00020&2\u0006\u0010%\u001a\u00020&@BX\u0082\u000e¢\u0006\b\n\u0000\"\u0004\b(\u0010)R\u000e\u0010*\u001a\u00020&X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010+\u001a\u0004\u0018\u00010,X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010-\u001a\u00020\u0017X\u0082\u000e¢\u0006\b\n\u0000\u0012\u0004\b.\u0010 R\u001e\u0010/\u001a\u00020&2\u0006\u0010%\u001a\u00020&@BX\u0082\u000e¢\u0006\b\n\u0000\"\u0004\b0\u0010)R\u000e\u00101\u001a\u000202X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00103\u001a\u000204X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00105\u001a\u000206X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u00107\u001a\u000e\u0012\u0004\u0012\u000209\u0012\u0004\u0012\u00020:08X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010;\u001a\u000206¢\u0006\b\n\u0000\u001a\u0004\b<\u0010=R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u001d\u0010>\u001a\u000e\u0012\u0004\u0012\u000209\u0012\u0004\u0012\u00020\u000608¢\u0006\b\n\u0000\u001a\u0004\b?\u0010@R\u000e\u0010A\u001a\u00020&X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010B\u001a\u00020CX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010D\u001a\u00020&X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010E\u001a\u00020FX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010G\u001a\u00020HX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006f"}, d2 = {"Lcom/android/systemui/media/MediaCarouselController;", "", "context", "Landroid/content/Context;", "mediaControlPanelFactory", "Ljavax/inject/Provider;", "Lcom/android/systemui/media/MediaControlPanel;", "visualStabilityManager", "Lcom/android/systemui/statusbar/notification/VisualStabilityManager;", "mediaHostStatesManager", "Lcom/android/systemui/media/MediaHostStatesManager;", "activityStarter", "Lcom/android/systemui/plugins/ActivityStarter;", "executor", "Lcom/android/systemui/util/concurrency/DelayableExecutor;", "mediaManager", "Lcom/android/systemui/media/MediaDataFilter;", "configurationController", "Lcom/android/systemui/statusbar/policy/ConfigurationController;", "falsingManager", "Lcom/android/systemui/plugins/FalsingManager;", "(Landroid/content/Context;Ljavax/inject/Provider;Lcom/android/systemui/statusbar/notification/VisualStabilityManager;Lcom/android/systemui/media/MediaHostStatesManager;Lcom/android/systemui/plugins/ActivityStarter;Lcom/android/systemui/util/concurrency/DelayableExecutor;Lcom/android/systemui/media/MediaDataFilter;Lcom/android/systemui/statusbar/policy/ConfigurationController;Lcom/android/systemui/plugins/FalsingManager;)V", "carouselMeasureHeight", "", "carouselMeasureWidth", "configListener", "com/android/systemui/media/MediaCarouselController$configListener$1", "Lcom/android/systemui/media/MediaCarouselController$configListener$1;", "currentCarouselHeight", "currentCarouselWidth", "currentEndLocation", "currentEndLocation$annotations", "()V", "currentStartLocation", "currentStartLocation$annotations", "currentTransitionProgress", "", Switch.SWITCH_ATTR_VALUE, "", "currentlyExpanded", "setCurrentlyExpanded", "(Z)V", "currentlyShowingOnlyActive", "desiredHostState", "Lcom/android/systemui/media/MediaHostState;", "desiredLocation", "desiredLocation$annotations", "isRtl", "setRtl", "mediaCarousel", "Lcom/android/systemui/media/MediaScrollView;", "mediaCarouselScrollHandler", "Lcom/android/systemui/media/MediaCarouselScrollHandler;", "mediaContent", "Landroid/view/ViewGroup;", "mediaData", "", "", "Lcom/android/systemui/media/MediaData;", "mediaFrame", "getMediaFrame", "()Landroid/view/ViewGroup;", "mediaPlayers", "getMediaPlayers", "()Ljava/util/Map;", "needsReordering", "pageIndicator", "Lcom/android/systemui/qs/PageIndicator;", "playersVisible", "settingsButton", "Landroid/view/View;", "visualStabilityCallback", "Lcom/android/systemui/statusbar/notification/VisualStabilityManager$Callback;", "addOrUpdatePlayer", "", "key", "oldKey", "data", "inflateMediaCarousel", "inflateSettingsButton", "maybeResetSettingsCog", "onDesiredLocationChanged", "animate", DataEntry.WP_DURATION, "", "startDelay", "recreatePlayers", "removePlayer", "reorderAllPlayers", "setCurrentState", "startLocation", "endLocation", "progress", "immediately", "updateCarouselDimensions", "updateCarouselSize", "updatePageIndicator", "updatePageIndicatorAlpha", "updatePageIndicatorLocation", "updatePlayerToState", "mediaPlayer", "noAnimation", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final class MediaCarouselController {
    private final ActivityStarter activityStarter;
    private int carouselMeasureHeight;
    private int carouselMeasureWidth;
    private final MediaCarouselController$configListener$1 configListener;
    private final Context context;
    private int currentCarouselHeight;
    private int currentCarouselWidth;
    private int currentEndLocation;
    private int currentStartLocation;
    private float currentTransitionProgress;
    private boolean currentlyExpanded;
    private boolean currentlyShowingOnlyActive;
    private MediaHostState desiredHostState;
    private int desiredLocation;
    private boolean isRtl;
    private final MediaScrollView mediaCarousel;
    private final MediaCarouselScrollHandler mediaCarouselScrollHandler;
    private final ViewGroup mediaContent;
    private final Provider<MediaControlPanel> mediaControlPanelFactory;
    private final Map<String, MediaData> mediaData;

    @NotNull
    private final ViewGroup mediaFrame;
    private final MediaHostStatesManager mediaHostStatesManager;

    @NotNull
    private final Map<String, MediaControlPanel> mediaPlayers;
    private boolean needsReordering;
    private final PageIndicator pageIndicator;
    private boolean playersVisible;
    private View settingsButton;
    private final VisualStabilityManager.Callback visualStabilityCallback;
    private final VisualStabilityManager visualStabilityManager;

    private static /* synthetic */ void currentEndLocation$annotations() {
    }

    private static /* synthetic */ void currentStartLocation$annotations() {
    }

    private static /* synthetic */ void desiredLocation$annotations() {
    }

    /* JADX INFO: renamed from: com.android.systemui.media.MediaCarouselController$1, reason: invalid class name */
    /* JADX INFO: compiled from: MediaCarouselController.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\b\n\u0000\n\u0002\u0010\u0002\n\u0000\u0010\u0000\u001a\u00020\u0001¢\u0006\u0002\b\u0002"}, d2 = {"<anonymous>", "", "invoke"}, k = 3, mv = {1, 1, 16})
    static final /* synthetic */ class AnonymousClass1 extends FunctionReference implements Function0<Unit> {
        AnonymousClass1(MediaDataFilter mediaDataFilter) {
            super(0, mediaDataFilter);
        }

        @Override // kotlin.jvm.internal.CallableReference, kotlin.reflect.KCallable
        public final String getName() {
            return "onSwipeToDismiss";
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final KDeclarationContainer getOwner() {
            return Reflection.getOrCreateKotlinClass(MediaDataFilter.class);
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final String getSignature() {
            return "onSwipeToDismiss()V";
        }

        @Override // kotlin.jvm.functions.Function0
        public /* bridge */ /* synthetic */ Unit invoke() {
            invoke2();
            return Unit.INSTANCE;
        }

        /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
        public final void invoke2() {
            ((MediaDataFilter) this.receiver).onSwipeToDismiss();
        }
    }

    /* JADX INFO: renamed from: com.android.systemui.media.MediaCarouselController$2, reason: invalid class name */
    /* JADX INFO: compiled from: MediaCarouselController.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\b\n\u0000\n\u0002\u0010\u0002\n\u0000\u0010\u0000\u001a\u00020\u0001¢\u0006\u0002\b\u0002"}, d2 = {"<anonymous>", "", "invoke"}, k = 3, mv = {1, 1, 16})
    static final /* synthetic */ class AnonymousClass2 extends FunctionReference implements Function0<Unit> {
        AnonymousClass2(MediaCarouselController mediaCarouselController) {
            super(0, mediaCarouselController);
        }

        @Override // kotlin.jvm.internal.CallableReference, kotlin.reflect.KCallable
        public final String getName() {
            return "updatePageIndicatorLocation";
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final KDeclarationContainer getOwner() {
            return Reflection.getOrCreateKotlinClass(MediaCarouselController.class);
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final String getSignature() {
            return "updatePageIndicatorLocation()V";
        }

        @Override // kotlin.jvm.functions.Function0
        public /* bridge */ /* synthetic */ Unit invoke() {
            invoke2();
            return Unit.INSTANCE;
        }

        /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
        public final void invoke2() {
            ((MediaCarouselController) this.receiver).updatePageIndicatorLocation();
        }
    }

    /* JADX INFO: renamed from: com.android.systemui.media.MediaCarouselController$addOrUpdatePlayer$2, reason: invalid class name and case insensitive filesystem */
    /* JADX INFO: compiled from: MediaCarouselController.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\b\n\u0000\n\u0002\u0010\u0002\n\u0000\u0010\u0000\u001a\u00020\u0001¢\u0006\u0002\b\u0002"}, d2 = {"<anonymous>", "", "invoke"}, k = 3, mv = {1, 1, 16})
    static final /* synthetic */ class C01252 extends FunctionReference implements Function0<Unit> {
        C01252(MediaCarouselController mediaCarouselController) {
            super(0, mediaCarouselController);
        }

        @Override // kotlin.jvm.internal.CallableReference, kotlin.reflect.KCallable
        public final String getName() {
            return "updateCarouselDimensions";
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final KDeclarationContainer getOwner() {
            return Reflection.getOrCreateKotlinClass(MediaCarouselController.class);
        }

        @Override // kotlin.jvm.internal.CallableReference
        public final String getSignature() {
            return "updateCarouselDimensions()V";
        }

        @Override // kotlin.jvm.functions.Function0
        public /* bridge */ /* synthetic */ Unit invoke() {
            invoke2();
            return Unit.INSTANCE;
        }

        /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
        public final void invoke2() {
            ((MediaCarouselController) this.receiver).updateCarouselDimensions();
        }
    }

    /* JADX WARN: Type inference failed for: r2v1, types: [com.android.systemui.media.MediaCarouselController$configListener$1] */
    @Inject
    public MediaCarouselController(@NotNull Context context, @NotNull Provider<MediaControlPanel> mediaControlPanelFactory, @NotNull VisualStabilityManager visualStabilityManager, @NotNull MediaHostStatesManager mediaHostStatesManager, @NotNull ActivityStarter activityStarter, @Main @NotNull DelayableExecutor executor, @NotNull MediaDataFilter mediaManager, @NotNull ConfigurationController configurationController, @NotNull FalsingManager falsingManager) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        Intrinsics.checkParameterIsNotNull(mediaControlPanelFactory, "mediaControlPanelFactory");
        Intrinsics.checkParameterIsNotNull(visualStabilityManager, "visualStabilityManager");
        Intrinsics.checkParameterIsNotNull(mediaHostStatesManager, "mediaHostStatesManager");
        Intrinsics.checkParameterIsNotNull(activityStarter, "activityStarter");
        Intrinsics.checkParameterIsNotNull(executor, "executor");
        Intrinsics.checkParameterIsNotNull(mediaManager, "mediaManager");
        Intrinsics.checkParameterIsNotNull(configurationController, "configurationController");
        Intrinsics.checkParameterIsNotNull(falsingManager, "falsingManager");
        this.context = context;
        this.mediaControlPanelFactory = mediaControlPanelFactory;
        this.visualStabilityManager = visualStabilityManager;
        this.mediaHostStatesManager = mediaHostStatesManager;
        this.activityStarter = activityStarter;
        this.desiredLocation = -1;
        this.currentEndLocation = -1;
        this.currentStartLocation = -1;
        this.currentTransitionProgress = 1.0f;
        this.mediaPlayers = new LinkedHashMap();
        this.mediaData = new LinkedHashMap();
        this.currentlyExpanded = true;
        this.configListener = new ConfigurationController.ConfigurationListener() { // from class: com.android.systemui.media.MediaCarouselController$configListener$1
            @Override // com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
            public void onDensityOrFontScaleChanged() {
                this.this$0.recreatePlayers();
                this.this$0.inflateSettingsButton();
            }

            @Override // com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
            public void onOverlayChanged() {
                this.this$0.inflateSettingsButton();
            }

            @Override // com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
            public void onConfigChanged(@Nullable Configuration newConfig) {
                if (newConfig == null) {
                    return;
                }
                this.this$0.setRtl(newConfig.getLayoutDirection() == 1);
            }
        };
        this.mediaFrame = inflateMediaCarousel();
        View viewRequireViewById = this.mediaFrame.requireViewById(R.id.media_carousel_scroller);
        Intrinsics.checkExpressionValueIsNotNull(viewRequireViewById, "mediaFrame.requireViewBy….media_carousel_scroller)");
        this.mediaCarousel = (MediaScrollView) viewRequireViewById;
        View viewRequireViewById2 = this.mediaFrame.requireViewById(R.id.media_page_indicator);
        Intrinsics.checkExpressionValueIsNotNull(viewRequireViewById2, "mediaFrame.requireViewBy….id.media_page_indicator)");
        this.pageIndicator = (PageIndicator) viewRequireViewById2;
        this.mediaCarouselScrollHandler = new MediaCarouselScrollHandler(this.mediaCarousel, this.pageIndicator, executor, new AnonymousClass1(mediaManager), new AnonymousClass2(this), falsingManager);
        Resources resources = this.context.getResources();
        Intrinsics.checkExpressionValueIsNotNull(resources, "context.resources");
        Configuration configuration = resources.getConfiguration();
        Intrinsics.checkExpressionValueIsNotNull(configuration, "context.resources.configuration");
        setRtl(configuration.getLayoutDirection() == 1);
        inflateSettingsButton();
        View viewRequireViewById3 = this.mediaCarousel.requireViewById(R.id.media_carousel);
        Intrinsics.checkExpressionValueIsNotNull(viewRequireViewById3, "mediaCarousel.requireViewById(R.id.media_carousel)");
        this.mediaContent = (ViewGroup) viewRequireViewById3;
        configurationController.addCallback(this.configListener);
        this.visualStabilityCallback = new VisualStabilityManager.Callback() { // from class: com.android.systemui.media.MediaCarouselController.3
            @Override // com.android.systemui.statusbar.notification.VisualStabilityManager.Callback
            public final void onChangeAllowed() {
                if (MediaCarouselController.this.needsReordering) {
                    MediaCarouselController.this.needsReordering = false;
                    MediaCarouselController.this.reorderAllPlayers();
                }
                MediaCarouselController.this.mediaCarouselScrollHandler.scrollToStart();
            }
        };
        this.visualStabilityManager.addReorderingAllowedCallback(this.visualStabilityCallback, true);
        mediaManager.addListener(new MediaDataManager.Listener() { // from class: com.android.systemui.media.MediaCarouselController.4
            @Override // com.android.systemui.media.MediaDataManager.Listener
            public void onMediaDataLoaded(@NotNull String key, @Nullable String oldKey, @NotNull MediaData data) {
                Intrinsics.checkParameterIsNotNull(key, "key");
                Intrinsics.checkParameterIsNotNull(data, "data");
                if (oldKey != null) {
                }
                if (data.getActive() || Utils.useMediaResumption(MediaCarouselController.this.context)) {
                    MediaCarouselController.this.mediaData.put(key, data);
                    MediaCarouselController.this.addOrUpdatePlayer(key, oldKey, data);
                } else {
                    onMediaDataRemoved(key);
                }
            }

            @Override // com.android.systemui.media.MediaDataManager.Listener
            public void onMediaDataRemoved(@NotNull String key) {
                Intrinsics.checkParameterIsNotNull(key, "key");
                MediaCarouselController.this.mediaData.remove(key);
                MediaCarouselController.this.removePlayer(key);
            }
        });
        this.mediaFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { // from class: com.android.systemui.media.MediaCarouselController.5
            @Override // android.view.View.OnLayoutChangeListener
            public final void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                MediaCarouselController.this.updatePageIndicatorLocation();
            }
        });
        this.mediaHostStatesManager.addCallback(new MediaHostStatesManager.Callback() { // from class: com.android.systemui.media.MediaCarouselController.6
            @Override // com.android.systemui.media.MediaHostStatesManager.Callback
            public void onHostStateChanged(int location, @NotNull MediaHostState mediaHostState) {
                Intrinsics.checkParameterIsNotNull(mediaHostState, "mediaHostState");
                if (location == MediaCarouselController.this.desiredLocation) {
                    MediaCarouselController mediaCarouselController = MediaCarouselController.this;
                    MediaCarouselController.onDesiredLocationChanged$default(mediaCarouselController, mediaCarouselController.desiredLocation, mediaHostState, false, 0L, 0L, 24, null);
                }
            }
        });
    }

    public static final /* synthetic */ View access$getSettingsButton$p(MediaCarouselController mediaCarouselController) {
        View view = mediaCarouselController.settingsButton;
        if (view == null) {
            Intrinsics.throwUninitializedPropertyAccessException("settingsButton");
        }
        return view;
    }

    @NotNull
    public final ViewGroup getMediaFrame() {
        return this.mediaFrame;
    }

    @NotNull
    public final Map<String, MediaControlPanel> getMediaPlayers() {
        return this.mediaPlayers;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void setRtl(boolean z) {
        if (z != this.isRtl) {
            this.isRtl = z;
            this.mediaFrame.setLayoutDirection(z ? 1 : 0);
            this.mediaCarouselScrollHandler.scrollToStart();
        }
    }

    private final void setCurrentlyExpanded(boolean z) {
        if (this.currentlyExpanded != z) {
            this.currentlyExpanded = z;
            Iterator<MediaControlPanel> it = this.mediaPlayers.values().iterator();
            while (it.hasNext()) {
                it.next().setListening(this.currentlyExpanded);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void inflateSettingsButton() {
        View viewInflate = LayoutInflater.from(this.context).inflate(R.layout.media_carousel_settings_button, this.mediaFrame, false);
        if (viewInflate == null) {
            throw new TypeCastException("null cannot be cast to non-null type android.view.View");
        }
        if (this.settingsButton != null) {
            ViewGroup viewGroup = this.mediaFrame;
            View view = this.settingsButton;
            if (view == null) {
                Intrinsics.throwUninitializedPropertyAccessException("settingsButton");
            }
            viewGroup.removeView(view);
        }
        this.settingsButton = viewInflate;
        ViewGroup viewGroup2 = this.mediaFrame;
        View view2 = this.settingsButton;
        if (view2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("settingsButton");
        }
        viewGroup2.addView(view2);
        this.mediaCarouselScrollHandler.onSettingsButtonUpdated(viewInflate);
        View view3 = this.settingsButton;
        if (view3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("settingsButton");
        }
        view3.setOnClickListener(new View.OnClickListener() { // from class: com.android.systemui.media.MediaCarouselController.inflateSettingsButton.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view4) {
                MediaCarouselController.this.activityStarter.startActivity(MediaCarouselControllerKt.settingsIntent, true);
            }
        });
    }

    /* JADX INFO: renamed from: com.android.systemui.media.MediaCarouselController$inflateSettingsButton$1, reason: invalid class name and case insensitive filesystem */
    /* JADX INFO: compiled from: MediaCarouselController.kt */
    @Metadata(bv = {1, 0, 3}, k = 3, mv = {1, 1, 16})
    final /* synthetic */ class C01261 extends MutablePropertyReference0 {
        C01261(MediaCarouselController mediaCarouselController) {
            super(mediaCarouselController);
        }

        @Override // kotlin.jvm.internal.CallableReference, kotlin.reflect.KCallable
        public String getName() {
            return "settingsButton";
        }

        @Override // kotlin.jvm.internal.CallableReference
        public KDeclarationContainer getOwner() {
            return Reflection.getOrCreateKotlinClass(MediaCarouselController.class);
        }

        @Override // kotlin.jvm.internal.CallableReference
        public String getSignature() {
            return "getSettingsButton()Landroid/view/View;";
        }

        @Override // kotlin.reflect.KProperty0
        @Nullable
        public Object get() {
            return MediaCarouselController.access$getSettingsButton$p((MediaCarouselController) this.receiver);
        }

        @Override // kotlin.reflect.KMutableProperty0
        public void set(@Nullable Object obj) {
            ((MediaCarouselController) this.receiver).settingsButton = (View) obj;
        }
    }

    private final ViewGroup inflateMediaCarousel() {
        View viewInflate = LayoutInflater.from(this.context).inflate(R.layout.media_carousel, (ViewGroup) new UniqueObjectHostView(this.context), false);
        if (viewInflate == null) {
            throw new TypeCastException("null cannot be cast to non-null type android.view.ViewGroup");
        }
        ViewGroup viewGroup = (ViewGroup) viewInflate;
        viewGroup.setLayoutDirection(3);
        return viewGroup;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void reorderAllPlayers() {
        for (MediaControlPanel mediaControlPanel : this.mediaPlayers.values()) {
            PlayerViewHolder view = mediaControlPanel.getView();
            TransitionLayout player = view != null ? view.getPlayer() : null;
            if (mediaControlPanel.isPlaying()) {
                TransitionLayout transitionLayout = player;
                if (this.mediaContent.indexOfChild(transitionLayout) != 0) {
                    this.mediaContent.removeView(transitionLayout);
                    this.mediaContent.addView(transitionLayout, 0);
                }
            }
        }
        this.mediaCarouselScrollHandler.onPlayersChanged();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void addOrUpdatePlayer(String key, String oldKey, MediaData data) {
        TransitionLayout player;
        if (this.mediaPlayers.get(oldKey) != null) {
            Map<String, MediaControlPanel> map = this.mediaPlayers;
            if (map == null) {
                throw new TypeCastException("null cannot be cast to non-null type kotlin.collections.MutableMap<K, V>");
            }
            MediaControlPanel mediaControlPanel = (MediaControlPanel) TypeIntrinsics.asMutableMap(map).remove(oldKey);
            Map<String, MediaControlPanel> map2 = this.mediaPlayers;
            if (mediaControlPanel == null) {
                Intrinsics.throwNpe();
            }
            if (map2.put(key, mediaControlPanel) != null) {
                Log.wtf("MediaCarouselController", "new key " + key + " already exists when migrating from " + oldKey);
            }
        }
        MediaControlPanel mediaControlPanel2 = this.mediaPlayers.get(key);
        if (mediaControlPanel2 == null) {
            MediaControlPanel existingPlayer = this.mediaControlPanelFactory.get();
            PlayerViewHolder.Companion companion = PlayerViewHolder.INSTANCE;
            LayoutInflater layoutInflaterFrom = LayoutInflater.from(this.context);
            Intrinsics.checkExpressionValueIsNotNull(layoutInflaterFrom, "LayoutInflater.from(context)");
            existingPlayer.attach(companion.create(layoutInflaterFrom, this.mediaContent));
            Intrinsics.checkExpressionValueIsNotNull(existingPlayer, "existingPlayer");
            existingPlayer.getMediaViewController().setSizeChangedListener(new C01252(this));
            this.mediaPlayers.put(key, existingPlayer);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            PlayerViewHolder view = existingPlayer.getView();
            if (view != null && (player = view.getPlayer()) != null) {
                player.setLayoutParams(layoutParams);
            }
            existingPlayer.bind(data);
            existingPlayer.setListening(this.currentlyExpanded);
            updatePlayerToState(existingPlayer, true);
            if (existingPlayer.isPlaying()) {
                ViewGroup viewGroup = this.mediaContent;
                PlayerViewHolder view2 = existingPlayer.getView();
                viewGroup.addView(view2 != null ? view2.getPlayer() : null, 0);
            } else {
                ViewGroup viewGroup2 = this.mediaContent;
                PlayerViewHolder view3 = existingPlayer.getView();
                viewGroup2.addView(view3 != null ? view3.getPlayer() : null);
            }
        } else {
            mediaControlPanel2.bind(data);
            if (mediaControlPanel2.isPlaying()) {
                ViewGroup viewGroup3 = this.mediaContent;
                PlayerViewHolder view4 = mediaControlPanel2.getView();
                if (viewGroup3.indexOfChild(view4 != null ? view4.getPlayer() : null) != 0) {
                    if (this.visualStabilityManager.isReorderingAllowed()) {
                        ViewGroup viewGroup4 = this.mediaContent;
                        PlayerViewHolder view5 = mediaControlPanel2.getView();
                        viewGroup4.removeView(view5 != null ? view5.getPlayer() : null);
                        ViewGroup viewGroup5 = this.mediaContent;
                        PlayerViewHolder view6 = mediaControlPanel2.getView();
                        viewGroup5.addView(view6 != null ? view6.getPlayer() : null, 0);
                    } else {
                        this.needsReordering = true;
                    }
                }
            }
        }
        updatePageIndicator();
        this.mediaCarouselScrollHandler.onPlayersChanged();
        UniqueObjectHostViewKt.setRequiresRemeasuring(this.mediaCarousel, true);
        if (this.mediaPlayers.size() != this.mediaContent.getChildCount()) {
            Log.wtf("MediaCarouselController", "Size of players list and number of views in carousel are out of sync");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void removePlayer(String key) {
        MediaControlPanel mediaControlPanelRemove = this.mediaPlayers.remove(key);
        if (mediaControlPanelRemove != null) {
            this.mediaCarouselScrollHandler.onPrePlayerRemoved(mediaControlPanelRemove);
            ViewGroup viewGroup = this.mediaContent;
            PlayerViewHolder view = mediaControlPanelRemove.getView();
            viewGroup.removeView(view != null ? view.getPlayer() : null);
            mediaControlPanelRemove.onDestroy();
            this.mediaCarouselScrollHandler.onPlayersChanged();
            updatePageIndicator();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void recreatePlayers() {
        this.mediaData.forEach(new BiConsumer<String, MediaData>() { // from class: com.android.systemui.media.MediaCarouselController.recreatePlayers.1
            @Override // java.util.function.BiConsumer
            public final void accept(@NotNull String key, @NotNull MediaData data) {
                Intrinsics.checkParameterIsNotNull(key, "key");
                Intrinsics.checkParameterIsNotNull(data, "data");
                MediaCarouselController.this.removePlayer(key);
                MediaCarouselController.this.addOrUpdatePlayer(key, null, data);
            }
        });
    }

    private final void updatePageIndicator() {
        int childCount = this.mediaContent.getChildCount();
        this.pageIndicator.setNumPages(childCount, -1);
        if (childCount == 1) {
            this.pageIndicator.setLocation(0.0f);
        }
        updatePageIndicatorAlpha();
    }

    public final void setCurrentState(int startLocation, int endLocation, float progress, boolean immediately) {
        if (startLocation == this.currentStartLocation && endLocation == this.currentEndLocation && progress == this.currentTransitionProgress && !immediately) {
            return;
        }
        this.currentStartLocation = startLocation;
        this.currentEndLocation = endLocation;
        this.currentTransitionProgress = progress;
        Iterator<MediaControlPanel> it = this.mediaPlayers.values().iterator();
        while (it.hasNext()) {
            updatePlayerToState(it.next(), immediately);
        }
        maybeResetSettingsCog();
        updatePageIndicatorAlpha();
    }

    private final void updatePageIndicatorAlpha() {
        Map<Integer, MediaHostState> mediaHostStates = this.mediaHostStatesManager.getMediaHostStates();
        MediaHostState mediaHostState = mediaHostStates.get(Integer.valueOf(this.currentEndLocation));
        boolean visible = mediaHostState != null ? mediaHostState.getVisible() : false;
        MediaHostState mediaHostState2 = mediaHostStates.get(Integer.valueOf(this.currentStartLocation));
        boolean visible2 = mediaHostState2 != null ? mediaHostState2.getVisible() : false;
        float fLerp = 1.0f;
        float f = visible2 ? 1.0f : 0.0f;
        float f2 = visible ? 1.0f : 0.0f;
        if (!visible || !visible2) {
            float f3 = this.currentTransitionProgress;
            if (!visible) {
                f3 = 1.0f - f3;
            }
            fLerp = MathUtils.lerp(f, f2, MathUtils.constrain(MathUtils.map(0.95f, 1.0f, 0.0f, 1.0f, f3), 0.0f, 1.0f));
        }
        this.pageIndicator.setAlpha(fLerp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void updatePageIndicatorLocation() {
        int width;
        int width2;
        if (this.isRtl) {
            width = this.pageIndicator.getWidth();
            width2 = this.currentCarouselWidth;
        } else {
            width = this.currentCarouselWidth;
            width2 = this.pageIndicator.getWidth();
        }
        this.pageIndicator.setTranslationX(((width - width2) / 2.0f) + this.mediaCarouselScrollHandler.getContentTranslation());
        if (this.pageIndicator.getLayoutParams() == null) {
            throw new TypeCastException("null cannot be cast to non-null type android.view.ViewGroup.MarginLayoutParams");
        }
        this.pageIndicator.setTranslationY((this.currentCarouselHeight - r1.getHeight()) - ((ViewGroup.MarginLayoutParams) r0).bottomMargin);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void updateCarouselDimensions() {
        Iterator<MediaControlPanel> it = this.mediaPlayers.values().iterator();
        int iMax = 0;
        int iMax2 = 0;
        while (it.hasNext()) {
            MediaViewController mediaViewController = it.next().getMediaViewController();
            Intrinsics.checkExpressionValueIsNotNull(mediaViewController, "mediaPlayer.mediaViewController");
            iMax = Math.max(iMax, mediaViewController.getCurrentWidth() + ((int) mediaViewController.getTranslationX()));
            iMax2 = Math.max(iMax2, mediaViewController.getCurrentHeight() + ((int) mediaViewController.getTranslationY()));
        }
        if (iMax == this.currentCarouselWidth && iMax2 == this.currentCarouselHeight) {
            return;
        }
        this.currentCarouselWidth = iMax;
        this.currentCarouselHeight = iMax2;
        this.mediaCarouselScrollHandler.setCarouselBounds(this.currentCarouselWidth, this.currentCarouselHeight);
        updatePageIndicatorLocation();
    }

    private final void maybeResetSettingsCog() {
        Map<Integer, MediaHostState> mediaHostStates = this.mediaHostStatesManager.getMediaHostStates();
        MediaHostState mediaHostState = mediaHostStates.get(Integer.valueOf(this.currentEndLocation));
        boolean showsOnlyActiveMedia = mediaHostState != null ? mediaHostState.getShowsOnlyActiveMedia() : true;
        MediaHostState mediaHostState2 = mediaHostStates.get(Integer.valueOf(this.currentStartLocation));
        boolean showsOnlyActiveMedia2 = mediaHostState2 != null ? mediaHostState2.getShowsOnlyActiveMedia() : showsOnlyActiveMedia;
        if (this.currentlyShowingOnlyActive == showsOnlyActiveMedia) {
            float f = this.currentTransitionProgress;
            if (f == 1.0f || f == 0.0f || showsOnlyActiveMedia2 == showsOnlyActiveMedia) {
                return;
            }
        }
        this.currentlyShowingOnlyActive = showsOnlyActiveMedia;
        this.mediaCarouselScrollHandler.resetTranslation(true);
    }

    private final void updatePlayerToState(MediaControlPanel mediaPlayer, boolean noAnimation) {
        mediaPlayer.getMediaViewController().setCurrentState(this.currentStartLocation, this.currentEndLocation, this.currentTransitionProgress, noAnimation);
    }

    public static /* synthetic */ void onDesiredLocationChanged$default(MediaCarouselController mediaCarouselController, int i, MediaHostState mediaHostState, boolean z, long j, long j2, int i2, Object obj) {
        mediaCarouselController.onDesiredLocationChanged(i, mediaHostState, z, (i2 & 8) != 0 ? 200L : j, (i2 & 16) != 0 ? 0L : j2);
    }

    public final void onDesiredLocationChanged(int desiredLocation, @Nullable MediaHostState desiredHostState, boolean animate, long duration, long startDelay) {
        if (desiredHostState != null) {
            this.desiredLocation = desiredLocation;
            this.desiredHostState = desiredHostState;
            setCurrentlyExpanded(desiredHostState.getExpansion() > ((float) 0));
            for (MediaControlPanel mediaControlPanel : this.mediaPlayers.values()) {
                if (animate) {
                    mediaControlPanel.getMediaViewController().animatePendingStateChange(duration, startDelay);
                }
                mediaControlPanel.getMediaViewController().onLocationPreChange(desiredLocation);
            }
            this.mediaCarouselScrollHandler.setShowsSettingsButton(!desiredHostState.getShowsOnlyActiveMedia());
            this.mediaCarouselScrollHandler.setFalsingProtectionNeeded(desiredHostState.getFalsingProtectionNeeded());
            boolean visible = desiredHostState.getVisible();
            if (visible != this.playersVisible) {
                this.playersVisible = visible;
                if (visible) {
                    MediaCarouselScrollHandler.resetTranslation$default(this.mediaCarouselScrollHandler, false, 1, null);
                }
            }
            updateCarouselSize();
        }
    }

    private final void updateCarouselSize() {
        MeasurementInput measurementInput;
        MeasurementInput measurementInput2;
        MeasurementInput measurementInput3;
        MeasurementInput measurementInput4;
        MediaHostState mediaHostState = this.desiredHostState;
        int width = (mediaHostState == null || (measurementInput4 = mediaHostState.getMeasurementInput()) == null) ? 0 : measurementInput4.getWidth();
        MediaHostState mediaHostState2 = this.desiredHostState;
        int height = (mediaHostState2 == null || (measurementInput3 = mediaHostState2.getMeasurementInput()) == null) ? 0 : measurementInput3.getHeight();
        if ((width == this.carouselMeasureWidth || width == 0) && (height == this.carouselMeasureHeight || height == 0)) {
            return;
        }
        this.carouselMeasureWidth = width;
        this.carouselMeasureHeight = height;
        int dimensionPixelSize = this.carouselMeasureWidth + this.context.getResources().getDimensionPixelSize(R.dimen.qs_media_padding);
        MediaHostState mediaHostState3 = this.desiredHostState;
        int widthMeasureSpec = (mediaHostState3 == null || (measurementInput2 = mediaHostState3.getMeasurementInput()) == null) ? 0 : measurementInput2.getWidthMeasureSpec();
        MediaHostState mediaHostState4 = this.desiredHostState;
        this.mediaCarousel.measure(widthMeasureSpec, (mediaHostState4 == null || (measurementInput = mediaHostState4.getMeasurementInput()) == null) ? 0 : measurementInput.getHeightMeasureSpec());
        MediaScrollView mediaScrollView = this.mediaCarousel;
        mediaScrollView.layout(0, 0, width, mediaScrollView.getMeasuredHeight());
        this.mediaCarouselScrollHandler.setPlayerWidthPlusPadding(dimensionPixelSize);
    }
}
