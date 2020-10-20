package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.net.URL;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.ldap.LdapUserService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;

/**
 * this test should be completely independent of any profiles or configurations (e.g. VCS, CIS)
 */
public abstract class AbstractArtemisIntegrationTest implements MockDelegate {

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @SpyBean
    protected LdapUserService ldapUserService;

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
    @SpyBean
    protected LtiService ltiService;

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

    @SpyBean
    protected ExamAccessService examAccessService;

    @SpyBean
    protected InstanceMessageSendService instanceMessageSendService;

    @SpyBean
    protected ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    @SpyBean
    protected ProgrammingExerciseParticipationService programmingExerciseParticipationServiceSpy;

    @SpyBean
    protected UrlService urlService;

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    public void resetSpyBeans() {
        Mockito.reset(ltiService, gitService, groupNotificationService, websocketMessagingService, plantUmlService, messagingTemplate, programmingSubmissionService,
                examAccessService, instanceMessageSendService, programmingExerciseScheduleService, programmingExerciseParticipationServiceSpy);
    }

    @Override
    public void mockGetRepositorySlugFromUrl(String repositorySlug, URL url) {
        doReturn(repositorySlug).when(urlService).getRepositorySlugFromUrl(url);
    }

    @Override
    public void mockGetProjectKeyFromUrl(String projectKey, URL url) {
        doReturn(projectKey).when(urlService).getProjectKeyFromUrl(url);
    }

    @Override
    public void mockGetProjectKeyFromAnyUrl(String projectKey) {
        doReturn(projectKey).when(urlService).getProjectKeyFromUrl(any());
    }
}
