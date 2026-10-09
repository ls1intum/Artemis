package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;

/**
 * An Iris content collection that cannot be read must surface as an error. Read as absent, an outage showed as content
 * that was never ingested: an empty unit in the browser and missing slides in every coverage count.
 */
class IngestionContentReadFailureTest {

    private final WeaviateService weaviateService = mock(WeaviateService.class);

    private final IngestionBrowserWeaviateReadService browserReadService = new IngestionBrowserWeaviateReadService(weaviateService);

    private final IngestionCoverageWeaviateReadService coverageReadService = new IngestionCoverageWeaviateReadService(weaviateService);

    @BeforeEach
    void makeWeaviateUnreachable() {
        when(weaviateService.externalCollectionExists(anyString())).thenThrow(new WeaviateException("Weaviate is unreachable", new IOException("connection refused")));
    }

    @Test
    void unitContentReportsTheFailureRatherThanAnEmptyUnit() {
        assertThatThrownBy(() -> browserReadService.listContentObjectsForUnit(1L, 2L, IngestionBrowserWeaviateReadService.KEY_SLIDES)).isInstanceOf(WeaviateException.class);
    }

    @Test
    void contentCoverageReportsTheFailureRatherThanNoContent() {
        assertThatThrownBy(() -> coverageReadService.readPresentContentUnitIds(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, List.of(1L)))
                .isInstanceOf(WeaviateException.class);
    }

    @Test
    void overviewStillReportsTheCollectionAsUnavailable() {
        assertThat(coverageReadService.countExternalCollection(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION)).isEmpty();
    }

    @Test
    void prefixedOverviewReportsAnUnreadableCollectionAsUnavailable() {
        when(weaviateService.getCollection("SearchableEntities")).thenThrow(new WeaviateException("Weaviate is unreachable", new IOException("connection refused")));

        assertThat(coverageReadService.countPrefixedCollection("SearchableEntities")).isEmpty();
    }
}
