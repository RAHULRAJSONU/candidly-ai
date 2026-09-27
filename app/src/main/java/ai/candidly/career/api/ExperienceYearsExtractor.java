package ai.candidly.career.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort "5+ years of experience" / "3-5 years" style extraction from free text,
 * deliberately kept as plain regex rather than a TypeSafe judgment - same "keep known
 * rules and exact lookups in code" precedent already established by
 * {@code emailintake.HeuristicDateTimeExtractor} for exact date/time parsing. Returns the
 * largest plausible figure found, or 0 (no stated minimum) if nothing matches.
 */
public final class ExperienceYearsExtractor {

    private static final Pattern YEARS_PATTERN = Pattern.compile(
            "(\\d{1,2})\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?\\+?\\s*years?", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PLAUSIBLE_YEARS = 20;

    private ExperienceYearsExtractor() {
    }

    public static int extract(String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = YEARS_PATTERN.matcher(text);
        int max = 0;
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (years <= MAX_PLAUSIBLE_YEARS) {
                max = Math.max(max, years);
            }
        }
        return max;
    }
}
