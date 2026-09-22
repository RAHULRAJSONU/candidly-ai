package ai.candidly.career.tailoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * LLM critic half of the evaluator-optimizer loop (docs/00 §3, docs/03 §5): the
 * deterministic verifier catches unbacked facts, this catches overstated PHRASING of
 * facts that are technically present (e.g. "single-handedly rebuilt" for a metric that
 * says "contributed to a 40% latency reduction"). One Noul per bullet, all asked in one
 * TypeSafe request (typesafe-ai skill: "ask independent questions over the same state
 * together" - fan-out pattern) since each bullet's tone judgment is independent.
 *
 * <p>This is advisory input to the critic loop, not the grounding invariant - per
 * docs/02 §1.4 the critic is never trusted as the guarantee on its own.
 */
@Component
public class ClaimToneVerifier {

    private static final double OVERSTATEMENT_THRESHOLD = 0.6;

    private final TypeSafeClient typeSafeClient;

    public ClaimToneVerifier(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    public List<ToneFinding> check(List<String> bullets) {
        if (bullets.isEmpty()) {
            return List.of();
        }

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < bullets.size(); i++) {
            String key = "bullet_" + i;
            questions.put(key, TypeSafeQuestion.noul(
                    "Does `bullets[" + i + "]` overstate the underlying achievement - claiming more credit, "
                            + "scale, or certainty than a plain reading of a verified metric would support - "
                            + "even if the underlying fact itself is real?",
                    "Phrasing inflates credit/scale/certainty beyond the underlying fact",
                    "Phrasing is a fair, literal rendering of the underlying fact"));
        }

        TypeSafeResponse response = typeSafeClient.ask(Map.of("bullets", bullets), questions);

        return IntStream.range(0, bullets.size())
                .mapToObj(i -> {
                    double overstatementP = response.answers().get("bullet_" + i).noul();
                    return new ToneFinding(bullets.get(i), overstatementP, overstatementP >= OVERSTATEMENT_THRESHOLD);
                })
                .toList();
    }

    public record ToneFinding(String bullet, double overstatementProbability, boolean flagged) {
    }
}
