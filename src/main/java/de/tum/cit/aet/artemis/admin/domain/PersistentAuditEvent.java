package de.tum.cit.aet.artemis.admin.domain;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;

/**
 * Persist AuditEvent managed by the Spring Boot actuator.
 *
 * @see org.springframework.boot.actuate.audit.AuditEvent
 */
@Entity
@Table(name = "jhi_persistent_audit_event")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AggregateRoot("Audit trail, deliberately independent.")
public class PersistentAuditEvent extends AbstractPersistedAuditEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    @ElementCollection
    @MapKeyColumn(name = "name")
    @Column(name = "event_data")
    @CollectionTable(name = "jhi_persistent_audit_evt_data", joinColumns = @JoinColumn(name = "event_id"))
    private Map<String, String> data = new HashMap<>();

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    @Override
    protected String auditLogName() {
        return "PersistentAuditEvent";
    }
}
