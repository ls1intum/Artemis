package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.web.rest.AccountResource;
import de.tum.cit.aet.artemis.web.rest.open.PublicAccountResource;

/**
 * Service class for {@link AccountResource} and {@link PublicAccountResource}.
 */
@Profile(PROFILE_CORE)
@Service
public class AccountService {

    @Value("${artemis.user-management.registration.enabled:#{null}}")
    private Optional<Boolean> registrationEnabled;

    /**
     * the registration is only enabled when the configuration artemis.user-management.registration.enabled is set to true.
     * A non-existing entry or false mean that the registration is not enabled
     *
     * @return whether the registration is enabled or not
     */
    public boolean isRegistrationDisabled() {
        return registrationEnabled.isEmpty() || Boolean.FALSE.equals(registrationEnabled.get());
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

}
