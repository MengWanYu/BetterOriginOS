import java.lang.reflect.Method;
import java.util.List;

/**
 * 反射调用模块编译产物里的 A11Palette.quantizeToSwatches(int[], int, SwatchFilter)，
 * 把每个 Swatch 的 (rgb, population) 打出来，交给 work/a11_quantize_selftest.py 比对。
 *
 * 输入每行：`maxColors [filterBlackWhite] px1 px2 ...`，像素是 0xRRGGBB（16 进制，无 alpha）。
 * 输出：`N -> RRGGBB/COUNT RRGGBB/COUNT ...`（按调用方给的顺序，比对时按多重集处理）。
 */
public class QuantizeProbe {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("com.betteroriginos.feature.media.A11Palette");
        Class<?> filterCls = Class.forName("com.betteroriginos.feature.media.A11Palette$SwatchFilter");
        final Method m = cls.getDeclaredMethod("quantizeToSwatches", int[].class, int.class, filterCls);
        m.setAccessible(true);

        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" +");
            int maxColors = Integer.parseInt(p[0]);
            final boolean dropMono = "1".equals(p[1]);
            int[] px = new int[p.length - 2];
            for (int i = 0; i < px.length; i++) {
                px[i] = (int) (0xFF000000L | Long.parseLong(p[i + 2], 16));
            }
            Object filter = null;
            if (dropMono) {
                // 与 A11 的 mBlackWhiteFilter 等价的简化版：剔掉极黑/极白
                filter = java.lang.reflect.Proxy.newProxyInstance(
                        filterCls.getClassLoader(), new Class<?>[]{filterCls},
                        (proxy, method, a) -> {
                            int rgb = ((Integer) a[0]).intValue();
                            int mx = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
                            int mn = Math.min((rgb >> 16) & 0xFF, Math.min((rgb >> 8) & 0xFF, rgb & 0xFF));
                            float l = (mx + mn) / 510f;
                            return Boolean.valueOf(l > 0.08f && l < 0.90f);
                        });
            }
            List<?> out = (List<?>) m.invoke(null, px, Integer.valueOf(maxColors), filter);
            StringBuilder sb = new StringBuilder();
            Class<?> swCls = Class.forName("com.betteroriginos.feature.media.A11Palette$Swatch");
            java.lang.reflect.Field fRgb = swCls.getDeclaredField("rgb");
            java.lang.reflect.Field fPop = swCls.getDeclaredField("population");
            for (Object s : out) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Integer.toHexString(((Integer) fRgb.get(s)).intValue() & 0xFFFFFF))
                        .append('/').append(((Integer) fPop.get(s)).intValue());
            }
            System.out.println(px.length + "px max=" + maxColors + " mono=" + (dropMono ? 1 : 0)
                    + " -> [" + sb + "]");
        }
    }
}
