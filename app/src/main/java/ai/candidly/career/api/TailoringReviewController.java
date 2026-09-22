package ai.candidly.career.api;

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

import ai.candidly.career.ats.AtsScoreService;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.tailoring.TailoringReviewService;
import ai.candidly.career.tailoring.TailoringReviewService.ArtifactReviewView;

/** Backs the static HITL console at /console.html - see TailoringReviewService. */
@RestController
@RequestMapping("/api/tailoring/artifacts")
public class TailoringReviewController {

    private final TailoringReviewService reviewService;
    private final TailoredArtifactRepository artifactRepository;
    private final AtsScoreService atsScoreService;

    public TailoringReviewController(TailoringReviewService reviewService, TailoredArtifactRepository artifactRepository,
            AtsScoreService atsScoreService) {
        this.reviewService = reviewService;
        this.artifactRepository = artifactRepository;
        this.atsScoreService = atsScoreService;
    }

    /** Deterministic ATS-friendliness score for a generated artifact - see AtsScoreService. */
    @GetMapping("/{artifactId}/ats-score")
    public AtsScoreService.AtsScoreResult atsScore(@PathVariable UUID artifactId) {
        TailoredArtifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Tailored artifact not found"));
        return atsScoreService.score(artifact);
    }

    /** A single artifact + its match scorecard - backs the Browser Assist flow. */
    @GetMapping("/{artifactId}")
    public ArtifactReviewView getOne(@PathVariable UUID artifactId) {
        try {
            return reviewService.getById(artifactId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), e.getMessage());
        }
    }

    /** Candidate's own confirmation that they submitted in their own browser session - see TailoringReviewService. */
    @PostMapping("/{artifactId}/mark-submitted")
    public void markSubmitted(@PathVariable UUID artifactId) {
        try {
            reviewService.recordCandidateSubmitted(artifactId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), e.getMessage());
        }
    }

    @GetMapping("/pending-review")
    public List<ArtifactReviewView> pendingReview() {
        return reviewService.pendingReview();
    }

    /** Already-decided artifacts (most recently reviewed first) - review history for the console. */
    @GetMapping("/history")
    public List<ArtifactReviewView> history() {
        return reviewService.recentlyDecided();
    }

    @PostMapping("/{artifactId}/approve")
    public TailoredArtifact approve(@PathVariable UUID artifactId, @RequestBody(required = false) Map<String, String> body) {
        try {
            return reviewService.approve(artifactId, body == null ? null : body.get("note"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), e.getMessage());
        }
    }

    @PostMapping("/{artifactId}/reject")
    public TailoredArtifact reject(@PathVariable UUID artifactId, @RequestBody(required = false) Map<String, String> body) {
        try {
            return reviewService.reject(artifactId, body == null ? null : body.get("note"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), e.getMessage());
        }
    }
}
