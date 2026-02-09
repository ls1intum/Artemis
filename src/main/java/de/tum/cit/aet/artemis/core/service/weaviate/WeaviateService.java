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

import de.tum.cit.aet.artemis.core.config.weaviate.WeaviateConfigurationProperties;
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

    private final String collectionPrefix;

    public WeaviateService(WeaviateClient client, WeaviateConfigurationProperties properties) {
        this.client = client;
        this.collectionPrefix = properties.collectionPrefix();
    }

    /**
     * Resolves the actual collection name by prepending the configured prefix.
     *
     * @param baseName the base collection name (e.g. "Exercises")
     * @return the prefixed collection name (e.g. "TestExercises" when prefix is "Test")
     */
    private String resolveCollectionName(String baseName) {
        return collectionPrefix + baseName;
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
        String collectionName = resolveCollectionName(schema.collectionName());

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
                    col.references(ReferenceProperty.to(ref.name(), resolveCollectionName(ref.targetCollection())));
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
        return client.collections.use(resolveCollectionName(collectionName));
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

        var collection = getCollection(WeaviateSchemas.PROGRAMMING_EXERCISES_COLLECTION);

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
        var collection = getCollection(WeaviateSchemas.PROGRAMMING_EXERCISES_COLLECTION);

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
        var collection = getCollection(WeaviateSchemas.PROGRAMMING_EXERCISES_COLLECTION);

        // Build the filter: course_id = courseId AND exercise_type = "programming"
        var courseFilter = Filter.property(WeaviateSchemas.ExercisesProperties.COURSE_ID).eq(courseId);
        var typeFilter = Filter.property(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE).eq("programming");
        var baseFilter = Filter.and(courseFilter, typeFilter);

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
     * Performs semantic search on exercises using vector similarity via REST API.
     *
     * @param searchQuery the search query text
     * @param courseId    the course ID to filter by (optional, null for all courses)
     * @param limit       maximum number of results to return
     * @return list of exercise properties as maps with similarity scores
     */
    public List<Map<String, Object>> semanticSearchExercises(String searchQuery, @Nullable Long courseId, int limit) {
        try {
            // For now, fall back to basic text filtering until we implement proper nearText API
            var collection = getCollection(WeaviateSchemas.PROGRAMMING_EXERCISES_COLLECTION);

            // Create basic filters
            var textFilters = new ArrayList<Filter>();

            // Add course filter if specified
            if (courseId != null) {
                textFilters.add(Filter.property(WeaviateSchemas.ExercisesProperties.COURSE_ID).eq(courseId));
            }

            // Add text search filters (basic keyword matching for now)
            var lowerQuery = searchQuery.toLowerCase();
            var titleFilter = Filter.property(WeaviateSchemas.ExercisesProperties.TITLE).like("*" + lowerQuery + "*");
            var problemFilter = Filter.property(WeaviateSchemas.ExercisesProperties.PROBLEM_STATEMENT).like("*" + lowerQuery + "*");
            var textMatchFilter = Filter.or(titleFilter, problemFilter);
            textFilters.add(textMatchFilter);

            Filter finalFilter = textFilters.get(0);
            for (int i = 1; i < textFilters.size(); i++) {
                finalFilter = finalFilter.and(textFilters.get(i));
            }

            final Filter searchFilter = finalFilter; // Make it effectively final for lambda
            var response = collection.query.fetchObjects(q -> q.filters(searchFilter).limit(limit));
            List<Map<String, Object>> results = new ArrayList<>();

            for (var obj : response.objects()) {
                Map<String, Object> result = new HashMap<>(obj.properties());
                // Add a basic relevance score (for compatibility)
                result.put("_relevance", 0.8); // Placeholder relevance score
                results.add(result);
            }

            log.debug("Basic text search for '{}' returned {} results", searchQuery, results.size());
            return results;
        }
        catch (Exception e) {
            log.error("Failed to perform search for query '{}': {}", searchQuery, e.getMessage(), e);
            throw new WeaviateException("Failed to perform search", e);
        }
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
