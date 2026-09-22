package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.retrieval.JobRecommendationService;

/**
 * Dense-retrieval-backed job recommendations for a candidate (docs/03 §1, Stage 3 -
 * pgvector HNSW cosine narrows the pool before scoring). See
 * {@link JobRecommendationService} for the two-stage retrieve-then-score pipeline.
 */
@RestController
@RequestMapping("/api/candidates/{candidateId}/recommended-jobs")
public class JobRecommendationController {

    private final JobRecommendationService recommendationService;
    private final CandidateRepository candidateRepository;

    public JobRecommendationController(JobRecommendationService recommendationService,
            CandidateRepository candidateRepository) {
        this.recommendationService = recommendationService;
        this.candidateRepository = candidateRepository;
    }

    @GetMapping
    public List<MatchScorecard> recommend(@PathVariable UUID candidateId,
            @RequestParam(defaultValue = "10") int limit) {
        var candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
        return recommendationService.recommend(candidate, limit);
    }
}
