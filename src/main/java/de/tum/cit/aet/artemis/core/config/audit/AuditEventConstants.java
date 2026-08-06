package de.tum.cit.aet.artemis.core.config.audit;

import java.util.Set;

public class AuditEventConstants {

    // --- Authentication events. Spring Boot's AuthenticationAuditListener writes one row per login attempt. ---

    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    public static final String AUTHENTICATION_PASSKEY_SUCCESS = "AUTHENTICATION_PASSKEY_SUCCESS";

    public static final String SAML2_AUTHENTICATION_SUCCESS = "SAML2_AUTHENTICATION_SUCCESS";

    /** Emitted on a failed login attempt (wrong password, unknown user, locked account, ...). */
    public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";

    /** Emitted when a session is logged out. */
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";

    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";

    /**
     * The general event types: the login record, i.e. the authentication events.
     * <p>
     * Named "general" because this is the category the audit log's short retention applies to, as opposed to the
     * deliberate actions that are kept for years.
     * <p>
     * These are what make the audit table large: one row per login attempt, and an individual attempt is rarely of
     * interest a few weeks later. Failed logins and logouts belong here too, failures being the highest-volume type of
     * all. Everything else in the table is a deliberate and comparatively rare action - deleting an exercise, resetting
     * an exam, changing an account's credentials - which stays relevant far longer, so the two are pruned on separate
     * schedules.
     */
    public static final Set<String> GENERAL_EVENT_TYPES = Set.of(AUTHENTICATION_SUCCESS, AUTHENTICATION_PASSKEY_SUCCESS, SAML2_AUTHENTICATION_SUCCESS, AUTHENTICATION_FAILURE,
            LOGOUT_SUCCESS);

    /**
     * Utility class, should not be instantiated.
     */
    private AuditEventConstants() {
    }

}
