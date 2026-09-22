# Product Requirements Document — Revised

**Revision 2 — 21 September 2026**
Companion to `00-CHANGELOG-and-critical-corrections.md`.

---

## 1. What the corrected product is

An **Autonomous Career Intelligence Platform** that discovers relevant vacancies, matches them against a candidate's *verified* record, generates factually grounded application artifacts, and manages the application lifecycle with a human in the loop — **without** impersonating candidates, evading access controls, or submitting unverifiable claims.

The single most important product correction: **automated submission is only available on the B2B / employer-partner path** where the operator legitimately holds ATS credentials. For an individual job seeker, the product is an *assistant* that prepares everything and hands the final submit to the human in their own session. (Rationale: `00` §1.1, `01` §3.)

---

## 2. Personas & governance

- **Candidate (primary controller)** — sets goals and non-negotiable constraints (compensation floor, location, work authorization), maintains the verified career vault, reviews match scores and generated artifacts, and gives explicit per-application authorization. On the consumer path, the candidate personally submits.
- **Employer / staffing partner (B2B controller)** — legitimately holds ATS credentials and operates the automated-submission path within its own authorization. This is the only actor for whom `DIRECT_API_SUPPORTED` and auto-apply are valid.
- **Enterprise recruiter (downstream beneficiary)** — receives clean, single-column, ATS-parseable resumes and structured payloads, every claim in which is backed by the candidate's own records.

---

## 3. Functional requirements (revised)

- **FR-1 Discovery (unchanged, legitimate).** Poll Greenhouse / Lever / Ashby **public GET** endpoints on a schedule (hourly), run Workday **CXS public search** with offset pagination (20/page, read-only). Deduplicate via SHA-256 over canonical company/title/location/source-id. Respect `robots.txt`, published rate guidance, and back off on 429/503.

- **FR-2 Profile ingestion.** Parse uploaded PDF/DOCX master resumes into structured biographical JSON; map competencies to ESCO/Lightcast IDs; partition verified accomplishments into atomic vector nodes. Record `embedding_model` + `embedding_version` per node (see `01` §4.1). Measure taxonomy-mapping coverage.

- **FR-3 Hybrid match engine.** Sparse BM25-style filter + **hard eligibility gate** (location, work authorization, comp floor, mandatory-skill minimum) → dense cosine (pgvector HNSW) → RRF (k=60) → cross-encoder rerank → **deterministic composite score over Skill / Experience / Semantic / Domain** (location removed from the sum; it is a gate). Weights are calibrated, not asserted (see `03`).

- **FR-4 Truthful tailoring.** Artifacts generated strictly from verified nodes via grounded RAG. **Two-stage grounding:** LLM critic *and* a deterministic verifier that rejects any claim not backed by a candidate DB record. Tailoring runs **asynchronously** (15–40 s budget, not 8 s synchronous). Resumes render single-column, ATS-parseable, under the relevant parse limit (e.g. Greenhouse's 2.5 MB text-parse buffer).

- **FR-5 Submission & oversight (materially revised).**
  - *Consumer path:* qualified positions enter `PENDING_APPROVAL`; the candidate reviews and **submits in their own authenticated session**. No automated final submit. No key. No evasion.
  - *B2B partner path:* automated submission via employer-held credentials; auto-apply permitted only when composite ≥ the calibrated auto-apply threshold, work mode is remote, no open-ended essays, **and no demographic requirement**.
  - *All paths:* voluntary EEOC/OFCCP self-identification is **never** completed by any model; defaults to decline unless the candidate fills it. Every submission is idempotent (see `02` §3.3).

- **FR-6 Immutable audit.** Every status change, payload, receipt and approval is logged via the **transactional outbox** to the append-only Kafka ledger and Postgres audit table — guaranteed consistent with committed state (see `02` §3). Consider hash-chaining for tamper-evidence.

- **FR-7 Regulatory (new).** Support GDPR data-subject rights (export/rectify/erase incl. vectors), meaningful human review for automated decisions, EU AI Act high-risk documentation and logging, NYC LL144 bias-audit data capture and candidate notices, and deterministic human-readable adverse-action reasons (see `02` §4).

- **FR-8 Honest automation (new, explicit).** No fingerprint spoofing, no CDP patching, no residential-proxy evasion, no CAPTCHA/WAF bypass. Automation uses honest user agents and the candidate's own session, and hands control to the human at any challenge. Enforced by a CI check against forbidden dependencies.

---

## 4. Non-functional requirements (corrected)

- **NFR-1 Latency (corrected).** Lexical+dense filter of 500 postings ≤ 120 s (keep). Single generation pass 3–8 s. **Full tailoring (retrieve → generate → ≤3 critic loops → deterministic verify) 15–40 s, asynchronous** — not 8 s synchronous (see `03` §4).
- **NFR-2 Security & encryption.** AES-256-GCM at rest with envelope encryption + KMS; enforced PII redaction before any external LLM call; ZDR/no-train provider tier; ATS secrets in a vault, never in browser/logs/heap.
- **NFR-3 Resilience.** Resilience4j circuit breakers, bounded retry budgets with jitter, Redis token-bucket rate limiters, bulkheads isolating ATS/LLM/embedding calls, and a dead-letter queue for poison messages. `FAILED` is terminal only after budget exhaustion + DLQ.
- **NFR-4 Correctness of audit (new).** Under fault injection (crash between DB commit and Kafka publish), the ledger must remain consistent with committed state — proven by test.
- **NFR-5 Measurability (new).** The matching model ships with the evaluation harness of `03` §6 (retrieval, ranking, grounding, outcome, fairness) and a regression suite gating every model/prompt/weight change.
- **NFR-6 Portability (new).** No JDK preview flags in production; run on Java 25 LTS + Spring Boot 4.1.1 within the certified matrix.

---

## 5. Application lifecycle — corrected state machine

The sixteen states are largely retained. Corrections:

- Location/eligibility failure routes to `DISCARDED` at the **`INGESTED → FILTERED`** gate (before scoring), not via a low `S_loc` term.
- `TAILORING` is entered by an **async worker**, not synchronously during discovery.
- `PENDING_APPROVAL → SUBMITTING` requires: consumer path → candidate's explicit signed authorization; B2B path → either signed authorization or auto-apply rule satisfaction (calibrated threshold + remote + no essays + no demographic requirement).
- `SUBMITTING → SUBMITTED` requires an idempotent external receipt; a retry of a timed-out submission reconciles to the existing application, never a duplicate.
- `SUBMITTING → FAILED` only after retry budget exhausted **and** dead-lettered with a diagnostic reason.

| Transition | Trigger | Corrected invariant |
|-----------|---------|---------------------|
| `INGESTED → FILTERED` | Constraint eval | **Hard eligibility gate** (location, work auth, comp floor) passes; else `DISCARDED` |
| `FILTERED → MATCHED` | Scoring | Composite over Skill/Exp/Sem/Domain computed (location already gated out) |
| `MATCHED → SHORTLISTED` | Threshold | Composite ≥ **calibrated** shortlist threshold for the role family |
| `SHORTLISTED → TAILORING` | Async worker | Handled off the discovery thread; not synchronous |
| `TAILORING → PENDING_APPROVAL` | Verification | Passes LLM critic **and** deterministic grounding verifier |
| `PENDING_APPROVAL → SUBMITTING` | Authorization | Consumer: candidate submits in own session. B2B: signed auth or auto-apply rule |
| `SUBMITTING → SUBMITTED` | Receipt | Idempotent external receipt; retries reconcile, never duplicate |
| `SUBMITTING → FAILED` | Failure | Retry budget exhausted **and** dead-lettered |

---

## 6. Phased roadmap (revised)

- **Phase 1 — Ingestion & taxonomy.** Postgres + pgvector + HNSW; resume parsing with ESCO/Lightcast mapping (+ coverage metric); **public-GET** discovery adapters for Greenhouse/Lever/Ashby/Workday CXS. On Java 25 LTS + Boot 4.1.1.
- **Phase 2 — Match engine, grounded RAG & evaluation.** Multi-stage retrieval with hard gates; cross-encoder rerank; grounded RAG with **deterministic verifier**; **evaluation harness live** (this is not optional and not later). Prompt-injection defences in place before any untrusted text reaches a model.
- **Phase 3 — HITL console, outbox, compliance.** React 19 dashboard + WebSocket telemetry; candidate approval + signed authorization; **transactional outbox** and idempotency; GDPR / EU AI Act / LL144 controls; consumer-path browser assist (honest, user-supervised).
- **Phase 4 — B2B partner automation & population learning.** Automated submission on the employer-partner path only, gated on calibrated thresholds and compliance invariants; population-level, held-out-validated weight learning behind version flags; annual bias audit.

---

## 7. Explicitly out of scope

- Any automated submission on behalf of a consumer job seeker who does not personally submit.
- Any anti-detection, fingerprint-spoofing, proxy-evasion, or CAPTCHA/WAF-bypass capability.
- Any use of ATS write credentials the operator does not legitimately hold.
- Any model authority to introduce a fact or trigger an external action.
