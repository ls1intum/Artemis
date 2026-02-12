package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;

class ExerciseSchemaTest {

    private static final WeaviateCollectionSchema SCHEMA = ExerciseSchema.SCHEMA;

    @Test
    void schema_hasCorrectCollectionName() {
        assertThat(SCHEMA.collectionName()).isEqualTo("Exercises");
    }

    @Test
    void schema_containsAllSharedProperties() {
        assertPropertyExists(ExerciseSchema.Properties.EXERCISE_ID, WeaviateDataType.INT);
        assertPropertyExists(ExerciseSchema.Properties.COURSE_ID, WeaviateDataType.INT);
        assertPropertyExists(ExerciseSchema.Properties.COURSE_NAME, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.TITLE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.SHORT_NAME, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.PROBLEM_STATEMENT, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.RELEASE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.DUE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.EXERCISE_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.DIFFICULTY, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.MAX_POINTS, WeaviateDataType.NUMBER);
    }

    @Test
    void schema_containsExamProperties() {
        assertPropertyExists(ExerciseSchema.Properties.IS_EXAM_EXERCISE, WeaviateDataType.BOOLEAN);
        assertPropertyExists(ExerciseSchema.Properties.EXAM_ID, WeaviateDataType.INT);
        assertPropertyExists(ExerciseSchema.Properties.EXAM_VISIBLE_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.EXAM_START_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.EXAM_END_DATE, WeaviateDataType.DATE);
        assertPropertyExists(ExerciseSchema.Properties.TEST_EXAM, WeaviateDataType.BOOLEAN);
    }

    @Test
    void schema_containsTypeSpecificProperties() {
        assertPropertyExists(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.PROJECT_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.DIAGRAM_TYPE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.QUIZ_MODE, WeaviateDataType.TEXT);
        assertPropertyExists(ExerciseSchema.Properties.QUIZ_DURATION, WeaviateDataType.INT);
        assertPropertyExists(ExerciseSchema.Properties.FILE_PATTERN, WeaviateDataType.TEXT);
    }

    @Test
    void schema_searchablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.TITLE).indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.COURSE_NAME).indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.PROBLEM_STATEMENT).indexSearchable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.SHORT_NAME).indexSearchable()).isTrue();
    }

    @Test
    void schema_filterablePropertiesAreCorrect() {
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.EXERCISE_ID).indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.COURSE_ID).indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.EXERCISE_TYPE).indexFilterable()).isTrue();
        assertThat(SCHEMA.getProperty(ExerciseSchema.Properties.IS_EXAM_EXERCISE).indexFilterable()).isTrue();
    }

    @Test
    void schema_hasNoReferences() {
        assertThat(SCHEMA.references()).isEmpty();
    }

    private void assertPropertyExists(String propertyName, WeaviateDataType expectedType) {
        WeaviatePropertyDefinition property = SCHEMA.getProperty(propertyName);
        assertThat(property).as("Property '%s' should exist", propertyName).isNotNull();
        assertThat(property.dataType()).as("Property '%s' data type", propertyName).isEqualTo(expectedType);
    }
}
