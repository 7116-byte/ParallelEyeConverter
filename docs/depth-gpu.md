# GPU 深度设置

设置中的「量化版本」可选择 Depth Anything V2 Small 的 FP16、普通 INT8 和旋转 INT8，也可切回原版 518 CPU 模型。另一个「模型」按钮可以切换 MiDaS。开启「真 3D」后，GPU 选项参与实际推理。应用会保存所选版本和分辨率。

「推理分辨率」的 448、644 和 812 都指模型输入的长边，默认 644。短边按采集画面比例四舍五入到最近的 14 像素倍数，横竖屏交换宽高，无需填充。以 2∶3 竖屏为例，三档分别使用 294×448、434×644 和 546×812。分辨率选择或采集尺寸变化会按新形状创建推理会话。输出尺寸与模型输入一致。

GPU 会话使用 ONNX Runtime Android QNN 1.29.0 和 QNN GPU 2.42.0。初次缓存这两个新增依赖可用 `scripts/build.ps1 -Online`，随后使用默认离线构建。QNN 后端加载失败时继续显示普通 SBS，状态显示 `GPU failed (2D)`。原三份 TFLite 与新增三份 ONNX 都以未压缩方式打包。

## 模型来源

三个版本从同一组 Depth Anything V2 Small 权重导出。Android 图将原来展开的 GELU 合为 GPU 支持的 GELU 算子。两种 INT8 保留各自的 8 位权重和标定范围，GPU 用 FP32 算子执行量化后的图。旋转 INT8 的正交变换发生在注意力层的特征空间，适用于横竖两种画面。 [ONNX 原始权重](https://huggingface.co/onnx-community/depth-anything-v2-small/tree/4472b7362082ad9968fee890ca0f1e5aca36b93d) · [Depth Anything V2 Small](https://github.com/DepthAnything/Depth-Anything-V2) · [QNN 执行后端](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)

本机的原始导出和标定证据位于 `.local/experiments/depth-pc-20261122`。本次使用的 Android 图转换脚本、五张 Middlebury 样张上的 DirectML 对照以及最终 APK 的模型 SHA-256 位于 `.local/verification/gpu-settings-20261122`。PC 对照中，最终 Android INT8 输出相对对应 PC INT8 的平均绝对差约为原输出 5–95 百分位动态范围的 1.65% 至 2.14%。FP16 合并 GELU 后在这组测试中的差异低于 0.00005%。

## 2026-11-22 验收

在连接的小米 25091RP04C 平板上，所有新模式都关闭 CPU 推理回退。下表为同一设备上的独立 QNN GPU 测试，使用合成图像，记录预热后两次推理的中位数。首次创建会话另耗约 2–5 秒。

| 量化版本 | 434×644 | 546×812 |
| --- | ---: | ---: |
| FP16 | 92 ms | 175 ms |
| 普通 INT8 | 326 ms | 597 ms |
| 旋转 INT8 | 326 ms | 596 ms |

横屏 644×434、812×546 和方形 812×812 对三种模型也各跑了三次，输出尺寸正确且深度有效。测试包上实点了三个量化选项和两个分辨率，强制关闭后重开仍保留选择。应用进程内另有 9 项插桩检查，将合成帧送入实际 `DepthProcessor`，逐一确认切换量化、切换分辨率和变换画面方向后，`DepthBus` 能收到尺寸正确的有效深度。验收结束后在平板上装回了无插桩包。真实录屏画面和长时间播放的稳定性留待实际使用验证。

离线 `assembleDebug` 和 `lintDebug` 通过，Lint 为 0 错误、24 警告。现有 `testDebugUnitTest` 为 `NO-SOURCE`。构建继续使用包名 `com.local.paralleleyeconverter`、0.1.29 / 30 和历史签名证书。签名、APK SHA-256、六个模型的未压缩及哈希检查记录位于 `.local/verification/gpu-settings-20261122`。

最终离线重建 APK 与首个 UI 验收包的 31 个 ZIP 条目逐项 SHA-256 一致。最终包已在平板覆盖安装，本地和设备上的 APK SHA-256 均为 `2A6D75B7BD35FAD52396C512BFFBB306C5431F89790CB555967582064301F97E`。

### 448 档补测

补入 448 长边选项后，在 PC 的 RTX 5070 Ti 上用 DirectML 验证了三个量化版本的 294×448、448×294 和 448×448，共 9 种组合，每种检查 5 次。推理禁止 CPU 回退，运行记录中的算子均由 DirectML 执行，输出尺寸正确且深度值有限、非常量。294×448 预热后的中位数为 FP16 3.48 ms、普通 INT8 7.42 ms、旋转 INT8 7.58 ms。另用生产 Kotlin 编译产物核验三个选项及 7 种实际输入形状。

离线构建和 Lint 通过，Lint 为 0 错误、24 警告。普通 APK 为 v0.1.29 / 30，沿用原签名，六份模型仍以 Stored 打包。此次补测以 PC 结果为准，普通 APK 留给用户试装。证据存于 `.local/verification/gpu-448-20261122`，普通 APK SHA-256 为 `6E476777C9D99678D123A16CB85D9731C5C5517540F7DDDC262E68FC52E25964`。
