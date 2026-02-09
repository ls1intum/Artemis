package de.tum.cit.aet.artemis.core.service.weaviate;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
import de.tum.cit.aet.artemis.core.config.weaviate.schema.entitySchemas.ProgrammingExerciseSchema;
import de.tum.cit.aet.artemis.core.exception.WeaviateException;
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

            client.collections.create(collectionName, collection -> {
                // Configure text2vec-transformers vectorizer for automatic embeddings
                collection.vectorConfig(VectorConfig.text2vecTransformers());

                // Add properties
                for (WeaviatePropertyDefinition prop : schema.properties()) {
                    collection.properties(createProperty(prop));
                }

                // Add references
                for (WeaviateReferenceDefinition ref : schema.references()) {
                    collection.references(ReferenceProperty.to(ref.name(), resolveCollectionName(ref.targetCollection())));
                }

                return collection;
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
            case INT -> Property.integer(definition.name(), property -> property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case TEXT -> Property.text(definition.name(), property -> property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case NUMBER -> Property.number(definition.name(), property -> property.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case BOOLEAN -> Property.bool(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case DATE -> Property.date(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
            case UUID -> Property.uuid(definition.name(), property -> property.indexFilterable(definition.indexFilterable()));
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
    public void insertProgrammingExercise(long exerciseId, long courseId, @Nullable String courseName, String title, @Nullable String shortName, @Nullable String problemStatement,
            @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, String exerciseType, @Nullable String programmingLanguage,
            @Nullable String difficulty, double maxPoints, String baseUrl) {

        var programmingExerciseCollection = getCollection(ProgrammingExerciseSchema.COLLECTION_NAME);

        Map<String, Object> properties = new HashMap<>();
        properties.put(ProgrammingExerciseSchema.Properties.EXERCISE_ID, exerciseId);
        properties.put(ProgrammingExerciseSchema.Properties.COURSE_ID, courseId);
        properties.put(ProgrammingExerciseSchema.Properties.TITLE, title);
        properties.put(ProgrammingExerciseSchema.Properties.EXERCISE_TYPE, exerciseType);
        properties.put(ProgrammingExerciseSchema.Properties.MAX_POINTS, maxPoints);
        properties.put(ProgrammingExerciseSchema.Properties.BASE_URL, baseUrl);

        // Add optional fields only if they are not null
        if (courseName != null) {
            properties.put(ProgrammingExerciseSchema.Properties.COURSE_NAME, courseName);
        }
        if (shortName != null) {
            properties.put(ProgrammingExerciseSchema.Properties.SHORT_NAME, shortName);
        }
        if (problemStatement != null) {
            properties.put(ProgrammingExerciseSchema.Properties.PROBLEM_STATEMENT, problemStatement);
        }
        if (releaseDate != null) {
            properties.put(ProgrammingExerciseSchema.Properties.RELEASE_DATE, formatDate(releaseDate));
        }
        if (startDate != null) {
            properties.put(ProgrammingExerciseSchema.Properties.START_DATE, formatDate(startDate));
        }
        if (dueDate != null) {
            properties.put(ProgrammingExerciseSchema.Properties.DUE_DATE, formatDate(dueDate));
        }
        if (programmingLanguage != null) {
            properties.put(ProgrammingExerciseSchema.Properties.PROGRAMMING_LANGUAGE, programmingLanguage);
        }
        if (difficulty != null) {
            properties.put(ProgrammingExerciseSchema.Properties.DIFFICULTY, difficulty);
        }

        try {
            programmingExerciseCollection.data.insert(properties);
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
    public void deleteProgrammingExercise(long exerciseId) {
        var programmingExerciseCollection = getCollection(ProgrammingExerciseSchema.COLLECTION_NAME);

        var deleteResult = programmingExerciseCollection.data.deleteMany(Filter.property(ProgrammingExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId));
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
    public void updateProgrammingExercise(long exerciseId, long courseId, @Nullable String courseName, String title, @Nullable String shortName, @Nullable String problemStatement,
            @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, String exerciseType, @Nullable String programmingLanguage,
            @Nullable String difficulty, double maxPoints, String baseUrl) {
        // Delete existing entries first
        deleteProgrammingExercise(exerciseId);
        // Insert the updated data
        insertProgrammingExercise(exerciseId, courseId, courseName, title, shortName, problemStatement, releaseDate, startDate, dueDate, exerciseType, programmingLanguage,
                difficulty, maxPoints, baseUrl);
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
