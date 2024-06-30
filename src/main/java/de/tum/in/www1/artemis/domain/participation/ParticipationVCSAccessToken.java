package de.tum.in.www1.artemis.domain.participation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation_vcs_access_token", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "participation_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
