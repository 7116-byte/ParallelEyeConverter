# ParallelEyeConverter 开发约定

本目录就是正式源码和 Git 根目录。恢复基线为 `v0.1.25`，提交
`75517a23d5e9a551dfe7a568373f6838498653a1`。保留 `.codex/AGENTS.md`
原规则文件及其未跟踪状态；不要清理 Git 历史、分支、标签、checkpoint、未提交内容。

- 先读 `docs/development.md` 与 `docs/restore-acceptance.md`，以实际源码和验收证据为准。
- 使用 `scripts/build.ps1` 的项目本地 JDK、SDK 和 Gradle 缓存；默认离线。
  一键恢复验收：`pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\accept-restore.ps1`。
  该脚本固定核对原始源码字节；当前 Lint 注解修正后应使用 build.ps1 进行正常构建和检查，
  不要改写原恢复基线来让旧脚本通过。
- `.local/` 包含本机私钥、原 APK、工具、离线依赖、原始恢复资料和详细日志，不得提交或上传。
  不要读取或输出登录 Token；不要自动登录、推送、发布或操作设备。
- debug 构建必须显式使用 `.local/private/debug.keystore` 和同目录私密配置。
  禁止回退到、复制到或覆盖 `%USERPROFILE%/.android/debug.keystore`。
- 保持包名 `com.local.paralleleyeconverter`。恢复阶段保持 versionName=0.1.25、
  versionCode=26、MainActivity.CURRENT_VERSION=0.1.25，不升级依赖、不改变功能。
- 后续发布沿用现有方式：同步更新 `app/build.gradle.kts` 中的版本名与递增 versionCode、
  `MainActivity.kt` 中的 CURRENT_VERSION、README、相应发布说明和 APK 名。
  发布及设备操作须由后续任务明确授权。
- 保留 MediaProjection 授权、前台服务、悬浮窗、快捷开关、目标应用记忆、64dp 透控通道、
  3 物理像素边线、左右眼绘制及 CPU 深度推理和缓冲复用行为。
- 两个深度模型 `midas-v2-w8a8.tflite`、`depth_anything_v2.tflite` 必须保持 APK 中未压缩打包，
  保留 `androidResources.noCompress += "tflite"`；还须保留现有 `mediapipe_selfie.tflite`。
- 基线没有单元测试用例，testDebugUnitTest 的 NO-SOURCE 不等于功能已被测试。
  原恢复 Lint 有 3 个错误、25 个警告；当前对两处受 SDK 条件保护的旧系统调用和 Usage Access
  权限声明添加了有依据的局部例外，离线 Lint 为 0 错误、25 警告。不要关闭整类检查或声称真机验证已通过。
- 3D 闪退尚无设备日志定因。构建成功、签名一致或 APK 完全相同都不能证明已修复闪退。
  运行态验收和备份删除条件见恢复验收记录；本次不删除原备份、不安装、不启动录屏或悬浮窗。
