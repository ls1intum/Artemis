package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.countRowsForEntity;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryExerciseProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for {@link SearchableEntityWeaviateService} using a real Weaviate Testcontainer.
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
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

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
            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, programmingExercise));

            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
            assertThat(properties.get(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo(programmingExercise.getProgrammingLanguage().name());
            assertThat(properties.get(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(false);
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.MAX_POINTS)).doubleValue()).isEqualTo(programmingExercise.getMaxPoints());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateExercise_updatesMetadataInWeaviate() throws Exception {
            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, programmingExercise));

            // Modify exercise properties
            String updatedTitle = "Updated Weaviate Test Title";
            double updatedMaxPoints = 42.0;
            programmingExercise.setTitle(updatedTitle);
            programmingExercise.setMaxPoints(updatedMaxPoints);

            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(updatedTitle);
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.MAX_POINTS)).doubleValue()).isEqualTo(updatedMaxPoints);
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(programmingExercise.getId());
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteExercise_removesMetadataFromWeaviate() throws Exception {
            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, programmingExercise));

            searchableEntityWeaviateService.deleteEntityAsync(SearchableEntitySchema.TypeValues.EXERCISE, programmingExercise.getId());

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseNotInWeaviate(weaviateService, programmingExercise.getId()));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testConcurrentUpserts_doNotCreateDuplicateRows() throws Exception {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Launch threads that all upsert the same exercise simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads at once to maximize race window
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Wait for all upserts to complete in Weaviate, then verify exactly one row exists
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                int rowCount = countRowsForEntity(weaviateService, SearchableEntitySchema.TypeValues.EXERCISE, programmingExercise.getId());
                assertThat(rowCount).as("Concurrent upserts for the same exercise must not create duplicate rows").isEqualTo(1);
            });
        }
    }

    @Nested
    class ProgrammingExercisePartialUpdateWeaviateTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateProblemStatement_updatesWeaviate() throws Exception {
            // Insert exercise into Weaviate first
            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, programmingExercise));

            // Update problem statement via endpoint
            final var newProblem = "updated problem statement for weaviate test";
            final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
            request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

            // Wait for async update from the endpoint to complete and verify Weaviate has the updated problem statement
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo(newProblem);
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateTimeline_updatesWeaviate() throws Exception {
            // Insert exercise into Weaviate first
            searchableEntityWeaviateService.upsertExerciseAsync(programmingExercise);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, programmingExercise));

            // Update timeline via endpoint
            var exerciseForUpdate = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
            ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(7);
            exerciseForUpdate.setDueDate(newDueDate);

            final var endpoint = "/api/programming/programming-exercises/timeline";
            request.putWithResponseBody(endpoint, exerciseForUpdate, ProgrammingExercise.class, HttpStatus.OK);

            // Wait for async update from the endpoint to complete and verify Weaviate has the updated due date
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DUE_DATE)).isNotNull();
            });
        }
    }
}
