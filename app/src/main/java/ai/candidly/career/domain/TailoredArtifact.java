package ai.candidly.career.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A generated resume artifact for one (candidate, job) pair, plus the trail the
 * evaluator-optimizer loop left behind. groundingPassed is set by the deterministic
 * verifier, not the LLM critic (docs/02 §1.4, C-8) - that is the invariant, not a
 * best-effort check.
 */
@Entity
@Table(name = "tailored_artifact")
public class TailoredArtifact {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private int criticLoopsUsed;

    private boolean groundingPassed;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "tailored_artifact_rejected_claim", joinColumns = @jakarta.persistence.JoinColumn(name = "artifact_id"))
    @Column(name = "claim", length = 1000)
    private List<String> rejectedClaims;

    /** Same grounding discipline as {@code content}, generated in a single pass (no critic
     * loop) since a cover letter is a secondary artifact - see ResumeTailoringService. */
    @Column(columnDefinition = "text")
    private String coverLetterContent;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "tailored_artifact_screening_answer", joinColumns = @jakarta.persistence.JoinColumn(name = "artifact_id"))
    @jakarta.persistence.OrderColumn(name = "position")
    @Column(name = "answer", length = 2000)
    private List<String> screeningAnswers = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TailoredArtifactStatus status;

    @Column(nullable = false)
    private Instant generatedAt;

    private Instant reviewedAt;

    @Column(columnDefinition = "text")
    private String reviewNote;

    protected TailoredArtifact() {
        // JPA
    }

    public TailoredArtifact(Candidate candidate, JobPosting jobPosting, String content, int criticLoopsUsed,
            boolean groundingPassed, List<String> rejectedClaims, TailoredArtifactStatus status) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.content = content;
        this.criticLoopsUsed = criticLoopsUsed;
        this.groundingPassed = groundingPassed;
        this.rejectedClaims = rejectedClaims;
        this.status = status;
        this.generatedAt = Instant.now();
    }

    /** The human-in-the-loop console's decision (docs/01 §2). Only meaningful from PENDING_APPROVAL/NEEDS_HUMAN_REVIEW. */
    public void approve(String note) {
        this.status = TailoredArtifactStatus.APPROVED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    public void reject(String note) {
        this.status = TailoredArtifactStatus.REJECTED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public String getContent() {
        return content;
    }

    public int getCriticLoopsUsed() {
        return criticLoopsUsed;
    }

    public boolean isGroundingPassed() {
        return groundingPassed;
    }

    public List<String> getRejectedClaims() {
        return rejectedClaims;
    }

    public TailoredArtifactStatus getStatus() {
        return status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public String getCoverLetterContent() {
        return coverLetterContent;
    }

    public void setCoverLetterContent(String coverLetterContent) {
        this.coverLetterContent = coverLetterContent;
    }

    public List<String> getScreeningAnswers() {
        return screeningAnswers;
    }

    public void setScreeningAnswers(List<String> screeningAnswers) {
        this.screeningAnswers = screeningAnswers;
    }
}
