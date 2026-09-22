package ai.candidly.career.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The candidate-side controller (docs/04 §2). compFloorMinorUnits and location/work
 * authorization are the hard-eligibility inputs (docs/03 §2.1) - never weighted terms.
 */
@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String location;

    /** Work authorizations the candidate legally holds, e.g. "US", "EU", "UK". */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_work_authorization", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "work_authorization")
    private Set<String> workAuthorizations = new HashSet<>();

    /** Minimum acceptable annual compensation, in minor currency units (e.g. cents). */
    private long compFloorMinorUnits;

    /** Canonical taxonomy skill ids the candidate holds (see SkillTaxonomy). */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_skill", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "skill_id")
    private Set<String> skillIds = new HashSet<>();

    /**
     * The candidate's self-reported/extracted skill mentions verbatim, before taxonomy
     * normalization - kept alongside {@link #skillIds} (never instead of it) so Career
     * Vault can display everything the resume/profile actually said, even mentions the
     * small in-memory {@code SkillTaxonomy} has no entry for. Matching/eligibility
     * (EligibilityGateService, CompositeScoringService) only ever reads {@link #skillIds};
     * this field is display-only.
     */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_raw_skill_mention", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "raw_skill_mention", length = 255)
    private Set<String> rawSkillMentions = new HashSet<>();

    protected Candidate() {
        // JPA
    }

    public Candidate(String fullName, String email, String location, Set<String> workAuthorizations,
            long compFloorMinorUnits, Set<String> skillIds, Set<String> rawSkillMentions) {
        this.fullName = fullName;
        this.email = email;
        this.location = location;
        this.workAuthorizations = new HashSet<>(workAuthorizations);
        this.compFloorMinorUnits = compFloorMinorUnits;
        this.skillIds = new HashSet<>(skillIds);
        this.rawSkillMentions = new HashSet<>(rawSkillMentions);
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getLocation() {
        return location;
    }

    public Set<String> getWorkAuthorizations() {
        return workAuthorizations;
    }

    public long getCompFloorMinorUnits() {
        return compFloorMinorUnits;
    }

    public Set<String> getSkillIds() {
        return skillIds;
    }

    public Set<String> getRawSkillMentions() {
        return rawSkillMentions;
    }
}
