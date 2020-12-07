package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
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
     * Calculate the progress in a learning goal for a specific user
     * @param learningGoal learning goal to get the progress for
     * @param user user to get the progress for
     * @return progress of the user in the learning goal
     */
    public LearningGoalProgress getLearningGoalPerformance(LearningGoal learningGoal, User user) {

        LearningGoalProgress learningGoalProgress = new LearningGoalProgress();
        learningGoalProgress.learningGoalId = learningGoal.getId();
        learningGoalProgress.learningGoalTitle = learningGoal.getTitle();
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 0.0;
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 0.0;

        // The progress will be calculated from a subset of the connected lecture units
        Set<LectureUnit> filteredLectureUnits = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents).filter(lectureUnit -> {
            if (lectureUnit instanceof ExerciseUnit) {
                Exercise exercise = ((ExerciseUnit) lectureUnit).getExercise();
                return exercise.isAssessmentDueDateOver();
            }
            else {
                return true;
            }
        }).collect(Collectors.toSet());

        // In the case that two or more connected exercise units reference the same exercise, only one of them should be used in the progress calculation
        Set<Exercise> exercisesAlreadyUsedInCalculation = new HashSet<>();

        for (LectureUnit lectureUnit : filteredLectureUnits) {
            LearningGoalProgress.LectureUnitProgress lectureUnitProgress = new LearningGoalProgress.LectureUnitProgress();
            lectureUnitProgress.lectureUnitId = lectureUnit.getId();
            // ToDo implement way to track progress for lecture units other than exercise units
            if (lectureUnit instanceof ExerciseUnit) {
                Exercise exercise = ((ExerciseUnit) lectureUnit).getExercise();
                // skip the exercise unit if the exercise was already used in the calculation
                if (exercisesAlreadyUsedInCalculation.contains(exercise)) {
                    continue;
                }
                else {
                    exercisesAlreadyUsedInCalculation.add(exercise);
                }
                lectureUnitProgress.lectureId = lectureUnit.getLecture().getId();
                lectureUnitProgress.lectureTitle = lectureUnit.getLecture().getTitle();
                lectureUnitProgress.lectureUnitTitle = exercise.getTitle();

                lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = exercise.getMaxScore();
                List<StudentParticipation> studentParticipationList = participationService.findByExerciseAndStudentId(exercise, user.getId());
                StudentParticipation relevantParticipation = exercise.findRelevantParticipation(studentParticipationList);

                if (relevantParticipation == null) {
                    lectureUnitProgress.pointsAchievedByStudentInLectureUnit = 0.0;
                }
                else {
                    Optional<Result> latestResultOptional = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(relevantParticipation.getId());
                    if (latestResultOptional.isEmpty()) {
                        lectureUnitProgress.pointsAchievedByStudentInLectureUnit = 0.0;
                    }
                    else {
                        Result latestResult = latestResultOptional.get();
                        lectureUnitProgress.pointsAchievedByStudentInLectureUnit = latestResult.getScore() / 100.0 * exercise.getMaxScore();
                        // rounding
                        lectureUnitProgress.pointsAchievedByStudentInLectureUnit = Math.round(lectureUnitProgress.pointsAchievedByStudentInLectureUnit * 100.0) / 100.0;

                    }
                }
                // updating learningGoalPerformance
                learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal += lectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit;
                learningGoalProgress.pointsAchievedByStudentInLearningGoal += lectureUnitProgress.pointsAchievedByStudentInLectureUnit;
                learningGoalProgress.progressInLectureUnits.add(lectureUnitProgress);
            }
        }
        return learningGoalProgress;

    }
}
