package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(@NotBlank String title, String description, String url, LocalDate startDate,
        LocalDate endDate, Set<String> technologies) {
}
