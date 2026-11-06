package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
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
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExamGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
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
        when(examRepository.findByIdElseThrow(1L)).thenReturn(exam);
        when(studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(attempt, false)).thenReturn(List.of(participation));
        when(gradingScaleRepository.findByExamIdWithBonusFrom(1L)).thenReturn(Optional.empty());
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

        var result = examService.calculateStudentResultWithGradeAndPoints(attempt, Set.of(grade(1L, 100.0)));

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

    private ExamGradeScoreDTO grade(long participationId, double score) {
        return new ExamGradeScoreDTO(participationId, 1L, quiz.getId(), score, quiz.getMaxPoints(), IncludedInOverallScore.INCLUDED_COMPLETELY);
    }
}
