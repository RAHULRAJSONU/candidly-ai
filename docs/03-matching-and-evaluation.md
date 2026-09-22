# Matching, Scoring & Evaluation — Corrected

**Revision 2 — 21 September 2026**
Companion to `00-CHANGELOG-and-critical-corrections.md`.

The retrieval funnel in the original is sound. The corrections here are to the **scoring semantics** (a hard constraint modelled as a soft term), the **threshold provenance** (asserted, not calibrated), the **learning claim** (needs labels that don't exist per candidate), and the **latency budget** (arithmetically impossible as stated).

---

## 1. Retrieval funnel — kept, with disciplines

The five-stage funnel is retained:

1. **Ingestion** — public GET endpoints, SHA-256 dedupe.
2. **Lexical / hard-constraint filter** — PostgreSQL FTS (BM25-style) + hard gates.
3. **Dense retrieval** — pgvector HNSW cosine; single pinned embedding model (see `01` §4.1).
4. **RRF fusion** — `k = 60`, standard.
5. **Cross-encoder rerank + deterministic scoring**.

Disciplines added: pin `hnsw.ef_search` and benchmark recall against brute force (`01` §4.2); version embeddings; treat any ATS schema change as a funnel-breaking event caught by the stage-conversion metric (`01` §7).

---

## 2. The scoring model — corrected (C-4)

### 2.1 Location must be a gate, not a term

The original composite:

```
S_final = 0.40·S_skill + 0.25·S_exp + 0.15·S_loc + 0.10·S_sem + 0.10·S_dom
```

treats `S_loc` as a weighted addend, yet also describes it as a step function returning 1.0 or 0.0 for hard legal/geographic criteria (work authorization, location). **A hard eligibility constraint cannot be a weighted term**: with `S_loc = 0`, a candidate who is legally ineligible still scores `0.85` on skills+experience+semantics alone and can clear the 0.85 threshold. That produces submissions to jobs the candidate cannot legally hold.

**Corrected structure — gate first, then score:**

```
if (!hardEligibility(candidate, job)) reject;   // work auth, location, comp floor, visa
S_final = w_skill·S_skill + w_exp·S_exp + w_sem·S_sem + w_dom·S_dom
```

Location/authorization leaves the weighted sum entirely and becomes a **boolean pre-filter** at Stage 2 (it's already a hard constraint there — this just removes the double-counting). Re-normalise the remaining weights so they sum to 1:

| Component | Original weight | Corrected weight (renormalised) |
|-----------|-----------------|-------------------------------|
| Skill | 0.40 | **0.47** |
| Experience | 0.25 | **0.29** |
| Semantic fit | 0.10 | **0.12** |
| Domain | 0.10 | **0.12** |
| Location | 0.15 (as term) | **gate (removed from sum)** |

(Exact renormalised values are a starting point, not gospel — see §3 on calibration.)

### 2.2 Skill and experience sub-scores — keep, but state the assumptions

The Jaccard-weighted skill overlap (0.70 required + 0.30 preferred) and the experience ratio × seniority factor are reasonable. Two cautions:

- **Skill overlap depends entirely on taxonomy normalisation quality.** "Postgres" ≡ "PostgreSQL" only works if ESCO/Lightcast mapping is high-recall. Measure mapping coverage; unmapped skills silently deflate the score.
- **The mandatory-skill rejection invariant** ("fails if critical mandatory skills match < 60%") is a second hard gate — apply it *before* computing the composite, consistent with §2.1.

---

## 3. Thresholds are calibration outputs, not constants (C-6)

The original asserts `S_final ≥ 0.85` to shortlist and `≥ 0.92` to auto-apply. **These numbers have no provenance.** A score of 0.85 means nothing until the score distribution is characterised against real outcomes.

**Calibration protocol:**

1. **Assemble a labelled set.** Collect (candidate, job, outcome) triples where outcome ∈ {advanced-to-assessment, interview, offer, rejected}. The `SUBMITTED → ASSESSMENT/INTERVIEW/REJECTED` transitions in the state machine are exactly this label source — instrument them from day one.
2. **Plot score vs. outcome.** Find the score above which advance-rate materially exceeds base rate. *That* is the shortlist threshold, per role family — a `0.85` that's right for backend engineering may be wrong for design.
3. **Set auto-apply conservatively** at a score where false-positive cost (a wasted, possibly reputation-damaging application) is acceptable — and only on the B2B path (see `01`/`02`).
4. **Version the thresholds** as config, not code (`thresholds.shortlist`, `thresholds.autoApply`), tied to a model version for reproducibility and LL144 audit.

Until enough labels exist, run **HITL-only** (no auto-apply) and treat every threshold as provisional.

### 3.1 The learning claim needs population-level data

Phase 4's "continuously refine dimensional scoring weights" via outcome tracking is right in spirit but wrong in scale if done per candidate — one job seeker generates far too few labelled outcomes to learn weights. **Learn weights at the population level** (across consenting candidates, privacy-preserving), validate on a held-out set, and roll out behind a version flag. Never let one user's handful of rejections silently reshape their own scoring.

---

## 4. Latency budgets — realistic (C-5)

NFR-1 asserts grounded RAG generation of a tailored document in **8 seconds** while the evaluator-optimizer loop runs the **generator → critic up to 3 iterations**. A single generate+critique round trip against a frontier model is typically several seconds; three rounds plus retrieval plus the deterministic verifier cannot fit in 8 seconds. The two requirements contradict each other.

**Corrected budgets:**

| Operation | Original | Corrected | Rationale |
|-----------|----------|-----------|-----------|
| Lexical + dense filter of 500 postings | 120 s | 120 s (keep) | I/O + ANN, parallelised across virtual threads — achievable |
| Single generation pass | (implied in 8s) | 3–8 s | one model call |
| Full tailoring (retrieve → generate → up-to-3 critic loops → deterministic verify) | 8 s | **15–40 s, asynchronous** | inherently multi-call |
| Cross-encoder rerank (top ~30) | — | budget explicitly; it's CPU-heavy | rerankers are not free |

**Architectural consequence:** tailoring is **not** synchronous inside `processDiscoveredJob`. Scoring commits `SHORTLISTED`; a separate worker performs tailoring and drives `TAILORING → PENDING_APPROVAL`. The UI shows progress over WebSocket. This is already how the state machine is shaped — the fix is to stop pretending it's an 8-second synchronous call.

---

## 5. Grounding is verified deterministically, not by the critic alone

(Cross-reference `02` §1.4 — restated here because it's central to matching integrity.)

The evaluator–optimizer loop stays, but the **critic LLM is not the guarantee**. After the loop, a compiled verifier checks every claim in the artifact against the candidate's DB by set membership / exact match. Any unbacked skill, metric, employer or date → reject. The critic reduces iterations; the deterministic gate provides the invariant. Monitor how often the gate catches something the critic passed — that number is your prompt-drift alarm.

---

## 6. Evaluation harness (new — required for a regulated matcher)

You cannot ship a high-risk matching system you can't measure. Build, from Phase 2:

- **Retrieval metrics** — recall@k and nDCG@k against a hand-labelled gold set of (candidate, relevant-jobs). Track ANN recall vs. brute-force baseline continuously.
- **Ranking metrics** — does the cross-encoder actually improve ordering over RRF alone? If not, it's cost without benefit — prove it earns its latency.
- **Grounding metrics** — precision/recall of the deterministic verifier; rate of critic misses.
- **Outcome metrics** — advance-rate and interview-rate by score band; this both calibrates thresholds (§3) and evidences the EU AI Act accuracy obligation.
- **Fairness metrics** — score and advance-rate parity across protected groups, computed on the *separately stored* demographic data, for the LL144 bias audit. Never let this data touch the scoring path.
- **Regression suite** — a fixed eval set that every model/prompt/weight change must run against before rollout.

---

## 7. Summary of algorithmic changes

| Change | From | To |
|--------|------|----|
| Location handling | 0.15 weighted term | Boolean hard gate before scoring |
| Weights | 0.40/0.25/0.15/0.10/0.10 | 0.47/0.29/–/0.12/0.12 (renormalised, then calibrated) |
| Thresholds | Asserted 0.85 / 0.92 | Calibrated per role family from labelled outcomes; versioned |
| Weight learning | Per-candidate refinement | Population-level, held-out validated, versioned |
| Tailoring latency | 8 s synchronous with 3-loop critic | 15–40 s asynchronous |
| Grounding | LLM critic | LLM critic + deterministic DB verifier (invariant) |
| Measurement | none specified | Full eval harness incl. fairness, from Phase 2 |
