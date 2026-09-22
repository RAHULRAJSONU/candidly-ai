package ai.candidly.career.autopilot;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.audit.AuditEventRepository;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.retrieval.JobRecommendationService;
import ai.candidly.career.tailoring.TailoringJobService;
import ai.candidly.career.tailoring.TailoringReviewService;

/**
 * The "AI Job Application Agent" (Autopilot mock concept) - discover, score, tailor and
 * (conditionally) approve applications with no human step, on a schedule. This is a
 * deliberate, explicit product decision to build the concept docs/00/docs/02 originally
 * excluded for contradicting the HITL-by-default guarantee - made at the user's direction
 * after being shown that tradeoff.
 *
 * <p><b>Scoping boundary that IS still enforced:</b> "Apply" never fires an outbound HTTP
 * request at a real third-party ATS (Greenhouse/Lever/etc "apply" endpoint) on a
 * candidate's behalf - this codebase has no such adapter, and building one that
 * auto-submits at scale to live employers without a per-submission human decision is a
 * different, much higher-stakes kind of action than the rest of this feature (irreversible,
 * affects real third parties, no way to validate correctness of an AI-generated submission
 * to a real company from here). Instead, "apply" reuses the exact mechanism the HITL
 * console already treats as the end of this app's responsibility: moving a {@link
 * TailoredArtifact} to {@link TailoredArtifactStatus#APPROVED} via {@link
 * TailoringReviewService#approve}, just without a human clicking it - matching how
 * docs/04 FR-5 already draws the line (approval clears the artifact for the candidate's
 * own session; nothing in this app has ever submitted past that point, autopilot or not).
 * A match scoring at/above {@link AutopilotSettings#getHighPriorityReviewThreshold()}
 * still lands in the ordinary HITL queue instead of auto-approving, matching the mock's
 * own "Auto Apply: Enabled (with review for high-priority roles)" copy - i.e. even the
 * mock's autonomous concept keeps a human gate at the high-stakes end.
 *
 * <p>"Follow up" is logged to the audit ledger ({@link AuditEventType#AUTOPILOT_FOLLOW_UP_LOGGED})
 * rather than actually sending an email, for the same reason - see {@code emailintake}'s
 * existing discipline of never giving a model send-access on a candidate's behalf.
 */
@Service
public class AutopilotService {

    private static final int MAX_NEW_MATCHES_PER_CYCLE = 5;
    private static final int RECOMMENDATION_POOL_SIZE = 15;
    private static final Set<AuditEventType> ACTIVITY_EVENT_TYPES = Set.of(
            AuditEventType.AUTOPILOT_CYCLE_STARTED,
            AuditEventType.MATCH_SCORED,
            AuditEventType.TAILORED_ARTIFACT_GENERATED,
            AuditEventType.AUTOPILOT_APPLICATION_SUBMITTED,
            AuditEventType.TAILORED_ARTIFACT_REVIEWED,
            AuditEventType.AUTOPILOT_FOLLOW_UP_LOGGED,
            AuditEventType.AUTOPILOT_CYCLE_COMPLETED);

    private final AutopilotSettingsRepository settingsRepository;
    private final CandidateRepository candidateRepository;
    private final JobRecommendationService jobRecommendationService;
    private final TailoringJobService tailoringJobService;
    private final TailoringReviewService tailoringReviewService;
    private final MatchScorecardRepository matchScorecardRepository;
    private final TailoredArtifactRepository artifactRepository;
    private final InterviewRepository interviewRepository;
    private final AuditLedgerService auditLedgerService;
    private final AuditEventRepository auditEventRepository;

    public AutopilotService(AutopilotSettingsRepository settingsRepository, CandidateRepository candidateRepository,
            JobRecommendationService jobRecommendationService, TailoringJobService tailoringJobService,
            TailoringReviewService tailoringReviewService, MatchScorecardRepository matchScorecardRepository,
            TailoredArtifactRepository artifactRepository, InterviewRepository interviewRepository,
            AuditLedgerService auditLedgerService, AuditEventRepository auditEventRepository) {
        this.settingsRepository = settingsRepository;
        this.candidateRepository = candidateRepository;
        this.jobRecommendationService = jobRecommendationService;
        this.tailoringJobService = tailoringJobService;
        this.tailoringReviewService = tailoringReviewService;
        this.matchScorecardRepository = matchScorecardRepository;
        this.artifactRepository = artifactRepository;
        this.interviewRepository = interviewRepository;
        this.auditLedgerService = auditLedgerService;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public AutopilotSettings getOrCreateSettings(UUID candidateId) {
        return settingsRepository.findByCandidateId(candidateId)
                .orElseGet(() -> settingsRepository.save(new AutopilotSettings(candidateId)));
    }

    @Transactional
    public AutopilotSettings updateSettings(UUID candidateId, AutopilotSettingsRequest request) {
        AutopilotSettings settings = getOrCreateSettings(candidateId);
        settings.applyUpdate(request);
        return settingsRepository.save(settings);
    }

    @Transactional
    public AutopilotSettings start(UUID candidateId) {
        AutopilotSettings settings = getOrCreateSettings(candidateId);
        settings.start(Instant.now());
        settings.markStage(AutopilotStage.IDLE, "Waiting for next scheduled run");
        return settingsRepository.save(settings);
    }

    @Transactional
    public AutopilotSettings pause(UUID candidateId) {
        AutopilotSettings settings = getOrCreateSettings(candidateId);
        settings.pause();
        return settingsRepository.save(settings);
    }

    @Transactional
    public AutopilotSettings stop(UUID candidateId) {
        AutopilotSettings settings = getOrCreateSettings(candidateId);
        settings.stop();
        return settingsRepository.save(settings);
    }

    /** Runs one full agent cycle for a candidate, regardless of scheduler timing - also reachable as a manual "run now". */
    @Transactional
    public void runCycle(UUID candidateId) {
        AutopilotSettings settings = getOrCreateSettings(candidateId);
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        auditLedgerService.record(AuditEventType.AUTOPILOT_CYCLE_STARTED, candidateId,
                "Autopilot cycle started for " + candidate.getFullName());

        settings.markStage(AutopilotStage.SEARCH_JOBS, "Scanning enabled job sources for new matches");
        settings.markStage(AutopilotStage.MATCH_AND_RANK, "Scoring and ranking candidate postings");
        List<MatchScorecard> candidates = jobRecommendationService.recommend(candidate, RECOMMENDATION_POOL_SIZE);
        List<MatchScorecard> eligible = candidates.stream()
                .filter(MatchScorecard::isShortlisted)
                .filter(sc -> passesKeywordFilters(sc, settings))
                .filter(sc -> !settings.isRemoteOnly() || sc.getJobPosting().isRemote())
                .sorted(Comparator.comparingDouble(MatchScorecard::getCompositeScore).reversed())
                .toList();

        int remainingToday = Math.max(0, settings.getDailyApplicationLimit() - submittedToday(candidateId));

        settings.markStage(AutopilotStage.CUSTOMIZE, "Generating tailored resumes for new matches");
        int newlyCustomized = 0;
        for (MatchScorecard scorecard : eligible) {
            if (newlyCustomized >= MAX_NEW_MATCHES_PER_CYCLE || newlyCustomized >= remainingToday) {
                break;
            }
            boolean alreadyHasArtifact = artifactRepository.findByCandidateId(candidateId).stream()
                    .anyMatch(a -> a.getJobPosting().getId().equals(scorecard.getJobPosting().getId()));
            if (alreadyHasArtifact) {
                continue;
            }
            if (settings.isAiTailorResume()) {
                tailoringJobService.submit(candidate, scorecard.getJobPosting());
                newlyCustomized++;
            }
        }

        settings.markStage(AutopilotStage.APPLY, "Reviewing generated artifacts for auto-submission");
        int submittedThisCycle = 0;
        if (settings.isAutoApply()) {
            List<TailoredArtifact> pending = artifactRepository.findByCandidateId(candidateId).stream()
                    .filter(a -> a.getStatus() == TailoredArtifactStatus.PENDING_APPROVAL)
                    .toList();
            for (TailoredArtifact artifact : pending) {
                if (remainingToday - submittedThisCycle <= 0) {
                    break;
                }
                MatchScorecard scorecard = matchScorecardRepository
                        .findByCandidateIdAndJobPostingId(candidateId, artifact.getJobPosting().getId())
                        .orElse(null);
                boolean highPriority = scorecard != null
                        && scorecard.getCompositeScore() >= settings.getHighPriorityReviewThreshold();
                if (highPriority) {
                    continue; // left in the ordinary HITL queue for a human to decide
                }
                tailoringReviewService.approve(artifact.getId(), "Auto-approved by Autopilot agent (auto-apply enabled)");
                auditLedgerService.record(AuditEventType.AUTOPILOT_APPLICATION_SUBMITTED, candidateId,
                        "job=%s company=%s title=%s".formatted(artifact.getJobPosting().getId(),
                                artifact.getJobPosting().getCompany(), artifact.getJobPosting().getTitle()));
                submittedThisCycle++;
            }
        }

        settings.markStage(AutopilotStage.TRACK, "Tracking submitted applications for status changes");

        settings.markStage(AutopilotStage.FOLLOW_UP, "Checking for applications needing a follow-up");
        if (settings.isAutoFollowUp()) {
            runFollowUps(candidateId);
        }

        settings.recordRun(Instant.now());
        settings.scheduleNextRun(Instant.now().plus(Duration.ofHours(12)));
        settings.markStage(AutopilotStage.IDLE, "Cycle complete - waiting for next scheduled run");
        settingsRepository.save(settings);

        auditLedgerService.record(AuditEventType.AUTOPILOT_CYCLE_COMPLETED, candidateId,
                "matchesFound=%d customized=%d submitted=%d".formatted(eligible.size(), newlyCustomized, submittedThisCycle));
    }

    private void runFollowUps(UUID candidateId) {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<TailoredArtifact> approved = artifactRepository.findByCandidateId(candidateId).stream()
                .filter(a -> a.getStatus() == TailoredArtifactStatus.APPROVED)
                .filter(a -> a.getReviewedAt() != null && a.getReviewedAt().isBefore(sevenDaysAgo))
                .toList();
        Set<String> alreadyFollowedUp = auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId).stream()
                .filter(e -> e.getEventType() == AuditEventType.AUTOPILOT_FOLLOW_UP_LOGGED)
                .map(e -> e.getDetails())
                .collect(java.util.stream.Collectors.toSet());
        for (TailoredArtifact artifact : approved) {
            String marker = "job=" + artifact.getJobPosting().getId();
            boolean alreadyDone = alreadyFollowedUp.stream().anyMatch(d -> d.startsWith(marker));
            boolean hasInterview = interviewRepository.findByCandidateId(candidateId).stream()
                    .anyMatch(i -> i.getJobPosting().getId().equals(artifact.getJobPosting().getId()));
            if (!alreadyDone && !hasInterview) {
                auditLedgerService.record(AuditEventType.AUTOPILOT_FOLLOW_UP_LOGGED, candidateId,
                        "%s company=%s title=%s (logged only - no email sent automatically)"
                                .formatted(marker, artifact.getJobPosting().getCompany(), artifact.getJobPosting().getTitle()));
            }
        }
    }

    private boolean passesKeywordFilters(MatchScorecard scorecard, AutopilotSettings settings) {
        String haystack = (scorecard.getJobPosting().getTitle() + " " + scorecard.getJobPosting().getCompany() + " "
                + scorecard.getJobPosting().getRawDescription()).toLowerCase(Locale.ROOT);
        for (String exclude : settings.getExcludeKeywords()) {
            if (!exclude.isBlank() && haystack.contains(exclude.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private int submittedToday(UUID candidateId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return (int) auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId).stream()
                .filter(e -> e.getEventType() == AuditEventType.AUTOPILOT_APPLICATION_SUBMITTED)
                .filter(e -> !e.getOccurredAt().isBefore(startOfDay))
                .count();
    }

    public AutopilotStats stats(UUID candidateId) {
        List<MatchScorecard> scorecards = matchScorecardRepository.findByCandidateId(candidateId);
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long jobsScannedToday = scorecards.stream().filter(sc -> !sc.getDecidedAt().isBefore(startOfDay)).count();
        long goodMatchesFound = scorecards.stream().filter(MatchScorecard::isShortlisted).count();
        long companiesTargeted = scorecards.stream().map(sc -> sc.getJobPosting().getCompany()).distinct().count();
        long applicationsSubmitted = artifactRepository.findByCandidateId(candidateId).stream()
                .filter(a -> a.getStatus() == TailoredArtifactStatus.APPROVED)
                .count();
        long interviewsScheduled = interviewRepository.findByCandidateId(candidateId).size();
        return new AutopilotStats(jobsScannedToday, (int) submittedToday(candidateId), goodMatchesFound,
                companiesTargeted, applicationsSubmitted, interviewsScheduled);
    }

    public List<ai.candidly.career.audit.AuditEvent> activity(UUID candidateId, int limit) {
        return auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId).stream()
                .filter(e -> ACTIVITY_EVENT_TYPES.contains(e.getEventType()))
                .sorted(Comparator.comparing(ai.candidly.career.audit.AuditEvent::getOccurredAt).reversed())
                .limit(limit)
                .toList();
    }

    public record AutopilotStats(long jobsScannedToday, int applicationsSubmittedToday, long goodMatchesFound,
            long companiesTargeted, long totalApplicationsSubmitted, long interviewsScheduled) {
    }
}
