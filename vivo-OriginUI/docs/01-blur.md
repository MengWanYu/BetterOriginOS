# 01 · OriginOS 模糊（Material/Blur）完整原理

> 逆向来源（本机 iQOO Z8，Android 15 / OriginOS 5）：
> - `vivo-framework.jar`（脱机提取自 /system/framework）→ `ref/BlurMaterial.java`、`ref/VivoBlurRender.java`、`ref/VivoViewImpl.java`
> - OriginUI 控件库（jadx 反编译 BBKFileManager.apk 486 类）→ `ref/originui-fm-src/`
> - 真机 gate 验证：`evidence/gate-material-1.png`（普通 App 反射调用成功，原生模糊生效）

## 1. 三层调用链（谁在做什么）

```
应用层  com.originui.core.blur.VBlurLinearLayout / VBlurRelativeLayout   ← OriginUI 库（打进每个 APK）
            │  只做一件事：选 material id + 调 View 的隐藏方法
框架层  android.view.VivoViewImpl（View 的 vivo 扩展，打包进 framework）
            │  View.setMaterial(style, contentType)
            │  → 按 contentType 分发到 setBackdropBlurEffect / setWindowBlurEffect
渲染层  RenderNode.setBackdropBlurEffect(...)（HWUI 原生，SKIA Kawase 模糊 + 双层遮罩 + 饱和度 + 融合）
            │  由 ViewRootImpl 的 VivoBlurRender 统一管理（每窗口一个）
系统层  com.vivo.systemblur.server（窗口级模糊 TYPE_WINDOW 走 WMS 侧）
```

**关键结论：`setMaterial` 系列方法是 `android.view.View` 的公共方法**（vivo 修改了 framework 的 View 类），
任何 App **无需 hook、无需权限**，直接 `view.setMaterial(210, 1)` 即可获得与系统应用完全一致的原生模糊。
`FtFeature.isFeatureSupport("vivo.software.spacesystem") == true` 表示设备支持。

真机证据（2026-10-25 22:47，`logcat -s MaterialGate`）：
```
View.setMaterial=true setMaterialAlpha=true setMaterialCornerRadius=true
FtFeature(spacesystem)=true
apply top=true bottom=true card=true
```
截图 `evidence/gate-material-1.png`：顶栏(210)、圆角卡(207+corner24dp)、底栏(204) 三处原生模糊全部生效，
彩色内容穿过时被雾化、圆角保留、卡外内容清晰。

## 2. API 清单（android.view.View 上的 vivo 扩展，均在 `ref/VivoViewImpl.java`）

| 方法 | 作用 |
|---|---|
| `setMaterial(int style, int contentType) → boolean` | 应用材质。style=材质 id，contentType 见 §3 |
| `setMaterialForce(style, contentType)` | 同上但绕过开关检查 |
| `setMaterialAlpha(float 0..1)` | 材质整体透明度（滚动渐显用） |
| `setMaterialCornerRadius(r)` / `(tl,tr,bl,br)` | 模糊区域圆角 |
| `setMaterialRegion(RectF, radius…)` | 模糊区域（部分区域模糊） |
| `setMaterialBlurRadius(float)` | 附加模糊半径（乘法系数外附加） |
| `setMaterialDarkenPercent(float)` | 压暗百分比 |
| `setMaterialGradient(x0,y0,x1,y1,colors)` | 遮罩渐变色（convertedColor = r*65536+g*256+b） |
| `setMaterialGroupID(int)` | 同组模糊共享采样（合并开销） |
| `setMaterialFPS(int)` | 模糊更新帧率（默认 240=跟随屏幕） |
| `setMaterialForceUpdateBg(boolean)` | 强制重采背景 |
| `enableMaterialG2Style(boolean)` | G2 连续曲率圆角 |
| `setMaterialClip(boolean)` | 窗口模糊自动裁剪到 outline |
| `setMaterialBackgroundScale(float)` | 背景采样缩放 |
| `clearMaterial()` | 清除材质 |
| `setVibrancyEffect(style, type)` / `clearVibrancyEffect()` | 鲜活感材质（RuntimeShader，301~381） |
| `setMaterialNoise(...)` | 噪点纹理（style 51-53/251-254/256-265 需要） |
| 窗口级：`setWindowBlurEffect(style)`、`setWindowBlurAlpha`、`setWindowBlurCornerRadius`、`setWindowBlurRegion`、`setWindowBlurGradient` | contentType=0 时使用，模糊窗口后面的壁纸/其他窗口 |
| 视图级：`setBackdropBlurEffect(style, forceUpdate, includeSelf, ...)` | 模糊 View 下层内容 |

`isSupportViewBlur()` = `IVivoBlurRender.isSupportViewBlur()`；总开关 `VivoBlur.ENABLE_BLUR`
（prop `debug.blur.enable`，默认 true）。

## 3. contentType 语义（`VivoViewImpl.setMaterialForce` 分发逻辑）

| contentType | 名称 | 行为 | 场景 |
|---|---|---|---|
| 0 | 窗口模糊 | `clearBackdropBlurEffect + setWindowBlurEffect` | 悬浮窗/通知盲区（糊壁纸） |
| 1 | 视图内模糊 | `clearWindowBlurEffect + setBackdropBlurEffect(style, true, true, false, false, false)` | **顶栏/底栏/卡片（最常用）** |
| 2 | 视图内模糊+自绘 | 同 1 但 includeSelf=true | View 自己有内容也要参与 |
| 3 | 同 2 + blurredBackground | 记录器模糊 | 录屏场景 |
| 4 | 组合 | 窗口+视图同时 | 特殊（sheet dialog toolbar） |

**复刻一律用 contentType=1**（真机 gate 已验证）。

## 4. material style 对照表（VBlurUtils.initMaterialMap + BlurMaterial）

### 4.1 OriginUI 语义类型 → 实际 style id

VBlurUtils.TYPE_* 在亮/暗下的 id（light 来自 initMaterialMap，dark 来自 initDarkMaterialMap）：

| OriginUI 类型 | 场景 | light | dark |
|---|---|---|---|
| TYPE_IMMERSION_MENU_VIEW (0) | 沉浸式菜单 | 206 | 256 |
| TYPE_FLOAT_MENU (1) | 悬浮菜单/卡片 | 207 | 257 |
| TYPE_TOOLBAR (2) | 顶栏 | 202 | 252 |
| TYPE_POPUP_WINDOW (3) | 弹窗 | 208 | 258 |
| TYPE_SHEET_DIALOG (4) | 底部弹层/底栏 | 204 | 254 |
| TYPE_SHEET_DIALOG_TOOLBAR (5) | 弹层顶栏 | 210 | 260 |
| TYPE_WINDOW (6) | 窗口（糊壁纸） | 201 | 251 |
| TYPE_TOAST (7) | Toast | 209 | 259 |
| TYPE_FLOAT_NOTIFICATION (9) | 浮动通知 | 205 | 255 |
| TYPE_SNACKBAR (10) | Snackbar | 211 | 261 |
| TYPE_PROMPT (11) | 提示条 | 203 | 253 |
| TYPE_BANNERTIP (12) | 横幅提示 | 212 | 262 |
| TYPE_SHEET_DIALOG_2 (13) | 底部弹层 2 | 213 | 263 |
| TYPE_M1/M2/M3/M4 (14-17) | 基础材质 1-4 | 1/2/3/4 | 51/52/53/54 |
| TYPE_BANNERTIP_2 (18) | 横幅提示 2 | 214 | 264 |
| TYPE_TOOLBAR_2 (19) | 顶栏 2（灰清除材质，走 setGrayClear） | 215 | 265 |

（混淆常量还原：SK_ERROR_ECIES_CREATE_KEY_ERROR=202、SK_ERROR_ECIES_CONVERT_KEY_ERROR=201、
SK_ERROR_ECIES_ECDH_ERROR=203、StateCode.NEED_UPDATE=210）

### 4.2 光学参数（BlurMaterial.resolveBlurGeometryByStyle，完整代码在 ref/BlurMaterial.java）

每个 style = 双层遮罩 + 饱和度 + 模糊半径系数 + 融合模式。
`FINGERPRINT_TYPE_ALL = 0xFFFFFF`（白色遮罩）；常用遮罩色：
- 16382457 = 0xFA1AF9 ≈ 近白微暖 (#FA1AF9? 实为 0x00F9F9F9 附近，解析为 0xFA1AF9 是 jadx 优化显示，实际值 16382457=0xF9F9F9)
- 16250871 = 0xF7F7F7
- 2105376 = 0x202020，1907997 = 0x1D1D1D，8158332 = 0x7C7C7C，2631720 = 0x282828，2434341 = 0x252525，6052956 = 0x5C5C5C，10066329 = 0x999999，6118749 = 0x5D5D5D

常用材质参数表（复刻兜底渲染用）：

| style | 遮罩1 | 透明度1 | 遮罩2 | 透明度2 | 饱和度 | 模糊系数 | 融合2 |
|---|---|---|---|---|---|---|---|
| 1 (M1 light) | white | 0.78 | white | 0.34 | 0 | 1.0 | 0 |
| 2 (M2 light) | white | 0.39 | white | 0.27 | 21 | 1.0 | 0 |
| 4 (M4 light) | white | 0.12 | white | 0.11 | 0 | 0.9 | 0 |
| 201 (window light) | white | 0.85 | white | 0.25 | 60 | 0.8 | 1 |
| 202 (toolbar light) | white | 0.39 | white | 0.27 | 21 | 1.0 | 0 |
| 204 (sheet light) | 0x999999 | 0.97 | 0x5D5D5D | 1.0 | 60 | 0.405 | 7 |
| 205 (notification light) | white | 0.55 | white | 0.05 | 41 | 0.65 | 1 |
| 206 (menu light) | 0xF7F7F7 | 0.85 | — | 0 | 60 | 0.306 | 0 |
| 207 (float menu light) | 0xF7F7F7 | 0.80 | — | 0 | 60 | 0.45 | 0 |
| 208 (popup light) | white | 0.65 | white | 0.30 | 40 | 0.8 | 0 |
| 209 (toast light) | white | 0.80 | white | 0.10 | 60 | 0.4 | 1 |
| 210 (sheet toolbar light) | white | 0.80 | white | 0.20 | 60 | 0.9 | 1 |
| 211 (snackbar light) | black | 0.80 | — | 0 | 0 | 0.8 | 0 |
| 212 (bannertip light) | 0xF9F9F9 | 0.80 | — | 0.08 | 60 | 0.45 | 0 |
| 213 (sheet2 light) | white | 0.80 | white | 0.20 | 60 | 0.9 | 1 |
| 214 (bannertip2 light) | 0xF9F9F9 | 0.60 | 0xF9F9F9 | 0.20 | 0 | 0.302 | 11 |
| 215 (toolbar2 light) | 0xF9F9F9 | 0.10 | — | 0 | 60 | 0.45 | 0 |
| 51 (M1 dark) | black | 0.60 | — | 0 | 0 | 1.5 | 0 |
| 52 (M2 dark) | black | 0.41 | — | 0 | 0 | 1.5 | 0 |
| 53 (M3 dark) | black | 0.26 | — | 0 | 0 | 1.0 | 0 |
| 54 (M4 dark) | black | 0.10 | — | 0 | 0 | 1.0 | 0 |
| 251 (window dark) | 0x202020 | 0.94 | — | 0 | 30 | 1.0 | 0 |
| 252 (toolbar dark) | 0x1D1D1D | 0.70 | — | 0 | 15 | 0.45 | 0 |
| 253 (prompt dark) | black | 0.60 | — | 0 | 0 | 0.8 | 0 |
| 254 (sheet dark) | 0x7C7C7C | 1.0 | 0x252525 | 0.9 | 0 | 0.8 | 0(融合1=6) |
| 255 (notification dark) | 0x282828 | 0.55 | — | 0 | 0 | 0.5 | 0 |
| 256 (menu dark) | 0x7C7C7C | 1.0 | 0x282828 | 0.7 | 20 | 0.303 | 0(融合1=6) |
| 257 (float menu dark) | 0x7C7C7C | 1.0 | 0x282828 | 0.7 | 20 | 0.303 | 0(融合1=6) |
| 258 (popup dark) | 见 BlurMaterial.java | | | | | | |

mBlurRadius 是**系数**，实际模糊半径 = 系数 × 基准（500px 上限，见 VivoBlurRender
`setBlurRadius(geometry.mBlurRadius * 500.0f)`——原生侧再把归一化值转像素）。
融合模式 maskFusion：0=普通叠加、1=正片叠底类、6/7/11=特殊混合（详见原生，复刻兜底只需近似 0/1）。

## 5. OriginUI 容器行为（VBlurLinearLayout，ref/app-usage-samples/VBlurLinearLayout.java）

应用侧封装的完整行为，复刻库 `OriginBlurLayout` 需 1:1 实现：

1. **构造**：默认 `enabled = VBlurUtils.getGlobalBlurEnabled(ctx) && VBlurUtils.getGlobalViewBlurEnabled()`；
   默认 material=TYPE_TOOLBAR(2)；默认 materialNightMode=AUTO(0)；创建发丝线 Paint（rom15 色/高）。
2. **onAttachedToWindow**：记录子 View 原 background → 调 `setBlurEffect`（contentType=1）。
3. **模糊成功回调** `f(true)`：
   - `setMaterialAlpha(blurAlpha)`（默认 alpha 可用 setBlurAlpha 设置）
   - `setBackground(new ColorDrawable(0))` —— **自绘背景必须清空**，模糊层才是背景
   - 遍历直接子 View：`setBackgroundTintList(透明)` + 打 tag `tag_child_view_clear_material`
4. **模糊失败**：恢复子 View 原 background（保存在 map）。
5. **divider**：`dispatchDraw` 后画（`setDividerBottom(true)` 默认在下边），颜色
   `originui_vblur_linearlayout_divider_color_rom15_0`，alpha 跟随 blurAlpha（`setDividerAlpha`）。
6. **夜间切换**：onConfigurationChanged → clearMaterial → 重新 setBlurEffect（map 自动换 dark id）。
7. **`setNeedClearAllChildBackground(true)`**：递归清除所有后代 View 的背景。
8. **滚动渐显**：`VBlurUtils.getScrollBlurAlpha(scrollDist, critical=16dp)` = scrollDist<16dp 时
   `dist/16dp` 线性，否则 1.0 → 配合 setMaterialAlpha 实现列表顶部滚动时顶栏模糊渐入。
9. **子 View tag**：`tag_child_view_material_blur_alpha` 记录当前 alpha（供子控件自绘时同步）。

## 6. 复刻实现方案（OriginBlurLayout / OriginBlur helper）

```java
public final class OriginBlur {
    public static boolean isSupported(Context ctx) {
        // FtFeature("vivo.software.spacesystem") == true
    }
    public static boolean apply(View v, @OriginType int type, boolean night, float alpha) {
        int style = MATERIAL[type][night ? 1 : 0];
        boolean ok = v.setMaterial(style, 1);          // 反射
        if (ok) v.setMaterialAlpha(alpha);
        return ok;
    }
}
```

- **真机路径**（gate 已过）：反射调 `setMaterial(style, 1)` + `setMaterialAlpha` + `setMaterialCornerRadius`。
- **兜底路径**（非 vivo 机/模拟器）：RenderEffect.createBlurEffect + 按 §4.2 参数画双层遮罩近似。
- 失败安全：`setMaterial` 返回 false 时保持原 background（OriginUI 同款行为）。

## 7. 未尽事项

- [ ] maskFusion 1/6/7/11 的精确混合公式（原生层，只能截图对比近似）
- [ ] setVibrancyEffect 的 RuntimeShader（51445768 号资源）解析
- [ ] 窗口级模糊（contentType=0）在普通 Activity 的表现（悬浮窗场景）
