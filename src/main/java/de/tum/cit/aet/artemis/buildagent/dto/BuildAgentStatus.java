package de.tum.cit.aet.artemis.buildagent.dto;

public enum BuildAgentStatus {
    ACTIVE, IDLE, PAUSED, SELF_PAUSED,
    /**
     * The agent is paused because a maintenance action (cache cleanup, cache wipe, or Docker image clearing) is
     * currently running. Distinct from {@link #PAUSED} so operator dashboards can tell user-initiated
     * administrative pauses apart from scheduled or admin-triggered maintenance windows.
     */
    MAINTENANCE
}
