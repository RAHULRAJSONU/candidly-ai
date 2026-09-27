package ai.candidly.career.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RRF fusion of the lexical and dense retrieval rankings (docs/03 §1: "RRF fusion,
 * k=60, standard"). Each ranking contributes {@code 1/(k+rank)} per item (rank is
 * 1-based); items in both rankings accumulate both contributions. This is the standard
 * fixed-k formula - no tuning knob, which is the point (docs/03 explicitly calls k=60
 * "standard", unlike the scoring weights/thresholds it flags as needing calibration).
 */
public final class ReciprocalRankFusion {

    private static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {
    }

    /** Fused ranking, best first, using the standard k=60. Items appearing in only one
     * input ranking are still included. */
    public static List<UUID> fuse(List<UUID> lexicalRanking, List<UUID> denseRanking) {
        return fuse(List.of(lexicalRanking, denseRanking), DEFAULT_K);
    }

    /** Same fusion over any number of rankings (e.g. lexical + dense + a preference-based
     * list), with k as a tuning knob - see {@code candidly.retrieval.rrf-k}. */
    public static List<UUID> fuse(List<List<UUID>> rankings, int k) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (List<UUID> ranking : rankings) {
            accumulate(scores, ranking, k);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void accumulate(Map<UUID, Double> scores, List<UUID> ranking, int k) {
        for (int i = 0; i < ranking.size(); i++) {
            int rank1Based = i + 1;
            scores.merge(ranking.get(i), 1.0 / (k + rank1Based), Double::sum);
        }
    }
}
