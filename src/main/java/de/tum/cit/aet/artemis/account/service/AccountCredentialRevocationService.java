package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.localvc.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;
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
 * <b>Scope.</b> This service removes credentials that are stored on the account, so that a credential which existed
 * before the transition cannot be used again afterwards. It does not touch the authentication path.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AccountCredentialRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AccountCredentialRevocationService.class);

    private final UserRepository userRepository;

    /**
     * Deliberately the unconditional cleanup repository rather than {@code Optional<PasskeyCredentialsRepository>}:
     * revocation must delete passkeys even while the feature is disabled, because rows enrolled beforehand survive and
     * become usable again on re-enable.
     */
    private final PasskeyCredentialCleanupRepository passkeyCredentialCleanupRepository;

    private final UserSshPublicKeyService userSshPublicKeyService;

    private final ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    public AccountCredentialRevocationService(UserRepository userRepository, PasskeyCredentialCleanupRepository passkeyCredentialCleanupRepository,
            UserSshPublicKeyService userSshPublicKeyService, ParticipationVcsAccessTokenService participationVcsAccessTokenService,
            RepositoryVcsAccessTokenService repositoryVcsAccessTokenService, UserVcsAccessTokenService userVcsAccessTokenService) {
        this.userRepository = userRepository;
        this.passkeyCredentialCleanupRepository = passkeyCredentialCleanupRepository;
        this.userSshPublicKeyService = userSshPublicKeyService;
        this.participationVcsAccessTokenService = participationVcsAccessTokenService;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
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
        revokeSelectedCredentials(user, new CredentialRevocationChoiceDTO(true, true, true), reason);
    }

    /**
     * Revokes the selected credential types and leaves the others in place.
     * <p>
     * Used where the user decides, which is the change-password flow: only they know whether the old password may have
     * been seen by someone else, and that is what decides whether losing their authenticators and keys is warranted.
     *
     * @param user   the account whose credentials are revoked
     * @param choice which credential types to revoke; revoking nothing is a valid choice
     * @param reason short description of the triggering transition, for the log
     */
    public void revokeSelectedCredentials(User user, CredentialRevocationChoiceDTO choice, String reason) {
        if (!choice.revokesAnything()) {
            return;
        }

        int passkeysDeleted = choice.passkeys() ? revokePasskeys(user) : 0;
        if (choice.sshKeys()) {
            userSshPublicKeyService.deleteAllByUserId(user.getId());
        }
        if (choice.vcsAccessTokens()) {
            clearPersonalVcsAccessToken(user);
            participationVcsAccessTokenService.deleteAllByUserId(user.getId());
            repositoryVcsAccessTokenService.deleteAllByUserId(user.getId());
        }

        log.info("Revoked credentials of user {} ({}): passkeys={} ({} deleted), sshKeys={}, vcsAccessTokens={}", user.getLogin(), reason, choice.passkeys(), passkeysDeleted,
                choice.sshKeys(), choice.vcsAccessTokens());
    }

    /**
     * Clears the personal VCS access token, a password-equivalent credential: it is long-lived and enough on its own to
     * read and write the user's repositories.
     */
    private void clearPersonalVcsAccessToken(User user) {
        // Deleting the row rather than nulling columns keeps "no row" the single representation of "no token", and needs no
        // save of the user afterwards, since the token lives in its own table.
        userVcsAccessTokenService.revoke(user.getId());
    }

    /**
     * Deletes all passkeys of the user, so that an authenticator enrolled by an intruder cannot outlive the password.
     * <p>
     * Runs whether or not the passkey feature is enabled: a passkey enrolled while it was on is still in the database
     * after it is switched off, and works again as soon as it is switched back on.
     *
     * @param user the account whose passkeys are deleted
     * @return how many passkeys were deleted
     */
    private int revokePasskeys(User user) {
        return passkeyCredentialCleanupRepository.deleteAllByUserId(user.getId());
    }
}
