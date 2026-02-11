package de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas;

import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.BOOLEAN;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.DATE;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.INT;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.NUMBER;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.TEXT;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.filterable;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.nonSearchable;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.searchable;

import java.util.List;

import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateCollectionSchema;

/**
 * Schema definition for the Exercises Weaviate collection.
 * This stores exercise metadata for all exercise types (programming, text, modeling, quiz, file-upload)
 * in a single collection for efficient cross-type search queries.
 * <p>
 * Type-specific properties (e.g. {@code programming_language}, {@code diagram_type}) are only
 * populated for the relevant exercise type and left absent for others.
 */
public final class ExerciseSchema {

    /**
     * Utility class, should not be instantiated.
     */
    private ExerciseSchema() {
    }

    /**
     * Collection name for exercise metadata.
     */
    public static final String COLLECTION_NAME = "Exercises";

    /**
     * Property names for the Exercises collection.
     */
    public static final class Properties {

        // Shared properties (all exercise types)

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

        public static final String DIFFICULTY = "difficulty";

        public static final String MAX_POINTS = "max_points";

        // Exam properties

        public static final String IS_EXAM_EXERCISE = "is_exam_exercise";

        public static final String EXAM_ID = "exam_id";

        public static final String EXAM_VISIBLE_DATE = "exam_visible_date";

        public static final String EXAM_START_DATE = "exam_start_date";

        public static final String EXAM_END_DATE = "exam_end_date";

        public static final String TEST_EXAM = "test_exam";

        // Programming exercise properties

        public static final String PROGRAMMING_LANGUAGE = "programming_language";

        public static final String PROJECT_TYPE = "project_type";

        // Modeling exercise properties

        public static final String DIAGRAM_TYPE = "diagram_type";

        // Quiz exercise properties

        public static final String QUIZ_MODE = "quiz_mode";

        public static final String QUIZ_DURATION = "quiz_duration";

        // File upload exercise properties

        public static final String FILE_PATTERN = "file_pattern";

        private Properties() {
        }
    }

    /**
     * Schema definition for the Exercises collection.
     * Type-specific properties are only populated for their respective exercise type.
     */
    public static final WeaviateCollectionSchema SCHEMA = WeaviateCollectionSchema.of(COLLECTION_NAME, List.of(
            // Shared properties
            filterable(Properties.EXERCISE_ID, INT, "The ID of the exercise"), filterable(Properties.COURSE_ID, INT, "The ID of the course"),
            searchable(Properties.COURSE_NAME, TEXT, "The name of the course"), searchable(Properties.TITLE, TEXT, "The title of the exercise"),
            searchable(Properties.SHORT_NAME, TEXT, "The short name of the exercise"), searchable(Properties.PROBLEM_STATEMENT, TEXT, "The problem statement of the exercise"),
            filterable(Properties.RELEASE_DATE, DATE, "The release date of the exercise"), nonSearchable(Properties.START_DATE, DATE, "The start date of the exercise"),
            nonSearchable(Properties.DUE_DATE, DATE, "The due date of the exercise"),
            filterable(Properties.EXERCISE_TYPE, TEXT, "The type of the exercise (programming, quiz, modeling, text, file-upload)"),
            filterable(Properties.DIFFICULTY, TEXT, "The difficulty level of the exercise"), nonSearchable(Properties.MAX_POINTS, NUMBER, "The maximum points for the exercise"),
            // Exam properties
            filterable(Properties.IS_EXAM_EXERCISE, BOOLEAN, "Whether this exercise belongs to an exam"),
            filterable(Properties.EXAM_ID, INT, "The ID of the exam (exam exercises only)"),
            filterable(Properties.EXAM_VISIBLE_DATE, DATE, "The visible date of the exam (exam exercises only)"),
            filterable(Properties.EXAM_START_DATE, DATE, "The start date of the exam (exam exercises only)"),
            filterable(Properties.EXAM_END_DATE, DATE, "The end date of the exam (exam exercises only)"),
            filterable(Properties.TEST_EXAM, BOOLEAN, "Whether this is a test exam (exam exercises only)"),
            // Programming exercise properties
            filterable(Properties.PROGRAMMING_LANGUAGE, TEXT, "The programming language (programming exercises only)"),
            filterable(Properties.PROJECT_TYPE, TEXT, "The project type (programming exercises only)"),
            // Modeling exercise properties
            filterable(Properties.DIAGRAM_TYPE, TEXT, "The diagram type (modeling exercises only)"),
            // Quiz exercise properties
            filterable(Properties.QUIZ_MODE, TEXT, "The quiz mode: SYNCHRONIZED, BATCHED, or INDIVIDUAL (quiz exercises only)"),
            nonSearchable(Properties.QUIZ_DURATION, INT, "The quiz duration in seconds (quiz exercises only)"),
            // File upload exercise properties
            filterable(Properties.FILE_PATTERN, TEXT, "The accepted file pattern (file upload exercises only)")));
}
