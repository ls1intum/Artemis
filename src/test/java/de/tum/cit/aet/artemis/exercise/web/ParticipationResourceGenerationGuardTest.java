package de.tum.cit.aet.artemis.exercise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.FeedbackRequestService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Unit test for the guard that refuses to start a participation while a Hyperion run owns the programming exercise. The rest of {@link ParticipationResource} is covered by
 * the participation integration tests; this test isolates the guard because a real run cannot be held open inside them.
 */
class ParticipationResourceGenerationGuardTest {

    private static final long EXERCISE_ID = 42L;

    private final ParticipationService participationService = mock();

    private final ExerciseTestRepository exerciseRepository = mock();

    private final AuthorizationCheckService authCheckService = mock();

    private final UserTestRepository userRepository = mock();

    private final FeatureToggleService featureToggleService = mock();

    private final HyperionExerciseMutationApi hyperionExerciseMutationApi = mock();

    private final User student = new User();

    private ProgrammingExercise exercise;

    private ParticipationResource resource;

    @BeforeEach
    void setUp() {
        resource = new ParticipationResource(participationService, exerciseRepository, mock(ProgrammingExerciseRepository.class), authCheckService, userRepository,
                mock(StudentParticipationRepository.class), mock(TeamRepository.class), featureToggleService, mock(ProgrammingExerciseStudentParticipationRepository.class),
                mock(SubmissionRepository.class), mock(ExerciseDateService.class), mock(ParticipationAuthorizationService.class), Optional.of(mock(StudentExamApi.class)),
                mock(ModuleFeatureService.class), mock(FeedbackRequestService.class), mock(CourseAthenaConfigRepository.class), Optional.of(hyperionExerciseMutationApi));

        student.setId(7L);
        student.setLogin("student");
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(new Course());
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise.setDueDate(ZonedDateTime.now().minusHours(1));

        when(exerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(exercise);
        when(userRepository.getUserWithAuthorities()).thenReturn(student);
        when(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).thenReturn(true);
    }

    @Test
    void startParticipation_whileGenerationOwnsExercise_returnsConflictBeforeCreatingAnything() {
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(hyperionExerciseMutationApi.isGenerationActive(EXERCISE_ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.startParticipation(EXERCISE_ID))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("exerciseGenerationRunning"));

        verify(participationService, never()).startExercise(any(), any(), eq(true));
    }

    @Test
    void startParticipation_withoutActiveGeneration_startsTheExercise() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        when(hyperionExerciseMutationApi.isGenerationActive(EXERCISE_ID)).thenReturn(false);
        StudentParticipation participation = participationWithExercise();
        when(participationService.startExercise(exercise, student, true)).thenReturn(participation);

        var response = resource.startParticipation(EXERCISE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(participation);
        // The guard is read-only: a student start must never take the mutation slot and serialize the other students behind it.
        verify(hyperionExerciseMutationApi, never()).claimExternalMutationSlot(EXERCISE_ID);
    }

    @Test
    void startPracticeParticipation_whileGenerationOwnsExercise_returnsConflictBeforeCreatingAnything() {
        when(participationService.findOneGradedByExerciseAndParticipant(exercise, student)).thenReturn(Optional.empty());
        when(hyperionExerciseMutationApi.isGenerationActive(EXERCISE_ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.startPracticeParticipation(EXERCISE_ID, false))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("exerciseGenerationRunning"));

        verify(participationService, never()).startPracticeMode(any(), any(), any(), eq(false));
    }

    @Test
    void startPracticeParticipation_withoutActiveGeneration_startsPracticeMode() throws Exception {
        when(participationService.findOneGradedByExerciseAndParticipant(exercise, student)).thenReturn(Optional.empty());
        when(hyperionExerciseMutationApi.isGenerationActive(EXERCISE_ID)).thenReturn(false);
        StudentParticipation participation = participationWithExercise();
        when(participationService.startPracticeMode(exercise, student, Optional.empty(), false)).thenReturn(participation);

        var response = resource.startPracticeParticipation(EXERCISE_ID, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(participation);
        verify(hyperionExerciseMutationApi, never()).claimExternalMutationSlot(EXERCISE_ID);
    }

    @Test
    void startParticipation_withoutHyperionApi_isUnaffected() throws Exception {
        resource = new ParticipationResource(participationService, exerciseRepository, mock(ProgrammingExerciseRepository.class), authCheckService, userRepository,
                mock(StudentParticipationRepository.class), mock(TeamRepository.class), featureToggleService, mock(ProgrammingExerciseStudentParticipationRepository.class),
                mock(SubmissionRepository.class), mock(ExerciseDateService.class), mock(ParticipationAuthorizationService.class), Optional.of(mock(StudentExamApi.class)),
                mock(ModuleFeatureService.class), mock(FeedbackRequestService.class), mock(CourseAthenaConfigRepository.class), Optional.empty());
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        StudentParticipation participation = participationWithExercise();
        when(participationService.startExercise(exercise, student, true)).thenReturn(participation);

        var response = resource.startParticipation(EXERCISE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private StudentParticipation participationWithExercise() {
        StudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(99L);
        participation.setExercise(exercise);
        return participation;
    }
}
