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

@Entity
@Table(name = "job_offer")
public class Offer {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    private Long compensationMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private Instant receivedAt;

    protected Offer() {
        // JPA
    }

    public Offer(Candidate candidate, JobPosting jobPosting, Long compensationMinorUnits, String notes) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.compensationMinorUnits = compensationMinorUnits;
        this.status = OfferStatus.EXTENDED;
        this.notes = notes;
        this.receivedAt = Instant.now();
    }

    public void updateStatus(OfferStatus status) {
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

    public Long getCompensationMinorUnits() {
        return compensationMinorUnits;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
