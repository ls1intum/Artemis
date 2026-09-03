package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundToNDecimalPlaces;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreContextDTO;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreSettingsDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradedPresentationConfigDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePointsDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentCourseScoreInputDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * The course score calculation, as a pure function.
 * <p>
 * Every input is a parameter and every output is a return value: this class holds no state, has no collaborators and
 * reaches neither the database nor the clock. That is deliberate and enforced by construction — it is static, so it
 * cannot be handed a repository. Callers fetch everything up front and pass one calculation time, which
 * makes the cost of scoring a course visible at the call site instead of hidden behind a lazy association or a query per
 * student.
 */
public final class CourseScoreCalculator {

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    private CourseScoreCalculator() {
    }

    /**
     * Builds the course-level part of a score calculation, which every student in the course shares.
     *
     * @param settings           the course settings the calculation rounds and scores by
     * @param presentationConfig the graded presentation configuration, or {@code null} when the course awards no presentation points
     * @param exercises          the exercises to score, either all of a course or all of one exercise type
     * @param calculationTime    the instant used for all due-date decisions
     * @return the context to hand to {@link #calculateCourseScoreForStudent}
     */
    public static CourseScoreContextDTO createContext(CourseScoreSettingsDTO settings, @Nullable GradedPresentationConfigDTO presentationConfig,
            Set<ExerciseCourseScoreDTO> exercises, ZonedDateTime calculationTime) {
        return new CourseScoreContextDTO(settings, presentationConfig, exercises, calculationTime,
                calculateMaxAndReachablePoints(settings, presentationConfig, exercises, calculationTime));
    }

    /**
     * Calculates max and reachable max points for the given exercises, plus the reachable presentation points when the
     * course awards them.
     * <p>
     * Max points are the sum of the points for all included (see {@link #includeIntoScoreCalculation}) exercises whose
     * due date has passed or is unset, or which are automatically assessed with their build-and-test date in the past.
     * Reachable max points only count exercises whose assessment is done (see {@link #isAssessmentDone}). An exercise
     * that is not automatically assessed with a due date in the past but an assessment due date in the future therefore
     * counts towards max points but not towards reachable max points.
     *
     * @param settings           the course settings the calculation rounds by
     * @param presentationConfig the graded presentation configuration, or {@code null} to calculate no presentation points
     * @param exercises          the exercises which are included into the max points calculation
     * @return the max and reachable max points for the given exercises
     */
    private static MaxAndReachablePointsDTO calculateMaxAndReachablePoints(CourseScoreSettingsDTO settings, @Nullable GradedPresentationConfigDTO presentationConfig,
            Set<ExerciseCourseScoreDTO> exercises, ZonedDateTime calculationTime) {
        var completelyIncludedNonVariants = exercises.stream().filter(exercise -> !isExerciseVariant(exercise))
                .filter(exercise -> includeIntoScoreCalculation(exercise, calculationTime))
                .filter(exercise -> exercise.includedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY).toList();

        double maxPoints = completelyIncludedNonVariants.stream().mapToDouble(ExerciseCourseScoreDTO::maxPoints).sum();
        double reachableMaxPoints = completelyIncludedNonVariants.stream().filter(exercise -> isAssessmentDone(exercise, calculationTime))
                .mapToDouble(ExerciseCourseScoreDTO::maxPoints).sum();

        // Variant groups each contribute at most their configured maxPoints.
        MaxAndReachablePointsDTO variantGroupPoints = calculateVariantGroupMaxAndReachablePoints(exercises, calculationTime);
        maxPoints += variantGroupPoints.maxPoints();
        reachableMaxPoints += variantGroupPoints.reachablePoints();

        double reachablePresentationPoints = calculateReachablePresentationPoints(presentationConfig, reachableMaxPoints, settings.accuracyOfScores());
        maxPoints += reachablePresentationPoints;
        reachableMaxPoints += reachablePresentationPoints;

        return new MaxAndReachablePointsDTO(maxPoints, reachableMaxPoints, reachablePresentationPoints);
    }

    /**
     * Calculates the max and reachable max points contributed by the exercise variant groups among the given exercises.
     * Each group's variant max points are summed and then capped at the group's configured maxPoints. Non-variant
     * exercises are ignored here (they are summed up separately).
     *
     * @param exercises the exercises which are checked for variant group membership
     * @return the capped max and reachable max points contributed by the variant groups (presentation points are always 0)
     */
    private static MaxAndReachablePointsDTO calculateVariantGroupMaxAndReachablePoints(Set<ExerciseCourseScoreDTO> exercises, ZonedDateTime calculationTime) {
        var maxPointsPerGroup = new VariantGroupCappedSum();
        var reachableMaxPointsPerGroup = new VariantGroupCappedSum();

        var completelyIncludedVariants = exercises.stream().filter(CourseScoreCalculator::isExerciseVariant)
                .filter(exercise -> includeIntoScoreCalculation(exercise, calculationTime))
                .filter(exercise -> exercise.includedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY).toList();
        completelyIncludedVariants.forEach(exercise -> maxPointsPerGroup.add(exercise.variantGroupId(), exercise.variantGroupMaxPoints(), exercise.maxPoints()));
        completelyIncludedVariants.stream().filter(exercise -> isAssessmentDone(exercise, calculationTime))
                .forEach(exercise -> reachableMaxPointsPerGroup.add(exercise.variantGroupId(), exercise.variantGroupMaxPoints(), exercise.maxPoints()));

        return new MaxAndReachablePointsDTO(maxPointsPerGroup.total(), reachableMaxPointsPerGroup.total(), 0.0);
    }

    /**
     * Calculates the presentation score and the relative and absolute points a student achieved in the course, taking
     * the effect of plagiarism verdicts on the grade into account.
     *
     * @param context      the course-level part of the calculation, from {@link #createContext}
     * @param studentInput every projected input specific to the student
     * @return the presentation score and the relative and absolute points achieved by the given student
     */
    public static StudentScoresDTO calculateCourseScoreForStudent(CourseScoreContextDTO context, StudentCourseScoreInputDTO studentInput) {
        Map<Long, PlagiarismCaseScoreDTO> plagiarismCasesForStudent = plagiarismCasesForStudent(studentInput);
        if (studentHasVerdict(plagiarismCasesForStudent.values(), PlagiarismVerdict.PLAGIARISM)) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0.0, 0);
        }

        CourseScoreSettingsDTO settings = context.settings();
        Map<Long, CourseGradeScoreDTO> gradeScorePerExercise = ratedGradeScoresPerExercise(studentInput.gradeScores());

        // Non-variant exercises are summed individually.
        double pointsAchievedByStudentInCourse = context.exercises().stream().filter(exercise -> !isExerciseVariant(exercise))
                .filter(exercise -> includeIntoScoreCalculation(exercise, context.calculationTime()))
                .flatMapToDouble(exercise -> Optional.ofNullable(gradeScorePerExercise.get(exercise.id())).stream()
                        .mapToDouble(gradeScore -> calculatePointsAchievedFromExercise(exercise, gradeScore.score(), plagiarismCasesForStudent.get(exercise.id()), settings)))
                .sum();

        // Exercise variants: the points a student earns across a group's variants are capped at the group's configured maxPoints.
        VariantGroupCappedSum variantGroupAchievedPoints = calculateVariantGroupAchievedPoints(context, gradeScorePerExercise, plagiarismCasesForStudent);
        pointsAchievedByStudentInCourse += variantGroupAchievedPoints.total();
        double pointsCappedAwayFromVariantGroups = variantGroupAchievedPoints.uncappedTotal() - variantGroupAchievedPoints.total();

        double gradedPresentationScore = calculatePresentationPoints(context.presentationConfig(), context.maxAndReachablePoints().reachablePresentationPoints(),
                studentInput.gradedPresentationScoreSum(), settings.accuracyOfScores());
        double basicPresentationScore = Optional.ofNullable(settings.presentationScore()).filter(requiredScore -> !context.usesGradedPresentations())
                .filter(requiredScore -> requiredScore > 0).map(requiredScore -> (double) studentInput.basicPresentationScoreCount()).orElse(0.0);
        double presentationScore = gradedPresentationScore + basicPresentationScore;
        pointsAchievedByStudentInCourse += gradedPresentationScore;

        return buildStudentScores(settings, context.maxAndReachablePoints(), pointsAchievedByStudentInCourse, pointsAchievedByStudentInCourse + pointsCappedAwayFromVariantGroups,
                presentationScore);
    }

    /**
     * Calculates the points a student earns from the exercise variant groups among the given exercises. Each group's
     * earned variant points are summed and then capped at the group's configured maxPoints. Non-variant exercises are
     * ignored here (they are summed up separately).
     *
     * @param context                   the course-level score context
     * @param gradeScorePerExercise     the student's achieved scores per exercise id
     * @param plagiarismCasesForStudent the plagiarism cases of the student per exercise id
     * @return the per-group accumulator of the points the student earns from the variant groups (see
     *         {@link VariantGroupCappedSum#total()} for the capped and {@link VariantGroupCappedSum#uncappedTotal()} for the uncapped sum)
     */
    private static VariantGroupCappedSum calculateVariantGroupAchievedPoints(CourseScoreContextDTO context, Map<Long, CourseGradeScoreDTO> gradeScorePerExercise,
            Map<Long, PlagiarismCaseScoreDTO> plagiarismCasesForStudent) {
        var achievedPointsPerGroup = new VariantGroupCappedSum();
        context.exercises().stream().filter(CourseScoreCalculator::isExerciseVariant).filter(exercise -> includeIntoScoreCalculation(exercise, context.calculationTime()))
                .forEach(exercise -> Optional.ofNullable(gradeScorePerExercise.get(exercise.id()))
                        .ifPresent(gradeScore -> achievedPointsPerGroup.add(exercise.variantGroupId(), exercise.variantGroupMaxPoints(),
                                calculatePointsAchievedFromExercise(exercise, gradeScore.score(), plagiarismCasesForStudent.get(exercise.id()), context.settings()))));
        return achievedPointsPerGroup;
    }

    /**
     * Calculates the plagiarism-adjusted points a student earns per variant group, keyed by group id. Groups with a
     * configured {@code maxPoints} are capped at it; groups without one contribute their raw sum. A course-wide
     * {@link PlagiarismVerdict#PLAGIARISM} verdict zeroes the whole course (empty map).
     *
     * @param context      the course-level part of the calculation, from {@link #createContext}
     * @param studentInput every projected input specific to the student
     * @return the plagiarism-adjusted points per variant group id, capped where a cap is configured; empty when no
     *         variant group contributes
     */
    public static Map<Long, Double> calculateAchievedPointsPerVariantGroup(CourseScoreContextDTO context, StudentCourseScoreInputDTO studentInput) {
        Map<Long, PlagiarismCaseScoreDTO> plagiarismCasesForStudent = plagiarismCasesForStudent(studentInput);
        if (studentHasVerdict(plagiarismCasesForStudent.values(), PlagiarismVerdict.PLAGIARISM)) {
            return Map.of();
        }
        Map<Long, CourseGradeScoreDTO> gradeScorePerExercise = ratedGradeScoresPerExercise(studentInput.gradeScores());

        var achievedPointsPerGroup = new VariantGroupCappedSum();
        context.exercises().stream().filter(exercise -> exercise.variantGroupId() != null).filter(exercise -> hasCountablePoints(exercise, context.calculationTime()))
                .forEach(exercise -> Optional.ofNullable(gradeScorePerExercise.get(exercise.id()))
                        .ifPresent(gradeScore -> achievedPointsPerGroup.add(exercise.variantGroupId(), exercise.variantGroupMaxPoints(),
                                calculatePointsAchievedFromExercise(exercise, gradeScore.score(), plagiarismCasesForStudent.get(exercise.id()), context.settings()))));
        return achievedPointsPerGroup.cappedPointsPerGroup();
    }

    private static Map<Long, CourseGradeScoreDTO> ratedGradeScoresPerExercise(Collection<CourseGradeScoreDTO> gradeScores) {
        return gradeScores.stream().filter(gradeScore -> Boolean.TRUE.equals(gradeScore.rated()))
                // A student can hold more than one graded participation in one exercise, for instance after moving between
                // individual and team mode. Keeping the higher score matches what the participation-based path credits,
                // and is in any case preferable to Collectors.toMap's default of throwing.
                .collect(Collectors.toMap(CourseGradeScoreDTO::exerciseId, gradeScore -> gradeScore, (first, second) -> first.score() >= second.score() ? first : second));
    }

    private static Map<Long, PlagiarismCaseScoreDTO> plagiarismCasesForStudent(StudentCourseScoreInputDTO studentInput) {
        return studentInput.plagiarismCases().stream().filter(plagiarismCase -> plagiarismCase.studentId() == studentInput.studentId())
                // Match PlagiarismMapping's legacy semantics if inconsistent data contains more than one case for the
                // same student and exercise: the later projection row replaces the earlier one.
                .collect(Collectors.toMap(PlagiarismCaseScoreDTO::exerciseId, plagiarismCase -> plagiarismCase, (previous, replacement) -> replacement));
    }

    private static boolean studentHasVerdict(Collection<PlagiarismCaseScoreDTO> plagiarismCases, PlagiarismVerdict verdict) {
        return plagiarismCases.stream().anyMatch(plagiarismCase -> verdict == plagiarismCase.verdict());
    }

    private static StudentScoresDTO buildStudentScores(CourseScoreSettingsDTO settings, MaxAndReachablePointsDTO maxAndReachablePoints, double pointsAchievedByStudentInCourse,
            double pointsAchievedByStudentInCourseUncapped, double presentationScore) {
        int accuracy = settings.accuracyOfScores();
        double absolutePoints = roundToNDecimalPlaces(pointsAchievedByStudentInCourse, accuracy);
        double absolutePointsTotal = roundToNDecimalPlaces(pointsAchievedByStudentInCourseUncapped, accuracy);
        double relativeScore = maxAndReachablePoints.maxPoints() > 0 ? roundToNDecimalPlaces(pointsAchievedByStudentInCourse / maxAndReachablePoints.maxPoints() * 100.0, accuracy)
                : 0.0;
        double currentRelativeScore = maxAndReachablePoints.reachablePoints() > 0
                ? roundToNDecimalPlaces(pointsAchievedByStudentInCourse / maxAndReachablePoints.reachablePoints() * 100.0, accuracy)
                : 0.0;

        return new StudentScoresDTO(absolutePoints, absolutePointsTotal, relativeScore, currentRelativeScore, presentationScore);
    }

    /**
     * Calculates the points a student earns from a single exercise, after the plagiarism deduction for that exercise.
     * <p>
     * Note that the result is rounded here, before the caller sums it up. This is necessary so that students arrive at
     * the same overall result when recalculating themselves: a student who achieved 1.05 points in each of 5 exercises
     * sees 1.1 points displayed per exercise and adds them up to 5.5, so the server has to round before summing too.
     *
     * @param exercise                  the exercise the points are earned in
     * @param score                     the percentage the student achieved in the exercise
     * @param plagiarismCaseForExercise the student's projected plagiarism case for this exercise, or {@code null} if there is none
     * @param settings                  the course settings the calculation rounds by
     * @return the rounded, plagiarism-adjusted points earned in the exercise
     */
    public static double calculatePointsAchievedFromExercise(ExerciseCourseScoreDTO exercise, double score, @Nullable PlagiarismCaseScoreDTO plagiarismCaseForExercise,
            CourseScoreSettingsDTO settings) {
        int accuracy = settings.accuracyOfScores();
        double pointsAchievedFromExercise = roundToNDecimalPlaces(score * SCORE_NORMALIZATION_VALUE * exercise.maxPoints(), accuracy);
        double plagiarismPointDeductionPercentage = Optional.ofNullable(plagiarismCaseForExercise).filter(plagiarismCase -> plagiarismCase.pointDeduction() > 0)
                .map(PlagiarismCaseScoreDTO::pointDeduction).orElse(0);
        return roundToNDecimalPlaces(pointsAchievedFromExercise * (100.0 - plagiarismPointDeductionPercentage) / 100.0, accuracy);
    }

    /**
     * Calculates the presentation points a student earns from the average of their presentation scores.
     *
     * @param presentationConfig          the graded presentation configuration of the course
     * @param reachablePresentationPoints the presentation points reachable in the course
     * @param presentationScoreSum        the sum of the student's presentation scores in the course
     * @param accuracyOfScores            the number of decimal places to round to
     * @return the presentation points for the student, or 0 when the course or the student has none
     */
    public static double calculatePresentationPoints(@Nullable GradedPresentationConfigDTO presentationConfig, double reachablePresentationPoints, double presentationScoreSum,
            int accuracyOfScores) {
        return Stream.ofNullable(presentationConfig).filter(config -> config.presentationsNumber() > 0).filter(config -> presentationScoreSum > 0.0)
                .mapToDouble(config -> roundToNDecimalPlaces(reachablePresentationPoints * presentationScoreSum / config.presentationsNumber() / 100.0, accuracyOfScores))
                .findFirst().orElse(0.0);
    }

    /**
     * Calculates how many of a course's points are reserved for presentations.
     * <p>
     * The presentations weight is the share of the <em>total</em> points that presentations account for, so the reachable
     * points passed in here are the remaining share and the total is scaled up from them accordingly.
     *
     * @param presentationConfig  the graded presentation configuration of the course
     * @param baseReachablePoints the points reachable in the course from exercises alone
     * @param accuracyOfScores    the number of decimal places to round to
     * @return the reachable presentation points, or 0 when the course has no graded presentations
     */
    public static double calculateReachablePresentationPoints(@Nullable GradedPresentationConfigDTO presentationConfig, double baseReachablePoints, int accuracyOfScores) {
        return Stream.ofNullable(presentationConfig).filter(config -> config.presentationsWeight() > 0.0).filter(config -> baseReachablePoints > 0.0).mapToDouble(config -> {
            double reachablePointsWithPresentation = -baseReachablePoints / (config.presentationsWeight() - 100.0) * 100.0;
            return roundToNDecimalPlaces(reachablePointsWithPresentation * config.presentationsWeight() / 100.0, accuracyOfScores);
        }).findFirst().orElse(0.0);
    }

    /**
     * Determines whether a given exercise will be included into course score calculation.
     * <p>
     * The requirement that has to be fulfilled for every exercise: it has to be included in the score.
     * The base case: an exercise that is not an automatically assessed programming exercise
     * -> include in maxPointsInCourse after the due date.
     * Edge case 1: an automatically assessed programming exercise without test runs after the due date
     * -> include in maxPointsInCourse directly after release because the student can achieve points immediately.
     * Edge case 2: an automatically assessed programming exercise with test runs after the due date
     * -> include in maxPointsInCourse after the final test run is over, not immediately after release, because
     * the test run after the due date is important for the final course score (hidden tests).
     *
     * @param exercise        the exercise whose involvement should be determined
     * @param calculationTime the instant at which the score is calculated
     * @return true if the exercise counts towards the course score
     */
    public static boolean includeIntoScoreCalculation(ExerciseCourseScoreDTO exercise, ZonedDateTime calculationTime) {
        // A milestone group's points are carried by its MilestoneExercise, which counts here in its own right; counting
        // its user stories as well would count the whole group twice. Their own includedInOverallScore stays
        // INCLUDED_COMPLETELY on purpose (see UserStoryExercise) - membership, not that flag, is what excludes them.
        return !exercise.memberOfMilestoneGroup() && hasCountablePoints(exercise, calculationTime);
    }

    /**
     * Whether the exercise's points are earned and countable at this instant - the inclusion setting plus the timing
     * rules of {@link #includeIntoScoreCalculation}, without asking whether the course counts this exercise directly or
     * through its group.
     * <p>
     * {@link #calculateAchievedPointsPerVariantGroup} needs exactly this: it reports what a student earned *within* a
     * group, so it must see the group's members even though the course score reaches them through their milestone.
     *
     * @param exercise        the exercise whose points are being considered
     * @param calculationTime the instant at which the score is calculated
     * @return true if the exercise's points are countable
     */
    private static boolean hasCountablePoints(ExerciseCourseScoreDTO exercise, ZonedDateTime calculationTime) {
        boolean isExerciseIncluded = exercise.includedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED;
        boolean isExerciseFinished = !isAssessedAutomatically(exercise) && (exercise.dueDate() == null || exercise.dueDate().isBefore(calculationTime));

        return isExerciseIncluded && (isExerciseFinished || isAutomaticAssessmentDone(exercise, calculationTime));
    }

    /**
     * Determines whether the given exercise is a variant of an exercise variant group with a configured points cap.
     * Only such exercises go through the separate, capped variant-group computation; all others are summed up
     * individually.
     *
     * @param exercise the exercise to check
     * @return true if the exercise belongs to a variant group that has a maxPoints cap
     */
    private static boolean isExerciseVariant(ExerciseCourseScoreDTO exercise) {
        return exercise.variantGroupId() != null && exercise.variantGroupMaxPoints() != null;
    }

    /**
     * Determines whether the max points of a given exercise should be included in the amount of reachable points of a course.
     * <p>
     * Base case: points are reachable if the exercise is released and the assessment is over -> it was possible for the student to get points.
     * Addition regarding edge case 1: the exercise score is reachable immediately after release since the student score only depends on the
     * immediate automatic feedback.
     * Addition regarding edge case 2: the exercise score is officially reachable after the final test run
     * (so after the buildAndTestStudentSubmissionsAfterDueDate is over).
     *
     * @param exercise the exercise whose assessment state should be determined
     */
    private static boolean isAssessmentDone(ExerciseCourseScoreDTO exercise, ZonedDateTime calculationTime) {
        boolean isNonAutomaticAssessmentDone = !isAssessedAutomatically(exercise)
                && (exercise.assessmentDueDate() == null || calculationTime.isAfter(exercise.assessmentDueDate()));
        return isNonAutomaticAssessmentDone || isAutomaticAssessmentDone(exercise, calculationTime);
    }

    private static boolean isAssessedAutomatically(ExerciseCourseScoreDTO exercise) {
        return exercise.type() == ExerciseType.PROGRAMMING && exercise.assessmentType() == AssessmentType.AUTOMATIC;
    }

    private static boolean isAutomaticAssessmentDone(ExerciseCourseScoreDTO exercise, ZonedDateTime calculationTime) {
        boolean finalBuildFinished = Optional.ofNullable(exercise.buildAndTestStudentSubmissionsAfterDueDate()).map(calculationTime::isAfter).orElse(true);
        return isAssessedAutomatically(exercise) && finalBuildFinished;
    }
}
