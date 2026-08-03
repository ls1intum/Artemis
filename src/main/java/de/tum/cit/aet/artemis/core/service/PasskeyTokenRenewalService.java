package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Decides whether a passkey session may still be extended.
 * <p>
 * A passkey token is silently rotated while the session stays in use, up to the passkey token lifetime measured from the
 * original login. Nothing used to be re-checked during those rotations, so the session outlived the credential that
 * created it: deleting a passkey - including as the remediation after a compromise - did not stop the sessions it had
 * already produced from being extended for months.
 * <p>
 * The check runs only when a rotation is actually due, not on every request, which is what makes it affordable: it is one
 * indexed lookup per rotation interval per session rather than one per authenticated request.
 * <p>
 * A token issued before this check existed carries no credential id. Those are still extended, because refusing them
 * would log every passkey user out on deployment; they age out on their own once the passkey lifetime elapses.
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
     * Whether the account behind a session still allows it to be extended.
     * <p>
     * Checks the two things that should end a session but cannot reach an already-issued token: the account has since been
     * deactivated or soft-deleted, and its credentials have changed since the session started. Both are evaluated only when
     * a rotation is due, so the cost is one lookup per rotation interval per session rather than one per request. The
     * consequence is that these events end a session within at most one rotation interval rather than immediately.
     *
     * @param login    the account the session belongs to
     * @param issuedAt when the session was originally established
     * @return {@code true} if the session may be extended
     */
    public boolean mayExtendSessionForAccount(String login, Instant issuedAt) {
        var user = userRepository.findOneByLogin(login);
        if (user.isEmpty()) {
            log.info("Not extending a session: its account no longer exists");
            return false;
        }
        if (!user.get().getActivated() || user.get().isDeleted()) {
            log.info("Not extending a session of user {}: the account is deactivated or deleted", login);
            return false;
        }

        var credentialsChangedDate = user.get().getCredentialsChangedDate();
        if (credentialsChangedDate != null && issuedAt.isBefore(credentialsChangedDate.toInstant())) {
            log.info("Not extending a session of user {}: it was established before the credentials last changed", login);
            return false;
        }
        return true;
    }

    /**
     * @param credentialId the passkey the token was issued for, or {@code null} for a token issued before the claim existed
     * @return {@code true} if the session may be extended
     */
    public boolean mayExtendSession(String credentialId) {
        if (credentialId == null) {
            return true;
        }
        if (passkeyCredentialsRepository.isEmpty()) {
            // Passkeys were disabled after this token was issued, so the credential can no longer be verified.
            log.debug("Not extending a passkey session because passkey support is disabled");
            return false;
        }

        boolean passkeyStillExists = passkeyCredentialsRepository.orElseThrow().findByCredentialId(credentialId).isPresent();
        if (!passkeyStillExists) {
            log.info("Not extending a passkey session: the passkey it was issued for no longer exists");
        }
        return passkeyStillExists;
    }
}
