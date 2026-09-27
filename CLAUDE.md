# CLAUDE.md

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
human step. That exact literal reading - an agent that submits without a human clicking
approve - directly contradicts docs/00/docs/02's core, deliberately-corrected
no-auto-submission/HITL-by-default guarantee, and was **not** built: see the `autopilot/`
package below, whose class javadoc spells out that "Auto Apply" only auto-generates and
queues a tailored artifact in the same `PENDING_APPROVAL` review queue every other
tailoring path uses, never calls `TailoringReviewService.approve`, and has no adapter
that fires an outbound submission at a real ATS. Everything upstream of that one
guardrail - autonomous discovery targeting, scheduled scoring/ranking cycles, tailoring,
follow-up tracking, and a live in-app status view of all of it - **is** built; see
`autopilot/` and `Autopilot.tsx`/`AutopilotSettings.tsx` below. `frontend/` otherwise
follows the other mock concept (the one with Dashboard/Job Discovery/Matches/
Applications/Compliance nav) and implements only the screens today's backend actually
has real data for:

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
  src/lib/currency.ts     Every money value's single formatting/conversion point (mirrors
                          backend CurrencyCodes): Intl.NumberFormat-backed formatMoney/
                          formatCompRange (en-IN locale for INR, so it renders lakh/crore
                          grouping - ₹57L/₹57,00,000 - not Western thousands-grouping), plus
                          toMinorUnits/fromMinorUnits so every edit form converts through the
                          currency's actual minor-unit factor rather than assuming /100. Set
                          per-candidate in Settings > Job Preferences and Career Vault's
                          Preferences tab (Candidate.preferredCurrency); JobPosting and
                          PipelineOffer carry their own independent currency instead. No FX
                          conversion exists anywhere in this app - an amount is always shown
                          in the currency it was recorded in.
  src/pages/
    Dashboard.tsx          stat cards + top matches + audit-ledger activity feed
    JobDiscovery.tsx       aligned to the `docs/ui_ux_mock` "Job Discovery" mock: source
                            pills (Greenhouse/Lever/Ashby/Workday/generic-JSON-LD "Company
                            career page", real per-source counts, click to filter - all
                            five render from the same generic `GET /api/discovery/sources`
                            list, see discovery/ below; no inert placeholder chip any
                            more), a mock-aligned filter bar of
                            label-over-value dropdown pills (Location/Work Type/Experience/
                            Salary/More Filters, the last holding skill + "Eligible roles
                            only" - all client-side, no backend filter endpoint; deliberately
                            no Job Type pill since JobPosting has no employment-type field)
                            with active-filter chips below that auto-hide while a pill's
                            panel is open (avoids the panel visually overlapping them - a
                            real overlap bug caught and fixed via Playwright during this
                            rework), a table-style list sorted newest-first, and a detail
                            panel with 5 tabs (Overview/Why it matches/Requirements/
                            Company/Similar Jobs). The eligibility checklist and skill-
                            coverage % are computed client-side by mirroring
                            EligibilityGateService's exact logic (incl. the hand-kept-in-
                            sync MANDATORY_SKILL_MINIMUM constant) rather than a new
                            endpoint; "Similar Jobs" calls `GET /api/job-postings/{id}/similar`
                            (retrieval.SimilarPostingJudgeService - a real TypeSafe judgment,
                            promoted off a former client-side skill/domain-overlap heuristic of
                            the same name), fetched lazily only once that tab is opened. Save/
                            Not Interested are per-browser
                            localStorage prefs keyed by candidate id (`lib/savedJobs.ts`,
                            same pattern as `lib/connectedAccounts.ts`) since there's no
                            backend model for either.
    Matches.tsx             list of MatchScorecards for the selected candidate
    MatchExplanation.tsx    per-match breakdown (skill/experience/semantic/domain
                            bars, eligibility, reason codes) + submit-for-tailoring
    Applications.tsx        the HITL review queue (pending/history), same data
                            TailoringReviewController/console.html already serves,
                            plus an ATS Optimization panel (deterministic checks,
                            not a model judgment) per selected artifact
    Compliance.tsx          audit-chain verify + retention purge + LL144 bias report
                            + GDPR export/erase, all against real endpoints
    CareerVault.tsx          matches docs/ui_ux_mock's "Career Vault" mock: a hero
                            header (avatar, name/headline/location, a "Verified
                            Profile" badge once completeness hits 100% - otherwise a
                            clickable "N% complete" badge that jumps to the first
                            empty section - plus a Years Experience/Core Skills/
                            Companies/Key Achievements stat row, all derived
                            client-side from CareerVaultView, no new backend fields)
                            and 8 tabs (Overview, Skills, Experience, Achievements,
                            Education, Certifications, Projects, Preferences).
                            The photo-upload affordance (camera button +
                            PhotoCropModal) lives only on the hero avatar now
                            (EditableAvatar) - Personal Information below it shows
                            the same photo read-only via plain CandidateAvatar
                            instead of a second editable one, avoiding two photo
                            editors for one candidate on the same page. Overview
                            leads with the folded-in Personal Info/
                            Professional Summary cards (moved to the top of the tab,
                            not buried below - the header's Import Resume/Add
                            Evidence actions cover "quick actions," so there's no
                            separate Quick Actions panel duplicating them), then a
                            Recent Evidence feed blending experience/achievements/
                            education/certifications newest-first (each still shown
                            with a hardcoded "Verified" badge - pre-existing, not
                            identity/evidence verification, a known residual
                            wording risk) alongside a Profile Completeness checklist
                            (the same 5 sections CareerVaultService.completeness
                            counts, each with a done/not-done state and a
                            click-to-jump-to-that-tab affordance - replaces a set of
                            static, non-informative marketing tiles from an earlier
                            pass) and a Skills Overview panel (per-skill "mention
                            count" bars - occurrences of that skill string across
                            experience.rawSkillMentions/achievement.tags,
                            client-computed, honestly can be 0). The Skills tab now
                            also has a per-skill "Skill Details" grid backed by a
                            real persisted field (not a fabricated one this time -
                            see CandidateSkillProfile below): each skill on
                            rawSkillMentions gets an editable card for years of
                            experience, a Beginner/Intermediate/Expert level, a 1-5
                            confidence star rating, and when/what version it was
                            last used (e.g. "React 19"). Matched to the skill by
                            exact name (case-insensitive), not the normalized
                            skillId, since normalization can collapse several raw
                            mentions onto one taxonomy id. There's no separate Experience
                            Timeline panel on Overview any more either - Recent
                            Evidence already surfaces experience entries, and the
                            full timeline lives on the Experience tab, so a second
                            copy was pure duplication. Preferences merges Career
                            Interests/Job Preferences/minimum-compensation/Connected
                            Accounts plus a "Manage my data" toggle for GDPR
                            export/erase (replacing the old standalone Documents/
                            Privacy tabs - Documents is now the header's Import
                            Resume modal instead); work authorization now lives only
                            on Overview's Personal Information card (it used to be
                            shown, read-only, a second time on this tab too - the
                            Minimum Compensation card here now just points there
                            instead of repeating it). completeness meter itself is
                            unchanged (fraction of 5 populated sections, backend
                            CareerVaultService).
    InterviewPrep.tsx        category-tabbed question bank, free-text answer form
                            scored by a real TypeSafe Score judgment (clarity/depth/
                            relevance) against a candidate-picked target job
    Pipeline.tsx             funnel stat cards (matched -> shortlisted -> tailoring ->
                            pending -> approved -> interviews -> offers) + a
                            paste-a-recruiter-email box (TypeSafe Choice
                            classification) + interview/offer lists
    Onboarding.tsx           5-step "New Candidate" wizard (personal info -> work
                            auth/comp -> skills -> experience -> review), posts via
                            api.candidates.create then switches CandidateContext to
                            the new candidate and navigates to the Dashboard
    Autopilot.tsx            live 6-stage tracker (IDLE/SEARCH_JOBS/MATCH_AND_RANK/
                            CUSTOMIZE/APPLY/TRACK/FOLLOW_UP) for one candidate's
                            AutopilotService cycle, polling GET .../autopilot/status
                            + .../activity every 5s (setInterval, no WebSocket/SSE
                            anywhere in this app) + a stats row and an audit-ledger
                            activity feed
    AutopilotSettings.tsx    the agent's configuration screen - job preferences,
                            skills/keywords (incl. exclude-keyword "blacklist"),
                            Job Sources toggles, and (since this session) a Tracked
                            Greenhouse boards / Tracked Lever companies chip input
                            under Job Sources: candidate-named board-token/
                            company-slug pairs (stored namespaced, e.g.
                            "greenhouse:notion", in AutopilotSettings.
                            trackedCompanySlugs) that DiscoveryScheduler merges into
                            that adapter's own application.yml board list at poll
                            time - see the autopilot/ and discovery/ entries below
    AiOps.tsx                global (not per-candidate) aggregated stats from GET
                            /ai-ops/summary + GET /config/ai (screening-decision and
                            tailored-artifact-status bar charts, grounding pass rate,
                            configured-provider cards) plus, since this session, a
                            LivePipelineFeed component (components/pipeline/
                            LivePipelineFeed.tsx) - the "animated visual pipeline":
                            polls GET /api/audit/pipeline-feed every 2.5s and renders
                            a 6-column stage rail (Discovered/Screened/Enriched/
                            Scored/Tailoring/Reviewed) that real postings visibly
                            move through as CSS-keyframe "pop-in" cards (plain inline
                            <style>, no animation library) - client-side state is a
                            Map<jobPostingId, ...> folded from each poll, monotonic
                            per job (a job only ever advances to a higher stage, never
                            regresses on a stale/out-of-order poll) and capped at 60
                            tracked jobs so a long-running tab doesn't grow unbounded -
                            plus, since this session, an AutopilotInsights component
                            (components/pipeline/AutopilotInsights.tsx), the "Autopilot
                            Insights" panel: loads GET /api/ai-ops/insights once (not
                            polled - a slower-moving aggregate, not live activity) and
                            renders three explainable breakdowns (By Source/By Domain/
                            By Mandatory Skill) of which ones actually correlate with a
                            tailored artifact getting approved or a candidate landing an
                            interview - a human-readable signal to act on by hand (track
                            more companies from a strong source, revisit
                            candidly.matching.weights.*), never a value this app feeds
                            back into scoring automatically (see aiops/InsightsService
                            below)
```

Autopilot (the mock concept depicting a fully autonomous submit-with-no-human-step
agent) remains the one thing deliberately **not** built, since it contradicts docs/00/
docs/02's HITL-by-default guarantee. Everything else the original mock survey listed as
"not built" - Career Vault, ATS scoring, AI mock interviews, email-inbox monitoring, an
onboarding wizard, interview/offer tracking - has since been implemented (backend +
frontend) once the requisite screens/data existed; see the `vault`, `ats`,
`interviewprep`, `pipeline`, and `emailintake` backend packages below. `static/
console.html` still exists and still works - it isn't replaced, just superseded in
scope by `Applications.tsx` for anyone using the new SPA.

Backend additions made to support the original 5-page frontend: `GET /api/candidates`
(list, since there's no auth yet to scope "my profile"), `GET /api/job-postings` (list,
excludes BLOCKed, now newest-discovered-first - see JobPosting.discoveredAt above), `GET
/api/matches?candidateId=` and `GET /api/matches/{id}`, `GET
/api/tailoring/jobs?candidateId=`. Additions for the six features above: `POST
/api/candidates` (onboarding); `GET /api/candidates/{id}/vault` + `POST .../vault/
{achievements|education|certifications}` + `PUT .../vault/skills` (upsert one skill's
CandidateSkillProfile); `GET /api/tailoring/artifacts/{id}/ats-score`;
`GET /api/interview-prep/questions[?category=]` + `POST /api/interview-prep/answers`;
`GET /api/candidates/{id}/pipeline-summary`, `GET/POST .../interviews`, `PUT .../
interviews/{id}/status`, `GET/POST .../offers`, `PUT .../offers/{id}/status`; `POST
/api/candidates/{id}/email-intake`.

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
                                + JobPostingSource (GREENHOUSE/LEVER/MANUAL - derived from the
                                DiscoveredJobPosting's namespaced sourceId prefix in
                                JobPostingIngestionService, not a new adapter/request field, so
                                no existing call site or test needed to change) and
                                JobPosting.discoveredAt (set once via @PrePersist rather than a
                                constructor param, for the same reason - both back the Job
                                Discovery frontend's Source/Posted columns; pre-existing rows
                                from before this field existed read back as null, which the
                                frontend treats as "unknown," not zero/epoch) + CurrencyCodes
                                (shared ISO 4217 normalize/validate used by Candidate.
                                preferredCurrency, JobPosting.currency, and Offer.currency -
                                every money field is integer minor units tagged with a
                                currency code; there's no FX-rate source, so amounts are
                                shown in whatever currency they were recorded in and never
                                converted. EligibilityGateService's comp-floor check treats a
                                posting whose currency differs from the candidate's preference
                                as not comparable rather than failing it - mirrored in
                                JobDiscovery.tsx's computeEligibilityChecks/salary filter,
                                which must stay in sync)
    ai/                        GroqChatClient, JinaEmbeddingClient, VectorMath
    typesafe/                  TypeSafeClient + request/response types (raw HTTP, no SDK)
    taxonomy/                  In-memory ESCO/Lightcast stand-in + SkillNormalizationService
    screening/                 JobPostingScreeningService (prompt-injection guardrail)
    matching/                  EligibilityGateService (hard gate) + CompositeScoringService
                                (docs/03 weighted scoring - S_sem and S_dom are both
                                TypeSafe Score judgments, via SemanticFitJudgeService and
                                DomainFitJudgeService respectively, not embedding cosine;
                                the four weights are `@Value`-injected from
                                `candidly.matching.weights.{skill,experience,semantic,domain}`,
                                defaulting to the same 0.47/0.29/0.12/0.12 docs/03 §2.1 values,
                                so they can be recalibrated per docs/03 §3 without a code change)
                                + MatchOrchestratorService
    retrieval/                 The docs/03 §1 funnel: LexicalSearchService (Postgres FTS,
                                Stage 2) + VectorSearchService (pgvector HNSW ANN via
                                JdbcTemplate, Stage 3) + ReciprocalRankFusion (k configurable
                                via `candidly.retrieval.rrf-k`, default 60, Stage 4) +
                                CrossEncoderRerankService (TypeSafe fan-out rerank, Stage 5a)
                                + JobRecommendationService (ties the funnel together, ending
                                in the gate+composite scorer, Stage 5b; oversample factors
                                configurable via `candidly.retrieval.{retrieval,rerank}-
                                oversample-factor`). JobRecommendationService fuses THREE
                                rankings into RRF, not two: the original lexical (candidate's
                                own experience titles/narratives) and dense (pooled experience
                                embedding) lists, plus a third lexical list built from the
                                candidate's own AutopilotSettings (keySkills + preferredRoles +
                                includeKeywords, via AutopilotSettingsRepository) - so a
                                candidate's stated preferences actually steer which postings
                                get scored, not just filter the output afterward the way
                                AutopilotService's keyword filter always has. A candidate with
                                no AutopilotSettings yet degrades to the original two-list
                                behavior. Also SimilarPostingJudgeService (not part of the match funnel -
                                backs the Job Discovery UI's "Similar Jobs" tab): a cheap
                                deterministic skill/domain-overlap count first shrinks the field
                                to 20 candidates, then one TypeSafe Noul per candidate (fan-out,
                                same shape as CrossEncoderRerankService) judges substantive
                                role similarity rather than shared-keyword overlap, capped to
                                the top 5 above a 0.5 probability threshold (backing GET
                                /api/job-postings/{id}/similar on JobPostingController)
    tailoring/                 GroundedResumeGenerator, DeterministicGroundingVerifier,
                                ClaimToneVerifier, ResumeTailoringService (critic loop) +
                                TailoringJobService/TailoringAsyncExecutor (async
                                dispatch, docs/03 §4) + TailoringReviewService (HITL
                                approve/reject, docs/01 §2)
    discovery/                 FR-1 discovery: JobBoardDiscoveryAdapter (now carries its
                                own isEnabled(), gating only the scheduled poll - see
                                DiscoveryScheduler; `poll()` defaults to `poll(List.of())`,
                                the real abstract method is `poll(List<String>
                                additionalTargets)`) + GreenhouseDiscoveryAdapter (real
                                public Greenhouse Job Board API) + LeverDiscoveryAdapter
                                (real public Lever postings API, api.lever.co/v0/
                                postings/{company}) + AshbyDiscoveryAdapter (real public
                                Ashby Job Board API, api.ashbyhq.com/posting-api/
                                job-board/{boardName} - verified live against the real
                                `ashby` board during this session, same
                                gitlab/palantir-style "one fixed global host, company
                                identified by a single slug" shape as the other two) +
                                WorkdayDiscoveryAdapter (real Workday CXS API, POST
                                https://{host}/wday/cxs/{tenant}/{site}/jobs - the one
                                adapter of the four docs/04 FR-1 names that could NOT get
                                a gitlab-style verified-live demo default in this
                                session: unlike the other three, every Workday tenant is
                                served from its own numbered pod hostname, so there's no
                                single guessable global endpoint; `candidly.discovery.
                                workday.sites` ships empty for exactly that reason, and
                                `additionalTargets`/tracked-company expansion can't reach
                                it either, since a Workday site needs a hostname/tenant/
                                site triple that a flat tracked-slug string can't carry -
                                see the adapter's own javadoc for the full honest
                                accounting) + GenericJsonLdDiscoveryAdapter (a fifth,
                                deliberately different-shaped source: Schema.org
                                `JobPosting` structured data embedded on a company's own
                                career page, for any company that isn't on a named ATS at
                                all - `candidly.discovery.jsonld.career-page-urls` holds
                                full per-company page URLs rather than one fixed global
                                host + short slugs like the other four. Verified live
                                against a real Ashby-*hosted* individual job page (Ramp's,
                                `jobs.ashbyhq.com/ramp/{id}` - confirmed source=JSONLD,
                                domain="fintech", minYearsExperience=5, all correctly
                                extracted downstream), which server-renders one clean
                                `JobPosting` JSON-LD block; every other career site tried
                                during verification (GitLab, Meta, Figma, Notion,
                                Sourcegraph, Buffer, LinkedIn, Indeed, Workable,
                                SmartRecruiters, even Ashby's own board *listing* page) is
                                client-side rendered and embeds no JSON-LD in the initial
                                HTML at all - this adapter has no headless-browser/JS
                                layer (same "no Kafka/Docker/Kubernetes/Redis" restraint),
                                so a configured URL that only injects JSON-LD via
                                client-side JS simply yields zero postings, not an error.
                                No `AutopilotSettings.tsx` control exists yet for adding a
                                candidate-tracked URL under this adapter's namespace
                                ("jsonld:https://...") - an honest gap, though
                                `DiscoveryScheduler` would already pick one up if added)
                                + HeadlessCareerPageDiscoveryAdapter (a sixth source, added
                                after a real user asked to "scrape LinkedIn" for more
                                matching jobs: LinkedIn scraping was refused outright -
                                against their ToS regardless of login state, and legally
                                pursued in LinkedIn Corp. v. hiQ Labs - so this adapter is
                                scoped to company-owned career-listing pages only, with a
                                code-level guardrail rejecting any configured/tracked URL
                                whose host is linkedin.com before a browser is even
                                launched, not just an operator convention. Drives a real
                                headless Chromium (Playwright) to reach postings
                                `GenericJsonLdDiscoveryAdapter` can't - most real career
                                pages inject their listings via client-side JS rather than
                                embedding them statically. Renders the listing page,
                                extracts same-host anchor hrefs matching a configurable (or
                                default job/career/position/req heuristic) pattern as
                                job-detail links, then renders each one and reuses
                                `JsonLdJobPostingParser` (factored out of
                                `GenericJsonLdDiscoveryAdapter` in this same session so both
                                adapters share one JSON-LD extraction path) against the
                                rendered HTML - falling back to a title/body-text-only
                                extraction when no structured data is present even after
                                rendering, since that's still more useful than nothing and
                                never fabricates skill/comp fields it didn't find. Live
                                testing this session surfaced a real bug worth remembering:
                                `WaitUntilState.NETWORKIDLE` looks like the right wait
                                condition for "let the JS finish rendering" but isn't -
                                figma.com/careers and about.sourcegraph.com/jobs both timed
                                out after 30s waiting for it, because real sites keep
                                background analytics/polling connections open indefinitely
                                so network activity never truly idles. Fixed by waiting on
                                `DOMCONTENTLOADED` plus a short fixed settle delay instead
                                (`navigateAndSettle`). That same live test produced the
                                adapter's most important finding: figma.com/careers and
                                about.sourcegraph.com/jobs, sites that look client-rendered
                                and unscrapeable at a glance, both turned out to just be a
                                Greenhouse widget underneath - confirmed by hitting the
                                plain public Greenhouse API directly with the board tokens
                                found in the rendered page's links (`figma`: 163 open
                                postings, `sourcegraph91`: 8), with no headless browser
                                needed at all. Both were added to
                                `candidly.discovery.greenhouse.board-tokens` instead of
                                configured as headless portals - the practical lesson this
                                adapter's own investigation produced: check whether a
                                "custom-looking" career page is secretly an ATS widget
                                first, since that's zero-risk and needs no browser. Ships
                                with `candidly.discovery.headless.portals` empty by
                                default - every real career page tried during this
                                implementation turned out to be Greenhouse-backed, so there
                                is not yet a live-verified example of a genuinely
                                custom-hosted target for this adapter, an honest gap
                                matching this repo's own verification discipline: no
                                fabricated demo default was added just to have one. Needs
                                Playwright's Chromium binary installed once - not
                                offline-buildable the first time - see the pom.xml
                                dependency comment)
                                + DiscoveryScheduler (poll interval
                                configurable via `candidly.discovery.poll-interval-seconds`,
                                default 3600, per enabled adapter + manual trigger,
                                unconditional). Each adapter merges `additionalTargets` into
                                its own `application.yml` board/company list before polling -
                                DiscoveryScheduler resolves those from every RUNNING
                                candidate's `AutopilotSettings.trackedCompanySlugs`
                                (namespace-prefix stripped per adapter name), so a candidate
                                naming a company they want tracked actually expands what
                                gets polled, not just the fixed global seed list
                                (gitlab/palantir/ashby). PAUSED/STOPPED candidates don't
                                expand polling scope; a RUNNING candidate's tracked company
                                on a globally-disabled adapter still only reaches the manual
                                `POST /api/discovery/poll` trigger, same as the global list.
                                `JobPostingSource` gained ASHBY/WORKDAY, then JSONLD,
                                alongside GREENHOUSE/LEVER/MANUAL - each addition meant
                                recreating `job_posting`'s stale CHECK constraint again,
                                a third recurrence in this repo's history; see "Known
                                sharp edges" below, this is the same recurring gap as
                                `audit_event.event_type`, not a new kind of bug.
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
                                retention/purge on AuditController). Also
                                PipelineActivityService, which turns the ledger into the
                                AI Ops page's "Live Pipeline" animated feed (GET /api/
                                audit/pipeline-feed on AuditController; frontend polls
                                every 2.5s - no WebSocket/SSE, same polling-only
                                precedent as Autopilot.tsx). JobPostingIngestionService
                                now records JOB_POSTING_DISCOVERED/_SCREENED/_EXTRACTED/
                                _EMBEDDED (subjectId = the job posting's own id, emitted
                                in that pipeline order) alongside the pre-existing
                                MATCH_SCORED/TAILORED_ARTIFACT_GENERATED/
                                TAILORED_ARTIFACT_REVIEWED (subjectId = candidateId,
                                unchanged) - PipelineActivityService recovers each of
                                those three's job posting id by parsing the `job=<uuid>`
                                marker every one of their `details` strings already
                                carries (TailoringReviewService's approve/reject now
                                writes one too, for the same reason), the same way
                                AutopilotService already parses its own `job=` markers to
                                detect "already queued." Per-event stage is derived from
                                event type, with MATCH_SCORED splitting into SCORED/
                                SHORTLISTED and TAILORED_ARTIFACT_REVIEWED into APPROVED/
                                REJECTED by substring-checking `details`
                                (`shortlisted=%s`/`decision=%s`, both written by the
                                emitting service). Adding these four new AuditEventType
                                values required manually recreating audit_event's stale
                                Postgres CHECK constraint (see the "Known sharp edges"
                                entry below - it was already stale before this session,
                                missing 3 older enum values too).
    vault/                      Career Vault: Achievement/Education/Certification
                                entities (+ repositories/request DTOs) and
                                CareerVaultService, which assembles a CareerVaultView
                                (skills + experience, read-only, alongside the 3
                                candidate-editable sections) and computes
                                `completeness` as the fraction of 5 populated
                                sections - plain CRUD/aggregation, no TypeSafe call
                                (backing GET /api/candidates/{id}/vault, POST .../
                                vault/{achievements|education|certifications} on
                                CareerVaultController). Also CandidateSkillProfile
                                (+ repository/request DTO): a candidate's own rating
                                of one skill from rawSkillMentions - years of
                                experience, a BEGINNER/INTERMEDIATE/EXPERT
                                proficiencyLevel, a 1-5 confidenceScore, and
                                lastUsedOn/lastUsedVersion - keyed on
                                (candidate_id, skillName) case-insensitive rather
                                than the normalized skillId, upserted via
                                PUT .../vault/skills and surfaced as
                                CareerVaultView.skillProfiles; wired into
                                CandidateDataSubjectService export/erase like every
                                other candidate-owned table
    ats/                        AtsScoreService: deterministic ATS-optimization
                                checks against a tailored artifact's content
                                (standard section headings, 150-1200 word count,
                                dated work history via year regex, no tables/
                                graphics, mandatory-skill keyword coverage >=60%) -
                                explicitly not a TypeSafe judgment, since these are
                                exact/rule-based checks per the typesafe-ai skill's
                                "keep known rules in code" guidance (backing GET
                                /api/tailoring/artifacts/{id}/ats-score on
                                TailoringReviewController)
    interviewprep/              AI mock interviews: a static InterviewQuestionBank
                                (8 questions across SYSTEM_DESIGN/CODING/BEHAVIORAL/
                                DOMAIN) + MockInterviewAnswerJudgeService, a genuine
                                TypeSafe Score judgment (clarity/depth/relevance,
                                5 levels each) over the candidate's free-text answer
                                plus the target job's already-screened structured
                                fields only (never raw job-posting text, same C-1
                                discipline as tailoring/) - a blank answer
                                short-circuits without calling TypeSafe (backing GET
                                /api/interview-prep/questions[?category=], POST
                                /api/interview-prep/answers on
                                InterviewPrepController)
    pipeline/                   Interview/offer tracking: Interview/Offer entities
                                (tables `interview`/`job_offer`) + PipelineSummaryService
                                (aggregates matched/shortlisted/tailoring/
                                pendingApproval/approved/interviews/offers counts,
                                plain aggregation) - manual scheduling/recording is
                                CRUD; automatic Interview creation from a classified
                                email is emailintake/'s job, not this package's
                                (backing GET .../pipeline-summary, GET/POST .../
                                interviews, PUT .../interviews/{id}/status, GET/POST
                                .../offers, PUT .../offers/{id}/status on
                                PipelineController)
    emailintake/                Email monitoring: HeuristicDateTimeExtractor (plain
                                regex/DateTimeFormatter date+time parsing - an exact-
                                lookup case, not a semantic judgment, per the
                                typesafe-ai skill) + EmailIntakeService, which runs
                                real TypeSafe Choice judgments for `email_type`
                                (INTERVIEW_INVITATION/REJECTION/FOLLOW_UP/OTHER) and
                                `mode` (VIRTUAL/ONSITE/PHONE/UNKNOWN), plus a
                                conditional `related_application` Choice built from
                                the candidate's actual known MatchScorecards (a
                                closed enumerable set the model selects from, never
                                free-text generation) - a recruiter email is
                                third-party text, so it's classified/extracted only,
                                never given tool access, matching the C-1 discipline
                                already applied to job-posting screening. When
                                classified as an interview invitation matched to a
                                known application, auto-creates an Interview row
                                (source EMAIL_EXTRACTED) using the regex-extracted
                                suggested time; the UI presents this as a
                                human-reviewable suggestion, not an autonomous action
                                on anyone's behalf (backing POST /api/candidates/
                                {id}/email-intake on EmailIntakeController). The same
                                batched request also asks one Noul, "does this email
                                match a typical job-scam pattern?" - free since the
                                email is already in-flight to TypeSafe; a flagged email
                                (`EmailIntakeResult.scamRisk`/`scamRiskProbability`,
                                threshold 0.6, audited as `EMAIL_SCAM_RISK_FLAGGED`)
                                still classifies and still creates an Interview when
                                applicable - the flag only adds a warning banner in
                                Pipeline.tsx's email-paste panel, it never blocks
                                anything, since a false positive must never hide a
                                real interview invitation.
    autopilot/                  The "AI Job Application Agent" (Autopilot mock concept):
                                AutopilotService runs a candidate-triggerable cycle that
                                finds shortlisted matches, tailors resumes for up to 5
                                new ones per cycle, and queues the results in the normal
                                Applications review queue - it never calls
                                TailoringReviewService.approve and this codebase has no
                                adapter that submits to a real ATS, so "Apply" here means
                                "queued for your review," preserving docs/00/docs/02's
                                HITL-by-default guarantee despite the mock depicting full
                                autonomy (see the class javadoc for the full rationale).
                                AutopilotExclusionJudgeService is a TypeSafe Noul fan-out
                                (one per shortlisted posting, mirrors
                                retrieval.CrossEncoderRerankService's shape) run after
                                AutopilotService's literal exclude-keyword substring
                                filter, to catch paraphrases the substring check misses
                                (e.g. "no sales" vs. "business development"); posting
                                fields sent are structured/already-screened only, never
                                rawDescription. Flagged postings are dropped from the
                                cycle and audited as AUTOPILOT_EXCLUSION_FILTERED with
                                the model's conflict probability (threshold 0.6, this
                                feature's own starting point per
                                FuzzyDuplicateJobPostingService's "calibrate before
                                trusting it unattended" precedent, not a spec value).
                                AutopilotSettings.trackedCompanySlugs (namespaced e.g.
                                "greenhouse:notion") is this feature's honest answer to
                                "let the agent search more broadly": public Greenhouse/Lever
                                APIs are board-token/company-slug lookups, not keyword
                                search, so a candidate naming companies they want tracked -
                                not an AI "find companies" step - is what actually expands
                                discovery/'s poll list; edited via AutopilotSettings.tsx's
                                Tracked Greenhouse boards/Tracked Lever companies fields.
                                AutopilotScheduler's poll interval
                                (`candidly.autopilot.scheduler-interval-seconds`, default
                                300) and AutopilotService's cycle-interval-hours/
                                recommendation-pool-size/max-new-matches-per-cycle (all
                                under `candidly.autopilot.*`, defaults 12/15/5 matching the
                                original hardcoded constants) are now `@Value`-injected
                                rather than private static constants.
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
    aiops/                      AiOpsService/AiOpsSummary (real aggregated numbers -
                                grounding pass rate, critic-loop usage, screening-decision
                                distribution - backing GET /api/ai-ops/summary, the AI Ops
                                page's stat cards/bar charts) + InsightsService/
                                InsightsSummary (plan Phase 3, backing GET /api/ai-ops/
                                insights, the "Autopilot Insights" panel): the same
                                "read-only, no TypeSafe call, plain in-memory grouping
                                over rows already in the database" approach AiOpsService/
                                BiasAuditReportService already use, computing three
                                independent breakdowns - by JobPostingSource, by
                                JobPosting.domain, and by mandatory skill (against
                                taxonomy.SkillTaxonomy) - of discovered/shortlisted/
                                tailored/approved/rejected/interviewed counts and the
                                approval/shortlist rates derived from them. A rate is
                                `null` (not 0.0) when its denominator is 0, so "no data
                                yet" is never rendered as "0%". Deliberately never feeds
                                back into scoring/ranking automatically - a human reads a
                                rate and decides by hand (track more companies from a
                                strong source, revisit candidly.matching.weights.*),
                                consistent with BiasAuditReportService's own
                                explainability posture
    api/                       REST controllers + ingestion services, incl.
                                JobPostingIngestionService (exact dedupe hash +
                                FuzzyDuplicateJobPostingService, a TypeSafe Noul fan-out
                                catching re-posts/cross-posts the exact hash can't, before
                                the screening gate). After screening passes (non-BLOCK),
                                if the incoming request left mandatorySkillIds/
                                preferredSkillIds/domain/minYearsExperience all empty (a
                                MANUAL posting that already states them is never
                                overridden) and `candidly.extraction.enabled` (default
                                true), JobPostingExtractionService backfills them: one
                                TypeSafe request holding one Choice question per
                                taxonomy.SkillTaxonomy entry (required/preferred/not
                                mentioned) plus one Choice question for domain against a
                                new taxonomy.DomainTaxonomy in-memory list (fintech,
                                healthcare, e-commerce, etc. - this implementation's own
                                stand-in, not an ESCO-style standard like SkillTaxonomy) -
                                "select instead of generate," the model can only pick an
                                id/label that already exists. minYearsExperience instead
                                uses a plain regex (ExperienceYearsExtractor, same
                                exact-lookup precedent as emailintake's
                                HeuristicDateTimeExtractor), not a TypeSafe call. This
                                closes what was previously a real gap (discovered
                                Greenhouse/Lever postings always had empty skill/domain
                                sets, silently making EligibilityGateService's
                                mandatory-skill gate and CompositeScoringService's skill-
                                Jaccard score no-ops for every non-MANUAL posting).
                                CandidateDataSubjectService (docs/02
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
rather than a dedicated cross-encoder model; all four discovery adapters named in docs/04
FR-1 are now implemented (Greenhouse/Lever/Ashby's real public APIs, plus Workday's real
CXS API), plus a fifth, generic Schema.org JSON-LD adapter for companies not on any named
ATS - Workday is the one with a real, honest gap left: no single guessable global
host the way the other three have, so it ships with an empty configured site list rather
than a verified-live demo default (see discovery/'s entry above and
WorkdayDiscoveryAdapter's javadoc); the generic JSON-LD adapter has a different honest
gap - it only sees markup present in the initial HTTP response, so it can't reach the
many career sites that inject JSON-LD via client-side JS - closed by a sixth adapter,
HeadlessCareerPageDiscoveryAdapter, which does now carry a real headless-browser layer
(Playwright) scoped specifically to that gap, a deliberate one-adapter exception to the
"no Kafka/Docker/Kubernetes/Redis" restraint rather than a reversal of it (see discovery/'s
entry above for why, and for the LinkedIn-scraping request it was built in response to and
explicitly does not serve); the HITL console is a single static HTML page
with vanilla JS and polling (now with a pending/history tab split and each artifact's
match scorecard inline), not the docs/01 React 19 + TanStack + WebSocket/STOMP telemetry
SPA.

What *is* real now, not simplified: tailoring is genuinely async off the request thread
(docs/03 §4) — `POST /api/tailoring` returns a `TailoringJob` handle in ~0s via virtual
threads, not a blocking 15-40s call; the retrieval funnel (docs/03 §1 Stages 2-5) runs
actual Postgres full-text search + pgvector HNSW cosine ANN, fused with RRF, reranked by
one batched TypeSafe call, before the gate+composite scorer ever touches a posting;
discovery pulls real live postings from GitLab's, Figma's, and Sourcegraph's public
Greenhouse boards, Palantir's public Lever board, Ashby's own public Ashby board, and a
real Ashby-hosted job page via the generic JSON-LD adapter (plus any candidate-tracked
boards/companies layered on top via AutopilotSettings, and any Workday site a real
deployment configures), not fixtures - Figma and Sourcegraph were added this session after
a headless-browser investigation (see HeadlessCareerPageDiscoveryAdapter's entry above)
found both companies' seemingly-custom, client-rendered career pages are secretly
Greenhouse widgets underneath, live-confirmed against the plain public API (163 and 8 open
postings respectively) with no headless browser needed at all;
every non-MANUAL posting that clears screening gets a
real TypeSafe extraction pass filling in its skill/domain/experience fields (see
JobPostingExtractionService above), not permanently-empty sets; which sources/domains/
skills actually correlate with an approval or an interview is a real, explainable
breakdown too (see aiops/InsightsService above), not a static claim.

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
  Recurred on `audit_event.event_type` when the four `JOB_POSTING_DISCOVERED/_SCREENED/
  _EXTRACTED/_EMBEDDED` values were added for the Live Pipeline feed - and the constraint
  was already stale even before that (missing `CANDIDATE_SUBMITTED_APPLICATION`/
  `AUTOPILOT_EXCLUSION_FILTERED`/`EMAIL_SCAM_RISK_FLAGGED`, added in earlier sessions
  without anyone rerunning the ALTER), which only hadn't surfaced yet because nothing had
  tried to insert one of those three event types against this particular local database
  since they were added. Fixed by dropping and recreating the constraint with the enum's
  complete current value list, not just the newly-added ones - worth checking for the same
  latent gap any time this constraint needs touching again. Recurred a second time in the
  same session on `job_posting.source_check` when `JobPostingSource` gained ASHBY/WORKDAY
  (plan Phase 4) - every Ashby discovery poll failed with `constraint [job_posting_
  source_check]` until it was dropped/recreated the same way. Two recurrences of the exact
  same class of bug in one session confirms this needs checking by habit, not luck, any
  time a `@Enumerated(STRING)` enum gains a value on an existing table.
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
  `tailored_artifact`, `tailoring_job`, `achievement`, `education`, `certification`,
  `interview`, `job_offer`, `candidate` itself - the 5 tables added by the Career
  Vault/pipeline features were wired into both export and erase when they were built,
  same discipline as the original set) but deliberately leaving that
  candidate's `audit_event` rows in place - `subjectId` there is a bare UUID with no FK,
  and `details` is built from reason-code/screening strings, not raw candidate PII, so a
  post-erasure ledger entry pointing at a UUID with nothing left behind it is the
  intended end state, not leftover PII. If a new audit event type ever puts raw
  candidate data into `details` (a free-text reviewer note already does, in
  `TailoringReviewService` - accepted as a known residual risk for now), erasure stops
  being clean and this needs revisiting.

Gaps found during an end-to-end discovery -> ingestion -> extraction -> retrieval ->
scoring validation pass (fresh DB, real Greenhouse/Lever/Ashby/JSON-LD postings, one
real candidate onboarded from an actual resume), fixed in a follow-up pass the same
session:
- **Vacuous skill match inflated non-technical postings above real matches - fixed.**
  `CompositeScoringService.jaccard()` and `EligibilityGateService.mandatorySkillCoverage()`
  both return `1.0` when the job side of the comparison is empty - i.e. when
  `JobPostingExtractionService` correctly found no taxonomy skill mentioned in a posting
  (a legitimate outcome for a sales/admin/design role, not an extraction failure). That
  made "this posting states no skill requirement" score identically to "this candidate
  has every skill this posting requires." Live-reproduced before the fix: a Principal
  Engineer candidate's top two recommendations by composite score were GitLab's "AI
  Transformation Owner, CRO" (0.90) and "Associate Renewals Manager" (0.84) - both
  non-technical, both with 0 extracted mandatory skills. **Fix applied:**
  `CompositeScoringService.score()` now redistributes `weightSkill` proportionally
  across experience/semantic/domain for a job with zero mandatory *and* zero preferred
  skills, instead of awarding a perfect score on a dimension with no signal (the same
  "can't fail/can't inflate what wasn't stated" principle already applied to
  cross-currency comp comparisons). `EligibilityGateService.mandatorySkillCoverage`'s
  own vacuous-pass was deliberately left unchanged - a posting can't be failed for
  skills it never asked for, that part was already correct.
  `CompositeScoringServiceTest` pins both the redistribution case and the unchanged
  fixed-weight case. Re-verified live post-fix: "Associate Renewals Manager" dropped
  0.84 -> 0.67 and several other vacuous-skill postings (e.g. Ashby's "Senior Product
  Designer") dropped from ~0.55 to ~0.16. **Residual, not fixed:** "AI Transformation
  Owner, CRO" only dropped to 0.80, still above "AI Engineer" (0.62) - because
  `CompositeScoringService.experienceScore()` has the *identical* vacuous-match shape
  (`job.getMinYearsExperience() <= 0` returns a perfect `1.0`), and GitLab happened not
  to state a years-of-experience requirement on that posting either. This is the same
  class of bug in a second dimension, not yet fixed - a natural next follow-up if
  ranking quality for postings with no stated experience requirement matters enough to
  prioritize.
- **The comp-floor eligibility check was a structural no-op for every discovered
  posting - partially fixed.** `DiscoveredJobPosting` (`discovery/
  DiscoveredJobPosting.java`) carried no compensation fields at all, so none of the five
  adapters ever populated `JobPosting.compMinMinorUnits`/`compMaxMinorUnits`, making
  `EligibilityGateService`'s `compComparable` check permanently `false` for every
  non-MANUAL posting (confirmed across all 16 postings in the original validation run).
  **Fix applied:** `DiscoveredJobPosting` gained nullable `compMinMinorUnits`/
  `compMaxMinorUnits`/`currency` fields, threaded through by `DiscoveryScheduler.
  toRequest`. Checked each source's real response shape live before writing any parsing
  code (per this repo's live-verification discipline) rather than assuming a shape:
  GitLab's public Greenhouse board and Palantir's public Lever board genuinely have no
  compensation field at all in their default API response (confirmed by inspecting the
  raw response keys) - `GreenhouseDiscoveryAdapter`/`LeverDiscoveryAdapter` correctly
  pass `null`, an honest source-data gap, not something fixable here.
  `AshbyDiscoveryAdapter` already called `?includeCompensation=true` but its DTO
  silently discarded the resulting `compensation` field - added `AshbyCompensation`/
  `AshbyCompensationComponent` records and now picks the first `Salary`-type
  `summaryComponent`, live-verified against Ramp's and Ashby's own boards.
  `GenericJsonLdDiscoveryAdapter` now parses Schema.org `baseSalary` (nested `value`
  `QuantitativeValue`, live-confirmed against Ramp's JSON-LD), restricted to
  `unitText: "YEAR"` since `compMinMinorUnits`/`compMaxMinorUnits` are compared directly
  against a candidate's *annual* comp floor elsewhere - an hourly/monthly figure yields
  no comp data rather than a silently wrong annual number. `WorkdayDiscoveryAdapter`
  still passes `null` (no live-verified tenant to check a shape against - same existing
  gap as its empty site list). Added `CurrencyCodes.toMinorUnits(long, String)` (mirrors
  the frontend's `toMinorUnits` in `lib/currency.ts`) so both adapters convert through
  each currency's own fraction-digit count rather than assuming a factor of 100.
  Re-verified live post-fix: Ashby's 5 postings and the one JSON-LD posting all now
  carry real comp data (e.g. Ramp's Security Engineer, Cloud: $211,400-$290,600 USD);
  Greenhouse/Lever correctly remain null.
- **Fixed: `GenericJsonLdDiscoveryAdapter` didn't trim the extracted title.**
  Live-confirmed a real posting's `title` field came through as `" Security Engineer,
  Cloud"` (leading space). `toDiscoveredPosting` now `.trim()`s title/company, and
  `addIfPresent` (used for location parts) trims defensively too. Re-verified live
  post-fix: the same Ramp posting's title now stores with no leading whitespace.
- **Stale Postgres NOT NULL column with no Java field is the same class of bug as the
  stale CHECK constraint above, just the inverse direction.** `AutopilotSettings` had a
  `highPriorityReviewThreshold` field removed from the entity in an earlier session, but
  `ddl-auto=update` never drops the now-orphaned `NOT NULL` column it had already created -
  so every `PUT /api/candidates/{id}/autopilot/settings` (including the very first one,
  which creates the row) failed with a generic 500 and no useful message, since Hibernate's
  generated INSERT simply never mentions a column the entity doesn't have, which Postgres
  then rejects as a NOT NULL violation. Confirmed via `\d autopilot_settings` in psql -
  the column was still there, still `NOT NULL`, with no default. Fixed non-destructively
  (`ALTER TABLE autopilot_settings ALTER COLUMN high_priority_review_threshold DROP NOT
  NULL`) rather than dropping the column outright. The general lesson, mirroring the CHECK-
  constraint entry above: removing a field from an already-migrated entity needs a manual
  schema follow-up just as much as adding one does - `ddl-auto=update` only ever adds.
- **`Page.NavigateOptions().setWaitUntil(NETWORKIDLE)` is not a reliable "JS finished
  rendering" signal against real sites.** Live-caught building `HeadlessCareerPage
  DiscoveryAdapter`: both `figma.com/careers` and `about.sourcegraph.com/jobs` timed out
  after 30s waiting for network idle, because real production sites keep background
  analytics/polling XHRs open indefinitely, so network activity never actually stops.
  `WaitUntilState.DOMCONTENTLOADED` plus a short fixed settle delay (3s) is what actually
  works in practice. Worth checking first if any future headless-browser work in this repo
  hangs on navigation.

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
