package de.tum.cit.aet.artemis.globalsearch.service.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Unit tests for the v0 → v1 migration property transformation logic.
 */
class V0ToV1MigrationTest {

    @Test
    void transformProperties_setsTypeToExercise() {
        Map<String, Object> oldProps = minimalV0Properties();

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps.get(SearchableEntitySchema.Properties.TYPE)).isEqualTo(SearchableEntitySchema.TypeValues.EXERCISE);
    }

    @Test
    void transformProperties_renamesExerciseIdToEntityId() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("exercise_id", 42L);

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps.get(SearchableEntitySchema.Properties.ENTITY_ID)).isEqualTo(42L);
        assertThat(newProps).doesNotContainKey("exercise_id");
    }

    @Test
    void transformProperties_renamesProblemStatementToDescription() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("problem_statement", "Write a function that...");

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("Write a function that...");
        assertThat(newProps).doesNotContainKey("problem_statement");
    }

    @Test
    void transformProperties_dropsCourseNameProperty() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("course_name", "Introduction to CS");

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps).doesNotContainKey("course_name");
    }

    @Test
    void transformProperties_copiesDirectlyMappedProperties() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("course_id", 10L);
        oldProps.put("title", "Hello World");
        oldProps.put("short_name", "HW");
        oldProps.put("exercise_type", "programming");
        oldProps.put("difficulty", "EASY");
        oldProps.put("max_points", 100.0);
        oldProps.put("programming_language", "JAVA");
        oldProps.put("project_type", "PLAIN_MAVEN");

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps.get(SearchableEntitySchema.Properties.COURSE_ID)).isEqualTo(10L);
        assertThat(newProps.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Hello World");
        assertThat(newProps.get(SearchableEntitySchema.Properties.SHORT_NAME)).isEqualTo("HW");
        assertThat(newProps.get(SearchableEntitySchema.Properties.EXERCISE_TYPE)).isEqualTo("programming");
        assertThat(newProps.get(SearchableEntitySchema.Properties.DIFFICULTY)).isEqualTo("EASY");
        assertThat(newProps.get(SearchableEntitySchema.Properties.MAX_POINTS)).isEqualTo(100.0);
        assertThat(newProps.get(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo("JAVA");
        assertThat(newProps.get(SearchableEntitySchema.Properties.PROJECT_TYPE)).isEqualTo("PLAIN_MAVEN");
    }

    @Test
    void transformProperties_copiesExamProperties() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("is_exam_exercise", true);
        oldProps.put("exam_id", 5L);
        oldProps.put("test_exam", false);
        oldProps.put("exam_visible_date", "2025-01-01T00:00:00Z");
        oldProps.put("exam_start_date", "2025-01-02T10:00:00Z");
        oldProps.put("exam_end_date", "2025-01-02T12:00:00Z");

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps.get(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(true);
        assertThat(newProps.get(SearchableEntitySchema.Properties.EXAM_ID)).isEqualTo(5L);
        assertThat(newProps.get(SearchableEntitySchema.Properties.TEST_EXAM)).isEqualTo(false);
        assertThat(newProps.get(SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE)).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(newProps.get(SearchableEntitySchema.Properties.EXAM_START_DATE)).isEqualTo("2025-01-02T10:00:00Z");
        assertThat(newProps.get(SearchableEntitySchema.Properties.EXAM_END_DATE)).isEqualTo("2025-01-02T12:00:00Z");
    }

    @Test
    void transformProperties_omitsNullValues() {
        Map<String, Object> oldProps = minimalV0Properties();
        oldProps.put("exercise_id", 1L);
        // Do not set optional fields like programming_language, diagram_type, etc.

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        assertThat(newProps).doesNotContainKey(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE);
        assertThat(newProps).doesNotContainKey(SearchableEntitySchema.Properties.DIAGRAM_TYPE);
        assertThat(newProps).doesNotContainKey(SearchableEntitySchema.Properties.QUIZ_MODE);
        // Values that are present should not be null
        assertThat(newProps.values()).doesNotContainNull();
    }

    @Test
    void transformProperties_handlesCompleteV0Exercise() {
        Map<String, Object> oldProps = new HashMap<>();
        oldProps.put("exercise_id", 99L);
        oldProps.put("course_id", 7L);
        oldProps.put("course_name", "Algorithms");
        oldProps.put("title", "Sorting");
        oldProps.put("short_name", "SO");
        oldProps.put("problem_statement", "Implement quicksort");
        oldProps.put("release_date", "2025-03-01T08:00:00Z");
        oldProps.put("start_date", "2025-03-01T08:00:00Z");
        oldProps.put("due_date", "2025-03-15T23:59:00Z");
        oldProps.put("exercise_type", "programming");
        oldProps.put("difficulty", "MEDIUM");
        oldProps.put("max_points", 50.0);
        oldProps.put("is_exam_exercise", false);
        oldProps.put("programming_language", "JAVA");
        oldProps.put("project_type", "PLAIN_GRADLE");

        Map<String, Object> newProps = V0ToV1Migration.transformProperties(oldProps);

        // Verify renamed fields
        assertThat(newProps.get(SearchableEntitySchema.Properties.ENTITY_ID)).isEqualTo(99L);
        assertThat(newProps.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("Implement quicksort");
        // Verify added fields
        assertThat(newProps.get(SearchableEntitySchema.Properties.TYPE)).isEqualTo(SearchableEntitySchema.TypeValues.EXERCISE);
        // Verify dropped fields
        assertThat(newProps).doesNotContainKey("course_name");
        assertThat(newProps).doesNotContainKey("exercise_id");
        assertThat(newProps).doesNotContainKey("problem_statement");
        // Verify all values are non-null
        assertThat(newProps.values()).doesNotContainNull();
    }

    @Test
    void targetVersion_isOne() {
        var migration = new V0ToV1Migration();
        assertThat(migration.targetVersion()).isEqualTo(1);
    }

    @Test
    void description_isNotEmpty() {
        var migration = new V0ToV1Migration();
        assertThat(migration.description()).isNotBlank();
    }

    /**
     * Returns a minimal v0 property map (only required fields set).
     */
    private static Map<String, Object> minimalV0Properties() {
        Map<String, Object> props = new HashMap<>();
        props.put("exercise_id", 1L);
        props.put("course_id", 1L);
        props.put("title", "Test Exercise");
        return props;
    }
}
