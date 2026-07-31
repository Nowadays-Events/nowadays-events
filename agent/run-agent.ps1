$ErrorActionPreference = 'Stop'

$agentRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = 'C:\Users\utilisateur\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$logDirectory = Join-Path $agentRoot 'logs'
$lockPath = Join-Path $agentRoot 'agent.lock'

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$lock = $null

try {
    $lock = [System.IO.File]::Open(
        $lockPath,
        [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
} catch {
    exit 0
}

try {
    $timestamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
    $logPath = Join-Path $logDirectory "$timestamp.log"
    & $python (Join-Path $agentRoot 'nowadays_agent.py') --max-runtime-seconds 180 *> $logPath
    exit $LASTEXITCODE
} finally {
    if ($null -ne $lock) {
        $lock.Dispose()
    }
}
