package ai.candidly.career.typesafe;

/**
 * One question sent to the TypeSafe System One API.
 *
 * <p>{@code criteria} shape depends on {@code type}:
 * <ul>
 *   <li>{@code noul}   - {@code Map<String,String>} with keys "true"/"false" (optional)</li>
 *   <li>{@code choice} - {@code Map<String,String>} of option -&gt; description (max 255 options)</li>
 *   <li>{@code score}  - ordered {@code List<String>} of 2-10 level descriptions</li>
 * </ul>
 */
public record TypeSafeQuestion(String type, String instructions, Object criteria) {

    public static TypeSafeQuestion noul(String instructions, String whenTrue, String whenFalse) {
        return new TypeSafeQuestion("noul", instructions, java.util.Map.of("true", whenTrue, "false", whenFalse));
    }

    public static TypeSafeQuestion choice(String instructions, java.util.Map<String, String> options) {
        return new TypeSafeQuestion("choice", instructions, options);
    }

    public static TypeSafeQuestion score(String instructions, java.util.List<String> levels) {
        return new TypeSafeQuestion("score", instructions, levels);
    }
}
