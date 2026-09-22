package ai.candidly.career.typesafe;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeSafeResponse(String model, Map<String, TypeSafeAnswer> answers) {
}
