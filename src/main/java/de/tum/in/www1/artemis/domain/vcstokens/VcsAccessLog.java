package de.tum.in.www1.artemis.domain.vcstokens;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * A Vcs access log entry.
 */
@Entity
@Table(name = "vcs_access_log")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VcsAccessLog extends DomainObject {

    @ManyToOne
    private User user;

    @ManyToOne
    private Participation participation;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "repository_action_type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RepositoryActionType repositoryActionType;

    @Column(name = "authentication_mechanism", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private AuthenticationMechanism authenticationMechanism;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "timestamp")
    private ZonedDateTime timestamp;

    public VcsAccessLog(User user, Participation participation, String name, String email, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism, String commitHash, String ipAddress) {
        this.user = user;
        this.participation = participation;
        this.name = name;
        this.email = email;
        this.repositoryActionType = repositoryActionType;
        this.authenticationMechanism = authenticationMechanism;
        this.commitHash = commitHash;
        this.ipAddress = ipAddress;
        this.timestamp = ZonedDateTime.now();
    }

    public VcsAccessLog() {
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public AuthenticationMechanism getAuthenticationMechanism() {
        return authenticationMechanism;
    }

    public RepositoryActionType getRepositoryActionType() {
        return repositoryActionType;
    }
}
