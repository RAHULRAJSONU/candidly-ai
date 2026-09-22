package ai.candidly.career.emailintake;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates/{candidateId}/email-intake")
public class EmailIntakeController {

    private final EmailIntakeService emailIntakeService;
    private final EmailReviewService emailReviewService;
    private final CandidateRepository candidateRepository;

    public EmailIntakeController(EmailIntakeService emailIntakeService, EmailReviewService emailReviewService,
            CandidateRepository candidateRepository) {
        this.emailIntakeService = emailIntakeService;
        this.emailReviewService = emailReviewService;
        this.candidateRepository = candidateRepository;
    }

    @PostMapping
    public EmailIntakeService.EmailIntakeResult classify(@PathVariable UUID candidateId,
            @Valid @RequestBody EmailIntakeRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        return emailIntakeService.classify(candidate, request.rawEmailText());
    }

    @GetMapping("/history")
    public List<EmailIntakeRecord> history(@PathVariable UUID candidateId) {
        return emailReviewService.history(candidateId);
    }

    @GetMapping("/needs-review")
    public List<EmailIntakeRecord> needsReview(@PathVariable UUID candidateId) {
        return emailReviewService.needsReview(candidateId);
    }

    @PostMapping("/{recordId}/approve-send")
    public EmailIntakeRecord approveSend(@PathVariable UUID candidateId, @PathVariable UUID recordId,
            @RequestBody Map<String, String> body) {
        return emailReviewService.approveSend(recordId, body.get("note"));
    }

    @PostMapping("/{recordId}/edit-send")
    public EmailIntakeRecord editAndSend(@PathVariable UUID candidateId, @PathVariable UUID recordId,
            @RequestBody Map<String, String> body) {
        return emailReviewService.editAndSend(recordId, body.get("editedText"), body.get("note"));
    }

    @PostMapping("/{recordId}/schedule")
    public EmailIntakeRecord schedule(@PathVariable UUID candidateId, @PathVariable UUID recordId,
            @RequestBody Map<String, String> body) {
        return emailReviewService.schedule(recordId, body.get("note"));
    }

    @PostMapping("/{recordId}/cancel")
    public EmailIntakeRecord cancel(@PathVariable UUID candidateId, @PathVariable UUID recordId,
            @RequestBody Map<String, String> body) {
        return emailReviewService.cancel(recordId, body.get("note"));
    }

    private Candidate requireCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }
}
