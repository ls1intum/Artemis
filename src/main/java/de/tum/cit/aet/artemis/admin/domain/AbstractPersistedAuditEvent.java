package de.tum.cit.aet.artemis.admin.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.jspecify.annotations.NonNull;

/** Shared identity and event fields of the independently retained audit logs. */
@MappedSuperclass
public abstract class AbstractPersistedAuditEvent implements Serializable, PersistedAuditEvent {

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
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AbstractPersistedAuditEvent that = (AbstractPersistedAuditEvent) obj;
        if (that.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    /**
     * The stable log label also used for proxy instances of each persisted audit type.
     *
     * @return concrete audit log name
     */
    protected abstract String auditLogName();

    @Override
    public String toString() {
        return auditLogName() + "{" + "principal='" + principal + '\'' + ", auditEventDate=" + auditEventDate + ", auditEventType='" + auditEventType + '\'' + '}';
    }
}
