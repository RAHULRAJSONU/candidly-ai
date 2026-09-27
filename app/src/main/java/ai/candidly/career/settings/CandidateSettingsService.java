package ai.candidly.career.settings;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Get-or-create + partial-update for {@link CandidateSettings}, plus the feature-gate
 * helpers the real (non-decorative) toggles enforce - see the class javadoc on {@link
 * CandidateSettings} for which fields those are.
 */
@Service
public class CandidateSettingsService {

    private static final Set<String> RESPONSE_STYLES = Set.of("CONCISE", "BALANCED", "DETAILED");
    private static final Set<String> DETAIL_LEVELS = Set.of("BRIEF", "STANDARD", "COMPREHENSIVE");
    private static final Set<String> THEMES = Set.of("LIGHT", "DARK", "SYSTEM");
    private static final Set<String> AI_MODELS = Set.of(CandidateSettings.DEFAULT_AI_MODEL);

    private final CandidateSettingsRepository repository;

    public CandidateSettingsService(CandidateSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CandidateSettings get(UUID candidateId) {
        return repository.findByCandidateId(candidateId)
                .orElseGet(() -> repository.save(new CandidateSettings(candidateId)));
    }

    @Transactional
    public CandidateSettings update(UUID candidateId, CandidateSettingsRequest request) {
        CandidateSettings settings = get(candidateId);

        if (request.preferredAiModel() != null) {
            requireOneOf(AI_MODELS, request.preferredAiModel(), "preferredAiModel");
            settings.setPreferredAiModel(request.preferredAiModel());
        }
        if (request.responseStyle() != null) {
            requireOneOf(RESPONSE_STYLES, request.responseStyle(), "responseStyle");
            settings.setResponseStyle(request.responseStyle());
        }
        if (request.levelOfDetail() != null) {
            requireOneOf(DETAIL_LEVELS, request.levelOfDetail(), "levelOfDetail");
            settings.setLevelOfDetail(request.levelOfDetail());
        }
        if (request.personalizedRecommendations() != null) settings.setPersonalizedRecommendations(request.personalizedRecommendations());
        if (request.aiResumeTailoring() != null) settings.setAiResumeTailoring(request.aiResumeTailoring());
        if (request.aiCoverLetterGeneration() != null) settings.setAiCoverLetterGeneration(request.aiCoverLetterGeneration());
        if (request.aiInterviewPrep() != null) settings.setAiInterviewPrep(request.aiInterviewPrep());
        if (request.useDataForModelImprovement() != null) settings.setUseDataForModelImprovement(request.useDataForModelImprovement());
        if (request.notifyJobMatches() != null) settings.setNotifyJobMatches(request.notifyJobMatches());
        if (request.notifyApplicationUpdates() != null) settings.setNotifyApplicationUpdates(request.notifyApplicationUpdates());
        if (request.notifyInterviewReminders() != null) settings.setNotifyInterviewReminders(request.notifyInterviewReminders());
        if (request.notifyWeeklyDigest() != null) settings.setNotifyWeeklyDigest(request.notifyWeeklyDigest());
        if (request.notifyProductUpdates() != null) settings.setNotifyProductUpdates(request.notifyProductUpdates());
        if (request.theme() != null) {
            requireOneOf(THEMES, request.theme(), "theme");
            settings.setTheme(request.theme());
        }
        if (request.planTier() != null) settings.setPlanTier(request.planTier());

        return settings;
    }

    /** Throws if the candidate has turned AI resume tailoring off - called before {@code
     * TailoringJobService.submit} queues a job. */
    @Transactional
    public void requireResumeTailoringEnabled(UUID candidateId) {
        if (!get(candidateId).isAiResumeTailoring()) {
            throw new AiFeatureDisabledException("AI resume tailoring is turned off in Settings > AI Preferences for this candidate.");
        }
    }

    /** Called before generating an interview-prep answer judgment. */
    @Transactional
    public void requireInterviewPrepEnabled(UUID candidateId) {
        if (!get(candidateId).isAiInterviewPrep()) {
            throw new AiFeatureDisabledException("AI interview preparation is turned off in Settings > AI Preferences for this candidate.");
        }
    }

    /** Non-throwing - {@code ResumeTailoringService} uses this to decide whether to skip
     * cover-letter generation rather than fail the whole tailoring run. */
    @Transactional
    public boolean isCoverLetterGenerationEnabled(UUID candidateId) {
        return get(candidateId).isAiCoverLetterGeneration();
    }

    private void requireOneOf(Set<String> allowed, String value, String field) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " must be one of " + allowed + ", got: " + value);
        }
    }

    public static class AiFeatureDisabledException extends RuntimeException {
        public AiFeatureDisabledException(String message) {
            super(message);
        }
    }
}
