package android.app;

import android.Manifest;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.PlayerBase;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.FtBuild;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TextAppearanceSpan;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.widget.RemoteViews;
import com.android.internal.R;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.ContrastColorUtil;
import com.vivo.VivoSystemFrameworkFactory;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/* JADX INFO: loaded from: classes.dex */
public class Notification implements Parcelable {
    public static final AudioAttributes AUDIO_ATTRIBUTES_DEFAULT;
    public static final int BADGE_ICON_LARGE = 2;
    public static final int BADGE_ICON_NONE = 0;
    public static final int BADGE_ICON_SMALL = 1;
    public static final String CATEGORY_ALARM = "alarm";
    public static final String CATEGORY_CALL = "call";

    @SystemApi
    public static final String CATEGORY_CAR_EMERGENCY = "car_emergency";

    @SystemApi
    public static final String CATEGORY_CAR_INFORMATION = "car_information";

    @SystemApi
    public static final String CATEGORY_CAR_WARNING = "car_warning";
    public static final String CATEGORY_EMAIL = "email";
    public static final String CATEGORY_ERROR = "err";
    public static final String CATEGORY_EVENT = "event";
    public static final String CATEGORY_MESSAGE = "msg";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_PROGRESS = "progress";
    public static final String CATEGORY_PROMO = "promo";
    public static final String CATEGORY_RECOMMENDATION = "recommendation";
    public static final String CATEGORY_REMINDER = "reminder";
    public static final String CATEGORY_SERVICE = "service";
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_STATUS = "status";
    public static final String CATEGORY_SYSTEM = "sys";
    public static final String CATEGORY_TRANSPORT = "transport";
    public static final int COLOR_DEFAULT = 0;
    public static final int COLOR_INVALID = 1;
    public static final Parcelable.Creator<Notification> CREATOR;
    public static final int DEFAULT_ALL = -1;
    public static final int DEFAULT_LIGHTS = 4;
    public static final int DEFAULT_SOUND = 1;
    public static final int DEFAULT_VIBRATE = 2;

    @SystemApi
    public static final String EXTRA_ALLOW_DURING_SETUP = "android.allowDuringSetup";
    public static final String EXTRA_AUDIO_CONTENTS_URI = "android.audioContents";
    public static final String EXTRA_BACKGROUND_IMAGE_URI = "android.backgroundImageUri";
    public static final String EXTRA_BIG_TEXT = "android.bigText";
    public static final String EXTRA_BUILDER_APPLICATION_INFO = "android.appInfo";
    public static final String EXTRA_CHANNEL_GROUP_ID = "android.intent.extra.CHANNEL_GROUP_ID";
    public static final String EXTRA_CHANNEL_ID = "android.intent.extra.CHANNEL_ID";
    public static final String EXTRA_CHRONOMETER_COUNT_DOWN = "android.chronometerCountDown";
    public static final String EXTRA_COLORIZED = "android.colorized";
    public static final String EXTRA_COMPACT_ACTIONS = "android.compactActions";
    public static final String EXTRA_CONTAINS_CUSTOM_VIEW = "android.contains.customView";
    public static final String EXTRA_CONVERSATION_ICON = "android.conversationIcon";
    public static final String EXTRA_CONVERSATION_TITLE = "android.conversationTitle";
    public static final String EXTRA_CONVERSATION_UNREAD_MESSAGE_COUNT = "android.conversationUnreadMessageCount";
    public static final String EXTRA_FOREGROUND_APPS = "android.foregroundApps";
    public static final String EXTRA_HIDE_SMART_REPLIES = "android.hideSmartReplies";
    public static final String EXTRA_HISTORIC_MESSAGES = "android.messages.historic";
    public static final String EXTRA_INFO_TEXT = "android.infoText";
    public static final String EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation";

    @Deprecated
    public static final String EXTRA_LARGE_ICON = "android.largeIcon";
    public static final String EXTRA_LARGE_ICON_BIG = "android.largeIcon.big";
    public static final String EXTRA_MEDIA_SESSION = "android.mediaSession";
    public static final String EXTRA_MESSAGES = "android.messages";
    public static final String EXTRA_MESSAGING_PERSON = "android.messagingUser";
    public static final String EXTRA_NOTIFICATION_ID = "android.intent.extra.NOTIFICATION_ID";
    public static final String EXTRA_NOTIFICATION_TAG = "android.intent.extra.NOTIFICATION_TAG";
    public static final String EXTRA_PEOPLE = "android.people";
    public static final String EXTRA_PEOPLE_LIST = "android.people.list";
    public static final String EXTRA_PICTURE = "android.picture";
    public static final String EXTRA_PROGRESS = "android.progress";
    public static final String EXTRA_PROGRESS_INDETERMINATE = "android.progressIndeterminate";
    public static final String EXTRA_PROGRESS_MAX = "android.progressMax";
    public static final String EXTRA_REDUCED_IMAGES = "android.reduced.images";
    public static final String EXTRA_REMOTE_INPUT_DRAFT = "android.remoteInputDraft";
    public static final String EXTRA_REMOTE_INPUT_HISTORY = "android.remoteInputHistory";
    public static final String EXTRA_REMOTE_INPUT_HISTORY_ITEMS = "android.remoteInputHistoryItems";
    public static final String EXTRA_SELF_DISPLAY_NAME = "android.selfDisplayName";
    public static final String EXTRA_SHOW_CHRONOMETER = "android.showChronometer";
    public static final String EXTRA_SHOW_REMOTE_INPUT_SPINNER = "android.remoteInputSpinner";
    public static final String EXTRA_SHOW_WHEN = "android.showWhen";

    @Deprecated
    public static final String EXTRA_SMALL_ICON = "android.icon";

    @SystemApi
    public static final String EXTRA_SUBSTITUTE_APP_NAME = "android.substName";
    public static final String EXTRA_SUB_TEXT = "android.subText";
    public static final String EXTRA_SUMMARY_TEXT = "android.summaryText";
    public static final String EXTRA_TEMPLATE = "android.template";
    public static final String EXTRA_TEXT = "android.text";
    public static final String EXTRA_TEXT_LINES = "android.textLines";
    public static final String EXTRA_TITLE = "android.title";
    public static final String EXTRA_TITLE_BIG = "android.title.big";

    @SystemApi
    public static final int FLAG_AUTOGROUP_SUMMARY = 1024;
    public static final int FLAG_AUTO_CANCEL = 16;
    public static final int FLAG_BUBBLE = 4096;
    public static final int FLAG_CAN_COLORIZE = 2048;
    public static final int FLAG_FOREGROUND_SERVICE = 64;
    public static final int FLAG_GROUP_SUMMARY = 512;

    @Deprecated
    public static final int FLAG_HIGH_PRIORITY = 128;
    public static final int FLAG_INSISTENT = 4;
    public static final int FLAG_LOCAL_ONLY = 256;
    public static final int FLAG_NO_CLEAR = 32;
    public static final int FLAG_ONGOING_EVENT = 2;
    public static final int FLAG_ONLY_ALERT_ONCE = 8;

    @Deprecated
    public static final int FLAG_SHOW_LIGHTS = 1;
    public static final int GROUP_ALERT_ALL = 0;
    public static final int GROUP_ALERT_CHILDREN = 2;
    public static final int GROUP_ALERT_SUMMARY = 1;
    public static final String INTENT_CATEGORY_NOTIFICATION_PREFERENCES = "android.intent.category.NOTIFICATION_PREFERENCES";
    public static final boolean IS_TIER_VOS;
    public static final int MAX_ACTION_BUTTONS = 3;
    private static final int MAX_CHARSEQUENCE_LENGTH = 1024;
    private static final int MAX_REPLY_HISTORY = 5;

    @Deprecated
    public static final int PRIORITY_DEFAULT = 0;

    @Deprecated
    public static final int PRIORITY_HIGH = 1;

    @Deprecated
    public static final int PRIORITY_LOW = -1;

    @Deprecated
    public static final int PRIORITY_MAX = 2;

    @Deprecated
    public static final int PRIORITY_MIN = -2;
    private static final ArraySet<Integer> STANDARD_LAYOUTS;

    @Deprecated
    public static final int STREAM_DEFAULT = -1;
    private static final String TAG = "Notification";
    public static final int VISIBILITY_PRIVATE = 0;
    public static final int VISIBILITY_PUBLIC = 1;
    public static final int VISIBILITY_SECRET = -1;
    public static IBinder processWhitelistToken;
    private static IVivoNotification sVivoNotification;
    public Action[] actions;
    public ArraySet<PendingIntent> allPendingIntents;

    @Deprecated
    public AudioAttributes audioAttributes;

    @Deprecated
    public int audioStreamType;

    @Deprecated
    public RemoteViews bigContentView;
    public String category;
    public int color;
    public PendingIntent contentIntent;

    @Deprecated
    public RemoteViews contentView;
    private long creationTime;

    @Deprecated
    public int defaults;
    public PendingIntent deleteIntent;
    public Bundle extras;
    public int flags;
    public PendingIntent fullScreenIntent;

    @Deprecated
    public RemoteViews headsUpContentView;

    @Deprecated
    public int icon;
    public int iconLevel;
    public int internalFlag;
    public int internalGroupPriority;
    public int internalPriority;
    public int internalType;
    public int isKeyguardNotification;
    public int isStandardNotification;

    @Deprecated
    public Bitmap largeIcon;

    @Deprecated
    public int ledARGB;

    @Deprecated
    public int ledOffMS;

    @Deprecated
    public int ledOnMS;
    private boolean mAllowSystemGeneratedContextualActions;
    private int mBadgeIcon;
    private BubbleMetadata mBubbleMetadata;
    private String mChannelId;
    private int mGroupAlertBehavior;
    private String mGroupKey;
    private Icon mLargeIcon;
    private LocusId mLocusId;
    private CharSequence mSettingsText;
    private String mShortcutId;
    private Icon mSmallIcon;
    private String mSortKey;
    private long mTimeout;
    private boolean mUsesStandardHeader;
    private IBinder mWhitelistToken;
    public int number;

    @Deprecated
    public int priority;
    public Notification publicVersion;

    @Deprecated
    public Uri sound;
    public CharSequence tickerText;

    @Deprecated
    public RemoteViews tickerView;

    @Deprecated
    public long[] vibrate;
    public int visibility;
    public long when;

    public interface Extender {
        Builder extend(Builder builder);
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface GroupAlertBehavior {
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationFlags {
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface Priority {
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface Visibility {
    }

    static {
        IVivoNotification iVivoNotificationCreateVivoNotification;
        ArraySet<Integer> arraySet = new ArraySet<>();
        STANDARD_LAYOUTS = arraySet;
        arraySet.add(Integer.valueOf(R.layout.notification_template_material_base));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_big_base));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_big_picture));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_big_text));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_inbox));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_messaging));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_conversation));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_media));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_material_big_media));
        STANDARD_LAYOUTS.add(Integer.valueOf(R.layout.notification_template_header));
        AUDIO_ATTRIBUTES_DEFAULT = new AudioAttributes.Builder().setContentType(4).setUsage(5).build();
        if (VivoSystemFrameworkFactory.getSystemFrameworkFactoryImpl() != null) {
            iVivoNotificationCreateVivoNotification = VivoSystemFrameworkFactory.getSystemFrameworkFactoryImpl().createVivoNotification();
        } else {
            iVivoNotificationCreateVivoNotification = null;
        }
        sVivoNotification = iVivoNotificationCreateVivoNotification;
        CREATOR = new Parcelable.Creator<Notification>() { // from class: android.app.Notification.1
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public Notification createFromParcel(Parcel parcel) {
                return new Notification(parcel);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public Notification[] newArray(int size) {
                return new Notification[size];
            }
        };
        IS_TIER_VOS = FtBuild.getTierLevel() != 0 && FtBuild.getOsName().equalsIgnoreCase("vos");
    }

    public String getGroup() {
        return this.mGroupKey;
    }

    public String getSortKey() {
        return this.mSortKey;
    }

    public static class Action implements Parcelable {
        public static final Parcelable.Creator<Action> CREATOR = new Parcelable.Creator<Action>() { // from class: android.app.Notification.Action.1
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public Action createFromParcel(Parcel in) {
                return new Action(in);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public Action[] newArray(int size) {
                return new Action[size];
            }
        };
        private static final String EXTRA_DATA_ONLY_INPUTS = "android.extra.DATA_ONLY_INPUTS";
        public static final int SEMANTIC_ACTION_ARCHIVE = 5;
        public static final int SEMANTIC_ACTION_CALL = 10;
        public static final int SEMANTIC_ACTION_DELETE = 4;
        public static final int SEMANTIC_ACTION_MARK_AS_READ = 2;
        public static final int SEMANTIC_ACTION_MARK_AS_UNREAD = 3;
        public static final int SEMANTIC_ACTION_MUTE = 6;
        public static final int SEMANTIC_ACTION_NONE = 0;
        public static final int SEMANTIC_ACTION_REPLY = 1;
        public static final int SEMANTIC_ACTION_THUMBS_DOWN = 9;
        public static final int SEMANTIC_ACTION_THUMBS_UP = 8;
        public static final int SEMANTIC_ACTION_UNMUTE = 7;
        public PendingIntent actionIntent;

        @Deprecated
        public int icon;
        private boolean mAllowGeneratedReplies;
        private final Bundle mExtras;
        private Icon mIcon;
        private final boolean mIsContextual;
        private final RemoteInput[] mRemoteInputs;
        private final int mSemanticAction;
        public CharSequence title;

        public interface Extender {
            Builder extend(Builder builder);
        }

        @Retention(RetentionPolicy.SOURCE)
        public @interface SemanticAction {
        }

        private Action(Parcel in) {
            this.mAllowGeneratedReplies = true;
            if (in.readInt() != 0) {
                Icon iconCreateFromParcel = Icon.CREATOR.createFromParcel(in);
                this.mIcon = iconCreateFromParcel;
                if (iconCreateFromParcel.getType() == 2) {
                    this.icon = this.mIcon.getResId();
                }
            }
            this.title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            if (in.readInt() == 1) {
                this.actionIntent = PendingIntent.CREATOR.createFromParcel(in);
            }
            this.mExtras = Bundle.setDefusable(in.readBundle(), true);
            this.mRemoteInputs = (RemoteInput[]) in.createTypedArray(RemoteInput.CREATOR);
            this.mAllowGeneratedReplies = in.readInt() == 1;
            this.mSemanticAction = in.readInt();
            this.mIsContextual = in.readInt() == 1;
        }

        @Deprecated
        public Action(int icon, CharSequence title, PendingIntent intent) {
            this(Icon.createWithResource("", icon), title, intent, new Bundle(), null, true, 0, false);
        }

        private Action(Icon icon, CharSequence title, PendingIntent intent, Bundle extras, RemoteInput[] remoteInputs, boolean allowGeneratedReplies, int semanticAction, boolean isContextual) {
            this.mAllowGeneratedReplies = true;
            this.mIcon = icon;
            if (icon != null && icon.getType() == 2) {
                this.icon = icon.getResId();
            }
            this.title = title;
            this.actionIntent = intent;
            this.mExtras = extras != null ? extras : new Bundle();
            this.mRemoteInputs = remoteInputs;
            this.mAllowGeneratedReplies = allowGeneratedReplies;
            this.mSemanticAction = semanticAction;
            this.mIsContextual = isContextual;
        }

        public Icon getIcon() {
            int i;
            if (this.mIcon == null && (i = this.icon) != 0) {
                this.mIcon = Icon.createWithResource("", i);
            }
            return this.mIcon;
        }

        public Bundle getExtras() {
            return this.mExtras;
        }

        public boolean getAllowGeneratedReplies() {
            return this.mAllowGeneratedReplies;
        }

        public RemoteInput[] getRemoteInputs() {
            return this.mRemoteInputs;
        }

        public int getSemanticAction() {
            return this.mSemanticAction;
        }

        public boolean isContextual() {
            return this.mIsContextual;
        }

        public RemoteInput[] getDataOnlyRemoteInputs() {
            return (RemoteInput[]) Notification.getParcelableArrayFromBundle(this.mExtras, EXTRA_DATA_ONLY_INPUTS, RemoteInput.class);
        }

        public static final class Builder {
            private boolean mAllowGeneratedReplies;
            private final Bundle mExtras;
            private final Icon mIcon;
            private final PendingIntent mIntent;
            private boolean mIsContextual;
            private ArrayList<RemoteInput> mRemoteInputs;
            private int mSemanticAction;
            private final CharSequence mTitle;

            @Deprecated
            public Builder(int icon, CharSequence title, PendingIntent intent) {
                this(Icon.createWithResource("", icon), title, intent);
            }

            public Builder(Icon icon, CharSequence title, PendingIntent intent) {
                this(icon, title, intent, new Bundle(), null, true, 0);
            }

            public Builder(Action action) {
                this(action.getIcon(), action.title, action.actionIntent, new Bundle(action.mExtras), action.getRemoteInputs(), action.getAllowGeneratedReplies(), action.getSemanticAction());
            }

            private Builder(Icon icon, CharSequence title, PendingIntent intent, Bundle extras, RemoteInput[] remoteInputs, boolean allowGeneratedReplies, int semanticAction) {
                this.mAllowGeneratedReplies = true;
                this.mIcon = icon;
                this.mTitle = title;
                this.mIntent = intent;
                this.mExtras = extras;
                if (remoteInputs != null) {
                    ArrayList<RemoteInput> arrayList = new ArrayList<>(remoteInputs.length);
                    this.mRemoteInputs = arrayList;
                    Collections.addAll(arrayList, remoteInputs);
                }
                this.mAllowGeneratedReplies = allowGeneratedReplies;
                this.mSemanticAction = semanticAction;
            }

            public Builder addExtras(Bundle extras) {
                if (extras != null) {
                    this.mExtras.putAll(extras);
                }
                return this;
            }

            public Bundle getExtras() {
                return this.mExtras;
            }

            public Builder addRemoteInput(RemoteInput remoteInput) {
                if (this.mRemoteInputs == null) {
                    this.mRemoteInputs = new ArrayList<>();
                }
                this.mRemoteInputs.add(remoteInput);
                return this;
            }

            public Builder setAllowGeneratedReplies(boolean allowGeneratedReplies) {
                this.mAllowGeneratedReplies = allowGeneratedReplies;
                return this;
            }

            public Builder setSemanticAction(int semanticAction) {
                this.mSemanticAction = semanticAction;
                return this;
            }

            public Builder setContextual(boolean isContextual) {
                this.mIsContextual = isContextual;
                return this;
            }

            public Builder extend(Extender extender) {
                extender.extend(this);
                return this;
            }

            private void checkContextualActionNullFields() {
                if (this.mIsContextual) {
                    if (this.mIcon == null) {
                        throw new NullPointerException("Contextual Actions must contain a valid icon");
                    }
                    if (this.mIntent == null) {
                        throw new NullPointerException("Contextual Actions must contain a valid PendingIntent");
                    }
                }
            }

            public Action build() {
                checkContextualActionNullFields();
                ArrayList<RemoteInput> dataOnlyInputs = new ArrayList<>();
                RemoteInput[] previousDataInputs = (RemoteInput[]) Notification.getParcelableArrayFromBundle(this.mExtras, Action.EXTRA_DATA_ONLY_INPUTS, RemoteInput.class);
                if (previousDataInputs != null) {
                    for (RemoteInput remoteInput : previousDataInputs) {
                        dataOnlyInputs.add(remoteInput);
                    }
                }
                List<RemoteInput> textInputs = new ArrayList<>();
                ArrayList<RemoteInput> arrayList = this.mRemoteInputs;
                if (arrayList != null) {
                    for (RemoteInput input : arrayList) {
                        if (input.isDataOnly()) {
                            dataOnlyInputs.add(input);
                        } else {
                            textInputs.add(input);
                        }
                    }
                }
                if (!dataOnlyInputs.isEmpty()) {
                    RemoteInput[] dataInputsArr = (RemoteInput[]) dataOnlyInputs.toArray(new RemoteInput[dataOnlyInputs.size()]);
                    this.mExtras.putParcelableArray(Action.EXTRA_DATA_ONLY_INPUTS, dataInputsArr);
                }
                RemoteInput[] textInputsArr = textInputs.isEmpty() ? null : (RemoteInput[]) textInputs.toArray(new RemoteInput[textInputs.size()]);
                return new Action(this.mIcon, this.mTitle, this.mIntent, this.mExtras, textInputsArr, this.mAllowGeneratedReplies, this.mSemanticAction, this.mIsContextual);
            }
        }

        /* JADX INFO: renamed from: clone, reason: merged with bridge method [inline-methods] */
        public Action m9clone() {
            return new Action(getIcon(), this.title, this.actionIntent, this.mExtras == null ? new Bundle() : new Bundle(this.mExtras), getRemoteInputs(), getAllowGeneratedReplies(), getSemanticAction(), isContextual());
        }

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        @Override // android.os.Parcelable
        public void writeToParcel(Parcel parcel, int i) {
            Icon icon = getIcon();
            if (icon != null) {
                parcel.writeInt(1);
                icon.writeToParcel(parcel, 0);
            } else {
                parcel.writeInt(0);
            }
            TextUtils.writeToParcel(this.title, parcel, i);
            if (this.actionIntent != null) {
                parcel.writeInt(1);
                this.actionIntent.writeToParcel(parcel, i);
            } else {
                parcel.writeInt(0);
            }
            parcel.writeBundle(this.mExtras);
            parcel.writeTypedArray(this.mRemoteInputs, i);
            parcel.writeInt(this.mAllowGeneratedReplies ? 1 : 0);
            parcel.writeInt(this.mSemanticAction);
            parcel.writeInt(this.mIsContextual ? 1 : 0);
        }

        public static final class WearableExtender implements Extender {
            private static final int DEFAULT_FLAGS = 1;
            private static final String EXTRA_WEARABLE_EXTENSIONS = "android.wearable.EXTENSIONS";
            private static final int FLAG_AVAILABLE_OFFLINE = 1;
            private static final int FLAG_HINT_DISPLAY_INLINE = 4;
            private static final int FLAG_HINT_LAUNCHES_ACTIVITY = 2;
            private static final String KEY_CANCEL_LABEL = "cancelLabel";
            private static final String KEY_CONFIRM_LABEL = "confirmLabel";
            private static final String KEY_FLAGS = "flags";
            private static final String KEY_IN_PROGRESS_LABEL = "inProgressLabel";
            private CharSequence mCancelLabel;
            private CharSequence mConfirmLabel;
            private int mFlags;
            private CharSequence mInProgressLabel;

            public WearableExtender() {
                this.mFlags = 1;
            }

            public WearableExtender(Action action) {
                this.mFlags = 1;
                Bundle wearableBundle = action.getExtras().getBundle(EXTRA_WEARABLE_EXTENSIONS);
                if (wearableBundle != null) {
                    this.mFlags = wearableBundle.getInt("flags", 1);
                    this.mInProgressLabel = wearableBundle.getCharSequence(KEY_IN_PROGRESS_LABEL);
                    this.mConfirmLabel = wearableBundle.getCharSequence(KEY_CONFIRM_LABEL);
                    this.mCancelLabel = wearableBundle.getCharSequence(KEY_CANCEL_LABEL);
                }
            }

            @Override // android.app.Notification.Action.Extender
            public Builder extend(Builder builder) {
                Bundle wearableBundle = new Bundle();
                int i = this.mFlags;
                if (i != 1) {
                    wearableBundle.putInt("flags", i);
                }
                CharSequence charSequence = this.mInProgressLabel;
                if (charSequence != null) {
                    wearableBundle.putCharSequence(KEY_IN_PROGRESS_LABEL, charSequence);
                }
                CharSequence charSequence2 = this.mConfirmLabel;
                if (charSequence2 != null) {
                    wearableBundle.putCharSequence(KEY_CONFIRM_LABEL, charSequence2);
                }
                CharSequence charSequence3 = this.mCancelLabel;
                if (charSequence3 != null) {
                    wearableBundle.putCharSequence(KEY_CANCEL_LABEL, charSequence3);
                }
                builder.getExtras().putBundle(EXTRA_WEARABLE_EXTENSIONS, wearableBundle);
                return builder;
            }

            /* JADX INFO: renamed from: clone, reason: merged with bridge method [inline-methods] */
            public WearableExtender m10clone() {
                WearableExtender that = new WearableExtender();
                that.mFlags = this.mFlags;
                that.mInProgressLabel = this.mInProgressLabel;
                that.mConfirmLabel = this.mConfirmLabel;
                that.mCancelLabel = this.mCancelLabel;
                return that;
            }

            public WearableExtender setAvailableOffline(boolean availableOffline) {
                setFlag(1, availableOffline);
                return this;
            }

            public boolean isAvailableOffline() {
                return (this.mFlags & 1) != 0;
            }

            private void setFlag(int mask, boolean value) {
                if (value) {
                    this.mFlags |= mask;
                } else {
                    this.mFlags &= ~mask;
                }
            }

            @Deprecated
            public WearableExtender setInProgressLabel(CharSequence label) {
                this.mInProgressLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getInProgressLabel() {
                return this.mInProgressLabel;
            }

            @Deprecated
            public WearableExtender setConfirmLabel(CharSequence label) {
                this.mConfirmLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getConfirmLabel() {
                return this.mConfirmLabel;
            }

            @Deprecated
            public WearableExtender setCancelLabel(CharSequence label) {
                this.mCancelLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getCancelLabel() {
                return this.mCancelLabel;
            }

            public WearableExtender setHintLaunchesActivity(boolean hintLaunchesActivity) {
                setFlag(2, hintLaunchesActivity);
                return this;
            }

            public boolean getHintLaunchesActivity() {
                return (this.mFlags & 2) != 0;
            }

            public WearableExtender setHintDisplayActionInline(boolean hintDisplayInline) {
                setFlag(4, hintDisplayInline);
                return this;
            }

            public boolean getHintDisplayActionInline() {
                return (this.mFlags & 4) != 0;
            }
        }
    }

    public Notification() {
        this.isStandardNotification = 0;
        this.isKeyguardNotification = 0;
        this.internalType = 0;
        this.internalPriority = 0;
        this.internalGroupPriority = 0;
        this.internalFlag = 0;
        this.number = 0;
        this.audioStreamType = -1;
        this.audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
        this.color = 0;
        this.extras = new Bundle();
        this.mGroupAlertBehavior = 0;
        this.mBadgeIcon = 0;
        this.mAllowSystemGeneratedContextualActions = true;
        this.when = System.currentTimeMillis();
        this.creationTime = System.currentTimeMillis();
        this.priority = 0;
    }

    public Notification(Context context, int icon, CharSequence tickerText, long when, CharSequence contentTitle, CharSequence contentText, Intent contentIntent) {
        this.isStandardNotification = 0;
        this.isKeyguardNotification = 0;
        this.internalType = 0;
        this.internalPriority = 0;
        this.internalGroupPriority = 0;
        this.internalFlag = 0;
        this.number = 0;
        this.audioStreamType = -1;
        this.audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
        this.color = 0;
        this.extras = new Bundle();
        this.mGroupAlertBehavior = 0;
        this.mBadgeIcon = 0;
        this.mAllowSystemGeneratedContextualActions = true;
        new Builder(context).setWhen(when).setSmallIcon(icon).setTicker(tickerText).setContentTitle(contentTitle).setContentText(contentText).setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, 0)).buildInto(this);
    }

    @Deprecated
    public Notification(int icon, CharSequence tickerText, long when) {
        this.isStandardNotification = 0;
        this.isKeyguardNotification = 0;
        this.internalType = 0;
        this.internalPriority = 0;
        this.internalGroupPriority = 0;
        this.internalFlag = 0;
        this.number = 0;
        this.audioStreamType = -1;
        this.audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
        this.color = 0;
        this.extras = new Bundle();
        this.mGroupAlertBehavior = 0;
        this.mBadgeIcon = 0;
        this.mAllowSystemGeneratedContextualActions = true;
        this.icon = icon;
        this.tickerText = tickerText;
        this.when = when;
        this.creationTime = System.currentTimeMillis();
    }

    public Notification(Parcel parcel) {
        this.isStandardNotification = 0;
        this.isKeyguardNotification = 0;
        this.internalType = 0;
        this.internalPriority = 0;
        this.internalGroupPriority = 0;
        this.internalFlag = 0;
        this.number = 0;
        this.audioStreamType = -1;
        this.audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
        this.color = 0;
        this.extras = new Bundle();
        this.mGroupAlertBehavior = 0;
        this.mBadgeIcon = 0;
        this.mAllowSystemGeneratedContextualActions = true;
        readFromParcelImpl(parcel);
        this.allPendingIntents = parcel.readArraySet(null);
    }

    private void readFromParcelImpl(Parcel parcel) {
        parcel.readInt();
        IBinder strongBinder = parcel.readStrongBinder();
        this.mWhitelistToken = strongBinder;
        if (strongBinder == null) {
            this.mWhitelistToken = processWhitelistToken;
        }
        parcel.setClassCookie(PendingIntent.class, this.mWhitelistToken);
        this.when = parcel.readLong();
        this.creationTime = parcel.readLong();
        if (parcel.readInt() != 0) {
            Icon iconCreateFromParcel = Icon.CREATOR.createFromParcel(parcel);
            this.mSmallIcon = iconCreateFromParcel;
            if (iconCreateFromParcel.getType() == 2) {
                this.icon = this.mSmallIcon.getResId();
            }
        }
        this.isStandardNotification = parcel.readInt();
        this.isKeyguardNotification = parcel.readInt();
        this.internalType = parcel.readInt();
        this.internalPriority = parcel.readInt();
        this.internalGroupPriority = parcel.readInt();
        this.internalFlag = parcel.readInt();
        this.number = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.contentIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.deleteIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.tickerText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.tickerView = RemoteViews.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.contentView = RemoteViews.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.mLargeIcon = Icon.CREATOR.createFromParcel(parcel);
        }
        this.defaults = parcel.readInt();
        this.flags = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.sound = Uri.CREATOR.createFromParcel(parcel);
        }
        this.audioStreamType = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.audioAttributes = AudioAttributes.CREATOR.createFromParcel(parcel);
        }
        this.vibrate = parcel.createLongArray();
        this.ledARGB = parcel.readInt();
        this.ledOnMS = parcel.readInt();
        this.ledOffMS = parcel.readInt();
        this.iconLevel = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.fullScreenIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        }
        this.priority = parcel.readInt();
        this.category = parcel.readString8();
        this.mGroupKey = parcel.readString8();
        this.mSortKey = parcel.readString8();
        this.extras = Bundle.setDefusable(parcel.readBundle(), true);
        fixDuplicateExtras();
        this.actions = (Action[]) parcel.createTypedArray(Action.CREATOR);
        if (parcel.readInt() != 0) {
            this.bigContentView = RemoteViews.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.headsUpContentView = RemoteViews.CREATOR.createFromParcel(parcel);
        }
        this.visibility = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.publicVersion = CREATOR.createFromParcel(parcel);
        }
        this.color = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.mChannelId = parcel.readString8();
        }
        this.mTimeout = parcel.readLong();
        if (parcel.readInt() != 0) {
            this.mShortcutId = parcel.readString8();
        }
        if (parcel.readInt() != 0) {
            this.mLocusId = LocusId.CREATOR.createFromParcel(parcel);
        }
        this.mBadgeIcon = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.mSettingsText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel);
        }
        this.mGroupAlertBehavior = parcel.readInt();
        if (parcel.readInt() != 0) {
            this.mBubbleMetadata = BubbleMetadata.CREATOR.createFromParcel(parcel);
        }
        this.mAllowSystemGeneratedContextualActions = parcel.readBoolean();
    }

    /* JADX INFO: renamed from: clone, reason: merged with bridge method [inline-methods] */
    public Notification m8clone() {
        Notification that = new Notification();
        cloneInto(that, true);
        return that;
    }

    public void cloneInto(Notification that, boolean heavy) {
        RemoteViews remoteViews;
        RemoteViews remoteViews2;
        Icon icon;
        RemoteViews remoteViews3;
        RemoteViews remoteViews4;
        that.mWhitelistToken = this.mWhitelistToken;
        that.when = this.when;
        that.creationTime = this.creationTime;
        that.mSmallIcon = this.mSmallIcon;
        that.isStandardNotification = this.isStandardNotification;
        that.isKeyguardNotification = this.isKeyguardNotification;
        that.internalType = this.internalType;
        that.internalPriority = this.internalPriority;
        that.internalGroupPriority = this.internalGroupPriority;
        that.internalFlag = this.internalFlag;
        that.number = this.number;
        that.contentIntent = this.contentIntent;
        that.deleteIntent = this.deleteIntent;
        that.fullScreenIntent = this.fullScreenIntent;
        CharSequence charSequence = this.tickerText;
        if (charSequence != null) {
            that.tickerText = charSequence.toString();
        }
        if (heavy && (remoteViews4 = this.tickerView) != null) {
            that.tickerView = remoteViews4.mo11clone();
        }
        if (heavy && (remoteViews3 = this.contentView) != null) {
            that.contentView = remoteViews3.mo11clone();
        }
        if (heavy && (icon = this.mLargeIcon) != null) {
            that.mLargeIcon = icon;
        }
        that.iconLevel = this.iconLevel;
        that.sound = this.sound;
        that.audioStreamType = this.audioStreamType;
        AudioAttributes audioAttributes = this.audioAttributes;
        if (audioAttributes != null) {
            that.audioAttributes = new AudioAttributes.Builder(audioAttributes).build();
        }
        long[] vibrate = this.vibrate;
        if (vibrate != null) {
            int N = vibrate.length;
            long[] vib = new long[N];
            that.vibrate = vib;
            System.arraycopy(vibrate, 0, vib, 0, N);
        }
        that.ledARGB = this.ledARGB;
        that.ledOnMS = this.ledOnMS;
        that.ledOffMS = this.ledOffMS;
        that.defaults = this.defaults;
        that.flags = this.flags;
        that.priority = this.priority;
        that.category = this.category;
        that.mGroupKey = this.mGroupKey;
        that.mSortKey = this.mSortKey;
        if (this.extras != null) {
            try {
                Bundle bundle = new Bundle(this.extras);
                that.extras = bundle;
                bundle.size();
            } catch (BadParcelableException e) {
                Log.e(TAG, "could not unparcel extras from notification: " + this, e);
                that.extras = null;
            }
        }
        if (!ArrayUtils.isEmpty(this.allPendingIntents)) {
            that.allPendingIntents = new ArraySet<>((ArraySet) this.allPendingIntents);
        }
        Action[] actionArr = this.actions;
        if (actionArr != null) {
            that.actions = new Action[actionArr.length];
            int i = 0;
            while (true) {
                Action[] actionArr2 = this.actions;
                if (i >= actionArr2.length) {
                    break;
                }
                if (actionArr2[i] != null) {
                    that.actions[i] = actionArr2[i].m9clone();
                }
                i++;
            }
        }
        if (heavy && (remoteViews2 = this.bigContentView) != null) {
            that.bigContentView = remoteViews2.mo11clone();
        }
        if (heavy && (remoteViews = this.headsUpContentView) != null) {
            that.headsUpContentView = remoteViews.mo11clone();
        }
        that.visibility = this.visibility;
        if (this.publicVersion != null) {
            Notification notification = new Notification();
            that.publicVersion = notification;
            this.publicVersion.cloneInto(notification, heavy);
        }
        that.color = this.color;
        that.mChannelId = this.mChannelId;
        that.mTimeout = this.mTimeout;
        that.mShortcutId = this.mShortcutId;
        that.mLocusId = this.mLocusId;
        that.mBadgeIcon = this.mBadgeIcon;
        that.mSettingsText = this.mSettingsText;
        that.mGroupAlertBehavior = this.mGroupAlertBehavior;
        that.mBubbleMetadata = this.mBubbleMetadata;
        that.mAllowSystemGeneratedContextualActions = this.mAllowSystemGeneratedContextualActions;
        if (!heavy) {
            that.lightenPayload();
        }
    }

    public void visitUris(Consumer<Uri> visitor) {
        Bundle bundle;
        visitor.accept(this.sound);
        RemoteViews remoteViews = this.tickerView;
        if (remoteViews != null) {
            remoteViews.visitUris(visitor);
        }
        RemoteViews remoteViews2 = this.contentView;
        if (remoteViews2 != null) {
            remoteViews2.visitUris(visitor);
        }
        RemoteViews remoteViews3 = this.bigContentView;
        if (remoteViews3 != null) {
            remoteViews3.visitUris(visitor);
        }
        RemoteViews remoteViews4 = this.headsUpContentView;
        if (remoteViews4 != null) {
            remoteViews4.visitUris(visitor);
        }
        Bundle bundle2 = this.extras;
        if (bundle2 != null) {
            visitor.accept((Uri) bundle2.getParcelable(EXTRA_AUDIO_CONTENTS_URI));
            if (this.extras.containsKey(EXTRA_BACKGROUND_IMAGE_URI)) {
                visitor.accept(Uri.parse(this.extras.getString(EXTRA_BACKGROUND_IMAGE_URI)));
            }
            ArrayList<Person> people = this.extras.getParcelableArrayList(EXTRA_PEOPLE_LIST);
            if (people != null && !people.isEmpty()) {
                for (Person p : people) {
                    if (p.getIconUri() != null) {
                        visitor.accept(p.getIconUri());
                    }
                }
            }
            Person person = (Person) this.extras.getParcelable(EXTRA_MESSAGING_PERSON);
            if (person != null && person.getIconUri() != null) {
                visitor.accept(person.getIconUri());
            }
        }
        if (MessagingStyle.class.equals(getNotificationStyle()) && (bundle = this.extras) != null) {
            Parcelable[] messages = bundle.getParcelableArray(EXTRA_MESSAGES);
            if (!ArrayUtils.isEmpty(messages)) {
                for (MessagingStyle.Message message : MessagingStyle.Message.getMessagesFromBundleArray(messages)) {
                    visitor.accept(message.getDataUri());
                    Person senderPerson = message.getSenderPerson();
                    if (senderPerson != null && senderPerson.getIconUri() != null) {
                        visitor.accept(senderPerson.getIconUri());
                    }
                }
            }
            Parcelable[] historic = this.extras.getParcelableArray(EXTRA_HISTORIC_MESSAGES);
            if (!ArrayUtils.isEmpty(historic)) {
                for (MessagingStyle.Message message2 : MessagingStyle.Message.getMessagesFromBundleArray(historic)) {
                    visitor.accept(message2.getDataUri());
                    Person senderPerson2 = message2.getSenderPerson();
                    if (senderPerson2 != null && senderPerson2.getIconUri() != null) {
                        visitor.accept(senderPerson2.getIconUri());
                    }
                }
            }
        }
        BubbleMetadata bubbleMetadata = this.mBubbleMetadata;
        if (bubbleMetadata != null && bubbleMetadata.getIcon() != null) {
            Icon icon = this.mBubbleMetadata.getIcon();
            int iconType = icon.getType();
            if (iconType == 6 || iconType == 4) {
                visitor.accept(icon.getUri());
            }
        }
    }

    public final void lightenPayload() {
        Object obj;
        this.tickerView = null;
        this.contentView = null;
        this.bigContentView = null;
        this.headsUpContentView = null;
        this.mLargeIcon = null;
        Bundle bundle = this.extras;
        if (bundle != null && !bundle.isEmpty()) {
            Set<String> keyset = this.extras.keySet();
            int N = keyset.size();
            String[] keys = (String[]) keyset.toArray(new String[N]);
            for (int i = 0; i < N; i++) {
                String key = keys[i];
                if (!"android.tv.EXTENSIONS".equals(key) && (obj = this.extras.get(key)) != null && ((obj instanceof Parcelable) || (obj instanceof Parcelable[]) || (obj instanceof SparseArray) || (obj instanceof ArrayList))) {
                    this.extras.remove(key);
                }
            }
        }
    }

    public static CharSequence safeCharSequence(CharSequence cs) {
        if (cs == null) {
            return cs;
        }
        if (cs.length() > 1024) {
            cs = cs.subSequence(0, 1024);
        }
        if (cs instanceof Parcelable) {
            Log.e(TAG, "warning: " + cs.getClass().getCanonicalName() + " instance is a custom Parcelable and not allowed in Notification");
            return cs.toString();
        }
        return removeTextSizeSpans(cs);
    }

    private static CharSequence removeTextSizeSpans(CharSequence charSequence) {
        Object resultSpan;
        if (charSequence instanceof Spanned) {
            Spanned ss = (Spanned) charSequence;
            Object[] spans = ss.getSpans(0, ss.length(), Object.class);
            SpannableStringBuilder builder = new SpannableStringBuilder(ss.toString());
            for (Object span : spans) {
                Object resultSpan2 = span;
                if (resultSpan2 instanceof CharacterStyle) {
                    resultSpan2 = ((CharacterStyle) span).getUnderlying();
                }
                if (resultSpan2 instanceof TextAppearanceSpan) {
                    TextAppearanceSpan originalSpan = (TextAppearanceSpan) resultSpan2;
                    resultSpan = new TextAppearanceSpan(originalSpan.getFamily(), originalSpan.getTextStyle(), -1, originalSpan.getTextColor(), originalSpan.getLinkTextColor());
                } else if (!(resultSpan2 instanceof RelativeSizeSpan) && !(resultSpan2 instanceof AbsoluteSizeSpan)) {
                    resultSpan = span;
                }
                builder.setSpan(resultSpan, ss.getSpanStart(span), ss.getSpanEnd(span), ss.getSpanFlags(span));
            }
            return builder;
        }
        return charSequence;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(final Parcel parcel, int flags) {
        boolean collectPendingIntents = this.allPendingIntents == null;
        if (collectPendingIntents) {
            PendingIntent.setOnMarshaledListener(new PendingIntent.OnMarshaledListener() { // from class: android.app.-$$Lambda$Notification$hOCsSZH8tWalFSbIzQ9x9IcPa9M
                @Override // android.app.PendingIntent.OnMarshaledListener
                public final void onMarshaled(PendingIntent pendingIntent, Parcel parcel2, int i) {
                    this.f$0.lambda$writeToParcel$0$Notification(parcel, pendingIntent, parcel2, i);
                }
            });
        }
        try {
            writeToParcelImpl(parcel, flags);
            synchronized (this) {
                parcel.writeArraySet(this.allPendingIntents);
            }
        } finally {
            if (collectPendingIntents) {
                PendingIntent.setOnMarshaledListener(null);
            }
        }
    }

    public /* synthetic */ void lambda$writeToParcel$0$Notification(Parcel parcel, PendingIntent intent, Parcel out, int outFlags) {
        if (parcel == out) {
            synchronized (this) {
                if (this.allPendingIntents == null) {
                    this.allPendingIntents = new ArraySet<>();
                }
                this.allPendingIntents.add(intent);
            }
        }
    }

    private void writeToParcelImpl(Parcel parcel, int flags) {
        Bitmap bitmap;
        int i;
        parcel.writeInt(1);
        parcel.writeStrongBinder(this.mWhitelistToken);
        parcel.writeLong(this.when);
        parcel.writeLong(this.creationTime);
        if (this.mSmallIcon == null && (i = this.icon) != 0) {
            this.mSmallIcon = Icon.createWithResource("", i);
        }
        if (this.mSmallIcon != null) {
            parcel.writeInt(1);
            this.mSmallIcon.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.isStandardNotification);
        parcel.writeInt(this.isKeyguardNotification);
        parcel.writeInt(this.internalType);
        parcel.writeInt(this.internalPriority);
        parcel.writeInt(this.internalGroupPriority);
        parcel.writeInt(this.internalFlag);
        parcel.writeInt(this.number);
        if (this.contentIntent != null) {
            parcel.writeInt(1);
            this.contentIntent.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        if (this.deleteIntent != null) {
            parcel.writeInt(1);
            this.deleteIntent.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        if (this.tickerText != null) {
            parcel.writeInt(1);
            TextUtils.writeToParcel(this.tickerText, parcel, flags);
        } else {
            parcel.writeInt(0);
        }
        if (this.tickerView != null) {
            parcel.writeInt(1);
            this.tickerView.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        if (this.contentView != null) {
            parcel.writeInt(1);
            this.contentView.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        if (this.mLargeIcon == null && (bitmap = this.largeIcon) != null) {
            this.mLargeIcon = Icon.createWithBitmap(bitmap);
        }
        if (this.mLargeIcon != null) {
            parcel.writeInt(1);
            this.mLargeIcon.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.defaults);
        parcel.writeInt(this.flags);
        if (this.sound != null) {
            parcel.writeInt(1);
            this.sound.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.audioStreamType);
        if (this.audioAttributes != null) {
            parcel.writeInt(1);
            this.audioAttributes.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeLongArray(this.vibrate);
        parcel.writeInt(this.ledARGB);
        parcel.writeInt(this.ledOnMS);
        parcel.writeInt(this.ledOffMS);
        parcel.writeInt(this.iconLevel);
        if (this.fullScreenIntent != null) {
            parcel.writeInt(1);
            this.fullScreenIntent.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.priority);
        parcel.writeString8(this.category);
        parcel.writeString8(this.mGroupKey);
        parcel.writeString8(this.mSortKey);
        parcel.writeBundle(this.extras);
        parcel.writeTypedArray(this.actions, 0);
        if (this.bigContentView != null) {
            parcel.writeInt(1);
            this.bigContentView.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        if (this.headsUpContentView != null) {
            parcel.writeInt(1);
            this.headsUpContentView.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.visibility);
        if (this.publicVersion != null) {
            parcel.writeInt(1);
            this.publicVersion.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.color);
        if (this.mChannelId != null) {
            parcel.writeInt(1);
            parcel.writeString8(this.mChannelId);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeLong(this.mTimeout);
        if (this.mShortcutId != null) {
            parcel.writeInt(1);
            parcel.writeString8(this.mShortcutId);
        } else {
            parcel.writeInt(0);
        }
        if (this.mLocusId != null) {
            parcel.writeInt(1);
            this.mLocusId.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.mBadgeIcon);
        if (this.mSettingsText != null) {
            parcel.writeInt(1);
            TextUtils.writeToParcel(this.mSettingsText, parcel, flags);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(this.mGroupAlertBehavior);
        if (this.mBubbleMetadata != null) {
            parcel.writeInt(1);
            this.mBubbleMetadata.writeToParcel(parcel, 0);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeBoolean(this.mAllowSystemGeneratedContextualActions);
    }

    public static boolean areActionsVisiblyDifferent(Notification first, Notification second) {
        Action[] firstAs = first.actions;
        Action[] secondAs = second.actions;
        if ((firstAs == null && secondAs != null) || (firstAs != null && secondAs == null)) {
            return true;
        }
        if (firstAs != null && secondAs != null) {
            if (firstAs.length != secondAs.length) {
                return true;
            }
            for (int i = 0; i < firstAs.length; i++) {
                if (!Objects.equals(String.valueOf(firstAs[i].title), String.valueOf(secondAs[i].title))) {
                    return true;
                }
                RemoteInput[] firstRs = firstAs[i].getRemoteInputs();
                RemoteInput[] secondRs = secondAs[i].getRemoteInputs();
                if (firstRs == null) {
                    firstRs = new RemoteInput[0];
                }
                if (secondRs == null) {
                    secondRs = new RemoteInput[0];
                }
                if (firstRs.length != secondRs.length) {
                    return true;
                }
                for (int j = 0; j < firstRs.length; j++) {
                    if (!Objects.equals(String.valueOf(firstRs[j].getLabel()), String.valueOf(secondRs[j].getLabel()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean areStyledNotificationsVisiblyDifferent(Builder first, Builder second) {
        if (first.getStyle() == null) {
            return second.getStyle() != null;
        }
        if (second.getStyle() == null) {
            return true;
        }
        return first.getStyle().areNotificationsVisiblyDifferent(second.getStyle());
    }

    public static boolean areRemoteViewsChanged(Builder first, Builder second) {
        return !Objects.equals(Boolean.valueOf(first.usesStandardHeader()), Boolean.valueOf(second.usesStandardHeader())) || areRemoteViewsChanged(first.mN.contentView, second.mN.contentView) || areRemoteViewsChanged(first.mN.bigContentView, second.mN.bigContentView) || areRemoteViewsChanged(first.mN.headsUpContentView, second.mN.headsUpContentView);
    }

    private static boolean areRemoteViewsChanged(RemoteViews first, RemoteViews second) {
        if (first == null && second == null) {
            return false;
        }
        if ((first != null || second == null) && ((first == null || second != null) && Objects.equals(Integer.valueOf(first.getLayoutId()), Integer.valueOf(second.getLayoutId())) && Objects.equals(Integer.valueOf(first.getSequenceNumber()), Integer.valueOf(second.getSequenceNumber())))) {
            return false;
        }
        return true;
    }

    private void fixDuplicateExtras() {
        if (this.extras != null) {
            fixDuplicateExtra(this.mLargeIcon, EXTRA_LARGE_ICON);
        }
    }

    private void fixDuplicateExtra(Parcelable original, String extraName) {
        if (original != null && this.extras.getParcelable(extraName) != null) {
            this.extras.putParcelable(extraName, original);
        }
    }

    @Deprecated
    public void setLatestEventInfo(Context context, CharSequence contentTitle, CharSequence contentText, PendingIntent contentIntent) {
        if (context.getApplicationInfo().targetSdkVersion > 22) {
            Log.e(TAG, "setLatestEventInfo() is deprecated and you should feel deprecated.", new Throwable());
        }
        if (context.getApplicationInfo().targetSdkVersion < 24) {
            this.extras.putBoolean(EXTRA_SHOW_WHEN, true);
        }
        Builder builder = new Builder(context, this);
        if (contentTitle != null) {
            builder.setContentTitle(contentTitle);
        }
        if (contentText != null) {
            builder.setContentText(contentText);
        }
        builder.setContentIntent(contentIntent);
        builder.build();
        this.isStandardNotification = 1;
        this.isKeyguardNotification = 0;
        this.internalType = 0;
        this.internalPriority = 0;
        this.internalGroupPriority = 0;
        this.internalFlag = 0;
    }

    public void setAllowlistToken(IBinder token) {
        this.mWhitelistToken = token;
    }

    public static void addFieldsFromContext(Context context, Notification notification) {
        addFieldsFromContext(context.getApplicationInfo(), notification);
    }

    public static void addFieldsFromContext(ApplicationInfo ai, Notification notification) {
        notification.extras.putParcelable(EXTRA_BUILDER_APPLICATION_INFO, ai);
    }

    public void dumpDebug(ProtoOutputStream proto, long fieldId) {
        long token = proto.start(fieldId);
        proto.write(1138166333441L, getChannelId());
        proto.write(1133871366146L, this.tickerText != null);
        proto.write(1120986464259L, this.flags);
        proto.write(1120986464260L, this.color);
        proto.write(1138166333445L, this.category);
        proto.write(1138166333446L, this.mGroupKey);
        proto.write(1138166333447L, this.mSortKey);
        Action[] actionArr = this.actions;
        if (actionArr != null) {
            proto.write(1120986464264L, actionArr.length);
        }
        int i = this.visibility;
        if (i >= -1 && i <= 1) {
            proto.write(1159641169929L, i);
        }
        Notification notification = this.publicVersion;
        if (notification != null) {
            notification.dumpDebug(proto, 1146756268042L);
        }
        proto.end(token);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Notification(channel=");
        sb.append(getChannelId());
        sb.append(" shortcut=");
        sb.append(getShortcutId());
        sb.append(" contentView=");
        RemoteViews remoteViews = this.contentView;
        if (remoteViews != null) {
            sb.append(remoteViews.getPackage());
            sb.append("/0x");
            sb.append(Integer.toHexString(this.contentView.getLayoutId()));
        } else {
            sb.append("null");
        }
        sb.append(" vibrate=");
        if ((this.defaults & 2) != 0) {
            sb.append("default");
        } else {
            long[] jArr = this.vibrate;
            if (jArr != null) {
                int N = jArr.length - 1;
                sb.append("[");
                for (int i = 0; i < N; i++) {
                    sb.append(this.vibrate[i]);
                    sb.append(',');
                }
                if (N != -1) {
                    sb.append(this.vibrate[N]);
                }
                sb.append("]");
            } else {
                sb.append("null");
            }
        }
        sb.append(" sound=");
        if ((this.defaults & 1) != 0) {
            sb.append("default");
        } else {
            Uri uri = this.sound;
            if (uri != null) {
                sb.append(uri.toString());
            } else {
                sb.append("null");
            }
        }
        if (this.tickerText != null) {
            sb.append(" tick");
        }
        sb.append(" defaults=0x");
        sb.append(Integer.toHexString(this.defaults));
        sb.append(" flags=0x");
        sb.append(Integer.toHexString(this.flags));
        sb.append(String.format(" color=0x%08x", Integer.valueOf(this.color)));
        if (this.category != null) {
            sb.append(" category=");
            sb.append(this.category);
        }
        if (this.mGroupKey != null) {
            sb.append(" groupKey=");
            sb.append(this.mGroupKey);
        }
        if (this.mSortKey != null) {
            sb.append(" sortKey=");
            sb.append(this.mSortKey);
        }
        if (this.actions != null) {
            sb.append(" actions=");
            sb.append(this.actions.length);
        }
        sb.append(" vis=");
        sb.append(visibilityToString(this.visibility));
        if (this.publicVersion != null) {
            sb.append(" publicVersion=");
            sb.append(this.publicVersion.toString());
        }
        if (this.mLocusId != null) {
            sb.append(" locusId=");
            sb.append(this.mLocusId);
        }
        sb.append(" internalType=");
        sb.append(this.internalType);
        sb.append(" internalPriority=");
        sb.append(this.internalPriority);
        sb.append(" internalGroupPriority=");
        sb.append(this.internalGroupPriority);
        sb.append(" internalFlag=");
        sb.append(this.internalFlag);
        sb.append(")");
        return sb.toString();
    }

    public static String visibilityToString(int vis) {
        if (vis == -1) {
            return "SECRET";
        }
        if (vis == 0) {
            return "PRIVATE";
        }
        if (vis == 1) {
            return "PUBLIC";
        }
        return "UNKNOWN(" + String.valueOf(vis) + ")";
    }

    public static String priorityToString(int pri) {
        if (pri == -2) {
            return "MIN";
        }
        if (pri == -1) {
            return "LOW";
        }
        if (pri == 0) {
            return "DEFAULT";
        }
        if (pri == 1) {
            return "HIGH";
        }
        if (pri == 2) {
            return "MAX";
        }
        return "UNKNOWN(" + String.valueOf(pri) + ")";
    }

    public boolean hasCompletedProgress() {
        return this.extras.containsKey(EXTRA_PROGRESS) && this.extras.containsKey(EXTRA_PROGRESS_MAX) && this.extras.getInt(EXTRA_PROGRESS_MAX) != 0 && this.extras.getInt(EXTRA_PROGRESS) == this.extras.getInt(EXTRA_PROGRESS_MAX);
    }

    @Deprecated
    public String getChannel() {
        return this.mChannelId;
    }

    public String getChannelId() {
        return this.mChannelId;
    }

    @Deprecated
    public long getTimeout() {
        return this.mTimeout;
    }

    public long getTimeoutAfter() {
        return this.mTimeout;
    }

    public int getBadgeIconType() {
        return this.mBadgeIcon;
    }

    public String getShortcutId() {
        return this.mShortcutId;
    }

    public LocusId getLocusId() {
        return this.mLocusId;
    }

    public CharSequence getSettingsText() {
        return this.mSettingsText;
    }

    public int getGroupAlertBehavior() {
        return this.mGroupAlertBehavior;
    }

    public BubbleMetadata getBubbleMetadata() {
        return this.mBubbleMetadata;
    }

    public void setBubbleMetadata(BubbleMetadata data) {
        this.mBubbleMetadata = data;
    }

    public boolean getAllowSystemGeneratedContextualActions() {
        return this.mAllowSystemGeneratedContextualActions;
    }

    public Icon getSmallIcon() {
        return this.mSmallIcon;
    }

    public void setSmallIcon(Icon icon) {
        this.mSmallIcon = icon;
    }

    public Icon getLargeIcon() {
        return this.mLargeIcon;
    }

    public boolean isGroupSummary() {
        return (this.mGroupKey == null || (this.flags & 512) == 0) ? false : true;
    }

    public boolean isGroupChild() {
        return this.mGroupKey != null && (this.flags & 512) == 0;
    }

    public boolean suppressAlertingDueToGrouping() {
        if (isGroupSummary() && getGroupAlertBehavior() == 2) {
            return true;
        }
        return isGroupChild() && getGroupAlertBehavior() == 1;
    }

    public Pair<RemoteInput, Action> findRemoteInputActionPair(boolean requiresFreeform) {
        Action[] actionArr = this.actions;
        if (actionArr == null) {
            return null;
        }
        for (Action action : actionArr) {
            if (action.getRemoteInputs() != null) {
                RemoteInput resultRemoteInput = null;
                for (RemoteInput remoteInput : action.getRemoteInputs()) {
                    if (remoteInput.getAllowFreeFormInput() || !requiresFreeform) {
                        resultRemoteInput = remoteInput;
                    }
                }
                if (resultRemoteInput != null) {
                    return Pair.create(resultRemoteInput, action);
                }
            }
        }
        return null;
    }

    public List<Action> getContextualActions() {
        if (this.actions == null) {
            return Collections.emptyList();
        }
        List<Action> contextualActions = new ArrayList<>();
        for (Action action : this.actions) {
            if (action.isContextual()) {
                contextualActions.add(action);
            }
        }
        return contextualActions;
    }

    public static class Builder {
        public static final String EXTRA_REBUILD_BIG_CONTENT_VIEW_ACTION_COUNT = "android.rebuild.bigViewActionCount";
        public static final String EXTRA_REBUILD_CONTENT_VIEW_ACTION_COUNT = "android.rebuild.contentViewActionCount";
        public static final String EXTRA_REBUILD_HEADS_UP_CONTENT_VIEW_ACTION_COUNT = "android.rebuild.hudViewActionCount";
        private static final int LIGHTNESS_TEXT_DIFFERENCE_DARK = -10;
        private static final int LIGHTNESS_TEXT_DIFFERENCE_LIGHT = 20;
        private static final boolean USE_ONLY_TITLE_IN_LOW_PRIORITY_SUMMARY = SystemProperties.getBoolean("notifications.only_title", true);
        private ArrayList<Action> mActions;
        private int mBackgroundColor;
        private int mCachedContrastColor;
        private int mCachedContrastColorIsFor;
        private ContrastColorUtil mColorUtil;
        private Context mContext;
        private int mForegroundColor;
        private boolean mInNightMode;
        private boolean mIsLegacy;
        private boolean mIsLegacyInitialized;
        private Notification mN;
        private int mNeutralColor;
        private ArrayList<Action> mOriginalActions;
        StandardTemplateParams mParams;
        private ArrayList<Person> mPersonList;
        private int mPrimaryTextColor;
        private boolean mRebuildStyledRemoteViews;
        private int mSecondaryTextColor;
        private Style mStyle;
        private int mTextColorsAreForBackground;
        private boolean mTintActionButtons;
        private Bundle mUserExtras;
        private int tempIsKeyguardNotification;
        private int tempIsStandardNotification;

        public Builder(Context context, String channelId) {
            this(context, (Notification) null);
            this.mN.mChannelId = channelId;
        }

        @Deprecated
        public Builder(Context context) {
            this(context, (Notification) null);
        }

        public Builder(Context context, Notification toAdopt) {
            boolean z;
            ArrayList<Person> people;
            boolean z2;
            this.mUserExtras = new Bundle();
            this.mActions = new ArrayList<>(3);
            this.mPersonList = new ArrayList<>();
            this.tempIsStandardNotification = 0;
            this.tempIsKeyguardNotification = 0;
            this.mCachedContrastColor = 1;
            this.mCachedContrastColorIsFor = 1;
            this.mNeutralColor = 1;
            this.mParams = new StandardTemplateParams();
            this.mTextColorsAreForBackground = 1;
            this.mPrimaryTextColor = 1;
            this.mSecondaryTextColor = 1;
            this.mBackgroundColor = 1;
            this.mForegroundColor = 1;
            this.mContext = context;
            Resources res = context.getResources();
            if (Notification.IS_TIER_VOS || !res.getBoolean(R.bool.config_tintNotificationActionButtons)) {
                z = false;
            } else {
                z = true;
            }
            this.mTintActionButtons = z;
            if (res.getBoolean(R.bool.config_enableNightMode)) {
                Configuration currentConfig = res.getConfiguration();
                if ((currentConfig.uiMode & 48) != 32) {
                    z2 = false;
                } else {
                    z2 = true;
                }
                this.mInNightMode = z2;
            }
            if (toAdopt == null) {
                this.mN = new Notification();
                if (context.getApplicationInfo().targetSdkVersion < 24) {
                    this.mN.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);
                }
                this.mN.priority = 0;
                this.mN.visibility = 0;
                return;
            }
            this.mN = toAdopt;
            if (toAdopt.actions != null) {
                Collections.addAll(this.mActions, this.mN.actions);
            }
            if (this.mN.extras.containsKey(Notification.EXTRA_PEOPLE_LIST) && (people = this.mN.extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST)) != null && !people.isEmpty()) {
                this.mPersonList.addAll(people);
            }
            if (this.mN.getSmallIcon() == null && this.mN.icon != 0) {
                setSmallIcon(this.mN.icon);
            }
            if (this.mN.getLargeIcon() == null && this.mN.largeIcon != null) {
                setLargeIcon(this.mN.largeIcon);
            }
            String templateClass = this.mN.extras.getString(Notification.EXTRA_TEMPLATE);
            if (!TextUtils.isEmpty(templateClass)) {
                Class<? extends Style> styleClass = Notification.getNotificationStyleClass(templateClass);
                if (styleClass == null) {
                    Log.d(Notification.TAG, "Unknown style class: " + templateClass);
                    return;
                }
                try {
                    Constructor<? extends Style> ctor = styleClass.getDeclaredConstructor(new Class[0]);
                    ctor.setAccessible(true);
                    Style style = ctor.newInstance(new Object[0]);
                    style.restoreFromExtras(this.mN.extras);
                    if (style != null) {
                        setStyle(style);
                    }
                } catch (Throwable t) {
                    Log.e(Notification.TAG, "Could not create Style", t);
                }
            }
        }

        private ContrastColorUtil getColorUtil() {
            if (this.mColorUtil == null) {
                this.mColorUtil = ContrastColorUtil.getInstance(this.mContext);
            }
            return this.mColorUtil;
        }

        public Builder setShortcutId(String shortcutId) {
            this.mN.mShortcutId = shortcutId;
            return this;
        }

        public Builder setLocusId(LocusId locusId) {
            this.mN.mLocusId = locusId;
            return this;
        }

        public Builder setBadgeIconType(int icon) {
            this.mN.mBadgeIcon = icon;
            return this;
        }

        public Builder setGroupAlertBehavior(int groupAlertBehavior) {
            this.mN.mGroupAlertBehavior = groupAlertBehavior;
            return this;
        }

        public Builder setBubbleMetadata(BubbleMetadata data) {
            this.mN.mBubbleMetadata = data;
            return this;
        }

        @Deprecated
        public Builder setChannel(String channelId) {
            this.mN.mChannelId = channelId;
            return this;
        }

        public Builder setChannelId(String channelId) {
            this.mN.mChannelId = channelId;
            return this;
        }

        @Deprecated
        public Builder setTimeout(long durationMs) {
            this.mN.mTimeout = durationMs;
            return this;
        }

        public Builder setTimeoutAfter(long durationMs) {
            this.mN.mTimeout = durationMs;
            return this;
        }

        public Builder setWhen(long when) {
            this.mN.when = when;
            return this;
        }

        public Builder setShowWhen(boolean show) {
            this.mN.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, show);
            return this;
        }

        public Builder setUsesChronometer(boolean b) {
            this.mN.extras.putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, b);
            return this;
        }

        public Builder setChronometerCountDown(boolean countDown) {
            this.mN.extras.putBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, countDown);
            return this;
        }

        public Builder setSmallIcon(int icon) {
            Icon iconCreateWithResource;
            if (icon != 0) {
                iconCreateWithResource = Icon.createWithResource(this.mContext, icon);
            } else {
                iconCreateWithResource = null;
            }
            return setSmallIcon(iconCreateWithResource);
        }

        public Builder setSmallIcon(int icon, int level) {
            this.mN.iconLevel = level;
            return setSmallIcon(icon);
        }

        public Builder setSmallIcon(Icon icon) {
            this.mN.setSmallIcon(icon);
            if (icon != null && icon.getType() == 2) {
                this.mN.icon = icon.getResId();
            }
            return this;
        }

        public Builder setContentTitle(CharSequence title) {
            this.mN.extras.putCharSequence(Notification.EXTRA_TITLE, Notification.safeCharSequence(title));
            return this;
        }

        public Builder setContentText(CharSequence text) {
            this.mN.extras.putCharSequence(Notification.EXTRA_TEXT, Notification.safeCharSequence(text));
            return this;
        }

        public Builder setSubText(CharSequence text) {
            this.mN.extras.putCharSequence(Notification.EXTRA_SUB_TEXT, Notification.safeCharSequence(text));
            return this;
        }

        public Builder setSettingsText(CharSequence text) {
            this.mN.mSettingsText = Notification.safeCharSequence(text);
            return this;
        }

        public Builder setRemoteInputHistory(CharSequence[] text) {
            if (text == null) {
                this.mN.extras.putCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY, null);
            } else {
                int itemCount = Math.min(5, text.length);
                CharSequence[] safe = new CharSequence[itemCount];
                RemoteInputHistoryItem[] items = new RemoteInputHistoryItem[itemCount];
                for (int i = 0; i < itemCount; i++) {
                    safe[i] = Notification.safeCharSequence(text[i]);
                    items[i] = new RemoteInputHistoryItem(text[i]);
                }
                this.mN.extras.putCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY, safe);
                this.mN.extras.putParcelableArray(Notification.EXTRA_REMOTE_INPUT_HISTORY_ITEMS, items);
            }
            return this;
        }

        public Builder setRemoteInputHistory(RemoteInputHistoryItem[] items) {
            if (items == null) {
                this.mN.extras.putParcelableArray(Notification.EXTRA_REMOTE_INPUT_HISTORY_ITEMS, null);
            } else {
                int itemCount = Math.min(5, items.length);
                RemoteInputHistoryItem[] history = new RemoteInputHistoryItem[itemCount];
                for (int i = 0; i < itemCount; i++) {
                    history[i] = items[i];
                }
                this.mN.extras.putParcelableArray(Notification.EXTRA_REMOTE_INPUT_HISTORY_ITEMS, history);
            }
            return this;
        }

        public Builder setShowRemoteInputSpinner(boolean showSpinner) {
            this.mN.extras.putBoolean(Notification.EXTRA_SHOW_REMOTE_INPUT_SPINNER, showSpinner);
            return this;
        }

        public Builder setHideSmartReplies(boolean hideSmartReplies) {
            this.mN.extras.putBoolean(Notification.EXTRA_HIDE_SMART_REPLIES, hideSmartReplies);
            return this;
        }

        public Builder setNumber(int number) {
            this.mN.number = number;
            return this;
        }

        @Deprecated
        public Builder setContentInfo(CharSequence info) {
            this.mN.extras.putCharSequence(Notification.EXTRA_INFO_TEXT, Notification.safeCharSequence(info));
            return this;
        }

        public Builder setProgress(int max, int progress, boolean indeterminate) {
            this.mN.extras.putInt(Notification.EXTRA_PROGRESS, progress);
            this.mN.extras.putInt(Notification.EXTRA_PROGRESS_MAX, max);
            this.mN.extras.putBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, indeterminate);
            return this;
        }

        @Deprecated
        public Builder setContent(RemoteViews views) {
            return setCustomContentView(views);
        }

        public Builder setCustomContentView(RemoteViews contentView) {
            this.mN.contentView = contentView;
            return this;
        }

        public Builder setCustomBigContentView(RemoteViews contentView) {
            this.mN.bigContentView = contentView;
            return this;
        }

        public Builder setCustomHeadsUpContentView(RemoteViews contentView) {
            this.mN.headsUpContentView = contentView;
            return this;
        }

        public Builder setContentIntent(PendingIntent intent) {
            this.mN.contentIntent = intent;
            return this;
        }

        public Builder setDeleteIntent(PendingIntent intent) {
            this.mN.deleteIntent = intent;
            return this;
        }

        public Builder setFullScreenIntent(PendingIntent intent, boolean highPriority) {
            this.mN.fullScreenIntent = intent;
            setFlag(128, highPriority);
            return this;
        }

        public Builder setTicker(CharSequence tickerText) {
            this.mN.tickerText = Notification.safeCharSequence(tickerText);
            return this;
        }

        @Deprecated
        public Builder setTicker(CharSequence tickerText, RemoteViews views) {
            setTicker(tickerText);
            return this;
        }

        public Builder setLargeIcon(Bitmap b) {
            return setLargeIcon(b != null ? Icon.createWithBitmap(b) : null);
        }

        public Builder setLargeIcon(Icon icon) {
            this.mN.mLargeIcon = icon;
            this.mN.extras.putParcelable(Notification.EXTRA_LARGE_ICON, icon);
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound) {
            this.mN.sound = sound;
            this.mN.audioAttributes = Notification.AUDIO_ATTRIBUTES_DEFAULT;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, int streamType) {
            PlayerBase.deprecateStreamTypeForPlayback(streamType, Notification.TAG, "setSound()");
            this.mN.sound = sound;
            this.mN.audioStreamType = streamType;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, AudioAttributes audioAttributes) {
            this.mN.sound = sound;
            this.mN.audioAttributes = audioAttributes;
            return this;
        }

        @Deprecated
        public Builder setVibrate(long[] pattern) {
            this.mN.vibrate = pattern;
            return this;
        }

        @Deprecated
        public Builder setLights(int argb, int onMs, int offMs) {
            this.mN.ledARGB = argb;
            this.mN.ledOnMS = onMs;
            this.mN.ledOffMS = offMs;
            if (onMs != 0 || offMs != 0) {
                this.mN.flags |= 1;
            }
            return this;
        }

        public Builder setOngoing(boolean ongoing) {
            setFlag(2, ongoing);
            return this;
        }

        public Builder setColorized(boolean colorize) {
            this.mN.extras.putBoolean(Notification.EXTRA_COLORIZED, colorize);
            return this;
        }

        public Builder setOnlyAlertOnce(boolean onlyAlertOnce) {
            setFlag(8, onlyAlertOnce);
            return this;
        }

        public Builder setAutoCancel(boolean autoCancel) {
            setFlag(16, autoCancel);
            return this;
        }

        public Builder setLocalOnly(boolean localOnly) {
            setFlag(256, localOnly);
            return this;
        }

        @Deprecated
        public Builder setDefaults(int defaults) {
            this.mN.defaults = defaults;
            return this;
        }

        @Deprecated
        public Builder setPriority(int pri) {
            this.mN.priority = pri;
            return this;
        }

        public Builder setCategory(String category) {
            this.mN.category = category;
            return this;
        }

        public Builder addPerson(String uri) {
            addPerson(new Person.Builder().setUri(uri).build());
            return this;
        }

        public Builder addPerson(Person person) {
            this.mPersonList.add(person);
            return this;
        }

        public Builder setGroup(String groupKey) {
            this.mN.mGroupKey = groupKey;
            return this;
        }

        public Builder setGroupSummary(boolean isGroupSummary) {
            setFlag(512, isGroupSummary);
            return this;
        }

        public Builder setSortKey(String sortKey) {
            this.mN.mSortKey = sortKey;
            return this;
        }

        public Builder addExtras(Bundle extras) {
            if (extras != null) {
                this.mUserExtras.putAll(extras);
            }
            return this;
        }

        public Builder setExtras(Bundle extras) {
            if (extras != null) {
                this.mUserExtras = extras;
            }
            return this;
        }

        public Bundle getExtras() {
            return this.mUserExtras;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public Bundle getAllExtras() {
            Bundle saveExtras = (Bundle) this.mUserExtras.clone();
            saveExtras.putAll(this.mN.extras);
            return saveExtras;
        }

        @Deprecated
        public Builder addAction(int icon, CharSequence title, PendingIntent intent) {
            this.mActions.add(new Action(icon, Notification.safeCharSequence(title), intent));
            return this;
        }

        public Builder addAction(Action action) {
            if (action != null) {
                this.mActions.add(action);
            }
            return this;
        }

        public Builder setActions(Action... actions) {
            this.mActions.clear();
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] != null) {
                    this.mActions.add(actions[i]);
                }
            }
            return this;
        }

        public Builder setStyle(Style style) {
            if (this.mStyle != style) {
                this.mStyle = style;
                if (style == null) {
                    this.mN.extras.remove(Notification.EXTRA_TEMPLATE);
                } else {
                    style.setBuilder(this);
                    this.mN.extras.putString(Notification.EXTRA_TEMPLATE, style.getClass().getName());
                }
            }
            return this;
        }

        public Style getStyle() {
            return this.mStyle;
        }

        public Builder setVisibility(int visibility) {
            this.mN.visibility = visibility;
            return this;
        }

        public Builder setPublicVersion(Notification n) {
            if (n != null) {
                this.mN.publicVersion = new Notification();
                n.cloneInto(this.mN.publicVersion, true);
            } else {
                this.mN.publicVersion = null;
            }
            return this;
        }

        public Builder extend(Extender extender) {
            extender.extend(this);
            return this;
        }

        public Builder setFlag(int mask, boolean value) {
            if (value) {
                this.mN.flags |= mask;
            } else {
                this.mN.flags &= ~mask;
            }
            return this;
        }

        public Builder setColor(int argb) {
            this.mN.color = argb;
            sanitizeColor();
            return this;
        }

        private Drawable getProfileBadgeDrawable() {
            if (this.mContext.getUserId() == 0) {
                return null;
            }
            if (Notification.sVivoNotification == null || !Notification.sVivoNotification.checkUseridForDoubleInstance(this.mContext.getUserId())) {
                return this.mContext.getPackageManager().getUserBadgeForDensityNoBackground(new UserHandle(this.mContext.getUserId()), 0);
            }
            return null;
        }

        private Bitmap getProfileBadge() {
            int badgeSize;
            Drawable badge = getProfileBadgeDrawable();
            if (badge == null) {
                return null;
            }
            if (!Notification.IS_TIER_VOS) {
                badgeSize = 51118656;
            } else {
                badgeSize = R.dimen.notification_badge_size;
            }
            int size = this.mContext.getResources().getDimensionPixelSize(badgeSize);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            badge.setBounds(0, 0, size, size);
            badge.draw(canvas);
            return bitmap;
        }

        private void bindProfileBadge(RemoteViews contentView, StandardTemplateParams p) {
            Bitmap profileBadge = getProfileBadge();
            if (profileBadge != null) {
                contentView.setImageViewBitmap(R.id.profile_badge, profileBadge);
                contentView.setViewVisibility(R.id.profile_badge, 0);
                if (isColorized(p)) {
                    contentView.setDrawableTint(R.id.profile_badge, false, getPrimaryTextColor(p), PorterDuff.Mode.SRC_ATOP);
                }
            }
        }

        private void bindAlertedIcon(RemoteViews contentView, StandardTemplateParams p) {
            contentView.setDrawableTint(R.id.alerted_icon, false, getNeutralColor(p), PorterDuff.Mode.SRC_ATOP);
        }

        public boolean usesStandardHeader() {
            if (this.mN.mUsesStandardHeader) {
                return true;
            }
            if (this.mContext.getApplicationInfo().targetSdkVersion >= 24 && this.mN.contentView == null && this.mN.bigContentView == null) {
                return true;
            }
            boolean contentViewUsesHeader = this.mN.contentView == null || Notification.STANDARD_LAYOUTS.contains(Integer.valueOf(this.mN.contentView.getLayoutId()));
            boolean bigContentViewUsesHeader = this.mN.bigContentView == null || Notification.STANDARD_LAYOUTS.contains(Integer.valueOf(this.mN.bigContentView.getLayoutId()));
            return contentViewUsesHeader && bigContentViewUsesHeader;
        }

        private void resetStandardTemplate(RemoteViews contentView) {
            resetNotificationHeader(contentView);
            contentView.setViewVisibility(R.id.right_icon, 8);
            contentView.setViewVisibility(16908310, 8);
            contentView.setTextViewText(16908310, null);
            contentView.setViewVisibility(R.id.text, 8);
            contentView.setTextViewText(R.id.text, null);
            contentView.setViewVisibility(R.id.text_line_1, 8);
            contentView.setTextViewText(R.id.text_line_1, null);
        }

        private void resetNotificationHeader(RemoteViews contentView) {
            contentView.setBoolean(R.id.notification_header, "setExpanded", false);
            contentView.setTextViewText(R.id.app_name_text, null);
            contentView.setViewVisibility(R.id.chronometer, 8);
            contentView.setViewVisibility(R.id.header_text, 8);
            contentView.setTextViewText(R.id.header_text, null);
            contentView.setViewVisibility(R.id.header_text_secondary, 8);
            contentView.setTextViewText(R.id.header_text_secondary, null);
            contentView.setViewVisibility(R.id.header_text_divider, 8);
            contentView.setViewVisibility(R.id.header_text_secondary_divider, 8);
            contentView.setViewVisibility(R.id.time_divider, 8);
            contentView.setViewVisibility(R.id.time, 8);
            contentView.setImageViewIcon(R.id.profile_badge, null);
            contentView.setViewVisibility(R.id.profile_badge, 8);
            contentView.setViewVisibility(R.id.alerted_icon, 8);
            this.mN.mUsesStandardHeader = false;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public RemoteViews applyStandardTemplate(int resId, TemplateBindResult result) {
            return applyStandardTemplate(resId, this.mParams.reset().fillTextsFrom(this), result);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public RemoteViews applyStandardTemplate(int resId, StandardTemplateParams p, TemplateBindResult result) {
            int i;
            RemoteViews contentView = new BuilderRemoteViews(this.mContext.getApplicationInfo(), resId);
            resetStandardTemplate(contentView);
            Bundle ex = this.mN.extras;
            updateBackgroundColor(contentView, p);
            bindNotificationHeader(contentView, p);
            bindLargeIconAndReply(contentView, p, result);
            boolean showProgress = handleProgressBar(contentView, ex, p);
            if (p.title != null) {
                contentView.setViewVisibility(16908310, 0);
                contentView.setTextViewText(16908310, processTextSpans(p.title));
                setTextViewColorPrimary(contentView, 16908310, p);
                if (Notification.IS_TIER_VOS) {
                    if (showProgress) {
                        i = -2;
                    } else {
                        i = -1;
                    }
                    contentView.setViewLayoutWidth(16908310, i);
                }
            }
            if (p.text != null) {
                int textId = showProgress ? R.id.text_line_1 : R.id.text;
                contentView.setTextViewText(textId, !Notification.IS_TIER_VOS ? p.text : processTextSpans(p.text));
                setTextViewColorSecondary(contentView, textId, p);
                contentView.setViewVisibility(textId, 0);
                if (showProgress && !Notification.IS_TIER_VOS) {
                    contentView.setViewVisibility(51183796, 0);
                }
            }
            setContentMinHeight(contentView, showProgress || this.mN.hasLargeIcon());
            this.tempIsStandardNotification = 1;
            this.tempIsKeyguardNotification = 0;
            return contentView;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public CharSequence processTextSpans(CharSequence text) {
            if (hasForegroundColor() || this.mInNightMode) {
                return ContrastColorUtil.clearColorSpans(text);
            }
            return text;
        }

        private void setTextViewColorPrimary(RemoteViews contentView, int id, StandardTemplateParams p) {
            ensureColors(p);
            contentView.setTextColor(id, this.mPrimaryTextColor);
        }

        private boolean hasForegroundColor() {
            return this.mForegroundColor != 1;
        }

        public int getPrimaryTextColor() {
            return getPrimaryTextColor(this.mParams);
        }

        public int getPrimaryTextColor(StandardTemplateParams p) {
            ensureColors(p);
            return this.mPrimaryTextColor;
        }

        public int getSecondaryTextColor() {
            return getSecondaryTextColor(this.mParams);
        }

        public int getSecondaryTextColor(StandardTemplateParams p) {
            ensureColors(p);
            return this.mSecondaryTextColor;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setTextViewColorSecondary(RemoteViews contentView, int id, StandardTemplateParams p) {
            ensureColors(p);
            contentView.setTextColor(id, this.mSecondaryTextColor);
        }

        private void ensureColors(StandardTemplateParams p) {
            int backgroundColor = getBackgroundColor(p);
            if (this.mPrimaryTextColor == 1 || this.mSecondaryTextColor == 1 || this.mTextColorsAreForBackground != backgroundColor) {
                this.mTextColorsAreForBackground = backgroundColor;
                if (!hasForegroundColor() || !isColorized(p)) {
                    this.mPrimaryTextColor = ContrastColorUtil.resolvePrimaryColor(this.mContext, backgroundColor, this.mInNightMode, true);
                    this.mSecondaryTextColor = ContrastColorUtil.resolveSecondaryColor(this.mContext, backgroundColor, this.mInNightMode, true);
                    if (backgroundColor != 0 && isColorized(p)) {
                        this.mPrimaryTextColor = ContrastColorUtil.findAlphaToMeetContrast(this.mPrimaryTextColor, backgroundColor, 4.5d);
                        this.mSecondaryTextColor = ContrastColorUtil.findAlphaToMeetContrast(this.mSecondaryTextColor, backgroundColor, 4.5d);
                        return;
                    }
                    return;
                }
                double backLum = ContrastColorUtil.calculateLuminance(backgroundColor);
                double textLum = ContrastColorUtil.calculateLuminance(this.mForegroundColor);
                double contrast = ContrastColorUtil.calculateContrast(this.mForegroundColor, backgroundColor);
                boolean backgroundLight = (backLum > textLum && ContrastColorUtil.satisfiesTextContrast(backgroundColor, -16777216)) || (backLum <= textLum && !ContrastColorUtil.satisfiesTextContrast(backgroundColor, -1));
                if (contrast < 4.5d) {
                    if (backgroundLight) {
                        int iFindContrastColor = ContrastColorUtil.findContrastColor(this.mForegroundColor, backgroundColor, true, 4.5d);
                        this.mSecondaryTextColor = iFindContrastColor;
                        this.mPrimaryTextColor = ContrastColorUtil.changeColorLightness(iFindContrastColor, -20);
                        return;
                    } else {
                        int iFindContrastColorAgainstDark = ContrastColorUtil.findContrastColorAgainstDark(this.mForegroundColor, backgroundColor, true, 4.5d);
                        this.mSecondaryTextColor = iFindContrastColorAgainstDark;
                        this.mPrimaryTextColor = ContrastColorUtil.changeColorLightness(iFindContrastColorAgainstDark, 10);
                        return;
                    }
                }
                int i = this.mForegroundColor;
                this.mPrimaryTextColor = i;
                int iChangeColorLightness = ContrastColorUtil.changeColorLightness(i, backgroundLight ? 20 : -10);
                this.mSecondaryTextColor = iChangeColorLightness;
                if (ContrastColorUtil.calculateContrast(iChangeColorLightness, backgroundColor) < 4.5d) {
                    if (backgroundLight) {
                        this.mSecondaryTextColor = ContrastColorUtil.findContrastColor(this.mSecondaryTextColor, backgroundColor, true, 4.5d);
                    } else {
                        this.mSecondaryTextColor = ContrastColorUtil.findContrastColorAgainstDark(this.mSecondaryTextColor, backgroundColor, true, 4.5d);
                    }
                    this.mPrimaryTextColor = ContrastColorUtil.changeColorLightness(this.mSecondaryTextColor, backgroundLight ? -20 : 10);
                }
            }
        }

        private void updateBackgroundColor(RemoteViews contentView, StandardTemplateParams p) {
            if (isColorized(p)) {
                contentView.setInt(R.id.status_bar_latest_event_content, "setBackgroundColor", getBackgroundColor(p));
            } else {
                contentView.setInt(R.id.status_bar_latest_event_content, "setBackgroundResource", 0);
            }
        }

        void setContentMinHeight(RemoteViews remoteView, boolean hasMinHeight) {
            int minContentHeight;
            int minHeight = 0;
            if (hasMinHeight) {
                if (!Notification.IS_TIER_VOS) {
                    minContentHeight = 51118679;
                } else {
                    minContentHeight = R.dimen.notification_min_content_height;
                }
                minHeight = this.mContext.getResources().getDimensionPixelSize(minContentHeight);
            }
            remoteView.setInt(R.id.notification_main_column, "setMinimumHeight", minHeight);
        }

        private boolean handleProgressBar(RemoteViews contentView, Bundle ex, StandardTemplateParams p) {
            int max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            int progress = ex.getInt(Notification.EXTRA_PROGRESS, 0);
            boolean ind = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE);
            if (p.hasProgress && (max != 0 || ind)) {
                contentView.setViewVisibility(16908301, 0);
                contentView.setProgressBar(16908301, max, progress, ind);
                contentView.setProgressBackgroundTintList(16908301, ColorStateList.valueOf(this.mContext.getColor(R.color.notification_progress_background_color)));
                if (getRawColor(p) != 0) {
                    int color = isColorized(p) ? getPrimaryTextColor(p) : resolveContrastColor(p);
                    ColorStateList colorStateList = ColorStateList.valueOf(color);
                    contentView.setProgressTintList(16908301, colorStateList);
                    contentView.setProgressIndeterminateTintList(16908301, colorStateList);
                    return true;
                }
                return true;
            }
            contentView.setViewVisibility(16908301, 8);
            return false;
        }

        private void bindLargeIconAndReply(RemoteViews contentView, StandardTemplateParams p, TemplateBindResult result) {
            boolean largeIconShown = bindLargeIcon(contentView, p);
            boolean replyIconShown = bindReplyIcon(contentView, p);
            boolean iconContainerVisible = largeIconShown || replyIconShown;
            contentView.setViewVisibility(R.id.right_icon_container, iconContainerVisible ? 0 : 8);
            int marginEnd = calculateMarginEnd(largeIconShown, replyIconShown);
            contentView.setViewLayoutMarginEnd(R.id.line1, marginEnd);
            contentView.setViewLayoutMarginEnd(R.id.text, marginEnd);
            contentView.setViewLayoutMarginEnd(16908301, marginEnd);
            if (!Notification.IS_TIER_VOS) {
                contentView.setViewLayoutMarginEnd(51183796, marginEnd);
            }
            if (result != null) {
                result.setIconMarginEnd(marginEnd);
                result.setRightIconContainerVisible(iconContainerVisible);
            }
        }

        private int calculateMarginEnd(boolean largeIconShown, boolean replyIconShown) {
            int marginEnd = 0;
            int contentMargin = this.mContext.getResources().getDimensionPixelSize(R.dimen.notification_content_margin_end);
            int iconSize = this.mContext.getResources().getDimensionPixelSize(R.dimen.notification_right_icon_size);
            if (replyIconShown) {
                int marginEnd2 = 0 + iconSize;
                int replyInset = this.mContext.getResources().getDimensionPixelSize(R.dimen.notification_reply_inset);
                marginEnd = marginEnd2 - (replyInset * 2);
            }
            if (largeIconShown) {
                marginEnd += iconSize;
                if (replyIconShown) {
                    marginEnd += contentMargin;
                }
            }
            if (replyIconShown || largeIconShown) {
                return marginEnd + contentMargin;
            }
            return marginEnd;
        }

        private boolean bindLargeIcon(RemoteViews contentView, StandardTemplateParams p) {
            if (this.mN.mLargeIcon == null && this.mN.largeIcon != null) {
                Notification notification = this.mN;
                notification.mLargeIcon = Icon.createWithBitmap(notification.largeIcon);
            }
            boolean showLargeIcon = (this.mN.mLargeIcon == null || p.hideLargeIcon) ? false : true;
            if (showLargeIcon) {
                contentView.setViewVisibility(R.id.right_icon, 0);
                contentView.setImageViewIcon(R.id.right_icon, this.mN.mLargeIcon);
                processLargeLegacyIcon(this.mN.mLargeIcon, contentView, p);
            }
            return showLargeIcon;
        }

        private boolean bindReplyIcon(RemoteViews contentView, StandardTemplateParams p) {
            boolean actionVisible = !p.hideReplyIcon;
            Action action = null;
            if (actionVisible) {
                action = findReplyAction();
                actionVisible = action != null;
            }
            if (!actionVisible) {
                contentView.setRemoteInputs(R.id.reply_icon_action, null);
            } else {
                contentView.setViewVisibility(R.id.reply_icon_action, 0);
                contentView.setDrawableTint(R.id.reply_icon_action, false, getNeutralColor(p), PorterDuff.Mode.SRC_ATOP);
                contentView.setOnClickPendingIntent(R.id.reply_icon_action, action.actionIntent);
                contentView.setRemoteInputs(R.id.reply_icon_action, action.mRemoteInputs);
            }
            contentView.setViewVisibility(R.id.reply_icon_action, actionVisible ? 0 : 8);
            return actionVisible;
        }

        private Action findReplyAction() {
            ArrayList<Action> actions = this.mActions;
            if (this.mOriginalActions != null) {
                actions = this.mOriginalActions;
            }
            int numActions = actions.size();
            for (int i = 0; i < numActions; i++) {
                Action action = actions.get(i);
                if (hasValidRemoteInput(action)) {
                    return action;
                }
            }
            return null;
        }

        private void bindNotificationHeader(RemoteViews contentView, StandardTemplateParams p) {
            bindSmallIcon(contentView, p);
            bindHeaderAppName(contentView, p);
            bindHeaderText(contentView, p);
            bindHeaderTextSecondary(contentView, p);
            bindHeaderChronometerAndTime(contentView, p);
            bindProfileBadge(contentView, p);
            bindAlertedIcon(contentView, p);
            bindActivePermissions(contentView, p);
            bindExpandButton(contentView, p);
            this.mN.mUsesStandardHeader = true;
        }

        private void bindActivePermissions(RemoteViews contentView, StandardTemplateParams p) {
            int color = getNeutralColor(p);
            contentView.setDrawableTint(R.id.camera, false, color, PorterDuff.Mode.SRC_ATOP);
            contentView.setDrawableTint(R.id.mic, false, color, PorterDuff.Mode.SRC_ATOP);
            contentView.setDrawableTint(R.id.overlay, false, color, PorterDuff.Mode.SRC_ATOP);
        }

        private void bindExpandButton(RemoteViews contentView, StandardTemplateParams p) {
            int color = isColorized(p) ? getPrimaryTextColor(p) : getSecondaryTextColor(p);
            contentView.setDrawableTint(R.id.expand_button, false, color, PorterDuff.Mode.SRC_ATOP);
            contentView.setInt(R.id.expand_button, "setOriginalNotificationColor", color);
        }

        private void bindHeaderChronometerAndTime(RemoteViews contentView, StandardTemplateParams p) {
            if (!showsTimeOrChronometer()) {
                contentView.setLong(R.id.time, "setTime", this.mN.when != 0 ? this.mN.when : this.mN.creationTime);
                return;
            }
            contentView.setViewVisibility(R.id.time_divider, 0);
            contentView.setDrawableTint(R.id.time_divider, false, getSecondaryTextColor(), PorterDuff.Mode.SRC_ATOP);
            if (this.mN.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
                contentView.setViewVisibility(R.id.chronometer, 0);
                contentView.setLong(R.id.chronometer, "setBase", this.mN.when + (SystemClock.elapsedRealtime() - System.currentTimeMillis()));
                contentView.setBoolean(R.id.chronometer, "setStarted", true);
                boolean countsDown = this.mN.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN);
                contentView.setChronometerCountDown(R.id.chronometer, countsDown);
                setTextViewColorSecondary(contentView, R.id.chronometer, p);
                return;
            }
            contentView.setViewVisibility(R.id.time, 0);
            contentView.setLong(R.id.time, "setTime", this.mN.when);
            setTextViewColorSecondary(contentView, R.id.time, p);
        }

        private void bindHeaderText(RemoteViews contentView, StandardTemplateParams p) {
            Style style;
            CharSequence summaryText = p.summaryText;
            if (summaryText == null && (style = this.mStyle) != null && style.mSummaryTextSet && this.mStyle.hasSummaryInHeader()) {
                summaryText = this.mStyle.mSummaryText;
            }
            if (summaryText == null && this.mContext.getApplicationInfo().targetSdkVersion < 24 && this.mN.extras.getCharSequence(Notification.EXTRA_INFO_TEXT) != null) {
                summaryText = this.mN.extras.getCharSequence(Notification.EXTRA_INFO_TEXT);
            }
            if (summaryText != null) {
                contentView.setTextViewText(R.id.header_text, processTextSpans(processLegacyText(summaryText)));
                setTextViewColorSecondary(contentView, R.id.header_text, p);
                contentView.setViewVisibility(R.id.header_text, 0);
                contentView.setViewVisibility(R.id.header_text_divider, 0);
                contentView.setDrawableTint(R.id.header_text_divider, false, getSecondaryTextColor(), PorterDuff.Mode.SRC_ATOP);
            }
        }

        private void bindHeaderTextSecondary(RemoteViews contentView, StandardTemplateParams p) {
            if (!TextUtils.isEmpty(p.headerTextSecondary)) {
                contentView.setTextViewText(R.id.header_text_secondary, processTextSpans(processLegacyText(p.headerTextSecondary)));
                setTextViewColorSecondary(contentView, R.id.header_text_secondary, p);
                contentView.setViewVisibility(R.id.header_text_secondary, 0);
                contentView.setViewVisibility(R.id.header_text_secondary_divider, 0);
                contentView.setDrawableTint(R.id.header_text_secondary_divider, false, getSecondaryTextColor(), PorterDuff.Mode.SRC_ATOP);
            }
        }

        public String loadHeaderAppName() {
            CharSequence name = null;
            PackageManager pm = this.mContext.getPackageManager();
            if (this.mN.extras.containsKey(Notification.EXTRA_SUBSTITUTE_APP_NAME)) {
                String pkg = this.mContext.getPackageName();
                String subName = this.mN.extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME);
                if (pm.checkPermission(Manifest.permission.SUBSTITUTE_NOTIFICATION_APP_NAME, pkg) == 0) {
                    name = subName;
                } else {
                    Log.w(Notification.TAG, "warning: pkg " + pkg + " attempting to substitute app name '" + subName + "' without holding perm " + Manifest.permission.SUBSTITUTE_NOTIFICATION_APP_NAME);
                }
            }
            if (TextUtils.isEmpty(name)) {
                name = pm.getApplicationLabel(this.mContext.getApplicationInfo());
            }
            if (TextUtils.isEmpty(name)) {
                return null;
            }
            return String.valueOf(name);
        }

        private void bindHeaderAppName(RemoteViews contentView, StandardTemplateParams p) {
            contentView.setTextViewText(R.id.app_name_text, loadHeaderAppName());
            if (isColorized(p)) {
                setTextViewColorPrimary(contentView, R.id.app_name_text, p);
            } else {
                contentView.setTextColor(R.id.app_name_text, getSecondaryTextColor(p));
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isColorized(StandardTemplateParams p) {
            return p.allowColorization && this.mN.isColorized();
        }

        private void bindSmallIcon(RemoteViews contentView, StandardTemplateParams p) {
            if (this.mN.mSmallIcon == null && this.mN.icon != 0) {
                Notification notification = this.mN;
                notification.mSmallIcon = Icon.createWithResource(this.mContext, notification.icon);
            }
            contentView.setImageViewIcon(16908294, this.mN.mSmallIcon);
            contentView.setInt(16908294, "setImageLevel", this.mN.iconLevel);
            processSmallIconColor(this.mN.mSmallIcon, contentView, p);
        }

        private boolean showsTimeOrChronometer() {
            return this.mN.showsTime() || this.mN.showsChronometer();
        }

        private void resetStandardTemplateWithActions(RemoteViews big) {
            big.setViewVisibility(R.id.actions, 8);
            big.removeAllViews(R.id.actions);
            big.setViewVisibility(R.id.notification_material_reply_container, 8);
            big.setTextViewText(R.id.notification_material_reply_text_1, null);
            big.setViewVisibility(R.id.notification_material_reply_text_1_container, 8);
            big.setViewVisibility(R.id.notification_material_reply_progress, 8);
            big.setViewVisibility(R.id.notification_material_reply_text_2, 8);
            big.setTextViewText(R.id.notification_material_reply_text_2, null);
            big.setViewVisibility(R.id.notification_material_reply_text_3, 8);
            big.setTextViewText(R.id.notification_material_reply_text_3, null);
            if (!Notification.IS_TIER_VOS) {
                big.setViewLayoutMarginBottomDimen(R.id.notification_action_list_margin_target, 51118658);
            } else {
                big.setViewLayoutMarginBottomDimen(R.id.notification_action_list_margin_target, R.dimen.notification_content_margin);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public RemoteViews applyStandardTemplateWithActions(int layoutId, TemplateBindResult result) {
            return applyStandardTemplateWithActions(layoutId, this.mParams.reset().fillTextsFrom(this), result);
        }

        private static List<Action> filterOutContextualActions(List<Action> actions) {
            List<Action> nonContextualActions = new ArrayList<>();
            for (Action action : actions) {
                if (!action.isContextual()) {
                    nonContextualActions.add(action);
                }
            }
            return nonContextualActions;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public RemoteViews applyStandardTemplateWithActions(int layoutId, StandardTemplateParams p, TemplateBindResult result) {
            RemoteViews big = applyStandardTemplate(layoutId, p, result);
            resetStandardTemplateWithActions(big);
            boolean validRemoteInput = false;
            List<Action> nonContextualActions = filterOutContextualActions(this.mActions);
            int N = nonContextualActions.size();
            if (N > 0) {
                big.setViewVisibility(R.id.actions_container, 0);
                big.setViewVisibility(R.id.actions, 0);
                big.setViewLayoutMarginBottomDimen(R.id.notification_action_list_margin_target, 0);
                if (N > 3) {
                    N = 3;
                }
                for (int i = 0; i < N; i++) {
                    Action action = nonContextualActions.get(i);
                    boolean actionHasValidInput = hasValidRemoteInput(action);
                    validRemoteInput |= actionHasValidInput;
                    RemoteViews button = generateActionButton(action, false, p);
                    if (actionHasValidInput) {
                        button.setInt(R.id.action0, "setBackgroundResource", 0);
                    }
                    big.addView(R.id.actions, button);
                }
            } else {
                big.setViewVisibility(R.id.actions_container, 8);
            }
            RemoteInputHistoryItem[] replyText = (RemoteInputHistoryItem[]) Notification.getParcelableArrayFromBundle(this.mN.extras, Notification.EXTRA_REMOTE_INPUT_HISTORY_ITEMS, RemoteInputHistoryItem.class);
            if (validRemoteInput && replyText != null && replyText.length > 0 && !TextUtils.isEmpty(replyText[0].getText()) && p.maxRemoteInputHistory > 0) {
                boolean showSpinner = this.mN.extras.getBoolean(Notification.EXTRA_SHOW_REMOTE_INPUT_SPINNER);
                big.setViewVisibility(R.id.notification_material_reply_container, 0);
                big.setViewVisibility(R.id.notification_material_reply_text_1_container, 0);
                big.setTextViewText(R.id.notification_material_reply_text_1, processTextSpans(replyText[0].getText()));
                setTextViewColorSecondary(big, R.id.notification_material_reply_text_1, p);
                big.setViewVisibility(R.id.notification_material_reply_progress, showSpinner ? 0 : 8);
                big.setProgressIndeterminateTintList(R.id.notification_material_reply_progress, ColorStateList.valueOf(isColorized(p) ? getPrimaryTextColor(p) : resolveContrastColor(p)));
                if (replyText.length > 1 && !TextUtils.isEmpty(replyText[1].getText()) && p.maxRemoteInputHistory > 1) {
                    big.setViewVisibility(R.id.notification_material_reply_text_2, 0);
                    big.setTextViewText(R.id.notification_material_reply_text_2, processTextSpans(replyText[1].getText()));
                    setTextViewColorSecondary(big, R.id.notification_material_reply_text_2, p);
                    if (replyText.length > 2 && !TextUtils.isEmpty(replyText[2].getText()) && p.maxRemoteInputHistory > 2) {
                        big.setViewVisibility(R.id.notification_material_reply_text_3, 0);
                        big.setTextViewText(R.id.notification_material_reply_text_3, processTextSpans(replyText[2].getText()));
                        setTextViewColorSecondary(big, R.id.notification_material_reply_text_3, p);
                    }
                }
            }
            return big;
        }

        private boolean hasValidRemoteInput(Action action) {
            RemoteInput[] remoteInputs;
            if (TextUtils.isEmpty(action.title) || action.actionIntent == null || (remoteInputs = action.getRemoteInputs()) == null) {
                return false;
            }
            for (RemoteInput r : remoteInputs) {
                CharSequence[] choices = r.getChoices();
                if (r.getAllowFreeFormInput()) {
                    return true;
                }
                if (choices != null && choices.length != 0) {
                    return true;
                }
            }
            return false;
        }

        public RemoteViews createContentView() {
            return createContentView(false);
        }

        public RemoteViews createContentView(boolean increasedHeight) {
            RemoteViews styleView;
            if (this.mN.contentView != null && useExistingRemoteView()) {
                return this.mN.contentView;
            }
            Style style = this.mStyle;
            if (style != null && (styleView = style.makeContentView(increasedHeight)) != null) {
                return styleView;
            }
            return applyStandardTemplate(getBaseLayoutResource(), null);
        }

        private boolean useExistingRemoteView() {
            Style style = this.mStyle;
            return style == null || !(style.displayCustomViewInline() || this.mRebuildStyledRemoteViews);
        }

        public RemoteViews createBigContentView() {
            RemoteViews result = null;
            if (this.mN.bigContentView != null && useExistingRemoteView()) {
                return this.mN.bigContentView;
            }
            Style style = this.mStyle;
            if (style != null) {
                result = style.makeBigContentView();
                hideLine1Text(result);
            } else if (this.mActions.size() != 0) {
                result = applyStandardTemplateWithActions(getBigBaseLayoutResource(), null);
            }
            makeHeaderExpanded(result);
            return result;
        }

        public RemoteViews makeNotificationHeader() {
            return makeNotificationHeader(this.mParams.reset().fillTextsFrom(this));
        }

        private RemoteViews makeNotificationHeader(StandardTemplateParams p) {
            int headerLayout;
            p.disallowColorization();
            if (!Notification.IS_TIER_VOS) {
                headerLayout = 50528330;
            } else {
                headerLayout = R.layout.notification_template_header;
            }
            RemoteViews header = new BuilderRemoteViews(this.mContext.getApplicationInfo(), headerLayout);
            resetNotificationHeader(header);
            bindNotificationHeader(header, p);
            return header;
        }

        public RemoteViews makeAmbientNotification() {
            RemoteViews headsUpContentView = createHeadsUpContentView(false);
            if (headsUpContentView != null) {
                return headsUpContentView;
            }
            return createContentView();
        }

        private void hideLine1Text(RemoteViews result) {
            if (result != null) {
                result.setViewVisibility(R.id.text_line_1, 8);
            }
        }

        public static void makeHeaderExpanded(RemoteViews result) {
            if (result != null) {
                result.setBoolean(R.id.notification_header, "setExpanded", true);
            }
        }

        public RemoteViews createHeadsUpContentView(boolean increasedHeight) {
            if (this.mN.headsUpContentView != null && useExistingRemoteView()) {
                return this.mN.headsUpContentView;
            }
            Style style = this.mStyle;
            if (style != null) {
                RemoteViews styleView = style.makeHeadsUpContentView(increasedHeight);
                if (styleView != null) {
                    return styleView;
                }
            } else if (this.mActions.size() == 0) {
                return null;
            }
            StandardTemplateParams p = this.mParams.reset().fillTextsFrom(this).setMaxRemoteInputHistory(1);
            return applyStandardTemplateWithActions(getBigBaseLayoutResource(), p, null);
        }

        public RemoteViews createHeadsUpContentView() {
            return createHeadsUpContentView(false);
        }

        public RemoteViews makePublicContentView(boolean isLowPriority) {
            RemoteViews publicView;
            if (this.mN.publicVersion != null) {
                Builder builder = recoverBuilder(this.mContext, this.mN.publicVersion);
                return builder.createContentView();
            }
            Bundle savedBundle = this.mN.extras;
            Style style = this.mStyle;
            this.mStyle = null;
            Icon largeIcon = this.mN.mLargeIcon;
            this.mN.mLargeIcon = null;
            Bitmap largeIconLegacy = this.mN.largeIcon;
            this.mN.largeIcon = null;
            ArrayList<Action> actions = this.mActions;
            this.mActions = new ArrayList<>();
            Bundle publicExtras = new Bundle();
            publicExtras.putBoolean(Notification.EXTRA_SHOW_WHEN, savedBundle.getBoolean(Notification.EXTRA_SHOW_WHEN));
            publicExtras.putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, savedBundle.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
            publicExtras.putBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, savedBundle.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN));
            String appName = savedBundle.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME);
            if (appName != null) {
                publicExtras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appName);
            }
            if (!Notification.IS_TIER_VOS) {
                publicExtras.putCharSequence(Notification.EXTRA_TITLE, this.mContext.getString(R.string.notification_hidden_text));
            }
            this.mN.extras = publicExtras;
            if (!Notification.IS_TIER_VOS) {
                publicView = applyStandardTemplate(getBaseLayoutResource(), null);
            } else {
                StandardTemplateParams params = this.mParams.reset().fillTextsFrom(this);
                if (isLowPriority) {
                    params.forceDefaultColor();
                }
                RemoteViews publicView2 = makeNotificationHeader(params);
                publicView2.setBoolean(R.id.notification_header, "setExpandOnlyOnButton", true);
                publicView = publicView2;
            }
            this.mN.extras = savedBundle;
            this.mN.mLargeIcon = largeIcon;
            this.mN.largeIcon = largeIconLegacy;
            this.mActions = actions;
            this.mStyle = style;
            return publicView;
        }

        public RemoteViews makeLowPriorityContentView(boolean useRegularSubtext) {
            StandardTemplateParams p = this.mParams.reset().forceDefaultColor().fillTextsFrom(this);
            if (!useRegularSubtext || TextUtils.isEmpty(this.mParams.summaryText)) {
                p.summaryText(createSummaryText());
            }
            RemoteViews header = makeNotificationHeader(p);
            header.setBoolean(R.id.notification_header, "setAcceptAllTouches", true);
            return header;
        }

        private CharSequence createSummaryText() {
            CharSequence titleText = this.mN.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (USE_ONLY_TITLE_IN_LOW_PRIORITY_SUMMARY) {
                return titleText;
            }
            SpannableStringBuilder summary = new SpannableStringBuilder();
            if (titleText == null) {
                titleText = this.mN.extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
            }
            BidiFormatter bidi = BidiFormatter.getInstance();
            if (titleText != null) {
                summary.append(bidi.unicodeWrap(titleText));
            }
            CharSequence contentText = this.mN.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (titleText != null && contentText != null) {
                summary.append(bidi.unicodeWrap(this.mContext.getText(R.string.notification_header_divider_symbol_with_spaces)));
            }
            if (contentText != null) {
                summary.append(bidi.unicodeWrap(contentText));
            }
            return summary;
        }

        private RemoteViews generateActionButton(Action action, boolean emphazisedMode, StandardTemplateParams p) {
            int actionTombstoneLayoutResource;
            CharSequence title;
            int textColor;
            boolean tombstone = action.actionIntent == null;
            ApplicationInfo applicationInfo = this.mContext.getApplicationInfo();
            if (emphazisedMode) {
                actionTombstoneLayoutResource = getEmphasizedActionLayoutResource();
            } else {
                actionTombstoneLayoutResource = tombstone ? getActionTombstoneLayoutResource() : getActionLayoutResource();
            }
            RemoteViews button = new BuilderRemoteViews(applicationInfo, actionTombstoneLayoutResource);
            if (!tombstone) {
                button.setOnClickPendingIntent(R.id.action0, action.actionIntent);
            }
            button.setContentDescription(R.id.action0, action.title);
            if (action.mRemoteInputs != null) {
                button.setRemoteInputs(R.id.action0, action.mRemoteInputs);
            }
            if (!emphazisedMode) {
                button.setTextViewText(R.id.action0, processTextSpans(processLegacyText(action.title)));
                if (isColorized(p)) {
                    setTextViewColorPrimary(button, R.id.action0, p);
                } else if (getRawColor(p) != 0 && this.mTintActionButtons) {
                    button.setTextColor(R.id.action0, resolveContrastColor(p));
                }
            } else {
                CharSequence title2 = action.title;
                ColorStateList[] outResultColor = null;
                int background = resolveBackgroundColor(p);
                if (isLegacy()) {
                    title = ContrastColorUtil.clearColorSpans(title2);
                } else {
                    outResultColor = new ColorStateList[1];
                    title = ensureColorSpanContrast(title2, background, outResultColor);
                }
                button.setTextViewText(R.id.action0, processTextSpans(title));
                setTextViewColorPrimary(button, R.id.action0, p);
                boolean hasColorOverride = (outResultColor == null || outResultColor[0] == null) ? false : true;
                if (hasColorOverride) {
                    background = outResultColor[0].getDefaultColor();
                    textColor = ContrastColorUtil.resolvePrimaryColor(this.mContext, background, this.mInNightMode, true);
                    button.setTextColor(R.id.action0, textColor);
                } else {
                    int rippleColor = getRawColor(p);
                    if (rippleColor != 0 && !isColorized(p) && this.mTintActionButtons && !this.mInNightMode) {
                        textColor = resolveContrastColor(p);
                        button.setTextColor(R.id.action0, textColor);
                    } else {
                        textColor = getPrimaryTextColor(p);
                    }
                }
                int rippleColor2 = (16777215 & textColor) | 855638016;
                button.setColorStateList(R.id.action0, "setRippleColor", ColorStateList.valueOf(rippleColor2));
                button.setColorStateList(R.id.action0, "setButtonBackground", ColorStateList.valueOf(background));
                button.setBoolean(R.id.action0, "setHasStroke", hasColorOverride ? false : true);
            }
            button.setIntTag(R.id.action0, R.id.notification_action_index_tag, this.mActions.indexOf(action));
            return button;
        }

        private CharSequence ensureColorSpanContrast(CharSequence charSequence, int background, ColorStateList[] outResultColor) {
            Object[] spans;
            int i;
            boolean z;
            if (charSequence instanceof Spanned) {
                Spanned ss = (Spanned) charSequence;
                boolean z2 = false;
                Object[] spans2 = ss.getSpans(0, ss.length(), Object.class);
                SpannableStringBuilder builder = new SpannableStringBuilder(ss.toString());
                int length = spans2.length;
                int i2 = 0;
                while (i2 < length) {
                    Object span = spans2[i2];
                    Object resultSpan = span;
                    int spanStart = ss.getSpanStart(span);
                    int spanEnd = ss.getSpanEnd(span);
                    boolean fullLength = spanEnd - spanStart == charSequence.length() ? true : z2;
                    if (resultSpan instanceof CharacterStyle) {
                        resultSpan = ((CharacterStyle) span).getUnderlying();
                    }
                    if (resultSpan instanceof TextAppearanceSpan) {
                        TextAppearanceSpan originalSpan = (TextAppearanceSpan) resultSpan;
                        ColorStateList textColor = originalSpan.getTextColor();
                        if (textColor == null) {
                            spans = spans2;
                            i = length;
                        } else {
                            int[] colors = textColor.getColors();
                            spans = spans2;
                            int[] newColors = new int[colors.length];
                            i = length;
                            int i3 = 0;
                            while (i3 < newColors.length) {
                                newColors[i3] = ContrastColorUtil.ensureLargeTextContrast(colors[i3], background, this.mInNightMode);
                                i3++;
                                colors = colors;
                            }
                            ColorStateList textColor2 = new ColorStateList((int[][]) textColor.getStates().clone(), newColors);
                            if (fullLength) {
                                outResultColor[0] = textColor2;
                                textColor2 = null;
                            }
                            resultSpan = new TextAppearanceSpan(originalSpan.getFamily(), originalSpan.getTextStyle(), originalSpan.getTextSize(), textColor2, originalSpan.getLinkTextColor());
                        }
                        z = false;
                    } else {
                        spans = spans2;
                        i = length;
                        if (resultSpan instanceof ForegroundColorSpan) {
                            int foregroundColor = ContrastColorUtil.ensureLargeTextContrast(((ForegroundColorSpan) resultSpan).getForegroundColor(), background, this.mInNightMode);
                            if (fullLength) {
                                z = false;
                                outResultColor[0] = ColorStateList.valueOf(foregroundColor);
                                resultSpan = null;
                            } else {
                                z = false;
                                resultSpan = new ForegroundColorSpan(foregroundColor);
                            }
                        } else {
                            z = false;
                            resultSpan = span;
                        }
                    }
                    if (resultSpan != null) {
                        builder.setSpan(resultSpan, spanStart, spanEnd, ss.getSpanFlags(span));
                    }
                    i2++;
                    z2 = z;
                    spans2 = spans;
                    length = i;
                }
                return builder;
            }
            return charSequence;
        }

        private boolean isLegacy() {
            if (!this.mIsLegacyInitialized) {
                this.mIsLegacy = this.mContext.getApplicationInfo().targetSdkVersion < 21;
                this.mIsLegacyInitialized = true;
            }
            return this.mIsLegacy;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public CharSequence processLegacyText(CharSequence charSequence) {
            boolean isAlreadyLightText = isLegacy() || textColorsNeedInversion();
            if (isAlreadyLightText) {
                return getColorUtil().invertCharSequenceColors(charSequence);
            }
            return charSequence;
        }

        private void processSmallIconColor(Icon smallIcon, RemoteViews contentView, StandardTemplateParams p) {
            int color;
            boolean colorable = !isLegacy() || getColorUtil().isGrayscaleIcon(this.mContext, smallIcon);
            if (isColorized(p)) {
                color = getPrimaryTextColor(p);
            } else {
                color = resolveContrastColor(p);
            }
            if (colorable) {
                contentView.setDrawableTint(16908294, false, color, PorterDuff.Mode.SRC_ATOP);
            }
            contentView.setInt(16908294, "setOriginalIconColor", colorable ? color : 1);
        }

        private void processLargeLegacyIcon(Icon largeIcon, RemoteViews contentView, StandardTemplateParams p) {
            if (largeIcon != null && isLegacy() && getColorUtil().isGrayscaleIcon(this.mContext, largeIcon)) {
                contentView.setDrawableTint(16908294, false, resolveContrastColor(p), PorterDuff.Mode.SRC_ATOP);
            }
        }

        private void sanitizeColor() {
            if (this.mN.color != 0) {
                this.mN.color |= -16777216;
            }
        }

        int resolveContrastColor(StandardTemplateParams p) {
            int color;
            int i;
            int rawColor = getRawColor(p);
            if (this.mCachedContrastColorIsFor == rawColor && (i = this.mCachedContrastColor) != 1) {
                return i;
            }
            int background = this.mContext.getColor(R.color.notification_material_background_color);
            if (rawColor == 0) {
                ensureColors(p);
                color = ContrastColorUtil.resolveDefaultColor(this.mContext, background, this.mInNightMode, true);
            } else {
                color = ContrastColorUtil.resolveContrastColor(this.mContext, rawColor, background, this.mInNightMode);
            }
            if (Color.alpha(color) < 255) {
                color = ContrastColorUtil.compositeColors(color, background);
            }
            this.mCachedContrastColorIsFor = rawColor;
            this.mCachedContrastColor = color;
            return color;
        }

        private int getRawColor(StandardTemplateParams p) {
            if (p.forceDefaultColor) {
                return 0;
            }
            return this.mN.color;
        }

        int resolveNeutralColor() {
            int i = this.mNeutralColor;
            if (i != 1) {
                return i;
            }
            int background = this.mContext.getColor(R.color.notification_material_background_color);
            int iResolveDefaultColor = ContrastColorUtil.resolveDefaultColor(this.mContext, background, this.mInNightMode, true);
            this.mNeutralColor = iResolveDefaultColor;
            if (Color.alpha(iResolveDefaultColor) < 255) {
                this.mNeutralColor = ContrastColorUtil.compositeColors(this.mNeutralColor, background);
            }
            return this.mNeutralColor;
        }

        public Notification buildUnstyled() {
            if (this.mActions.size() > 0) {
                this.mN.actions = new Action[this.mActions.size()];
                this.mActions.toArray(this.mN.actions);
            }
            if (!this.mPersonList.isEmpty()) {
                this.mN.extras.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, this.mPersonList);
            }
            if (this.mN.bigContentView != null || this.mN.contentView != null || this.mN.headsUpContentView != null) {
                this.mN.extras.putBoolean(Notification.EXTRA_CONTAINS_CUSTOM_VIEW, true);
            }
            this.mN.isStandardNotification = this.tempIsStandardNotification;
            this.mN.isKeyguardNotification = this.tempIsKeyguardNotification;
            return this.mN;
        }

        public static Builder recoverBuilder(Context context, Notification n) {
            Context builderContext;
            ApplicationInfo applicationInfo = (ApplicationInfo) n.extras.getParcelable(Notification.EXTRA_BUILDER_APPLICATION_INFO);
            if (applicationInfo != null) {
                try {
                    builderContext = context.createApplicationContext(applicationInfo, 4);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(Notification.TAG, "ApplicationInfo " + applicationInfo + " not found");
                    builderContext = context;
                }
            } else {
                builderContext = context;
            }
            return new Builder(builderContext, n);
        }

        public Builder setAllowSystemGeneratedContextualActions(boolean allowed) {
            this.mN.mAllowSystemGeneratedContextualActions = allowed;
            return this;
        }

        @Deprecated
        public Notification getNotification() {
            return build();
        }

        public Notification build() {
            if (this.mN.mShortcutId != null && this.mN.mBubbleMetadata != null && this.mN.mBubbleMetadata.getShortcutId() != null && !this.mN.mShortcutId.equals(this.mN.mBubbleMetadata.getShortcutId())) {
                throw new IllegalArgumentException("Notification and BubbleMetadata shortcut id's don't match, notification: " + this.mN.mShortcutId + " vs bubble: " + this.mN.mBubbleMetadata.getShortcutId());
            }
            if (this.mUserExtras != null) {
                this.mN.extras = getAllExtras();
            }
            this.mN.creationTime = System.currentTimeMillis();
            Notification.addFieldsFromContext(this.mContext, this.mN);
            buildUnstyled();
            Style style = this.mStyle;
            if (style != null) {
                style.reduceImageSizes(this.mContext);
                this.mStyle.purgeResources();
                this.mStyle.validate(this.mContext);
                this.mStyle.buildStyled(this.mN);
            }
            this.mN.reduceImageSizes(this.mContext);
            if (this.mContext.getApplicationInfo().targetSdkVersion < 24 && useExistingRemoteView()) {
                if (this.mN.contentView == null) {
                    this.mN.contentView = createContentView();
                    this.mN.extras.putInt(EXTRA_REBUILD_CONTENT_VIEW_ACTION_COUNT, this.mN.contentView.getSequenceNumber());
                }
                if (this.mN.bigContentView == null) {
                    this.mN.bigContentView = createBigContentView();
                    if (this.mN.bigContentView != null) {
                        this.mN.extras.putInt(EXTRA_REBUILD_BIG_CONTENT_VIEW_ACTION_COUNT, this.mN.bigContentView.getSequenceNumber());
                    }
                }
                if (this.mN.headsUpContentView == null) {
                    this.mN.headsUpContentView = createHeadsUpContentView();
                    if (this.mN.headsUpContentView != null) {
                        this.mN.extras.putInt(EXTRA_REBUILD_HEADS_UP_CONTENT_VIEW_ACTION_COUNT, this.mN.headsUpContentView.getSequenceNumber());
                    }
                }
            }
            if ((this.mN.defaults & 4) != 0) {
                this.mN.flags |= 1;
            }
            this.mN.allPendingIntents = null;
            return this.mN;
        }

        public Notification buildInto(Notification n) {
            build().cloneInto(n, true);
            return n;
        }

        public static Notification maybeCloneStrippedForDelivery(Notification n) {
            String templateClass = n.extras.getString(Notification.EXTRA_TEMPLATE);
            if (!TextUtils.isEmpty(templateClass) && Notification.getNotificationStyleClass(templateClass) == null) {
                return n;
            }
            boolean stripContentView = (n.contentView instanceof BuilderRemoteViews) && n.extras.getInt(EXTRA_REBUILD_CONTENT_VIEW_ACTION_COUNT, -1) == n.contentView.getSequenceNumber();
            boolean stripBigContentView = (n.bigContentView instanceof BuilderRemoteViews) && n.extras.getInt(EXTRA_REBUILD_BIG_CONTENT_VIEW_ACTION_COUNT, -1) == n.bigContentView.getSequenceNumber();
            boolean stripHeadsUpContentView = (n.headsUpContentView instanceof BuilderRemoteViews) && n.extras.getInt(EXTRA_REBUILD_HEADS_UP_CONTENT_VIEW_ACTION_COUNT, -1) == n.headsUpContentView.getSequenceNumber();
            if (!stripContentView && !stripBigContentView && !stripHeadsUpContentView) {
                return n;
            }
            Notification clone = n.m8clone();
            if (stripContentView) {
                clone.contentView = null;
                clone.extras.remove(EXTRA_REBUILD_CONTENT_VIEW_ACTION_COUNT);
            }
            if (stripBigContentView) {
                clone.bigContentView = null;
                clone.extras.remove(EXTRA_REBUILD_BIG_CONTENT_VIEW_ACTION_COUNT);
            }
            if (stripHeadsUpContentView) {
                clone.headsUpContentView = null;
                clone.extras.remove(EXTRA_REBUILD_HEADS_UP_CONTENT_VIEW_ACTION_COUNT);
            }
            return clone;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getBaseLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528334;
            }
            return R.layout.notification_template_material_base;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getBigBaseLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528337;
            }
            return R.layout.notification_template_material_big_base;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getBigPictureLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528343;
            }
            return R.layout.notification_template_material_big_picture;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getBigTextLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528346;
            }
            return R.layout.notification_template_material_big_text;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getInboxLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528350;
            }
            return R.layout.notification_template_material_inbox;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getMessagingLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528356;
            }
            return R.layout.notification_template_material_messaging;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int getConversationLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528347;
            }
            return R.layout.notification_template_material_conversation;
        }

        private int getActionLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528319;
            }
            return R.layout.notification_material_action;
        }

        private int getEmphasizedActionLayoutResource() {
            return R.layout.notification_material_action_emphasized;
        }

        private int getActionTombstoneLayoutResource() {
            if (!Notification.IS_TIER_VOS) {
                return 50528322;
            }
            return R.layout.notification_material_action_tombstone;
        }

        private int getBackgroundColor(StandardTemplateParams p) {
            if (isColorized(p)) {
                int i = this.mBackgroundColor;
                return i != 1 ? i : getRawColor(p);
            }
            return 0;
        }

        private int getNeutralColor(StandardTemplateParams p) {
            if (isColorized(p)) {
                return getSecondaryTextColor(p);
            }
            return resolveNeutralColor();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int resolveBackgroundColor(StandardTemplateParams p) {
            int backgroundColor = getBackgroundColor(p);
            if (backgroundColor == 0) {
                return this.mContext.getColor(R.color.notification_material_background_color);
            }
            return backgroundColor;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean shouldTintActionButtons() {
            return this.mTintActionButtons;
        }

        private boolean textColorsNeedInversion() {
            int targetSdkVersion;
            Style style = this.mStyle;
            return style != null && MediaStyle.class.equals(style.getClass()) && (targetSdkVersion = this.mContext.getApplicationInfo().targetSdkVersion) > 23 && targetSdkVersion < 26;
        }

        public void setColorPalette(int backgroundColor, int foregroundColor) {
            this.mBackgroundColor = backgroundColor;
            this.mForegroundColor = foregroundColor;
            this.mTextColorsAreForBackground = 1;
            ensureColors(this.mParams.reset().fillTextsFrom(this));
        }

        public void setRebuildStyledRemoteViews(boolean rebuild) {
            this.mRebuildStyledRemoteViews = rebuild;
        }

        public CharSequence getHeadsUpStatusBarText(boolean publicMode) {
            Style style = this.mStyle;
            if (style != null && !publicMode) {
                CharSequence text = style.getHeadsUpStatusBarText();
                if (!TextUtils.isEmpty(text)) {
                    return text;
                }
            }
            return loadHeaderAppName();
        }

        public boolean usesTemplate() {
            Style style;
            return (this.mN.contentView == null && this.mN.headsUpContentView == null && this.mN.bigContentView == null) || ((style = this.mStyle) != null && style.displayCustomViewInline());
        }
    }

    void reduceImageSizes(Context context) {
        int i;
        int i2;
        int i3;
        if (this.extras.getBoolean(EXTRA_REDUCED_IMAGES)) {
            return;
        }
        boolean isLowRam = ActivityManager.isLowRamDeviceStatic();
        if (this.mLargeIcon != null || this.largeIcon != null) {
            Resources resources = context.getResources();
            Class<? extends Style> style = getNotificationStyle();
            if (isLowRam) {
                i = R.dimen.notification_right_icon_size_low_ram;
            } else {
                i = R.dimen.notification_right_icon_size;
            }
            int maxWidth = resources.getDimensionPixelSize(i);
            int maxHeight = maxWidth;
            if (MediaStyle.class.equals(style) || DecoratedMediaCustomViewStyle.class.equals(style)) {
                if (isLowRam) {
                    i2 = R.dimen.notification_media_image_max_height_low_ram;
                } else {
                    i2 = R.dimen.notification_media_image_max_height;
                }
                maxHeight = resources.getDimensionPixelSize(i2);
                if (isLowRam) {
                    i3 = R.dimen.notification_media_image_max_width_low_ram;
                } else {
                    i3 = R.dimen.notification_media_image_max_width;
                }
                maxWidth = resources.getDimensionPixelSize(i3);
            }
            Icon icon = this.mLargeIcon;
            if (icon != null) {
                icon.scaleDownIfNecessary(maxWidth, maxHeight);
            }
            Bitmap bitmap = this.largeIcon;
            if (bitmap != null) {
                this.largeIcon = Icon.scaleDownIfNecessary(bitmap, maxWidth, maxHeight);
            }
        }
        reduceImageSizesForRemoteView(this.contentView, context, isLowRam);
        reduceImageSizesForRemoteView(this.headsUpContentView, context, isLowRam);
        reduceImageSizesForRemoteView(this.bigContentView, context, isLowRam);
        this.extras.putBoolean(EXTRA_REDUCED_IMAGES, true);
    }

    private void reduceImageSizesForRemoteView(RemoteViews remoteView, Context context, boolean isLowRam) {
        int i;
        int i2;
        if (remoteView != null) {
            Resources resources = context.getResources();
            if (isLowRam) {
                i = R.dimen.notification_custom_view_max_image_width_low_ram;
            } else {
                i = R.dimen.notification_custom_view_max_image_width;
            }
            int maxWidth = resources.getDimensionPixelSize(i);
            if (isLowRam) {
                i2 = R.dimen.notification_custom_view_max_image_height_low_ram;
            } else {
                i2 = R.dimen.notification_custom_view_max_image_height;
            }
            int maxHeight = resources.getDimensionPixelSize(i2);
            remoteView.reduceImageSizes(maxWidth, maxHeight);
        }
    }

    public boolean isForegroundService() {
        return (this.flags & 64) != 0;
    }

    public boolean hasMediaSession() {
        return this.extras.getParcelable(EXTRA_MEDIA_SESSION) != null;
    }

    public Class<? extends Style> getNotificationStyle() {
        String templateClass = this.extras.getString(EXTRA_TEMPLATE);
        if (!TextUtils.isEmpty(templateClass)) {
            return getNotificationStyleClass(templateClass);
        }
        return null;
    }

    public boolean isColorized() {
        if (isColorizedMedia()) {
            return true;
        }
        return this.extras.getBoolean(EXTRA_COLORIZED) && (hasColorizedPermission() || isForegroundService());
    }

    private boolean hasColorizedPermission() {
        return (this.flags & 2048) != 0;
    }

    public boolean isColorizedMedia() {
        Class<? extends Style> style = getNotificationStyle();
        if (!MediaStyle.class.equals(style)) {
            return DecoratedMediaCustomViewStyle.class.equals(style) && this.extras.getBoolean(EXTRA_COLORIZED) && hasMediaSession();
        }
        Boolean colorized = (Boolean) this.extras.get(EXTRA_COLORIZED);
        return (colorized == null || colorized.booleanValue()) && hasMediaSession();
    }

    public boolean isMediaNotification() {
        Class<? extends Style> style = getNotificationStyle();
        return MediaStyle.class.equals(style) || DecoratedMediaCustomViewStyle.class.equals(style);
    }

    public boolean isBubbleNotification() {
        return (this.flags & 4096) != 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean hasLargeIcon() {
        return (this.mLargeIcon == null && this.largeIcon == null) ? false : true;
    }

    public boolean showsTime() {
        return this.when != 0 && this.extras.getBoolean(EXTRA_SHOW_WHEN);
    }

    public boolean showsChronometer() {
        return this.when != 0 && this.extras.getBoolean(EXTRA_SHOW_CHRONOMETER);
    }

    @SystemApi
    public static Class<? extends Style> getNotificationStyleClass(String templateClass) {
        Class<? extends Style>[] classes = {BigTextStyle.class, BigPictureStyle.class, InboxStyle.class, MediaStyle.class, DecoratedCustomViewStyle.class, DecoratedMediaCustomViewStyle.class, MessagingStyle.class, VivoCustomViewStyle.class};
        for (Class<? extends Style> innerClass : classes) {
            if (templateClass.equals(innerClass.getName())) {
                return innerClass;
            }
        }
        return null;
    }

    public static abstract class Style {
        static final int MAX_REMOTE_INPUT_HISTORY_LINES = 3;
        private CharSequence mBigContentTitle;
        protected Builder mBuilder;
        protected CharSequence mSummaryText = null;
        protected boolean mSummaryTextSet = false;

        public abstract boolean areNotificationsVisiblyDifferent(Style style);

        protected void internalSetBigContentTitle(CharSequence title) {
            this.mBigContentTitle = title;
        }

        protected void internalSetSummaryText(CharSequence cs) {
            this.mSummaryText = cs;
            this.mSummaryTextSet = true;
        }

        public void setBuilder(Builder builder) {
            if (this.mBuilder != builder) {
                this.mBuilder = builder;
                if (builder != null) {
                    builder.setStyle(this);
                }
            }
        }

        protected void checkBuilder() {
            if (this.mBuilder == null) {
                throw new IllegalArgumentException("Style requires a valid Builder object");
            }
        }

        protected RemoteViews getStandardView(int layoutId) {
            StandardTemplateParams p = this.mBuilder.mParams.reset().fillTextsFrom(this.mBuilder);
            return getStandardView(layoutId, p, null);
        }

        protected RemoteViews getStandardView(int layoutId, StandardTemplateParams p, TemplateBindResult result) {
            checkBuilder();
            CharSequence charSequence = this.mBigContentTitle;
            if (charSequence != null) {
                p.title = charSequence;
            }
            RemoteViews contentView = this.mBuilder.applyStandardTemplateWithActions(layoutId, p, result);
            CharSequence charSequence2 = this.mBigContentTitle;
            if (charSequence2 != null && charSequence2.equals("")) {
                contentView.setViewVisibility(R.id.line1, 8);
            } else {
                contentView.setViewVisibility(R.id.line1, 0);
            }
            return contentView;
        }

        public RemoteViews makeContentView(boolean increasedHeight) {
            return null;
        }

        public RemoteViews makeBigContentView() {
            return null;
        }

        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            return null;
        }

        public void addExtras(Bundle extras) {
            if (this.mSummaryTextSet) {
                extras.putCharSequence(Notification.EXTRA_SUMMARY_TEXT, this.mSummaryText);
            }
            CharSequence charSequence = this.mBigContentTitle;
            if (charSequence != null) {
                extras.putCharSequence(Notification.EXTRA_TITLE_BIG, charSequence);
            }
            extras.putString(Notification.EXTRA_TEMPLATE, getClass().getName());
        }

        protected void restoreFromExtras(Bundle extras) {
            if (extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) {
                this.mSummaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);
                this.mSummaryTextSet = true;
            }
            if (extras.containsKey(Notification.EXTRA_TITLE_BIG)) {
                this.mBigContentTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
            }
        }

        public Notification buildStyled(Notification wip) {
            addExtras(wip.extras);
            return wip;
        }

        public void purgeResources() {
        }

        public Notification build() {
            checkBuilder();
            return this.mBuilder.build();
        }

        protected boolean hasProgress() {
            return true;
        }

        public boolean hasSummaryInHeader() {
            return true;
        }

        public boolean displayCustomViewInline() {
            return false;
        }

        public void reduceImageSizes(Context context) {
        }

        public void validate(Context context) {
        }

        public CharSequence getHeadsUpStatusBarText() {
            return null;
        }
    }

    public static class VivoCustomViewStyle extends Style {
        public static final String EXTRA_CUSTOM_BIG_CONTENTVIEW = "android.custom.contentView.big";
        public static final String EXTRA_CUSTOM_CONTENTVIEW = "android.custom.contentView";
        public static final String EXTRA_CUSTOM_DELETEACTIONINTENT = "android.custom.deleteActionIntent";
        public static final String EXTRA_CUSTOM_HEADSUP_CONTENTVIEW = "android.custom.contentView.headsup";
        private RemoteViews customBigContentView;
        private RemoteViews customContentView;
        private RemoteViews customHeadsUpContentView;
        private PendingIntent deleteActionIntent;

        public VivoCustomViewStyle setCustomContentView(RemoteViews contentView) {
            this.customContentView = contentView;
            if (contentView != null) {
                contentView.mIsVivoCustomStyleContent = true;
            }
            return this;
        }

        public VivoCustomViewStyle setCustomBigContentView(RemoteViews contentView) {
            this.customBigContentView = contentView;
            if (contentView != null) {
                contentView.mIsVivoCustomStyleContent = true;
            }
            return this;
        }

        public VivoCustomViewStyle setCustomHeadsUpContentView(RemoteViews contentView) {
            this.customHeadsUpContentView = contentView;
            if (contentView != null) {
                contentView.mIsVivoCustomStyleContent = true;
            }
            return this;
        }

        public VivoCustomViewStyle setHeaderDeletePendingIntent(PendingIntent deleteIntent) {
            this.deleteActionIntent = deleteIntent;
            return this;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            RemoteViews remoteViews = this.customContentView;
            if (remoteViews != null) {
                RemoteViews view = createViews(remoteViews);
                bindDeleteAction(view);
                bindHeader(view);
                return view;
            }
            return super.makeContentView(increasedHeight);
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            RemoteViews remoteViews = this.customBigContentView;
            if (remoteViews != null) {
                RemoteViews view = createViews(remoteViews);
                bindDeleteAction(view);
                bindHeader(view);
                return view;
            }
            return null;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            RemoteViews remoteViews = this.customHeadsUpContentView;
            if (remoteViews != null) {
                RemoteViews view = createViews(remoteViews);
                bindDeleteAction(view);
                bindHeader(view);
                return view;
            }
            return makeContentView(increasedHeight);
        }

        private RemoteViews createViews(RemoteViews viewToAdd) {
            StandardTemplateParams p = this.mBuilder.mParams.reset().hasProgress(false);
            RemoteViews view = this.mBuilder.applyStandardTemplate(getVivoCustomBaseLayout(), p, null);
            RemoteViews clonedView = new RemoteViews(viewToAdd);
            view.addView(51183837, clonedView, -1);
            return view;
        }

        private void bindDeleteAction(RemoteViews parent) {
            if (this.deleteActionIntent != null) {
                parent.setViewVisibility(51183753, 0);
                parent.setOnClickPendingIntent(51183753, this.deleteActionIntent);
            }
        }

        private void bindHeader(RemoteViews parent) {
            Bitmap bitmap;
            if (this.mBuilder.mN.extras.containsKey("vivo.summaryIconRes")) {
                int id = this.mBuilder.mN.extras.getInt("vivo.summaryIconRes");
                Icon icon = Icon.createWithResource(this.mBuilder.mContext, id);
                if (icon != null) {
                    parent.setImageViewIcon(16908294, icon);
                }
            } else if (this.mBuilder.mN.extras.containsKey("vivo.summaryIconResBitmap") && (bitmap = (Bitmap) this.mBuilder.mN.extras.getParcelable("vivo.summaryIconResBitmap")) != null) {
                parent.setImageViewBitmap(16908294, bitmap);
            }
            if (this.mBuilder.mN.extras.containsKey(Notification.EXTRA_SUBSTITUTE_APP_NAME)) {
                String customAppName = this.mBuilder.mN.extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME);
                if (!TextUtils.isEmpty(customAppName)) {
                    parent.setTextViewText(R.id.app_name_text, customAppName);
                }
            }
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            RemoteViews remoteViews = this.customContentView;
            if (remoteViews != null) {
                extras.putParcelable(EXTRA_CUSTOM_CONTENTVIEW, remoteViews);
            }
            RemoteViews remoteViews2 = this.customBigContentView;
            if (remoteViews2 != null) {
                extras.putParcelable(EXTRA_CUSTOM_BIG_CONTENTVIEW, remoteViews2);
            }
            RemoteViews remoteViews3 = this.customHeadsUpContentView;
            if (remoteViews3 != null) {
                extras.putParcelable(EXTRA_CUSTOM_HEADSUP_CONTENTVIEW, remoteViews3);
            }
            PendingIntent pendingIntent = this.deleteActionIntent;
            if (pendingIntent != null) {
                extras.putParcelable(EXTRA_CUSTOM_DELETEACTIONINTENT, pendingIntent);
            }
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            if (extras.containsKey(EXTRA_CUSTOM_CONTENTVIEW)) {
                this.customContentView = (RemoteViews) extras.getParcelable(EXTRA_CUSTOM_CONTENTVIEW);
            }
            if (extras.containsKey(EXTRA_CUSTOM_BIG_CONTENTVIEW)) {
                this.customBigContentView = (RemoteViews) extras.getParcelable(EXTRA_CUSTOM_BIG_CONTENTVIEW);
            }
            if (extras.containsKey(EXTRA_CUSTOM_HEADSUP_CONTENTVIEW)) {
                this.customHeadsUpContentView = (RemoteViews) extras.getParcelable(EXTRA_CUSTOM_HEADSUP_CONTENTVIEW);
            }
            if (extras.containsKey(EXTRA_CUSTOM_DELETEACTIONINTENT)) {
                this.deleteActionIntent = (PendingIntent) extras.getParcelable(EXTRA_CUSTOM_DELETEACTIONINTENT);
            }
        }

        @Override // android.app.Notification.Style
        protected boolean hasProgress() {
            return false;
        }

        @Override // android.app.Notification.Style
        public boolean hasSummaryInHeader() {
            return true;
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            VivoCustomViewStyle otherS = (VivoCustomViewStyle) other;
            return (Objects.equals(this.customContentView, otherS.customContentView) && Objects.equals(this.customBigContentView, otherS.customBigContentView) && Objects.equals(this.customHeadsUpContentView, otherS.customHeadsUpContentView)) ? false : true;
        }

        private int getVivoCustomBaseLayout() {
            return 50528368;
        }
    }

    public static class BigPictureStyle extends Style {
        public static final int MIN_ASHMEM_BITMAP_SIZE = 131072;
        private Icon mBigLargeIcon;
        private boolean mBigLargeIconSet = false;
        private Bitmap mPicture;

        public BigPictureStyle() {
        }

        @Deprecated
        public BigPictureStyle(Builder builder) {
            setBuilder(builder);
        }

        public BigPictureStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(Notification.safeCharSequence(title));
            return this;
        }

        public BigPictureStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(Notification.safeCharSequence(cs));
            return this;
        }

        public Bitmap getBigPicture() {
            return this.mPicture;
        }

        public BigPictureStyle bigPicture(Bitmap b) {
            this.mPicture = b;
            return this;
        }

        public BigPictureStyle bigLargeIcon(Bitmap b) {
            return bigLargeIcon(b != null ? Icon.createWithBitmap(b) : null);
        }

        public BigPictureStyle bigLargeIcon(Icon icon) {
            this.mBigLargeIconSet = true;
            this.mBigLargeIcon = icon;
            return this;
        }

        @Override // android.app.Notification.Style
        public void purgeResources() {
            super.purgeResources();
            Bitmap bitmap = this.mPicture;
            if (bitmap != null && bitmap.isMutable() && this.mPicture.getAllocationByteCount() >= 131072) {
                this.mPicture = this.mPicture.createAshmemBitmap();
            }
            Icon icon = this.mBigLargeIcon;
            if (icon != null) {
                icon.convertToAshmem();
            }
        }

        @Override // android.app.Notification.Style
        public void reduceImageSizes(Context context) {
            int i;
            int i2;
            int i3;
            super.reduceImageSizes(context);
            Resources resources = context.getResources();
            boolean isLowRam = ActivityManager.isLowRamDeviceStatic();
            if (this.mPicture != null) {
                if (isLowRam) {
                    i2 = R.dimen.notification_big_picture_max_height_low_ram;
                } else {
                    i2 = R.dimen.notification_big_picture_max_height;
                }
                int maxPictureWidth = resources.getDimensionPixelSize(i2);
                if (isLowRam) {
                    i3 = R.dimen.notification_big_picture_max_width_low_ram;
                } else {
                    i3 = R.dimen.notification_big_picture_max_width;
                }
                int maxPictureHeight = resources.getDimensionPixelSize(i3);
                this.mPicture = Icon.scaleDownIfNecessary(this.mPicture, maxPictureWidth, maxPictureHeight);
            }
            if (this.mBigLargeIcon != null) {
                if (isLowRam) {
                    i = R.dimen.notification_right_icon_size_low_ram;
                } else {
                    i = R.dimen.notification_right_icon_size;
                }
                int rightIconSize = resources.getDimensionPixelSize(i);
                this.mBigLargeIcon.scaleDownIfNecessary(rightIconSize, rightIconSize);
            }
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            Icon oldLargeIcon = null;
            Bitmap largeIconLegacy = null;
            if (this.mBigLargeIconSet) {
                oldLargeIcon = this.mBuilder.mN.mLargeIcon;
                this.mBuilder.mN.mLargeIcon = this.mBigLargeIcon;
                largeIconLegacy = this.mBuilder.mN.largeIcon;
                this.mBuilder.mN.largeIcon = null;
            }
            StandardTemplateParams p = this.mBuilder.mParams.reset().fillTextsFrom(this.mBuilder);
            RemoteViews contentView = getStandardView(this.mBuilder.getBigPictureLayoutResource(), p, null);
            if (this.mSummaryTextSet) {
                contentView.setTextViewText(R.id.text, this.mBuilder.processTextSpans(this.mBuilder.processLegacyText(this.mSummaryText)));
                this.mBuilder.setTextViewColorSecondary(contentView, R.id.text, p);
                contentView.setViewVisibility(R.id.text, 0);
            }
            this.mBuilder.setContentMinHeight(contentView, this.mBuilder.mN.hasLargeIcon());
            if (this.mBigLargeIconSet) {
                this.mBuilder.mN.mLargeIcon = oldLargeIcon;
                this.mBuilder.mN.largeIcon = largeIconLegacy;
            }
            contentView.setImageViewBitmap(R.id.big_picture, this.mPicture);
            return contentView;
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            if (this.mBigLargeIconSet) {
                extras.putParcelable(Notification.EXTRA_LARGE_ICON_BIG, this.mBigLargeIcon);
            }
            extras.putParcelable(Notification.EXTRA_PICTURE, this.mPicture);
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            if (extras.containsKey(Notification.EXTRA_LARGE_ICON_BIG)) {
                this.mBigLargeIconSet = true;
                this.mBigLargeIcon = (Icon) extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG);
            }
            this.mPicture = (Bitmap) extras.getParcelable(Notification.EXTRA_PICTURE);
        }

        @Override // android.app.Notification.Style
        public boolean hasSummaryInHeader() {
            return false;
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            BigPictureStyle otherS = (BigPictureStyle) other;
            return areBitmapsObviouslyDifferent(getBigPicture(), otherS.getBigPicture());
        }

        private static boolean areBitmapsObviouslyDifferent(Bitmap a, Bitmap b) {
            if (a == b) {
                return false;
            }
            if (a == null || b == null) {
                return true;
            }
            if (a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() && a.getConfig() == b.getConfig() && a.getGenerationId() == b.getGenerationId()) {
                return false;
            }
            return true;
        }
    }

    public static class BigTextStyle extends Style {
        private CharSequence mBigText;

        public BigTextStyle() {
        }

        @Deprecated
        public BigTextStyle(Builder builder) {
            setBuilder(builder);
        }

        public BigTextStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(Notification.safeCharSequence(title));
            return this;
        }

        public BigTextStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(Notification.safeCharSequence(cs));
            return this;
        }

        public BigTextStyle bigText(CharSequence cs) {
            this.mBigText = Notification.safeCharSequence(cs);
            return this;
        }

        public CharSequence getBigText() {
            return this.mBigText;
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            extras.putCharSequence(Notification.EXTRA_BIG_TEXT, this.mBigText);
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            this.mBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            if (increasedHeight) {
                this.mBuilder.mOriginalActions = this.mBuilder.mActions;
                this.mBuilder.mActions = new ArrayList();
                RemoteViews remoteViews = makeBigContentView();
                this.mBuilder.mActions = this.mBuilder.mOriginalActions;
                this.mBuilder.mOriginalActions = null;
                return remoteViews;
            }
            RemoteViews remoteViews2 = super.makeContentView(increasedHeight);
            return remoteViews2;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            if (increasedHeight && this.mBuilder.mActions.size() > 0) {
                return makeBigContentView();
            }
            return super.makeHeadsUpContentView(increasedHeight);
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            StandardTemplateParams p = this.mBuilder.mParams.reset().fillTextsFrom(this.mBuilder).text(null);
            TemplateBindResult result = new TemplateBindResult();
            RemoteViews contentView = getStandardView(this.mBuilder.getBigTextLayoutResource(), p, result);
            contentView.setInt(R.id.big_text, "setImageEndMargin", result.getIconMarginEnd());
            CharSequence bigTextText = this.mBuilder.processLegacyText(this.mBigText);
            if (TextUtils.isEmpty(bigTextText)) {
                bigTextText = this.mBuilder.processLegacyText(this.mBuilder.getAllExtras().getCharSequence(Notification.EXTRA_TEXT));
            }
            contentView.setTextViewText(R.id.big_text, this.mBuilder.processTextSpans(bigTextText));
            this.mBuilder.setTextViewColorSecondary(contentView, R.id.big_text, p);
            contentView.setViewVisibility(R.id.big_text, TextUtils.isEmpty(bigTextText) ? 8 : 0);
            contentView.setBoolean(R.id.big_text, "setHasImage", result.isRightIconContainerVisible());
            return contentView;
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            BigTextStyle newS = (BigTextStyle) other;
            return true ^ Objects.equals(String.valueOf(getBigText()), String.valueOf(newS.getBigText()));
        }
    }

    public static class MessagingStyle extends Style {
        public static final int CONVERSATION_TYPE_IMPORTANT = 2;
        public static final int CONVERSATION_TYPE_LEGACY = 0;
        public static final int CONVERSATION_TYPE_NORMAL = 1;
        public static final int MAXIMUM_RETAINED_MESSAGES = 25;
        CharSequence mConversationTitle;
        int mConversationType;
        List<Message> mHistoricMessages;
        boolean mIsGroupConversation;
        List<Message> mMessages;
        Icon mShortcutIcon;
        int mUnreadMessageCount;
        Person mUser;

        @Retention(RetentionPolicy.SOURCE)
        public @interface ConversationType {
        }

        MessagingStyle() {
            this.mMessages = new ArrayList();
            this.mHistoricMessages = new ArrayList();
            this.mConversationType = 0;
        }

        public MessagingStyle(CharSequence userDisplayName) {
            this(new Person.Builder().setName(userDisplayName).build());
        }

        public MessagingStyle(Person user) {
            this.mMessages = new ArrayList();
            this.mHistoricMessages = new ArrayList();
            this.mConversationType = 0;
            this.mUser = user;
        }

        @Override // android.app.Notification.Style
        public void validate(Context context) {
            super.validate(context);
            if (context.getApplicationInfo().targetSdkVersion >= 28) {
                Person person = this.mUser;
                if (person == null || person.getName() == null) {
                    throw new RuntimeException("User must be valid and have a name.");
                }
            }
        }

        @Override // android.app.Notification.Style
        public CharSequence getHeadsUpStatusBarText() {
            CharSequence conversationTitle;
            if (!TextUtils.isEmpty(((Style) this).mBigContentTitle)) {
                conversationTitle = ((Style) this).mBigContentTitle;
            } else {
                conversationTitle = this.mConversationTitle;
            }
            if (this.mConversationType == 0 && !TextUtils.isEmpty(conversationTitle) && !hasOnlyWhiteSpaceSenders()) {
                return conversationTitle;
            }
            return null;
        }

        public Person getUser() {
            return this.mUser;
        }

        public CharSequence getUserDisplayName() {
            return this.mUser.getName();
        }

        public MessagingStyle setConversationTitle(CharSequence conversationTitle) {
            this.mConversationTitle = conversationTitle;
            return this;
        }

        public CharSequence getConversationTitle() {
            return this.mConversationTitle;
        }

        public MessagingStyle setShortcutIcon(Icon conversationIcon) {
            this.mShortcutIcon = conversationIcon;
            return this;
        }

        public Icon getShortcutIcon() {
            return this.mShortcutIcon;
        }

        public MessagingStyle setConversationType(int conversationType) {
            this.mConversationType = conversationType;
            return this;
        }

        public int getConversationType() {
            return this.mConversationType;
        }

        public int getUnreadMessageCount() {
            return this.mUnreadMessageCount;
        }

        public MessagingStyle setUnreadMessageCount(int unreadMessageCount) {
            this.mUnreadMessageCount = unreadMessageCount;
            return this;
        }

        public MessagingStyle addMessage(CharSequence text, long timestamp, CharSequence sender) {
            return addMessage(text, timestamp, sender == null ? null : new Person.Builder().setName(sender).build());
        }

        public MessagingStyle addMessage(CharSequence text, long timestamp, Person sender) {
            return addMessage(new Message(text, timestamp, sender));
        }

        public MessagingStyle addMessage(Message message) {
            this.mMessages.add(message);
            if (this.mMessages.size() > 25) {
                this.mMessages.remove(0);
            }
            return this;
        }

        public MessagingStyle addHistoricMessage(Message message) {
            this.mHistoricMessages.add(message);
            if (this.mHistoricMessages.size() > 25) {
                this.mHistoricMessages.remove(0);
            }
            return this;
        }

        public List<Message> getMessages() {
            return this.mMessages;
        }

        public List<Message> getHistoricMessages() {
            return this.mHistoricMessages;
        }

        public MessagingStyle setGroupConversation(boolean isGroupConversation) {
            this.mIsGroupConversation = isGroupConversation;
            return this;
        }

        public boolean isGroupConversation() {
            if (this.mBuilder == null || this.mBuilder.mContext.getApplicationInfo().targetSdkVersion >= 28) {
                return this.mIsGroupConversation;
            }
            return this.mConversationTitle != null;
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            Person person = this.mUser;
            if (person != null) {
                extras.putCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME, person.getName());
                extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, this.mUser);
            }
            CharSequence charSequence = this.mConversationTitle;
            if (charSequence != null) {
                extras.putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, charSequence);
            }
            if (!this.mMessages.isEmpty()) {
                extras.putParcelableArray(Notification.EXTRA_MESSAGES, Message.getBundleArrayForMessages(this.mMessages));
            }
            if (!this.mHistoricMessages.isEmpty()) {
                extras.putParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES, Message.getBundleArrayForMessages(this.mHistoricMessages));
            }
            Icon icon = this.mShortcutIcon;
            if (icon != null) {
                extras.putParcelable(Notification.EXTRA_CONVERSATION_ICON, icon);
            }
            extras.putInt(Notification.EXTRA_CONVERSATION_UNREAD_MESSAGE_COUNT, this.mUnreadMessageCount);
            fixTitleAndTextExtras(extras);
            extras.putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, this.mIsGroupConversation);
        }

        private void fixTitleAndTextExtras(Bundle extras) {
            CharSequence text;
            CharSequence title;
            Message m = findLatestIncomingMessage();
            CharSequence sender = null;
            if (m == null) {
                text = null;
            } else {
                text = m.mText;
            }
            if (m != null) {
                sender = ((m.mSender == null || TextUtils.isEmpty(m.mSender.getName())) ? this.mUser : m.mSender).getName();
            }
            if (!TextUtils.isEmpty(this.mConversationTitle)) {
                if (!TextUtils.isEmpty(sender)) {
                    BidiFormatter bidi = BidiFormatter.getInstance();
                    title = this.mBuilder.mContext.getString(R.string.notification_messaging_title_template, bidi.unicodeWrap(this.mConversationTitle), bidi.unicodeWrap(sender));
                } else {
                    title = this.mConversationTitle;
                }
            } else {
                title = sender;
            }
            if (title != null) {
                extras.putCharSequence(Notification.EXTRA_TITLE, title);
            }
            if (text != null) {
                extras.putCharSequence(Notification.EXTRA_TEXT, text);
            }
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            Person person = (Person) extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
            this.mUser = person;
            if (person == null) {
                CharSequence displayName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME);
                this.mUser = new Person.Builder().setName(displayName).build();
            }
            this.mConversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            this.mMessages = Message.getMessagesFromBundleArray(messages);
            Parcelable[] histMessages = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES);
            this.mHistoricMessages = Message.getMessagesFromBundleArray(histMessages);
            this.mIsGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION);
            this.mUnreadMessageCount = extras.getInt(Notification.EXTRA_CONVERSATION_UNREAD_MESSAGE_COUNT);
            this.mShortcutIcon = (Icon) extras.getParcelable(Notification.EXTRA_CONVERSATION_ICON);
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            this.mBuilder.mOriginalActions = this.mBuilder.mActions;
            this.mBuilder.mActions = new ArrayList();
            RemoteViews remoteViews = makeMessagingView(true, false);
            this.mBuilder.mActions = this.mBuilder.mOriginalActions;
            this.mBuilder.mOriginalActions = null;
            return remoteViews;
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            CharSequence name;
            CharSequence name2;
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            MessagingStyle newS = (MessagingStyle) other;
            List<Message> oldMs = getMessages();
            List<Message> newMs = newS.getMessages();
            if (oldMs == null || newMs == null) {
                newMs = new ArrayList();
            }
            int n = oldMs.size();
            if (n != newMs.size()) {
                return true;
            }
            for (int i = 0; i < n; i++) {
                Message oldM = oldMs.get(i);
                Message newM = newMs.get(i);
                if (!Objects.equals(String.valueOf(oldM.getText()), String.valueOf(newM.getText())) || !Objects.equals(oldM.getDataUri(), newM.getDataUri())) {
                    return true;
                }
                if (oldM.getSenderPerson() == null) {
                    name = oldM.getSender();
                } else {
                    name = oldM.getSenderPerson().getName();
                }
                String oldSender = String.valueOf(name);
                if (newM.getSenderPerson() == null) {
                    name2 = newM.getSender();
                } else {
                    name2 = newM.getSenderPerson().getName();
                }
                String newSender = String.valueOf(name2);
                if (!Objects.equals(oldSender, newSender)) {
                    return true;
                }
                String oldKey = oldM.getSenderPerson() == null ? null : oldM.getSenderPerson().getKey();
                String newKey = newM.getSenderPerson() != null ? newM.getSenderPerson().getKey() : null;
                if (!Objects.equals(oldKey, newKey)) {
                    return true;
                }
            }
            return false;
        }

        private Message findLatestIncomingMessage() {
            return findLatestIncomingMessage(this.mMessages);
        }

        public static Message findLatestIncomingMessage(List<Message> messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m.mSender != null && !TextUtils.isEmpty(m.mSender.getName())) {
                    return m;
                }
            }
            if (!messages.isEmpty()) {
                return messages.get(messages.size() - 1);
            }
            return null;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            RemoteViews remoteViews = makeMessagingView(false, true);
            if (!Notification.IS_TIER_VOS) {
                remoteViews.setInt(R.id.notification_messaging, "setMaxDisplayedLines", 7);
            }
            return remoteViews;
        }

        private RemoteViews makeMessagingView(boolean isCollapsed, boolean hideRightIcons) {
            CharSequence conversationTitle;
            boolean isOneToOne;
            int iResolveContrastColor;
            if (!TextUtils.isEmpty(((Style) this).mBigContentTitle)) {
                conversationTitle = ((Style) this).mBigContentTitle;
            } else {
                conversationTitle = this.mConversationTitle;
            }
            boolean z = true;
            boolean atLeastP = this.mBuilder.mContext.getApplicationInfo().targetSdkVersion >= 28;
            CharSequence nameReplacement = null;
            if (!atLeastP) {
                isOneToOne = TextUtils.isEmpty(conversationTitle);
                if (hasOnlyWhiteSpaceSenders()) {
                    isOneToOne = true;
                    nameReplacement = conversationTitle;
                    conversationTitle = null;
                }
            } else {
                boolean isOneToOne2 = isGroupConversation();
                isOneToOne = !isOneToOne2;
            }
            boolean isConversationLayout = this.mConversationType != 0;
            boolean isImportantConversation = this.mConversationType == 2;
            Icon largeIcon = this.mBuilder.mN.mLargeIcon;
            TemplateBindResult bindResult = new TemplateBindResult();
            StandardTemplateParams standardTemplateParamsText = this.mBuilder.mParams.reset().hasProgress(false).title(conversationTitle).text(null);
            if (!hideRightIcons && !isOneToOne) {
                z = false;
            }
            StandardTemplateParams p = standardTemplateParamsText.hideLargeIcon(z).hideReplyIcon(hideRightIcons).headerTextSecondary(conversationTitle);
            RemoteViews contentView = this.mBuilder.applyStandardTemplateWithActions(isConversationLayout ? this.mBuilder.getConversationLayoutResource() : this.mBuilder.getMessagingLayoutResource(), p, bindResult);
            addExtras(this.mBuilder.mN.extras);
            if (!isConversationLayout) {
                contentView.setViewLayoutMarginEnd(R.id.notification_messaging, bindResult.getIconMarginEnd());
            }
            if (this.mBuilder.isColorized(p)) {
                iResolveContrastColor = this.mBuilder.getPrimaryTextColor(p);
            } else {
                iResolveContrastColor = this.mBuilder.resolveContrastColor(p);
            }
            contentView.setInt(R.id.status_bar_latest_event_content, "setLayoutColor", iResolveContrastColor);
            contentView.setInt(R.id.status_bar_latest_event_content, "setSenderTextColor", this.mBuilder.getPrimaryTextColor(p));
            contentView.setInt(R.id.status_bar_latest_event_content, "setMessageTextColor", this.mBuilder.getSecondaryTextColor(p));
            contentView.setInt(R.id.status_bar_latest_event_content, "setNotificationBackgroundColor", this.mBuilder.resolveBackgroundColor(p));
            contentView.setBoolean(R.id.status_bar_latest_event_content, "setIsCollapsed", isCollapsed);
            contentView.setIcon(R.id.status_bar_latest_event_content, "setAvatarReplacement", this.mBuilder.mN.mLargeIcon);
            contentView.setCharSequence(R.id.status_bar_latest_event_content, "setNameReplacement", nameReplacement);
            contentView.setBoolean(R.id.status_bar_latest_event_content, "setIsOneToOne", isOneToOne);
            contentView.setCharSequence(R.id.status_bar_latest_event_content, "setConversationTitle", conversationTitle);
            if (isConversationLayout) {
                contentView.setIcon(R.id.status_bar_latest_event_content, "setShortcutIcon", this.mShortcutIcon);
                contentView.setBoolean(R.id.status_bar_latest_event_content, "setIsImportantConversation", isImportantConversation);
            }
            contentView.setIcon(R.id.status_bar_latest_event_content, "setLargeIcon", largeIcon);
            contentView.setBundle(R.id.status_bar_latest_event_content, "setData", this.mBuilder.mN.extras);
            return contentView;
        }

        private boolean hasOnlyWhiteSpaceSenders() {
            for (int i = 0; i < this.mMessages.size(); i++) {
                Message m = this.mMessages.get(i);
                Person sender = m.getSenderPerson();
                if (sender != null && !isWhiteSpace(sender.getName())) {
                    return false;
                }
            }
            return true;
        }

        private boolean isWhiteSpace(CharSequence sender) {
            if (TextUtils.isEmpty(sender) || sender.toString().matches("^\\s*$")) {
                return true;
            }
            for (int i = 0; i < sender.length(); i++) {
                char c = sender.charAt(i);
                if (c != 8203) {
                    return false;
                }
            }
            return true;
        }

        private CharSequence createConversationTitleFromMessages() {
            ArraySet<CharSequence> names = new ArraySet<>();
            for (int i = 0; i < this.mMessages.size(); i++) {
                Message m = this.mMessages.get(i);
                Person sender = m.getSenderPerson();
                if (sender != null) {
                    names.add(sender.getName());
                }
            }
            SpannableStringBuilder title = new SpannableStringBuilder();
            int size = names.size();
            for (int i2 = 0; i2 < size; i2++) {
                CharSequence name = names.valueAt(i2);
                if (!TextUtils.isEmpty(title)) {
                    title.append((CharSequence) ", ");
                }
                title.append(BidiFormatter.getInstance().unicodeWrap(name));
            }
            return title;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            RemoteViews remoteViews = makeMessagingView(true, true);
            if (this.mConversationType == 0) {
                remoteViews.setInt(R.id.notification_messaging, "setMaxDisplayedLines", 1);
            }
            return remoteViews;
        }

        private static TextAppearanceSpan makeFontColorSpan(int color) {
            return new TextAppearanceSpan(null, 0, 0, ColorStateList.valueOf(color), null);
        }

        public static final class Message {
            static final String KEY_DATA_MIME_TYPE = "type";
            static final String KEY_DATA_URI = "uri";
            static final String KEY_EXTRAS_BUNDLE = "extras";
            static final String KEY_REMOTE_INPUT_HISTORY = "remote_input_history";
            static final String KEY_SENDER = "sender";
            static final String KEY_SENDER_PERSON = "sender_person";
            public static final String KEY_TEXT = "text";
            static final String KEY_TIMESTAMP = "time";
            private String mDataMimeType;
            private Uri mDataUri;
            private Bundle mExtras;
            private final boolean mRemoteInputHistory;
            private final Person mSender;
            private final CharSequence mText;
            private final long mTimestamp;

            public Message(CharSequence text, long timestamp, CharSequence sender) {
                this(text, timestamp, sender == null ? null : new Person.Builder().setName(sender).build());
            }

            public Message(CharSequence text, long timestamp, Person sender) {
                this(text, timestamp, sender, false);
            }

            public Message(CharSequence text, long timestamp, Person sender, boolean remoteInputHistory) {
                this.mExtras = new Bundle();
                this.mText = Notification.safeCharSequence(text);
                this.mTimestamp = timestamp;
                this.mSender = sender;
                this.mRemoteInputHistory = remoteInputHistory;
            }

            public Message setData(String dataMimeType, Uri dataUri) {
                this.mDataMimeType = dataMimeType;
                this.mDataUri = dataUri;
                return this;
            }

            public CharSequence getText() {
                return this.mText;
            }

            public long getTimestamp() {
                return this.mTimestamp;
            }

            public Bundle getExtras() {
                return this.mExtras;
            }

            public CharSequence getSender() {
                Person person = this.mSender;
                if (person == null) {
                    return null;
                }
                return person.getName();
            }

            public Person getSenderPerson() {
                return this.mSender;
            }

            public String getDataMimeType() {
                return this.mDataMimeType;
            }

            public Uri getDataUri() {
                return this.mDataUri;
            }

            public boolean isRemoteInputHistory() {
                return this.mRemoteInputHistory;
            }

            public Bundle toBundle() {
                Bundle bundle = new Bundle();
                CharSequence charSequence = this.mText;
                if (charSequence != null) {
                    bundle.putCharSequence("text", charSequence);
                }
                bundle.putLong("time", this.mTimestamp);
                Person person = this.mSender;
                if (person != null) {
                    bundle.putCharSequence("sender", Notification.safeCharSequence(person.getName()));
                    bundle.putParcelable(KEY_SENDER_PERSON, this.mSender);
                }
                String str = this.mDataMimeType;
                if (str != null) {
                    bundle.putString("type", str);
                }
                Uri uri = this.mDataUri;
                if (uri != null) {
                    bundle.putParcelable("uri", uri);
                }
                Bundle bundle2 = this.mExtras;
                if (bundle2 != null) {
                    bundle.putBundle(KEY_EXTRAS_BUNDLE, bundle2);
                }
                boolean z = this.mRemoteInputHistory;
                if (z) {
                    bundle.putBoolean(KEY_REMOTE_INPUT_HISTORY, z);
                }
                return bundle;
            }

            static Bundle[] getBundleArrayForMessages(List<Message> messages) {
                Bundle[] bundles = new Bundle[messages.size()];
                int N = messages.size();
                for (int i = 0; i < N; i++) {
                    bundles[i] = messages.get(i).toBundle();
                }
                return bundles;
            }

            public static List<Message> getMessagesFromBundleArray(Parcelable[] bundles) {
                Message message;
                if (bundles == null) {
                    return new ArrayList();
                }
                List<Message> messages = new ArrayList<>(bundles.length);
                for (int i = 0; i < bundles.length; i++) {
                    if ((bundles[i] instanceof Bundle) && (message = getMessageFromBundle((Bundle) bundles[i])) != null) {
                        messages.add(message);
                    }
                }
                return messages;
            }

            public static Message getMessageFromBundle(Bundle bundle) {
                Person senderPerson;
                CharSequence senderName;
                try {
                    if (bundle.containsKey("text") && bundle.containsKey("time")) {
                        Person senderPerson2 = (Person) bundle.getParcelable(KEY_SENDER_PERSON);
                        if (senderPerson2 == null && (senderName = bundle.getCharSequence("sender")) != null) {
                            senderPerson = new Person.Builder().setName(senderName).build();
                        } else {
                            senderPerson = senderPerson2;
                        }
                        Message message = new Message(bundle.getCharSequence("text"), bundle.getLong("time"), senderPerson, bundle.getBoolean(KEY_REMOTE_INPUT_HISTORY, false));
                        if (bundle.containsKey("type") && bundle.containsKey("uri")) {
                            message.setData(bundle.getString("type"), (Uri) bundle.getParcelable("uri"));
                        }
                        if (bundle.containsKey(KEY_EXTRAS_BUNDLE)) {
                            message.getExtras().putAll(bundle.getBundle(KEY_EXTRAS_BUNDLE));
                        }
                        return message;
                    }
                    return null;
                } catch (ClassCastException e) {
                    return null;
                }
            }
        }
    }

    public static class InboxStyle extends Style {
        private static final int NUMBER_OF_HISTORY_ALLOWED_UNTIL_REDUCTION = 1;
        private ArrayList<CharSequence> mTexts = new ArrayList<>(5);

        public InboxStyle() {
        }

        @Deprecated
        public InboxStyle(Builder builder) {
            setBuilder(builder);
        }

        public InboxStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(Notification.safeCharSequence(title));
            return this;
        }

        public InboxStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(Notification.safeCharSequence(cs));
            return this;
        }

        public InboxStyle addLine(CharSequence cs) {
            this.mTexts.add(Notification.safeCharSequence(cs));
            return this;
        }

        public ArrayList<CharSequence> getLines() {
            return this.mTexts;
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            CharSequence[] a = new CharSequence[this.mTexts.size()];
            extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, (CharSequence[]) this.mTexts.toArray(a));
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            this.mTexts.clear();
            if (extras.containsKey(Notification.EXTRA_TEXT_LINES)) {
                Collections.addAll(this.mTexts, extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));
            }
        }

        /* JADX WARN: Removed duplicated region for block: B:22:0x00a8  */
        @Override // android.app.Notification.Style
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public android.widget.RemoteViews makeBigContentView() {
            /*
                Method dump skipped, instruction units count: 348
                To view this dump add '--comments-level debug' option
            */
            throw new UnsupportedOperationException("Method not decompiled: android.app.Notification.InboxStyle.makeBigContentView():android.widget.RemoteViews");
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            InboxStyle newS = (InboxStyle) other;
            ArrayList<CharSequence> myLines = getLines();
            ArrayList<CharSequence> newLines = newS.getLines();
            int n = myLines.size();
            if (n != newLines.size()) {
                return true;
            }
            for (int i = 0; i < n; i++) {
                if (!Objects.equals(String.valueOf(myLines.get(i)), String.valueOf(newLines.get(i)))) {
                    return true;
                }
            }
            return false;
        }

        private void handleInboxImageMargin(RemoteViews contentView, int id, boolean first, int marginEndValue) {
            int endMargin = 0;
            if (first) {
                int max = this.mBuilder.mN.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
                boolean ind = this.mBuilder.mN.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE);
                boolean hasProgress = max != 0 || ind;
                if (!hasProgress) {
                    endMargin = marginEndValue;
                }
            }
            contentView.setViewLayoutMarginEnd(id, endMargin);
        }
    }

    public static class MediaStyle extends Style {
        static final int MAX_MEDIA_BUTTONS = 5;
        static final int MAX_MEDIA_BUTTONS_IN_COMPACT = 4;
        private static final int[] MEDIA_BUTTON_IDS = {R.id.action0, R.id.action1, R.id.action2, R.id.action3, R.id.action4};
        private int[] mActionsToShowInCompact = null;
        private MediaSession.Token mToken;

        public MediaStyle() {
        }

        @Deprecated
        public MediaStyle(Builder builder) {
            setBuilder(builder);
        }

        public MediaStyle setShowActionsInCompactView(int... actions) {
            this.mActionsToShowInCompact = actions;
            return this;
        }

        public MediaStyle setMediaSession(MediaSession.Token token) {
            this.mToken = token;
            return this;
        }

        @Override // android.app.Notification.Style
        public Notification buildStyled(Notification wip) {
            super.buildStyled(wip);
            if (wip.category == null) {
                wip.category = Notification.CATEGORY_TRANSPORT;
            }
            return wip;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            return makeMediaContentView();
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            return makeMediaBigContentView();
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            return makeMediaContentView();
        }

        @Override // android.app.Notification.Style
        public void addExtras(Bundle extras) {
            super.addExtras(extras);
            MediaSession.Token token = this.mToken;
            if (token != null) {
                extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, token);
            }
            int[] iArr = this.mActionsToShowInCompact;
            if (iArr != null) {
                extras.putIntArray(Notification.EXTRA_COMPACT_ACTIONS, iArr);
            }
        }

        @Override // android.app.Notification.Style
        protected void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                this.mToken = (MediaSession.Token) extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            }
            if (extras.containsKey(Notification.EXTRA_COMPACT_ACTIONS)) {
                this.mActionsToShowInCompact = extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS);
            }
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            return false;
        }

        private void bindMediaActionButton(RemoteViews container, int buttonId, Action action, StandardTemplateParams p) {
            int tintColor;
            boolean tombstone = action.actionIntent == null;
            container.setViewVisibility(buttonId, 0);
            if (buttonId != 16909158) {
                container.setImageViewIcon(buttonId, action.getIcon());
            }
            Resources resources = this.mBuilder.mContext.getResources();
            Configuration currentConfig = resources.getConfiguration();
            boolean inNightMode = (currentConfig.uiMode & 48) == 32;
            if (!this.mBuilder.shouldTintActionButtons() && !this.mBuilder.isColorized(p)) {
                tintColor = ContrastColorUtil.resolveColor(this.mBuilder.mContext, 0, inNightMode);
            } else {
                tintColor = getActionColor(p);
            }
            container.setDrawableTint(buttonId, false, tintColor, PorterDuff.Mode.SRC_ATOP);
            TypedArray typedArray = this.mBuilder.mContext.obtainStyledAttributes(new int[]{16843820});
            int rippleAlpha = Color.alpha(typedArray.getColor(0, 0));
            typedArray.recycle();
            int rippleColor = Color.argb(rippleAlpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor));
            container.setRippleDrawableColor(buttonId, ColorStateList.valueOf(rippleColor));
            if (!tombstone) {
                container.setOnClickPendingIntent(buttonId, action.actionIntent);
            }
            container.setContentDescription(buttonId, action.title);
        }

        private RemoteViews makeMediaContentView() {
            int layoutId;
            int numActionsToShow;
            StandardTemplateParams p = this.mBuilder.mParams.reset().hasProgress(false).fillTextsFrom(this.mBuilder);
            if (!Notification.IS_TIER_VOS) {
                layoutId = 50528353;
            } else {
                layoutId = R.layout.notification_template_material_media;
            }
            RemoteViews view = this.mBuilder.applyStandardTemplate(layoutId, p, null);
            int numActions = this.mBuilder.mActions.size();
            int[] iArr = this.mActionsToShowInCompact;
            if (iArr == null) {
                numActionsToShow = 0;
            } else {
                numActionsToShow = Math.min(iArr.length, 4);
            }
            if (numActionsToShow > numActions) {
                throw new IllegalArgumentException(String.format("setShowActionsInCompactView: action %d out of bounds (max %d)", Integer.valueOf(numActions), Integer.valueOf(numActions - 1)));
            }
            for (int i = 0; i < 4; i++) {
                if (i < numActionsToShow) {
                    Action action = (Action) this.mBuilder.mActions.get(this.mActionsToShowInCompact[i]);
                    bindMediaActionButton(view, MEDIA_BUTTON_IDS[i], action, p);
                } else {
                    view.setViewVisibility(MEDIA_BUTTON_IDS[i], 8);
                }
            }
            bindMediaActionButton(view, R.id.media_seamless, new Action(R.drawable.ic_media_seamless, this.mBuilder.mContext.getString(R.string.ext_media_seamless_action), null), p);
            view.setViewVisibility(R.id.media_seamless, 8);
            handleImage(view);
            int endMargin = R.dimen.notification_content_margin_end;
            if (this.mBuilder.mN.hasLargeIcon()) {
                endMargin = R.dimen.notification_media_image_margin_end;
            }
            view.setViewLayoutMarginEndDimen(R.id.notification_main_column, endMargin);
            return view;
        }

        private int getActionColor(StandardTemplateParams p) {
            return this.mBuilder.isColorized(p) ? this.mBuilder.getPrimaryTextColor(p) : this.mBuilder.resolveContrastColor(p);
        }

        private RemoteViews makeMediaBigContentView() {
            int actionsInCompact;
            int bigLayoutId;
            int actionCount = Math.min(this.mBuilder.mActions.size(), 5);
            int[] iArr = this.mActionsToShowInCompact;
            if (iArr == null) {
                actionsInCompact = 0;
            } else {
                actionsInCompact = Math.min(iArr.length, 4);
            }
            if (!this.mBuilder.mN.hasLargeIcon() && actionCount <= actionsInCompact) {
                return null;
            }
            StandardTemplateParams p = this.mBuilder.mParams.reset().hasProgress(false).fillTextsFrom(this.mBuilder);
            if (!Notification.IS_TIER_VOS) {
                bigLayoutId = 50528340;
            } else {
                bigLayoutId = R.layout.notification_template_material_big_media;
            }
            RemoteViews big = this.mBuilder.applyStandardTemplate(bigLayoutId, p, null);
            for (int i = 0; i < 5; i++) {
                if (i < actionCount) {
                    bindMediaActionButton(big, MEDIA_BUTTON_IDS[i], (Action) this.mBuilder.mActions.get(i), p);
                } else {
                    big.setViewVisibility(MEDIA_BUTTON_IDS[i], 8);
                }
            }
            bindMediaActionButton(big, R.id.media_seamless, new Action(R.drawable.ic_media_seamless, this.mBuilder.mContext.getString(R.string.ext_media_seamless_action), null), p);
            big.setViewVisibility(R.id.media_seamless, 8);
            handleImage(big);
            return big;
        }

        private void handleImage(RemoteViews contentView) {
            if (this.mBuilder.mN.hasLargeIcon()) {
                contentView.setViewLayoutMarginEndDimen(R.id.line1, 0);
                contentView.setViewLayoutMarginEndDimen(R.id.text, 0);
            }
        }

        @Override // android.app.Notification.Style
        protected boolean hasProgress() {
            return false;
        }
    }

    public static class DecoratedCustomViewStyle extends Style {
        @Override // android.app.Notification.Style
        public boolean displayCustomViewInline() {
            return true;
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            return makeStandardTemplateWithCustomContent(this.mBuilder.mN.contentView);
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            return makeDecoratedBigContentView();
        }

        @Override // android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            return makeDecoratedHeadsUpContentView();
        }

        private RemoteViews makeDecoratedHeadsUpContentView() {
            RemoteViews headsUpContentView = this.mBuilder.mN.headsUpContentView == null ? this.mBuilder.mN.contentView : this.mBuilder.mN.headsUpContentView;
            if (this.mBuilder.mActions.size() == 0) {
                return makeStandardTemplateWithCustomContent(headsUpContentView);
            }
            TemplateBindResult result = new TemplateBindResult();
            RemoteViews remoteViews = this.mBuilder.applyStandardTemplateWithActions(this.mBuilder.getBigBaseLayoutResource(), result);
            buildIntoRemoteViewContent(remoteViews, headsUpContentView, result);
            return remoteViews;
        }

        private RemoteViews makeStandardTemplateWithCustomContent(RemoteViews customContent) {
            TemplateBindResult result = new TemplateBindResult();
            RemoteViews remoteViews = this.mBuilder.applyStandardTemplate(this.mBuilder.getBaseLayoutResource(), result);
            buildIntoRemoteViewContent(remoteViews, customContent, result);
            return remoteViews;
        }

        private RemoteViews makeDecoratedBigContentView() {
            RemoteViews bigContentView = this.mBuilder.mN.bigContentView == null ? this.mBuilder.mN.contentView : this.mBuilder.mN.bigContentView;
            if (this.mBuilder.mActions.size() == 0) {
                return makeStandardTemplateWithCustomContent(bigContentView);
            }
            TemplateBindResult result = new TemplateBindResult();
            RemoteViews remoteViews = this.mBuilder.applyStandardTemplateWithActions(this.mBuilder.getBigBaseLayoutResource(), result);
            buildIntoRemoteViewContent(remoteViews, bigContentView, result);
            return remoteViews;
        }

        private void buildIntoRemoteViewContent(RemoteViews remoteViews, RemoteViews customContent, TemplateBindResult result) {
            int childIndex = -1;
            if (customContent != null) {
                RemoteViews customContent2 = customContent.mo11clone();
                remoteViews.removeAllViewsExceptId(R.id.notification_main_column, 16908301);
                remoteViews.addView(R.id.notification_main_column, customContent2, 0);
                remoteViews.addFlags(1);
                childIndex = 0;
            }
            remoteViews.setIntTag(R.id.notification_main_column, R.id.notification_custom_view_index_tag, childIndex);
            Resources resources = this.mBuilder.mContext.getResources();
            int endMargin = resources.getDimensionPixelSize(R.dimen.notification_content_margin_end) + result.getIconMarginEnd();
            remoteViews.setViewLayoutMarginEnd(R.id.notification_main_column, endMargin);
        }

        @Override // android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            return false;
        }
    }

    public static class DecoratedMediaCustomViewStyle extends MediaStyle {
        @Override // android.app.Notification.Style
        public boolean displayCustomViewInline() {
            return true;
        }

        @Override // android.app.Notification.MediaStyle, android.app.Notification.Style
        public RemoteViews makeContentView(boolean increasedHeight) {
            RemoteViews remoteViews = super.makeContentView(false);
            return buildIntoRemoteView(remoteViews, R.id.notification_content_container, this.mBuilder.mN.contentView);
        }

        @Override // android.app.Notification.MediaStyle, android.app.Notification.Style
        public RemoteViews makeBigContentView() {
            RemoteViews customRemoteView = this.mBuilder.mN.bigContentView != null ? this.mBuilder.mN.bigContentView : this.mBuilder.mN.contentView;
            return makeBigContentViewWithCustomContent(customRemoteView);
        }

        private RemoteViews makeBigContentViewWithCustomContent(RemoteViews customRemoteView) {
            RemoteViews remoteViews = super.makeBigContentView();
            if (remoteViews == null) {
                if (customRemoteView != this.mBuilder.mN.contentView) {
                    return buildIntoRemoteView(super.makeContentView(false), R.id.notification_content_container, customRemoteView);
                }
                return null;
            }
            return buildIntoRemoteView(remoteViews, R.id.notification_main_column, customRemoteView);
        }

        @Override // android.app.Notification.MediaStyle, android.app.Notification.Style
        public RemoteViews makeHeadsUpContentView(boolean increasedHeight) {
            RemoteViews customRemoteView = this.mBuilder.mN.headsUpContentView != null ? this.mBuilder.mN.headsUpContentView : this.mBuilder.mN.contentView;
            return makeBigContentViewWithCustomContent(customRemoteView);
        }

        @Override // android.app.Notification.MediaStyle, android.app.Notification.Style
        public boolean areNotificationsVisiblyDifferent(Style other) {
            if (other == null || getClass() != other.getClass()) {
                return true;
            }
            return false;
        }

        private RemoteViews buildIntoRemoteView(RemoteViews remoteViews, int id, RemoteViews customContent) {
            if (customContent != null) {
                RemoteViews customContent2 = customContent.mo11clone();
                customContent2.overrideTextColors(this.mBuilder.getPrimaryTextColor(this.mBuilder.mParams));
                remoteViews.removeAllViews(id);
                remoteViews.addView(id, customContent2);
                remoteViews.addFlags(1);
            }
            return remoteViews;
        }
    }

    public static final class BubbleMetadata implements Parcelable {
        public static final Parcelable.Creator<BubbleMetadata> CREATOR = new Parcelable.Creator<BubbleMetadata>() { // from class: android.app.Notification.BubbleMetadata.1
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public BubbleMetadata createFromParcel(Parcel source) {
                return new BubbleMetadata(source);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // android.os.Parcelable.Creator
            public BubbleMetadata[] newArray(int size) {
                return new BubbleMetadata[size];
            }
        };
        public static final int FLAG_AUTO_EXPAND_BUBBLE = 1;
        public static final int FLAG_SUPPRESS_NOTIFICATION = 2;
        private PendingIntent mDeleteIntent;
        private int mDesiredHeight;
        private int mDesiredHeightResId;
        private int mFlags;
        private Icon mIcon;
        private PendingIntent mPendingIntent;
        private String mShortcutId;

        private BubbleMetadata(PendingIntent expandIntent, PendingIntent deleteIntent, Icon icon, int height, int heightResId, String shortcutId) {
            this.mPendingIntent = expandIntent;
            this.mIcon = icon;
            this.mDesiredHeight = height;
            this.mDesiredHeightResId = heightResId;
            this.mDeleteIntent = deleteIntent;
            this.mShortcutId = shortcutId;
        }

        private BubbleMetadata(Parcel in) {
            if (in.readInt() != 0) {
                this.mPendingIntent = PendingIntent.CREATOR.createFromParcel(in);
            }
            if (in.readInt() != 0) {
                this.mIcon = Icon.CREATOR.createFromParcel(in);
            }
            this.mDesiredHeight = in.readInt();
            this.mFlags = in.readInt();
            if (in.readInt() != 0) {
                this.mDeleteIntent = PendingIntent.CREATOR.createFromParcel(in);
            }
            this.mDesiredHeightResId = in.readInt();
            if (in.readInt() != 0) {
                this.mShortcutId = in.readString8();
            }
        }

        public String getShortcutId() {
            return this.mShortcutId;
        }

        public PendingIntent getIntent() {
            return this.mPendingIntent;
        }

        @Deprecated
        public PendingIntent getBubbleIntent() {
            return this.mPendingIntent;
        }

        public PendingIntent getDeleteIntent() {
            return this.mDeleteIntent;
        }

        public Icon getIcon() {
            return this.mIcon;
        }

        @Deprecated
        public Icon getBubbleIcon() {
            return this.mIcon;
        }

        public int getDesiredHeight() {
            return this.mDesiredHeight;
        }

        public int getDesiredHeightResId() {
            return this.mDesiredHeightResId;
        }

        public boolean getAutoExpandBubble() {
            return (this.mFlags & 1) != 0;
        }

        public boolean isNotificationSuppressed() {
            return (this.mFlags & 2) != 0;
        }

        @Override // android.os.Parcelable
        public int describeContents() {
            return 0;
        }

        @Override // android.os.Parcelable
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeInt(this.mPendingIntent != null ? 1 : 0);
            PendingIntent pendingIntent = this.mPendingIntent;
            if (pendingIntent != null) {
                pendingIntent.writeToParcel(parcel, 0);
            }
            parcel.writeInt(this.mIcon != null ? 1 : 0);
            Icon icon = this.mIcon;
            if (icon != null) {
                icon.writeToParcel(parcel, 0);
            }
            parcel.writeInt(this.mDesiredHeight);
            parcel.writeInt(this.mFlags);
            parcel.writeInt(this.mDeleteIntent != null ? 1 : 0);
            PendingIntent pendingIntent2 = this.mDeleteIntent;
            if (pendingIntent2 != null) {
                pendingIntent2.writeToParcel(parcel, 0);
            }
            parcel.writeInt(this.mDesiredHeightResId);
            parcel.writeInt(!TextUtils.isEmpty(this.mShortcutId) ? 1 : 0);
            if (!TextUtils.isEmpty(this.mShortcutId)) {
                parcel.writeString8(this.mShortcutId);
            }
        }

        public void setFlags(int flags) {
            this.mFlags = flags;
        }

        public int getFlags() {
            return this.mFlags;
        }

        public static final class Builder {
            private PendingIntent mDeleteIntent;
            private int mDesiredHeight;
            private int mDesiredHeightResId;
            private int mFlags;
            private Icon mIcon;
            private PendingIntent mPendingIntent;
            private String mShortcutId;

            @Deprecated
            public Builder() {
            }

            public Builder(String shortcutId) {
                if (TextUtils.isEmpty(shortcutId)) {
                    throw new NullPointerException("Bubble requires a non-null shortcut id");
                }
                this.mShortcutId = shortcutId;
            }

            public Builder(PendingIntent intent, Icon icon) {
                if (intent == null) {
                    throw new NullPointerException("Bubble requires non-null pending intent");
                }
                if (icon == null) {
                    throw new NullPointerException("Bubbles require non-null icon");
                }
                if (icon.getType() != 6 && icon.getType() != 4) {
                    Log.w(Notification.TAG, "Bubbles work best with icons of TYPE_URI or TYPE_URI_ADAPTIVE_BITMAP. In the future, using an icon of this type will be required.");
                }
                this.mPendingIntent = intent;
                this.mIcon = icon;
            }

            @Deprecated
            public Builder createShortcutBubble(String shortcutId) {
                if (!TextUtils.isEmpty(shortcutId)) {
                    this.mPendingIntent = null;
                    this.mIcon = null;
                }
                this.mShortcutId = shortcutId;
                return this;
            }

            @Deprecated
            public Builder createIntentBubble(PendingIntent intent, Icon icon) {
                if (intent == null) {
                    throw new IllegalArgumentException("Bubble requires non-null pending intent");
                }
                if (icon == null) {
                    throw new IllegalArgumentException("Bubbles require non-null icon");
                }
                if (icon.getType() != 6 && icon.getType() != 4) {
                    Log.w(Notification.TAG, "Bubbles work best with icons of TYPE_URI or TYPE_URI_ADAPTIVE_BITMAP. In the future, using an icon of this type will be required.");
                }
                this.mShortcutId = null;
                this.mPendingIntent = intent;
                this.mIcon = icon;
                return this;
            }

            public Builder setIntent(PendingIntent intent) {
                if (this.mShortcutId != null) {
                    throw new IllegalStateException("Created as a shortcut bubble, cannot set a PendingIntent. Consider using BubbleMetadata.Builder(PendingIntent,Icon) instead.");
                }
                if (intent == null) {
                    throw new NullPointerException("Bubble requires non-null pending intent");
                }
                this.mPendingIntent = intent;
                return this;
            }

            public Builder setIcon(Icon icon) {
                if (this.mShortcutId != null) {
                    throw new IllegalStateException("Created as a shortcut bubble, cannot set an Icon. Consider using BubbleMetadata.Builder(PendingIntent,Icon) instead.");
                }
                if (icon == null) {
                    throw new NullPointerException("Bubbles require non-null icon");
                }
                if (icon.getType() != 6 && icon.getType() != 4) {
                    Log.w(Notification.TAG, "Bubbles work best with icons of TYPE_URI or TYPE_URI_ADAPTIVE_BITMAP. In the future, using an icon of this type will be required.");
                }
                this.mIcon = icon;
                return this;
            }

            public Builder setDesiredHeight(int height) {
                this.mDesiredHeight = Math.max(height, 0);
                this.mDesiredHeightResId = 0;
                return this;
            }

            public Builder setDesiredHeightResId(int heightResId) {
                this.mDesiredHeightResId = heightResId;
                this.mDesiredHeight = 0;
                return this;
            }

            public Builder setAutoExpandBubble(boolean shouldExpand) {
                setFlag(1, shouldExpand);
                return this;
            }

            public Builder setSuppressNotification(boolean shouldSuppressNotif) {
                setFlag(2, shouldSuppressNotif);
                return this;
            }

            public Builder setDeleteIntent(PendingIntent deleteIntent) {
                this.mDeleteIntent = deleteIntent;
                return this;
            }

            public BubbleMetadata build() {
                if (this.mShortcutId == null && this.mPendingIntent == null) {
                    throw new NullPointerException("Must supply pending intent or shortcut to bubble");
                }
                if (this.mShortcutId == null && this.mIcon == null) {
                    throw new NullPointerException("Must supply an icon or shortcut for the bubble");
                }
                BubbleMetadata data = new BubbleMetadata(this.mPendingIntent, this.mDeleteIntent, this.mIcon, this.mDesiredHeight, this.mDesiredHeightResId, this.mShortcutId);
                data.setFlags(this.mFlags);
                return data;
            }

            public Builder setFlag(int mask, boolean value) {
                if (value) {
                    this.mFlags |= mask;
                } else {
                    this.mFlags &= ~mask;
                }
                return this;
            }
        }
    }

    public static final class WearableExtender implements Extender {
        private static final int DEFAULT_CONTENT_ICON_GRAVITY = 8388613;
        private static final int DEFAULT_FLAGS = 1;
        private static final int DEFAULT_GRAVITY = 80;
        private static final String EXTRA_WEARABLE_EXTENSIONS = "android.wearable.EXTENSIONS";
        private static final int FLAG_BIG_PICTURE_AMBIENT = 32;
        private static final int FLAG_CONTENT_INTENT_AVAILABLE_OFFLINE = 1;
        private static final int FLAG_HINT_AVOID_BACKGROUND_CLIPPING = 16;
        private static final int FLAG_HINT_CONTENT_INTENT_LAUNCHES_ACTIVITY = 64;
        private static final int FLAG_HINT_HIDE_ICON = 2;
        private static final int FLAG_HINT_SHOW_BACKGROUND_ONLY = 4;
        private static final int FLAG_START_SCROLL_BOTTOM = 8;
        private static final String KEY_ACTIONS = "actions";
        private static final String KEY_BACKGROUND = "background";
        private static final String KEY_BRIDGE_TAG = "bridgeTag";
        private static final String KEY_CONTENT_ACTION_INDEX = "contentActionIndex";
        private static final String KEY_CONTENT_ICON = "contentIcon";
        private static final String KEY_CONTENT_ICON_GRAVITY = "contentIconGravity";
        private static final String KEY_CUSTOM_CONTENT_HEIGHT = "customContentHeight";
        private static final String KEY_CUSTOM_SIZE_PRESET = "customSizePreset";
        private static final String KEY_DISMISSAL_ID = "dismissalId";
        private static final String KEY_DISPLAY_INTENT = "displayIntent";
        private static final String KEY_FLAGS = "flags";
        private static final String KEY_GRAVITY = "gravity";
        private static final String KEY_HINT_SCREEN_TIMEOUT = "hintScreenTimeout";
        private static final String KEY_PAGES = "pages";

        @Deprecated
        public static final int SCREEN_TIMEOUT_LONG = -1;

        @Deprecated
        public static final int SCREEN_TIMEOUT_SHORT = 0;

        @Deprecated
        public static final int SIZE_DEFAULT = 0;

        @Deprecated
        public static final int SIZE_FULL_SCREEN = 5;

        @Deprecated
        public static final int SIZE_LARGE = 4;

        @Deprecated
        public static final int SIZE_MEDIUM = 3;

        @Deprecated
        public static final int SIZE_SMALL = 2;

        @Deprecated
        public static final int SIZE_XSMALL = 1;
        public static final int UNSET_ACTION_INDEX = -1;
        private ArrayList<Action> mActions;
        private Bitmap mBackground;
        private String mBridgeTag;
        private int mContentActionIndex;
        private int mContentIcon;
        private int mContentIconGravity;
        private int mCustomContentHeight;
        private int mCustomSizePreset;
        private String mDismissalId;
        private PendingIntent mDisplayIntent;
        private int mFlags;
        private int mGravity;
        private int mHintScreenTimeout;
        private ArrayList<Notification> mPages;

        public WearableExtender() {
            this.mActions = new ArrayList<>();
            this.mFlags = 1;
            this.mPages = new ArrayList<>();
            this.mContentIconGravity = 8388613;
            this.mContentActionIndex = -1;
            this.mCustomSizePreset = 0;
            this.mGravity = 80;
        }

        public WearableExtender(Notification notif) {
            this.mActions = new ArrayList<>();
            this.mFlags = 1;
            this.mPages = new ArrayList<>();
            this.mContentIconGravity = 8388613;
            this.mContentActionIndex = -1;
            this.mCustomSizePreset = 0;
            this.mGravity = 80;
            Bundle wearableBundle = notif.extras.getBundle(EXTRA_WEARABLE_EXTENSIONS);
            if (wearableBundle != null) {
                List<Action> actions = wearableBundle.getParcelableArrayList("actions");
                if (actions != null) {
                    this.mActions.addAll(actions);
                }
                this.mFlags = wearableBundle.getInt("flags", 1);
                this.mDisplayIntent = (PendingIntent) wearableBundle.getParcelable(KEY_DISPLAY_INTENT);
                Notification[] pages = (Notification[]) Notification.getParcelableArrayFromBundle(wearableBundle, KEY_PAGES, Notification.class);
                if (pages != null) {
                    Collections.addAll(this.mPages, pages);
                }
                this.mBackground = (Bitmap) wearableBundle.getParcelable("background");
                this.mContentIcon = wearableBundle.getInt(KEY_CONTENT_ICON);
                this.mContentIconGravity = wearableBundle.getInt(KEY_CONTENT_ICON_GRAVITY, 8388613);
                this.mContentActionIndex = wearableBundle.getInt(KEY_CONTENT_ACTION_INDEX, -1);
                this.mCustomSizePreset = wearableBundle.getInt(KEY_CUSTOM_SIZE_PRESET, 0);
                this.mCustomContentHeight = wearableBundle.getInt(KEY_CUSTOM_CONTENT_HEIGHT);
                this.mGravity = wearableBundle.getInt(KEY_GRAVITY, 80);
                this.mHintScreenTimeout = wearableBundle.getInt(KEY_HINT_SCREEN_TIMEOUT);
                this.mDismissalId = wearableBundle.getString(KEY_DISMISSAL_ID);
                this.mBridgeTag = wearableBundle.getString(KEY_BRIDGE_TAG);
            }
        }

        @Override // android.app.Notification.Extender
        public Builder extend(Builder builder) {
            Bundle wearableBundle = new Bundle();
            if (!this.mActions.isEmpty()) {
                wearableBundle.putParcelableArrayList("actions", this.mActions);
            }
            int i = this.mFlags;
            if (i != 1) {
                wearableBundle.putInt("flags", i);
            }
            PendingIntent pendingIntent = this.mDisplayIntent;
            if (pendingIntent != null) {
                wearableBundle.putParcelable(KEY_DISPLAY_INTENT, pendingIntent);
            }
            if (!this.mPages.isEmpty()) {
                ArrayList<Notification> arrayList = this.mPages;
                wearableBundle.putParcelableArray(KEY_PAGES, (Parcelable[]) arrayList.toArray(new Notification[arrayList.size()]));
            }
            Bitmap bitmap = this.mBackground;
            if (bitmap != null) {
                wearableBundle.putParcelable("background", bitmap);
            }
            int i2 = this.mContentIcon;
            if (i2 != 0) {
                wearableBundle.putInt(KEY_CONTENT_ICON, i2);
            }
            int i3 = this.mContentIconGravity;
            if (i3 != 8388613) {
                wearableBundle.putInt(KEY_CONTENT_ICON_GRAVITY, i3);
            }
            int i4 = this.mContentActionIndex;
            if (i4 != -1) {
                wearableBundle.putInt(KEY_CONTENT_ACTION_INDEX, i4);
            }
            int i5 = this.mCustomSizePreset;
            if (i5 != 0) {
                wearableBundle.putInt(KEY_CUSTOM_SIZE_PRESET, i5);
            }
            int i6 = this.mCustomContentHeight;
            if (i6 != 0) {
                wearableBundle.putInt(KEY_CUSTOM_CONTENT_HEIGHT, i6);
            }
            int i7 = this.mGravity;
            if (i7 != 80) {
                wearableBundle.putInt(KEY_GRAVITY, i7);
            }
            int i8 = this.mHintScreenTimeout;
            if (i8 != 0) {
                wearableBundle.putInt(KEY_HINT_SCREEN_TIMEOUT, i8);
            }
            String str = this.mDismissalId;
            if (str != null) {
                wearableBundle.putString(KEY_DISMISSAL_ID, str);
            }
            String str2 = this.mBridgeTag;
            if (str2 != null) {
                wearableBundle.putString(KEY_BRIDGE_TAG, str2);
            }
            builder.getExtras().putBundle(EXTRA_WEARABLE_EXTENSIONS, wearableBundle);
            return builder;
        }

        /* JADX INFO: renamed from: clone, reason: merged with bridge method [inline-methods] */
        public WearableExtender m12clone() {
            WearableExtender that = new WearableExtender();
            that.mActions = new ArrayList<>(this.mActions);
            that.mFlags = this.mFlags;
            that.mDisplayIntent = this.mDisplayIntent;
            that.mPages = new ArrayList<>(this.mPages);
            that.mBackground = this.mBackground;
            that.mContentIcon = this.mContentIcon;
            that.mContentIconGravity = this.mContentIconGravity;
            that.mContentActionIndex = this.mContentActionIndex;
            that.mCustomSizePreset = this.mCustomSizePreset;
            that.mCustomContentHeight = this.mCustomContentHeight;
            that.mGravity = this.mGravity;
            that.mHintScreenTimeout = this.mHintScreenTimeout;
            that.mDismissalId = this.mDismissalId;
            that.mBridgeTag = this.mBridgeTag;
            return that;
        }

        public WearableExtender addAction(Action action) {
            this.mActions.add(action);
            return this;
        }

        public WearableExtender addActions(List<Action> actions) {
            this.mActions.addAll(actions);
            return this;
        }

        public WearableExtender clearActions() {
            this.mActions.clear();
            return this;
        }

        public List<Action> getActions() {
            return this.mActions;
        }

        @Deprecated
        public WearableExtender setDisplayIntent(PendingIntent intent) {
            this.mDisplayIntent = intent;
            return this;
        }

        @Deprecated
        public PendingIntent getDisplayIntent() {
            return this.mDisplayIntent;
        }

        @Deprecated
        public WearableExtender addPage(Notification page) {
            this.mPages.add(page);
            return this;
        }

        @Deprecated
        public WearableExtender addPages(List<Notification> pages) {
            this.mPages.addAll(pages);
            return this;
        }

        @Deprecated
        public WearableExtender clearPages() {
            this.mPages.clear();
            return this;
        }

        @Deprecated
        public List<Notification> getPages() {
            return this.mPages;
        }

        @Deprecated
        public WearableExtender setBackground(Bitmap background) {
            this.mBackground = background;
            return this;
        }

        @Deprecated
        public Bitmap getBackground() {
            return this.mBackground;
        }

        @Deprecated
        public WearableExtender setContentIcon(int icon) {
            this.mContentIcon = icon;
            return this;
        }

        @Deprecated
        public int getContentIcon() {
            return this.mContentIcon;
        }

        @Deprecated
        public WearableExtender setContentIconGravity(int contentIconGravity) {
            this.mContentIconGravity = contentIconGravity;
            return this;
        }

        @Deprecated
        public int getContentIconGravity() {
            return this.mContentIconGravity;
        }

        public WearableExtender setContentAction(int actionIndex) {
            this.mContentActionIndex = actionIndex;
            return this;
        }

        public int getContentAction() {
            return this.mContentActionIndex;
        }

        @Deprecated
        public WearableExtender setGravity(int gravity) {
            this.mGravity = gravity;
            return this;
        }

        @Deprecated
        public int getGravity() {
            return this.mGravity;
        }

        @Deprecated
        public WearableExtender setCustomSizePreset(int sizePreset) {
            this.mCustomSizePreset = sizePreset;
            return this;
        }

        @Deprecated
        public int getCustomSizePreset() {
            return this.mCustomSizePreset;
        }

        @Deprecated
        public WearableExtender setCustomContentHeight(int height) {
            this.mCustomContentHeight = height;
            return this;
        }

        @Deprecated
        public int getCustomContentHeight() {
            return this.mCustomContentHeight;
        }

        public WearableExtender setStartScrollBottom(boolean startScrollBottom) {
            setFlag(8, startScrollBottom);
            return this;
        }

        public boolean getStartScrollBottom() {
            return (this.mFlags & 8) != 0;
        }

        public WearableExtender setContentIntentAvailableOffline(boolean contentIntentAvailableOffline) {
            setFlag(1, contentIntentAvailableOffline);
            return this;
        }

        public boolean getContentIntentAvailableOffline() {
            return (this.mFlags & 1) != 0;
        }

        @Deprecated
        public WearableExtender setHintHideIcon(boolean hintHideIcon) {
            setFlag(2, hintHideIcon);
            return this;
        }

        @Deprecated
        public boolean getHintHideIcon() {
            return (this.mFlags & 2) != 0;
        }

        @Deprecated
        public WearableExtender setHintShowBackgroundOnly(boolean hintShowBackgroundOnly) {
            setFlag(4, hintShowBackgroundOnly);
            return this;
        }

        @Deprecated
        public boolean getHintShowBackgroundOnly() {
            return (this.mFlags & 4) != 0;
        }

        @Deprecated
        public WearableExtender setHintAvoidBackgroundClipping(boolean hintAvoidBackgroundClipping) {
            setFlag(16, hintAvoidBackgroundClipping);
            return this;
        }

        @Deprecated
        public boolean getHintAvoidBackgroundClipping() {
            return (this.mFlags & 16) != 0;
        }

        @Deprecated
        public WearableExtender setHintScreenTimeout(int timeout) {
            this.mHintScreenTimeout = timeout;
            return this;
        }

        @Deprecated
        public int getHintScreenTimeout() {
            return this.mHintScreenTimeout;
        }

        @Deprecated
        public WearableExtender setHintAmbientBigPicture(boolean hintAmbientBigPicture) {
            setFlag(32, hintAmbientBigPicture);
            return this;
        }

        @Deprecated
        public boolean getHintAmbientBigPicture() {
            return (this.mFlags & 32) != 0;
        }

        public WearableExtender setHintContentIntentLaunchesActivity(boolean hintContentIntentLaunchesActivity) {
            setFlag(64, hintContentIntentLaunchesActivity);
            return this;
        }

        public boolean getHintContentIntentLaunchesActivity() {
            return (this.mFlags & 64) != 0;
        }

        public WearableExtender setDismissalId(String dismissalId) {
            this.mDismissalId = dismissalId;
            return this;
        }

        public String getDismissalId() {
            return this.mDismissalId;
        }

        public WearableExtender setBridgeTag(String bridgeTag) {
            this.mBridgeTag = bridgeTag;
            return this;
        }

        public String getBridgeTag() {
            return this.mBridgeTag;
        }

        private void setFlag(int mask, boolean value) {
            if (value) {
                this.mFlags |= mask;
            } else {
                this.mFlags &= ~mask;
            }
        }
    }

    public static final class CarExtender implements Extender {
        private static final String EXTRA_CAR_EXTENDER = "android.car.EXTENSIONS";
        private static final String EXTRA_COLOR = "app_color";
        private static final String EXTRA_CONVERSATION = "car_conversation";
        private static final String EXTRA_LARGE_ICON = "large_icon";
        private static final String TAG = "CarExtender";
        private int mColor;
        private Bitmap mLargeIcon;
        private UnreadConversation mUnreadConversation;

        public CarExtender() {
            this.mColor = 0;
        }

        public CarExtender(Notification notif) {
            this.mColor = 0;
            Bundle carBundle = notif.extras == null ? null : notif.extras.getBundle(EXTRA_CAR_EXTENDER);
            if (carBundle != null) {
                this.mLargeIcon = (Bitmap) carBundle.getParcelable(EXTRA_LARGE_ICON);
                this.mColor = carBundle.getInt(EXTRA_COLOR, 0);
                Bundle b = carBundle.getBundle(EXTRA_CONVERSATION);
                this.mUnreadConversation = UnreadConversation.getUnreadConversationFromBundle(b);
            }
        }

        @Override // android.app.Notification.Extender
        public Builder extend(Builder builder) {
            Bundle carExtensions = new Bundle();
            Bitmap bitmap = this.mLargeIcon;
            if (bitmap != null) {
                carExtensions.putParcelable(EXTRA_LARGE_ICON, bitmap);
            }
            int i = this.mColor;
            if (i != 0) {
                carExtensions.putInt(EXTRA_COLOR, i);
            }
            UnreadConversation unreadConversation = this.mUnreadConversation;
            if (unreadConversation != null) {
                Bundle b = unreadConversation.getBundleForUnreadConversation();
                carExtensions.putBundle(EXTRA_CONVERSATION, b);
            }
            Bundle b2 = builder.getExtras();
            b2.putBundle(EXTRA_CAR_EXTENDER, carExtensions);
            return builder;
        }

        public CarExtender setColor(int color) {
            this.mColor = color;
            return this;
        }

        public int getColor() {
            return this.mColor;
        }

        public CarExtender setLargeIcon(Bitmap largeIcon) {
            this.mLargeIcon = largeIcon;
            return this;
        }

        public Bitmap getLargeIcon() {
            return this.mLargeIcon;
        }

        public CarExtender setUnreadConversation(UnreadConversation unreadConversation) {
            this.mUnreadConversation = unreadConversation;
            return this;
        }

        public UnreadConversation getUnreadConversation() {
            return this.mUnreadConversation;
        }

        public static class UnreadConversation {
            private static final String KEY_AUTHOR = "author";
            private static final String KEY_MESSAGES = "messages";
            private static final String KEY_ON_READ = "on_read";
            private static final String KEY_ON_REPLY = "on_reply";
            private static final String KEY_PARTICIPANTS = "participants";
            private static final String KEY_REMOTE_INPUT = "remote_input";
            private static final String KEY_TEXT = "text";
            private static final String KEY_TIMESTAMP = "timestamp";
            private final long mLatestTimestamp;
            private final String[] mMessages;
            private final String[] mParticipants;
            private final PendingIntent mReadPendingIntent;
            private final RemoteInput mRemoteInput;
            private final PendingIntent mReplyPendingIntent;

            UnreadConversation(String[] messages, RemoteInput remoteInput, PendingIntent replyPendingIntent, PendingIntent readPendingIntent, String[] participants, long latestTimestamp) {
                this.mMessages = messages;
                this.mRemoteInput = remoteInput;
                this.mReadPendingIntent = readPendingIntent;
                this.mReplyPendingIntent = replyPendingIntent;
                this.mParticipants = participants;
                this.mLatestTimestamp = latestTimestamp;
            }

            public String[] getMessages() {
                return this.mMessages;
            }

            public RemoteInput getRemoteInput() {
                return this.mRemoteInput;
            }

            public PendingIntent getReplyPendingIntent() {
                return this.mReplyPendingIntent;
            }

            public PendingIntent getReadPendingIntent() {
                return this.mReadPendingIntent;
            }

            public String[] getParticipants() {
                return this.mParticipants;
            }

            public String getParticipant() {
                String[] strArr = this.mParticipants;
                if (strArr.length > 0) {
                    return strArr[0];
                }
                return null;
            }

            public long getLatestTimestamp() {
                return this.mLatestTimestamp;
            }

            Bundle getBundleForUnreadConversation() {
                Bundle b = new Bundle();
                String author = null;
                String[] strArr = this.mParticipants;
                if (strArr != null && strArr.length > 1) {
                    author = strArr[0];
                }
                Parcelable[] messages = new Parcelable[this.mMessages.length];
                for (int i = 0; i < messages.length; i++) {
                    Bundle m = new Bundle();
                    m.putString("text", this.mMessages[i]);
                    m.putString("author", author);
                    messages[i] = m;
                }
                b.putParcelableArray(KEY_MESSAGES, messages);
                Parcelable parcelable = this.mRemoteInput;
                if (parcelable != null) {
                    b.putParcelable(KEY_REMOTE_INPUT, parcelable);
                }
                b.putParcelable(KEY_ON_REPLY, this.mReplyPendingIntent);
                b.putParcelable(KEY_ON_READ, this.mReadPendingIntent);
                b.putStringArray(KEY_PARTICIPANTS, this.mParticipants);
                b.putLong("timestamp", this.mLatestTimestamp);
                return b;
            }

            static UnreadConversation getUnreadConversationFromBundle(Bundle b) {
                if (b == null) {
                    return null;
                }
                Parcelable[] parcelableMessages = b.getParcelableArray(KEY_MESSAGES);
                String[] messages = null;
                if (parcelableMessages != null) {
                    String[] tmp = new String[parcelableMessages.length];
                    boolean success = true;
                    int i = 0;
                    while (true) {
                        if (i >= tmp.length) {
                            break;
                        }
                        if (!(parcelableMessages[i] instanceof Bundle)) {
                            success = false;
                            break;
                        }
                        tmp[i] = ((Bundle) parcelableMessages[i]).getString("text");
                        if (tmp[i] != null) {
                            i++;
                        } else {
                            success = false;
                            break;
                        }
                    }
                    if (!success) {
                        return null;
                    }
                    messages = tmp;
                }
                PendingIntent onRead = (PendingIntent) b.getParcelable(KEY_ON_READ);
                PendingIntent onReply = (PendingIntent) b.getParcelable(KEY_ON_REPLY);
                RemoteInput remoteInput = (RemoteInput) b.getParcelable(KEY_REMOTE_INPUT);
                String[] participants = b.getStringArray(KEY_PARTICIPANTS);
                if (participants == null || participants.length != 1) {
                    return null;
                }
                return new UnreadConversation(messages, remoteInput, onReply, onRead, participants, b.getLong("timestamp"));
            }
        }

        public static class Builder {
            private long mLatestTimestamp;
            private final List<String> mMessages = new ArrayList();
            private final String mParticipant;
            private PendingIntent mReadPendingIntent;
            private RemoteInput mRemoteInput;
            private PendingIntent mReplyPendingIntent;

            public Builder(String name) {
                this.mParticipant = name;
            }

            public Builder addMessage(String message) {
                this.mMessages.add(message);
                return this;
            }

            public Builder setReplyAction(PendingIntent pendingIntent, RemoteInput remoteInput) {
                this.mRemoteInput = remoteInput;
                this.mReplyPendingIntent = pendingIntent;
                return this;
            }

            public Builder setReadPendingIntent(PendingIntent pendingIntent) {
                this.mReadPendingIntent = pendingIntent;
                return this;
            }

            public Builder setLatestTimestamp(long timestamp) {
                this.mLatestTimestamp = timestamp;
                return this;
            }

            public UnreadConversation build() {
                List<String> list = this.mMessages;
                String[] messages = (String[]) list.toArray(new String[list.size()]);
                String[] participants = {this.mParticipant};
                return new UnreadConversation(messages, this.mRemoteInput, this.mReplyPendingIntent, this.mReadPendingIntent, participants, this.mLatestTimestamp);
            }
        }
    }

    @SystemApi
    public static final class TvExtender implements Extender {
        private static final String EXTRA_CHANNEL_ID = "channel_id";
        private static final String EXTRA_CONTENT_INTENT = "content_intent";
        private static final String EXTRA_DELETE_INTENT = "delete_intent";
        private static final String EXTRA_FLAGS = "flags";
        private static final String EXTRA_SUPPRESS_SHOW_OVER_APPS = "suppressShowOverApps";
        private static final String EXTRA_TV_EXTENDER = "android.tv.EXTENSIONS";
        private static final int FLAG_AVAILABLE_ON_TV = 1;
        private static final String TAG = "TvExtender";
        private String mChannelId;
        private PendingIntent mContentIntent;
        private PendingIntent mDeleteIntent;
        private int mFlags;
        private boolean mSuppressShowOverApps;

        public TvExtender() {
            this.mFlags = 1;
        }

        public TvExtender(Notification notif) {
            Bundle bundle = notif.extras == null ? null : notif.extras.getBundle(EXTRA_TV_EXTENDER);
            if (bundle != null) {
                this.mFlags = bundle.getInt("flags");
                this.mChannelId = bundle.getString("channel_id");
                this.mSuppressShowOverApps = bundle.getBoolean(EXTRA_SUPPRESS_SHOW_OVER_APPS);
                this.mContentIntent = (PendingIntent) bundle.getParcelable(EXTRA_CONTENT_INTENT);
                this.mDeleteIntent = (PendingIntent) bundle.getParcelable(EXTRA_DELETE_INTENT);
            }
        }

        @Override // android.app.Notification.Extender
        public Builder extend(Builder builder) {
            Bundle bundle = new Bundle();
            bundle.putInt("flags", this.mFlags);
            bundle.putString("channel_id", this.mChannelId);
            bundle.putBoolean(EXTRA_SUPPRESS_SHOW_OVER_APPS, this.mSuppressShowOverApps);
            PendingIntent pendingIntent = this.mContentIntent;
            if (pendingIntent != null) {
                bundle.putParcelable(EXTRA_CONTENT_INTENT, pendingIntent);
            }
            PendingIntent pendingIntent2 = this.mDeleteIntent;
            if (pendingIntent2 != null) {
                bundle.putParcelable(EXTRA_DELETE_INTENT, pendingIntent2);
            }
            builder.getExtras().putBundle(EXTRA_TV_EXTENDER, bundle);
            return builder;
        }

        public boolean isAvailableOnTv() {
            return (this.mFlags & 1) != 0;
        }

        public TvExtender setChannel(String channelId) {
            this.mChannelId = channelId;
            return this;
        }

        public TvExtender setChannelId(String channelId) {
            this.mChannelId = channelId;
            return this;
        }

        @Deprecated
        public String getChannel() {
            return this.mChannelId;
        }

        public String getChannelId() {
            return this.mChannelId;
        }

        public TvExtender setContentIntent(PendingIntent intent) {
            this.mContentIntent = intent;
            return this;
        }

        public PendingIntent getContentIntent() {
            return this.mContentIntent;
        }

        public TvExtender setDeleteIntent(PendingIntent intent) {
            this.mDeleteIntent = intent;
            return this;
        }

        public PendingIntent getDeleteIntent() {
            return this.mDeleteIntent;
        }

        public TvExtender setSuppressShowOverApps(boolean suppress) {
            this.mSuppressShowOverApps = suppress;
            return this;
        }

        public boolean getSuppressShowOverApps() {
            return this.mSuppressShowOverApps;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static <T extends Parcelable> T[] getParcelableArrayFromBundle(Bundle bundle, String str, Class<T> cls) {
        T[] tArr = (T[]) bundle.getParcelableArray(str);
        if (Array.newInstance((Class<?>) cls, 0).getClass().isInstance(tArr) || tArr == null) {
            return tArr;
        }
        T[] tArr2 = (T[]) ((Parcelable[]) Array.newInstance((Class<?>) cls, tArr.length));
        for (int i = 0; i < tArr.length; i++) {
            tArr2[i] = tArr[i];
        }
        bundle.putParcelableArray(str, tArr2);
        return tArr2;
    }

    private static class BuilderRemoteViews extends RemoteViews {
        public BuilderRemoteViews(Parcel parcel) {
            super(parcel);
        }

        public BuilderRemoteViews(ApplicationInfo appInfo, int layoutId) {
            super(appInfo, layoutId);
        }

        @Override // android.widget.RemoteViews
        /* JADX INFO: renamed from: clone */
        public BuilderRemoteViews mo11clone() {
            Parcel p = Parcel.obtain();
            writeToParcel(p, 0);
            p.setDataPosition(0);
            BuilderRemoteViews brv = new BuilderRemoteViews(p);
            p.recycle();
            return brv;
        }

        @Override // android.widget.RemoteViews
        protected boolean shouldUseStaticFilter() {
            return true;
        }
    }

    private static class TemplateBindResult {
        int mIconMarginEnd;
        boolean mRightIconContainerVisible;

        private TemplateBindResult() {
        }

        public int getIconMarginEnd() {
            return this.mIconMarginEnd;
        }

        public boolean isRightIconContainerVisible() {
            return this.mRightIconContainerVisible;
        }

        public void setIconMarginEnd(int iconMarginEnd) {
            this.mIconMarginEnd = iconMarginEnd;
        }

        public void setRightIconContainerVisible(boolean iconContainerVisible) {
            this.mRightIconContainerVisible = iconContainerVisible;
        }
    }

    private static class StandardTemplateParams {
        boolean allowColorization;
        boolean forceDefaultColor;
        boolean hasProgress;
        CharSequence headerTextSecondary;
        boolean hideLargeIcon;
        boolean hideReplyIcon;
        int maxRemoteInputHistory;
        CharSequence summaryText;
        CharSequence text;
        CharSequence title;

        private StandardTemplateParams() {
            this.hasProgress = true;
            this.maxRemoteInputHistory = 3;
            this.allowColorization = true;
            this.forceDefaultColor = false;
        }

        final StandardTemplateParams reset() {
            this.hasProgress = true;
            this.title = null;
            this.text = null;
            this.summaryText = null;
            this.headerTextSecondary = null;
            this.maxRemoteInputHistory = 3;
            this.allowColorization = true;
            this.forceDefaultColor = false;
            return this;
        }

        final StandardTemplateParams hasProgress(boolean hasProgress) {
            this.hasProgress = hasProgress;
            return this;
        }

        final StandardTemplateParams title(CharSequence title) {
            this.title = title;
            return this;
        }

        final StandardTemplateParams text(CharSequence text) {
            this.text = text;
            return this;
        }

        final StandardTemplateParams summaryText(CharSequence text) {
            this.summaryText = text;
            return this;
        }

        final StandardTemplateParams headerTextSecondary(CharSequence text) {
            this.headerTextSecondary = text;
            return this;
        }

        final StandardTemplateParams hideLargeIcon(boolean hideLargeIcon) {
            this.hideLargeIcon = hideLargeIcon;
            return this;
        }

        final StandardTemplateParams hideReplyIcon(boolean hideReplyIcon) {
            this.hideReplyIcon = hideReplyIcon;
            return this;
        }

        final StandardTemplateParams disallowColorization() {
            this.allowColorization = false;
            return this;
        }

        final StandardTemplateParams forceDefaultColor() {
            this.forceDefaultColor = true;
            return this;
        }

        final StandardTemplateParams fillTextsFrom(Builder b) {
            Bundle extras = b.mN.extras;
            this.title = b.processLegacyText(extras.getCharSequence(Notification.EXTRA_TITLE));
            this.text = b.processLegacyText(extras.getCharSequence(Notification.EXTRA_TEXT));
            this.summaryText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            return this;
        }

        public StandardTemplateParams setMaxRemoteInputHistory(int maxRemoteInputHistory) {
            this.maxRemoteInputHistory = maxRemoteInputHistory;
            return this;
        }
    }

    public void setChannel(String channelId) {
        this.mChannelId = channelId;
    }

    public boolean isCustomNotification() {
        boolean hasCustomContentView = (this.contentView == null || this.extras.getInt(Builder.EXTRA_REBUILD_CONTENT_VIEW_ACTION_COUNT, -1) == this.contentView.getSequenceNumber()) ? false : true;
        boolean hasBigCustomContenView = (this.bigContentView == null || this.extras.getInt(Builder.EXTRA_REBUILD_BIG_CONTENT_VIEW_ACTION_COUNT, -1) == this.bigContentView.getSequenceNumber()) ? false : true;
        return hasCustomContentView || hasBigCustomContenView;
    }

    public void setGroup(String group) {
        this.mGroupKey = group;
    }
}
