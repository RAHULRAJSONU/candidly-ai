#!/usr/bin/env bash
# Starts the Spring Boot backend (app/) on JDK 25.
# Requires: local Postgres 18 service running, database `candidly` on :5432
# (see CLAUDE.md). GROQ_API_KEY / JINA_API_KEY / TYPESAFE_API_KEY are read from
# the environment if set; application.yml defaults them to empty string otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Kill anything already listening on :8080 - otherwise a stale process from a
# previous run keeps serving old compiled classes and the app looks like it
# "didn't pick up" your changes even after re-running this script.
existing_pids="$(netstat -ano 2>/dev/null | { grep -E ':8080\s.*LISTENING' || true; } | awk '{print $NF}' | sort -u)"
if [ -n "$existing_pids" ]; then
    echo "Stopping existing process(es) on :8080 ($existing_pids)..."
    for pid in $existing_pids; do
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
    done
    sleep 0.5
fi

export JAVA_HOME="/c/Program Files/Java/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$SCRIPT_DIR/../app"
./mvnw spring-boot:run
