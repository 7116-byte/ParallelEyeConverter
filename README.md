# 实时平行眼转化 / ParallelEyeConverter

这是实时转化版平行眼 App 的源码项目，和静态图库项目 `ParallelVrGallery` 分离。

## 当前能力

- 使用 Android `MediaProjection` 请求屏幕录制授权。
- 使用前台服务持续捕获屏幕帧。
- 使用悬浮窗把当前帧输出为左右眼 SBS 画面。
- 主界面提供“检查更新”按钮：
  - 查询 `https://api.github.com/repos/7116-byte/ParallelEyeConverter/releases/latest`
  - 如果发现新版本，优先打开 Release 里的 `.apk` 下载地址。
  - 如果没有 APK 资产，则打开 Release 页面。

## 构建

项目使用 Android Gradle Plugin 8.7.3、Kotlin 2.0.21、原生 Android View。

```powershell
$env:JAVA_HOME='D:\AndroidBuild\jdk17\jdk-17.0.19+10'
$env:ANDROID_SDK_ROOT='D:\Android\Sdk'
$env:GRADLE_USER_HOME=(Resolve-Path '.').Path + '\.gradle-user'
D:\AndroidBuild\gradle\gradle-8.10.2\bin\gradle.bat assembleDebug
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub 发布约定

默认仓库：

```text
https://github.com/7116-byte/ParallelEyeConverter
```

发布新版时，在 GitHub Release 上传 APK。App 内检查更新会根据 Release tag 和当前 `versionName` 对比：

- 当前版本：`v0.1.0`
- APK 文件名不限，只要 Release asset URL 以 `.apk` 结尾即可。

## 下一步

- 接入实时深度推理。
- 将当前左右眼复制渲染升级为深度视差渲染。
- 加入深度平滑、边缘补洞、视差范围和性能档位。
- 加入悬浮窗控制按钮和旋转稳定逻辑。
