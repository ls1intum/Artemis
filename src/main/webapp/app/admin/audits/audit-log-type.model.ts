/**
 * The three audit logs, each backed by its own table with its own retention period.
 * Mirrors the server-side `AuditLogType` enum; the value is sent as the `logType` query parameter.
 */
export enum AuditLogType {
    /** Authentication / login events. High volume, short retention. */
    GENERAL = 'GENERAL',
    /** Account credential and identity changes. Long retention. */
    SECURITY = 'SECURITY',
    /** Application / domain actions (e.g. deleting an exercise). Long retention. */
    APPLICATION = 'APPLICATION',
}
