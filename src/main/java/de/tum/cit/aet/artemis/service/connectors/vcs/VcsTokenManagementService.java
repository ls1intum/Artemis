package de.tum.cit.aet.artemis.service.connectors.vcs;

import java.time.Duration;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Provides an interface for managing VCS access tokens.
 */
public abstract class VcsTokenManagementService {

    // TODO: this should be configurable in the yml file so that server admins can change it, 365 should be the default
    protected static final Duration MAX_LIFETIME = Duration.ofDays(365);

    /**
     * Adapter for {@link #createAccessToken(User, Duration)} that uses {@link #MAX_LIFETIME} for the lifetime.
     *
     * @param user the user to create an access token for
     */
    public void createAccessToken(User user) {
        createAccessToken(user, MAX_LIFETIME);
    }

    /**
     * Generates a VCS access token for a given user with a specific lifetime, required that the user does not yet have a VCS access token.
     * This method has no effect if the VCS access token config option is disabled.
     *
     * @param user     the user to create an access token for
     * @param lifetime the lifetime of the created access token
     */
    public abstract void createAccessToken(User user, Duration lifetime);

    /**
     * Adapter for {@link #renewAccessToken(User, Duration)} that uses {@link #MAX_LIFETIME} for the lifetime.
     *
     * @param user the user whose access token is to be renewed
     */
    public void renewAccessToken(User user) {
        renewAccessToken(user, MAX_LIFETIME);
    }

    /**
     * Generates a new VCS access token for a given user with a given lifetime, required that the user already has a VCS access token, which may or may not be valid.
     * This method has no effect if the VCS access token config option is disabled.
     *
     * @param user        the user whose access token is to be renewed
     * @param newLifetime the lifetime for the newly crated access token
     */
    // TODO: we should notify the user via email that the token was changed
    public abstract void renewAccessToken(User user, Duration newLifetime);

    // Todo: See future plans in https://github.com/ls1intum/Artemis/issues/8103
    // public abstract void revokeAccessToken(User user);

}
