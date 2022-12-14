package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import de.tum.in.www1.artemis.web.rest.CourseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService.PlagiarismMapping;
import de.tum.in.www1.artemis.web.rest.dto.*;

/**
 * Service Implementation for calculating course scores.
 * Adapted from the implementation at course-score-calculation.service.ts.
 */
@Service
public class CourseScoreCalculationService {

    private final Logger log = LoggerFactory.getLogger(CourseScoreCalculationService.class);

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    // Used when calculating course scores in CourseScoreCalculationService.
    // Scores are calculated for all exercises ("total")  as well as for the subset of exercises of each exercise type.
    public static final String TOTAL = "total";

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final ParticipantScoreService participantScoreService;

    public CourseScoreCalculationService(StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
                                         PlagiarismCaseRepository plagiarismCaseRepository, ParticipantScoreService participantScoreService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.participantScoreService = participantScoreService;
    }

    private record MaxAndReachablePoints(double maxPoints, double reachablePoints) {
    }

    /**
     * Calculates max and reachable max points for the given exercises.
     * Implementation is adapted from course-score-calculation.service.ts.
     *
     * @param exercises the exercises which are included into max points calculation
     * @return the max and reachable max points for the given exercises
     */
    private MaxAndReachablePoints calculateMaxAndReachablePoints(Set<Exercise> exercises) {

        log.info("CourseScoreCalculationService - calculateMaxAndReachablePoints: exercises: {}", exercises);
        if (exercises.isEmpty()) {
            return new MaxAndReachablePoints(0, 0);
        }

        double maxPointsInCourse = 0.0; // the sum of all included exercises, whose due date is over or unset or who are automatically assessed and assessment is done
        double reachableMaxPointsInCourse = 0.0; // the sum of all included and already assessed exercises

        for (var exercise : exercises) {
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }
            var maxPointsReachableInExercise = exercise.getMaxPoints();
            if (exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY) {
                maxPointsInCourse += maxPointsReachableInExercise;
                if (isAssessmentDone(exercise)) {
                    reachableMaxPointsInCourse += maxPointsReachableInExercise;
                }
            }
        }

        log.info("CourseScoreCalculationService - calculateMaxAndReachablePoints: maxPointsInCourse, reachableMaxPointsInCourse: {}, {}", maxPointsInCourse, reachableMaxPointsInCourse);

        return new MaxAndReachablePoints(maxPointsInCourse, reachableMaxPointsInCourse);
    }

    /**
     * Prepares all entities required for calculateCourseScoresForStudent and calls it to retrieve the total scores for each specified student in the specified course.
     * If there is a single student id in studentIds, the student id will be filtered in the database as an optimization wherever possible.
     *
     * @param courseId   the id of the course to calculate the total scores for
     * @param studentIds the id of the students whose scores in the course will be calculated.
     * @return the max and reachable max points for the given course and the student scores with related plagiarism verdicts for the given student ids
     */
    public CourseScoresForExamBonusSourceDTO calculateCourseScoresForExamBonusSource(long courseId, Collection<Long> studentIds) {

        Set<Exercise> courseExercises = exerciseRepository.findByCourseIdWithCategories(courseId);
        if (courseExercises.isEmpty()) return null;

        MaxAndReachablePoints maxAndReachablePoints = calculateMaxAndReachablePoints(courseExercises);

        List<PlagiarismCase> plagiarismCases;

        MultiValueMap<Long, StudentParticipation> studentIdToParticipations = new LinkedMultiValueMap<>();

        if (studentIds.size() == 1) { // Optimize single student case by filtering in the database.
            Long studentId = studentIds.iterator().next();
            var participations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(courseId, studentId);
            if (!participations.isEmpty()) {
                studentIdToParticipations.addAll(studentId, participations);
            }
            plagiarismCases = plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, studentId);
        } else {
            var participations = studentParticipationRepository.findByCourseIdWithRelevantResult(courseId);
            // Only use those participations where one of the students handed to this method actually takes part.
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

        Course course = courseExercises.iterator().next().getCourseViaExerciseGroupOrCourseMember();

        List<StudentScoresForExamBonusSourceDTO> studentScoresForExamBonusSource = new ArrayList<>();

        for (var entry : studentIdToParticipations.entrySet()) {
            StudentScoresDTO studentScores = calculateCourseScoreForStudent(course, entry.getKey(), entry.getValue(), maxAndReachablePoints.maxPoints, maxAndReachablePoints.reachablePoints, plagiarismCases);

            boolean presentationScorePassed;
            PlagiarismVerdict mostSeverePlagiarismVerdict;
            PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);
            if (plagiarismMapping.studentHasVerdict(entry.getKey(), PlagiarismVerdict.PLAGIARISM)) {
                mostSeverePlagiarismVerdict = PlagiarismVerdict.PLAGIARISM;
                presentationScorePassed = false;
            } else {
                presentationScorePassed = isPresentationScoreSufficientForBonus(studentScores.getPresentationScore(), course.getPresentationScore());
                Map<Long, PlagiarismCase> plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(entry.getKey());
                mostSeverePlagiarismVerdict = findMostServerePlagiarismVerdict(plagiarismCasesForStudent.values());
            }
            studentScoresForExamBonusSource.add(new StudentScoresForExamBonusSourceDTO(studentScores.getAbsoluteScore(), studentScores.getRelativeScore(), studentScores.getCurrentRelativeScore(), studentScores.getPresentationScore(), entry.getKey(), presentationScorePassed, mostSeverePlagiarismVerdict));
        }

        return new CourseScoresForExamBonusSourceDTO(maxAndReachablePoints.maxPoints, maxAndReachablePoints.reachablePoints, course.getPresentationScore(), studentScoresForExamBonusSource);
    }

    /**
     * Get all the items needed for the CourseForDashboardDTO.
     * This includes scoresPerExerciseType and participationResults.
     *
     * @param course the course to calculate the items for.
     * @param userId the id of the students whose scores in the course will be calculated.
     * @return the CourseForDashboardDTO containing all the mentioned items.
     */
    public CourseForDashboardDTO getScoresAndParticipationResults(Course course, long userId) {
        log.info("CourseScoreCalculationService - getScoresAndParticipationResults: course: {}", course);
        // Get scores per exercise type for the course (used in course-statistics.component i.a.).
        Map<String, CourseScoresDTO> scoresPerExerciseType = calculateCourseScoresPerExerciseType(course, userId);

        // Get participation results (used in course-statistics.component).
        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), userId);
        List<Result> participationResults = new ArrayList<>();
        for (StudentParticipation studentParticipation : studentParticipations) {
            participationResults.add(getResultForParticipation(studentParticipation, studentParticipation.getIndividualDueDate()));
        }

        return new CourseForDashboardDTO(course, scoresPerExerciseType, participationResults);
    }

    /**
     * Prepares all entities required for calculateCourseScoresForStudent and calls it multiple times to retrieve the scores per exercise type for the specified student in the specified course.
     * In addition to the scores per exercise type, the total scores per course are calculated.
     *
     * @param course the course to calculate the scores for
     * @param userId the id of the user whose scores will be calculated
     * @return a map of the scores for the different exercise types (total, for programming exercises etc.). For each type, the map contains the max and reachable max points and the scores of the current user.
     */
    public Map<String, CourseScoresDTO> calculateCourseScoresPerExerciseType(Course course, long userId) {

        Map<String, CourseScoresDTO> scoresPerExerciseType = new HashMap<>();

        long courseId = course.getId();

        // Retrieve required entities

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(courseId, userId);

        log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: studentParticipations: {}", studentParticipations);

        Set<Exercise> courseExercises = course.getExercises();

        log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: courseExercises: {}", courseExercises);

        MaxAndReachablePoints maxAndReachablePoints = calculateMaxAndReachablePoints(courseExercises);

        log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: maxAndReachablePoints: {}", maxAndReachablePoints);

        List<PlagiarismCase> plagiarismCases = plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, userId);

        log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: plagiarismCases: {}", plagiarismCases);

        // Get scores for all exercises in course.
        StudentScoresDTO totalStudentScores = calculateCourseScoreForStudent(course, userId, studentParticipations, maxAndReachablePoints.maxPoints, maxAndReachablePoints.reachablePoints, plagiarismCases);
        CourseScoresDTO totalScores = new CourseScoresDTO(maxAndReachablePoints.maxPoints, maxAndReachablePoints.reachablePoints, totalStudentScores);
        log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: totalScores: {}", totalScores);
        scoresPerExerciseType.put(TOTAL, totalScores);

        // Get scores per exercise type.
        for (ExerciseType exerciseType : ExerciseType.values()) {
            // Filter out the entities per exercise type.
            Set<Exercise> exercisesOfExerciseType = courseExercises.stream().filter(exercise -> exercise.getExerciseType() == exerciseType).collect(Collectors.toSet());
            log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: exercisesOfExerciseType and exerciseType: {}, {}", exercisesOfExerciseType, exerciseType);
            MaxAndReachablePoints maxAndReachablePointsOfExerciseType = calculateMaxAndReachablePoints(exercisesOfExerciseType);
            log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: maxAndReachablePointsOfExerciseType and exerciseType: {}, {}", maxAndReachablePointsOfExerciseType, exerciseType);
            List<StudentParticipation> studentParticipationsOfExerciseType = studentParticipations.stream().filter(participation -> participation.getExercise().getExerciseType() == exerciseType).toList();
            log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: studentParticipationsOfExerciseType and exerciseType: {}, {}", studentParticipationsOfExerciseType, exerciseType);

            List<PlagiarismCase> plagiarismCasesOfExerciseType = plagiarismCases.stream().filter(plagiarismCase -> plagiarismCase.getExercise().getExerciseType() == exerciseType).toList();
            log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: plagiarismCasesOfExerciseType and exerciseType: {}, {}", plagiarismCasesOfExerciseType, exerciseType);

            StudentScoresDTO studentScoresOfExerciseType = calculateCourseScoreForStudent(course, userId, studentParticipationsOfExerciseType, maxAndReachablePointsOfExerciseType.maxPoints, maxAndReachablePoints.reachablePoints, plagiarismCasesOfExerciseType);
            CourseScoresDTO scoresOfExerciseType = new CourseScoresDTO(maxAndReachablePointsOfExerciseType.maxPoints, maxAndReachablePointsOfExerciseType.reachablePoints, studentScoresOfExerciseType);
            log.info("CourseScoreCalculationService - calculateCourseScoresPerExerciseType: scoresOfExerciseType and exerciseType: {}, {}", scoresOfExerciseType, exerciseType);
            scoresPerExerciseType.put(exerciseType.getExerciseTypeAsString(), scoresOfExerciseType);
        }

        return scoresPerExerciseType;
    }

    /**
     * Calculates the presentation score, relative and absolute points for the given studentId and corresponding participationsOfStudent
     * and takes the effects of related plagiarism verdicts on the grade into account.
     *
     * @param course                     the course the scores are calculated for.
     * @param studentId                  the id of the student who has participated in the course exercises.
     * @param participationsOfStudent    should be non-empty. The exercise participations of the given student.
     * @param maxPointsInCourse          max points in the given course.
     * @param reachableMaxPointsInCourse max points achievable in the given course depending on the due dates of the exercises.
     * @param plagiarismCases            the plagiarism verdicts for the student for the participated exercises.
     * @return a StudentScore instance with the presentation score, relative and absolute points achieved by the given student and the most severe plagiarism verdict.
     */
    public StudentScoresDTO calculateCourseScoreForStudent(Course course, Long studentId, List<StudentParticipation> participationsOfStudent, double maxPointsInCourse,
                                                           double reachableMaxPointsInCourse, List<PlagiarismCase> plagiarismCases) {

        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: arguments: {}, {}, {}, {}, {}, {}", course, studentId, participationsOfStudent, maxPointsInCourse, reachableMaxPointsInCourse, plagiarismCases);

        PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);

        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: plagiarismMapping: {}", plagiarismMapping);

        if (plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0);
        }

        double pointsAchievedByStudentInCourse = 0.0;
        int presentationScore = 0;
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);

        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: plagiarismCasesForStudent: {}", plagiarismCasesForStudent);

        for (StudentParticipation participation : participationsOfStudent) {
            Exercise exercise = participation.getExercise();
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }

            log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: participation and exercise: {}, {}", participation, exercise);
            // getResultForParticipation always sorts the results by completion date, maybe optimize with a flag
            // if input results are already sorted.
            var result = getResultForParticipation(participation, exercise.getDueDate());

            log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: resultForParticipation: {}", result);
            if (result != null && Boolean.TRUE.equals(result.isRated())) {
                double pointsAchievedFromExercise = calculatePointsAchievedFromExercise(exercise, result, plagiarismCasesForStudent.get(exercise.getId()));
                log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: pointsAchievedFromExercise: {}", pointsAchievedFromExercise);
                pointsAchievedByStudentInCourse += pointsAchievedFromExercise;
            }
            presentationScore += participation.getPresentationScore() != null ? participation.getPresentationScore() : 0;
        }

        double absolutePoints = roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse, course);
        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: absolutePoints: {}", absolutePoints);
        double relativeScore = maxPointsInCourse > 0 ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxPointsInCourse * 100.0, course) : 0.0;
        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: relativeScore: {}", relativeScore);
        double currentRelativeScore = reachableMaxPointsInCourse > 0
            ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / reachableMaxPointsInCourse * 100.0, course)
            : 0.0;
        log.info("CourseScoreCalculationService - calculateCourseScoreForStudent: currentRelativeScore: {}", currentRelativeScore);

        return new StudentScoresDTO(absolutePoints, relativeScore, currentRelativeScore, presentationScore);
    }

    private double calculatePointsAchievedFromExercise(Exercise exercise, Result result, @Nullable PlagiarismCase plagiarismCaseForExercise) {

        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: arguments: {}, {}, {}", exercise, result, plagiarismCaseForExercise);
        var score = result.getScore();
        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: score: {}", score);
        if (score == null) {
            score = 0.0;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: course: {}", course);
        // Note: It is important that we round on the individual exercise level first and then sum up.
        // This is necessary so that the students arrive at the same overall result when doing their own recalculations.
        // Let's assume that a student achieved 1.05 points in each of 5 exercises.
        // In the client, these are now displayed rounded as 1.1 points.
        // If the student adds up the displayed points, the student gets a total of 5.5 points.
        // In order to get the same total result as the student, we have to round before summing.
        double pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(score * SCORE_NORMALIZATION_VALUE * exercise.getMaxPoints(), course);
        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: pointsAchievedFromExercise: {}", pointsAchievedFromExercise);
        double plagiarismPointDeductionPercentage = plagiarismCaseForExercise != null ? plagiarismCaseForExercise.getVerdictPointDeduction() : 0.0;
        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: plagiarismPointDeductionPercentage: {}", plagiarismPointDeductionPercentage);
        if (plagiarismPointDeductionPercentage > 0.0) {
            pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(pointsAchievedFromExercise * (100.0 - plagiarismPointDeductionPercentage) / 100.0, course);
        }
        log.info("CourseScoreCalculationService - calculatePointsAchievedFromExercise: pointsAchievedFromExercise after deducting plagiarism penalty: {}", pointsAchievedFromExercise);
        return pointsAchievedFromExercise;
    }

    private Result getResultForParticipation(Participation participation, ZonedDateTime dueDate) {

        log.info("CourseScoreCalculationService - getResultForParticipation: participation and dueDate: {}, {}", participation, dueDate);
        if (participation == null) {
            return null;
        }
        var resultsSet = participation.getResults();

        log.info("CourseScoreCalculationService - getResultForParticipation: resultsSet: {}", resultsSet);
        Result chosenResult;

        if (resultsSet != null && !resultsSet.isEmpty()) {

            var resultsList = new ArrayList<>(resultsSet);

            log.info("CourseScoreCalculationService - getResultForParticipation: resultsList: {}", resultsList);

            var ratedResults = resultsList.stream().filter(result -> Boolean.TRUE.equals(result.isRated())).toList();

            log.info("CourseScoreCalculationService - getResultForParticipation: ratedResults: {}", ratedResults);

            if (ratedResults.size() == 1) {
                return ratedResults.get(0);
            }

            // sorting in descending order to have the last result at the beginning
            resultsList.sort(Comparator.comparing(Result::getCompletionDate).reversed());

            log.info("CourseScoreCalculationService - getResultForParticipation: resultsListSorted: {}", resultsList);

            long gracePeriodInSeconds = 10L;
            if (dueDate == null || !dueDate.plusSeconds(gracePeriodInSeconds).isBefore(resultsList.get(0).getCompletionDate())) {
                // find the first result that is before the due date
                chosenResult = resultsList.get(0);
            } else if (dueDate.plusSeconds(gracePeriodInSeconds).isBefore(resultsList.get(0).getCompletionDate())) {
                chosenResult = new Result();
                chosenResult.setScore(0.0);
            } else {
                chosenResult = resultsList.get(resultsList.size() - 1);
            }
        } else {
            chosenResult = new Result();
            chosenResult.setScore(0.0);
        }

        log.info("CourseScoreCalculationService - getResultForParticipation: chosenResult: {}", chosenResult);

        return chosenResult;
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
        boolean isNonAutomaticAssessmentDone = !isAssessedAutomatically(exercise)
            && (exercise.getAssessmentDueDate() == null || exercise.getAssessmentDueDate().isBefore(ZonedDateTime.now()));
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
    private boolean isPresentationScoreSufficientForBonus(int achievedPresentationScore, Integer coursePresentationScore) {
        return coursePresentationScore == null || achievedPresentationScore >= coursePresentationScore;
    }

    private PlagiarismVerdict findMostServerePlagiarismVerdict(Collection<PlagiarismCase> plagiarismCasesForSingleStudent) {
        if (plagiarismCasesForSingleStudent.isEmpty()) {
            return null;
        }
        var studentVerdictsFromExercises = plagiarismCasesForSingleStudent.stream().map(PlagiarismCase::getVerdict).toList();
        return PlagiarismVerdict.findMostSevereVerdict(studentVerdictsFromExercises);
    }

}
