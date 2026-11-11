[CmdletBinding()]
param(
    [ValidateSet('Inspect','Start','ReadOnlyPreflight','RunDatabaseTests')][string]$Mode = 'Inspect',
    [string]$ExpectedMysqld,
    [string]$ExpectedDatadir,
    [string]$ExpectedExecutableSha256,
    [long]$ExpectedPid = 0,
    [string]$StartOptIn,
    [string]$DatabaseOptIn,
    [string]$JavaHome = 'C:/Program Files/Java/jdk-17'
)
$ErrorActionPreference = 'Stop'
$rf016Workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../../'))
$rf016PrivateRoot = Join-Path $rf016Workspace 'local/rf016-results'
function Assert-Rf016([bool]$condition, [string]$reason) { if (!$condition) { throw $reason } }
function Get-Rf016Listeners {
    $rf016Netstat = & netstat -ano -p tcp
    Assert-Rf016 ($LASTEXITCODE -eq 0) 'RF016 listener query failed'
    return @($rf016Netstat | ForEach-Object {
        $rf016Fields = $_.Trim() -split '\s+'
        if ($rf016Fields.Count -ge 5 -and $rf016Fields[1] -match ':13306$' -and $rf016Fields[3] -eq 'LISTENING') {
            [pscustomobject]@{Endpoint=$rf016Fields[1];Pid=[long]$rf016Fields[4]}
        }
    })
}
Assert-Rf016 (![string]::IsNullOrWhiteSpace($ExpectedMysqld) -and ![string]::IsNullOrWhiteSpace($ExpectedDatadir)) 'RF016 explicit existing executable and datadir required'
$rf016Exe = (Resolve-Path -LiteralPath $ExpectedMysqld).Path
$rf016Data = (Resolve-Path -LiteralPath $ExpectedDatadir).Path
Assert-Rf016 ((Split-Path $rf016Exe -Leaf) -eq 'mysqld.exe') 'RF016 unexpected executable'
Assert-Rf016 (Test-Path -LiteralPath $rf016Exe -PathType Leaf) 'RF016 executable missing'
Assert-Rf016 (Test-Path -LiteralPath $rf016Data -PathType Container) 'RF016 existing datadir missing'
Assert-Rf016 (Test-Path -LiteralPath (Join-Path $rf016Data 'auto.cnf') -PathType Leaf) 'RF016 initialized existing datadir required; initialization prohibited'
Assert-Rf016 (Test-Path -LiteralPath (Join-Path $rf016Data 'ngng_local') -PathType Container) 'RF016 existing target schema required; creation prohibited'
Assert-Rf016 ($ExpectedExecutableSha256 -match '^[a-fA-F0-9]{64}$') 'RF016 previously reviewed executable hash required'
Assert-Rf016 ((Get-FileHash -LiteralPath $rf016Exe -Algorithm SHA256).Hash -eq $ExpectedExecutableSha256) 'RF016 executable hash mismatch'
$rf016Listeners = Get-Rf016Listeners
$rf016Processes = @(Get-Process mysqld -ErrorAction SilentlyContinue)
Write-Output ('RF016 read-only host: executable-exists=true datadir-exists=true selected-listeners=' + $rf016Listeners.Count + ' mysqld-processes=' + $rf016Processes.Count)
if ($Mode -eq 'Inspect') { return }
Assert-Rf016 (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf) 'RF016 explicit Java installation missing'
if ($Mode -eq 'Start') {
    Assert-Rf016 ($StartOptIn -eq 'reviewed-rf016-existing-private-server-start') 'RF016 server start requires separate reviewed opt-in'
    Assert-Rf016 ($rf016Listeners.Count -eq 0 -and $rf016Processes.Count -eq 0) 'RF016 existing listener/process; will not start or stop anything'
    # This operation writes the existing datadir (possibly outside the workspace). Execute only
    # after independent review and the host sandbox authorization for this concrete target.
    # No initialize, schema SQL, credential changes, service install, or shutdown operation exists.
    $rf016Basedir = Split-Path (Split-Path $rf016Exe -Parent) -Parent
    $rf016Arguments = @('--no-defaults', ('--basedir="' + $rf016Basedir + '"'), ('--datadir="' + $rf016Data + '"'),
        '--port=13306', '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin',
        '--persisted-globals-load=OFF', '--skip-slave-start', '--event-scheduler=OFF', '--console')
    New-Item -ItemType Directory -Force -Path $rf016PrivateRoot | Out-Null
    $rf016Launch = Join-Path $rf016PrivateRoot ('start-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $rf016Launch | Out-Null
    $rf016Process = Start-Process -FilePath $rf016Exe -ArgumentList $rf016Arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $rf016Launch 'server.stdout.private.txt') -RedirectStandardError (Join-Path $rf016Launch 'server.stderr.private.txt')
    Write-Output ('RF016 started PID=' + $rf016Process.Id + '; DB connections=0. Reinspect before separately selecting RunDatabaseTests.')
    return
}
$rf016SelectedClass = 'com.ngng.api.isolation.mysql.Rf016MySqlCommitRollbackTest'
if ($Mode -eq 'ReadOnlyPreflight') {
    Assert-Rf016 ($DatabaseOptIn -eq 'approved-rf016-readonly-preflight-only') 'RF016 read-only preflight requires its separate opt-in'
    $rf016SelectedClass = 'com.ngng.api.isolation.mysql.Rf016MySqlReadOnlyPreflightTest'
} else { Assert-Rf016 ($DatabaseOptIn -eq 'approved-rf016-owned-user-point-only') 'RF016 database execution requires separately reviewed opt-in' }
Assert-Rf016 ($ExpectedPid -gt 0 -and $rf016Listeners.Count -eq 1 -and $rf016Listeners[0].Endpoint -eq '127.0.0.1:13306' -and $rf016Listeners[0].Pid -eq $ExpectedPid) 'RF016 live loopback listener/PID mismatch'
$rf016Process = Get-Process -Id $ExpectedPid
Assert-Rf016 ($rf016Process.ProcessName -eq 'mysqld' -and [IO.Path]::GetFullPath($rf016Process.Path) -eq $rf016Exe) 'RF016 live executable mismatch'
Assert-Rf016 (![string]::IsNullOrWhiteSpace($env:RF016_DB_PASSWORD)) 'RF016 password must be supplied privately in process environment; never as script argument'
# Java performs the authoritative ProcessHandle argument/datadir check before its first socket,
# checks DB identity on each new connection, then read-only schema/row/FK gates before JPA/writes.
$rf016Names = @('JAVA_HOME','RF016_MYSQL_TEST','RF016_EXPECTED_PID','RF016_EXPECTED_MYSQLD','RF016_EXPECTED_DATADIR','RF016_LEDGER_FILE')
$rf016Original = @{}
foreach ($rf016Name in $rf016Names) { $rf016Original[$rf016Name] = [Environment]::GetEnvironmentVariable($rf016Name, 'Process') }
$rf016Result = Join-Path $rf016PrivateRoot ('database-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
Assert-Rf016 (!(Test-Path -LiteralPath $rf016Result)) 'RF016 result directory exists; never overwrite evidence'
New-Item -ItemType Directory -Path $rf016Result -Force | Out-Null
try {
    $env:JAVA_HOME=$JavaHome; $env:RF016_MYSQL_TEST=$DatabaseOptIn; $env:RF016_EXPECTED_PID=[string]$ExpectedPid
    $env:RF016_EXPECTED_MYSQLD=$rf016Exe; $env:RF016_EXPECTED_DATADIR=$rf016Data
    $env:RF016_LEDGER_FILE=Join-Path $rf016Result 'owned-inserts.private.jsonl'
    Push-Location (Join-Path $rf016Workspace 'NGNG-backend')
    try {
        & ./gradlew.bat test --offline --no-daemon --rerun-tasks --tests $rf016SelectedClass *> (Join-Path $rf016Result 'test-console.private.txt')
        $rf016Exit = $LASTEXITCODE
        $rf016Xml = 'build/test-results/test/TEST-' + $rf016SelectedClass + '.xml'
        if (Test-Path -LiteralPath $rf016Xml) { Copy-Item -LiteralPath $rf016Xml -Destination (Join-Path $rf016Result 'test.private.xml') }
        Write-Output ('RF016 database selection exit=' + $rf016Exit + '; originals retained privately, inspect before sharing. No broader cleanup or server shutdown will be attempted.')
        if ($rf016Exit -ne 0) {
            if ($Mode -eq 'ReadOnlyPreflight') { throw 'RF016 read-only preflight blocked; fixture writes were not attempted' }
            throw 'RF016 selected database test failed; use marker/PK ledger for read-only recovery review'
        }
    } finally { Pop-Location }
} finally {
    foreach ($rf016Name in $rf016Names) { [Environment]::SetEnvironmentVariable($rf016Name, $rf016Original[$rf016Name], 'Process') }
}
