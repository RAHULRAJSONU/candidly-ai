package ai.candidly.career.pipeline;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;

/**
 * One interview in the post-tailoring pipeline (docs/04's application lifecycle,
 * beyond what this repo has built so far - discovery/match/tailor/HITL-review). Created
 * either manually or by {@code EmailIntakeService} classifying a pasted recruiter email
 * (source EMAIL_EXTRACTED) - either way it's just a tracking record, never something a
 * model or this app acts on: nothing here schedules a calendar invite or replies to
 * anyone.
 */
@Entity
@Table(name = "interview")
public class Interview {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewSource source;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    protected Interview() {
        // JPA
    }

    public Interview(Candidate candidate, JobPosting jobPosting, Instant scheduledAt, InterviewMode mode,
            InterviewSource source, String notes) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.scheduledAt = scheduledAt;
        this.mode = mode;
        this.status = InterviewStatus.SCHEDULED;
        this.source = source;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public void updateStatus(InterviewStatus status) {
        this.status = status;
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

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public InterviewSource getSource() {
        return source;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
