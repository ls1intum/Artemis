package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
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
     * Updates exercise metadata in Weaviate using an upsert strategy.
     * Queries for the existing object by exercise ID, then uses replace if found or insert if not.
     * This avoids the data loss window of delete-then-insert and uses Weaviate's intended update API.
     *
     * @param exercise the exercise to update
     */
    private void updateExercise(Exercise exercise) {
        if (exercise.getId() == null) {
            log.warn("Cannot update exercise without an ID");
            return;
        }

        try {
            upsertExerciseInWeaviate(exercise);
            log.debug("Successfully updated exercise {} '{}' in Weaviate", exercise.getId(), exercise.getTitle());
        }
        catch (Exception e) {
            log.error("Failed to update exercise {} in Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Performs an upsert operation: queries for existing object and replaces it, or inserts if not found.
     *
     * @param exercise the exercise to upsert
     * @throws Exception if the operation fails
     */
    private void upsertExerciseInWeaviate(Exercise exercise) throws Exception {
        var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);

        var existingObjectQueryResult = collection.query.fetchObjects(query -> query.filters(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exercise.getId())).limit(1));

        Map<String, Object> properties = buildExerciseProperties(exercise);

        if (!existingObjectQueryResult.objects().isEmpty()) {
            // Object exists - use replace to update it
            var existingObject = existingObjectQueryResult.objects().getFirst();
            String uuid = existingObject.uuid();
            collection.data.replace(uuid, r -> r.properties(properties));
            log.debug("Replaced existing exercise {} with UUID {}", exercise.getId(), uuid);
        }
        else {
            // Object doesn't exist - insert new one
            collection.data.insert(properties);
            log.debug("Inserted new exercise {}", exercise.getId());
        }
    }

    /**
     * Builds the complete property map for an exercise.
     *
     * @param exercise the exercise
     * @return the property map ready for Weaviate
     */
    private Map<String, Object> buildExerciseProperties(Exercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Map<String, Object> properties = new HashMap<>();

        properties.put(ExerciseSchema.Properties.EXERCISE_ID, exercise.getId());
        properties.put(ExerciseSchema.Properties.COURSE_ID, course.getId());
        properties.put(ExerciseSchema.Properties.TITLE, exercise.getTitle());
        properties.put(ExerciseSchema.Properties.EXERCISE_TYPE, exercise.getType());
        properties.put(ExerciseSchema.Properties.MAX_POINTS, exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0);

        addSharedExerciseProperties(exercise, course, properties);
        addExamProperties(exercise, properties);
        addExerciseTypeSpecificProperties(exercise, properties);

        return properties;
    }

    /**
     * Asynchronously inserts exercise metadata into Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     *
     * @param exercise the exercise to insert
     */
    @Async
    public void insertExerciseAsync(Exercise exercise) {
        SecurityUtils.setAuthorizationObject();

        if (exercise.getId() == null) {
            log.warn("Cannot insert exercise without an ID");
            return;
        }

        try {
            upsertExerciseInWeaviate(exercise);
            log.debug("Successfully inserted exercise {} '{}' into Weaviate", exercise.getId(), exercise.getTitle());
        }
        catch (Exception e) {
            log.error("Failed to insert exercise {} into Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously updates exercise metadata in Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     *
     * @param exercise the exercise to update
     */
    @Async
    public void updateExerciseAsync(Exercise exercise) {
        SecurityUtils.setAuthorizationObject();
        updateExercise(exercise);
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
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     *
     * @param exam the exam whose exercises should be updated (must have exercise groups and exercises loaded)
     */
    @Async
    public void updateExamExercisesAsync(Exam exam) {
        SecurityUtils.setAuthorizationObject();

        if (exam == null || exam.getExerciseGroups() == null) {
            return;
        }

        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                updateExercise(exercise);
            }
        }
    }

    /**
     * Adds properties that are common to all exercise types if their values are present.
     *
     * @param exercise   the exercise providing optional metadata such as dates or difficulty
     * @param course     the owning course used for denormalized course metadata
     * @param properties the property map that will be sent to Weaviate
     */
    private void addSharedExerciseProperties(Exercise exercise, Course course, Map<String, Object> properties) {
        if (course.getTitle() != null) {
            properties.put(ExerciseSchema.Properties.COURSE_NAME, course.getTitle());
        }
        if (exercise.getShortName() != null) {
            properties.put(ExerciseSchema.Properties.SHORT_NAME, exercise.getShortName());
        }
        if (exercise.getProblemStatement() != null) {
            properties.put(ExerciseSchema.Properties.PROBLEM_STATEMENT, exercise.getProblemStatement());
        }
        if (exercise.getReleaseDate() != null) {
            properties.put(ExerciseSchema.Properties.RELEASE_DATE, formatDate(exercise.getReleaseDate()));
        }
        if (exercise.getStartDate() != null) {
            properties.put(ExerciseSchema.Properties.START_DATE, formatDate(exercise.getStartDate()));
        }
        if (exercise.getDueDate() != null) {
            properties.put(ExerciseSchema.Properties.DUE_DATE, formatDate(exercise.getDueDate()));
        }
        if (exercise.getDifficulty() != null) {
            properties.put(ExerciseSchema.Properties.DIFFICULTY, exercise.getDifficulty().name());
        }
    }

    /**
     * Adds exam-related properties, including denormalized exam metadata when the exercise belongs to an exam.
     *
     * @param exercise   the exercise whose exam details should be added
     * @param properties the property map that will be sent to Weaviate
     */
    private void addExamProperties(Exercise exercise, Map<String, Object> properties) {
        properties.put(ExerciseSchema.Properties.IS_EXAM_EXERCISE, exercise.isExamExercise());
        if (!exercise.isExamExercise()) {
            return;
        }

        Exam exam = exercise.getExam();
        if (exam == null) {
            return;
        }

        properties.put(ExerciseSchema.Properties.EXAM_ID, exam.getId());
        properties.put(ExerciseSchema.Properties.TEST_EXAM, exam.isTestExam());
        properties.put(ExerciseSchema.Properties.EXAM_VISIBLE_DATE, formatDate(exam.getVisibleDate()));
        properties.put(ExerciseSchema.Properties.EXAM_START_DATE, formatDate(exam.getStartDate()));
        properties.put(ExerciseSchema.Properties.EXAM_END_DATE, formatDate(exam.getEndDate()));
    }

    /**
     * Adds exercise type-specific properties to the Weaviate property map based on the exercise type.
     *
     * @param exercise   the exercise
     * @param properties the property map to populate
     */
    private void addExerciseTypeSpecificProperties(Exercise exercise, Map<String, Object> properties) {
        switch (exercise) {
            case ProgrammingExercise programmingExercise -> {
                if (programmingExercise.getProgrammingLanguage() != null) {
                    properties.put(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE, programmingExercise.getProgrammingLanguage().name());
                }
                if (programmingExercise.getProjectType() != null) {
                    properties.put(ExerciseSchema.Properties.PROJECT_TYPE, programmingExercise.getProjectType().name());
                }
            }
            case ModelingExercise modelingExercise -> {
                if (modelingExercise.getDiagramType() != null) {
                    properties.put(ExerciseSchema.Properties.DIAGRAM_TYPE, modelingExercise.getDiagramType().name());
                }
            }
            case QuizExercise quizExercise -> {
                if (quizExercise.getQuizMode() != null) {
                    properties.put(ExerciseSchema.Properties.QUIZ_MODE, quizExercise.getQuizMode().name());
                }
                if (quizExercise.getDuration() != null) {
                    properties.put(ExerciseSchema.Properties.QUIZ_DURATION, quizExercise.getDuration());
                }
            }
            case FileUploadExercise fileUploadExercise -> {
                if (fileUploadExercise.getFilePattern() != null) {
                    properties.put(ExerciseSchema.Properties.FILE_PATTERN, fileUploadExercise.getFilePattern());
                }
            }
            default -> {
                // TextExercise and any other types have no additional properties
            }
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
