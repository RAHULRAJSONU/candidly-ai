package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.ScreeningDecision;
import ai.candidly.career.retrieval.SimilarPostingJudgeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingIngestionService ingestionService;
    private final JobPostingRepository jobPostingRepository;
    private final SimilarPostingJudgeService similarPostingJudgeService;

    public JobPostingController(JobPostingIngestionService ingestionService, JobPostingRepository jobPostingRepository,
            SimilarPostingJudgeService similarPostingJudgeService) {
        this.ingestionService = ingestionService;
        this.jobPostingRepository = jobPostingRepository;
        this.similarPostingJudgeService = similarPostingJudgeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPosting create(@Valid @RequestBody JobPostingRequest request) {
        return ingestionService.ingest(request);
    }

    @GetMapping("/{id}")
    public JobPosting get(@PathVariable UUID id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
    }

    /**
     * The Job Discovery UI's listing - excludes BLOCKed postings (failed the guardrail
     * screen), newest-discovered first.
     */
    @GetMapping
    public List<JobPosting> list() {
        return jobPostingRepository.findAll().stream()
                .filter(job -> job.getScreeningDecision() != ScreeningDecision.BLOCK)
                .sorted(java.util.Comparator.comparing(JobPosting::getDiscoveredAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    /** Backs the Job Discovery detail panel's "Similar Jobs" tab - see SimilarPostingJudgeService. */
    @GetMapping("/{id}/similar")
    public List<JobPosting> similar(@PathVariable UUID id) {
        JobPosting reference = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
        return similarPostingJudgeService.findSimilar(reference, jobPostingRepository.findAll());
    }
}
