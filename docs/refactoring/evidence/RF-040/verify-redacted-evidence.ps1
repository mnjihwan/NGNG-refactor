$ErrorActionPreference = 'Stop'
$evidenceRoot = $PSScriptRoot
$rawRoot = Join-Path $evidenceRoot 'raw'
$pairs = Import-Csv -Delimiter "`t" -LiteralPath (Join-Path $evidenceRoot 'REDACTION_PAIRS.tsv')
$originalRecords = Import-Csv -Delimiter "`t" -LiteralPath (Join-Path $rawRoot 'prior-metadata/ARTIFACT_SHA256.tsv')
$hostPattern = '(\bhostname=")([^"]+)(")'
$accountPattern = '(\bwith PID \d+ \(started by )([^\r\n]+?)( in )'
$profilePath = $env:USERPROFILE
if (-not $profilePath) { throw 'Run verification with the original Windows user profile.' }
$forwardHome = $profilePath.Replace('\', '/')
$homeVariants = @($profilePath, $forwardHome, $profilePath.Replace('\', '\\'),
    [uri]::EscapeDataString($profilePath), [uri]::EscapeDataString($forwardHome), $forwardHome.Replace(':', '%3A')) |
    Select-Object -Unique | Sort-Object Length -Descending
$knownHosts = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$knownAccounts = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
if ($env:COMPUTERNAME) { [void]$knownHosts.Add($env:COMPUTERNAME) }
if ($env:USERNAME) { [void]$knownAccounts.Add($env:USERNAME) }
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Get-Sha256([string]$file) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()
}

function Get-XmlResultMetadata([string]$file) {
    $document = [Xml.XmlDocument]::new()
    $document.Load($file)
    $results = foreach ($node in $document.SelectNodes('//testsuite | //testcase')) {
        $attributes = [ordered]@{}
        foreach ($item in ($node.Attributes | Where-Object Name -ne 'hostname' | Sort-Object Name)) {
            $attributes[$item.Name] = $item.Value
        }
        [pscustomobject]@{
            element = $node.Name
            attributes = $attributes
            outcomes = @($node.SelectNodes('./failure | ./error | ./skipped') | ForEach-Object OuterXml)
        }
    }
    return ($results | ConvertTo-Json -Depth 10 -Compress)
}

$results = foreach ($pair in $pairs) {
    $original = Join-Path $evidenceRoot $pair.raw_path
    $shared = Join-Path $evidenceRoot $pair.shared_path
    $prior = @($originalRecords | Where-Object path -ceq $pair.shared_path)
    if ($prior.Count -ne 1 -or $prior[0].sha256 -cne $pair.original_sha256 -or
        (Get-Sha256 $original) -cne $pair.original_sha256 -or (Get-Sha256 $shared) -cne $pair.shared_sha256 -or
        (Get-Item -LiteralPath $original).Length -ne $pair.original_bytes -or
        (Get-Item -LiteralPath $shared).Length -ne $pair.shared_bytes) {
        throw ('Invalid original or shared evidence hash for ' + $pair.shared_path)
    }

    $originalText = $utf8.GetString([IO.File]::ReadAllBytes($original))
    $xml = $pair.shared_path.EndsWith('.xml')
    $expected = $originalText
    $homeCount = 0
    foreach ($variant in $homeVariants) {
        $pattern = [regex]::new([regex]::Escape($variant), 'IgnoreCase')
        $homeCount += $pattern.Matches($expected).Count
        $expected = $pattern.Replace($expected, '[USER_HOME]')
    }
    $accounts = [regex]::Matches($originalText, $accountPattern)
    foreach ($match in $accounts) { [void]$knownAccounts.Add($match.Groups[2].Value) }
    $expected = [regex]::Replace($expected, $accountPattern, '$1[OS_USER]$3')
    $hostCount = 0
    if ($xml) {
        foreach ($match in [regex]::Matches($originalText, $hostPattern)) {
            [void]$knownHosts.Add($match.Groups[2].Value)
            $hostCount++
        }
        $expected = [regex]::Replace($expected, $hostPattern, '$1[HOST]$3')
        if ((Get-XmlResultMetadata $original) -cne (Get-XmlResultMetadata $shared)) {
            throw ('XML test results or timing changed for ' + $pair.shared_path)
        }
    }
    $expectedHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($expected))).ToLowerInvariant()
    if ($expectedHash -cne $pair.shared_sha256) {
        throw ('Unexpected content change for ' + $pair.shared_path)
    }
    [pscustomobject]@{
        path = $pair.shared_path
        xml = $xml
        startupAccountsMasked = $accounts.Count
        hostnamesMasked = $hostCount
        homePathsMasked = $homeCount
    }
}

$pairPaths = @($pairs | ForEach-Object shared_path)
$unchangedRecords = @($originalRecords | Where-Object { $_.path -cnotin $pairPaths })
foreach ($record in $unchangedRecords) {
    if ((Get-Sha256 (Join-Path $evidenceRoot $record.path)) -cne $record.sha256) {
        throw ('Unchanged evidence differs from the original manifest for ' + $record.path)
    }
}

$files = @(Get-ChildItem -Force -Recurse -File -LiteralPath $evidenceRoot | Where-Object {
    -not $_.FullName.StartsWith($rawRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
})
$files += Get-Item -LiteralPath (Join-Path $evidenceRoot '../../tasks/RF-040.md')
$identityPatterns = @($knownHosts | ForEach-Object { [regex]::Escape($_) })
$identityPatterns += @($knownAccounts | ForEach-Object { '(?<![\w.-])' + [regex]::Escape($_) + '(?![\w.-])' })
$identityPatterns += @($homeVariants | ForEach-Object { [regex]::Escape($_) })
$identityPatterns += '[A-Za-z](?::|%3[Aa])(?:[\\/]+|%5[cC]|%2[fF])Users(?:[\\/]+|%5[cC]|%2[fF])[A-Za-z0-9_.-]+'
foreach ($file in $files) {
    $contents = $utf8.GetString([IO.File]::ReadAllBytes($file.FullName))
    $unmasked = $identityPatterns | Where-Object { [regex]::IsMatch($contents, $_, 'IgnoreCase') }
    if ($unmasked -or $contents -cmatch '/(?:home|Users)/[A-Za-z0-9_.-]+') {
        throw ('Machine, account or home path candidate in shared file ' + $file.Name)
    }
}

[pscustomobject]@{
    originalAndSharedHashPairs = $results.Count
    xmlFilesWithUnchangedTestResultsAndTiming = @($results | Where-Object xml).Count
    startupAccountsMasked = ($results | Measure-Object -Property startupAccountsMasked -Sum).Sum
    hostnamesMasked = ($results | Measure-Object -Property hostnamesMasked -Sum).Sum
    homePathsMasked = ($results | Measure-Object -Property homePathsMasked -Sum).Sum
    sharedFilesMatchingOriginalManifest = $unchangedRecords.Count
    sharedTextFilesScanned = $files.Count
    remainingKnownIdentityCandidates = 0
    verifiedRedactions = $results
} | ConvertTo-Json -Depth 6
