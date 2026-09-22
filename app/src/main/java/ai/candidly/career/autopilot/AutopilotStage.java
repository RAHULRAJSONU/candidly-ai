package ai.candidly.career.autopilot;

/** The six stages of one agent cycle, mirroring the mock's pipeline tracker. */
public enum AutopilotStage {
    IDLE,
    SEARCH_JOBS,
    MATCH_AND_RANK,
    CUSTOMIZE,
    APPLY,
    TRACK,
    FOLLOW_UP
}
