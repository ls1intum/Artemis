package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.service.ExerciseService;

class ExerciseTest extends AbstractSpringIntegrationIndependentTest {

    private Course course;

    private Exercise exercise;

    private Set<StudentParticipation> studentParticipations;

    private StudentParticipation studentParticipationInitialized;

    private StudentParticipation studentParticipationInactive;

    private StudentParticipation studentParticipationFinished;

    private StudentParticipation studentParticipationUninitialized;

    private ProgrammingSubmission submission1;

    private ProgrammingSubmission submission2;

    private ProgrammingSubmission submission3;

    private Result ratedResult;

    private Result unratedResult;

    @Autowired
    private ExerciseService exerciseService;

    @BeforeEach
    void setUp() {
        course = CourseFactory.generateCourse(42L, null, null, null);
        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);

        studentParticipationInitialized = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.INITIALIZED, exercise);
        studentParticipationInactive = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.INACTIVE, exercise);
        studentParticipationFinished = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.FINISHED, exercise);
        studentParticipationUninitialized = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.UNINITIALIZED, exercise);

        studentParticipations = Set.of(studentParticipationInactive, studentParticipationFinished, studentParticipationUninitialized, studentParticipationInitialized);

        ratedResult = new Result();
        ratedResult.setRated(true);
        unratedResult = new Result();
        unratedResult.setRated(false);

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

        studentParticipationFinished.setSubmissions(Set.of(submission1, submission2, submission3));
    }

    @Test
    void findRelevantParticipation() {
        var relevantParticipations = exercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipations).containsExactly(studentParticipationInitialized);
    }

    @Test
    void findRelevantParticipation_empty() {
        var relevantParticipations = exercise.findRelevantParticipation(Set.of());
        assertThat(relevantParticipations).isEmpty();
    }

    @Test
    void findRelevantParticipation_modelingExercise() {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(null, null, null, DiagramType.ClassDiagram, course);

        studentParticipationInitialized.setExercise(modelingExercise);
        studentParticipationInactive.setExercise(modelingExercise);
        studentParticipationFinished.setExercise(modelingExercise);
        studentParticipationUninitialized.setExercise(modelingExercise);

        var relevantParticipations = modelingExercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipations).containsExactly(studentParticipationInitialized);
    }

    @Test
    void findRelevantParticipation_textExercise() {
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(null, null, null, course);

        studentParticipationInitialized.setExercise(textExercise);
        studentParticipationInactive.setExercise(textExercise);
        studentParticipationFinished.setExercise(textExercise);
        studentParticipationUninitialized.setExercise(textExercise);

        var relevantParticipations = textExercise.findRelevantParticipation(studentParticipations);
        assertThat(relevantParticipations).containsExactly(studentParticipationInitialized);
    }

    /* Primarily the functionality of findAppropriateSubmissionByResults() is tested with the following tests */

    @Test
    void filterForCourseDashboard_filterSensitiveInformation() {
        exercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        ratedResult.setAssessor(new User());
        ratedResult.setAssessmentType(AssessmentType.MANUAL);
        ratedResult.setCompletionDate(ZonedDateTime.now().minusHours(2));

        // only use the relevant participation
        exerciseService.filterForCourseDashboard(exercise, Set.of(studentParticipationFinished), "student", true);
        var submissions = exercise.getStudentParticipations().iterator().next().getSubmissions();
        // We should only get the one relevant submission to send to the client
        assertThat(submissions).hasSize(1);
        Result result = submissions.iterator().next().getLatestResult();
        assertThat(result).isNotNull();
        assertThat(result.getAssessor()).isNull();
    }

    @Test
    void filterForCourseDashboard_nullParticipations() {
        exerciseService.filterForCourseDashboard(exercise, null, "student", true);
        assertThat(exercise.getStudentParticipations()).isEmpty();
    }

    @Test
    void filterForCourseDashboard_nullSubmissions() {
        studentParticipationInactive.setSubmissions(null);
        studentParticipationFinished.setSubmissions(null);
        studentParticipationUninitialized.setSubmissions(null);
        studentParticipationInitialized.setSubmissions(null);

        exerciseService.filterForCourseDashboard(exercise, studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isNull();
    }

    @Test
    void filterForCourseDashboard_emptyParticipations() {
        exerciseService.filterForCourseDashboard(exercise, Set.of(), "student", true);
        assertThat(exercise.getStudentParticipations()).isEmpty();
    }

    @Test
    void filterForCourseDashboard_emptySubmissions() {
        studentParticipationInactive.setSubmissions(new HashSet<>());
        studentParticipationFinished.setSubmissions(new HashSet<>());
        studentParticipationUninitialized.setSubmissions(new HashSet<>());
        studentParticipationInitialized.setSubmissions(new HashSet<>());

        exerciseService.filterForCourseDashboard(exercise, studentParticipations, "student", true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isNull();
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
        Exam exam = ExamFactory.generateExam(null);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam);
        Exercise examExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);

        Exam result = examExercise.getExamViaExerciseGroupOrCourseMember();
        assertThat(result).isEqualTo(exam);
    }

    @Test
    void getExamViaExerciseGroupOrCourseMember_withoutExamExercise() {
        Exercise examExercise = TextExerciseFactory.generateTextExerciseForExam(null);

        Exam result = examExercise.getExamViaExerciseGroupOrCourseMember();
        assertThat(result).isNull();
    }

    private Set<StudentParticipation> filterForCourseDashboard_prepareParticipations() {
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setExercise(exercise);
        participation.setSubmissions(Set.of(submission1, submission2, submission3));

        Set<StudentParticipation> participations = new HashSet<>();
        participations.add(participation);

        return participations;
    }

    @Test
    void testSanitizedExerciseTitleDoesntContainAnyIllegalCharacters() {
        Exercise exercise = new ProgrammingExercise();
        exercise.setTitle("Test?+#*                Exercise123%$§");
        assertThat(exercise.getSanitizedExerciseTitle()).isEqualTo("Test_Exercise123");
    }
}
