package de.tum.cit.aet.artemis.admin.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Common read contract for a persisted audit event, implemented by the three audit-log entities
 * ({@link PersistentAuditEvent}, {@link SecurityAuditEvent}, {@link ApplicationAuditEvent}). It lets the converter and
 * other cross-table code treat any of them uniformly without caring which table a row came from.
 */
public interface PersistedAuditEvent {

    Long getId();

    String getPrincipal();

    Instant getAuditEventDate();

    String getAuditEventType();

    Map<String, String> getData();
}
