package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.jira.JiraAuthenticationProvider;
import de.tum.in.www1.artemis.web.rest.UserJWTController;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

@ActiveProfiles({ "artemis", "jira" })
public class JiraAuthenticationIntegationTest extends AuthenticationIntegrationTest {

    @Value("${artemis.user-management.external.admin-group-name}")
    private String ADMIN_GROUP_NAME;

    @Value("${artemis.user-management.external.user}")
    private String JIRA_USER;

    @Value("${artemis.user-management.external.password}")
    private String JIRA_PASSWORD;

    @Autowired
    private JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JiraAuthenticationProvider jiraAuthenticationProvider;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    private static final String USERNAME = "student1";

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        jiraRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    public void analyzeApplicationContext_withExternalUserManagement_NoInternalAuthenticationBeanPresent() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).as("No bean of type ArtemisInternalAuthenticationProvider initialized")
                .isThrownBy(() -> applicationContext.getBean(ArtemisInternalAuthenticationProvider.class));
    }

    @Override
    @Test
    public void launchLtiRequest_authViaEmail_success() throws Exception {
        final var username = "mrrobot";
        final var email = ltiLaunchRequest.getLis_person_contact_email_primary();
        final var firstName = "Elliot";
        final var groups = Set.of("allsec", "security", ADMIN_GROUP_NAME, course.getInstructorGroupName(), course.getTeachingAssistantGroupName());
        jiraRequestMockProvider.mockGetUsernameForEmail(email, username);
        jiraRequestMockProvider.mockGetOrCreateUserLti(JIRA_USER, JIRA_PASSWORD, username, email, firstName, groups);
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course.getStudentGroupName()));
        jiraRequestMockProvider.mockGetOrCreateUserLti(username, "", username, email, firstName, groups);
        super.launchLtiRequest_authViaEmail_success();

        final var user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username).get();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getFirstName()).isEqualTo(firstName);
        assertThat(user.getGroups()).containsAll(groups);
        assertThat(user.getGroups()).contains(course.getStudentGroupName());
        assertThat(user.getAuthorities()).containsAll(authorityRepository.findAll());

        final var password = userService.encryptor().decrypt(user.getPassword());
        final var auth = new TestingAuthenticationToken(username, password);
        final var responseAuth = jiraAuthenticationProvider.authenticate(auth);

        assertThat(responseAuth.getPrincipal()).isEqualTo(username);
        assertThat(responseAuth.getCredentials()).isEqualTo(user.getPassword());
    }

    @Test
    @WithAnonymousUser
    public void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        jiraRequestMockProvider.mockGetOrCreateUserJira(USERNAME, "test@test.de", "Test", Set.of("test"));

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        UserJWTController.JWTToken jwtToken = request.postWithResponseBody("/api/authenticate", loginVM, UserJWTController.JWTToken.class, HttpStatus.OK, httpHeaders);
        assertThat(jwtToken.getIdToken()).as("JWT token is present").isNotNull();
        assertThat(this.tokenProvider.validateToken(jwtToken.getIdToken())).as("JWT token is valid").isTrue();
    }

    @Test
    @WithAnonymousUser
    public void testJWTAuthenticationCaptcha() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        jiraRequestMockProvider.mockGetOrCreateUserJiraCaptchaException(USERNAME, "test@test.de", "Test", Set.of("test"));

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        var expectedResponseHeaders = new HashMap<String, String>();
        expectedResponseHeaders.put("x-artemisapp-error", "CAPTCHA required");
        UserJWTController.JWTToken jwtToken = request.postWithResponseBody("/api/authenticate", loginVM, UserJWTController.JWTToken.class, HttpStatus.FORBIDDEN, httpHeaders,
                expectedResponseHeaders);
    }
}
