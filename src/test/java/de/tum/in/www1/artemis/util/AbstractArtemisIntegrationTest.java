package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.util.*;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.javamail.JavaMailSender;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.exercise.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.lti.Lti10Service;
import de.tum.in.www1.artemis.service.connectors.lti.Lti13Service;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.*;
import de.tum.in.www1.artemis.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.in.www1.artemis.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.service.scheduled.ScheduleService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.user.UserFactory;

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
    protected Lti10Service lti10Service;

    @SpyBean
    protected Lti13Service lti13Service;

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
    protected ConversationNotificationService conversationNotificationService;

    @SpyBean
    protected SingleUserNotificationService singleUserNotificationService;

    @SpyBean
    protected JavaMailSender javaMailSender;

    @SpyBean
    protected MailService mailService;

    @SpyBean
    protected GeneralInstantNotificationService generalInstantNotificationService;

    @SpyBean
    protected FirebasePushNotificationService firebasePushNotificationService;

    @SpyBean
    protected ApplePushNotificationService applePushNotificationService;

    @SpyBean
    protected WebsocketMessagingService websocketMessagingService;

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
    protected ParticipantScoreScheduleService participantScoreScheduleService;

    @SpyBean
    protected TextBlockService textBlockService;

    @Autowired
    protected QuizScheduleService quizScheduleService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected HibernateQueryInterceptor queryInterceptor;

    @BeforeEach
    void mockMailService() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void stopQuizScheduler() {
        quizScheduleService.stopSchedule();
        quizScheduleService.clearAllQuizData();
    }

    @AfterEach
    void stopRunningTasks() {
        participantScoreScheduleService.shutdown();
    }

    protected void resetSpyBeans() {
        Mockito.reset(lti10Service, gitService, groupNotificationService, conversationNotificationService, tutorialGroupNotificationService, singleUserNotificationService,
                websocketMessagingService, examAccessService, mailService, instanceMessageSendService, programmingExerciseScheduleService, programmingExerciseParticipationService,
                urlService, scheduleService, participantScoreScheduleService, javaMailSender, programmingTriggerService, zipFileService);
    }

    @Override
    public void mockGetRepositorySlugFromRepositoryUrl(String repositorySlug, VcsRepositoryUri repositoryUrl) {
        // mock both versions to be independent
        doReturn(repositorySlug).when(urlService).getRepositorySlugFromRepositoryUrl(repositoryUrl);
        doReturn(repositorySlug).when(urlService).getRepositorySlugFromRepositoryUrlString(repositoryUrl.toString());
    }

    @Override
    public void mockGetProjectKeyFromRepositoryUrl(String projectKey, VcsRepositoryUri repositoryUrl) {
        doReturn(projectKey).when(urlService).getProjectKeyFromRepositoryUrl(repositoryUrl);
    }

    @Override
    public void mockGetRepositoryPathFromRepositoryUrl(String projectPath, VcsRepositoryUri repositoryUrl) {
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

    /**
     * Provides a list of various user mentions flagged with in indicator whether the user mention is valid
     *
     * @param courseMemberLogin1 login of one course member
     * @param courseMemberLogin2 login of another course member
     * @return list of user mentions and validity flags
     */
    protected static List<Arguments> userMentionProvider(String courseMemberLogin1, String courseMemberLogin2) {
        User courseMember1 = UserFactory.generateActivatedUser(courseMemberLogin1);
        User courseMember2 = UserFactory.generateActivatedUser(courseMemberLogin2);
        User noCourseMember = UserFactory.generateActivatedUser("noCourseMember");

        // First argument is a string containing a user mention
        // Second argument indicates whether the user mention is valid
        return List.of(Arguments.of("no mention", true), // no user mention
                Arguments.of("[user]" + courseMember1.getName() + "(" + courseMember1.getLogin() + ")[/user]", true), // valid mention
                Arguments.of("[user](" + courseMember1.getLogin() + ")[/user]", false), // missing full name
                Arguments.of("[user]" + courseMember1.getName() + "()[/user]", false), // missing login
                Arguments.of("[user]" + courseMember1.getName() + "[/user]", false), // missing login and parentheses
                Arguments.of("[user]" + courseMember2.getName() + "(" + courseMember2.getLogin() + ")[/user][user]" + courseMember1.getName() + "(" + courseMember1.getLogin()
                        + ")[/user]", true), // multiple valid user mentions
                Arguments.of("[user]invalidName(" + courseMember1.getLogin() + ")[/user]", false), // invalid full name
                Arguments.of("[user]" + noCourseMember.getName() + "(" + noCourseMember.getLogin() + ")[/user]", false), // not a course member
                Arguments.of("[user]invalidName[user]" + courseMember1.getName() + "(" + courseMember1.getLogin() + ")[/user](invalid)[/user]", true) // matching only inner

        );
    }
}
