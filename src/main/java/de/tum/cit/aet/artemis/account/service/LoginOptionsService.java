package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
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
            return new LoginOptionsDTO(LoginMethod.PASSWORD, null, "input is null or blank");
        }
        String sanitizedInput = emailOrLogin.trim().toLowerCase(Locale.ROOT);
        boolean isEmail = SecurityUtils.isEmail(sanitizedInput);
        Optional<User> user = isEmail ? userRepository.findOneByEmailIgnoreCase(sanitizedInput) : userRepository.findOneByLogin(sanitizedInput);
        // if user is already in database
        if (user.isPresent()) {
            if (user.get().isInternal()) {
                return new LoginOptionsDTO(LoginMethod.PASSWORD, null, "User found in Artemis DB, but isInternal=true");
            }
            else {
                return getExternalUser("User found in Artemis DB (isInternal=false)");
            }
        }
        if (ldapUserService.isPresent()) {
            Optional<LdapUserDto> ldapUser = isEmail ? ldapUserService.get().findByAnyEmail(sanitizedInput) : ldapUserService.get().findByLogin(sanitizedInput);
            // if user has a university account
            if (ldapUser.isPresent()) {
                return getExternalUser("User not in DB, but found in LDAP");
            }
        }
        return new LoginOptionsDTO(LoginMethod.PASSWORD, null, "User not in DB AND LdapUserService is not present");
    }

    /**
     * Helper method to determine the active external identity provider (OIDC or SAML2) and return its details.
     *
     * @return the LoginOptionsDTO representing the active external authentication provider, or PASSWORD fallback
     */
    private LoginOptionsDTO getExternalUser(String baseReason) {
        if (oidcEnabled) {
            return new LoginOptionsDTO(LoginMethod.OIDC, oidcDisplayName, baseReason + " OIDC is ENABLED");
        }
        if (samlEnabled) {
            return new LoginOptionsDTO(LoginMethod.SAML2, samlDisplayName, baseReason + " SAML IS ENABLED");
        }
        return new LoginOptionsDTO(LoginMethod.PASSWORD, null, baseReason + " BOTH OIDC AND SAML2 ARE ENABLED");
    }
}
