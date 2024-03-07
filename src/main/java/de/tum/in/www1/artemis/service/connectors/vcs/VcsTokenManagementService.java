package de.tum.in.www1.artemis.service.connectors.vcs;

import java.time.Duration;

import de.tum.in.www1.artemis.domain.User;

public abstract class VcsTokenManagementService {

    protected static final Duration MAX_LIFETIME = Duration.ofDays(365);

    public void createAccessToken(User user) {
        createAccessToken(user, MAX_LIFETIME);
    }

    public abstract void createAccessToken(User user, Duration lifetime);

    public void renewAccessToken(User user) {
        renewAccessToken(user, MAX_LIFETIME);
    }

    public abstract void renewAccessToken(User user, Duration newLifetime);

    // Todo: See future plans in https://github.com/ls1intum/Artemis/issues/8103
    // public abstract void revokeAccessToken(User user);

}
