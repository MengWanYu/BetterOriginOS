久等了各位。

本次快照版更新了以下内容：

**新增 OriginOS 6（Android 16）适配（快照）**

- 媒体卡三种样式可用（OS5 原子随身听 / Material / Android 11）
- 系统升级防护：只下载不安装、拦自动重启
- 安装器防护、桌面动效增强
- 通知条美化在 OS6 暂时停用

**媒体卡修复（OS6）**

- 切歌不再把应用图标当封面取色（底色不会变成图标颜色了）
- 切歌空窗期保持上一首封面，新封面到了再一起淡入
- 底色和封面同步淡入，不再慢半拍
- 快速连切不会整卡纯色
- 没有上一首动作的通知会自动合成一个上一首按钮
- Android 11 样式恢复和通知条一致的圆角

**修复（所有机型）**

- 播放器按钮/图标被误隐藏的回归

**已知问题**

- OS6 媒体卡圆角弧度和通知条还有细微不一致
- OS6 切歌时左半底色时序还有细微不同步
- OS6 桌面动效档位视觉验收未完成

**安装**

root + LSPosed，装 APK 启用，勾作用域，重启 SystemUI。
OS6 作用域记得加：`com.vivo.systemuiplugin`、`com.iqoo.media.module`、`com.bbk.updater`、`packageinstaller`、`camera`、`launcher2`、`settings`。

下载：BetterOriginOS-0.1.4-snapshot.apk（377 KB）
MD5：5e66e518d77c8c3af8c1c8686595c27c
源码：github.com/MengWanYu/BetterOriginOS

有问题带上机型 + 系统版本 + 开了哪几个功能。OS6 媒体卡问题优先收。
