package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode.PROGRAMMING_EXERCISE_CHAT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
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
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAssessmentService;
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

class IrisPromptUserServiceTest {

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

    private IrisAssessmentService irisAssessmentService;

    private IrisPromptUserService irisPromptUserService;

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
        irisAssessmentService = mock(IrisAssessmentService.class);

        irisPromptUserService = new IrisPromptUserService(irisSettingsService, authCheckService, irisSessionRepository, irisChatSessionRepository, irisChatSessionService,
                pyrisPipelineService, programmingExerciseRepository, programmingExerciseStudentParticipationRepository, programmingSubmissionRepository, userRepository,
                irisAssessmentService, mock(IrisAssessmentQuizWebsocketService.class), mock(TaskScheduler.class));
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

        irisPromptUserService.handleNewResultEvent(new NewResultEvent(result));

        verify(irisChatSessionService).shouldSendProgressStalledEvent(participation);
        verifyNoInteractions(programmingExerciseStudentParticipationRepository);
        verifyNoInteractions(pyrisPipelineService);
    }

    @Test
    void startPromptingModeStartsOnlyUserInitiatesPromptingPipeline() {
        var fixture = stubPromptUserPipeline();

        irisPromptUserService.startPromptingModeForCurrentSession(fixture.exercise(), fixture.user());

        verify(pyrisPipelineService, timeout(1000)).executePromptUserPipeline(anyString(), anyString(), same(fixture.submission()), same(fixture.exercise()),
                same(fixture.session()), eq(Optional.of(IrisPipeEvent.USER_INITIATES_PROMPTING.name())), any());
        verify(pyrisPipelineService, after(250).times(1)).executePromptUserPipeline(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void userInitiatesPromptingResultStartsFirstQuestionPipeline() {
        var fixture = stubPromptUserPipeline();
        var job = new ChatJob("prompt-user-run", fixture.course().getId(), fixture.session().getId(), fixture.exercise().getId(), null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("Prompting starts now.", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.USER_INITIATES_PROMPTING.name(), null);

        irisPromptUserService.handleStatusUpdate(job, statusUpdate);

        verify(pyrisPipelineService, timeout(1000)).executePromptUserPipeline(anyString(), anyString(), same(fixture.submission()), same(fixture.exercise()),
                same(fixture.session()), eq(Optional.of(IrisPipeEvent.FIRST_QUESTION.name())), any());
    }

    private PromptUserPipelineFixture stubPromptUserPipeline() {
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

        return new PromptUserPipelineFixture(course, exercise, user, session, submission);
    }

    private record PromptUserPipelineFixture(Course course, ProgrammingExercise exercise, User user, IrisChatSession session, ProgrammingSubmission submission) {
    }
}
