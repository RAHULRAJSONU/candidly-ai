package ai.candidly.career.pipeline;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record ManualApplicationRequest(@NotBlank String company, @NotBlank String role, LocalDate appliedDate, String notes) {
}
