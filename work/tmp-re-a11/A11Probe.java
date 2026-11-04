import com.betteroriginos.feature.media.A11Palette;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 把模块里真实的 A11Palette 跑成 stdin->stdout，用于和 Python 参考实现逐值比对 */
public class A11Probe {
    public static void main(String[] args) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" +");
            int bg = (int) Long.parseLong(parts[0], 16);
            int fg = (int) Long.parseLong(parts[1], 16);
            int[] t = A11Palette.colorizedTextColors(bg, fg);
            int card = A11Palette.computeCardBackground(fg);
            sb.append(String.format("%08x %08x %08x %08x%n", bg, t[0], t[1], card));
        }
        System.out.print(sb);
    }
}
