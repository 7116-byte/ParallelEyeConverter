# 中心距调节

在主界面的“中心距”区域拖动滑杆，可调整左右画面中心之间的水平距离，点击“恢复默认中心距”返回初始位置。
设置保存在原有 converter_settings 偏好配置的新键 center_distance_ratio 中，重入分屏及重启应用后继续使用。

| 设置 | 左画面中心 | 右画面中心 | 两中心之间的距离 |
| --- | --- | --- | --- |
| 默认 50% | 横向 1/4 | 横向 3/4 | 分屏宽度的 1/2 |
| 中间约 58.3% | 横向 5/24 | 横向 19/24 | 分屏宽度的 7/12 |
| 最大约 66.7% | 横向 1/6 | 横向 5/6 | 分屏宽度的 2/3 |

两个中心始终围绕分屏中点对称。滑杆提供 101 个位置，支持触摸及系统原生滑杆的无障碍操作。
该设置同时用于普通双眼分屏和深度 3D；它改变画面摆放位置，原有缩放、显示模式、视差和深度推理继续保留。
按用户要求，保持画面大小向外平移，超出各自半屏的部分直接裁切，不为扩大间距而缩小画面。

宽度指当前分屏绘图区的宽度；打开原有右侧 64dp 透控通道后，以剩余绘图区为准。
旋转和窗口尺寸变化时根据当前宽度重新计算中心位置。左右半屏的裁切边界、3 物理像素中线/通道边线保持原样。
渲染服务每帧读取当前设置，返回分屏即可按已保存的值绘制，不重建深度解释器。

## 实现位置

- StereoLayout.kt：纯 Kotlin 几何计算；限制比例在 1/2～2/3，异常浮点值返回默认。
- AppSettings.kt：保存/读取新设置，未配置时保持原有 1/4、3/4 的中心位置。
- MainActivity.kt 与 strings.xml：滑杆、当前百分比、范围说明及恢复默认按钮。
- ConverterOverlayService.kt：普通分屏和深度 3D 均使用同一组中心坐标，继续在原半屏边界内绘制。

功能发布版本为 v0.1.26 / versionCode 27；此前下方功能验证记录针对 v0.1.25 的开发构建。
本次发布不升级依赖、不操作手机、不删除备份；原始恢复的 SHA-256 基线及证据不改写。
发布说明见 [release-notes-v0.1.26.md](../release-notes-v0.1.26.md)。

## 2026-11-05 本机验证

结果：[本次 result.json](../.local/verification/center-distance-20261105-013012-143/result.json)，
最近结果另存 [latest-center-distance.json](../.local/verification/latest-center-distance.json)。

- 使用 scripts/build.ps1 执行 clean、testDebugUnitTest、assembleDebug、lintDebug，默认全离线，
  49 个任务执行成功；调整分数字符的资源文案后，再运行 assembleDebug、lintDebug 验证最终 APK。
- 最终 Lint 为 0 错误、25 个原有警告，没有为本功能新增 suppression。
- 用项目 JDK 对实际编译的 StereoLayout 运行独立 Java 几何检查，共 1,233 项断言通过：
  默认/中间/最大位置、对称性、向外移动的单调性、奇数宽度、透控后的绘图区尺寸、零宽度、
  越界比例及 NaN/无穷值回退。源文件 GeometryCheck.java、运行结果 geometry-check.txt 存于本次证据目录。
- 原有 Gradle 单元测试任务仍为 NO-SOURCE；上面的几何检查是独立 JVM 检查，不能当作已完成 Android UI 或设备测试。
- 包名 com.local.paralleleyeconverter、版本 0.1.25 / 26、原签名证书均验证一致；
  三个模型的源文件和 APK 内 SHA-256、尺寸、Stored 未压缩方式均符合原始基线。
- 原签名密钥、用户全局密钥、依赖/签名构建配置、Manifest、采集服务、DepthProcessor 和
  StereoFrameRenderer 文件哈希与本次修改前一致。实际 APK 的权限及组件也与基线一致（忽略源码行号）。
- APK 已归档到本次证据目录的 ParallelEyeConverter-v0.1.25-debug.apk；SHA-256：
  `CD6405C7A0714D2D7B4309F77DF172C01A86FEC49DBCCD7B70AE44DF590D621A`。

待另行授权的真机验证：主界面滑杆触摸/无障碍操作、恢复默认、退出重启后的设置记忆、
普通分屏/两种深度模型下的中心位置与外侧裁切、两种显示模式/缩放/横竖屏及透控通道配合。
本次未安装或操作手机。既有 3D 闪退仍未定因，不能由本次构建或几何检查宣称已解决。
