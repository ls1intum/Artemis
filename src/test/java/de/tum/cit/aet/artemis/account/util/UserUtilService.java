package de.tum.cit.aet.artemis.account.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CalendarSubscriptionTokenStore;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.repository.CalendarSubscriptionTokenStoreRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;

/**
 * Service responsible for initializing the database with specific testdata related to Users for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class UserUtilService {

    private static final Logger log = LoggerFactory.getLogger(UserUtilService.class);

    private static final Authority userAuthority = new Authority(Role.STUDENT.getAuthority());

    private static final Authority tutorAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority editorAuthority = new Authority(Role.EDITOR.getAuthority());

    private static final Authority instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority adminAuthority = new Authority(Role.ADMIN.getAuthority());

    private static final Authority superAdminAuthority = new Authority(Role.SUPER_ADMIN.getAuthority());

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> editorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority);

    private static final Set<Authority> superAdminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority, superAdminAuthority);

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Autowired
    private UserAiPreferenceService userAiPreferenceService;

    @Autowired
    private CalendarSubscriptionTokenStoreRepository calendarSubscriptionTokenStoreRepository;

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    /**
     * Changes the currently authorized User to the User with the given username.
     *
     * @param username The username of the User to change to
     */
    public void changeUser(String username) {
        User user = getUserByLogin(username);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority.getName()));
        }
        org.springframework.security.core.userdetails.User securityContextUser = new org.springframework.security.core.userdetails.User(user.getLogin(), user.getPassword(),
                grantedAuthorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityContextUser, securityContextUser.getPassword(), grantedAuthorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        TestSecurityContextHolder.setContext(context);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix              The prefix that will be added in front of every User's username
     * @param authorities              The authorities that the Users will have
     * @param amount                   The amount of Users to generate
     * @param registrationNumberPrefix The prefix that will be added in front of every User's registration number
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, Set<Authority> authorities, int amount, String registrationNumberPrefix) {
        List<User> generatedUsers = generateAndSaveActivatedUsers(loginPrefix, authorities, amount);
        for (int i = 0; i < generatedUsers.size(); i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix A number will be appended to this prefix to create the login
     * @param authorities The authorities that the Users will have
     * @param amount      The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, Set<Authority> authorities, int amount) {
        return generateAndSaveActivatedUsers(loginPrefix, UserFactory.USER_PASSWORD, authorities, amount);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        A number will be appended to this prefix to create the login
     * @param commonPasswordHash The password hash that will be set for every User
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String commonPasswordHash, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            user.setAuthorities(authorities);
            user = saveWithDefaultAiPreference(user);
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Updates and saves the Users' registration numbers. The username of the updated Users is a concatenation of the testPrefix + "student" + a number counting from 1 to the size
     * of
     * the registrationNumbers list. Throws an IllegalArgumentException if the Users do not exist.
     *
     * @param registrationNumbers The registration numbers to set
     * @param testPrefix          The prefix to use for the username
     * @return A List of the updated Users
     */
    public List<User> setRegistrationNumberOfStudents(List<String> registrationNumbers, String testPrefix) {
        List<User> students = new ArrayList<>();
        for (int i = 1; i <= registrationNumbers.size(); i++) {
            students.add(setRegistrationNumberOfUserAndSave(testPrefix + "student" + i, registrationNumbers.get(i - 1)));
        }
        return students;
    }

    /**
     * Updates and saves the User's registration number.
     *
     * @param login              The username of the User to update
     * @param registrationNumber The registration number to set
     * @return The updated User
     */
    public User setRegistrationNumberOfUserAndSave(String login, String registrationNumber) {
        User user = getUserByLogin(login);
        return setRegistrationNumberOfUserAndSave(user, registrationNumber);
    }

    /**
     * Updates and saves the User's registration number.
     *
     * @param user               The User to update
     * @param registrationNumber The registration number to set
     * @return The updated User
     */
    public User setRegistrationNumberOfUserAndSave(User user, String registrationNumber) {
        user.setRegistrationNumber(registrationNumber);
        return saveWithDefaultAiPreference(user);
    }

    /**
     * Updates and saves the user's vcsAccessToken and its expiry date
     *
     * @param user           The User to update
     * @param vcsAccessToken The userVcsAccessToken to set
     * @param expiryDate     The tokens expiry date
     * @return The updated User
     */
    public User setUserVcsAccessTokenAndExpiryDateAndSave(User user, String vcsAccessToken, ZonedDateTime expiryDate) {
        // The personal token lives in user_vcs_access_token, so it is seeded through its service rather than on the user row.
        userVcsAccessTokenService.store(user.getId(), vcsAccessToken, expiryDate);
        return user;
    }

    /**
     * Deletes all tokens and saves the calendarSubscriptionToken in their calendarSubscriptionTokenStore of the user.
     *
     * @param user                      The User to update
     * @param calendarSubscriptionToken The calendarSubscriptionToken to set
     */
    public void clearAllTokensAndSetTokenForUser(User user, String calendarSubscriptionToken) {
        calendarSubscriptionTokenStoreRepository.deleteAll();
        CalendarSubscriptionTokenStore store = new CalendarSubscriptionTokenStore();
        store.setToken(calendarSubscriptionToken);
        store.setUser(user);
        calendarSubscriptionTokenStoreRepository.save(store);
    }

    /**
     * Deletes the userVcsAccessToken from a user
     *
     * @param userWithUserToken The user whose token gets deleted
     */
    public void deleteUserVcsAccessToken(User userWithUserToken) {
        userVcsAccessTokenService.revoke(userWithUserToken.getId());
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, commonPasswordHash, authorities, 1, amount);
    }

    /**
     * Creates Users with the given arguments. Creates [to - from + 1] Users (NOT yet saved — callers must save them).
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param authorities        The authorities that the Users will have
     * @param from               The first number to append to the loginPrefix
     * @param to                 The last number to append to the loginPrefix
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, Set<Authority> authorities, int from, int to) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets an existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            user.setAuthorities(authorities);
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Creates and saves a User. If a User with the given username already exists, the existing User is updated and saved.
     *
     * @param login          The username of the User
     * @param hashedPassword The password hash of the User
     * @return The created User
     */
    public User createAndSaveUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return saveWithDefaultAiPreference(user);
    }

    /**
     * Creates a User. If a User with the given username already exists, the newly created User's ID is set to the existing User's ID.
     *
     * @param login          The username of the User
     * @param hashedPassword The password hash of the User
     * @return The created User
     */
    public User createOrReuseExistingUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return user;
    }

    /**
     * Creates and saves a User. If a User with the given username already exists, the existing User is updated and saved.
     *
     * @param login The username of the User
     * @return The created User
     */
    public User createAndSaveUser(String login) {
        User user = UserFactory.generateActivatedUser(login);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return saveWithDefaultAiPreference(user);
    }

    /**
     * Creates and saves multiple Users given the amounts for each role.
     *
     * @param numberOfStudents    The number of students to create
     * @param numberOfTutors      The number of tutors to create
     * @param numberOfEditors     The number of editors to create
     * @param numberOfInstructors The number of instructors to create
     * @return The List of created Users
     */
    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        return addUsers("", numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
    }

    /**
     * Creates and saves multiple students, tutors, editors, and instructors given the corresponding numbers. It also creates and saves an admin User if it does not exist.
     * The username of the Users is a concatenation of the prefix, the role (student|tutor|editor|instructor) and a number counting from 1 to the number of Users with the
     * corresponding role. The admin User's username is "admin". This method avoids the accumulation of many Users per Course by removing existing Users before adding new ones.
     *
     * @param prefix              The prefix for the User username
     * @param numberOfStudents    The number of students to create
     * @param numberOfTutors      The number of tutors to create
     * @param numberOfEditors     The number of editors to create
     * @param numberOfInstructors The number of instructors to create
     * @return The List of created Users
     */
    public List<User> addUsers(String prefix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        if (authorityRepository.count() == 0) {
            authorityRepository.saveAll(superAdminAuthorities);
        }
        log.debug("Generate {} students...", numberOfStudents);
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(UserFactory.USER_PASSWORD), studentAuthorities, numberOfStudents);
        log.debug("{} students generated. Generate {} tutors...", numberOfStudents, numberOfTutors);
        var tutors = generateActivatedUsers(prefix + "tutor", passwordService.hashPassword(UserFactory.USER_PASSWORD), tutorAuthorities, numberOfTutors);
        log.debug("{} tutors generated. Generate {} editors...", numberOfTutors, numberOfEditors);
        var editors = generateActivatedUsers(prefix + "editor", passwordService.hashPassword(UserFactory.USER_PASSWORD), editorAuthorities, numberOfEditors);
        log.debug("{} editors generated. Generate {} instructors...", numberOfEditors, numberOfInstructors);
        var instructors = generateActivatedUsers(prefix + "instructor", passwordService.hashPassword(UserFactory.USER_PASSWORD), instructorAuthorities, numberOfInstructors);
        log.debug("{} instructors generated", numberOfInstructors);

        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(editors);
        usersToAdd.addAll(instructors);

        usersToAdd.addAll(generateMissingAdminUsers());

        // Before adding new users, remove all user_course_role entries so AuthorizationCheckService sees no
        // stale roles from a previous test. Courses created afterwards re-populate via
        // CourseUtilService.enrollPrefixedUsersInCourse().
        if (!usersToAdd.isEmpty()) {
            userCourseRoleTestRepository.deleteAllInBulk();
            log.debug("Save {} users to database...", usersToAdd.size());
            usersToAdd = new ArrayList<>(userTestRepository.saveAllOrUpdate(usersToAdd));
            usersToAdd.forEach(this::seedDefaultAiPreference);
            log.debug("Save {} users to database. Done", usersToAdd.size());
        }

        return usersToAdd;
    }

    /**
     * Builds the shared "admin" and "superadmin" accounts, skipping whichever already exists. The users are returned
     * unsaved so that {@link #addUsers} can persist them in the same batch as the rest of its users.
     *
     * @return the admin accounts that are still missing from the database
     */
    private List<User> generateMissingAdminUsers() {
        List<User> admins = new ArrayList<>();
        if (!userExistsWithLogin("admin")) {
            User admin = UserFactory.generateActivatedUser("admin", passwordService.hashPassword(UserFactory.USER_PASSWORD));
            admin.setAuthorities(adminAuthorities);
            admins.add(admin);
        }
        if (!userExistsWithLogin("superadmin")) {
            User superAdmin = UserFactory.generateActivatedUser("superadmin", passwordService.hashPassword(UserFactory.USER_PASSWORD));
            superAdmin.setAuthorities(superAdminAuthorities);
            admins.add(superAdmin);
        }
        return admins;
    }

    /**
     * Creates the shared "admin" and "superadmin" accounts if they are missing.
     * <p>
     * For test classes that authenticate as {@code admin} through {@code @WithMockUser} but create no users of their
     * own. They used to rely on some other class in the same database having called {@link #addUsers} first, which
     * only held because every bucket shared one database - it stops holding as soon as a bucket runs on its own.
     * Unlike {@link #addUsers} this touches no course roles, so it is safe to call before every test.
     */
    public void ensureAdminUsersExist() {
        if (authorityRepository.count() == 0) {
            authorityRepository.saveAll(superAdminAuthorities);
        }
        List<User> admins = generateMissingAdminUsers();
        if (!admins.isEmpty()) {
            userTestRepository.saveAllOrUpdate(admins).forEach(this::seedDefaultAiPreference);
        }
    }

    /**
     * Creates and saves Users with student authorities. Creates [to - from + 1] Users.
     *
     * @param prefix The prefix that will be added in front of every User's username
     * @param from   The first number to append to the loginPrefix
     * @param to     The last number to append to the loginPrefix
     */
    public void addStudents(String prefix, int from, int to) {
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(UserFactory.USER_PASSWORD), studentAuthorities, from, to);
        userTestRepository.saveAllOrUpdate(students).forEach(this::seedDefaultAiPreference);
    }

    /**
     * Updates and saves the User's registration number setting it to null.
     *
     * @param user The User to update
     */
    public void cleanUpRegistrationNumberForUser(User user) {
        if (user.getRegistrationNumber() == null) {
            return;
        }

        var existingUserWithRegistrationNumber = userTestRepository.findOneWithAuthoritiesByRegistrationNumber(user.getRegistrationNumber());
        if (existingUserWithRegistrationNumber.isPresent()) {
            existingUserWithRegistrationNumber.get().setRegistrationNumber(null);
            userTestRepository.save(existingUserWithRegistrationNumber.get());
        }
    }

    /**
     * Creates and saves a User with instructor authorities, if no User with the given username exists.
     * Course membership is managed via {@code user_course_role}; callers should use
     * {@link de.tum.cit.aet.artemis.core.util.CourseTestService#enrollPrefixedUsersInCourse} or
     * {@link de.tum.cit.aet.artemis.core.repository.userCourseRoleTestRepository} to enrol this user.
     *
     * @param instructorName The login of the instructor
     */
    public void addInstructor(final String instructorName) {
        User instructor = createOrReuseExistingUser(instructorName, UserFactory.USER_PASSWORD);
        instructor.setAuthorities(instructorAuthorities);
        instructor = saveWithDefaultAiPreference(instructor);
        assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
    }

    /**
     * Creates and saves a User with editor authorities, if no User with the given username exists.
     * Course membership is managed via {@code user_course_role}; callers should use
     * {@link de.tum.cit.aet.artemis.core.util.CourseTestService#enrollPrefixedUsersInCourse} or
     * {@link de.tum.cit.aet.artemis.core.repository.userCourseRoleTestRepository} to enrol this user.
     *
     * @param editorName The login of the editor
     */
    public void addEditor(final String editorName) {
        User editor = createOrReuseExistingUser(editorName, UserFactory.USER_PASSWORD);
        editor.setAuthorities(editorAuthorities);
        editor = saveWithDefaultAiPreference(editor);
        assertThat(editor.getId()).as("Editor has been created").isNotNull();
    }

    /**
     * Creates and saves a User with tutor authorities, if no User with the given username exists.
     * Course membership is managed via {@code user_course_role}; callers should use
     * {@link de.tum.cit.aet.artemis.core.util.CourseTestService#enrollPrefixedUsersInCourse} or
     * {@link de.tum.cit.aet.artemis.core.repository.userCourseRoleTestRepository} to enrol this user.
     *
     * @param taName The login of the teaching assistant
     */
    public void addTeachingAssistant(final String taName) {
        User ta = createOrReuseExistingUser(taName, UserFactory.USER_PASSWORD);
        ta.setAuthorities(tutorAuthorities);
        ta = saveWithDefaultAiPreference(ta);
        assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
    }

    /**
     * Creates and saves a User with student authorities, if no User with the given username exists.
     * Course membership is managed via {@code user_course_role}; callers should use
     * {@link de.tum.cit.aet.artemis.core.util.CourseTestService#enrollPrefixedUsersInCourse} or
     * {@link de.tum.cit.aet.artemis.core.repository.userCourseRoleTestRepository} to enrol this user.
     *
     * @param studentName The login of the student
     */
    public void addStudent(final String studentName) {
        User student = createOrReuseExistingUser(studentName, UserFactory.USER_PASSWORD);
        student.setAuthorities(studentAuthorities);
        student = saveWithDefaultAiPreference(student);
        assertThat(student.getId()).as("Student has been created").isNotNull();
    }

    /**
     * Enrolls an already-existing User in the given course with the specified role by inserting a {@code user_course_role} row.
     * This is the single canonical place for individual UCR enrollment in tests.
     *
     * @param user   the user to enroll
     * @param course the course to enroll them in
     * @param role   the role they should have in the course
     */
    public void enrollUserInCourse(final User user, final Course course, final CourseRole role) {
        userCourseRoleTestRepository.save(new UserCourseRole(user, course, role));
    }

    /**
     * Enrolls all test users matching the given login prefix in the given course. Looks up users whose login starts with
     * {@code userPrefix + "student"}, {@code userPrefix + "tutor"}, {@code userPrefix + "editor"}, and
     * {@code userPrefix + "instructor"} and creates the corresponding {@link UserCourseRole} entries.
     * <p>
     * This is the equivalent of {@code CourseUtilService.enrollPrefixedUsersInCourse} for use inside exercise util services
     * that do not have a direct reference to {@code CourseUtilService}.
     *
     * @param course     the course to enroll the users in
     * @param userPrefix the login prefix used when the test users were created via {@code addUsers(userPrefix, ...)}
     */
    public void enrollPrefixedUsersInCourse(final Course course, final String userPrefix) {
        enrollByLoginPrefix(course, userPrefix + "student", CourseRole.STUDENT);
        enrollByLoginPrefix(course, userPrefix + "tutor", CourseRole.TEACHING_ASSISTANT);
        enrollByLoginPrefix(course, userPrefix + "editor", CourseRole.EDITOR);
        enrollByLoginPrefix(course, userPrefix + "instructor", CourseRole.INSTRUCTOR);
    }

    private void enrollByLoginPrefix(final Course course, final String loginPrefix, final CourseRole role) {
        userTestRepository.findAllByUserPrefix(loginPrefix).forEach(user -> userCourseRoleTestRepository.save(new UserCourseRole(user, course, role)));
    }

    /**
     * Creates (or reuses) a User with instructor authorities and immediately enrolls them in the given course via {@code user_course_role}.
     *
     * @param login  The login of the instructor
     * @param course The course the instructor should be enrolled in
     * @return the saved User
     */
    public User addInstructorToCourse(final String login, final Course course) {
        addInstructor(login);
        User user = getUserByLogin(login);
        enrollUserInCourse(user, course, CourseRole.INSTRUCTOR);
        return getUserByLogin(login);
    }

    /**
     * Creates (or reuses) a User with editor authorities and immediately enrolls them in the given course via {@code user_course_role}.
     *
     * @param login  The login of the editor
     * @param course The course the editor should be enrolled in
     * @return the saved User with up-to-date course roles loaded
     */
    public User addEditorToCourse(final String login, final Course course) {
        addEditor(login);
        User user = getUserByLogin(login);
        enrollUserInCourse(user, course, CourseRole.EDITOR);
        return getUserByLogin(login);
    }

    /**
     * Creates (or reuses) a User with tutor authorities and immediately enrolls them in the given course via {@code user_course_role}.
     *
     * @param login  The login of the teaching assistant
     * @param course The course the teaching assistant should be enrolled in
     * @return the saved User with up-to-date course roles loaded
     */
    public User addTeachingAssistantToCourse(final String login, final Course course) {
        addTeachingAssistant(login);
        User user = getUserByLogin(login);
        enrollUserInCourse(user, course, CourseRole.TEACHING_ASSISTANT);
        return getUserByLogin(login);
    }

    /**
     * Creates (or reuses) a User with student authorities and immediately enrolls them in the given course via {@code user_course_role}.
     *
     * @param login  The login of the student
     * @param course The course the student should be enrolled in
     * @return the saved User with up-to-date course roles loaded
     */
    public User addStudentToCourse(final String login, final Course course) {
        addStudent(login);
        User user = getUserByLogin(login);
        enrollUserInCourse(user, course, CourseRole.STUDENT);
        return getUserByLogin(login);
    }

    /**
     * Creates and saves a User with super admin authorities.
     *
     * @param prefix The prefix for the super admin username
     */
    public void addSuperAdmin(final String prefix) {
        String superAdminLogin = prefix + "superadmin";
        User superAdmin = createOrReuseExistingUser(superAdminLogin, UserFactory.USER_PASSWORD);
        superAdmin.setAuthorities(superAdminAuthorities);
        superAdmin = saveWithDefaultAiPreference(superAdmin);
        assertThat(superAdmin.getId()).as("Super admin has been created").isNotNull();
    }

    /**
     * Creates a new admin user if it doesn't exist or resets and updates existing user with admin authorities.
     *
     * @param prefix The prefix for the admin username
     */
    public void addAdmin(final String prefix) {
        String adminLogin = prefix + "admin";
        User admin = createOrReuseExistingUser(adminLogin, UserFactory.USER_PASSWORD);
        admin.setAuthorities(adminAuthorities);
        admin = saveWithDefaultAiPreference(admin);
        assertThat(admin.getId()).as("Admin has been created").isNotNull();
    }

    /**
     * Grants the admin authorities to an account that already exists, so a test can authenticate as an account that is
     * also part of the data it manipulates. {@link #addAdmin(String)} always uses the {@code <prefix>admin} login and
     * cannot promote an arbitrary one.
     *
     * @param login the login of the account to promote
     */
    public void addAdminAuthorityTo(final String login) {
        User user = getUserByLoginWithoutAuthorities(login);
        user.setAuthorities(adminAuthorities);
        saveWithDefaultAiPreference(user);
    }

    /**
     * Gets a user from the database using the provided login but without the authorities.
     * <p>
     * Note: Jackson sometimes fails to deserialize the authorities leading to flaky server tests. The specific
     * circumstances when this happens in still unknown.
     *
     * @param login login to find user with
     * @return user with the provided logih
     */
    public User getUserByLoginWithoutAuthorities(String login) {
        return userTestRepository.findOneByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Gets the User with the given username from the database. Throws an IllegalArgumentException if the User does not exist.
     *
     * @param login The username of the User
     * @return The User with eagerly loaded groups and authorities
     */
    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userTestRepository.findOneWithAuthoritiesByLogin(login.toLowerCase(Locale.ENGLISH))
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Checks if a User with the given username exists.
     *
     * @param login The username of the User
     * @return True, if a User with the given login exists, false otherwise
     */
    public boolean userExistsWithLogin(String login) {
        return userTestRepository.findOneByLogin(login).isPresent();
    }

    /**
     * Removes the User with the given username from all Courses by deleting their {@code user_course_role} entries.
     *
     * @param login The login of the User
     */
    public void removeUserFromAllCourses(User user) {
        userCourseRoleTestRepository.deleteByUser_Id(user.getId());
    }

    /**
     * Removes the {@code user_course_role} entry for a specific user and course,
     * effectively unenrolling the user from it.
     *
     * @param user   the user to unenroll
     * @param course the course from which the user should be unenrolled
     */
    public void unenrollUserFromCourse(User user, Course course) {
        userCourseRoleTestRepository.deleteByUser_IdAndCourse_Id(user.getId(), course.getId());
    }

    /**
     * Removes the {@code user_course_role} entry for a specific user, course, and role.
     *
     * @param user   the user to unenroll
     * @param course the course from which the user should be unenrolled
     * @param role   the specific role to remove
     */
    public void unenrollUserFromCourseByRole(User user, Course course, CourseRole role) {
        userCourseRoleTestRepository.deleteByUser_IdAndCourse_IdAndRole(user.getId(), course.getId(), role);
    }

    /**
     * Removes all {@code user_course_role} entries for the given course,
     * effectively unenrolling all users from it.
     *
     * @param course the course whose enrollments should be cleared
     */
    public void removeAllCourseEnrollments(Course course) {
        userCourseRoleTestRepository.deleteByCourse_Id(course.getId());
    }

    /**
     * Creates a ManagedUserVM for testing.
     *
     * @param login the login of the user
     * @return the created ManagedUserVM
     */
    public ManagedUserVM createManagedUserVM(String login) {
        ManagedUserVM userVM = new ManagedUserVM();
        userVM.setLogin(login);
        userVM.setPassword(UserFactory.USER_PASSWORD);
        userVM.setFirstName("Firstname");
        userVM.setLastName("Lastname");
        userVM.setEmail(login + "@test.de");
        userVM.setActivated(true);
        userVM.setLangKey(Constants.DEFAULT_LANGUAGE);
        userVM.setAuthorities(Set.of(Role.STUDENT.getAuthority()));
        return userVM;
    }

    /**
     * Records an account's LLM usage decision. The preference lives in {@code user_ai_preference}, keyed on the user id, so
     * the account has to be saved before it can be recorded.
     *
     * @param user     the account, which must already be saved
     * @param decision the decision to record, or null to record only a timestamp
     */
    public void setAiSelectionDecision(User user, AiSelectionDecision decision) {
        if (decision == null) {
            return;
        }
        userAiPreferenceService.recordDecision(user.getId(), decision, ZonedDateTime.now());
    }

    /**
     * Records when an account made its LLM usage decision, keeping whatever decision is already stored.
     *
     * @param user the account, which must already be saved
     * @param when the timestamp to record
     */
    public void setAiSelectionDecisionDate(User user, ZonedDateTime when) {
        AiSelectionDecision existing = userAiPreferenceService.findDecision(user.getId());
        userAiPreferenceService.recordDecision(user.getId(), existing != null ? existing : AiSelectionDecision.CLOUD_AI, when);
    }

    /**
     * Turns Memiris on or off for an account.
     *
     * @param user    the account, which must already be saved
     * @param enabled whether Memiris may remember anything
     */
    public void setMemirisEnabled(User user, boolean enabled) {
        userAiPreferenceService.setMemirisEnabled(user.getId(), enabled);
    }

    /**
     * Records the fixture's default AI decision for a generated account. The preference is a row keyed on the user id, so
     * it can only be recorded once the account is saved, which is why it is applied here rather than by UserFactory. The
     * many Iris and Athena tests that rely on the fixture default depend on this.
     *
     * @param user a saved account
     */
    private void seedDefaultAiPreference(User user) {
        if (user.getId() == null) {
            return;
        }
        // Only when the account has no preference row at all. Checking the decision instead would re-seed an account whose
        // decision a test cleared on purpose: clearAiSelectionDecision leaves the row behind when it still holds a Memiris
        // choice, and a null decision then looks exactly like never having decided.
        if (!userAiPreferenceService.hasPreferenceRow(user.getId())) {
            userAiPreferenceService.recordDecision(user.getId(), AiSelectionDecision.CLOUD_AI, ZonedDateTime.now());
        }
    }

    /**
     * Saves a generated account and records the fixture's default AI decision for it, which needs the saved account's id.
     *
     * @param user the generated account
     * @return the saved account
     */
    private User saveWithDefaultAiPreference(User user) {
        User saved = userTestRepository.save(user);
        seedDefaultAiPreference(saved);
        return saved;
    }

    /**
     * Removes an account's recorded LLM usage decision, which the fixture seeds by default. Needed by tests that assert the
     * behaviour of an account that has not decided yet, since the decision is a persisted row rather than in-memory state.
     *
     * @param user the account, which must already be saved
     */
    public void clearAiSelectionDecision(User user) {
        userAiPreferenceService.clearDecision(user.getId());
    }
}
