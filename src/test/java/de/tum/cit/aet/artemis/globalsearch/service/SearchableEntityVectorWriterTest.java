package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

class SearchableEntityVectorWriterTest {

    @Test
    void prepareWithoutEmbeddingsNeedsNoChunksAndNoStoredRow() {
        var weaviateService = mock(WeaviateService.class);
        var embeddingClient = mock(SearchableEntityEmbeddingClient.class);
        when(embeddingClient.isEnabled()).thenReturn(false);

        var row = new SearchableEntityVectorWriter(weaviateService, embeddingClient).prepare(SearchableEntitySchema.TypeValues.FAQ, 1L,
                Map.of(SearchableEntitySchema.Properties.TITLE, "How do I submit?"));

        assertThat(row.chunks()).isEmpty();
        assertThat(row.missingChunks()).isEmpty();
    }

    @Test
    void aFailureToReadTheStoredRowIsReportedAsAWeaviateError() {
        var weaviateService = mock(WeaviateService.class);
        var embeddingClient = mock(SearchableEntityEmbeddingClient.class);
        when(embeddingClient.isEnabled()).thenReturn(true);
        when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenThrow(new IllegalStateException("unavailable"));
        var writer = new SearchableEntityVectorWriter(weaviateService, embeddingClient);

        assertThatThrownBy(() -> writer.prepare(SearchableEntitySchema.TypeValues.FAQ, 1L, Map.of(SearchableEntitySchema.Properties.TITLE, "How do I submit?")))
                .isInstanceOf(WeaviateException.class).hasCauseInstanceOf(IllegalStateException.class);
    }
}
