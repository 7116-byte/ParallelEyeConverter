# 上滑后黑屏：采集恢复修正

日期：2026-11-07。修改基于 v0.1.26，发布版本为 v0.1.27 / versionCode 28，由用户明确授权发布；没有操作手机。
用户反馈上滑后左右画面一起变黑，即使应用尚未退出。缺少设备日志，尚不能确认具体手机上的唯一根因。

## 已确认的代码问题

原看门狗在 2.2 秒没有新帧时调用 rebuildCaptureSurface，但函数遇到尺寸未变且 VirtualDisplay 存在便返回。
因此“同尺寸下采集卡住”的自动恢复是空操作；lastFrameTimeMs 必须大于 0 的条件还漏掉了首帧一直未到的情况。
此外，仅监听物理屏幕尺寸，未处理 Android 14+ 的实际被捕获应用区域尺寸和可见性变化。
系统停止 MediaProjection 后，悬浮层仍只提示等待画面，没有说明必须重新授权。

## 本次修正

- 独立的 CaptureRecovery 使用单调时钟判断无首帧、已出帧后停帧、可见性及停止状态，恢复尝试间隔至少 3.5 秒。
- 停帧走 force 路径，即使尺寸不变也替换 ImageReader Surface；继续使用原 VirtualDisplay 的 resize/setSurface，
  不释放并重新 createVirtualDisplay，不复用旧授权创建第二个显示。
- 一次授权最多尝试一次 createVirtualDisplay；首次创建失败后提示重新授权，不在同一授权上反复创建。
- Surface 切换成功后才替换当前 Reader 和缓冲引用并释放旧 Reader；绑定失败时尝试恢复旧尺寸和 Surface，关闭失败候选 Reader。
- Surface 绑定/释放与图像读取使用同一资源锁；Image 在深度推理前释放，推理不持有该锁。
  旧 Reader 或旧会话回调会被忽略，读取失败记录 ParallelEyeCapture 日志，由看门狗有间隔地重试。
- 接入 onCapturedContentResize，按实际捕获区域而非仅物理屏幕重建；保留原半分辨率及最小 320 像素行为。
- 接入 onCapturedContentVisibilityChanged：隐藏时暂停恢复、清除旧图像/深度；重新可见时在原会话内重新绑定 Surface。
  隐藏不等同于授权停止，不会自动重新申请权限。
- 录屏停止后显示“录屏已停止，点按重新授权”。仅用户点击才打开原系统授权页，取消后仍可再次点击；
  空启动 Intent 不会留下无采集能力的粘性前台服务。
- 保留中心距、透控通道、左右眼绘制、原模型和深度推理参数，没有按“黑像素”猜测坏帧或用旧内容覆盖合法黑画面。

平台依据：[MediaProjection 回调](https://developer.android.com/reference/android/media/projection/MediaProjection.Callback)、
[采集尺寸变化与资源回收](https://developer.android.com/media/grow/media-projection)、
[VirtualDisplay.setSurface](https://developer.android.com/reference/android/hardware/display/VirtualDisplay#setSurface(android.view.Surface))。
已停止的 MediaProjection 无法继续使用，必须由用户重新授权；本修正遵循此边界。

## 本机验证

```powershell
& .\scripts\build.ps1 -Tasks @('clean', 'testDebugUnitTest', 'assembleDebug', 'lintDebug')
& .\scripts\test-capture-recovery.ps1
```

所有操作默认离线，使用项目内的 JDK、SDK 和缓存，不新增依赖。
后一个脚本先编译实际生产 CaptureRecovery，再用项目 JDK 运行 tests/CaptureRecoveryCheck.java，避免测试陈旧 class 文件。

- 干净构建成功；资源锁/生命周期复核后的最终 assembleDebug 与 lintDebug 也成功。
- Lint：0 错误、25 个原有警告，没有为本修正增加 suppression。
- 124 项独立 JVM 断言通过，覆盖无首帧、超时边界、失败后的重试间隔、真实新帧重置、隐藏/返回、停止/新授权、
  连续帧、健康同尺寸不重建，以及卡住时同尺寸强制重建的回归条件。
- 原 Gradle 单元测试任务仍为 NO-SOURCE；独立 JVM 检查不模拟 Android 的 MediaProjection、Binder 或图形驱动。
- 原包名/版本、签名、三个模型及未压缩打包规则继续核对；原始恢复基线与原已发布 APK 保留。

本机详细证据：`.local/verification/capture-recovery-20261107-002319-777/`，
最近结果为 `.local/verification/latest-capture-recovery.json`。
归档 APK：[本机修正版](../.local/verification/capture-recovery-20261107-002319-777/ParallelEyeConverter-v0.1.26-debug.apk)，
SHA-256 为 `EB4492D1E7211BBC6CDCCA9F2197EF7832F3AFE6A838D770B3A0A348899F50FC`。
上述归档是发布前的本机开发构建 com.local.paralleleyeconverter / 0.1.26 / 27，历史证据继续保留。
正式发布升级为 v0.1.27 / 28，不替换原 GitHub v0.1.26 资产。
本次发布验收见 [v0.1.27](releases/v0.1.27.md)。

## 仍需真机对应验证

需要分别验证在右侧透控通道滚动、分屏内部上滑、底部系统手势三条路径，记录 Android 版本、目标应用和录屏通知状态。
还应验证目标应用临时不可见后返回、横竖屏/窗口尺寸变化、静止页面、3D 开关、主动停止/系统停止后的提示，
以及点击重新授权与取消授权的行为。

本机测试不能证明已复现或彻底解决这台手机的黑屏。若仍有黑屏，应获取 ParallelEyeCapture 与 AndroidRuntime 日志，
区分停帧、目标不可见、授权停止、受保护内容和持续送来黑帧；本次没有连接设备或抓取日志。
原有 3D 闪退问题仍待独立日志定案，备份删除条件不变。
