package de.tum.cit.aet.artemis.core.service.user;

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
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import tech.jhipster.security.RandomUtil;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserCreationService {

    private static final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final OrganizationRepository organizationRepository;

    private final CacheManager cacheManager;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository, CacheManager cacheManager,
            OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.cacheManager = cacheManager;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login              user login string
     * @param password           user password, if set to null, the password will be set randomly
     * @param groups             The groups the user should belong to
     * @param firstName          first name of user
     * @param lastName           last name of the user
     * @param email              email of the user
     * @param registrationNumber the matriculation number of the student*
     * @param imageUrl           user image url
     * @param langKey            user language
     * @param isInternal         true if the actual password gets saved in the database
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, @Nullable Set<String> groups, String firstName, String lastName, String email,
            @Nullable String registrationNumber, String imageUrl, String langKey, boolean isInternal) {
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
        // needs to be mutable --> new HashSet<>(Set.of())
        newUser.setGroups(groups != null ? new HashSet<>(groups) : new HashSet<>());
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

        String password = userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword();
        String passwordHash = passwordService.hashPassword(password);
        user.setPassword(passwordHash);
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        try {
            Set<Organization> matchingOrganizations = organizationRepository.getAllMatchingOrganizationsByUserEmail(userDTO.getEmail());
            user.setOrganizations(matchingOrganizations);
        }
        catch (InvalidDataAccessApiUsageException | PatternSyntaxException pse) {
            log.warn("Could not retrieve matching organizations from pattern: {}", pse.getMessage());
        }
        user.setGroups(userDTO.getGroups());
        user.setActivated(true);
        user.setInternal(true);
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(userDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(userDTO.getVisibleRegistrationNumber());
        }
        saveUser(user);

        addUserToGroupsInternal(user, userDTO.getGroups());

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
        // an empty string is considered as null to satisfy the unique constraint on registration number
        if (StringUtils.hasText(updatedUserDTO.getVisibleRegistrationNumber())) {
            user.setRegistrationNumber(updatedUserDTO.getVisibleRegistrationNumber());
        }
        if (updatedUserDTO.getImageUrl() != null) {
            user.setImageUrl(updatedUserDTO.getImageUrl());
        }
        user.setActivated(updatedUserDTO.isActivated());
        user.setLangKey(updatedUserDTO.getLangKey());
        user.setGroups(updatedUserDTO.getGroups());
        if (user.isInternal() && updatedUserDTO.getPassword() != null) {
            user.setPassword(passwordService.hashPassword(updatedUserDTO.getPassword()));
        }
        user.setOrganizations(updatedUserDTO.getOrganizations());
        setUserAuthorities(updatedUserDTO, user);

        log.debug("Changed Information for User: {}", user);

        return saveUser(user);
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
        log.info("Deactivated user: {}", user);
    }

    /**
     * saves the user and clears the cache
     *
     * @param user the user object that will be saved into the database
     * @return the saved and potentially updated user object
     */
    public User saveUser(User user) {
        clearUserCaches(user);
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

    // TODO: this is duplicated code, we should move it into e.g. a CacheService
    private void clearUserCaches(User user) {
        var userCache = cacheManager.getCache(User.class.getName());
        if (userCache != null) {
            userCache.evict(user.getLogin());
        }
    }

    /**
     * Adds a user to the specified set of groups.
     *
     * @param user   the user who should be added to the given groups
     * @param groups the groups in which the user should be added
     */
    private void addUserToGroupsInternal(User user, @Nullable Set<String> groups) {
        if (groups == null) {
            return;
        }
        boolean userChanged = false;
        for (String group : groups) {
            if (!user.getGroups().contains(group)) {
                userChanged = true;
                user.getGroups().add(group);
            }
        }

        if (userChanged) {
            // we only save if this is needed
            saveUser(user);
        }
    }
}
