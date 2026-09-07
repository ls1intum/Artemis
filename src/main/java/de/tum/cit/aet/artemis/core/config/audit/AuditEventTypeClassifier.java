package de.tum.cit.aet.artemis.core.config.audit;

/**
 * Maps an audit event type string to the {@link AuditLogType} (and therefore the table and retention) it belongs to.
 * This is the single source of truth for the routing; the write path, the cleanup job and the admin read API all use it.
 */
public final class AuditEventTypeClassifier {

    private AuditEventTypeClassifier() {
    }

    /**
     * Classifies an audit event type.
     * <p>
     * The authentication and security type sets are enumerated explicitly; everything else defaults to
     * {@link AuditLogType#APPLICATION}. Making application the default (rather than the general log) means a domain event
     * added in the future is retained for years by default, rather than being dropped after the short general retention -
     * the safe direction when the records may be needed to prove something later.
     *
     * @param eventType the audit event type, may be {@code null}
     * @return the audit log the event belongs to; {@link AuditLogType#APPLICATION} for {@code null} or unknown types
     */
    public static AuditLogType classify(String eventType) {
        if (eventType == null) {
            return AuditLogType.APPLICATION;
        }
        if (AuditEventConstants.GENERAL_EVENT_TYPES.contains(eventType)) {
            return AuditLogType.GENERAL;
        }
        if (AuditEventConstants.SECURITY_EVENT_TYPES.contains(eventType)) {
            return AuditLogType.SECURITY;
        }
        return AuditLogType.APPLICATION;
    }
}
