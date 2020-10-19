package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.PlantUmlService;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsService;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ "artemis", "gitlab", "jenkins", "automaticText" })
@TestPropertySource(properties = { "info.tutorial-course-groups.tutors=artemis-artemistutorial-tutors", "info.tutorial-course-groups.students=artemis-artemistutorial-students",
        "info.tutorial-course-groups.instructors=artemis-artemistutorial-instructors", "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationJenkinsGitlabTest {

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
    @SpyBean
    protected LtiService ltiService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Jenkins using the corresponding RestTemplate.
    @SpyBean
    protected JenkinsService continuousIntegrationService;

    // please only use this to verify method calls using Mockito. Do not mock methods, instead mock the communication with Gitlab using the corresponding RestTemplate and
    // GitlabApi.
    @SpyBean
    protected GitLabService versionControlService;

    @SpyBean
    protected JenkinsServer jenkinsServer;

    @SpyBean
    protected GitService gitService;

    @SpyBean
    protected GroupNotificationService groupNotificationService;

    @SpyBean
    protected WebsocketMessagingService websocketMessagingService;

    @SpyBean
    protected PlantUmlService plantUmlService;

    @SpyBean
    protected SimpMessageSendingOperations messagingTemplate;

    @SpyBean
    protected ProgrammingSubmissionService programmingSubmissionService;

    @AfterEach
    public void resetSpyBeans() {
        Mockito.reset(ltiService, continuousIntegrationService, versionControlService, jenkinsServer, gitService, groupNotificationService, websocketMessagingService,
                plantUmlService, messagingTemplate, programmingSubmissionService);
    }
}
