package ai.candidly.career.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A discovered posting. rawDescription is attacker-controlled free text (docs/02 §1) -
 * it must pass JobPostingScreeningService before any of it reaches a model with tool
 * access, and screeningDecision/screeningReasons record that gate's outcome.
 */
@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue
    private UUID id;

    /** SHA-256 over canonical (company, title, location, source_job_id) - docs/01 §3.1. */
    @Column(nullable = false, unique = true)
    private String dedupeHash;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String location;

    private boolean remote;

    private Long compMinMinorUnits;
    private Long compMaxMinorUnits;

    /** ISO 4217 code the comp fields above are denominated in - the posting's own currency,
     * independent of any candidate's preference, and never converted (see {@link
     * CurrencyCodes}). Set via {@link #assignCurrency} rather than a constructor parameter
     * (same reason as {@link #discoveredAt}); nullable for ddl-auto=update on an existing
     * table, reading as {@link CurrencyCodes#DEFAULT} - every posting ingested before this
     * field existed was entered against USD-labelled UI. */
    private String currency;

    /** Work authorizations this role can legally accept; empty = no restriction stated. */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "job_posting_work_authorization", joinColumns = @jakarta.persistence.JoinColumn(name = "job_posting_id"))
    @Column(name = "work_authorization")
    private Set<String> acceptedWorkAuthorizations = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "job_posting_mandatory_skill", joinColumns = @jakarta.persistence.JoinColumn(name = "job_posting_id"))
    @Column(name = "skill_id")
    private Set<String> mandatorySkillIds = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "job_posting_preferred_skill", joinColumns = @jakarta.persistence.JoinColumn(name = "job_posting_id"))
    @Column(name = "skill_id")
    private Set<String> preferredSkillIds = new HashSet<>();

    private String domain;

    private int minYearsExperience;

    @Column(nullable = false, columnDefinition = "text")
    private String rawDescription;

    @Enumerated(EnumType.STRING)
    private ScreeningDecision screeningDecision;

    @Column(columnDefinition = "text")
    private String screeningReasons;

    @org.hibernate.annotations.Type(PgVectorType.class)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    private String embeddingModel;
    private int embeddingVersion;

    @Enumerated(EnumType.STRING)
    private JobPostingSource source = JobPostingSource.MANUAL;

    /** When this row was first persisted - set once via {@link #onPrePersist()} rather than a
     * constructor parameter, so the many existing call sites (adapters, tests, direct API
     * submission) don't all need to start passing a timestamp. */
    private Instant discoveredAt;

    protected JobPosting() {
        // JPA
    }

    @PrePersist
    private void onPrePersist() {
        if (discoveredAt == null) {
            discoveredAt = Instant.now();
        }
    }

    public JobPosting(String dedupeHash, String company, String title, String location, boolean remote,
            Long compMinMinorUnits, Long compMaxMinorUnits, Set<String> acceptedWorkAuthorizations,
            Set<String> mandatorySkillIds, Set<String> preferredSkillIds, String domain, int minYearsExperience,
            String rawDescription) {
        this.dedupeHash = dedupeHash;
        this.company = company;
        this.title = title;
        this.location = location;
        this.remote = remote;
        this.compMinMinorUnits = compMinMinorUnits;
        this.compMaxMinorUnits = compMaxMinorUnits;
        this.acceptedWorkAuthorizations = new HashSet<>(acceptedWorkAuthorizations);
        this.mandatorySkillIds = new HashSet<>(mandatorySkillIds);
        this.preferredSkillIds = new HashSet<>(preferredSkillIds);
        this.domain = domain;
        this.minYearsExperience = minYearsExperience;
        this.rawDescription = rawDescription;
    }

    public void applyScreening(ScreeningDecision decision, String reasons) {
        this.screeningDecision = decision;
        this.screeningReasons = reasons;
    }

    /** Backfills skill/domain/experience fields a discovery adapter couldn't supply - see
     * {@code JobPostingExtractionService}. Callers are responsible for only invoking this
     * when the fields were actually empty, so an explicitly-submitted (e.g. MANUAL) value
     * is never overwritten. */
    public void applyExtraction(Set<String> mandatorySkillIds, Set<String> preferredSkillIds, String domain,
            int minYearsExperience) {
        this.mandatorySkillIds = new HashSet<>(mandatorySkillIds);
        this.preferredSkillIds = new HashSet<>(preferredSkillIds);
        this.domain = domain;
        this.minYearsExperience = minYearsExperience;
    }

    public void assignEmbedding(float[] embedding, String embeddingModel, int embeddingVersion) {
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.embeddingVersion = embeddingVersion;
    }

    public void assignSource(JobPostingSource source) {
        this.source = source;
    }

    public JobPostingSource getSource() {
        return source;
    }

    public void assignCurrency(String currency) {
        this.currency = currency;
    }

    public String getCurrency() {
        return currency == null ? CurrencyCodes.DEFAULT : currency;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDedupeHash() {
        return dedupeHash;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public boolean isRemote() {
        return remote;
    }

    public Long getCompMinMinorUnits() {
        return compMinMinorUnits;
    }

    public Long getCompMaxMinorUnits() {
        return compMaxMinorUnits;
    }

    public Set<String> getAcceptedWorkAuthorizations() {
        return acceptedWorkAuthorizations;
    }

    public Set<String> getMandatorySkillIds() {
        return mandatorySkillIds;
    }

    public Set<String> getPreferredSkillIds() {
        return preferredSkillIds;
    }

    public String getDomain() {
        return domain;
    }

    public int getMinYearsExperience() {
        return minYearsExperience;
    }

    public String getRawDescription() {
        return rawDescription;
    }

    public ScreeningDecision getScreeningDecision() {
        return screeningDecision;
    }

    public String getScreeningReasons() {
        return screeningReasons;
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
