package de.tum.in.www1.artemis.domain.science;

import java.time.ZonedDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * Individual Science Setting which combined make the Science Settings (inside the hierarchical structure on the client side)
 * The unique constraint is needed to avoid duplications.
 */
@Entity
@Table(name = "science_event")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScienceEvent extends DomainObject {

    @Column(name = "identity", nullable = false)
    private String identity;

    @Column(name = "timestamp", nullable = false)
    private ZonedDateTime timestamp;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "event_type", nullable = false)
    private ScienceEventType type;

    @Column(name = "resource_id")
    private Long resourceId;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public ScienceEventType getType() {
        return type;
    }

    public void setType(ScienceEventType type) {
        this.type = type;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }
}
