package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BasicInformationUpdateResultDTO;
import de.tum.cit.aet.artemis.account.dto.PendingEmailChangeDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.web.AccountResource;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.SessionFreshnessService;
import de.tum.cit.aet.artemis.core.web.open.PublicAccountResource;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;

/**
 * Service class for {@link AccountResource} and {@link PublicAccountResource}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AccountService {

    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserCreationService userCreationService;

    private final ProfileService profileService;

    private final AccountSecurityEventService accountSecurityEventService;

    private final EmailChangeService emailChangeService;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final SessionFreshnessService sessionFreshnessService;

    public AccountService(UserRepository userRepository, UserService userService, UserCreationService userCreationService, ProfileService profileService,
            AccountSecurityEventService accountSecurityEventService, EmailChangeService emailChangeService, UserVcsAccessTokenService userVcsAccessTokenService,
            SessionFreshnessService sessionFreshnessService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.profileService = profileService;
        this.accountSecurityEventService = accountSecurityEventService;
        this.emailChangeService = emailChangeService;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.sessionFreshnessService = sessionFreshnessService;
    }

    /**
     * the registration is only enabled when the configuration artemis.user-management.registration.enabled is set to true.
     * A non-existing entry or false mean that the registration is not enabled
     *
     * @return whether the registration is enabled or not
     */
    public boolean isRegistrationDisabled() {
        return registrationEnabled.isEmpty() || !registrationEnabled.get();
    }

    /**
     * A password is invalid if it is empty, outside the character limits or exceeds BCrypt's UTF-8 byte limit.
     *
     * @param password the password to validate
     * @return whether the password is invalid or not
     */
    public boolean isPasswordLengthInvalid(String password) {
        return StringUtils.isEmpty(password) || password.length() < Constants.PASSWORD_MIN_LENGTH || password.length() > Constants.PASSWORD_MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > Constants.PASSWORD_MAX_BYTES;
    }

    /**
     * Updates the basic information (name, email, language, image) of the current user's account, enforcing the account-update policy: blocked when registration is disabled for
     * external users, only the language key is updatable while SAML2 is active, and the e-mail must not already be used by a different user.
     * <p>
     * A new e-mail address of an internal account is not applied here. It is only requested, and takes effect once the owners of both the current and the new address have
     * confirmed it, see {@link EmailChangeService}. The other fields are saved immediately.
     *
     * @param userDTO the new account information of the current user
     * @return the e-mail change that is now waiting for confirmation, which is empty if there is none
     * @throws AccessForbiddenException  if registration is disabled and the current user is not an internal user
     * @throws EmailAlreadyUsedException if the requested e-mail is already used by another user
     */
    public BasicInformationUpdateResultDTO updateBasicInformationOfCurrentUser(UserDTO userDTO) {
        User currentUser = userRepository.getUser();

        // Allow internal users to update their account even when registration is disabled
        if (isRegistrationDisabled() && !currentUser.isInternal()) {
            throw new AccessForbiddenException("Can't edit user information as user registration is disabled");
        }

        // When SAML2 is active, names and email are synced from the IdP — only langKey can be changed
        if (profileService.isSaml2Active()) {
            // Preserve concurrent account/security changes: this request only owns the language column.
            userService.updateUserLanguageKey(currentUser.getId(), userDTO.getLangKey());
            return new BasicInformationUpdateResultDTO(null);
        }

        final String userLogin = currentUser.getLogin();
        // Captured before the update: once the address has been replaced there is no longer any way to reach the
        // previous one, and that is where the change notice has to go.
        final String previousEmail = currentUser.getEmail();
        final String previousLangKey = currentUser.getLangKey();

        boolean emailDiffers = !Objects.equals(User.canonicalEmail(previousEmail), User.canonicalEmail(userDTO.getEmail()));
        boolean emailChangeNeedsConfirmation = emailDiffers && emailChangeService.requiresConfirmation(currentUser);
        PendingEmailChangeDTO pendingEmailChange = null;
        // The address of an account that has to confirm a change is not written by this save at all, whether or not the form carries a
        // different one. It is not compared with the address read above either: a confirmation that completes after that read has
        // changed the address, and saving the one that was read would silently undo it.
        String emailToSave = emailChangeService.requiresConfirmation(currentUser) ? null : userDTO.getEmail();
        if (emailChangeNeedsConfirmation) {
            // Asked for before anything is saved, so a refused address leaves the account as it was. What is saved below keeps the
            // address the account has: the new one is applied when both addresses have confirmed.
            pendingEmailChange = emailChangeService.requestChange(currentUser, userDTO.getEmail());
        }

        userCreationService.updateBasicInformationOfCurrentUser(userDTO.getFirstName(), userDTO.getLastName(), emailToSave, userDTO.getLangKey(), userDTO.getImageUrl());

        if (emailChangeNeedsConfirmation) {
            return new BasicInformationUpdateResultDTO(pendingEmailChange);
        }
        if (emailDiffers) {
            User updatedUser = userRepository.getUserByLoginElseThrow(userLogin);
            accountSecurityEventService.recordEmailChanged(updatedUser, previousEmail, previousLangKey);
            return new BasicInformationUpdateResultDTO(null);
        }
        // An unchanged address leaves a request that is already waiting as it is: a settings form that is saved for another field
        // sends the address back, and must not look like withdrawing it.
        return new BasicInformationUpdateResultDTO(emailChangeService.findPendingChange(currentUser.getId()).orElse(null));
    }

    /**
     * Adds the secret of the user's personal VCS access token to the account, unless the session predates the last change of the account's credentials.
     * <p>
     * An outdated session is not refused here, because the client reads the account on every page load and would otherwise be signed out of a session that may still read.
     * It only does not get the secret: it could otherwise copy a token that was created after the change and keep using it once its own session has expired.
     * The check only runs for an account that has a token.
     *
     * @param userDTO the account that is returned to the client
     * @param userId  the id of the account
     */
    public void addPersonalVcsAccessTokenIfSessionIsCurrent(UserDTO userDTO, long userId) {
        String token = userVcsAccessTokenService.findToken(userId);
        if (token != null && sessionFreshnessService.isCurrentSession(SecurityContextHolder.getContext().getAuthentication())) {
            userDTO.setVcsAccessToken(token);
            userDTO.setVcsAccessTokenExpiryDate(userVcsAccessTokenService.findExpiryDate(userId));
        }
    }

}
