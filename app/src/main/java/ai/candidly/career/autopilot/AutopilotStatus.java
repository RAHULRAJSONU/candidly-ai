package ai.candidly.career.autopilot;

/** Whether the agent's scheduled cycle is currently allowed to run for a given candidate. */
public enum AutopilotStatus {
    STOPPED,
    RUNNING,
    PAUSED
}
