package de.tum.cit.aet.artemis.hyperion.domain;

/** Core-observed availability of a configured standalone generation worker. */
public enum GenerationWorkerState {
    OFFLINE, NOT_READY, AVAILABLE, RESERVED, BUSY
}
