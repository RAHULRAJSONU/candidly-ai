package ai.candidly.career.emailintake;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort "Sep 24, 2026" + "2:00 PM" extraction from free text, deliberately kept
 * as plain regex/{@code DateTimeFormatter} code rather than a TypeSafe judgment - exact
 * date/time parsing is a "keep known rules and exact lookups in code" case per the
 * typesafe-ai skill, not a semantic-ambiguity judgment a model is well-suited for.
 * Assumes UTC when a date and time are both found (no timezone is reliably present in
 * casual recruiter email text) - the UI shows this as a suggestion the human confirms or
 * edits, never as a fact acted on automatically.
 */
public final class HeuristicDateTimeExtractor {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

    private HeuristicDateTimeExtractor() {
    }

    public static Optional<Instant> extract(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (!dateMatcher.find()) {
            return Optional.empty();
        }
        LocalDate date;
        try {
            String normalized = dateMatcher.group(1).substring(0, 3) + " " + dateMatcher.group(2) + " " + dateMatcher.group(3);
            date = LocalDate.parse(normalized, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }

        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        LocalTime time = LocalTime.NOON;
        if (timeMatcher.find()) {
            int hour = Integer.parseInt(timeMatcher.group(1));
            int minute = Integer.parseInt(timeMatcher.group(2));
            String meridiem = timeMatcher.group(3);
            if (meridiem != null && meridiem.equalsIgnoreCase("PM") && hour < 12) {
                hour += 12;
            }
            if (meridiem != null && meridiem.equalsIgnoreCase("AM") && hour == 12) {
                hour = 0;
            }
            if (hour <= 23 && minute <= 59) {
                time = LocalTime.of(hour, minute);
            }
        }

        return Optional.of(LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC));
    }
}
