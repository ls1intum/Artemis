package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Integration tests for {@link ExerciseWeaviateService} using a real Weaviate Testcontainer.
 * <p>
 * These tests verify that exercise metadata is correctly stored, updated, and deleted
 * in Weaviate when the corresponding service methods are invoked.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class ExerciseWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exweaviateint";

    @Autowired
    private ExerciseWeaviateService exerciseWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInsertExercise_storesMetadataInWeaviate() throws Exception {
        exerciseWeaviateService.insertExercise(programmingExercise);

        var properties = queryExerciseProperties(programmingExercise.getId());

        assertThat(properties).isNotNull();
        assertThat(properties.get(ExerciseSchema.Properties.TITLE)).isEqualTo(programmingExercise.getTitle());
        assertThat(properties.get(ExerciseSchema.Properties.EXERCISE_TYPE)).isEqualTo("programming");
        assertThat(properties.get(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo(programmingExercise.getProgrammingLanguage().name());
        assertThat(((Number) properties.get(ExerciseSchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());
        assertThat(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue()).isEqualTo(programmingExercise.getId());
        assertThat(properties.get(ExerciseSchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(false);
        assertThat(((Number) properties.get(ExerciseSchema.Properties.MAX_POINTS)).doubleValue()).isEqualTo(programmingExercise.getMaxPoints());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExercise_updatesMetadataInWeaviate() throws Exception {
        exerciseWeaviateService.insertExercise(programmingExercise);

        // Modify exercise properties
        String updatedTitle = "Updated Weaviate Test Title";
        double updatedMaxPoints = 42.0;
        programmingExercise.setTitle(updatedTitle);
        programmingExercise.setMaxPoints(updatedMaxPoints);

        exerciseWeaviateService.updateExercise(programmingExercise);

        var properties = queryExerciseProperties(programmingExercise.getId());

        assertThat(properties).isNotNull();
        assertThat(properties.get(ExerciseSchema.Properties.TITLE)).isEqualTo(updatedTitle);
        assertThat(((Number) properties.get(ExerciseSchema.Properties.MAX_POINTS)).doubleValue()).isEqualTo(updatedMaxPoints);
        assertThat(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue()).isEqualTo(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExercise_removesMetadataFromWeaviate() throws Exception {
        exerciseWeaviateService.insertExercise(programmingExercise);

        // Verify the exercise exists in Weaviate before deletion
        var propertiesBefore = queryExerciseProperties(programmingExercise.getId());
        assertThat(propertiesBefore).isNotNull();

        exerciseWeaviateService.deleteExercise(programmingExercise.getId());

        var propertiesAfter = queryExerciseProperties(programmingExercise.getId());
        assertThat(propertiesAfter).isNull();
    }

    /**
     * Queries Weaviate for the exercise with the given ID and returns its properties,
     * or {@code null} if no exercise was found.
     */
    private Map<String, Object> queryExerciseProperties(long exerciseId) throws Exception {
        var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);
        var response = collection.query.fetchObjects(q -> q.filters(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId)).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }
}
