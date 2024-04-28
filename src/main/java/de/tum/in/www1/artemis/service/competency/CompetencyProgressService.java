package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.HashSet;
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
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.Participant;
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

    private final ParticipantScoreService participantScoreService;

    private final LearningObjectService learningObjectService;

    public CompetencyProgressService(CompetencyRepository competencyRepository, CompetencyProgressRepository competencyProgressRepository, ExerciseRepository exerciseRepository,
            LectureUnitRepository lectureUnitRepository, UserRepository userRepository, LearningPathService learningPathService, ParticipantScoreService participantScoreService,
            LearningObjectService learningObjectService) {
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.userRepository = userRepository;
        this.learningPathService = learningPathService;
        this.participantScoreService = participantScoreService;
        this.learningObjectService = learningObjectService;
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
        if (learningObject instanceof Exercise exercise) {
            course = exercise.getCourseViaExerciseGroupOrCourseMember();
        }
        else if (learningObject instanceof LectureUnit lectureUnit) {
            course = lectureUnit.getLecture().getCourse();
        }
        else {
            throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
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
            if (learningObject instanceof Exercise exercise) {
                competencies = exerciseRepository.findWithCompetenciesById(exercise.getId()).map(Exercise::getCompetencies).orElse(null);
            }
            else if (learningObject instanceof LectureUnit lectureUnit) {
                competencies = lectureUnitRepository.findWithCompetenciesById(lectureUnit.getId()).map(LectureUnit::getCompetencies).orElse(null);
            }
            else {
                throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
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
     * Updates the progress value (and confidence score) of the given competency and user, then returns it
     *
     * @param competencyId The id of the competency to update the progress for
     * @param user         The user for which the progress should be updated
     * @return The updated competency progress, which is also persisted to the database
     */
    public CompetencyProgress updateCompetencyProgress(Long competencyId, User user) {
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsAndCompletions(competencyId).orElse(null);

        if (user == null || competency == null) {
            log.debug("User or competency no longer exist, skipping.");
            return null;
        }

        var competencyProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId());

        if (competencyProgress.isPresent()) {
            var lastModified = competencyProgress.get().getLastModifiedDate();
            if (lastModified != null && lastModified.isAfter(Instant.now().minusSeconds(1))) {
                log.debug("Competency progress has been updated very recently, skipping.");
                return competencyProgress.get();
            }
        }

        var studentProgress = competencyProgress.orElse(new CompetencyProgress());
        Set<LearningObject> learningObjects = new HashSet<>();

        Set<LectureUnit> allLectureUnits = competency.getLectureUnits().stream().filter(LectureUnit::isVisibleToStudents).collect(Collectors.toSet());

        Set<LectureUnit> lectureUnits = allLectureUnits.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        Set<Exercise> exercises = competency.getExercises().stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet());

        learningObjects.addAll(lectureUnits);
        learningObjects.addAll(exercises);

        var progress = RoundingUtil.roundScoreSpecifiedByCourseSettings(calculateProgress(learningObjects, user), competency.getCourse());
        var confidence = RoundingUtil.roundScoreSpecifiedByCourseSettings(calculateConfidence(exercises, user), competency.getCourse());

        if (exercises.isEmpty()) {
            // If the competency has no exercises, the confidence score equals the progress
            confidence = progress;
        }

        studentProgress.setCompetency(competency);
        studentProgress.setUser(user);
        studentProgress.setProgress(progress);
        studentProgress.setConfidence(confidence);

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
     * Calculate the progress value for the given user in a competency.
     *
     * @param learningObjects A list of all learning objects linked to a specific competency
     * @param user            The user for which the progress should be calculated
     * @return The percentage of completed learning objects by the user
     */
    private double calculateProgress(@NotNull Set<LearningObject> learningObjects, @NotNull User user) {
        return learningObjects.stream().map(learningObject -> learningObjectService.isCompletedByUser(learningObject, user)).mapToInt(completed -> completed ? 100 : 0).average()
                .orElse(0.);
    }

    /**
     * Calculate the confidence score for the given user in a competency.
     *
     * @param exercises A list of all exercises linked to a specific competency
     * @param user      The user for which the confidence score should be calculated
     * @return The average score of the user in all exercises linked to the competency
     */
    private double calculateConfidence(@NotNull Set<Exercise> exercises, @NotNull User user) {
        return participantScoreService.getStudentAndTeamParticipationScoresAsDoubleStream(user, exercises).summaryStatistics().getAverage();
    }

    /**
     * Calculates a user's mastery level for competency given the progress.
     *
     * @param competencyProgress The user's progress
     * @return The mastery level
     */
    public static double getMastery(@NotNull CompetencyProgress competencyProgress) {
        // mastery as a weighted function of progress and confidence (consistent with client)
        final double weight = 2.0 / 3.0;
        return (1 - weight) * competencyProgress.getProgress() + weight * competencyProgress.getConfidence();
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
        final var lectureUnits = competency.getLectureUnits().size();
        final var numberOfLearningObjects = lectureUnits + competency.getExercises().size();
        final var achievableMasteryScore = ((double) lectureUnits) / (3 * numberOfLearningObjects) * 100;
        return achievableMasteryScore >= competency.getMasteryThreshold();
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
        var numberOfMasteredStudents = competencyProgressRepository.countByCompetencyAndProgressAndConfidenceGreaterThanEqual(competency.getId(), 100.0,
                competency.getMasteryThreshold());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(competencyProgressRepository.findAverageConfidenceByCompetencyId(competency.getId()).orElse(0.0),
                course);
        return new CourseCompetencyProgressDTO(competency.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore);
    }
}
