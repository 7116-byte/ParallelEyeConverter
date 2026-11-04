# Dot-source this file to use only this checkout's restored Android toolchain.
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($projectRoot -match '[^\x00-\x7F]') { throw 'Android build root must use an ASCII-only path.' }
$env:JAVA_HOME = Join-Path $projectRoot '.local\tools\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME = Join-Path $projectRoot '.local\tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.local\android-user'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.local\gradle-home'
foreach ($required in @(
    (Join-Path $env:JAVA_HOME 'bin\java.exe'),
    (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'),
    (Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat'),
    $env:GRADLE_USER_HOME
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing restored toolchain item: $required" }
}
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + (Join-Path $env:ANDROID_HOME 'platform-tools') + ';' + $env:Path
$sdkProperty = 'sdk.dir=' + $env:ANDROID_HOME.Replace('\', '/')
$localProperties = Join-Path $projectRoot 'local.properties'
if (Test-Path -LiteralPath $localProperties) {
    $existing = Get-Content -LiteralPath $localProperties -Raw
    if ($existing.Trim() -ne $sdkProperty) {
        throw 'local.properties differs from the restored SDK path; inspect and preserve it before changing it.'
    }
} else {
    [IO.File]::WriteAllText($localProperties, $sdkProperty + [Environment]::NewLine, [Text.Encoding]::ASCII)
}
