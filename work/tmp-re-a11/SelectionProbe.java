import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 反射调用模块编译产物里的 A11 取色**选择**逻辑（Palette 的 target 打分、findBackgroundSwatch、
 * selectForegroundColor），把每个族选中的 swatch 打印出来，交给
 * work/a11_selection_selftest.py 与独立参考实现逐值比对。
 *
 * 输入每行是若干 "RRGGBBAA:population" 词，空格分隔。
 */
public class SelectionProbe {
    public static void main(String[] args) throws Exception {
        Class<?> swCls = Class.forName("com.betteroriginos.feature.media.A11Palette$Swatch");
        Class<?> palCls = Class.forName("com.betteroriginos.feature.media.A11Palette$Palette");
        Class<?> utilCls = Class.forName("com.betteroriginos.feature.media.A11Palette");

        Constructor<?> swCtor = swCls.getDeclaredConstructor(int.class, int.class);
        swCtor.setAccessible(true);
        Constructor<?> palCtor = palCls.getDeclaredConstructor(List.class, int.class);
        palCtor.setAccessible(true);

        Method mDominant = palCls.getDeclaredMethod("dominant");
        Method mVibrant = palCls.getDeclaredMethod("vibrant");
        Method mLightVibrant = palCls.getDeclaredMethod("lightVibrant");
        Method mDarkVibrant = palCls.getDeclaredMethod("darkVibrant");
        Method mMuted = palCls.getDeclaredMethod("muted");
        Method mLightMuted = palCls.getDeclaredMethod("lightMuted");
        Method mDarkMuted = palCls.getDeclaredMethod("darkMuted");
        java.lang.reflect.Field mRgb = swCls.getDeclaredField("rgb");
        java.lang.reflect.Field mPop = swCls.getDeclaredField("population");
        Method mFindBg = utilCls.getDeclaredMethod("findBackgroundSwatch", palCls);
        Method mSelectFg = utilCls.getDeclaredMethod("selectForegroundColor", int.class, palCls);

        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<Object> swatches = new ArrayList<Object>();
            int maxPop = 0;
            for (String tok : line.split(" +")) {
                int colon = tok.indexOf(':');
                int rgb = (int) (Long.parseLong(tok.substring(0, colon), 16) & 0xFFFFFFFFL);
                int pop = Integer.parseInt(tok.substring(colon + 1));
                if (pop > maxPop) {
                    maxPop = pop;
                }
                swatches.add(swCtor.newInstance(Integer.valueOf(rgb), Integer.valueOf(pop)));
            }
            Object palette = palCtor.newInstance(swatches, Integer.valueOf(maxPop));
            StringBuilder out = new StringBuilder(line).append(" ->");
            out.append(" DOM=").append(show(mDominant.invoke(palette), mRgb, mPop));
            out.append(" V=").append(show(mVibrant.invoke(palette), mRgb, mPop));
            out.append(" LV=").append(show(mLightVibrant.invoke(palette), mRgb, mPop));
            out.append(" DV=").append(show(mDarkVibrant.invoke(palette), mRgb, mPop));
            out.append(" M=").append(show(mMuted.invoke(palette), mRgb, mPop));
            out.append(" LM=").append(show(mLightMuted.invoke(palette), mRgb, mPop));
            out.append(" DM=").append(show(mDarkMuted.invoke(palette), mRgb, mPop));
            Object bg = mFindBg.invoke(null, palette);
            out.append(" BG=").append(show(bg, mRgb, mPop));
            int bgRgb = bg == null ? 0xFFFFFFFF : ((Integer) mRgb.get(bg)).intValue();
            out.append(" FG=").append(Integer.toHexString(
                    ((Integer) mSelectFg.invoke(null, Integer.valueOf(bgRgb), palette)).intValue()));
            System.out.println(out);
        }
    }

    private static String show(Object swatch, Field mRgb, Field mPop) throws Exception {
        if (swatch == null) {
            return "-";
        }
        return Integer.toHexString(((Integer) mRgb.get(swatch)).intValue()) + "/"
                + ((Integer) mPop.get(swatch)).intValue();
    }
}
