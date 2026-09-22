package ai.candidly.career.api;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public record JobPostingRequest(
        @NotBlank String sourceId,
        @NotBlank String company,
        @NotBlank String title,
        @NotBlank String location,
        boolean remote,
        Long compMinMinorUnits,
        Long compMaxMinorUnits,
        Set<String> acceptedWorkAuthorizations,
        Set<String> mandatorySkillMentions,
        Set<String> preferredSkillMentions,
        String domain,
        int minYearsExperience,
        @NotBlank String rawDescription) {
}
