package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.Authority.ADMIN_AUTHORITY;
import static de.tum.in.www1.artemis.security.AuthoritiesConstants.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.UsernameAlreadyUsedException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.PBEPasswordEncoder;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jira.JiraAuthenticationProvider;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EmailAlreadyUsedException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.errors.InvalidPasswordException;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.github.jhipster.security.RandomUtil;

/**
 * Service class for managing users.
 */
@Service
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);

    @Value("${artemis.user-management.external.admin-group-name:#{null}}")
    private Optional<String> adminGroupName;

    @Value("${artemis.user-management.use-external}")
    private Boolean useExternalUserManagement;

    @Value("${artemis.encryption-password}")
    private String encryptionPassword;

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

    private final CourseRepository courseRepository;

    private final AuthorityRepository authorityRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final GuidedTourSettingsRepository guidedTourSettingsRepository;

    private final CacheManager cacheManager;

    private final Optional<LdapUserService> ldapUserService;

    private Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private ArtemisAuthenticationProvider artemisAuthenticationProvider;

    public UserService(UserRepository userRepository, AuthorityRepository authorityRepository, CacheManager cacheManager, Optional<LdapUserService> ldapUserService,
            GuidedTourSettingsRepository guidedTourSettingsRepository, CourseRepository courseRepository, StudentScoreRepository studentScoreRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.cacheManager = cacheManager;
        this.ldapUserService = ldapUserService;
        this.guidedTourSettingsRepository = guidedTourSettingsRepository;
        this.courseRepository = courseRepository;
        this.studentScoreRepository = studentScoreRepository;
    }

    @Autowired
    // break the dependency cycle
    public void setOptionalVcsUserManagementService(Optional<VcsUserManagementService> optionalVcsUserManagementService) {
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
    }

    @Autowired
    // break the dependency cycle
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
                    existingInternalAdmin.get().setPassword(passwordEncoder().encode(artemisInternalAdminPassword.get()));
                    // needs to be mutable --> new HashSet<>(Set.of(...))
                    existingInternalAdmin.get().setAuthorities(new HashSet<>(Set.of(ADMIN_AUTHORITY, new Authority(USER))));
                    saveUser(existingInternalAdmin.get());
                    updateUserInConnectorsAndAuthProvider(existingInternalAdmin.get(), existingInternalAdmin.get().getGroups());
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

    private PBEPasswordEncoder passwordEncoder;

    private StandardPBEStringEncryptor encryptor;

    /**
     * Get the encoder for password encryption
     * @return existing password encoder or newly created password encryptor
     */
    public PBEPasswordEncoder passwordEncoder() {
        if (passwordEncoder != null) {
            return passwordEncoder;
        }
        passwordEncoder = new PBEPasswordEncoder(encryptor());
        return passwordEncoder;
    }

    /**
     * Get the the password encryptor with MD5 and DES encryption algorithm
     * @return existing encryptor or newly created encryptor
     */
    public StandardPBEStringEncryptor encryptor() {
        if (encryptor != null) {
            return encryptor;
        }
        encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword(encryptionPassword);
        return encryptor;
    }

    /**
     * Activate user registration
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
     * @param newPassword new password string
     * @param key reset key
     * @return user for whom the password was performed
     */
    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);
        return userRepository.findOneByResetKey(key).filter(user -> user.getResetDate().isAfter(Instant.now().minusSeconds(86400))).map(user -> {
            user.setPassword(passwordEncoder().encode(newPassword));
            user.setResetKey(null);
            user.setResetDate(null);
            saveUser(user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, null, null, true));
            return user;
        });
    }

    /**
     * saves the user and clears the cache
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
     * @param userDTO user data transfer object
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
        String encryptedPassword = passwordEncoder().encode(password);
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
     * Finds a single user with groups and authorities using the registration number
     *
     * @param registrationNumber user registration number as string
     * @return the user with groups and authorities
     */
    public Optional<User> findUserWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        return userRepository.findOneWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
    }

    /**
     * Finds a single user with groups and authorities using the login name
     *
     * @param login user login string
     * @return the user with groups and authorities
     */
    public Optional<User> findUserWithGroupsAndAuthoritiesByLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return Optional.empty();
        }
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login     user login string
     * @param password  user password
     * @param firstName first name of user
     * @param lastName  last name of the user
     * @param email     email of the user
     * @param registrationNumber the matriculation number of the student
     * @param imageUrl  user image url
     * @param langKey   user language
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, String firstName, String lastName, String email, String registrationNumber, String imageUrl, String langKey) {
        return createUser(login, password, new HashSet<>(), firstName, lastName, email, registrationNumber, imageUrl, langKey);
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login     user login string
     * @param groups The groups the user should belong to
     * @param firstName first name of user
     * @param lastName  last name of the user
     * @param email     email of the user
     * @param registrationNumber the matriculation number of the student
     * @param imageUrl  user image url
     * @param langKey   user language
     * @return newly created user
     */
    public User createUser(String login, Set<String> groups, String firstName, String lastName, String email, String registrationNumber, String imageUrl, String langKey) {
        return createUser(login, null, groups, firstName, lastName, email, registrationNumber, imageUrl, langKey);
    }

    /**
     * Create user only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param login     user login string
     * @param password  user password, if set to null, the password will be set randomly
     * @param groups The groups the user should belong to
     * @param firstName first name of user
     * @param lastName  last name of the user
     * @param email     email of the user
     * @param registrationNumber the matriculation number of the student*
     * @param imageUrl  user image url
     * @param langKey   user language
     * @return newly created user
     */
    public User createUser(String login, @Nullable String password, Set<String> groups, String firstName, String lastName, String email, String registrationNumber, String imageUrl,
            String langKey) {
        User newUser = new User();

        // Set random password for null passwords
        if (password == null) {
            password = RandomUtil.generatePassword();
        }
        String encryptedPassword = passwordEncoder().encode(password);
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
     *
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
        String encryptedPassword = passwordEncoder().encode(userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword());
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
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, null, null, true));
        });
    }

    /**
     * Update all information for a specific user (incl. its password), and return the modified user.
     *
     * @param user The user that should get updated
     * @param updatedUserDTO The DTO containing the to be updated values
     * @return updated user
     */
    public User updateUser(User user, ManagedUserVM updatedUserDTO) {
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
            user.setPassword(passwordEncoder().encode(updatedUserDTO.getPassword()));
        }
        Set<Authority> managedAuthorities = user.getAuthorities();
        managedAuthorities.clear();
        updatedUserDTO.getAuthorities().stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get).forEach(managedAuthorities::add);
        user = saveUser(user);

        updateUserInConnectorsAndAuthProvider(user, oldGroups);

        log.debug("Changed Information for User: {}", user);
        return user;
    }

    /**
     * Updates the user (optionally also synchronizes its password) and its groups in the connected version control system (e.g. GitLab if available).
     * Also updates the user groups in the used authentication provider (like {@link JiraAuthenticationProvider}.
     *  @param user The updated user in Artemis (this method assumes that the user including its groups was already saved to the Artemis database)
     * @param oldGroups The old groups of the user before the update
     */
    private void updateUserInConnectorsAndAuthProvider(User user, Set<String> oldGroups) {
        final var updatedGroups = user.getGroups();
        final var removedGroups = oldGroups.stream().filter(group -> !updatedGroups.contains(group)).collect(Collectors.toSet());
        final var addedGroups = updatedGroups.stream().filter(group -> !oldGroups.contains(group)).collect(Collectors.toSet());
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, removedGroups, addedGroups, true));
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
    private void deleteUser(User user) {
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

        studentScoreRepository.deleteAllByUser(user);

        userRepository.delete(user);
        clearUserCaches(user);
        userRepository.flush();
    }

    /**
     * Change password of current user
     * @param currentClearTextPassword cleartext password
     * @param newPassword new password string
     */
    public void changePassword(String currentClearTextPassword, String newPassword) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            String currentEncryptedPassword = user.getPassword();
            if (!passwordEncoder().matches(currentClearTextPassword, currentEncryptedPassword)) {
                throw new InvalidPasswordException();
            }
            String encryptedPassword = passwordEncoder().encode(newPassword);
            user.setPassword(encryptedPassword);
            saveUser(user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, null, null, true));
            log.debug("Changed password for User: {}", user);
        });
    }

    /**
     * Get decrypted password for the current user
     * @return decrypted password or empty string
     */
    public String decryptPasswordOfCurrentUser() {
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin().get()).get();
        try {
            return encryptor().decrypt(user.getPassword());
        }
        catch (Exception e) {
            return "";
        }
    }

    /**
     * Get decrypted password for given user
     * @param user the user
     * @return decrypted password or empty string
     */
    public String decryptPassword(User user) {
        return encryptor().decrypt(user.getPassword());
    }

    /**
     * Get decrypted password for given user login
     * @param login of a user
     * @return decrypted password or empty string
     */
    public Optional<String> decryptPasswordByLogin(String login) {
        return userRepository.findOneByLogin(login).map(user -> encryptor().decrypt(user.getPassword()));
    }

    /**
     * Get all managed users
     * @param userSearch used to find users
     * @return all users
     */
    public Page<UserDTO> getAllManagedUsers(PageableSearchDTO<String> userSearch) {
        final var searchTerm = userSearch.getSearchTerm();
        var sorting = Sort.by(userSearch.getSortedColumn());
        sorting = userSearch.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(userSearch.getPage(), userSearch.getPageSize(), sorting);
        return userRepository.searchByLoginOrNameWithGroups(searchTerm, sorted).map(UserDTO::new);
    }

    /**
     * Search for all users by login or name
     * @param pageable Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @return all users matching search criteria
     */
    public Page<UserDTO> searchAllUsersByLoginOrName(Pageable pageable, String loginOrName) {
        Page<User> users = userRepository.searchAllByLoginOrName(pageable, loginOrName);
        users.forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        return users.map(UserDTO::new);
    }

    /**
     * Get user with groups and authorities by given login string
     * @param login user login string
     * @return existing user with given login string or null
     */
    public Optional<User> getUserWithGroupsAndAuthoritiesByLogin(String login) {
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Get user with authorities by given login string
     * @param login user login string
     * @return existing user with given login string or null
     */
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Get current user for login string
     * @param login user login string
     * @return existing user for the given login string or null
     */
    public Optional<User> getUserByLogin(String login) {
        return userRepository.findOneByLogin(login);
    }

    /**
     * @return existing user object by current user login
     */
    @NotNull
    public User getUser() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups and authorities of currently logged in user
     * @return currently logged in user
     */
    @NotNull
    public User getUserWithGroupsAndAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and guided tour settings of currently logged in user
     * Note: this method should only be invoked if the guided tour settings are really needed
     * @return currently logged in user
     */
    @NotNull
    public User getUserWithGroupsAuthoritiesAndGuidedTourSettings() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    @NotNull
    private User unwrapOptionalUser(Optional<User> optionalUser, String currentUserLogin) {
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        }
        throw new EntityNotFoundException("No user found with login: " + currentUserLogin);
    }

    private String getCurrentUserLogin() {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            return currentUserLogin.get();
        }
        throw new EntityNotFoundException("ERROR: No current user login found!");
    }

    /**
     * Get user with user groups and authorities with the username (i.e. user.getLogin() or principal.getName())
     * @param username the username of the user who should be retrieved from the database
     * @return the user that belongs to the given principal with eagerly loaded groups and authorities
     */
    public User getUserWithGroupsAndAuthorities(@NotNull String username) {
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        return unwrapOptionalUser(user, username);
    }

    /**
     * @return a list of all the authorities
     */
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).collect(Collectors.toList());
    }

    private void clearUserCaches(User user) {
        var userCache = cacheManager.getCache(User.class.getName());
        if (userCache != null) {
            userCache.evict(user.getLogin());
        }
    }

    /**
     * Update user notification read date for current user
     * @param userId the user for which the notification read date should be updated
     */
    @Transactional // ok because of modifying query
    public void updateUserNotificationReadDate(long userId) {
        userRepository.updateUserNotificationReadDate(userId, ZonedDateTime.now());
    }

    /**
     * Get students by given course
     * @param course object
     * @return list of students for given course
     */
    public List<User> getStudents(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     * @param course object
     * @return list of tutors for given course
     */
    public List<User> getTutors(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getTeachingAssistantGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return A list of all users that have the role of instructor in the course
     */
    public List<User> getInstructors(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getInstructorGroupName());
    }

    /**
     * Get all users in a given group
     *
     * @param groupName The group name for which to return all members
     * @return A list of all users that belong to the group
     */
    public List<User> findAllUsersInGroupWithAuthorities(String groupName) {
        return userRepository.findAllInGroupWithAuthorities(groupName);
    }

    /**
     * Get all users in a given team
     *
     * @param course The course to which the team belongs (acts as a scope for the team short name)
     * @param teamShortName The short name of the team for which to get all students
     * @return A set of all users that belong to the team
     */
    public Set<User> findAllUsersInTeam(Course course, String teamShortName) {
        return userRepository.findAllInTeam(course.getId(), teamShortName);
    }

    /**
     * Update the guided tour settings of the currently logged in user
     * @param guidedTourSettings the updated set of guided tour settings
     * @return the updated user object with the changed guided tour settings
     */
    public User updateGuidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
        User loggedInUser = getUserWithGroupsAuthoritiesAndGuidedTourSettings();
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
     * @param guidedTourSettingsKey the key of the guided tour setting that should be deleted
     * @return the updated user object without the deleted guided tour setting
     */
    public User deleteGuidedTourSetting(String guidedTourSettingsKey) {
        User loggedInUser = getUserWithGroupsAuthoritiesAndGuidedTourSettings();
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
     * Finds all users that are part of the specified group, but are not contained in the collection of excluded users
     *
     * @param groupName The group by which all users should get filtered
     * @param excludedUsers The users that should get ignored/excluded
     * @return A list of filtered users
     */
    public List<User> findAllUserInGroupAndNotIn(String groupName, Collection<User> excludedUsers) {
        // For an empty list, we have to use another query, because Hibernate builds an invalid query with empty lists
        if (!excludedUsers.isEmpty()) {
            return userRepository.findAllInGroupContainingAndNotIn(groupName, new HashSet<>(excludedUsers));
        }
        return userRepository.findAllInGroupWithAuthorities(groupName);
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

    public Long countUserInGroup(String groupName) {
        return userRepository.countByGroupsIsContaining(groupName);
    }

    /**
     * add the user to the specified group and update in VCS (like GitLab) if used
     * @param user the user
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
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, Set.of(), Set.of(group), false)); // e.g. Gitlab
    }

    /**
     * adds the user to the group only in the Artemis database
     *
     * @param user the user
     * @param group the group
     */
    private void addUserToGroupInternal(User user, String group) {
        log.debug("Add user " + user.getLogin() + " to group " + group);
        if (!user.getGroups().contains(group)) {
            user.getGroups().add(group);
            user.setAuthorities(buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     * Adds a user to the specified set of groups.
     *
     * @param user the user who should be added to the given groups
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
     * @param user the user
     * @param group the group
     */
    public void removeUserFromGroup(User user, String group) {
        removeUserFromGroupInternal(user, group); // internal Artemis database
        artemisAuthenticationProvider.removeUserFromGroup(user, group); // e.g. JIRA
        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateUser(user, Set.of(group), Set.of(), false)); // e.g. Gitlab
    }

    /**
     * remove the user from the specified group and update in VCS (like GitLab) if used
     *
     * @param user the user
     * @param group the group
     */
    private void removeUserFromGroupInternal(User user, String group) {
        log.info("Remove user " + user.getLogin() + " from group " + group);
        if (user.getGroups().contains(group)) {
            user.getGroups().remove(group);
            user.setAuthorities(buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     *
     * Builds the authorities list from the groups:
     *
     * 1) Admin group if the globally defined ADMIN_GROUP_NAME is available and is contained in the users groups, or if the user was an admin before
     * 2) group contains configured instructor group name -> instructor role
     * 3) group contains configured tutor group name -> tutor role
     * 4) the user role is always given
     *
     * @param user a user with groups
     * @return a set of authorities based on the course configuration and the given groups
     */
    public Set<Authority> buildAuthorities(User user) {
        Set<Authority> authorities = new HashSet<>();
        Set<String> groups = user.getGroups();
        if (groups == null) {
            // prevent null pointer exceptions
            groups = new HashSet<>();
        }

        // Check if the user is admin in case the admin group is defined
        if (adminGroupName.isPresent() && groups.contains(adminGroupName.get())) {
            authorities.add(ADMIN_AUTHORITY);
        }

        // Users who already have admin access, keep admin access.
        if (user.getAuthorities() != null && user.getAuthorities().contains(ADMIN_AUTHORITY)) {
            authorities.add(ADMIN_AUTHORITY);
        }

        Set<String> instructorGroups = courseRepository.findAllInstructorGroupNames();
        Set<String> teachingAssistantGroups = courseRepository.findAllTeachingAssistantGroupNames();

        // Check if user is an instructor in any course
        if (groups.stream().anyMatch(instructorGroups::contains)) {
            authorities.add(new Authority(INSTRUCTOR));
        }

        // Check if user is a tutor in any course
        if (groups.stream().anyMatch(teachingAssistantGroups::contains)) {
            authorities.add(new Authority(TEACHING_ASSISTANT));
        }

        authorities.add(new Authority(USER));
        return authorities;
    }
}
