package de.tum.cit.aet.artemis.globalsearch.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
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
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.EXERCISE),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(exerciseId))).limit(1));
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
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, exercise.getId());
            assertThat(properties).as("Exercise %d should exist in Weaviate", exercise.getId()).isNotNull();

            assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(exercise.getTitle());
            assertThat(properties.get(SearchableEntitySchema.Properties.EXERCISE_TYPE)).isEqualTo(exercise.getExerciseType().getValue());
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(exercise.getId());

            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());

            assertDateProperty(properties, SearchableEntitySchema.Properties.RELEASE_DATE, exercise.getReleaseDate());
            assertDateProperty(properties, SearchableEntitySchema.Properties.START_DATE, exercise.getStartDate());
            assertDateProperty(properties, SearchableEntitySchema.Properties.DUE_DATE, exercise.getDueDate());
        });
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

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, programmingExercise.getId());
            assertThat(properties).isNotNull();
            if (programmingExercise.getProgrammingLanguage() != null) {
                assertThat(properties.get(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE)).isEqualTo(programmingExercise.getProgrammingLanguage().name());
            }
            if (programmingExercise.getProjectType() != null) {
                assertThat(properties.get(SearchableEntitySchema.Properties.PROJECT_TYPE)).isEqualTo(programmingExercise.getProjectType().name());
            }
        });
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

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, modelingExercise.getId());
            assertThat(properties).isNotNull();
            if (modelingExercise.getDiagramType() != null) {
                assertThat(properties.get(SearchableEntitySchema.Properties.DIAGRAM_TYPE)).isEqualTo(modelingExercise.getDiagramType().name());
            }
        });
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

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, quizExercise.getId());
            assertThat(properties).isNotNull();
            if (quizExercise.getQuizMode() != null) {
                assertThat(properties.get(SearchableEntitySchema.Properties.QUIZ_MODE)).isEqualTo(quizExercise.getQuizMode().name());
            }
            if (quizExercise.getDuration() != null) {
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.QUIZ_DURATION)).intValue()).isEqualTo(quizExercise.getDuration());
            }
        });
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

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, fileUploadExercise.getId());
            assertThat(properties).isNotNull();
            if (fileUploadExercise.getFilePattern() != null) {
                assertThat(properties.get(SearchableEntitySchema.Properties.FILE_PATTERN)).isEqualTo(fileUploadExercise.getFilePattern());
            }
        });
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
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, exerciseId);
            assertThat(properties).as("Exercise %d should exist in Weaviate", exerciseId).isNotNull();

            assertThat(properties.get(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(true);
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.EXAM_ID)).longValue()).isEqualTo(exam.getId());

            assertDateProperty(properties, SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, exam.getVisibleDate());
            assertDateProperty(properties, SearchableEntitySchema.Properties.EXAM_START_DATE, exam.getStartDate());
            assertDateProperty(properties, SearchableEntitySchema.Properties.EXAM_END_DATE, exam.getEndDate());
        });
    }

    /**
     * Asserts that a Weaviate date property matches the expected ZonedDateTime value.
     * Compares by converting both dates to UTC before comparison.
     */
    private static void assertDateProperty(Map<String, Object> properties, String propertyName, ZonedDateTime expected) {
        if (expected == null) {
            return;
        }
        // Convert expected to UTC for comparison
        String expectedUTC = expected.withZoneSameInstant(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Handle both OffsetDateTime (newer Weaviate client) and String (older versions)
        Object actualValue = properties.get(propertyName);
        String actualUTC;

        if (actualValue instanceof OffsetDateTime offsetDateTime) {
            // Convert OffsetDateTime to UTC
            actualUTC = offsetDateTime.atZoneSameInstant(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        else if (actualValue instanceof String actualStr) {
            // Parse string date and convert to UTC
            ZonedDateTime actualDateTime = ZonedDateTime.parse(actualStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            actualUTC = actualDateTime.withZoneSameInstant(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        else {
            throw new AssertionError("Property " + propertyName + " has unexpected type: " + (actualValue != null ? actualValue.getClass() : "null"));
        }

        // Compare first 19 chars (YYYY-MM-DDTHH:MM:SS) to avoid millisecond precision differences
        assertThat(actualUTC).as("Property %s should match expected date", propertyName).startsWith(expectedUTC.substring(0, 19));
    }

    // -- Lecture utilities --

    /**
     * Queries Weaviate for the lecture with the given ID and returns its properties,
     * or {@code null} if no lecture was found.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lectureId       the ID of the lecture to look up
     * @return the lecture properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryLectureProperties(WeaviateService weaviateService, long lectureId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.LECTURE),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(lectureId))).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the lecture exists in Weaviate and its core properties match the given lecture.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lecture         the lecture whose metadata should be verified in Weaviate
     */
    public static void assertLectureExistsInWeaviate(WeaviateService weaviateService, Lecture lecture) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryLectureProperties(weaviateService, lecture.getId());
            assertThat(properties).as("Lecture %d should exist in Weaviate", lecture.getId()).isNotNull();

            assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(lecture.getTitle());
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(lecture.getId());

            Course course = lecture.getCourse();
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());
        });
    }

    /**
     * Asserts that no lecture with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lectureId       the ID of the lecture that should not exist
     */
    public static void assertLectureNotInWeaviate(WeaviateService weaviateService, long lectureId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryLectureProperties(weaviateService, lectureId);
            assertThat(properties).as("Lecture %d should not exist in Weaviate", lectureId).isNull();
        });
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
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExerciseProperties(weaviateService, exerciseId);
            assertThat(properties).as("Exercise %d should not exist in Weaviate", exerciseId).isNull();
        });
    }

    /**
     * Counts how many rows exist in Weaviate for the given {@code (type, entityId)} pair.
     * Useful for detecting duplicate rows caused by race conditions.
     *
     * @param weaviateService the Weaviate service to query
     * @param type            the entity type (use constants from {@link SearchableEntitySchema.TypeValues})
     * @param entityId        the entity id
     * @return the number of matching rows
     */
    public static int countRowsForEntity(WeaviateService weaviateService, String type, long entityId) throws Exception {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query.fetchObjects(query -> query
                .filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type), Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(entityId)))
                .limit(100));
        return response.objects().size();
    }

    // -- Exam utilities --

    /**
     * Queries Weaviate for the exam with the given ID and returns its properties,
     * or {@code null} if no exam was found.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param examId          the ID of the exam to look up
     * @return the exam properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryExamProperties(WeaviateService weaviateService, long examId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.EXAM),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(examId))).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the exam exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param examId          the ID of the exam that should exist
     */
    public static void assertExamExistsInWeaviate(WeaviateService weaviateService, long examId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExamProperties(weaviateService, examId);
            assertThat(properties).as("Exam %d should exist in Weaviate", examId).isNotNull();
        });
    }

    /**
     * Asserts that no exam with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param examId          the ID of the exam that should not exist
     */
    public static void assertExamNotInWeaviate(WeaviateService weaviateService, long examId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryExamProperties(weaviateService, examId);
            assertThat(properties).as("Exam %d should not exist in Weaviate", examId).isNull();
        });
    }

    // -- Lecture unit utilities --

    /**
     * Queries Weaviate for the lecture unit with the given ID and returns its properties,
     * or {@code null} if no lecture unit was found.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lectureUnitId   the ID of the lecture unit to look up
     * @return the lecture unit properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryLectureUnitProperties(WeaviateService weaviateService, long lectureUnitId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.LECTURE_UNIT),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(lectureUnitId))).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the lecture unit exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lectureUnitId   the ID of the lecture unit that should exist
     */
    public static void assertLectureUnitExistsInWeaviate(WeaviateService weaviateService, long lectureUnitId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryLectureUnitProperties(weaviateService, lectureUnitId);
            assertThat(properties).as("Lecture unit %d should exist in Weaviate", lectureUnitId).isNotNull();
        });
    }

    /**
     * Asserts that no lecture unit with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param lectureUnitId   the ID of the lecture unit that should not exist
     */
    public static void assertLectureUnitNotInWeaviate(WeaviateService weaviateService, long lectureUnitId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryLectureUnitProperties(weaviateService, lectureUnitId);
            assertThat(properties).as("Lecture unit %d should not exist in Weaviate", lectureUnitId).isNull();
        });
    }

    // -- FAQ utilities --

    /**
     * Queries Weaviate for the FAQ with the given ID and returns its properties,
     * or {@code null} if no FAQ was found.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param faqId           the ID of the FAQ to look up
     * @return the FAQ properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryFaqProperties(WeaviateService weaviateService, long faqId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.FAQ),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(faqId))).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the FAQ exists in Weaviate and its core properties match the given FAQ.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param faqId           the ID of the FAQ that should exist
     */
    public static void assertFaqExistsInWeaviate(WeaviateService weaviateService, long faqId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryFaqProperties(weaviateService, faqId);
            assertThat(properties).as("FAQ %d should exist in Weaviate", faqId).isNotNull();
        });
    }

    /**
     * Asserts that no FAQ with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param faqId           the ID of the FAQ that should not exist
     */
    public static void assertFaqNotInWeaviate(WeaviateService weaviateService, long faqId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryFaqProperties(weaviateService, faqId);
            assertThat(properties).as("FAQ %d should not exist in Weaviate", faqId).isNull();
        });
    }

    // -- Channel utilities --

    /**
     * Queries Weaviate for the channel with the given ID and returns its properties,
     * or {@code null} if no channel was found.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param channelId       the ID of the channel to look up
     * @return the channel properties map, or {@code null} if not found or Docker unavailable
     */
    public static Map<String, Object> queryChannelProperties(WeaviateService weaviateService, long channelId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return null;
        }
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var response = collection.query
                .fetchObjects(query -> query.filters(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.CHANNEL),
                        Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(channelId))).limit(1));
        if (response.objects().isEmpty()) {
            return null;
        }
        return response.objects().getFirst().properties();
    }

    /**
     * Asserts that the channel exists in Weaviate and its core properties match the given channel.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param channel         the channel whose metadata should be verified in Weaviate
     */
    public static void assertChannelExistsInWeaviate(WeaviateService weaviateService, Channel channel) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryChannelProperties(weaviateService, channel.getId());
            assertThat(properties).as("Channel %d should exist in Weaviate", channel.getId()).isNotNull();

            assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(channel.getName());
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue()).isEqualTo(channel.getId());

            Course course = channel.getCourse();
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());

            assertThat(properties.get(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE)).isEqualTo(channel.getIsCourseWide());
            assertThat(properties.get(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC)).isEqualTo(channel.getIsPublic());
        });
    }

    /**
     * Asserts that no channel with the given ID exists in Weaviate.
     *
     * @param weaviateService the Weaviate service to query (may be {@code null} if Docker is unavailable)
     * @param channelId       the ID of the channel that should not exist
     */
    public static void assertChannelNotInWeaviate(WeaviateService weaviateService, long channelId) throws Exception {
        if (shouldSkipWeaviateAssertions(weaviateService)) {
            return;
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var properties = queryChannelProperties(weaviateService, channelId);
            assertThat(properties).as("Channel %d should not exist in Weaviate", channelId).isNull();
        });
    }
}
