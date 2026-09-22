package de.tum.cit.aet.artemis.aiworker.domain;

/** Execution transport states, independent of workload interpretation. */
public enum WorkerCommandType {
    START, CANCEL, FINISH, RENEW
}
