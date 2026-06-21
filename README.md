# ParallelEyeConverter

Realtime parallel-eye converter for Android. This project is separate from the static gallery app `ParallelVrGallery`.

## Features

- Uses Android `MediaProjection` for screen capture.
- Runs capture in a foreground service.
- Shows the captured frame as left/right parallel-eye output in an overlay.
- Supports a floating ball for minimizing and reopening the converter.
- Supports optional real-time depth-based 3D conversion with MiDaS or Depth Anything V2.
- Provides GitHub Release based update checks from inside the app.

## Build

```powershell
$env:JAVA_HOME='D:\AndroidBuild\jdk17\jdk-17.0.19+10'
$env:ANDROID_SDK_ROOT='D:\Android\Sdk'
$env:GRADLE_USER_HOME=(Resolve-Path '.').Path + '\.gradle-user'
.\gradlew.bat assembleDebug --no-daemon '-Dorg.gradle.workers.max=1'
```

Build output:

```text
app/build/outputs/apk/debug/ParallelEyeConverter-v0.1.12-debug.apk
```

## Release

Repository:

```text
https://github.com/7116-byte/ParallelEyeConverter
```

Current version: `v0.1.12`

Release assets should use a distinct APK name such as:

```text
ParallelEyeConverter-v0.1.12-debug.apk
```
