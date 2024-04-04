package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithAnonymousUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.JiraRequestMockProvider;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exception.LtiEmailAlreadyInUseException;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.connectors.lti.LtiService;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

class JiraAuthenticationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "jiraauthintegration";

    @Autowired
    private JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AuthorityRepository authorityRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    LtiService ltiService;

    private static final String USERNAME = TEST_PREFIX + "student1";

    protected ProgrammingExercise programmingExercise;

    protected Course course;

    @BeforeEach
    void setUp() {
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseUtilService.addOnlineCourseConfigurationToCourse(course);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        userRepository.findOneByLogin(USERNAME).ifPresent(userRepository::delete);

        jiraRequestMockProvider.enableMockingOfRequests();
    }

    @WithAnonymousUser
    @Test
    void authenticateLtiUserEmailAlreadyInUse() throws IOException {
        final var username = "mrrobot";
        userRepository.findOneByLogin(username).ifPresent(userRepository::delete);
        final var firstName = "Elliot";
        final var email = "anonymous@tum.de";

        jiraRequestMockProvider.mockGetUsernameForEmail(email, email, username);

        assertThatExceptionOfType(LtiEmailAlreadyInUseException.class).isThrownBy(() -> ltiService.authenticateLtiUser(email, username, firstName, "lastname", true));
    }

    @Test
    void analyzeApplicationContext_withExternalUserManagement_NoInternalAuthenticationBeanPresent() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).as("No bean of type ArtemisInternalAuthenticationProvider initialized")
                .isThrownBy(() -> applicationContext.getBean(ArtemisInternalAuthenticationProvider.class));
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        jiraRequestMockProvider.mockGetOrCreateUserJira(USERNAME, "test@test.de", "Test", Set.of("test"));

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthenticationCaptcha() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        jiraRequestMockProvider.mockGetOrCreateUserJiraCaptchaException(USERNAME, "test@test.de", "Test", Set.of("test"));

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        var expectedResponseHeaders = new HashMap<String, String>();
        expectedResponseHeaders.put("x-artemisapp-error", "CAPTCHA required");
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.FORBIDDEN, httpHeaders, expectedResponseHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testEmptyPasswordAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword("");
        loginVM.setRememberMe(true);

        jiraRequestMockProvider.verifyNoGetOrCreateUserJira(USERNAME);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        // validation fails due to empty password is validated against min size
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.BAD_REQUEST, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }
}
