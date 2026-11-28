package de.tum.cit.aet.artemis.core.security;

/** Limits failed service credential checks for the current request's calling address. */
public interface ServiceCredentialBudget {

    /** Reserves an attempt before checking a credential, rejecting requests that exhausted their budget. */
    void requireServiceCredentialBudget();

    /** Returns the reserved attempt after successful authentication. */
    void refundServiceCredentialBudget();
}
