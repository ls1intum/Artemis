package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.dto.LoginOptionsDTO;
import de.tum.cit.aet.artemis.account.dto.LoginOptionsDTO.LoginMethod;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Service responsible for determining the appropriate login options (such as password, OIDC, or SAML2)
 * for a user based on their identifier (login or email).
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class LoginOptionsService {

    private final UserRepository userRepository;

    private final Optional<LdapUserService> ldapUserService;

    @Value("${artemis.user-management.oidc.enabled:false}")
    private boolean oidcEnabled;

    @Value("${artemis.user-management.saml2.enabled:false}")
    private boolean samlEnabled;

    @Value("${info.oidc.buttonLabel:TUM Login}")
    private String oidcDisplayName;

    @Value("${info.saml2.buttonLabel:TUM Login}")
    private String samlDisplayName;

    public LoginOptionsService(UserRepository userRepository, Optional<LdapUserService> ldapUserService) {
        this.userRepository = userRepository;
        this.ldapUserService = ldapUserService;
    }

    /**
     * Determines which login method the user should use based on their identifier (login or email).
     *
     * @param emailOrLogin the username or email address entered by the user
     * @return the LoginOptionsDTO containing the determined login method and the display name of the provider
     */
    public LoginOptionsDTO getLoginOptions(String emailOrLogin) {
        if (emailOrLogin == null || emailOrLogin.isBlank()) {
            return new LoginOptionsDTO(LoginMethod.PASSWORD, null);
        }
        String sanitizedInput = emailOrLogin.trim().toLowerCase(Locale.ROOT);
        boolean isEmail = SecurityUtils.isEmail(sanitizedInput);
        // only project the internal flag instead of loading the whole user entity: empty means the user is unknown to Artemis
        Optional<Boolean> internalUser = isEmail ? userRepository.isInternalUserByEmailIgnoreCase(sanitizedInput) : userRepository.isInternalUserByLogin(sanitizedInput);
        // if user is already in database
        if (internalUser.isPresent()) {
            if (internalUser.get()) {
                return new LoginOptionsDTO(LoginMethod.PASSWORD, null);
            }
            else {
                return getExternalUser();
            }
        }
        if (ldapUserService.isPresent()) {
            Optional<LdapUserDto> ldapUser = isEmail ? ldapUserService.get().findByAnyEmail(sanitizedInput) : ldapUserService.get().findByLogin(sanitizedInput);
            // if user has a university account
            if (ldapUser.isPresent()) {
                return getExternalUser();
            }
            // if not: this is an internal user
            else {
                return new LoginOptionsDTO(LoginMethod.PASSWORD, null);
            }
        }
        // If user is new and ldap is disabled - provide the SSO authentication option
        if (oidcEnabled || samlEnabled) {
            return getExternalUser();
        }
        return new LoginOptionsDTO(LoginMethod.PASSWORD, null);
    }

    /**
     * Helper method to determine the active external identity provider (OIDC or SAML2) and return its details.
     *
     * @return the LoginOptionsDTO representing the active external authentication provider, or PASSWORD fallback
     */
    private LoginOptionsDTO getExternalUser() {
        if (oidcEnabled) {
            return new LoginOptionsDTO(LoginMethod.OIDC, oidcDisplayName);
        }
        if (samlEnabled) {
            return new LoginOptionsDTO(LoginMethod.SAML2, samlDisplayName);
        }
        return new LoginOptionsDTO(LoginMethod.PASSWORD, null);
    }
}
