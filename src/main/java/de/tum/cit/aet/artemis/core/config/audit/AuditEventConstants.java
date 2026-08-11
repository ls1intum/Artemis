package de.tum.cit.aet.artemis.core.config.audit;

import java.util.Set;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Audit event type constants and the taxonomy that maps each type to one of the three audit logs.
 * <p>
 * Audit events are split across three physical tables so they can be filtered and retained independently instead of
 * scanning one ever-growing table:
 * <ul>
 * <li><b>General</b> ({@code jhi_persistent_audit_event}) - authentication / login events. High volume, short retention.</li>
 * <li><b>Security</b> ({@code security_audit_event}) - account credential and identity changes. Low volume, long retention
 * (needed to prove account provenance, e.g. for exam disputes).</li>
 * <li><b>Application</b> ({@code application_audit_event}) - domain actions such as {@code DELETE_EXERCISE} or
 * {@code RESET_EXAM}. Long retention for the same reason, and the default bucket for any unrecognised type so a newly
 * added domain event is over-retained rather than silently lost.</li>
 * </ul>
 * {@link AuditEventTypeClassifier} is the single place that turns a type string into an {@link AuditLogType}; the sets
 * below are its source of truth.
 */
public class AuditEventConstants {

    // --- General / authentication events (Spring Boot's AuthenticationAuditListener emits these on every login) ---

    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    public static final String AUTHENTICATION_PASSKEY_SUCCESS = "AUTHENTICATION_PASSKEY_SUCCESS";

    public static final String SAML2_AUTHENTICATION_SUCCESS = "SAML2_AUTHENTICATION_SUCCESS";

    /** Emitted by Spring Boot on a failed login attempt (wrong password, unknown user, locked account, ...). */
    public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";

    /** Emitted by Spring Boot when a session is logged out. */
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";

    /** Emitted by Spring Security on an access-denied decision. Deliberately not persisted (see CustomAuditEventRepository). */
    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";

    // --- Security events: changes to an account's credentials or identity. Recorded explicitly at the point of change. ---

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

    /** A new account was provisioned from a SAML2 assertion on first login. */
    public static final String SAML2_ACCOUNT_CREATE = "SAML2_ACCOUNT_CREATE";

    /**
     * Emitted by Spring Boot when one account assumes another account's identity. Artemis does not currently configure
     * Spring Security's switch-user filter, so this is not expected to occur; it is classified as a security event
     * because if it ever did occur, who acted as whom is precisely what an investigation would need to reconstruct.
     */
    public static final String AUTHENTICATION_SWITCH = "AUTHENTICATION_SWITCH";

    /**
     * Authentication / login event types. These stay in the general audit log ({@code jhi_persistent_audit_event}) with
     * the short retention: they are high-volume and an individual login is rarely of interest after a short while.
     * Failed logins belong here too: they are part of the login record and are the highest-volume type of all, so the
     * five-year retention of the other logs would be the wrong trade for them.
     * <p>
     * Keep in sync with the event type lists in {@code 20260803140000_changelog.xml}, which migrates existing rows.
     */
    public static final Set<String> GENERAL_EVENT_TYPES = Set.of(AUTHENTICATION_SUCCESS, AUTHENTICATION_PASSKEY_SUCCESS, SAML2_AUTHENTICATION_SUCCESS, AUTHENTICATION_FAILURE,
            LOGOUT_SUCCESS);

    /**
     * Security event types: changes to an account's credentials or identity. Routed to {@code security_audit_event} and
     * retained for years, because they are exactly what an investigation into account provenance (e.g. an exam dispute)
     * reconstructs a timeline from, and such investigations often start long after the fact.
     * <p>
     * The four password and credential events are declared in {@link Constants} rather than here, because they are
     * recorded by {@code AccountSecurityNotificationService} alongside the mail it sends and predate this taxonomy.
     * They belong in this set for the same reason as the rest: each one replaces or destroys a credential, which is
     * precisely what such an investigation asks about. Without them a password change would land in the application
     * log, where the admin view's security filter would not show it.
     * <p>
     * Keep in sync with the event type lists in {@code 20260803140000_changelog.xml}, which migrates existing rows.
     */
    public static final Set<String> SECURITY_EVENT_TYPES = Set.of(PASSWORD_RESET_REQUESTED, PASSWORD_RESET_REQUEST_REJECTED, PASSWORD_RESET_COMPLETED, ACCOUNT_EMAIL_CHANGED,
            ACCOUNT_REGISTERED, SAML2_ACCOUNT_CREATE, AUTHENTICATION_SWITCH, Constants.CHANGE_OWN_PASSWORD, Constants.COMPLETE_PASSWORD_RESET, Constants.ADMIN_CHANGE_USER_PASSWORD,
            Constants.REVOKE_OWN_CREDENTIALS);

    /**
     * @deprecated use {@link #SECURITY_EVENT_TYPES}. Retained as an alias so existing references keep compiling.
     */
    @Deprecated
    public static final Set<String> ACCOUNT_SECURITY_EVENT_TYPES = SECURITY_EVENT_TYPES;

    /**
     * Utility class, should not be instantiated.
     */
    private AuditEventConstants() {
    }

}
