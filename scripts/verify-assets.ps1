param([Parameter(Mandatory=$true)][string]$ReportDirectory)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$migration = @(Import-Csv -LiteralPath (Join-Path $root '.local\recovery\migration-files.csv'))
$summary = Get-Content -LiteralPath (Join-Path $root '.local\recovery\migration-summary.json') -Raw | ConvertFrom-Json
if ($summary.ManifestMismatches -ne 0 -or $summary.DestinationMismatches -ne 0 -or $summary.CopiedFiles -ne $migration.Count) {
    throw 'Migration evidence is incomplete or reports a mismatch.'
}
[IO.Directory]::CreateDirectory($ReportDirectory) | Out-Null
$modifiedFiles = @('.gitignore', 'README.md', 'app\build.gradle.kts', '.git\index')
$checks = [Collections.Generic.List[object]]::new()
$count = 0
foreach ($row in $migration) {
    $path = Join-Path $root $row.Destination
    $exists = Test-Path -LiteralPath $path -PathType Leaf
    $actual = if ($exists) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash } else { $null }
    $status = if ($actual -eq $row.SHA256) { 'Exact' } else { 'UnexpectedChange' }
    if ($status -ne 'Exact') {
        if ($modifiedFiles -contains $row.Destination) {
            $archive = Join-Path $root ('.local\history\project-baseline\' + $row.Destination)
            if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -eq $row.SHA256) { $status = 'OriginalPreservedInHistory' }
        } elseif ($row.Destination.StartsWith('.local\gradle-home\') -and
                  -not $row.Destination.StartsWith('.local\gradle-home\caches\modules-2\files-2.1\') -and
                  -not $row.Destination.StartsWith('.local\gradle-home\wrapper\dists\')) {
            # Build execution updates journals, daemon logs, locks and derived caches.
            # Original dependency artifacts and the Gradle distribution stay protected.
            $status = 'MutableBuildCache'
        }
    }
    $checks.Add([pscustomobject]@{Path=$row.Destination;OriginalSHA256=$row.SHA256;CurrentSHA256=$actual;Status=$status})
    $count++
    if ($count % 5000 -eq 0) { Write-Host "Local asset audit: $count / $($migration.Count)" }
}
$checks | Export-Csv -LiteralPath (Join-Path $ReportDirectory 'asset-audit.csv') -NoTypeInformation -Encoding UTF8
$result = [ordered]@{
    Checked=$checks.Count
    Exact=@($checks | Where-Object Status -eq 'Exact').Count
    OriginalPreservedInHistory=@($checks | Where-Object Status -eq 'OriginalPreservedInHistory').Count
    MutableBuildCache=@($checks | Where-Object Status -eq 'MutableBuildCache').Count
    UnexpectedChanges=@($checks | Where-Object Status -eq 'UnexpectedChange' | Select-Object -ExpandProperty Path)
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'asset-audit.json') -Encoding UTF8
if ($result.UnexpectedChanges.Count) { throw "Unexpected migrated asset changes; see $ReportDirectory\asset-audit.csv" }
[pscustomobject]$result
