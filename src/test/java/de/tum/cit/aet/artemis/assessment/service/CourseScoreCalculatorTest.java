package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreContextDTO;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreSettingsDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradedPresentationConfigDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentCourseScoreInputDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

class CourseScoreCalculatorTest {

    private static final long STUDENT_ID = 42L;

    private static final long COURSE_ID = 7L;

    private static final ZonedDateTime CALCULATION_TIME = ZonedDateTime.of(2026, 8, 8, 12, 0, 0, 0, ZoneOffset.UTC);

    private static final CourseScoreSettingsDTO SETTINGS = new CourseScoreSettingsDTO(1, null);

    @Test
    void shouldCalculateIncludedBonusAndRatedScoresFromProjectedInputs() {
        ExerciseCourseScoreDTO completelyIncluded = exercise(1L, IncludedInOverallScore.INCLUDED_COMPLETELY, 10.0, null, null);
        ExerciseCourseScoreDTO bonus = exercise(2L, IncludedInOverallScore.INCLUDED_AS_BONUS, 5.0, null, null);
        ExerciseCourseScoreDTO notIncluded = exercise(3L, IncludedInOverallScore.NOT_INCLUDED, 20.0, null, null);
        ExerciseCourseScoreDTO future = new ExerciseCourseScoreDTO(4L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL,
                CALCULATION_TIME.plusDays(1), CALCULATION_TIME.plusDays(2), null, 30.0, 0.0, COURSE_ID, null, null);
        ExerciseCourseScoreDTO unrated = exercise(5L, IncludedInOverallScore.INCLUDED_COMPLETELY, 2.0, null, null);
        CourseScoreContextDTO context = CourseScoreCalculator.createContext(SETTINGS, null, Set.of(completelyIncluded, bonus, notIncluded, future, unrated), CALCULATION_TIME);

        StudentCourseScoreInputDTO input = input(List.of(grade(1L, 80.0, true), grade(2L, 100.0, true), grade(3L, 100.0, true), grade(4L, 100.0, true),
                new CourseGradeScoreDTO(5L, STUDENT_ID, 5L, 100.0, false, null, ExerciseType.TEXT)), List.of(), 0.0, 0);
        StudentScoresDTO scores = CourseScoreCalculator.calculateCourseScoreForStudent(context, input);

        assertThat(context.maxAndReachablePoints().maxPoints()).isEqualTo(12.0);
        assertThat(context.maxAndReachablePoints().reachablePoints()).isEqualTo(12.0);
        assertThat(scores.absoluteScore()).isEqualTo(13.0);
        assertThat(scores.relativeScore()).isEqualTo(108.3);
        assertThat(scores.currentRelativeScore()).isEqualTo(108.3);
    }

    @Test
    void shouldCapVariantPointsAfterApplyingPointDeduction() {
        ExerciseCourseScoreDTO firstVariant = exercise(1L, IncludedInOverallScore.INCLUDED_COMPLETELY, 5.0, 11L, 5.0);
        ExerciseCourseScoreDTO secondVariant = exercise(2L, IncludedInOverallScore.INCLUDED_COMPLETELY, 5.0, 11L, 5.0);
        CourseScoreContextDTO context = CourseScoreCalculator.createContext(SETTINGS, null, Set.of(firstVariant, secondVariant), CALCULATION_TIME);
        PlagiarismCaseScoreDTO deduction = new PlagiarismCaseScoreDTO(STUDENT_ID, 1L, PlagiarismVerdict.POINT_DEDUCTION, 50);
        StudentCourseScoreInputDTO input = input(List.of(grade(1L, 100.0, true), grade(2L, 100.0, true)), List.of(deduction), 0.0, 0);

        StudentScoresDTO scores = CourseScoreCalculator.calculateCourseScoreForStudent(context, input);

        assertThat(scores.absoluteScore()).isEqualTo(5.0);
        assertThat(scores.absoluteScoreTotal()).isEqualTo(7.5);
        assertThat(CourseScoreCalculator.calculateAchievedPointsPerVariantGroup(context, input)).containsExactlyEntriesOf(java.util.Map.of(11L, 5.0));
    }

    @Test
    void shouldZeroEveryScoreForPlagiarismVerdict() {
        CourseScoreContextDTO context = CourseScoreCalculator.createContext(SETTINGS, null, Set.of(exercise(1L, IncludedInOverallScore.INCLUDED_COMPLETELY, 10.0, 11L, 10.0)),
                CALCULATION_TIME);
        StudentCourseScoreInputDTO input = input(List.of(grade(1L, 100.0, true)), List.of(new PlagiarismCaseScoreDTO(STUDENT_ID, 1L, PlagiarismVerdict.PLAGIARISM, 0)), 0.0, 0);

        assertThat(CourseScoreCalculator.calculateCourseScoreForStudent(context, input)).isEqualTo(new StudentScoresDTO(0.0, 0.0, 0.0, 0.0, 0.0));
        assertThat(CourseScoreCalculator.calculateAchievedPointsPerVariantGroup(context, input)).isEmpty();
    }

    @Test
    void shouldCalculateGradedAndBasicPresentationsFromExplicitInputs() {
        ExerciseCourseScoreDTO exercise = exercise(1L, IncludedInOverallScore.INCLUDED_COMPLETELY, 5.0, null, null);
        CourseScoreContextDTO gradedContext = CourseScoreCalculator.createContext(SETTINGS, new GradedPresentationConfigDTO(2, 37.5), Set.of(exercise), CALCULATION_TIME);
        StudentCourseScoreInputDTO gradedInput = input(List.of(), List.of(), 150.0, 0);

        StudentScoresDTO gradedScores = CourseScoreCalculator.calculateCourseScoreForStudent(gradedContext, gradedInput);

        assertThat(gradedContext.maxAndReachablePoints().reachablePresentationPoints()).isEqualTo(3.0);
        assertThat(gradedScores.presentationScore()).isEqualTo(2.3);
        assertThat(gradedScores.absoluteScore()).isEqualTo(2.3);

        CourseScoreContextDTO basicContext = CourseScoreCalculator.createContext(new CourseScoreSettingsDTO(1, 2), null, Set.of(exercise), CALCULATION_TIME);
        StudentScoresDTO basicScores = CourseScoreCalculator.calculateCourseScoreForStudent(basicContext, input(List.of(), List.of(), 0.0, 1));

        assertThat(basicScores.presentationScore()).isEqualTo(1.0);
        assertThat(basicScores.absoluteScore()).isZero();
    }

    @Test
    void shouldUseTheProvidedCalculationTimeForAutomaticAssessment() {
        ExerciseCourseScoreDTO exercise = new ExerciseCourseScoreDTO(1L, ExerciseType.PROGRAMMING, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.AUTOMATIC,
                CALCULATION_TIME.minusDays(1), CALCULATION_TIME.minusHours(1), CALCULATION_TIME.plusMinutes(1), 10.0, 0.0, COURSE_ID, null, null);

        CourseScoreContextDTO beforeFinalBuild = CourseScoreCalculator.createContext(SETTINGS, null, Set.of(exercise), CALCULATION_TIME);
        CourseScoreContextDTO afterFinalBuild = CourseScoreCalculator.createContext(SETTINGS, null, Set.of(exercise), CALCULATION_TIME.plusMinutes(2));

        assertThat(beforeFinalBuild.maxAndReachablePoints().maxPoints()).isZero();
        assertThat(afterFinalBuild.maxAndReachablePoints().maxPoints()).isEqualTo(10.0);
        assertThat(afterFinalBuild.maxAndReachablePoints().reachablePoints()).isEqualTo(10.0);
    }

    private static ExerciseCourseScoreDTO exercise(long id, IncludedInOverallScore includedInOverallScore, double maxPoints, Long variantGroupId, Double variantGroupMaxPoints) {
        return new ExerciseCourseScoreDTO(id, ExerciseType.TEXT, includedInOverallScore, AssessmentType.MANUAL, CALCULATION_TIME.minusDays(2), CALCULATION_TIME.minusDays(1), null,
                maxPoints, 0.0, COURSE_ID, variantGroupId, variantGroupMaxPoints);
    }

    private static CourseGradeScoreDTO grade(long exerciseId, double score, boolean rated) {
        return new CourseGradeScoreDTO(exerciseId, STUDENT_ID, exerciseId, score, rated, null, ExerciseType.TEXT);
    }

    private static StudentCourseScoreInputDTO input(List<CourseGradeScoreDTO> grades, List<PlagiarismCaseScoreDTO> plagiarismCases, double presentationScoreSum,
            long basicPresentationScoreCount) {
        return new StudentCourseScoreInputDTO(STUDENT_ID, grades, plagiarismCases, presentationScoreSum, basicPresentationScoreCount);
    }
}
