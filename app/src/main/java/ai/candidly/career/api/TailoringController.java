package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.ScreeningDecision;
import ai.candidly.career.domain.TailoringJob;
import ai.candidly.career.domain.TailoringJobRepository;
import ai.candidly.career.tailoring.TailoringJobService;

/**
 * SHORTLISTED -> TAILORING (docs/04 §5), async as docs/03 §4 specifies: this returns a
 * {@link TailoringJob} handle immediately (202) rather than blocking the request for the
 * 15-40s critic loop. Poll {@code GET /api/tailoring/jobs/{id}} for the result - no
 * WebSocket/STOMP push yet (see CLAUDE.md simplifications).
 */
@RestController
@RequestMapping("/api/tailoring")
public class TailoringController {

    private final TailoringJobService tailoringJobService;
    private final TailoringJobRepository tailoringJobRepository;
    private final CandidateRepository candidateRepository;
    private final JobPostingRepository jobPostingRepository;
    private final MatchScorecardRepository matchScorecardRepository;

    public TailoringController(TailoringJobService tailoringJobService, TailoringJobRepository tailoringJobRepository,
            CandidateRepository candidateRepository, JobPostingRepository jobPostingRepository,
            MatchScorecardRepository matchScorecardRepository) {
        this.tailoringJobService = tailoringJobService;
        this.tailoringJobRepository = tailoringJobRepository;
        this.candidateRepository = candidateRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.matchScorecardRepository = matchScorecardRepository;
    }

    /**
     * Refuses a BLOCKed posting outright and requires an existing shortlisted
     * MatchScorecard - tailoring never runs ahead of, or instead of, the match gate.
     */
    @PostMapping
    public ResponseEntity<TailoringJob> tailor(@RequestParam UUID candidateId, @RequestParam UUID jobPostingId) {
        var candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
        var job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));

        if (job.getScreeningDecision() == ScreeningDecision.BLOCK) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Job posting failed the guardrail screen and is blocked");
        }

        var scorecard = matchScorecardRepository.findByCandidateIdAndJobPostingId(candidateId, jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(409),
                        "No match scorecard yet - call POST /api/matches first"));
        if (!scorecard.isShortlisted()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Candidate is not shortlisted for this job");
        }

        TailoringJob job2 = tailoringJobService.submit(candidate, job);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job2);
    }

    @GetMapping("/jobs/{jobId}")
    public TailoringJob getJob(@PathVariable UUID jobId) {
        return tailoringJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Tailoring job not found"));
    }

    /** The Applications UI's listing for one candidate - every tailoring run, any status. */
    @GetMapping("/jobs")
    public List<TailoringJob> listJobs(@RequestParam UUID candidateId) {
        return tailoringJobRepository.findByCandidateId(candidateId);
    }
}
