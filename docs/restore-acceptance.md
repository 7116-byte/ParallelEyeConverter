# 重装恢复验收记录

验收日期：2026-10-31（Asia/Shanghai）。正式根目录：`E:\AI-Programme\ParallelEyeConverter`。
原备份：`U:\备份\ParallelEyeConverter_重装恢复`；基线 v0.1.25。

**本机恢复验收完成，可以在新目录独立离线构建并继续迭代。运行态验收尚未完成，当前不能据此删除原备份。**
本次没有删除备份、安装手机、启动录屏或悬浮窗，也没有创建提交、推送或发布版本。

## 后续功能迭代（2026-11-05）

已按用户要求新增中心距调节，范围为当前分屏绘图区宽度的 1/2～2/3，保存设置，
普通分屏与深度 3D 共用；保留画面大小，向外平移并按各自半屏裁切。
当前源码已包含功能变化，下文的原恢复/Lint 指令一致性结论均属于对应日期的历史记录。
本次离线构建、Lint 和 1,233 项独立几何断言通过，Lint 为 0 错误、25 个原有警告。
实际证据及待真机验证项见 [中心距功能说明](center-distance.md)。原恢复基线和旧验收结果不改写。

## 后续 Lint 修正验收（2026-10-31 14:19–14:20）

按用户后续授权分析并处理三处错误，保持 v0.1.25 / versionCode 26，不升级依赖、不改变运行行为。
**当前离线构建与 lintDebug 均成功，Lint 为 0 个错误、25 个警告。** 没有操作手机、发布或删除备份。
详细结果：[本次 result.json](../.local/verification/lint-fix-20261031-141930-694/result.json)，
另存 [latest-lint-fix.json](../.local/verification/latest-lint-fix.json)。原恢复记录与哈希清单完整保留。

### 分析与处理范围

这三处是固定 Lint 31.7.3 对现有兼容/特殊授权路径的误报，处理方式是有依据的局部规则例外，
不是修改运行逻辑，也不是修复已证实的运行态崩溃。保留权限和 API 分支，避免破坏现有功能。

| 错误 | 核对依据 | 本次改动 |
| --- | --- | --- |
| StartActivityAndCollapseDeprecated | 原代码在 SDK >= 34 使用 PendingIntent，Intent 只在 SDK < 34 执行；当前 Lint 检测器只按 targetSdk 判定，没有检查 SDK 分支 | 仅旧系统调用表达式添加该 ID 的 Kotlin Suppress 与解释，保留原 DEPRECATION 标记和全部分支 |
| UnspecifiedRegisterReceiverFlag | SDK >= 33 已使用 RECEIVER_NOT_EXPORTED，旧注册仅在 SDK < 33 执行；固定检测器不分析该条件 | 仅旧系统 registerReceiver 表达式添加该 ID 的 Kotlin Suppress 与解释，不改变接收器导出语义、过滤条件或生命周期 |
| ProtectedPermissions | PACKAGE_USAGE_STATS 包含 appop；官方允许用户从设置授予 Usage Access。项目已有设置页入口及 AppOps 检查，而固定检测器仅按权限名单报错 | 只在该 uses-permission 元素添加 tools:ignore 与理由，保留权限声明；构建时 tools 属性移除 |

官方依据：[TileService API](https://developer.android.com/reference/android/service/quicksettings/TileService#startActivityAndCollapse(android.content.Intent))、
[PACKAGE_USAGE_STATS](https://developer.android.com/reference/android/Manifest.permission#PACKAGE_USAGE_STATS)、
[Context.registerReceiver](https://developer.android.com/reference/android/content/Context#registerReceiver(android.content.BroadcastReceiver,android.content.IntentFilter,int))。
还直接核对了本机固定版本 lint-checks-31.7.3.jar 的三个检测器；反汇编分别保存为
TileServiceActivityDetector.txt、RegisterReceiverFlagDetector.txt、SystemPermissionsDetector.txt。
该 JAR 的 SHA-256 为 `6C285320DADD7D590D89030227C766D752BFEA1069976EF25D35A0FE15C1A6BF`。
没有新增依赖、修改 targetSdk、关闭整类 Lint 规则或生成 lint-baseline。

### 实际验证

执行命令（项目本地 JDK、SDK、缓存，默认带 --offline）：

```powershell
& .\scripts\build.ps1 -Tasks @('clean', 'testDebugUnitTest', 'assembleDebug', 'lintDebug')
```

- BUILD SUCCESSFUL，27 秒，49 个任务执行；原始日志 offline-build-lint.log。
- Lint：0 错误、25 警告；完整 XML/TXT/HTML 保存在本次证据目录，25 个警告没有被屏蔽。
- testDebugUnitTest：仍为 NO-SOURCE，不声称功能用例已通过。
- 21 项基线运行文件中，仅三处预期文件因规则注解/注释变化；其他 18 项 SHA-256 完全一致，
  详见 runtime-file-hashes.csv。原始清单、私钥、全局密钥、构建配置、版本和规则文件未改变。
- 修复前的 33 个编译类先按原恢复最终清单核验，再对照修复后同一组类的 javap -p -c -s 输出；
  方法指令和描述符完全一致。两份反汇编文本 SHA-256 均为
  `CBB874018F7BCDB65C7C87636625F9BA0A6A6D0356BBC67FAC0D2107CAC1B816`。
  证据为 bytecode-before.txt、bytecode-after.txt、bytecode-comparison.json。
- 新 APK 的签名、包名、版本、三个模型的哈希与未压缩方式、4 字节 zipalign 检查通过。
  原/新 Manifest 的 aapt xmltree 输出仅源码行号不同，权限和组件等内容一致。
- 当前新 APK 为 132,493,282 字节，SHA-256 为
  `B15FEF9E027FFA71720EA4CF434F4B1FA2FE5167C3C129A5703CC1500C4E491E`。
  它已因源码行号等构建元数据变化而不同于原 APK，不能继续宣称当前 APK 与原包字节相同。
  原 APK 在 .local/original-apk 中未改动；当前 APK 使用同一原证书。
- Git refs 未改变；未提交、推送、发布或连接设备。3D 闪退仍待日志定案，备份删除仍不允许。

当前日常验收使用 build.ps1 的上述构建/Lint 任务。
accept-restore.ps1 和 restore-baseline.json 保留为初次恢复的严格字节基线；
当前三处注解变化会使旧脚本按设计报哈希不匹配，不重写原始哈希以掩盖变更。

## 初次恢复实际验收结果（历史记录）

完整自动结果：[`result.json`](../.local/recovery/runs/20261031-141341-199/result.json)，
开始 14:13:41，结束 14:15:02（+08:00）；该阶段结果另存 [latest.json](../.local/recovery/latest.json)，
此文件保留原恢复结论，不代表上方后续 Lint 修正的当前结果。
全部细节保存在 Git 忽略的 `.local/recovery/`，不能用这份摘要替代原始证据。

| 项目 | 结论和证据 |
| --- | --- |
| 目标已有内容 | 开始时正式根目录为空；迁移逐文件遇同名先比较 SHA-256，不同则停止，不覆盖 |
| 备份清单 | SHA256SUMS.csv 共 28,426 项、2,359,629,862 字节，全部尺寸与 SHA-256 符合；source-verification.csv |
| 额外文件 | 另外 9 项为清单自身及 8 个旧项目 .gradle 元数据，也完整复制并核对哈希 |
| 全量迁移 | 28,435 个文件、2,364,525,014 字节，目标逐项 SHA-256 一致；migration-files.csv、migration-summary.json |
| 目录结构 | 10,068 个原子目录已映射保留，含空 .github/workflows；migration-directories.csv |
| Git 基线 | HEAD/main/origin/main 均为 75517a23d5e9a551dfe7a568373f6838498653a1；1 个本地分支、25 个标签、28 个 refs 全部一致，含原 checkpoint |
| Git 完整性 | git fsck --full 退出 0；有原有 dangling tree，不是缺失或损坏对象，未执行 gc/prune |
| 原工作区 | 备份无已跟踪修改，仅 .codex/ 未跟踪；当前保留该状态与规则文件，另有本次恢复修改和新增文件 |
| 运行源码 | Manifest、11 个 Kotlin 文件、资源和模型共 21 项与备份逐字节哈希一致，未改功能 |
| 构建环境 | 项目 .local 内 JDK 17.0.19+10、SDK、Gradle 8.10.2；local.properties 与 toolchain 指向当前根目录 |
| 干净离线构建 | clean testDebugUnitTest assembleDebug --offline 成功，39 个任务执行；offline-build.log |
| 现有测试 | testDebugUnitTest 为 NO-SOURCE；基线没有测试用例，不能声称已通过单元功能测试 |
| Lint | 原缓存缺少 Lint JAR，按原版本联网补齐；最终离线运行到检查阶段，仍报告 3 错误、25 警告；无 suppress/关闭检查 |
| 原/新 APK | 包名、版本、证书、完整签名、模型、4 字节 zipalign 验证通过；整个 APK 的 SHA-256 完全相同 |
| 资源包装 | 两个在用深度模型及第三个原有模型，源文件与原/新 APK 内 SHA-256 和尺寸均一致，均为 Stored 未压缩 |
| Manifest 包装 | 原/新 APK 的 aapt xmltree 输出完全一致，权限和组件均保留 |
| 原私钥 | 原 debug.keystore 的 SHA-256 符合备份；显式放于 .local/private，私密目录限制 ACL，配置缺失即停止 |
| 全局密钥 | 原本存在不同全局调试密钥，本次前后 SHA-256 一致，未复制覆盖或替换 |
| 构建后资产审计 | 28,435 项中 28,120 项完全不变、4 项原件保存在 history、311 项为可变构建缓存；意外变化 0 |
| Git 忽略 | .local 与密钥、APK、local.properties 被忽略；Git 跟踪列表无私密/生成资产 |
| GitHub | 匿名可读远端 main 与 v0.1.25 标签均指向基线；未找到 gh，GCM GitHub 账号列表为空，未验证登录/推送权限 |

构建后 4 项归档原件为 README.md、.gitignore、app/build.gradle.kts 和 Git index。
311 项变化属于 Gradle 日志、访问索引、锁及派生缓存；原模块依赖文件、Gradle 分发、全部 JDK/SDK、
源码模型、原 APK、签名、技术资料和 Git 对象均受严格哈希检查，不被归入可变缓存豁免。

## 初次恢复 APK 与证书证据（历史记录）

原 APK 和初次恢复新构建 APK 均为 132,493,278 字节，包名 `com.local.paralleleyeconverter`，
versionName `0.1.25`，versionCode `26`，minSdk `26`，targetSdk `35`，debug APK。
apksigner 验证通过，1 个签名者，APK Signature Scheme v2。

两份 APK 的完整 SHA-256：

```text
1F15089B73C8D5FAFEEF36763C60B1FC0CB075091FA5D43E931781941FE75443
```

原密钥文件 SHA-256：

```text
1B6DEA3917FA96CC8F361596337DC559641CB8F3ECF6B6E712AC130F9A14E0C4
```

实际原/新 APK 签名证书，以及从原密钥导出 DER 的 SHA-256：

```text
C51C08290903E65D2E99FE35CEFFD44B637F04CC5BBDD5EA9ECB8847D4EA7359
```

原 RESTORE.md 记成 `C51C08290903E65D2E99FE35CEFFD44B637F04CC5BBD5EA9ECB8847D4EA7359`，
只有 63 位，漏写一个 D。首次自动验收因此停止；交叉核对原 APK 和原密钥后修正了验收基线，
没有修改原密钥或原 APK，也未改动原备份资料。
保留的证据包括 certificate-document-discrepancy.json、original-debug-cert.der、
original-apk-signature.txt 和首次失败的 runs/20261031-141231-700/result.json。

| 模型 | 字节数 | SHA-256（源文件及两份 APK 内一致） |
| --- | ---: | --- |
| depth_anything_v2.tflite | 98,920,480 | B407F34F61750F31441E6F858A4BC48D8572F9EE5399FFD015CEE5FA1767083F |
| midas-v2-w8a8.tflite | 17,749,000 | 874A4F48DED1047ED330CAA1230CE70F698B73987618AF57B22553B232814D81 |
| mediapipe_selfie.tflite | 231,904 | 3494786FFF8FDD0907FC3D067F3CF92493BCDCD5C4F85CE8FF9A079DA4FB658B |

## 初次恢复已知检查限制（历史记录）

初次恢复时原代码的 Lint 错误（当次未修复、未压制；后续处理见本文首节）：

| 检查 ID | 原源码位置 | 后续处理 |
| --- | --- | --- |
| StartActivityAndCollapseDeprecated | ConverterTileService.kt:39 的旧系统 Intent 分支 | 结合 API 34 分支与实际系统验证，恢复阶段保留原行为 |
| ProtectedPermissions | AndroidManifest.xml:8 的 PACKAGE_USAGE_STATS | 结合 Usage Access/AppOps 授权路径验证，不能仅凭该提示删权限 |
| UnspecifiedRegisterReceiverFlag | MainActivity.kt:387 的旧系统 registerReceiver 分支 | 结合 API 33 分支与实际更新广播验证，另行迭代处理 |

完整 3 个错误、25 个警告及原始消息见该次验收目录的 lint-results-debug.xml/txt/html 和 offline-lint.log。
这些静态报告不能单独证明设备一定崩溃，也不能作为已修复的证据。
自动结果 RecoveryVerified=true 与 LintPassed=false 分开记录，DeviceValidation=PENDING、
BackupDeletionAllowed=false。原恢复脚本允许已记录且完全匹配的历史 Lint 基线，
`-RequireCleanLint` 当时会以失败退出；历史失败结果保留，当前 lintDebug 成功结果另存，不混用。

## 待另行授权的真机验证

以下均未执行，不得填写为通过。后续应保存设备型号、Android 版本、APK 哈希、时间、步骤、
实际结果和日志路径；覆盖安装和运行前须获得新的明确设备任务授权。

| 验证项 | 必须记录的结果 |
| --- | --- |
| 原安装覆盖更新 | 同包同证书覆盖安装成功、应用数据及设置保留；不能仅以包名/证书静态一致替代安装结果 |
| MediaProjection | 接受/拒绝授权、系统撤销授权、重入授权、旋转重建与停止释放 |
| 前台服务与悬浮窗 | 通知显示、悬浮权限入口、悬浮球拖动/隐藏/重入、退出与资源释放 |
| 通知栏快捷开关 | 不同权限状态的入口流程、重新录屏授权、API 分支实际行为 |
| 目标应用记忆 | 首个目标学习、桌面/系统页排除、重入拉起、本次授权边界及退出清空 |
| 透控和左右眼 | 左滑打开右侧 64dp 通道、右滑关闭、3 物理像素边线、双眼绘制、缩放与旋转 |
| 深度与 3D 闪退 | MiDaS / Depth Anything、原生/360/480/720 分辨率、性能档、切换/重入/持续运行及完整 logcat；必要时 native crash 日志 |
| 更新检查流程 | 查询/下载/广播回调在相应 Android 版本的行为，避免把本次 GitHub refs 查询当成 App 测试 |

**3D 闪退根因仍未通过设备日志定案。** v0.1.25 的模型 mmap、CPU 推理和缓冲复用是原有行为，
初次恢复仅验证相同 APK；后续 Lint 处理也未进行真机验证，不能声称解决了闪退。

## 将来删除原备份前的条件

当前结论为 **保留原备份**。以下条件满足并由后续任务确认之前，不得删除 U 盘恢复包：

1. 保留本次原始证据与完整迁移映射；再次核对不可再生资产：原私钥/私密配置、原 APK、三个模型、
   完整 .git（含 refs、reflog、checkpoint 和原有悬空对象）、.codex 规则、发布说明、恢复资料，不能只备份 GitHub 可见源码。
2. 将新目录的源码、未提交修改、未跟踪规则/文档，以及必要的 .local 工具、离线依赖、签名、历史资料、
   验收记录备份到独立且受保护的介质。保留可校验清单，实际读取并验证恢复；同一 E 盘中的额外副本不算独立备份。
   私钥须有独立安全副本，不能依靠公开 GitHub 仓库保存。
3. 在不使用 U 盘、其他项目工具和联网依赖下载的条件下，从已保护的新副本演练环境恢复和干净离线构建，
   验证包名、版本、实际证书、模型和哈希，并保留新的结果。本次已证明当前新根目录可离线构建，
   但没有断开 U 盘或进行独立介质还原演练。
4. 完成上述真机覆盖安装及功能验证，保留结果。3D 若仍闪退，保留日志、现状和后续处置决定，
   明确这是基线已有待处理问题，不能以“恢复后能构建”关闭该问题。
5. 确认本次尚未提交的恢复变更及原未跟踪文件已进入可验证备份；如依靠远端保存源码，
   还需另行验证正常认证与推送。本次未推送，匿名 refs 可读不代表认证已完成。
6. 将待验证项与已知 Lint 技术债处理状态补写进验收记录，明确旧包是否仍有独有内容，
   并由用户在后续删除任务中明确授权最终删除。不得自动删除。

## 证据索引

- source-verification.csv：原清单每项期望/实际哈希及尺寸。
- migration-files.csv / migration-directories.csv / migration-summary.json：源到新根目录的完整映射及未列清单文件。
- backup-git-status.txt / backup-git-refs.txt / git-refs-before.txt / git-fsck.txt：原状态与历史完整性。
- gradle-version.txt：Gradle、Launcher JVM 和 Daemon JVM 实际位置。
- initial-offline-build.log / initial-offline-lint.log / online-lint-bootstrap.log：首次构建、缺失离线 Lint 依赖和补齐后发现。
- runs/20261031-141341-199/：干净离线构建、离线 Lint、原/新 APK 身份/签名/Manifest/ZIP/对齐检查、
  result.json、28,435 项 asset-audit.csv 和汇总 asset-audit.json。
- .local/history/：全部原恢复文件、旧 README/构建配置/忽略规则/规则文件原件、原 Git index、旧 Gradle 元数据。
- scripts/restore-baseline.json：包名/版本/正确证书和 21 项原运行文件哈希，作为可重复验收依据。
- .local/verification/lint-fix-20261031-141930-694/：本次修正前快照、离线构建与 Lint 报告、检测器分析、
  编译指令对照、APK/Manifest/模型验证和 result.json，当前结果另存 latest-lint-fix.json。

脚本与环境使用方式见 [development.md](development.md)，本次正式构建产物位于
`app/build/outputs/apk/debug/ParallelEyeConverter-v0.1.25-debug.apk`。
