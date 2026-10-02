package de.tum.cit.aet.artemis.aiworker.domain;

/** Core-observed availability of a configured standalone generation worker. */
public enum WorkerState {
    OFFLINE, NOT_READY, AVAILABLE, RESERVED, BUSY
}
