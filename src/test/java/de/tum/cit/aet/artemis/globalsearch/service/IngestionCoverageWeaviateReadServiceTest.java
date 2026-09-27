package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.IRIS_CONTENT_COLLECTIONS;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.dropIrisContentCollections;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertContent;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertMetadata;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.recreateIrisContentCollections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Integration test for {@link IngestionCoverageWeaviateReadService} against a real Weaviate Testcontainer.
 * <p>
 * Seeds the {@code SearchableEntities} metadata collection and the four Iris content collections ({@code Lectures},
 * {@code LectureTranscriptions}, {@code LectureUnitSegments}, {@code LectureUnits}) directly, then asserts the read layer
 * returns the exact present id-sets bucketed by course and type (with posts/answer posts excluded), the exact distinct
 * content-unit set per collection, the last-ingested time per course, and that an absent content collection reads as
 * empty rather than erroring. The Iris collections are created under their exact, unprefixed names because that is how the
 * Pyris ingestion pipeline names them and how the read layer addresses them.
 * <p>
 * Weaviate indexing is asynchronous, so reads are wrapped in {@link org.awaitility.Awaitility} polls.
 */
@EnabledIf("isWeaviateEnabled")
class IngestionCoverageWeaviateReadServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private IngestionCoverageWeaviateReadService coverageReadService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private WeaviateClient weaviateClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // Distinctive course ids so the shared, per-context SearchableEntities collection is never confused with other tests.
    private static final long COURSE_A = 880001L;

    private static final long COURSE_B = 880002L;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() throws Exception {
        clearMetadataForTestCourses();
        recreateIrisContentCollections(weaviateClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            clearMetadataForTestCourses();
            dropIrisContentCollections(weaviateClient);
        }
    }

    @Test
    void readsExactMetadataPresentSetsBucketedByCourseAndTypeExcludingPosts() throws Exception {
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L, null);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 2L, null);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE_UNIT, 10L, null);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, null);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.FAQ, 30L, null);
        // Posts and answer posts are the firehose the coverage read must NOT id-diff.
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.POST, 99L, null);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.ANSWER_POST, 98L, null);
        insertMetadata(weaviateService, COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 3L, null);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var present = coverageReadService.readPresentMetadata(List.of(COURSE_A, COURSE_B));
            var byCourse = present.presentIdsByCourseAndType();

            assertThat(byCourse.get(COURSE_A).get(SearchableEntitySchema.TypeValues.EXERCISE)).containsExactlyInAnyOrder(1L, 2L);
            assertThat(byCourse.get(COURSE_A).get(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).containsExactly(10L);
            assertThat(byCourse.get(COURSE_A).get(SearchableEntitySchema.TypeValues.LECTURE)).containsExactly(20L);
            assertThat(byCourse.get(COURSE_A).get(SearchableEntitySchema.TypeValues.FAQ)).containsExactly(30L);
            assertThat(byCourse.get(COURSE_A)).doesNotContainKeys(SearchableEntitySchema.TypeValues.POST, SearchableEntitySchema.TypeValues.ANSWER_POST);
            assertThat(byCourse.get(COURSE_B).get(SearchableEntitySchema.TypeValues.EXERCISE)).containsExactly(3L);
        });
    }

    @Test
    void aFullReadIsSplitSoNoCourseLosesObjectsToAnotherCoursesVolume() throws Exception {
        // Two courses of two objects each fill a read capped at three. Kept as is, that read drops an object of either
        // course, so it is split and each course read on its own.
        ReflectionTestUtils.setField(coverageReadService, "metadataReadLimit", 3);
        try {
            insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L, null);
            insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 2L, null);
            insertMetadata(weaviateService, COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 3L, null);
            insertMetadata(weaviateService, COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 4L, null);

            await().atMost(TIMEOUT).untilAsserted(() -> {
                var byCourse = coverageReadService.readPresentMetadata(List.of(COURSE_A, COURSE_B)).presentIdsByCourseAndType();
                assertThat(byCourse.get(COURSE_A).get(SearchableEntitySchema.TypeValues.EXERCISE)).containsExactlyInAnyOrder(1L, 2L);
                assertThat(byCourse.get(COURSE_B).get(SearchableEntitySchema.TypeValues.EXERCISE)).containsExactlyInAnyOrder(3L, 4L);
            });
        }
        finally {
            ReflectionTestUtils.setField(coverageReadService, "metadataReadLimit", 10_000);
        }
    }

    @Test
    void capturesLastIngestedAtPerCourse() throws Exception {
        Instant before = Instant.now().minus(5, ChronoUnit.MINUTES);
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L, null);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var present = coverageReadService.readPresentMetadata(List.of(COURSE_A));
            assertThat(present.lastIngestedAtByCourse().get(COURSE_A)).isNotNull().isAfter(before).isBefore(Instant.now().plus(5, ChronoUnit.MINUTES));
        });
    }

    @Test
    void readsDistinctContentUnitIdsForEachIrisCollection() throws Exception {
        for (String collection : IRIS_CONTENT_COLLECTIONS) {
            // Unit 10 has two objects (chunks/segments), unit 11 one - the read must return the DISTINCT unit set {10, 11}.
            insertContent(weaviateService, collection, COURSE_A, 10L, null);
            insertContent(weaviateService, collection, COURSE_A, 10L, null);
            insertContent(weaviateService, collection, COURSE_A, 11L, null);
            insertContent(weaviateService, collection, COURSE_B, 40L, null);
        }

        for (String collection : IRIS_CONTENT_COLLECTIONS) {
            await().atMost(TIMEOUT).untilAsserted(() -> {
                var byCourse = coverageReadService.readPresentContentUnitIds(collection, List.of(COURSE_A, COURSE_B));
                assertThat(byCourse.get(COURSE_A)).as("distinct present units for course A in %s", collection).containsExactlyInAnyOrder(10L, 11L);
                assertThat(byCourse.get(COURSE_B)).as("distinct present units for course B in %s", collection).containsExactly(40L);
            });
        }
    }

    @Test
    void absentContentCollectionYieldsEmptyNotError() {
        Map<Long, ?> result = coverageReadService.readPresentContentUnitIds("NonExistentCoverageCollection", List.of(COURSE_A, COURSE_B));
        assertThat(result).isEmpty();
    }

    private void clearMetadataForTestCourses() throws Exception {
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data
                .deleteMany(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).containsAny(COURSE_A, COURSE_B));
    }

}
