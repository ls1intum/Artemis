package de.tum.cit.aet.artemis.core.service.weaviate;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateReferenceDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.ReferenceProperty;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Service for interacting with Weaviate vector database.
 * This service handles schema creation, data insertion, and search operations.
 */
@Service
@ConditionalOnProperty(name = "artemis.weaviate.enabled", havingValue = "true")
public class WeaviateService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateService.class);

    private final WeaviateClient client;

    public WeaviateService(WeaviateClient client) {
        this.client = client;
    }

    /**
     * Initializes the Weaviate collections on startup.
     * Creates collections that don't exist yet.
     */
    @PostConstruct
    public void initializeCollections() {
        log.info("Initializing Weaviate collections...");

        for (WeaviateCollectionSchema schema : WeaviateSchemas.ALL_SCHEMAS) {
            ensureCollectionExists(schema);
        }

        log.info("Weaviate collection initialization complete");
    }

    /**
     * Ensures a collection exists, creating it if necessary.
     *
     * @param schema the schema definition
     */
    private void ensureCollectionExists(WeaviateCollectionSchema schema) {
        String collectionName = schema.collectionName();

        try {
            if (client.collections.exists(collectionName)) {
                log.debug("Collection '{}' already exists", collectionName);
                return;
            }

            log.info("Creating collection '{}'...", collectionName);

            client.collections.create(collectionName, col -> {
                // Configure text2vec-transformers vectorizer for automatic embeddings
                col.vectorConfig(VectorConfig.text2vecTransformers());

                // Add properties
                for (WeaviatePropertyDefinition prop : schema.properties()) {
                    col.properties(createProperty(prop));
                }

                // Add references
                for (WeaviateReferenceDefinition ref : schema.references()) {
                    col.references(ReferenceProperty.to(ref.name(), ref.targetCollection()));
                }

                return col;
            });

            log.info("Successfully created collection '{}'", collectionName);
        }
        catch (IOException e) {
            log.error("Failed to create collection '{}': {}", collectionName, e.getMessage(), e);
            throw new WeaviateException("Failed to create collection: " + collectionName, e);
        }
    }

    /**
     * Creates a Weaviate property from a property definition.
     *
     * @param definition the property definition
     * @return the Weaviate property
     */
    private Property createProperty(WeaviatePropertyDefinition definition) {
        return switch (definition.dataType()) {
            case INT -> Property.integer(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case TEXT -> Property.text(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case NUMBER -> Property.number(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case BOOLEAN -> Property.bool(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case DATE -> Property.date(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case UUID -> Property.uuid(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case BLOB -> Property.blob(definition.name());
        };
    }

    /**
     * Gets a collection handle for the specified collection name.
     *
     * @param collectionName the collection name
     * @return the collection handle
     */
    public CollectionHandle<Map<String, Object>> getCollection(String collectionName) {
        return client.collections.use(collectionName);
    }

    /**
     * Inserts an object into the Lectures collection.
     *
     * @param courseId          the course ID
     * @param courseLanguage    the course language
     * @param lectureId         the lecture ID
     * @param lectureUnitId     the lecture unit ID
     * @param pageTextContent   the page text content
     * @param pageNumber        the page number
     * @param baseUrl           the base URL
     * @param attachmentVersion the attachment version
     */
    public void insertLecturePageChunk(long courseId, String courseLanguage, long lectureId, long lectureUnitId, String pageTextContent, int pageNumber, String baseUrl,
            int attachmentVersion) {

        var collection = getCollection(WeaviateSchemas.LECTURES_COLLECTION);

        try {
            collection.data.insert(Map.of(WeaviateSchemas.LecturesProperties.COURSE_ID, courseId, WeaviateSchemas.LecturesProperties.COURSE_LANGUAGE, courseLanguage,
                    WeaviateSchemas.LecturesProperties.LECTURE_ID, lectureId, WeaviateSchemas.LecturesProperties.LECTURE_UNIT_ID, lectureUnitId,
                    WeaviateSchemas.LecturesProperties.PAGE_TEXT_CONTENT, pageTextContent, WeaviateSchemas.LecturesProperties.PAGE_NUMBER, pageNumber,
                    WeaviateSchemas.LecturesProperties.BASE_URL, baseUrl, WeaviateSchemas.LecturesProperties.ATTACHMENT_VERSION, attachmentVersion));

            log.debug("Inserted lecture page chunk for lecture unit {} page {}", lectureUnitId, pageNumber);
        }
        catch (IOException e) {
            log.error("Failed to insert lecture page chunk for lecture unit {} page {}: {}", lectureUnitId, pageNumber, e.getMessage(), e);
            throw new WeaviateException("Failed to insert lecture page chunk", e);
        }
    }

    /**
     * Inserts an FAQ into the Faqs collection.
     *
     * @param courseId          the course ID
     * @param courseName        the course name
     * @param courseDescription the course description
     * @param courseLanguage    the course language
     * @param faqId             the FAQ ID
     * @param questionTitle     the question title
     * @param questionAnswer    the question answer
     */
    public void insertFaq(long courseId, String courseName, String courseDescription, String courseLanguage, long faqId, String questionTitle, String questionAnswer) {

        var collection = getCollection(WeaviateSchemas.FAQS_COLLECTION);

        try {
            collection.data.insert(Map.of(WeaviateSchemas.FaqsProperties.COURSE_ID, courseId, WeaviateSchemas.FaqsProperties.COURSE_NAME, courseName,
                    WeaviateSchemas.FaqsProperties.COURSE_DESCRIPTION, courseDescription, WeaviateSchemas.FaqsProperties.COURSE_LANGUAGE, courseLanguage,
                    WeaviateSchemas.FaqsProperties.FAQ_ID, faqId, WeaviateSchemas.FaqsProperties.QUESTION_TITLE, questionTitle, WeaviateSchemas.FaqsProperties.QUESTION_ANSWER,
                    questionAnswer));

            log.debug("Inserted FAQ {} for course {}", faqId, courseId);
        }
        catch (IOException e) {
            log.error("Failed to insert FAQ {} for course {}: {}", faqId, courseId, e.getMessage(), e);
            throw new WeaviateException("Failed to insert FAQ", e);
        }
    }

    /**
     * Inserts an exercise into the Exercises collection.
     *
     * @param exerciseId          the exercise ID
     * @param courseId            the course ID
     * @param courseName          the course name
     * @param title               the exercise title
     * @param shortName           the exercise short name
     * @param problemStatement    the problem statement
     * @param releaseDate         the release date (may be null)
     * @param startDate           the start date (may be null)
     * @param dueDate             the due date (may be null)
     * @param exerciseType        the exercise type (programming, quiz, modeling, text, file-upload)
     * @param programmingLanguage the programming language (for programming exercises, may be null)
     * @param difficulty          the difficulty level (may be null)
     * @param maxPoints           the maximum points
     * @param baseUrl             the base URL of the Artemis instance
     */
    public void insertExercise(long exerciseId, long courseId, @Nullable String courseName, String title, @Nullable String shortName, @Nullable String problemStatement,
            @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, String exerciseType, @Nullable String programmingLanguage,
            @Nullable String difficulty, double maxPoints, String baseUrl) {

        var collection = getCollection(WeaviateSchemas.EXERCISES_COLLECTION);

        Map<String, Object> properties = new HashMap<>();
        properties.put(WeaviateSchemas.ExercisesProperties.EXERCISE_ID, exerciseId);
        properties.put(WeaviateSchemas.ExercisesProperties.COURSE_ID, courseId);
        properties.put(WeaviateSchemas.ExercisesProperties.TITLE, title);
        properties.put(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE, exerciseType);
        properties.put(WeaviateSchemas.ExercisesProperties.MAX_POINTS, maxPoints);
        properties.put(WeaviateSchemas.ExercisesProperties.BASE_URL, baseUrl);

        // Add optional fields only if they are not null
        if (courseName != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.COURSE_NAME, courseName);
        }
        if (shortName != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.SHORT_NAME, shortName);
        }
        if (problemStatement != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.PROBLEM_STATEMENT, problemStatement);
        }
        if (releaseDate != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.RELEASE_DATE, formatDate(releaseDate));
        }
        if (startDate != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.START_DATE, formatDate(startDate));
        }
        if (dueDate != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.DUE_DATE, formatDate(dueDate));
        }
        if (programmingLanguage != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.PROGRAMMING_LANGUAGE, programmingLanguage);
        }
        if (difficulty != null) {
            properties.put(WeaviateSchemas.ExercisesProperties.DIFFICULTY, difficulty);
        }

        try {
            collection.data.insert(properties);
            log.debug("Inserted exercise {} '{}' for course {}", exerciseId, title, courseId);
        }
        catch (IOException e) {
            log.error("Failed to insert exercise {} '{}' for course {}: {}", exerciseId, title, courseId, e.getMessage(), e);
            throw new WeaviateException("Failed to insert exercise", e);
        }
    }

    /**
     * Deletes all exercise entries for the given exercise ID from the Exercises collection.
     *
     * @param exerciseId the exercise ID
     */
    public void deleteExercise(long exerciseId) {
        var collection = getCollection(WeaviateSchemas.EXERCISES_COLLECTION);

        // Delete objects where exercise_id equals the given ID
        var deleteResult = collection.data.deleteMany(Filter.property(WeaviateSchemas.ExercisesProperties.EXERCISE_ID).eq(exerciseId));
        log.debug("Deleted {} exercise entries for exercise ID {}", deleteResult.successful(), exerciseId);
    }

    /**
     * Updates an exercise in the Exercises collection by deleting and re-inserting it.
     *
     * @param exerciseId          the exercise ID
     * @param courseId            the course ID
     * @param courseName          the course name
     * @param title               the exercise title
     * @param shortName           the exercise short name
     * @param problemStatement    the problem statement
     * @param releaseDate         the release date (may be null)
     * @param startDate           the start date (may be null)
     * @param dueDate             the due date (may be null)
     * @param exerciseType        the exercise type (programming, quiz, modeling, text, file-upload)
     * @param programmingLanguage the programming language (for programming exercises, may be null)
     * @param difficulty          the difficulty level (may be null)
     * @param maxPoints           the maximum points
     * @param baseUrl             the base URL of the Artemis instance
     */
    public void updateExercise(long exerciseId, long courseId, @Nullable String courseName, String title, @Nullable String shortName, @Nullable String problemStatement,
            @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, String exerciseType, @Nullable String programmingLanguage,
            @Nullable String difficulty, double maxPoints, String baseUrl) {
        // Delete existing entries first
        deleteExercise(exerciseId);
        // Insert the updated data
        insertExercise(exerciseId, courseId, courseName, title, shortName, problemStatement, releaseDate, startDate, dueDate, exerciseType, programmingLanguage, difficulty,
                maxPoints, baseUrl);
    }

    /**
     * Fetches programming exercises for a course from the Exercises collection.
     * Optionally filters by release date for student visibility.
     *
     * @param courseId           the course ID to filter by
     * @param filterReleasedOnly if true, only return exercises with release_date in the past or null
     * @return list of exercise properties as maps
     */
    public List<Map<String, Object>> fetchProgrammingExercisesByCourseId(long courseId, boolean filterReleasedOnly) {
        var collection = getCollection(WeaviateSchemas.EXERCISES_COLLECTION);

        // Build the filter: course_id = courseId AND exercise_type = "programming"
        var courseFilter = Filter.property(WeaviateSchemas.ExercisesProperties.COURSE_ID).eq(courseId);
        var typeFilter = Filter.property(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE).eq("programming");
        var baseFilter = courseFilter.and(typeFilter);

        Filter finalFilter;
        if (filterReleasedOnly) {
            // For students: only show exercises where release_date <= now
            var releaseDateFilter = Filter.property(WeaviateSchemas.ExercisesProperties.RELEASE_DATE).lte(OffsetDateTime.now());
            finalFilter = Filter.and(baseFilter, releaseDateFilter);
        }
        else {
            finalFilter = baseFilter;
        }

        try {
            var response = collection.query.fetchObjects(q -> q.filters(finalFilter));
            List<Map<String, Object>> results = new ArrayList<>();
            for (var obj : response.objects()) {
                results.add(obj.properties());
            }
            log.debug("Fetched {} programming exercises for course {} (filterReleasedOnly={})", results.size(), courseId, filterReleasedOnly);
            return results;
        }
        catch (Exception e) {
            log.error("Failed to fetch programming exercises for course {}: {}", courseId, e.getMessage(), e);
            throw new WeaviateException("Failed to fetch programming exercises", e);
        }
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

    /**
     * Checks if the Weaviate connection is healthy.
     *
     * @return true if the connection is healthy
     */
    public boolean isHealthy() {
        try {
            // Check if we can list collections
            client.collections.list();
            return true;
        }
        catch (Exception e) {
            log.warn("Weaviate health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Exception thrown when Weaviate operations fail.
     */
    public static class WeaviateException extends RuntimeException {

        public WeaviateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
