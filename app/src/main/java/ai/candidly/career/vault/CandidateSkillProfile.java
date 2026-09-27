package ai.candidly.career.vault;

import java.time.LocalDate;
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

/**
 * A candidate's self-reported detail for one skill on their {@code rawSkillMentions}
 * list: years of experience, a Beginner/Intermediate/Expert level, a 1-5 confidence
 * rating, and when/what version they last used it (e.g. "React 19", "Python 3.12").
 * Keyed by {@code (candidate_id, skillName)} where {@code skillName} is the exact raw
 * mention string (same free-text identity {@code rawSkillMentions}/{@code TopSkillsCard}
 * already use) rather than the normalized taxonomy {@code skillId} - a candidate edits
 * this per skill the way they typed it, and normalization can map several raw mentions
 * to the same taxonomy id, which would collide as one profile.
 *
 * <p>Purely a richer version of the same "honest, derived-or-self-reported, never
 * fabricated" signal the Skills tab's mention-count bars already followed - see the
 * javadoc on {@code CareerVault.tsx}'s {@code computeSkillMentionCounts}, which this
 * entity supersedes the "there's no such field" half of.
 */
@Entity
@Table(name = "candidate_skill_profile", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = { "candidate_id", "skill_name" }))
public class CandidateSkillProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Enumerated(EnumType.STRING)
    private SkillProficiencyLevel proficiencyLevel;

    private Double yearsOfExperience;

    /** 1-5 self-rated confidence. */
    private Integer confidenceScore;

    private LocalDate lastUsedOn;

    private String lastUsedVersion;

    protected CandidateSkillProfile() {
        // JPA
    }

    public CandidateSkillProfile(Candidate candidate, String skillName, SkillProficiencyLevel proficiencyLevel,
            Double yearsOfExperience, Integer confidenceScore, LocalDate lastUsedOn, String lastUsedVersion) {
        this.candidate = candidate;
        this.skillName = skillName;
        this.proficiencyLevel = proficiencyLevel;
        this.yearsOfExperience = yearsOfExperience;
        this.confidenceScore = confidenceScore;
        this.lastUsedOn = lastUsedOn;
        this.lastUsedVersion = lastUsedVersion;
    }

    public void update(SkillProficiencyLevel proficiencyLevel, Double yearsOfExperience, Integer confidenceScore,
            LocalDate lastUsedOn, String lastUsedVersion) {
        this.proficiencyLevel = proficiencyLevel;
        this.yearsOfExperience = yearsOfExperience;
        this.confidenceScore = confidenceScore;
        this.lastUsedOn = lastUsedOn;
        this.lastUsedVersion = lastUsedVersion;
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public String getSkillName() {
        return skillName;
    }

    public SkillProficiencyLevel getProficiencyLevel() {
        return proficiencyLevel;
    }

    public Double getYearsOfExperience() {
        return yearsOfExperience;
    }

    public Integer getConfidenceScore() {
        return confidenceScore;
    }

    public LocalDate getLastUsedOn() {
        return lastUsedOn;
    }

    public String getLastUsedVersion() {
        return lastUsedVersion;
    }
}
