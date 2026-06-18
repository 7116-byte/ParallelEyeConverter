# v0.1.7

修复旋转闪退，并补全控制条显示逻辑。

- 旋转时不再对同一个 `MediaProjection` 反复 `createVirtualDisplay`。
- 改为保留原 `VirtualDisplay`，旋转后使用 `resize + setSurface` 切换新的 `ImageReader.surface`，避免新安卓闪退。
- 控制条增加最大化按钮，可强制恢复当前方向全屏布局并重置缩放。
- 控制条默认显示 3 秒后自动隐藏。
- 点分屏画面会重新显示控制条，并再次 3 秒后隐藏。
- 保留最小化按钮和绿色关闭按钮。
- APK 文件名为 `ParallelEyeConverter-v0.1.7-debug.apk`。
