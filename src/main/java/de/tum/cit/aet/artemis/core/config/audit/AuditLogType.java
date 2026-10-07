package de.tum.cit.aet.artemis.core.config.audit;

/**
 * The three audit logs an audit event can be routed to. Each corresponds to a physical table with its own retention.
 * The admin audit view exposes one tab per value.
 */
public enum AuditLogType {

    /** Authentication / login events. Stored in {@code jhi_persistent_audit_event}, short retention. */
    GENERAL,

    /** Account credential and identity changes. Stored in {@code security_audit_event}, long retention. */
    SECURITY,

    /** Application / domain actions (e.g. {@code DELETE_EXERCISE}). Stored in {@code application_audit_event}, long retention. */
    APPLICATION
}
