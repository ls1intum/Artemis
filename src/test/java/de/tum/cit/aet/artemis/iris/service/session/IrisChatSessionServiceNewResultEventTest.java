package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.MessageSource;

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

    private User student;

    @BeforeEach
    void setUp() {
        irisSettingsService = mock(IrisSettingsService.class);
        submissionRepository = mock(SubmissionTestRepository.class);
        UserAiPreferenceService userAiPreferenceService = mock(UserAiPreferenceService.class);
        when(userAiPreferenceService.hasOptedIntoLlmUsage(USER_ID)).thenReturn(true);

        irisChatSessionService = new IrisChatSessionService(mock(IrisMessageService.class), mock(IrisMessageRepository.class), mock(LLMTokenUsageService.class),
                irisSettingsService, mock(IrisChatWebsocketService.class), mock(AuthorizationCheckService.class), mock(IrisSessionRepository.class),
                mock(IrisChatSessionRepository.class), mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ProgrammingSubmissionRepository.class),
                mock(IrisRateLimitService.class), JsonObjectMapper.get(), mock(ExerciseRepository.class), submissionRepository, mock(CourseRepository.class),
                Optional.<LectureRepositoryApi>empty(), mock(IrisCitationService.class), mock(MessageSource.class), mock(IrisChatPipelineExecutionService.class),
                mock(PyrisJobService.class), userAiPreferenceService, new IrisProactiveProperties());

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
    void shouldIgnoreResultWhenProgrammingExerciseIsMissing() {
        assertThatCode(() -> irisChatSessionService.handleNewResultEvent(eventFor(null))).doesNotThrowAnyException();

        verify(irisSettingsService, never()).getSettingsForCourse(any(Course.class));
        verify(submissionRepository, never()).findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(any(Long.class));
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
    @CsvSource({ ",", ",true", "90,true", "90,false" })
    void shouldEvaluateOfficialProgressDespiteNewerAthenaResults(Double aiScore, Boolean successful) {
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
        List<Submission> history = IntStream.rangeClosed(1, 3).mapToObj(index -> {
            var submission = new ProgrammingSubmission();
            submission.setId((long) index);
            submission.setParticipation(participation);
            var official = new Result();
            official.setId(index * 2L);
            official.setAssessmentType(AssessmentType.AUTOMATIC);
            official.setScore(index * 20.0);
            var ai = new Result();
            ai.setId(index * 2L + 1);
            ai.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            ai.setScore(aiScore);
            ai.setSuccessful(successful);
            submission.setResults(Set.of(official, ai));
            return (Submission) submission;
        }).toList();
        when(submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(42L)).thenReturn(history);

        // Official scores improve; flat AI scores must neither trigger intervention nor hide the official history.
        assertThatCode(() -> irisChatSessionService.handleNewResultEvent(event)).doesNotThrowAnyException();
        verify(submissionRepository).findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(42L);
    }

}
