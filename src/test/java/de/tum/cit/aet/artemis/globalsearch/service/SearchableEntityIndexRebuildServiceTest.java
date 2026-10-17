package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Unit tests for {@link SearchableEntityIndexRebuildService}; the rebuild itself is tested against a real Weaviate in
 * {@link SearchableEntityMultiVectorWeaviateTest}.
 */
class SearchableEntityIndexRebuildServiceTest {

    private static WeaviateConfigurationProperties properties(String mode, String model) {
        return new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "", mode, model, null, null, null, null);
    }

    @Test
    void theRebuildServiceNeedsAWeaviateClientUnlessTheOpenApiDocsAreGenerated() {
        var properties = properties("none", null);
        var weaviateService = mock(WeaviateService.class);
        var syncStateRepository = mock(SearchableEntitySyncStateRepository.class);
        var reconcileStateRepository = mock(SearchableEntityReconcileStateRepository.class);

        assertThatThrownBy(() -> new SearchableEntityIndexRebuildService(Optional.empty(), properties, new MockEnvironment(), weaviateService, syncStateRepository,
                reconcileStateRepository, mock(WeaviateOutboxRepository.class))).isInstanceOf(IllegalStateException.class);
        var openApiDocsGeneration = new MockEnvironment().withProperty("artemis.openapi-docs-generation", "true");
        assertThat(new SearchableEntityIndexRebuildService(Optional.empty(), properties, openApiDocsGeneration, weaviateService, syncStateRepository, reconcileStateRepository,
                mock(WeaviateOutboxRepository.class)).desiredSignature()).isEqualTo("layout=2;mode=none");
    }

    @Test
    void theSignatureNamesTheModelOnlyWhenArtemisEmbeds() {
        var openApiDocsGeneration = new MockEnvironment().withProperty("artemis.openapi-docs-generation", "true");
        var embedding = new SearchableEntityIndexRebuildService(Optional.empty(), properties("text2vec-openai", "Qwen/Qwen3-Embedding-8B"), openApiDocsGeneration,
                mock(WeaviateService.class), mock(SearchableEntitySyncStateRepository.class), mock(SearchableEntityReconcileStateRepository.class),
                mock(WeaviateOutboxRepository.class));

        assertThat(embedding.desiredSignature()).isEqualTo("layout=2;mode=text2vec-openai;model=Qwen/Qwen3-Embedding-8B");
    }
}
