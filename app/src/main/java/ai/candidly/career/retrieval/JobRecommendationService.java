package ai.candidly.career.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ai.candidly.career.ai.VectorMath;
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

    /** How much wider than the requested result count each retrieval stage casts its net, before fusion. */
    private static final int RETRIEVAL_OVERSAMPLE_FACTOR = 4;
    /** How much wider than the requested result count survives rerank, before the expensive per-job scorer runs. */
    private static final int RERANK_OVERSAMPLE_FACTOR = 2;

    private final LexicalSearchService lexicalSearchService;
    private final VectorSearchService vectorSearchService;
    private final CrossEncoderRerankService crossEncoderRerankService;
    private final CandidateExperienceRepository experienceRepository;
    private final JobPostingRepository jobPostingRepository;
    private final MatchOrchestratorService matchOrchestratorService;

    public JobRecommendationService(LexicalSearchService lexicalSearchService,
            VectorSearchService vectorSearchService,
            CrossEncoderRerankService crossEncoderRerankService,
            CandidateExperienceRepository experienceRepository,
            JobPostingRepository jobPostingRepository,
            MatchOrchestratorService matchOrchestratorService) {
        this.lexicalSearchService = lexicalSearchService;
        this.vectorSearchService = vectorSearchService;
        this.crossEncoderRerankService = crossEncoderRerankService;
        this.experienceRepository = experienceRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.matchOrchestratorService = matchOrchestratorService;
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

        int retrievalShortlistSize = limit * RETRIEVAL_OVERSAMPLE_FACTOR;
        List<UUID> denseRanking = vectorSearchService.nearestJobPostingIds(pooled, retrievalShortlistSize);

        String lexicalQuery = lexicalQueryText(experiences);
        List<UUID> lexicalRanking = lexicalQuery.isBlank()
                ? List.of()
                : lexicalSearchService.topByLexicalRank(lexicalQuery, retrievalShortlistSize);

        List<UUID> fusedIds = ReciprocalRankFusion.fuse(lexicalRanking, denseRanking).stream()
                .limit(retrievalShortlistSize)
                .toList();

        List<JobPosting> fusedJobs = fusedIds.stream()
                .map(jobPostingRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        int rerankedShortlistSize = limit * RERANK_OVERSAMPLE_FACTOR;
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
                .flatMap(exp -> java.util.stream.Stream.of(exp.getTitle(), exp.getNarrative()))
                .filter(text -> text != null && !text.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
