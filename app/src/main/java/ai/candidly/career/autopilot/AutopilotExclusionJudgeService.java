package ai.candidly.career.autopilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Semantic backstop for {@link AutopilotService}'s exclude-keyword pre-filter: literal
 * substring matching (see {@code AutopilotService.passesKeywordFilters}) misses
 * paraphrases - "no sales" doesn't catch "business development" - so this asks one Noul
 * per shortlisted posting that already survived the substring check, fanned into a
 * single TypeSafe request per cycle, the same fan-out shape as {@code
 * retrieval.CrossEncoderRerankService}. Only structured, already-screened posting fields
 * go into the judgment - never {@link JobPosting#getRawDescription()} - per docs/02 §1
 * C-1. {@link #CONFLICT_THRESHOLD} is this feature's own starting point, not a spec
 * value, the same "calibrate before trusting it unattended" caveat {@code
 * FuzzyDuplicateJobPostingService} documents for its own threshold.
 */
@Service
public class AutopilotExclusionJudgeService {

    private static final double CONFLICT_THRESHOLD = 0.6;

    private final TypeSafeClient typeSafeClient;
    private final AuditLedgerService auditLedgerService;

    public AutopilotExclusionJudgeService(TypeSafeClient typeSafeClient, AuditLedgerService auditLedgerService) {
        this.typeSafeClient = typeSafeClient;
        this.auditLedgerService = auditLedgerService;
    }

    /** @return {@code candidates} minus any posting the model judges conflicts with a stated exclusion. */
    public List<MatchScorecard> filterConflicting(UUID candidateId, Set<String> excludeKeywords, List<MatchScorecard> candidates) {
        if (excludeKeywords.isEmpty() || candidates.isEmpty()) {
            return candidates;
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("candidate_exclusions", excludeKeywords);

        List<Map<String, Object>> postings = new ArrayList<>();
        for (MatchScorecard scorecard : candidates) {
            JobPosting job = scorecard.getJobPosting();
            postings.add(Map.of(
                    "title", job.getTitle(),
                    "company", job.getCompany(),
                    "domain", job.getDomain() == null ? "" : job.getDomain(),
                    "mandatory_skills", job.getMandatorySkillIds(),
                    "preferred_skills", job.getPreferredSkillIds()));
        }
        state.put("postings", postings);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            questions.put("conflicts_" + i, TypeSafeQuestion.noul(
                    "Given `candidate_exclusions` (things this candidate does not want in a job), does "
                            + "`postings[" + i + "]` conflict with any of them - including close paraphrases "
                            + "and related roles, not just literal keyword matches?",
                    "This posting matches something the candidate asked to exclude",
                    "This posting does not conflict with any stated exclusion"));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        List<MatchScorecard> kept = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            MatchScorecard scorecard = candidates.get(i);
            double conflictProbability = response.answers().get("conflicts_" + i).noul();
            if (conflictProbability >= CONFLICT_THRESHOLD) {
                JobPosting job = scorecard.getJobPosting();
                auditLedgerService.record(AuditEventType.AUTOPILOT_EXCLUSION_FILTERED, candidateId,
                        "job=%s company=%s title=%s conflictProbability=%.2f".formatted(
                                job.getId(), job.getCompany(), job.getTitle(), conflictProbability));
            } else {
                kept.add(scorecard);
            }
        }
        return kept;
    }
}
