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

    /** Minimum acceptable annual compensation, in minor units of {@link #getPreferredCurrency()}
     * (e.g. cents, paise). */
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

    // --- Profile & preferences fields below (mockup-driven addition, no direct docs/00-04
    // requirement - see CandidateProfileService). All nullable/optional so existing rows
    // created before this addition stay valid under ddl-auto=update. None of these are
    // read by EligibilityGateService/CompositeScoringService - they're purely for the
    // candidate's own "Resume & Profile" page, distinct from the hard-eligibility fields
    // above (location/workAuthorizations/compFloorMinorUnits).

    private String phone;
    private String linkedinUrl;
    private String portfolioUrl;
    private String headline;

    @Column(columnDefinition = "text")
    private String professionalSummary;

    /** Free-text, not @Enumerated - avoids the stale-CHECK-constraint sharp edge (CLAUDE.md). */
    private String workMode;
    private String noticePeriod;

    /** Boxed, not primitive - a primitive boolean maps to a NOT NULL column, which breaks
     * ddl-auto=update against a table that already has rows (existing rows can't backfill
     * a NOT NULL column added later). Null reads as "not open to relocation" everywhere. */
    private Boolean openToRelocation;
    private Long expectedCompMinMinorUnits;
    private Long expectedCompMaxMinorUnits;

    /** The candidate's currency preference (Settings > Job Preferences): the ISO 4217 code
     * every candidate-owned amount is denominated in - {@link #compFloorMinorUnits}, the
     * expected-comp fields above, and Autopilot's salary range - and the currency the
     * frontend renders them in. The one field in this block EligibilityGateService does
     * read, since it decides whether a posting's comp is comparable to the floor at all
     * (see {@link CurrencyCodes}). Nullable (not NOT NULL + default) for the same reason as
     * {@link #openToRelocation} - a column added after the table already has rows can't
     * backfill a NOT NULL constraint under ddl-auto=update. Null reads as {@link
     * CurrencyCodes#DEFAULT} via the getter below. Changing it relabels the stored amounts
     * rather than converting them (no FX source exists). */
    private String preferredCurrency;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_preferred_location", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "location")
    private Set<String> preferredLocations = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_preferred_role", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "role")
    private Set<String> preferredRoles = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_preferred_industry", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "industry")
    private Set<String> preferredIndustries = new HashSet<>();

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "candidate_employment_type", joinColumns = @jakarta.persistence.JoinColumn(name = "candidate_id"))
    @Column(name = "employment_type")
    private Set<String> employmentTypes = new HashSet<>();

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

    public void setCompFloorMinorUnits(long compFloorMinorUnits) {
        this.compFloorMinorUnits = compFloorMinorUnits;
    }

    public Set<String> getSkillIds() {
        return skillIds;
    }

    public Set<String> getRawSkillMentions() {
        return rawSkillMentions;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getProfessionalSummary() {
        return professionalSummary;
    }

    public void setProfessionalSummary(String professionalSummary) {
        this.professionalSummary = professionalSummary;
    }

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    public String getNoticePeriod() {
        return noticePeriod;
    }

    public void setNoticePeriod(String noticePeriod) {
        this.noticePeriod = noticePeriod;
    }

    public boolean isOpenToRelocation() {
        return Boolean.TRUE.equals(openToRelocation);
    }

    public void setOpenToRelocation(Boolean openToRelocation) {
        this.openToRelocation = openToRelocation;
    }

    public Long getExpectedCompMinMinorUnits() {
        return expectedCompMinMinorUnits;
    }

    public void setExpectedCompMinMinorUnits(Long expectedCompMinMinorUnits) {
        this.expectedCompMinMinorUnits = expectedCompMinMinorUnits;
    }

    public Long getExpectedCompMaxMinorUnits() {
        return expectedCompMaxMinorUnits;
    }

    public void setExpectedCompMaxMinorUnits(Long expectedCompMaxMinorUnits) {
        this.expectedCompMaxMinorUnits = expectedCompMaxMinorUnits;
    }

    public String getPreferredCurrency() {
        return preferredCurrency == null ? CurrencyCodes.DEFAULT : preferredCurrency;
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = preferredCurrency;
    }

    public Set<String> getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(Set<String> preferredLocations) {
        this.preferredLocations = new HashSet<>(preferredLocations);
    }

    public Set<String> getPreferredRoles() {
        return preferredRoles;
    }

    public void setPreferredRoles(Set<String> preferredRoles) {
        this.preferredRoles = new HashSet<>(preferredRoles);
    }

    public Set<String> getPreferredIndustries() {
        return preferredIndustries;
    }

    public void setPreferredIndustries(Set<String> preferredIndustries) {
        this.preferredIndustries = new HashSet<>(preferredIndustries);
    }

    public Set<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(Set<String> employmentTypes) {
        this.employmentTypes = new HashSet<>(employmentTypes);
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setWorkAuthorizations(Set<String> workAuthorizations) {
        this.workAuthorizations = new HashSet<>(workAuthorizations);
    }

    public void setRawSkillMentions(Set<String> rawSkillMentions) {
        this.rawSkillMentions = new HashSet<>(rawSkillMentions);
    }
}
