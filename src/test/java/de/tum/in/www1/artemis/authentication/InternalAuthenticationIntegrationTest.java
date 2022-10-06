package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.domain.Authority.*;
import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

class InternalAuthenticationIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ArtemisInternalAuthenticationProvider artemisInternalAuthenticationProvider;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LtiUserIdRepository ltiUserIdRepository;

    @Autowired
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private User student;

    private static final String USERNAME = "student1";

    private ProgrammingExercise programmingExercise;

    private LtiLaunchRequestDTO ltiLaunchRequest;

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);

        database.addUsers(1, 0, 0, 0);
        database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        ltiLaunchRequest = AuthenticationIntegrationTestHelper.setupDefaultLtiLaunchRequest();
        doReturn(null).when(ltiService).verifyRequest(any());

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(USERNAME).get();
        final var encodedPassword = passwordService.hashPassword(USER_PASSWORD);
        student.setPassword(encodedPassword);
        student.setInternal(true);
        userRepository.save(student);
        ltiLaunchRequest.setLis_person_contact_email_primary(student.getEmail());
    }

    @AfterEach
    void teardown() {
        database.resetDatabase();
    }

    @Test
    void launchLtiRequest_authViaEmail_success() throws Exception {
        ltiLaunchRequest.setCustom_lookup_user_by_email(true);
        request.postForm("/api/lti/launch/" + programmingExercise.getId(), ltiLaunchRequest, HttpStatus.FOUND);

        final var user = userRepository.findAll().get(0);
        final var ltiUser = ltiUserIdRepository.findAll().get(0);
        final var ltiOutcome = ltiOutcomeUrlRepository.findAll().get(0);
        assertThat(ltiUser.getUser()).isEqualTo(user);
        assertThat(ltiUser.getLtiUserId()).isEqualTo(ltiLaunchRequest.getUser_id());
        assertThat(ltiOutcome.getUser()).isEqualTo(user);
        assertThat(ltiOutcome.getExercise()).isEqualTo(programmingExercise);
        assertThat(ltiOutcome.getUrl()).isEqualTo(ltiLaunchRequest.getLis_outcome_service_url());
        assertThat(ltiOutcome.getSourcedId()).isEqualTo(ltiLaunchRequest.getLis_result_sourcedid());

        final var updatedStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin(USERNAME).get();
        assertThat(student).isEqualTo(updatedStudent);
    }

    @Test
    @WithAnonymousUser
    void authenticateAfterLtiRequest_success() throws Exception {
        launchLtiRequest_authViaEmail_success();

        final var auth = new TestingAuthenticationToken(student.getLogin(), USER_PASSWORD);
        final var authResponse = artemisInternalAuthenticationProvider.authenticate(auth);

        assertThat(authResponse.getCredentials()).hasToString(student.getPassword());
        assertThat(authResponse.getPrincipal()).isEqualTo(student.getLogin());
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void registerForCourse_internalAuth_success() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        final var student = ModelFactory.generateActivatedUser("ab12cde");
        userRepository.save(student);

        final var pastTimestamp = ZonedDateTime.now().minusDays(5);
        final var futureTimestamp = ZonedDateTime.now().plusDays(5);
        var course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1.setRegistrationEnabled(true);
        course1 = courseRepository.save(course1);

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(student.getLogin(), student, student.getGroups(), Set.of(), false);
        final var updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());
    }

    @NotNull
    private User createUserWithRestApi(Set<Authority> authorities) throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        gitlabRequestMockProvider.mockGetUserID();
        database.addTutorialCourse();

        student.setId(null);
        student.setLogin("user1");
        student.setPassword("foobar");
        student.setEmail("user1@secret.invalid");
        student.setAuthorities(authorities);

        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(student.getGroups());
        assertThat(exercises).isEmpty();
        jenkinsRequestMockProvider.mockCreateUser(student, false, false, false);

        final var user = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(user).isNotNull();
        return user;
    }

    private void assertUserGroups(User user, boolean students, boolean tutors, boolean editors, boolean instructors) {
        if (students) {
            assertThat(user.getGroups()).contains(tutorialGroupStudents.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupStudents.get());
        }
        if (tutors) {
            assertThat(user.getGroups()).contains(tutorialGroupTutors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupTutors.get());
        }
        if (editors) {
            assertThat(user.getGroups()).contains(tutorialGroupEditors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupEditors.get());
        }
        if (instructors) {
            assertThat(user.getGroups()).contains(tutorialGroupInstructors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupInstructors.get());
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

        // UserJWTController.JWTToken jwtToken = request.postWithResponseBody("/api/authenticate", loginVM, UserJWTController.JWTToken.class, HttpStatus.OK, httpHeaders);
        // assertThat(jwtToken.getIdToken()).as("JWT token is present").isNotNull();
        // assertThat(this.tokenProvider.validateTokenForAuthority(jwtToken.getIdToken())).as("JWT token is valid").isTrue();
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

        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();

        assertThat(passwordService.checkPasswordMatch(managedUserVM.getPassword(), updatedUserIndDB.getPassword())).isTrue();

        // Skip passwords for comparison
        updatedUserIndDB.setPassword(null);
        student.setPassword(null);

        assertThat(response).isNotNull();
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }
}
