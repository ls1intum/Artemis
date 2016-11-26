package de.tum.in.www1.exerciseapp.domain;

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
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "lti_user_id")
    private String ltiUserId;

    @OneToOne
    @JoinColumn(unique = true)
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLtiUserId() {
        return ltiUserId;
    }

    public void setLtiUserId(String ltiUserId) {
        this.ltiUserId = ltiUserId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LtiUserId ltiUserId = (LtiUserId) o;
        if(ltiUserId.id == null || id == null) {
            return false;
        }
        return Objects.equals(id, ltiUserId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LtiUserId{" +
            "id=" + id +
            ", ltiUserId='" + ltiUserId + "'" +
            '}';
    }
}
