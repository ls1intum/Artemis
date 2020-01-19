package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;

@ActiveProfiles({ "artemis", "jira" })
public class JiraAuthenticationIntegationTest extends AuthenticationIntegrationTest {

    @Value("${artemis.user-management.external.user}")
    private String JIRA_USER;

    @Value("${artemis.user-management.external.password}")
    private String JIRA_PASSWORD;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private JiraRequestMockProvider jiraRequestMockProvider;

    // @Test
    // public void analyzeApplicationContext_withExternalUserManagement_NoInternalAuthenticationBeanPresent() {
    // assertThatExceptionOfType(NoSuchBeanDefinitionException.class).as("No bean of type ArtemisInternalAuthenticationProvider initialized")
    // .isThrownBy(() -> applicationContext.getBean(ArtemisInternalAuthenticationProvider.class));
    // }

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        jiraRequestMockProvider.enableMockingOfRequests();

        final var userAuthority = new Authority(AuthoritiesConstants.USER);
        authorityRepository.save(userAuthority);
    }

    @Override
    @Test
    public void launchLtiRequest_authViaEmail_success() throws Exception {
        final var username = "mrrobot";
        final var email = ltiLaunchRequest.getLis_person_contact_email_primary();
        final var firstName = "Elliot";
        final var groups = Set.of("allsec", "security");
        jiraRequestMockProvider.mockGetUsernameForEmail(email, username);
        jiraRequestMockProvider.mockGetOrCreateUser(JIRA_USER, JIRA_PASSWORD, username, email, firstName, groups);
        jiraRequestMockProvider.mockAddUserToGroup(Set.of(course.getStudentGroupName()));
        super.launchLtiRequest_authViaEmail_success();

        final var user = userRepository.findAllWithGroups(Pageable.unpaged()).iterator().next();
        assertThat(user.getLogin()).isEqualTo(username);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getFirstName()).isEqualTo(firstName);
        assertThat(user.getGroups()).containsAll(groups);
        assertThat(user.getGroups()).contains(course.getStudentGroupName());
    }
}
