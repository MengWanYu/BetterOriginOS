package com.originui.core.utils;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: classes5.dex */
public abstract class G2CornerUtil {
    public static final double ANGLE_COS_25 = Math.cos(0.4363323129985824d);
    public static final double ANGLE_SIN_25 = Math.sin(0.4363323129985824d);
    public static final boolean G2_PATH_STYLE;
    public static Method sMethodCanvasClipG2OutPath;
    public static Method sMethodCanvasClipG2Path;
    public static Method sMethodCanvasClipG2PathRegion;
    public static Method sMethodCanvasDrawG2RoundRect;
    public static Method sMethodCanvasDrawG2RoundRectF;
    public static Method sMethodCanvasDrawG2RoundRectRadii;
    public static Method sMethodOutlineSetRoundRect;
    public static Method sMethodPathAddG2RoundRect;

    public static int getDefaultRadiusByG2(float f, float f2) {
        return (int) f;
    }

    public static int getG2Radius(float f, float f2) {
        return (int) f;
    }

    static {
        G2_PATH_STYLE = VSystemPropertiesUtils.get("com.originui.debug.g2path.style", 0) == 1;
        sMethodOutlineSetRoundRect = null;
        sMethodCanvasDrawG2RoundRect = null;
        sMethodCanvasDrawG2RoundRectRadii = null;
        sMethodCanvasDrawG2RoundRectF = null;
        sMethodPathAddG2RoundRect = null;
        sMethodCanvasClipG2Path = null;
        sMethodCanvasClipG2PathRegion = null;
        sMethodCanvasClipG2OutPath = null;
    }

    public static boolean setViewG2Corner(View view, float f) {
        return setViewG2Corner(view, 0.0f, 0.0f, f, true, true, true, true);
    }

    public static boolean setViewG2Corner(View view, final float f, final float f2, final float f3, final boolean z, final boolean z2, final boolean z3, final boolean z4) {
        if (view == null) {
            return false;
        }
        view.setOutlineProvider(new ViewOutlineProvider() { // from class: com.originui.core.utils.G2CornerUtil.1
            public G2PathCache g2PathCache = new G2PathCache();

            @Override // android.view.ViewOutlineProvider
            public void getOutline(View view2, Outline outline) {
                float width = f;
                if (width <= 0.0f) {
                    width = view2.getWidth();
                }
                float height = f2;
                if (height <= 0.0f) {
                    height = view2.getHeight();
                }
                float f4 = height;
                Path g2RoundConerPath = this.g2PathCache.getG2RoundConerPath(f3, 0.0f, 0.0f, width, f4, z, z2, z3, z4);
                if (g2RoundConerPath.isEmpty()) {
                    outline.setRoundRect(0, 0, (int) width, (int) f4, f3);
                } else {
                    outline.setPath(g2RoundConerPath);
                    outline.setAlpha(0.99f);
                }
            }
        });
        view.setClipToOutline(f3 > 0.0f);
        return true;
    }

    public static Path getNormalCornerPath(Path path, float f, float f2, float f3, float f4, float f5, boolean z, boolean z2, boolean z3, boolean z4) {
        if (path == null) {
            path = new Path();
        } else {
            path.reset();
        }
        int i = (int) (f3 - f);
        int i2 = (int) (f4 - f2);
        if (i < 0 || i2 < 0) {
            if (!VLogUtils.sIsDebugOn) {
                return null;
            }
            VLogUtils.i("getNormalCornerPath is  null");
            return null;
        }
        float f6 = f5 * 2.0f;
        if (f6 > i || f6 > i2) {
            f5 = Math.min(i, i2) / 2.0f;
            if (VLogUtils.sIsDebugOn) {
                VLogUtils.i("normal corner path radius max is " + f5);
            }
        }
        float f7 = f2 + f5;
        path.moveTo(f, f7);
        if (z) {
            float f8 = f5 * 2.0f;
            path.arcTo(new RectF(f, f2, f + f8, f8 + f2), 180.0f, 90.0f);
        } else {
            path.lineTo(f, f2);
            path.lineTo(f + f5, f2);
        }
        float f9 = f3 - f5;
        path.lineTo(f9, f2);
        if (z2) {
            float f10 = f5 * 2.0f;
            path.arcTo(new RectF(f3 - f10, f2, f3, f10 + f2), 270.0f, 90.0f);
        } else {
            path.lineTo(f3, f2);
            path.lineTo(f3, f7);
        }
        float f11 = f4 - f5;
        path.lineTo(f3, f11);
        if (z3) {
            float f12 = f5 * 2.0f;
            path.arcTo(new RectF(f3 - f12, f4 - f12, f3, f4), 0.0f, 90.0f);
        } else {
            path.lineTo(f3, f4);
            path.lineTo(f9, f4);
        }
        path.lineTo(f + f5, f4);
        if (z4) {
            float f13 = f5 * 2.0f;
            path.arcTo(new RectF(f, f4 - f13, f13 + f, f4), 90.0f, 90.0f);
        } else {
            path.lineTo(f, f4);
            path.lineTo(f, f11);
        }
        path.lineTo(f, f7);
        path.close();
        setPathG2Style(path, true);
        return path;
    }

    public static Path getG2RoundConerPath(float f, float f2, float f3, float f4, float f5, boolean z, boolean z2, boolean z3, boolean z4) {
        return getG2RoundConerPath(null, f, f2, f3, f4, f5, z, z2, z3, z4);
    }

    public static Path getG2RoundConerPath(Path path, float f, float f2, float f3, float f4, float f5, boolean z, boolean z2, boolean z3, boolean z4) {
        Path path2;
        float f6;
        float f7;
        float f8;
        float f9;
        float f10;
        float f11;
        if (path == null) {
            path2 = new Path();
        } else {
            path.reset();
            path2 = path;
        }
        float f12 = f3 - f;
        float f13 = f4 - f2;
        if (f12 < 0.0f || f13 < 0.0f) {
            if (VLogUtils.sIsDebugOn) {
                VLogUtils.i("getG2RoundConerPath is  null");
            }
            return path2;
        }
        float f14 = 2.2f * f5;
        if (f14 > f12 || f14 > f13) {
            if (VLogUtils.sIsDebugOn) {
                VLogUtils.i("Return to normal rounded corners, g2 corner path radius max is " + f5);
            }
            return getNormalCornerPath(path2, f, f2, f3, f4, f5, z, z2, z3, z4);
        }
        double d = f5;
        float f15 = (float) (d - (ANGLE_COS_25 * d));
        float f16 = (float) (d - (ANGLE_SIN_25 * d));
        float f17 = (-0.008f) * f5;
        float f18 = 0.4f * f5;
        float f19 = 1.1f * f5;
        if (Math.min(f12, f13) > 2.6f * f5) {
            f17 = f5 * (-0.013f);
            f18 = f5 * 0.61f;
            f19 = f5 * 1.3f;
        }
        float f20 = f17;
        float f21 = f18;
        float f22 = f19;
        float f23 = f2 + f22;
        path2.moveTo(f, f23);
        if (z) {
            float f24 = f + f15;
            float f25 = f2 + f16;
            f6 = 40.0f;
            f7 = f23;
            path2.cubicTo(f, f23 - f21, f24 - f20, f25, f24, f25);
            float f26 = f5 * 2.0f;
            path2.arcTo(new RectF(f, f2, f + f26, f26 + f2), 205.0f, 40.0f);
            float f27 = f + f22;
            path2.cubicTo(f + f16, (f2 + f15) - f20, f27 - f21, f2, f27, f2);
        } else {
            f6 = 40.0f;
            f7 = f23;
            path2.lineTo(f, f2);
            path2.lineTo(f + f22, f2);
        }
        float f28 = f3 - f22;
        path2.lineTo(f28, f2);
        if (z2) {
            float f29 = f3 - f16;
            float f30 = f2 + f15;
            f8 = f28;
            path2.cubicTo(f28 + f21, f2, f29, f30 - f20, f29, f30);
            float f31 = f5 * 2.0f;
            path2.arcTo(new RectF(f3 - f31, f2, f3, f31 + f2), 295.0f, f6);
            float f32 = f7;
            f9 = f32;
            path2.cubicTo((f3 - f15) + f20, f2 + f16, f3, f32 - f21, f3, f9);
        } else {
            f8 = f28;
            f9 = f7;
            path2.lineTo(f3, f2);
            path2.lineTo(f3, f9);
        }
        float f33 = f6;
        float f34 = f4 - f22;
        path2.lineTo(f3, f34);
        if (z3) {
            float f35 = f3 - f15;
            float f36 = f4 - f16;
            f10 = f34;
            f11 = f9;
            path2.cubicTo(f3, f34 + f21, f35 + f20, f36, f35, f36);
            float f37 = f5 * 2.0f;
            path2.arcTo(new RectF(f3 - f37, f4 - f37, f3, f4), 25.0f, f33);
            float f38 = f8;
            path2.cubicTo(f3 - f16, (f4 - f15) + f20, f38 + f21, f4, f38, f4);
        } else {
            f10 = f34;
            f11 = f9;
            path2.lineTo(f3, f4);
            path2.lineTo(f8, f4);
        }
        float f39 = f + f22;
        path2.lineTo(f39, f4);
        if (z4) {
            float f40 = f + f16;
            float f41 = f4 - f15;
            path2.cubicTo(f39 - f21, f4, f40, f41 + f20, f40, f41);
            float f42 = f5 * 2.0f;
            path2.arcTo(new RectF(f, f4 - f42, f42 + f, f4), 115.0f, f33);
            float f43 = f10;
            path2.cubicTo((f + f15) - f20, f4 - f16, f, f43 + f21, f, f43);
        } else {
            path2.lineTo(f, f4);
            path2.lineTo(f, f10);
        }
        path2.lineTo(f, f11);
        path2.close();
        setPathG2Style(path2, true);
        return path2;
    }

    public static boolean setPathG2Style(Path path, boolean z) {
        if (path == null || !G2_PATH_STYLE) {
            return false;
        }
        VReflectionUtils.invokeMethod(path, "setG2Style", new Class[]{Boolean.TYPE}, new Object[]{Boolean.valueOf(z)});
        return true;
    }

    public static boolean isAlgorithmG2Support(int i, int i2, float f) {
        return ((float) Math.min(i, i2)) > (f * 2.0f) * 1.4f;
    }

    public static int getG2Radius(float f) {
        return getG2Radius(f, 1.0f);
    }

    public static boolean canvasDrawG2RoundRectRadii(Canvas canvas, float f, float f2, float f3, float f4, float[] fArr, Paint paint) {
        if (canvas == null) {
            return false;
        }
        if (sMethodCanvasDrawG2RoundRectRadii == null) {
            Class cls = Float.TYPE;
            sMethodCanvasDrawG2RoundRectRadii = VReflectionUtils.getDeclaredMethod(canvas, "drawG2RoundRect", new Class[]{cls, cls, cls, cls, float[].class, Paint.class});
            if (VLogUtils.sIsDebugOn) {
                VLogUtils.d("canvasDrawG2RoundRectRadii canvas:" + canvas + ",sMethodCanvasDrawG2RoundRectRadii:" + sMethodCanvasDrawG2RoundRectRadii);
            }
        }
        try {
            Method method = sMethodCanvasDrawG2RoundRectRadii;
            if (method != null) {
                method.invoke(canvas, Float.valueOf(f), Float.valueOf(f2), Float.valueOf(f3), Float.valueOf(f4), fArr, paint);
                return true;
            }
            canvas.drawPath(getG2RoundConerPath(null, f, f2, f3, f4, getDefaultRadiusByG2(Math.max(fArr[0], Math.max(fArr[2], Math.max(fArr[4], fArr[6]))), 1.0f), fArr[0] > 0.0f, fArr[2] > 0.0f, fArr[4] > 0.0f, fArr[6] > 0.0f), paint);
            return true;
        } catch (Exception unused) {
            return false;
        }
    }

    public static boolean canvasClipG2Path(Canvas canvas, Path path) {
        if (canvas != null && path != null) {
            if (sMethodCanvasClipG2Path == null) {
                sMethodCanvasClipG2Path = VReflectionUtils.getDeclaredMethod(canvas, "clipG2Path", new Class[]{Path.class});
                if (VLogUtils.sIsDebugOn) {
                    VLogUtils.d("canvasClipG2Path canvas:" + canvas + ",sMethodCanvasClipG2Path:" + sMethodCanvasClipG2Path);
                }
            }
            try {
                Method method = sMethodCanvasClipG2Path;
                if (method != null) {
                    method.invoke(canvas, path);
                    return true;
                }
            } catch (Exception unused) {
            }
        }
        return false;
    }

    public static boolean setOutLineG2RoundRect(Outline outline, int i, int i2, int i3, int i4, float f) {
        if (outline == null) {
            return false;
        }
        if (sMethodOutlineSetRoundRect == null) {
            Class cls = Integer.TYPE;
            sMethodOutlineSetRoundRect = VReflectionUtils.getDeclaredMethod(outline, "setRoundRect", new Class[]{cls, cls, cls, cls, Float.TYPE, Boolean.TYPE});
            if (VLogUtils.sIsDebugOn) {
                VLogUtils.d("setOutLineG2RoundRect outline:" + outline + ",sMethodOutlineSetRoundRect:" + sMethodOutlineSetRoundRect);
            }
        }
        try {
            Method method = sMethodOutlineSetRoundRect;
            if (method != null) {
                method.invoke(outline, Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3), Integer.valueOf(i4), Float.valueOf(f), Boolean.TRUE);
                return true;
            }
            outline.setRoundRect(i, i2, i3, i4, getDefaultRadiusByG2(f, 1.0f));
            return true;
        } catch (Exception unused) {
            return false;
        }
    }

    public static float[] getRoundRectRadiiCW(float f, boolean z, boolean z2, boolean z3, boolean z4) {
        float f2 = z ? f : 0.0f;
        float f3 = z2 ? f : 0.0f;
        float f4 = z3 ? f : 0.0f;
        if (!z4) {
            f = 0.0f;
        }
        return new float[]{f2, f2, f3, f3, f4, f4, f, f};
    }
}
