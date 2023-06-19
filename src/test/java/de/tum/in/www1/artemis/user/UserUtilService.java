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
 * Service responsible for initializing the database with specific testdata related to users for use in integration tests.
 */
@Service
public class UserUtilService {

    private final Logger log = LoggerFactory.getLogger(getClass());

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
     * Generate users that have registration numbers
     *
     * @param loginPrefix              prefix that will be added in front of every user's login
     * @param groups                   groups that the users will be added
     * @param authorities              authorities that the users will have
     * @param amount                   amount of users to generate
     * @param registrationNumberPrefix prefix that will be added in front of every user
     * @return users that were generated
     */
    public List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount, String registrationNumberPrefix) {
        List<User> generatedUsers = generateAndSaveActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < generatedUsers.size(); i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateAndSaveActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
    }

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

    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, commonPasswordHash, groups, authorities, 1, amount);
    }

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

    public User createAndSaveUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userRepo.save(user);
    }

    public User createOrReuseExistingUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return user;
    }

    public User createAndSaveUser(String login) {
        User user = UserFactory.generateActivatedUser(login);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userRepo.save(user);
    }

    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        return addUsers("", numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
    }

    /**
     * Adds the provided number of students and tutors into the user repository. Students login is a concatenation of the prefix "student" and a number counting from 1 to
     * numberOfStudents Tutors login is a concatenation of the prefix "tutor" and a number counting from 1 to numberOfStudents Tutors are all in the "tutor" group and students in
     * the "tumuser" group.
     * To avoid accumulating a high number of users per course, this method also removes existing users from courses before adding new users.
     *
     * @param prefix              the prefix for the user login
     * @param numberOfStudents    the number of students that will be added to the database
     * @param numberOfTutors      the number of tutors that will be added to the database
     * @param numberOfEditors     the number of editors that will be added to the database
     * @param numberOfInstructors the number of instructors that will be added to the database
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
        if (usersToAdd.size() > 0) {
            Set<User> currentUsers = userTestRepository.findAllInAnyGroup();
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
     * generates and adds students to the repo, starting with student with the index to
     *
     * @param prefix the test prefix
     * @param from   first student to be added (inclusive)
     * @param to     last student to be added (inclusive)
     */
    public void addStudents(String prefix, int from, int to) {
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(USER_PASSWORD), new String[] { "tumuser", "testgroup", prefix + "tumuser" },
                studentAuthorities, from, to);
        userRepo.saveAll(students);
    }

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

    public void addInstructor(final String instructorGroup, final String instructorName) {
        if (!userExistsWithLogin(instructorName)) {
            var newUsers = generateAndSaveActivatedUsers(instructorName, new String[] { instructorGroup, "testgroup" }, instructorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var instructor = userRepo.save(newUsers.get(0));
                assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
            }
        }
    }

    public void addEditor(final String editorGroup, final String editorName) {
        if (!userExistsWithLogin(editorName)) {
            var newUsers = generateAndSaveActivatedUsers(editorName, new String[] { editorGroup, "testgroup" }, editorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var editor = userRepo.save(newUsers.get(0));
                assertThat(editor.getId()).as("Editor has been created").isNotNull();
            }
        }
    }

    public void addTeachingAssistant(final String taGroup, final String taName) {
        if (!userExistsWithLogin(taName)) {
            var newUsers = generateAndSaveActivatedUsers(taName, new String[] { taGroup, "testgroup" }, tutorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var ta = userRepo.save(newUsers.get(0));
                assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
            }
        }
    }

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

    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userRepo.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    public boolean userExistsWithLogin(String login) {
        return userRepo.findOneByLogin(login).isPresent();
    }

    /**
     * Removes a user from all courses they are currently in.
     *
     * @param login login to find user with
     */
    public void removeUserFromAllCourses(String login) {
        User user = getUserByLogin(login);
        user.setGroups(Set.of());
        userRepo.save(user);
    }

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
