package de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas;

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
 * Unified schema definition for the {@code SearchableItems} Weaviate collection.
 * <p>
 * A single collection backs global search across all entity types (exercises, lectures, lecture units,
 * exams, FAQs, channels). Each row carries a {@link Properties#TYPE} discriminator so per-type access
 * rules can be expressed as compound disjuncts within one Weaviate request.
 * <p>
 * Sparse/type-specific properties are only populated for the relevant entity type and left absent for
 * others. Weaviate's null-aware inverted index (enabled in {@code WeaviateService.ensureCollectionExists})
 * makes this wide, optional schema cheap.
 */
public final class SearchableItemSchema {

    /**
     * Utility class, should not be instantiated.
     */
    private SearchableItemSchema() {
    }

    /**
     * Collection name for the unified searchable items collection.
     */
    public static final String COLLECTION_NAME = "SearchableItems";

    /**
     * Canonical string values for the {@code type} discriminator.
     * Kept as string constants (not an enum) because Weaviate stores and filters on raw text values.
     */
    public static final class TypeValues {

        public static final String EXERCISE = "exercise";

        public static final String LECTURE = "lecture";

        public static final String LECTURE_UNIT = "lecture_unit";

        public static final String EXAM = "exam";

        public static final String FAQ = "faq";

        public static final String CHANNEL = "channel";

        private TypeValues() {
        }
    }

    /**
     * Property names for the {@link #COLLECTION_NAME} collection.
     */
    public static final class Properties {

        // Common properties (always present)

        public static final String TYPE = "type";

        public static final String ENTITY_ID = "entity_id";

        public static final String COURSE_ID = "course_id";

        public static final String TITLE = "title";

        public static final String DESCRIPTION = "description";

        // Access-control / sparse filter properties

        public static final String RELEASE_DATE = "release_date";

        public static final String IS_EXAM_EXERCISE = "is_exam_exercise";

        public static final String EXAM_VISIBLE_DATE = "exam_visible_date";

        public static final String EXAM_START_DATE = "exam_start_date";

        public static final String EXAM_END_DATE = "exam_end_date";

        public static final String TEST_EXAM = "test_exam";

        public static final String EXAM_ID = "exam_id";

        /**
         * For type {@link TypeValues#EXAM} only: the exam's own visible date (student access gate).
         * Distinct from {@link #EXAM_VISIBLE_DATE}, which is denormalized onto each exam exercise.
         */
        public static final String VISIBLE_DATE = "visible_date";

        /**
         * For type {@link TypeValues#LECTURE_UNIT} only: pre-computed student visibility, derived at
         * upsert time from the unit's release date.
         */
        public static final String UNIT_VISIBLE = "unit_visible";

        public static final String FAQ_STATE = "faq_state";

        public static final String CHANNEL_IS_COURSE_WIDE = "channel_is_course_wide";

        public static final String CHANNEL_IS_PUBLIC = "channel_is_public";

        public static final String CHANNEL_IS_ARCHIVED = "channel_is_archived";

        // Type-specific display / metadata fields

        public static final String SHORT_NAME = "short_name";

        public static final String EXERCISE_TYPE = "exercise_type";

        public static final String DIFFICULTY = "difficulty";

        public static final String MAX_POINTS = "max_points";

        public static final String PROGRAMMING_LANGUAGE = "programming_language";

        public static final String PROJECT_TYPE = "project_type";

        public static final String DIAGRAM_TYPE = "diagram_type";

        public static final String QUIZ_MODE = "quiz_mode";

        public static final String QUIZ_DURATION = "quiz_duration";

        public static final String FILE_PATTERN = "file_pattern";

        public static final String START_DATE = "start_date";

        public static final String END_DATE = "end_date";

        public static final String DUE_DATE = "due_date";

        public static final String LECTURE_ID = "lecture_id";

        public static final String UNIT_TYPE = "unit_type";

        private Properties() {
        }
    }

    /**
     * Schema definition for the unified {@code SearchableItems} collection.
     * <p>
     * Only {@link Properties#TITLE}, {@link Properties#SHORT_NAME} and {@link Properties#DESCRIPTION} are
     * BM25-searchable — all other properties are either filterable (used in the compound access filter)
     * or non-searchable (display-only, stored but not indexed). Structured metadata fields are never
     * matched by free-text queries.
     */
    public static final WeaviateCollectionSchema SCHEMA = WeaviateCollectionSchema.of(COLLECTION_NAME, List.of(
            // Common fields
            filterable(Properties.TYPE, TEXT, "The entity type discriminator (exercise, lecture, lecture_unit, exam, faq, channel)"),
            filterable(Properties.ENTITY_ID, INT, "The database ID of the underlying entity (unique within type)"),
            filterable(Properties.COURSE_ID, INT, "The ID of the course the entity belongs to"), searchable(Properties.TITLE, TEXT, "The canonical title/name of the entity"),
            searchable(Properties.DESCRIPTION, TEXT, "The body text of the entity (problem statement / description / content / answer)"),

            // Access-control / sparse filter fields
            filterable(Properties.RELEASE_DATE, DATE, "The release date (exercises and lecture units)"),
            filterable(Properties.IS_EXAM_EXERCISE, BOOLEAN, "Whether this exercise belongs to an exam"),
            filterable(Properties.EXAM_VISIBLE_DATE, DATE, "The parent exam's visible date (denormalized onto exam exercises)"),
            filterable(Properties.EXAM_START_DATE, DATE, "The parent exam's start date (denormalized onto exam exercises)"),
            filterable(Properties.EXAM_END_DATE, DATE, "The parent exam's end date (denormalized onto exam exercises)"),
            filterable(Properties.TEST_EXAM, BOOLEAN, "Whether this is a test exam (exams and exam exercises)"),
            filterable(Properties.EXAM_ID, INT, "The ID of the parent exam (exam exercises only)"),
            filterable(Properties.VISIBLE_DATE, DATE, "The exam's own visible date (exam rows only)"),
            filterable(Properties.UNIT_VISIBLE, BOOLEAN, "Pre-computed visibility of a lecture unit to students (lecture_unit rows only)"),
            filterable(Properties.FAQ_STATE, TEXT, "The state of the FAQ: ACCEPTED, REJECTED, or PROPOSED (faq rows only)"),
            filterable(Properties.CHANNEL_IS_COURSE_WIDE, BOOLEAN, "Whether the channel is course-wide (channel rows only)"),
            filterable(Properties.CHANNEL_IS_PUBLIC, BOOLEAN, "Whether the channel is public (channel rows only)"),
            filterable(Properties.CHANNEL_IS_ARCHIVED, BOOLEAN, "Whether the channel is archived (channel rows only)"),

            // Type-specific display / metadata fields
            searchable(Properties.SHORT_NAME, TEXT, "The short name of the exercise (exercises only)"),
            filterable(Properties.EXERCISE_TYPE, TEXT, "The type of exercise: programming, quiz, modeling, text, file-upload (exercises only)"),
            nonSearchable(Properties.DIFFICULTY, TEXT, "The difficulty level of the exercise (exercises only)"),
            nonSearchable(Properties.MAX_POINTS, NUMBER, "The maximum points for the exercise (exercises only)"),
            nonSearchable(Properties.PROGRAMMING_LANGUAGE, TEXT, "The programming language (programming exercises only)"),
            nonSearchable(Properties.PROJECT_TYPE, TEXT, "The project type (programming exercises only)"),
            nonSearchable(Properties.DIAGRAM_TYPE, TEXT, "The diagram type (modeling exercises only)"),
            nonSearchable(Properties.QUIZ_MODE, TEXT, "The quiz mode: SYNCHRONIZED, BATCHED, INDIVIDUAL (quiz exercises only)"),
            nonSearchable(Properties.QUIZ_DURATION, INT, "The quiz duration in seconds (quiz exercises only)"),
            nonSearchable(Properties.FILE_PATTERN, TEXT, "The accepted file pattern (file upload exercises only)"),
            nonSearchable(Properties.START_DATE, DATE, "The start date (exercises, lectures, exams)"), nonSearchable(Properties.END_DATE, DATE, "The end date (lectures, exams)"),
            nonSearchable(Properties.DUE_DATE, DATE, "The due date (exercises only)"),
            filterable(Properties.LECTURE_ID, INT, "The ID of the parent lecture (lecture_unit rows only, used for bulk delete on lecture deletion)"),
            nonSearchable(Properties.UNIT_TYPE, TEXT, "The lecture unit type: text, attachment_video, online (lecture_unit rows only)")));
}
