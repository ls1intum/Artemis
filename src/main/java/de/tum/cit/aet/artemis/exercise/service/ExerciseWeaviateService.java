package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.entitySchemas.ProgrammingExerciseSchema;
import de.tum.cit.aet.artemis.core.service.weaviate.WeaviateService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import io.weaviate.client6.v1.api.collections.query.Filter;

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
            insertExerciseIntoWeaviate(exercise);
            log.debug("Successfully inserted exercise {} '{}' into Weaviate", exercise.getId(), exercise.getTitle());
        }
        catch (Exception e) {
            log.error("Failed to insert exercise {} into Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Updates exercise metadata in Weaviate by deleting and re-inserting.
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
            deleteExerciseFromWeaviate(exercise.getId());
            insertExerciseIntoWeaviate(exercise);
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
            deleteExerciseFromWeaviate(exerciseId);
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

    /**
     * Inserts exercise data into the Weaviate collection.
     *
     * @param exercise the exercise to insert
     * @throws Exception if the insertion fails
     */
    private void insertExerciseIntoWeaviate(Exercise exercise) throws Exception {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var collection = weaviateService.get().getCollection(ProgrammingExerciseSchema.COLLECTION_NAME);

        Map<String, Object> properties = new HashMap<>();
        properties.put(ProgrammingExerciseSchema.Properties.EXERCISE_ID, exercise.getId());
        properties.put(ProgrammingExerciseSchema.Properties.COURSE_ID, course.getId());
        properties.put(ProgrammingExerciseSchema.Properties.TITLE, exercise.getTitle());
        properties.put(ProgrammingExerciseSchema.Properties.EXERCISE_TYPE, exercise.getType());
        properties.put(ProgrammingExerciseSchema.Properties.MAX_POINTS, exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0);
        properties.put(ProgrammingExerciseSchema.Properties.BASE_URL, serverUrl);

        // Add optional fields only if they are not null
        if (course.getTitle() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.COURSE_NAME, course.getTitle());
        }
        if (exercise.getShortName() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.SHORT_NAME, exercise.getShortName());
        }
        if (exercise.getProblemStatement() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.PROBLEM_STATEMENT, exercise.getProblemStatement());
        }
        if (exercise.getReleaseDate() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.RELEASE_DATE, formatDate(exercise.getReleaseDate()));
        }
        if (exercise.getStartDate() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.START_DATE, formatDate(exercise.getStartDate()));
        }
        if (exercise.getDueDate() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.DUE_DATE, formatDate(exercise.getDueDate()));
        }
        if (exercise instanceof ProgrammingExercise programmingExercise && programmingExercise.getProgrammingLanguage() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.PROGRAMMING_LANGUAGE, programmingExercise.getProgrammingLanguage().name());
        }
        if (exercise.getDifficulty() != null) {
            properties.put(ProgrammingExerciseSchema.Properties.DIFFICULTY, exercise.getDifficulty().name());
        }

        collection.data.insert(properties);
    }

    /**
     * Deletes all exercise entries for the given exercise ID from the Weaviate collection.
     *
     * @param exerciseId the exercise ID
     */
    private void deleteExerciseFromWeaviate(long exerciseId) {
        var collection = weaviateService.get().getCollection(ProgrammingExerciseSchema.COLLECTION_NAME);
        var deleteResult = collection.data.deleteMany(Filter.property(ProgrammingExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId));
        log.debug("Deleted {} exercise entries for exercise ID {}", deleteResult.successful(), exerciseId);
    }

    /**
     * Formats a ZonedDateTime to RFC3339 format required by Weaviate.
     *
     * @param dateTime the date time to format
     * @return the formatted date string
     */
    private String formatDate(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
