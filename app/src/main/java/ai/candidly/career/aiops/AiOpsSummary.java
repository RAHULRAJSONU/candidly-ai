package ai.candidly.career.aiops;

import java.util.Map;

/**
 * Real, aggregated numbers this deployment actually has, for the "AI Ops" view (the
 * mock's "AI / Model Evaluation" screen). Deliberately NOT a multi-provider comparison
 * (GPT-4o vs. Claude vs. Llama vs. Gemini) - this stack only calls Groq (tailoring/
 * drafting), Jina (embeddings), and TypeSafe (judgments), so a side-by-side vendor
 * leaderboard would be fabricated. Every field here is computed from rows already in
 * this database, not invented.
 */
public record AiOpsSummary(
        long totalArtifactsGenerated,
        double groundingPassRate,
        double avgCriticLoopsUsed,
        double avgRejectedClaimsPerArtifact,
        long totalMatchesScored,
        double avgCompositeScore,
        Map<String, Long> screeningDecisionCounts,
        Map<String, Long> artifactStatusCounts) {
}
