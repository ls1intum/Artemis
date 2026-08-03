package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class ExerciseTest extends AbstractSpringIntegrationIndependentBatchTest {

    private Exercise exercise;

    private StudentParticipation studentParticipation;

    private ProgrammingSubmission submission1;

    private ProgrammingSubmission submission2;

    private ProgrammingSubmission submission3;

    private Result ratedResult;

    @Autowired
    private ExerciseService exerciseService;

    @BeforeEach
    void setUp() {
        Course course = CourseFactory.generateCourse(42L, null, null, null);
        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);

        studentParticipation = ParticipationFactory.generateStudentParticipationWithoutUser(InitializationState.FINISHED, exercise);

        ratedResult = new Result();
        ratedResult.setRated(true);
        Result unratedResult = new Result();
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

        studentParticipation.setSubmissions(Set.of(submission1, submission2, submission3));
    }

    @Test
    void filterForCourseDashboard_filterSensitiveInformation() {
        exercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        ratedResult.setAssessor(new User());
        ratedResult.setAssessmentType(AssessmentType.MANUAL);
        ratedResult.setCompletionDate(ZonedDateTime.now().minusHours(2));

        // only use the relevant participation
        Set.of(submission1, submission2, submission3).forEach(s -> s.setParticipation(studentParticipation));
        exerciseService.filterExerciseForCourseDashboard(exercise, Set.of(studentParticipation), true);
        var submissions = exercise.getStudentParticipations().iterator().next().getSubmissions();
        // We should only get the one relevant submission to send to the client
        assertThat(submissions).hasSize(1);
        Result result = submissions.iterator().next().getLatestResult();
        assertThat(result).isNotNull();
        assertThat(result.getAssessor()).isNull();
    }

    @Test
    void filterForCourseDashboard_nullParticipations() {
        exerciseService.filterExerciseForCourseDashboard(exercise, null, true);
        assertThat(exercise.getStudentParticipations()).isEmpty();
    }

    @Test
    void filterForCourseDashboard_nullSubmissions() {
        studentParticipation.setSubmissions(null);

        exerciseService.filterExerciseForCourseDashboard(exercise, Set.of(studentParticipation), true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEmpty();
    }

    @Test
    void filterForCourseDashboard_emptyParticipations() {
        exerciseService.filterExerciseForCourseDashboard(exercise, Set.of(), true);
        assertThat(exercise.getStudentParticipations()).isEmpty();
    }

    @Test
    void filterForCourseDashboard_submissionsWithRatedResultsOrder() {
        exerciseService.filterExerciseForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission3));
    }

    @Test
    void filterForCourseDashboard_submissionWithoutResultsOrder() {
        submission1.setResults(List.of());
        submission2.setResults(List.of());
        submission3.setResults(List.of());

        exerciseService.filterExerciseForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), true);
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

        exerciseService.filterExerciseForCourseDashboard(exercise, filterForCourseDashboard_prepareParticipations(), true);
        assertThat(exercise.getStudentParticipations().iterator().next().getSubmissions()).isEqualTo(Set.of(submission1));
    }

    @Test
    void getExam_withExamExercise() {
        Exam exam = ExamFactory.generateExam(null);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam);
        Exercise examExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);

        Exam result = examExercise.getExam();
        assertThat(result).isEqualTo(exam);
    }

    @Test
    void getExam_withoutExamExercise() {
        Exercise examExercise = TextExerciseFactory.generateTextExerciseForExam(null);

        Exam result = examExercise.getExam();
        assertThat(result).isNull();
    }

    private Set<StudentParticipation> filterForCourseDashboard_prepareParticipations() {
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setExercise(exercise);
        participation.setSubmissions(Set.of(submission1, submission2, submission3));
        participation.getSubmissions().forEach(s -> s.setParticipation(participation));

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

    @Test
    void validateScoreSettings_notIncludedWithoutMaxPoints_doesNotThrow() {
        Exercise exercise = new ProgrammingExercise();
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exercise.setMaxPoints(null);
        exercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(exercise::validateScoreSettings);
        assertThat(exercise.getMaxPoints()).isZero();
    }

    @Test
    void validateScoreSettings_notIncludedTextExerciseWithZeroMaxPoints_throws() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exercise.setMaxPoints(0.0);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("The max points needs to be greater than 0");
    }

    @Test
    void validateScoreSettings_includedWithoutMaxPoints_throws() {
        Exercise exercise = new ProgrammingExercise();
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(null);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("The max points needs to be greater than 0");
    }

    @Test
    void validateScoreSettings_maxPointsWithScientificNotationPrecision_throws() {
        // 1e-30 parses to a valid, in-range Double, so only a decimal-precision check can catch it, see issue #12451
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(1e-30);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("The max points must not have more than 4 decimal places");
    }

    @Test
    void validateScoreSettings_bonusPointsWithScientificNotationPrecision_throws() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(1e-30);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("The bonus points must not have more than 4 decimal places");
    }

    @Test
    void validateScoreSettings_maxPointsNaN_throwsWithoutCrashing() {
        // BigDecimal.valueOf(NaN) throws NumberFormatException, so isFinite must be checked first
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(Double.NaN);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("The max points must be a finite number");
    }

    @Test
    void validateScoreSettings_maxPointsInfinite_throwsWithoutCrashing() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(Double.POSITIVE_INFINITY);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("The max points must be a finite number");
    }

    @Test
    void validateScoreSettings_bonusPointsInfinite_throwsWithoutCrashing() {
        // NEGATIVE_INFINITY is < 0, so it would get silently corrected to 0.0 by the existing negative-bonusPoints
        // defaulting logic before ever reaching the finite check - use POSITIVE_INFINITY to actually exercise it.
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(Double.POSITIVE_INFINITY);

        assertThatThrownBy(exercise::validateScoreSettings).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("The bonus points must be a finite number");
    }

    @Test
    void validateScoreSettings_pointsWithReasonablePrecision_doesNotThrow() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.5);
        exercise.setBonusPoints(0.25);

        assertThatNoException().isThrownBy(exercise::validateScoreSettings);
    }
}
