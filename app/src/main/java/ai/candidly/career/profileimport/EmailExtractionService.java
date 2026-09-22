package ai.candidly.career.profileimport;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * "Select instead of generate" (typesafe-ai skill, pre-parsed value extraction
 * pattern): a recall-tuned regex finds every email-looking span in the source text,
 * and - only when there's more than one candidate to disambiguate - TypeSafe's Choice
 * primitive picks which one is the document owner's own contact email (not a
 * reference's, recruiter's, or employer's). The returned value is always a verbatim
 * copy of a regex-found span, never model-generated text, so it can't be invented or
 * have a character transposed. This replaces trusting the free-text Groq completion
 * ({@link ProfileExtractionService}) for just this one field - unlike name, location,
 * or the narrative summaries, an email is lexically well-defined (regex-identifiable)
 * and a wrong value here corrupts the candidate's account identity, so it warrants the
 * stricter guarantee. Skipped entirely (falls back to Groq's NONE handling) when zero
 * or exactly one candidate is found, since a single unambiguous match needs no model
 * judgment at all.
 */
@Service
public class EmailExtractionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final String NO_MATCH_OPTION = "none_of_the_above";

    private final TypeSafeClient typeSafeClient;

    public EmailExtractionService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    public Optional<String> extractOwnEmail(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return Optional.empty();
        }
        Set<String> candidates = findCandidates(sourceText);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }

        Map<String, String> options = new LinkedHashMap<>();
        for (String candidate : candidates) {
            options.put(candidate, candidate);
        }
        options.put(NO_MATCH_OPTION, "None of these is the document owner's own contact email");

        TypeSafeResponse response = typeSafeClient.ask(
                Map.of("document_text", sourceText),
                Map.of("owner_email", TypeSafeQuestion.choice(
                        "Which of these email addresses, if any, is the document owner's own contact "
                                + "email - the person this resume/profile belongs to - rather than a "
                                + "reference's, recruiter's, or employer's email?",
                        options)));

        TypeSafeAnswer answer = response.answers().get("owner_email");
        if (answer == null || answer.choice() == null || NO_MATCH_OPTION.equals(answer.choice())) {
            return Optional.empty();
        }
        return Optional.of(answer.choice());
    }

    private Set<String> findCandidates(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
