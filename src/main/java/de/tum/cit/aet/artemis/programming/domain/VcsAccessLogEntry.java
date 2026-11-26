package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * What the access log of a repository records about one access, whichever table the entry is stored in: {@link VcsAccessLog} for the repositories of a participation and
 * {@link VcsStaffRepositoryAccessLog} for the tests and auxiliary repositories of an exercise. The log is read, and its entries are joined to operations, the same way for both.
 */
public interface VcsAccessLogEntry {

    Long getId();

    /**
     * The person who accessed the repository.
     *
     * @return the user, null for a build agent, which authenticates as a build job, and for an account that was deleted afterwards
     */
    @Nullable
    User getUser();

    String getName();

    String getEmail();

    RepositoryActionType getRepositoryActionType();

    AuthenticationMechanism getAuthenticationMechanism();

    String getCommitHash();

    String getIpAddress();

    ZonedDateTime getTimestamp();
}
