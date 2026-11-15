package de.tum.cit.aet.artemis.aiworker.domain;

/** Execution transport states, independent of workload interpretation. */
public enum WorkerEventType {
    HEARTBEAT, STARTED, PROGRESS, ACCOUNTING, CHECKPOINT, FINISHED, CANCELLED, ERROR
}
