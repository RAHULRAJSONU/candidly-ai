# Candidly AI — Autonomous Career Intelligence Platform

Discovery -> hybrid match/scoring -> grounded resume tailoring -> human-in-the-loop
submission, with a hash-chained audit ledger for every automated decision.

See [`docs/00-CHANGELOG-and-critical-corrections.md`](docs/00-CHANGELOG-and-critical-corrections.md)
for the product spec (`docs/00` through `docs/04`) and [`CLAUDE.md`](CLAUDE.md) for a full
map of what's implemented vs. simplified in this codebase. Note: most other top-level
`docs/*.md` files (`docs/design`, `docs/lld`, `docs/epics`, `docs/brand`, etc.) are stale
leftovers from an unrelated prior project and don't describe this repo.

## Structure

- `app/` — Spring Boot 4.1.1 / Java 25 backend (Maven). Discovery, matching, grounded
  tailoring, HITL review, audit ledger, Career Vault, ATS scoring, interview prep,
  pipeline tracking, email intake, and the Autopilot agent (queues tailored resumes for
  human review — never auto-submits).
- `frontend/` — React 19 + TypeScript SPA (Vite) covering the same backend from a
  browser, alongside the existing `app/src/main/resources/static/console.html` HITL
  console.
- `docs/00`–`docs/04` — the real requirements for this repo.
- `docs/typesafe/` — local compressed reference for the TypeSafe AI primitives used
  throughout (`ai.candidly.career.typesafe`).

## Prerequisites

- JDK 25 (the project is pinned to Java 25 LTS; do not build with a newer JDK installed
  alongside it)
- Maven (or use the bundled `app/mvnw` wrapper)
- Node.js + npm
- Local Postgres 18 with the `vector` extension installed (pgvector), database
  `candidly` on port 5432 — this is the only datasource, there's no H2/in-memory profile

## Running the backend

```bash
cd app
./mvnw spring-boot:run
```

On first boot against a fresh database, run the one-time SQL setup scripts (they need
the tables to exist first):

```bash
psql -d candidly -f sql/pgvector-indexes.sql
psql -d candidly -f sql/lexical-search-setup.sql
```

Set `GROQ_API_KEY`, `JINA_API_KEY`, and `TYPESAFE_API_KEY` in the environment — never
commit them. The HITL console is available at `http://localhost:8080/console.html` once
the app is running.

External job-board discovery adapters (Greenhouse/Lever) are off by default
(`candidly.discovery.*.enabled: false`); flip them on in `app/src/main/resources/
application.yml` or call `POST /api/discovery/poll` to trigger a manual pull.

## Running the frontend

```bash
cd frontend
npm install
npm run dev
```

Vite serves on `:5173` and proxies `/api` to the Spring Boot app on `:8080` — both must
be running. `npm run build` type-checks (`tsc -b`) and produces a static `dist/`; it
isn't wired into the Spring Boot artifact yet, so it's a separate deploy step.

## Security

Never commit API keys or real credentials. `application.yml`'s default Postgres
credentials are for local-only, no-network-exposure development. See
[`docs/02-security-and-compliance.md`](docs/02-security-and-compliance.md) for the full
security and compliance posture.
