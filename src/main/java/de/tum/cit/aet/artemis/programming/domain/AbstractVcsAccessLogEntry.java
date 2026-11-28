package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * What the access log of a repository records about one access, whichever table the entry is stored in: {@link VcsAccessLog} for the repositories of a participation and
 * {@link VcsStaffRepositoryAccessLog} for the tests and auxiliary repositories of an exercise. The log is read, and its entries are joined to operations, the same way for both.
 */
@MappedSuperclass
public abstract class AbstractVcsAccessLogEntry extends DomainObject {

    /** The person who accessed the repository, absent for a build agent, which authenticates as a build job, and for an account that was deleted afterwards. */
    @ManyToOne
    private User user;

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

    protected AbstractVcsAccessLogEntry() {
        // for Hibernate
    }

    /**
     * Creates the part of an entry that every access log has.
     *
     * @param user                    who accessed the repository
     * @param name                    the name of the user, or of the build agent
     * @param email                   the email address of the user
     * @param repositoryActionType    what was done
     * @param authenticationMechanism how the user authenticated
     * @param commitHash              the commit that belongs to the action
     * @param ipAddress               where the access came from
     * @param timestamp               when it happened
     */
    protected AbstractVcsAccessLogEntry(User user, String name, String email, RepositoryActionType repositoryActionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress, ZonedDateTime timestamp) {
        this.user = user;
        this.name = name;
        this.email = email;
        this.repositoryActionType = repositoryActionType;
        this.authenticationMechanism = authenticationMechanism;
        this.commitHash = commitHash;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public void setRepositoryActionType(RepositoryActionType repositoryActionType) {
        this.repositoryActionType = repositoryActionType;
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

    public String getIpAddress() {
        return ipAddress;
    }
}
