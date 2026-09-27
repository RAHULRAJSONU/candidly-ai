package ai.candidly.career.autopilot;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEvent;
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
 * The "AI Job Application Agent" (Autopilot mock concept) - discovers, scores and tailors
 * applications on a schedule with no manual step, but never submits one past this app's
 * existing human-in-the-loop gate. This is a deliberate scoping decision: docs/00/docs/02's
 * core no-auto-submission/HITL-by-default guarantee is a load-bearing compliance property
 * (adverse-action and LL144 reasoning both assume a human decided to submit), not a default
 * this feature gets to relax just because the mock depicts full autonomy.
 *
 * <p><b>What "Auto Apply" actually does here:</b> when enabled, each cycle finds shortlisted
 * matches, generates a tailored resume/cover letter for up to {@link
 * #MAX_NEW_MATCHES_PER_CYCLE} of them via the normal {@link TailoringJobService#submit}
 * path, and leaves each resulting {@link TailoredArtifact} sitting in {@link
 * TailoredArtifactStatus#PENDING_APPROVAL} - i.e. it pushes work into the candidate's
 * ordinary Applications/console review queue automatically instead of requiring the
 * candidate to trigger tailoring by hand for every match. It never calls {@link
 * TailoringReviewService#approve}; only a human click on the Applications page (or
 * console.html) moves an artifact to {@link TailoredArtifactStatus#APPROVED}, and this
 * codebase has no adapter that fires an outbound submission at a real third-party ATS from
 * there either way. "Apply" in the stage pipeline therefore means "queued for your review",
 * not "submitted".
 *
 * <p>"Follow up" is logged to the audit ledger ({@link AuditEventType#AUTOPILOT_FOLLOW_UP_LOGGED})
 * rather than actually sending an email, for the same reason - see {@code emailintake}'s
 * existing discipline of never giving a model send-access on a candidate's behalf.
 */
@Service
public class AutopilotService {

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
    private final MatchScorecardRepository matchScorecardRepository;
    private final TailoredArtifactRepository artifactRepository;
    private final InterviewRepository interviewRepository;
    private final AuditLedgerService auditLedgerService;
    private final AuditEventRepository auditEventRepository;
    private final AutopilotExclusionJudgeService exclusionJudgeService;
    private final int maxNewMatchesPerCycle;
    private final int recommendationPoolSize;
    private final int cycleIntervalHours;

    public AutopilotService(AutopilotSettingsRepository settingsRepository, CandidateRepository candidateRepository,
            JobRecommendationService jobRecommendationService, TailoringJobService tailoringJobService,
            MatchScorecardRepository matchScorecardRepository,
            TailoredArtifactRepository artifactRepository, InterviewRepository interviewRepository,
            AuditLedgerService auditLedgerService, AuditEventRepository auditEventRepository,
            AutopilotExclusionJudgeService exclusionJudgeService,
            @Value("${candidly.autopilot.max-new-matches-per-cycle:5}") int maxNewMatchesPerCycle,
            @Value("${candidly.autopilot.recommendation-pool-size:15}") int recommendationPoolSize,
            @Value("${candidly.autopilot.cycle-interval-hours:12}") int cycleIntervalHours) {
        this.settingsRepository = settingsRepository;
        this.candidateRepository = candidateRepository;
        this.jobRecommendationService = jobRecommendationService;
        this.tailoringJobService = tailoringJobService;
        this.matchScorecardRepository = matchScorecardRepository;
        this.artifactRepository = artifactRepository;
        this.interviewRepository = interviewRepository;
        this.auditLedgerService = auditLedgerService;
        this.auditEventRepository = auditEventRepository;
        this.exclusionJudgeService = exclusionJudgeService;
        this.maxNewMatchesPerCycle = maxNewMatchesPerCycle;
        this.recommendationPoolSize = recommendationPoolSize;
        this.cycleIntervalHours = cycleIntervalHours;
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
        List<MatchScorecard> candidates = jobRecommendationService.recommend(candidate, recommendationPoolSize);
        List<MatchScorecard> keywordFiltered = candidates.stream()
                .filter(MatchScorecard::isShortlisted)
                .filter(sc -> passesKeywordFilters(sc, settings))
                .filter(sc -> !settings.isRemoteOnly() || sc.getJobPosting().isRemote())
                .sorted(Comparator.comparingDouble(MatchScorecard::getCompositeScore).reversed())
                .toList();
        // Literal substring matching above misses paraphrases the candidate meant to exclude
        // (e.g. "no sales" vs. "business development") - this catches what that pass can't.
        List<MatchScorecard> eligible = exclusionJudgeService.filterConflicting(
                candidateId, settings.getExcludeKeywords(), keywordFiltered);

        int remainingToday = Math.max(0, settings.getDailyApplicationLimit() - submittedToday(candidateId));

        settings.markStage(AutopilotStage.CUSTOMIZE, "Generating tailored resumes for new matches");
        int newlyCustomized = 0;
        for (MatchScorecard scorecard : eligible) {
            if (newlyCustomized >= maxNewMatchesPerCycle || newlyCustomized >= remainingToday) {
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

        settings.markStage(AutopilotStage.APPLY, "Queuing tailored applications in your review queue - nothing is submitted automatically");
        int queuedThisCycle = 0;
        if (settings.isAutoApply()) {
            List<TailoredArtifact> pending = artifactRepository.findByCandidateId(candidateId).stream()
                    .filter(a -> a.getStatus() == TailoredArtifactStatus.PENDING_APPROVAL)
                    .toList();
            Set<String> alreadyQueued = auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId).stream()
                    .filter(e -> e.getEventType() == AuditEventType.AUTOPILOT_APPLICATION_SUBMITTED)
                    .map(AuditEvent::getDetails)
                    .collect(java.util.stream.Collectors.toSet());
            for (TailoredArtifact artifact : pending) {
                String marker = "job=" + artifact.getJobPosting().getId();
                boolean alreadyLogged = alreadyQueued.stream().anyMatch(d -> d.startsWith(marker));
                if (alreadyLogged) {
                    continue;
                }
                auditLedgerService.record(AuditEventType.AUTOPILOT_APPLICATION_SUBMITTED, candidateId,
                        "%s company=%s title=%s queued in your Applications review queue - approve or reject there before anything is submitted"
                                .formatted(marker, artifact.getJobPosting().getCompany(), artifact.getJobPosting().getTitle()));
                queuedThisCycle++;
            }
        }

        settings.markStage(AutopilotStage.TRACK, "Tracking submitted applications for status changes");

        settings.markStage(AutopilotStage.FOLLOW_UP, "Checking for applications needing a follow-up");
        if (settings.isAutoFollowUp()) {
            runFollowUps(candidateId);
        }

        settings.recordRun(Instant.now());
        settings.scheduleNextRun(Instant.now().plus(Duration.ofHours(cycleIntervalHours)));
        settings.markStage(AutopilotStage.IDLE, "Cycle complete - waiting for next scheduled run");
        settingsRepository.save(settings);

        auditLedgerService.record(AuditEventType.AUTOPILOT_CYCLE_COMPLETED, candidateId,
                "matchesFound=%d customized=%d queuedForReview=%d".formatted(eligible.size(), newlyCustomized, queuedThisCycle));
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
