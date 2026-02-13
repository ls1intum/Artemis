package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryExerciseProperties;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

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

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Nested
    class ExerciseWeaviateServiceTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testInsertExercise_storesMetadataInWeaviate() throws Exception {
            exerciseWeaviateService.insertExercise(programmingExercise);

            assertExerciseExistsInWeaviate(weaviateService, programmingExercise);

            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
            assertThat(properties.get(ExerciseSchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo(programmingExercise.getProgrammingLanguage().name());
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

            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());

            assertThat(properties).isNotNull();
            assertThat(properties.get(ExerciseSchema.Properties.TITLE)).isEqualTo(updatedTitle);
            assertThat(((Number) properties.get(ExerciseSchema.Properties.MAX_POINTS)).doubleValue()).isEqualTo(updatedMaxPoints);
            assertThat(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue()).isEqualTo(programmingExercise.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteExercise_removesMetadataFromWeaviate() throws Exception {
            exerciseWeaviateService.insertExercise(programmingExercise);

            assertExerciseExistsInWeaviate(weaviateService, programmingExercise);

            exerciseWeaviateService.deleteExercise(programmingExercise.getId());

            assertExerciseNotInWeaviate(weaviateService, programmingExercise.getId());
        }
    }

    @Nested
    class ProgrammingExercisePartialUpdateWeaviateTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateProblemStatement_updatesWeaviate() throws Exception {
            // Insert exercise into Weaviate first
            exerciseWeaviateService.insertExercise(programmingExercise);
            assertExerciseExistsInWeaviate(weaviateService, programmingExercise);

            // Update problem statement via endpoint
            final var newProblem = "updated problem statement for weaviate test";
            final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
            request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

            // Verify Weaviate has the updated problem statement
            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
            assertThat(properties).isNotNull();
            assertThat(properties.get(ExerciseSchema.Properties.PROBLEM_STATEMENT)).isEqualTo(newProblem);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateTimeline_updatesWeaviate() throws Exception {
            // Insert exercise into Weaviate first
            exerciseWeaviateService.insertExercise(programmingExercise);
            assertExerciseExistsInWeaviate(weaviateService, programmingExercise);

            // Update timeline via endpoint
            var exerciseForUpdate = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
            ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(7);
            exerciseForUpdate.setDueDate(newDueDate);

            final var endpoint = "/api/programming/programming-exercises/timeline";
            request.putWithResponseBody(endpoint, exerciseForUpdate, ProgrammingExercise.class, HttpStatus.OK);

            // Verify Weaviate has the updated due date
            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
            assertThat(properties).isNotNull();
            assertThat(properties.get(ExerciseSchema.Properties.DUE_DATE)).isNotNull();
        }
    }
}
