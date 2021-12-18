package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;

/**
 * this test should be completely independent of any profiles or configurations (e.g. VCS, CIS)
 */
public abstract class AbstractArtemisIntegrationTest implements MockDelegate {

    @Value("${server.url}")
    protected String artemisServerUrl;

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
    @SpyBean
    protected LtiService ltiService;

    @SpyBean
    protected GitService gitService;

    @SpyBean
    protected FileService fileService;

    @SpyBean
    protected ZipFileService zipFileService;

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
    protected ProgrammingExerciseGradingService programmingExerciseGradingService;

    @SpyBean
    protected ExamAccessService examAccessService;

    @SpyBean
    protected InstanceMessageSendService instanceMessageSendService;

    @SpyBean
    protected ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    @SpyBean
    protected ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @SpyBean
    protected ScoreService scoreService;

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    protected MockedStatic<UrlService> urlServiceMockedStatic;

    @BeforeEach
    public void setupStaticMock() {
        urlServiceMockedStatic = Mockito.mockStatic(UrlService.class, Mockito.withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    }

    public void resetSpyBeans() {
        Mockito.reset(ltiService, gitService, groupNotificationService, websocketMessagingService, plantUmlService, messagingTemplate, programmingSubmissionService,
                examAccessService, instanceMessageSendService, programmingExerciseScheduleService, programmingExerciseParticipationService, scoreService);
        urlServiceMockedStatic.reset();
        urlServiceMockedStatic.close();
    }

    @Override
    public void mockGetRepositorySlugFromRepositoryUrl(String repositorySlug, VcsRepositoryUrl repositoryUrl) {
        // we convert this to URL to make sure the mock is properly hit, as there could be problems with objects such as VcsRepositoryUrl and its subclasses
        urlServiceMockedStatic.when(() -> UrlService.getRepositorySlugFromRepositoryUrl(repositoryUrl)).thenReturn(repositorySlug);
    }

    @Override
    public void mockGetProjectKeyFromRepositoryUrl(String projectKey, VcsRepositoryUrl repositoryUrl) {
        // we convert this to URL to make sure the mock is properly hit, as there could be problems with objects such as VcsRepositoryUrl and its subclasses
        urlServiceMockedStatic.when(() -> UrlService.getProjectKeyFromRepositoryUrl(repositoryUrl)).thenReturn(projectKey);
    }

    @Override
    public void mockGetRepositoryPathFromRepositoryUrl(String projectPath, VcsRepositoryUrl repositoryUrl) {
        // we convert this to URL to make sure the mock is properly hit, as there could be problems with objects such as VcsRepositoryUrl and its subclasses
        urlServiceMockedStatic.when(() -> UrlService.getRepositoryPathFromRepositoryUrl(repositoryUrl)).thenReturn(projectPath);
    }

    @Override
    public void mockGetProjectKeyFromAnyUrl(String projectKey) {
        urlServiceMockedStatic.when(() -> UrlService.getProjectKeyFromRepositoryUrl(any())).thenReturn(projectKey);
    }
}
