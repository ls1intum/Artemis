package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.CONTENT_LECTURE_UNIT_ID;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.CONTENT_PAGE_NUMBER;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.dropIrisContentCollections;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertContent;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertMetadata;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.recreateIrisContentCollections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityRecordDTO;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;
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
    void readsStoredEntitiesForOneCourseExcludingPosts() throws Exception {
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1");
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE_UNIT, UNIT_WITH_CONTENT, "Introduction");
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.EXERCISE, 1L, "Sorting");
        // The firehose types are never enumerated: they would crowd out the rows the tree is built from.
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.POST, 99L, "A post");
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.ANSWER_POST, 98L, "An answer");
        // A different course must not leak into the read.
        insertMetadata(weaviateService, COURSE_B, SearchableEntitySchema.TypeValues.EXERCISE, 3L, "Other course exercise");

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
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1");

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
    void readsRecordsOfTheRequestedTypeWithTheirStoredProperties() throws Exception {
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE, 20L, "Week 1");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<IndexedEntityRecordDTO> records = browserReadService.listIndexedEntityRecords(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE);

            assertThat(records).hasSize(1);
            IndexedEntityRecordDTO record = records.getFirst();
            assertThat(record.type()).isEqualTo(SearchableEntitySchema.TypeValues.LECTURE);
            assertThat(record.entityId()).isEqualTo(20L);
            assertThat(record.title()).isEqualTo("Week 1");
            assertThat(record.properties()).containsEntry(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.LECTURE);
        });
    }

    /**
     * The TYPE property is word-tokenized text, so Weaviate's equality filter for "lecture" also matches a
     * "lecture_unit" row, whose tokens are "lecture" and "unit". Before this was guarded, selecting a lecture that was
     * not itself indexed but had an indexed unit under it showed that unit's stored record in the lecture's own detail
     * pane, mislabelled with the lecture's type.
     */
    @Test
    void excludesAnObjectWhoseTypeOnlyContainsTheRequestedTypeAsAToken() throws Exception {
        insertMetadata(weaviateService, COURSE_A, SearchableEntitySchema.TypeValues.LECTURE_UNIT, 2L, "Tutorial Slides");

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(browserReadService.listIndexedEntitiesForCourse(COURSE_A)).isNotEmpty());

        List<IndexedEntityRecordDTO> records = browserReadService.listIndexedEntityRecords(COURSE_A, SearchableEntitySchema.TypeValues.LECTURE);
        assertThat(records).as("a lecture_unit row must never be returned for a query asking for lecture records").isEmpty();
    }

    @Test
    void readsOneUnitsContentObjectsScopedToThatUnitAndCollection() throws Exception {
        insertContent(weaviateService, IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 1);
        insertContent(weaviateService, IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 2);
        // Neither the other unit, the other course, nor the other collection may appear in the result.
        insertContent(weaviateService, IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_A, UNIT_WITH_ONE_CHUNK, 1);
        insertContent(weaviateService, IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, COURSE_B, UNIT_WITH_CONTENT, 1);
        insertContent(weaviateService, IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, COURSE_A, UNIT_WITH_CONTENT, 9);

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
        dropIrisContentCollections(weaviateClient);

        assertThat(browserReadService.listContentObjectsForUnit(COURSE_A, UNIT_WITH_CONTENT, "slides")).isEmpty();
    }

    private void clearMetadataForTestCourses() throws Exception {
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data
                .deleteMany(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).containsAny(COURSE_A, COURSE_B));
    }

}
