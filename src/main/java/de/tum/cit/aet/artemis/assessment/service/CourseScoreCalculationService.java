package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePoints;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismCaseService.PlagiarismMapping;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service Implementation for calculating course scores.
 * Adapted from the implementation at course-score-calculation.service.ts.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseScoreCalculationService {

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    public CourseScoreCalculationService(StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
            PlagiarismCaseRepository plagiarismCaseRepository, PresentationPointsCalculationService presentationPointsCalculationService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
    }

    /**
     * Calculates max and reachable max points for the given exercises. Also calculates the reachable presentation points for the course, if a grading scale with course is given.
     * Max points are the sum of the points for all included (see {@link #includeIntoScoreCalculation(Exercise)}) exercises, whose due date is over or unset or who are
     * automatically assessed and the buildAndTestStudentSubmissionsAfterDueDate is in the past.
     * Reachable max points contain only those points where the exercise's assessmentDueDate is in the past. (see {@link #isAssessmentDone(Exercise)}).
     * Example: An exercise that is not automatically assessed (e.g. text exercise), that has the dueDate in the past but the assessmentDueDate set in the future is included in
     * the max points calculation,
     * but not in the reachable max points calculation.
     *
     * @param gradingScale the gradingScale for which the reachable presentation points should be calculated. Null if no reachable presentation points should be calculated.
     * @param exercises    the exercises which are included into max points calculation
     * @return the max and reachable max points for the given exercises
     */
    private MaxAndReachablePoints calculateMaxAndReachablePoints(GradingScale gradingScale, Set<Exercise> exercises) {

        if (exercises.isEmpty()) {
            return new MaxAndReachablePoints(0, 0, 0);
        }

        double maxPoints = 0.0;
        double reachableMaxPoints = 0.0;
        double reachablePresentationPoints = 0.0;

        for (var exercise : exercises) {
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }
            var maxPointsReachableInExercise = exercise.getMaxPoints();
            if (exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY && maxPointsReachableInExercise != null) {
                maxPoints += maxPointsReachableInExercise;
                if (isAssessmentDone(exercise)) {
                    reachableMaxPoints += maxPointsReachableInExercise;
                }
            }
        }

        if (gradingScale != null) {
            reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, reachableMaxPoints);
            maxPoints += reachablePresentationPoints;
            reachableMaxPoints += reachablePresentationPoints;
        }

        return new MaxAndReachablePoints(maxPoints, reachableMaxPoints, reachablePresentationPoints);
    }

    /**
     * Prepares all entities required for calculateCourseScoresForStudent and calls it to retrieve the total scores for each specified student in the specified course.
     * If there is a single student id in studentIds, the student id will be filtered in the database as an optimization wherever possible.
     *
     * @param course       the course to calculate the total scores for.
     * @param gradingScale the grading scale with the presentation configuration to use for calculating the scores.
     * @param studentIds   the id of the students whose scores in the course will be calculated.
     * @return the max and reachable max points for the given course and the student scores with related plagiarism verdicts for the given student ids.
     */
    @Nullable
    public Map<Long, BonusSourceResultDTO> calculateCourseScoresForExamBonusSource(Course course, GradingScale gradingScale, Collection<Long> studentIds) {
        if (course == null) {
            return null;
        }
        Long courseId = course.getId();

        Set<Exercise> courseExercises = exerciseRepository.findAllExercisesByCourseId(courseId);
        if (courseExercises.isEmpty()) {
            return null;
        }

        MaxAndReachablePoints maxAndReachablePoints = calculateMaxAndReachablePoints(gradingScale, courseExercises);

        List<PlagiarismCase> plagiarismCases;

        MultiValueMap<Long, StudentParticipation> studentIdToParticipations = new LinkedMultiValueMap<>();
        if (studentIds.size() == 1) {  // Optimize single student case by filtering in the database.
            Long studentId = studentIds.iterator().next();
            var participations = studentParticipationRepository.findByCourseIdAndStudentIdWithRelevantResult(courseId, studentId);
            if (!participations.isEmpty()) {
                studentIdToParticipations.addAll(studentId, participations);
            }
            plagiarismCases = plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, studentId);
        }
        else {
            // Get all participations for the course.
            var participations = studentParticipationRepository.findByCourseIdWithRelevantResult(courseId);
            // These participations also contain participations for students with ids not included in 'studentIds'.
            // Filter out those participations that belong to the students in 'studentIds'.
            // For the single student case, this is done in the db query.
            var studentIdSet = new HashSet<>(studentIds);
            for (StudentParticipation participation : participations) {
                for (User student : participation.getStudents()) {
                    if (studentIdSet.contains(student.getId())) {
                        studentIdToParticipations.add(student.getId(), participation);
                    }
                }
            }
            plagiarismCases = plagiarismCaseRepository.findByCourseId(courseId);
        }

        return studentIdToParticipations.entrySet().parallelStream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> constructBonusSourceResultDTO(course, gradingScale, entry.getKey(), entry.getValue(), maxAndReachablePoints, plagiarismCases)));
    }

    private BonusSourceResultDTO constructBonusSourceResultDTO(Course course, GradingScale gradingScale, Long studentId, List<StudentParticipation> participations,
            MaxAndReachablePoints maxAndReachablePoints, List<PlagiarismCase> plagiarismCases) {
        StudentScoresDTO studentScores = calculateCourseScoreForStudent(course, gradingScale, studentId, participations, maxAndReachablePoints, plagiarismCases);

        boolean presentationScorePassed;
        PlagiarismVerdict mostSeverePlagiarismVerdict = null;
        boolean hasParticipated;
        PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);
        if (participations.isEmpty()) {
            presentationScorePassed = false;
            hasParticipated = false;
        }
        else if (plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            presentationScorePassed = false;
            mostSeverePlagiarismVerdict = PlagiarismVerdict.PLAGIARISM;
            hasParticipated = true;
        }
        else {
            presentationScorePassed = isPresentationScoreSufficientForBonus(studentScores.presentationScore(), course.getPresentationScore());
            Map<Long, PlagiarismCase> plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);
            mostSeverePlagiarismVerdict = findMostServerePlagiarismVerdict(plagiarismCasesForStudent.values());
            hasParticipated = true;
        }

        return new BonusSourceResultDTO(presentationScorePassed ? studentScores.absoluteScore() : 0.0, mostSeverePlagiarismVerdict, studentScores.presentationScore(),
                course.getPresentationScore(), hasParticipated);
    }

    /**
     * Get all the items needed for the CourseForDashboardDTO.
     * This includes scoresPerExerciseType and participationResults.
     *
     * @param course       the course to calculate the items for.
     * @param gradingScale the grading scale with the presentation configuration to use for calculating the presentation points.
     * @param userId       the id of the students whose scores in the course will be calculated.
     * @return the CourseForDashboardDTO containing all the mentioned items.
     */
    public CourseForDashboardDTO getScoresAndParticipationResults(Course course, GradingScale gradingScale, long userId) {
        Set<StudentParticipation> gradedStudentParticipations = new HashSet<>();
        for (Exercise exercise : course.getExercises()) {
            exercise.setCourse(course);
            // This method is used in the CourseResource where the course is first fetched with lazy participations, and participations are then fetched separately in the
            // CourseService and added to the course if found.
            // If no participations are found for the course, no value is set to the course's participations and trying to access them here would throw a
            // LazyInitializationException. This is why we first need to check if the participations are initialized before adding them to the list of participations.
            // If they are not initialized, this means that there are no participations for this exercise and user.
            // TODO: Look into refactoring the fetchParticipationsWithSubmissionsAndResultsForCourses method in the CourseService to always initialize the participations (to an
            // empty list if there aren't any). This way you don't need this very unintuitive check for the initialization state.
            if (Hibernate.isInitialized(exercise.getStudentParticipations())) {
                exercise.getStudentParticipations().stream().filter(participation -> !participation.isPracticeMode()).forEach(participation -> {
                    participation.setExercise(exercise);
                    gradedStudentParticipations.add(participation);
                });
            }
        }

        Set<Exercise> courseExercises = course.getExercises();

        MaxAndReachablePoints maxAndReachablePoints = calculateMaxAndReachablePoints(gradingScale, courseExercises);

        List<PlagiarismCase> plagiarismCases = new ArrayList<>();
        for (Exercise exercise : courseExercises) {
            // TODO: Look into refactoring the fetchPlagiarismCasesForCourseExercises method in the CourseService to always initialize the participations (to an
            // empty list if there aren't any). This way you don't need this very unintuitive check for the initialization state.
            if (Hibernate.isInitialized(exercise.getPlagiarismCases())) {
                plagiarismCases.addAll(exercise.getPlagiarismCases());
            }
        }

        // Get the total scores for the course.
        StudentScoresDTO totalStudentScores = calculateCourseScoreForStudent(course, gradingScale, userId, gradedStudentParticipations, maxAndReachablePoints, plagiarismCases);
        CourseScoresDTO totalScores = new CourseScoresDTO(maxAndReachablePoints.maxPoints(), maxAndReachablePoints.reachablePoints(),
                maxAndReachablePoints.reachablePresentationPoints(), totalStudentScores);

        // Get scores per exercise type for the course (used in course-statistics.component i.a.).
        Map<ExerciseType, CourseScoresDTO> scoresPerExerciseType = calculateCourseScores(course, gradedStudentParticipations, userId, plagiarismCases);

        // Get participation results (used in course-statistics.component).
        Set<ParticipationResultDTO> participationResults = new HashSet<>();
        for (StudentParticipation studentParticipation : gradedStudentParticipations) {
            if (studentParticipation.getResults() != null && !studentParticipation.getResults().isEmpty()) {
                Result result = getResultForParticipation(studentParticipation, studentParticipation.getIndividualDueDate());
                var participationResult = new ParticipationResultDTO(result.getScore(), result.isRated(), studentParticipation.getId());
                participationResults.add(participationResult);
                // this line is an important workaround. It prevents that the whole tree
                // "result -> participation -> exercise -> course -> exercises -> studentParticipations -> submissions -> results" is sent again to the client which is useless
                // TODO: in the future, we need a better solution to prevent this
                studentParticipation.setExercise(null);
            }
        }

        return new CourseForDashboardDTO(course, totalScores, scoresPerExerciseType.get(ExerciseType.TEXT), scoresPerExerciseType.get(ExerciseType.PROGRAMMING),
                scoresPerExerciseType.get(ExerciseType.MODELING), scoresPerExerciseType.get(ExerciseType.FILE_UPLOAD), scoresPerExerciseType.get(ExerciseType.QUIZ),
                participationResults);
    }

    /**
     * Prepares all entities required for calculateCourseScoresForStudent and calls it multiple times to retrieve the scores per exercise type for the specified student in the
     * specified course.
     * In addition to the scores per exercise type, the total scores per course are calculated.
     *
     * @param course the course to calculate the scores for
     * @param userId the id of the user whose scores will be calculated
     * @return a map of the scores for the different exercise types (total, for programming exercises etc.). For each type, the map contains the max and reachable max points and
     *         the scores of the current user.
     */
    private Map<ExerciseType, CourseScoresDTO> calculateCourseScores(Course course, Collection<StudentParticipation> studentParticipations, long userId,
            Collection<PlagiarismCase> plagiarismCases) {

        Map<ExerciseType, CourseScoresDTO> scoresPerExerciseType = new HashMap<>();

        // Get scores per exercise type.
        for (ExerciseType type : ExerciseType.values()) {
            // Filter out the entities per exercise type.
            var exercisesOfExerciseType = course.getExercises().stream().filter(exercise -> exercise.getExerciseType() == type).collect(Collectors.toSet());
            var maxAndReachablePoints = calculateMaxAndReachablePoints(null, exercisesOfExerciseType);
            var studentParticipationsOfType = studentParticipations.stream().filter(participation -> participation.getExercise().getExerciseType() == type).toList();

            // Hand over all plagiarism cases (not just the ones for the current exercise type) because a student will receive a 0 score for all exercises if there is any
            // PLAGIARISM verdict.
            var studentScoresOfExerciseType = calculateCourseScoreForStudent(course, null, userId, studentParticipationsOfType, maxAndReachablePoints, plagiarismCases);
            var scoresOfExerciseType = new CourseScoresDTO(maxAndReachablePoints.maxPoints(), maxAndReachablePoints.reachablePoints(), 0.0, studentScoresOfExerciseType);
            scoresPerExerciseType.put(type, scoresOfExerciseType);
        }

        return scoresPerExerciseType;
    }

    /**
     * Calculates the presentation score, relative and absolute points for the given studentId and corresponding participationsOfStudent
     * and takes the effects of related plagiarism verdicts on the grade into account.
     *
     * @param course                  the course the scores are calculated for.
     * @param gradingScale            the grading scale of the course.
     * @param studentId               the id of the student who has participated in the course exercises.
     * @param participationsOfStudent should be non-empty. The exercise participations of the given student.
     * @param maxAndReachablePoints   max points and max reachable points in the given course.
     * @param plagiarismCases         the plagiarism verdicts for the student.
     * @return a StudentScoresDTO instance with the presentation score, relative and absolute points achieved by the given student.
     */
    public StudentScoresDTO calculateCourseScoreForStudent(Course course, GradingScale gradingScale, Long studentId, Collection<StudentParticipation> participationsOfStudent,
            MaxAndReachablePoints maxAndReachablePoints, Collection<PlagiarismCase> plagiarismCases) {

        PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);

        if (participationsOfStudent.isEmpty() || plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0);
        }

        double pointsAchievedByStudentInCourse = 0.0;
        double presentationScore = 0;
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);

        for (StudentParticipation participation : participationsOfStudent) {
            Exercise exercise = participation.getExercise();
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }
            // getResultForParticipation always sorts the results by completion date, maybe optimize with a flag
            // if input results are already sorted.
            var result = getResultForParticipation(participation, exercise.getDueDate());
            if (result != null && Boolean.TRUE.equals(result.isRated())) {
                double pointsAchievedFromExercise = calculatePointsAchievedFromExercise(exercise, result, plagiarismCasesForStudent.get(exercise.getId()));
                pointsAchievedByStudentInCourse += pointsAchievedFromExercise;
            }
        }

        // calculate presentation points for graded presentations
        if (gradingScale != null && maxAndReachablePoints.reachablePresentationPoints() > 0.0) {
            presentationScore = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, studentId,
                    maxAndReachablePoints.reachablePresentationPoints());
            pointsAchievedByStudentInCourse += presentationScore;
        }
        // calculate presentation score for basic presentations
        else if (course.getPresentationScore() != null && course.getPresentationScore() > 0.0) {
            presentationScore = participationsOfStudent.stream().filter(p -> p.getPresentationScore() != null && p.getPresentationScore() > 0.0).count();
        }

        double absolutePoints = roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse, course);
        double relativeScore = maxAndReachablePoints.maxPoints() > 0
                ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxAndReachablePoints.maxPoints() * 100.0, course)
                : 0.0;
        double currentRelativeScore = maxAndReachablePoints.reachablePoints() > 0
                ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxAndReachablePoints.reachablePoints() * 100.0, course)
                : 0.0;

        return new StudentScoresDTO(absolutePoints, relativeScore, currentRelativeScore, presentationScore);
    }

    private double calculatePointsAchievedFromExercise(Exercise exercise, Result result, @Nullable PlagiarismCase plagiarismCaseForExercise) {
        var score = result.getScore();
        if (score == null) {
            score = 0.0;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        // Note: It is important that we round on the individual exercise level first and then sum up.
        // This is necessary so that the students arrive at the same overall result when doing their own recalculations.
        // Let's assume that a student achieved 1.05 points in each of 5 exercises.
        // In the client, these are now displayed rounded as 1.1 points.
        // If the student adds up the displayed points, the student gets a total of 5.5 points.
        // In order to get the same total result as the student, we have to round before summing.
        double pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(score * SCORE_NORMALIZATION_VALUE * exercise.getMaxPoints(), course);
        double plagiarismPointDeductionPercentage = plagiarismCaseForExercise != null ? plagiarismCaseForExercise.getVerdictPointDeduction() : 0.0;
        if (plagiarismPointDeductionPercentage > 0.0) {
            pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(pointsAchievedFromExercise * (100.0 - plagiarismPointDeductionPercentage) / 100.0, course);
        }
        return pointsAchievedFromExercise;
    }

    /**
     * Returns the result of the participation that should be used for the score calculation.
     *
     * @param participation the participation for which the result should be returned.
     * @param dueDate       the due date of the exercise.
     * @return the result that should be used for the score calculation.
     */
    // TODO: This connection between participations and results will not be used in the future. This should be refactored to take the latest rated result from the submissions.
    public Result getResultForParticipation(Participation participation, ZonedDateTime dueDate) {
        if (participation == null) {
            return null;
        }
        var resultsSet = participation.getResults();

        Result emptyResult = new Result();
        // TODO: Check if you can just instantiate Result.score with 0.0.
        emptyResult.setScore(0.0);

        if (resultsSet == null || resultsSet.isEmpty()) {
            return emptyResult;
        }

        var resultsList = new ArrayList<>(resultsSet);

        List<Result> ratedResultsWithCompletionDate = resultsList.stream().filter(result -> Boolean.TRUE.equals(result.isRated() && result.getCompletionDate() != null)).toList();

        if (ratedResultsWithCompletionDate.isEmpty()) {
            return emptyResult;
        }

        if (ratedResultsWithCompletionDate.size() == 1) {
            return ratedResultsWithCompletionDate.getFirst();
        }

        // Sort the list in descending order to have the latest result at the beginning.
        resultsList.sort(Comparator.comparing(Result::getCompletionDate).reversed());

        if (dueDate == null) {
            // If the due date is null, you can always submit something, and it will always ge graded. Just take the latest graded result.
            return resultsList.getFirst();
        }

        // The due date is set and we need to find the latest result that was completed before the due date.
        Optional<Result> latestResultBeforeDueDate = resultsList.stream().filter(result -> result.getCompletionDate().isBefore(dueDate)).findFirst();

        return latestResultBeforeDueDate.orElse(emptyResult);
    }

    /**
     * Determines whether a given exercise will be included into course score calculation
     * <p>
     * The requirement that has to be fulfilled for every exercise: It has to be included in score.
     * The base case: An exercise that is not an automatically assessed programming exercise
     * -> include in maxPointsInCourse after due date.
     * Edge case 1: An automatically assessed programming exercise without test runs after the due date
     * -> include in maxPointsInCourse directly after release because the student can achieve points immediately.
     * Edge case 2: An automatically assessed programming exercise with test runs after the due date
     * -> include in maxPointsInCourse after the final test run is over, not immediately after release because
     * the test run after due date is important for the final course score (hidden tests).
     *
     * @param exercise the exercise whose involvement should be determined
     */
    private boolean includeIntoScoreCalculation(Exercise exercise) {
        boolean isExerciseIncluded = exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED;
        boolean isExerciseFinished = !isAssessedAutomatically(exercise) && (exercise.getDueDate() == null || exercise.getDueDate().isBefore(ZonedDateTime.now()));

        return isExerciseIncluded && (isExerciseFinished || isAutomaticAssessmentDone(exercise));
    }

    /**
     * Determines whether the max points of a given exercise should be included in the amount of reachable points of a course.
     * <p>
     * Base case: points are reachable if the exercise is released and the assessment is over -> It was possible for the student to get points.
     * Addition regarding edge case 1: the exercise score is reachable immediately after release since the student score only depends on the
     * immediate automatic feedback.
     * Addition regarding edge case 2: the exercise score is officially reachable after the final test run
     * (so after the buildAndTestStudentSubmissionsAfterDueDate is over).
     *
     * @param exercise the exercise whose assessment state should be determined
     */
    private boolean isAssessmentDone(Exercise exercise) {
        boolean isNonAutomaticAssessmentDone = !isAssessedAutomatically(exercise) && ExerciseDateService.isAfterAssessmentDueDate(exercise);
        return isNonAutomaticAssessmentDone || isAutomaticAssessmentDone(exercise);
    }

    private boolean isAssessedAutomatically(Exercise exercise) {
        return exercise.getExerciseType() == ExerciseType.PROGRAMMING && exercise.getAssessmentType() == AssessmentType.AUTOMATIC;
    }

    private boolean isAutomaticAssessmentDone(Exercise exercise) {
        return isAssessedAutomatically(exercise) && (((ProgrammingExercise) exercise).getBuildAndTestStudentSubmissionsAfterDueDate() == null
                || ZonedDateTime.now().isAfter(((ProgrammingExercise) exercise).getBuildAndTestStudentSubmissionsAfterDueDate()));
    }

    /**
     * Checks the presentation score requirement to get the bonus from this course applied to the final exam.
     *
     * @param achievedPresentationScore presentation score achieved by the student
     * @param coursePresentationScore   presentation score limit of the course that needs to be passed to get the bonus for the final exam
     * @return True if presentation score limit is not set or surpassed, otherwise false.
     */
    public boolean isPresentationScoreSufficientForBonus(double achievedPresentationScore, Integer coursePresentationScore) {
        return coursePresentationScore == null || achievedPresentationScore >= coursePresentationScore;
    }

    /**
     * Finds the most severe plagiarism verdict for a single student.
     *
     * @param plagiarismCasesForSingleStudent the plagiarism cases for a single student.
     * @return the most severe plagiarism verdict for a single student.
     */
    public PlagiarismVerdict findMostServerePlagiarismVerdict(Collection<PlagiarismCase> plagiarismCasesForSingleStudent) {
        if (plagiarismCasesForSingleStudent.isEmpty()) {
            return null;
        }
        var studentVerdictsFromExercises = plagiarismCasesForSingleStudent.stream().map(PlagiarismCase::getVerdict).toList();
        return PlagiarismVerdict.findMostSevereVerdict(studentVerdictsFromExercises);
    }

    /**
     * Calculates the reachable points for a course given its grading scale and exercises.
     *
     * @param gradingScale the grading scale of the course.
     * @param exercises    the exercises of the course.
     * @return the reachable points for a course excluding bonus and optional points.
     */
    public double calculateReachablePoints(GradingScale gradingScale, Set<Exercise> exercises) {
        return calculateMaxAndReachablePoints(gradingScale, exercises).reachablePoints();
    }
}
