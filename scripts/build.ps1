param(
    [switch]$Online,
    [string[]]$Tasks = @('assembleDebug')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$buildArguments = @(
    '--no-daemon', '--console=plain', '--max-workers=1',
    '--project-cache-dir', (Join-Path $projectRoot '.local\project-cache'),
    '-Porg.gradle.java.installations.auto-detect=false',
    '-Porg.gradle.java.installations.auto-download=false',
    "-Porg.gradle.java.installations.paths=$env:JAVA_HOME"
)
if (-not $Online) { $buildArguments += '--offline' }
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @Tasks @buildArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
