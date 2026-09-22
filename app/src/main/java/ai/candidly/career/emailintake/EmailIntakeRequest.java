package ai.candidly.career.emailintake;

import jakarta.validation.constraints.NotBlank;

public record EmailIntakeRequest(@NotBlank String rawEmailText) {
}
