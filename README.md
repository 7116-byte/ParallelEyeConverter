# ParallelEyeConverter

Android 实时平行眼转换应用。源码与 `.git/` 直接位于正式开发目录
`E:\AI-Programme\ParallelEyeConverter`；本机工具、离线依赖、原签名和恢复资料位于 Git 忽略的 `.local/`。

## Features

- Uses Android `MediaProjection` for screen capture.
- Runs capture in a foreground service.
- Shows the captured frame as left/right parallel-eye output in an overlay.
- Supports a floating ball for minimizing and reopening the converter.
- Supports a saved center-distance slider from half to two-thirds of the split-screen width, for both 2D and 3D.
- Supports optional real-time depth-based 3D conversion with MiDaS or Depth Anything V2.
- Provides GitHub Release based update checks from inside the app.

## Build

```powershell
# 默认使用项目本地环境，全离线构建
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1

# 在当前 PowerShell 会话中运行离线构建和 Lint
& .\scripts\build.ps1 -Tasks @('clean', 'testDebugUnitTest', 'assembleDebug', 'lintDebug')
```

Build output:

```text
app/build/outputs/apk/debug/ParallelEyeConverter-v0.1.27-debug.apk
```

## Release

Repository:

```text
https://github.com/7116-byte/ParallelEyeConverter
```

Current version: `v0.1.27` (versionCode `28`)

包名：`com.local.paralleleyeconverter`。debug 构建显式使用 `.local/private/` 的原签名，
不修改用户全局密钥。从 GitHub 克隆源码后仍需另行恢复这些本机资产，尤其是原签名。

Release assets should use a distinct APK name such as:

```text
ParallelEyeConverter-v0.1.27-debug.apk
```

环境、命令与架构见 [开发说明](docs/development.md)。实际证据、已知 Lint 问题、待真机验证项和
备份删除条件见 [恢复验收记录](docs/restore-acceptance.md)。恢复阶段的原始记录完整保留。
恢复构建成功不代表 3D 闪退已修复；本次未进行设备安装或运行态验证。

原恢复的 3 处 Lint 误报已通过局部规则例外处理，目前离线 Lint 为 0 错误、25 警告。
`scripts/accept-restore.ps1` 保留为原始恢复源码的严格字节验收脚本，当前源码注解已变化，
不再用它验收当前工作区，也不重写原始哈希基线。

中心距：在主界面拖动“中心距”滑杆，可从 50%（左右中心 1/4、3/4）调到约 66.7%
（左右中心 1/6、5/6）；支持恢复默认并记忆设置。画面保持原有大小，向外移动后按各自半屏裁切。
功能及验证说明见 [中心距说明](docs/center-distance.md)。
v0.1.27 增强采集停帧和切换后的恢复，补充录屏停止时的重新授权入口；
说明见 [采集恢复修正](docs/capture-recovery.md) 和 [发布说明](release-notes-v0.1.27.md)。
