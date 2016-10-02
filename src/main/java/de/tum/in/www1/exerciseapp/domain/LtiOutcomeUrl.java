package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A LtiOutcomeUrl.
 */
@Entity
@Table(name = "lti_outcome_url")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LtiOutcomeUrl implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "url")
    private String url;

    @Column(name = "sourced_id")
    private String sourcedId;

    @ManyToOne
    private User user;

    @ManyToOne
    private Exercise exercise;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourcedId() {
        return sourcedId;
    }

    public void setSourcedId(String sourcedId) {
        this.sourcedId = sourcedId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LtiOutcomeUrl ltiOutcomeUrl = (LtiOutcomeUrl) o;
        if(ltiOutcomeUrl.id == null || id == null) {
            return false;
        }
        return Objects.equals(id, ltiOutcomeUrl.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "LtiOutcomeUrl{" +
            "id=" + id +
            ", url='" + url + "'" +
            ", sourcedId='" + sourcedId + "'" +
            '}';
    }
}
