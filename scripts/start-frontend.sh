#!/usr/bin/env bash
# Starts the React/Vite frontend (frontend/) on :5173, proxying /api to the
# Spring Boot backend on :8080 (see vite.config.ts). Start the backend first
# (start-backend.sh) or requests to /api will fail.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Kill anything already listening on :5173 - a stale Vite dev server from a
# previous run will otherwise keep serving its already-loaded module graph
# (or just occupy the port so this run silently fails to bind it).
existing_pids="$(netstat -ano 2>/dev/null | { grep -E ':5173\s.*LISTENING' || true; } | awk '{print $NF}' | sort -u)"
if [ -n "$existing_pids" ]; then
    echo "Stopping existing process(es) on :5173 ($existing_pids)..."
    for pid in $existing_pids; do
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
    done
    sleep 0.5
fi

cd "$SCRIPT_DIR/../frontend"
if [ ! -d node_modules ]; then
    npm install
fi
npm run dev
