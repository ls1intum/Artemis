package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
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

    private static final String CONTENT_COURSE_ID = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID = "lecture_unit_id";

    private static final List<String> IRIS_CONTENT_COLLECTIONS = List.of(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION);

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() throws Exception {
        clearMetadataForTestCourses();
        recreateIrisContentCollections();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            clearMetadataForTestCourses();
            dropIrisContentCollections();
        }
    }

    @Test
    void readsExactMetadataPresentSetsBucketedByCourseAndTypeExcludingPosts() throws Exception {
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 2L);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE_UNIT, 10L);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.FAQ, 30L);
        // Posts and answer posts are the firehose the coverage read must NOT id-diff.
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.POST, 99L);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.ANSWER_POST, 98L);
        insertMetadata(COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 3L);

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
    void capturesLastIngestedAtPerCourse() throws Exception {
        Instant before = Instant.now().minus(5, ChronoUnit.MINUTES);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var present = coverageReadService.readPresentMetadata(List.of(COURSE_A));
            assertThat(present.lastIngestedAtByCourse().get(COURSE_A)).isNotNull().isAfter(before).isBefore(Instant.now().plus(5, ChronoUnit.MINUTES));
        });
    }

    @Test
    void readsDistinctContentUnitIdsForEachIrisCollection() throws Exception {
        for (String collection : IRIS_CONTENT_COLLECTIONS) {
            // Unit 10 has two objects (chunks/segments), unit 11 one - the read must return the DISTINCT unit set {10, 11}.
            insertContent(collection, COURSE_A, 10L);
            insertContent(collection, COURSE_A, 10L);
            insertContent(collection, COURSE_A, 11L);
            insertContent(collection, COURSE_B, 40L);
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

    private void insertMetadata(long courseId, String type, long entityId) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.insert(properties);
    }

    private void insertContent(String collectionName, long courseId, long unitId) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_COURSE_ID, courseId);
        properties.put(CONTENT_LECTURE_UNIT_ID, unitId);
        weaviateService.getExternalCollection(collectionName).data.insert(properties);
    }

    private void clearMetadataForTestCourses() throws Exception {
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data
                .deleteMany(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).containsAny(COURSE_A, COURSE_B));
    }

    private void recreateIrisContentCollections() throws Exception {
        dropIrisContentCollections();
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            weaviateClient.collections.create(name, collection -> {
                collection.vectorConfig(VectorConfig.selfProvided());
                collection.properties(Property.integer(CONTENT_COURSE_ID));
                collection.properties(Property.integer(CONTENT_LECTURE_UNIT_ID));
                return collection;
            });
        }
    }

    private void dropIrisContentCollections() throws Exception {
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            if (weaviateClient.collections.exists(name)) {
                weaviateClient.collections.delete(name);
            }
        }
    }
}
