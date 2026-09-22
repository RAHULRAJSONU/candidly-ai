package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ai.candidly.career.domain.Candidate;

@Entity
@Table(name = "education")
public class Education {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String institution;

    @Column(nullable = false)
    private String degree;

    private String fieldOfStudy;

    private LocalDate startDate;
    private LocalDate endDate;

    protected Education() {
        // JPA
    }

    public Education(Candidate candidate, String institution, String degree, String fieldOfStudy, LocalDate startDate,
            LocalDate endDate) {
        this.candidate = candidate;
        this.institution = institution;
        this.degree = degree;
        this.fieldOfStudy = fieldOfStudy;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public String getInstitution() {
        return institution;
    }

    public String getDegree() {
        return degree;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
