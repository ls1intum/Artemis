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
        // Utility class
    }

    // ==================== Collection Names ====================

    /**
     * Collection name for lecture page chunks (slides content).
     * Iris file: lecture_unit_page_chunk_schema.py
     */
    public static final String LECTURES_COLLECTION = "Lectures";

    /**
     * Collection name for lecture transcriptions.
     * Iris file: lecture_transcription_schema.py
     */
    public static final String LECTURE_TRANSCRIPTIONS_COLLECTION = "LectureTranscriptions";

    /**
     * Collection name for lecture unit segments.
     * Iris file: lecture_unit_segment_schema.py
     */
    public static final String LECTURE_UNIT_SEGMENTS_COLLECTION = "LectureUnitSegments";

    /**
     * Collection name for lecture units.
     * Iris file: lecture_unit_schema.py
     */
    public static final String LECTURE_UNITS_COLLECTION = "LectureUnits";

    /**
     * Collection name for FAQs.
     * Iris file: faq_schema.py
     */
    public static final String FAQS_COLLECTION = "Faqs";

    // ==================== Lectures (LectureUnitPageChunk) Schema ====================

    /**
     * Property names for the Lectures collection.
     * These must match the property names defined in lecture_unit_page_chunk_schema.py
     */
    public static final class LecturesProperties {

        public static final String COURSE_ID = "course_id";

        public static final String COURSE_LANGUAGE = "course_language";

        public static final String LECTURE_ID = "lecture_id";

        public static final String LECTURE_UNIT_ID = "lecture_unit_id";

        public static final String PAGE_TEXT_CONTENT = "page_text_content";

        public static final String PAGE_NUMBER = "page_number";

        public static final String BASE_URL = "base_url";

        public static final String ATTACHMENT_VERSION = "attachment_version";

        private LecturesProperties() {
        }
    }

    /**
     * Schema definition for the Lectures collection.
     * This stores lecture slide content for BM25 and hybrid search.
     */
    public static final WeaviateCollectionSchema LECTURES_SCHEMA = WeaviateCollectionSchema.of(LECTURES_COLLECTION,
            List.of(nonSearchable(LecturesProperties.COURSE_ID, INT, "The ID of the course"), nonSearchable(LecturesProperties.COURSE_LANGUAGE, TEXT, "The language of the course"),
                    nonSearchable(LecturesProperties.LECTURE_ID, INT, "The ID of the lecture"),
                    nonSearchable(LecturesProperties.LECTURE_UNIT_ID, INT, "The ID of the lecture unit"),
                    searchable(LecturesProperties.PAGE_TEXT_CONTENT, TEXT, "The content of the lecture slide"),
                    nonSearchable(LecturesProperties.PAGE_NUMBER, INT, "The page number of the slide"),
                    nonSearchable(LecturesProperties.BASE_URL, TEXT, "The base url of the website where the lecture unit is hosted"),
                    nonSearchable(LecturesProperties.ATTACHMENT_VERSION, INT, "The version of the page")));

    // ==================== LectureTranscriptions Schema ====================

    /**
     * Property names for the LectureTranscriptions collection.
     * These must match the property names defined in lecture_transcription_schema.py
     */
    public static final class LectureTranscriptionsProperties {

        public static final String COURSE_ID = "course_id";

        public static final String LECTURE_ID = "lecture_id";

        public static final String LECTURE_UNIT_ID = "lecture_unit_id";

        public static final String LANGUAGE = "language";

        public static final String SEGMENT_START_TIME = "segment_start_time";

        public static final String SEGMENT_END_TIME = "segment_end_time";

        public static final String PAGE_NUMBER = "page_number";

        public static final String SEGMENT_TEXT = "segment_text";

        public static final String SEGMENT_SUMMARY = "segment_summary";

        public static final String BASE_URL = "base_url";

        private LectureTranscriptionsProperties() {
        }
    }

    /**
     * Schema definition for the LectureTranscriptions collection.
     * This stores lecture video transcription segments for search.
     */
    public static final WeaviateCollectionSchema LECTURE_TRANSCRIPTIONS_SCHEMA = WeaviateCollectionSchema.of(LECTURE_TRANSCRIPTIONS_COLLECTION,
            List.of(nonSearchable(LectureTranscriptionsProperties.COURSE_ID, INT, "The ID of the course"),
                    nonSearchable(LectureTranscriptionsProperties.LECTURE_ID, INT, "The ID of the lecture"),
                    nonSearchable(LectureTranscriptionsProperties.LECTURE_UNIT_ID, INT, "The id of the lecture unit of the transcription"),
                    nonSearchable(LectureTranscriptionsProperties.LANGUAGE, TEXT, "The language of the text"),
                    nonSearchable(LectureTranscriptionsProperties.SEGMENT_START_TIME, NUMBER, "The start time of the segment"),
                    nonSearchable(LectureTranscriptionsProperties.SEGMENT_END_TIME, NUMBER, "The end time of the segment"),
                    nonSearchable(LectureTranscriptionsProperties.PAGE_NUMBER, INT, "The page number of the lecture unit of the segment"),
                    searchable(LectureTranscriptionsProperties.SEGMENT_TEXT, TEXT, "The transcription of the segment"),
                    searchable(LectureTranscriptionsProperties.SEGMENT_SUMMARY, TEXT, "The summary of the text of the segment"),
                    nonSearchable(LectureTranscriptionsProperties.BASE_URL, TEXT, "The base url of the website where the lecture unit is hosted")));

    // ==================== LectureUnitSegments Schema ====================

    /**
     * Property names for the LectureUnitSegments collection.
     * These must match the property names defined in lecture_unit_segment_schema.py
     */
    public static final class LectureUnitSegmentsProperties {

        public static final String COURSE_ID = "course_id";

        public static final String LECTURE_ID = "lecture_id";

        public static final String LECTURE_UNIT_ID = "lecture_unit_id";

        public static final String PAGE_NUMBER = "page_number";

        public static final String SEGMENT_SUMMARY = "segment_summary";

        public static final String BASE_URL = "base_url";

        // References
        public static final String TRANSCRIPTIONS = "transcriptions";

        public static final String SLIDES = "slides";

        private LectureUnitSegmentsProperties() {
        }
    }

    /**
     * Schema definition for the LectureUnitSegments collection.
     * This stores combined segment summaries linking transcriptions and slides.
     */
    public static final WeaviateCollectionSchema LECTURE_UNIT_SEGMENTS_SCHEMA = WeaviateCollectionSchema.of(LECTURE_UNIT_SEGMENTS_COLLECTION,
            List.of(nonSearchable(LectureUnitSegmentsProperties.COURSE_ID, INT, "The ID of the course"),
                    nonSearchable(LectureUnitSegmentsProperties.LECTURE_ID, INT, "The ID of the lecture"),
                    nonSearchable(LectureUnitSegmentsProperties.LECTURE_UNIT_ID, INT, "The id of the lecture unit"),
                    nonSearchable(LectureUnitSegmentsProperties.PAGE_NUMBER, INT, "The page number of the lecture unit"),
                    searchable(LectureUnitSegmentsProperties.SEGMENT_SUMMARY, TEXT, "The summary of the transcription and the lecture content of the segment"),
                    nonSearchable(LectureUnitSegmentsProperties.BASE_URL, TEXT, "The base url of the website where the lecture unit is hosted")),
            List.of(WeaviateReferenceDefinition.of(LectureUnitSegmentsProperties.TRANSCRIPTIONS, LECTURE_TRANSCRIPTIONS_COLLECTION, "Reference to lecture transcriptions"),
                    WeaviateReferenceDefinition.of(LectureUnitSegmentsProperties.SLIDES, LECTURES_COLLECTION, "Reference to lecture slides")));

    // ==================== LectureUnits Schema ====================

    /**
     * Property names for the LectureUnits collection.
     * These must match the property names defined in lecture_unit_schema.py
     */
    public static final class LectureUnitsProperties {

        public static final String COURSE_ID = "course_id";

        public static final String COURSE_NAME = "course_name";

        public static final String COURSE_DESCRIPTION = "course_description";

        public static final String COURSE_LANGUAGE = "course_language";

        public static final String LECTURE_ID = "lecture_id";

        public static final String LECTURE_NAME = "lecture_name";

        public static final String LECTURE_UNIT_ID = "lecture_unit_id";

        public static final String LECTURE_UNIT_NAME = "lecture_unit_name";

        public static final String LECTURE_UNIT_LINK = "lecture_unit_link";

        public static final String VIDEO_LINK = "video_link";

        public static final String BASE_URL = "base_url";

        public static final String LECTURE_UNIT_SUMMARY = "lecture_unit_summary";

        private LectureUnitsProperties() {
        }
    }

    /**
     * Schema definition for the LectureUnits collection.
     * This stores lecture unit metadata and summaries.
     */
    public static final WeaviateCollectionSchema LECTURE_UNITS_SCHEMA = WeaviateCollectionSchema.of(LECTURE_UNITS_COLLECTION,
            List.of(nonSearchable(LectureUnitsProperties.COURSE_ID, INT, "The ID of the course"), nonSearchable(LectureUnitsProperties.COURSE_NAME, TEXT, "The name of the course"),
                    nonSearchable(LectureUnitsProperties.COURSE_DESCRIPTION, TEXT, "The description of the course"),
                    nonSearchable(LectureUnitsProperties.COURSE_LANGUAGE, TEXT, "The language of the course"),
                    nonSearchable(LectureUnitsProperties.LECTURE_ID, INT, "The ID of the lecture"),
                    nonSearchable(LectureUnitsProperties.LECTURE_NAME, TEXT, "The name of the lecture"),
                    nonSearchable(LectureUnitsProperties.LECTURE_UNIT_ID, INT, "The id of the lecture unit"),
                    nonSearchable(LectureUnitsProperties.LECTURE_UNIT_NAME, TEXT, "The name of the lecture unit"),
                    nonSearchable(LectureUnitsProperties.LECTURE_UNIT_LINK, TEXT, "The link to the lecture unit"),
                    nonSearchable(LectureUnitsProperties.VIDEO_LINK, TEXT, "The link to the video of the lecture unit"),
                    nonSearchable(LectureUnitsProperties.BASE_URL, TEXT, "The base url of the website where the lecture unit is hosted"),
                    searchable(LectureUnitsProperties.LECTURE_UNIT_SUMMARY, TEXT, "The summary of the lecture unit")));

    // ==================== FAQs Schema ====================

    /**
     * Property names for the Faqs collection.
     * These must match the property names defined in faq_schema.py
     */
    public static final class FaqsProperties {

        public static final String COURSE_ID = "course_id";

        public static final String COURSE_NAME = "course_name";

        public static final String COURSE_DESCRIPTION = "course_description";

        public static final String COURSE_LANGUAGE = "course_language";

        public static final String FAQ_ID = "faq_id";

        public static final String QUESTION_TITLE = "question_title";

        public static final String QUESTION_ANSWER = "question_answer";

        private FaqsProperties() {
        }
    }

    /**
     * Schema definition for the FAQs collection.
     * This stores FAQ entries for search.
     */
    public static final WeaviateCollectionSchema FAQS_SCHEMA = WeaviateCollectionSchema.of(FAQS_COLLECTION,
            List.of(filterable(FaqsProperties.COURSE_ID, INT, "The ID of the course"), nonSearchable(FaqsProperties.COURSE_NAME, TEXT, "The name of the course"),
                    nonSearchable(FaqsProperties.COURSE_DESCRIPTION, TEXT, "The description of the course"),
                    nonSearchable(FaqsProperties.COURSE_LANGUAGE, TEXT, "The language of the course"), filterable(FaqsProperties.FAQ_ID, INT, "The ID of the Faq"),
                    searchable(FaqsProperties.QUESTION_TITLE, TEXT, "The title of the faq"), searchable(FaqsProperties.QUESTION_ANSWER, TEXT, "The answer of the faq")));

    // ==================== Exercises Schema ====================

    /**
     * Collection name for exercises metadata.
     */
    public static final String EXERCISES_COLLECTION = "Exercises";

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
    public static final WeaviateCollectionSchema EXERCISES_SCHEMA = WeaviateCollectionSchema.of(EXERCISES_COLLECTION,
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
    public static final List<WeaviateCollectionSchema> ALL_SCHEMAS = List.of(LECTURES_SCHEMA, LECTURE_TRANSCRIPTIONS_SCHEMA, LECTURE_UNIT_SEGMENTS_SCHEMA, LECTURE_UNITS_SCHEMA,
            FAQS_SCHEMA, EXERCISES_SCHEMA);

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
