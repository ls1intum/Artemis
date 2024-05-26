package de.tum.in.www1.artemis.domain.iris.session;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

@MappedSuperclass
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisChatSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
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
