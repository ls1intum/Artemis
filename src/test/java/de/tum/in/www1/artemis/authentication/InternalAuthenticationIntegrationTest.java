package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.domain.Authority.*;
import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.tutorialgroups.TutorialGroupUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

class InternalAuthenticationIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "internalauth";

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private User student;

    private static final String USERNAME = TEST_PREFIX + "student1";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseUtilService.addOnlineCourseConfigurationToCourse(course);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(USERNAME).orElseThrow();
        final var encodedPassword = passwordService.hashPassword(USER_PASSWORD);
        student.setPassword(encodedPassword);
        student.setInternal(true);
        userRepository.save(student);
    }

    @AfterEach
    void teardown() {
        // Set the student group to some other group because only one group can have the tutorialGroupStudents-group
        SecurityUtils.setAuthorizationObject();
        var tutorialCourse = courseRepository.findCourseByStudentGroupName(tutorialGroupStudents.orElseThrow());
        if (tutorialCourse != null) {
            tutorialCourse.setStudentGroupName("non-tutorial-course");
            tutorialCourse.setTeachingAssistantGroupName("non-tutorial-course");
            tutorialCourse.setEditorGroupName("non-tutorial-course");
            tutorialCourse.setInstructorGroupName("non-tutorial-course");
            courseRepository.save(tutorialCourse);
        }
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void registerForCourse_internalAuth_success() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        final var student = userUtilService.createAndSaveUser("ab12cde");

        final var pastTimestamp = ZonedDateTime.now().minusDays(5);
        final var futureTimestamp = ZonedDateTime.now().plusDays(5);
        var course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1.setEnrollmentEnabled(true);
        course1 = courseRepository.save(course1);

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(student.getLogin(), student, student.getGroups(), Set.of(), false);
        Set<String> updatedGroups = request.postWithResponseBody("/api/courses/" + course1.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is registered for course").contains(course1.getStudentGroupName());
    }

    @NotNull
    private User createUserWithRestApi(Set<Authority> authorities) throws Exception {
        userRepository.findOneByLogin("user1").ifPresent(userRepository::delete);
        gitlabRequestMockProvider.enableMockingOfRequests();
        gitlabRequestMockProvider.mockGetUserID();
        tutorialGroupUtilService.addTutorialCourse();

        student.setId(null);
        student.setLogin("user1");
        student.setPassword("foobar");
        student.setEmail("user1@secret.invalid");
        student.setAuthorities(authorities);

        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(student.getGroups());
        assertThat(exercises).isEmpty();
        jenkinsRequestMockProvider.mockCreateUser(student, false, false, false);

        final var user = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(user).isNotNull();
        return user;
    }

    private void assertUserGroups(User user, boolean students, boolean tutors, boolean editors, boolean instructors) {
        if (students) {
            assertThat(user.getGroups()).contains(tutorialGroupStudents.orElseThrow());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupStudents.orElseThrow());
        }
        if (tutors) {
            assertThat(user.getGroups()).contains(tutorialGroupTutors.orElseThrow());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupTutors.orElseThrow());
        }
        if (editors) {
            assertThat(user.getGroups()).contains(tutorialGroupEditors.orElseThrow());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupEditors.orElseThrow());
        }
        if (instructors) {
            assertThat(user.getGroups()).contains(tutorialGroupInstructors.orElseThrow());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupInstructors.orElseThrow());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createUserWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY));
        assertUserGroups(user, true, false, false, false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createTutorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY));
        assertUserGroups(user, true, true, false, false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createEditorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY, EDITOR_AUTHORITY));
        assertUserGroups(user, true, true, true, false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createInstructorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY, EDITOR_AUTHORITY, INSTRUCTOR_AUTHORITY));
        assertUserGroups(user, true, true, true, true);
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthenticationLogoutAnonymous() throws Exception {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/logout", HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), true);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testJWTAuthenticationLogout() throws Exception {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/logout", HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), true);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserWithRemovedGroups_internalAuth_successful() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        gitlabRequestMockProvider.mockUpdateUser();

        final var oldGroups = student.getGroups();
        final var newGroups = Set.of("foo", "bar");
        student.setGroups(newGroups);
        final var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword("12345678");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(student.getLogin(), student, newGroups, oldGroups, false);

        final var response = request.putWithResponseBody("/api/admin/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        assertThat(passwordService.checkPasswordMatch(managedUserVM.getPassword(), updatedUserIndDB.getPassword())).isTrue();

        // Skip passwords for comparison
        updatedUserIndDB.setPassword(null);
        student.setPassword(null);

        assertThat(response).isNotNull();
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }
}
