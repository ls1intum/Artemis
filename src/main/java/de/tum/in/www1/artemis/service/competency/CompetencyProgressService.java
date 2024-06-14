package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.MIN_SCORE_GREEN;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.service.util.TimeUtil.toRelativeTime;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.enumeration.CompetencyProgressConfidenceReason;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.LearningObjectService;
import de.tum.in.www1.artemis.service.ParticipantScoreService;
import de.tum.in.www1.artemis.service.learningpath.LearningPathService;
import de.tum.in.www1.artemis.service.util.RoundingUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyExerciseMasteryCalculationDTO;

/**
 * Service for calculating the progress of a student in a competency.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyProgressService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyProgressService.class);

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    private final LearningPathService learningPathService;

    private final LearningObjectService learningObjectService;

    private final ParticipantScoreService participantScoreService;

    public CompetencyProgressService(CompetencyRepository competencyRepository, CompetencyProgressRepository competencyProgressRepository, ExerciseRepository exerciseRepository,
            LectureUnitRepository lectureUnitRepository, UserRepository userRepository, LearningPathService learningPathService, LearningObjectService learningObjectService,
            ParticipantScoreService participantScoreService) {
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.userRepository = userRepository;
        this.learningPathService = learningPathService;
        this.learningObjectService = learningObjectService;
        this.participantScoreService = participantScoreService;
    }

    /**
     * Asynchronously update the progress for competencies linked to the given learning object (for the specified participant)
     *
     * @param learningObject The learning object for which to fetch the competencies
     * @param participant    The participant (user or team) for which to update the progress
     */
    @Async
    public void updateProgressByLearningObjectAsync(LearningObject learningObject, @NotNull Participant participant) {
        SecurityUtils.setAuthorizationObject(); // required for async
        updateProgressByLearningObject(learningObject, participant.getParticipants());
    }

    /**
     * Asynchronously update the progress for the competencies linked to the given learning object (for all students in the course)
     *
     * @param learningObject The learning object for which to fetch the competencies
     */
    @Async
    public void updateProgressByLearningObjectAsync(LearningObject learningObject) {
        SecurityUtils.setAuthorizationObject(); // required for async
        Course course;
        switch (learningObject) {
            case Exercise exercise -> course = exercise.getCourseViaExerciseGroupOrCourseMember();
            case LectureUnit lectureUnit -> course = lectureUnit.getLecture().getCourse();
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        }
        updateProgressByLearningObject(learningObject, userRepository.getStudents(course));
    }

    /**
     * Asynchronously update the existing progress for a specific competency
     *
     * @param competency The competency for which to update all existing student progress
     */
    @Async
    public void updateProgressByCompetencyAsync(Competency competency) {
        SecurityUtils.setAuthorizationObject(); // required for async
        competencyProgressRepository.findAllByCompetencyId(competency.getId()).stream().map(CompetencyProgress::getUser)
                .forEach(user -> updateCompetencyProgress(competency.getId(), user));
    }

    /**
     * Update the progress for all competencies linked to the given learning object
     *
     * @param learningObject The learning object for which to fetch the competencies
     * @param users          A list of users for which to update the progress
     */
    public void updateProgressByLearningObject(LearningObject learningObject, @NotNull Set<User> users) {
        log.debug("Updating competency progress for {} users.", users.size());
        try {
            Set<Competency> competencies;
            switch (learningObject) {
                case Exercise exercise -> competencies = exerciseRepository.findWithCompetenciesById(exercise.getId()).map(Exercise::getCompetencies).orElse(null);
                case LectureUnit lectureUnit -> competencies = lectureUnitRepository.findWithCompetenciesById(lectureUnit.getId()).map(LectureUnit::getCompetencies).orElse(null);
                default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
            }

            if (competencies == null) {
                // Competencies couldn't be loaded, the exercise/lecture unit might have already been deleted
                log.debug("Competencies could not be fetched, skipping.");
                return;
            }

            users.forEach(user -> competencies.forEach(competency -> updateCompetencyProgress(competency.getId(), user)));
        }
        catch (Exception e) {
            log.error("Exception while updating progress for competency", e);
        }
    }

    /**
     * Updates the progress value and confidence score of the given competency and user, then returns it
     *
     * @param competencyId The id of the competency to update the progress for
     * @param user         The user for which the progress should be updated
     * @return The updated competency progress, which is also persisted to the database
     */
    public CompetencyProgress updateCompetencyProgress(Long competencyId, User user) {
        Competency competency = competencyRepository.findByIdWithLectureUnitsAndCompletedUsers(competencyId);

        if (user == null || competency == null) {
            log.debug("User or competency no longer exist, skipping.");
            return null;
        }

        Set<LectureUnit> lectureUnits = competency.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos = competencyRepository.findAllExerciseInfoByCompetencyId(competencyId);

        var competencyProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId());

        if (competencyProgress.isPresent()) {
            var lastModified = competencyProgress.get().getLastModifiedDate();
            if (lastModified != null && lastModified.isAfter(Instant.now().minusSeconds(1))) {
                log.debug("Competency progress has been updated very recently, skipping.");
                return competencyProgress.get();
            }
        }

        var studentProgress = competencyProgress.orElse(new CompetencyProgress());

        calculateProgress(lectureUnits, exerciseInfos, user, studentProgress);
        calculateConfidence(exerciseInfos, studentProgress);

        studentProgress.setCompetency(competency);
        studentProgress.setUser(user);

        try {
            competencyProgressRepository.save(studentProgress);
        }
        catch (DataIntegrityViolationException e) {
            // In rare instances of initially creating a progress entity, async updates might run in parallel.
            // This fails the SQL unique constraint and throws an exception. We can safely ignore it.
        }

        log.debug("Updated progress for user {} in competency {} to {} / {}.", user.getLogin(), competency.getId(), studentProgress.getProgress(), studentProgress.getConfidence());

        learningPathService.updateLearningPathProgress(competency.getCourse().getId(), user.getId());
        return studentProgress;
    }

    /**
     * Calculate the progress for the given user in a competency.
     * The progress for exercises is the percentage of points achieved in all exercises linked to the competency.
     * The progress for lecture units is the percentage of lecture units completed by the user.
     * The final progress is a weighted average of the progress in exercises and lecture units.
     *
     * @param lectureUnits       The lecture units linked to the competency
     * @param exerciseInfos      The information about the exercises linked to the competency
     * @param user               The user for which the progress should be calculated
     * @param competencyProgress The progress entity to update
     */
    private void calculateProgress(Set<LectureUnit> lectureUnits, Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos, User user, CompetencyProgress competencyProgress) {
        double numberOfLearningObjects = lectureUnits.size() + exerciseInfos.size();
        if (numberOfLearningObjects == 0) {
            // If nothing is linked to the competency, the competency is considered completed
            competencyProgress.setProgress(100.0);
        }

        double achievedPoints = exerciseInfos.stream().map(CompetencyExerciseMasteryCalculationDTO::participantScore)
                .mapToDouble(score -> score != null && score.getLastPoints() != null ? score.getLastPoints() : 0).sum();
        double maxPoints = exerciseInfos.stream().mapToDouble(exerciseInfo -> exerciseInfo.exercise().getMaxPoints()).sum();
        double exerciseProgress = maxPoints > 0 ? achievedPoints / maxPoints * 100 : 0;

        double lectureProgress = lectureUnits.stream().mapToDouble(lectureUnit -> learningObjectService.isCompletedByUser(lectureUnit, user) ? 100 : 0).average().orElse(0.0);

        double progress = exerciseInfos.size() / numberOfLearningObjects * exerciseProgress + lectureUnits.size() / numberOfLearningObjects * lectureProgress;
        // Bonus points can lead to a progress > 100%
        progress = Math.clamp(Math.round(progress), 0, 100);
        competencyProgress.setProgress(progress);
    }

    /**
     * Calculate the confidence score for the given user in a competency based on the exercises linked to the competency.
     *
     * @param exerciseInfos      The information about the exercises linked to the competency
     * @param competencyProgress The progress entity to update
     */
    private void calculateConfidence(Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos, CompetencyProgress competencyProgress) {
        Set<CompetencyExerciseMasteryCalculationDTO> participantScoreInfos = exerciseInfos.stream().filter(info -> info.participantScore() != null
                && info.participantScore().getLastScore() != null && info.participantScore().getLastPoints() != null && info.participantScore().getLastModifiedDate() != null)
                .collect(Collectors.toSet());

        double recencyConfidenceHeuristic = calculateRecencyConfidenceHeuristic(participantScoreInfos);
        double difficultyConfidenceHeuristic = calculateDifficultyConfidenceHeuristic(participantScoreInfos, exerciseInfos);
        double quickSolveConfidenceHeuristic = calculateQuickSolveConfidenceHeuristic(participantScoreInfos);

        double confidence = 1 + recencyConfidenceHeuristic + difficultyConfidenceHeuristic + quickSolveConfidenceHeuristic;

        competencyProgress.setConfidence(confidence);
        setConfidenceReason(competencyProgress, recencyConfidenceHeuristic, difficultyConfidenceHeuristic, quickSolveConfidenceHeuristic);
    }

    /**
     * Calculate the recency confidence heuristic for the given user in a competency based on the exercises linked to the competency.
     * If the recent scores are higher than the average scores, the confidence should also be higher and vice versa.
     *
     * @param participantScores the participant scores for the exercises linked to the competency
     * @return The recency confidence heuristic
     */
    private double calculateRecencyConfidenceHeuristic(@NotNull Set<CompetencyExerciseMasteryCalculationDTO> participantScores) {
        if (participantScores.size() < 3) {
            return 0;
        }

        Instant earliestScoreDate = participantScores.stream().map(info -> info.participantScore().getLastModifiedDate()).min(Instant::compareTo).get();
        Instant latestScoreDate = participantScores.stream().map(info -> info.participantScore().getLastModifiedDate()).max(Instant::compareTo).get();

        double doubleWeightedScoreSum = participantScores.stream()
                .mapToDouble(info -> info.participantScore().getLastScore() * toRelativeTime(earliestScoreDate, latestScoreDate, info.participantScore().getLastModifiedDate()))
                .sum();
        double weightSum = participantScores.stream().mapToDouble(info -> toRelativeTime(earliestScoreDate, latestScoreDate, info.participantScore().getLastModifiedDate())).sum();
        double weightedAverageScore = doubleWeightedScoreSum / weightSum;

        double averageScore = participantScores.stream().mapToDouble(info -> info.participantScore().getLastScore()).average().orElse(0.0);

        double recencyConfidence = weightedAverageScore - averageScore;

        return Math.clamp(recencyConfidence, -0.25, 0.25);
    }

    /**
     * Calculate the difficulty confidence heuristic for the given user in a competency based on the exercises linked to the competency.
     * If the proportion of achieved points in hard exercises is higher than the proportion of hard points in the competency, the confidence should be higher and vice versa.
     * If the proportion of achieved points in easy exercises is higher than the proportion of easy points in the competency, the confidence should be lower and vice versa.
     *
     * @param participantScores the participant scores for the exercises linked to the competency
     * @param exerciseInfos     The information about the exercises linked to the competency
     * @return The difficulty confidence heuristic
     */
    private double calculateDifficultyConfidenceHeuristic(@NotNull Set<CompetencyExerciseMasteryCalculationDTO> participantScores,
            @NotNull Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos) {
        if (participantScores.isEmpty()) {
            return 0;
        }

        double achievedPoints = participantScores.stream().map(CompetencyExerciseMasteryCalculationDTO::participantScore).mapToDouble(ParticipantScore::getLastPoints).sum();
        double pointsInCompetency = exerciseInfos.stream().mapToDouble(exerciseInfo -> exerciseInfo.exercise().getMaxPoints()).sum();

        if (achievedPoints == 0 || pointsInCompetency == 0) {
            return 0;
        }

        double easyConfidence = calculateDifficultyConfidenceHeuristicForDifficulty(participantScores, exerciseInfos, achievedPoints, pointsInCompetency, DifficultyLevel.EASY);
        double hardConfidence = calculateDifficultyConfidenceHeuristicForDifficulty(participantScores, exerciseInfos, achievedPoints, pointsInCompetency, DifficultyLevel.HARD);

        double difficultyConfidence = hardConfidence - easyConfidence;

        return Math.clamp(difficultyConfidence, -0.25, 0.25);
    }

    /**
     * Calculate the difficulty confidence heuristic for the given user and difficulty in a competency based on the exercises linked to the competency.
     * If the proportion of achieved points in the given difficulty is higher than the proportion of points in the competency, the confidence should be higher.
     *
     * @param participantScores  the participant scores for the exercises linked to the competency
     * @param exerciseInfos      the information about the exercises linked to the competency
     * @param achievedPoints     the total points achieved by the user in the competency
     * @param pointsInCompetency the total points in the competency
     * @param difficultyLevel    the difficulty level to calculate the confidence for
     * @return the difficulty confidence heuristic for the given difficulty
     */
    private double calculateDifficultyConfidenceHeuristicForDifficulty(@NotNull Set<CompetencyExerciseMasteryCalculationDTO> participantScores,
            @NotNull Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos, double achievedPoints, double pointsInCompetency, DifficultyLevel difficultyLevel) {

        double achievedPointsInDifficulty = participantScores.stream().filter(exerciseInfo -> exerciseInfo.exercise().getDifficulty() == difficultyLevel)
                .mapToDouble(exerciseInfo -> exerciseInfo.participantScore().getLastPoints()).sum();
        double pointsInCompetencyInDifficulty = exerciseInfos.stream().filter(exerciseInfo -> exerciseInfo.exercise().getDifficulty() == difficultyLevel)
                .mapToDouble(exerciseInfo -> exerciseInfo.exercise().getMaxPoints()).sum();

        double proportionOfAchievedPointsInDifficulty = achievedPointsInDifficulty / achievedPoints;
        double proportionOfPointsInCompetencyInDifficulty = pointsInCompetencyInDifficulty / pointsInCompetency;

        return proportionOfAchievedPointsInDifficulty - proportionOfPointsInCompetencyInDifficulty;
    }

    /**
     * Calculate the quick solve confidence heuristic for the given user in a competency based on the exercises linked to the competency.
     * If the user has solved a high proportion of programming exercises with a high score in a short amount of time, the confidence should be higher.
     *
     * @param participantScores the participant scores for the exercises linked to the competency
     * @return The quick solve confidence heuristic
     */
    private double calculateQuickSolveConfidenceHeuristic(@NotNull Set<CompetencyExerciseMasteryCalculationDTO> participantScores) {
        Set<CompetencyExerciseMasteryCalculationDTO> programmingParticipationScores = participantScores.stream()
                .filter(info -> info.exercise().getExerciseType() == ExerciseType.PROGRAMMING).collect(Collectors.toSet());

        if (programmingParticipationScores.isEmpty()) {
            return 0;
        }

        double numberOfQuicklySolvedProgrammingExercises = programmingParticipationScores.stream()
                .filter(info -> info.participantScore().getLastScore() >= MIN_SCORE_GREEN && info.submissionCount() <= 3).count();

        double quickSolveConfidence = numberOfQuicklySolvedProgrammingExercises / programmingParticipationScores.size();

        return Math.clamp(quickSolveConfidence, -0.25, 0.25);
    }

    private void setConfidenceReason(CompetencyProgress competencyProgress, double recencyConfidence, double difficultyConfidence, double quickSolveConfidence) {
        if (competencyProgress.getConfidence() < 0.95) {
            double minConfidenceHeuristic = Math.min(recencyConfidence, difficultyConfidence);
            if (recencyConfidence == minConfidenceHeuristic) {
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.RECENT_SCORES_LOWER);
            }
            else {
                // quickSolveConfidence cannot be negative therefore we don't check it here
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.MORE_EASY_POINTS);
            }
        }
        else if (competencyProgress.getConfidence() > 1.05) {
            double maxConfidenceHeuristic = Math.max(recencyConfidence, Math.max(difficultyConfidence, quickSolveConfidence));
            if (recencyConfidence == maxConfidenceHeuristic) {
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.RECENT_SCORES_HIGHER);
            }
            else if (difficultyConfidence == maxConfidenceHeuristic) {
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.MORE_HARD_POINTS);
            }
            else {
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.QUICKLY_SOLVED_EXERCISES);
            }
        }
        else {
            competencyProgress.setConfidenceReason(null);
        }
    }

    /**
     * Calculates a user's mastery level for competency given the progress.
     *
     * @param competencyProgress The user's progress
     * @return The mastery level
     */
    public static double getMastery(@NotNull CompetencyProgress competencyProgress) {
        return Math.clamp(competencyProgress.getProgress() * competencyProgress.getConfidence(), 0, 100);
    }

    /**
     * Calculates a user's mastery progress scaled to the mastery threshold of the corresponding competency.
     *
     * @param competencyProgress The user's progress
     * @return The mastery level in percent
     */
    public static double getMasteryProgress(@NotNull CompetencyProgress competencyProgress) {
        final double mastery = getMastery(competencyProgress);
        return mastery / competencyProgress.getCompetency().getMasteryThreshold();
    }

    /**
     * Checks if the user associated to this {@code CompetencyProgress} has mastered the associated {@code Competency}.
     *
     * @param competencyProgress The user's progress
     * @return True if the user mastered the competency, false otherwise
     */
    public static boolean isMastered(@NotNull CompetencyProgress competencyProgress) {
        final double mastery = getMastery(competencyProgress);
        return mastery >= competencyProgress.getCompetency().getMasteryThreshold();
    }

    /**
     * Checks if the competency can be mastered without completing any exercises.
     *
     * @param competency the competency to check
     * @return true if the competency can be mastered without completing any exercises, false otherwise
     */
    public static boolean canBeMasteredWithoutExercises(@NotNull Competency competency) {
        double numberOfLectureUnits = competency.getLectureUnits().size();
        double numberOfLearningObjects = numberOfLectureUnits + competency.getExercises().size();
        double achievableProgressScore = numberOfLectureUnits / numberOfLearningObjects * 100;
        // Without exercises, the confidence score is 1 and the mastery is equal to the progress
        return achievableProgressScore >= competency.getMasteryThreshold();
    }

    /**
     * Deletes all progress for the given competency.
     *
     * @param competencyId The id of the competency for which to delete the progress
     */
    public void deleteProgressForCompetency(long competencyId) {
        competencyProgressRepository.deleteAllByCompetencyId(competencyId);
    }

    /**
     * Gets the progress for the whole course.
     *
     * @param competency The competency for which to get the progress
     * @param course     The course for which to get the progress
     * @return The progress for the course
     */
    public CourseCompetencyProgressDTO getCompetencyCourseProgress(@NotNull Competency competency, @NotNull Course course) {
        var numberOfStudents = competencyProgressRepository.countByCompetency(competency.getId());
        var numberOfMasteredStudents = competencyProgressRepository.countByCompetencyAndMastered(competency.getId(), competency.getMasteryThreshold());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(participantScoreService.getAverageOfAverageScores(competency.getExercises()), course);
        return new CourseCompetencyProgressDTO(competency.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore);
    }
}
