package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.*;

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

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    public CourseScoreCalculationService(StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
            PlagiarismCaseRepository plagiarismCaseRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
    }

    public CourseScoresDTO calculateCourseScores(long courseId, Collection<Long> studentIds) {
        Set<Exercise> courseExercises = exerciseRepository.findAllExercisesByCourseId(courseId);
        if (courseExercises.isEmpty()) {
            return null;
        }

        double maxPointsInCourse = 0.0; // the sum of all included exercises, whose due date is over or unset or who are automatically assessed and assessment is done
        double reachableMaxPointsInCourse = 0.0; // the sum of all included and already assessed exercises
        for (var exercise : courseExercises) {
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
        List<PlagiarismCase> plagiarismCases;

        MultiValueMap<Long, StudentParticipation> studentIdToParticipations = new LinkedMultiValueMap<>();
        if (studentIds.size() == 1) {  // Optimize single student case by filtering in the database.
            Long studentId = studentIds.iterator().next();
            var participations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(courseId, studentId);
            studentIdToParticipations.addAll(studentId, participations);
            plagiarismCases = plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, studentId);
        }
        else {
            var participations = studentParticipationRepository.findByCourseIdWithRelevantResult(courseId);
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
        var plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);
        double finaLMaxPointsInCourse = maxPointsInCourse; // needed to make the variable effectively final for lambda below
        double finalReachableMaxPointsInCourse = reachableMaxPointsInCourse; // needed to make the variable effectively final for lambda below
        var studentScores = studentIdToParticipations.entrySet().stream()
                .map(entry -> calculateCourseScoreForStudent(entry.getKey(), entry.getValue(), finaLMaxPointsInCourse, finalReachableMaxPointsInCourse, plagiarismMapping))
                .toList();

        return new CourseScoresDTO(maxPointsInCourse, reachableMaxPointsInCourse, studentScores);
    }

    /**
     * TODO: Ata
     * @param studentId
     * @param participationsOfStudent should be non-empty
     * @param maxPointsInCourse
     * @param reachableMaxPointsInCourse
     * @param plagiarismMapping
     * @return
     */
    public CourseScoresDTO.StudentScore calculateCourseScoreForStudent(Long studentId, List<StudentParticipation> participationsOfStudent, double maxPointsInCourse,
            double reachableMaxPointsInCourse, PlagiarismMapping plagiarismMapping) {

        if (plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            return new CourseScoresDTO.StudentScore(studentId, 0.0, 0.0, 0.0, 0, PlagiarismVerdict.PLAGIARISM);
        }

        double pointsAchievedByStudentInCourse = 0.0;
        int presentationScore = 0;
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);

        // for (var participation : participations.stream().parallel()
        // .filter(participation -> includeIntoScoreCalculation(participation.getExercise()))
        // .mapMulti((participation, consumer) -> participation.getStudents()
        // .forEach(student -> consumer.accept(new ImmutablePair<>(student.getId(), participation))))
        // .collect(Collectors.groupingByConcurrent(ImmutablePair::left))){

        for (StudentParticipation participation : participationsOfStudent) {
            // getResultForParticipation always sorts the results by completion date, maybe optimize with a flag
            // if input results are already sorted.
            Exercise exercise = participation.getExercise();
            var result = getResultForParticipation(participation, exercise.getDueDate());
            if (result != null && Boolean.TRUE.equals(result.isRated())) {
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
                PlagiarismCase plagiarismCase = plagiarismCasesForStudent.get(exercise.getId());
                double plagiarismPointDeductionPercentage = plagiarismCase != null ? plagiarismCase.getVerdictPointDeduction() : 0.0;
                if (plagiarismPointDeductionPercentage > 0.0) {
                    pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(pointsAchievedFromExercise * (100.0 - plagiarismPointDeductionPercentage) / 100.0, course);
                }
                pointsAchievedByStudentInCourse += pointsAchievedFromExercise;
            }
            presentationScore += participation.getPresentationScore() != null ? participation.getPresentationScore() : 0;
        }

        Course course = participationsOfStudent.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();

        double absolutePoints = roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse, course);
        double relativeScore = maxPointsInCourse > 0 ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxPointsInCourse * 100.0, course) : 0.0;
        double currentRelativeScore = reachableMaxPointsInCourse > 0
                ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / reachableMaxPointsInCourse * 100.0, course)
                : 0.0;

        PlagiarismVerdict mostSevereVerdict = null;
        if (!plagiarismCasesForStudent.isEmpty()) {
            var studentVerdictsFromExercises = plagiarismCasesForStudent.values().stream().map(PlagiarismCase::getVerdict).toList();
            mostSevereVerdict = PlagiarismVerdict.findMostSevereVerdict(studentVerdictsFromExercises);
        }
        return new CourseScoresDTO.StudentScore(studentId, absolutePoints, relativeScore, currentRelativeScore, presentationScore, mostSevereVerdict);
    }

    Result getResultForParticipation(Participation participation, ZonedDateTime dueDate) {
        if (participation == null) {
            return null;
        }
        var results = participation.getResults();
        Result chosenResult;

        if (results != null) {
            var resultsArray = new ArrayList<>(results);

            if (resultsArray.size() <= 0) {
                chosenResult = new Result();
                chosenResult.setScore(0.0);
                return chosenResult;
            }

            var ratedResults = resultsArray.stream().filter(result -> Boolean.TRUE.equals(result.isRated())).toList();

            if (ratedResults.size() == 1) {
                return ratedResults.get(0);
            }

            // sorting in descending order to have the last result at the beginning
            resultsArray.sort(Comparator.comparing(Result::getCompletionDate).reversed());

            long gracePeriodInSeconds = 10L;
            if (dueDate == null || !dueDate.plusSeconds(gracePeriodInSeconds).isBefore(resultsArray.get(0).getCompletionDate())) {
                // find the first result that is before the due date
                chosenResult = resultsArray.get(0);
            }
            else if (dueDate.plusSeconds(gracePeriodInSeconds).isBefore(resultsArray.get(0).getCompletionDate())) {
                chosenResult = new Result();
                chosenResult.setScore(0.0);
            }
            else {
                chosenResult = resultsArray.get(resultsArray.size() - 1);
            }
        }
        else {
            chosenResult = new Result();
            chosenResult.setScore(0.0);
        }

        return chosenResult;
    }

    /**
     * Determines whether a given exercise will be included into course score calculation
     *
     * The requirement that has to be fulfilled for every exercise: It has to be included in score.
     * The base case: An exercise that is not an automatically assessed programming exercise
     *                  -> include in maxPointsInCourse after due date.
     * Edge case 1: An automatically assessed programming exercise without test runs after the due date
     *                  -> include in maxPointsInCourse directly after release because the student can achieve points immediately.
     * Edge case 2: An automatically assessed programming exercise with test runs after the due date
     *                  -> include in maxPointsInCourse after the final test run is over, not immediately after release because
     *                      the test run after due date is important for the final course score (hidden tests).
     * @param exercise the exercise whose involvement should be determined
     */
    private boolean includeIntoScoreCalculation(Exercise exercise) {
        boolean isExerciseIncluded = exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED;
        boolean isExerciseFinished = !isAssessedAutomatically(exercise) && (exercise.getDueDate() == null || exercise.getDueDate().isBefore(ZonedDateTime.now()));

        return isExerciseIncluded && (isExerciseFinished || isAutomaticAssessmentDone(exercise));
    }

    /**
     * Determines whether the max points of a given exercise should be included in the amount of reachable points of a course.
     *
     * Base case: points are reachable if the exercise is released and the assessment is over -> It was possible for the student to get points.
     Addition regarding edge case 1: the exercise score is reachable immediately after release since the student score only depends on the
     immediate automatic feedback.
     Addition regarding edge case 2: the exercise score is officially reachable after the final test run
     (so after the buildAndTestStudentSubmissionsAfterDueDate is over).
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

}
