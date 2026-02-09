package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.deleteProgrammingExerciseParamsFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import de.tum.cit.aet.artemis.core.service.weaviate.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Endpoint-level integration tests verifying that REST operations on programming exercises
 * correctly synchronize metadata with Weaviate.
 * <p>
 * These tests call the actual REST endpoints (PUT, DELETE) and then query Weaviate to verify
 * that the exercise data was correctly updated or removed.
 */
class ExerciseWeaviateEndpointIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "exweaviateendpt";

    @Autowired
    private ExerciseWeaviateService exerciseWeaviateService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void checkWeaviateAvailable() {
        assumeTrue(exerciseWeaviateService.isWeaviateAvailable(), "Weaviate is not available (Docker not running?), skipping test");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateProgrammingExercise_shouldUpdateInWeaviate() throws Exception {
        // Pre-insert the exercise into Weaviate
        exerciseWeaviateService.insertExercise(programmingExercise);

        // Verify it exists in Weaviate
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        List<Map<String, Object>> beforeResults = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(beforeResults).isNotEmpty();

        // Update the exercise title via the REST endpoint
        String updatedTitle = "Weaviate Updated Title";
        programmingExercise.setTitle(updatedTitle);
        request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Query Weaviate and verify the updated title
        List<Map<String, Object>> afterResults = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(afterResults).hasSize(1);
        assertThat(afterResults.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, updatedTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExercise_shouldRemoveFromWeaviate() throws Exception {
        // Pre-insert the exercise into Weaviate
        exerciseWeaviateService.insertExercise(programmingExercise);

        // Verify it exists in Weaviate
        long courseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        List<Map<String, Object>> beforeResults = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(beforeResults).isNotEmpty();

        // Delete the exercise via the REST endpoint
        request.delete("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, deleteProgrammingExerciseParamsFalse());

        // Query Weaviate and verify the exercise is gone
        List<Map<String, Object>> afterResults = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(afterResults).isEmpty();
    }
}
