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

    private static final int K = 60;

    private ReciprocalRankFusion() {
    }

    /** Fused ranking, best first. Items appearing in only one input ranking are still included. */
    public static List<UUID> fuse(List<UUID> lexicalRanking, List<UUID> denseRanking) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        accumulate(scores, lexicalRanking);
        accumulate(scores, denseRanking);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void accumulate(Map<UUID, Double> scores, List<UUID> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            int rank1Based = i + 1;
            scores.merge(ranking.get(i), 1.0 / (K + rank1Based), Double::sum);
        }
    }
}
