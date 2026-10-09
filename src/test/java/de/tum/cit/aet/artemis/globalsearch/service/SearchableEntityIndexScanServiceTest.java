package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;

class SearchableEntityIndexScanServiceTest {

    private final WeaviateService weaviateService = mock(WeaviateService.class);

    private final SearchableEntityIndexScanService indexScanService = new SearchableEntityIndexScanService(weaviateService);

    @Test
    void testScanFailurePropagatesInsteadOfLookingLikeAnEmptySlice() {
        RuntimeException failure = new RuntimeException("Weaviate unavailable");
        when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenThrow(failure);

        assertThatThrownBy(() -> indexScanService.scanFrom(null, 100, 1)).isInstanceOf(WeaviateException.class).hasMessageContaining("Failed to scan")
                .satisfies(exception -> assertThat(exception.getCause()).isSameAs(failure));
    }

    @Test
    void testPresenceLookupFailurePropagatesInsteadOfLookingLikeNoIndexedRows() {
        RuntimeException failure = new RuntimeException("Weaviate unavailable");
        when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenThrow(failure);

        assertThatThrownBy(() -> indexScanService.existingEntityIds(SearchableEntitySchema.TypeValues.COURSE, List.of(1L))).isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Failed to check index presence").satisfies(exception -> assertThat(exception.getCause()).isSameAs(failure));
    }
}
