import java.lang.reflect.Method;

/**
 * 反射调用模块编译产物里的 A11 取色"工作系"算术：
 * workingSize / collapsedRegions / gridPixels（都不碰 Bitmap，所以能在桌面 JVM 上跑）。
 *
 * 输入每行：`W H`（源图尺寸）或 `G W H`（要第几号取色区的像素数）。
 * 输出：`W H -> ww hh bgL bgT bgR bgB fgL fgT fgR fgB gridBg gridFg`
 */
public class SamplingProbe {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("com.betteroriginos.feature.media.A11Palette");
        Method mWs = cls.getDeclaredMethod("workingSize", int.class, int.class);
        Method mReg = cls.getDeclaredMethod("collapsedRegions", int.class, int.class);
        Method mGrid = cls.getDeclaredMethod("gridPixels", int.class, int.class, int.class);
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(" +");
            int w = Integer.parseInt(p[p.length - 2]);
            int h = Integer.parseInt(p[p.length - 1]);
            int[] ws = (int[]) mWs.invoke(null, Integer.valueOf(w), Integer.valueOf(h));
            int[] reg = (int[]) mReg.invoke(null, Integer.valueOf(w), Integer.valueOf(h));
            Object bg = mGrid.invoke(null, Integer.valueOf(w), Integer.valueOf(h),
                    Integer.valueOf(0));
            Object fg = mGrid.invoke(null, Integer.valueOf(w), Integer.valueOf(h),
                    Integer.valueOf(1));
            StringBuilder sb = new StringBuilder();
            sb.append(ws[0]).append(' ').append(ws[1]);
            for (int v : reg) {
                sb.append(' ').append(v);
            }
            sb.append(' ').append(bg).append(' ').append(fg);
            System.out.println(w + "x" + h + " -> " + sb);
        }
    }
}
