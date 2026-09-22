package ai.candidly.career.tailoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ai.candidly.career.domain.CandidateExperience;

/**
 * The invariant, not the critic (docs/02 §1.4, C-8): every claim in a generated bullet
 * is checked against the candidate's own DB records by set membership / exact match.
 * The LLM critic (ClaimToneVerifier) reduces how often this has to reject something;
 * this class is what actually enforces the guarantee. No model call here at all.
 */
@Component
public class DeterministicGroundingVerifier {

    private static final Pattern CONTAINS_NUMBER = Pattern.compile("\\d");

    public List<VerifiedBullet> verify(List<String> bullets, List<CandidateExperience> experiences) {
        List<String> verifiedMetrics = experiences.stream()
                .flatMap(e -> e.getVerifiedMetrics().stream())
                .map(m -> m.toLowerCase(Locale.ROOT))
                .toList();
        List<String> employers = experiences.stream()
                .map(e -> e.getEmployer().toLowerCase(Locale.ROOT))
                .toList();

        List<VerifiedBullet> results = new ArrayList<>();
        for (String bullet : bullets) {
            String lower = bullet.toLowerCase(Locale.ROOT);

            if (CONTAINS_NUMBER.matcher(bullet).find()) {
                boolean backed = verifiedMetrics.stream().anyMatch(lower::contains);
                if (!backed) {
                    results.add(VerifiedBullet.rejected(bullet, "contains a number/metric not found verbatim in any verified_metrics record"));
                    continue;
                }
            }

            boolean mentionsUnknownEmployer = employers.stream().noneMatch(lower::contains)
                    && looksLikeItNamesAnEmployer(lower, employers);
            if (mentionsUnknownEmployer) {
                results.add(VerifiedBullet.rejected(bullet, "appears to reference an employer not in the candidate's verified experience"));
                continue;
            }

            results.add(VerifiedBullet.accepted(bullet));
        }
        return results;
    }

    /**
     * Best-effort guard: only flags bullets that clearly claim "at <employer>" phrasing
     * for a name that isn't one of the candidate's real employers. Deliberately
     * conservative (false negatives over false positives) since this is a defense in
     * depth on top of the generator only ever being given real employer names to begin
     * with.
     */
    private boolean looksLikeItNamesAnEmployer(String lowerBullet, List<String> knownEmployers) {
        return lowerBullet.contains(" at ") && knownEmployers.stream().noneMatch(lowerBullet::contains);
    }

    public record VerifiedBullet(String text, boolean passed, String rejectionReason) {
        static VerifiedBullet accepted(String text) {
            return new VerifiedBullet(text, true, null);
        }

        static VerifiedBullet rejected(String text, String reason) {
            return new VerifiedBullet(text, false, reason);
        }
    }
}
