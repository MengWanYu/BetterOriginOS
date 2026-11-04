package android.graphics;
import java.util.ArrayList;
import java.util.List;
/** 记录型 Path 桩：只为在桌面 JVM 上验证模块里真实的 g2Path 生成的指令序列 */
public class Path {
    public enum Direction { CW, CCW }
    public final List<String> ops = new ArrayList<String>();
    public boolean isEmpty() { return ops.isEmpty(); }
    public void reset() { ops.clear(); }
    public void moveTo(float x, float y) { ops.add("M " + f(x) + " " + f(y)); }
    public void lineTo(float x, float y) { ops.add("L " + f(x) + " " + f(y)); }
    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        ops.add("C " + f(x1) + " " + f(y1) + " " + f(x2) + " " + f(y2) + " " + f(x3) + " " + f(y3));
    }
    public void arcTo(RectF r, float start, float sweep) {
        ops.add("A " + f(r.left) + " " + f(r.top) + " " + f(r.right) + " " + f(r.bottom)
                + " " + f(start) + " " + f(sweep));
    }
    public void addRoundRect(RectF r, float[] radii, Direction d) {
        StringBuilder sb = new StringBuilder("R");
        for (float v : radii) sb.append(' ').append(f(v));
        ops.add(sb.toString());
    }
    public void addRect(float l, float t, float r, float b, Direction d) {
        ops.add("Q " + f(l) + " " + f(t) + " " + f(r) + " " + f(b));
    }
    public void close() { ops.add("Z"); }
    private static String f(float v) { return String.format("%.4f", v); }
}
