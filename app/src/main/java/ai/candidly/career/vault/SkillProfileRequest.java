package ai.candidly.career.vault;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record SkillProfileRequest(@NotBlank String skillName, SkillProficiencyLevel proficiencyLevel,
        Double yearsOfExperience, Integer confidenceScore, LocalDate lastUsedOn, String lastUsedVersion) {
}
