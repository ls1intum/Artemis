package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.seedRow;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

/**
 * Integration tests for the resumable scan of the {@code SearchableEntities} collection: that a slice is bounded,
 * that resuming from the returned cursor continues rather than restarting, that walking to the end reports no
 * cursor, and that only the scanned properties come back.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class SearchableEntityIndexScanIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String COURSE_TYPE = SearchableEntitySchema.TypeValues.COURSE;

    private static final int SEEDED_ROWS = 12;

    @Autowired
    private SearchableEntityIndexScanService indexScanService;

    @Autowired
    private WeaviateService weaviateService;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void seedCollection() throws Exception {
        for (int index = 0; index < SEEDED_ROWS; index++) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(SearchableEntitySchema.Properties.TYPE, COURSE_TYPE);
            properties.put(SearchableEntitySchema.Properties.ENTITY_ID, (long) index);
            properties.put(SearchableEntitySchema.Properties.TITLE, "Course " + index);
            properties.put(SearchableEntitySchema.Properties.SOURCE_SEQ, (long) index);
            properties.put(SearchableEntitySchema.Properties.CONTENT_HASH, "v1:hash-" + index);
            seedRow(weaviateService, COURSE_TYPE, index, properties);
        }
    }

    @Test
    void testASliceIsBoundedAndReportsWhereToResume() {
        var slice = indexScanService.scanFrom(null, 2, 2);

        assertThat(slice.rows()).hasSize(4);
        assertThat(slice.nextCursor()).as("a full slice means there is more to read").isNotNull();
    }

    @Test
    void testResumingFromTheCursorContinuesRatherThanRestarting() {
        var first = indexScanService.scanFrom(null, 2, 2);
        var second = indexScanService.scanFrom(first.nextCursor(), 2, 2);

        Set<String> firstUuids = first.rows().stream().map(SearchableEntityIndexScanService.IndexedRow::uuid).collect(Collectors.toSet());
        Set<String> secondUuids = second.rows().stream().map(SearchableEntityIndexScanService.IndexedRow::uuid).collect(Collectors.toSet());
        assertThat(secondUuids).as("resuming must not hand back rows the previous slice already returned").doesNotContainAnyElementsOf(firstUuids);
    }

    @Test
    void testWalkingToTheEndReportsNoCursor() {
        // Asking for more than the collection holds is how a pass learns it has finished a cycle.
        var slice = indexScanService.scanFrom(null, SEEDED_ROWS * 2, 1);

        assertThat(slice.rows()).hasSizeGreaterThanOrEqualTo(SEEDED_ROWS);
        assertThat(slice.nextCursor()).as("a short slice means the collection ran out").isNull();
    }

    @Test
    void testTheWholeCollectionIsReachableBySteppingThroughCursors() {
        Set<String> seen = new HashSet<>();
        String cursor = null;
        do {
            var slice = indexScanService.scanFrom(cursor, 5, 1);
            slice.rows().forEach(row -> seen.add(row.uuid()));
            cursor = slice.nextCursor();
        }
        while (cursor != null);

        assertThat(seen).hasSizeGreaterThanOrEqualTo(SEEDED_ROWS);
    }

    @Test
    void testOnlyTheScannedPropertiesAreRead() {
        var slice = indexScanService.scanFrom(null, SEEDED_ROWS * 2, 1);

        List<SearchableEntityIndexScanService.IndexedRow> courseRows = slice.rows().stream().filter(row -> COURSE_TYPE.equals(row.entityType())).toList();
        assertThat(courseRows).isNotEmpty();
        assertThat(courseRows).allSatisfy(row -> {
            assertThat(row.uuid()).isNotBlank();
            assertThat(row.entityId()).isNotNull();
            assertThat(row.contentHash()).startsWith("v1:");
        });
    }

    @Test
    void testRowsWrittenBeforeTheOperationalPropertiesExistedReadAsNull() throws Exception {
        // A row with no stored hash is not corrupt, it is simply one nothing has verified yet. The verify pass
        // relies on being able to tell that apart from a mismatch.
        Map<String, Object> withoutOperationalProperties = new HashMap<>();
        withoutOperationalProperties.put(SearchableEntitySchema.Properties.TYPE, COURSE_TYPE);
        withoutOperationalProperties.put(SearchableEntitySchema.Properties.ENTITY_ID, 9_000L);
        withoutOperationalProperties.put(SearchableEntitySchema.Properties.TITLE, "Legacy course");
        seedRow(weaviateService, COURSE_TYPE, 9_000L, withoutOperationalProperties);

        // Other Weaviate tests in this context leave rows behind, so walk every cursor rather than trust one page.
        List<SearchableEntityIndexScanService.IndexedRow> rows = new ArrayList<>();
        String cursor = null;
        do {
            var slice = indexScanService.scanFrom(cursor, 1_000, 1);
            rows.addAll(slice.rows());
            cursor = slice.nextCursor();
        }
        while (cursor != null);

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.entityId()).isEqualTo(9_000L);
            assertThat(row.contentHash()).isNull();
            assertThat(row.sourceSeq()).isNull();
        });
    }
}
