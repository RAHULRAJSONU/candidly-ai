package ai.candidly.career.settings;

/**
 * Partial-update payload for {@code PUT /api/candidates/{id}/settings} - every field is
 * optional, {@code null} means "leave unchanged" (same convention as {@code
 * CandidateProfileUpdateRequest}). Booleans use the boxed {@link Boolean} type for the same
 * reason.
 */
public record CandidateSettingsRequest(
        String preferredAiModel,
        String responseStyle,
        String levelOfDetail,
        Boolean personalizedRecommendations,
        Boolean aiResumeTailoring,
        Boolean aiCoverLetterGeneration,
        Boolean aiInterviewPrep,
        Boolean useDataForModelImprovement,
        Boolean notifyJobMatches,
        Boolean notifyApplicationUpdates,
        Boolean notifyInterviewReminders,
        Boolean notifyWeeklyDigest,
        Boolean notifyProductUpdates,
        String theme,
        String planTier) {
}
