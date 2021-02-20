package de.tum.in.www1.artemis.service.user;

import static de.tum.in.www1.artemis.security.AuthoritiesConstants.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import io.github.jhipster.security.RandomUtil;

@Service
public class UserCreationService {

    @Value("${artemis.user-management.use-external}")
    private Boolean useExternalUserManagement;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private final Logger log = LoggerFactory.getLogger(UserCreationService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityRepository authorityRepository;

    private final CourseRepository courseRepository;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final CacheManager cacheManager;

    public UserCreationService(UserRepository userRepository, PasswordService passwordService, AuthorityRepository authorityRepository, CourseRepository courseRepository,
            Optional<VcsUserManagementService> optionalVcsUserManagementService, CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authorityRepository = authorityRepository;
        this.courseRepository = courseRepository;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.cacheManager = cacheManager;
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

        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createUser(user));
        addUserToGroupsInternal(user, userDTO.getGroups());

        log.debug("Created Information for User: {}", user);
        return user;
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
        log.debug("Save user " + user);
        return userRepository.save(user);
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
}
