package de.tum.in.www1.artemis.usermanagement;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Set;

import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.usermanagement.util.AbstractArtemisIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.domain.User;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "gitlab", "jenkins", "scheduling" })
@TestPropertySource(properties = { "info.guided-tour.course-group-tutors=artemis-artemistutorial-tutors", "info.guided-tour.course-group-students=artemis-artemistutorial-students",
        "info.guided-tour.course-group-editors=artemis-artemistutorial-editors", "info.guided-tour.course-group-instructors=artemis-artemistutorial-instructors",
        "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationJenkinsGitlabTest extends AbstractArtemisIntegrationTest {

    @SpyBean
    protected JenkinsServer jenkinsServer;

    @Autowired
    protected JenkinsRequestMockProvider jenkinsRequestMockProvider;

    @Autowired
    protected GitlabRequestMockProvider gitlabRequestMockProvider;

    @AfterEach
    public void resetSpyBeans() {
        Mockito.reset( jenkinsServer);
        super.resetSpyBeans();
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, Set<String> oldGroups) throws Exception {
        jenkinsRequestMockProvider.mockUpdateUserAndGroups(oldLogin, user, user.getGroups(), oldGroups, true);
        gitlabRequestMockProvider.mockUpdateVcsUser(oldLogin, user, oldGroups, user.getGroups(), true);
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception {
        gitlabRequestMockProvider.mockCreateVcsUser(user, false);
        jenkinsRequestMockProvider.mockCreateUser(user, userExistsInCi, false, false);
    }

    @Override
    public void mockFailToCreateUserInExernalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception {
        gitlabRequestMockProvider.mockCreateVcsUser(user, failInVcs);
        jenkinsRequestMockProvider.mockCreateUser(user, false, failInCi, failToGetCiUser);
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws Exception {
        gitlabRequestMockProvider.mockDeleteVcsUser(user.getLogin(), failInVcs);
        jenkinsRequestMockProvider.mockDeleteUser(user, userExistsInUserManagement, failInCi);
    }
}
