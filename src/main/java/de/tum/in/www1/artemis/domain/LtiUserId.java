package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A LtiUserId.
 */
@Entity
@Table(name = "lti_user_id")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LtiUserId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lti_user_id")
    private String ltiUserId;

    @OneToOne
    @JoinColumn(unique = true)
    private User user;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LtiUserId ltiUserId = (LtiUserId) o;
        if (ltiUserId.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), ltiUserId.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "LtiUserId{" +
            "id=" + getId() +
            ", ltiUserId='" + getLtiUserId() + "'" +
            "}";
    }
}
