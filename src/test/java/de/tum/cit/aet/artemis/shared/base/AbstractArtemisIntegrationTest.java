package de.tum.cit.aet.artemis.shared.base;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.util.List;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.notifications.ConversationNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.GeneralInstantNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.TutorialGroupNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.HibernateQueryInterceptor;
import de.tum.cit.aet.artemis.core.util.QueryCountAssert;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.util.ThrowingProducer;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.lti.service.Lti13Service;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.shared.TestRepositoryConfiguration;
import de.tum.cit.aet.artemis.text.service.TextBlockService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * this test should be completely independent of any profiles or configurations (e.g. VCS, CIS)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@Import(TestRepositoryConfiguration.class)
@AutoConfigureEmbeddedDatabase
public abstract class AbstractArtemisIntegrationTest implements MockDelegate {

    @Value("${server.url}")
    protected String artemisServerUrl;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    // NOTE: we prefer SpyBean over MockBean, because it is more lightweight, we can mock method, but we can also invoke actual methods during testing
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
    protected ModelingSubmissionService modelingSubmissionService;

    @SpyBean
    protected TextSubmissionService textSubmissionService;

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
    protected UriService uriService;

    @SpyBean
    protected ScheduleService scheduleService;

    @SpyBean
    protected ParticipantScoreScheduleService participantScoreScheduleService;

    @SpyBean
    protected TextBlockService textBlockService;

    @SpyBean
    protected CompetencyProgressService competencyProgressService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected HibernateQueryInterceptor queryInterceptor;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected UserTestRepository userTestRepository;

    @Autowired
    protected ExerciseRepository exerciseRepository;

    @Autowired
    protected ResultTestRepository resultRepository;

    @Autowired
    protected CourseTestRepository courseRepository;

    @BeforeEach
    void mockMailService() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void stopQuizScheduler() {
        scheduleService.clearAllTasks();
    }

    @AfterEach
    void stopRunningTasks() {
        participantScoreScheduleService.shutdown();
    }

    protected void resetSpyBeans() {
        Mockito.reset(gitService, groupNotificationService, conversationNotificationService, tutorialGroupNotificationService, singleUserNotificationService,
                websocketMessagingService, examAccessService, mailService, instanceMessageSendService, programmingExerciseScheduleService, programmingExerciseParticipationService,
                uriService, scheduleService, participantScoreScheduleService, javaMailSender, programmingTriggerService, zipFileService, competencyProgressService);
    }

    @Override
    public void mockGetRepositorySlugFromRepositoryUri(String repositorySlug, VcsRepositoryUri repositoryUri) {
        // mock both versions to be independent
        doReturn(repositorySlug).when(uriService).getRepositorySlugFromRepositoryUri(repositoryUri);
        doReturn(repositorySlug).when(uriService).getRepositorySlugFromRepositoryUriString(repositoryUri.toString());
    }

    @Override
    public void mockGetProjectKeyFromRepositoryUri(String projectKey, VcsRepositoryUri repositoryUri) {
        doReturn(projectKey).when(uriService).getProjectKeyFromRepositoryUri(repositoryUri);
    }

    @Override
    public void mockGetRepositoryPathFromRepositoryUri(String projectPath, VcsRepositoryUri repositoryUri) {
        doReturn(projectPath).when(uriService).getRepositoryPathFromRepositoryUri(repositoryUri);
    }

    @Override
    public void mockGetProjectKeyFromAnyUrl(String projectKey) {
        doReturn(projectKey).when(uriService).getProjectKeyFromRepositoryUri(any());
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
