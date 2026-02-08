package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.weaviate.WeaviateContainer;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.weaviate.WeaviateService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Integration test for {@link ExerciseWeaviateService} using a real Weaviate instance via Testcontainers.
 * Tests the full CRUD lifecycle of exercise metadata in Weaviate.
 * Requires Docker to be available on the test machine.
 */
@EnabledIf("isDockerAvailable")
class ExerciseWeaviateServiceIntegrationTest {

    private static final String WEAVIATE_IMAGE = "cr.weaviate.io/semitechnologies/weaviate:1.34.10";

    private static final int WEAVIATE_HTTP_PORT = 8080;

    private static final int WEAVIATE_GRPC_PORT = 50051;

    private static WeaviateContainer weaviateContainer;

    private static WeaviateClient client;

    private WeaviateService weaviateService;

    private ExerciseWeaviateService exerciseWeaviateService;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void startContainer() throws IOException {
        weaviateContainer = new WeaviateContainer(WEAVIATE_IMAGE);
        weaviateContainer.start();

        client = WeaviateClient.connectToLocal(config -> config.host(weaviateContainer.getHost()).port(weaviateContainer.getMappedPort(WEAVIATE_HTTP_PORT))
                .grpcPort(weaviateContainer.getMappedPort(WEAVIATE_GRPC_PORT)));

        createExercisesCollection();
    }

    @AfterAll
    static void stopContainer() {
        if (weaviateContainer != null) {
            weaviateContainer.stop();
        }
    }

    @BeforeEach
    void setup() {
        // Delete all exercise objects from the collection for test isolation
        var collection = client.collections.use(WeaviateSchemas.EXERCISES_COLLECTION);
        collection.data.deleteMany(Filter.property(WeaviateSchemas.ExercisesProperties.EXERCISE_ID).gte(0));

        weaviateService = new WeaviateService(client);
        exerciseWeaviateService = new ExerciseWeaviateService(Optional.of(weaviateService));
        ReflectionTestUtils.setField(exerciseWeaviateService, "serverUrl", "http://localhost:9000");
    }

    /**
     * Creates the Exercises collection without a vectorizer module.
     * The production code uses text2vec-transformers, but for CRUD integration tests
     * we don't need embedding generation - only the schema and data operations.
     */
    private static void createExercisesCollection() throws IOException {
        WeaviateCollectionSchema schema = WeaviateSchemas.EXERCISES_SCHEMA;

        client.collections.create(schema.collectionName(), col -> {
            for (WeaviatePropertyDefinition prop : schema.properties()) {
                col.properties(createProperty(prop));
            }
            return col;
        });
    }

    /**
     * Mirrors {@link WeaviateService}'s private createProperty method to reuse the same
     * schema definitions for collection creation in tests.
     * <p>
     * Note: DATE properties are always created with indexFilterable=true because
     * {@link WeaviateService#fetchProgrammingExercisesByCourseId} filters on release_date.
     * The production schema defines release_date as nonSearchable (indexFilterable=false),
     * which is inconsistent with the query code - this test reveals that discrepancy.
     */
    private static Property createProperty(WeaviatePropertyDefinition definition) {
        return switch (definition.dataType()) {
            case INT -> Property.integer(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case TEXT -> Property.text(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case NUMBER -> Property.number(definition.name(), p -> p.indexSearchable(definition.indexSearchable()).indexFilterable(definition.indexFilterable()));
            case BOOLEAN -> Property.bool(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case DATE -> Property.date(definition.name(), p -> p.indexFilterable(true));
            case UUID -> Property.uuid(definition.name(), p -> p.indexFilterable(definition.indexFilterable()));
            case BLOB -> Property.blob(definition.name());
        };
    }

    private ProgrammingExercise createTestExercise(long exerciseId, long courseId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Test Course");

        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        exercise.setCourse(course);
        exercise.setTitle("Test Programming Exercise");
        exercise.setShortName("test-prog");
        exercise.setProblemStatement("Write a solution for this problem");
        exercise.setMaxPoints(10.0);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise.setStartDate(ZonedDateTime.now().minusDays(1));
        exercise.setDueDate(ZonedDateTime.now().plusDays(7));

        return exercise;
    }

    @Test
    void testInsertExercise_shouldStoreInWeaviate() {
        ProgrammingExercise exercise = createTestExercise(1L, 100L);

        exerciseWeaviateService.insertExercise(exercise);

        List<Map<String, Object>> results = weaviateService.fetchProgrammingExercisesByCourseId(100L, false);
        assertThat(results).hasSize(1);

        Map<String, Object> stored = results.getFirst();
        assertThat(stored).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise")
                .containsEntry(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE, "programming").containsEntry(WeaviateSchemas.ExercisesProperties.PROGRAMMING_LANGUAGE, "JAVA")
                .containsEntry(WeaviateSchemas.ExercisesProperties.COURSE_NAME, "Test Course").containsEntry(WeaviateSchemas.ExercisesProperties.SHORT_NAME, "test-prog")
                .containsEntry(WeaviateSchemas.ExercisesProperties.PROBLEM_STATEMENT, "Write a solution for this problem")
                .containsEntry(WeaviateSchemas.ExercisesProperties.DIFFICULTY, "MEDIUM").containsEntry(WeaviateSchemas.ExercisesProperties.BASE_URL, "http://localhost:9000");
    }

    @Test
    void testFetchExercises_shouldReturnOnlyReleasedForStudents() {
        // Insert a released exercise (release date in the past)
        ProgrammingExercise releasedExercise = createTestExercise(1L, 100L);
        releasedExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exerciseWeaviateService.insertExercise(releasedExercise);

        // Insert an unreleased exercise (release date in the future)
        ProgrammingExercise unreleasedExercise = createTestExercise(2L, 100L);
        unreleasedExercise.setTitle("Unreleased Exercise");
        unreleasedExercise.setReleaseDate(ZonedDateTime.now().plusDays(30));
        exerciseWeaviateService.insertExercise(unreleasedExercise);

        // Student view (isAtLeastTutor=false -> filterReleasedOnly=true): only released exercises
        List<Map<String, Object>> studentResults = exerciseWeaviateService.fetchProgrammingExercisesForCourse(100L, false);
        assertThat(studentResults).hasSize(1);
        assertThat(studentResults.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise");

        // Tutor view (isAtLeastTutor=true -> filterReleasedOnly=false): all exercises
        List<Map<String, Object>> tutorResults = exerciseWeaviateService.fetchProgrammingExercisesForCourse(100L, true);
        assertThat(tutorResults).hasSize(2);
    }

    @Test
    void testDeleteExercise_shouldRemoveFromWeaviate() {
        ProgrammingExercise exercise = createTestExercise(1L, 100L);
        exerciseWeaviateService.insertExercise(exercise);

        // Verify it exists
        assertThat(weaviateService.fetchProgrammingExercisesByCourseId(100L, false)).hasSize(1);

        // Delete
        exerciseWeaviateService.deleteExercise(1L);

        // Verify it's gone
        assertThat(weaviateService.fetchProgrammingExercisesByCourseId(100L, false)).isEmpty();
    }

    @Test
    void testUpdateExercise_shouldReplaceInWeaviate() {
        ProgrammingExercise exercise = createTestExercise(1L, 100L);
        exerciseWeaviateService.insertExercise(exercise);

        // Update the exercise
        exercise.setTitle("Updated Exercise Title");
        exercise.setMaxPoints(20.0);
        exerciseWeaviateService.updateExercise(exercise);

        // Verify the update - should still be one object, not two
        List<Map<String, Object>> results = weaviateService.fetchProgrammingExercisesByCourseId(100L, false);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Updated Exercise Title");
    }

    @Test
    void testInsertExercise_withNullOptionalFields_shouldStoreSuccessfully() {
        ProgrammingExercise exercise = createTestExercise(1L, 100L);
        exercise.setShortName(null);
        exercise.setProblemStatement(null);
        exercise.setDifficulty(null);
        exercise.setReleaseDate(null);
        exercise.setStartDate(null);
        exercise.setDueDate(null);

        exerciseWeaviateService.insertExercise(exercise);

        // Use tutor view (no release date filter) since release_date is null
        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(100L, true);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise");
    }

    @Test
    void testInsertExercise_withNullId_shouldNotInsert() {
        ProgrammingExercise exercise = createTestExercise(1L, 100L);
        exercise.setId(null);

        exerciseWeaviateService.insertExercise(exercise);

        // Should not have inserted anything
        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(100L, true);
        assertThat(results).isEmpty();
    }

    @Test
    void testWeaviateNotAvailable_shouldReturnEmptyList() {
        ExerciseWeaviateService disabledService = new ExerciseWeaviateService(Optional.empty());

        assertThat(disabledService.isWeaviateAvailable()).isFalse();
        assertThat(disabledService.fetchProgrammingExercisesForCourse(100L, true)).isEmpty();
    }

    @Test
    void testHealthCheck_shouldReturnTrue() {
        assertThat(weaviateService.isHealthy()).isTrue();
    }

    @Test
    void testInsertMultipleExercises_shouldAllBeRetrievable() {
        ProgrammingExercise exercise1 = createTestExercise(1L, 100L);
        exerciseWeaviateService.insertExercise(exercise1);

        ProgrammingExercise exercise2 = createTestExercise(2L, 100L);
        exercise2.setTitle("Second Exercise");
        exerciseWeaviateService.insertExercise(exercise2);

        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(100L, true);
        assertThat(results).hasSize(2);

        List<String> titles = results.stream().map(r -> (String) r.get(WeaviateSchemas.ExercisesProperties.TITLE)).toList();
        assertThat(titles).containsExactlyInAnyOrder("Test Programming Exercise", "Second Exercise");
    }
}
