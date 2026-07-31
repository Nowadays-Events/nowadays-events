$ErrorActionPreference = 'Stop'

$agentRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = 'C:\Users\utilisateur\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$logDirectory = Join-Path $agentRoot 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$logPath = Join-Path $logDirectory 'api.log'
& $python (Join-Path $agentRoot 'api_server.py') --host 0.0.0.0 --port 8765 *> $logPath
