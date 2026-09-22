package ai.candidly.career.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The async handle for a tailoring run (docs/03 §4: "retrieve -> generate -> up-to-3
 * critic loops -> deterministic verify" is inherently multi-call and slow - 15-40s, not
 * an 8s synchronous call). {@code POST /api/tailoring} returns one of these immediately;
 * the actual work runs on {@code TailoringAsyncExecutor} off the request thread, and the
 * client polls {@code GET /api/tailoring/jobs/{id}} (no WebSocket/STOMP push yet - see
 * CLAUDE.md simplifications).
 */
@Entity
@Table(name = "tailoring_job")
public class TailoringJob {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TailoringJobStatus status;

    @ManyToOne
    @jakarta.persistence.JoinColumn(name = "result_artifact_id")
    private TailoredArtifact resultArtifact;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant completedAt;

    protected TailoringJob() {
        // JPA
    }

    public TailoringJob(Candidate candidate, JobPosting jobPosting) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.status = TailoringJobStatus.QUEUED;
        this.submittedAt = Instant.now();
    }

    public void markRunning() {
        this.status = TailoringJobStatus.RUNNING;
    }

    public void markCompleted(TailoredArtifact artifact) {
        this.status = TailoringJobStatus.COMPLETED;
        this.resultArtifact = artifact;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = TailoringJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
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

    public TailoringJobStatus getStatus() {
        return status;
    }

    public TailoredArtifact getResultArtifact() {
        return resultArtifact;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
