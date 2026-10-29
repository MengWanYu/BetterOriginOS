# vivo-OriginUI Skill Pack · 2026-10-26

OriginOS 5 UI 框架（OriginUI）逆向复刻 + 接入指南的可迁移技能包。
来源项目：BetterOriginOS（iQOO Z8 / Android 15 / OriginOS 5，真机验证）。

## 包内容

```
vivo-OriginUI-skill-pack-20261026/
├── README.md                        ← 本文件
├── originui-integration/            ← 主技能（可迁移）
│   ├── SKILL.md                     接入指南（模糊/控件/token/坑/验收）
│   ├── lib/src/com/originui/replica/   9 个纯 Java 文件（无 androidx/资源依赖）
│   │   ├── OriginTheme.java         设计 token（day/night 自动）
│   │   ├── OriginBlur.java          View.setMaterial 原生模糊反射封装
│   │   ├── OriginBlurLayout.java    模糊容器（VBlurLinearLayout 1:1）
│   │   ├── OriginTopBar.java        模糊顶栏 + 滚动 16dp 渐显
│   │   ├── OriginBottomBar.java     模糊底栏
│   │   ├── OriginSwitch.java        OS5 开关（空心环/双动画器/Overshoot 续播）
│   │   ├── OriginSlider.java        滑杆（蓝盘白心 + 拖动数值气泡）
│   │   ├── OriginRows.java          行级圆角分组列表 + 分隔线
│   │   └── OriginOverlays.java      Dialog / BottomSheet / ListPopup
│   ├── docs/
│   │   ├── 01-blur.md               模糊完整原理（调用链/19 种材质映射/光学参数）
│   │   ├── 02-viewport.md           顶底栏/滚动视口/阻尼头逆向
│   │   └── 03-controls.md           全控件参数（开关/滑杆/行/对话框/弹层…）
│   └── evidence/                    10 张真机基准截图
└── betteroriginos-dev/              ← 附带：原项目开发规范（LSPosed 模块向，非通用）
    └── SKILL.md
```

## 装到其他平台

### A. ZCode / Claude Code 风格（目录 + SKILL.md frontmatter）
1. 把 `originui-integration/` 整个目录放到平台的 skills 目录，例如：
   - 项目级：`<project>/.zcode/skills/originui-integration/`（ZCode）
   - 用户级：`~/.claude/skills/originui-integration/`（Claude Code 等）
2. 确保 `SKILL.md` 在该目录根（frontmatter 的 `name` / `description` 保留）。
3. 重启会话或刷新技能列表；部分平台需要新会话才能发现新增 skill。

### B. 只吃规则文件的平台（AGENTS.md / rules / system prompt）
1. 把 `originui-integration/SKILL.md` 的正文（去掉 frontmatter 也可）加入规则文件。
2. 把 `lib/`、`docs/`、`evidence/` 放到工程内，路径保持与 SKILL.md 中的相对路径一致：
   ```
   <project>/skills/originui-integration/{SKILL.md,lib,docs,evidence}
   ```
3. 若平台不支持相对引用，把 SKILL.md 里的 `lib/...`、`docs/...` 改成你的实际路径。

### C. 纯手工
直接读 `SKILL.md` 按「1. 接入步骤」做；库是纯源码拷贝，不需要任何构建插件。

## 依赖与兼容

- Android **minSdk 29+**；源码级别 Java 8（无 androidx / 无第三方依赖）。
- 原生模糊依赖 vivo 设备：`android.util.FtFeature.isFeatureSupport("vivo.software.spacesystem")`
  + `android.view.View.setMaterial(style, contentType)`（framework 公共方法，反射调用）。
- 非 vivo 设备：`OriginBlur.isSupported()` 返回 false，容器自动回退为普通背景（不会崩）。
- 深色模式：token 与材质 id 自动切换（`OriginBlurLayout.onConfigurationChanged`）。

## 快速自检（真机上）

```java
boolean ok = OriginBlur.isSupported(context);       // vivo: true
OriginBlur.apply(view, OriginBlur.TOOLBAR, OriginTheme.night(ctx)); // 返回 true
// 视觉验证：让彩色内容从 View 下方滚过，模糊区应呈雾化；纯色背景看不出差别。
```

## 来源与说明

- 逆向自本机 `vivo-framework.jar`（android/view/blur、VivoViewImpl）、
  OriginUI 控件库（反编译 486 类）、framework 通知/设置资源；坐标/尺寸/颜色均经真机像素测量校准。
- 仅用于自用/学习性质的 UI 复刻；`View.setMaterial` 为 vivo 私有扩展，非标准 Android API，
  跨 ROM/版本需重新验证（`docs/01-blur.md` 有完整调用链与材质表）。
- 基准截图对应设备：iQOO Z8（PD2314），Android 15 / OriginOS 5，SystemUI 15.0.5.241。
