package de.tum.in.www1.artemis.user;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.UserTestRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.user.PasswordService;

/**
 * Service responsible for initializing the database with specific testdata related to Users for use in integration tests.
 */
@Service
public class UserUtilService {

    private static final Logger log = LoggerFactory.getLogger(UserUtilService.class);

    private static final Authority userAuthority = new Authority(Role.STUDENT.getAuthority());

    private static final Authority tutorAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority editorAuthority = new Authority(Role.EDITOR.getAuthority());

    private static final Authority instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority adminAuthority = new Authority(Role.ADMIN.getAuthority());

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> editorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority);

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserTestRepository userTestRepository;

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
     * @param groups                   The groups that the Users will be added to
     * @param authorities              The authorities that the Users will have
     * @param amount                   The amount of Users to generate
     * @param registrationNumberPrefix The prefix that will be added in front of every User's registration number
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount, String registrationNumberPrefix) {
        List<User> generatedUsers = generateAndSaveActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < generatedUsers.size(); i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix The prefix that will be added in front of every User's username
     * @param groups      The groups that the Users will be added to
     * @param authorities The authorities that the Users will have
     * @param amount      The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateAndSaveActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            user = userRepo.save(user);
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
        return userRepo.save(user);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, commonPasswordHash, groups, authorities, 1, amount);
    }

    /**
     * Creates and saves Users with the given arguments. Creates [to - from + 1] Users.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param from               The first number to append to the loginPrefix
     * @param to                 The last number to append to the loginPrefix
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int from, int to) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
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
        return userRepo.save(user);
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
        return userRepo.save(user);
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
            authorityRepository.saveAll(adminAuthorities);
        }
        log.debug("Generate {} students...", numberOfStudents);
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(USER_PASSWORD), new String[] { "tumuser", "testgroup", prefix + "tumuser" },
                studentAuthorities, numberOfStudents);
        log.debug("{} students generated. Generate {} tutors...", numberOfStudents, numberOfTutors);
        var tutors = generateActivatedUsers(prefix + "tutor", passwordService.hashPassword(USER_PASSWORD), new String[] { "tutor", "testgroup", prefix + "tutor" },
                tutorAuthorities, numberOfTutors);
        log.debug("{} tutors generated. Generate {} editors...", numberOfTutors, numberOfEditors);
        var editors = generateActivatedUsers(prefix + "editor", passwordService.hashPassword(USER_PASSWORD), new String[] { "editor", "testgroup", prefix + "editor" },
                editorAuthorities, numberOfEditors);
        log.debug("{} editors generated. Generate {} instructors...", numberOfEditors, numberOfInstructors);
        var instructors = generateActivatedUsers(prefix + "instructor", passwordService.hashPassword(USER_PASSWORD),
                new String[] { "instructor", "testgroup", prefix + "instructor" }, instructorAuthorities, numberOfInstructors);
        log.debug("{} instructors generated", numberOfInstructors);

        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(editors);
        usersToAdd.addAll(instructors);

        if (!userExistsWithLogin("admin")) {
            log.debug("Generate admin");
            User admin = UserFactory.generateActivatedUser("admin", passwordService.hashPassword(USER_PASSWORD));
            admin.setGroups(Set.of("admin"));
            admin.setAuthorities(adminAuthorities);
            usersToAdd.add(admin);
            log.debug("Generate admin done");
        }

        // Before adding new users, existing users are removed from courses.
        // Otherwise, the amount users per course constantly increases while running the tests,
        // even though the old users are not needed anymore.
        if (!usersToAdd.isEmpty()) {
            Set<User> currentUsers = userTestRepository.findAllByGroupsNotEmpty();
            log.debug("Removing {} users from all courses...", currentUsers.size());
            currentUsers.forEach(user -> user.setGroups(Set.of()));
            userRepo.saveAll(currentUsers);
            log.debug("Removing {} users from all courses. Done", currentUsers.size());
            log.debug("Save {} users to database...", usersToAdd.size());
            usersToAdd = userRepo.saveAll(usersToAdd);
            log.debug("Save {} users to database. Done", usersToAdd.size());
        }

        return usersToAdd;
    }

    /**
     * Creates and saves Users with student authorities. Creates [to - from + 1] Users.
     *
     * @param prefix The prefix that will be added in front of every User's username
     * @param from   The first number to append to the loginPrefix
     * @param to     The last number to append to the loginPrefix
     */
    public void addStudents(String prefix, int from, int to) {
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(USER_PASSWORD), new String[] { "tumuser", "testgroup", prefix + "tumuser" },
                studentAuthorities, from, to);
        userRepo.saveAll(students);
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

        var existingUserWithRegistrationNumber = userRepo.findOneWithGroupsAndAuthoritiesByRegistrationNumber(user.getRegistrationNumber());
        if (existingUserWithRegistrationNumber.isPresent()) {
            existingUserWithRegistrationNumber.get().setRegistrationNumber(null);
            userRepo.save(existingUserWithRegistrationNumber.get());
        }
    }

    /**
     * Creates and saves a User with instructor authorities, if no User with the given username exists.
     *
     * @param instructorGroup The group that the instructor will be added to
     * @param instructorName  The login of the instructor
     */
    public void addInstructor(final String instructorGroup, final String instructorName) {
        if (!userExistsWithLogin(instructorName)) {
            var newUsers = generateAndSaveActivatedUsers(instructorName, new String[] { instructorGroup, "testgroup" }, instructorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var instructor = userRepo.save(newUsers.get(0));
                assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
            }
        }
    }

    /**
     * Creates and saves a User with editor authorities, if no User with the given username exists.
     *
     * @param editorGroup The group that the editor will be added to
     * @param editorName  The login of the editor
     */
    public void addEditor(final String editorGroup, final String editorName) {
        if (!userExistsWithLogin(editorName)) {
            var newUsers = generateAndSaveActivatedUsers(editorName, new String[] { editorGroup, "testgroup" }, editorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var editor = userRepo.save(newUsers.get(0));
                assertThat(editor.getId()).as("Editor has been created").isNotNull();
            }
        }
    }

    /**
     * Creates and saves a User with tutor authorities, if no User with the given username exists.
     *
     * @param taGroup The group that the tutor will be added to
     * @param taName  The login of the tutor
     */
    public void addTeachingAssistant(final String taGroup, final String taName) {
        if (!userExistsWithLogin(taName)) {
            var newUsers = generateAndSaveActivatedUsers(taName, new String[] { taGroup, "testgroup" }, tutorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var ta = userRepo.save(newUsers.get(0));
                assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
            }
        }
    }

    /**
     * Creates and saves a User with student authorities, if no User with the given username exists.
     *
     * @param studentGroup The group that the student will be added to
     * @param studentName  The login of the student
     */
    public void addStudent(final String studentGroup, final String studentName) {
        if (!userExistsWithLogin(studentName)) {
            var newUsers = generateAndSaveActivatedUsers(studentName, new String[] { studentGroup, "testgroup" }, studentAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var student = userRepo.save(newUsers.get(0));
                assertThat(student.getId()).as("Student has been created").isNotNull();
            }
        }
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
        return userRepo.findOneByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Gets the User with the given username from the database. Throws an IllegalArgumentException if the User does not exist.
     *
     * @param login The username of the User
     * @return The User with eagerly loaded groups and authorities
     */
    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userRepo.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Checks if a User with the given username exists.
     *
     * @param login The username of the User
     * @return True, if a User with the given login exists, false otherwise
     */
    public boolean userExistsWithLogin(String login) {
        return userRepo.findOneByLogin(login).isPresent();
    }

    /**
     * Removes the User with the given username from all Courses and saves the updated User.
     *
     * @param login The login of the User
     */
    public void removeUserFromAllCourses(String login) {
        User user = getUserByLogin(login);
        user.setGroups(Set.of());
        userRepo.save(user);
    }

    /**
     * Updates and saves the User's groups.
     *
     * @param userPrefix          The prefix of the User's username
     * @param userSuffix          The suffix of the custom group
     * @param numberOfStudents    The number of students to update
     * @param numberOfTutors      The number of tutors to update
     * @param numberOfEditors     The number of editors to update
     * @param numberOfInstructors The number of instructors to update
     */
    public void adjustUserGroupsToCustomGroups(String userPrefix, String userSuffix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        for (int i = 1; i <= numberOfStudents; i++) {
            var user = getUserByLogin(userPrefix + "student" + i);
            user.setGroups(Set.of(userPrefix + "student" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfTutors; i++) {
            var user = getUserByLogin(userPrefix + "tutor" + i);
            user.setGroups(Set.of(userPrefix + "tutor" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfEditors; i++) {
            var user = getUserByLogin(userPrefix + "editor" + i);
            user.setGroups(Set.of(userPrefix + "editor" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfInstructors; i++) {
            var user = getUserByLogin(userPrefix + "instructor" + i);
            user.setGroups(Set.of(userPrefix + "instructor" + userSuffix));
            userRepo.save(user);
        }
    }
}
