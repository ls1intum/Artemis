package de.tum.cit.aet.artemis.account.service;

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
import de.tum.cit.aet.artemis.localvc.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.localvc.service.sshuserkeys.UserSshPublicKeyService;

/**
 * Revokes the credentials that can be used to act as a user besides their password.
 * <p>
 * An account accumulates several credentials that outlive the password and that no lifecycle transition used to touch:
 * passkeys, the personal VCS access token, SSH keys, and the per-participation and per-repository VCS access tokens.
 * Each of them alone is enough to keep using the account, so a password reset that leaves them in place does not end an
 * intrusion, and deactivating a user does not actually cut off their repository access.
 * <p>
 * <b>Why this is a separate service rather than a few lines in each caller.</b> The set of credentials grows over time -
 * passkeys and repository tokens are both recent additions, and neither was added to the existing cleanup in
 * {@code softDeleteUser}. Keeping the list in one place means a new credential type has exactly one place to be
 * registered, and every lifecycle transition picks it up.
 * <p>
 * <b>What this deliberately does not do.</b> It does not invalidate issued JWTs: those are validated from their claims
 * alone, so revoking them would require a per-request lookup on the authentication path. The bound on an existing
 * session therefore remains its token lifetime. What this service removes is every credential that would let an
 * intruder mint <em>new</em> sessions or keep repository access after the password changed - which is what makes the
 * remediation a user is told to perform ("reset your password") actually mean something.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AccountCredentialRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AccountCredentialRevocationService.class);

    private final UserRepository userRepository;

    /**
     * Empty when passkeys are disabled: the repository is {@code @Conditional(PasskeyEnabled.class)}.
     */
    private final Optional<PasskeyCredentialsRepository> passkeyCredentialsRepository;

    private final UserSshPublicKeyService userSshPublicKeyService;

    private final ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    public AccountCredentialRevocationService(UserRepository userRepository, Optional<PasskeyCredentialsRepository> passkeyCredentialsRepository,
            UserSshPublicKeyService userSshPublicKeyService, ParticipationVcsAccessTokenService participationVcsAccessTokenService,
            RepositoryVcsAccessTokenService repositoryVcsAccessTokenService) {
        this.userRepository = userRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
        this.userSshPublicKeyService = userSshPublicKeyService;
        this.participationVcsAccessTokenService = participationVcsAccessTokenService;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
    }

    /**
     * Revokes every credential of the account except the password itself: passkeys, the personal VCS access token, SSH
     * keys, and the participation and repository VCS access tokens.
     * <p>
     * Used where the account's control is in question or is being taken away - a completed password reset, an
     * administrator deactivating the account, and a soft delete. A completed password reset counts because it is the
     * recovery flow: either the owner is remediating a compromise, in which case leaving an intruder's passkey in place
     * defeats the remediation, or an intruder has just taken the account over, in which case they already control it and
     * the surviving credentials would only extend their reach.
     *
     * @param user   the account whose credentials are revoked
     * @param reason short description of the triggering transition, for the log
     */
    public void revokeAllCredentials(User user, String reason) {
        int passkeysDeleted = revokePasskeys(user);
        revokeVcsAccessToken(user, reason);
        userSshPublicKeyService.deleteAllByUserId(user.getId());
        participationVcsAccessTokenService.deleteAllByUserId(user.getId());
        repositoryVcsAccessTokenService.deleteAllByUserId(user.getId());

        log.info("Revoked all credentials of user {} ({}): {} passkeys, the personal VCS access token, SSH keys, and participation and repository VCS access tokens",
                user.getLogin(), reason, passkeysDeleted);
    }

    /**
     * Clears the personal VCS access token, which is a password-equivalent credential: it is long-lived, and it is
     * enough on its own to read and write the user's repositories.
     * <p>
     * Used on its own where the user is demonstrably in control of the account and is rotating their password
     * deliberately. Deleting their passkeys and SSH keys there would be disproportionate - those are credentials the
     * user manages and can see, and losing them on every routine password change would be a poor trade - but leaving a
     * long-lived alternative password in place while the password is rotated defeats the point of rotating it.
     *
     * @param user   the account whose token is cleared
     * @param reason short description of the triggering transition, for the log
     */
    public void revokeVcsAccessToken(User user, String reason) {
        if (user.getVcsAccessToken() == null && user.getVcsAccessTokenExpiryDate() == null) {
            return;
        }
        user.setVcsAccessToken(null);
        user.setVcsAccessTokenExpiryDate(null);
        // Saved here rather than left to the caller: every caller happens to save the user afterwards today, but a
        // revocation that silently depends on that would be easy to defeat by a later refactor.
        userRepository.save(user);
        log.info("Cleared the personal VCS access token of user {} ({})", user.getLogin(), reason);
    }

    /**
     * Deletes all passkeys of the user, so that an authenticator enrolled by an intruder cannot outlive the password.
     *
     * @param user the account whose passkeys are deleted
     * @return how many passkeys were deleted, or 0 when passkeys are disabled
     */
    private int revokePasskeys(User user) {
        return passkeyCredentialsRepository.map(repository -> repository.deleteAllByUserId(user.getId())).orElse(0);
    }
}
