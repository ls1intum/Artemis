package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.User;

@Entity
public abstract class IrisChatSession extends IrisSession {

    private long userId;

    @Transient
    private long entityId;

    public IrisChatSession(User user) {
        this.userId = user.getId();
    }

    public IrisChatSession() {
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    @JsonProperty
    public long getEntityId() {
        return entityId;
    }
}
