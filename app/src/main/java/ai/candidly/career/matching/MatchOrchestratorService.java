package ai.candidly.career.matching;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;

/**
 * Ties the hard eligibility gate to the composite scorer (docs/03 §2.1): the gate runs
 * first and a failure short-circuits straight to a rejected, unscored MatchScorecard
 * with human-readable reasons - never a low score a strong skill match could offset.
 */
@Service
public class MatchOrchestratorService {

    private final EligibilityGateService eligibilityGateService;
    private final CompositeScoringService compositeScoringService;
    private final CandidateExperienceRepository experienceRepository;
    private final MatchScorecardRepository scorecardRepository;
    private final AuditLedgerService auditLedgerService;
    private final double shortlistThreshold;

    public MatchOrchestratorService(EligibilityGateService eligibilityGateService,
            CompositeScoringService compositeScoringService,
            CandidateExperienceRepository experienceRepository,
            MatchScorecardRepository scorecardRepository,
            AuditLedgerService auditLedgerService,
            @Value("${candidly.thresholds.shortlist-score}") double shortlistThreshold) {
        this.eligibilityGateService = eligibilityGateService;
        this.compositeScoringService = compositeScoringService;
        this.experienceRepository = experienceRepository;
        this.scorecardRepository = scorecardRepository;
        this.auditLedgerService = auditLedgerService;
        this.shortlistThreshold = shortlistThreshold;
    }

    @Transactional
    public MatchScorecard evaluate(Candidate candidate, JobPosting job) {
        EligibilityGateService.EligibilityResult eligibility = eligibilityGateService.evaluate(candidate, job);

        MatchScorecard scorecard;
        if (!eligibility.passed()) {
            scorecard = new MatchScorecard(candidate, job, false, 0, 0, 0, 0, 0, false, eligibility.failureReasons());
        } else {
            var experiences = experienceRepository.findByCandidateId(candidate.getId());
            CompositeScoringService.ScoreBreakdown breakdown = compositeScoringService.score(candidate, experiences, job);
            boolean shortlisted = breakdown.compositeScore() >= shortlistThreshold;

            List<String> reasons = new ArrayList<>();
            reasons.add("eligibility gate passed (mandatory-skill coverage %.0f%%)"
                    .formatted(eligibility.mandatorySkillCoverage() * 100));
            reasons.add("skill=%.2f experience=%.2f semantic=%.2f domain=%.2f composite=%.2f (threshold %.2f)"
                    .formatted(breakdown.skillScore(), breakdown.experienceScore(), breakdown.semanticScore(),
                            breakdown.domainScore(), breakdown.compositeScore(), shortlistThreshold));
            if (job.getMandatorySkillIds().isEmpty() && job.getPreferredSkillIds().isEmpty()) {
                reasons.add("skill dimension not comparable (job stated no required/preferred skills) - "
                        + "its weight was redistributed across experience/semantic/domain, not counted as a perfect match");
            }

            scorecard = new MatchScorecard(candidate, job, true, breakdown.skillScore(), breakdown.experienceScore(),
                    breakdown.semanticScore(), breakdown.domainScore(), breakdown.compositeScore(), shortlisted, reasons);
        }

        // Hibernate's default flush ordering runs inserts before deletes within the same
        // flush, so save()-ing the replacement before the old row's delete has actually
        // hit the DB trips the (candidate_id, job_posting_id) unique constraint. Force
        // the delete to flush first.
        scorecardRepository.findByCandidateIdAndJobPostingId(candidate.getId(), job.getId())
                .ifPresent(existing -> {
                    scorecardRepository.delete(existing);
                    scorecardRepository.flush();
                });
        MatchScorecard saved = scorecardRepository.save(scorecard);
        // shortlisted=%s is parsed by AuditController's pipeline-feed endpoint to tell a
        // "scored but not shortlisted" event apart from a "shortlisted" one.
        auditLedgerService.record(AuditEventType.MATCH_SCORED, candidate.getId(),
                "job=%s shortlisted=%s %s".formatted(job.getId(), saved.isShortlisted(),
                        String.join("; ", saved.getReasonCodes())));
        return saved;
    }
}
