package de.tum.in.www1.artemis.usermanagement;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.URISyntaxException;
import java.util.Set;

import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.connector.JiraRequestMockProvider;
import de.tum.in.www1.artemis.usermanagement.util.AbstractArtemisIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling" })
public abstract class AbstractSpringIntegrationBambooBitbucketJiraTest extends AbstractArtemisIntegrationTest {

    @Autowired
    protected JiraRequestMockProvider jiraRequestMockProvider;

    @Autowired
    protected BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @AfterEach
    public void resetSpyBeans() {
        super.resetSpyBeans();
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) throws URISyntaxException {
        var managedUserVM = new ManagedUserVM(user);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockRemoveUserFromGroup(oldGroups, managedUserVM.getLogin(), false, true);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(managedUserVM.getGroups());
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws URISyntaxException {
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
