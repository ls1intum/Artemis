package de.tum.cit.aet.artemis.atlas.service.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.MIN_SCORE_GREEN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.TimeUtil.toRelativeTime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.domain.CompetencyProgressConfidenceReason;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLearningObjectLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyExerciseMasteryCalculationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseCompetencyProgressDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;

/**
 * Service for calculating the progress of a student in a competency.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyProgressService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyProgressService.class);

    private final CompetencyProgressRepository competencyProgressRepository;

    private final LearningPathService learningPathService;

    private final ParticipantScoreService participantScoreService;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final UserRepository userRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private static final int MIN_EXERCISES_RECENCY_CONFIDENCE = 3;

    private static final int MAX_SUBMISSIONS_FOR_QUICK_SOLVE_HEURISTIC = 3;

    private static final double DEFAULT_CONFIDENCE = 1.0;

    private static final double MAX_CONFIDENCE_HEURISTIC = 0.25;

    private static final double CONFIDENCE_REASON_DEADZONE = 0.05;

    public CompetencyProgressService(CompetencyProgressRepository competencyProgressRepository, UserRepository userRepository, LearningPathService learningPathService,
            ParticipantScoreService participantScoreService, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyProgressRepository = competencyProgressRepository;
        this.learningPathService = learningPathService;
        this.participantScoreService = participantScoreService;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.userRepository = userRepository;
    }

    /**
     * Asynchronously update the progress for competencies linked to the given learning object (for the specified participant)
     *
     * @param learningObject The learning object for which to fetch the competencies
     * @param participant    The participant (user or team) for which to update the progress
     */
    @Async
    public void updateProgressByLearningObjectForParticipantAsync(LearningObject learningObject, @NotNull Participant participant) {
        SecurityUtils.setAuthorizationObject(); // Required for async
        updateProgressByLearningObjectSync(learningObject, participant.getParticipants());
    }

    /**
     * Asynchronously update the progress for the competencies linked to the given learning object (for all students in the course)
     *
     * @param learningObject The learning object for which to fetch the competencies
     */
    @Async
    public void updateProgressByLearningObjectAsync(LearningObject learningObject) {
        SecurityUtils.setAuthorizationObject(); // Required for async
        Set<Long> competencyIds = courseCompetencyRepository.findAllIdsByLearningObject(learningObject);

        for (long competencyId : competencyIds) {
            Set<User> users = competencyProgressRepository.findAllByCompetencyId(competencyId).stream().map(CompetencyProgress::getUser).collect(Collectors.toSet());
            log.debug("Updating competency progress for {} users.", users.size());

            users.forEach(user -> updateCompetencyProgress(competencyId, user));
        }
    }

    /**
     * Asynchronously update the existing progress for a specific competency
     *
     * @param competency The competency for which to update all existing student progress
     */
    @Async
    public void updateProgressByCompetencyAsync(CourseCompetency competency) {
        SecurityUtils.setAuthorizationObject(); // Required for async
        List<CompetencyProgress> existingProgress = competencyProgressRepository.findAllByCompetencyId(competency.getId());
        log.debug("Updating competency progress for {} users.", existingProgress.size());
        existingProgress.stream().map(CompetencyProgress::getUser).forEach(user -> updateCompetencyProgress(competency.getId(), user));
    }

    /**
     * Asynchronously update the progress of all users in the course for a specific competency
     *
     * @param competency The competency for which to update all existing student progress
     */
    @Async
    public void updateProgressByCompetencyAndUsersInCourseAsync(CourseCompetency competency) {
        SecurityUtils.setAuthorizationObject(); // Required for async
        Set<User> users = userRepository.getUsersInCourse(competency.getCourse());
        log.debug("Updating competency progress for {} users.", users.size());
        users.forEach(user -> updateCompetencyProgress(competency.getId(), user));
    }

    /**
     * Asynchronously update the existing progress for all changed competencies linked to the given learning object
     * If new competencies are added, the progress is updated for all users in the course, otherwise only the existing progresses are updated.
     *
     * @param originalLearningObject The original learning object before the update
     * @param updatedLearningObject  The updated learning object after the update (empty if the learning object was deleted)
     */
    @Async
    public void updateProgressForUpdatedLearningObjectAsync(LearningObject originalLearningObject, Optional<LearningObject> updatedLearningObject) {
        SecurityUtils.setAuthorizationObject(); // Required for async

        Set<Long> originalCompetencyIds = originalLearningObject.getCompetencyLinks().stream().map(CompetencyLearningObjectLink::getCompetency).map(CourseCompetency::getId)
                .collect(Collectors.toSet());
        Set<CourseCompetency> updatedCompetencies = updatedLearningObject
                .map(learningObject -> learningObject.getCompetencyLinks().stream().map(CompetencyLearningObjectLink::getCompetency).collect(Collectors.toSet())).orElse(Set.of());
        Set<Long> updatedCompetencyIds = updatedCompetencies.stream().map(CourseCompetency::getId).collect(Collectors.toSet());

        Set<Long> removedCompetencyIds = originalCompetencyIds.stream().filter(id -> !updatedCompetencyIds.contains(id)).collect(Collectors.toSet());
        Set<Long> addedCompetencyIds = updatedCompetencyIds.stream().filter(id -> !originalCompetencyIds.contains(id)).collect(Collectors.toSet());

        updateProgressByCompetencyIds(removedCompetencyIds);
        if (!addedCompetencyIds.isEmpty()) {
            updateProgressByCompetencyIdsAndLearningObject(addedCompetencyIds, originalLearningObject);
        }
    }

    private void updateProgressByCompetencyIds(Set<Long> competencyIds) {
        for (long competencyId : competencyIds) {
            List<CompetencyProgress> existingProgress = competencyProgressRepository.findAllByCompetencyId(competencyId);
            log.debug("Updating competency progress for {} users.", existingProgress.size());
            existingProgress.stream().map(CompetencyProgress::getUser).forEach(user -> updateCompetencyProgress(competencyId, user));
        }
    }

    private void updateProgressByCompetencyIdsAndLearningObject(Set<Long> competencyIds, LearningObject learningObject) {
        for (long competencyId : competencyIds) {
            Set<User> existingCompetencyUsers = competencyProgressRepository.findAllByCompetencyId(competencyId).stream().map(CompetencyProgress::getUser)
                    .collect(Collectors.toSet());
            Set<User> existingLearningObjectUsers = switch (learningObject) {
                case Exercise exercise -> participantScoreService.getAllParticipatedUsersInExercise(exercise);
                case LectureUnit lectureUnit -> lectureUnitCompletionRepository.findCompletedUsersForLectureUnit(lectureUnit);
                default -> throw new IllegalStateException("Unexpected value: " + learningObject);
            };
            existingCompetencyUsers.addAll(existingLearningObjectUsers);
            log.debug("Updating competency progress for {} users.", existingCompetencyUsers.size());
            existingCompetencyUsers.forEach(user -> updateCompetencyProgress(competencyId, user));
        }
    }

    /**
     * Update the progress for all competencies linked to the given learning object synchronously
     *
     * @param learningObject The learning object for which to fetch the competencies
     * @param users          The users for which to update the progress
     */
    public void updateProgressByLearningObjectSync(LearningObject learningObject, Set<User> users) {
        Set<Long> competencyIds = courseCompetencyRepository.findAllIdsByLearningObject(learningObject);

        for (long competencyId : competencyIds) {
            log.debug("Updating competency progress synchronously for {} users.", users.size());

            users.forEach(user -> updateCompetencyProgress(competencyId, user));
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
        Optional<CourseCompetency> optionalCompetency = courseCompetencyRepository.findByIdWithLectureUnits(competencyId);

        if (user == null || optionalCompetency.isEmpty()) {
            log.debug("User or competency no longer exist, skipping.");
            return null;
        }

        CourseCompetency competency = optionalCompetency.get();
        Set<LectureUnit> lectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit)
                .filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos = courseCompetencyRepository.findAllExerciseInfoByCompetencyId(competencyId, user);
        int numberOfCompletedLectureUnits = lectureUnitCompletionRepository
                .countByLectureUnitIdsAndUserId(lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet()), user.getId());

        var competencyProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId());

        if (competencyProgress.isPresent()) {
            var lastModified = competencyProgress.get().getLastModifiedDate();
            if (lastModified != null && lastModified.isAfter(Instant.now().minusSeconds(1))) {
                log.debug("Competency progress has been updated very recently, skipping.");
                return competencyProgress.get();
            }
        }

        var studentProgress = competencyProgress.orElse(new CompetencyProgress());

        calculateProgress(lectureUnits, exerciseInfos, numberOfCompletedLectureUnits, studentProgress);
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
     * @param lectureUnits                  The lecture units linked to the competency
     * @param exerciseInfos                 The information about the exercises linked to the competency
     * @param numberOfCompletedLectureUnits The number of lecture units completed by the user
     * @param competencyProgress            The progress entity to update
     */
    private void calculateProgress(Set<LectureUnit> lectureUnits, Set<CompetencyExerciseMasteryCalculationDTO> exerciseInfos, int numberOfCompletedLectureUnits,
            CompetencyProgress competencyProgress) {
        double numberOfLearningObjects = lectureUnits.size() + exerciseInfos.size();
        if (numberOfLearningObjects == 0) {
            // If nothing is linked to the competency, the competency is considered completed
            competencyProgress.setProgress(100.0);
        }

        double achievedPoints = exerciseInfos.stream().mapToDouble(info -> info.lastPoints() != null ? info.lastPoints() : 0).sum();
        double maxPoints = exerciseInfos.stream().mapToDouble(CompetencyExerciseMasteryCalculationDTO::maxPoints).sum();
        double exerciseProgress = maxPoints > 0 ? achievedPoints / maxPoints * 100 : 0;

        double lectureProgress = 100.0 * numberOfCompletedLectureUnits / lectureUnits.size();

        double weightedExerciseProgress = exerciseInfos.size() / numberOfLearningObjects * exerciseProgress;
        double weightedLectureProgress = lectureUnits.size() / numberOfLearningObjects * lectureProgress;

        double progress = weightedExerciseProgress + weightedLectureProgress;
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
        Set<CompetencyExerciseMasteryCalculationDTO> participantScoreInfos = exerciseInfos.stream()
                .filter(info -> info.lastScore() != null && info.lastPoints() != null && info.lastModifiedDate() != null).collect(Collectors.toSet());

        double recencyConfidenceHeuristic = calculateRecencyConfidenceHeuristic(participantScoreInfos);
        double difficultyConfidenceHeuristic = calculateDifficultyConfidenceHeuristic(participantScoreInfos, exerciseInfos);
        double quickSolveConfidenceHeuristic = calculateQuickSolveConfidenceHeuristic(participantScoreInfos);

        // Standard factor of 1 (no change to mastery compared to progress) plus the confidence heuristics
        double confidence = DEFAULT_CONFIDENCE + recencyConfidenceHeuristic + difficultyConfidenceHeuristic + quickSolveConfidenceHeuristic;

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
        if (participantScores.size() < MIN_EXERCISES_RECENCY_CONFIDENCE) {
            return 0;
        }

        Instant earliestScoreDate = participantScores.stream().map(CompetencyExerciseMasteryCalculationDTO::lastModifiedDate).min(Instant::compareTo).get();
        Instant latestScoreDate = participantScores.stream().map(CompetencyExerciseMasteryCalculationDTO::lastModifiedDate).max(Instant::compareTo).get();

        double doubleWeightedScoreSum = participantScores.stream()
                .mapToDouble(info -> info.lastScore() * toRelativeTime(earliestScoreDate, latestScoreDate, info.lastModifiedDate())).sum();
        double weightSum = participantScores.stream().mapToDouble(info -> toRelativeTime(earliestScoreDate, latestScoreDate, info.lastModifiedDate())).sum();
        double weightedAverageScore = doubleWeightedScoreSum / weightSum;

        double averageScore = participantScores.stream().mapToDouble(CompetencyExerciseMasteryCalculationDTO::lastScore).average().orElse(0.0);

        double recencyConfidence = weightedAverageScore - averageScore;

        return Math.clamp(recencyConfidence, -MAX_CONFIDENCE_HEURISTIC, MAX_CONFIDENCE_HEURISTIC);
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

        double achievedPoints = participantScores.stream().mapToDouble(CompetencyExerciseMasteryCalculationDTO::lastPoints).sum();
        double pointsInCompetency = exerciseInfos.stream().mapToDouble(CompetencyExerciseMasteryCalculationDTO::maxPoints).sum();

        if (achievedPoints == 0 || pointsInCompetency == 0) {
            return 0;
        }

        double easyConfidence = calculateDifficultyConfidenceHeuristicForDifficulty(participantScores, exerciseInfos, achievedPoints, pointsInCompetency, DifficultyLevel.EASY);
        double hardConfidence = calculateDifficultyConfidenceHeuristicForDifficulty(participantScores, exerciseInfos, achievedPoints, pointsInCompetency, DifficultyLevel.HARD);

        double difficultyConfidence = hardConfidence - easyConfidence;

        return Math.clamp(difficultyConfidence, -MAX_CONFIDENCE_HEURISTIC, MAX_CONFIDENCE_HEURISTIC);
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

        double achievedPointsInDifficulty = participantScores.stream().filter(info -> info.difficulty() == difficultyLevel)
                .mapToDouble(CompetencyExerciseMasteryCalculationDTO::lastPoints).sum();
        double pointsInCompetencyInDifficulty = exerciseInfos.stream().filter(info -> info.difficulty() == difficultyLevel)
                .mapToDouble(CompetencyExerciseMasteryCalculationDTO::maxPoints).sum();

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
                .filter(CompetencyExerciseMasteryCalculationDTO::isProgrammingExercise).collect(Collectors.toSet());

        if (programmingParticipationScores.isEmpty()) {
            return 0;
        }

        double numberOfQuicklySolvedProgrammingExercises = programmingParticipationScores.stream()
                .filter(info -> info.lastScore() >= MIN_SCORE_GREEN && info.submissionCount() <= MAX_SUBMISSIONS_FOR_QUICK_SOLVE_HEURISTIC).count();

        double quickSolveConfidence = numberOfQuicklySolvedProgrammingExercises / programmingParticipationScores.size();

        return Math.clamp(quickSolveConfidence, -MAX_CONFIDENCE_HEURISTIC, MAX_CONFIDENCE_HEURISTIC);
    }

    /**
     * Find most important heuristic that influences the confidence score and set the confidence reason accordingly.
     * If the confidence does not deviate significantly from 1, the reason is set to NO_REASON.
     *
     * @param competencyProgress   the progress entity add the confidence reason to
     * @param recencyConfidence    the recency confidence heuristic
     * @param difficultyConfidence the difficulty confidence heuristic
     * @param quickSolveConfidence the quick solve confidence heuristic
     */
    private void setConfidenceReason(CompetencyProgress competencyProgress, double recencyConfidence, double difficultyConfidence, double quickSolveConfidence) {
        if (competencyProgress.getConfidence() < DEFAULT_CONFIDENCE - CONFIDENCE_REASON_DEADZONE) {
            double minConfidenceHeuristic = Math.min(recencyConfidence, difficultyConfidence);
            if (recencyConfidence == minConfidenceHeuristic) {
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.RECENT_SCORES_LOWER);
            }
            else {
                // quickSolveConfidence cannot be negative therefore we don't check it here
                competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.MORE_EASY_POINTS);
            }
        }
        else if (competencyProgress.getConfidence() > DEFAULT_CONFIDENCE + CONFIDENCE_REASON_DEADZONE) {
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
            competencyProgress.setConfidenceReason(CompetencyProgressConfidenceReason.NO_REASON);
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
     * @return The progress to the mastery between 0 and 1
     */
    public static double getMasteryProgress(@NotNull CompetencyProgress competencyProgress) {
        final double mastery = getMastery(competencyProgress);
        return Math.clamp(mastery / competencyProgress.getCompetency().getMasteryThreshold(), 0, 1);
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
    public static boolean canBeMasteredWithoutExercises(@NotNull CourseCompetency competency) {
        double numberOfLectureUnits = competency.getLectureUnitLinks().size();
        double numberOfLearningObjects = numberOfLectureUnits + competency.getExerciseLinks().size();
        if (numberOfLearningObjects == 0) {
            return true;
        }

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
    public CourseCompetencyProgressDTO getCompetencyCourseProgress(@NotNull CourseCompetency competency, @NotNull Course course) {
        var numberOfStudents = competencyProgressRepository.countByCompetency(competency.getId());
        var numberOfMasteredStudents = competencyProgressRepository.countByCompetencyAndMastered(competency.getId(), competency.getMasteryThreshold());
        Set<Exercise> exercises = competency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise).collect(Collectors.toSet());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(participantScoreService.getAverageOfAverageScores(exercises), course);
        return new CourseCompetencyProgressDTO(competency.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore);
    }
}
