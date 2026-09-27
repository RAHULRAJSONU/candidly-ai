# Launches the backend (Spring Boot, :8080) and frontend (Vite, :5173) each in
# their own PowerShell window. Requires the local Postgres 18 service to already
# be running (see CLAUDE.md) - this script does not start it.

$ErrorActionPreference = "Stop"

Start-Process powershell -ArgumentList @(
    "-NoExit", "-File", "`"$PSScriptRoot\start-backend.ps1`""
)

Start-Process powershell -ArgumentList @(
    "-NoExit", "-File", "`"$PSScriptRoot\start-frontend.ps1`""
)

Write-Host "Backend starting on http://localhost:8080 (console.html at /console.html)"
Write-Host "Frontend starting on http://localhost:5173"
