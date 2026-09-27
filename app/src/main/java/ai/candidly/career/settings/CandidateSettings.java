package ai.candidly.career.settings;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One candidate's Settings-page preferences (the mock's "Account" tab: AI Preferences,
 * Notifications, Appearance, Billing cards) - a different entity from {@code
 * AutopilotSettings}, which configures the separate autonomous-agent feature. Every enum-ish
 * field here is a plain String, not {@code @Enumerated}, per CLAUDE.md's documented
 * stale-CHECK-constraint sharp edge; {@link CandidateSettingsService} validates against the
 * allowed value sets instead.
 *
 * <p>Fields are split into two honesty tiers:
 * <ul>
 * <li>Genuinely wired: {@code aiResumeTailoring}/{@code aiCoverLetterGeneration} gate
 * {@code TailoringJobService}/{@code ResumeTailoringService}; {@code aiInterviewPrep} gates
 * {@code InterviewPrepController}; {@code responseStyle}/{@code levelOfDetail} are threaded
 * into {@code GroundedResumeGenerator}'s prompts as real tone/length instructions.
 * <li>Stored-but-inert, honestly disclosed as such in the frontend copy: {@code
 * preferredAiModel} (this deployment has exactly one configured chat model - see {@code
 * AiClientsConfig} - so this can't actually route between models), {@code
 * personalizedRecommendations} (matching already always runs; there's no per-candidate
 * opt-out path in {@code MatchOrchestratorService}), {@code useDataForModelImprovement}
 * (no training pipeline exists), and every {@code notify*} flag (no email/push delivery
 * channel exists in this codebase - these are preferences with nothing to act on them yet).
 * </ul>
 */
@Entity
@Table(name = "candidate_settings")
public class CandidateSettings {

    public static final String DEFAULT_AI_MODEL = "openai/gpt-oss-120b";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID candidateId;

    // --- AI Preferences ---
    private String preferredAiModel = DEFAULT_AI_MODEL;
    private String responseStyle = "BALANCED";
    private String levelOfDetail = "STANDARD";
    private boolean personalizedRecommendations = true;
    private boolean aiResumeTailoring = true;
    private boolean aiCoverLetterGeneration = true;
    private boolean aiInterviewPrep = true;
    private boolean useDataForModelImprovement = false;

    // --- Notifications ---
    private boolean notifyJobMatches = true;
    private boolean notifyApplicationUpdates = true;
    private boolean notifyInterviewReminders = true;
    private boolean notifyWeeklyDigest = true;
    private boolean notifyProductUpdates = false;

    // --- Appearance / Billing ---
    private String theme = "LIGHT";

    /** Display-only - no payment processor is wired into this codebase. */
    private String planTier = "PRO";

    protected CandidateSettings() {
        // JPA
    }

    public CandidateSettings(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getPreferredAiModel() {
        return preferredAiModel;
    }

    public void setPreferredAiModel(String preferredAiModel) {
        this.preferredAiModel = preferredAiModel;
    }

    public String getResponseStyle() {
        return responseStyle;
    }

    public void setResponseStyle(String responseStyle) {
        this.responseStyle = responseStyle;
    }

    public String getLevelOfDetail() {
        return levelOfDetail;
    }

    public void setLevelOfDetail(String levelOfDetail) {
        this.levelOfDetail = levelOfDetail;
    }

    public boolean isPersonalizedRecommendations() {
        return personalizedRecommendations;
    }

    public void setPersonalizedRecommendations(boolean personalizedRecommendations) {
        this.personalizedRecommendations = personalizedRecommendations;
    }

    public boolean isAiResumeTailoring() {
        return aiResumeTailoring;
    }

    public void setAiResumeTailoring(boolean aiResumeTailoring) {
        this.aiResumeTailoring = aiResumeTailoring;
    }

    public boolean isAiCoverLetterGeneration() {
        return aiCoverLetterGeneration;
    }

    public void setAiCoverLetterGeneration(boolean aiCoverLetterGeneration) {
        this.aiCoverLetterGeneration = aiCoverLetterGeneration;
    }

    public boolean isAiInterviewPrep() {
        return aiInterviewPrep;
    }

    public void setAiInterviewPrep(boolean aiInterviewPrep) {
        this.aiInterviewPrep = aiInterviewPrep;
    }

    public boolean isUseDataForModelImprovement() {
        return useDataForModelImprovement;
    }

    public void setUseDataForModelImprovement(boolean useDataForModelImprovement) {
        this.useDataForModelImprovement = useDataForModelImprovement;
    }

    public boolean isNotifyJobMatches() {
        return notifyJobMatches;
    }

    public void setNotifyJobMatches(boolean notifyJobMatches) {
        this.notifyJobMatches = notifyJobMatches;
    }

    public boolean isNotifyApplicationUpdates() {
        return notifyApplicationUpdates;
    }

    public void setNotifyApplicationUpdates(boolean notifyApplicationUpdates) {
        this.notifyApplicationUpdates = notifyApplicationUpdates;
    }

    public boolean isNotifyInterviewReminders() {
        return notifyInterviewReminders;
    }

    public void setNotifyInterviewReminders(boolean notifyInterviewReminders) {
        this.notifyInterviewReminders = notifyInterviewReminders;
    }

    public boolean isNotifyWeeklyDigest() {
        return notifyWeeklyDigest;
    }

    public void setNotifyWeeklyDigest(boolean notifyWeeklyDigest) {
        this.notifyWeeklyDigest = notifyWeeklyDigest;
    }

    public boolean isNotifyProductUpdates() {
        return notifyProductUpdates;
    }

    public void setNotifyProductUpdates(boolean notifyProductUpdates) {
        this.notifyProductUpdates = notifyProductUpdates;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getPlanTier() {
        return planTier;
    }

    public void setPlanTier(String planTier) {
        this.planTier = planTier;
    }
}
