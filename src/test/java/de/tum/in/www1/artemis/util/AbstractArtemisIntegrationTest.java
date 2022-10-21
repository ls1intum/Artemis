package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.service.notifications.TutorialGroupNotificationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.service.scheduled.ScheduleService;

/**
 * this test should be completely independent of any profiles or configurations (e.g. VCS, CIS)
 */
public abstract class AbstractArtemisIntegrationTest implements MockDelegate {

    @Value("${server.url}")
    protected String artemisServerUrl;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

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
    protected TutorialGroupNotificationService tutorialGroupNotificationService;

    @SpyBean
    protected SingleUserNotificationService singleUserNotificationService;

    @SpyBean
    protected JavaMailSender javaMailSender;

    @SpyBean
    protected MailService mailService;

    @SpyBean
    protected WebsocketMessagingService websocketMessagingService;

    @SpyBean
    protected SimpMessageSendingOperations messagingTemplate;

    @SpyBean
    protected ProgrammingTriggerService programmingTriggerService;

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
    protected UrlService urlService;

    @SpyBean
    protected ScheduleService scheduleService;

    @SpyBean
    protected ParticipantScoreSchedulerService participantScoreSchedulerService;

    @SpyBean
    protected TextBlockService textBlockService;

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected HibernateQueryInterceptor queryInterceptor;

    @BeforeEach
    void mockMailService() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void stopRunningTasks() {
        participantScoreSchedulerService.shutdown();
    }

    public void resetSpyBeans() {
        Mockito.reset(ltiService, gitService, groupNotificationService, tutorialGroupNotificationService, singleUserNotificationService, websocketMessagingService,
                messagingTemplate, examAccessService, mailService, instanceMessageSendService, programmingExerciseScheduleService, programmingExerciseParticipationService,
                urlService, scheduleService, participantScoreSchedulerService, javaMailSender, programmingTriggerService);
    }

    @Override
    public void mockGetRepositorySlugFromRepositoryUrl(String repositorySlug, VcsRepositoryUrl repositoryUrl) {
        // mock both versions to be independent
        doReturn(repositorySlug).when(urlService).getRepositorySlugFromRepositoryUrl(repositoryUrl);
        doReturn(repositorySlug).when(urlService).getRepositorySlugFromRepositoryUrlString(repositoryUrl.toString());
    }

    @Override
    public void mockGetProjectKeyFromRepositoryUrl(String projectKey, VcsRepositoryUrl repositoryUrl) {
        doReturn(projectKey).when(urlService).getProjectKeyFromRepositoryUrl(repositoryUrl);
    }

    @Override
    public void mockGetRepositoryPathFromRepositoryUrl(String projectPath, VcsRepositoryUrl repositoryUrl) {
        doReturn(projectPath).when(urlService).getRepositoryPathFromRepositoryUrl(repositoryUrl);
    }

    @Override
    public void mockGetProjectKeyFromAnyUrl(String projectKey) {
        doReturn(projectKey).when(urlService).getProjectKeyFromRepositoryUrl(any());
    }

    /**
     * Allows to test the number of database queries during a REST call by passing in the REST call and returning a QueryCountAssert object
     *
     * @param call the REST call during which the number of database queries will be tracked
     * @return a QueryCountAssert object allowing to test how many queries were done during the call
     */
    protected <T, E extends Exception> QueryCountAssert<T, E> assertThatDb(ThrowingProducer<T, E> call) {
        return QueryCountAssert.assertThatDb(queryInterceptor, call);
    }
}
