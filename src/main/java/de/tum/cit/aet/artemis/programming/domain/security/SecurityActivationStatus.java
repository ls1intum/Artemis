package de.tum.cit.aet.artemis.programming.domain.security;

/**
 * The persisted, settled activation state of the Security Framework for a programming exercise.
 * <p>
 * Only settled states are stored server-side. The transient client states (GENERATING while a policy
 * is being generated and committed, DELETING while it is being removed) and the ERROR state exist
 * purely in the client while an HTTP request is in flight or has failed; the server never persists them.
 */
public enum SecurityActivationStatus {
    INACTIVE, ACTIVE
}
