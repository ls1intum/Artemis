package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Decides whether a passkey session may still be extended.
 * <p>
 * A passkey token is silently rotated while the session stays in use, up to the passkey token lifetime measured from the
 * original login. Nothing used to be re-checked during those rotations, so the session outlived the credential that
 * created it: deleting a passkey - including as the remediation after a compromise - did not stop the sessions it had
 * already produced from being extended, and neither did deactivating or deleting the account.
 * <p>
 * The check runs only when a rotation is actually due, not on every request, which is what makes it affordable: it is
 * one lookup per rotation interval per session rather than one per authenticated request. The consequence is that these
 * events end a session within at most one rotation interval rather than immediately.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PasskeyTokenRenewalService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyTokenRenewalService.class);

    /**
     * Empty when passkeys are disabled: the repository is {@code @Conditional(PasskeyEnabled.class)}.
     */
    private final Optional<PasskeyCredentialsRepository> passkeyCredentialsRepository;

    private final UserRepository userRepository;

    public PasskeyTokenRenewalService(Optional<PasskeyCredentialsRepository> passkeyCredentialsRepository, UserRepository userRepository) {
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
        this.userRepository = userRepository;
    }

    /**
     * Whether the passkey session of the given account may still be extended.
     * <p>
     * Checks what should end a session but cannot reach an already-issued token: the account has since been deactivated,
     * soft-deleted or removed, or every passkey it could have been established with is gone.
     * <p>
     * The passkey check is per account rather than per credential, because a token issued by this version carries no
     * credential id. An account that still holds another passkey therefore keeps its sessions when one of several
     * passkeys is deleted; the account-wide cases and the single-passkey case, which is the common one, are covered.
     *
     * @param login the account the session belongs to
     * @return {@code true} if the session may be extended
     */
    public boolean mayExtendPasskeySession(String login) {
        if (passkeyCredentialsRepository.isEmpty()) {
            // Passkeys were disabled after this token was issued, so no passkey session can be verified any more.
            log.debug("Not extending a passkey session because passkey support is disabled");
            return false;
        }

        Optional<User> user = userRepository.findOneByLogin(login);
        if (user.isEmpty()) {
            log.info("Not extending a passkey session: its account no longer exists");
            return false;
        }
        if (!user.get().getActivated() || user.get().isDeleted()) {
            log.info("Not extending the passkey session of user {}: the account is deactivated or deleted", login);
            return false;
        }

        boolean passkeyStillExists = passkeyCredentialsRepository.orElseThrow().existsByUserId(user.get().getId());
        if (!passkeyStillExists) {
            log.info("Not extending the passkey session of user {}: the account has no passkey any more", login);
        }
        return passkeyStillExists;
    }
}
