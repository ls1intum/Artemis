package de.tum.cit.aet.artemis.core.config.weaviate.schema;

import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.DATE;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.INT;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.NUMBER;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.TEXT;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.filterable;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.nonSearchable;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.searchable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines the Weaviate collection schemas that match the Iris repository definitions.
 * These schemas must be kept in sync with the Python schema definitions in the Iris repository
 * at: https://github.com/ls1intum/edutelligence/tree/main/iris/src/iris/vector_database
 * <p>
 * Schema validation on startup ensures compatibility between Artemis and Iris.
 */
public final class WeaviateSchemas {

    private WeaviateSchemas() {
        // Utility class, should not be instantiated
    }

    /**
     * Collection name for exercises metadata.
     */
    public static final String PROGRAMMING_EXERCISES_COLLECTION = "ProgrammingExercises";

    /**
     * Property names for the Exercises collection.
     */
    public static final class ExercisesProperties {

        public static final String EXERCISE_ID = "exercise_id";

        public static final String COURSE_ID = "course_id";

        public static final String COURSE_NAME = "course_name";

        public static final String TITLE = "title";

        public static final String SHORT_NAME = "short_name";

        public static final String PROBLEM_STATEMENT = "problem_statement";

        public static final String RELEASE_DATE = "release_date";

        public static final String START_DATE = "start_date";

        public static final String DUE_DATE = "due_date";

        public static final String EXERCISE_TYPE = "exercise_type";

        public static final String PROGRAMMING_LANGUAGE = "programming_language";

        public static final String DIFFICULTY = "difficulty";

        public static final String MAX_POINTS = "max_points";

        public static final String BASE_URL = "base_url";

        private ExercisesProperties() {
        }
    }

    /**
     * Schema definition for the Exercises collection.
     * This stores exercise metadata for global search.
     */
    public static final WeaviateCollectionSchema EXERCISES_SCHEMA = WeaviateCollectionSchema.of(PROGRAMMING_EXERCISES_COLLECTION,
            List.of(filterable(ExercisesProperties.EXERCISE_ID, INT, "The ID of the exercise"), filterable(ExercisesProperties.COURSE_ID, INT, "The ID of the course"),
                    nonSearchable(ExercisesProperties.COURSE_NAME, TEXT, "The name of the course"), searchable(ExercisesProperties.TITLE, TEXT, "The title of the exercise"),
                    searchable(ExercisesProperties.SHORT_NAME, TEXT, "The short name of the exercise"),
                    searchable(ExercisesProperties.PROBLEM_STATEMENT, TEXT, "The problem statement of the exercise"),
                    filterable(ExercisesProperties.RELEASE_DATE, DATE, "The release date of the exercise"),
                    nonSearchable(ExercisesProperties.START_DATE, DATE, "The start date of the exercise"),
                    nonSearchable(ExercisesProperties.DUE_DATE, DATE, "The due date of the exercise"),
                    filterable(ExercisesProperties.EXERCISE_TYPE, TEXT, "The type of the exercise (programming, quiz, modeling, text, file-upload)"),
                    filterable(ExercisesProperties.PROGRAMMING_LANGUAGE, TEXT, "The programming language (for programming exercises)"),
                    filterable(ExercisesProperties.DIFFICULTY, TEXT, "The difficulty level of the exercise"),
                    nonSearchable(ExercisesProperties.MAX_POINTS, NUMBER, "The maximum points for the exercise"),
                    nonSearchable(ExercisesProperties.BASE_URL, TEXT, "The base URL of the Artemis instance")));

    // ==================== All Schemas ====================

    /**
     * List of all Weaviate collection schemas defined in Artemis.
     */
    public static final List<WeaviateCollectionSchema> ALL_SCHEMAS = List.of(EXERCISES_SCHEMA);

    /**
     * Map of collection name to schema for quick lookup.
     */
    public static final Map<String, WeaviateCollectionSchema> SCHEMAS_BY_NAME = ALL_SCHEMAS.stream()
            .collect(Collectors.toMap(WeaviateCollectionSchema::collectionName, Function.identity()));

    /**
     * Gets a schema by collection name.
     *
     * @param collectionName the collection name
     * @return the schema, or null if not found
     */
    public static WeaviateCollectionSchema getSchema(String collectionName) {
        return SCHEMAS_BY_NAME.get(collectionName);
    }
}
