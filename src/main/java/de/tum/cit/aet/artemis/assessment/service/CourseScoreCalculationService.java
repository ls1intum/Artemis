package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreContextDTO;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreSettingsDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradedPresentationConfigDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePointsDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentCourseScoreInputDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationStatusRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;
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

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    private final UserCourseNotificationStatusRepository userCourseNotificationStatusRepository;

    public CourseScoreCalculationService(StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
            Optional<PlagiarismCaseApi> plagiarismCaseApi, UserCourseNotificationStatusRepository userCourseNotificationStatusRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.plagiarismCaseApi = plagiarismCaseApi;
        this.userCourseNotificationStatusRepository = userCourseNotificationStatusRepository;
    }

    private MaxAndReachablePointsDTO calculateMaxAndReachablePoints(@Nullable Course course, @Nullable GradingScale gradingScale, Set<ExerciseCourseScoreDTO> exercises) {
        CourseScoreSettingsDTO settings = course != null ? CourseScoreSettingsDTO.from(course) : new CourseScoreSettingsDTO(2, null);
        GradedPresentationConfigDTO presentationConfig = gradedPresentationConfig(gradingScale);
        return CourseScoreCalculator.createContext(settings, presentationConfig, exercises, TimeUtil.now()).maxAndReachablePoints();
    }

    private static @Nullable GradedPresentationConfigDTO gradedPresentationConfig(@Nullable GradingScale gradingScale) {
        if (gradingScale == null || gradingScale.getCourse() == null) {
            return null;
        }
        return new GradedPresentationConfigDTO(Objects.requireNonNullElse(gradingScale.getPresentationsNumber(), 0),
                Objects.requireNonNullElse(gradingScale.getPresentationsWeight(), 0.0));
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

        MaxAndReachablePointsDTO maxAndReachablePoints = calculateMaxAndReachablePoints(course, gradingScale, courseExercises);

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
     * @param course       the course to calculate the items for.
     * @param gradingScale the grading scale with the presentation configuration to use for calculating the presentation points.
     * @param userId       the id of the students whose scores in the course will be calculated.
     * @return the CourseForDashboardDTO containing all the mentioned items.
     */
    public CourseForDashboardDTO getScoresAndParticipationResults(Course course, @Nullable GradingScale gradingScale, long userId) {
        Set<StudentParticipation> gradedStudentParticipations = new HashSet<>();
        for (Exercise exercise : course.getExercises()) {
            exercise.setCourse(course);
            // This method is used in the CourseOverviewResource where the course is first fetched with lazy participations, and participations are then fetched separately in the
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

        MaxAndReachablePointsDTO maxAndReachablePoints = calculateMaxAndReachablePoints(course, gradingScale, exerciseCourseScores);

        List<PlagiarismCase> plagiarismCases = new ArrayList<>();
        for (Exercise exercise : courseExercises) {
            // TODO: implement this differently
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

        // Computed before the loop below nulls each participation's exercise, while the group membership is still available.
        Map<Long, Double> achievedPointsPerVariantGroup = calculateAchievedPointsPerVariantGroup(userId, gradedStudentParticipations, plagiarismCases);

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

        return new CourseForDashboardDTO(CourseDashboardDTO.of(course), totalScores, scoresPerExerciseType.get(ExerciseType.TEXT),
                scoresPerExerciseType.get(ExerciseType.PROGRAMMING), scoresPerExerciseType.get(ExerciseType.MODELING), scoresPerExerciseType.get(ExerciseType.FILE_UPLOAD),
                scoresPerExerciseType.get(ExerciseType.QUIZ), participationResults,
                userCourseNotificationStatusRepository.countUnseenCourseNotificationsForUserInCourse(userId, course.getId()), achievedPointsPerVariantGroup);
    }

    /**
     * Calculates the plagiarism-adjusted points a student earns per variant group, keyed by group id. Groups with a
     * configured {@code maxPoints} are capped at it; groups without one contribute their raw sum. A course-wide
     * {@link PlagiarismVerdict#PLAGIARISM} verdict zeroes the whole course (empty map); otherwise each contribution runs
     * through {@link #calculatePointsAchievedFromExercise}, applying the per-exercise plagiarism deduction.
     *
     * @param userId                  the id of the student whose per-group points are calculated
     * @param participationsOfStudent the student's graded participations (exercises must still be attached)
     * @param plagiarismCases         the plagiarism verdicts relevant for the student
     * @return the plagiarism-adjusted points per variant group id (capped where a cap is configured); empty when no
     *         variant group contributes
     */
    Map<Long, Double> calculateAchievedPointsPerVariantGroup(long userId, Collection<StudentParticipation> participationsOfStudent, Collection<PlagiarismCase> plagiarismCases) {
        if (participationsOfStudent.isEmpty()) {
            return Map.of();
        }
        Set<ExerciseCourseScoreDTO> exercises = participationsOfStudent.stream().map(StudentParticipation::getExercise).map(ExerciseCourseScoreDTO::from)
                .collect(Collectors.toSet());
        Course course = participationsOfStudent.iterator().next().getExercise().getCourseViaExerciseGroupOrCourseMemberElseThrow();
        var context = new CourseScoreContextDTO(CourseScoreSettingsDTO.from(course), null, exercises, TimeUtil.now(), new MaxAndReachablePointsDTO(0.0, 0.0, 0.0));
        var studentInput = new StudentCourseScoreInputDTO(userId, gradeScoresFromParticipations(participationsOfStudent, userId), plagiarismScoreDTOs(plagiarismCases), 0.0, 0L);
        return CourseScoreCalculator.calculateAchievedPointsPerVariantGroup(context, studentInput);
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
            var maxAndReachablePoints = calculateMaxAndReachablePoints(course, null, exerciseCourseScores);
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
        var presentationConfig = gradedPresentationConfig(gradingScale);
        var context = new CourseScoreContextDTO(CourseScoreSettingsDTO.from(course), presentationConfig, courseExercises, TimeUtil.now(), maxAndReachablePoints);
        var studentInput = studentScoreInput(course, studentId, courseGradeScoreDTOsOfStudent, plagiarismCases, context);
        return CourseScoreCalculator.calculateCourseScoreForStudent(context, studentInput);
    }

    /**
     * Converts participation results into the projected input consumed by the shared score calculator.
     *
     * @param participations the student's persisted participations; each must have an id for the grade-score projection
     * @param studentId      the student whose scores are projected
     * @return one score input per participation, with unrated results marked as such
     */
    private List<CourseGradeScoreDTO> gradeScoresFromParticipations(Collection<StudentParticipation> participations, long studentId) {
        List<CourseGradeScoreDTO> gradeScores = new ArrayList<>();
        for (StudentParticipation participation : participations) {
            Exercise exercise = participation.getExercise();
            boolean exerciseExcludedFromScore = exercise.getIncludedInOverallScore() == IncludedInOverallScore.NOT_INCLUDED;
            Result result = exerciseExcludedFromScore ? null : getResultForParticipation(participation, exercise.getDueDate());
            gradeScores.add(new CourseGradeScoreDTO(participation.getId(), studentId, exercise.getId(), result != null && result.getScore() != null ? result.getScore() : 0.0,
                    result != null && result.isRated(), participation.getPresentationScore(), exercise.getExerciseType()));
        }
        return gradeScores;
    }

    /**
     * Adds the student-level presentation inputs and projected plagiarism cases to the shared calculator input.
     *
     * @param course          the course whose presentation scores are read if needed
     * @param studentId       the student whose scores are calculated
     * @param gradeScores     the student's exercise scores
     * @param plagiarismCases the relevant course plagiarism cases
     * @param context         the shared course score context
     * @return the complete input for calculating this student's scores
     */
    private StudentCourseScoreInputDTO studentScoreInput(Course course, long studentId, Collection<CourseGradeScoreDTO> gradeScores, Collection<PlagiarismCase> plagiarismCases,
            CourseScoreContextDTO context) {
        double gradedPresentationScoreSum = context.usesGradedPresentations() ? studentParticipationRepository.sumPresentationScoreByStudentIdAndCourseId(course.getId(), studentId)
                : 0.0;
        long basicPresentationScoreCount = gradeScores.stream().filter(score -> score.presentationScore() != null && score.presentationScore() > 0.0).count();
        return new StudentCourseScoreInputDTO(studentId, gradeScores, plagiarismScoreDTOs(plagiarismCases), gradedPresentationScoreSum, basicPresentationScoreCount);
    }

    private static List<PlagiarismCaseScoreDTO> plagiarismScoreDTOs(Collection<PlagiarismCase> plagiarismCases) {
        return plagiarismCases.stream().flatMap(plagiarismCase -> plagiarismCase.getStudents().stream().map(student -> new PlagiarismCaseScoreDTO(student.getId(),
                plagiarismCase.getExercise().getId(), plagiarismCase.getVerdict(), plagiarismCase.getVerdictPointDeduction()))).toList();
    }

    /**
     * Calculates course scores for a given set of student participations for a particular student.
     *
     * @param course                  the course the scores are calculated for.
     * @param gradingScale            the grading scale of the course.
     * @param studentId               the id of the student who has participated in the course exercises.
     * @param participationsOfStudent the persisted exercise participations of the given student; every participation must have an id.
     * @param maxAndReachablePoints   max points and max reachable points in the given course.
     * @param plagiarismCases         the plagiarism verdicts for the student.
     * @return a StudentScoresDTO instance with the presentation score, relative and absolute points achieved by the given student.
     */
    public StudentScoresDTO calculateCourseScoreForStudentParticipations(Course course, @Nullable GradingScale gradingScale, Long studentId,
            Collection<StudentParticipation> participationsOfStudent, MaxAndReachablePointsDTO maxAndReachablePoints, Collection<PlagiarismCase> plagiarismCases) {
        if (participationsOfStudent.isEmpty()) {
            return new StudentScoresDTO(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        Set<ExerciseCourseScoreDTO> exercises = participationsOfStudent.stream().map(StudentParticipation::getExercise).map(ExerciseCourseScoreDTO::from)
                .collect(Collectors.toSet());
        var context = new CourseScoreContextDTO(CourseScoreSettingsDTO.from(course), gradedPresentationConfig(gradingScale), exercises, TimeUtil.now(), maxAndReachablePoints);
        var gradeScores = gradeScoresFromParticipations(participationsOfStudent, studentId);
        var studentInput = studentScoreInput(course, studentId, gradeScores, plagiarismCases, context);
        return CourseScoreCalculator.calculateCourseScoreForStudent(context, studentInput);
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
        return calculateMaxAndReachablePoints(gradingScale != null ? gradingScale.getCourse() : null, gradingScale, exercises).reachablePoints();
    }

}
