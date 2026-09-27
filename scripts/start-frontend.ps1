# Starts the React/Vite frontend (frontend/) on :5173, proxying /api to the
# Spring Boot backend on :8080 (see vite.config.ts). Start the backend first
# (start-backend.ps1) or requests to /api will fail.

$ErrorActionPreference = "Stop"

# Kill anything already listening on :5173 - a stale Vite dev server from a
# previous run will otherwise keep serving its already-loaded module graph
# (or just occupy the port so this run silently fails to bind it), which is
# why a code change can appear not to show up even after "restarting".
$existing = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Stopping existing process(es) on :5173 ($($existing.OwningProcess -join ', '))..."
    $existing | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 500
}

Push-Location "$PSScriptRoot\..\frontend"
try {
    if (-not (Test-Path "node_modules")) {
        npm install
    }
    npm run dev
}
finally {
    Pop-Location
}
