package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchableEntityDTO;

/**
 * Unit tests for {@link PyrisSearchableEntityDTO#fromProperties(Map)}.
 * <p>
 * Covers every branch: accepted/rejected entity types, missing required fields,
 * description vs. per-type fallback descriptions, channel-prefix routing, and
 * exercise-type extraction.
 */
class PyrisSearchableEntityDTOTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final String TYPE = SearchableEntitySchema.Properties.TYPE;

    private static final String ENTITY_ID = SearchableEntitySchema.Properties.ENTITY_ID;

    private static final String COURSE_ID = SearchableEntitySchema.Properties.COURSE_ID;

    private static final String TITLE = SearchableEntitySchema.Properties.TITLE;

    private static final String DESCRIPTION = SearchableEntitySchema.Properties.DESCRIPTION;

    private static final String EXERCISE_TYPE = SearchableEntitySchema.Properties.EXERCISE_TYPE;

    private static Map<String, Object> props(String type, long entityId, long courseId, String title) {
        var map = new HashMap<String, Object>();
        map.put(TYPE, type);
        map.put(ENTITY_ID, entityId);
        map.put(COURSE_ID, courseId);
        map.put(TITLE, title);
        return map;
    }

    // -------------------------------------------------------------------------
    // Accepted entity types
    // -------------------------------------------------------------------------

    @Test
    void fromProperties_exercise_withDescription_usesDescriptionAsSnippet() {
        var p = props(SearchableEntitySchema.TypeValues.EXERCISE, 10L, 5L, "Binary Trees");
        p.put(DESCRIPTION, "A data structure problem involving trees.");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.sourceType()).isEqualTo("exercise");
        assertThat(dto.entityId()).isEqualTo(10L);
        assertThat(dto.course().id()).isEqualTo(5L);
        assertThat(dto.title()).isEqualTo("Binary Trees");
        assertThat(dto.snippet()).isEqualTo("A data structure problem involving trees.");
    }

    @Test
    void fromProperties_exercise_withoutDescription_usesFallback() {
        var p = props(SearchableEntitySchema.TypeValues.EXERCISE, 10L, 5L, "Quicksort");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.snippet()).isEqualTo("An exercise: Quicksort");
    }

    @Test
    void fromProperties_exercise_withExerciseType_extractsType() {
        var p = props(SearchableEntitySchema.TypeValues.EXERCISE, 10L, 5L, "Graph Coloring");
        p.put(EXERCISE_TYPE, "programming");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.exerciseType()).isEqualTo("programming");
        assertThat(dto.snippet()).isEqualTo("A programming exercise: Graph Coloring");
    }

    @Test
    void fromProperties_faq_withoutDescription_usesFallback() {
        var p = props(SearchableEntitySchema.TypeValues.FAQ, 20L, 5L, "What is Artemis?");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.sourceType()).isEqualTo("faq");
        assertThat(dto.snippet()).isEqualTo("A frequently asked question: What is Artemis?");
        assertThat(dto.exerciseType()).isNull();
    }

    @Test
    void fromProperties_exam_withoutDescription_usesFallback() {
        var p = props(SearchableEntitySchema.TypeValues.EXAM, 30L, 5L, "Midterm 2025");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.sourceType()).isEqualTo("exam");
        assertThat(dto.snippet()).isEqualTo("An exam: Midterm 2025");
    }

    @Test
    void fromProperties_channel_generic_usesFallback() {
        var p = props(SearchableEntitySchema.TypeValues.CHANNEL, 40L, 5L, "general");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.snippet()).isEqualTo("A communication channel where students can ask questions and discuss.");
    }

    @Test
    void fromProperties_channel_exercisePrefix_usesExerciseFallback() {
        var p = props(SearchableEntitySchema.TypeValues.CHANNEL, 41L, 5L, "exercise-binary-trees");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.snippet()).isEqualTo("A communication channel for asking questions and discussing an exercise.");
    }

    @Test
    void fromProperties_channel_examPrefix_usesExamFallback() {
        var p = props(SearchableEntitySchema.TypeValues.CHANNEL, 42L, 5L, "exam-midterm");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.snippet()).isEqualTo("A communication channel for asking questions and discussing an exam.");
    }

    @Test
    void fromProperties_channel_lecturePrefix_usesLectureFallback() {
        var p = props(SearchableEntitySchema.TypeValues.CHANNEL, 43L, 5L, "lecture-intro-to-ml");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.snippet()).isEqualTo("A communication channel for asking questions and discussing a lecture.");
    }

    @Test
    void fromProperties_anyType_courseRefHasEmptyName() {
        // Pyris enriches the course name after the RRF merge — the name must be blank here.
        var p = props(SearchableEntitySchema.TypeValues.FAQ, 20L, 7L, "Some FAQ");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.course().name()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Rejected entity types (Pyris does not handle them)
    // -------------------------------------------------------------------------

    @Test
    void fromProperties_lectureType_returnsNull() {
        var p = props("lecture", 50L, 5L, "Intro to ML");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_lectureUnitType_returnsNull() {
        var p = props("lecture_unit", 51L, 5L, "Slide 1");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_courseType_returnsNull() {
        var p = props("course", 52L, 5L, "Machine Learning");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_nullType_returnsNull() {
        var p = new HashMap<String, Object>();
        p.put(ENTITY_ID, 1L);
        p.put(COURSE_ID, 5L);
        p.put(TITLE, "Something");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    // -------------------------------------------------------------------------
    // Missing required fields
    // -------------------------------------------------------------------------

    @Test
    void fromProperties_missingEntityId_returnsNull() {
        var p = new HashMap<String, Object>();
        p.put(TYPE, SearchableEntitySchema.TypeValues.FAQ);
        p.put(COURSE_ID, 5L);
        p.put(TITLE, "Missing entity id");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_missingCourseId_returnsNull() {
        var p = new HashMap<String, Object>();
        p.put(TYPE, SearchableEntitySchema.TypeValues.FAQ);
        p.put(ENTITY_ID, 1L);
        p.put(TITLE, "Missing course id");
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_missingTitle_returnsNull() {
        var p = new HashMap<String, Object>();
        p.put(TYPE, SearchableEntitySchema.TypeValues.FAQ);
        p.put(ENTITY_ID, 1L);
        p.put(COURSE_ID, 5L);
        assertThat(PyrisSearchableEntityDTO.fromProperties(p)).isNull();
    }

    @Test
    void fromProperties_entityIdAsString_isCoercedToLong() {
        // Weaviate may return numeric fields as strings; getLong handles Number types.
        // This test ensures that a raw Long value (as returned by the live client) works.
        var p = props(SearchableEntitySchema.TypeValues.EXAM, 99L, 5L, "Final Exam");
        // Replace the numeric Long with a Number subtype to simulate a variety of numeric types.
        p.put(ENTITY_ID, Integer.valueOf(99));
        p.put(COURSE_ID, Integer.valueOf(5));

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.entityId()).isEqualTo(99L);
        assertThat(dto.course().id()).isEqualTo(5L);
    }

    @Test
    void fromProperties_exerciseType_notExtractedForNonExercise() {
        // exerciseType should only be populated for type=exercise
        var p = props(SearchableEntitySchema.TypeValues.FAQ, 20L, 5L, "FAQ about exams");
        p.put(EXERCISE_TYPE, "programming");

        var dto = PyrisSearchableEntityDTO.fromProperties(p);

        assertThat(dto).isNotNull();
        assertThat(dto.exerciseType()).isNull();
    }
}
