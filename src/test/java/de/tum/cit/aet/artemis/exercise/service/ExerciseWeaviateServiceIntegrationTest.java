package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.weaviate.WeaviateService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Integration test for {@link ExerciseWeaviateService} using a real Weaviate instance via Testcontainers.
 * Tests the full CRUD lifecycle of exercise metadata in Weaviate.
 * <p>
 * The Weaviate and transformer inference containers are started by the base class
 * {@link AbstractSpringIntegrationLocalCILocalVCTest}. If Docker is not available,
 * all tests in this class are skipped via {@code assumeTrue}.
 * <p>
 * Each test uses unique exercise and course IDs via atomic counters to avoid
 * interference with other tests running in parallel.
 */
class ExerciseWeaviateServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final AtomicLong EXERCISE_ID_COUNTER = new AtomicLong(500_000);

    private static final AtomicLong COURSE_ID_COUNTER = new AtomicLong(600_000);

    @Autowired
    private ExerciseWeaviateService exerciseWeaviateService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @BeforeEach
    void checkWeaviateAvailable() {
        assumeTrue(exerciseWeaviateService.isWeaviateAvailable(), "Weaviate is not available (Docker not running?), skipping test");
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
        long exerciseId = EXERCISE_ID_COUNTER.getAndIncrement();
        long courseId = COURSE_ID_COUNTER.getAndIncrement();
        ProgrammingExercise exercise = createTestExercise(exerciseId, courseId);

        exerciseWeaviateService.insertExercise(exercise);

        List<Map<String, Object>> results = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(results).hasSize(1);

        Map<String, Object> stored = results.getFirst();
        assertThat(stored).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise")
                .containsEntry(WeaviateSchemas.ExercisesProperties.EXERCISE_TYPE, "programming").containsEntry(WeaviateSchemas.ExercisesProperties.PROGRAMMING_LANGUAGE, "JAVA")
                .containsEntry(WeaviateSchemas.ExercisesProperties.COURSE_NAME, "Test Course").containsEntry(WeaviateSchemas.ExercisesProperties.SHORT_NAME, "test-prog")
                .containsEntry(WeaviateSchemas.ExercisesProperties.PROBLEM_STATEMENT, "Write a solution for this problem")
                .containsEntry(WeaviateSchemas.ExercisesProperties.DIFFICULTY, "MEDIUM");
    }

    @Test
    void testFetchExercises_shouldReturnOnlyReleasedForStudents() {
        long courseId = COURSE_ID_COUNTER.getAndIncrement();

        // Insert a released exercise (release date in the past)
        ProgrammingExercise releasedExercise = createTestExercise(EXERCISE_ID_COUNTER.getAndIncrement(), courseId);
        releasedExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exerciseWeaviateService.insertExercise(releasedExercise);

        // Insert an unreleased exercise (release date in the future)
        ProgrammingExercise unreleasedExercise = createTestExercise(EXERCISE_ID_COUNTER.getAndIncrement(), courseId);
        unreleasedExercise.setTitle("Unreleased Exercise");
        unreleasedExercise.setReleaseDate(ZonedDateTime.now().plusDays(30));
        exerciseWeaviateService.insertExercise(unreleasedExercise);

        // Student view (isAtLeastTutor=false -> filterReleasedOnly=true): only released exercises
        List<Map<String, Object>> studentResults = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, false);
        assertThat(studentResults).hasSize(1);
        assertThat(studentResults.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise");

        // Tutor view (isAtLeastTutor=true -> filterReleasedOnly=false): all exercises
        List<Map<String, Object>> tutorResults = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, true);
        assertThat(tutorResults).hasSize(2);
    }

    @Test
    void testDeleteExercise_shouldRemoveFromWeaviate() {
        long exerciseId = EXERCISE_ID_COUNTER.getAndIncrement();
        long courseId = COURSE_ID_COUNTER.getAndIncrement();
        ProgrammingExercise exercise = createTestExercise(exerciseId, courseId);
        exerciseWeaviateService.insertExercise(exercise);

        // Verify it exists
        assertThat(weaviateService.fetchProgrammingExercisesByCourseId(courseId, false)).hasSize(1);

        // Delete
        exerciseWeaviateService.deleteExercise(exerciseId);

        // Verify it's gone
        assertThat(weaviateService.fetchProgrammingExercisesByCourseId(courseId, false)).isEmpty();
    }

    @Test
    void testUpdateExercise_shouldReplaceInWeaviate() {
        long exerciseId = EXERCISE_ID_COUNTER.getAndIncrement();
        long courseId = COURSE_ID_COUNTER.getAndIncrement();
        ProgrammingExercise exercise = createTestExercise(exerciseId, courseId);
        exerciseWeaviateService.insertExercise(exercise);

        // Update the exercise
        exercise.setTitle("Updated Exercise Title");
        exercise.setMaxPoints(20.0);
        exerciseWeaviateService.updateExercise(exercise);

        // Verify the update - should still be one object, not two
        List<Map<String, Object>> results = weaviateService.fetchProgrammingExercisesByCourseId(courseId, false);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Updated Exercise Title");
    }

    @Test
    void testInsertExercise_withNullOptionalFields_shouldStoreSuccessfully() {
        long exerciseId = EXERCISE_ID_COUNTER.getAndIncrement();
        long courseId = COURSE_ID_COUNTER.getAndIncrement();
        ProgrammingExercise exercise = createTestExercise(exerciseId, courseId);
        exercise.setShortName(null);
        exercise.setProblemStatement(null);
        exercise.setDifficulty(null);
        exercise.setReleaseDate(null);
        exercise.setStartDate(null);
        exercise.setDueDate(null);

        exerciseWeaviateService.insertExercise(exercise);

        // Use tutor view (no release date filter) since release_date is null
        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, true);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry(WeaviateSchemas.ExercisesProperties.TITLE, "Test Programming Exercise");
    }

    @Test
    void testInsertExercise_withNullId_shouldNotInsert() {
        long courseId = COURSE_ID_COUNTER.getAndIncrement();
        ProgrammingExercise exercise = createTestExercise(EXERCISE_ID_COUNTER.getAndIncrement(), courseId);
        exercise.setId(null);

        exerciseWeaviateService.insertExercise(exercise);

        // Should not have inserted anything
        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, true);
        assertThat(results).isEmpty();
    }

    @Test
    void testHealthCheck_shouldReturnTrue() {
        assertThat(weaviateService.isHealthy()).isTrue();
    }

    @Test
    void testInsertMultipleExercises_shouldAllBeRetrievable() {
        long courseId = COURSE_ID_COUNTER.getAndIncrement();

        ProgrammingExercise exercise1 = createTestExercise(EXERCISE_ID_COUNTER.getAndIncrement(), courseId);
        exerciseWeaviateService.insertExercise(exercise1);

        ProgrammingExercise exercise2 = createTestExercise(EXERCISE_ID_COUNTER.getAndIncrement(), courseId);
        exercise2.setTitle("Second Exercise");
        exerciseWeaviateService.insertExercise(exercise2);

        List<Map<String, Object>> results = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, true);
        assertThat(results).hasSize(2);

        List<String> titles = results.stream().map(r -> (String) r.get(WeaviateSchemas.ExercisesProperties.TITLE)).toList();
        assertThat(titles).containsExactlyInAnyOrder("Test Programming Exercise", "Second Exercise");
    }
}
