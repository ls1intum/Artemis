package de.tum.cit.aet.artemis.account.security;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
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
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
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

    // Optional since it's used only if LDAP profile is enbabled
    private final Optional<LdapUserService> ldapUserService;

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

    public OIDCService(UserRepository userRepository, UserCreationService userCreationService, Optional<LdapUserService> ldapUserService) {
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
        this.ldapUserService = ldapUserService;
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

        // The claim may carry an uppercase letter, and the lookup below is an exact match. Canonicalize once here so that
        // the lookup and the account createNewUserFromOidc stores use the same value User#setLogin would persist anyway.
        username = User.canonicalLogin(username);
        logReceivedClaimNames(userRequest, oidcUser);

        // Check if user with given username already exists
        Optional<User> localUser = userRepository.findOneWithAuthoritiesByLogin(username);
        User actualUser;
        if (localUser.isEmpty()) {
            try {
                // Add new user to database
                actualUser = createNewUserFromOidc(username, oidcUser);
            }
            catch (DataIntegrityViolationException e) {
                // Race condition which occurs when two users try to create same account
                // In such case the account is already created so this value will be assigned
                actualUser = userRepository.findOneWithAuthoritiesByLogin(username)
                        .orElseThrow(() -> new OAuth2AuthenticationException("Failed to resolve concurrent OIDC user provisioning"));
            }
        }
        else {
            // Update user information and store changes if necessary
            actualUser = localUser.get();
            boolean isUpdated = applyProfileClaims(actualUser, oidcUser);
            boolean isMatriculationNumberUpdated = updateMatriculationNumberIfChanged(actualUser, oidcUser.getAttribute(matriculationClaimKey));
            if (isUpdated || isMatriculationNumberUpdated) {
                actualUser = saveProfileUpdates(actualUser, oidcUser, isMatriculationNumberUpdated);
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
     * Copies the name and email claims onto an existing account. The matriculation number is handled separately, because it can collide with another account.
     *
     * @param user     the account to update, which is not saved here
     * @param oidcUser the user whose claims are applied
     * @return whether the account changed
     */
    private boolean applyProfileClaims(User user, OidcUser oidcUser) {
        String firstName = oidcUser.getAttribute(firstNameClaimKey);
        String lastName = oidcUser.getAttribute(lastNameClaimKey);
        String email = User.canonicalEmail(oidcUser.getAttribute(emailClaimKey));
        boolean isUpdated = false;

        if (firstName != null && !firstName.isBlank() && !Objects.equals(user.getFirstName(), firstName)) {
            user.setFirstName(firstName);
            isUpdated = true;
        }
        if (lastName != null && !lastName.isBlank() && !Objects.equals(user.getLastName(), lastName)) {
            user.setLastName(lastName);
            isUpdated = true;
        }
        // Deliberately keeps the stored address when the claim is absent or blank, unlike the LDAP path, which
        // clears it. A directory lookup returns the whole record, so a missing address there means the user has
        // none; a token carries only the claims that were configured and granted, so an absent one says nothing
        // about the account. Dropping an address because a token did not mention it is not recoverable.
        if (email != null && userCreationService.updateEmailIfChanged(user, email)) {
            isUpdated = true;
        }
        return isUpdated;
    }

    /**
     * Saves the claims applied to an existing account. Another account can take the matriculation number between the ownership lookup and this save, and the save then fails
     * on the unique constraint. The failed save rolled back its repository transaction, so a confirmed conflict is recovered from by reloading the account and storing the other
     * profile updates without the contested number in a fresh one. Any other failure is not recoverable here and propagates.
     *
     * @param user                         the account with the applied claims
     * @param oidcUser                     the user whose claims were applied
     * @param isMatriculationNumberUpdated whether the matriculation number is among the applied changes
     * @return the saved account
     */
    private User saveProfileUpdates(User user, OidcUser oidcUser, boolean isMatriculationNumberUpdated) {
        try {
            return userRepository.save(user);
        }
        catch (DataIntegrityViolationException e) {
            if (!isMatriculationNumberUpdated || findOtherOwnerOfMatriculationNumber(user, user.getRegistrationNumber()).isEmpty()) {
                throw e;
            }
            log.warn("OIDC matriculation number of user '{}' was not synchronized because another account claimed it in the meantime.", user.getLogin());
            User reloadedUser = userRepository.findOneWithAuthoritiesByLogin(user.getLogin())
                    .orElseThrow(() -> new OAuth2AuthenticationException("Failed to reload the account after a matriculation number conflict"));
            return applyProfileClaims(reloadedUser, oidcUser) ? userRepository.save(reloadedUser) : reloadedUser;
        }
    }

    private Optional<User> findOtherOwnerOfMatriculationNumber(User user, String matriculationNumber) {
        return userRepository.findUserWithAuthoritiesByRegistrationNumber(matriculationNumber).filter(owner -> !Objects.equals(owner.getId(), user.getId()));
    }

    /**
     * Logs which claims the identity provider delivered, to diagnose a mapping that does not resolve, for example a matriculation number that never arrives.
     * Only the claim names are logged and never their values, because the values are personal data. That includes the login, which is the value of the username claim.
     *
     * @param userRequest the request that carries the ID token
     * @param oidcUser    the user whose userinfo claims were merged in by the provider
     */
    private void logReceivedClaimNames(OidcUserRequest userRequest, OidcUser oidcUser) {
        Set<String> idTokenClaims = new TreeSet<>(userRequest.getIdToken().getClaims().keySet());
        Set<String> userInfoClaims = oidcUser.getUserInfo() == null ? Set.of() : new TreeSet<>(oidcUser.getUserInfo().getClaims().keySet());
        String matriculationNumber = oidcUser.getAttribute(matriculationClaimKey);
        boolean matriculationPresent = matriculationNumber != null && !matriculationNumber.isBlank();
        log.info("OIDC login received the claims {} in the ID token and {} in the userinfo response. The configured matriculation claim '{}' is {}.", idTokenClaims, userInfoClaims,
                matriculationClaimKey, matriculationPresent ? "present" : "missing or blank");
    }

    /**
     * Stores the matriculation number from the token when it differs from the stored one. An absent or blank claim keeps the stored value, for the same reason as the email:
     * a token carries only the claims that were configured and granted, so an absent one says nothing about the account. A number that already belongs to another account is
     * skipped instead of rejected, because the unique constraint on it would otherwise turn a profile sync into a failed login. A number that another account takes after this
     * check is caught when the account is saved, see {@link #saveProfileUpdates}.
     *
     * @param user                the account to update, which is not saved here
     * @param matriculationNumber the claim value, which may be {@code null} or blank
     * @return whether the stored matriculation number changed
     */
    private boolean updateMatriculationNumberIfChanged(User user, @Nullable String matriculationNumber) {
        if (matriculationNumber == null || matriculationNumber.isBlank() || matriculationNumber.equals(user.getRegistrationNumber())) {
            return false;
        }
        Optional<User> owner = findOtherOwnerOfMatriculationNumber(user, matriculationNumber);
        if (owner.isPresent()) {
            log.warn("OIDC matriculation number of user '{}' was not synchronized because it already belongs to user '{}'.", user.getLogin(), owner.get().getLogin());
            return false;
        }
        user.setRegistrationNumber(matriculationNumber);
        return true;
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
        if ((matriculationNumber == null || matriculationNumber.isBlank()) && ldapUserService.isPresent()) {
            try {
                LdapUserDto ldapUserInfo = ldapUserService.get().loadUserDetailsFromLdap(username);

                if (ldapUserInfo != null && ldapUserInfo.getRegistrationNumber() != null) {
                    matriculationNumber = ldapUserInfo.getRegistrationNumber();
                }
            }
            catch (Exception e) {
                log.error("Failed to query LDAP fallback during OIDC login for user: {}", username, e);
            }
        }
        if (matriculationNumber != null && !matriculationNumber.isBlank()) {
            newUserDto.setVisibleRegistrationNumber(matriculationNumber);
        }

        newUserDto.setLangKey("en");
        newUserDto.setAuthorities(new HashSet<>(Set.of(Role.STUDENT.getAuthority())));
        User createdUser = userCreationService.createUser(newUserDto);
        createdUser.setInternal(false);
        return userRepository.save(createdUser);
    }
}
