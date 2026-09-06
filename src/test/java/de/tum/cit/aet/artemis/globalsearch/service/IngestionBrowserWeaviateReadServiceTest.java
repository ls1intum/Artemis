package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
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
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Integration test for {@link IngestionBrowserWeaviateReadService} against a real Weaviate Testcontainer.
 * <p>
 * Seeds the {@code SearchableEntities} metadata collection and the four Iris content collections directly, then asserts
 * that the browser reads return the stored rows scoped to one course with their populated property maps, that posts and
 * answer posts are excluded, and that reading one unit's objects is scoped to that unit and that
 * collection.
 * <p>
 * Weaviate indexing is asynchronous, so reads are wrapped in {@link org.awaitility.Awaitility} polls.
 */
@EnabledIf("isWeaviateEnabled")
class IngestionBrowserWeaviateReadServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private IngestionBrowserWeaviateReadService browserReadService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private WeaviateClient weaviateClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // Distinctive course ids so the shared, per-context SearchableEntities collection is never confused with other tests.
    private static final long COURSE_A = 881001L;

    private static final long COURSE_B = 881002L;

    private static final long UNIT_WITH_CONTENT = 10L;

    /** A second unit, so a per-unit read can be shown not to return it. */
    private static final long UNIT_WITH_ONE_CHUNK = 11L;

    private static final String CONTENT_COURSE_ID = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID = "lecture_unit_id";

    private static final String CONTENT_PAGE_NUMBER = "page_number";

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
    void readsStoredEntitiesForOneCourseExcludingPosts() throws Exception {
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1");
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE_UNIT, UNIT_WITH_CONTENT, "Introduction");
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L, "Sorting");
        // The firehose types are never enumerated: they would crowd out the rows the tree is built from.
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.POST, 99L, "A post");
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.ANSWER_POST, 98L, "An answer");
        // A different course must not leak into the read.
        insertMetadata(COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 3L, "Other course exercise");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<IndexedEntityDTO> entities = browserReadService.listIndexedEntitiesForCourse(COURSE_A);

            assertThat(entities).extracting(IndexedEntityDTO::type, IndexedEntityDTO::entityId, IndexedEntityDTO::title).containsExactlyInAnyOrder(
                    tuple(SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1"), tuple(SearchableEntitySchema.TypeValues.LECTURE_UNIT, UNIT_WITH_CONTENT, "Introduction"),
                    tuple(SearchableEntitySchema.TypeValues.EXERCISE, 1L, "Sorting"));
        });
    }

    @Test
    void storedEntityCarriesItsTitleAndIngestionTime() throws Exception {
        Instant before = Instant.now().minus(5, ChronoUnit.MINUTES);
        insertMetadata(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<IndexedEntityDTO> entities = browserReadService.listIndexedEntitiesForCourse(COURSE_A);
            assertThat(entities).hasSize(1);
            IndexedEntityDTO lecture = entities.getFirst();

            assertThat(lecture.ingestedAt()).isNotNull().isAfter(before);
            assertThat(lecture.title()).isEqualTo("Week 1");
            // A lecture has no parent lecture; the field is only set on units.
            assertThat(lecture.lectureId()).isNull();
        });
    }

    @Test
    void readsOneUnitsContentObjectsScopedToThatUnitAndCollection() throws Exception {
        insertContent(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 1);
        insertContent(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 2);
        // Neither the other unit, the other course, nor the other collection may appear in the result.
        insertContent(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_ONE_CHUNK, 1);
        insertContent(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_B, UNIT_WITH_CONTENT, 1);
        insertContent(IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 9);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<IndexedContentObjectDTO> objects = browserReadService.listContentObjectsForUnit(COURSE_A, UNIT_WITH_CONTENT, "slides");

            assertThat(objects).hasSize(2);
            assertThat(objects).allSatisfy(object -> {
                assertThat(object.ingestedAt()).isNotNull();
                assertThat(object.properties()).containsEntry(CONTENT_LECTURE_UNIT_ID, UNIT_WITH_CONTENT);
            });
            assertThat(objects).extracting(object -> object.properties().get(CONTENT_PAGE_NUMBER)).containsExactlyInAnyOrder(1L, 2L);
        });
    }

    @Test
    void unknownContentKeyIsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> browserReadService.listContentObjectsForUnit(COURSE_A, UNIT_WITH_CONTENT, "not-a-key"))
                .withMessageContaining("not-a-key");
    }

    @Test
    void absentContentCollectionReadsAsEmptyNotError() throws Exception {
        dropIrisContentCollections();

        assertThat(browserReadService.listContentObjectsForUnit(COURSE_A, UNIT_WITH_CONTENT, "slides")).isEmpty();
    }

    private void insertMetadata(long courseId, String type, long entityId, String title) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.insert(properties);
    }

    private void insertContent(String collectionName, long courseId, long unitId, int pageNumber) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_COURSE_ID, courseId);
        properties.put(CONTENT_LECTURE_UNIT_ID, unitId);
        properties.put(CONTENT_PAGE_NUMBER, pageNumber);
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
                collection.properties(Property.integer(CONTENT_PAGE_NUMBER));
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
