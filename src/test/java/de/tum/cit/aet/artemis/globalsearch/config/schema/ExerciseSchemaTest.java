package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

class ExerciseSchemaTest {

    private static final WeaviateCollectionSchema SCHEMA = SearchableEntitySchema.SCHEMA;

    @Test
    void schema_hasCorrectCollectionName() {
        assertThat(SCHEMA.collectionName()).isEqualTo("SearchableItems");
    }

    @Test
    void schema_containsCommonProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.ENTITY_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableEntitySchema.Properties.COURSE_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableEntitySchema.Properties.TITLE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.DESCRIPTION, WeaviateDataType.TEXT);
    }

    @Test
    void schema_containsExerciseProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.SHORT_NAME, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.RELEASE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.DUE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.EXERCISE_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.DIFFICULTY, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.MAX_POINTS, WeaviateDataType.NUMBER);
    }

    @Test
    void schema_containsExamProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableEntitySchema.Properties.EXAM_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.EXAM_START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.EXAM_END_DATE, WeaviateDataType.DATE);
        assertPropertyExists(SearchableEntitySchema.Properties.TEST_EXAM, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableEntitySchema.Properties.VISIBLE_DATE, WeaviateDataType.DATE);
    }

    @Test
    void schema_containsTypeSpecificProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.PROJECT_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.DIAGRAM_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.QUIZ_MODE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.QUIZ_DURATION, WeaviateDataType.INT);
        assertPropertyExists(SearchableEntitySchema.Properties.FILE_PATTERN, WeaviateDataType.TEXT);
    }

    @Test
    void schema_containsLectureUnitProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.LECTURE_ID, WeaviateDataType.INT);
        assertPropertyExists(SearchableEntitySchema.Properties.UNIT_TYPE, WeaviateDataType.TEXT);
    }

    @Test
    void schema_containsFaqAndChannelProperties() {
        assertPropertyExists(SearchableEntitySchema.Properties.FAQ_STATE, WeaviateDataType.TEXT);
        assertPropertyExists(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE, WeaviateDataType.BOOLEAN);
        assertPropertyExists(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC, WeaviateDataType.BOOLEAN);
    }

    @Test
    void schema_searchablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.TITLE).orElseThrow().indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.DESCRIPTION).orElseThrow().indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.SHORT_NAME).orElseThrow().indexSearchable()).isTrue();
    }

    @Test
    void schema_filterablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.TYPE).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.ENTITY_ID).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.COURSE_ID).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.EXERCISE_TYPE).orElseThrow().indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).orElseThrow().indexFilterable()).isTrue();
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
