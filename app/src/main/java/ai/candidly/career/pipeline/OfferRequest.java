package ai.candidly.career.pipeline;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** {@code currency} is optional - it defaults to the candidate's preferred currency at record time (see {@link Offer}). */
public record OfferRequest(@NotNull UUID jobPostingId, Long compensationMinorUnits, String currency, String notes) {
}
