package ai.candidly.career.vault;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record ExperienceRequest(
        @NotBlank String employer,
        @NotBlank String title,
        LocalDate startDate,
        LocalDate endDate,
        String narrative,
        Set<String> verifiedMetrics,
        Set<String> rawSkillMentions) {
}
