package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.ExerciseService;

class ExerciseTest {

    @Mock
    private Exercise exercise;

    private List<StudentParticipation> studentParticipations;

    @Mock
    private ExerciseGroup exerciseGroup;

    @Mock
    private Exam exam;

    @Mock
    private StudentParticipation studentParticipationInitialized;

    @Mock
    private StudentParticipation studentParticipationInactive;

    @Mock
    private StudentParticipation studentParticipationFinished;

    @Mock
    private StudentParticipation studentParticipationUninitialized;

    @Mock
    private ProgrammingSubmission submission1;

    @Mock
    private ProgrammingSubmission submission2;

    @Mock
    private ProgrammingSubmission submission3;

    @Mock
    private Result ratedResult;

    @Mock
    private Result unratedResult;

    @Mock
    private ExerciseService exerciseService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        exercise = mock(Exercise.class, CALLS_REAL_METHODS);
        exerciseService = mock(ExerciseService.class, CALLS_REAL_METHODS);

        when(studentParticipationInitialized.getInitializationState()).thenReturn(InitializationState.INITIALIZED);
        when(studentParticipationInactive.getInitializationState()).thenReturn(InitializationState.INACTIVE);
        when(studentParticipationFinished.getInitializationState()).thenReturn(InitializationState.FINISHED);
        when(studentParticipationUninitialized.getInitializationState()).thenReturn(InitializationState.UNINITIALIZED);

        when(studentParticipationInitialized.getExercise()).thenReturn(exercise);
        when(studentParticipationInactive.getExercise()).thenReturn(exercise);
        when(studentParticipationFinished.getExercise()).thenReturn(exercise);
        when(studentParticipationUninitialized.getExercise()).thenReturn(exercise);

        studentParticipations = Arrays.asList(studentParticipationInactive, studentParticipationFinished, studentParticipationUninitialized, studentParticipationInitialized);

        submission1 = new ProgrammingSubmission();
        submission2 = new ProgrammingSubmission();
        submission3 = new ProgrammingSubmission();

        submission1.setSubmissionDate(ZonedDateTime.now());
        submission2.setSubmissionDate(ZonedDateTime.now().plusDays(1));
        submission3.setSubmissionDate(ZonedDateTime.now().plusDays(2));

        submission1.setResults(List.of(ratedResult));
        submission2.setResults(List.of(ratedResult));
        submission3.setResults(List.of(ratedResult));

        submission1.setCommitHash("aaaaa");
        submission2.setCommitHash("bbbbb");
        submission3.setCommitHash("ccccc");

        when(ratedResult.isRated()).thenReturn(true);
        when(unratedResult.isRated()).thenReturn(false);

        when(studentParticipationInitialized.getSubmissions()).thenReturn(Set.of(submission1, submission2, submission3));
    }

    @AfterEach
    void reset() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void findRelevantParticipation() {
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(studentParticipationInitialized);
    }

    @Test
    void findRelevantParticipation_inactiveParticipation() {
        StudentParticipation desiredStudentParticipationInactive = mock(StudentParticipation.class);
        when(desiredStudentParticipationInactive.getInitializationState()).thenReturn(InitializationState.INACTIVE);
        when(desiredStudentParticipationInactive.getExercise()).thenReturn(exercise);
        studentParticipations = Arrays.asList(studentParticipationInactive, studentParticipationFinished, studentParticipationUninitialized, desiredStudentParticipationInactive);

        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(desiredStudentParticipationInactive);
    }

    @Test
    void findRelevantParticipation_empty() {
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(new ArrayList<>());
        assertThat(relevantParticipation).isNull();
    }

    @Test
    void findRelevantParticipation_modelingExercise() {
        ModelingExercise modelingExercise = mock(ModelingExercise.class, CALLS_REAL_METHODS);

        when(studentParticipationInitialized.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationInactive.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationFinished.getExercise()).thenReturn(modelingExercise);
        when(studentParticipationUninitialized.getExercise()).thenReturn(modelingExercise);

        StudentParticipation relevantParticipation = modelingExercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipation).isEqualTo(studentParticipationFinished);
    }

    @Test
    void findRelevantParticipation_textExercise() {
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
    void filterForCourseDashboard_filterSensitiveInformation() {
        Result ratedResultTmp = new Result();
        ratedResultTmp.setAssessor(mock(User.class));
        ratedResultTmp.setRated(true);

        submission1.setResults(List.of(ratedResultTmp));
        submission2.setResults(List.of(ratedResultTmp));
        submission3.setResults(List.of(ratedResultTmp));

        exerciseService.filterForCourseDashboard(exercise, studentParticipations, "student", true);
        Result result = exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().getLatestResult();
        assertThat(result.getAssessor()).isNull();
    }

    @Test
    void filterForCourseDashboard_nullParticipations() {
        exerciseService.filterForCourseDashboard(exercise, null, "student", true);
        assertThat(exercise.getStudentParticipations()).isNull();
    }

    @Test
    void filterForCourseDashboard_nullSubmissions() {
        when(studentParticipationInactive.getSubmissions()).thenReturn(null);
        when(studentParticipationFinished.getSubmissions()).thenReturn(null);
        when(studentParticipationUninitialized.getSubmissions()).thenReturn(null);
        when(studentParticipationInitialized.getSubmissions()).thenReturn(null);

        exerciseService.filterForCourseDashboard(exercise, studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isNull();
    }

    @Test
    void filterForCourseDashboard_emptyParticipations() {
        exerciseService.filterForCourseDashboard(exercise, new ArrayList<>(), "student", true);
        assertThat(exercise.getStudentParticipations()).isNull();
    }

    @Test
    void filterForCourseDashboard_emptySubmissions() {
        when(studentParticipationInactive.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationFinished.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationUninitialized.getSubmissions()).thenReturn(new HashSet<>());
        when(studentParticipationInitialized.getSubmissions()).thenReturn(new HashSet<>());

        exerciseService.filterForCourseDashboard(exercise, studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(new HashSet<>());
    }

    @Test
    void filterForCourseDashboard_submissionsWithRatedResultsOrder() {
        exerciseService.filterForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    void filterForCourseDashboard_submissionsWithUnratedResultsOrder() {
        submission1.setResults(List.of(unratedResult));
        submission2.setResults(List.of(unratedResult));
        submission3.setResults(List.of(unratedResult));

        exerciseService.filterForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    void filterForCourseDashboard_submissionWithoutResultsOrder() {
        submission1.setResults(List.of());
        submission2.setResults(List.of());
        submission3.setResults(List.of());

        exerciseService.filterForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    void filterForCourseDashboard_submissionWithoutResultsAndSameCommitHashOrder() {
        submission1.commitHash("same");
        submission2.commitHash("same");
        submission3.commitHash("same");

        submission1.setId(42L);
        submission2.setId(21L);
        submission3.setId(15L);

        exerciseService.filterForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission1));
    }

    @Test
    void filterForCourseDashboard_submissionWithMixedResults() {
        submission1.setResults(List.of(ratedResult));
        submission2.setResults(List.of());
        submission3.setResults(List.of(unratedResult));

        exerciseService.filterForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission1));
    }

    @Test
    void getExamViaExerciseGroupOrCourseMember_withExamExercise() {
        Exercise examExercise = mock(Exercise.class, CALLS_REAL_METHODS);

        when(examExercise.isExamExercise()).thenReturn(true);
        when(examExercise.getExerciseGroup()).thenReturn(exerciseGroup);
        when(exerciseGroup.getExam()).thenReturn(exam);

        Exam result = examExercise.getExamViaExerciseGroupOrCourseMember();
        assertThat(result).isEqualTo(exam);
    }

    @Test
    void getExamViaExerciseGroupOrCourseMember_withoutExamExercise() {
        Exercise examExercise = mock(Exercise.class, CALLS_REAL_METHODS);
        when(examExercise.isExamExercise()).thenReturn(false);
        Exam result = examExercise.getExamViaExerciseGroupOrCourseMember();
        assertThat(result).isNull();
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
