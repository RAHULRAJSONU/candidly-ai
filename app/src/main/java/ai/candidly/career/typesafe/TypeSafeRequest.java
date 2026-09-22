package ai.candidly.career.typesafe;

import java.util.Map;

/** state: the content to evaluate (string, object, or array). */
public record TypeSafeRequest(Object state, String model, Map<String, TypeSafeQuestion> questions) {
}
