param([switch]$RequireCleanLint)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$run = Join-Path $root ('.local\recovery\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
[IO.Directory]::CreateDirectory($run) | Out-Null
$baseline = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'restore-baseline.json') -Raw | ConvertFrom-Json
$globalKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
function Get-GlobalKeyHash {
    if (Test-Path -LiteralPath $globalKey) { (Get-FileHash -LiteralPath $globalKey -Algorithm SHA256).Hash } else { 'ABSENT' }
}
function Assert-Equal($Actual, $Expected, [string]$Label) {
    if ($Actual -ne $Expected) { throw "$Label mismatch: expected $Expected; actual $Actual" }
}
function Invoke-Native([string]$Executable, [string[]]$Arguments, [string]$LogName) {
    $output = @(& $Executable @Arguments 2>&1 | ForEach-Object { "$_" })
    $code = $LASTEXITCODE
    $output | Set-Content -LiteralPath (Join-Path $run $LogName) -Encoding UTF8
    if ($code -ne 0) { throw "$Executable failed ($code); see $run\$LogName" }
    $output
}
function Test-Apk([string]$Path, [string]$Label) {
    $tools = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'
    $signature = @(Invoke-Native (Join-Path $tools 'apksigner.bat') @('verify','--verbose','--print-certs',$Path) "$Label-signature.txt")
    $certificates = @($signature | Select-String '^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$')
    Assert-Equal $certificates.Count 1 "$Label signer count"
    $certificate = $certificates[0].Matches[0].Groups[1].Value.ToUpperInvariant()
    Assert-Equal $certificate $baseline.certificateSHA256 "$Label certificate SHA-256"
    $badging = @(Invoke-Native (Join-Path $tools 'aapt.exe') @('dump','badging',$Path) "$Label-badging.txt")
    if ($badging[0] -notmatch "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") { throw "Cannot parse $Label APK identity" }
    $package = $Matches[1]; $versionCode = $Matches[2]; $versionName = $Matches[3]
    Assert-Equal $package $baseline.packageName "$Label package"
    Assert-Equal $versionCode $baseline.versionCode "$Label versionCode"
    Assert-Equal $versionName $baseline.versionName "$Label versionName"
    Assert-Equal ($badging -contains "sdkVersion:'$($baseline.minSdk)'") $true "$Label minSdk"
    Assert-Equal ($badging -contains "targetSdkVersion:'$($baseline.targetSdk)'") $true "$Label targetSdk"
    Assert-Equal ($badging -contains 'application-debuggable') $true "$Label debug APK"
    $listing = @(Invoke-Native (Join-Path $tools 'aapt.exe') @('list','-v',$Path) "$Label-zip.txt")
    $null = Invoke-Native (Join-Path $tools 'zipalign.exe') @('-c','-v','4',$Path) "$Label-alignment.txt"
    $null = Invoke-Native (Join-Path $tools 'aapt.exe') @('dump','xmltree',$Path,'AndroidManifest.xml') "$Label-manifest.txt"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    $models = [Collections.Generic.List[object]]::new()
    try {
        foreach ($model in @($baseline.runtimeFiles | Where-Object path -like '*.tflite')) {
            $entryName = $model.path.Substring('app/src/main/'.Length)
            $entry = $zip.GetEntry($entryName)
            if (-not $entry) { throw "$Label missing $entryName" }
            Assert-Equal $entry.Length $model.bytes "$Label $entryName bytes"
            $stored = @($listing | Where-Object { $_ -match ('^\s*\d+\s+Stored\s+\d+\s+.*\s' + [regex]::Escape($entryName) + '$') })
            Assert-Equal $stored.Count 1 "$Label $entryName stored without compression"
            $stream = $entry.Open()
            $sha = [Security.Cryptography.SHA256]::Create()
            try { $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','') }
            finally { $stream.Dispose(); $sha.Dispose() }
            Assert-Equal $hash $model.sha256 "$Label $entryName SHA-256"
            $models.Add([pscustomobject]@{Name=$entryName;Bytes=$entry.Length;SHA256=$hash;Compression='Stored'})
        }
    } finally { $zip.Dispose() }
    [pscustomobject]@{
        Path=$Path;SHA256=(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
        Package=$package;VersionName=$versionName;VersionCode=[int]$versionCode
        CertificateSHA256=$certificate;Models=$models.ToArray()
    }
}
$globalBefore = Get-GlobalKeyHash
$result = [ordered]@{
    Started=[DateTimeOffset]::Now.ToString('o');ReportDirectory=$run
    RecoveryVerified=$false;LintPassed=$false;DeviceValidation='PENDING';BackupDeletionAllowed=$false
    GlobalDebugKeyBefore=$globalBefore
}
$failure = $null
Push-Location $root
try {
    . (Join-Path $PSScriptRoot 'env.ps1')
    $env:GIT_OPTIONAL_LOCKS = '0'
    $result.Toolchain = [ordered]@{JavaHome=$env:JAVA_HOME;Sdk=$env:ANDROID_HOME;GradleHome=$env:GRADLE_USER_HOME;Offline=$true}
    $null = Invoke-Native (Join-Path $env:JAVA_HOME 'bin\java.exe') @('-version') 'java-version.txt'
    $head = @(Invoke-Native 'git' @('rev-parse','HEAD') 'git-head.txt')[0]
    Assert-Equal $head $baseline.commit 'Recovery Git HEAD'
    $null = Invoke-Native 'git' @('fsck','--full') 'git-fsck.txt'
    $refs = @(Invoke-Native 'git' @('for-each-ref','--format=%(refname) %(objectname)') 'git-refs.txt')
    $oldRefs = @(Get-Content -LiteralPath (Join-Path $root '.local\recovery\git-refs-before.txt'))
    if (Compare-Object $refs $oldRefs) { throw 'Git references differ from migrated baseline' }
    $null = Invoke-Native 'git' @('-c','core.safecrlf=false','status','--porcelain=v2','--branch') 'git-status.txt'
    $null = Invoke-Native 'git' @('check-ignore','.local/private/debug.keystore','.local/gradle-home/caches') 'git-ignore.txt'
    $trackedPrivate = @(Invoke-Native 'git' @('ls-files','--','.local','*.keystore','*.jks','signing.properties','*.apk','*.aab') 'git-private-files.txt')
    if ($trackedPrivate.Count) { throw 'Private/generated assets are tracked by Git' }
    foreach ($file in $baseline.runtimeFiles) {
        Assert-Equal (Get-FileHash -LiteralPath (Join-Path $root $file.path) -Algorithm SHA256).Hash $file.sha256 $file.path
    }
    Assert-Equal (Get-FileHash -LiteralPath '.local\private\debug.keystore' -Algorithm SHA256).Hash $baseline.keySHA256 'Original key'
    $main = Get-Content -LiteralPath 'app\src\main\java\com\local\paralleleyeconverter\MainActivity.kt' -Raw
    Assert-Equal ($main -match ('CURRENT_VERSION = "' + [regex]::Escape($baseline.versionName) + '"')) $true 'In-app version'
    $result.RuntimeFilesUnchanged = $baseline.runtimeFiles.Count
    $result.OriginalApk = Test-Apk (Join-Path $root '.local\original-apk\ParallelEyeConverter-v0.1.25-debug.apk') 'original'
    Assert-Equal $result.OriginalApk.SHA256 $baseline.originalApkSHA256 'Original APK hash'
    Write-Host 'Running clean testDebugUnitTest assembleDebug with project-local tools and --offline.'
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks @('clean','testDebugUnitTest','assembleDebug') 2>&1 |
        Tee-Object -FilePath (Join-Path $run 'offline-build.log') | Out-Host
    $result.BuildPassed = $true
    $buildLog = Get-Content -LiteralPath (Join-Path $run 'offline-build.log') -Raw
    $result.UnitTests = if ($buildLog -match ':app:testDebugUnitTest NO-SOURCE') { 'NO-SOURCE (no test cases in baseline)' } else { 'Task completed; see build log' }
    $apk = Join-Path $root 'app\build\outputs\apk\debug\ParallelEyeConverter-v0.1.25-debug.apk'
    $result.RebuiltApk = Test-Apk $apk 'rebuilt'
    $result.ByteIdenticalToOriginal = $result.RebuiltApk.SHA256 -eq $result.OriginalApk.SHA256
    $result.ManifestIdenticalToOriginal = -not [bool](Compare-Object (Get-Content -LiteralPath (Join-Path $run 'original-manifest.txt')) (Get-Content -LiteralPath (Join-Path $run 'rebuilt-manifest.txt')))
    Assert-Equal $result.ManifestIdenticalToOriginal $true 'Packaged manifest'
    Write-Host 'Running offline lintDebug; known baseline findings remain visible and are not suppressed.'
    $lintTaskFailure = $null
    try {
        & (Join-Path $PSScriptRoot 'build.ps1') -Tasks @('lintDebug') 2>&1 |
            Tee-Object -FilePath (Join-Path $run 'offline-lint.log') | Out-Host
        $result.LintPassed = $true
    } catch { $lintTaskFailure = $_.Exception.Message }
    foreach ($extension in @('xml','txt','html')) {
        Copy-Item -LiteralPath (Join-Path $root "app\build\reports\lint-results-debug.$extension") -Destination $run
    }
    [xml]$lint = Get-Content -LiteralPath (Join-Path $run 'lint-results-debug.xml') -Raw
    $errors = @($lint.issues.issue | Where-Object { $_.severity -in @('Error','Fatal') })
    $warnings = @($lint.issues.issue | Where-Object severity -eq 'Warning')
    $expectedErrors = @('ProtectedPermissions','StartActivityAndCollapseDeprecated','UnspecifiedRegisterReceiverFlag') | Sort-Object
    $actualErrors = @($errors | ForEach-Object { $_.id } | Sort-Object)
    $known = $errors.Count -eq 3 -and $warnings.Count -eq 25 -and -not [bool](Compare-Object $expectedErrors $actualErrors)
    $result.Lint = [ordered]@{Passed=$result.LintPassed;Errors=$errors.Count;Warnings=$warnings.Count;KnownBaselineMatched=$known;ErrorIds=$actualErrors;TaskFailure=$lintTaskFailure}
    if (-not $result.LintPassed -and -not $known) { throw 'Lint failed beyond the documented baseline; inspect offline-lint.log' }
    $result.Assets = & (Join-Path $PSScriptRoot 'verify-assets.ps1') -ReportDirectory $run
    Assert-Equal (Get-GlobalKeyHash) $globalBefore 'Global debug key unchanged'
    $result.RecoveryVerified = $true
    if ($RequireCleanLint -and -not $result.LintPassed) { throw 'Strict lint requested: existing lint findings require follow-up.' }
} catch {
    $failure = $_
    $result.Failure = $_.Exception.Message
} finally {
    $result.GlobalDebugKeyAfter = Get-GlobalKeyHash
    $result.GlobalDebugKeyUnchanged = $result.GlobalDebugKeyAfter -eq $globalBefore
    if (-not $result.GlobalDebugKeyUnchanged) {
        $result.RecoveryVerified = $false
        $result.Failure = 'Global debug key changed during verification'
        if (-not $failure) { $failure = $result.Failure }
    }
    $result.Completed = [DateTimeOffset]::Now.ToString('o')
    $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding UTF8
    $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $root '.local\recovery\latest.json') -Encoding UTF8
    Pop-Location
}
Write-Host "Evidence: $run"
if ($failure) { throw $failure }
Write-Host "Recovery verified. LintPassed=$($result.LintPassed); device validation pending; backup deletion is NOT approved."
