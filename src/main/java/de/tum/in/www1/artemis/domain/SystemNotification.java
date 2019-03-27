package de.tum.in.www1.artemis.domain;


import de.tum.in.www1.artemis.domain.enumeration.SystemNotificationType;
import org.aspectj.weaver.ast.Not;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A SystemNotification.
 */
@Entity
@DiscriminatorValue(value="S")
public class SystemNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Column(name = "expire_date")
    private ZonedDateTime expireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private SystemNotificationType type;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public ZonedDateTime getExpireDate() {
        return expireDate;
    }

    public SystemNotification expireDate(ZonedDateTime expireDate) {
        this.expireDate = expireDate;
        return this;
    }

    public void setExpireDate(ZonedDateTime expireDate) {
        this.expireDate = expireDate;
    }

    public SystemNotificationType getType() {
        return type;
    }

    public SystemNotification type(SystemNotificationType type) {
        this.type = type;
        return this;
    }

    public void setType(SystemNotificationType type) {
        this.type = type;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SystemNotification systemNotification = (SystemNotification) o;
        if (systemNotification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), systemNotification.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "SystemNotification{" +
            "id=" + getId() +
            ", expireDate='" + getExpireDate() + "'" +
            ", type='" + getType() + "'" +
            "}";
    }
}
