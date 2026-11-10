package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.BonusService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExamGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

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

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    @Spy
    private Optional<PlagiarismCaseApi> plagiarismCaseApi = Optional.empty();

    @InjectMocks
    @Spy
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
        text.setExerciseGroup(quiz.getExerciseGroup());
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
        text.setExerciseGroup(quiz.getExerciseGroup());
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
        text.setExerciseGroup(quiz.getExerciseGroup());
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
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setSubmissionDate(ZonedDateTime.now());
        programming.addSubmission(submission);
        var completedResult = new Result();
        completedResult.setId(1L);
        completedResult.setCompletionDate(ZonedDateTime.now());
        completedResult.setRated(true);
        completedResult.setScore(80.0);
        submission.addResult(completedResult);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));
        var grade = new ExamGradeScoreDTO(programming.getId(), 1L, 2L, 80.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade));
        assertThat(result.pendingExerciseIds()).isEmpty();
        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(16.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(80.0);
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void programmingCompletionBetweenScoreAndParticipationReadsUsesTheCompletedSnapshot(boolean instructorView, boolean earlierProgrammingScore) {
        var programming = programmingParticipation();
        attempt.setExercises(List.of(quiz, programming.getExercise()));
        var quizParticipation = new StudentParticipation();
        quizParticipation.setId(1L);
        quizParticipation.setExercise(quiz);
        var submission = new ProgrammingSubmission();
        submission.setId(10L);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        programming.addSubmission(submission);
        var completedResult = new Result();
        completedResult.setId(10L);
        completedResult.setRated(true);
        completedResult.setScore(80.0);
        completedResult.setCompletionDate(ZonedDateTime.now());
        var earlierGrades = new HashSet<>(Set.of(grade(1L, 50.0)));
        if (earlierProgrammingScore) {
            earlierGrades.add(new ExamGradeScoreDTO(programming.getId(), 1L, 2L, 20.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY));
        }
        // The result finishes after the grade projection, but before the exact-attempt participation reload.
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenAnswer(invocation -> {
            submission.addResult(completedResult);
            return List.of(quizParticipation, programming);
        });

        if (instructorView) {
            exam.setExerciseGroups(List.of(quiz.getExerciseGroup()));
            exam.setNumberOfCorrectionRoundsInExam(1);
            when(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(1L)).thenReturn(exam);
            when(studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(1L)).thenReturn(Set.of(attempt));
            when(studentParticipationRepository.findGradesByExamId(1L)).thenReturn(earlierGrades);
            var result = examService.calculateExamScores(1L).studentResults().getFirst();
            assertThat(result.overallPointsAchieved()).isEqualTo(21.0);
            assertThat(result.overallScoreAchieved()).isEqualTo(70.0);
            assertThat(result.maxPoints()).isEqualTo(30.0);
        }
        else {
            var result = examService.calculateStudentResultWithGradeAndPoints(attempt, earlierGrades);
            assertThat(result.pendingExerciseIds()).isEmpty();
            assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(21.0);
            assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(70.0);
            assertThat(result.achievedPointsPerExercise()).containsEntry(quiz.getId(), 5.0).containsEntry(2L, 16.0);
        }
    }

    @ParameterizedTest
    @CsvSource(value = { "true,0", "false,80", "true,NULL" }, nullValues = "NULL")
    void completedProgrammingSnapshotRetainsZeroAndDoesNotAwaitIneligibleScores(boolean rated, Double score) {
        var programming = programmingParticipation();
        var submission = new ProgrammingSubmission();
        submission.setId(1L);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        programming.addSubmission(submission);
        var completedResult = new Result();
        completedResult.setId(1L);
        completedResult.setCompletionDate(ZonedDateTime.now());
        completedResult.setRated(rated);
        completedResult.setScore(score);
        submission.addResult(completedResult);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(programming));
        var olderGrade = new ExamGradeScoreDTO(programming.getId(), 1L, 2L, 80.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(olderGrade));

        assertThat(result.pendingExerciseIds()).isEmpty();
        assertThat(result.studentResult().overallPointsAchieved()).isZero();
        assertThat(result.studentResult().overallScoreAchieved()).isZero();
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void unscoredAttemptsRetainAssignedExerciseAndNonEmptyMetadata(boolean manualOnly, boolean instructorView) {
        StudentParticipation participation;
        if (manualOnly) {
            var text = new TextExercise();
            text.setId(2L);
            text.setTitle("Manual essay");
            text.setMaxPoints(20.0);
            text.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
            text.setExerciseGroup(quiz.getExerciseGroup());
            attempt.setExercises(List.of(text));
            participation = new StudentParticipation();
            participation.setId(2L);
            participation.setExercise(text);
            var submission = new TextSubmission();
            submission.setId(2L);
            submission.setText("An actual non-empty answer");
            participation.addSubmission(submission);
        }
        else {
            participation = programmingParticipation();
            participation.getExercise().setTitle("Programming");
            var submission = new ProgrammingSubmission();
            submission.setId(2L);
            submission.setType(SubmissionType.MANUAL);
            submission.setSubmitted(true);
            submission.setSubmissionDate(ZonedDateTime.now());
            participation.addSubmission(submission);
        }
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(participation));
        de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO.StudentResultDTO result;
        if (instructorView) {
            exam.setExerciseGroups(List.of(quiz.getExerciseGroup()));
            exam.setNumberOfCorrectionRoundsInExam(1);
            when(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(1L)).thenReturn(exam);
            when(studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(1L)).thenReturn(Set.of(attempt));
            when(studentParticipationRepository.findGradesByExamId(1L)).thenReturn(Set.of());
            result = examService.calculateExamScores(1L).studentResults().getFirst();
        }
        else {
            var summary = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());
            assertThat(summary.achievedPointsPerExercise()).isEmpty();
            result = summary.studentResult();
        }

        assertThat(result.exerciseGroupIdToExerciseResult()).containsKey(quiz.getExerciseGroup().getId());
        var metadata = result.exerciseGroupIdToExerciseResult().get(quiz.getExerciseGroup().getId());
        assertThat(metadata.exerciseId()).isEqualTo(2L);
        assertThat(metadata.hasNonEmptySubmission()).isTrue();
        assertThat(metadata.achievedPoints()).isNull();
        assertThat(metadata.achievedScore()).isNull();
        assertThat(result.overallPointsAchieved()).isEqualTo(manualOnly ? 0.0 : null);
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
        assertThat(result.studentResult().gradeWithBonus().pending()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BonusStrategy.class, names = { "POINTS", "GRADES_CONTINUOUS" })
    void pendingBonusSourceKeepsTheTargetScoreWithoutInventingAFinalGrade(BonusStrategy strategy) {
        var scale = new GradingScale();
        scale.setExam(exam);
        scale.setBonusStrategy(strategy);
        var passing = new GradeStep();
        passing.setGradeName("1.0");
        passing.setIsPassingGrade(true);
        var sourceCourse = new Course();
        sourceCourse.setId(2L);
        sourceCourse.setMaxPoints(100);
        sourceCourse.setTitle("Pending bonus source");
        var sourceScale = new GradingScale();
        sourceScale.setCourse(sourceCourse);
        var bonus = new Bonus();
        bonus.setSourceGradingScale(sourceScale);
        scale.addBonusFrom(bonus);
        when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.of(scale));
        when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(scale))).thenReturn(passing);
        // This is the nullable source-result contract produced by an exam whose submitted programming build is pending.
        when(courseScoreCalculationService.calculateCourseScoresForExamBonusSource(sourceCourse, sourceScale, List.of(1L)))
                .thenReturn(Map.of(1L, new BonusSourceResultDTO(null, null, null, null, true)));
        when(courseScoreCalculationService.calculateReachablePoints(eq(sourceScale), org.mockito.ArgumentMatchers.any())).thenReturn(100.0);
        // Exercise the real arithmetic if the pending source is incorrectly forwarded to the bonus strategy.
        org.mockito.Mockito.lenient().when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), org.mockito.ArgumentMatchers.nullable(Double.class), eq(100.0)))
                .thenAnswer(invocation -> new BonusService(null, gradingScaleRepository).calculateGradeWithBonus(bonus, invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3)));

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 90.0)));

        assertThat(result.studentResult().overallPointsAchieved()).isEqualTo(9.0);
        assertThat(result.studentResult().overallScoreAchieved()).isEqualTo(90.0);
        assertThat(result.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(result.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isNull();
        assertThat(result.studentResult().gradeWithBonus().finalPoints()).isNull();
        assertThat(result.studentResult().gradeWithBonus().finalGrade()).isNull();
        assertThat(result.studentResult().gradeWithBonus().pending()).isTrue();
        assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsString(result.studentResult().gradeWithBonus())).contains("\"pending\":true");
        verifyNoInteractions(bonusService);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void practiceBonusUsesTheLatestSubmittedSourceAttemptIncludingWhenItIsPending(boolean pending) {
        var sourceExam = sourceExam();
        var previous = sourceAttempt(10L, sourceExam, attempt.getUser(), true, false);
        var latest = sourceAttempt(11L, sourceExam, attempt.getUser(), true, false);
        var unsubmitted = sourceAttempt(12L, sourceExam, attempt.getUser(), false, false);
        var testRun = sourceAttempt(13L, sourceExam, attempt.getUser(), true, true);
        var sourceExercise = new ProgrammingExercise();
        sourceExercise.setId(2L);
        sourceExercise.setMaxPoints(20.0);
        sourceExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        sourceExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        sourceExercise.setExerciseGroup(sourceExam.getExerciseGroups().getFirst());
        latest.setExercises(List.of(sourceExercise));
        var sourceParticipation = new StudentParticipation();
        sourceParticipation.setId(11L);
        sourceParticipation.setExercise(sourceExercise);
        var submission = new ProgrammingSubmission();
        submission.setId(11L);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        sourceParticipation.addSubmission(submission);
        if (!pending) {
            var completedResult = new Result();
            completedResult.setId(11L);
            completedResult.setCompletionDate(ZonedDateTime.now());
            completedResult.setRated(true);
            completedResult.setScore(80.0);
            submission.addResult(completedResult);
        }
        when(examRepository.findByIdElseThrow(2L)).thenReturn(sourceExam);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(latest, false)).thenReturn(List.of(sourceParticipation));
        var sourceGrade = examService.calculateStudentResultWithGradeAndPoints(latest,
                pending ? Set.of() : Set.of(new ExamGradeScoreDTO(11L, 1L, 2L, 80.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY)));
        var sourceScale = new GradingScale();
        sourceScale.setExam(sourceExam);
        var bonus = bonusFrom(sourceScale);
        when(userRepository.findByIdWithAuthoritiesElseThrow(1L)).thenReturn(attempt.getUser());
        org.mockito.Mockito.lenient().when(studentExamRepository.findWithExercisesByUserIdAndExamId(1L, 2L, false)).thenThrow(new IncorrectResultSizeDataAccessException(1, 4));
        org.mockito.Mockito.lenient().when(studentExamRepository.findStudentExamsForTestExamsByUserIdAndExamId(1L, 2L)).thenReturn(List.of(previous, latest, unsubmitted, testRun));
        org.mockito.Mockito.lenient().when(studentExamRepository.findWithExercisesById(11L)).thenReturn(Optional.of(latest));
        org.mockito.Mockito.lenient().doReturn(sourceGrade).when(examService).getStudentExamGradesForSummary(attempt.getUser(), latest, false);
        if (!pending) {
            org.mockito.Mockito.lenient().when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), eq(16.0), eq(20.0)))
                    .thenReturn(new BonusExampleDTO(90.0, 16.0, "1.0", 1.0, 100.0, "1.0", false));
        }

        var target = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 90.0)));

        assertThat(target.studentResult().gradeWithBonus().pending()).isEqualTo(pending);
        assertThat(target.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(pending ? null : 16.0);
        org.mockito.Mockito.verify(examService).getStudentExamGradesForSummary(attempt.getUser(), latest, false);
        org.mockito.Mockito.verify(studentExamRepository, org.mockito.Mockito.never()).findWithExercisesByUserIdAndExamId(1L, 2L, false);
    }

    @Test
    void bulkBonusCalculationUsesOneLatestSubmittedPracticeAttemptPerStudent() {
        var sourceExam = sourceExam();
        sourceExam.setExamMaxPoints(100);
        var secondUser = new User();
        secondUser.setId(2L);
        var secondTarget = sourceAttempt(2L, exam, secondUser, true, false);
        secondTarget.setExercises(List.of(quiz));
        var secondParticipation = new StudentParticipation();
        secondParticipation.setId(2L);
        secondParticipation.setExercise(quiz);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(secondTarget, false)).thenReturn(List.of(secondParticipation));
        exam.setExerciseGroups(List.of(quiz.getExerciseGroup()));
        exam.setNumberOfCorrectionRoundsInExam(1);
        when(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(1L)).thenReturn(exam);
        when(studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(1L)).thenReturn(Set.of(attempt, secondTarget));
        when(studentParticipationRepository.findGradesByExamId(1L))
                .thenReturn(Set.of(grade(1L, 60.0), new ExamGradeScoreDTO(2L, 2L, 1L, 60.0, 10.0, IncludedInOverallScore.INCLUDED_COMPLETELY)));
        var sourceQuiz = new QuizExercise();
        sourceQuiz.setId(2L);
        sourceQuiz.setMaxPoints(20.0);
        sourceQuiz.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        sourceQuiz.setExerciseGroup(sourceExam.getExerciseGroups().getFirst());
        var sourceAttempts = new HashSet<StudentExam>();
        var sourceGrades = new HashSet<ExamGradeScoreDTO>();
        for (long id : List.of(10L, 11L, 12L, 13L, 20L, 21L)) {
            var source = sourceAttempt(id, sourceExam, id < 20 ? attempt.getUser() : secondUser, id != 12, id == 13);
            source.setExercises(List.of(sourceQuiz));
            sourceAttempts.add(source);
            var participation = new StudentParticipation();
            participation.setId(id);
            participation.setExercise(sourceQuiz);
            org.mockito.Mockito.lenient().when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(source, false)).thenReturn(List.of(participation));
            sourceGrades.add(new ExamGradeScoreDTO(id, source.getUser().getId(), 2L, id == 11 ? 60.0 : id == 21 ? 80.0 : 100.0, 20.0, IncludedInOverallScore.INCLUDED_COMPLETELY));
        }
        when(examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(2L)).thenReturn(sourceExam);
        when(studentExamRepository.findAllWithoutTestRunsWithExercisesByExamId(2L)).thenReturn(sourceAttempts);
        when(studentParticipationRepository.findGradesByExamId(2L)).thenReturn(sourceGrades);
        var sourceScale = new GradingScale();
        sourceScale.setExam(sourceExam);
        var bonus = bonusFrom(sourceScale);
        org.mockito.Mockito.lenient().when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), anyDouble(), eq(20.0))).thenAnswer(invocation -> {
            double sourcePoints = invocation.getArgument(2);
            return new BonusExampleDTO(60.0, sourcePoints, "1.0", 1.0, 60 + sourcePoints, "1.0", false);
        });

        var results = examService.calculateExamScores(1L).studentResults();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(result -> result.gradeWithBonus().studentPointsOfBonusSource()).containsExactlyInAnyOrder(12.0, 16.0);
    }

    @Test
    void manualOnlySourceHasAuthoritativeZeroPointsAndDoesNotRemainPending() {
        var manual = new TextExercise();
        manual.setId(2L);
        manual.setMaxPoints(100.0);
        manual.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        manual.setExerciseGroup(quiz.getExerciseGroup());
        attempt.setExercises(List.of(manual));
        var manualSource = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of());
        assertThat(manualSource.maxPoints()).isZero();
        assertThat(manualSource.studentResult().overallPointsAchieved()).isZero();
        assertThat(manualSource.pendingExerciseIds()).isEmpty();
        attempt.setExercises(List.of(quiz));
        var course = new Course();
        course.setId(2L);
        course.setMaxPoints(100);
        var sourceScale = new GradingScale();
        sourceScale.setCourse(course);
        var bonus = bonusFrom(sourceScale);
        when(courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, sourceScale, List.of(1L)))
                .thenReturn(Map.of(1L, new BonusSourceResultDTO(manualSource.studentResult().overallPointsAchieved(), null, null, null, true)));
        when(courseScoreCalculationService.calculateReachablePoints(eq(sourceScale), org.mockito.ArgumentMatchers.any())).thenReturn(100.0);
        when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), eq(0.0), eq(100.0))).thenReturn(new BonusExampleDTO(90.0, 0.0, "1.0", 0.0, 90.0, "1.0", false));

        var target = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 90.0)));

        assertThat(target.studentResult().gradeWithBonus().pending()).isFalse();
        assertThat(target.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isZero();
        assertThat(target.studentResult().gradeWithBonus().finalGrade()).isEqualTo("1.0");
        assertThat(target.studentResult().gradeWithBonus().finalPoints()).isEqualTo(9.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void practiceBonusUsesItsAutomaticDenominatorAndKeepsManualOnlySourcesTerminal(boolean manualOnly) {
        var sourceExam = sourceExam();
        sourceExam.setExamMaxPoints(100);
        var sourceAttempt = sourceAttempt(11L, sourceExam, attempt.getUser(), true, false);
        var sourceQuiz = new QuizExercise();
        sourceQuiz.setId(2L);
        sourceQuiz.setMaxPoints(30.5);
        sourceQuiz.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        sourceQuiz.setExerciseGroup(sourceExam.getExerciseGroups().getFirst());
        var manual = new TextExercise();
        manual.setId(3L);
        manual.setMaxPoints(manualOnly ? 100.0 : 69.5);
        manual.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        manual.setExerciseGroup(sourceQuiz.getExerciseGroup());
        sourceAttempt.setExercises(manualOnly ? List.of(manual) : List.of(sourceQuiz, manual));
        var participation = new StudentParticipation();
        participation.setId(11L);
        participation.setExercise(sourceQuiz);
        when(examRepository.findByIdElseThrow(2L)).thenReturn(sourceExam);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(sourceAttempt, false)).thenReturn(List.of(participation));
        var sourceGrade = examService.calculateStudentResultWithGradeAndPoints(sourceAttempt,
                Set.of(new ExamGradeScoreDTO(11L, 1L, 2L, 20.0 / 30.5 * 100, 30.5, IncludedInOverallScore.INCLUDED_COMPLETELY)));
        assertThat(sourceGrade.maxPoints()).isEqualTo(manualOnly ? 0.0 : 30.5);
        assertThat(sourceGrade.studentResult().overallPointsAchieved()).isEqualTo(manualOnly ? 0.0 : 20.0);
        var sourceScale = new GradingScale();
        sourceScale.setExam(sourceExam);
        var bonus = bonusFrom(sourceScale, false);
        bonus.setWeight(1.0);
        var before = new GradeStep();
        before.setGradeName("3.0");
        before.setIsPassingGrade(true);
        var after = new GradeStep();
        after.setGradeName("1.0");
        after.setIsPassingGrade(true);
        when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(bonus.getBonusToGradingScale())))
                .thenAnswer(invocation -> (double) invocation.getArgument(0) >= 90 ? after : before);
        var noBonus = new GradeStep();
        noBonus.setGradeName("0.0");
        var earnedBonus = new GradeStep();
        earnedBonus.setGradeName("10.0");
        when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(sourceScale)))
                .thenAnswer(invocation -> (double) invocation.getArgument(0) >= 50 ? earnedBonus : noBonus);
        when(userRepository.findByIdWithAuthoritiesElseThrow(1L)).thenReturn(attempt.getUser());
        when(studentExamRepository.findStudentExamsForTestExamsByUserIdAndExamId(1L, 2L)).thenReturn(List.of(sourceAttempt));
        when(studentExamRepository.findWithExercisesById(11L)).thenReturn(Optional.of(sourceAttempt));
        org.mockito.Mockito.doReturn(sourceGrade).when(examService).getStudentExamGradesForSummary(attempt.getUser(), sourceAttempt, false);
        when(bonusService.calculateGradeWithBonus(eq(bonus), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> new BonusService(null, gradingScaleRepository)
                .calculateGradeWithBonus(bonus, invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));

        var target = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 80.0)));

        assertThat(target.studentResult().overallPointsAchieved()).isEqualTo(8.0);
        assertThat(target.studentResult().overallGrade()).isEqualTo("3.0");
        assertThat(target.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(manualOnly ? 0.0 : 20.0);
        assertThat(target.studentResult().gradeWithBonus().bonusGrade()).isEqualTo(manualOnly ? "0.0" : "10.0");
        assertThat(target.studentResult().gradeWithBonus().finalPoints()).isEqualTo(manualOnly ? 8.0 : 9.0);
        assertThat(target.studentResult().gradeWithBonus().finalGrade()).isEqualTo(manualOnly ? "3.0" : "1.0");
        assertThat(target.studentResult().gradeWithBonus().pending()).isFalse();
        double expectedSourcePercentage = manualOnly ? 0.0 : 20.0 / 30.5 * 100;
        org.mockito.Mockito.verify(gradingScaleRepository).matchPercentageToGradeStep(
                org.mockito.ArgumentMatchers.doubleThat(value -> Double.isFinite(value) && Math.abs(value - expectedSourcePercentage) < 1e-9), eq(sourceScale));
    }

    private Bonus bonusFrom(GradingScale sourceScale) {
        return bonusFrom(sourceScale, true);
    }

    private Bonus bonusFrom(GradingScale sourceScale, boolean stubTargetGrade) {
        var targetScale = new GradingScale();
        targetScale.setExam(exam);
        targetScale.setBonusStrategy(BonusStrategy.POINTS);
        var bonus = new Bonus();
        bonus.setSourceGradingScale(sourceScale);
        targetScale.addBonusFrom(bonus);
        var passing = new GradeStep();
        passing.setGradeName("1.0");
        passing.setIsPassingGrade(true);
        when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.of(targetScale));
        if (stubTargetGrade) {
            when(gradingScaleRepository.matchPercentageToGradeStep(anyDouble(), eq(targetScale))).thenReturn(passing);
        }
        return bonus;
    }

    private Exam sourceExam() {
        var source = new Exam();
        source.setId(2L);
        source.setCourse(exam.getCourse());
        source.setTestExam(true);
        source.setExamMaxPoints(20);
        source.setNumberOfCorrectionRoundsInExam(1);
        var group = new ExerciseGroup();
        group.setId(2L);
        group.setExam(source);
        source.setExerciseGroups(List.of(group));
        return source;
    }

    private StudentExam sourceAttempt(long id, Exam sourceExam, User user, boolean submitted, boolean testRun) {
        var source = new StudentExam();
        source.setId(id);
        source.setExam(sourceExam);
        source.setUser(user);
        source.setSubmitted(submitted);
        source.setTestRun(testRun);
        source.setCreatedDate(Instant.EPOCH.plusSeconds(id));
        return source;
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
