package ai.candidly.career.pipeline;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates/{candidateId}")
public class PipelineController {

    private final CandidateRepository candidateRepository;
    private final JobPostingRepository jobPostingRepository;
    private final InterviewRepository interviewRepository;
    private final OfferRepository offerRepository;
    private final PipelineSummaryService pipelineSummaryService;
    private final ManualApplicationRepository manualApplicationRepository;

    public PipelineController(CandidateRepository candidateRepository, JobPostingRepository jobPostingRepository,
            InterviewRepository interviewRepository, OfferRepository offerRepository,
            PipelineSummaryService pipelineSummaryService, ManualApplicationRepository manualApplicationRepository) {
        this.candidateRepository = candidateRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.interviewRepository = interviewRepository;
        this.offerRepository = offerRepository;
        this.pipelineSummaryService = pipelineSummaryService;
        this.manualApplicationRepository = manualApplicationRepository;
    }

    @GetMapping("/manual-applications")
    public List<ManualApplication> manualApplications(@PathVariable UUID candidateId) {
        return manualApplicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
    }

    @PostMapping("/manual-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ManualApplication addManualApplication(@PathVariable UUID candidateId,
            @Valid @RequestBody ManualApplicationRequest request) {
        requireCandidate(candidateId);
        return manualApplicationRepository.save(
                new ManualApplication(candidateId, request.company(), request.role(), request.appliedDate(), request.notes()));
    }

    @PutMapping("/manual-applications/{applicationId}/status")
    public ManualApplication updateManualApplicationStatus(@PathVariable UUID candidateId,
            @PathVariable UUID applicationId, @RequestBody Map<String, String> body) {
        ManualApplication application = manualApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Application not found"));
        application.updateStatus(ManualApplicationStatus.valueOf(body.get("status")), body.get("note"));
        return manualApplicationRepository.save(application);
    }

    @PostMapping("/manual-applications/{applicationId}/timeline")
    public ManualApplication addTimelineNote(@PathVariable UUID candidateId, @PathVariable UUID applicationId,
            @RequestBody Map<String, String> body) {
        ManualApplication application = manualApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Application not found"));
        application.addTimelineNote(body.get("note"));
        return manualApplicationRepository.save(application);
    }

    @GetMapping("/pipeline-summary")
    public PipelineSummaryService.PipelineSummary summary(@PathVariable UUID candidateId) {
        return pipelineSummaryService.summarize(candidateId);
    }

    @GetMapping("/interviews")
    public List<Interview> interviews(@PathVariable UUID candidateId) {
        return interviewRepository.findByCandidateId(candidateId);
    }

    @PostMapping("/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Interview scheduleInterview(@PathVariable UUID candidateId, @Valid @RequestBody InterviewRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        JobPosting job = requireJob(request.jobPostingId());
        return interviewRepository.save(new Interview(candidate, job, request.scheduledAt(),
                request.mode() == null ? InterviewMode.UNKNOWN : request.mode(), InterviewSource.MANUAL, request.notes()));
    }

    @PutMapping("/interviews/{interviewId}/status")
    public Interview updateInterviewStatus(@PathVariable UUID candidateId, @PathVariable UUID interviewId,
            @RequestBody Map<String, String> body) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Interview not found"));
        interview.updateStatus(InterviewStatus.valueOf(body.get("status")));
        return interviewRepository.save(interview);
    }

    @GetMapping("/offers")
    public List<Offer> offers(@PathVariable UUID candidateId) {
        return offerRepository.findByCandidateId(candidateId);
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Offer recordOffer(@PathVariable UUID candidateId, @Valid @RequestBody OfferRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        JobPosting job = requireJob(request.jobPostingId());
        return offerRepository.save(new Offer(candidate, job, request.compensationMinorUnits(), request.notes()));
    }

    @PutMapping("/offers/{offerId}/status")
    public Offer updateOfferStatus(@PathVariable UUID candidateId, @PathVariable UUID offerId,
            @RequestBody Map<String, String> body) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Offer not found"));
        offer.updateStatus(OfferStatus.valueOf(body.get("status")));
        return offerRepository.save(offer);
    }

    private Candidate requireCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }

    private JobPosting requireJob(UUID jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
    }
}
