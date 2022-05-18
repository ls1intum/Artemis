package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class LearningGoalService {

    private final LearningGoalRepository learningGoalRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final AuthorizationCheckService authCheckService;

    public LearningGoalService(LearningGoalRepository learningGoalRepository, StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository,
            StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, AuthorizationCheckService authCheckService) {
        this.learningGoalRepository = learningGoalRepository;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Search for all learning goals fitting a {@link PageableSearchDTO search query}. The result is paged.
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found learning goals and the total number of pages
     */
    public SearchResultPageDTO<LearningGoal> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createLearningGoalPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningGoal> lecturePage;
        if (authCheckService.isAdmin(user)) {
            lecturePage = learningGoalRepository.findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(searchTerm, searchTerm, pageable);
        }
        else {
            lecturePage = learningGoalRepository.findByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(lecturePage.getContent(), lecturePage.getTotalPages());
    }

    /**
     * Calculates the progress of the given user in the given exercise units
     * <p>
     * Note: In the case of two exercise units referencing the same exercise, only the first exercise unit will be used.
     * <p>
     * Note: Please note that we take always the last submission into account here. Even submissions after the due date. This means for example that a student can improve his/her
     * progress by re-trying a quiz as often as you like. It is therefore normal, that the points here might differ from the points officially achieved in an exercise.
     *
     * @param exerciseUnits            exercise units to check
     * @param user                     user to check for
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @return progress of the user in the exercise units
     */
    public Set<IndividualLearningGoalProgress.IndividualLectureUnitProgress> calculateExerciseUnitsProgress(Set<ExerciseUnit> exerciseUnits, User user,
            boolean useParticipantScoreTable) {
        // for each exercise unit, the exercise will be mapped to a freshly created lecture unit progress.
        Map<Exercise, IndividualLearningGoalProgress.IndividualLectureUnitProgress> exerciseToLectureUnitProgress = exerciseUnits.stream()
                .filter(exerciseUnit -> exerciseUnit.getExercise() != null && exerciseUnit.getExercise().isAssessmentDueDateOver())
                .collect(Collectors.toMap(ExerciseUnit::getExercise, exerciseUnit -> {
                    IndividualLearningGoalProgress.IndividualLectureUnitProgress individualLectureUnitProgress = new IndividualLearningGoalProgress.IndividualLectureUnitProgress();
                    individualLectureUnitProgress.lectureUnitId = exerciseUnit.getId();
                    individualLectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = exerciseUnit.getExercise().getMaxPoints();
                    return individualLectureUnitProgress;
                }, (progress1, progress2) -> progress1)); // in the case of two exercises referencing the same exercise, take the first one

        List<Exercise> individualExercises = exerciseToLectureUnitProgress.keySet().stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toList());
        List<Exercise> teamExercises = exerciseToLectureUnitProgress.keySet().stream().filter(Exercise::isTeamMode).collect(Collectors.toList());

        if (useParticipantScoreTable) {
            fillInScoreAchievedByStudentUsingParticipantScores(user, exerciseToLectureUnitProgress, individualExercises, teamExercises);
        }
        else {
            fillInScoreAchievedByStudentUsingParticipationsSubmissionsResults(user, exerciseToLectureUnitProgress, individualExercises, teamExercises);
        }

        return new HashSet<>(exerciseToLectureUnitProgress.values());
    }

    private void fillInScoreAchievedByStudentUsingParticipationsSubmissionsResults(User user,
            Map<Exercise, IndividualLearningGoalProgress.IndividualLectureUnitProgress> exerciseToLectureUnitProgress, List<Exercise> individualExercises,
            List<Exercise> teamExercises) {
        // for all relevant exercises the participations with submissions and results will be batch-loaded
        List<StudentParticipation> participationsOfTheStudent = getStudentParticipationsWithSubmissionsAndResults(user, individualExercises, teamExercises);

        for (Exercise exercise : exerciseToLectureUnitProgress.keySet()) {
            // exercise -> participation -> submission -> result until possibly the latest result is found for the student
            Optional<Result> optionalResult = findLastResultOfExerciseInListOfParticipations(exercise, participationsOfTheStudent);
            exerciseToLectureUnitProgress.get(exercise).scoreAchievedByStudentInLectureUnit = optionalResult.isEmpty() || optionalResult.get().getScore() == null ? 0.0
                    : optionalResult.get().getScore();
        }
    }

    private void fillInScoreAchievedByStudentUsingParticipantScores(User user,
            Map<Exercise, IndividualLearningGoalProgress.IndividualLectureUnitProgress> exerciseToLectureUnitProgress, List<Exercise> individualExercises,
            List<Exercise> teamExercises) {
        for (Exercise exercise : individualExercises) {
            Optional<StudentScore> studentScoreOptional = studentScoreRepository.findStudentScoreByExerciseAndUserLazy(exercise, user);
            exerciseToLectureUnitProgress.get(exercise).scoreAchievedByStudentInLectureUnit = studentScoreOptional.map(ParticipantScore::getLastScore).orElse(0.0);
        }

        for (Exercise exercise : teamExercises) {
            Optional<TeamScore> teamScoreOptional = teamScoreRepository.findTeamScoreByExerciseAndUserLazy(exercise, user);
            exerciseToLectureUnitProgress.get(exercise).scoreAchievedByStudentInLectureUnit = teamScoreOptional.map(ParticipantScore::getLastScore).orElse(0.0);
        }
    }

    /**
     * Calculates the course progress in the given exercise units
     * <p>
     * Note: In the case of two exercise units referencing the same exercise, only the first exercise unit will be used.
     * <p>
     * Note: Please note that we take always the last submission into account here. Even submissions after the due date. This means for example that a student can improve his/her
     * progress by re-trying a quiz as often as you like. It is therefore normal, that the points here might differ from the points officially achieved in an exercise.
     *
     * @param exerciseUnits            exercise units to check
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @return progress of the course in the exercise units
     */
    private Set<CourseLearningGoalProgress.CourseLectureUnitProgress> calculateExerciseUnitsProgressForCourse(List<ExerciseUnit> exerciseUnits, boolean useParticipantScoreTable) {
        List<ExerciseUnit> filteredExerciseUnits = exerciseUnits.stream()
                .filter(exerciseUnit -> exerciseUnit.getExercise() != null && exerciseUnit.getExercise().isAssessmentDueDateOver()).toList();
        List<Long> exerciseIds = filteredExerciseUnits.stream().map(exerciseUnit -> exerciseUnit.getExercise().getId()).distinct().collect(Collectors.toList());

        Map<Long, CourseExerciseStatisticsDTO> exerciseIdToExerciseCourseStatistics = this.exerciseRepository.calculateExerciseStatistics(exerciseIds, useParticipantScoreTable)
                .stream().collect(Collectors.toMap(CourseExerciseStatisticsDTO::getExerciseId, courseExerciseStatisticsDTO -> courseExerciseStatisticsDTO));

        // for each exercise unit, the exercise will be mapped to a freshly created lecture unit course progress.
        Map<Exercise, CourseLearningGoalProgress.CourseLectureUnitProgress> exerciseToLectureUnitCourseProgress = filteredExerciseUnits.stream()
                .collect(Collectors.toMap(ExerciseUnit::getExercise, exerciseUnit -> {
                    CourseExerciseStatisticsDTO courseExerciseStatisticsDTO = exerciseIdToExerciseCourseStatistics.get(exerciseUnit.getExercise().getId());
                    CourseLearningGoalProgress.CourseLectureUnitProgress courseLectureUnitProgress = new CourseLearningGoalProgress.CourseLectureUnitProgress();
                    courseLectureUnitProgress.lectureUnitId = exerciseUnit.getId();
                    courseLectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = exerciseUnit.getExercise().getMaxPoints();
                    courseLectureUnitProgress.averageScoreAchievedByStudentInLectureUnit = courseExerciseStatisticsDTO.getAverageScoreInPercent();
                    courseLectureUnitProgress.noOfParticipants = courseExerciseStatisticsDTO.getNoOfParticipatingStudentsOrTeams();
                    courseLectureUnitProgress.participationRate = courseExerciseStatisticsDTO.getParticipationRateInPercent();
                    return courseLectureUnitProgress;
                }, (progress1, progress2) -> progress1)); // in the case of two exercises referencing the same exercise, take the first one

        return new HashSet<>(exerciseToLectureUnitCourseProgress.values());

    }

    /**
     * Finds the latest result for a given exercise in a list of relevant participations
     * @param exercise exercise to find the result for
     * @param participationsList participations with submissions and results that should be checked
     * @return optional containing the last result or else an empty optional
     */
    private Optional<Result> findLastResultOfExerciseInListOfParticipations(Exercise exercise, List<StudentParticipation> participationsList) {
        // find the relevant participation
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(participationsList);
        if (relevantParticipation == null) {
            return Optional.empty();
        }

        relevantParticipation.setSubmissions(relevantParticipation.getSubmissions().stream().filter(submission -> {
            boolean hasFittingResult = false;
            for (Result result : submission.getResults()) {
                if (result.getScore() != null && result.getCompletionDate() != null) {
                    hasFittingResult = true;
                    break;
                }
            }
            return hasFittingResult;
        }).collect(Collectors.toSet()));

        // find the latest submission of the relevant participation
        Optional<Submission> latestSubmissionOptional = relevantParticipation.findLatestSubmission();
        if (latestSubmissionOptional.isEmpty()) {
            return Optional.empty();
        }
        Submission latestSubmission = latestSubmissionOptional.get();

        latestSubmission
                .setResults(latestSubmission.getResults().stream().filter(result -> result.getScore() != null && result.getCompletionDate() != null).collect(Collectors.toList()));

        // find the latest result of the latest submission
        Result latestResult = latestSubmission.getLatestResult();
        return latestResult == null ? Optional.empty() : Optional.of(latestResult);
    }

    /**
     * Gets the participations of a user (including submissions and results) for specific individual or team exercises
     * @param user user to get the participations for
     * @param individualExercises individual exercises to get the participations for
     * @param teamExercises team exercises to get the participations for
     * @return list of student participations for the given exercises and user
     */
    private List<StudentParticipation> getStudentParticipationsWithSubmissionsAndResults(User user, List<Exercise> individualExercises, List<Exercise> teamExercises) {
        // 1st: fetch participations, submissions and results for individual exercises
        List<StudentParticipation> participationsOfIndividualExercises = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(), individualExercises);

        // 2nd: fetch participations, submissions and results for team exercises
        List<StudentParticipation> participationsOfTeamExercises = studentParticipationRepository.findByStudentIdAndTeamExercisesWithEagerLegalSubmissionsResult(user.getId(),
                teamExercises);

        // 3rd: merge both into one list for further processing
        return Stream.concat(participationsOfIndividualExercises.stream(), participationsOfTeamExercises.stream()).collect(Collectors.toList());
    }

    /**
     * Calculate the progress in a learning goal for a specific user
     *
     * @param learningGoal             learning goal to get the progress for
     * @param user                     user to get the progress for
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @return progress of the user in the learning goal
     */
    public IndividualLearningGoalProgress calculateLearningGoalProgress(LearningGoal learningGoal, User user, boolean useParticipantScoreTable) {

        IndividualLearningGoalProgress individualLearningGoalProgress = new IndividualLearningGoalProgress();
        individualLearningGoalProgress.studentId = user.getId();
        individualLearningGoalProgress.learningGoalId = learningGoal.getId();
        individualLearningGoalProgress.learningGoalTitle = learningGoal.getTitle();
        individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 0.0;
        individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal = 0.0;

        // The progress will be calculated from a subset of the connected lecture units (currently only from released exerciseUnits)
        Set<ExerciseUnit> exerciseUnitsUsableForProgressCalculation = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents)
                .filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> (ExerciseUnit) lectureUnit).collect(Collectors.toSet());
        Set<IndividualLearningGoalProgress.IndividualLectureUnitProgress> progressInLectureUnits = this.calculateExerciseUnitsProgress(exerciseUnitsUsableForProgressCalculation,
                user, useParticipantScoreTable);

        // updating learningGoalPerformance by summing up the points of the individual lecture unit performances
        individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = progressInLectureUnits.stream()
                .map(individualLectureUnitProgress -> individualLectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit).reduce(0.0, Double::sum);

        individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal = progressInLectureUnits.stream()
                .map(individualLectureUnitProgress -> (individualLectureUnitProgress.scoreAchievedByStudentInLectureUnit / 100.0)
                        * individualLectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit)
                .reduce(0.0, Double::sum);

        individualLearningGoalProgress.progressInLectureUnits = new ArrayList<>(progressInLectureUnits);
        return individualLearningGoalProgress;
    }

    /**
     * Calculate the progress in a learning goal for a whole course
     *
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @param learningGoal             learning goal to get the progress for
     * @return progress of the course in the learning goal
     */
    public CourseLearningGoalProgress calculateLearningGoalCourseProgress(LearningGoal learningGoal, boolean useParticipantScoreTable) {
        CourseLearningGoalProgress courseLearningGoalProgress = new CourseLearningGoalProgress();
        courseLearningGoalProgress.courseId = learningGoal.getCourse().getId();
        courseLearningGoalProgress.learningGoalId = learningGoal.getId();
        courseLearningGoalProgress.learningGoalTitle = learningGoal.getTitle();
        courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 0.0;
        courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal = 0.0;

        // The progress will be calculated from a subset of the connected lecture units (currently only from released exerciseUnits)
        List<ExerciseUnit> exerciseUnitsUsableForProgressCalculation = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents)
                .filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> (ExerciseUnit) lectureUnit).collect(Collectors.toList());
        Set<CourseLearningGoalProgress.CourseLectureUnitProgress> progressInLectureUnits = this.calculateExerciseUnitsProgressForCourse(exerciseUnitsUsableForProgressCalculation,
                useParticipantScoreTable);

        // updating learningGoalPerformance by summing up the points of the individual lecture unit progress
        courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = progressInLectureUnits.stream()
                .map(lectureUnitProgress -> lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit).reduce(0.0, Double::sum);

        courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal = progressInLectureUnits.stream().map(
                lectureUnitProgress -> (lectureUnitProgress.averageScoreAchievedByStudentInLectureUnit / 100.0) * lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit)
                .reduce(0.0, Double::sum);

        courseLearningGoalProgress.progressInLectureUnits = new ArrayList<>(progressInLectureUnits);
        return courseLearningGoalProgress;

    }

}
