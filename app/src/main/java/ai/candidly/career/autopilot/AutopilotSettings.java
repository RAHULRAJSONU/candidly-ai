package ai.candidly.career.autopilot;

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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * One candidate's "AI Job Application Agent" configuration + live run state (the mock's
 * Autopilot settings screen). This is a deliberate, explicit deviation from docs/00/
 * docs/02's HITL-by-default guarantee - see the class-level note on {@link
 * AutopilotService} for the scoping decision (no real outbound submission to a live
 * third-party ATS; "apply" here writes through the same {@code TailoredArtifact}
 * approval path the HITL console already uses, just without a human clicking it when
 * {@link #autoApply} is on and the match isn't flagged high-priority).
 */
@Entity
@Table(name = "autopilot_settings")
public class AutopilotSettings {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutopilotStatus status = AutopilotStatus.STOPPED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutopilotStage currentStage = AutopilotStage.IDLE;

    @Column(nullable = false)
    private String currentStageDetail = "Not started";

    private Instant lastRunAt;
    private Instant nextRunAt;

    // --- Job preferences ---

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_preferred_role", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "role")
    private Set<String> preferredRoles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_preferred_location", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "location")
    private Set<String> preferredLocations = new HashSet<>();

    private String experienceLevel = "MID";
    private String jobType = "FULL_TIME";
    private Long salaryMinMinorUnits;
    private Long salaryMaxMinorUnits;
    private boolean remoteOnly;
    private boolean openToRelocation;
    private boolean includeGlobalOpportunities;

    // --- Skills & keywords ---

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_key_skill", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "skill")
    private Set<String> keySkills = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_include_keyword", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "keyword")
    private Set<String> includeKeywords = new HashSet<>();

    /** The mock's closest analog to a company blacklist - free-text terms to exclude, e.g. "internship, fresher". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_exclude_keyword", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "keyword")
    private Set<String> excludeKeywords = new HashSet<>();

    // --- Job sources ---

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "autopilot_enabled_source", joinColumns = @JoinColumn(name = "autopilot_settings_id"))
    @Column(name = "source")
    private Set<String> enabledJobSources = new HashSet<>(Set.of("GREENHOUSE", "LEVER"));

    // --- Application settings ---

    private boolean autoApply = true;
    private boolean aiTailorResume = true;
    private boolean generateCoverLetter = true;
    private boolean autoFillForms = true;
    private boolean skipSponsorshipRequired = true;
    private boolean notifyBeforeApplying = false;
    private boolean autoFollowUp = true;
    private int dailyApplicationLimit = 20;

    /** Composite score at/above which a match is "high-priority" and still routes to human review even with autoApply on. */
    private double highPriorityReviewThreshold = 0.85;

    // --- Notifications ---

    private boolean notifyNewMatches = true;
    private boolean notifyApplicationSubmitted = true;
    private boolean notifyStatusChanges = true;
    private boolean notifyInterviewInvitations = true;
    private boolean notifyWeeklySummary = true;

    protected AutopilotSettings() {
        // JPA
    }

    public AutopilotSettings(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public void applyUpdate(AutopilotSettingsRequest r) {
        if (r.preferredRoles() != null) this.preferredRoles = new HashSet<>(r.preferredRoles());
        if (r.preferredLocations() != null) this.preferredLocations = new HashSet<>(r.preferredLocations());
        if (r.experienceLevel() != null) this.experienceLevel = r.experienceLevel();
        if (r.jobType() != null) this.jobType = r.jobType();
        this.salaryMinMinorUnits = r.salaryMinMinorUnits();
        this.salaryMaxMinorUnits = r.salaryMaxMinorUnits();
        if (r.remoteOnly() != null) this.remoteOnly = r.remoteOnly();
        if (r.openToRelocation() != null) this.openToRelocation = r.openToRelocation();
        if (r.includeGlobalOpportunities() != null) this.includeGlobalOpportunities = r.includeGlobalOpportunities();
        if (r.keySkills() != null) this.keySkills = new HashSet<>(r.keySkills());
        if (r.includeKeywords() != null) this.includeKeywords = new HashSet<>(r.includeKeywords());
        if (r.excludeKeywords() != null) this.excludeKeywords = new HashSet<>(r.excludeKeywords());
        if (r.enabledJobSources() != null) this.enabledJobSources = new HashSet<>(r.enabledJobSources());
        if (r.autoApply() != null) this.autoApply = r.autoApply();
        if (r.aiTailorResume() != null) this.aiTailorResume = r.aiTailorResume();
        if (r.generateCoverLetter() != null) this.generateCoverLetter = r.generateCoverLetter();
        if (r.autoFillForms() != null) this.autoFillForms = r.autoFillForms();
        if (r.skipSponsorshipRequired() != null) this.skipSponsorshipRequired = r.skipSponsorshipRequired();
        if (r.notifyBeforeApplying() != null) this.notifyBeforeApplying = r.notifyBeforeApplying();
        if (r.autoFollowUp() != null) this.autoFollowUp = r.autoFollowUp();
        if (r.dailyApplicationLimit() != null) this.dailyApplicationLimit = r.dailyApplicationLimit();
        if (r.notifyNewMatches() != null) this.notifyNewMatches = r.notifyNewMatches();
        if (r.notifyApplicationSubmitted() != null) this.notifyApplicationSubmitted = r.notifyApplicationSubmitted();
        if (r.notifyStatusChanges() != null) this.notifyStatusChanges = r.notifyStatusChanges();
        if (r.notifyInterviewInvitations() != null) this.notifyInterviewInvitations = r.notifyInterviewInvitations();
        if (r.notifyWeeklySummary() != null) this.notifyWeeklySummary = r.notifyWeeklySummary();
    }

    public void markStage(AutopilotStage stage, String detail) {
        this.currentStage = stage;
        this.currentStageDetail = detail;
    }

    public void start(Instant now) {
        this.status = AutopilotStatus.RUNNING;
        this.nextRunAt = now;
    }

    public void pause() {
        this.status = AutopilotStatus.PAUSED;
    }

    public void stop() {
        this.status = AutopilotStatus.STOPPED;
        this.currentStage = AutopilotStage.IDLE;
        this.currentStageDetail = "Stopped";
        this.nextRunAt = null;
    }

    public void scheduleNextRun(Instant next) {
        this.nextRunAt = next;
    }

    public void recordRun(Instant now) {
        this.lastRunAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public AutopilotStatus getStatus() {
        return status;
    }

    public AutopilotStage getCurrentStage() {
        return currentStage;
    }

    public String getCurrentStageDetail() {
        return currentStageDetail;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Set<String> getPreferredRoles() {
        return preferredRoles;
    }

    public Set<String> getPreferredLocations() {
        return preferredLocations;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public String getJobType() {
        return jobType;
    }

    public Long getSalaryMinMinorUnits() {
        return salaryMinMinorUnits;
    }

    public Long getSalaryMaxMinorUnits() {
        return salaryMaxMinorUnits;
    }

    public boolean isRemoteOnly() {
        return remoteOnly;
    }

    public boolean isOpenToRelocation() {
        return openToRelocation;
    }

    public boolean isIncludeGlobalOpportunities() {
        return includeGlobalOpportunities;
    }

    public Set<String> getKeySkills() {
        return keySkills;
    }

    public Set<String> getIncludeKeywords() {
        return includeKeywords;
    }

    public Set<String> getExcludeKeywords() {
        return excludeKeywords;
    }

    public Set<String> getEnabledJobSources() {
        return enabledJobSources;
    }

    public boolean isAutoApply() {
        return autoApply;
    }

    public boolean isAiTailorResume() {
        return aiTailorResume;
    }

    public boolean isGenerateCoverLetter() {
        return generateCoverLetter;
    }

    public boolean isAutoFillForms() {
        return autoFillForms;
    }

    public boolean isSkipSponsorshipRequired() {
        return skipSponsorshipRequired;
    }

    public boolean isNotifyBeforeApplying() {
        return notifyBeforeApplying;
    }

    public boolean isAutoFollowUp() {
        return autoFollowUp;
    }

    public int getDailyApplicationLimit() {
        return dailyApplicationLimit;
    }

    public double getHighPriorityReviewThreshold() {
        return highPriorityReviewThreshold;
    }

    public boolean isNotifyNewMatches() {
        return notifyNewMatches;
    }

    public boolean isNotifyApplicationSubmitted() {
        return notifyApplicationSubmitted;
    }

    public boolean isNotifyStatusChanges() {
        return notifyStatusChanges;
    }

    public boolean isNotifyInterviewInvitations() {
        return notifyInterviewInvitations;
    }

    public boolean isNotifyWeeklySummary() {
        return notifyWeeklySummary;
    }
}
