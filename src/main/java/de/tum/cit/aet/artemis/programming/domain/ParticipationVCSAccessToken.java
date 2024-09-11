package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * A ParticipationVcsAccessToken.
 */
@Entity
@Table(name = "participation_vcs_access_token", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "participation_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

public class ParticipationVCSAccessToken extends DomainObject {

    @ManyToOne
    private User user;

    @ManyToOne
    private Participation participation;

    @Column(name = "vcs_access_token", length = 50)
    private String vcsAccessToken;

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public Participation getParticipation() {
        return participation;
    }

    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    public void setVcsAccessToken(String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }
}
