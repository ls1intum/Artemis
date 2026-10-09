package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

/**
 * Unit tests for the course resolution of {@link IrisChatSessionService#handleNewResultEvent}: the course of the
 * exercise is required to look up the Iris settings, so an exercise whose course cannot be resolved fails loudly and
 * a course with Iris disabled stops the legacy trigger before any session work happens.
 */
class IrisChatSessionServiceNewResultEventTest {

    private static final long USER_ID = 5L;

    private IrisSettingsService irisSettingsService;

    private SubmissionTestRepository submissionRepository;

    private IrisChatSessionService irisChatSessionService;

    private IrisChatSessionRepository irisChatSessionRepository;

    private IrisChatPipelineExecutionService chatPipelineExecutionService;

    private User student;

    @BeforeEach
    void setUp() {
        irisSettingsService = mock(IrisSettingsService.class);
        submissionRepository = mock(SubmissionTestRepository.class);
        irisChatSessionRepository = mock(IrisChatSessionRepository.class);
        chatPipelineExecutionService = mock(IrisChatPipelineExecutionService.class);
        UserAiPreferenceService userAiPreferenceService = mock(UserAiPreferenceService.class);
        when(userAiPreferenceService.hasOptedIntoLlmUsage(USER_ID)).thenReturn(true);

        irisChatSessionService = new IrisChatSessionService(mock(IrisMessageService.class), mock(IrisMessageRepository.class), mock(LLMTokenUsageService.class),
                irisSettingsService, mock(IrisChatWebsocketService.class), mock(AuthorizationCheckService.class), mock(IrisSessionRepository.class), irisChatSessionRepository,
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ProgrammingSubmissionRepository.class), mock(IrisRateLimitService.class),
                JsonObjectMapper.get(), mock(ExerciseRepository.class), submissionRepository, mock(CourseRepository.class), Optional.<LectureRepositoryApi>empty(),
                mock(IrisCitationService.class), mock(MessageSource.class), chatPipelineExecutionService, mock(PyrisJobService.class), userAiPreferenceService,
                new IrisProactiveProperties());

        student = new User();
        student.setId(USER_ID);
    }

    private NewResultEvent eventFor(ProgrammingExercise exercise) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setParticipant(student);
        participation.setExercise(exercise);
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        var result = new Result();
        result.setSubmission(submission);
        return new NewResultEvent(result);
    }

    @Test
    void shouldFailWhenTheCourseOfTheExerciseCannotBeResolved() {
        var exercise = new ProgrammingExercise();
        exercise.setId(11L);

        assertThatThrownBy(() -> irisChatSessionService.handleNewResultEvent(eventFor(exercise))).isInstanceOf(IllegalStateException.class).hasMessageContaining("11");

        verify(irisSettingsService, never()).getSettingsForCourse(any(Course.class));
    }

    @Test
    void shouldStopBeforeAnySessionWorkWhenIrisIsDisabledForTheCourse() {
        var course = new Course();
        course.setId(3L);
        var exercise = new ProgrammingExercise();
        exercise.setId(11L);
        exercise.setCourse(course);
        var settings = mock(IrisCourseSettings.class);
        when(settings.enabled()).thenReturn(false);
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(settings);

        assertThatCode(() -> irisChatSessionService.handleNewResultEvent(eventFor(exercise))).doesNotThrowAnyException();

        verify(irisSettingsService).getSettingsForCourse(course);
        verify(submissionRepository, never()).findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(any(Long.class));
    }

    @ParameterizedTest
    @CsvSource({ ",,false", ",true,false", "90,true,false", "90,false,false", ",,true", ",true,true", "90,true,true", "90,false,true" })
    void shouldEvaluateOfficialProgressDespiteNewerAthenaResults(Double aiScore, Boolean successful, boolean stalled) {
        var course = new Course();
        course.setId(3L);
        var exercise = new ProgrammingExercise();
        exercise.setId(11L);
        exercise.setCourse(course);
        var settings = mock(IrisCourseSettings.class);
        when(settings.enabled()).thenReturn(true);
        when(settings.legacyBuildTriggersEffective()).thenReturn(true);
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(settings);
        var event = eventFor(exercise);
        var participation = event.getEventObject().getSubmission().getParticipation();
        participation.setId(42L);
        var session = mock(IrisChatSession.class);
        when(session.getMode()).thenReturn(IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        when(irisChatSessionRepository.findLatestByEntityIdAndChatModeAndUserIdWithMessages(11L, IrisChatMode.PROGRAMMING_EXERCISE_CHAT, USER_ID, Pageable.ofSize(1)))
                .thenReturn(List.of(session));
        List<Submission> history = IntStream.rangeClosed(1, 3).mapToObj(index -> {
            var submission = new ProgrammingSubmission();
            submission.setId((long) index);
            submission.setParticipation(participation);
            var official = new Result();
            official.setId(index * 2L);
            official.setAssessmentType(AssessmentType.AUTOMATIC);
            official.setScore(stalled ? 20.0 : index * 20.0);
            var ai = new Result();
            ai.setId(index * 2L + 1);
            ai.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            ai.setScore(stalled && aiScore != null ? Double.valueOf(index * 20.0) : aiScore);
            ai.setSuccessful(successful);
            submission.setResults(Set.of(official, ai));
            return (Submission) submission;
        }).toList();
        when(submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(42L)).thenReturn(history);

        // Only official progress determines intervention, regardless of pending, completed or failed AI feedback.
        assertThatCode(() -> irisChatSessionService.handleNewResultEvent(event)).doesNotThrowAnyException();
        verify(submissionRepository).findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(42L);
        if (stalled) {
            verify(chatPipelineExecutionService, timeout(1000)).execute(eq(session), eq(Optional.of("progress_stalled")), eq(Optional.of(settings)),
                    eq(Optional.of((ProgrammingSubmission) event.getEventObject().getSubmission())), eq(Map.of()), eq(List.of()));
        }
        else {
            // Session lookup is synchronous and precedes scheduling the pipeline, so this also detects an incorrectly scheduled intervention.
            verify(irisChatSessionRepository, never()).findLatestByEntityIdAndChatModeAndUserIdWithMessages(any(), any(), any(), any());
            verify(chatPipelineExecutionService, never()).execute(any(), any(), any(), any(), any(), any());
        }
    }

}
