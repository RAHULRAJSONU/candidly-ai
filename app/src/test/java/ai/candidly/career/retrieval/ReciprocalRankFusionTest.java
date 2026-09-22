package ai.candidly.career.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void itemRankedFirstInBothListsWinsFusion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        List<UUID> lexical = List.of(a, b, c);
        List<UUID> dense = List.of(a, c, b);

        List<UUID> fused = ReciprocalRankFusion.fuse(lexical, dense);

        assertThat(fused.get(0)).isEqualTo(a);
    }

    @Test
    void itemInOnlyOneRankingStillAppears() {
        UUID onlyLexical = UUID.randomUUID();
        UUID onlyDense = UUID.randomUUID();

        List<UUID> fused = ReciprocalRankFusion.fuse(List.of(onlyLexical), List.of(onlyDense));

        assertThat(fused).containsExactlyInAnyOrder(onlyLexical, onlyDense);
    }

    @Test
    void agreementBeatsATopRankInOnlyOneList() {
        UUID agreesInBoth = UUID.randomUUID();
        UUID topLexicalOnly = UUID.randomUUID();
        UUID filler1 = UUID.randomUUID();
        UUID filler2 = UUID.randomUUID();

        // topLexicalOnly ranks #1 lexically but doesn't appear in dense at all;
        // agreesInBoth ranks #2 in both - RRF should still favor broad agreement.
        List<UUID> lexical = List.of(topLexicalOnly, agreesInBoth, filler1);
        List<UUID> dense = List.of(filler2, agreesInBoth, filler1);

        List<UUID> fused = ReciprocalRankFusion.fuse(lexical, dense);

        assertThat(fused.get(0)).isEqualTo(agreesInBoth);
    }

    @Test
    void emptyRankingsProduceEmptyFusion() {
        assertThat(ReciprocalRankFusion.fuse(List.of(), List.of())).isEmpty();
    }
}
