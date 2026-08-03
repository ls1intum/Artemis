package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentSessionCacheService.MessagePreviewData;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

@ExtendWith(MockitoExtension.class)
class AtlasAgentSessionCacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache operationsCache;

    @Mock
    private Cache relationsCache;

    @Mock
    private DistributedDataProvider mockDistributedDataProvider;

    private AtlasAgentSessionCacheService service;

    private static final String SESSION_ID = "course_1_user_1";

    @BeforeEach
    void setUp() {
        service = new AtlasAgentSessionCacheService(cacheManager, mockDistributedDataProvider);
    }

    @Test
    void shouldStoreAndRetrieveCompetencyOperations() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(operationsCache);
        List<CompetencyOperation> operations = List.of(new CompetencyOperation(null, "OOP", "Object-Oriented Programming", CompetencyTaxonomy.APPLY));

        service.cachePendingCompetencyOperations(SESSION_ID, operations);

        verify(operationsCache).put(SESSION_ID, operations);
    }

    @Test
    void shouldReturnNullWhenCompetencyOperationsCacheNotConfigured() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(null);

        List<CompetencyOperation> result = service.getCachedPendingCompetencyOperations(SESSION_ID);

        assertThat(result).isNull();
    }

    @Test
    void shouldEvictCompetencyOperations() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(operationsCache);

        service.clearCachedPendingCompetencyOperations(SESSION_ID);

        verify(operationsCache).evict(SESSION_ID);
    }

    @Test
    void shouldStoreAndRetrieveRelationData() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(relationsCache);
        List<CompetencyRelationDTO> relations = List.of(new CompetencyRelationDTO(null, 1L, 2L, RelationType.ASSUMES));

        service.cacheRelationOperations(SESSION_ID, relations);

        verify(relationsCache).put(SESSION_ID, relations);
    }

    @Test
    void shouldReturnNullWhenRelationCacheNotConfigured() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(null);

        List<CompetencyRelationDTO> result = service.getCachedRelationData(SESSION_ID);

        assertThat(result).isNull();
    }

    @Test
    void shouldEvictRelationData() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(relationsCache);

        service.clearCachedRelationOperations(SESSION_ID);

        verify(relationsCache).evict(SESSION_ID);
    }

    @Test
    void shouldNotLoseEntriesUnderConcurrentStoreCalls() throws Exception {
        // Use a real provider so the per-key locking that guards the read-modify-write is actually exercised. The local
        // provider is enough for that: the cross-backend lock contract itself is covered by AbstractDistributedDataTest.
        LocalDataProviderService provider = new LocalDataProviderService();
        {
            AtlasAgentSessionCacheService realService = new AtlasAgentSessionCacheService(cacheManager, provider);

            int threads = 32;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    realService.storePreviewForMessage(SESSION_ID, idx, new MessagePreviewData(null, null, null, null));
                    return null;
                }));
            }
            try {
                start.countDown();
                for (Future<?> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }
            }
            finally {
                // A throwing Future#get would otherwise leave the fixed pool's threads alive and hang the test JVM.
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }

            assertThat(realService.getPreviewHistory(SESSION_ID)).hasSize(threads);
        }
    }

    /**
     * The preview history has to keep expiring. {@code HazelcastConfiguration} gives this map a two-hour TTL, but a map
     * from {@code DistributedDataProvider#getMap} never expires by contract, so requesting a plain map here would keep
     * every session's history forever on any provider that does not read the Hazelcast map configuration.
     */
    @Test
    void shouldRequestTheExpiringPreviewHistoryMap() {
        DistributedMap<String, Map<Integer, MessagePreviewData>> historyMap = new LocalDataProviderService()
                .getMap(AtlasAgentSessionCacheService.ATLAS_SESSION_PREVIEW_HISTORY_CACHE);
        // doReturn rather than when(...).thenReturn(...), because getExpiringMap is generic and has no assignment context
        // here for the type arguments to be inferred from.
        doReturn(historyMap).when(mockDistributedDataProvider).getExpiringMap(AtlasAgentSessionCacheService.ATLAS_SESSION_PREVIEW_HISTORY_CACHE, Duration.ofHours(2));

        service.getPreviewHistory(SESSION_ID);

        verify(mockDistributedDataProvider).getExpiringMap(AtlasAgentSessionCacheService.ATLAS_SESSION_PREVIEW_HISTORY_CACHE, Duration.ofHours(2));
    }

    @Test
    void shouldNotThrowWhenCacheNotConfiguredOnPut() {
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_OPERATIONS_CACHE)).thenReturn(null);
        when(cacheManager.getCache(AtlasAgentSessionCacheService.ATLAS_SESSION_PENDING_RELATIONS_CACHE)).thenReturn(null);

        service.cachePendingCompetencyOperations(SESSION_ID, List.of());
        service.cacheRelationOperations(SESSION_ID, List.of());
        service.clearCachedPendingCompetencyOperations(SESSION_ID);
        service.clearCachedRelationOperations(SESSION_ID);
    }
}
