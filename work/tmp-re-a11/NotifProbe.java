import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 离线探针：把模块编译产物里的 A11NotifSpec（反射调用）逐项算出来，
 * 由 work/a11_notif_selftest.py 拿**独立参考实现**逐值比对。
 *
 * 为什么走反射：本探针不属于模块包，直接 import 会和 module/build/classes 的包路径纠缠；
 * 反射反而能保证"被测的就是 APK 里那份字节码"。
 */
public class NotifProbe {

    static Class<?> spec;
    static Class<?> palette;

    public static void main(String[] args) throws Exception {
        spec = Class.forName("com.betteroriginos.feature.media.A11NotifSpec");
        try {
            palette = Class.forName("com.betteroriginos.feature.media.A11Palette");
        } catch (Throwable t) {
            palette = null;   // 三方比对少一路，自检里会显式报出来
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        StringBuilder out = new StringBuilder();
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+");
            out.append(handle(p)).append('\n');
        }
        System.out.print(out);
    }

    static String f(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    static String handle(String[] p) throws Exception {
        String cmd = p[0];
        if ("luminance".equals(cmd)) {
            return f((Double) call("luminance", int.class, hex(p[1])));
        }
        if ("contrast".equals(cmd)) {
            return f((Double) call("contrast", int.class, int.class, hex(p[1]), hex(p[2])));
        }
        if ("paletteContrast".equals(cmd)) {
            Method m = palette.getMethod("contrast", int.class, int.class);
            return f((Double) m.invoke(null, hex(p[1]), hex(p[2])));
        }
        if ("paletteLuminance".equals(cmd)) {
            Method m = palette.getMethod("luminance", int.class);
            return f((Double) m.invoke(null, hex(p[1])));
        }
        if ("useDark".equals(cmd)) {
            return bool(call("shouldUseDark", int.class, boolean.class,
                    hex(p[1]), "1".equals(p[2])));
        }
        if ("primary".equals(cmd)) {
            return hexStr((Integer) call("primaryTextColor", int.class, boolean.class,
                    hex(p[1]), "1".equals(p[2])));
        }
        if ("secondary".equals(cmd)) {
            return hexStr((Integer) call("secondaryTextColor", int.class, boolean.class,
                    hex(p[1]), "1".equals(p[2])));
        }
        if ("cardBg".equals(cmd)) {
            return hexStr((Integer) call("cardBackground", boolean.class, int.class, boolean.class,
                    "1".equals(p[1]), hex(p[2]), "1".equals(p[3])));
        }
        if ("alpha45".equals(cmd)) {
            return hexStr((Integer) call("findAlphaToMeetContrast", int.class, int.class,
                    double.class, hex(p[1]), hex(p[2]), 4.5d));
        }
        if ("tint".equals(cmd)) {
            return hexStr((Integer) call("actionIconTint", boolean.class, int.class, int.class,
                    "1".equals(p[1]), hex(p[2]), hex(p[3])));
        }
        if ("ripple".equals(cmd)) {
            return hexStr((Integer) call("rippleColor", int.class, int.class,
                    hex(p[1]), Integer.parseInt(p[2])));
        }
        if ("relief".equals(cmd)) {
            float[] m = (float[]) call("reliefMatrix", int.class, hex(p[1]));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < m.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(String.format(Locale.US, "%.4f", m[i]));
            }
            return sb.toString();
        }
        if ("crop".equals(cmd)) {
            return join((int[]) call("squareCrop", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("bgRegion".equals(cmd)) {
            return join((int[]) call("backgroundRegion", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("fgRegion".equals(cmd)) {
            return join((int[]) call("foregroundRegion", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("bdScale".equals(cmd)) {
            return String.valueOf(call("backdropScalePx", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("push".equals(cmd)) {
            return String.valueOf(call("artPushInPx", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("mge".equals(cmd)) {
            return String.valueOf(call("mainColumnMarginEndPx", int.class, int.class,
                    Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        if ("artSide".equals(cmd)) {
            return String.valueOf(call("artSidePx", int.class, Integer.parseInt(p[1])));
        }
        if ("seekColors".equals(cmd)) {
            int base = (int) Long.parseLong(p[1], 16);
            return Integer.toHexString((Integer) call("seekThumbColor", int.class, base)) + ','
                    + Integer.toHexString((Integer) call("seekProgressColor", int.class, base)) + ','
                    + Integer.toHexString((Integer) call("seekTrackColor", int.class, base));
        }
        if ("consts".equals(cmd)) {
            return consts();
        }
        return "ERR unknown " + cmd;
    }

    static String consts() throws Exception {
        StringBuilder sb = new StringBuilder();
        String[] names = {"ART_RADIUS_DP",
                "DEVICE_BUTTON_W_DP", "DEVICE_BUTTON_H_DP", "DEVICE_BUTTON_PADDING_TB_DP",
                "DEVICE_BUTTON_PADDING_LR_DP", "DEVICE_BUTTON_MARGIN_DP",
                "AOSP_BUTTON_DP", "AOSP_BUTTON_PADDING_DP", "AOSP_BUTTON_MARGIN_END_DP",
                "DEVICE_ACTIONS_MARGIN_START_DP", "DEVICE_ACTIONS_MARGIN_END_DP",
                "DEVICE_COLUMN_MARGIN_TOP_DP", "DEVICE_COLUMN_MARGIN_SIDE_DP",
                "AOSP_COLUMN_MARGIN_TOP_DP", "AOSP_COLUMN_MARGIN_SIDE_DP",
                "MIN_CONTENT_HEIGHT_DP", "AOSP_MIN_CONTENT_HEIGHT_DP",
                "HEADER_HEIGHT_DP", "HEADER_ICON_DP", "AOSP_HEADER_ICON_DP",
                "SEEK_TRACK_DP", "SEEK_PADDING_TB_DP", "SEEK_MARGIN_TOP_DP",
                "SEEK_MARGIN_BOTTOM_DP", "SEEK_TIME_MARGIN_SIDE_DP",
                "SEEK_TIME_ROW_MARGIN_BOTTOM_DP", "SEEK_THUMB_ALPHA", "SEEK_PROGRESS_ALPHA",
                "SEEK_TRACK_ALPHA", "ACTION_SEEK_TO", "PROGRESS_UPDATE_INTERVAL_MS",
                "PLAYER_SEEK_TIME_SP", "PLAYER_SEEK_PADDING_TB_DP",
                "PLAYER_SEEK_PROGRESS_ALPHA", "PLAYER_SEEK_TRACK_ALPHA",
                "SEEK_TIME_SHADOW_RADIUS", "SEEK_TIME_SHADOW_DX", "SEEK_TIME_SHADOW_DY",
                "ART_FADE_MS",
                "RELIEF_LAYER_ALPHA", "GRAD_HALF_WHITE", "GRAD_TRANSPARENT", "GRAD_BLACK",
                "BACKDROP_BLUR_RADIUS", "BACKDROP_TINT_ALPHA", "PRIMARY_ON_LIGHT",
                "PRIMARY_ON_DARK", "SECONDARY_ON_LIGHT", "SECONDARY_ON_DARK",
                "CARD_BG_LIGHT", "CARD_BG_DARK", "FALLBACK_SWATCH_RGB",
                "FALLBACK_SWATCH_POPULATION", "POPULATION_FRACTION_FOR_WHITE_OR_BLACK",
                "ROW_MIN_HEIGHT_MEDIA_DP", "CONTENT_MARGIN_DP",
                "TITLE_SP", "ARTIST_SP", "AOSP_ARTIST_DP", "SEEK_TIME_DP", "HEADER_TEXT_SP",
                "ART_SCALE_TYPE", "DEVICE_BUTTON_HAS_RIPPLE", "LUMA_R", "LUMA_G", "LUMA_B"};
        for (String n : names) {
            sb.append(n).append('=').append(spec.getField(n).get(null)).append(' ');
        }
        float[] first = (float[]) spec.getField("GRAD_STOPS_FIRST").get(null);
        float[] second = (float[]) spec.getField("GRAD_STOPS_SECOND").get(null);
        sb.append("STOPS1=");
        for (float v : first) {
            sb.append(v).append(',');
        }
        sb.append(" STOPS2=");
        for (float v : second) {
            sb.append(v).append(',');
        }
        return sb.toString().trim();
    }

    static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(a[i]);
        }
        return sb.toString();
    }

    static String bool(Object b) {
        return Boolean.TRUE.equals(b) ? "1" : "0";
    }

    /** 反射调用：类型与实参一一对应，避免可变参数带来的重载歧义。 */
    static Object call(String name, Class<?> t1, Object a1) throws Exception {
        return spec.getMethod(name, t1).invoke(null, a1);
    }

    static Object call(String name, Class<?> t1, Class<?> t2, Object a1, Object a2)
            throws Exception {
        return spec.getMethod(name, t1, t2).invoke(null, a1, a2);
    }

    static Object call(String name, Class<?> t1, Class<?> t2, Class<?> t3,
                       Object a1, Object a2, Object a3) throws Exception {
        return spec.getMethod(name, t1, t2, t3).invoke(null, a1, a2, a3);
    }

    static int hex(String s) {
        String v = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
        return (int) Long.parseLong(v, 16);
    }

    static String hexStr(int c) {
        return String.format(Locale.US, "0x%08X", c);
    }
}
