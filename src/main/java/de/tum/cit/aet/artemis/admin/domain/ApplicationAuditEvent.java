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
 * A persisted audit event recording an application / domain action (deleting an exercise, resetting an exam,
 * ...). Structurally identical to {@link PersistentAuditEvent}, but stored in its own table so application
 * events can be filtered and retained independently from the high-volume general/authentication log.
 */
@Entity
@Table(name = "application_audit_event")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AggregateRoot("Audit trail, deliberately independent.")
public class ApplicationAuditEvent extends AbstractPersistedAuditEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    @ElementCollection
    @MapKeyColumn(name = "name")
    @Column(name = "event_data")
    @CollectionTable(name = "application_audit_evt_data", joinColumns = @JoinColumn(name = "event_id"))
    private Map<String, String> data = new HashMap<>();

    @Override
    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    @Override
    protected String auditLogName() {
        return "ApplicationAuditEvent";
    }
}
