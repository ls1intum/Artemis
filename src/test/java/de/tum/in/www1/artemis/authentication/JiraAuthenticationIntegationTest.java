package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.security.JiraAuthenticationProvider;
import de.tum.in.www1.artemis.service.UserService;

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
    private UserService userService;

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
        jiraRequestMockProvider.mockGetOrCreateUser(JIRA_USER, JIRA_PASSWORD, username, email, firstName, groups);
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course.getStudentGroupName()));
        jiraRequestMockProvider.mockGetOrCreateUser(username, "", username, email, firstName, groups);
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
}
