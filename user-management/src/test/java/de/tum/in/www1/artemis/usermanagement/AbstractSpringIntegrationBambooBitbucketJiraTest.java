package de.tum.in.www1.artemis.usermanagement;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Set;

import de.tum.in.www1.artemis.usermanagement.connector.JiraRequestMockProvider;
import de.tum.in.www1.artemis.usermanagement.util.AbstractArtemisIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene", "apollon" })
public abstract class AbstractSpringIntegrationBambooBitbucketJiraTest extends AbstractArtemisIntegrationTest {

    @Autowired
    protected JiraRequestMockProvider jiraRequestMockProvider;

    @AfterEach
    public void resetSpyBeans() {
        super.resetSpyBeans();
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) {
        var managedUserVM = new ManagedUserVM(user);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockRemoveUserFromGroup(oldGroups, managedUserVM.getLogin(), false);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(managedUserVM.getGroups());
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        var managedUserVM = new ManagedUserVM(user);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(managedUserVM.getGroups());
    }

    @Override
    public void mockFailToCreateUserInExernalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        // Not needed here
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        // Not needed here
    }
}
