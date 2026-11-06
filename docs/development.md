# 本地开发与恢复环境

正式根目录：`E:\AI-Programme\ParallelEyeConverter`。源码、Gradle Wrapper、`.git/`、
原 `.codex/AGENTS.md` 和全部发布说明直接位于此处。脚本按自身位置计算路径，项目路径须为纯 ASCII。
旧 README 已修正；原件存于 `.local/history/project-baseline/README.md`，Git 历史也完整保留。

## 目录与工具版本

| 位置 | 用途 |
| --- | --- |
| `.local/tools/jdk17/jdk-17.0.19+10/` | 备份 Temurin JDK 17.0.19+10 |
| `.local/tools/android-sdk/` | 备份 SDK；编译 android-35，含 android-36、Build Tools 34/35、platform-tools |
| `.local/gradle-home/` | Gradle 8.10.2 分发、离线依赖和后续缓存 |
| `.local/project-cache/` | 当前项目生成的 Gradle 元数据 |
| `.local/android-user/` | 项目本地 Android 用户配置位置 |
| `.local/private/` | 原 debug.keystore 和 signing.properties；仅当前用户及 SYSTEM 具有继承访问权 |
| `.local/original-apk/` | 原 v0.1.25 debug APK |
| `.local/history/` | 原 RESTORE.md、旧脚本、SHA256SUMS.csv、旧项目 .gradle、修改文件原件及原 Git index |
| `.local/recovery/` | 全量迁移对照表、摘要、原证书、构建/Lint 日志和逐次验收结果 |

固定版本：AGP 8.7.3、Kotlin 2.0.21、TensorFlow Lite 2.16.1、Gradle 8.10.2、JDK 17；
compileSdk/targetSdk 35、minSdk 26。恢复没有升级版本。
原备份支持离线 assemble 和 test 任务，但缺少 Lint 的部分 JAR；已按 AGP 原有 Lint 31.7.3
及其固定传递依赖联网补齐到项目缓存，随后用离线验收复核。

## 构建与验收

使用本机 PowerShell 7（本次 7.6.5）和 Git for Windows（本次 2.53.0）。
不依赖 Android Studio、Python、其他项目工具、备份盘或全局 JAVA_HOME。

```powershell
# 默认离线构建
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1

# 在当前 PowerShell 会话执行多个任务
& .\scripts\build.ps1 -Tasks @('clean', 'testDebugUnitTest', 'assembleDebug')
& .\scripts\build.ps1 -Tasks @('lintDebug')

# 将来新增依赖时，明确选择联网；不会自动升级依赖
& .\scripts\build.ps1 -Online -Tasks @('assembleDebug')

# 当前工作区的一次完整离线构建和 Lint
& .\scripts\build.ps1 -Tasks @('clean', 'testDebugUnitTest', 'assembleDebug', 'lintDebug')

# 采集停帧恢复的独立 JVM 回归检查（不连接设备）
& .\scripts\test-capture-recovery.ps1

# 手动使用 SDK/Gradle 时启用本地环境
. .\scripts\env.ps1
```

脚本只修改当前进程环境，不改用户/系统环境变量。`env.ps1` 生成被忽略的 local.properties，
固定本项目 SDK；遇到已有不同配置会停止，避免静默覆盖。
构建默认 `--offline --no-daemon --max-workers=1`，显式禁用 Java toolchain 自动发现与下载，
仅使用项目 JDK。自动脚本不包含 adb、安装、录屏、悬浮窗启动、发布或删除备份命令。

原始恢复脚本 `scripts/accept-restore.ps1` 的历史验收输出为
`.local/recovery/runs/<时间>/result.json`，当次结果另存 `.local/recovery/latest.json`。
脚本核对所有迁移资产：原依赖 JAR 与 Gradle 分发、JDK、SDK、模型、原 APK、Git 对象和恢复资料
必须保持 SHA-256；构建日志、锁、访问时间索引等可变缓存另列。修改过的 README、构建配置、
忽略规则与 Git index 具有经过原始哈希校验的归档原件。

初次恢复的 `RecoveryVerified=true` 仅表示恢复完整性、签名和独立构建已验证；
当次 `LintPassed=false` 保留原源码的 3 个错误、25 个警告。该历史结果和原始哈希基线不改写。
随后本次 Lint 修正添加三处最小作用范围的误报例外，离线 lintDebug 已成功，结果为 0 错误、25 警告；
未关闭整类检查，未添加 lint-baseline，也未改动 API 分支、权限或运行逻辑。
当前结果位于 `.local/verification/latest-lint-fix.json`，详细分析及证据见恢复验收记录的后续修正章节。
testDebugUnitTest 的 NO-SOURCE 表示没有测试用例，不是运行态验证。

恢复脚本固定检查 v0.1.25 的 HEAD 和 21 个原始运行文件的字节哈希。
当前三处源码的注解/注释已变化，旧脚本会按设计报告哈希不匹配，不应再用于当前工作区验收。
后续提交、修改注解、功能或版本后使用 build.ps1、正常测试和新增的回归检查；
不要为通过恢复基线验收而回退用户的新改动。

## 原签名与 GitHub

历史发行物是 debug APK。`app/build.gradle.kts` 显式绑定 `.local/private/debug.keystore`，
从同目录 signing.properties 读取 storePassword、keyAlias、keyPassword。
缺文件即停止，不会自动生成或回退到全局密钥。
不要执行 `.local/history/restore-build.ps1`，其旧行为会恢复用户全局 debug.keystore。
本机全局密钥与本项目不同，本次保持原样。

原密钥文件 SHA-256：

```text
1B6DEA3917FA96CC8F361596337DC559641CB8F3ECF6B6E712AC130F9A14E0C4
```

原 APK 与原密钥导出证书的实际 SHA-256：

```text
C51C08290903E65D2E99FE35CEFFD44B637F04CC5BBDD5EA9ECB8847D4EA7359
```

原 RESTORE.md 的证书摘要只有 63 位，漏写一个 D；以上通过 apksigner 以及原密钥导出的 DER
证书 SHA-256 交叉确认，不以该笔误作为签名依据。

`.local/`、密钥扩展名、APK、构建输出和 local.properties 均被 Git 忽略。
不要 `git add -f .local`，不要上传缓存、私钥、私密资料。
初次恢复时仅查询公开远端 refs，未找到 gh，Git Credential Manager 的 GitHub 账号列表为空。
2026-11-05 用户授权推送及发布后，已恢复经官方校验和验证的项目本地 GitHub CLI，
复用系统 keyring 中既有的 7116-byte 登录，并确认本仓库 ADMIN 权限；没有重新登录或读取/打印 Token。
CLI 路径记录在被忽略的 `.local/releases/github-cli-path.txt`。旧全局凭据助手引用 D 盘路径，
发布操作使用本次 Git 命令的临时 helper 覆盖，不修改用户全局凭据配置。
v0.1.26 / versionCode 27 的发布前检查见 `docs/releases/v0.1.26.md`。
v0.1.27 / versionCode 28 的采集恢复发布检查见 `docs/releases/v0.1.27.md`。

## 源码结构与行为

- MainActivity：权限入口、深度参数、GitHub 更新检查、CURRENT_VERSION 显示。
- StereoLayout：中心距范围约束及对称中心位置计算；MainActivity 滑杆通过 AppSettings 保存比例，
  ConverterOverlayService 在普通分屏和深度 3D 的统一绘制路径中应用。使用方式和检查见 `docs/center-distance.md`。
- CapturePermissionActivity：MediaProjection 请求；成功后开始本次目标学习，启动采集及悬浮服务。
- ConverterProjectionService：前台 mediaProjection 服务、授权回调、半分辨率 ImageReader、尺寸重建及帧看门狗。
- CaptureRecovery：无首帧/停帧超时、重试间隔和可见性/停止状态；同尺寸强制换 Surface 的修正及验证见 `docs/capture-recovery.md`。
- FrameBus / DepthBus：图像和深度结果及版本信息。
- ForegroundAppHelper：基于使用情况授权，学习本次授权后的第一个可启动目标，排除自身、桌面和临时系统页；
  重入分屏时按需拉起目标，退出时清空，不是跨授权永久记忆。
- ConverterOverlayService：悬浮球、分屏重入、方向变化、控制条；左滑打开右侧 64dp 透控通道，右滑关闭。
  中线与通道边线均为 3 物理像素；StereoFrameRenderer 按深度和视差绘制左右眼。
- ConverterTileService：通知栏快捷入口；API 34+ 使用 PendingIntent，旧系统路径保留 Intent。
- DepthProcessor：assets.openFd + FileChannel.map 模型映射、CPU 4 线程、输入/输出缓冲与位图复用；
  保留推理间隔、分辨率尝试及原生尺寸回退、Depth Anything 失败时 MiDaS 回退等原行为。

两个在用深度模型在 APK 中必须为 Stored（未压缩），保持 noCompress 规则。
第三个 mediapipe_selfie.tflite 也保留；验收检查三个模型源文件及原/新 APK 中的哈希、尺寸和压缩方式。
模型哈希固定在 `scripts/restore-baseline.json`，来自原备份清单，不由新构建结果反推。
初次恢复时共 21 项运行文件（Manifest、11 个 Kotlin 文件、资源及三个模型）逐字节原样保留；
2026-10-31 的 Lint 修正仅改变其中 Manifest、ConverterTileService.kt 和 MainActivity.kt 的构建期规则标记与注释，
其他 18 项哈希仍与原始基线一致。33 个编译类的方法指令/描述符对照一致，打包 Manifest
除源码行号外一致；模型、原签名、包名和版本保持原样。这些静态核对不能替代真机验证。

2026-11-05 开始的中心距功能迭代新增 StereoLayout，并修改 AppSettings、MainActivity、
ConverterOverlayService 和字符串资源；已属于功能改动，不再声称当前代码与恢复基线逐字节或指令一致。
原始恢复与 Lint 修正证据保持不变，当前功能单独记录于 `docs/center-distance.md`。

3D 闪退仍待设备日志定案。另行授权真机任务后，保存机型/Android 版本、授权流程、模型/分辨率/性能档、
复现步骤和完整 logcat；必要时补充 native crash/tombstone 信息，不能仅凭异常提示或构建成功判定原因。
设备验证矩阵和删除条件见 `docs/restore-acceptance.md`。

## 后续版本规则

恢复阶段不增加提交、不改版本。以后发布同步更新 app/build.gradle.kts 的 versionName 和递增
versionCode、MainActivity.CURRENT_VERSION、README、release-notes-v<版本>.md，
沿用 ParallelEyeConverter-v<版本>-debug.apk 命名及原签名。发布前核对 APK 实际元数据、证书、
模型和 SHA-256，发布与设备操作需另行明确授权。
