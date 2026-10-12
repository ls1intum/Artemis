package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityContentHasher;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityResolver;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityVectorWriter;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.CollectionHandleDefaults;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Bm25;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Hybrid;
import io.weaviate.client6.v1.api.collections.query.NearVector;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import io.weaviate.client6.v1.api.collections.query.Target;
import io.weaviate.client6.v1.api.collections.query.WeaviateQueryClient;
import io.weaviate.client6.v1.internal.grpc.GrpcTransport;
import io.weaviate.client6.v1.internal.orm.CollectionDescriptor;
import io.weaviate.client6.v1.internal.rest.RestTransport;

class SearchableEntityWeaviateServiceTest {

    private static final float[] QUERY_VECTOR = { 0.6f, 0.8f };

    private static SearchableEntityWeaviateService createService(WeaviateService weaviateService, SearchableEntityEmbeddingClient embeddingClient) {
        return new SearchableEntityWeaviateService(weaviateService, mock(WeaviateOutboxRepository.class), mock(SearchableEntityResolver.class), JsonObjectMapper.get(),
                new SearchableEntityContentHasher(JsonObjectMapper.get()), mock(ApplicationEventPublisher.class), mock(SearchableEntityVectorWriter.class), embeddingClient);
    }

    private static SearchableEntityEmbeddingClient embeddingClient(boolean enabled) {
        var embeddingClient = mock(SearchableEntityEmbeddingClient.class);
        when(embeddingClient.isEnabled()).thenReturn(enabled);
        when(embeddingClient.embedQuery(any())).thenReturn(QUERY_VECTOR);
        return embeddingClient;
    }

    private static QueryResponse<Map<String, Object>> responseWithOneRow() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, 23L);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Semantic exercise");
        properties.put(SearchableEntitySchema.Properties.RELEASE_DATE, OffsetDateTime.of(2026, 10, 3, 12, 30, 0, 0, ZoneOffset.UTC));
        return new QueryResponse<>(List.of(WeaviateObject.<Map<String, Object>>of(builder -> builder.properties(properties))), null);
    }

    /**
     * Runs the body against a real collection handle whose query client answers hybrid and keyword requests with the given
     * stubs, so the request objects the service builds can be inspected.
     */
    private static WeaviateQueryClient<?> withCollection(WeaviateService weaviateService, Consumer<WeaviateQueryClient<?>> stubs, Runnable body) {
        try (var mockedQueries = Mockito.mockConstruction(WeaviateQueryClient.class, Mockito.withSettings().defaultAnswer(CALLS_REAL_METHODS), (query, _) -> stubs.accept(query))) {
            CollectionHandle<Map<String, Object>> collection = new CollectionHandle<>(mock(RestTransport.class), mock(GrpcTransport.class),
                    CollectionDescriptor.ofMap(SearchableEntitySchema.COLLECTION_NAME), CollectionHandleDefaults.of(CollectionHandleDefaults.none()));
            when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenReturn(collection);
            body.run();
            return mockedQueries.constructed().getFirst();
        }
    }

    @Test
    void searchEntityCandidatesForAnswer_wrapsCollectionFailures() {
        var weaviateService = mock(WeaviateService.class);
        when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenThrow(new IllegalStateException("unavailable"));
        var searchableEntityWeaviateService = createService(weaviateService, embeddingClient(true));

        assertThatThrownBy(() -> searchableEntityWeaviateService.searchEntityCandidatesForAnswer("question", null, 10)).isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Failed to search entity candidates").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void searchEntityCandidatesForAnswer_buildsHybridQueriesWithTheQueryVectorAndWithAndWithoutAccessFilter() {
        var weaviateService = mock(WeaviateService.class);
        var embeddingClient = embeddingClient(true);
        var service = createService(weaviateService, embeddingClient);
        Filter filter = Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(42L);
        List<List<SearchableEntityWeaviateService.AnswerCandidate>> results = new ArrayList<>();

        var query = withCollection(weaviateService, mockQuery -> doReturn(responseWithOneRow()).when(mockQuery).hybrid(any(Hybrid.class)), () -> {
            results.add(service.searchEntityCandidatesForAnswer("semantic question", filter, 7));
            results.add(service.searchEntityCandidatesForAnswer("fallback question", null, 3));
        });

        assertThat(results.getFirst()).singleElement().satisfies(candidate -> {
            assertThat(candidate.properties()).containsEntry(SearchableEntitySchema.Properties.ENTITY_ID, 23L).containsEntry(SearchableEntitySchema.Properties.RELEASE_DATE,
                    "2026-10-03T12:30:00.000Z");
            // The row carries no chunk hashes and no vectors, so no passage can be determined reliably.
            assertThat(candidate.passage()).isNull();
        });
        assertThat(results.get(1)).singleElement()
                .satisfies(candidate -> assertThat(candidate.properties()).containsEntry(SearchableEntitySchema.Properties.TITLE, "Semantic exercise"));
        ArgumentCaptor<Hybrid> hybridQueries = ArgumentCaptor.forClass(Hybrid.class);
        verify(query, times(2)).hybrid(hybridQueries.capture());
        assertHybridQuery(hybridQueries.getAllValues().getFirst(), "semantic question", 7, filter);
        assertHybridQuery(hybridQueries.getAllValues().get(1), "fallback question", 3, null);
        verify(embeddingClient).embedQuery("semantic question");
        verify(query, never()).bm25(any(Bm25.class));
    }

    @Test
    void searchSearchableEntities_usesKeywordSearchWhenArtemisComputesNoEmbeddings() {
        var weaviateService = mock(WeaviateService.class);
        var embeddingClient = embeddingClient(false);
        var service = createService(weaviateService, embeddingClient);
        List<Map<String, Object>> results = new ArrayList<>();

        var query = withCollection(weaviateService, mockQuery -> doReturn(responseWithOneRow()).when(mockQuery).bm25(any(Bm25.class)),
                () -> results.addAll(service.searchSearchableEntities("flyweight", null, 5)));

        assertThat(results).singleElement().satisfies(row -> assertThat(row).containsEntry(SearchableEntitySchema.Properties.ENTITY_ID, 23L));
        assertKeywordQuery(query, "flyweight", 5);
        verify(embeddingClient, never()).embedQuery(any());
    }

    @Test
    void searchSearchableEntities_fallsBackToKeywordSearchWhenTheQueryCannotBeEmbedded() {
        var weaviateService = mock(WeaviateService.class);
        var embeddingClient = embeddingClient(true);
        when(embeddingClient.embedQuery(any())).thenThrow(new EmbeddingFailedException("timeout"));
        var service = createService(weaviateService, embeddingClient);
        List<Map<String, Object>> results = new ArrayList<>();

        var query = withCollection(weaviateService, mockQuery -> doReturn(responseWithOneRow()).when(mockQuery).bm25(any(Bm25.class)),
                () -> results.addAll(service.searchSearchableEntities("flyweight", null, 5)));

        assertThat(results).hasSize(1);
        assertKeywordQuery(query, "flyweight", 5);
    }

    @Test
    void searchSearchableEntities_fallsBackToKeywordSearchWhenTheHybridSearchFails() {
        var weaviateService = mock(WeaviateService.class);
        var service = createService(weaviateService, embeddingClient(true));
        List<Map<String, Object>> results = new ArrayList<>();

        var query = withCollection(weaviateService, mockQuery -> {
            doThrow(new IllegalStateException("collection has no vector index")).when(mockQuery).hybrid(any(Hybrid.class));
            doReturn(responseWithOneRow()).when(mockQuery).bm25(any(Bm25.class));
        }, () -> results.addAll(service.searchSearchableEntities("flyweight", null, 5)));

        assertThat(results).hasSize(1);
        verify(query).hybrid(any(Hybrid.class));
        assertKeywordQuery(query, "flyweight", 5);
    }

    private static void assertKeywordQuery(WeaviateQueryClient<?> query, String expectedText, int expectedLimit) {
        ArgumentCaptor<Bm25> keywordQuery = ArgumentCaptor.forClass(Bm25.class);
        verify(query).bm25(keywordQuery.capture());
        assertThat(keywordQuery.getValue().query()).isEqualTo(expectedText);
        assertThat(keywordQuery.getValue().common().limit()).isEqualTo(expectedLimit);
    }

    private static void assertHybridQuery(Hybrid query, String expectedText, int expectedLimit, Filter expectedFilter) {
        assertThat(query.searchTarget()).isInstanceOf(Target.TextTarget.class);
        assertThat(((Target.TextTarget) query.searchTarget()).query()).containsExactly(expectedText);
        // Artemis provides the query vector; Weaviate must not try to vectorize the text itself.
        assertThat(query.near()).isInstanceOf(NearVector.class);
        assertThat(query.queryProperties()).isEmpty();
        assertThat(query.common().limit()).isEqualTo(expectedLimit);
        if (expectedFilter == null) {
            assertThat(query.common().filters()).isNull();
        }
        else {
            assertThat(query.common().filters()).isSameAs(expectedFilter);
        }
    }
}
