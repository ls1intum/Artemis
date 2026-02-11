package de.tum.cit.aet.artemis.globalsearch.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Utility class for Weaviate-related assertions in integration tests.
 * Provides helper methods to query Weaviate and verify that exercise metadata
 * was correctly stored, updated, or deleted.
 */
public final class WeaviateTestUtil {

    private WeaviateTestUtil() {
        // Utility class
    }

    /**
     * Queries Weaviate for the exercise with the given ID and returns its properties,
     * or {@code null} if no exercise was found.
     * Fails the test if the Weaviate service is not available.
     *
     * @param weaviateService the Weaviate service to query
     * @param exerciseId      the ID of the exercise to look up
     * @return the exercise properties map, or {@code null} if not found
     */
    public static Map<String, Object> queryExerciseProperties(WeaviateService weaviateService, long exerciseId) throws Exception {
        assertThat(weaviateService).as("WeaviateService must not be null — is the Weaviate Testcontainer running?").isNotNull();
        var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);
        var response = collection.query.fetchObjects(q -> q.filters(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId)).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the exercise exists in Weaviate and its core properties match the given exercise.
     *
     * @param weaviateService the Weaviate service to query
     * @param exercise        the exercise whose metadata should be verified in Weaviate
     */
    public static void assertExerciseExistsInWeaviate(WeaviateService weaviateService, Exercise exercise) throws Exception {
        var properties = queryExerciseProperties(weaviateService, exercise.getId());
        assertThat(properties).as("Exercise %d should exist in Weaviate", exercise.getId()).isNotNull();

        assertThat(properties.get(ExerciseSchema.Properties.TITLE)).isEqualTo(exercise.getTitle());
        assertThat(properties.get(ExerciseSchema.Properties.EXERCISE_TYPE)).isEqualTo(exercise.getType());
        assertThat(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue()).isEqualTo(exercise.getId());

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        assertThat(((Number) properties.get(ExerciseSchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());
    }

    /**
     * Asserts that the exercise exists in Weaviate and verifies programming-specific properties.
     *
     * @param weaviateService     the Weaviate service to query
     * @param programmingExercise the programming exercise whose metadata should be verified in Weaviate
     */
    public static void assertProgrammingExerciseExistsInWeaviate(WeaviateService weaviateService, ProgrammingExercise programmingExercise) throws Exception {
        assertExerciseExistsInWeaviate(weaviateService, programmingExercise);

        var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
        if (programmingExercise.getProgrammingLanguage() != null) {
            assertThat(properties.get(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo(programmingExercise.getProgrammingLanguage().name());
        }
        if (programmingExercise.getProjectType() != null) {
            assertThat(properties.get(ExerciseSchema.Properties.PROJECT_TYPE)).isEqualTo(programmingExercise.getProjectType().name());
        }
    }

    /**
     * Asserts that no exercise with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query
     * @param exerciseId      the ID of the exercise that should not exist
     */
    public static void assertExerciseNotInWeaviate(WeaviateService weaviateService, long exerciseId) throws Exception {
        var properties = queryExerciseProperties(weaviateService, exerciseId);
        assertThat(properties).as("Exercise %d should not exist in Weaviate", exerciseId).isNull();
    }
}
