# v0.1.5

修复卡住和悬浮球重新授权体验。

- 悬浮球点击恢复为直接打开分屏画面，不再重新拉起录屏授权。
- 捕获层增加显示变化监听，旋转或屏幕尺寸变化后延迟 700ms 重建 `VirtualDisplay + ImageReader`。
- 增加停帧 watchdog，如果超过约 2.2 秒没有新帧，会自动重建捕获 surface。
- 重建前正确释放旧 `VirtualDisplay` 和旧 `ImageReader`，减少旧 surface 卡住。
- 捕获帧裁剪改为复用 bitmap/canvas，减少每帧分配导致的 GC 卡顿。
- APK 文件名为 `ParallelEyeConverter-v0.1.5-debug.apk`。

说明：如果系统录屏授权时选择了“仅录某个应用”，切到其他 App 后系统本身可能不给画面。现在不会在点悬浮球时强制重新授权；需要切换录制目标时，从主界面重新开始转化即可。
