package ai.candidly.career.domain;

/**
 * Where a tailored artifact sits in the (simplified) FR-5 lifecycle for this slice.
 * APPROVED/REJECTED are the human-in-the-loop console's decision (docs/01 §2: "Human-in-
 * the-Loop Approval Console - DEFAULT path, not the exception"). Neither this app nor
 * any model submits anything on the candidate's behalf past APPROVED - per docs/04 FR-5,
 * the consumer path ends at the candidate submitting in their own session; APPROVED here
 * just means the artifact is cleared to hand to the candidate for that step.
 */
public enum TailoredArtifactStatus {
    PENDING_APPROVAL,
    NEEDS_HUMAN_REVIEW,
    APPROVED,
    REJECTED
}
