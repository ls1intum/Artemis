package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityContentHasher;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityResolver;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.CollectionHandleDefaults;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Hybrid;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import io.weaviate.client6.v1.api.collections.query.Target;
import io.weaviate.client6.v1.api.collections.query.WeaviateQueryClient;
import io.weaviate.client6.v1.internal.grpc.GrpcTransport;
import io.weaviate.client6.v1.internal.orm.CollectionDescriptor;
import io.weaviate.client6.v1.internal.rest.RestTransport;

class SearchableEntityWeaviateServiceTest {

    private static SearchableEntityWeaviateService createService(WeaviateService weaviateService) {
        return new SearchableEntityWeaviateService(weaviateService, mock(WeaviateOutboxRepository.class), mock(SearchableEntityResolver.class), JsonObjectMapper.get(),
                new SearchableEntityContentHasher(JsonObjectMapper.get()), mock(ApplicationEventPublisher.class));
    }

    @Test
    void searchEntityCandidatesForAnswer_wrapsCollectionFailures() {
        var weaviateService = mock(WeaviateService.class);
        when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenThrow(new IllegalStateException("unavailable"));
        var searchableEntityWeaviateService = createService(weaviateService);

        assertThatThrownBy(() -> searchableEntityWeaviateService.searchEntityCandidatesForAnswer("question", null, 10)).isInstanceOf(WeaviateException.class)
                .hasMessageContaining("Failed to search entity candidates").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void searchEntityCandidatesForAnswer_buildsHybridQueriesWithAndWithoutAccessFilter() {
        var weaviateService = mock(WeaviateService.class);
        when(weaviateService.isVectorizerAvailable()).thenReturn(true);
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, 23L);
        properties.put(SearchableEntitySchema.Properties.TITLE, "Semantic exercise");
        properties.put(SearchableEntitySchema.Properties.RELEASE_DATE, OffsetDateTime.of(2026, 10, 3, 12, 30, 0, 0, ZoneOffset.UTC));
        var response = new QueryResponse<>(List.of(WeaviateObject.<Map<String, Object>>of(builder -> builder.properties(properties))), null);

        try (var mockedQueries = Mockito.mockConstruction(WeaviateQueryClient.class, Mockito.withSettings().defaultAnswer(CALLS_REAL_METHODS),
                (query, _) -> doReturn(response).when(query).hybrid(any(Hybrid.class)))) {
            CollectionHandle<Map<String, Object>> collection = new CollectionHandle<>(mock(RestTransport.class), mock(GrpcTransport.class),
                    CollectionDescriptor.ofMap(SearchableEntitySchema.COLLECTION_NAME), CollectionHandleDefaults.of(CollectionHandleDefaults.none()));
            when(weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME)).thenReturn(collection);
            var searchableEntityWeaviateService = createService(weaviateService);
            Filter filter = Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(42L);

            var filteredCandidates = searchableEntityWeaviateService.searchEntityCandidatesForAnswer("semantic question", filter, 7);
            var unfilteredCandidates = searchableEntityWeaviateService.searchEntityCandidatesForAnswer("fallback question", null, 3);

            assertThat(filteredCandidates).hasSize(1);
            assertThat(filteredCandidates.getFirst()).containsEntry(SearchableEntitySchema.Properties.ENTITY_ID, 23L).containsEntry(SearchableEntitySchema.Properties.RELEASE_DATE,
                    "2026-10-03T12:30:00.000Z");
            assertThat(unfilteredCandidates).hasSize(1);
            assertThat(unfilteredCandidates.getFirst()).containsEntry(SearchableEntitySchema.Properties.TITLE, "Semantic exercise");

            var query = mockedQueries.constructed().getFirst();
            ArgumentCaptor<Hybrid> hybridQueries = ArgumentCaptor.forClass(Hybrid.class);
            verify(query, times(2)).hybrid(hybridQueries.capture());
            assertHybridQuery(hybridQueries.getAllValues().getFirst(), "semantic question", 7, filter);
            assertHybridQuery(hybridQueries.getAllValues().get(1), "fallback question", 3, null);
        }
    }

    private static void assertHybridQuery(Hybrid query, String expectedText, int expectedLimit, Filter expectedFilter) {
        assertThat(query.searchTarget()).isInstanceOf(Target.TextTarget.class);
        assertThat(((Target.TextTarget) query.searchTarget()).query()).containsExactly(expectedText);
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
