package ai.candidly.career.emailintake;

/**
 * The human-review gate on the agent's *outgoing* reply to a classified email (the mock's
 * separate "Human-in-the-Loop" email-approval screen, distinct from the general inbox).
 * Nothing here ever actually sends an email - this repo has no outbound SMTP/Gmail-send
 * integration - so every terminal status here is just a recorded decision, same as
 * {@code TailoredArtifactStatus.APPROVED} never means "submitted to an employer."
 */
public enum EmailReviewStatus {
    NO_REPLY_NEEDED,
    NEEDS_REVIEW,
    APPROVED_SEND,
    EDITED_SEND,
    SCHEDULED,
    CANCELLED,
    ALTERNATIVE_SUGGESTED
}
