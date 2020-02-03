package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;

public class ExerciseTest {

    @Mock
    private Exercise exercise;

    private List<StudentParticipation> studentParticipations;

    @Mock
    private StudentParticipation studentParticipationInitialized;

    @Mock
    private StudentParticipation studentParticipationInactive;

    @Mock
    private StudentParticipation studentParticipationFinished;

    @Mock
    private StudentParticipation studentParticipationUninitialized;

    @Mock
    private Submission submission1;

    @Mock
    private Submission submission2;

    @Mock
    private Submission submission3;

    @Mock
    private Result ratedResult;

    @Mock
    private Result unratedResult;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        exercise = mock(Exercise.class, CALLS_REAL_METHODS);

        when(studentParticipationInitialized.getInitializationState()).thenReturn(InitializationState.INITIALIZED);
        when(studentParticipationInactive.getInitializationState()).thenReturn(InitializationState.INACTIVE);
        when(studentParticipationFinished.getInitializationState()).thenReturn(InitializationState.FINISHED);
        when(studentParticipationUninitialized.getInitializationState()).thenReturn(InitializationState.UNINITIALIZED);

        when(studentParticipationInitialized.getExercise()).thenReturn(exercise);
        when(studentParticipationInactive.getExercise()).thenReturn(exercise);
        when(studentParticipationFinished.getExercise()).thenReturn(exercise);
        when(studentParticipationUninitialized.getExercise()).thenReturn(exercise);

        studentParticipations = Arrays.asList(studentParticipationInactive, studentParticipationFinished, studentParticipationUninitialized, studentParticipationInitialized);

        when(submission1.getSubmissionDate()).thenReturn(ZonedDateTime.now());
        when(submission2.getSubmissionDate()).thenReturn(ZonedDateTime.now().plus(Period.ofDays(1)));
        when(submission3.getSubmissionDate()).thenReturn(ZonedDateTime.now().plus(Period.ofDays(2)));

        when(ratedResult.isRated()).thenReturn(true);
        when(unratedResult.isRated()).thenReturn(false);

        when(submission1.getResult()).thenReturn(ratedResult);
        when(submission2.getResult()).thenReturn(ratedResult);
        when(submission3.getResult()).thenReturn(ratedResult);

        when(studentParticipationInitialized.getSubmissions()).thenReturn(Set.of(submission1, submission2, submission3));
    }

    @Test
    public void findRelevantParticipation() {
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(studentParticipationInitialized);
    }

    @Test
    public void findRelevantParticipation_inactiveParticipation() {
        StudentParticipation desiredStudentParticipationInactive = mock(StudentParticipation.class);
        when(desiredStudentParticipationInactive.getInitializationState()).thenReturn(InitializationState.INACTIVE);
        when(desiredStudentParticipationInactive.getExercise()).thenReturn(exercise);
        studentParticipations = Arrays.asList(studentParticipationInactive, studentParticipationFinished, studentParticipationUninitialized, desiredStudentParticipationInactive);

        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(desiredStudentParticipationInactive);
    }

    @Test
    public void findRelevantParticipation_empty() {
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(new ArrayList<>());
        assertThat(relevantParticipation).isNull();
    }

    @Test
    public void findRelevantParticipation_modelingExercise() {
        ModelingExercise modelingExercise = mock(ModelingExercise.class, CALLS_REAL_METHODS);

        when(studentParticipationInitialized.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationInactive.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationFinished.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationUninitialized.getExercise()).thenReturn(modelingExercise);

        StudentParticipation relevantParticipation = modelingExercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(studentParticipationFinished);
    }

    @Test
    public void findRelevantParticipation_textExercise() {
        TextExercise textExercise = mock(TextExercise.class, CALLS_REAL_METHODS);

        when(studentParticipationInitialized.getExercise()).thenReturn(textExercise);
        when(studentParticipationInactive.getExercise()).thenReturn(textExercise);
        when(studentParticipationFinished.getExercise()).thenReturn(textExercise);
        when(studentParticipationUninitialized.getExercise()).thenReturn(textExercise);

        StudentParticipation relevantParticipation = textExercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(studentParticipationFinished);
    }

    /* Primarily the functionality of findAppropriateSubmissionByResults() is tested with the following tests */

    @Test
    public void filterForCourseDashboard_filterSensitiveInformation() {
        Result ratedResultTmp = new Result();
        ratedResultTmp.setAssessor(mock(User.class));
        ratedResultTmp.setRated(true);

        when(submission1.getResult()).thenReturn(ratedResultTmp);
        when(submission2.getResult()).thenReturn(ratedResultTmp);
        when(submission3.getResult()).thenReturn(ratedResultTmp);

        exercise.filterForCourseDashboard(studentParticipations, "student", true);
        Result result = exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().getResult();
        assertThat(result.getAssessor()).isNull();
    }

    @Test
    public void filterForCourseDashboard_nullParticipations() {
        exercise.filterForCourseDashboard(null, "student", true);
        assertThat(exercise.getStudentParticipations()).isNull();
    }

    @Test
    public void filterForCourseDashboard_nullSubmissions() {
        when(studentParticipationInactive.getSubmissions()).thenReturn(null);
        when(studentParticipationFinished.getSubmissions()).thenReturn(null);
        when(studentParticipationUninitialized.getSubmissions()).thenReturn(null);
        when(studentParticipationInitialized.getSubmissions()).thenReturn(null);

        exercise.filterForCourseDashboard(studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(null);
    }

    @Test
    public void filterForCourseDashboard_emptyParticipations() {
        exercise.filterForCourseDashboard(new ArrayList<>(), "student", true);
        assertThat(exercise.getStudentParticipations()).isNull();
    }

    @Test
    public void filterForCourseDashboard_emptySubmissions() {
        when(studentParticipationInactive.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationFinished.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationUninitialized.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationInitialized.getSubmissions()).thenReturn(new HashSet<>());

        exercise.filterForCourseDashboard(studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(new HashSet<>());
    }

    @Test
    public void filterForCourseDashboard_submissionsWithRatedResultsOrder() {
        exercise.filterForCourseDashboard(filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    public void filterForCourseDashboard_submissionsWithUnratedResultsOrder() {
        when(submission1.getResult()).thenReturn(unratedResult);
        when(submission2.getResult()).thenReturn(unratedResult);
        when(submission3.getResult()).thenReturn(unratedResult);

        exercise.filterForCourseDashboard(filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    public void filterForCourseDashboard_submissionWithoutResultsOrder() {
        when(submission1.getResult()).thenReturn(null);
        when(submission2.getResult()).thenReturn(null);
        when(submission3.getResult()).thenReturn(null);

        exercise.filterForCourseDashboard(filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    public void filterForCourseDashboard_submissionWithMixedResults() {
        when(submission1.getResult()).thenReturn(ratedResult);
        when(submission2.getResult()).thenReturn(null);
        when(submission3.getResult()).thenReturn(unratedResult);

        exercise.filterForCourseDashboard(filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission1));
    }

    private List<StudentParticipation> filterForCourseDashboard_prepareParticipations() {
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setExercise(exercise);
        participation.setSubmissions(Set.of(submission1, submission2, submission3));

        List<StudentParticipation> participations = new ArrayList<>();
        participations.add(participation);

        return participations;
    }
}
