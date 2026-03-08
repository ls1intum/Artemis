package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.dto.ExerciseWeaviateDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Service for synchronizing exercise metadata with Weaviate vector database.
 * This service handles insert, update, and delete operations for exercises in Weaviate.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class ExerciseWeaviateService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseWeaviateService.class);

    private final WeaviateService weaviateService;

    public ExerciseWeaviateService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * Performs an upsert operation: queries for existing object and replaces it, or inserts if not found.
     *
     * @param exerciseWeaviateDTO the exercise data to upsert
     * @throws WeaviateException if the operation fails
     */
    private void upsertExerciseInWeaviate(ExerciseWeaviateDTO exerciseWeaviateDTO) throws WeaviateException {
        try {
            var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);

            var existingObjectQueryResult = collection.query
                    .fetchObjects(query -> query.filters(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exerciseWeaviateDTO.exerciseId())).limit(1));

            Map<String, Object> properties = buildExerciseProperties(exerciseWeaviateDTO);

            if (!existingObjectQueryResult.objects().isEmpty()) {
                // Object exists - use replace to update it
                var existingObject = existingObjectQueryResult.objects().getFirst();
                String uuid = existingObject.uuid();
                collection.data.replace(uuid, r -> r.properties(properties));
                log.debug("Replaced existing exercise {} with UUID {}", exerciseWeaviateDTO.exerciseId(), uuid);
            }
            else {
                // Object doesn't exist - insert new one
                collection.data.insert(properties);
                log.debug("Inserted new exercise {}", exerciseWeaviateDTO.exerciseId());
            }
        }
        catch (IOException e) {
            log.error("Failed to upsert exercise {} in Weaviate: {}", exerciseWeaviateDTO.exerciseId(), e.getMessage(), e);
            throw new WeaviateException("Failed to upsert exercise: " + exerciseWeaviateDTO.exerciseId(), e);
        }
    }

    /**
     * Builds the complete property map for an exercise.
     *
     * @param exerciseWeaviateDTO the exercise data
     * @return the property map ready for Weaviate
     */
    private Map<String, Object> buildExerciseProperties(ExerciseWeaviateDTO exerciseWeaviateDTO) {
        Map<String, Object> properties = new HashMap<>();

        properties.put(ExerciseSchema.Properties.EXERCISE_ID, exerciseWeaviateDTO.exerciseId());
        properties.put(ExerciseSchema.Properties.COURSE_ID, exerciseWeaviateDTO.courseId());
        properties.put(ExerciseSchema.Properties.TITLE, exerciseWeaviateDTO.exerciseTitle());
        properties.put(ExerciseSchema.Properties.EXERCISE_TYPE, exerciseWeaviateDTO.exerciseType());
        properties.put(ExerciseSchema.Properties.MAX_POINTS, exerciseWeaviateDTO.maxPoints());

        addSharedExerciseProperties(exerciseWeaviateDTO, properties);
        addExamProperties(exerciseWeaviateDTO, properties);
        addExerciseTypeSpecificProperties(exerciseWeaviateDTO, properties);

        return properties;
    }

    /**
     * Asynchronously upserts (inserts or updates) exercise metadata in Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     * Uses an upsert strategy: if the exercise already exists in Weaviate it will be updated,
     * otherwise it will be inserted. This makes the operation idempotent.
     * IMPORTANT: The exercise entity must have its course relationship eagerly loaded before calling this method,
     * otherwise a LazyInitializationException will be thrown.
     *
     * @param exercise the exercise to upsert (must have course and exam relationships loaded)
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    @Async
    public void upsertExerciseAsync(Exercise exercise) {
        SecurityUtils.setAuthorizationObject();

        if (exercise.getId() == null) {
            log.warn("Cannot upsert exercise without an ID");
            return;
        }

        try {
            // Extract data immediately to fail fast if relationships aren't loaded
            ExerciseWeaviateDTO data = ExerciseWeaviateDTO.fromExercise(exercise);
            upsertExerciseInWeaviate(data);
            log.debug("Successfully upserted exercise {} '{}' in Weaviate", data.exerciseId(), data.exerciseTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert exercise {} in Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Event listener that synchronizes exercise metadata to Weaviate when a version is created.
     * This method is automatically invoked when an {@link ExerciseVersionCreatedEvent} is published,
     * decoupling the versioning service from search indexing concerns.
     * <p>
     * Uses an upsert strategy (insert if new, update if exists) so no distinction is needed
     * between new and updated exercises.
     *
     * @param event the exercise version created event
     */
    @EventListener
    @Async
    public void onExerciseVersionCreated(ExerciseVersionCreatedEvent event) {
        upsertExerciseAsync(event.exercise());
    }

    /**
     * Asynchronously deletes exercise metadata from Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     *
     * @param exerciseId the ID of the exercise to delete
     */
    @Async
    public void deleteExerciseAsync(long exerciseId) {
        SecurityUtils.setAuthorizationObject();

        try {
            deleteExerciseFromWeaviate(exerciseId);
            log.debug("Successfully deleted exercise {} from Weaviate", exerciseId);
        }
        catch (Exception e) {
            log.error("Failed to delete exercise {} from Weaviate: {}", exerciseId, e.getMessage(), e);
        }
    }

    /**
     * Asynchronously updates Weaviate metadata for all exercises belonging to an exam.
     * Uses sequential upserts for now (in case performance becomes and issue we can optimize with batch operations later).
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     * IMPORTANT: The exam must have exercise groups, exercises, and their course relationships eagerly loaded,
     * otherwise a LazyInitializationException will be thrown.
     *
     * @param exam the exam whose exercises should be updated (must have exercise groups, exercises, and course relationships loaded)
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    @Async
    public void updateExamExercisesAsync(Exam exam) {
        SecurityUtils.setAuthorizationObject();

        if (exam == null || exam.getExerciseGroups() == null) {
            log.warn("Cannot update exam exercises in Weaviate: exam or exercise groups are null");
            return;
        }

        log.info("Updating {} exercise groups for exam {} in Weaviate", exam.getExerciseGroups().size(), exam.getId());

        // Step 1: Collect all exercises and convert to DTOs
        List<ExerciseWeaviateDTO> exerciseDTOs = new ArrayList<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            log.debug("Processing exercise group {} with {} exercises", exerciseGroup.getId(), exerciseGroup.getExercises().size());
            for (Exercise exercise : exerciseGroup.getExercises()) {
                try {
                    // Extract data immediately to fail fast if relationships aren't loaded
                    // Use the exam parameter's dates to ensure we have the latest exam dates
                    log.debug("Converting exercise {} to DTO with exam dates: visible={}, start={}, end={}", exercise.getId(), exam.getVisibleDate(), exam.getStartDate(),
                            exam.getEndDate());
                    ExerciseWeaviateDTO data = ExerciseWeaviateDTO.fromExerciseWithExam(exercise, exam);
                    exerciseDTOs.add(data);
                    log.debug("Successfully converted exercise {} to DTO", exercise.getId());
                }
                catch (Exception e) {
                    log.error("Failed to convert exercise {} in exam {}: {}", exercise.getId(), exam.getId(), e.getMessage(), e);
                }
            }
        }

        if (exerciseDTOs.isEmpty()) {
            log.warn("No exercise DTOs to update for exam {}", exam.getId());
            return;
        }

        log.debug("Created {} DTOs for exam {}", exerciseDTOs.size(), exam.getId());

        // Step 2: Update each exercise using simple upsert (no batch optimization for now)
        // This is simpler and more reliable than batch operations
        int successCount = 0;
        for (ExerciseWeaviateDTO dto : exerciseDTOs) {
            try {
                log.debug("Upserting exercise {} for exam {}", dto.exerciseId(), exam.getId());
                upsertExerciseInWeaviate(dto);
                successCount++;
                log.debug("Successfully upserted exercise {} for exam {}", dto.exerciseId(), exam.getId());
            }
            catch (Exception e) {
                log.error("Failed to update exercise {} in exam {}: {}", dto.exerciseId(), exam.getId(), e.getMessage(), e);
            }
        }

        log.info("Successfully updated {} out of {} exercises for exam {} in Weaviate", successCount, exerciseDTOs.size(), exam.getId());
    }

    /**
     * Adds properties that are common to all exercise types if their values are present.
     *
     * @param exerciseWeaviateDTO the exercise data providing optional metadata such as dates or difficulty
     * @param properties          the property map that will be sent to Weaviate
     */
    private void addSharedExerciseProperties(ExerciseWeaviateDTO exerciseWeaviateDTO, Map<String, Object> properties) {
        if (exerciseWeaviateDTO.courseTitle() != null) {
            properties.put(ExerciseSchema.Properties.COURSE_NAME, exerciseWeaviateDTO.courseTitle());
        }
        if (exerciseWeaviateDTO.shortName() != null) {
            properties.put(ExerciseSchema.Properties.SHORT_NAME, exerciseWeaviateDTO.shortName());
        }
        if (exerciseWeaviateDTO.problemStatement() != null) {
            properties.put(ExerciseSchema.Properties.PROBLEM_STATEMENT, exerciseWeaviateDTO.problemStatement());
        }
        if (exerciseWeaviateDTO.releaseDate() != null) {
            properties.put(ExerciseSchema.Properties.RELEASE_DATE, formatDate(exerciseWeaviateDTO.releaseDate()));
        }
        if (exerciseWeaviateDTO.startDate() != null) {
            properties.put(ExerciseSchema.Properties.START_DATE, formatDate(exerciseWeaviateDTO.startDate()));
        }
        if (exerciseWeaviateDTO.dueDate() != null) {
            properties.put(ExerciseSchema.Properties.DUE_DATE, formatDate(exerciseWeaviateDTO.dueDate()));
        }
        if (exerciseWeaviateDTO.difficulty() != null) {
            properties.put(ExerciseSchema.Properties.DIFFICULTY, exerciseWeaviateDTO.difficulty());
        }
    }

    /**
     * Adds exam-related properties, including denormalized exam metadata when the exercise belongs to an exam.
     *
     * @param exerciseWeaviateDTO the exercise data whose exam details should be added
     * @param properties          the property map that will be sent to Weaviate
     */
    private void addExamProperties(ExerciseWeaviateDTO exerciseWeaviateDTO, Map<String, Object> properties) {
        properties.put(ExerciseSchema.Properties.IS_EXAM_EXERCISE, exerciseWeaviateDTO.isExamExercise());
        if (!exerciseWeaviateDTO.isExamExercise()) {
            return;
        }

        if (exerciseWeaviateDTO.examId() == null) {
            return;
        }

        properties.put(ExerciseSchema.Properties.EXAM_ID, exerciseWeaviateDTO.examId());
        properties.put(ExerciseSchema.Properties.TEST_EXAM, exerciseWeaviateDTO.isTestExam());
        properties.put(ExerciseSchema.Properties.EXAM_VISIBLE_DATE, formatDate(exerciseWeaviateDTO.examVisibleDate()));
        properties.put(ExerciseSchema.Properties.EXAM_START_DATE, formatDate(exerciseWeaviateDTO.examStartDate()));
        properties.put(ExerciseSchema.Properties.EXAM_END_DATE, formatDate(exerciseWeaviateDTO.examEndDate()));
    }

    /**
     * Adds exercise type-specific properties to the Weaviate property map based on the exercise type.
     *
     * @param exerciseWeaviateDTO the exercise data
     * @param properties          the property map to populate
     */
    private void addExerciseTypeSpecificProperties(ExerciseWeaviateDTO exerciseWeaviateDTO, Map<String, Object> properties) {
        if (exerciseWeaviateDTO.programmingLanguage() != null) {
            properties.put(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE, exerciseWeaviateDTO.programmingLanguage());
        }
        if (exerciseWeaviateDTO.projectType() != null) {
            properties.put(ExerciseSchema.Properties.PROJECT_TYPE, exerciseWeaviateDTO.projectType());
        }
        if (exerciseWeaviateDTO.diagramType() != null) {
            properties.put(ExerciseSchema.Properties.DIAGRAM_TYPE, exerciseWeaviateDTO.diagramType());
        }
        if (exerciseWeaviateDTO.quizMode() != null) {
            properties.put(ExerciseSchema.Properties.QUIZ_MODE, exerciseWeaviateDTO.quizMode());
        }
        if (exerciseWeaviateDTO.quizDuration() != null) {
            properties.put(ExerciseSchema.Properties.QUIZ_DURATION, exerciseWeaviateDTO.quizDuration());
        }
        if (exerciseWeaviateDTO.filePattern() != null) {
            properties.put(ExerciseSchema.Properties.FILE_PATTERN, exerciseWeaviateDTO.filePattern());
        }
    }

    /**
     * Deletes all exercise entries for the given exercise ID from the Weaviate collection.
     *
     * @param exerciseId the exercise ID
     */
    private void deleteExerciseFromWeaviate(long exerciseId) {
        var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);
        var deleteResult = collection.data.deleteMany(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId));
        log.debug("Deleted {} exercise entries for exercise ID {}", deleteResult.successful(), exerciseId);
    }

    /**
     * Formats a ZonedDateTime to RFC3339 format required by Weaviate.
     *
     * @param dateTime the date time to format
     * @return the formatted date string
     */
    private String formatDate(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
