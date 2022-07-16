package de.tum.in.www1.artemis.service.user;

import static de.tum.in.www1.artemis.security.Role.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import tech.jhipster.security.RandomUtil;

@Service
public class UserCreationService {

    @Value("${artemis.user-management.use-external}")
    private Boolean useExternalUserManagement;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final CourseRepository courseRepository;

    private final OrganizationRepository organizationRepository;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final Optional<CIUserManagementService> optionalCIUserManagementService;

    private final CacheManager cacheManager;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository, CourseRepository courseRepository,
            Optional<VcsUserManagementService> optionalVcsUserManagementService, Optional<CIUserManagementService> optionalCIUserManagementService, CacheManager cacheManager,
            OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.courseRepository = courseRepository;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.optionalCIUserManagementService = optionalCIUserManagementService;
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
    public User createUser(String login, @Nullable String password, @Nullable Set<String> groups, String firstName, String lastName, String email, String registrationNumber,
            String imageUrl, String langKey, boolean isInternal) {
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
        newUser.setRegistrationNumber(registrationNumber);
        newUser.setImageUrl(imageUrl);
        newUser.setLangKey(langKey);
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        newUser.setInternal(isInternal);

        final var authority = authorityRepository.findById(STUDENT.getAuthority()).get();
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
        saveUser(newUser);
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
        if (userDTO.getAuthorities() != null) {
            Set<Authority> authorities = userDTO.getAuthorities().stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toSet());
            user.setAuthorities(authorities);
        }
        String password = userDTO.getPassword() == null ? RandomUtil.generatePassword() : userDTO.getPassword();
        String passwordHash = passwordService.hashPassword(password);
        user.setPassword(passwordHash);
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        if (!useExternalUserManagement) {
            addTutorialGroups(userDTO); // Automatically add interactive tutorial course groups to the new created user if it has been specified
        }
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
        user.setRegistrationNumber(userDTO.getVisibleRegistrationNumber());
        saveUser(user);

        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createVcsUser(user, password));
        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.createUser(user, password));

        addUserToGroupsInternal(user, userDTO.getGroups());

        log.debug("Created Information for User: {}", user);
        return user;
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
            user.setImageUrl(imageUrl);
            saveUser(user);
            log.info("Changed Information for User: {}", user);
            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.updateVcsUser(user.getLogin(), user, null, null));
            optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.updateUser(user, null));
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
    @NotNull
    public User updateUser(@NotNull User user, ManagedUserVM updatedUserDTO) {
        user.setLogin(updatedUserDTO.getLogin().toLowerCase());
        user.setFirstName(updatedUserDTO.getFirstName());
        user.setLastName(updatedUserDTO.getLastName());
        user.setEmail(updatedUserDTO.getEmail().toLowerCase());
        user.setRegistrationNumber(updatedUserDTO.getVisibleRegistrationNumber());
        user.setImageUrl(updatedUserDTO.getImageUrl());
        user.setActivated(updatedUserDTO.isActivated());
        user.setLangKey(updatedUserDTO.getLangKey());
        user.setGroups(updatedUserDTO.getGroups());
        if (user.isInternal() && updatedUserDTO.getPassword() != null) {
            user.setPassword(passwordService.hashPassword(updatedUserDTO.getPassword()));
        }
        user.setOrganizations(updatedUserDTO.getOrganizations());
        Set<Authority> managedAuthorities = user.getAuthorities();
        managedAuthorities.clear();
        updatedUserDTO.getAuthorities().stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get).forEach(managedAuthorities::add);
        log.debug("Changed Information for User: {}", user);

        return saveUser(user);
    }

    /**
     * Update all information for a specific user (including its password) based on the data made available by the LDAP
     * server, and return the modified user.
     * This method is typically invoked by the admin user
     *
     * @param user the user to save
     * @param login the updated login of the user to save
     * @param firstName the updated first name of the user to save
     * @param lastName the updated last name of the user to save
     * @param email the updated email of the user to save
     * @param registrationNumber the updated registration number of the user to save
     * @return updated user
     */
    public User updateUserLdap(@NotNull User user, String login, String firstName, String lastName, String email, String registrationNumber) {
        user.setLogin(login.toLowerCase());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email.toLowerCase());
        user.setRegistrationNumber(registrationNumber);

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

        optionalCIUserManagementService.ifPresent(service -> {
            service.updateUser(user, newPassword);
        });

        optionalVcsUserManagementService.ifPresent(service -> {
            service.updateVcsUser(user.getLogin(), user, new HashSet<>(), new HashSet<>(), newPassword);
        });

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

    /**
     * Adds the tutorial course groups to a user basing on its authorities,
     * if a course with the given group names exist
     * The groups can be customized in application-dev.yml or application-prod.yml
     * at {info.tutorial-course-groups}
     *
     * @param user the userDTO to add to the groups to
     */
    private void addTutorialGroups(ManagedUserVM user) {
        if (tutorialGroupInstructors.isPresent() || tutorialGroupEditors.isPresent() || tutorialGroupTutors.isPresent() || tutorialGroupStudents.isPresent()) {
            Set<String> groupsToAdd = new HashSet<>();
            if (tutorialGroupStudents.isPresent() && courseRepository.findCourseByStudentGroupName(tutorialGroupStudents.get()) != null) {
                groupsToAdd.add(tutorialGroupStudents.get());
            }
            if (tutorialGroupTutors.isPresent() && user.getAuthorities().contains(TEACHING_ASSISTANT.getAuthority())
                    && courseRepository.findCourseByTeachingAssistantGroupName(tutorialGroupTutors.get()) != null) {
                groupsToAdd.add(tutorialGroupTutors.get());
            }
            if (tutorialGroupEditors.isPresent() && user.getAuthorities().contains(EDITOR.getAuthority())
                    && courseRepository.findCourseByEditorGroupName(tutorialGroupEditors.get()) != null) {
                groupsToAdd.add(tutorialGroupEditors.get());
            }
            if (tutorialGroupInstructors.isPresent() && user.getAuthorities().contains(INSTRUCTOR.getAuthority())
                    && courseRepository.findCourseByInstructorGroupName(tutorialGroupInstructors.get()) != null) {
                groupsToAdd.add(tutorialGroupInstructors.get());
            }
            if (user.getGroups() != null) {
                groupsToAdd.addAll(user.getGroups());
            }
            user.setGroups(groupsToAdd);
        }
    }
}
