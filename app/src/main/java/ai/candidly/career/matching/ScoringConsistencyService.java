package ai.candidly.career.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.JobPosting;

/**
 * Answers a question this session's own code repeatedly flags but never measures:
 * "TypeSafe Score judgments are a starting point to calibrate against real outcomes
 * (docs/03 §3) - how stable are they, really?" {@code CompositeScoringService} calls a
 * live TypeSafe endpoint for S_sem/S_dom on every invocation with no caching, so calling
 * it {@code runs} times on the *same* (candidate, job) pair and looking at the spread is
 * a direct empirical answer, not a guess - low variance means the 0.72 shortlist
 * threshold (docs/03 §2.1, {@code application.yml}) is measuring something real; high
 * variance would mean a candidate could ping-pong across the shortlist line between two
 * identical requests, which the threshold and any adverse-action reasoning built on top
 * of it would need to account for.
 *
 * <p>Deliberately does not persist results or feed them back into scoring - this is a
 * read-only diagnostic tool for calibration work, not a production code path (see
 * {@code CalibrationController}'s manual-trigger-only, capped-runs design for why it's
 * gated the same way {@code DiscoveryController}'s manual poll is).
 */
@Service
public class ScoringConsistencyService {

    private final CompositeScoringService compositeScoringService;
    private final CandidateExperienceRepository candidateExperienceRepository;

    public ScoringConsistencyService(CompositeScoringService compositeScoringService,
            CandidateExperienceRepository candidateExperienceRepository) {
        this.compositeScoringService = compositeScoringService;
        this.candidateExperienceRepository = candidateExperienceRepository;
    }

    public ConsistencyReport check(Candidate candidate, JobPosting job, int runs) {
        List<CandidateExperience> experiences = candidateExperienceRepository.findByCandidateId(candidate.getId());

        List<CompositeScoringService.ScoreBreakdown> results = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            results.add(compositeScoringService.score(candidate, experiences, job));
        }

        return new ConsistencyReport(runs,
                stats(results.stream().mapToDouble(CompositeScoringService.ScoreBreakdown::semanticScore)),
                stats(results.stream().mapToDouble(CompositeScoringService.ScoreBreakdown::domainScore)),
                stats(results.stream().mapToDouble(CompositeScoringService.ScoreBreakdown::compositeScore)));
    }

    private Stat stats(DoubleStream stream) {
        double[] values = stream.toArray();
        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        double variance = java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        double min = java.util.Arrays.stream(values).min().orElse(0.0);
        double max = java.util.Arrays.stream(values).max().orElse(0.0);
        return new Stat(mean, Math.sqrt(variance), min, max);
    }

    public record Stat(double mean, double stddev, double min, double max) {
    }

    public record ConsistencyReport(int runs, Stat semanticScore, Stat domainScore, Stat compositeScore) {
    }
}
