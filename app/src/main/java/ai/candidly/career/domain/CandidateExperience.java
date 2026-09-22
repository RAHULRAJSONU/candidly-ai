package ai.candidly.career.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single verified achievement/role node in the candidate's Career Story Ledger
 * (docs/01 §2). Every claim in a tailored artifact must trace back to one of these by
 * set membership / exact match (docs/02 §1.4, C-8) - this is the row that grounds it.
 */
@Entity
@Table(name = "candidate_experience")
public class CandidateExperience {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    private Candidate candidate;

    @Column(nullable = false)
    private String employer;

    @Column(nullable = false)
    private String title;

    private LocalDate startDate;
    private LocalDate endDate;

    /** Free-text narrative used to build the embedding and shown to the tailoring model as context. */
    @Column(columnDefinition = "text")
    private String narrative;

    /** Verified, exact-match-checkable claims, e.g. "Reduced p95 latency by 40%". */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_experience_metric", joinColumns = @jakarta.persistence.JoinColumn(name = "experience_id"))
    @Column(name = "metric", length = 1000)
    private Set<String> verifiedMetrics = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_experience_skill", joinColumns = @jakarta.persistence.JoinColumn(name = "experience_id"))
    @Column(name = "skill_id")
    private Set<String> skillIds = new HashSet<>();

    /** Verbatim skill mentions for this role, before taxonomy normalization - see
     * {@link Candidate#getRawSkillMentions()} for why this is kept alongside skillIds. */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_experience_raw_skill_mention", joinColumns = @jakarta.persistence.JoinColumn(name = "experience_id"))
    @Column(name = "raw_skill_mention", length = 255)
    private Set<String> rawSkillMentions = new HashSet<>();

    @org.hibernate.annotations.Type(PgVectorType.class)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(nullable = false)
    private String embeddingModel;

    @Column(nullable = false)
    private int embeddingVersion;

    protected CandidateExperience() {
        // JPA
    }

    public CandidateExperience(Candidate candidate, String employer, String title, LocalDate startDate,
            LocalDate endDate, String narrative, Set<String> verifiedMetrics, Set<String> skillIds,
            Set<String> rawSkillMentions) {
        this.candidate = candidate;
        this.employer = employer;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.narrative = narrative;
        this.verifiedMetrics = new HashSet<>(verifiedMetrics);
        this.skillIds = new HashSet<>(skillIds);
        this.rawSkillMentions = new HashSet<>(rawSkillMentions);
    }

    public void assignEmbedding(float[] embedding, String embeddingModel, int embeddingVersion) {
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.embeddingVersion = embeddingVersion;
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public String getEmployer() {
        return employer;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNarrative() {
        return narrative;
    }

    public Set<String> getVerifiedMetrics() {
        return verifiedMetrics;
    }

    public Set<String> getSkillIds() {
        return skillIds;
    }

    public Set<String> getRawSkillMentions() {
        return rawSkillMentions;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public int getEmbeddingVersion() {
        return embeddingVersion;
    }
}
