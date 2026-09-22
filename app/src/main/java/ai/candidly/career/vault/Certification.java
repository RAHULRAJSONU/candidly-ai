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
@Table(name = "certification")
public class Certification {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String issuer;

    private LocalDate issuedOn;

    private String credentialId;

    protected Certification() {
        // JPA
    }

    public Certification(Candidate candidate, String name, String issuer, LocalDate issuedOn, String credentialId) {
        this.candidate = candidate;
        this.name = name;
        this.issuer = issuer;
        this.issuedOn = issuedOn;
        this.credentialId = credentialId;
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public String getName() {
        return name;
    }

    public String getIssuer() {
        return issuer;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public String getCredentialId() {
        return credentialId;
    }
}
