package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record AchievementRequest(@NotBlank String title, String description, LocalDate occurredOn, Set<String> tags) {
}
