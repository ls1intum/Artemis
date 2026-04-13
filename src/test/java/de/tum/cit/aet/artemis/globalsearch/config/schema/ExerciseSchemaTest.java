package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableItemSchema;

class ExerciseSchemaTest {

    private static final WeaviateCollectionSchema SCHEMA = SearchableItemSchema.SCHEMA;

    @Test
    void schema_hasCorrectCollectionName() {
        assertThat(SCHEMA.collectionName()).isEqualTo("SearchableItems");
    }

    @Test
    void schema_containsCommonProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.ENTITY_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableItemSchema.Properties.COURSE_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableItemSchema.Properties.TITLE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.DESCRIPTION, WeaviateDataType.TEXT);
    }

    @Test
    void schema_containsExerciseProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.SHORT_NAME, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.RELEASE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.DUE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.EXERCISE_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.DIFFICULTY, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.MAX_POINTS, WeaviateDataType.NUMBER);
    }

    @Test
    void schema_containsExamProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.IS_EXAM_EXERCISE, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableItemSchema.Properties.EXAM_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableItemSchema.Properties.EXAM_VISIBLE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.EXAM_START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.EXAM_END_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableItemSchema.Properties.TEST_EXAM, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableItemSchema.Properties.VISIBLE_DATE, WeaviateDataType.DATE);
    }

    @Test
    void schema_containsTypeSpecificProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.PROGRAMMING_LANGUAGE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.PROJECT_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.DIAGRAM_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.QUIZ_MODE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.QUIZ_DURATION, WeaviateDataType.INT);
        assertPropertyExists(SearchableItemSchema.Properties.FILE_PATTERN, WeaviateDataType.TEXT);
    }

    @Test
    void schema_containsLectureUnitProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.LECTURE_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableItemSchema.Properties.UNIT_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.UNIT_VISIBLE, WeaviateDataType.BOOLEAN);
    }

    @Test
    void schema_containsFaqAndChannelProperties() {
        assertPropertyExists(SearchableItemSchema.Properties.FAQ_STATE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableItemSchema.Properties.CHANNEL_IS_COURSE_WIDE, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableItemSchema.Properties.CHANNEL_IS_PUBLIC, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableItemSchema.Properties.CHANNEL_IS_ARCHIVED, WeaviateDataType.BOOLEAN);
    }

    @Test
    void schema_searchablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.TITLE).orElseThrow().indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.DESCRIPTION).orElseThrow().indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.SHORT_NAME).orElseThrow().indexSearchable()).isTrue();
    }

    @Test
    void schema_filterablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.TYPE).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.ENTITY_ID).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.COURSE_ID).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.EXERCISE_TYPE).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableItemSchema.Properties.IS_EXAM_EXERCISE).orElseThrow().indexFilterable()).isTrue();
    }

    @Test
    void schema_hasNoReferences() {
        assertThat(SCHEMA.references()).isEmpty();
    }

    private void assertPropertyExists(String propertyName, WeaviateDataType expectedType) {
        var property = SCHEMA.getProperty(propertyName);
        assertThat(property).as("Property '%s' should exist", propertyName).isPresent();
        assertThat(property.orElseThrow().dataType()).as("Property '%s' data type", propertyName).isEqualTo(expectedType);
    }
}
