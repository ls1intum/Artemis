/**
 * The `data` payload of an audit event: the key/value map the server stored in the event's child table.
 *
 * `remoteAddress`, `sessionId` and `message` are the keys Spring Boot's authentication events use, so they are named
 * here. Everything else is application-specific — a domain event records its own keys (`course`, `sessionId`, ...), and
 * an account security event records e.g. `reason` — which is why this is an index signature rather than a fixed shape.
 */
export interface AuditData {
    remoteAddress?: string;
    sessionId?: string;
    message?: string;
    [key: string]: string | undefined;
}
