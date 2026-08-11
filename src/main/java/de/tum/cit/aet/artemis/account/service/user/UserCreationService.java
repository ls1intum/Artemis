package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.account.service.AccountCredentialRevocationService;
import de.tum.cit.aet.artemis.account.service.AccountSecurityNotificationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserCreationService {

    private static final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final OrganizationRepository organizationRepository;

    private final AccountCredentialRevocationService accountCredentialRevocationService;

    private final AccountSecurityNotificationService accountSecurityNotificationService;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository,
            OrganizationRepository organizationRepository, AccountCredentialRevocationService accountCredentialRevocationService,
            AccountSecurityNotificationService accountSecurityNotificationService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.accountCredentialRevocationService = accountCredentialRevocationService;
        this.accountSecurityNotificationService = accountSecurityNotificationService;
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login              user login string
     * @param password           user password, if set to null, the password will be set randomly
     * @param firstName          first name of user
     * @param lastName           last name of the user
     * @param email              email of the user
     * @param registrationNumber the matriculation number of the student*
     * @param imageUrl           user image url
     * @param langKey            user language
     * @param isInternal         true if the actual password gets saved in the database
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, String firstName, String lastName, String email, @Nullable String registrationNumber, String imageUrl,
            String langKey, boolean isInternal) {
        User newUser = new User();

        if (isInternal) {
            // Set random password for null passwords
            if (password == null) {
                password = RandomUtil.generatePassword();
            }
            String passwordHash = passwordService.hashPassword(password);
            // new user gets initially a generated password
            newUser.setPassword(passwordHash);
        }

        newUser.setLogin(login);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEmail(email);
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(registrationNumber)) {
            newUser.setRegistrationNumber(registrationNumber);
        }
        newUser.setImageUrl(imageUrl);
        newUser.setLangKey(langKey);
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        newUser.setInternal(isInternal);

        final var authority = authorityRepository.findById(STUDENT.getAuthority()).orElseThrow();
        // needs to be mutable --> new HashSet<>(Set.of(...))
        final var authorities = new HashSet<>(Set.of(authority));
        newUser.setAuthorities(authorities);
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(email);
            newUser.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        newUser = saveUser(newUser);
        log.debug("Created user: {}", newUser);
        return newUser;
    }

    /**
     * Create user based on UserDTO. If the user management is done internally by Artemis, also create the user in the (optional) version control system
     * In case user management is done externally, the users groups are configured in the external user management as well.
     *
     * @param userDTO user data transfer object
     * @return newly created user
     */
    public User createUser(ManagedUserVM userDTO) {
        User user = new User();
        user.setLogin(userDTO.getLogin());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setImageUrl(userDTO.getImageUrl());
        if (userDTO.getLangKey() == null) {
            user.setLangKey(Constants.DEFAULT_LANGUAGE); // default language
        }
        else {
            user.setLangKey(userDTO.getLangKey());
        }

        setUserAuthorities(userDTO, user);

        if (userDTO.isInternal()) {
            String password = userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword();
            user.setPassword(passwordService.hashPassword(password));
        }
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(userDTO.getEmail());
            user.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        user.setActivated(true);
        user.setInternal(userDTO.isInternal());
        user.setTestUser(userDTO.isTestUser());
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(userDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(userDTO.getVisibleRegistrationNumber());
        }
        saveUser(user);

        log.debug("Created Information for User: {}", user);
        return user;
    }

    /**
     * Updates the authorities for the user according to the ones set in the DTO.
     *
     * @param userDTO The source for the authorities that should be set.
     * @param user    The target user where the authorities are set.
     */
    private void setUserAuthorities(final ManagedUserVM userDTO, final User user) {
        // A user needs to have at least some role, otherwise an authentication token can never be constructed
        if (userDTO.getAuthorities() == null || userDTO.getAuthorities().isEmpty()) {
            userDTO.setAuthorities(Set.of(STUDENT.getAuthority()));
        }

        // clear and add instead of new Set for Hibernate change tracking
        final Set<Authority> authorities = user.getAuthorities();
        authorities.clear();
        userDTO.getAuthorities().stream().map(authorityRepository::findById).flatMap(Optional::stream).forEach(authorities::add);
    }

    /**
     * Update basic information (first name, last name, email, language) for the current user.
     * This method is typically invoked by the user
     *
     * @param firstName first name of user
     * @param lastName  last name of user
     * @param email     email id of user
     * @param langKey   language key
     * @param imageUrl  image URL of user
     */
    public void updateBasicInformationOfCurrentUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email.toLowerCase());
            user.setLangKey(langKey);
            if (imageUrl != null) {
                user.setImageUrl(imageUrl);
            }
            saveUser(user);
            log.info("Changed Information for User: {}", user);
        });
    }

    /**
     * Update all information for a specific user (including its password), and return the modified user.
     * This method is typically invoked by the admin user
     *
     * @param user           The user that should get updated
     * @param updatedUserDTO The DTO containing the to be updated values
     * @return updated user
     */
    @NonNull
    public User updateUser(@NonNull User user, ManagedUserVM updatedUserDTO) {
        user.setLogin(updatedUserDTO.getLogin().toLowerCase());
        user.setFirstName(updatedUserDTO.getFirstName());
        user.setLastName(updatedUserDTO.getLastName());
        user.setEmail(updatedUserDTO.getEmail().toLowerCase());

        // allow to remove the registration: an empty string is considered as null to satisfy the unique constraint on registration number
        if (!StringUtils.hasText(updatedUserDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(null);
        }
        else {
            user.setRegistrationNumber(updatedUserDTO.getVisibleRegistrationNumber());
        }
        if (updatedUserDTO.getImageUrl() != null) {
            user.setImageUrl(updatedUserDTO.getImageUrl());
        }
        // Captured before the flag is overwritten: the admin edit form reaches the same transition as deactivateUser, and
        // it has to revoke the same credentials, otherwise an account the admin sees as deactivated keeps working over git.
        boolean isBeingDeactivated = Boolean.TRUE.equals(user.getActivated()) && !updatedUserDTO.isActivated();
        user.setActivated(updatedUserDTO.isActivated());
        user.setTestUser(updatedUserDTO.isTestUser());
        user.setLangKey(updatedUserDTO.getLangKey());
        user.setInternal(updatedUserDTO.isInternal());
        boolean revokeCredentialsAfterPasswordChange = user.isInternal() && updatedUserDTO.getPassword() != null && updatedUserDTO.isRevokeCredentials();
        if (user.isInternal() && updatedUserDTO.getPassword() != null) {
            user.setPassword(passwordService.hashPassword(updatedUserDTO.getPassword()));
        }
        user.setOrganizations(updatedUserDTO.getOrganizations());
        setUserAuthorities(updatedUserDTO, user);

        log.debug("Changed Information for User: {}", user);

        User savedUser = saveUser(user);
        boolean passwordChangedByAdministrator = user.isInternal() && updatedUserDTO.getPassword() != null;
        boolean credentialsRevoked = isBeingDeactivated || revokeCredentialsAfterPasswordChange;
        if (credentialsRevoked) {
            String reason = isBeingDeactivated ? "user deactivated by an administrator" : "password changed by an administrator";
            accountCredentialRevocationService.revokeAllCredentials(savedUser, reason);
        }
        if (passwordChangedByAdministrator) {
            // The affected user is told, not the administrator who did it: their credentials just stopped working, and only
            // this email lets them tell an administrator's action apart from an intruder's. The acting administrator is
            // recorded in the audit event instead. Deactivation alone is not announced here - the user cannot sign in to act
            // on it, and #13404 already blocks authentication for inactive accounts.
            //
            // Reported from what was actually revoked, not from the checkbox: deactivating and changing the password in one
            // update revokes everything through `isBeingDeactivated`, so keying the message off the checkbox alone told the
            // user their keys and tokens had been kept while they had in fact just been deleted.
            CredentialRevocationChoiceDTO revoked = credentialsRevoked ? new CredentialRevocationChoiceDTO(true, true, true) : CredentialRevocationChoiceDTO.none();
            accountSecurityNotificationService.passwordChanged(savedUser, revoked, AccountSecurityNotificationService.PasswordChangeActor.ADMINISTRATOR);
        }
        return savedUser;
    }

    /**
     * Activate user
     *
     * @param user the user that should be activated
     */
    public void activateUser(User user) {
        user.setActivated(true);
        user.setActivationKey(null);
        saveUser(user);
        log.info("Activated user: {}", user);
    }

    /**
     * Deactivate user
     *
     * @param user the user that should be deactivated
     */
    public void deactivateUser(User user) {
        user.setActivated(false);
        saveUser(user);
        // Web login checks `activated` on every attempt, but the git authentication paths accept a VCS access token or an
        // SSH key without consulting account state, so deactivation only takes effect once those credentials are gone.
        accountCredentialRevocationService.revokeAllCredentials(user, "user deactivated");
        log.info("Deactivated user: {}", user);
    }

    /**
     * Saves the user.
     *
     * @param user the user object that will be saved into the database
     * @return the saved and potentially updated user object
     */
    public User saveUser(User user) {
        log.debug("Save user {}", user);
        return userRepository.save(user);
    }

    /**
     * Sets for the provided user a random password and ends the initialization process.
     * Updates the password on CI and VCS systems
     *
     * @param user the user to update
     * @return the newly created password
     */
    public String setRandomPasswordAndReturn(User user) {
        String newPassword = RandomUtil.generatePassword();
        user.setPassword(passwordService.hashPassword(newPassword));
        user.setActivated(true);
        userRepository.save(user);
        return newPassword;
    }

}
