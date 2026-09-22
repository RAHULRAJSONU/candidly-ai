package ai.candidly.career.domain;

/** Lifecycle of an async tailoring run (docs/03 §4: tailoring is inherently multi-call and slow). */
public enum TailoringJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}
