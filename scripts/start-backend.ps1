# Starts the Spring Boot backend (app/) on JDK 25.
# Requires: local Postgres 18 Windows service running, database `candidly` on :5432
# (see CLAUDE.md). GROQ_API_KEY / JINA_API_KEY / TYPESAFE_API_KEY are read from the
# environment if set; application.yml defaults them to empty string otherwise.

$ErrorActionPreference = "Stop"

# Kill anything already listening on :8080 - otherwise a stale process from a
# previous run keeps serving old compiled classes while this run either fails
# to bind the port or opens in a window you're not looking at, and the app
# looks like it "didn't pick up" your changes.
$existing = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Stopping existing process(es) on :8080 ($($existing.OwningProcess -join ', '))..."
    $existing | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 500
}

$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Push-Location "$PSScriptRoot\..\app"
try {
    & .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}
