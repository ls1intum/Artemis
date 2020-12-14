package de.tum.in.www1.artemis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.web.rest.dto.LearningGoalProgress;

@Service
public class LearningGoalService {

    private final ParticipationService participationService;

    public LearningGoalService(ParticipationService participationService) {
        this.participationService = participationService;
    }

    /**
     * Calculates the progress of the given user in the given exercise units
     *
     * Note: In the case of two exercise units referencing the same exercise, only the first exercise unit will be used.
     *
     * Note: Please note that we take always the last submission into account here. Even submissions after the due date. This means for example that a student can improve his/her
     * progress by re-trying a quiz as often as you like. It is therefore normal, that the points here might differ from the points officially achieved in an exercise.
     *
     * @param exerciseUnits exercise units to check
     * @param user user to check for
     * @return progress of the user in the exercise units
     */
    public Set<LearningGoalProgress.LectureUnitProgress> calculateExerciseUnitsProgress(Set<ExerciseUnit> exerciseUnits, User user) {
        // for each exercise unit, the exercise will be mapped to a freshly created lecture unit progress.
        Map<Exercise, LearningGoalProgress.LectureUnitProgress> exerciseToLectureUnitProgress = exerciseUnits.stream()
                .filter(exerciseUnit -> exerciseUnit.getExercise() != null && exerciseUnit.getExercise().isAssessmentDueDateOver())
                .collect(Collectors.toMap(ExerciseUnit::getExercise, exerciseUnit -> {
                    LearningGoalProgress.LectureUnitProgress lectureUnitProgress = new LearningGoalProgress.LectureUnitProgress();
                    lectureUnitProgress.lectureTitle = exerciseUnit.getLecture().getTitle();
                    lectureUnitProgress.lectureId = exerciseUnit.getLecture().getId();
                    lectureUnitProgress.lectureUnitId = exerciseUnit.getId();
                    lectureUnitProgress.lectureUnitTitle = exerciseUnit.getExercise().getTitle();
                    lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = exerciseUnit.getExercise().getMaxScore();
                    return lectureUnitProgress;
                }, (progress1, progress2) -> progress1)); // in the case of two exercises referencing the same exercise, take the first one

        List<Exercise> individualExercises = exerciseToLectureUnitProgress.keySet().stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toList());
        List<Exercise> teamExercises = exerciseToLectureUnitProgress.keySet().stream().filter(Exercise::isTeamMode).collect(Collectors.toList());

        // for all relevant exercises the participations with submissions and results will be batch loaded
        List<StudentParticipation> participationsOfTheStudent = getStudentParticipationsWithSubmissionsAndResults(user, individualExercises, teamExercises);

        // exercise -> participation -> submission -> result until possibly the latest result is found for the student
        for (Exercise exercise : exerciseToLectureUnitProgress.keySet()) {
            Optional<Result> optionalResult = findLastResultOfExerciseInListOfParticipatons(exercise, participationsOfTheStudent);

            if (optionalResult.isEmpty()) {
                exerciseToLectureUnitProgress.get(exercise).pointsAchievedByStudentInLectureUnit = 0.0;
            }
            else {
                Result result = optionalResult.get();
                if (result.getScore() == null) {
                    exerciseToLectureUnitProgress.get(exercise).pointsAchievedByStudentInLectureUnit = 0.0;
                }
                else {
                    BigDecimal points = new BigDecimal((result.getScore() / 100.0) * exercise.getMaxScore());
                    points = points.setScale(2, RoundingMode.HALF_EVEN);
                    exerciseToLectureUnitProgress.get(exercise).pointsAchievedByStudentInLectureUnit = points.doubleValue();
                }
            }

        }

        return new HashSet<>(exerciseToLectureUnitProgress.values());
    }

    /**
     * Finds the latest result for a given exercise in a list of relevant participations
     * @param exercise exercise to find the result for
     * @param participationsList participations with submissions and results that should be checked
     * @return optional containing the last result or else an empty optional
     */
    private Optional<Result> findLastResultOfExerciseInListOfParticipatons(Exercise exercise, List<StudentParticipation> participationsList) {
        StudentParticipation relevantParticipation = exercise.findRelevantParticipation(participationsList);
        if (relevantParticipation == null) {
            return Optional.empty();
        }
        else {
            Optional<Submission> latestSubmissionOptional = relevantParticipation.findLatestSubmission();
            if (latestSubmissionOptional.isEmpty()) {
                return Optional.empty();
            }
            else {
                Submission latestSubmission = latestSubmissionOptional.get();
                Result latestResult = latestSubmission.getLatestResult();
                if (latestResult == null) {
                    return Optional.empty();
                }
                else {
                    return Optional.of(latestResult);
                }
            }
        }
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
        List<StudentParticipation> participationsOfIndividualExercises = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(user.getId(),
                individualExercises);

        // 2nd: fetch participations, submissions and results for team exercises
        List<StudentParticipation> participationsOfTeamExercises = participationService.findByStudentIdAndTeamExercisesWithEagerSubmissionsResult(user.getId(), teamExercises);

        // 3rd: merge both into one list for further processing
        return Stream.concat(participationsOfIndividualExercises.stream(), participationsOfTeamExercises.stream()).collect(Collectors.toList());
    }

    /**
     * Calculate the progress in a learning goal for a specific user
     *
     * @param learningGoal learning goal to get the progress for
     * @param user user to get the progress for
     * @return progress of the user in the learning goal
     */
    public LearningGoalProgress calculateLearningGoalProgress(LearningGoal learningGoal, User user) {

        LearningGoalProgress learningGoalProgress = new LearningGoalProgress();
        learningGoalProgress.learningGoalId = learningGoal.getId();
        learningGoalProgress.learningGoalTitle = learningGoal.getTitle();
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 0.0;
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 0.0;

        // The progress will be calculated from a subset of the connected lecture units (currently only from released exerciseUnits)
        Set<ExerciseUnit> exerciseUnitsUsableForProgressCalculation = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents)
                .filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> (ExerciseUnit) lectureUnit).collect(Collectors.toSet());
        Set<LearningGoalProgress.LectureUnitProgress> progressInLectureUnits = this.calculateExerciseUnitsProgress(exerciseUnitsUsableForProgressCalculation, user);

        // updating learningGoalPerformance by summing up the points of the individual lecture unit performances
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = progressInLectureUnits.stream()
                .map(lectureUnitProgress -> lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit).reduce(0.0, Double::sum);

        learningGoalProgress.pointsAchievedByStudentInLearningGoal = progressInLectureUnits.stream()
                .map(lectureUnitProgress -> lectureUnitProgress.pointsAchievedByStudentInLectureUnit).reduce(0.0, Double::sum);

        learningGoalProgress.progressInLectureUnits = new ArrayList<>(progressInLectureUnits);
        return learningGoalProgress;
    }
}
