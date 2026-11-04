import android.graphics.Path;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/** 反射调用模块里包私有的 Os5Mirror.g2Path，输出完整指令序列（用 | 分隔） */
public class G2Probe {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("com.betteroriginos.feature.notify.Os5Geometry");
        Method m = cls.getDeclaredMethod("g2Path", Path.class, float.class, float.class,
                float.class, float.class, float.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        m.setAccessible(true);
        Method rs = cls.getDeclaredMethod("rescaleRadii", float.class, float.class,
                float.class, float.class);
        rs.setAccessible(true);
        for (String c : new String[]{"51.0 54.0 51.0 0.0", "51.0 54.0 51.0 51.0",
                "0.0 54.0 40.0 40.0", "54.0 54.0 54.0 27.0", "54.0 72.0 0.0 54.0"}) {
            String[] v = c.split(" ");
            float[] out = (float[]) rs.invoke(null, Float.parseFloat(v[0]), Float.parseFloat(v[1]),
                    Float.parseFloat(v[2]), Float.parseFloat(v[3]));
            System.out.println("RESCALE " + c + " -> " + String.format("%.3f %.3f %.3f",
                    out[0], out[1], out[2]));
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String line;
        StringBuilder out = new StringBuilder();
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) { continue; }
            String[] p = line.split(" +");
            Path path = new Path();
            m.invoke(null, path,
                    Float.parseFloat(p[0]), Float.parseFloat(p[1]),
                    Float.parseFloat(p[2]), Float.parseFloat(p[3]),
                    Float.parseFloat(p[4]),
                    "1".equals(p[5]), "1".equals(p[6]), "1".equals(p[7]), "1".equals(p[8]));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.ops.size(); i++) {
                if (i > 0) sb.append("|");
                sb.append(path.ops.get(i));
            }
            out.append(sb).append(System.lineSeparator());
        }
        System.out.print(out);
    }
}
