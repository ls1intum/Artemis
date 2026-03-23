package de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas;

import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.BOOLEAN;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.DATE;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.INT;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateDataType.TEXT;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.filterable;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.nonSearchable;
import static de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviatePropertyDefinition.searchable;

import java.util.List;

import de.tum.cit.aet.artemis.globalsearch.config.schema.WeaviateCollectionSchema;

/**
 * Schema definition for the Lectures Weaviate collection.
 * This stores lecture metadata for efficient search queries.
 */
public final class LectureSchema {

    /**
     * Utility class, should not be instantiated.
     */
    private LectureSchema() {
    }

    /**
     * Collection name for lecture metadata.
     */
    public static final String COLLECTION_NAME = "Lectures";

    /**
     * Property names for the Lectures collection.
     */
    public static final class Properties {

        public static final String LECTURE_ID = "lecture_id";

        public static final String COURSE_ID = "course_id";

        public static final String COURSE_NAME = "course_name";

        public static final String TITLE = "title";

        public static final String DESCRIPTION = "description";

        public static final String START_DATE = "start_date";

        public static final String END_DATE = "end_date";

        public static final String IS_TUTORIAL_LECTURE = "is_tutorial_lecture";

        private Properties() {
        }
    }

    /**
     * Schema definition for the Lectures collection.
     */
    public static final WeaviateCollectionSchema SCHEMA = WeaviateCollectionSchema.of(COLLECTION_NAME,
            List.of(filterable(Properties.LECTURE_ID, INT, "The ID of the lecture"), filterable(Properties.COURSE_ID, INT, "The ID of the course"),
                    searchable(Properties.COURSE_NAME, TEXT, "The name of the course"), searchable(Properties.TITLE, TEXT, "The title of the lecture"),
                    searchable(Properties.DESCRIPTION, TEXT, "The description of the lecture"), nonSearchable(Properties.START_DATE, DATE, "The start date of the lecture"),
                    nonSearchable(Properties.END_DATE, DATE, "The end date of the lecture"),
                    filterable(Properties.IS_TUTORIAL_LECTURE, BOOLEAN, "Whether this is a tutorial lecture")));
}
