package ai.candidly.career.pipeline;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record InterviewRequest(@NotNull UUID jobPostingId, Instant scheduledAt, InterviewMode mode, String notes) {
}
