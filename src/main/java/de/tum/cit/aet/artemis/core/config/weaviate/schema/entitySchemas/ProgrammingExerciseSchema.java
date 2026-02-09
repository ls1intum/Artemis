package de.tum.cit.aet.artemis.core.config.weaviate.schema.entitySchemas;

import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.DATE;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.INT;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.NUMBER;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateDataType.TEXT;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.filterable;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.nonSearchable;
import static de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition.searchable;

import java.util.List;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateCollectionSchema;

/**
 * Schema definition for the ProgrammingExercises Weaviate collection.
 * This stores exercise metadata for global search.
 */
public final class ProgrammingExerciseSchema {

    private ProgrammingExerciseSchema() {
    }

    /**
     * Collection name for programming exercises metadata.
     */
    public static final String COLLECTION_NAME = "ProgrammingExercises";

    /**
     * Property names for the ProgrammingExercises collection.
     */
    public static final class Properties {

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

        private Properties() {
        }
    }

    /**
     * Schema definition for the ProgrammingExercises collection.
     */
    public static final WeaviateCollectionSchema SCHEMA = WeaviateCollectionSchema.of(COLLECTION_NAME, List.of(filterable(Properties.EXERCISE_ID, INT, "The ID of the exercise"),
            filterable(Properties.COURSE_ID, INT, "The ID of the course"), searchable(Properties.COURSE_NAME, TEXT, "The name of the course"),
            searchable(Properties.TITLE, TEXT, "The title of the exercise"), searchable(Properties.SHORT_NAME, TEXT, "The short name of the exercise"),
            searchable(Properties.PROBLEM_STATEMENT, TEXT, "The problem statement of the exercise"), filterable(Properties.RELEASE_DATE, DATE, "The release date of the exercise"),
            nonSearchable(Properties.START_DATE, DATE, "The start date of the exercise"), nonSearchable(Properties.DUE_DATE, DATE, "The due date of the exercise"),
            filterable(Properties.EXERCISE_TYPE, TEXT, "The type of the exercise (programming, quiz, modeling, text, file-upload)"),
            filterable(Properties.PROGRAMMING_LANGUAGE, TEXT, "The programming language (for programming exercises)"),
            filterable(Properties.DIFFICULTY, TEXT, "The difficulty level of the exercise"), nonSearchable(Properties.MAX_POINTS, NUMBER, "The maximum points for the exercise"),
            nonSearchable(Properties.BASE_URL, TEXT, "The base URL of the Artemis instance")));
}
