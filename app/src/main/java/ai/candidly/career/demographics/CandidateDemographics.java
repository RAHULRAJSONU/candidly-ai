package ai.candidly.career.demographics;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Voluntary EEOC/OFCCP self-identification (OMB CC-305), stored separately from every
 * table the scoring/matching path reads (docs/02 §4, docs/04: "never generate, predict
 * or alter" this data, defaults to decline). {@code candidateId} is deliberately a bare
 * UUID with no {@code @ManyToOne}/FK to {@code Candidate} - the same isolation pattern
 * {@code AuditEvent.subjectId} already uses in this repo - so nothing in {@code
 * matching}/{@code tailoring} can join into this table even by accident; only {@link
 * ai.candidly.career.demographics.BiasAuditReportService} and the candidate's own export
 * (docs/02 §4.1) read it.
 *
 * <p>Categories are free-text strings, not an enum, because docs/03's fairness-metrics
 * requirement ("score and advance-rate parity across protected groups... for the LL144
 * bias audit") doesn't specify a fixed category set and NYC/state requirements vary; a
 * candidate who doesn't answer gets {@link #DECLINED} rather than a null, so a bias
 * report can report the decline rate itself (LL144 auditors care about non-response
 * bias too).
 */
@Entity
@Table(name = "candidate_demographics")
public class CandidateDemographics {

    public static final String DECLINED = "DECLINE_TO_SELF_IDENTIFY";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID candidateId;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private String raceEthnicity;

    @Column(nullable = false)
    private String veteranStatus;

    @Column(nullable = false)
    private String disabilityStatus;

    @Column(nullable = false)
    private Instant submittedAt;

    protected CandidateDemographics() {
        // JPA
    }

    public CandidateDemographics(UUID candidateId, String gender, String raceEthnicity, String veteranStatus,
            String disabilityStatus) {
        this.candidateId = candidateId;
        this.gender = orDeclined(gender);
        this.raceEthnicity = orDeclined(raceEthnicity);
        this.veteranStatus = orDeclined(veteranStatus);
        this.disabilityStatus = orDeclined(disabilityStatus);
        this.submittedAt = Instant.now();
    }

    private static String orDeclined(String value) {
        return value == null || value.isBlank() ? DECLINED : value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getGender() {
        return gender;
    }

    public String getRaceEthnicity() {
        return raceEthnicity;
    }

    public String getVeteranStatus() {
        return veteranStatus;
    }

    public String getDisabilityStatus() {
        return disabilityStatus;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
