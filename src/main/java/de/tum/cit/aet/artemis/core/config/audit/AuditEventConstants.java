package de.tum.cit.aet.artemis.core.config.audit;

import java.util.Set;

public class AuditEventConstants {

    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    public static final String AUTHENTICATION_PASSKEY_SUCCESS = "AUTHENTICATION_PASSKEY_SUCCESS";

    public static final String SAML2_AUTHENTICATION_SUCCESS = "SAML2_AUTHENTICATION_SUCCESS";

    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";

    // Account lifecycle events. Unlike the authentication events above - which Spring Boot's AuthenticationAuditListener
    // emits automatically on every single login - these are recorded explicitly at the point an account actually changes.
    // They are low-volume by nature and describe changes to credentials or identity, which is why they are tracked
    // separately from the high-volume login records (see ACCOUNT_SECURITY_EVENT_TYPES).

    /** A password reset was requested for an existing, resettable account, and the reset mail was sent. */
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";

    /** A password reset was requested for an identifier that matches no account, or one that cannot be reset in Artemis. */
    public static final String PASSWORD_RESET_REQUEST_REJECTED = "PASSWORD_RESET_REQUEST_REJECTED";

    /** A password reset was completed: an account's password was replaced using a reset key. */
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";

    /** A user changed the e-mail address on their own account. */
    public static final String ACCOUNT_EMAIL_CHANGED = "ACCOUNT_EMAIL_CHANGED";

    /** A new account was created through self-registration. */
    public static final String ACCOUNT_REGISTERED = "ACCOUNT_REGISTERED";

    /**
     * The audit event types that record a change to an account's credentials or identity.
     * <p>
     * These are grouped because they have a fundamentally different retention profile from the authentication events.
     * Login events are high-volume and individually uninteresting after a short while, and they are what makes the audit
     * table large. The events below are rare and are the ones needed to reconstruct how an account reached its current
     * state - a question typically asked long after the fact - so they need to outlive the login records. Any pruning of
     * the audit table should use this set to retain them longer than the general retention period.
     */
    public static final Set<String> ACCOUNT_SECURITY_EVENT_TYPES = Set.of(PASSWORD_RESET_REQUESTED, PASSWORD_RESET_REQUEST_REJECTED, PASSWORD_RESET_COMPLETED,
            ACCOUNT_EMAIL_CHANGED, ACCOUNT_REGISTERED);

    /**
     * Utility class, should not be instantiated.
     */
    private AuditEventConstants() {
    }

}
