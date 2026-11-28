package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * A Vcs access log entry of a repository that belongs to a participation.
 */
@Entity
@Table(name = "vcs_access_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VcsAccessLog extends AbstractVcsAccessLogEntry {

    /**
     * How the name of an access by a build agent starts. The agent authenticates as a build job, not as a person, so the entry has no user and the name identifies the accessor.
     */
    public static final String BUILD_AGENT_ACCESSOR_PREFIX = "Build agent ";

    @ManyToOne
    @JoinColumn(nullable = false)
    @Parent
    private Participation participation;

    public VcsAccessLog(User user, Participation participation, String name, String email, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism, String commitHash, String ipAddress) {
        this(user, participation, name, email, repositoryActionType, authenticationMechanism, commitHash, ipAddress, ZonedDateTime.now());
    }

    /**
     * Creates an entry for an access that happened at the given time, which is earlier than the time at which the entry is created if it is written after the response of the
     * request was sent.
     */
    public VcsAccessLog(User user, Participation participation, String name, String email, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism, String commitHash, String ipAddress, ZonedDateTime timestamp) {
        super(user, name, email, repositoryActionType, authenticationMechanism, commitHash, ipAddress, timestamp);
        this.participation = participation;
    }

    public VcsAccessLog() {
    }

    public Participation getParticipation() {
        return participation;
    }
}
