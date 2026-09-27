package ai.candidly.career.positioning;

import java.util.List;

/** Response shape for {@code GET /api/candidates/{id}/profile-positioning}. */
public record ProfilePositioningView(
        boolean hasEnoughData,
        String suggestedSeniority,
        double seniorityConfidence,
        List<TitleSuggestion> suggestedTitles,
        List<String> suggestedKeywords,
        String rationale) {

    public record TitleSuggestion(String title, double confidence) {
    }
}
