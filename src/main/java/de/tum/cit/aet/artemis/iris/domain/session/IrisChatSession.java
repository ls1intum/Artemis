package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.core.domain.User;

@Entity
public abstract class IrisChatSession extends IrisSession {

    private long userId;

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

    public abstract IrisChatMode getMode();
}
