package ai.candidly.career.pipeline;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OfferRequest(@NotNull UUID jobPostingId, Long compensationMinorUnits, String notes) {
}
