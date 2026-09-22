package ai.candidly.career.screening;

import ai.candidly.career.domain.ScreeningDecision;

public record ScreeningResult(ScreeningDecision decision, String humanReadableReason) {
}
