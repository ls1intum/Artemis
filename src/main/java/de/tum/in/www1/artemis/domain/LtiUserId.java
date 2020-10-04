package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A LtiUserId.
 */
@Entity
@Table(name = "lti_user_id")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LtiUserId extends DomainObject {

    @Column(name = "lti_user_id")
    private String ltiUserId;

    @OneToOne
    @JoinColumn(unique = true)
    private User user;

    public String getLtiUserId() {
        return ltiUserId;
    }

    public LtiUserId ltiUserId(String ltiUserId) {
        this.ltiUserId = ltiUserId;
        return this;
    }

    public void setLtiUserId(String ltiUserId) {
        this.ltiUserId = ltiUserId;
    }

    public User getUser() {
        return user;
    }

    public LtiUserId user(User user) {
        this.user = user;
        return this;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "LtiUserId{" + "id=" + getId() + ", ltiUserId='" + getLtiUserId() + "'" + "}";
    }
}
