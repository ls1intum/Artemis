package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisAssessmentQuizWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

class IrisAskUserServiceTest {

    private IrisSettingsService irisSettingsService;

    private AuthorizationCheckService authCheckService;

    private IrisSessionRepository irisSessionRepository;

    private IrisChatSessionRepository irisChatSessionRepository;

    private IrisChatSessionService irisChatSessionService;

    private PyrisPipelineService pyrisPipelineService;

    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    private UserRepository userRepository;

    private IrisAssessmentReviewService irisAssessmentReviewService;

    private IrisAskUserService irisAskUserService;

    @BeforeEach
    void setUp() {
        irisSettingsService = mock(IrisSettingsService.class);
        authCheckService = mock(AuthorizationCheckService.class);
        irisSessionRepository = mock(IrisSessionRepository.class);
        irisChatSessionRepository = mock(IrisChatSessionRepository.class);
        irisChatSessionService = mock(IrisChatSessionService.class);
        pyrisPipelineService = mock(PyrisPipelineService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        programmingExerciseStudentParticipationRepository = mock(ProgrammingExerciseStudentParticipationRepository.class);
        programmingSubmissionRepository = mock(ProgrammingSubmissionRepository.class);
        userRepository = mock(UserRepository.class);
        irisAssessmentReviewService = mock(IrisAssessmentReviewService.class);

        irisAskUserService = new IrisAskUserService(irisSettingsService, authCheckService, irisSessionRepository, irisChatSessionRepository, irisChatSessionService,
                pyrisPipelineService, programmingExerciseRepository, programmingExerciseStudentParticipationRepository, programmingSubmissionRepository, userRepository,
                irisAssessmentReviewService, mock(IrisAssessmentQuizWebsocketService.class), mock(TaskScheduler.class));
    }

    @Test
    void handleNewResultEventDoesNotTriggerBuildWithPointsWhenProgressStalledEventWouldBeSent() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setCourse(new Course());

        var user = new User();
        user.setId(2L);
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        user.setSelectedLLMUsageTimestamp(ZonedDateTime.now());

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(3L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(user);

        var submission = new ProgrammingSubmission();
        submission.setId(4L);
        submission.setParticipation(participation);

        var result = new Result();
        result.setId(5L);
        result.setScore(40.0);
        result.setSubmission(submission);
        submission.addResult(result);

        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());
        when(irisChatSessionService.shouldSendProgressStalledEvent(participation)).thenReturn(true);

        irisAskUserService.handleNewResultEvent(new NewResultEvent(result));

        verify(irisChatSessionService).shouldSendProgressStalledEvent(participation);
        verifyNoInteractions(programmingExerciseStudentParticipationRepository);
        verifyNoInteractions(pyrisPipelineService);
    }

    @Test
    void startPromptingModeStartsOnlyUserInitiatesPromptingPipeline() {
        var fixture = stubAskUserPipeline();

        irisAskUserService.startQuizForCurrentSession(fixture.exercise(), fixture.user());

        verify(pyrisPipelineService, timeout(1000)).executeAskUserPipeline(anyString(), same(fixture.submission()), same(fixture.exercise()), same(fixture.session()),
                eq(Optional.of(IrisPipeEvent.USER_STARTS_QUIZ.name())), any());
        verify(pyrisPipelineService, after(250).times(1)).executeAskUserPipeline(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void userInitiatesPromptingResultStartsFirstQuestionPipeline() {
        var fixture = stubAskUserPipeline();
        var job = new ChatJob("prompt-user-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("Prompting starts now.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.USER_STARTS_QUIZ.name(), null);

        irisAskUserService.handleStatusUpdate(job, statusUpdate);

        verify(pyrisPipelineService, timeout(1000)).executeAskUserPipeline(anyString(), same(fixture.submission()), same(fixture.exercise()), same(fixture.session()),
                eq(Optional.of(IrisPipeEvent.FIRST_QUESTION.name())), any());
    }

    @Test
    void failedAskUserJobResetsActiveQuizState() {
        var exercise = new ProgrammingExercise();
        exercise.setId(3L);
        var user = new User();
        user.setId(4L);
        var session = new IrisChatSession();
        session.setId(2L);
        session.setEntityId(exercise.getId());
        session.setUserId(user.getId());
        session.setInAskUserModePipeline(true);
        session.setInClassQuiz(true);
        session.setQuestionsAsked(3);
        var job = new ChatJob("ask-user-run", 1L, session.getId(), exercise.getId(), null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        when(irisChatSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findByIdElseThrow(user.getId())).thenReturn(user);
        when(programmingExerciseRepository.findByIdElseThrow(exercise.getId())).thenReturn(exercise);

        assertThat(irisAskUserService.resetAskUserPipelineAfterPyrisFailure(job)).isTrue();

        assertThat(session.isInAskUserModePipeline()).isFalse();
        assertThat(session.isInClassQuiz()).isFalse();
        assertThat(session.getQuestionsAsked()).isZero();
        verify(irisChatSessionRepository).save(session);
        verify(irisAssessmentReviewService).resetVerdictAndReasoning(user, exercise, true);
    }

    @Test
    void buildWithPointsResetsActiveRegularQuizStateBeforeRunningBuildWithPointsPipeline() {
        var fixture = stubBuildWithPointsPipeline(false);

        irisAskUserService.handleNewResultEvent(new NewResultEvent(fixture.result()));

        assertThat(fixture.session().isInAskUserModePipeline()).isFalse();
        assertThat(fixture.session().isInClassQuiz()).isFalse();
        assertThat(fixture.session().getQuestionsAsked()).isZero();
        verify(irisChatSessionRepository).save(fixture.session());
        verify(irisAssessmentReviewService).resetVerdictAndReasoning(fixture.user(), fixture.exercise(), false);
        verify(irisAssessmentReviewService, never()).createNewAssessment(fixture.participation());
        verify(pyrisPipelineService, timeout(1000)).executeAskUserPipeline(anyString(), same(fixture.submission()), same(fixture.exercise()), same(fixture.session()),
                eq(Optional.of(IrisPipeEvent.BUILD_WITH_POINTS.name())), any());
    }

    @Test
    void buildWithPointsDoesNotResetInClassQuizState() {
        var fixture = stubBuildWithPointsPipeline(true);

        irisAskUserService.handleNewResultEvent(new NewResultEvent(fixture.result()));

        assertThat(fixture.session().isInAskUserModePipeline()).isTrue();
        assertThat(fixture.session().isInClassQuiz()).isTrue();
        assertThat(fixture.session().getQuestionsAsked()).isEqualTo(2);
        verifyNoInteractions(pyrisPipelineService);
        verify(irisAssessmentReviewService, never()).resetVerdictAndReasoning(fixture.user(), fixture.exercise(), true);
    }

    @Test
    void regularChatJobDoesNotResetQuizState() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);

        assertThat(irisAskUserService.resetAskUserPipelineAfterPyrisFailure(job)).isFalse();

        verifyNoInteractions(irisChatSessionRepository);
    }

    private AskUserPipelineFixture stubAskUserPipeline() {
        var course = new Course();
        course.setId(1L);

        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        exercise.setCourse(course);

        var user = new User();
        user.setId(3L);
        user.setLogin("student1");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        user.setSelectedLLMUsageTimestamp(ZonedDateTime.now());

        var session = new IrisChatSession(exercise, user, PROGRAMMING_EXERCISE_CHAT);
        session.setId(4L);

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(user);

        var submission = new ProgrammingSubmission();
        submission.setId(6L);
        participation.addSubmission(submission);

        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());
        when(irisChatSessionService.findOrCreateEmptySession(course.getId(), user)).thenReturn(session);
        when(irisSessionRepository.findByIdWithMessagesAndContents(session.getId())).thenReturn(session);
        when(irisChatSessionRepository.findByIdElseThrow(session.getId())).thenReturn(session);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId())).thenReturn(exercise);
        when(programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin()))
                .thenReturn(List.of(participation));
        when(programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findByIdElseThrow(user.getId())).thenReturn(user);
        when(pyrisPipelineService.executeAskUserPipeline(anyString(), any(), any(), any(), any(), any())).thenReturn(true);

        return new AskUserPipelineFixture(course, exercise, user, session, submission);
    }

    private BuildWithPointsFixture stubBuildWithPointsPipeline(boolean inClassQuiz) {
        var course = new Course();
        course.setId(1L);

        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        exercise.setCourse(course);

        var user = new User();
        user.setId(3L);
        user.setLogin("student1");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        user.setSelectedLLMUsageTimestamp(ZonedDateTime.now());

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(4L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(user);

        var submission = new ProgrammingSubmission();
        submission.setId(5L);
        participation.addSubmission(submission);

        var result = new Result();
        result.setId(6L);
        result.setScore(80.0);
        result.setSubmission(submission);
        submission.addResult(result);

        var session = new IrisChatSession(exercise, user, PROGRAMMING_EXERCISE_CHAT);
        session.setId(7L);
        session.setInAskUserModePipeline(true);
        session.setInClassQuiz(inClassQuiz);
        session.setQuestionsAsked(2);

        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());
        when(irisChatSessionService.shouldSendProgressStalledEvent(participation)).thenReturn(false);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentById(participation.getId())).thenReturn(Optional.of(participation));
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user)).thenReturn(session);
        when(userRepository.findByIdElseThrow(user.getId())).thenReturn(user);
        when(programmingExerciseRepository.findByIdElseThrow(exercise.getId())).thenReturn(exercise);

        if (!inClassQuiz) {
            when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId())).thenReturn(exercise);
            when(programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(submission.getId())).thenReturn(Optional.of(submission));
            when(irisSessionRepository.findByIdWithMessagesAndContents(session.getId())).thenReturn(session);
            when(pyrisPipelineService.executeAskUserPipeline(anyString(), any(), any(), any(), any(), any())).thenReturn(true);
        }

        return new BuildWithPointsFixture(course, exercise, user, participation, submission, result, session);
    }

    private record AskUserPipelineFixture(Course course, ProgrammingExercise exercise, User user, IrisChatSession session, ProgrammingSubmission submission) {
    }

    private record BuildWithPointsFixture(Course course, ProgrammingExercise exercise, User user, ProgrammingExerciseStudentParticipation participation,
            ProgrammingSubmission submission, Result result, IrisChatSession session) {
    }
}
