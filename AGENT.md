# AGENT.md

Guidance for Claude Code when working in this repository.

## What this repo is

`docs/00` through `docs/04` describe the **Autonomous Career Intelligence Platform**
(discovery → hybrid match/scoring → grounded resume tailoring → human-in-the-loop
submission). Read `docs/00-CHANGELOG-and-critical-corrections.md` first — it's the entry
point and indexes the other four.

`docs/design`, `docs/lld`, `docs/epics`, `docs/brand`, and most other top-level `docs/*.md`
files belong to a **different, unrelated project** ("MediSync", a medication-management
app) that predates this one and was never cleaned out of the folder. Don't treat them as
requirements for this repo; they're stale leftovers.

The implementation lives in `app/` — a Spring Boot 4.1.1 / Java 25 service. Phase 2
(match + grounded tailoring, now async) and slices of Phase 1 (discovery) and Phase 3
(HITL console) are built, plus a hash-chained audit ledger standing in for docs/02 §3's
transactional outbox (see `docs/04` §6 for the full phased roadmap: Kafka itself and
Phase 4 B2B automation are not started, and skipped deliberately per standing
instruction - no Kafka, Docker, Kubernetes, or Redis in this stack). `frontend/` is a
separate React 19 + TypeScript SPA (see its own section below) covering the same
backend from a browser instead of `static/console.html`.

## Frontend (`frontend/`)

`docs/ui_ux_mock/` holds ~31 AI-generated exploratory mockup images spanning at least
two inconsistent product concepts under one shared "CareerIntelligence" visual language
(different sidebar/nav sets, different taglines). One concept ("Autopilot" / "AI Job
Application Agent") depicts a fully autonomous agent that submits applications with no
human step - this directly contradicts docs/00/docs/02's core, deliberately-corrected
no-auto-submission/HITL-by-default guarantee, and was **not** built. `frontend/` follows
the other concept (the one with Dashboard/Job Discovery/Matches/Applications/Compliance
nav) and implements only the screens today's backend actually has real data for:

```
frontend/
  src/api/           client.ts (fetch wrapper over /api, proxied by Vite to :8080 in
                      dev - see vite.config.ts) + types.ts (hand-written TS mirrors of
                      the Java DTOs; keep in sync by hand, there's no shared schema)
  src/context/        CandidateContext - a top-bar candidate switcher standing in for
                      real auth/session, which this backend doesn't have
  src/components/ui/  Card, Badge, Button, ScoreRing (the mock's circular % rings),
                      StatCard/ProgressBar, Tabs, CompanyAvatar (deterministic color
                      from company name, matching the mock's colored logo squares)
  src/components/layout/  Sidebar, TopBar, AppShell - the mock's dark-navy nav shell
  src/pages/
    Dashboard.tsx          stat cards + top matches + audit-ledger activity feed
    JobDiscovery.tsx       postings list/search + manual discovery-poll trigger +
                            detail panel + "score against this candidate" action
    Matches.tsx             list of MatchScorecards for the selected candidate
    MatchExplanation.tsx    per-match breakdown (skill/experience/semantic/domain
                            bars, eligibility, reason codes) + submit-for-tailoring
    Applications.tsx        the HITL review queue (pending/history), same data
                            TailoringReviewController/console.html already serves
    Compliance.tsx          audit-chain verify + retention purge + LL144 bias report
                            + GDPR export/erase, all against real endpoints
```

Deliberately **not** built (no backing API, or would require the auto-apply concept
above): Career Vault, Evidence Graph, ATS scoring, AI mock interviews, email-inbox
monitoring, an onboarding wizard, interview/offer tracking, Autopilot. `static/
console.html` still exists and still works - it isn't replaced, just superseded in
scope by `Applications.tsx` for anyone using the new SPA.

Backend additions made to support this: `GET /api/candidates` (list, since there's no
auth yet to scope "my profile"), `GET /api/job-postings` (list, excludes BLOCKed),
`GET /api/matches?candidateId=` and `GET /api/matches/{id}`, `GET /api/tailoring/jobs?
candidateId=`.

Run it: `cd frontend && npm install && npm run dev` (Vite on :5173, proxying `/api` to
the Spring Boot app on :8080 - both must be running). `npm run build` type-checks
(`tsc -b`, `noUnusedLocals`/`noUnusedParameters` are on) then produces a static
`dist/` - nothing wires that into the Spring Boot artifact yet, so it's a separate
deploy step, unlike `console.html`.

## TypeSafe skill

This project uses TypeSafe (System One models, including Jev) for AI-powered judgments,
Jina for embeddings, and Groq (OpenAI-compatible chat) for text generation. All three are
wired as plain `RestClient` beans in `app/src/main/java/ai/candidly/career/config/
AiClientsConfig.java` under the `candidly.groq.*` / `candidly.jina.*` /
`candidly.typesafe.*` config prefixes (`app/src/main/resources/application.yml`) — there
is no Spring AI dependency in this project; it was tried and dropped because Spring AI
2.0.1's OpenAI module wraps the official `openai-java` SDK client-builder machinery in a
way that's fragile to point at two different providers (Groq + Jina) under one starter.

**A compressed local copy of the TypeSafe docs lives at
`docs/typesafe/TYPESAFE_REFERENCE.md`.** Check it first for primitive shapes, the HTTP API
contract, model limitations, and a cookbook-to-this-repo mapping — it's faster than
re-fetching the live site for things that don't change often. The live docs
(https://docs.typesafe.ai) remain the source of truth; re-fetch a specific page if
something in the local copy looks stale or a call behaves differently than documented,
and update the local copy if you do.

**Before doing any work that touches AI/LLM behavior in this repo** — designing a new
judgment, adding a classifier, extraction, ranking, routing, or verification step, or
otherwise deciding how a feature should use an LLM — invoke the `typesafe:typesafe-ai`
skill first and follow its guidance before writing code. This applies to every
chat/session in this repo, not just the first time it comes up.

This does not apply to unrelated engineering work (schema migrations, plain CRUD, build
config, etc.) that has no AI/LLM decision-making involved.

## Project layout

```
docs/00-04                     Career platform spec (the real requirements for this repo)
docs/typesafe/                 Local compressed TypeSafe reference (see above)
docs/{design,lld,epics,...}    Unrelated MediSync project debris - ignore for this repo
app/                           Spring Boot 4.1.1 / Java 25 service (Maven)
  src/main/java/ai/candidly/career/
    domain/                    JPA entities + repositories (Candidate, CandidateExperience,
                                JobPosting, MatchScorecard, TailoredArtifact) + PgVectorType
                                (Hibernate UserType binding float[] to a native `vector` column)
    ai/                        GroqChatClient, JinaEmbeddingClient, VectorMath
    typesafe/                  TypeSafeClient + request/response types (raw HTTP, no SDK)
    taxonomy/                  In-memory ESCO/Lightcast stand-in + SkillNormalizationService
    screening/                 JobPostingScreeningService (prompt-injection guardrail)
    matching/                  EligibilityGateService (hard gate) + CompositeScoringService
                                (docs/03 weighted scoring - S_sem and S_dom are both
                                TypeSafe Score judgments, via SemanticFitJudgeService and
                                DomainFitJudgeService respectively, not embedding cosine)
                                + MatchOrchestratorService
    retrieval/                 The docs/03 §1 funnel: LexicalSearchService (Postgres FTS,
                                Stage 2) + VectorSearchService (pgvector HNSW ANN via
                                JdbcTemplate, Stage 3) + ReciprocalRankFusion (k=60,
                                Stage 4) + CrossEncoderRerankService (TypeSafe fan-out
                                rerank, Stage 5a) + JobRecommendationService (ties the
                                funnel together, ending in the gate+composite scorer, Stage 5b)
    tailoring/                 GroundedResumeGenerator, DeterministicGroundingVerifier,
                                ClaimToneVerifier, ResumeTailoringService (critic loop) +
                                TailoringJobService/TailoringAsyncExecutor (async
                                dispatch, docs/03 §4) + TailoringReviewService (HITL
                                approve/reject, docs/01 §2)
    discovery/                 FR-1 discovery: JobBoardDiscoveryAdapter (now carries its
                                own isEnabled(), gating only the scheduled poll - see
                                DiscoveryScheduler) + GreenhouseDiscoveryAdapter (real
                                public Greenhouse Job Board API) + LeverDiscoveryAdapter
                                (real public Lever postings API, api.lever.co/v0/
                                postings/{company}) + DiscoveryScheduler (hourly per
                                enabled adapter + manual trigger, unconditional)
    audit/                     AuditEvent + AuditEventRepository + AuditLedgerService: a
                                SHA-256 hash-chained, append-only decision ledger
                                (docs/02 §3.2/§3.4, §4.3 LL144, §4.4 adverse-action) -
                                the transactional-outbox intent without Kafka, since
                                AuditLedgerService.record runs inside the caller's own
                                @Transactional method (Propagation.MANDATORY enforces
                                this) rather than a separate broker publish. Also
                                AuditCheckpoint/AuditCheckpointRepository, backing
                                AuditLedgerService.purgeOlderThan - a retention policy
                                (docs/02 §2) that keeps the chain verifiable past a purge
                                by checkpointing the last purged row's hash rather than
                                always re-verifying from GENESIS_HASH (POST /api/audit/
                                retention/purge on AuditController)
    demographics/               CandidateDemographics (voluntary EEOC/OFCCP self-ID,
                                OMB CC-305 - candidateId is a bare UUID, no FK, same
                                isolation pattern as AuditEvent.subjectId) +
                                DemographicsController (PUT/GET /api/candidates/{id}/
                                demographics, candidate-only) + BiasAuditReportService
                                (docs/02 §4.3 LL144 annual bias audit + docs/03 fairness
                                metrics: shortlist-rate parity and the four-fifths/0.8
                                impact-ratio rule, by gender and race/ethnicity, backing
                                GET /api/audit/bias-report on AuditController) - nothing
                                in matching/ or tailoring/ imports this package
    api/                       REST controllers + ingestion services, incl.
                                JobPostingIngestionService (exact dedupe hash +
                                FuzzyDuplicateJobPostingService, a TypeSafe Noul fan-out
                                catching re-posts/cross-posts the exact hash can't, before
                                the screening gate), CandidateDataSubjectService (docs/02
                                §4.1 GDPR export/erasure, backing GET/DELETE
                                /api/candidates/{id}[/export] on CandidateController),
                                TailoringReviewController (backs static/console.html;
                                pending-review/history both return an
                                ArtifactReviewView pairing the artifact with its
                                MatchScorecard, so the console shows why a pair was
                                shortlisted, not just the generated text),
                                DiscoveryController (manual poll trigger),
                                CalibrationController (docs/03 §3 calibration: POST
                                /api/calibration/consistency reruns CompositeScoringService
                                on the same pair N times via ScoringConsistencyService to
                                measure real TypeSafe judgment variance - capped at 20
                                runs, each one a real billed call), and AuditController
                                (ledger read + chain-verify + bias-report + retention-purge
                                endpoints)
  src/main/resources/static/
    console.html                Minimal vanilla-JS HITL approval console (see below)
  sql/pgvector-indexes.sql      One-time HNSW index creation (ddl-auto=update can't do this)
  sql/lexical-search-setup.sql  One-time generated tsvector column + GIN index
```

Database: the local Postgres 18 Windows service (not Docker), database `candidly` on
port 5432, with the `vector` extension already installed system-wide (pgvector 0.8.6).
This is now the only datasource — there is no H2/in-memory fallback profile. Run both
`sql/pgvector-indexes.sql` and `sql/lexical-search-setup.sql` once (via psql) after the
app has booted at least once (they need the tables to exist first); `ddl-auto=update`
creates ordinary columns but knows nothing about pgvector's HNSW index type or generated
`tsvector` columns.

Discovery is **off by default** for both adapters (`candidly.discovery.greenhouse.enabled`
/ `candidly.discovery.lever.enabled: false` in `application.yml`) so a fresh checkout
doesn't start polling live external services on an hourly schedule unasked. Flip either
on, or call `POST /api/discovery/poll` directly (works regardless of either `enabled`
flag - it always runs every registered adapter, the manual trigger), to pull real
postings from GitLab's public Greenhouse board (`board-tokens: [gitlab]`) and Palantir's
public Lever board (`company-slugs: [palantir]`), each capped at
`max-postings-per-board: 5` - a real board can list hundreds (Palantir's had 312 live
postings when this was tested), and each discovered posting costs a screening +
embedding call downstream. The HITL console is at `GET /console.html` once the app is
running.

Audit ledger: every screening decision, match score, tailoring generation, and
approve/reject decision is written to `audit_event` (via `AuditLedgerService.record`,
called inside the same transaction as the decision, so the ledger row and the decision
commit or roll back together - no dual-write gap). Rows are SHA-256 hash-chained
(`previousHash`/`hash`); `GET /api/audit/verify` recomputes the whole chain and reports
the first row that fails to match, `GET /api/audit/events` / `/api/audit/events/subject/
{id}` read it back. This is the docs/02 §3.2 transactional-outbox *intent* - atomic,
durable, tamper-evident decision history - without Kafka.

Key simplifications vs. the full docs/01 architecture (documented inline in the relevant
class javadoc, not repeated here): no Kafka (the audit ledger above covers the
tamper-evidence/durability goal that motivated it, without the broker; deliberately
skipped per standing instruction, not just "not yet"); the cross-encoder
rerank stage is a TypeSafe fan-out (one Noul per shortlisted posting in one request)
rather than a dedicated cross-encoder model; two of the four discovery adapters named in
docs/04 FR-1 (Greenhouse and Lever's real public APIs; Ashby/Workday still not started),
and discovered postings carry no extracted skills/domain (that would need another
judgment step this slice doesn't build); the HITL console is a single static HTML page
with vanilla JS and polling (now with a pending/history tab split and each artifact's
match scorecard inline), not the docs/01 React 19 + TanStack + WebSocket/STOMP telemetry
SPA.

What *is* real now, not simplified: tailoring is genuinely async off the request thread
(docs/03 §4) — `POST /api/tailoring` returns a `TailoringJob` handle in ~0s via virtual
threads, not a blocking 15-40s call; the retrieval funnel (docs/03 §1 Stages 2-5) runs
actual Postgres full-text search + pgvector HNSW cosine ANN, fused with RRF, reranked by
one batched TypeSafe call, before the gate+composite scorer ever touches a posting;
discovery pulls real live postings from GitLab's public Greenhouse board and Palantir's
public Lever board, not fixtures.

Known sharp edges hit and fixed in this repo so far - check for these first if a similar
symptom shows up elsewhere, before assuming it's something new:
- **Flush ordering.** Hibernate flushes inserts before deletes within one flush by
  default, so a delete-then-save "replace this row" pattern (`MatchOrchestratorService`,
  `TailoringJobService`) can trip a unique constraint unless the delete is force-flushed
  first (`repository.flush()` right after `delete()`).
- **Lazy `@ElementCollection` outside its loading transaction.** Every `List`/`Set` field
  in `domain/` needs `@ElementCollection(fetch = FetchType.EAGER)` (already applied
  everywhere it's needed) - Spring Data's default repository methods each run their own
  short transaction, so a collection lazily loaded during a `findById()` throws
  `LazyInitializationException` (surfaces as a truncated/invalid JSON response, since
  Jackson hits it mid-serialization after the response has already started streaming) the
  moment a controller reads it back later. If a new entity gains a collection field, add
  `fetch = EAGER` up front.
- **Async task started before its own transaction commits.** Don't call an `@Async`
  method directly from inside the `@Transactional` method that just created the row it
  operates on - the background thread can run before the transaction commits, and
  `findById()` won't see the row yet. Defer with
  `TransactionSynchronizationManager.registerSynchronization(... afterCommit ...)` (see
  `TailoringJobService.submit`).
- **Stale Postgres CHECK constraint on `@Enumerated(STRING)` columns.** `ddl-auto=update`
  bakes a CHECK constraint listing only the enum values that existed when the table was
  first created, and never revisits it when the Java enum gains new values later
  (`TailoredArtifactStatus` gained `APPROVED`/`REJECTED` after the table already existed,
  and every write failed a stale `ANY (ARRAY['PENDING_APPROVAL', ...])` check until the
  constraint was manually dropped and recreated). Adding an enum value to an
  already-migrated entity needs a manual `ALTER TABLE ... DROP/ADD CONSTRAINT` - `ddl-auto`
  won't do it for you. A fresh database created from current code doesn't hit this.
- **`@Transactional` needs the Spring proxy too, not just `@Async`.** `TailoringAsyncExecutor`
  originally called `this.markCompleted(...)` (an `@Transactional` method) from inside its
  own `@Async run(...)` method - direct self-invocation bypasses the proxy for
  `@Transactional` exactly like it does for `@Async`, so those methods were never
  actually running in a transaction. This went unnoticed for a while because each
  `repository.save()` inside them quietly opened its own one-statement transaction; it
  only surfaced once `AuditLedgerService.record` (called from `markCompleted`) declared
  `Propagation.MANDATORY` and threw "No existing transaction found". Fixed by moving the
  status-write methods to a separate bean (`TailoringJobStatusService`) that
  `TailoringAsyncExecutor` calls into, the same pattern already used to split
  `TailoringJobService`/`TailoringAsyncExecutor` for `@Async`. If a new `@Transactional`
  method is ever called via `this.` from another method on the same bean, it silently
  runs with no transaction - always split into a second bean instead.
- **A retention/checkpoint scheme has to be threaded through every "what's the previous
  hash" read site, not just the verifier.** Adding `AuditLedgerService.purgeOlderThan`
  (checkpointing the last-purged row's hash so `verifyChain` can keep validating what's
  left) initially only updated `verifyChain`'s starting point. `record()` still asked
  `repository.findTopByOrderByOccurredAtDescIdDesc()` for the previous hash directly, and
  once a purge had ever emptied the table, that query legitimately found nothing and fell
  back to `GENESIS_HASH` - so the first row written after a purge claimed `GENESIS_HASH`
  as its `previousHash` instead of the checkpoint's, and `verifyChain` correctly rejected
  it as broken (live-tested and caught immediately: purge, write, verify → `intact:
  false`). Fixed by having `record()`'s same fallback resolve through the checkpoint too
  (`.orElseGet(this::startingHash)`, the same private helper `verifyChain` uses). The
  general lesson: when a table can now be legitimately empty for a reason other than
  "nothing has ever been written," every place that means "empty = use the initial value"
  needs to also mean "empty = use the checkpoint," not just the one method being changed.
- **Don't hash a value whose DB round-trip precision differs from its in-memory
  precision.** `AuditLedgerService`'s hash chain initially hashed `Instant.now()` at full
  nanosecond precision, but Postgres' `timestamp` column round-trips at microsecond
  precision - so recomputing the hash after a reload (`verifyChain`) used a different
  timestamp string than the one hashed at write time, and every row falsely reported as
  tampered. Fixed by truncating to `ChronoUnit.MICROS` before hashing *and* persisting,
  so the same string is hashed both times. Any hash/signature over a value that survives
  a DB round-trip needs to be computed on the post-round-trip-precision form, not the
  richer in-memory one.
- **GDPR erasure vs. the tamper-evident audit ledger is a real, deliberate tension, not
  a bug to fix.** Docs/02 asks for both "hard delete with cascade" (§4.1) and an
  append-only, hash-chained decision ledger where deleting any row breaks every later
  row's hash (§4.3/§5 - see the timestamp-precision edge above for how strictly that's
  enforced). `CandidateDataSubjectService.erase` resolves it by cascading the hard
  delete across every candidate-owned table (`candidate_experience`, `match_scorecard`,
  `tailored_artifact`, `tailoring_job`, `candidate` itself) but deliberately leaving that
  candidate's `audit_event` rows in place - `subjectId` there is a bare UUID with no FK,
  and `details` is built from reason-code/screening strings, not raw candidate PII, so a
  post-erasure ledger entry pointing at a UUID with nothing left behind it is the
  intended end state, not leftover PII. If a new audit event type ever puts raw
  candidate data into `details` (a free-text reviewer note already does, in
  `TailoringReviewService` - accepted as a known residual risk for now), erasure stops
  being clean and this needs revisiting.

## Coding standards

- Java 25, Spring Boot 4.1.1, Maven (`app/pom.xml`). Build/test with the JDK 25 install
  (`/c/Program Files/Java/jdk-25` on this machine) — JDK 27 is also installed but the
  docs explicitly correct the stack to Java 25 LTS (`docs/00` §1.3), don't switch to 27.
- No Lombok; entities use explicit constructors/getters and a protected no-arg
  constructor for JPA.
- Every non-obvious deviation from the `docs/00-04` spec (a simplification, an assumption
  the source docs didn't specify, a deliberate scope cut) should be a short class-level
  javadoc comment citing the relevant doc section, not a separate design-notes file.

## Security

- Follow `docs/02-security-and-compliance.md`. In particular: tailoring/critic model
  calls never get tool access and never see raw job-posting text (only already-screened,
  structured fields) — see `GroundedResumeGenerator`. The deterministic grounding verifier
  (`DeterministicGroundingVerifier`), not the LLM critic, is the actual invariant.
- Never commit `GROQ_API_KEY`, `JINA_API_KEY`, or `TYPESAFE_API_KEY` — they're read from
  the environment (`application.yml` defaults them to empty string).
- `application.yml`'s Postgres credentials (`postgres`/`postgres`) point at a local-only,
  no-network-exposure dev database — fine for this stage, but do not carry that pattern
  into anything that isn't a solo local machine (docs/02 §2: secrets belong in a manager,
  never in a committed properties file, once this has a real deployment target).
