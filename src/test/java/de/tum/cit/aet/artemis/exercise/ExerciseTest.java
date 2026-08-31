package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
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
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
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

        submission1.setResults(Set.of(ratedResult));
        submission2.setResults(Set.of(ratedResult));
        submission3.setResults(Set.of(ratedResult));

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
        submission1.setResults(Set.of());
        submission2.setResults(Set.of());
        submission3.setResults(Set.of());

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
    void validateScoreSettings_maxPointsWithTooManyDecimalPlaces_throws() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(1.1111111111111112);
        exercise.setBonusPoints(0.0);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("decimal places");
    }

    @Test
    void validateScoreSettings_bonusPointsWithTooManyDecimalPlaces_throws() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(10.0);
        exercise.setBonusPoints(5.5555555555555555);

        assertThatThrownBy(exercise::validateScoreSettings).hasMessageContaining("decimal places");
    }

    @Test
    void validateScoreSettings_pointsWithAtMostTwoDecimalPlaces_doesNotThrow() {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(10.5);
        exercise.setBonusPoints(2.25);

        assertThatNoException().isThrownBy(exercise::validateScoreSettings);
    }

    @Test
    void validateScoreSettings_nonProgrammingExerciseIgnoresCourseAccuracyOfScores_usesFallback() {
        // The shared `exercise` field (set up in setUp()) is a TextExercise whose course has accuracyOfScores = 1.
        // Only ProgrammingExercise is meant to pick up the course's dynamic setting; other exercise types keep the
        // static MAX_POINTS_DECIMAL_PLACES fallback (= 2) regardless of the course setting, so this must still accept
        // 2 decimal places even though the course only allows 1.
        assertThat(exercise.getCourseViaExerciseGroupOrCourseMember().getAccuracyOfScores()).isEqualTo(1);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(10.25);
        exercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(exercise::validateScoreSettings);
    }

    @Test
    void validateScoreSettings_programmingExerciseWithoutResolvableCourse_usesFallback() {
        // A transient ProgrammingExercise with no course/exerciseGroup attached at all (e.g. before the course is
        // resolved during creation) cannot look up an accuracy setting, so it must fall back to the default.
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(10.25);
        programmingExercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(programmingExercise::validateScoreSettings);

        programmingExercise.setMaxPoints(10.125);
        assertThatThrownBy(programmingExercise::validateScoreSettings).hasMessageContaining("2 decimal places");
    }

    @Test
    void validateScoreSettings_programmingExerciseUsesCourseAccuracyOfScores_acceptsPrecisionMatchingCourseSetting() {
        Course course = CourseFactory.generateCourse(43L, null, null, null);
        course.setAccuracyOfScores(3);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(10.125);
        programmingExercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(programmingExercise::validateScoreSettings);
    }

    @Test
    void validateScoreSettings_programmingExerciseUsesCourseAccuracyOfScores_rejectsPrecisionExceedingCourseSetting() {
        Course course = CourseFactory.generateCourse(43L, null, null, null);
        course.setAccuracyOfScores(1);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        // 2 decimal places, allowed by the global fallback but not by this course's accuracyOfScores = 1
        programmingExercise.setMaxPoints(10.25);
        programmingExercise.setBonusPoints(0.0);

        assertThatThrownBy(programmingExercise::validateScoreSettings).hasMessageContaining("1 decimal places");
    }

    @Test
    void validateScoreSettings_programmingExerciseWithZeroAccuracyOfScores_wholeNumbersDoNotThrow() {
        // accuracyOfScores = 0 is a valid course setting (Course#validateAccuracyOfScores allows 0-5). Whole-number
        // doubles like 100.0 must still be accepted: BigDecimal.valueOf(100.0) has scale 1 (Double#toString always
        // renders a fractional digit), so the check must strip trailing zeros rather than reject every value.
        Course course = CourseFactory.generateCourse(43L, null, null, null);
        course.setAccuracyOfScores(0);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(100.0);
        programmingExercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(programmingExercise::validateScoreSettings);
    }

    @Test
    void validateScoreSettings_programmingExerciseWithZeroAccuracyOfScores_rejectsAnyDecimal() {
        Course course = CourseFactory.generateCourse(43L, null, null, null);
        course.setAccuracyOfScores(0);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(100.5);
        programmingExercise.setBonusPoints(0.0);

        assertThatThrownBy(programmingExercise::validateScoreSettings).hasMessageContaining("0 decimal places");
    }

    @Test
    void validateScoreSettings_existingPointsExceedingNewlyLoweredCourseAccuracy_rejectedOnNextValidation() {
        // Simulates: an exercise was created/saved while the course allowed 2 decimal places (maxPoints = 1.25 was
        // valid at the time). The course's accuracyOfScores is later tightened to 1 by an instructor. The already
        // persisted exercise is never retroactively touched or invalidated - nothing re-runs validateScoreSettings()
        // in the background - but the next time this exact exercise is saved/updated again, validation now runs
        // against the current (lowered) course setting and correctly rejects the still-too-precise value.
        Course course = CourseFactory.generateCourse(43L, null, null, null);
        course.setAccuracyOfScores(2);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(1.25);
        programmingExercise.setBonusPoints(0.0);

        // Valid under the original course setting (accuracyOfScores = 2)
        assertThatNoException().isThrownBy(programmingExercise::validateScoreSettings);

        // The course setting is tightened after the fact; the persisted exercise itself is left untouched
        course.setAccuracyOfScores(1);

        // The same exercise, unmodified, now fails re-validation on its next write
        assertThatThrownBy(programmingExercise::validateScoreSettings).hasMessageContaining("1 decimal places");
    }

    @Test
    void validateScoreSettings_quizExerciseWithFloatingPointAggregateMaxPoints_doesNotThrow() {
        // QuizExercise#getMaxPoints() is a raw double sum of its questions' individual points (see
        // QuizExercise#getOverallQuizPoints()), so two fully valid question points can still produce a sum that
        // *looks* like it has more decimal places due to binary floating-point rounding, e.g. 0.1 + 0.2 ==
        // 0.30000000000000004. The precision check must not reject a quiz for this - see
        // Exercise#validateScoreSettings(), which skips the maxPoints precision check entirely for QuizExercise.
        Course course = CourseFactory.generateCourse(44L, null, null, null);
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(null, null, QuizMode.SYNCHRONIZED, course);
        quizExercise.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion().score(0.1));
        quizExercise.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion().score(0.2));
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        assertThat(quizExercise.getMaxPoints()).isNotEqualTo(0.3);
        quizExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        quizExercise.setBonusPoints(0.0);

        assertThatNoException().isThrownBy(quizExercise::validateScoreSettings);
    }
}
