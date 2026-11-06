package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.BonusService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExamGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExamPracticeGradeTest {

    @Mock
    private ExamTestRepository examRepository;

    @Mock
    private GradingScaleRepository gradingScaleRepository;

    @Mock
    private StudentParticipationTestRepository studentParticipationRepository;

    @Mock
    private StudentExamTestRepository studentExamRepository;

    @Mock
    private BonusService bonusService;

    @Mock
    private CourseScoreCalculationService courseScoreCalculationService;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Spy
    private Optional<PlagiarismCaseApi> plagiarismCaseApi = Optional.empty();

    @InjectMocks
    private ExamService examService;

    private Exam exam;

    private StudentExam attempt;

    private QuizExercise quiz;

    @BeforeEach
    void setUp() {
        Course course = new Course();
        course.setId(1L);
        course.setAccuracyOfScores(2);
        exam = new Exam();
        exam.setId(1L);
        exam.setCourse(course);
        exam.setTestExam(true);
        exam.setExamMaxPoints(100);
        User student = new User();
        student.setId(1L);
        attempt = new StudentExam();
        attempt.setId(1L);
        attempt.setExam(exam);
        attempt.setUser(student);
        attempt.setSubmitted(true);
        quiz = new QuizExercise();
        quiz.setId(1L);
        quiz.setMaxPoints(10.0);
        quiz.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        ExerciseGroup group = new ExerciseGroup();
        group.setId(1L);
        group.setExam(exam);
        quiz.setExerciseGroup(group);
        attempt.setExercises(List.of(quiz));
        StudentParticipation participation = new StudentParticipation();
        participation.setId(1L);
        participation.setExercise(quiz);
        org.mockito.Mockito.lenient().when(examRepository.findByIdElseThrow(1L)).thenReturn(exam);
        org.mockito.Mockito.lenient().when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(participation));
        org.mockito.Mockito.lenient().when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.empty());
    }

    @Test
    void partialGradeUsesOnlyAutomaticallyAssessedExercises() {
        TextExercise text = new TextExercise();
        text.setId(2L);
        text.setMaxPoints(90.0);
        text.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        attempt.setExercises(List.of(quiz, text));
        GradingScale scale = new GradingScale();
        GradeStep passing = new GradeStep();
        passing.setGradeName("1.0");
        passing.setIsPassingGrade(true);
        when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.of(scale));
        when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(scale))).thenReturn(passing);

        StudentParticipation manualParticipation = new StudentParticipation();
        manualParticipation.setId(2L);
        manualParticipation.setExercise(text);
        StudentParticipation quizParticipation = new StudentParticipation();
        quizParticipation.setId(1L);
        quizParticipation.setExercise(quiz);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(quizParticipation, manualParticipation));
        var manualGrade = new ExamGradeScoreDTO(2L, 1L, text.getId(), 100.0, text.getMaxPoints(), IncludedInOverallScore.INCLUDED_COMPLETELY);
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 100.0), manualGrade));

        assertThat(result.maxPoints()).isEqualTo(10.0);
        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(10.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(result.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(result.achievedPointsPerExercise()).doesNotContainKey(text.getId());
    }

    @Test
    void gradeDoesNotIncludeAnotherAttemptOfTheSameExercise() {
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 60.0), grade(99L, 100.0)));

        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(6.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(60.0);
        assertThat(result.achievedPointsPerExercise()).containsOnlyKeys(quiz.getId());
        assertThat(result.achievedPointsPerExercise().get(quiz.getId())).isEqualTo(6.0);
    }

    @Test
    void manuallyAssessedOnlyTestExamHasNoTheoreticalGrade() {
        TextExercise text = new TextExercise();
        text.setId(2L);
        text.setMaxPoints(100.0);
        text.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        attempt.setExercises(List.of(text));

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());

        assertThat(result.maxPoints()).isZero();
        assertThat(result.studentResult().overallPointsAchieved()).isZero();
        assertThat(result.studentResult().overallGrade()).isEmpty();
    }

    @Test
    void instructorScoresUseTheSameAutomaticCurrentAttemptInputs() {
        TextExercise text = new TextExercise();
        text.setId(2L);
        text.setMaxPoints(90.0);
        text.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        attempt.setExercises(List.of(quiz, text));
        exam.setExerciseGroups(List.of(quiz.getExerciseGroup()));
        exam.setNumberOfCorrectionRoundsInExam(1);
        when(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(1L)).thenReturn(exam);
        when(studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(1L)).thenReturn(Set.of(attempt));
        when(studentParticipationRepository.findGradesByExamId(1L)).thenReturn(
                Set.of(grade(1L, 60.0), grade(99L, 100.0), new ExamGradeScoreDTO(2L, 1L, text.getId(), 100.0, text.getMaxPoints(), IncludedInOverallScore.INCLUDED_COMPLETELY)));

        var result = examService.calculateExamScores(1L).studentResults().getFirst();

        assertThat(result.overallPointsAchieved()).isEqualTo(6.0);
        assertThat(result.overallScoreAchieved()).isEqualTo(60.0);
        assertThat(result.maxPoints()).isEqualTo(10.0);
    }

    @Test
    void queuedProgrammingBuildDoesNotProduceAPrematureGrade() {
        var programming = programmingParticipation();
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        programming.addSubmission(submission);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());

        assertThat(result.pendingExerciseIds()).containsExactly(2L);
        assertThat(result.studentResult().overallPointsAchieved()).isNull();
        assertThat(result.studentResult().overallScoreAchieved()).isNull();
        assertThat(result.studentResult().overallGrade()).isEmpty();
    }

    @Test
    void programmingWithoutASubmissionIsZeroRatherThanPending() {
        var programming = programmingParticipation();
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());
        assertThat(result.pendingExerciseIds()).isEmpty();
        assertThat(result.studentResult().overallPointsAchieved()).isZero();
    }

    @Test
    void failedProgrammingBuildIsFinishedRatherThanPending() {
        var programming = programmingParticipation();
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setBuildFailed(true);
        programming.addSubmission(submission);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());
        assertThat(result.pendingExerciseIds()).isEmpty();
        assertThat(result.studentResult().overallPointsAchieved()).isZero();
    }

    @Test
    void completedProgrammingResultContributesToTheGrade() {
        var programming = programmingParticipation();
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));
        var grade = new ExamGradeScoreDTO(programming.getId(), 1L, 2L, 80.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade));
        assertThat(result.pendingExerciseIds()).isEmpty();
        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(16.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(80.0);
    }

    @Test
    void bonusUsesTheSamePartialPercentageAndReturnsAttemptPoints() {
        var scale = new GradingScale();
        scale.setExam(exam);
        scale.setBonusStrategy(BonusStrategy.POINTS);
        var passing = new GradeStep();
        passing.setGradeName("1.0");
        passing.setIsPassingGrade(true);
        var sourceCourse = new Course();
        sourceCourse.setId(2L);
        sourceCourse.setMaxPoints(100);
        sourceCourse.setTitle("Bonus source");
        var sourceScale = new GradingScale();
        sourceScale.setCourse(sourceCourse);
        var bonus = new Bonus();
        bonus.setSourceGradingScale(sourceScale);
        scale.addBonusFrom(bonus);
        when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.of(scale));
        when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(scale))).thenReturn(passing);
        when(courseScoreCalculationService.calculateCourseScoresForExamBonusSource(sourceCourse, sourceScale, List.of(1L)))
                .thenReturn(Map.of(1L, new BonusSourceResultDTO(100.0, null, null, null, true)));
        when(courseScoreCalculationService.calculateReachablePoints(eq(sourceScale), org.mockito.ArgumentMatchers.any())).thenReturn(100.0);
        // The bonus calculator operates on the configured 100-point scale, while this attempt has 10 automatic points.
        when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), eq(100.0), eq(100.0))).thenAnswer(invocation -> {
            double points = invocation.getArgument(1);
            return new BonusExampleDTO(points, 100.0, "1.0", 10.0, Math.min(100, points + 10), points >= 90 ? "1.0" : "5.0", false);
        });

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 90.0)));

        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(9.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(90.0);
        assertThat(result.studentResult().gradeWithBonus().finalGrade()).isEqualTo("1.0");
        assertThat(result.studentResult().gradeWithBonus().finalPoints()).isEqualTo(10.0);
    }

    private StudentParticipation programmingParticipation() {
        var programming = new ProgrammingExercise();
        programming.setId(2L);
        programming.setMaxPoints(20.0);
        programming.setAssessmentType(AssessmentType.AUTOMATIC);
        programming.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programming.setExerciseGroup(quiz.getExerciseGroup());
        attempt.setExercises(List.of(programming));
        var participation = new StudentParticipation();
        participation.setId(2L);
        participation.setExercise(programming);
        return participation;
    }

    private ExamGradeScoreDTO grade(long participationId, double score) {
        return new ExamGradeScoreDTO(participationId, 1L, quiz.getId(), score, quiz.getMaxPoints(), IncludedInOverallScore.INCLUDED_COMPLETELY);
    }
}
