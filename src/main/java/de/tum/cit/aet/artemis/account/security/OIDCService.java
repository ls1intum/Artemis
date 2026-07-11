package de.tum.cit.aet.artemis.account.security;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.config.OIDCEnabled;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.Role;

@Service
@Lazy
@Conditional(OIDCEnabled.class)
public class OIDCService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(OIDCService.class);

    private final UserRepository userRepository;

    private final UserCreationService userCreationService;

    @Value("${artemis.user-management.oidc.mappings.username:preferred_username}")
    private String usernameClaimKey;

    @Value("${artemis.user-management.oidc.mappings.matriculation-number:matriculation_number}")
    private String matriculationClaimKey;

    @Value("${artemis.user-management.oidc.mappings.first-name:given_name}")
    private String firstNameClaimKey;

    @Value("${artemis.user-management.oidc.mappings.last-name:family_name}")
    private String lastNameClaimKey;

    @Value("${artemis.user-management.oidc.mappings.email:email}")
    private String emailClaimKey;

    public OIDCService(UserRepository userRepository, UserCreationService userCreationService) {
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
    }

    /**
     * Check if user with login from userRequest is present in database. New user should be stored in database
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        // Check the token
        OidcUser oidcUser = super.loadUser(userRequest);

        // Extract the TUM username (login)
        String username = oidcUser.getAttribute(usernameClaimKey);
        if (username == null || username.isBlank()) {
            log.error("OIDC Claim '{}' not found in the ID token. Cannot authenticate.", usernameClaimKey);
            throw new OAuth2AuthenticationException("Required username claim is missing from Identity Provider");
        }

        // Check if user with given username already exists
        Optional<User> localUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        User actualUser;
        if (localUser.isEmpty()) {
            try {
                // Add new user to database
                actualUser = createNewUserFromOidc(username, oidcUser);
            }
            catch (DataIntegrityViolationException e) {
                // Race condition which occurs when two users try to create same account
                // In such case the account is already created so this value will be assigned
                actualUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username)
                        .orElseThrow(() -> new OAuth2AuthenticationException("Failed to resolve concurrent OIDC user provisioning"));
            }
        }
        else {
            // Update user information and store changes if necessary
            actualUser = localUser.get();
            String firstName = oidcUser.getAttribute(firstNameClaimKey);
            String lastName = oidcUser.getAttribute(lastNameClaimKey);
            String email = oidcUser.getAttribute(emailClaimKey);
            boolean isUpdated = false;

            if (firstName != null && !firstName.isBlank() && !Objects.equals(actualUser.getFirstName(), firstName)) {
                actualUser.setFirstName(firstName);
                isUpdated = true;
            }
            if (lastName != null && !lastName.isBlank() && !Objects.equals(actualUser.getLastName(), lastName)) {
                actualUser.setLastName(lastName);
                isUpdated = true;
            }
            if (email != null && !email.isBlank() && !Objects.equals(actualUser.getEmail(), email)) {
                actualUser.setEmail(email);
                isUpdated = true;
            }
            if (isUpdated) {
                userRepository.save(actualUser);
            }
        }
        // Don't issue JWT cookie for inactive users
        if (!actualUser.getActivated()) {
            log.warn("OIDC authentication rejected: User account '{}' is deactivated in Artemis.", username);
            throw new OAuth2AuthenticationException(new OAuth2Error("user_deactivated"), "User account is deactivated.");
        }
        return oidcUser;
    }

    /**
     * Helper function to map OIDC JSON claims into Artemis User and persist it.
     */
    private User createNewUserFromOidc(String username, OidcUser oidcUser) {
        ManagedUserVM newUserDto = new ManagedUserVM();

        newUserDto.setLogin(username);
        newUserDto.setFirstName(oidcUser.getAttribute(firstNameClaimKey));
        newUserDto.setLastName(oidcUser.getAttribute(lastNameClaimKey));
        newUserDto.setEmail(oidcUser.getAttribute(emailClaimKey));
        String matriculationNumber = oidcUser.getAttribute(matriculationClaimKey);
        if (matriculationNumber != null && !matriculationNumber.isBlank()) {
            newUserDto.setVisibleRegistrationNumber(matriculationNumber);
        }

        newUserDto.setLangKey("en");
        newUserDto.setAuthorities(new HashSet<>(Set.of(Role.STUDENT.getAuthority())));
        newUserDto.setGroups(new HashSet<>());

        return userCreationService.createUser(newUserDto);
    }

}
