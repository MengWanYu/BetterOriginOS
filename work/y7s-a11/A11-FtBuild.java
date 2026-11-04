package android.os;

import android.media.MediaMetrics;
import android.telecom.Logging.Session;
import android.text.TextUtils;
import vivo.util.VSlog;

/* JADX INFO: loaded from: classes2.dex */
public class FtBuild {
    private static final String TAG = "FtBuild";
    private static final String UNKNOWN = "unknown";
    public static final int VIVO_TIER1 = 1;
    public static final int VIVO_TIER2 = 2;
    public static final int VIVO_TIER_NONE = 0;
    private static Float mRomVer = null;
    private static final String mDisplayId = SystemProperties.get("ro.vivo.os.build.display.id", "unknown");
    private static final String mOsName = SystemProperties.get("ro.vivo.os.name", "unknown");
    private static final String mOsVersion = SystemProperties.get("ro.vivo.os.version", "unknown");
    private static final String mProductName = SystemProperties.get("ro.vivo.product.model", "unknown");
    private static final String mProductVersion = SystemProperties.get("ro.vivo.product.version", "unknown");
    private static final String mNetEntry = SystemProperties.get("ro.vivo.net.entry", "unknown");
    private static final String mOverSeas = SystemProperties.get("ro.vivo.product.overseas", "unknown");
    private static final String mOpEntry = SystemProperties.get("ro.vivo.op.entry", "unknown");
    private static final String mCustiomizeBbk = SystemProperties.get("ro.product.customize.bbk", "unknown");
    private static final String PLATFORM_INFO = SystemProperties.get("ro.vivo.product.solution", "unknown");
    private static final String mReleaseBrand = SystemProperties.get("ro.vivo.product.release.brand", "unknown");
    private static final String mReleaseModel = SystemProperties.get("ro.vivo.product.release.model", "unknown");
    private static final String mReleaseName = SystemProperties.get("ro.vivo.product.release.name", "unknown");
    private static final String mModel = SystemProperties.get("ro.product.model", "unknown");
    private static final String mSeries = SystemProperties.get("ro.vivo.product.series", "VIVO");
    private static final String firstOsVersion = SystemProperties.get("ro.vivo.fist.os.version", "unknown");
    public static final String PROP_ROM_VERSION = SystemProperties.get("ro.vivo.rom.version", "unknown");
    private static final String ROM_AB_VERSION = SystemProperties.get("ro.vivo.rom.abversion", "unknown");
    private static final String VIVO_TIER_LEVEL = SystemProperties.get("ro.system.vivo.tierlevel", "0");
    public static final boolean IS_MULTIWINDOWVALID = SystemProperties.get("persist.vivo.multiwindow", "false").contains("true");
    public static final boolean IS_VIVO_FREEFORM_VALID = SystemProperties.get("persist.vivo.freeform", "false").contains("true");

    private static float getFuntouchSDKVersion() {
        return Float.valueOf(21.1f).floatValue();
    }

    public static String getProductSeries() {
        return mSeries;
    }

    public static String getFuntouchOsVersion() {
        String funtouchOsVersion;
        if (mDisplayId.equals("unknown")) {
            funtouchOsVersion = mOsName + " " + mOsVersion;
        } else {
            funtouchOsVersion = mDisplayId;
        }
        printfInfo("getFuntouchOsVersion:" + funtouchOsVersion);
        return funtouchOsVersion;
    }

    public static float getRomVersion() {
        Float f = mRomVer;
        if (f != null) {
            return f.floatValue();
        }
        String romVer = mOsVersion;
        if (romVer != null && !romVer.equals("unknown")) {
            try {
                mRomVer = Float.valueOf(romVer);
            } catch (NumberFormatException e) {
                printfInfo("NumberFormatException " + e);
            }
        }
        if (mRomVer == null) {
            try {
                mRomVer = Float.valueOf(1.0f);
            } catch (NumberFormatException e2) {
                printfInfo("NumberFormatException default " + e2);
            }
        }
        printfInfo("getRomVersion mRomVer=" + mRomVer);
        return mRomVer.floatValue();
    }

    public static String getProductVersion() {
        String version = mProductVersion;
        if (isNetEntry()) {
            return SystemProperties.get("ro.build.netaccess.version", "unknown");
        }
        if (isCmccOpEntry()) {
            return SystemProperties.get("ro.vivo.op.entry.version", "unknown");
        }
        if (mCustiomizeBbk.equals("CN-YD")) {
            if ("PD1421".equals(getProductName())) {
                return version.replaceFirst("PD1421D", "PD1421L");
            }
            return version.replaceFirst(Session.SESSION_SEPARATION_CHAR_CHILD, "-YD_");
        }
        if (mCustiomizeBbk.equals("CN-DX")) {
            return version.replaceFirst(Session.SESSION_SEPARATION_CHAR_CHILD, "-DX_");
        }
        return version;
    }

    public static String getProductName() {
        return mProductName;
    }

    public static boolean isNetEntry() {
        return MediaMetrics.Value.YES.equals(mNetEntry);
    }

    public static boolean isCmccOpEntry() {
        if (mOpEntry.contains("CMCC_RW") || mOpEntry.equals("CMCC")) {
            return true;
        }
        return false;
    }

    public static boolean isOverSeas() {
        return MediaMetrics.Value.YES.equals(mOverSeas);
    }

    public static boolean isMTKPlatform() {
        return PLATFORM_INFO.equals("MTK");
    }

    public static boolean isQCOMPlatform() {
        return PLATFORM_INFO.equals("QCOM");
    }

    public static boolean isSamsungPlatform() {
        return PLATFORM_INFO.equals("SAMSUNG");
    }

    private static boolean isFlagshipMachine() {
        return !mReleaseName.equals(mReleaseModel);
    }

    public static String getProductReleaseName() {
        if (isFlagshipMachine()) {
            if (mReleaseBrand.equals("unknown")) {
                return mReleaseName;
            }
            return mReleaseBrand + " " + mReleaseName;
        }
        return mModel;
    }

    public static String getProductReleaseModel() {
        if (mReleaseBrand.equals("unknown")) {
            return mReleaseModel;
        }
        return mReleaseBrand + " " + mReleaseModel;
    }

    public static String getRomABVersion() {
        String abVersion = SystemProperties.get(ROM_AB_VERSION, BatteryStats.UID_TIMES_TYPE_ALL);
        return (abVersion.equals(BatteryStats.UID_TIMES_TYPE_ALL) || abVersion.equals("B")) ? abVersion : BatteryStats.UID_TIMES_TYPE_ALL;
    }

    private static void printfInfo(String msg) {
        VSlog.i(TAG, msg);
    }

    public static String getScreenProportion() {
        String screenProportion = SystemProperties.get("ro.vivo.lcm.xhd", "FHD_19x5_9");
        int i = screenProportion.indexOf(95);
        return screenProportion.substring(i + 1);
    }

    public static int getTierLevel() {
        if (TextUtils.isEmpty(VIVO_TIER_LEVEL)) {
            return 0;
        }
        try {
            return Integer.parseInt(VIVO_TIER_LEVEL);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String getOsName() {
        return mOsName;
    }

    public static String getOsVersion() {
        return mOsVersion;
    }

    public static String getFirstOsVersion() {
        return firstOsVersion;
    }
}
