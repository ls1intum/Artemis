package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentReviewService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

class IrisAskUserServiceTest {

    private IrisSettingsService irisSettingsService;

    private AuthorizationCheckService authCheckService;

    private IrisSessionRepository irisSessionRepository;

    private IrisChatSessionRepository irisChatSessionRepository;

    private IrisChatSessionService irisChatSessionService;

    private PyrisPipelineService pyrisPipelineService;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    private UserTestRepository userRepository;

    private IrisAssessmentReviewService irisAssessmentReviewService;

    private TaskScheduler taskScheduler;

    private IrisAskUserService irisAskUserService;

    @BeforeEach
    void setUp() {
        irisSettingsService = mock(IrisSettingsService.class);
        authCheckService = mock(AuthorizationCheckService.class);
        irisSessionRepository = mock(IrisSessionRepository.class);
        irisChatSessionRepository = mock(IrisChatSessionRepository.class);
        irisChatSessionService = mock(IrisChatSessionService.class);
        pyrisPipelineService = mock(PyrisPipelineService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        programmingExerciseStudentParticipationRepository = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        programmingSubmissionRepository = mock(ProgrammingSubmissionTestRepository.class);
        userRepository = mock(UserTestRepository.class);
        irisAssessmentReviewService = mock(IrisAssessmentReviewService.class);
        taskScheduler = mock(TaskScheduler.class);

        irisAskUserService = new IrisAskUserService(irisSettingsService, authCheckService, irisSessionRepository, irisChatSessionRepository, irisChatSessionService,
                pyrisPipelineService, programmingExerciseRepository, programmingExerciseStudentParticipationRepository, programmingSubmissionRepository, userRepository,
                irisAssessmentReviewService, taskScheduler);

        // the ConcurrentHashMap backing the quiz timers rejects null values, so give schedule(...) a non-null default return
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
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

    @Test
    void startInClassQuizForCurrentSessionValidatesAvailabilityThenStartsInClassQuiz() {
        var fixture = stubAskUserPipeline();

        var session = irisAskUserService.startInClassQuizForCurrentSession(fixture.exercise(), fixture.user());

        verify(irisAssessmentReviewService).validateInClassQuizIsAvailableOrElseThrow(fixture.exercise());
        assertThat(session.isInClassQuiz()).isTrue();
        assertThat(session.isInAskUserModePipeline()).isTrue();
    }

    @Test
    void startInClassQuizForCurrentSessionPropagatesConflictWhenTimerNotAvailable() {
        var fixture = stubAskUserPipeline();
        doThrow(new ConflictException("The in-class quiz timer has expired or is not active", "Iris", "irisInClassQuizExpired")).when(irisAssessmentReviewService)
                .validateInClassQuizIsAvailableOrElseThrow(fixture.exercise());

        assertThatThrownBy(() -> irisAskUserService.startInClassQuizForCurrentSession(fixture.exercise(), fixture.user())).isInstanceOf(ConflictException.class);

        verify(irisChatSessionRepository, never()).save(any());
    }

    @Test
    void registerDefocusIsNoOpWhenPipelineNotActive() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        var session = stubCurrentSession(exercise, user, false, false);

        irisAskUserService.registerDefocusForCurrentSession(exercise, user);

        assertThat(session.isInAskUserModePipeline()).isFalse();
        verify(irisSessionRepository, never()).findByIdWithMessagesAndContents(session.getId());
    }

    @Test
    void registerDefocusSendsTabDefocusEventWhenPipelineActive() {
        var course = new Course();
        course.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        exercise.setCourse(course);
        var user = optedInUser(3L);
        var session = stubCurrentSession(exercise, user, true, false);

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(user);
        var submission = new ProgrammingSubmission();
        submission.setId(6L);
        participation.addSubmission(submission);

        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId())).thenReturn(exercise);
        when(programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin())).thenReturn(List.of(participation));
        when(programmingSubmissionRepository
                .findLatestSubmissionWithEagerResultsAndFeedbacksAndBuildLogsBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId()))
                .thenReturn(Optional.of(submission));
        when(userRepository.findByIdElseThrow(user.getId())).thenReturn(user);
        when(irisSessionRepository.findByIdWithMessagesAndContents(session.getId())).thenReturn(session);
        when(pyrisPipelineService.executeAskUserPipeline(anyString(), any(), any(), any(), any(), any())).thenReturn(true);

        irisAskUserService.registerDefocusForCurrentSession(exercise, user);

        verify(pyrisPipelineService, timeout(1000)).executeAskUserPipeline(anyString(), same(submission), same(exercise), same(session),
                eq(Optional.of(IrisPipeEvent.TAB_DEFOCUS.name())), any());
    }

    @Test
    void startTimerForCurrentSessionReturnsEmptyTimerWhenPipelineNotActive() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        stubCurrentSession(exercise, user, false, false);

        var timer = irisAskUserService.startTimerForCurrentSession(exercise, user);

        assertThat(timer.timerExpiresAt()).isNull();
        assertThat(timer.timeLimit()).isZero();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void startTimerForCurrentSessionSchedulesTimerWhenPipelineActive() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        stubCurrentSession(exercise, user, true, false);
        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());

        var timer = irisAskUserService.startTimerForCurrentSession(exercise, user);

        assertThat(timer.timerExpiresAt()).isNotNull();
        assertThat(timer.timeLimit()).isEqualTo(IrisCourseSettings.defaultSettings().askUserModeSettings().timeLimitQuestion());
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void stopTimerForCurrentSessionCancelsPreviouslyScheduledTimer() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        stubCurrentSession(exercise, user, true, false);
        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());
        var scheduledFuture = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(scheduledFuture);
        irisAskUserService.startTimerForCurrentSession(exercise, user);

        irisAskUserService.stopTimerForCurrentSession(exercise, user);

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void isQuizAlreadyDoneReflectsWhetherAssessmentHasAVerdict() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        var participation = new ProgrammingExerciseStudentParticipation();
        var assessment = new IrisAssessment(user, exercise);
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        participation.setIrisAssessment(assessment);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false, false))
                .thenReturn(Optional.of(participation));

        assertThat(irisAskUserService.isQuizAlreadyDone(exercise, user, false)).isTrue();
    }

    @Test
    void isQuizAlreadyDoneIsFalseWhenNoAssessmentExists() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false, false))
                .thenReturn(Optional.empty());

        assertThat(irisAskUserService.isQuizAlreadyDone(exercise, user, false)).isFalse();
    }

    @Test
    void isQuizStartedDistinguishesRegularAndInClassQuizzes() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        stubCurrentSession(exercise, user, true, false);

        assertThat(irisAskUserService.isQuizStarted(exercise, user, false)).isTrue();
        assertThat(irisAskUserService.isQuizStarted(exercise, user, true)).isFalse();
    }

    @Test
    void isQuizStartedReturnsTrueForInClassOnlyWhenSessionIsAnInClassQuiz() {
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var user = optedInUser(3L);
        stubCurrentSession(exercise, user, true, true);

        assertThat(irisAskUserService.isQuizStarted(exercise, user, true)).isTrue();
        assertThat(irisAskUserService.isQuizStarted(exercise, user, false)).isFalse();
    }

    @Test
    void requestAndHandleResponseThrowsWhenSessionNotInAskUserPipeline() {
        var session = new IrisChatSession();
        session.setInAskUserModePipeline(false);

        assertThatThrownBy(() -> irisAskUserService.requestAndHandleResponse(session)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(pyrisPipelineService);
    }

    @Test
    void handleQuizFinishedSwallowsExceptionFromAssessmentReviewService() {
        var fixture = stubAskUserPipeline();
        fixture.session().setInAskUserModePipeline(true);
        var job = new ChatJob("finish-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        var verdictDto = new IrisVerdictDTO(IrisVerdict.SUSPICIOUS, "reason");
        var statusUpdate = new PyrisChatStatusUpdateDTO("Quiz finished.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.QUIZ_FINISHED.name(), verdictDto);
        doThrow(new RuntimeException("boom")).when(irisAssessmentReviewService).saveAndHandleVerdict(fixture.user(), fixture.exercise(), verdictDto, false);

        assertThatCode(() -> irisAskUserService.handleStatusUpdate(job, statusUpdate)).doesNotThrowAnyException();

        assertThat(fixture.session().isInAskUserModePipeline()).isFalse();
    }

    @Test
    void handleNextQuestionSwallowsExceptionFromAssessmentReviewService() {
        var fixture = stubAskUserPipeline();
        var job = new ChatJob("next-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        var verdictDto = new IrisVerdictDTO(IrisVerdict.UNSUSPICIOUS, "reason");
        var statusUpdate = new PyrisChatStatusUpdateDTO("Next question.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.NEXT_QUESTION.name(), verdictDto);
        doThrow(new RuntimeException("boom")).when(irisAssessmentReviewService).addReasoning(fixture.user(), fixture.exercise(), "reason", false);

        assertThatCode(() -> irisAskUserService.handleStatusUpdate(job, statusUpdate)).doesNotThrowAnyException();

        assertThat(fixture.session().getQuestionsAsked()).isZero();
    }

    @Test
    void handleFirstQuestionIncrementsQuestionsAskedAndSavesSession() {
        var fixture = stubAskUserPipeline();
        fixture.session().setQuestionsAsked(0);
        var job = new ChatJob("first-question-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null,
                ChatJob.ASK_USER_PIPELINE_NAME);
        var statusUpdate = new PyrisChatStatusUpdateDTO("First question.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.FIRST_QUESTION.name(), null);

        irisAskUserService.handleStatusUpdate(job, statusUpdate);

        assertThat(fixture.session().getQuestionsAsked()).isEqualTo(1);
        verify(irisChatSessionRepository).save(fixture.session());
    }

    @Test
    void handleFirstQuestionSwallowsExceptionWhenAskUserModeGetsDisabled() {
        var fixture = stubAskUserPipeline();
        fixture.session().setQuestionsAsked(0);
        var job = new ChatJob("first-question-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null,
                ChatJob.ASK_USER_PIPELINE_NAME);
        var statusUpdate = new PyrisChatStatusUpdateDTO("First question.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.FIRST_QUESTION.name(), null);
        // first call (resolving the exercise for the session) succeeds, second call (inside handleFirstQuestion) throws
        doNothing().doThrow(new AccessForbiddenAlertException("disabled", "Iris", "iris.ask_user_mode_disabled")).when(irisSettingsService)
                .ensureAskUserModeEnabledForExerciseOrElseThrow(fixture.exercise());

        assertThatCode(() -> irisAskUserService.handleStatusUpdate(job, statusUpdate)).doesNotThrowAnyException();

        assertThat(fixture.session().getQuestionsAsked()).isZero();
        verify(irisChatSessionRepository, never()).save(fixture.session());
    }

    @Test
    void unknownAskUserEventStringIsIgnoredWithoutThrowing() {
        var fixture = stubAskUserPipeline();
        var job = new ChatJob("garbage-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        var statusUpdate = new PyrisChatStatusUpdateDTO("some result", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null, "not-a-real-event",
                null);

        assertThatCode(() -> irisAskUserService.handleStatusUpdate(job, statusUpdate)).doesNotThrowAnyException();

        verifyNoInteractions(irisAssessmentReviewService);
    }

    private User optedInUser(long id) {
        var user = new User();
        user.setId(id);
        user.setLogin("student" + id);
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        user.setSelectedLLMUsageTimestamp(ZonedDateTime.now());
        return user;
    }

    private IrisChatSession stubCurrentSession(ProgrammingExercise exercise, User user, boolean inPipeline, boolean inClassQuiz) {
        if (exercise.getCourseViaExerciseGroupOrCourseMember() == null) {
            var course = new Course();
            course.setId(100L);
            exercise.setCourse(course);
        }
        var session = new IrisChatSession(exercise, user, PROGRAMMING_EXERCISE_CHAT);
        session.setId(50L);
        session.setInAskUserModePipeline(inPipeline);
        session.setInClassQuiz(inClassQuiz);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(PROGRAMMING_EXERCISE_CHAT, exercise.getId(), user)).thenReturn(session);
        return session;
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
        when(programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin())).thenReturn(List.of(participation));
        when(programmingSubmissionRepository
                .findLatestSubmissionWithEagerResultsAndFeedbacksAndBuildLogsBeforeExerciseDueDateAndResultScoreGreaterThanZeroByParticipationId(participation.getId()))
                .thenReturn(Optional.of(submission));
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
