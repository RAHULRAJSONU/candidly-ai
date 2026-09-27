package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.LinkedHashSet;
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
 * A self-reported project in the Career Vault (docs/01 §2), distinct from a
 * {@code CandidateExperience} - a side project, open-source contribution, or portfolio
 * piece not tied to one employer. Same self-reported-not-verified status as
 * Achievement/Education/Certification; nothing here is fed into resume-tailoring
 * grounding (see DeterministicGroundingVerifier, which only trusts CandidateExperience).
 */
@Entity
@Table(name = "project")
public class Project {

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

    private String url;

    private LocalDate startDate;

    private LocalDate endDate;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "project_tech", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "technology")
    private Set<String> technologies = new LinkedHashSet<>();

    protected Project() {
        // JPA
    }

    public Project(Candidate candidate, String title, String description, String url, LocalDate startDate,
            LocalDate endDate, Set<String> technologies) {
        this.candidate = candidate;
        this.title = title;
        this.description = description;
        this.url = url;
        this.startDate = startDate;
        this.endDate = endDate;
        this.technologies = technologies == null ? new LinkedHashSet<>() : new LinkedHashSet<>(technologies);
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

    public String getUrl() {
        return url;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Set<String> getTechnologies() {
        return technologies;
    }
}
