package ai.candidly.career.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

public record CandidateRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String location,
        @NotEmpty Set<String> workAuthorizations,
        long compFloorMinorUnits,
        /** ISO 4217 code compFloorMinorUnits is in; optional, null reads as USD (see Candidate#getPreferredCurrency). */
        String preferredCurrency,
        @NotEmpty Set<String> rawSkillMentions,
        @Valid List<ExperienceRequest> experiences) {

    public record ExperienceRequest(
            @NotBlank String employer,
            @NotBlank String title,
            LocalDate startDate,
            LocalDate endDate,
            @NotBlank String narrative,
            Set<String> verifiedMetrics,
            Set<String> rawSkillMentions) {
    }
}
