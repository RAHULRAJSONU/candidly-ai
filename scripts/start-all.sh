#!/usr/bin/env bash
# Runs the backend (Spring Boot, :8080) and frontend (Vite, :5173) together in
# this shell, backend in the background and frontend in the foreground.
# Requires the local Postgres 18 service to already be running (see
# CLAUDE.md) - this script does not start it. Ctrl-C stops both.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/start-backend.sh" &
BACKEND_PID=$!

cleanup() {
    kill "$BACKEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Backend starting (pid $BACKEND_PID) on http://localhost:8080 (console.html at /console.html)"
echo "Frontend starting on http://localhost:5173"

"$SCRIPT_DIR/start-frontend.sh"
