$ErrorActionPreference = 'Stop'
$evidenceRoot = $PSScriptRoot
$pairs = Get-Content -LiteralPath (Join-Path $evidenceRoot 'redaction-pairs.json') -Raw | ConvertFrom-Json
$previous = Get-Content -LiteralPath (Join-Path $evidenceRoot 'raw/prior-metadata/sha256-manifest.json') -Raw | ConvertFrom-Json
$startupAccountPattern = '\bwith PID \d+ \(started by ([^\r\n]+?) in '
function Xml-Invariants([string]$file) {
    $doc = [System.Xml.XmlDocument]::new()
    $doc.Load($file)
    $result = foreach ($node in $doc.SelectNodes('//testsuite | //testcase')) {
        $attributes = [ordered]@{}
        foreach ($attribute in ($node.Attributes | Where-Object Name -ne 'hostname' | Sort-Object Name)) {
            $attributes[$attribute.Name] = $attribute.Value
        }
        [pscustomobject]@{
            element = $node.Name
            attributes = $attributes
            failures = $node.SelectNodes('./failure').Count
            errors = $node.SelectNodes('./error').Count
            skipped = $node.SelectNodes('./skipped').Count
        }
    }
    return ($result | ConvertTo-Json -Depth 12 -Compress)
}
$results = foreach ($pair in $pairs) {
    $originalPath = Join-Path $evidenceRoot $pair.raw
    $sharedPath = Join-Path $evidenceRoot $pair.shared
    $originalHash = (Get-FileHash -LiteralPath $originalPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $sharedHash = (Get-FileHash -LiteralPath $sharedPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $oldRecord = @($previous | Where-Object path -eq $pair.shared)
    if ($oldRecord.Count -ne 1 -or $oldRecord[0].sha256 -ne $originalHash -or
        $pair.originalSha256 -ne $originalHash -or $pair.sharedSha256 -ne $sharedHash -or
        (Get-Item -LiteralPath $originalPath).Length -ne $pair.originalBytes -or
        (Get-Item -LiteralPath $sharedPath).Length -ne $pair.sharedBytes -or
        $pair.changed -ne ($originalHash -ne $sharedHash)) {
        throw ('Evidence hash mismatch for ' + $pair.shared)
    }
    $originalStartup = [regex]::Matches([IO.File]::ReadAllText($originalPath), $startupAccountPattern)
    $sharedStartup = [regex]::Matches([IO.File]::ReadAllText($sharedPath), $startupAccountPattern)
    if ($originalStartup.Count -ne $sharedStartup.Count -or
        @($sharedStartup | Where-Object { $_.Groups[1].Value -cne '[OS_USER]' }).Count -ne 0) {
        throw ('Startup account redaction mismatch for ' + $pair.shared)
    }
    $xmlChecked = $pair.shared.EndsWith('.xml')
    if ($xmlChecked -and (Xml-Invariants $originalPath) -cne (Xml-Invariants $sharedPath)) {
        throw ('XML test/status/timestamp metadata changed for ' + $pair.shared)
    }
    [pscustomobject]@{
        path = $pair.shared
        originalMatchesPriorManifest = $true
        pairedHashesVerified = $true
        startupAccountsRedacted = $sharedStartup.Count
        xmlChecked = $xmlChecked
        xmlTestStatusTimestampUnchanged = $(if ($xmlChecked) { $true } else { $null })
    }
}
$sourceRecords = @($previous | Where-Object { $_.path -like 'after/source/*' })
foreach ($record in $sourceRecords) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $evidenceRoot $record.path) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $record.sha256) { throw ('Execution source snapshot changed: ' + $record.path) }
}
[pscustomobject]@{
    scope = 'Raw backup bytes match the prior manifest. Shared bytes match paired hashes and startup accounts are masked. XML suite/test attributes except hostname and failure/error/skipped status counts remain identical, including timestamp/time.'
    files = $results.Count
    xmlFiles = @($results | Where-Object xmlChecked).Count
    startupAccountsRedacted = ($results | Measure-Object -Property startupAccountsRedacted -Sum).Sum
    unchangedExecutionSourceSnapshots = $sourceRecords.Count
    allPassed = $true
    results = $results
} | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'redaction-verification.json') -Encoding utf8
Write-Output ('Verified pairs=' + $results.Count + '; XML=' + @($results | Where-Object xmlChecked).Count)
