package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import de.tum.cit.aet.artemis.domain.User;

@Entity
public abstract class IrisChatSession extends IrisSession {

    @ManyToOne
    private User user;

    public IrisChatSession(User user) {
        this.user = user;
    }

    public IrisChatSession() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
