package ai.candidly.career.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ai.candidly.career.ai.VectorMath;
import ai.candidly.career.autopilot.AutopilotSettings;
import ai.candidly.career.autopilot.AutopilotSettingsRepository;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.matching.MatchOrchestratorService;

/**
 * The retrieval funnel from docs/03 §1: lexical filter (Stage 2, {@link
 * LexicalSearchService}) and dense retrieval (Stage 3, {@link VectorSearchService}) each
 * independently shortlist the full job-posting table, {@link ReciprocalRankFusion}
 * merges the two rankings (Stage 4), {@link CrossEncoderRerankService} narrows and
 * reorders that fused shortlist by relevance (Stage 5's rerank half), and only then does
 * the deterministic gate+composite-score pipeline ({@link MatchOrchestratorService})
 * score and gate what's left (Stage 5's scoring half). Retrieval and rerank are never
 * the ranking that ships - they only decide which postings are worth the (more
 * expensive, one-TypeSafe-call-per-posting) full scoring pass.
 */
@Service
public class JobRecommendationService {

    private final int retrievalOversampleFactor;
    private final int rerankOversampleFactor;
    private final int rrfK;

    private final LexicalSearchService lexicalSearchService;
    private final VectorSearchService vectorSearchService;
    private final CrossEncoderRerankService crossEncoderRerankService;
    private final CandidateExperienceRepository experienceRepository;
    private final JobPostingRepository jobPostingRepository;
    private final MatchOrchestratorService matchOrchestratorService;
    private final AutopilotSettingsRepository autopilotSettingsRepository;

    public JobRecommendationService(LexicalSearchService lexicalSearchService,
            VectorSearchService vectorSearchService,
            CrossEncoderRerankService crossEncoderRerankService,
            CandidateExperienceRepository experienceRepository,
            JobPostingRepository jobPostingRepository,
            MatchOrchestratorService matchOrchestratorService,
            AutopilotSettingsRepository autopilotSettingsRepository,
            @Value("${candidly.retrieval.retrieval-oversample-factor:4}") int retrievalOversampleFactor,
            @Value("${candidly.retrieval.rerank-oversample-factor:2}") int rerankOversampleFactor,
            @Value("${candidly.retrieval.rrf-k:60}") int rrfK) {
        this.lexicalSearchService = lexicalSearchService;
        this.vectorSearchService = vectorSearchService;
        this.crossEncoderRerankService = crossEncoderRerankService;
        this.experienceRepository = experienceRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.matchOrchestratorService = matchOrchestratorService;
        this.autopilotSettingsRepository = autopilotSettingsRepository;
        this.retrievalOversampleFactor = retrievalOversampleFactor;
        this.rerankOversampleFactor = rerankOversampleFactor;
        this.rrfK = rrfK;
    }

    public List<MatchScorecard> recommend(Candidate candidate, int limit) {
        List<CandidateExperience> experiences = experienceRepository.findByCandidateId(candidate.getId());

        List<float[]> vectors = experiences.stream()
                .map(CandidateExperience::getEmbedding)
                .filter(v -> v != null)
                .toList();
        float[] pooled = VectorMath.meanPool(vectors);
        if (pooled == null) {
            return List.of();
        }

        int retrievalShortlistSize = limit * retrievalOversampleFactor;
        List<UUID> denseRanking = vectorSearchService.nearestJobPostingIds(pooled, retrievalShortlistSize);

        String lexicalQuery = lexicalQueryText(experiences);
        List<UUID> lexicalRanking = lexicalQuery.isBlank()
                ? List.of()
                : lexicalSearchService.topByLexicalRank(lexicalQuery, retrievalShortlistSize);

        // A candidate's own AutopilotSettings (roles/skills/keywords they've explicitly
        // said they want) is folded in as a third ranked list, so stated preference
        // actually up-weights what gets scored - not just a post-hoc filter on the output,
        // which is all AutopilotService.runCycle's keyword filter ever did before this.
        String preferenceQuery = autopilotSettingsRepository.findByCandidateId(candidate.getId())
                .map(this::preferenceQueryText)
                .orElse("");
        List<UUID> preferenceRanking = preferenceQuery.isBlank()
                ? List.of()
                : lexicalSearchService.topByLexicalRank(preferenceQuery, retrievalShortlistSize);

        List<UUID> fusedIds = ReciprocalRankFusion.fuse(
                        List.of(lexicalRanking, denseRanking, preferenceRanking), rrfK).stream()
                .limit(retrievalShortlistSize)
                .toList();

        List<JobPosting> fusedJobs = fusedIds.stream()
                .map(jobPostingRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        int rerankedShortlistSize = limit * rerankOversampleFactor;
        List<JobPosting> rerankedJobs = crossEncoderRerankService.rerank(experiences, fusedJobs).stream()
                .limit(rerankedShortlistSize)
                .toList();

        return rerankedJobs.stream()
                .map(job -> matchOrchestratorService.evaluate(candidate, job))
                .filter(MatchScorecard::isHardEligibilityPassed)
                .sorted(Comparator.comparingDouble(MatchScorecard::getCompositeScore).reversed())
                .limit(limit)
                .toList();
    }

    /** A plain-language stand-in for "what this candidate is" - their own titles and narratives. */
    private String lexicalQueryText(List<CandidateExperience> experiences) {
        return experiences.stream()
                .flatMap(exp -> Stream.of(exp.getTitle(), exp.getNarrative()))
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /** A plain-language stand-in for "what this candidate says they want" - their declared
     * Autopilot preferences, not their history. */
    private String preferenceQueryText(AutopilotSettings settings) {
        return Stream.of(settings.getKeySkills(), settings.getPreferredRoles(), settings.getIncludeKeywords())
                .flatMap(Set::stream)
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
