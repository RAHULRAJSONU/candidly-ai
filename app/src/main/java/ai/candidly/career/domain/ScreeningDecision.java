package ai.candidly.career.domain;

/** Outcome of the prompt-injection/guardrail screen (docs/02 §1, C-1) run over raw job text. */
public enum ScreeningDecision {
    PASS,
    REVIEW,
    BLOCK
}
