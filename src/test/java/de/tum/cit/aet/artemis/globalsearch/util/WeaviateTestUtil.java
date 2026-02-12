package de.tum.cit.aet.artemis.globalsearch.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
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
     * Returns {@code true} when Weaviate assertions should be skipped because
     * Docker is not available on the current machine.
     * If Docker IS available but the service is {@code null}, this method fails
     * the test with a descriptive error instead of silently skipping.
     */
    public static boolean shouldSkipWeaviateAssertions(WeaviateService weaviateService) {
        if (weaviateService != null) {
            return false;
        }
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return true;
        }
        throw new AssertionError("WeaviateService is null even though Docker is available — the Weaviate Testcontainer should be running. "
                + "Check that the Weaviate container started successfully and that artemis.weaviate.enabled is set to true.");
    }

    /**
     * Queries Weaviate for the exercise with the given ID and returns its properties,
     * or {@code null} if no exercise was found.
     * Returns {@code null} without querying if Docker is not available.
     * Fails the test if Docker is available but WeaviateService is null.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param exerciseId      the ID of the exercise to look up
     * @return the exercise properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryExerciseProperties(WeaviateService weaviateService, long exerciseId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(ExerciseSchema.COLLECTION_NAME);
        var response = collection.query.fetchObjects(query -> query.filters(Filter.property(ExerciseSchema.Properties.EXERCISE_ID).eq(exerciseId)).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the exercise exists in Weaviate and its core properties match the given exercise.
     * Skips if Docker is not available. Fails if Docker is available but WeaviateService is null.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param exercise        the exercise whose metadata should be verified in Weaviate
     */
    public static void assertExerciseExistsInWeaviate(WeaviateService weaviateService, Exercise exercise) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        var properties = queryExerciseProperties(weaviateService, exercise.getId());
        assertThat(properties).as("Exercise %d should exist in Weaviate", exercise.getId()).isNotNull();

        assertThat(properties.get(ExerciseSchema.Properties.TITLE)).isEqualTo(exercise.getTitle());
        assertThat(properties.get(ExerciseSchema.Properties.EXERCISE_TYPE)).isEqualTo(exercise.getType());
        assertThat(((Number) properties.get(ExerciseSchema.Properties.EXERCISE_ID)).longValue()).isEqualTo(exercise.getId());

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        assertThat(((Number) properties.get(ExerciseSchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());

        assertDateProperty(properties, ExerciseSchema.Properties.RELEASE_DATE, exercise.getReleaseDate());
        assertDateProperty(properties, ExerciseSchema.Properties.START_DATE, exercise.getStartDate());
        assertDateProperty(properties, ExerciseSchema.Properties.DUE_DATE, exercise.getDueDate());
    }

    /**
     * Asserts that the exercise exists in Weaviate and verifies programming-specific properties.
     *
     * @param weaviateService     the Weaviate service to query
     * @param programmingExercise the programming exercise whose metadata should be verified in Weaviate
     */
    public static void assertProgrammingExerciseExistsInWeaviate(WeaviateService weaviateService, ProgrammingExercise programmingExercise) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
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
     * Asserts that the exercise exists in Weaviate and verifies modeling-specific properties.
     *
     * @param weaviateService  the Weaviate service to query
     * @param modelingExercise the modeling exercise whose metadata should be verified in Weaviate
     */
    public static void assertModelingExerciseExistsInWeaviate(WeaviateService weaviateService, ModelingExercise modelingExercise) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        assertExerciseExistsInWeaviate(weaviateService, modelingExercise);

        var properties = queryExerciseProperties(weaviateService, modelingExercise.getId());
        if (modelingExercise.getDiagramType() != null) {
            assertThat(properties.get(ExerciseSchema.Properties.DIAGRAM_TYPE)).isEqualTo(modelingExercise.getDiagramType().name());
        }
    }

    /**
     * Asserts that the exercise exists in Weaviate and verifies quiz-specific properties.
     *
     * @param weaviateService the Weaviate service to query
     * @param quizExercise    the quiz exercise whose metadata should be verified in Weaviate
     */
    public static void assertQuizExerciseExistsInWeaviate(WeaviateService weaviateService, QuizExercise quizExercise) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        assertExerciseExistsInWeaviate(weaviateService, quizExercise);

        var properties = queryExerciseProperties(weaviateService, quizExercise.getId());
        if (quizExercise.getQuizMode() != null) {
            assertThat(properties.get(ExerciseSchema.Properties.QUIZ_MODE)).isEqualTo(quizExercise.getQuizMode().name());
        }
        if (quizExercise.getDuration() != null) {
            assertThat(((Number) properties.get(ExerciseSchema.Properties.QUIZ_DURATION)).intValue()).isEqualTo(quizExercise.getDuration());
        }
    }

    /**
     * Asserts that the exercise exists in Weaviate and verifies file-upload-specific properties.
     *
     * @param weaviateService    the Weaviate service to query
     * @param fileUploadExercise the file upload exercise whose metadata should be verified in Weaviate
     */
    public static void assertFileUploadExerciseExistsInWeaviate(WeaviateService weaviateService, FileUploadExercise fileUploadExercise) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        assertExerciseExistsInWeaviate(weaviateService, fileUploadExercise);

        var properties = queryExerciseProperties(weaviateService, fileUploadExercise.getId());
        if (fileUploadExercise.getFilePattern() != null) {
            assertThat(properties.get(ExerciseSchema.Properties.FILE_PATTERN)).isEqualTo(fileUploadExercise.getFilePattern());
        }
    }

    /**
     * Asserts that the exercise exists in Weaviate and its exam date properties match the given exam.
     * Skips if Docker is not available. Fails if Docker is available but WeaviateService is null.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param exerciseId      the ID of the exercise to verify
     * @param exam            the exam whose dates should be reflected in Weaviate
     */
    public static void assertExerciseExamDatesInWeaviate(WeaviateService weaviateService, long exerciseId, Exam exam) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        var properties = queryExerciseProperties(weaviateService, exerciseId);
        assertThat(properties).as("Exercise %d should exist in Weaviate", exerciseId).isNotNull();

        assertThat(properties.get(ExerciseSchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(true);
        assertThat(((Number) properties.get(ExerciseSchema.Properties.EXAM_ID)).longValue()).isEqualTo(exam.getId());

        assertDateProperty(properties, ExerciseSchema.Properties.EXAM_VISIBLE_DATE, exam.getVisibleDate());
        assertDateProperty(properties, ExerciseSchema.Properties.EXAM_START_DATE, exam.getStartDate());
        assertDateProperty(properties, ExerciseSchema.Properties.EXAM_END_DATE, exam.getEndDate());
    }

    /**
     * Asserts that a Weaviate date property matches the expected ZonedDateTime value.
     * Compares by converting both the stored value and expected value to RFC3339 format.
     */
    private static void assertDateProperty(Map<String, Object> properties, String propertyName, ZonedDateTime expected) {
        if (expected == null) {
            return;
        }
        String expectedFormatted = expected.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(properties.get(propertyName)).as("Property %s should match expected date", propertyName).asString().startsWith(expectedFormatted.substring(0, 19));
    }

    /**
     * Asserts that no exercise with the given ID exists in Weaviate.
     * Skips if Docker is not available. Fails if Docker is available but WeaviateService is null.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param exerciseId      the ID of the exercise that should not exist
     */
    public static void assertExerciseNotInWeaviate(WeaviateService weaviateService, long exerciseId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        var properties = queryExerciseProperties(weaviateService, exerciseId);
        assertThat(properties).as("Exercise %d should not exist in Weaviate", exerciseId).isNull();
    }
}
