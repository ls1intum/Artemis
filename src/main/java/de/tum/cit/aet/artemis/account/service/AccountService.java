package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.web.AccountResource;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.web.open.PublicAccountResource;

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

    public AccountService(UserRepository userRepository, UserService userService, UserCreationService userCreationService, ProfileService profileService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCreationService = userCreationService;
        this.profileService = profileService;
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
     * A password is invalid if it is empty, too short or too long.
     *
     * @param password the password to validate
     * @return whether the password is invalid or not
     */
    public boolean isPasswordLengthInvalid(String password) {
        return StringUtils.isEmpty(password) || password.length() < Constants.PASSWORD_MIN_LENGTH || password.length() > Constants.PASSWORD_MAX_LENGTH;
    }

    /**
     * Updates the basic information (name, email, language, image) of the current user's account, enforcing the account-update policy: blocked when registration is disabled for
     * external users, only the language key is updatable while SAML2 is active, and the e-mail must not already be used by a different user.
     *
     * @param userDTO the new account information of the current user
     * @throws AccessForbiddenException  if registration is disabled and the current user is not an internal user
     * @throws EmailAlreadyUsedException if the requested e-mail is already used by another user
     */
    public void updateBasicInformationOfCurrentUser(UserDTO userDTO) {
        User currentUser = userRepository.getUser();

        // Allow internal users to update their account even when registration is disabled
        if (isRegistrationDisabled() && !currentUser.isInternal()) {
            throw new AccessForbiddenException("Can't edit user information as user registration is disabled");
        }

        // When SAML2 is active, names and email are synced from the IdP — only langKey can be changed
        if (profileService.isSaml2Active()) {
            currentUser.setLangKey(userDTO.getLangKey());
            userService.saveUser(currentUser);
            return;
        }

        final String userLogin = currentUser.getLogin();
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }

        userCreationService.updateBasicInformationOfCurrentUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(), userDTO.getLangKey(), userDTO.getImageUrl());
    }

}
