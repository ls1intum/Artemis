package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.dto.LearningGoalProgress;

@Service
public class LearningGoalService {

    private final LearningGoalRepository learningGoalRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    public LearningGoalService(LearningGoalRepository learningGoalRepository, AuthorizationCheckService authorizationCheckService, ParticipationService participationService,
            ResultRepository resultRepository) {
        this.learningGoalRepository = learningGoalRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
    }

    /**
     * Calculates the progress of the given user in the given exercise units
     *
     * Note: In the case of two exercise units referencing the same exercise, only the first exercise unit will be used.
     *
     * @param exerciseUnits exercise units to check
     * @param user user to check for
     * @return progress of the user in the exercise units
     */
    public Set<LearningGoalProgress.LectureUnitProgress> calculateExerciseUnitsProgress(Set<ExerciseUnit> exerciseUnits, User user) {
        // This will also make sure that every exercise is only counted once
        Map<Exercise, LearningGoalProgress.LectureUnitProgress> exerciseToExerciseUnit = exerciseUnits.stream().filter(exerciseUnit -> exerciseUnit.getExercise() != null)
                .filter(exerciseUnit -> exerciseUnit.getExercise().isAssessmentDueDateOver()).collect(Collectors.toMap(ExerciseUnit::getExercise, exerciseUnit -> {
                    LearningGoalProgress.LectureUnitProgress lectureUnitProgress = new LearningGoalProgress.LectureUnitProgress();
                    lectureUnitProgress.lectureTitle = exerciseUnit.getLecture().getTitle();
                    lectureUnitProgress.lectureId = exerciseUnit.getLecture().getId();
                    lectureUnitProgress.lectureUnitId = exerciseUnit.getId();
                    lectureUnitProgress.lectureUnitTitle = exerciseUnit.getExercise().getTitle();
                    lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = exerciseUnit.getExercise().getMaxScore();
                    return lectureUnitProgress;
                }, (progress1, progress2) -> progress1));

        List<Exercise> individualExercises = exerciseToExerciseUnit.keySet().stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toList());
        List<Exercise> teamExercises = exerciseToExerciseUnit.keySet().stream().filter(Exercise::isTeamMode).collect(Collectors.toList());

        // 1st: fetch participations, submissions and results for individual exercises
        List<StudentParticipation> participationsOfIndividualExercises = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(user.getId(),
                individualExercises);

        // 2nd: fetch participations, submissions and results for team exercises
        List<StudentParticipation> participationsOfTeamExercises = participationService.findByStudentIdAndTeamExercisesWithEagerSubmissionsResult(user.getId(), teamExercises);

        // 3rd: merge both into one list for further processing
        List<StudentParticipation> participations = Stream.concat(participationsOfIndividualExercises.stream(), participationsOfTeamExercises.stream())
                .collect(Collectors.toList());

        for (Exercise exercise : exerciseToExerciseUnit.keySet()) {
            StudentParticipation relevantParticipation = exercise.findRelevantParticipation(participations);
            if (relevantParticipation == null) {
                exerciseToExerciseUnit.get(exercise).pointsAchievedByStudentInLectureUnit = 0.0;
            }
            else {
                Optional<Submission> latestSubmissionOptional = relevantParticipation.findLatestSubmission();
                if (latestSubmissionOptional.isEmpty()) {
                    exerciseToExerciseUnit.get(exercise).pointsAchievedByStudentInLectureUnit = 0.0;
                }
                else {
                    Submission latestSubmission = latestSubmissionOptional.get();
                    Result latestResult = latestSubmission.getResult();
                    if (latestResult == null) {
                        exerciseToExerciseUnit.get(exercise).pointsAchievedByStudentInLectureUnit = 0.0;
                    }
                    else {
                        exerciseToExerciseUnit.get(exercise).pointsAchievedByStudentInLectureUnit = Math.round((latestResult.getScore() / 100.0 * exercise.getMaxScore()) * 100.0)
                                / 100.0;
                    }

                }
            }

        }

        return new HashSet<>(exerciseToExerciseUnit.values());
    }

    /**
     * Calculate the progress in a learning goal for a specific user
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

        // The progress will be calculated from a subset of the connected lecture units (currently only from exerciseUnits)
        Set<ExerciseUnit> exerciseUnitsUsableForProgressCalculation = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents)
                .filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> (ExerciseUnit) lectureUnit).collect(Collectors.toSet());
        Set<LearningGoalProgress.LectureUnitProgress> progressInLectureUnits = this.calculateExerciseUnitsProgress(exerciseUnitsUsableForProgressCalculation, user);

        // updating learningGoalPerformance
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = progressInLectureUnits.stream()
                .map(lectureUnitProgress -> lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit).reduce(0.0, Double::sum);

        learningGoalProgress.pointsAchievedByStudentInLearningGoal = progressInLectureUnits.stream()
                .map(lectureUnitProgress -> lectureUnitProgress.pointsAchievedByStudentInLectureUnit).reduce(0.0, Double::sum);

        learningGoalProgress.progressInLectureUnits = new ArrayList<>(progressInLectureUnits);
        return learningGoalProgress;
    }
}
