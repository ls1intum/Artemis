package de.tum.in.www1.artemis.service.user;

import static de.tum.in.www1.artemis.domain.Authority.ADMIN_AUTHORITY;
import static de.tum.in.www1.artemis.security.AuthoritiesConstants.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.UsernameAlreadyUsedException;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.GuidedTourSettingsRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jira.JiraAuthenticationProvider;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.web.rest.errors.EmailAlreadyUsedException;
import de.tum.in.www1.artemis.web.rest.errors.InvalidPasswordException;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.github.jhipster.security.RandomUtil;

/**
 * Service class for managing users.
 */
@Service
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);

    @Value("${artemis.user-management.use-external}")
    private Boolean useExternalUserManagement;

    @Value("${artemis.user-management.internal-admin.username:#{null}}")
    private Optional<String> artemisInternalAdminUsername;

    @Value("${artemis.user-management.internal-admin.password:#{null}}")
    private Optional<String> artemisInternalAdminPassword;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityService authorityService;

    private final Optional<LdapUserService> ldapUserService;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final CacheManager cacheManager;

    private final CourseRepository courseRepository;

    private final AuthorityRepository authorityRepository;

    private final GuidedTourSettingsRepository guidedTourSettingsRepository;

    public UserService(UserRepository userRepository, AuthorityService authorityService, AuthorityRepository authorityRepository, CacheManager cacheManager,
            Optional<LdapUserService> ldapUserService, GuidedTourSettingsRepository guidedTourSettingsRepository, CourseRepository courseRepository,
            PasswordService passwordService, Optional<VcsUserManagementService> optionalVcsUserManagementService) {
        this.userRepository = userRepository;
        this.authorityService = authorityService;
        this.authorityRepository = authorityRepository;
        this.cacheManager = cacheManager;
        this.ldapUserService = ldapUserService;
        this.guidedTourSettingsRepository = guidedTourSettingsRepository;
        this.courseRepository = courseRepository;
        this.passwordService = passwordService;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
    }

    @Autowired // break the dependency cycle
    public void setArtemisAuthenticationProvider(ArtemisAuthenticationProvider artemisAuthenticationProvider) {
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
    }

    /**
     * find all users who do not have registration numbers: in case they are TUM users, try to retrieve their registration number and set a proper first name and last name
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {

        try {
            if (artemisInternalAdminUsername.isPresent() && artemisInternalAdminPassword.isPresent()) {
                // authenticate so that db queries are possible
                SecurityUtils.setAuthorizationObject();
                Optional<User> existingInternalAdmin = userRepository.findOneWithGroupsAndAuthoritiesByLogin(artemisInternalAdminUsername.get());
                if (existingInternalAdmin.isPresent()) {
                    log.info("Update internal admin user " + artemisInternalAdminUsername.get());
                    existingInternalAdmin.get().setPassword(passwordService.encodePassword(artemisInternalAdminPassword.get()));
                    // needs to be mutable --> new HashSet<>(Set.of(...))
                    existingInternalAdmin.get().setAuthorities(new HashSet<>(Set.of(ADMIN_AUTHORITY, new Authority(USER))));
                    saveUser(existingInternalAdmin.get());
                    updateUserInConnectorsAndAuthProvider(existingInternalAdmin.get(), existingInternalAdmin.get().getLogin(), existingInternalAdmin.get().getGroups());
                }
                else {
                    log.info("Create internal admin user " + artemisInternalAdminUsername.get());
                    ManagedUserVM userDto = new ManagedUserVM();
                    userDto.setLogin(artemisInternalAdminUsername.get());
                    userDto.setPassword(artemisInternalAdminPassword.get());
                    userDto.setActivated(true);
                    userDto.setFirstName("Administrator");
                    userDto.setLastName("Administrator");
                    userDto.setEmail("admin@localhost");
                    userDto.setLangKey("en");
                    userDto.setCreatedBy("system");
                    userDto.setLastModifiedBy("system");
                    // needs to be mutable --> new HashSet<>(Set.of(...))
                    userDto.setAuthorities(new HashSet<>(Set.of(ADMIN, USER)));
                    userDto.setGroups(new HashSet<>());
                    createUser(userDto);
                }
            }
        }
        catch (Exception ex) {
            log.error("An error occurred after application startup when creating or updating the admin user or in the LDAP search: " + ex.getMessage(), ex);
        }
    }

    /**
     * load additional user details from the ldap if it is available: correct firstname, correct lastname and registration number (= matriculation number)
     *
     * @param login the login of the user for which the details should be retrieved
     * @return the found Ldap user details or null if the user cannot be found
     */
    @Nullable
    public LdapUserDto loadUserDetailsFromLdap(@NotNull String login) {
        try {
            Optional<LdapUserDto> ldapUserOptional = ldapUserService.get().findByUsername(login);
            if (ldapUserOptional.isPresent()) {
                LdapUserDto ldapUser = ldapUserOptional.get();
                log.info("Ldap User " + ldapUser.getUsername() + " has registration number: " + ldapUser.getRegistrationNumber());
                return ldapUserOptional.get();
            }
            else {
                log.warn("Ldap User " + login + " not found");
            }
        }
        catch (Exception ex) {
            log.error("Error in LDAP Search " + ex.getMessage());
        }
        return null;
    }

    /**
     * Activate user registration
     *
     * @param key activation key for user registration
     * @return user if user exists otherwise null
     */
    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return userRepository.findOneByActivationKey(key).map(user -> {
            // activate given user for the registration key.
            activateUser(user);
            return user;
        });
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
     * Reset user password for given reset key
     *
     * @param newPassword new password string
     * @param key         reset key
     * @return user for whom the password was performed
     */
    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);
        return userRepository.findOneByResetKey(key).filter(user -> user.getResetDate().isAfter(Instant.now().minusSeconds(86400))).map(user -> {
            user.setPassword(passwordService.encodePassword(newPassword));
            user.setResetKey(null);
            user.setResetDate(null);
            saveUser(user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user.getLogin(), user, null, null, true));
            return user;
        });
    }

    /**
     * saves the user and clears the cache
     *
     * @param user the user object that will be saved into the database
     * @return the saved and potentially updated user object
     */
    public User saveUser(User user) {
        clearUserCaches(user);
        log.debug("Save user " + user);
        return userRepository.save(user);
    }

    /**
     * Request password reset for user email
     *
     * @param mail to find user
     * @return user if user exists otherwise null
     */
    public Optional<User> requestPasswordReset(String mail) {
        return userRepository.findOneByEmailIgnoreCase(mail).filter(User::getActivated).map(user -> {
            user.setResetKey(RandomUtil.generateResetKey());
            user.setResetDate(Instant.now());
            return saveUser(user);
        });
    }

    /**
     * Register user and create it only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param userDTO  user data transfer object
     * @param password string
     * @return newly registered user or throw registration exception
     */
    public User registerUser(UserDTO userDTO, String password) {
        userRepository.findOneByLogin(userDTO.getLogin().toLowerCase()).ifPresent(existingUser -> {
            boolean removed = removeNonActivatedUser(existingUser);
            if (!removed) {
                throw new UsernameAlreadyUsedException();
            }
        });
        userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).ifPresent(existingUser -> {
            boolean removed = removeNonActivatedUser(existingUser);
            if (!removed) {
                throw new EmailAlreadyUsedException();
            }
        });
        User newUser = new User();
        String encryptedPassword = passwordService.encodePassword(password);
        newUser.setLogin(userDTO.getLogin().toLowerCase());
        // new user gets initially a generated password
        newUser.setPassword(encryptedPassword);
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setEmail(userDTO.getEmail().toLowerCase());
        newUser.setImageUrl(userDTO.getImageUrl());
        newUser.setLangKey(userDTO.getLangKey());
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        newUser = saveUser(newUser);
        // we need to save first so that the user can be found in the database in the subsequent method
        createUserInExternalSystems(newUser);
        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    /**
     * Remove non activated user
     *
     * @param existingUser user object of an existing user
     * @return true if removal has been executed successfully otherwise false
     */
    private boolean removeNonActivatedUser(User existingUser) {
        if (existingUser.getActivated()) {
            return false;
        }
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.deleteUser(existingUser.getLogin()));
        deleteUser(existingUser);
        return true;
    }

    /**
     * Searches the (optional) LDAP service for a user with the give registration number (= Matrikelnummer) and returns a new Artemis user-
     * Also creates the user in the external user management (e.g. JIRA), in case this is activated
     * Note: this method should only be used if the user does not yet exist in the database
     *
     * @param registrationNumber the matriculation number of the student
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdap(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        if (ldapUserService.isPresent()) {
            Optional<LdapUserDto> ldapUserOptional = ldapUserService.get().findByRegistrationNumber(registrationNumber);
            if (ldapUserOptional.isPresent()) {
                LdapUserDto ldapUser = ldapUserOptional.get();
                log.info("Ldap User " + ldapUser.getUsername() + " has registration number: " + ldapUser.getRegistrationNumber());

                // handle edge case, the user already exists in Artemis, but for some reason does not have a registration number or it is wrong
                if (StringUtils.hasText(ldapUser.getUsername())) {
                    var existingUser = userRepository.findOneByLogin(ldapUser.getUsername());
                    if (existingUser.isPresent()) {
                        existingUser.get().setRegistrationNumber(ldapUser.getRegistrationNumber());
                        saveUser(existingUser.get());
                        return existingUser;
                    }
                }

                // Use empty password, so that we don't store the credentials of Jira users in the Artemis DB
                User user = createUser(ldapUser.getUsername(), "", ldapUser.getFirstName(), ldapUser.getLastName(), ldapUser.getEmail(), registrationNumber, null, "en");
                if (useExternalUserManagement) {
                    artemisAuthenticationProvider.createUserInExternalUserManagement(user);
                }
                return Optional.of(user);
            }
            else {
                log.warn("Ldap User with registration number " + registrationNumber + " not found");
            }
        }
        return Optional.empty();
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login              user login string
     * @param password           user password
     * @param firstName          first name of user
     * @param lastName           last name of the user
     * @param email              email of the user
     * @param registrationNumber the matriculation number of the student
     * @param imageUrl           user image url
     * @param langKey            user language
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, String firstName, String lastName, String email, String registrationNumber, String imageUrl, String langKey) {
        return createUser(login, password, new HashSet<>(), firstName, lastName, email, registrationNumber, imageUrl, langKey);
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login              user login string
     * @param groups             The groups the user should belong to
     * @param firstName          first name of user
     * @param lastName           last name of the user
     * @param email              email of the user
     * @param registrationNumber the matriculation number of the student
     * @param imageUrl           user image url
     * @param langKey            user language
     * @return newly created user
     */
    public User createUser(String login, Set<String> groups, String firstName, String lastName, String email, String registrationNumber, String imageUrl, String langKey) {
        return createUser(login, null, groups, firstName, lastName, email, registrationNumber, imageUrl, langKey);
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
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, Set<String> groups, String firstName, String lastName, String email, String registrationNumber, String imageUrl,
            String langKey) {
        User newUser = new User();

        // Set random password for null passwords
        if (password == null) {
            password = RandomUtil.generatePassword();
        }
        String encryptedPassword = passwordService.encodePassword(password);
        // new user gets initially a generated password
        newUser.setPassword(encryptedPassword);

        newUser.setLogin(login);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setGroups(groups);
        newUser.setEmail(email);
        newUser.setRegistrationNumber(registrationNumber);
        newUser.setImageUrl(imageUrl);
        newUser.setLangKey(langKey);
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());

        final var authority = authorityRepository.findById(USER).get();
        // needs to be mutable --> new HashSet<>(Set.of(...))
        final var authorities = new HashSet<>(Set.of(authority));
        newUser.setAuthorities(authorities);

        saveUser(newUser);
        log.debug("Created user: {}", newUser);
        return newUser;
    }

    /**
     * Create user based on UserDTO. If the user management is done internally by Artemis, also create the user in the (optional) version control system
     * In case user management is done externally, the users groups are configured in the external user management as well.
     * <p>
     * TODO: how should we handle the case, that a new user is created that does not exist in the external user management?
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
        if (userDTO.getAuthorities() != null) {
            Set<Authority> authorities = userDTO.getAuthorities().stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toSet());
            user.setAuthorities(authorities);
        }
        String encryptedPassword = passwordService.encodePassword(userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword());
        user.setPassword(encryptedPassword);
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        if (!useExternalUserManagement) {
            addTutorialGroups(userDTO); // Automatically add interactive tutorial course groups to the new created user if it has been specified
        }
        user.setGroups(userDTO.getGroups());
        user.setActivated(true);
        saveUser(user);

        createUserInExternalSystems(user);
        addUserToGroupsInternal(user, userDTO.getGroups());

        log.debug("Created Information for User: {}", user);
        return user;
    }

    /**
     * tries to create the user in the external system, in case this is available
     *
     * @param user the user, that should be created in the external system
     */
    private void createUserInExternalSystems(User user) {
        // If user management is done by Artemis, we also have to create the user in the version control system
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createUser(user));
    }

    /**
     * Update basic information (first name, last name, email, language) for the current user.
     *
     * @param firstName first name of user
     * @param lastName  last name of user
     * @param email     email id of user
     * @param langKey   language key
     * @param imageUrl  image URL of user
     */
    public void updateUser(String firstName, String lastName, String email, String langKey, String imageUrl) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email.toLowerCase());
            user.setLangKey(langKey);
            user.setImageUrl(imageUrl);
            saveUser(user);
            log.info("Changed Information for User: {}", user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user.getLogin(), user, null, null, true));
        });
    }

    /**
     * Update all information for a specific user (incl. its password), and return the modified user.
     *
     * @param user           The user that should get updated
     * @param updatedUserDTO The DTO containing the to be updated values
     * @return updated user
     */
    public User updateUser(User user, ManagedUserVM updatedUserDTO) {
        final var oldUserLogin = user.getLogin();
        final var oldGroups = user.getGroups();
        user.setLogin(updatedUserDTO.getLogin().toLowerCase());
        user.setFirstName(updatedUserDTO.getFirstName());
        user.setLastName(updatedUserDTO.getLastName());
        user.setEmail(updatedUserDTO.getEmail().toLowerCase());
        user.setImageUrl(updatedUserDTO.getImageUrl());
        user.setActivated(updatedUserDTO.isActivated());
        user.setLangKey(updatedUserDTO.getLangKey());
        user.setGroups(updatedUserDTO.getGroups());
        if (updatedUserDTO.getPassword() != null) {
            user.setPassword(passwordService.encodePassword(updatedUserDTO.getPassword()));
        }
        Set<Authority> managedAuthorities = user.getAuthorities();
        managedAuthorities.clear();
        updatedUserDTO.getAuthorities().stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get).forEach(managedAuthorities::add);
        user = saveUser(user);

        updateUserInConnectorsAndAuthProvider(user, oldUserLogin, oldGroups);

        log.debug("Changed Information for User: {}", user);
        return user;
    }

    /**
     * Updates the user (optionally also synchronizes its password) and its groups in the connected version control system (e.g. GitLab if available).
     * Also updates the user groups in the used authentication provider (like {@link JiraAuthenticationProvider}.
     *
     * @param oldUserLogin The username of the user. If the username is updated in the user object, it must be the one before the update in order to find the user in the VCS
     * @param user         The updated user in Artemis (this method assumes that the user including its groups was already saved to the Artemis database)
     * @param oldGroups    The old groups of the user before the update
     */
    private void updateUserInConnectorsAndAuthProvider(User user, String oldUserLogin, Set<String> oldGroups) {
        final var updatedGroups = user.getGroups();
        final var removedGroups = oldGroups.stream().filter(group -> !updatedGroups.contains(group)).collect(Collectors.toSet());
        final var addedGroups = updatedGroups.stream().filter(group -> !oldGroups.contains(group)).collect(Collectors.toSet());
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(oldUserLogin, user, removedGroups, addedGroups, true));
        removedGroups.forEach(group -> artemisAuthenticationProvider.removeUserFromGroup(user, group)); // e.g. Jira
        try {
            addedGroups.forEach(group -> artemisAuthenticationProvider.addUserToGroup(user, group)); // e.g. Jira
        }
        catch (ArtemisAuthenticationException e) {
            // This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore it
        }
    }

    /**
     * Delete user based on login string
     *
     * @param login user login string
     */
    @Transactional // ok because entities are deleted
    public void deleteUser(String login) {
        // Delete the user in the connected VCS if necessary (e.g. for GitLab)
        optionalVcsUserManagementService.ifPresent(userManagementService -> userManagementService.deleteUser(login));
        // Delete the user in the local Artemis database
        userRepository.findOneByLogin(login).ifPresent(user -> {
            deleteUser(user);
            log.warn("Deleted User: {}", user);
        });
    }

    @Transactional // ok because entities are deleted
    protected void deleteUser(User user) {
        // TODO: before we can delete the user, we need to make sure that all associated objects are deleted as well (or the connection to user is set to null)
        // 1) All participation connected to the user (as student)
        // 2) All notifications connected to the user
        // 3) All results connected to the user (as assessor)
        // 4) All complaints and complaints responses associated to the user
        // 5) All student exams associated to the user
        // 6) All LTIid and LTIOutcomeUrls associated to the user
        // 7) All StudentQuestion and StudentQuestionAnswer
        // 8) Remove the user from its teams
        // 9) Delete the submissionVersion / remove the user from the submissionVersion
        // 10) Delete the tutor participation
        userRepository.delete(user);
        clearUserCaches(user);
        userRepository.flush();
    }

    /**
     * Change password of current user
     *
     * @param currentClearTextPassword cleartext password
     * @param newPassword              new password string
     */
    public void changePassword(String currentClearTextPassword, String newPassword) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            String currentEncryptedPassword = user.getPassword();
            if (!passwordService.checkPasswordMatch(currentClearTextPassword, currentEncryptedPassword)) {
                throw new InvalidPasswordException();
            }
            String encryptedPassword = passwordService.encodePassword(newPassword);
            user.setPassword(encryptedPassword);
            saveUser(user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user.getLogin(), user, null, null, true));
            log.debug("Changed password for User: {}", user);
        });
    }

    private void clearUserCaches(User user) {
        var userCache = cacheManager.getCache(User.class.getName());
        if (userCache != null) {
            userCache.evict(user.getLogin());
        }
    }

    /**
     * Update the guided tour settings of the currently logged in user
     *
     * @param guidedTourSettings the updated set of guided tour settings
     * @return the updated user object with the changed guided tour settings
     */
    public User updateGuidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
        User loggedInUser = userRepository.getUserWithGroupsAuthoritiesAndGuidedTourSettings();
        loggedInUser.getGuidedTourSettings().clear();
        for (GuidedTourSetting setting : guidedTourSettings) {
            loggedInUser.addGuidedTourSetting(setting);
            guidedTourSettingsRepository.save(setting);
        }
        // TODO: do we really need to save the user here, or is it enough if we save in the guidedTourSettingsRepository?
        return saveUser(loggedInUser);
    }

    /**
     * Delete a given guided tour setting of the currently logged in user (e.g. when the user restarts a guided tutorial)
     *
     * @param guidedTourSettingsKey the key of the guided tour setting that should be deleted
     * @return the updated user object without the deleted guided tour setting
     */
    public User deleteGuidedTourSetting(String guidedTourSettingsKey) {
        User loggedInUser = userRepository.getUserWithGroupsAuthoritiesAndGuidedTourSettings();
        Set<GuidedTourSetting> guidedTourSettings = loggedInUser.getGuidedTourSettings();
        for (GuidedTourSetting setting : guidedTourSettings) {
            if (setting.getGuidedTourKey().equals(guidedTourSettingsKey)) {
                loggedInUser.removeGuidedTourSetting(setting);
                break;
            }
        }
        return saveUser(loggedInUser);
    }

    /**
     * removes the passed group from all users in the Artemis database, e.g. when the group was deleted
     *
     * @param groupName the group that should be removed from all existing users
     */
    public void removeGroupFromUsers(String groupName) {
        log.info("Remove group " + groupName + " from users");
        List<User> users = userRepository.findAllInGroupWithAuthorities(groupName);
        log.info("Found " + users.size() + " users with group " + groupName);
        for (User user : users) {
            user.getGroups().remove(groupName);
            saveUser(user);
        }
    }

    /**
     * add the user to the specified group and update in VCS (like GitLab) if used
     *
     * @param user  the user
     * @param group the group
     */
    public void addUserToGroup(User user, String group) {
        addUserToGroupInternal(user, group); // internal Artemis database
        try {
            artemisAuthenticationProvider.addUserToGroup(user, group);  // e.g. JIRA
        }
        catch (ArtemisAuthenticationException e) {
            // This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore it
        }
        // e.g. Gitlab
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user.getLogin(), user, Set.of(), Set.of(group), false));
    }

    /**
     * adds the user to the group only in the Artemis database
     *
     * @param user  the user
     * @param group the group
     */
    private void addUserToGroupInternal(User user, String group) {
        log.debug("Add user " + user.getLogin() + " to group " + group);
        if (!user.getGroups().contains(group)) {
            user.getGroups().add(group);
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
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

    /**
     * Adds the tutorial course groups to a user basing on its authorities,
     * if a course with the given group names exist
     * The groups can be customized in application-dev.yml or application-prod.yml
     * at {info.tutorial-course-groups}
     *
     * @param user the userDTO to add to the groups to
     */
    private void addTutorialGroups(ManagedUserVM user) {
        if (tutorialGroupInstructors.isPresent() || tutorialGroupTutors.isPresent() || tutorialGroupStudents.isPresent()) {
            Set<String> groupsToAdd = new HashSet<>();
            if (tutorialGroupStudents.isPresent() && courseRepository.findCourseByStudentGroupName(tutorialGroupStudents.get()) != null) {
                groupsToAdd.add(tutorialGroupStudents.get());
            }
            if (tutorialGroupTutors.isPresent() && user.getAuthorities().contains(TEACHING_ASSISTANT)
                    && courseRepository.findCourseByTeachingAssistantGroupName(tutorialGroupTutors.get()) != null) {
                groupsToAdd.add(tutorialGroupTutors.get());
            }
            if (tutorialGroupInstructors.isPresent() && user.getAuthorities().contains(INSTRUCTOR)
                    && courseRepository.findCourseByInstructorGroupName(tutorialGroupInstructors.get()) != null) {
                groupsToAdd.add(tutorialGroupInstructors.get());
            }
            if (user.getGroups() != null) {
                groupsToAdd.addAll(user.getGroups());
            }
            user.setGroups(groupsToAdd);
        }
    }

    /**
     * remove the user from the specified group only in the Artemis database
     *
     * @param user  the user
     * @param group the group
     */
    public void removeUserFromGroup(User user, String group) {
        removeUserFromGroupInternal(user, group); // internal Artemis database
        artemisAuthenticationProvider.removeUserFromGroup(user, group); // e.g. JIRA
        // e.g. Gitlab
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user.getLogin(), user, Set.of(group), Set.of(), false));
    }

    /**
     * remove the user from the specified group and update in VCS (like GitLab) if used
     *
     * @param user  the user
     * @param group the group
     */
    private void removeUserFromGroupInternal(User user, String group) {
        log.info("Remove user " + user.getLogin() + " from group " + group);
        if (user.getGroups().contains(group)) {
            user.getGroups().remove(group);
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
        }
    }
}
