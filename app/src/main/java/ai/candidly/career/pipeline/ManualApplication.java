package ai.candidly.career.pipeline;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * An application the candidate made outside this platform (a job board, a company site,
 * a referral) and is logging manually - the mock's "+ Add Application" button. Distinct
 * from {@code MatchScorecard}/{@code TailoringJob} on purpose: those describe postings
 * this app discovered and scored; a manual entry has no {@code JobPosting} row at all
 * (free-text company/role), so it's new state to track, not a duplicate of what {@code
 * PipelineSummaryService} already derives.
 */
@Entity
@Table(name = "manual_application")
public class ManualApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    private LocalDate appliedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManualApplicationStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id")
    @OrderColumn(name = "entry_order")
    private List<ManualApplicationTimelineEntry> timeline = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    protected ManualApplication() {
        // JPA
    }

    public ManualApplication(UUID candidateId, String company, String role, LocalDate appliedDate, String notes) {
        this.candidateId = candidateId;
        this.company = company;
        this.role = role;
        this.appliedDate = appliedDate;
        this.status = ManualApplicationStatus.APPLIED;
        this.notes = notes;
        this.createdAt = Instant.now();
        this.timeline.add(new ManualApplicationTimelineEntry("Logged as applied", Instant.now()));
    }

    public void updateStatus(ManualApplicationStatus status, String note) {
        this.status = status;
        this.timeline.add(new ManualApplicationTimelineEntry(
                note != null && !note.isBlank() ? note : "Status changed to " + status, Instant.now()));
    }

    public void addTimelineNote(String note) {
        this.timeline.add(new ManualApplicationTimelineEntry(note, Instant.now()));
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public ManualApplicationStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<ManualApplicationTimelineEntry> getTimeline() {
        return timeline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
