package de.tum.cit.aet.artemis.admin.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A persisted audit event recording an application / domain action (deleting an exercise, resetting an exam,
 * ...). Structurally identical to {@link PersistentAuditEvent}, but stored in its own table so application
 * events can be filtered and retained independently from the high-volume general/authentication log.
 */
@Entity
@Table(name = "application_audit_event")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApplicationAuditEvent implements Serializable, PersistedAuditEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @NonNull
    @Column(nullable = false)
    private String principal;

    @Column(name = "event_date")
    private Instant auditEventDate;

    @Column(name = "event_type")
    private String auditEventType;

    @ElementCollection
    @MapKeyColumn(name = "name")
    @Column(name = "event_data")
    @CollectionTable(name = "application_audit_evt_data", joinColumns = @JoinColumn(name = "event_id"))
    private Map<String, String> data = new HashMap<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    @Override
    public Instant getAuditEventDate() {
        return auditEventDate;
    }

    public void setAuditEventDate(Instant auditEventDate) {
        this.auditEventDate = auditEventDate;
    }

    @Override
    public String getAuditEventType() {
        return auditEventType;
    }

    public void setAuditEventType(String auditEventType) {
        this.auditEventType = auditEventType;
    }

    @Override
    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ApplicationAuditEvent that = (ApplicationAuditEvent) obj;
        if (that.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ApplicationAuditEvent{" + "principal='" + principal + '\'' + ", auditEventDate=" + auditEventDate + ", auditEventType='" + auditEventType + '\'' + '}';
    }
}
