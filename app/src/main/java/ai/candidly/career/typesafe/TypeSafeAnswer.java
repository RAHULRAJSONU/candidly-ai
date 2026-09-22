package ai.candidly.career.typesafe;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One answer in a TypeSafe response. Fields are nullable/absent depending on {@code type}:
 * noul -&gt; {@code noul}; choice -&gt; {@code choice}/{@code probabilities}/{@code confidence};
 * score -&gt; {@code score}/{@code legend}/{@code probabilities}/{@code confidence}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeSafeAnswer(
        String type,
        Double noul,
        String choice,
        Double score,
        Map<String, String> legend,
        Map<String, Double> probabilities,
        Double confidence) {

    /** Convenience accessor: yes-probability for a noul, top-choice probability, or normalised score confidence. */
    public double probabilityOfYes() {
        if (noul != null) {
            return noul;
        }
        throw new IllegalStateException("Not a noul answer: type=" + type);
    }
}
