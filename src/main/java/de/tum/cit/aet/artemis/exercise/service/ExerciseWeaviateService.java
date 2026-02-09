package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.weaviate.WeaviateService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service for synchronizing exercise metadata with Weaviate vector database.
 * This service handles insert, update, and delete operations for exercises in Weaviate.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseWeaviateService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseWeaviateService.class);

    private final Optional<WeaviateService> weaviateService;

    @Value("${server.url:http://localhost:9000}")
    private String serverUrl;

    public ExerciseWeaviateService(Optional<WeaviateService> weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * Inserts exercise metadata into Weaviate.
     * If Weaviate is not enabled, this method does nothing.
     *
     * @param exercise the exercise to insert
     */
    public void insertExercise(Exercise exercise) {
        if (weaviateService.isEmpty()) {
            log.trace("Weaviate is not enabled, skipping exercise insertion for exercise {}", exercise.getId());
            return;
        }

        if (exercise.getId() == null) {
            log.warn("Cannot insert exercise without an ID");
            return;
        }

        try {
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();
            String programmingLanguage = null;
            if (exercise instanceof ProgrammingExercise programmingExercise && programmingExercise.getProgrammingLanguage() != null) {
                programmingLanguage = programmingExercise.getProgrammingLanguage().name();
            }

            weaviateService.get().insertProgrammingExercise(exercise.getId(), course.getId(), course.getTitle(), exercise.getTitle(), exercise.getShortName(),
                    exercise.getProblemStatement(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getType(), programmingLanguage,
                    exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null, exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0, serverUrl);

            log.debug("Successfully inserted exercise {} '{}' into Weaviate", exercise.getId(), exercise.getTitle());
        }
        catch (Exception e) {
            log.error("Failed to insert exercise {} into Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Updates exercise metadata in Weaviate.
     * If Weaviate is not enabled, this method does nothing.
     *
     * @param exercise the exercise to update
     */
    public void updateExercise(Exercise exercise) {
        if (weaviateService.isEmpty()) {
            log.trace("Weaviate is not enabled, skipping exercise update for exercise {}", exercise.getId());
            return;
        }

        if (exercise.getId() == null) {
            log.warn("Cannot update exercise without an ID");
            return;
        }

        try {
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();
            String programmingLanguage = null;
            if (exercise instanceof ProgrammingExercise programmingExercise && programmingExercise.getProgrammingLanguage() != null) {
                programmingLanguage = programmingExercise.getProgrammingLanguage().name();
            }

            weaviateService.get().updateProgrammingExercise(exercise.getId(), course.getId(), course.getTitle(), exercise.getTitle(), exercise.getShortName(),
                    exercise.getProblemStatement(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getType(), programmingLanguage,
                    exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null, exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0, serverUrl);

            log.debug("Successfully updated exercise {} '{}' in Weaviate", exercise.getId(), exercise.getTitle());
        }
        catch (Exception e) {
            log.error("Failed to update exercise {} in Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Deletes exercise metadata from Weaviate.
     * If Weaviate is not enabled, this method does nothing.
     *
     * @param exerciseId the ID of the exercise to delete
     */
    public void deleteExercise(long exerciseId) {
        if (weaviateService.isEmpty()) {
            log.trace("Weaviate is not enabled, skipping exercise deletion for exercise {}", exerciseId);
            return;
        }

        try {
            weaviateService.get().deleteProgrammingExercise(exerciseId);
            log.debug("Successfully deleted exercise {} from Weaviate", exerciseId);
        }
        catch (Exception e) {
            log.error("Failed to delete exercise {} from Weaviate: {}", exerciseId, e.getMessage(), e);
        }
    }

    /**
     * Checks if Weaviate integration is available.
     *
     * @return true if Weaviate is enabled and available
     */
    public boolean isWeaviateAvailable() {
        return weaviateService.isPresent();
    }

}
