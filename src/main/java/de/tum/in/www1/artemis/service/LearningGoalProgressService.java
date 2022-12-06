package de.tum.in.www1.artemis.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.util.RoundingUtil;

/**
 * Service for calculating the progress of a student in a learning goal.
 */
@Service
public class LearningGoalProgressService {

    private final Logger logger = LoggerFactory.getLogger(LearningGoalProgressService.class);

    private final LearningGoalRepository learningGoalRepository;

    private final LearningGoalProgressRepository learningGoalProgressRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    public LearningGoalProgressService(LearningGoalRepository learningGoalRepository, LearningGoalProgressRepository learningGoalProgressRepository,
            StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ExerciseRepository exerciseRepository,
            LectureUnitRepository lectureUnitRepository) {
        this.learningGoalRepository = learningGoalRepository;
        this.learningGoalProgressRepository = learningGoalProgressRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * Asynchronously update the progress for all learning goals linked to the given learning object
     * @param learningObject The learning object for which to fetch the learning goals
     * @param user The user for which to update the progress
     */
    @Async
    public void updateProgressByLearningObject(ILearningObject learningObject, @NotNull User user) {
        logger.debug("Updating learning goal progress for user {}.", user.getLogin());
        try {
            SecurityUtils.setAuthorizationObject();

            Set<LearningGoal> learningGoals;
            if (learningObject instanceof Exercise exercise) {
                learningGoals = exerciseRepository.findByIdWithLearningGoals(exercise.getId()).map(Exercise::getLearningGoals).orElse(null);
            }
            else if (learningObject instanceof LectureUnit lectureUnit) {
                learningGoals = lectureUnitRepository.findByIdWithLearningGoals(lectureUnit.getId()).map(LectureUnit::getLearningGoals).orElse(null);
            }
            else {
                return;
            }

            if (learningGoals == null) {
                // Learning goals couldn't be loaded, the exercise/lecture unit might have already been deleted
                return;
            }

            learningGoals.forEach(learningGoal -> {
                updateProgress(learningGoal.getId(), user);
            });
        }
        catch (Exception e) {
            logger.error("Exception while updating progress for learning goal", e);
        }
    }

    /**
     * Updates the progress value (and confidence score) of the given learning goal and user
     * @param learningGoalId The id of the learning goal to update the progress for
     * @param user The user for which the progress should be updated
     */
    private void updateProgress(Long learningGoalId, User user) {
        var learningGoal = learningGoalRepository.findByIdWithExercisesAndLectureUnitsAndCompletions(learningGoalId).orElse(null);

        if (user == null || learningGoal == null) {
            // If the user or learning goal no longer exist, there is nothing to do
            return;
        }

        var learningGoalProgress = learningGoalProgressRepository.findEagerByLearningGoalIdAndUserId(learningGoalId, user.getId());

        if (learningGoalProgress.isPresent()) {
            var lastModified = learningGoalProgress.get().getLastModifiedDate();
            if (lastModified != null && lastModified.isAfter(Instant.now().minusSeconds(5))) {
                // If we have updated the progress within the last five seconds, skip it
                return;
            }
        }

        // Now do the heavy lifting
        var progress = learningGoalProgress.orElse(new LearningGoalProgress());
        List<ILearningObject> learningObjects = new ArrayList<>();

        List<LectureUnit> allLectureUnits = learningGoal.getLectureUnits().stream().filter(LectureUnit::isVisibleToStudents).toList();

        List<LectureUnit> lectureUnits = allLectureUnits.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).toList();
        List<Exercise> exercises = learningGoal.getExercises().stream().filter(Exercise::isVisibleToStudents).toList();

        learningObjects.addAll(lectureUnits);
        learningObjects.addAll(exercises);

        progress.setLearningGoal(learningGoal);
        progress.setUser(user);
        progress.setProgress(RoundingUtil.roundScoreSpecifiedByCourseSettings(calculateProgress(learningObjects, user), learningGoal.getCourse()));
        progress.setConfidence(RoundingUtil.roundScoreSpecifiedByCourseSettings(calculateConfidence(exercises, user), learningGoal.getCourse()));

        learningGoalProgressRepository.save(progress);

        logger.debug("Updated progress for user {} in learning goal {} to {}.", user.getLogin(), learningGoal.getId(), progress.getProgress());
    }

    /**
     * Calculate the progress value for the given user in a learning goal.
     * @param learningObjects A list of all learning objects linked to a specific learning goal
     * @param user The user for which the progress should be calculated
     * @return The percentage of completed learning objects by the user
     */
    private double calculateProgress(@NotNull List<ILearningObject> learningObjects, @NotNull User user) {
        var completions = learningObjects.stream().map(learningObject -> hasUserCompleted(user, learningObject)).toList();
        completions.forEach(completed -> logger.debug("{} completed {}", user.getLogin(), completed));
        return completions.stream().mapToInt(completed -> completed ? 100 : 0).summaryStatistics().getAverage();
    }

    /**
     * Calculate the confidence score for the given user in a learning goal.
     * @param exercises A list of all exercises linked to a specific learning goal
     * @param user The user for which the confidence score should be calculated
     * @return The average score of the user in all exercises linked to the learning goal
     */
    private double calculateConfidence(@NotNull List<Exercise> exercises, @NotNull User user) {
        var studentScores = studentScoreRepository.findAllByExercisesAndUser(exercises, user);
        var teamScores = teamScoreRepository.findAllByExercisesAndUser(exercises, user);
        return Stream.concat(studentScores.stream(), teamScores.stream()).map(ParticipantScore::getLastScore).mapToDouble(score -> score).summaryStatistics().getAverage();
    }

    /**
     * Checks if the user has completed the learning object.
     * @param user The user for which to check the completion status
     * @param learningObject The lecture unit or exercise
     * @return True if the user completed the lecture unit or has at least one result for the exercise, false otherwise
     */
    private boolean hasUserCompleted(@NotNull User user, ILearningObject learningObject) {
        if (learningObject instanceof LectureUnit lectureUnit) {
            return lectureUnit.getCompletedUsers().stream().map(LectureUnitCompletion::getUser).anyMatch(user1 -> user1.getId().equals(user.getId()));
        }
        else if (learningObject instanceof Exercise exercise) {
            var studentScores = studentScoreRepository.findAllByExercisesAndUser(List.of(exercise), user);
            var teamScores = teamScoreRepository.findAllByExercisesAndUser(List.of(exercise), user);
            return Stream.concat(studentScores.stream(), teamScores.stream()).findAny().isPresent();
        }
        throw new IllegalArgumentException("Completable must be either LectureUnit or Exercise");
    }

}
