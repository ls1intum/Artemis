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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePointsDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationStatusRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismMapping;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * Service Implementation for calculating course scores.
 * Adapted from the implementation at course-score-calculation.service.ts.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseScoreCalculationService {

    private static final double SCORE_NORMALIZATION_VALUE = 0.01;

    private static final Logger log = LoggerFactory.getLogger(CourseScoreCalculationService.class);

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    private final UserCourseNotificationStatusRepository userCourseNotificationStatusRepository;

    public CourseScoreCalculationService(StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
            Optional<PlagiarismCaseApi> plagiarismCaseApi, PresentationPointsCalculationService presentationPointsCalculationService,
            UserCourseNotificationStatusRepository userCourseNotificationStatusRepository, Optional<IrisSettingsApi> irisSettingsApi) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.plagiarismCaseApi = plagiarismCaseApi;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
        this.userCourseNotificationStatusRepository = userCourseNotificationStatusRepository;
        this.irisSettingsApi = irisSettingsApi;
    }

    /**
     * Calculates max and reachable max points for the given exercises. Also calculates the reachable presentation points for the course, if a grading scale with course is given.
     * Max points are the sum of the points for all included (see {@link #includeIntoScoreCalculation(ExerciseCourseScoreDTO)}) exercises, whose due date is over or unset or who
     * are automatically assessed and the buildAndTestStudentSubmissionsAfterDueDate is in the past.
     * Reachable max points contain only those points where the exercise's assessmentDueDate is in the past. (see {@link #isAssessmentDone(ExerciseCourseScoreDTO)}).
     * Example: An exercise that is not automatically assessed (e.g. text exercise), that has the dueDate in the past but the assessmentDueDate set in the future is included in
     * the max points calculation,
     * but not in the reachable max points calculation.
     *
     * @param gradingScale the gradingScale for which the reachable presentation points should be calculated. Null if no reachable presentation points should be calculated.
     * @param exercises    the exercises which are included into max points calculation
     * @return the max and reachable max points for the given exercises
     */
    private MaxAndReachablePointsDTO calculateMaxAndReachablePoints(@Nullable GradingScale gradingScale, Set<ExerciseCourseScoreDTO> exercises) {

        if (exercises.isEmpty()) {
            return new MaxAndReachablePointsDTO(0, 0, 0);
        }

        double maxPoints = 0.0;
        double reachableMaxPoints = 0.0;
        double reachablePresentationPoints = 0.0;

        for (var exercise : exercises) {
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }
            var maxPointsReachableInExercise = exercise.maxPoints();
            if (exercise.includedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY) {
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

        return new MaxAndReachablePointsDTO(maxPoints, reachableMaxPoints, reachablePresentationPoints);
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
        long courseId = course.getId();
        Set<ExerciseCourseScoreDTO> courseExercises = exerciseRepository.findCourseExerciseScoreInformationByCourseId(courseId);
        if (courseExercises.isEmpty()) {
            return null;
        }

        MaxAndReachablePointsDTO maxAndReachablePoints = calculateMaxAndReachablePoints(gradingScale, courseExercises);

        List<PlagiarismCase> plagiarismCases;

        MultiValueMap<Long, CourseGradeScoreDTO> studentIdToGradeScores = new LinkedMultiValueMap<>();
        if (studentIds.size() == 1) {  // Optimize single student case by filtering in the database.
            long studentId = studentIds.iterator().next();
            List<CourseGradeScoreDTO> gradeScores = studentParticipationRepository.findGradeScoresForAllExercisesForCourseAndStudent(courseId, studentId);
            if (!gradeScores.isEmpty()) {
                studentIdToGradeScores.addAll(studentId, gradeScores);
            }
            plagiarismCases = plagiarismCaseApi.map(api -> api.findByCourseIdAndStudentId(courseId, studentId)).orElse(List.of());
        }
        else {
            var courseGradeScoreDtos = studentParticipationRepository.findGradeScoresForAllExercisesForCourse(courseId);
            // These course grade score DTOs also contain DTOs for students with ids not included in 'studentIds'.
            // Filter out those DTOs that belong to the students in 'studentIds'.
            // For the single student case, this is done in the db query.
            var studentIdSet = new HashSet<>(studentIds);
            for (CourseGradeScoreDTO courseGradeScoreDTO : courseGradeScoreDtos) {
                Long studentId = courseGradeScoreDTO.userId();
                if (studentIdSet.contains(studentId)) {
                    studentIdToGradeScores.add(studentId, courseGradeScoreDTO);
                }
            }
            plagiarismCases = plagiarismCaseApi.map(api -> api.findByCourseId(courseId)).orElse(List.of());
        }

        return studentIdToGradeScores.entrySet().parallelStream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> constructBonusSourceResultDTO(course, gradingScale, entry.getKey(), entry.getValue(), maxAndReachablePoints, plagiarismCases, courseExercises)));
    }

    private BonusSourceResultDTO constructBonusSourceResultDTO(Course course, GradingScale gradingScale, Long studentId, List<CourseGradeScoreDTO> participations,
            MaxAndReachablePointsDTO maxAndReachablePoints, List<PlagiarismCase> plagiarismCases, Set<ExerciseCourseScoreDTO> courseExercises) {
        StudentScoresDTO studentScores = calculateCourseScoreForStudent(course, gradingScale, studentId, participations, maxAndReachablePoints, plagiarismCases, courseExercises);

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
     * @param course                            the course to calculate the items for.
     * @param gradingScale                      the grading scale with the presentation configuration to use for calculating the presentation points.
     * @param userId                            the id of the students whose scores in the course will be calculated.
     * @param includeIrisCourseDashboardEnabled whether the enabled state of the course chat should be included in the CourseForDashboardDTO
     * @return the CourseForDashboardDTO containing all the mentioned items.
     */
    public CourseForDashboardDTO getScoresAndParticipationResults(Course course, @Nullable GradingScale gradingScale, long userId, boolean includeIrisCourseDashboardEnabled) {
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
        Set<ExerciseCourseScoreDTO> exerciseCourseScores = courseExercises.stream().map(ExerciseCourseScoreDTO::from).collect(Collectors.toSet());

        MaxAndReachablePointsDTO maxAndReachablePoints = calculateMaxAndReachablePoints(gradingScale, exerciseCourseScores);

        List<PlagiarismCase> plagiarismCases = new ArrayList<>();
        for (Exercise exercise : courseExercises) {
            // TODO: Look into refactoring the fetchPlagiarismCasesForCourseExercises method in the CourseService to always initialize the participations (to an
            // empty list if there aren't any). This way you don't need this very unintuitive check for the initialization state.
            if (Hibernate.isInitialized(exercise.getPlagiarismCases())) {
                plagiarismCases.addAll(exercise.getPlagiarismCases());
            }
        }

        // Get the total scores for the course.
        StudentScoresDTO totalStudentScores = calculateCourseScoreForStudentParticipations(course, gradingScale, userId, gradedStudentParticipations, maxAndReachablePoints,
                plagiarismCases);
        CourseScoresDTO totalScores = new CourseScoresDTO(maxAndReachablePoints.maxPoints(), maxAndReachablePoints.reachablePoints(),
                maxAndReachablePoints.reachablePresentationPoints(), totalStudentScores);

        // Get scores per exercise type for the course (used in course-statistics.component i.a.).
        Map<ExerciseType, CourseScoresDTO> scoresPerExerciseType = calculateCourseScoresForStudentParticipations(course, gradedStudentParticipations, userId, plagiarismCases);

        // Get participation results (used in course-statistics.component).
        Set<ParticipationResultDTO> participationResults = new HashSet<>();
        for (StudentParticipation studentParticipation : gradedStudentParticipations) {
            Result result = getResultForParticipation(studentParticipation, studentParticipation.getIndividualDueDate());
            var participationResult = new ParticipationResultDTO(result.getScore(), result.isRated(), studentParticipation.getId());
            participationResults.add(participationResult);
            // this line is an important workaround. It prevents that the whole tree
            // "result -> participation -> exercise -> course -> exercises -> studentParticipations -> submissions -> results" is sent again to the client which is useless
            // TODO: in the future, we need a better solution to prevent this
            studentParticipation.setExercise(null);
        }

        return new CourseForDashboardDTO(course, totalScores, scoresPerExerciseType.get(ExerciseType.TEXT), scoresPerExerciseType.get(ExerciseType.PROGRAMMING),
                scoresPerExerciseType.get(ExerciseType.MODELING), scoresPerExerciseType.get(ExerciseType.FILE_UPLOAD), scoresPerExerciseType.get(ExerciseType.QUIZ),
                participationResults, userCourseNotificationStatusRepository.countUnseenCourseNotificationsForUserInCourse(userId, course.getId()),
                includeIrisCourseDashboardEnabled ? irisSettingsApi.map(api -> api.isIrisEnabledForCourse(course.getId())).orElse(false) : null);
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
    private Map<ExerciseType, CourseScoresDTO> calculateCourseScoresForStudentParticipations(Course course, Collection<StudentParticipation> studentParticipations, long userId,
            Collection<PlagiarismCase> plagiarismCases) {

        Map<ExerciseType, CourseScoresDTO> scoresPerExerciseType = new HashMap<>();

        // Get scores per exercise type.
        for (ExerciseType type : ExerciseType.values()) {
            // Filter out the entities per exercise type.
            var exercisesOfExerciseType = course.getExercises().stream().filter(exercise -> exercise.getExerciseType() == type).collect(Collectors.toSet());
            var exerciseCourseScores = exercisesOfExerciseType.stream().map(ExerciseCourseScoreDTO::from).collect(Collectors.toSet());
            var maxAndReachablePoints = calculateMaxAndReachablePoints(null, exerciseCourseScores);
            var studentParticipationsOfType = studentParticipations.stream().filter(participation -> participation.getExercise().getExerciseType() == type).toList();

            // Hand over all plagiarism cases (not just the ones for the current exercise type) because a student will receive a 0 score for all exercises if there is any
            // PLAGIARISM verdict.
            var studentScoresOfExerciseType = calculateCourseScoreForStudentParticipations(course, null, userId, studentParticipationsOfType, maxAndReachablePoints,
                    plagiarismCases);
            var scoresOfExerciseType = new CourseScoresDTO(maxAndReachablePoints.maxPoints(), maxAndReachablePoints.reachablePoints(), 0.0, studentScoresOfExerciseType);
            scoresPerExerciseType.put(type, scoresOfExerciseType);
        }

        return scoresPerExerciseType;
    }

    /**
     * Calculates the presentation score, relative and absolute points for the given studentId and corresponding courseGradeScoreDTOsOfStudent
     * and takes the effects of related plagiarism verdicts on the grade into account.
     *
     * @param course                        the course the scores are calculated for.
     * @param gradingScale                  the grading scale of the course.
     * @param studentId                     the id of the student who has participated in the course exercises.
     * @param courseGradeScoreDTOsOfStudent should be non-empty. The exercise participations of the given student.
     * @param maxAndReachablePoints         max points and max reachable points in the given course.
     * @param plagiarismCases               the plagiarism verdicts for the student.
     * @param courseExercises               the exercises of the course with their properties relevant for the score calculation.
     * @return a StudentScoresDTO instance with the presentation score, relative and absolute points achieved by the given student.
     */
    public StudentScoresDTO calculateCourseScoreForStudent(Course course, @Nullable GradingScale gradingScale, Long studentId,
            Collection<CourseGradeScoreDTO> courseGradeScoreDTOsOfStudent, MaxAndReachablePointsDTO maxAndReachablePoints, Collection<PlagiarismCase> plagiarismCases,
            Set<ExerciseCourseScoreDTO> courseExercises) {

        PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);

        if (plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0);
        }

        Map<Long, CourseGradeScoreDTO> gradeScoreDTOMap = courseGradeScoreDTOsOfStudent.stream()
                .collect(Collectors.toMap(CourseGradeScoreDTO::exerciseId, courseGradeScoreDTO -> courseGradeScoreDTO));

        double pointsAchievedByStudentInCourse = 0.0;
        double presentationScore = 0;
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);

        for (ExerciseCourseScoreDTO exercise : courseExercises) {
            if (!includeIntoScoreCalculation(exercise)) {
                continue;
            }

            CourseGradeScoreDTO courseGradeScoreDTO = gradeScoreDTOMap.get(exercise.id());
            if (courseGradeScoreDTO != null) {
                double pointsAchievedFromExercise = calculatePointsAchievedFromExerciseScoreDTO(exercise, courseGradeScoreDTO.score(), plagiarismCasesForStudent.get(exercise.id()),
                        course);
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
            presentationScore = courseGradeScoreDTOsOfStudent.stream().filter(p -> p.presentationScore() != null && p.presentationScore() > 0.0).count();
        }

        return getStudentScoresDTO(course, maxAndReachablePoints, pointsAchievedByStudentInCourse, presentationScore);
    }

    private StudentScoresDTO getStudentScoresDTO(Course course, MaxAndReachablePointsDTO maxAndReachablePoints, double pointsAchievedByStudentInCourse, double presentationScore) {
        double absolutePoints = roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse, course);
        double relativeScore = maxAndReachablePoints.maxPoints() > 0
                ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxAndReachablePoints.maxPoints() * 100.0, course)
                : 0.0;
        double currentRelativeScore = maxAndReachablePoints.reachablePoints() > 0
                ? roundScoreSpecifiedByCourseSettings(pointsAchievedByStudentInCourse / maxAndReachablePoints.reachablePoints() * 100.0, course)
                : 0.0;

        return new StudentScoresDTO(absolutePoints, relativeScore, currentRelativeScore, presentationScore);
    }

    /**
     * Calculates course scores for a given set of student participations for a particular student.
     *
     * @param course                  the course the scores are calculated for.
     * @param gradingScale            the grading scale of the course.
     * @param studentId               the id of the student who has participated in the course exercises.
     * @param participationsOfStudent the exercise participations of the given student.
     * @param maxAndReachablePoints   max points and max reachable points in the given course.
     * @param plagiarismCases         the plagiarism verdicts for the student.
     * @return a StudentScoresDTO instance with the presentation score, relative and absolute points achieved by the given student.
     */
    public StudentScoresDTO calculateCourseScoreForStudentParticipations(Course course, @Nullable GradingScale gradingScale, Long studentId,
            Collection<StudentParticipation> participationsOfStudent, MaxAndReachablePointsDTO maxAndReachablePoints, Collection<PlagiarismCase> plagiarismCases) {

        PlagiarismMapping plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCases);

        if (participationsOfStudent.isEmpty() || plagiarismMapping.studentHasVerdict(studentId, PlagiarismVerdict.PLAGIARISM)) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0);
        }

        double pointsAchievedByStudentInCourse = 0.0;
        double presentationScore = 0;
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(studentId);

        for (StudentParticipation participation : participationsOfStudent) {
            Exercise exercise = participation.getExercise();
            if (!includeIntoScoreCalculation(ExerciseCourseScoreDTO.from(exercise))) {
                continue;
            }
            // getResultForParticipation always sorts the results by completion date, maybe optimize with a flag
            // if input results are already sorted.
            var result = getResultForParticipation(participation, exercise.getDueDate());
            if (result != null && result.isRated()) {
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

        return getStudentScoresDTO(course, maxAndReachablePoints, pointsAchievedByStudentInCourse, presentationScore);
    }

    private double calculatePointsAchievedFromExercise(Exercise exercise, Result result, @Nullable PlagiarismCase plagiarismCaseForExercise) {
        var score = result.getScore();
        if (score == null) {
            score = 0.0;
        }
        return calculatePointsAchievedFromExerciseScore(exercise, score, plagiarismCaseForExercise);
    }

    private double calculatePointsAchievedFromExerciseScore(Exercise exercise, double score, @Nullable PlagiarismCase plagiarismCaseForExercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return calculatePointsAchievedFromExerciseScoreDTO(ExerciseCourseScoreDTO.from(exercise), score, plagiarismCaseForExercise, course);
    }

    private double calculatePointsAchievedFromExerciseScoreDTO(ExerciseCourseScoreDTO exercise, double score, @Nullable PlagiarismCase plagiarismCaseForExercise, Course course) {
        // Note: It is important that we round on the individual exercise level first and then sum up.
        // This is necessary so that the students arrive at the same overall result when doing their own recalculations.
        // Let's assume that a student achieved 1.05 points in each of 5 exercises.
        // In the client, these are now displayed rounded as 1.1 points.
        // If the student adds up the displayed points, the student gets a total of 5.5 points.
        // In order to get the same total result as the student, we have to round before summing.
        double pointsAchievedFromExercise = roundScoreSpecifiedByCourseSettings(score * SCORE_NORMALIZATION_VALUE * exercise.maxPoints(), course);
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
    public Result getResultForParticipation(Participation participation, ZonedDateTime dueDate) {
        if (participation == null) {
            return null;
        }
        Set<Result> resultsSet = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull))
                .collect(Collectors.toSet());

        Result emptyResult = new Result();
        // TODO: Check if you can just instantiate Result.score with 0.0.
        emptyResult.setScore(0.0);

        if (resultsSet.isEmpty()) {
            return emptyResult;
        }

        var resultsList = new ArrayList<>(resultsSet);

        List<Result> ratedResultsWithCompletionDate = resultsList.stream().filter(result -> result.isRated() && result.getCompletionDate() != null).toList();

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
    private boolean includeIntoScoreCalculation(ExerciseCourseScoreDTO exercise) {
        boolean isExerciseIncluded = exercise.includedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED;
        boolean isExerciseFinished = !isAssessedAutomatically(exercise) && (exercise.dueDate() == null || exercise.dueDate().isBefore(ZonedDateTime.now()));

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
    private boolean isAssessmentDone(ExerciseCourseScoreDTO exercise) {
        boolean isNonAutomaticAssessmentDone = !isAssessedAutomatically(exercise) && ExerciseDateService.isAfterAssessmentDueDate(exercise);
        return isNonAutomaticAssessmentDone || isAutomaticAssessmentDone(exercise);
    }

    private boolean isAssessedAutomatically(ExerciseCourseScoreDTO exercise) {
        return exercise.exerciseType() == ExerciseType.PROGRAMMING && exercise.assessmentType() == AssessmentType.AUTOMATIC;
    }

    private boolean isAutomaticAssessmentDone(ExerciseCourseScoreDTO exercise) {
        return isAssessedAutomatically(exercise)
                && (exercise.buildAndTestStudentSubmissionsAfterDueDate() == null || ZonedDateTime.now().isAfter(exercise.buildAndTestStudentSubmissionsAfterDueDate()));
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
    public double calculateReachablePoints(GradingScale gradingScale, Set<ExerciseCourseScoreDTO> exercises) {
        return calculateMaxAndReachablePoints(gradingScale, exercises).reachablePoints();
    }
}
