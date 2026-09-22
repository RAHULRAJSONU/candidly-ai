package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ai.candidly.career.domain.Candidate;

/**
 * A standalone, quantified accomplishment in the Career Vault (docs/01 §2's
 * "verified professional record"), distinct from a {@code CandidateExperience}'s own
 * {@code verifiedMetrics} - this is for achievements a candidate wants to surface on
 * their own (e.g. an award, a cross-team initiative) rather than tied to one role.
 */
@Entity
@Table(name = "achievement")
public class Achievement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDate occurredOn;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "achievement_tag", joinColumns = @JoinColumn(name = "achievement_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    protected Achievement() {
        // JPA
    }

    public Achievement(Candidate candidate, String title, String description, LocalDate occurredOn, Set<String> tags) {
        this.candidate = candidate;
        this.title = title;
        this.description = description;
        this.occurredOn = occurredOn;
        this.tags = new HashSet<>(tags);
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Set<String> getTags() {
        return tags;
    }
}
