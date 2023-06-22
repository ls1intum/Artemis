package de.tum.in.www1.artemis.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.util.RoundingUtil;

/**
 * Service for calculating the progress of a student in a competency.
 */
@Service
public class CompetencyProgressService {

    private final Logger logger = LoggerFactory.getLogger(CompetencyProgressService.class);

    private final CompetencyRepository competencyRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    public CompetencyProgressService(CompetencyRepository competencyRepository, CompetencyProgressRepository competencyProgressRepository,
            StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ExerciseRepository exerciseRepository,
            LectureUnitRepository lectureUnitRepository, UserRepository userRepository) {
        this.competencyRepository = competencyRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.userRepository = userRepository;
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
        updateProgressByLearningObject(learningObject, new HashSet<>(userRepository.getStudents(course)));
    }

    /**
     * Asynchronously update the existing progress for a specific competency
     *
     * @param competency The competency for which to update all existing student progress
     */
    @Async
    public void updateProgressByCompetencyAsync(Competency competency) {
        SecurityUtils.setAuthorizationObject(); // required for async
        competencyProgressRepository.findAllByCompetencyId(competency.getId()).stream().map(CompetencyProgress::getUser).forEach(user -> {
            updateCompetencyProgress(competency.getId(), user);
        });
    }

    /**
     * Update the existing progress for a specific user in a course
     *
     * @param user   The user for whom to update the existing competency progress
     * @param course The course for which to fetch the competencies from
     * @return All competencies of the course with the updated progress for the user
     */
    public Set<Competency> getCompetenciesAndUpdateProgressByUserInCourse(User user, Course course) {
        var competencies = competencyRepository.findAllForCourse(course.getId());
        competencies.forEach(competency -> {
            var updatedProgress = updateCompetencyProgress(competency.getId(), user);
            if (updatedProgress != null) {
                competency.setUserProgress(Set.of(updatedProgress));
            }
        });
        return competencies;
    }

    /**
     * Update the progress for all competencies linked to the given learning object
     *
     * @param learningObject The learning object for which to fetch the competencies
     * @param users          A list of users for which to update the progress
     */
    public void updateProgressByLearningObject(LearningObject learningObject, @NotNull Set<User> users) {
        logger.debug("Updating competency progress for {} users.", users.size());
        try {
            Set<Competency> competencies;
            if (learningObject instanceof Exercise exercise) {
                competencies = exerciseRepository.findByIdWithCompetencies(exercise.getId()).map(Exercise::getCompetencies).orElse(null);
            }
            else if (learningObject instanceof LectureUnit lectureUnit) {
                competencies = lectureUnitRepository.findByIdWithCompetencies(lectureUnit.getId()).map(LectureUnit::getCompetencies).orElse(null);
            }
            else {
                throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
            }

            if (competencies == null) {
                // Competencies couldn't be loaded, the exercise/lecture unit might have already been deleted
                logger.debug("Competencies could not be fetched, skipping.");
                return;
            }

            users.forEach(user -> {
                competencies.forEach(competency -> {
                    updateCompetencyProgress(competency.getId(), user);
                });
            });
        }
        catch (Exception e) {
            logger.error("Exception while updating progress for competency", e);
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
            logger.debug("User or competency no longer exist, skipping.");
            return null;
        }

        var competencyProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId());

        if (competencyProgress.isPresent()) {
            var lastModified = competencyProgress.get().getLastModifiedDate();
            if (lastModified != null && lastModified.isAfter(Instant.now().minusSeconds(1))) {
                logger.debug("Competency progress has been updated very recently, skipping.");
                return competencyProgress.get();
            }
        }

        var studentProgress = competencyProgress.orElse(new CompetencyProgress());
        List<LearningObject> learningObjects = new ArrayList<>();

        List<LectureUnit> allLectureUnits = competency.getLectureUnits().stream().filter(LectureUnit::isVisibleToStudents).toList();

        List<LectureUnit> lectureUnits = allLectureUnits.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).toList();
        List<Exercise> exercises = competency.getExercises().stream().filter(Exercise::isVisibleToStudents).toList();

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

        logger.debug("Updated progress for user {} in competency {} to {} / {}.", user.getLogin(), competency.getId(), studentProgress.getProgress(),
                studentProgress.getConfidence());
        return studentProgress;
    }

    /**
     * Calculate the progress value for the given user in a competency.
     *
     * @param learningObjects A list of all learning objects linked to a specific competency
     * @param user            The user for which the progress should be calculated
     * @return The percentage of completed learning objects by the user
     */
    private double calculateProgress(@NotNull List<LearningObject> learningObjects, @NotNull User user) {
        var completions = learningObjects.stream().map(learningObject -> hasUserCompleted(user, learningObject)).toList();
        return completions.stream().mapToInt(completed -> completed ? 100 : 0).summaryStatistics().getAverage();
    }

    /**
     * Calculate the confidence score for the given user in a competency.
     *
     * @param exercises A list of all exercises linked to a specific competency
     * @param user      The user for which the confidence score should be calculated
     * @return The average score of the user in all exercises linked to the competency
     */
    private double calculateConfidence(@NotNull List<Exercise> exercises, @NotNull User user) {
        var studentScores = studentScoreRepository.findAllByExercisesAndUser(exercises, user);
        var teamScores = teamScoreRepository.findAllByExercisesAndUser(exercises, user);
        return Stream.concat(studentScores.stream(), teamScores.stream()).map(ParticipantScore::getLastScore).mapToDouble(score -> score).summaryStatistics().getAverage();
    }

    /**
     * Checks if the user has completed the learning object.
     *
     * @param user           The user for which to check the completion status
     * @param learningObject The lecture unit or exercise
     * @return True if the user completed the lecture unit or has at least one result for the exercise, false otherwise
     */
    private boolean hasUserCompleted(@NotNull User user, LearningObject learningObject) {
        if (learningObject instanceof LectureUnit lectureUnit) {
            return lectureUnit.getCompletedUsers().stream().map(LectureUnitCompletion::getUser).anyMatch(user1 -> user1.getId().equals(user.getId()));
        }
        else if (learningObject instanceof Exercise exercise) {
            var studentScores = studentScoreRepository.findAllByExercisesAndUser(List.of(exercise), user);
            var teamScores = teamScoreRepository.findAllByExercisesAndUser(List.of(exercise), user);
            return Stream.concat(studentScores.stream(), teamScores.stream()).findAny().isPresent();
        }
        throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
    }

}
