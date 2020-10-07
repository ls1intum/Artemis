package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A LtiOutcomeUrl.
 */
@Entity
@Table(name = "lti_outcome_url")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LtiOutcomeUrl extends DomainObject {

    @Column(name = "url")
    private String url;

    @Column(name = "sourced_id")
    private String sourcedId;

    @ManyToOne
    private User user;

    @ManyToOne
    private Exercise exercise;

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
    public String toString() {
        return "LtiOutcomeUrl{" + "id=" + getId() + ", url='" + url + "'" + ", sourcedId='" + sourcedId + "'" + '}';
    }
}
