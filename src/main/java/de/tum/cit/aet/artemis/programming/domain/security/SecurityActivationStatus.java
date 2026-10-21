package de.tum.cit.aet.artemis.programming.domain.security;

/**
 * The settled activation state of the Security Framework for a programming exercise, derived from its
 * exercise-tests repository (ACTIVE when it contains the Ares security policy).
 * <p>
 * The transient client states (GENERATING while a policy is being committed, DELETING while it is being
 * removed) and the ERROR state exist purely in the client while an HTTP request is in flight or has failed.
 */
public enum SecurityActivationStatus {
    INACTIVE, ACTIVE
}
