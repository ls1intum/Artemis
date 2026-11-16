package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.api.ScienceEventApi;

/**
 * Keeps already collected science events attached to an account whose login changes.
 * <p>
 * Kept apart from {@link UserCreationService}, which is created at server startup: depending on the science API there
 * would create that API, and what it pulls in, at startup as well.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ScienceIdentityRenameService {

    private final UserRepository userRepository;

    private final Optional<ScienceEventApi> scienceEventApi;

    public ScienceIdentityRenameService(UserRepository userRepository, Optional<ScienceEventApi> scienceEventApi) {
        this.userRepository = userRepository;
        this.scienceEventApi = scienceEventApi;
    }

    /**
     * Renames the identity of already collected science events when a login changes, so the events stay attached to the
     * account they were recorded for. {@code science_event} stores the login rather than the user id, so a rename that
     * skips this leaves the old rows unreachable.
     * <p>
     * The account has been saved under its new login before this runs, and the two cannot share a transaction across the
     * modules. If the rename fails, the account therefore gets its previous login back: the events stay reachable under it,
     * and retrying the update renames them, which it could not do once the logins already matched. The rename is a single
     * statement, so it has either renamed every event or none.
     *
     * @param previousLogin the login the account had before the update
     * @param savedUser     the account as saved with its new login
     */
    public void renameScienceEventIdentityIfLoginChanged(String previousLogin, User savedUser) {
        String newLogin = savedUser.getLogin();
        if (Objects.equals(previousLogin, newLogin) || scienceEventApi.isEmpty()) {
            return;
        }
        try {
            scienceEventApi.get().renameIdentity(previousLogin, newLogin);
        }
        catch (RuntimeException e) {
            savedUser.setLogin(previousLogin);
            userRepository.save(savedUser);
            throw e;
        }
    }
}
