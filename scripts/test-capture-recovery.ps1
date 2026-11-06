param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
# Compile the actual production policy before testing, including on a clean checkout.
& (Join-Path $PSScriptRoot 'build.ps1') -Tasks @('compileDebugKotlin')
$classes = Join-Path $projectRoot 'app\build\tmp\kotlin-classes\debug'
$stdlib = @(Get-ChildItem -LiteralPath (Join-Path $env:GRADLE_USER_HOME 'caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.0.21') -Recurse -Filter '*.jar')
if ($stdlib.Count -ne 1) { throw 'Expected one restored Kotlin 2.0.21 standard library.' }
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $projectRoot ('.local\verification\capture-recovery-tests-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$testClasses = Join-Path $OutputDirectory 'test-classes'
[IO.Directory]::CreateDirectory($testClasses) | Out-Null
$classpath = $classes + ';' + $stdlib[0].FullName
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -cp $classpath -d $testClasses (Join-Path $projectRoot 'tests\CaptureRecoveryCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Capture recovery regression checks did not compile.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp ($testClasses + ';' + $classpath) CaptureRecoveryCheck |
    Tee-Object -FilePath (Join-Path $OutputDirectory 'capture-recovery-check.txt')
if ($LASTEXITCODE -ne 0) { throw 'Capture recovery regression failed.' }
