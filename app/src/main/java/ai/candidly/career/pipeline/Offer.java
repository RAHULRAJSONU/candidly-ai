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
import ai.candidly.career.domain.CurrencyCodes;
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

    /** ISO 4217 code for {@link #compensationMinorUnits}, captured when the offer is recorded
     * (defaulting to the candidate's preferred currency then) - an offer is a fact, so a later
     * change to the candidate's currency preference must not relabel it. Nullable for
     * ddl-auto=update on an existing table; null reads as {@link CurrencyCodes#DEFAULT}. */
    private String currency;

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

    public Offer(Candidate candidate, JobPosting jobPosting, Long compensationMinorUnits, String currency, String notes) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.compensationMinorUnits = compensationMinorUnits;
        this.currency = currency;
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

    public String getCurrency() {
        return currency == null ? CurrencyCodes.DEFAULT : currency;
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
