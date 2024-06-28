package de.tum.in.www1.artemis.domain.participation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
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
    @JoinColumn(name = "student_id")
    private User user;

    @ManyToOne
    private Participation participation;

    @Column(name = "vcs_access_token", length = 50)
    private String vcsAccessToken;

}
