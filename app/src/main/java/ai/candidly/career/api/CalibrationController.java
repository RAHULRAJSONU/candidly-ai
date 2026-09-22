package ai.candidly.career.api;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.matching.ScoringConsistencyService;
import ai.candidly.career.matching.ScoringConsistencyService.ConsistencyReport;

/**
 * Manual calibration tooling (docs/03 §3: "calibrate against real outcomes"), same
 * manual-trigger shape as {@code DiscoveryController}'s poll rather than an automatic
 * job - see {@code ScoringConsistencyService}'s javadoc for what this actually measures.
 * {@code runs} is capped small since each one is a real, billed TypeSafe call pair, not
 * a free loop.
 */
@RestController
@RequestMapping("/api/calibration")
public class CalibrationController {

    private static final int MAX_RUNS = 20;

    private final ScoringConsistencyService consistencyService;
    private final CandidateRepository candidateRepository;
    private final JobPostingRepository jobPostingRepository;

    public CalibrationController(ScoringConsistencyService consistencyService, CandidateRepository candidateRepository,
            JobPostingRepository jobPostingRepository) {
        this.consistencyService = consistencyService;
        this.candidateRepository = candidateRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    @PostMapping("/consistency")
    public ConsistencyReport consistency(@RequestParam UUID candidateId, @RequestParam UUID jobPostingId,
            @RequestParam(defaultValue = "5") int runs) {
        if (runs < 1 || runs > MAX_RUNS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "runs must be between 1 and " + MAX_RUNS + " - each run is a real TypeSafe call pair");
        }
        var candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
        var job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
        return consistencyService.check(candidate, job, runs);
    }
}
