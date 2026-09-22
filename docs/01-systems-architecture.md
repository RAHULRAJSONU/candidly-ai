# Systems Architecture — Corrected & Hardened

**Revision 2 — 21 September 2026**
Companion to `00-CHANGELOG-and-critical-corrections.md`.

---

## 1. Runtime and framework baseline (corrected)

| Layer | Original (incorrect) | Corrected |
|-------|----------------------|-----------|
| JDK | "OpenJDK 27 LTS" | **OpenJDK 25 LTS** (current LTS; released Sep 2025). Java 27 exists but is non-LTS and outside Boot's supported matrix. |
| Framework | Spring Boot 4.1.x / Spring Framework 7.x | **Spring Boot 4.1.1 / Spring Framework 7.0.9** (Jakarta EE 11). Boot 4.1 is validated up to Java 26; run it on Java 25 LTS. |
| Concurrency | Virtual threads | Unchanged — virtual threads are GA on Java 25. Use `Executors.newVirtualThreadPerTaskExecutor()` for I/O-bound ATS polling. **Do not** wrap CPU-bound reranking in virtual threads; use a bounded platform-thread pool. |
| AI orchestration | Spring AI 1.1+/2.x | Unchanged (Spring AI as cognitive substrate + Spring StateMachine for control). The framework selection rationale in the original is sound. |
| DB | PostgreSQL 16/17 + pgvector (HNSW) | Unchanged. Pin the pgvector version and record HNSW build params in migration. |
| Cache / rate limit | Redis 7.4 cluster | Unchanged. |
| Event mesh | Kafka | Unchanged, **but** fed via transactional outbox (see `02`), not from the request thread. |
| Frontend | React 19 + TS + Vite + TanStack | Unchanged. |

> Do not enable `--enable-preview` in production for "experimental memory APIs." Preview flags make the build non-portable across minor JDK updates and are a support-contract liability. If a preview feature is genuinely needed, isolate it behind an interface and a feature flag.

---

## 2. Component hierarchy (revised)

```
Tier 1 — Client & Interaction
  React 19 SPA (Vite, TS, Tailwind, TanStack Query v5)
  Candidate Profile Vault + verified Career Story Ledger
  Human-in-the-Loop Approval Console  ← DEFAULT path, not the exception
  Real-time telemetry: STOMP over WSS

Tier 2 — Control & Orchestration
  Spring Boot 4.1.1 microservices on OpenJDK 25 LTS
  Virtual-thread executors for I/O; bounded pools for CPU
  Spring StateMachine core (transactional transitions + compensation)
  Spring AI subagents (Discovery, Match, Tailoring, Critic)

Tier 3 — Ingestion & Retrieval
  Resume Intelligence Service (ESCO / Lightcast normalizers)
  Hybrid Match Engine (BM25 + dense cosine)
  RRF fusion + cross-encoder rerank
  Grounded RAG with DETERMINISTIC factual-verification gate (see 02, C-8)

Tier 4 — Persistence & Messaging
  PostgreSQL 16/17 + pgvector (HNSW, vector_cosine_ops)
  Redis 7.4 (locks, rate-limit token buckets, dedupe)
  Kafka event mesh — fed by transactional-outbox relay

Tier 5 — Discovery & Submission  ← REDESIGNED
  Discovery adapters: public GET endpoints only (Greenhouse / Lever / Ashby / Workday CXS)
  Submission — one of:
    (a) B2B partner submission (employer-held ATS credentials), OR
    (b) User-supervised browser assist (candidate's own session)
  NO evasion layer.  NO residential proxy rotation.  NO WAF bypass.
```

---

## 3. Tier 5 redesign — discovery vs. submission

### 3.1 Discovery (unchanged, legitimate)

Public job-board GET endpoints require no authentication and are intended for syndication. Keep the polling adapters, with these disciplines:

- **Poll on a schedule** (hourly is reasonable; Greenhouse publishes no hard rate limit but blocks aggressive callers). Cache with ETag / `updated_at`.
- **Deduplicate** with SHA-256 over canonical `(company, title, location, source_job_id)`.
- **Respect `robots.txt` and published rate guidance.** Back off on 429/503.
- Workday CXS discovery uses the public `/wday/cxs/{tenant}/{site}/jobs` search with offset pagination (20/page). This is read-only discovery and is fine.

### 3.2 Submission model A — B2B / employer partner (the only true automation path)

When the customer is an **employer or staffing partner** who legitimately holds ATS credentials, automated submission is valid:

- Greenhouse: employer's Base64 Job Board key, server-proxied (never in the browser).
- Lever: employer Super-Admin apply key, or Partner OAuth (1-hour tokens → refresh scheduler).
- Ashby: employer key with `candidatesWrite`; files via `file.createFileUploadHandle` → presigned S3 → `applicationForm.submit`.

Here `ApplicationCapability.DIRECT_API_SUPPORTED` is valid and `submitApplication(...)` is retained — inside the partner boundary only.

### 3.3 Submission model B — user-supervised browser assist (consumer path)

For an individual job seeker with no ATS key, the honest pattern is **assist, don't impersonate**:

1. The platform prepares the tailored resume, cover letter and draft answers.
2. It opens the target careers page **in the candidate's own authenticated session**, using the candidate's real credentials, with an honest user agent.
3. It pre-fills fields the candidate has verified.
4. **The candidate reviews and presses submit.** The system never clicks the final submit on the candidate's behalf without an explicit per-application action, and never touches voluntary demographic fields (see `02`).
5. On any CAPTCHA/anti-bot challenge, automation **pauses and hands control to the human**. It does not attempt to solve or evade it.

This is the same "human-in-the-loop escalation" the original described — but it becomes the *whole* mechanism for consumers, not a fallback after an evasion attempt.

### 3.4 Removed components

`ResidentialProxyRotationGateway`, `WafMitigationGateway`, JA3/JA4 alignment, patched-CDP browser images, behavioural-emulation drivers — all deleted. The Playwright cluster, if retained for model-B assist, runs standard, non-evasive, and only ever inside a session the candidate has authenticated.

---

## 4. Data model corrections

### 4.1 Embedding dimension mismatch (C-7)

The schema pins `embedding vector(1536)`, but the two suggested models don't produce 1536-dim vectors:

| Model | Native dim |
|-------|-----------|
| `text-embedding-3-large` | 3072 (truncatable via `dimensions` param) |
| `text-embedding-3-small` | 1536 |
| BAAI/BGE-M3 | 1024 |

Pick **one** model per index and size the column to it. Vectors from different models are not comparable, so:

- Add `embedding_model VARCHAR(64) NOT NULL` and `embedding_version INT NOT NULL` to every table holding vectors.
- Never mix models in one HNSW index. A model change is a **re-embedding migration**, not an in-place update.
- If using `text-embedding-3-large` at reduced `dimensions=1536`, record that explicitly — it is not interchangeable with `3-small` at 1536.

```sql
ALTER TABLE candidate_experience
  ADD COLUMN embedding_model   VARCHAR(64) NOT NULL DEFAULT 'bge-m3',
  ADD COLUMN embedding_version INT         NOT NULL DEFAULT 1;
-- and size embedding to the chosen model, e.g. vector(1024) for BGE-M3
```

### 4.2 HNSW index tuning

`m = 16, ef_construction = 64` is a reasonable build default, but recall is dominated by **`ef_search` at query time**, which the original never sets:

```sql
SET hnsw.ef_search = 100;  -- raise for recall, lower for latency; tune per SLO
```

Benchmark recall@k against a brute-force baseline on a held-out set before trusting ANN results for a hard filter.

### 4.3 Idempotency and audit integrity

Add to `application`:

```sql
ALTER TABLE application
  ADD COLUMN idempotency_key UUID NOT NULL DEFAULT uuid_generate_v4(),
  ADD CONSTRAINT uk_app_idem UNIQUE (candidate_id, job_posting_id);  -- one live app per (candidate, job)
```

The event ledger stays append-only, but it must be written via the **outbox**, not a second write inside the business transaction (see `02`).

---

## 5. Orchestrator — corrected

Key changes to `ApplicationOrchestrator`:

- Threshold constants (`0.85`, `0.92`) become injected, versioned configuration, not magic numbers (see `03`).
- Tailoring is **not** performed synchronously inside `processDiscoveredJob`. Discovery/scoring commits `SHORTLISTED`; a separate worker consumes and performs tailoring, because the critic loop is multi-call and slow (see `03`, C-5).
- State transitions and the audit event are written in the **same local transaction as the outbox row**; Kafka publication is downstream.
- Auto-apply (`canAutoApply`) is gated additionally on **submission model** — it is only ever reachable on the B2B partner path, never the consumer path.

```java
// Corrected canAutoApply — note the submission-model gate
private boolean canAutoApply(MatchScorecard s, JobPosting job, SubmissionContext ctx) {
    return ctx.isPartnerAuthorized()                         // NEW: only B2B path
        && s.compositeScore() >= thresholds.autoApply()      // injected, versioned
        && "REMOTE".equalsIgnoreCase(job.getWorkplaceType())
        && job.applicationSchema() != null
        && job.applicationSchema().requiresNoEssays()
        && job.applicationSchema().hasNoDemographicRequirement();  // NEW: never auto-fill EEO
}
```

---

## 6. Resilience contract (C-10)

Every remote integration (ATS, LLM provider, embedding provider) must declare:

- **Timeouts** — connect + read, always set.
- **Retry budget** — bounded, exponential backoff + jitter, honour `Retry-After`.
- **Circuit breaker** — Resilience4j, per-endpoint, with half-open probing.
- **Rate limiter** — Redis token bucket keyed per (source, tenant).
- **Bulkhead** — isolate ATS polling from LLM calls so one provider's slowness can't exhaust the pool.
- **Dead-letter queue** — poison messages (unparseable postings, permanently failing submissions) go to a DLQ with the failure payload, not an infinite retry loop.

`FAILED` in the state machine is a terminal state only after the retry budget is exhausted **and** the message has been dead-lettered with a diagnostic reason.

---

## 7. Observability

Keep Micrometer + OpenTelemetry. Add these domain metrics beyond infra defaults:

- Funnel conversion per stage (`DISCOVERED → INGESTED → … → SUBMITTED`) — a sudden drop is the earliest signal of a broken adapter or a changed ATS schema.
- Per-model token spend and p50/p95/p99 latency for each subagent.
- **Grounding-verification failure rate** — how often the deterministic gate (C-8) catches a claim the critic missed. If this trends up, the generator prompt has drifted.
- Cross-encoder recall@k drift against the periodic brute-force baseline.
